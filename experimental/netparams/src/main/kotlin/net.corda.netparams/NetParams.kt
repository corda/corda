package net.corda.netparams

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigParseOptions
//import com.typesafe.config.ConfigParseOptions
import net.corda.cliutils.CordaCliWrapper
import net.corda.cliutils.start
import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.SignedData
import net.corda.core.identity.Party
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
import java.time.Instant

fun main(args: Array<String>) {
    NetParamsSigner().start(args)
}

/*data class WhitelistContracts(val cordappsJars: List<Path> = emptyList(),
                              val contracts: List<WhitelistContract> = emptyList(),
                              val exclude: List<String> = emptyList())

data class WhitelistContract(val className: String, val attachmentIds: List<SecureHash>)

data class NotaryConfig(private val notaryNodeInfoFile: Path,
                        private val validating: Boolean) {
    val nodeInfo by lazy { toNodeInfo() }
    fun toNotaryInfo(): NotaryInfo {
        // It is always the last identity (in the list of identities) that corresponds to the notary identity.
        // In case of a single notary, the list has only one element. In case of distributed notaries the list has
        // two items and the second one corresponds to the notary identity.
        return NotaryInfo(nodeInfo.legalIdentities.last(), validating)
    }

    private fun toNodeInfo(): NodeInfo {
        return if (notaryNodeInfoFile.exists()) {
            notaryNodeInfoFile.readObject<SignedNodeInfo>().verified()
        } else {
            check(!notaryNodeInfoFile.startsWith("\'")) {
                "File with notary node info file: $notaryNodeInfoFile doesn't exist. Probable cause is use of wrong quotes in config (use \" or any at all)"
            }
            throw IllegalArgumentException("File with notary node info file: $notaryNodeInfoFile doesn't exist")
        }
    }
}

class NetworkParametersConfig(
    val minimumPlatformVersion: Int,
    val notaries: List<NotaryConfig>,
    val maxMessageSize: Int,
    val maxTransactionSize: Int,
    val eventHorizonDays: Int,
    val whitelistContracts: WhitelistContracts = WhitelistContracts()
)//val parametersUpdate: ParametersUpdateConfig?)
*/

fun nodeInfoIdentity(nodeInfo: NodeInfo) : Party {
    return nodeInfo.legalIdentities.last()
}

class NetParamsSigner : CordaCliWrapper("netparams-signer", "Sign network parameters") {

    @Option(names = ["--config"], paramLabel = "file", description = ["Network Parameters config."])
    private var configFile: Path? = null

    @Option(names = ["--output"], paramLabel = "file", description = ["Network Parameters "])
    private var outputFile: Path? = null

    @Option(names = ["--notaryInfo"], paramLabel = "nodeInfo", description = ["Path to notary NodeInfo"])
    private var notaryInfos: MutableList<Path> = mutableListOf<Path>()

    @Option(names = ["--notaryKeyStore"], paramLabel = "keyStore", description = ["Path to node keystore"])
    private var notaryKeyStores: MutableList<Path> = mutableListOf<Path>()

    @Option(names = ["--notaryKeyPass"], paramLabel = "password", description = ["Password to node keystore"])
    private var notaryKeyPasswords: MutableList<String> = mutableListOf<String>()

    @Option(names = ["--keyStore"],  description = ["Keystore containing NetworkParameters signing key"])
    private var keyStorePath: Path? = null

    @Option(names = ["--keyStorePass"], description = ["Keystore password"])
    private var keyStorePass: String? = null

    @Option(names = ["--keyAlias"],  description = ["Alias of signing key"])
    private var keyAlias: String? = null

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

    private fun verifyInputs() {
        require(configFile != null ) { "The --config parameter must be specified" }
        //require(outputFile != null ) { "The --output parameter must be specified" }
        require(keyStorePath == null || keyStorePath != null && keyStorePass != null && keyAlias != null) { "The --keyStorePath, --keyStorePass and --keyAlias parameters must be specified" }
    }

    override fun runProgram(): Int {
        verifyInputs()
        initialiseSerialization()

        // notary specified by nodeInfo paths
        var n1: List<NotaryInfo> = notaryInfos.map {
            var party = identityFromNodeInfo(it.toFile())
            NotaryInfo(party, false)
        }

        // notary specified by keystore path (+password)
        require(notaryKeyStores.size == notaryKeyPasswords.size)
        var n2: List<NotaryInfo> = notaryKeyStores.zip(notaryKeyPasswords) { path, password ->
            var party = identityFromKeyStore(path.toFile(), password)
            NotaryInfo(party, false)
        }

        var networkParameters = parametersFromConfig(configFile!!, n1 + n2)
        print(networkParameters.toString())

        var signingkey = if(keyStorePath != null) {

            require(keyStorePass != null && keyAlias != null && keyPass != null) { "The --keyStorePath, --keyStorePass and --keyAlias and --keyPass parameters must be specified" }

            var keyStore = X509KeyStore.fromFile(keyStorePath!!, keyStorePass!!)
            keyStore.getCertificateAndKeyPair(keyAlias!!, keyPass!!)
        }
        else {
            // issue from the development root
            createDevNetworkMapCa()
        }

        // sign & serialise
        var serializedSignedNetParams = signingkey.sign(networkParameters).serialize()

        if(outputFile != null) {
            print("\nWriting: " + outputFile)
            serializedSignedNetParams.open().copyTo(outputFile!!, StandardCopyOption.REPLACE_EXISTING)
        }
        else {
            print("\nUse --output to write results")
        }

        return 0
    }

    fun signParameters(networkParameters: NetworkParameters, signingKey: CertificateAndKeyPair) : SerializedBytes<SignedDataWithCert<NetworkParameters>> {
        var serializedNetParams = signingKey.sign(networkParameters).serialize()
        // serialize & sign
        /*var serializedNetParams = serializer.serialize(networkParameters, context)
        val rawsig          = Crypto.doSign(signingKey.keyPair.private, serializedNetParams.bytes)
        var sigcert         = DigitalSignatureWithCert(signingKey.certificate, rawsig)
        var signedNetParams = SignedDataWithCert<NetworkParameters>(serializedNetParams, sigcert)
        var sssNetParams    = serializer.serialize(signedNetParams, context)
        */
        return serializedNetParams
    }

    //
    // return the Identity from the specified nodekeystore
    //
    fun identityFromKeyStore(keyStorePath: File, keyStorePass: String, alias: String = "identity-private-key") : Party {

        var keyStore = X509KeyStore.fromFile(keyStorePath.toPath(), keyStorePass)
        return Party(keyStore.getCertificate(alias))
    }

    fun identityFromNodeInfo(nodeInfoPath: File) : Party{

        var serializedNodeInfo = SerializedBytes<SignedNodeInfo>(nodeInfoPath.toPath().readAll())
        var signedNodeInfo = serializedNodeInfo.deserialize()
        return nodeInfoIdentity(signedNodeInfo.verified())
    }

    fun parseConfig(config: Config, optionalNotaryList: List<NotaryInfo>) : NetworkParameters {

        // convert the notary list (of nodeinfo paths) to NotaryInfos
        var notaryList: List<NotaryInfo> = config.getConfigList("notaries").map {
            var path = it.getString("notaryNodeInfoFile")
            var party = identityFromNodeInfo(File(path))

            NotaryInfo(party, it.getBoolean("validating"))
        }

        var whitelistContracts = emptyMap<String, List<AttachmentId>>()

        return NetworkParameters(
                minimumPlatformVersion = config.getInt("minimumPlatformVersion"),
                maxMessageSize = config.getInt("maxMessageSize"),
                maxTransactionSize = config.getInt("maxTransactionSize"),
                epoch = config.getInt("epoch"),
                modifiedTime = Instant.now(),
                whitelistedContractImplementations = emptyMap(),//whitelistContracts,
                notaries = notaryList + optionalNotaryList
        )
    }

    fun parametersFromConfig(file: Path, notaryList: List<NotaryInfo>) : NetworkParameters {

        var parseOptions = ConfigParseOptions.defaults().setAllowMissing(true)
        val config = ConfigFactory.parseFile(file.toFile(), parseOptions).resolve()

        return parseConfig(config, notaryList)

    }


}

