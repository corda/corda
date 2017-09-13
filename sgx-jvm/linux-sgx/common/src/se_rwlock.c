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


#include "se_rwlock.h"
#include "internal/util.h"


#include <stdlib.h>
void se_wtlock(se_prwlock_t lock)
{
    int ret = pthread_rwlock_wrlock(lock);
    if(0 != ret)
        abort();
}

void se_wtunlock(se_prwlock_t lock)
{
    int ret = pthread_rwlock_unlock(lock);
    if(0 != ret)
        abort();
}

int se_try_rdlock(se_prwlock_t lock)
{
    return (0 == pthread_rwlock_tryrdlock(lock));
}

void se_rdlock(se_prwlock_t lock)
{
    int ret = pthread_rwlock_rdlock(lock);
    if(0 != ret)
        abort();
}

void se_rdunlock(se_prwlock_t lock)
{
    int ret = pthread_rwlock_unlock(lock);
    if(0 != ret)
        abort();
}

void se_init_rwlock(se_prwlock_t lock)
{
    /* use the default attribute. */
    int ret = pthread_rwlock_init(lock, NULL);
    if(0 != ret)
        abort();
}

void se_fini_rwlock(se_prwlock_t lock)
{
    int ret = pthread_rwlock_destroy(lock);
    if(0 != ret)
        abort();
}

