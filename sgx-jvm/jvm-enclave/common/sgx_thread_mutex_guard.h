#pragma once

#include <sgx_thread.h>

class sgx_thread_mutex_guard {
    sgx_thread_mutex_t * const mutex;
public:
    sgx_thread_mutex_guard(sgx_thread_mutex_t *mutex) noexcept : mutex(mutex) {
        sgx_thread_mutex_lock(mutex);
    }
    ~sgx_thread_mutex_guard() noexcept {
        sgx_thread_mutex_unlock(mutex);
    }
};

