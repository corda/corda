#include <cstddef>

#include <sgx.h>
#include <sgx_key_exchange.h>
#include <sgx_uae_service.h>

#include "enclave-manager.hpp"
#include "logging.hpp"

// Instantiate a new enclave from a signed enclave binary, and return the
// identifier of the instance.
sgx_enclave_id_t create_enclave(
    const char *path,
    bool use_platform_services,
    sgx_status_t *result,
    sgx_launch_token_t *token
) {
    int updated = 0; // Indication of whether the launch token was updated.
    sgx_enclave_id_t enclave_id = 0; // The identifier of the created enclave.

    // If the launch token is empty, then create a new enclave. Otherwise, try
    // to re-activate the existing enclave. `SGX_DEBUG_FLAG` is automatically
    // set to 1 in debug mode, and 0 in release mode.
    sgx_status_t status = sgx_create_enclave(
        path, SGX_DEBUG_FLAG, token, &updated, &enclave_id, NULL
    );

    LOG(enclave_id, status, 0, "sgx_create_enclave()");

    // Store the return value of the operation.
    if (NULL != result) {
        *result = status;
    }

    // Return the identifier of the created enclave. Remember that if `status`
    // is `SGX_ERROR_ENCLAVE_LOST`, the enclave should be destroyed and then
    // re-created.
    return (SGX_SUCCESS == status) ? enclave_id : 0;
}

// Destroy enclave if currently loaded.
bool destroy_enclave(sgx_enclave_id_t enclave_id) {
    if (enclave_id != 0){
        // Attempt to destroy the enclave if we are provided with a valid
        // enclave identifier.
        sgx_status_t status = sgx_destroy_enclave(enclave_id);

        LOG(enclave_id, status, 0, "sgx_destroy_enclave()");

        return SGX_SUCCESS == status;
    }
    return false;
}

// Check the status of the SGX device on the current machine.
sgx_device_status_t get_device_status(void) {
#if SGX_SIM == 1
#pragma message "get_device_status() is being simulated"
    // If in simulation mode, simulate device capabilities.
    return SGX_ENABLED;
#endif

    // Try to retrieve the current status of the SGX device.
    sgx_device_status_t status;
    sgx_status_t ret = sgx_cap_enable_device(&status);

    LOG(0, ret, 0, "sgx_cap_enable_device() = { status = %x }", status);

    if (SGX_SUCCESS != ret) {
        return SGX_DISABLED;
    }

    return status;
}

// Report which extended Intel EPID Group the client uses by default.
uint32_t get_extended_group_id(sgx_status_t *result) {
    uint32_t egid;

    // The extended EPID group identifier is indicative of which attestation
    // service the client is supposed to be communicating with. Currently, only
    // a value of zero is supported, which is referring to Intel. The user
    // should verify the retrieved extended group identifier, as any other
    // value than zero will be disregarded by the service provider.
    sgx_status_t status = sgx_get_extended_epid_group_id(&egid);

    LOG(0, status, 0, "sgx_get_extended_epid_group_id() = %u", egid);

    // Store the return value of the operation.
    if (NULL != result) {
        *result = status;
    }

    return egid;
}
