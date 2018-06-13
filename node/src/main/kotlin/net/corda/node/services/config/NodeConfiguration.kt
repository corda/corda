/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.core.context.AuthServiceId
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.loggerFor
import net.corda.core.utilities.seconds
import net.corda.node.internal.artemis.CertificateChainCheckPolicy
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.nodeapi.BrokerRpcSslOptions
import net.corda.nodeapi.internal.config.NodeSSLConfiguration
import net.corda.nodeapi.internal.config.UnknownConfigKeysPolicy
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.config.parseAs
import net.corda.nodeapi.internal.persistence.CordaPersistence.DataSourceConfigTag
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.tools.shell.SSHDConfiguration
import org.slf4j.Logger
import sun.security.x509.X500Name
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
    val networkServices: NetworkServicesConfig?
    val certificateChainCheckPolicies: List<CertChainPolicyConfig>
    val verifierType: VerifierType
    val flowTimeout: FlowTimeoutConfiguration
    val notary: NotaryConfig?
    val additionalNodeInfoPollingFrequencyMsec: Long
    val p2pAddress: NetworkHostAndPort
    val rpcOptions: NodeRpcOptions
    val messagingServerAddress: NetworkHostAndPort?
    val messagingServerExternal: Boolean
    val enterpriseConfiguration: EnterpriseConfiguration
    // TODO Move into DevModeOptions
    val useTestClock: Boolean get() = false
    val lazyBridgeStart: Boolean
    val detectPublicIp: Boolean get() = true
    val sshd: SSHDConfiguration?
    val database: DatabaseConfig
    val relay: RelayConfiguration?
    val noLocalShell: Boolean get() = false
    val transactionCacheSizeBytes: Long get() = defaultTransactionCacheSize
    val attachmentContentCacheSizeBytes: Long get() = defaultAttachmentContentCacheSize
    val attachmentCacheBound: Long get() = defaultAttachmentCacheBound
    val graphiteOptions: GraphiteOptions? get() = null

    // do not change this value without syncing it with ScheduledFlowsDrainingModeTest
    val drainingModePollPeriod: Duration get() = Duration.ofSeconds(5)
    val extraNetworkMapKeys: List<UUID>
    val tlsCertCrlDistPoint: URL?
    val tlsCertCrlIssuer: String?

    fun validate(): List<String>

    companion object {
        // default to at least 8MB and a bit extra for larger heap sizes
        val defaultTransactionCacheSize: Long = 8.MB + getAdditionalCacheMemory()

        // add 5% of any heapsize over 300MB to the default transaction cache size
        private fun getAdditionalCacheMemory(): Long {
            return Math.max((Runtime.getRuntime().maxMemory() - 300.MB) / 20, 0)
        }

        val defaultAttachmentContentCacheSize: Long = 10.MB
        const val defaultAttachmentCacheBound = 1024L
    }
}

data class DevModeOptions(val disableCheckpointChecker: Boolean = false, val allowCompatibilityZone: Boolean = false)

data class GraphiteOptions(
        val server: String,
        val port: Int,
        val prefix: String? = null, // defaults to org name and ip address when null
        val sampleInvervallSeconds: Long = 60
)

fun NodeConfiguration.shouldCheckCheckpoints(): Boolean {
    return this.devMode && this.devModeOptions?.disableCheckpointChecker != true
}

fun NodeConfiguration.shouldStartSSHDaemon() = this.sshd != null
fun NodeConfiguration.shouldStartLocalShell() = !this.noLocalShell && System.console() != null && this.devMode
fun NodeConfiguration.shouldInitCrashShell() = shouldStartLocalShell() || shouldStartSSHDaemon()

data class NotaryConfig(val validating: Boolean,
                        val raft: RaftConfig? = null,
                        val bftSMaRt: BFTSMaRtConfiguration? = null,
                        val custom: Boolean = false,
                        val mysql: MySQLConfiguration? = null,
                        val serviceLegalName: CordaX500Name? = null
) {
    init {
        require(raft == null || bftSMaRt == null || !custom || mysql == null) {
            "raft, bftSMaRt, custom, and mysql configs cannot be specified together"
        }
    }

    val isClusterConfig: Boolean get() = raft != null || bftSMaRt != null || mysql != null
}

data class MySQLConfiguration(
        val dataSource: Properties,
        /**
         * Number of times to attempt to reconnect to the database.
         */
        val connectionRetries: Int = 2, // Default value for a 3 server cluster.
        /**
         * Time increment between re-connection attempts.
         *
         * The total back-off duration is calculated as: backOffIncrement * backOffBase ^ currentRetryCount
         */
        val backOffIncrement: Int = 500,
        /** Exponential back-off multiplier base. */
        val backOffBase: Double = 1.5,
        /** The maximum number of transactions processed in a single batch. */
        val maxBatchSize: Int = 500,
        /** The maximum combined number of input states processed in a single batch. */
        val maxBatchInputStates: Int = 10_000,
        /** A batch will be processed after a specified timeout even if it has not yet reached full capacity. */
        val batchTimeoutMs: Long = 200,
        /**
         * The maximum number of commit requests in flight. Once the capacity is reached the service will block on
         * further commit requests.
         */
        val maxQueueSize: Int = 100_000
        ) {
    init {
        require(connectionRetries >= 0) { "connectionRetries cannot be negative" }
    }
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
 * Used as an alternative to the older compatibilityZoneURL to allow the doorman and network map
 * services for a node to be configured as different URLs. Cannot be set at the same time as the
 * compatibilityZoneURL, and will be defaulted (if not set) to both point at the configured
 * compatibilityZoneURL.
 *
 * @property doormanURL The URL of the tls certificate signing service.
 * @property networkMapURL The URL of the Network Map service.
 * @property inferred Non user setting that indicates weather the Network Services configuration was
 * set explicitly ([inferred] == false) or weather they have been inferred via the compatibilityZoneURL parameter
 * ([inferred] == true) where both the network map and doorman are running on the same endpoint. Only one,
 * compatibilityZoneURL or networkServices, can be set at any one time.
 */
data class NetworkServicesConfig(
        val doormanURL: URL,
        val networkMapURL: URL,
        val inferred : Boolean = false
)

/**
 * Currently only used for notarisation requests.
 *
 * Specifies the configuration for timing out and restarting a [TimedFlow].
 */
data class FlowTimeoutConfiguration(
        val timeout: Duration,
        val maxRestartCount: Int,
        val backoffBase: Double
)

fun Config.parseAsNodeConfiguration(onUnknownKeys: ((Set<String>, logger: Logger) -> Unit) = UnknownConfigKeysPolicy.FAIL::handle): NodeConfiguration = parseAs<NodeConfigurationImpl>(onUnknownKeys)

data class NodeConfigurationImpl(
        /** This is not retrieved from the config file but rather from a command line argument. */
        override val baseDirectory: Path,
        override val myLegalName: CordaX500Name,
        override val jmxMonitoringHttpPort: Int? = null,
        override val emailAddress: String,
        override val keyStorePassword: String,
        override val trustStorePassword: String,
        override val crlCheckSoftFail: Boolean,
        override val dataSourceProperties: Properties,
        override val compatibilityZoneURL: URL? = null,
        override var networkServices: NetworkServicesConfig? = null,
        override val tlsCertCrlDistPoint: URL? = null,
        override val tlsCertCrlIssuer: String? = null,
        override val rpcUsers: List<User>,
        override val security: SecurityConfiguration? = null,
        override val verifierType: VerifierType,
        override val flowTimeout: FlowTimeoutConfiguration,
        override val p2pAddress: NetworkHostAndPort,
        private val rpcAddress: NetworkHostAndPort? = null,
        private val rpcSettings: NodeRpcSettings,
        override val relay: RelayConfiguration?,
        // TODO This field is slightly redundant as p2pAddress is sufficient to hold the address of the node's MQ broker.
        // Instead this should be a Boolean indicating whether that broker is an internal one started by the node or an external one
        override val messagingServerAddress: NetworkHostAndPort?,
        override val messagingServerExternal: Boolean = (messagingServerAddress != null),
        override val enterpriseConfiguration: EnterpriseConfiguration,
        override val notary: NotaryConfig?,
        @Deprecated("Do not configure")
        override val certificateChainCheckPolicies: List<CertChainPolicyConfig> = emptyList(),
        override val devMode: Boolean = false,
        override val noLocalShell: Boolean = false,
        override val devModeOptions: DevModeOptions? = null,
        override val useTestClock: Boolean = false,
        override val lazyBridgeStart: Boolean = true,
        override val detectPublicIp: Boolean = true,
        // TODO See TODO above. Rename this to nodeInfoPollingFrequency and make it of type Duration
        override val additionalNodeInfoPollingFrequencyMsec: Long = 5.seconds.toMillis(),
        override val sshd: SSHDConfiguration? = null,
        override val database: DatabaseConfig = DatabaseConfig(exportHibernateJMXStatistics = devMode),
        private val transactionCacheSizeMegaBytes: Int? = null,
        private val attachmentContentCacheSizeMegaBytes: Int? = null,
        override val attachmentCacheBound: Long = NodeConfiguration.defaultAttachmentCacheBound,
        override val graphiteOptions: GraphiteOptions? = null,
        override val extraNetworkMapKeys: List<UUID> = emptyList(),
        // do not use or remove (breaks DemoBench together with rejection of unknown configuration keys during parsing)
        private val h2port: Int = 0,
        // do not use or remove (used by Capsule)
        private val jarDirs: List<String> = emptyList()
) : NodeConfiguration {
    companion object {
        private val logger = loggerFor<NodeConfigurationImpl>()
    }

    override val rpcOptions: NodeRpcOptions = initialiseRpcOptions(rpcAddress, rpcSettings, BrokerRpcSslOptions(baseDirectory / "certificates" / "nodekeystore.jks", keyStorePassword))

    private fun initialiseRpcOptions(explicitAddress: NetworkHostAndPort?, settings: NodeRpcSettings, fallbackSslOptions: BrokerRpcSslOptions): NodeRpcOptions {
        return when {
            explicitAddress != null -> {
                require(settings.address == null) { "Can't provide top-level rpcAddress and rpcSettings.address (they control the same property)." }
                logger.warn("Top-level declaration of property 'rpcAddress' is deprecated. Please use 'rpcSettings.address' instead.")

                settings.copy(address = explicitAddress)
            }
            else -> {
                settings.address ?: throw ConfigException.Missing("rpcSettings.address")
                settings
            }
        }.asOptions(fallbackSslOptions)
    }

    private fun validateTlsCertCrlConfig(): List<String> {
        val errors = mutableListOf<String>()
        if (tlsCertCrlIssuer != null) {
            if (tlsCertCrlDistPoint == null) {
                errors += "tlsCertCrlDistPoint needs to be specified when tlsCertCrlIssuer is not NULL"
            }
            try {
                X500Name(tlsCertCrlIssuer)
            } catch (e: Exception) {
                errors += "Error when parsing tlsCertCrlIssuer: ${e.message}"
            }
        }
        if (!crlCheckSoftFail && tlsCertCrlDistPoint == null) {
            errors += "tlsCertCrlDistPoint needs to be specified when crlCheckSoftFail is FALSE"
        }
        return errors
    }

    override fun validate(): List<String> {
        val errors = mutableListOf<String>()
        errors += validateDevModeOptions()
        errors += validateRpcOptions(rpcOptions)
        errors += validateTlsCertCrlConfig()
        errors += validateNetworkServices()
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

    private fun validateDevModeOptions(): List<String> {
        if (devMode) {
            compatibilityZoneURL?.let {
                if (devModeOptions?.allowCompatibilityZone != true) {
                    return listOf("'compatibilityZoneURL': present. Property cannot be set when 'devMode' is true unless devModeOptions.allowCompatibilityZone is also true")
                }
            }

            // if compatibiliZoneURL is set then it will be copied into the networkServices field and thus skipping
            // this check by returning above is fine.
            networkServices?.let {
                if (devModeOptions?.allowCompatibilityZone != true) {
                    return listOf("'networkServices': present. Property cannot be set when 'devMode' is true unless devModeOptions.allowCompatibilityZone is also true")
                }
            }
        }
        return emptyList()
    }

    private fun validateNetworkServices(): List<String> {
        val errors = mutableListOf<String>()

        if (compatibilityZoneURL != null && networkServices != null && !(networkServices!!.inferred)) {
            errors += "Cannot configure both compatibilityZoneUrl and networkServices simultaneously"
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

        // ensure our datasource configuration is sane
        require(dataSourceProperties.get("autoCommit") != true) { "Datbase auto commit cannot be enabled, Corda requires transactional behaviour" }
        dataSourceProperties.set("autoCommit", false)
        if (dataSourceProperties.get("transactionIsolation") == null) {
            dataSourceProperties["transactionIsolation"] = database.transactionIsolationLevel.jdbcString
        }

        // enforce that SQLServer does not get sent all strings as Unicode - hibernate handles this "cleverly"
        val dataSourceUrl = dataSourceProperties.getProperty(DataSourceConfigTag.DATA_SOURCE_URL, "")
        if (dataSourceUrl.contains(":sqlserver:") && !dataSourceUrl.contains("sendStringParametersAsUnicode", true)) {
            dataSourceProperties[DataSourceConfigTag.DATA_SOURCE_URL] = dataSourceUrl + ";sendStringParametersAsUnicode=false"
        }

        // Adjust connection pool size depending on N=flow thread pool size.
        // If there is no configured pool size set it to N + 1, otherwise check that it's greater than N.
        val flowThreadPoolSize = enterpriseConfiguration.tuning.flowThreadPoolSize
        val maxConnectionPoolSize = dataSourceProperties.getProperty("maximumPoolSize")
        if (maxConnectionPoolSize == null) {
            dataSourceProperties.setProperty("maximumPoolSize", (flowThreadPoolSize + 1).toString())
        } else {
            require(maxConnectionPoolSize.toInt() > flowThreadPoolSize)
        }

        // Check for usage of deprecated config
        if(certificateChainCheckPolicies.isNotEmpty()) {
            logger.warn("""You are configuring certificateChainCheckPolicies. This is a setting that is not used, and will be removed in a future version.
                |Please contact the R3 team on the public slack to discuss your use case.
            """.trimMargin())
        }

        if (compatibilityZoneURL != null && networkServices == null) {
            networkServices = NetworkServicesConfig(compatibilityZoneURL, compatibilityZoneURL, true)
        }
    }
}

data class NodeRpcSettings(
        val address: NetworkHostAndPort?,
        val adminAddress: NetworkHostAndPort?,
        val standAloneBroker: Boolean = false,
        val useSsl: Boolean = false,
        val ssl: BrokerRpcSslOptions?
) {
    fun asOptions(fallbackSslOptions: BrokerRpcSslOptions): NodeRpcOptions {
        return object : NodeRpcOptions {
            override val address = this@NodeRpcSettings.address!!
            override val adminAddress = this@NodeRpcSettings.adminAddress!!
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

@Deprecated("Do not use")
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

            fun copyWithAdditionalUser(user: User): DataSource {
                val extendedList = this.users?.toMutableList() ?: mutableListOf()
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

data class RelayConfiguration(val relayHost: String,
                              val remoteInboundPort: Int,
                              val username: String,
                              val privateKeyFile: Path,
                              val publicKeyFile: Path,
                              val sshPort: Int = 22)
