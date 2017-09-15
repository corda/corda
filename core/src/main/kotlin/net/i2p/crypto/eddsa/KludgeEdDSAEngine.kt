package net.i2p.crypto.eddsa

import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Temporary solution to EdDSA engine not being able to handle X.509 keys.
 */
class KludgeEdDSAEngine : Signature {
    private val engine: EdDSAEngine

    constructor() : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine()
    }
    constructor(digest: MessageDigest) : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine(digest)
    }

    override fun engineInitSign(privateKey: PrivateKey) = engine.engineInitSign(privateKey)
    override fun engineInitVerify(publicKey: PublicKey) {
        val parsedKey = if (publicKey is sun.security.x509.X509Key) {
            EdDSAPublicKey(X509EncodedKeySpec(publicKey.encoded))
        } else {
            publicKey
        }
        engine.engineInitVerify(parsedKey)
    }

    override fun engineVerify(sigBytes: ByteArray): Boolean = engine.engineVerify(sigBytes)
    override fun engineSign(): ByteArray = engine.engineSign()
    override fun engineUpdate(b: Byte) = engine.engineUpdate(b)
    override fun engineUpdate(b: ByteArray, off: Int, len: Int) = engine.engineUpdate(b, off, len)
    override fun engineGetParameters(): AlgorithmParameters {
        val method = engine.javaClass.getMethod("engineGetParameters")
        return method.invoke(engine) as AlgorithmParameters
    }
    override fun engineSetParameter(params: AlgorithmParameterSpec) = engine.setParameter(params)
    override fun engineGetParameter(param: String): Any = engine.engineGetParameter(param)
    override fun engineSetParameter(param: String, value: Any?) = engine.engineSetParameter(param, value)
    override fun engineInitSign(privateKey: PrivateKey, random: SecureRandom) {
        val method = engine.javaClass.getMethod("engineInitSign", PrivateKey::class.java, SecureRandom::class.java)
        method.invoke(engine, privateKey, random)
    }
}
