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



#ifndef _SE_THREAD_H_
#define _SE_THREAD_H_


#ifndef _GNU_SOURCE
#define _GNU_SOURCE /* for PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP */
#endif
#include <string.h>
#include <unistd.h>
#include <sys/syscall.h>
#include <pthread.h>
typedef pthread_mutex_t se_mutex_t;
typedef pthread_cond_t se_cond_t;
typedef pid_t se_thread_id_t;
typedef pthread_key_t se_tls_index_t;

#ifdef __cplusplus
extern "C" {
#endif
/*
@mutex:	A pointer to the critical section object.
@return value:	If the function succeeds, the return value is nonzero.If the function fails, the return value is zero.
*/
void se_mutex_init(se_mutex_t* mutex);
int se_mutex_lock(se_mutex_t* mutex);
int se_mutex_unlock(se_mutex_t* mutex);
int se_mutex_destroy(se_mutex_t* mutex);

void se_thread_cond_init(se_cond_t* cond);
int se_thread_cond_wait(se_cond_t *cond, se_mutex_t *mutex);
int se_thread_cond_signal(se_cond_t *cond);
int se_thread_cond_broadcast(se_cond_t *cond);
int se_thread_cond_destroy(se_cond_t* cond);

unsigned int se_get_threadid(void);

/* tls functions */
int se_tls_alloc(se_tls_index_t *tls_index);
int se_tls_free(se_tls_index_t tls_index);
void * se_tls_get_value(se_tls_index_t tls_index);
int se_tls_set_value(se_tls_index_t tls_index, void *tls_value);

/* se_thread_handle_t se_create_thread(size_t stack_size, thread_start_routine_t start_routine, void *param, se_thread_t* thread); */

#ifdef __cplusplus
}
#endif

#endif
