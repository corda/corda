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
	.file "jn.c"
	.text
..TXTST0:
# -- Begin  jn
	.text
       .align    16,0x90
	.globl jn
jn:
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
        fnstcw    102(%esp)
..B1.3:
        movzwl    18(%ebp), %eax
        andl      $32752, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $32752, %eax
        je        ..B1.75
..B1.4:
        testl     %edx, %edx
        je        ..B1.51
..B1.5:
        fldl      12(%ebp)
        fldl      _zeros@GOTOFF(%ebx)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.6
        je        ..B1.79
..B1.6:
        testl     %edx, %edx
        jl        ..B1.74
..B1.7:
        movl      $1, %edi
..B1.8:
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.10
..B1.9:
        movl      %edi, %eax
        fxch      %st(1)
        fchs      
        negl      %eax
        testb     $1, %dl
        fstl      12(%ebp)
        je        ..L3
        movl      %eax, %edi
..L3:
        fxch      %st(1)
..B1.10:
        cmpl      $1, %edx
        je        ..B1.71
..B1.11:
        movl      %edx, %eax
        sarl      $5, %eax
        cmpl      $160, %eax
        jg        ..B1.13
..B1.12:
        fldl      XZERO@GOTOFF(%ebx,%eax,8)
        jmp       ..B1.14
..B1.13:
        lea       -640(,%eax,4), %eax
        lea       (,%eax,8), %ecx
        subl      %eax, %ecx
        movl      %ecx, 80(%esp)
        fildl     80(%esp)
        faddl     .L_2il0floatpacket.0@GOTOFF(%ebx)
..B1.14:
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        ja        ..B1.50
..B1.15:
        fstp      %st(0)
        movzwl    102(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.70
..B1.16:
        fstp      %st(0)
        orl       $-64768, %ecx
        movw      %cx, 100(%esp)
..B1.17:
        fldcw     100(%esp)
..B1.18:
        fldl      12(%ebp)
        movl      $1, %esi
..B1.19:
        movl      %edx, 80(%esp)
        cmpl      $1753, %edx
        fildl     80(%esp)
        jg        ..B1.28
..B1.20:
        fldt      val_0_4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B1.28
..B1.21:
        fstp      %st(0)
        fldt      _val_2_0L@GOTOFF(%ebx)
        testl     %edx, %edx
        fdivrp    %st, %st(1)
        fldt      _val_1_0L@GOTOFF(%ebx)
        fld       %st(0)
        fld       %st(0)
        fld       %st(2)
        fld       %st(3)
        jle       ..B1.24
..B1.22:
        xorl      %eax, %eax
..B1.23:
        incl      %eax
        fmul      %st(5), %st
        cmpl      %edx, %eax
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fxch      %st(3)
        fxch      %st(1)
        jb        ..B1.23
..B1.24:
        fstp      %st(3)
..B1.25:
        fxch      %st(4)
        fmul      %st(0), %st
        xorl      %ecx, %ecx
        fchs      
..B1.26:
        incl      %ecx
        incl      %edx
        movl      %ecx, %ebx
        fld       %st(3)
        imull     %edx, %ebx
        fxch      %st(1)
        fmul      %st, %st(2)
        movl      %ebx, 80(%esp)
        fildl     80(%esp)
        fdivrp    %st, %st(3)
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(1)
        fucomp    %st(4)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        jne       ..B1.26
        jp        ..B1.26
..B1.27:
        fstp      %st(0)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fdivrp    %st, %st(1)
        fxch      %st(1)
        fstpt     56(%esp)
        jmp       ..B1.41
..B1.28:
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.47
..B1.29:
        fldt      _val_20_0L@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _val_1000_0L@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        ja        ..B1.53
..B1.30:
        fldt      _val_4_0L@GOTOFF(%ebx)
        movl      $-1, %eax
        movl      %ebx, 12(%esp)
        xorl      %ecx, %ecx
        fstpt     152(%esp)
        fldt      152(%esp)
        fld       %st(1)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     184(%esp)
        fldt      184(%esp)
        fxch      %st(1)
        fstpt     104(%esp)
        fldt      104(%esp)
        fldt      _val_8_0L@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     168(%esp)
        fldt      168(%esp)
        fldt      _val_1_0L@GOTOFF(%ebx)
        fld       %st(0)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fstpt     88(%esp)
        fldt      _val_2_0L@GOTOFF(%ebx)
        fstpt     136(%esp)
        fldt      136(%esp)
        fstpt     48(%esp)
        fldt      _val_3_0L@GOTOFF(%ebx)
        fstpt     120(%esp)
        fldt      120(%esp)
        fldt      _val_5_0L@GOTOFF(%ebx)
        movl      %eax, %ebx
        fstpt     (%esp)
        fldt      (%esp)
        fldt      120(%esp)
        fldt      48(%esp)
        fldt      16(%esp)
..B1.31:
        fldt      168(%esp)
        fld       %st(3)
        fmul      %st(4), %st
        testl     %ebx, %ebx
        fxch      %st(4)
        fstpt     120(%esp)
        fxch      %st(2)
        fstpt     48(%esp)
        fldt      48(%esp)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      184(%esp)
        fsub      %st, %st(4)
        fxch      %st(3)
        fdivrp    %st, %st(4)
        fxch      %st(6)
        fmulp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(2)
        fdivrp    %st, %st(1)
        fmul      %st(1), %st
        fldt      120(%esp)
        fldt      48(%esp)
        jle       ..B1.33
..B1.32:
        fxch      %st(3)
        faddp     %st, %st(6)
        fldt      32(%esp)
        fadd      %st(2), %st
        fstpt     32(%esp)
        jmp       ..B1.34
..B1.33:
        fxch      %st(3)
        fsubrp    %st, %st(6)
        fldt      32(%esp)
        fsub      %st(2), %st
        fstpt     32(%esp)
..B1.34:
        fldt      136(%esp)
        fadd      %st, %st(3)
        faddp     %st, %st(5)
        fldt      152(%esp)
        fadd      %st, %st(1)
        faddp     %st, %st(4)
        fld       %st(1)
        fdiv      %st(6), %st
        fabs      
        fldt      88(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.36
..B1.35:
        fstpt     88(%esp)
        movl      $1, %ecx
        jmp       ..B1.37
..B1.36:
        fstp      %st(0)
        testl     %ecx, %ecx
        jne       ..B1.52
..B1.37:
        fldt      (%esp)
        negl      %ebx
        fucomp    %st(6)
        fnstsw    %ax
        sahf      
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(5)
        jne       ..B1.31
        jp        ..B1.31
..B1.38:
        fstp      %st(4)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        fldt      72(%esp)
        movl      12(%esp), %ebx
        fstpt     (%esp)
        fstpt     16(%esp)
        fstpt     56(%esp)
        fldt      104(%esp)
..B1.39:
        addl      $-32, %esp
        addl      %edx, %edx
        negl      %edx
        lea       32(%esp), %eax
        decl      %edx
        lea       88(%esp), %ecx
        fstpt     (%esp)
        fldt      (%esp)
        movl      %eax, 12(%esp)
        movl      %ecx, 16(%esp)
        movl      %edx, 20(%esp)
        fstpt     104(%eax)
        call      __libm_sincos_pi4l
..B1.82:
        fldt      136(%esp)
        addl      $32, %esp
..B1.40:
        fldt      _tonpi@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        fsqrt     
        fldt      56(%esp)
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fldt      (%esp)
        fldt      32(%esp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(1)
..B1.41:
        testl     %edi, %edi
        jl        ..B1.43
..B1.42:
        fstpt     (%esp)
        jmp       ..B1.44
..B1.43:
        fchs      
        fstpt     (%esp)
..B1.44:
        testl     %esi, %esi
        je        ..B1.46
..B1.45:
        fldcw     102(%esp)
..B1.46:
        fldt      (%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.47:
        fstp      %st(0)
        fldt      _val_0_0L@GOTOFF(%ebx)
        lea       (%edx,%edx), %eax
        movl      %eax, 80(%esp)
        fstpt     32(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fchs      
        fstpt     64(%esp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     104(%esp)
        fldt      _val_1_0L@GOTOFF(%ebx)
        fstpt     (%esp)
        fldt      (%esp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     16(%esp)
        fildl     80(%esp)
        fld       %st(0)
        fldt      _val_2_0L@GOTOFF(%ebx)
        fstpt     48(%esp)
        fldt      (%esp)
        jmp       ..B1.48
..B1.49:
        fstp      %st(0)
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
..B1.48:
        fldt      48(%esp)
        faddp     %st, %st(2)
        fld       %st(4)
        fmul      %st(2), %st
        fldt      64(%esp)
        fmul      %st, %st(2)
        fldt      32(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(5)
        fstpt     32(%esp)
        fld       %st(4)
        fxch      %st(1)
        fdivr     %st, %st(5)
        fxch      %st(4)
        fucom     %st(5)
        fnstsw    %ax
        sahf      
        jne       ..B1.49
        jp        ..B1.49
        jmp       ..B1.63
..B1.50:
        fstp      %st(1)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.51:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      j0@PLT
..B1.83:
        addl      $260, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.52:
        fstp      %st(4)
        fstp      %st(3)
        fstp      %st(0)
        fstp      %st(0)
        fldt      72(%esp)
        movl      12(%esp), %ebx
        fstpt     (%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fstpt     56(%esp)
        fldt      104(%esp)
        jmp       ..B1.39
..B1.53:
        fstp      %st(0)
        fldt      _val_36_0L@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.57
..B1.54:
        fstp      %st(0)
        addl      $-16, %esp
        fstpt     (%esp)
        fldt      (%esp)
        movl      %edx, 52(%esp)
        fstpt     120(%esp)
        call      __j0l@PLT
..B1.84:
        fldt      120(%esp)
        movl      52(%esp), %edx
        addl      $16, %esp
..B1.55:
        fxch      %st(1)
        fstpt     56(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        fldt      (%esp)
        movl      %edx, 52(%esp)
        fstpt     120(%esp)
        call      __j1l@PLT
..B1.85:
        fldt      120(%esp)
        movl      52(%esp), %edx
        addl      $16, %esp
..B1.56:
        fxch      %st(1)
        jmp       ..B1.59
..B1.57:
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
        lea       44(%esp), %eax
        lea       32(%esp), %ecx
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
        fstpt     60(%eax)
        movl      %edx, 24(%eax)
        fxch      %st(1)
        fstpt     92(%eax)
        fldt      24+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      60+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      84+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      96+_QP0@GOTOFF(%ebx)
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
        fmulp     %st, %st(3)
        fldt      108+_QP0@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     44(%eax)
        fldt      108+_QP1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     76(%eax)
        call      __libm_sincos_pi4l
..B1.86:
        fldt      136(%esp)
        fldt      120(%esp)
        fldt      104(%esp)
        fldt      88(%esp)
        movl      68(%esp), %edx
        addl      $32, %esp
..B1.58:
        fldt      _tonpi@GOTOFF(%ebx)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fsqrt     
        fldt      (%esp)
        fldt      12(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(4)
        fldt      40(%esp)
        fmul      %st(1), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     56(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.59:
        fldt      _val_2_0L@GOTOFF(%ebx)
        cmpl      $1, %edx
        fld       %st(0)
        jle       ..B1.91
..B1.60:
        fldt      56(%esp)
        decl      %edx
        xorl      %eax, %eax
..B1.61:
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
        jae       ..B1.62
..B1.92:
        fstp      %st(4)
        fxch      %st(3)
        jmp       ..B1.61
..B1.62:
        fstp      %st(0)
        fstp      %st(1)
        fstp      %st(3)
        fstp      %st(0)
        fstpt     56(%esp)
        jmp       ..B1.41
..B1.63:
        fstp      %st(1)
        fstp      %st(3)
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(1)
        fldt      48(%esp)
        decl      %edx
        fldt      16(%esp)
        xorl      %eax, %eax
        movl      %edx, 80(%esp)
        fld       %st(0)
        fxch      %st(1)
        fdiv      %st, %st(3)
        fildl     80(%esp)
        fmul      %st(3), %st
        fldt      104(%esp)
        fdivr     %st, %st(3)
..B1.64:
        fld       %st(5)
        incl      %eax
        fmul      %st(2), %st
        fxch      %st(2)
        fsub      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        cmpl      %edx, %eax
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(5)
        fld       %st(1)
        jge       ..B1.65
..B1.93:
        fstp      %st(7)
        fstp      %st(1)
        fxch      %st(2)
        jmp       ..B1.64
..B1.65:
        fstp      %st(0)
        fstp      %st(4)
        fstp      %st(4)
        fstp      %st(0)
        fxch      %st(2)
        fstpt     56(%esp)
        fldt      56(%esp)
        fxch      %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fcompp    
        fnstsw    %ax
        fxch      %st(1)
        fstpt     (%esp)
        sahf      
        jae       ..B1.68
        jp        ..B1.68
..B1.66:
        addl      $-16, %esp
        fstpt     (%esp)
        call      __j1l@PLT
..B1.87:
        addl      $16, %esp
..B1.67:
        fldt      (%esp)
        fdivrp    %st, %st(1)
        jmp       ..B1.41
..B1.68:
        addl      $-16, %esp
        fstpt     (%esp)
        call      __j0l@PLT
..B1.88:
        addl      $16, %esp
..B1.69:
        fldt      56(%esp)
        fdivrp    %st, %st(1)
        jmp       ..B1.41
..B1.70:
        xorl      %esi, %esi
        jmp       ..B1.19
..B1.71:
        fstp      %st(0)
        addl      $-16, %esp
        fstpl     (%esp)
        call      j1@PLT
..B1.89:
        addl      $16, %esp
        testl     %edi, %edi
        jge       ..B1.73
..B1.72:
        fchs      
..B1.73:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.74:
        movl      %edx, %eax
        negl      %edx
        notl      %eax
        andl      $1, %eax
        lea       -1(%eax,%eax), %edi
        jmp       ..B1.8
..B1.75:
        testl     $1048575, 16(%ebp)
        jne       ..B1.77
..B1.76:
        cmpl      $0, 12(%ebp)
        je        ..B1.78
..B1.77:
        fldl      12(%ebp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        movzbl    19(%ebp), %ecx
        movl      %edx, %eax
        andl      $128, %ecx
        shrl      $7, %ecx
        shrl      $31, %eax
        xorl      %eax, %ecx
        andl      %edx, %ecx
        fldl      _zeros@GOTOFF(%ebx,%ecx,8)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.79:
        fstp      %st(0)
        fstp      %st(0)
        movzbl    19(%ebp), %ecx
        movl      %edx, %eax
        andl      $128, %ecx
        shrl      $7, %ecx
        shrl      $31, %eax
        xorl      %eax, %ecx
        andl      %edx, %ecx
        fldl      _zeros@GOTOFF(%ebx,%ecx,8)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.91:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(1)
        jmp       ..B1.41
        .align    16,0x90
	.type	jn,@function
	.size	jn,.-jn
	.data
# -- End  jn
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x40add600
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
XZERO:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.long	0x33333333,0x3fd33333
	.long	0x66666666,0x3ff66666
	.long	0x00000000,0x400c0000
	.long	0x00000000,0x401c0000
	.long	0x00000000,0x40280000
	.long	0xcccccccd,0x40324ccc
	.long	0x00000000,0x403a0000
	.long	0x00000000,0x40418000
	.long	0x9999999a,0x40469999
	.long	0x00000000,0x404c4000
	.long	0x33333333,0x40513333
	.long	0x00000000,0x40548000
	.long	0x00000000,0x40580000
	.long	0x33333333,0x405bb333
	.long	0xcccccccd,0x405f8ccc
	.long	0xcccccccd,0x4061cccc
	.long	0x33333333,0x4063e333
	.long	0xcccccccd,0x40660ccc
	.long	0x33333333,0x40684333
	.long	0xcccccccd,0x406a8ccc
	.long	0x33333333,0x406ce333
	.long	0x66666666,0x406f4666
	.long	0x9999999a,0x4070d999
	.long	0x00000000,0x40721800
	.long	0x9999999a,0x40735999
	.long	0x9999999a,0x4074a199
	.long	0x66666666,0x4075ee66
	.long	0x00000000,0x40774000
	.long	0xcccccccd,0x407894cc
	.long	0x66666666,0x4079ee66
	.long	0x33333333,0x407b4b33
	.long	0x33333333,0x407cab33
	.long	0x00000000,0x407e1000
	.long	0x66666666,0x407f7666
	.long	0xcccccccd,0x408070cc
	.long	0x33333333,0x40812733
	.long	0x33333333,0x4081df33
	.long	0xcccccccd,0x408298cc
	.long	0x33333333,0x40835333
	.long	0x33333333,0x40840f33
	.long	0xcccccccd,0x4084cccc
	.long	0x66666666,0x40858a66
	.long	0x66666666,0x40864a66
	.long	0x66666666,0x40870a66
	.long	0x00000000,0x4087cc00
	.long	0x66666666,0x40888e66
	.long	0x9999999a,0x40895199
	.long	0x9999999a,0x408a1599
	.long	0x33333333,0x408adb33
	.long	0xcccccccd,0x408ba0cc
	.long	0x00000000,0x408c6000
	.long	0x00000000,0x408d2800
	.long	0x00000000,0x408df000
	.long	0x00000000,0x408ec000
	.long	0x00000000,0x408f8800
	.long	0x00000000,0x40902800
	.long	0x00000000,0x40909000
	.long	0x00000000,0x4090f400
	.long	0x00000000,0x40915c00
	.long	0x00000000,0x4091c000
	.long	0x00000000,0x40922800
	.long	0x00000000,0x40929000
	.long	0x00000000,0x4092f800
	.long	0x00000000,0x40935c00
	.long	0x00000000,0x4093c400
	.long	0x00000000,0x40943000
	.long	0x00000000,0x40949800
	.long	0x00000000,0x40950000
	.long	0x00000000,0x40956800
	.long	0x00000000,0x4095d000
	.long	0x00000000,0x40963c00
	.long	0x00000000,0x4096a400
	.long	0x00000000,0x40971000
	.long	0x00000000,0x40977800
	.long	0x00000000,0x4097e400
	.long	0x00000000,0x40984c00
	.long	0x00000000,0x4098b800
	.long	0x00000000,0x40992400
	.long	0x00000000,0x40999000
	.long	0x00000000,0x4099f800
	.long	0x00000000,0x409a6400
	.long	0x00000000,0x409ad000
	.long	0x00000000,0x409b3c00
	.long	0x00000000,0x409ba800
	.long	0x00000000,0x409c1400
	.long	0x00000000,0x409c8000
	.long	0x00000000,0x409cec00
	.long	0x00000000,0x409d5c00
	.long	0x00000000,0x409dc800
	.long	0x00000000,0x409e3400
	.long	0x00000000,0x409ea000
	.long	0x00000000,0x409f1000
	.long	0x00000000,0x409f7c00
	.long	0x00000000,0x409fe800
	.long	0x00000000,0x40a02c00
	.long	0x00000000,0x40a06200
	.long	0x00000000,0x40a09a00
	.long	0x00000000,0x40a0d000
	.long	0x00000000,0x40a10800
	.long	0x00000000,0x40a13e00
	.long	0x00000000,0x40a17600
	.long	0x00000000,0x40a1ae00
	.long	0x00000000,0x40a1e400
	.long	0x00000000,0x40a21c00
	.long	0x00000000,0x40a25400
	.long	0x00000000,0x40a28c00
	.long	0x00000000,0x40a2c200
	.long	0x00000000,0x40a2fa00
	.long	0x00000000,0x40a33200
	.long	0x00000000,0x40a36a00
	.long	0x00000000,0x40a3a200
	.long	0x00000000,0x40a3da00
	.long	0x00000000,0x40a41200
	.long	0x00000000,0x40a44a00
	.long	0x00000000,0x40a48200
	.long	0x00000000,0x40a4ba00
	.long	0x00000000,0x40a4f200
	.long	0x00000000,0x40a52a00
	.long	0x00000000,0x40a56200
	.long	0x00000000,0x40a59a00
	.long	0x00000000,0x40a5d200
	.long	0x00000000,0x40a60a00
	.long	0x00000000,0x40a64400
	.long	0x00000000,0x40a67c00
	.long	0x00000000,0x40a6b400
	.long	0x00000000,0x40a6ec00
	.long	0x00000000,0x40a72600
	.long	0x00000000,0x40a75e00
	.long	0x00000000,0x40a79600
	.long	0x00000000,0x40a7d000
	.long	0x00000000,0x40a80800
	.long	0x00000000,0x40a84000
	.long	0x00000000,0x40a87a00
	.long	0x00000000,0x40a8b200
	.long	0x00000000,0x40a8ec00
	.long	0x00000000,0x40a92400
	.long	0x00000000,0x40a95c00
	.long	0x00000000,0x40a99600
	.long	0x00000000,0x40a9ce00
	.long	0x00000000,0x40aa0800
	.long	0x00000000,0x40aa4200
	.long	0x00000000,0x40aa7a00
	.long	0x00000000,0x40aab400
	.long	0x00000000,0x40aaec00
	.long	0x00000000,0x40ab2600
	.long	0x00000000,0x40ab6000
	.long	0x00000000,0x40ab9800
	.long	0x00000000,0x40abd200
	.long	0x00000000,0x40ac0a00
	.long	0x00000000,0x40ac4400
	.long	0x00000000,0x40ac7e00
	.long	0x00000000,0x40acb800
	.long	0x00000000,0x40acf000
	.long	0x00000000,0x40ad2a00
	.long	0x00000000,0x40ad6400
	.long	0x00000000,0x40ad9e00
	.long	0x00000000,0x40add600
	.type	XZERO,@object
	.size	XZERO,1288
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
val_0_4:
	.byte	0x00,0xd0,0xcc,0xcc,0xcc,0xcc,0xcc,0xcc,0xfd,0x3f,0x00,0x00
	.type	val_0_4,@object
	.size	val_0_4,12
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
_val_1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.type	_val_1_0L,@object
	.size	_val_1_0L,12
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
