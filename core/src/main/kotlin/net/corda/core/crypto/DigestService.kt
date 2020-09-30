package net.corda.core.crypto

import co.paralleluniverse.strands.Strand
import net.corda.core.KeepForDJVM
import net.corda.core.crypto.SecureHash.Companion.SHA2_256
import net.corda.core.crypto.SecureHash.Companion.SHA3_256
import net.corda.core.internal.FlowStateMachine
import net.corda.core.internal.getDefaultHashAlgorithm
import net.corda.core.serialization.CordaSerializable

@CordaSerializable
@KeepForDJVM
 open class DigestService(val digestLength: Int, val hashAlgorithm: String) {

    // TODO: implement private constructors and factory method to reduce allocations

    constructor(hashAlgorithm: String) : this(SecureHash.digestLengthFor(hashAlgorithm), hashAlgorithm)

    constructor() : this((Strand.currentStrand() as? FlowStateMachine<*>)?.serviceHub?.networkParameters?.getDefaultHashAlgorithm()
            ?: SHA3_256)

    init {
        require(hashAlgorithm.isNotEmpty()) { "Hash algorithm name unavailable or not specified" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is DigestService &&
                other.digestLength == this.digestLength &&
                other.hashAlgorithm == this.hashAlgorithm
    }

    override fun hashCode(): Int = digestLength * 31 + hashAlgorithm.hashCode() * 31

    /**
     * Computes the digest of the [ByteArray].
     *
     * @param bytes The [ByteArray] to hash.
     * @param salt The [ByteArray] to use as salt.
     */
    fun hash(bytes: ByteArray): SecureHash = SecureHash.hashAs(hashAlgorithm, bytes)

    /**
     * Computes the digest of the [String]'s UTF-8 byte contents.
     *
     * @param str [String] whose UTF-8 contents will be hashed.
     * @param salt The [String] to use as salt.
     */
    fun hash(str: String): SecureHash = hash(str.toByteArray())

    /**
     * A digest value consisting of [digestLength] 0xFF bytes.
     */
    val allOnesHash: SecureHash
        get() = SecureHash.allOnesHashFor(hashAlgorithm)

    /**
     * A hash value consisting of [digestLength] 0x00 bytes.
     */
    val zeroHash: SecureHash
        get() = SecureHash.zeroHashFor(hashAlgorithm)

    fun salted(msg: ByteArray, salt: ByteArray?): ByteArray {
        return salt?.plus(msg) ?: msg
    }

     //fun validateSalt(privacySalt: PrivacySalt) = privacySalt.validateFor(hashAlgorithm)
}

@CordaSerializable
class SHA2256DigestService : DigestService(32, SecureHash.SHA2_256)

@CordaSerializable
class SHA3256DigestService : DigestService(32, SecureHash.SHA3_256)
