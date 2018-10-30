package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigObject
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.core.identity.CordaX500Name
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.services.config.*
import net.corda.node.services.config.rpc.NodeRpcOptions
import net.corda.nodeapi.internal.config.User
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.tools.shell.SSHDConfiguration
import java.net.URL
import java.time.Duration
import java.util.*

// TODO sollecitom move
internal object UserSpec : Configuration.Specification<User>("User") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<User> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object SecurityConfigurationSpec : Configuration.Specification<SecurityConfiguration>("SecurityConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<SecurityConfiguration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object DevModeOptionsSpec : Configuration.Specification<DevModeOptions>("DevModeOptions") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<DevModeOptions> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object NetworkServicesConfigSpec : Configuration.Specification<NetworkServicesConfig>("NetworkServicesConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NetworkServicesConfig> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
@Suppress("DEPRECATION")
internal object CertChainPolicyConfigSpec : Configuration.Specification<CertChainPolicyConfig>("CertChainPolicyConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<CertChainPolicyConfig> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object FlowTimeoutConfigurationSpec : Configuration.Specification<FlowTimeoutConfiguration>("FlowTimeoutConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<FlowTimeoutConfiguration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object NotaryConfigSpec : Configuration.Specification<NotaryConfig>("NotaryConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NotaryConfig> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object RpcOptionsSpec : Configuration.Specification<NodeRpcOptions>("NodeRpcOptions") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<NodeRpcOptions> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object SSHDConfigurationSpec : Configuration.Specification<SSHDConfiguration>("SSHDConfiguration") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<SSHDConfiguration> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

// TODO sollecitom move
internal object DatabaseConfigSpec : Configuration.Specification<DatabaseConfig>("DatabaseConfig") {

    // TODO sollecitom add fields here

    override fun parseValid(configuration: Config): Valid<DatabaseConfig> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

internal object V1NodeConfigurationSpec : Configuration.Specification<NodeConfiguration>("NodeConfiguration") {

    private val myLegalName by string().mapValid(::toLegalName)
    private val emailAddress by string()
    private val jmxMonitoringHttpPort by int().optional()
    private val dataSourceProperties by nestedObject().map(::toProperties)
    private val rpcUsers by nested(UserSpec).list()
    private val security by nested(SecurityConfigurationSpec).optional()
    private val devMode by boolean()
    private val devModeOptions by nested(DevModeOptionsSpec).optional()
    private val compatibilityZoneURL by string().mapValid(::toUrl).optional()
    private val networkServices by nested(NetworkServicesConfigSpec).optional()
    private val certificateChainCheckPolicies by nested(CertChainPolicyConfigSpec).list()
    private val verifierType by enum(VerifierType::class)
    private val flowTimeout by nested(FlowTimeoutConfigurationSpec)
    private val notary by nested(NotaryConfigSpec)
    private val additionalNodeInfoPollingFrequencyMsec by long()
    private val p2pAddress by string().mapValid(::toNetworkHostAndPort)
    private val additionalP2PAddresses by string().mapValid(::toNetworkHostAndPort).list()
    private val rpcOptions by nested(RpcOptionsSpec)
    private val messagingServerAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val messagingServerExternal by boolean()
    private val useTestClock by boolean().optional(false)
    private val lazyBridgeStart by boolean()
    private val detectPublicIp by boolean().optional(true)
    private val sshd by nested(SSHDConfigurationSpec).optional()
    private val database by nested(DatabaseConfigSpec)
    private val noLocalShell by boolean().optional(false)
    // TODO sollecitom perhaps create a Property.Definition.Optional interface exposing a `withDefault(<non-null> defaultValue: TYPE): Property.Definition<TYPE>`
    private val transactionCacheSizeBytes by long().optional(NodeConfiguration.defaultTransactionCacheSize)
    private val attachmentContentCacheSizeBytes by long().optional(NodeConfiguration.defaultAttachmentContentCacheSize)
    private val attachmentCacheBound by long().optional(NodeConfiguration.defaultAttachmentCacheBound)
    private val drainingModePollPeriod by duration().optional(Duration.ofSeconds(5))

    override fun parseValid(configuration: Config): Valid<NodeConfiguration> {

        TODO("not implemented")
    }

    private fun toLegalName(rawValue: String): Valid<CordaX500Name> {

        TODO("not implemented")
    }

    private fun toProperties(rawValue: ConfigObject): Properties {

        val properties = Properties()
        rawValue.entries.forEach { (key, value) ->
            properties[key] = value.unwrapped()
        }
        return properties
    }

    private fun toUrl(rawValue: String): Valid<URL> {

        TODO("not implemented")
    }

    private fun toNetworkHostAndPort(rawValue: String): Valid<NetworkHostAndPort> {

        TODO("not implemented")
    }

//    val extraNetworkMapKeys: List<UUID>
//    val tlsCertCrlDistPoint: URL?
//    val tlsCertCrlIssuer: X500Principal?
//    val effectiveH2Settings: NodeH2Settings?
//    val flowMonitorPeriodMillis: Duration get() = DEFAULT_FLOW_MONITOR_PERIOD_MILLIS
//    val flowMonitorSuspensionLoggingThresholdMillis: Duration get() = DEFAULT_FLOW_MONITOR_SUSPENSION_LOGGING_THRESHOLD_MILLIS
//    val crlCheckSoftFail: Boolean
//    val jmxReporterType: JmxReporterType? get() = defaultJmxReporterType
//
//    val baseDirectory: Path
//    val certificatesDirectory: Path
//    val signingCertificateStore: FileBasedCertificateStoreSupplier
//    val p2pSslOptions: MutualSslConfiguration
//
//    val cordappDirectories: List<Path>
//    val flowOverrides: FlowOverrideConfig?

//    private fun validateTlsCertCrlConfig(): List<String> {
//        val errors = mutableListOf<String>()
//        if (tlsCertCrlIssuer != null) {
//            if (tlsCertCrlDistPoint == null) {
//                errors += "tlsCertCrlDistPoint needs to be specified when tlsCertCrlIssuer is not NULL"
//            }
//        }
//        if (!crlCheckSoftFail && tlsCertCrlDistPoint == null) {
//            errors += "tlsCertCrlDistPoint needs to be specified when crlCheckSoftFail is FALSE"
//        }
//        return errors
//    }

//    override fun validate(): List<String> {
//        val errors = mutableListOf<String>()
//        errors += validateDevModeOptions()
//        val rpcSettingsErrors = validateRpcSettings(rpcSettings)
//        errors += rpcSettingsErrors
//        if (rpcSettingsErrors.isEmpty()) {
//            // Forces lazy property to initialise in order to throw exceptions
//            rpcOptions
//        }
//        errors += validateTlsCertCrlConfig()
//        errors += validateNetworkServices()
//        errors += validateH2Settings()
//        return errors
//    }
//
//    private fun validateH2Settings(): List<String> {
//        val errors = mutableListOf<String>()
//        if (h2port != null && h2Settings != null) {
//            errors += "Cannot specify both 'h2port' and 'h2Settings' in configuration"
//        }
//        return errors
//    }
//
//    private fun validateRpcSettings(options: NodeRpcSettings): List<String> {
//        val errors = mutableListOf<String>()
//        if (options.adminAddress == null) {
//            errors += "'rpcSettings.adminAddress': missing"
//        }
//        if (options.useSsl && options.ssl == null) {
//            errors += "'rpcSettings.ssl': missing (rpcSettings.useSsl was set to true)."
//        }
//        return errors
//    }
//
//    private fun validateDevModeOptions(): List<String> {
//        if (devMode) {
//            compatibilityZoneURL?.let {
//                if (devModeOptions?.allowCompatibilityZone != true) {
//                    return listOf("'compatibilityZoneURL': present. Property cannot be set when 'devMode' is true unless devModeOptions.allowCompatibilityZone is also true")
//                }
//            }
//
//            // if compatibiliZoneURL is set then it will be copied into the networkServices field and thus skipping
//            // this check by returning above is fine.
//            networkServices?.let {
//                if (devModeOptions?.allowCompatibilityZone != true) {
//                    return listOf("'networkServices': present. Property cannot be set when 'devMode' is true unless devModeOptions.allowCompatibilityZone is also true")
//                }
//            }
//        }
//        return emptyList()
//    }
//
//    private fun validateNetworkServices(): List<String> {
//        val errors = mutableListOf<String>()
//
//        if (compatibilityZoneURL != null && networkServices != null && !(networkServices!!.inferred)) {
//            errors += "Cannot configure both compatibilityZoneUrl and networkServices simultaneously"
//        }
//
//        return errors
//    }
}