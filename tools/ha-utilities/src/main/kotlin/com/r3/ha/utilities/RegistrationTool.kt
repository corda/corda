package com.r3.ha.utilities

import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.ExitCodes
import net.corda.common.logging.CordaVersion
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
import net.corda.node.utilities.registration.NodeRegistrationConfiguration
import net.corda.node.utilities.registration.NodeRegistrationHelper
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import picocli.CommandLine.Option
import java.lang.IllegalStateException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

class RegistrationTool : CordaCliWrapper("node-registration", "Corda registration tool for registering 1 or more node with the Corda Network, using provided node configuration(s)." +
        " For convenience the tool is also downloading network parameters.") {
    companion object {
        private val VERSION_INFO = VersionInfo(
                PLATFORM_VERSION,
                CordaVersion.releaseVersion,
                CordaVersion.revision,
                CordaVersion.vendor)

        private val logger by lazy { contextLogger() }
    }

    @Option(names = ["-b", BASE_DIR], paramLabel = "FOLDER", description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["--config-files", "-f"], arity = "1..*", paramLabel = "FILE", description = ["The path to the config file"], required = true)
    lateinit var configFiles: List<Path>
    @Option(names = ["-t", "--network-root-truststore"], paramLabel = "FILE", description = ["Network root trust store obtained from network operator."], required = true)
    var networkRootTrustStorePath: Path = Paths.get(".").toAbsolutePath().normalize() / "network-root-truststore.jks"
    @Option(names = ["-p", "--network-root-truststore-password"], paramLabel = "PASSWORD", description = ["Network root trust store password obtained from network operator."], required = true)
    lateinit var networkRootTrustStorePassword: String

    init {
        initialiseSerialization()
    }

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
        return try {
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
                            val parsedConfig = ConfigHelper.loadConfig(it.parent, it)
                                    .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDir.toString()))
                                    .parseAsNodeConfiguration()
                                    .value()
                            with(parsedConfig) {
                                NodeRegistrationHelper(NodeRegistrationConfiguration(this), HTTPNetworkRegistrationService(networkServices!!, VERSION_INFO),
                                        NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword),
                                        logProgress = logger::info, logError = logger::error).generateKeysAndRegister()
                            }
                            parsedConfig
                        } catch (ex: Exception) {
                            logger.error("Failed to process $legalName", ex)
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
                val versionInfo = VersionInfo(PLATFORM_VERSION, CordaVersion.releaseVersion, CordaVersion.revision, CordaVersion.vendor)
                val networkMapClient = NetworkMapClient(success.first().second.getOrThrow().networkServices!!.networkMapURL, versionInfo)
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

            ExitCodes.SUCCESS
        } catch (e: Exception) {
            logger.error("RegistrationTool failed with exception", e)
            ExitCodes.FAILURE
        }
    }

    private fun String.toFileName(): String {
        return replace("[^a-zA-Z0-9-_.]".toRegex(), "_")
    }
}