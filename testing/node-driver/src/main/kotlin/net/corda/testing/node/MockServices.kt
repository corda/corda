package net.corda.testing.node

import com.google.common.collect.MutableClassToInstanceMap
import net.corda.core.contracts.ContractClassName
import net.corda.core.contracts.StateRef
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.StateMachineTransactionMapping
import net.corda.core.node.*
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.internal.ServicesForResolutionImpl
import net.corda.node.internal.configureDatabase
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.api.*
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.vault.NodeVaultService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.TestIdentity
import net.corda.testing.internal.DEV_ROOT_CA
import net.corda.testing.internal.MockCordappProvider
import net.corda.testing.node.internal.MockKeyManagementService
import net.corda.testing.node.internal.MockTransactionStorage
import net.corda.testing.services.MockAttachmentStorage
import java.security.KeyPair
import java.sql.Connection
import java.time.Clock
import java.util.*

/**
 * Returns a simple [InMemoryIdentityService] containing the supplied [identities].
 */
fun makeTestIdentityService(vararg identities: PartyAndCertificate) = InMemoryIdentityService(identities.toList(), DEV_ROOT_CA.certificate)

/**
 * An implementation of [ServiceHub] that is designed for in-memory unit tests of contract validation logic. It has
 * enough functionality to do tests of code that queries the vault, inserts to the vault, and constructs/checks
 * transactions. However it isn't enough to test flows and other aspects of an app that require a network. For that
 * you should investigate [MockNetwork].
 *
 * There are a variety of constructors that can be used to supply enough data to simulate a node. Each mock service hub
 * must have at least an identity of its own. The other components have defaults that work in most situations.
 */
open class MockServices private constructor(
        cordappLoader: CordappLoader,
        override val validatedTransactions: TransactionStorage,
        override val identityService: IdentityService,
        final override val networkParameters: NetworkParameters,
        private val initialIdentity: TestIdentity,
        private val moreKeys: Array<out KeyPair>
) : ServiceHub {
    companion object {
        /**
         * Make properties appropriate for creating a DataSource for unit tests.
         *
         * @param nodeName Reflects the "instance" of the in-memory database.  Defaults to a random string.
         */
        // TODO: Can we use an X509 principal generator here?
        @JvmStatic
        fun makeTestDataSourceProperties(nodeName: String = SecureHash.randomSHA256().toString()): Properties {
            val props = Properties()
            props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
            props.setProperty("dataSource.url", "jdbc:h2:mem:${nodeName}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
            props.setProperty("dataSource.user", "sa")
            props.setProperty("dataSource.password", "")
            return props
        }

        /**
         * Makes database and mock services appropriate for unit tests.
         *
         * @param cordappPackages A [List] of cordapp packages to scan for any cordapp code, e.g. contract verification code, flows and services.
         * @param identityService An instance of [IdentityService], see [makeTestIdentityService].
         * @param initialIdentity The first (typically sole) identity the services will represent.
         * @param moreKeys A list of additional [KeyPair] instances to be used by [MockServices].
         * @return A pair where the first element is the instance of [CordaPersistence] and the second is [MockServices].
         */
        @JvmStatic
        @JvmOverloads
        fun makeTestDatabaseAndMockServices(cordappPackages: List<String>,
                                            identityService: IdentityService,
                                            initialIdentity: TestIdentity,
                                            networkParameters: NetworkParameters = testNetworkParameters(),
                                            vararg moreKeys: KeyPair): Pair<CordaPersistence, MockServices> {
            val cordappLoader = CordappLoader.createWithTestPackages(cordappPackages)
            val dataSourceProps = makeTestDataSourceProperties()
            val schemaService = NodeSchemaService(cordappLoader.cordappSchemas)
            val database = configureDatabase(dataSourceProps, DatabaseConfig(), identityService::wellKnownPartyFromX500Name, identityService::wellKnownPartyFromAnonymous, schemaService)
            val mockService = database.transaction {
                object : MockServices(cordappLoader, identityService, networkParameters, initialIdentity, moreKeys) {
                    override val vaultService: VaultService = makeVaultService(database.hibernateConfig, schemaService, database)

                    override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
                        ServiceHubInternal.recordTransactions(statesToRecord, txs,
                                validatedTransactions as WritableTransactionStorage,
                                mockStateMachineRecordedTransactionMappingStorage,
                                vaultService as VaultServiceInternal,
                                database)
                    }

                    override fun jdbcSession(): Connection = database.createSession()
                }
            }
            return Pair(database, mockService)
        }

        @JvmStatic
        private fun getCallerPackage(): String {
            // TODO: In Java 9 there's a new stack walker API that is better than this.
            // The magic number '3' here is to chop off this method, an invisible bridge method generated by the
            // compiler and then the c'tor itself.
            return Throwable().stackTrace[3].className.split('.').dropLast(1).joinToString(".")
        }

        // Because Kotlin is dumb and makes not publicly visible objects public, thus changing the public API.
        private val mockStateMachineRecordedTransactionMappingStorage = MockStateMachineRecordedTransactionMappingStorage()
    }

    private class MockStateMachineRecordedTransactionMappingStorage : StateMachineRecordedTransactionMappingStorage {
        override fun addMapping(stateMachineRunId: StateMachineRunId, transactionId: SecureHash) {
            throw UnsupportedOperationException()
        }

        override fun track(): DataFeed<List<StateMachineTransactionMapping>, StateMachineTransactionMapping> {
            throw UnsupportedOperationException()
        }
    }

    private constructor(cordappLoader: CordappLoader, identityService: IdentityService, networkParameters: NetworkParameters,
                        initialIdentity: TestIdentity, moreKeys: Array<out KeyPair>)
            : this(cordappLoader, MockTransactionStorage(), identityService, networkParameters, initialIdentity, moreKeys)

    /**
     * Create a mock [ServiceHub] that looks for app code in the given package names, uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>,
                initialIdentity: TestIdentity,
                identityService: IdentityService = makeTestIdentityService(),
                vararg moreKeys: KeyPair) :
            this(CordappLoader.createWithTestPackages(cordappPackages), identityService, testNetworkParameters(), initialIdentity, moreKeys)

    constructor(cordappPackages: List<String>,
                initialIdentity: TestIdentity,
                identityService: IdentityService,
                networkParameters: NetworkParameters,
                vararg moreKeys: KeyPair) :
            this(CordappLoader.createWithTestPackages(cordappPackages), identityService, networkParameters, initialIdentity, moreKeys)

    /**
     * Create a mock [ServiceHub] that looks for app code in the given package names, uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>, initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService(), key: KeyPair, vararg moreKeys: KeyPair) :
            this(cordappPackages, TestIdentity(initialIdentityName, key), identityService, *moreKeys)

    /**
     * Create a mock [ServiceHub] that can't load CorDapp code, which uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and which represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>, initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService()) :
            this(cordappPackages, TestIdentity(initialIdentityName), identityService)

    /**
     * Create a mock [ServiceHub] that can't load CorDapp code, and which uses a default service identity.
     */
    constructor(cordappPackages: List<String>) : this(cordappPackages, CordaX500Name("TestIdentity", "", "GB"), makeTestIdentityService())

    /**
     * Create a mock [ServiceHub] which uses the package of the caller to find CorDapp code. It uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and which represents the given identity.
     */
    @JvmOverloads
    constructor(initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService(), key: KeyPair, vararg moreKeys: KeyPair)
            : this(listOf(getCallerPackage()), TestIdentity(initialIdentityName, key), identityService, *moreKeys)

    /**
     * Create a mock [ServiceHub] which uses the package of the caller to find CorDapp code. It uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and which represents the given identity. It has no keys.
     */
    @JvmOverloads
    constructor(initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService())
            : this(listOf(getCallerPackage()), TestIdentity(initialIdentityName), identityService)

    /**
     * A helper constructor that requires at least one test identity to be registered, and which takes the package of
     * the caller as the package in which to find app code. This is the most convenient constructor and the one that
     * is normally worth using. The first identity is the identity of this service hub, the rest are identities that
     * it is aware of.
     */
    constructor(firstIdentity: TestIdentity, vararg moreIdentities: TestIdentity) : this(
            listOf(getCallerPackage()),
            firstIdentity,
            makeTestIdentityService(*listOf(firstIdentity, *moreIdentities).map { it.identity }.toTypedArray()),
            firstIdentity.keyPair
    )

    /**
     * Create a mock [ServiceHub] which uses the package of the caller to find CorDapp code. It uses a default service
     * identity.
     */
    constructor() : this(listOf(getCallerPackage()), CordaX500Name("TestIdentity", "", "GB"), makeTestIdentityService())

    override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
        txs.forEach {
            (validatedTransactions as WritableTransactionStorage).addTransaction(it)
        }
    }

    final override val attachments = MockAttachmentStorage()
    override val keyManagementService: KeyManagementService by lazy { MockKeyManagementService(identityService, *arrayOf(initialIdentity.keyPair) + moreKeys) }
    override val vaultService: VaultService get() = throw UnsupportedOperationException()
    override val contractUpgradeService: ContractUpgradeService get() = throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache get() = throw UnsupportedOperationException()
    override val clock: TestClock get() = TestClock(Clock.systemUTC())
    override val myInfo: NodeInfo
        get() {
            return NodeInfo(listOf(NetworkHostAndPort("mock.node.services", 10000)), listOf(initialIdentity.identity), 1, serial = 1L)
        }
    override val transactionVerifierService: TransactionVerifierService get() = InMemoryTransactionVerifierService(2)
    private val mockCordappProvider: MockCordappProvider = MockCordappProvider(cordappLoader, attachments, networkParameters.whitelistedContractImplementations)
    override val cordappProvider: CordappProvider get() = mockCordappProvider

    protected val servicesForResolution: ServicesForResolution get() = ServicesForResolutionImpl(identityService, attachments, cordappProvider, networkParameters, validatedTransactions)

    internal fun makeVaultService(hibernateConfig: HibernateConfiguration, schemaService: SchemaService, database: CordaPersistence): VaultServiceInternal {
        val vaultService = NodeVaultService(clock, keyManagementService, servicesForResolution, hibernateConfig, database)
        HibernateObserver.install(vaultService.rawUpdates, hibernateConfig, schemaService)
        return vaultService
    }

    // This needs to be internal as MutableClassToInstanceMap is a guava type and shouldn't be part of our public API
    /** A map of available [CordaService] implementations */
    internal val cordappServices: MutableClassToInstanceMap<SerializeAsToken> = MutableClassToInstanceMap.create<SerializeAsToken>()

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
        require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
        return cordappServices.getInstance(type)
                ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
    }

    override fun jdbcSession(): Connection = throw UnsupportedOperationException()

    override fun registerUnloadHandler(runOnStop: () -> Unit) = throw UnsupportedOperationException()

    /** Add the given package name to the list of packages which will be scanned for cordapp contract verification code */
    fun addMockCordapp(contractClassName: ContractClassName) {
        mockCordappProvider.addMockCordapp(contractClassName, attachments)
    }

    override fun loadState(stateRef: StateRef) = servicesForResolution.loadState(stateRef)
    override fun loadStates(stateRefs: Set<StateRef>) = servicesForResolution.loadStates(stateRefs)
}

/**
 * Function which can be used to create a mock [CordaService] for use within testing, such as an Oracle.
 */
fun <T : SerializeAsToken> createMockCordaService(serviceHub: MockServices, serviceConstructor: (AppServiceHub) -> T): T {
    class MockAppServiceHubImpl<out T : SerializeAsToken>(val serviceHub: MockServices, serviceConstructor: (AppServiceHub) -> T) : AppServiceHub, ServiceHub by serviceHub {
        val serviceInstance: T = serviceConstructor(this)

        init {
            serviceHub.cordappServices.putInstance(serviceInstance.javaClass, serviceInstance)
        }

        override fun <T> startFlow(flow: FlowLogic<T>): FlowHandle<T> {
            throw UnsupportedOperationException()
        }

        override fun <T> startTrackedFlow(flow: FlowLogic<T>): FlowProgressHandle<T> {
            throw UnsupportedOperationException()
        }
    }
    return MockAppServiceHubImpl(serviceHub, serviceConstructor).serviceInstance
}