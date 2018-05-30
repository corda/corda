package net.corda.core.crypto

import net.corda.core.Deterministic
import net.corda.core.serialization.deserialize
import java.io.ByteArrayOutputStream
import java.security.*
import java.security.spec.AlgorithmParameterSpec

/**
 * Dedicated class for storing a set of signatures that comprise [CompositeKey].
 */
@Deterministic
class CompositeSignature : Signature(SIGNATURE_ALGORITHM) {
    companion object {
        const val SIGNATURE_ALGORITHM = "COMPOSITESIG"
        @JvmStatic
        fun getService(provider: Provider) = Provider.Service(provider, "Signature", SIGNATURE_ALGORITHM, CompositeSignature::class.java.name, emptyList(), emptyMap())
    }

    private var signatureState: State? = null

    /**
     * Check that the signature state has been initialised, then return it.
     */
    @Throws(SignatureException::class)
    private fun assertInitialised(): State {
        if (signatureState == null)
            throw SignatureException("Engine has not been initialised")
        return signatureState!!
    }

    @Deprecated("Deprecated in inherited API")
    @Throws(InvalidAlgorithmParameterException::class)
    override fun engineGetParameter(param: String?): Any {
        throw InvalidAlgorithmParameterException("Composite signatures do not support any parameters")
    }

    @Throws(InvalidKeyException::class)
    override fun engineInitSign(privateKey: PrivateKey?) {
        throw InvalidKeyException("Composite signatures must be assembled independently from signatures provided by the component private keys")
    }

    @Throws(InvalidKeyException::class)
    override fun engineInitVerify(publicKey: PublicKey?) {
        if (publicKey is CompositeKey) {
            signatureState = State(ByteArrayOutputStream(1024), publicKey)
        } else {
            throw InvalidKeyException("Key to verify must be a composite key")
        }
    }

    @Deprecated("Deprecated in inherited API")
    @Throws(InvalidAlgorithmParameterException::class)
    override fun engineSetParameter(param: String?, value: Any?) {
        throw InvalidAlgorithmParameterException("Composite signatures do not support any parameters")
    }

    @Throws(InvalidAlgorithmParameterException::class)
    override fun engineSetParameter(params: AlgorithmParameterSpec) {
        throw InvalidAlgorithmParameterException("Composite signatures do not support any parameters")
    }

    @Throws(SignatureException::class)
    override fun engineSign(): ByteArray {
        throw SignatureException("Composite signatures must be assembled independently from signatures provided by the component private keys")
    }

    override fun engineUpdate(b: Byte) {
        assertInitialised().buffer.write(b.toInt())
    }

    override fun engineUpdate(b: ByteArray, off: Int, len: Int) {
        assertInitialised().buffer.write(b, off, len)
    }

    @Throws(SignatureException::class)
    override fun engineVerify(sigBytes: ByteArray): Boolean = assertInitialised().engineVerify(sigBytes)

    data class State(val buffer: ByteArrayOutputStream, val verifyKey: CompositeKey) {
        fun engineVerify(sigBytes: ByteArray): Boolean {
            val sig = sigBytes.deserialize<CompositeSignaturesWithKeys>()
            return if (verifyKey.isFulfilledBy(sig.sigs.map { it.by })) {
                val clearData = SecureHash.SHA256(buffer.toByteArray())
                sig.sigs.all { it.isValid(clearData) }
            } else {
                false
            }
        }
    }
}