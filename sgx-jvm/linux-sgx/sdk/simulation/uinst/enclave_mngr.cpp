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

#include <string.h>

#include "se_memory.h"
#include "se_memcpy.h"
#include "util.h"
#include "enclave_mngr.h"
#include "se_atomic.h"


static uint32_t atomic_inc32(uint32_t volatile *val)
{
    return se_atomic_inc(val);
}

uint32_t CEnclaveSim::m_counter = 1;

sgx_enclave_id_t CEnclaveSim::gen_enclave_id(void)
{
    //getpid() is to simulate fork() scenario, refer to do_ecall in sig_handler.cpp
    sgx_enclave_id_t id = ((uint64_t)getpid() << 32) | atomic_inc32(&m_counter);
    return id;
}

CEnclaveSim::CEnclaveSim(const secs_t* secs)
{
    m_cpages = static_cast<size_t>(secs->size >> SE_PAGE_SHIFT);
    m_flags = new si_flags_t[m_cpages];

    // pages flags are initialized to -1
    memset(m_flags, 0xff,  m_cpages * sizeof(si_flags_t));

    memcpy_s(&m_secs, sizeof(m_secs), secs, sizeof(*secs));

    m_enclave_id = gen_enclave_id();
}

CEnclaveSim::~CEnclaveSim()
{
    delete[] m_flags;
    se_virtual_free(m_secs.base, (size_t)m_secs.size, MEM_RELEASE);
}

sgx_enclave_id_t CEnclaveSim::get_enclave_id() const
{
    return m_enclave_id;
}

secs_t* CEnclaveSim::get_secs()
{
    return &m_secs;
}

size_t CEnclaveSim::get_pg_idx(const void* pgaddr) const
{
    return PTR_DIFF(pgaddr, m_secs.base) >> SE_PAGE_SHIFT;
}


bool CEnclaveSim::validate_pg_and_flags(const void* addr, si_flags_t flags)
{
    // Must be page aligned
    if (!IS_PAGE_ALIGNED(addr))
        return false;

    size_t page_idx = get_pg_idx(addr);
    // Must be within enclave address space

    if (page_idx >= m_cpages)
        return false;

    // Requested flags should only set those visible by instuctions
    if ((flags & (~SI_FLAGS_EXTERNAL)))
        return false;

    return true;
}


bool CEnclaveSim::add_page(const void* addr, si_flags_t flags)
{
    if (!validate_pg_and_flags(addr, flags))
        return false;

    // We only deal with these flags
    flags &= static_cast<si_flags_t>(SI_FLAGS_EXTERNAL);

    // Must not have been added yet
    size_t page_idx = get_pg_idx(addr);

    if (m_flags[page_idx] != (si_flags_t)-1)
        return false;

    m_flags[page_idx] = flags;

    return true;
}

bool CEnclaveSim::remove_page(const void* epc_lin_addr)
{
    size_t page_idx = get_pg_idx(epc_lin_addr);

    if (m_flags[page_idx] != (si_flags_t)-1) {
        m_flags[page_idx] = (si_flags_t)-1;
        return true;
    }

    return false;
}
bool CEnclaveSim::is_tcs_page(const void* addr) const
{
    // Must be page aligned
    if (!IS_PAGE_ALIGNED(addr))
        return false;

    size_t page_idx = get_pg_idx(addr);

    // Must be within enclave address space
    if (page_idx >= m_cpages)
        return false;

    return (m_flags[page_idx] & SI_FLAG_PT_MASK) == SI_FLAG_TCS;
}


//////////////////////////////////////////////////////////////////////
CEnclaveMngr::CEnclaveMngr()
{
    se_mutex_init(&m_list_lock);
}

CEnclaveMngr::~CEnclaveMngr()
{
    se_mutex_destroy(&m_list_lock);

    std::list<CEnclaveSim*>::iterator it = m_enclave_list.begin();
    for (; it != m_enclave_list.end(); ++it)
    {
        delete (*it);
    }
}

// Note: this singleton implemenation is not multi-threading safe.
CEnclaveMngr* CEnclaveMngr::get_instance()
{
    static CEnclaveMngr mngr;
    return &mngr;
}

// Use constructor attribute to make sure that the later calling of 
// CEnclaveMngr::get_instance() is MT-safe.
__attribute__ ((__constructor__)) static void build_mngr_instance()
{
    CEnclaveMngr::get_instance();
}

void CEnclaveMngr::add(CEnclaveSim* ce)
{
    if (ce != NULL)
    {
        se_mutex_lock(&m_list_lock);
        m_enclave_list.push_back(ce);
        se_mutex_unlock(&m_list_lock);
    }
}

void CEnclaveMngr::remove(CEnclaveSim* ce)
{
    if (ce != NULL)
    {
        se_mutex_lock(&m_list_lock);
        m_enclave_list.remove(ce);
        se_mutex_unlock(&m_list_lock);
    }
}

CEnclaveSim* CEnclaveMngr::get_enclave(const sgx_enclave_id_t id)
{
    CEnclaveSim* ce = NULL;

    se_mutex_lock(&m_list_lock);

    std::list<CEnclaveSim*>::iterator it = m_enclave_list.begin();
    for (; it != m_enclave_list.end(); ++it)
    {
        if ((*it)->get_enclave_id() == id)
        {
            ce = *it;
            break;
        }
    }

    se_mutex_unlock(&m_list_lock);
    return ce;
}

CEnclaveSim* CEnclaveMngr::get_enclave(const void* base_addr)
{
    CEnclaveSim* ce = NULL;

    se_mutex_lock(&m_list_lock);

    std::list<CEnclaveSim*>::iterator it = m_enclave_list.begin();
    for (; it != m_enclave_list.end(); ++it)
    {
        secs_t* secs = (*it)->get_secs();
        if (base_addr >= secs->base &&
            PTR_DIFF(base_addr, secs->base) < secs->size)
        {
            ce = *it;
            break;
        }
    }

    se_mutex_unlock(&m_list_lock);
    return ce;
}

CEnclaveSim* CEnclaveMngr::get_enclave(const secs_t* secs)
{
    // The pEnclaveSECS field might not have been initialized yet.
    return get_enclave(secs->base);
}
