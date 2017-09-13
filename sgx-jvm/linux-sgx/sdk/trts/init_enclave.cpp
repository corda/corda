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
 * File: init_enclave.cpp
 * Description:
 *     Initialize enclave by rebasing the image to the enclave base 
 */

#include <string.h>
#include "thread_data.h"
#include "global_data.h"
#include "util.h"
#include "xsave.h"
#include "sgx_trts.h"
#include "init_optimized_lib.h"
#include "trts_internal.h"
#  include "linux/elf_parser.h"
#include "rts.h"

// The global cpu feature bits from uRTS
uint64_t g_cpu_feature_indicator = 0;

const volatile global_data_t g_global_data = {1, 2, 3, 4, 0, 
   {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, {0, 0, 0, 0, 0, 0}}};
uint32_t g_enclave_state = ENCLAVE_INIT_NOT_STARTED;

extern "C" {
uintptr_t __stack_chk_guard = 0;
#define __weak_alias(alias,sym)                 \
    __asm__(".weak " __STRING(alias) " ; "      \
        __STRING(alias) " = " __STRING(sym))
__weak_alias(__intel_security_cookie, __stack_chk_guard);
}

// init_enclave()
//      Initialize enclave.
// Parameters:
//      [IN] enclave_base - the enclave base address
//      [IN] ms - the marshalling structure passed by uRTS
// Return Value:
//       0 - success
//      -1 - fail
//
extern "C" int init_enclave(void *enclave_base, void *ms)
{
    if(enclave_base == NULL || ms == NULL)
    {
        return -1;
    }

    // relocation
    if(0 != relocate_enclave(enclave_base))
    {
        return -1;
    }

    // Check if the ms is outside the enclave.
    // sgx_is_outside_enclave() should be placed after relocate_enclave()
    cpu_sdk_info_t *info = (cpu_sdk_info_t *)ms;
    if(!sgx_is_outside_enclave(info, sizeof(cpu_sdk_info_t)))
    {
        return -1;
    }
    const sdk_version_t sdk_version = info->version;
    const uint64_t cpu_features = info->cpu_features;
    
    if (sdk_version != SDK_VERSION_1_5)
        return -1;

    // xsave
    uint64_t xfrm = get_xfeature_state();

    // optimized libs
    if(0 != init_optimized_libs(cpu_features, xfrm))
    {
        CLEAN_XFEATURE_REGS
        return -1;
    }

    if(SGX_SUCCESS != sgx_read_rand((unsigned char*)&__stack_chk_guard,
                                     sizeof(__stack_chk_guard)))
    {
        CLEAN_XFEATURE_REGS
        return -1;
    }

    // clean extended registers, no need to save
    CLEAN_XFEATURE_REGS
    return 0;
}

sgx_status_t do_init_enclave(void *ms)
{
    void *enclave_base = get_enclave_base();
    if(ENCLAVE_INIT_NOT_STARTED != lock_enclave())
    {
        return SGX_ERROR_UNEXPECTED;
    }
    if(0 != init_enclave(enclave_base, ms))
    {
        return SGX_ERROR_UNEXPECTED;
    }
    memset(GET_PTR(void, enclave_base, g_global_data.heap_offset), 0, g_global_data.heap_size);
    g_enclave_state = ENCLAVE_INIT_DONE;
    return SGX_SUCCESS;
}

