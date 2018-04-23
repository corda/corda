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

/* This file implement lock guard */

#ifndef SE_LOCK_HPP
#define SE_LOCK_HPP

#include "util.h"
#include "se_thread.h"
#include "uncopyable.h"

class Mutex: private Uncopyable
{
public:
    Mutex(){se_mutex_init(&m_mutex);}
    ~Mutex(){se_mutex_destroy(&m_mutex);}
    void lock(){se_mutex_lock(&m_mutex);}
    void unlock(){se_mutex_unlock(&m_mutex);}
private:
    se_mutex_t m_mutex;
};

class Cond: private Uncopyable
{
public:
    Cond(){se_mutex_init(&m_mutex); se_thread_cond_init(&m_cond);}
    ~Cond(){se_mutex_destroy(&m_mutex); se_thread_cond_destroy(&m_cond);}
    void lock(){se_mutex_lock(&m_mutex);}
    void unlock(){se_mutex_unlock(&m_mutex);}
    void wait(){se_thread_cond_wait(&m_cond, &m_mutex);}
    void signal(){se_thread_cond_signal(&m_cond);}
    void broadcast(){se_thread_cond_broadcast(&m_cond);}
private:
    se_mutex_t m_mutex;
    se_cond_t  m_cond;
};

class LockGuard: private Uncopyable
{
public:
    LockGuard(Mutex* mutex):m_mutex(mutex){m_mutex->lock();}
    ~LockGuard(){m_mutex->unlock();}
private:
    Mutex* m_mutex;
};

#endif
