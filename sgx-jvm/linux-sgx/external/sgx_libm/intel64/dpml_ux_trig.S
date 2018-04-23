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
	.file "dpml_ux_trig.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_radian_reduce__
	.text
       .align    16,0x90
__dpml_ux_radian_reduce__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B1.1:
	.cfi_startproc
..___tag_value___dpml_ux_radian_reduce__.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        movq      8(%rdi), %rax
        movq      16(%rdi), %rcx
        movl      (%rdi), %r8d
        movq      %rdx, 48(%rsp)
        movq      %rsi, %rdx
        movslq    4(%rdi), %rsi
        testq     %rsi, %rsi
        movq      %rax, 80(%rsp)
        movq      %rcx, 104(%rsp)
        movl      %r8d, 40(%rsp)
        jl        ..B1.21
..B1.2:
        addq      $8, %rsi
        movq      %rsi, %rbx
        sarq      $6, %rbx
        movq      __four_over_pi@GOTPCREL(%rip), %rax
        movq      %rbx, 56(%rsp)
        lea       (%rax,%rbx,8), %rbp
        movq      32(%rbp), %rcx
        movq      (%rbp), %rbx
        movq      8(%rbp), %r11
        movq      16(%rbp), %rdi
        movq      24(%rbp), %rax
        addq      $40, %rbp
        movq      %rcx, 8(%rsp)
        andq      $63, %rsi
        movq      %rbp, (%rsp)
        je        ..B1.4
..B1.3:
        movq      %rsi, %rbp
        movl      %esi, %ecx
        negq      %rbp
        movq      %r11, %r8
        movq      %rbp, 56(%rsp)
        movq      %rdi, %r9
        shlq      %cl, %rbx
        movl      %ebp, %ecx
        shrq      %cl, %r8
        movl      %esi, %ecx
        shlq      %cl, %r11
        movl      %ebp, %ecx
        shrq      %cl, %r9
        movl      %esi, %ecx
        shlq      %cl, %rdi
        movl      %ebp, %ecx
        movq      %rax, %r10
        orq       %r8, %rbx
        shrq      %cl, %r10
        movl      %esi, %ecx
        shlq      %cl, %rax
        movl      %ebp, %ecx
        movq      8(%rsp), %r12
        orq       %r9, %r11
        shrq      %cl, %r12
        orq       %r10, %rdi
        orq       %r12, %rax
..B1.4:
        movq      %rdx, 32(%rsp)
        xorl      %r13d, %r13d
        movl      %eax, %edx
        movq      104(%rsp), %r10
        movq      %r10, %r8
        movl      %r10d, %ebp
        movq      %rdx, %r15
        movq      %rbx, 24(%rsp)
        movq      %rax, %rbx
        shrq      $32, %r8
        movq      %rdx, %r12
        imulq     %rbp, %r15
        imulq     %r8, %r12
        shrq      $32, %rbx
        movq      %rbp, %r9
        imulq     %rbx, %r9
        movq      %rsi, 112(%rsp)
        movq      %r10, %rsi
        imulq     %rax, %rsi
        shrq      $32, %r15
        movq      %r8, %r14
        addq      %r12, %r15
        addq      %r15, %r9
        cmpq      %r15, %r9
        movq      %r9, %r12
        movq      80(%rsp), %r9
        setb      %r13b
        shrq      $32, %r12
        shlq      $32, %r13
        movq      %rsi, 16(%rsp)
        addq      %r13, %r12
        movl      %r9d, %ecx
        movq      %r9, %rsi
        movq      %rdx, %r13
        movq      %rcx, %r15
        shrq      $32, %rsi
        imulq     %rcx, %r13
        imulq     %rsi, %rdx
        imulq     %rbx, %r15
        imulq     %rbx, %r14
        imulq     %r9, %rax
        imulq     %rsi, %rbx
        shrq      $32, %r13
        addq      %r14, %r12
        addq      %rdx, %r13
        addq      %rax, %r12
        addq      %r13, %r15
        cmpq      %r13, %r15
        movq      %r15, %rdx
        movl      $0, %r15d
        setb      %r15b
        cmpq      %rax, %r12
        movl      $0, %eax
        setb      %al
        shrq      $32, %rdx
        shlq      $32, %r15
        addq      %r15, %rdx
        movq      %r10, %r15
        addq      %rbx, %rdx
        addq      %rax, %rdx
        movq      %rdi, %rbx
        movl      %edi, %eax
        movq      %rax, %r14
        movq      %rax, %r13
        imulq     %rbp, %r14
        imulq     %r8, %r13
        imulq     %rdi, %r15
        imulq     %r9, %rdi
        shrq      $32, %rbx
        addq      %r15, %r12
        movq      %rbp, 88(%rsp)
        imulq     %rbx, %rbp
        shrq      $32, %r14
        addq      %r13, %r14
        addq      %r14, %rbp
        cmpq      %r14, %rbp
        movl      $0, %r14d
        movq      %rbp, %r13
        movq      %r8, %rbp
        setb      %r14b
        cmpq      %r15, %r12
        movl      $0, %r15d
        movq      %rcx, 64(%rsp)
        setb      %r15b
        imulq     %rbx, %rbp
        shrq      $32, %r13
        shlq      $32, %r14
        addq      %r14, %r13
        movq      %rax, %r14
        imulq     %rcx, %r14
        imulq     %rsi, %rax
        imulq     %rbx, %rcx
        imulq     %rsi, %rbx
        addq      %rbp, %r13
        addq      %r15, %rdx
        xorl      %ebp, %ebp
        cmpq      %r15, %rdx
        movq      %rsi, 72(%rsp)
        setb      %bpl
        xorl      %esi, %esi
        addq      %r13, %rdx
        cmpq      %r13, %rdx
        movl      $0, %r13d
        setb      %r13b
        addq      %rdi, %rdx
        shrq      $32, %r14
        addq      %rax, %r14
        addq      %r14, %rcx
        addq      %r13, %rbp
        xorl      %r13d, %r13d
        cmpq      %r14, %rcx
        movq      %rcx, %r15
        setb      %r13b
        cmpq      %rdi, %rdx
        movl      $0, %edi
        setb      %dil
        movq      %r10, %r14
        shrq      $32, %r15
        shlq      $32, %r13
        addq      %r13, %r15
        movq      %r11, %r13
        addq      %rbx, %r15
        addq      %rdi, %r15
        addq      %rbp, %r15
        movl      %r11d, %ebp
        movq      %rbp, %rbx
        movq      88(%rsp), %rax
        imulq     %rax, %rbx
        imulq     %r8, %rbp
        imulq     %r11, %r14
        imulq     %r9, %r11
        shrq      $32, %r13
        addq      %r14, %rdx
        imulq     %r13, %rax
        imulq     %r8, %r13
        shrq      $32, %rbx
        addq      %r15, %r11
        addq      %rbp, %rbx
        xorl      %ebp, %ebp
        addq      %rbx, %rax
        cmpq      %rbx, %rax
        movq      %rax, %rdi
        movq      32(%rsp), %rbx
        movq      %rbx, %rcx
        setb      %sil
        cmpq      %r14, %rdx
        setb      %bpl
        negq      %rcx
        shrq      $32, %rdi
        shlq      $32, %rsi
        addq      %rsi, %rdi
        addq      %r13, %rdi
        addq      %rdi, %rbp
        addq      %r11, %rbp
        movq      24(%rsp), %r11
        imulq     %r10, %r11
        cmpl      $0, 40(%rsp)
        movq      %r8, 96(%rsp)
        cmovne    %rcx, %rbx
        addq      %r11, %rbp
        shlq      $61, %rbx
        addq      %rbx, %rbp
        movq      16(%rsp), %r8
        xorl      %ebx, %ebx
        movq      (%rsp), %r10
        movq      8(%rsp), %r11
        jmp       ..B1.5
..B1.9:
        movq      $0xe000000000000000, %rsi
        addq      $64, %rbx
        andq      %rbp, %rsi
        movq      $0x1fffffffffffffff, %rbp
        andq      %rdx, %rbp
        movq      %r12, %rdx
        orq       %rsi, %rbp
        movq      %r8, %r12
        movq      %rcx, %r8
..B1.5:
        movq      $0x40000000000000, %rcx
        movq      $0x3f80000000000000, %rdi
        lea       (%rcx,%rbp), %rsi
        testq     %rdi, %rsi
        jne       ..B1.10
..B1.6:
        movq      %r11, %r9
        movq      112(%rsp), %rsi
        movl      %esi, %ecx
        movq      %r9, %r14
        movq      (%r10), %r11
        movq      %r11, %rdi
        shlq      %cl, %r14
        addq      $8, %r10
        movq      56(%rsp), %rcx
        movq      88(%rsp), %rax
        movq      %rax, %r15
        shrq      %cl, %rdi
        orq       %rdi, %r14
        testq     %rsi, %rsi
        movq      96(%rsp), %r13
        cmovne    %r14, %r9
        movq      %r13, %r14
        movl      %r9d, %esi
        movq      %r9, %rdi
        imulq     %rsi, %r15
        imulq     %rsi, %r14
        shrq      $32, %rdi
        imulq     %rdi, %rax
        imulq     %rdi, %r13
        shrq      $32, %r15
        addq      %r14, %r15
        addq      %r15, %rax
        cmpq      %r15, %rax
        movl      $0, %r15d
        movq      %rax, %r14
        setb      %r15b
        shrq      $32, %r14
        shlq      $32, %r15
        addq      %r15, %r14
        addq      %r13, %r14
        movq      64(%rsp), %r13
        movq      %r13, %rax
        imulq     %rsi, %rax
        movq      72(%rsp), %r15
        addq      %r14, %r8
        imulq     %r15, %rsi
        shrq      $32, %rax
        cmpq      %r14, %r8
        movq      104(%rsp), %rcx
        movl      $0, %r14d
        setb      %r14b
        addq      %rsi, %rax
        movq      %r13, %rsi
        imulq     %rdi, %rsi
        imulq     %r9, %rcx
        imulq     80(%rsp), %r9
        imulq     %r15, %rdi
        addq      %rax, %rsi
        addq      %r14, %r12
        movq      %rsi, %r13
        cmpq      %r14, %r12
        movl      $0, %r14d
        setb      %r14b
        cmpq      %rax, %rsi
        movl      $0, %esi
        setb      %sil
        addq      %r9, %r8
        cmpq      %r9, %r8
        movl      $0, %r9d
        setb      %r9b
        shrq      $32, %r13
        shlq      $32, %rsi
        addq      %rsi, %r13
        addq      %r9, %r12
        addq      %rdi, %r13
        xorl      %esi, %esi
        cmpq      %r9, %r12
        setb      %sil
        addq      %r13, %r12
        cmpq      %r13, %r12
        movl      $0, %r13d
        setb      %r13b
        addl      %r14d, %esi
        addl      %r13d, %esi
        je        ..B1.8
..B1.7:
        incq      %rdx
        lea       1(%rbp), %rsi
        cmove     %rsi, %rbp
..B1.8:
        movq      %rdx, %r9
        movq      %rbp, %rsi
        shrq      $55, %r9
        shlq      $9, %rsi
        orq       %rsi, %r9
        movq      %r9, %rdi
        sarq      $63, %rdi
        cmpq      %r9, %rdi
        je        ..B1.9
..B1.10:
        movq      %r8, 16(%rsp)
..B1.11:
        lea       (,%rbp,4), %rcx
        sarq      $2, %rcx
        movq      %rcx, %rsi
        subq      %rcx, %rbp
        sarq      $63, %rsi
        movq      %rcx, %rdi
        cmpq      %rsi, %rcx
        jne       ..B1.13
..B1.12:
        movq      %rdx, %rcx
        movq      %r12, %rdx
        movq      %r8, %r12
        addq      $64, %rbx
..B1.13:
        movl      $-2147483648, %r10d
        xorl      %esi, %esi
        testq     %rdi, %rdi
        cmovge    %esi, %r10d
        testl     %r10d, %r10d
        je        ..B1.15
..B1.14:
        notq      %r12
        movl      $1, %edi
        incq      %r12
        notq      %rdx
        cmove     %edi, %esi
        xorl      %r8d, %r8d
        notq      %rcx
        addq      %rsi, %rdx
        cmove     %edi, %r8d
        addq      %r8, %rcx
..B1.15:
        movq      %rbp, %r8
        xorl      %esi, %esi
        movq      48(%rsp), %rdi
        negq      %r8
        movl      40(%rsp), %r9d
        testl     %r9d, %r9d
        movl      $3, 4(%rdi)
        cmovne    %r8, %rbp
        xorl      %r9d, %r10d
        movl      %r10d, (%rdi)
        movq      %rcx, 8(%rdi)
        movq      %rdx, 16(%rdi)
..___tag_value___dpml_ux_radian_reduce__.16:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_radian_reduce__.17:
..B1.16:
        movq      48(%rsp), %rdx
        movslq    4(%rdx), %rsi
        movq      %rsi, %rcx
        addq      $-3, %rcx
        je        ..B1.18
..B1.17:
        shrq      %cl, %r12
        orq       %r12, 16(%rdx)
..B1.18:
        movq      %rdx, %rdi
        subq      %rbx, %rsi
        movq      %rdi, %rdx
        movl      %esi, 4(%rdi)
        lea       1008+__trig_x_table(%rip), %rsi
..___tag_value___dpml_ux_radian_reduce__.18:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_radian_reduce__.19:
..B1.19:
        shrq      $62, %rbp
        movq      %rbp, %rax
        addq      $120, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.21:
        movl      %r8d, %ecx
        sarl      $31, %ecx
        movslq    %ecx, %rcx
        addq      %rdx, %rcx
        movq      %rcx, %rbx
        andq      $1, %rbx
        addq      %rbx, %rcx
        movq      %rcx, %rbx
        sarq      $1, %rbx
        subq      %rcx, %rdx
        je        ..B1.23
..B1.22:
        shrq      $63, %rdx
        lea       1008+__trig_x_table(%rip), %rsi
        movq      48(%rsp), %rcx
..___tag_value___dpml_ux_radian_reduce__.40:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_radian_reduce__.41:
        jmp       ..B1.24
..B1.23:
        movq      48(%rsp), %rcx
        movl      %r8d, %edx
        movl      %esi, 4(%rcx)
        movq      %rax, %rsi
        movq      %rsi, 8(%rcx)
        movq      16(%rdi), %rdi
        movl      %edx, (%rcx)
        movq      %rdi, 16(%rcx)
..B1.24:
        movq      %rbx, %rax
        addq      $120, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_radian_reduce__,@function
	.size	__dpml_ux_radian_reduce__,.-__dpml_ux_radian_reduce__
	.data
# -- End  __dpml_ux_radian_reduce__
	.text
# -- Begin  __dpml_ux_degree_reduce__
	.text
       .align    16,0x90
__dpml_ux_degree_reduce__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B2.1:
	.cfi_startproc
..___tag_value___dpml_ux_degree_reduce__.56:
..L57:

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
        subq      $48, %rsp
	.cfi_def_cfa_offset 96
        movq      %rdi, %rbp
        movq      %rdx, %r13
        movq      %rsi, %rbx
        movl      4(%rbp), %r14d
        cmpl      $142, %r14d
        movl      (%rbp), %r15d
        jle       ..B2.3
..B2.2:
        lea       264+__trig_x_table(%rip), %rax
        movslq    %r14d, %r8
        addq      $32641, %r8
        movl      %r8d, %ecx
        movq      (%rax), %r9
        movq      %rcx, %r10
        movl      %r9d, %esi
        shrq      $32, %r9
        imulq     %rsi, %r10
        imulq     %r9, %rcx
        shrq      $32, %r8
        imulq     %r8, %rsi
        imulq     %r8, %r9
        shrq      $32, %r10
        addq      %rcx, %r10
        addq      %rsi, %r10
        shrq      $32, %r10
        addq      %r9, %r10
        lea       (,%r10,8), %r11d
        lea       (%r11,%r10,4), %eax
        negl      %eax
        lea       32772(%r14,%rax), %r14d
        movl      %r14d, 4(%rbp)
..B2.3:
        cmpl      $16, %r14d
        jl        ..B2.25
..B2.4:
        movslq    %r14d, %r14
        xorl      %r8d, %r8d
        lea       -15(%r14), %rsi
        movq      %rsi, %rcx
        andq      $63, %rcx
        sarq      $6, %rsi
        subq      %rcx, %r14
        testq     %rcx, %rcx
        je        ..B2.6
..B2.5:
        movq      16(%rbp), %rax
        movq      %rax, %r8
        movq      %rcx, %r9
        shlq      %cl, %r8
        negq      %r9
        movq      %r8, 16(%rbp)
        movq      8(%rbp), %r8
        movq      %r8, %r10
        shlq      %cl, %r10
        movl      %r9d, %ecx
        shrq      %cl, %rax
        movl      %r9d, %ecx
        orq       %rax, %r10
        movq      %r10, 8(%rbp)
        shrq      %cl, %r8
..B2.6:
        movq      %r8, (%rbp)
        lea       (%rbp,%rsi,8), %rdi
        movq      8(%rdi), %rcx
        movq      %rcx, %r9
        shrq      $52, %r9
        xorl      %r11d, %r11d
        testq     %rsi, %rsi
        jl        ..B2.19
..B2.7:
        xorl      %eax, %eax
        lea       1(%rsi), %rdx
        movq      %rdx, %rcx
        movl      $1, %r8d
        shrq      $63, %rcx
        xorl      %r10d, %r10d
        lea       1(%rsi,%rcx), %rcx
        sarq      $1, %rcx
        testq     %rcx, %rcx
        jbe       ..B2.15
..B2.8:
        movq      (%rbp,%rsi,8), %r8
        movq      %r8, (%rsp)
        movq      $0xfffffffffffffff, %r8
        movq      $0, 8(%rsp)
        movl      %r15d, 32(%rsp)
        movq      %r13, 16(%rsp)
        movq      %rbp, 24(%rsp)
        movq      %rcx, %rbp
        movq      %r12, 40(%rsp)
	.cfi_offset 12, -56
        movq      (%rsp), %r15
        movq      8(%rsp), %r13
..B2.9:
        movq      %r8, %r12
        andq      %r15, %r12
        shrq      $60, %r15
        addq      %r15, %r12
        addq      %r9, %r12
        movq      $0, (%rdi,%r13)
        addq      $4, %r11
        movq      -8(%rdi,%r13), %r15
        je        ..B2.11
..B2.10:
        movl      %r11d, %ecx
        movq      %r15, %r9
        negq      %r11
        addq      $12, %r11
        shlq      %cl, %r9
        movl      %r11d, %ecx
        andq      $4095, %r9
        negq      %r11
        shrq      %cl, %r15
        addq      %r9, %r12
..B2.11:
        movq      %r8, %r9
        incq      %rax
        andq      %r15, %r9
        addq      $4, %r11
        shrq      $60, %r15
        addq      $-16, %r10
        addq      %r15, %r9
        addq      %r12, %r9
        movq      $0, -8(%rdi,%r13)
        cmpq      %rbp, %rax
        jae       ..B2.14
..B2.12:
        movq      (%r10,%rdi), %r15
        movq      %r10, %r13
        testq     %r11, %r11
        je        ..B2.9
..B2.13:
        movl      %r11d, %ecx
        movq      %r15, %r12
        negq      %r11
        addq      $12, %r11
        shlq      %cl, %r12
        movl      %r11d, %ecx
        andq      $4095, %r12
        negq      %r11
        shrq      %cl, %r15
        addq      %r12, %r9
        jmp       ..B2.9
..B2.14:
        movl      32(%rsp), %r15d
        lea       1(,%rax,2), %r8
        movq      24(%rsp), %rbp
        movq      16(%rsp), %r13
        movq      40(%rsp), %r12
	.cfi_restore 12
..B2.15:
        lea       -1(%r8), %rax
        cmpq      %rax, %rdx
        jbe       ..B2.45
..B2.16:
        subq      %r8, %rsi
        testq     %r11, %r11
        movq      8(%rbp,%rsi,8), %rax
        je        ..B2.18
..B2.17:
        movl      %r11d, %ecx
        movq      %rax, %r8
        negq      %r11
        addq      $12, %r11
        shlq      %cl, %r8
        movl      %r11d, %ecx
        andq      $4095, %r8
        shrq      %cl, %rax
        addq      %r8, %r9
..B2.18:
        movq      $0xfffffffffffffff, %rcx
        andq      %rax, %rcx
        shrq      $60, %rax
        movq      $0, 8(%rbp,%rsi,8)
        addq      %rax, %rcx
        addq      %rcx, %r9
        movq      8(%rdi), %rcx
..B2.19:
        movq      %r9, %rax
        shrq      $12, %rax
        testq     %rax, %rax
        je        ..B2.23
..B2.21:
        andq      $4095, %r9
        addq      %rax, %r9
        movq      %r9, %rax
        shrq      $12, %rax
        testq     %rax, %rax
        jne       ..B2.21
..B2.23:
        movq      $0xfffffffffffff, %rax
        xorl      %esi, %esi
        shlq      $52, %r9
        andq      %rcx, %rax
        orq       %r9, %rax
        movq      %rax, 8(%rdi)
        movq      %rbp, %rdi
        movl      %r14d, 4(%rbp)
..___tag_value___dpml_ux_degree_reduce__.71:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_degree_reduce__.72:
..B2.24:
        subq      %rax, %r14
..B2.25:
        movslq    %r14d, %r14
        movl      $0, (%rbp)
        addq      $-5, %r14
        movq      8(%rbp), %r8
        jle       ..B2.27
..B2.26:
        lea       256+__trig_x_table(%rip), %rsi
        movq      %r8, %rax
        movl      %r8d, %ecx
        xorl      %r11d, %r11d
        movq      %rcx, %r10
        shrq      $32, %rax
        movq      (%rsi), %rdx
        movl      %edx, %r9d
        shrq      $32, %rdx
        imulq     %r9, %r10
        imulq     %rdx, %rcx
        imulq     %rax, %r9
        imulq     %rax, %rdx
        shrq      $32, %r10
        addq      %rcx, %r10
        addq      %r10, %r9
        cmpq      %r10, %r9
        movq      %r9, %rsi
        setb      %r11b
        shrq      $32, %rsi
        shlq      $32, %r11
        addq      %r11, %rsi
        addq      %rdx, %rsi
        jmp       ..B2.28
..B2.27:
        movl      $1, %r14d
        xorl      %esi, %esi
..B2.28:
        negq      %r14
        movq      %rbx, %rax
        andq      $1, %rax
        incq      %rax
        shrq      $1, %rbx
        lea       63(%r14), %rcx
        shlq      %cl, %rax
        addq      %rsi, %rax
        movl      %r14d, %ecx
        movl      $1, %esi
        shlq      %cl, %rsi
        movl      %r14d, %ecx
        decq      %rsi
        notq      %rsi
        andq      %rsi, %rax
        movq      %r8, %rsi
        movq      %rax, %r10
        andq      $3, %r8
        shrq      %cl, %r10
        movq      $0x0b4000000, %rcx
        movl      %eax, %r11d
        movq      %r10, %r9
        imulq     %rcx, %r11
        shrq      $32, %rax
        negq      %r9
        imulq     %rax, %rcx
        shrq      $2, %rsi
        testl     %r15d, %r15d
        cmovne    %r9, %r10
        shrq      $32, %r11
        addq      %r10, %rbx
        subq      %r11, %rsi
        subq      %rcx, %rsi
        lea       (,%rsi,4), %rax
        orq       %rax, %r8
        testq     %rsi, %rsi
        jns       ..B2.30
..B2.29:
        movq      16(%rbp), %rax
        movl      $1, %ecx
        xorl      %esi, %esi
        testq     %rax, %rax
        cmovne    %ecx, %esi
        movq      %rax, %r9
        movslq    %r15d, %r15
        negq      %r9
        btcq      $31, %r15
        addq      %rsi, %r8
        movq      %r9, 16(%rbp)
        negq      %r8
..B2.30:
        movq      %rbp, %rdi
        xorl      %esi, %esi
        movq      %r8, 8(%rbp)
..___tag_value___dpml_ux_degree_reduce__.73:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_degree_reduce__.74:
..B2.31:
        shlq      $3, %rbx
        movq      8(%rbp), %rsi
        shrl      $3, %ebx
        movl      4(%rbp), %ecx
        testq     %rsi, %rsi
        jne       ..B2.34
..B2.32:
        movq      $0x2000000000000000, %rcx
        orq       %rcx, %rbx
        testq     $2, %rbx
        je        ..B2.43
..B2.33:
        movslq    (%rbp), %rcx
        btcq      $31, %rcx
        movl      %ecx, (%rbp)
        jmp       ..B2.43
..B2.34:
        cmpl      $5, %ecx
        jl        ..B2.42
..B2.35:
        cmpq      $0, 16(%rbp)
        jne       ..B2.42
..B2.36:
        negl      %ecx
        movq      %rsi, %r9
        shrq      %cl, %r9
        movq      %r9, %r8
        shlq      %cl, %r8
        cmpq      %r8, %rsi
        jne       ..B2.42
..B2.37:
        cmpq      $30, %r9
        jne       ..B2.41
..B2.38:
        testq     $1, %rbx
        jne       ..B2.42
..B2.39:
        movq      $0x4000000000000000, %rcx
        xorl      %r15d, (%rbp)
        orq       %rcx, %rbx
        jmp       ..B2.43
..B2.41:
        movq      $0x8000000000000000, %rcx
        orq       %rbx, %rcx
        cmpq      $45, %r9
        cmove     %rcx, %rbx
..B2.42:
        xorl      %r15d, (%rbp)
..B2.43:
        movq      %rbp, %rdi
        lea       216+__trig_x_table(%rip), %rsi
        movq      %r13, %rdx
..___tag_value___dpml_ux_degree_reduce__.75:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_degree_reduce__.76:
..B2.44:
        movq      %rbx, %rax
        addq      $48, %rsp
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
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B2.45:
        movq      8(%rdi), %rcx
        jmp       ..B2.19
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_degree_reduce__,@function
	.size	__dpml_ux_degree_reduce__,.-__dpml_ux_degree_reduce__
	.data
# -- End  __dpml_ux_degree_reduce__
	.text
# -- Begin  __dpml_ux_sincos
	.text
       .align    16,0x90
	.globl __dpml_ux_sincos
__dpml_ux_sincos:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B3.1:
	.cfi_startproc
..___tag_value___dpml_ux_sincos.95:
..L96:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $136, %rsp
	.cfi_def_cfa_offset 192
        movq      %rdx, %r15
        testq     $16, %r15
        lea       __dpml_ux_degree_reduce__(%rip), %r10
        lea       __dpml_ux_radian_reduce__(%rip), %r9
        movq      %rcx, %r12
        cmovne    %r10, %r9
        movq      %rsi, %rbp
        movq      %fs:40, %rax
        movq      %rdi, %r13
        xorq      %rsp, %rax
        movq      %rax, 128(%rsp)
        cmpq      %r10, %r9
        jne       ..B3.54
..B3.2:
        movl      4(%r13), %ebx
        cmpl      $142, %ebx
        movl      (%r13), %r14d
        jle       ..B3.4
..B3.3:
        lea       264+__trig_x_table(%rip), %rax
        movslq    %ebx, %r9
        addq      $32641, %r9
        movl      %r9d, %ecx
        movq      (%rax), %r10
        movq      %rcx, %r11
        movl      %r10d, %esi
        shrq      $32, %r10
        imulq     %rsi, %r11
        imulq     %r10, %rcx
        shrq      $32, %r9
        imulq     %r9, %rsi
        imulq     %r9, %r10
        shrq      $32, %r11
        addq      %rcx, %r11
        addq      %rsi, %r11
        shrq      $32, %r11
        addq      %r10, %r11
        lea       (,%r11,8), %eax
        lea       (%rax,%r11,4), %eax
        negl      %eax
        lea       32772(%rbx,%rax), %ebx
        movl      %ebx, 4(%r13)
..B3.4:
        cmpl      $16, %ebx
        jl        ..B3.26
..B3.5:
        movslq    %ebx, %rbx
        xorl      %r9d, %r9d
        lea       -15(%rbx), %rax
        movq      %rax, %rcx
        andq      $63, %rcx
        sarq      $6, %rax
        subq      %rcx, %rbx
        testq     %rcx, %rcx
        je        ..B3.7
..B3.6:
        movq      16(%r13), %rsi
        movq      %rsi, %r9
        movq      %rcx, %r10
        shlq      %cl, %r9
        negq      %r10
        movq      %r9, 16(%r13)
        movq      8(%r13), %r9
        movq      %r9, %r11
        shlq      %cl, %r11
        movl      %r10d, %ecx
        shrq      %cl, %rsi
        movl      %r10d, %ecx
        orq       %rsi, %r11
        movq      %r11, 8(%r13)
        shrq      %cl, %r9
..B3.7:
        movq      %r9, (%r13)
        lea       (%r13,%rax,8), %r8
        movq      8(%r8), %rcx
        movq      %rcx, %rsi
        shrq      $52, %rsi
        xorl      %edx, %edx
        testq     %rax, %rax
        jl        ..B3.20
..B3.8:
        xorl      %r10d, %r10d
        lea       1(%rax), %rdi
        movq      %rdi, %rcx
        movl      $1, %r9d
        shrq      $63, %rcx
        xorl      %r11d, %r11d
        lea       1(%rax,%rcx), %rcx
        sarq      $1, %rcx
        testq     %rcx, %rcx
        jbe       ..B3.16
..B3.9:
        movq      (%r13,%rax,8), %r9
        movq      %r9, (%rsp)
        movq      $0xfffffffffffffff, %r9
        movq      $0, 8(%rsp)
        movl      %r14d, 24(%rsp)
        movq      %r13, 32(%rsp)
        movq      %rbx, 16(%rsp)
        movq      %rcx, %rbx
        movq      %r12, 40(%rsp)
        movq      (%rsp), %r14
        movq      8(%rsp), %r13
..B3.10:
        movq      %r9, %r12
        andq      %r14, %r12
        shrq      $60, %r14
        addq      %r14, %r12
        addq      %rsi, %r12
        movq      $0, (%r8,%r13)
        addq      $4, %rdx
        movq      -8(%r8,%r13), %r14
        je        ..B3.12
..B3.11:
        movl      %edx, %ecx
        movq      %r14, %rsi
        negq      %rdx
        addq      $12, %rdx
        shlq      %cl, %rsi
        movl      %edx, %ecx
        andq      $4095, %rsi
        negq      %rdx
        shrq      %cl, %r14
        addq      %rsi, %r12
..B3.12:
        movq      %r9, %rsi
        incq      %r10
        andq      %r14, %rsi
        addq      $4, %rdx
        shrq      $60, %r14
        addq      $-16, %r11
        addq      %r14, %rsi
        addq      %r12, %rsi
        movq      $0, -8(%r8,%r13)
        cmpq      %rbx, %r10
        jae       ..B3.15
..B3.13:
        movq      (%r11,%r8), %r14
        movq      %r11, %r13
        testq     %rdx, %rdx
        je        ..B3.10
..B3.14:
        movl      %edx, %ecx
        movq      %r14, %r12
        negq      %rdx
        addq      $12, %rdx
        shlq      %cl, %r12
        movl      %edx, %ecx
        andq      $4095, %r12
        negq      %rdx
        shrq      %cl, %r14
        addq      %r12, %rsi
        jmp       ..B3.10
..B3.15:
        movq      16(%rsp), %rbx
        lea       1(,%r10,2), %r9
        movl      24(%rsp), %r14d
        movq      32(%rsp), %r13
        movq      40(%rsp), %r12
..B3.16:
        lea       -1(%r9), %rcx
        cmpq      %rcx, %rdi
        jbe       ..B3.69
..B3.17:
        subq      %r9, %rax
        testq     %rdx, %rdx
        movq      8(%r13,%rax,8), %r9
        je        ..B3.19
..B3.18:
        movl      %edx, %ecx
        movq      %r9, %r10
        negq      %rdx
        addq      $12, %rdx
        shlq      %cl, %r10
        movl      %edx, %ecx
        andq      $4095, %r10
        shrq      %cl, %r9
        addq      %r10, %rsi
..B3.19:
        movq      $0xfffffffffffffff, %rcx
        andq      %r9, %rcx
        shrq      $60, %r9
        movq      $0, 8(%r13,%rax,8)
        addq      %r9, %rcx
        addq      %rcx, %rsi
        movq      8(%r8), %rcx
..B3.20:
        movq      %rsi, %rax
        shrq      $12, %rax
        testq     %rax, %rax
        je        ..B3.24
..B3.22:
        andq      $4095, %rsi
        addq      %rax, %rsi
        movq      %rsi, %rax
        shrq      $12, %rax
        testq     %rax, %rax
        jne       ..B3.22
..B3.24:
        movq      $0xfffffffffffff, %rax
        movq      %r13, %rdi
        shlq      $52, %rsi
        andq      %rcx, %rax
        orq       %rsi, %rax
        xorl      %esi, %esi
        movq      %rax, 8(%r8)
        movl      %ebx, 4(%r13)
..___tag_value___dpml_ux_sincos.110:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_sincos.111:
..B3.25:
        subq      %rax, %rbx
..B3.26:
        movslq    %ebx, %rbx
        movl      $0, (%r13)
        addq      $-5, %rbx
        movq      8(%r13), %r9
        jle       ..B3.28
..B3.27:
        lea       256+__trig_x_table(%rip), %rsi
        movq      %r9, %rax
        movl      %r9d, %ecx
        movq      %rcx, %r11
        shrq      $32, %rax
        movq      (%rsi), %rdx
        movl      %edx, %r10d
        shrq      $32, %rdx
        imulq     %r10, %r11
        imulq     %rdx, %rcx
        imulq     %rax, %r10
        imulq     %rax, %rdx
        shrq      $32, %r11
        addq      %rcx, %r11
        xorl      %ecx, %ecx
        addq      %r11, %r10
        cmpq      %r11, %r10
        movq      %r10, %rsi
        setb      %cl
        shrq      $32, %rsi
        shlq      $32, %rcx
        addq      %rcx, %rsi
        addq      %rdx, %rsi
        jmp       ..B3.29
..B3.28:
        movl      $1, %ebx
        xorl      %esi, %esi
..B3.29:
        negq      %rbx
        movq      %rbp, %rax
        andq      $1, %rax
        incq      %rax
        shrq      $1, %rbp
        lea       63(%rbx), %rcx
        shlq      %cl, %rax
        addq      %rsi, %rax
        movl      %ebx, %ecx
        movl      $1, %esi
        shlq      %cl, %rsi
        movl      %ebx, %ecx
        decq      %rsi
        notq      %rsi
        andq      %rsi, %rax
        movq      $0x0b4000000, %rsi
        movq      %rax, %r11
        shrq      %cl, %r11
        movl      %eax, %ecx
        movq      %r11, %r10
        imulq     %rsi, %rcx
        shrq      $32, %rax
        negq      %r10
        imulq     %rax, %rsi
        testl     %r14d, %r14d
        cmovne    %r10, %r11
        movq      %r9, %r10
        shrq      $2, %r10
        andq      $3, %r9
        shrq      $32, %rcx
        addq      %r11, %rbp
        subq      %rcx, %r10
        subq      %rsi, %r10
        lea       (,%r10,4), %rax
        orq       %rax, %r9
        testq     %r10, %r10
        jns       ..B3.31
..B3.30:
        movq      16(%r13), %rax
        movl      $1, %ecx
        xorl      %esi, %esi
        testq     %rax, %rax
        cmovne    %ecx, %esi
        movq      %rax, %r10
        movslq    %r14d, %r14
        negq      %r10
        btcq      $31, %r14
        addq      %rsi, %r9
        movq      %r10, 16(%r13)
        negq      %r9
..B3.31:
        movq      %r13, %rdi
        xorl      %esi, %esi
        movq      %r9, 8(%r13)
..___tag_value___dpml_ux_sincos.112:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_sincos.113:
..B3.32:
        shlq      $3, %rbp
        movq      8(%r13), %rax
        shrl      $3, %ebp
        movl      4(%r13), %ecx
        movl      %ebp, %ebx
        testq     %rax, %rax
        jne       ..B3.35
..B3.33:
        movq      $0x2000000000000000, %rax
        orq       %rax, %rbx
        testq     $2, %rbx
        je        ..B3.44
..B3.34:
        movslq    (%r13), %rax
        btcq      $31, %rax
        movl      %eax, (%r13)
        jmp       ..B3.44
..B3.35:
        cmpl      $5, %ecx
        jl        ..B3.43
..B3.36:
        cmpq      $0, 16(%r13)
        jne       ..B3.43
..B3.37:
        negl      %ecx
        movq      %rax, %rsi
        shrq      %cl, %rsi
        movq      %rsi, %rdx
        shlq      %cl, %rdx
        cmpq      %rdx, %rax
        jne       ..B3.43
..B3.38:
        cmpq      $30, %rsi
        jne       ..B3.42
..B3.39:
        testq     $1, %rbx
        jne       ..B3.43
..B3.40:
        movq      $0x4000000000000000, %rax
        xorl      %r14d, (%r13)
        orq       %rax, %rbx
        jmp       ..B3.44
..B3.42:
        movq      $0x8000000000000000, %rax
        orq       %rbx, %rax
        cmpq      $45, %rsi
        cmove     %rax, %rbx
..B3.43:
        xorl      %r14d, (%r13)
..B3.44:
        movq      %r13, %rdi
        lea       216+__trig_x_table(%rip), %rsi
        lea       (%rsp), %rdx
..___tag_value___dpml_ux_sincos.114:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_sincos.115:
..B3.46:
        andq      $-17, %r15
        movl      $3, %ecx
        movl      $1, %eax
        cmpq      $3, %r15
        movq      %rbx, %rdx
        cmove     %rax, %rcx
        xorl      %r13d, %r13d
        movl      $1, %esi
        sarq      $61, %rdx
        testq     %rcx, %rdx
        movq      %rbx, %rcx
        cmove     %esi, %r13d
        andq      $1, %rcx
        cmpq      $3, %r15
        je        ..B3.68
..B3.47:
        movl      $1216, %edx
        testq     %rcx, %rcx
        movl      $1038, %ecx
        cmovne    %rdx, %rcx
..B3.48:
        lea       272+__trig_x_table(%rip), %rsi
        lea       (%rsp), %rdi
        movl      $13, %edx
        movq      %r12, %r8
..___tag_value___dpml_ux_sincos.116:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_sincos.117:
..B3.49:
        testq     $2, %rbx
        je        ..B3.51
..B3.50:
        movslq    (%r12), %rdx
        btcq      $31, %rdx
        movl      %edx, (%r12)
..B3.51:
        cmpq      $3, %r15
        je        ..B3.64
..B3.52:
        movq      128(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B3.63
..B3.53:
        movq      %r13, %rax
        addq      $136, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.54:
        movq      8(%r13), %r11
        movq      16(%r13), %rax
        movl      (%r13), %ecx
        movq      %r11, 88(%rsp)
        movslq    4(%r13), %r11
        testq     %r11, %r11
        movq      %rax, 112(%rsp)
        movl      %ecx, 56(%rsp)
        jl        ..B3.81
..B3.55:
        addq      $8, %r11
        movq      %r11, %rsi
        sarq      $6, %rsi
        movq      __four_over_pi@GOTPCREL(%rip), %rax
        movq      %rsi, 64(%rsp)
        lea       (%rax,%rsi,8), %r10
        movq      32(%r10), %rcx
        movq      (%r10), %r9
        movq      8(%r10), %rsi
        movq      16(%r10), %rax
        movq      24(%r10), %rdi
        addq      $40, %r10
        movq      %rcx, 8(%rsp)
        andq      $63, %r11
        movq      %r10, (%rsp)
        je        ..B3.57
..B3.56:
        movq      %r11, %r10
        movl      %r11d, %ecx
        negq      %r10
        movq      %rsi, %r13
        movq      %r10, 64(%rsp)
        movq      %rax, %r14
        shlq      %cl, %r9
        movl      %r10d, %ecx
        shrq      %cl, %r13
        movl      %r11d, %ecx
        shlq      %cl, %rsi
        movl      %r10d, %ecx
        shrq      %cl, %r14
        movl      %r11d, %ecx
        shlq      %cl, %rax
        orq       %r13, %r9
        movl      %r10d, %ecx
        movq      %rdi, %r13
        shrq      %cl, %r13
        movl      %r11d, %ecx
        shlq      %cl, %rdi
        movl      %r10d, %ecx
        orq       %r13, %rax
        orq       %r14, %rsi
        movq      8(%rsp), %r13
        shrq      %cl, %r13
        orq       %r13, %rdi
..B3.57:
        movl      %edi, %edx
        movq      %r11, 120(%rsp)
        movq      %rdx, %r13
        movq      112(%rsp), %r11
        movq      %r11, %r8
        movq      %r9, 24(%rsp)
        movq      %r11, %r9
        movq      %r15, 16(%rsp)
        movq      %rdx, %r15
        movl      %r11d, %ebx
        movq      %rbp, 32(%rsp)
        movq      %rdi, %rbp
        shrq      $32, %r9
        movq      %rbx, %r10
        imulq     %rbx, %r15
        imulq     %r9, %r13
        imulq     %rdi, %r8
        shrq      $32, %rbp
        movq      %r9, %r14
        imulq     %rbp, %r10
        imulq     %rbp, %r14
        shrq      $32, %r15
        addq      %r13, %r15
        addq      %r15, %r10
        cmpq      %r15, %r10
        movq      %r10, %r13
        movq      %r12, 40(%rsp)
        movl      $0, %r12d
        setb      %r12b
        shrq      $32, %r13
        shlq      $32, %r12
        movq      88(%rsp), %r10
        addq      %r12, %r13
        movq      %r8, 48(%rsp)
        movq      %r10, %r8
        movl      %r10d, %ecx
        movq      %rdx, %r12
        shrq      $32, %r8
        movq      %rcx, %r15
        imulq     %rcx, %r12
        imulq     %r8, %rdx
        imulq     %rbp, %r15
        imulq     %r10, %rdi
        imulq     %r8, %rbp
        shrq      $32, %r12
        addq      %r14, %r13
        addq      %rdx, %r12
        addq      %rdi, %r13
        addq      %r12, %r15
        cmpq      %r12, %r15
        movq      %r15, %rdx
        movl      $0, %r15d
        setb      %r15b
        cmpq      %rdi, %r13
        movl      $0, %edi
        setb      %dil
        shrq      $32, %rdx
        shlq      $32, %r15
        addq      %r15, %rdx
        movq      %r11, %r15
        addq      %rbp, %rdx
        addq      %rdi, %rdx
        movq      %rax, %rbp
        movl      %eax, %edi
        movq      %rdi, %r14
        movq      %rdi, %r12
        imulq     %rbx, %r14
        imulq     %r9, %r12
        imulq     %rax, %r15
        imulq     %r10, %rax
        shrq      $32, %rbp
        addq      %r15, %r13
        movq      %rbx, 96(%rsp)
        imulq     %rbp, %rbx
        shrq      $32, %r14
        addq      %r12, %r14
        addq      %r14, %rbx
        cmpq      %r14, %rbx
        movl      $0, %r14d
        movq      %rbx, %r12
        movq      %r9, %rbx
        setb      %r14b
        cmpq      %r15, %r13
        movl      $0, %r15d
        movq      %rcx, 72(%rsp)
        setb      %r15b
        imulq     %rbp, %rbx
        shrq      $32, %r12
        shlq      $32, %r14
        addq      %r14, %r12
        movq      %rdi, %r14
        imulq     %rcx, %r14
        imulq     %r8, %rdi
        imulq     %rbp, %rcx
        imulq     %r8, %rbp
        addq      %rbx, %r12
        addq      %r15, %rdx
        xorl      %ebx, %ebx
        cmpq      %r15, %rdx
        movq      %r9, 104(%rsp)
        setb      %bl
        addq      %r12, %rdx
        cmpq      %r12, %rdx
        movl      $0, %r12d
        setb      %r12b
        addq      %rax, %rdx
        shrq      $32, %r14
        addq      %rdi, %r14
        xorl      %edi, %edi
        addq      %r14, %rcx
        addq      %r12, %rbx
        xorl      %r12d, %r12d
        cmpq      %r14, %rcx
        movq      %rcx, %r15
        setb      %r12b
        cmpq      %rax, %rdx
        movl      $0, %eax
        setb      %al
        movq      %r11, %r14
        shrq      $32, %r15
        shlq      $32, %r12
        addq      %r12, %r15
        movq      %rsi, %r12
        addq      %rbp, %r15
        addq      %rax, %r15
        addq      %rbx, %r15
        movl      %esi, %ebx
        movq      %rbx, %rbp
        movq      96(%rsp), %rcx
        imulq     %rcx, %rbp
        imulq     %r9, %rbx
        imulq     %rsi, %r14
        imulq     %r10, %rsi
        shrq      $32, %r12
        addq      %r14, %rdx
        imulq     %r12, %rcx
        imulq     %r9, %r12
        shrq      $32, %rbp
        addq      %r15, %rsi
        addq      %rbx, %rbp
        xorl      %ebx, %ebx
        addq      %rbp, %rcx
        cmpq      %rbp, %rcx
        movq      %rcx, %rax
        movq      32(%rsp), %rcx
        setb      %dil
        cmpq      %r14, %rdx
        setb      %bl
        xorl      %r14d, %r14d
        shrq      $32, %rax
        shlq      $32, %rdi
        addq      %rdi, %rax
        addq      %r12, %rax
        addq      %rax, %rbx
        movq      %rcx, %rax
        addq      %rsi, %rbx
        negq      %rax
        movq      24(%rsp), %rsi
        imulq     %r11, %rsi
        cmpl      $0, 56(%rsp)
        movq      %r8, 80(%rsp)
        cmovne    %rax, %rcx
        addq      %rsi, %rbx
        shlq      $61, %rcx
        movq      120(%rsp), %r11
        addq      %rcx, %rbx
        movq      48(%rsp), %r10
        movq      (%rsp), %r8
        movq      8(%rsp), %r9
        jmp       ..B3.58
..B3.62:
        movq      $0xe000000000000000, %rax
        addq      $64, %r14
        andq      %rbx, %rax
        movq      $0x1fffffffffffffff, %rbx
        andq      %rdx, %rbx
        movq      %r13, %rdx
        orq       %rax, %rbx
        movq      %r10, %r13
        movq      %rcx, %r10
..B3.58:
        movq      $0x40000000000000, %rax
        movq      $0x3f80000000000000, %rsi
        lea       (%rax,%rbx), %rcx
        testq     %rsi, %rcx
        jne       ..B3.70
..B3.59:
        movq      %r9, %rdi
        movq      120(%rsp), %rax
        movl      %eax, %ecx
        movq      %rdi, %r12
        movq      (%r8), %r9
        movq      %r9, %rsi
        shlq      %cl, %r12
        addq      $8, %r8
        movq      64(%rsp), %rcx
        movq      96(%rsp), %rbp
        movq      %rbp, %r15
        shrq      %cl, %rsi
        orq       %rsi, %r12
        testq     %rax, %rax
        movq      104(%rsp), %r11
        cmovne    %r12, %rdi
        movq      %r11, %r12
        movl      %edi, %eax
        movq      %rdi, %rsi
        imulq     %rax, %r15
        imulq     %rax, %r12
        shrq      $32, %rsi
        imulq     %rsi, %rbp
        imulq     %rsi, %r11
        shrq      $32, %r15
        addq      %r12, %r15
        addq      %r15, %rbp
        cmpq      %r15, %rbp
        movl      $0, %r15d
        movq      %rbp, %r12
        setb      %r15b
        shrq      $32, %r12
        shlq      $32, %r15
        addq      %r15, %r12
        addq      %r11, %r12
        movq      72(%rsp), %r11
        movq      %r11, %rbp
        imulq     %rax, %rbp
        movq      80(%rsp), %r15
        addq      %r12, %r10
        imulq     %r15, %rax
        shrq      $32, %rbp
        cmpq      %r12, %r10
        movq      112(%rsp), %rcx
        movl      $0, %r12d
        setb      %r12b
        addq      %rax, %rbp
        movq      %r11, %rax
        imulq     %rsi, %rax
        imulq     %rdi, %rcx
        imulq     88(%rsp), %rdi
        imulq     %r15, %rsi
        addq      %rbp, %rax
        addq      %r12, %r13
        movq      %rax, %r11
        cmpq      %r12, %r13
        movl      $0, %r12d
        setb      %r12b
        cmpq      %rbp, %rax
        movl      $0, %eax
        setb      %al
        addq      %rdi, %r10
        cmpq      %rdi, %r10
        movl      $0, %edi
        setb      %dil
        shrq      $32, %r11
        shlq      $32, %rax
        addq      %rax, %r11
        addq      %rdi, %r13
        addq      %rsi, %r11
        xorl      %eax, %eax
        cmpq      %rdi, %r13
        setb      %al
        addq      %r11, %r13
        cmpq      %r11, %r13
        movl      $0, %r11d
        setb      %r11b
        addl      %r12d, %eax
        addl      %r11d, %eax
        je        ..B3.61
..B3.60:
        incq      %rdx
        lea       1(%rbx), %rax
        cmove     %rax, %rbx
..B3.61:
        movq      %rdx, %rdi
        movq      %rbx, %rax
        shrq      $55, %rdi
        shlq      $9, %rax
        orq       %rax, %rdi
        movq      %rdi, %rsi
        sarq      $63, %rsi
        cmpq      %rdi, %rsi
        jne       ..B3.70
        jmp       ..B3.62
..B3.63:
        call      __stack_chk_fail@PLT
..B3.64:
        cmpq      $0, 32(%r12)
        jne       ..B3.66
..B3.65:
        movl      $0, 24(%r12)
        jmp       ..B3.52
..B3.66:
        incq      %rbx
        testq     $2, %rbx
        je        ..B3.52
..B3.67:
        movslq    24(%r12), %rdx
        btcq      $31, %rdx
        movl      %edx, 24(%r12)
        jmp       ..B3.52
..B3.68:
        shlq      $9, %rcx
        orq       $462, %rcx
        jmp       ..B3.48
..B3.69:
        movq      8(%r8), %rcx
        jmp       ..B3.20
..B3.70:
        movq      16(%rsp), %r15
        movq      40(%rsp), %r12
..B3.71:
        lea       (,%rbx,4), %rcx
        sarq      $2, %rcx
        movq      %rcx, %rax
        subq      %rcx, %rbx
        sarq      $63, %rax
        movq      %rcx, %rsi
        cmpq      %rax, %rcx
        jne       ..B3.73
..B3.72:
        movq      %rdx, %rcx
        movq      %r13, %rdx
        movq      %r10, %r13
        addq      $64, %r14
..B3.73:
        movl      $-2147483648, %eax
        testq     %rsi, %rsi
        movl      $0, %esi
        cmovge    %esi, %eax
        testl     %eax, %eax
        je        ..B3.75
..B3.74:
        notq      %r13
        movl      $1, %edi
        incq      %r13
        notq      %rdx
        cmove     %edi, %esi
        xorl      %r9d, %r9d
        notq      %rcx
        addq      %rsi, %rdx
        cmove     %edi, %r9d
        addq      %r9, %rcx
..B3.75:
        movq      %rbx, %r9
        lea       (%rsp), %rdi
        movl      56(%rdi), %r10d
        negq      %r9
        testl     %r10d, %r10d
        movl      $3, 4(%rdi)
        movq      %rcx, 8(%rdi)
        cmovne    %r9, %rbx
        xorl      %esi, %esi
        xorl      %r10d, %eax
        movl      %eax, (%rdi)
        movq      %rdx, 16(%rdi)
..___tag_value___dpml_ux_sincos.138:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_sincos.139:
..B3.76:
        movslq    4(%rsp), %rax
        movq      %rax, %rcx
        addq      $-3, %rcx
        je        ..B3.78
..B3.77:
        shrq      %cl, %r13
        orq       %r13, 16(%rsp)
..B3.78:
        lea       1008+__trig_x_table(%rip), %rsi
        lea       (%rsp), %rdi
        movq      %rdi, %rdx
        subq      %r14, %rax
        movl      %eax, 4(%rdi)
..___tag_value___dpml_ux_sincos.140:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_sincos.141:
..B3.79:
        shrq      $62, %rbx
        jmp       ..B3.46
..B3.81:
        movl      %ecx, %eax
        sarl      $31, %eax
        movslq    %eax, %rax
        addq      %rbp, %rax
        movq      %rax, %rcx
        andq      $1, %rcx
        addq      %rcx, %rax
        movq      %rax, %rbx
        sarq      $1, %rbx
        subq      %rax, %rbp
        je        ..B3.83
..B3.82:
        shrq      $63, %rbp
        movq      %r13, %rdi
        lea       1008+__trig_x_table(%rip), %rsi
        movq      %rbp, %rdx
        lea       (%rsp), %rcx
..___tag_value___dpml_ux_sincos.142:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_sincos.143:
        jmp       ..B3.46
..B3.83:
        movl      56(%rsp), %eax
        movq      88(%rsp), %rdx
        movq      112(%rsp), %rcx
        movl      %eax, (%rsp)
        movl      %r11d, 4(%rsp)
        movq      %rdx, 8(%rsp)
        movq      %rcx, 16(%rsp)
        jmp       ..B3.46
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_sincos,@function
	.size	__dpml_ux_sincos,.-__dpml_ux_sincos
	.data
# -- End  __dpml_ux_sincos
	.section .rodata, "a"
	.align 16
	.align 16
__trig_x_table:
	.long	15442952
	.long	1678786816
	.long	15709192
	.long	1414612240
	.long	15975432
	.long	1141915904
	.long	16241672
	.long	873463808
	.long	16507912
	.long	609305872
	.long	16774152
	.long	336592896
	.long	2
	.long	0
	.long	103
	.long	0
	.long	22
	.long	0
	.long	104
	.long	0
	.long	107
	.long	0
	.long	23
	.long	0
	.long	105
	.long	0
	.long	15176712
	.long	336609536
	.long	115
	.long	0
	.long	15176712
	.long	336592896
	.long	118
	.long	0
	.long	15176712
	.long	519700480
	.long	28
	.long	0
	.long	29
	.long	0
	.long	30
	.long	0
	.long	15176712
	.long	528281504
	.long	34
	.long	0
	.long	32
	.long	0
	.long	33
	.long	0
	.long	35
	.long	0
	.long	36
	.long	0
	.long	0
	.long	4294967291
	.long	2498349230
	.long	2398762258
	.long	2491794649
	.long	247854702
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	95443718
	.long	1527099483
	.long	1431655766
	.long	357913941
	.long	2657305954
	.long	3
	.long	0
	.long	0
	.long	3705764381
	.long	2553
	.long	0
	.long	0
	.long	138031221
	.long	1532340
	.long	0
	.long	0
	.long	2983678984
	.long	775364077
	.long	0
	.long	0
	.long	20739419
	.long	3530365445
	.long	75
	.long	0
	.long	674366505
	.long	499172021
	.long	25931
	.long	0
	.long	1191312077
	.long	2630803580
	.long	7053263
	.long	0
	.long	2382099329
	.long	2712937970
	.long	1481185358
	.long	0
	.long	2412211635
	.long	2311528398
	.long	3431649258
	.long	53
	.long	3364759590
	.long	865053377
	.long	3819263687
	.long	5917
	.long	1745256079
	.long	2154301056
	.long	109078534
	.long	426088
	.long	286331142
	.long	286331153
	.long	286331153
	.long	17895697
	.long	1431655765
	.long	1431655765
	.long	1431655765
	.long	357913941
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	2851833826
	.long	97
	.long	0
	.long	0
	.long	1768459388
	.long	63846
	.long	0
	.long	0
	.long	2009193996
	.long	35243820
	.long	0
	.long	0
	.long	1368406537
	.long	3397743743
	.long	3
	.long	0
	.long	3946395699
	.long	2652434014
	.long	1440
	.long	0
	.long	2553743692
	.long	4190957063
	.long	440828
	.long	0
	.long	595011843
	.long	807348040
	.long	105798954
	.long	0
	.long	883371940
	.long	908455249
	.long	2075540478
	.long	4
	.long	761938407
	.long	3951975904
	.long	3388403475
	.long	591
	.long	217841633
	.long	3490513104
	.long	13634816
	.long	53261
	.long	3626846480
	.long	2195205506
	.long	763549741
	.long	2982616
	.long	1431655403
	.long	1431655765
	.long	1431655765
	.long	89478485
	.long	4294967292
	.long	4294967295
	.long	4294967295
	.long	1073741823
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2877741414
	.long	48456580
	.long	4555740
	.long	0
	.long	3312728015
	.long	4268104823
	.long	3744444631
	.long	1
	.long	6483522
	.long	3858350617
	.long	1187701358
	.long	879
	.long	3780777519
	.long	2626127639
	.long	2801410624
	.long	158172
	.long	3864078256
	.long	3316933375
	.long	1275824684
	.long	11667904
	.long	1744149096
	.long	2725495384
	.long	3617949641
	.long	318163395
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	3422290646
	.long	426546810
	.long	43450
	.long	0
	.long	3479121894
	.long	3493673009
	.long	236644653
	.long	0
	.long	4247003031
	.long	1084451344
	.long	1285072157
	.long	46
	.long	1927283714
	.long	2972340862
	.long	2243521359
	.long	13184
	.long	739625925
	.long	4125699428
	.long	1446479475
	.long	1522115
	.long	2250255868
	.long	2030226330
	.long	3149913922
	.long	70000510
	.long	312493331
	.long	1293839619
	.long	2186293876
	.long	1033991278
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	0
	.long	560513588
	.long	3373259426
	.long	2161908945
	.long	3301335691
	.long	0
	.long	0
	.type	__trig_x_table,@object
	.size	__trig_x_table,1040
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
