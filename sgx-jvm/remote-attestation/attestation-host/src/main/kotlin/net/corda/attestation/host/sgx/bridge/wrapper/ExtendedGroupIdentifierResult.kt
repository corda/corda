package net.corda.attestation.host.sgx.bridge.wrapper

/**
 * The result of a call to [NativeWrapper.getExtendedGroupIdentifier].
 */
data class ExtendedGroupIdentifierResult(
    /**
     * The extended Intel EPID group identifier (of type
     * [ExtendedGroupIdentifier] downstream).
     */
    val extendedGroupIdentifier: Int,

    /**
     * The result of the operation (of type [SgxStatus] downstream).
     */
    val result: Long
)
