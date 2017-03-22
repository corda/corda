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
	.file "clog10l.c"
	.text
..TXTST0:
# -- Begin  clog10l
	.text
       .align    16,0x90
	.globl clog10l
clog10l:
# parameter 1: 352 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_clog10l.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        subq      $312, %rsp
	.cfi_def_cfa_offset 352
        xorb      %r12b, %r12b
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 304(%rsp)
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fstpt     288(%rsp)
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movzwl    82(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.5:
        fldcw     80(%rsp)
..B1.6:
        movb      $1, %r12b
..B1.7:
        movzwl    360(%rsp), %edx
        andl      $32767, %edx
        movzwl    376(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %edx
        jge       ..B1.46
..B1.8:
        cmpl      $32767, %eax
        jge       ..B1.82
..B1.9:
        testl     %edx, %edx
        jne       ..B1.15
..B1.10:
        cmpl      $0, 356(%rsp)
        jne       ..B1.15
..B1.11:
        cmpl      $0, 352(%rsp)
        jne       ..B1.15
..B1.12:
        testl     %eax, %eax
        jne       ..B1.15
..B1.13:
        cmpl      $0, 372(%rsp)
        jne       ..B1.15
..B1.14:
        cmpl      $0, 368(%rsp)
        je        ..B1.45
..B1.15:
        fldt      352(%rsp)
        lea       368(%rsp), %rbx
        fld       %st(0)
        fabs      
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      (%rbx)
        fld       %st(0)
        fabs      
        fstpt     (%rsp)
        fldt      (%rsp)
        movzwl    24(%rsp), %eax
        movzwl    8(%rsp), %edx
        andl      $32767, %eax
        andl      $32767, %edx
        cmpl      %edx, %eax
        jl        ..B1.20
..B1.16:
        jne       ..B1.21
..B1.17:
        movl      20(%rsp), %edx
        movl      4(%rsp), %ecx
        cmpl      %ecx, %edx
        jb        ..B1.20
..B1.18:
        jne       ..B1.21
..B1.19:
        movl      16(%rsp), %edx
        cmpl      (%rsp), %edx
        jae       ..B1.21
..B1.20:
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movzwl    24(%rsp), %eax
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        andl      $32767, %eax
..B1.21:
        cmpl      $16382, %eax
        jg        ..B1.26
..B1.22:
        jne       ..B1.42
..B1.23:
        lea       16+_CONSTANTS(%rip), %rcx
        movl      20(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.26
..B1.24:
        jne       ..B1.42
..B1.25:
        movl      (%rcx), %edx
        cmpl      16(%rsp), %edx
        ja        ..B1.42
..B1.26:
        cmpl      $16383, %eax
        jl        ..B1.31
..B1.27:
        jne       ..B1.42
..B1.28:
        lea       32+_CONSTANTS(%rip), %rcx
        movl      20(%rsp), %edx
        cmpl      4(%rcx), %edx
        jb        ..B1.31
..B1.29:
        jne       ..B1.42
..B1.30:
        movl      16(%rsp), %edx
        cmpl      (%rcx), %edx
        ja        ..B1.42
..B1.31:
        movzwl    8(%rsp), %esi
        andl      $32767, %esi
        cmpl      $8256, %esi
        jg        ..B1.36
..B1.32:
        jne       ..B1.37
..B1.33:
        lea       48+_CONSTANTS(%rip), %rcx
        movl      4(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.36
..B1.34:
        jne       ..B1.37
..B1.35:
        movl      (%rsp), %edx
        cmpl      (%rcx), %edx
        jbe       ..B1.37
..B1.36:
        fld       %st(2)
        lea       _CONSTANTS(%rip), %rdx
        fmul      %st(3), %st
        fld       %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.10(%rip)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fabs      
        fldt      (%rdx)
        fcomip    %st(1), %st
        fstp      %st(0)
        ja        ..B1.38
        jmp       ..B1.83
..B1.37:
        fldt      .L_2il0floatpacket.10(%rip)
        fld       %st(3)
        fmul      %st(4), %st
        lea       _CONSTANTS(%rip), %rdx
        fsub      %st(1), %st
        fabs      
        fldt      (%rdx)
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B1.83
..B1.38:
        fstp      %st(2)
        cmpl      $16183, %esi
        jl        ..B1.40
..B1.39:
        fldt      .L_2il0floatpacket.9(%rip)
        fld       %st(3)
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rax
        fxch      %st(5)
        fstpt     (%rsp)
        fld       %st(3)
        lea       _Q1(%rip), %rdx
        fsubr     %st(5), %st
        lea       96+_CONSTANTS(%rip), %r15
        fsubrp    %st, %st(5)
        fld       %st(4)
        lea       112+_CONSTANTS(%rip), %r13
        fmul      %st(5), %st
        fld       %st(2)
        fmulp     %st, %st(2)
        fxch      %st(4)
        fsub      %st(5), %st
        fld       %st(2)
        fsubr     %st(2), %st
        fsubrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.13(%rip)
        fmul      %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(4)
        fmul      %st(0), %st
        fstpt     48(%rsp)
        fld       %st(5)
        fadd      %st(3), %st
        fsub      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(3)
        fmulp     %st, %st(6)
        fmul      %st, %st(5)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fld       %st(0)
        fsub      %st(5), %st
        fsubr     %st, %st(2)
        fld       %st(1)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(5), %st
        fld       %st(0)
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(4)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        faddp     %st, %st(1)
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(1)
        fld       %st(4)
        fsub      %st(6), %st
        fsubr     %st, %st(7)
        fsubr     %st(5), %st
        fsubrp    %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fld       %st(1)
        fsub      %st(3), %st
        fldt      32(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(3)
        fldt      64(%rsp)
        fld       %st(0)
        fsub      %st(3), %st
        fldt      16(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(4)
        fsub      %st(2), %st
        fldt      48(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(6), %st
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.9(%rip)
        fld       %st(0)
        fmul      %st(4), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%r15)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      (%r13)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.11(%rip)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     272(%rsp)
        fldt      (%rsp)
        jmp       ..B1.49
..B1.40:
        cmpl      $16383, %eax
        je        ..B1.56
..B1.41:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.9(%rip)
        fld       %st(2)
        fmul      %st(1), %st
        fld       %st(3)
        lea       16+_Q1(%rip), %rax
        fsubr     %st(1), %st
        lea       _Q1(%rip), %rdx
        fsubrp    %st, %st(1)
        fld       %st(0)
        lea       96+_CONSTANTS(%rip), %r15
        lea       112+_CONSTANTS(%rip), %r13
        fmul      %st(1), %st
        fsubp     %st, %st(3)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.13(%rip)
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fld       %st(3)
        fxch      %st(5)
        fmul      %st(0), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fadd      %st, %st(5)
        fxch      %st(4)
        fsubr     %st(5), %st
        fxch      %st(2)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      (%rax)
        fmul      %st(4), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%r15)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      (%r13)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.11(%rip)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     272(%rsp)
        fxch      %st(1)
        jmp       ..B1.49
..B1.42:
        fstp      %st(0)
        fstp      %st(1)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        lea       64(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_clog10l.13:
        call      __libm_hypot2l_k80@PLT
..___tag_value_clog10l.14:
..B1.78:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
..B1.43:
        fldt      .L_2il0floatpacket.11(%rip)
        lea       160+_CONSTANTS(%rip), %rax
        lea       176+_CONSTANTS(%rip), %rdx
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmul      %st(1), %st
        fstpt     16(%rsp)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fldt      64(%rsp)
        lea       128(%rsp), %rdi
        fstpt     (%rsp)
        fldt      -48(%rdi)
        fstpt     16(%rsp)
..___tag_value_clog10l.17:
        call      __libm_logl_k80@PLT
..___tag_value_clog10l.18:
..B1.79:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
..B1.44:
        fldt      .L_2il0floatpacket.11(%rip)
        lea       96+_CONSTANTS(%rip), %r15
        fldt      96(%rsp)
        lea       112+_CONSTANTS(%rip), %r13
        fxch      %st(2)
        fmulp     %st, %st(1)
        fmul      %st, %st(1)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fsub      %st(4), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%r15)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      (%r13)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(4), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%rsp)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     272(%rsp)
        fldt      352(%rsp)
        jmp       ..B1.49
..B1.45:
        fldt      .L_2il0floatpacket.9(%rip)
        lea       368(%rsp), %rbx
        movsd     .L_2il0floatpacket.12(%rip), %xmm1
        pxor      %xmm0, %xmm0
        divsd     %xmm0, %xmm1
        movsd     %xmm1, (%rsp)
        lea       96+_CONSTANTS(%rip), %r15
        fldl      (%rsp)
        lea       112+_CONSTANTS(%rip), %r13
        fstpt     272(%rsp)
        fldt      -16(%rbx)
        jmp       ..B1.49
..B1.46:
        je        ..B1.70
..B1.47:
        cmpl      $32767, %eax
        je        ..B1.64
..B1.48:
        fldt      .L_2il0floatpacket.9(%rip)
        lea       368(%rsp), %rbx
        lea       64+_CONSTANTS(%rip), %rax
        lea       96+_CONSTANTS(%rip), %r15
        lea       112+_CONSTANTS(%rip), %r13
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     272(%rsp)
        fldt      -16(%rbx)
..B1.49:
        fldt      .L_2il0floatpacket.8(%rip)
        lea       224(%rsp), %rdi
        xorl      %eax, %eax
        lea       176(%rsp), %rsi
        movl      %eax, -16(%rsi)
        lea       128(%rsp), %rdx
        fstpt     16(%rdx)
        fldt      16(%rdx)
        fxch      %st(1)
        fstpt     (%rdx)
        movl      %eax, 80(%rdx)
        fstpt     64(%rdx)
        fstpt     -112(%rdx)
        fldt      (%rbx)
        fstpt     48(%rdx)
..___tag_value_clog10l.20:
        call      __libm_atan2l_k80@PLT
..___tag_value_clog10l.21:
..B1.50:
        fldt      16(%rsp)
        fldt      224(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        movl      256(%rsp), %edi
        fsubr     %st(2), %st
        fsubrp    %st, %st(2)
        fsub      %st(1), %st
        fldt      240(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        faddp     %st, %st(1)
        fldt      (%r15)
        fld       %st(0)
        fmul      %st(3), %st
        fstpt     (%rsp)
        fldt      (%r13)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_clog10l.23:
        call      __libm_scalbl_k80@PLT
..___tag_value_clog10l.24:
..B1.80:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
..B1.51:
        fstpt     288(%rsp)
        testb     %r12b, %r12b
        je        ..B1.53
..B1.52:
        fldcw     82(%rsp)
..B1.53:
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.63
..B1.54:
        fldt      272(%rsp)
        fldt      288(%rsp)
        movq      304(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.62
..B1.55:
        addq      $312, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 352
	.cfi_offset 3, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 15, -32
..B1.56:
        cmpl      $-2147483648, 20(%rsp)
        jne       ..B1.41
..B1.57:
        cmpl      $0, 16(%rsp)
        jne       ..B1.41
..B1.58:
        fstp      %st(2)
        fstp      %st(0)
        cmpq      $0, (%rsp)
        jne       ..B1.60
..B1.59:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.8(%rip)
        lea       96+_CONSTANTS(%rip), %r15
        fstpt     272(%rsp)
        lea       112+_CONSTANTS(%rip), %r13
        fldt      .L_2il0floatpacket.9(%rip)
        fxch      %st(1)
        jmp       ..B1.49
..B1.60:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.13(%rip)
        lea       144+_CONSTANTS(%rip), %r13
        fldt      (%r13)
        lea       112+_CONSTANTS(%rip), %r13
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      .L_2il0floatpacket.9(%rip)
        movzwl    8(%rsp), %eax
        movl      %eax, %r15d
        andl      $-32768, %eax
        fld       %st(0)
        orl       $-49153, %eax
        fld       %st(1)
        movw      %ax, 8(%rsp)
        andl      $32767, %r15d
        fldt      (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fmul      %st, %st(2)
        fld       %st(0)
        fsubr     %st(3), %st
        fsubrp    %st, %st(3)
        fld       %st(2)
        lea       -33167(%r15,%r15), %edi
        fmul      %st(3), %st
        lea       96+_CONSTANTS(%rip), %r15
        fmul      %st, %st(2)
        fld       %st(2)
        fsub      %st(1), %st
        fsubrp    %st, %st(3)
        fsub      %st(2), %st
        fxch      %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        fxch      %st(5)
        fmulp     %st, %st(3)
        fld       %st(4)
        fmulp     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      (%r15)
        fld       %st(0)
        fmul      %st(4), %st
        fstpt     (%rsp)
        fldt      (%r13)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     48(%rsp)
..___tag_value_clog10l.41:
        call      __libm_scalbl_k80@PLT
..___tag_value_clog10l.42:
..B1.81:
        fldt      48(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
..B1.61:
        fxch      %st(1)
        fstpt     272(%rsp)
        fldt      352(%rsp)
        jmp       ..B1.49
..B1.62:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.63:
        call      __stack_chk_fail@PLT
..B1.64:
        cmpl      $-2147483648, 372(%rsp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 368(%rsp)
        je        ..B1.48
..B1.66:
        cmpl      $32767, %edx
        je        ..B1.68
..B1.67:
        fldt      .L_2il0floatpacket.9(%rip)
        lea       368(%rsp), %rbx
        fldt      (%rbx)
        lea       96+_CONSTANTS(%rip), %r15
        fmul      %st(0), %st
        lea       112+_CONSTANTS(%rip), %r13
        fstpt     272(%rsp)
        fldt      -16(%rbx)
        jmp       ..B1.49
..B1.68:
        movq      $0x8000000000000000, %rax
        cmpq      352(%rsp), %rax
        jne       ..B1.67
..B1.69:
        fldt      352(%rsp)
        lea       368(%rsp), %rbx
        fld       %st(0)
        lea       96+_CONSTANTS(%rip), %r15
        fmul      %st(1), %st
        lea       112+_CONSTANTS(%rip), %r13
        fstpt     272(%rsp)
        fldt      .L_2il0floatpacket.9(%rip)
        fxch      %st(1)
        jmp       ..B1.49
..B1.70:
        cmpl      $-2147483648, 356(%rsp)
        jne       ..B1.72
..B1.71:
        cmpl      $0, 352(%rsp)
        je        ..B1.47
..B1.72:
        cmpl      $32767, %eax
        je        ..B1.74
..B1.73:
        fldt      352(%rsp)
        lea       368(%rsp), %rbx
        fldt      (%rbx)
        lea       96+_CONSTANTS(%rip), %r15
        fmul      %st(1), %st
        lea       112+_CONSTANTS(%rip), %r13
        fstpt     272(%rsp)
        fldt      .L_2il0floatpacket.9(%rip)
        fxch      %st(1)
        jmp       ..B1.49
..B1.74:
        movq      $0x8000000000000000, %rax
        cmpq      368(%rsp), %rax
        jne       ..B1.73
..B1.75:
        fldt      .L_2il0floatpacket.9(%rip)
        lea       368(%rsp), %rbx
        fldt      (%rbx)
        lea       96+_CONSTANTS(%rip), %r15
        fmul      %st(0), %st
        lea       112+_CONSTANTS(%rip), %r13
        fstpt     272(%rsp)
        fldt      -16(%rbx)
        jmp       ..B1.49
..B1.82:
        cmpl      $32767, %edx
        je        ..B1.70
        jmp       ..B1.47
..B1.83:
        fstp      %st(0)
        jmp       ..B1.42
        .align    16,0x90
	.cfi_endproc
	.type	clog10l,@function
	.size	clog10l,.-clog10l
	.data
# -- End  clog10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,16
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	37342
	.word	47265
	.word	62214
	.word	46340
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	64512
	.word	65535
	.word	31
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8256
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	55465
	.word	56923
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	43711
	.word	54638
	.word	50772
	.word	56481
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16583
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39557
	.word	39456
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	51839
	.word	3796
	.word	3310
	.word	34305
	.word	49112
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,192
	.align 2
_Q1:
	.word	3134
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	49331
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
