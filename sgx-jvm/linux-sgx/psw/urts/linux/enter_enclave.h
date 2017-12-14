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


/* ------------------------------------------------------------
 * Normal function enter/leave wrappers.
 * ------------------------------------------------------------
 */

#ifndef _ENTER_ENCLAVE_H_
#define _ENTER_ENCLAVE_H_

#include "linux-regs.h"
#include "rts_cmd.h"


.macro lea_symbol symbol, reg
#ifdef __x86_64__
mov     \symbol@GOTPCREL(%rip), \reg
#else
lea     \symbol, \reg
#endif
.endm

/* macro for enter_enclave
 * There is no .cfi_xxx to describe unwind information, because we want c++ exception can't across enclave boundary
*/
.macro EENTER_PROLOG
push    %xbp
mov     %xsp, %xbp

/* save GPRs */
#ifdef __i386__
sub     $(4 * SE_WORDSIZE), %xsp       /* for xsave, xbx, xdi, xsi */
mov     %xbx, -2 * SE_WORDSIZE(%xbp)
mov     %xsi, -3 * SE_WORDSIZE(%xbp)
mov     %xdi, -4 * SE_WORDSIZE(%xbp)
#else /* __x86_64__ */
sub     $(12 * SE_WORDSIZE), %xsp      /* for xsave, params, and non-volatile GPRs */
mov     %xdi, -10 * SE_WORDSIZE(%xbp)
mov     %xsi,  -9 * SE_WORDSIZE(%xbp)
mov     %rdx,  -8 * SE_WORDSIZE(%xbp)
mov     %rcx,  -7 * SE_WORDSIZE(%xbp)
mov     %r8,   -6 * SE_WORDSIZE(%xbp)
mov     %xbx, -11 * SE_WORDSIZE(%xbp)
mov     %r12,  -5 * SE_WORDSIZE(%xbp)
mov     %r13,  -4 * SE_WORDSIZE(%xbp)
mov     %r14,  -3 * SE_WORDSIZE(%xbp)
mov     %r15,  -2 * SE_WORDSIZE(%xbp)
#endif

lea_symbol g_xsave_size, %xdi
xor     %xax, %xax
movl    (%xdi), %eax
sub     %xax, %xsp
mov     %xax, %xcx                     /* xsave size */
mov     $0x3f, %xax
not     %xax
and     %xax, %xsp                     /* xsave requires 64 byte aligned */
mov     %xsp, -1 * SE_WORDSIZE(%xbp)   /* xsave pointer */

/* shadow space for arguments */
sub     $(4 * SE_WORDSIZE), %xsp

/* save extended xfeature registers */
shr     $2, %xcx
xor     %xax, %xax
mov     -1 * SE_WORDSIZE(%xbp), %xdi
cld
rep stos %eax, %es:(%xdi)

mov     -1 * SE_WORDSIZE(%xbp), %xdi
mov     %xdi, (%xsp)
call    save_xregs
.endm

.macro EENTER_EPILOG
/* restore extended xfeature registers */
mov     -SE_WORDSIZE*1(%xbp), %xdi
mov     %xdi, (%xsp)
call    restore_xregs
mov     %xsi, %xax

/* restore GPRs */
#ifdef __i386__
mov     -SE_WORDSIZE*2(%xbp),  %xbx
mov     -SE_WORDSIZE*3(%xbp),  %xsi
mov     -SE_WORDSIZE*4(%xbp),  %xdi
#else
mov     -SE_WORDSIZE*11(%xbp),  %xbx
mov     -SE_WORDSIZE*10(%xbp),  %xdi
mov     -SE_WORDSIZE*9(%xbp),   %xsi
mov     -SE_WORDSIZE*5(%rbp),   %r12
mov     -SE_WORDSIZE*4(%rbp),   %r13
mov     -SE_WORDSIZE*3(%rbp),   %r14
mov     -SE_WORDSIZE*2(%rbp),   %r15

#endif
/* don't need recover rdi, rsi, rdx, rcx */
mov     %xbp, %xsp
pop     %xbp
ret
.endm

#if defined(__i386__)
#define frame_arg0  2*SE_WORDSIZE(%ebp)
#define frame_arg1  3*SE_WORDSIZE(%ebp)
#define frame_arg2  4*SE_WORDSIZE(%ebp)
#define frame_arg3  5*SE_WORDSIZE(%ebp)
#define frame_arg4  6*SE_WORDSIZE(%ebp)
#elif defined(__x86_64__)
#define frame_arg0  -10*SE_WORDSIZE(%rbp)
#define frame_arg1  -9*SE_WORDSIZE(%rbp)
#define frame_arg2  -8*SE_WORDSIZE(%rbp)
#define frame_arg3  -7*SE_WORDSIZE(%rbp)
#define frame_arg4  -6*SE_WORDSIZE(%rbp)
#else
#   error unknown platform
#endif

//refer sgx_error.h
#define SE_ERROR_READ_LOCK_FAIL 0xc0002202

#endif
