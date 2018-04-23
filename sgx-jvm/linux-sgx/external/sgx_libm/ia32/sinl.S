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
	.file "sinl.c"
	.text
..TXTST0:
# -- Begin  sinl
	.text
       .align    16,0x90
	.globl sinl
sinl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 152(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movzwl    14(%esp), %edx
        cmpl      $16378, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jge       ..B1.41
..B1.4:
        cmpl      $16372, %eax
        jge       ..B1.33
..B1.5:
        cmpl      $16364, %eax
        jge       ..B1.25
..B1.6:
        cmpl      $16308, %eax
        jge       ..B1.17
..B1.7:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.75
..B1.8:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movzwl    16(%ebp), %eax
        movl      $1, %esi
        andl      $32767, %eax
..B1.11:
        testl     %eax, %eax
        jle       ..B1.71
..B1.12:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ebx)
        fstpt     16(%esp)
..B1.13:
        testl     %esi, %esi
        je        ..B1.15
..B1.14:
        fldcw     14(%esp)
..B1.15:
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.16:
        fldt      16(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.77
..B1.18:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.19:
        fldcw     12(%esp)
..B1.20:
        movl      $1, %eax
..B1.21:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _SP3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.23
..B1.22:
        fldcw     14(%esp)
..B1.23:
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.24:
        fldt      16(%esp)
        addl      $180, %esp
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
        je        ..B1.79
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
        fldt      12+_SP2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _SP2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
..B1.31:
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.32:
        fldt      16(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.33:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.81
..B1.34:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
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
        fldt      36+_SP1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_SP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_SP1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _SP1@GOTOFF(%ebx)
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
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.40:
        fldt      16(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        movl      %edx, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.84
..B1.42:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.43:
        fldcw     12(%esp)
..B1.44:
        movzwl    16(%ebp), %eax
        movl      $1, %edi
        andl      $32767, %eax
..B1.45:
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        cmpl      $16413, %eax
        jge       ..B1.52
..B1.46:
        fldt      8(%ebp)
        fldl      _Pi4Inv@GOTOFF(%ebx)
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ebx,%esi,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     24(%esp)
        movzwl    32(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.48
..B1.47:
        fldl      _TWO_52H@GOTOFF(%ebx)
        movl      $1, %edx
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(1)
        fsub      %st(1), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.63
..B1.48:
        fldl      _TWO_32H@GOTOFF(%ebx)
        negl      %ecx
        addl      $30, %ecx
        movl      28(%esp), %edx
        shrl      %cl, %edx
        incl      %edx
        movl      %edx, %ecx
        andl      $-2, %ecx
        movl      %ecx, 16(%esp)
        cmpl      $16400, %eax
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
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      24+_Pi04x5@GOTOFF(%ebx)
        fmul      %st(2), %st
        fld       %st(0)
        fsubr     %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsubrp    %st, %st(2)
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
        fldl      _TWO_52H@GOTOFF(%ebx)
        movl      %edx, %eax
        fmul      %st(1), %st
        fld       %st(1)
        shrl      $2, %eax
        fadd      %st(1), %st
        xorl      %eax, %esi
        fsubp     %st, %st(1)
        andl      $1, %esi
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.63
..B1.52:
        cmpl      $32767, %eax
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
        testl     %edi, %edi
        je        ..B1.59
..B1.58:
        fldcw     14(%esp)
..B1.59:
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.60:
        fldt      16(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        fldt      8(%ebp)
        addl      $-32, %esp
        fmull     _ones@GOTOFF(%ebx,%esi,8)
        lea       168(%esp), %eax
        fstpt     (%esp)
        movl      $0, 12(%esp)
        movl      %eax, 16(%esp)
        call      __libm_reduce_pi04l
..B1.87:
        movl      %eax, %edx
        addl      $32, %esp
..B1.62:
        fldl      136(%esp)
        incl      %edx
        fldl      _TWO_52H@GOTOFF(%ebx)
        movl      %edx, %eax
        fmul      %st(1), %st
        fld       %st(0)
        shrl      $2, %eax
        fadd      %st(2), %st
        xorl      %eax, %esi
        fsubp     %st, %st(1)
        fld       %st(0)
        andl      $1, %esi
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddl     144(%esp)
        fadd      %st, %st(1)
..B1.63:
        fld       %st(2)
        testb     $2, %dl
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fstpt     56(%esp)
        fldt      56(%esp)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     72(%esp)
        fldt      72(%esp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     88(%esp)
        fldt      88(%esp)
        fxch      %st(1)
        fstpt     104(%esp)
        fldt      104(%esp)
        fmul      %st(0), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      _TWO_53H@GOTOFF(%ebx)
        fstpt     40(%esp)
        fldl      _ones@GOTOFF(%ebx,%esi,8)
        fstpl     16(%esp)
        je        ..B1.65
..B1.64:
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(2)
        fldt      96+_CP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      84+_CP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      72+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      60+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      88(%esp)
        fldt      56(%esp)
        fldt      104(%esp)
        fldt      40(%esp)
        fldt      120+_CP@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(5)
        fldt      108+_CP@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      _ones@GOTOFF(%ebx)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fldl      16(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     120(%esp)
        jmp       ..B1.66
..B1.65:
        fldt      96+_SP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      84+_SP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      72+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      60+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      88(%esp)
        fldt      120+_SP@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        fldt      108+_SP@GOTOFF(%ebx)
        fldt      56(%esp)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fldt      104(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(3)
        fldt      40(%esp)
        fmul      %st(2), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fldl      16(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     120(%esp)
..B1.66:
        testl     %edi, %edi
        je        ..B1.89
..B1.67:
        fstpt     24(%esp)
..B1.88:
        fldcw     14(%esp)
        jmp       ..B1.68
..B1.89:
        fstp      %st(0)
..B1.68:
        movl      152(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.70
..B1.69:
        fldt      120(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.70:
        call      __stack_chk_fail@PLT
..B1.71:
        cmpl      $0, 12(%ebp)
        jne       ..B1.73
..B1.72:
        cmpl      $0, 8(%ebp)
        je        ..B1.74
..B1.73:
        fldt      _small_value_80@GOTOFF(%ebx)
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st(1), %st
        movzbl    17(%ebp), %edx
        fsubp     %st, %st(1)
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
..B1.74:
        fldt      8(%ebp)
        fstpt     16(%esp)
        jmp       ..B1.13
..B1.75:
        xorl      %esi, %esi
        jmp       ..B1.11
..B1.77:
        xorl      %eax, %eax
        jmp       ..B1.21
..B1.79:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.81:
        xorl      %eax, %eax
        jmp       ..B1.37
..B1.84:
        xorl      %edi, %edi
        jmp       ..B1.45
        .align    16,0x90
	.type	sinl,@function
	.size	sinl,.-sinl
	.data
# -- End  sinl
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
_SP3:
	.word	17476
	.word	43656
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.type	_SP3,@object
	.size	_SP3,12
	.space 4, 0x00 	# pad
	.align 16
_SP2:
	.word	43210
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	1587
	.word	57422
	.word	34932
	.word	34952
	.word	16376
	.word	0
	.type	_SP2,@object
	.size	_SP2,24
	.space 8, 0x00 	# pad
	.align 16
_SP1:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	12518
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	43088
	.word	7396
	.word	207
	.word	53261
	.word	49138
	.word	0
	.word	55827
	.word	48618
	.word	3602
	.word	47342
	.word	16364
	.word	0
	.type	_SP1,@object
	.size	_SP1,48
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
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.space 8, 0x00 	# pad
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
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.space 8, 0x00 	# pad
	.align 16
_CP:
	.word	51631
	.word	16464
	.word	16497
	.word	44012
	.word	16306
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16372
	.word	0
	.word	46557
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	28380
	.word	3328
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	23816
	.word	50302
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	49866
	.word	64527
	.word	51070
	.word	36726
	.word	16354
	.word	0
	.word	65056
	.word	3638
	.word	41889
	.word	51659
	.word	49114
	.word	0
	.word	13834
	.word	11317
	.word	33607
	.word	55101
	.word	16338
	.word	0
	.word	40709
	.word	42374
	.word	64411
	.word	45709
	.word	49098
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
	.word	43008
	.word	16378
	.word	0
	.type	_CP,@object
	.size	_CP,132
	.space 12, 0x00 	# pad
	.align 16
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	34948
	.word	34952
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	53243
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	30577
	.word	46649
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	11760
	.word	43464
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	47000
	.word	43232
	.word	12444
	.word	45202
	.word	16350
	.word	0
	.word	52983
	.word	47009
	.word	40440
	.word	55103
	.word	49110
	.word	0
	.word	35433
	.word	14927
	.word	40941
	.word	51860
	.word	16334
	.word	0
	.word	1340
	.word	16361
	.word	50747
	.word	38523
	.word	49094
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
	.word	34816
	.word	16376
	.word	0
	.type	_SP,@object
	.size	_SP,132
	.space 12, 0x00 	# pad
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
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
