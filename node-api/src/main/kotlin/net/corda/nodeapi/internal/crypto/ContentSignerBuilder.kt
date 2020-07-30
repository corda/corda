package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.Crypto.SPHINCS256_SHA256
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.internal.Instances
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.OutputStream
import java.security.InvalidKeyException
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.Signature

/**
 *  Provide extra OID look up for signature algorithm not supported by BouncyCastle.
 *  This builder will use BouncyCastle's JcaContentSignerBuilder as fallback for unknown algorithm.
 */
object ContentSignerBuilder {
    fun build(signatureScheme: SignatureScheme, privateKey: PrivateKey, provider: Provider,
              random: SecureRandom? = null, optimised: Boolean = true): ContentSigner {
        val sigAlgId = signatureScheme.signatureOID
        val signatureInstance = if (optimised)
            Instances.getSignatureInstance(signatureScheme.signatureName, provider)
        else
            Signature.getInstance(signatureScheme.signatureName, provider)

        val sig = try {
            signatureInstance.apply {
                // TODO special handling for Sphincs due to a known BouncyCastle's Sphincs bug we reported.
                //      It is fixed in BC 161b12, so consider updating the below if-statement after updating BouncyCastle.
                if (random != null && signatureScheme != SPHINCS256_SHA256) {
                    initSign(privateKey, random)
                } else {
                    initSign(privateKey)
                }
            }
        } catch(ex: InvalidKeyException) {
            throw InvalidKeyException("Incorrect key type ${privateKey.algorithm} for signature scheme ${signatureInstance.algorithm}", ex)
        }
        return object : ContentSigner {
            private val stream = SignatureOutputStream(sig, optimised)
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgId
            override fun getOutputStream(): OutputStream = stream
            override fun getSignature(): ByteArray = stream.signature
        }
    }

    private class SignatureOutputStream(private val sig: Signature, private val optimised: Boolean) : OutputStream() {
        private var alreadySigned = false
        internal val signature: ByteArray by lazy {
            try {
                alreadySigned = true
                sig.sign()
            } finally {
                if (optimised) {
                    Instances.releaseSignatureInstance(sig)
                }
            }
        }
        private fun checkNotSigned(func: () -> Unit) { if (alreadySigned) throw IllegalStateException("Cannot write to already signed object"); func()}
        override fun write(bytes: ByteArray, off: Int, len: Int) = checkNotSigned { sig.update(bytes, off, len) }
        override fun write(bytes: ByteArray) = checkNotSigned { sig.update(bytes) }
        override fun write(b: Int) = checkNotSigned { sig.update(b.toByte()) }
    }
}
