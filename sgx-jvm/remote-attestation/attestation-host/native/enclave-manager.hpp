#ifndef __ENCLAVE_MANAGER_H__
#define __ENCLAVE_MANAGER_H__

#include <sgx_capable.h>
#include <sgx_urts.h>

/**
 * Instantiate a new enclave from a signed enclave binary, and return the
 * identifier of the instance.
 *
 * @param path The file name of the signed enclave binary to load.
 * @param use_platform_services If true, Intel's platform services are used to
 * add extra protection against replay attacks during nonce generation and to
 * provide a trustworthy monotonic counter.
 * @param result Variable receiving the result of the operation, if not NULL.
 * @param token Pointer to launch token; cannot be NULL.
 *
 * @return The identifier of the created enclave.
 */
sgx_enclave_id_t create_enclave(
    const char *path,
    bool use_platform_services,
    sgx_status_t *result,
    sgx_launch_token_t *token
);

/**
 * Destroy enclave if currently loaded.
 *
 * @param enclave_id The identifier of the enclave to destroy.
 *
 * @return True if the enclave was active and got destroyed. False otherwise.
 */
bool destroy_enclave(
    sgx_enclave_id_t enclave_id
);

/**
 * Check the status of the SGX device on the current machine.
 */
sgx_device_status_t get_device_status(void);

/**
 * Report which extended Intel EPID Group the client uses by default. The key
 * used to sign a Quote will be a member of the extended EPID Group reported in
 * this API. The application will typically use this value to tell the ISV
 * Service Provider which group to use during remote attestation.
 *
 * @param result Variable receiving the result of the operation, if not NULL.
 *
 * @return The extended EPID group identifier.
 */
uint32_t get_extended_group_id(sgx_status_t *result);

#endif /* __ENCLAVE_MANAGER_H__ */
