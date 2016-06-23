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
	.file "cbrtl.c"
	.text
..TXTST0:
# -- Begin  cbrtl
	.text
       .align    16,0x90
	.globl cbrtl
cbrtl:
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
        fnstcw    30(%esp)
..B1.3:
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        andl      $32767, %ebx
        movzwl    30(%esp), %ecx
        cmpl      $32767, %ebx
        je        ..B1.23
..B1.4:
        movl      %ecx, %edx
        movzbl    17(%ebp), %eax
        andl      $768, %edx
        andl      $128, %eax
        shrl      $7, %eax
        cmpl      $768, %edx
        je        ..B1.22
..B1.5:
        orl       $-64768, %ecx
        movw      %cx, 28(%esp)
..B1.6:
        fldcw     28(%esp)
..B1.7:
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        movl      $1, %edi
        andl      $32767, %ebx
..B1.8:
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        testl     %ebx, %ebx
        jne       ..B1.15
..B1.9:
        cmpl      $0, 12(%ebp)
        jne       ..B1.14
..B1.10:
        cmpl      $0, 8(%ebp)
        jne       ..B1.14
..B1.11:
        fldl      _zeros@GOTOFF(%ecx,%eax,8)
        testl     %edi, %edi
        fstpl     (%esp)
        je        ..B1.13
..B1.12:
        fldcw     30(%esp)
..B1.13:
        fldl      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        fldt      8(%ebp)
        movl      $-25, %edx
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
        movzwl    16(%ebp), %esi
        movl      %esi, %ebx
        andl      $32767, %ebx
        jmp       ..B1.16
..B1.15:
        xorl      %edx, %edx
..B1.16:
        fldl      ones@GOTOFF(%ecx,%eax,8)
        andl      $-32768, %esi
        imull     $21845, %ebx, %eax
        orl       $-49153, %esi
        fstpt     (%esp)
        fldt      (%esp)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fld       %st(0)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        movw      %si, 16(%ebp)
        addl      $21845, %eax
        fldt      8(%ebp)
        fmulp     %st, %st(4)
        shrl      $16, %eax
        fxch      %st(1)
        fadd      %st(3), %st
        movl      %edi, 12(%esp)
        lea       (%eax,%eax), %edi
        subl      %edi, %ebx
        lea       10922(%edx,%eax), %edx
        movl      12(%ebp), %edi
        fsubp     %st, %st(2)
        shrl      $23, %edi
        fxch      %st(1)
        fsubr     %st, %st(2)
        fldt      72+_P@GOTOFF(%ecx)
        fldt      48+_P@GOTOFF(%ecx)
        andl      $255, %edi
        movzwl    8(%esp), %esi
        andl      $-32768, %esi
        orl       %edx, %esi
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%edi,4)
        fld       %st(0)
        fmulp     %st, %st(4)
        shll      $4, %edi
        fxch      %st(3)
        fsub      %st(4), %st
        fxch      %st(3)
        fmulp     %st, %st(5)
        movw      %si, 8(%esp)
        subl      %eax, %ebx
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fmul      %st(1), %st
        fldt      60+_P@GOTOFF(%ecx)
        fmul      %st(4), %st
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      _P@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fadd      %st(1), %st
        fstpt     16(%esp)
        fldl      8+__libm_cbrtl_table_256@GOTOFF(%ecx,%edi)
        fldl      __libm_cbrtl_table_256@GOTOFF(%ecx,%edi)
        fldt      (%esp)
        movl      12(%esp), %edi
        jne       ..B1.18
..B1.17:
        fldt      16(%esp)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.19
..B1.18:
        fxch      %st(2)
        fmull     -8+_SH@GOTOFF(%ecx,%ebx,8)
        fldl      24+_SH@GOTOFF(%ecx,%ebx,8)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmull     8+_SH@GOTOFF(%ecx,%ebx,8)
        fmul      %st, %st(3)
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
..B1.19:
        testl     %edi, %edi
        je        ..B1.21
..B1.20:
        fldcw     30(%esp)
..B1.21:
        fldt      16(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        xorl      %edi, %edi
        jmp       ..B1.8
..B1.23:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.24:
        orl       $-64768, %ecx
        movw      %cx, 28(%esp)
..B1.25:
        fldcw     28(%esp)
..B1.26:
        fldt      8(%ebp)
        fstpt     (%esp)
..B1.27:
        fldcw     30(%esp)
..B1.28:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.28
        .align    16,0x90
	.type	cbrtl,@function
	.size	cbrtl,.-cbrtl
	.data
# -- End  cbrtl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
_SH:
	.long	0xf98d728b,0x3ff428a2
	.long	0xa53d6e3d,0x3ff965fe
	.long	0xf8000000,0x3ff428a2
	.long	0xa4000000,0x3ff965fe
	.long	0xae223ddb,0x3e38d728
	.long	0xc82b059a,0x3e33d6e3
	.type	_SH,@object
	.size	_SH,48
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_P:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	58320
	.word	36408
	.word	14563
	.word	58254
	.word	49147
	.word	0
	.word	50474
	.word	25890
	.word	59872
	.word	64726
	.word	16378
	.word	0
	.word	45217
	.word	37017
	.word	18069
	.word	43151
	.word	49146
	.word	0
	.word	45059
	.word	7616
	.word	35240
	.word	63288
	.word	16377
	.word	0
	.word	54522
	.word	17175
	.word	48475
	.word	49224
	.word	49145
	.word	0
	.word	568
	.word	55426
	.word	44619
	.word	39848
	.word	16377
	.word	0
	.type	_P,@object
	.size	_P,84
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_cbrtl_table_256
	.section .note.GNU-stack, ""
# End
