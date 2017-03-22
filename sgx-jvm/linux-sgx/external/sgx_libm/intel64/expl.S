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
	.file "expl.c"
	.text
..TXTST0:
# -- Begin  expl
	.text
       .align    16,0x90
	.globl expl
expl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_expl.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %dil, %dil
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movb      105(%rsp), %al
        lea       _ranges(%rip), %r8
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %esi
        movq      %rsi, %rcx
        shlq      $4, %rcx
        movzwl    104(%rsp), %eax
        andl      $32767, %eax
        movzwl    8(%rcx,%r8), %edx
        andl      $32767, %edx
        cmpl      %edx, %eax
        jl        ..B1.8
..B1.4:
        jne       ..B1.31
..B1.5:
        movl      100(%rsp), %edx
        cmpl      4(%rcx,%r8), %edx
        jb        ..B1.8
..B1.6:
        jne       ..B1.31
..B1.7:
        movl      96(%rsp), %edx
        cmpl      (%rcx,%r8), %edx
        jae       ..B1.31
..B1.8:
        movzwl    82(%rsp), %r8d
        cmpl      $16363, %eax
        jge       ..B1.19
..B1.9:
        movl      %r8d, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.13
..B1.10:
        orl       $-64768, %r8d
        movw      %r8w, 80(%rsp)
..B1.11:
        fldcw     80(%rsp)
..B1.12:
        movzwl    104(%rsp), %eax
        movb      $1, %dil
        andl      $32767, %eax
..B1.13:
        fldt      96(%rsp)
        cmpl      $16316, %eax
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
        lea       16+_P1(%rip), %rax
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _P1(%rip), %rdx
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.16:
        testb     %dil, %dil
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
        movl      %r8d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.23
..B1.20:
        orl       $-64768, %r8d
        movw      %r8w, 80(%rsp)
..B1.21:
        fldcw     80(%rsp)
..B1.22:
        movb      $1, %dil
..B1.23:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %r8
        fstpt     48(%rsp)
        lea       _TWO_32H(%rip), %r9
        fldt      96(%rsp)
        lea       SC2_BIAS(%rip), %r10
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        fldl      (%r8)
        movzwl    8+.L_2il0floatpacket.0(%rip), %r8d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        andl      $-32768, %r8d
        movl      64(%rsp), %edx
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(1), %st
        movsbq    %dl, %rax
        fsubr     %st(2), %st
        fld       %st(0)
        movl      %eax, %r11d
        fldt      .L_2il0floatpacket.3(%rip)
        negl      %r11d
        fmulp     %st, %st(3)
        addl      %edx, %r11d
        shrl      $8, %r11d
        addl      $4196735, %edx
        addl      (%r10,%rsi,4), %r11d
        lea       64+_P(%rip), %rsi
        andl      $32767, %r11d
        lea       16+_P(%rip), %r10
        orl       %r11d, %r8d
        fsub      %st(2), %st
        fldl      (%r9)
        lea       48+_P(%rip), %r9
        movw      %r8w, 56(%rsp)
        lea       _P(%rip), %r8
        shlq      $4, %rax
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        cmpl      $3070, %edx
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r11
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       32+_P(%rip), %rsi
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      2048(%r11,%rax)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmull     2056(%r11,%rax)
        faddp     %st, %st(2)
        faddl     2056(%r11,%rax)
        faddp     %st, %st(1)
        ja        ..B1.26
..B1.24:
        lea       _expbut(%rip), %rax
        fldt      (%rax)
        fcomip    %st(3), %st
        jbe       ..B1.26
..B1.25:
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
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rcx,%rdx)
        fldt      48(%rsp)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.27
..B1.26:
        fstp      %st(2)
        lea       _SC2(%rip), %rax
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rcx,%rax)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
..B1.27:
        fldt      32(%rsp)
        lea       _minnorm(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        ja        ..B1.42
..B1.28:
        testb     %dil, %dil
        je        ..B1.30
..B1.29:
        fldt      16(%rsp)
        fstpt     64(%rsp)
..B1.49:
        fldcw     82(%rsp)
..B1.30:
        fldt      32(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.31:
        movzwl    82(%rsp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.35
..B1.32:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.33:
        fldcw     80(%rsp)
..B1.34:
        movzwl    104(%rsp), %eax
        movb      $1, %dil
        andl      $32767, %eax
..B1.35:
        cmpl      $32767, %eax
        je        ..B1.43
..B1.36:
        testq     %rsi, %rsi
        je        ..B1.38
..B1.37:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        jmp       ..B1.39
..B1.38:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
..B1.39:
        testb     %dil, %dil
        je        ..B1.41
..B1.40:
        fldcw     82(%rsp)
..B1.41:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.42:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.28
..B1.43:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.46
..B1.44:
        cmpl      $0, 96(%rsp)
        jne       ..B1.46
..B1.45:
        lea       _inf_zeros(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     16(%rsp)
        jmp       ..B1.39
..B1.46:
        fldt      96(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.39
        .align    16,0x90
	.cfi_endproc
	.type	expl,@function
	.size	expl,.-expl
	.data
# -- End  expl
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
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	488
	.word	27074
	.word	50247
	.word	45600
	.word	16396
	.word	0
	.word	0
	.word	0
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_P1:
	.word	43691
	.word	10
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30584
	.word	43703
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,32
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
_expbut:
	.word	60207
	.word	4624
	.word	35943
	.word	45420
	.word	49164
	.word	0
	.word	0
	.word	0
	.type	_expbut,@object
	.size	_expbut,16
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
