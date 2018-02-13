package net.corda.testing.node

import com.google.common.collect.MutableClassToInstanceMap
import net.corda.core.cordapp.CordappProvider
import net.corda.core.crypto.*
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.GlobalProperties
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowHandle
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.node.*
import net.corda.core.node.services.*
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.transactions.SignedTransaction
import net.corda.node.VersionInfo
import net.corda.node.internal.configureDatabase
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.api.SchemaService
import net.corda.node.services.api.VaultServiceInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.identity.InMemoryIdentityService
import net.corda.node.services.keys.freshCertificate
import net.corda.node.services.keys.getSigner
import net.corda.node.services.schema.HibernateObserver
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.transactions.InMemoryTransactionVerifierService
import net.corda.node.services.vault.NodeVaultService
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.nodeapi.internal.persistence.HibernateConfiguration
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.DEV_ROOT_CA
import net.corda.testing.core.TestIdentity
import net.corda.testing.services.MockAttachmentStorage
import net.corda.testing.services.MockCordappProvider
import org.bouncycastle.operator.ContentSigner
import rx.Observable
import rx.subjects.PublishSubject
import java.security.KeyPair
import java.security.PrivateKey
import java.security.PublicKey
import java.sql.Connection
import java.time.Clock
import java.util.*

fun makeTestIdentityService(vararg identities: PartyAndCertificate) = InMemoryIdentityService(identities, DEV_ROOT_CA.certificate)

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
        override val validatedTransactions: WritableTransactionStorage,
        override val identityService: IdentityService,
        private val initialIdentity: TestIdentity,
        private val moreKeys: Array<out KeyPair>
) : ServiceHub, StateLoader by validatedTransactions {

    companion object {
        @JvmStatic
        val MOCK_VERSION_INFO = VersionInfo(1, "Mock release", "Mock revision", "Mock Vendor")

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
         * @param moreKeys a list of additional [KeyPair] instances to be used by [MockServices].
         * @param identityService an instance of [IdentityService], see [makeTestIdentityService].
         * @param initialIdentity the first (typically sole) identity the services will represent.
         * @return a pair where the first element is the instance of [CordaPersistence] and the second is [MockServices].
         */
        @JvmStatic
        fun makeTestDatabaseAndMockServices(cordappPackages: List<String>,
                                            identityService: IdentityService,
                                            initialIdentity: TestIdentity,
                                            vararg moreKeys: KeyPair): Pair<CordaPersistence, MockServices> {
            val cordappLoader = CordappLoader.createWithTestPackages(cordappPackages)
            val dataSourceProps = makeTestDataSourceProperties()
            val schemaService = NodeSchemaService(cordappLoader.cordappSchemas)
            val database = configureDatabase(dataSourceProps, DatabaseConfig(), identityService, schemaService)
            val mockService = database.transaction {
                object : MockServices(cordappLoader, identityService, initialIdentity, moreKeys) {
                    override val vaultService: VaultServiceInternal = makeVaultService(database.hibernateConfig, schemaService)

                    override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
                        super.recordTransactions(statesToRecord, txs)
                        // Refactored to use notifyAll() as we have no other unit test for that method with multiple transactions.
                        vaultService.notifyAll(statesToRecord, txs.map { it.tx })
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
    }

    private constructor(cordappLoader: CordappLoader, identityService: IdentityService,
                        initialIdentity: TestIdentity, moreKeys: Array<out KeyPair>)
            : this(cordappLoader, MockTransactionStorage(), identityService, initialIdentity, moreKeys)

    /**
     * Create a mock [ServiceHub] that looks for app code in the given package names, uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>, initialIdentity: TestIdentity, identityService: IdentityService = makeTestIdentityService(), vararg moreKeys: KeyPair) : this(CordappLoader.createWithTestPackages(cordappPackages), identityService, initialIdentity, moreKeys)

    /**
     * Create a mock [ServiceHub] that looks for app code in the given package names, uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>, initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService(), key: KeyPair, vararg moreKeys: KeyPair) : this(cordappPackages, TestIdentity(initialIdentityName, key), identityService, *moreKeys)

    /**
     * Create a mock [ServiceHub] that can't load CorDapp code, which uses the provided identity service
     * (you can get one from [makeTestIdentityService]) and which represents the given identity.
     */
    @JvmOverloads
    constructor(cordappPackages: List<String>, initialIdentityName: CordaX500Name, identityService: IdentityService = makeTestIdentityService()) : this(cordappPackages, TestIdentity(initialIdentityName), identityService)

    /**
     * Create a mock [ServiceHub] that can't load CorDapp code, and which uses a default service identity.
     */
    constructor(cordappPackages: List<String>): this(cordappPackages, CordaX500Name("TestIdentity", "", "GB"), makeTestIdentityService())

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
    constructor(): this(listOf(getCallerPackage()), CordaX500Name("TestIdentity", "", "GB"), makeTestIdentityService())

    override fun recordTransactions(statesToRecord: StatesToRecord, txs: Iterable<SignedTransaction>) {
        txs.forEach {
            validatedTransactions.addTransaction(it)
        }
    }

    final override val attachments = MockAttachmentStorage()
    override val keyManagementService: KeyManagementService by lazy { MockKeyManagementService(identityService, *arrayOf(initialIdentity.keyPair) + moreKeys) }
    override val vaultService: VaultService get() = throw UnsupportedOperationException()
    override val contractUpgradeService: ContractUpgradeService get() = throw UnsupportedOperationException()
    override val networkMapCache: NetworkMapCache get() = throw UnsupportedOperationException()
    override val clock: Clock get() = Clock.systemUTC()
    override val myInfo: NodeInfo
        get() {
            return NodeInfo(emptyList(), listOf(initialIdentity.identity), 1, serial = 1L)
        }
    override val transactionVerifierService: TransactionVerifierService get() = InMemoryTransactionVerifierService(2)
    val mockCordappProvider = MockCordappProvider(cordappLoader, attachments)
    override val cordappProvider: CordappProvider get() = mockCordappProvider

    internal fun makeVaultService(hibernateConfig: HibernateConfiguration, schemaService: SchemaService): VaultServiceInternal {
        val vaultService = NodeVaultService(Clock.systemUTC(), keyManagementService, validatedTransactions, hibernateConfig)
        HibernateObserver.install(vaultService.rawUpdates, hibernateConfig, schemaService)
        return vaultService
    }

    val cordappServices: MutableClassToInstanceMap<SerializeAsToken> = MutableClassToInstanceMap.create<SerializeAsToken>()
    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
        require(type.isAnnotationPresent(CordaService::class.java)) { "${type.name} is not a Corda service" }
        return cordappServices.getInstance(type) ?: throw IllegalArgumentException("Corda service ${type.name} does not exist")
    }

    override fun jdbcSession(): Connection = throw UnsupportedOperationException()

    override fun registerUnloadHandler(runOnStop: () -> Unit) = throw UnsupportedOperationException()
}

class MockKeyManagementService(val identityService: IdentityService,
                               vararg initialKeys: KeyPair) : SingletonSerializeAsToken(), KeyManagementService {
    private val keyStore: MutableMap<PublicKey, PrivateKey> = initialKeys.associateByTo(HashMap(), { it.public }, { it.private })

    override val keys: Set<PublicKey> get() = keyStore.keys

    private val nextKeys = LinkedList<KeyPair>()

    override fun freshKey(): PublicKey {
        val k = nextKeys.poll() ?: generateKeyPair()
        keyStore[k.public] = k.private
        return k.public
    }

    override fun filterMyKeys(candidateKeys: Iterable<PublicKey>): Iterable<PublicKey> = candidateKeys.filter { it in this.keys }

    override fun freshKeyAndCert(identity: PartyAndCertificate, revocationEnabled: Boolean): PartyAndCertificate {
        return freshCertificate(identityService, freshKey(), identity, getSigner(identity.owningKey), revocationEnabled)
    }

    private fun getSigner(publicKey: PublicKey): ContentSigner = getSigner(getSigningKeyPair(publicKey))

    private fun getSigningKeyPair(publicKey: PublicKey): KeyPair {
        val pk = publicKey.keys.firstOrNull { keyStore.containsKey(it) } ?: throw IllegalArgumentException("Public key not found: ${publicKey.toStringShort()}")
        return KeyPair(pk, keyStore[pk]!!)
    }

    override fun sign(bytes: ByteArray, publicKey: PublicKey): DigitalSignature.WithKey {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(bytes)
    }

    override fun sign(signableData: SignableData, publicKey: PublicKey): TransactionSignature {
        val keyPair = getSigningKeyPair(publicKey)
        return keyPair.sign(signableData)
    }
}

open class MockTransactionStorage : WritableTransactionStorage, SingletonSerializeAsToken() {
    override fun track(): DataFeed<List<SignedTransaction>, SignedTransaction> {
        return DataFeed(txns.values.toList(), _updatesPublisher)
    }

    private val txns = HashMap<SecureHash, SignedTransaction>()

    private val _updatesPublisher = PublishSubject.create<SignedTransaction>()

    override val updates: Observable<SignedTransaction>
        get() = _updatesPublisher

    private fun notify(transaction: SignedTransaction) = _updatesPublisher.onNext(transaction)

    override fun addTransaction(transaction: SignedTransaction): Boolean {
        val recorded = txns.putIfAbsent(transaction.id, transaction) == null
        if (recorded) {
            notify(transaction)
        }
        return recorded
    }

    override fun getTransaction(id: SecureHash): SignedTransaction? = txns[id]
}

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