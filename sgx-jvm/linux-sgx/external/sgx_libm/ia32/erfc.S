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
	.file "erfc.c"
	.text
..TXTST0:
# -- Begin  erfc
	.text
       .align    16,0x90
	.globl erfc
erfc:
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
        movzwl    14(%ebp), %ebx
        andl      $32752, %ebx
        shrl      $4, %ebx
        movzbl    15(%ebp), %ecx
        movl      %ebx, %eax
        shrl      $7, %ecx
        shll      $31, %ecx
        shll      $20, %eax
        movl      12(%ebp), %edx
        orl       %eax, %ecx
        andl      $1048575, %edx
        orl       %edx, %ecx
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.41
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.5:
        fldcw     12(%esp)
..B1.6:
        movl      $1, %edx
..B1.7:
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $2047, %ebx
        jne       ..B1.16
..B1.8:
        testl     $1048575, 12(%ebp)
        jne       ..B1.13
..B1.9:
        cmpl      $0, 8(%ebp)
        jne       ..B1.13
..B1.10:
        movzbl    15(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        testl     %edx, %edx
        je        ..B1.12
..B1.11:
        fldcw     14(%esp)
..B1.12:
        flds      zero_two@GOTOFF(%eax,%ecx,4)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        testl     %edx, %edx
        je        ..B1.15
..B1.14:
        fldcw     14(%esp)
..B1.15:
        fldl      8(%ebp)
        fmul      %st(0), %st
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        testl     %ebx, %ebx
        jne       ..B1.22
..B1.17:
        testl     $1048575, 12(%ebp)
        jne       ..B1.22
..B1.18:
        cmpl      $0, 8(%ebp)
        jne       ..B1.22
..B1.19:
        testl     %edx, %edx
        je        ..B1.21
..B1.20:
        fldcw     14(%esp)
..B1.21:
        fld1      
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        cmpl      $1077578514, %ecx
        jae       ..B1.26
..B1.23:
        fldl      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fld1      
        movzwl    8+.L_2il0floatpacket.2@GOTOFF(%eax), %edi
        fadd      %st(3), %st
        fmul      %st(0), %st
        andl      $-32768, %edi
        fmul      %st(0), %st
        fstpt     24(%esp)
        fldl      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st(3), %st
        movzwl    32(%esp), %ebx
        fsub      %st, %st(2)
        andl      $32767, %ebx
        fsubp     %st, %st(2)
        fld       %st(1)
        lea       (%ebx,%ebx,2), %ebx
        fmul      %st(2), %st
        fchs      
        fxch      %st(1)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        shll      $6, %ebx
        fmulp     %st, %st(3)
        fmul      %st, %st(2)
        fmul      %st(0), %st
        fsubrp    %st, %st(2)
        fldt      _EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      36+_EXPL_TABLE@GOTOFF(%eax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fsubp     %st, %st(1)
        fldt      -3145536+_ERFC_TABLE@GOTOFF(%ebx,%eax)
        movl      36(%esp), %ecx
        movl      %ecx, %esi
        andl      $-128, %esi
        andl      $127, %ecx
        addl      $2097024, %esi
        fsubrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fldt      .L_2il0floatpacket.2@GOTOFF(%eax)
        fstpt     48(%esp)
        fldt      12+_EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(2), %st
        shrl      $7, %esi
        fsubrp    %st, %st(3)
        fldt      24+_EXPL_TABLE@GOTOFF(%eax)
        andl      $32767, %esi
        fmul      %st(2), %st
        orl       %esi, %edi
        movw      %di, 56(%esp)
        lea       (,%ecx,8), %esi
        testl     %edx, %edx
        lea       (%esi,%ecx,4), %ecx
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      -3145356+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(3), %st
        fldt      -3145380+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145404+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145428+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145452+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145476+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145500+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145524+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fldt      -3145368+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fldt      -3145392+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145416+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145440+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145464+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145488+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      -3145512+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      72+_EXP_POLY@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      60+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      _EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fldt      48(%esp)
        fldt      _T_TABLE@GOTOFF(%eax,%ecx)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.46
..B1.24:
        fstpt     36(%esp)
..B1.44:
        fldcw     14(%esp)
        jmp       ..B1.25
..B1.46:
        fstp      %st(0)
..B1.25:
        fldt      (%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        cmpl      $1077673984, %ecx
        jae       ..B1.30
..B1.27:
        fldl      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fld1      
        movzwl    8+.L_2il0floatpacket.2@GOTOFF(%eax), %edi
        fadd      %st(3), %st
        fmul      %st(0), %st
        andl      $-32768, %edi
        fmul      %st(0), %st
        fstpt     24(%esp)
        fldl      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st(3), %st
        movzwl    32(%esp), %ebx
        fsub      %st, %st(2)
        andl      $32767, %ebx
        fsubp     %st, %st(2)
        fld       %st(1)
        lea       (%ebx,%ebx,2), %ebx
        fmul      %st(2), %st
        fchs      
        fxch      %st(1)
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        shll      $6, %ebx
        fmulp     %st, %st(3)
        fmul      %st, %st(2)
        fmul      %st(0), %st
        fsubrp    %st, %st(2)
        fldt      _EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      36+_EXPL_TABLE@GOTOFF(%eax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fsubp     %st, %st(1)
        fldt      -3145536+_ERFC_TABLE@GOTOFF(%ebx,%eax)
        movl      36(%esp), %ecx
        movl      %ecx, %esi
        andl      $-128, %esi
        andl      $127, %ecx
        addl      $2097024, %esi
        fsubrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldt      .L_2il0floatpacket.2@GOTOFF(%eax)
        fstpt     48(%esp)
        fldt      12+_EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(1), %st
        shrl      $7, %esi
        fsubrp    %st, %st(3)
        fldt      24+_EXPL_TABLE@GOTOFF(%eax)
        andl      $32767, %esi
        fmulp     %st, %st(1)
        orl       %esi, %edi
        movw      %di, 56(%esp)
        lea       (,%ecx,8), %esi
        testl     %edx, %edx
        lea       (%esi,%ecx,4), %ecx
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      -3145356+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(2), %st
        fldt      -3145380+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145404+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145428+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145452+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145476+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145500+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      -3145524+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fldt      -3145368+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(3), %st
        fldt      -3145392+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145416+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145440+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145464+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      -3145488+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      -3145512+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      72+_EXP_POLY@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      60+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fldt      _T_TABLE@GOTOFF(%eax,%ecx)
        fmulp     %st, %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpl     16(%esp)
        je        ..B1.29
..B1.28:
        fldcw     14(%esp)
..B1.29:
        fldl      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.30:
        cmpl      $2146435072, %ecx
        jb        ..B1.38
..B1.31:
        cmpl      $-1072168960, %ecx
        jae       ..B1.35
..B1.32:
        fldl      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fchs      
        fld1      
        movzwl    8+.L_2il0floatpacket.2@GOTOFF(%eax), %edi
        fadd      %st(1), %st
        fmul      %st(0), %st
        andl      $-32768, %edi
        fmul      %st(0), %st
        fstpt     24(%esp)
        fldl      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st(3), %st
        movzwl    32(%esp), %ebx
        fsub      %st, %st(2)
        andl      $32767, %ebx
        fsubp     %st, %st(2)
        fld       %st(1)
        lea       (%ebx,%ebx,2), %ebx
        fmul      %st(2), %st
        fchs      
        fxch      %st(2)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        shll      $6, %ebx
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fmul      %st(0), %st
        fsubrp    %st, %st(3)
        fldt      _EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      36+_EXPL_TABLE@GOTOFF(%eax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fsubp     %st, %st(1)
        fldt      -3145536+_ERFC_TABLE@GOTOFF(%ebx,%eax)
        movl      36(%esp), %ecx
        movl      %ecx, %esi
        andl      $-128, %esi
        andl      $127, %ecx
        addl      $2097024, %esi
        fsubrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.2@GOTOFF(%eax)
        fstpt     48(%esp)
        fldt      12+_EXPL_TABLE@GOTOFF(%eax)
        fmul      %st(2), %st
        shrl      $7, %esi
        fsubrp    %st, %st(4)
        fldt      24+_EXPL_TABLE@GOTOFF(%eax)
        andl      $32767, %esi
        fmul      %st(2), %st
        orl       %esi, %edi
        movw      %di, 56(%esp)
        lea       (,%ecx,8), %esi
        testl     %edx, %edx
        lea       (%esi,%ecx,4), %ecx
        fsubrp    %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      -3145356+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fldt      -3145380+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145404+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145428+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145452+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145476+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145500+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      -3145524+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fldt      -3145368+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        fmul      %st(5), %st
        fldt      -3145392+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      -3145416+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      -3145440+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      -3145464+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      -3145488+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      -3145512+_ERFC_TABLE@GOTOFF(%eax,%ebx)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      72+_EXP_POLY@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      60+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _EXP_POLY@GOTOFF(%eax)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        fldt      48(%esp)
        fldt      _T_TABLE@GOTOFF(%eax,%ecx)
        fmulp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fldt      60+_EXPL_TABLE@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.47
..B1.33:
        fstpt     36(%esp)
..B1.45:
        fldcw     14(%esp)
        jmp       ..B1.34
..B1.47:
        fstp      %st(0)
..B1.34:
        fldt      (%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.35:
        fldt      48+_EXPL_TABLE@GOTOFF(%eax)
        testl     %edx, %edx
        fldt      72+_EXPL_TABLE@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpl     16(%esp)
        je        ..B1.37
..B1.36:
        fldcw     14(%esp)
..B1.37:
        fldl      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        fldt      84+_EXPL_TABLE@GOTOFF(%eax)
        testl     %edx, %edx
        fmul      %st(0), %st
        fstpl     16(%esp)
        je        ..B1.40
..B1.39:
        fldcw     14(%esp)
..B1.40:
        fldl      16(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        xorl      %edx, %edx
        jmp       ..B1.7
        .align    16,0x90
	.type	erfc,@function
	.size	erfc,.-erfc
	.data
# -- End  erfc
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0xc0,0x00,0x00
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
zero_two:
	.long	0x00000000
	.long	0x40000000
	.type	zero_two,@object
	.size	zero_two,8
	.space 8, 0x00 	# pad
	.align 16
_ERFC_TABLE:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	26765
	.word	5339
	.word	47746
	.word	36974
	.word	49151
	.word	0
	.word	65515
	.word	65535
	.word	65535
	.word	65535
	.word	16382
	.word	0
	.word	10533
	.word	7119
	.word	41816
	.word	49299
	.word	49150
	.word	0
	.word	52669
	.word	65527
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	16123
	.word	5294
	.word	46560
	.word	39439
	.word	49149
	.word	0
	.word	45245
	.word	18034
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	4032
	.word	40793
	.word	62556
	.word	45073
	.word	49147
	.word	0
	.word	4147
	.word	29718
	.word	43156
	.word	43690
	.word	16378
	.word	0
	.word	16916
	.word	58237
	.word	36396
	.word	40065
	.word	49145
	.word	0
	.word	30369
	.word	5547
	.word	38547
	.word	34949
	.word	16376
	.word	0
	.word	5723
	.word	34549
	.word	4774
	.word	58209
	.word	49142
	.word	0
	.word	32046
	.word	45872
	.word	27437
	.word	46045
	.word	16373
	.word	0
	.word	53798
	.word	26622
	.word	41794
	.word	32792
	.word	49140
	.word	0
	.word	43993
	.word	60371
	.word	27258
	.word	64442
	.word	16369
	.word	0
	.word	19322
	.word	28101
	.word	33420
	.word	49599
	.word	16380
	.word	0
	.word	7793
	.word	15289
	.word	7920
	.word	53594
	.word	16382
	.word	0
	.word	28952
	.word	49971
	.word	44506
	.word	53668
	.word	49150
	.word	0
	.word	36016
	.word	10961
	.word	40954
	.word	43439
	.word	16382
	.word	0
	.word	20915
	.word	61681
	.word	29972
	.word	60599
	.word	49149
	.word	0
	.word	24299
	.word	42445
	.word	45918
	.word	37706
	.word	16381
	.word	0
	.word	61980
	.word	35870
	.word	4262
	.word	42772
	.word	49148
	.word	0
	.word	2378
	.word	21048
	.word	27187
	.word	44880
	.word	16379
	.word	0
	.word	26023
	.word	38535
	.word	63103
	.word	44029
	.word	49146
	.word	0
	.word	51865
	.word	42701
	.word	357
	.word	40715
	.word	16377
	.word	0
	.word	52714
	.word	49990
	.word	38847
	.word	35713
	.word	49144
	.word	0
	.word	43806
	.word	65105
	.word	55139
	.word	59728
	.word	16374
	.word	0
	.word	64429
	.word	64517
	.word	6745
	.word	47746
	.word	49141
	.word	0
	.word	3465
	.word	57626
	.word	20546
	.word	36179
	.word	16372
	.word	0
	.word	22094
	.word	56102
	.word	37959
	.word	48728
	.word	49138
	.word	0
	.word	23306
	.word	25455
	.word	51183
	.word	43976
	.word	16368
	.word	0
	.word	37393
	.word	59257
	.word	52431
	.word	54291
	.word	16381
	.word	0
	.word	39090
	.word	10024
	.word	15035
	.word	43415
	.word	16382
	.word	0
	.word	43741
	.word	37879
	.word	6777
	.word	37983
	.word	49150
	.word	0
	.word	35426
	.word	24730
	.word	14790
	.word	55364
	.word	16381
	.word	0
	.word	35922
	.word	59851
	.word	47783
	.word	35355
	.word	49149
	.word	0
	.word	26997
	.word	19270
	.word	26422
	.word	40719
	.word	16380
	.word	0
	.word	44927
	.word	10365
	.word	61824
	.word	43075
	.word	49147
	.word	0
	.word	58693
	.word	50832
	.word	29111
	.word	42397
	.word	16378
	.word	0
	.word	18548
	.word	6343
	.word	29493
	.word	39194
	.word	49145
	.word	0
	.word	63186
	.word	18681
	.word	63761
	.word	34279
	.word	16376
	.word	0
	.word	30590
	.word	51670
	.word	24700
	.word	57056
	.word	49142
	.word	0
	.word	12395
	.word	18187
	.word	53964
	.word	45381
	.word	16373
	.word	0
	.word	8442
	.word	17290
	.word	60340
	.word	34554
	.word	49140
	.word	0
	.word	42583
	.word	52808
	.word	14422
	.word	49744
	.word	16370
	.word	0
	.word	38491
	.word	10790
	.word	17138
	.word	62758
	.word	49136
	.word	0
	.word	64957
	.word	43359
	.word	35041
	.word	51589
	.word	16366
	.word	0
	.word	54760
	.word	44346
	.word	63893
	.word	44681
	.word	16382
	.word	0
	.word	58037
	.word	18353
	.word	47660
	.word	34940
	.word	16382
	.word	0
	.word	12942
	.word	61844
	.word	37020
	.word	52609
	.word	49149
	.word	0
	.word	9074
	.word	21010
	.word	41308
	.word	34012
	.word	16381
	.word	0
	.word	33111
	.word	27346
	.word	43463
	.word	39226
	.word	49148
	.word	0
	.word	56229
	.word	38650
	.word	52618
	.word	41280
	.word	16379
	.word	0
	.word	28394
	.word	30437
	.word	45662
	.word	40246
	.word	49146
	.word	0
	.word	60627
	.word	1334
	.word	52268
	.word	36747
	.word	16377
	.word	0
	.word	31481
	.word	19361
	.word	56420
	.word	63358
	.word	49143
	.word	0
	.word	4433
	.word	31036
	.word	44150
	.word	51896
	.word	16374
	.word	0
	.word	21935
	.word	37845
	.word	52167
	.word	40591
	.word	49141
	.word	0
	.word	52857
	.word	25760
	.word	12934
	.word	60865
	.word	16371
	.word	0
	.word	30097
	.word	40209
	.word	51883
	.word	43747
	.word	49138
	.word	0
	.word	3097
	.word	41736
	.word	19718
	.word	59248
	.word	16368
	.word	0
	.word	62213
	.word	58640
	.word	23379
	.word	34650
	.word	49135
	.word	0
	.word	39490
	.word	47564
	.word	3293
	.word	51441
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	37057
	.word	51893
	.word	15367
	.word	56044
	.word	16381
	.word	0
	.word	32940
	.word	48642
	.word	29176
	.word	35810
	.word	49149
	.word	0
	.word	5161
	.word	6502
	.word	37918
	.word	40467
	.word	16380
	.word	0
	.word	17565
	.word	33655
	.word	49092
	.word	41537
	.word	49147
	.word	0
	.word	53678
	.word	44288
	.word	26743
	.word	39397
	.word	16378
	.word	0
	.word	31872
	.word	15269
	.word	30938
	.word	34942
	.word	49145
	.word	0
	.word	20440
	.word	62677
	.word	51881
	.word	58469
	.word	16375
	.word	0
	.word	54439
	.word	520
	.word	12343
	.word	46457
	.word	49142
	.word	0
	.word	50526
	.word	61632
	.word	2909
	.word	35241
	.word	16373
	.word	0
	.word	37830
	.word	61581
	.word	47207
	.word	51261
	.word	49139
	.word	0
	.word	25082
	.word	64640
	.word	37139
	.word	35852
	.word	16370
	.word	0
	.word	4883
	.word	40101
	.word	42814
	.word	48151
	.word	49136
	.word	0
	.word	19629
	.word	49380
	.word	65167
	.word	60688
	.word	16366
	.word	0
	.word	28545
	.word	10321
	.word	37155
	.word	65097
	.word	49132
	.word	0
	.word	64819
	.word	9690
	.word	1918
	.word	43254
	.word	16362
	.word	0
	.word	21215
	.word	7025
	.word	57507
	.word	45167
	.word	16383
	.word	0
	.word	55056
	.word	34238
	.word	8713
	.word	44914
	.word	16381
	.word	0
	.word	4267
	.word	1060
	.word	46425
	.word	48156
	.word	49148
	.word	0
	.word	14908
	.word	57526
	.word	48994
	.word	46896
	.word	16379
	.word	0
	.word	21326
	.word	53665
	.word	1969
	.word	42227
	.word	49146
	.word	0
	.word	25051
	.word	4472
	.word	10229
	.word	35587
	.word	16377
	.word	0
	.word	2076
	.word	31531
	.word	22832
	.word	56640
	.word	49143
	.word	0
	.word	60662
	.word	38735
	.word	55096
	.word	42849
	.word	16374
	.word	0
	.word	46215
	.word	1268
	.word	63191
	.word	61960
	.word	49140
	.word	0
	.word	4281
	.word	13548
	.word	25997
	.word	42995
	.word	16371
	.word	0
	.word	59094
	.word	61396
	.word	50012
	.word	57465
	.word	49137
	.word	0
	.word	56623
	.word	39859
	.word	52689
	.word	37061
	.word	16368
	.word	0
	.word	9718
	.word	28760
	.word	6243
	.word	45969
	.word	49134
	.word	0
	.word	22149
	.word	16273
	.word	53084
	.word	53283
	.word	16364
	.word	0
	.word	34037
	.word	57333
	.word	5882
	.word	51824
	.word	49130
	.word	0
	.word	11184
	.word	36893
	.word	23744
	.word	61115
	.word	16359
	.word	0
	.word	51465
	.word	62396
	.word	58983
	.word	59913
	.word	16383
	.word	0
	.word	21679
	.word	9895
	.word	5635
	.word	36049
	.word	16381
	.word	0
	.word	37768
	.word	22289
	.word	42182
	.word	64290
	.word	49147
	.word	0
	.word	29929
	.word	29351
	.word	11432
	.word	53291
	.word	16378
	.word	0
	.word	58374
	.word	18153
	.word	737
	.word	41523
	.word	49145
	.word	0
	.word	31896
	.word	56867
	.word	6398
	.word	61321
	.word	16375
	.word	0
	.word	55425
	.word	64032
	.word	46488
	.word	43176
	.word	49142
	.word	0
	.word	62735
	.word	35007
	.word	19863
	.word	58262
	.word	16372
	.word	0
	.word	2759
	.word	46881
	.word	15376
	.word	37816
	.word	49139
	.word	0
	.word	27202
	.word	45269
	.word	46930
	.word	47379
	.word	16369
	.word	0
	.word	36329
	.word	2846
	.word	16122
	.word	57442
	.word	49135
	.word	0
	.word	7470
	.word	414
	.word	53668
	.word	33732
	.word	16366
	.word	0
	.word	4702
	.word	32984
	.word	47709
	.word	38156
	.word	49132
	.word	0
	.word	59392
	.word	2467
	.word	11268
	.word	40167
	.word	16362
	.word	0
	.word	49683
	.word	209
	.word	55200
	.word	35020
	.word	49128
	.word	0
	.word	45504
	.word	61587
	.word	60548
	.word	36340
	.word	16357
	.word	0
	.word	27380
	.word	54941
	.word	64714
	.word	38724
	.word	16384
	.word	0
	.word	47150
	.word	21425
	.word	33441
	.word	58047
	.word	16380
	.word	0
	.word	10709
	.word	48511
	.word	39648
	.word	42794
	.word	49147
	.word	0
	.word	60862
	.word	8841
	.word	14633
	.word	59785
	.word	16377
	.word	0
	.word	38166
	.word	61183
	.word	58848
	.word	39827
	.word	49144
	.word	0
	.word	18364
	.word	43708
	.word	39063
	.word	50867
	.word	16374
	.word	0
	.word	61920
	.word	34013
	.word	44157
	.word	62530
	.word	49140
	.word	0
	.word	62014
	.word	46109
	.word	55800
	.word	37115
	.word	16371
	.word	0
	.word	46135
	.word	31864
	.word	22791
	.word	42668
	.word	49137
	.word	0
	.word	58857
	.word	54577
	.word	2989
	.word	47612
	.word	16367
	.word	0
	.word	34902
	.word	37732
	.word	51405
	.word	51663
	.word	49133
	.word	0
	.word	9364
	.word	27291
	.word	20920
	.word	54522
	.word	16363
	.word	0
	.word	37485
	.word	50
	.word	60675
	.word	55504
	.word	49129
	.word	0
	.word	35187
	.word	50155
	.word	51754
	.word	52385
	.word	16359
	.word	0
	.word	14814
	.word	63962
	.word	41088
	.word	40486
	.word	49125
	.word	0
	.word	11362
	.word	25769
	.word	50575
	.word	36669
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16384
	.word	0
	.word	26223
	.word	5353
	.word	5097
	.word	46924
	.word	16380
	.word	0
	.word	30267
	.word	42379
	.word	29288
	.word	57013
	.word	49146
	.word	0
	.word	42189
	.word	50693
	.word	61652
	.word	33311
	.word	16377
	.word	0
	.word	47396
	.word	16957
	.word	12509
	.word	37576
	.word	49143
	.word	0
	.word	23478
	.word	31241
	.word	24950
	.word	41038
	.word	16373
	.word	0
	.word	17240
	.word	63077
	.word	25824
	.word	43503
	.word	49139
	.word	0
	.word	25576
	.word	39889
	.word	51350
	.word	44857
	.word	16369
	.word	0
	.word	13068
	.word	16791
	.word	29619
	.word	45074
	.word	49135
	.word	0
	.word	60332
	.word	2969
	.word	19586
	.word	44206
	.word	16365
	.word	0
	.word	37599
	.word	42283
	.word	62126
	.word	42363
	.word	49131
	.word	0
	.word	41529
	.word	34093
	.word	65146
	.word	39638
	.word	16361
	.word	0
	.word	10083
	.word	55225
	.word	38396
	.word	35838
	.word	49127
	.word	0
	.word	53136
	.word	32600
	.word	23694
	.word	59889
	.word	16356
	.word	0
	.word	63669
	.word	4602
	.word	61995
	.word	40592
	.word	49122
	.word	0
	.word	64442
	.word	41272
	.word	29937
	.word	63695
	.word	16350
	.word	0
	.word	21215
	.word	7025
	.word	57507
	.word	61551
	.word	16384
	.word	0
	.word	60759
	.word	43057
	.word	21108
	.word	38100
	.word	16380
	.word	0
	.word	19048
	.word	27034
	.word	20875
	.word	38100
	.word	49146
	.word	0
	.word	43286
	.word	62467
	.word	46495
	.word	37059
	.word	16376
	.word	0
	.word	18535
	.word	3739
	.word	31085
	.word	35131
	.word	49142
	.word	0
	.word	47338
	.word	988
	.word	43337
	.word	65023
	.word	16371
	.word	0
	.word	53014
	.word	19205
	.word	31790
	.word	58830
	.word	49137
	.word	0
	.word	64942
	.word	4422
	.word	52367
	.word	52104
	.word	16367
	.word	0
	.word	18376
	.word	62542
	.word	21190
	.word	45226
	.word	49133
	.word	0
	.word	36541
	.word	5903
	.word	55315
	.word	38509
	.word	16363
	.word	0
	.word	58232
	.word	34982
	.word	30759
	.word	64373
	.word	49128
	.word	0
	.word	53034
	.word	58877
	.word	34756
	.word	52727
	.word	16358
	.word	0
	.word	43589
	.word	44050
	.word	54239
	.word	41804
	.word	49124
	.word	0
	.word	58188
	.word	24032
	.word	18788
	.word	61115
	.word	16353
	.word	0
	.word	53178
	.word	32234
	.word	55399
	.word	35974
	.word	49119
	.word	0
	.word	32107
	.word	65066
	.word	9168
	.word	48562
	.word	16347
	.word	0
	.word	25732
	.word	63966
	.word	62259
	.word	38148
	.word	16385
	.word	0
	.word	1290
	.word	5604
	.word	33063
	.word	62145
	.word	16379
	.word	0
	.word	61015
	.word	23258
	.word	11350
	.word	51162
	.word	49145
	.word	0
	.word	27005
	.word	25391
	.word	61437
	.word	41308
	.word	16375
	.word	0
	.word	36713
	.word	64273
	.word	62675
	.word	65487
	.word	49140
	.word	0
	.word	27734
	.word	12066
	.word	17277
	.word	51007
	.word	16370
	.word	0
	.word	22545
	.word	24708
	.word	32287
	.word	39069
	.word	49136
	.word	0
	.word	7137
	.word	26934
	.word	42406
	.word	58901
	.word	16365
	.word	0
	.word	28920
	.word	48738
	.word	2272
	.word	43725
	.word	49131
	.word	0
	.word	12386
	.word	49194
	.word	59841
	.word	63966
	.word	16360
	.word	0
	.word	12291
	.word	20618
	.word	59744
	.word	46112
	.word	49126
	.word	0
	.word	45255
	.word	8860
	.word	16074
	.word	65374
	.word	16355
	.word	0
	.word	24201
	.word	30931
	.word	20557
	.word	44934
	.word	49121
	.word	0
	.word	57092
	.word	56173
	.word	59274
	.word	56860
	.word	16350
	.word	0
	.word	421
	.word	9843
	.word	65450
	.word	57636
	.word	49115
	.word	0
	.word	26834
	.word	64481
	.word	48830
	.word	33265
	.word	16344
	.word	0
	.word	27380
	.word	54941
	.word	64714
	.word	46916
	.word	16385
	.word	0
	.word	16042
	.word	6176
	.word	23933
	.word	50894
	.word	16379
	.word	0
	.word	15496
	.word	30354
	.word	41738
	.word	34536
	.word	49145
	.word	0
	.word	16771
	.word	22379
	.word	52151
	.word	46241
	.word	16374
	.word	0
	.word	13611
	.word	51387
	.word	33531
	.word	61111
	.word	49139
	.word	0
	.word	58938
	.word	199
	.word	35276
	.word	39876
	.word	16369
	.word	0
	.word	34069
	.word	62119
	.word	6313
	.word	51412
	.word	49134
	.word	0
	.word	26024
	.word	61062
	.word	29148
	.word	65510
	.word	16363
	.word	0
	.word	21349
	.word	54243
	.word	12871
	.word	41265
	.word	49129
	.word	0
	.word	4785
	.word	38663
	.word	43132
	.word	51413
	.word	16358
	.word	0
	.word	24101
	.word	62567
	.word	22734
	.word	63346
	.word	49123
	.word	0
	.word	15399
	.word	49395
	.word	40313
	.word	38484
	.word	16353
	.word	0
	.word	28161
	.word	41902
	.word	27845
	.word	45418
	.word	49118
	.word	0
	.word	12182
	.word	20933
	.word	3877
	.word	49303
	.word	16347
	.word	0
	.word	20492
	.word	46011
	.word	10667
	.word	42720
	.word	49112
	.word	0
	.word	46757
	.word	22481
	.word	16184
	.word	41956
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	0
	.word	57344
	.word	16385
	.word	0
	.word	15972
	.word	32891
	.word	13820
	.word	41838
	.word	16379
	.word	0
	.word	63823
	.word	14280
	.word	41618
	.word	46885
	.word	49144
	.word	0
	.word	62247
	.word	60117
	.word	61219
	.word	52049
	.word	16373
	.word	0
	.word	4514
	.word	2791
	.word	14455
	.word	57256
	.word	49138
	.word	0
	.word	8237
	.word	29451
	.word	46967
	.word	62423
	.word	16367
	.word	0
	.word	38621
	.word	18263
	.word	65293
	.word	33733
	.word	49133
	.word	0
	.word	20437
	.word	17700
	.word	46099
	.word	36151
	.word	16362
	.word	0
	.word	7628
	.word	50188
	.word	47081
	.word	38422
	.word	49127
	.word	0
	.word	27034
	.word	6950
	.word	65014
	.word	40504
	.word	16356
	.word	0
	.word	929
	.word	31466
	.word	57314
	.word	42341
	.word	49121
	.word	0
	.word	51218
	.word	18814
	.word	40699
	.word	43755
	.word	16350
	.word	0
	.word	60139
	.word	33609
	.word	1443
	.word	43987
	.word	49115
	.word	0
	.word	28121
	.word	19894
	.word	16
	.word	40667
	.word	16344
	.word	0
	.word	47275
	.word	5106
	.word	2805
	.word	59896
	.word	49108
	.word	0
	.word	45414
	.word	34202
	.word	16579
	.word	49848
	.word	16336
	.word	0
	.word	43375
	.word	36280
	.word	61521
	.word	34871
	.word	16386
	.word	0
	.word	16506
	.word	6420
	.word	3056
	.word	34509
	.word	16379
	.word	0
	.word	1987
	.word	42760
	.word	41343
	.word	63988
	.word	49143
	.word	0
	.word	60842
	.word	56864
	.word	63566
	.word	58939
	.word	16372
	.word	0
	.word	55417
	.word	6969
	.word	17921
	.word	53943
	.word	49137
	.word	0
	.word	51573
	.word	24611
	.word	61942
	.word	49060
	.word	16366
	.word	0
	.word	60047
	.word	61936
	.word	1505
	.word	44346
	.word	49131
	.word	0
	.word	6985
	.word	28094
	.word	62181
	.word	39841
	.word	16360
	.word	0
	.word	44966
	.word	25211
	.word	37807
	.word	35582
	.word	49125
	.word	0
	.word	18549
	.word	30768
	.word	43886
	.word	63182
	.word	16353
	.word	0
	.word	28367
	.word	55574
	.word	54863
	.word	55743
	.word	49118
	.word	0
	.word	14135
	.word	59968
	.word	11402
	.word	48712
	.word	16347
	.word	0
	.word	10831
	.word	6630
	.word	39162
	.word	41467
	.word	49112
	.word	0
	.word	44318
	.word	36886
	.word	57433
	.word	64946
	.word	16340
	.word	0
	.word	48971
	.word	52692
	.word	39577
	.word	40471
	.word	49105
	.word	0
	.word	51132
	.word	42759
	.word	59794
	.word	56909
	.word	16332
	.word	0
	.word	25732
	.word	63966
	.word	62259
	.word	42244
	.word	16386
	.word	0
	.word	59756
	.word	3318
	.word	14822
	.word	57094
	.word	16378
	.word	0
	.word	53698
	.word	1627
	.word	14262
	.word	43879
	.word	49143
	.word	0
	.word	39975
	.word	26109
	.word	52231
	.word	33570
	.word	16372
	.word	0
	.word	51646
	.word	31403
	.word	31812
	.word	51139
	.word	49136
	.word	0
	.word	2483
	.word	26589
	.word	1676
	.word	38780
	.word	16365
	.word	0
	.word	48498
	.word	1356
	.word	1608
	.word	58560
	.word	49129
	.word	0
	.word	3924
	.word	17004
	.word	812
	.word	44025
	.word	16358
	.word	0
	.word	46861
	.word	28945
	.word	33303
	.word	32957
	.word	49123
	.word	0
	.word	44566
	.word	64586
	.word	45755
	.word	49135
	.word	16351
	.word	0
	.word	24613
	.word	10799
	.word	48364
	.word	36456
	.word	49116
	.word	0
	.word	30085
	.word	45589
	.word	61330
	.word	53663
	.word	16344
	.word	0
	.word	33497
	.word	5155
	.word	3788
	.word	38522
	.word	49109
	.word	0
	.word	14725
	.word	21779
	.word	34456
	.word	50904
	.word	16337
	.word	0
	.word	52940
	.word	10411
	.word	49330
	.word	53514
	.word	49101
	.word	0
	.word	34723
	.word	21028
	.word	57012
	.word	63430
	.word	16328
	.word	0
	.word	27380
	.word	54941
	.word	64714
	.word	51012
	.word	16386
	.word	0
	.word	45653
	.word	64195
	.word	32236
	.word	47349
	.word	16378
	.word	0
	.word	879
	.word	33324
	.word	35436
	.word	60443
	.word	49142
	.word	0
	.word	46894
	.word	26961
	.word	28118
	.word	38458
	.word	16371
	.word	0
	.word	44554
	.word	3572
	.word	63976
	.word	48787
	.word	49135
	.word	0
	.word	2272
	.word	4313
	.word	35324
	.word	61701
	.word	16363
	.word	0
	.word	30703
	.word	23672
	.word	41539
	.word	38897
	.word	49128
	.word	0
	.word	37671
	.word	61454
	.word	12213
	.word	48895
	.word	16356
	.word	0
	.word	61848
	.word	29005
	.word	2524
	.word	61278
	.word	49120
	.word	0
	.word	28836
	.word	16521
	.word	28345
	.word	38282
	.word	16349
	.word	0
	.word	22288
	.word	21607
	.word	62473
	.word	47665
	.word	49113
	.word	0
	.word	61670
	.word	56396
	.word	40174
	.word	58939
	.word	16341
	.word	0
	.word	11500
	.word	65235
	.word	61923
	.word	35576
	.word	49106
	.word	0
	.word	11242
	.word	2180
	.word	826
	.word	39560
	.word	16334
	.word	0
	.word	43321
	.word	47027
	.word	63349
	.word	35004
	.word	49098
	.word	0
	.word	4958
	.word	42423
	.word	31587
	.word	34920
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	0
	.word	61440
	.word	16386
	.word	0
	.word	62439
	.word	8267
	.word	42241
	.word	39352
	.word	16378
	.word	0
	.word	51100
	.word	10343
	.word	41485
	.word	41791
	.word	49142
	.word	0
	.word	22569
	.word	27301
	.word	5322
	.word	44285
	.word	16370
	.word	0
	.word	35593
	.word	39735
	.word	46317
	.word	46825
	.word	49134
	.word	0
	.word	62209
	.word	35548
	.word	38355
	.word	49405
	.word	16362
	.word	0
	.word	41076
	.word	45055
	.word	12154
	.word	52016
	.word	49126
	.word	0
	.word	33794
	.word	34841
	.word	26433
	.word	54648
	.word	16354
	.word	0
	.word	32708
	.word	8606
	.word	28372
	.word	57292
	.word	49118
	.word	0
	.word	58537
	.word	41843
	.word	17546
	.word	59935
	.word	16346
	.word	0
	.word	14261
	.word	60109
	.word	32952
	.word	62535
	.word	49110
	.word	0
	.word	15086
	.word	37621
	.word	31044
	.word	64853
	.word	16338
	.word	0
	.word	41112
	.word	8679
	.word	1028
	.word	32860
	.word	49103
	.word	0
	.word	5006
	.word	59706
	.word	53773
	.word	61388
	.word	16330
	.word	0
	.word	8320
	.word	26227
	.word	30276
	.word	45657
	.word	49094
	.word	0
	.word	12516
	.word	27384
	.word	49627
	.word	38295
	.word	16321
	.word	0
	.word	43375
	.word	36280
	.word	61521
	.word	36919
	.word	16387
	.word	0
	.word	13964
	.word	47780
	.word	48486
	.word	65532
	.word	16377
	.word	0
	.word	53655
	.word	14178
	.word	29810
	.word	57985
	.word	49141
	.word	0
	.word	40644
	.word	13825
	.word	33576
	.word	51229
	.word	16369
	.word	0
	.word	43867
	.word	51159
	.word	21808
	.word	45192
	.word	49133
	.word	0
	.word	64774
	.word	25334
	.word	42575
	.word	39806
	.word	16361
	.word	0
	.word	8550
	.word	43268
	.word	19146
	.word	35010
	.word	49125
	.word	0
	.word	10274
	.word	39807
	.word	58829
	.word	61491
	.word	16352
	.word	0
	.word	64510
	.word	64333
	.word	45409
	.word	53921
	.word	49116
	.word	0
	.word	7837
	.word	47962
	.word	10236
	.word	47211
	.word	16344
	.word	0
	.word	58632
	.word	16136
	.word	57648
	.word	41252
	.word	49108
	.word	0
	.word	50839
	.word	20695
	.word	9993
	.word	35851
	.word	16336
	.word	0
	.word	53977
	.word	3173
	.word	20021
	.word	60931
	.word	49099
	.word	0
	.word	21252
	.word	21065
	.word	18238
	.word	47764
	.word	16327
	.word	0
	.word	15598
	.word	48005
	.word	54092
	.word	59667
	.word	49090
	.word	0
	.word	43865
	.word	24204
	.word	30376
	.word	42053
	.word	16317
	.word	0
	.word	25732
	.word	63966
	.word	62259
	.word	44292
	.word	16387
	.word	0
	.word	57042
	.word	17954
	.word	41982
	.word	54649
	.word	16377
	.word	0
	.word	35272
	.word	59341
	.word	60292
	.word	40343
	.word	49141
	.word	0
	.word	50299
	.word	52389
	.word	64962
	.word	59502
	.word	16368
	.word	0
	.word	39701
	.word	8675
	.word	64086
	.word	43833
	.word	49132
	.word	0
	.word	32919
	.word	36749
	.word	17653
	.word	64514
	.word	16359
	.word	0
	.word	20450
	.word	29105
	.word	54347
	.word	47425
	.word	49123
	.word	0
	.word	51457
	.word	46834
	.word	19008
	.word	34827
	.word	16351
	.word	0
	.word	56572
	.word	25900
	.word	35557
	.word	51097
	.word	49114
	.word	0
	.word	52629
	.word	22805
	.word	35492
	.word	37443
	.word	16342
	.word	0
	.word	60134
	.word	44346
	.word	29251
	.word	54790
	.word	49105
	.word	0
	.word	40525
	.word	1894
	.word	29226
	.word	39888
	.word	16333
	.word	0
	.word	58308
	.word	40784
	.word	61879
	.word	56822
	.word	49096
	.word	0
	.word	39626
	.word	13594
	.word	33728
	.word	37362
	.word	16324
	.word	0
	.word	30018
	.word	64156
	.word	50426
	.word	39179
	.word	49087
	.word	0
	.word	26770
	.word	35175
	.word	34395
	.word	46390
	.word	16313
	.word	0
	.word	27380
	.word	54941
	.word	64714
	.word	53060
	.word	16387
	.word	0
	.word	21489
	.word	47049
	.word	52913
	.word	45633
	.word	16377
	.word	0
	.word	34273
	.word	45339
	.word	64425
	.word	56278
	.word	49140
	.word	0
	.word	9281
	.word	44296
	.word	1457
	.word	34678
	.word	16368
	.word	0
	.word	54032
	.word	26533
	.word	19904
	.word	42704
	.word	49131
	.word	0
	.word	4885
	.word	48150
	.word	35442
	.word	52549
	.word	16358
	.word	0
	.word	37026
	.word	46558
	.word	719
	.word	64617
	.word	49121
	.word	0
	.word	24924
	.word	32254
	.word	45168
	.word	39698
	.word	16349
	.word	0
	.word	58798
	.word	11406
	.word	38703
	.word	48743
	.word	49112
	.word	0
	.word	65026
	.word	39487
	.word	36015
	.word	59805
	.word	16339
	.word	0
	.word	17049
	.word	61342
	.word	62907
	.word	36661
	.word	49103
	.word	0
	.word	29048
	.word	51483
	.word	25283
	.word	44910
	.word	16330
	.word	0
	.word	26979
	.word	49508
	.word	25129
	.word	54883
	.word	49093
	.word	0
	.word	1679
	.word	10398
	.word	12735
	.word	33027
	.word	16321
	.word	0
	.word	27523
	.word	22868
	.word	1994
	.word	36413
	.word	49084
	.word	0
	.word	13664
	.word	7895
	.word	2857
	.word	54679
	.word	16310
	.word	0
	.type	_ERFC_TABLE,@object
	.size	_ERFC_TABLE,3840
	.space 768, 0x00 	# pad
	.align 16
_EXP_POLY:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	46603
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.type	_EXP_POLY,@object
	.size	_EXP_POLY,84
	.space 12, 0x00 	# pad
	.align 16
_T_TABLE:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	43884
	.word	55705
	.word	60751
	.word	32945
	.word	16383
	.word	0
	.word	1907
	.word	48131
	.word	53747
	.word	33124
	.word	16383
	.word	0
	.word	9708
	.word	29692
	.word	44867
	.word	33304
	.word	16383
	.word	0
	.word	41431
	.word	44075
	.word	34456
	.word	33485
	.word	16383
	.word	0
	.word	60983
	.word	61366
	.word	22862
	.word	33667
	.word	16383
	.word	0
	.word	16454
	.word	44254
	.word	10435
	.word	33850
	.word	16383
	.word	0
	.word	6697
	.word	14236
	.word	63062
	.word	34033
	.word	16383
	.word	0
	.word	31509
	.word	52296
	.word	50023
	.word	34218
	.word	16383
	.word	0
	.word	47620
	.word	37439
	.word	37211
	.word	34404
	.word	16383
	.word	0
	.word	4112
	.word	40589
	.word	24982
	.word	34591
	.word	16383
	.word	0
	.word	55186
	.word	63128
	.word	13695
	.word	34779
	.word	16383
	.word	0
	.word	34087
	.word	37594
	.word	3712
	.word	34968
	.word	16383
	.word	0
	.word	24541
	.word	24974
	.word	60931
	.word	35157
	.word	16383
	.word	0
	.word	64922
	.word	18798
	.word	54645
	.word	35348
	.word	16383
	.word	0
	.word	35108
	.word	11378
	.word	50757
	.word	35540
	.word	16383
	.word	0
	.word	55015
	.word	60043
	.word	49635
	.word	35733
	.word	16383
	.word	0
	.word	19934
	.word	25711
	.word	51652
	.word	35927
	.word	16383
	.word	0
	.word	43494
	.word	32347
	.word	57179
	.word	36122
	.word	16383
	.word	0
	.word	40150
	.word	8934
	.word	1056
	.word	36319
	.word	16383
	.word	0
	.word	21440
	.word	17869
	.word	14731
	.word	36516
	.word	16383
	.word	0
	.word	58820
	.word	59080
	.word	33047
	.word	36714
	.word	16383
	.word	0
	.word	45532
	.word	5222
	.word	56387
	.word	36913
	.word	16383
	.word	0
	.word	45355
	.word	61156
	.word	19595
	.word	37114
	.word	16383
	.word	0
	.word	49974
	.word	43793
	.word	54131
	.word	37315
	.word	16383
	.word	0
	.word	63916
	.word	38193
	.word	29309
	.word	37518
	.word	16383
	.word	0
	.word	59692
	.word	5094
	.word	11055
	.word	37722
	.word	16383
	.word	0
	.word	1206
	.word	43804
	.word	65295
	.word	37926
	.word	16383
	.word	0
	.word	2401
	.word	65271
	.word	61352
	.word	38132
	.word	16383
	.word	0
	.word	32751
	.word	54988
	.word	65158
	.word	38339
	.word	16383
	.word	0
	.word	23040
	.word	8216
	.word	11575
	.word	38548
	.word	16383
	.word	0
	.word	45198
	.word	61818
	.word	32073
	.word	38757
	.word	16383
	.word	0
	.word	43375
	.word	36280
	.word	61521
	.word	38967
	.word	16383
	.word	0
	.word	35242
	.word	26305
	.word	34786
	.word	39179
	.word	16383
	.word	0
	.word	64101
	.word	8375
	.word	17811
	.word	39392
	.word	16383
	.word	0
	.word	34378
	.word	38143
	.word	11004
	.word	39606
	.word	16383
	.word	0
	.word	21817
	.word	54606
	.word	14777
	.word	39821
	.word	16383
	.word	0
	.word	11309
	.word	11971
	.word	29544
	.word	40037
	.word	16383
	.word	0
	.word	46929
	.word	11519
	.word	55719
	.word	40254
	.word	16383
	.word	0
	.word	9248
	.word	40263
	.word	28184
	.word	40473
	.word	16383
	.word	0
	.word	4526
	.word	37281
	.word	12896
	.word	40693
	.word	16383
	.word	0
	.word	56582
	.word	25600
	.word	10277
	.word	40914
	.word	16383
	.word	0
	.word	20418
	.word	47473
	.word	20751
	.word	41136
	.word	16383
	.word	0
	.word	46820
	.word	34116
	.word	44746
	.word	41359
	.word	16383
	.word	0
	.word	26649
	.word	3145
	.word	17155
	.word	41584
	.word	16383
	.word	0
	.word	48019
	.word	59394
	.word	3944
	.word	41810
	.word	16383
	.word	0
	.word	32926
	.word	2534
	.word	5550
	.word	42037
	.word	16383
	.word	0
	.word	62265
	.word	48798
	.word	22406
	.word	42265
	.word	16383
	.word	0
	.word	14570
	.word	45393
	.word	54953
	.word	42494
	.word	16383
	.word	0
	.word	27422
	.word	61160
	.word	38095
	.word	42725
	.word	16383
	.word	0
	.word	13674
	.word	59749
	.word	37812
	.word	42957
	.word	16383
	.word	0
	.word	3593
	.word	31538
	.word	54550
	.word	43190
	.word	16383
	.word	0
	.word	3832
	.word	60028
	.word	23220
	.word	43425
	.word	16383
	.word	0
	.word	30249
	.word	60560
	.word	9810
	.word	43661
	.word	16383
	.word	0
	.word	54071
	.word	43326
	.word	14773
	.word	43898
	.word	16383
	.word	0
	.word	59689
	.word	48703
	.word	38564
	.word	44136
	.word	16383
	.word	0
	.word	19142
	.word	17057
	.word	16106
	.word	44376
	.word	16383
	.word	0
	.word	47118
	.word	51765
	.word	13394
	.word	44617
	.word	16383
	.word	0
	.word	17269
	.word	26890
	.word	30893
	.word	44859
	.word	16383
	.word	0
	.word	17796
	.word	46816
	.word	3531
	.word	45103
	.word	16383
	.word	0
	.word	9616
	.word	53932
	.word	62849
	.word	45347
	.word	16383
	.word	0
	.word	65083
	.word	26136
	.word	12710
	.word	45594
	.word	16383
	.word	0
	.word	9353
	.word	43281
	.word	50194
	.word	45841
	.word	16383
	.word	0
	.word	38977
	.word	25931
	.word	44706
	.word	46090
	.word	16383
	.word	0
	.word	25732
	.word	63966
	.word	62259
	.word	46340
	.word	16383
	.word	0
	.word	63340
	.word	24277
	.word	37800
	.word	46592
	.word	16383
	.word	0
	.word	30609
	.word	10449
	.word	37347
	.word	46845
	.word	16383
	.word	0
	.word	7804
	.word	36004
	.word	61386
	.word	47099
	.word	16383
	.word	0
	.word	40681
	.word	25339
	.word	44871
	.word	47355
	.word	16383
	.word	0
	.word	40427
	.word	11275
	.word	53829
	.word	47612
	.word	16383
	.word	0
	.word	17915
	.word	4926
	.word	23218
	.word	47871
	.word	16383
	.word	0
	.word	64269
	.word	62185
	.word	19070
	.word	48131
	.word	16383
	.word	0
	.word	14015
	.word	22540
	.word	41887
	.word	48392
	.word	16383
	.word	0
	.word	37858
	.word	34313
	.word	26633
	.word	48655
	.word	16383
	.word	0
	.word	4227
	.word	31347
	.word	39350
	.word	48919
	.word	16383
	.word	0
	.word	36272
	.word	61648
	.word	15009
	.word	49185
	.word	16383
	.word	0
	.word	37974
	.word	26224
	.word	19658
	.word	49452
	.word	16383
	.word	0
	.word	26227
	.word	7741
	.word	53809
	.word	49720
	.word	16383
	.word	0
	.word	25607
	.word	9367
	.word	52442
	.word	49990
	.word	16383
	.word	0
	.word	52019
	.word	21300
	.word	16076
	.word	50262
	.word	16383
	.word	0
	.word	56029
	.word	21766
	.word	10769
	.word	50535
	.word	16383
	.word	0
	.word	24441
	.word	43556
	.word	37045
	.word	50809
	.word	16383
	.word	0
	.word	45405
	.word	43961
	.word	29896
	.word	51085
	.word	16383
	.word	0
	.word	11333
	.word	36862
	.word	55388
	.word	51362
	.word	16383
	.word	0
	.word	10147
	.word	28207
	.word	48518
	.word	51641
	.word	16383
	.word	0
	.word	30542
	.word	17040
	.word	9822
	.word	51922
	.word	16383
	.word	0
	.word	31837
	.word	62066
	.word	5374
	.word	52204
	.word	16383
	.word	0
	.word	52690
	.word	20541
	.word	35718
	.word	52487
	.word	16383
	.word	0
	.word	32996
	.word	8068
	.word	35861
	.word	52772
	.word	16383
	.word	0
	.word	6337
	.word	6425
	.word	6351
	.word	53059
	.word	16383
	.word	0
	.word	9621
	.word	61227
	.word	13274
	.word	53347
	.word	16383
	.word	0
	.word	39622
	.word	20841
	.word	57186
	.word	53636
	.word	16383
	.word	0
	.word	58458
	.word	61738
	.word	7569
	.word	53928
	.word	16383
	.word	0
	.word	50041
	.word	34202
	.word	61593
	.word	54220
	.word	16383
	.word	0
	.word	64031
	.word	53229
	.word	23211
	.word	54515
	.word	16383
	.word	0
	.word	52743
	.word	40859
	.word	24062
	.word	54811
	.word	16383
	.word	0
	.word	27380
	.word	54941
	.word	64714
	.word	55108
	.word	16383
	.word	0
	.word	11396
	.word	28083
	.word	14668
	.word	55408
	.word	16383
	.word	0
	.word	55222
	.word	30895
	.word	5570
	.word	55709
	.word	16383
	.word	0
	.word	52338
	.word	10953
	.word	37999
	.word	56011
	.word	16383
	.word	0
	.word	14165
	.word	56050
	.word	46999
	.word	56315
	.word	16383
	.word	0
	.word	19488
	.word	2098
	.word	33157
	.word	56621
	.word	16383
	.word	0
	.word	37156
	.word	24078
	.word	62594
	.word	56928
	.word	16383
	.word	0
	.word	17440
	.word	47344
	.word	4830
	.word	57238
	.word	16383
	.word	0
	.word	57617
	.word	10900
	.word	57068
	.word	57548
	.word	16383
	.word	0
	.word	54121
	.word	65155
	.word	23295
	.word	57861
	.word	16383
	.word	0
	.word	23121
	.word	48778
	.word	35186
	.word	58175
	.word	16383
	.word	0
	.word	43149
	.word	14141
	.word	27808
	.word	58491
	.word	16383
	.word	0
	.word	18600
	.word	31875
	.word	1767
	.word	58809
	.word	16383
	.word	0
	.word	52770
	.word	60959
	.word	23210
	.word	59128
	.word	16383
	.word	0
	.word	56424
	.word	15435
	.word	27216
	.word	59449
	.word	16383
	.word	0
	.word	35927
	.word	27727
	.word	14400
	.word	59772
	.word	16383
	.word	0
	.word	14639
	.word	56612
	.word	50919
	.word	60096
	.word	16383
	.word	0
	.word	48604
	.word	19484
	.word	6326
	.word	60423
	.word	16383
	.word	0
	.word	11140
	.word	55700
	.word	12318
	.word	60751
	.word	16383
	.word	0
	.word	603
	.word	3491
	.word	3992
	.word	61081
	.word	16383
	.word	0
	.word	62923
	.word	56538
	.word	47515
	.word	61412
	.word	16383
	.word	0
	.word	17673
	.word	44297
	.word	12455
	.word	61746
	.word	16383
	.word	0
	.word	45370
	.word	23039
	.word	30524
	.word	62081
	.word	16383
	.word	0
	.word	6747
	.word	14948
	.word	36830
	.word	62418
	.word	16383
	.word	0
	.word	52268
	.word	9350
	.word	32021
	.word	62757
	.word	16383
	.word	0
	.word	33902
	.word	29503
	.word	16748
	.word	63098
	.word	16383
	.word	0
	.word	15289
	.word	2769
	.word	57203
	.word	63440
	.word	16383
	.word	0
	.word	47732
	.word	24020
	.word	22971
	.word	63785
	.word	16383
	.word	0
	.word	826
	.word	29226
	.word	45787
	.word	64131
	.word	16383
	.word	0
	.word	40009
	.word	58864
	.word	60780
	.word	64479
	.word	16383
	.word	0
	.word	49525
	.word	62598
	.word	3084
	.word	64830
	.word	16383
	.word	0
	.word	34892
	.word	31631
	.word	4444
	.word	65182
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.type	_T_TABLE,@object
	.size	_T_TABLE,1548
	.space 4, 0x00 	# pad
	.align 8
.L_2il0floatpacket.0:
	.long	0x00100000,0x41f00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 2
_EXPL_TABLE:
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	16390
	.word	0
	.word	0
	.word	53760
	.word	6135
	.word	45426
	.word	16375
	.word	0
	.word	40944
	.word	28977
	.word	20696
	.word	49689
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	16382
	.word	0
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.type	_EXPL_TABLE,@object
	.size	_EXPL_TABLE,96
	.data
	.section .note.GNU-stack, ""
# End
