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
	.file "libm_cosl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_cosl_k80
	.text
       .align    16,0x90
	.hidden __libm_cosl_k80
	.globl __libm_cosl_k80
__libm_cosl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %ebx
        subl      $184, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 144(%esp)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $16378, %eax
        movl      20(%ebp), %esi
        jge       ..B1.18
..B1.2:
        cmpl      $16372, %eax
        jge       ..B1.16
..B1.3:
        cmpl      $16364, %eax
        jge       ..B1.14
..B1.4:
        cmpl      $16308, %eax
        jge       ..B1.12
..B1.5:
        testl     %eax, %eax
        jne       ..B1.8
..B1.6:
        cmpl      $0, 12(%ebp)
        jne       ..B1.8
..B1.7:
        cmpl      $0, 8(%ebp)
        je        ..B1.9
..B1.8:
        fldl      _ones@GOTOFF(%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fsubr     %st(1), %st
        fstpt     (%esi)
        fldl      _zeros@GOTOFF(%ebx)
        fstpt     12(%esi)
        jmp       ..B1.10
..B1.9:
        fldl      _ones@GOTOFF(%ebx)
        fstpt     (%esi)
        fldt      (%esi)
        fldl      _zeros@GOTOFF(%ebx)
        fstpt     12(%esi)
..B1.10:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.28
..B1.11:
        addl      $184, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.12:
        fldl      _ones@GOTOFF(%ebx)
        fstpt     (%esi)
        fldt      (%esi)
        fldt      8(%ebp)
        fldt      _CP3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     12(%esi)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.28
..B1.13:
        addl      $184, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        fldt      8(%ebp)
        fmul      %st(0), %st
        fldl      _ones@GOTOFF(%ebx)
        fstpt     (%esi)
        fldt      (%esi)
        fldt      12+_CP2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      _CP2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esi)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.28
..B1.15:
        addl      $184, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        fldt      8(%ebp)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      _ones@GOTOFF(%ebx)
        fstpt     (%esi)
        fldt      (%esi)
        fldt      36+_CP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      12+_CP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_CP1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fldt      _CP1@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esi)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.28
..B1.17:
        addl      $184, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        cmpl      $16382, %eax
        jge       ..B1.20
..B1.19:
        fldt      8(%ebp)
        fldl      _TWO_52H@GOTOFF(%ebx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ebx,%eax,8)
        xorl      %eax, %eax
        fmul      %st, %st(1)
        xorl      %edx, %edx
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fxch      %st(1)
        fstpt     112(%esp)
        fldt      112(%esp)
        fstpt     8(%ebp)
        jmp       ..B1.23
..B1.20:
        je        ..B1.33
..B1.21:
        fldt      8(%ebp)
        lea       128(%esp), %edx
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmull     _ones@GOTOFF(%ebx,%eax,8)
        fstpt     (%esp)
        fldt      (%esp)
        movl      $0, 12(%esp)
        fstpt     8(%ebp)
        movl      %edx, 16(%esp)
        call      __libm_reduce_pi04l
..B1.38:
        movl      %eax, %edx
..B1.22:
        fldl      128(%esp)
        lea       3(%edx), %eax
        fldl      _TWO_52H@GOTOFF(%ebx)
        fmul      %st(1), %st
        fld       %st(0)
        shrl      $2, %eax
        fadd      %st(2), %st
        andl      $1, %eax
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddl     136(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     112(%esp)
..B1.23:
        fldt      112(%esp)
        fld       %st(2)
        fmul      %st(2), %st
        fld       %st(2)
        fmulp     %st, %st(2)
        incl      %edx
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        testb     $2, %dl
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%esp)
        fldt      80(%esp)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      _TWO_53H@GOTOFF(%ebx)
        fstpt     64(%esp)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        je        ..B1.25
..B1.24:
        fldt      84+_SP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      60+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_SP@GOTOFF(%ebx)
        fldt      96+_SP@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fldt      48(%esp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      80(%esp)
        fldt      96(%esp)
        fldt      120+_SP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      108+_SP@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fld       %st(3)
        fldt      64(%esp)
        fmul      %st(4), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fmul      %st(3), %st
        fxch      %st(2)
        fsub      %st(3), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      112(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%esi)
        fmulp     %st, %st(1)
        fstpt     12(%esi)
        fldl      _ones@GOTOFF(%ebx)
        jmp       ..B1.26
..B1.25:
        fstp      %st(4)
        fstp      %st(4)
        fldt      84+_CP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      60+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      72+_CP@GOTOFF(%ebx)
        fldt      96+_CP@GOTOFF(%ebx)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      80(%esp)
        fldt      96(%esp)
        fldt      120+_CP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fldt      108+_CP@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fldt      64(%esp)
        fmul      %st(2), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldl      _ones@GOTOFF(%ebx)
        fadd      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     (%esi)
        fmulp     %st, %st(1)
        fstpt     12(%esi)
..B1.26:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.28
..B1.27:
        addl      $184, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.33:
        movl      12(%ebp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.19
..B1.34:
        jne       ..B1.21
..B1.35:
        cmpl      $560513589, 8(%ebp)
        jbe       ..B1.19
        jmp       ..B1.21
        .align    16,0x90
	.type	__libm_cosl_k80,@function
	.size	__libm_cosl_k80,.-__libm_cosl_k80
	.data
# -- End  __libm_cosl_k80
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
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
	.word	32768
	.word	39151
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,24
	.align 2
_CP3:
	.word	0
	.word	65450
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.type	_CP3,@object
	.size	_CP3,12
	.align 2
_CP2:
	.word	63855
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	18238
	.word	17476
	.word	43656
	.word	43690
	.word	16378
	.word	0
	.type	_CP2,@object
	.size	_CP2,24
	.align 2
_CP1:
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	47533
	.word	43689
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	14131
	.word	49466
	.word	24756
	.word	46603
	.word	49141
	.word	0
	.word	37142
	.word	18013
	.word	35855
	.word	53259
	.word	16367
	.word	0
	.type	_CP1,@object
	.size	_CP1,48
	.align 2
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	34948
	.word	34952
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	53243
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	30577
	.word	46649
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	11760
	.word	43464
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	47000
	.word	43232
	.word	12444
	.word	45202
	.word	16350
	.word	0
	.word	52983
	.word	47009
	.word	40440
	.word	55103
	.word	49110
	.word	0
	.word	35433
	.word	14927
	.word	40941
	.word	51860
	.word	16334
	.word	0
	.word	1340
	.word	16361
	.word	50747
	.word	38523
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	16376
	.word	0
	.type	_SP,@object
	.size	_SP,132
	.align 2
_CP:
	.word	51631
	.word	16464
	.word	16497
	.word	44012
	.word	16306
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16372
	.word	0
	.word	46557
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	28380
	.word	3328
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	23816
	.word	50302
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	49866
	.word	64527
	.word	51070
	.word	36726
	.word	16354
	.word	0
	.word	65056
	.word	3638
	.word	41889
	.word	51659
	.word	49114
	.word	0
	.word	13834
	.word	11317
	.word	33607
	.word	55101
	.word	16338
	.word	0
	.word	40709
	.word	42374
	.word	64411
	.word	45709
	.word	49098
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
	.word	43008
	.word	16378
	.word	0
	.type	_CP,@object
	.size	_CP,132
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
