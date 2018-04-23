package net.corda.sgx.enclave

/**
 * The status of an SGX operation
 *
 * @property code The native status code returned from the SGX API.
 * @property message A human readable representation of the state.
 */
enum class SgxStatus(val code: Long, val message: String) {

    /**
     * Success.
     */
    SUCCESS(0x0000, "Success"),

    /**
     * Unexpected error.
     */
    ERROR_UNEXPECTED(0x0001, "Unexpected error"),

    /**
     * The parameter is incorrect.
     */
    ERROR_INVALID_PARAMETER(0x0002, "The parameter is incorrect"),

    /**
     * Not enough memory is available to complete this operation.
     */
    ERROR_OUT_OF_MEMORY(0x0003, "Not enough memory is available to complete this operation"),

    /**
     * Enclave lost after power transition or used in child process created by linux:fork().
     */
    ERROR_ENCLAVE_LOST(0x0004, "Enclave lost after power transition or used in child process created by linux:fork()"),

    /**
     * SGX API is invoked in incorrect order or state.
     */
    ERROR_INVALID_STATE(0x0005, "SGX API is invoked in incorrect order or state"),

    /**
     * The ECALL/OCALL index is invalid.
     */
    ERROR_INVALID_FUNCTION(0x1001, "The ecall/ocall index is invalid"),

    /**
     * The enclave is out of TCS.
     */
    ERROR_OUT_OF_TCS(0x1003, "The enclave is out of TCS"),

    /**
     * The enclave is crashed.
     */
    ERROR_ENCLAVE_CRASHED(0x1006, "The enclave is crashed"),

    /**
     * The ECALL is not allowed at this time, e.g. ECALL is blocked by the dynamic entry table, or nested ECALL is not allowed during initialization.
     */
    ERROR_ECALL_NOT_ALLOWED(0x1007, "The ECALL is not allowed at this time, e.g. ECALL is blocked by the dynamic entry table, or nested ECALL is not allowed during initialization"),

    /**
     * The OCALL is not allowed at this time, e.g. OCALL is not allowed during exception handling.
     */
    ERROR_OCALL_NOT_ALLOWED(0x1008, "The OCALL is not allowed at this time, e.g. OCALL is not allowed during exception handling"),

    /**
     * The enclave is running out of stack.
     */
    ERROR_STACK_OVERRUN(0x1009, "The enclave is running out of stack"),

    /**
     * The enclave image has one or more undefined symbols.
     */
    ERROR_UNDEFINED_SYMBOL(0x2000, "The enclave image has one or more undefined symbols"),

    /**
     * The enclave image is not correct.
     */
    ERROR_INVALID_ENCLAVE(0x2001, "The enclave image is not correct."),

    /**
     * The enclave identifier is invalid.
     */
    ERROR_INVALID_ENCLAVE_ID(0x2002, "The enclave identifier is invalid."),

    /**
     * The signature is invalid.
     */
    ERROR_INVALID_SIGNATURE(0x2003, "The signature is invalid"),

    /**
     * The enclave is signed as product enclave, and can not be created as debuggable enclave.
     */
    ERROR_NDEBUG_ENCLAVE(0x2004, "The enclave is signed as product enclave, and can not be created as debuggable enclave"),

    /**
     * Not enough EPC is available to load the enclave.
     */
    ERROR_OUT_OF_EPC(0x2005, "Not enough EPC is available to load the enclave"),

    /**
     * Cannot open SGX device.
     */
    ERROR_NO_DEVICE(0x2006, "Cannot open SGX device"),

    /**
     * Page mapping failed in driver.
     */
    ERROR_MEMORY_MAP_CONFLICT(0x2007, "Page mapping failed in driver"),

    /**
     * The metadata is incorrect.
     */
    ERROR_INVALID_METADATA(0x2009, "The metadata is incorrect"),

    /**
     * Device is busy, mostly EINIT failed.
     */
    ERROR_DEVICE_BUSY(0x200c, "Device is busy, mostly EINIT failed"),

    /**
     * Metadata version is inconsistent between uRTS and sgx_sign, or uRTS is incompatible with current platform.
     */
    ERROR_INVALID_VERSION(0x200d, "Metadata version is inconsistent between uRTS and sgx_sign, or uRTS is incompatible with current platform"),

    /**
     * The target enclave 32/64 bit mode or SIM/HW mode is incompatible with the mode of current uRTS.
     */
    ERROR_MODE_INCOMPATIBLE(0x200e, "The target enclave 32/64 bit mode or SIM/HW mode is incompatible with the mode of current uRTS"),

    /**
     * Cannot open enclave file.
     */
    ERROR_ENCLAVE_FILE_ACCESS(0x200f, "Cannot open enclave file"),

    /**
     * The MiscSelct/MiscMask settings are not correct.
     */
    ERROR_INVALID_MISC(0x2010, "The MiscSelct/MiscMask settings are not correct"),

    /**
     * Indicates verification error for reports, sealed data, etc.
     */
    ERROR_MAC_MISMATCH(0x3001, "Indicates verification error for reports, sealed data, etc"),

    /**
     * The enclave is not authorized.
     */
    ERROR_INVALID_ATTRIBUTE(0x3002, "The enclave is not authorized"),

    /**
     * The CPU SVN is beyond platform's CPU SVN value.
     */
    ERROR_INVALID_CPUSVN(0x3003, "The CPU SVN is beyond platform's CPU SVN value"),

    /**
     * The ISV SVN is greater than the enclave's ISV SVN.
     */
    ERROR_INVALID_ISVSVN(0x3004, "The ISV SVN is greater than the enclave's ISV SVN"),

    /**
     * The key name is an unsupported value.
     */
    ERROR_INVALID_KEYNAME(0x3005, "The key name is an unsupported value"),

    /**
     * Indicates AESM didn't respond or the requested service is not supported.
     */
    ERROR_SERVICE_UNAVAILABLE(0x4001, "Indicates AESM didn't respond or the requested service is not supported"),

    /**
     * The request to AESM timed out.
     */
    ERROR_SERVICE_TIMEOUT(0x4002, "The request to AESM timed out"),

    /**
     * Indicates EPID blob verification error.
     */
    ERROR_AE_INVALID_EPIDBLOB(0x4003, "Indicates EPID blob verification error"),

    /**
     * Enclave has no privilege to get launch token.
     */
    ERROR_SERVICE_INVALID_PRIVILEGE(0x4004, "Enclave has no privilege to get launch token"),

    /**
     * The EPID group membership is revoked.
     */
    ERROR_EPID_MEMBER_REVOKED(0x4005, "The EPID group membership is revoked"),

    /**
     * SGX needs to be updated.
     */
    ERROR_UPDATE_NEEDED(0x4006, "SGX needs to be updated"),

    /**
     * Network connection or proxy settings issue is encountered.
     */
    ERROR_NETWORK_FAILURE(0x4007, "Network connection or proxy settings issue is encountered"),

    /**
     * Session is invalid or ended by server.
     */
    ERROR_AE_SESSION_INVALID(0x4008, "Session is invalid or ended by server"),

    /**
     * Requested service is temporarily not available.
     */
    ERROR_BUSY(0x400a, "Requested service is temporarily not available"),

    /**
     * Monotonic Counter doesn't exist or has been invalidated.
     */
    ERROR_MC_NOT_FOUND(0x400c, "Monotonic Counter doesn't exist or has been invalidated"),

    /**
     * Caller doesn't have access to specified VMC.
     */
    ERROR_MC_NO_ACCESS_RIGHT(0x400d, "Caller doesn't have access to specified VMC"),

    /**
     * Monotonic counters are used up.
     */
    ERROR_MC_USED_UP(0x400e, "Monotonic counters are used up"),

    /**
     * Monotonic counters exceeds quota limitation.
     */
    ERROR_MC_OVER_QUOTA(0x400f, "Monotonic counters exceeds quota limitation"),

    /**
     * Key derivation function doesn't match during key exchange.
     */
    ERROR_KDF_MISMATCH(0x4011, "Key derivation function doesn't match during key exchange"),

    /**
     * EPID provisioning failed due to platform not being recognized by backend server.
     */
    ERROR_UNRECOGNIZED_PLATFORM(0x4012, "EPID provisioning failed due to platform not being recognized by backend server"),

    /**
     * Not privileged to perform this operation.
     */
    ERROR_NO_PRIVILEGE(0x5002, "Not privileged to perform this operation"),

    /**
     * The file is in a bad state.
     */
    ERROR_FILE_BAD_STATUS(0x7001, "The file is in a bad state, run sgx_clearerr to try and fix it"),

    /**
     * The KeyID field is all zeros, cannot re-generate the encryption key.
     */
    ERROR_FILE_NO_KEY_ID(0x7002, "The KeyID field is all zeros, cannot re-generate the encryption key"),

    /**
     * The current file name is different then the original file name (not allowed due to potential substitution attack).
     */
    ERROR_FILE_NAME_MISMATCH(0x7003, "The current file name is different then the original file name (not allowed due to potential substitution attack)"),

    /**
     * The file is not an SGX file.
     */
    ERROR_FILE_NOT_SGX_FILE(0x7004, "The file is not an SGX file"),

    /**
     * A recovery file cannot be opened, so flush operation cannot continue (only used when no EXXX is returned).
     */
    ERROR_FILE_CANT_OPEN_RECOVERY_FILE(0x7005, "A recovery file cannot be opened, so flush operation cannot continue (only used when no EXXX is returned)"),

    /**
     * A recovery file cannot be written, so flush operation cannot continue (only used when no EXXX is returned).
     */
    ERROR_FILE_CANT_WRITE_RECOVERY_FILE(0x7006, "A recovery file cannot be written, so flush operation cannot continue (only used when no EXXX is returned)"),

    /**
     * When opening the file, recovery is needed, but the recovery process failed.
     */
    ERROR_FILE_RECOVERY_NEEDED(0x7007, "When opening the file, recovery is needed, but the recovery process failed"),

    /**
     * fflush operation (to disk) failed (only used when no EXXX is returned).
     */
    ERROR_FILE_FLUSH_FAILED(0x7008, "fflush operation (to disk) failed (only used when no EXXX is returned)"),

    /**
     * fclose operation (to disk) failed (only used when no EXXX is returned).
     */
    ERROR_FILE_CLOSE_FAILED(0x7009, "fclose operation (to disk) failed (only used when no EXXX is returned)"),

}
