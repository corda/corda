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
	.file "atanhl.c"
	.text
..TXTST0:
# -- Begin  atanhl
	.text
       .align    16,0x90
	.globl atanhl
atanhl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movzwl    14(%esp), %edx
        cmpl      $16383, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jge       ..B1.53
..B1.4:
        cmpl      $16381, %eax
        jge       ..B1.46
..B1.5:
        cmpl      $16376, %eax
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %eax
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %eax
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %eax
        jge       ..B1.18
..B1.9:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.68
..B1.10:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.11:
        fldcw     12(%esp)
..B1.12:
        movzwl    16(%ebp), %eax
        movl      $1, %esi
        andl      $32767, %eax
..B1.13:
        testl     %eax, %eax
        jle       ..B1.64
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ebx)
        fstpt     (%esp)
..B1.15:
        testl     %esi, %esi
        je        ..B1.17
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.69
..B1.19:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.20:
        fldcw     12(%esp)
..B1.21:
        movl      $1, %eax
..B1.22:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _Q3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.24
..B1.23:
        fldcw     14(%esp)
..B1.24:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.26:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.27:
        fldcw     12(%esp)
..B1.28:
        movl      $1, %eax
..B1.29:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_Q2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
..B1.31:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.71
..B1.33:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.34:
        fldcw     12(%esp)
..B1.35:
        movl      $1, %eax
..B1.36:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.38
..B1.37:
        fldcw     14(%esp)
..B1.38:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.40:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.41:
        fldcw     12(%esp)
..B1.42:
        movl      $1, %eax
..B1.43:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_52H@GOTOFF(%ebx)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fldt      132+_Q@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      108+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      120+_Q@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      96+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_Q@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _Q@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      156+_Q@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fldt      144+_Q@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fldt      16(%esp)
        fld       %st(0)
        fldl      _TWO_48H@GOTOFF(%ebx)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fldt      8(%ebp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      (%esp)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.45
..B1.44:
        fldcw     14(%esp)
..B1.45:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.73
..B1.47:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.48:
        fldcw     12(%esp)
..B1.49:
        movl      $1, %ecx
..B1.50:
        fldt      8(%ebp)
        fldl      _ones@GOTOFF(%ebx)
        movzbl    17(%ebp), %edx
        fld       %st(0)
        andl      $128, %edx
        fld       %st(1)
        shrl      $7, %edx
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        fldl      _TWO_48H@GOTOFF(%ebx)
        movzwl    8(%esp), %edi
        fxch      %st(4)
        fmull     _ones@GOTOFF(%ebx,%edx,8)
        andl      $-32768, %edi
        fsubr     %st, %st(2)
        fld       %st(2)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fsubr     %st(2), %st
        fsub      %st(1), %st
        fld       %st(3)
        fadd      %st(1), %st
        fxch      %st(2)
        fadd      %st, %st(5)
        fld       %st(5)
        fmul      %st(7), %st
        fadd      %st, %st(6)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubr     %st(3), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fdivr     %st(3), %st
        fmul      %st, %st(6)
        fld       %st(0)
        fadd      %st(7), %st
        fsubp     %st, %st(7)
        fxch      %st(1)
        fmul      %st(6), %st
        fld       %st(1)
        fsub      %st(7), %st
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(4)
        fmul      %st(6), %st
        fsubrp    %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fldt      16(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     8(%ebp)
        fldt      60+_P@GOTOFF(%ebx)
        fldt      36+_P@GOTOFF(%ebx)
        fldt      12+_P@GOTOFF(%ebx)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movl      %eax, %esi
        addl      $-16383, %eax
        negl      %esi
        addl      $-2, %esi
        andl      $32767, %esi
        orl       %esi, %edi
        movw      %di, 8(%esp)
        fldt      (%esp)
        movl      12(%ebp), %esi
        shrl      $23, %esi
        fmul      %st, %st(4)
        fmulp     %st, %st(5)
        andl      $255, %esi
        movl      %eax, 32(%esp)
        fildl     32(%esp)
        fxch      %st(4)
        fmuls     __libm_rcp_table_256@GOTOFF(%ebx,%esi,4)
        fxch      %st(5)
        fmuls     __libm_rcp_table_256@GOTOFF(%ebx,%esi,4)
        fxch      %st(5)
        faddl     8+_ones@GOTOFF(%ebx)
        fld       %st(0)
        shll      $4, %esi
        fadd      %st(6), %st
        fld       %st(0)
        testl     %ecx, %ecx
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fstpt     8(%ebp)
        fldt      48+_P@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        faddl     8+__libm_logl_table_256@GOTOFF(%ebx,%esi)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddl     __libm_logl_table_256@GOTOFF(%esi,%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     halfs@GOTOFF(%ebx,%edx,8)
        fstpt     16(%esp)
        je        ..B1.52
..B1.51:
        fldcw     14(%esp)
..B1.52:
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.53:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.79
..B1.54:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.55:
        fldcw     12(%esp)
..B1.56:
        movzwl    16(%ebp), %eax
        movl      $1, %edx
        andl      $32767, %eax
..B1.57:
        cmpl      $32767, %eax
        jl        ..B1.74
..B1.58:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.60
..B1.59:
        cmpl      $0, 8(%ebp)
        je        ..B1.74
..B1.60:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ebx)
        fstpt     (%esp)
..B1.61:
        testl     %edx, %edx
        je        ..B1.63
..B1.62:
        fldcw     14(%esp)
..B1.63:
        fldt      (%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
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
        fldt      8(%ebp)
        fldt      _small_value_80@GOTOFF(%ebx)
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _small_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.67:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.68:
        xorl      %esi, %esi
        jmp       ..B1.13
..B1.69:
        xorl      %eax, %eax
        jmp       ..B1.22
..B1.70:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.71:
        xorl      %eax, %eax
        jmp       ..B1.36
..B1.72:
        xorl      %eax, %eax
        jmp       ..B1.43
..B1.73:
        xorl      %ecx, %ecx
        jmp       ..B1.50
..B1.74:
        cmpl      $16383, %eax
        jne       ..B1.78
..B1.75:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.78
..B1.76:
        cmpl      $0, 8(%ebp)
        jne       ..B1.78
..B1.77:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fdivl     _zeros@GOTOFF(%ebx)
        fstpt     (%esp)
        jmp       ..B1.61
..B1.78:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        fstpt     (%esp)
        jmp       ..B1.61
..B1.79:
        xorl      %edx, %edx
        jmp       ..B1.57
        .align    16,0x90
	.type	atanhl,@function
	.size	atanhl,.-atanhl
	.data
# -- End  atanhl
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
	.space 4, 0x00 	# pad
	.align 8
halfs:
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbfe00000
	.type	halfs,@object
	.size	halfs,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
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
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_Q3:
	.word	43691
	.word	45330
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	33124
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	58478
	.word	29023
	.word	52648
	.word	52428
	.word	16380
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	43686
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	5258
	.word	52447
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	10439
	.word	34405
	.word	9349
	.word	37449
	.word	16380
	.word	0
	.word	25253
	.word	49895
	.word	32943
	.word	58261
	.word	16379
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	43101
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	4657
	.word	52441
	.word	52428
	.word	52428
	.word	16368
	.word	0
	.word	42633
	.word	18723
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	11223
	.word	36719
	.word	14563
	.word	58254
	.word	16379
	.word	0
	.word	64465
	.word	27664
	.word	35746
	.word	47662
	.word	16379
	.word	0
	.word	6969
	.word	8211
	.word	55486
	.word	40329
	.word	16379
	.word	0
	.word	15522
	.word	40807
	.word	33480
	.word	34952
	.word	16379
	.word	0
	.word	44124
	.word	7207
	.word	21918
	.word	61682
	.word	16378
	.word	0
	.word	13236
	.word	62073
	.word	35694
	.word	55158
	.word	16378
	.word	0
	.word	859
	.word	55468
	.word	55479
	.word	50356
	.word	16378
	.word	0
	.word	19186
	.word	33607
	.word	822
	.word	41715
	.word	16378
	.word	0
	.word	51623
	.word	9477
	.word	49456
	.word	61855
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	0
	.word	52416
	.word	16380
	.word	0
	.type	_Q,@object
	.size	_Q,168
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
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.section .note.GNU-stack, ""
# End
