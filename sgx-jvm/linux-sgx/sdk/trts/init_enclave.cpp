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
#include "linux/elf_parser.h"
#include "rts.h"
#include "trts_util.h"
#include "se_memcpy.h"

// The global cpu feature bits from uRTS
uint64_t g_cpu_feature_indicator = 0;
int EDMM_supported = 0;
sdk_version_t g_sdk_version = SDK_VERSION_1_5;

const volatile global_data_t g_global_data = {1, 2, 3, 4,  
   {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, {0, 0, 0, 0, 0, 0}, 0}, {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0, 0, {{{0, 0, 0, 0, 0, 0, 0}}}};
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
    system_features_t *info = (system_features_t *)ms;
    if(!sgx_is_outside_enclave(info, sizeof(system_features_t)))
    {
        return -1;
    }

    const system_features_t sys_features = *info;
    g_sdk_version = sys_features.version;
    if (g_sdk_version == SDK_VERSION_1_5)
    {
        EDMM_supported = 0;
    }
    else if (g_sdk_version == SDK_VERSION_2_0)
    {
        EDMM_supported = feature_supported((const uint64_t *)sys_features.system_feature_set, 0);
    }
    else
    {
        return -1;
    }

    if (heap_init(get_heap_base(), get_heap_size(), get_heap_min_size(), EDMM_supported) != SGX_SUCCESS)
        return -1;

    // xsave
    uint64_t xfrm = get_xfeature_state();

    // optimized libs
    if(0 != init_optimized_libs((const uint64_t)sys_features.cpu_features, xfrm))
    {
        return -1;
    }

    if(SGX_SUCCESS != sgx_read_rand((unsigned char*)&__stack_chk_guard,
                                     sizeof(__stack_chk_guard)))
    {
        return -1;
    }

    return 0;
}

#ifndef SE_SIM
int accept_post_remove(const volatile layout_t *layout_start, const volatile layout_t *layout_end, size_t offset);
#endif

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

#ifndef SE_SIM
    /* for EDMM, we need to accept the trimming of the POST_REMOVE pages. */
    if (EDMM_supported)
    {
        if (0 != accept_post_remove(&g_global_data.layout_table[0], &g_global_data.layout_table[0] + g_global_data.layout_entry_num, 0))
            return SGX_ERROR_UNEXPECTED;

        size_t heap_min_size = get_heap_min_size();
        memset_s(GET_PTR(void, enclave_base, g_global_data.heap_offset), heap_min_size, 0, heap_min_size);
    }
    else
#endif
    {
        memset_s(GET_PTR(void, enclave_base, g_global_data.heap_offset), g_global_data.heap_size, 0, g_global_data.heap_size);	
    }

    g_enclave_state = ENCLAVE_INIT_DONE;
    return SGX_SUCCESS;
}

