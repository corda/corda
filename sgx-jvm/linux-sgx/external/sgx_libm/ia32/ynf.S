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
	.file "ynf.c"
	.text
..TXTST0:
# -- Begin  ynf
	.text
       .align    16,0x90
	.globl ynf
ynf:
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
        movl      12(%ebp), %ecx
        movl      8(%ebp), %esi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        lea       -1(%ecx), %edx
        cmpl      $2139095039, %edx
        jae       ..B1.51
..B1.2:
        testl     %esi, %esi
        je        ..B1.66
..B1.3:
        movl      %ecx, %edi
        movl      %esi, %eax
        xorl      %esi, %edi
        cltd      
        shrl      $31, %edi
        andl      %esi, %edi
        xorl      %edx, %esi
        subl      %edx, %esi
        cmpl      $1, %esi
        je        ..B1.61
..B1.4:
        movl      %esi, %eax
        sarl      $3, %eax
        cmpl      $320, %eax
        jg        ..B1.6
..B1.5:
        flds      function_zeros@GOTOFF(%ebx,%eax,4)
        jmp       ..B1.7
..B1.6:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        lea       -320(%eax), %edx
        lea       -2560(,%eax,8), %eax
        subl      %edx, %eax
        movl      %eax, 192(%esp)
        fildl     192(%esp)
        fstps     192(%esp)
        flds      192(%esp)
        faddp     %st, %st(1)
        fstps     192(%esp)
        flds      192(%esp)
..B1.7:
        flds      12(%ebp)
        fxch      %st(1)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.50
..B1.8:
        cmpl      $6, %esi
        jge       ..B1.23
..B1.9:
        fldl      _val_0_6@GOTOFF(%ebx)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jb        ..B1.24
..B1.10:
        fldl      _val_2_0@GOTOFF(%ebx)
        fld       %st(1)
        fdiv      %st(1), %st
        testl     %esi, %esi
        fstpl     200(%esp)
        fdiv      %st(1), %st
        fldl      200(%esp)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        flds      _ones@GOTOFF(%ebx)
        fstpt     32(%esp)
        fldt      32(%esp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      96(%esp)
        fld       %st(0)
        flds      _zeros@GOTOFF(%ebx)
        jle       ..B1.57
..B1.11:
        fstp      %st(7)
        fxch      %st(4)
        fstpt     112(%esp)
        xorl      %edx, %edx
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(3)
        fstps     44(%esp)
        fldt      96(%esp)
        fldt      32(%esp)
..B1.12:
        fldt      112(%esp)
        incl      %edx
        fmulp     %st, %st(5)
        cmpl      %esi, %edx
        fxch      %st(4)
        fstpl     200(%esp)
        fld       %st(3)
        fxch      %st(3)
        fmul      %st, %st(4)
        fldl      200(%esp)
        fldt      48(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpl     200(%esp)
        fld       %st(2)
        fdiv      %st(1), %st
        faddp     %st, %st(6)
        fadd      %st(2), %st
        fldl      200(%esp)
        fxch      %st(5)
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(6)
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        jae       ..B1.13
..B1.77:
        fstp      %st(4)
        fxch      %st(5)
        fxch      %st(4)
        fxch      %st(1)
        fxch      %st(4)
        jmp       ..B1.12
..B1.13:
        fstp      %st(0)
        flds      44(%esp)
        fxch      %st(5)
        fstpt     96(%esp)
        fxch      %st(5)
        fstpt     32(%esp)
        fldt      112(%esp)
        fxch      %st(3)
        fxch      %st(5)
        fxch      %st(2)
..B1.14:
        fldl      _TWO_19H@GOTOFF(%ebx)
        fld       %st(3)
        fxch      %st(5)
        fmul      %st(0), %st
        xorl      %ecx, %ecx
        fchs      
        movl      %esi, %edx
        fstpl     200(%esp)
        fxch      %st(1)
        fstl      168(%esp)
        fxch      %st(3)
        fstl      184(%esp)
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(4)
        fstps     44(%esp)
        fldl      200(%esp)
..B1.15:
        incl      %ecx
        incl      %edx
        movl      %ecx, %eax
        imull     %edx, %eax
        movl      %eax, 192(%esp)
        lea       (%esi,%ecx,2), %eax
        fildl     192(%esp)
        fstps     192(%esp)
        flds      192(%esp)
        fdivrs    _ones@GOTOFF(%ebx)
        movl      %eax, 192(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fildl     192(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fmul      %st(1), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldl      168(%esp)
        fmul      %st(2), %st
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpl     200(%esp)
        fldl      200(%esp)
        fstl      168(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpl     200(%esp)
        fxch      %st(2)
        fmul      %st(3), %st
        fldl      200(%esp)
        fxch      %st(1)
        faddl     184(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fld       %st(0)
        fmul      %st(6), %st
        fstpl     200(%esp)
        fstl      184(%esp)
        fldl      200(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     176(%esp)
        fldl      176(%esp)
        fsubp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fucom     %st(2)
        fnstsw    %ax
        fxch      %st(2)
        fstp      %st(0)
        sahf      
        fxch      %st(2)
        jne       ..B1.15
        jp        ..B1.15
..B1.16:
        fstp      %st(1)
        fstp      %st(3)
        fstp      %st(1)
        fldt      48(%esp)
        flds      44(%esp)
        fldt      32(%esp)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpl     200(%esp)
        fxch      %st(1)
        fstpl     168(%esp)
        fldl      200(%esp)
        fldl      _eulon2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     (%esp)
        fxch      %st(1)
        fstpt     112(%esp)
        fstpt     64(%esp)
        fstpt     80(%esp)
        call      __libm_log_k32
..B1.72:
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      112(%esp)
..B1.17:
        fldl      _oonpi@GOTOFF(%ebx)
        fxch      %st(1)
        fchs      
        fldl      _tonpi@GOTOFF(%ebx)
        cmpl      $1, %esi
        fmulp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fldl      184(%esp)
        fmul      %st(1), %st
        fmull     168(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        jle       ..B1.78
..B1.18:
        movl      $1, %ecx
        lea       -1(%esi), %edx
        fld       %st(2)
        .align    16,0x90
..B1.19:
        movl      %ecx, %ebx
        incl      %ecx
        imull     %edx, %ebx
        decl      %edx
        fmul      %st(4), %st
        movl      %ebx, 192(%esp)
        cmpl      %esi, %ecx
        fildl     192(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fdivrp    %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(3)
        jl        ..B1.19
..B1.20:
        fstp      %st(0)
        fstp      %st(3)
..B1.21:
        fldt      96(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        jmp       ..B1.46
..B1.23:
        cmpl      $80, %esi
        jge       ..B1.35
..B1.24:
        fxch      %st(1)
        fstpl     184(%esp)
..B1.25:
        fldl      _val_18_0@GOTOFF(%ebx)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.29
..B1.26:
        fstl      (%esp)
        fstps     44(%esp)
        call      y0@PLT
..B1.73:
        flds      44(%esp)
..B1.27:
        fstl      (%esp)
        fxch      %st(1)
        fstpl     168(%esp)
        fstps     44(%esp)
        call      y1@PLT
..B1.74:
        flds      44(%esp)
..B1.28:
        fxch      %st(1)
        jmp       ..B1.31
..B1.29:
        flds      _ones@GOTOFF(%ebx)
        lea       96(%esp), %edx
        fdiv      %st(1), %st
        lea       104(%esp), %ecx
        fstpl     104(%edx)
        fldl      104(%edx)
        fstpt     -64(%edx)
        fldt      -64(%edx)
        fldl      _val_16_0@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpl     104(%edx)
        fldl      104(%edx)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     104(%edx)
        fldl      104(%edx)
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     104(%edx)
        fldl      104(%edx)
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
        fstps     -52(%edx)
        call      __libm_sincos_k32
..B1.30:
        flds      44(%esp)
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
        fstpl     200(%esp)
        fldl      200(%esp)
        fldl      32+_PP1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fxch      %st(2)
        faddl     _PP0@GOTOFF(%ebx)
        fstpl     200(%esp)
        fldl      200(%esp)
        faddp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
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
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        faddp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
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
        fstpl     200(%esp)
        fldl      200(%esp)
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
        fstpl     200(%esp)
        fldl      200(%esp)
        fldt      32(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpl     200(%esp)
        fldl      200(%esp)
        fsqrt     
        fstpl     200(%esp)
        fldl      200(%esp)
        fldl      96(%esp)
        fmul      %st, %st(5)
        fmulp     %st, %st(4)
        fldl      104(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpl     168(%esp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
..B1.31:
        fldl      _val_2_0@GOTOFF(%ebx)
        cmpl      $1, %esi
        fdivp     %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        jle       ..B1.79
..B1.32:
        fldl      168(%esp)
        movl      $1, %edx
..B1.33:
        movl      %edx, 192(%esp)
        incl      %edx
        fildl     192(%esp)
        fstpl     200(%esp)
        fld       %st(2)
        fldl      200(%esp)
        cmpl      %esi, %edx
        fmul      %st(3), %st
        fmulp     %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(2)
        jl        ..B1.33
..B1.34:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B1.46
..B1.35:
        lea       5000(%esi,%esi,4), %edx
        movl      %edx, 192(%esp)
        fildl     192(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B1.24
..B1.36:
        fstp      %st(1)
        fldl      _val_4_0@GOTOFF(%ebx)
        movl      $-1, %ecx
        movl      %esi, 192(%esp)
        xorl      %edx, %edx
        fstl      160(%esp)
        fxch      %st(1)
        fsts      44(%esp)
        fildl     192(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fld       %st(0)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     128(%esp)
        fldt      128(%esp)
        fldl      _val_8_0@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     112(%esp)
        fldt      112(%esp)
        flds      _ones@GOTOFF(%ebx)
        fstl      152(%esp)
        fstpt     80(%esp)
        fldt      80(%esp)
        flds      4+_ones@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     64(%esp)
        fldt      64(%esp)
        fstpl     168(%esp)
        fldl      _val_2_0@GOTOFF(%ebx)
        fstl      144(%esp)
        fldl      _val_3_0@GOTOFF(%ebx)
        fstpt     48(%esp)
        fldt      48(%esp)
        fldl      _val_5_0@GOTOFF(%ebx)
        fstpt     32(%esp)
        fld       %st(2)
        fdiv      %st(3), %st
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     96(%esp)
        fldt      80(%esp)
        fldt      32(%esp)
        fldt      48(%esp)
..B1.37:
        fldt      112(%esp)
        fld       %st(5)
        fmul      %st(1), %st
        testl     %ecx, %ecx
        fxch      %st(1)
        fmul      %st(5), %st
        fxch      %st(7)
        fstpl     184(%esp)
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
        fdivp     %st, %st(6)
        fxch      %st(2)
        fmull     168(%esp)
        fstpl     200(%esp)
        fldl      200(%esp)
        fxch      %st(5)
        fstpl     200(%esp)
        fldl      200(%esp)
        fmul      %st(5), %st
        fstpl     200(%esp)
        fldl      200(%esp)
        fldt      80(%esp)
        jle       ..B1.59
..B1.38:
        faddp     %st, %st(6)
        fxch      %st(5)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldt      64(%esp)
        fadd      %st(6), %st
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     64(%esp)
..B1.39:
        fldl      144(%esp)
        fadd      %st, %st(5)
        fxch      %st(5)
        fstpl     200(%esp)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fstl      168(%esp)
        fldl      200(%esp)
        fxch      %st(3)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldl      160(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpl     200(%esp)
        fxch      %st(2)
        faddp     %st, %st(5)
        fldl      200(%esp)
        fxch      %st(5)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fabs      
        fldl      152(%esp)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.41
..B1.40:
        fstpl     152(%esp)
        movl      $1, %edx
        jmp       ..B1.42
..B1.41:
        fstp      %st(0)
        testl     %edx, %edx
        jne       ..B1.58
..B1.42:
        fldl      _TWO_19H@GOTOFF(%ebx)
        negl      %ecx
        fmul      %st(4), %st
        fstpl     200(%esp)
        fld       %st(3)
        fldl      200(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     176(%esp)
        fldl      176(%esp)
        fsubp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldl      184(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        fxch      %st(5)
        fxch      %st(2)
        fxch      %st(3)
        fxch      %st(4)
        fxch      %st(2)
        jne       ..B1.37
        jp        ..B1.37
..B1.43:
        fstp      %st(5)
        fstp      %st(4)
        fstp      %st(3)
        fstp      %st(0)
        fstp      %st(0)
        fstpt     80(%esp)
        flds      44(%esp)
..B1.44:
        addl      %esi, %esi
        lea       184(%esp), %edx
        negl      %esi
        lea       168(%esp), %ecx
        fstl      (%esp)
        decl      %esi
        movl      %edx, 8(%esp)
        movl      %ecx, 12(%esp)
        movl      %esi, 16(%esp)
        fstps     -124(%ecx)
        call      __libm_sincos_k32
..B1.45:
        flds      44(%esp)
        fldt      80(%esp)
        fmull     184(%esp)
        fldt      64(%esp)
        fmull     168(%esp)
        faddp     %st, %st(1)
        fldl      _tonpi@GOTOFF(%ebx)
        fdivp     %st, %st(2)
        fxch      %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
        fsqrt     
        fstpl     200(%esp)
        fldl      200(%esp)
        fmulp     %st, %st(1)
        fstpl     200(%esp)
        fldl      200(%esp)
..B1.46:
        testl     %edi, %edi
        je        ..B1.49
..B1.47:
        fchs      
..B1.49:
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        shrl      $31, %ecx
        fmuls     _large_value_32@GOTOFF(%ebx,%ecx,4)
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.51:
        fstp      %st(0)
        movl      %ecx, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        ja        ..B1.56
..B1.52:
        testl     %edx, %edx
        je        ..B1.69
..B1.53:
        testl     $-2147483648, %ecx
        jne       ..B1.68
..B1.54:
        movl      %esi, %edx
        shrl      $31, %edx
        andl      %esi, %edx
        flds      _zeros@GOTOFF(%ebx,%edx,4)
..B1.55:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.56:
        flds      12(%ebp)
        fmuls     _ones@GOTOFF(%ebx)
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        fstp      %st(1)
        fstp      %st(3)
        jmp       ..B1.14
..B1.58:
        fstp      %st(4)
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        fstpt     80(%esp)
        flds      44(%esp)
        jmp       ..B1.44
..B1.59:
        fsubp     %st, %st(6)
        fxch      %st(5)
        fstpl     200(%esp)
        fldl      200(%esp)
        fldt      64(%esp)
        fsub      %st(6), %st
        fstpl     200(%esp)
        fldl      200(%esp)
        fstpt     64(%esp)
        jmp       ..B1.39
..B1.61:
        fstpl     184(%esp)
        addl      $4, %esp
        pushl     12(%ebp)
        call      y1f@PLT
..B1.62:
        testl     %edi, %edi
        je        ..B1.65
..B1.63:
        fchs      
..B1.65:
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.66:
        fstpl     184(%esp)
        addl      $4, %esp
        pushl     12(%ebp)
        call      y0f@PLT
..B1.67:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.68:
        flds      _zeros@GOTOFF(%ebx)
        fmuls     _infs@GOTOFF(%ebx)
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.69:
        flds      4+_ones@GOTOFF(%ebx)
        fdivs     _zeros@GOTOFF(%ebx)
        fstps     192(%esp)
        flds      192(%esp)
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        fstp      %st(3)
        jmp       ..B1.21
..B1.79:
        fstp      %st(0)
        jmp       ..B1.46
        .align    16,0x90
	.type	ynf,@function
	.size	ynf,.-ynf
	.data
# -- End  ynf
	.data
	.align 4
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_infs:
	.long	2139095040
	.long	4286578688
	.type	_infs,@object
	.size	_infs,8
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x60,0x8e,0x0a,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x63,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 4
function_zeros:
	.long	0x00000000
	.long	0x00000000
	.long	0x00000000
	.long	0x3ecccccd
	.long	0x3fa66666
	.long	0x40400000
	.long	0x40a9999a
	.long	0x4101999a
	.long	0x41366666
	.long	0x4171999a
	.long	0x4198cccd
	.long	0x41ba6666
	.long	0x41df3333
	.long	0x42026666
	.long	0x42160000
	.long	0x422a0000
	.long	0x423f3333
	.long	0x42546666
	.long	0x426a0000
	.long	0x42803333
	.long	0x428b999a
	.long	0x42970000
	.long	0x42a2999a
	.long	0x42ae6666
	.long	0x42ba6666
	.long	0x42c66666
	.long	0x42d2999a
	.long	0x42decccd
	.long	0x42eb3333
	.long	0x42f7999a
	.long	0x4302199a
	.long	0x43086666
	.long	0x430ecccd
	.long	0x43153333
	.long	0x431b999a
	.long	0x4322199a
	.long	0x4328999a
	.long	0x432f199a
	.long	0x4335b333
	.long	0x433c3333
	.long	0x4342e666
	.long	0x43498000
	.long	0x43503333
	.long	0x4356e666
	.long	0x435d999a
	.long	0x43644ccd
	.long	0x436b0000
	.long	0x4371cccd
	.long	0x4378999a
	.long	0x437f6666
	.long	0x4383199a
	.long	0x43868ccd
	.long	0x4389f333
	.long	0x438d6666
	.long	0x4390d99a
	.long	0x43944ccd
	.long	0x4397c000
	.long	0x439b3333
	.long	0x439eb333
	.long	0x43a23333
	.long	0x43a5a666
	.long	0x43a92666
	.long	0x43aca666
	.long	0x43b02666
	.long	0x43b3a666
	.long	0x43b73333
	.long	0x43bab333
	.long	0x43be4000
	.long	0x43c1c000
	.long	0x43c54ccd
	.long	0x43c8d99a
	.long	0x43cc6666
	.long	0x43cff333
	.long	0x43d38000
	.long	0x43d70ccd
	.long	0x43da999a
	.long	0x43de2666
	.long	0x43e1c000
	.long	0x43e54ccd
	.long	0x43e8e666
	.long	0x43ec8000
	.long	0x43f00ccd
	.long	0x43f3a666
	.long	0x43f74000
	.long	0x43fad99a
	.long	0x43fe7333
	.long	0x44010666
	.long	0x4402d333
	.long	0x4404a000
	.long	0x44067333
	.long	0x44084000
	.long	0x440a0ccd
	.long	0x440be000
	.long	0x440daccd
	.long	0x440f8000
	.long	0x44114ccd
	.long	0x44132000
	.long	0x4414f333
	.long	0x4416c666
	.long	0x4418999a
	.long	0x441a6666
	.long	0x441c399a
	.long	0x441e0ccd
	.long	0x441fe000
	.long	0x4421b333
	.long	0x44238ccd
	.long	0x44256000
	.long	0x44273333
	.long	0x44290666
	.long	0x442ae000
	.long	0x442cb333
	.long	0x442e8666
	.long	0x44306000
	.long	0x44323333
	.long	0x44340ccd
	.long	0x4435e000
	.long	0x4437b99a
	.long	0x44398ccd
	.long	0x443b6666
	.long	0x443d4000
	.long	0x443f199a
	.long	0x4440eccd
	.long	0x4442c666
	.long	0x4444a000
	.long	0x4446799a
	.long	0x44485333
	.long	0x444a2ccd
	.long	0x444c0666
	.long	0x444de000
	.long	0x444fb99a
	.long	0x44519333
	.long	0x44536ccd
	.long	0x44554666
	.long	0x44572000
	.long	0x44590000
	.long	0x445ad99a
	.long	0x445cb333
	.long	0x445e9333
	.long	0x44606ccd
	.long	0x44624666
	.long	0x44642666
	.long	0x44660000
	.long	0x4467d99a
	.long	0x4469b99a
	.long	0x446b9333
	.long	0x446d7333
	.long	0x446f5333
	.long	0x44712ccd
	.long	0x44730ccd
	.long	0x4474e666
	.long	0x4476c666
	.long	0x4478a666
	.long	0x447a8000
	.long	0x447c6000
	.long	0x447e4000
	.long	0x44801000
	.long	0x4480fccd
	.long	0x4481eccd
	.long	0x4482dccd
	.long	0x4483cccd
	.long	0x4484bccd
	.long	0x4485accd
	.long	0x44869ccd
	.long	0x44878ccd
	.long	0x44887ccd
	.long	0x44896ccd
	.long	0x448a5ccd
	.long	0x448b4ccd
	.long	0x448c3ccd
	.long	0x448d2ccd
	.long	0x448e1ccd
	.long	0x448f0ccd
	.long	0x448ffccd
	.long	0x4490eccd
	.long	0x4491e000
	.long	0x4492d000
	.long	0x4493c000
	.long	0x4494b000
	.long	0x4495a333
	.long	0x44969333
	.long	0x44978333
	.long	0x44987333
	.long	0x44996666
	.long	0x449a5666
	.long	0x449b499a
	.long	0x449c399a
	.long	0x449d299a
	.long	0x449e1ccd
	.long	0x449f0ccd
	.long	0x44a00000
	.long	0x44a0f000
	.long	0x44a1e333
	.long	0x44a2d333
	.long	0x44a3c666
	.long	0x44a4b666
	.long	0x44a5a99a
	.long	0x44a6999a
	.long	0x44a78ccd
	.long	0x44a87ccd
	.long	0x44a97000
	.long	0x44aa6333
	.long	0x44ab5333
	.long	0x44ac4666
	.long	0x44ad399a
	.long	0x44ae299a
	.long	0x44af1ccd
	.long	0x44b01000
	.long	0x44b10333
	.long	0x44b1f333
	.long	0x44b2e666
	.long	0x44b3d99a
	.long	0x44b4cccd
	.long	0x44b5bccd
	.long	0x44b6b000
	.long	0x44b7a333
	.long	0x44b89666
	.long	0x44b9899a
	.long	0x44ba7ccd
	.long	0x44bb6ccd
	.long	0x44bc6000
	.long	0x44bd5333
	.long	0x44be4666
	.long	0x44bf399a
	.long	0x44c02ccd
	.long	0x44c12000
	.long	0x44c21333
	.long	0x44c30666
	.long	0x44c3f99a
	.long	0x44c4eccd
	.long	0x44c5e000
	.long	0x44c6d333
	.long	0x44c7c666
	.long	0x44c8b99a
	.long	0x44c9accd
	.long	0x44caa000
	.long	0x44cb9333
	.long	0x44cc899a
	.long	0x44cd7ccd
	.long	0x44ce7000
	.long	0x44cf6333
	.long	0x44d05666
	.long	0x44d1499a
	.long	0x44d23ccd
	.long	0x44d33333
	.long	0x44d42666
	.long	0x44d5199a
	.long	0x44d60ccd
	.long	0x44d70000
	.long	0x44d7f666
	.long	0x44d8e99a
	.long	0x44d9dccd
	.long	0x44dad000
	.long	0x44dbc666
	.long	0x44dcb99a
	.long	0x44ddaccd
	.long	0x44dea333
	.long	0x44df9666
	.long	0x44e0899a
	.long	0x44e18000
	.long	0x44e27333
	.long	0x44e36666
	.long	0x44e45ccd
	.long	0x44e55000
	.long	0x44e64333
	.long	0x44e7399a
	.long	0x44e82ccd
	.long	0x44e92333
	.long	0x44ea1666
	.long	0x44eb099a
	.long	0x44ec0000
	.long	0x44ecf333
	.long	0x44ede99a
	.long	0x44eedccd
	.long	0x44efd333
	.long	0x44f0c666
	.long	0x44f1bccd
	.long	0x44f2b000
	.long	0x44f3a666
	.long	0x44f4999a
	.long	0x44f59000
	.long	0x44f68333
	.long	0x44f7799a
	.long	0x44f86ccd
	.long	0x44f96333
	.long	0x44fa599a
	.long	0x44fb4ccd
	.long	0x44fc4333
	.long	0x44fd3666
	.long	0x44fe2ccd
	.long	0x44ff2000
	.long	0x45000b33
	.long	0x45008666
	.long	0x45010000
	.long	0x45017000
	.long	0x4501f000
	.long	0x45027000
	.long	0x4502e000
	.long	0x45036000
	.long	0x4503e000
	.long	0x45045000
	.long	0x4504d000
	.long	0x45055000
	.long	0x4505c000
	.long	0x45064000
	.long	0x4506c000
	.long	0x45073000
	.long	0x4507b000
	.long	0x45083000
	.long	0x4508a000
	.long	0x45092000
	.long	0x4509a000
	.long	0x450a1000
	.long	0x450a9000
	.long	0x450b1000
	.long	0x450b8000
	.long	0x450c0000
	.long	0x450c8000
	.long	0x450cf000
	.long	0x450d7000
	.long	0x450df000
	.long	0x450e6000
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
_eulon2:
	.long	3400611180
	.long	1072463685
	.type	_eulon2,@object
	.size	_eulon2,8
	.align 4
_oonpi:
	.long	1841940611
	.long	1070882608
	.type	_oonpi,@object
	.size	_oonpi,8
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_val_18_0:
	.long	0
	.long	1077018624
	.type	_val_18_0,@object
	.size	_val_18_0,8
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
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.data
	.hidden __libm_sincos_k32
	.hidden __libm_log_k32
	.section .note.GNU-stack, ""
# End
