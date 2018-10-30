package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.node.services.config.*
import net.corda.node.services.config.schema.parsers.*
import java.time.Duration

internal object V1NodeConfigurationSpec : Configuration.Specification<NodeConfiguration>("NodeConfiguration") {

    // TODO sollecitom review all default values against NodeConfigurationImpl
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
    private val rpcSettings by nested(NodeRpcSettingsSpec)
    private val messagingServerAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val messagingServerExternal by boolean()
    private val useTestClock by boolean().optional(false)
    private val lazyBridgeStart by boolean()
    private val detectPublicIp by boolean().optional(true)
    private val sshd by nested(SSHDConfigurationSpec).optional()
    private val database by nested(DatabaseConfigSpec)
    private val noLocalShell by boolean().optional(false)
    // TODO sollecitom perhaps create a Property.Definition.Optional interface exposing a `withDefault(<non-null> defaultValue: TYPE): Property.Definition<TYPE>`
    // TODO sollecitom use it for list properties to default to empty
    private val transactionCacheSizeBytes by long().optional(NodeConfiguration.defaultTransactionCacheSize)
    private val attachmentContentCacheSizeBytes by long().optional(NodeConfiguration.defaultAttachmentContentCacheSize)
    private val attachmentCacheBound by long().optional(NodeConfiguration.defaultAttachmentCacheBound)
    private val drainingModePollPeriod by duration().optional(Duration.ofSeconds(5))
    private val extraNetworkMapKeys by string().mapValid(::toUuid).list()
    private val tlsCertCrlDistPoint by string().mapValid(::toUrl).optional()
    private val tlsCertCrlIssuer by string().mapValid(::toPrincipal).optional()
    private val h2Settings by nested(NodeH2SettingsSpec).optional()
    private val flowMonitorPeriodMillis by duration().optional(DEFAULT_FLOW_MONITOR_PERIOD_MILLIS)
    private val flowMonitorSuspensionLoggingThresholdMillis by duration().optional(DEFAULT_FLOW_MONITOR_SUSPENSION_LOGGING_THRESHOLD_MILLIS)
    private val crlCheckSoftFail by boolean()
    private val jmxReporterType by enum(JmxReporterType::class).optional(NodeConfiguration.defaultJmxReporterType)
    private val baseDirectory by string().mapValid(::toPath)
    private val certificatesDirectory by string().mapValid(::toPath)
    private val flowOverrides by nested(FlowOverrideConfigSpec).optional()
    private val keyStorePassword by string()
    private val trustStorePassword by string()
    private val rpcAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val transactionCacheSizeMegaBytes by int().optional()
    private val attachmentContentCacheSizeMegaBytes by int().optional()
    private val h2port by int().optional()
    // TODO sollecitom this should really be List<Path>, not sure why it's List<String> in NodeConfigurationImpl
    private val jarDirs by string().list()

    override fun parseValid(configuration: Config): Valid<NodeConfiguration> {

        TODO("not implemented")
    }

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