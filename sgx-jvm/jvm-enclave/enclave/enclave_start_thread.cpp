#include "enclave_start_thread.h"
#include <sgx_thread_mutex_guard.h>
#include <sgx_thread_completion.h>
#include <sgx_trts.h>
#include <sgx_utils.h>
#include <java_t.h>

#include "aex_assert.h"
#include <cstdlib>
#include <map>

struct new_thread_data {
    void *param;
    void (*thread_routine)(void *);
    sgx_thread_cond_t *thread_started;
    sgx_thread_completion *thread_completed;
};

typedef unsigned int nonce_t;
static sgx_thread_mutex_t new_thread_map_mutex;
static std::map<nonce_t, new_thread_data> new_thread_map;
static sgx_thread_mutex_t started_thread_data_map_mutex;
static std::map<nonce_t, thread_data_t *> started_thread_data_map;
struct ThreadMutexInit {
    ThreadMutexInit() noexcept {
        sgx_thread_mutex_init(&new_thread_map_mutex, NULL);
        sgx_thread_mutex_init(&started_thread_data_map_mutex, NULL);
    }
};
static ThreadMutexInit _thread_mutex_init;

static sgx_status_t get_mr_enclave(sgx_measurement_t *mr_enclave) {
    sgx_report_t report;
    sgx_status_t ret = sgx_create_report(NULL, NULL, &report);
    if (ret == SGX_SUCCESS) {
        memcpy(mr_enclave, &report.body.mr_enclave, sizeof(sgx_measurement_t));
    }
    return ret;
}

thread_data_t *start_thread(void (*routine)(void *), void *param, sgx_thread_completion *thread_completed) {
    sgx_measurement_t mr_enclave = { 0 };
    if (SGX_SUCCESS != get_mr_enclave(&mr_enclave)) {
        return NULL;
    }

    nonce_t nonce;
    aex_assert(SGX_SUCCESS == sgx_read_rand((unsigned char*)&nonce, sizeof(nonce)));
    sgx_thread_cond_t thread_started;
    sgx_thread_cond_init(&thread_started, NULL);
    sgx_thread_mutex_t thread_started_mutex;
    sgx_thread_mutex_init(&thread_started_mutex, NULL);
    sgx_thread_mutex_guard thread_started_guard(&thread_started_mutex);
    new_thread_data thread_init_data = {
        .param = param,
        .thread_routine = routine,
        .thread_started = &thread_started,
        .thread_completed = thread_completed
    };
    {
        sgx_thread_mutex_guard new_thread_map_guard(&new_thread_map_mutex);
        aex_assert(new_thread_map.find(nonce) == new_thread_map.end());
        new_thread_map[nonce] = thread_init_data;
    }
    request_new_thread(mr_enclave, nonce);
    sgx_thread_cond_wait(&thread_started, &thread_started_mutex);
    sgx_thread_mutex_guard started_thread_data_map_guard(&started_thread_data_map_mutex);
    auto thread_data_iter = started_thread_data_map.find(nonce);
    aex_assert(thread_data_iter != started_thread_data_map.end());
    auto thread_data = thread_data_iter->second;
    started_thread_data_map.erase(thread_data_iter);
    return thread_data;
}

void create_new_thread(unsigned int nonce) {
    auto thread_data = get_thread_data();
    bool thread_created = false;
    new_thread_data thread_init_data;
    {
        sgx_thread_mutex_guard new_thread_map_guard(&new_thread_map_mutex);
        auto thread_init_data_iter = new_thread_map.find(nonce);
        aex_assert(thread_init_data_iter != new_thread_map.end());
        thread_init_data = thread_init_data_iter->second;
        new_thread_map.erase(thread_init_data_iter);
        {
            sgx_thread_mutex_guard started_thread_data_map_guard(&started_thread_data_map_mutex);
            aex_assert(started_thread_data_map.find(nonce) == started_thread_data_map.end());
            started_thread_data_map[nonce] = thread_data;
        }
    }
    sgx_thread_cond_signal(thread_init_data.thread_started);
    thread_init_data.thread_routine(thread_init_data.param);
    if (thread_init_data.thread_completed != NULL) {
        thread_init_data.thread_completed->complete();
    }
}

