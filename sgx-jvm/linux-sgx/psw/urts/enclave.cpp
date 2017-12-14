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


#include "enclave.h"
#include "util.h"
#include "se_detect.h"
#include "enclave_creator.h"
#include "sgx_error.h"
#include "se_error_internal.h"
#include "debugger_support.h"
#include "se_memory.h"
#include "urts_trim.h"
#include "urts_emodpr.h"
#include <assert.h>
#include "rts.h"


using namespace std;

int do_ecall(const int fn, const void *ocall_table, const void *ms, CTrustThread *trust_thread);
int do_ocall(const bridge_fn_t bridge, void *ms);

CEnclave::CEnclave(CLoader &ldr)
    : m_loader(ldr)
    , m_enclave_id(0)
    , m_start_addr(NULL)
    , m_size(0)
    , m_power_event_flag(0)
    , m_ref(0)
    , m_zombie(false)
    , m_thread_pool(NULL)
    , m_dbg_flag(false)
    , m_destroyed(false)
    , m_version(0)
    , m_ocall_table(NULL)
    , m_pthread_is_valid(false)
    , m_new_thread_event(NULL)
{
    memset(&m_enclave_info, 0, sizeof(debug_enclave_info_t));
    se_init_rwlock(&m_rwlock);
}


sgx_status_t CEnclave::initialize(const se_file_t& file, const sgx_enclave_id_t enclave_id, void * const start_addr, const uint64_t enclave_size, 
                                        const uint32_t tcs_policy, const uint32_t enclave_version, const uint32_t tcs_min_pool)
{
    uint32_t name_len = file.name_len;
    if (file.unicode)
        name_len *= (uint32_t)sizeof(wchar_t);

    const int buf_len = name_len + 4; //+4, because we need copy the charactor of string end ('\0').;

    m_enclave_info.lpFileName = calloc(1, buf_len);
    if (m_enclave_info.lpFileName == NULL)
        return SGX_ERROR_OUT_OF_MEMORY;

    memcpy_s(m_enclave_info.lpFileName, name_len, file.name, name_len);
    m_enclave_info.unicode = file.unicode?0:1;
    m_enclave_info.file_name_size = name_len;

    m_enclave_info.struct_version = DEBUG_INFO_STRUCT_VERSION;


    m_enclave_id = enclave_id;
    m_start_addr = start_addr;
    m_size = enclave_size;
    m_version = enclave_version;

    m_new_thread_event = se_event_init();
    if(m_new_thread_event == NULL)
    {
        free(m_enclave_info.lpFileName);
        m_enclave_info.lpFileName = NULL;
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    if(TCS_POLICY_BIND == tcs_policy)
    {
        m_thread_pool = new CThreadPoolBindMode(tcs_min_pool);
    }
    else if(TCS_POLICY_UNBIND == tcs_policy)
    {
        //we also set it as bind mode.
        m_thread_pool = new CThreadPoolUnBindMode(tcs_min_pool);
    }
    else
    {
        SE_TRACE(SE_TRACE_WARNING, "BUG: unknown tcs policy\n");
        //Should NOT run here, because we have validate the metadata before.
        free(m_enclave_info.lpFileName);
        m_enclave_info.lpFileName = NULL;
        return SGX_ERROR_INVALID_PARAMETER;
    }
    
    return SGX_SUCCESS;
}

CEnclave::~CEnclave()
{
    if (m_thread_pool)
    {
        delete m_thread_pool;
        m_thread_pool = NULL;
    }

    m_ocall_table = NULL;

    destory_debug_info(&m_enclave_info);
    se_fini_rwlock(&m_rwlock);
        
    se_event_destroy(m_new_thread_event);
    m_new_thread_event = NULL;
}

void * CEnclave::get_symbol_address(const char * const symbol)
{
    return m_loader.get_symbol_address(symbol);
}

sgx_enclave_id_t CEnclave::get_enclave_id()
{
    return m_enclave_id;
}

uint32_t CEnclave::get_enclave_version()
{
    return m_version;
}

size_t CEnclave::get_dynamic_tcs_list_size()
{
    std::vector<std::pair<tcs_t *, bool>> tcs_list = m_loader.get_tcs_list();
    size_t count = 0;
    for (size_t idx = 0; idx < tcs_list.size(); ++idx)
    {
        if(tcs_list[idx].second == true)
        {
            count++;
        }
    }
    return count;
}

sgx_status_t CEnclave::error_trts2urts(unsigned int trts_error)
{
    if(trts_error == (unsigned int)SE_ERROR_READ_LOCK_FAIL)
    {
        return SGX_ERROR_ENCLAVE_LOST;
    }

    //tRTS may directly return the external error code, so we don't need transfer it.
    if(EXTERNAL_ERROR != (trts_error >> MAIN_MOD_SHIFT))
    {
        SE_TRACE(SE_TRACE_WARNING, "trts return error %x, it should be urts/trts bug\n", trts_error);
        return SGX_ERROR_UNEXPECTED;
    }

    return (sgx_status_t)trts_error;
}

sgx_status_t CEnclave::ecall(const int proc, const void *ocall_table, void *ms)
{
    if(se_try_rdlock(&m_rwlock))
    {
        //Maybe the enclave has been destroyed after acquire/release m_rwlock. See CEnclave::destroy()
        if(m_destroyed)
        {
            se_rdunlock(&m_rwlock);
            return SGX_ERROR_ENCLAVE_LOST;
        }

        //do sgx_ecall
        CTrustThread *trust_thread = get_tcs(proc == ECMD_INIT_ENCLAVE);
        unsigned ret = SGX_ERROR_OUT_OF_TCS;

        if(NULL != trust_thread)
        {
            if (NULL == m_ocall_table)
            {
                m_ocall_table = (sgx_ocall_table_t *)ocall_table;
            }

            if (proc == ECMD_UNINIT_ENCLAVE)
            {
                if(m_pthread_is_valid == true)
                {
                    m_pthread_is_valid = false;
                    se_event_wake(m_new_thread_event);
                    pthread_join(m_pthread_tid, NULL);
                }
                ocall_table = m_ocall_table;

                std::vector<CTrustThread *> threads = m_thread_pool->get_thread_list();
                for (unsigned idx = 0; idx < threads.size(); ++idx)
                {
                    if (trust_thread->get_tcs() == threads[idx]->get_tcs())
                    {
                        continue;
                    }

                    uint64_t start = (uint64_t)(threads[idx]->get_tcs());
                    uint64_t end = start + (1 << SE_PAGE_SHIFT);

                    if (get_enclave_creator()->is_EDMM_supported(CEnclave::get_enclave_id()))
                    {
                        if (SGX_SUCCESS != (ret = get_enclave_creator()->trim_range(start, end)))
                        {
                            se_rdunlock(&m_rwlock);
                            return (sgx_status_t)ret;
                        }
                    }
                }
            }

            ret = do_ecall(proc, ocall_table, ms, trust_thread);
        }
        put_tcs(trust_thread);

        //release the read/write lock, the only exception is enclave already be removed in ocall
        if(AbnormalTermination() || ret != SE_ERROR_READ_LOCK_FAIL)
        {
            se_rdunlock(&m_rwlock);
        }
        return error_trts2urts(ret);
    }
    else
    {
        return SGX_ERROR_ENCLAVE_LOST;
    }
}

int CEnclave::ocall(const unsigned int proc, const sgx_ocall_table_t *ocall_table, void *ms)
{
    int error = SGX_ERROR_UNEXPECTED;

    if ((int)proc == EDMM_TRIM || (int)proc == EDMM_TRIM_COMMIT || (int)proc == EDMM_MODPR)
    {
        se_rdunlock(&m_rwlock);
        if((int)proc == EDMM_TRIM)
            error = ocall_trim_range(ms);
        else if ((int)proc == EDMM_TRIM_COMMIT)
            error = ocall_trim_accept(ms);
        else if ((int)proc == EDMM_MODPR)
            error = ocall_emodpr(ms);
    }
    else 
    {
        //validate the proc is within ocall_table;
        if(NULL == ocall_table ||
                (proc >= ocall_table->count))
        {
            return SGX_ERROR_INVALID_FUNCTION;
        }

        se_rdunlock(&m_rwlock);
        bridge_fn_t bridge = reinterpret_cast<bridge_fn_t>(ocall_table->ocall[proc]);
        error = do_ocall(bridge, ms);
    }

    if (!se_try_rdlock(&m_rwlock))
    {
        //Probablly the enclave has been destroyed, so we can't get the read lock.
        error = SE_ERROR_READ_LOCK_FAIL;
    }
    //We have m_destroyed to determinate if the enclave has been destroyed.
    else if(m_destroyed)
    {
        //Enclave has been destroyed, emulate that we fail to get read lock.
        se_rdunlock(&m_rwlock);
        error = SE_ERROR_READ_LOCK_FAIL;
    }
    return error;
}

const debug_enclave_info_t* CEnclave::get_debug_info()
{
    return &m_enclave_info;
}



CTrustThread * CEnclave::get_tcs(bool is_initialize_ecall)
{
    CTrustThread *trust_thread = m_thread_pool->acquire_thread(is_initialize_ecall);

    return trust_thread;
}

void *fill_tcs_mini_pool_func(void *args)  
{  
     CEnclave *it = (CEnclave*)(args);
     if(it != NULL)
     {
        it->fill_tcs_mini_pool(); 
     }
     return NULL;
} 

sgx_status_t CEnclave::fill_tcs_mini_pool_fn()
{
    pthread_t tid;
    if(m_pthread_is_valid == false)
    {
       m_pthread_is_valid = true; 
       int ret = pthread_create(&tid, NULL, fill_tcs_mini_pool_func, (void *)(this));  
        if(ret != 0)
        {
            m_pthread_is_valid = false; 
            return SGX_ERROR_UNEXPECTED;
        }
        m_pthread_tid = tid;
    }
    else if(m_pthread_is_valid == true)
    {
        if(se_event_wake(m_new_thread_event) != SE_MUTEX_SUCCESS)
        {
            return SGX_ERROR_UNEXPECTED;
        }
    }
    

    return SGX_SUCCESS;
}

sgx_status_t CEnclave::fill_tcs_mini_pool()
{
    do
    {
        if(se_try_rdlock(&m_rwlock))
        {
            //Maybe the enclave has been destroyed after acquire/release m_rwlock. See CEnclave::destroy()
            if(m_destroyed)
            {
                se_rdunlock(&m_rwlock);
                return SGX_ERROR_ENCLAVE_LOST;
            }
            if(m_pthread_is_valid == false)
            {
                se_rdunlock(&m_rwlock);
                return SGX_SUCCESS;
            }
            m_thread_pool->fill_tcs_mini_pool();
            se_rdunlock(&m_rwlock);
        }
        else
        {
            return SGX_ERROR_ENCLAVE_LOST;
        } 
   }while(se_event_wait(m_new_thread_event) == SE_MUTEX_SUCCESS);
   return SGX_ERROR_UNEXPECTED;
  
}

void CEnclave::put_tcs(CTrustThread *trust_thread)
{
    if(NULL == trust_thread)
    {
        return;
    }

    m_thread_pool->release_thread(trust_thread);
}

void CEnclave::destroy()
{
    se_wtlock(&m_rwlock);

    get_enclave_creator()->destroy_enclave(ENCLAVE_ID_IOCTL, m_size);

    m_destroyed = true;
    //We are going to destory m_rwlock. At this point, maybe an ecall is in progress, and try to get m_rwlock.
    //To prevent such ecall, we use m_destroyed to identify that the no ecall should going on. See CEnclave::ecall(...).
    //For new ecall to the enclave, it will return with SGX_ERROR_INVALID_ENCLAVE_ID immediately.
    se_wtunlock(&m_rwlock);
    // We should not use loader to destroy encalve because loader has been removed after successful enclave loading
    //m_loader.destroy_enclave();
}

void CEnclave::add_thread(tcs_t * const tcs, bool is_unallocated)
{
    CTrustThread *trust_thread = m_thread_pool->add_thread(tcs, this, is_unallocated);
    if(!is_unallocated)
    {
        insert_debug_tcs_info_head(&m_enclave_info, trust_thread->get_debug_info());
    }
}

void CEnclave::add_thread(CTrustThread * const trust_thread)
{
    insert_debug_tcs_info_head(&m_enclave_info, trust_thread->get_debug_info());
}


int CEnclave::set_extra_debug_info(secs_t& secs)
{
    void *g_peak_heap_used_addr = get_symbol_address("g_peak_heap_used");
    m_enclave_info.g_peak_heap_used_addr = g_peak_heap_used_addr;
    m_enclave_info.start_addr = secs.base;
    m_enclave_info.misc_select = secs.misc_select;

    if(g_peak_heap_used_addr == NULL)
    {
        SE_TRACE(SE_TRACE_DEBUG, "Symbol 'g_peak_heap_used' is not found\n");
        //This error should not break loader and debugger, so the upper layer function will ignore it.
        return SGX_ERROR_INVALID_ENCLAVE;
    }
    return SGX_SUCCESS;
}

void CEnclave::push_ocall_frame(ocall_frame_t* frame_point, CTrustThread *trust_thread)
{
    if(NULL == trust_thread)
    {
        return;
    }

    trust_thread->push_ocall_frame(frame_point);
}

void CEnclave::pop_ocall_frame(CTrustThread *trust_thread)
{
    if(NULL == trust_thread)
    {
        return;
    }

    trust_thread->pop_ocall_frame();
}

CEnclavePool CEnclavePool::m_instance;
CEnclavePool::CEnclavePool()
{
    m_enclave_list = NULL;
    se_mutex_init(&m_enclave_mutex);
    SE_TRACE(SE_TRACE_NOTICE, "enter CEnclavePool constructor\n");
}

CEnclavePool *CEnclavePool::instance()
{
    return &m_instance;
}

int CEnclavePool::add_enclave(CEnclave *enclave)
{
    int result = TRUE;

    se_mutex_lock(&m_enclave_mutex);

    if (m_enclave_list == NULL) {
        m_enclave_list = new Node<sgx_enclave_id_t, CEnclave*>(enclave->get_enclave_id(), enclave);
    } else {
        Node<sgx_enclave_id_t, CEnclave*>* node = new Node<sgx_enclave_id_t, CEnclave*>(enclave->get_enclave_id(), enclave);
        if (m_enclave_list->InsertNext(node) == false) {
            delete node;
            SE_TRACE(SE_TRACE_WARNING, "the encalve %llx has already been added\n", enclave->get_enclave_id());
            result = FALSE;
        }
    }
    se_mutex_unlock(&m_enclave_mutex);
    return result;
}

CEnclave * CEnclavePool::get_enclave(const sgx_enclave_id_t enclave_id)
{
    se_mutex_lock(&m_enclave_mutex);
    Node<sgx_enclave_id_t, CEnclave*>* it = m_enclave_list->Find(enclave_id);
    if(it != NULL)
    {
        se_mutex_unlock(&m_enclave_mutex);
        return it->value;
    }
    else
    {
        se_mutex_unlock(&m_enclave_mutex);
        return NULL;
    }
}

CEnclave * CEnclavePool::ref_enclave(const sgx_enclave_id_t enclave_id)
{
    se_mutex_lock(&m_enclave_mutex);
    Node<sgx_enclave_id_t, CEnclave*>* it = m_enclave_list->Find(enclave_id);
    if(it != NULL)
    {
        it->value->atomic_inc_ref();
        se_mutex_unlock(&m_enclave_mutex);
        return it->value;
    }
    else
    {
        se_mutex_unlock(&m_enclave_mutex);
        return NULL;
    }
}

void CEnclavePool::unref_enclave(CEnclave *enclave)
{
    //We use enclave pool lock to protect data, the lock is big, but is more secure.
    se_mutex_lock(&m_enclave_mutex);
    //The ref is increased in ref_enclave;
    uint32_t ref = enclave->atomic_dec_ref();

    //If the enclave is in zombie state, the HW enclave must have been destroyed.
    //And if the enclave is not referenced, the enclave instance will not be referenced any more,
    //so we delete the instance.
    //Another code path that delete enclave instance is in function "CEnclavePool::remove_enclave"
    if(enclave->is_zombie() && !ref)
        delete enclave;

    se_mutex_unlock(&m_enclave_mutex);
}

se_handle_t CEnclavePool::get_event(const void * const tcs)
{
    se_handle_t hevent = NULL;
    CEnclave *enclave = NULL;

    assert(tcs != NULL);
    se_mutex_lock(&m_enclave_mutex);

    Node<sgx_enclave_id_t, CEnclave*>* it = m_enclave_list;
    for(; it != NULL; it = it->next)
    {
        void *start = it->value->get_start_address();
        void *end = GET_PTR(void, start, it->value->get_size());

        /* check start & end */
        if (tcs >= start && tcs < end) {
            enclave = it->value;
            break;
        }
    }

    if (NULL != enclave)
    {
        CTrustThreadPool *pool = enclave->get_thread_pool();
        if (pool != NULL)
        {
            CTrustThread *thread = pool->get_bound_thread((const tcs_t *)tcs);
            if (thread != NULL)
                hevent = thread->get_event();
        }
    }

    se_mutex_unlock(&m_enclave_mutex);
    return hevent;
}

CEnclave* CEnclavePool::remove_enclave(const sgx_enclave_id_t enclave_id, sgx_status_t &status)
{
    status = SGX_SUCCESS;
    se_mutex_lock(&m_enclave_mutex);

    CEnclave *enclave = get_enclave(enclave_id);
    if(NULL == enclave)
    {
        status = SGX_ERROR_INVALID_ENCLAVE_ID;
        SE_TRACE(SE_TRACE_WARNING, "remove an unknown enclave\n");
        se_mutex_unlock(&m_enclave_mutex);
        return enclave;
    }

    enclave->destroy();
    //the ref is not 0, maybe some thread is in sgx_ocall, so we can NOT delete enclave instance.
    if(enclave->get_ref())
    {
        enclave->mark_zombie();

        /* When destroy the enclave, all threads that are waiting/about to wait
         * on untrusted event need to be waked. Otherwise, they will be always
         * pending on the untrusted events, and app need to manually kill the threads.
         */
        CTrustThreadPool *pool = enclave->get_thread_pool();
        pool->wake_threads();

        enclave = NULL;
    }
    Node<sgx_enclave_id_t, CEnclave*>* it = m_enclave_list->Remove(enclave_id);
    if (it == m_enclave_list)
        m_enclave_list = it->next;
    delete it;
    se_mutex_unlock(&m_enclave_mutex);

    return enclave;
}

void CEnclavePool::notify_debugger()
{
    se_mutex_lock(&m_enclave_mutex);
    if(m_enclave_list!= NULL)
    {
        Node<sgx_enclave_id_t, CEnclave*>* it = m_enclave_list;
        for(; it != NULL; it = it->next)
        {
            //send debug event to debugger when enclave is debug mode or release mode
            debug_enclave_info_t * debug_info = const_cast<debug_enclave_info_t*>((it->value)->get_debug_info());
            generate_enclave_debug_event(URTS_EXCEPTION_PREREMOVEENCLAVE, debug_info);
        }
    }
    se_mutex_unlock(&m_enclave_mutex);

}

bool CEnclave::update_trust_thread_debug_flag(void* tcs_address, uint8_t debug_flag)
{
    uint64_t debug_flag2 = (uint64_t)debug_flag;
    debug_enclave_info_t *debug_info = NULL;

    debug_info = const_cast<debug_enclave_info_t *>(get_debug_info());

    pid_t pid = getpid();

    if(debug_info->enclave_type == ET_DEBUG)
    {
       
         if(!se_write_process_mem(pid, reinterpret_cast<unsigned char *>(tcs_address) + sizeof(uint64_t), &debug_flag2, sizeof(uint64_t), NULL))
              return FALSE;

    }

    return TRUE;
}

bool CEnclave::update_debug_flag(uint8_t debug_flag)
{
    debug_tcs_info_t* tcs_list_entry = m_enclave_info.tcs_list;
    
    while(tcs_list_entry)
    {
         if(!update_trust_thread_debug_flag(tcs_list_entry->TCS_address, debug_flag))
              return FALSE;

         tcs_list_entry = tcs_list_entry->next_tcs_info;     
    }

    return TRUE;
}
