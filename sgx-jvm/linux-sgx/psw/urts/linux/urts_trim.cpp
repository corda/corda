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

#include "urts_trim.h"
#include "enclave_creator.h"

typedef struct ms_trim_range_ocall_t {
    size_t ms_fromaddr;
    size_t ms_toaddr;
} ms_trim_range_ocall_t;

typedef struct ms_trim_accept_ocall_t {
    size_t ms_addr;
} ms_trim_accept_ocall_t;

sgx_status_t ocall_trim_range(void* pms)
{
    int ret = 0;
    ms_trim_range_ocall_t* ms = SGX_CAST(ms_trim_range_ocall_t*, pms);

    EnclaveCreator *enclave_creator = get_enclave_creator();
    if(NULL == enclave_creator)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    ret = enclave_creator->trim_range(ms->ms_fromaddr, ms->ms_toaddr);
    
    return (sgx_status_t)ret; 
}

sgx_status_t ocall_trim_accept(void* pms)
{
    int ret = 0;
    ms_trim_accept_ocall_t* ms = SGX_CAST(ms_trim_accept_ocall_t*, pms);

    EnclaveCreator *enclave_creator = get_enclave_creator();
    if(NULL == enclave_creator)
    {
        return SGX_ERROR_UNEXPECTED;
    }

    ret = enclave_creator->trim_accept(ms->ms_addr);

    return (sgx_status_t)ret; 

}


