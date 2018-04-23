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

#ifndef _SGX_THREAD_H_
#define _SGX_THREAD_H_

#include <stdint.h>
#include <stddef.h>
#include "sgx_defs.h"

typedef uintptr_t sgx_thread_t;

typedef struct _sgx_thread_queue_t
{
    sgx_thread_t        m_first;  /* first element */
    sgx_thread_t        m_last;   /* last element */
} sgx_thread_queue_t;

/* Mutex */
typedef struct _sgx_thread_mutex_t
{
    size_t              m_refcount;
    uint32_t            m_control;
    volatile uint32_t   m_lock;   /* use sgx_spinlock_t */
    sgx_thread_t        m_owner;
    sgx_thread_queue_t  m_queue;
} sgx_thread_mutex_t;

#define SGX_THREAD_T_NULL   ((sgx_thread_t)(NULL))

#define SGX_THREAD_MUTEX_NONRECURSIVE   0x01
#define SGX_THREAD_MUTEX_RECURSIVE      0x02
#define SGX_THREAD_NONRECURSIVE_MUTEX_INITIALIZER \
            {0, SGX_THREAD_MUTEX_NONRECURSIVE, 0, SGX_THREAD_T_NULL, {SGX_THREAD_T_NULL, SGX_THREAD_T_NULL}}
#define SGX_THREAD_RECURSIVE_MUTEX_INITIALIZER \
            {0, SGX_THREAD_MUTEX_RECURSIVE, 0, SGX_THREAD_T_NULL, {SGX_THREAD_T_NULL, SGX_THREAD_T_NULL}}
#define SGX_THREAD_MUTEX_INITIALIZER \
            SGX_THREAD_NONRECURSIVE_MUTEX_INITIALIZER

typedef struct _sgx_thread_mutex_attr_t
{
    unsigned char       m_dummy;  /* for C syntax check */
} sgx_thread_mutexattr_t;

/* Condition Variable */
typedef struct _sgx_thread_cond_t
{
    volatile uint32_t   m_lock;   /* use sgx_spinlock_t */
    sgx_thread_queue_t  m_queue;
} sgx_thread_cond_t;

#define SGX_THREAD_COND_INITIALIZER  {0, {SGX_THREAD_T_NULL, SGX_THREAD_T_NULL}}

typedef struct _sgx_thread_cond_attr_t
{
    unsigned char       m_dummy;  /* for C syntax check */
} sgx_thread_condattr_t;

#ifdef __cplusplus
extern "C" {
#endif

/* Mutex */
int SGXAPI sgx_thread_mutex_init(sgx_thread_mutex_t *mutex, const sgx_thread_mutexattr_t *unused);
int SGXAPI sgx_thread_mutex_destroy(sgx_thread_mutex_t *mutex);

int SGXAPI sgx_thread_mutex_lock(sgx_thread_mutex_t *mutex);
int SGXAPI sgx_thread_mutex_trylock(sgx_thread_mutex_t *mutex);
int SGXAPI sgx_thread_mutex_unlock(sgx_thread_mutex_t *mutex);

/* Condition Variable */
int SGXAPI sgx_thread_cond_init(sgx_thread_cond_t *cond, const sgx_thread_condattr_t *unused);
int SGXAPI sgx_thread_cond_destroy(sgx_thread_cond_t *cond);

int SGXAPI sgx_thread_cond_wait(sgx_thread_cond_t *cond, sgx_thread_mutex_t *mutex);
int SGXAPI sgx_thread_cond_signal(sgx_thread_cond_t *cond);
int SGXAPI sgx_thread_cond_broadcast(sgx_thread_cond_t *cond);

sgx_thread_t SGXAPI sgx_thread_self(void);
int sgx_thread_equal(sgx_thread_t a, sgx_thread_t b);

#ifdef __cplusplus
}
#endif

#endif /* _SGX_THREAD_H_ */
