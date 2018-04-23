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

int sgx_thread_mutex_init(sgx_thread_mutex_t *mutex, const sgx_thread_mutexattr_t *unused)
{
    UNUSED(unused);
    CHECK_PARAMETER(mutex);

    mutex->m_control = SGX_THREAD_MUTEX_NONRECURSIVE;
    mutex->m_refcount = 0;
    mutex->m_owner = SGX_THREAD_T_NULL;
    mutex->m_lock = SGX_SPINLOCK_INITIALIZER;

    QUEUE_INIT(&mutex->m_queue);

    return 0;
}

int sgx_thread_mutex_destroy(sgx_thread_mutex_t *mutex)
{
    CHECK_PARAMETER(mutex);

    SPIN_LOCK(&mutex->m_lock);
    if (mutex->m_owner != SGX_THREAD_T_NULL
        || QUEUE_FIRST(&mutex->m_queue) != SGX_THREAD_T_NULL) {
        SPIN_UNLOCK(&mutex->m_lock);
        return EBUSY;
    }

    mutex->m_control = 0;
    mutex->m_refcount = 0;
    
    SPIN_UNLOCK(&mutex->m_lock);

    return 0;
}

int sgx_thread_mutex_lock(sgx_thread_mutex_t *mutex)
{
    CHECK_PARAMETER(mutex);

    sgx_thread_t self = (sgx_thread_t)get_thread_data();

    while (1) {
        SPIN_LOCK(&mutex->m_lock);

        if(mutex->m_control != SGX_THREAD_MUTEX_RECURSIVE
            && mutex->m_control != SGX_THREAD_MUTEX_NONRECURSIVE) {
            SPIN_UNLOCK(&mutex->m_lock);
            return EINVAL;
        }
        
        if (mutex->m_control == SGX_THREAD_MUTEX_RECURSIVE
            && mutex->m_owner == self) {
            mutex->m_refcount++;
            SPIN_UNLOCK(&mutex->m_lock);
            return 0;
        }

        if (mutex->m_owner == SGX_THREAD_T_NULL
            && (QUEUE_FIRST(&mutex->m_queue) == self
            || QUEUE_FIRST(&mutex->m_queue) == SGX_THREAD_T_NULL)) {

            if (QUEUE_FIRST(&mutex->m_queue) == self)
                QUEUE_REMOVE_HEAD(&mutex->m_queue);

            mutex->m_owner = self;
            mutex->m_refcount++;
            SPIN_UNLOCK(&mutex->m_lock);
            return 0;
        }

        sgx_thread_t waiter = SGX_THREAD_T_NULL;
        QUEUE_FOREACH(waiter, &mutex->m_queue) {
            if (waiter == self) break;
        }
        
        if (waiter == SGX_THREAD_T_NULL)
            QUEUE_INSERT_TAIL(&mutex->m_queue, self);

        SPIN_UNLOCK(&mutex->m_lock);

        int err = 0;
        sgx_thread_wait_untrusted_event_ocall(&err, TD2TCS(self));
    }

    /* NOTREACHED */
}

int sgx_thread_mutex_trylock(sgx_thread_mutex_t *mutex)
{
    CHECK_PARAMETER(mutex);

    sgx_thread_t self = (sgx_thread_t)get_thread_data();

    SPIN_LOCK(&mutex->m_lock);

    if(mutex->m_control != SGX_THREAD_MUTEX_RECURSIVE
        && mutex->m_control != SGX_THREAD_MUTEX_NONRECURSIVE) {
        SPIN_UNLOCK(&mutex->m_lock);
        return EINVAL;
    }
    
    if (mutex->m_control == SGX_THREAD_MUTEX_RECURSIVE
        && mutex->m_owner == self) {
        mutex->m_refcount++;
        SPIN_UNLOCK(&mutex->m_lock);
        return 0;
    }

    if (mutex->m_owner == SGX_THREAD_T_NULL
        && (QUEUE_FIRST(&mutex->m_queue) == self
        || QUEUE_FIRST(&mutex->m_queue) == SGX_THREAD_T_NULL)) {

        if (QUEUE_FIRST(&mutex->m_queue) == self)
            QUEUE_REMOVE_HEAD(&mutex->m_queue);

        mutex->m_owner = self;
        mutex->m_refcount++;

        SPIN_UNLOCK(&mutex->m_lock);
        return 0;
    }

    SPIN_UNLOCK(&mutex->m_lock);
    return EBUSY;
}

/* sgx_thread_mutex_unlock_lazy:
 *  check and modify mutex object, but not wake the pending thread up.
 */
int sgx_thread_mutex_unlock_lazy(sgx_thread_mutex_t *mutex, sgx_thread_t *pwaiter)
{
    CHECK_PARAMETER(mutex);

    sgx_thread_t self = (sgx_thread_t)get_thread_data();

    SPIN_LOCK(&mutex->m_lock);
    
    if(mutex->m_control != SGX_THREAD_MUTEX_RECURSIVE
        && mutex->m_control != SGX_THREAD_MUTEX_NONRECURSIVE) {
        SPIN_UNLOCK(&mutex->m_lock);
        return EINVAL;
    }

    /* if the mutux is not locked by anyone */
    if(mutex->m_owner == SGX_THREAD_T_NULL) {
        SPIN_UNLOCK(&mutex->m_lock);
        return EINVAL;
    }

    /* if the mutex is locked by another thread */
    if (mutex->m_owner != self) {
        SPIN_UNLOCK(&mutex->m_lock);
        return EPERM;
    }

    /* the mutex is locked by current thread */
    if (--mutex->m_refcount == 0)
        mutex->m_owner = SGX_THREAD_T_NULL;
    else {
        SPIN_UNLOCK(&mutex->m_lock);
        return 0;
    }

    /* Before releasing the mutex, get the first thread,
     * the thread should be waked up by the caller.
     */
    sgx_thread_t waiter = QUEUE_FIRST(&mutex->m_queue);

    SPIN_UNLOCK(&mutex->m_lock);
    if (pwaiter != NULL) *pwaiter = waiter;

    return 0;
}

/* sgx_thread_mutex_unlock:
 *  invoke sgx_thread_mutex_unlock_lazy, wake the pending thread up.
 */
int sgx_thread_mutex_unlock(sgx_thread_mutex_t *mutex)
{
    sgx_thread_t waiter = SGX_THREAD_T_NULL;

    int ret = sgx_thread_mutex_unlock_lazy(mutex, &waiter);
    if (ret != 0) return ret;

    if (waiter != SGX_THREAD_T_NULL) /* wake the waiter up*/
        sgx_thread_set_untrusted_event_ocall(&ret, TD2TCS(waiter));

    return 0;
}
