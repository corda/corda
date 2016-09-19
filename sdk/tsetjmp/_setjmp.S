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

#include "linux-regs.h"
#if defined(LIBC_SCCS)
	RCSID("$NetBSD: _setjmp.S,v 1.9 2014/05/23 02:34:19 uebayasi Exp $")
#endif

/*
 * C library -- _setjmp, _longjmp
 *
 *	_longjmp(a,v)
 * will generate a "return(v)" from the last call to
 *	_setjmp(a)
 * by restoring registers from the stack.
 * The previous signal state is NOT restored.
 */

#ifdef LINUX32
#define _JB_EDX 0 
#define _JB_EBX 1 
#define _JB_ESP 2 
#define _JB_EBP 3 
#define _JB_ESI 4 
#define _JB_EDI 5 
#define MOV movl
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
#define MOV movq
#endif

DECLARE_GLOBAL_FUNC _setjmp
#ifdef LINUX32
	movl	4(%esp),%eax
	movl	0(%esp),%edx
	movl	%edx, (_JB_EDX * 4)(%eax)		/* rta */
	movl	%ebx, (_JB_EBX * 4)(%eax)
	movl	%esp, (_JB_ESP * 4)(%eax)
	movl	%ebp, (_JB_EBP * 4)(%eax)
	movl	%esi, (_JB_ESI * 4)(%eax)
	movl	%edi, (_JB_EDI * 4)(%eax)
#endif
#ifdef LINUX64
	movq    (%rsp),%r11
	movq    %rbx,(_JB_RBX * 8)(%rdi)
	movq    %rbp,(_JB_RBP * 8)(%rdi)
	movq    %r12,(_JB_R12 * 8)(%rdi)
	movq    %r13,(_JB_R13 * 8)(%rdi)
	movq    %r14,(_JB_R14 * 8)(%rdi)
	movq    %r15,(_JB_R15 * 8)(%rdi)
	movq    %rsp,(_JB_RSP * 8)(%rdi)
	movq    %r11,(_JB_PC  * 8)(%rdi)
#endif	
        xorl	%eax,%eax
	ret

DECLARE_GLOBAL_FUNC _longjmp
#ifdef LINUX32
	movl	4(%esp),%edx
	movl	8(%esp),%eax
	movl	(_JB_EDX * 4)(%edx),%ecx
	movl	(_JB_EBX * 4)(%edx),%ebx
	movl	(_JB_ESP * 4)(%edx),%esp
	movl	(_JB_EBP * 4)(%edx),%ebp
	movl	(_JB_ESI * 4)(%edx),%esi
	movl	(_JB_EDI * 4)(%edx),%edi
#endif
#ifdef LINUX64
	movl	%esi,%eax
        movq    (_JB_RBX * 8)(%rdi),%rbx
	movq    (_JB_RBP * 8)(%rdi),%rbp
	movq    (_JB_R12 * 8)(%rdi),%r12
	movq    (_JB_R13 * 8)(%rdi),%r13
	movq    (_JB_R14 * 8)(%rdi),%r14
	movq    (_JB_R15 * 8)(%rdi),%r15
	movq    (_JB_RSP * 8)(%rdi),%rsp
	movq    (_JB_PC  * 8)(%rdi),%rcx

#endif	
	testl	%eax,%eax
	jnz	1f
	incl	%eax
1:	MOV	%xcx,0(%xsp)
	ret

DECLARE_GLOBAL_FUNC set_sgx_tlongjmp_version
        lea_pic sgx_tsetjmp_version, %xax 
        ret

