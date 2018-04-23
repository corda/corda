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
	.file "y0.c"
	.text
..TXTST0:
# -- Begin  y0
	.text
       .align    16,0x90
	.globl y0
y0:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_y0.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        subq      $16, %rsp
	.cfi_def_cfa_offset 32
        xorb      %bl, %bl
        movsd     %xmm0, (%rsp)
..B1.2:
        fnstcw    10(%rsp)
..B1.3:
        movl      4(%rsp), %edx
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $2146435072, %ecx
        jge       ..B1.13
..B1.4:
        cmpq      $0, (%rsp)
        jle       ..B1.19
..B1.5:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.7:
        fldcw     8(%rsp)
..B1.8:
        movb      $1, %bl
..B1.9:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 48
        fldl      16(%rsp)
        fstpt     (%rsp)
..___tag_value_y0.7:
        call      __y0l@PLT
..___tag_value_y0.8:
..B1.25:
        addq      $16, %rsp
	.cfi_def_cfa_offset 32
..B1.10:
        fstpl     (%rsp)
        testb     %bl, %bl
        movsd     (%rsp), %xmm0
        je        ..B1.12
..B1.11:
        fldcw     10(%rsp)
..B1.12:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -16
..B1.13:
        andl      $-2146435073, %ecx
        orl       (%rsp), %ecx
        jne       ..B1.18
..B1.14:
        lea       _zeros(%rip), %rax
        testl     $-2147483648, %edx
        movsd     (%rax), %xmm0
        je        ..B1.17
..B1.15:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
..B1.17:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -16
..B1.18:
        movsd     (%rsp), %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -16
..B1.19:
        lea       _zeros(%rip), %rax
        movl      (%rsp), %edx
        orl       %edx, %ecx
        movsd     (%rax), %xmm1
        je        ..B1.21
..B1.20:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -16
..B1.21:
        lea       8+_ones(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     %xmm1, %xmm0
..B1.22:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	y0,@function
	.size	y0,.-y0
	.data
# -- End  y0
	.text
# -- Begin  __y0l
	.text
       .align    16,0x90
	.globl __y0l
__y0l:
# parameter 1: 96 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___y0l.34:
..L35:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        lea       176+_S(%rip), %rax
        fldt      96(%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        jb        ..B2.22
..B2.2:
        lea       _val_0_6L(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        ja        ..B2.20
..B2.3:
        lea       _val_1_2L(%rip), %rax
        fldt      (%rax)
        fcomi     %st(1), %st
        jbe       ..B2.5
..B2.4:
        fstp      %st(0)
        lea       _Z(%rip), %rax
        lea       _P11(%rip), %rdx
        lea       16+_P11(%rip), %rcx
        lea       32+_P11(%rip), %rdi
        lea       48+_P11(%rip), %r8
        lea       64+_P11(%rip), %r9
        lea       80+_P11(%rip), %r10
        fldt      (%rax)
        lea       _Q11(%rip), %rsi
        lea       16+_Q11(%rip), %r11
        lea       96+_P11(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       112+_P11(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       128+_P11(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       144+_P11(%rip), %rsi
        fmul      %st(4), %st
        fldt      (%rdi)
        lea       32+_Q11(%rip), %rdi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r8)
        lea       160+_P11(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        lea       176+_P11(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        lea       192+_P11(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       208+_P11(%rip), %r11
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      (%rax)
        lea       48+_Q11(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rdx)
        lea       _ones(%rip), %rdx
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rcx)
        lea       224+_P11(%rip), %rcx
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rsi)
        lea       240+_P11(%rip), %rsi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rdi)
        lea       256+_P11(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        lea       272+_P11(%rip), %r8
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(3)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fdivrl    (%rdx)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B2.27
..B2.5:
        lea       _S(%rip), %rcx
        fldt      (%rcx)
        fcomip    %st(2), %st
        jbe       ..B2.7
..B2.6:
        lea       _P12(%rip), %rax
        lea       16+_P12(%rip), %rdx
        lea       32+_P12(%rip), %rcx
        lea       48+_P12(%rip), %rsi
        lea       64+_P12(%rip), %rdi
        lea       80+_P12(%rip), %r8
        lea       96+_P12(%rip), %r9
        lea       112+_P12(%rip), %r10
        fldt      (%rax)
        lea       128+_P12(%rip), %r11
        lea       144+_P12(%rip), %rax
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       160+_P12(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       176+_P12(%rip), %rcx
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      (%rsi)
        lea       192+_P12(%rip), %rsi
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdi)
        lea       208+_P12(%rip), %rdi
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r8)
        lea       224+_P12(%rip), %r8
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r9)
        lea       240+_P12(%rip), %r9
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r10)
        lea       256+_P12(%rip), %r10
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r11)
        lea       272+_P12(%rip), %r11
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rax)
        lea       288+_P12(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdx)
        lea       304+_P12(%rip), %rdx
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rcx)
        lea       320+_P12(%rip), %rcx
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rsi)
        lea       336+_P12(%rip), %rsi
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rdi)
        lea       352+_P12(%rip), %rdi
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r8)
        lea       368+_P12(%rip), %r8
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r9)
        lea       384+_P12(%rip), %r9
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r10)
        lea       400+_P12(%rip), %r10
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r11)
        lea       416+_P12(%rip), %r11
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rdx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rcx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdi)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r8)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r9)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r11)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.7:
        fstp      %st(0)
        lea       _val_3_0L(%rip), %rax
        fldt      (%rax)
        fcomi     %st(1), %st
        jbe       ..B2.9
..B2.8:
        lea       _P20(%rip), %rax
        lea       16+_P20(%rip), %rdx
        lea       32+_P20(%rip), %rcx
        lea       48+_P20(%rip), %rsi
        lea       64+_P20(%rip), %rdi
        lea       80+_P20(%rip), %r8
        lea       96+_P20(%rip), %r9
        lea       112+_P20(%rip), %r10
        lea       128+_P20(%rip), %r11
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rax)
        lea       144+_P20(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       160+_P20(%rip), %rdx
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       176+_P20(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       192+_P20(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       208+_P20(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       224+_P20(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       240+_P20(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       256+_P20(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       272+_P20(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r11)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.9:
        fstp      %st(0)
        lea       _val_3_875L(%rip), %rax
        fldt      (%rax)
        fcomi     %st(1), %st
        jbe       ..B2.11
..B2.10:
        lea       _P21(%rip), %rax
        lea       16+_P21(%rip), %rdx
        lea       32+_P21(%rip), %rcx
        lea       48+_P21(%rip), %rsi
        lea       64+_P21(%rip), %rdi
        lea       80+_P21(%rip), %r8
        lea       96+_P21(%rip), %r9
        lea       112+_P21(%rip), %r10
        fldt      (%rax)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(1)
        lea       128+_P21(%rip), %r11
        lea       144+_P21(%rip), %rax
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       160+_P21(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       176+_P21(%rip), %rcx
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      (%rsi)
        lea       192+_P21(%rip), %rsi
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdi)
        lea       208+_P21(%rip), %rdi
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r8)
        lea       224+_P21(%rip), %r8
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r9)
        lea       240+_P21(%rip), %r9
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%r10)
        lea       256+_P21(%rip), %r10
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r11)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rdx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rsi)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r8)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fldt      (%r9)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r10)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.11:
        fstp      %st(0)
        lea       16+_S(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        jb        ..B2.13
..B2.12:
        lea       16+_Z(%rip), %rax
        lea       _P22(%rip), %rdx
        lea       16+_P22(%rip), %rcx
        lea       32+_P22(%rip), %rsi
        lea       48+_P22(%rip), %rdi
        lea       64+_P22(%rip), %r8
        lea       80+_P22(%rip), %r9
        lea       96+_P22(%rip), %r10
        fldt      (%rax)
        lea       112+_P22(%rip), %r11
        lea       128+_P22(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       144+_P22(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       160+_P22(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       176+_P22(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       192+_P22(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       208+_P22(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       224+_P22(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       240+_P22(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       256+_P22(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       272+_P22(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       288+_P22(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.13:
        lea       32+_S(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        jbe       ..B2.15
..B2.14:
        lea       32+_Z(%rip), %rax
        lea       _P3(%rip), %rdx
        lea       16+_P3(%rip), %rcx
        lea       32+_P3(%rip), %rsi
        lea       48+_P3(%rip), %rdi
        lea       64+_P3(%rip), %r8
        lea       80+_P3(%rip), %r9
        fldt      (%rax)
        lea       96+_P3(%rip), %r10
        lea       112+_P3(%rip), %r11
        lea       128+_P3(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       144+_P3(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       160+_P3(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       176+_P3(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       192+_P3(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       208+_P3(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       224+_P3(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       240+_P3(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       256+_P3(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       272+_P3(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       288+_P3(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       304+_P3(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       320+_P3(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       336+_P3(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.15:
        movl      $3, %edx
        movl      $48, %eax
..B2.16:
        fldt      (%rax,%rcx)
        fcomip    %st(1), %st
        ja        ..B2.19
        jp        ..B2.19
..B2.17:
        incq      %rdx
        addq      $16, %rax
        cmpq      $11, %rdx
        jl        ..B2.16
..B2.18:
        movq      %rdx, %rax
        shlq      $4, %rax
..B2.19:
        lea       _Z(%rip), %rcx
        fldt      (%rax,%rcx)
        lea       (%rdx,%rdx,4), %rax
        shlq      $6, %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        lea       _P(%rip), %rdx
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      -960(%rax,%rdx)
        fmul      %st(1), %st
        fldt      -944(%rax,%rdx)
        fmul      %st(2), %st
        fldt      -928(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -912(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -896(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -880(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -864(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -848(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -832(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -816(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -800(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -784(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -768(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -752(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -736(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -720(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -704(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -688(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      -672(%rax,%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      -656(%rax,%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.20:
        fld       %st(0)
        lea       _P0(%rip), %rax
        fmul      %st(1), %st
        lea       16+_P0(%rip), %rdx
        fxch      %st(1)
        fld       %st(1)
        lea       32+_P0(%rip), %rcx
        fmul      %st(2), %st
        lea       48+_P0(%rip), %rsi
        lea       64+_P0(%rip), %rdi
        lea       80+_P0(%rip), %r8
        lea       96+_P0(%rip), %r9
        lea       112+_P0(%rip), %r10
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r9)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      (%r10)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value___y0l.38:
        call      __j0l@PLT
..___tag_value___y0l.39:
..B2.31:
        fldt      112(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.21:
        fxch      %st(1)
        fstpl     40(%rsp)
        movsd     40(%rsp), %xmm0
        fstpt     (%rsp)
        call      log@PLT
..B2.32:
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      16(%rsp)
        movsd     %xmm0, 32(%rsp)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fmull     32(%rsp)
        faddp     %st, %st(1)
        jmp       ..B2.27
..B2.22:
        lea       _ones(%rip), %rax
        lea       _val_36_0L(%rip), %rdx
        lea       _PP(%rip), %rcx
        lea       _QP(%rip), %rsi
        lea       16+_PP(%rip), %rdi
        lea       16+_QP(%rip), %r8
        lea       32+_PP(%rip), %r9
        lea       32+_QP(%rip), %r10
        fldl      (%rax)
        lea       48+_PP(%rip), %r11
        fdiv      %st(1), %st
        lea       48+_QP(%rip), %rax
        fxch      %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdx)
        lea       64+_PP(%rip), %rdx
        fmulp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rcx)
        lea       64+_QP(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       80+_PP(%rip), %rsi
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80+_QP(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       96+_PP(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       96+_QP(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       112+_PP(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       112+_QP(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_PP(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       128+_QP(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       144+_PP(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       144+_QP(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80(%rsp), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        movl      $-1, %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fstpt     -48(%rdi)
        fldt      (%rsi)
        lea       64(%rsp), %rsi
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -16(%rsi)
..___tag_value___y0l.42:
        call      __libm_sincos_pi4l@PLT
..___tag_value___y0l.43:
..B2.33:
        fldt      112(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.23:
        fldt      64(%rsp)
        lea       _val_0_03L(%rip), %rax
        fmulp     %st, %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        fadd      %st, %st(1)
        fld       %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fldt      (%rax)
        fmulp     %st, %st(1)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B2.25
        jp        ..B2.25
..B2.24:
        fstp      %st(1)
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fsqrt     
        fmulp     %st, %st(1)
        jmp       ..B2.27
..B2.25:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
        call      __ltoq@PLT
..B2.35:
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
        movaps    (%rsp), %xmm0
..___tag_value___y0l.47:
        call      __y0q@PLT
..___tag_value___y0l.48:
..B2.26:
        movaps    %xmm0, (%rsp)
        lea       (%rsp), %rdi
        call      __qtol@PLT
..B2.36:
        fstpl     40(%rsp)
        movsd     40(%rsp), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
..B2.27:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__y0l,@function
	.size	__y0l,.-__y0l
	.data
# -- End  __y0l
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 2
_S:
	.word	5921
	.word	65426
	.word	63142
	.word	35997
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	29632
	.word	15745
	.word	62068
	.word	44479
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	41288
	.word	2893
	.word	15731
	.word	35209
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	12322
	.word	41360
	.word	35270
	.word	48124
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	21828
	.word	62500
	.word	60486
	.word	61019
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	54460
	.word	63729
	.word	58186
	.word	36952
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	27250
	.word	3142
	.word	10825
	.word	43393
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	20649
	.word	49414
	.word	53633
	.word	49831
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	59160
	.word	26587
	.word	26484
	.word	56269
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	57283
	.word	10561
	.word	16432
	.word	62706
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	44243
	.word	51162
	.word	18495
	.word	34571
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	63781
	.word	54231
	.word	15770
	.word	37789
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_S,@object
	.size	_S,192
	.align 2
_val_0_6L:
	.word	39322
	.word	39321
	.word	39321
	.word	39321
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_val_0_6L,@object
	.size	_val_0_6L,16
	.align 2
_val_1_2L:
	.word	39322
	.word	39321
	.word	39321
	.word	39321
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_val_1_2L,@object
	.size	_val_1_2L,16
	.align 2
_Z:
	.word	20971
	.word	41151
	.word	30150
	.word	58561
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	56848
	.word	49844
	.word	39532
	.word	64842
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	5940
	.word	14600
	.word	60967
	.word	58048
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	48233
	.word	9194
	.word	47533
	.word	41870
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	19716
	.word	3898
	.word	3621
	.word	54727
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	41547
	.word	400
	.word	58272
	.word	33793
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	46312
	.word	65374
	.word	26362
	.word	40225
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	15253
	.word	48480
	.word	38891
	.word	46657
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12158
	.word	16394
	.word	14239
	.word	53090
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	31253
	.word	2914
	.word	8779
	.word	59523
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	29606
	.word	9567
	.word	8455
	.word	32978
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	51148
	.word	41491
	.word	50276
	.word	36194
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_Z,@object
	.size	_Z,192
	.align 2
_P11:
	.word	21672
	.word	14168
	.word	38767
	.word	33539
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	41169
	.word	50619
	.word	21849
	.word	37014
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	55003
	.word	42793
	.word	44721
	.word	50737
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	1169
	.word	13656
	.word	30890
	.word	57268
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	2761
	.word	22275
	.word	55697
	.word	58639
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	63624
	.word	9235
	.word	64995
	.word	56818
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	62376
	.word	14952
	.word	24232
	.word	50456
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	35764
	.word	14862
	.word	11423
	.word	44812
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	799
	.word	749
	.word	44421
	.word	44099
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	57800
	.word	45157
	.word	3532
	.word	40524
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	54824
	.word	35595
	.word	46140
	.word	46550
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	57927
	.word	14014
	.word	38764
	.word	61194
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	31025
	.word	58986
	.word	13056
	.word	57752
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	25304
	.word	41780
	.word	21387
	.word	41754
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	38881
	.word	54183
	.word	37931
	.word	34827
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	19650
	.word	12565
	.word	569
	.word	58514
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	23916
	.word	27820
	.word	49726
	.word	50434
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	37686
	.word	17141
	.word	5720
	.word	38948
	.word	16317
	.word	0
	.word	0
	.word	0
	.type	_P11,@object
	.size	_P11,288
	.align 2
_Q11:
	.word	14194
	.word	21345
	.word	52686
	.word	49437
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	16969
	.word	59638
	.word	1826
	.word	39310
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	28926
	.word	48282
	.word	1364
	.word	41291
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	13574
	.word	15497
	.word	63728
	.word	57349
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_Q11,@object
	.size	_Q11,64
	.align 2
_P12:
	.word	59330
	.word	22263
	.word	34178
	.word	51006
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	16861
	.word	21193
	.word	35273
	.word	46715
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	2103
	.word	12628
	.word	1838
	.word	41114
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	46148
	.word	53581
	.word	46203
	.word	46468
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	19710
	.word	1268
	.word	64842
	.word	38086
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	10276
	.word	59226
	.word	39596
	.word	48586
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	31029
	.word	64049
	.word	23137
	.word	50693
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	22014
	.word	1854
	.word	56980
	.word	45052
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	12740
	.word	38047
	.word	44756
	.word	35383
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	9689
	.word	42893
	.word	35756
	.word	50848
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	16570
	.word	20820
	.word	51546
	.word	34499
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	49962
	.word	36228
	.word	57995
	.word	45370
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	32489
	.word	35981
	.word	43312
	.word	58898
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	10311
	.word	12657
	.word	16405
	.word	38148
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	32504
	.word	29604
	.word	49577
	.word	49580
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	65322
	.word	54336
	.word	27403
	.word	64825
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	33227
	.word	51353
	.word	11288
	.word	42704
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	30209
	.word	39595
	.word	16790
	.word	56786
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	17645
	.word	4926
	.word	40100
	.word	38165
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	6983
	.word	2488
	.word	5292
	.word	51990
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	4652
	.word	21592
	.word	9570
	.word	36478
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	53361
	.word	14763
	.word	26464
	.word	52381
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	50485
	.word	35307
	.word	30503
	.word	59794
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	18614
	.word	21402
	.word	49824
	.word	37715
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	55547
	.word	61092
	.word	564
	.word	48870
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	9082
	.word	24716
	.word	52021
	.word	40706
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	17305
	.word	30912
	.word	47308
	.word	59790
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P12,@object
	.size	_P12,432
	.align 2
_val_3_0L:
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_val_3_0L,@object
	.size	_val_3_0L,16
	.align 2
_P20:
	.word	37654
	.word	4018
	.word	25149
	.word	64448
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	19189
	.word	12397
	.word	54733
	.word	33028
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	22349
	.word	41220
	.word	13172
	.word	45200
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	43891
	.word	61784
	.word	34756
	.word	53833
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	23592
	.word	62506
	.word	40671
	.word	50152
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	27771
	.word	49376
	.word	49653
	.word	53593
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	29892
	.word	65375
	.word	48818
	.word	47355
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	33664
	.word	20071
	.word	46988
	.word	35237
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	40179
	.word	47562
	.word	27113
	.word	46088
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	39977
	.word	33568
	.word	2856
	.word	39237
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	22462
	.word	11227
	.word	63692
	.word	48552
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	9895
	.word	49985
	.word	29445
	.word	35598
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	41034
	.word	13031
	.word	20740
	.word	35245
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	32927
	.word	48253
	.word	43
	.word	37376
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	5109
	.word	58801
	.word	31733
	.word	33042
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	34717
	.word	51477
	.word	15888
	.word	35209
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	4166
	.word	43043
	.word	47592
	.word	42555
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	20077
	.word	289
	.word	31753
	.word	49394
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_P20,@object
	.size	_P20,288
	.align 2
_val_3_875L:
	.word	0
	.word	0
	.word	0
	.word	63488
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_val_3_875L,@object
	.size	_val_3_875L,16
	.align 2
_P21:
	.word	27895
	.word	14185
	.word	11256
	.word	61473
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	55110
	.word	27090
	.word	21413
	.word	51858
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	47481
	.word	51958
	.word	45685
	.word	44708
	.word	49121
	.word	0
	.word	0
	.word	0
	.word	53606
	.word	43192
	.word	10601
	.word	36675
	.word	49120
	.word	0
	.word	0
	.word	0
	.word	9703
	.word	39697
	.word	3773
	.word	54687
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	39016
	.word	46259
	.word	57439
	.word	47927
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	18601
	.word	64800
	.word	43294
	.word	45654
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	11755
	.word	20846
	.word	12691
	.word	36847
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	34925
	.word	21969
	.word	12496
	.word	40232
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	33074
	.word	22131
	.word	45345
	.word	58077
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	28402
	.word	30562
	.word	17788
	.word	44258
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	64431
	.word	22682
	.word	21098
	.word	38133
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	38064
	.word	47115
	.word	9737
	.word	49752
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	32940
	.word	23551
	.word	17164
	.word	63584
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	20289
	.word	55054
	.word	36724
	.word	37823
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	46434
	.word	29308
	.word	2375
	.word	53705
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	16931
	.word	9593
	.word	51956
	.word	35227
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_P21,@object
	.size	_P21,272
	.align 2
_P22:
	.word	40244
	.word	7737
	.word	8636
	.word	43048
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	47822
	.word	28463
	.word	39991
	.word	46660
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	52293
	.word	15799
	.word	45377
	.word	51162
	.word	49113
	.word	0
	.word	0
	.word	0
	.word	37860
	.word	10494
	.word	27911
	.word	39108
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	1970
	.word	24596
	.word	57275
	.word	48105
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	64547
	.word	28877
	.word	41771
	.word	52711
	.word	16352
	.word	0
	.word	0
	.word	0
	.word	63459
	.word	4647
	.word	40018
	.word	64048
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	38817
	.word	50313
	.word	34514
	.word	41599
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	6873
	.word	59727
	.word	11575
	.word	48044
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	29515
	.word	62000
	.word	64058
	.word	40336
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	47818
	.word	19180
	.word	47066
	.word	42033
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	35527
	.word	6093
	.word	4081
	.word	54678
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	16787
	.word	50723
	.word	15144
	.word	52417
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	27442
	.word	31785
	.word	24103
	.word	36633
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	6233
	.word	52232
	.word	32482
	.word	57483
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	59606
	.word	43125
	.word	44255
	.word	61366
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	29998
	.word	6975
	.word	18760
	.word	53326
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	24786
	.word	2384
	.word	4789
	.word	52762
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	30902
	.word	22802
	.word	44066
	.word	49086
	.word	0
	.word	0
	.word	0
	.type	_P22,@object
	.size	_P22,304
	.align 2
_P3:
	.word	38373
	.word	25081
	.word	52784
	.word	41269
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	2433
	.word	58494
	.word	34093
	.word	49310
	.word	49089
	.word	0
	.word	0
	.word	0
	.word	20384
	.word	14386
	.word	32743
	.word	56565
	.word	16322
	.word	0
	.word	0
	.word	0
	.word	12279
	.word	3882
	.word	34299
	.word	37877
	.word	49096
	.word	0
	.word	0
	.word	0
	.word	57529
	.word	46240
	.word	60154
	.word	40126
	.word	16332
	.word	0
	.word	0
	.word	0
	.word	46223
	.word	16522
	.word	54101
	.word	39334
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	17152
	.word	52627
	.word	55415
	.word	33597
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	46957
	.word	15713
	.word	64890
	.word	47269
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	63416
	.word	7921
	.word	11783
	.word	63655
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	22182
	.word	52374
	.word	10257
	.word	64822
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	20533
	.word	18718
	.word	7837
	.word	41493
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	34870
	.word	43221
	.word	2667
	.word	34068
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	62446
	.word	64657
	.word	15033
	.word	39509
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	31017
	.word	38768
	.word	5018
	.word	46409
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	37858
	.word	10529
	.word	62079
	.word	49339
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	60794
	.word	51427
	.word	47345
	.word	37742
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	8999
	.word	61205
	.word	62511
	.word	36506
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	22683
	.word	43326
	.word	42314
	.word	55672
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	37536
	.word	24482
	.word	58150
	.word	50356
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	47735
	.word	57962
	.word	45199
	.word	44407
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	54502
	.word	19410
	.word	25859
	.word	39334
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	1861
	.word	57822
	.word	27975
	.word	42824
	.word	49086
	.word	0
	.word	0
	.word	0
	.type	_P3,@object
	.size	_P3,352
	.align 2
_P:
	.word	6710
	.word	46975
	.word	45541
	.word	45018
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	47994
	.word	32334
	.word	52627
	.word	65214
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	25829
	.word	961
	.word	45314
	.word	63623
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	56740
	.word	896
	.word	13408
	.word	40400
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	60385
	.word	50719
	.word	53670
	.word	35961
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	18363
	.word	40724
	.word	30788
	.word	37641
	.word	16343
	.word	0
	.word	0
	.word	0
	.word	54199
	.word	26560
	.word	1100
	.word	63052
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	31112
	.word	47384
	.word	24910
	.word	52460
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	62051
	.word	62630
	.word	7457
	.word	41285
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	41021
	.word	16553
	.word	53133
	.word	51701
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	43336
	.word	19494
	.word	20322
	.word	38261
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	44128
	.word	40851
	.word	40287
	.word	33282
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	15978
	.word	61692
	.word	60093
	.word	46462
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	34924
	.word	34591
	.word	27618
	.word	49090
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	22325
	.word	56457
	.word	51367
	.word	65297
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	21258
	.word	62600
	.word	63264
	.word	33170
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	31459
	.word	43701
	.word	15672
	.word	42803
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	10392
	.word	64190
	.word	8847
	.word	51227
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	34244
	.word	38351
	.word	44643
	.word	65457
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	24705
	.word	53181
	.word	1549
	.word	55232
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	24274
	.word	2157
	.word	59872
	.word	44641
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	44404
	.word	52032
	.word	31206
	.word	56185
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	42376
	.word	53700
	.word	44782
	.word	63474
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	55880
	.word	43379
	.word	3093
	.word	34064
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	62288
	.word	61979
	.word	24976
	.word	35576
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	30673
	.word	34148
	.word	2010
	.word	61956
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	45192
	.word	28065
	.word	60055
	.word	61722
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	24582
	.word	59932
	.word	774
	.word	41831
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	39741
	.word	15639
	.word	29541
	.word	39821
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	32965
	.word	53765
	.word	3652
	.word	39681
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	55274
	.word	5931
	.word	14729
	.word	36200
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	4659
	.word	41741
	.word	5124
	.word	48783
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	61896
	.word	13936
	.word	31722
	.word	42885
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	29587
	.word	13947
	.word	35938
	.word	34351
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	30548
	.word	42185
	.word	15217
	.word	58709
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	32944
	.word	45803
	.word	62008
	.word	44929
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	15543
	.word	17819
	.word	22981
	.word	37733
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	40787
	.word	12944
	.word	27687
	.word	34273
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	14474
	.word	51712
	.word	20794
	.word	57241
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	31505
	.word	44115
	.word	25843
	.word	56405
	.word	49084
	.word	0
	.word	0
	.word	0
	.word	29537
	.word	33343
	.word	23367
	.word	43948
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	15837
	.word	34931
	.word	8097
	.word	48214
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	1126
	.word	23018
	.word	59367
	.word	62418
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	50591
	.word	16203
	.word	45917
	.word	57446
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	6095
	.word	44294
	.word	16725
	.word	34699
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	2346
	.word	25530
	.word	14073
	.word	51139
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	48724
	.word	10078
	.word	7882
	.word	59578
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	3077
	.word	7734
	.word	24746
	.word	33688
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	52085
	.word	9505
	.word	49832
	.word	37954
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	41663
	.word	47247
	.word	238
	.word	62191
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	48361
	.word	39255
	.word	14492
	.word	33999
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	35816
	.word	34414
	.word	54424
	.word	37185
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	20077
	.word	189
	.word	48758
	.word	39656
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	35728
	.word	48114
	.word	41225
	.word	51144
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	10943
	.word	64308
	.word	2314
	.word	53541
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	32154
	.word	56468
	.word	40039
	.word	32925
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	4941
	.word	52917
	.word	35073
	.word	34082
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	61194
	.word	15048
	.word	42139
	.word	49938
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	19128
	.word	18289
	.word	6860
	.word	51502
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	49300
	.word	58708
	.word	59005
	.word	55071
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	26502
	.word	47642
	.word	36933
	.word	42964
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	30051
	.word	60756
	.word	30625
	.word	41460
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	61015
	.word	15762
	.word	48007
	.word	60846
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	21975
	.word	20753
	.word	22643
	.word	48677
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	54246
	.word	4624
	.word	38600
	.word	33572
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	59379
	.word	53723
	.word	32353
	.word	42607
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	40791
	.word	16038
	.word	57576
	.word	57139
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	25939
	.word	3603
	.word	54023
	.word	55128
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	7176
	.word	58381
	.word	25669
	.word	36047
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	23057
	.word	6107
	.word	22526
	.word	49962
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	19351
	.word	39386
	.word	35003
	.word	63923
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	45293
	.word	12882
	.word	9948
	.word	58748
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	20001
	.word	20829
	.word	53529
	.word	36918
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	21919
	.word	49831
	.word	37577
	.word	39856
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	47026
	.word	46471
	.word	55318
	.word	49443
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	61519
	.word	51459
	.word	6950
	.word	50870
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	1555
	.word	2971
	.word	23597
	.word	62610
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	45926
	.word	36681
	.word	39043
	.word	38451
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	64160
	.word	21923
	.word	31542
	.word	47202
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	36569
	.word	36429
	.word	41036
	.word	40257
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	63272
	.word	57206
	.word	4215
	.word	41813
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	38880
	.word	42175
	.word	14999
	.word	35837
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	42787
	.word	9806
	.word	63168
	.word	59020
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	31095
	.word	14929
	.word	48209
	.word	41580
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	36413
	.word	27679
	.word	45695
	.word	64718
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	24761
	.word	31380
	.word	27544
	.word	35930
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	11221
	.word	62995
	.word	50975
	.word	54692
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	29668
	.word	1933
	.word	17144
	.word	45888
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	30810
	.word	24149
	.word	32113
	.word	34254
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	34856
	.word	24408
	.word	21475
	.word	41075
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	33943
	.word	23810
	.word	62559
	.word	60314
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	3305
	.word	24934
	.word	37437
	.word	47776
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	54895
	.word	55211
	.word	32000
	.word	34613
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	35009
	.word	47065
	.word	21660
	.word	64281
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	9852
	.word	9156
	.word	55656
	.word	46121
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	36442
	.word	41210
	.word	20060
	.word	40802
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	21758
	.word	47322
	.word	2788
	.word	58210
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	52348
	.word	9004
	.word	18276
	.word	61559
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	54786
	.word	7147
	.word	27070
	.word	43826
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	34268
	.word	15768
	.word	2480
	.word	60089
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	1157
	.word	52048
	.word	20458
	.word	40588
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	51540
	.word	48806
	.word	47292
	.word	62380
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	19999
	.word	57758
	.word	46120
	.word	57112
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	21459
	.word	54484
	.word	23863
	.word	35854
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	28754
	.word	5362
	.word	55327
	.word	62303
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	45243
	.word	6687
	.word	17114
	.word	61365
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	32048
	.word	1009
	.word	39781
	.word	52368
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	64584
	.word	3308
	.word	26412
	.word	38818
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	23088
	.word	38547
	.word	22659
	.word	65247
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	14061
	.word	22418
	.word	61292
	.word	34447
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	57848
	.word	26921
	.word	61797
	.word	57155
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	1597
	.word	31414
	.word	38572
	.word	39774
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	30034
	.word	27108
	.word	16687
	.word	65316
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	32265
	.word	44536
	.word	9779
	.word	53216
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	550
	.word	16407
	.word	19715
	.word	43369
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	7132
	.word	1973
	.word	39654
	.word	33659
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	19090
	.word	42384
	.word	19608
	.word	54616
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	29916
	.word	8899
	.word	53582
	.word	50715
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	63987
	.word	34002
	.word	32751
	.word	41084
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	31930
	.word	50610
	.word	35321
	.word	40669
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	64556
	.word	32042
	.word	47688
	.word	39353
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	22761
	.word	33268
	.word	25802
	.word	54704
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	48596
	.word	35082
	.word	36228
	.word	55222
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	62834
	.word	26257
	.word	62649
	.word	62428
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	22216
	.word	57771
	.word	11637
	.word	59993
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	32841
	.word	24077
	.word	25618
	.word	53031
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	7134
	.word	15474
	.word	25724
	.word	50216
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	679
	.word	61058
	.word	8963
	.word	33314
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	30607
	.word	23707
	.word	50236
	.word	62315
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	57112
	.word	44811
	.word	22996
	.word	58760
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	7925
	.word	14145
	.word	19419
	.word	54389
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	15063
	.word	11101
	.word	53306
	.word	33747
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	17212
	.word	63840
	.word	49608
	.word	61962
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	64221
	.word	2367
	.word	56115
	.word	44976
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	25958
	.word	42476
	.word	21228
	.word	41045
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	39160
	.word	29939
	.word	20970
	.word	56757
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	34212
	.word	35958
	.word	8729
	.word	51611
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	47817
	.word	56326
	.word	46415
	.word	42719
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	14697
	.word	51278
	.word	14116
	.word	38800
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	42056
	.word	1202
	.word	6817
	.word	52251
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	9811
	.word	3897
	.word	54180
	.word	38147
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	22279
	.word	36400
	.word	23268
	.word	48339
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	3604
	.word	63501
	.word	23839
	.word	53404
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	1179
	.word	37183
	.word	50892
	.word	54850
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	59751
	.word	27404
	.word	14795
	.word	57826
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	14718
	.word	11779
	.word	34360
	.word	46330
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	59705
	.word	41619
	.word	32041
	.word	48245
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	23030
	.word	54615
	.word	27535
	.word	57905
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	39169
	.word	62610
	.word	58529
	.word	59687
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	51398
	.word	44311
	.word	12717
	.word	50833
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	27431
	.word	2954
	.word	22657
	.word	51954
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	58296
	.word	29685
	.word	27243
	.word	58169
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	51216
	.word	48206
	.word	42318
	.word	59054
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	56132
	.word	37000
	.word	55250
	.word	38652
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	33362
	.word	39562
	.word	49329
	.word	39051
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	45334
	.word	5673
	.word	58520
	.word	48690
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	35653
	.word	20625
	.word	33642
	.word	49050
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	58051
	.word	8993
	.word	6809
	.word	36624
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	52356
	.word	59873
	.word	62958
	.word	36858
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	32142
	.word	25405
	.word	33867
	.word	33307
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	49519
	.word	13151
	.word	56037
	.word	36991
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	44868
	.word	63207
	.word	24006
	.word	43028
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	8083
	.word	45145
	.word	39702
	.word	51683
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	45111
	.word	52725
	.word	38011
	.word	48603
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	60521
	.word	20244
	.word	23464
	.word	55814
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	20556
	.word	16261
	.word	38082
	.word	40872
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	25337
	.word	58099
	.word	56911
	.word	46447
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	37285
	.word	19168
	.word	56812
	.word	50878
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	15949
	.word	20065
	.word	40118
	.word	57328
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	40507
	.word	40248
	.word	14830
	.word	44509
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	5104
	.word	24529
	.word	29756
	.word	49797
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	46472
	.word	25780
	.word	5432
	.word	50788
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	15446
	.word	10547
	.word	15373
	.word	56506
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	3213
	.word	104
	.word	59551
	.word	33676
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	34009
	.word	54843
	.word	32265
	.word	37318
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	45472
	.word	58672
	.word	64591
	.word	42366
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	32200
	.word	21528
	.word	8481
	.word	46835
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	49716
	.word	3176
	.word	29068
	.word	63703
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	39156
	.word	24341
	.word	43754
	.word	35182
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	61730
	.word	30329
	.word	55010
	.word	38388
	.word	16320
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,2880
	.align 2
_P0:
	.word	46060
	.word	21479
	.word	19707
	.word	37102
	.word	16345
	.word	0
	.word	0
	.word	0
	.word	35676
	.word	11242
	.word	15120
	.word	54134
	.word	49120
	.word	0
	.word	0
	.word	0
	.word	34641
	.word	49003
	.word	21090
	.word	56946
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	36501
	.word	52298
	.word	48288
	.word	40780
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	2947
	.word	56486
	.word	859
	.word	36145
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	6106
	.word	41115
	.word	36319
	.word	33709
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	13230
	.word	6676
	.word	23048
	.word	46558
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	62532
	.word	24192
	.word	46286
	.word	38694
	.word	49147
	.word	0
	.word	0
	.word	0
	.type	_P0,@object
	.size	_P0,128
	.align 2
_tonpi:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_tonpi,@object
	.size	_tonpi,16
	.align 2
_val_36_0L:
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_val_36_0L,@object
	.size	_val_36_0L,16
	.align 2
_PP:
	.word	3555
	.word	4199
	.word	61716
	.word	36505
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	21640
	.word	56203
	.word	47336
	.word	57608
	.word	16322
	.word	0
	.word	0
	.word	0
	.word	3059
	.word	27554
	.word	42044
	.word	45622
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	55815
	.word	35656
	.word	54748
	.word	47307
	.word	16332
	.word	0
	.word	0
	.word	0
	.word	42025
	.word	6728
	.word	28939
	.word	34692
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	58943
	.word	11189
	.word	14960
	.word	38786
	.word	16344
	.word	0
	.word	0
	.word	0
	.word	43526
	.word	20005
	.word	29826
	.word	37014
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	58976
	.word	17109
	.word	36079
	.word	36708
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	58243
	.word	36408
	.word	14563
	.word	58254
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_PP,@object
	.size	_PP,160
	.align 2
_QP:
	.word	30466
	.word	14887
	.word	33885
	.word	35327
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	41139
	.word	65374
	.word	59488
	.word	50915
	.word	49088
	.word	0
	.word	0
	.word	0
	.word	35675
	.word	30946
	.word	22284
	.word	35505
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	65216
	.word	29458
	.word	42913
	.word	63176
	.word	49097
	.word	0
	.word	0
	.word	0
	.word	5130
	.word	24339
	.word	48915
	.word	38634
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	30040
	.word	27658
	.word	23448
	.word	34596
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	37816
	.word	17431
	.word	24401
	.word	49646
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	4377
	.word	35057
	.word	45578
	.word	33037
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	19863
	.word	65266
	.word	6202
	.word	53939
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	49142
	.word	0
	.word	0
	.word	0
	.type	_QP,@object
	.size	_QP,160
	.align 2
_val_0_03L:
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_val_0_03L,@object
	.size	_val_0_03L,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
