package net.corda.core.internal

import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.X509EncodedKeySpec
import kotlin.reflect.full.functions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.isAccessible

/**
 * Wrapper around [EdDSAEngine] which can intelligently rewrite X509Keys to a [EdDSAPublicKey]. This is a temporary
 * solution until this is integrated upstream and/or a custom certificate factory implemented to force the correct
 * key type. Only intercepts public keys passed into [engineInitVerify], as there is no equivalent issue with private
 * keys.
 */
class X509EdDSAEngine : Signature {
    private val engine: EdDSAEngine
    val getParameter = EdDSAEngine::class.functions.single { it.name == "engineGetParameter" }.apply { isAccessible = true }
    val getParameters = EdDSAEngine::class.functions.single { it.name == "engineGetParameters" }.apply { isAccessible = true }
    val setParameter = EdDSAEngine::class.functions.single { it.name == "engineSetParameter" && it.valueParameters.size == 2 }.apply { isAccessible = true }
    val initSign = EdDSAEngine::class.functions.single { it.name == "engineInitSign" && it.valueParameters.size == 1 }.apply { isAccessible = true }
    val initSignRandom = EdDSAEngine::class.functions.single { it.name == "engineInitSign" && it.valueParameters.size == 2 }.apply { isAccessible = true }
    val initVerify = EdDSAEngine::class.functions.single { it.name == "engineInitVerify" }.apply { isAccessible = true }
    val sign = EdDSAEngine::class.functions.single { it.name == "engineSign" && it.valueParameters.size == 0 }.apply { isAccessible = true }
    val updateBuffer = EdDSAEngine::class.functions.single { it.name == "engineUpdate" && it.valueParameters.size == 3 }.apply { isAccessible = true }
    val verify = EdDSAEngine::class.functions.single { it.name == "engineVerify" && it.valueParameters.size == 1 }.apply { isAccessible = true }

    constructor() : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine()
    }

    constructor(digest: MessageDigest) : super(EdDSAEngine.SIGNATURE_ALGORITHM) {
        engine = EdDSAEngine(digest)
    }

    override fun engineInitSign(privateKey: PrivateKey) = initSign.call(engine, privateKey) as Unit
    override fun engineInitSign(privateKey: PrivateKey, random: SecureRandom)  = initSignRandom.call(engine, privateKey, random) as Unit

    override fun engineInitVerify(publicKey: PublicKey) {
        val parsedKey = if (publicKey is sun.security.x509.X509Key) {
            EdDSAPublicKey(X509EncodedKeySpec(publicKey.encoded))
        } else {
            publicKey
        }

        initVerify.call(engine, parsedKey)
    }

    override fun engineVerify(sigBytes: ByteArray): Boolean = verify.call(engine, sigBytes) as Boolean
    override fun engineSign(): ByteArray = sign.call(engine) as ByteArray

    override fun engineUpdate(b: Byte) {
        val updateByte = EdDSAEngine::class.functions.single { it.name == "engineUpdate" && it.valueParameters.size == 1 && it.valueParameters.first().type == Byte::javaClass }.apply { isAccessible = true }

        updateByte.call(engine, b)
    }

    override fun engineUpdate(b: ByteArray, off: Int, len: Int) = updateBuffer.call(engine, b, off, len) as Unit

    override fun engineGetParameters(): AlgorithmParameters = getParameters.call(engine) as AlgorithmParameters
    override fun engineSetParameter(params: AlgorithmParameterSpec) = engine.setParameter(params)
    override fun engineGetParameter(param: String): Any = getParameter.call(engine, param)!!
    override fun engineSetParameter(param: String, value: Any?) = setParameter.call(engine, param, value) as Unit
}
