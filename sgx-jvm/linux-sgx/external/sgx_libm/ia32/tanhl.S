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
	.file "tanhl.c"
	.text
..TXTST0:
# -- Begin  tanhl
	.text
       .align    16,0x90
	.globl tanhl
tanhl:
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
        fnstcw    54(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movzwl    54(%esp), %ecx
        cmpl      $16388, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        jge       ..B1.56
..B1.4:
        cmpl      $16382, %eax
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
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.69
..B1.10:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.11:
        fldcw     52(%esp)
..B1.12:
        movzwl    16(%ebp), %eax
        movl      $1, %esi
        andl      $32767, %eax
..B1.13:
        testl     %eax, %eax
        jle       ..B1.65
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%edx)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%edx)
        fstpt     16(%esp)
..B1.15:
        testl     %esi, %esi
        je        ..B1.17
..B1.16:
        fldcw     54(%esp)
..B1.17:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.19:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.20:
        fldcw     52(%esp)
..B1.21:
        movl      $1, %eax
..B1.22:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _Q3@GOTOFF(%edx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.24
..B1.23:
        fldcw     54(%esp)
..B1.24:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.71
..B1.26:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.27:
        fldcw     52(%esp)
..B1.28:
        movl      $1, %eax
..B1.29:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_Q2@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.31
..B1.30:
        fldcw     54(%esp)
..B1.31:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.33:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.34:
        fldcw     52(%esp)
..B1.35:
        movl      $1, %eax
..B1.36:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      12+_Q1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%edx)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.38
..B1.37:
        fldcw     54(%esp)
..B1.38:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.73
..B1.40:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.41:
        fldcw     52(%esp)
..B1.42:
        movl      $1, %ecx
..B1.43:
        fldt      8(%ebp)
        fldl      _TWO_52H@GOTOFF(%edx)
        fldt      180+_Q@GOTOFF(%edx)
        fldt      168+_Q@GOTOFF(%edx)
        fldt      156+_Q@GOTOFF(%edx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        testl     %ecx, %ecx
        fxch      %st(4)
        fmull     ones@GOTOFF(%edx,%eax,8)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(6)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fsubr     %st(1), %st
        fxch      %st(5)
        fstpt     16(%esp)
        fldt      16(%esp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      144+_Q@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(6), %st
        fldt      132+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      120+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      108+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      96+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      84+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      72+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      60+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      48+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      36+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      24+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        fldt      12+_Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(6)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st(0), %st
        fldt      _Q@GOTOFF(%edx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(6)
        fld       %st(4)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      204+_Q@GOTOFF(%edx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(7)
        fmulp     %st, %st(1)
        fldt      192+_Q@GOTOFF(%edx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(2)
        fldl      _TWO_48H@GOTOFF(%edx)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(3)
        fxch      %st(6)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%esp)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmull     ones@GOTOFF(%edx,%eax,8)
        fxch      %st(2)
        fmull     ones@GOTOFF(%edx,%eax,8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        je        ..B1.83
..B1.44:
        fstpt     28(%esp)
..B1.82:
        fldcw     54(%esp)
        jmp       ..B1.45
..B1.83:
        fstp      %st(0)
..B1.45:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.74
..B1.47:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.48:
        fldcw     52(%esp)
..B1.49:
        movzwl    16(%ebp), %eax
        movl      $1, %ebx
..B1.50:
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fldl      _TWO_32@GOTOFF(%edx)
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        fstl      56(%esp)
        shll      $16, %eax
        fldl      twos@GOTOFF(%edx,%esi,8)
        fmul      %st, %st(5)
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st(4), %st
        fstpt     28(%esp)
        fldt      28(%esp)
        fsubp     %st, %st(4)
        fxch      %st(1)
        fmul      %st(3), %st
        movl      28(%esp), %ecx
        fsubrp    %st, %st(4)
        fld       %st(3)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(4)
        movsbl    %cl, %edi
        fsub      %st(3), %st
        fldl      _TWO_32P@GOTOFF(%edx)
        subl      %edi, %ecx
        fmul      %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        shll      $4, %edi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        shrl      $8, %ecx
        fxch      %st(3)
        fsubrp    %st, %st(4)
        shll      $23, %ecx
        fadd      %st(3), %st
        fld       %st(0)
        addl      $1065353216, %ecx
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        movl      %ecx, 12(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        movzwl    14(%ebp), %ecx
        orl       %ecx, %eax
        andl      $2147483647, %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        cmpl      $1073845862, %eax
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(5)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%edi)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%edi)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(4)
        fadd      %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     16(%esp)
        fmul      %st(2), %st
        flds      12(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        jae       ..B1.52
..B1.51:
        fldl      56(%esp)
        fld       %st(5)
        fadd      %st(4), %st
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st(3), %st
        fld       %st(0)
        fldt      16(%esp)
        faddp     %st, %st(6)
        fxch      %st(5)
        fmulp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fdivr     %st(4), %st
        fmul      %st, %st(2)
        fldl      _TWO_32H@GOTOFF(%edx)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     28(%esp)
        fldt      28(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fmul      %st, %st(7)
        fxch      %st(4)
        faddp     %st, %st(7)
        fmul      %st, %st(2)
        fxch      %st(5)
        fsubrp    %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fsubp     %st, %st(3)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fsubrl    ones@GOTOFF(%edx,%esi,8)
        fsubp     %st, %st(1)
        fstpt     40(%esp)
        jmp       ..B1.53
..B1.52:
        fldt      16(%esp)
        faddp     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fdivrp    %st, %st(1)
        fsubrl    ones@GOTOFF(%edx,%esi,8)
        fstpt     40(%esp)
..B1.53:
        testl     %ebx, %ebx
        je        ..B1.55
..B1.54:
        fldcw     54(%esp)
..B1.55:
        fldt      40(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.56:
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.79
..B1.57:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.58:
        fldcw     52(%esp)
..B1.59:
        movzwl    16(%ebp), %eax
        movl      $1, %ecx
        andl      $32767, %eax
..B1.60:
        cmpl      $32767, %eax
        je        ..B1.75
..B1.61:
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %ebx
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fsubrl    ones@GOTOFF(%edx,%esi,8)
        fstpt     16(%esp)
..B1.62:
        testl     %ecx, %ecx
        je        ..B1.64
..B1.63:
        fldcw     54(%esp)
..B1.64:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.65:
        cmpl      $0, 12(%ebp)
        jne       ..B1.67
..B1.66:
        cmpl      $0, 8(%ebp)
        je        ..B1.68
..B1.67:
        fldt      _small_value_80@GOTOFF(%edx)
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%edx)
        fmul      %st(1), %st
        movzbl    17(%ebp), %ecx
        fsubp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%edx)
        andl      $128, %ecx
        shrl      $7, %ecx
        fstpt     16(%esp)
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %ebx
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.68:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.15
..B1.69:
        xorl      %esi, %esi
        jmp       ..B1.13
..B1.70:
        xorl      %eax, %eax
        jmp       ..B1.22
..B1.71:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.72:
        xorl      %eax, %eax
        jmp       ..B1.36
..B1.73:
        xorl      %ecx, %ecx
        jmp       ..B1.43
..B1.74:
        xorl      %ebx, %ebx
        jmp       ..B1.50
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
        fldl      ones@GOTOFF(%edx,%eax,8)
        fstpt     16(%esp)
        jmp       ..B1.62
..B1.78:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.62
..B1.79:
        xorl      %ecx, %ecx
        jmp       ..B1.60
        .align    16,0x90
	.type	tanhl,@function
	.size	tanhl,.-tanhl
	.data
# -- End  tanhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0xf0,0x17,0x5c,0x29,0x3b,0xaa,0xb8,0x07,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xf6,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xd4,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
twos:
	.long	0x00000000,0x40000000
	.long	0x00000000,0xc0000000
	.type	twos,@object
	.size	twos,16
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
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.align 4
_TWO_32P:
	.long	1048576
	.long	1106247680
	.type	_TWO_32P,@object
	.size	_TWO_32P,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_Q3:
	.word	21845
	.word	42325
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	36147
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	55638
	.word	59918
	.word	34848
	.word	34952
	.word	16380
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	43688
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	48627
	.word	34947
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	23872
	.word	38674
	.word	53460
	.word	56589
	.word	49146
	.word	0
	.word	33152
	.word	13396
	.word	6324
	.word	45860
	.word	16377
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	43505
	.word	43690
	.word	43690
	.word	43690
	.word	49137
	.word	0
	.word	51768
	.word	34951
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	53715
	.word	3536
	.word	53469
	.word	56589
	.word	49146
	.word	0
	.word	37274
	.word	24708
	.word	42049
	.word	45863
	.word	16377
	.word	0
	.word	51222
	.word	13677
	.word	6831
	.word	37175
	.word	49144
	.word	0
	.word	45961
	.word	31945
	.word	59504
	.word	60265
	.word	16374
	.word	0
	.word	5972
	.word	17449
	.word	45604
	.word	48849
	.word	49141
	.word	0
	.word	32816
	.word	2946
	.word	4564
	.word	39596
	.word	16372
	.word	0
	.word	14786
	.word	2112
	.word	44465
	.word	64190
	.word	49138
	.word	0
	.word	3031
	.word	16844
	.word	22916
	.word	52030
	.word	16369
	.word	0
	.word	47485
	.word	32270
	.word	51436
	.word	42167
	.word	49136
	.word	0
	.word	3071
	.word	14344
	.word	30185
	.word	34131
	.word	16367
	.word	0
	.word	24996
	.word	54454
	.word	53234
	.word	54781
	.word	49133
	.word	0
	.word	39193
	.word	24581
	.word	37984
	.word	42131
	.word	16364
	.word	0
	.word	5913
	.word	28127
	.word	47865
	.word	55395
	.word	49130
	.word	0
	.word	25952
	.word	54950
	.word	21582
	.word	44803
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34944
	.word	16380
	.word	0
	.type	_Q,@object
	.size	_Q,216
	.align 2
_P:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.type	_P,@object
	.size	_P,60
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
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
