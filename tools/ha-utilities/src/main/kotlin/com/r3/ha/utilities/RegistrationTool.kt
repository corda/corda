package com.r3.ha.utilities

import com.typesafe.config.Config
import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaVersionProvider
import net.corda.cliutils.ExitCodes
import net.corda.core.crypto.SecureHash
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.div
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.utilities.Try
import net.corda.core.utilities.contextLogger
import net.corda.node.NodeRegistrationOption
import net.corda.node.VersionInfo
import net.corda.node.internal.NetworkParametersReader
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.utilities.registration.HTTPNetworkRegistrationService
import net.corda.node.utilities.registration.NodeRegistrationHelper
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.nodeapi.internal.cryptoservice.CryptoService
import net.corda.nodeapi.internal.cryptoservice.CryptoServiceFactory
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
import picocli.CommandLine
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import javax.security.auth.x500.X500Principal
import kotlin.concurrent.thread

class RegistrationTool : CordaCliWrapper("node-registration", "Corda registration tool for registering 1 or more node with the Corda Network, using provided node configuration(s)." +
        " For convenience the tool is also downloading network parameters. Additionally, it can import the TLS keys into the bridge.") {
    companion object {
        private val DUMMY_X500_NAME = CordaX500Name("Bridge", "London", "GB")
        private val VERSION_INFO by lazy {
            // Use lazy init to ensure that CliWrapperBase.initLogging() is called before accessing CordaVersionProvider
            VersionInfo(
                    PLATFORM_VERSION,
                    CordaVersionProvider.releaseVersion,
                    CordaVersionProvider.revision,
                    CordaVersionProvider.vendor)
        }

        private val logger by lazy { contextLogger() }

        internal fun x500PrincipalToTLSAlias(x500Principal: X500Principal): String {
            val nameHash = SecureHash.sha256(x500Principal.toString())
            // Note we use lower case as .JKS aliases are squashed to lowercase during the insert procedure and this breaks any link to the HSM alias.
            return "${X509Utilities.CORDA_CLIENT_TLS}-$nameHash".toLowerCase()
        }

        fun CordaX500Name.toFolderName() : String {
            val folderName = if (commonName == null) organisation else "$commonName,$organisation"
            return folderName.toFileName()
        }

        private fun String.toFileName(): String {
            return replace("[^a-zA-Z0-9-_.]".toRegex(), "_")
        }
    }

    @Option(names = ["-b", BASE_DIR], paramLabel = "FOLDER", description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["--config-files", "-f"], arity = "1..*", paramLabel = "FILE", description = ["The path to the node config file"], required = true)
    lateinit var configFiles: List<Path>
    @Option(names = ["-t", "--network-root-truststore"], paramLabel = "FILE", description = ["Network root trust store obtained from network operator."], required = true)
    var networkRootTrustStorePath: Path = Paths.get(".").toAbsolutePath().normalize() / "network-root-truststore.jks"
    @Option(names = ["-p", "--network-root-truststore-password"], paramLabel = "PASSWORD", description = ["Network root trust store password obtained from network operator."], required = true)
    lateinit var networkRootTrustStorePassword: String
    @Option(names = ["--bridge-config-file", "-g"],  paramLabel = "FILE", description = ["The path to the bridge configuration file."])
    var bridgeConfigFile: Path? = null
    @Option(names = ["-r", "--bridge-keystore-password"], paramLabel = "PASSWORDS", description = ["The password of the bridge SSL keystore."])
    var bridgeKeystorePassword: String? = null

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
            validateNodeHsmConfigs(configFiles)
            val bridgeCryptoService = makeBridgeCryptoService(bridgeConfigFile)
            // Parallel processing is beneficial as it is possible to submit multiple CSR in close succession
            // If manual interaction will be needed - it would be possible to sign them all off at once on Doorman side.
            val nodeConfigurations = ConcurrentLinkedQueue<Pair<CordaX500Name, Try<NodeConfiguration>>>()
            val startedThreads = configFiles.map {
                val legalName = ConfigHelper.loadConfig(it.parent, it).parseAsNodeConfiguration().value().myLegalName
                thread(name = legalName.toString(), start = true) {
                    nodeConfigurations.add(Pair(legalName, Try.on {
                        try {
                            logger.info("Processing registration for: $legalName")
                            // Load the config again with modified base directory.
                            val folderName = if (legalName.commonName == null) legalName.organisation else "${legalName.commonName},${legalName.organisation}"
                            val baseDir = baseDirectory / folderName.toFileName()
                            val parsedConfig = resolveCryptoServiceConfPathToAbsolutePath(it.parent,
                                                                                          ConfigHelper.loadConfig(it.parent, it))
                                    .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                                    .parseAsNodeConfiguration()
                                    .value()
                            val sslPublicKey = if (bridgeCryptoService != null) {
                                val alias = x500PrincipalToTLSAlias(legalName.x500Principal) // must be lower case to stay consistent with public .JKS file
                                bridgeCryptoService.generateKeyPair(alias, X509Utilities.DEFAULT_TLS_SIGNATURE_SCHEME)
                            } else {
                                null
                            }
                            with(parsedConfig) {
                                val helper = NodeRegistrationHelper(this, HTTPNetworkRegistrationService(networkServices!!, VERSION_INFO),
                                        NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword),
                                        logProgress = logger::info, logError = logger::error)
                                helper.generateKeysAndRegister(sslPublicKey)
                                helper.generateNodeIdentity()
                                helper.generateWrappingKey()
                            }
                            parsedConfig
                        } catch (ex: Throwable) {
                            logger.error("Failed to process the following X500 name [$legalName]", ex)
                            throw ex
                        }
                    }))
                }
            }

            // Wait for all to complete successfully or not.
            startedThreads.forEach { it.join() }

            val (success, fail) = nodeConfigurations.partition { it.second.isSuccess }

            if(success.isNotEmpty()) {
                // Fetch the network params and store them in the `baseDirectory`
                val versionInfo = VersionInfo(PLATFORM_VERSION, CordaVersionProvider.releaseVersion, CordaVersionProvider.revision, CordaVersionProvider.vendor)
                val networkMapClient = NetworkMapClient(success.first().second.getOrThrow().networkServices!!, versionInfo)
                val trustRootCertificate = X509KeyStore.fromFile(networkRootTrustStorePath, networkRootTrustStorePassword)
                        .getCertificate(CORDA_ROOT_CA)
                networkMapClient.start(trustRootCertificate)
                val networkParamsReader = NetworkParametersReader(
                        trustRootCertificate,
                        networkMapClient,
                        baseDirectory)
                networkParamsReader.read()
            }

            if (fail.isNotEmpty()) {
                fun List<Pair<CordaX500Name, Try<NodeConfiguration>>>.allX500NamesAsStr(): String {
                    return map { it.first }.joinToString(";")
                }

                throw IllegalStateException("Registration of [${success.allX500NamesAsStr()}] been successful." +
                        " However, for the following X500 names it has failed: [${fail.allX500NamesAsStr()}]. Please see log for more details.")
            }

            bridgeKeystorePassword?.let {
                logger.info("Running BridgeSSLKeyTool to distribute sslkeystores")
                val toolArgs = generateBridgeSSLKeyToolArguments(baseDirectory, success, bridgeKeystorePassword!!)
                val sslKeyTool = BridgeSSLKeyTool()
                logger.info("Params for BridgeSSLKeyTool are: $toolArgs")
                CommandLine.populateCommand(sslKeyTool, *toolArgs.toTypedArray())
                sslKeyTool.runProgram()
            }

            ExitCodes.SUCCESS
        } catch (e: Exception) {
            logger.error("RegistrationTool failed with exception", e)
            ExitCodes.FAILURE
        }
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

    private fun String.toFileName(): String {
        return replace("[^a-zA-Z0-9-_.]".toRegex(), "_")
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
                    when(nodeCryptoServiceName) {
                        SupportedCryptoServices.UTIMACO -> {
                            toProcess as UtimacoConfig
                            it as UtimacoConfig
                            if (toProcess.host == it.host && toProcess.port == it.port) {
                                require (toProcess.username != it.username) { errorMessage }
                            }
                        }
                        SupportedCryptoServices.GEMALTO_LUNA -> {
                            toProcess as GemaltoLunaConfiguration
                            it as GemaltoLunaConfiguration
                            require (toProcess.keyStore != it.keyStore) { errorMessage }
                        }
                        SupportedCryptoServices.AZURE_KEY_VAULT -> {
                            toProcess as AzureKeyVaultConfig
                            it as AzureKeyVaultConfig
                            if (toProcess.keyVaultURL == it.keyVaultURL) {
                                require(toProcess.path != it.path &&
                                        toProcess.alias != it.alias &&
                                        toProcess.clientId != it.clientId) { errorMessage }
                            }
                        }
                        SupportedCryptoServices.FUTUREX -> {
                            toProcess as FutureXCryptoService.FutureXConfiguration
                            it as FutureXCryptoService.FutureXConfiguration
                            require(toProcess.credentials != it.credentials) { errorMessage }
                        }
                        SupportedCryptoServices.PRIMUS_X -> {
                            toProcess as PrimusXCryptoService.PrimusXConfiguration
                            it as PrimusXCryptoService.PrimusXConfiguration
                            if (toProcess.host == it.host && toProcess.port == it.port) {
                                require (toProcess.username != it.username) { errorMessage }
                            }
                        }
                        SupportedCryptoServices.BC_SIMPLE -> {
                            // Nothing to be done for BC
                            Any()
                        }
                    }
                }
                configs.add(toProcess)
                cryptoServicesTypes[nodeCryptoServiceName] = configs
            }
        }
    }

    private fun readCryptoConfigFile(type: SupportedCryptoServices, path: Path): Any {
        return when(type) {
            SupportedCryptoServices.UTIMACO -> UtimacoCryptoService.parseConfigFile(path)
            SupportedCryptoServices.GEMALTO_LUNA -> GemaltoLunaCryptoService.parseConfigFile(path)
            SupportedCryptoServices.AZURE_KEY_VAULT -> AzureKeyVaultCryptoService.parseConfigFile(path)
            SupportedCryptoServices.FUTUREX -> FutureXCryptoService.parseConfigFile(path)
            SupportedCryptoServices.PRIMUS_X -> PrimusXCryptoService.parseConfigFile(path)
            SupportedCryptoServices.BC_SIMPLE -> Any()
        }
    }

    private fun generateBridgeSSLKeyToolArguments(dir: Path, nodeConfigs: List<Pair<CordaX500Name, Try<NodeConfiguration>>>, bridgeKeyStorePassword: String): List<String> {
        val args = mutableListOf<String>()
        args.add("--base-directory")
        args.add(dir.toString())
        args.add("--bridge-keystore-password")
        args.add(bridgeKeyStorePassword)
        args.add("--node-keystores")
        val passwords = mutableListOf<String>()
        passwords.add("--node-keystore-passwords")
        nodeConfigs.forEach {
            val x500Name = it.first
            val config = it.second.getOrThrow()
            args.add((dir / x500Name.toFolderName() / "certificates/sslkeystore.jks").toString())
            passwords.add(config.p2pSslOptions.keyStore.storePassword)
        }
        args.addAll(passwords)
        return args
    }

    private fun makeBridgeCryptoService(configFile: Path?): CryptoService? {
        return if (configFile != null) {
            var bridgeCryptoServiceName: SupportedCryptoServices? = null
            try {
                // Get CryptoService type from bridge configuration
                val bridgeConfig = ConfigHelper.loadConfig(configFile.parent, configFile)
                if (bridgeConfig.isEmpty || !bridgeConfig.hasPath("p2pTlsSigningCryptoServiceConfig")) {
                    throw IllegalArgumentException("Bridge configuration file missing 'p2pTlsSigningCryptoServiceConfig' property.")
                }

                val bridgeCryptoServiceConfig = bridgeConfig.getConfig("p2pTlsSigningCryptoServiceConfig")
                if (bridgeCryptoServiceConfig.isEmpty || !bridgeCryptoServiceConfig.hasPath("name") || !bridgeCryptoServiceConfig.hasPath("conf")) {
                    throw IllegalArgumentException("Bridge 'p2pTlsSigningCryptoServiceConfig' is incomplete.")
                }
                bridgeCryptoServiceName = SupportedCryptoServices.valueOf(bridgeCryptoServiceConfig.getString("name"))
                val bridgeCryptoServiceConfigFile = Paths.get(bridgeCryptoServiceConfig.getString("conf"))
                CryptoServiceFactory.makeCryptoService(bridgeCryptoServiceName, DUMMY_X500_NAME, null, (configFile.parent / bridgeCryptoServiceConfigFile.fileName.toString()))
            }
            catch (ex: NoClassDefFoundError) {
                logger.error ("Caught a NoClassDefFoundError exception when trying to load the ${bridgeCryptoServiceName?.name} crypto service")
                logger.error("Please check that the ${baseDirectory / "drivers"} directory contains the client side jar for the HSM")
                throw ex
            }
        } else {
            null
        }
    }
}