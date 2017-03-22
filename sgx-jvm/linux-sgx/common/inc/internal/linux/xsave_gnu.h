/*
 * Copyright (C) 2011-2016 Intel Corporation. All rights reserved.
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

#ifndef _XSAVE_GNU_H_
#define _XSAVE_GNU_H_

#include "se_types.h"

#ifdef __x86_64__
#  define ASM_FXSAVE "rex64/fxsave"
#  define ASM_FXRSTR "rex64/fxrstor"
#  define ASM_XSAVE  ".byte 0x48,0x0f,0xae,0x21"
#  define ASM_XRSTR  ".byte 0x48,0x0f,0xae,0x2f"
#else
#  define ASM_FXSAVE "fxsave"
#  define ASM_FXRSTR "fxrstor"
#  define ASM_XSAVE  ".byte 0x0f,0xae,0x21"
#  define ASM_XRSTR  ".byte 0x0f,0xae,0x2f"
#endif

static inline void do_fwait(void)
{
    asm volatile("fwait");
}

static inline void do_fxsave(void *buffer)
{
    asm volatile(ASM_FXSAVE" (%0)" : : "r"(buffer) : "memory");
}

static inline void do_fxrstor(const void *buffer)
{
    asm volatile(ASM_FXRSTR" (%0)" : : "r"(buffer));
}

static inline void do_xsave(void *buffer)
{
    asm volatile(ASM_XSAVE
            :
            : "D" (buffer), "a" (-1), "d" (-1)
            : "memory");
}

static inline void _do_xrstor(const void *buffer, uint64_t mask)
{
    uint32_t lmask = (uint32_t)mask;
    uint32_t hmask = (uint32_t)(mask >> 32);

    asm volatile(ASM_XRSTR
            :
            : "D" (buffer), "a" (lmask), "d" (hmask));
}

static inline void do_xrstor(const void *buffer)
{
    _do_xrstor(buffer, 0xffffffffffffffffULL);
}

static inline void do_vzeroupper()
{
    asm volatile("vzeroupper");
}

#endif
