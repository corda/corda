package com.r3.ha.utilities

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaVersionProvider
import net.corda.cliutils.ExitCodes
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.CertRole
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.div
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.contextLogger
import net.corda.node.VersionInfo
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NetworkServicesConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NetworkRegistrationHelper
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.cryptoservice.SupportedCryptoServices
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService
import net.corda.nodeapi.internal.cryptoservice.azure.AzureKeyVaultCryptoService.Companion.AzureKeyVaultConfig
import net.corda.nodeapi.internal.cryptoservice.futurex.FutureXCryptoService
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService
import net.corda.nodeapi.internal.cryptoservice.gemalto.GemaltoLunaCryptoService.GemaltoLunaConfiguration
import net.corda.nodeapi.internal.cryptoservice.securosys.PrimusXCryptoService
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService
import net.corda.nodeapi.internal.cryptoservice.utimaco.UtimacoCryptoService.UtimacoConfig
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import picocli.CommandLine.Option
import java.lang.IllegalArgumentException
import java.lang.UnsupportedOperationException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths

class NotaryRegistrationTool : CordaCliWrapper("notary-registration", "Corda registration tool for registering a HA notary service with the Corda Network.") {
    companion object {
        private const val NOTARY_PRIVATE_KEY_ALIAS = "${X509Utilities.DISTRIBUTED_NOTARY_ALIAS_PREFIX}-private-key"
        private val VERSION_INFO by lazy {
            // Use lazy init to ensure that CliWrapperBase.initLogging() is called before accessing CordaVersionProvider
            VersionInfo(
                    PLATFORM_VERSION,
                    CordaVersionProvider.releaseVersion,
                    CordaVersionProvider.revision,
                    CordaVersionProvider.vendor)
        }

        private val logger by lazy { contextLogger() }
    }

    @Option(names = ["-b", BASE_DIR], paramLabel = "FOLDER", description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["--config-file", "-f"], paramLabel = "FILE", description = ["The path to the node config file"], required = true, defaultValue = "node.conf")
    lateinit var configFile: Path
    @Option(names = ["-t", "--network-root-truststore"], paramLabel = "FILE", description = ["Network root trust store obtained from network operator."], required = true)
    var networkRootTrustStorePath: Path = Paths.get(".").toAbsolutePath().normalize() / "network-root-truststore.jks"
    @Option(names = ["-p", "--network-root-truststore-password"], paramLabel = "PASSWORD", description = ["Network root trust store password obtained from network operator."], required = true)
    lateinit var networkRootTrustStorePassword: String

    private fun initialiseSerialization() {
        if (nodeSerializationEnv == null) {
            nodeSerializationEnv =
                    SerializationEnvironment.with(
                            SerializationFactoryImpl().apply {
                                registerScheme(AMQPClientSerializationScheme(emptyList()))
                            },
                            AMQP_P2P_CONTEXT,
                            rpcClientContext = AMQP_RPC_CLIENT_CONTEXT)
        }
    }

    override fun runProgram(): Int {
        initialiseSerialization() // Should be called after CliWrapperBase.initLogging()
        return try {
            if (!HAUtilities.addJarsInDriversDirectoryToSystemClasspath(baseDirectory)) {
                HAUtilities.addJarsInDriversDirectoryToSystemClasspath(Paths.get("."))
            }

            validateNodeHsmConfigs(listOf(configFile))

            val parsedConfig = parseNodeConfiguration()
            val serviceLegalName = parsedConfig.notary?.serviceLegalName
                    ?: throw IllegalStateException("Notary service legal name not specified. Please include the notary.serviceLegalName entry in the node configuration file.")

            logger.info("Processing notary service registration for: $serviceLegalName")

            // This is a workaround for using the service legal name instead of node's legal name for the certificate signing request
            // TODO: Update NetworkRegistrationHelper to allow specifying a legal name for the CSR
            val configurationForRegistration = object : NodeConfiguration by parsedConfig {
                override val myLegalName: CordaX500Name
                    get() = serviceLegalName
            }

            with(configurationForRegistration) {
                val networkRegistrationService = HTTPNetworkRegistrationService(
                        NetworkServicesConfig(this.networkServices!!.doormanURL, URL("http://dummy-host")),
                        VERSION_INFO
                )
                val registrationHelper = NetworkRegistrationHelper(
                        this,
                        networkRegistrationService,
                        networkRootTrustStorePath,
                        networkRootTrustStorePassword,
                        NOTARY_PRIVATE_KEY_ALIAS,
                        CertRole.SERVICE_IDENTITY
                )
                registrationHelper.generateKeysAndRegister()
            }
            ExitCodes.SUCCESS
        } catch (e: Exception) {
            logger.error("Notary service registration failed with exception", e)
            ExitCodes.FAILURE
        }
    }

    private fun parseNodeConfiguration(): NodeConfiguration {
        return resolveCryptoServiceConfPathToAbsolutePath(
                configFile.parent,
                ConfigHelper.loadConfig(configFile.parent, configFile)
        )
                .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDirectory.toString()))
                .parseAsNodeConfiguration()
                .value()
    }

    private fun resolveCryptoServiceConfPathToAbsolutePath(configFileParentPath: Path, config: Config): Config {
        if (config.hasPath("cryptoServiceConf")) {
            val cryptoServiceConfPath = Paths.get(config.getString("cryptoServiceConf"))
            if (!cryptoServiceConfPath.isAbsolute) {
                return config.withValue("cryptoServiceConf",
                        ConfigValueFactory.fromAnyRef(resolveRelativeCryptoConfPath(configFileParentPath, cryptoServiceConfPath).toString()))
            }
        }
        return config
    }

    private fun resolveCryptoConfPath(configFileParentPath: Path, configFilePath: Path): Path {
        if (!configFilePath.isAbsolute) {
            return resolveRelativeCryptoConfPath(configFileParentPath, configFilePath)
        }
        return configFilePath
    }

    private fun resolveRelativeCryptoConfPath(configFileParentPath: Path, relativeConfigFilePath: Path): Path {
        val absoluteParentPath = configFileParentPath.toAbsolutePath()
        return absoluteParentPath.resolve(relativeConfigFilePath)
    }

    // Make sure the nodes don't have conflicting crypto service configurations
    private fun validateNodeHsmConfigs(configFiles: List<Path>) {
        val cryptoServicesTypes = mutableMapOf<SupportedCryptoServices, MutableList<Any>>()
        configFiles.forEach { configPath ->
            val nodeConfig = ConfigHelper.loadConfig(configPath.parent, configPath).parseAsNodeConfiguration().value()
            val errorMessage = "Node ${nodeConfig.myLegalName} has conflicting crypto service configuration."
            nodeConfig.cryptoServiceName?.let { nodeCryptoServiceName ->
                val configs = cryptoServicesTypes.getOrDefault(nodeCryptoServiceName, mutableListOf())
                val cryptoServiceConfigPath = resolveCryptoConfPath(configPath.parent, nodeConfig.cryptoServiceConf!!)
                val toProcess = readCryptoConfigFile(nodeCryptoServiceName, cryptoServiceConfigPath)
                configs.forEach {
                    when (nodeCryptoServiceName) {
                        SupportedCryptoServices.AZURE_KEY_VAULT -> {
                            toProcess as AzureKeyVaultConfig
                            it as AzureKeyVaultConfig
                            if (toProcess.keyVaultURL == it.keyVaultURL) {
                                require(toProcess.path != it.path &&
                                        toProcess.alias != it.alias &&
                                        toProcess.clientId != it.clientId) { errorMessage }
                            }
                        }
                        SupportedCryptoServices.BC_SIMPLE -> {
                            // Nothing to be done for BC
                            Any()
                        }
                        else -> {
                            throw UnsupportedOperationException("Only supported HSM is Azure Key Vault")
                        }
                    }
                }
                configs.add(toProcess)
                cryptoServicesTypes[nodeCryptoServiceName] = configs
            }
        }
    }

    private fun readCryptoConfigFile(type: SupportedCryptoServices, path: Path): Any {
        return when (type) {
            SupportedCryptoServices.AZURE_KEY_VAULT -> AzureKeyVaultCryptoService.parseConfigFile(path)
            SupportedCryptoServices.BC_SIMPLE -> Any()
            else -> {
                throw UnsupportedOperationException("Only supported HSM is Azure Key Vault")
            }
        }
    }
}