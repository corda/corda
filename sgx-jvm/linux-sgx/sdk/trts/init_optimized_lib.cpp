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


#include "init_optimized_lib.h"
#include <stdint.h>
#include "se_cpu_feature.h"
#include "sgx_trts.h"
#include "sgx_attributes.h"
#include "global_data.h"

extern "C" int sgx_init_string_lib(uint64_t cpu_feature_indicator);
extern "C" sgx_status_t sgx_init_crypto_lib(uint64_t cpu_feature_indicator);

static int set_global_feature_indicator(uint64_t feature_bit_array, uint64_t xfrm)
{
    // Confirm the reserved bits and the unset bits by uRTS must be 0.
    
    
    if(feature_bit_array & (RESERVED_CPU_FEATURE_BIT))
    {
        // clear the reserved bits
        feature_bit_array = feature_bit_array & (~(RESERVED_CPU_FEATURE_BIT));
    }

    // Requires SSE4.1. Take SSE4.1 as the baseline.
    if(!(feature_bit_array & ~(CPU_FEATURE_SSE4_1 - 1)))
    {
        return -1;
    }

    // Check for inconsistencies in the CPUID feature mask.
    if ( (((feature_bit_array & CPU_FEATURE_SSE) == CPU_FEATURE_SSE) &&((feature_bit_array & (CPU_FEATURE_SSE - 1)) != (CPU_FEATURE_SSE - 1))) || 
        (((feature_bit_array & CPU_FEATURE_SSE2) == CPU_FEATURE_SSE2) &&((feature_bit_array & (CPU_FEATURE_SSE2 - 1)) != (CPU_FEATURE_SSE2 - 1))) ||
        (((feature_bit_array & CPU_FEATURE_SSE3) == CPU_FEATURE_SSE3) &&((feature_bit_array & (CPU_FEATURE_SSE3 - 1)) != (CPU_FEATURE_SSE3 - 1))) ||
        (((feature_bit_array & CPU_FEATURE_SSSE3) == CPU_FEATURE_SSSE3) && ((feature_bit_array & (CPU_FEATURE_SSSE3 - 1)) != (CPU_FEATURE_SSSE3 - 1))) ||
        (((feature_bit_array & CPU_FEATURE_SSE4_1) == CPU_FEATURE_SSE4_1) && ((feature_bit_array & (CPU_FEATURE_SSE4_1 - 1)) != (CPU_FEATURE_SSE4_1 - 1))) ||
        (((feature_bit_array & CPU_FEATURE_SSE4_2) == CPU_FEATURE_SSE4_2) && ((feature_bit_array & (CPU_FEATURE_SSE4_2 - 1)) != (CPU_FEATURE_SSE4_2 - 1))) )
    {
        return -1;
    }

    // Determine whether the OS & ENCLAVE support SAVE/RESTORE of the AVX register set
    // IF NOT, clear the advanced feature set bits corresponding to AVX and beyond
    if(!XFEATURE_ENABLED_AVX(xfrm))
    {
        // AVX is disabled by OS, so clear the AVX related feature bits
        feature_bit_array &= (~(CPU_FEATURE_AVX | CPU_FEATURE_F16C | CPU_FEATURE_AVX2 | 
            CPU_FEATURE_FMA | CPU_FEATURE_RTM | CPU_FEATURE_HLE | CPU_FEATURE_BMI |
            CPU_FEATURE_PREFETCHW | CPU_FEATURE_RDSEED | CPU_FEATURE_ADCOX));
    }

    g_cpu_feature_indicator = feature_bit_array;
    return 0;
}

extern "C" int init_optimized_libs(const uint64_t feature_bit_array, uint64_t xfrm)
{
    if (g_enclave_state != ENCLAVE_INIT_IN_PROGRESS)
    {
        return -1;
    }
    // set the global feature indicator
    if(set_global_feature_indicator(feature_bit_array, xfrm))
    {
        return -1;
    }

    // Init string library with the global feature indicator
    if(sgx_init_string_lib(g_cpu_feature_indicator) != 0)
    {
        return -1;
    }

    // Init IPP crypto library with the global feature indicator	
    if(sgx_init_crypto_lib(g_cpu_feature_indicator) != 0)
    {
        return -1;
    }

    return 0;
}
