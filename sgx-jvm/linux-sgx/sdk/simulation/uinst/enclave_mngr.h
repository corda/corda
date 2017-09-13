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


#ifndef _SE_ENCLAVE_MNGR_H_
#define _SE_ENCLAVE_MNGR_H_

#include <list>

#include "arch.h"
#include "sgx_eid.h"
#include "se_thread.h"

class CEnclaveSim
{
public:
    CEnclaveSim(const secs_t* secs);
    virtual ~CEnclaveSim(void);

    // The following functions are declared virtual so that they can
    // be called from the tRTS instruction simulation functions.

    virtual sgx_enclave_id_t get_enclave_id() const;
    virtual secs_t* get_secs();

    virtual size_t get_pg_idx(const void* pgaddr) const;
    virtual bool add_page(const void* pgaddr, si_flags_t flags);
    virtual bool remove_page(const void* epc_lin_addr);
    virtual bool is_tcs_page(const void* pgaddr) const;

private:
    secs_t          m_secs;
    si_flags_t*     m_flags;        // memory managed by CEnclaveSim
    size_t          m_cpages;       // page count

    sgx_enclave_id_t    m_enclave_id;   // an unique Id for the enclave
    static uint32_t m_counter;      // enclave counter

    sgx_enclave_id_t gen_enclave_id(void);
    bool validate_pg_and_flags(const void* pgaddr, si_flags_t flags);

    CEnclaveSim(const CEnclaveSim& es);
    CEnclaveSim& operator=(const CEnclaveSim& es);
};


class CEnclaveMngr
{
public:
     
     // The factory method for CEnclaveMngr.
     //
     // Note: this factory is not thread-safe.

    static CEnclaveMngr* get_instance();
    ~CEnclaveMngr();

    void add(CEnclaveSim* ce);
    void remove(CEnclaveSim* ce);

    CEnclaveSim* get_enclave(const sgx_enclave_id_t id);
    CEnclaveSim* get_enclave(const secs_t* secs);
    CEnclaveSim* get_enclave(const void* base_addr);

private:
    std::list<CEnclaveSim*> m_enclave_list;

    // to protect the access to m_enclave_list
    se_mutex_t m_list_lock;

    CEnclaveMngr();
    CEnclaveMngr(const CEnclaveMngr& em);
    CEnclaveMngr& operator=(const CEnclaveMngr& em);
};

#endif
