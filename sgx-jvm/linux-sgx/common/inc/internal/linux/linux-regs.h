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

/*
 * This header wraps the register names for x86/x64.
 */

#ifndef LINUX_REGS_H__
#define LINUX_REGS_H__

#if defined(__i386) || defined(__i386__)
#  define LINUX32       1
#  define SE_WORDSIZE   4

/* Generic argument picker for `naked' functions */
#  define naked_arg0    4(%esp)
#  define naked_arg1    8(%esp)
#  define naked_arg2   12(%esp)
#  define naked_arg3   16(%esp)

#  define xax eax
#  define xbx ebx
#  define xcx ecx
#  define xdx edx

#  define xsi esi
#  define xdi edi
#  define xbp ebp
#  define xsp esp
#elif defined(__x86_64) || defined(__x86_64__)
#  define LINUX64       1
#  define SE_WORDSIZE   8

/* For x86_64, the first six parameters are passed by
 * rdi, rsi, rdx, rcx, r8, r9.
 */
#  define naked_arg0    %rdi
#  define naked_arg1    %rsi
#  define naked_arg2    %rdx
#  define naked_arg3    %rcx

#  define xax rax
#  define xbx rbx
#  define xcx rcx
#  define xdx rdx

#  define xsi rsi
#  define xdi rdi
#  define xbp rbp
#  define xsp rsp
#else
#  error unknown platform!
#endif

/* SE instructions - needs to be sync-up with inst70.h */
#define SE_EREPORT    0
#define SE_EGETKEY    1
#define SE_EENTER     2
#define SE_EEXIT      4
#define SE_EACCEPT    5
#define SE_EMODPE     6


#define SE_ECREATE    0
#define SE_EADD       1
#define SE_EINIT      2
#define SE_EREMOVE    3

/*
 * Macros for GNU assembly
 */
.macro ENCLU
#ifdef SE_SIM
    cmp     $SE_EEXIT, %xax
    jne     1f

    /* if leaf is EEXIT, xbp and xsp need to be passed by xdx and xcx */
    mov     %xbp, %xdx
    mov     %xsp, %xcx
1:
    push    %xdi
    push    %xsi
    push    %xdx
    push    %xcx
    push    %xbx
    push    %xax

#   ifdef LINUX64
    pop     %rdi
    pop     %rsi
    pop     %rdx
    pop     %rcx
    pop     %r8
    pop     %r9
#   endif

.type _SE3,@function
.protected _SE3
    call    _SE3

#   ifdef LINUX32
    add     $(SE_WORDSIZE * 6), %esp
#   endif

#else /* SE_SIM */
.byte 0x0f, 0x01, 0xd7 /* 0xf3 */
#endif /* !SE_SIM */
.endm

/* declare a function with default visibility */
.macro DECLARE_GLOBAL_FUNC name
    .globl \name
    .type \name, @function
\name:
.endm

/* declare a function with visibility='hidden' */
.macro DECLARE_LOCAL_FUNC name
    .globl \name
    .hidden \name
    .type \name, @function
\name:
.endm

.macro NAKED_PROLOG
    push    %xbp
    mov     %xsp, %xbp
    sub     $(7 * SE_WORDSIZE), %xsp
.endm

.macro NAKED_EPILOG
    mov     %xbp, %xsp
    pop     %xbp
.endm

/* `paramN' (N = 1,2,3,4) should be registers. */
.macro SET_PARAMS param1:req, param2, param3, param4
#if defined(LINUX32)

.ifnb \param4
    mov     \param4, 3*SE_WORDSIZE(%esp)
.endif

.ifnb \param3
    mov     \param3, 2*SE_WORDSIZE(%esp)
.endif

.ifnb \param2
    mov     \param2, 1*SE_WORDSIZE(%esp)
.endif

    mov     \param1, 0*SE_WORDSIZE(%esp)

#else /* LINUX32 */

.ifnb \param4
.ifnc \param4, %rcx
    mov     \param4, %rcx
.endif
.endif

.ifnb \param3
.ifnc \param3, %rdx
    mov     \param3, %rdx
.endif
.endif

.ifnb \param2
.ifnc \param2, %rsi
    mov     \param2, %rsi
.endif
.endif

.ifnc \param1, %rdi
    mov     \param1, %rdi
.endif

#endif /* LINUX64 */
.endm

/*******************************************************************/

.macro SE_PROLOG
    .cfi_startproc

#ifdef LINUX32
    pushl   %ebp
    movl    %esp, %ebp
#endif

    push    %xbx
    push    %xcx
    push    %xdx

#if defined LINUX64
    movq    %rdi, %rbx
    movq    %rsi, %rcx
    /* rdx remains the same, rdi/rsi is not used by _SE0
     */
#elif defined LINUX32
    movl    2*SE_WORDSIZE(%ebp), %ebx
    movl    3*SE_WORDSIZE(%ebp), %ecx
    movl    4*SE_WORDSIZE(%ebp), %edx
#endif

.endm

/*******************************************************************/

.macro SE_EPILOG
    pop     %xdx
    pop     %xcx
    pop     %xbx

#ifdef LINUX32
    movl    %ebp, %esp
    popl    %ebp
#endif

    ret
    .cfi_endproc
.endm

/*******************************************************************/

/* load the address of `symbol' to the register `reg' in PIC way. */
.macro lea_pic symbol, reg
#ifdef LINUX64
    lea   \symbol(%rip), \reg
#else
/* The real code on x86 would look like this (get `bar' from `foo'):
 *
 * 00000198 <bar>:
 * 198:   c3                      ret
 *
 * 00000199 <foo>:
 * 199:   e8 00 00 00 00          call   19e <foo+0x5>
 * 19e:   58                      pop    %eax
 * 19f:   8d 40 fa                lea    -0x6(%eax),%eax
 */
    call  . + 0x5 /* No label here to avoid interfering w/ calling code */
    pop   \reg
    lea   (\symbol - . + 1)(\reg), \reg
#endif
.endm

#endif /* LINUX_REGS_H__ */
