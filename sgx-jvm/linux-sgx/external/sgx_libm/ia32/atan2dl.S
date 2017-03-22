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
	.file "atan2dl.c"
	.text
..TXTST0:
# -- Begin  atand2l
	.text
       .align    16,0x90
	.globl atand2l
atand2l:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %ebx
        subl      $60, %esp
        fldt      8(%ebp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fstpt     (%esp)
        fldt      20(%ebp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B1.2:
        addl      $60, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	atand2l,@function
	.size	atand2l,.-atand2l
	.data
# -- End  atand2l
	.text
# -- Begin  atan2dl
	.text
       .align    16,0x90
	.globl atan2dl
atan2dl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $68, %esp
..B2.2:
        fnstcw    62(%esp)
..B2.3:
        movzbl    29(%ebp), %ecx
        movzbl    17(%ebp), %esi
        andl      $128, %ecx
        movzwl    16(%ebp), %edi
        andl      $128, %esi
        andl      $32767, %edi
        movzwl    28(%ebp), %ebx
        shrl      $7, %ecx
        andl      $32767, %ebx
        shrl      $7, %esi
        call      ..L4
..L4:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%edx), %edx
        cmpl      $32767, %edi
        je        ..B2.114
..B2.4:
        cmpl      $32767, %ebx
        je        ..B2.95
..B2.5:
        testl     %edi, %edi
        jne       ..B2.8
..B2.6:
        cmpl      $0, 12(%ebp)
        jne       ..B2.8
..B2.7:
        cmpl      $0, 8(%ebp)
        je        ..B2.13
..B2.8:
        testl     %ebx, %ebx
        jne       ..B2.29
..B2.9:
        cmpl      $0, 24(%ebp)
        jne       ..B2.29
..B2.10:
        cmpl      $0, 20(%ebp)
        jne       ..B2.29
..B2.11:
        testl     %edi, %edi
        jne       ..B2.25
..B2.12:
        cmpl      $0, 12(%ebp)
        je        ..B2.122
        jmp       ..B2.23
..B2.13:
        jne       ..B2.23
..B2.14:
        testl     %ebx, %ebx
        jne       ..B2.19
..B2.15:
        cmpl      $0, 24(%ebp)
        jne       ..B2.18
..B2.16:
        cmpl      $0, 20(%ebp)
        je        ..B2.89
..B2.17:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     40(%esp)
        jmp       ..B2.19
..B2.18:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     40(%esp)
..B2.19:
        testl     %ecx, %ecx
        je        ..B2.21
..B2.20:
        fldl      c180@GOTOFF(%edx,%esi,8)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.21:
        fldl      _zeros@GOTOFF(%edx,%esi,8)
..B2.22:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.23:
        testl     %ebx, %ebx
        jne       ..B2.29
..B2.24:
        cmpl      $0, 24(%ebp)
        jne       ..B2.29
..B2.25:
        cmpl      $0, 20(%ebp)
        jne       ..B2.29
..B2.26:
        testl     %edi, %edi
        jne       ..B2.28
..B2.27:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     40(%esp)
..B2.28:
        fldl      c90@GOTOFF(%edx,%esi,8)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.29:
        cmpl      %edi, %ebx
        jne       ..B2.35
..B2.30:
        movl      24(%ebp), %eax
        cmpl      12(%ebp), %eax
        jne       ..B2.35
..B2.31:
        movl      20(%ebp), %eax
        cmpl      8(%ebp), %eax
        jne       ..B2.35
..B2.32:
        orl       %edi, %ebx
        jne       ..B2.34
..B2.33:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     40(%esp)
..B2.34:
        lea       (%esi,%ecx,2), %eax
        fldl      exact@GOTOFF(%edx,%eax,8)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.35:
        movzwl    62(%esp), %eax
        movl      %eax, (%esp)
        andl      $3840, %eax
        cmpl      $768, %eax
        je        ..B2.94
..B2.36:
        movl      (%esp), %eax
        andl      $-3841, %eax
        orl       $-64768, %eax
        movw      %ax, 60(%esp)
..B2.37:
        fldcw     60(%esp)
..B2.38:
        movl      $1, 36(%esp)
..B2.39:
        cmpl      $8191, %ebx
        jg        ..B2.43
..B2.40:
        cmpl      $8191, %edi
        jg        ..B2.42
..B2.41:
        fldt      _TWO_8192@GOTOFF(%edx)
        fldt      20(%ebp)
        fmul      %st(1), %st
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fldt      8(%ebp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        movzwl    28(%ebp), %ebx
        movzwl    16(%ebp), %edi
        andl      $32767, %ebx
        andl      $32767, %edi
        jmp       ..B2.44
..B2.42:
        fldt      20(%ebp)
        fldt      8(%ebp)
        jmp       ..B2.47
..B2.43:
        fldt      20(%ebp)
        fldt      8(%ebp)
..B2.44:
        cmpl      $24575, %ebx
        jl        ..B2.47
..B2.45:
        cmpl      $24575, %edi
        jl        ..B2.47
..B2.46:
        fldt      12+_TWO_8192@GOTOFF(%edx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        movzwl    28(%ebp), %ebx
        movzwl    16(%ebp), %edi
        andl      $32767, %ebx
        andl      $32767, %edi
..B2.47:
        fxch      %st(1)
        fmull     _ones@GOTOFF(%edx,%ecx,8)
        fld       %st(1)
        fldl      _ones@GOTOFF(%edx,%esi,8)
        fmul      %st, %st(1)
        fxch      %st(2)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.75
..B2.48:
        lea       12(%edi), %eax
        cmpl      %eax, %ebx
        jle       ..B2.71
..B2.49:
        lea       75(%edi), %eax
        cmpl      %eax, %ebx
        jle       ..B2.68
..B2.50:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(0)
        testl     %ecx, %ecx
        je        ..B2.52
..B2.51:
        fstp      %st(0)
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %ecx
        fldt      _small_value_80@GOTOFF(%edx,%ecx)
        fsubrl    c180@GOTOFF(%edx,%esi,8)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.52:
        testl     %edi, %edi
        jle       ..B2.93
..B2.53:
        fstp      %st(0)
        xorl      %eax, %eax
..B2.54:
        fldl      _TWO_32H@GOTOFF(%edx)
        shll      $15, %ecx
        fld       %st(0)
        orl       $-49153, %ecx
        movw      %cx, 28(%ebp)
        fldt      20(%ebp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(1)
        shll      $15, %esi
        fsubr     %st(1), %st
        movzwl    16(%ebp), %edi
        orl       $-49153, %esi
        movw      %si, 16(%ebp)
        andl      $32767, %edi
        subl      %edi, %ebx
        fsubrp    %st, %st(1)
        fld       %st(1)
        cmpl      $16382, %ebx
        fsub      %st(1), %st
        fxch      %st(2)
        fstpt     24(%esp)
        fldt      24(%esp)
        fldl      _ones@GOTOFF(%edx)
        fld       %st(0)
        fdivp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fsub      %st(1), %st
        fsubrp    %st, %st(5)
        fld       %st(4)
        fsubr     %st(1), %st
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(5), %st
        fsubr     %st(2), %st
        fsubp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(4)
        fmulp     %st, %st(2)
        fldl      8+_C180byPi@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      8(%ebp)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(1)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fxch      %st(4)
        fmul      %st, %st(5)
        fld       %st(5)
        fsub      %st(1), %st
        fsubrp    %st, %st(6)
        fsub      %st(5), %st
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fld       %st(0)
        fsub      %st(4), %st
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fadd      %st, %st(4)
        fld       %st(4)
        fxch      %st(1)
        fsub      %st(5), %st
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmul      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        fchs      
        fsubrp    %st, %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fstpt     12(%esp)
        fldt      12(%esp)
        faddp     %st, %st(3)
        jl        ..B2.58
..B2.55:
        fldt      _small@GOTOFF(%edx)
        addl      $-16318, %ebx
        fmul      %st, %st(3)
        cmpl      $16382, %ebx
        fldt      48(%esp)
        fmul      %st(1), %st
        fstpt     48(%esp)
        jl        ..B2.57
..B2.56:
        fldt      48(%esp)
        addl      $-16318, %ebx
        fmul      %st(1), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fstpt     (%esp)
        fmulp     %st, %st(3)
        jmp       ..B2.59
..B2.57:
        fstp      %st(0)
        fldt      48(%esp)
        fstpt     (%esp)
        jmp       ..B2.59
..B2.58:
        fldt      48(%esp)
        fstpt     (%esp)
..B2.59:
        movl      %ebx, %ecx
        negl      %ecx
        addl      $16383, %ecx
        fstpt     12(%esp)
        testl     %eax, %eax
        movw      %cx, 20(%esp)
        je        ..B2.61
..B2.60:
        fldt      12(%esp)
        fmull     8+_two64@GOTOFF(%edx)
        fstpt     12(%esp)
        fldt      12(%esp)
        jmp       ..B2.62
..B2.61:
        fldt      12(%esp)
..B2.62:
        movzwl    8(%esp), %ecx
        andl      $32767, %ecx
        subl      %eax, %ecx
        subl      %ebx, %ecx
        cmpl      $-9, %ecx
        jl        ..B2.65
..B2.63:
        testl     %ecx, %ecx
        jg        ..B2.65
..B2.64:
        fldt      48(%esp)
        fmul      %st(1), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B2.66
..B2.65:
        fldt      48(%esp)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B2.66:
        fldl      _zeros@GOTOFF(%edx)
        fldt      48(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.123
        je        ..B2.67
..B2.123:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B2.86
..B2.67:
        fldt      24(%esp)
        fdivrp    %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.68:
        fstp      %st(3)
        testl     %ecx, %ecx
        je        ..B2.70
..B2.69:
        fldl      _TWO_32H@GOTOFF(%edx)
        fxch      %st(3)
        fdivrp    %st, %st(1)
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(3)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(6)
        fmulp     %st, %st(3)
        fldl      8+_C180byPi@GOTOFF(%edx)
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(4), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(5)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fsubrl    8200+__libm_atandl_table_128@GOTOFF(%edx)
        faddl     8192+__libm_atandl_table_128@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.70:
        fldt      36+_P@GOTOFF(%edx)
        fld       %st(1)
        fdiv      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(5)
        fstpl     24(%esp)
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(5)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fld       %st(1)
        fld       %st(1)
        fld       %st(3)
        fld       %st(3)
        fldl      _TWO_32H@GOTOFF(%edx)
        fmul      %st, %st(4)
        fxch      %st(2)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(3)
        fsub      %st(3), %st
        fld       %st(2)
        fxch      %st(5)
        fmull     8+_C180byPi@GOTOFF(%edx)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(5)
        fxch      %st(5)
        fmul      %st, %st(6)
        fxch      %st(5)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(5)
        fadd      %st(2), %st
        fld       %st(4)
        fadd      %st(6), %st
        fsubp     %st, %st(6)
        fxch      %st(4)
        fsub      %st(5), %st
        faddp     %st, %st(1)
        fxch      %st(3)
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%esp)
        fxch      %st(1)
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fsubrp    %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     24(%esp)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.71:
        fstp      %st(3)
        fldl      _TWO_63H@GOTOFF(%edx)
        fld       %st(1)
        fxch      %st(3)
        fstpl     24(%esp)
        fld       %st(3)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(3), %st
        fdiv      %st(5), %st
        fadd      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(2)
        fld       %st(4)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fld       %st(3)
        fldl      _TWO_48H@GOTOFF(%edx)
        fmul      %st, %st(6)
        movl      12(%esp), %eax
        fxch      %st(1)
        fadd      %st(6), %st
        shll      $4, %eax
        fsubp     %st, %st(6)
        testl     %ecx, %ecx
        fxch      %st(4)
        fsub      %st(5), %st
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(6)
        fxch      %st(2)
        fadd      %st(6), %st
        fld       %st(5)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(4)
        fmul      %st, %st(7)
        fxch      %st(7)
        fsubrp    %st, %st(2)
        fld       %st(3)
        fadd      %st(1), %st
        fxch      %st(3)
        fmulp     %st, %st(7)
        fxch      %st(6)
        fsubrp    %st, %st(5)
        fld       %st(2)
        fxch      %st(2)
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(5)
        fld       %st(4)
        fsubr     %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(4), %st
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st(2), %st
        fmul      %st, %st(4)
        fld       %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(4)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(3)
        fmulp     %st, %st(4)
        fldl      8+_C180byPi@GOTOFF(%edx)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldl      24(%esp)
        je        ..B2.73
..B2.72:
        negl      %eax
        fld       %st(2)
        fldl      8192+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      8200+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B2.74
..B2.73:
        fldl      __libm_atandl_table_128@GOTOFF(%eax,%edx)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     8+__libm_atandl_table_128@GOTOFF(%eax,%edx)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     48(%esp)
..B2.74:
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.75:
        fstp      %st(3)
        lea       12(%ebx), %eax
        cmpl      %eax, %edi
        jle       ..B2.82
..B2.76:
        addl      $75, %ebx
        cmpl      %ebx, %edi
        jle       ..B2.78
..B2.77:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %ecx
        fldt      _small_value_80@GOTOFF(%edx,%ecx)
        fsubrl    c90@GOTOFF(%edx,%esi,8)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.78:
        fldl      _TWO_32H@GOTOFF(%edx)
        testl     %ecx, %ecx
        fxch      %st(1)
        fdivrp    %st, %st(3)
        fmul      %st(2), %st
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(3), %st
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(5)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      8+_C180byPi@GOTOFF(%edx)
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(6), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(7)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(7)
        fxch      %st(1)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      4096+__libm_atandl_table_128@GOTOFF(%edx)
        fstpt     48(%esp)
        fldl      4104+__libm_atandl_table_128@GOTOFF(%edx)
        je        ..B2.80
..B2.79:
        fldt      48(%esp)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B2.81
..B2.80:
        fldt      48(%esp)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B2.81:
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B2.86
..B2.82:
        fldl      _TWO_63H@GOTOFF(%edx)
        fld       %st(3)
        fxch      %st(3)
        fstpl     24(%esp)
        fld       %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(5), %st
        fdiv      %st(3), %st
        fadd      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(2)
        fld       %st(4)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fld       %st(3)
        fldl      _TWO_48H@GOTOFF(%edx)
        fmul      %st, %st(6)
        movl      12(%esp), %eax
        fxch      %st(2)
        fadd      %st(6), %st
        shll      $4, %eax
        fsubp     %st, %st(6)
        fld       %st(5)
        testl     %ecx, %ecx
        fmul      %st(4), %st
        fxch      %st(6)
        fsubr     %st, %st(7)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(6)
        fmul      %st(4), %st
        fadd      %st(5), %st
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(5)
        fsubrp    %st, %st(7)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fld       %st(4)
        fxch      %st(1)
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(4)
        fmulp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fadd      %st(3), %st
        fmul      %st, %st(4)
        fld       %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(4)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _C180byPi@GOTOFF(%edx)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      8+_C180byPi@GOTOFF(%edx)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      24(%esp)
        je        ..B2.84
..B2.83:
        fldl      4096+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddl     4104+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B2.85
..B2.84:
        negl      %eax
        fld       %st(1)
        fldl      4096+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(3)
        fldl      4104+__libm_atandl_table_128@GOTOFF(%edx,%eax)
        fsubp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     48(%esp)
..B2.85:
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fstpt     48(%esp)
..B2.86:
        cmpl      $0, 36(%esp)
        je        ..B2.88
..B2.87:
        fldcw     62(%esp)
..B2.88:
        fldt      48(%esp)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.89:
        testl     %ecx, %ecx
        je        ..B2.91
..B2.90:
        fldl      c180@GOTOFF(%edx,%esi,8)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.91:
        fldl      _zeros@GOTOFF(%edx,%esi,8)
..B2.92:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.93:
        fmull     _two64@GOTOFF(%edx)
        movl      $64, %eax
        fstpt     8(%ebp)
        jmp       ..B2.54
..B2.94:
        movl      $0, 36(%esp)
        jmp       ..B2.39
..B2.95:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B2.109
..B2.96:
        cmpl      $0, 20(%ebp)
        jne       ..B2.109
..B2.97:
        testl     %edi, %edi
        jne       ..B2.104
..B2.98:
        cmpl      $0, 12(%ebp)
        jne       ..B2.100
..B2.99:
        cmpl      $0, 8(%ebp)
        je        ..B2.103
..B2.100:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     40(%esp)
..B2.101:
        cmpl      $32767, %ebx
        je        ..B2.104
..B2.102:
        fldl      c90@GOTOFF(%edx,%esi,8)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.103:
        shll      $3, %esi
        jmp       ..B2.105
..B2.104:
        shll      $3, %esi
        cmpl      $32767, %edi
        je        ..B2.110
..B2.105:
        testl     %ecx, %ecx
        je        ..B2.107
..B2.106:
        fldl      c180@GOTOFF(%esi,%edx)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.107:
        fldl      _zeros@GOTOFF(%esi,%edx)
..B2.108:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.109:
        fldt      20(%ebp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.110:
        testl     %ecx, %ecx
        je        ..B2.112
..B2.111:
        fldl      c135@GOTOFF(%edx,%esi)
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.112:
        fldl      c45@GOTOFF(%edx,%esi)
..B2.113:
        addl      $68, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.114:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B2.109
..B2.115:
        cmpl      $0, 8(%ebp)
        jne       ..B2.109
..B2.116:
        cmpl      $32767, %ebx
        je        ..B2.95
..B2.117:
        testl     %ebx, %ebx
        jne       ..B2.101
..B2.118:
        cmpl      $0, 24(%ebp)
        jne       ..B2.100
..B2.119:
        cmpl      $0, 20(%ebp)
        jne       ..B2.100
        jmp       ..B2.102
..B2.122:
        cmpl      $0, 8(%ebp)
        je        ..B2.14
        jmp       ..B2.23
        .align    16,0x90
	.type	atan2dl,@function
	.size	atan2dl,.-atan2dl
	.data
# -- End  atan2dl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x08,0x00,0x80,0x13,0x40,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 16
_TWO_8192:
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
	.type	_TWO_8192,@object
	.size	_TWO_8192,24
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
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.space 8, 0x00 	# pad
	.align 16
_C180byPi:
	.long	442499072
	.long	1078765020
	.long	3272251219
	.long	1042157501
	.type	_C180byPi,@object
	.size	_C180byPi,16
	.align 16
_small:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	65
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32769
	.word	0
	.type	_small,@object
	.size	_small,24
	.space 8, 0x00 	# pad
	.align 16
_two64:
	.long	0
	.long	1139802112
	.long	0
	.long	1005584384
	.type	_two64,@object
	.size	_two64,16
	.align 16
_P:
	.word	54228
	.word	26975
	.word	60215
	.word	39113
	.word	49155
	.word	0
	.word	30637
	.word	6142
	.word	46044
	.word	46936
	.word	16386
	.word	0
	.word	47408
	.word	26349
	.word	14153
	.word	33526
	.word	49154
	.word	0
	.word	53934
	.word	32714
	.word	35699
	.word	52145
	.word	16385
	.word	0
	.type	_P,@object
	.size	_P,48
	.align 16
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.space 8, 0x00 	# pad
	.align 16
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 8
c180:
	.long	0x00000000,0x40668000
	.long	0x00000000,0xc0668000
	.type	c180,@object
	.size	c180,16
	.align 8
c90:
	.long	0x00000000,0x40568000
	.long	0x00000000,0xc0568000
	.type	c90,@object
	.size	c90,16
	.align 8
exact:
	.long	0x00000000,0x40468000
	.long	0x00000000,0xc0468000
	.long	0x00000000,0x4060e000
	.long	0x00000000,0xc060e000
	.type	exact,@object
	.size	exact,32
	.align 8
c135:
	.long	0x00000000,0x4060e000
	.long	0x00000000,0xc060e000
	.type	c135,@object
	.size	c135,16
	.align 8
c45:
	.long	0x00000000,0x40468000
	.long	0x00000000,0xc0468000
	.type	c45,@object
	.size	c45,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.data
	.hidden __libm_atandl_table_128
	.section .note.GNU-stack, ""
# End
