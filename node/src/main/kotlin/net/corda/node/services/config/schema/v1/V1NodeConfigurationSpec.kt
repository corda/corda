package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.node.services.config.JmxReporterType
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.NodeConfigurationImpl.Defaults
import net.corda.node.services.config.Valid
import net.corda.node.services.config.VerifierType
import net.corda.node.services.config.schema.parsers.*

internal object V1NodeConfigurationSpec : Configuration.Specification<NodeConfiguration>("NodeConfiguration") {
    private val myLegalName by string().mapValid(::toCordaX500Name)
    private val emailAddress by string()
    private val jmxMonitoringHttpPort by int().optional()
    private val dataSourceProperties by nestedObject(sensitive = true).map(::toProperties)
    private val rpcUsers by nested(UserSpec).listOrEmpty()
    private val security by nested(SecurityConfigurationSpec).optional()
    private val devMode by boolean().optional().withDefaultValue(Defaults.devMode)
    private val devModeOptions by nested(DevModeOptionsSpec).optional()
    private val compatibilityZoneURL by string().mapValid(::toURL).optional()
    private val networkServices by nested(NetworkServicesConfigSpec).optional()
    private val certificateChainCheckPolicies by nested(CertChainPolicyConfigSpec).list().optional().withDefaultValue(Defaults.certificateChainCheckPolicies)
    private val verifierType by enum(VerifierType::class)
    private val flowTimeout by nested(FlowTimeoutConfigurationSpec)
    private val notary by nested(NotaryConfigSpec).optional()
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
    private val keyStorePassword by string(sensitive = true)
    private val trustStorePassword by string(sensitive = true)
    private val rpcAddress by string().mapValid(::toNetworkHostAndPort).optional()
    private val transactionCacheSizeMegaBytes by int().optional()
    private val attachmentContentCacheSizeMegaBytes by int().optional()
    private val h2port by int().optional()
    private val jarDirs by string().list().optional().withDefaultValue(Defaults.jarDirs)
    private val cordappDirectories by string().mapValid(::toPath).list().optional()
    private val cordappSignerKeyFingerprintBlacklist by string().list().optional().withDefaultValue(Defaults.cordappSignerKeyFingerprintBlacklist)
    @Suppress("unused")
    private val custom by nestedObject().optional()
    @Suppress("unused")
    private val systemProperties by nestedObject().optional()

    override fun parseValid(configuration: Config): Valid<NodeConfiguration> {

        val messagingServerExternal = configuration[messagingServerExternal] ?: Defaults.messagingServerExternal(configuration[messagingServerAddress])
        val database = configuration[database] ?: Defaults.database(configuration[devMode])
        val baseDirectoryPath = configuration[baseDirectory]
        val cordappDirectories = configuration[cordappDirectories] ?: Defaults.cordappsDirectories(baseDirectoryPath)
        val result = try {
            valid<NodeConfigurationImpl, Configuration.Validation.Error>(NodeConfigurationImpl(
                    baseDirectory = baseDirectoryPath,
                    myLegalName = configuration[myLegalName],
                    emailAddress = configuration[emailAddress],
                    p2pAddress = configuration[p2pAddress],
                    keyStorePassword = configuration[keyStorePassword],
                    trustStorePassword = configuration[trustStorePassword],
                    crlCheckSoftFail = configuration[crlCheckSoftFail],
                    dataSourceProperties = configuration[dataSourceProperties],
                    rpcUsers = configuration[rpcUsers],
                    verifierType = configuration[verifierType],
                    flowTimeout = configuration[flowTimeout],
                    rpcSettings = configuration[rpcSettings],
                    messagingServerAddress = configuration[messagingServerAddress],
                    notary = configuration[notary],
                    flowOverrides = configuration[flowOverrides],
                    additionalP2PAddresses = configuration[additionalP2PAddresses],
                    additionalNodeInfoPollingFrequencyMsec = configuration[additionalNodeInfoPollingFrequencyMsec],
                    jmxMonitoringHttpPort = configuration[jmxMonitoringHttpPort],
                    security = configuration[security],
                    devMode = configuration[devMode],
                    devModeOptions = configuration[devModeOptions],
                    compatibilityZoneURL = configuration[compatibilityZoneURL],
                    networkServices = configuration[networkServices],
                    certificateChainCheckPolicies = configuration[certificateChainCheckPolicies],
                    messagingServerExternal = messagingServerExternal,
                    useTestClock = configuration[useTestClock],
                    lazyBridgeStart = configuration[lazyBridgeStart],
                    detectPublicIp = configuration[detectPublicIp],
                    sshd = configuration[sshd],
                    database = database,
                    noLocalShell = configuration[noLocalShell],
                    attachmentCacheBound = configuration[attachmentCacheBound],
                    extraNetworkMapKeys = configuration[extraNetworkMapKeys],
                    tlsCertCrlDistPoint = configuration[tlsCertCrlDistPoint],
                    tlsCertCrlIssuer = configuration[tlsCertCrlIssuer],
                    h2Settings = configuration[h2Settings],
                    flowMonitorPeriodMillis = configuration[flowMonitorPeriodMillis],
                    flowMonitorSuspensionLoggingThresholdMillis = configuration[flowMonitorSuspensionLoggingThresholdMillis],
                    jmxReporterType = configuration[jmxReporterType],
                    rpcAddress = configuration[rpcAddress],
                    transactionCacheSizeMegaBytes = configuration[transactionCacheSizeMegaBytes],
                    attachmentContentCacheSizeMegaBytes = configuration[attachmentContentCacheSizeMegaBytes],
                    h2port = configuration[h2port],
                    jarDirs = configuration[jarDirs],
                    cordappDirectories = cordappDirectories.map { baseDirectoryPath.resolve(it) },
                    cordappSignerKeyFingerprintBlacklist = configuration[cordappSignerKeyFingerprintBlacklist]
            ))
        } catch (e: Exception) {
            return when (e) {
                is ConfigException -> invalid(e.toValidationError(typeName = "NodeConfiguration"))
                is IllegalArgumentException -> badValue(e.message!!)
                else -> throw e
            }
        }
        return result.mapValid { conf -> Valid.withResult(conf as NodeConfiguration, conf.validate().map(::toError).toSet()) }
    }
}

private fun toError(validationErrorMessage: String): Configuration.Validation.Error = Configuration.Validation.Error.BadValue.of(validationErrorMessage)