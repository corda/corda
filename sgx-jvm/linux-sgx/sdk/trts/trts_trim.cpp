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


#include "trts_trim.h"
#include "sgx_trts.h" // for sgx_ocalloc, sgx_is_outside_enclave
#include "internal/rts.h"

/* sgx_ocfree() just restores the original outside stack pointer. */
#define OCALLOC(val, type, len) do {    \
    void* __tmp = sgx_ocalloc(len); \
    if (__tmp == NULL) {    \
        sgx_ocfree();   \
        return SGX_ERROR_UNEXPECTED;\
    }           \
    (val) = (type)__tmp;    \
} while (0)

typedef struct ms_trim_range_ocall_t {
    size_t ms_fromaddr;
    size_t ms_toaddr;
} ms_trim_range_ocall_t;

typedef struct ms_trim_range_commit_ocall_t {
    size_t ms_addr;
} ms_trim_range_commit_ocall_t;

sgx_status_t SGXAPI trim_range_ocall(size_t fromaddr, size_t toaddr)
{
    sgx_status_t status = SGX_SUCCESS;

    ms_trim_range_ocall_t* ms;
    OCALLOC(ms, ms_trim_range_ocall_t*, sizeof(*ms));

    ms->ms_fromaddr = fromaddr;
    ms->ms_toaddr = toaddr;
    status = sgx_ocall(EDMM_TRIM, ms);


    sgx_ocfree();
    return status;
}

sgx_status_t SGXAPI trim_range_commit_ocall(size_t addr)
{
    sgx_status_t status = SGX_SUCCESS;

    ms_trim_range_commit_ocall_t* ms;
    OCALLOC(ms, ms_trim_range_commit_ocall_t*, sizeof(*ms));

    ms->ms_addr = addr;
    status = sgx_ocall(EDMM_TRIM_COMMIT, ms);


    sgx_ocfree();
    return status;
}

