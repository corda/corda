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
	.file "erfl.c"
	.text
..TXTST0:
# -- Begin  erfl
	.text
       .align    16,0x90
	.globl erfl
erfl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $68, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.39
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.5:
        fldcw     12(%esp)
..B1.6:
        movl      $1, %ecx
..B1.7:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movzwl    16(%ebp), %esi
        andl      $32767, %esi
        flds      ones@GOTOFF(%ebx,%edx,4)
        movl      %esi, %edx
        movl      12(%ebp), %edi
        movl      %edi, %eax
        shll      $16, %edx
        shrl      $16, %eax
        orl       %eax, %edx
        fstpt     48(%esp)
        cmpl      $32767, %esi
        je        ..B1.32
..B1.8:
        cmpl      $1073860832, %edx
        jge       ..B1.26
..B1.9:
        fldt      8(%ebp)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B1.10
        je        ..B1.29
..B1.10:
        cmpl      $16343, %esi
        jle       ..B1.18
..B1.11:
        cmpl      $16380, %esi
        jge       ..B1.15
..B1.12:
        fldt      32(%esp)
        testl     %ecx, %ecx
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fld       %st(2)
        fldl      _TWO_32H@GOTOFF(%ebx)
        fmul      %st(4), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fsub      %st(1), %st
        fldt      96+_ldbA@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      84+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      60+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      36+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_ldbA@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      12+_ldbA@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        fmulp     %st, %st(4)
        fldt      _ldbA@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        je        ..B1.14
..B1.13:
        fldcw     14(%esp)
..B1.14:
        fldt      32(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        fldt      48(%esp)
        lea       -1073793024(%edx), %eax
        fldt      32(%esp)
        shrl      $16, %edx
        addl      $-16380, %edx
        cmpl      $47103, %eax
        fmulp     %st, %st(1)
        ja        ..L3
        movl      $6, %edx
..L3:
        movl      %edx, %esi
        shll      $4, %esi
        imull     $360, %edx, %edx
        fmull     erfRRTable@GOTOFF(%esi,%ebx)
        fldt      456+_ldbA@GOTOFF(%ebx,%edx)
        testl     %ecx, %ecx
        fldt      444+_ldbA@GOTOFF(%ebx,%edx)
        fldt      432+_ldbA@GOTOFF(%ebx,%edx)
        fldt      420+_ldbA@GOTOFF(%ebx,%edx)
        fldt      408+_ldbA@GOTOFF(%ebx,%edx)
        fldt      396+_ldbA@GOTOFF(%ebx,%edx)
        fldt      384+_ldbA@GOTOFF(%ebx,%edx)
        fxch      %st(7)
        faddl     8+erfRRTable@GOTOFF(%ebx,%esi)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      372+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      360+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      348+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      336+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      324+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      312+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      300+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      288+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      276+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      264+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fld       %st(0)
        fldt      252+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      240+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      228+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fld       %st(0)
        fldt      216+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      204+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     (%esp)
        fldl      _TWO_32H@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fxch      %st(3)
        fsub      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmul      %st(6), %st
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(1), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        fsub      %st(4), %st
        faddp     %st, %st(2)
        fldt      132+_ldbA@GOTOFF(%ebx,%edx)
        fld       %st(0)
        fmul      %st(6), %st
        fstpt     16(%esp)
        fmul      %st(1), %st
        fldt      144+_ldbA@GOTOFF(%ebx,%edx)
        fmul      %st(7), %st
        faddp     %st, %st(1)
        fldt      180+_ldbA@GOTOFF(%ebx,%edx)
        fmul      %st, %st(6)
        fmulp     %st, %st(2)
        fldt      192+_ldbA@GOTOFF(%ebx,%edx)
        fmulp     %st, %st(7)
        fxch      %st(6)
        faddp     %st, %st(1)
        fldt      156+_ldbA@GOTOFF(%ebx,%edx)
        fld       %st(0)
        fadd      %st(6), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(6)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      168+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(1)
        fld       %st(4)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      108+_ldbA@GOTOFF(%ebx,%edx)
        fld       %st(0)
        fldt      16(%esp)
        fadd      %st, %st(1)
        fld       %st(1)
        fadd      %st(5), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(5)
        faddp     %st, %st(2)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      32(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fldt      120+_ldbA@GOTOFF(%ebx,%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
        je        ..B1.17
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      48(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        fldt      _TWO5600@GOTOFF(%ebx)
        testl     %esi, %esi
        fldt      32(%esp)
        fmul      %st(1), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_32H@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(3), %st
        fldt      _ldbA@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        fldt      12+_ldbA@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        jne       ..B1.22
..B1.19:
        testl     $65520, %edx
        je        ..B1.22
..B1.20:
        fldt      48(%esp)
        fldt      32(%esp)
        fmulp     %st, %st(1)
        fldt      _DenoBND@GOTOFF(%ebx)
        fmul      %st(5), %st
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.22
..B1.21:
        fstp      %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      12+_TWO5600@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.23
..B1.22:
        fstp      %st(1)
        fstp      %st(2)
        faddp     %st, %st(1)
        fldt      12+_TWO5600@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.23:
        testl     %ecx, %ecx
        je        ..B1.25
..B1.24:
        fldcw     14(%esp)
..B1.25:
        fldt      (%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        fldt      _Epsilon@GOTOFF(%ebx)
        testl     %ecx, %ecx
        fldt      48(%esp)
        fmul      %st, %st(1)
        fsubp     %st, %st(1)
        fstpt     48(%esp)
        je        ..B1.28
..B1.27:
        fldcw     14(%esp)
..B1.28:
        fldt      48(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        testl     %ecx, %ecx
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
        fldt      8(%ebp)
        fstpt     32(%esp)
..B1.31:
        fldt      32(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        cmpl      $-2147483648, %edi
        jne       ..B1.34
..B1.33:
        cmpl      $0, 8(%ebp)
        je        ..B1.36
..B1.34:
        fldt      8(%ebp)
        fadd      %st(0), %st
        fstpt     48(%esp)
..B1.36:
        testl     %ecx, %ecx
        je        ..B1.38
..B1.37:
        fldcw     14(%esp)
..B1.38:
        fldt      48(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        xorl      %ecx, %ecx
        jmp       ..B1.7
        .align    16,0x90
	.type	erfl,@function
	.size	erfl,.-erfl
	.data
# -- End  erfl
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
_ldbA:
	.word	0
	.word	0
	.word	47746
	.word	36974
	.word	16383
	.word	0
	.word	30720
	.word	36515
	.word	17515
	.word	42715
	.word	16348
	.word	0
	.word	13842
	.word	7119
	.word	41816
	.word	49299
	.word	49149
	.word	0
	.word	3215
	.word	8543
	.word	37072
	.word	59159
	.word	16379
	.word	0
	.word	33545
	.word	17489
	.word	29065
	.word	56342
	.word	49145
	.word	0
	.word	23161
	.word	4543
	.word	59015
	.word	43821
	.word	16375
	.word	0
	.word	15198
	.word	65095
	.word	55656
	.word	57366
	.word	49140
	.word	0
	.word	11319
	.word	61676
	.word	11803
	.word	64720
	.word	16369
	.word	0
	.word	23735
	.word	16978
	.word	56014
	.word	63664
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	61888
	.word	54818
	.word	16380
	.word	0
	.word	10044
	.word	28505
	.word	55013
	.word	60780
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	27524
	.word	35697
	.word	16380
	.word	0
	.word	61965
	.word	13477
	.word	25391
	.word	40556
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	8518
	.word	53546
	.word	49142
	.word	0
	.word	60180
	.word	52984
	.word	38086
	.word	60834
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	60744
	.word	44249
	.word	49140
	.word	0
	.word	63138
	.word	9916
	.word	20408
	.word	61684
	.word	16340
	.word	0
	.word	50506
	.word	17424
	.word	9343
	.word	52291
	.word	16367
	.word	0
	.word	50365
	.word	34444
	.word	5016
	.word	49178
	.word	16364
	.word	0
	.word	17941
	.word	44182
	.word	56946
	.word	34035
	.word	49128
	.word	0
	.word	41364
	.word	35872
	.word	36905
	.word	43189
	.word	49124
	.word	0
	.word	47342
	.word	2906
	.word	40861
	.word	33222
	.word	16352
	.word	0
	.word	48408
	.word	44394
	.word	43316
	.word	61646
	.word	16347
	.word	0
	.word	52639
	.word	32949
	.word	29686
	.word	51873
	.word	49111
	.word	0
	.word	13354
	.word	8312
	.word	46883
	.word	36813
	.word	49107
	.word	0
	.word	10783
	.word	42642
	.word	20549
	.word	33739
	.word	16335
	.word	0
	.word	42435
	.word	44080
	.word	27835
	.word	37640
	.word	16330
	.word	0
	.word	55413
	.word	51626
	.word	37570
	.word	37609
	.word	49094
	.word	0
	.word	62471
	.word	35756
	.word	61962
	.word	33520
	.word	49089
	.word	0
	.word	40388
	.word	48302
	.word	32466
	.word	36673
	.word	16317
	.word	0
	.word	21782
	.word	37556
	.word	26897
	.word	52597
	.word	16311
	.word	0
	.word	35272
	.word	16771
	.word	34830
	.word	63430
	.word	49075
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	26972
	.word	52968
	.word	16381
	.word	0
	.word	34629
	.word	50944
	.word	29785
	.word	56844
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	26328
	.word	64248
	.word	16380
	.word	0
	.word	5285
	.word	428
	.word	53188
	.word	60110
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	19746
	.word	48186
	.word	49145
	.word	0
	.word	3964
	.word	321
	.word	7123
	.word	45083
	.word	16344
	.word	0
	.word	0
	.word	0
	.word	25230
	.word	61571
	.word	49142
	.word	0
	.word	57803
	.word	48946
	.word	59164
	.word	36732
	.word	49110
	.word	0
	.word	46948
	.word	32819
	.word	54758
	.word	43668
	.word	16372
	.word	0
	.word	16614
	.word	63709
	.word	23635
	.word	47684
	.word	16368
	.word	0
	.word	5734
	.word	50071
	.word	41702
	.word	52540
	.word	49134
	.word	0
	.word	1767
	.word	50346
	.word	34241
	.word	45792
	.word	49129
	.word	0
	.word	21673
	.word	57712
	.word	23306
	.word	47181
	.word	16360
	.word	0
	.word	30917
	.word	34592
	.word	23220
	.word	33297
	.word	16352
	.word	0
	.word	55246
	.word	63001
	.word	17535
	.word	33707
	.word	49122
	.word	0
	.word	5457
	.word	35457
	.word	53300
	.word	62645
	.word	16347
	.word	0
	.word	9460
	.word	62267
	.word	29700
	.word	39878
	.word	16347
	.word	0
	.word	54791
	.word	30369
	.word	50586
	.word	36074
	.word	49110
	.word	0
	.word	2078
	.word	44475
	.word	65531
	.word	40136
	.word	49108
	.word	0
	.word	65081
	.word	909
	.word	65384
	.word	51785
	.word	16335
	.word	0
	.word	42502
	.word	52825
	.word	47801
	.word	35033
	.word	16333
	.word	0
	.word	56809
	.word	22568
	.word	24289
	.word	58056
	.word	49096
	.word	0
	.word	53182
	.word	10272
	.word	7761
	.word	53779
	.word	49093
	.word	0
	.word	48071
	.word	2665
	.word	17237
	.word	54209
	.word	16321
	.word	0
	.word	49513
	.word	25795
	.word	55780
	.word	36409
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	19372
	.word	46606
	.word	16382
	.word	0
	.word	22905
	.word	34398
	.word	64354
	.word	59506
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	8556
	.word	42135
	.word	16381
	.word	0
	.word	15826
	.word	39989
	.word	27742
	.word	58791
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	45602
	.word	63202
	.word	49147
	.word	0
	.word	61021
	.word	62759
	.word	37190
	.word	44093
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	11408
	.word	56180
	.word	16374
	.word	0
	.word	10545
	.word	4814
	.word	18495
	.word	39194
	.word	49109
	.word	0
	.word	49818
	.word	2604
	.word	44885
	.word	39501
	.word	16376
	.word	0
	.word	35457
	.word	26400
	.word	3144
	.word	55829
	.word	49141
	.word	0
	.word	1168
	.word	22940
	.word	48307
	.word	56355
	.word	49139
	.word	0
	.word	60825
	.word	56496
	.word	32133
	.word	38661
	.word	16370
	.word	0
	.word	41837
	.word	62246
	.word	39292
	.word	38617
	.word	16366
	.word	0
	.word	61718
	.word	27283
	.word	11883
	.word	33288
	.word	49134
	.word	0
	.word	51738
	.word	39830
	.word	42849
	.word	49937
	.word	16360
	.word	0
	.word	45393
	.word	52381
	.word	54757
	.word	41874
	.word	16361
	.word	0
	.word	45169
	.word	44591
	.word	30009
	.word	57007
	.word	49125
	.word	0
	.word	11552
	.word	4380
	.word	40671
	.word	40665
	.word	49124
	.word	0
	.word	28211
	.word	31057
	.word	64947
	.word	47497
	.word	16353
	.word	0
	.word	61755
	.word	33819
	.word	30246
	.word	61557
	.word	16350
	.word	0
	.word	57599
	.word	65445
	.word	31608
	.word	55873
	.word	49116
	.word	0
	.word	21063
	.word	33239
	.word	23242
	.word	34595
	.word	49113
	.word	0
	.word	24566
	.word	16137
	.word	47167
	.word	52509
	.word	16343
	.word	0
	.word	20857
	.word	62758
	.word	44126
	.word	43729
	.word	16338
	.word	0
	.word	26678
	.word	64201
	.word	37761
	.word	41436
	.word	49106
	.word	0
	.word	15315
	.word	3435
	.word	56025
	.word	62814
	.word	16331
	.word	0
	.word	19814
	.word	59948
	.word	14740
	.word	56311
	.word	16332
	.word	0
	.word	36977
	.word	22840
	.word	9289
	.word	39778
	.word	49096
	.word	0
	.word	62085
	.word	64048
	.word	54574
	.word	65118
	.word	49094
	.word	0
	.word	28752
	.word	34202
	.word	22833
	.word	38817
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	43704
	.word	63314
	.word	16382
	.word	0
	.word	17048
	.word	17271
	.word	64911
	.word	39894
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	47414
	.word	62353
	.word	16379
	.word	0
	.word	53119
	.word	3173
	.word	58504
	.word	64362
	.word	49113
	.word	0
	.word	0
	.word	0
	.word	19176
	.word	46765
	.word	49148
	.word	0
	.word	36591
	.word	64385
	.word	59980
	.word	41399
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	352
	.word	36373
	.word	16380
	.word	0
	.word	63217
	.word	55085
	.word	16723
	.word	42154
	.word	49116
	.word	0
	.word	62758
	.word	20699
	.word	19176
	.word	46765
	.word	49146
	.word	0
	.word	18142
	.word	49445
	.word	47413
	.word	62353
	.word	49144
	.word	0
	.word	54343
	.word	9201
	.word	60310
	.word	40529
	.word	16377
	.word	0
	.word	62797
	.word	60885
	.word	40240
	.word	39787
	.word	49143
	.word	0
	.word	7512
	.word	45008
	.word	10012
	.word	39639
	.word	49142
	.word	0
	.word	20359
	.word	48234
	.word	1564
	.word	57372
	.word	16373
	.word	0
	.word	11764
	.word	26683
	.word	64145
	.word	49882
	.word	49137
	.word	0
	.word	32393
	.word	50330
	.word	31801
	.word	34151
	.word	49139
	.word	0
	.word	5247
	.word	4806
	.word	33577
	.word	41709
	.word	16369
	.word	0
	.word	43823
	.word	34097
	.word	44124
	.word	38558
	.word	16367
	.word	0
	.word	65526
	.word	14942
	.word	25531
	.word	60526
	.word	49134
	.word	0
	.word	38557
	.word	7794
	.word	42920
	.word	40918
	.word	16362
	.word	0
	.word	31363
	.word	35665
	.word	11710
	.word	52655
	.word	16363
	.word	0
	.word	33312
	.word	35439
	.word	34687
	.word	46194
	.word	49129
	.word	0
	.word	21144
	.word	58026
	.word	60687
	.word	57306
	.word	49127
	.word	0
	.word	20701
	.word	12315
	.word	12846
	.word	54836
	.word	16358
	.word	0
	.word	45362
	.word	30760
	.word	28723
	.word	42182
	.word	16354
	.word	0
	.word	16353
	.word	46602
	.word	41748
	.word	42697
	.word	49123
	.word	0
	.word	20735
	.word	3347
	.word	26735
	.word	62816
	.word	16351
	.word	0
	.word	13205
	.word	16964
	.word	38504
	.word	48289
	.word	16351
	.word	0
	.word	28004
	.word	15789
	.word	31604
	.word	37913
	.word	49117
	.word	0
	.word	17923
	.word	56666
	.word	63953
	.word	36481
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	36194
	.word	65534
	.word	16382
	.word	0
	.word	12232
	.word	62525
	.word	48597
	.word	39676
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	29918
	.word	37380
	.word	16371
	.word	0
	.word	48638
	.word	64479
	.word	2811
	.word	58988
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	44876
	.word	56070
	.word	49141
	.word	0
	.word	64093
	.word	3811
	.word	8439
	.word	45892
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	42382
	.word	52955
	.word	16375
	.word	0
	.word	7924
	.word	29399
	.word	61525
	.word	36582
	.word	16343
	.word	0
	.word	46606
	.word	18426
	.word	11663
	.word	35044
	.word	49145
	.word	0
	.word	15382
	.word	23178
	.word	43683
	.word	34109
	.word	16378
	.word	0
	.word	6438
	.word	10314
	.word	6872
	.word	49529
	.word	49146
	.word	0
	.word	64583
	.word	11206
	.word	41955
	.word	52421
	.word	16378
	.word	0
	.word	13495
	.word	31464
	.word	61723
	.word	36178
	.word	49146
	.word	0
	.word	28427
	.word	18799
	.word	40522
	.word	59729
	.word	16375
	.word	0
	.word	47510
	.word	26829
	.word	39947
	.word	33535
	.word	16377
	.word	0
	.word	64666
	.word	11700
	.word	15653
	.word	46358
	.word	49145
	.word	0
	.word	6475
	.word	64324
	.word	16618
	.word	52067
	.word	16376
	.word	0
	.word	31765
	.word	32664
	.word	9436
	.word	33916
	.word	16373
	.word	0
	.word	38078
	.word	37286
	.word	55507
	.word	62195
	.word	49143
	.word	0
	.word	16438
	.word	19728
	.word	54394
	.word	45557
	.word	16375
	.word	0
	.word	34868
	.word	45266
	.word	60847
	.word	41133
	.word	49140
	.word	0
	.word	6929
	.word	9316
	.word	56077
	.word	32933
	.word	49142
	.word	0
	.word	60039
	.word	42217
	.word	20756
	.word	52409
	.word	16373
	.word	0
	.word	38571
	.word	57934
	.word	18985
	.word	61369
	.word	49138
	.word	0
	.word	44492
	.word	40353
	.word	2663
	.word	35859
	.word	49140
	.word	0
	.word	54254
	.word	26892
	.word	54523
	.word	56697
	.word	16370
	.word	0
	.word	63636
	.word	38694
	.word	30824
	.word	51163
	.word	49138
	.word	0
	.word	56107
	.word	41638
	.word	48577
	.word	57632
	.word	49138
	.word	0
	.word	13301
	.word	46198
	.word	34221
	.word	52080
	.word	16368
	.word	0
	.word	6730
	.word	19704
	.word	41453
	.word	49062
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	32541
	.word	25746
	.word	20185
	.word	50811
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	40118
	.word	38623
	.word	16333
	.word	0
	.word	63899
	.word	60569
	.word	18971
	.word	35021
	.word	49068
	.word	0
	.word	0
	.word	0
	.word	35006
	.word	57940
	.word	49105
	.word	0
	.word	31190
	.word	18012
	.word	64261
	.word	44050
	.word	16305
	.word	0
	.word	0
	.word	0
	.word	44288
	.word	57137
	.word	16341
	.word	0
	.word	30741
	.word	21478
	.word	2085
	.word	43126
	.word	49077
	.word	0
	.word	43184
	.word	28511
	.word	48193
	.word	41628
	.word	49113
	.word	0
	.word	38069
	.word	12322
	.word	59848
	.word	47812
	.word	16348
	.word	0
	.word	14423
	.word	51984
	.word	32740
	.word	45128
	.word	49119
	.word	0
	.word	4208
	.word	46602
	.word	27709
	.word	35782
	.word	16354
	.word	0
	.word	36873
	.word	25112
	.word	30428
	.word	48402
	.word	49124
	.word	0
	.word	10843
	.word	45056
	.word	322
	.word	58525
	.word	16358
	.word	0
	.word	34740
	.word	5397
	.word	19459
	.word	63343
	.word	49128
	.word	0
	.word	43258
	.word	62838
	.word	12965
	.word	52557
	.word	16362
	.word	0
	.word	52181
	.word	57107
	.word	24596
	.word	44275
	.word	49132
	.word	0
	.word	8033
	.word	61170
	.word	58452
	.word	55638
	.word	16366
	.word	0
	.word	18754
	.word	55045
	.word	40463
	.word	41822
	.word	49134
	.word	0
	.word	31326
	.word	56708
	.word	50347
	.word	42397
	.word	49136
	.word	0
	.word	54840
	.word	45764
	.word	11355
	.word	33431
	.word	49141
	.word	0
	.word	56285
	.word	35328
	.word	49599
	.word	58875
	.word	49141
	.word	0
	.word	42904
	.word	46324
	.word	60880
	.word	56231
	.word	16375
	.word	0
	.word	42754
	.word	64912
	.word	63809
	.word	36632
	.word	16379
	.word	0
	.word	28983
	.word	59630
	.word	18663
	.word	33292
	.word	16381
	.word	0
	.word	56135
	.word	38086
	.word	60740
	.word	37575
	.word	16382
	.word	0
	.word	11769
	.word	21615
	.word	56585
	.word	54481
	.word	16382
	.word	0
	.word	64055
	.word	1299
	.word	36942
	.word	50744
	.word	16382
	.word	0
	.word	6073
	.word	42872
	.word	14757
	.word	53598
	.word	16381
	.word	0
	.word	7021
	.word	43375
	.word	58781
	.word	46458
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	64268
	.word	65535
	.word	16382
	.word	0
	.word	36579
	.word	26545
	.word	24435
	.word	50138
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	14458
	.word	38069
	.word	16364
	.word	0
	.word	15998
	.word	59065
	.word	36918
	.word	41001
	.word	16328
	.word	0
	.word	0
	.word	0
	.word	15150
	.word	34500
	.word	49134
	.word	0
	.word	33246
	.word	7128
	.word	23211
	.word	39186
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	36820
	.word	40101
	.word	16367
	.word	0
	.word	45920
	.word	25716
	.word	39121
	.word	51060
	.word	49103
	.word	0
	.word	52724
	.word	56956
	.word	1384
	.word	33467
	.word	49136
	.word	0
	.word	16783
	.word	11603
	.word	62021
	.word	42511
	.word	16368
	.word	0
	.word	34064
	.word	49622
	.word	4121
	.word	42444
	.word	49136
	.word	0
	.word	14084
	.word	4161
	.word	1985
	.word	33838
	.word	16368
	.word	0
	.word	65189
	.word	58247
	.word	7682
	.word	43141
	.word	49135
	.word	0
	.word	15983
	.word	3226
	.word	43602
	.word	43186
	.word	16366
	.word	0
	.word	7235
	.word	9522
	.word	4836
	.word	63885
	.word	49132
	.word	0
	.word	33104
	.word	49991
	.word	48728
	.word	55353
	.word	16362
	.word	0
	.word	10837
	.word	43216
	.word	47090
	.word	42202
	.word	16359
	.word	0
	.word	11340
	.word	13469
	.word	16673
	.word	42993
	.word	49128
	.word	0
	.word	19848
	.word	42271
	.word	35005
	.word	38963
	.word	16359
	.word	0
	.word	38280
	.word	23060
	.word	26803
	.word	65491
	.word	49124
	.word	0
	.word	3155
	.word	63825
	.word	24453
	.word	53521
	.word	49121
	.word	0
	.word	8736
	.word	25576
	.word	59728
	.word	40305
	.word	16354
	.word	0
	.word	21836
	.word	54867
	.word	10689
	.word	53743
	.word	49120
	.word	0
	.word	35590
	.word	40641
	.word	40511
	.word	35834
	.word	16349
	.word	0
	.word	42959
	.word	52506
	.word	48660
	.word	55474
	.word	16348
	.word	0
	.word	62734
	.word	58818
	.word	9020
	.word	51302
	.word	49115
	.word	0
	.word	59117
	.word	1762
	.word	19420
	.word	58690
	.word	16344
	.word	0
	.word	54865
	.word	3647
	.word	6267
	.word	65292
	.word	16342
	.word	0
	.word	35287
	.word	43939
	.word	50577
	.word	38705
	.word	49110
	.word	0
	.word	78
	.word	46283
	.word	25201
	.word	34088
	.word	16338
	.word	0
	.type	_ldbA,@object
	.size	_ldbA,2628
	.space 12, 0x00 	# pad
	.align 16
erfRRTable:
	.long	0x00000000,0x40200000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x40100000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x40000000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3fd00000
	.long	0x00000000,0xbff80000
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xc00d0000
	.type	erfRRTable,@object
	.size	erfRRTable,112
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_TWO5600:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	21983
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	10783
	.word	0
	.type	_TWO5600,@object
	.size	_TWO5600,24
	.align 2
_DenoBND:
	.word	43678
	.word	54205
	.word	57926
	.word	29039
	.word	0
	.word	0
	.type	_DenoBND,@object
	.size	_DenoBND,12
	.align 2
_Epsilon:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16313
	.word	0
	.type	_Epsilon,@object
	.size	_Epsilon,12
	.data
	.section .note.GNU-stack, ""
# End
