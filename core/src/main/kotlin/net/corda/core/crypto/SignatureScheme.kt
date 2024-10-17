package net.corda.core.crypto

import net.corda.core.crypto.internal.providerMap
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.AlgorithmParameterSpec

/**
 * This class is used to define a digital signature scheme.
 * @param schemeNumberID unique number ID for better efficiency on-wire serialisation.
 * @param schemeCodeName unique code name for this signature scheme (e.g. RSA_SHA256, ECDSA_SECP256K1_SHA256, ECDSA_SECP256R1_SHA256,
 * EDDSA_ED25519_SHA512).
 * @param signatureOID ASN.1 algorithm identifier of the signature algorithm (e.g 1.3.101.112 for EdDSA)
 * @param alternativeOIDs ASN.1 algorithm identifiers for keys of the signature, where we want to map multiple keys to
 * the same signature scheme.
 * @param providerName the provider's name (e.g. "BC").
 * @param algorithmName which signature algorithm is used (e.g. RSA, ECDSA. EdDSA).
 * @param signatureName a signature-scheme name as required to create [Signature] objects (e.g. "SHA256withECDSA")
 * @param algSpec parameter specs for the underlying algorithm. Note that RSA is defined by the key size rather than algSpec.
 * eg. ECGenParameterSpec("secp256k1").
 * @param keySize the private key size (currently used for RSA only).
 * @param desc a human-readable description for this scheme.
 */
data class SignatureScheme(
        val schemeNumberID: Int,
        val schemeCodeName: String,
        val signatureOID: AlgorithmIdentifier,
        val alternativeOIDs: List<AlgorithmIdentifier>,
        val providerName: String,
        val algorithmName: String,
        val signatureName: String,
        val algSpec: AlgorithmParameterSpec?,
        val keySize: Int?,
        val desc: String
) {
    @Volatile
    private var memoizedKeyFactory: KeyFactory? = null

    internal val keyFactory: KeyFactory
        get() = memoizedKeyFactory ?: KeyFactory.getInstance(algorithmName, providerMap[providerName]).also { memoizedKeyFactory = it }
}
