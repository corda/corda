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
	.file "sinhcoshl.c"
	.text
..TXTST0:
# -- Begin  sinhcoshl
	.text
       .align    16,0x90
	.globl sinhcoshl
sinhcoshl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
# parameter 3: 24 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $100, %esp
        movl      20(%ebp), %esi
        movl      24(%ebp), %ebx
..B1.2:
        fnstcw    62(%esp)
..B1.3:
        movzwl    16(%ebp), %edx
        andl      $32767, %edx
        movl      12(%ebp), %edi
        movl      %edx, %eax
        movl      %edi, (%esp)
        shll      $16, %eax
        shrl      $16, %edi
        movzbl    17(%ebp), %ecx
        orl       %edi, %eax
        andl      $128, %ecx
        shrl      $7, %ecx
        cmpl      $1073992020, %eax
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        jae       ..B1.53
..B1.4:
        movzwl    62(%esp), %edi
        cmpl      $16382, %edx
        movl      %edi, (%esp)
        jge       ..B1.46
..B1.5:
        cmpl      $16378, %edx
        jge       ..B1.39
..B1.6:
        cmpl      $16372, %edx
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %edx
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %edx
        jge       ..B1.18
..B1.9:
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.76
..B1.10:
        movl      (%esp), %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.11:
        fldcw     60(%esp)
..B1.12:
        movzbl    17(%ebp), %ecx
        movl      $1, %edi
        andl      $128, %ecx
        movzwl    16(%ebp), %edx
        shrl      $7, %ecx
        andl      $32767, %edx
..B1.13:
        testl     %edx, %edx
        jle       ..B1.72
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%eax)
        fmul      %st(1), %st
        fadd      %st(1), %st
        fmull     8+_TWO_75@GOTOFF(%eax)
        fstpt     (%esp)
..B1.15:
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        testl     %edi, %edi
        fxch      %st(1)
        fmull     ones@GOTOFF(%eax,%ecx,8)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.17
..B1.16:
        fldcw     62(%esp)
..B1.17:
        fldt      (%esp)
        fstpt     (%esi)
        fldt      16(%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.77
..B1.19:
        movl      %edi, %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.20:
        fldcw     60(%esp)
..B1.21:
        movl      $1, %edx
..B1.22:
        fldt      8(%ebp)
        testl     %edx, %edx
        fld       %st(0)
        fmul      %st(1), %st
        fldt      _PS3@GOTOFF(%eax)
        fmul      %st(1), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      _PC3@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.24
..B1.23:
        fldcw     62(%esp)
..B1.24:
        fldt      (%esp)
        fstpt     (%esi)
        fldt      16(%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.78
..B1.26:
        movl      %edi, %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.27:
        fldcw     60(%esp)
..B1.28:
        movl      $1, %edx
..B1.29:
        fldt      8(%ebp)
        testl     %edx, %edx
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_PS2@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      _PS2@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      12+_PC2@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      _PC2@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.31
..B1.30:
        fldcw     62(%esp)
..B1.31:
        fldt      (%esp)
        fstpt     (%esi)
        fldt      16(%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.79
..B1.33:
        movl      %edi, %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.34:
        fldcw     60(%esp)
..B1.35:
        movl      $1, %edx
..B1.36:
        fldt      8(%ebp)
        testl     %edx, %edx
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_PS1@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      24+_PS1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_PS1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _PS1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      36+_PC1@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      24+_PC1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_PC1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _PC1@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.38
..B1.37:
        fldcw     62(%esp)
..B1.38:
        fldt      16(%esp)
        fstpt     (%esi)
        fldt      (%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.80
..B1.40:
        movl      %edi, %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.41:
        fldcw     60(%esp)
..B1.42:
        movl      $1, %edx
..B1.43:
        fldl      _TWO_48H@GOTOFF(%eax)
        testl     %edx, %edx
        fld       %st(0)
        fldt      8(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        fld       %st(2)
        fsub      %st(1), %st
        fldt      84+_PS@GOTOFF(%eax)
        fmul      %st(3), %st
        fldt      72+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      60+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      36+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      12+_PS@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      84+_PC@GOTOFF(%eax)
        fmul      %st(4), %st
        fldt      72+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      60+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      48+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      36+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      24+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      12+_PC@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fstpt     64(%esp)
        fld       %st(4)
        fmul      %st(2), %st
        fldt      _PS@GOTOFF(%eax)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      96+_PS@GOTOFF(%eax)
        fld       %st(0)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(6)
        fld       %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%esp)
        fldt      108+_PC@GOTOFF(%eax)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      64(%esp)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      48(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fld       %st(0)
        fldt      96+_PC@GOTOFF(%eax)
        fmul      %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(6)
        fxch      %st(2)
        fmulp     %st, %st(6)
        fldt      80(%esp)
        fadd      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fld       %st(2)
        fadd      %st(1), %st
        fldl      _TWO_48H@GOTOFF(%eax)
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        je        ..B1.90
..B1.44:
        fstpt     80(%esp)
..B1.89:
        fldcw     62(%esp)
        jmp       ..B1.45
..B1.90:
        fstp      %st(0)
..B1.45:
        fldt      (%esp)
        fstpt     (%esi)
        fldt      16(%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.81
..B1.47:
        movl      %edi, %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.48:
        fldcw     60(%esp)
..B1.49:
        movzbl    17(%ebp), %ecx
        movl      $1, %edi
        andl      $128, %ecx
        shrl      $7, %ecx
..B1.50:
        fldl      ones@GOTOFF(%eax,%ecx,8)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_63H@GOTOFF(%eax)
        fldt      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st(2), %st
        movl      %ebx, 44(%esp)
        fadd      %st(1), %st
        fstpt     80(%esp)
        fldt      80(%esp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        fmul      %st(1), %st
        movl      80(%esp), %edx
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%eax)
        fmulp     %st, %st(2)
        movsbl    %dl, %ebx
        fsub      %st(1), %st
        fldl      _TWO_32P@GOTOFF(%eax)
        subl      %ebx, %edx
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     _TWO_32@GOTOFF(%eax)
        shll      $4, %ebx
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        shrl      $8, %edx
        fxch      %st(2)
        fsubrp    %st, %st(3)
        movl      %edx, %ecx
        negl      %edx
        shll      $23, %ecx
        fadd      %st(2), %st
        fld       %st(0)
        addl      $1056964608, %ecx
        fmul      %st(1), %st
        fldt      48+_PE@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      36+_PE@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      24+_PE@GOTOFF(%eax)
        shll      $23, %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        addl      $1056964608, %edx
        fldt      12+_PE@GOTOFF(%eax)
        movl      %ecx, 12(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        movl      %edx, 28(%esp)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fldt      _PE@GOTOFF(%eax)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fadd      %st, %st(1)
        fsubp     %st, %st(3)
        fldl      2056+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        negl      %ebx
        fstpt     48(%esp)
        fldt      48(%esp)
        testl     %edi, %edi
        fadd      %st(1), %st
        fmulp     %st, %st(2)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fldl      2048+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        fld       %st(0)
        movl      44(%esp), %ebx
        fadd      %st(3), %st
        fmulp     %st, %st(7)
        fxch      %st(1)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        fstpt     64(%esp)
        fldt      48(%esp)
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
        fldt      64(%esp)
        faddp     %st, %st(3)
        flds      12(%esp)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        flds      28(%esp)
        fmul      %st, %st(5)
        fld       %st(5)
        fadd      %st(4), %st
        fxch      %st(5)
        fmulp     %st, %st(1)
        fxch      %st(2)
        fsub      %st(5), %st
        fld       %st(0)
        fsubr     %st(4), %st
        fsub      %st(6), %st
        fld       %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(1)
        fxch      %st(4)
        fsub      %st(5), %st
        faddp     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.52
..B1.51:
        fldcw     62(%esp)
..B1.52:
        fldt      16(%esp)
        fstpt     (%esi)
        fldt      (%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.53:
        cmpl      $16396, %edx
        jge       ..B1.61
..B1.54:
        movzwl    62(%esp), %edi
        movl      %edi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.82
..B1.55:
        orl       $-64768, %edi
        movw      %di, 60(%esp)
..B1.56:
        fldcw     60(%esp)
..B1.57:
        movzbl    17(%ebp), %ecx
        movl      $1, %edi
        andl      $128, %ecx
        shrl      $7, %ecx
..B1.58:
        fldl      ones@GOTOFF(%eax,%ecx,8)
        fstpt     48(%esp)
        fldt      48(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        fstpt     (%esp)
        fldl      _TWO_63H@GOTOFF(%eax)
        fldt      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st(2), %st
        movl      %ebx, 44(%esp)
        fadd      %st(1), %st
        fstpt     80(%esp)
        fldt      80(%esp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        fmul      %st(1), %st
        movl      80(%esp), %edx
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%eax)
        fmulp     %st, %st(2)
        movsbl    %dl, %ebx
        fsub      %st(1), %st
        fldl      _TWO_32@GOTOFF(%eax)
        subl      %ebx, %edx
        fldl      _TWO_32P@GOTOFF(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        shll      $4, %ebx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        shrl      $8, %edx
        fsubr     %st, %st(3)
        addl      $16350, %edx
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fld       %st(1)
        andl      $32767, %edx
        movzwl    8+.L_2il0floatpacket.3@GOTOFF(%eax), %ecx
        fadd      %st(3), %st
        fld       %st(0)
        andl      $-32768, %ecx
        fmul      %st(1), %st
        orl       %edx, %ecx
        fldt      48+_PE@GOTOFF(%eax)
        testl     %edi, %edi
        fmul      %st(1), %st
        fldt      36+_PE@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      24+_PE@GOTOFF(%eax)
        movw      %cx, 8(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_PE@GOTOFF(%eax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(5)
        fldt      _PE@GOTOFF(%eax)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      2048+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmull     2056+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmul      %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddl     2056+__libm_expl_table_256@GOTOFF(%eax,%ebx)
        movl      44(%esp), %ebx
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     16(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        je        ..B1.60
..B1.59:
        fldcw     62(%esp)
..B1.60:
        fldt      32(%esp)
        fstpt     (%esi)
        fldt      16(%esp)
        fstpt     (%ebx)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        je        ..B1.84
..B1.62:
        movzwl    62(%esp), %edi
        movl      %edi, (%esp)
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.83
..B1.63:
        movl      (%esp), %edx
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.64:
        fldcw     60(%esp)
..B1.65:
        movzbl    17(%ebp), %ecx
        movl      $1, %edi
        andl      $128, %ecx
        movzwl    16(%ebp), %edx
        shrl      $7, %ecx
        andl      $32767, %edx
..B1.66:
        cmpl      $32767, %edx
        jne       ..B1.68
..B1.67:
        fldt      8(%ebp)
        fstpt     16(%esp)
        fldt      16(%esp)
        fldl      ones@GOTOFF(%eax,%ecx,8)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.69
..B1.68:
        fldt      _large_value_80@GOTOFF(%eax)
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %ecx
        fldt      _large_value_80@GOTOFF(%eax,%ecx)
        fmul      %st(1), %st
        fstpt     16(%esp)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.69:
        testl     %edi, %edi
        je        ..B1.71
..B1.70:
        fldcw     62(%esp)
..B1.71:
        fldt      16(%esp)
        fstpt     (%esi)
        fldt      (%esp)
        fstpt     (%ebx)
        addl      $100, %esp
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
        fldt      _small_value_80@GOTOFF(%eax)
        lea       (,%ecx,8), %edx
        fldt      8(%ebp)
        lea       (%edx,%ecx,4), %edx
        fldt      _small_value_80@GOTOFF(%eax,%edx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%esp)
        fldt      80(%esp)
        fadd      %st(1), %st
        fstpt     (%esp)
        jmp       ..B1.15
..B1.75:
        fldt      8(%ebp)
        fstpt     (%esp)
        fldt      (%esp)
        jmp       ..B1.15
..B1.76:
        xorl      %edi, %edi
        jmp       ..B1.13
..B1.77:
        xorl      %edx, %edx
        jmp       ..B1.22
..B1.78:
        xorl      %edx, %edx
        jmp       ..B1.29
..B1.79:
        xorl      %edx, %edx
        jmp       ..B1.36
..B1.80:
        xorl      %edx, %edx
        jmp       ..B1.43
..B1.81:
        xorl      %edi, %edi
        jmp       ..B1.50
..B1.82:
        xorl      %edi, %edi
        jmp       ..B1.58
..B1.83:
        xorl      %edi, %edi
        jmp       ..B1.66
..B1.84:
        cmpl      $-1317741120, (%esp)
        jb        ..B1.54
..B1.85:
        jne       ..B1.62
..B1.86:
        cmpl      $833536233, 8(%ebp)
        jbe       ..B1.54
        jmp       ..B1.62
        .align    16,0x90
	.type	sinhcoshl,@function
	.size	sinhcoshl,.-sinhcoshl
	.data
# -- End  sinhcoshl
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
_PS3:
	.word	21845
	.word	44373
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_PS3,@object
	.size	_PS3,12
	.align 2
_PC3:
	.word	21845
	.word	213
	.word	0
	.word	32768
	.word	16382
	.word	0
	.type	_PC3,@object
	.size	_PC3,12
	.align 2
_PS2:
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
	.type	_PS2,@object
	.size	_PS2,24
	.align 2
_PC2:
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
	.type	_PC2,@object
	.size	_PC2,24
	.align 2
_PS1:
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
	.type	_PS1,@object
	.size	_PS1,48
	.align 2
_PC1:
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
	.type	_PC1,@object
	.size	_PC1,48
	.align 2
_PS:
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
	.type	_PS,@object
	.size	_PS,108
	.align 2
_PC:
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
	.type	_PC,@object
	.size	_PC,120
	.align 2
_PE:
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
	.type	_PE,@object
	.size	_PE,60
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
