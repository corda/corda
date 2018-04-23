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
	.file "hypotl.c"
	.text
..TXTST0:
# -- Begin  hypotl
	.text
       .align    16,0x90
	.globl hypotl
hypotl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $24, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        andb      $127, 17(%ebp)
        andb      $127, 29(%ebp)
        movzwl    16(%ebp), %esi
        andl      $32767, %esi
        movzwl    28(%ebp), %ebx
        andl      $32767, %ebx
        cmpl      $32767, %esi
        je        ..B1.73
..B1.4:
        cmpl      $32767, %ebx
        je        ..B1.73
..B1.5:
        movl      %esi, %eax
        subl      %ebx, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        lea       64(%eax), %edx
        cmpl      $128, %edx
        ja        ..B1.27
..B1.6:
        lea       (%esi,%ebx), %edx
        cmpl      $18376, %edx
        jge       ..B1.38
..B1.7:
        testl     %esi, %esi
        jne       ..B1.14
..B1.8:
        cmpl      $0, 12(%ebp)
        jne       ..B1.14
..B1.9:
        cmpl      $0, 8(%ebp)
        jne       ..B1.14
..B1.10:
        testl     %ebx, %ebx
        jne       ..B1.14
..B1.11:
        cmpl      $0, 24(%ebp)
        jne       ..B1.14
..B1.12:
        cmpl      $0, 20(%ebp)
        jne       ..B1.14
..B1.13:
        fldl      _zeros@GOTOFF(%ecx)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        movzwl    14(%esp), %ebx
        movl      %ebx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.37
..B1.15:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        movl      $1, %ebx
..B1.18:
        fldt      _scales@GOTOFF(%ecx)
        lea       4(%eax), %edx
        fldt      8(%ebp)
        cmpl      $8, %edx
        fmul      %st(1), %st
        fldt      20(%ebp)
        fmulp     %st, %st(2)
        ja        ..B1.22
..B1.19:
        fldl      _TWO_48H@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(1), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fld       %st(4)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(6)
        fsub      %st(5), %st
        fmul      %st, %st(2)
        fxch      %st(6)
        fcom      %st(7)
        fnstsw    %ax
        fmulp     %st, %st(1)
        sahf      
        faddp     %st, %st(2)
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(2)
        fmul      %st(0), %st
        fadd      %st, %st(2)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        jbe       ..B1.21
..B1.20:
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B1.24
..B1.21:
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B1.24
..B1.22:
        fldl      _TWO_48H@GOTOFF(%ecx)
        testl     %eax, %eax
        fldl      _TWO_32H@GOTOFF(%ecx)
        jle       ..B1.36
..B1.23:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
..B1.24:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fld       %st(1)
        testl     %ebx, %ebx
        fadd      %st(4), %st
        fsqrt     
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(3)
        fld       %st(3)
        fxch      %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(4), %st
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      12+_scales@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.26
..B1.25:
        fldcw     14(%esp)
..B1.26:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.27:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.72
..B1.28:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.29:
        fldcw     12(%esp)
..B1.30:
        movl      $1, %edx
..B1.31:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      12+_ranges@GOTOFF(%ecx)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.33
        jp        ..B1.33
..B1.32:
        fldt      _large_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.33:
        testl     %edx, %edx
        je        ..B1.35
..B1.34:
        fldcw     14(%esp)
..B1.35:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        jmp       ..B1.24
..B1.37:
        xorl      %ebx, %ebx
        jmp       ..B1.18
..B1.38:
        movzwl    14(%esp), %ebx
        cmpl      $47156, %edx
        jle       ..B1.54
..B1.39:
        movl      %ebx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.69
..B1.40:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.41:
        fldcw     12(%esp)
..B1.42:
        xorl      %ebx, %ebx
        incl      %ebx
..B1.43:
        fldt      12+_scales@GOTOFF(%ecx)
        lea       4(%eax), %edx
        fldt      8(%ebp)
        cmpl      $8, %edx
        fmul      %st(1), %st
        fldt      20(%ebp)
        fmulp     %st, %st(2)
        ja        ..B1.47
..B1.44:
        fldl      _TWO_48H@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(1), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fld       %st(4)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(6)
        fsub      %st(5), %st
        fmul      %st, %st(2)
        fxch      %st(6)
        fcom      %st(7)
        fnstsw    %ax
        fmulp     %st, %st(1)
        sahf      
        faddp     %st, %st(2)
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(2)
        fmul      %st(0), %st
        fadd      %st, %st(2)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        jbe       ..B1.46
..B1.45:
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B1.49
..B1.46:
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B1.49
..B1.47:
        fldl      _TWO_48H@GOTOFF(%ecx)
        testl     %eax, %eax
        fldl      _TWO_32H@GOTOFF(%ecx)
        jle       ..B1.68
..B1.48:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
..B1.49:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fld       %st(1)
        fadd      %st(4), %st
        fsqrt     
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(3)
        fld       %st(3)
        fxch      %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(4), %st
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      _ranges@GOTOFF(%ecx)
        fcompp    
        fnstsw    %ax
        sahf      
        jp        ..B1.50
        jbe       ..B1.67
..B1.50:
        fldt      _scales@GOTOFF(%ecx)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.51:
        testl     %ebx, %ebx
        je        ..B1.53
..B1.52:
        fldcw     14(%esp)
..B1.53:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.54:
        movl      %ebx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.71
..B1.55:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.56:
        fldcw     12(%esp)
..B1.57:
        xorl      %ebx, %ebx
        incl      %ebx
..B1.58:
        lea       4(%eax), %edx
        cmpl      $8, %edx
        ja        ..B1.62
..B1.59:
        fldt      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      20(%ebp)
        fld       %st(0)
        fxch      %st(5)
        fcom      %st(1)
        fnstsw    %ax
        fldl      _TWO_48H@GOTOFF(%ecx)
        fmul      %st, %st(5)
        sahf      
        fxch      %st(4)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fld       %st(2)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(3)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fxch      %st(4)
        fsub      %st(6), %st
        fmul      %st, %st(1)
        fxch      %st(5)
        fsubr     %st, %st(7)
        fxch      %st(4)
        fmul      %st(7), %st
        fxch      %st(2)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(2)
        fmul      %st(0), %st
        fadd      %st, %st(2)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        jbe       ..B1.61
..B1.60:
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.64
..B1.61:
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.64
..B1.62:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      20(%ebp)
        fldl      _TWO_48H@GOTOFF(%ecx)
        fldl      _TWO_32H@GOTOFF(%ecx)
        jle       ..B1.70
..B1.63:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
..B1.64:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fld       %st(1)
        testl     %ebx, %ebx
        fadd      %st(3), %st
        fsqrt     
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(3)
        fld       %st(4)
        fxch      %st(1)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(3)
        fadd      %st(3), %st
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.66
..B1.65:
        fldcw     14(%esp)
..B1.66:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.67:
        fldt      _large_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.51
..B1.68:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        jmp       ..B1.49
..B1.69:
        xorl      %ebx, %ebx
        jmp       ..B1.43
..B1.70:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        jmp       ..B1.64
..B1.71:
        xorl      %ebx, %ebx
        jmp       ..B1.58
..B1.72:
        xorl      %edx, %edx
        jmp       ..B1.31
..B1.73:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.96
..B1.74:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.75:
        fldcw     12(%esp)
..B1.76:
        movl      $1, %eax
..B1.77:
        cmpl      $32767, %esi
        je        ..B1.94
..B1.78:
        cmpl      $32767, %ebx
        je        ..B1.91
..B1.79:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.80:
        testl     %eax, %eax
        je        ..B1.82
..B1.81:
        fldcw     14(%esp)
..B1.82:
        cmpl      $32767, %esi
        je        ..B1.88
..B1.83:
        cmpl      $32767, %ebx
        jne       ..B1.87
..B1.84:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.87
..B1.85:
        cmpl      $0, 20(%ebp)
        jne       ..B1.87
..B1.86:
        fldt      20(%ebp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.87:
        fldt      (%esp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.88:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.83
..B1.89:
        cmpl      $0, 8(%ebp)
        jne       ..B1.83
..B1.90:
        fldt      8(%ebp)
        addl      $24, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.91:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.93
..B1.92:
        cmpl      $0, 20(%ebp)
        je        ..B1.79
..B1.93:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.80
..B1.94:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.93
..B1.95:
        cmpl      $0, 8(%ebp)
        jne       ..B1.93
        jmp       ..B1.78
..B1.96:
        xorl      %eax, %eax
        jmp       ..B1.77
        .align    16,0x90
	.type	hypotl,@function
	.size	hypotl,.-hypotl
	.data
# -- End  hypotl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_scales:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24653
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8113
	.word	0
	.type	_scales,@object
	.size	_scales,24
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24497
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	32766
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
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
	.section .note.GNU-stack, ""
# End
