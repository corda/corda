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
	.file "asinl.c"
	.text
..TXTST0:
# -- Begin  asinl
	.text
       .align    16,0x90
	.globl asinl
asinl:
# parameter 1: 112 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_asinl.1:
..L2:

        subq      $104, %rsp
	.cfi_def_cfa_offset 112
        xorb      %dl, %dl
..B1.2:
        fnstcw    90(%rsp)
..B1.3:
        movb      121(%rsp), %dil
        movzwl    120(%rsp), %esi
        andb      $-128, %dil
        andl      $32767, %esi
        shrb      $7, %dil
        movzwl    90(%rsp), %eax
        cmpl      $16383, %esi
        jge       ..B1.59
..B1.4:
        cmpl      $16382, %esi
        jge       ..B1.49
..B1.5:
        cmpl      $16376, %esi
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %esi
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %esi
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %esi
        jge       ..B1.18
..B1.9:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.13
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.11:
        fldcw     88(%rsp)
..B1.12:
        movzwl    120(%rsp), %esi
        movb      $1, %dl
        andl      $32767, %esi
..B1.13:
        testl     %esi, %esi
        jle       ..B1.75
..B1.14:
        fldt      112(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rcx
        fldl      (%rax)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     (%rcx)
        fstpt     (%rsp)
..B1.15:
        testb     %dl, %dl
        je        ..B1.17
..B1.16:
        fldcw     90(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.18:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.22
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.20:
        fldcw     88(%rsp)
..B1.21:
        movb      $1, %dl
..B1.22:
        fldt      112(%rsp)
        lea       _P4(%rip), %rax
        testb     %dl, %dl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.24
..B1.23:
        fldcw     90(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.25:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.29
..B1.26:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.27:
        fldcw     88(%rsp)
..B1.28:
        movb      $1, %dl
..B1.29:
        fldt      112(%rsp)
        lea       16+_P3(%rip), %rax
        fld       %st(0)
        lea       _P3(%rip), %rcx
        fmul      %st(1), %st
        testb     %dl, %dl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.31
..B1.30:
        fldcw     90(%rsp)
..B1.31:
        fldt      (%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.32:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.36
..B1.33:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.34:
        fldcw     88(%rsp)
..B1.35:
        movb      $1, %dl
..B1.36:
        fldt      112(%rsp)
        lea       48+_P2(%rip), %rax
        fld       %st(0)
        lea       32+_P2(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_P2(%rip), %rcx
        fld       %st(0)
        lea       _P2(%rip), %rdi
        fmul      %st(1), %st
        testb     %dl, %dl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.38
..B1.37:
        fldcw     90(%rsp)
..B1.38:
        fldt      (%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.39:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.43
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.41:
        fldcw     88(%rsp)
..B1.42:
        movzwl    120(%rsp), %esi
        movb      $1, %dl
        andl      $32767, %esi
..B1.43:
        fldt      112(%rsp)
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        cmpl      $16381, %esi
        fldl      (%rax)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fld       %st(1)
        fsubr     %st(3), %st
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        faddp     %st, %st(2)
        jge       ..B1.45
..B1.44:
        fstp      %st(0)
        lea       176+_P1(%rip), %rax
        lea       144+_P1(%rip), %rcx
        lea       112+_P1(%rip), %rsi
        lea       80+_P1(%rip), %rdi
        lea       160+_P1(%rip), %r10
        lea       48+_P1(%rip), %r8
        lea       128+_P1(%rip), %r11
        lea       16+_P1(%rip), %r9
        fldt      (%rax)
        lea       96+_P1(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       64+_P1(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       32+_P1(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       _P1(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       192+_P1(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fldt      (%r8)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        jmp       ..B1.46
..B1.45:
        lea       272+_P(%rip), %rax
        lea       240+_P(%rip), %rcx
        lea       208+_P(%rip), %rsi
        lea       176+_P(%rip), %rdi
        lea       144+_P(%rip), %r8
        lea       112+_P(%rip), %r9
        lea       80+_P(%rip), %r10
        lea       48+_P(%rip), %r11
        fldt      (%rax)
        lea       16+_P(%rip), %rax
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       256+_P(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       224+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       192+_P(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       160+_P(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       128+_P(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       96+_P(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       64+_P(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       32+_P(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       _P(%rip), %rcx
        fmul      %st(3), %st
        fldt      (%rsi)
        lea       304+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        lea       288+_P(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%rcx)
        faddp     %st, %st(3)
        fldt      48(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        fldt      (%rsp)
        fldt      (%rsi)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st(1), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fsub      %st(3), %st
        faddp     %st, %st(1)
..B1.46:
        fldt      64(%rsp)
        fld       %st(2)
        fmul      %st(4), %st
        testb     %dl, %dl
        fxch      %st(5)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        je        ..B1.48
..B1.47:
        fldcw     90(%rsp)
..B1.48:
        fldt      64(%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.49:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.53
..B1.50:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.51:
        fldcw     88(%rsp)
..B1.52:
        movb      $1, %dl
..B1.53:
        fldt      112(%rsp)
        lea       _ones(%rip), %rcx
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_32H(%rip), %rsi
        movzbl    %dil, %eax
        lea       _TWO_52H(%rip), %rdi
        fldl      (%rsi)
        fldl      (%rcx,%rax,8)
        fmul      %st, %st(3)
        fstpl     80(%rsp)
        fxch      %st(2)
        fsubrl    (%rcx)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fxch      %st(1)
        fsub      %st(3), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fldl      (%rdi)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fsubr     %st, %st(5)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fsubrp    %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(5)
        fld       %st(0)
        fadd      %st(2), %st
        fdivrp    %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fld       %st(4)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B1.55
..B1.54:
        fstp      %st(1)
        lea       176+_P1(%rip), %rax
        lea       144+_P1(%rip), %rcx
        lea       112+_P1(%rip), %rsi
        lea       80+_P1(%rip), %rdi
        lea       160+_P1(%rip), %r10
        lea       48+_P1(%rip), %r8
        lea       128+_P1(%rip), %r11
        lea       16+_P1(%rip), %r9
        fldt      (%rax)
        lea       96+_P1(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       64+_P1(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       32+_P1(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       _P1(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       192+_P1(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        fldt      (%r8)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.56
..B1.55:
        lea       272+_P(%rip), %rax
        lea       240+_P(%rip), %rcx
        lea       208+_P(%rip), %rsi
        lea       176+_P(%rip), %rdi
        lea       144+_P(%rip), %r8
        lea       112+_P(%rip), %r9
        lea       80+_P(%rip), %r10
        lea       48+_P(%rip), %r11
        fldt      (%rax)
        lea       16+_P(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       256+_P(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       224+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       192+_P(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       160+_P(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        lea       128+_P(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        lea       96+_P(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        lea       64+_P(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       32+_P(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       _P(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       304+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       288+_P(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fldt      16(%rsp)
        fldt      (%rsi)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fldt      32(%rsp)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
..B1.56:
        fldt      (%rsp)
        lea       _pi_02(%rip), %rax
        fmulp     %st, %st(2)
        fld       %st(2)
        fmul      %st(1), %st
        lea       16+_pi_02(%rip), %rcx
        testb     %dl, %dl
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     80(%rsp)
        fstpt     16(%rsp)
        je        ..B1.58
..B1.57:
        fldcw     90(%rsp)
..B1.58:
        fldt      16(%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.59:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.63
..B1.60:
        orl       $-64768, %eax
        movw      %ax, 88(%rsp)
..B1.61:
        fldcw     88(%rsp)
..B1.62:
        movzwl    120(%rsp), %esi
        movb      $1, %dl
        andl      $32767, %esi
..B1.63:
        cmpl      $32767, %esi
        jne       ..B1.67
..B1.64:
        cmpl      $-2147483648, 116(%rsp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 112(%rsp)
        je        ..B1.71
..B1.66:
        fldt      112(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.72
..B1.67:
        cmpl      $16383, %esi
        jne       ..B1.71
..B1.68:
        cmpl      $-2147483648, 116(%rsp)
        jne       ..B1.71
..B1.69:
        cmpl      $0, 112(%rsp)
        jne       ..B1.71
..B1.70:
        movzbl    %dil, %ecx
        lea       _pi_2l(%rip), %rax
        shlq      $4, %rcx
        lea       _small_value_80(%rip), %rsi
        fldt      (%rcx,%rax)
        fldt      (%rcx,%rsi)
        faddp     %st, %st(1)
        fstpt     8(%rsp)
        jmp       ..B1.72
..B1.71:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     8(%rsp)
..B1.72:
        testb     %dl, %dl
        je        ..B1.74
..B1.73:
        fldcw     90(%rsp)
..B1.74:
        fldt      8(%rsp)
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B1.75:
        cmpl      $0, 116(%rsp)
        jne       ..B1.77
..B1.76:
        cmpl      $0, 112(%rsp)
        je        ..B1.78
..B1.77:
        fldt      112(%rsp)
        lea       _small_value_80(%rip), %rcx
        movzbl    %dil, %eax
        shlq      $4, %rax
        fldt      (%rcx)
        fldt      (%rcx,%rax)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.78:
        fldt      112(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.15
        .align    16,0x90
	.cfi_endproc
	.type	asinl,@function
	.size	asinl,.-asinl
	.data
# -- End  asinl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
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
_P4:
	.word	43691
	.word	44938
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P4,@object
	.size	_P4,16
	.align 2
_P3:
	.word	36763
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	40704
	.word	58046
	.word	39460
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_P3,@object
	.size	_P3,32
	.align 2
_P2:
	.word	43688
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	20771
	.word	39331
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	23807
	.word	53798
	.word	28073
	.word	46811
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	35198
	.word	7516
	.word	54204
	.word	63722
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_P2,@object
	.size	_P2,64
	.align 2
_P1:
	.word	43589
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	39364
	.word	39321
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	17602
	.word	56173
	.word	28086
	.word	46811
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	47501
	.word	58414
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	27078
	.word	6583
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	39781
	.word	21053
	.word	25224
	.word	36391
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	46568
	.word	6235
	.word	50749
	.word	58572
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	41318
	.word	51558
	.word	37628
	.word	48452
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	55026
	.word	7893
	.word	43866
	.word	40925
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	43387
	.word	19951
	.word	2911
	.word	35445
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	32572
	.word	59281
	.word	11243
	.word	56666
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	49800
	.word	50337
	.word	42707
	.word	39184
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,208
	.align 2
_P:
	.word	42691
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	4317
	.word	52400
	.word	52428
	.word	52428
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	26206
	.word	56172
	.word	28086
	.word	46811
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	36130
	.word	58514
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	21592
	.word	8113
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	3093
	.word	49950
	.word	25215
	.word	36391
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	21499
	.word	24428
	.word	51889
	.word	58572
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	54828
	.word	41264
	.word	61120
	.word	48451
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	10243
	.word	1489
	.word	39717
	.word	40940
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	51433
	.word	46039
	.word	63564
	.word	35220
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	60402
	.word	39286
	.word	25675
	.word	60826
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	34219
	.word	36833
	.word	44839
	.word	57547
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	13013
	.word	26091
	.word	19721
	.word	60832
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	22353
	.word	41544
	.word	55443
	.word	55735
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	14948
	.word	7038
	.word	38091
	.word	38339
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	61727
	.word	61480
	.word	15906
	.word	51927
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	13386
	.word	23084
	.word	64443
	.word	55906
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	17499
	.word	23573
	.word	43947
	.word	42761
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39328
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,320
	.align 2
_pi_02:
	.word	0
	.word	0
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	35374
	.word	4889
	.word	2259
	.word	34211
	.word	16349
	.word	0
	.word	0
	.word	0
	.type	_pi_02,@object
	.size	_pi_02,32
	.align 2
_pi_2l:
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
	.type	_pi_2l,@object
	.size	_pi_2l,32
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
