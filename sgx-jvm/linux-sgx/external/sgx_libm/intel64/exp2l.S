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
	.file "exp2l.c"
	.text
..TXTST0:
# -- Begin  exp2l
	.text
       .align    16,0x90
	.globl exp2l
exp2l:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_exp2l.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %r8b, %r8b
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movb      105(%rsp), %cl
        lea       _ranges(%rip), %rdx
        andb      $-128, %cl
        shrb      $7, %cl
        movzbl    %cl, %edi
        movq      %rdi, %rsi
        shlq      $4, %rsi
        movzwl    104(%rsp), %ecx
        andl      $32767, %ecx
        movzwl    8(%rsi,%rdx), %eax
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jl        ..B1.8
..B1.4:
        jne       ..B1.39
..B1.5:
        movl      100(%rsp), %eax
        cmpl      4(%rsi,%rdx), %eax
        jb        ..B1.8
..B1.6:
        jne       ..B1.39
..B1.7:
        movl      96(%rsp), %eax
        cmpl      (%rsi,%rdx), %eax
        jae       ..B1.39
..B1.8:
        movzwl    82(%rsp), %edx
        cmpl      $16369, %ecx
        jge       ..B1.19
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
        movzwl    104(%rsp), %ecx
        movb      $1, %r8b
        andl      $32767, %ecx
..B1.13:
        fldt      96(%rsp)
        cmpl      $16316, %ecx
        fstpt     16(%rsp)
        jge       ..B1.15
..B1.14:
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.16
..B1.15:
        fldt      16(%rsp)
        lea       48+_P1(%rip), %rax
        fld       %st(0)
        lea       32+_P1(%rip), %rcx
        lea       16+_P1(%rip), %rdx
        lea       _P1(%rip), %rsi
        fmul      %st(1), %st
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
        fldt      .L_2il0floatpacket.0(%rip)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.16:
        testb     %r8b, %r8b
        je        ..B1.18
..B1.17:
        fldcw     82(%rsp)
..B1.18:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.19:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.23
..B1.20:
        orl       $-64768, %edx
        movw      %dx, 80(%rsp)
..B1.21:
        fldcw     80(%rsp)
..B1.22:
        movzwl    104(%rsp), %ecx
        movb      $1, %r8b
        andl      $32767, %ecx
..B1.23:
        fldt      .L_2il0floatpacket.0(%rip)
        cmpl      $16383, %ecx
        fstpt     64(%rsp)
        jl        ..B1.30
..B1.24:
        fldt      96(%rsp)
        lea       _TWO_63H(%rip), %rax
        cmpl      $0, 96(%rsp)
        fldl      (%rax)
        jne       ..B1.31
..B1.25:
        negl      %ecx
        movl      $-1, %eax
        addl      $30, %ecx
        shll      %cl, %eax
        testl     %eax, 100(%rsp)
        jne       ..B1.31
..B1.26:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     48(%rsp)
        movl      48(%rsp), %eax
        cmpl      $-16382, %eax
        jl        ..B1.31
..B1.27:
        fstp      %st(0)
        fstp      %st(0)
        addl      $16383, %eax
        movzwl    8+.L_2il0floatpacket.0(%rip), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 72(%rsp)
        testb     %r8b, %r8b
        je        ..B1.29
..B1.28:
        fldcw     82(%rsp)
..B1.29:
        fldt      64(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.30:
        fldt      96(%rsp)
        lea       _TWO_63H(%rip), %rax
        fldl      (%rax)
..B1.31:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _TWO_48H(%rip), %rcx
        fmul      %st(2), %st
        lea       SC2_BIAS(%rip), %r9
        movzwl    72(%rsp), %r11d
        fadd      %st(1), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        andl      $-32768, %r11d
        movl      48(%rsp), %edx
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.2(%rip)
        fmulp     %st, %st(1)
        movsbq    %dl, %rax
        fsubr     %st(1), %st
        fldl      (%rcx)
        movl      %eax, %r10d
        fld       %st(0)
        negl      %r10d
        addl      %edx, %r10d
        lea       48+_P(%rip), %rcx
        shrl      $8, %r10d
        addl      $4196735, %edx
        addl      (%r9,%rdi,4), %r10d
        lea       80+_P(%rip), %rdi
        lea       64+_P(%rip), %r9
        andl      $32767, %r10d
        orl       %r10d, %r11d
        lea       32+_P(%rip), %r10
        movw      %r11w, 72(%rsp)
        lea       _P(%rip), %r11
        shlq      $4, %rax
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        cmpl      $3070, %edx
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       16+_P(%rip), %rdi
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       96+_P(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rcx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldl      2048(%rdi,%rax)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(2)
        fadd      %st, %st(3)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fmull     2056(%rdi,%rax)
        faddp     %st, %st(1)
        faddl     2056(%rdi,%rax)
        ja        ..B1.34
..B1.32:
        lea       _exp2but(%rip), %rax
        fldt      (%rax)
        fcomip    %st(3), %st
        jbe       ..B1.34
..B1.33:
        fstp      %st(2)
        fld       %st(0)
        lea       _TWO_12H(%rip), %rax
        lea       _SC2(%rip), %rdx
        fadd      %st(2), %st
        fldl      (%rax)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rsi,%rdx)
        fldt      64(%rsp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.35
..B1.34:
        fstp      %st(2)
        fldt      64(%rsp)
        fxch      %st(2)
        faddp     %st, %st(1)
        lea       _SC2(%rip), %rax
        fmulp     %st, %st(1)
        fldt      (%rsi,%rax)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
..B1.35:
        fldt      32(%rsp)
        lea       _minnorm(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        ja        ..B1.48
..B1.36:
        testb     %r8b, %r8b
        je        ..B1.38
..B1.37:
        fldt      16(%rsp)
        fstpt     48(%rsp)
..B1.58:
        fldcw     82(%rsp)
..B1.38:
        fldt      32(%rsp)
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
        movzwl    104(%rsp), %ecx
        movb      $1, %r8b
        andl      $32767, %ecx
..B1.43:
        cmpl      $32767, %ecx
        je        ..B1.49
..B1.44:
        testq     %rdi, %rdi
        je        ..B1.46
..B1.45:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.46:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
..B1.47:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.48:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.36
..B1.49:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.52
..B1.50:
        cmpl      $0, 96(%rsp)
        jne       ..B1.52
..B1.51:
        lea       _inf_zeros(%rip), %rax
        fldl      (%rax,%rdi,8)
        fstpt     16(%rsp)
        jmp       ..B1.53
..B1.52:
        fldt      96(%rsp)
        fstpt     16(%rsp)
..B1.53:
        testb     %r8b, %r8b
        je        ..B1.55
..B1.54:
        fldcw     82(%rsp)
..B1.55:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	exp2l,@function
	.size	exp2l,.-exp2l
	.data
# -- End  exp2l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x07,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf7,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
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
SC2_BIAS:
	.long	8191
	.long	24575
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_TWO_12H:
	.long	0
	.long	1085800448
	.type	_TWO_12H,@object
	.size	_TWO_12H,8
	.align 4
_inf_zeros:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,16
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16397
	.word	0
	.word	0
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	32894
	.word	16397
	.word	0
	.word	0
	.word	0
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_P1:
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30019
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	31705
	.word	37440
	.word	18104
	.word	58200
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	59309
	.word	6415
	.word	23422
	.word	40341
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,64
	.align 2
_P:
	.word	12039
	.word	52575
	.word	36475
	.word	49086
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	30020
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	64966
	.word	9463
	.word	18104
	.word	58200
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	46891
	.word	53866
	.word	23421
	.word	40341
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	12114
	.word	63546
	.word	699
	.word	44740
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	63250
	.word	29232
	.word	35994
	.word	41348
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	45426
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,112
	.align 2
_exp2but:
	.word	0
	.word	0
	.word	0
	.word	65528
	.word	49164
	.word	0
	.word	0
	.word	0
	.type	_exp2but,@object
	.size	_exp2but,16
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
_minnorm:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.type	_minnorm,@object
	.size	_minnorm,16
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
