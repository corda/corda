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



#include "se_cpu_feature_defs.h"
#include "se_types.h"
#include "cpu_features.h"

void get_cpu_features(uint64_t *__intel_cpu_feature_indicator)
{
    unsigned int cpuid0_eax, cpuid0_ebx, cpuid0_ecx, cpuid0_edx;
    unsigned int cpuid1_eax, cpuid1_ebx, cpuid1_ecx, cpuid1_edx;
    unsigned int cpuid7_eax, cpuid7_ebx, cpuid7_ecx, cpuid7_edx;
    unsigned int ecpuid1_eax, ecpuid1_ebx, ecpuid1_ecx, ecpuid1_edx;
    uint64_t cpu_feature_indicator = CPU_FEATURE_GENERIC_IA32;

    sgx_cpuid(0, &cpuid0_eax, &cpuid0_ebx, &cpuid0_ecx, &cpuid0_edx);
    if(cpuid0_eax == 0 ||
            !(cpuid0_ebx == CPU_GENU_VAL &&
              cpuid0_edx == CPU_INEI_VAL &&
              cpuid0_ecx == CPU_NTEL_VAL))
    {
        *__intel_cpu_feature_indicator = cpu_feature_indicator;
        return;
    }

    sgx_cpuid(1, &cpuid1_eax, &cpuid1_ebx, &cpuid1_ecx, &cpuid1_edx);
    if (CPU_MODEL(cpuid1_eax) == CPU_ATOM1 ||
            CPU_MODEL(cpuid1_eax) == CPU_ATOM2 ||
            CPU_MODEL(cpuid1_eax) == CPU_ATOM3) {
        cpu_feature_indicator |= CPU_FEATURE_FULL_INORDER;
    }

    // Walk through supported features
    if (CPU_HAS_FPU(cpuid1_edx)) {
        cpu_feature_indicator |= CPU_FEATURE_FPU;
    }

    if (CPU_HAS_CMOV(cpuid1_edx)) {
        cpu_feature_indicator |= CPU_FEATURE_CMOV;
    }

    if (CPU_HAS_MMX(cpuid1_edx)) {
        cpu_feature_indicator |= CPU_FEATURE_MMX;
    }

    if (CPU_HAS_FXSAVE(cpuid1_edx)) {
        cpu_feature_indicator |= CPU_FEATURE_FXSAVE;

        if (CPU_HAS_SSE(cpuid1_edx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSE;
        }
        if (CPU_HAS_SSE2(cpuid1_edx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSE2;
        }
        if (CPU_HAS_SSE3(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSE3;
        }

        if (CPU_HAS_SSSE3(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSSE3;
        }

        if (CPU_HAS_MOVBE(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_MOVBE;
        }
        //
        // Penryn is a P6 with SNI support.
        //
        if (CPU_HAS_SSE4_1(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSE4_1;
        }
        if (CPU_HAS_SSE4_2(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_SSE4_2;
        }

        if (CPU_HAS_POPCNT(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_POPCNT;
        }
        if (CPU_HAS_PCLMULQDQ(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_PCLMULQDQ;
        }

        if (CPU_HAS_AES(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_AES;
        }
    }

    // IvyBridge
    if (CPU_HAS_RDRAND(cpuid1_ecx)) {
        cpu_feature_indicator |= CPU_FEATURE_RDRND;
    }

    sgx_cpuidex(7, 0, &cpuid7_eax, &cpuid7_ebx, &cpuid7_ecx, &cpuid7_edx);
    sgx_cpuid(0x80000001, &ecpuid1_eax, &ecpuid1_ebx, &ecpuid1_ecx, &ecpuid1_edx);

    // Haswell
    // BMI checks for both ebx[3] and ebx[8] (VEX-encoded instructions)
    if (CPU_HAS_BMI(cpuid7_ebx)) {
        cpu_feature_indicator |= CPU_FEATURE_BMI;
    }
    if (CPU_HAS_LZCNT(ecpuid1_ecx)) {
        cpu_feature_indicator |= CPU_FEATURE_LZCNT;
    }
    if (CPU_HAS_PREFETCHW(ecpuid1_ecx)) {
        cpu_feature_indicator |= CPU_FEATURE_PREFETCHW;
    }
    if (CPU_HAS_HLE(cpuid7_ebx)) {
        cpu_feature_indicator |= CPU_FEATURE_HLE;
    }
    if (CPU_HAS_RTM(cpuid7_ebx)) {
        cpu_feature_indicator |= CPU_FEATURE_RTM;
    }
    if (CPU_HAS_RDSEED(cpuid7_ebx)) {
        cpu_feature_indicator |= CPU_FEATURE_RDSEED;
    }
    if (CPU_HAS_ADCOX(cpuid7_ebx)) {
        cpu_feature_indicator |= CPU_FEATURE_ADCOX;
    }

    if (CPU_HAS_XSAVE(cpuid1_ecx))
    {
        //don't get xcr0_features, tRTS will do it.
        if (CPU_HAS_AVX(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_AVX;
        }

        // IvyBridge
        if (CPU_HAS_F16C(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_F16C;
        }

        // Haswell
        if (CPU_HAS_AVX2(cpuid7_ebx)) {
            cpu_feature_indicator |= CPU_FEATURE_AVX2;
        }
        if (CPU_HAS_FMA(cpuid1_ecx)) {
            cpu_feature_indicator |= CPU_FEATURE_FMA;
        }
    }
    

    *__intel_cpu_feature_indicator = cpu_feature_indicator;
}
