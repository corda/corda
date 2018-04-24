#include <assert.h>
#include <pthread.h>
#include "java_u.h"
#include "sgx_utilities.h"
#include "enclave_map.h"

extern "C" {
    struct new_thread_data {
        sgx_measurement_t mr_enclave;
        unsigned int nonce;
    };
    void *create_new_enclave_thread(void *param) {
        auto thread_data = (new_thread_data*) param;
        sgx_enclave_id_t enclave_id = get_enclave_id(&thread_data->mr_enclave);
        CHECK_SGX(create_new_thread(enclave_id, thread_data->nonce));
        delete thread_data;
    }
    void request_new_thread(sgx_measurement_t mr_enclave, unsigned int nonce) {
        pthread_t enclave_thread;
        new_thread_data *thread_data = new new_thread_data { mr_enclave, nonce };
        int ret = pthread_create(&enclave_thread, NULL, create_new_enclave_thread, (void *)thread_data);
        assert(!ret);
    }
}
