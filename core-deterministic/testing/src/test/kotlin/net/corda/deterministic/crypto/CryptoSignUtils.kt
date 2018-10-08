@file:JvmName("CryptoSignUtils")

package net.corda.deterministic.crypto

import net.corda.core.crypto.*
import net.corda.core.crypto.Crypto.findSignatureScheme
import net.corda.core.crypto.Crypto.isSupportedSignatureScheme
import net.corda.core.serialization.serialize
import java.security.*

/**
 * This is a slightly modified copy of signing utils from net.corda.core.crypto.Crypto, which are normally removed from DJVM.
 * However, we need those for TransactionSignatureTest.
 */
object CryptoSignUtils {
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(schemeCodeName: String, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        return doSign(findSignatureScheme(schemeCodeName), privateKey, clearData)
    }

    /**
     * Generic way to sign [ByteArray] data with a [PrivateKey] and a known [Signature].
     * @param signatureScheme a [SignatureScheme] object, retrieved from supported signature schemes, see [Crypto].
     * @param privateKey the signer's [PrivateKey].
     * @param clearData the data/message to be signed in [ByteArray] form (usually the Merkle root).
     * @return the digital signature (in [ByteArray]) on the input message.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(signatureScheme: SignatureScheme, privateKey: PrivateKey, clearData: ByteArray): ByteArray {
        require(isSupportedSignatureScheme(signatureScheme)) {
            "Unsupported key/algorithm for schemeCodeName: ${signatureScheme.schemeCodeName}"
        }
        require(clearData.isNotEmpty()) { "Signing of an empty array is not permitted!" }
        val signature = Signature.getInstance(signatureScheme.signatureName, signatureScheme.providerName)
        signature.initSign(privateKey)
        signature.update(clearData)
        return signature.sign()
    }

    /**
     * Generic way to sign [SignableData] objects with a [PrivateKey].
     * [SignableData] is a wrapper over the transaction's id (Merkle root) in order to attach extra information, such as
     * a timestamp or partial and blind signature indicators.
     * @param keyPair the signer's [KeyPair].
     * @param signableData a [SignableData] object that adds extra information to a transaction.
     * @return a [TransactionSignature] object than contains the output of a successful signing, signer's public key and
     * the signature metadata.
     * @throws IllegalArgumentException if the signature scheme is not supported for this private key.
     * @throws InvalidKeyException if the private key is invalid.
     * @throws SignatureException if signing is not possible due to malformed data or private key.
     */
    @JvmStatic
    @Throws(InvalidKeyException::class, SignatureException::class)
    fun doSign(keyPair: KeyPair, signableData: SignableData): TransactionSignature {
        val sigKey: SignatureScheme = findSignatureScheme(keyPair.private)
        val sigMetaData: SignatureScheme = findSignatureScheme(keyPair.public)
        require(sigKey == sigMetaData) {
            "Metadata schemeCodeName: ${sigMetaData.schemeCodeName} is not aligned with the key type: ${sigKey.schemeCodeName}."
        }
        val signatureBytes = doSign(sigKey.schemeCodeName, keyPair.private, signableData.serialize().bytes)
        return TransactionSignature(signatureBytes, keyPair.public, signableData.signatureMetadata)
    }
}
