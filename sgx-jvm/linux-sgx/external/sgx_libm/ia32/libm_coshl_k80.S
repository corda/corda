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
	.file "libm_coshl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_coshl_k80
	.text
       .align    16,0x90
	.hidden __libm_coshl_k80
	.globl __libm_coshl_k80
__libm_coshl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $68, %esp
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movl      20(%ebp), %ebx
        cmpl      $16405, %eax
        call      ..L2
..L2:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%esi), %esi
        jge       ..B1.17
..B1.2:
        cmpl      $16382, %eax
        jge       ..B1.13
..B1.3:
        cmpl      $16378, %eax
        jge       ..B1.11
..B1.4:
        fldt      .L_2il0floatpacket.1@GOTOFF(%esi)
        cmpl      $16372, %eax
        jge       ..B1.10
..B1.5:
        cmpl      $16365, %eax
        jge       ..B1.9
..B1.6:
        fstpt     (%ebx)
        cmpl      $16308, %eax
        jge       ..B1.8
..B1.7:
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        fstpt     12(%ebx)
        fldt      12(%ebx)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        fldt      8(%ebp)
        fldt      _Q3@GOTOFF(%esi)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        fldt      8(%ebp)
        fmul      %st(0), %st
        fxch      %st(1)
        fstpt     (%ebx)
        fldt      12+_Q2@GOTOFF(%esi)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        fldt      8(%ebp)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      36+_Q1@GOTOFF(%esi)
        fmul      %st(2), %st
        fldt      12+_Q1@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_Q1@GOTOFF(%esi)
        fmulp     %st, %st(3)
        fldt      _Q1@GOTOFF(%esi)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        fldt      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      _TWO_48H@GOTOFF(%esi)
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     56(%esp)
        fldt      56(%esp)
        fsub      %st(4), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(6)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st(0), %st
        fldt      72+_Q@GOTOFF(%esi)
        fmul      %st(3), %st
        fldt      48+_Q@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_Q@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      84+_Q@GOTOFF(%esi)
        fmul      %st(4), %st
        fldt      60+_Q@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      36+_Q@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fld       %st(5)
        fldt      12+_Q@GOTOFF(%esi)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      108+_Q@GOTOFF(%esi)
        fld       %st(0)
        fmul      %st(7), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fldt      96+_Q@GOTOFF(%esi)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fmulp     %st, %st(4)
        fld       %st(1)
        fadd      %st(4), %st
        fadd      %st(3), %st
        fstpt     56(%esp)
        fldt      56(%esp)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%ebx)
        fldt      .L_2il0floatpacket.1@GOTOFF(%esi)
        faddp     %st, %st(1)
        fstpt     (%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
..B1.12:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%esi)
        fldt      _KLN2@GOTOFF(%esi)
        fldt      _LN2K@GOTOFF(%esi)
        fldt      12+_LN2K@GOTOFF(%esi)
        fldl      _TWO_32@GOTOFF(%esi)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fxch      %st(5)
        fmull     ones@GOTOFF(%esi,%eax,8)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(4), %st
        fstpt     56(%esp)
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      56(%esp)
        movl      56(%esp), %eax
        fsubp     %st, %st(4)
        fxch      %st(1)
        fmul      %st(3), %st
        movsbl    %al, %ecx
        fsubrp    %st, %st(1)
        fld       %st(0)
        subl      %ecx, %eax
        fxch      %st(2)
        fmulp     %st, %st(3)
        movzwl    16(%ebp), %edx
        fxch      %st(1)
        fsub      %st(2), %st
        fldl      _TWO_32P@GOTOFF(%esi)
        andl      $32767, %edx
        fmul      %st(1), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        sarl      $8, %eax
        fsubrp    %st, %st(3)
        fld       %st(2)
        cmpl      $16387, %edx
        fxch      %st(1)
        fsub      %st(3), %st
        fsubp     %st, %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%esi)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _P@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%esi)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%esi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        jle       ..B1.15
..B1.14:
        shll      $4, %ecx
        decl      %eax
        movl      %eax, 48(%esp)
        faddp     %st, %st(1)
        fildl     48(%esp)
        fldl      2056+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        movzbl    17(%ebp), %eax
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(0)
        andl      $128, %eax
        shrl      $7, %eax
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      ones@GOTOFF(%esi,%eax,8)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        je        ..B1.20
..B1.16:
        fldt      .L_2il0floatpacket.1@GOTOFF(%esi)
        lea       -1(%eax), %edi
        shll      $4, %ecx
        fstpt     (%esp)
        fld       %st(0)
        movl      %edi, 48(%esp)
        movl      %eax, %edi
        negl      %edi
        fadd      %st(2), %st
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(2)
        addl      $16383, %edi
        fldl      2056+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        subl      %eax, %edi
        fldl      2048+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        negl      %ecx
        fstpt     16(%esp)
        fldt      16(%esp)
        andl      $32767, %edi
        movzwl    8+.L_2il0floatpacket.1@GOTOFF(%esi), %edx
        fadd      %st(1), %st
        fmulp     %st, %st(4)
        fld       %st(4)
        fmul      %st(1), %st
        andl      $-32768, %edx
        faddp     %st, %st(4)
        fldl      2056+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        orl       %edi, %edx
        fmul      %st, %st(2)
        fldl      2048+__libm_expl_table_256@GOTOFF(%esi,%ecx)
        fld       %st(0)
        movw      %dx, 8(%esp)
        fadd      %st(2), %st
        fmulp     %st, %st(5)
        movzbl    17(%ebp), %eax
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fstpt     32(%esp)
        fld       %st(4)
        fxch      %st(2)
        fmul      %st, %st(5)
        andl      $128, %eax
        fldt      16(%esp)
        fmul      %st, %st(3)
        fld       %st(0)
        shrl      $7, %eax
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
        fld       %st(0)
        fldt      32(%esp)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmul      %st, %st(5)
        fxch      %st(1)
        fadd      %st(5), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmulp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      ones@GOTOFF(%esi,%eax,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%ebx)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fildl     48(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        cmpl      $32767, %eax
        je        ..B1.23
..B1.18:
        fldt      _infs@GOTOFF(%esi)
        fstpt     (%ebx)
        fldt      (%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        fstpt     12(%ebx)
..B1.19:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        movl      12(%ebp), %edx
        cmpl      $-782985146, %edx
        ja        ..B1.14
..B1.21:
        jne       ..B1.16
..B1.22:
        cmpl      $1501741449, 8(%ebp)
        ja        ..B1.14
        jmp       ..B1.16
..B1.23:
        fldt      8(%ebp)
        fmul      %st(0), %st
        fstpt     (%ebx)
        fldt      (%ebx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%esi)
        fstpt     12(%ebx)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	__libm_coshl_k80,@function
	.size	__libm_coshl_k80,.-__libm_coshl_k80
	.data
# -- End  __libm_coshl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
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
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	62430
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	22894
	.word	21845
	.word	43733
	.word	43690
	.word	16378
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	65531
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	7054
	.word	43693
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	25016
	.word	58228
	.word	24755
	.word	46603
	.word	16373
	.word	0
	.word	57397
	.word	35800
	.word	53031
	.word	53262
	.word	16367
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	18360
	.word	43637
	.word	28980
	.word	40373
	.word	49076
	.word	0
	.word	16302
	.word	43691
	.word	43690
	.word	43690
	.word	16362
	.word	0
	.word	46125
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	29965
	.word	3335
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	54950
	.word	46751
	.word	32187
	.word	37874
	.word	16361
	.word	0
	.word	30733
	.word	25775
	.word	51110
	.word	36726
	.word	16354
	.word	0
	.word	49850
	.word	450
	.word	9929
	.word	51659
	.word	16346
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
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16378
	.word	0
	.type	_Q,@object
	.size	_Q,120
	.align 2
_KLN2:
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	16391
	.word	0
	.type	_KLN2,@object
	.size	_KLN2,12
	.align 2
_LN2K:
	.word	0
	.word	0
	.word	6134
	.word	45426
	.word	16374
	.word	0
	.word	55756
	.word	58609
	.word	48341
	.word	59623
	.word	16343
	.word	0
	.type	_LN2K,@object
	.size	_LN2K,24
	.align 2
_P:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.type	_P,@object
	.size	_P,60
	.align 2
_infs:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.type	_infs,@object
	.size	_infs,12
	.data
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
