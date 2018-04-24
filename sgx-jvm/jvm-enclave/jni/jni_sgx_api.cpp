#include "jni_sgx_api.h"

#include "java_u.h"
#include "sgx_utilities.h"
#include "enclave_map.h"
#include "enclave_metadata.h"
#include <sgx_urts.h>

extern "C" {

JNIEXPORT jstring JNICALL Java_com_r3_enclaves_txverify_NativeSgxApi_verify(JNIEnv *env, jclass, jstring enclave_path, jbyteArray transaction) {
    sgx_launch_token_t token = {0};
    sgx_enclave_id_t enclave_id = {0};
    int updated = 0;

    const char *enclave_path_sz = env->GetStringUTFChars(enclave_path, NULL);
    jbyte *transaction_bytes = env->GetByteArrayElements(transaction, NULL);

    sgx_measurement_t mr_enclave;
    enclave_hash_result_t hash_result = retrieve_enclave_hash(enclave_path_sz, mr_enclave.m);
    if (EHR_SUCCESS != hash_result) {
        return NULL;
    }

    CHECK_SGX(sgx_create_enclave(enclave_path_sz, SGX_DEBUG_FLAG, &token, &updated, &enclave_id, NULL));
    add_enclave_mapping(&mr_enclave, enclave_id);

    char error[1024] = {0};
    printf("Array length %d\n", env->GetArrayLength(transaction));
    CHECK_SGX(check_transaction(enclave_id, transaction_bytes, env->GetArrayLength(transaction), &error[0]));
    if (error[0]) {
        return env->NewStringUTF(error);
    } else {
        return NULL;
    }
}

}
