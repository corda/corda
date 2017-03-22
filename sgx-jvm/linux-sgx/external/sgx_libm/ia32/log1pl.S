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
	.file "log1pl.c"
	.text
..TXTST0:
# -- Begin  log1pl
	.text
       .align    16,0x90
	.globl log1pl
log1pl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $56, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, %edx
        movzwl    16(%ebp), %edi
        shll      $15, %edx
        andl      $32767, %edi
        orl       %edi, %edx
        shll      $16, %edx
        movzwl    14(%ebp), %ecx
        orl       %ecx, %edx
        movzwl    14(%esp), %ebx
        cmpl      $32767, %edi
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        je        ..B1.78
..B1.4:
        cmpl      $-1073774592, %edx
        jae       ..B1.57
..B1.5:
        cmpl      $16379, %edi
        jge       ..B1.47
..B1.6:
        cmpl      $16369, %edi
        jge       ..B1.40
..B1.7:
        cmpl      $16358, %edi
        jge       ..B1.33
..B1.8:
        cmpl      $16345, %edi
        jge       ..B1.26
..B1.9:
        cmpl      $16308, %edi
        jge       ..B1.19
..B1.10:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.71
..B1.11:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.12:
        fldcw     12(%esp)
..B1.13:
        movzwl    16(%ebp), %edi
        movl      $1, %edx
        andl      $32767, %edi
..B1.14:
        testl     %edi, %edi
        jle       ..B1.67
..B1.15:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ecx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ecx,%eax,8)
        fsubrp    %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     16(%esp)
..B1.16:
        testl     %edx, %edx
        je        ..B1.18
..B1.17:
        fldcw     14(%esp)
..B1.18:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.20:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.21:
        fldcw     12(%esp)
..B1.22:
        movl      $1, %eax
..B1.23:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _Q3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fadd      %st(1), %st
        fstpt     16(%esp)
        fldl      _TWO_75@GOTOFF(%ecx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.25
..B1.24:
        fldcw     14(%esp)
..B1.25:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.73
..B1.27:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.28:
        fldcw     12(%esp)
..B1.29:
        movl      $1, %eax
..B1.30:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      12+_Q2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.32
..B1.31:
        fldcw     14(%esp)
..B1.32:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.33:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.74
..B1.34:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.35:
        fldcw     12(%esp)
..B1.36:
        movl      $1, %eax
..B1.37:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      12+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.39
..B1.38:
        fldcw     14(%esp)
..B1.39:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.40:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.75
..B1.41:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.42:
        fldcw     12(%esp)
..B1.43:
        movl      $1, %eax
..B1.44:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldl      _TWO_52H@GOTOFF(%ecx)
        fmul      %st(4), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(3)
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(0), %st
        fadd      %st, %st(3)
        fldt      156+_Q@GOTOFF(%ecx)
        fmul      %st(4), %st
        fldt      132+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      108+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      84+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      60+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      36+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      12+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      144+_Q@GOTOFF(%ecx)
        fmul      %st(5), %st
        fldt      120+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      96+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      72+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      48+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      24+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fld       %st(2)
        fldt      _Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fxch      %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        fldt      180+_Q@GOTOFF(%ecx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(1)
        fldt      168+_Q@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(5)
        fldt      32(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.46
..B1.45:
        fldcw     14(%esp)
..B1.46:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.47:
        movl      %ebx, %edi
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.76
..B1.48:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.49:
        fldcw     12(%esp)
..B1.50:
        movzbl    17(%ebp), %eax
        movl      $1, %ebx
        andl      $128, %eax
        shrl      $7, %eax
..B1.51:
        cmpl      ranges@GOTOFF(%ecx,%eax,4), %edx
        jae       ..B1.53
..B1.52:
        fldt      8(%ebp)
        xorl      %edx, %edx
        movzwl    16(%ebp), %edi
        fld       %st(0)
        fldl      _ones@GOTOFF(%ecx)
        andl      $32767, %edi
        addl      $-16415, %edi
        fstpt     16(%esp)
        setl      %dl
        fldl      _shifters@GOTOFF(%ecx,%edx,8)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        faddl     zero_one@GOTOFF(%ecx,%edx,8)
        xorl      $1, %edx
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(1)
        faddl     zero_one@GOTOFF(%ecx,%edx,8)
        movzwl    24(%esp), %edx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%ebp)
        andl      $-32768, %edx
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movl      %eax, %edi
        addl      $-16383, %eax
        negl      %edi
        addl      $-2, %edi
        andl      $32767, %edi
        orl       %edi, %edx
        movl      12(%ebp), %edi
        shrl      $23, %edi
        andl      $255, %edi
        movw      %dx, 24(%esp)
        fldt      16(%esp)
        fmuls     __libm_rcp_table_256@GOTOFF(%ecx,%edi,4)
        jmp       ..B1.54
..B1.53:
        fldt      8(%ebp)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fxch      %st(1)
        faddl     zero_one@GOTOFF(%ecx,%eax,8)
        fstpt     8(%ebp)
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        andl      $-32768, %edx
        andl      $32767, %eax
        orl       $-49153, %edx
        addl      $-16383, %eax
        movw      %dx, 16(%ebp)
        fldt      8(%ebp)
        fld       %st(0)
        movl      12(%ebp), %edi
        fadd      %st(2), %st
        shrl      $23, %edi
        fsubp     %st, %st(2)
        andl      $255, %edi
        fsub      %st(1), %st
        fxch      %st(1)
        fstpt     32(%esp)
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%edi,4)
..B1.54:
        fldt      32(%esp)
        fmul      %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        movl      %eax, 16(%esp)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fildl     16(%esp)
        fldt      60+_P@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      36+_P@GOTOFF(%ecx)
        shll      $4, %edi
        faddp     %st, %st(1)
        fmul      %st(3), %st
        testl     %ebx, %ebx
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmulp     %st, %st(2)
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     8+__libm_logl_table_256@GOTOFF(%ecx,%edi)
        faddp     %st, %st(3)
        faddl     __libm_logl_table_256@GOTOFF(%edi,%ecx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        je        ..B1.56
..B1.55:
        fldcw     14(%esp)
..B1.56:
        fldt      32(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.77
..B1.58:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.59:
        fldcw     12(%esp)
..B1.60:
        movl      $1, %edx
..B1.61:
        fldl      8+_ones@GOTOFF(%ecx)
        fstpt     16(%esp)
        fldt      16(%esp)
        fldl      _zeros@GOTOFF(%ecx)
        fldt      8(%ebp)
        fucomp    %st(2)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jp        ..B1.62
        je        ..B1.63
..B1.62:
        fldl      _infs@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.64
..B1.63:
        fldt      16(%esp)
        fdivp     %st, %st(1)
        fstpt     16(%esp)
..B1.64:
        testl     %edx, %edx
        je        ..B1.66
..B1.65:
        fldcw     14(%esp)
..B1.66:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.67:
        cmpl      $0, 12(%ebp)
        jne       ..B1.69
..B1.68:
        cmpl      $0, 8(%ebp)
        je        ..B1.70
..B1.69:
        fldt      _small_value_80@GOTOFF(%ecx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmul      %st(0), %st
        fstpt     (%esp)
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ecx,%eax,8)
        fsubrp    %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     16(%esp)
        jmp       ..B1.16
..B1.70:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.16
..B1.71:
        xorl      %edx, %edx
        jmp       ..B1.14
..B1.72:
        xorl      %eax, %eax
        jmp       ..B1.23
..B1.73:
        xorl      %eax, %eax
        jmp       ..B1.30
..B1.74:
        xorl      %eax, %eax
        jmp       ..B1.37
..B1.75:
        xorl      %eax, %eax
        jmp       ..B1.44
..B1.76:
        xorl      %ebx, %ebx
        jmp       ..B1.51
..B1.77:
        xorl      %edx, %edx
        jmp       ..B1.61
..B1.78:
        movl      %ebx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.90
..B1.79:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.80:
        fldcw     12(%esp)
..B1.81:
        movzbl    17(%ebp), %eax
        movl      $1, %edx
        andl      $128, %eax
        shrl      $7, %eax
..B1.82:
        testl     %eax, %eax
        je        ..B1.85
..B1.83:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.85
..B1.84:
        cmpl      $0, 8(%ebp)
        je        ..B1.89
..B1.85:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     16(%esp)
..B1.86:
        testl     %edx, %edx
        je        ..B1.88
..B1.87:
        fldcw     14(%esp)
..B1.88:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.89:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     16(%esp)
        jmp       ..B1.86
..B1.90:
        xorl      %edx, %edx
        jmp       ..B1.82
        .align    16,0x90
	.type	log1pl,@function
	.size	log1pl,.-log1pl
	.data
# -- End  log1pl
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
zero_one:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x3ff00000
	.type	zero_one,@object
	.size	zero_one,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
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
ranges:
	.long	1078558720
	.long	3221127168
	.type	ranges,@object
	.size	ranges,8
	.align 4
_shifters:
	.long	0
	.long	1098383360
	.long	0
	.long	1123549184
	.type	_shifters,@object
	.size	_shifters,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
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
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	4096
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	53521
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	65509
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	43631
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	43680
	.word	43690
	.word	6
	.word	32768
	.word	49149
	.word	0
	.word	60885
	.word	52536
	.word	52444
	.word	52428
	.word	16380
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	14363
	.word	62279
	.word	27629
	.word	62293
	.word	49080
	.word	0
	.word	44555
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	64968
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	50395
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	39590
	.word	43714
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	15749
	.word	18782
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	61040
	.word	7204
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	10454
	.word	61175
	.word	14561
	.word	58254
	.word	16379
	.word	0
	.word	57771
	.word	22430
	.word	52985
	.word	52428
	.word	49147
	.word	0
	.word	7036
	.word	15828
	.word	36565
	.word	47662
	.word	16379
	.word	0
	.word	17194
	.word	46991
	.word	54030
	.word	43687
	.word	49147
	.word	0
	.word	63541
	.word	11931
	.word	21910
	.word	40326
	.word	16379
	.word	0
	.word	36457
	.word	1697
	.word	61895
	.word	37932
	.word	49147
	.word	0
	.word	65454
	.word	16107
	.word	6024
	.word	35469
	.word	16379
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
	.word	43680
	.word	16381
	.word	0
	.type	_Q,@object
	.size	_Q,192
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
