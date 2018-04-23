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
	.file "powl.c"
	.text
..TXTST0:
# -- Begin  powl
	.text
       .align    16,0x90
	.globl powl
powl:
# parameter 1: 336 + %rsp
# parameter 2: 352 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_powl.1:
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
        subq      $280, %rsp
	.cfi_def_cfa_offset 336
        xorb      %r10b, %r10b
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 264(%rsp)
..B1.2:
        fnstcw    250(%rsp)
..B1.3:
        movzbl    345(%rsp), %ecx
        xorl      %r15d, %r15d
        shrl      $7, %ecx
        movzwl    344(%rsp), %r14d
        shll      $15, %ecx
        andl      $32767, %r14d
        movl      340(%rsp), %edx
        orl       %r14d, %ecx
        movl      %edx, %eax
        movzbl    361(%rsp), %r8d
        shrl      $7, %r8d
        shll      $16, %ecx
        shrl      $16, %eax
        movzwl    360(%rsp), %r12d
        orl       %eax, %ecx
        shll      $15, %r8d
        andl      $32767, %r12d
        movl      356(%rsp), %eax
        orl       %r12d, %r8d
        movl      %eax, %ebx
        movl      %ecx, %r9d
        shll      $16, %r8d
        andl      $2147483647, %r9d
        shrl      $16, %ebx
        cmpl      $1073709056, %r9d
        movl      %ecx, %r13d
        setb      %r15b
        orl       %ebx, %r8d
        movl      %r8d, %ebp
        andl      $2147483647, %r8d
        shrl      $31, %ebp
        movl      %ebp, 128(%rsp)
        xorl      %ebp, %r15d
        movl      %r8d, %ebp
        orl       %eax, %ebp
        shrl      $31, %r13d
        movl      352(%rsp), %r11d
        orl       %r11d, %ebp
        movl      336(%rsp), %ebx
        movl      %r15d, 256(%rsp)
        je        ..B1.5
..B1.4:
        addl      $-1073709056, %ecx
        lea       -2147483648(%rdx), %r15d
        orl       %r15d, %ecx
        movl      %r15d, 24(%rsp)
        orl       %ebx, %ecx
        jne       ..B1.11
..B1.5:
        cmpl      $2147450880, %r8d
        jae       ..B1.8
..B1.6:
        cmpl      $2147450880, %r9d
        jb        ..B1.9
..B1.7:
        addl      $-2147483648, %edx
        orl       %ebx, %edx
        je        ..B1.9
..B1.8:
        fldt      336(%rsp)
        fldt      352(%rsp)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
..B1.9:
        lea       _ones(%rip), %rax
        movq      264(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        movsd     (%rax), %xmm0
        jne       ..B1.155
..B1.10:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.11:
        xorl      %ecx, %ecx
        lea       -1073709056(%r8), %r15d
        movq      %rcx, 16(%rsp)
        cmpl      $4194304, %r15d
        movq      %rcx, 240(%rsp)
        movl      %r15d, 8(%rsp)
        jae       ..B1.16
..B1.12:
        movl      %r8d, %edi
        shrl      $16, %edi
        cmpl      $1075806208, %r8d
        ja        ..B1.14
..B1.13:
        movl      %edi, %ecx
        movl      %eax, %ebp
        negl      %ecx
        addl      $30, %ecx
        shrl      %cl, %ebp
        movl      %ebp, (%rsp)
        lea       2(%rdi), %ecx
        movl      %eax, %ebp
        shll      %cl, %ebp
        orl       %r11d, %ebp
        cmpl      $1075740672, %r8d
        cmova     %r11d, %ebp
        jmp       ..B1.15
..B1.14:
        movl      %edi, %ecx
        movl      %r11d, %ebp
        negl      %ecx
        xorl      %r15d, %r15d
        addl      $30, %ecx
        shrl      %cl, %ebp
        movl      %ebp, (%rsp)
        lea       2(%rdi), %ecx
        movl      %r11d, %ebp
        shll      %cl, %ebp
        cmpl      $1077837824, %r8d
        cmovae    %r15d, %ebp
..B1.15:
        xorl      %ecx, %ecx
        andl      $1, (%rsp)
        movl      %r13d, %r15d
        cmove     %rcx, %r15
        movl      (%rsp), %ecx
        movq      %r15, 240(%rsp)
        movq      %rcx, 16(%rsp)
        jmp       ..B1.17
..B1.16:
        xorl      %ecx, %ecx
        cmpl      $1073709056, %r8d
        cmovae    %ecx, %ebp
..B1.17:
        cmpl      $32767, %r14d
        je        ..B1.233
..B1.18:
        cmpl      $32767, %r12d
        je        ..B1.189
..B1.19:
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        orl       %ebx, %ecx
        jne       ..B1.26
..B1.20:
        movl      %edx, %ecx
        orl       %r9d, %ecx
        jne       ..B1.26
..B1.21:
        movl      $1, %edx
        xorl      %ecx, %ecx
        testl     %ebp, %ebp
        cmove     %edx, %ecx
        movq      240(%rsp), %rax
        andl      %ecx, %eax
        cmpl      $0, 128(%rsp)
        jne       ..B1.157
..B1.22:
        cmpl      $65536, %r8d
        jae       ..B1.24
..B1.23:
        lea       _smallest_value_64(%rip), %rdx
        movq      (%rdx), %rcx
        movq      %rcx, 216(%rsp)
..B1.24:
        movl      %eax, %eax
        lea       _zeros(%rip), %rdx
        movq      264(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        movsd     (%rdx,%rax,8), %xmm0
        jne       ..B1.155
..B1.25:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.26:
        testl     %r13d, %r13d
        je        ..B1.32
..B1.27:
        cmpl      $1077837824, %r8d
        jae       ..B1.29
..B1.28:
        testl     %ebp, %ebp
        jne       ..B1.160
..B1.29:
        lea       -1073709056(%r9), %ecx
        orl       24(%rsp), %ecx
        orl       %ebx, %ecx
        jne       ..B1.32
..B1.30:
        lea       _ones(%rip), %rax
        movq      16(%rsp), %rdx
        movq      264(%rsp), %rcx
        xorq      %rsp, %rcx
        movsd     (%rax,%rdx,8), %xmm0
        cmpq      %fs:40, %rcx
        jne       ..B1.155
..B1.31:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.32:
        lea       -65536(%r9), %r12d
        lea       -65536(%r8), %ecx
        orl       %ecx, %r12d
        testl     $-2147483648, %r12d
        je        ..B1.34
..B1.33:
        lea       _smallest_value_64(%rip), %rcx
        movq      (%rcx), %r12
        movq      %r12, 216(%rsp)
..B1.34:
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        orl       %r11d, %ecx
        jne       ..B1.67
..B1.35:
        cmpl      $1073709056, %r8d
        jne       ..B1.47
..B1.36:
        cmpl      $0, 128(%rsp)
        je        ..B1.45
..B1.37:
        movzwl    250(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.41
..B1.38:
        orl       $-64768, %edx
        movw      %dx, 248(%rsp)
..B1.39:
        fldcw     248(%rsp)
..B1.40:
        movb      $1, %r10b
..B1.41:
        fldt      336(%rsp)
        lea       _ones(%rip), %rax
        testb     %r10b, %r10b
        movsd     (%rax), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fdivp     %st, %st(1)
        fstpt     144(%rsp)
        je        ..B1.43
..B1.42:
        fldcw     250(%rsp)
..B1.43:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.44:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.45:
        fldt      336(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.46:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.47:
        cmpl      $1073643520, %r8d
        jne       ..B1.67
..B1.48:
        movzwl    250(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.52
..B1.49:
        orl       $-64768, %edx
        movw      %dx, 248(%rsp)
..B1.50:
        fldcw     248(%rsp)
..B1.51:
        movb      $1, %r10b
..B1.52:
        cmpl      $0, 128(%rsp)
        je        ..B1.60
..B1.53:
        fldt      336(%rsp)
        lea       _ones(%rip), %rcx
        movzbl    345(%rsp), %ebp
        shrl      $7, %ebp
        movzwl    344(%rsp), %eax
        shll      $15, %ebp
        andl      $32767, %eax
        orl       %eax, %ebp
        shll      $16, %ebp
        movzwl    342(%rsp), %edx
        orl       %edx, %ebp
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstp      %st(0)
        fldl      (%rcx)
        fstpt     (%rsp)
        fldt      (%rsp)
        lea       -536838144(%rbp), %ebx
        cmpl      $1073741824, %ebx
        jbe       ..B1.57
..B1.54:
        cmpl      $536838144, %ebp
        jae       ..B1.56
..B1.55:
        fldt      32(%rsp)
        lea       _invsqrtl_scales(%rip), %rax
        lea       16+_invsqrtl_scales(%rip), %rdx
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstp      %st(0)
        fldt      (%rdx)
        fstpt     (%rsp)
        jmp       ..B1.57
..B1.56:
        fldt      32(%rsp)
        lea       32+_invsqrtl_scales(%rip), %rax
        lea       48+_invsqrtl_scales(%rip), %rdx
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstp      %st(0)
        fldt      (%rdx)
        fstpt     (%rsp)
..B1.57:
        fldt      32(%rsp)
        lea       _TWO_43H(%rip), %rax
        fld       %st(0)
        fxch      %st(1)
        fdivr     %st, %st(2)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldl      (%rax)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
        fstpt     32(%rsp)
..B1.58:
        fldt      64(%rsp)
        fsqrt     
        fstpt     96(%rsp)
..B1.59:
        fldt      96(%rsp)
        fld       %st(0)
        fldt      16(%rsp)
        fld       %st(0)
        fldt      32(%rsp)
        fmul      %st(4), %st
        fldl      (%rax)
        lea       8+_ones(%rip), %rax
        fmul      %st(5), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fsubr     %st(5), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fld       %st(1)
        fxch      %st(3)
        fmul      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(3), %st
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      (%rsp)
        fxch      %st(2)
        faddl     (%rax)
        faddp     %st, %st(4)
        fxch      %st(5)
        fmulp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
        jmp       ..B1.63
..B1.60:
        fldt      336(%rsp)
        fstpt     48(%rsp)
..B1.61:
        fldt      48(%rsp)
        fsqrt     
        fstpt     80(%rsp)
..B1.62:
        fldt      80(%rsp)
        fstpt     144(%rsp)
..B1.63:
        testb     %r10b, %r10b
        je        ..B1.65
..B1.64:
        fldcw     250(%rsp)
..B1.65:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.66:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.67:
        movzwl    250(%rsp), %r11d
        movl      %r11d, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.71
..B1.68:
        orl       $-64768, %r11d
        movw      %r11w, 248(%rsp)
..B1.69:
        fldcw     248(%rsp)
..B1.70:
        movb      $1, %r10b
..B1.71:
        fldt      336(%rsp)
        lea       _ones(%rip), %r11
        testl     %ebp, %ebp
        fmull     (%r11,%r13,8)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        jne       ..B1.100
..B1.72:
        cmpl      $1048576, 8(%rsp)
        jae       ..B1.100
..B1.73:
        movl      %r8d, %ecx
        lea       _exact_masks(%rip), %r12
        shrl      $16, %ecx
        negl      %ecx
        addl      $30, %ecx
        shrl      %cl, %eax
        cmpl      $46, %eax
        lea       -2(%rax), %edi
        jb        ..L136
        movl      $46, %edi
..L136:
        movslq    %edi, %rdi
        movl      4(%r12,%rdi,8), %ebp
        andl      %ebp, %edx
        movl      (%r12,%rdi,8), %r13d
        andl      %r13d, %ebx
        orl       %ebx, %edx
        jne       ..B1.100
..B1.74:
        movq      240(%rsp), %rdx
        cmpl      $65535, %r9d
        fldl      (%r11,%rdx,8)
        ja        ..B1.76
..B1.75:
        lea       _TWO_75(%rip), %rdx
        fldl      (%rdx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        movzwl    184(%rsp), %ebp
        movl      %ebp, %ebx
        andl      $32767, %ebx
        addl      $-16458, %ebx
        jmp       ..B1.77
..B1.76:
        movzwl    344(%rsp), %ebx
        fxch      %st(1)
        fstpt     176(%rsp)
        andl      $32767, %ebx
        movzwl    184(%rsp), %ebp
        addl      $-16383, %ebx
..B1.77:
        lea       iones(%rip), %rdx
        andl      $-32768, %ebp
        movl      128(%rsp), %ecx
        orl       $-49153, %ebp
        movw      %bp, 184(%rsp)
        movl      (%rdx,%rcx,4), %edx
        imull     %eax, %edx
        imull     %ebx, %edx
        lea       16445(%rdx), %ebx
        cmpl      $32829, %ebx
        lea       16383(%rdx), %ecx
        jb        ..B1.85
..B1.78:
        shlq      $4, 240(%rsp)
        cmpl      $-63, %ecx
        jg        ..B1.80
..B1.79:
        lea       _small_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        jmp       ..B1.81
..B1.80:
        lea       _large_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
..B1.81:
        fstpt     144(%rsp)
        testb     %r10b, %r10b
        je        ..B1.245
..B1.82:
        fstpt     32(%rsp)
..B1.237:
        fldcw     250(%rsp)
        jmp       ..B1.83
..B1.245:
        fstp      %st(0)
..B1.83:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.84:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.85:
        fstpt     32(%rsp)
        testl     %ecx, %ecx
        jg        ..B1.87
..B1.86:
        addl      $16458, %edx
        movzwl    40(%rsp), %ecx
        andl      $32767, %edx
        andl      $-32768, %ecx
        orl       %edx, %ecx
        lea       8+_TWO_75(%rip), %rdx
        movw      %cx, 40(%rsp)
        fldt      32(%rsp)
        fmull     (%rdx)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        jmp       ..B1.88
..B1.87:
        movzwl    40(%rsp), %edx
        andl      $32767, %ecx
        andl      $-32768, %edx
        orl       %ecx, %edx
        movw      %dx, 40(%rsp)
        fldt      32(%rsp)
..B1.88:
        cmpl      $-2147483648, 180(%rsp)
        jne       ..B1.95
..B1.89:
        cmpl      $0, 176(%rsp)
        jne       ..B1.95
..B1.90:
        fstpt     144(%rsp)
..B1.91:
        testb     %r10b, %r10b
        je        ..B1.93
..B1.92:
        fldcw     250(%rsp)
..B1.93:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.94:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.95:
        fldt      176(%rsp)
        lea       _zeros(%rip), %rdx
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldl      (%r11)
        movl      $0, 112(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(3)
        fldl      (%rdx)
        jmp       ..B1.96
..B1.99:
        fld       %st(1)
        fadd      %st(5), %st
        fld       %st(2)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fld       %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        movl      $0, 112(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
..B1.96:
        testb     $1, %al
        je        ..B1.98
..B1.97:
        fld       %st(1)
        fadd      %st(5), %st
        fmulp     %st, %st(1)
        fld       %st(4)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movl      $0, 16(%rsp)
        fldt      16(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
..B1.98:
        shrl      $1, %eax
        testl     %eax, %eax
        je        ..B1.169
        jmp       ..B1.99
..B1.100:
        lea       -1073676224(%r9), %eax
        cmpl      $32864, %eax
        jae       ..B1.102
..B1.101:
        lea       8+_ones(%rip), %rdx
        lea       _TWO_32H(%rip), %rcx
        movb      $1, %al
        fldl      (%rdx)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fldl      (%rcx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fsub      %st(2), %st
        fxch      %st(2)
        jmp       ..B1.106
..B1.102:
        xorb      %al, %al
        cmpl      $65535, %r9d
        ja        ..B1.104
..B1.103:
        lea       _TWO_75(%rip), %rdx
        fldl      (%rdx)
        fmulp     %st, %st(1)
        fstpt     176(%rsp)
        movzwl    184(%rsp), %edx
        movl      %edx, %edi
        andl      $32767, %edi
        addl      $-16458, %edi
        jmp       ..B1.105
..B1.104:
        movzwl    344(%rsp), %edi
        fstpt     176(%rsp)
        andl      $32767, %edi
        movzwl    184(%rsp), %edx
        addl      $-16383, %edi
..B1.105:
        andl      $-32768, %edx
        lea       8+_ones(%rip), %rbx
        orl       $-49153, %edx
        movw      %dx, 184(%rsp)
        lea       _TWO_32H(%rip), %rdx
        fldt      176(%rsp)
        fld       %st(0)
        fld       %st(1)
        movl      180(%rsp), %esi
        fldl      (%rdx)
        fmul      %st, %st(2)
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rcx
        fxch      %st(1)
        fadd      %st(2), %st
        shrl      $23, %esi
        fsubp     %st, %st(2)
        movzbl    %sil, %esi
        fxch      %st(1)
        fsubr     %st, %st(2)
        flds      (%rcx,%rsi,4)
        fmul      %st, %st(1)
        fmulp     %st, %st(3)
        faddl     (%rbx)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
..B1.106:
        fldt      192(%rsp)
        lea       128+_LP(%rip), %rdx
        fld       %st(0)
        lea       112+_LP(%rip), %rcx
        lea       96+_LP(%rip), %rbx
        lea       80+_LP(%rip), %rbp
        lea       64+_LP(%rip), %r9
        lea       48+_LP(%rip), %r12
        lea       32+_LP(%rip), %r13
        lea       16+_LP(%rip), %r14
        lea       _LP(%rip), %r15
        testb     %al, %al
        fadd      %st(2), %st
        fmul      %st(4), %st
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(6)
        fstpt     16(%rsp)
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(6)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fsub      %st(5), %st
        faddp     %st, %st(3)
        fldt      (%rdx)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmul      %st(2), %st
        fldt      (%rbx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rbp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r12)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r13)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r14)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fmul      %st(4), %st
        fldt      (%r15)
        fmul      %st, %st(5)
        fld       %st(5)
        fadd      %st(2), %st
        fsubr     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(6)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        fadd      %st(4), %st
        fldt      16(%rsp)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        je        ..B1.108
..B1.107:
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fxch      %st(5)
        fxch      %st(2)
        jmp       ..B1.109
..B1.108:
        fstp      %st(3)
        movslq    %esi, %rsi
        pxor      %xmm0, %xmm0
        lea       _LT2(%rip), %rax
        movq      %rsi, %rdx
        shlq      $4, %rdx
        lea       _LLN2(%rip), %rbp
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rcx
        lea       16+_LLN2(%rip), %rbx
        cvtss2sd  (%rax,%rsi,4), %xmm0
        fldl      (%rdx,%rcx)
        fldt      (%rbx)
        fldt      (%rbp)
        addsd     8(%rdx,%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        movl      %edi, (%rsp)
        fildl     (%rsp)
        fmul      %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(4), %st
        fld       %st(0)
        fld       %st(2)
        fsub      %st(2), %st
        fadd      %st(6), %st
        faddp     %st, %st(4)
..B1.109:
        fld       %st(1)
        fadd      %st(4), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movzwl    24(%rsp), %edx
        shll      $16, %edx
        movzwl    22(%rsp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        lea       -1610514432(%rdx,%r8), %ecx
        cmpl      $538050560, %ecx
        lea       (%r8,%rdx), %eax
        jbe       ..B1.123
..B1.110:
        cmpl      $-2146402304, %eax
        jbe       ..B1.118
..B1.111:
        fstp      %st(4)
        fstp      %st(4)
        fstp      %st(2)
        fstp      %st(1)
        shlq      $4, 240(%rsp)
        cmpl      $0, 256(%rsp)
        je        ..B1.113
..B1.112:
        lea       _small_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        jmp       ..B1.114
..B1.113:
        lea       _large_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
..B1.114:
        fstpt     144(%rsp)
        testb     %r10b, %r10b
        je        ..B1.246
..B1.115:
        fldt      32(%rsp)
        fstpt     160(%rsp)
        fxch      %st(2)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
..B1.238:
        fldcw     250(%rsp)
        jmp       ..B1.116
..B1.246:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
..B1.116:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.117:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.118:
        cmpl      $1610514432, %eax
        jae       ..B1.123
..B1.119:
        fstp      %st(4)
        fstp      %st(4)
        fstp      %st(2)
        fstp      %st(1)
        movq      240(%rsp), %rcx
        lea       _small_value_80(%rip), %rdx
        movl      %ecx, %eax
        xorl      256(%rsp), %eax
        shlq      $4, %rax
        testb     %r10b, %r10b
        fldt      (%rdx,%rax)
        faddl     (%r11,%rcx,8)
        fstpt     144(%rsp)
        je        ..B1.247
..B1.120:
        fldt      32(%rsp)
        fstpt     160(%rsp)
        fxch      %st(2)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
..B1.239:
        fldcw     250(%rsp)
        jmp       ..B1.121
..B1.247:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
..B1.121:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.122:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.123:
        fstp      %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        movq      240(%rsp), %rax
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(3)
        fldt      352(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fmul      %st(3), %st
        fxch      %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldl      (%r11,%rax,8)
        movzwl    24(%rsp), %eax
        shll      $16, %eax
        movzwl    22(%rsp), %edx
        orl       %edx, %eax
        andl      $2147483647, %eax
        fstpl     208(%rsp)
        cmpl      $1072398336, %eax
        jae       ..B1.131
..B1.124:
        fstp      %st(4)
        cmpl      $1069318144, %eax
        jae       ..B1.126
..B1.125:
        fstp      %st(2)
        fldl      208(%rsp)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.127
..B1.126:
        lea       16+_EP1(%rip), %rax
        lea       _EP1(%rip), %rdx
        fldt      (%rax)
        fmul      %st(4), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fldl      208(%rsp)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
..B1.127:
        fstpt     144(%rsp)
        testb     %r10b, %r10b
        je        ..B1.248
..B1.128:
        fldt      128(%rsp)
        fstpt     160(%rsp)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
        fldl      208(%rsp)
        fstpt     32(%rsp)
..B1.240:
        fldcw     250(%rsp)
        jmp       ..B1.129
..B1.248:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
..B1.129:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.130:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.131:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _TWO_63H(%rip), %rdx
        fmul      %st(1), %st
        lea       ESC2_BIAS(%rip), %rbx
        fxch      %st(1)
        fstpt     144(%rsp)
        fldl      (%rdx)
        movl      256(%rsp), %eax
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(1), %st
        movl      160(%rsp), %edx
        movl      %edx, %esi
        movsbq    %dl, %rcx
        fsubr     %st(2), %st
        fldt      .L_2il0floatpacket.3(%rip)
        subl      %ecx, %esi
        fmulp     %st, %st(2)
        fld       %st(0)
        sarl      $8, %esi
        fsub      %st(2), %st
        movl      (%rbx,%rax,4), %ebp
        fadd      %st(6), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        lea       -1(%rbp,%rsi), %edi
        cmpl      $32766, %edi
        lea       (%rbp,%rsi), %ebx
        fsubp     %st, %st(6)
        fsub      %st(5), %st
        fsubp     %st, %st(1)
        fstpt     112(%rsp)
        fldt      144(%rsp)
        jb        ..B1.139
..B1.132:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(2)
        shlq      $4, 240(%rsp)
        cmpl      $1, %ebx
        jg        ..B1.134
..B1.133:
        lea       _small_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        jmp       ..B1.135
..B1.134:
        lea       _large_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
..B1.135:
        fstpt     144(%rsp)
        testb     %r10b, %r10b
        je        ..B1.249
..B1.136:
        fldt      128(%rsp)
        fstpt     160(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
        fldl      208(%rsp)
        fstpt     32(%rsp)
..B1.241:
        fldcw     250(%rsp)
        jmp       ..B1.137
..B1.249:
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
..B1.137:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.138:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.139:
        fldl      208(%rsp)
        andl      $32767, %ebx
        fstpt     32(%rsp)
        fld       %st(4)
        fxch      %st(2)
        fstpt     16(%rsp)
        lea       48+_EP(%rip), %rsi
        movzwl    40(%rsp), %ebp
        lea       32+_EP(%rip), %rdi
        andl      $-32768, %ebp
        lea       16+_EP(%rip), %r8
        fstpt     144(%rsp)
        orl       %ebx, %ebp
        lea       64+_EP(%rip), %rbx
        lea       _EP(%rip), %r9
        fxch      %st(1)
        fstpt     (%rsp)
        addl      $4196735, %edx
        fldt      112(%rsp)
        movslq    %ecx, %rcx
        faddp     %st, %st(2)
        shlq      $4, %rcx
        fadd      %st(1), %st
        fld       %st(0)
        cmpl      $3070, %edx
        fmul      %st(1), %st
        fldt      (%rbx)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%rdi)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        movw      %bp, 40(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        fldt      (%r9)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st(2), %st
        fldl      2048(%rcx,%r11)
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fmull     2056(%rcx,%r11)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fxch      %st(3)
        faddl     2056(%rcx,%r11)
        faddp     %st, %st(2)
        fldt      144(%rsp)
        fldt      (%rsp)
        ja        ..B1.142
..B1.140:
        lea       _Eexpbut(%rip), %rdx
        fldt      (%rdx)
        fcomip    %st(2), %st
        jbe       ..B1.142
..B1.141:
        fstp      %st(1)
        fld       %st(1)
        lea       _TWO_12H(%rip), %rdx
        shlq      $4, %rax
        fadd      %st(3), %st
        fldl      (%rdx)
        lea       _ESC2(%rip), %rcx
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%rax,%rcx)
        fldt      32(%rsp)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        jmp       ..B1.143
..B1.142:
        fstp      %st(1)
        shlq      $4, %rax
        lea       _ESC2(%rip), %rdx
        fldt      (%rax,%rdx)
        fmul      %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
..B1.143:
        movzwl    152(%rsp), %ecx
        shll      $16, %ecx
        movzwl    150(%rsp), %eax
        orl       %eax, %ecx
        andl      $2147483647, %ecx
        lea       -98304(%rcx), %edx
        cmpl      $2147319808, %edx
        jb        ..B1.151
..B1.144:
        shlq      $4, 240(%rsp)
        cmpl      $65535, %ecx
        ja        ..B1.146
..B1.145:
        lea       _small_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
        jmp       ..B1.147
..B1.146:
        lea       _large_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
..B1.147:
        testb     %r10b, %r10b
        je        ..B1.250
..B1.148:
        fldt      128(%rsp)
        fstpt     160(%rsp)
        fxch      %st(2)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
..B1.242:
        fldcw     250(%rsp)
        jmp       ..B1.149
..B1.250:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
..B1.149:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.150:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.151:
        testb     %r10b, %r10b
        je        ..B1.251
..B1.152:
        fldt      128(%rsp)
        fstpt     160(%rsp)
        fxch      %st(2)
        fstpt     112(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      192(%rsp)
        fstpt     176(%rsp)
..B1.243:
        fldcw     250(%rsp)
        jmp       ..B1.153
..B1.251:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
..B1.153:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.154:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.155:
        call      __stack_chk_fail@PLT
..B1.157:
        lea       _ones(%rip), %rdx
        lea       _zeros(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        divsd     (%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movq      264(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        je        ..B1.44
..B1.159:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.160:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movq      264(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B1.159
        jmp       ..B1.44
..B1.169:
        fstp      %st(4)
        fstp      %st(0)
        cmpl      $0, 128(%rsp)
        jne       ..B1.171
..B1.170:
        fstp      %st(0)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B1.172
..B1.171:
        fld       %st(1)
        lea       8+_ones(%rip), %rax
        fadd      %st(3), %st
        fdivr     %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fld       %st(0)
        movl      $0, (%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      (%rsp)
        fmul      %st, %st(5)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddl     (%rax)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
        fsubp     %st, %st(2)
..B1.172:
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fld       %st(4)
        fmul      %st(4), %st
        fadd      %st(1), %st
        fstpt     144(%rsp)
        movzwl    152(%rsp), %edx
        shll      $16, %edx
        movzwl    150(%rsp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        cmpl      $65535, %edx
        ja        ..B1.174
..B1.173:
        fld       %st(4)
        fdivrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fadd      %st(1), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movzwl    152(%rsp), %edx
        shll      $16, %edx
        movzwl    150(%rsp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        fxch      %st(1)
        fxch      %st(3)
        fxch      %st(2)
..B1.174:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(1)
        fstp      %st(0)
        lea       -98304(%rdx), %eax
        cmpl      $2147319808, %eax
        jb        ..B1.91
..B1.175:
        shlq      $4, 240(%rsp)
        cmpl      $65535, %edx
        ja        ..B1.177
..B1.176:
        lea       _small_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
        jmp       ..B1.178
..B1.177:
        lea       _large_value_80(%rip), %rdx
        movq      240(%rsp), %rax
        fldt      (%rax,%rdx)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
..B1.178:
        testb     %r10b, %r10b
        je        ..B1.180
..B1.179:
        fldcw     250(%rsp)
..B1.180:
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
        jmp       ..B1.44
..B1.189:
        movq      $0x8000000000000000, %rcx
        cmpq      352(%rsp), %rcx
        je        ..B1.193
..B1.190:
        fldt      336(%rsp)
        fldt      352(%rsp)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
        jmp       ..B1.44
..B1.193:
        cmpl      $32767, %r14d
        je        ..B1.223
..B1.194:
        cmpl      $32767, %r12d
        jne       ..B1.19
..B1.195:
        cmpl      $-2147483648, %eax
        jne       ..B1.19
..B1.196:
        testl     %r11d, %r11d
        jne       ..B1.19
..B1.197:
        cmpl      $1073709056, %r9d
        jae       ..B1.210
..B1.198:
        orl       %r9d, %ebx
        jne       ..B1.203
..B1.199:
        lea       _zeros(%rip), %rax
        cmpl      $0, 128(%rsp)
        movsd     (%rax), %xmm0
        jne       ..B1.219
..B1.200:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
..B1.201:
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.202:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.203:
        cmpl      $65536, %r9d
        jae       ..B1.205
..B1.204:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 216(%rsp)
..B1.205:
        cmpl      $0, 128(%rsp)
        je        ..B1.207
..B1.206:
        lea       _infs(%rip), %rax
        fldl      (%rax)
        jmp       ..B1.208
..B1.207:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
..B1.208:
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.209:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.210:
        addl      $-1073709056, %r9d
        orl       24(%rsp), %r9d
        orl       %ebx, %r9d
        je        ..B1.216
..B1.211:
        cmpl      $0, 128(%rsp)
        je        ..B1.213
..B1.212:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        jmp       ..B1.214
..B1.213:
        lea       _infs(%rip), %rax
        fldl      (%rax)
..B1.214:
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
..B1.215:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.216:
        lea       _ones(%rip), %rax
        movq      16(%rsp), %rdx
        movq      264(%rsp), %rcx
        xorq      %rsp, %rcx
        movsd     (%rax,%rdx,8), %xmm0
        cmpq      %fs:40, %rcx
        jne       ..B1.155
..B1.217:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.219:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        divsd     %xmm0, %xmm1
        movsd     %xmm1, (%rsp)
        fldl      (%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        jmp       ..B1.201
..B1.223:
        cmpl      $-2147483648, %edx
        jne       ..B1.194
..B1.224:
        testl     %ebx, %ebx
        jne       ..B1.194
..B1.225:
        cmpl      $65536, %r8d
        jae       ..B1.227
..B1.226:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 216(%rsp)
..B1.227:
        xorl      %eax, %eax
        testl     %ebp, %ebp
        je        ..L377
        movq      %rax, 240(%rsp)
..L377:
        cmpl      $0, 128(%rsp)
        je        ..B1.229
..B1.228:
        lea       _zeros(%rip), %rax
        movq      240(%rsp), %rdx
        fldl      (%rax,%rdx,8)
        jmp       ..B1.230
..B1.229:
        lea       _infs(%rip), %rax
        movq      240(%rsp), %rdx
        fldl      (%rax,%rdx,8)
..B1.230:
        movq      264(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.159
        jmp       ..B1.44
..B1.233:
        movq      $0x8000000000000000, %rcx
        cmpq      336(%rsp), %rcx
        jne       ..B1.190
..B1.234:
        cmpl      $32767, %r12d
        je        ..B1.189
        jmp       ..B1.193
        .align    16,0x90
	.cfi_endproc
	.type	powl,@function
	.size	powl,.-powl
	.data
# -- End  powl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0xf0,0x17,0x5c,0x29,0x3b,0xaa,0xb8,0x07,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xf6,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xd4,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_43H:
	.long	0
	.long	1118306304
	.type	_TWO_43H,@object
	.size	_TWO_43H,8
	.align 4
_exact_masks:
	.long	131071
	.long	0
	.long	4294967295
	.long	1
	.long	4294967295
	.long	511
	.long	4294967295
	.long	16383
	.long	4294967295
	.long	131071
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	2097151
	.long	4294967295
	.long	8388607
	.long	4294967295
	.long	16777215
	.long	4294967295
	.long	33554431
	.long	4294967295
	.long	33554431
	.long	4294967295
	.long	67108863
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.type	_exact_masks,@object
	.size	_exact_masks,376
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_LT2:
	.long	180342526
	.long	2357584585
	.long	2340989257
	.long	193458247
	.long	2370723239
	.long	2317577053
	.long	2382055315
	.long	232148275
	.long	200972627
	.long	2376530255
	.long	2376860906
	.long	222325808
	.long	228268523
	.long	229816737
	.long	2387925496
	.long	239088248
	.long	235445027
	.long	219576635
	.long	2365457762
	.long	200344795
	.long	2377779510
	.long	2396359259
	.long	256760275
	.long	2390604874
	.long	2403557039
	.long	2397936349
	.long	2387307486
	.long	2389941185
	.long	2375749593
	.long	224865835
	.long	2394232861
	.long	180745560
	.long	231885773
	.long	2352962188
	.long	2306055684
	.long	206875141
	.long	258325764
	.long	2386208973
	.long	231066739
	.long	195513036
	.long	2360327132
	.long	2396045581
	.long	236564318
	.long	2282584976
	.long	2351736009
	.long	236364231
	.long	2384427773
	.long	267355851
	.long	255771104
	.long	265321597
	.long	2384375360
	.long	2399209743
	.long	186438128
	.long	2407131686
	.long	2382793784
	.long	2395218767
	.long	229223248
	.long	256892528
	.long	2394735602
	.long	2385223602
	.long	2372921302
	.long	2398642377
	.long	2399679754
	.long	255686225
	.long	220689523
	.long	2385772978
	.long	209771590
	.long	2396079173
	.long	247531082
	.long	236085121
	.long	264660069
	.long	2357821390
	.long	236486650
	.long	2368138461
	.long	239690108
	.long	2396070228
	.long	196955922
	.long	217533735
	.long	255641653
	.long	249646543
	.long	253232621
	.long	246212714
	.long	2396427575
	.long	261629730
	.long	2364672847
	.long	2403865410
	.long	251273869
	.long	261125380
	.long	2388321036
	.long	2375891827
	.long	2381014317
	.long	2414822930
	.long	2406245053
	.long	2406988902
	.long	2395399973
	.long	213294711
	.long	2401319324
	.long	265450299
	.long	231845893
	.long	260578794
	.long	258934474
	.long	2368795786
	.long	2422506717
	.long	218205033
	.long	258708287
	.long	257044854
	.long	2396281866
	.long	2415975150
	.long	257697283
	.long	2393801226
	.long	2388654986
	.long	262910764
	.long	235638432
	.long	268054962
	.long	271083258
	.long	256780444
	.long	256064350
	.long	252979189
	.long	2420460964
	.long	233178508
	.long	251655134
	.long	2404577473
	.long	2408876374
	.long	2401072099
	.long	228623072
	.long	2421706621
	.long	2353033467
	.long	2411792212
	.long	2345330012
	.long	2397189005
	.long	243110369
	.long	2408213151
	.long	219253901
	.long	246695172
	.long	259288551
	.long	254210780
	.long	261865355
	.long	230359170
	.long	263524551
	.long	2405618542
	.long	2399869921
	.long	233826616
	.long	2362750769
	.long	257576291
	.long	251808114
	.long	2403390977
	.long	253249058
	.long	261065811
	.long	215406974
	.long	2407628993
	.long	258391343
	.long	2394470699
	.long	258495470
	.long	2415771687
	.long	224670583
	.long	263047889
	.long	2389031226
	.long	259453051
	.long	266564569
	.long	2422023012
	.long	264051375
	.long	2409075373
	.long	252244729
	.long	220302768
	.long	254847756
	.long	243124762
	.long	2363519942
	.long	2393496158
	.long	2402135082
	.long	2365706590
	.long	237902971
	.long	262295026
	.long	252689257
	.long	236678680
	.long	255167613
	.long	2406879752
	.long	274254693
	.long	259983961
	.long	245852525
	.long	256459527
	.long	220533989
	.long	245387845
	.long	243835221
	.long	2377853444
	.long	274540751
	.long	229741069
	.long	263626566
	.long	220608387
	.long	226161046
	.long	247301717
	.long	268433686
	.long	222008210
	.long	2414961139
	.long	276137642
	.long	2412197898
	.long	258445918
	.long	2412645341
	.long	260857859
	.long	263113037
	.long	2386484264
	.long	2390742630
	.long	259665290
	.long	2363417497
	.long	2384122046
	.long	263493300
	.long	219472625
	.long	2420433676
	.long	274201285
	.long	2404911312
	.long	257926520
	.long	260332203
	.long	256695016
	.long	218494534
	.long	2420150889
	.long	2414586287
	.long	267006159
	.long	231320206
	.long	2382797064
	.long	2414903062
	.long	263514471
	.long	2406010284
	.long	2410428205
	.long	2414319991
	.long	249083715
	.long	2381171937
	.long	236369629
	.long	2409614279
	.long	2424220710
	.long	2378379154
	.long	218677462
	.long	2382987336
	.long	2377870458
	.long	258155310
	.long	2368239928
	.long	2414896572
	.long	193310867
	.long	256673115
	.long	2406448055
	.long	271719709
	.long	248566852
	.long	229016248
	.long	272541555
	.long	2382115967
	.long	2375311600
	.long	2393766434
	.long	257187362
	.long	277931545
	.long	281038165
	.long	2373962226
	.long	261638946
	.long	255465134
	.long	273460929
	.long	275851296
	.long	2417166241
	.long	2361106656
	.long	278653754
	.type	_LT2,@object
	.size	_LT2,1024
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
ESC2_BIAS:
	.long	4095
	.long	28671
	.type	ESC2_BIAS,@object
	.size	ESC2_BIAS,8
	.align 4
_TWO_12H:
	.long	0
	.long	1085800448
	.type	_TWO_12H,@object
	.size	_TWO_12H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_invsqrtl_scales:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24575
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	20479
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	12287
	.word	0
	.word	0
	.word	0
	.type	_invsqrtl_scales,@object
	.size	_invsqrtl_scales,64
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
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.align 2
_LP:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
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
	.word	52785
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	44453
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	25655
	.word	14678
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	38741
	.word	53722
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	45676
	.word	59527
	.word	48579
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	41682
	.word	64796
	.word	25002
	.word	52429
	.word	49147
	.word	0
	.word	0
	.word	0
	.type	_LP,@object
	.size	_LP,144
	.align 2
_LLN2:
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
	.type	_LLN2,@object
	.size	_LLN2,32
	.align 2
_EP1:
	.word	43691
	.word	10
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30584
	.word	43703
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_EP1,@object
	.size	_EP1,32
	.align 2
_EP:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.type	_EP,@object
	.size	_EP,80
	.align 2
_Eexpbut:
	.word	60207
	.word	4624
	.word	35943
	.word	45420
	.word	49164
	.word	0
	.word	0
	.word	0
	.type	_Eexpbut,@object
	.size	_Eexpbut,16
	.align 2
_ESC2:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	28671
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	4095
	.word	0
	.word	0
	.word	0
	.type	_ESC2,@object
	.size	_ESC2,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
