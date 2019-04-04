package net.corda.nodeinfo

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
//import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.core.crypto.*
import net.corda.core.identity.Party
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.node.services.AttachmentId
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.*
import net.corda.nodeapi.internal.config.CertificateStore
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.KEYSTORE_TYPE
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.nodeapi.internal.network.NetworkBootstrapper
import net.corda.nodeapi.internal.network.NetworkBootstrapperWithOverridableParameters
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.*
import net.corda.serialization.internal.amqp.custom.InstantSerializer
import picocli.CommandLine.*
import java.io.Console
import java.io.File
import java.io.FileInputStream
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Instant

fun main(args: Array<String>) {
    NodeInfoSigner().start(args)
}

fun nodeInfoIdentity(nodeInfo: NodeInfo) : Party {
    return nodeInfo.legalIdentities.last()
}

class NetworkHostAndPortConverter : ITypeConverter<NetworkHostAndPort> {
    override fun convert(value: String?): NetworkHostAndPort {
        return NetworkHostAndPort.parse(value!!)
    }
}

class NodeInfoSigner : CordaCliWrapper("nodeinfo-signer", "Display and generate nodeinfos") {

    @Option(names = ["--display"], paramLabel = "nodeinfo-file", description = ["Path to NodeInfo"])
    private var displayPath: Path? = null

    @Option(names = ["--address"], paramLabel = "host:port", description = ["Public address of node"], converter = [NetworkHostAndPortConverter::class])
    private var addressList: MutableList<NetworkHostAndPort> = mutableListOf<NetworkHostAndPort>()

    @Option(names = ["--platformVersion"], paramLabel = "int", description = ["Platform version that this node supports"])
    private var platformVersion: Int = 4

    @Option(names = ["--serial"], paramLabel = "long", description = [""])
    private var serial: Long = 0

    @Option(names = ["--outdir"], paramLabel = "directory", description = ["Output directory"])
    private var outputDirectory: Path? = null

    @Option(names = ["--keyStore"],  description = ["Keystore containing identity certificate"])
    private var keyStorePath: Path? = null

    @Option(names = ["--keyStorePass"], description = ["Keystore password"])
    private var keyStorePass: String? = null

    @Option(names = ["--keyAlias"],  description = ["Alias of signing key"])
    private var keyAlias: String? = "identity-private-key"

    @Option(names = ["--keyPass"], description = ["Password of signing key"])
    private var keyPass: String? = null

    private fun getInput(prompt: String, isPassword: Boolean = false): String {
        print(prompt)
        val console = System.console()
        return if (isPassword) {
            String(console.readPassword())
        } else {
            readLine() ?: ""
        }
    }

    private object AMQPInspectorSerializationScheme : AbstractAMQPSerializationScheme(emptyList()) {
        override fun canDeserializeVersion(magic: CordaSerializationMagic, target: SerializationContext.UseCase): Boolean {
            return magic == amqpMagic
        }

        override fun rpcClientSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerSerializerFactory(context: SerializationContext) = throw UnsupportedOperationException()
    }

    private fun initialiseSerialization() {
        nodeSerializationEnv = SerializationEnvironment.with(
                SerializationFactoryImpl().apply {
                    registerScheme(AMQPInspectorSerializationScheme)
                },
                AMQP_P2P_CONTEXT)
    }

    class NodeInfoAndSignedNodeInfo(val nodeInfo : NodeInfo,  val signedInfoNode: SignedNodeInfo)

    fun generateNodeInfo() : NodeInfoAndSignedNodeInfo {
        var keyStore   = X509KeyStore.fromFile(keyStorePath!!, keyStorePass!!)
        var signingKey = keyStore.getCertificateAndKeyPair(keyAlias!!, keyPass!!)
        var x509Chain  = keyStore.getCertificateChain(keyAlias!!)

        var cf = CertificateFactory.getInstance("X.509")
        var certPath = cf.generateCertPath(x509Chain)

        var identityList = listOf<PartyAndCertificate>(PartyAndCertificate(certPath))


        var nodeInfo = NodeInfo(addressList, identityList, platformVersion, serial)
        var serializedNodeInfo = nodeInfo.serialize()

        var sig = signingKey.keyPair.sign(serializedNodeInfo.bytes).withoutKey()  // pure DigitalSignature
        var sni = SignedNodeInfo(serializedNodeInfo, listOf(sig))

        return NodeInfoAndSignedNodeInfo(nodeInfo, sni)
    }


    override fun runProgram(): Int {

        initialiseSerialization()

        if(displayPath != null) {
            var nodeInfo = nodeInfoFromFile(displayPath!!.toFile())

            println("identities:      " + nodeInfo.legalIdentities[0].name)

            println("address:         " + nodeInfo.addresses[0])
            println("platformVersion: " + nodeInfo.platformVersion)
            println("serial           " + nodeInfo.serial)
            return 0;
        }
        else {
            require(addressList.size > 0){ "At least one --address must be specified" }
            require(outputDirectory != null) { "The --outdir parameter must be specified" }
            require(keyStorePath != null && keyStorePass != null && keyAlias != null && keyPass != null) { "The --keyStorePath, --keyStorePass and --keyAlias and --keyPass parameters must be specified" }
        }


        if(outputDirectory != null) {
            var nodeInfo = generateNodeInfo()
            var fileNameHash = nodeInfo.nodeInfo.legalIdentities[0].name.serialize().hash

            var outputFile = outputDirectory!!.toString() / "nodeinfo-${fileNameHash.toString()}"

            println(outputFile)

            outputFile!!.toFile().writeBytes(nodeInfo.signedInfoNode.serialize().bytes)
        }
        else {
            print("\nUse --output to write results")
        }

        return 0
    }

    //
    // return the Identity from the specified nodekeystore
    //
    fun identityFromKeyStore(keyStorePath: File, keyStorePass: String, alias: String = "identity-private-key") : Party {
        var keyStore = X509KeyStore.fromFile(keyStorePath.toPath(), keyStorePass)
        return Party(keyStore.getCertificate(alias))
    }

    fun nodeInfoFromFile(nodeInfoPath: File) : NodeInfo {
        var serializedNodeInfo = SerializedBytes<SignedNodeInfo>(nodeInfoPath.toPath().readAll())
        var signedNodeInfo = serializedNodeInfo.deserialize()
        return signedNodeInfo.verified()
    }

    fun identityFromNodeInfo(nodeInfoPath: File) : Party{

        var serializedNodeInfo = SerializedBytes<SignedNodeInfo>(nodeInfoPath.toPath().readAll())
        var signedNodeInfo = serializedNodeInfo.deserialize()
        return nodeInfoIdentity(signedNodeInfo.verified())
    }



}

