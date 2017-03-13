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
	.file "yn.c"
	.text
..TXTST0:
# -- Begin  yn
	.text
       .align    16,0x90
	.globl yn
yn:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        movl      8(%ebp), %edx
..B1.2:
        fnstcw    70(%esp)
..B1.3:
        movzwl    18(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %ecx
        je        ..B1.69
..B1.4:
        fldl      12(%ebp)
        fldl      _zeros@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jb        ..B1.8
..B1.5:
        fxch      %st(1)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.6
        je        ..B1.44
..B1.6:
        fldl      _infs@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.7:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.8:
        fstp      %st(0)
        testl     %edx, %edx
        je        ..B1.43
..B1.9:
        jl        ..B1.11
..B1.10:
        movl      $1, %edi
        jmp       ..B1.12
..B1.11:
        negl      %edx
        movl      %edx, %eax
        notl      %eax
        andl      $1, %eax
        lea       -1(%eax,%eax), %edi
..B1.12:
        cmpl      $1, %edx
        je        ..B1.58
..B1.13:
        movl      %edx, %eax
        sarl      $5, %eax
        cmpl      $160, %eax
        jg        ..B1.15
..B1.14:
        fldl      XINF@GOTOFF(%ebx,%eax,8)
        jmp       ..B1.16
..B1.15:
        lea       -640(,%eax,4), %eax
        lea       (,%eax,8), %ecx
        subl      %eax, %ecx
        movl      %ecx, (%esp)
        fildl     (%esp)
        faddl     .L_2il0floatpacket.0@GOTOFF(%ebx)
..B1.16:
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.42
..B1.17:
        movzwl    70(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.57
..B1.18:
        fstp      %st(0)
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.19:
        fldcw     68(%esp)
..B1.20:
        fldl      12(%ebp)
        movl      $1, %esi
..B1.21:
        fldt      _val_20_0L@GOTOFF(%ebx)
        fldt      _val_1000_0L@GOTOFF(%ebx)
        movl      %edx, (%esp)
        fildl     (%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        ja        ..B1.46
..B1.22:
        fldt      _val_4_0L@GOTOFF(%ebx)
        movl      $-1, %eax
        fstpt     144(%esp)
        fldt      144(%esp)
        fld       %st(1)
        fmulp     %st, %st(1)
        xorl      %ecx, %ecx
        fmulp     %st, %st(1)
        movl      %ebx, 28(%esp)
        fstpt     192(%esp)
        fldt      192(%esp)
        fxch      %st(1)
        fstl      104(%esp)
        fldt      _val_8_0L@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     176(%esp)
        fldt      176(%esp)
        fldt      _val_p1_0L@GOTOFF(%ebx)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     112(%esp)
        fldt      _val_n1_0L@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fdivrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      _val_2_0L@GOTOFF(%ebx)
        fstpt     48(%esp)
        fldt      48(%esp)
        fstpt     160(%esp)
        fldt      _val_3_0L@GOTOFF(%ebx)
        fstpt     32(%esp)
        fldt      32(%esp)
        fstpt     128(%esp)
        fldt      _val_5_0L@GOTOFF(%ebx)
        fstpt     16(%esp)
        fldt      _val_0_0L@GOTOFF(%ebx)
        movl      %eax, %ebx
        fstpt     88(%esp)
        fldt      16(%esp)
        fldt      128(%esp)
        fldt      32(%esp)
        fldt      48(%esp)
        fldt      (%esp)
..B1.23:
        fldt      192(%esp)
        fld       %st(4)
        fmul      %st(5), %st
        testl     %ebx, %ebx
        fsubr     %st(1), %st
        fxch      %st(5)
        fstpt     128(%esp)
        fxch      %st(2)
        fstpt     48(%esp)
        fldt      48(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      176(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fdivrp    %st, %st(5)
        fxch      %st(6)
        fmulp     %st, %st(4)
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(2)
        fmul      %st(2), %st
        fdivrp    %st, %st(1)
        fmul      %st(2), %st
        fldt      128(%esp)
        fldt      48(%esp)
        jle       ..B1.25
..B1.24:
        fldt      72(%esp)
        fadd      %st(3), %st
        fstpt     72(%esp)
        fxch      %st(4)
        faddp     %st, %st(6)
        jmp       ..B1.26
..B1.25:
        fldt      72(%esp)
        fsub      %st(3), %st
        fstpt     72(%esp)
        fxch      %st(4)
        fsubrp    %st, %st(6)
..B1.26:
        fldt      160(%esp)
        fadd      %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fdiv      %st(6), %st
        fldt      144(%esp)
        fadd      %st, %st(2)
        faddp     %st, %st(6)
        fldt      88(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.29
..B1.27:
        fchs      
..B1.29:
        fldt      112(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.31
..B1.30:
        fstpt     112(%esp)
        movl      $1, %ecx
        jmp       ..B1.32
..B1.31:
        fstp      %st(0)
        testl     %ecx, %ecx
        jne       ..B1.45
..B1.32:
        fldt      16(%esp)
        negl      %ebx
        fucomp    %st(6)
        fnstsw    %ax
        sahf      
        fxch      %st(3)
        fxch      %st(1)
        fxch      %st(5)
        jne       ..B1.23
        jp        ..B1.23
..B1.33:
        fstp      %st(2)
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(0)
        fldt      32(%esp)
        movl      28(%esp), %ebx
        fstpt     12(%esp)
        fstpt     (%esp)
        fstpt     56(%esp)
        fldl      104(%esp)
..B1.34:
        addl      $-32, %esp
        addl      %edx, %edx
        negl      %edx
        lea       44(%esp), %eax
        decl      %edx
        lea       88(%esp), %ecx
        fstpt     (%esp)
        fldt      (%esp)
        movl      %eax, 12(%esp)
        movl      %ecx, 16(%esp)
        movl      %edx, 20(%esp)
        fstpl     92(%eax)
        call      __libm_sincos_pi4l
..B1.78:
        fldl      136(%esp)
        addl      $32, %esp
..B1.35:
        fldt      _tonpi@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        fsqrt     
        fldt      12(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fldt      56(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
..B1.36:
        testl     %esi, %esi
        je        ..B1.38
..B1.37:
        fldcw     70(%esp)
..B1.38:
        fldt      12(%esp)
        testl     %edi, %edi
        jge       ..B1.41
..B1.40:
        fchs      
..B1.41:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.42:
        fstp      %st(0)
        movzbl    19(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      _large_value_64@GOTOFF(%ebx,%eax,8)
        fmull     _large_value_64@GOTOFF(%ebx)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        addl      $-16, %esp
        fstpl     (%esp)
        call      y0@PLT
..B1.79:
        addl      $260, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.44:
        fldl      8+_ones@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        fstp      %st(2)
        fstp      %st(3)
        fstp      %st(1)
        fstp      %st(0)
        fldt      32(%esp)
        movl      28(%esp), %ebx
        fstpt     12(%esp)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     56(%esp)
        fldl      104(%esp)
        jmp       ..B1.34
..B1.46:
        fstp      %st(0)
        fldt      _val_36_0L@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.50
..B1.47:
        fstp      %st(0)
        addl      $-16, %esp
        fstpt     (%esp)
        fldt      (%esp)
        fstpl     120(%esp)
        movl      %edx, 52(%esp)
        call      __y0l@PLT
..B1.80:
        fldl      120(%esp)
        movl      52(%esp), %edx
        addl      $16, %esp
..B1.48:
        fxch      %st(1)
        fstpt     56(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        fldt      (%esp)
        fstpl     120(%esp)
        movl      %edx, 52(%esp)
        call      __y1l@PLT
..B1.81:
        fldl      120(%esp)
        movl      52(%esp), %edx
        addl      $16, %esp
..B1.49:
        fxch      %st(1)
        jmp       ..B1.52
..B1.50:
        fldl      _ones@GOTOFF(%ebx)
        fdiv      %st(2), %st
        fmul      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fxch      %st(1)
        fstpt     24(%esp)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      _PP0@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      12+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _PP1@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      12+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      24+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      36+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      36+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      48+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      60+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      48+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      60+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      72+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      84+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      96+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      84+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      96+_PP1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      108+_PP0@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     40(%esp)
        addl      $-32, %esp
        lea       32(%esp), %eax
        lea       44(%esp), %ecx
        fxch      %st(3)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      108+_PP1@GOTOFF(%ebx)
        movl      %eax, 12(%esp)
        faddp     %st, %st(4)
        fldt      _QP0@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      12+_QP0@GOTOFF(%ebx)
        movl      %ecx, 16(%esp)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      _QP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldt      12+_QP1@GOTOFF(%ebx)
        movl      $-1, 20(%esp)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fxch      %st(5)
        fstpt     88(%eax)
        fxch      %st(1)
        fstpl     104(%eax)
        fldt      24+_QP0@GOTOFF(%ebx)
        movl      %edx, 36(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fldt      36+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      48+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      60+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      72+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      84+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      96+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fldt      36+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      48+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      60+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      72+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      84+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      96+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fmulp     %st, %st(3)
        fldt      108+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     56(%eax)
        fldt      108+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     72(%eax)
        call      __libm_sincos_pi4l
..B1.82:
        fldl      136(%esp)
        fldt      120(%esp)
        fldt      104(%esp)
        fldt      88(%esp)
        movl      68(%esp), %edx
        addl      $32, %esp
..B1.51:
        fldt      _tonpi@GOTOFF(%ebx)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fsqrt     
        fldt      (%esp)
        fldt      12(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(5)
        fldt      40(%esp)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     56(%esp)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(1)
..B1.52:
        fldt      _val_2_0L@GOTOFF(%ebx)
        cmpl      $1, %edx
        fld       %st(0)
        jle       ..B1.56
..B1.53:
        fldt      56(%esp)
        decl      %edx
        xorl      %eax, %eax
..B1.54:
        fld       %st(1)
        incl      %eax
        fdiv      %st(5), %st
        cmpl      %edx, %eax
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(2)
        fsubp     %st, %st(1)
        fld       %st(3)
        jae       ..B1.55
..B1.84:
        fstp      %st(4)
        fxch      %st(3)
        jmp       ..B1.54
..B1.55:
        fstp      %st(5)
        fstp      %st(4)
        fstp      %st(0)
        fstp      %st(0)
        fxch      %st(1)
        fstpt     12(%esp)
        fstpt     56(%esp)
        jmp       ..B1.36
..B1.56:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        fstpt     12(%esp)
        jmp       ..B1.36
..B1.57:
        xorl      %esi, %esi
        jmp       ..B1.21
..B1.58:
        testl     %ecx, %ecx
        jne       ..B1.65
..B1.59:
        testl     $1048575, 16(%ebp)
        jne       ..B1.65
..B1.60:
        cmpl      $1, 12(%ebp)
        jne       ..B1.65
..B1.61:
        fstp      %st(0)
        fldl      _large_value_64@GOTOFF(%ebx)
        testl     %edi, %edi
        jl        ..B1.63
..B1.62:
        fldl      8+_large_value_64@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.63:
        fld       %st(0)
        fmulp     %st, %st(1)
..B1.64:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.65:
        addl      $-16, %esp
        fstpl     (%esp)
        call      y1@PLT
..B1.83:
        addl      $16, %esp
        testl     %edi, %edi
        jge       ..B1.68
..B1.67:
        fchs      
..B1.68:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.69:
        testl     $1048575, 16(%ebp)
        jne       ..B1.74
..B1.70:
        cmpl      $0, 12(%ebp)
        jne       ..B1.74
..B1.71:
        movzbl    19(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.75
..B1.72:
        movl      %edx, %eax
        shrl      $31, %eax
        andl      %edx, %eax
        fldl      _zeros@GOTOFF(%ebx,%eax,8)
..B1.73:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.74:
        fldl      12(%ebp)
        fmull     _ones@GOTOFF(%ebx)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.75:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	yn,@function
	.size	yn,.-yn
	.data
# -- End  yn
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x40adc000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
XINF:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x33333333,0x3fd33333
	.long	0xcccccccd,0x3ff4cccc
	.long	0x33333333,0x400b3333
	.long	0x33333333,0x401b3333
	.long	0x33333333,0x40273333
	.long	0xcccccccd,0x4031cccc
	.long	0x66666666,0x40396666
	.long	0x9999999a,0x40411999
	.long	0x9999999a,0x40461999
	.long	0x66666666,0x404ba666
	.long	0x9999999a,0x4050d999
	.long	0x00000000,0x40542000
	.long	0x33333333,0x40579333
	.long	0x9999999a,0x405b3999
	.long	0x00000000,0x405f0000
	.long	0x00000000,0x40618000
	.long	0x00000000,0x40638000
	.long	0x00000000,0x4065a000
	.long	0x00000000,0x4067e000
	.long	0x00000000,0x406a2000
	.long	0x00000000,0x406c8000
	.long	0x00000000,0x406ee000
	.long	0x00000000,0x4070a000
	.long	0x00000000,0x4071e000
	.long	0x00000000,0x40732000
	.long	0x00000000,0x40746000
	.long	0x00000000,0x4075b000
	.long	0x00000000,0x40770000
	.long	0x00000000,0x40785000
	.long	0x00000000,0x4079a000
	.long	0x00000000,0x407b0000
	.long	0x00000000,0x407c6000
	.long	0x00000000,0x407dc000
	.long	0x00000000,0x407f3000
	.long	0x00000000,0x40804800
	.long	0x00000000,0x40810000
	.long	0x00000000,0x4081b800
	.long	0x00000000,0x40827000
	.long	0x00000000,0x40832800
	.long	0x00000000,0x4083e000
	.long	0x00000000,0x4084a000
	.long	0x00000000,0x40856000
	.long	0x00000000,0x40861800
	.long	0x00000000,0x4086d800
	.long	0x00000000,0x4087a000
	.long	0x00000000,0x40886000
	.long	0x00000000,0x40892000
	.long	0x00000000,0x4089e800
	.long	0x00000000,0x408aa800
	.long	0x00000000,0x408b7000
	.long	0x00000000,0x408c3000
	.long	0x00000000,0x408cf800
	.long	0x00000000,0x408dc000
	.long	0x00000000,0x408e8800
	.long	0x00000000,0x408f5800
	.long	0x00000000,0x40901000
	.long	0x00000000,0x40907400
	.long	0x00000000,0x4090d800
	.long	0x00000000,0x40914000
	.long	0x00000000,0x4091a800
	.long	0x00000000,0x40920c00
	.long	0x00000000,0x40927400
	.long	0x00000000,0x4092dc00
	.long	0x00000000,0x40934400
	.long	0x00000000,0x4093a800
	.long	0x00000000,0x40941000
	.long	0x00000000,0x40947800
	.long	0x00000000,0x4094e400
	.long	0x00000000,0x40954c00
	.long	0x00000000,0x4095b400
	.long	0x00000000,0x40961c00
	.long	0x00000000,0x40968800
	.long	0x00000000,0x4096f000
	.long	0x00000000,0x40975c00
	.long	0x00000000,0x4097c400
	.long	0x00000000,0x40983000
	.long	0x00000000,0x40989800
	.long	0x00000000,0x40990400
	.long	0x00000000,0x40997000
	.long	0x00000000,0x4099d800
	.long	0x00000000,0x409a4400
	.long	0x00000000,0x409ab000
	.long	0x00000000,0x409b1c00
	.long	0x00000000,0x409b8800
	.long	0x00000000,0x409bf400
	.long	0x00000000,0x409c6000
	.long	0x00000000,0x409ccc00
	.long	0x00000000,0x409d3800
	.long	0x00000000,0x409da400
	.long	0x00000000,0x409e1000
	.long	0x00000000,0x409e8000
	.long	0x00000000,0x409eec00
	.long	0x00000000,0x409f5800
	.long	0x00000000,0x409fc800
	.long	0x00000000,0x40a01a00
	.long	0x00000000,0x40a05000
	.long	0x00000000,0x40a08800
	.long	0x00000000,0x40a0be00
	.long	0x00000000,0x40a0f600
	.long	0x00000000,0x40a12c00
	.long	0x00000000,0x40a16400
	.long	0x00000000,0x40a19a00
	.long	0x00000000,0x40a1d200
	.long	0x00000000,0x40a20a00
	.long	0x00000000,0x40a24000
	.long	0x00000000,0x40a27800
	.long	0x00000000,0x40a2b000
	.long	0x00000000,0x40a2e800
	.long	0x00000000,0x40a31e00
	.long	0x00000000,0x40a35600
	.long	0x00000000,0x40a38e00
	.long	0x00000000,0x40a3c600
	.long	0x00000000,0x40a3fe00
	.long	0x00000000,0x40a43600
	.long	0x00000000,0x40a46e00
	.long	0x00000000,0x40a4a600
	.long	0x00000000,0x40a4de00
	.long	0x00000000,0x40a51600
	.long	0x00000000,0x40a54e00
	.long	0x00000000,0x40a58600
	.long	0x00000000,0x40a5be00
	.long	0x00000000,0x40a5f600
	.long	0x00000000,0x40a62e00
	.long	0x00000000,0x40a66600
	.long	0x00000000,0x40a6a000
	.long	0x00000000,0x40a6d800
	.long	0x00000000,0x40a71000
	.long	0x00000000,0x40a74800
	.long	0x00000000,0x40a78200
	.long	0x00000000,0x40a7ba00
	.long	0x00000000,0x40a7f200
	.long	0x00000000,0x40a82c00
	.long	0x00000000,0x40a86400
	.long	0x00000000,0x40a89c00
	.long	0x00000000,0x40a8d600
	.long	0x00000000,0x40a90e00
	.long	0x00000000,0x40a94600
	.long	0x00000000,0x40a98000
	.long	0x00000000,0x40a9b800
	.long	0x00000000,0x40a9f200
	.long	0x00000000,0x40aa2a00
	.long	0x00000000,0x40aa6400
	.long	0x00000000,0x40aa9c00
	.long	0x00000000,0x40aad600
	.long	0x00000000,0x40ab1000
	.long	0x00000000,0x40ab4800
	.long	0x00000000,0x40ab8200
	.long	0x00000000,0x40abba00
	.long	0x00000000,0x40abf400
	.long	0x00000000,0x40ac2e00
	.long	0x00000000,0x40ac6600
	.long	0x00000000,0x40aca000
	.long	0x00000000,0x40acda00
	.long	0x00000000,0x40ad1200
	.long	0x00000000,0x40ad4c00
	.long	0x00000000,0x40ad8600
	.long	0x00000000,0x40adc000
	.type	XINF,@object
	.size	XINF,1288
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 2
_val_20_0L:
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16387
	.word	0
	.type	_val_20_0L,@object
	.size	_val_20_0L,12
	.align 2
_val_1000_0L:
	.word	0
	.word	0
	.word	0
	.word	64000
	.word	16392
	.word	0
	.type	_val_1000_0L,@object
	.size	_val_1000_0L,12
	.align 2
_val_4_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16385
	.word	0
	.type	_val_4_0L,@object
	.size	_val_4_0L,12
	.align 2
_val_8_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16386
	.word	0
	.type	_val_8_0L,@object
	.size	_val_8_0L,12
	.align 2
_val_p1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.type	_val_p1_0L,@object
	.size	_val_p1_0L,12
	.align 2
_val_n1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.type	_val_n1_0L,@object
	.size	_val_n1_0L,12
	.align 2
_val_2_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.type	_val_2_0L,@object
	.size	_val_2_0L,12
	.align 2
_val_3_0L:
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16384
	.word	0
	.type	_val_3_0L,@object
	.size	_val_3_0L,12
	.align 2
_val_5_0L:
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16385
	.word	0
	.type	_val_5_0L,@object
	.size	_val_5_0L,12
	.align 2
_val_0_0L:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_val_0_0L,@object
	.size	_val_0_0L,12
	.align 2
_tonpi:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16382
	.word	0
	.type	_tonpi,@object
	.size	_tonpi,12
	.align 2
_val_36_0L:
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16388
	.word	0
	.type	_val_36_0L,@object
	.size	_val_36_0L,12
	.align 2
_PP0:
	.word	3555
	.word	4199
	.word	61716
	.word	36505
	.word	49086
	.word	0
	.word	21640
	.word	56203
	.word	47336
	.word	57608
	.word	16322
	.word	0
	.word	3059
	.word	27554
	.word	42044
	.word	45622
	.word	49095
	.word	0
	.word	55815
	.word	35656
	.word	54748
	.word	47307
	.word	16332
	.word	0
	.word	42025
	.word	6728
	.word	28939
	.word	34692
	.word	49106
	.word	0
	.word	58943
	.word	11189
	.word	14960
	.word	38786
	.word	16344
	.word	0
	.word	43526
	.word	20005
	.word	29826
	.word	37014
	.word	49119
	.word	0
	.word	58976
	.word	17109
	.word	36079
	.word	36708
	.word	16359
	.word	0
	.word	58243
	.word	36408
	.word	14563
	.word	58254
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.type	_PP0,@object
	.size	_PP0,120
	.align 2
_PP1:
	.word	52008
	.word	23245
	.word	2457
	.word	38658
	.word	16318
	.word	0
	.word	61876
	.word	57278
	.word	25234
	.word	61346
	.word	49090
	.word	0
	.word	33834
	.word	23040
	.word	39409
	.word	49003
	.word	16327
	.word	0
	.word	54525
	.word	6554
	.word	40935
	.word	51421
	.word	49100
	.word	0
	.word	9848
	.word	9769
	.word	18248
	.word	38344
	.word	16338
	.word	0
	.word	45790
	.word	60252
	.word	47538
	.word	43957
	.word	49112
	.word	0
	.word	27022
	.word	41654
	.word	23333
	.word	43744
	.word	16351
	.word	0
	.word	12816
	.word	40723
	.word	46387
	.word	47196
	.word	49127
	.word	0
	.word	4849
	.word	19418
	.word	12136
	.word	48545
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.type	_PP1,@object
	.size	_PP1,120
	.align 2
_QP0:
	.word	30466
	.word	14887
	.word	33885
	.word	35327
	.word	16316
	.word	0
	.word	41139
	.word	65374
	.word	59488
	.word	50915
	.word	49088
	.word	0
	.word	35675
	.word	30946
	.word	22284
	.word	35505
	.word	16325
	.word	0
	.word	65216
	.word	29458
	.word	42913
	.word	63176
	.word	49097
	.word	0
	.word	5130
	.word	24339
	.word	48915
	.word	38634
	.word	16335
	.word	0
	.word	30040
	.word	27658
	.word	23448
	.word	34596
	.word	49109
	.word	0
	.word	37816
	.word	17431
	.word	24401
	.word	49646
	.word	16347
	.word	0
	.word	4377
	.word	35057
	.word	45578
	.word	33037
	.word	49123
	.word	0
	.word	19863
	.word	65266
	.word	6202
	.word	53939
	.word	16363
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	49142
	.word	0
	.type	_QP0,@object
	.size	_QP0,120
	.align 2
_QP1:
	.word	65402
	.word	6822
	.word	45128
	.word	37300
	.word	49084
	.word	0
	.word	41532
	.word	52948
	.word	50360
	.word	54021
	.word	16320
	.word	0
	.word	21320
	.word	30785
	.word	26870
	.word	37955
	.word	49093
	.word	0
	.word	55382
	.word	38611
	.word	29746
	.word	34115
	.word	16330
	.word	0
	.word	37194
	.word	6554
	.word	16235
	.word	42314
	.word	49103
	.word	0
	.word	17886
	.word	15840
	.word	33918
	.word	38666
	.word	16341
	.word	0
	.word	62905
	.word	55876
	.word	18072
	.word	57284
	.word	49115
	.word	0
	.word	28423
	.word	64693
	.word	26579
	.word	40379
	.word	16355
	.word	0
	.word	40135
	.word	58793
	.word	24002
	.word	37757
	.word	49132
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16376
	.word	0
	.type	_QP1,@object
	.size	_QP1,120
	.data
	.hidden __libm_sincos_pi4l
	.section .note.GNU-stack, ""
# End
