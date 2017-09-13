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


#include "../Enclave.h"
#include "Enclave_t.h"

#include "sgx_thread.h"

static size_t global_counter = 0;
static sgx_thread_mutex_t global_mutex = SGX_THREAD_MUTEX_INITIALIZER;

#define BUFFER_SIZE 50

typedef struct {
    int buf[BUFFER_SIZE];
    int occupied;
    int nextin;
    int nextout;
    sgx_thread_mutex_t mutex;
    sgx_thread_cond_t more;
    sgx_thread_cond_t less;
} cond_buffer_t;

static cond_buffer_t buffer = {{0, 0, 0, 0, 0, 0}, 0, 0, 0,
    SGX_THREAD_MUTEX_INITIALIZER, SGX_THREAD_COND_INITIALIZER, SGX_THREAD_COND_INITIALIZER};

/*
 * ecall_increase_counter:
 *   Utilize thread APIs inside the enclave.
 */
size_t ecall_increase_counter(void)
{
    size_t ret = 0;
    for (int i = 0; i < LOOPS_PER_THREAD; i++) {
        sgx_thread_mutex_lock(&global_mutex);
        /* mutually exclusive adding */
        size_t tmp = global_counter;
        global_counter = ++tmp;
        if (4*LOOPS_PER_THREAD == global_counter)
            ret = global_counter;
        sgx_thread_mutex_unlock(&global_mutex);
    }
    return ret;
}

void ecall_producer(void)
{
    for (int i = 0; i < 4*LOOPS_PER_THREAD; i++) {
        cond_buffer_t *b = &buffer;
        sgx_thread_mutex_lock(&b->mutex);
        while (b->occupied >= BUFFER_SIZE)
            sgx_thread_cond_wait(&b->less, &b->mutex);
        b->buf[b->nextin] = b->nextin;
        b->nextin++;
        b->nextin %= BUFFER_SIZE;
        b->occupied++;
        sgx_thread_cond_signal(&b->more);
        sgx_thread_mutex_unlock(&b->mutex);
    }
}

void ecall_consumer(void)
{
    for (int i = 0; i < LOOPS_PER_THREAD; i++) {
        cond_buffer_t *b = &buffer;
        sgx_thread_mutex_lock(&b->mutex);
        while(b->occupied <= 0)
            sgx_thread_cond_wait(&b->more, &b->mutex);
        b->buf[b->nextout++] = 0;
        b->nextout %= BUFFER_SIZE;
        b->occupied--;
        sgx_thread_cond_signal(&b->less);
        sgx_thread_mutex_unlock(&b->mutex);
    }
}
