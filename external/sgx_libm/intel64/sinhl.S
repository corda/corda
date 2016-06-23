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
	.file "sinhl.c"
	.text
..TXTST0:
# -- Begin  sinhl
	.text
       .align    16,0x90
	.globl sinhl
sinhl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_sinhl.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %cl, %cl
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movzwl    104(%rsp), %esi
        andl      $32767, %esi
        cmpl      $16396, %esi
        jge       ..B1.59
..B1.4:
        cmpl      $16382, %esi
        jge       ..B1.46
..B1.5:
        movzwl    82(%rsp), %edx
        cmpl      $16378, %esi
        jge       ..B1.39
..B1.6:
        cmpl      $16373, %esi
        jge       ..B1.32
..B1.7:
        cmpl      $16366, %esi
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %esi
        jge       ..B1.18
..B1.9:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.13
..B1.10:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.11:
        fldcw     80(%rsp)
..B1.12:
        movzwl    104(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.13:
        testl     %esi, %esi
        jle       ..B1.70
..B1.14:
        fldt      96(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     (%rdx)
        fstpt     (%rsp)
..B1.15:
        testb     %cl, %cl
        je        ..B1.17
..B1.16:
        fldcw     82(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.18:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.22
..B1.19:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.20:
        fldcw     80(%rsp)
..B1.21:
        movb      $1, %cl
..B1.22:
        fldt      96(%rsp)
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
        fldcw     82(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.25:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.26:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.27:
        fldcw     80(%rsp)
..B1.28:
        movb      $1, %cl
..B1.29:
        fldt      96(%rsp)
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
        fldcw     82(%rsp)
..B1.31:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.32:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.33:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.34:
        fldcw     80(%rsp)
..B1.35:
        movb      $1, %cl
..B1.36:
        fldt      96(%rsp)
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
        fldcw     82(%rsp)
..B1.38:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.39:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.43
..B1.40:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.41:
        fldcw     80(%rsp)
..B1.42:
        movb      $1, %cl
..B1.43:
        fldt      96(%rsp)
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       112+_Q(%rip), %rsi
        fmul      %st(3), %st
        lea       80+_Q(%rip), %rdi
        fld       %st(0)
        lea       48+_Q(%rip), %r8
        fmul      %st(1), %st
        lea       16+_Q(%rip), %r9
        fldl      (%rax)
        lea       96+_Q(%rip), %r10
        lea       64+_Q(%rip), %r11
        lea       32+_Q(%rip), %rax
        lea       128+_Q(%rip), %rdx
        testb     %cl, %cl
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(3)
        fxch      %st(4)
        fstpt     48(%rsp)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(0), %st
        fldt      (%rdx)
        lea       _Q(%rip), %rdx
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        fldt      (%rsi)
        fmul      %st(4), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        fmul      %st(5), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      (%rdx)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      96(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.45
..B1.44:
        fldt      16(%rsp)
        fstpt     32(%rsp)
..B1.84:
        fldcw     82(%rsp)
..B1.45:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.46:
        movzwl    82(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.50
..B1.47:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.48:
        fldcw     80(%rsp)
..B1.49:
        movb      $1, %cl
..B1.50:
        fldt      96(%rsp)
        lea       ones(%rip), %rax
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rdi
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _TWO_32(%rip), %r8
        fldt      .L_2il0floatpacket.2(%rip)
        lea       _TWO_32P(%rip), %r9
        movb      105(%rsp), %dl
        lea       64+_P(%rip), %r10
        fldl      (%rdi)
        andb      $-128, %dl
        fldl      (%r8)
        lea       32+_P(%rip), %r11
        shrb      $7, %dl
        lea       48+_P(%rip), %rdi
        fstl      16(%rsp)
        movzbl    %dl, %esi
        lea       16+_P(%rip), %r8
        fldl      (%rax,%rsi,8)
        lea       _P(%rip), %rsi
        fmul      %st, %st(6)
        fxch      %st(5)
        fmul      %st(6), %st
        fadd      %st(2), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fsubp     %st, %st(2)
        fld       %st(1)
        movl      32(%rsp), %edx
        fsub      %st(1), %st
        movsbq    %dl, %rax
        subl      %eax, %edx
        shrl      $8, %edx
        fxch      %st(3)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldl      (%r9)
        fmul      %st(4), %st
        fxch      %st(5)
        fmulp     %st, %st(4)
        movzwl    104(%rsp), %r9d
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fld       %st(3)
        andl      $32767, %r9d
        cmpl      $16387, %r9d
        fxch      %st(2)
        fsub      %st(4), %st
        fsubp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        jle       ..B1.54
..B1.51:
        addl      $16350, %edx
        faddp     %st, %st(1)
        movzwl    72(%rsp), %esi
        andl      $32767, %edx
        andl      $-32768, %esi
        shlq      $4, %rax
        orl       %edx, %esi
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        testb     %cl, %cl
        movw      %si, 72(%rsp)
        fldl      2056(%rdx,%rax)
        fldl      2048(%rdx,%rax)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fmul      %st, %st(4)
        fld       %st(0)
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      64(%rsp)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        fldl      16(%rsp)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        je        ..B1.86
..B1.52:
        fstpt     48(%rsp)
..B1.85:
        fldcw     82(%rsp)
        jmp       ..B1.53
..B1.86:
        fstp      %st(0)
..B1.53:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.54:
        je        ..B1.74
..B1.55:
        fxch      %st(2)
        fstpt     48(%rsp)
..B1.56:
        movzwl    56(%rsp), %r8d
        lea       16382(%rdx), %esi
        negl      %edx
        andl      $-32768, %r8d
        addl      $16382, %edx
        fld       %st(0)
        andl      $32767, %edx
        andl      $32767, %esi
        shlq      $4, %rax
        orl       %edx, %r8d
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        fadd      %st(2), %st
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(2)
        fldl      2056(%rdx,%rax)
        fldl      2048(%rdx,%rax)
        negq      %rax
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movzwl    72(%rsp), %edi
        fadd      %st(1), %st
        fmulp     %st, %st(4)
        fld       %st(4)
        fmul      %st(1), %st
        andl      $-32768, %edi
        faddp     %st, %st(4)
        fldl      2056(%rdx,%rax)
        orl       %esi, %edi
        fldl      2048(%rdx,%rax)
        testb     %cl, %cl
        fld       %st(0)
        movw      %di, 72(%rsp)
        fadd      %st(2), %st
        fmulp     %st, %st(5)
        fld       %st(6)
        fmul      %st(2), %st
        movw      %r8w, 56(%rsp)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        fstpt     32(%rsp)
        fxch      %st(3)
        fmul      %st, %st(5)
        fldt      16(%rsp)
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fsub      %st(5), %st
        fsubr     %st, %st(4)
        fxch      %st(5)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmul      %st, %st(1)
        fld       %st(1)
        fxch      %st(4)
        fmulp     %st, %st(1)
        fldt      48(%rsp)
        fmul      %st, %st(5)
        fxch      %st(4)
        fsub      %st(5), %st
        fsubr     %st, %st(2)
        fxch      %st(5)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.58
..B1.57:
        fldcw     82(%rsp)
..B1.58:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.59:
        je        ..B1.79
..B1.60:
        movzwl    82(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.64
..B1.61:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.62:
        fldcw     80(%rsp)
..B1.63:
        movzwl    104(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.64:
        cmpl      $32767, %esi
        jne       ..B1.66
..B1.65:
        fldt      96(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.67
..B1.66:
        movb      105(%rsp), %al
        lea       _large_value_80(%rip), %rsi
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fldt      (%rsi,%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.67:
        testb     %cl, %cl
        je        ..B1.69
..B1.68:
        fldcw     82(%rsp)
..B1.69:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.70:
        cmpl      $0, 100(%rsp)
        jne       ..B1.72
..B1.71:
        cmpl      $0, 96(%rsp)
        je        ..B1.73
..B1.72:
        fldt      96(%rsp)
        lea       _small_value_80(%rip), %rsi
        movb      105(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fldt      (%rsi,%rdx)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.73:
        fldt      96(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.74:
        movl      100(%rsp), %esi
        cmpl      $-782985146, %esi
        ja        ..B1.51
..B1.75:
        jne       ..B1.55
..B1.76:
        cmpl      $1501741449, 96(%rsp)
        ja        ..B1.51
        jmp       ..B1.55
..B1.79:
        movl      100(%rsp), %eax
        cmpl      $-1317741120, %eax
        jb        ..B1.4
..B1.80:
        jne       ..B1.60
..B1.81:
        cmpl      $833536233, 96(%rsp)
        jbe       ..B1.4
        jmp       ..B1.60
        .align    16,0x90
	.cfi_endproc
	.type	sinhl,@function
	.size	sinhl,.-sinhl
	.data
# -- End  sinhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0xf0,0x17,0x5c,0x29,0x3b,0xaa,0xb8,0x07,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xf6,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xd4,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
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
	.align 2
_Q3:
	.word	21845
	.word	44373
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	29491
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	1838
	.word	3745
	.word	35050
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	56472
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	6470
	.word	8609
	.word	207
	.word	53261
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	51515
	.word	9159
	.word	10794
	.word	47344
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	43620
	.word	43690
	.word	43690
	.word	43690
	.word	16364
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
	.word	52382
	.word	3328
	.word	208
	.word	53261
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	44665
	.word	46658
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	15678
	.word	35942
	.word	11071
	.word	55090
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	10412
	.word	23121
	.word	12507
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	37213
	.word	41879
	.word	65084
	.word	55102
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	1721
	.word	42148
	.word	19309
	.word	52099
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,144
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
