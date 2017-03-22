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
	.file "tanhl.c"
	.text
..TXTST0:
# -- Begin  tanhl
	.text
       .align    16,0x90
	.globl tanhl
tanhl:
# parameter 1: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_tanhl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %dil, %dil
..B1.2:
        fnstcw    50(%rsp)
..B1.3:
        movb      73(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzwl    72(%rsp), %ecx
        andl      $32767, %ecx
        movzbl    %al, %esi
        cmpl      $16388, %ecx
        movzwl    50(%rsp), %eax
        jge       ..B1.57
..B1.4:
        cmpl      $16382, %ecx
        jge       ..B1.46
..B1.5:
        cmpl      $16376, %ecx
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %ecx
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %ecx
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %ecx
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
        movzwl    72(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.13:
        testl     %ecx, %ecx
        jle       ..B1.66
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
        testb     %dil, %dil
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
        movb      $1, %dil
..B1.22:
        fldt      64(%rsp)
        lea       _Q3(%rip), %rax
        testb     %dil, %dil
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
        movb      $1, %dil
..B1.29:
        fldt      64(%rsp)
        lea       16+_Q2(%rip), %rax
        fld       %st(0)
        lea       _Q2(%rip), %rdx
        fmul      %st(1), %st
        testb     %dil, %dil
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
        movb      $1, %dil
..B1.36:
        fldt      64(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rcx
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rdx
        fld       %st(0)
        lea       _Q1(%rip), %rsi
        fmul      %st(1), %st
        testb     %dil, %dil
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmulp     %st, %st(2)
        fldt      (%rsi)
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
        movb      $1, %dil
..B1.43:
        fldt      64(%rsp)
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       240+_Q(%rip), %rdx
        fmul      %st(3), %st
        lea       208+_Q(%rip), %rcx
        fld       %st(0)
        lea       176+_Q(%rip), %rsi
        fmul      %st(1), %st
        lea       144+_Q(%rip), %r8
        fldl      (%rax)
        lea       112+_Q(%rip), %r9
        fmul      %st(5), %st
        lea       80+_Q(%rip), %r10
        lea       48+_Q(%rip), %r11
        lea       16+_Q(%rip), %rax
        testb     %dil, %dil
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(4)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(5)
        fstpt     16(%rsp)
        fld       %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(4)
        fmul      %st(0), %st
        fldt      (%rdx)
        lea       224+_Q(%rip), %rdx
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       192+_Q(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rsi)
        lea       160+_Q(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r8)
        lea       128+_Q(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r9)
        lea       96+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r10)
        lea       64+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r11)
        lea       32+_Q(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rax)
        lea       _Q(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rdx)
        lea       272+_Q(%rip), %rdx
        fmul      %st(6), %st
        fldt      (%rcx)
        lea       256+_Q(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%rsi)
        lea       _TWO_48H(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(6)
        fldt      (%rax)
        faddp     %st, %st(6)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      (%rdx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        fld       %st(0)
        fldl      (%rsi)
        fld       %st(0)
        fadd      %st(4), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(4)
        fxch      %st(5)
        faddp     %st, %st(4)
        fldt      64(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        je        ..B1.80
..B1.44:
        fstpt     16(%rsp)
..B1.79:
        fldcw     50(%rsp)
        jmp       ..B1.45
..B1.80:
        fstp      %st(0)
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
        movzwl    72(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.50:
        fldt      64(%rsp)
        lea       twos(%rip), %r8
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %r9
        fstpt     32(%rsp)
        lea       _TWO_32(%rip), %r10
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _TWO_32P(%rip), %r11
        fxch      %st(1)
        fmull     (%r8,%rsi,8)
        lea       64+_P(%rip), %r8
        fmul      %st, %st(1)
        fldl      (%r9)
        lea       32+_P(%rip), %r9
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(2), %st
        movl      16(%rsp), %edx
        fsubrp    %st, %st(1)
        fld       %st(0)
        fldt      .L_2il0floatpacket.3(%rip)
        fmulp     %st, %st(3)
        movsbq    %dl, %rax
        fsub      %st(2), %st
        fldl      (%r10)
        lea       _P(%rip), %r10
        fldl      (%r11)
        lea       48+_P(%rip), %r11
        fmul      %st(2), %st
        subl      %eax, %edx
        fxch      %st(1)
        fmul      %st, %st(2)
        shrl      $8, %edx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(0)
        addl      $16383, %edx
        andl      $32767, %edx
        fxch      %st(1)
        fsubr     %st, %st(3)
        shlq      $4, %rax
        fxch      %st(4)
        fsubrp    %st, %st(3)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       16+_P(%rip), %r8
        fmul      %st(1), %st
        fldt      (%r9)
        movzwl    8+.L_2il0floatpacket.0(%rip), %r9d
        faddp     %st, %st(1)
        fmul      %st(1), %st
        andl      $-32768, %r9d
        fldt      (%r10)
        orl       %edx, %r9d
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        cmpl      $16385, %ecx
        movw      %r9w, 40(%rsp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      2056(%rdx,%rax)
        fldl      2048(%rdx,%rax)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        jge       ..B1.52
..B1.51:
        fldt      32(%rsp)
        fld       %st(1)
        lea       _TWO_32H(%rip), %rax
        fadd      %st(6), %st
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fadd      %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fdivr     %st(1), %st
        fld       %st(2)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fmul      %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fxch      %st(2)
        fsub      %st(3), %st
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(6)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      .L_2il0floatpacket.4(%rip)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.54
..B1.52:
        je        ..B1.70
..B1.53:
        fstp      %st(2)
        fldt      .L_2il0floatpacket.0(%rip)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        fmulp     %st, %st(2)
        fadd      %st, %st(1)
        fldt      .L_2il0floatpacket.4(%rip)
        fdivp     %st, %st(2)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
..B1.54:
        fldt      (%rsp)
        testq     %rsi, %rsi
        fld       %st(0)
        fchs      
        fcmove    %st(1), %st
        fstp      %st(1)
        fstpt     (%rsp)
        testb     %dil, %dil
        je        ..B1.56
..B1.55:
        fldcw     50(%rsp)
..B1.56:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.57:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.61
..B1.58:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.59:
        fldcw     48(%rsp)
..B1.60:
        movzwl    72(%rsp), %ecx
        movb      $1, %dil
        andl      $32767, %ecx
..B1.61:
        cmpl      $32767, %ecx
        je        ..B1.73
..B1.62:
        movq      %rsi, %rdx
        lea       _small_value_80(%rip), %rax
        shlq      $4, %rdx
        lea       ones(%rip), %rcx
        fldt      (%rax,%rdx)
        fsubrl    (%rcx,%rsi,8)
        fstpt     (%rsp)
..B1.63:
        testb     %dil, %dil
        je        ..B1.65
..B1.64:
        fldcw     50(%rsp)
..B1.65:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.66:
        cmpl      $0, 68(%rsp)
        jne       ..B1.68
..B1.67:
        cmpl      $0, 64(%rsp)
        je        ..B1.69
..B1.68:
        fldt      64(%rsp)
        lea       _small_value_80(%rip), %rax
        shlq      $4, %rsi
        fldt      (%rax)
        fldt      (%rax,%rsi)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.69:
        fldt      64(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.70:
        movl      68(%rsp), %eax
        cmpl      $-1771674010, %eax
        jb        ..B1.51
..B1.71:
        jne       ..B1.53
..B1.72:
        cmpl      $1717986918, 64(%rsp)
        jb        ..B1.51
        jmp       ..B1.53
..B1.73:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.76
..B1.74:
        cmpl      $0, 64(%rsp)
        jne       ..B1.76
..B1.75:
        lea       ones(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     (%rsp)
        jmp       ..B1.63
..B1.76:
        fldt      64(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.63
        .align    16,0x90
	.cfi_endproc
	.type	tanhl,@function
	.size	tanhl,.-tanhl
	.data
# -- End  tanhl
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 8
twos:
	.long	0x00000000,0x40000000
	.long	0x00000000,0xc0000000
	.type	twos,@object
	.size	twos,16
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
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.align 4
_TWO_32P:
	.long	1048576
	.long	1106247680
	.type	_TWO_32P,@object
	.size	_TWO_32P,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_Q3:
	.word	21845
	.word	42325
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	36147
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	55638
	.word	59918
	.word	34848
	.word	34952
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	43688
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	48627
	.word	34947
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	23872
	.word	38674
	.word	53460
	.word	56589
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	33152
	.word	13396
	.word	6324
	.word	45860
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	43505
	.word	43690
	.word	43690
	.word	43690
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	51768
	.word	34951
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	53715
	.word	3536
	.word	53469
	.word	56589
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	37274
	.word	24708
	.word	42049
	.word	45863
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	51222
	.word	13677
	.word	6831
	.word	37175
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	45961
	.word	31945
	.word	59504
	.word	60265
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	5972
	.word	17449
	.word	45604
	.word	48849
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	32816
	.word	2946
	.word	4564
	.word	39596
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	14786
	.word	2112
	.word	44465
	.word	64190
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	3031
	.word	16844
	.word	22916
	.word	52030
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	47485
	.word	32270
	.word	51436
	.word	42167
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	3071
	.word	14344
	.word	30185
	.word	34131
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	24996
	.word	54454
	.word	53234
	.word	54781
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	39193
	.word	24581
	.word	37984
	.word	42131
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	5913
	.word	28127
	.word	47865
	.word	55395
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	25952
	.word	54950
	.word	21582
	.word	44803
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34944
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,288
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
