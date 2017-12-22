#include <cstring>
#include <sgx_tcrypto.h>
#include <sgx_tkey_exchange.h>

#include "enclave_t.h"

#define CHECKED(expr) { \
    sgx_status_t _status = (expr); \
    if (SGX_SUCCESS != _status) { return _status; } \
}

#define MAX_SECRET_SIZE 128

extern "C" {

static const uint8_t safe_empty[] = {};

// === Initialization and Finalization =======================================

static inline sgx_status_t create_pse_session(
    bool use_platform_services
) {
    // If desired, try up to three times to establish a PSE session.
    sgx_status_t status = SGX_SUCCESS;
    if (use_platform_services) {
        int retry_count = 3;
        do { status = sgx_create_pse_session(); }
        while (SGX_ERROR_BUSY == status && --retry_count);
    }
    return status;
}

static inline sgx_status_t close_pse_session(
    bool use_platform_services
) {
    // If a PSE session was created, close it properly.
    sgx_status_t status = SGX_SUCCESS;
    if (use_platform_services) {
         status = sgx_close_pse_session();
    }
    return status;
}

// Initialize the remote attestation process.
sgx_status_t initializeRemoteAttestation(
    bool use_platform_services,
    const sgx_ec256_public_t *challenger_key,
    sgx_ra_context_t *context
) {
    sgx_status_t status;

    // Abort if the public key of the challenger and/or the output context
    // variable is not provided.
    if (NULL == challenger_key || NULL == context) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // If desired, try to establish a PSE session.
    CHECKED(create_pse_session(use_platform_services));

    // Initialize the remote attestation and key exchange process, and place
    // the resulting context in `context`.
    status = sgx_ra_init(challenger_key, use_platform_services, context);

    // If a PSE session was created, close it properly.
    CHECKED(close_pse_session(use_platform_services));
    return status;
}

// Clean up and finalize the remote attestation process.
sgx_status_t finalizeRemoteAttestation(
    sgx_ra_context_t context
) {
    // Release the remote attestation and key exchange context after the
    // process has been completed.
    return sgx_ra_close(context);
}

// === Remote Attestation Verification =======================================

/*
 * This function compares `len` bytes from buffer `b1` and `b2` in constant
 * time to protect against side-channel attacks. For sensitive code running
 * inside an enclave, this function is preferred over `memcmp`.
 */
static int consttime_memequal(const void *b1, const void *b2, size_t len) {
    // Written by Matthias Drochner <drochner@NetBSD.org>. Public domain.
    const unsigned char
        *c1 = (unsigned char*)b1,
        *c2 = (unsigned char*)b2;
    unsigned int res = 0;

    while (len--) res |= *c1++ ^ *c2++;

    /*
     * Map 0 to 1 and [1, 256) to 0 using only constant-time
     * arithmetic.
     *
     * This is not simply `!res' because although many CPUs support branchless
     * conditional moves and many compilers will take advantage of them,
     * certain compilers generate branches on certain CPUs for `!res'.
     */
    return (1 & ((res - 1) >> 8));
}

// Verify CMAC from the challenger to protect against spoofed results.
sgx_status_t verifyCMAC(
    sgx_ra_context_t context,
    const uint8_t *message,
    size_t message_size,
    const uint8_t *cmac,
    size_t cmac_size
) {
    // Check inputs.
    if (sizeof(sgx_mac_t) != cmac_size || NULL == cmac) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    if (UINT32_MAX < message_size || ((NULL == message) && (message_size > 0))) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // Get negotiated MK key of remote attestation and key exchange session.
    sgx_ec_key_128bit_t mk_key = { 0 };
    CHECKED(sgx_ra_get_keys(context, SGX_RA_KEY_MK, &mk_key));

    // Perform 128-bit CMAC hash over the first four bytes of the status
    // obtained from the challenger.
    uint8_t computed_cmac[SGX_CMAC_MAC_SIZE] = { 0 };
    const uint8_t* safe_message = (message == NULL) ? safe_empty : message;
    CHECKED(sgx_rijndael128_cmac_msg(
        &mk_key, safe_message, message_size, &computed_cmac
    ));

    // Compare the computed CMAC-SMK with the provided one.
    if (0 == consttime_memequal(computed_cmac, cmac, sizeof(computed_cmac))) {
        return SGX_ERROR_MAC_MISMATCH;
    }

    // Can further test number of uses of the secret data, and require
    // re-attestation after X uses... But, for now, we're happy!
    return SGX_SUCCESS;
}

// Verify attestation response from the challenger.
sgx_status_t verifyAttestationResponse(
    sgx_ra_context_t context,
    const uint8_t *secret,
    size_t secret_size,
    const uint8_t *gcm_iv,
    const uint8_t *gcm_mac,
    size_t gcm_mac_size,
    uint8_t *sealed_secret,
    size_t sealed_secret_size
) {
    // Check inputs.
    if (secret_size > MAX_SECRET_SIZE || NULL == secret) {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (gcm_mac_size != SGX_AESGCM_MAC_SIZE || NULL == gcm_mac) {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    if (sealed_secret_size - sizeof(sgx_sealed_data_t) > MAX_SECRET_SIZE) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // Get negotiated SK key of remote attestation and key exchange session.
    sgx_ec_key_128bit_t sk_key;
    CHECKED(sgx_ra_get_keys(context, SGX_RA_KEY_SK, &sk_key));

    // Decrypt using Rijndael AES-GCM.
    uint8_t *decrypted_secret = (uint8_t*)malloc(secret_size);
    CHECKED(sgx_rijndael128GCM_decrypt(
        &sk_key, secret, secret_size, decrypted_secret, gcm_iv,
        SGX_AESGCM_IV_SIZE, NULL, 0, (sgx_aes_gcm_128bit_tag_t*)gcm_mac
    ));

    // Return sealed secret if requested.
    if (NULL != sealed_secret && secret_size <= sealed_secret_size) {
        // Seal the secret so that it can be returned to the untrusted
        // environment.
        CHECKED(sgx_seal_data(
            0, NULL, secret_size, decrypted_secret,
            sealed_secret_size, (sgx_sealed_data_t*)sealed_secret
        ));
    }

    // Free temporary memory.
    free(decrypted_secret);

    return SGX_SUCCESS;
}

// Check whether the sealed secret is unsealable or not.
sgx_status_t unsealSecret(
    uint8_t *sealed_secret,
    size_t sealed_secret_size
) {
    // Allocate temporary buffer for the output of the operation.
    uint8_t *buffer = (uint8_t*)malloc(sealed_secret_size);
    uint32_t buffer_size = sealed_secret_size;

    if (NULL == buffer) {
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    // Attempt to unseal the secret.
    sgx_status_t status = sgx_unseal_data(
        (sgx_sealed_data_t*)sealed_secret, NULL, NULL,
        buffer, &buffer_size
    );

    // Free up the temporary memory buffer.
    free(buffer);

    return status;
}

}
