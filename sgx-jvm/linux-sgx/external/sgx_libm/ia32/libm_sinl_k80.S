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
	.file "libm_sinl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_sinl_k80
	.text
       .align    16,0x90
	.hidden __libm_sinl_k80
	.globl __libm_sinl_k80
__libm_sinl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
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
        movl      20(%ebp), %edi
        jge       ..B1.13
..B1.2:
        fldt      8(%ebp)
        cmpl      $16372, %eax
        jge       ..B1.11
..B1.3:
        cmpl      $16364, %eax
        jge       ..B1.9
..B1.4:
        fstpt     (%edi)
        fldt      (%edi)
        cmpl      $16308, %eax
        jge       ..B1.6
..B1.5:
        fstp      %st(0)
        fldl      _zeros@GOTOFF(%ebx)
        fstpt     12(%edi)
        fldl      _ones@GOTOFF(%ebx)
        jmp       ..B1.7
..B1.6:
        fldt      _SP3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     12(%edi)
        fldl      _ones@GOTOFF(%ebx)
..B1.7:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.23
..B1.8:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        fldt      12+_SP2@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fxch      %st(2)
        fstpt     (%edi)
        fldt      (%edi)
        fldt      _SP2@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     12(%edi)
        fldl      _ones@GOTOFF(%ebx)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.23
..B1.10:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        fldt      36+_SP1@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(3)
        fstpt     (%edi)
        fldt      (%edi)
        fldt      12+_SP1@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      24+_SP1@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fldt      _SP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%edi)
        fldl      _ones@GOTOFF(%ebx)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.23
..B1.12:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        cmpl      $16382, %eax
        jge       ..B1.15
..B1.14:
        fldt      8(%ebp)
        xorl      %edx, %edx
        fldl      _TWO_52H@GOTOFF(%ebx)
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ebx,%esi,8)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     32(%esp)
        fstpt     112(%esp)
        fldt      112(%esp)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      32(%esp)
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubrp    %st, %st(1)
        jmp       ..B1.18
..B1.15:
        je        ..B1.27
..B1.16:
        fldt      8(%ebp)
        lea       128(%esp), %eax
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        fmull     _ones@GOTOFF(%ebx,%esi,8)
        fstpt     (%esp)
        fldt      (%esp)
        movl      $0, 12(%esp)
        fstpt     8(%ebp)
        movl      %eax, 16(%esp)
        call      __libm_reduce_pi04l
..B1.32:
        movl      %eax, %edx
..B1.17:
        fldl      128(%esp)
        lea       1(%edx), %eax
        fldl      _TWO_52H@GOTOFF(%ebx)
        fmul      %st(1), %st
        fld       %st(0)
        shrl      $2, %eax
        fadd      %st(2), %st
        fstpt     32(%esp)
        xorl      %eax, %esi
        fldt      32(%esp)
        andl      $1, %esi
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddl     136(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     112(%esp)
..B1.18:
        fldt      112(%esp)
        fld       %st(2)
        fmul      %st(2), %st
        fld       %st(2)
        fmulp     %st, %st(2)
        incl      %edx
        faddp     %st, %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        testb     $2, %dl
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     80(%esp)
        fldt      80(%esp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fmul      %st(0), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      _TWO_53H@GOTOFF(%ebx)
        je        ..B1.20
..B1.19:
        fstp      %st(3)
        fstp      %st(3)
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
        fldt      80(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      96(%esp)
        fldt      120+_CP@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fldt      108+_CP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fadd      %st(1), %st
        fmul      %st, %st(2)
        fadd      %st(2), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldl      _ones@GOTOFF(%ebx)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      _ones@GOTOFF(%ebx,%esi,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%edi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%edi)
        jmp       ..B1.21
..B1.20:
        fldt      84+_SP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      60+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_SP@GOTOFF(%ebx)
        fldt      96+_SP@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fldt      80(%esp)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      64(%esp)
        fldt      48(%esp)
        fldt      120+_SP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      108+_SP@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
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
        fldl      _ones@GOTOFF(%ebx,%esi,8)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%edi)
        fmulp     %st, %st(1)
        fstpt     12(%edi)
        fldl      _ones@GOTOFF(%ebx)
..B1.21:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.23
..B1.22:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.27:
        movl      12(%ebp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.14
..B1.28:
        jne       ..B1.16
..B1.29:
        cmpl      $560513589, 8(%ebp)
        jbe       ..B1.14
        jmp       ..B1.16
        .align    16,0x90
	.type	__libm_sinl_k80,@function
	.size	__libm_sinl_k80,.-__libm_sinl_k80
	.data
# -- End  __libm_sinl_k80
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 2
_SP3:
	.word	17476
	.word	43656
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.type	_SP3,@object
	.size	_SP3,12
	.align 2
_SP2:
	.word	43210
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	1587
	.word	57422
	.word	34932
	.word	34952
	.word	16376
	.word	0
	.type	_SP2,@object
	.size	_SP2,24
	.align 2
_SP1:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	12518
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	43088
	.word	7396
	.word	207
	.word	53261
	.word	49138
	.word	0
	.word	55827
	.word	48618
	.word	3602
	.word	47342
	.word	16364
	.word	0
	.type	_SP1,@object
	.size	_SP1,48
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
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
