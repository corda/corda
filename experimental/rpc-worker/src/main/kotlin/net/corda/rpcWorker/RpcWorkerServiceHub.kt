package net.corda.rpcWorker

import com.codahale.metrics.MetricRegistry
import com.jcabi.manifests.Manifests
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.core.contracts.*
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.sign
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.AttachmentTrustCalculator
import net.corda.core.node.NodeInfo
import net.corda.core.node.services.ContractUpgradeService
import net.corda.core.node.services.TransactionVerifierService
import net.corda.core.serialization.SerializeAsToken
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.effectiveSerializationEnv
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.node.CordaClock
import net.corda.node.SimpleClock
import net.corda.node.VersionInfo
import net.corda.node.internal.*
import net.corda.node.internal.cordapp.CordappConfigFileProvider
import net.corda.node.internal.cordapp.CordappProviderImpl
import net.corda.node.internal.cordapp.JarScanningCordappLoader
import net.corda.node.serialization.amqp.AMQPServerSerializationScheme
import net.corda.node.serialization.kryo.KRYO_CHECKPOINT_CONTEXT
import net.corda.node.services.api.AuditService
import net.corda.node.services.api.MonitoringService
import net.corda.node.services.api.ServiceHubInternal
import net.corda.node.services.api.WritableTransactionStorage
import net.corda.node.services.attachments.NodeAttachmentTrustCalculator
import net.corda.node.services.config.NetworkParameterAcceptanceSettings
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.identity.PersistentIdentityService
import net.corda.node.services.keys.BasicHSMKeyManagementService
import net.corda.node.services.messaging.MessagingService
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.services.network.NetworkMapUpdater
import net.corda.node.services.network.NodeInfoWatcher
import net.corda.node.services.network.PersistentNetworkMapCache
import net.corda.node.services.persistence.*
import net.corda.node.services.schema.NodeSchemaService
import net.corda.node.services.vault.NodeVaultService
import net.corda.node.utilities.EnterpriseNamedCacheFactory
import net.corda.node.utilities.profiling.getTracingConfig
import net.corda.nodeapi.internal.NodeInfoAndSigned
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceFactory
import net.corda.nodeapi.internal.cryptoservice.ManagedCryptoService
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.serialization.internal.*
import org.slf4j.Logger
import rx.schedulers.Schedulers
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.sql.Connection
import java.time.Clock
import java.time.Duration
import java.util.*
import java.util.function.Consumer
import javax.persistence.EntityManager

class RpcWorkerServiceHub(override val configuration: NodeConfiguration,
                          override val myInfo: NodeInfo,
                          private val signedNetworkParameters: NetworkParametersReader.NetworkParametersAndSigned,
                          private val ourKeyPair: KeyPair,
                          private val trustRoot: X509Certificate,
                          private val nodeCa: X509Certificate) : ServiceHubInternal, SingletonSerializeAsToken() {

    override val clock: CordaClock = SimpleClock(Clock.systemUTC())
    private val versionInfo = getVersionInfo()
    private val cordappLoader = JarScanningCordappLoader.fromDirectories(configuration.cordappDirectories, versionInfo)

    private val log: Logger get() = staticLog

    companion object {
        private val staticLog = contextLogger()
    }

    private val runOnStop = ArrayList<() -> Any?>()

    private val metricRegistry = MetricRegistry()
    override val cacheFactory = EnterpriseNamedCacheFactory(configuration.enterpriseConfiguration.getTracingConfig()).bindWithConfig(configuration).bindWithMetrics(metricRegistry)

    override val schemaService = NodeSchemaService(cordappLoader.cordappSchemas)
    override val identityService = PersistentIdentityService(cacheFactory)
    override val database: CordaPersistence = createCordaPersistence(
            configuration.database,
            identityService::wellKnownPartyFromX500Name,
            identityService::wellKnownPartyFromAnonymous,
            schemaService,
            cacheFactory,
            cordappLoader.appClassLoader
    )

    init {
        // TODO Break cyclic dependency
        identityService.database = database
    }

    override val networkMapCache = PersistentNetworkMapCache(cacheFactory, database, identityService)
    @Suppress("LeakingThis")
    override val validatedTransactions: WritableTransactionStorage = DBTransactionStorage(database, cacheFactory)
    private val networkMapClient: NetworkMapClient? = configuration.networkServices?.let { NetworkMapClient(it, versionInfo) }
    override val attachments = NodeAttachmentService(metricRegistry, cacheFactory, database)
    override val attachmentTrustCalculator = makeAttachmentTrustCalculator(configuration, database)
    override val cordappProvider = CordappProviderImpl(cordappLoader, CordappConfigFileProvider(emptyList()), attachments)

    private val pkToIdCache = PublicKeyToOwningIdentityCacheImpl(database, cacheFactory)
    private val cryptoService : ManagedCryptoService = CryptoServiceFactory.makeManagedCryptoService(
            configuration.cryptoServiceName ?: SupportedCryptoServices.BC_SIMPLE,
            configuration.myLegalName,
            configuration.signingCertificateStore,
            configuration.cryptoServiceConf,
            configuration.cryptoServiceTimeout
    ).closeOnStop()
    @Suppress("LeakingThis")
    override val keyManagementService = BasicHSMKeyManagementService(cacheFactory, identityService, database, cryptoService)
    override val networkParametersService = DBNetworkParametersStorage(cacheFactory, database, networkMapClient)
    private val servicesForResolution = ServicesForResolutionImpl(identityService, attachments, cordappProvider, networkParametersService, validatedTransactions)
    @Suppress("LeakingThis")
    override val vaultService = NodeVaultService(clock, keyManagementService, servicesForResolution, database, schemaService, cacheFactory, cordappLoader.appClassLoader)
    override val nodeProperties = NodePropertiesPersistentStore(StubbedNodeUniqueIdProvider::value, database, cacheFactory)
    override val monitoringService = MonitoringService(metricRegistry)
    override val networkMapUpdater = NetworkMapUpdater(
            networkMapCache,
            NodeInfoWatcher(
                    configuration.baseDirectory,
                    @Suppress("LeakingThis")
                    Schedulers.io(),
                    Duration.ofMillis(configuration.additionalNodeInfoPollingFrequencyMsec)
            ),
            networkMapClient,
            configuration.baseDirectory,
            configuration.extraNetworkMapKeys,
            networkParametersService
    ).closeOnStop()

    override val networkParameters = signedNetworkParameters.networkParameters

    override val transactionVerifierService: TransactionVerifierService
        get() {
            throw NotImplementedError()
        }
    override val contractUpgradeService: ContractUpgradeService
        get() {
            throw NotImplementedError()
        }
    override val auditService: AuditService
        get() {
            throw NotImplementedError()
        }

    // TODO schedulerService

    override val rpcFlows = ArrayList<Class<out FlowLogic<*>>>()
    override val stateMachineRecordedTransactionMapping = DBTransactionMappingStorage(database)
    override val networkService: MessagingService
        get() {
            throw NotImplementedError()
        }

    private fun <T : AutoCloseable> T.closeOnStop(): T {
        runOnStop += this::close
        return this
    }

    override fun getFlowFactory(initiatingFlowClass: Class<out FlowLogic<*>>): InitiatedFlowFactory<*>? {
        throw NotImplementedError()
    }

    override fun loadState(stateRef: StateRef): TransactionState<*> {
        return servicesForResolution.loadState(stateRef)
    }

    override fun loadStates(stateRefs: Set<StateRef>): Set<StateAndRef<ContractState>> {
        return servicesForResolution.loadStates(stateRefs)
    }

    override fun <T : SerializeAsToken> cordaService(type: Class<T>): T {
        throw NotImplementedError()
    }

    override fun jdbcSession(): Connection {
        throw NotImplementedError()
    }

    override fun <T : Any?> withEntityManager(block: EntityManager.() -> T): T {
        throw NotImplementedError()
    }

    override fun withEntityManager(block: Consumer<EntityManager>) {
        throw NotImplementedError()
    }

    override fun registerUnloadHandler(runOnStop: () -> Unit) {
        this.runOnStop += runOnStop
    }

    override fun loadContractAttachment(stateRef: StateRef): Attachment {
        return servicesForResolution.loadContractAttachment(stateRef)
    }

    private fun getVersionInfo(): VersionInfo {
        // Manifest properties are only available if running from the corda jar
        fun manifestValue(name: String): String? = if (Manifests.exists(name)) Manifests.read(name) else null

        return VersionInfo(
                manifestValue("Corda-Platform-Version")?.toInt() ?: 1,
                manifestValue("Corda-Release-Version") ?: "Unknown",
                manifestValue("Corda-Revision") ?: "Unknown",
                manifestValue("Corda-Vendor") ?: "Unknown"
        )
    }

    private fun makeAttachmentTrustCalculator(
        configuration: NodeConfiguration,
        database: CordaPersistence
    ): AttachmentTrustCalculator {
        val blacklistedAttachmentSigningKeys: List<SecureHash> =
            parseSecureHashConfiguration(configuration.blacklistedAttachmentSigningKeys) { "Error while adding signing key $it to blacklistedAttachmentSigningKeys" }
        return NodeAttachmentTrustCalculator(
            attachmentStorage = attachments,
            database = database,
            cacheFactory = cacheFactory,
            blacklistedAttachmentSigningKeys = blacklistedAttachmentSigningKeys
        )
    }

    private fun parseSecureHashConfiguration(unparsedConfig: List<String>, errorMessage: (String) -> String): List<SecureHash.SHA256> {
        return unparsedConfig.map {
            try {
                SecureHash.parse(it)
            } catch (e: IllegalArgumentException) {
                log.error("${errorMessage(it)} due to - ${e.message}", e)
                throw e
            }
        }
    }

    private fun initialiseSerialization() {
        val serializationExists = try {
            effectiveSerializationEnv
            true
        } catch (e: IllegalStateException) {
            false
        }
        if (!serializationExists) {
            val classloader = cordappLoader.appClassLoader
            nodeSerializationEnv = SerializationEnvironment.with(
                    SerializationFactoryImpl().apply {
                        registerScheme(AMQPServerSerializationScheme(cordappLoader.cordapps))
                        registerScheme(AMQPClientSerializationScheme(cordappLoader.cordapps))
                    },
                    p2pContext = AMQP_P2P_CONTEXT.withClassLoader(classloader),
                    rpcServerContext = AMQP_RPC_SERVER_CONTEXT.withClassLoader(classloader),
                    storageContext = AMQP_STORAGE_CONTEXT.withClassLoader(classloader),
                    checkpointContext = KRYO_CHECKPOINT_CONTEXT.withClassLoader(classloader),
                    rpcClientContext = AMQP_RPC_CLIENT_CONTEXT.withClassLoader(classloader))
        }
    }

    val rpcOps = CordaRpcWorkerOps(this, {})

    fun start() {
        log.info("Rpc Worker starting up ...")

        initialiseSerialization()

        networkMapClient?.start(trustRoot)

        database.startHikariPool(configuration.dataSourceProperties, configuration.database, schemaService.schemaOptions.keys, ourName = myInfo.legalIdentities.first().name)
        identityService.start(trustRoot, listOf(myInfo.legalIdentitiesAndCerts.first().certificate, nodeCa), listOf(), pkToIdCache)

        // networkParametersService needs the database started
        networkParametersService.setCurrentParameters(signedNetworkParameters.signed, trustRoot)
        runOnStop += { rpcOps.shutdown() }
        rpcOps.start()

        database.transaction {
            networkMapCache.start(networkParameters.notaries)
            networkMapCache.addNode(myInfo)
        }

        val nodeInfoAndSigned = NodeInfoAndSigned(myInfo) { _, serialised ->
            ourKeyPair.private.sign(serialised.bytes)
        }
        identityService.ourNames = myInfo.legalIdentities.map { it.name }.toSet()

        networkMapUpdater.start(
            trustRoot,
            signedNetworkParameters.signed.raw.hash,
            nodeInfoAndSigned.signed,
            networkParameters,
            keyManagementService,
            NetworkParameterAcceptanceSettings())

        database.transaction {
            identityService.loadIdentities(myInfo.legalIdentitiesAndCerts)
            attachments.start()
            nodeProperties.start()
            keyManagementService.start(setOf(ourKeyPair))
            vaultService.start()
        }
    }

    fun stop() {
        for (toRun in runOnStop.reversed()) {
            toRun()
        }
        runOnStop.clear()
    }
}
