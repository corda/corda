package net.corda.nodeapi.internal.crypto

import net.corda.core.crypto.SignatureScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.operator.ContentSigner
import java.io.OutputStream
import java.security.PrivateKey
import java.security.Provider
import java.security.SecureRandom
import java.security.Signature

/**
 *  Provide extra OID look up for signature algorithm not supported by bouncy castle.
 *  This builder will use bouncy castle's JcaContentSignerBuilder as fallback for unknown algorithm.
 */
object ContentSignerBuilder {
    fun build(signatureScheme: SignatureScheme, privateKey: PrivateKey, provider: Provider, random: SecureRandom? = null): ContentSigner {
        val sigAlgId = signatureScheme.signatureOID
        val sig = Signature.getInstance(signatureScheme.signatureName, provider).apply {
            if (random != null) {
                initSign(privateKey, random)
            } else {
                initSign(privateKey)
            }
        }
        return object : ContentSigner {
            private val stream = SignatureOutputStream(sig)
            override fun getAlgorithmIdentifier(): AlgorithmIdentifier = sigAlgId
            override fun getOutputStream(): OutputStream = stream
            override fun getSignature(): ByteArray = stream.signature
        }
    }

    private class SignatureOutputStream(private val sig: Signature) : OutputStream() {
        internal val signature: ByteArray get() = sig.sign()
        override fun write(bytes: ByteArray, off: Int, len: Int) = sig.update(bytes, off, len)
        override fun write(bytes: ByteArray) = sig.update(bytes)
        override fun write(b: Int) = sig.update(b.toByte())
    }
}
