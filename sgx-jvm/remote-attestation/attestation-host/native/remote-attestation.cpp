#include <string.h>
#include <unistd.h>

#include <sgx_uae_service.h>

#include "enclave_u.h"
#include "logging.hpp"
#include "remote-attestation.hpp"

// Initialize the remote attestation.
sgx_status_t initialize_remote_attestation(
    // Inputs
    sgx_enclave_id_t enclave_id,
    bool use_platform_services,
    sgx_ec256_public_t *key_challenger,

    // Outputs
    sgx_ra_context_t *context
) {
    sgx_status_t ret;

    // Perform ECALL into the application enclave to initialize the remote
    // attestation. The resulting attestation context will be stored in the
    // variable referenced by the `context` parameter.
    sgx_status_t _ret = initializeRemoteAttestation(
        enclave_id, &ret, use_platform_services, key_challenger, context
    );

    LOG(enclave_id, _ret | ret, *context, "initialize_remote_attestation()");

    // If the ECALL itself failed, report why. Otherwise, return the status of
    // the underlying function call.
    return (SGX_SUCCESS != _ret) ? _ret : ret;
}

// Clean up and finalize the remote attestation process.
sgx_status_t finalize_remote_attestation(
    sgx_enclave_id_t enclave_id,
    sgx_ra_context_t context
) {
    sgx_status_t ret;

    // Perform ECALL into the application enclave to close the current
    // attestation context and tidy up.
    sgx_status_t _ret = finalizeRemoteAttestation(enclave_id, &ret, context);

    LOG(enclave_id, _ret | ret, context, "finalize_remote_attestation()");

    // If the ECALL itself failed, report why. Otherwise, return the status of
    // the underlying function call.
    return (SGX_SUCCESS != _ret) ? _ret : ret;
}

// Retrieve the application enclave's public key and the platform's group
// identifier.
sgx_status_t get_public_key_and_group_identifier(
    // Inputs
    sgx_enclave_id_t enclave_id,
    sgx_ra_context_t context,

    // Outputs
    sgx_ec256_public_t *public_key,
    sgx_epid_group_id_t *group_id,

    // Retry logic
    int max_retry_count,
    unsigned int retry_wait_in_secs
) {
    sgx_status_t ret;
    sgx_ra_msg1_t message;

    // It is generally recommended that the caller should wait (typically
    // several seconds to tens of seconds) and retry `sgx_ra_get_msg1()` if
    // `SGX_ERROR_BUSY` is returned.
    int retry_count = max_retry_count;

    while (retry_count-- >= 0) {
        // Using an ECALL proxy to `sgx_ra_get_ga()` in the `sgx_tkey_exchange`
        // library to retrieve the public key of the application enclave.
        ret = sgx_ra_get_msg1(context, enclave_id, sgx_ra_get_ga, &message);

        LOG(enclave_id, ret, context, "sgx_ra_get_msg1()");

        if (SGX_ERROR_BUSY == ret) {
            // Wait before retrying...
            sleep(retry_wait_in_secs);
        } else if (SGX_SUCCESS != ret) {
            return ret;
        } else {
            break;
        }
    }

    // Store the public key; components X and Y, each 256 bits long.
    if (NULL != public_key) {
        memcpy(public_key, &message.g_a, sizeof(sgx_ec256_public_t));
    }

    // Store the EPID group identifier. Note, this is not the same as the
    // extended group identifier.
    if (NULL != group_id) {
        memcpy(group_id, &message.gid, sizeof(sgx_epid_group_id_t));
    }

    return ret;
}

// Process details received from challenger via the service provider, and
// generate quote.
sgx_status_t process_challenger_details_and_generate_quote(
    // Inputs
    sgx_enclave_id_t enclave_id,
    sgx_ra_context_t context,
    sgx_ec256_public_t *challenger_public_key,
    sgx_spid_t *service_provider_id,
    uint16_t quote_type,
    uint16_t key_derivation_function,
    sgx_ec256_signature_t *signature,
    sgx_mac_t *challenger_mac,
    uint32_t revocation_list_size,
    uint8_t *revocation_list,

    // Outputs
    sgx_mac_t *enclave_mac,
    sgx_ec256_public_t *enclave_public_key,
    sgx_ps_sec_prop_desc_t *security_properties,
    uint8_t **quote,
    size_t *quote_size,

    // Retry logic
    int max_retry_count,
    unsigned int retry_wait_in_secs
) {
    sgx_status_t ret = SGX_SUCCESS;
    size_t msg_in_size = sizeof(sgx_ra_msg2_t) + revocation_list_size;
    sgx_ra_msg2_t *msg_in = (sgx_ra_msg2_t*)malloc(msg_in_size);
    sgx_ra_msg3_t *msg_out = NULL;
    uint32_t msg_out_size;

    if (NULL == msg_in) {
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    // Populate input message (message 2 in the Intel attestation flow).
    memcpy(&msg_in->g_b, challenger_public_key, sizeof(sgx_ec256_public_t));
    memcpy(&msg_in->spid, service_provider_id, sizeof(sgx_spid_t));
    msg_in->quote_type = quote_type;
    msg_in->kdf_id = key_derivation_function;
    memcpy(&msg_in->sign_gb_ga, signature, sizeof(sgx_ec256_signature_t));
    memcpy(&msg_in->mac, challenger_mac, sizeof(sgx_mac_t));
    msg_in->sig_rl_size = revocation_list_size;
    if (revocation_list_size > 0) {
        memcpy(&msg_in->sig_rl, revocation_list, revocation_list_size);
    }

    // Nullify outputs.
    *quote = NULL;

    // It is generally recommended that the caller should wait (typically
    // several seconds to tens of seconds) and retry `sgx_ra_proc_msg2()` if
    // `SGX_ERROR_BUSY` is returned.
    int retry_count = max_retry_count;

    while (retry_count-- >= 0) {
        // Using an ECALL proxy to `sgx_ra_proc_msg2_trusted()` in the
        // `sgx_tkey_exchange` library to process the incoming details from the
        // challenger, and `sgx_ra_get_msg3_trusted()` in the same library to
        // generate the quote.
        ret = sgx_ra_proc_msg2(
            context,
            enclave_id,
            sgx_ra_proc_msg2_trusted,
            sgx_ra_get_msg3_trusted,
            msg_in,
            sizeof(sgx_ra_msg2_t) + revocation_list_size,
            &msg_out,
            &msg_out_size
        );

        LOG(enclave_id, ret, context, "sgx_ra_proc_msg2()");

        if (SGX_ERROR_BUSY == ret) {
            // Wait before retrying...
            sleep(retry_wait_in_secs);
        } else {
            break;
        }
    }

    // Populate outputs from the returned message structure.
    if (NULL != msg_out) {
        memcpy(enclave_mac, &msg_out->mac, sizeof(sgx_mac_t));
        memcpy(enclave_public_key, &msg_out->g_a, sizeof(sgx_ec256_public_t));

        size_t z_sec_prop = sizeof(sgx_ps_sec_prop_desc_t);
        memcpy(security_properties, &msg_out->ps_sec_prop, z_sec_prop);
    }

    // Populate the quote structure.
    if (NULL != msg_out) {
        *quote_size = msg_out_size - offsetof(sgx_ra_msg3_t, quote);
        *quote = (uint8_t*)malloc(*quote_size);
        if (NULL != quote) {
            memcpy(*quote, &msg_out->quote, *quote_size);
        }
    } else {
        *quote = NULL;
    }

    // The output message is generated by the library and thus has to be freed
    // upon completion.
    free(msg_out);

    // Allocated due to the variable size revocation list. Free up the
    // temporary structure.
    free(msg_in);

    // Check if the malloc() call for the output quote failed above; if it did,
    // it was due to an out-of-memory condition.
    if (NULL == quote && SGX_SUCCESS == ret) {
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    return ret;
}

sgx_status_t verify_attestation_response(
    // Inputs
    sgx_enclave_id_t enclave_id,
    sgx_ra_context_t context,
    uint8_t *message,
    size_t message_size,
    uint8_t *cmac,
    size_t cmac_size,
    uint8_t *secret,
    size_t secret_size,
    uint8_t *gcm_iv,
    uint8_t *gcm_mac,
    size_t gcm_mac_size,

    // Outputs
    uint8_t *sealed_secret,
    size_t *sealed_secret_size,
    sgx_status_t *cmac_status
) {
    // Check the generated CMAC from the service provider.
    sgx_status_t ret = SGX_SUCCESS;
    sgx_status_t _ret = verifyCMAC(
        enclave_id, &ret, context, message, message_size, cmac, cmac_size
    );

    *cmac_status = ret;
    LOG(enclave_id, _ret, context, "verify_cmac() = %x", (uint32_t)ret);

    // Abort if call failed. Otherwise, forward the outcome to the caller.
    if (SGX_SUCCESS != _ret) {
        return _ret;
    }

    // Try to decrypt and verify the attestation response.
    _ret = verifyAttestationResponse(
        enclave_id, &ret, context, secret, secret_size,
        gcm_iv, gcm_mac, gcm_mac_size,
        sealed_secret, sizeof(sgx_sealed_data_t) + secret_size
    );

    LOG(
        enclave_id, _ret, context,
        "verify_attestation_response() = %x",
        (uint32_t)ret
    );

    // Abort if unable to verify attestation response.
    if (SGX_SUCCESS != (_ret | ret)) {
        return (SGX_SUCCESS != _ret) ? _ret : ret;
    }

    // Return sealed secret if requested. The buffer is populated by the ECALL
    // above, if sealed_secret is non-null.
    if (NULL != sealed_secret_size) {
        *sealed_secret_size = sizeof(sgx_sealed_data_t) + secret_size;
    }

    return SGX_SUCCESS;
}
