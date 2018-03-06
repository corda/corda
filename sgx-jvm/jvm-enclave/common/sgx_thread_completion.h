#pragma once

#include <sgx_thread.h>

class sgx_thread_completion {
    bool completed;
    sgx_thread_mutex_t mutex;
    sgx_thread_cond_t thread_complete;

public:
    sgx_thread_completion() noexcept : completed(false) {
        sgx_thread_mutex_init(&mutex, NULL);
        sgx_thread_cond_init(&thread_complete, NULL);
    }
    ~sgx_thread_completion() noexcept {
        sgx_thread_cond_destroy(&thread_complete);
        sgx_thread_mutex_destroy(&mutex);
    }
    void complete() noexcept;
    void wait() noexcept;
};

