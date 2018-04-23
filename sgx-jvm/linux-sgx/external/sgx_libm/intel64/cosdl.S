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
	.file "cosdl.c"
	.text
..TXTST0:
# -- Begin  cosdl
	.text
       .align    16,0x90
	.globl cosdl
cosdl:
# parameter 1: 144 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cosdl.1:
..L2:

        subq      $136, %rsp
	.cfi_def_cfa_offset 144
        xorb      %r8b, %r8b
..B1.2:
        fnstcw    122(%rsp)
..B1.3:
        movzwl    122(%rsp), %edx
        movl      %edx, %eax
        andl      $3840, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        andl      $-3841, %edx
        orl       $-64768, %edx
        movw      %dx, 120(%rsp)
..B1.5:
        fldcw     120(%rsp)
..B1.6:
        movb      $1, %r8b
..B1.7:
        fldt      144(%rsp)
        lea       ones(%rip), %rdi
        movzwl    152(%rsp), %eax
        movl      %eax, %esi
        shrl      $15, %eax
        andl      $32767, %esi
        cmpl      $32767, %esi
        fmuls     (%rdi,%rax,4)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        je        ..B1.26
..B1.8:
        fldt      .L_2il0floatpacket.0(%rip)
        fucomip   %st(1), %st
        jp        ..B1.9
        je        ..B1.25
..B1.9:
        cmpl      $10783, %esi
        jge       ..B1.11
..B1.10:
        fldt      .L_2il0floatpacket.1(%rip)
        fsubp     %st, %st(1)
        fstpt     104(%rsp)
        jmp       ..B1.21
..B1.11:
        cmpl      $16446, %esi
        jge       ..B1.16
..B1.12:
        fldt      .L_2il0floatpacket.2(%rip)
        lea       _Rcp90(%rip), %rax
        fldt      .L_2il0floatpacket.3(%rip)
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      (%rax)
        fmulp     %st, %st(4)
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        movl      80(%rsp), %ecx
        fsubp     %st, %st(3)
        fmulp     %st, %st(2)
        incl      %ecx
        fldt      144(%rsp)
        movl      %ecx, %edx
        andl      $2, %edx
        fsubp     %st, %st(2)
        shrl      $1, %edx
        movss     (%rdi,%rdx,4), %xmm0
        fucomip   %st(1), %st
        jp        ..B1.13
        je        ..B1.24
..B1.13:
        fldt      .L_2il0floatpacket.4(%rip)
        lea       _TWO_53H(%rip), %rax
        fmul      %st(1), %st
        fld       %st(1)
        movss     %xmm0, (%rsp)
        testb     $1, %cl
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(2)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fld       %st(1)
        fld       %st(4)
        fldl      (%rax)
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fld       %st(5)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fstpt     64(%rsp)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        flds      (%rsp)
        fstps     96(%rsp)
        je        ..B1.15
..B1.14:
        fstp      %st(6)
        fstp      %st(2)
        fld       %st(2)
        lea       112+_cosdl_poly_coeff(%rip), %rax
        fmul      %st(3), %st
        lea       80+_cosdl_poly_coeff(%rip), %rdx
        fmul      %st, %st(1)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(4)
        fstpt     (%rsp)
        fldt      (%rsp)
        fld       %st(3)
        lea       96+_cosdl_poly_coeff(%rip), %rsi
        lea       48+_cosdl_poly_coeff(%rip), %rcx
        lea       64+_cosdl_poly_coeff(%rip), %rdi
        lea       32+_cosdl_poly_coeff(%rip), %r9
        lea       24+_cosdl_mp_poly_coeff(%rip), %r10
        lea       8+_cosdl_mp_poly_coeff(%rip), %r11
        fmul      %st(7), %st
        fxch      %st(1)
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fldt      (%rax)
        fmul      %st(5), %st
        fldt      (%rdx)
        lea       16+_cosdl_mp_poly_coeff(%rip), %rax
        lea       _cosdl_mp_poly_coeff(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        fmul      %st(6), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      (%r10)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fldl      (%r11)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      (%rdx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%rsp)
        fmulp     %st, %st(2)
        fld       %st(1)
        fldt      16(%rsp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        flds      96(%rsp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.1(%rip)
        fadd      %st(4), %st
        fmul      %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fsubp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     104(%rsp)
        jmp       ..B1.21
..B1.15:
        fld       %st(6)
        lea       128+_sindl_poly_coeff(%rip), %rax
        fmul      %st(2), %st
        lea       96+_sindl_poly_coeff(%rip), %rdx
        fstpt     32(%rsp)
        fld       %st(4)
        fmul      %st(4), %st
        lea       64+_sindl_poly_coeff(%rip), %rcx
        fmul      %st, %st(3)
        lea       32+_sindl_poly_coeff(%rip), %rsi
        fxch      %st(6)
        fmul      %st(7), %st
        fxch      %st(2)
        fstpt     16(%rsp)
        lea       112+_sindl_poly_coeff(%rip), %rdi
        fxch      %st(3)
        fstpt     (%rsp)
        lea       80+_sindl_poly_coeff(%rip), %r9
        fldt      64(%rsp)
        lea       48+_sindl_poly_coeff(%rip), %r10
        lea       24+_sindl_mp_poly_coeff(%rip), %r11
        fmul      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(4)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       8+_sindl_mp_poly_coeff(%rip), %rax
        lea       16+_sindl_mp_poly_coeff(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       _sindl_mp_poly_coeff(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rdi)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmul      %st, %st(1)
        fldl      (%r11)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      (%rax)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldl      (%rdx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      (%rcx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%rsp)
        fmulp     %st, %st(2)
        fld       %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        flds      96(%rsp)
        fld       %st(0)
        fmulp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     104(%rsp)
        jmp       ..B1.21
..B1.16:
        fstp      %st(0)
        lea       -16446(%rsi), %ecx
        cmpl      $14, %ecx
        jle       ..B1.18
..B1.17:
        addl      $-16449, %esi
        movl      $715827883, %eax
        imull     %esi
        movl      %esi, %ecx
        sarl      $1, %edx
        sarl      $31, %ecx
        subl      %ecx, %edx
        lea       (,%rdx,8), %r9d
        lea       (%r9,%rdx,4), %r10d
        subl      %r10d, %esi
        lea       3(%rsi), %ecx
..B1.18:
        movl      148(%rsp), %r11d
        movl      %r11d, %esi
        shll      $8, %esi
        movl      $381774871, %eax
        movl      %esi, %r9d
        andl      $-16777216, %r11d
        shrl      $3, %r9d
        movl      %r11d, %r10d
        mull      %r9d
        shrl      $19, %r11d
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %esi
        movl      $381774871, %eax
        mull      %r11d
        shrl      $16, %r10d
        movl      $381774871, %eax
        shrl      $2, %edx
        imull     $-360, %edx, %r11d
        addl      %r11d, %r10d
        movl      144(%rsp), %r9d
        addl      %r10d, %esi
        movl      %r9d, %r10d
        shrl      $3, %r10d
        mull      %r10d
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %r9d
        movl      $-1240768329, %eax
        addl      %r9d, %esi
        shll      %cl, %esi
        imull     %esi
        movl      %esi, %ecx
        addl      %esi, %edx
        sarl      $8, %edx
        sarl      $31, %ecx
        subl      %ecx, %edx
        movl      $1, %ecx
        imull     $-360, %edx, %eax
        lea       (%rsi,%rax), %edx
        cmpl      $179, %edx
        lea       -180(%rsi,%rax), %eax
        movl      $3, %esi
        cmovg     %eax, %edx
        cmovg     %esi, %ecx
        cmpl      $89, %edx
        jle       ..B1.20
..B1.19:
        incl      %ecx
        addl      $-90, %edx
..B1.20:
        movl      %ecx, %eax
        andl      $2, %ecx
        andl      $1, %eax
        addl      %eax, %eax
        movq      __libm_sindl_cosdl_table@GOTPCREL(%rip), %rsi
        shrl      $1, %ecx
        lea       (%rax,%rdx,4), %edx
        movslq    %edx, %rdx
        fldl      (%rsi,%rdx,8)
        faddl     8(%rsi,%rdx,8)
        fmuls     (%rdi,%rcx,4)
        fstpt     104(%rsp)
..B1.21:
        testb     %r8b, %r8b
        je        ..B1.23
..B1.22:
        fldcw     122(%rsp)
..B1.23:
        fldt      104(%rsp)
        addq      $136, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
..B1.24:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        testb     $1, %cl
        movss     %xmm0, (%rsp)
        flds      (%rsp)
        fcmove    %st(1), %st
        fstp      %st(1)
        fstpt     104(%rsp)
        jmp       ..B1.21
..B1.25:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.1(%rip)
        fstpt     104(%rsp)
        jmp       ..B1.21
..B1.26:
        fldt      .L_2il0floatpacket.0(%rip)
        fmulp     %st, %st(1)
        fstpt     104(%rsp)
        jmp       ..B1.21
        .align    16,0x90
	.cfi_endproc
	.type	cosdl,@function
	.size	cosdl,.-cosdl
	.data
# -- End  cosdl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x3e,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xb4,0x05,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
_cosdl_poly_coeff:
	.word	52350
	.word	41213
	.word	3800
	.word	40885
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	28613
	.word	7908
	.word	35668
	.word	34008
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	19927
	.word	58728
	.word	49885
	.word	45261
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	32884
	.word	22035
	.word	23267
	.word	64541
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	36401
	.word	51897
	.word	8309
	.word	57265
	.word	49070
	.word	0
	.word	0
	.word	0
	.word	34286
	.word	2728
	.word	41564
	.word	34642
	.word	16284
	.word	0
	.word	0
	.word	0
	.word	63248
	.word	18030
	.word	35596
	.word	60796
	.word	49032
	.word	0
	.word	0
	.word	0
	.word	52149
	.word	21294
	.word	63985
	.word	40123
	.word	16245
	.word	0
	.word	0
	.word	0
	.type	_cosdl_poly_coeff,@object
	.size	_cosdl_poly_coeff,128
	.align 16
_cosdl_mp_poly_coeff:
	.long	3675529145
	.long	3206805153
	.long	2134983071
	.long	3151100167
	.long	1787026573
	.long	1043372817
	.long	205083639
	.long	988746860
	.type	_cosdl_mp_poly_coeff,@object
	.size	_cosdl_mp_poly_coeff,32
	.align 16
_sindl_poly_coeff:
	.word	51374
	.word	38121
	.word	13586
	.word	36602
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	50116
	.word	41339
	.word	4204
	.word	60892
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	33704
	.word	2155
	.word	42839
	.word	60780
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	21250
	.word	19076
	.word	27901
	.word	57780
	.word	49097
	.word	0
	.word	0
	.word	0
	.word	9076
	.word	49244
	.word	613
	.word	64083
	.word	16311
	.word	0
	.word	0
	.word	0
	.word	40572
	.word	30418
	.word	36251
	.word	46520
	.word	49061
	.word	0
	.word	0
	.word	0
	.word	3227
	.word	25505
	.word	5540
	.word	47626
	.word	16274
	.word	0
	.word	0
	.word	0
	.word	60933
	.word	3300
	.word	57416
	.word	36218
	.word	49023
	.word	0
	.word	0
	.word	0
	.word	45811
	.word	42646
	.word	37125
	.word	42185
	.word	16235
	.word	0
	.word	0
	.word	0
	.type	_sindl_poly_coeff,@object
	.size	_sindl_poly_coeff,144
	.align 16
_sindl_mp_poly_coeff:
	.long	2723323193
	.long	1066524486
	.long	2863989530
	.long	1008058840
	.long	227815288
	.long	3199056770
	.long	3752327299
	.long	3142458725
	.type	_sindl_mp_poly_coeff,@object
	.size	_sindl_mp_poly_coeff,32
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 2
_Rcp90:
	.word	46603
	.word	2912
	.word	24758
	.word	46603
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_Rcp90,@object
	.size	_Rcp90,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
