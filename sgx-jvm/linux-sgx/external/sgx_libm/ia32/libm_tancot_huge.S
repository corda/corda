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
	.file "libm_tancot_huge.c"
	.text
..TXTST0:
# -- Begin  __libm_tancot_huge
	.text
       .align    16,0x90
	.hidden __libm_tancot_huge
	.globl __libm_tancot_huge
__libm_tancot_huge:
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
        subl      $116, %esp
        movl      16(%ebp), %eax
        movl      20(%ebp), %edx
        movl      %eax, 28(%esp)
        movl      %edx, 32(%esp)
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 64(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movl      12(%ebp), %edx
        movl      %edx, %eax
        andl      $2147483647, %eax
        shrl      $31, %edx
        movl      %edx, 44(%esp)
        cmpl      $1104150528, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jae       ..B1.14
..B1.4:
        fldl      8(%ebp)
        fabs      
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        fstpt     16(%esp)
        fldt      16(%esp)
        cmpl      $768, %edx
        fstl      8(%ebp)
        fldl      _Pi4Inv@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpl     (%esp)
        je        ..B1.46
..B1.5:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.6:
        fldcw     12(%esp)
..B1.7:
        fldl      8(%ebp)
        movl      $1, %esi
        fstpt     16(%esp)
..B1.8:
        movl      (%esp), %edx
        movl      4(%esp), %ecx
        movl      %ecx, %edi
        movl      %edx, 8(%esp)
        movl      %ecx, %edx
        andl      $1048575, %edx
        shrl      $20, %edi
        orl       $1048576, %edx
        cmpl      $1094713344, %ecx
        jae       ..B1.10
..B1.9:
        movl      %edi, %ecx
        negl      %ecx
        addl      $19, %ecx
        shrl      %cl, %edx
        jmp       ..B1.11
..B1.10:
        lea       13(%edi), %ecx
        negl      %edi
        addl      $19, %edi
        shll      %cl, %edx
        movl      %edi, %ecx
        movl      8(%esp), %edi
        shrl      %cl, %edi
        orl       %edi, %edx
..B1.11:
        lea       1(%edx), %ecx
        andl      $-2, %ecx
        movl      %ecx, (%esp)
        cmpl      $1094713344, %eax
        fildl     (%esp)
        jae       ..B1.13
..B1.12:
        fldl      _Pi4x3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      8+_Pi4x3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(2)
        fmull     16+_Pi4x3@GOTOFF(%ebx)
        fldt      16(%esp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.20
..B1.13:
        fldl      _Pi4x4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      8+_Pi4x4@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldl      16+_Pi4x4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(3)
        fmull     24+_Pi4x4@GOTOFF(%ebx)
        fldt      16(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.20
..B1.14:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.51
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        movl      $1, %esi
..B1.18:
        fldl      8(%ebp)
        addl      $-32, %esp
        fabs      
        lea       80(%esp), %eax
        fstpt     (%esp)
        movl      $0, 12(%esp)
        movl      %eax, 16(%esp)
        call      __libm_reduce_pi04l
..B1.54:
        movl      %eax, %edx
        addl      $32, %esp
..B1.19:
        fldl      48(%esp)
        faddl     56(%esp)
        fstpt     16(%esp)
..B1.20:
        movl      32(%esp), %eax
        andl      $3, %eax
        cmpl      $3, %eax
        jne       ..B1.28
..B1.21:
        fldl      _ones@GOTOFF(%ebx)
        incl      %edx
        fldt      16(%esp)
        testb     $2, %dl
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fdivr     %st, %st(3)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      36+_TP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      24+_TP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_TP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _TP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      36+_TQ@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      24+_TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        fldt      132+_GP@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldt      120+_GP@GOTOFF(%ebx)
        fmul      %st(5), %st
        fldt      108+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      96+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      84+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      72+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      60+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      48+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      36+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      24+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fldt      12+_GP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      _GP@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        je        ..B1.23
..B1.22:
        fldt      (%esp)
        fxch      %st(1)
        fdivrp    %st, %st(3)
        fmulp     %st, %st(2)
        fldt      16(%esp)
        movl      44(%esp), %eax
        xorl      $1, %eax
        fmul      %st, %st(2)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(2)
        fmul      %st, %st(4)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        jmp       ..B1.24
..B1.23:
        fdivrp    %st, %st(2)
        fldt      (%esp)
        fmulp     %st, %st(2)
        fldt      16(%esp)
        fmul      %st, %st(2)
        movl      44(%esp), %eax
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(3)
        fmul      %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     32(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.24:
        testl     %esi, %esi
        je        ..B1.26
..B1.25:
        fldcw     14(%esp)
..B1.26:
        fldt      32(%esp)
        fldt      (%esp)
        movl      28(%esp), %eax
        fstpl     8(%eax)
        fstpl     (%eax)
        movl      64(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.47
..B1.27:
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        testb     $2, 32(%esp)
        je        ..B1.36
..B1.29:
        fldt      16(%esp)
        incl      %edx
        testb     $2, %dl
        fmul      %st(0), %st
        je        ..B1.31
..B1.30:
        fldl      _ones@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      16(%esp)
        fdivr     %st, %st(2)
        fldt      132+_GP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      120+_GP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      108+_GP@GOTOFF(%ebx)
        movl      44(%esp), %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        xorl      $1, %eax
        fldt      96+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      84+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      72+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      60+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      36+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      12+_GP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      _GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.32
..B1.31:
        fldt      36+_TP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      24+_TP@GOTOFF(%ebx)
        movl      44(%esp), %eax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_TQ@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      24+_TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      12+_TP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      _TP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fdivp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
..B1.32:
        testl     %esi, %esi
        je        ..B1.34
..B1.33:
        fldcw     14(%esp)
..B1.34:
        fldt      16(%esp)
        movl      28(%esp), %eax
        fstpl     (%eax)
        movl      64(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.47
..B1.35:
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        testb     $1, 32(%esp)
        je        ..B1.44
..B1.37:
        fldt      16(%esp)
        incl      %edx
        testb     $2, %dl
        fmul      %st(0), %st
        fstpt     (%esp)
        je        ..B1.39
..B1.38:
        fldt      36+_TP@GOTOFF(%ebx)
        fldt      24+_TP@GOTOFF(%ebx)
        fldt      12+_TP@GOTOFF(%ebx)
        fldt      _TP@GOTOFF(%ebx)
        fldt      36+_TQ@GOTOFF(%ebx)
        fldt      24+_TQ@GOTOFF(%ebx)
        fldt      12+_TQ@GOTOFF(%ebx)
        fldt      (%esp)
        fmul      %st, %st(7)
        movl      44(%esp), %eax
        fxch      %st(6)
        faddp     %st, %st(7)
        fxch      %st(5)
        fmul      %st, %st(6)
        xorl      $1, %eax
        fxch      %st(4)
        faddp     %st, %st(6)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _TQ@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fdivrp    %st, %st(2)
        fmulp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.40
..B1.39:
        fldl      _ones@GOTOFF(%ebx)
        fldt      16(%esp)
        fdivr     %st, %st(1)
        fldt      (%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      132+_GP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      120+_GP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      108+_GP@GOTOFF(%ebx)
        movl      44(%esp), %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      96+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      84+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      72+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      60+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_GP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      12+_GP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _GP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.40:
        testl     %esi, %esi
        je        ..B1.42
..B1.41:
        fldcw     14(%esp)
..B1.42:
        fldt      (%esp)
        movl      28(%esp), %eax
        fstpl     8(%eax)
        movl      64(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.47
..B1.43:
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.44:
        movl      64(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.47
..B1.45:
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        xorl      %esi, %esi
        jmp       ..B1.8
..B1.47:
        call      __stack_chk_fail@PLT
..B1.51:
        xorl      %esi, %esi
        jmp       ..B1.18
        .align    16,0x90
	.type	__libm_tancot_huge,@function
	.size	__libm_tancot_huge,.-__libm_tancot_huge
	.data
# -- End  __libm_tancot_huge
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
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 16
_TP:
	.word	19670
	.word	44908
	.word	50960
	.word	50786
	.word	49149
	.word	0
	.word	19206
	.word	45228
	.word	54194
	.word	52268
	.word	16377
	.word	0
	.word	227
	.word	51280
	.word	43560
	.word	38195
	.word	49139
	.word	0
	.word	12272
	.word	18029
	.word	6715
	.word	45670
	.word	16357
	.word	0
	.type	_TP,@object
	.size	_TP,48
	.align 16
_TQ:
	.word	14748
	.word	33681
	.word	5452
	.word	38090
	.word	49151
	.word	0
	.word	46755
	.word	50026
	.word	17634
	.word	35372
	.word	16382
	.word	0
	.word	46863
	.word	53352
	.word	42702
	.word	59869
	.word	49145
	.word	0
	.word	33295
	.word	20942
	.word	32118
	.word	39935
	.word	16371
	.word	0
	.type	_TQ,@object
	.size	_TQ,48
	.align 16
_GP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	46639
	.word	2912
	.word	24758
	.word	46603
	.word	49145
	.word	0
	.word	57255
	.word	2218
	.word	21984
	.word	35507
	.word	49142
	.word	0
	.word	34208
	.word	43033
	.word	48281
	.word	56811
	.word	49138
	.word	0
	.word	28773
	.word	27191
	.word	31071
	.word	45908
	.word	49135
	.word	0
	.word	43257
	.word	33777
	.word	11976
	.word	37184
	.word	49132
	.word	0
	.word	62410
	.word	35990
	.word	36363
	.word	60269
	.word	49128
	.word	0
	.word	13659
	.word	55568
	.word	26569
	.word	48851
	.word	49125
	.word	0
	.word	10347
	.word	46238
	.word	47188
	.word	39576
	.word	49122
	.word	0
	.word	2161
	.word	6703
	.word	25719
	.word	64708
	.word	49118
	.word	0
	.word	42329
	.word	7593
	.word	44754
	.word	47734
	.word	49115
	.word	0
	.word	163
	.word	32746
	.word	39875
	.word	61957
	.word	49112
	.word	0
	.type	_GP,@object
	.size	_GP,144
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
