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

#include "se_wrapper.h"
#include "util.h"
#include "enclave.h"

#include "enclave_mngr.h"
#include "sgxsim.h"
#include "driver_api.h"

#define TO_STR(e) #e

#define BUG_ON(cond, rv) do {                                        \
    if (cond) {                                                      \
        SE_TRACE(SE_TRACE_DEBUG, "*** BUG ***: %s\n", TO_STR(cond)); \
        return rv;                                                   \
    }                                                                \
} while (0)


/* Allocate linear address space. */
int create_enclave(secs_t           *secs,
                   sgx_enclave_id_t *enclave_id,
                   void             **start_addr)
{
    CEnclaveSim   *ce;
    sec_info_t    sinfo;
    page_info_t   pinfo;

    BUG_ON(secs == NULL, SGX_ERROR_UNEXPECTED);
    BUG_ON(enclave_id == NULL, SGX_ERROR_UNEXPECTED);
    BUG_ON(start_addr == NULL, SGX_ERROR_UNEXPECTED);

    memset(&sinfo, 0, sizeof(sinfo));
    sinfo.flags = SI_FLAGS_SECS;

    memset(&pinfo, 0, sizeof(pinfo));
    pinfo.src_page = secs;
    pinfo.sec_info = &sinfo;

    ce = reinterpret_cast<CEnclaveSim*>(DoECREATE_SW(&pinfo));
    if (ce == NULL) {
        SE_TRACE(SE_TRACE_DEBUG, "out of memory.\n");
        return SGX_ERROR_OUT_OF_MEMORY;
    }

    *start_addr = ce->get_secs()->base;
    *enclave_id = ce->get_enclave_id();
    secs->base = *start_addr;

    return SGX_SUCCESS;
}

int add_enclave_page(sgx_enclave_id_t enclave_id,
                     void             *source,
                     size_t           offset,
                     const sec_info_t &secinfo,
                     uint32_t         attr)
{
    sec_info_t   sinfo;
    page_info_t  pinfo;
    CEnclaveMngr *mngr;
    CEnclaveSim  *ce;

    UNUSED(attr);

    mngr = CEnclaveMngr::get_instance();
    ce = mngr->get_enclave(enclave_id);
    if (ce == NULL) {
        SE_TRACE(SE_TRACE_DEBUG,
                 "enclave (id = %llu) not found.\n",
                 enclave_id);
        return SGX_ERROR_INVALID_ENCLAVE_ID;
    }
    memset(&sinfo, 0, sizeof(sec_info_t));
    sinfo.flags = secinfo.flags;
    if(memcmp(&sinfo, &secinfo, sizeof(sec_info_t)))
        return SGX_ERROR_UNEXPECTED;

    memset(&pinfo, 0, sizeof(pinfo));
    pinfo.secs     = ce->get_secs();
    pinfo.lin_addr = (char*)ce->get_secs()->base + offset;
    pinfo.src_page = source;
    pinfo.sec_info = &sinfo;

    /* Passing NULL here when there is no EPC mgmt. */
    return (int)DoEADD_SW(&pinfo, GET_PTR(void, ce->get_secs()->base, offset));
}

int init_enclave(sgx_enclave_id_t  enclave_id,
                 enclave_css_t     *enclave_css,
                 token_t           *launch)
{
    CEnclaveMngr* mngr = CEnclaveMngr::get_instance();
    CEnclaveSim* ce = mngr->get_enclave(enclave_id);

    if (ce == NULL) {
        SE_TRACE(SE_TRACE_DEBUG,
                 "enclave (id = %llu) not found.\n",
                 enclave_id);
        return SGX_ERROR_INVALID_ENCLAVE_ID;
    }

    return (int)DoEINIT_SW(ce->get_secs(), enclave_css, launch);
}

int destroy_enclave(sgx_enclave_id_t enclave_id)
{
    CEnclaveMngr* mngr = CEnclaveMngr::get_instance();
    CEnclaveSim* ce = mngr->get_enclave(enclave_id);
    if (ce == NULL) {
        SE_TRACE(SE_TRACE_DEBUG,
                 "enclave (id = %llu) not found.\n",
                 enclave_id);
        return SGX_ERROR_INVALID_ENCLAVE_ID;
    }

    /* In simulation mode, all allocated pages will be freed upon the later
       `delete ce'.  Just remove the first page here. */
    DoEREMOVE_SW(0, ce->get_secs()->base);

    mngr->remove(ce);
    delete ce;

    return SGX_SUCCESS;
}
