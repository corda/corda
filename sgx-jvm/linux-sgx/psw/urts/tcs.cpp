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


#include "tcs.h"
#include "se_trace.h"
#include "sgx_error.h"
#include "se_memory.h"
#include "se_thread.h"
#include <assert.h>
#include "routine.h"
#include "enclave_creator.h"
#include "rts.h"
#include "enclave.h"

extern se_thread_id_t get_thread_id();
int do_ecall(const int fn, const void *ocall_table, const void *ms, CTrustThread *trust_thread);



CTrustThread::CTrustThread(tcs_t *tcs, CEnclave* enclave)
    : m_tcs(tcs)
    , m_enclave(enclave)
    , m_reference(0)
    , m_event(NULL)
{
    memset(&m_tcs_info, 0, sizeof(debug_tcs_info_t));
    m_tcs_info.TCS_address = reinterpret_cast<void*>(tcs);
    m_tcs_info.ocall_frame = 0;
    m_tcs_info.thread_id = 0;
}

CTrustThread::~CTrustThread()
{
    se_event_destroy(m_event);
    m_event = NULL;
}

se_handle_t CTrustThread::get_event()
{
    if (m_event == NULL)
        m_event = se_event_init();

    return m_event;
}

void CTrustThread::push_ocall_frame(ocall_frame_t* frame_point)
{
    frame_point->index = this->get_reference();
    frame_point->pre_last_frame = m_tcs_info.ocall_frame;
    m_tcs_info.ocall_frame = reinterpret_cast<uintptr_t>(frame_point);
    m_tcs_info.thread_id = get_thread_id();
}

void CTrustThread::pop_ocall_frame()
{
    ocall_frame_t* last_ocall_frame = reinterpret_cast<ocall_frame_t*>(m_tcs_info.ocall_frame);
    if (last_ocall_frame)
    {
        m_tcs_info.ocall_frame = last_ocall_frame->pre_last_frame;
    }
}


CTrustThreadPool::CTrustThreadPool(uint32_t tcs_min_pool)
{
    m_thread_list = NULL;
    m_utility_thread = NULL;
    m_tcs_min_pool = tcs_min_pool;
    m_need_to_wait_for_new_thread = false;
}

CTrustThreadPool::~CTrustThreadPool()
{
    LockGuard lock(&m_thread_mutex);
    //destroy free tcs list
    for(vector<CTrustThread *>::iterator it=m_free_thread_vector.begin(); it!=m_free_thread_vector.end(); it++)
    {
        delete *it;
    }
    m_free_thread_vector.clear();
    //destroy unallocated tcs list
    for(vector<CTrustThread *>::iterator it=m_unallocated_threads.begin(); it!=m_unallocated_threads.end(); it++)
    {
        delete *it;
    }
    m_unallocated_threads.clear();

    //destroy thread cache
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list, *tmp = NULL;
    while (it != NULL)
    {
        delete it->value;
        tmp = it;
        it = it->next;
        delete tmp;
    }
    m_thread_list = NULL;

    if (m_utility_thread)
    {
        delete m_utility_thread;
        m_utility_thread = NULL;
    }

}

void get_thread_set(vector<se_thread_id_t> &thread_vector);
inline int CTrustThreadPool::find_thread(vector<se_thread_id_t> &thread_vector, se_thread_id_t thread_id)
{
    for(vector<se_thread_id_t>::iterator it=thread_vector.begin(); it!=thread_vector.end(); it++)
        if(*it == thread_id)
            return TRUE;
    return FALSE;
}

inline CTrustThread * CTrustThreadPool::get_free_thread()
{
    LockGuard lock(&m_free_thread_mutex);
    if(true == m_free_thread_vector.empty())
    {
        return NULL;
    }

    //if there is free tcs, remove it from free list
    CTrustThread *thread_node = m_free_thread_vector.back();
    m_free_thread_vector.pop_back();

    return thread_node;
}

//This tcs policy is bind tcs with one thread.
int CTrustThreadPool::bind_thread(const se_thread_id_t thread_id,  CTrustThread * const trust_thread)
{
    if (m_thread_list == NULL) {
        m_thread_list = new Node<se_thread_id_t, CTrustThread*>(thread_id, trust_thread);
    } else {
        Node<se_thread_id_t, CTrustThread*>* it = new Node<se_thread_id_t, CTrustThread*>(thread_id, trust_thread);
        if (m_thread_list->InsertNext(it) == false) {
            delete it;
            SE_TRACE(SE_TRACE_WARNING, "trust thread %x is already added to the list\n", trust_thread);
            return FALSE;
        }
    }
    return TRUE;
}

CTrustThread * CTrustThreadPool::get_bound_thread(const se_thread_id_t thread_id)
{
    CTrustThread *trust_thread = nullptr;

    if (m_thread_list)
    {
        auto it = m_thread_list->Find(thread_id);
        if (it)
            trust_thread = it->value;
    }

    return trust_thread;
}

CTrustThread * CTrustThreadPool::add_thread(tcs_t * const tcs, CEnclave * const enclave, bool is_unallocated)
{
    CTrustThread *trust_thread = new CTrustThread(tcs, enclave);
    LockGuard lock(&m_thread_mutex);
    //add tcs to free list
    if(!is_unallocated)
    {
        if (g_enclave_creator->is_EDMM_supported(enclave->get_enclave_id()) && !m_utility_thread && (enclave->get_dynamic_tcs_list_size() != 0))
            m_utility_thread = trust_thread;
        else
            m_free_thread_vector.push_back(trust_thread);
    }
    else
    {
        m_unallocated_threads.push_back(trust_thread);
    }

    return trust_thread;
}

CTrustThread *CTrustThreadPool::get_bound_thread(const tcs_t *tcs)
{
    //Since now this function will be call outside, we need get lock to protect map
    LockGuard lock(&m_thread_mutex);

    CTrustThread *trust_thread = NULL;
    if (m_thread_list == NULL)
        return NULL;
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list;
    while (it != NULL) {
        trust_thread = it->value;
        if(trust_thread->get_tcs() == tcs) {
            return trust_thread;
        }
        it = it->next;
    }
    return NULL;
}

std::vector<CTrustThread *> CTrustThreadPool::get_thread_list()
{
    LockGuard lock(&m_thread_mutex);

    vector<CTrustThread *> threads;

    for(vector<CTrustThread *>::iterator it = m_free_thread_vector.begin(); it != m_free_thread_vector.end(); it++)
    {
        threads.push_back(*it);
    }

    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list;
    while (it != NULL) {
        threads.push_back(it->value);
        it = it->next;
    }

    return threads;
}

void CTrustThreadPool::reset()
{
    //get lock at the begin of list walk.
    LockGuard lock(&m_thread_mutex);

    //walk through thread cache to free every element;
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list, *tmp = NULL;

    while(it != NULL)
    {
        tmp = it;
        it = it->next;
        CTrustThread *trust_thread = tmp->value;
        //remove from thread cache
        delete tmp;
        trust_thread->reset_ref();
        add_to_free_thread_vector(trust_thread);
    }
    m_thread_list = NULL;

    return;
}

void CTrustThreadPool::wake_threads()
{
    LockGuard lock(&m_thread_mutex);
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list;

    while (it != NULL) {
        CTrustThread *thread = it->value;
        se_handle_t event = thread->get_event();
        se_event_wake(event);
        it = it->next;
    }
}

CTrustThread * CTrustThreadPool::_acquire_thread()
{
    //try to get tcs from thread cache
    se_thread_id_t thread_id = get_thread_id();
    CTrustThread *trust_thread = get_bound_thread(thread_id);
    if(NULL != trust_thread  && m_utility_thread != trust_thread)
    {
        return trust_thread;
    }
    //try get tcs from free list;
    trust_thread = get_free_thread();
    //if there is no free tcs, collect useless tcs.
    if(NULL == trust_thread)
    {
        if(!garbage_collect())
            return NULL;
        //get tcs from free list again.
        trust_thread = get_free_thread();
        assert(NULL != trust_thread);
    }
    //we have got a free tcs. add the tcs to thread cache
    bind_thread(thread_id, trust_thread);
    return trust_thread;
}

CTrustThread * CTrustThreadPool::acquire_thread(bool is_initialize_ecall)
{
    LockGuard lock(&m_thread_mutex);
    CTrustThread *trust_thread = NULL;

    if(is_initialize_ecall == true)
    {
        if (m_utility_thread)
        {
            trust_thread = m_utility_thread;
            assert(trust_thread != NULL);
        }
        else
        {
            trust_thread = _acquire_thread();
        }
    }
    else
    {
        trust_thread = _acquire_thread();
        // for edmm feature, we don't support simulation mode yet
        // m_utility_thread will be NULL in simulation mode
        if(NULL == trust_thread && NULL != m_utility_thread)
        {
            m_need_to_wait_for_new_thread_cond.lock();
            m_utility_thread->get_enclave()->fill_tcs_mini_pool_fn();
            m_need_to_wait_for_new_thread = true;
            while(m_need_to_wait_for_new_thread != false)
            {
                m_need_to_wait_for_new_thread_cond.wait();
            }
            m_need_to_wait_for_new_thread_cond.unlock();
            trust_thread = _acquire_thread();
        }
    }
    
    if(trust_thread)
    {
        trust_thread->increase_ref();
    }

    if(is_initialize_ecall != true &&
       need_to_new_thread() == true)
    {     
        m_utility_thread->get_enclave()->fill_tcs_mini_pool_fn();
    }
    return trust_thread;
}

//Do nothing for bind mode, the tcs is always bound to a thread.
void CTrustThreadPool::release_thread(CTrustThread * const trust_thread)
{
    LockGuard lock(&m_thread_mutex);
    trust_thread->decrease_ref();
    return;
}


bool CTrustThreadPool::is_dynamic_thread_exist()
{
    if (m_unallocated_threads.empty())
    {
        return false;
    }
    else
    {
        return true;
    }
}

bool CTrustThreadPool::need_to_new_thread()
{
    LockGuard lock(&m_free_thread_mutex);
    if (m_unallocated_threads.empty())
    {
        return false;
    }

    if(m_tcs_min_pool == 0 && m_free_thread_vector.size() > m_tcs_min_pool)
    {
        return false;
    }
    
    if(m_tcs_min_pool != 0 && m_free_thread_vector.size() >= m_tcs_min_pool)
    {
        return false;
    }

    return true;
}


static int make_tcs(size_t tcs)
{
    return g_enclave_creator->mktcs(tcs);
}

struct ms_str
{
    void * ms;
};

#define fastcall __attribute__((regparm(3),noinline,visibility("default")))
//this function is used to notify GDB scripts
//GDB is supposed to have a breakpoint on urts_add_tcs to receive debug interupt
//once the breakpoint has been hit, GDB extracts the address of tcs and sets DBGOPTIN for the tcs
extern "C" void fastcall urts_add_tcs(tcs_t * const tcs)
{
    UNUSED(tcs);
    SE_TRACE(SE_TRACE_WARNING, "urts_add_tcs %x\n", tcs);
}

sgx_status_t CTrustThreadPool::new_thread()
{
    sgx_status_t ret = SGX_ERROR_UNEXPECTED;
    if(!m_utility_thread)
    {
        return ret;
    }
    if (m_unallocated_threads.empty())
    {
        return SGX_SUCCESS;
    }

    size_t octbl_buf[ROUND_TO(sizeof(sgx_ocall_table_t) + sizeof(void*), sizeof(size_t)) / sizeof(size_t)];
    sgx_ocall_table_t *octbl = reinterpret_cast<sgx_ocall_table_t*>(octbl_buf);
    octbl->count = 1;
    void **ocalls = octbl->ocall;
    *ocalls = reinterpret_cast<void*>(make_tcs);
    CTrustThread *trust_thread = m_unallocated_threads.back();
    tcs_t *tcsp = trust_thread->get_tcs();
    struct ms_str ms1;
    ms1.ms = tcsp;
    ret = (sgx_status_t)do_ecall(ECMD_MKTCS, octbl, &ms1, m_utility_thread);
    if (SGX_SUCCESS == ret )
    {    
        //add tcs to debug tcs info list
        trust_thread->get_enclave()->add_thread(trust_thread);
        add_to_free_thread_vector(trust_thread);
        m_unallocated_threads.pop_back();

        urts_add_tcs(tcsp);
    }
    
    return ret;
}


void CTrustThreadPool::add_to_free_thread_vector(CTrustThread* it)
{
    LockGuard lock(&m_free_thread_mutex);
    m_free_thread_vector.push_back(it);
}

sgx_status_t CTrustThreadPool::fill_tcs_mini_pool()
{
    sgx_status_t ret = SGX_SUCCESS;
    bool stop = false;

    while(stop != true)
    {
        if(need_to_new_thread() == true)
        {
            ret = new_thread();
            if(ret != SGX_SUCCESS)
            {
                stop= true;
            }
        }
        else
        {
            stop = true;
        }
        
        m_need_to_wait_for_new_thread_cond.lock();
        if(m_need_to_wait_for_new_thread == true)
        {
            m_need_to_wait_for_new_thread = false;
            m_need_to_wait_for_new_thread_cond.signal();
        }
        m_need_to_wait_for_new_thread_cond.unlock();
    }
    
    return ret;
}


//The return value stand for the number of free trust thread.
int CThreadPoolBindMode::garbage_collect()
{
    int nr_free = 0;

    //if free list is NULL, recycle tcs.
    //get thread id set of current process
    vector<se_thread_id_t> thread_vector;
    get_thread_set(thread_vector);
    //walk through thread cache to see if there is any thread that has exited
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list, *pre = NULL, *tmp = NULL;

    while(it != NULL)
    {
        se_thread_id_t thread_id = it->key;
        //if the thread has exited
        if(FALSE == find_thread(thread_vector, thread_id))
        {
            //if the reference is not 0, there must be some wrong termination, so we can't recycle such trust thread.
            //return to free_tcs list
            if(0 == it->value->get_reference())
            {
                add_to_free_thread_vector(it->value);
                nr_free++;
            }
            else
            {
                //the list only record the pointer of trust thread, so we can delete it first and then erase from map.
                delete it->value;
            }
            tmp = it;
            it = it->next;
            if (tmp == m_thread_list)
                m_thread_list = it;
            if (pre != NULL)
                pre->next = it;
            //remove from thread cache
            delete tmp;
        }
        else
        {
            pre = it;
            it = it->next;
        }
    }

    return nr_free;
}

int CThreadPoolUnBindMode::garbage_collect()
{
    int nr_free = 0;

    //walk through to free unused trust thread
    Node<se_thread_id_t, CTrustThread*>* it = m_thread_list, *pre = NULL, *tmp = NULL;
    while(it != NULL)
    {
        //if the reference is 0, then the trust thread is not in use, so return to free_tcs list
        if(0 == it->value->get_reference())
        {
            add_to_free_thread_vector(it->value);
            nr_free++;

            tmp = it;
            it = it->next;
            if (tmp == m_thread_list)
                m_thread_list = it;
            if (pre != NULL)
                pre->next = it;
            //remove from thread cache
            delete tmp;
        }
        else
        {
            pre = it;
            it = it->next;
        }
    }

    return nr_free;
}
