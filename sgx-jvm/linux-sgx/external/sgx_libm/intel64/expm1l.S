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
	.file "expm1l.c"
	.text
..TXTST0:
# -- Begin  expm1l
	.text
       .align    16,0x90
	.globl expm1l
expm1l:
# parameter 1: 160 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_expm1l.1:
..L2:

        subq      $152, %rsp
	.cfi_def_cfa_offset 160
        xorb      %dil, %dil
..B1.2:
        fnstcw    146(%rsp)
..B1.3:
        movb      169(%rsp), %cl
        lea       _ranges(%rip), %r8
        andb      $-128, %cl
        shrb      $7, %cl
        movzbl    %cl, %esi
        movq      %rsi, %rdx
        shlq      $4, %rdx
        movzwl    168(%rsp), %ecx
        andl      $32767, %ecx
        movzwl    8(%rdx,%r8), %eax
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jl        ..B1.8
..B1.4:
        jne       ..B1.63
..B1.5:
        movl      164(%rsp), %eax
        cmpl      4(%rdx,%r8), %eax
        jb        ..B1.8
..B1.6:
        jne       ..B1.63
..B1.7:
        movl      160(%rsp), %eax
        cmpl      (%rdx,%r8), %eax
        jae       ..B1.63
..B1.8:
        cmpl      $16381, %ecx
        jge       ..B1.49
..B1.9:
        movzwl    146(%rsp), %edx
        cmpl      $16370, %ecx
        jge       ..B1.42
..B1.10:
        cmpl      $16359, %ecx
        jge       ..B1.35
..B1.11:
        cmpl      $16345, %ecx
        jge       ..B1.28
..B1.12:
        cmpl      $16308, %ecx
        jge       ..B1.22
..B1.13:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.17
..B1.14:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.15:
        fldcw     144(%rsp)
..B1.16:
        movzwl    168(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.17:
        testl     %ecx, %ecx
        jle       ..B1.73
..B1.18:
        fldt      160(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       ones(%rip), %rdx
        lea       8+_TWO_75(%rip), %rcx
        fldl      (%rax)
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     (%rdx,%rsi,8)
        faddp     %st, %st(1)
        fmull     (%rcx)
        fstpt     16(%rsp)
..B1.19:
        testb     %dil, %dil
        je        ..B1.21
..B1.20:
        fldcw     146(%rsp)
..B1.21:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.22:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.77
..B1.23:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.24:
        fldcw     144(%rsp)
..B1.25:
        fldt      160(%rsp)
        lea       _Q3(%rip), %rax
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.26:
        fldcw     146(%rsp)
..B1.27:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.28:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.32
..B1.29:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.30:
        fldcw     144(%rsp)
..B1.31:
        movb      $1, %dil
..B1.32:
        fldt      160(%rsp)
        lea       16+_Q2(%rip), %rax
        lea       _Q2(%rip), %rdx
        testb     %dil, %dil
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fldt      (%rdx)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.34
..B1.33:
        fldcw     146(%rsp)
..B1.34:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.35:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.39
..B1.36:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.37:
        fldcw     144(%rsp)
..B1.38:
        movb      $1, %dil
..B1.39:
        fldt      160(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rcx
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rdx
        fldt      (%rax)
        lea       _Q1(%rip), %rsi
        fmul      %st(1), %st
        testb     %dil, %dil
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmulp     %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.41
..B1.40:
        fldcw     146(%rsp)
..B1.41:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.42:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.46
..B1.43:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.44:
        fldcw     144(%rsp)
..B1.45:
        movb      $1, %dil
..B1.46:
        fldt      160(%rsp)
        lea       _TWO_54H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       208+_Q(%rip), %r8
        lea       240+_Q(%rip), %rcx
        lea       176+_Q(%rip), %r9
        fld       %st(3)
        lea       224+_Q(%rip), %rsi
        fldl      (%rax)
        lea       144+_Q(%rip), %r10
        fmul      %st(5), %st
        lea       112+_Q(%rip), %r11
        lea       80+_Q(%rip), %rax
        lea       256+_Q(%rip), %rdx
        testb     %dil, %dil
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fld       %st(4)
        fmul      %st(5), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fxch      %st(4)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(4), %st
        fxch      %st(5)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fstpt     96(%rsp)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      (%rdx)
        lea       48+_Q(%rip), %rdx
        fmul      %st, %st(2)
        fldt      (%rcx)
        lea       16+_Q(%rip), %rcx
        fstpt     112(%rsp)
        fldt      (%rsi)
        lea       192+_Q(%rip), %rsi
        fstpt     128(%rsp)
        fldt      (%r8)
        lea       160+_Q(%rip), %r8
        fmul      %st(4), %st
        fldt      (%r9)
        lea       128+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        lea       96+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r11)
        lea       64+_Q(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rax)
        lea       32+_Q(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rdx)
        lea       _Q(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rsi)
        fmul      %st(5), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      (%rdx)
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      112(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      128(%rsp)
        fld       %st(0)
        fldt      64(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        fldt      96(%rsp)
        fmulp     %st, %st(4)
        fldt      80(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      48(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        je        ..B1.48
..B1.47:
        fldt      16(%rsp)
        fstpt     48(%rsp)
..B1.85:
        fldcw     146(%rsp)
..B1.48:
        fldt      32(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.49:
        movzwl    146(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.53
..B1.50:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.51:
        fldcw     144(%rsp)
..B1.52:
        movzwl    168(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.53:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rax
        fstpt     32(%rsp)
        lea       _TWO_32H(%rip), %r8
        fldt      160(%rsp)
        cmpl      $16396, %ecx
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        fldl      (%rax)
        movzwl    8+.L_2il0floatpacket.0(%rip), %r9d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(1), %st
        movl      48(%rsp), %eax
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.3(%rip)
        fmulp     %st, %st(2)
        movsbq    %al, %rdx
        fsub      %st(1), %st
        fldl      (%r8)
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        jge       ..B1.60
..B1.54:
        fld       %st(1)
        subl      %edx, %eax
        shrl      $8, %eax
        andl      $-32768, %r9d
        addl      $16383, %eax
        lea       32+_P(%rip), %r8
        andl      $32767, %eax
        lea       48+_P(%rip), %r10
        orl       %eax, %r9d
        lea       64+_P(%rip), %rax
        movw      %r9w, 40(%rsp)
        lea       _P(%rip), %r9
        lea       16+_P(%rip), %r11
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%r8)
        shlq      $4, %rdx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        testq     %rsi, %rsi
        fldt      (%r9)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      2048(%rax,%rdx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmull     2056(%rax,%rdx)
        faddp     %st, %st(3)
        fldt      32(%rsp)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddl     2056(%rax,%rdx)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        je        ..B1.56
..B1.55:
        fldt      .L_2il0floatpacket.4(%rip)
        fadd      %st(3), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        faddp     %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        jmp       ..B1.57
..B1.56:
        movzbl    169(%rsp), %edx
        xorl      %esi, %esi
        shrl      $7, %edx
        lea       zero_one(%rip), %r8
        shll      $15, %edx
        orl       %ecx, %edx
        shll      $16, %edx
        movzwl    166(%rsp), %eax
        orl       %eax, %edx
        cmpl      $1074049228, %edx
        setb      %sil
        fldl      (%r8,%rsi,8)
        xorl      $1, %esi
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     16(%rsp)
        fsubl     (%r8,%rsi,8)
..B1.57:
        fldt      16(%rsp)
        testb     %dil, %dil
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.88
..B1.58:
        fstpt     48(%rsp)
..B1.86:
        fldcw     146(%rsp)
        jmp       ..B1.59
..B1.88:
        fstp      %st(0)
..B1.59:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.60:
        fld       %st(1)
        subl      %edx, %eax
        shrl      $8, %eax
        lea       32+_P(%rip), %rsi
        addl      $8191, %eax
        lea       _P(%rip), %r8
        movzwl    40(%rsp), %ecx
        andl      $32767, %eax
        andl      $-32768, %ecx
        lea       48+_P(%rip), %r9
        orl       %eax, %ecx
        lea       64+_P(%rip), %rax
        lea       16+_P(%rip), %r10
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       _SC2(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rsi)
        shlq      $4, %rdx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        testb     %dil, %dil
        fldt      (%r8)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        fmul      %st(2), %st
        fldt      (%r10)
        movw      %cx, 40(%rsp)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      2056(%r11,%rdx)
        fldl      2048(%r11,%rdx)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(4)
        fadd      %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.89
..B1.61:
        fstpt     48(%rsp)
..B1.87:
        fldcw     146(%rsp)
        jmp       ..B1.62
..B1.89:
        fstp      %st(0)
..B1.62:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.63:
        movzwl    146(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.67
..B1.64:
        orl       $-64768, %edx
        movw      %dx, 144(%rsp)
..B1.65:
        fldcw     144(%rsp)
..B1.66:
        movzwl    168(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.67:
        cmpl      $32767, %ecx
        je        ..B1.79
..B1.68:
        testq     %rsi, %rsi
        je        ..B1.78
..B1.69:
        fldt      .L_2il0floatpacket.4(%rip)
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.70:
        testb     %dil, %dil
        je        ..B1.72
..B1.71:
        fldcw     146(%rsp)
..B1.72:
        fldt      16(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
..B1.73:
        cmpl      $0, 164(%rsp)
        jne       ..B1.75
..B1.74:
        cmpl      $0, 160(%rsp)
        je        ..B1.76
..B1.75:
        fldt      160(%rsp)
        lea       _TWO_75(%rip), %rdx
        lea       ones(%rip), %rcx
        lea       _small_value_80(%rip), %rax
        fldl      (%rdx)
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     (%rcx,%rsi,8)
        lea       8+_TWO_75(%rip), %rsi
        faddp     %st, %st(1)
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        fmull     (%rsi)
        fstpt     16(%rsp)
        jmp       ..B1.19
..B1.76:
        fldt      160(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.19
..B1.77:
        fldt      160(%rsp)
        lea       _Q3(%rip), %rax
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.27
..B1.78:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        jmp       ..B1.70
..B1.79:
        cmpl      $-2147483648, 164(%rsp)
        jne       ..B1.82
..B1.80:
        cmpl      $0, 160(%rsp)
        jne       ..B1.82
..B1.81:
        lea       _inf_none(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     16(%rsp)
        jmp       ..B1.70
..B1.82:
        fldt      160(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.70
        .align    16,0x90
	.cfi_endproc
	.type	expm1l,@function
	.size	expm1l,.-expm1l
	.data
# -- End  expm1l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
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
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
zero_one:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x3ff00000
	.type	zero_one,@object
	.size	zero_one,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_54H:
	.long	0
	.long	1129840640
	.type	_TWO_54H,@object
	.size	_TWO_54H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_inf_none:
	.long	0
	.long	2146435072
	.long	0
	.long	3220176896
	.type	_inf_none,@object
	.size	_inf_none,16
	.align 2
_ranges:
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16389
	.word	0
	.word	0
	.word	0
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_Q3:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	2731
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	46967
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	65530
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	43687
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	52365
	.word	52428
	.word	43692
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	56491
	.word	10318
	.word	34954
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	39683
	.word	36691
	.word	42743
	.word	55145
	.word	16297
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	46686
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	53372
	.word	3328
	.word	208
	.word	53261
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	5395
	.word	3321
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	7401
	.word	46642
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	52768
	.word	7506
	.word	32188
	.word	37874
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	23003
	.word	12485
	.word	11072
	.word	55090
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	3702
	.word	18929
	.word	50035
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	2630
	.word	23096
	.word	11140
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	46660
	.word	62794
	.word	49270
	.word	51706
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	61237
	.word	7998
	.word	19059
	.word	55154
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43648
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43648
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,272
	.align 2
_P:
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
	.type	_P,@object
	.size	_P,80
	.align 2
_SC2:
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
	.word	8191
	.word	0
	.word	0
	.word	0
	.type	_SC2,@object
	.size	_SC2,32
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
