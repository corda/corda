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
#include "routine.h"
#include "se_error_internal.h"
#include "xsave.h"

extern "C"
sgx_status_t sgx_ecall(const sgx_enclave_id_t enclave_id, const int proc, const void *ocall_table, void *ms)
{
    if(proc < 0)
        return SGX_ERROR_INVALID_FUNCTION;

    CEnclave* enclave = CEnclavePool::instance()->ref_enclave(enclave_id);

    //If we failed to reference enclave, there is no corresponding enclave instance, so we didn't increase the enclave.m_ref;
    if(!enclave)
        return SGX_ERROR_INVALID_ENCLAVE_ID;

    sgx_status_t result = SGX_ERROR_UNEXPECTED;
    {
        result = enclave->ecall(proc, ocall_table, ms);
    }
    {
        //This solution seems more readable and easy to validate, but low performace
        CEnclavePool::instance()->unref_enclave(enclave);
    }

    return result;
}

extern "C"
int sgx_ocall(const unsigned int proc, const sgx_ocall_table_t *ocall_table, void *ms, CTrustThread *trust_thread)
{
    assert(trust_thread != NULL);
    CEnclave* enclave = trust_thread->get_enclave();
    assert(enclave != NULL);
    return enclave->ocall(proc, ocall_table, ms);
}
