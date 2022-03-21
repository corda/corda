package net.corda.core.conclave.common

import net.corda.core.serialization.CordaSerializable
import java.util.*

@CordaSerializable
data class EnclaveMessage(val invocationId: UUID, val command: EnclaveCommand, val message: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EnclaveMessage

        if (invocationId != other.invocationId) return false
        if (command != other.command) return false
        if (!message.contentEquals(other.message)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = command.hashCode()
        result = 31 * result + invocationId.hashCode()
        result = 31 * result + message.contentHashCode()
        return result
    }
}