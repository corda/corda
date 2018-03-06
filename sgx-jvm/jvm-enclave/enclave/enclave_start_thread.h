#pragma once

#include "internal/thread_data.h"

class sgx_thread_completion;

extern "C" {
    thread_data_t *start_thread(void (*routine)(void *), void *param, sgx_thread_completion*);
}

