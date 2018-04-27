package net.corda.core.crypto.internal

import net.corda.core.crypto.Crypto
import net.corda.core.crypto.SignatureScheme
import net.corda.core.crypto.sha256
import net.i2p.crypto.eddsa.EdDSAPrivateKey
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveSpec
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec
import org.bouncycastle.jce.spec.ECParameterSpec
import org.bouncycastle.jce.spec.ECPrivateKeySpec
import org.bouncycastle.jce.spec.ECPublicKeySpec
import org.bouncycastle.math.ec.ECConstants
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import org.bouncycastle.math.ec.WNafUtil
import java.math.BigInteger
import java.security.InvalidKeyException
import java.security.KeyPair
import java.security.PrivateKey
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Given the domain parameters, this routine deterministically generates an ECDSA key pair,
 * in accordance with X9.62 section 5.2.1 pages 26, 27.
 */
internal fun deriveKeyPairECDSA(parameterSpec: ECParameterSpec, privateKey: PrivateKey, seed: ByteArray): KeyPair {
    // Compute HMAC(privateKey, seed).
    val macBytes = deriveHMAC(privateKey, seed)
    // Get the first EC curve fieldSized-bytes from macBytes.
    // According to recommendations from the deterministic ECDSA rfc, see https://tools.ietf.org/html/rfc6979
    // performing a simple modular reduction would induce biases that would be detrimental to security.
    // Thus, the result is not reduced modulo q and similarly to BIP32, EC curve fieldSized-bytes are utilised.
    val fieldSizeMacBytes = macBytes.copyOf(parameterSpec.curve.fieldSize / 8)

    // Calculate value d for private key.
    val deterministicD = BigInteger(1, fieldSizeMacBytes)

    // Key generation checks follow the BC logic found in
    // https://github.com/bcgit/bc-java/blob/master/core/src/main/java/org/bouncycastle/crypto/generators/ECKeyPairGenerator.java
    // There is also an extra check to align with the BIP32 protocol, according to which
    // if deterministicD >= order_of_the_curve the resulted key is invalid and we should proceed with another seed.
    // TODO: We currently use SHA256(seed) when retrying, but BIP32 just skips a counter (i) that results to an invalid key.
    //       Although our hashing approach seems reasonable, we should check if there are alternatives,
    //       especially if we use counters as well.
    if (deterministicD < ECConstants.TWO
            || WNafUtil.getNafWeight(deterministicD) < parameterSpec.n.bitLength().ushr(2)
            || deterministicD >= parameterSpec.n) {
        // Instead of throwing an exception, we retry with SHA256(seed).
        return deriveKeyPairECDSA(parameterSpec, privateKey, seed.sha256().bytes)
    }
    val privateKeySpec = ECPrivateKeySpec(deterministicD, parameterSpec)
    val privateKeyD = BCECPrivateKey(privateKey.algorithm, privateKeySpec, BouncyCastleProvider.CONFIGURATION)

    // Compute the public key by scalar multiplication.
    // Note that BIP32 uses masterKey + mac_derived_key as the final private key and it consequently
    // requires an extra point addition: master_public + mac_derived_public for the public part.
    // In our model, the mac_derived_output, deterministicD, is not currently added to the masterKey and it
    // it forms, by itself, the new private key, which in turn is used to compute the new public key.
    val pointQ = FixedPointCombMultiplier().multiply(parameterSpec.g, deterministicD)
    // This is unlikely to happen, but we should check for point at infinity.
    if (pointQ.isInfinity) {
        // Instead of throwing an exception, we retry with SHA256(seed).
        return deriveKeyPairECDSA(parameterSpec, privateKey, seed.sha256().bytes)
    }
    val publicKeySpec = ECPublicKeySpec(pointQ, parameterSpec)
    val publicKeyD = BCECPublicKey(privateKey.algorithm, publicKeySpec, BouncyCastleProvider.CONFIGURATION)

    return KeyPair(publicKeyD, privateKeyD)
}

/** Deterministically generate an EdDSA key. */
internal fun deriveKeyPairEdDSA(privateKey: PrivateKey, seed: ByteArray): KeyPair {
    // Compute HMAC(privateKey, seed).
    val macBytes = deriveHMAC(privateKey, seed)

    // Calculate key pair.
    val params = Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec
    val bytes = macBytes.copyOf(params.curve.field.getb() / 8) // Need to pad the entropy to the valid seed length.
    val privateKeyD = EdDSAPrivateKeySpec(bytes, params)
    val publicKeyD = EdDSAPublicKeySpec(privateKeyD.a, params)
    return KeyPair(EdDSAPublicKey(publicKeyD), EdDSAPrivateKey(privateKeyD))
}

/**
 * Custom key pair generator from an entropy required for various tests. It is similar to deriveKeyPairECDSA,
 * but the accepted range of the input entropy is more relaxed:
 * 2 <= entropy < N, where N is the order of base-point G.
 */
internal fun deriveECDSAKeyPairFromEntropy(signatureScheme: SignatureScheme, entropy: BigInteger): KeyPair {
    val parameterSpec = signatureScheme.algSpec as ECNamedCurveParameterSpec

    // The entropy might be a negative number and/or out of range (e.g. PRNG output).
    // In such cases we retry with hash(currentEntropy).
    while (entropy < ECConstants.TWO || entropy >= parameterSpec.n) {
        return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256().bytes))
    }

    val privateKeySpec = ECPrivateKeySpec(entropy, parameterSpec)
    val priv = BCECPrivateKey("EC", privateKeySpec, BouncyCastleProvider.CONFIGURATION)

    val pointQ = FixedPointCombMultiplier().multiply(parameterSpec.g, entropy)
    while (pointQ.isInfinity) {
        // Instead of throwing an exception, we retry with hash(entropy).
        return deriveECDSAKeyPairFromEntropy(signatureScheme, BigInteger(1, entropy.toByteArray().sha256().bytes))
    }
    val publicKeySpec = ECPublicKeySpec(pointQ, parameterSpec)
    val pub = BCECPublicKey("EC", publicKeySpec, BouncyCastleProvider.CONFIGURATION)

    return KeyPair(pub, priv)
}

/**
 * Custom key pair generator from entropy.
 * BigIntenger.toByteArray() uses the two's-complement representation.
 * The entropy is transformed to a byte array in big-endian byte-order and
 * only the first ed25519.field.getb() / 8 bytes are used.
 */
internal fun deriveEdDSAKeyPairFromEntropy(entropy: BigInteger): KeyPair {
    val params = Crypto.EDDSA_ED25519_SHA512.algSpec as EdDSANamedCurveSpec
    val bytes = entropy.toByteArray().copyOf(params.curve.field.getb() / 8) // Need to pad the entropy to the valid seed length.
    val priv = EdDSAPrivateKeySpec(bytes, params)
    val pub = EdDSAPublicKeySpec(priv.a, params)
    return KeyPair(EdDSAPublicKey(pub), EdDSAPrivateKey(priv))
}

/** Compute the HMAC-SHA512 using a privateKey as the MAC_key and a seed ByteArray. */
internal fun deriveHMAC(privateKey: PrivateKey, seed: ByteArray): ByteArray {
    // Compute hmac(privateKey, seed).
    val mac = Mac.getInstance("HmacSHA512", cordaBouncyCastleProvider)
    val keyData = when (privateKey) {
        is BCECPrivateKey -> privateKey.d.toByteArray()
        is EdDSAPrivateKey -> privateKey.geta()
        else -> throw InvalidKeyException("Key type ${privateKey.algorithm} is not supported for deterministic key derivation")
    }
    val key = SecretKeySpec(keyData, "HmacSHA512")
    mac.init(key)
    return mac.doFinal(seed)
}