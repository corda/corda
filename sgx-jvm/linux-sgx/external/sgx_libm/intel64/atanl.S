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
	.file "atanl.c"
	.text
..TXTST0:
# -- Begin  atanl
	.text
       .align    16,0x90
	.globl atanl
atanl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_atanl.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %cl, %cl
..B1.2:
        fnstcw    74(%rsp)
..B1.3:
        movzbl    105(%rsp), %edx
        movzwl    104(%rsp), %edi
        andl      $128, %edx
        andl      $32767, %edi
        shrl      $7, %edx
        movzwl    74(%rsp), %eax
        cmpl      $16394, %edi
        jge       ..B1.48
..B1.4:
        cmpl      $16376, %edi
        jge       ..B1.38
..B1.5:
        cmpl      $16371, %edi
        jge       ..B1.31
..B1.6:
        cmpl      $16365, %edi
        jge       ..B1.24
..B1.7:
        cmpl      $16308, %edi
        jge       ..B1.17
..B1.8:
        movl      %eax, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.12
..B1.9:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.10:
        fldcw     72(%rsp)
..B1.11:
        movzwl    104(%rsp), %edi
        movb      $1, %cl
        andl      $32767, %edi
..B1.12:
        testl     %edi, %edi
        jle       ..B1.64
..B1.13:
        fldt      96(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     (%rdx)
        fstpt     (%rsp)
..B1.14:
        testb     %cl, %cl
        je        ..B1.16
..B1.15:
        fldcw     74(%rsp)
..B1.16:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.17:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.21
..B1.18:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.19:
        fldcw     72(%rsp)
..B1.20:
        movb      $1, %cl
..B1.21:
        fldt      96(%rsp)
        lea       _P2(%rip), %rax
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.23
..B1.22:
        fldcw     74(%rsp)
..B1.23:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.24:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.28
..B1.25:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.26:
        fldcw     72(%rsp)
..B1.27:
        movb      $1, %cl
..B1.28:
        fldt      96(%rsp)
        lea       16+_P1(%rip), %rax
        fld       %st(0)
        lea       _P1(%rip), %rdx
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
        je        ..B1.30
..B1.29:
        fldcw     74(%rsp)
..B1.30:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.31:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.35
..B1.32:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.33:
        fldcw     72(%rsp)
..B1.34:
        movb      $1, %cl
..B1.35:
        fldt      96(%rsp)
        lea       48+_P(%rip), %rax
        fld       %st(0)
        lea       32+_P(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_P(%rip), %rdx
        fld       %st(0)
        lea       _P(%rip), %rdi
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
        je        ..B1.37
..B1.36:
        fldcw     74(%rsp)
..B1.37:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.38:
        movl      %eax, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.42
..B1.39:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.40:
        fldcw     72(%rsp)
..B1.41:
        movb      $1, %cl
..B1.42:
        fldt      96(%rsp)
        lea       ones(%rip), %rax
        movl      %edx, %edx
        lea       _TWO_63H(%rip), %rsi
        fldl      (%rax,%rdx,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        movzwl    104(%rsp), %edi
        andl      $32767, %edi
        fxch      %st(1)
        fstpl     64(%rsp)
        cmpl      $16383, %edi
        fldl      (%rsi)
        jge       ..B1.44
..B1.43:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_48H(%rip), %rax
        fmul      %st(2), %st
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(2)
        fld       %st(3)
        fld       %st(4)
        movl      16(%rsp), %edx
        fxch      %st(4)
        fsubrp    %st, %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fldl      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(4)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.2(%rip)
        fadd      %st, %st(3)
        fldt      .L_2il0floatpacket.3(%rip)
        fstpt     (%rsp)
        jmp       ..B1.45
..B1.44:
        fldt      .L_2il0floatpacket.2(%rip)
        fld       %st(2)
        fdivrp    %st, %st(1)
        lea       _TWO_48H(%rip), %rax
        fld       %st(2)
        fldt      .L_2il0floatpacket.0(%rip)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(2)
        fld       %st(2)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        fldl      (%rax)
        fmul      %st, %st(2)
        movl      16(%rsp), %edx
        fxch      %st(1)
        fadd      %st(2), %st
        negl      %edx
        fsubp     %st, %st(2)
        fld       %st(1)
        addl      $256, %edx
        fmul      %st(3), %st
        fld       %st(2)
        fadd      %st(4), %st
        fxch      %st(3)
        fsubr     %st, %st(5)
        fld       %st(5)
        fmul      %st(5), %st
        fldt      .L_2il0floatpacket.3(%rip)
        fstpt     (%rsp)
        fldt      (%rsp)
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fldt      .L_2il0floatpacket.2(%rip)
..B1.45:
        fld       %st(3)
        lea       48+_P(%rip), %rax
        lea       32+_P(%rip), %rdi
        lea       16+_P(%rip), %rsi
        lea       _P(%rip), %r8
        lea       _TWO_32H(%rip), %r9
        movslq    %edx, %rdx
        fadd      %st(6), %st
        fdivrp    %st, %st(1)
        fmul      %st, %st(2)
        fld       %st(2)
        shlq      $4, %rdx
        fadd      %st(1), %st
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %r10
        testb     %cl, %cl
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fld       %st(4)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(7)
        faddp     %st, %st(5)
        fxch      %st(3)
        fstpt     48(%rsp)
        fldt      (%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      (%rax)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        fmulp     %st, %st(4)
        fldt      (%r8)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      (%r10,%rdx)
        fld       %st(0)
        fadd      %st(2), %st
        fldl      (%r9)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddl     8(%r10,%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     64(%rsp)
        fstpt     32(%rsp)
        je        ..B1.74
..B1.46:
        fstpt     16(%rsp)
..B1.73:
        fldcw     74(%rsp)
        jmp       ..B1.47
..B1.74:
        fstp      %st(0)
..B1.47:
        fldt      32(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.48:
        movl      %eax, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.52
..B1.49:
        orl       $-64768, %eax
        movw      %ax, 72(%rsp)
..B1.50:
        fldcw     72(%rsp)
..B1.51:
        movzwl    104(%rsp), %edi
        movb      $1, %cl
        andl      $32767, %edi
..B1.52:
        cmpl      $32767, %edi
        je        ..B1.68
..B1.53:
        cmpl      $16448, %edi
        jge       ..B1.60
..B1.54:
        fldt      96(%rsp)
        xorl      %esi, %esi
        testl     %edx, %edx
        lea       ones(%rip), %rdi
        sete      %sil
        fdivrl    (%rdi,%rsi,8)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        movzwl    104(%rsp), %eax
        andl      $32767, %eax
        fstpt     (%rsp)
        cmpl      $16398, %eax
        jge       ..B1.56
..B1.55:
        fldt      (%rsp)
        lea       16+_P1(%rip), %rax
        fld       %st(0)
        lea       _P1(%rip), %rsi
        fmul      %st(1), %st
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.59
..B1.56:
        cmpl      $16408, %eax
        jge       ..B1.59
..B1.57:
        fldt      (%rsp)
        lea       _P2(%rip), %rax
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
..B1.59:
        fldt      .L_2il0floatpacket.4(%rip)
        fldt      (%rsp)
        movl      %edx, %edx
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.5(%rip)
        faddp     %st, %st(1)
        fmull     (%rdi,%rdx,8)
        fstpt     (%rsp)
        jmp       ..B1.61
..B1.60:
        movl      %edx, %edx
        lea       _pi_2l(%rip), %rax
        shlq      $4, %rdx
        lea       _small_value_80(%rip), %rsi
        fldt      (%rdx,%rax)
        fldt      (%rdx,%rsi)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
..B1.61:
        testb     %cl, %cl
        je        ..B1.63
..B1.62:
        fldcw     74(%rsp)
..B1.63:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.64:
        cmpl      $0, 100(%rsp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 96(%rsp)
        je        ..B1.67
..B1.66:
        fldt      96(%rsp)
        lea       _small_value_80(%rip), %rax
        shlq      $4, %rdx
        fldt      (%rax)
        fldt      (%rax,%rdx)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.14
..B1.67:
        fldt      96(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.14
..B1.68:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.70
..B1.69:
        cmpl      $0, 96(%rsp)
        je        ..B1.53
..B1.70:
        fldt      96(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.61
        .align    16,0x90
	.cfi_endproc
	.type	atanl,@function
	.size	atanl,.-atanl
	.data
# -- End  atanl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x88,0x19,0x13,0xd3,0x08,0xa3,0x85,0xdd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0xa2,0xda,0x0f,0xc9,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
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
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_P2:
	.word	43691
	.word	41642
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.type	_P2,@object
	.size	_P2,16
	.align 2
_P1:
	.word	23723
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	22830
	.word	33705
	.word	52154
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,32
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,64
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
