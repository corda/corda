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


#include "sgx_trts.h"
#include "sgx_spinlock.h"
#include "sgx_thread.h"

#include "internal/se_cdefs.h"
#include "internal/arch.h"
#include "internal/thread_data.h"
#include "trts_internal.h"


typedef struct _thread_data_t *pTD;

/* Queue operations */
#define QUEUE_FIRST(head)   ((sgx_thread_t)((head)->m_first))
#define QUEUE_NEXT(elm)     ((sgx_thread_t)(((pTD)(elm))->m_next))

#define QUEUE_INIT(head) do {               \
    (head)->m_first =                       \
        (head)->m_last = SGX_THREAD_T_NULL; \
} while (0)

#define QUEUE_FOREACH(var, head)            \
    for((var) = QUEUE_FIRST(head);          \
        (var) != SGX_THREAD_T_NULL;         \
        (var) = QUEUE_NEXT(var))

#define QUEUE_INSERT_TAIL(head, elm) do {       \
    ((pTD)(elm))->m_next = NULL;                \
    if ((head)->m_first != SGX_THREAD_T_NULL)   \
        ((pTD)((head)->m_last))->m_next = (pTD)(elm);       \
    else                                        \
        (head)->m_first = (elm);                \
    (head)->m_last = (elm);                     \
} while (0)

#define QUEUE_REMOVE_HEAD(head) do {                        \
    if (((head)->m_first =                                  \
        QUEUE_NEXT((head)->m_first)) == SGX_THREAD_T_NULL)  \
    (head)->m_last = SGX_THREAD_T_NULL;                     \
} while (0)

#define QUEUE_COUNT_ALL(var, head, total) do {      \
    QUEUE_FOREACH(var, head)                        \
        (total)++;                                  \
} while(0)                                          \

/* Spinlock */
#define SPIN_LOCK(_lck)     sgx_spin_lock((sgx_spinlock_t *)_lck);
#define SPIN_UNLOCK(_lck)   sgx_spin_unlock((sgx_spinlock_t *)_lck);

/* check enclave address */
#define CHECK_PARAMETER(addr) do {          \
    if (addr == NULL ||                     \
            !sgx_is_within_enclave((void *)addr, sizeof(*addr))) \
        return EINVAL;                      \
} while (0)

/* Generated OCALLs */
extern "C" sgx_status_t sgx_thread_wait_untrusted_event_ocall(int* retval, const void *self);
extern "C" sgx_status_t sgx_thread_set_untrusted_event_ocall(int* retval, const void *waiter);
extern "C" sgx_status_t sgx_thread_set_multiple_untrusted_events_ocall(int* retval, const void** waiters, size_t total);
extern "C" sgx_status_t sgx_thread_setwait_untrusted_events_ocall(int* retval, const void *waiter, const void *self);

extern "C" int sgx_thread_mutex_unlock_lazy(sgx_thread_mutex_t *mutex, sgx_thread_t *pwaiter);
