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
	.file "asinhl.c"
	.text
..TXTST0:
# -- Begin  asinhl
	.text
       .align    16,0x90
	.globl asinhl
asinhl:
# parameter 1: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_asinhl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %cl, %cl
..B1.2:
        fnstcw    50(%rsp)
..B1.3:
        movzwl    72(%rsp), %esi
        andl      $32767, %esi
        movzwl    50(%rsp), %eax
        cmpl      $32767, %esi
        je        ..B1.67
..B1.4:
        cmpl      $16382, %esi
        jge       ..B1.46
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
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.13
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.11:
        fldcw     48(%rsp)
..B1.12:
        movzwl    72(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.13:
        testl     %esi, %esi
        jle       ..B1.63
..B1.14:
        fldt      64(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     (%rdx)
        fstpt     (%rsp)
..B1.15:
        testb     %cl, %cl
        je        ..B1.17
..B1.16:
        fldcw     50(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.18:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.22
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.20:
        fldcw     48(%rsp)
..B1.21:
        movb      $1, %cl
..B1.22:
        fldt      64(%rsp)
        lea       _Q3(%rip), %rax
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.24
..B1.23:
        fldcw     50(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.25:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.29
..B1.26:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.27:
        fldcw     48(%rsp)
..B1.28:
        movb      $1, %cl
..B1.29:
        fldt      64(%rsp)
        lea       16+_Q2(%rip), %rax
        fld       %st(0)
        lea       _Q2(%rip), %rdx
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.31
..B1.30:
        fldcw     50(%rsp)
..B1.31:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.32:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.36
..B1.33:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.34:
        fldcw     48(%rsp)
..B1.35:
        movb      $1, %cl
..B1.36:
        fldt      64(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rdx
        fld       %st(0)
        lea       _Q1(%rip), %rdi
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
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
        fldcw     50(%rsp)
..B1.38:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.39:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.43
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.41:
        fldcw     48(%rsp)
..B1.42:
        movb      $1, %cl
..B1.43:
        fldt      64(%rsp)
        lea       _TWO_53H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        lea       304+_Q(%rip), %rdx
        lea       272+_Q(%rip), %rsi
        lea       240+_Q(%rip), %rdi
        lea       208+_Q(%rip), %r8
        fldl      (%rax)
        lea       176+_Q(%rip), %r9
        fmul      %st(3), %st
        lea       144+_Q(%rip), %r10
        lea       112+_Q(%rip), %r11
        lea       80+_Q(%rip), %rax
        testb     %cl, %cl
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fadd      %st, %st(2)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(4)
        fstpt     16(%rsp)
        fld       %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fadd      %st, %st(1)
        fldt      (%rdx)
        fmul      %st(2), %st
        lea       48+_Q(%rip), %rdx
        fldt      (%rsi)
        lea       16+_Q(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       288+_Q(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       256+_Q(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       224+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       192+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       160+_Q(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_Q(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       96+_Q(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       64+_Q(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       32+_Q(%rip), %rdi
        fmul      %st(3), %st
        fldt      (%r8)
        lea       _Q(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        lea       336+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        lea       320+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       _TWO_48H(%rip), %r11
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
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r8)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      (%r9)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fldt      (%r10)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fld       %st(0)
        fldl      (%r11)
        fld       %st(0)
        fadd      %st(5), %st
        fsubp     %st, %st(1)
        fmul      %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      64(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fmul      %st, %st(2)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.45
..B1.44:
        fldcw     50(%rsp)
..B1.45:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.46:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.50
..B1.47:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.48:
        fldcw     48(%rsp)
..B1.49:
        movb      $1, %cl
..B1.50:
        fldt      64(%rsp)
        lea       ones(%rip), %rdx
        movb      73(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %esi
        fldl      (%rdx,%rsi,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        movzwl    72(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
        cmpl      $16421, %edx
        jge       ..B1.59
..B1.51:
        cmpl      $16388, %edx
        jge       ..B1.53
..B1.52:
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fldl      (%rax)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(3)
        fsubr     %st, %st(3)
        fld       %st(3)
        fld       %st(4)
        fmul      %st(5), %st
        fxch      %st(4)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fsqrt     
        fadd      %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fsubrp    %st, %st(4)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fxch      %st(1)
        jmp       ..B1.58
..B1.53:
        fldt      .L_2il0floatpacket.2(%rip)
        lea       _TWO_32H(%rip), %rax
        fmul      %st(1), %st
        cmpl      $16394, %edx
        fld       %st(0)
        fldl      (%rax)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubr     %st(1), %st
        fldt      .L_2il0floatpacket.0(%rip)
        jge       ..B1.55
..B1.54:
        fstp      %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fsqrt     
        faddp     %st, %st(3)
        fxch      %st(1)
        fdiv      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B1.58
..B1.55:
        fstp      %st(3)
        cmpl      $16401, %edx
        jge       ..B1.57
..B1.56:
        fld       %st(2)
        fdiv      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fdivr     %st(2), %st
        faddp     %st, %st(1)
        jmp       ..B1.58
..B1.57:
        fxch      %st(1)
        fdivr     %st(2), %st
        faddp     %st, %st(1)
..B1.58:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     64(%rsp)
        movzwl    72(%rsp), %edx
        andl      $32767, %edx
        movl      %edx, %eax
        addl      $-16383, %edx
        negl      %eax
        fxch      %st(2)
        fstpt     (%rsp)
        addl      $-2, %eax
        movzwl    8(%rsp), %esi
        andl      $32767, %eax
        andl      $-32768, %esi
        orl       %eax, %esi
        movw      %si, 8(%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%rsp)
        fmulp     %st, %st(1)
        jmp       ..B1.60
..B1.59:
        fstp      %st(0)
        andl      $-32768, %eax
        addl      $-16382, %edx
        orl       $-49153, %eax
        movw      %ax, 72(%rsp)
        lea       _TWO_52H(%rip), %rax
        fldt      64(%rsp)
        fld       %st(0)
        fldl      (%rax)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
..B1.60:
        fldt      16(%rsp)
        lea       48+_P(%rip), %r8
        fldt      .L_2il0floatpacket.3(%rip)
        lea       16+_P(%rip), %r9
        movl      68(%rsp), %esi
        lea       64+_P(%rip), %r10
        shrl      $23, %esi
        lea       32+_P(%rip), %r11
        fldt      (%r8)
        movzbl    %sil, %eax
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        movl      %edx, (%rsp)
        lea       80+_P(%rip), %rdx
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rsi
        flds      (%rdi,%rax,4)
        fmul      %st, %st(3)
        shlq      $4, %rax
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(1)
        fld       %st(1)
        testb     %cl, %cl
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fildl     (%rsp)
        fldt      (%rdx)
        lea       _P(%rip), %rdx
        fmul      %st(3), %st
        faddp     %st, %st(6)
        fxch      %st(2)
        fmul      %st, %st(5)
        fldt      (%r9)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fldt      (%r10)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.4(%rip)
        fmul      %st(3), %st
        fldt      .L_2il0floatpacket.5(%rip)
        fmulp     %st, %st(4)
        faddl     8(%rsi,%rax)
        faddp     %st, %st(1)
        fxch      %st(2)
        faddl     (%rax,%rsi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.62
..B1.61:
        fldcw     50(%rsp)
..B1.62:
        fldt      16(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.63:
        cmpl      $0, 68(%rsp)
        jne       ..B1.65
..B1.64:
        cmpl      $0, 64(%rsp)
        je        ..B1.66
..B1.65:
        fldt      64(%rsp)
        lea       _small_value_80(%rip), %rsi
        movb      73(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fldt      (%rsi,%rdx)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.66:
        fldt      64(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.67:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.73
..B1.68:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.69:
        fldcw     48(%rsp)
..B1.70:
        fldt      64(%rsp)
        fstpt     (%rsp)
..B1.71:
        fldcw     50(%rsp)
..B1.72:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.73:
        fldt      64(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.72
        .align    16,0x90
	.cfi_endproc
	.type	asinhl,@function
	.size	asinhl,.-asinhl
	.data
# -- End  asinhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_Q3:
	.word	6991
	.word	42216
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	33268
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	9764
	.word	39322
	.word	39161
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	43682
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	14144
	.word	39303
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	10686
	.word	40209
	.word	28068
	.word	46811
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	6376
	.word	25459
	.word	13370
	.word	63707
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	42228
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	4505
	.word	52442
	.word	52428
	.word	52428
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	31967
	.word	56173
	.word	28086
	.word	46811
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	26124
	.word	58234
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	22137
	.word	23222
	.word	53620
	.word	46917
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	45395
	.word	61656
	.word	25205
	.word	36391
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	28077
	.word	15147
	.word	52421
	.word	58572
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	19650
	.word	2873
	.word	50009
	.word	48451
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	47538
	.word	64725
	.word	9133
	.word	40943
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	42460
	.word	64288
	.word	29314
	.word	35191
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	54924
	.word	18085
	.word	19628
	.word	61339
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	8359
	.word	63244
	.word	48955
	.word	54066
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	13301
	.word	22367
	.word	28967
	.word	48066
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	47487
	.word	41074
	.word	54143
	.word	42870
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	18176
	.word	28317
	.word	25378
	.word	37849
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	2947
	.word	59345
	.word	39582
	.word	64148
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	7071
	.word	46945
	.word	60101
	.word	49232
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	33184
	.word	23492
	.word	18153
	.word	62298
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	44779
	.word	60944
	.word	30881
	.word	55868
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	22870
	.word	51718
	.word	28591
	.word	51709
	.word	16370
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
	.word	39328
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,352
	.align 2
_P:
	.word	65
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	44010
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	58670
	.word	61414
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	24905
	.word	46201
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	57053
	.word	27587
	.word	64478
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	41508
	.word	48914
	.word	32459
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,96
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
