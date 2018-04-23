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
	.file "cosl.c"
	.text
..TXTST0:
# -- Begin  cosl
	.text
       .align    16,0x90
	.globl cosl
cosl:
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
        movl      %eax, 136(%esp)
..B1.2:
        fnstcw    46(%esp)
..B1.3:
        movzwl    16(%ebp), %edx
        andl      $32767, %edx
        movzwl    46(%esp), %eax
        cmpl      $16378, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jge       ..B1.47
..B1.4:
        cmpl      $16372, %edx
        jge       ..B1.39
..B1.5:
        cmpl      $16364, %edx
        jge       ..B1.31
..B1.6:
        cmpl      $16308, %edx
        jge       ..B1.23
..B1.7:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.77
..B1.8:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.9:
        fldcw     44(%esp)
..B1.10:
        movzwl    16(%ebp), %edx
        movl      $1, %eax
        andl      $32767, %edx
..B1.11:
        testl     %edx, %edx
        jne       ..B1.17
..B1.12:
        cmpl      $0, 12(%ebp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, 8(%ebp)
        je        ..B1.18
..B1.91:
        cmpl      $0, 12(%ebp)
..B1.14:
        jne       ..B1.16
..B1.15:
        cmpl      $0, 8(%ebp)
        je        ..B1.17
..B1.16:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     (%esp)
..B1.17:
        fldt      _small_value_80@GOTOFF(%ebx)
        fsubrl    _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
        jmp       ..B1.19
..B1.18:
        fldl      _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
..B1.19:
        testl     %eax, %eax
        je        ..B1.21
..B1.20:
        fldcw     46(%esp)
..B1.21:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.22:
        fldt      8(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.79
..B1.24:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.25:
        fldcw     44(%esp)
..B1.26:
        movl      $1, %eax
..B1.27:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _CP3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        faddl     _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
        je        ..B1.29
..B1.28:
        fldcw     46(%esp)
..B1.29:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.30:
        fldt      8(%esp)
        addl      $180, %esp
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
        je        ..B1.81
..B1.32:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.33:
        fldcw     44(%esp)
..B1.34:
        movl      $1, %eax
..B1.35:
        fldt      8(%ebp)
        testl     %eax, %eax
        fmul      %st(0), %st
        fldt      12+_CP2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _CP2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddl     _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
        je        ..B1.37
..B1.36:
        fldcw     46(%esp)
..B1.37:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.38:
        fldt      8(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.83
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.41:
        fldcw     44(%esp)
..B1.42:
        movl      $1, %eax
..B1.43:
        fldt      8(%ebp)
        testl     %eax, %eax
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_CP1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_CP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_CP1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _CP1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddl     _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
        je        ..B1.45
..B1.44:
        fldcw     46(%esp)
..B1.45:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.46:
        fldt      8(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.47:
        movl      %eax, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.86
..B1.48:
        orl       $-64768, %eax
        movw      %ax, 44(%esp)
..B1.49:
        fldcw     44(%esp)
..B1.50:
        movzwl    16(%ebp), %edx
        movl      $1, %esi
        andl      $32767, %edx
..B1.51:
        cmpl      $16413, %edx
        jge       ..B1.58
..B1.52:
        fldt      8(%ebp)
        fldl      _Pi4Inv@GOTOFF(%ebx)
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fxch      %st(1)
        fmull     _ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     20(%esp)
        movzwl    28(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.54
..B1.53:
        fldl      _TWO_52H@GOTOFF(%ebx)
        xorl      %eax, %eax
        fmul      %st(1), %st
        fld       %st(1)
        movl      $1, %ecx
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(1)
        fsub      %st(1), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.69
..B1.54:
        fldl      _TWO_32H@GOTOFF(%ebx)
        negl      %ecx
        addl      $30, %ecx
        movl      24(%esp), %eax
        shrl      %cl, %eax
        lea       1(%eax), %ecx
        movl      %ecx, %edi
        andl      $-2, %edi
        movl      %edi, 8(%esp)
        cmpl      $16400, %edx
        fildl     8(%esp)
        jge       ..B1.56
..B1.55:
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
        jmp       ..B1.57
..B1.56:
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
..B1.57:
        fldl      _TWO_52H@GOTOFF(%ebx)
        addl      $3, %eax
        fmul      %st(1), %st
        fld       %st(1)
        shrl      $2, %eax
        fadd      %st(1), %st
        andl      $1, %eax
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.69
..B1.58:
        cmpl      $32767, %edx
        jne       ..B1.67
..B1.59:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.62
..B1.60:
        cmpl      $0, 8(%ebp)
        jne       ..B1.62
..B1.61:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        fstpt     8(%esp)
        jmp       ..B1.63
..B1.62:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ebx)
        fstpt     8(%esp)
..B1.63:
        testl     %esi, %esi
        je        ..B1.65
..B1.64:
        fldcw     46(%esp)
..B1.65:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.66:
        fldt      8(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.67:
        fldt      8(%ebp)
        addl      $-32, %esp
        movzbl    17(%ebp), %eax
        lea       152(%esp), %edx
        andl      $128, %eax
        shrl      $7, %eax
        fmull     _ones@GOTOFF(%ebx,%eax,8)
        fstpt     (%esp)
        movl      $0, 12(%esp)
        movl      %edx, 16(%esp)
        call      __libm_reduce_pi04l
..B1.89:
        movl      %eax, %ecx
        addl      $32, %esp
..B1.68:
        fldl      120(%esp)
        lea       3(%ecx), %eax
        fldl      _TWO_52H@GOTOFF(%ebx)
        incl      %ecx
        fmul      %st(1), %st
        fld       %st(0)
        shrl      $2, %eax
        fadd      %st(2), %st
        andl      $1, %eax
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddl     128(%esp)
        fadd      %st, %st(1)
..B1.69:
        fldl      _TWO_53H@GOTOFF(%ebx)
        testb     $2, %cl
        fstpt     48(%esp)
        fld       %st(2)
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fstpt     80(%esp)
        fldt      80(%esp)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fstpl     112(%esp)
        je        ..B1.71
..B1.70:
        fldt      96+_SP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(6)
        fstpt     8(%esp)
        fldt      84+_SP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      72+_SP@GOTOFF(%ebx)
        faddp     %st, %st(7)
        fxch      %st(2)
        fmul      %st, %st(6)
        fldt      60+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      48+_SP@GOTOFF(%ebx)
        faddp     %st, %st(7)
        fmul      %st, %st(6)
        fldt      36+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      24+_SP@GOTOFF(%ebx)
        faddp     %st, %st(7)
        fmul      %st, %st(6)
        fldt      12+_SP@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fldt      _SP@GOTOFF(%ebx)
        faddp     %st, %st(6)
        fldt      32(%esp)
        fmulp     %st, %st(6)
        fxch      %st(1)
        faddp     %st, %st(5)
        fldt      64(%esp)
        fldt      80(%esp)
        fldt      120+_SP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(7)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      108+_SP@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
        fadd      %st(4), %st
        fld       %st(2)
        fldt      48(%esp)
        fmul      %st(2), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      8(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fldl      112(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        jmp       ..B1.72
..B1.71:
        fstp      %st(3)
        fstp      %st(3)
        fstp      %st(3)
        fldt      96+_CP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      84+_CP@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      72+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      60+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      36+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      12+_CP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      _CP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      64(%esp)
        fldt      80(%esp)
        fldt      120+_CP@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      108+_CP@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fldt      48(%esp)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fldl      112(%esp)
        fldl      _ones@GOTOFF(%ebx)
        fadd      %st, %st(2)
        fsub      %st(2), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
..B1.72:
        testl     %esi, %esi
        je        ..B1.92
..B1.73:
        fstpt     20(%esp)
..B1.90:
        fldcw     46(%esp)
        jmp       ..B1.74
..B1.92:
        fstp      %st(0)
..B1.74:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.76
..B1.75:
        fldt      96(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.76:
        call      __stack_chk_fail@PLT
..B1.77:
        xorl      %eax, %eax
        jmp       ..B1.11
..B1.79:
        xorl      %eax, %eax
        jmp       ..B1.27
..B1.81:
        xorl      %eax, %eax
        jmp       ..B1.35
..B1.83:
        xorl      %eax, %eax
        jmp       ..B1.43
..B1.86:
        xorl      %esi, %esi
        jmp       ..B1.51
        .align    16,0x90
	.type	cosl,@function
	.size	cosl,.-cosl
	.data
# -- End  cosl
	.section .rodata, "a"
	.align 16
	.align 16
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
_CP3:
	.word	0
	.word	65450
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.type	_CP3,@object
	.size	_CP3,12
	.space 4, 0x00 	# pad
	.align 16
_CP2:
	.word	63855
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	18238
	.word	17476
	.word	43656
	.word	43690
	.word	16378
	.word	0
	.type	_CP2,@object
	.size	_CP2,24
	.space 8, 0x00 	# pad
	.align 16
_CP1:
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	47533
	.word	43689
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	14131
	.word	49466
	.word	24756
	.word	46603
	.word	49141
	.word	0
	.word	37142
	.word	18013
	.word	35855
	.word	53259
	.word	16367
	.word	0
	.type	_CP1,@object
	.size	_CP1,48
	.align 16
_Pi4Inv:
	.long	1841940611
	.long	1072979760
	.type	_Pi4Inv,@object
	.size	_Pi4Inv,8
	.space 8, 0x00 	# pad
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
	.data
	.hidden __libm_reduce_pi04l
	.section .note.GNU-stack, ""
# End
