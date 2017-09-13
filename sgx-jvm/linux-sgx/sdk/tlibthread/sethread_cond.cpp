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


#include <stdlib.h>
#include <string.h>
#include <errno.h>

#include "util.h"
#include "sethread_internal.h"

int sgx_thread_cond_init(sgx_thread_cond_t *cond, const sgx_thread_condattr_t *unused)
{
    UNUSED(unused);
    CHECK_PARAMETER(cond);

    cond->m_lock = SGX_SPINLOCK_INITIALIZER;
    QUEUE_INIT(&cond->m_queue);

    return 0;
}

int sgx_thread_cond_destroy(sgx_thread_cond_t *cond)
{
    CHECK_PARAMETER(cond);

    SPIN_LOCK(&cond->m_lock);

    if (QUEUE_FIRST(&cond->m_queue) != SGX_THREAD_T_NULL) {
        SPIN_UNLOCK(&cond->m_lock);
        return EBUSY;
    }

    SPIN_UNLOCK(&cond->m_lock);

    return 0;
}

int sgx_thread_cond_wait(sgx_thread_cond_t *cond, sgx_thread_mutex_t *mutex)
{
    CHECK_PARAMETER(cond);
    CHECK_PARAMETER(mutex);

    sgx_thread_t self = (sgx_thread_t)get_thread_data();

    SPIN_LOCK(&cond->m_lock);
    QUEUE_INSERT_TAIL(&cond->m_queue, self);

    sgx_thread_t waiter = SGX_THREAD_T_NULL;
    int ret = sgx_thread_mutex_unlock_lazy(mutex, &waiter);
    if (ret != 0) {
        SPIN_UNLOCK(&cond->m_lock);
        return ret;
    }

    while (1) {
        sgx_thread_t tmp = SGX_THREAD_T_NULL;

        SPIN_UNLOCK(&cond->m_lock);
        /* OPT: if there is a thread waiting on the mutex, wake it in a single OCALL. */
        if (waiter == SGX_THREAD_T_NULL) {
            sgx_thread_wait_untrusted_event_ocall(&ret, TD2TCS(self));
        } else {
            sgx_thread_setwait_untrusted_events_ocall(&ret, TD2TCS(waiter), TD2TCS(self));
            waiter = SGX_THREAD_T_NULL;
        }
        SPIN_LOCK(&cond->m_lock);

        QUEUE_FOREACH(tmp, &cond->m_queue) {
            if (tmp == self) break; /* stop searching and re-wait outside */
        }
        if (tmp == SGX_THREAD_T_NULL) break;     /* current thread isn't in the queue */
    }

    SPIN_UNLOCK(&cond->m_lock);
    sgx_thread_mutex_lock(mutex);

    return 0;
}

int sgx_thread_cond_signal(sgx_thread_cond_t *cond)
{
    int err = 0;
    sgx_thread_t waiter = SGX_THREAD_T_NULL;

    CHECK_PARAMETER(cond);
    SPIN_LOCK(&cond->m_lock);

    if ((waiter = QUEUE_FIRST(&cond->m_queue)) == SGX_THREAD_T_NULL) {
        SPIN_UNLOCK(&cond->m_lock);
        return 0;
    }

    QUEUE_REMOVE_HEAD(&cond->m_queue);
    SPIN_UNLOCK(&cond->m_lock);

    sgx_thread_set_untrusted_event_ocall(&err, TD2TCS(waiter));    /* wake first pending thread */

    return 0;
}

int sgx_thread_cond_broadcast(sgx_thread_cond_t *cond)
{
    size_t n_waiter = 0; int err = 0;
    sgx_thread_t waiter = SGX_THREAD_T_NULL;
    const void **waiters = NULL;

    CHECK_PARAMETER(cond);
    SPIN_LOCK(&cond->m_lock);

    QUEUE_COUNT_ALL(waiter, &cond->m_queue, n_waiter);
    if (n_waiter == 0) {
        SPIN_UNLOCK(&cond->m_lock);
        return 0;
    }

    waiters = (const void **)malloc(n_waiter * sizeof(const void *));
    if (waiters == NULL) {
        SPIN_UNLOCK(&cond->m_lock);
        return ENOMEM;
    }

    const void **tmp = waiters;
    while ((waiter = QUEUE_FIRST(&cond->m_queue)) != SGX_THREAD_T_NULL) {
        QUEUE_REMOVE_HEAD(&cond->m_queue);      /* remove the pending thread */
        *tmp++ = TD2TCS(waiter);
    }

    SPIN_UNLOCK(&cond->m_lock);

    sgx_thread_set_multiple_untrusted_events_ocall(&err, waiters, n_waiter);   /* wake all pending threads up */
    free(waiters);
    return 0;
}
