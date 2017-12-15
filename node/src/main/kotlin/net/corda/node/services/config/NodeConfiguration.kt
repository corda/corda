package net.corda.node.services.config

import com.typesafe.config.Config
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.seconds
import net.corda.node.services.messaging.CertificateChainCheckPolicy
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import java.net.URL
import java.nio.file.Path
import java.util.*

interface NodeConfiguration : NodeSSLConfiguration {
    // myLegalName should be only used in the initial network registration, we should use the name from the certificate instead of this.
    // TODO: Remove this so we don't accidentally use this identity in the code?
    val myLegalName: CordaX500Name
    val emailAddress: String
    val exportJMXto: String
    val dataSourceProperties: Properties
    val rpcUsers: List<User>
    val security: SecurityConfiguration?
    val devMode: Boolean
    val devModeOptions: DevModeOptions?
    val compatibilityZoneURL: URL?
    val certificateChainCheckPolicies: List<CertChainPolicyConfig>
    val verifierType: VerifierType
    val messageRedeliveryDelaySeconds: Int
    val notary: NotaryConfig?
    val activeMQServer: ActiveMqServerConfiguration
    val additionalNodeInfoPollingFrequencyMsec: Long
    // TODO Remove as this is only used by the driver
    val useHTTPS: Boolean
    val p2pAddress: NetworkHostAndPort
    val rpcAddress: NetworkHostAndPort?
    val messagingServerAddress: NetworkHostAndPort?
    // TODO Move into DevModeOptions
    val useTestClock: Boolean get() = false
    val detectPublicIp: Boolean get() = true
    val sshd: SSHDConfiguration?
    val database: DatabaseConfig
    val useAMQPBridges: Boolean get() = true
}

data class DevModeOptions(val disableCheckpointChecker: Boolean = false)

fun NodeConfiguration.shouldCheckCheckpoints(): Boolean {
    return this.devMode && this.devModeOptions?.disableCheckpointChecker != true
}

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

data class BridgeConfiguration(val retryIntervalMs: Long,
                               val maxRetryIntervalMin: Long,
                               val retryIntervalMultiplier: Double)

data class ActiveMqServerConfiguration(val bridge: BridgeConfiguration)

fun Config.parseAsNodeConfiguration(): NodeConfiguration = this.parseAs<NodeConfigurationImpl>()

data class NodeConfigurationImpl(
        /** This is not retrieved from the config file but rather from a command line argument. */
        override val baseDirectory: Path,
        override val myLegalName: CordaX500Name,
        override val emailAddress: String,
        override val keyStorePassword: String,
        override val trustStorePassword: String,
        override val dataSourceProperties: Properties,
        override val compatibilityZoneURL: URL? = null,
        override val rpcUsers: List<User>,
        override val security : SecurityConfiguration? = null,
        override val verifierType: VerifierType,
        // TODO typesafe config supports the notion of durations. Make use of that by mapping it to java.time.Duration.
        // Then rename this to messageRedeliveryDelay and make it of type Duration
        override val messageRedeliveryDelaySeconds: Int = 30,
        override val useHTTPS: Boolean,
        override val p2pAddress: NetworkHostAndPort,
        override val rpcAddress: NetworkHostAndPort?,
        // TODO This field is slightly redundant as p2pAddress is sufficient to hold the address of the node's MQ broker.
        // Instead this should be a Boolean indicating whether that broker is an internal one started by the node or an external one
        override val messagingServerAddress: NetworkHostAndPort?,
        override val notary: NotaryConfig?,
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig>,
        override val devMode: Boolean = false,
        override val devModeOptions: DevModeOptions? = null,
        override val useTestClock: Boolean = false,
        override val detectPublicIp: Boolean = true,
        override val activeMQServer: ActiveMqServerConfiguration,
        // TODO See TODO above. Rename this to nodeInfoPollingFrequency and make it of type Duration
        override val additionalNodeInfoPollingFrequencyMsec: Long = 5.seconds.toMillis(),
        override val sshd: SSHDConfiguration? = null,
        override val database: DatabaseConfig = DatabaseConfig(initialiseSchema = devMode, exportHibernateJMXStatistics = devMode),
        override val useAMQPBridges: Boolean = true
        ) : NodeConfiguration {

    override val exportJMXto: String get() = "http"

    init {
        // This is a sanity feature do not remove.
        require(!useTestClock || devMode) { "Cannot use test clock outside of dev mode" }
        require(devModeOptions == null || devMode) { "Cannot use devModeOptions outside of dev mode" }
        require(security == null || rpcUsers.isEmpty()) {
            "Cannot specify both 'rpcUsers' and 'security' in configuration"
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
    MustContainOneOf
}

data class CertChainPolicyConfig(val role: String, private val policy: CertChainPolicyType, private val trustedAliases: Set<String>) {
    val certificateChainCheckPolicy: CertificateChainCheckPolicy
        get() {
            return when (policy) {
                CertChainPolicyType.Any -> CertificateChainCheckPolicy.Any
                CertChainPolicyType.RootMustMatch -> CertificateChainCheckPolicy.RootMustMatch
                CertChainPolicyType.LeafMustMatch -> CertificateChainCheckPolicy.LeafMustMatch
                CertChainPolicyType.MustContainOneOf -> CertificateChainCheckPolicy.MustContainOneOf(trustedAliases)
            }
        }
}

data class SSHDConfiguration(val port: Int)

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

        // Optional components: cache
        data class Options(val cache: Options.Cache?) {

            // Cache parameters
            data class Cache(val expireAfterSecs: Long, val maxEntries: Long)

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
        }

        companion object {
            // If unspecified, we assign an AuthServiceId by default based on the
            // underlying data provider
            fun defaultAuthServiceId(type: AuthDataSourceType) = when (type) {
                AuthDataSourceType.INMEMORY -> AuthServiceId("NODE_CONFIG")
                AuthDataSourceType.DB -> AuthServiceId("REMOTE_DATABASE")
            }

            fun fromUsers(users: List<User>) = AuthService(
                    dataSource = DataSource(
                            type = AuthDataSourceType.INMEMORY,
                            users = users,
                            passwordEncryption = PasswordEncryption.NONE),
                    id = AuthServiceId("NODE_CONFIG"))
        }
    }
}