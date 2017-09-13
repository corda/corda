/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in
 *     the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Intel Corporation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */


#include "sgx_defs.h"
#include "enclave.h"
#include "se_event.h"

#include "internal/se_error_internal.h"

/* wait on untrusted event */
extern "C" int sgx_thread_wait_untrusted_event_ocall(const void *self)
{
    if (self == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    se_handle_t hevent = CEnclavePool::instance()->get_event(self);
    if (hevent == NULL)
        return SE_ERROR_MUTEX_GET_EVENT;

    if (SE_MUTEX_SUCCESS != se_event_wait(hevent))
        return SE_ERROR_MUTEX_WAIT_EVENT;

    return SGX_SUCCESS;
}

/* set untrusted event */
extern "C" int sgx_thread_set_untrusted_event_ocall(const void *waiter)
{
    if (waiter == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    se_handle_t hevent = CEnclavePool::instance()->get_event(waiter);
    if (hevent == NULL)
        return SE_ERROR_MUTEX_GET_EVENT;

    if (SE_MUTEX_SUCCESS != se_event_wake(hevent))
        return SE_ERROR_MUTEX_WAKE_EVENT;

    return SGX_SUCCESS;
}

extern "C" int sgx_thread_set_multiple_untrusted_events_ocall(const void **waiters, size_t total)
{
    if (waiters == NULL || *waiters == NULL)
        return SGX_ERROR_INVALID_PARAMETER;

    for (unsigned int i = 0; i < total; i++) {
        se_handle_t hevent = CEnclavePool::instance()->get_event(*waiters++);

        if (hevent == NULL)
            return SE_ERROR_MUTEX_GET_EVENT;

        if (SE_MUTEX_SUCCESS != se_event_wake(hevent))
            return SE_ERROR_MUTEX_WAKE_EVENT;
    }

    return SGX_SUCCESS;
}

extern "C" int sgx_thread_setwait_untrusted_events_ocall(const void *waiter, const void *self)
{
    int ret = sgx_thread_set_untrusted_event_ocall(waiter);
    if (ret != SGX_SUCCESS) return ret;

    return sgx_thread_wait_untrusted_event_ocall(self);
}
