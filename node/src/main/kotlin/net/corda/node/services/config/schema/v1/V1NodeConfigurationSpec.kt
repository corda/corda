package net.corda.node.services.config.schema.v1

import com.typesafe.config.Config
import com.typesafe.config.ConfigException
import net.corda.common.configuration.parsing.internal.*
import net.corda.common.validation.internal.Validated.Companion.invalid
import net.corda.common.validation.internal.Validated.Companion.valid
import net.corda.node.services.config.*
import net.corda.node.services.config.NodeConfigurationImpl.Defaults
import net.corda.node.services.config.schema.parsers.*
import net.corda.node.services.config.Valid
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices

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
    private val cryptoServiceName by enum(SupportedCryptoServices::class).optional()
    private val cryptoServiceConf by string().mapValid(::toPath).optional()
    private val cryptoServiceTimeout by duration().optional().withDefaultValue(Defaults.cryptoServiceTimeout)
    @Suppress("unused")
    private val custom by nestedObject().optional()
    private val relay by nested(RelayConfigurationSpec).optional()
    private val enableSNI by boolean().optional().withDefaultValue(Defaults.enableSNI)
    private val useOpenSsl by boolean().optional().withDefaultValue(Defaults.useOpenSsl)
    private val graphiteOptions by nested(GraphiteOptionsSpec).optional()
    private val enterpriseConfiguration by nested(EnterpriseConfigurationSpec)
    @Suppress("unused")
    private val systemProperties by nestedObject().optional()

    override fun parseValid(configuration: Config, options: Configuration.Options): Valid<NodeConfiguration> {
        val config = configuration.withOptions(options)
        val messagingServerExternal = config[messagingServerExternal] ?: Defaults.messagingServerExternal(config[messagingServerAddress])
        val database = config[database] ?: Defaults.database(config[devMode])
        val baseDirectoryPath = config[baseDirectory]
        val cordappDirectories = config[cordappDirectories] ?: Defaults.cordappsDirectories(baseDirectoryPath)
        val result = try {
            valid<NodeConfigurationImpl, Configuration.Validation.Error>(NodeConfigurationImpl(
                    baseDirectory = baseDirectoryPath,
                    myLegalName = config[myLegalName],
                    emailAddress = config[emailAddress],
                    p2pAddress = config[p2pAddress],
                    keyStorePassword = config[keyStorePassword],
                    trustStorePassword = config[trustStorePassword],
                    crlCheckSoftFail = config[crlCheckSoftFail],
                    dataSourceProperties = config[dataSourceProperties],
                    rpcUsers = config[rpcUsers],
                    verifierType = config[verifierType],
                    flowTimeout = config[flowTimeout],
                    rpcSettings = config[rpcSettings],
                    messagingServerAddress = config[messagingServerAddress],
                    notary = config[notary],
                    flowOverrides = config[flowOverrides],
                    additionalP2PAddresses = config[additionalP2PAddresses],
                    additionalNodeInfoPollingFrequencyMsec = config[additionalNodeInfoPollingFrequencyMsec],
                    jmxMonitoringHttpPort = config[jmxMonitoringHttpPort],
                    security = config[security],
                    devMode = config[devMode],
                    devModeOptions = config[devModeOptions],
                    compatibilityZoneURL = config[compatibilityZoneURL],
                    networkServices = config[networkServices],
                    certificateChainCheckPolicies = config[certificateChainCheckPolicies],
                    messagingServerExternal = messagingServerExternal,
                    useTestClock = config[useTestClock],
                    lazyBridgeStart = config[lazyBridgeStart],
                    detectPublicIp = config[detectPublicIp],
                    sshd = config[sshd],
                    database = database,
                    noLocalShell = config[noLocalShell],
                    attachmentCacheBound = config[attachmentCacheBound],
                    extraNetworkMapKeys = config[extraNetworkMapKeys],
                    tlsCertCrlDistPoint = config[tlsCertCrlDistPoint],
                    tlsCertCrlIssuer = config[tlsCertCrlIssuer],
                    h2Settings = config[h2Settings],
                    flowMonitorPeriodMillis = config[flowMonitorPeriodMillis],
                    flowMonitorSuspensionLoggingThresholdMillis = config[flowMonitorSuspensionLoggingThresholdMillis],
                    jmxReporterType = config[jmxReporterType],
                    rpcAddress = config[rpcAddress],
                    transactionCacheSizeMegaBytes = config[transactionCacheSizeMegaBytes],
                    attachmentContentCacheSizeMegaBytes = config[attachmentContentCacheSizeMegaBytes],
                    h2port = config[h2port],
                    jarDirs = config[jarDirs],
                    cordappDirectories = cordappDirectories.map { baseDirectoryPath.resolve(it) },
                    cordappSignerKeyFingerprintBlacklist = config[cordappSignerKeyFingerprintBlacklist],
                    cryptoServiceName = config[cryptoServiceName],
                    cryptoServiceConf = config[cryptoServiceConf],
                    cryptoServiceTimeout = config[cryptoServiceTimeout],
                    relay = config[relay],
                    enableSNI = config[enableSNI],
                    useOpenSsl = config[useOpenSsl],
                    graphiteOptions = config[graphiteOptions],
                    enterpriseConfiguration = config[enterpriseConfiguration]
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