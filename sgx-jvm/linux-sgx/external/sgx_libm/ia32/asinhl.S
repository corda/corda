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
	.file "asinhl.c"
	.text
..TXTST0:
# -- Begin  asinhl
	.text
       .align    16,0x90
	.globl asinhl
asinhl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $56, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %ebx
        andl      $32767, %ebx
        movzwl    14(%esp), %eax
        cmpl      $32767, %ebx
        je        ..B1.73
..B1.4:
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $16382, %ebx
        jge       ..B1.46
..B1.5:
        cmpl      $16376, %ebx
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %ebx
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %ebx
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %ebx
        jge       ..B1.18
..B1.9:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.67
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.11:
        fldcw     12(%esp)
..B1.12:
        movzwl    16(%ebp), %ebx
        movl      $1, %eax
        andl      $32767, %ebx
..B1.13:
        testl     %ebx, %ebx
        jle       ..B1.63
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ecx)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.15:
        testl     %eax, %eax
        je        ..B1.17
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      (%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.68
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.20:
        fldcw     12(%esp)
..B1.21:
        movl      $1, %eax
..B1.22:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _Q3@GOTOFF(%ecx)
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
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.69
..B1.26:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.27:
        fldcw     12(%esp)
..B1.28:
        movl      $1, %eax
..B1.29:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_Q2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%ecx)
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
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.70
..B1.33:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
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
        fldt      36+_Q1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      12+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%ecx)
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
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.71
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.41:
        fldcw     12(%esp)
..B1.42:
        movl      $1, %eax
..B1.43:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fld       %st(1)
        fldl      _TWO_53H@GOTOFF(%ecx)
        fmul      %st(3), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(0)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        fmul      %st(0), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fadd      %st, %st(2)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(4)
        fstpt     16(%esp)
        fld       %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fadd      %st, %st(1)
        fldt      228+_Q@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      204+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      180+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      156+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      132+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      108+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      84+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      60+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      216+_Q@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      192+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      168+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      144+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      120+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      96+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      _Q@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      252+_Q@GOTOFF(%ecx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fldt      240+_Q@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%esp)
        fld       %st(0)
        fldl      _TWO_48H@GOTOFF(%ecx)
        fld       %st(0)
        fadd      %st(5), %st
        fsubp     %st, %st(1)
        fmul      %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      8(%ebp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%esp)
        fmul      %st, %st(2)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.45
..B1.44:
        fldcw     14(%esp)
..B1.45:
        fldt      (%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.72
..B1.47:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.48:
        fldcw     12(%esp)
..B1.49:
        movl      $1, %edx
..B1.50:
        fldt      8(%ebp)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      ones@GOTOFF(%ecx,%eax,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        movzwl    16(%ebp), %ebx
        movl      %ebx, %eax
        andl      $32767, %eax
        cmpl      $16421, %eax
        jge       ..B1.59
..B1.51:
        cmpl      $16388, %eax
        jge       ..B1.53
..B1.52:
        fldl      _TWO_52H@GOTOFF(%ecx)
        fld       %st(0)
        fld       %st(2)
        fadd      %st(2), %st
        fsub      %st(2), %st
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsqrt     
        fadd      %st, %st(4)
        fxch      %st(5)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(1)
        fld       %st(3)
        fxch      %st(4)
        fsubr     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(4), %st
        fdivrp    %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fxch      %st(1)
        jmp       ..B1.58
..B1.53:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        cmpl      $16394, %eax
        fmul      %st(1), %st
        fld       %st(0)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubr     %st(1), %st
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        jge       ..B1.55
..B1.54:
        fstp      %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fsqrt     
        faddp     %st, %st(3)
        fxch      %st(1)
        fdiv      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B1.58
..B1.55:
        fstp      %st(3)
        cmpl      $16401, %eax
        jge       ..B1.57
..B1.56:
        fld       %st(2)
        fdiv      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fdivr     %st(2), %st
        faddp     %st, %st(1)
        jmp       ..B1.58
..B1.57:
        fxch      %st(1)
        fdivr     %st(2), %st
        faddp     %st, %st(1)
..B1.58:
        fldt      16(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     8(%ebp)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movl      %eax, %ebx
        addl      $-16383, %eax
        negl      %ebx
        fxch      %st(2)
        fstpt     (%esp)
        addl      $-2, %ebx
        movzwl    8(%esp), %esi
        andl      $32767, %ebx
        andl      $-32768, %esi
        orl       %ebx, %esi
        movw      %si, 8(%esp)
        fldt      (%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%esp)
        fmulp     %st, %st(1)
        jmp       ..B1.60
..B1.59:
        fstp      %st(0)
        fldl      _TWO_52H@GOTOFF(%ecx)
        andl      $-32768, %ebx
        orl       $-49153, %ebx
        addl      $-16382, %eax
        movw      %bx, 16(%ebp)
        fldt      8(%ebp)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
..B1.60:
        fldt      16(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fldt      60+_P@GOTOFF(%ecx)
        movl      12(%ebp), %ebx
        shrl      $23, %ebx
        movzbl    %bl, %ebx
        movl      %eax, (%esp)
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%ebx,4)
        fmul      %st, %st(3)
        shll      $4, %ebx
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        testl     %edx, %edx
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        fmul      %st, %st(2)
        fxch      %st(4)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fildl     (%esp)
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fmulp     %st, %st(1)
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(5), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.5@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     8+__libm_logl_table_256@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(3)
        faddl     __libm_logl_table_256@GOTOFF(%ebx,%ecx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.62
..B1.61:
        fldcw     14(%esp)
..B1.62:
        fldt      16(%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.63:
        cmpl      $0, 12(%ebp)
        jne       ..B1.65
..B1.64:
        cmpl      $0, 8(%ebp)
        je        ..B1.66
..B1.65:
        fldt      8(%ebp)
        fldt      _small_value_80@GOTOFF(%ecx)
        movzbl    17(%ebp), %ebx
        andl      $128, %ebx
        shrl      $7, %ebx
        lea       (,%ebx,8), %edx
        lea       (%edx,%ebx,4), %esi
        fldt      _small_value_80@GOTOFF(%ecx,%esi)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.66:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.67:
        xorl      %eax, %eax
        jmp       ..B1.13
..B1.68:
        xorl      %eax, %eax
        jmp       ..B1.22
..B1.69:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.70:
        xorl      %eax, %eax
        jmp       ..B1.36
..B1.71:
        xorl      %eax, %eax
        jmp       ..B1.43
..B1.72:
        xorl      %edx, %edx
        jmp       ..B1.50
..B1.73:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.79
..B1.74:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.75:
        fldcw     12(%esp)
..B1.76:
        fldt      8(%ebp)
        fstpt     (%esp)
..B1.77:
        fldcw     14(%esp)
..B1.78:
        fldt      (%esp)
        addl      $56, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.79:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.78
        .align    16,0x90
	.type	asinhl,@function
	.size	asinhl,.-asinhl
	.data
# -- End  asinhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
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
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00
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
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_Q3:
	.word	6991
	.word	42216
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	33268
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	9764
	.word	39322
	.word	39161
	.word	39321
	.word	16379
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	43682
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	14144
	.word	39303
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	10686
	.word	40209
	.word	28068
	.word	46811
	.word	49146
	.word	0
	.word	6376
	.word	25459
	.word	13370
	.word	63707
	.word	16377
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	42228
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	4505
	.word	52442
	.word	52428
	.word	52428
	.word	49134
	.word	0
	.word	31967
	.word	56173
	.word	28086
	.word	46811
	.word	49146
	.word	0
	.word	26124
	.word	58234
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	22137
	.word	23222
	.word	53620
	.word	46917
	.word	49145
	.word	0
	.word	45395
	.word	61656
	.word	25205
	.word	36391
	.word	16377
	.word	0
	.word	28077
	.word	15147
	.word	52421
	.word	58572
	.word	49144
	.word	0
	.word	19650
	.word	2873
	.word	50009
	.word	48451
	.word	16376
	.word	0
	.word	47538
	.word	64725
	.word	9133
	.word	40943
	.word	49144
	.word	0
	.word	42460
	.word	64288
	.word	29314
	.word	35191
	.word	16376
	.word	0
	.word	54924
	.word	18085
	.word	19628
	.word	61339
	.word	49143
	.word	0
	.word	8359
	.word	63244
	.word	48955
	.word	54066
	.word	16375
	.word	0
	.word	13301
	.word	22367
	.word	28967
	.word	48066
	.word	49143
	.word	0
	.word	47487
	.word	41074
	.word	54143
	.word	42870
	.word	16375
	.word	0
	.word	18176
	.word	28317
	.word	25378
	.word	37849
	.word	49143
	.word	0
	.word	2947
	.word	59345
	.word	39582
	.word	64148
	.word	16374
	.word	0
	.word	7071
	.word	46945
	.word	60101
	.word	49232
	.word	49142
	.word	0
	.word	33184
	.word	23492
	.word	18153
	.word	62298
	.word	16373
	.word	0
	.word	44779
	.word	60944
	.word	30881
	.word	55868
	.word	49140
	.word	0
	.word	22870
	.word	51718
	.word	28591
	.word	51709
	.word	16370
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
	.word	39328
	.word	16379
	.word	0
	.type	_Q,@object
	.size	_Q,264
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
