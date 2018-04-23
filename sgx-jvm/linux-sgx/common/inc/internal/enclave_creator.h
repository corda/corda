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

/** 
 * File: enclave_creator.h
 * Description: this header file defines the interface used by Enclave loader to create the Enclave
 * 
 *   The hardware, simulation and signing mode shall inherit from this class and 
 *   implement all the virtual functions
 */

#ifndef _ENCLAVE_CREATOR_H
#define _ENCLAVE_CREATOR_H

#include "arch.h"
#include "sgx_eid.h"
#include "metadata.h"
#include "sgx_error.h"
#include "util.h"
#include "launch_checker.h"
#include "uncopyable.h"
#include <string.h>
#include "file.h"
#include "isgx_user.h"


// this is the interface to both hardware, simulation and signing mode
class EnclaveCreator : private Uncopyable
{
public:
    /*
    @quote      the EPC reserved;
    @enclave_id identify the unique enclave;
    @start_addr is the linear address allocated for Enclave;
    */
    virtual int create_enclave(secs_t *secs, sgx_enclave_id_t *enclave_id, void **start_addr, bool ae = false) = 0;
    /*
    *@attr can be REMOVABLE
    */
    virtual int add_enclave_page(sgx_enclave_id_t enclave_id, void *source, uint64_t offset, const sec_info_t &sinfo, uint32_t attr) = 0;
    virtual int init_enclave(sgx_enclave_id_t enclave_id, enclave_css_t *enclave_css, SGXLaunchToken *lc, le_prd_css_file_t *prd_css_file = NULL) = 0;
    virtual int destroy_enclave(sgx_enclave_id_t enclave_id, uint64_t enclave_size = 0) = 0;
    virtual int initialize(sgx_enclave_id_t enclave_id) = 0;
    virtual bool use_se_hw() const = 0;
    virtual bool is_EDMM_supported(sgx_enclave_id_t enclave_id) = 0;
    virtual bool is_driver_compatible() = 0;

    virtual int get_misc_attr(sgx_misc_attribute_t *sgx_misc_attr, metadata_t *metadata, SGXLaunchToken * const lc, uint32_t flag) = 0;
    virtual bool get_plat_cap(sgx_misc_attribute_t *se_attr) = 0;
#ifdef SE_1P5_VERTICAL
    virtual uint32_t handle_page_fault(uint64_t pf_address) { UNUSED(pf_address); return (uint32_t)SGX_ERROR_UNEXPECTED; }
#endif
    virtual int emodpr(uint64_t addr, uint64_t size, uint64_t flag) = 0;
    virtual int mktcs(uint64_t tcs_addr) = 0;
    virtual int trim_range(uint64_t fromaddr, uint64_t toaddr) = 0;
    virtual int trim_accept(uint64_t addr) = 0;
    virtual int remove_range(uint64_t fromaddr, uint64_t numpages) = 0;
    // destructor
    virtual ~EnclaveCreator() {};
};

EnclaveCreator* get_enclave_creator(void);

extern EnclaveCreator* g_enclave_creator;

#endif
