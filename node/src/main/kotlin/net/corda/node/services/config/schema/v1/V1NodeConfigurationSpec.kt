package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import net.corda.common.configuration.parsing.internal.Configuration
import net.corda.common.configuration.parsing.internal.get
import net.corda.common.configuration.parsing.internal.map
import net.corda.common.configuration.parsing.internal.mapValid
import net.corda.common.configuration.parsing.internal.nested
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.node.services.config.JmxReporterType
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.NodeConfigurationImpl.Defaults
import net.corda.node.services.config.Valid
import net.corda.node.services.config.VerifierType
import net.corda.node.services.config.schema.parsers.toCordaX500Name
import net.corda.node.services.config.schema.parsers.toNetworkHostAndPort
import net.corda.node.services.config.schema.parsers.toPath
import net.corda.node.services.config.schema.parsers.toPrincipal
import net.corda.node.services.config.schema.parsers.toProperties
import net.corda.node.services.config.schema.parsers.toURL
import net.corda.node.services.config.schema.parsers.toUUID

// TODO sollecitom make all password fields sensitive, including nested configs
// TODO sollecitom reuse default values for all nested configs
internal object V1NodeConfigurationSpec : Configuration.Specification<NodeConfiguration>("NodeConfiguration") {
    private val myLegalName by string().mapValid(::toCordaX500Name)
    private val emailAddress by string()
    private val jmxMonitoringHttpPort by int().optional()
    private val dataSourceProperties by nestedObject().map(::toProperties)
    private val rpcUsers by nested(UserSpec).list()
    private val security by nested(SecurityConfigurationSpec).optional()
    private val devMode by boolean().optional().withDefaultValue(Defaults.devMode)
    private val devModeOptions by nested(DevModeOptionsSpec).optional()
    private val compatibilityZoneURL by string().mapValid(::toURL).optional()
    private val networkServices by nested(NetworkServicesConfigSpec).optional()
    private val certificateChainCheckPolicies by nested(CertChainPolicyConfigSpec).list().optional().withDefaultValue(Defaults.certificateChainCheckPolicies)
    private val verifierType by enum(VerifierType::class)
    private val flowTimeout by nested(FlowTimeoutConfigurationSpec)
    private val notary by nested(NotaryConfigSpec)
    private val additionalNodeInfoPollingFrequencyMsec by long().optional().withDefaultValue(Defaults.additionalNodeInfoPollingFrequencyMsec)
    private val p2pAddress by string().mapValid(::toNetworkHostAndPort)
    private val additionalP2PAddresses by string().mapValid(::toNetworkHostAndPort).list().optional().withDefaultValue(Defaults.additionalP2PAddresses)
    private val rpcSettings by nested(NodeRpcSettingsSpec)
    private val messagingServerAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val messagingServerExternal by boolean().optional()
    private val useTestClock by boolean().optional().withDefaultValue(Defaults.useTestClock)
    private val lazyBridgeStart by boolean().optional().withDefaultValue(Defaults.lazyBridgeStart)
    private val detectPublicIp by boolean().optional().withDefaultValue(Defaults.detectPublicIp)
    private val sshd by nested(SSHDConfigurationSpec).optional()
    private val database by nested(DatabaseConfigSpec).optional()
    private val noLocalShell by boolean().optional().withDefaultValue(Defaults.noLocalShell)
    private val attachmentCacheBound by long().optional().withDefaultValue(Defaults.attachmentCacheBound)
    private val extraNetworkMapKeys by string().mapValid(::toUUID).list().optional().withDefaultValue(Defaults.extraNetworkMapKeys)
    private val tlsCertCrlDistPoint by string().mapValid(::toURL).optional()
    private val tlsCertCrlIssuer by string().mapValid(::toPrincipal).optional()
    private val h2Settings by nested(NodeH2SettingsSpec).optional()
    private val flowMonitorPeriodMillis by duration().optional().withDefaultValue(Defaults.flowMonitorPeriodMillis)
    private val flowMonitorSuspensionLoggingThresholdMillis by duration().optional().withDefaultValue(Defaults.flowMonitorSuspensionLoggingThresholdMillis)
    private val crlCheckSoftFail by boolean()
    private val jmxReporterType by enum(JmxReporterType::class).optional().withDefaultValue(Defaults.jmxReporterType)
    private val baseDirectory by string().mapValid(::toPath)
    private val flowOverrides by nested(FlowOverridesConfigSpec).optional()
    private val keyStorePassword by string()
    private val trustStorePassword by string()
    private val rpcAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val transactionCacheSizeMegaBytes by int().optional()
    private val attachmentContentCacheSizeMegaBytes by int().optional()
    private val h2port by int().optional()
    private val jarDirs by string().list().optional().withDefaultValue(Defaults.jarDirs)
    private val cordappDirectories by string().mapValid(::toPath).list().optional()
    private val cordappSignerKeyFingerprintBlacklist by string().list().optional().withDefaultValue(Defaults.cordappSignerKeyFingerprintBlacklist)

    override fun parseValid(configuration: Config): Valid<NodeConfiguration> {
        val myLegalName = configuration[myLegalName]
        val emailAddress = configuration[emailAddress]
        val dataSourceProperties = configuration[dataSourceProperties]
        val rpcUsers = configuration[rpcUsers]
        val verifierType = configuration[verifierType]
        val flowTimeout = configuration[flowTimeout]
        val notary = configuration[notary]
        val p2pAddress = configuration[p2pAddress]
        val rpcSettings = configuration[rpcSettings]
        val messagingServerAddress = configuration[messagingServerAddress]
        val crlCheckSoftFail = configuration[crlCheckSoftFail]
        val baseDirectory = configuration[baseDirectory]
        val flowOverrides = configuration[flowOverrides]
        val keyStorePassword = configuration[keyStorePassword]
        val trustStorePassword = configuration[trustStorePassword]

        val additionalP2PAddresses = configuration[additionalP2PAddresses]
        val additionalNodeInfoPollingFrequencyMsec = configuration[additionalNodeInfoPollingFrequencyMsec]
        val jmxMonitoringHttpPort = configuration[jmxMonitoringHttpPort]
        val security = configuration[security]
        val devMode = configuration[devMode]
        val devModeOptions = configuration[devModeOptions]
        val compatibilityZoneURL = configuration[compatibilityZoneURL]
        val networkServices = configuration[networkServices]
        val certificateChainCheckPolicies = configuration[certificateChainCheckPolicies]
        val messagingServerExternal = configuration[messagingServerExternal] ?: Defaults.messagingServerExternal(messagingServerAddress)
        val useTestClock = configuration[useTestClock]
        val lazyBridgeStart = configuration[lazyBridgeStart]
        val detectPublicIp = configuration[detectPublicIp]
        val sshd = configuration[sshd]
        val database = configuration[database] ?: Defaults.database(devMode)
        val noLocalShell = configuration[noLocalShell]
        val attachmentCacheBound = configuration[attachmentCacheBound]
        val extraNetworkMapKeys = configuration[extraNetworkMapKeys]
        val tlsCertCrlDistPoint = configuration[tlsCertCrlDistPoint]
        val tlsCertCrlIssuer = configuration[tlsCertCrlIssuer]
        val h2Settings = configuration[h2Settings]
        val flowMonitorPeriodMillis = configuration[flowMonitorPeriodMillis]
        val flowMonitorSuspensionLoggingThresholdMillis = configuration[flowMonitorSuspensionLoggingThresholdMillis]
        val jmxReporterType = configuration[jmxReporterType]
        val rpcAddress = configuration[rpcAddress]
        val transactionCacheSizeMegaBytes = configuration[transactionCacheSizeMegaBytes]
        val attachmentContentCacheSizeMegaBytes = configuration[attachmentContentCacheSizeMegaBytes]
        val h2port = configuration[h2port]
        val jarDirs = configuration[jarDirs]
        val cordappDirectories = configuration[cordappDirectories] ?: Defaults.cordappsDirectories(baseDirectory)
        val cordappSignerKeyFingerprintBlacklist = configuration[cordappSignerKeyFingerprintBlacklist]

        // TODO sollecitom add validation here

        return valid(NodeConfigurationImpl(
                baseDirectory = baseDirectory,
                myLegalName = myLegalName,
                emailAddress = emailAddress,
                p2pAddress = p2pAddress,
                keyStorePassword = keyStorePassword,
                trustStorePassword = trustStorePassword,
                crlCheckSoftFail = crlCheckSoftFail,
                dataSourceProperties = dataSourceProperties,
                rpcUsers = rpcUsers,
                verifierType = verifierType,
                flowTimeout = flowTimeout,
                rpcSettings = rpcSettings,
                messagingServerAddress = messagingServerAddress,
                notary = notary,
                flowOverrides = flowOverrides,
                additionalP2PAddresses = additionalP2PAddresses,
                additionalNodeInfoPollingFrequencyMsec = additionalNodeInfoPollingFrequencyMsec,
                jmxMonitoringHttpPort = jmxMonitoringHttpPort,
                security = security,
                devMode = devMode,
                devModeOptions = devModeOptions,
                compatibilityZoneURL = compatibilityZoneURL,
                networkServices = networkServices,
                certificateChainCheckPolicies = certificateChainCheckPolicies,
                messagingServerExternal = messagingServerExternal,
                useTestClock = useTestClock,
                lazyBridgeStart = lazyBridgeStart,
                detectPublicIp = detectPublicIp,
                sshd = sshd,
                database = database,
                noLocalShell = noLocalShell,
                attachmentCacheBound = attachmentCacheBound,
                extraNetworkMapKeys = extraNetworkMapKeys,
                tlsCertCrlDistPoint = tlsCertCrlDistPoint,
                tlsCertCrlIssuer = tlsCertCrlIssuer,
                h2Settings = h2Settings,
                flowMonitorPeriodMillis = flowMonitorPeriodMillis,
                flowMonitorSuspensionLoggingThresholdMillis = flowMonitorSuspensionLoggingThresholdMillis,
                jmxReporterType = jmxReporterType,
                rpcAddress = rpcAddress,
                transactionCacheSizeMegaBytes = transactionCacheSizeMegaBytes,
                attachmentContentCacheSizeMegaBytes = attachmentContentCacheSizeMegaBytes,
                h2port = h2port,
                jarDirs = jarDirs,
                cordappDirectories = cordappDirectories,
                cordappSignerKeyFingerprintBlacklist = cordappSignerKeyFingerprintBlacklist
        ))
    }

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