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

#include "stdint.h"
#include "se_cpu_feature.h"
#include "se_cdefs.h"

// add a version to tlibc.
SGX_ACCESS_VERSION(tstdc, 1)

#ifdef _TLIBC_USE_INTEL_FAST_STRING_
extern uint64_t __intel_cpu_feature_indicator;
extern uint64_t __intel_cpu_feature_indicator_x;
extern unsigned int __intel_cpu_indicator;

static int _intel_cpu_indicator_init(uint64_t cpu_feature_bits)
{
    // We have the assumption that SSE3 is the lowest feature for enclave loading,
    // so failure will be returned if features are all below-SSE3.

    __intel_cpu_indicator = CPU_GENERIC;

    if ((cpu_feature_bits & CPU_FEATURE_AVX2) &&
        (cpu_feature_bits & CPU_FEATURE_FMA) &&
        (cpu_feature_bits & CPU_FEATURE_BMI) &&
        (cpu_feature_bits & CPU_FEATURE_LZCNT) &&
        (cpu_feature_bits & CPU_FEATURE_HLE) &&
        (cpu_feature_bits & CPU_FEATURE_RTM)) {

        __intel_cpu_indicator = CPU_HSW;
    }
    else if (cpu_feature_bits & CPU_FEATURE_F16C) {
        __intel_cpu_indicator = CPU_IVB;
    }
    else if (cpu_feature_bits & CPU_FEATURE_AVX) {
        __intel_cpu_indicator = CPU_SNB;
    }
    else if ((cpu_feature_bits & CPU_FEATURE_PCLMULQDQ) &&
        (cpu_feature_bits & CPU_FEATURE_AES)) {
            __intel_cpu_indicator = CPU_WSM;
    }
    else if ((cpu_feature_bits & CPU_FEATURE_SSE4_2) &&
        (cpu_feature_bits & CPU_FEATURE_POPCNT)) {
            __intel_cpu_indicator = CPU_NHM;
    }
    else if (cpu_feature_bits & CPU_FEATURE_SSE4_1) {
        __intel_cpu_indicator = CPU_SNI;
    }
    else if (cpu_feature_bits & CPU_FEATURE_MOVBE) {
        __intel_cpu_indicator = CPU_BNL;
    }
    else if (cpu_feature_bits & CPU_FEATURE_SSSE3) {
        __intel_cpu_indicator = CPU_MNI;
    }
    else if (cpu_feature_bits & CPU_FEATURE_SSE3) {
        __intel_cpu_indicator = CPU_PENTIUM_4_PNI;
    }
    else if (cpu_feature_bits & CPU_FEATURE_SSE2) {
        __intel_cpu_indicator = CPU_BNI;
    }
    else if (cpu_feature_bits & CPU_FEATURE_SSE) {
        __intel_cpu_indicator = CPU_PENTIUM_III_SSE;
    }
    else {
        return -1;
    }
    return 0;
}


int sgx_init_string_lib(uint64_t cpu_feature_indicator)
{
    int genuine_intel = (cpu_feature_indicator & ~(CPU_FEATURE_GENERIC_IA32));

    if(genuine_intel) {
        __intel_cpu_feature_indicator = __intel_cpu_feature_indicator_x = cpu_feature_indicator;
    }
    else {
        __intel_cpu_feature_indicator = __intel_cpu_feature_indicator_x = CPU_FEATURE_GENERIC_IA32;
    }

    return _intel_cpu_indicator_init(cpu_feature_indicator);
}

#else
int sgx_init_string_lib(uint64_t cpu_feature_indicator)
{
    (void)cpu_feature_indicator; 
    return 0;
}
#endif
