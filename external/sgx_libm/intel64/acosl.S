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
	.file "acosl.c"
	.text
..TXTST0:
# -- Begin  acosl
	.text
       .align    16,0x90
	.globl acosl
acosl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_acosl.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %cl, %cl
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movb      105(%rsp), %dl
        movzwl    104(%rsp), %edi
        andb      $-128, %dl
        andl      $32767, %edi
        shrb      $7, %dl
        movzwl    82(%rsp), %eax
        cmpl      $16383, %edi
        jge       ..B1.60
..B1.4:
        cmpl      $16382, %edi
        jge       ..B1.47
..B1.5:
        cmpl      $16376, %edi
        jge       ..B1.37
..B1.6:
        cmpl      $16371, %edi
        jge       ..B1.30
..B1.7:
        cmpl      $16365, %edi
        jge       ..B1.23
..B1.8:
        cmpl      $16308, %edi
        jge       ..B1.16
..B1.9:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.13
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.11:
        fldcw     80(%rsp)
..B1.12:
        movb      $1, %cl
..B1.13:
        fldt      96(%rsp)
        lea       16+_pi_02(%rip), %rax
        lea       _pi_02(%rip), %rdx
        testb     %cl, %cl
        fldt      (%rax)
        fsubp     %st, %st(1)
        fldt      (%rdx)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.15
..B1.14:
        fldcw     82(%rsp)
..B1.15:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.16:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.20
..B1.17:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.18:
        fldcw     80(%rsp)
..B1.19:
        movb      $1, %cl
..B1.20:
        fldt      96(%rsp)
        lea       _P4(%rip), %rax
        lea       16+_pi_02(%rip), %rdx
        lea       _pi_02(%rip), %rsi
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldt      (%rdx)
        fsubp     %st, %st(1)
        fldt      (%rsi)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.22
..B1.21:
        fldcw     82(%rsp)
..B1.22:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.23:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.27
..B1.24:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.25:
        fldcw     80(%rsp)
..B1.26:
        movb      $1, %cl
..B1.27:
        fldt      96(%rsp)
        lea       16+_P3(%rip), %rax
        fld       %st(0)
        lea       _P3(%rip), %rdx
        fmul      %st(1), %st
        lea       16+_pi_02(%rip), %rsi
        fldt      (%rax)
        lea       _pi_02(%rip), %rdi
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldt      (%rsi)
        fsubp     %st, %st(1)
        fldt      (%rdi)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.29
..B1.28:
        fldcw     82(%rsp)
..B1.29:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.30:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.34
..B1.31:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.32:
        fldcw     80(%rsp)
..B1.33:
        movb      $1, %cl
..B1.34:
        fldt      96(%rsp)
        lea       _TWO_32H(%rip), %rax
        fld       %st(0)
        lea       48+_P2(%rip), %rdx
        fmul      %st(1), %st
        lea       16+_P2(%rip), %rsi
        fld       %st(0)
        lea       32+_P2(%rip), %rdi
        fmul      %st(1), %st
        fld       %st(2)
        lea       _P2(%rip), %r8
        fld       %st(3)
        fldl      (%rax)
        lea       16+_pi_02(%rip), %r9
        fmul      %st(5), %st
        lea       _pi_02(%rip), %r10
        testb     %cl, %cl
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fsub      %st(1), %st
        fldt      (%rdx)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        fmulp     %st, %st(4)
        fldt      (%r8)
        faddp     %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%r9)
        fsubp     %st, %st(1)
        fldt      (%r10)
        fsubp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.36
..B1.35:
        fldcw     82(%rsp)
..B1.36:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.37:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.41
..B1.38:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.39:
        fldcw     80(%rsp)
..B1.40:
        movzwl    104(%rsp), %edi
        movb      $1, %cl
        andl      $32767, %edi
..B1.41:
        fldt      96(%rsp)
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        cmpl      $16381, %edi
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldl      (%rax)
        fmul      %st, %st(2)
        fstpt     32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fld       %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fxch      %st(3)
        fstpt     16(%rsp)
        jge       ..B1.43
..B1.42:
        fstp      %st(2)
        lea       176+_P1(%rip), %rax
        lea       144+_P1(%rip), %rdx
        lea       112+_P1(%rip), %rsi
        lea       80+_P1(%rip), %rdi
        lea       160+_P1(%rip), %r10
        lea       48+_P1(%rip), %r8
        lea       128+_P1(%rip), %r11
        lea       16+_P1(%rip), %r9
        fldt      (%rax)
        lea       96+_P1(%rip), %rax
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       64+_P1(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       32+_P1(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       _P1(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       192+_P1(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(3)
        fldt      64(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fldt      (%r8)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.44
..B1.43:
        lea       272+_P(%rip), %rax
        lea       240+_P(%rip), %rdx
        lea       208+_P(%rip), %rsi
        lea       176+_P(%rip), %rdi
        lea       144+_P(%rip), %r8
        lea       112+_P(%rip), %r9
        lea       80+_P(%rip), %r10
        lea       48+_P(%rip), %r11
        fldt      (%rax)
        lea       16+_P(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       256+_P(%rip), %rdx
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
        fldt      (%rdx)
        lea       _P(%rip), %rdx
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
        fldt      (%rdx)
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
        fmulp     %st, %st(4)
        fldt      (%rdi)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmul      %st(3), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
..B1.44:
        fldt      (%rsp)
        lea       _pi_02(%rip), %rax
        fmulp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        lea       16+_pi_02(%rip), %rdx
        testb     %cl, %cl
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldt      (%rax)
        fld       %st(0)
        fsubr     %st(3), %st
        fchs      
        fsub      %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      (%rdx)
        fsubp     %st, %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.46
..B1.45:
        fldcw     82(%rsp)
..B1.46:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.47:
        movl      %eax, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.51
..B1.48:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.49:
        fldcw     80(%rsp)
..B1.50:
        movb      $1, %cl
..B1.51:
        fldt      96(%rsp)
        lea       _ones(%rip), %rsi
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_32H(%rip), %rdi
        movzbl    %dl, %eax
        lea       _TWO_52H(%rip), %r8
        fldl      (%rdi)
        fxch      %st(2)
        fmull     (%rsi,%rax,8)
        fldl      (%r8)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(1)
        fsubrl    (%rsi)
        fmul      %st(2), %st
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fxch      %st(1)
        fsub      %st(4), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fsubr     %st, %st(5)
        fld       %st(3)
        fxch      %st(2)
        fsub      %st(4), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(5)
        fld       %st(2)
        fadd      %st(1), %st
        fdivrp    %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(5), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fld       %st(1)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(1)
        fstpt     (%rsp)
        fld       %st(4)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B1.53
..B1.52:
        fstp      %st(1)
        lea       176+_P1(%rip), %rax
        lea       144+_P1(%rip), %rsi
        lea       112+_P1(%rip), %rdi
        lea       80+_P1(%rip), %r8
        lea       160+_P1(%rip), %r11
        lea       48+_P1(%rip), %r9
        lea       16+_P1(%rip), %r10
        fldt      (%rax)
        lea       128+_P1(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       96+_P1(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       64+_P1(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       32+_P1(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        lea       _P1(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        lea       192+_P1(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r9)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fldt      (%r10)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(4)
        fxch      %st(3)
        jmp       ..B1.54
..B1.53:
        lea       272+_P(%rip), %rax
        lea       240+_P(%rip), %rsi
        lea       208+_P(%rip), %rdi
        lea       176+_P(%rip), %r8
        lea       144+_P(%rip), %r9
        lea       112+_P(%rip), %r10
        lea       80+_P(%rip), %r11
        fldt      (%rax)
        lea       48+_P(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       16+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       256+_P(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       224+_P(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        lea       192+_P(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        lea       160+_P(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        lea       128+_P(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       96+_P(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       64+_P(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       32+_P(%rip), %rdi
        fmul      %st(2), %st
        fldt      (%r8)
        lea       _P(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       304+_P(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       288+_P(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r8)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fldt      (%rsp)
        fldt      (%r9)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r10)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(0)
        fldt      16(%rsp)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
..B1.54:
        fldt      64(%rsp)
        testb     %dl, %dl
        fmulp     %st, %st(4)
        fld       %st(2)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        je        ..B1.56
..B1.55:
        fstp      %st(3)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.2(%rip)
        lea       _pi_00(%rip), %rax
        lea       16+_pi_00(%rip), %rdx
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.57
..B1.56:
        fldt      .L_2il0floatpacket.3(%rip)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.57:
        testb     %cl, %cl
        je        ..B1.59
..B1.58:
        fldcw     82(%rsp)
..B1.59:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.60:
        movl      %eax, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.64
..B1.61:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.62:
        fldcw     80(%rsp)
..B1.63:
        movzwl    104(%rsp), %edi
        movb      $1, %cl
        andl      $32767, %edi
..B1.64:
        cmpl      $32767, %edi
        jne       ..B1.68
..B1.65:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.67
..B1.66:
        cmpl      $0, 96(%rsp)
        je        ..B1.74
..B1.67:
        fldt      96(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.75
..B1.68:
        cmpl      $16383, %edi
        jne       ..B1.74
..B1.69:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.74
..B1.70:
        cmpl      $0, 96(%rsp)
        jne       ..B1.74
..B1.71:
        testb     %dl, %dl
        je        ..B1.73
..B1.72:
        lea       _pi_0l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rax)
        fldt      (%rdx)
        fsubrp    %st, %st(1)
        fstpt     8(%rsp)
        jmp       ..B1.75
..B1.73:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        fstpt     8(%rsp)
        jmp       ..B1.75
..B1.74:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     8(%rsp)
..B1.75:
        testb     %cl, %cl
        je        ..B1.77
..B1.76:
        fldcw     82(%rsp)
..B1.77:
        fldt      8(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	acosl,@function
	.size	acosl,.-acosl
	.data
# -- End  acosl
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
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
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
_pi_00:
	.word	0
	.word	0
	.word	55970
	.word	51471
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	35374
	.word	4889
	.word	2259
	.word	34211
	.word	16350
	.word	0
	.word	0
	.word	0
	.type	_pi_00,@object
	.size	_pi_00,32
	.align 2
_pi_0l:
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
	.type	_pi_0l,@object
	.size	_pi_0l,32
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
