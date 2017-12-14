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


#ifndef _ENCLAVE_H_
#define _ENCLAVE_H_

#include "se_wrapper.h"
#include "tcs.h"
#include "create_param.h"
#include "sgx_eid.h"
#include "routine.h"
#include "loader.h"
#include "file.h"
#include "uncopyable.h"
#include "node.h"

class CLoader;

class CEnclave: private Uncopyable
{
public:
    explicit CEnclave(CLoader &ldr);
    ~CEnclave();
    void* get_start_address(){ return m_start_addr;};
    void * get_symbol_address(const char * const symbol);
    sgx_enclave_id_t get_enclave_id();
    uint32_t get_enclave_version();
    size_t get_dynamic_tcs_list_size();
    CTrustThreadPool * get_thread_pool() { return m_thread_pool; }
    uint64_t get_size() { return m_size; };
    sgx_status_t ecall(const int proc, const void *ocall_table, void *ms);
    int ocall(const unsigned int proc, const sgx_ocall_table_t *ocall_table, void *ms);
    void destroy();
    uint32_t atomic_inc_ref() { return se_atomic_inc(&m_ref); }
    uint32_t atomic_dec_ref() { return se_atomic_dec(&m_ref); }
    uint32_t get_ref() { return m_ref; }
    void mark_zombie()  { m_zombie = true; }
    bool is_zombie() { return m_zombie; }
    sgx_status_t initialize(const se_file_t& file, const sgx_enclave_id_t enclave_id, void * const start_addr, const uint64_t enclave_size, const uint32_t tcs_policy, const uint32_t enclave_version, const uint32_t tcs_min_pool);
    void add_thread(tcs_t * const tcs, bool is_unallocated);
    void add_thread(CTrustThread * const trust_thread);
    const debug_enclave_info_t* get_debug_info();
    void set_dbg_flag(bool dbg_flag) { m_dbg_flag = dbg_flag; }
    bool get_dbg_flag() { return m_dbg_flag; }
    int set_extra_debug_info(secs_t& secs);
    //rdunlock is used in signal handler
    void rdunlock() { se_rdunlock(&m_rwlock); }
    void push_ocall_frame(ocall_frame_t* frame_point, CTrustThread *trust_thread);
    void pop_ocall_frame(CTrustThread *trust_thread);
    bool update_trust_thread_debug_flag(void*, uint8_t);
    bool update_debug_flag(uint8_t);
    sgx_status_t fill_tcs_mini_pool();
    sgx_status_t fill_tcs_mini_pool_fn();
private:
    CTrustThread * get_tcs(bool is_initialize_ecall = false);
    void put_tcs(CTrustThread *trust_thread);
    sgx_status_t error_trts2urts(unsigned int trts_error);

    CLoader                 &m_loader;
    sgx_enclave_id_t        m_enclave_id;
    void                    *m_start_addr;
    uint64_t                m_size;
    se_rwlock_t             m_rwlock;
    uint32_t                m_power_event_flag;
    uint32_t                m_ref;
    bool                    m_zombie;
    CTrustThreadPool        *m_thread_pool;
    debug_enclave_info_t    m_enclave_info;
    bool                    m_dbg_flag;
    bool                    m_destroyed;
    uint32_t                m_version;
    sgx_ocall_table_t       *m_ocall_table;
    pthread_t               m_pthread_tid;
    bool                    m_pthread_is_valid;
    se_handle_t             m_new_thread_event;
};

class CEnclavePool: private Uncopyable
{
public:
    static CEnclavePool *instance();
    int add_enclave(CEnclave *enclave);
    CEnclave * get_enclave(const sgx_enclave_id_t enclave_id);
    CEnclave * ref_enclave(const sgx_enclave_id_t enclave_id);
    void unref_enclave(CEnclave *enclave);
    int get_symbol_ordinal(const sgx_enclave_id_t enclave_id, const char *symbol, int *ordinal);
    CEnclave * remove_enclave(const sgx_enclave_id_t enclave_id, sgx_status_t &status);
    se_handle_t get_event(const void * const);
    void notify_debugger();
private:
    CEnclavePool();

    Node<sgx_enclave_id_t, CEnclave*>   *m_enclave_list;
    se_mutex_t                          m_enclave_mutex;       //sync for add/get/remove enclave.
    static CEnclavePool                 m_instance;
};

#define AbnormalTermination() (FALSE)

#endif
