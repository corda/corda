package net.corda.nodeapi.internal.network

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.verify
import net.corda.core.identity.Party
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.security.SignatureException
import java.security.cert.CertPathValidatorException
import java.security.cert.X509Certificate
import java.time.Instant

const val NETWORK_PARAMS_FILE_NAME = "network-parameters"

/**
 * Data class containing hash of [NetworkParameters] and network participant's [NodeInfo] hashes.
 */
@CordaSerializable
data class NetworkMap(val nodeInfoHashes: List<SecureHash>, val networkParameterHash: SecureHash)

/**
 * @property minimumPlatformVersion Minimum version of Corda platform that is required for nodes in the network.
 * @property notaries List of well known and trusted notary identities with information on validation type.
 * @property maxMessageSize Maximum P2P message sent over the wire in bytes.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 */
// TODO Add eventHorizon - how many days a node can be offline before being automatically ejected from the network.
//  It needs separate design.
// TODO Currently maxTransactionSize is not wired.
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int
) {
    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
        require(maxMessageSize > 0) { "maxMessageSize must be at least 1" }
        require(maxTransactionSize > 0) { "maxTransactionSize must be at least 1" }
    }
}

@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)

/**
 * A serialized [NetworkMap] and its signature and certificate. Enforces signature validity in order to deserialize the data
 * contained within.
 */
@CordaSerializable
class SignedNetworkMap(val raw: SerializedBytes<NetworkMap>, val signature: DigitalSignatureWithCert) {
    /**
     * Return the deserialized NetworkMap if the signature and certificate can be verified.
     *
     * @throws CertPathValidatorException if the certificate path is invalid.
     * @throws SignatureException if the signature is invalid.
     */
    @Throws(SignatureException::class, CertPathValidatorException::class)
    fun verified(trustedRoot: X509Certificate): NetworkMap {
        signature.by.publicKey.verify(raw.bytes, signature)
        // Assume network map cert is under the default trust root.
        X509Utilities.validateCertificateChain(trustedRoot, signature.by, trustedRoot)
        return raw.deserialize()
    }
}

// TODO: This class should reside in the [DigitalSignature] class.
// TODO: Removing the val from signatureBytes causes serialisation issues
/** A digital signature that identifies who the public key is owned by, and the certificate which provides prove of the identity */
class DigitalSignatureWithCert(val by: X509Certificate, val signatureBytes: ByteArray) : DigitalSignature(signatureBytes)
