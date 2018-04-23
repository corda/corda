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
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzbl    17(%ebp), %edi
        andl      $128, %edi
        shrl      $7, %edi
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      %edi, %ebx
        andl      $32767, %eax
        shll      $15, %ebx
        movl      12(%ebp), %esi
        orl       %eax, %ebx
        movl      %esi, %ecx
        shll      $16, %ebx
        shrl      $16, %ecx
        orl       %ecx, %ebx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $2147450880, %ebx
        jae       ..B1.32
..B1.4:
        lea       -1073674240(%ebx), %edi
        cmpl      $36864, %edi
        ja        ..B1.21
..B1.5:
        addl      $-1073676287, %ebx
        cmpl      $32769, %ebx
        ja        ..B1.14
..B1.6:
        cmpl      $16383, %eax
        je        ..B1.42
..B1.7:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.41
..B1.8:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movl      $1, %eax
..B1.11:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      36+_Q1@GOTOFF(%ecx)
        fldt      12+_Q1@GOTOFF(%ecx)
        fldt      24+_Q1@GOTOFF(%ecx)
        fldt      _Q1@GOTOFF(%ecx)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fadd      %st(1), %st
        fstpt     (%esp)
        fstpt     8(%ebp)
        je        ..B1.13
..B1.12:
        fldcw     14(%esp)
..B1.13:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        movl      $1, %eax
..B1.18:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_52H@GOTOFF(%ecx)
        fldt      156+_Q2@GOTOFF(%ecx)
        fldt      132+_Q2@GOTOFF(%ecx)
        fxch      %st(3)
        faddl     8+_ones@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fld       %st(1)
        fsub      %st(4), %st
        fstpt     16(%esp)
        fldt      16(%esp)
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
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      108+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      84+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      60+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      36+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      12+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      144+_Q2@GOTOFF(%ecx)
        fmul      %st(6), %st
        fldt      120+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      96+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      72+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      48+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      24+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(6)
        fldt      _Q2@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      180+_Q2@GOTOFF(%ecx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(1)
        fld       %st(1)
        fldt      168+_Q2@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fldt      16(%esp)
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
        fstpt     (%esp)
        je        ..B1.20
..B1.19:
        fldcw     14(%esp)
..B1.20:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        movzwl    14(%esp), %esi
        movl      %esi, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.49
..B1.22:
        orl       $-64768, %esi
        movw      %si, 12(%esp)
..B1.23:
        fldcw     12(%esp)
..B1.24:
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      $1, %esi
        andl      $32767, %eax
..B1.25:
        testl     %eax, %eax
        jne       ..B1.28
..B1.26:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _zeros@GOTOFF(%ecx)
        fxch      %st(1)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.27
        je        ..B1.46
..B1.27:
        fstp      %st(0)
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      $-16458, %ebx
        andl      $32767, %eax
        jmp       ..B1.29
..B1.28:
        movl      $-16383, %ebx
..B1.29:
        fldl      _TWO_32H@GOTOFF(%ecx)
        andl      $-32768, %edx
        fldt      60+_P@GOTOFF(%ecx)
        orl       $-49153, %edx
        fldt      36+_P@GOTOFF(%ecx)
        addl      %eax, %ebx
        movw      %dx, 16(%ebp)
        fldt      8(%ebp)
        fld       %st(0)
        movl      12(%ebp), %eax
        fadd      %st(4), %st
        shrl      $23, %eax
        fsubp     %st, %st(4)
        movzbl    %al, %eax
        fsub      %st(3), %st
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%eax,4)
        fmul      %st, %st(4)
        fmulp     %st, %st(1)
        fxch      %st(3)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        movl      %ebx, 16(%esp)
        fadd      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        shll      $4, %eax
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        testl     %esi, %esi
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fildl     16(%esp)
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     8+__libm_logl_table_256@GOTOFF(%ecx,%eax)
        faddp     %st, %st(3)
        faddl     __libm_logl_table_256@GOTOFF(%eax,%ecx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
..B1.31:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.58
..B1.33:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.34:
        fldcw     12(%esp)
..B1.35:
        movzbl    17(%ebp), %edi
        movl      $1, %edx
        andl      $128, %edi
        shrl      $7, %edi
..B1.36:
        testl     %edi, %edi
        jne       ..B1.50
..B1.37:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.38:
        testl     %edx, %edx
        je        ..B1.40
..B1.39:
        fldcw     14(%esp)
..B1.40:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        xorl      %eax, %eax
        jmp       ..B1.11
..B1.42:
        cmpl      $-2147483648, %esi
        jne       ..B1.7
..B1.43:
        cmpl      $0, 8(%ebp)
        jne       ..B1.7
..B1.44:
        fldl      _zeros@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        xorl      %eax, %eax
        jmp       ..B1.18
..B1.46:
        fldl      8+_ones@GOTOFF(%ecx)
        testl     %esi, %esi
        fdivp     %st, %st(1)
        fstpl     (%esp)
        je        ..B1.48
..B1.47:
        fldcw     14(%esp)
..B1.48:
        fldl      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.49:
        xorl      %esi, %esi
        jmp       ..B1.25
..B1.50:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.53
..B1.51:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.37
..B1.52:
        cmpl      $0, 8(%ebp)
        jne       ..B1.37
        jmp       ..B1.57
..B1.53:
        testl     %eax, %eax
        jne       ..B1.57
..B1.54:
        cmpl      $0, 12(%ebp)
        jne       ..B1.57
..B1.55:
        cmpl      $0, 8(%ebp)
        jne       ..B1.57
..B1.56:
        fldl      8+_ones@GOTOFF(%ecx)
        fdivl     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.38
..B1.57:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.38
..B1.58:
        xorl      %edx, %edx
        jmp       ..B1.36
        .align    16,0x90
	.type	__libm_logl,@function
	.size	__libm_logl,.-__libm_logl
	.data
# -- End  __libm_logl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
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
	.word	5458
	.word	43692
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	44682
	.word	49156
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	63914
	.word	24446
	.word	52434
	.word	52426
	.word	16380
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q2:
	.word	58950
	.word	11767
	.word	59949
	.word	40744
	.word	49076
	.word	0
	.word	44486
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	65514
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	49718
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	36693
	.word	43691
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	41156
	.word	18852
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	14101
	.word	65290
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	21744
	.word	48151
	.word	14557
	.word	58254
	.word	16379
	.word	0
	.word	57454
	.word	983
	.word	52353
	.word	52428
	.word	49147
	.word	0
	.word	54021
	.word	59409
	.word	39452
	.word	47662
	.word	16379
	.word	0
	.word	35608
	.word	4659
	.word	11214
	.word	43692
	.word	49147
	.word	0
	.word	35524
	.word	31332
	.word	8749
	.word	40326
	.word	16379
	.word	0
	.word	54832
	.word	20873
	.word	17674
	.word	36915
	.word	49147
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
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16381
	.word	0
	.type	_Q2,@object
	.size	_Q2,192
	.align 2
_P:
	.word	65
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	44010
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	58670
	.word	61414
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	24905
	.word	46201
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	57053
	.word	27587
	.word	64478
	.word	43690
	.word	49148
	.word	0
	.word	41508
	.word	48914
	.word	32459
	.word	37449
	.word	16380
	.word	0
	.type	_P,@object
	.size	_P,72
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.section .note.GNU-stack, ""
# End
