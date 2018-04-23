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
	.file "coshl.c"
	.text
..TXTST0:
# -- Begin  coshl
	.text
       .align    16,0x90
	.globl coshl
coshl:
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
        fnstcw    58(%esp)
..B1.3:
        movzwl    16(%ebp), %ebx
        andl      $32767, %ebx
        movl      12(%ebp), %ecx
        movl      %ebx, %esi
        movl      %ecx, %edx
        shll      $16, %esi
        shrl      $16, %edx
        movzbl    17(%ebp), %eax
        orl       %edx, %esi
        andl      $128, %eax
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $1073992020, %esi
        jae       ..B1.50
..B1.4:
        movzwl    58(%esp), %ecx
        cmpl      $16382, %ebx
        jge       ..B1.43
..B1.5:
        cmpl      $16378, %ebx
        jge       ..B1.36
..B1.6:
        cmpl      $16372, %ebx
        jge       ..B1.29
..B1.7:
        cmpl      $16365, %ebx
        jge       ..B1.22
..B1.8:
        cmpl      $16308, %ebx
        jge       ..B1.16
..B1.9:
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.69
..B1.10:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.11:
        fldcw     56(%esp)
..B1.12:
        movzbl    17(%ebp), %eax
        movl      $1, %ecx
        andl      $128, %eax
        shrl      $7, %eax
..B1.13:
        fldt      8(%ebp)
        testl     %ecx, %ecx
        fmull     ones@GOTOFF(%edx,%eax,8)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.15
..B1.14:
        fldcw     58(%esp)
..B1.15:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.17:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.18:
        fldcw     56(%esp)
..B1.19:
        fldt      8(%ebp)
        fldt      _Q3@GOTOFF(%edx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.20:
        fldcw     58(%esp)
..B1.21:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.71
..B1.23:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.24:
        fldcw     56(%esp)
..B1.25:
        movl      $1, %eax
..B1.26:
        fldt      8(%ebp)
        testl     %eax, %eax
        fmul      %st(0), %st
        fldt      12+_Q2@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.28
..B1.27:
        fldcw     58(%esp)
..B1.28:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.30:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.31:
        fldcw     56(%esp)
..B1.32:
        movl      $1, %eax
..B1.33:
        fldt      8(%ebp)
        testl     %eax, %eax
        fmul      %st(0), %st
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
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.35
..B1.34:
        fldcw     58(%esp)
..B1.35:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.73
..B1.37:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.38:
        fldcw     56(%esp)
..B1.39:
        movzbl    17(%ebp), %eax
        movl      $1, %ecx
        andl      $128, %eax
        shrl      $7, %eax
..B1.40:
        fldl      _TWO_48H@GOTOFF(%edx)
        testl     %ecx, %ecx
        fld       %st(0)
        fldt      8(%ebp)
        fmull     ones@GOTOFF(%edx,%eax,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fadd      %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      84+_Q@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      72+_Q@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      60+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(4)
        fmul      %st(4), %st
        fxch      %st(6)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st(0), %st
        fldt      12+_Q@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      108+_Q@GOTOFF(%edx)
        fld       %st(0)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fmul      %st, %st(1)
        fldt      96+_Q@GOTOFF(%edx)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmul      %st, %st(3)
        fld       %st(3)
        fxch      %st(3)
        fmulp     %st, %st(1)
        fadd      %st, %st(2)
        fldl      _TWO_48H@GOTOFF(%edx)
        fadd      %st, %st(3)
        fsubr     %st(3), %st
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B1.83
..B1.41:
        fstpt     44(%esp)
..B1.82:
        fldcw     58(%esp)
        jmp       ..B1.42
..B1.83:
        fstp      %st(0)
..B1.42:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        movl      %ecx, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.74
..B1.44:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.45:
        fldcw     56(%esp)
..B1.46:
        movzbl    17(%ebp), %eax
        movl      $1, %ecx
        andl      $128, %eax
        shrl      $7, %eax
..B1.47:
        fldt      8(%ebp)
        fmull     ones@GOTOFF(%edx,%eax,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(1), %st
        movl      44(%esp), %eax
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(2)
        movsbl    %al, %esi
        fsub      %st(1), %st
        fldl      _TWO_32P@GOTOFF(%edx)
        subl      %esi, %eax
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     _TWO_32@GOTOFF(%edx)
        shll      $4, %esi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        shrl      $8, %eax
        fxch      %st(2)
        fsubrp    %st, %st(3)
        movl      %eax, %ebx
        negl      %eax
        shll      $23, %ebx
        fadd      %st(2), %st
        fld       %st(0)
        addl      $1056964608, %ebx
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        shll      $23, %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        addl      $1056964608, %eax
        fldt      12+_P@GOTOFF(%edx)
        movl      %ebx, 12(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        movl      %eax, 28(%esp)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fadd      %st, %st(1)
        fsubp     %st, %st(3)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%esi)
        negl      %esi
        fstpt     16(%esp)
        fldt      16(%esp)
        testl     %ecx, %ecx
        fadd      %st(1), %st
        fmulp     %st, %st(2)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fld       %st(0)
        fmul      %st(4), %st
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fld       %st(0)
        fadd      %st(3), %st
        fmulp     %st, %st(7)
        fxch      %st(1)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        fstpt     32(%esp)
        fldt      16(%esp)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(4), %st
        fld       %st(4)
        fsub      %st(1), %st
        fsubr     %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      32(%esp)
        faddp     %st, %st(3)
        flds      12(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        flds      28(%esp)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.49
..B1.48:
        fldcw     58(%esp)
..B1.49:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        cmpl      $16396, %ebx
        jge       ..B1.58
..B1.51:
        movzwl    58(%esp), %ebx
        movl      %ebx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.75
..B1.52:
        orl       $-64768, %ebx
        movw      %bx, 56(%esp)
..B1.53:
        fldcw     56(%esp)
..B1.54:
        movzbl    17(%ebp), %eax
        movl      $1, %ecx
        andl      $128, %eax
        shrl      $7, %eax
..B1.55:
        fldt      8(%ebp)
        fmull     ones@GOTOFF(%edx,%eax,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fstpt     (%esp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(2), %st
        movzwl    8+.L_2il0floatpacket.3@GOTOFF(%edx), %ebx
        fadd      %st(1), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        andl      $-32768, %ebx
        movl      44(%esp), %eax
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(1), %st
        movsbl    %al, %esi
        fsubrp    %st, %st(2)
        fld       %st(1)
        subl      %esi, %eax
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(2)
        shll      $4, %esi
        fsub      %st(1), %st
        fldl      _TWO_32@GOTOFF(%edx)
        fldl      _TWO_32P@GOTOFF(%edx)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        shrl      $8, %eax
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(0)
        addl      $16350, %eax
        andl      $32767, %eax
        fxch      %st(1)
        fsubr     %st, %st(4)
        orl       %eax, %ebx
        fxch      %st(3)
        fsubrp    %st, %st(4)
        movw      %bx, 8(%esp)
        testl     %ecx, %ecx
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(5)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(4)
        fadd      %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.57
..B1.56:
        fldcw     58(%esp)
..B1.57:
        fldt      16(%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.58:
        je        ..B1.77
..B1.59:
        movzwl    58(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.76
..B1.60:
        orl       $-64768, %ecx
        movw      %cx, 56(%esp)
..B1.61:
        fldcw     56(%esp)
..B1.62:
        movzwl    16(%ebp), %ebx
        movl      $1, %ecx
        andl      $32767, %ebx
..B1.63:
        cmpl      $32767, %ebx
        jne       ..B1.65
..B1.64:
        fldt      8(%ebp)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmull     ones@GOTOFF(%edx,%eax,8)
        fstpt     (%esp)
        jmp       ..B1.66
..B1.65:
        fldt      _large_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.66:
        testl     %ecx, %ecx
        je        ..B1.68
..B1.67:
        fldcw     58(%esp)
..B1.68:
        fldt      (%esp)
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.69:
        xorl      %ecx, %ecx
        jmp       ..B1.13
..B1.70:
        fldt      8(%ebp)
        fldt      _Q3@GOTOFF(%edx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.21
..B1.71:
        xorl      %eax, %eax
        jmp       ..B1.26
..B1.72:
        xorl      %eax, %eax
        jmp       ..B1.33
..B1.73:
        xorl      %ecx, %ecx
        jmp       ..B1.40
..B1.74:
        xorl      %ecx, %ecx
        jmp       ..B1.47
..B1.75:
        xorl      %ecx, %ecx
        jmp       ..B1.55
..B1.76:
        xorl      %ecx, %ecx
        jmp       ..B1.63
..B1.77:
        cmpl      $-1317741120, %ecx
        jb        ..B1.51
..B1.78:
        jne       ..B1.59
..B1.79:
        cmpl      $833536233, 8(%ebp)
        jbe       ..B1.51
        jmp       ..B1.59
        .align    16,0x90
	.type	coshl,@function
	.size	coshl,.-coshl
	.data
# -- End  coshl
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
_TWO_32P:
	.long	1048576
	.long	1106247680
	.type	_TWO_32P,@object
	.size	_TWO_32P,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.align 2
_Q3:
	.word	21845
	.word	213
	.word	0
	.word	32768
	.word	16382
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	62430
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	22894
	.word	21845
	.word	43733
	.word	43690
	.word	16378
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	65531
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	7054
	.word	43693
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	25016
	.word	58228
	.word	24755
	.word	46603
	.word	16373
	.word	0
	.word	57397
	.word	35800
	.word	53031
	.word	53262
	.word	16367
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	18360
	.word	43637
	.word	28980
	.word	40373
	.word	49076
	.word	0
	.word	16302
	.word	43691
	.word	43690
	.word	43690
	.word	16362
	.word	0
	.word	46125
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	29965
	.word	3335
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	54950
	.word	46751
	.word	32187
	.word	37874
	.word	16361
	.word	0
	.word	30733
	.word	25775
	.word	51110
	.word	36726
	.word	16354
	.word	0
	.word	49850
	.word	450
	.word	9929
	.word	51659
	.word	16346
	.word	0
	.word	2776
	.word	45191
	.word	37201
	.word	55334
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16378
	.word	0
	.type	_Q,@object
	.size	_Q,120
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
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,24
	.data
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
