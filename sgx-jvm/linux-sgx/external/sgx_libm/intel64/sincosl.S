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
	.file "sincosl.c"
	.text
..TXTST0:
# -- Begin  sincosl
	.text
       .align    16,0x90
	.globl sincosl
sincosl:
# parameter 1: 224 + %rsp
# parameter 2: %rdi
# parameter 3: %rsi
..B1.1:
	.cfi_startproc
..___tag_value_sincosl.1:
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
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $176, %rsp
	.cfi_def_cfa_offset 224
        movq      %rsi, %r14
        movq      %fs:40, %rax
        movq      %rdi, %r13
        xorq      %rsp, %rax
        xorb      %r12b, %r12b
        movq      %rax, 168(%rsp)
..B1.2:
        fnstcw    146(%rsp)
..B1.3:
        movzwl    232(%rsp), %edx
        andl      $32767, %edx
        movzwl    146(%rsp), %ecx
        cmpl      $32767, %edx
        je        ..B1.70
..B1.4:
        cmpl      $16378, %edx
        jge       ..B1.42
..B1.5:
        cmpl      $16372, %edx
        jge       ..B1.34
..B1.6:
        cmpl      $16364, %edx
        jge       ..B1.26
..B1.7:
        cmpl      $16308, %edx
        jge       ..B1.18
..B1.8:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.10:
        fldcw     144(%rsp)
..B1.11:
        movzwl    232(%rsp), %edx
        movb      $1, %r12b
        andl      $32767, %edx
..B1.12:
        testl     %edx, %edx
        jle       ..B1.59
..B1.13:
        fldt      224(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       _small_value_80(%rip), %rcx
        lea       8+_TWO_75(%rip), %rdx
        lea       _ones(%rip), %rbx
        fldl      (%rax)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fldt      (%rcx)
        fsubrl    (%rbx)
        fstpt     32(%rsp)
        fmull     (%rdx)
        fstpt     16(%rsp)
..B1.14:
        testb     %r12b, %r12b
        je        ..B1.16
..B1.15:
        fldcw     146(%rsp)
..B1.16:
        fldt      16(%rsp)
        fstpt     (%r13)
        fldt      32(%rsp)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.17:
        addq      $176, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.18:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.22
..B1.19:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.20:
        fldcw     144(%rsp)
..B1.21:
        movb      $1, %r12b
..B1.22:
        fldt      224(%rsp)
        lea       _SP3(%rip), %rax
        lea       _CP3(%rip), %rdx
        lea       _ones(%rip), %rcx
        testb     %r12b, %r12b
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        fadd      %st(1), %st
        fstpt     32(%rsp)
        fldt      (%rdx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        faddl     (%rcx)
        fstpt     16(%rsp)
        je        ..B1.24
..B1.23:
        fldcw     146(%rsp)
..B1.24:
        fldt      32(%rsp)
        fstpt     (%r13)
        fldt      16(%rsp)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.25:
        addq      $176, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.26:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.30
..B1.27:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.28:
        fldcw     144(%rsp)
..B1.29:
        movb      $1, %r12b
..B1.30:
        fldt      224(%rsp)
        lea       16+_SP2(%rip), %rax
        fld       %st(0)
        lea       _SP2(%rip), %rdx
        fmul      %st(1), %st
        lea       16+_CP2(%rip), %rcx
        fldt      (%rax)
        lea       _CP2(%rip), %rbx
        fmul      %st(1), %st
        lea       _ones(%rip), %rbp
        fldt      (%rdx)
        testb     %r12b, %r12b
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      (%rcx)
        fmul      %st(1), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddl     (%rbp)
        fstpt     16(%rsp)
        je        ..B1.32
..B1.31:
        fldcw     146(%rsp)
..B1.32:
        fldt      32(%rsp)
        fstpt     (%r13)
        fldt      16(%rsp)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.33:
        addq      $176, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.34:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.35:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.36:
        fldcw     144(%rsp)
..B1.37:
        movb      $1, %r12b
..B1.38:
        fldt      224(%rsp)
        lea       32+_SP1(%rip), %rcx
        fld       %st(0)
        lea       _SP1(%rip), %rbx
        fmul      %st(1), %st
        lea       48+_SP1(%rip), %rax
        fld       %st(0)
        lea       48+_CP1(%rip), %rbp
        fmul      %st(1), %st
        lea       16+_SP1(%rip), %rdx
        fldt      (%rax)
        lea       16+_CP1(%rip), %rsi
        fmul      %st(1), %st
        lea       32+_CP1(%rip), %rdi
        fldt      (%rdx)
        lea       _CP1(%rip), %r8
        lea       _ones(%rip), %r9
        testb     %r12b, %r12b
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmul      %st(2), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      (%rbp)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        fmulp     %st, %st(3)
        fldt      (%r8)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddl     (%r9)
        fstpt     16(%rsp)
        je        ..B1.40
..B1.39:
        fldcw     146(%rsp)
..B1.40:
        fldt      32(%rsp)
        fstpt     (%r13)
        fldt      16(%rsp)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.41:
        addq      $176, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.42:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.46
..B1.43:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.44:
        fldcw     144(%rsp)
..B1.45:
        movb      $1, %r12b
..B1.46:
        fldt      224(%rsp)
        lea       _ones(%rip), %rbp
        movzbl    233(%rsp), %ebx
        xorl      %eax, %eax
        andl      $128, %ebx
        shrl      $7, %ebx
        fmull     (%rbp,%rbx,8)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        movzwl    232(%rsp), %edx
        andl      $32767, %edx
        cmpl      $16382, %edx
        jge       ..B1.48
..B1.47:
        lea       _TWO_52H(%rip), %rcx
        fld       %st(0)
        xorl      %edx, %edx
        fldl      (%rcx)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(1)
        fsubp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.51
..B1.48:
        je        ..B1.67
..B1.49:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 240
        xorl      %edi, %edi
        lea       168(%rsp), %rsi
        fstpt     (%rsp)
..___tag_value_sincosl.83:
        call      __libm_reduce_pi04l@PLT
..___tag_value_sincosl.84:
..B1.85:
        movl      %eax, %edx
        addq      $16, %rsp
	.cfi_def_cfa_offset 224
..B1.50:
        fldl      152(%rsp)
        lea       1(%rdx), %eax
        lea       _TWO_52H(%rip), %rcx
        fld       %st(0)
        movsd     152(%rsp), %xmm0
        shrl      $2, %eax
        xorl      %eax, %ebx
        lea       3(%rdx), %eax
        mulsd     (%rcx), %xmm0
        andl      $1, %ebx
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        shrl      $2, %eax
        fadd      %st, %st(1)
        andl      $1, %eax
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubr     %st, %st(1)
        fldl      160(%rsp)
        faddp     %st, %st(2)
        fadd      %st(1), %st
..B1.51:
        fldt      16(%rsp)
        lea       112+_SP(%rip), %rcx
        fld       %st(0)
        lea       80+_SP(%rip), %rsi
        fmul      %st(3), %st
        lea       48+_SP(%rip), %rdi
        fxch      %st(1)
        fmul      %st(0), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(5)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        lea       128+_SP(%rip), %r9
        fmul      %st(4), %st
        lea       16+_SP(%rip), %r8
        lea       96+_SP(%rip), %r10
        lea       64+_SP(%rip), %r11
        movl      %ebx, %ebx
        incl      %edx
        testb     $2, %dl
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(1)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(5), %st
        fxch      %st(4)
        fstpt     64(%rsp)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rcx)
        lea       32+_SP(%rip), %rcx
        fmul      %st(4), %st
        fldt      (%rsi)
        lea       _SP(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       160+_SP(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        lea       144+_SP(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        lea       _TWO_53H(%rip), %r9
        fmul      %st(5), %st
        fldt      (%r10)
        lea       112+_CP(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r11)
        lea       80+_CP(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       48+_CP(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rsi)
        lea       16+_CP(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(5)
        fldt      (%rdi)
        lea       128+_CP(%rip), %rdi
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        fldt      (%r8)
        lea       96+_CP(%rip), %r8
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fldl      (%r9)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        lea       64+_CP(%rip), %r9
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      64(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%r10)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       32+_CP(%rip), %r10
        lea       _CP(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        lea       160+_CP(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        lea       144+_CP(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      (%r11)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rcx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      (%rsi)
        fldt      80(%rsp)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldt      112(%rsp)
        fmulp     %st, %st(4)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fldt      128(%rsp)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fldl      (%rbp)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldl      (%rbp,%rbx,8)
        fldl      (%rbp,%rax,8)
        je        ..B1.53
..B1.52:
        fxch      %st(1)
        fmul      %st, %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.54
..B1.53:
        fldt      32(%rsp)
        fmul      %st(2), %st
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
..B1.54:
        testb     %r12b, %r12b
        je        ..B1.56
..B1.55:
        fldcw     146(%rsp)
..B1.56:
        fldt      16(%rsp)
        fstpt     (%r13)
        fldt      32(%rsp)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.57:
        addq      $176, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
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
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.58:
        call      __stack_chk_fail@PLT
..B1.59:
        cmpl      $0, 228(%rsp)
        jne       ..B1.61
..B1.60:
        cmpl      $0, 224(%rsp)
        je        ..B1.62
..B1.61:
        fldt      224(%rsp)
        lea       _TWO_75(%rip), %rbx
        movb      233(%rsp), %al
        lea       _small_value_80(%rip), %rcx
        andb      $-128, %al
        lea       _ones(%rip), %rsi
        lea       8+_TWO_75(%rip), %rbp
        fldl      (%rbx)
        shrb      $7, %al
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fldt      (%rcx)
        fxch      %st(1)
        fmull     (%rbp)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fstpt     16(%rsp)
        fldl      (%rsi)
        fsub      %st(1), %st
        fstpt     32(%rsp)
        fldt      (%rcx,%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.14
..B1.62:
        fldt      224(%rsp)
        lea       _ones(%rip), %rax
        fstpt     16(%rsp)
        fldl      (%rax)
        fstpt     32(%rsp)
        jmp       ..B1.14
..B1.67:
        movl      228(%rsp), %edx
        cmpl      $-921707870, %edx
        jb        ..B1.47
..B1.68:
        jne       ..B1.49
..B1.69:
        cmpl      $560513589, 224(%rsp)
        jbe       ..B1.47
        jmp       ..B1.49
..B1.70:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.74
..B1.71:
        orl       $-64768, %ecx
        movw      %cx, 144(%rsp)
..B1.72:
        fldcw     144(%rsp)
..B1.73:
        movb      $1, %r12b
..B1.74:
        cmpl      $-2147483648, 228(%rsp)
        jne       ..B1.77
..B1.75:
        cmpl      $0, 224(%rsp)
        jne       ..B1.77
..B1.76:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.78
..B1.77:
        fldt      224(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
..B1.78:
        testb     %r12b, %r12b
        je        ..B1.80
..B1.79:
        fldcw     146(%rsp)
..B1.80:
        fldt      16(%rsp)
        fstpt     (%r13)
        fldt      (%r13)
        fstpt     (%r14)
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
        jmp       ..B1.17
        .align    16,0x90
	.cfi_endproc
	.type	sincosl,@function
	.size	sincosl,.-sincosl
	.data
# -- End  sincosl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
_SP3:
	.word	17476
	.word	43656
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_SP3,@object
	.size	_SP3,16
	.align 2
_CP3:
	.word	0
	.word	65450
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.type	_CP3,@object
	.size	_CP3,16
	.align 2
_SP2:
	.word	43210
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	1587
	.word	57422
	.word	34932
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP2,@object
	.size	_SP2,32
	.align 2
_CP2:
	.word	63855
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	18238
	.word	17476
	.word	43656
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP2,@object
	.size	_CP2,32
	.align 2
_SP1:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	12518
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	43088
	.word	7396
	.word	207
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	55827
	.word	48618
	.word	3602
	.word	47342
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_SP1,@object
	.size	_SP1,64
	.align 2
_CP1:
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	47533
	.word	43689
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	14131
	.word	49466
	.word	24756
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	37142
	.word	18013
	.word	35855
	.word	53259
	.word	16367
	.word	0
	.word	0
	.word	0
	.type	_CP1,@object
	.size	_CP1,64
	.align 2
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	34948
	.word	34952
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	53243
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	30577
	.word	46649
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	11760
	.word	43464
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	47000
	.word	43232
	.word	12444
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	52983
	.word	47009
	.word	40440
	.word	55103
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	35433
	.word	14927
	.word	40941
	.word	51860
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	1340
	.word	16361
	.word	50747
	.word	38523
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP,@object
	.size	_SP,176
	.align 2
_CP:
	.word	51631
	.word	16464
	.word	16497
	.word	44012
	.word	16306
	.word	0
	.word	0
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	46557
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	28380
	.word	3328
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	23816
	.word	50302
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	49866
	.word	64527
	.word	51070
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	65056
	.word	3638
	.word	41889
	.word	51659
	.word	49114
	.word	0
	.word	0
	.word	0
	.word	13834
	.word	11317
	.word	33607
	.word	55101
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	40709
	.word	42374
	.word	64411
	.word	45709
	.word	49098
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
	.word	43008
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP,@object
	.size	_CP,176
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
