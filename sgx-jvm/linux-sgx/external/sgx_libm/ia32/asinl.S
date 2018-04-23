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
	.file "asinl.c"
	.text
..TXTST0:
# -- Begin  asinl
	.text
       .align    16,0x90
	.globl asinl
asinl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $88, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzbl    17(%ebp), %edx
        movzwl    16(%ebp), %eax
        andl      $128, %edx
        andl      $32767, %eax
        shrl      $7, %edx
        movzwl    14(%esp), %ebx
        cmpl      $16383, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        jge       ..B1.59
..B1.4:
        cmpl      $16382, %eax
        jge       ..B1.49
..B1.5:
        cmpl      $16376, %eax
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %eax
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %eax
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %eax
        jge       ..B1.18
..B1.9:
        movl      %ebx, %edi
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.79
..B1.10:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.11:
        fldcw     12(%esp)
..B1.12:
        movzwl    16(%ebp), %eax
        movl      $1, %ebx
        andl      $32767, %eax
..B1.13:
        testl     %eax, %eax
        jle       ..B1.75
..B1.14:
        fldt      8(%ebp)
        fldl      _TWO_75@GOTOFF(%ecx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     8+_TWO_75@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.15:
        testl     %ebx, %ebx
        je        ..B1.17
..B1.16:
        fldcw     14(%esp)
..B1.17:
        fldt      (%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.80
..B1.19:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.20:
        fldcw     12(%esp)
..B1.21:
        movl      $1, %eax
..B1.22:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _P4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.24
..B1.23:
        fldcw     14(%esp)
..B1.24:
        fldt      (%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.81
..B1.26:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.27:
        fldcw     12(%esp)
..B1.28:
        movl      $1, %eax
..B1.29:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_P3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      _P3@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.31
..B1.30:
        fldcw     14(%esp)
..B1.31:
        fldt      (%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.82
..B1.33:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.34:
        fldcw     12(%esp)
..B1.35:
        movl      $1, %eax
..B1.36:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_P2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      12+_P2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P2@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        fldt      _P2@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.38
..B1.37:
        fldcw     14(%esp)
..B1.38:
        fldt      (%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        movl      %ebx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.83
..B1.40:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.41:
        fldcw     12(%esp)
..B1.42:
        movzwl    16(%ebp), %eax
        movl      $1, %edx
        andl      $32767, %eax
..B1.43:
        fldt      8(%ebp)
        cmpl      $16381, %eax
        fld       %st(0)
        fldl      _TWO_52H@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fstpt     16(%esp)
        fld       %st(1)
        fsubr     %st(3), %st
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(2)
        fstpt     64(%esp)
        fldt      64(%esp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     32(%esp)
        fldt      32(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        faddp     %st, %st(2)
        jge       ..B1.45
..B1.44:
        fstp      %st(0)
        fldt      132+_P1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      108+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      72+_P1@GOTOFF(%ecx)
        fldt      120+_P1@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      96+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _P1@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%esp)
        fldt      144+_P1@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        jmp       ..B1.46
..B1.45:
        fldt      204+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      180+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      156+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      132+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      108+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      84+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      60+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      192+_P@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      168+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      144+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      120+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      96+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fldt      48(%esp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      32(%esp)
        fldt      (%esp)
        fldt      228+_P@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      216+_P@GOTOFF(%ecx)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%esp)
        fmul      %st(1), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fsub      %st(3), %st
        faddp     %st, %st(1)
..B1.46:
        fldt      64(%esp)
        fld       %st(2)
        fmul      %st(4), %st
        testl     %edx, %edx
        fxch      %st(5)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        je        ..B1.48
..B1.47:
        fldcw     14(%esp)
..B1.48:
        fldt      64(%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.49:
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.84
..B1.50:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.51:
        fldcw     12(%esp)
..B1.52:
        movl      $1, %ebx
..B1.53:
        fldl      _ones@GOTOFF(%ecx,%edx,8)
        fstl      80(%esp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fldl      _TWO_52H@GOTOFF(%ecx)
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(3)
        fsubrl    _ones@GOTOFF(%ecx)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fsub      %st(2), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fsubr     %st, %st(3)
        fld       %st(5)
        fxch      %st(2)
        fsub      %st(6), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(4)
        fadd      %st(1), %st
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fld       %st(4)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     64(%esp)
        fldt      64(%esp)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.55
..B1.54:
        fstp      %st(1)
        fldt      132+_P1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      108+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      72+_P1@GOTOFF(%ecx)
        fldt      120+_P1@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      96+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_P1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _P1@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fldt      64(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%esp)
        fldt      144+_P1@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.56
..B1.55:
        fldt      204+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      180+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      156+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      132+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      108+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      192+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      168+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      144+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      120+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      96+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fldt      64(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      48(%esp)
        fldt      16(%esp)
        fldt      228+_P@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      216+_P@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fldt      32(%esp)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
..B1.56:
        fldt      (%esp)
        testl     %ebx, %ebx
        fmulp     %st, %st(2)
        fld       %st(2)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _pi_02@GOTOFF(%ecx)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      12+_pi_02@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     80(%esp)
        fstpt     16(%esp)
        je        ..B1.58
..B1.57:
        fldcw     14(%esp)
..B1.58:
        fldt      16(%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.59:
        movl      %ebx, %edi
        andl      $768, %edi
        cmpl      $768, %edi
        je        ..B1.85
..B1.60:
        orl       $-64768, %ebx
        movw      %bx, 12(%esp)
..B1.61:
        fldcw     12(%esp)
..B1.62:
        movzwl    16(%ebp), %eax
        movl      $1, %ebx
        andl      $32767, %eax
..B1.63:
        cmpl      $32767, %eax
        jne       ..B1.67
..B1.64:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 8(%ebp)
        je        ..B1.71
..B1.66:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.72
..B1.67:
        cmpl      $16383, %eax
        jne       ..B1.71
..B1.68:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.71
..B1.69:
        cmpl      $0, 8(%ebp)
        jne       ..B1.71
..B1.70:
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %edx
        fldt      _pi_2l@GOTOFF(%edx,%ecx)
        fldt      _small_value_80@GOTOFF(%edx,%ecx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.72
..B1.71:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.72:
        testl     %ebx, %ebx
        je        ..B1.74
..B1.73:
        fldcw     14(%esp)
..B1.74:
        fldt      (%esp)
        addl      $88, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.75:
        cmpl      $0, 12(%ebp)
        jne       ..B1.77
..B1.76:
        cmpl      $0, 8(%ebp)
        je        ..B1.78
..B1.77:
        fldt      8(%ebp)
        lea       (,%edx,8), %eax
        fldt      _small_value_80@GOTOFF(%ecx)
        lea       (%eax,%edx,4), %edx
        fldt      _small_value_80@GOTOFF(%ecx,%edx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.78:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.15
..B1.79:
        xorl      %ebx, %ebx
        jmp       ..B1.13
..B1.80:
        xorl      %eax, %eax
        jmp       ..B1.22
..B1.81:
        xorl      %eax, %eax
        jmp       ..B1.29
..B1.82:
        xorl      %eax, %eax
        jmp       ..B1.36
..B1.83:
        xorl      %edx, %edx
        jmp       ..B1.43
..B1.84:
        xorl      %ebx, %ebx
        jmp       ..B1.53
..B1.85:
        xorl      %ebx, %ebx
        jmp       ..B1.63
        .align    16,0x90
	.type	asinl,@function
	.size	asinl,.-asinl
	.data
# -- End  asinl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0xc0,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
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
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 2
_P4:
	.word	43691
	.word	44938
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_P4,@object
	.size	_P4,12
	.align 2
_P3:
	.word	36763
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	40704
	.word	58046
	.word	39460
	.word	39321
	.word	16379
	.word	0
	.type	_P3,@object
	.size	_P3,24
	.align 2
_P2:
	.word	43688
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	20771
	.word	39331
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	23807
	.word	53798
	.word	28073
	.word	46811
	.word	16378
	.word	0
	.word	35198
	.word	7516
	.word	54204
	.word	63722
	.word	16377
	.word	0
	.type	_P2,@object
	.size	_P2,48
	.align 2
_P1:
	.word	43589
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	39364
	.word	39321
	.word	39321
	.word	39321
	.word	16379
	.word	0
	.word	17602
	.word	56173
	.word	28086
	.word	46811
	.word	16378
	.word	0
	.word	47501
	.word	58414
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	27078
	.word	6583
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	39781
	.word	21053
	.word	25224
	.word	36391
	.word	16377
	.word	0
	.word	46568
	.word	6235
	.word	50749
	.word	58572
	.word	16376
	.word	0
	.word	41318
	.word	51558
	.word	37628
	.word	48452
	.word	16376
	.word	0
	.word	55026
	.word	7893
	.word	43866
	.word	40925
	.word	16376
	.word	0
	.word	43387
	.word	19951
	.word	2911
	.word	35445
	.word	16376
	.word	0
	.word	32572
	.word	59281
	.word	11243
	.word	56666
	.word	16375
	.word	0
	.word	49800
	.word	50337
	.word	42707
	.word	39184
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16380
	.word	0
	.type	_P1,@object
	.size	_P1,156
	.align 2
_P:
	.word	42691
	.word	43690
	.word	43690
	.word	43690
	.word	16368
	.word	0
	.word	4317
	.word	52400
	.word	52428
	.word	52428
	.word	49134
	.word	0
	.word	26206
	.word	56172
	.word	28086
	.word	46811
	.word	16378
	.word	0
	.word	36130
	.word	58514
	.word	36408
	.word	63715
	.word	16377
	.word	0
	.word	21592
	.word	8113
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	3093
	.word	49950
	.word	25215
	.word	36391
	.word	16377
	.word	0
	.word	21499
	.word	24428
	.word	51889
	.word	58572
	.word	16376
	.word	0
	.word	54828
	.word	41264
	.word	61120
	.word	48451
	.word	16376
	.word	0
	.word	10243
	.word	1489
	.word	39717
	.word	40940
	.word	16376
	.word	0
	.word	51433
	.word	46039
	.word	63564
	.word	35220
	.word	16376
	.word	0
	.word	60402
	.word	39286
	.word	25675
	.word	60826
	.word	16375
	.word	0
	.word	34219
	.word	36833
	.word	44839
	.word	57547
	.word	16375
	.word	0
	.word	13013
	.word	26091
	.word	19721
	.word	60832
	.word	16374
	.word	0
	.word	22353
	.word	41544
	.word	55443
	.word	55735
	.word	16376
	.word	0
	.word	14948
	.word	7038
	.word	38091
	.word	38339
	.word	49145
	.word	0
	.word	61727
	.word	61480
	.word	15906
	.word	51927
	.word	16378
	.word	0
	.word	13386
	.word	23084
	.word	64443
	.word	55906
	.word	49146
	.word	0
	.word	17499
	.word	23573
	.word	43947
	.word	42761
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39328
	.word	16379
	.word	0
	.type	_P,@object
	.size	_P,240
	.align 2
_pi_02:
	.word	0
	.word	0
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	35374
	.word	4889
	.word	2259
	.word	34211
	.word	16349
	.word	0
	.type	_pi_02,@object
	.size	_pi_02,24
	.align 2
_pi_2l:
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
	.type	_pi_2l,@object
	.size	_pi_2l,24
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
	.section .note.GNU-stack, ""
# End
