#pragma once

#include "sgx_thread.h"
#include "sgx_trts.h"
#include "java_t.h"
#include <stdlib.h>
#include "internal/global_data.h"

thread_data_t *start_thread(void (*routine)(void *), void *param);

struct sgx_thread_mutex_guard {
    sgx_thread_mutex_t * const mutex;
    sgx_thread_mutex_guard(sgx_thread_mutex_t *mutex) : mutex(mutex) {
        sgx_thread_mutex_lock(mutex);
    }
    ~sgx_thread_mutex_guard() {
        sgx_thread_mutex_unlock(mutex);
    }
};
