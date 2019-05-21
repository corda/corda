package net.corda.nodeinfo

import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.core.crypto.*
import net.corda.core.identity.PartyAndCertificate
import net.corda.core.internal.*
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.*
import picocli.CommandLine.*
import java.io.File
import java.nio.file.Path
import java.security.cert.CertificateFactory

/**
 * NodeInfo signing tool for Corda
 *
 * This utility can be used to generate nodeInfo files without having to run a corda node
 *
 * The java keystore containing the signing key for the nodeInfo must specified at the commandline
 * using the --keyStore parameter. HSM are not currently supported.
 *
 * The resulting filename of the nodeinfo will be displayed to stdout
 *
 * Example usage:
 *
 *   # generate a nodeinfo
 *   java -jar nodeinfo.jar --address host:port --outdir /nodedir --keystore /nodedir/certificates/nodekeystore.jks
 *
 *   nodeinfo will prompt you for the keystore password, and the password of the node identity private key:
 *    Store password (nodekeystore.jks): *******
 *    Key password (identity-private-key): *******
 *
 *   # display information about an existing nodeinfo (name, address etc)
 *   java -jar nodeinfo.jar --display nodeinfo-12345678
 *
 */
fun main(args: Array<String>) {
    NodeInfoSigner().start(args)
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

    @Option(names = ["--platform-version"], paramLabel = "int", description = ["Platform version that this node supports"])
    private var platformVersion: Int = 4

    @Option(names = ["--serial"], paramLabel = "long", description = [""])
    private var serial: Long = 0

    @Option(names = ["--outdir"], paramLabel = "directory", description = ["Output directory"])
    private var outputDirectory: Path? = null

    @Option(names = ["--keystore"],  description = ["Keystore containing identity certificate for signing"])
    private var keyStorePath: Path? = null

    @Option(names = ["--keystore-pass"], description = ["Keystore password (will prompt if not specified)"])
    private var keyStorePass: String? = null

    @Option(names = ["--keyalias"],  description = ["Alias of signing key - default is identity-private-key"])
    private var keyAlias: String? = "identity-private-key"

    @Option(names = ["--keypass"], description = ["Password of signing key (will prompt if not specified)"])
    private var keyPass: String? = null

    private fun getInput(prompt: String): String {
        print(prompt)
        System.out.flush()
        val console = System.console()
        if(console != null)
            return console.readPassword().toString()
        else
            return readLine()!!
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
        val keyStore   = X509KeyStore.fromFile(keyStorePath!!, keyStorePass!!)
        val signingKey = keyStore.getCertificateAndKeyPair(keyAlias!!, keyPass!!)
        val x509Chain  = keyStore.getCertificateChain(keyAlias!!)

        val cf = CertificateFactory.getInstance("X.509")
        val certPath = cf.generateCertPath(x509Chain)
        val identityList = listOf(PartyAndCertificate(certPath))

        val nodeInfo = NodeInfo(addressList, identityList, platformVersion, serial)
        val serializedNodeInfo = nodeInfo.serialize()

        val sig = signingKey.keyPair.sign(serializedNodeInfo.bytes).withoutKey()  // pure DigitalSignature
        val sni = SignedNodeInfo(serializedNodeInfo, listOf(sig))

        return NodeInfoAndSignedNodeInfo(nodeInfo, sni)
    }

    override fun runProgram(): Int {

        initialiseSerialization()

        if(displayPath != null) {
            val nodeInfo = nodeInfoFromFile(displayPath!!.toFile())

            println("identities:      " + nodeInfo.legalIdentities[0].name)
            println("address:         " + nodeInfo.addresses[0])
            println("platformVersion: " + nodeInfo.platformVersion)
            println("serial           " + nodeInfo.serial)
            return 0;
        }
        else {
            require(addressList.size > 0){ "At least one --address must be specified" }
            require(outputDirectory != null) { "The --outdir parameter must be specified" }
            require(keyStorePath != null && keyAlias != null) { "The --keystore and --keyalias parameters must be specified" }
        }

        if(keyStorePass == null)
            keyStorePass = getInput("Store password (${keyStorePath?.fileName}): ")

        if(keyPass == null)
            keyPass = getInput("Key password (${keyAlias}): ")


        val nodeInfoSigned = generateNodeInfo()
        val fileNameHash = nodeInfoSigned.nodeInfo.legalIdentities[0].name.serialize().hash

        val outputFile = outputDirectory!!.toString() / "nodeinfo-${fileNameHash.toString()}"

        println(outputFile)

        outputFile!!.toFile().writeBytes(nodeInfoSigned.signedInfoNode.serialize().bytes)
        return 0
    }

    fun nodeInfoFromFile(nodeInfoPath: File) : NodeInfo {
        var serializedNodeInfo = SerializedBytes<SignedNodeInfo>(nodeInfoPath.toPath().readAll())
        var signedNodeInfo = serializedNodeInfo.deserialize()
        return signedNodeInfo.verified()
    }

}

