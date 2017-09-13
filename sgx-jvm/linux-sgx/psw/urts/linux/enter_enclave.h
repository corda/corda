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

/* macro for enter_enclave
 * There is no .cfi_xxx to describe unwind information, because we want c++ exception can't across enclave boundary
*/
.macro EENTER_PROLOG
push    %xbp
mov     %xsp, %xbp

#if defined(__i386__)
push    %ebx
push    %esi
push    %edi

/* These 3 wordsize buffer are used for paddings, so that the
 * stack-boundary can be kept 16-byte aligned.
 *
 * Note that, by default GCC assumes
 *  -mpreferred-stack-boundary=4
 * which results to 16 (2^4) byte alignment of stack boundary.
 *
 *   | parameters     |              <-  previous frame
 * ==+================+======
 *   | return address |  ^
 *   +----------------+  |
 *   | ebp            |  |
 *   +----------------+ 16 bytes     <-  current frame
 *   | ebx            |  |
 *   +----------------+  |
 *   | esi            |  v
 *   +----------------+ ----
 *   | edi            |  4 bytes
 *   +----------------+
 *   | paddings       | 3*4 bytes
 * ==+================+======
 *   | ENCLU          |
 */
sub    $(3 * SE_WORDSIZE), %esp

#elif defined(__x86_64__)
push    %rbx
push    %r12
push    %r13
push    %r14
push    %r15
/*save 5 parameter*/
push    %r8
push    %rcx
push    %rdx
push    %rsi
push    %rdi
#else
#   error unknown platform
#endif
.endm

.macro EENTER_EPILOG
#if defined(__i386__)
mov          -SE_WORDSIZE*1(%ebp),  %ebx
mov          -SE_WORDSIZE*2(%ebp),  %esi
mov          -SE_WORDSIZE*3(%ebp),  %edi
#elif defined(__x86_64__)
mov          -SE_WORDSIZE*1(%rbp),  %rbx
mov          -SE_WORDSIZE*2(%rbp),  %r12
mov          -SE_WORDSIZE*3(%rbp),  %r13
mov          -SE_WORDSIZE*4(%rbp),  %r14
mov          -SE_WORDSIZE*5(%rbp),  %r15
#else
#   error unknown platform
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
