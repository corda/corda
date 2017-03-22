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
	.file "sinhl.c"
	.text
..TXTST0:
# -- Begin  sinhl
	.text
       .align    16,0x90
	.globl sinhl
sinhl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $84, %esp
..B1.2:
        fnstcw    46(%esp)
..B1.3:
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        movl      12(%ebp), %esi
        movl      %ecx, %edx
        movl      %esi, %eax
        shll      $16, %edx
        shrl      $16, %eax
        movzbl    17(%ebp), %ebx
        orl       %eax, %edx
        andl      $128, %ebx
        shrl      $7, %ebx
        cmpl      $1073992020, %edx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        jae       ..B1.53
..B1.4:
        movzwl    46(%esp), %eax
        cmpl      $16382, %ecx
        jge       ..B1.46
..B1.5:
        cmpl      $16378, %ecx
        jge       ..B1.39
..B1.6:
        cmpl      $16373, %ecx
        jge       ..B1.32
..B1.7:
        cmpl      $16366, %ecx
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %ecx
        jge       ..B1.18
..B1.9:
        movl      %eax, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.76
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.11:
        fldcw     44(%esp)
..B1.12:
        movzwl    16(%ebp), %ecx
        movl      $1, %esi
        andl      $32767, %ecx
..B1.13:
        testl     %ecx, %ecx
        jle       ..B1.72
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%edx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%edx)
        fstpt     16(%esp)
..B1.15:
        testl     %esi, %esi
        je        ..B1.17
..B1.16:
        fldcw     46(%esp)
..B1.17:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.77
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.20:
        fldcw     44(%esp)
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
        fldcw     46(%esp)
..B1.24:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.78
..B1.26:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.27:
        fldcw     44(%esp)
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
        fldcw     46(%esp)
..B1.31:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.79
..B1.33:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.34:
        fldcw     44(%esp)
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
        fldcw     46(%esp)
..B1.38:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.80
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.41:
        fldcw     44(%esp)
..B1.42:
        movzbl    17(%ebp), %ebx
        movl      $1, %eax
        andl      $128, %ebx
        shrl      $7, %ebx
..B1.43:
        fldl      ones@GOTOFF(%edx,%ebx,8)
        testl     %eax, %eax
        fstl      48(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_48H@GOTOFF(%edx)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fldt      84+_Q@GOTOFF(%edx)
        fmul      %st(5), %st
        fldt      72+_Q@GOTOFF(%edx)
        fmul      %st(6), %st
        fldt      60+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(6), %st
        fldt      48+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(6), %st
        fldt      36+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(6), %st
        fldt      24+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(6), %st
        fldt      12+_Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(6)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      _Q@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fld       %st(1)
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fldt      96+_Q@GOTOFF(%edx)
        fmul      %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(5)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      48(%esp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.45
..B1.44:
        fldt      32(%esp)
        fstpt     60(%esp)
..B1.89:
        fldcw     46(%esp)
..B1.45:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.81
..B1.47:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.48:
        fldcw     44(%esp)
..B1.49:
        movzbl    17(%ebp), %ebx
        movl      $1, %edi
        andl      $128, %ebx
        shrl      $7, %ebx
..B1.50:
        fldt      8(%ebp)
        fmull     ones@GOTOFF(%edx,%ebx,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fstpt     60(%esp)
        fldt      60(%esp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(1), %st
        movl      60(%esp), %eax
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
        movl      %eax, %ecx
        negl      %eax
        shll      $23, %ecx
        fadd      %st(2), %st
        fld       %st(0)
        addl      $1056964608, %ecx
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
        movl      %ecx, 12(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        movl      %eax, 28(%esp)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fld       %st(2)
        fadd      %st(1), %st
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%esi)
        negl      %esi
        fstpt     32(%esp)
        fldt      32(%esp)
        testl     %edi, %edi
        fadd      %st(1), %st
        fmulp     %st, %st(4)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fld       %st(0)
        fmul      %st(4), %st
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%esi)
        fld       %st(0)
        fadd      %st(3), %st
        fmulp     %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fstpt     48(%esp)
        fldt      32(%esp)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(0)
        fsub      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      48(%esp)
        faddp     %st, %st(2)
        flds      12(%esp)
        fmull     ones@GOTOFF(%edx,%ebx,8)
        fmul      %st, %st(4)
        fld       %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        flds      28(%esp)
        fmull     ones@GOTOFF(%edx,%ebx,8)
        fmul      %st, %st(5)
        fxch      %st(2)
        fsub      %st(5), %st
        fsubr     %st, %st(6)
        fxch      %st(5)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        je        ..B1.91
..B1.51:
        fstps     28(%esp)
        fstps     12(%esp)
..B1.90:
        fldcw     46(%esp)
        jmp       ..B1.52
..B1.91:
        fstp      %st(0)
        fstp      %st(0)
..B1.52:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.53:
        cmpl      $16396, %ecx
        jge       ..B1.61
..B1.54:
        movzwl    46(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.82
..B1.55:
        orl       $-64768, %ecx
        movw      %cx, 44(%esp)
..B1.56:
        fldcw     44(%esp)
..B1.57:
        movzbl    17(%ebp), %ebx
        movl      $1, %esi
        andl      $128, %ebx
        shrl      $7, %ebx
..B1.58:
        fldl      ones@GOTOFF(%edx,%ebx,8)
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(2), %st
        movzwl    24(%esp), %ecx
        fadd      %st(1), %st
        fstpt     60(%esp)
        fldt      60(%esp)
        andl      $-32768, %ecx
        movl      60(%esp), %eax
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(1), %st
        movsbl    %al, %ebx
        fsubrp    %st, %st(2)
        fld       %st(1)
        subl      %ebx, %eax
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(2)
        shll      $4, %ebx
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
        orl       %eax, %ecx
        fxch      %st(3)
        fsubrp    %st, %st(4)
        movw      %cx, 24(%esp)
        testl     %esi, %esi
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
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%ebx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%ebx)
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
        fldt      16(%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        je        ..B1.60
..B1.59:
        fldcw     46(%esp)
..B1.60:
        fldt      32(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        je        ..B1.84
..B1.62:
        movzwl    46(%esp), %ebx
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.83
..B1.63:
        orl       $-64768, %ebx
        movw      %bx, 44(%esp)
..B1.64:
        fldcw     44(%esp)
..B1.65:
        movzwl    16(%ebp), %ecx
        movl      $1, %esi
        andl      $32767, %ecx
..B1.66:
        cmpl      $32767, %ecx
        jne       ..B1.68
..B1.67:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.69
..B1.68:
        fldt      _large_value_80@GOTOFF(%edx)
        movzbl    17(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %ebx
        fldt      _large_value_80@GOTOFF(%edx,%ebx)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
..B1.69:
        testl     %esi, %esi
        je        ..B1.71
..B1.70:
        fldcw     46(%esp)
..B1.71:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.72:
        cmpl      $0, 12(%ebp)
        jne       ..B1.74
..B1.73:
        cmpl      $0, 8(%ebp)
        je        ..B1.75
..B1.74:
        fldt      _small_value_80@GOTOFF(%edx)
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%edx)
        fmul      %st(1), %st
        movzbl    17(%ebp), %ecx
        faddp     %st, %st(1)
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
..B1.75:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.15
..B1.76:
        xorl      %esi, %esi
        jmp       ..B1.13
..B1.77:
        xorl      %eax, %eax
        jmp       ..B1.22
..B1.78:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.79:
        xorl      %eax, %eax
        jmp       ..B1.36
..B1.80:
        xorl      %eax, %eax
        jmp       ..B1.43
..B1.81:
        xorl      %edi, %edi
        jmp       ..B1.50
..B1.82:
        xorl      %esi, %esi
        jmp       ..B1.58
..B1.83:
        xorl      %esi, %esi
        jmp       ..B1.66
..B1.84:
        cmpl      $-1317741120, %esi
        jb        ..B1.54
..B1.85:
        jne       ..B1.62
..B1.86:
        cmpl      $833536233, 8(%ebp)
        jbe       ..B1.54
        jmp       ..B1.62
        .align    16,0x90
	.type	sinhl,@function
	.size	sinhl,.-sinhl
	.data
# -- End  sinhl
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
	.word	44373
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	29491
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	1838
	.word	3745
	.word	35050
	.word	34952
	.word	16376
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	56472
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	6470
	.word	8609
	.word	207
	.word	53261
	.word	16370
	.word	0
	.word	51515
	.word	9159
	.word	10794
	.word	47344
	.word	16364
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	43620
	.word	43690
	.word	43690
	.word	43690
	.word	16364
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	52382
	.word	3328
	.word	208
	.word	53261
	.word	16370
	.word	0
	.word	44665
	.word	46658
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	15678
	.word	35942
	.word	11071
	.word	55090
	.word	16357
	.word	0
	.word	10412
	.word	23121
	.word	12507
	.word	45202
	.word	16350
	.word	0
	.word	37213
	.word	41879
	.word	65084
	.word	55102
	.word	16342
	.word	0
	.word	1721
	.word	42148
	.word	19309
	.word	52099
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16380
	.word	0
	.type	_Q,@object
	.size	_Q,108
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
