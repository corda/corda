package net.corda.core.crypto.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureMetadata
import net.corda.core.crypto.SignatureScheme
import org.bouncycastle.asn1.x509.AlgorithmIdentifier

/**
 * Supported digital signature schemes.
 * Note: Only the schemes added in this map will be supported (see [Crypto]).
 */
internal val signatureSchemeMap: Map<String, SignatureScheme> = listOf(
        Crypto.RSA_SHA256,
        Crypto.ECDSA_SECP256K1_SHA256,
        Crypto.ECDSA_SECP256R1_SHA256,
        Crypto.EDDSA_ED25519_SHA512,
        Crypto.SPHINCS256_SHA256,
        Crypto.COMPOSITE_KEY
).associateBy { it.schemeCodeName }

/**
 * Map of supported digital signature schemes associated by [SignatureScheme.schemeNumberID].
 * SchemeNumberID is the scheme identifier attached to [SignatureMetadata].
 */
internal val signatureSchemeNumberIDMap: Map<Int, SignatureScheme> = Crypto.supportedSignatureSchemes().associateBy { it.schemeNumberID }

/**
 * Map of X.509 algorithm identifiers to signature schemes Corda recognises. See RFC 2459 for the format of
 * algorithm identifiers.
 */
internal val algorithmMap: Map<AlgorithmIdentifier, SignatureScheme>
        = (signatureSchemeMap.values.flatMap { scheme -> scheme.alternativeOIDs.map { Pair(it, scheme) } }
        + signatureSchemeMap.values.map { Pair(it.signatureOID, it) })
        .toMap()
