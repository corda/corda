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

/* Implement functions:
 *         init_stack_guard() 
 *         do_init_thread()
 *
 *  The functions in this source file will be called during the stack guard initialization.
 *  They cannot be built with '-fstack-protector-strong'. Otherwise, stack guard check will
 *  be failed before the function returns and 'ud2' will be triggered. 
*/

#include "sgx_trts.h"
#include "trts_inst.h"
#include "se_memcpy.h"
#include <string.h>
#include <stdlib.h>
#include "thread_data.h"
#include "global_data.h"
#include "trts_internal.h"

#include "linux/elf_parser.h"
#define GET_TLS_INFO  elf_tls_info

static void init_stack_guard(void)
{
    thread_data_t *thread_data = get_thread_data();
    assert(thread_data != NULL);

    size_t tmp_stack_guard = 0;
    if (SGX_SUCCESS != sgx_read_rand(
                (unsigned char*)&tmp_stack_guard,
                sizeof(tmp_stack_guard)))
        abort();

    thread_data->stack_guard = tmp_stack_guard;
}

extern "C" sgx_status_t do_init_thread(void *tcs)
{
    thread_data_t *thread_data = GET_PTR(thread_data_t, tcs, g_global_data.td_template.self_addr);
    memcpy_s(thread_data, SE_PAGE_SIZE, const_cast<thread_data_t *>(&g_global_data.td_template), sizeof(thread_data_t));
    thread_data->last_sp += (size_t)tcs;
    thread_data->self_addr += (size_t)tcs;
    thread_data->stack_base_addr += (size_t)tcs;
    thread_data->stack_limit_addr += (size_t)tcs;
    thread_data->first_ssa_gpr += (size_t)tcs;
    thread_data->tls_array += (size_t)tcs;
    thread_data->tls_addr += (size_t)tcs;

    thread_data->last_sp -= (size_t)STATIC_STACK_SIZE;
    thread_data->stack_base_addr -= (size_t)STATIC_STACK_SIZE;

    uintptr_t tls_addr = 0;
    size_t tdata_size = 0;

    if(0 != GET_TLS_INFO(&__ImageBase, &tls_addr, &tdata_size))
    {
        return SGX_ERROR_UNEXPECTED;
    }
    if(tls_addr)
    {
        memset((void *)TRIM_TO_PAGE(thread_data->tls_addr), 0, ROUND_TO_PAGE(thread_data->self_addr - thread_data->tls_addr));
        memcpy_s((void *)(thread_data->tls_addr), thread_data->self_addr - thread_data->tls_addr, (void *)tls_addr, tdata_size);
    }
    init_stack_guard();
    return SGX_SUCCESS;
}

