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


#include "se_thread.h"
#include <stdlib.h>

static pthread_key_t g_tid_key;
static pthread_once_t g_key_once = PTHREAD_ONCE_INIT;

static void create_key()
{
    pthread_key_create(&g_tid_key, NULL);
}

se_thread_id_t get_thread_id()
{
    if(pthread_once(&g_key_once, create_key) != 0)
        abort();

    // If the key is invalid, pthread_getspecific will return NULL.
    // No need to check the pthread_key_create and pthread_setspecific return value.
    se_thread_id_t tid = (se_thread_id_t)(size_t)pthread_getspecific(g_tid_key);

    if( tid == 0)
    {
        tid = se_get_threadid();

        // pthread_setspecific() shall fail if insufficient memory exists to associate the value
        // with the key or the input key value is invalid.
        // Here we don't check the return value. Then if pthread_setspecific fails in one ecall,
        // the following ecall would call the syscall gettid to retrive the tid in one thread
        // instead of returning SGX_ERROR_OUT_OF_TCS.
        pthread_setspecific(g_tid_key, (void*)(size_t)tid);
    }
    return tid;
}
