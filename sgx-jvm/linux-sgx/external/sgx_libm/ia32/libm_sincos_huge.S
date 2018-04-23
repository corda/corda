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
	.file "libm_sincos_huge.c"
	.text
..TXTST0:
# -- Begin  __libm_sincos_huge
	.text
       .align    16,0x90
	.hidden __libm_sincos_huge
	.globl __libm_sincos_huge
__libm_sincos_huge:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
# parameter 3: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        movl      16(%ebp), %eax
        movl      20(%ebp), %edx
        movl      %eax, 16(%esp)
        movl      %edx, 20(%esp)
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 40(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        fldl      8(%ebp)
        movl      12(%ebp), %edi
        movl      %edi, %eax
        andl      $2147483647, %eax
        fabs      
        shrl      $31, %edi
        movl      %eax, 24(%esp)
        cmpl      $1104150528, %eax
        fstl      8(%ebp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jae       ..B1.14
..B1.4:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        fldl      _Pi4Inv@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpl     (%esp)
        je        ..B1.52
..B1.5:
        fstp      %st(0)
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.6:
        fldcw     12(%esp)
..B1.7:
        fldl      8(%ebp)
        movl      $1, %esi
..B1.8:
        movl      (%esp), %eax
        movl      4(%esp), %edx
        movl      %edx, %ecx
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        andl      $1048575, %eax
        shrl      $20, %ecx
        orl       $1048576, %eax
        cmpl      $1094713344, %edx
        jae       ..B1.10
..B1.9:
        negl      %ecx
        addl      $19, %ecx
        shrl      %cl, %eax
        jmp       ..B1.11
..B1.10:
        movl      %ecx, %edx
        lea       13(%edx), %ecx
        negl      %edx
        addl      $19, %edx
        shll      %cl, %eax
        movl      %edx, %ecx
        movl      8(%esp), %edx
        shrl      %cl, %edx
        orl       %edx, %eax
..B1.11:
        lea       1(%eax), %edx
        movl      %edx, %ecx
        andl      $-2, %ecx
        movl      %ecx, (%esp)
        fildl     (%esp)
        cmpl      $1094713344, 24(%esp)
        jae       ..B1.13
..B1.12:
        fldl      _Pi4x3@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldl      8+_Pi4x3@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldl      16+_Pi4x3@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B1.20
..B1.13:
        fldl      _Pi4x4@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldl      8+_Pi4x4@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldl      16+_Pi4x4@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldl      24+_Pi4x4@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B1.20
..B1.14:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.60
..B1.15:
        fstp      %st(0)
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        fldl      8(%ebp)
        movl      $1, %esi
..B1.18:
        addl      $-32, %esp
        lea       56(%esp), %eax
        fstpt     (%esp)
        movl      $0, 12(%esp)
        movl      %eax, 16(%esp)
        call      __libm_reduce_pi04l
..B1.63:
        addl      $32, %esp
..B1.19:
        fldl      24(%esp)
        lea       1(%eax), %edx
        faddl     32(%esp)
..B1.20:
        movl      %edx, %ecx
        addl      $3, %eax
        shrl      $2, %ecx
        andl      $1, %ecx
        shrl      $2, %eax
        xorl      %ecx, %edi
        movl      20(%esp), %ecx
        andl      $1, %eax
        andl      $3, %ecx
        cmpl      $3, %ecx
        jne       ..B1.30
..B1.21:
        fldt      84+_SP@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testb     $2, %dl
        fmul      %st, %st(1)
        fldt      72+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      60+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      84+_CP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      72+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%edi,8)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        je        ..B1.26
..B1.22:
        fmulp     %st, %st(4)
        testl     %esi, %esi
        fxch      %st(2)
        fmul      %st(3), %st
        movl      16(%esp), %eax
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpl     (%eax)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpl     8(%eax)
        je        ..B1.24
..B1.23:
        fldcw     14(%esp)
..B1.24:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.25:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        fxch      %st(1)
        fmulp     %st, %st(4)
        testl     %esi, %esi
        fxch      %st(2)
        fmul      %st(3), %st
        movl      16(%esp), %eax
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpl     8(%eax)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpl     (%eax)
        je        ..B1.28
..B1.27:
        fldcw     14(%esp)
..B1.28:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.29:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.30:
        testb     $2, 20(%esp)
        je        ..B1.40
..B1.31:
        fld       %st(0)
        testb     $2, %dl
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        je        ..B1.36
..B1.32:
        fstp      %st(2)
        fldt      84+_CP@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(2), %st
        fldt      72+_CP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      60+_CP@GOTOFF(%ebx)
        movl      16(%esp), %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%edi,8)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpl     8(%eax)
        je        ..B1.34
..B1.33:
        fldcw     14(%esp)
..B1.34:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.35:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        fldt      84+_SP@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(1), %st
        fxch      %st(3)
        fmull     _ones@GOTOFF(%ebx,%edi,8)
        fldt      72+_SP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      60+_SP@GOTOFF(%ebx)
        movl      16(%esp), %eax
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(4)
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fmul      %st, %st(4)
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpl     8(%eax)
        je        ..B1.38
..B1.37:
        fldcw     14(%esp)
..B1.38:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.39:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.40:
        testb     $1, 20(%esp)
        je        ..B1.50
..B1.41:
        fld       %st(0)
        testb     $2, %dl
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        je        ..B1.46
..B1.42:
        fldt      84+_SP@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(1), %st
        fxch      %st(3)
        fmull     _ones@GOTOFF(%ebx,%eax,8)
        fldt      72+_SP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      60+_SP@GOTOFF(%ebx)
        movl      16(%esp), %eax
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(4)
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fmul      %st, %st(4)
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpl     (%eax)
        je        ..B1.44
..B1.43:
        fldcw     14(%esp)
..B1.44:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.45:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        fstp      %st(2)
        fldt      84+_CP@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(2), %st
        fldt      72+_CP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      60+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(1)
        movl      16(%esp), %eax
        faddp     %st, %st(1)
        fstpl     (%eax)
        je        ..B1.48
..B1.47:
        fldcw     14(%esp)
..B1.48:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.49:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        fstp      %st(0)
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.53
..B1.51:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.52:
        xorl      %esi, %esi
        jmp       ..B1.8
..B1.53:
        call      __stack_chk_fail@PLT
..B1.60:
        xorl      %esi, %esi
        jmp       ..B1.18
        .align    16,0x90
	.type	__libm_sincos_huge,@function
	.size	__libm_sincos_huge,.-__libm_sincos_huge
	.data
# -- End  __libm_sincos_huge
	.section .rodata, "a"
	.align 16
	.align 16
_Pi4Inv:
	.long	1841940611
	.long	1072979760
	.type	_Pi4Inv,@object
	.size	_Pi4Inv,8
	.space 8, 0x00 	# pad
	.align 16
_Pi4x3:
	.long	1413754880
	.long	3219726843
	.long	993632256
	.long	1027030475
	.long	3773204808
	.long	3129236486
	.type	_Pi4x3,@object
	.size	_Pi4x3,24
	.space 8, 0x00 	# pad
	.align 16
_Pi4x4:
	.long	1413480448
	.long	3219726843
	.long	442499072
	.long	3183522913
	.long	771751936
	.long	3146979722
	.long	622873025
	.long	3110831002
	.type	_Pi4x4,@object
	.size	_Pi4x4,32
	.align 16
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	34951
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	50471
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	17910
	.word	46614
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	33371
	.word	14743
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	48947
	.word	35764
	.word	12250
	.word	45202
	.word	16350
	.word	0
	.word	17574
	.word	60698
	.word	10735
	.word	55102
	.word	49110
	.word	0
	.word	34320
	.word	12415
	.word	25249
	.word	51489
	.word	16334
	.word	0
	.type	_SP,@object
	.size	_SP,96
	.align 16
_CP:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	39983
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	61476
	.word	3244
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	1022
	.word	16229
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	55373
	.word	44526
	.word	50840
	.word	36726
	.word	16354
	.word	0
	.word	55994
	.word	65145
	.word	59958
	.word	51657
	.word	49114
	.word	0
	.word	15046
	.word	2976
	.word	1998
	.word	54661
	.word	16338
	.word	0
	.type	_CP,@object
	.size	_CP,96
	.align 16
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
