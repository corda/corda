package net.corda.netparams

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.core.crypto.Crypto
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.core.serialization.serialize
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.crypto.X509KeyStore
import net.corda.serialization.internal.*
import net.corda.serialization.internal.amqp.*
import picocli.CommandLine.*
import java.io.File
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.KeyPair
import java.security.cert.X509Certificate
import java.time.Instant

/**
 * NetworkParameters signing tool for Corda
 *
 * This utility can be used to create and sign NetworkParameters files. This is useful for manual bootstrapping
 * of a corda network, or by distribution via the NetworkMap
 *
 * By default the NetworkParameters will be signed with the development NetworkMap key, which is what happens
 * currently with the Corda network bootstrapper. Arbitrary signing keys can be specified using
 * the --keystore and --alias parameters, in which case the specified Java keystore (and key) will be used instead.
 *
 * Values for the NetworkParameters are specified in a configuration file (in HOCON format),
 * using the --config option. An example content is shown below:
 *
 *    notaries : []
 *    minimumPlatformVersion = 1
 *    maxMessageSize = 10485760
 *    maxTransactionSize = 10485760
 *    whitelistContracts = {}
 *    eventHorizonDays = 30
 *    epoch = 1
 *
 * Notary nodes for the network can be specified in the notaries[] section (as a string of filenames), as below:
 *
 *    notaries: ["/path/to/nodeinfo"]
 *
 * or via the commandline with the --notary-info option. Notaries are identified either by their respective nodeInfo files,
 * or by a path to the identity certificate (JKS file containing the identity cert)
 *
 * Example usage:
 *
 * # Generate NetworkParameters with one notary, identified by it's NodeInfo file (and signed with default development NetworkMap key)
 * java -jar netparams.jar --config netparams.conf --notary-info /path/to/nodeinfo --output /path/to/network-parameters
 *
 * # Generate using the notary's identity certificate (instead of it's nodeinfo)
 * java -jar netparams.jar --config netparams.conf --notary-keystore /path/to/node/certificates/nodekeystore.jks
 *
 * # Generate using an arbitrary 'netparams' signing key
 * java -jar netparams.jar --config netparams.conf --notary-info /path/to/nodeinfo --keystore /my/keystore.jks --keyalias netparams
 *
 */
fun main(args: Array<String>) {
    NetParamsSigner().start(args)
}

class NetParamsSigner : CordaCliWrapper("netparams-signer", "Sign network parameters") {

    @Option(names = ["--config"], paramLabel = "file", description = ["Network Parameters config."])
    private var configFile: Path? = null

    @Option(names = ["--output"], paramLabel = "file", description = ["Network Parameters "])
    private var outputFile: Path? = null

    @Option(names = ["--notary-info"], paramLabel = "nodeInfo", description = ["Path to notary NodeInfo"])
    private var notaryInfos: MutableList<Path> = mutableListOf<Path>()

    @Option(names = ["--notary-keystore"], paramLabel = "keyStore", description = ["Path to node keystore"])
    private var notaryKeyStores: MutableList<Path> = mutableListOf<Path>()

    @Option(names = ["--notary-keypass"], paramLabel = "password", description = ["Password to node keystore"])
    private var notaryKeyPasswords: MutableList<String> = mutableListOf<String>()

    @Option(names = ["--keystore"], description = ["Keystore containing NetworkParameters signing key"])
    private var keyStorePath: Path? = null

    @Option(names = ["--keystore-pass"], description = ["Keystore password"])
    private var keyStorePass: String? = null

    @Option(names = ["--keyalias"], description = ["Alias of signing key"])
    private var keyAlias: String? = null

    @Option(names = ["--keypass"], description = ["Password of signing key"])
    private var keyPass: String? = null

    private fun getInput(prompt: String): String {
        print(prompt)
        System.out.flush()
        val console = System.console()
        if (console != null)
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

    class CertificatePathAndKeyPair(val certPath: List<X509Certificate>, private val certificateAndKeyPair: CertificateAndKeyPair) {
        val keyPair: KeyPair
            get() = certificateAndKeyPair.keyPair
    }

    override fun runProgram(): Int {
        require(configFile != null) { "The --config parameter must be specified" }

        initialiseSerialization()

        // notary specified by nodeInfo paths
        val n1: List<NotaryInfo> = notaryInfos.map {
            val party = identityFromNodeInfoPath(it.toFile())
            NotaryInfo(party, false)
        }

        // notary specified by keystore path (+password)
        require(notaryKeyStores.size == notaryKeyPasswords.size)
        val n2: List<NotaryInfo> = notaryKeyStores.zip(notaryKeyPasswords) { path, password ->
            val party = identityFromKeyStore(path.toFile(), password)
            NotaryInfo(party, false)
        }

        val networkParameters = parametersFromConfig(configFile!!, n1 + n2)
        print(networkParameters.toString())

        val signingkey = if (keyStorePath != null) {

            require(keyAlias != null) { "The --keyAlias parameters must be specified" }

            if (keyStorePass == null)
                keyStorePass = getInput("Store password (${keyStorePath?.fileName}): ")

            if (keyPass == null)
                keyPass = getInput("Key password (${keyAlias}): ")

            val keyStore = X509KeyStore.fromFile(keyStorePath!!, keyStorePass!!)
            val signingKey = keyStore.getCertificateAndKeyPair(keyAlias!!, keyPass!!)
            val x509Chain = keyStore.getCertificateChain(keyAlias!!)

            CertificatePathAndKeyPair(x509Chain, signingKey)
        } else {
            // issue from the development root
            CertificatePathAndKeyPair(emptyList(), createDevNetworkMapCa())
        }

        // sign and include the certificate path
        val signedNetParams = networkParameters.signWithCertPath(signingkey.keyPair.private, signingkey.certPath)

        if (outputFile != null) {
            print("\nWriting: " + outputFile)
            val ssnp = signedNetParams.serialize()
            ssnp.open().copyTo(outputFile!!, StandardCopyOption.REPLACE_EXISTING)
        } else {
            print("\nUse --output to write results")
        }

        return 0
    }

    fun identityFromKeyStore(keyStorePath: File, keyStorePass: String, alias: String = "identity-private-key"): Party {

        val keyStore = X509KeyStore.fromFile(keyStorePath.toPath(), keyStorePass)
        return Party(keyStore.getCertificate(alias))
    }

    fun identityFromNodeInfoPath(nodeInfoPath: File): Party {

        val signedNodeInfo = nodeInfoPath.toPath().readObject<SignedNodeInfo>()
        val nodeInfo = signedNodeInfo.verified()

        return nodeInfo.legalIdentities.last()
    }

    fun parseConfig(config: Config, optionalNotaryList: List<NotaryInfo>): NetworkParameters {

        // convert the notary list (of nodeinfo paths) to NotaryInfos
        val notaryList: List<NotaryInfo> = config.getConfigList("notaries").map {
            val path = it.getString("notaryNodeInfoFile")
            val party = identityFromNodeInfoPath(File(path))

            NotaryInfo(party, it.getBoolean("validating"))
        }

        return NetworkParameters(
                minimumPlatformVersion = config.getInt("minimumPlatformVersion"),
                maxMessageSize = config.getInt("maxMessageSize"),
                maxTransactionSize = config.getInt("maxTransactionSize"),
                epoch = config.getInt("epoch"),
                modifiedTime = Instant.now(),
                whitelistedContractImplementations = emptyMap(), // TODO: not supported
                notaries = notaryList + optionalNotaryList
        )
    }

    fun parametersFromConfig(file: Path, notaryList: List<NotaryInfo>): NetworkParameters {

        val parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val config = ConfigFactory.parseFile(file.toFile(), parseOptions).resolve()

        return parseConfig(config, notaryList)
    }
}

