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
#include "thread_data.h"
#include "global_data.h"
#include "sgx_edger8r.h"
#include "rts.h"
#include "util.h"
#include "trts_internal.h"

extern "C" sgx_status_t asm_oret(uintptr_t sp, void *ms);
extern "C" sgx_status_t __morestack(const unsigned int index, void *ms);
#define do_ocall __morestack

//
// sgx_ocall
// Parameters:
//      index - the index in the ocall table
//      ms - the mashalling structure
// Return Value:
//      OCALL status
//
sgx_status_t sgx_ocall(const unsigned int index, void *ms)
{
    // sgx_ocall is not allowed during exception handling
    thread_data_t *thread_data = get_thread_data();
    
    // we have exceptions being handled
    if(thread_data->exception_flag != 0) {
        return SGX_ERROR_OCALL_NOT_ALLOWED;
    }
    // the OCALL index should be within the ocall table range
    // -2, -3 and -4 should be allowed to test SDK 2.0 features
    if((index != 0) &&
            (index != (unsigned int)EDMM_TRIM) &&
            (index != (unsigned int)EDMM_TRIM_COMMIT) &&
            (index != (unsigned int)EDMM_MODPR) &&
            static_cast<size_t>(index) >= g_dyn_entry_table.nr_ocall)
    {
        return SGX_ERROR_INVALID_FUNCTION;
    }

    // do sgx_ocall
    sgx_status_t status = do_ocall(index, ms);

    return status;
}

extern "C"
uintptr_t update_ocall_lastsp(ocall_context_t* context)
{
    thread_data_t* thread_data = get_thread_data();

    uintptr_t last_sp = 0;

    last_sp = thread_data->last_sp;

    context->pre_last_sp = last_sp;

    if (context->pre_last_sp == thread_data->stack_base_addr)
    {
        context->ocall_depth = 1;
    } else {
        // thread_data->last_sp is only set when ocall or exception handling occurs
        // ocall is block during exception handling, so last_sp is always ocall frame here
        ocall_context_t* context_pre = reinterpret_cast<ocall_context_t*>(context->pre_last_sp);
        context->ocall_depth = context_pre->ocall_depth + 1;
    }

    thread_data->last_sp = reinterpret_cast<uintptr_t>(context);

    return last_sp;
}

sgx_status_t do_oret(void *ms)
{
    thread_data_t *thread_data = get_thread_data();
    uintptr_t last_sp = thread_data->last_sp;
    ocall_context_t *context = reinterpret_cast<ocall_context_t*>(thread_data->last_sp);
    if(0 == last_sp || last_sp <= (uintptr_t)&context)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    // At least 1 ecall frame and 1 ocall frame are expected on stack. 
    // 30 is an estimated value: 8 for enclave_entry and 22 for do_ocall.
    if(last_sp > thread_data->stack_base_addr - 30 * sizeof(size_t))
    {
        return SGX_ERROR_UNEXPECTED;
    }
    if(context->ocall_flag != OCALL_FLAG)
    {
        return SGX_ERROR_UNEXPECTED;
    }
    if(context->pre_last_sp > thread_data->stack_base_addr
       || context->pre_last_sp <= (uintptr_t)context)
    {
        return SGX_ERROR_UNEXPECTED;
    }

    thread_data->last_sp = context->pre_last_sp;
    asm_oret(last_sp, ms);
    
    // Should not come here
    return SGX_ERROR_UNEXPECTED;
}

