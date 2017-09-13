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

#ifndef _SE_CPU_FEATURE_DEFS_H_
#define _SE_CPU_FEATURE_DEFS_H_

/*
 * Different extended model + model values for Silverthorn.
 */
#define CPU_ATOM1          0x1c
#define CPU_ATOM2          0x26
#define CPU_ATOM3          0x27

/*
 * The processor family is an 8-bit value obtained by adding the
 * Extended Family field of the processor signature returned by
 * CPUID Function 1 with the Family field.
 * F = (CPUID(1).EAX[27:20] >> 20) + (CPUID(1).EAX[11:8] >> 8)
 */
#define CPU_FAMILY(x)     (((((x) >> 20) & 0xffU) | (((x) >> 8) & 0xfU)) & 0xffU)

/* The processor model is an 8-bit value obtained by shifting left 4
 * the Extended Model field of the processor signature returned by
 * CPUID Function 1 then adding the Model field.
 * M = (CPUID(1).EAX[19:16] >> 12) + (CPUID(1).EAX[7:4] >> 4)
 */
#define CPU_MODEL(x)      ((((x) >> 12) & 0xf0U)  | (((x) >> 4) & 0xfU))
#define CPU_STEPPING(x)   (((x) >> 0) & 0xf)

#define CPU_HAS_MMX(x)    (((x) & (1 << 23)) != 0)
#define CPU_HAS_FXSAVE(x) (((x) & (1 << 24)) != 0)
#define CPU_HAS_SSE(x)    (((x) & (1 << 25)) != 0)
#define CPU_HAS_SSE2(x)   (((x) & (1 << 26)) != 0)
#define CPU_HAS_PNI(x)    (((x) & (1 << 0)) != 0)
#define CPU_HAS_MNI(x)    (((x) & (1 << 9)) != 0)
#define CPU_HAS_SNI(x)    (((x) & (1 << 19)) != 0)
#define CPU_HAS_MOVBE(x)  (((x) & (1 << 22)) != 0)
#define CPU_HAS_SSE4_2(x) (((x) & (1 << 20)) != 0)
#define CPU_HAS_POPCNT(x) (((x) & (1 << 23)) != 0)
#define CPU_HAS_PCLMULQDQ(x) (((x) & (1 <<  1)) != 0)
#define CPU_HAS_AES(x)       (((x) & (1 << 25)) != 0)
#define CPU_HAS_XSAVE(x)  (((x) & (1 << 27)) != 0)
#define CPU_HAS_AVX(x)    (((x) & (1 << 28)) != 0)
#define XFEATURE_ENABLED_AVX(x) \
    (((x) & 0x06) == 0x06)
#define CPU_HAS_F16C(x)   (((x) & (1 << 29)) != 0)
#define CPU_HAS_RDRAND(x) (((x) & (1 << 30)) != 0)
#define CPU_HAS_IVB(x)    (CPU_HAS_F16C(x) && CPU_HAS_RDRAND(x))
#define CPU_HAS_IVB_NORDRAND(x)  (CPU_HAS_F16C(x))
#define CPU_HAS_AVX2(x)                 (((x) & (1 << 5)) != 0)
#define CPU_HAS_HLE(x)                  (((x) & (1 << 4)) != 0)
#define CPU_HAS_RTM(x)                  (((x) & (1 << 11)) != 0)
#define CPU_HAS_ADCOX(x)                (((x) & (1 << 19)) != 0)
#define CPU_HAS_RDSEED(x)               (((x) & (1 << 18)) != 0)
#define CPU_HAS_BMI(x)                  (((x) & (1 << 3)) != 0 && \
    ((x) & (1 << 8)) != 0)
#define CPU_HAS_LZCNT(x)                (((x) & (1 << 5)) != 0)
#define CPU_HAS_PREFETCHW(x)            (((x) & (1 << 8)) != 0)
#define CPU_HAS_FMA(x)                  (((x) & (1 << 12)) != 0)
#define CPU_HAS_HSW(cpuid7_ebx, ecpuid1_ecx, cpuid1_ecx) \
    (CPU_HAS_AVX2(cpuid7_ebx) && CPU_HAS_BMI(cpuid7_ebx) && \
    CPU_HAS_LZCNT(ecpuid1_ecx) && CPU_HAS_FMA(cpuid1_ecx) && \
    CPU_HAS_HLE(cpuid7_ebx) && CPU_HAS_RTM(cpuid7_ebx))

#define CPU_HAS_FPU(x)          (((x) & (1 << 0)) != 0)
#define CPU_HAS_CMOV(x)         (((x) & (1 << 15)) != 0)

#define CPU_HAS_SSE3(x)         (((x) & (1 << 0)) != 0)
#define CPU_HAS_SSSE3(x)        (((x) & (1 << 9)) != 0)

#define CPU_HAS_SSE4_1(x)       (((x) & (1 << 19)) != 0)

#define CPU_HAS_LRBNI(x)        (((x) & (1 << 1)) != 0)
#define CPU_HAS_LRB2(x)         (((x) & (1 << 4)) != 0)


#define CPU_GENU_VAL      ('G' << 0 | 'e' << 8 | 'n' << 16 | 'u' << 24)
#define CPU_INEI_VAL      ('i' << 0 | 'n' << 8 | 'e' << 16 | 'I' << 24)
#define CPU_NTEL_VAL      ('n' << 0 | 't' << 8 | 'e' << 16 | 'l' << 24)

/*
 * These values must be in sync with dev/proton/globals/glob_cpu_info.c
 * c_legacy_cpu_set_xxx constants.
 */
#define CPU_GENERIC             0x1
#define CPU_PENTIUM             0x2
#define CPU_PENTIUM_PRO         0x4
#define CPU_PENTIUM_MMX         0x8
#define CPU_PENTIUM_II          0x10
#define CPU_PENTIUM_II_FXSV     0x20
#define CPU_PENTIUM_III         0x40
#define CPU_PENTIUM_III_SSE     0x80
#define CPU_PENTIUM_4           0x100
#define CPU_PENTIUM_4_SSE2      0x200
#define CPU_BNI                 0x400
#define CPU_PENTIUM_4_PNI       0x800
#define CPU_MNI                 0x1000
#define CPU_SNI                 0x2000
#define CPU_BNL                 0x4000
#define CPU_NHM                 0x8000
#define CPU_WSM                 0x10000
#define CPU_SNB                 0x20000
#define CPU_IVB                 0x40000
#define CPU_HSW                 0x400000

#define CPU_PENTIUM_FAMILY 5
#define CPU_PPRO_FAMILY    6
#define CPU_WMT_FAMILY     15

/*
 * The processor is a generic IA32 CPU
 */
#define CPU_FEATURE_GENERIC_IA32        0x00000001ULL

/*
 * Floating point unit is on-chip.
 */
#define CPU_FEATURE_FPU                 0x00000002ULL

/*
 * Conditional mov instructions are supported.
 */
#define CPU_FEATURE_CMOV                0x00000004ULL

/*
 * The processor supports the MMX technology instruction set extensions
 * to Intel Architecture.
 */
#define CPU_FEATURE_MMX                 0x00000008ULL

/*
 * The FXSAVE and FXRSTOR instructions are supported for fast
 * save and restore of the floating point context.
 */
#define CPU_FEATURE_FXSAVE              0x00000010ULL

/*
 * Indicates the processor supports the Streaming SIMD Extensions Instructions.
 */
#define CPU_FEATURE_SSE                 0x00000020ULL

/*
 * Indicates the processor supports the Streaming SIMD
 * Extensions 2 Instructions.
 */
#define CPU_FEATURE_SSE2                0x00000040ULL

/*
 * Indicates the processor supports the Streaming SIMD
 * Extensions 3 Instructions. (PNI)
 */
#define CPU_FEATURE_SSE3                0x00000080ULL

/*
 * The processor supports the Supplemental Streaming SIMD Extensions 3
 * instructions. (MNI)
 */
#define CPU_FEATURE_SSSE3               0x00000100ULL

/*
 * The processor supports the Streaming SIMD Extensions 4.1 instructions.(SNI)
 */
#define CPU_FEATURE_SSE4_1              0x00000200ULL

/*
 * The processor supports the Streaming SIMD Extensions 4.1 instructions.
 * (NNI + STTNI)
 */
#define CPU_FEATURE_SSE4_2              0x00000400ULL


/*
 * The processor supports POPCNT instruction.
 */
#define CPU_FEATURE_POPCNT              0x00000800ULL

/*
 * The processor supports MOVBE instruction.
 */
#define CPU_FEATURE_MOVBE               0x00001000ULL

/*
 * The processor supports PCLMULQDQ instruction.
 */
#define CPU_FEATURE_PCLMULQDQ           0x00002000ULL

/*
 * The processor supports instruction extension for encryption.
 */
#define CPU_FEATURE_AES                 0x00004000ULL

/*
 * The processor supports 16-bit floating-point conversions instructions.
 */
#define CPU_FEATURE_F16C                0x00008000ULL

/*
 * The processor supports AVX instruction extension.
 */
#define CPU_FEATURE_AVX                 0x00010000ULL

/*
 * The processor supports RDRND (read random value) instruction.
 */
#define CPU_FEATURE_RDRND               0x00020000ULL

/*
 * The processor supports FMA instructions.
 */
#define CPU_FEATURE_FMA                 0x00040000ULL

/*
 * The processor supports two groups of advanced bit manipulation extensions. - Haswell introduced, AVX2 related 
 */
#define CPU_FEATURE_BMI                 0x00080000ULL

/*
 * The processor supports LZCNT instruction (counts the number of leading zero
 * bits). - Haswell introduced
 */
#define CPU_FEATURE_LZCNT               0x00100000ULL

/*
 * The processor supports HLE extension (hardware lock elision). - Haswell introduced
 */
#define CPU_FEATURE_HLE                 0x00200000ULL

/*
 * The processor supports RTM extension (restricted transactional memory) - Haswell AVX2 related.
 */
#define CPU_FEATURE_RTM                 0x00400000ULL

/*
 * The processor supports AVX2 instruction extension.
 */
#define CPU_FEATURE_AVX2                0x00800000ULL

/*
 * The processor supports AVX512 instruction extension. 
 */
#define CPU_FEATURE_AVX512              0x01000000ULL

/*
 * The processor supports the PREFETCHW instruction.
 */
#define CPU_FEATURE_PREFETCHW           0x02000000ULL

/*
 * The processor supports RDSEED instruction.
 */
#define CPU_FEATURE_RDSEED              0x04000000ULL

/*
 * The processor supports ADCX and ADOX instructions.
 */
#define CPU_FEATURE_ADCOX               0x08000000ULL

/*
 * The processor is a full inorder (Silverthorne) processor
 */ 
#define CPU_FEATURE_FULL_INORDER        0x10000000ULL

/* Reserved feature bits which includes the unset bit CPU_FEATURE_AVX512 */
#define RESERVED_CPU_FEATURE_BIT        ((~(0x20000000ULL - 1)) | 0x01000000ULL)

#endif
