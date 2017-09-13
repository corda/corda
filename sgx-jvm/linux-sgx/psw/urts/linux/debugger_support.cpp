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


#include "debugger_support.h"
#include "se_trace.h"
#include "util.h"
#include "se_debugger_lib.h"

#include "rts.h"
#include "thread_data.h"
#include "se_memory.h"
#include "enclave.h"
#include <string.h>
#include <util.h>
#include <pthread.h>

#define fastcall __attribute__((regparm(3)))

extern "C" void fastcall sgx_debug_load_state_add_element(const debug_enclave_info_t *enclave_info, debug_enclave_info_t** g_debug_enclave_info_list);
extern "C" void fastcall sgx_debug_unload_state_remove_element(const debug_enclave_info_t *enclave_info, debug_enclave_info_t** pre_enclave_info, debug_enclave_info_t* next_enclave_info);

static pthread_mutex_t g_debug_info_mutex = PTHREAD_MUTEX_INITIALIZER;
debug_enclave_info_t *g_debug_enclave_info_list = NULL;

// There is no need to add lock here:
//     This function only be called by CEnclave::add_thread and the enclave_info is a member
//     of CEnclave instance. There is no race condition
void insert_debug_tcs_info_head(debug_enclave_info_t* enclave_info, debug_tcs_info_t* tcs_info)
{
    tcs_info->next_tcs_info = enclave_info->tcs_list;
    enclave_info->tcs_list = tcs_info;
}

static void insert_debug_info_head(debug_enclave_info_t *enclave_info)
{
    enclave_info->next_enclave_info = g_debug_enclave_info_list;
    //g_debug_enclave_info_list = enclave_info;
    //To avoid the race between attach event and load event, we set load event bp at where the list is changed
    sgx_debug_load_state_add_element(enclave_info, &g_debug_enclave_info_list);
}

static void remove_debug_info(debug_enclave_info_t *enclave_info)
{
    debug_enclave_info_t **pre_entry = &g_debug_enclave_info_list;
    debug_enclave_info_t *cur = g_debug_enclave_info_list;

    while(cur)
    {
        if(cur == enclave_info)
        {
            //*pre_entry = cur->next_enclave_info;
            //To avoid the race between attach event and unload event, we set unload event bp at where the list is changed
            sgx_debug_unload_state_remove_element(enclave_info, pre_entry, cur->next_enclave_info);
            break;
        }
        pre_entry = &cur->next_enclave_info;
        cur = cur->next_enclave_info;
    }
}

void generate_enclave_debug_event(uint32_t code, const debug_enclave_info_t* enclave_info)
{
    if(URTS_EXCEPTION_POSTINITENCLAVE == code)
    {
        if(pthread_mutex_lock(&g_debug_info_mutex) != 0)
            abort();
        insert_debug_info_head(const_cast<debug_enclave_info_t *>(enclave_info));
        if(pthread_mutex_unlock(&g_debug_info_mutex) != 0)
            abort();
    }
    else if(URTS_EXCEPTION_PREREMOVEENCLAVE == code)
    {
        if(pthread_mutex_lock(&g_debug_info_mutex) != 0)
            abort();
        remove_debug_info(const_cast<debug_enclave_info_t *>(enclave_info));
        if(pthread_mutex_unlock(&g_debug_info_mutex) != 0)
            abort();
    }
}


extern "C" void fastcall notify_gdb_to_update(void* base, tcs_t* tcs, uintptr_t of)
{
    UNUSED(base);
    UNUSED(tcs);
    UNUSED(of);
}

extern "C" void push_ocall_frame(uintptr_t frame_point, tcs_t* tcs, CTrustThread *trust_thread)
{
    assert(trust_thread != NULL);
    CEnclave* enclave = trust_thread->get_enclave();
    assert(enclave != NULL);
    enclave->push_ocall_frame(container_of(frame_point, ocall_frame_t, xbp), trust_thread);
    notify_gdb_to_update(enclave->get_start_address(), tcs, (uintptr_t)container_of(frame_point, ocall_frame_t, xbp));
}

extern "C" void pop_ocall_frame(tcs_t* tcs, CTrustThread *trust_thread)
{
    UNUSED(tcs);
    assert(trust_thread != NULL);
    CEnclave* enclave = trust_thread->get_enclave();
    assert(enclave != NULL);
    enclave->pop_ocall_frame(trust_thread);
}
