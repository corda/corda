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


// u_instructions.cpp -- It simulates Enclave instructions.
#include <string.h>
#include <assert.h>

#include <stdio.h>
#include <stdlib.h>

#include "arch.h"
#include "util.h"
#include "se_memory.h"
#include "se_memcpy.h"
#include "se_trace.h"
#include "enclave.h"
#include "td_mngr.h"

#include "lowlib.h"
#include "sgxsim.h"
#include "enclave_mngr.h"
#include "u_instructions.h"

#include "crypto_wrapper.h"

static uintptr_t _EINIT(secs_t* secs, enclave_css_t* css, token_t* launch);
static uintptr_t _ECREATE (page_info_t* pi);
static uintptr_t _EADD (page_info_t* pi, void* epc_lin_addr);
static uintptr_t _EREMOVE(const void* epc_lin_addr);

////////////////////////////////////////////////////////////////////////
#define __GP__() exit(EXIT_FAILURE)

#define GP() do {                                                       \
        SE_TRACE(SE_TRACE_DEBUG, "#GP on %s, line: %d\n", __FILE__, __LINE__); \
        __GP__();                                                       \
    } while (0)

#define GP_ON(cond) if (cond) GP()

#define GP_ON_EENTER GP_ON

#define mcp_same_size(dst_ptr, src_ptr, size) memcpy_s(dst_ptr, size, src_ptr, size)

uintptr_t _EINIT(secs_t* secs, enclave_css_t *css, token_t *launch)
{
    CEnclaveMngr *mngr = CEnclaveMngr::get_instance();
    assert(mngr != NULL);

    CEnclaveSim* ce = mngr->get_enclave(secs);
    GP_ON(ce == NULL);

    GP_ON((ce->get_secs()->attributes.flags & SGX_FLAGS_INITTED) != 0);

    // Fill MREnclave, MRSigner, ISVPRODID, ISVSVN
    secs_t* this_secs = ce->get_secs();
    if (css != NULL) {
        // Check signature
        if ((css->body.attribute_mask.xfrm & this_secs->attributes.xfrm)
            != (css->body.attribute_mask.xfrm & css->body.attributes.xfrm))
        {
            SE_TRACE(SE_TRACE_DEBUG,
                "SECS attributes.xfrm does NOT match signature attributes.xfrm\n");
            return SGX_ERROR_INVALID_ATTRIBUTE;
        }

        if ((css->body.attribute_mask.flags & this_secs->attributes.flags)
            != (css->body.attribute_mask.flags & css->body.attributes.flags))
        {
            SE_TRACE(SE_TRACE_DEBUG,
                "SECS attributes.flag does NOT match signature attributes.flag\n");
            return SGX_ERROR_INVALID_ATTRIBUTE;
        }

        mcp_same_size(&this_secs->mr_enclave, &css->body.enclave_hash, sizeof(sgx_measurement_t));
        this_secs->isv_prod_id = css->body.isv_prod_id;
        this_secs->isv_svn = css->body.isv_svn;
        
        uint8_t signer[SGX_HASH_SIZE] = {0};
        unsigned int signer_len = SGX_HASH_SIZE;
        sgx_status_t ret = sgx_EVP_Digest(EVP_sha256(), css->key.modulus, SE_KEY_SIZE, signer, &signer_len);
        if(ret != SGX_SUCCESS)
        {
            if(ret != SGX_ERROR_OUT_OF_MEMORY)
                ret = SGX_ERROR_UNEXPECTED;
            return ret;
        }
        assert(signer_len == SGX_HASH_SIZE);

        mcp_same_size(&this_secs->mr_signer, signer, SGX_HASH_SIZE);
    }

    // Check launch token
    if (launch != NULL && launch->body.valid) {
        if (memcmp(&launch->body.attributes, &this_secs->attributes, sizeof(sgx_attributes_t)))
        {
            SE_TRACE(SE_TRACE_DEBUG,
                "SECS attributes does NOT match launch token attribuets\n");
            return SGX_ERROR_INVALID_ATTRIBUTE;
        }
    }

    // Mark it initialized
    this_secs->attributes.flags |= SGX_FLAGS_INITTED;

    return SGX_SUCCESS;
}

static inline bool is_power_of_two(size_t n)
{
    return (n != 0) && (!(n & (n - 1)));
}

// Returns the pointer to the Enclave instance on success.
uintptr_t _ECREATE(page_info_t* pi)
{
    secs_t* secs = reinterpret_cast<secs_t*>(pi->src_page);

    // Enclave size must be at least 2 pages and a power of 2.
    GP_ON(!is_power_of_two((size_t)secs->size));
    GP_ON(secs->size < (SE_PAGE_SIZE << 1));

    CEnclaveSim* ce = new CEnclaveSim(secs);
    void*   addr;

    // `ce' is not checked against NULL, since it is not
    // allocated with new(std::no_throw).
    addr = se_virtual_alloc(NULL, (size_t)secs->size, MEM_COMMIT);
    if (addr == NULL) {
        delete ce;
        return 0;
    }

    // Mark all the memory inaccessible.
    se_virtual_protect(addr, (size_t)secs->size, SGX_PROT_NONE);
    ce->get_secs()->base = addr;

    CEnclaveMngr::get_instance()->add(ce);
    return reinterpret_cast<uintptr_t>(ce);
}

uintptr_t _EADD(page_info_t* pi, void *epc_lin_addr)
{
    void     *src_page = pi->src_page;
    CEnclaveMngr *mngr = CEnclaveMngr::get_instance();
    CEnclaveSim    *ce = mngr->get_enclave(pi->lin_addr);

    if (ce == NULL) {
        SE_TRACE(SE_TRACE_DEBUG, "failed to get enclave instance\n");
        return SGX_ERROR_UNEXPECTED;
    }

    GP_ON(!IS_PAGE_ALIGNED(epc_lin_addr));
    GP_ON((ce->get_secs()->attributes.flags & SGX_FLAGS_INITTED) != 0);

    // Make the page writable before doing memcpy()
    se_virtual_protect(epc_lin_addr, SE_PAGE_SIZE, SI_FLAGS_RW);

    mcp_same_size(epc_lin_addr, src_page, SE_PAGE_SIZE);

    se_virtual_protect(epc_lin_addr, SE_PAGE_SIZE, (uint32_t)pi->sec_info->flags);

    GP_ON(!ce->add_page(pi->lin_addr, pi->sec_info->flags));
    return SGX_SUCCESS;
}

uintptr_t _EREMOVE(const void *epc_lin_addr)
{
    CEnclaveMngr *mngr = CEnclaveMngr::get_instance();
    CEnclaveSim *ce = mngr->get_enclave(epc_lin_addr);

    GP_ON(!ce);
    GP_ON(!IS_PAGE_ALIGNED(epc_lin_addr));

    return ce->remove_page(epc_lin_addr) ? 0 : -1;
}

////////////////////////////////////////////////////////////////////////

// Master entry functions


void _SE3(uintptr_t xax, uintptr_t xbx,
          uintptr_t xcx, uintptr_t xdx,
          uintptr_t xsi, uintptr_t xdi)
{
    UNUSED(xdx);

    switch (xax)
    {
    case SE_EENTER:
        uintptr_t     xip;
        void        * enclave_base_addr;
        se_pt_regs_t* p_pt_regs;
        tcs_t*        tcs;
        tcs_sim_t*    tcs_sim;
        ssa_gpr_t*    p_ssa_gpr;
        secs_t*       secs;
        CEnclaveMngr* mngr;
        CEnclaveSim*    ce;

        // xbx contains the address of a TCS
        tcs = reinterpret_cast<tcs_t*>(xbx);

        // Is TCS pointer page-aligned?
        GP_ON_EENTER(!IS_PAGE_ALIGNED(tcs));

        mngr = CEnclaveMngr::get_instance();
        assert(mngr != NULL);

        // Is it really a TCS?
        ce = mngr->get_enclave(tcs);
        GP_ON_EENTER(ce == NULL);
        GP_ON_EENTER(!ce->is_tcs_page(tcs));

        // Check the EntryReason
        tcs_sim = reinterpret_cast<tcs_sim_t *>(tcs->reserved);
        GP_ON_EENTER(tcs_sim->tcs_state != TCS_STATE_INACTIVE);
        GP_ON_EENTER(tcs->cssa >= tcs->nssa);

        secs = ce->get_secs();
        enclave_base_addr = secs->base;

        p_ssa_gpr = reinterpret_cast<ssa_gpr_t*>(reinterpret_cast<uintptr_t>(enclave_base_addr) + static_cast<size_t>(tcs->ossa)
                + secs->ssa_frame_size * SE_PAGE_SIZE
                - sizeof(ssa_gpr_t));

        tcs_sim->saved_aep = xcx;

        p_pt_regs = reinterpret_cast<se_pt_regs_t*>(get_bp());
        p_ssa_gpr->REG(bp_u) = p_pt_regs->xbp;

        p_ssa_gpr->REG(sp_u) = reinterpret_cast<uintptr_t>(p_pt_regs + 1);
        xcx = p_pt_regs->xip;

        xip = reinterpret_cast<uintptr_t>(enclave_base_addr);
        GP_ON_EENTER(xip == 0);

        //set the _tls_array to point to the self_addr of TLS section inside the enclave
        GP_ON_EENTER(td_mngr_set_td(enclave_base_addr, tcs) == false);
 
        // Destination depends on STATE
        xip += (uintptr_t)tcs->oentry;
        tcs_sim->tcs_state = TCS_STATE_ACTIVE;

        // Link the TCS to the thread
        GP_ON_EENTER((secs->attributes.flags & SGX_FLAGS_INITTED) == 0);

        // Replace the return address on the stack with the enclave entry,
        // so that when we return from this function, we'll enter the enclave.
        enclu_regs_t regs;
        regs.xax = tcs->cssa;
        regs.xbx = reinterpret_cast<uintptr_t>(tcs);
        regs.xcx = xcx;
        regs.xdx = 0;
        regs.xsi = xsi;
        regs.xdi = xdi;
        regs.xbp = p_ssa_gpr->REG(bp_u);
        regs.xsp = p_ssa_gpr->REG(sp_u);
        regs.xip = xip;

        load_regs(&regs);

        // Returning from this function enters the enclave
        return;
    default:
        // There's only 1 ring 3 instruction outside the enclave: EENTER.
        GP();
    }
}

uintptr_t _SE0(uintptr_t xax, uintptr_t xbx,
               uintptr_t xcx, uintptr_t xdx,
               uintptr_t xsi, uintptr_t xdi)
{
    UNUSED(xsi), UNUSED(xdi);

    switch (xax)
    {
    case SE_ECREATE:
        return _ECREATE(reinterpret_cast<page_info_t*>(xbx));

    case SE_EADD:
        return _EADD(reinterpret_cast<page_info_t*>(xbx),
                     reinterpret_cast<void*>(xcx));

    case SE_EINIT:
        return _EINIT(reinterpret_cast<secs_t*>(xbx),
                      reinterpret_cast<enclave_css_t *>(xcx),
                      reinterpret_cast<token_t *>(xdx));

    case SE_EREMOVE:
        return _EREMOVE(reinterpret_cast<void*>(xcx));

    default:
        GP();
    }

    return 0;
}

