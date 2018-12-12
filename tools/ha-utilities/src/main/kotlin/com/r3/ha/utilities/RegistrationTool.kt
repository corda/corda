package com.r3.ha.utilities

import com.typesafe.config.ConfigValueFactory
import net.corda.client.rpc.internal.serialization.amqp.AMQPClientSerializationScheme
import net.corda.cliutils.CommonCliConstants.BASE_DIR
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.CordaVersionProvider
import net.corda.cliutils.ExitCodes
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.div
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
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
import net.corda.nodeapi.internal.crypto.X509Utilities.CORDA_ROOT_CA
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.AMQP_RPC_CLIENT_CONTEXT
import net.corda.serialization.internal.SerializationFactoryImpl
import picocli.CommandLine.Option
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.concurrent.thread

class RegistrationTool : CordaCliWrapper("node-registration", "Corda registration tool for registering 1 or more node with the Corda Network, using provided node configuration(s)." +
        " For convenience the tool is also downloading network parameters.") {
    companion object {
        private val VERSION_INFO = VersionInfo(
                PLATFORM_VERSION,
                CordaVersionProvider.releaseVersion,
                CordaVersionProvider.revision,
                CordaVersionProvider.vendor)
    }

    @Option(names = ["-b", BASE_DIR], paramLabel = "FOLDER", description = ["The node working directory where all the files are kept."])
    var baseDirectory: Path = Paths.get(".").toAbsolutePath().normalize()
    @Option(names = ["--config-files", "-f"], arity = "1..*", paramLabel = "FILE", description = ["The path to the config file"], required = true)
    lateinit var configFiles: List<Path>
    @Option(names = ["-t", "--network-root-truststore"], paramLabel = "FILE", description = ["Network root trust store obtained from network operator."], required = true)
    var networkRootTrustStorePath: Path = Paths.get(".").toAbsolutePath().normalize() / "network-root-truststore.jks"
    @Option(names = ["-p", "--network-root-truststore-password"], paramLabel = "PASSWORD", description = ["Network root trust store password obtained from network operator."], required = true)
    lateinit var networkRootTrustStorePassword: String

    private lateinit var parsedConfig: NodeConfiguration

    init {
        initialiseSerialization()
    }

    private fun initialiseSerialization() {
        nodeSerializationEnv =
                SerializationEnvironment.with(
                        SerializationFactoryImpl().apply {
                            registerScheme(AMQPClientSerializationScheme(emptyList()))
                        },
                        AMQP_P2P_CONTEXT,
                        rpcClientContext = AMQP_RPC_CLIENT_CONTEXT)
    }

    override fun runProgram(): Int {
        return try {
            configFiles.map {
                thread {
                    val legalName = ConfigHelper.loadConfig(it.parent, it).parseAsNodeConfiguration().value().myLegalName
                    // Load the config again with modified base directory.
                    val folderName = if (legalName.commonName == null) legalName.organisation else "${legalName.commonName},${legalName.organisation}"
                    val baseDir = baseDirectory / folderName.toFileName()
                    parsedConfig = ConfigHelper.loadConfig(it.parent, it)
                            .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(baseDir.toString())).parseAsNodeConfiguration()
                            .value()
                    with(parsedConfig) {
                        NodeRegistrationHelper(this, HTTPNetworkRegistrationService(networkServices!!, VERSION_INFO), NodeRegistrationOption(networkRootTrustStorePath, networkRootTrustStorePassword)).generateKeysAndRegister()
                    }
                }
            }.forEach(Thread::join)

            // Fetch the network params and store them in the `baseDirectory`
            val versionInfo = VersionInfo(PLATFORM_VERSION, CordaVersionProvider.releaseVersion, CordaVersionProvider.revision, CordaVersionProvider.vendor)
            val networkMapClient = NetworkMapClient(parsedConfig.compatibilityZoneURL!!, versionInfo)
            val trustRootCertificate = X509KeyStore.fromFile(networkRootTrustStorePath, networkRootTrustStorePassword).getCertificate(CORDA_ROOT_CA)
            networkMapClient.start(trustRootCertificate)
            val networkParamsReader = NetworkParametersReader(
                    trustRootCertificate,
                    networkMapClient,
                    baseDirectory)
            networkParamsReader.read()

            ExitCodes.SUCCESS
        } catch (e: Exception) {
            e.printStackTrace()
            ExitCodes.FAILURE
        }
    }

    private fun String.toFileName(): String {
        return replace("[^a-zA-Z0-9-_.]".toRegex(), "_")
    }
}