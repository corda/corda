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
	.file "libm_logl.c"
	.text
..TXTST0:
# -- Begin  __libm_logl
	.text
       .align    16,0x90
	.globl __libm_logl
__libm_logl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___libm_logl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %sil, %sil
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzbl    57(%rsp), %r8d
        andl      $128, %r8d
        shrl      $7, %r8d
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        movl      %r8d, %edi
        andl      $32767, %edx
        shll      $15, %edi
        orl       %edx, %edi
        shll      $16, %edi
        movzwl    54(%rsp), %ecx
        orl       %ecx, %edi
        cmpl      $2147450880, %edi
        jae       ..B1.31
..B1.4:
        lea       -1073674240(%rdi), %ecx
        cmpl      $36864, %ecx
        ja        ..B1.21
..B1.5:
        addl      $-1073676287, %edi
        cmpl      $32769, %edi
        ja        ..B1.14
..B1.6:
        cmpl      $16383, %edx
        je        ..B1.40
..B1.7:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.11
..B1.8:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.9:
        fldcw     32(%rsp)
..B1.10:
        movb      $1, %sil
..B1.11:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        lea       48+_Q1(%rip), %rdx
        lea       16+_Q1(%rip), %rcx
        lea       32+_Q1(%rip), %rdi
        lea       _Q1(%rip), %r8
        testb     %sil, %sil
        faddl     (%rax)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%rdx)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fmul      %st(1), %st
        fldt      (%rdi)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.13
..B1.12:
        fldcw     34(%rsp)
..B1.13:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.14:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.18
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.16:
        fldcw     32(%rsp)
..B1.17:
        movb      $1, %sil
..B1.18:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        lea       _TWO_52H(%rip), %rdx
        lea       208+_Q2(%rip), %rcx
        lea       176+_Q2(%rip), %rdi
        lea       144+_Q2(%rip), %r8
        lea       112+_Q2(%rip), %r9
        lea       80+_Q2(%rip), %r10
        lea       48+_Q2(%rip), %r11
        testb     %sil, %sil
        fldl      (%rdx)
        lea       192+_Q2(%rip), %rdx
        fldt      (%rcx)
        lea       160+_Q2(%rip), %rcx
        fldt      (%rdi)
        lea       128+_Q2(%rip), %rdi
        fxch      %st(3)
        faddl     (%rax)
        lea       16+_Q2(%rip), %rax
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fld       %st(1)
        fsub      %st(4), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(6)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fxch      %st(2)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r8)
        lea       96+_Q2(%rip), %r8
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      (%r9)
        lea       64+_Q2(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r10)
        lea       32+_Q2(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r11)
        lea       _Q2(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rax)
        lea       240+_Q2(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rdx)
        lea       224+_Q2(%rip), %rdx
        fmul      %st(6), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      (%rdi)
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
        fmulp     %st, %st(6)
        fldt      (%r11)
        faddp     %st, %st(6)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      (%rax)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(1)
        fld       %st(1)
        fldt      (%rdx)
        fld       %st(0)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.20
..B1.19:
        fldcw     34(%rsp)
..B1.20:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.21:
        movzwl    34(%rsp), %edi
        movl      %edi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.25
..B1.22:
        orl       $-64768, %edi
        movw      %di, 32(%rsp)
..B1.23:
        fldcw     32(%rsp)
..B1.24:
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        movb      $1, %sil
        andl      $32767, %edx
..B1.25:
        movl      $-16383, %ecx
        testl     %edx, %edx
        jne       ..B1.28
..B1.26:
        fldt      48(%rsp)
        lea       _TWO_75(%rip), %rcx
        lea       _zeros(%rip), %rax
        fmull     (%rcx)
        movl      $-16458, %ecx
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldl      (%rax)
        movsd     (%rax), %xmm0
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.27
        je        ..B1.42
..B1.27:
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
..B1.28:
        lea       _TWO_32H(%rip), %rdi
        andl      $-32768, %eax
        orl       $-49153, %eax
        addl      %edx, %ecx
        movw      %ax, 56(%rsp)
        lea       8+_ones(%rip), %r9
        fldt      48(%rsp)
        lea       48+_P(%rip), %r10
        fld       %st(0)
        lea       16+_P(%rip), %r11
        fldl      (%rdi)
        lea       _P(%rip), %rdi
        movl      52(%rsp), %edx
        fadd      %st, %st(1)
        shrl      $23, %edx
        fsubrp    %st, %st(1)
        movzbl    %dl, %eax
        lea       64+_P(%rip), %rdx
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r8
        fsubr     %st, %st(1)
        flds      (%r8,%rax,4)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddl     (%r9)
        fld       %st(0)
        movl      %ecx, 16(%rsp)
        lea       80+_P(%rip), %rcx
        shlq      $4, %rax
        fadd      %st(2), %st
        fld       %st(0)
        testb     %sil, %sil
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fildl     16(%rsp)
        fldt      (%rcx)
        lea       32+_P(%rip), %rcx
        fmul      %st(3), %st
        fldt      (%r10)
        movq      __libm_logl_table_256@GOTPCREL(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmulp     %st, %st(2)
        fldt      (%rdx)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(2)
        faddl     8(%r8,%rax)
        faddp     %st, %st(3)
        faddl     (%rax,%r8)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.30
..B1.29:
        fldcw     34(%rsp)
..B1.30:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.31:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.35
..B1.32:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.33:
        fldcw     32(%rsp)
..B1.34:
        movzbl    57(%rsp), %r8d
        movb      $1, %sil
        andl      $128, %r8d
        shrl      $7, %r8d
..B1.35:
        testl     %r8d, %r8d
        jne       ..B1.45
..B1.36:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.37:
        testb     %sil, %sil
        je        ..B1.39
..B1.38:
        fldcw     34(%rsp)
..B1.39:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.40:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.7
..B1.41:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.42:
        lea       8+_ones(%rip), %rax
        testb     %sil, %sil
        movsd     (%rax), %xmm1
        divsd     %xmm0, %xmm1
        je        ..B1.44
..B1.43:
        fldcw     34(%rsp)
..B1.44:
        movsd     %xmm1, 16(%rsp)
        fldl      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.45:
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.47
..B1.46:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.36
        jmp       ..B1.50
..B1.47:
        testl     %eax, %eax
        jne       ..B1.50
..B1.48:
        cmpq      $0, 48(%rsp)
        jne       ..B1.50
..B1.49:
        lea       8+_ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        divsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.37
..B1.50:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.37
        .align    16,0x90
	.cfi_endproc
	.type	__libm_logl,@function
	.size	__libm_logl,.-__libm_logl
	.data
# -- End  __libm_logl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_Q1:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	5458
	.word	43692
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	44682
	.word	49156
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	63914
	.word	24446
	.word	52434
	.word	52426
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q2:
	.word	58950
	.word	11767
	.word	59949
	.word	40744
	.word	49076
	.word	0
	.word	0
	.word	0
	.word	44486
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	65514
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	49718
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	36693
	.word	43691
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	41156
	.word	18852
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	14101
	.word	65290
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	21744
	.word	48151
	.word	14557
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	57454
	.word	983
	.word	52353
	.word	52428
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	54021
	.word	59409
	.word	39452
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	35608
	.word	4659
	.word	11214
	.word	43692
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	35524
	.word	31332
	.word	8749
	.word	40326
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	54832
	.word	20873
	.word	17674
	.word	36915
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	48567
	.word	9481
	.word	46911
	.word	55870
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,256
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
