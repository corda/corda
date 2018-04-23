package net.corda.sgx.system

/**
 * Exception raised if SGX for some reason is unavailable on the system.
 *
 * @property status The status of the SGX device.
 */
class SgxUnavailableException(
        val status: SgxDeviceStatus
) : Exception(status.message)