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
	.file "atanl.c"
	.text
..TXTST0:
# -- Begin  atanl
	.text
       .align    16,0x90
	.globl atanl
atanl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $72, %esp
..B1.2:
        fnstcw    38(%esp)
..B1.3:
        movzbl    17(%ebp), %ecx
        movzwl    16(%ebp), %eax
        andl      $128, %ecx
        andl      $32767, %eax
        shrl      $7, %ecx
        movzwl    38(%esp), %ebx
        cmpl      $16394, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        jge       ..B1.48
..B1.4:
        cmpl      $16376, %eax
        jge       ..B1.38
..B1.5:
        cmpl      $16371, %eax
        jge       ..B1.31
..B1.6:
        cmpl      $16365, %eax
        jge       ..B1.24
..B1.7:
        cmpl      $16308, %eax
        jge       ..B1.17
..B1.8:
        movl      %ebx, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.68
..B1.9:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.10:
        fldcw     36(%esp)
..B1.11:
        movzwl    16(%ebp), %eax
        movl      $1, %ebx
        andl      $32767, %eax
..B1.12:
        testl     %eax, %eax
        jle       ..B1.64
..B1.13:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%edx)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%edx)
        fstpt     (%esp)
..B1.14:
        testl     %ebx, %ebx
        je        ..B1.16
..B1.15:
        fldcw     38(%esp)
..B1.16:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.69
..B1.18:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.19:
        fldcw     36(%esp)
..B1.20:
        movl      $1, %eax
..B1.21:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _P2@GOTOFF(%edx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.23
..B1.22:
        fldcw     38(%esp)
..B1.23:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.25:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.26:
        fldcw     36(%esp)
..B1.27:
        movl      $1, %eax
..B1.28:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_P1@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _P1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.30
..B1.29:
        fldcw     38(%esp)
..B1.30:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.71
..B1.32:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.33:
        fldcw     36(%esp)
..B1.34:
        movl      $1, %eax
..B1.35:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(2)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.37
..B1.36:
        fldcw     38(%esp)
..B1.37:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.39:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.40:
        fldcw     36(%esp)
..B1.41:
        movl      $1, %ebx
..B1.42:
        fldl      ones@GOTOFF(%edx,%ecx,8)
        fstl      56(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        cmpl      $16383, %eax
        jge       ..B1.44
..B1.43:
        fldl      _TWO_48H@GOTOFF(%edx)
        fld       %st(3)
        fmul      %st(1), %st
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(3)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fld       %st(3)
        movl      12(%esp), %eax
        fsub      %st(3), %st
        fld       %st(0)
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(4)
        fmul      %st, %st(5)
        fmulp     %st, %st(4)
        fld       %st(1)
        fmul      %st(1), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fadd      %st, %st(4)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fstpt     (%esp)
        fxch      %st(3)
        fxch      %st(4)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.45
..B1.44:
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fld       %st(3)
        fdivr     %st(1), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fld       %st(3)
        movl      12(%esp), %eax
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(3)
        negl      %eax
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        addl      $256, %eax
        fmulp     %st, %st(2)
        fldl      _TWO_48H@GOTOFF(%edx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(4)
        fsubr     %st, %st(5)
        fld       %st(5)
        fmul      %st(3), %st
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fstpt     (%esp)
        fldt      (%esp)
        faddp     %st, %st(6)
        fld       %st(1)
        fadd      %st(4), %st
        fmul      %st, %st(3)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(5)
        fldl      _TWO_48H@GOTOFF(%edx)
..B1.45:
        fld       %st(1)
        shll      $4, %eax
        fadd      %st(6), %st
        fdivrp    %st, %st(4)
        testl     %ebx, %ebx
        fmul      %st(3), %st
        fld       %st(0)
        fadd      %st(4), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(4), %st
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(5)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(7)
        faddp     %st, %st(3)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      (%esp)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fldt      40(%esp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(3), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(4)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      __libm_atanl_table_128@GOTOFF(%edx,%eax)
        fld       %st(0)
        fadd      %st(2), %st
        fldl      _TWO_32H@GOTOFF(%edx)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddl     8+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     56(%esp)
        fstpt     24(%esp)
        je        ..B1.80
..B1.46:
        fstpt     12(%esp)
..B1.79:
        fldcw     38(%esp)
        jmp       ..B1.47
..B1.80:
        fstp      %st(0)
..B1.47:
        fldt      24(%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.48:
        movl      %ebx, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.76
..B1.49:
        orl       $-64768, %ebx
        movw      %bx, 36(%esp)
..B1.50:
        fldcw     36(%esp)
..B1.51:
        movzwl    16(%ebp), %eax
        movl      $1, %ebx
        andl      $32767, %eax
..B1.52:
        cmpl      $32767, %eax
        je        ..B1.73
..B1.53:
        cmpl      $16448, %eax
        jge       ..B1.60
..B1.54:
        fldt      8(%ebp)
        xorl      %eax, %eax
        testl     %ecx, %ecx
        sete      %al
        fdivrl    ones@GOTOFF(%edx,%eax,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        fstpt     (%esp)
        cmpl      $16398, %eax
        jge       ..B1.56
..B1.55:
        fldt      (%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_P1@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _P1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.59
..B1.56:
        cmpl      $16408, %eax
        jge       ..B1.59
..B1.57:
        fldt      _P2@GOTOFF(%edx)
        fldt      (%esp)
        fmul      %st, %st(1)
        fmul      %st, %st(1)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.59:
        fldt      .L_2il0floatpacket.4@GOTOFF(%edx)
        fldt      (%esp)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.5@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmull     ones@GOTOFF(%edx,%ecx,8)
        fstpt     (%esp)
        jmp       ..B1.61
..B1.60:
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %ecx
        fldt      _pi_2l@GOTOFF(%ecx,%edx)
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
..B1.61:
        testl     %ebx, %ebx
        je        ..B1.63
..B1.62:
        fldcw     38(%esp)
..B1.63:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.64:
        cmpl      $0, 12(%ebp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 8(%ebp)
        je        ..B1.67
..B1.66:
        fldt      _small_value_80@GOTOFF(%edx)
        lea       (,%ecx,8), %eax
        fldt      8(%ebp)
        lea       (%eax,%ecx,4), %ecx
        fldt      _small_value_80@GOTOFF(%edx,%ecx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.14
..B1.67:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.14
..B1.68:
        xorl      %ebx, %ebx
        jmp       ..B1.12
..B1.69:
        xorl      %eax, %eax
        jmp       ..B1.21
..B1.70:
        xorl      %eax, %eax
        jmp       ..B1.28
..B1.71:
        xorl      %eax, %eax
        jmp       ..B1.35
..B1.72:
        xorl      %ebx, %ebx
        jmp       ..B1.42
..B1.73:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.75
..B1.74:
        cmpl      $0, 8(%ebp)
        je        ..B1.53
..B1.75:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.61
..B1.76:
        xorl      %ebx, %ebx
        jmp       ..B1.52
        .align    16,0x90
	.type	atanl,@function
	.size	atanl,.-atanl
	.data
# -- End  atanl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x88,0x19,0x13,0xd3,0x08,0xa3,0x85,0xdd,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0xa2,0xda,0x0f,0xc9,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.space 4, 0x00 	# pad
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_P2:
	.word	43691
	.word	41642
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.type	_P2,@object
	.size	_P2,12
	.align 2
_P1:
	.word	23723
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	22830
	.word	33705
	.word	52154
	.word	52428
	.word	16380
	.word	0
	.type	_P1,@object
	.size	_P1,24
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.type	_P,@object
	.size	_P,48
	.align 2
_pi_2l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49151
	.word	0
	.type	_pi_2l,@object
	.size	_pi_2l,24
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
	.data
	.hidden __libm_atanl_table_128
	.section .note.GNU-stack, ""
# End
