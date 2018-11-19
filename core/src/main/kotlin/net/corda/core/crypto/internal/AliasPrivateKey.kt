package net.corda.core.crypto.internal

import net.corda.core.CordaOID
import org.bouncycastle.asn1.*
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyPair
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec

/**
 * [PrivateKey] wrapper to just store the alias of a private key.
 * Usually, HSM (hardware secure module) key entries are accessed via unique aliases and the private key material never
 * leaves the box. This class wraps a [String] key alias into a [PrivateKey] object, which helps on transferring
 * [KeyPair] objects without exposing the private key material. Then, whenever we need to sign with the actual private
 * key, we provide the [alias] from this [AliasPrivateKey] to the underlying HSM implementation.
 */
data class AliasPrivateKey(val alias: String): PrivateKey {

    companion object {
        const val ALIAS_KEY_ALGORITHM = "AliasPrivateKey"
    }

    override fun getAlgorithm() = ALIAS_KEY_ALGORITHM

    override fun getEncoded(): ByteArray {
        val keyVector = ASN1EncodableVector()
        keyVector.add(DERUTF8String(alias))
        val privateKeyInfoBytes = PrivateKeyInfo(
                AlgorithmIdentifier(ASN1ObjectIdentifier(CordaOID.ALIAS_PRIVATE_KEY)),
                DERSequence(keyVector)
        ).getEncoded(ASN1Encoding.DER)
        val keySpec = PKCS8EncodedKeySpec(privateKeyInfoBytes)
        return keySpec.encoded
    }

    override fun getFormat() = "PKCS#8"
}
