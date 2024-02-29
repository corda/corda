@file:Suppress("MagicNumber")

package net.corda.core.crypto.internal

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.math.BigInteger
import java.math.BigInteger.ZERO
import java.security.AlgorithmParameters
import java.security.KeyPair
import java.security.KeyPairGeneratorSpi
import java.security.PrivateKey
import java.security.Provider
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.SignatureSpi
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.ECFieldFp
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.EllipticCurve
import java.security.spec.NamedParameterSpec

/**
 * Augment the SunEC provider with secp256k1 curve support by delegating to [BouncyCastleProvider] when secp256k1 keys or params are
 * requested. Otherwise delegates to SunEC.
 */
class Secp256k1SupportProvider : Provider("Secp256k1Support", "1.0", "Augmenting SunEC with support for the secp256k1 curve via BC") {
    init {
        put("Signature.SHA256withECDSA", Secp256k1SupportSignatureSpi::class.java.name)
        put("KeyPairGenerator.EC", Secp256k1SupportKeyPairGeneratorSpi::class.java.name)
        put("AlgorithmParameters.EC", "sun.security.util.ECParameters")
        put("KeyFactory.EC", "sun.security.ec.ECKeyFactory")
    }

    class Secp256k1SupportSignatureSpi : SignatureSpi() {
        private lateinit var sunEc: Signature
        private lateinit var bc: Signature
        private lateinit var selected: Signature

        override fun engineInitVerify(publicKey: PublicKey?) {
            selectProvider((publicKey as? ECPublicKey)?.params)
            selected.initVerify(publicKey)
        }

        override fun engineInitSign(privateKey: PrivateKey?) {
            selectProvider((privateKey as? ECPrivateKey)?.params)
            selected.initSign(privateKey)
        }

        override fun engineSetParameter(params: AlgorithmParameterSpec?) {
            selectProvider(params)
            // The BC implementation throws UnsupportedOperationException, so we just avoid calling it.
            if (selected !== bc) {
                selected.setParameter(params)
            }
        }

        private fun selectProvider(params: AlgorithmParameterSpec?) {
            if (params.isSecp256k1) {
                if (!::bc.isInitialized) {
                    bc = Signature.getInstance("SHA256withECDSA", cordaBouncyCastleProvider)
                }
                selected = bc
            } else {
                selectSunEc()
            }
        }

        private fun selectSunEc() {
            if (!::sunEc.isInitialized) {
                sunEc = Signature.getInstance("SHA256withECDSA", sunEcProvider)
            }
            selected = sunEc
        }

        override fun engineUpdate(b: Byte) {
            defaultToSunEc()
            selected.update(b)
        }

        override fun engineUpdate(b: ByteArray?, off: Int, len: Int) {
            defaultToSunEc()
            selected.update(b, off, len)
        }

        override fun engineSign(): ByteArray {
            defaultToSunEc()
            return selected.sign()
        }

        override fun engineVerify(sigBytes: ByteArray?): Boolean {
            defaultToSunEc()
            return selected.verify(sigBytes)
        }

        override fun engineGetParameters(): AlgorithmParameters {
            defaultToSunEc()
            return selected.parameters
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun engineSetParameter(param: String?, value: Any?) {
            defaultToSunEc()
            selected.setParameter(param, value)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun engineGetParameter(param: String?): Any {
            defaultToSunEc()
            return selected.getParameter(param)
        }

        private fun defaultToSunEc() {
            // Even though it's probably a bug to start using the Signature object without first calling one of the intialize methods,
            // default it to SunEC provider anyway and let it deal with the issue.
            if (!::selected.isInitialized) {
                selectSunEc()
            }
        }
    }

    class Secp256k1SupportKeyPairGeneratorSpi : KeyPairGeneratorSpi() {
        // The methods in KeyPairGeneratorSpi are public, which allows us to directly call them. This is not the case with SignatureSpi (above).
        private lateinit var sunEc: KeyPairGeneratorSpi
        private lateinit var bc: KeyPairGeneratorSpi
        private lateinit var selected: KeyPairGeneratorSpi

        override fun initialize(keysize: Int, random: SecureRandom?) {
            selectSunEc()
            selected.initialize(keysize, random)
        }

        override fun initialize(params: AlgorithmParameterSpec?, random: SecureRandom?) {
            if (params.isSecp256k1) {
                if (!::bc.isInitialized) {
                    bc = org.bouncycastle.jcajce.provider.asymmetric.ec.KeyPairGeneratorSpi.EC()
                }
                selected = bc
            } else {
                selectSunEc()
            }
            selected.initialize(params, random)
        }

        private fun selectSunEc() {
            if (!::sunEc.isInitialized) {
                sunEc = sunEcProvider.getService("KeyPairGenerator", "EC").newInstance(null) as KeyPairGeneratorSpi
            }
            selected = sunEc
        }

        override fun generateKeyPair(): KeyPair {
            if (!::selected.isInitialized) {
                // In-case initialize wasn't first called, default to SunEC
                selectSunEc()
            }
            return selected.generateKeyPair()
        }
    }
}

/**
 * Parameters for the secp256k1 curve
 */
private object Secp256k1 {
    val n = BigInteger("fffffffffffffffffffffffffffffffebaaedce6af48a03bbfd25e8cd0364141", 16)
    val g = ECPoint(
            BigInteger("79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798", 16),
            BigInteger("483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8", 16)
    )
    val curve = EllipticCurve(
            ECFieldFp(BigInteger("fffffffffffffffffffffffffffffffffffffffffffffffffffffffefffffc2f", 16)),
            ZERO,
            7.toBigInteger()
    )
}

val AlgorithmParameterSpec?.isSecp256k1: Boolean
    get() = when (this) {
        is ECParameterSpec -> cofactor == 1 && order == Secp256k1.n && curve == Secp256k1.curve && generator == Secp256k1.g
        is NamedParameterSpec -> name.equals("secp256k1", ignoreCase = true)
        else -> false
    }
