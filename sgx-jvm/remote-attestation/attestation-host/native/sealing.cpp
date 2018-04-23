#include "enclave_u.h"
#include "sealing.hpp"

// Check whether the application enclave is able to unseal a secret.
sgx_status_t unseal_secret(
    sgx_enclave_id_t enclave_id,
    uint8_t *sealed_secret,
    size_t sealed_secret_size
) {
    sgx_status_t status = SGX_SUCCESS;
    sgx_status_t ret = unsealSecret(
        enclave_id, &status, sealed_secret, sealed_secret_size
    );
    return SGX_SUCCESS != ret ? ret : status;
}
