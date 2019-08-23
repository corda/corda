package net.corda.core.identity

import net.corda.core.serialization.CordaSerializable
import java.nio.ByteBuffer
import java.util.*
import javax.xml.bind.DatatypeConverter

/**
 * SGX: represents an SGX enclave measurement (TODO: duplicated from SGXJVM)
 */
@CordaSerializable
class EnclaveIdentity(private val data: ByteArray) {

    init {
        require(data.size == SGX_MEASUREMENT_SIZE)
    }

    constructor(hex: String): this(DatatypeConverter.parseHexBinary(hex))

    override fun equals(other: Any?): Boolean {
        return if (other is EnclaveIdentity) {
            Arrays.equals(data, other.data)
        } else false
    }

    override fun hashCode(): Int {
        return Arrays.hashCode(data)
    }

    override fun toString(): String = DatatypeConverter.printHexBinary(data)

    val encoded get() = data.copyOf()

    companion object {
        /**
         * Parse measurement from string representation in hex format
         */
        @JvmStatic
        fun of(hex: String) = EnclaveIdentity(hex)

        /**
         * Read measurement from buffer
         */
        @JvmStatic
        fun read(bytes: ByteBuffer): EnclaveIdentity {
            val data = ByteArray(SGX_MEASUREMENT_SIZE)
            bytes.get(data)
            return EnclaveIdentity(data)
        }

        private val SGX_MEASUREMENT_SIZE = 32
    }
}
