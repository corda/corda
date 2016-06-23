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
	.file "tanl.c"
	.text
..TXTST0:
# -- Begin  tanl
	.text
       .align    16,0x90
	.globl tanl
tanl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 88(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %edi
        andl      $32767, %edi
        movzwl    14(%esp), %eax
        cmpl      $16373, %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jge       ..B1.41
..B1.4:
        cmpl      $16371, %edi
        jge       ..B1.33
..B1.5:
        cmpl      $16364, %edi
        jge       ..B1.25
..B1.6:
        cmpl      $16308, %edi
        jge       ..B1.17
..B1.7:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.87
..B1.8:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movzwl    16(%ebp), %edi
        movl      $1, %esi
        andl      $32767, %edi
..B1.11:
        testl     %edi, %edi
        jle       ..B1.83
..B1.12:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ebx)
        fstpt     16(%esp)
..B1.13:
        testl     %esi, %esi
        je        ..B1.15
..B1.14:
        fldcw     14(%esp)
..B1.15:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.16:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.89
..B1.18:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.19:
        fldcw     12(%esp)
..B1.20:
        movl      $1, %eax
..B1.21:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      _P3@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.23
..B1.22:
        fldcw     14(%esp)
..B1.23:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.24:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.91
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
        fldt      12+_P2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _P2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
..B1.31:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.32:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.33:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.93
..B1.34:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.35:
        fldcw     12(%esp)
..B1.36:
        movl      $1, %eax
..B1.37:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_P1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_P1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _P1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.39
..B1.38:
        fldcw     14(%esp)
..B1.39:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.40:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.99
..B1.42:
        orl       $-64768, %eax
        movw      %ax, 12(%esp)
..B1.43:
        fldcw     12(%esp)
..B1.44:
        movzwl    16(%ebp), %edi
        movl      $1, %esi
        andl      $32767, %edi
..B1.45:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        cmpl      $16413, %edi
        jge       ..B1.52
..B1.46:
        fldt      8(%ebp)
        fldl      _Pi4Inv@GOTOFF(%ebx)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fld       %st(0)
        fmul      %st(3), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     60(%esp)
        movzwl    68(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.48
..B1.47:
        fstp      %st(0)
        fstp      %st(0)
        fldl      _TWO_48H@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(1), %st
        fld       %st(2)
        movl      $1, %edx
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(2)
        fsub      %st(1), %st
        fldt      _KPi04@GOTOFF(%ebx)
        fmul      %st(4), %st
        fstpt     60(%esp)
        fxch      %st(3)
        jmp       ..B1.63
..B1.48:
        fstp      %st(2)
        fldl      _TWO_32H@GOTOFF(%ebx)
        negl      %ecx
        addl      $30, %ecx
        movl      64(%esp), %edx
        shrl      %cl, %edx
        incl      %edx
        movl      %edx, %eax
        andl      $-2, %eax
        movl      %eax, 16(%esp)
        cmpl      $16400, %edi
        fildl     16(%esp)
        jge       ..B1.50
..B1.49:
        fldl      _Pi04x3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fldl      8+_Pi04x3@GOTOFF(%ebx)
        fmul      %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      16+_Pi04x3@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        jmp       ..B1.51
..B1.50:
        fldl      _Pi04x5@GOTOFF(%ebx)
        fmul      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fldl      8+_Pi04x5@GOTOFF(%ebx)
        fmul      %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      16+_Pi04x5@GOTOFF(%ebx)
        fmul      %st(2), %st
        fld       %st(0)
        fsubr     %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldl      24+_Pi04x5@GOTOFF(%ebx)
        fmul      %st(2), %st
        fld       %st(0)
        fsubr     %st(4), %st
        fsubr     %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      32+_Pi04x5@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
..B1.51:
        fldl      _TWO_48H@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(1), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fmulp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      _KPi04@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     60(%esp)
        jmp       ..B1.63
..B1.52:
        cmpl      $32767, %edi
        jne       ..B1.61
..B1.53:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.56
..B1.54:
        cmpl      $0, 8(%ebp)
        jne       ..B1.56
..B1.55:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        fstpt     16(%esp)
        jmp       ..B1.57
..B1.56:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ebx)
        fstpt     16(%esp)
..B1.57:
        testl     %esi, %esi
        je        ..B1.59
..B1.58:
        fldcw     14(%esp)
..B1.59:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.60:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fstl      16(%esp)
        addl      $-32, %esp
        fldt      8(%ebp)
        lea       104(%esp), %eax
        fmulp     %st, %st(1)
        fstpt     (%esp)
        movl      $0, 12(%esp)
        movl      %eax, 16(%esp)
        call      __libm_reduce_pi04l
..B1.102:
        movl      %eax, %edx
        addl      $32, %esp
..B1.62:
        fldl      72(%esp)
        incl      %edx
        fld       %st(0)
        fldl      16(%esp)
        fldl      _TWO_48H@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(4), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fmul      %st(1), %st
        fld       %st(0)
        fxch      %st(4)
        faddl     80(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      _KPi04@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     60(%esp)
..B1.63:
        movzwl    68(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.73
..B1.64:
        testb     $2, %dl
        je        ..B1.69
..B1.65:
        fldl      8+_ones@GOTOFF(%ebx)
        testl     %esi, %esi
        fdiv      %st(1), %st
        fmul      %st, %st(3)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(5)
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fxch      %st(2)
        fstpt     48(%esp)
        fldt      48(%esp)
        fmul      %st(0), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fldt      60+_PG@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      48+_PG@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      36+_PG@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_PG@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(3)
        fadd      %st(5), %st
        fsub      %st(5), %st
        fldt      12+_PG@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(6)
        fldt      _PG@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(3)
        fsub      %st(2), %st
        fxch      %st(6)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      32(%esp)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        faddl     _ones@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        je        ..B1.107
..B1.66:
        fstpt     60(%esp)
..B1.103:
        fldcw     14(%esp)
        jmp       ..B1.67
..B1.107:
        fstp      %st(0)
..B1.67:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.68:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.69:
        fldt      60+_PT@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testl     %esi, %esi
        fxch      %st(3)
        fstpt     48(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        fmul      %st, %st(1)
        fldt      48+_PT@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      36+_PT@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      24+_PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      12+_PT@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fldt      _PT@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        fmulp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.108
..B1.70:
        fstpt     60(%esp)
..B1.104:
        fldcw     14(%esp)
        jmp       ..B1.71
..B1.108:
        fstp      %st(0)
..B1.71:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.72:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.73:
        fstp      %st(0)
        fldl      .L_2il0floatpacket.0@GOTOFF(%ebx)
        negl      %ecx
        fldl      .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $30, %ecx
        movl      64(%esp), %eax
        shrl      %cl, %eax
        movl      %eax, %ecx
        movzbl    17(%ebp), %edi
        negl      %ecx
        testl     $128, %edi
        jne       ..L3
        movl      %eax, %ecx
..L3:
        movl      %ecx, 16(%esp)
        testb     $2, %dl
        fildl     16(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fadd      %st(3), %st
        je        ..B1.78
..B1.74:
        fld       %st(1)
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(2), %st
        shll      $4, %ecx
        faddp     %st, %st(1)
        fld       %st(2)
        testl     %esi, %esi
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(4)
        fstpt     32(%esp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      60+_PT@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldt      48+_PT@GOTOFF(%ebx)
        fmul      %st(5), %st
        fldt      36+_PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      24+_PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      12+_PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(4)
        fldt      _PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fldl      272+_TG@GOTOFF(%ecx,%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fxch      %st(5)
        faddl     280+_TT@GOTOFF(%ecx,%ebx)
        fxch      %st(3)
        fmull     280+_TG@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(4)
        fldt      32(%esp)
        fadd      %st, %st(5)
        fsubr     %st, %st(5)
        fxch      %st(2)
        fsub      %st(5), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        faddp     %st, %st(1)
        fxch      %st(3)
        faddl     272+_TT@GOTOFF(%ecx,%ebx)
        fld       %st(0)
        fadd      %st(3), %st
        fdivrl    _ones@GOTOFF(%ebx)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(0)
        fsub      %st(3), %st
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(3), %st
        fld       %st(3)
        fmul      %st(6), %st
        fxch      %st(6)
        fadd      %st(7), %st
        fxch      %st(7)
        fmulp     %st, %st(4)
        faddl     8+_ones@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fsubp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        faddl     272+_GG@GOTOFF(%ecx,%ebx)
        fxch      %st(1)
        faddl     280+_GG@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      48(%esp)
        je        ..B1.109
..B1.75:
        fstpt     60(%esp)
..B1.105:
        fldcw     14(%esp)
        jmp       ..B1.76
..B1.109:
        fstp      %st(0)
..B1.76:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.77:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        fldt      48+_PT@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(4)
        fstpt     48(%esp)
        fldt      60+_PT@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldt      36+_PT@GOTOFF(%ebx)
        shll      $4, %ecx
        faddp     %st, %st(1)
        fmul      %st(4), %st
        testl     %esi, %esi
        fldt      24+_PT@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      12+_PT@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(4)
        fldt      _PT@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(3)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      48(%esp)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(1), %st
        fldl      272+_TG@GOTOFF(%ecx,%ebx)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(5), %st
        fxch      %st(3)
        faddl     280+_GG@GOTOFF(%ecx,%ebx)
        fxch      %st(5)
        faddl     272+_GG@GOTOFF(%ecx,%ebx)
        fxch      %st(2)
        fmull     280+_TG@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(5), %st
        fdivrl    8+_ones@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fld       %st(0)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(0)
        fsub      %st(5), %st
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(5), %st
        fld       %st(5)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(5), %st
        fxch      %st(5)
        fmulp     %st, %st(6)
        faddl     _ones@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fldl      272+_TT@GOTOFF(%ecx,%ebx)
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubr     %st(2), %st
        fxch      %st(3)
        faddl     280+_TT@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        je        ..B1.110
..B1.79:
        fstpt     60(%esp)
..B1.106:
        fldcw     14(%esp)
        jmp       ..B1.80
..B1.110:
        fstp      %st(0)
..B1.80:
        movl      88(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.82
..B1.81:
        fldt      16(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.82:
        call      __stack_chk_fail@PLT
..B1.83:
        cmpl      $0, 12(%ebp)
        jne       ..B1.85
..B1.84:
        cmpl      $0, 8(%ebp)
        je        ..B1.86
..B1.85:
        fldt      _small_value_80@GOTOFF(%ebx)
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st(1), %st
        movzbl    17(%ebp), %edx
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ebx)
        andl      $128, %edx
        shrl      $7, %edx
        fstpt     16(%esp)
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _small_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.13
..B1.86:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.13
..B1.87:
        xorl      %esi, %esi
        jmp       ..B1.11
..B1.89:
        xorl      %eax, %eax
        jmp       ..B1.21
..B1.91:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.93:
        xorl      %eax, %eax
        jmp       ..B1.37
..B1.99:
        xorl      %esi, %esi
        jmp       ..B1.45
        .align    16,0x90
	.type	tanl,@function
	.size	tanl,.-tanl
	.data
# -- End  tanl
	.section .rodata, "a"
	.align 16
	.align 16
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 16
_P3:
	.word	13380
	.word	43827
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.type	_P3,@object
	.size	_P3,12
	.space 4, 0x00 	# pad
	.align 16
_P2:
	.word	42806
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	23834
	.word	52331
	.word	35007
	.word	34952
	.word	16380
	.word	0
	.type	_P2,@object
	.size	_P2,24
	.space 8, 0x00 	# pad
	.align 16
_P1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	31445
	.word	3491
	.word	53469
	.word	56589
	.word	16378
	.word	0
	.word	16733
	.word	58542
	.word	44372
	.word	45863
	.word	16377
	.word	0
	.type	_P1,@object
	.size	_P1,48
	.align 16
_Pi4Inv:
	.long	1841940611
	.long	1072979760
	.type	_Pi4Inv,@object
	.size	_Pi4Inv,8
	.space 8, 0x00 	# pad
	.align 16
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 16
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.space 8, 0x00 	# pad
	.align 16
_KPi04:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16387
	.word	0
	.type	_KPi04,@object
	.size	_KPi04,12
	.space 4, 0x00 	# pad
	.align 16
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.space 8, 0x00 	# pad
	.align 16
_Pi04x3:
	.long	1413754112
	.long	1072243195
	.long	2563527040
	.long	1021855384
	.long	3417685868
	.long	3118450936
	.type	_Pi04x3,@object
	.size	_Pi04x3,24
	.space 8, 0x00 	# pad
	.align 16
_Pi04x5:
	.long	1413480448
	.long	1072243195
	.long	442499072
	.long	1036039265
	.long	771751936
	.long	999496074
	.long	622854144
	.long	963347354
	.long	1396597664
	.long	922906692
	.type	_Pi04x5,@object
	.size	_Pi04x5,40
	.space 8, 0x00 	# pad
	.align 16
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 16
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 16
_PG:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	46604
	.word	2912
	.word	24758
	.word	46603
	.word	16377
	.word	0
	.word	64867
	.word	2218
	.word	21984
	.word	35507
	.word	16374
	.word	0
	.word	37428
	.word	49066
	.word	48281
	.word	56811
	.word	16370
	.word	0
	.word	28819
	.word	40692
	.word	30466
	.word	45908
	.word	16367
	.word	0
	.word	58229
	.word	57025
	.word	27882
	.word	37211
	.word	16364
	.word	0
	.type	_PG,@object
	.size	_PG,72
	.space 8, 0x00 	# pad
	.align 16
_PT:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	35260
	.word	34952
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	59431
	.word	3445
	.word	53469
	.word	56589
	.word	16378
	.word	0
	.word	60805
	.word	19035
	.word	42054
	.word	45863
	.word	16377
	.word	0
	.word	32963
	.word	17667
	.word	64505
	.word	37174
	.word	16376
	.word	0
	.word	33271
	.word	14204
	.word	52730
	.word	60442
	.word	16374
	.word	0
	.type	_PT,@object
	.size	_PT,72
	.space 8, 0x00 	# pad
	.align 16
_TG:
	.long	2684354560
	.long	3221230545
	.long	1980244105
	.long	1046278452
	.long	0
	.long	3221225472
	.long	0
	.long	0
	.long	2684354560
	.long	3221230545
	.long	1980244105
	.long	1046278452
	.long	3758096384
	.long	3221246014
	.long	1513286675
	.long	1048381067
	.long	0
	.long	3221272655
	.long	4057975187
	.long	3193921290
	.long	2147483648
	.long	3221311866
	.long	2902494488
	.long	1046516509
	.long	3221225472
	.long	3221365863
	.long	1948335328
	.long	3196126810
	.long	2684354560
	.long	3221438007
	.long	3744091947
	.long	3192783219
	.long	2684354560
	.long	3221533379
	.long	3860423252
	.long	1047335024
	.long	2147483648
	.long	3221659806
	.long	862942416
	.long	1049198788
	.long	0
	.long	3221829776
	.long	543592927
	.long	3195468420
	.long	0
	.long	3222064283
	.long	2814267453
	.long	1048596082
	.long	536870912
	.long	3222337673
	.long	2980891112
	.long	3196362141
	.long	2684354560
	.long	3222595502
	.long	141458722
	.long	1049458206
	.long	3221225472
	.long	3223031588
	.long	2120823910
	.long	1049591613
	.long	3221225472
	.long	3223617753
	.long	663882580
	.long	3198186420
	.long	2147483648
	.long	3224659859
	.long	3051968734
	.long	3194791707
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1077176211
	.long	3051968734
	.long	1047308059
	.long	3221225472
	.long	1076134105
	.long	663882580
	.long	1050702772
	.long	3221225472
	.long	1075547940
	.long	2120823910
	.long	3197075261
	.long	2684354560
	.long	1075111854
	.long	141458722
	.long	3196941854
	.long	536870912
	.long	1074854025
	.long	2980891112
	.long	1048878493
	.long	0
	.long	1074580635
	.long	2814267453
	.long	3196079730
	.long	0
	.long	1074346128
	.long	543592927
	.long	1047984772
	.long	2147483648
	.long	1074176158
	.long	862942416
	.long	3196682436
	.long	2684354560
	.long	1074049731
	.long	3860423252
	.long	3194818672
	.long	2684354560
	.long	1073954359
	.long	3744091947
	.long	1045299571
	.long	3221225472
	.long	1073882215
	.long	1948335328
	.long	1048643162
	.long	2147483648
	.long	1073828218
	.long	2902494488
	.long	3194000157
	.long	0
	.long	1073789007
	.long	4057975187
	.long	1046437642
	.long	3758096384
	.long	1073762366
	.long	1513286675
	.long	3195864715
	.long	2684354560
	.long	1073746897
	.long	1980244105
	.long	3193762100
	.long	0
	.long	1073741824
	.long	0
	.long	0
	.long	2684354560
	.long	1073746897
	.long	1980244105
	.long	3193762100
	.type	_TG,@object
	.size	_TG,560
	.align 16
_TT:
	.long	1610612736
	.long	3220285245
	.long	1765132704
	.long	1046851815
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	3221225472
	.long	3219980491
	.long	3100042604
	.long	3192709428
	.long	1073741824
	.long	3219800832
	.long	2956254064
	.long	1046813655
	.long	0
	.long	3219635098
	.long	829070670
	.long	1046664815
	.long	0
	.long	3219481016
	.long	3358544266
	.long	3195362300
	.long	2147483648
	.long	3219336728
	.long	4185368031
	.long	3193047295
	.long	536870912
	.long	3219200695
	.long	1373988029
	.long	1045159724
	.long	0
	.long	3219014926
	.long	4104169752
	.long	3192540796
	.long	2147483648
	.long	3218768505
	.long	843197631
	.long	3193568495
	.long	0
	.long	3218531914
	.long	1839318447
	.long	1044402419
	.long	1073741824
	.long	3218303496
	.long	1215676724
	.long	1044727608
	.long	1610612736
	.long	3218081786
	.long	2922073518
	.long	3193275942
	.long	0
	.long	3217651190
	.long	2818065035
	.long	1045408462
	.long	2147483648
	.long	3217226924
	.long	2142552361
	.long	1043903475
	.long	2147483648
	.long	3216586427
	.long	1153689642
	.long	3190339163
	.long	2147483648
	.long	3215533863
	.long	3288774277
	.long	3189012002
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1068050215
	.long	3288774277
	.long	1041528354
	.long	2147483648
	.long	1069102779
	.long	1153689642
	.long	1042855515
	.long	2147483648
	.long	1069743276
	.long	2142552361
	.long	3191387123
	.long	0
	.long	1070167542
	.long	2818065035
	.long	3192892110
	.long	1610612736
	.long	1070598138
	.long	2922073518
	.long	1045792294
	.long	1073741824
	.long	1070819848
	.long	1215676724
	.long	3192211256
	.long	0
	.long	1071048266
	.long	1839318447
	.long	3191886067
	.long	2147483648
	.long	1071284857
	.long	843197631
	.long	1046084847
	.long	0
	.long	1071531278
	.long	4104169752
	.long	1045057148
	.long	536870912
	.long	1071717047
	.long	1373988029
	.long	3192643372
	.long	2147483648
	.long	1071853080
	.long	4185368031
	.long	1045563647
	.long	0
	.long	1071997368
	.long	3358544266
	.long	1047878652
	.long	0
	.long	1072151450
	.long	829070670
	.long	3194148463
	.long	1073741824
	.long	1072317184
	.long	2956254064
	.long	3194297303
	.long	3221225472
	.long	1072496843
	.long	3100042604
	.long	1045225780
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1610612736
	.long	1072801597
	.long	1765132704
	.long	3194335463
	.type	_TT,@object
	.size	_TT,560
	.align 16
_GG:
	.long	3221225472
	.long	1072496843
	.long	3100042604
	.long	1045225780
	.long	0
	.long	1072693248
	.long	2680413623
	.long	2803148914
	.long	1610612736
	.long	1072801597
	.long	1765132704
	.long	3194335463
	.long	2147483648
	.long	1072922365
	.long	4224648010
	.long	1047203520
	.long	0
	.long	1073058513
	.long	1429029132
	.long	1047610106
	.long	0
	.long	1073213977
	.long	2662307862
	.long	3195905419
	.long	2147483648
	.long	1073394115
	.long	2332932060
	.long	3196140229
	.long	3221225472
	.long	1073606419
	.long	4089189126
	.long	3194062807
	.long	3758096384
	.long	1073801761
	.long	295749197
	.long	3195130376
	.long	1073741824
	.long	1073958991
	.long	862942416
	.long	3195633860
	.long	0
	.long	1074158535
	.long	1551453531
	.long	3197746903
	.long	0
	.long	1074421594
	.long	590804010
	.long	3196516863
	.long	0
	.long	1074786323
	.long	1872593098
	.long	1046983523
	.long	3758096384
	.long	1075059710
	.long	2286165020
	.long	1046614587
	.long	2147483648
	.long	1075509055
	.long	2023416727
	.long	3198791271
	.long	1610612736
	.long	1076121196
	.long	3469402705
	.long	3196752904
	.long	0
	.long	1077172992
	.long	2869016295
	.long	3199393764
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3224656640
	.long	2869016295
	.long	1051910116
	.long	1610612736
	.long	3223604844
	.long	3469402705
	.long	1049269256
	.long	2147483648
	.long	3222992703
	.long	2023416727
	.long	1051307623
	.long	3758096384
	.long	3222543358
	.long	2286165020
	.long	3194098235
	.long	0
	.long	3222269971
	.long	1872593098
	.long	3194467171
	.long	0
	.long	3221905242
	.long	590804010
	.long	1049033215
	.long	0
	.long	3221642183
	.long	1551453531
	.long	1050263255
	.long	1073741824
	.long	3221442639
	.long	862942416
	.long	1048150212
	.long	3758096384
	.long	3221285409
	.long	295749197
	.long	1047646728
	.long	3221225472
	.long	3221090067
	.long	4089189126
	.long	1046579159
	.long	2147483648
	.long	3220877763
	.long	2332932060
	.long	1048656581
	.long	0
	.long	3220697625
	.long	2662307862
	.long	1048421771
	.long	0
	.long	3220542161
	.long	1429029132
	.long	3195093754
	.long	2147483648
	.long	3220406013
	.long	4224648010
	.long	3194687168
	.long	1610612736
	.long	3220285245
	.long	1765132704
	.long	1046851815
	.long	0
	.long	3220176896
	.long	2680413623
	.long	655665266
	.long	3221225472
	.long	3219980491
	.long	3100042604
	.long	3192709428
	.type	_GG,@object
	.size	_GG,560
	.align 16
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
	.align 8
.L_2il0floatpacket.0:
	.long	0x54400000,0x3fa921fb
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x1a626331,0x3d80b461
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
