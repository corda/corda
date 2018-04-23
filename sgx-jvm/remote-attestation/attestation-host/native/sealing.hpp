#ifndef __SEALING_H__
#define __SEALING_H__

#include <cstdlib>
#include <sgx_urts.h>

/**
 * Check whether the application enclave is able to unseal a persisted, sealed
 * secret.
 *
 * @param enclave_id The identifier of the application enclave.
 * @param sealed_secret The pre-existing, sealed secret.
 * @param sealed_secret_size The size of the sealed secret.
 *
 * @return An indication of whether or not the enclave was able to unseal the
 * secret.
 */
sgx_status_t unseal_secret(
    sgx_enclave_id_t enclave_id,
    uint8_t *sealed_secret,
    size_t sealed_secret_size
);

#endif /* __SEALING_H__ */
