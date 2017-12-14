package net.corda.nodeapi.internal

import net.corda.core.crypto.DigitalSignature
import net.corda.core.crypto.SecureHash
import net.corda.core.crypto.verify
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.SerializedBytes
import net.corda.core.serialization.deserialize
import net.corda.nodeapi.internal.crypto.X509Utilities
import java.security.SignatureException
import java.security.cert.CertPathValidatorException
import java.security.cert.X509Certificate
import java.time.Duration
import java.time.Instant

// TODO: Need more discussion on rather we should move this class out of internal.
/**
 * Data class containing hash of [NetworkParameters] and network participant's [NodeInfo] hashes.
 */
@CordaSerializable
data class NetworkMap(val nodeInfoHashes: List<SecureHash>, val networkParameterHash: SecureHash)

/**
 * @property minimumPlatformVersion
 * @property notaries
 * @property eventHorizon
 * @property maxMessageSize Maximum P2P message sent over the wire in bytes.
 * @property maxTransactionSize Maximum permitted transaction size in bytes.
 * @property modifiedTime
 * @property epoch Version number of the network parameters. Starting from 1, this will always increment on each new set
 * of parameters.
 */
// TODO Wire up the parameters
@CordaSerializable
data class NetworkParameters(
        val minimumPlatformVersion: Int,
        val notaries: List<NotaryInfo>,
        val eventHorizon: Duration,
        val maxMessageSize: Int,
        val maxTransactionSize: Int,
        val modifiedTime: Instant,
        val epoch: Int
) {
    init {
        require(minimumPlatformVersion > 0) { "minimumPlatformVersion must be at least 1" }
        require(notaries.distinctBy { it.identity } == notaries) { "Duplicate notary identities" }
        require(epoch > 0) { "epoch must be at least 1" }
    }
}

@CordaSerializable
data class NotaryInfo(val identity: Party, val validating: Boolean)

/**
 * A serialized [NetworkMap] and its signature and certificate. Enforces signature validity in order to deserialize the data
 * contained within.
 */
@CordaSerializable
class SignedNetworkMap(val raw: SerializedBytes<NetworkMap>, val sig: DigitalSignatureWithCert) {
    /**
     * Return the deserialized NetworkMap if the signature and certificate can be verified.
     *
     * @throws CertPathValidatorException if the certificate path is invalid.
     * @throws SignatureException if the signature is invalid.
     */
    @Throws(SignatureException::class, CertPathValidatorException::class)
    fun verified(trustedRoot: X509Certificate): NetworkMap {
        sig.by.publicKey.verify(raw.bytes, sig)
        // Assume network map cert is under the default trust root.
        X509Utilities.validateCertificateChain(trustedRoot, sig.by, trustedRoot)
        return raw.deserialize()
    }
}

// TODO: This class should reside in the [DigitalSignature] class.
/** A digital signature that identifies who the public key is owned by, and the certificate which provides prove of the identity */
class DigitalSignatureWithCert(val by: X509Certificate, val signatureBytes: ByteArray) : DigitalSignature(signatureBytes)