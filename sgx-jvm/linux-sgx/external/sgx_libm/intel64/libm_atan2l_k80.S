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
	.file "libm_atan2l_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_atan2l_k80
	.text
       .align    16,0x90
	.globl __libm_atan2l_k80
__libm_atan2l_k80:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B1.1:
	.cfi_startproc
..___tag_value___libm_atan2l_k80.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $224, %rsp
	.cfi_def_cfa_offset 272
        movq      %rdi, %r15
        movb      9(%rsi), %cl
        andb      $-128, %cl
        shrb      $7, %cl
        movzbl    9(%rdx), %r14d
        movzwl    8(%rsi), %ebp
        andl      $128, %r14d
        andl      $32767, %ebp
        movq      %fs:40, %rax
        movzbl    %cl, %ebx
        xorq      %rsp, %rax
        movzwl    8(%rdx), %ecx
        shrl      $7, %r14d
        andl      $32767, %ecx
        movq      %rax, 208(%rsp)
        cmpl      $32767, %ebp
        je        ..B1.104
..B1.2:
        cmpl      $32767, %ecx
        je        ..B1.92
..B1.3:
        cmpq      $0, (%rsi)
        jne       ..B1.11
..B1.4:
        cmpq      $0, (%rdx)
        jne       ..B1.8
..B1.5:
        movl      $0, 32(%r15)
        testl     %r14d, %r14d
        je        ..B1.7
..B1.6:
        shlq      $4, %rbx
        lea       _pilo_00l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_00l(%rip), %rax
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.13
..B1.7:
        shlq      $4, %rbx
        lea       zeros(%rip), %rax
        lea       ones(%rip), %rdx
        fldt      (%rax)
        fstpt     16(%r15)
        fldt      16(%r15)
        fldt      (%rdx,%rbx)
        fmulp     %st, %st(1)
        fstpt     (%r15)
        jmp       ..B1.13
..B1.8:
        movl      $0, 32(%r15)
        testl     %r14d, %r14d
        je        ..B1.10
..B1.9:
        shlq      $4, %rbx
        lea       _pilo_00l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_00l(%rip), %rax
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.13
..B1.10:
        shlq      $4, %rbx
        lea       zeros(%rip), %rax
        lea       ones(%rip), %rdx
        fldt      (%rax)
        fstpt     16(%r15)
        fldt      16(%r15)
        fldt      (%rdx,%rbx)
        fmulp     %st, %st(1)
        fstpt     (%r15)
        jmp       ..B1.13
..B1.11:
        cmpq      $0, (%rdx)
        jne       ..B1.15
..B1.12:
        shlq      $4, %rbx
        lea       _pilo_02l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_02l(%rip), %rax
        movl      $0, 32(%r15)
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
..B1.13:
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
..B1.14:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.15:
        movq      (%rdx), %rax
        lea       112(%rsp), %rbp
        movq      8(%rdx), %rcx
        movq      %rbp, %rdi
        movq      16(%rdx), %r8
        movq      24(%rdx), %r9
        movq      32(%rdx), %r10
        movq      40(%rdx), %rdx
        movq      %rax, (%rbp)
        movq      %rcx, 8(%rbp)
        movq      %rdx, 40(%rbp)
        movq      (%rsi), %r11
        movq      8(%rsi), %r13
        movq      16(%rsi), %rax
        movq      24(%rsi), %rdx
        movq      32(%rsi), %rcx
        movq      40(%rsi), %rsi
        movq      %r8, 16(%rbp)
        movq      %r9, 24(%rbp)
        movq      %r10, 32(%rbp)
        movq      %r11, 48(%rbp)
        movq      %r13, 56(%rbp)
        movq      %rax, 64(%rbp)
        movq      %rdx, 72(%rbp)
        movq      %rcx, 80(%rbp)
        movq      %rsi, 88(%rbp)
..___tag_value___libm_atan2l_k80.31:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.32:
..B1.16:
        lea       160(%rsp), %rdi
..___tag_value___libm_atan2l_k80.33:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.34:
..B1.17:
        fldt      112(%rsp)
        movq      %rbx, %rdx
        fld       %st(0)
        lea       ones(%rip), %r13
        fldt      128(%rsp)
        movl      %r14d, %eax
        fadd      %st, %st(1)
        shlq      $4, %rax
        fxch      %st(1)
        fsub      %st, %st(2)
        shlq      $4, %rdx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      160(%rsp)
        fld       %st(0)
        fldt      176(%rsp)
        movl      144(%rsp), %ecx
        fadd      %st, %st(1)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      (%rax,%r13)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldt      (%rdx,%r13)
        movl      192(%rsp), %eax
        cmpl      %eax, %ecx
        fmul      %st, %st(2)
        fmul      %st, %st(1)
        jg        ..B1.20
..B1.18:
        jne       ..B1.70
..B1.19:
        fxch      %st(4)
        fcomi     %st(2), %st
        fxch      %st(4)
        jbe       ..B1.70
..B1.20:
        lea       12(%rax), %edx
        cmpl      %edx, %ecx
        jle       ..B1.65
..B1.21:
        lea       75(%rax), %edx
        cmpl      %edx, %ecx
        jle       ..B1.38
..B1.22:
        testl     %r14d, %r14d
        je        ..B1.24
..B1.23:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        shlq      $4, %rbx
        lea       _pilo_00l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_00l(%rip), %rax
        movl      $0, 32(%r15)
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.36
..B1.24:
        fxch      %st(2)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        movzwl    168(%rsp), %edx
        andl      $32767, %edx
        fxch      %st(4)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        cmpl      $8383, %edx
        jge       ..B1.26
..B1.25:
        fstp      %st(4)
        fstp      %st(3)
        fxch      %st(2)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
        jmp       ..B1.32
..B1.26:
        cmpl      $24383, %edx
        jg        ..B1.25
..B1.28:
        movzwl    120(%rsp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.25
..B1.30:
        cmpl      $24383, %edx
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(4)
        fxch      %st(1)
        jle       ..B1.35
..B1.31:
        fstp      %st(1)
        fstp      %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
..B1.32:
        fstpt     64(%rsp)
        lea       160(%rsp), %rdi
..___tag_value___libm_atan2l_k80.35:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.36:
..B1.33:
        fldt      64(%rsp)
        movq      %rbp, %rdi
        fstpt     64(%rsp)
..___tag_value___libm_atan2l_k80.37:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.38:
..B1.34:
        fldt      64(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        fldt      160(%rsp)
        fldt      176(%rsp)
        movl      144(%rsp), %ecx
        movl      192(%rsp), %eax
..B1.35:
        fld       %st(1)
        subl      %ecx, %eax
        fdiv      %st(4), %st
        lea       _small_value_80(%rip), %rdx
        movl      %eax, 32(%r15)
        lea       t32(%rip), %rax
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(5)
        fstpt     64(%rsp)
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rax)
        fld       %st(0)
        fmul      %st(6), %st
        fld       %st(0)
        fsubr     %st(7), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fldt      (%rsp)
        fdivrp    %st, %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        fldt      (%r13)
        fldt      (%rdx)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
..B1.36:
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
..B1.37:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.38:
        fldt      (%r13)
        subl      %ecx, %eax
        movzwl    8(%r13), %ebx
        movl      %ebx, %edx
        addl      $16383, %eax
        andl      $-32768, %edx
        andl      $32767, %eax
        xorl      %ecx, %ecx
        fstpt     96(%rsp)
        fldt      96(%rsp)
        orl       %eax, %edx
        movw      %dx, 104(%rsp)
        xorl      %edx, %edx
        fxch      %st(5)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        xorl      %esi, %esi
        fxch      %st(5)
        fstpt     48(%rsp)
        fldt      96(%rsp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        movzwl    168(%rsp), %eax
        andl      $32767, %eax
        fxch      %st(3)
        fmulp     %st, %st(2)
        testl     %r14d, %r14d
        je        ..B1.52
..B1.39:
        cmpl      $8383, %eax
        jge       ..B1.41
..B1.40:
        fstp      %st(2)
        fstp      %st(3)
        movl      %edx, 192(%rsp)
        movl      %edx, 144(%rsp)
        fxch      %st(2)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
        jmp       ..B1.47
..B1.41:
        cmpl      $24383, %eax
        jg        ..B1.40
..B1.43:
        movzwl    120(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.40
..B1.45:
        cmpl      $24383, %eax
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jle       ..B1.50
..B1.46:
        fstp      %st(1)
        fstp      %st(2)
        movl      %edx, 192(%rsp)
        movl      %edx, 144(%rsp)
        fxch      %st(1)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
..B1.47:
        fstpt     64(%rsp)
        lea       160(%rsp), %rdi
..___tag_value___libm_atan2l_k80.56:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.57:
..B1.48:
        fldt      64(%rsp)
        movq      %rbp, %rdi
        fstpt     64(%rsp)
..___tag_value___libm_atan2l_k80.58:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.59:
..B1.49:
        fldt      64(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        fldt      160(%rsp)
        fldt      176(%rsp)
        movl      144(%rsp), %ecx
        movl      192(%rsp), %esi
..B1.50:
        fld       %st(1)
        lea       t32(%rip), %rax
        fdiv      %st(4), %st
        subl      %ecx, %esi
        fstpt     16(%rsp)
        fldt      16(%rsp)
        addl      $16383, %esi
        fxch      %st(5)
        fstpt     64(%rsp)
        andl      $-32768, %ebx
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        andl      $32767, %esi
        fldt      (%rax)
        orl       %esi, %ebx
        fld       %st(0)
        lea       48+_P(%rip), %rdx
        fmul      %st(6), %st
        lea       16+_P(%rip), %rcx
        fld       %st(0)
        lea       _P(%rip), %rbp
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rsi
        fsubr     %st(7), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fldt      (%rsp)
        fdivrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fstpt     96(%rsp)
        fldt      (%rdx)
        fldt      (%rcx)
        fldt      (%rbp)
        movw      %bx, 104(%rsp)
        lea       32+_P(%rip), %rbx
        fldt      96(%rsp)
        fmulp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      (%rbx)
        fmulp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldl      8192(%rsi)
        fldl      8200(%rsi)
        movl      $0, 32(%r15)
        fsubp     %st, %st(2)
        fldt      64(%rsp)
        fmul      %st, %st(1)
        fld       %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fadd      %st(2), %st
        fstpt     (%r15)
        fldt      (%r15)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rdi
        xorq      %rsp, %rdi
        cmpq      %fs:40, %rdi
        jne       ..B1.84
..B1.51:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.52:
        cmpl      $8383, %eax
        jge       ..B1.54
..B1.53:
        fstp      %st(2)
        fstp      %st(3)
        movl      %edx, 192(%rsp)
        movl      %edx, 144(%rsp)
        fxch      %st(2)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
        jmp       ..B1.60
..B1.54:
        cmpl      $24383, %eax
        jg        ..B1.53
..B1.56:
        movzwl    120(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.53
..B1.58:
        cmpl      $24383, %eax
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jle       ..B1.63
..B1.59:
        fstp      %st(1)
        fstp      %st(2)
        movl      %edx, 192(%rsp)
        movl      %edx, 144(%rsp)
        fxch      %st(1)
        fstpt     176(%rsp)
        fstpt     128(%rsp)
..B1.60:
        fstpt     64(%rsp)
        lea       160(%rsp), %rdi
..___tag_value___libm_atan2l_k80.77:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.78:
..B1.61:
        fldt      64(%rsp)
        movq      %rbp, %rdi
        fstpt     64(%rsp)
..___tag_value___libm_atan2l_k80.79:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_atan2l_k80.80:
..B1.62:
        fldt      64(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        fldt      160(%rsp)
        fldt      176(%rsp)
        movl      144(%rsp), %ecx
        movl      192(%rsp), %esi
..B1.63:
        fld       %st(1)
        lea       t32(%rip), %rax
        fdiv      %st(4), %st
        subl      %ecx, %esi
        fstpt     16(%rsp)
        fldt      16(%rsp)
        addl      $16383, %esi
        fxch      %st(5)
        fstpt     64(%rsp)
        andl      $-32768, %ebx
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        andl      $32767, %esi
        fldt      (%rax)
        orl       %esi, %ebx
        fld       %st(0)
        lea       48+_P(%rip), %rdx
        fmul      %st(6), %st
        lea       16+_P(%rip), %rcx
        fld       %st(0)
        lea       _P(%rip), %rbp
        movl      $0, 32(%r15)
        fsubr     %st(7), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fldt      (%rsp)
        fdivrp    %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        fstpt     96(%rsp)
        fldt      (%rdx)
        fldt      (%rcx)
        fldt      (%rbp)
        movw      %bx, 104(%rsp)
        lea       32+_P(%rip), %rbx
        fldt      96(%rsp)
        fmul      %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(6)
        fmulp     %st, %st(6)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      (%rbx)
        fmulp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rsi
        xorq      %rsp, %rsi
        cmpq      %fs:40, %rsi
        jne       ..B1.84
..B1.64:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.65:
        fldt      (%r13)
        subl      %ecx, %eax
        addl      $16383, %eax
        lea       _TWO_63H(%rip), %rcx
        movzwl    8(%r13), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        lea       _TWO_48H(%rip), %rbx
        fstpt     96(%rsp)
        fldt      96(%rsp)
        orl       %eax, %edx
        movw      %dx, 104(%rsp)
        fld       %st(5)
        fxch      %st(1)
        fstpt     (%rsp)
        lea       16+ones(%rip), %rbp
        fxch      %st(1)
        fstpt     64(%rsp)
        lea       48+_P(%rip), %rsi
        fxch      %st(4)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        lea       32+_P(%rip), %r8
        fxch      %st(3)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        lea       16+_P(%rip), %rdi
        fldt      96(%rsp)
        lea       _P(%rip), %r9
        fmul      %st, %st(3)
        xorl      %eax, %eax
        fxch      %st(3)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fldt      .L_2il0floatpacket.11(%rip)
        fmul      %st(3), %st
        fdiv      %st(4), %st
        fldt      .L_2il0floatpacket.12(%rip)
        movl      %eax, 144(%rsp)
        movl      %eax, 192(%rsp)
        fxch      %st(1)
        faddl     (%rcx)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      (%rbx)
        movslq    80(%rsp), %rdx
        shlq      $4, %rdx
        fxch      %st(1)
        fsubl     (%rcx)
        fmulp     %st, %st(2)
        fld       %st(5)
        fmul      %st(1), %st
        testl     %r14d, %r14d
        fadd      %st, %st(7)
        fxch      %st(7)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubp     %st, %st(7)
        fxch      %st(5)
        fsub      %st(6), %st
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(5), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(1), %st
        fadd      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        fadd      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fsubrp    %st, %st(1)
        fld       %st(4)
        fadd      %st(2), %st
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     80(%rsp)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      80(%rsp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(5), %st
        fld       %st(6)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st, %st(6)
        fldt      (%rbp)
        faddp     %st, %st(7)
        fxch      %st(1)
        faddp     %st, %st(6)
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fld       %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      (%rsi)
        fmul      %st(1), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        fmulp     %st, %st(2)
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      64(%rsp)
        je        ..B1.67
..B1.66:
        negq      %rdx
        fld       %st(2)
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rax
        fldl      8192(%rax,%rdx)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      8200(%rax,%rdx)
        fsubp     %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.68
..B1.67:
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rax
        fld       %st(2)
        fldl      (%rdx,%rax)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      8(%rdx,%rax)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
..B1.68:
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      $0, 32(%r15)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fldt      (%r15)
        fsubp     %st, %st(2)
        fsubp     %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
..B1.69:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.70:
        lea       12(%rcx), %edx
        cmpl      %edx, %eax
        jle       ..B1.79
..B1.71:
        fstp      %st(3)
        fstp      %st(0)
        lea       75(%rcx), %edx
        cmpl      %edx, %eax
        jg        ..B1.77
..B1.72:
        fldt      (%r13)
        subl      %ecx, %eax
        addl      $16383, %eax
        lea       32+_P(%rip), %rbx
        movzwl    8(%r13), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        lea       16+_P(%rip), %rcx
        fstpt     96(%rsp)
        orl       %eax, %edx
        movw      %dx, 104(%rsp)
        lea       48+_P(%rip), %rax
        fldt      96(%rsp)
        lea       _P(%rip), %rbp
        fmulp     %st, %st(1)
        testl     %r14d, %r14d
        fdivrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rcx)
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rbx)
        fmulp     %st, %st(2)
        fldt      (%rbp)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldl      4096(%rsi)
        fmul      %st(1), %st
        fldl      4104(%rsi)
        movl      $0, 32(%r15)
        je        ..B1.74
..B1.73:
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        jmp       ..B1.75
..B1.74:
        fsubp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
..B1.75:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%r15)
        fldt      (%r15)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
..B1.76:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.77:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        shlq      $4, %rbx
        lea       _pilo_02l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_02l(%rip), %rax
        movl      $0, 32(%r15)
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        jne       ..B1.84
..B1.78:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.79:
        fldt      (%r13)
        subl      %ecx, %eax
        addl      $16383, %eax
        lea       _TWO_63H(%rip), %rcx
        movzwl    8(%r13), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        lea       _TWO_48H(%rip), %rbx
        fstpt     96(%rsp)
        fldt      96(%rsp)
        orl       %eax, %edx
        movw      %dx, 104(%rsp)
        fld       %st(5)
        fxch      %st(2)
        fstpt     64(%rsp)
        lea       16+ones(%rip), %rbp
        fstpt     (%rsp)
        lea       48+_P(%rip), %rsi
        fxch      %st(4)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        lea       16+_P(%rip), %rdi
        fxch      %st(3)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        lea       32+_P(%rip), %r8
        fldt      96(%rsp)
        lea       _P(%rip), %r9
        fmul      %st, %st(3)
        xorl      %eax, %eax
        fxch      %st(3)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fldt      .L_2il0floatpacket.11(%rip)
        fmul      %st(4), %st
        fdiv      %st(3), %st
        fldt      .L_2il0floatpacket.12(%rip)
        movl      %eax, 144(%rsp)
        movl      %eax, 192(%rsp)
        fxch      %st(1)
        faddl     (%rcx)
        fstpt     80(%rsp)
        fld       %st(4)
        fldt      80(%rsp)
        movslq    80(%rsp), %rdx
        shlq      $4, %rdx
        fsubl     (%rcx)
        fmulp     %st, %st(2)
        testl     %r14d, %r14d
        fldl      (%rbx)
        fmul      %st, %st(7)
        fxch      %st(1)
        fadd      %st(7), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubp     %st, %st(7)
        fxch      %st(5)
        fsub      %st(6), %st
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(5), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(5)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fld       %st(2)
        fmul      %st(1), %st
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      16(%rsp)
        fadd      %st(2), %st
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     80(%rsp)
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      80(%rsp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(4), %st
        fldt      16(%rsp)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st(4), %st
        fldt      (%rbp)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fsubp     %st, %st(3)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fadd      %st(4), %st
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmul      %st(1), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        fmulp     %st, %st(2)
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      64(%rsp)
        je        ..B1.81
..B1.80:
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rax
        fld       %st(2)
        fldl      4096(%rax,%rdx)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      4104(%rax,%rdx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.82
..B1.81:
        negq      %rdx
        fld       %st(2)
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rax
        fldl      4096(%rax,%rdx)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      4104(%rax,%rdx)
        fsubp     %st, %st(3)
        faddp     %st, %st(2)
..B1.82:
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      $0, 32(%r15)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fldt      (%r15)
        fsubp     %st, %st(2)
        fsubp     %st, %st(1)
        fstpt     16(%r15)
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
..B1.83:
        addq      $224, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 272
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.84:
        call      __stack_chk_fail@PLT
..B1.92:
        movq      $0x8000000000000000, %rax
        cmpq      (%rdx), %rax
        je        ..B1.97
..B1.93:
        fldt      (%rdx)
        lea       zeros(%rip), %rax
        fldt      (%rsi)
        movl      $0, 32(%r15)
        fmulp     %st, %st(1)
        fstpt     (%r15)
        fldt      (%rax)
        fstpt     16(%r15)
..B1.94:
        movq      208(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.84
        jmp       ..B1.14
..B1.97:
        movl      $0, 32(%r15)
        cmpl      $32767, %ebp
        je        ..B1.101
..B1.98:
        testl     %r14d, %r14d
        je        ..B1.100
..B1.99:
        shlq      $4, %rbx
        lea       _pilo_00l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_00l(%rip), %rax
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.94
..B1.100:
        shlq      $4, %rbx
        lea       zeros(%rip), %rax
        lea       ones(%rip), %rdx
        fldt      (%rax)
        fstpt     16(%r15)
        fldt      16(%r15)
        fldt      (%rdx,%rbx)
        fmulp     %st, %st(1)
        fstpt     (%r15)
        jmp       ..B1.94
..B1.101:
        shlq      $4, %rbx
        testl     %r14d, %r14d
        je        ..B1.103
..B1.102:
        lea       _pilo_34l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_34l(%rip), %rax
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.94
..B1.103:
        lea       _pilo_04l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_04l(%rip), %rax
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.94
..B1.104:
        movq      $0x8000000000000000, %rax
        cmpq      (%rsi), %rax
        jne       ..B1.93
..B1.105:
        cmpl      $32767, %ecx
        je        ..B1.92
..B1.106:
        shlq      $4, %rbx
        lea       _pilo_02l(%rip), %rdx
        lea       _small_value_80(%rip), %rcx
        lea       _pi_02l(%rip), %rax
        movl      $0, 32(%r15)
        fldt      (%rbx,%rdx)
        fldt      (%rbx,%rax)
        fstpt     (%r15)
        fldt      (%rbx,%rcx)
        fsubrp    %st, %st(1)
        fstpt     16(%r15)
        jmp       ..B1.94
        .align    16,0x90
	.cfi_endproc
	.type	__libm_atan2l_k80,@function
	.size	__libm_atan2l_k80,.-__libm_atan2l_k80
	.data
# -- End  __libm_atan2l_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 16
.L_2il0floatpacket.12:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,16
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
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 2
_pilo_00l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16318
	.word	0
	.word	0
	.word	0
	.type	_pilo_00l,@object
	.size	_pilo_00l,32
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.word	0
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,32
	.align 2
_pi_00l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49152
	.word	0
	.word	0
	.word	0
	.type	_pi_00l,@object
	.size	_pi_00l,32
	.align 2
_pilo_02l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16317
	.word	0
	.word	0
	.word	0
	.type	_pilo_02l,@object
	.size	_pilo_02l,32
	.align 2
_pi_02l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49151
	.word	0
	.word	0
	.word	0
	.type	_pi_02l,@object
	.size	_pi_02l,32
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,64
	.align 2
_pilo_34l:
	.word	54470
	.word	48821
	.word	27694
	.word	55510
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	54470
	.word	48821
	.word	27694
	.word	55510
	.word	16319
	.word	0
	.word	0
	.word	0
	.type	_pilo_34l,@object
	.size	_pilo_34l,32
	.align 2
_pi_34l:
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	49152
	.word	0
	.word	0
	.word	0
	.type	_pi_34l,@object
	.size	_pi_34l,32
	.align 2
_pilo_04l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49084
	.word	0
	.word	0
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16316
	.word	0
	.word	0
	.word	0
	.type	_pilo_04l,@object
	.size	_pilo_04l,32
	.align 2
_pi_04l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49150
	.word	0
	.word	0
	.word	0
	.type	_pi_04l,@object
	.size	_pi_04l,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
