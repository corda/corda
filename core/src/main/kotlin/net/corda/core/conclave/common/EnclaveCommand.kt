package net.corda.core.conclave.common

/**
 * Generic type for an enclave command.
 * This type contains no properties or methods and simply instructs the enclave to perform a specific action.
 */
interface EnclaveCommand {

    /**
     * Convert this type to a [String].
     * @return serialized [String] of this type.
     */
    fun serialize(): String {
        return this.javaClass.name
    }
}

/**
 * Deserialize an [EnclaveCommand].
 * Convert a serialized string into an [EnclaveCommand] instance.
 */
fun String.toEnclaveCommand(): EnclaveCommand {
    return Class.forName(this).newInstance() as EnclaveCommand
}

/**
 * An [EnclaveCommand] that instructs the enclave to register a host identity.
 */
class RegisterHostIdentity : EnclaveCommand