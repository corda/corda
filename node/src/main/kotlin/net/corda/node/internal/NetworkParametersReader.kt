package net.corda.node.internal

import net.corda.core.crypto.SecureHash
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.services.NetworkParametersStorage
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.MAX_HASH_HEX_SIZE
import net.corda.core.utilities.contextLogger
import net.corda.core.utilities.trace
import net.corda.node.services.network.NetworkMapClient
import net.corda.node.utilities.AppendOnlyPersistentMap
import net.corda.nodeapi.internal.crypto.X509CertificateFactory
import net.corda.nodeapi.internal.crypto.X509Utilities
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_UPDATE_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.network.verifiedNetworkMapCert
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.NODE_DATABASE_PREFIX
import org.apache.commons.lang.ArrayUtils
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.cert.X509Certificate
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Lob

class NetworkParametersReader(private val trustRoot: X509Certificate,
                              private val networkMapClient: NetworkMapClient?,
                              private val baseDirectory: Path) {
    companion object {
        private val logger = contextLogger()
    }

    sealed class Error(message: String) : Exception(message) {
        class ParamsNotConfigured : Error("Couldn't find network parameters file and compatibility zone wasn't configured/isn't reachable.")
        class NetworkMapNotConfigured : Error("Node hasn't been configured to connect to a network map from which to get the network parameters.")
        class OldParamsAndUpdate : Error(
                "Both network parameters and network parameters update files don't match" +
                        "parameters advertised by network map. Please update node to use correct network parameters file."
        )

        class OldParams(previousParametersHash: SecureHash, advertisedParametersHash: SecureHash) : Error(
                "Node uses parameters with hash: $previousParametersHash but network map is advertising: " +
                        "$advertisedParametersHash. Please update node to use correct network parameters file."
        )
    }

    private val networkParamsFile = baseDirectory / NETWORK_PARAMS_FILE_NAME

    fun read(): NetworkParametersAndSigned {
        val advertisedParametersHash = try {
            networkMapClient?.getNetworkMap()?.payload?.networkParameterHash
        } catch (e: Exception) {
            logger.info("Unable to download network map", e)
            // If NetworkMap is down while restarting the node, we should be still able to continue with parameters from file
            null
        }
        val signedParametersFromFile = if (networkParamsFile.exists()) {
            networkParamsFile.readObject<SignedNetworkParameters>()
        } else {
            null
        }
        val signedParameters = if (advertisedParametersHash != null) {
            // TODO On one hand we have node starting without parameters and just accepting them by default,
            //  on the other we have parameters update process - it needs to be unified. Say you start the node, you don't have matching parameters,
            //  you get them from network map, but you have to run the approval step.
            if (signedParametersFromFile == null) { // Node joins for the first time.
                downloadParameters(advertisedParametersHash)
            } else if (signedParametersFromFile.raw.hash == advertisedParametersHash) { // Restarted with the same parameters.
                signedParametersFromFile
            } else { // Update case.
                readParametersUpdate(advertisedParametersHash, signedParametersFromFile.raw.hash)
            }
        } else { // No compatibility zone configured. Node should proceed with parameters from file.
            signedParametersFromFile ?: throw Error.ParamsNotConfigured()
        }

        return NetworkParametersAndSigned(signedParameters, trustRoot)
    }

    private fun readParametersUpdate(advertisedParametersHash: SecureHash, previousParametersHash: SecureHash): SignedNetworkParameters {
        val parametersUpdateFile = baseDirectory / NETWORK_PARAMS_UPDATE_FILE_NAME
        if (!parametersUpdateFile.exists()) {
            throw Error.OldParams(previousParametersHash, advertisedParametersHash)
        }
        val signedUpdatedParameters = parametersUpdateFile.readObject<SignedNetworkParameters>()
        if (signedUpdatedParameters.raw.hash != advertisedParametersHash) {
            throw Error.OldParamsAndUpdate()
        }
        parametersUpdateFile.moveTo(networkParamsFile, StandardCopyOption.REPLACE_EXISTING)
        logger.info("Scheduled update to network parameters has occurred - node now updated to these new parameters.")
        return signedUpdatedParameters
    }

    // Used only when node joins for the first time.
    private fun downloadParameters(parametersHash: SecureHash): SignedNetworkParameters {
        logger.info("No network-parameters file found. Expecting network parameters to be available from the network map.")
        networkMapClient ?: throw Error.NetworkMapNotConfigured()
        val signedParams = networkMapClient.getNetworkParameters(parametersHash)
        signedParams.serialize().open().copyTo(baseDirectory / NETWORK_PARAMS_FILE_NAME)
        return signedParams
    }

    // By passing in just the SignedNetworkParameters object, this class guarantees that the networkParameters property
    // could have only been derived from it.
    class NetworkParametersAndSigned(val signed: SignedNetworkParameters, trustRoot: X509Certificate) {
        // for backwards compatibility we allow netparams to be signed with the networkmap cert,
        // but going forwards we also accept the distinct netparams cert as well
        val networkParameters: NetworkParameters = signed.verifiedNetworkParametersCert(trustRoot)
        operator fun component1() = networkParameters
        operator fun component2() = signed
    }
}

// TODO NetworkParametersReader + NodeParametersStorage refactor
class NodeParametersStorage(
        cacheFactory: NamedCacheFactory,
        private val database: CordaPersistence,
        // TODO It's very inefficient solution (at least at the beginning when node joins without historical data)
        // We could have historic parameters endpoint or always add parameters as an attachment to the transaction.
        private val networkMapClient: NetworkMapClient?
) : NetworkParametersStorage, SingletonSerializeAsToken() {
    private lateinit var trustRoot: X509Certificate

    companion object {
        private val log = contextLogger()

        fun createParametersMap(cacheFactory: NamedCacheFactory): AppendOnlyPersistentMap<SecureHash, SignedDataWithCert<NetworkParameters>, PersistentNetworkParameters, String> {
            return AppendOnlyPersistentMap(
                    cacheFactory = cacheFactory,
                    name = "NodeParametersStorage_networkParametersByHash",
                    toPersistentEntityKey = { it.toString() },
                    fromPersistentEntity = {
                        Pair(
                                SecureHash.parse(it.hash),
                                it.signedNetworkParameters
                        )
                    },
                    toPersistentEntity = { key: SecureHash, value: SignedDataWithCert<NetworkParameters> ->
                        PersistentNetworkParameters(key.toString(), value.raw.bytes, value.sig.bytes, value.sig.by.encoded,
                                X509Utilities.buildCertPath(value.sig.parentCertsChain).encoded)
                    },
                    persistentEntityClass = PersistentNetworkParameters::class.java
            )
        }
    }

    fun start(currentSignedParameters: SignedDataWithCert<NetworkParameters>, trustRoot: X509Certificate) {
        this.trustRoot = trustRoot
        saveParameters(currentSignedParameters)
        _currentHash = currentSignedParameters.raw.hash
    }

    private lateinit var _currentHash: SecureHash
    override val currentParametersHash: SecureHash get() = _currentHash
    override val currentParameters: NetworkParameters
        get() = readParametersFromHash(currentParametersHash)
                ?: throw IllegalAccessException("No current parameters for the network provided")
    // TODO Have network map serve special "starting" parameters as parameters for resolution for older transactions?
    override val defaultParametersHash: SecureHash get() = currentParametersHash
    override val defaultParameters: NetworkParameters
        get() = readParametersFromHash(defaultParametersHash)
                ?: throw IllegalAccessException("No default parameters for the network provided")

    private val hashToParameters = createParametersMap(cacheFactory)

    override fun readParametersFromHash(hash: SecureHash): NetworkParameters? {
        return database.transaction {
            hashToParameters[hash]?.raw?.deserialize() ?: tryDownloadUnknownParameters(hash)
        }
    }

    override fun saveParameters(signedNetworkParameters: SignedNetworkParameters) {
        log.trace { "Saving new network parameters to network parameters storage." }
        val networkParameters = signedNetworkParameters.verified()
        val hash = signedNetworkParameters.raw.hash
        log.trace { "Parameters to save $networkParameters with hash $hash" }
        hashToParameters.addWithDuplicatesAllowed(hash, signedNetworkParameters)
    }

    // TODO For the future we could get them also as signed (by network operator) attachments on transactions.
    private fun tryDownloadUnknownParameters(parametersHash: SecureHash): NetworkParameters? {
        return if (networkMapClient != null) {
            try {
                val signedParams = networkMapClient.getNetworkParameters(parametersHash)
                val networkParameters = signedParams.verifiedNetworkMapCert(trustRoot)
                saveParameters(signedParams)
                networkParameters
            } catch (e: Exception) {
                log.warn("Failed to downolad historical network parameters with hash $parametersHash", e)
                null
            }
        } else {
            log.warn("Tried to download historical network parameters with hash $parametersHash, but network map url isn't configured")
            null
        }
    }

    @Entity
    @javax.persistence.Table(name = "${NODE_DATABASE_PREFIX}network_parameters")
    class PersistentNetworkParameters(
            @Id
            @Column(name = "hash", length = MAX_HASH_HEX_SIZE, nullable = false)
            val hash: String = "",

            // Stored as serialized bytes because network parameters structure evolves over time.
            @Lob
            @Column(name = "parameters_bytes", nullable = false)
            val networkParametersBytes: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY,

            @Lob
            @Column(name = "signature_bytes", nullable = false)
            private val signature: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY,

            // First certificate in the certificate chain.
            @Lob
            @Column(name = "cert", nullable = false)
            private val certificate: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY,

            // Parent certificate path (the first one is stored separately), so node is agnostic to certificate hierarchy.
            @Lob
            @Column(name = "parent_cert_path", nullable = false)
            private val certPath: ByteArray = ArrayUtils.EMPTY_BYTE_ARRAY
    ) {
        val networkParameters: NetworkParameters get() = networkParametersBytes.deserialize()
        val signedNetworkParameters: SignedDataWithCert<NetworkParameters>
            get() {
                val certChain = X509CertificateFactory().delegate.generateCertPath(certPath.inputStream())
                        .certificates.map { it as X509Certificate }
                val signWithCert = DigitalSignatureWithCert(X509CertificateFactory().generateCertificate(certificate.inputStream()), certChain, signature)
                return SignedDataWithCert(SerializedBytes(networkParametersBytes), signWithCert)
            }
    }
}
