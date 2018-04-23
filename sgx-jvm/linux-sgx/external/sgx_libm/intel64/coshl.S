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
	.file "coshl.c"
	.text
..TXTST0:
# -- Begin  coshl
	.text
       .align    16,0x90
	.globl coshl
coshl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_coshl.1:
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
        jge       ..B1.52
..B1.4:
        cmpl      $16382, %esi
        jge       ..B1.39
..B1.5:
        movzwl    82(%rsp), %edx
        cmpl      $16378, %esi
        jge       ..B1.32
..B1.6:
        cmpl      $16372, %esi
        jge       ..B1.25
..B1.7:
        cmpl      $16365, %esi
        jge       ..B1.18
..B1.8:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.10:
        fldcw     80(%rsp)
..B1.11:
        movzwl    104(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.12:
        fldt      96(%rsp)
        cmpl      $16308, %esi
        fstpt     (%rsp)
        jge       ..B1.14
..B1.13:
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      (%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.14:
        fldt      (%rsp)
        lea       _Q3(%rip), %rax
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      (%rax)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
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
        lea       16+_Q2(%rip), %rax
        fmul      %st(0), %st
        lea       _Q2(%rip), %rdx
        fldt      (%rax)
        testb     %cl, %cl
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
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
        lea       48+_Q1(%rip), %rax
        fmul      %st(0), %st
        lea       32+_Q1(%rip), %rsi
        fld       %st(0)
        lea       16+_Q1(%rip), %rdx
        fmul      %st(1), %st
        lea       _Q1(%rip), %rdi
        fldt      (%rax)
        testb     %cl, %cl
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
        fldt      .L_2il0floatpacket.0(%rip)
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
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       96+_Q(%rip), %rdx
        fmul      %st(3), %st
        lea       64+_Q(%rip), %rsi
        fld       %st(0)
        lea       112+_Q(%rip), %r8
        fmul      %st(1), %st
        lea       32+_Q(%rip), %rdi
        fldl      (%rax)
        lea       80+_Q(%rip), %r9
        lea       48+_Q(%rip), %r10
        lea       16+_Q(%rip), %r11
        lea       144+_Q(%rip), %rax
        testb     %cl, %cl
        fadd      %st, %st(4)
        fsubr     %st, %st(4)
        fld       %st(4)
        fxch      %st(4)
        fsub      %st(5), %st
        fmul      %st, %st(4)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        fldt      (%rdx)
        lea       128+_Q(%rip), %rdx
        fmul      %st(4), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        fmul      %st(5), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      (%r11)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(2)
        fldt      (%rax)
        fld       %st(0)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(1)
        fldt      (%rdx)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fadd      %st(6), %st
        fsub      %st, %st(6)
        fxch      %st(2)
        fsub      %st(6), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        je        ..B1.75
..B1.37:
        fstpt     16(%rsp)
..B1.73:
        fldcw     82(%rsp)
        jmp       ..B1.38
..B1.75:
        fstp      %st(0)
..B1.38:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.39:
        movzwl    82(%rsp), %edx
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
        lea       ones(%rip), %rax
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rdi
        movb      105(%rsp), %dl
        lea       _TWO_32(%rip), %r8
        andb      $-128, %dl
        lea       _TWO_32P(%rip), %r9
        shrb      $7, %dl
        lea       64+_P(%rip), %r10
        fstpt     64(%rsp)
        fldl      (%rdi)
        fldt      .L_2il0floatpacket.1(%rip)
        fldt      .L_2il0floatpacket.2(%rip)
        fldt      .L_2il0floatpacket.3(%rip)
        fldl      (%r9)
        movzbl    %dl, %esi
        lea       32+_P(%rip), %r11
        lea       48+_P(%rip), %rdi
        fxch      %st(5)
        fmull     (%rax,%rsi,8)
        lea       _P(%rip), %rsi
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(4), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(4)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        movl      16(%rsp), %edx
        fsub      %st(2), %st
        fmul      %st, %st(3)
        fldl      (%r8)
        lea       16+_P(%rip), %r8
        fmul      %st, %st(1)
        movsbq    %dl, %rax
        fxch      %st(1)
        fsubrp    %st, %st(4)
        movzwl    104(%rsp), %r9d
        subl      %eax, %edx
        andl      $32767, %r9d
        fxch      %st(1)
        fsub      %st(3), %st
        shrl      $8, %edx
        fsubp     %st, %st(2)
        fld       %st(2)
        cmpl      $16387, %r9d
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
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
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        jle       ..B1.47
..B1.44:
        addl      $16350, %edx
        faddp     %st, %st(2)
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
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(0)
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.46
..B1.45:
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     48(%rsp)
..B1.74:
        fldcw     82(%rsp)
..B1.46:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.47:
        je        ..B1.63
..B1.48:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     48(%rsp)
..B1.49:
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
        fmulp     %st, %st(3)
        fldt      48(%rsp)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.51
..B1.50:
        fldcw     82(%rsp)
..B1.51:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.52:
        je        ..B1.68
..B1.53:
        movzwl    82(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.57
..B1.54:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.55:
        fldcw     80(%rsp)
..B1.56:
        movzwl    104(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.57:
        cmpl      $32767, %esi
        jne       ..B1.59
..B1.58:
        fldt      96(%rsp)
        lea       ones(%rip), %rdx
        movb      105(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %esi
        fmull     (%rdx,%rsi,8)
        fstpt     (%rsp)
        jmp       ..B1.60
..B1.59:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
..B1.60:
        testb     %cl, %cl
        je        ..B1.62
..B1.61:
        fldcw     82(%rsp)
..B1.62:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.63:
        movl      100(%rsp), %esi
        cmpl      $-782985146, %esi
        ja        ..B1.44
..B1.64:
        jne       ..B1.67
..B1.65:
        cmpl      $1501741449, 96(%rsp)
        ja        ..B1.44
..B1.66:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     48(%rsp)
        jmp       ..B1.49
..B1.67:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     48(%rsp)
        jmp       ..B1.49
..B1.68:
        movl      100(%rsp), %eax
        cmpl      $-1317741120, %eax
        jb        ..B1.4
..B1.69:
        jne       ..B1.53
..B1.70:
        cmpl      $833536233, 96(%rsp)
        jbe       ..B1.4
        jmp       ..B1.53
        .align    16,0x90
	.cfi_endproc
	.type	coshl,@function
	.size	coshl,.-coshl
	.data
# -- End  coshl
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
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
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
	.word	213
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
	.word	62430
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	22894
	.word	21845
	.word	43733
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	65531
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	7054
	.word	43693
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	25016
	.word	58228
	.word	24755
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	57397
	.word	35800
	.word	53031
	.word	53262
	.word	16367
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	18360
	.word	43637
	.word	28980
	.word	40373
	.word	49076
	.word	0
	.word	0
	.word	0
	.word	16302
	.word	43691
	.word	43690
	.word	43690
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	46125
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	29965
	.word	3335
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	54950
	.word	46751
	.word	32187
	.word	37874
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	30733
	.word	25775
	.word	51110
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	49850
	.word	450
	.word	9929
	.word	51659
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	2776
	.word	45191
	.word	37201
	.word	55334
	.word	16338
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
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,160
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
