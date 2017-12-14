/*	$NetBSD: _setjmp.S,v 1.9 2014/05/23 02:34:19 uebayasi Exp $	*/

/*-
 * Copyright (c) 1990 The Regents of the University of California.
 * All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * William Jolitz.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 *	from: @(#)_setjmp.s	5.1 (Berkeley) 4/23/90
 */
.file "_setjmp.S"
#include "linux-regs.h"
#if defined(LIBC_SCCS)
	RCSID("$NetBSD: _setjmp.S,v 1.9 2014/05/23 02:34:19 uebayasi Exp $")
#endif

/*
 * C library -- setjmp, longjmp
 *
 *	longjmp(a,v)
 * will generate a "return(v)" from the last call to
 *	setjmp(a)
 * by restoring registers from the stack.
 * The previous signal state is NOT restored.
 */
#include "../trts/linux/trts_pic.h"

.text 

#ifdef LINUX32
#define _JB_PC  0 
#define _JB_EBX 1 
#define _JB_ESP 2 
#define _JB_EBP 3 
#define _JB_ESI 4 
#define _JB_EDI 5 
#endif

#ifdef LINUX64
#define _JB_RBX 0 
#define _JB_RBP 1 
#define _JB_R12 2 
#define _JB_R13 3 
#define _JB_R14 4 
#define _JB_R15 5 
#define _JB_RSP 6 
#define _JB_PC  7 
#endif

.macro PUSHAQ
       push %rax
       push %rbx
       push %rcx
       push %rdx
       push %rsi
       push %rdi
       push %r8
       push %r9
       push %r10
       push %r11
       push %r12
       push %r13
       push %r14
       push %r15
.endm

.macro POPAQ
       pop %r15
       pop %r14
       pop %r13
       pop %r12
       pop %r11
       pop %r10
       pop %r9
       pop %r8
       pop %rdi
       pop %rsi
       pop %rdx
       pop %rcx
       pop %rbx
       pop %rax  
.endm

     
DECLARE_GLOBAL_FUNC setjmp
#ifdef LINUX32
    PUSHAL
    /* check the buf is within the enclave */
    movl    (SE_WORDSIZE + 8*SE_WORDSIZE)(%esp), %eax
    pushl   $SE_WORDSIZE
    pushl   %eax
    call    sgx_is_within_enclave
    cmpl    $0, %eax
    jz      .crash
    addl    $(2*SE_WORDSIZE), %esp
    POPAL 
    /* store the registers */
    movl    SE_WORDSIZE(%esp),%eax
    movl    0(%esp),%edx
    movl    %edx, (_JB_PC  * SE_WORDSIZE)(%eax)     /* rta */
    movl    %ebx, (_JB_EBX * SE_WORDSIZE)(%eax)
    movl    %esp, (_JB_ESP * SE_WORDSIZE)(%eax)
    movl    %ebp, (_JB_EBP * SE_WORDSIZE)(%eax)
    movl    %esi, (_JB_ESI * SE_WORDSIZE)(%eax)
    movl    %edi, (_JB_EDI * SE_WORDSIZE)(%eax)
    movl    %eax, %edx
    /* use statck_guard as cookie*/
    call    get_stack_guard
    xchg    %eax, %edx
    xorl    %edx, (_JB_PC  * SE_WORDSIZE)(%eax)
    xorl    %edx, (_JB_EBX * SE_WORDSIZE)(%eax)
    xorl    %edx, (_JB_ESP * SE_WORDSIZE)(%eax)
    xorl    %edx, (_JB_EBP * SE_WORDSIZE)(%eax)
    xorl    %edx, (_JB_ESI * SE_WORDSIZE)(%eax)
    xorl    %edx, (_JB_EDI * SE_WORDSIZE)(%eax)
#endif
#ifdef LINUX64
    PUSHAQ
    /* check the buf is within the enclave */
    movq    $SE_WORDSIZE, %rsi
    call    sgx_is_within_enclave
    cmpl    $0, %eax
    jz      .crash
    POPAQ
    /* store the registers */
    movq    (%rsp),%r11
    movq    %rbx, (_JB_RBX * SE_WORDSIZE)(%rdi)
    movq    %rbp, (_JB_RBP * SE_WORDSIZE)(%rdi)
    movq    %r12, (_JB_R12 * SE_WORDSIZE)(%rdi)
    movq    %r13, (_JB_R13 * SE_WORDSIZE)(%rdi)
    movq    %r14, (_JB_R14 * SE_WORDSIZE)(%rdi)
    movq    %r15, (_JB_R15 * SE_WORDSIZE)(%rdi)
    movq    %rsp, (_JB_RSP * SE_WORDSIZE)(%rdi)
    movq    %r11, (_JB_PC  * SE_WORDSIZE)(%rdi)
    /* use statck_guard as cookie*/
    call    get_stack_guard
    xorq    %rax, (_JB_RBX * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_RBP * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_R12 * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_R13 * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_R14 * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_R15 * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_RSP * SE_WORDSIZE)(%rdi)
    xorq    %rax, (_JB_PC  * SE_WORDSIZE)(%rdi)
#endif	
    xorl    %eax,%eax
    ret
.crash:
    ud2

DECLARE_GLOBAL_FUNC longjmp
#ifdef LINUX32
    PUSHAL
    /* check the buf is within the enclave */
    movl    (SE_WORDSIZE + 8*SE_WORDSIZE)(%esp), %eax
    pushl   $SE_WORDSIZE
    pushl   %eax
    call    sgx_is_within_enclave
    cmpl    $0, %eax
    jz      .crash
    addl    $(2*SE_WORDSIZE), %esp
    /* restore xbp and xsp, push them to stack */
    movl    (SE_WORDSIZE + 8*SE_WORDSIZE)(%esp), %eax
    movl    (_JB_ESP * SE_WORDSIZE)(%eax), %ebx
    movl    (_JB_EBP * SE_WORDSIZE)(%eax), %ecx
    call    get_stack_guard
    xorl    %eax, %ebx
    xorl    %eax, %ecx
    pushl   %ebx
    pushl   %ecx
    /* check restored ebp is on current statck */
    call    is_valid_sp
    cmpl    $0, %eax     
    jz      .crash
    popl    %ecx
    /* check restored esp is on current statck */
    call    is_valid_sp
    cmpl    $0, %eax
    jz      .crash
    popl    %ebx   
    POPAL 
    /* restore the registers */
    movl    SE_WORDSIZE(%esp),%edx
    movl    (SE_WORDSIZE*2)(%esp),%eax
    pushl   %eax
    movl    (_JB_PC  * SE_WORDSIZE)(%edx),%ecx
    movl    (_JB_EBX * SE_WORDSIZE)(%edx),%ebx
    pushl   (_JB_ESP * SE_WORDSIZE)(%edx)
    pushl   (_JB_EBP * SE_WORDSIZE)(%edx)
    movl    (_JB_ESI * SE_WORDSIZE)(%edx),%esi
    movl    (_JB_EDI * SE_WORDSIZE)(%edx),%edi
    call    get_stack_guard
    xorl    %eax, %ecx
    xorl    %eax, %ebx
    movl    (0)(%esp), %edx
    xorl    %eax, %edx
    movl    %edx, (0)(%esp) 
    movl    (SE_WORDSIZE)(%esp), %edx
    xorl    %eax, %edx
    movl    %edx, (SE_WORDSIZE)(%esp) 
    xorl    %eax, %esi
    xorl    %eax, %edi
    popl    %ebp
    popl    %edx
    movl    %ecx, (0)(%edx)
    popl    %eax   
    movl    %edx, %esp
#endif
#ifdef LINUX64
    PUSHAQ
    pushq   %rdi
    /* check the buf is within the enclave */
    movq    $SE_WORDSIZE, %rsi
    call    sgx_is_within_enclave
    cmpl    $0, %eax
    jz      .crash
    popq     %rdi
    /* restore xbp and xsp, push them to stack */
    movq    (_JB_RBP * SE_WORDSIZE)(%rdi),%rcx
    movq    (_JB_RSP * SE_WORDSIZE)(%rdi),%rdx
    call    get_stack_guard
    xorq    %rax, %rcx
    xorq    %rax, %rdx
    pushq   %rdx
    pushq   %rcx
    /* check restored rbp is on current statck */
    popq    %rdi
    call    is_valid_sp
    cmpl    $0, %eax
    jz      .crash
    /* check restored rsp is on current statck */
    popq    %rdi
    call    is_valid_sp
    cmpl    $0, %eax
    jz      .crash
    POPAQ
    /* restore the registers */
    movl    %esi,%eax
    movq    (_JB_RBX * SE_WORDSIZE)(%rdi),%rbx
    movq    (_JB_RBP * SE_WORDSIZE)(%rdi),%rsi
    movq    (_JB_R12 * SE_WORDSIZE)(%rdi),%r12
    movq    (_JB_R13 * SE_WORDSIZE)(%rdi),%r13
    movq    (_JB_R14 * SE_WORDSIZE)(%rdi),%r14
    movq    (_JB_R15 * SE_WORDSIZE)(%rdi),%r15
    movq    (_JB_RSP * SE_WORDSIZE)(%rdi),%rdx
    movq    (_JB_PC  * SE_WORDSIZE)(%rdi),%rcx
    pushq   %rax
    call    get_stack_guard
    xorq    %rax, %rbx
    xorq    %rax, %rsi
    xorq    %rax, %r12
    xorq    %rax, %r13
    xorq    %rax, %r14
    xorq    %rax, %r15
    xorq    %rax, %rdx
    xorq    %rax, %rcx
    popq    %rax
    movq    %rsi, %rbp
    movq    %rcx, 0(%rdx)
    movq    %rdx, %rsp
#endif
    testl   %eax,%eax
    jnz     1f
    incl    %eax
1:  ret


DECLARE_GLOBAL_FUNC set_sgx_tlongjmp_version
    lea_pic sgx_tsetjmp_version, %xax 
    ret

.weak _setjmp
_setjmp=setjmp
.weak _longjmp
_longjmp=longjmp
