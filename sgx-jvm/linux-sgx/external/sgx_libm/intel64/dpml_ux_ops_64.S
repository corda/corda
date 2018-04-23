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
	.file "dpml_ux_ops_64.c"
	.text
..TXTST0:
# -- Begin  __dpml_extended_multiply__
	.text
       .align    16,0x90
	.globl __dpml_extended_multiply__
__dpml_extended_multiply__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B1.1:
	.cfi_startproc
..___tag_value___dpml_extended_multiply__.1:
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
        movq      %rdi, %r15
        movq      16(%rsi), %r14
        movq      %r14, %r10
        movl      %r14d, %r9d
        movq      %rcx, %r8
        movq      %r9, %r13
        movq      16(%r15), %rax
        movq      %rax, %rbp
        movl      %eax, %ebx
        movq      %rax, %rdi
        shrq      $32, %rbp
        imulq     %rbx, %r13
        imulq     %r14, %rdi
        movq      %r9, -8(%rsp)
        imulq     %rbp, %r9
        shrq      $32, %r13
        shrq      $32, %r10
        addq      %r9, %r13
        movq      %rbx, %r9
        imulq     %r10, %r9
        addq      %r13, %r9
        movq      %r9, %rcx
        cmpq      %r13, %r9
        movq      %rbp, %r9
        movl      $0, %r13d
        movq      8(%r15), %r12
        setb      %r13b
        imulq     %r10, %r9
        imulq     %r12, %r14
        movq      8(%rsi), %r11
        imulq     %r11, %rax
        shrq      $32, %rcx
        shlq      $32, %r13
        addq      %r13, %rcx
        addq      %r9, %rcx
        movl      (%r15), %r9d
        addq      %r14, %rcx
        movq      %rdi, 16(%r8)
        xorl      (%rsi), %r9d
        cmpq      %r14, %rcx
        movl      4(%rsi), %esi
        movq      %r12, %r14
        movl      4(%r15), %edi
        movl      %r9d, (%rdx)
        movl      %r9d, (%r8)
        movl      $0, %r9d
        setb      %r9b
        addq      %rax, %rcx
        movq      %rcx, 8(%r8)
        cmpq      %rax, %rcx
        movl      %r11d, %ecx
        lea       (%rdi,%rsi), %r13d
        movl      %r13d, 4(%rdx)
        lea       -128(%rdi,%rsi), %r15d
        movl      %r15d, 4(%r8)
        movq      %rcx, %r15
        movq      %rcx, %r13
        movl      $0, %eax
        setb      %al
        imulq     %rbx, %r15
        imulq     %r11, %r14
        imulq     %rbp, %r13
        shrq      $32, %r11
        imulq     %r11, %rbx
        imulq     %r11, %rbp
        shrq      $32, %r15
        addq      %r13, %r15
        addq      %r15, %rbx
        cmpq      %r15, %rbx
        movq      %rbx, %r8
        movl      $0, %ebx
        setb      %bl
        shrq      $32, %r8
        shlq      $32, %rbx
        addq      %rbx, %r8
        addq      %rbp, %r8
        movq      -8(%rsp), %rbp
        movq      %rbp, %rdi
        movl      %r12d, %r15d
        shrq      $32, %r12
        movq      %r15, %rsi
        imulq     %r15, %rdi
        imulq     %r12, %rbp
        imulq     %r10, %rsi
        imulq     %r12, %r10
        addq      %rax, %r9
        xorl      %eax, %eax
        shrq      $32, %rdi
        addq      %r14, %r9
        addq      %rbp, %rdi
        cmpq      %r14, %r9
        movl      $0, %r14d
        setb      %r14b
        addq      %rdi, %rsi
        cmpq      %rdi, %rsi
        movq      %rsi, %r13
        setb      %al
        addq      %r8, %r9
        shrq      $32, %r13
        shlq      $32, %rax
        addq      %rax, %r13
        movq      %rcx, %rax
        imulq     %r15, %rax
        imulq     %r12, %rcx
        imulq     %r11, %r15
        imulq     %r11, %r12
        shrq      $32, %rax
        addq      %r10, %r13
        addq      %rcx, %rax
        xorl      %r10d, %r10d
        cmpq      %r8, %r9
        setb      %r10b
        xorl      %r11d, %r11d
        addq      %rax, %r15
        cmpq      %rax, %r15
        movq      %r15, %rcx
        movl      $0, %r15d
        setb      %r15b
        addq      %r13, %r9
        cmpq      %r13, %r9
        setb      %r11b
        shrq      $32, %rcx
        addq      %r10, %r14
        shlq      $32, %r15
        addq      %r15, %rcx
        addq      %r12, %rcx
        addq      %r11, %r14
        addq      %r14, %rcx
        movq      %r9, 16(%rdx)
        movq      %rcx, 8(%rdx)
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
	.type	__dpml_extended_multiply__,@function
	.size	__dpml_extended_multiply__,.-__dpml_extended_multiply__
	.data
# -- End  __dpml_extended_multiply__
	.text
# -- Begin  __dpml_evaluate_rational__
	.text
       .align    16,0x90
	.globl __dpml_evaluate_rational__
__dpml_evaluate_rational__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
..B2.1:
	.cfi_startproc
..___tag_value___dpml_evaluate_rational__.28:
..L29:

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
        subq      $88, %rsp
	.cfi_def_cfa_offset 144
        movq      %rdi, %r13
        movq      %rcx, %rbx
        movq      %r8, %r14
        movq      %rbx, %r9
        movq      %rsi, %r15
        movq      %rdx, 32(%rsp)
        movq      %rbx, %rbp
        sarq      $58, %r9
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 72(%rsp)
        addl      %r9d, 4(%r13)
        testq     $68, %rbx
        jne       ..B2.3
..B2.2:
        movl      $136, %eax
        xorl      %ebp, %ebp
        cmpl      $0, (%r13)
        movq      %r13, %r12
        cmovne    %rax, %rbp
        xorq      %rbx, %rbp
        jmp       ..B2.4
..B2.3:
        movq      %r13, %rdi
        lea       48(%rsp), %r12
        movq      %r13, %rsi
        movq      %r12, %rdx
..___tag_value___dpml_evaluate_rational__.43:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_evaluate_rational__.44:
..B2.4:
        movq      %r12, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_evaluate_rational__.45:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_evaluate_rational__.46:
..B2.5:
        movq      32(%rsp), %r9
        movq      %r9, %r8
        negq      %r8
        movslq    4(%r12), %rax
        imulq     %rax, %r8
        shlq      $4, %r9
        movq      %r8, 24(%rsp)
        testq     $512, %rbx
        movq      %r9, 40(%rsp)
        je        ..B2.7
..B2.6:
        testq     $1024, %rbx
        je        ..B2.8
..B2.7:
        xorl      %eax, %eax
        jmp       ..B2.9
..B2.8:
        movl      $1, %eax
..B2.9:
        testq     $15, %rbx
        lea       (%rax,%rax,2), %rax
        lea       (,%rax,8), %r9
        lea       (%r14,%rax,8), %r8
        je        ..B2.16
..B2.10:
        testq     $8, %rbp
        lea       __eval_neg_poly(%rip), %r11
        lea       __eval_pos_poly(%rip), %r10
        movq      %rbx, %rax
        cmovne    %r11, %r10
        negq      %r9
        andq      $240, %rax
        cmove     %r14, %r8
        addq      %r14, %r9
        addq      $24, %r9
        cmpq      %r11, %r10
        jne       ..B2.12
..B2.11:
        movq      %r12, %rdi
        movq      %r15, %rdx
        movq      24(%rsp), %rsi
        movq      32(%rsp), %rcx
        movq      %r8, (%rsp)
        movq      %rax, 8(%rsp)
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.47:
        call      __eval_neg_poly
..___tag_value___dpml_evaluate_rational__.48:
        jmp       ..B2.36
..B2.12:
        movq      %r12, %rdi
        movq      %r15, %rdx
        movq      24(%rsp), %rsi
        movq      32(%rsp), %rcx
        movq      %r8, (%rsp)
        movq      %rax, 8(%rsp)
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.49:
        call      __eval_pos_poly
..___tag_value___dpml_evaluate_rational__.50:
..B2.36:
        movq      16(%rsp), %r9
        movq      8(%rsp), %rax
        movq      (%rsp), %r8
..B2.13:
        testq     $2, %rbx
        je        ..B2.15
..B2.14:
        movq      %r13, %rdi
        movq      %r8, %rsi
        movq      %r8, %rdx
        movq      %r8, (%rsp)
        movq      %rax, 8(%rsp)
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.51:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_evaluate_rational__.52:
..B2.35:
        movq      16(%rsp), %r9
        movq      8(%rsp), %rax
        movq      (%rsp), %r8
..B2.15:
        movq      40(%rsp), %r10
        lea       24(%r15,%r10), %r15
        movl      -8(%r15), %r11d
        addl      %r11d, 4(%r8)
        jmp       ..B2.17
..B2.16:
        movq      40(%rsp), %r10
        orq       $256, %rbx
        testq     $1024, %rbx
        movq      %rbx, %rax
        movq      %r14, %r9
        lea       24(%r15,%r10), %r11
        cmovne    %r11, %r15
        andq      $240, %rax
..B2.17:
        testq     %rax, %rax
        je        ..B2.26
..B2.18:
        testq     $128, %rbp
        lea       __eval_neg_poly(%rip), %rbp
        lea       __eval_pos_poly(%rip), %rax
        cmovne    %rbp, %rax
        cmpq      %rbp, %rax
        jne       ..B2.20
..B2.19:
        movq      %r12, %rdi
        movq      %r15, %rdx
        movq      %r9, %r8
        movq      24(%rsp), %rsi
        movq      32(%rsp), %rcx
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.53:
        call      __eval_neg_poly
..___tag_value___dpml_evaluate_rational__.54:
        jmp       ..B2.39
..B2.20:
        movq      %r12, %rdi
        movq      %r15, %rdx
        movq      %r9, %r8
        movq      24(%rsp), %rsi
        movq      32(%rsp), %rcx
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.55:
        call      __eval_pos_poly
..___tag_value___dpml_evaluate_rational__.56:
..B2.39:
        movq      16(%rsp), %r9
..B2.21:
        testq     $32, %rbx
        je        ..B2.23
..B2.22:
        movq      %r13, %rdi
        movq      %r9, %rsi
        movq      %r9, %rdx
        movq      %r9, 16(%rsp)
..___tag_value___dpml_evaluate_rational__.57:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_evaluate_rational__.58:
..B2.38:
        movq      16(%rsp), %r9
..B2.23:
        movq      40(%rsp), %rax
        movl      16(%rax,%r15), %ebp
        addl      %ebp, 4(%r9)
        testq     $1024, %rbx
        je        ..B2.27
..B2.24:
        movq      72(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.31
..B2.25:
        addq      $88, %rsp
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
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.26:
        orq       $256, %rbx
..B2.27:
        testq     $256, %rbx
        jne       ..B2.29
..B2.28:
        movq      %r14, %rdi
        lea       24(%r14), %rsi
        movl      $2, %edx
        movq      %r14, %rcx
..___tag_value___dpml_evaluate_rational__.79:
        call      __dpml_divide__@PLT
..___tag_value___dpml_evaluate_rational__.80:
..B2.29:
        movq      72(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.31
..B2.30:
        addq      $88, %rsp
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
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.31:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_evaluate_rational__,@function
	.size	__dpml_evaluate_rational__,.-__dpml_evaluate_rational__
	.data
# -- End  __dpml_evaluate_rational__
	.text
# -- Begin  __eval_neg_poly
	.text
       .align    16,0x90
__eval_neg_poly:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
..B3.1:
	.cfi_startproc
..___tag_value___eval_neg_poly.102:
..L103:

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
        movq      %rsi, %r12
        xorl      %r10d, %r10d
        cmpq      $128, %r12
        movq      %rcx, %rax
        movl      $1, %ecx
        movslq    4(%rdi), %r11
        movq      %rdx, %r13
        movq      8(%rdi), %r14
        movq      16(%rdi), %rdx
        movq      %r11, %rdi
        cmovl     %ecx, %r10d
        xorl      %ebx, %ebx
        negq      %rdi
        xorl      %r9d, %r9d
        testl     %r10d, %r10d
        jne       ..B3.5
..B3.3:
        addq      %r11, %r12
        addq      $16, %r13
        decq      %rax
        cmpq      $128, %r12
        jge       ..B3.3
..B3.5:
        xorl      %ebp, %ebp
        cmpq      $64, %r12
        cmovge    %ebp, %ecx
        testl     %ecx, %ecx
        jne       ..B3.12
..B3.7:
        movl      %r12d, %ecx
        decq      %rax
        addq      %r11, %r12
        movq      8(%r13), %r9
        addq      $16, %r13
        shrq      %cl, %r9
        cmpq      $64, %r12
        jl        ..B3.12
..B3.8:
        testq     %r9, %r9
        je        ..B3.7
..B3.9:
        movq      %r14, %rbp
        xorl      %r15d, %r15d
        movl      %r14d, %esi
        shrq      $32, %rbp
        movq      %r8, -72(%rsp)
..B3.10:
        movl      %r9d, %r10d
        movq      %rsi, %r8
        imulq     %r10, %r8
        imulq     %rbp, %r10
        shrq      $32, %r9
        movq      %rsi, %rcx
        imulq     %r9, %rcx
        imulq     %rbp, %r9
        shrq      $32, %r8
        decq      %rax
        addq      %r10, %r8
        addq      %r8, %rcx
        cmpq      %r8, %rcx
        movl      %r15d, %r8d
        movq      %rcx, %r10
        movl      %r12d, %ecx
        setb      %r8b
        addq      %r11, %r12
        shrq      $32, %r10
        shlq      $32, %r8
        addq      %r8, %r10
        addq      %r9, %r10
        movq      8(%r13), %r9
        addq      $16, %r13
        shrq      %cl, %r9
        subq      %r10, %r9
        cmpq      $64, %r12
        jge       ..B3.10
..B3.11:
        movq      -72(%rsp), %r8
..B3.12:
        movq      %r12, %r10
        negq      %r10
        testq     %r12, %r12
        je        ..B3.19
..B3.13:
        movq      %r14, %rsi
        movl      %r14d, %ebp
        shrq      $32, %rsi
        movq      %r14, -16(%rsp)
        xorl      %r14d, %r14d
        movq      %r8, -72(%rsp)
..B3.14:
        movl      %r9d, %ebx
        movq      %rbp, %rcx
        imulq     %rbx, %rcx
        imulq     %rsi, %rbx
        shrq      $32, %r9
        movq      %rbp, %r15
        imulq     %r9, %r15
        imulq     %rsi, %r9
        shrq      $32, %rcx
        decq      %rax
        addq      %rbx, %rcx
        movl      %r14d, %ebx
        addq      %rcx, %r15
        cmpq      %rcx, %r15
        movq      %r15, %r8
        movl      %r12d, %ecx
        setb      %bl
        shrq      $32, %r8
        shlq      $32, %rbx
        addq      %rbx, %r8
        movq      8(%r13), %rbx
        addq      %r9, %r8
        movq      (%r13), %r15
        movq      %rbx, %r9
        shrq      %cl, %r15
        movl      %r10d, %ecx
        shlq      %cl, %r9
        movl      %r12d, %ecx
        orq       %r9, %r15
        addq      %rdi, %r10
        movq      %r15, %r9
        addq      $16, %r13
        subq      %r8, %r9
        movl      %r14d, %r8d
        cmpq      %r15, %r9
        seta      %r8b
        shrq      %cl, %rbx
        subq      %r8, %rbx
        addq      %r11, %r12
        je        ..B3.24
..B3.15:
        testq     %rbx, %rbx
        je        ..B3.14
..B3.16:
        movq      %rdx, %rcx
        movl      %edx, %r15d
        shrq      $32, %rcx
        movq      %rdx, -8(%rsp)
        movq      %rcx, -48(%rsp)
        movq      %r15, -40(%rsp)
        movq      %rsi, -32(%rsp)
        movq      %rdi, -56(%rsp)
        movq      %r11, -64(%rsp)
..B3.17:
        movl      %r12d, %ecx
        decq      %rax
        movq      8(%r13), %r14
        movq      %r14, %r11
        movq      (%r13), %rdx
        addq      $16, %r13
        shrq      %cl, %rdx
        movl      %r10d, %ecx
        shlq      %cl, %r11
        movl      %r12d, %ecx
        orq       %r11, %rdx
        movq      -40(%rsp), %r11
        movq      %r11, %r8
        movl      %ebx, %esi
        movq      -48(%rsp), %r15
        shrq      %cl, %r14
        movq      %r15, %rcx
        imulq     %rsi, %r8
        imulq     %rsi, %rcx
        shrq      $32, %r8
        movq      -16(%rsp), %rdi
        addq      %rcx, %r8
        imulq     %rbx, %rdi
        shrq      $32, %rbx
        movq      %r11, %rcx
        imulq     %rbx, %rcx
        imulq     %rbx, %r15
        addq      %r8, %rcx
        negq      %rdi
        cmpq      %r8, %rcx
        movl      $0, %r8d
        movq      %rcx, %r11
        movl      %r8d, %ecx
        setb      %cl
        addq      %rdx, %rdi
        cmpq      %rdx, %rdi
        movq      %rax, -24(%rsp)
        movl      %r8d, %edx
        movl      %r9d, %eax
        seta      %dl
        shrq      $32, %r11
        shlq      $32, %rcx
        addq      %rcx, %r11
        addq      %r15, %r11
        movq      %rbp, %r15
        imulq     %rax, %r15
        movq      -32(%rsp), %rcx
        negq      %r11
        imulq     %rcx, %rax
        shrq      $32, %r15
        addq      %rdi, %r11
        shrq      $32, %r9
        addq      %rax, %r15
        movq      %rbp, %rax
        imulq     %r9, %rax
        imulq     %rcx, %r9
        addq      %r15, %rax
        cmpq      %r15, %rax
        movl      %r8d, %r15d
        setb      %r15b
        subq      %rdx, %r14
        movq      %rax, %rdx
        shrq      $32, %rdx
        shlq      $32, %r15
        addq      %r15, %rdx
        xorl      %r15d, %r15d
        addq      %r9, %rdx
        xorl      %r9d, %r9d
        cmpq      %rdi, %r11
        movq      %rbp, %rdi
        seta      %r9b
        imulq     %rsi, %rdi
        imulq     %rcx, %rsi
        shrq      $32, %rdi
        addq      %rsi, %rdi
        movq      %rbp, %rsi
        imulq     %rbx, %rsi
        imulq     %rcx, %rbx
        addq      %rdi, %rsi
        subq      %r9, %r14
        movq      %r11, %r9
        cmpq      %rdi, %rsi
        movq      %rsi, %rax
        setb      %r15b
        subq      %rdx, %r9
        cmpq      %r11, %r9
        seta      %r8b
        shrq      $32, %rax
        shlq      $32, %r15
        addq      %r15, %rax
        subq      %r8, %r14
        addq      %rbx, %rax
        movq      %r14, %rbx
        addq      -56(%rsp), %r10
        subq      %rax, %rbx
        movq      -24(%rsp), %rax
        addq      -64(%rsp), %r12
        jne       ..B3.17
..B3.18:
        movq      -8(%rsp), %rdx
        movq      -16(%rsp), %r14
        movq      -72(%rsp), %r8
..B3.19:
        testq     %rax, %rax
        js        ..B3.23
..B3.20:
        movl      %edx, %r12d
        movq      %r14, %r10
        shrq      $32, %rdx
        movl      %r14d, %r11d
        shrq      $32, %r10
        movq      %rdx, -8(%rsp)
        movq      %r14, -16(%rsp)
        movq      %r8, -72(%rsp)
..B3.21:
        movl      %ebx, %ecx
        movq      %r12, %r8
        movq      -8(%rsp), %r15
        movq      %r15, %rdi
        imulq     %rcx, %r8
        imulq     %rcx, %rdi
        movq      -16(%rsp), %rdx
        movq      %r12, %rbp
        imulq     %rbx, %rdx
        shrq      $32, %rbx
        negq      %rdx
        imulq     %rbx, %rbp
        imulq     %rbx, %r15
        shrq      $32, %r8
        addq      %rdi, %r8
        addq      %r8, %rbp
        movq      %rbp, %rdi
        cmpq      %r8, %rbp
        movl      $0, %ebp
        movl      %ebp, %r8d
        setb      %r8b
        movq      (%r13), %r14
        addq      %r14, %rdx
        shrq      $32, %rdi
        shlq      $32, %r8
        addq      %r8, %rdi
        xorl      %r8d, %r8d
        cmpq      %r14, %rdx
        movq      %r11, %r14
        seta      %r8b
        addq      %r15, %rdi
        movl      %r9d, %r15d
        negq      %rdi
        addq      %rdx, %rdi
        imulq     %r15, %r14
        imulq     %r10, %r15
        movq      8(%r13), %rsi
        addq      $16, %r13
        shrq      $32, %r9
        subq      %r8, %rsi
        movq      %r11, %r8
        imulq     %r9, %r8
        imulq     %r10, %r9
        shrq      $32, %r14
        addq      %r15, %r14
        addq      %r14, %r8
        cmpq      %r14, %r8
        movl      %ebp, %r14d
        movq      %r8, %r15
        setb      %r14b
        shrq      $32, %r15
        shlq      $32, %r14
        addq      %r14, %r15
        xorl      %r14d, %r14d
        addq      %r9, %r15
        xorl      %r9d, %r9d
        cmpq      %rdx, %rdi
        movq      %r11, %rdx
        seta      %r9b
        imulq     %rcx, %rdx
        imulq     %r10, %rcx
        shrq      $32, %rdx
        addq      %rcx, %rdx
        movq      %r11, %rcx
        imulq     %rbx, %rcx
        imulq     %r10, %rbx
        addq      %rdx, %rcx
        subq      %r9, %rsi
        movq      %rdi, %r9
        cmpq      %rdx, %rcx
        movq      %rcx, %r8
        setb      %r14b
        subq      %r15, %r9
        cmpq      %rdi, %r9
        seta      %bpl
        shrq      $32, %r8
        shlq      $32, %r14
        addq      %r14, %r8
        subq      %rbp, %rsi
        addq      %rbx, %r8
        movq      %rsi, %rbx
        subq      %r8, %rbx
        decq      %rax
        jns       ..B3.21
..B3.22:
        movq      -72(%rsp), %r8
..B3.23:
        xorl      %eax, %eax
        movq      %rbx, 8(%r8)
        movq      %r9, 16(%r8)
        movl      %eax, 4(%r8)
        movl      %eax, (%r8)
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
	.cfi_def_cfa_offset 56
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.24:
        movq      -16(%rsp), %r14
        movq      -72(%rsp), %r8
        jmp       ..B3.19
        .align    16,0x90
	.cfi_endproc
	.type	__eval_neg_poly,@function
	.size	__eval_neg_poly,.-__eval_neg_poly
	.data
# -- End  __eval_neg_poly
	.text
# -- Begin  __dpml_divide__
	.text
       .align    16,0x90
	.globl __dpml_divide__
__dpml_divide__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B4.1:
	.cfi_startproc
..___tag_value___dpml_divide__.136:
..L137:

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
        subq      $56, %rsp
	.cfi_def_cfa_offset 112
        movq      %rsi, %rbp
        testq     %rbp, %rbp
        lea       __ux_one__(%rip), %r8
        movq      %rdi, %rbx
        movq      %rcx, %r15
        jne       ..L151
        movq      %r8, %rbp
..L151:
        testq     %rbx, %rbx
        movq      %rdx, %r12
        movq      %fs:40, %rax
        jne       ..L152
        movq      %r8, %rbx
..L152:
        xorq      %rsp, %rax
        movq      %rax, 48(%rsp)
        cmpq      %r8, %rbp
        movq      8(%rbp), %r13
        movq      16(%rbp), %r14
        je        ..B4.12
..B4.2:
        testq     %r13, %r13
        jl        ..B4.5
..B4.3:
        movq      %rbp, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_divide__.153:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_divide__.154:
..B4.4:
        movq      8(%rbp), %r13
        movq      16(%rbp), %r14
..B4.5:
        lea       16(%rsp), %rdi
..___tag_value___dpml_divide__.155:
        call      fegetenv@PLT
..___tag_value___dpml_divide__.156:
..B4.6:
        movq      %r13, %rcx
        pxor      %xmm0, %xmm0
        shrq      $1, %rcx
        movq      $0x3fffffffff, %rax
        cvtsi2sdq %rcx, %xmm0
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        movq      %r13, %r9
        divsd     %xmm0, %xmm3
        andq      %rax, %r9
        movq      %r14, %r8
        shlq      $15, %r9
        movq      $0xffffffc000000000, %rdx
        shrq      $49, %r8
        movq      %r13, %rsi
        movl      (%rbx), %r10d
        orq       %r8, %r9
        movl      4(%rbx), %r8d
        andq      %rdx, %rsi
        xorl      (%rbp), %r10d
        pxor      %xmm1, %xmm1
        subl      4(%rbp), %r8d
        pxor      %xmm2, %xmm2
        cvttsd2si %xmm3, %rbp
        cvtsi2sdq %r9, %xmm2
        shrq      $1, %rsi
        addq      $-1280, %rbp
        cvtsi2sdq %rsi, %xmm1
        movq      8(%rbx), %rsi
        pxor      %xmm7, %xmm7
        movq      16(%rbx), %rcx
        movq      $0xfffffff000000000, %rbx
        andq      %rbx, %rbp
        andq      %rsi, %rax
        cvtsi2sdq %rbp, %xmm7
        movsd     .L_2il0floatpacket.1(%rip), %xmm5
        movq      %rcx, %r11
        mulsd     %xmm5, %xmm2
        pxor      %xmm6, %xmm6
        mulsd     %xmm7, %xmm1
        mulsd     %xmm7, %xmm2
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        movq      %rsi, %rdi
        shlq      $15, %rax
        andq      %rsi, %rdx
        shrq      $49, %r11
        pxor      %xmm8, %xmm8
        orq       %r11, %rax
        pxor      %xmm9, %xmm9
        cvtsi2sdq %rax, %xmm6
        subsd     %xmm1, %xmm0
        mulsd     %xmm6, %xmm5
        subsd     %xmm2, %xmm0
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        mulsd     %xmm3, %xmm4
        shrq      $11, %rdi
        shlq      $10, %rdi
        shrq      $1, %rdx
        cvtsi2sdq %rdi, %xmm8
        cvtsi2sdq %rdx, %xmm9
        mulsd     %xmm4, %xmm0
        movsd     .L_2il0floatpacket.3(%rip), %xmm10
        xorl      %edi, %edi
        mulsd     %xmm7, %xmm10
        mulsd     %xmm0, %xmm8
        mulsd     %xmm10, %xmm9
        mulsd     %xmm5, %xmm10
        mulsd     .L_2il0floatpacket.4(%rip), %xmm9
        addsd     %xmm8, %xmm10
        cvttsd2si %xmm9, %rdx
        cvttsd2si %xmm10, %rbx
        movq      %rdx, %rax
        lea       (%rbx,%rdx,4), %rdx
        cmpq      %rbx, %rdx
        movl      %r10d, (%r15)
        setb      %dil
        xorl      %ebx, %ebx
        shrq      $62, %rax
        addq      %rdi, %rax
        cmpq      $1, %r12
        je        ..B4.8
..B4.7:
        movsd     .L_2il0floatpacket.5(%rip), %xmm1
        movq      %r14, %r9
        mulsd     %xmm0, %xmm1
        xorl      %r11d, %r11d
        movq      %r15, (%rsp)
        movq      %rax, %r12
        cvttsd2si %xmm1, %r15
        movl      %edx, %ebx
        lea       (%r15,%rbp,4), %r15
        testq     %r15, %r15
        movq      %rbx, %rdi
        movl      %r8d, 8(%rsp)
        movq      $-1, %r8
        movl      %r14d, %ebp
        cmove     %r8, %r15
        shrq      $32, %r9
        movq      %rdx, %r8
        imulq     %rbp, %rdi
        movq      %rbx, %r10
        negq      %r12
        shrq      $32, %r8
        andq      %r12, %r14
        imulq     %r9, %r10
        imulq     %r8, %rbp
        imulq     %r8, %r9
        shrq      $32, %rdi
        andq      %r13, %r12
        addq      %r10, %rdi
        addq      %rdi, %rbp
        cmpq      %rdi, %rbp
        movq      %rbp, %r10
        movl      %r13d, %ebp
        movq      %rbx, %rdi
        setb      %r11b
        shrq      $32, %r10
        shlq      $32, %r11
        addq      %r11, %r10
        movq      %r13, %r11
        addq      %r9, %r10
        movq      %r13, %r9
        shrq      $32, %r9
        addq      %r10, %r14
        imulq     %rbp, %rdi
        imulq     %r9, %rbx
        imulq     %r8, %rbp
        imulq     %rdx, %r11
        imulq     %r8, %r9
        shrq      $32, %rdi
        xorl      %r8d, %r8d
        addq      %rbx, %rdi
        addq      %rdi, %rbp
        cmpq      %rdi, %rbp
        movq      %rbp, %rbx
        movl      $0, %ebp
        setb      %bpl
        cmpq      %r10, %r14
        setb      %r8b
        xorl      %r10d, %r10d
        addq      %r11, %r14
        shrq      $32, %rbx
        shlq      $32, %rbp
        addq      %rbp, %rbx
        xorl      %ebp, %ebp
        addq      %r9, %rbx
        xorl      %r9d, %r9d
        cmpq      %r11, %r14
        setb      %r9b
        xorl      %r11d, %r11d
        addq      %rbx, %r12
        cmpq      %rbx, %r12
        setb      %r11b
        xorl      %ebx, %ebx
        addq      %r9, %r8
        addq      %r8, %r12
        xorl      %r9d, %r9d
        cmpq      %r8, %r12
        setb      %bl
        cmpq      %r14, %rcx
        setb      %r10b
        addq      %rbx, %r11
        xorl      %ebx, %ebx
        cmpq      %r12, %rsi
        setb      %bl
        negq      %r12
        addq      %rsi, %r12
        negq      %r11
        cmpq      %r10, %r12
        setb      %bpl
        subq      %rbx, %r11
        movl      $1, %ebx
        cmpq      %r12, %r10
        movl      $0, %r12d
        cmovne    %ebx, %r12d
        subq      %rbp, %r11
        orq       %r11, %r12
        cmove     %r13, %r11
        negq      %r14
        addq      %rcx, %r14
        xorq      %r11, %r13
        subq      %r13, %r14
        movl      %r15d, %r13d
        movl      %r14d, %edi
        movq      %r13, %r8
        shrq      $32, %r14
        imulq     %rdi, %r8
        imulq     %r14, %r13
        shrq      $32, %r15
        imulq     %r15, %rdi
        imulq     %r15, %r14
        shrq      $32, %r8
        addq      %r13, %r8
        addq      %r8, %rdi
        cmpq      %r8, %rdi
        movq      %rdi, %r13
        movl      8(%rsp), %r8d
        setb      %r9b
        orq       %rcx, %rsi
        movl      $0, %ecx
        cmove     %ecx, %ebx
        xorl      %esi, %esi
        shrq      $32, %r13
        shlq      $32, %r9
        addq      %r9, %r13
        addq      %r14, %r13
        movq      %r13, %r14
        shrq      $63, %r14
        addq      %r14, %r12
        addq      %r12, %rdx
        cmpq      %r12, %rdx
        setb      %sil
        sarq      $63, %r12
        addq      %r12, %rax
        movq      (%rsp), %r15
        lea       (%rbx,%r13,2), %rbx
        addq      %rsi, %rax
..B4.8:
        movq      %rax, %rsi
        movl      %eax, %ecx
        movq      %rdx, %rbp
        andq      %rax, %rdx
        shrq      %cl, %rbp
        movl      %eax, %ecx
        shlq      $63, %rsi
        lea       16(%rsp), %rdi
        shlq      $63, %rdx
        orq       %rbp, %rsi
        shrq      %cl, %rbx
        movslq    %r8d, %r8
        orq       %rbx, %rdx
        addq      %rax, %r8
        movq      %rsi, 8(%r15)
        movq      %rdx, 16(%r15)
        movl      %r8d, 4(%r15)
..___tag_value___dpml_divide__.157:
        call      fesetenv@PLT
..___tag_value___dpml_divide__.158:
..B4.9:
        movq      48(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B4.11
..B4.10:
        addq      $56, %rsp
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
	.cfi_def_cfa_offset 112
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.11:
        call      __stack_chk_fail@PLT
..B4.12:
        movq      8(%rbx), %rcx
        movq      %rcx, 8(%r15)
        movl      (%rbx), %eax
        movl      4(%rbx), %edx
        movq      16(%rbx), %rbx
        movl      %eax, (%r15)
        movl      %edx, 4(%r15)
        movq      %rbx, 16(%r15)
        movq      48(%rsp), %rbp
        xorq      %rsp, %rbp
        cmpq      %fs:40, %rbp
        jne       ..B4.11
        jmp       ..B4.10
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_divide__,@function
	.size	__dpml_divide__,.-__dpml_divide__
	.data
# -- End  __dpml_divide__
	.text
# -- Begin  __dpml_multiply__
	.text
       .align    16,0x90
	.globl __dpml_multiply__
__dpml_multiply__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B5.1:
	.cfi_startproc
..___tag_value___dpml_multiply__.180:
..L181:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        movq      %rdi, %r15
        movq      8(%rsi), %r9
        movq      %rdx, %r11
        movl      %r9d, %ecx
        movq      %rcx, %r13
        movq      %rcx, %rax
        movq      16(%r15), %rdi
        movl      %edi, %r14d
        movq      8(%r15), %r8
        movq      %r8, %r10
        shrq      $32, %rdi
        imulq     %r14, %r13
        imulq     %r9, %r10
        imulq     %rdi, %rax
        shrq      $32, %r9
        imulq     %r9, %r14
        imulq     %r9, %rdi
        shrq      $32, %r13
        addq      %rax, %r13
        addq      %r13, %r14
        cmpq      %r13, %r14
        movq      %r14, %rax
        movl      $0, %r14d
        setb      %r14b
        shrq      $32, %rax
        shlq      $32, %r14
        addq      %r14, %rax
        addq      %rdi, %rax
        movl      (%r15), %edi
        addq      %rax, %r10
        movq      16(%rsi), %rdx
        xorl      (%rsi), %edi
        movl      4(%r15), %r15d
        movl      %edx, %r13d
        movl      %edi, (%r11)
        movq      %r13, %rdi
        addl      4(%rsi), %r15d
        movl      %r8d, %esi
        shrq      $32, %r8
        movq      %rsi, %r14
        imulq     %rsi, %rdi
        imulq     %r8, %r13
        shrq      $32, %rdx
        imulq     %rdx, %r14
        imulq     %r8, %rdx
        shrq      $32, %rdi
        addq      %r13, %rdi
        xorl      %r13d, %r13d
        addq      %rdi, %r14
        cmpq      %rdi, %r14
        movl      %r15d, 4(%r11)
        movq      %r14, %r15
        setb      %r13b
        xorl      %r14d, %r14d
        cmpq      %rax, %r10
        setb      %r14b
        shrq      $32, %r15
        shlq      $32, %r13
        addq      %r13, %r15
        addq      %rdx, %r15
        movq      %rcx, %rdx
        imulq     %rsi, %rdx
        imulq     %r8, %rcx
        imulq     %r9, %rsi
        imulq     %r9, %r8
        shrq      $32, %rdx
        addq      %r15, %r10
        addq      %rcx, %rdx
        xorl      %ecx, %ecx
        addq      %rdx, %rsi
        cmpq      %rdx, %rsi
        movq      %rsi, %r13
        setb      %cl
        shrq      $32, %r13
        shlq      $32, %rcx
        addq      %rcx, %r13
        addq      %r8, %r13
        xorl      %r8d, %r8d
        cmpq      %r15, %r10
        setb      %r8b
        addq      %r14, %r8
        addq      %r8, %r13
        movq      %r10, 16(%r11)
        movq      %r13, 8(%r11)
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
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_multiply__,@function
	.size	__dpml_multiply__,.-__dpml_multiply__
	.data
# -- End  __dpml_multiply__
	.text
# -- Begin  __eval_pos_poly
	.text
       .align    16,0x90
__eval_pos_poly:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
..B6.1:
	.cfi_startproc
..___tag_value___eval_pos_poly.195:
..L196:

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
        movq      %rsi, %r11
        movq      16(%rdi), %r9
        cmpq      $128, %r11
        movq      %r9, -32(%rsp)
        movl      $0, %r9d
        movq      %rcx, %rax
        movl      $1, %ecx
        movslq    4(%rdi), %rbp
        cmovl     %ecx, %r9d
        xorl      %ebx, %ebx
        xorl      %esi, %esi
        movq      %rbp, -64(%rsp)
        negq      %rbp
        movq      8(%rdi), %r10
        testl     %r9d, %r9d
        jne       ..B6.5
..B6.2:
        movq      -64(%rsp), %rdi
..B6.3:
        addq      %rdi, %r11
        addq      $16, %rdx
        decq      %rax
        cmpq      $128, %r11
        jge       ..B6.3
..B6.5:
        xorl      %edi, %edi
        cmpq      $64, %r11
        cmovge    %edi, %ecx
        testl     %ecx, %ecx
        jne       ..B6.12
..B6.6:
        movq      -64(%rsp), %rdi
..B6.7:
        movl      %r11d, %ecx
        decq      %rax
        addq      %rdi, %r11
        movq      8(%rdx), %rsi
        addq      $16, %rdx
        shrq      %cl, %rsi
        cmpq      $64, %r11
        jl        ..B6.12
..B6.8:
        testq     %rsi, %rsi
        je        ..B6.7
..B6.9:
        movq      %r10, %r9
        xorl      %r12d, %r12d
        movl      %r10d, %ebx
        shrq      $32, %r9
        movq      -64(%rsp), %r13
..B6.10:
        movl      %esi, %edi
        movq      %rbx, %r15
        imulq     %rdi, %r15
        imulq     %r9, %rdi
        shrq      $32, %rsi
        movq      %rbx, %r14
        imulq     %rsi, %r14
        imulq     %r9, %rsi
        shrq      $32, %r15
        movl      %r12d, %ecx
        addq      %rdi, %r15
        decq      %rax
        addq      %r15, %r14
        cmpq      %r15, %r14
        movq      %r14, %rdi
        setb      %cl
        shrq      $32, %rdi
        shlq      $32, %rcx
        addq      %rcx, %rdi
        movl      %r11d, %ecx
        addq      %rsi, %rdi
        addq      %r13, %r11
        movq      8(%rdx), %rsi
        addq      $16, %rdx
        shrq      %cl, %rsi
        addq      %rdi, %rsi
        cmpq      $64, %r11
        jge       ..B6.10
..B6.11:
        xorl      %ebx, %ebx
        cmpq      %rdi, %rsi
        setb      %bl
..B6.12:
        movq      %r11, %r9
        xorl      %edi, %edi
        negq      %r9
        testq     %r11, %r11
        je        ..B6.38
..B6.13:
        testq     %rbx, %rbx
        jne       ..B6.18
..B6.14:
        movq      %r10, %rbx
        xorl      %r13d, %r13d
        movq      %r8, -56(%rsp)
        movl      %r10d, %r15d
        shrq      $32, %rbx
        movq      %r10, -40(%rsp)
        movq      -64(%rsp), %r8
..B6.15:
        movl      %esi, %r12d
        movq      %r15, %r14
        imulq     %r12, %r14
        imulq     %rbx, %r12
        shrq      $32, %rsi
        movq      %r15, %rcx
        imulq     %rsi, %rcx
        imulq     %rbx, %rsi
        shrq      $32, %r14
        decq      %rax
        addq      %r12, %r14
        movl      %r13d, %r12d
        addq      %r14, %rcx
        cmpq      %r14, %rcx
        movq      %rcx, %r10
        movl      %r11d, %ecx
        setb      %r12b
        shrq      $32, %r10
        shlq      $32, %r12
        addq      %r12, %r10
        movq      8(%rdx), %r12
        addq      %rsi, %r10
        movq      (%rdx), %rsi
        movq      %r12, %r14
        shrq      %cl, %rsi
        movl      %r9d, %ecx
        shlq      %cl, %r14
        movl      %r11d, %ecx
        orq       %r14, %rsi
        addq      %rbp, %r9
        addq      %r10, %rsi
        addq      $16, %rdx
        cmpq      %r10, %rsi
        movl      %r13d, %r10d
        setb      %r10b
        shrq      %cl, %r12
        addq      %r10, %r12
        addq      %r8, %r11
        je        ..B6.32
..B6.16:
        testq     %r12, %r12
        je        ..B6.15
..B6.17:
        movq      -40(%rsp), %r10
        movq      %r12, %rbx
        movq      -56(%rsp), %r8
..B6.18:
        testq     %rax, %rax
        jge       ..B6.33
..B6.19:
        movq      %rbx, 8(%r8)
        movq      %rsi, 16(%r8)
        movl      %edi, 4(%r8)
        movl      $0, (%r8)
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
	.cfi_def_cfa_offset 56
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B6.38:
        movl      %edi, -48(%rsp)
        movq      %r10, -40(%rsp)
        movq      %r8, -56(%rsp)
..B6.21:
        testq     %rax, %rax
        jl        ..B6.30
..B6.22:
        movl      %ebx, %edi
        decq      %rax
        movq      -32(%rsp), %rcx
        movq      %rdi, %r15
        movl      %ecx, %r13d
        movq      %rdi, %r11
        movq      -40(%rsp), %rbp
        movq      %rbp, %r9
        shrq      $32, %rcx
        imulq     %r13, %r15
        imulq     %rbx, %r9
        imulq     %rcx, %r11
        shrq      $32, %rbx
        movq      %r13, -24(%rsp)
        imulq     %rbx, %r13
        shrq      $32, %r15
        addq      %r11, %r15
        xorl      %r11d, %r11d
        addq      %r15, %r13
        xorl      %r14d, %r14d
        movq      %r13, %r8
        cmpq      %r15, %r13
        movq      %rcx, %r13
        setb      %r14b
        imulq     %rbx, %r13
        shrq      $32, %r8
        shlq      $32, %r14
        movq      (%rdx), %r12
        addq      %r14, %r8
        movq      %rax, -16(%rsp)
        addq      %r9, %r12
        movl      %esi, %eax
        addq      %r13, %r8
        xorl      %r13d, %r13d
        cmpq      %r9, %r12
        movq      %rax, %r14
        movl      %ebp, %r9d
        setb      %r13b
        addq      %r8, %r12
        shrq      $32, %rbp
        imulq     %r9, %r14
        imulq     %rbp, %rax
        shrq      $32, %r14
        shrq      $32, %rsi
        addq      %rax, %r14
        movq      %r9, %rax
        imulq     %rsi, %rax
        imulq     %rbp, %rsi
        addq      %r14, %rax
        cmpq      %r14, %rax
        movl      %r11d, %r14d
        movq      %rax, %r15
        setb      %r14b
        shrq      $32, %r15
        shlq      $32, %r14
        addq      %r14, %r15
        xorl      %r14d, %r14d
        addq      %rsi, %r15
        xorl      %esi, %esi
        cmpq      %r8, %r12
        movq      %rdi, %r8
        setb      %sil
        imulq     %r9, %r8
        imulq     %rbp, %rdi
        shrq      $32, %r8
        addq      %rdi, %r8
        movq      %r9, %rdi
        imulq     %rbx, %rdi
        imulq     %rbp, %rbx
        addq      %r8, %rdi
        cmpq      %r8, %rdi
        movq      %rdi, %rax
        setb      %r14b
        xorl      %edi, %edi
        shrq      $32, %rax
        addq      %rsi, %r13
        shlq      $32, %r14
        lea       (%r12,%r15), %rsi
        addq      %r14, %rax
        addq      %rbx, %rax
        xorl      %ebx, %ebx
        cmpq      %r15, %rsi
        setb      %bl
        addq      %r13, %rbx
        movq      8(%rdx), %r10
        addq      $16, %rdx
        addq      %rbx, %r10
        cmpq      %rbx, %r10
        setb      %dil
        lea       (%r10,%rax), %rbx
        cmpq      %rax, %rbx
        movq      -16(%rsp), %rax
        setb      %r11b
        addl      %r11d, %edi
        je        ..B6.21
..B6.23:
        movq      %rbx, %rdi
        movl      $1, %r11d
        shrq      $1, %rsi
        shlq      $63, %rdi
        shrq      $1, %rbx
        orq       %rdi, %rsi
        incl      -48(%rsp)
        btsq      $63, %rbx
        testq     %rax, %rax
        js        ..B6.30
..B6.39:
        movq      %rcx, -72(%rsp)
..B6.26:
        movl      %r11d, %ecx
        decq      %rax
        movq      (%rdx), %r15
        shrq      %cl, %r15
        movq      %r11, %rcx
        negq      %rcx
        movq      8(%rdx), %r12
        movq      %r12, %r13
        movq      -72(%rsp), %r10
        movq      %r10, %r8
        shlq      %cl, %r13
        addq      $16, %rdx
        movl      %r11d, %ecx
        orq       %r13, %r15
        shrq      %cl, %r12
        movq      -24(%rsp), %rcx
        movq      %rcx, %r14
        movl      %ebx, %r13d
        imulq     %r13, %r14
        imulq     %r13, %r8
        movq      -40(%rsp), %rdi
        imulq     %rbx, %rdi
        shrq      $32, %rbx
        addq      %rdi, %r15
        imulq     %rbx, %rcx
        imulq     %rbx, %r10
        shrq      $32, %r14
        addq      %r8, %r14
        addq      %r14, %rcx
        cmpq      %r14, %rcx
        movl      $0, %r14d
        movq      %rcx, %r8
        movl      %r14d, %ecx
        setb      %cl
        shrq      $32, %r8
        shlq      $32, %rcx
        addq      %rcx, %r8
        movq      %rax, -16(%rsp)
        addq      %r10, %r8
        movl      %esi, %eax
        xorl      %r10d, %r10d
        cmpq      %rdi, %r15
        movq      %r9, %rdi
        setb      %r10b
        addq      %r8, %r15
        imulq     %rax, %rdi
        imulq     %rbp, %rax
        shrq      $32, %rdi
        shrq      $32, %rsi
        addq      %rax, %rdi
        movq      %r9, %rax
        imulq     %rsi, %rax
        imulq     %rbp, %rsi
        addq      %rdi, %rax
        cmpq      %rdi, %rax
        movl      %r14d, %edi
        movq      %rax, %rcx
        setb      %dil
        shrq      $32, %rcx
        shlq      $32, %rdi
        addq      %rdi, %rcx
        movq      %r9, %rdi
        imulq     %r13, %rdi
        imulq     %rbp, %r13
        shrq      $32, %rdi
        addq      %rsi, %rcx
        addq      %r13, %rdi
        movq      %r9, %r13
        imulq     %rbx, %r13
        imulq     %rbp, %rbx
        xorl      %esi, %esi
        cmpq      %r8, %r15
        setb      %sil
        xorl      %r8d, %r8d
        addq      %rdi, %r13
        cmpq      %rdi, %r13
        movq      %r13, %rax
        setb      %r8b
        shrq      $32, %rax
        addq      %rsi, %r10
        shlq      $32, %r8
        lea       (%r15,%rcx), %rsi
        addq      %r8, %rax
        xorl      %r15d, %r15d
        addq      %rbx, %rax
        xorl      %ebx, %ebx
        cmpq      %rcx, %rsi
        setb      %bl
        addq      %r10, %rbx
        addq      %rbx, %r12
        cmpq      %rbx, %r12
        setb      %r15b
        addq      -64(%rsp), %r11
        lea       (%r12,%rax), %rbx
        cmpq      %rax, %rbx
        movq      -16(%rsp), %rax
        setb      %r14b
        addl      %r14d, %r15d
        je        ..B6.28
..B6.27:
        movq      %rbx, %rcx
        incq      %r11
        shrq      $1, %rsi
        shlq      $63, %rcx
        shrq      $1, %rbx
        orq       %rcx, %rsi
        incl      -48(%rsp)
        btsq      $63, %rbx
..B6.28:
        testq     %r11, %r11
        je        ..B6.21
..B6.29:
        testq     %rax, %rax
        jns       ..B6.26
..B6.30:
        movl      -48(%rsp), %edi
        movq      -56(%rsp), %r8
        jmp       ..B6.19
..B6.32:
        movq      -40(%rsp), %r10
        movq      %r12, %rbx
        movq      -56(%rsp), %r8
        jmp       ..B6.38
..B6.33:
        movq      -32(%rsp), %rcx
        movq      %r10, %rbp
        movl      %ecx, %r15d
        movl      %r10d, %r9d
        shrq      $32, %rbp
        shrq      $32, %rcx
        movq      %r15, -24(%rsp)
        movl      %edi, -48(%rsp)
        movq      %r10, -40(%rsp)
        movq      %r8, -56(%rsp)
        jmp       ..B6.39
        .align    16,0x90
	.cfi_endproc
	.type	__eval_pos_poly,@function
	.size	__eval_pos_poly,.-__eval_pos_poly
	.data
# -- End  __eval_pos_poly
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x47b00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3ef00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x34700000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x3c300000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0x3fd00000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x00000000,0x43d00000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
__ux_one__:
	.long	0
	.long	1
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.type	__ux_one__,@object
	.size	__ux_one__,24
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
