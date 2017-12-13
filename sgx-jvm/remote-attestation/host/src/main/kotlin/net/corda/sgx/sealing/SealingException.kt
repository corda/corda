package net.corda.sgx.sealing

/**
 * Exception raised whenever there is a problem with a sealing operation.
 *
 * @property status The status or outcome of the operation.
 */
class SealingException(
        val status: SealingResult
) : Exception(status.message)