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
	.file "powl.c"
	.text
..TXTST0:
# -- Begin  powl
	.text
       .align    16,0x90
	.globl powl
powl:
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
        subl      $148, %esp
..B1.2:
        fnstcw    70(%esp)
..B1.3:
        movzbl    17(%ebp), %ecx
        shrl      $7, %ecx
        movzwl    16(%ebp), %eax
        shll      $15, %ecx
        andl      $32767, %eax
        orl       %eax, %ecx
        movl      12(%ebp), %ebx
        shll      $16, %ecx
        shrl      $16, %ebx
        movl      %eax, 44(%esp)
        orl       %ebx, %ecx
        movzbl    29(%ebp), %eax
        movl      %ecx, %edi
        shrl      $7, %eax
        movl      %ecx, %edx
        movzwl    28(%ebp), %esi
        andl      $2147483647, %edx
        shll      $15, %eax
        andl      $32767, %esi
        movl      24(%ebp), %ebx
        orl       %esi, %eax
        shrl      $31, %edi
        cmpl      $1073709056, %edx
        movl      %edi, 28(%esp)
        movl      %ebx, %edi
        movl      %edx, 12(%esp)
        movl      $0, %edx
        movl      %esi, 48(%esp)
        setb      %dl
        shll      $16, %eax
        shrl      $16, %edi
        orl       %edi, %eax
        movl      %eax, %esi
        andl      $2147483647, %eax
        shrl      $31, %esi
        xorl      %esi, %edx
        movl      %edx, 124(%esp)
        movl      %eax, %edx
        movl      8(%ebp), %edi
        orl       %ebx, %edx
        movl      %edi, 32(%esp)
        movl      20(%ebp), %edi
        orl       %edi, %edx
        movl      %esi, 56(%esp)
        movl      %edx, 40(%esp)
        movl      %ebx, 60(%esp)
        movl      %edi, 36(%esp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      12(%ebp), %edx
        movl      12(%esp), %esi
        je        ..B1.5
..B1.4:
        addl      $-1073709056, %ecx
        lea       -2147483648(%edx), %edi
        orl       %edi, %ecx
        movl      %edi, 24(%esp)
        orl       32(%esp), %ecx
        jne       ..B1.10
..B1.5:
        cmpl      $2147450880, %eax
        jae       ..B1.8
..B1.6:
        cmpl      $2147450880, %esi
        jb        ..B1.9
..B1.7:
        addl      $-2147483648, %edx
        orl       32(%esp), %edx
        je        ..B1.9
..B1.8:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     136(%esp)
..B1.9:
        fldl      _ones@GOTOFF(%ebx)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.10:
        lea       -1073709056(%eax), %ecx
        movl      %ecx, 20(%esp)
        cmpl      $4194304, %ecx
        jae       ..B1.17
..B1.11:
        movl      %eax, %ecx
        shrl      $16, %ecx
        movl      %ecx, 52(%esp)
        cmpl      $1075806208, %eax
        ja        ..B1.13
..B1.12:
        movl      %ebx, 12(%esp)
        movl      %ecx, %ebx
        movl      %ebx, %ecx
        negl      %ecx
        addl      $30, %ecx
        movl      60(%esp), %edi
        shrl      %cl, %edi
        lea       2(%ebx), %ecx
        movl      %edi, 16(%esp)
        movl      60(%esp), %edi
        shll      %cl, %edi
        movl      36(%esp), %ebx
        orl       %ebx, %edi
        cmpl      $1075740672, %eax
        jbe       ..L3
        movl      %ebx, %edi
..L3:
        movl      12(%esp), %ebx
        movl      %edi, 40(%esp)
        jmp       ..B1.14
..B1.13:
        movl      %ebx, 12(%esp)
        movl      %ecx, %ebx
        movl      %ebx, %ecx
        negl      %ecx
        addl      $30, %ecx
        movl      36(%esp), %edi
        shrl      %cl, %edi
        lea       2(%ebx), %ecx
        movl      36(%esp), %ebx
        shll      %cl, %ebx
        cmpl      $1077837824, %eax
        movl      %edi, 16(%esp)
        jb        ..L4
        movl      $0, %ebx
..L4:
        movl      %ebx, 40(%esp)
        movl      12(%esp), %ebx
..B1.14:
        testb     $1, 16(%esp)
        je        ..B1.16
..B1.15:
        movl      28(%esp), %ecx
        movl      %ecx, 108(%esp)
        movl      $1, 12(%esp)
        jmp       ..B1.18
..B1.16:
        xorl      %ecx, %ecx
        movl      %ecx, 12(%esp)
        movl      %ecx, 108(%esp)
        jmp       ..B1.18
..B1.17:
        cmpl      $1073709056, %eax
        movl      40(%esp), %edi
        jb        ..L5
        movl      $0, %edi
..L5:
        xorl      %ecx, %ecx
        movl      %ecx, 12(%esp)
        movl      %ecx, 108(%esp)
        movl      %edi, 40(%esp)
..B1.18:
        cmpl      $32767, 44(%esp)
        je        ..B1.189
..B1.19:
        cmpl      $32767, 48(%esp)
        je        ..B1.156
..B1.20:
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        orl       32(%esp), %ecx
        jne       ..B1.26
..B1.21:
        movl      %edx, %ecx
        orl       %esi, %ecx
        jne       ..B1.26
..B1.22:
        xorl      %edx, %edx
        cmpl      $0, 40(%esp)
        sete      %dl
        andl      %edx, 108(%esp)
        cmpl      $0, 56(%esp)
        jne       ..B1.193
..B1.23:
        cmpl      $65536, %eax
        jae       ..B1.25
..B1.24:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     128(%esp)
..B1.25:
        movl      108(%esp), %eax
        fldl      _zeros@GOTOFF(%ebx,%eax,8)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        cmpl      $0, 28(%esp)
        je        ..B1.31
..B1.27:
        cmpl      $1077837824, %eax
        jae       ..B1.29
..B1.28:
        cmpl      $0, 40(%esp)
        jne       ..B1.192
..B1.29:
        lea       -1073709056(%esi), %ecx
        orl       24(%esp), %ecx
        orl       32(%esp), %ecx
        jne       ..B1.31
..B1.30:
        movl      12(%esp), %eax
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        lea       -65536(%esi), %edi
        lea       -65536(%eax), %ecx
        orl       %ecx, %edi
        testl     $-2147483648, %edi
        je        ..B1.33
..B1.32:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     128(%esp)
..B1.33:
        movl      60(%esp), %ecx
        andl      $2147483647, %ecx
        orl       36(%esp), %ecx
        jne       ..B1.61
..B1.34:
        cmpl      $1073709056, %eax
        jne       ..B1.43
..B1.35:
        cmpl      $0, 56(%esp)
        je        ..B1.42
..B1.36:
        movzwl    70(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.141
..B1.37:
        orl       $-64768, %edx
        movw      %dx, 68(%esp)
..B1.38:
        fldcw     68(%esp)
..B1.39:
        fldt      8(%ebp)
        fdivrl    _ones@GOTOFF(%ebx)
        fstpt     24(%esp)
..B1.40:
        fldcw     70(%esp)
..B1.41:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.42:
        fldt      8(%ebp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        cmpl      $1073643520, %eax
        jne       ..B1.61
..B1.44:
        movzwl    70(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.142
..B1.45:
        orl       $-64768, %edx
        movw      %dx, 68(%esp)
..B1.46:
        fldcw     68(%esp)
..B1.47:
        movl      $1, %esi
..B1.48:
        cmpl      $0, 56(%esp)
        je        ..B1.56
..B1.49:
        fldl      _ones@GOTOFF(%ebx)
        movzbl    17(%ebp), %edi
        fld       %st(0)
        shrl      $7, %edi
        movzwl    16(%ebp), %eax
        shll      $15, %edi
        andl      $32767, %eax
        orl       %eax, %edi
        shll      $16, %edi
        movzwl    14(%ebp), %edx
        orl       %edx, %edi
        lea       -536838144(%edi), %ecx
        cmpl      $1073741824, %ecx
        jbe       ..B1.53
..B1.50:
        fstp      %st(0)
        cmpl      $536838144, %edi
        jae       ..B1.52
..B1.51:
        fldt      8(%ebp)
        fldt      _invsqrtl_scales@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      12+_invsqrtl_scales@GOTOFF(%ebx)
        fldt      8(%ebp)
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.54
..B1.52:
        fldt      8(%ebp)
        fldt      24+_invsqrtl_scales@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      36+_invsqrtl_scales@GOTOFF(%ebx)
        fldt      8(%ebp)
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B1.54
..B1.53:
        fldt      8(%ebp)
        fld       %st(0)
..B1.54:
        fldl      _TWO_43H@GOTOFF(%ebx)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(3)
        fdivr     %st, %st(5)
        fxch      %st(5)
        fstpt     12(%esp)
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     (%esp)
        fldt      (%esp)
        addl      $-16, %esp
        fsubp     %st, %st(4)
        fsub      %st(3), %st
        lea       28(%esp), %eax
        movl      %eax, (%esp)
        fstpt     28(%eax)
        fstpt     12(%eax)
        fxch      %st(1)
        fstpt     44(%eax)
        fstpt     60(%eax)
        call      __sqrtl
..B1.196:
        fldt      88(%esp)
        fldt      72(%esp)
        fldt      56(%esp)
        fldt      40(%esp)
        addl      $16, %esp
..B1.55:
        fmul      %st(4), %st
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     (%esp)
        fld       %st(4)
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(5)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      (%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(5)
        fld       %st(2)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddl     8+_ones@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     24(%esp)
        jmp       ..B1.58
..B1.56:
        addl      $-16, %esp
        lea       8(%ebp), %eax
        movl      %eax, (%esp)
        call      __sqrtl
..B1.197:
        addl      $16, %esp
..B1.57:
        fstpt     24(%esp)
..B1.58:
        testl     %esi, %esi
        je        ..B1.60
..B1.59:
        fldcw     70(%esp)
..B1.60:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.61:
        movzwl    70(%esp), %edi
        movl      %edi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.155
..B1.62:
        orl       $-64768, %edi
        movw      %di, 68(%esp)
..B1.63:
        fldcw     68(%esp)
..B1.64:
        movl      $1, 36(%esp)
..B1.65:
        fldt      8(%ebp)
        movl      28(%esp), %ecx
        cmpl      $0, 40(%esp)
        fmull     _ones@GOTOFF(%ebx,%ecx,8)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        jne       ..B1.92
..B1.66:
        cmpl      $1048576, 20(%esp)
        jae       ..B1.92
..B1.67:
        movl      %eax, %ecx
        shrl      $16, %ecx
        negl      %ecx
        addl      $30, %ecx
        movl      60(%esp), %edi
        shrl      %cl, %edi
        cmpl      $46, %edi
        movl      %edi, 60(%esp)
        lea       -2(%edi), %ecx
        movl      32(%esp), %edi
        jb        ..L6
        movl      $46, %ecx
..L6:
        andl      4+_exact_masks@GOTOFF(%ebx,%ecx,8), %edx
        andl      _exact_masks@GOTOFF(%ebx,%ecx,8), %edi
        movl      %ecx, 52(%esp)
        orl       %edi, %edx
        jne       ..B1.92
..B1.68:
        movl      108(%esp), %eax
        cmpl      $65535, %esi
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        ja        ..B1.70
..B1.69:
        fldl      _TWO_75@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     40(%esp)
        movzwl    48(%esp), %ecx
        movl      %ecx, %edx
        andl      $32767, %edx
        addl      $-16458, %edx
        jmp       ..B1.71
..B1.70:
        movzwl    16(%ebp), %edx
        fxch      %st(1)
        fstpt     40(%esp)
        andl      $32767, %edx
        movzwl    48(%esp), %ecx
        addl      $-16383, %edx
..B1.71:
        movl      56(%esp), %eax
        andl      $-32768, %ecx
        orl       $-49153, %ecx
        movw      %cx, 48(%esp)
        movl      iones@GOTOFF(%ebx,%eax,4), %eax
        imull     60(%esp), %eax
        imull     %edx, %eax
        lea       16445(%eax), %ecx
        cmpl      $32829, %ecx
        lea       16383(%eax), %edx
        jb        ..B1.78
..B1.72:
        movl      108(%esp), %ecx
        cmpl      $-63, %edx
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %eax
        jg        ..B1.74
..B1.73:
        fldt      _small_value_80@GOTOFF(%eax,%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        jmp       ..B1.75
..B1.74:
        fldt      _large_value_80@GOTOFF(%eax,%ebx)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.75:
        fstpt     24(%esp)
        cmpl      $0, 36(%esp)
        je        ..B1.200
..B1.76:
        fstpt     12(%esp)
..B1.198:
        fldcw     70(%esp)
        jmp       ..B1.77
..B1.200:
        fstp      %st(0)
..B1.77:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        fstpt     12(%esp)
        testl     %edx, %edx
        jg        ..B1.80
..B1.79:
        addl      $16458, %eax
        movzwl    20(%esp), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 20(%esp)
        fldt      12(%esp)
        fmull     8+_TWO_75@GOTOFF(%ebx)
        fstpt     12(%esp)
        jmp       ..B1.81
..B1.80:
        movzwl    20(%esp), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        orl       %edx, %eax
        movw      %ax, 20(%esp)
..B1.81:
        cmpl      $-2147483648, 44(%esp)
        jne       ..B1.87
..B1.82:
        cmpl      $0, 40(%esp)
        jne       ..B1.87
..B1.83:
        fldt      12(%esp)
        fstpt     24(%esp)
..B1.84:
        cmpl      $0, 36(%esp)
        je        ..B1.86
..B1.85:
        fldcw     70(%esp)
..B1.86:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.87:
        fldt      40(%esp)
        fstpt     24(%esp)
        fldt      24(%esp)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldl      _ones@GOTOFF(%ebx)
        movl      $0, 24(%esp)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      24(%esp)
        movl      60(%esp), %eax
        fsubr     %st, %st(2)
        fldt      72(%esp)
        fldl      _zeros@GOTOFF(%ebx)
        jmp       ..B1.88
..B1.91:
        fld       %st(2)
        fadd      %st(5), %st
        fld       %st(3)
        fmul      %st(6), %st
        fxch      %st(4)
        fmul      %st(0), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fxch      %st(1)
        fmulp     %st, %st(6)
        movl      $0, 24(%esp)
        fxch      %st(5)
        faddp     %st, %st(3)
        fxch      %st(4)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      24(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(3)
        fxch      %st(2)
        fxch      %st(4)
..B1.88:
        testb     $1, %al
        je        ..B1.90
..B1.89:
        fld       %st(4)
        fadd      %st(3), %st
        fmulp     %st, %st(1)
        fld       %st(4)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     72(%esp)
        fldt      72(%esp)
        fstpt     96(%esp)
        fldt      96(%esp)
        movl      $0, 72(%esp)
        fldt      72(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
..B1.90:
        shrl      $1, %eax
        testl     %eax, %eax
        je        ..B1.143
        jmp       ..B1.91
..B1.92:
        lea       -1073676224(%esi), %edx
        cmpl      $32864, %edx
        jae       ..B1.94
..B1.93:
        fldl      8+_ones@GOTOFF(%ebx)
        movl      $1, %ecx
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldl      _TWO_32H@GOTOFF(%ebx)
        fstpt     112(%esp)
        fldt      112(%esp)
        fmul      %st(1), %st
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     84(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubr     %st, %st(2)
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.98
..B1.94:
        cmpl      $65535, %esi
        ja        ..B1.96
..B1.95:
        fmull     _TWO_75@GOTOFF(%ebx)
        fstpt     40(%esp)
        movzwl    48(%esp), %ecx
        movl      %ecx, %edx
        andl      $32767, %edx
        addl      $-16458, %edx
        movl      %edx, 52(%esp)
        jmp       ..B1.97
..B1.96:
        movzwl    16(%ebp), %edx
        andl      $32767, %edx
        fstpt     40(%esp)
        addl      $-16383, %edx
        movl      %edx, 52(%esp)
        movzwl    48(%esp), %ecx
..B1.97:
        fldl      _TWO_32H@GOTOFF(%ebx)
        andl      $-32768, %ecx
        orl       $-49153, %ecx
        movw      %cx, 48(%esp)
        xorl      %ecx, %ecx
        movl      44(%esp), %edx
        shrl      $23, %edx
        movzbl    %dl, %edx
        fstpt     112(%esp)
        fldt      112(%esp)
        fldt      40(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     84(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        flds      __libm_rcp_table_256@GOTOFF(%ebx,%edx,4)
        fmul      %st, %st(1)
        fstpt     56(%esp)
        fldt      56(%esp)
        fmulp     %st, %st(2)
        faddl     8+_ones@GOTOFF(%ebx)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fstpt     84(%esp)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
..B1.98:
        fldt      112(%esp)
        testl     %ecx, %ecx
        fld       %st(0)
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(3)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(5), %st
        fmul      %st, %st(3)
        fld       %st(0)
        fadd      %st(4), %st
        fstpt     84(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(4)
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(5)
        fldt      96+_LP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      84+_LP@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      72+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      60+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_LP@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fldt      40(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     84(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(7), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      _LP@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpt     96(%esp)
        fldt      96(%esp)
        fadd      %st(1), %st
        fstpt     72(%esp)
        fldt      72(%esp)
        fxch      %st(4)
        fmulp     %st, %st(6)
        fxch      %st(1)
        faddp     %st, %st(5)
        fldt      96(%esp)
        fsub      %st(3), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      24(%esp)
        fstpt     96(%esp)
        fldt      96(%esp)
        fadd      %st(1), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        je        ..B1.100
..B1.99:
        fstpt     40(%esp)
        fldt      40(%esp)
        jmp       ..B1.101
..B1.100:
        fstp      %st(0)
        fldt      12+_LLN2@GOTOFF(%ebx)
        movl      %edx, %ecx
        fldt      _LLN2@GOTOFF(%ebx)
        shll      $4, %ecx
        fldl      8+__libm_logl_table_256@GOTOFF(%ecx,%ebx)
        fldl      __libm_logl_table_256@GOTOFF(%ecx,%ebx)
        fxch      %st(1)
        fadds     _LT2@GOTOFF(%ebx,%edx,4)
        movl      52(%esp), %edx
        movl      %edx, 16(%esp)
        fildl     16(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     96(%esp)
        fldt      96(%esp)
        fadd      %st(2), %st
        fxch      %st(3)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      96(%esp)
        fsub      %st(2), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      24(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
..B1.101:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     56(%esp)
        fldt      56(%esp)
        movzwl    64(%esp), %ecx
        shll      $16, %ecx
        movzwl    62(%esp), %edx
        orl       %edx, %ecx
        andl      $2147483647, %ecx
        lea       (%eax,%ecx), %edx
        lea       -1610514432(%ecx,%eax), %eax
        cmpl      $538050560, %eax
        jbe       ..B1.113
..B1.102:
        cmpl      $-2146402304, %edx
        jbe       ..B1.109
..B1.103:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
        movl      108(%esp), %edx
        cmpl      $0, 124(%esp)
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %eax
        je        ..B1.105
..B1.104:
        fldt      _small_value_80@GOTOFF(%eax,%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        jmp       ..B1.106
..B1.105:
        fldt      _large_value_80@GOTOFF(%eax,%ebx)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.106:
        fstpt     24(%esp)
        cmpl      $0, 36(%esp)
        je        ..B1.108
..B1.107:
        fldcw     70(%esp)
..B1.108:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.109:
        cmpl      $1610514432, %edx
        jae       ..B1.113
..B1.110:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
        movl      108(%esp), %ecx
        movl      124(%esp), %eax
        xorl      %ecx, %eax
        cmpl      $0, 36(%esp)
        lea       (,%eax,8), %esi
        lea       (%esi,%eax,4), %edx
        fldt      _small_value_80@GOTOFF(%ebx,%edx)
        faddl     _ones@GOTOFF(%ebx,%ecx,8)
        fstpt     24(%esp)
        je        ..B1.112
..B1.111:
        fldcw     70(%esp)
..B1.112:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.113:
        fldt      56(%esp)
        fxch      %st(1)
        fmulp     %st, %st(4)
        movl      108(%esp), %eax
        fadd      %st(3), %st
        fxch      %st(1)
        fstpt     96(%esp)
        fstpt     84(%esp)
        fldt      84(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fldt      112(%esp)
        fldt      20(%ebp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     84(%esp)
        fld       %st(0)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      84(%esp)
        fsubp     %st, %st(2)
        fsub      %st(1), %st
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fstpt     56(%esp)
        fldt      56(%esp)
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        movzwl    64(%esp), %eax
        shll      $16, %eax
        movzwl    62(%esp), %edx
        orl       %edx, %eax
        andl      $2147483647, %eax
        cmpl      $1072398336, %eax
        jae       ..B1.120
..B1.114:
        cmpl      $1069318144, %eax
        jae       ..B1.116
..B1.115:
        fstp      %st(3)
        fmul      %st(2), %st
        fadd      %st(2), %st
        fxch      %st(2)
        jmp       ..B1.117
..B1.116:
        fldt      12+_EP1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      _EP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fld       %st(0)
        faddp     %st, %st(3)
..B1.117:
        fxch      %st(2)
        fstpt     24(%esp)
        cmpl      $0, 36(%esp)
        je        ..B1.201
..B1.118:
        fstpt     40(%esp)
        fstpt     12(%esp)
..B1.199:
        fldcw     70(%esp)
        jmp       ..B1.119
..B1.201:
        fstp      %st(0)
        fstp      %st(0)
..B1.119:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.120:
        fldl      _TWO_63H@GOTOFF(%ebx)
        fxch      %st(1)
        fstpt     12(%esp)
        fxch      %st(2)
        fstpt     40(%esp)
        fldt      40(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        movl      124(%esp), %eax
        fadd      %st(2), %st
        fstpt     84(%esp)
        fldt      84(%esp)
        movl      84(%esp), %ecx
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      96(%esp)
        fldt      112(%esp)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        movsbl    %cl, %edx
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        movl      %ecx, 24(%esp)
        subl      %edx, %ecx
        sarl      $8, %ecx
        fsubr     %st(2), %st
        movl      ESC2_BIAS@GOTOFF(%ebx,%eax,4), %esi
        fadd      %st(1), %st
        fstpt     84(%esp)
        lea       -1(%esi,%ecx), %edi
        fldt      84(%esp)
        lea       (%esi,%ecx), %eax
        cmpl      $32766, %edi
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fldt      96(%esp)
        fsubrp    %st, %st(2)
        jb        ..B1.127
..B1.121:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      108(%esp), %ecx
        cmpl      $1, %eax
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        jg        ..B1.123
..B1.122:
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        jmp       ..B1.124
..B1.123:
        fldt      _large_value_80@GOTOFF(%edx,%ebx)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.124:
        fstpt     24(%esp)
        cmpl      $0, 36(%esp)
        je        ..B1.126
..B1.125:
        fldcw     70(%esp)
..B1.126:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.127:
        fldt      48+_EP@GOTOFF(%ebx)
        andl      $32767, %eax
        fstpt     96(%esp)
        fld       %st(0)
        shll      $4, %edx
        fxch      %st(2)
        faddp     %st, %st(3)
        movzwl    20(%esp), %esi
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        andl      $-32768, %esi
        fmul      %st(1), %st
        orl       %eax, %esi
        fldt      96(%esp)
        fmul      %st(1), %st
        fstpt     96(%esp)
        fldt      36+_EP@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      24+_EP@GOTOFF(%ebx)
        fldt      12+_EP@GOTOFF(%ebx)
        movl      24(%esp), %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        addl      $4196735, %eax
        fmulp     %st, %st(3)
        cmpl      $3070, %eax
        fldt      96(%esp)
        movw      %si, 20(%esp)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fmul      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      _EP@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%ebx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmul      %st, %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fadd      %st(1), %st
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        ja        ..B1.130
..B1.128:
        fldt      56(%esp)
        fldt      _Eexpbut@GOTOFF(%ebx)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.130
..B1.129:
        fldl      _TWO_12H@GOTOFF(%ebx)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        movl      124(%esp), %eax
        fadd      %st(1), %st
        fstpt     84(%esp)
        lea       (,%eax,8), %esi
        fldt      84(%esp)
        lea       (%esi,%eax,4), %edx
        fsubp     %st, %st(1)
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(2)
        fldt      12(%esp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _ESC2@GOTOFF(%edx,%ebx)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     24(%esp)
        jmp       ..B1.131
..B1.130:
        movl      124(%esp), %eax
        lea       (,%eax,8), %esi
        lea       (%esi,%eax,4), %edx
        fldt      _ESC2@GOTOFF(%edx,%ebx)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      12(%esp)
        fmulp     %st, %st(1)
        fstpt     24(%esp)
..B1.131:
        movzwl    32(%esp), %eax
        shll      $16, %eax
        movzwl    30(%esp), %edx
        orl       %edx, %eax
        andl      $2147483647, %eax
        lea       -98304(%eax), %ecx
        cmpl      $2147319808, %ecx
        jb        ..B1.138
..B1.132:
        movl      108(%esp), %ecx
        cmpl      $65535, %eax
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        ja        ..B1.134
..B1.133:
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     136(%esp)
        jmp       ..B1.135
..B1.134:
        fldt      _large_value_80@GOTOFF(%edx,%ebx)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     24(%esp)
..B1.135:
        cmpl      $0, 36(%esp)
        je        ..B1.137
..B1.136:
        fldcw     70(%esp)
..B1.137:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.138:
        cmpl      $0, 36(%esp)
        je        ..B1.140
..B1.139:
        fldcw     70(%esp)
..B1.140:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.141:
        fldt      8(%ebp)
        fdivrl    _ones@GOTOFF(%ebx)
        fstpt     24(%esp)
        jmp       ..B1.41
..B1.142:
        xorl      %esi, %esi
        jmp       ..B1.48
..B1.143:
        fstp      %st(2)
        fstp      %st(3)
        cmpl      $0, 56(%esp)
        jne       ..B1.145
..B1.144:
        fstp      %st(1)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fxch      %st(1)
        jmp       ..B1.146
..B1.145:
        fld       %st(2)
        fadd      %st(1), %st
        fdivr     %st, %st(2)
        fxch      %st(2)
        fstpt     40(%esp)
        fldt      40(%esp)
        movl      $0, 40(%esp)
        fstpt     56(%esp)
        fldt      56(%esp)
        fldt      40(%esp)
        fld       %st(0)
        fsubr     %st(2), %st
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
..B1.146:
        fldt      12(%esp)
        fld       %st(2)
        fmul      %st(1), %st
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fstpt     24(%esp)
        movzwl    32(%esp), %eax
        shll      $16, %eax
        movzwl    30(%esp), %edx
        orl       %edx, %eax
        andl      $2147483647, %eax
        cmpl      $65535, %eax
        ja        ..B1.148
..B1.147:
        fldt      40(%esp)
        fmul      %st(1), %st
        fstpt     40(%esp)
        fldt      40(%esp)
        fxch      %st(3)
        fstpt     96(%esp)
        fld       %st(0)
        fdivr     %st(3), %st
        fldt      96(%esp)
        fsub      %st(1), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     24(%esp)
        movzwl    32(%esp), %eax
        shll      $16, %eax
        movzwl    30(%esp), %edx
        orl       %edx, %eax
        andl      $2147483647, %eax
        fxch      %st(2)
        fxch      %st(1)
..B1.148:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        lea       -98304(%eax), %edx
        cmpl      $2147319808, %edx
        jb        ..B1.84
..B1.149:
        movl      108(%esp), %ecx
        cmpl      $65535, %eax
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        ja        ..B1.151
..B1.150:
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     136(%esp)
        jmp       ..B1.152
..B1.151:
        fldt      _large_value_80@GOTOFF(%edx,%ebx)
        fldt      _large_value_80@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     24(%esp)
..B1.152:
        cmpl      $0, 36(%esp)
        je        ..B1.154
..B1.153:
        fldcw     70(%esp)
..B1.154:
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.155:
        movl      $0, 36(%esp)
        jmp       ..B1.65
..B1.156:
        cmpl      $-2147483648, 60(%esp)
        jne       ..B1.158
..B1.157:
        cmpl      $0, 36(%esp)
        je        ..B1.159
..B1.158:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     136(%esp)
        fldt      136(%esp)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.159:
        cmpl      $32767, 44(%esp)
        je        ..B1.181
..B1.160:
        cmpl      $32767, 48(%esp)
        jne       ..B1.20
..B1.161:
        cmpl      $-2147483648, 60(%esp)
        jne       ..B1.20
..B1.162:
        cmpl      $0, 36(%esp)
        jne       ..B1.20
..B1.163:
        cmpl      $1073709056, %esi
        jae       ..B1.174
..B1.164:
        movl      32(%esp), %eax
        orl       %esi, %eax
        jne       ..B1.168
..B1.165:
        fldl      _zeros@GOTOFF(%ebx)
        cmpl      $0, 56(%esp)
        jne       ..B1.180
..B1.167:
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.168:
        cmpl      $65536, %esi
        jae       ..B1.170
..B1.169:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     128(%esp)
..B1.170:
        cmpl      $0, 56(%esp)
        je        ..B1.172
..B1.171:
        fldl      _infs@GOTOFF(%ebx)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.172:
        fldl      _zeros@GOTOFF(%ebx)
..B1.173:
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.174:
        addl      $-1073709056, %esi
        orl       24(%esp), %esi
        orl       32(%esp), %esi
        je        ..B1.179
..B1.175:
        cmpl      $0, 56(%esp)
        je        ..B1.177
..B1.176:
        fldl      _zeros@GOTOFF(%ebx)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.177:
        fldl      _infs@GOTOFF(%ebx)
..B1.178:
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.179:
        movl      12(%esp), %eax
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.180:
        fldl      _ones@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.181:
        cmpl      $-2147483648, %edx
        jne       ..B1.160
..B1.182:
        cmpl      $0, 32(%esp)
        jne       ..B1.160
..B1.183:
        cmpl      $65536, %eax
        jae       ..B1.185
..B1.184:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     128(%esp)
..B1.185:
        cmpl      $0, 40(%esp)
        movl      %ecx, %eax
        je        ..L7
        movl      $0, %eax
..L7:
        movl      %eax, 108(%esp)
        cmpl      $0, 56(%esp)
        je        ..B1.187
..B1.186:
        fldl      _zeros@GOTOFF(%ebx,%eax,8)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.187:
        fldl      _infs@GOTOFF(%ebx,%eax,8)
..B1.188:
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.189:
        cmpl      $-2147483648, %edx
        jne       ..B1.158
..B1.190:
        cmpl      $0, 32(%esp)
        jne       ..B1.158
..B1.191:
        cmpl      $32767, 48(%esp)
        je        ..B1.156
        jmp       ..B1.159
..B1.192:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.193:
        movl      108(%esp), %eax
        fldl      _ones@GOTOFF(%ebx,%eax,8)
        fdivl     _zeros@GOTOFF(%ebx)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	powl,@function
	.size	powl,.-powl
	.data
# -- End  powl
	.text
# -- Begin  __sqrtl
	.text
       .align    16,0x90
__sqrtl:
# parameter 1: 4 + %esp
..B2.1:
..L8:

..B2.2:
        movl      4(%esp), %ecx
        fldt      (%ecx)
        fsqrt     
        ret       
..B2.3:
        .align    16,0x90
	.type	__sqrtl,@function
	.size	__sqrtl,.-__sqrtl
	.data
# -- End  __sqrtl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0xf0,0x17,0x5c,0x29,0x3b,0xaa,0xb8,0x07,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xf6,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xd4,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
_TWO_43H:
	.long	0
	.long	1118306304
	.type	_TWO_43H,@object
	.size	_TWO_43H,8
	.align 4
_exact_masks:
	.long	131071
	.long	0
	.long	4294967295
	.long	1
	.long	4294967295
	.long	511
	.long	4294967295
	.long	16383
	.long	4294967295
	.long	131071
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	2097151
	.long	4294967295
	.long	8388607
	.long	4294967295
	.long	16777215
	.long	4294967295
	.long	33554431
	.long	4294967295
	.long	33554431
	.long	4294967295
	.long	67108863
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	134217727
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	268435455
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	536870911
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	1073741823
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.type	_exact_masks,@object
	.size	_exact_masks,376
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_LT2:
	.long	180342526
	.long	2357584585
	.long	2340989257
	.long	193458247
	.long	2370723239
	.long	2317577053
	.long	2382055315
	.long	232148275
	.long	200972627
	.long	2376530255
	.long	2376860906
	.long	222325808
	.long	228268523
	.long	229816737
	.long	2387925496
	.long	239088248
	.long	235445027
	.long	219576635
	.long	2365457762
	.long	200344795
	.long	2377779510
	.long	2396359259
	.long	256760275
	.long	2390604874
	.long	2403557039
	.long	2397936349
	.long	2387307486
	.long	2389941185
	.long	2375749593
	.long	224865835
	.long	2394232861
	.long	180745560
	.long	231885773
	.long	2352962188
	.long	2306055684
	.long	206875141
	.long	258325764
	.long	2386208973
	.long	231066739
	.long	195513036
	.long	2360327132
	.long	2396045581
	.long	236564318
	.long	2282584976
	.long	2351736009
	.long	236364231
	.long	2384427773
	.long	267355851
	.long	255771104
	.long	265321597
	.long	2384375360
	.long	2399209743
	.long	186438128
	.long	2407131686
	.long	2382793784
	.long	2395218767
	.long	229223248
	.long	256892528
	.long	2394735602
	.long	2385223602
	.long	2372921302
	.long	2398642377
	.long	2399679754
	.long	255686225
	.long	220689523
	.long	2385772978
	.long	209771590
	.long	2396079173
	.long	247531082
	.long	236085121
	.long	264660069
	.long	2357821390
	.long	236486650
	.long	2368138461
	.long	239690108
	.long	2396070228
	.long	196955922
	.long	217533735
	.long	255641653
	.long	249646543
	.long	253232621
	.long	246212714
	.long	2396427575
	.long	261629730
	.long	2364672847
	.long	2403865410
	.long	251273869
	.long	261125380
	.long	2388321036
	.long	2375891827
	.long	2381014317
	.long	2414822930
	.long	2406245053
	.long	2406988902
	.long	2395399973
	.long	213294711
	.long	2401319324
	.long	265450299
	.long	231845893
	.long	260578794
	.long	258934474
	.long	2368795786
	.long	2422506717
	.long	218205033
	.long	258708287
	.long	257044854
	.long	2396281866
	.long	2415975150
	.long	257697283
	.long	2393801226
	.long	2388654986
	.long	262910764
	.long	235638432
	.long	268054962
	.long	271083258
	.long	256780444
	.long	256064350
	.long	252979189
	.long	2420460964
	.long	233178508
	.long	251655134
	.long	2404577473
	.long	2408876374
	.long	2401072099
	.long	228623072
	.long	2421706621
	.long	2353033467
	.long	2411792212
	.long	2345330012
	.long	2397189005
	.long	243110369
	.long	2408213151
	.long	219253901
	.long	246695172
	.long	259288551
	.long	254210780
	.long	261865355
	.long	230359170
	.long	263524551
	.long	2405618542
	.long	2399869921
	.long	233826616
	.long	2362750769
	.long	257576291
	.long	251808114
	.long	2403390977
	.long	253249058
	.long	261065811
	.long	215406974
	.long	2407628993
	.long	258391343
	.long	2394470699
	.long	258495470
	.long	2415771687
	.long	224670583
	.long	263047889
	.long	2389031226
	.long	259453051
	.long	266564569
	.long	2422023012
	.long	264051375
	.long	2409075373
	.long	252244729
	.long	220302768
	.long	254847756
	.long	243124762
	.long	2363519942
	.long	2393496158
	.long	2402135082
	.long	2365706590
	.long	237902971
	.long	262295026
	.long	252689257
	.long	236678680
	.long	255167613
	.long	2406879752
	.long	274254693
	.long	259983961
	.long	245852525
	.long	256459527
	.long	220533989
	.long	245387845
	.long	243835221
	.long	2377853444
	.long	274540751
	.long	229741069
	.long	263626566
	.long	220608387
	.long	226161046
	.long	247301717
	.long	268433686
	.long	222008210
	.long	2414961139
	.long	276137642
	.long	2412197898
	.long	258445918
	.long	2412645341
	.long	260857859
	.long	263113037
	.long	2386484264
	.long	2390742630
	.long	259665290
	.long	2363417497
	.long	2384122046
	.long	263493300
	.long	219472625
	.long	2420433676
	.long	274201285
	.long	2404911312
	.long	257926520
	.long	260332203
	.long	256695016
	.long	218494534
	.long	2420150889
	.long	2414586287
	.long	267006159
	.long	231320206
	.long	2382797064
	.long	2414903062
	.long	263514471
	.long	2406010284
	.long	2410428205
	.long	2414319991
	.long	249083715
	.long	2381171937
	.long	236369629
	.long	2409614279
	.long	2424220710
	.long	2378379154
	.long	218677462
	.long	2382987336
	.long	2377870458
	.long	258155310
	.long	2368239928
	.long	2414896572
	.long	193310867
	.long	256673115
	.long	2406448055
	.long	271719709
	.long	248566852
	.long	229016248
	.long	272541555
	.long	2382115967
	.long	2375311600
	.long	2393766434
	.long	257187362
	.long	277931545
	.long	281038165
	.long	2373962226
	.long	261638946
	.long	255465134
	.long	273460929
	.long	275851296
	.long	2417166241
	.long	2361106656
	.long	278653754
	.type	_LT2,@object
	.size	_LT2,1024
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
ESC2_BIAS:
	.long	4095
	.long	28671
	.type	ESC2_BIAS,@object
	.size	ESC2_BIAS,8
	.align 4
_TWO_12H:
	.long	0
	.long	1085800448
	.type	_TWO_12H,@object
	.size	_TWO_12H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_invsqrtl_scales:
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
	.word	20479
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	12287
	.word	0
	.type	_invsqrtl_scales,@object
	.size	_invsqrtl_scales,48
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
	.align 2
_LP:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49149
	.word	0
	.word	52785
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	44453
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	25655
	.word	14678
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	38741
	.word	53722
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	45676
	.word	59527
	.word	48579
	.word	58254
	.word	16379
	.word	0
	.word	41682
	.word	64796
	.word	25002
	.word	52429
	.word	49147
	.word	0
	.type	_LP,@object
	.size	_LP,108
	.align 2
_LLN2:
	.word	0
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	2022
	.word	26416
	.word	37831
	.word	62295
	.word	16333
	.word	0
	.type	_LLN2,@object
	.size	_LLN2,24
	.align 2
_EP1:
	.word	43691
	.word	10
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	30584
	.word	43703
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_EP1,@object
	.size	_EP1,24
	.align 2
_EP:
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
	.type	_EP,@object
	.size	_EP,60
	.align 2
_Eexpbut:
	.word	60207
	.word	4624
	.word	35943
	.word	45420
	.word	49164
	.word	0
	.type	_Eexpbut,@object
	.size	_Eexpbut,12
	.align 2
_ESC2:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	28671
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	4095
	.word	0
	.type	_ESC2,@object
	.size	_ESC2,24
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
