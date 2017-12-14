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


#include "sgx_tcrypto.h"
#include "ippcp.h"
#include "ippcore.h"
#include "se_cpu_feature.h"
#include "se_cdefs.h"

// add a version to tcrypto.
SGX_ACCESS_VERSION(tcrypto, 1)


/* Crypto Library Initialization
* Parameters:
* 	Return: sgx_status_t  - SGX_SUCCESS or failure as defined sgx_error.h
*	Inputs: uint64_t cpu_feature_indicator - Bit array of host CPU feature bits */
extern "C" sgx_status_t sgx_init_crypto_lib(uint64_t cpu_feature_indicator)
{
    IppStatus error_code = ippStsNoOperation;

    // Use cpu_feature_indicator to determine the host CPU and specify that CPU type
    // in the initialization of the IPP dispatcher.
    // NOTE: Only 2 ISA Optimized Algorithms are utilized:
    //       1. AVX2
    //       2. SSE4.1
    //  We set SSE4.1 as the baseline.
    // Set the IPP feature bits based on host attributes that have been collected
    // NOTE: Some sanity check
    Ipp64u ippCpuFeatures = 0;
    if ((cpu_feature_indicator & CPU_FEATURE_SSE4_1) == CPU_FEATURE_SSE4_1)
    {
        // Some sanity checking has been performed when setting the feature mask
        // If SSE4.1 is set, then all earlier SSE/MMX ISA enhancements are available
        ippCpuFeatures |= (ippCPUID_SSE41 | ippCPUID_MMX | ippCPUID_SSE |
            ippCPUID_SSE2 | ippCPUID_SSE3 | ippCPUID_SSSE3);
        if ((cpu_feature_indicator & CPU_FEATURE_MOVBE) == CPU_FEATURE_MOVBE)
        {
            ippCpuFeatures |= ippCPUID_MOVBE;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_SSE4_2) == CPU_FEATURE_SSE4_2)
        {
            ippCpuFeatures |= ippCPUID_SSE42;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_AVX) == CPU_FEATURE_AVX)
        {
            ippCpuFeatures |= ippCPUID_AVX;
            ippCpuFeatures |= ippAVX_ENABLEDBYOS;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_AES) == CPU_FEATURE_AES)
        {
            ippCpuFeatures |= ippCPUID_AES;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_PCLMULQDQ) == CPU_FEATURE_PCLMULQDQ)
        {
            ippCpuFeatures |= ippCPUID_CLMUL;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_RDRND) == CPU_FEATURE_RDRND)
        {
            ippCpuFeatures |= ippCPUID_RDRAND;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_F16C) == CPU_FEATURE_F16C)
        {
            ippCpuFeatures |= ippCPUID_F16C;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_AVX2) == CPU_FEATURE_AVX2)
        {
            ippCpuFeatures |= ippCPUID_AVX2;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_ADCOX) == CPU_FEATURE_ADCOX)
        {
            ippCpuFeatures |= ippCPUID_ADCOX;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_RDSEED) == CPU_FEATURE_RDSEED)
        {
            ippCpuFeatures |= ippCPUID_RDSEED;
        }
        if ((cpu_feature_indicator & CPU_FEATURE_PREFETCHW) == CPU_FEATURE_PREFETCHW)
        {
            ippCpuFeatures |= ippCPUID_PREFETCHW;
        }
    }
    else
    {
        // Return error if the old platoform has no SSE4.1
        return SGX_ERROR_INVALID_PARAMETER;

    }

    // Call SetCpuFeatures() to set the IPP library with the collected CPU features
    ippCpuFeatures |= ippCPUID_NOCHECK; /* Force ippSetCpuFeatures to set CPU features without check */
    error_code = ippSetCpuFeatures(ippCpuFeatures);
    if (error_code != ippStsNoErr)
    {
        return SGX_ERROR_INVALID_PARAMETER;
    }
    return SGX_SUCCESS;
}

