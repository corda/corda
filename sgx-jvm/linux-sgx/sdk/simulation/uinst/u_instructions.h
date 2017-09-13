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


#ifndef _UINSTRUCTIONS_H__
#define _UINSTRUCTIONS_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// this struct defines the way the registers are stored on the
// stack during an EENTER.
typedef struct _se_pt_regs_t {
#if defined(__x86_64__)
    uintptr_t xbp;
    uintptr_t xip;
#else
    uintptr_t xbp;
    uintptr_t xip;
    uintptr_t xax;
    uintptr_t xbx;
    uintptr_t xcx;
    uintptr_t xdx;
    uintptr_t xsi;
    uintptr_t xdi;
#endif
} se_pt_regs_t;

uintptr_t _SE0(uintptr_t xax, uintptr_t xbx,
               uintptr_t xcx, uintptr_t xdx,
               uintptr_t xsi, uintptr_t xdi);

void _SE3(uintptr_t xax, uintptr_t xbx,
        uintptr_t xcx, uintptr_t xdx,
        uintptr_t xsi, uintptr_t xdi);

#ifdef __cplusplus
}
#endif

#endif
