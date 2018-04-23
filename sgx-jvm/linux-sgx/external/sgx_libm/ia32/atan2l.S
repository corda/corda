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
	.file "atan2l.c"
	.text
..TXTST0:
# -- Begin  atan2l
	.text
       .align    16,0x90
	.globl atan2l
atan2l:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
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
        fnstcw    30(%esp)
..B1.3:
        movzbl    29(%ebp), %edi
        movzbl    17(%ebp), %ebx
        andl      $128, %edi
        movzwl    16(%ebp), %ecx
        andl      $128, %ebx
        andl      $32767, %ecx
        movzwl    28(%ebp), %esi
        shrl      $7, %edi
        andl      $32767, %esi
        shrl      $7, %ebx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $32767, %ecx
        je        ..B1.90
..B1.4:
        cmpl      $32767, %esi
        je        ..B1.90
..B1.5:
        testl     %ecx, %ecx
        jne       ..B1.8
..B1.6:
        cmpl      $0, 12(%ebp)
        jne       ..B1.8
..B1.7:
        cmpl      $0, 8(%ebp)
        je        ..B1.11
..B1.8:
        testl     %esi, %esi
        jne       ..B1.32
..B1.9:
        cmpl      $0, 24(%ebp)
        jne       ..B1.32
..B1.10:
        cmpl      $0, 20(%ebp)
        jne       ..B1.32
..B1.11:
        movzwl    30(%esp), %eax
        movl      %eax, 12(%esp)
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.88
..B1.12:
        movl      12(%esp), %eax
        orl       $-64768, %eax
        movw      %ax, 28(%esp)
..B1.13:
        fldcw     28(%esp)
..B1.14:
        movl      $1, %eax
..B1.15:
        testl     %ecx, %ecx
        jne       ..B1.28
..B1.16:
        cmpl      $0, 12(%ebp)
        jne       ..B1.27
..B1.17:
        cmpl      $0, 8(%ebp)
        jne       ..B1.26
..B1.18:
        testl     %esi, %esi
        jne       ..B1.23
..B1.19:
        cmpl      $0, 24(%ebp)
        jne       ..B1.22
..B1.20:
        cmpl      $0, 20(%ebp)
        je        ..B1.85
..B1.21:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     56(%esp)
        jmp       ..B1.23
..B1.22:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     56(%esp)
..B1.23:
        testl     %edi, %edi
        je        ..B1.25
..B1.24:
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ebx
        fldt      _pi_00l@GOTOFF(%ebx,%edx)
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.29
..B1.25:
        fldl      _zeros@GOTOFF(%edx,%ebx,8)
        fstpt     64(%esp)
        jmp       ..B1.29
..B1.26:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     56(%esp)
        jmp       ..B1.28
..B1.27:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     56(%esp)
..B1.28:
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ebx
        fldt      _pi_02l@GOTOFF(%ebx,%edx)
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
..B1.29:
        testl     %eax, %eax
        je        ..B1.31
..B1.30:
        fldcw     30(%esp)
..B1.31:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movzwl    30(%esp), %eax
        movl      %eax, 12(%esp)
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.89
..B1.33:
        movl      12(%esp), %eax
        orl       $-64768, %eax
        movw      %ax, 28(%esp)
..B1.34:
        fldcw     28(%esp)
..B1.35:
        movl      $1, 12(%esp)
..B1.36:
        cmpl      $8191, %esi
        jg        ..B1.40
..B1.37:
        cmpl      $8191, %ecx
        jg        ..B1.39
..B1.38:
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
        movzwl    28(%ebp), %esi
        movzwl    16(%ebp), %ecx
        andl      $32767, %esi
        andl      $32767, %ecx
        jmp       ..B1.41
..B1.39:
        fldt      20(%ebp)
        fldt      8(%ebp)
        jmp       ..B1.44
..B1.40:
        fldt      20(%ebp)
        fldt      8(%ebp)
..B1.41:
        cmpl      $24575, %esi
        jl        ..B1.44
..B1.42:
        cmpl      $24575, %ecx
        jl        ..B1.44
..B1.43:
        fldt      12+_TWO_8192@GOTOFF(%edx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        movzwl    28(%ebp), %esi
        movzwl    16(%ebp), %ecx
        andl      $32767, %esi
        andl      $32767, %ecx
..B1.44:
        fldl      _ones@GOTOFF(%edx,%ebx,8)
        fld       %st(2)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(1)
        fmull     _ones@GOTOFF(%edx,%edi,8)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.68
..B1.45:
        lea       12(%ecx), %eax
        cmpl      %eax, %esi
        jle       ..B1.62
..B1.46:
        addl      $75, %ecx
        cmpl      %ecx, %esi
        jle       ..B1.55
..B1.47:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        testl     %edi, %edi
        je        ..B1.49
..B1.48:
        fstp      %st(1)
        fstp      %st(0)
        lea       (,%ebx,8), %eax
        lea       (%eax,%ebx,4), %ecx
        fldt      _pi_00l@GOTOFF(%ecx,%edx)
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.52
..B1.49:
        fdivp     %st, %st(1)
        fstpt     64(%esp)
        movzwl    72(%esp), %eax
        testl     $32767, %eax
        jne       ..B1.51
..B1.50:
        fldt      _small_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.52
..B1.51:
        fldt      _small_value_80@GOTOFF(%edx)
        faddl     _ones@GOTOFF(%edx)
        fstpt     (%esp)
..B1.52:
        cmpl      $0, 12(%esp)
        je        ..B1.54
..B1.53:
        fldcw     30(%esp)
..B1.54:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.55:
        fstp      %st(4)
        fstp      %st(2)
        testl     %edi, %edi
        je        ..B1.59
..B1.56:
        fldt      36+_P@GOTOFF(%edx)
        fxch      %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      12+_P@GOTOFF(%edx)
        cmpl      $0, 12(%esp)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        faddp     %st, %st(2)
        fxch      %st(1)
        fsubrl    8200+__libm_atanl_table_128@GOTOFF(%edx)
        faddl     8192+__libm_atanl_table_128@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstpt     64(%esp)
        je        ..B1.58
..B1.57:
        fldcw     30(%esp)
..B1.58:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.59:
        fldt      36+_P@GOTOFF(%edx)
        fld       %st(2)
        fdiv      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(3)
        fstpl     48(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        fmul      %st, %st(2)
        fldt      12+_P@GOTOFF(%edx)
        cmpl      $0, 12(%esp)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fld       %st(4)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     32(%esp)
        fld       %st(2)
        fld       %st(3)
        fld       %st(3)
        fldl      _TWO_32H@GOTOFF(%edx)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fld       %st(5)
        fadd      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fld       %st(3)
        fxch      %st(2)
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st, %st(4)
        fld       %st(5)
        fmul      %st(1), %st
        fxch      %st(2)
        fadd      %st, %st(6)
        fmulp     %st, %st(1)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%esp)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fmull     48(%esp)
        fstpt     64(%esp)
        je        ..B1.61
..B1.60:
        fldt      16(%esp)
        fstpt     32(%esp)
..B1.123:
        fldcw     30(%esp)
..B1.61:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.62:
        fstp      %st(4)
        fstp      %st(2)
        fldl      _TWO_63H@GOTOFF(%edx)
        fld       %st(2)
        fxch      %st(2)
        fstpl     48(%esp)
        fld       %st(2)
        fld       %st(4)
        fld       %st(5)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(6), %st
        fdiv      %st(7), %st
        fadd      %st(4), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(4)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(4)
        fldl      _TWO_48H@GOTOFF(%edx)
        fmul      %st, %st(3)
        movl      32(%esp), %eax
        fxch      %st(3)
        fadd      %st, %st(5)
        shll      $4, %eax
        fsubrp    %st, %st(5)
        fld       %st(4)
        testl     %edi, %edi
        fmul      %st(4), %st
        fxch      %st(5)
        fsubr     %st, %st(6)
        fxch      %st(2)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(6)
        fld       %st(5)
        fmul      %st(4), %st
        fadd      %st(7), %st
        fxch      %st(4)
        fmul      %st, %st(7)
        fxch      %st(7)
        fsubrp    %st, %st(6)
        fmul      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(4)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(4)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(6)
        fadd      %st(7), %st
        fxch      %st(7)
        fmulp     %st, %st(1)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
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
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      48(%esp)
        je        ..B1.64
..B1.63:
        negl      %eax
        fld       %st(2)
        fldl      8192+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      8200+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B1.65
..B1.64:
        fldl      __libm_atanl_table_128@GOTOFF(%eax,%edx)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fldl      8+__libm_atanl_table_128@GOTOFF(%eax,%edx)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
..B1.65:
        fstpt     64(%esp)
        cmpl      $0, 12(%esp)
        je        ..B1.67
..B1.66:
        fldt      16(%esp)
        fstpt     32(%esp)
..B1.124:
        fldcw     30(%esp)
..B1.67:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.68:
        fstp      %st(4)
        fstp      %st(2)
        lea       12(%esi), %eax
        cmpl      %eax, %ecx
        jle       ..B1.79
..B1.69:
        addl      $75, %esi
        cmpl      %esi, %ecx
        jle       ..B1.73
..B1.70:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        cmpl      $0, 12(%esp)
        lea       (,%ebx,8), %eax
        lea       (%eax,%ebx,4), %ecx
        fldt      _pi_02l@GOTOFF(%ecx,%edx)
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        je        ..B1.72
..B1.71:
        fldcw     30(%esp)
..B1.72:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.73:
        fldt      12+_P@GOTOFF(%edx)
        testl     %edi, %edi
        fxch      %st(2)
        fdivrp    %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(4)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        fldt      24+_P@GOTOFF(%edx)
        fmulp     %st, %st(5)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldl      4096+__libm_atanl_table_128@GOTOFF(%edx)
        fldl      4104+__libm_atanl_table_128@GOTOFF(%edx)
        je        ..B1.75
..B1.74:
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B1.76
..B1.75:
        fsubp     %st, %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
..B1.76:
        fstpt     64(%esp)
        cmpl      $0, 12(%esp)
        je        ..B1.78
..B1.77:
        fldcw     30(%esp)
..B1.78:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.79:
        fldl      _TWO_63H@GOTOFF(%edx)
        fld       %st(3)
        fxch      %st(2)
        fstpl     48(%esp)
        fld       %st(2)
        fld       %st(3)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(6), %st
        fdiv      %st(5), %st
        fadd      %st(3), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(3)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(3)
        fld       %st(5)
        fldl      _TWO_48H@GOTOFF(%edx)
        fmul      %st, %st(5)
        movl      32(%esp), %eax
        fxch      %st(1)
        fadd      %st(5), %st
        shll      $4, %eax
        fsubp     %st, %st(5)
        testl     %edi, %edi
        fxch      %st(4)
        fsubr     %st, %st(6)
        fld       %st(6)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(5)
        fxch      %st(2)
        fadd      %st(5), %st
        fld       %st(1)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(4)
        fmul      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(7)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fsubp     %st, %st(4)
        fld       %st(1)
        fadd      %st(1), %st
        fdivrl    _ones@GOTOFF(%edx)
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(7), %st
        fxch      %st(6)
        fadd      %st, %st(7)
        fmulp     %st, %st(5)
        faddl     8+_ones@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
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
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      48(%esp)
        je        ..B1.81
..B1.80:
        fldl      4096+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fldl      4104+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B1.82
..B1.81:
        negl      %eax
        fld       %st(2)
        fldl      4096+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      4104+__libm_atanl_table_128@GOTOFF(%edx,%eax)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
..B1.82:
        fstpt     64(%esp)
        cmpl      $0, 12(%esp)
        je        ..B1.84
..B1.83:
        fldt      16(%esp)
        fstpt     32(%esp)
..B1.125:
        fldcw     30(%esp)
..B1.84:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.85:
        testl     %edi, %edi
        je        ..B1.87
..B1.86:
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ebx
        fldt      _pi_00l@GOTOFF(%ebx,%edx)
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.29
..B1.87:
        fldl      _zeros@GOTOFF(%edx,%ebx,8)
        fstpt     64(%esp)
        jmp       ..B1.29
..B1.88:
        xorl      %eax, %eax
        jmp       ..B1.15
..B1.89:
        movl      $0, 12(%esp)
        jmp       ..B1.36
..B1.90:
        movzwl    30(%esp), %eax
        movl      %eax, 12(%esp)
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.120
..B1.91:
        movl      12(%esp), %eax
        orl       $-64768, %eax
        movw      %ax, 28(%esp)
..B1.92:
        fldcw     28(%esp)
..B1.93:
        movl      $1, %eax
..B1.94:
        cmpl      $32767, %ecx
        je        ..B1.118
..B1.95:
        cmpl      $32767, %esi
        je        ..B1.115
..B1.96:
        testl     %ecx, %ecx
        jne       ..B1.99
..B1.97:
        cmpl      $0, 12(%ebp)
        jne       ..B1.102
..B1.98:
        cmpl      $0, 8(%ebp)
        jne       ..B1.102
..B1.99:
        testl     %esi, %esi
        jne       ..B1.103
..B1.100:
        cmpl      $0, 24(%ebp)
        jne       ..B1.102
..B1.101:
        cmpl      $0, 20(%ebp)
        je        ..B1.104
..B1.102:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     56(%esp)
..B1.103:
        cmpl      $32767, %esi
        je        ..B1.108
..B1.104:
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ebx
        fldt      _pi_02l@GOTOFF(%ebx,%edx)
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
..B1.105:
        testl     %eax, %eax
        je        ..B1.107
..B1.106:
        fldcw     30(%esp)
..B1.107:
        fldt      64(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.108:
        cmpl      $32767, %ecx
        je        ..B1.112
..B1.109:
        testl     %edi, %edi
        je        ..B1.111
..B1.110:
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ebx
        fldt      _pi_00l@GOTOFF(%ebx,%edx)
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.105
..B1.111:
        fldl      _zeros@GOTOFF(%edx,%ebx,8)
        fstpt     64(%esp)
        jmp       ..B1.105
..B1.112:
        testl     %edi, %edi
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ecx
        je        ..B1.114
..B1.113:
        fldt      _pi_34l@GOTOFF(%ecx,%edx)
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.105
..B1.114:
        fldt      _pi_04l@GOTOFF(%ecx,%edx)
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.105
..B1.115:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.117
..B1.116:
        cmpl      $0, 20(%ebp)
        je        ..B1.96
..B1.117:
        fldt      20(%ebp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B1.105
..B1.118:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.117
..B1.119:
        cmpl      $0, 8(%ebp)
        jne       ..B1.117
        jmp       ..B1.95
..B1.120:
        xorl      %eax, %eax
        jmp       ..B1.94
        .align    16,0x90
	.type	atan2l,@function
	.size	atan2l,.-atan2l
	.data
# -- End  atan2l
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
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 2
_pi_00l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16384
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49152
	.word	0
	.type	_pi_00l,@object
	.size	_pi_00l,24
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
_pi_02l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49151
	.word	0
	.type	_pi_02l,@object
	.size	_pi_02l,24
	.align 2
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
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.type	_P,@object
	.size	_P,48
	.align 2
_pi_34l:
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	16384
	.word	0
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	49152
	.word	0
	.type	_pi_34l,@object
	.size	_pi_34l,24
	.align 2
_pi_04l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16382
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49150
	.word	0
	.type	_pi_04l,@object
	.size	_pi_04l,24
	.data
	.hidden __libm_atanl_table_128
	.section .note.GNU-stack, ""
# End
