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

#ifndef _SEDEBUGGERLIB_H_ 
#define _SEDEBUGGERLIB_H_

#include "arch.h"
#include "se_types.h"
#include "se_macro.h"
#include <stdlib.h>

#define URTS_EXCEPTION_PRECREATEENCLAVE     0xa1a01ec0
#define URTS_EXCEPTION_POSTINITENCLAVE      0xa1a01ec1
#define URTS_EXCEPTION_PREREMOVEENCLAVE     0xa1a01ec3
#define URTS_EXCEPTION_PREEENTER            0xa1a01ec7

#define FIRST_CHANCE_EXCEPTION              1
#define SECOND_CHANCE_EXCEPTION             0

#define DBWIN_BUFFER                        0xa1a01ec5
#define CXX_EXCEPTION                       0xe06d7363

#define SE_UNICODE 1
#define SE_ANSI 0
#define DEBUGGER_ENABLED 1

#define DEBUG_INFO_STRUCT_VERSION 0x83d0ce23

const size_t BUF_SIZE = sizeof(void*);

typedef struct _debug_tcs_info_t
{
    struct _debug_tcs_info_t* next_tcs_info;
    void* TCS_address;
    uintptr_t ocall_frame; /* ocall_frame_t** */
    unsigned long thread_id;
}debug_tcs_info_t;


#define DEBUG_INFO_MAX_PARAMETERS 10
typedef struct _debug_info_t
{
    uintptr_t       param_array[DEBUG_INFO_MAX_PARAMETERS];
}debug_info_t;

//enclave_type bit map
#define ET_SIM_SHIFT        0       /*bits[0]=0 hw, bits[0]=1 sim*/
#define ET_DEBUG_SHIFT      1       /*bits[1]=0 product enclave, bits[1]=1 debug enclave*/
#define ET_SIM              (1 << ET_SIM_SHIFT)
#define ET_DEBUG            (1 << ET_DEBUG_SHIFT)

typedef struct _debug_enclave_info_t
{
    PADDED_POINTER(struct _debug_enclave_info_t, next_enclave_info);
    PADDED_POINTER(void, start_addr);
    PADDED_POINTER(debug_tcs_info_t, tcs_list);
    uint32_t enclave_type;
    uint32_t file_name_size;
    PADDED_POINTER(void, lpFileName);
    PADDED_POINTER(void, g_peak_heap_used_addr);
    PADDED_POINTER(void, dyn_sec);
    sgx_misc_select_t  misc_select;
    /* The following members are optional or unused */
    uint32_t struct_version;
    uint32_t unicode;
}debug_enclave_info_t; 
typedef struct _ocall_frame_t
{
    uintptr_t pre_last_frame;
    uintptr_t index;
    uintptr_t xbp;
    uintptr_t ret;
}ocall_frame_t;

static inline void destory_debug_info(debug_enclave_info_t *debug_info)
{
    if(debug_info->lpFileName)
    {
        free(debug_info->lpFileName);
        debug_info->lpFileName = NULL;
    }
    
    /*tcs_list is just a pointer, the instance is maintained in CTrustThread, so don't free it.*/
    debug_info->tcs_list = NULL;
}
#endif /*_SEDEBUGGERLIB_H_*/

