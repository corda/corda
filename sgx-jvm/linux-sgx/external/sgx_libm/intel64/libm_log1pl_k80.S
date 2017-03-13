/*
* Math library
*
* Copyright (C) 2016 Intel Corporation. All rights reserved.
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
*
* Author Name <jingwei.zhang@intel.com>
*   History:
*   03-14-2016 Initial version. numerics svn rev. 12864
*/
	.file "libm_log1pl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_log1p_k80
	.text
       .align    16,0x90
	.globl __libm_log1p_k80
__libm_log1p_k80:
# parameter 1: %rdi
# parameter 2: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___libm_log1p_k80.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        subq      $120, %rsp
	.cfi_def_cfa_offset 144
        movq      %rsi, %r14
        movq      %fs:40, %rax
        movq      %rdi, %r13
        xorq      %rsp, %rax
        movq      %rax, 112(%rsp)
        movzwl    8(%r14), %edx
        andl      $32767, %edx
        movl      32(%r14), %ecx
        lea       (%rcx,%rdx), %eax
        cmpl      $16511, %eax
        jge       ..B1.9
..B1.2:
        cmpl      $16255, %eax
        jle       ..B1.8
..B1.3:
        addl      $-8383, %edx
        cmpl      $16000, %edx
        jbe       ..B1.6
..B1.4:
        movq      %r14, %rdi
..___tag_value___libm_log1p_k80.8:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_log1p_k80.9:
..B1.5:
        movl      32(%r14), %ecx
..B1.6:
        fldt      (%r14)
        addl      $16383, %ecx
        fldt      16(%r14)
        andl      $32767, %ecx
        andb      $127, 9(%rsp)
        lea       80(%rsp), %rdi
        movzwl    -72(%rdi), %eax
        lea       48(%rsp), %rsi
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, -40(%rsi)
        movl      $-2147483648, -44(%rsi)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%rsi)
        fmulp     %st, %st(1)
        fstpt     16(%rsi)
..___tag_value___libm_log1p_k80.10:
        call      __libm_log1pl_k80@PLT
..___tag_value___libm_log1p_k80.11:
..B1.7:
        fldt      80(%rsp)
        fmul      %st(1), %st
        movl      $0, 32(%r13)
        fstpt     (%r13)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        jmp       ..B1.19
..B1.8:
        movq      (%r14), %rax
        movq      %rax, (%r13)
        movq      8(%r14), %rdx
        movq      %rdx, 8(%r13)
        movq      16(%r14), %rcx
        movq      %rcx, 16(%r13)
        movq      24(%r14), %rsi
        movq      %rsi, 24(%r13)
        movq      32(%r14), %r8
        movq      %r8, 32(%r13)
        movq      40(%r14), %rdi
        movq      %rdi, 40(%r13)
        jmp       ..B1.19
..B1.9:
        movq      %r14, %rdi
..___tag_value___libm_log1p_k80.12:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_log1p_k80.13:
..B1.10:
        fildl     32(%r14)
        fstpt     (%rsp)
        fldt      (%r14)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      16(%r14)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 176
        lea       112(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_log1p_k80.15:
        call      __libm_logl_k80@PLT
..___tag_value___libm_log1p_k80.16:
..B1.25:
        addq      $32, %rsp
	.cfi_def_cfa_offset 144
..B1.11:
        fldt      80(%rsp)
        lea       _LN2_K80(%rip), %rax
        lea       16+_LN2_K80(%rip), %rdx
        fmul      %st(1), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(2)
        movzwl    88(%rsp), %esi
        andl      $32767, %esi
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      (%rax)
        fldt      (%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(0)
        movzwl    24(%rsp), %ecx
        fadd      %st(4), %st
        fldt      (%rdx)
        andl      $32767, %ecx
        fmulp     %st, %st(3)
        cmpl      %esi, %ecx
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        movl      $0, 32(%r13)
        jg        ..B1.16
..B1.12:
        jne       ..B1.17
..B1.13:
        movl      20(%rsp), %eax
        movl      84(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B1.16
..B1.14:
        jne       ..B1.17
..B1.15:
        movl      16(%rsp), %eax
        cmpl      80(%rsp), %eax
        jbe       ..B1.17
..B1.16:
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.18
..B1.17:
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
..B1.18:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%r13)
        fldt      (%r13)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r13)
..B1.19:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.21
..B1.20:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 13, -16
	.cfi_offset 14, -24
..B1.21:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__libm_log1p_k80,@function
	.size	__libm_log1p_k80,.-__libm_log1p_k80
	.data
# -- End  __libm_log1p_k80
	.text
# -- Begin  __libm_log1pl_k80
	.text
       .align    16,0x90
	.globl __libm_log1pl_k80
__libm_log1pl_k80:
# parameter 1: %rdi
# parameter 2: %rsi
..B2.1:
	.cfi_startproc
..___tag_value___libm_log1pl_k80.27:
..L28:

        subq      $440, %rsp
	.cfi_def_cfa_offset 448
        movq      %rsi, %rax
        movq      %fs:40, %rdx
        movq      %rdi, %r9
        xorq      %rsp, %rdx
        lea       zeros(%rip), %r8
        movq      %rdx, 432(%rsp)
        fldt      (%rax)
        fld       %st(0)
        fldt      16(%rax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fsub      %st, %st(2)
        movzbl    9(%rsp), %edi
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      (%r8)
        andl      $128, %edi
        shrl      $7, %edi
        movl      %edi, %edx
        movzwl    8(%rsp), %esi
        shll      $15, %edx
        andl      $32767, %esi
        movl      4(%rsp), %r10d
        orl       %esi, %edx
        movl      %r10d, %ecx
        shll      $16, %edx
        shrl      $16, %ecx
        orl       %ecx, %edx
        fstpt     16(%r9)
        fldt      16(%r9)
        cmpl      $32767, %esi
        je        ..B2.115
..B2.2:
        cmpl      $-1073774592, %edx
        jae       ..B2.101
..B2.3:
        fstp      %st(1)
        fstpt     (%r9)
        fldt      (%r9)
..B2.4:
        cmpl      $16375, %esi
        jge       ..B2.52
..B2.5:
        fstp      %st(0)
        cmpl      $16363, %esi
        jge       ..B2.30
..B2.6:
        fstp      %st(0)
        cmpl      $16298, %esi
        jge       ..B2.8
..B2.7:
        fldt      (%rax)
        fstpt     (%r9)
        fldt      16(%rax)
        lea       ones(%rip), %rax
        fstpt     16(%r9)
        fldt      (%rax)
        jmp       ..B2.104
..B2.8:
        fldt      (%rax)
        lea       t32(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        lea       32+_S(%rip), %rcx
        lea       _S(%rip), %rsi
        lea       16+_S(%rip), %rdi
        lea       64+_S(%rip), %r8
        fldt      (%rdx)
        fmul      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fsub      %st(3), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st(0), %st
        fld       %st(0)
        fld       %st(4)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(5)
        fxch      %st(4)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(5)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(3)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      16(%rax)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(5)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rcx)
        fld       %st(0)
        fmul      %st(2), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fxch      %st(1)
        fmul      %st(3), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     320(%rsp)
        fld       %st(1)
        fsub      %st(6), %st
        faddp     %st, %st(6)
        fld       %st(1)
        fsub      %st(6), %st
        fxch      %st(1)
        fstpt     304(%rsp)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(3)
        fmul      %st(6), %st
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      16(%rax)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      (%rsi)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fxch      %st(1)
        fmul      %st(6), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fld       %st(5)
        fsub      %st(1), %st
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%rdi)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fsubr     %st, %st(1)
        movzwl    344(%rsp), %r10d
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        andl      $32767, %r10d
        fstpt     352(%rsp)
        fldt      32(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      80(%rsp)
        fmul      %st, %st(2)
        fldt      48(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      (%rax)
        fmul      %st, %st(5)
        fldt      16(%rax)
        fmulp     %st, %st(7)
        fxch      %st(6)
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      (%r8)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpt     368(%rsp)
        fldt      368(%rsp)
        fadd      %st, %st(1)
        fxch      %st(4)
        fmulp     %st, %st(3)
        movzwl    376(%rsp), %r11d
        andl      $32767, %r11d
        fxch      %st(2)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        cmpl      %r11d, %r10d
        fldt      64(%rsp)
        fldt      96(%rsp)
        jg        ..B2.13
..B2.9:
        jne       ..B2.14
..B2.10:
        movl      340(%rsp), %edx
        movl      372(%rsp), %ecx
        cmpl      %ecx, %edx
        ja        ..B2.13
..B2.11:
        jne       ..B2.14
..B2.12:
        movl      336(%rsp), %edx
        cmpl      368(%rsp), %edx
        jbe       ..B2.14
..B2.13:
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B2.15
..B2.14:
        fxch      %st(4)
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
..B2.15:
        fld       %st(2)
        movzwl    312(%rsp), %edx
        fadd      %st(1), %st
        fstpt     400(%rsp)
        fldt      400(%rsp)
        andl      $32767, %edx
        movzwl    408(%rsp), %ecx
        fld       %st(2)
        andl      $32767, %ecx
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        cmpl      %ecx, %edx
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        jg        ..B2.20
..B2.16:
        jne       ..B2.21
..B2.17:
        movl      308(%rsp), %edx
        movl      404(%rsp), %ecx
        cmpl      %ecx, %edx
        ja        ..B2.20
..B2.18:
        jne       ..B2.21
..B2.19:
        movl      304(%rsp), %edx
        cmpl      400(%rsp), %edx
        jbe       ..B2.21
..B2.20:
        fldt      (%rsp)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.22
..B2.21:
        fldt      (%rsp)
        fxch      %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.22:
        fld       %st(0)
        movzwl    8(%rax), %edx
        fadd      %st(2), %st
        fstpt     272(%rsp)
        fldt      272(%rsp)
        andl      $32767, %edx
        movzwl    280(%rsp), %ecx
        fsubr     %st, %st(2)
        andl      $32767, %ecx
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     288(%rsp)
        fldt      288(%rsp)
        cmpl      %ecx, %edx
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%r9)
        fldt      16(%r9)
        jg        ..B2.27
..B2.23:
        jne       ..B2.28
..B2.24:
        movl      276(%rsp), %edx
        cmpl      4(%rax), %edx
        jb        ..B2.27
..B2.25:
        jne       ..B2.28
..B2.26:
        movl      (%rax), %edx
        cmpl      272(%rsp), %edx
        jbe       ..B2.28
..B2.27:
        fldt      (%rax)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        jmp       ..B2.29
..B2.28:
        fldt      (%rax)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      16(%rax)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
..B2.29:
        fld       %st(1)
        lea       ones(%rip), %rax
        fadd      %st(1), %st
        fstpt     (%r9)
        fldt      (%r9)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r9)
        fldt      (%rax)
        jmp       ..B2.104
..B2.30:
        fldt      (%rax)
        lea       t32(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       _S(%rip), %rcx
        fxch      %st(4)
        fstpt     48(%rsp)
        lea       16+_S(%rip), %rsi
        fldt      (%rdx)
        lea       32+_S(%rip), %rdi
        fmul      %st, %st(2)
        lea       176+_S(%rip), %r10
        lea       64+_S(%rip), %r8
        lea       160+_S(%rip), %r11
        lea       144+_S(%rip), %rdx
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(1)
        fmul      %st(5), %st
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fxch      %st(3)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      16(%rax)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fadd      %st(0), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        fmul      %st(7), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(5), %st
        fsubr     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fmulp     %st, %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      16(%rax)
        fldt      64(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st, %st(3)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fld       %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fld       %st(1)
        fsub      %st(1), %st
        fldt      80(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fldt      16(%rax)
        fmul      %st(3), %st
        faddp     %st, %st(5)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        faddp     %st, %st(3)
        fldt      48(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rcx)
        lea       128+_S(%rip), %rcx
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fmul      %st(5), %st
        fxch      %st(2)
        fmul      %st, %st(5)
        fldt      112(%rsp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fldt      16(%rsp)
        fldt      (%rsi)
        lea       112+_S(%rip), %rsi
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     352(%rsp)
        fldt      352(%rsp)
        fldt      (%rdi)
        lea       96+_S(%rip), %rdi
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     320(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     304(%rsp)
        fldt      304(%rsp)
        fldt      (%r8)
        lea       80+_S(%rip), %r8
        fldt      32(%rsp)
        fmul      %st(1), %st
        fstpt     368(%rsp)
        fldt      368(%rsp)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        fldt      (%r10)
        fldt      48(%rsp)
        fmul      %st, %st(1)
        fldt      (%r11)
        movzwl    344(%rsp), %r10d
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        andl      $32767, %r10d
        fldt      (%rdx)
        movzwl    376(%rsp), %edx
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        andl      $32767, %edx
        fldt      (%rcx)
        cmpl      %edx, %r10d
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      (%r8)
        faddp     %st, %st(1)
        fld       %st(5)
        faddp     %st, %st(3)
        fldt      (%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rax)
        jg        ..B2.35
..B2.31:
        jne       ..B2.36
..B2.32:
        movl      340(%rsp), %edx
        movl      372(%rsp), %ecx
        cmpl      %ecx, %edx
        ja        ..B2.35
..B2.33:
        jne       ..B2.36
..B2.34:
        movl      336(%rsp), %edx
        cmpl      368(%rsp), %edx
        jbe       ..B2.36
..B2.35:
        fldt      32(%rsp)
        fxch      %st(3)
        fsubr     %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(6)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        jmp       ..B2.37
..B2.36:
        fldt      32(%rsp)
        fsub      %st(3), %st
        faddp     %st, %st(6)
        fxch      %st(4)
        faddp     %st, %st(5)
        faddp     %st, %st(4)
..B2.37:
        fld       %st(3)
        movzwl    312(%rsp), %edx
        fadd      %st(1), %st
        fstpt     400(%rsp)
        fldt      400(%rsp)
        andl      $32767, %edx
        movzwl    408(%rsp), %ecx
        fsubr     %st, %st(1)
        andl      $32767, %ecx
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fld       %st(1)
        cmpl      %ecx, %edx
        fadd      %st(4), %st
        jg        ..B2.42
..B2.38:
        jne       ..B2.43
..B2.39:
        movl      308(%rsp), %edx
        movl      404(%rsp), %ecx
        cmpl      %ecx, %edx
        ja        ..B2.42
..B2.40:
        jne       ..B2.43
..B2.41:
        movl      304(%rsp), %edx
        cmpl      400(%rsp), %edx
        jbe       ..B2.43
..B2.42:
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fxch      %st(2)
        jmp       ..B2.44
..B2.43:
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldt      16(%rsp)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
..B2.44:
        fld       %st(2)
        movzwl    8(%rax), %edx
        fadd      %st(1), %st
        fstpt     272(%rsp)
        fldt      272(%rsp)
        andl      $32767, %edx
        movzwl    280(%rsp), %ecx
        fsubr     %st, %st(1)
        andl      $32767, %ecx
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     288(%rsp)
        fldt      288(%rsp)
        cmpl      %ecx, %edx
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     16(%r9)
        fldt      16(%r9)
        jg        ..B2.49
..B2.45:
        jne       ..B2.50
..B2.46:
        movl      276(%rsp), %edx
        cmpl      4(%rax), %edx
        jb        ..B2.49
..B2.47:
        jne       ..B2.50
..B2.48:
        movl      (%rax), %edx
        cmpl      272(%rsp), %edx
        jbe       ..B2.50
..B2.49:
        fldt      (%rax)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        jmp       ..B2.51
..B2.50:
        fldt      (%rax)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      16(%rax)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
..B2.51:
        fld       %st(1)
        lea       ones(%rip), %rax
        fadd      %st(1), %st
        fstpt     (%r9)
        fldt      (%r9)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        faddp     %st, %st(1)
        fstpt     16(%r9)
        fldt      (%rax)
        jmp       ..B2.104
..B2.52:
        fstp      %st(1)
        fldt      (%rax)
        lea       ones(%rip), %r8
        movzwl    8(%rax), %edx
        fld       %st(0)
        andl      $32767, %edx
        fldt      (%r8)
        movzwl    8(%r8), %edi
        movl      %edi, %esi
        andl      $32767, %esi
        fadd      %st, %st(1)
        cmpl      %esi, %edx
        jg        ..B2.57
..B2.53:
        jne       ..B2.58
..B2.54:
        movl      4(%rax), %edx
        cmpl      4(%r8), %edx
        ja        ..B2.57
..B2.55:
        jne       ..B2.58
..B2.56:
        movl      (%rax), %edx
        cmpl      (%r8), %edx
        jbe       ..B2.58
..B2.57:
        fldt      16(%rax)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        fadd      %st, %st(3)
        fxch      %st(3)
        fadd      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.59
..B2.58:
        fldt      16(%rax)
        fld       %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
..B2.59:
        fld       %st(0)
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r10
        fadd      %st(2), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(2)
        movzwl    120(%rsp), %ecx
        fxch      %st(2)
        faddp     %st, %st(1)
        movl      116(%rsp), %eax
        andl      $32767, %ecx
        shrl      $23, %eax
        movzbl    %al, %eax
        fstpt     128(%rsp)
        fldt      128(%rsp)
        lea       -16383(%rcx), %edx
        cmpl      $16383, %edx
        flds      (%r10,%rax,4)
        jge       ..B2.61
..B2.60:
        movl      %edx, %r10d
        andl      $-32768, %edi
        negl      %r10d
        addl      $16383, %r10d
        andl      $32767, %r10d
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        orl       %r10d, %edi
        movw      %di, 8(%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        fxch      %st(4)
        fxch      %st(1)
        jmp       ..B2.62
..B2.61:
        lea       _CONSTANTS(%rip), %rdi
        fldt      (%rdi)
..B2.62:
        movzwl    136(%rsp), %edi
        andl      $32767, %edi
        addl      $128, %edi
        fmul      %st, %st(3)
        cmpl      %edi, %ecx
        jge       ..B2.64
..B2.63:
        fmulp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.65
..B2.64:
        fstp      %st(0)
        fstp      %st(1)
        fld       %st(3)
..B2.65:
        movl      %edx, 96(%rsp)
        lea       _LN2(%rip), %rdx
        fildl     96(%rsp)
        lea       16+_LN2(%rip), %rcx
        fxch      %st(4)
        fstpt     64(%rsp)
        shlq      $4, %rax
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rdi
        fstpt     80(%rsp)
        fld       %st(1)
        fldt      (%rdx)
        fmul      %st(4), %st
        fldt      (%rcx)
        fmulp     %st, %st(5)
        fld       %st(3)
        fxch      %st(1)
        faddl     (%rax,%rdi)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     240(%rsp)
        fld       %st(3)
        fxch      %st(4)
        fmul      %st(6), %st
        fxch      %st(5)
        faddl     8(%rdi,%rax)
        lea       t32(%rip), %rax
        fstpt     (%rsp)
        fld       %st(2)
        fldt      (%rax)
        fmul      %st, %st(3)
        fxch      %st(3)
        fsubr     %st, %st(5)
        faddp     %st, %st(5)
        fxch      %st(1)
        fsub      %st(4), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fstpt     32(%rsp)
        fld       %st(2)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(2)
        fsub      %st(2), %st
        fld       %st(4)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(1)
        movzwl    120(%rsp), %r10d
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %r10d
        fxch      %st(1)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        cmpl      %esi, %r10d
        fldt      64(%rsp)
        fld       %st(0)
        fsubr     %st(3), %st
        fstpt     48(%rsp)
        jg        ..B2.70
..B2.66:
        jne       ..B2.71
..B2.67:
        movl      116(%rsp), %eax
        cmpl      4(%r8), %eax
        ja        ..B2.70
..B2.68:
        jne       ..B2.71
..B2.69:
        movl      112(%rsp), %eax
        cmpl      (%r8), %eax
        jbe       ..B2.71
..B2.70:
        fldt      48(%rsp)
        fsubrp    %st, %st(3)
        fsubr     %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        jmp       ..B2.72
..B2.71:
        fldt      48(%rsp)
        fadd      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
..B2.72:
        fldt      48(%rsp)
        lea       _S(%rip), %rax
        fld       %st(0)
        lea       16+_S(%rip), %rdx
        lea       32+_S(%rip), %rcx
        lea       64+_S(%rip), %rsi
        lea       176+_S(%rip), %rdi
        lea       160+_S(%rip), %r8
        lea       144+_S(%rip), %r10
        lea       128+_S(%rip), %r11
        fadd      %st(2), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     144(%rsp)
        fxch      %st(2)
        fstpt     64(%rsp)
        fld       %st(0)
        fxch      %st(2)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(1)
        fstpt     256(%rsp)
        fldt      256(%rsp)
        fldt      32(%rsp)
        fld       %st(0)
        fmul      %st(2), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fld       %st(3)
        fld       %st(2)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st(0), %st
        fld       %st(0)
        fld       %st(6)
        fmul      %st(7), %st
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(5)
        fmul      %st(5), %st
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fld       %st(1)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fld       %st(6)
        fmul      %st(4), %st
        fxch      %st(1)
        fsub      %st(4), %st
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(7)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(2)
        fld       %st(3)
        fmulp     %st, %st(1)
        fld       %st(4)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fldt      32(%rsp)
        fldt      160(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fld       %st(2)
        fmulp     %st, %st(4)
        fldt      192(%rsp)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        faddp     %st, %st(2)
        fldt      144(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      (%rax)
        lea       112+_S(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fmul      %st(5), %st
        fxch      %st(2)
        fmul      %st, %st(5)
        fldt      208(%rsp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fldt      96(%rsp)
        fldt      (%rdx)
        lea       96+_S(%rip), %rdx
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     352(%rsp)
        fldt      352(%rsp)
        fldt      (%rcx)
        lea       80+_S(%rip), %rcx
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     320(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     304(%rsp)
        fldt      304(%rsp)
        fldt      (%rsi)
        fldt      112(%rsp)
        fmul      %st(1), %st
        fstpt     368(%rsp)
        fldt      368(%rsp)
        movzwl    344(%rsp), %esi
        andl      $32767, %esi
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      224(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        fldt      (%rdi)
        fldt      144(%rsp)
        fmul      %st, %st(1)
        fldt      (%r8)
        movzwl    376(%rsp), %edi
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        andl      $32767, %edi
        fldt      (%r10)
        cmpl      %edi, %esi
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r11)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fld       %st(4)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fldt      128(%rsp)
        jg        ..B2.77
..B2.73:
        jne       ..B2.78
..B2.74:
        movl      340(%rsp), %eax
        movl      372(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.77
..B2.75:
        jne       ..B2.78
..B2.76:
        movl      336(%rsp), %eax
        cmpl      368(%rsp), %eax
        jbe       ..B2.78
..B2.77:
        fldt      112(%rsp)
        fxch      %st(4)
        fsubr     %st, %st(7)
        fxch      %st(4)
        faddp     %st, %st(7)
        fxch      %st(2)
        faddp     %st, %st(6)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fxch      %st(4)
        jmp       ..B2.79
..B2.78:
        fldt      112(%rsp)
        fsub      %st(4), %st
        faddp     %st, %st(7)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
..B2.79:
        fld       %st(0)
        movzwl    312(%rsp), %eax
        fadd      %st(2), %st
        fstpt     400(%rsp)
        fldt      400(%rsp)
        andl      $32767, %eax
        movzwl    408(%rsp), %edx
        fld       %st(3)
        andl      $32767, %edx
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        cmpl      %edx, %eax
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        jg        ..B2.84
..B2.80:
        jne       ..B2.85
..B2.81:
        movl      308(%rsp), %eax
        movl      404(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.84
..B2.82:
        jne       ..B2.85
..B2.83:
        movl      304(%rsp), %eax
        cmpl      400(%rsp), %eax
        jbe       ..B2.85
..B2.84:
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.86
..B2.85:
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.86:
        fld       %st(0)
        movzwl    264(%rsp), %eax
        fadd      %st(2), %st
        fstpt     272(%rsp)
        fldt      272(%rsp)
        andl      $32767, %eax
        movzwl    280(%rsp), %edx
        fld       %st(3)
        andl      $32767, %edx
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        cmpl      %edx, %eax
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     288(%rsp)
        fldt      288(%rsp)
        jg        ..B2.91
..B2.87:
        jne       ..B2.92
..B2.88:
        movl      260(%rsp), %eax
        movl      276(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.91
..B2.89:
        jne       ..B2.92
..B2.90:
        movl      256(%rsp), %eax
        cmpl      272(%rsp), %eax
        jbe       ..B2.92
..B2.91:
        fldt      48(%rsp)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.93
..B2.92:
        fldt      48(%rsp)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.93:
        fldt      80(%rsp)
        fld       %st(1)
        movzwl    248(%rsp), %edx
        fadd      %st(3), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        andl      $32767, %edx
        movzwl    40(%rsp), %eax
        fsubr     %st, %st(3)
        andl      $32767, %eax
        fxch      %st(2)
        faddp     %st, %st(3)
        cmpl      %edx, %eax
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fadd      %st(1), %st
        jg        ..B2.98
..B2.94:
        jne       ..B2.99
..B2.95:
        movl      36(%rsp), %eax
        movl      244(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.98
..B2.96:
        jne       ..B2.99
..B2.97:
        movl      32(%rsp), %eax
        cmpl      240(%rsp), %eax
        jbe       ..B2.99
..B2.98:
        fldt      16(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rsp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B2.100
..B2.99:
        fldt      16(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        faddp     %st, %st(1)
..B2.100:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%r9)
        fldt      (%r9)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r9)
        jmp       ..B2.104
..B2.101:
        cmpl      $1, %edi
        je        ..B2.108
..B2.122:
        fstp      %st(1)
..B2.102:
        lea       16+ones(%rip), %rax
        fldt      (%rax)
        fxch      %st(2)
        fucomip   %st(2), %st
        jp        ..B2.103
        je        ..B2.107
..B2.103:
        fstp      %st(1)
        lea       _infs(%rip), %rax
        lea       ones(%rip), %rdx
        fldl      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%r9)
        fldt      (%rdx)
..B2.104:
        movq      432(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.106
..B2.105:
        addq      $440, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 448
..B2.106:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B2.107:
        fdivrp    %st, %st(1)
        lea       ones(%rip), %rax
        fstpt     (%r9)
        fldt      (%rax)
        jmp       ..B2.104
..B2.108:
        cmpl      $16383, %esi
        jne       ..B2.122
..B2.109:
        movq      $0x8000000000000000, %rdx
        cmpq      (%rsp), %rdx
        jne       ..B2.122
..B2.110:
        fxch      %st(1)
        fstpt     (%rsp)
        testb     $-128, 9(%rsp)
        jne       ..B2.102
..B2.111:
        cmpl      $0, 4(%rsp)
        jne       ..B2.114
..B2.113:
        cmpl      $0, (%rsp)
        je        ..B2.102
..B2.114:
        fstpt     (%r9)
        fldt      (%r9)
        jmp       ..B2.4
..B2.115:
        fstp      %st(1)
        testl     %edi, %edi
        je        ..B2.119
..B2.116:
        cmpl      $-2147483648, %r10d
        jne       ..B2.119
..B2.117:
        cmpl      $0, (%rsp)
        jne       ..B2.119
..B2.118:
        fstp      %st(1)
        lea       _infs(%rip), %rax
        lea       ones(%rip), %rdx
        fldl      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%r9)
        fldt      (%rdx)
        jmp       ..B2.104
..B2.119:
        fstp      %st(0)
        lea       ones(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r9)
        jmp       ..B2.104
        .align    16,0x90
	.cfi_endproc
	.type	__libm_log1pl_k80,@function
	.size	__libm_log1pl_k80,.-__libm_log1pl_k80
	.data
# -- End  __libm_log1pl_k80
	.section .rodata, "a"
	.align 16
	.align 16
zeros:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	zeros,@object
	.size	zeros,32
	.align 16
ones:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	ones,@object
	.size	ones,32
	.align 16
t32:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	t32,@object
	.size	t32,32
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_LN2_K80:
	.word	0
	.word	0
	.word	6136
	.word	45426
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	12704
	.word	55409
	.word	6480
	.word	47298
	.word	49116
	.word	0
	.word	0
	.word	0
	.type	_LN2_K80,@object
	.size	_LN2_K80,32
	.align 2
_S:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16317
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	52429
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	37449
	.word	18724
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	52429
	.word	52428
	.word	52428
	.word	52428
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	11916
	.word	59578
	.word	35746
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_S,@object
	.size	_S,192
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,16
	.align 2
_LN2:
	.word	0
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	2022
	.word	26416
	.word	37831
	.word	62295
	.word	16333
	.word	0
	.word	0
	.word	0
	.type	_LN2,@object
	.size	_LN2,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
