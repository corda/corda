#include <sgx_thread_completion.h>

void sgx_thread_completion::complete() noexcept {
    sgx_thread_mutex_lock(&mutex);
    completed = true;
    sgx_thread_mutex_unlock(&mutex);
    sgx_thread_cond_signal(&thread_complete);
}

void sgx_thread_completion::wait() noexcept {
    sgx_thread_mutex_lock(&mutex);
    if (!completed) {
        sgx_thread_cond_wait(&thread_complete, &mutex);
    }
    sgx_thread_mutex_unlock(&mutex);
}
