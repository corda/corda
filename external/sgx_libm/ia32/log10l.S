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
	.file "log10l.c"
	.text
..TXTST0:
# -- Begin  log10l
	.text
       .align    16,0x90
	.globl log10l
log10l:
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
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        movl      %edi, %edx
        andl      $32767, %ebx
        shll      $15, %edx
        movl      12(%ebp), %eax
        orl       %ebx, %edx
        movl      %eax, %ecx
        shll      $16, %edx
        shrl      $16, %ecx
        orl       %ecx, %edx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $2147450880, %edx
        jae       ..B1.30
..B1.4:
        addl      $-1073676224, %edx
        cmpl      $32863, %edx
        ja        ..B1.13
..B1.5:
        cmpl      $16383, %ebx
        je        ..B1.40
..B1.6:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.39
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.8:
        fldcw     12(%esp)
..B1.9:
        movl      $1, %eax
..B1.10:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_32H@GOTOFF(%ecx)
        fldt      84+_P@GOTOFF(%ecx)
        fldt      60+_P@GOTOFF(%ecx)
        fldt      36+_P@GOTOFF(%ecx)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(1)
        fsub      %st(3), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st(5), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      96+_P@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.12
..B1.11:
        fldcw     14(%esp)
..B1.12:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        cmpl      $16383, %ebx
        jl        ..B1.19
..B1.14:
        cmpl      $16471, %ebx
        jge       ..B1.19
..B1.15:
        movl      %eax, (%esp)
        lea       -16383(%ebx), %edi
        movl      $1431655766, %eax
        imull     %edi
        sarl      $31, %edi
        subl      %edi, %edx
        lea       (%edx,%edx,2), %edx
        movzwl    8+_exact_values@GOTOFF(%ecx,%edx,8), %eax
        andl      $32767, %eax
        cmpl      %eax, %ebx
        movl      (%esp), %eax
        jne       ..B1.19
..B1.16:
        cmpl      4+_exact_values@GOTOFF(%ecx,%edx,8), %eax
        jne       ..B1.19
..B1.17:
        movl      8(%ebp), %eax
        cmpl      _exact_values@GOTOFF(%ecx,%edx,8), %eax
        jne       ..B1.19
..B1.18:
        fldt      12+_exact_values@GOTOFF(%ecx,%edx,8)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.46
..B1.20:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.21:
        fldcw     12(%esp)
..B1.22:
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        movl      $1, %edx
        andl      $32767, %ebx
..B1.23:
        testl     %ebx, %ebx
        jne       ..B1.26
..B1.24:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _zeros@GOTOFF(%ecx)
        fxch      %st(1)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.25
        je        ..B1.43
..B1.25:
        fstp      %st(0)
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        movl      $-16458, %eax
        andl      $32767, %ebx
        jmp       ..B1.27
..B1.26:
        movl      $-16383, %eax
..B1.27:
        fldl      _TWO_32H@GOTOFF(%ecx)
        andl      $-32768, %esi
        fldt      84+_P@GOTOFF(%ecx)
        orl       $-49153, %esi
        fldt      60+_P@GOTOFF(%ecx)
        addl      %eax, %ebx
        movw      %si, 16(%ebp)
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
        testl     %edx, %edx
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      96+_P@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fildl     16(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     8+__libm_log10l_table_256@GOTOFF(%ecx,%eax)
        faddp     %st, %st(2)
        faddl     __libm_log10l_table_256@GOTOFF(%eax,%ecx)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.29
..B1.28:
        fldcw     14(%esp)
..B1.29:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.30:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.55
..B1.31:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.32:
        fldcw     12(%esp)
..B1.33:
        movzbl    17(%ebp), %edi
        movl      $1, %edx
        andl      $128, %edi
        shrl      $7, %edi
..B1.34:
        testl     %edi, %edi
        jne       ..B1.47
..B1.35:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.36:
        testl     %edx, %edx
        je        ..B1.38
..B1.37:
        fldcw     14(%esp)
..B1.38:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        xorl      %eax, %eax
        jmp       ..B1.10
..B1.40:
        cmpl      $-2147483648, %eax
        jne       ..B1.6
..B1.41:
        cmpl      $0, 8(%ebp)
        jne       ..B1.6
..B1.42:
        fldl      _zeros@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        fldl      8+_ones@GOTOFF(%ecx)
        testl     %edx, %edx
        fdivp     %st, %st(1)
        fstpl     (%esp)
        je        ..B1.45
..B1.44:
        fldcw     14(%esp)
..B1.45:
        fldl      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        xorl      %edx, %edx
        jmp       ..B1.23
..B1.47:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.50
..B1.48:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.35
..B1.49:
        cmpl      $0, 8(%ebp)
        jne       ..B1.35
        jmp       ..B1.54
..B1.50:
        testl     %eax, %eax
        jne       ..B1.54
..B1.51:
        cmpl      $0, 12(%ebp)
        jne       ..B1.54
..B1.52:
        cmpl      $0, 8(%ebp)
        jne       ..B1.54
..B1.53:
        fldl      8+_ones@GOTOFF(%ecx)
        fdivl     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.36
..B1.54:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.36
..B1.55:
        xorl      %edx, %edx
        jmp       ..B1.34
        .align    16,0x90
	.type	log10l,@function
	.size	log10l,.-log10l
	.data
# -- End  log10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0xc8,0xd4,0x0e,0xee,0x0c,0x01,0x86,0xd8,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x85,0x9a,0x20,0x9a,0xfd,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_P:
	.word	23339
	.word	38197
	.word	10353
	.word	43319
	.word	16357
	.word	0
	.word	29077
	.word	14120
	.word	55465
	.word	56923
	.word	49148
	.word	0
	.word	63176
	.word	31258
	.word	15131
	.word	37949
	.word	16380
	.word	0
	.word	29577
	.word	14120
	.word	55465
	.word	56923
	.word	49147
	.word	0
	.word	61319
	.word	34328
	.word	5050
	.word	45539
	.word	16379
	.word	0
	.word	52415
	.word	26390
	.word	15131
	.word	37949
	.word	49147
	.word	0
	.word	16765
	.word	7621
	.word	20590
	.word	65056
	.word	16378
	.word	0
	.word	14162
	.word	11829
	.word	26041
	.word	56924
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	55296
	.word	56923
	.word	16381
	.word	0
	.type	_P,@object
	.size	_P,108
	.align 2
_exact_values:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	0
	.word	64000
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40000
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	0
	.word	50000
	.word	16399
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	0
	.word	62500
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39062
	.word	16406
	.word	0
	.word	0
	.word	0
	.word	0
	.word	57344
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	8192
	.word	48828
	.word	16409
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	10240
	.word	61035
	.word	16412
	.word	0
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	63744
	.word	38146
	.word	16416
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	46912
	.word	47683
	.word	16419
	.word	0
	.word	0
	.word	0
	.word	0
	.word	45056
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	42256
	.word	59604
	.word	16422
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	59178
	.word	37252
	.word	16426
	.word	0
	.word	0
	.word	0
	.word	0
	.word	53248
	.word	16386
	.word	0
	.word	0
	.word	32768
	.word	8436
	.word	46566
	.word	16429
	.word	0
	.word	0
	.word	0
	.word	0
	.word	57344
	.word	16386
	.word	0
	.word	0
	.word	40960
	.word	43313
	.word	58207
	.word	16432
	.word	0
	.word	0
	.word	0
	.word	0
	.word	61440
	.word	16386
	.word	0
	.word	0
	.word	1024
	.word	51647
	.word	36379
	.word	16436
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16387
	.word	0
	.word	0
	.word	50432
	.word	48174
	.word	45474
	.word	16439
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	16387
	.word	0
	.word	0
	.word	30272
	.word	27450
	.word	56843
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	35304
	.word	8964
	.word	35527
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	38912
	.word	16387
	.word	0
	.word	0
	.word	44130
	.word	60357
	.word	44408
	.word	16449
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16387
	.word	0
	.word	32768
	.word	6010
	.word	9911
	.word	55511
	.word	16452
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43008
	.word	16387
	.word	0
	.word	36864
	.word	28332
	.word	30770
	.word	34694
	.word	16456
	.word	0
	.word	0
	.word	0
	.word	0
	.word	45056
	.word	16387
	.word	0
	.word	46080
	.word	2647
	.word	5695
	.word	43368
	.word	16459
	.word	0
	.word	0
	.word	0
	.word	0
	.word	47104
	.word	16387
	.word	0
	.word	41216
	.word	52461
	.word	7118
	.word	54210
	.word	16462
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16387
	.word	0
	.word	33952
	.word	16404
	.word	20833
	.word	33881
	.word	16466
	.word	0
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	16387
	.word	0
	.word	42440
	.word	36889
	.word	42425
	.word	42351
	.word	16469
	.word	0
	.word	0
	.word	0
	.word	0
	.word	53248
	.word	16387
	.word	0
	.word	3898
	.word	62496
	.word	36647
	.word	52939
	.word	16472
	.word	0
	.word	0
	.word	0
	.word	0
	.word	55296
	.word	16387
	.word	0
	.type	_exact_values,@object
	.size	_exact_values,720
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_log10l_table_256
	.section .note.GNU-stack, ""
# End
