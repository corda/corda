#include <cstdlib>
#include <cstring>

#include <sgx_tcrypto.h>
#include <sgx_tseal.h>

#include "wrapper.hpp"
#include "jni.hpp"

#include "logging.hpp"
#include "enclave-manager.hpp"
#include "remote-attestation.hpp"
#include "sealing.hpp"

NATIVE_WRAPPER(jint, getDeviceStatus)
  (JNIEnv *, jobject)
{
    // Get the status of the SGX device on the local machine.
    return get_device_status();
}

NATIVE_WRAPPER(jobject, getExtendedGroupIdentifier)
  (JNIEnv *env, jobject)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("ExtendedGroupIdentifierResult"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "(IJ)V");
    if (cid == NULL) { return NULL; }

    // Get the extended EPID group identifier from SGX.
    sgx_status_t status = SGX_ERROR_UNEXPECTED;
    uint32_t extended_group_id = get_extended_group_id(&status);

    // Construct and return ExtendedGroupIdentifierResult(identifier, status).
    return env->NewObject(klass, cid, extended_group_id, status);
}

NATIVE_WRAPPER(jobject, createEnclave)
  (JNIEnv *env, jobject, jstring path, jboolean use_platform_services,
   jbyteArray in_launch_token)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("EnclaveResult"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "(J[BJ)V");
    if (cid == NULL) { return NULL; }

    // Marshall inputs.
    const char *n_path = env->GetStringUTFChars(path, NULL);
    sgx_status_t status;

    // Initialize launch token.
    sgx_launch_token_t launch_token = { 0 };
    env->GetByteArrayRegion(
        in_launch_token, 0, sizeof(sgx_launch_token_t), (jbyte*)&launch_token
    );

    // Create the enclave.
    sgx_enclave_id_t enclave_id = create_enclave(
        n_path, (bool)use_platform_services, &status,
        &launch_token
    );

    // Construct resulting launch token (could be the same as the input).
    jbyteArray _launch_token = env->NewByteArray(sizeof(sgx_launch_token_t));
    env->SetByteArrayRegion(
        _launch_token, 0, sizeof(sgx_launch_token_t), (jbyte*)&launch_token
    );

    // Free up memory.
    env->ReleaseStringUTFChars(path, n_path);

    // Construct and return EnclaveResult(identifier, launch_token, status).
    return env->NewObject(klass, cid, enclave_id, _launch_token, status);
}

NATIVE_WRAPPER(jboolean, destroyEnclave)
  (JNIEnv *, jobject, jlong enclave_id)
{
    // Destroy the enclave if a valid identifier has been passed in.
    if (enclave_id != 0) {
        return destroy_enclave((sgx_enclave_id_t)enclave_id);
    } else {
        return false;
    }
}

NATIVE_WRAPPER(jobject, initializeRemoteAttestation)
  (JNIEnv *env, jobject, jlong enclave_id, jboolean use_platform_services,
   jbyteArray in_key_challenger)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("InitializationResult"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "(IJ)V");
    if (cid == NULL) { return NULL; }

    // Marshall the public key passed in from the JVM.
    sgx_ec256_public_t key_challenger = { 0 };
    env->GetByteArrayRegion(
        in_key_challenger, 0, sizeof(sgx_ec256_public_t),
        (jbyte*)&key_challenger
    );

    // Initialize the remote attestation context.
    sgx_ra_context_t context;
    sgx_status_t status = initialize_remote_attestation(
        enclave_id, use_platform_services, &key_challenger, &context
    );

    // Construct and return InitializationResult(context, status).
    return env->NewObject(klass, cid, context, status);
}

NATIVE_WRAPPER(jlong, finalizeRemoteAttestation)
  (JNIEnv *, jobject, jlong enclave_id, jint context)
{
    // Finalize the remote attestation
    return finalize_remote_attestation(enclave_id, context);
}

NATIVE_WRAPPER(jobject, getPublicKeyAndGroupIdentifier)
  (JNIEnv *env, jobject, jlong enclave_id, jint context, jint max_retry_count,
   jint retry_wait_in_secs)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("PublicKeyAndGroupIdentifier"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "([BIJ)V");
    if (cid == NULL) { return NULL; }

    // Get the public key of the application enclave, and the group identifier
    // of the platform.
    sgx_ec256_public_t public_key;
    sgx_epid_group_id_t group_id;
    sgx_status_t status = get_public_key_and_group_identifier(
        enclave_id, context, &public_key, &group_id, max_retry_count,
        retry_wait_in_secs
    );

    // Cast group identifier into an unsigned integer.
    uint32_t gid = *((uint32_t*)group_id);

    // Create managed array used to return the enclave's public key.
    jbyteArray _public_key = env->NewByteArray(sizeof(sgx_ec256_public_t));
    if (NULL == _public_key) {
        // Out of memory - abort
        return NULL;
    }

    // Copy public key bytes over to managed array.
    env->SetByteArrayRegion(
        _public_key, 0, sizeof(sgx_ec256_public_t), (jbyte*)&public_key
    );

    // Return PublicKeyAndGroupIdentifier(publicKey, groupIdentifier, status).
    return env->NewObject(klass, cid, _public_key, gid, status);
}

NATIVE_WRAPPER(jobject, processServiceProviderDetailsAndGenerateQuote)
   (JNIEnv *env, jobject, jlong enclave_id, jint context,
    jbyteArray in_challenger_public_key, jbyteArray in_service_provider_id,
    jshort quote_type, jshort key_derivation_function, jbyteArray in_signature,
    jbyteArray in_mac, jint revocation_list_size,
    jbyteArray in_revocation_list, jint max_retry_count,
    jint retry_wait_in_secs)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("QuoteResult"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "([B[B[B[BJ)V");
    if (cid == NULL) { return NULL; }

    // Marshal inputs.
    sgx_ec256_public_t challenger_public_key;
    sgx_spid_t service_provider_id;
    sgx_ec256_signature_t signature;
    sgx_mac_t mac;
    uint8_t *revocation_list = (uint8_t*)malloc(revocation_list_size);

    // Check if there's enough free memory to allocate a buffer for the
    // revocation list.
    if (NULL == revocation_list) {
        return NULL;
    }

    env->GetByteArrayRegion(
        in_challenger_public_key, 0, sizeof(sgx_ec256_public_t),
        (jbyte*)&challenger_public_key
    );
    env->GetByteArrayRegion(
        in_service_provider_id, 0, sizeof(sgx_spid_t),
        (jbyte*)&service_provider_id
    );
    env->GetByteArrayRegion(
        in_signature, 0, sizeof(sgx_ec256_signature_t),
        (jbyte*)&signature
    );
    env->GetByteArrayRegion(
        in_mac, 0, sizeof(sgx_mac_t),
        (jbyte*)&mac
    );
    env->GetByteArrayRegion(
        in_revocation_list, 0, revocation_list_size, (jbyte*)revocation_list
    );

    // Output variables.
    sgx_mac_t enclave_mac = { 0 };
    sgx_ec256_public_t enclave_public_key = { 0 };
    sgx_ps_sec_prop_desc_t security_properties = { 0 };
    uint8_t *quote = NULL;
    size_t quote_size = 0;

    // Process details received from challenger via the service provider, and
    // generate quote.
    sgx_status_t status = process_challenger_details_and_generate_quote(
        // Inputs
        enclave_id, context, &challenger_public_key, &service_provider_id,
        quote_type, key_derivation_function, &signature, &mac,
        revocation_list_size, revocation_list,
        // Outputs
        &enclave_mac, &enclave_public_key, &security_properties, &quote,
        &quote_size,
        // Retry logic
        max_retry_count, retry_wait_in_secs
    );

    LOG(
        enclave_id, status, context,
        "process_challenger_details_and_generate_quote() = quote(size=%u)",
        quote_size
    );

    // Create output objects.
    jbyteArray _enclave_mac = env->NewByteArray(sizeof(sgx_mac_t));
    env->SetByteArrayRegion(
        _enclave_mac, 0, sizeof(sgx_mac_t), (jbyte*)&enclave_mac
    );
    jbyteArray _enclave_public_key = env->NewByteArray(
        sizeof(sgx_ec256_public_t)
    );
    env->SetByteArrayRegion(
        _enclave_public_key, 0, sizeof(sgx_ec256_public_t),
        (jbyte*)&enclave_public_key
    );
    jbyteArray _security_properties = env->NewByteArray(
        sizeof(sgx_ps_sec_prop_desc_t)
    );
    env->SetByteArrayRegion(
        _security_properties, 0, sizeof(sgx_ps_sec_prop_desc_t),
        (jbyte*)&security_properties
    );

    jbyteArray _quote = NULL;

    // Free up memory.
    if (NULL != quote) {
        _quote = env->NewByteArray(quote_size);
        env->SetByteArrayRegion(_quote, 0, quote_size, (jbyte*)quote);
        free(quote);
    } else {
        _quote = env->NewByteArray(0);
    }
    free(revocation_list);

    // Return QuoteResult(mac, publicKey, securityProperties, quote, status).
    return env->NewObject(
        klass, cid,
        _enclave_mac, _enclave_public_key, _security_properties, _quote, status
    );
}

NATIVE_WRAPPER(jobject, verifyAttestationResponse)
  (JNIEnv *env, jobject, jlong enclave_id, jint context,
   jbyteArray message, jbyteArray cmac, jbyteArray secret,
   jbyteArray gcm_iv, jbyteArray gcm_mac)
{
    // FindClass/GetMethodID both throw an exception upon failure, so we don't
    // need to perform any further NULL-checks.
    jclass klass = env->FindClass(KLASS("VerificationResult"));
    jmethodID cid = env->GetMethodID(klass, "<init>", "([BJJ)V");
    if (cid == NULL) { return NULL; }

    // Get buffer sizes.
    size_t message_size = (message == NULL) ? 0 : env->GetArrayLength(message);
    size_t cmac_size = (cmac == NULL) ? 0 : env->GetArrayLength(cmac);
    size_t secret_size = (secret == NULL) ? 0 : env->GetArrayLength(secret);
    size_t gcm_mac_size = (gcm_mac == NULL) ? 0 : env->GetArrayLength(gcm_mac);

    // Allocate buffers.
    uint8_t *_message = (uint8_t*)malloc(message_size);
    uint8_t *_cmac = (uint8_t*)malloc(cmac_size);
    uint8_t *_secret = (uint8_t*)malloc(secret_size);
    uint8_t *_gcm_iv = (uint8_t*)calloc(1, SGX_AESGCM_IV_SIZE);
    uint8_t *_gcm_mac = (uint8_t*)malloc(gcm_mac_size);

    // Length of secret is preserved during encryption, but prepend header.
    size_t _sealed_secret_size = sizeof(sgx_sealed_data_t) + secret_size;
    uint8_t *_sealed_secret = (uint8_t*)malloc(_sealed_secret_size);

    // Check if we ran out of memory.
    if (NULL == _message || NULL == _cmac || NULL == _secret ||
            NULL == _gcm_iv || NULL == _gcm_mac || NULL == _sealed_secret) {
        free(_message);
        free(_cmac);
        free(_secret);
        free(_gcm_iv);
        free(_gcm_mac);
        free(_sealed_secret);
        return NULL;
    }

    // Marshal inputs.
    if (message != NULL) {
        env->GetByteArrayRegion(message, 0, message_size, (jbyte*)_message);
    }
    if (cmac != NULL) {
        env->GetByteArrayRegion(cmac, 0, cmac_size, (jbyte*)_cmac);
    }
    if (secret != NULL) {
        env->GetByteArrayRegion(secret, 0, secret_size, (jbyte*)_secret);
    }
    if (gcm_iv != NULL) {
        env->GetByteArrayRegion(gcm_iv, 0, SGX_AESGCM_IV_SIZE, (jbyte*)_gcm_iv);
    }
    if (gcm_mac != NULL) {
        env->GetByteArrayRegion(gcm_mac, 0, gcm_mac_size, (jbyte*)_gcm_mac);
    }

    // Verify the attestation response received from the service provider.
    sgx_status_t cmac_status = SGX_SUCCESS;
    sgx_status_t status = verify_attestation_response(
        enclave_id, context, _message, message_size, _cmac, cmac_size,
        _secret, secret_size, _gcm_iv, _gcm_mac, gcm_mac_size, _sealed_secret,
        &_sealed_secret_size, &cmac_status
    );

    // Free temporary allocations.
    free(_message);
    free(_cmac);
    free(_secret);
    free(_gcm_iv);
    free(_gcm_mac);

    // Marshal outputs.
    jbyteArray sealed_secret;
    if (NULL != _sealed_secret) {
        sealed_secret = env->NewByteArray(_sealed_secret_size);
        env->SetByteArrayRegion(
            sealed_secret, 0, _sealed_secret_size, (jbyte*)_sealed_secret
        );
        free(_sealed_secret);
    } else {
        sealed_secret = env->NewByteArray(0);
    }

    // Return VerificationResult(sealedSecret, cmacValidationStatus, status).
    return env->NewObject(klass, cid, sealed_secret, cmac_status, status);
}

NATIVE_WRAPPER(jlong, unsealSecret)
  (JNIEnv *env, jobject, jlong enclave_id, jbyteArray sealed_secret)
{
    // Check if we've actually got a sealed secret to unseal.
    uint8_t *_sealed_secret = NULL;
    size_t sealed_secret_size = env->GetArrayLength(sealed_secret);
    if (0 == sealed_secret_size) {
        return SGX_ERROR_INVALID_PARAMETER;
    }

    // Allocate buffer.
    _sealed_secret = (uint8_t*)malloc(sealed_secret_size);

    // Check if we ran out of memory.
    if (NULL == _sealed_secret) {
        return 0;
    }

    // Marshal inputs.
    env->GetByteArrayRegion(
        sealed_secret, 0, sealed_secret_size, (jbyte*)_sealed_secret
    );

    // Try to unseal the secret.
    sgx_status_t result = unseal_secret(
        enclave_id, _sealed_secret, sealed_secret_size
    );

    // Free temporary allocations.
    free(_sealed_secret);

    return result;
}
