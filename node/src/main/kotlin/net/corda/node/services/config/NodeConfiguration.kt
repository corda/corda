package net.corda.node.services.config

import com.typesafe.config.Config
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.node.internal.artemis.CertificateChainCheckPolicy
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.nodeapi.internal.config.*
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.tools.shell.SSHDConfiguration
import java.net.URL
import java.nio.file.Path
import java.time.Duration
import java.util.*


val Int.MB: Long get() = this * 1024L * 1024L

interface NodeConfiguration : NodeSSLConfiguration {
    val myLegalName: CordaX500Name
    val emailAddress: String
    val jmxMonitoringHttpPort: Int?
    val dataSourceProperties: Properties
    val rpcUsers: List<User>
    val security: SecurityConfiguration?
    val devMode: Boolean
    val devModeOptions: DevModeOptions?
    val compatibilityZoneURL: URL?
    val certificateChainCheckPolicies: List<CertChainPolicyConfig>
    val verifierType: VerifierType
    val p2pMessagingRetry: P2PMessagingRetryConfiguration
    val notary: NotaryConfig?
    val additionalNodeInfoPollingFrequencyMsec: Long
    val p2pAddress: NetworkHostAndPort
    val rpcOptions: NodeRpcOptions
    val messagingServerAddress: NetworkHostAndPort?
    val messagingServerExternal: Boolean
    // TODO Move into DevModeOptions
    val useTestClock: Boolean get() = false
    val detectPublicIp: Boolean get() = true
    val sshd: SSHDConfiguration?
    val database: DatabaseConfig
    val noLocalShell: Boolean get() = false
    val transactionCacheSizeBytes: Long get() = defaultTransactionCacheSize
    val attachmentContentCacheSizeBytes: Long get() = defaultAttachmentContentCacheSize
    val attachmentCacheBound: Long get() = defaultAttachmentCacheBound
    // do not change this value without syncing it with ScheduledFlowsDrainingModeTest
    val drainingModePollPeriod: Duration get() = Duration.ofSeconds(5)
    val extraNetworkMapKeys: List<UUID>

    fun validate(): List<String>

    companion object {
        // default to at least 8MB and a bit extra for larger heap sizes
        val defaultTransactionCacheSize: Long = 8.MB + getAdditionalCacheMemory()

        // add 5% of any heapsize over 300MB to the default transaction cache size
        private fun getAdditionalCacheMemory(): Long {
            return Math.max((Runtime.getRuntime().maxMemory() - 300.MB) / 20, 0)
        }

        val defaultAttachmentContentCacheSize: Long = 10.MB
        val defaultAttachmentCacheBound = 1024L
    }
}

data class DevModeOptions(val disableCheckpointChecker: Boolean = false)

fun NodeConfiguration.shouldCheckCheckpoints(): Boolean {
    return this.devMode && this.devModeOptions?.disableCheckpointChecker != true
}

fun NodeConfiguration.shouldStartSSHDaemon() = this.sshd != null
fun NodeConfiguration.shouldStartLocalShell() = !this.noLocalShell && System.console() != null && this.devMode
fun NodeConfiguration.shouldInitCrashShell() = shouldStartLocalShell() || shouldStartSSHDaemon()

data class NotaryConfig(val validating: Boolean,
                        val raft: RaftConfig? = null,
                        val bftSMaRt: BFTSMaRtConfiguration? = null,
                        val custom: Boolean = false
) {
    init {
        require(raft == null || bftSMaRt == null || !custom) {
            "raft, bftSMaRt, and custom configs cannot be specified together"
        }
    }
    val isClusterConfig: Boolean get() = raft != null || bftSMaRt != null
}

data class RaftConfig(val nodeAddress: NetworkHostAndPort, val clusterAddresses: List<NetworkHostAndPort>)

/** @param exposeRaces for testing only, so its default is not in reference.conf but here. */
data class BFTSMaRtConfiguration(
        val replicaId: Int,
        val clusterAddresses: List<NetworkHostAndPort>,
        val debug: Boolean = false,
        val exposeRaces: Boolean = false
) {
    init {
        require(replicaId >= 0) { "replicaId cannot be negative" }
    }
}

/**
 * Currently only used for notarisation requests.
 *
 * When the response doesn't arrive in time, the message is resent to a different notary-replica round-robin
 * in case of clustered notaries.
 */
data class P2PMessagingRetryConfiguration(
        val messageRedeliveryDelay: Duration,
        val maxRetryCount: Int,
        val backoffBase: Double
)

fun Config.parseAsNodeConfiguration(): NodeConfiguration = parseAs<NodeConfigurationImpl>()

data class NodeConfigurationImpl(
        /** This is not retrieved from the config file but rather from a command line argument. */
        override val baseDirectory: Path,
        override val myLegalName: CordaX500Name,
        override val jmxMonitoringHttpPort: Int? = null,
        override val emailAddress: String,
        override val keyStorePassword: String,
        override val trustStorePassword: String,
        override val revocationCheckConfig: RevocationCheckConfig = RevocationCheckConfig(),
        override val dataSourceProperties: Properties,
        override val compatibilityZoneURL: URL? = null,
        override val rpcUsers: List<User>,
        override val security : SecurityConfiguration? = null,
        override val verifierType: VerifierType,
        override val p2pMessagingRetry: P2PMessagingRetryConfiguration,
        override val p2pAddress: NetworkHostAndPort,
        private val rpcAddress: NetworkHostAndPort? = null,
        private val rpcSettings: NodeRpcSettings,
        override val messagingServerAddress: NetworkHostAndPort?,
        override val messagingServerExternal: Boolean = (messagingServerAddress != null),
        override val notary: NotaryConfig?,
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig>,
        override val devMode: Boolean = false,
        override val noLocalShell: Boolean = false,
        override val devModeOptions: DevModeOptions? = null,
        override val useTestClock: Boolean = false,
        override val detectPublicIp: Boolean = true,
        // TODO See TODO above. Rename this to nodeInfoPollingFrequency and make it of type Duration
        override val additionalNodeInfoPollingFrequencyMsec: Long = 5.seconds.toMillis(),
        override val sshd: SSHDConfiguration? = null,
        override val database: DatabaseConfig = DatabaseConfig(initialiseSchema = devMode, exportHibernateJMXStatistics = devMode),
        private val transactionCacheSizeMegaBytes: Int? = null,
        private val attachmentContentCacheSizeMegaBytes: Int? = null,
        override val attachmentCacheBound: Long = NodeConfiguration.defaultAttachmentCacheBound,
        override val extraNetworkMapKeys: List<UUID> = emptyList(),
        // do not use or remove (breaks DemoBench together with rejection of unknown configuration keys during parsing)
        private val h2port: Int  = 0,
        // do not use or remove (used by Capsule)
        private val jarDirs: List<String> = emptyList()
    ) : NodeConfiguration {
    companion object {
        private val logger = loggerFor<NodeConfigurationImpl>()
    }

    override val rpcOptions: NodeRpcOptions = initialiseRpcOptions(rpcAddress, rpcSettings, SslOptions(baseDirectory / "certificates", keyStorePassword, trustStorePassword, revocationCheckConfig))

    private fun initialiseRpcOptions(explicitAddress: NetworkHostAndPort?, settings: NodeRpcSettings, fallbackSslOptions: SSLConfiguration): NodeRpcOptions {
        return when {
            explicitAddress != null -> {
                require(settings.address == null) { "Can't provide top-level rpcAddress and rpcSettings.address (they control the same property)." }
                logger.warn("Top-level declaration of property 'rpcAddress' is deprecated. Please use 'rpcSettings.address' instead.")
                settings.copy(address = explicitAddress)
            }
            else -> settings
        }.asOptions(fallbackSslOptions)
    }

    override fun validate(): List<String> {
        val errors = mutableListOf<String>()
        errors += validateRpcOptions(rpcOptions)
        return errors
    }

    private fun validateRpcOptions(options: NodeRpcOptions): List<String> {
        val errors = mutableListOf<String>()
        if (options.address != null) {
            if (!options.useSsl && options.adminAddress == null) {
                errors += "'rpcSettings.adminAddress': missing. Property is mandatory when 'rpcSettings.useSsl' is false (default)."
            }
        }
        return errors
    }

    override val transactionCacheSizeBytes: Long
        get() = transactionCacheSizeMegaBytes?.MB ?: super.transactionCacheSizeBytes
    override val attachmentContentCacheSizeBytes: Long
        get() = attachmentContentCacheSizeMegaBytes?.MB ?: super.attachmentContentCacheSizeBytes


    init {
        // This is a sanity feature do not remove.
        require(!useTestClock || devMode) { "Cannot use test clock outside of dev mode" }
        require(devModeOptions == null || devMode) { "Cannot use devModeOptions outside of dev mode" }
        require(security == null || rpcUsers.isEmpty()) {
            "Cannot specify both 'rpcUsers' and 'security' in configuration"
        }
    }
}

data class NodeRpcSettings(
        val address: NetworkHostAndPort?,
        val adminAddress: NetworkHostAndPort?,
        val standAloneBroker: Boolean = false,
        val useSsl: Boolean = false,
        val ssl: SslOptions?
) {
    fun asOptions(fallbackSslOptions: SSLConfiguration): NodeRpcOptions {
        return object : NodeRpcOptions {
            override val address = this@NodeRpcSettings.address
            override val adminAddress = this@NodeRpcSettings.adminAddress
            override val standAloneBroker = this@NodeRpcSettings.standAloneBroker
            override val useSsl = this@NodeRpcSettings.useSsl
            override val sslConfig = this@NodeRpcSettings.ssl ?: fallbackSslOptions

            override fun toString(): String {
                return "address: $address, adminAddress: $adminAddress, standAloneBroker: $standAloneBroker, useSsl: $useSsl, sslConfig: $sslConfig"
            }
        }
    }
}

enum class VerifierType {
    InMemory,
    OutOfProcess
}

enum class CertChainPolicyType {
    Any,
    RootMustMatch,
    LeafMustMatch,
    MustContainOneOf,
    UsernameMustMatch
}

data class CertChainPolicyConfig(val role: String, private val policy: CertChainPolicyType, private val trustedAliases: Set<String>) {
    val certificateChainCheckPolicy: CertificateChainCheckPolicy
        get() {
            return when (policy) {
                CertChainPolicyType.Any -> CertificateChainCheckPolicy.Any
                CertChainPolicyType.RootMustMatch -> CertificateChainCheckPolicy.RootMustMatch
                CertChainPolicyType.LeafMustMatch -> CertificateChainCheckPolicy.LeafMustMatch
                CertChainPolicyType.MustContainOneOf -> CertificateChainCheckPolicy.MustContainOneOf(trustedAliases)
                CertChainPolicyType.UsernameMustMatch -> CertificateChainCheckPolicy.UsernameMustMatchCommonName
            }
        }
}

// Supported types of authentication/authorization data providers
enum class AuthDataSourceType {
    // External RDBMS
    DB,

    // Static dataset hard-coded in config
    INMEMORY
}

// Password encryption scheme
enum class PasswordEncryption {

    // Password stored in clear
    NONE,

    // Password salt-hashed using Apache Shiro flexible encryption format
    // [org.apache.shiro.crypto.hash.format.Shiro1CryptFormat]
    SHIRO_1_CRYPT
}

// Subset of Node configuration related to security aspects
data class SecurityConfiguration(val authService: SecurityConfiguration.AuthService) {

    // Configure RPC/Shell users authentication/authorization service
    data class AuthService(val dataSource: AuthService.DataSource,
                           val id: AuthServiceId = defaultAuthServiceId(dataSource.type),
                           val options: AuthService.Options? = null) {

        init {
            require(!(dataSource.type == AuthDataSourceType.INMEMORY &&
                    options?.cache != null)) {
                "No cache supported for INMEMORY data provider"
            }
        }

        fun copyWithAdditionalUser(user: User) = AuthService(dataSource.copyWithAdditionalUser(user), id, options)

        // Optional components: cache
        data class Options(val cache: Options.Cache?) {

            // Cache parameters
            data class Cache(val expireAfterSecs: Long, val maxEntries: Long) {
                init {
                    require(expireAfterSecs >= 0) {
                        "Expected positive value for 'cache.expireAfterSecs'"
                    }
                    require(maxEntries > 0) {
                        "Expected positive value for 'cache.maxEntries'"
                    }
                }
            }
        }

        // Provider of users credentials and permissions data
        data class DataSource(val type: AuthDataSourceType,
                              val passwordEncryption: PasswordEncryption = PasswordEncryption.NONE,
                              val connection: Properties? = null,
                              val users: List<User>? = null) {
            init {
                when (type) {
                    AuthDataSourceType.INMEMORY -> require(users != null && connection == null)
                    AuthDataSourceType.DB -> require(users == null && connection != null)
                }
            }

            fun copyWithAdditionalUser(user: User) : DataSource{
                val extendedList = this.users?.toMutableList()?: mutableListOf()
                extendedList.add(user)
                return DataSource(this.type, this.passwordEncryption, this.connection, listOf(*extendedList.toTypedArray()))
            }
        }

        companion object {
            // If unspecified, we assign an AuthServiceId by default based on the
            // underlying data provider
            fun defaultAuthServiceId(type: AuthDataSourceType) = when (type) {
                AuthDataSourceType.INMEMORY -> AuthServiceId("NODE_CONFIG")
                AuthDataSourceType.DB -> AuthServiceId("REMOTE_DATABASE")
            }

            fun fromUsers(users: List<User>, encryption: PasswordEncryption = PasswordEncryption.NONE) =
                    AuthService(
                            dataSource = DataSource(
                                    type = AuthDataSourceType.INMEMORY,
                                    users = users,
                                    passwordEncryption = encryption),
                            id = AuthServiceId("NODE_CONFIG"))
        }
    }
}
