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

#include "offsets.h"

	.global setcontext
	.type setcontext, @function
setcontext:
	movl	4(%esp),%eax	/* ucontext_t* */

	/* restore FP state */
	movl	LINUX_UC_FPREGS_PTR_OFF(%eax), %ecx
	fldenv	(%ecx)

#ifdef SE_SIM
	/* restore FS */
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_FS_OFF)(%eax), %ecx

	movl	%ecx, %fs
#endif

	/* new stack */
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_ESP_OFF)(%eax), %esp

	/* push the return address on the stack */
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EIP_OFF)(%eax), %ecx
	pushl	%ecx

	/* restore GPRs */
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EDI_OFF)(%eax), %edi
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_ESI_OFF)(%eax), %esi
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EBP_OFF)(%eax), %ebp
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EBX_OFF)(%eax), %ebx
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EDX_OFF)(%eax), %edx
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_ECX_OFF)(%eax), %ecx
	movl	(LINUX_UC_MCONTEXT_OFF+LINUX_SC_EAX_OFF)(%eax), %eax

	ret
