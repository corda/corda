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
	.file "jnf.c"
	.text
..TXTST0:
# -- Begin  jnf
	.text
       .align    16,0x90
	.globl jnf
jnf:
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
        fldz      
        movl      12(%ebp), %edi
        movl      %edi, %edx
        andl      $2147483647, %edx
        movl      8(%ebp), %esi
        cmpl      $2139095040, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jae       ..B1.41
..B1.2:
        testl     %esi, %esi
        je        ..B1.39
..B1.3:
        xorl      %esi, %edi
        shrl      $31, %edi
        andl      %esi, %edi
        testl     %edx, %edx
        je        ..B1.38
..B1.4:
        flds      12(%ebp)
        movl      %esi, %eax
        cltd      
        xorl      %edx, %esi
        fabs      
        subl      %edx, %esi
        fsts      12(%ebp)
        cmpl      $1, %esi
        je        ..B1.61
..B1.5:
        movl      %esi, %edx
        sarl      $3, %edx
        cmpl      $320, %edx
        jg        ..B1.7
..B1.6:
        flds      function_zeros@GOTOFF(%ebx,%edx,4)
        jmp       ..B1.8
..B1.7:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        lea       -320(%edx), %ecx
        lea       -2560(,%edx,8), %edx
        subl      %ecx, %edx
        movl      %edx, 168(%esp)
        fildl     168(%esp)
        fstps     168(%esp)
        flds      168(%esp)
        faddp     %st, %st(1)
        fstps     168(%esp)
        flds      168(%esp)
..B1.8:
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.37
..B1.9:
        movl      %esi, 168(%esp)
        cmpl      $170, %esi
        fildl     168(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        jg        ..B1.18
..B1.10:
        fldl      _val_0_6@GOTOFF(%ebx)
        fmul      %st(1), %st
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jb        ..B1.18
..B1.11:
        fstp      %st(2)
        fstp      %st(1)
        fldl      _val_2_0@GOTOFF(%ebx)
        testl     %esi, %esi
        fdivrp    %st, %st(1)
        fstpl     192(%esp)
        fld1      
        fld       %st(0)
        fld       %st(1)
        fldl      192(%esp)
        jle       ..B1.15
..B1.12:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fld       %st(2)
        xorl      %edx, %edx
        .align    16,0x90
..B1.13:
        fxch      %st(2)
        fmul      %st, %st(3)
        incl      %edx
        fxch      %st(3)
        fstpl     192(%esp)
        cmpl      %esi, %edx
        fxch      %st(1)
        fmul      %st, %st(3)
        fadd      %st(1), %st
        fldl      192(%esp)
        fxch      %st(4)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(4)
        jb        ..B1.13
..B1.14:
        fstp      %st(1)
        fstp      %st(0)
..B1.15:
        fldl      _TWO_19H@GOTOFF(%ebx)
        xorl      %edx, %edx
        fxch      %st(1)
        fmul      %st(0), %st
        fchs      
        fstpl     192(%esp)
        fxch      %st(3)
        fstl      176(%esp)
        fstl      56(%esp)
        fldl      192(%esp)
..B1.16:
        fldl      176(%esp)
        incl      %edx
        incl      %esi
        movl      %edx, %ecx
        imull     %esi, %ecx
        fmul      %st(1), %st
        fldl      56(%esp)
        movl      %ecx, 168(%esp)
        fildl     168(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fstl      176(%esp)
        faddp     %st, %st(1)
        fstpl     192(%esp)
        fld       %st(4)
        fldl      192(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpl     192(%esp)
        fstl      56(%esp)
        fldl      192(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     184(%esp)
        fldl      184(%esp)
        fsubp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(2)
        fucomp    %st(2)
        fnstsw    %ax
        sahf      
        jne       ..B1.16
        jp        ..B1.16
..B1.17:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(2)
        fxch      %st(1)
        fmull     56(%esp)
        fdivp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        jmp       ..B1.30
..B1.18:
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.34
..B1.19:
        lea       (%esi,%esi,4), %edx
        lea       1000(,%edx,4), %ecx
        movl      %ecx, 168(%esp)
        fildl     168(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        ja        ..B1.47
..B1.20:
        fstp      %st(2)
        fldl      _val_4_0@GOTOFF(%ebx)
        movl      $-1, %ecx
        fstl      152(%esp)
        fld       %st(2)
        fmulp     %st, %st(1)
        xorl      %edx, %edx
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     192(%esp)
        fstpt     112(%esp)
        fldt      112(%esp)
        fldl      192(%esp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     128(%esp)
        fldl      _val_8_0@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fstpt     80(%esp)
        fldt      80(%esp)
        fsubrp    %st, %st(2)
        fdivrp    %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fstpt     64(%esp)
        fldt      64(%esp)
        fstpl     176(%esp)
        fldl      _val_2_0@GOTOFF(%ebx)
        fstl      144(%esp)
        fldl      _val_3_0@GOTOFF(%ebx)
        fstpt     48(%esp)
        fldt      48(%esp)
        fldl      _val_5_0@GOTOFF(%ebx)
        fstpt     32(%esp)
        fld1      
        fld       %st(0)
        fstpl     160(%esp)
        fldt      80(%esp)
        fldt      32(%esp)
        fldt      48(%esp)
..B1.21:
        fldt      96(%esp)
        fld       %st(6)
        fmul      %st(1), %st
        testl     %ecx, %ecx
        fxch      %st(1)
        fmul      %st(6), %st
        fxch      %st(5)
        fstpl     56(%esp)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(4)
        fstpt     80(%esp)
        fldt      128(%esp)
        fsub      %st, %st(4)
        fxch      %st(1)
        fdivrp    %st, %st(4)
        fld       %st(2)
        fmul      %st(3), %st
        fsubrp    %st, %st(1)
        fdivp     %st, %st(4)
        fxch      %st(2)
        fmull     176(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(3)
        fstpl     192(%esp)
        fldl      192(%esp)
        fmul      %st(3), %st
        fstpl     192(%esp)
        fldl      192(%esp)
        fldt      80(%esp)
        jle       ..B1.46
..B1.22:
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldt      64(%esp)
        fadd      %st(4), %st
        fstpl     192(%esp)
        fldl      192(%esp)
        fstpt     64(%esp)
..B1.23:
        fldl      144(%esp)
        fadd      %st, %st(6)
        fxch      %st(6)
        fstpl     192(%esp)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fstl      176(%esp)
        fdiv      %st(4), %st
        fldl      192(%esp)
        fxch      %st(4)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      152(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpl     192(%esp)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      192(%esp)
        fxch      %st(3)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fabs      
        fldl      160(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.25
..B1.24:
        fstpl     160(%esp)
        movl      $1, %edx
        jmp       ..B1.26
..B1.25:
        fstp      %st(0)
        testl     %edx, %edx
        jne       ..B1.45
..B1.26:
        fldl      _TWO_19H@GOTOFF(%ebx)
        negl      %ecx
        fmul      %st(5), %st
        fstpl     192(%esp)
        fld       %st(4)
        fldl      192(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     184(%esp)
        fldl      184(%esp)
        fsubp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      56(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(4)
        fxch      %st(5)
        fxch      %st(2)
        jne       ..B1.21
        jp        ..B1.21
..B1.27:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(1)
        fldt      112(%esp)
        fxch      %st(1)
        fstpt     80(%esp)
..B1.28:
        addl      %esi, %esi
        lea       56(%esp), %edx
        negl      %esi
        lea       176(%esp), %ecx
        fstl      (%esp)
        decl      %esi
        movl      %edx, 8(%esp)
        movl      %ecx, 12(%esp)
        movl      %esi, 16(%esp)
        fstpt     56(%edx)
        call      __libm_sincos_k32
..B1.29:
        fldt      112(%esp)
        fldt      80(%esp)
        fmull     176(%esp)
        fldl      _tonpi@GOTOFF(%ebx)
        fdivp     %st, %st(2)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fsqrt     
        fstpl     192(%esp)
        fldl      192(%esp)
        fldt      64(%esp)
        fmull     56(%esp)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
..B1.30:
        testl     %edi, %edi
        je        ..B1.33
..B1.31:
        fchs      
..B1.33:
        fstps     168(%esp)
        flds      168(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.34:
        fstp      %st(0)
        fldl      _val_2_0@GOTOFF(%ebx)
        lea       (%esi,%esi), %edx
        movl      %edx, 168(%esp)
        fstpl     88(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fchs      
        fld1      
        fstl      176(%esp)
        fld       %st(0)
        fxch      %st(3)
        fstl      48(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fxch      %st(3)
        fstl      56(%esp)
        fxch      %st(3)
        fstpt     112(%esp)
        fildl     168(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fstpt     64(%esp)
        fldl      _TWO_19H@GOTOFF(%ebx)
        fstpl     80(%esp)
        fld       %st(0)
        fldl      48(%esp)
        jmp       ..B1.35
..B1.36:
        fstp      %st(1)
        fstl      56(%esp)
        fstpl     176(%esp)
        fxch      %st(2)
        fxch      %st(3)
        fxch      %st(1)
        fxch      %st(4)
        fxch      %st(1)
..B1.35:
        fldt      64(%esp)
        fmul      %st, %st(5)
        fld       %st(1)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fld       %st(3)
        fxch      %st(3)
        faddl     88(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fmul      %st, %st(1)
        fxch      %st(6)
        faddp     %st, %st(1)
        fstpl     192(%esp)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fldl      192(%esp)
        fld       %st(0)
        fxch      %st(4)
        fstpl     192(%esp)
        fldl      192(%esp)
        fdivr     %st, %st(1)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(1)
        fmull     80(%esp)
        fstpl     192(%esp)
        fldl      192(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     184(%esp)
        fldl      184(%esp)
        fsubp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      176(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jne       ..B1.36
        jp        ..B1.36
        jmp       ..B1.57
..B1.37:
        fstp      %st(0)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        fstp      %st(0)
        flds      _zeros@GOTOFF(%ebx,%edi,4)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.39:
        fstpl     56(%esp)
        addl      $4, %esp
        pushl     12(%ebp)
        call      j0f@PLT
..B1.40:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.41:
        fstp      %st(0)
        ja        ..B1.43
..B1.42:
        xorl      %esi, %edi
        shrl      $31, %edi
        andl      %esi, %edi
        flds      _zeros@GOTOFF(%ebx,%edi,4)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        flds      12(%ebp)
..B1.44:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        fldt      112(%esp)
        fxch      %st(1)
        fstpt     80(%esp)
        jmp       ..B1.28
..B1.46:
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldt      64(%esp)
        fsub      %st(4), %st
        fstpl     192(%esp)
        fldl      192(%esp)
        fstpt     64(%esp)
        jmp       ..B1.23
..B1.47:
        fstp      %st(0)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      _val_17_0@GOTOFF(%ebx)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.51
..B1.48:
        fstl      (%esp)
        fstpt     112(%esp)
        call      j0@PLT
..B1.69:
        fldt      112(%esp)
..B1.49:
        fstl      (%esp)
        fxch      %st(1)
        fstpl     176(%esp)
        fstpt     112(%esp)
        call      j1@PLT
..B1.70:
        fldt      112(%esp)
..B1.50:
        fxch      %st(1)
        jmp       ..B1.53
..B1.51:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        lea       96(%esp), %edx
        fdiv      %st(1), %st
        lea       104(%esp), %ecx
        fstpl     96(%edx)
        fldl      96(%edx)
        fstpt     -64(%edx)
        fldt      -64(%edx)
        fldl      _val_16_0@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpl     96(%edx)
        fldl      96(%edx)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     96(%edx)
        fldl      96(%edx)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     96(%edx)
        fldl      96(%edx)
        fxch      %st(3)
        fstl      (%esp)
        movl      %edx, 8(%esp)
        movl      %ecx, 12(%esp)
        movl      $-1, 16(%esp)
        fxch      %st(1)
        fstpt     -48(%edx)
        fxch      %st(2)
        fstpt     -32(%edx)
        fstpt     -16(%edx)
        fstpt     16(%edx)
        call      __libm_sincos_k32
..B1.52:
        fldt      112(%esp)
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      48(%esp)
        fldl      40+_PP0@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldl      32+_PP0@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     24+_PP0@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     16+_PP0@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     8+_PP0@GOTOFF(%ebx)
        fmul      %st(2), %st
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      32+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     _PP0@GOTOFF(%ebx)
        fstpl     192(%esp)
        fldl      192(%esp)
        faddp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      40+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     16+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     24+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     _PP1@GOTOFF(%ebx)
        fldl      32+_QP0@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(3)
        faddl     8+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        faddp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      40+_QP0@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(3)
        faddl     16+_QP0@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(3)
        faddl     24+_QP0@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(3)
        faddl     _QP0@GOTOFF(%ebx)
        fmul      %st(6), %st
        fxch      %st(3)
        faddl     8+_QP0@GOTOFF(%ebx)
        fmul      %st(4), %st
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      40+_QP1@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddl     24+_QP1@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddl     8+_QP1@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fldl      32+_QP1@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddl     16+_QP1@GOTOFF(%ebx)
        fmulp     %st, %st(5)
        fldl      _tonpi@GOTOFF(%ebx)
        fxch      %st(5)
        faddl     _QP1@GOTOFF(%ebx)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpl     192(%esp)
        fldl      192(%esp)
        fldt      32(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpl     192(%esp)
        fldl      192(%esp)
        fsqrt     
        fstpl     192(%esp)
        fldl      192(%esp)
        fldl      96(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fldl      104(%esp)
        fmul      %st, %st(5)
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpl     176(%esp)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
..B1.53:
        fldl      _val_2_0@GOTOFF(%ebx)
        cmpl      $1, %esi
        fdivp     %st, %st(2)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        jle       ..B1.74
..B1.54:
        fldl      176(%esp)
        movl      $1, %edx
..B1.55:
        movl      %edx, 168(%esp)
        incl      %edx
        fildl     168(%esp)
        fstpl     192(%esp)
        fld       %st(2)
        fldl      192(%esp)
        cmpl      %esi, %edx
        fmul      %st(3), %st
        fmulp     %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(2)
        jl        ..B1.55
..B1.56:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B1.30
..B1.57:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(3)
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(1)
        fldl      88(%esp)
        lea       -2(%esi,%esi), %edx
        fldt      32(%esp)
        decl      %esi
        fldt      112(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fdiv      %st, %st(4)
        movl      %edx, 168(%esp)
        xorl      %edx, %edx
        fxch      %st(4)
        fstpl     192(%esp)
        fdivr     %st, %st(3)
        fildl     168(%esp)
        fldl      192(%esp)
        fxch      %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        fxch      %st(5)
        fstpl     192(%esp)
        fldl      192(%esp)
..B1.58:
        fld       %st(1)
        incl      %edx
        fmul      %st(6), %st
        fxch      %st(5)
        fsubr     %st, %st(6)
        fxch      %st(3)
        fmul      %st, %st(4)
        cmpl      %esi, %edx
        fxch      %st(4)
        fsubrp    %st, %st(5)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpl     192(%esp)
        fld       %st(0)
        fldl      192(%esp)
        fld       %st(0)
        fxch      %st(7)
        fstpl     192(%esp)
        fldl      192(%esp)
        jge       ..B1.59
..B1.75:
        fstp      %st(3)
        fstp      %st(0)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(4)
        fxch      %st(5)
        fxch      %st(1)
        fxch      %st(5)
        jmp       ..B1.58
..B1.59:
        fstp      %st(6)
        fstp      %st(3)
        fstp      %st(0)
        fstp      %st(4)
        fstp      %st(2)
        fxch      %st(1)
        fstpl     176(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fstpl     (%esp)
        call      j0@PLT
..B1.60:
        fldl      176(%esp)
        fdivrp    %st, %st(1)
        fstpl     192(%esp)
        fldl      192(%esp)
        jmp       ..B1.30
..B1.61:
        fxch      %st(1)
        fstpl     56(%esp)
        fstps     (%esp)
        call      j1f@PLT
..B1.62:
        testl     %edi, %edi
        je        ..B1.65
..B1.63:
        fchs      
..B1.65:
        fstps     168(%esp)
        flds      168(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.74:
        fstp      %st(0)
        jmp       ..B1.30
        .align    16,0x90
	.type	jnf,@function
	.size	jnf,.-jnf
	.data
# -- End  jnf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0xa0,0x8e,0x0a,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 4
function_zeros:
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x3f000000
	.long	0x3fcccccd
	.long	0x40600000
	.long	0x40c33333
	.long	0x4111999a
	.long	0x414b3333
	.long	0x4184cccd
	.long	0x41a66666
	.long	0x41ca6666
	.long	0x41f0cccd
	.long	0x420c0000
	.long	0x42206666
	.long	0x4235999a
	.long	0x424b3333
	.long	0x42613333
	.long	0x42780000
	.long	0x42876666
	.long	0x42930000
	.long	0x429ecccd
	.long	0x42aacccd
	.long	0x42b6cccd
	.long	0x42c30000
	.long	0x42cf6666
	.long	0x42dbcccd
	.long	0x42e86666
	.long	0x42f50000
	.long	0x4300e666
	.long	0x43074ccd
	.long	0x430db333
	.long	0x43143333
	.long	0x431ab333
	.long	0x43214ccd
	.long	0x4327e666
	.long	0x432e8000
	.long	0x4335199a
	.long	0x433bcccd
	.long	0x43428000
	.long	0x43493333
	.long	0x43500000
	.long	0x43560000
	.long	0x435d0000
	.long	0x43640000
	.long	0x436b0000
	.long	0x43720000
	.long	0x43780000
	.long	0x437f0000
	.long	0x43830000
	.long	0x43868000
	.long	0x438a0000
	.long	0x438d8000
	.long	0x43910000
	.long	0x43948000
	.long	0x43980000
	.long	0x439b8000
	.long	0x439f0000
	.long	0x43a28000
	.long	0x43a60000
	.long	0x43a98000
	.long	0x43ad0000
	.long	0x43b08000
	.long	0x43b40000
	.long	0x43b78000
	.long	0x43bb0000
	.long	0x43bf0000
	.long	0x43c28000
	.long	0x43c60000
	.long	0x43c98000
	.long	0x43cd0000
	.long	0x43d08000
	.long	0x43d40000
	.long	0x43d80000
	.long	0x43db8000
	.long	0x43df0000
	.long	0x43e28000
	.long	0x43e60000
	.long	0x43ea0000
	.long	0x43ed8000
	.long	0x43f10000
	.long	0x43f48000
	.long	0x43f88000
	.long	0x43fc0000
	.long	0x43ff8000
	.long	0x44018000
	.long	0x44038000
	.long	0x44054000
	.long	0x44070000
	.long	0x44090000
	.long	0x440ac000
	.long	0x440c8000
	.long	0x440e4000
	.long	0x44104000
	.long	0x44120000
	.long	0x4413c000
	.long	0x4415c000
	.long	0x44178000
	.long	0x44194000
	.long	0x441b4000
	.long	0x441d0000
	.long	0x441ec000
	.long	0x4420c000
	.long	0x44228000
	.long	0x44244000
	.long	0x44264000
	.long	0x44280000
	.long	0x442a0000
	.long	0x442bc000
	.long	0x442d8000
	.long	0x442f8000
	.long	0x44314000
	.long	0x44334000
	.long	0x44350000
	.long	0x4436c000
	.long	0x4438c000
	.long	0x443a8000
	.long	0x443c8000
	.long	0x443e4000
	.long	0x44400000
	.long	0x44420000
	.long	0x4443c000
	.long	0x4445c000
	.long	0x44478000
	.long	0x44498000
	.long	0x444b4000
	.long	0x444d0000
	.long	0x444f0000
	.long	0x4450c000
	.long	0x4452c000
	.long	0x44548000
	.long	0x44568000
	.long	0x44584000
	.long	0x445a4000
	.long	0x445c0000
	.long	0x445e0000
	.long	0x445fc000
	.long	0x4461c000
	.long	0x44638000
	.long	0x44658000
	.long	0x44674000
	.long	0x44694000
	.long	0x446b0000
	.long	0x446cc000
	.long	0x446ec000
	.long	0x44708000
	.long	0x44728000
	.long	0x44744000
	.long	0x44764000
	.long	0x44784000
	.long	0x447a0000
	.long	0x447c0000
	.long	0x447dc000
	.long	0x447fc000
	.long	0x4480c000
	.long	0x4481c000
	.long	0x4482a000
	.long	0x4483a000
	.long	0x44848000
	.long	0x44858000
	.long	0x44866000
	.long	0x44876000
	.long	0x44884000
	.long	0x44894000
	.long	0x448a2000
	.long	0x448b2000
	.long	0x448c0000
	.long	0x448d0000
	.long	0x448e0000
	.long	0x448ee000
	.long	0x448fe000
	.long	0x4490c000
	.long	0x4491c000
	.long	0x4492a000
	.long	0x4493a000
	.long	0x44948000
	.long	0x44958000
	.long	0x44968000
	.long	0x44976000
	.long	0x44986000
	.long	0x44994000
	.long	0x449a4000
	.long	0x449b2000
	.long	0x449c2000
	.long	0x449d2000
	.long	0x449e0000
	.long	0x449f0000
	.long	0x449fe000
	.long	0x44a0e000
	.long	0x44a1c000
	.long	0x44a2c000
	.long	0x44a3c000
	.long	0x44a4a000
	.long	0x44a5a000
	.long	0x44a68000
	.long	0x44a78000
	.long	0x44a88000
	.long	0x44a96000
	.long	0x44aa6000
	.long	0x44ab4000
	.long	0x44ac4000
	.long	0x44ad4000
	.long	0x44ae2000
	.long	0x44af2000
	.long	0x44b00000
	.long	0x44b10000
	.long	0x44b20000
	.long	0x44b2e000
	.long	0x44b3e000
	.long	0x44b4c000
	.long	0x44b5c000
	.long	0x44b6c000
	.long	0x44b7a000
	.long	0x44b8a000
	.long	0x44b9a000
	.long	0x44ba8000
	.long	0x44bb8000
	.long	0x44bc6000
	.long	0x44bd6000
	.long	0x44be6000
	.long	0x44bf4000
	.long	0x44c04000
	.long	0x44c14000
	.long	0x44c22000
	.long	0x44c32000
	.long	0x44c40000
	.long	0x44c50000
	.long	0x44c60000
	.long	0x44c6e000
	.long	0x44c7e000
	.long	0x44c8e000
	.long	0x44c9c000
	.long	0x44cac000
	.long	0x44cba000
	.long	0x44cca000
	.long	0x44cda000
	.long	0x44ce8000
	.long	0x44cf8000
	.long	0x44d08000
	.long	0x44d16000
	.long	0x44d26000
	.long	0x44d36000
	.long	0x44d44000
	.long	0x44d54000
	.long	0x44d64000
	.long	0x44d72000
	.long	0x44d82000
	.long	0x44d92000
	.long	0x44da0000
	.long	0x44db0000
	.long	0x44dc0000
	.long	0x44dce000
	.long	0x44dde000
	.long	0x44dec000
	.long	0x44dfc000
	.long	0x44e0c000
	.long	0x44e1a000
	.long	0x44e2a000
	.long	0x44e3a000
	.long	0x44e48000
	.long	0x44e58000
	.long	0x44e68000
	.long	0x44e76000
	.long	0x44e86000
	.long	0x44e96000
	.long	0x44ea4000
	.long	0x44eb4000
	.long	0x44ec4000
	.long	0x44ed4000
	.long	0x44ee2000
	.long	0x44ef2000
	.long	0x44f02000
	.long	0x44f10000
	.long	0x44f20000
	.long	0x44f30000
	.long	0x44f3e000
	.long	0x44f4e000
	.long	0x44f5e000
	.long	0x44f6c000
	.long	0x44f7c000
	.long	0x44f8c000
	.long	0x44f9a000
	.long	0x44faa000
	.long	0x44fba000
	.long	0x44fc8000
	.long	0x44fd8000
	.long	0x44fe8000
	.long	0x44ff6000
	.long	0x45003000
	.long	0x4500b000
	.long	0x45013000
	.long	0x4501a000
	.long	0x45022000
	.long	0x4502a000
	.long	0x45031000
	.long	0x45039000
	.long	0x45041000
	.long	0x45048000
	.long	0x45050000
	.long	0x45058000
	.long	0x4505f000
	.long	0x45067000
	.long	0x4506f000
	.long	0x45077000
	.long	0x4507e000
	.long	0x45086000
	.long	0x4508e000
	.long	0x45095000
	.long	0x4509d000
	.long	0x450a5000
	.long	0x450ac000
	.long	0x450b4000
	.long	0x450bc000
	.long	0x450c4000
	.long	0x450cb000
	.long	0x450d3000
	.long	0x450db000
	.long	0x450e2000
	.long	0x450ea000
	.type	function_zeros,@object
	.size	function_zeros,1284
	.align 4
_val_0_6:
	.long	858993459
	.long	1071854387
	.type	_val_0_6,@object
	.size	_val_0_6,8
	.align 4
_val_2_0:
	.long	0
	.long	1073741824
	.type	_val_2_0,@object
	.size	_val_2_0,8
	.align 4
_TWO_19H:
	.long	0
	.long	1093140480
	.type	_TWO_19H,@object
	.size	_TWO_19H,8
	.align 4
_val_4_0:
	.long	0
	.long	1074790400
	.type	_val_4_0,@object
	.size	_val_4_0,8
	.align 4
_val_8_0:
	.long	0
	.long	1075838976
	.type	_val_8_0,@object
	.size	_val_8_0,8
	.align 4
_val_3_0:
	.long	0
	.long	1074266112
	.type	_val_3_0,@object
	.size	_val_3_0,8
	.align 4
_val_5_0:
	.long	0
	.long	1075052544
	.type	_val_5_0,@object
	.size	_val_5_0,8
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_val_17_0:
	.long	0
	.long	1076953088
	.type	_val_17_0,@object
	.size	_val_17_0,8
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_PP0:
	.long	4294966288
	.long	1072693247
	.long	4269912424
	.long	3207725055
	.long	1780799603
	.long	1052554744
	.long	2285933488
	.long	3194113879
	.long	3892950982
	.long	1041746526
	.long	150212775
	.long	3184818833
	.type	_PP0,@object
	.size	_PP0,48
	.align 4
_PP1:
	.long	551
	.long	1072693248
	.long	4267608614
	.long	1061027839
	.long	3690881252
	.long	3200414971
	.long	3933039373
	.long	1046848465
	.long	3246008603
	.long	3189439465
	.long	1689113401
	.long	1037483563
	.type	_PP1,@object
	.size	_PP1,48
	.align 4
_QP0:
	.long	4294921804
	.long	3212836863
	.long	4153409851
	.long	1056096255
	.long	2226361937
	.long	3196916170
	.long	3849695089
	.long	1044094312
	.long	663042994
	.long	3187124278
	.long	3336948587
	.long	1035486718
	.type	_QP0,@object
	.size	_QP0,48
	.align 4
_QP1:
	.long	4294954956
	.long	1066926079
	.long	4141388976
	.long	3204071423
	.long	1500123100
	.long	1049740228
	.long	3264213437
	.long	3191856517
	.long	1489731078
	.long	1039824630
	.long	1807021260
	.long	3183130166
	.type	_QP1,@object
	.size	_QP1,48
	.data
	.hidden __libm_sincos_k32
	.section .note.GNU-stack, ""
# End
