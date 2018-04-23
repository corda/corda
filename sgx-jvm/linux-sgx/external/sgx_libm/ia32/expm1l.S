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
	.file "expm1l.c"
	.text
..TXTST0:
# -- Begin  expm1l
	.text
       .align    16,0x90
	.globl expm1l
expm1l:
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
        fnstcw    58(%esp)
..B1.3:
        movzwl    16(%ebp), %ebx
        andl      $32767, %ebx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $16389, %ebx
        jge       ..B1.55
..B1.4:
        movzwl    58(%esp), %eax
        cmpl      $16381, %ebx
        jge       ..B1.45
..B1.5:
        cmpl      $16370, %ebx
        jge       ..B1.38
..B1.6:
        cmpl      $16359, %ebx
        jge       ..B1.31
..B1.7:
        cmpl      $16345, %ebx
        jge       ..B1.24
..B1.8:
        cmpl      $16308, %ebx
        jge       ..B1.18
..B1.9:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.88
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.11:
        fldcw     56(%esp)
..B1.12:
        movzwl    16(%ebp), %ebx
        movl      $1, %edx
        andl      $32767, %ebx
..B1.13:
        testl     %ebx, %ebx
        jle       ..B1.84
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ecx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     ones@GOTOFF(%ecx,%eax,8)
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     16(%esp)
..B1.15:
        testl     %edx, %edx
        je        ..B1.17
..B1.16:
        fldcw     58(%esp)
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
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.89
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.20:
        fldcw     56(%esp)
..B1.21:
        fldt      8(%ebp)
        fldt      _Q3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
..B1.22:
        fldcw     58(%esp)
..B1.23:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.90
..B1.25:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.26:
        fldcw     56(%esp)
..B1.27:
        movl      $1, %eax
..B1.28:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      12+_Q2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%ecx)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.30
..B1.29:
        fldcw     58(%esp)
..B1.30:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.91
..B1.32:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.33:
        fldcw     56(%esp)
..B1.34:
        movl      $1, %eax
..B1.35:
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
        je        ..B1.37
..B1.36:
        fldcw     58(%esp)
..B1.37:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.92
..B1.39:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.40:
        fldcw     56(%esp)
..B1.41:
        movl      $1, %eax
..B1.42:
        fldl      _TWO_54H@GOTOFF(%ecx)
        testl     %eax, %eax
        fld       %st(0)
        fldt      8(%ebp)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fldt      156+_Q@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      144+_Q@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      132+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      120+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      108+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      96+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      84+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      72+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      60+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_Q@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(4)
        fmul      %st(3), %st
        fld       %st(4)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(5)
        fldt      _Q@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fld       %st(5)
        fmul      %st(6), %st
        fmul      %st, %st(2)
        fld       %st(4)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(2)
        fldt      192+_Q@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(3)
        fldt      180+_Q@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(2)
        fldt      168+_Q@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fldt      8(%ebp)
        fld       %st(0)
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        je        ..B1.44
..B1.43:
        fldt      16(%esp)
        fstpt     44(%esp)
..B1.103:
        fldcw     58(%esp)
..B1.44:
        fldt      32(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.93
..B1.46:
        orl       $-64768, %eax
        movw      %ax, 56(%esp)
..B1.47:
        fldcw     56(%esp)
..B1.48:
        movl      $1, %eax
..B1.49:
        fldl      _TWO_63H@GOTOFF(%ecx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmul      %st(2), %st
        movl      44(%esp), %edx
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        fld       %st(0)
        movsbl    %dl, %ebx
        fsub      %st(2), %st
        fldl      _TWO_32H@GOTOFF(%ecx)
        subl      %ebx, %edx
        shll      $4, %ebx
        fadd      %st, %st(1)
        shrl      $8, %edx
        fsubr     %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        shll      $23, %edx
        fxch      %st(4)
        fsubrp    %st, %st(3)
        addl      $1065353216, %edx
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ecx)
        movl      %edx, 12(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%ecx)
        movzbl    17(%ebp), %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        andl      $128, %edx
        fmulp     %st, %st(3)
        shrl      $7, %edx
        faddp     %st, %st(2)
        fldt      _P@GOTOFF(%ecx)
        testl     %edx, %edx
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%ecx,%ebx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%ecx,%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(0)
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        flds      12(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        je        ..B1.51
..B1.50:
        fldt      .L_2il0floatpacket.4@GOTOFF(%ecx)
        fadd      %st(3), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        jmp       ..B1.52
..B1.51:
        movzwl    16(%ebp), %esi
        shll      $15, %edx
        andl      $32767, %esi
        orl       %esi, %edx
        shll      $16, %edx
        movzwl    14(%ebp), %edi
        orl       %edi, %edx
        cmpl      $1074049228, %edx
        movl      $0, %edx
        setb      %dl
        fldl      zero_one@GOTOFF(%ecx,%edx,8)
        xorl      $1, %edx
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     16(%esp)
        fsubl     zero_one@GOTOFF(%ecx,%edx,8)
..B1.52:
        fldt      16(%esp)
        testl     %eax, %eax
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.107
..B1.53:
        fstpt     44(%esp)
..B1.104:
        fldcw     58(%esp)
        jmp       ..B1.54
..B1.107:
        fstp      %st(0)
..B1.54:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.55:
        movzbl    17(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %eax
        movzwl    8+_ranges@GOTOFF(%eax,%ecx), %esi
        andl      $32767, %esi
        cmpl      %esi, %ebx
        jl        ..B1.60
..B1.56:
        jne       ..B1.74
..B1.57:
        movl      12(%ebp), %esi
        cmpl      4+_ranges@GOTOFF(%eax,%ecx), %esi
        jb        ..B1.60
..B1.58:
        jne       ..B1.74
..B1.59:
        movl      8(%ebp), %esi
        cmpl      _ranges@GOTOFF(%eax,%ecx), %esi
        jae       ..B1.74
..B1.60:
        movzwl    58(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.94
..B1.61:
        orl       $-64768, %esi
        movw      %si, 56(%esp)
..B1.62:
        fldcw     56(%esp)
..B1.63:
        movzwl    16(%ebp), %ebx
        movl      $1, %edi
        andl      $32767, %ebx
..B1.64:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        cmpl      $16396, %ebx
        movzwl    8+.L_2il0floatpacket.3@GOTOFF(%ecx), %esi
        movl      %esi, 40(%esp)
        fstpt     28(%esp)
        fldt      28(%esp)
        fldl      _TWO_63H@GOTOFF(%ecx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmul      %st(2), %st
        movl      44(%esp), %eax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmulp     %st, %st(3)
        fsub      %st(2), %st
        fldl      _TWO_32H@GOTOFF(%ecx)
        movsbl    %al, %esi
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        jge       ..B1.71
..B1.65:
        fldt      48+_P@GOTOFF(%ecx)
        fxch      %st(1)
        fstpt     16(%esp)
        fld       %st(2)
        fadd      %st(2), %st
        fld       %st(0)
        subl      %esi, %eax
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fldt      36+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ecx)
        shll      $4, %esi
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fmul      %st, %st(3)
        fldt      12+_P@GOTOFF(%ecx)
        shrl      $8, %eax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        addl      $16383, %eax
        fxch      %st(2)
        fmulp     %st, %st(1)
        andl      $32767, %eax
        movl      %edi, 12(%esp)
        faddp     %st, %st(2)
        fldt      _P@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        movl      40(%esp), %edi
        faddp     %st, %st(1)
        andl      $-32768, %edi
        faddp     %st, %st(1)
        fldl      2048+__libm_expl_table_256@GOTOFF(%ecx,%esi)
        orl       %eax, %edi
        fld       %st(0)
        testl     %edx, %edx
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fld       %st(0)
        movw      %di, 36(%esp)
        fadd      %st(4), %st
        movl      12(%esp), %edi
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      28(%esp)
        fmul      %st, %st(4)
        fxch      %st(2)
        fmull     2056+__libm_expl_table_256@GOTOFF(%ecx,%esi)
        faddp     %st, %st(3)
        faddl     2056+__libm_expl_table_256@GOTOFF(%ecx,%esi)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      16(%esp)
        je        ..B1.67
..B1.66:
        fldt      .L_2il0floatpacket.4@GOTOFF(%ecx)
        fadd      %st(3), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        faddp     %st, %st(4)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fsubp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.68
..B1.67:
        fstp      %st(3)
        movzbl    17(%ebp), %eax
        xorl      %edx, %edx
        shrl      $7, %eax
        shll      $15, %eax
        orl       %ebx, %eax
        shll      $16, %eax
        movzwl    14(%ebp), %esi
        orl       %esi, %eax
        cmpl      $1074049228, %eax
        setb      %dl
        fldl      zero_one@GOTOFF(%ecx,%edx,8)
        xorl      $1, %edx
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fsubl     zero_one@GOTOFF(%ecx,%edx,8)
..B1.68:
        fldt      16(%esp)
        testl     %edi, %edi
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.108
..B1.69:
        fstpt     44(%esp)
..B1.105:
        fldcw     58(%esp)
        jmp       ..B1.70
..B1.108:
        fstp      %st(0)
..B1.70:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.71:
        fstp      %st(3)
        fldt      48+_P@GOTOFF(%ecx)
        fld       %st(2)
        fadd      %st(2), %st
        fld       %st(0)
        subl      %esi, %eax
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fldt      36+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ecx)
        shll      $4, %esi
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fmul      %st, %st(3)
        fldt      12+_P@GOTOFF(%ecx)
        shrl      $8, %eax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        addl      $8191, %eax
        fxch      %st(2)
        fmulp     %st, %st(1)
        andl      $32767, %eax
        movzwl    36(%esp), %edx
        faddp     %st, %st(2)
        fldt      _P@GOTOFF(%ecx)
        andl      $-32768, %edx
        fmulp     %st, %st(1)
        orl       %eax, %edx
        movw      %dx, 36(%esp)
        testl     %edi, %edi
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      2056+__libm_expl_table_256@GOTOFF(%ecx,%esi)
        fldl      2048+__libm_expl_table_256@GOTOFF(%ecx,%esi)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      28(%esp)
        fmulp     %st, %st(1)
        fldt      _SC2@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.109
..B1.72:
        fstpt     44(%esp)
..B1.106:
        fldcw     58(%esp)
        jmp       ..B1.73
..B1.109:
        fstp      %st(0)
..B1.73:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.74:
        movzwl    58(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.100
..B1.75:
        orl       $-64768, %esi
        movw      %si, 56(%esp)
..B1.76:
        fldcw     56(%esp)
..B1.77:
        movzwl    16(%ebp), %ebx
        movl      $1, %eax
        andl      $32767, %ebx
..B1.78:
        cmpl      $32767, %ebx
        je        ..B1.96
..B1.79:
        testl     %edx, %edx
        je        ..B1.95
..B1.80:
        fldt      .L_2il0floatpacket.4@GOTOFF(%ecx)
        fldt      _small_value_80@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fstpt     16(%esp)
..B1.81:
        testl     %eax, %eax
        je        ..B1.83
..B1.82:
        fldcw     58(%esp)
..B1.83:
        fldt      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.84:
        cmpl      $0, 12(%ebp)
        jne       ..B1.86
..B1.85:
        cmpl      $0, 8(%ebp)
        je        ..B1.87
..B1.86:
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
        fmull     ones@GOTOFF(%ecx,%eax,8)
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     16(%esp)
        jmp       ..B1.15
..B1.87:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.15
..B1.88:
        xorl      %edx, %edx
        jmp       ..B1.13
..B1.89:
        fldt      8(%ebp)
        fldt      _Q3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.23
..B1.90:
        xorl      %eax, %eax
        jmp       ..B1.28
..B1.91:
        xorl      %eax, %eax
        jmp       ..B1.35
..B1.92:
        xorl      %eax, %eax
        jmp       ..B1.42
..B1.93:
        xorl      %eax, %eax
        jmp       ..B1.49
..B1.94:
        xorl      %edi, %edi
        jmp       ..B1.64
..B1.95:
        fldt      _large_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     16(%esp)
        jmp       ..B1.81
..B1.96:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.99
..B1.97:
        cmpl      $0, 8(%ebp)
        jne       ..B1.99
..B1.98:
        fldl      _inf_none@GOTOFF(%ecx,%edx,8)
        fstpt     16(%esp)
        jmp       ..B1.81
..B1.99:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.81
..B1.100:
        xorl      %eax, %eax
        jmp       ..B1.78
        .align    16,0x90
	.type	expm1l,@function
	.size	expm1l,.-expm1l
	.data
# -- End  expm1l
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
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
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
_TWO_54H:
	.long	0
	.long	1129840640
	.type	_TWO_54H,@object
	.size	_TWO_54H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_inf_none:
	.long	0
	.long	2146435072
	.long	0
	.long	3220176896
	.type	_inf_none,@object
	.size	_inf_none,16
	.align 2
_Q3:
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	2731
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	46967
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	65530
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	43687
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	52365
	.word	52428
	.word	43692
	.word	43690
	.word	16378
	.word	0
	.word	56491
	.word	10318
	.word	34954
	.word	34952
	.word	16376
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q:
	.word	39683
	.word	36691
	.word	42743
	.word	55145
	.word	16297
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16370
	.word	0
	.word	43680
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	46686
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	53372
	.word	3328
	.word	208
	.word	53261
	.word	16370
	.word	0
	.word	5395
	.word	3321
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	7401
	.word	46642
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	52768
	.word	7506
	.word	32188
	.word	37874
	.word	16361
	.word	0
	.word	23003
	.word	12485
	.word	11072
	.word	55090
	.word	16357
	.word	0
	.word	3702
	.word	18929
	.word	50035
	.word	36726
	.word	16354
	.word	0
	.word	2630
	.word	23096
	.word	11140
	.word	45202
	.word	16350
	.word	0
	.word	46660
	.word	62794
	.word	49270
	.word	51706
	.word	16346
	.word	0
	.word	61237
	.word	7998
	.word	19059
	.word	55154
	.word	16342
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
	.word	43648
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43648
	.word	16378
	.word	0
	.type	_Q,@object
	.size	_Q,204
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
_ranges:
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16389
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
	.align 2
_SC2:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24575
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.type	_SC2,@object
	.size	_SC2,24
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
