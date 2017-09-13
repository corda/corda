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

#ifndef _CPUID_GNU_H_
#define _CPUID_GNU_H_

#ifdef __cplusplus
extern "C" {
#endif

/* This is a PIC-compliant version of CPUID */
static inline void cpuid(int *eax, int *ebx, int *ecx, int *edx)
{
#if defined(__x86_64__)
    asm("cpuid"
            : "=a" (*eax),
            "=b" (*ebx),
            "=c" (*ecx),
            "=d" (*edx)
            : "0" (*eax), "2" (*ecx));

#else
    /*on 32bit, ebx can NOT be used as PIC code*/
    asm volatile ("xchgl %%ebx, %1; cpuid; xchgl %%ebx, %1"
            : "=a" (*eax), "=r" (*ebx), "=c" (*ecx), "=d" (*edx)
            : "0" (*eax), "2" (*ecx));
#endif
}

static inline void __cpuid(int a[4], int b)
{
    a[0] = b;
    a[2] = 0;
    cpuid(&a[0], &a[1], &a[2], &a[3]);
}

static inline void __cpuidex(int a[4], int b, int c)
{
    a[0] = b;
    a[2] = c;
    cpuid(&a[0], &a[1], &a[2], &a[3]);
}

#ifdef __cplusplus
}
#endif

#endif /*_CPUID_GNU_H_*/
