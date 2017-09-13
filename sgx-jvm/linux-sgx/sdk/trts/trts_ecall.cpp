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


#include "se_memcpy.h"
#include "thread_data.h"
#include "global_data.h"
#include "rts.h"
#include "util.h"
#include "xsave.h"
#include "sgx_trts.h"
#include "sgx_spinlock.h"
#include "global_init.h"
#include "trts_internal.h"

// is_ecall_allowed()
// check the index in the dynamic entry table
static sgx_status_t is_ecall_allowed(uint32_t ordinal)
{
    if(ordinal >= g_ecall_table.nr_ecall)
    {
        return SGX_ERROR_INVALID_FUNCTION;
    }
    thread_data_t *thread_data = get_thread_data();
    if(thread_data->last_sp == thread_data->stack_base_addr)
    {
        // root ECALL, check the priv bits.
        if (g_ecall_table.ecall_table[ordinal].is_priv)
            return SGX_ERROR_ECALL_NOT_ALLOWED;
        return SGX_SUCCESS;
    }
    ocall_context_t *context = reinterpret_cast<ocall_context_t*>(thread_data->last_sp);
    if(context->ocall_flag != OCALL_FLAG)
    {
        // abort the enclave if ocall frame is invalid
        abort();
    }
    uintptr_t ocall_index = context->ocall_index;
    if(ocall_index >= g_dyn_entry_table.nr_ocall)
    {
        return SGX_ERROR_INVALID_FUNCTION;
    }
    return (g_dyn_entry_table.entry_table[ocall_index * g_ecall_table.nr_ecall + ordinal] ? SGX_SUCCESS : SGX_ERROR_ECALL_NOT_ALLOWED);
}
// get_func_addr()
//      Get the address of ecall function from the ecall table
// Parameters:
//      [IN] ordinal - the index of the ecall function in the ecall table
// Return Value:
//      non-zero - success
//      zero - fail
//
static sgx_status_t get_func_addr(uint32_t ordinal, void **addr)
{
    sgx_status_t status = is_ecall_allowed(ordinal);
    if(SGX_SUCCESS != status)
    {
        return status;
    }

    *addr = const_cast<void *>(g_ecall_table.ecall_table[ordinal].ecall_addr);
    if(!sgx_is_within_enclave(*addr, 0))
    {
        return SGX_ERROR_UNEXPECTED;
    }

    return SGX_SUCCESS;
}

static volatile bool           g_is_first_ecall = true;
static volatile sgx_spinlock_t g_ife_lock       = SGX_SPINLOCK_INITIALIZER;

typedef sgx_status_t (*ecall_func_t)(void *ms);
static sgx_status_t trts_ecall(uint32_t ordinal, void *ms)
{
    if (unlikely(g_is_first_ecall))
    {
        // The thread performing the global initialization cannot do a nested ECall
        thread_data_t *thread_data = get_thread_data();
        if (thread_data->last_sp != thread_data->stack_base_addr)
        { // nested ecall
            return SGX_ERROR_ECALL_NOT_ALLOWED;
        }

        sgx_spin_lock(&g_ife_lock);
        if (g_is_first_ecall)
        {
            //invoke global object's construction
            init_global_object();
            g_is_first_ecall = false;
        }
        sgx_spin_unlock(&g_ife_lock);
    }

    void *addr = NULL;
    sgx_status_t status = get_func_addr(ordinal, &addr);
    if(status == SGX_SUCCESS)
    {
        ecall_func_t func = (ecall_func_t)addr;
        status = func(ms);
    }

    // clean extended registers, no need to save
    CLEAN_XFEATURE_REGS
    return status;
}

extern "C" sgx_status_t do_init_thread(void *tcs);
sgx_status_t do_ecall(int index, void *ms, void *tcs)
{
    sgx_status_t status = SGX_ERROR_UNEXPECTED;
    if(ENCLAVE_INIT_DONE != get_enclave_state())
    {
        return status;
    }
    thread_data_t *thread_data = get_thread_data();
    if( (NULL == thread_data) || ((thread_data->stack_base_addr == thread_data->last_sp) && (0 != g_global_data.thread_policy)))
    {
        status = do_init_thread(tcs);
        if(0 != status)
        {
            return status;
        }
    }
    status = trts_ecall(index, ms);
    return status;
}

