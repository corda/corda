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


#ifndef _TCS_H_
#define _TCS_H_
#include "arch.h"
#include "se_wrapper.h"
#include "util.h"
#include "sgx_error.h"
#include "sgx_eid.h"
#include "se_debugger_lib.h"
#include "se_lock.hpp"
#include <vector>
#include "node.h"

using namespace std;

typedef int (*bridge_fn_t)(sgx_enclave_id_t enclave_id, const void*);

class CEnclave;

class CTrustThread: private Uncopyable
{
public:
    CTrustThread(tcs_t *tcs, CEnclave* enclave);
    ~CTrustThread();
    int get_reference() { return m_reference; }
    void increase_ref() { m_reference++; }
    void decrease_ref() { m_reference--; }
    tcs_t *get_tcs()    { return m_tcs; }
    CEnclave *get_enclave() { return m_enclave; }
    se_handle_t get_event();
    void reset_ref() { m_reference = 0; }
    debug_tcs_info_t* get_debug_info(){return &m_tcs_info;}
    void push_ocall_frame(ocall_frame_t* frame_point);
    void pop_ocall_frame();
private:
    tcs_t               *m_tcs;
    CEnclave            *m_enclave;
    int                 m_reference;  //it will increase by 1 before ecall, and decrease after ecall.
    se_handle_t         m_event;
    debug_tcs_info_t    m_tcs_info;
};

class CTrustThreadPool: private Uncopyable
{
public:
    CTrustThreadPool();
    virtual ~CTrustThreadPool();
    CTrustThread * acquire_thread();
    void release_thread(CTrustThread * const trust_thread);
    CTrustThread *add_thread(tcs_t * const tcs, CEnclave * const enclave);
    CTrustThread *get_bound_thread(const tcs_t *tcs);
    void reset();
    void wake_threads();
protected:
    virtual int garbage_collect() = 0;
    inline int find_thread(vector<se_thread_id_t> &thread_vector, se_thread_id_t thread_id);
    inline CTrustThread * get_free_thread();
    int bind_thread(const se_thread_id_t thread_id, CTrustThread * const trust_thread);
    CTrustThread * get_bound_thread(const se_thread_id_t thread_id);

    vector<CTrustThread *>                  m_free_thread_vector;
    Node<se_thread_id_t, CTrustThread *>    *m_thread_list;
    Mutex                                   m_thread_mutex; //protect both thread_cache list and fress tcs list. The mutex is recursive.
                                                            //Thread can operate the two list when it get the mutex
private:
    CTrustThread * _acquire_thread();
};

class CThreadPoolBindMode : public CTrustThreadPool
{
public:
    CThreadPoolBindMode():CTrustThreadPool(){}
private:
    virtual int garbage_collect();
};

class CThreadPoolUnBindMode : public CTrustThreadPool
{
public:
    CThreadPoolUnBindMode():CTrustThreadPool(){}
private:
    virtual int garbage_collect();
};
#endif
