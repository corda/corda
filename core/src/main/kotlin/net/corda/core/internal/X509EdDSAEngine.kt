package net.corda.core.internal

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Wrapper around [EdDSAEngine] which can intelligently rewrite X509Keys to a [EdDSAPublicKey]. This is a temporary
 * solution until this is integrated upstream and/or a custom certificate factory implemented to force the correct
 * key type. Only intercepts public keys passed into [engineInitVerify], as there is no equivalent issue with private
 * keys.
 */
class X509EdDSAEngine : Signature {
    private val engine: EdDSAEngine

    constructor() : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine()
    }

    constructor(digest: MessageDigest) : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine(digest)
    }

    override fun engineInitSign(privateKey: PrivateKey) = engine.initSign(privateKey)
    override fun engineInitSign(privateKey: PrivateKey, random: SecureRandom) = engine.initSign(privateKey, random)

    override fun engineInitVerify(publicKey: PublicKey) {
        val parsedKey = try {
            publicKey as? EdDSAPublicKey ?: EdDSAPublicKey(X509EncodedKeySpec(publicKey.encoded))
        } catch (e: Exception) {
            throw (InvalidKeyException(e.message))
        }
        engine.initVerify(parsedKey)
    }

    override fun engineSign(): ByteArray = engine.sign()
    override fun engineVerify(sigBytes: ByteArray): Boolean = engine.verify(sigBytes)

    override fun engineUpdate(b: Byte) = engine.update(b)
    override fun engineUpdate(b: ByteArray, off: Int, len: Int) = engine.update(b, off, len)

    override fun engineGetParameters(): AlgorithmParameters = engine.parameters
    override fun engineSetParameter(params: AlgorithmParameterSpec) = engine.setParameter(params)
    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun engineGetParameter(param: String): Any = engine.getParameter(param)

    @Suppress("DEPRECATION", "OverridingDeprecatedMember")
    override fun engineSetParameter(param: String, value: Any?) = engine.setParameter(param, value)
}
