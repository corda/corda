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
	.file "lgamma.c"
	.text
..TXTST0:
# -- Begin  lgamma
	.text
       .align    16,0x90
	.globl lgamma
lgamma:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        subl      $28, %esp
        fldl      32(%esp)
        lea       16(%esp), %eax
        fstpl     (%esp)
        movl      %eax, 8(%esp)
        call      __libm_lgamma
..B1.2:
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	lgamma,@function
	.size	lgamma,.-lgamma
	.data
# -- End  lgamma
	.text
# -- Begin  __libm_lgamma
	.text
       .align    16,0x90
__libm_lgamma:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B2.1:
..L2:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        movl      16(%ebp), %eax
        movzwl    14(%ebp), %ecx
        andl      $32752, %ecx
        movl      %eax, 16(%esp)
        shrl      $4, %ecx
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 144(%esp)
        movl      12(%ebp), %eax
        movzbl    15(%ebp), %edx
        andl      $1048575, %eax
        movl      %ecx, 4(%esp)
        andl      $128, %edx
        shll      $20, %ecx
        shrl      $7, %edx
        orl       %eax, %ecx
        movl      %ecx, 12(%esp)
        movl      %edx, (%esp)
..B2.2:
        fnstcw    46(%esp)
..B2.3:
        call      fegetround
..B2.208:
        movl      %eax, 28(%esp)
..B2.4:
        cmpl      $0, 28(%esp)
        jne       ..B2.6
..B2.5:
        xorl      %edi, %edi
        jmp       ..B2.8
..B2.6:
        addl      $-16, %esp
        movl      $0, (%esp)
        call      fesetround
..B2.209:
        addl      $16, %esp
..B2.7:
        movl      $1, %edi
..B2.8:
        movzwl    46(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.205
..B2.9:
        orl       $-64768, %edx
        movw      %dx, 44(%esp)
..B2.10:
        fldcw     44(%esp)
..B2.11:
        movl      $1, %esi
..B2.12:
        call      ..L3
..L3:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L3](%ebx), %ebx
        cmpl      $2047, 4(%esp)
        jne       ..B2.19
..B2.13:
        movl      16(%esp), %eax
        testl     %esi, %esi
        movl      $1, (%eax)
        je        ..B2.15
..B2.14:
        fldcw     46(%esp)
..B2.15:
        testl     %edi, %edi
        je        ..B2.17
..B2.16:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.210:
        addl      $16, %esp
..B2.17:
        fldl      8(%ebp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        fmul      %st(0), %st
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.18:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.19:
        cmpl      $0, 4(%esp)
        jne       ..B2.24
..B2.20:
        testl     $1048575, 12(%ebp)
        jne       ..B2.24
..B2.21:
        cmpl      $0, 8(%ebp)
        je        ..B2.100
..B2.24:
        movl      16(%esp), %eax
        movl      $1, (%eax)
..B2.25:
        cmpl      $0, (%esp)
        je        ..B2.126
..B2.26:
        fldl      8(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      nearbyint@PLT
..B2.211:
        fldl      8(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpl     112(%esp)
        fldl      112(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.27
        je        ..B2.119
..B2.27:
        fldl      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B2.244
        jp        ..B2.244
..B2.28:
        fld       %st(1)
        frndint   
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jnb       ..L4
        fld1      
        faddp     %st, %st(1)
..L4:
        fstpl     112(%esp)
        fldl      112(%esp)
        faddp     %st, %st(1)
        fstpl     112(%esp)
        testb     $1, 112(%esp)
        jne       ..B2.31
..B2.29:
        movl      16(%esp), %eax
        movl      $-1, (%eax)
        jmp       ..B2.31
..B2.244:
        fstp      %st(0)
..B2.31:
        cmpl      $1073741824, 12(%esp)
        jae       ..B2.43
..B2.32:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.111
..B2.33:
        fld       %st(0)
..B2.34:
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st, %st(2)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B2.34
..B2.35:
        fstp      %st(1)
        fstpt     72(%esp)
..B2.36:
        andb      $127, 81(%esp)
        addl      $-16, %esp
        fldt      88(%esp)
        fstpt     (%esp)
        fstpt     104(%esp)
        call      logl@PLT
..B2.213:
        fldt      104(%esp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        call      lgamma_pos
..B2.212:
        fldt      16(%esp)
        addl      $16, %esp
        testl     %edi, %edi
        je        ..B2.39
..B2.37:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.214:
        fldt      16(%esp)
        addl      $16, %esp
..B2.38:
        fldt      72(%esp)
..B2.39:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.41
..B2.40:
        fldcw     46(%esp)
..B2.41:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.42:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.43:
        cmpl      $1076756480, 12(%esp)
        jae       ..B2.73
..B2.44:
        fldl      _TWO_52H@GOTOFF(%ebx)
        fld       %st(1)
        fldt      .L_2il0floatpacket.4@GOTOFF(%ebx)
        fxch      %st(1)
        faddl     .L_2il0floatpacket.3@GOTOFF(%ebx)
        fstpl     64(%esp)
        fldl      64(%esp)
        fsubr     %st(2), %st
        fstpl     112(%esp)
        fldl      112(%esp)
        movl      112(%esp), %ecx
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpl     112(%esp)
        lea       1(%ecx), %edx
        fldl      112(%esp)
        fldl      64(%esp)
        faddp     %st, %st(1)
        fcompp    
        fnstsw    %ax
        sahf      
        jp        ..L6
        jb        ..L5
..L6:
        movl      %ecx, %edx
..L5:
        lea       (%edx,%edx,2), %ecx
        fcoml     _LRIB@GOTOFF(%ebx,%ecx,8)
        fnstsw    %ax
        sahf      
        jbe       ..B2.53
..B2.45:
        fldl      16+_LRIB@GOTOFF(%ebx,%ecx,8)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.53
..B2.46:
        fldl      _LRP16@GOTOFF(%ebx,%edx,8)
        fxch      %st(1)
        fsubl     8+_LRIB@GOTOFF(%ebx,%ecx,8)
        shll      $6, %ecx
        fld       %st(0)
        testl     %edi, %edi
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fldt      64+_LRP@GOTOFF(%ecx,%ebx)
        fldt      32+_LRP@GOTOFF(%ecx,%ebx)
        fldt      _LRP@GOTOFF(%ecx,%ebx)
        fldt      48+_LRP@GOTOFF(%ecx,%ebx)
        fldt      16+_LRP@GOTOFF(%ecx,%ebx)
        fxch      %st(7)
        faddl     152+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(5), %st
        faddl     136+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(5), %st
        faddl     120+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(5), %st
        faddl     104+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(5), %st
        faddl     88+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldl      160+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddl     144+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddl     128+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddl     112+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddl     96+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddl     80+_LRP@GOTOFF(%ecx,%ebx)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B2.49
..B2.47:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.215:
        addl      $16, %esp
..B2.48:
        fldt      72(%esp)
..B2.49:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.51
..B2.50:
        fldcw     46(%esp)
..B2.51:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.52:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.53:
        fcoml     _RRIB@GOTOFF(%ebx,%ecx,8)
        fnstsw    %ax
        sahf      
        jbe       ..B2.62
..B2.54:
        fldl      16+_RRIB@GOTOFF(%ebx,%ecx,8)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.62
..B2.55:
        fsubl     8+_RRIB@GOTOFF(%ebx,%ecx,8)
        fld       %st(0)
        lea       (%edx,%edx,4), %eax
        shll      $5, %eax
        fmul      %st(1), %st
        testl     %edi, %edi
        fldl      72+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(1), %st
        fldt      32+_RRP@GOTOFF(%eax,%ebx)
        fldt      _RRP@GOTOFF(%eax,%ebx)
        fldl      64+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     120+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     112+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     88+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     80+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     136+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     128+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     104+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     96+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        fxch      %st(3)
        faddl     152+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      48+_RRP@GOTOFF(%eax,%ebx)
        fldt      16+_RRP@GOTOFF(%eax,%ebx)
        fxch      %st(3)
        faddl     144+_RRP@GOTOFF(%eax,%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B2.58
..B2.56:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.216:
        addl      $16, %esp
..B2.57:
        fldt      72(%esp)
..B2.58:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.60
..B2.59:
        fldcw     46(%esp)
..B2.60:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.61:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.62:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.115
..B2.63:
        fld       %st(0)
..B2.64:
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st, %st(2)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B2.64
..B2.65:
        fstp      %st(1)
        fstpt     72(%esp)
..B2.66:
        andb      $127, 81(%esp)
        addl      $-16, %esp
        fldt      88(%esp)
        fstpt     (%esp)
        fstpt     104(%esp)
        call      logl@PLT
..B2.218:
        fldt      104(%esp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        call      lgamma_pos
..B2.217:
        fldt      16(%esp)
        addl      $16, %esp
        testl     %edi, %edi
        je        ..B2.69
..B2.67:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.219:
        fldt      16(%esp)
        addl      $16, %esp
..B2.68:
        fldt      72(%esp)
..B2.69:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.71
..B2.70:
        fldcw     46(%esp)
..B2.71:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.72:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.73:
        cmpl      $1076953088, 12(%esp)
        jae       ..B2.84
..B2.74:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fstpt     120(%esp)
        fldt      120(%esp)
        fcom      %st(1)
        fnstsw    %ax
        fldt      .L_2il0floatpacket.4@GOTOFF(%ebx)
        fstpt     132(%esp)
        fldt      132(%esp)
        sahf      
        jbe       ..B2.245
..B2.75:
        fldt      .L_2il0floatpacket.5@GOTOFF(%ebx)
        fstpt     16(%esp)
        fstpt     (%esp)
        .align    16,0x90
..B2.76:
        fldt      (%esp)
        fstp      %st(0)
        fld       %st(1)
        fxch      %st(2)
        fldt      16(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     100(%esp)
        fldt      100(%esp)
        fsubp     %st, %st(4)
        fld       %st(1)
        fsub      %st(4), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fcom      %st(3)
        fnstsw    %ax
        fldt      120(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        sahf      
        fadd      %st(3), %st
        fstpt     100(%esp)
        fldt      132(%esp)
        fldt      100(%esp)
        fsubp     %st, %st(4)
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(1), %st
        fld       %st(6)
        fmul      %st(4), %st
        fxch      %st(2)
        fmulp     %st, %st(7)
        faddp     %st, %st(6)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fstpt     120(%esp)
        fldt      120(%esp)
        fsubp     %st, %st(2)
        fxch      %st(3)
        fsubp     %st, %st(1)
        fstpt     132(%esp)
        jp        ..B2.77
        jb        ..B2.76
..B2.77:
        fstp      %st(0)
..B2.78:
        andb      $127, 129(%esp)
        addl      $-16, %esp
        fldt      136(%esp)
        fstpt     (%esp)
        fstpt     104(%esp)
        call      logl@PLT
..B2.221:
        fldt      104(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     (%esp)
        call      lgamma_pos
..B2.220:
        addl      $16, %esp
        testl     %edi, %edi
        je        ..B2.80
..B2.79:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.222:
        fldt      16(%esp)
        addl      $16, %esp
..B2.80:
        fldt      16(%esp)
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.82
..B2.81:
        fldcw     46(%esp)
..B2.82:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.83:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.84:
        cmpl      $1082130432, 12(%esp)
        jb        ..B2.92
..B2.85:
        fldt      _LN_SQRT_TWO_PI@GOTOFF(%ebx)
        fstpt     (%esp)
        addl      $-16, %esp
        fld       %st(0)
        fchs      
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     32(%esp)
        fstpt     104(%esp)
        fldt      104(%esp)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        call      logl@PLT
..B2.223:
        fldt      104(%esp)
        fldt      64(%esp)
        fmulp     %st, %st(2)
        fldt      32(%esp)
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fdiv      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmull     _W4@GOTOFF(%ebx)
        fldt      _W2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      _TWO_52H@GOTOFF(%ebx)
        fstl      112(%esp)
        fldl      112(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpl     112(%esp)
        fldl      112(%esp)
        fsubp     %st, %st(1)
        fstpl     112(%esp)
        fldl      112(%esp)
        fsubrp    %st, %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      _S26@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _S10@GOTOFF(%ebx)
        fldt      _S06@GOTOFF(%ebx)
        fldt      _S02@GOTOFF(%ebx)
        fldl      _S28@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     _S22@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     _S24@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     _S18@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     _S20@GOTOFF(%ebx)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     _S14@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddl     _S16@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _S12@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _S08@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      _S04@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        je        ..B2.88
..B2.86:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     64(%esp)
        call      fesetround
..B2.224:
        fldt      64(%esp)
        addl      $16, %esp
..B2.87:
        fldt      72(%esp)
..B2.88:
        andb      $127, 41(%esp)
        addl      $-16, %esp
        fldt      48(%esp)
        fstpt     (%esp)
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        call      logl@PLT
..B2.225:
        fldt      64(%esp)
        addl      $16, %esp
        testl     %esi, %esi
        fsubp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.90
..B2.89:
        fldcw     46(%esp)
..B2.90:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.91:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.92:
        fldl      _TWO_52H@GOTOFF(%ebx)
        fstl      112(%esp)
        fldl      112(%esp)
        fsub      %st(2), %st
        fstpl     112(%esp)
        fxch      %st(1)
        fchs      
        fldl      112(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpl     112(%esp)
        fldl      112(%esp)
        fsubr     %st(1), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(0), %st
        fstpt     (%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.227:
        fchs      
        fldl      8(%ebp)
        fchs      
        fstpt     (%esp)
        fstpt     32(%esp)
        call      lgamma_pos
..B2.226:
        fldt      32(%esp)
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fldl      _S26@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _S10@GOTOFF(%ebx)
        fldt      _S06@GOTOFF(%ebx)
        fldt      _S02@GOTOFF(%ebx)
        fldt      _S12@GOTOFF(%ebx)
        fxch      %st(4)
        faddl     _S22@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddl     _S18@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddl     _S14@GOTOFF(%ebx)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldl      _S28@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      _S08@GOTOFF(%ebx)
        fldt      _S04@GOTOFF(%ebx)
        fxch      %st(2)
        faddl     _S24@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     _S20@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     _S16@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        je        ..B2.95
..B2.93:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     32(%esp)
        call      fesetround
..B2.228:
        fldt      32(%esp)
        addl      $16, %esp
..B2.94:
        fldt      72(%esp)
..B2.95:
        andb      $127, 41(%esp)
        addl      $-16, %esp
        fldt      48(%esp)
        fstpt     (%esp)
        fsubrp    %st, %st(1)
        fstpt     32(%esp)
        call      logl@PLT
..B2.229:
        fldt      32(%esp)
        addl      $16, %esp
        testl     %esi, %esi
        fsubp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.97
..B2.96:
        fldcw     46(%esp)
..B2.97:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.98:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.99:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B2.100:
        cmpl      $0, (%esp)
        je        ..B2.102
..B2.101:
        movl      16(%esp), %eax
        movl      $-1, (%eax)
        jmp       ..B2.103
..B2.102:
        movl      16(%esp), %eax
        movl      $1, (%eax)
..B2.103:
        testl     %edi, %edi
        je        ..B2.105
..B2.104:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.230:
        addl      $16, %esp
..B2.105:
        fld1      
        fldz      
        testl     %esi, %esi
        fstpl     112(%esp)
        fldl      112(%esp)
        fdivrp    %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.107
..B2.106:
        fldcw     46(%esp)
..B2.107:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
        jmp       ..B2.18
..B2.111:
        fstpt     72(%esp)
        jmp       ..B2.36
..B2.115:
        fstpt     72(%esp)
        jmp       ..B2.66
..B2.119:
        fstp      %st(0)
        testl     %edi, %edi
        je        ..B2.121
..B2.120:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.231:
        addl      $16, %esp
..B2.121:
        fld1      
        fldz      
        testl     %esi, %esi
        fstpl     112(%esp)
        fldl      112(%esp)
        fdivrp    %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.123
..B2.122:
        fldcw     46(%esp)
..B2.123:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
        jmp       ..B2.18
..B2.126:
        movzwl    14(%ebp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2037, %eax
        jg        ..B2.198
..B2.127:
        je        ..B2.195
..B2.128:
        fldl      8(%ebp)
        fld1      
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.129
        je        ..B2.188
..B2.129:
        cmpl      $1032, 4(%esp)
        jb        ..B2.136
..B2.130:
        fldt      _LN_SQRT_TWO_PI@GOTOFF(%ebx)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fsubr     %st(2), %st
        fstpt     (%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(1)
        fstpt     48(%esp)
        fstpt     32(%esp)
        call      logl@PLT
..B2.232:
        fldt      32(%esp)
        fldt      48(%esp)
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fsubr     %st, %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fmull     _W4@GOTOFF(%ebx)
        fldt      _W2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        je        ..B2.132
..B2.131:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        fstpt     48(%esp)
        call      fesetround
..B2.233:
        fldt      48(%esp)
        fldt      16(%esp)
        addl      $16, %esp
..B2.132:
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.134
..B2.133:
        fldcw     46(%esp)
..B2.134:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.135:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.136:
        cmpl      $1025, 4(%esp)
        jb        ..B2.143
..B2.137:
        movl      4(%esp), %eax
        fsubl     -8200+_A@GOTOFF(%ebx,%eax,8)
        fmull     -8200+_B@GOTOFF(%ebx,%eax,8)
        lea       -9225(%eax,%eax,8), %edx
        shll      $5, %edx
        fld       %st(0)
        fmul      %st(1), %st
        testl     %edi, %edi
        fldt      12+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fldt      _C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fldt      36+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      24+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      60+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      48+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      84+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      72+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      108+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      96+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      132+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      120+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      156+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      144+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      180+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      168+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      204+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      192+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      228+_C5@GOTOFF(%edx,%ebx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      216+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      252+_C5@GOTOFF(%edx,%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      240+_C5@GOTOFF(%edx,%ebx)
        faddp     %st, %st(1)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     8(%esp)
        fstpt     72(%esp)
        je        ..B2.139
..B2.138:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        movl      %edx, 16(%esp)
        call      fesetround
..B2.234:
        movl      16(%esp), %edx
        addl      $16, %esp
..B2.139:
        fldt      264+_C5@GOTOFF(%edx,%ebx)
        testl     %esi, %esi
        fldt      8(%esp)
        fmulp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.141
..B2.140:
        fldcw     46(%esp)
..B2.141:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.142:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.143:
        cmpl      $1073872896, 12(%esp)
        jb        ..B2.150
..B2.144:
        fldt      12+_C4@GOTOFF(%ebx)
        testl     %edi, %edi
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fldt      _C4@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fldt      36+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      24+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      60+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      48+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      84+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      72+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      108+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      96+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      132+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      120+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      156+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      144+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      180+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      168+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      204+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      192+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      228+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      216+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      252+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldt      240+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st, %st(1)
        fstpt     72(%esp)
        fldt      264+_C4@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B2.146
..B2.145:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        fstpt     32(%esp)
        call      fesetround
..B2.235:
        fldt      32(%esp)
        addl      $16, %esp
..B2.146:
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.148
..B2.147:
        fldcw     46(%esp)
..B2.148:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.149:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.150:
        cmpl      $1073479680, 12(%esp)
        jb        ..B2.158
..B2.151:
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        testl     %edi, %edi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      168+_C3@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      160+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     152+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     144+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     136+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     128+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     120+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     112+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     104+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     96+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     88+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     80+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      64+_C3@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      32+_C3@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _C3@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      48+_C3@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      16+_C3@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B2.154
..B2.152:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.236:
        addl      $16, %esp
..B2.153:
        fldt      72(%esp)
..B2.154:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.156
..B2.155:
        fldcw     46(%esp)
..B2.156:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.157:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.158:
        cmpl      $1072955392, 12(%esp)
        jb        ..B2.166
..B2.159:
        fldt      _LM@GOTOFF(%ebx)
        testl     %edi, %edi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      184+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      80+_LGAMMA_C2@GOTOFF(%ebx)
        fldt      48+_LGAMMA_C2@GOTOFF(%ebx)
        fldt      16+_LGAMMA_C2@GOTOFF(%ebx)
        fldt      32+_LGAMMA_C2@GOTOFF(%ebx)
        fldt      _LGAMMA_C2@GOTOFF(%ebx)
        fxch      %st(5)
        faddl     168+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddl     152+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddl     136+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddl     120+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddl     104+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldl      176+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(3), %st
        fldt      64+_LGAMMA_C2@GOTOFF(%ebx)
        fxch      %st(1)
        faddl     160+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     144+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     128+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     112+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddl     96+_LGAMMA_C2@GOTOFF(%ebx)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B2.162
..B2.160:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.237:
        addl      $16, %esp
..B2.161:
        fldt      72(%esp)
..B2.162:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.164
..B2.163:
        fldcw     46(%esp)
..B2.164:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.165:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.166:
        cmpl      $1072431104, 12(%esp)
        jb        ..B2.174
..B2.167:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        testl     %edi, %edi
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      168+_C1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      160+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     152+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     144+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     136+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     128+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     120+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     112+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     104+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     96+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     88+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     80+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      64+_C1@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      32+_C1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _C1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     72(%esp)
        fldt      72(%esp)
        fldt      48+_C1@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      16+_C1@GOTOFF(%ebx)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B2.170
..B2.168:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.238:
        addl      $16, %esp
..B2.169:
        fldt      72(%esp)
..B2.170:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.172
..B2.171:
        fldcw     46(%esp)
..B2.172:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.173:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.174:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $-16, %esp
        fadd      %st(1), %st
        fstpt     (%esp)
        fstpt     32(%esp)
        call      lgamma_pos
..B2.240:
        fldt      32(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fstpt     (%esp)
        call      logl@PLT
..B2.239:
        addl      $16, %esp
        fstpt     72(%esp)
        fldt      72(%esp)
        testl     %edi, %edi
        je        ..B2.177
..B2.175:
        fstp      %st(0)
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.241:
        addl      $16, %esp
..B2.176:
        fldt      72(%esp)
..B2.177:
        fldt      (%esp)
        testl     %esi, %esi
        fsubp     %st, %st(1)
        fstpl     112(%esp)
        je        ..B2.179
..B2.178:
        fldcw     46(%esp)
..B2.179:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
..B2.180:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.188:
        fstp      %st(0)
        testl     %esi, %esi
        je        ..B2.190
..B2.189:
        fldcw     46(%esp)
..B2.190:
        testl     %edi, %edi
        je        ..B2.192
..B2.191:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.242:
        addl      $16, %esp
..B2.192:
        movl      144(%esp), %eax
        fldz      
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
        jmp       ..B2.18
..B2.195:
        movl      12(%ebp), %eax
        andl      $1048575, %eax
        cmpl      $480473, %eax
        jg        ..B2.198
..B2.196:
        jne       ..B2.128
..B2.197:
        cmpl      $663441831, 8(%ebp)
        jbe       ..B2.128
..B2.198:
        testl     %edi, %edi
        je        ..B2.200
..B2.199:
        addl      $-16, %esp
        movl      44(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.243:
        addl      $16, %esp
..B2.200:
        fldl      _PBIG@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(0), %st
        fstpl     112(%esp)
        je        ..B2.202
..B2.201:
        fldcw     46(%esp)
..B2.202:
        fldl      112(%esp)
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.99
        jmp       ..B2.18
..B2.205:
        xorl      %esi, %esi
        jmp       ..B2.12
..B2.245:
        fstp      %st(1)
        fstp      %st(0)
        jmp       ..B2.78
        .align    16,0x90
	.type	__libm_lgamma,@function
	.size	__libm_lgamma,.-__libm_lgamma
	.data
# -- End  __libm_lgamma
	.text
# -- Begin  lgamma_pos
	.text
       .align    16,0x90
lgamma_pos:
# parameter 1: 8 + %ebp
..B3.1:
..L7:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        fldt      8(%ebp)
        fstl      (%esp)
        movzwl    6(%esp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        movl      4(%esp), %edx
        shll      $20, %ecx
        andl      $1048575, %edx
        orl       %edx, %ecx
        call      ..L8
..L8:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L8](%eax), %eax
        cmpl      $1074790400, %ecx
        jb        ..B3.3
..B3.2:
        addl      $-1074790400, %ecx
        sarl      $20, %ecx
        fldl      _A@GOTOFF(%eax,%ecx,8)
        lea       (%ecx,%ecx,8), %edx
        shll      $5, %edx
        fsubrp    %st, %st(1)
        fmull     _B@GOTOFF(%eax,%ecx,8)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_C5@GOTOFF(%eax,%edx)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fldt      36+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      24+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fldt      60+_C5@GOTOFF(%eax,%edx)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fldt      48+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fldt      84+_C5@GOTOFF(%eax,%edx)
        fmul      %st(5), %st
        fadd      %st(4), %st
        fldt      108+_C5@GOTOFF(%eax,%edx)
        fmul      %st(6), %st
        fadd      %st(5), %st
        fldt      _C5@GOTOFF(%edx,%eax)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      72+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      96+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      132+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      120+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      156+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      144+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      180+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      168+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      204+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      192+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      228+_C5@GOTOFF(%eax,%edx)
        fmul      %st(3), %st
        fadd      %st(2), %st
        fldt      216+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      252+_C5@GOTOFF(%eax,%edx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      240+_C5@GOTOFF(%eax,%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      264+_C5@GOTOFF(%eax,%edx)
        fmulp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.3:
        cmpl      $1073872896, %ecx
        jb        ..B3.5
..B3.4:
        fldt      12+_C4@GOTOFF(%eax)
        fmul      %st(1), %st
        fld       %st(1)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fldt      _C4@GOTOFF(%eax)
        faddp     %st, %st(2)
        fldt      36+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      24+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      60+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      48+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      84+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      72+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      108+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      96+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      132+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      120+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      156+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      144+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      180+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      168+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      204+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      192+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      228+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        fadd      %st(1), %st
        fldt      216+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      252+_C4@GOTOFF(%eax)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldt      240+_C4@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      264+_C4@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.6@GOTOFF(%eax)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.5:
        cmpl      $1073479680, %ecx
        jb        ..B3.7
..B3.6:
        fldt      .L_2il0floatpacket.6@GOTOFF(%eax)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      160+_C3@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      48+_C3@GOTOFF(%eax)
        fldt      16+_C3@GOTOFF(%eax)
        fldt      64+_C3@GOTOFF(%eax)
        fldt      32+_C3@GOTOFF(%eax)
        fldt      _C3@GOTOFF(%eax)
        fxch      %st(5)
        faddl     144+_C3@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     128+_C3@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     112+_C3@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     96+_C3@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     80+_C3@GOTOFF(%eax)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldl      168+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     152+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     136+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     120+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     104+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     88+_C3@GOTOFF(%eax)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.7:
        cmpl      $1072955392, %ecx
        jb        ..B3.9
..B3.8:
        fldt      _LM@GOTOFF(%eax)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      176+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      64+_LGAMMA_C2@GOTOFF(%eax)
        fldt      32+_LGAMMA_C2@GOTOFF(%eax)
        fldt      _LGAMMA_C2@GOTOFF(%eax)
        fldt      48+_LGAMMA_C2@GOTOFF(%eax)
        fldt      80+_LGAMMA_C2@GOTOFF(%eax)
        fxch      %st(5)
        faddl     160+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     144+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     128+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     112+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     96+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fldl      184+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddl     168+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddl     152+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddl     136+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddl     120+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddl     104+_LGAMMA_C2@GOTOFF(%eax)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      16+_LGAMMA_C2@GOTOFF(%eax)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.9:
        cmpl      $1072431104, %ecx
        jb        ..B3.11
..B3.10:
        fldt      .L_2il0floatpacket.1@GOTOFF(%eax)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      160+_C1@GOTOFF(%eax)
        fmul      %st(1), %st
        fldt      48+_C1@GOTOFF(%eax)
        fldt      16+_C1@GOTOFF(%eax)
        fldt      64+_C1@GOTOFF(%eax)
        fldt      32+_C1@GOTOFF(%eax)
        fldt      _C1@GOTOFF(%eax)
        fxch      %st(5)
        faddl     144+_C1@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     128+_C1@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     112+_C1@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     96+_C1@GOTOFF(%eax)
        fmul      %st(6), %st
        faddl     80+_C1@GOTOFF(%eax)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldl      168+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     152+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     136+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     120+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     104+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddl     88+_C1@GOTOFF(%eax)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.11:
        fstp      %st(0)
        fldz      
..B3.12:
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	lgamma_pos,@function
	.size	lgamma_pos,.-lgamma_pos
	.data
# -- End  lgamma_pos
	.text
# -- Begin  gamma
	.text
       .align    16,0x90
	.globl gamma
gamma:
# parameter 1: 32 + %esp
..B4.1:
..L9:

        subl      $28, %esp
        fldl      32(%esp)
        lea       16(%esp), %eax
        fstpl     (%esp)
        movl      %eax, 8(%esp)
        call      __libm_lgamma
..B4.2:
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	gamma,@function
	.size	gamma,.-gamma
	.data
# -- End  gamma
	.text
# -- Begin  lgamma_r
	.text
       .align    16,0x90
	.globl lgamma_r
lgamma_r:
# parameter 1: 4 + %esp
# parameter 2: 12 + %esp
..B5.1:
..L10:

        jmp       __libm_lgamma
        .align    16,0x90
	.type	lgamma_r,@function
	.size	lgamma_r,.-lgamma_r
	.data
# -- End  lgamma_r
	.text
# -- Begin  gamma_r
	.text
       .align    16,0x90
	.globl gamma_r
gamma_r:
# parameter 1: 4 + %esp
# parameter 2: 12 + %esp
..B6.1:
..L11:

        jmp       __libm_lgamma
        .align    16,0x90
	.type	gamma_r,@function
	.size	gamma_r,.-gamma_r
	.data
# -- End  gamma_r
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,12
	.space 4, 0x00 	# pad
	.align 16
_LRIB:
	.long	3639930760
	.long	3221638968
	.long	169597185
	.long	3221617473
	.long	994230906
	.long	3221595977
	.long	249193764
	.long	3222253755
	.long	1417424869
	.long	3222250609
	.long	2585655974
	.long	3222247463
	.long	679699934
	.long	3222534303
	.long	2054089469
	.long	3222533975
	.long	3428479004
	.long	3222533647
	.long	1666697798
	.long	3222798023
	.long	4119983117
	.long	3222797970
	.long	2278301140
	.long	3222797918
	.long	753528318
	.long	3223060435
	.long	4151019248
	.long	3223060427
	.long	3253542882
	.long	3223060420
	.long	1221915992
	.long	3223322618
	.long	2139183567
	.long	3223322617
	.long	3056451142
	.long	3223322616
	.long	2968798493
	.long	3223453695
	.long	2743618512
	.long	3223453695
	.long	2518438531
	.long	3223453695
	.long	4156721779
	.long	3223584767
	.long	4139833280
	.long	3223584767
	.long	4122944781
	.long	3223584767
	.long	4282834537
	.long	3223715839
	.long	4280864212
	.long	3223715839
	.long	4278893887
	.long	3223715839
	.long	4293918703
	.long	3223846911
	.long	4293792039
	.long	3223846911
	.long	4293665375
	.long	3223846911
	.long	4294888151
	.long	3223977983
	.long	4294876892
	.long	3223977983
	.long	4294865633
	.long	3223977983
	.long	4294961261
	.long	3224109055
	.long	4294960839
	.long	3224109055
	.long	4294960417
	.long	3224109055
	.long	4294966894
	.long	3224240127
	.long	4294966866
	.long	3224240127
	.long	4294966838
	.long	3224240127
	.type	_LRIB,@object
	.size	_LRIB,312
	.space 8, 0x00 	# pad
	.align 16
_LRP16:
	.long	2378050348
	.long	1101969305
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.type	_LRP16,@object
	.size	_LRP16,104
	.space 8, 0x00 	# pad
	.align 16
_LRP:
	.word	17751
	.word	54196
	.word	10629
	.word	51164
	.word	16330
	.word	0
	.word	0
	.word	0
	.word	41133
	.word	9612
	.word	27976
	.word	62729
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	42459
	.word	13010
	.word	63969
	.word	39219
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	65437
	.word	25696
	.word	54808
	.word	41154
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	58026
	.word	20377
	.word	22118
	.word	64130
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	35937
	.word	22559
	.word	22660
	.word	49256
	.word	8259
	.word	31940
	.word	14143
	.word	16516
	.word	36722
	.word	48879
	.word	8761
	.word	49313
	.word	17702
	.word	59215
	.word	47717
	.word	16573
	.word	13453
	.word	50588
	.word	11555
	.word	49370
	.word	8896
	.word	4195
	.word	22549
	.word	16631
	.word	8306
	.word	47544
	.word	1832
	.word	49429
	.word	60153
	.word	61869
	.word	6523
	.word	16691
	.word	63612
	.word	2764
	.word	26286
	.word	49489
	.word	63356
	.word	31621
	.word	64278
	.word	16751
	.word	24055
	.word	64192
	.word	21464
	.word	49552
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49769
	.word	49242
	.word	6580
	.word	61152
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	7708
	.word	25266
	.word	60595
	.word	42444
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	45975
	.word	8629
	.word	62832
	.word	64438
	.word	16390
	.word	0
	.word	0
	.word	0
	.word	39855
	.word	22905
	.word	44266
	.word	59689
	.word	49162
	.word	0
	.word	0
	.word	0
	.word	10240
	.word	41089
	.word	5368
	.word	62588
	.word	16398
	.word	0
	.word	0
	.word	0
	.word	3194
	.word	32877
	.word	5879
	.word	49457
	.word	40173
	.word	43959
	.word	59635
	.word	16755
	.word	53371
	.word	1664
	.word	56254
	.word	49591
	.word	34510
	.word	44659
	.word	12150
	.word	16893
	.word	33872
	.word	42862
	.word	8799
	.word	49730
	.word	10662
	.word	44157
	.word	53571
	.word	17030
	.word	55074
	.word	58798
	.word	65525
	.word	49868
	.word	1276
	.word	34610
	.word	36363
	.word	17170
	.word	7041
	.word	29575
	.word	61144
	.word	50007
	.word	19121
	.word	53977
	.word	34010
	.word	17312
	.word	54457
	.word	12310
	.word	48419
	.word	50149
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	55737
	.word	12115
	.word	42602
	.word	40775
	.word	16337
	.word	0
	.word	0
	.word	0
	.word	42581
	.word	53029
	.word	20983
	.word	59666
	.word	49157
	.word	0
	.word	0
	.word	0
	.word	24816
	.word	20947
	.word	58172
	.word	55961
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	3130
	.word	13042
	.word	49143
	.word	34463
	.word	49170
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	24697
	.word	14253
	.word	47765
	.word	16408
	.word	0
	.word	0
	.word	0
	.word	32693
	.word	5691
	.word	15709
	.word	49649
	.word	20491
	.word	21402
	.word	35932
	.word	16986
	.word	44071
	.word	16276
	.word	1675
	.word	49861
	.word	13758
	.word	14468
	.word	65445
	.word	17200
	.word	60394
	.word	3375
	.word	60434
	.word	50075
	.word	49748
	.word	2796
	.word	14419
	.word	17415
	.word	61579
	.word	22133
	.word	33066
	.word	50291
	.word	25055
	.word	28661
	.word	31699
	.word	17632
	.word	55823
	.word	18661
	.word	6347
	.word	50508
	.word	58142
	.word	19935
	.word	64799
	.word	17849
	.word	41216
	.word	59562
	.word	42287
	.word	50726
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	16006
	.word	65034
	.word	42959
	.word	34185
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	40843
	.word	53953
	.word	46369
	.word	45839
	.word	49160
	.word	0
	.word	0
	.word	0
	.word	54179
	.word	42767
	.word	58812
	.word	64462
	.word	16400
	.word	0
	.word	0
	.word	0
	.word	50676
	.word	3143
	.word	1093
	.word	60276
	.word	49177
	.word	0
	.word	0
	.word	0
	.word	0
	.word	56865
	.word	38982
	.word	63406
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	65273
	.word	45226
	.word	24230
	.word	49857
	.word	23450
	.word	59950
	.word	19796
	.word	17236
	.word	4038
	.word	16755
	.word	26702
	.word	50152
	.word	24087
	.word	51196
	.word	62539
	.word	17533
	.word	25486
	.word	58858
	.word	44079
	.word	50450
	.word	31845
	.word	29909
	.word	37453
	.word	17831
	.word	19699
	.word	12384
	.word	3664
	.word	50750
	.word	26808
	.word	47918
	.word	18611
	.word	18131
	.word	25376
	.word	13791
	.word	62285
	.word	51048
	.word	64886
	.word	33542
	.word	28786
	.word	18433
	.word	54686
	.word	5814
	.word	3500
	.word	51351
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	33175
	.word	51870
	.word	55516
	.word	61254
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	3685
	.word	30034
	.word	48430
	.word	40287
	.word	49163
	.word	0
	.word	0
	.word	0
	.word	3323
	.word	33320
	.word	53546
	.word	49572
	.word	16406
	.word	0
	.word	0
	.word	0
	.word	19902
	.word	19517
	.word	60782
	.word	40648
	.word	49186
	.word	0
	.word	0
	.word	0
	.word	28672
	.word	31090
	.word	60187
	.word	37497
	.word	16430
	.word	0
	.word	0
	.word	0
	.word	4362
	.word	57115
	.word	1063
	.word	50082
	.word	17298
	.word	34206
	.word	30558
	.word	17506
	.word	19701
	.word	2977
	.word	30695
	.word	50467
	.word	45209
	.word	25144
	.word	62418
	.word	17892
	.word	6287
	.word	3260
	.word	59477
	.word	50854
	.word	26251
	.word	62311
	.word	23506
	.word	18281
	.word	50215
	.word	3065
	.word	23294
	.word	51244
	.word	519
	.word	28602
	.word	60080
	.word	18671
	.word	56824
	.word	33188
	.word	7096
	.word	51634
	.word	27357
	.word	50961
	.word	5574
	.word	19062
	.word	7770
	.word	52374
	.word	38304
	.word	52025
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	33559
	.word	2783
	.word	4720
	.word	38214
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	65291
	.word	24589
	.word	47090
	.word	40315
	.word	49166
	.word	0
	.word	0
	.word	0
	.word	7930
	.word	21505
	.word	15205
	.word	49607
	.word	16412
	.word	0
	.word	0
	.word	0
	.word	61775
	.word	9936
	.word	17817
	.word	40691
	.word	49195
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	37628
	.word	658
	.word	37550
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	14352
	.word	33815
	.word	3114
	.word	50322
	.word	43816
	.word	13363
	.word	33081
	.word	17794
	.word	62601
	.word	27719
	.word	33798
	.word	50803
	.word	1036
	.word	16074
	.word	700
	.word	18277
	.word	33117
	.word	56648
	.word	64173
	.word	51286
	.word	17251
	.word	52492
	.word	29265
	.word	18761
	.word	11155
	.word	27451
	.word	30367
	.word	51772
	.word	7487
	.word	7247
	.word	2371
	.word	19248
	.word	14759
	.word	9929
	.word	13587
	.word	52258
	.word	11730
	.word	26190
	.word	56262
	.word	19733
	.word	56923
	.word	11047
	.word	18072
	.word	52745
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	37589
	.word	36570
	.word	62298
	.word	45320
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	17708
	.word	57749
	.word	28642
	.word	45359
	.word	49169
	.word	0
	.word	0
	.word	0
	.word	40067
	.word	38581
	.word	2689
	.word	62790
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	36505
	.word	63458
	.word	34596
	.word	57945
	.word	49204
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	38946
	.word	9029
	.word	60159
	.word	16454
	.word	0
	.word	0
	.word	0
	.word	61014
	.word	7077
	.word	17362
	.word	50576
	.word	11572
	.word	44638
	.word	49972
	.word	18098
	.word	64171
	.word	46242
	.word	17172
	.word	51158
	.word	7607
	.word	1130
	.word	63213
	.word	18682
	.word	20847
	.word	56361
	.word	38627
	.word	51744
	.word	49497
	.word	58620
	.word	43800
	.word	19268
	.word	63056
	.word	11021
	.word	657
	.word	52330
	.word	42127
	.word	49291
	.word	30722
	.word	19856
	.word	13674
	.word	31481
	.word	1908
	.word	52917
	.word	60194
	.word	12516
	.word	3868
	.word	20445
	.word	3386
	.word	31577
	.word	62924
	.word	53506
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	44838
	.word	25647
	.word	6736
	.word	62345
	.word	16353
	.word	0
	.word	0
	.word	0
	.word	31560
	.word	12645
	.word	60726
	.word	56699
	.word	49172
	.word	0
	.word	0
	.word	0
	.word	56942
	.word	25011
	.word	17364
	.word	49055
	.word	16425
	.word	0
	.word	0
	.word	0
	.word	3371
	.word	3119
	.word	24466
	.word	56588
	.word	49214
	.word	0
	.word	0
	.word	0
	.word	14336
	.word	15684
	.word	2636
	.word	36719
	.word	16468
	.word	0
	.word	0
	.word	0
	.word	47554
	.word	44810
	.word	53673
	.word	50840
	.word	53371
	.word	50832
	.word	58584
	.word	18417
	.word	46548
	.word	9025
	.word	35353
	.word	51530
	.word	2727
	.word	25780
	.word	5967
	.word	19108
	.word	27113
	.word	1073
	.word	59097
	.word	52222
	.word	11834
	.word	38905
	.word	4058
	.word	19800
	.word	56017
	.word	55971
	.word	60637
	.word	52914
	.word	5287
	.word	8439
	.word	65517
	.word	20493
	.word	37028
	.word	58958
	.word	62480
	.word	53607
	.word	57582
	.word	43721
	.word	1738
	.word	21188
	.word	9819
	.word	37368
	.word	17165
	.word	54304
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	13429
	.word	7444
	.word	20593
	.word	34837
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	49742
	.word	37689
	.word	16091
	.word	38981
	.word	49176
	.word	0
	.word	0
	.word	0
	.word	46524
	.word	21314
	.word	39987
	.word	46372
	.word	16432
	.word	0
	.word	0
	.word	0
	.word	14081
	.word	37459
	.word	64348
	.word	36776
	.word	49225
	.word	0
	.word	0
	.word	0
	.word	30720
	.word	57650
	.word	51740
	.word	32812
	.word	16482
	.word	0
	.word	0
	.word	0
	.word	13868
	.word	61714
	.word	32487
	.word	51118
	.word	53288
	.word	8914
	.word	15189
	.word	18750
	.word	25547
	.word	51031
	.word	54157
	.word	51918
	.word	4587
	.word	22569
	.word	2868
	.word	19552
	.word	4628
	.word	56702
	.word	63256
	.word	52720
	.word	60585
	.word	38579
	.word	10763
	.word	20354
	.word	20343
	.word	32825
	.word	42223
	.word	53523
	.word	15259
	.word	21287
	.word	25207
	.word	21157
	.word	19232
	.word	27853
	.word	30722
	.word	54327
	.word	6202
	.word	34921
	.word	46892
	.word	21963
	.word	54314
	.word	63337
	.word	3818
	.word	55135
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	12932
	.word	7413
	.word	50416
	.word	52775
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	49070
	.word	42612
	.word	57119
	.word	58471
	.word	49179
	.word	0
	.word	0
	.word	0
	.word	37517
	.word	48554
	.word	12217
	.word	52169
	.word	16439
	.word	0
	.word	0
	.word	0
	.word	3686
	.word	14060
	.word	10284
	.word	62061
	.word	49235
	.word	0
	.word	0
	.word	0
	.word	12288
	.word	36186
	.word	45004
	.word	41528
	.word	16496
	.word	0
	.word	0
	.word	0
	.word	36572
	.word	10708
	.word	62070
	.word	51404
	.word	43863
	.word	23526
	.word	34237
	.word	19093
	.word	49453
	.word	49937
	.word	30100
	.word	52320
	.word	42622
	.word	48103
	.word	45810
	.word	20009
	.word	63343
	.word	18283
	.word	24986
	.word	53236
	.word	211
	.word	7406
	.word	23986
	.word	20928
	.word	55487
	.word	58939
	.word	35978
	.word	54154
	.word	20786
	.word	29108
	.word	45903
	.word	21845
	.word	43676
	.word	27033
	.word	56912
	.word	55073
	.word	56762
	.word	13326
	.word	51569
	.word	22766
	.word	11195
	.word	61166
	.word	50980
	.word	55993
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	8176
	.word	5164
	.word	64154
	.word	36090
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	19140
	.word	63711
	.word	39190
	.word	47508
	.word	49183
	.word	0
	.word	0
	.word	0
	.word	61957
	.word	29915
	.word	7046
	.word	34440
	.word	16447
	.word	0
	.word	0
	.word	0
	.word	29909
	.word	15770
	.word	39264
	.word	33288
	.word	49247
	.word	0
	.word	0
	.word	0
	.word	38912
	.word	2108
	.word	35420
	.word	36197
	.word	16511
	.word	0
	.word	0
	.word	0
	.word	63755
	.word	16362
	.word	32791
	.word	51700
	.word	7043
	.word	34288
	.word	50374
	.word	19448
	.word	36869
	.word	13569
	.word	51161
	.word	52734
	.word	30422
	.word	39536
	.word	34368
	.word	20483
	.word	2356
	.word	27571
	.word	10627
	.word	53769
	.word	24204
	.word	35273
	.word	27312
	.word	21520
	.word	8451
	.word	46569
	.word	41820
	.word	54805
	.word	23553
	.word	63476
	.word	47626
	.word	22556
	.word	30024
	.word	47627
	.word	14141
	.word	55843
	.word	22061
	.word	10635
	.word	17534
	.word	23595
	.word	50593
	.word	17237
	.word	38322
	.word	56882
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	26357
	.word	41545
	.word	7157
	.word	37888
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	28550
	.word	47270
	.word	51275
	.word	41572
	.word	49187
	.word	0
	.word	0
	.word	0
	.word	7474
	.word	4460
	.word	27226
	.word	52743
	.word	16454
	.word	0
	.word	0
	.word	0
	.word	18816
	.word	51641
	.word	26417
	.word	44610
	.word	49258
	.word	0
	.word	0
	.word	0
	.word	43008
	.word	61733
	.word	61279
	.word	42447
	.word	16526
	.word	0
	.word	0
	.word	0
	.word	45407
	.word	18829
	.word	2399
	.word	52005
	.word	35928
	.word	2098
	.word	15792
	.word	19814
	.word	23614
	.word	62930
	.word	12196
	.word	53160
	.word	7674
	.word	56712
	.word	55660
	.word	20970
	.word	53358
	.word	2019
	.word	18277
	.word	54318
	.word	57563
	.word	186
	.word	18788
	.word	22129
	.word	38957
	.word	32276
	.word	61462
	.word	55475
	.word	46995
	.word	58305
	.word	12150
	.word	23287
	.word	60702
	.word	18274
	.word	9955
	.word	56635
	.word	40407
	.word	43665
	.word	14520
	.word	24448
	.word	50736
	.word	28578
	.word	16181
	.word	57795
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	5404
	.word	12511
	.word	60447
	.word	38763
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	42770
	.word	34093
	.word	51209
	.word	39016
	.word	49191
	.word	0
	.word	0
	.word	0
	.word	6888
	.word	59302
	.word	12508
	.word	46457
	.word	16462
	.word	0
	.word	0
	.word	0
	.word	49565
	.word	60790
	.word	42341
	.word	36877
	.word	49270
	.word	0
	.word	0
	.word	0
	.word	28672
	.word	39351
	.word	39031
	.word	32932
	.word	16542
	.word	0
	.word	0
	.word	0
	.word	35933
	.word	37102
	.word	41613
	.word	52318
	.word	59745
	.word	3284
	.word	26050
	.word	20190
	.word	16458
	.word	62792
	.word	1547
	.word	53599
	.word	38519
	.word	37566
	.word	10560
	.word	21472
	.word	36560
	.word	62869
	.word	6875
	.word	54881
	.word	58623
	.word	15626
	.word	21634
	.word	22754
	.word	22503
	.word	3380
	.word	55147
	.word	56163
	.word	21679
	.word	23644
	.word	42908
	.word	24037
	.word	43760
	.word	1032
	.word	52442
	.word	57447
	.word	14782
	.word	45605
	.word	45261
	.word	25322
	.word	22271
	.word	11633
	.word	47287
	.word	58733
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_LRP,@object
	.size	_LRP,2496
	.align 16
_RRIB:
	.long	4234632914
	.long	3221491298
	.long	2516645996
	.long	3221465084
	.long	798659078
	.long	3221438870
	.long	2133702104
	.long	3221835523
	.long	3164494255
	.long	3221825037
	.long	4195286406
	.long	3221814551
	.long	816546054
	.long	3222285677
	.long	2019136897
	.long	3222284366
	.long	3221727740
	.long	3222283055
	.long	2093868532
	.long	3222538608
	.long	1475393241
	.long	3222538346
	.long	856917950
	.long	3222538084
	.long	2471449286
	.long	3222798751
	.long	629767309
	.long	3222798699
	.long	3083052628
	.long	3222798646
	.long	862741805
	.long	3223060539
	.long	4260232735
	.long	3223060531
	.long	3362756369
	.long	3223060524
	.long	3046734174
	.long	3223322627
	.long	1076409337
	.long	3223322627
	.long	3401051796
	.long	3223322626
	.long	1762435764
	.long	3223453696
	.long	1551329531
	.long	3223453696
	.long	1340223298
	.long	3223453696
	.long	175962963
	.long	3223584768
	.long	155133815
	.long	3223584768
	.long	134304667
	.long	3223584768
	.long	16073407
	.long	3223715840
	.long	14103082
	.long	3223715840
	.long	12132757
	.long	3223715840
	.long	1330068
	.long	3223846912
	.long	1175257
	.long	3223846912
	.long	1020446
	.long	3223846912
	.long	101663
	.long	3223977984
	.long	90404
	.long	3223977984
	.long	79145
	.long	3223977984
	.long	7020
	.long	3224109056
	.long	6457
	.long	3224109056
	.long	5894
	.long	3224109056
	.type	_RRIB,@object
	.size	_RRIB,312
	.space 8, 0x00 	# pad
	.align 16
_RRP:
	.word	26273
	.word	65347
	.word	56168
	.word	33169
	.word	16329
	.word	0
	.word	0
	.word	0
	.word	899
	.word	31387
	.word	19253
	.word	49663
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	58984
	.word	6058
	.word	23936
	.word	39799
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	62168
	.word	21442
	.word	12332
	.word	46245
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	32619
	.word	37054
	.word	53131
	.word	16575
	.word	61379
	.word	13125
	.word	14178
	.word	16562
	.word	10270
	.word	45115
	.word	37615
	.word	16506
	.word	38745
	.word	51073
	.word	34560
	.word	16498
	.word	53490
	.word	20212
	.word	2544
	.word	16440
	.word	38291
	.word	26669
	.word	54063
	.word	16440
	.word	45779
	.word	11956
	.word	18019
	.word	16540
	.word	18677
	.word	64033
	.word	42794
	.word	16529
	.word	35035
	.word	51098
	.word	10618
	.word	16473
	.word	64107
	.word	31719
	.word	36522
	.word	16468
	.word	8831
	.word	57419
	.word	13215
	.word	16407
	.word	40450
	.word	31904
	.word	29069
	.word	16417
	.word	3821
	.word	29651
	.word	12061
	.word	62640
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	3177
	.word	32099
	.word	13049
	.word	63749
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	10153
	.word	35464
	.word	38075
	.word	52902
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	20859
	.word	27364
	.word	47250
	.word	57481
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	12411
	.word	3381
	.word	24721
	.word	16978
	.word	33723
	.word	48761
	.word	26869
	.word	16934
	.word	60995
	.word	48715
	.word	17741
	.word	16804
	.word	43031
	.word	16238
	.word	40105
	.word	16761
	.word	3889
	.word	41280
	.word	47482
	.word	16635
	.word	43797
	.word	63405
	.word	37827
	.word	16594
	.word	62186
	.word	39109
	.word	61647
	.word	16889
	.word	62356
	.word	27755
	.word	9779
	.word	16848
	.word	54712
	.word	4438
	.word	22518
	.word	16720
	.word	46932
	.word	1372
	.word	7843
	.word	16677
	.word	64555
	.word	14297
	.word	39523
	.word	16553
	.word	24913
	.word	15735
	.word	26400
	.word	16514
	.word	23128
	.word	31275
	.word	33489
	.word	52246
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	56543
	.word	37040
	.word	59300
	.word	54866
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	35342
	.word	2554
	.word	44652
	.word	41504
	.word	16391
	.word	0
	.word	0
	.word	0
	.word	60438
	.word	21728
	.word	35033
	.word	43725
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	2425
	.word	35124
	.word	13908
	.word	17426
	.word	60985
	.word	47869
	.word	25665
	.word	17352
	.word	21213
	.word	45459
	.word	26876
	.word	17133
	.word	52591
	.word	14597
	.word	24526
	.word	17060
	.word	20426
	.word	18941
	.word	5374
	.word	16845
	.word	38724
	.word	49064
	.word	24125
	.word	16773
	.word	29994
	.word	36366
	.word	21088
	.word	17279
	.word	13269
	.word	25406
	.word	24800
	.word	17205
	.word	56893
	.word	35190
	.word	33689
	.word	16988
	.word	18235
	.word	52539
	.word	13276
	.word	16916
	.word	8454
	.word	46451
	.word	9758
	.word	16704
	.word	59084
	.word	57882
	.word	27928
	.word	16633
	.word	38110
	.word	57460
	.word	5118
	.word	49980
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	32056
	.word	19825
	.word	28737
	.word	63161
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	37650
	.word	4479
	.word	6518
	.word	59238
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	47963
	.word	64405
	.word	49841
	.word	37534
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	53082
	.word	25478
	.word	58397
	.word	17968
	.word	35980
	.word	11362
	.word	59289
	.word	17858
	.word	37178
	.word	29007
	.word	44012
	.word	17530
	.word	40762
	.word	17715
	.word	56491
	.word	17422
	.word	58318
	.word	3223
	.word	43216
	.word	17097
	.word	46157
	.word	23530
	.word	32141
	.word	16991
	.word	63138
	.word	35190
	.word	24151
	.word	17748
	.word	9862
	.word	36309
	.word	14376
	.word	17639
	.word	38034
	.word	65220
	.word	2337
	.word	17314
	.word	16819
	.word	31850
	.word	22525
	.word	17205
	.word	7604
	.word	29558
	.word	57367
	.word	16883
	.word	27719
	.word	62170
	.word	8797
	.word	16778
	.word	19348
	.word	65423
	.word	37299
	.word	53534
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	8233
	.word	55180
	.word	9457
	.word	46319
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	43066
	.word	26574
	.word	6722
	.word	65137
	.word	16400
	.word	0
	.word	0
	.word	0
	.word	4285
	.word	7102
	.word	7454
	.word	61224
	.word	16409
	.word	0
	.word	0
	.word	0
	.word	1453
	.word	4915
	.word	61926
	.word	18584
	.word	43088
	.word	28644
	.word	50480
	.word	18434
	.word	16532
	.word	18100
	.word	54137
	.word	17983
	.word	49885
	.word	46980
	.word	54409
	.word	17832
	.word	24326
	.word	38273
	.word	20489
	.word	17385
	.word	28149
	.word	12270
	.word	61982
	.word	17236
	.word	4581
	.word	37632
	.word	45525
	.word	18282
	.word	46626
	.word	30655
	.word	34430
	.word	18132
	.word	63429
	.word	60638
	.word	37201
	.word	17683
	.word	18031
	.word	50879
	.word	14891
	.word	17535
	.word	64064
	.word	1811
	.word	54229
	.word	17089
	.word	27163
	.word	21077
	.word	40059
	.word	16943
	.word	43177
	.word	42972
	.word	46613
	.word	45511
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	51480
	.word	49923
	.word	15697
	.word	40352
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	9513
	.word	31676
	.word	11989
	.word	49652
	.word	16406
	.word	0
	.word	0
	.word	0
	.word	30527
	.word	7034
	.word	38319
	.word	40746
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	7022
	.word	27009
	.word	58741
	.word	19257
	.word	64033
	.word	16049
	.word	22023
	.word	19062
	.word	45243
	.word	42262
	.word	39716
	.word	18476
	.word	50731
	.word	20793
	.word	36853
	.word	18281
	.word	36100
	.word	48270
	.word	37858
	.word	17699
	.word	42104
	.word	28941
	.word	36380
	.word	17506
	.word	23499
	.word	6717
	.word	19492
	.word	18866
	.word	55911
	.word	14060
	.word	7353
	.word	18672
	.word	26519
	.word	16988
	.word	4787
	.word	18087
	.word	28065
	.word	5309
	.word	5697
	.word	17893
	.word	40193
	.word	22022
	.word	5795
	.word	17314
	.word	8456
	.word	42078
	.word	24130
	.word	17122
	.word	59016
	.word	62162
	.word	39673
	.word	39540
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	38154
	.word	49049
	.word	18422
	.word	40324
	.word	16398
	.word	0
	.word	0
	.word	0
	.word	42164
	.word	50610
	.word	50330
	.word	49617
	.word	16412
	.word	0
	.word	0
	.word	0
	.word	25875
	.word	59750
	.word	15469
	.word	40704
	.word	16427
	.word	0
	.word	0
	.word	0
	.word	34173
	.word	58862
	.word	54392
	.word	19977
	.word	59819
	.word	8454
	.word	18296
	.word	19734
	.word	30024
	.word	20882
	.word	32589
	.word	19004
	.word	29754
	.word	31085
	.word	31060
	.word	18761
	.word	52191
	.word	27376
	.word	34749
	.word	18035
	.word	4562
	.word	4929
	.word	33854
	.word	17794
	.word	25001
	.word	9100
	.word	13901
	.word	19490
	.word	47424
	.word	1290
	.word	2840
	.word	19248
	.word	22386
	.word	25633
	.word	77
	.word	18519
	.word	15514
	.word	17548
	.word	1870
	.word	18277
	.word	63012
	.word	41083
	.word	3742
	.word	17554
	.word	48968
	.word	51531
	.word	22462
	.word	17314
	.word	41404
	.word	42732
	.word	62757
	.word	38778
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	7009
	.word	42712
	.word	36892
	.word	45360
	.word	16401
	.word	0
	.word	0
	.word	0
	.word	39847
	.word	2109
	.word	39294
	.word	62791
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	32179
	.word	43914
	.word	44915
	.word	57947
	.word	16436
	.word	0
	.word	0
	.word	0
	.word	27779
	.word	16034
	.word	51140
	.word	20738
	.word	17003
	.word	57008
	.word	53620
	.word	20444
	.word	23469
	.word	38349
	.word	865
	.word	19562
	.word	25226
	.word	11109
	.word	43957
	.word	19268
	.word	36845
	.word	29881
	.word	17299
	.word	18390
	.word	19837
	.word	16116
	.word	50064
	.word	18098
	.word	57862
	.word	29290
	.word	2992
	.word	20149
	.word	46818
	.word	59090
	.word	31382
	.word	19856
	.word	61466
	.word	30954
	.word	38749
	.word	18976
	.word	30984
	.word	39998
	.word	63388
	.word	18682
	.word	41543
	.word	16681
	.word	17428
	.word	17808
	.word	20643
	.word	64049
	.word	24643
	.word	17517
	.word	52468
	.word	15578
	.word	48717
	.word	44571
	.word	16353
	.word	0
	.word	0
	.word	0
	.word	54595
	.word	6191
	.word	4821
	.word	56700
	.word	16404
	.word	0
	.word	0
	.word	0
	.word	50514
	.word	10282
	.word	25695
	.word	49055
	.word	16425
	.word	0
	.word	0
	.word	0
	.word	13176
	.word	11594
	.word	38881
	.word	56588
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	28019
	.word	59660
	.word	42453
	.word	21536
	.word	50810
	.word	20541
	.word	28938
	.word	21188
	.word	53928
	.word	47752
	.word	60679
	.word	20146
	.word	24270
	.word	60826
	.word	4097
	.word	19800
	.word	57327
	.word	59875
	.word	35368
	.word	18762
	.word	31038
	.word	58486
	.word	58593
	.word	18417
	.word	41074
	.word	26743
	.word	60755
	.word	20839
	.word	49334
	.word	8669
	.word	63919
	.word	20493
	.word	20472
	.word	19395
	.word	59120
	.word	19454
	.word	27230
	.word	61299
	.word	5980
	.word	19108
	.word	60536
	.word	14479
	.word	53684
	.word	18072
	.word	24589
	.word	24698
	.word	60903
	.word	17729
	.word	24724
	.word	11272
	.word	24781
	.word	60135
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	54850
	.word	34200
	.word	16766
	.word	38981
	.word	16408
	.word	0
	.word	0
	.word	0
	.word	58717
	.word	18738
	.word	40849
	.word	46372
	.word	16432
	.word	0
	.word	0
	.word	0
	.word	11614
	.word	63671
	.word	65373
	.word	36776
	.word	16457
	.word	0
	.word	0
	.word	0
	.word	54464
	.word	24503
	.word	3823
	.word	22367
	.word	6780
	.word	11041
	.word	46896
	.word	21963
	.word	31922
	.word	33372
	.word	42225
	.word	20755
	.word	24666
	.word	18144
	.word	10765
	.word	20354
	.word	1690
	.word	51390
	.word	54159
	.word	19150
	.word	40728
	.word	53860
	.word	15190
	.word	18750
	.word	62163
	.word	16835
	.word	30725
	.word	21559
	.word	49586
	.word	46435
	.word	25209
	.word	21157
	.word	392
	.word	18629
	.word	63258
	.word	19952
	.word	57023
	.word	35208
	.word	2869
	.word	19552
	.word	15849
	.word	23514
	.word	32489
	.word	18350
	.word	45455
	.word	56342
	.word	1433
	.word	17952
	.word	26203
	.word	24931
	.word	14914
	.word	64372
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	32494
	.word	4334
	.word	57160
	.word	58471
	.word	16411
	.word	0
	.word	0
	.word	0
	.word	38313
	.word	48554
	.word	12217
	.word	52169
	.word	16439
	.word	0
	.word	0
	.word	0
	.word	13841
	.word	14060
	.word	10284
	.word	62061
	.word	16467
	.word	0
	.word	0
	.word	0
	.word	27928
	.word	25887
	.word	22600
	.word	23226
	.word	25867
	.word	43031
	.word	24792
	.word	22767
	.word	61361
	.word	28179
	.word	36008
	.word	21386
	.word	1901
	.word	19713
	.word	23997
	.word	20928
	.word	53849
	.word	49977
	.word	30100
	.word	19552
	.word	12987
	.word	23545
	.word	34237
	.word	19093
	.word	36035
	.word	18526
	.word	55738
	.word	22305
	.word	4173
	.word	53170
	.word	44844
	.word	21845
	.word	20134
	.word	6065
	.word	24986
	.word	20468
	.word	63684
	.word	40633
	.word	45810
	.word	20009
	.word	28281
	.word	10708
	.word	62070
	.word	18636
	.word	42557
	.word	63889
	.word	18197
	.word	18180
	.word	24464
	.word	43564
	.word	52277
	.word	36083
	.word	16365
	.word	0
	.word	0
	.word	0
	.word	8651
	.word	37688
	.word	39193
	.word	47508
	.word	16415
	.word	0
	.word	0
	.word	0
	.word	61957
	.word	29915
	.word	7046
	.word	34440
	.word	16447
	.word	0
	.word	0
	.word	0
	.word	29909
	.word	15770
	.word	39264
	.word	33288
	.word	16479
	.word	0
	.word	0
	.word	0
	.word	16042
	.word	19253
	.word	38322
	.word	24114
	.word	28448
	.word	10617
	.word	17534
	.word	23595
	.word	64229
	.word	46570
	.word	41820
	.word	22037
	.word	23727
	.word	35273
	.word	27312
	.word	21520
	.word	36874
	.word	13569
	.word	51161
	.word	19966
	.word	7043
	.word	34288
	.word	50374
	.word	19448
	.word	38316
	.word	47529
	.word	14141
	.word	23075
	.word	12297
	.word	63477
	.word	47626
	.word	22556
	.word	1229
	.word	27571
	.word	10627
	.word	21001
	.word	30426
	.word	39536
	.word	34368
	.word	20483
	.word	63755
	.word	16362
	.word	32791
	.word	18932
	.word	1939
	.word	19329
	.word	44209
	.word	18417
	.word	1291
	.word	6677
	.word	4999
	.word	37888
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	36671
	.word	58224
	.word	51275
	.word	41572
	.word	16419
	.word	0
	.word	0
	.word	0
	.word	7476
	.word	4460
	.word	27226
	.word	52743
	.word	16454
	.word	0
	.word	0
	.word	0
	.word	18838
	.word	51641
	.word	26417
	.word	44610
	.word	16490
	.word	0
	.word	0
	.word	0
	.word	10710
	.word	48640
	.word	32083
	.word	25027
	.word	33620
	.word	55766
	.word	26236
	.word	24448
	.word	24667
	.word	39142
	.word	61464
	.word	22707
	.word	56170
	.word	8470
	.word	18789
	.word	22129
	.word	23296
	.word	62931
	.word	12196
	.word	20392
	.word	57231
	.word	2098
	.word	15792
	.word	19814
	.word	1704
	.word	49608
	.word	9547
	.word	23867
	.word	46419
	.word	24648
	.word	11891
	.word	23287
	.word	19376
	.word	1296
	.word	18277
	.word	21550
	.word	46223
	.word	56399
	.word	55660
	.word	20970
	.word	45364
	.word	18829
	.word	2399
	.word	19237
	.word	9387
	.word	60414
	.word	47613
	.word	18660
	.type	_RRP,@object
	.size	_RRP,2080
	.align 16
_LN_SQRT_TWO_PI:
	.word	42293
	.word	9717
	.word	36419
	.word	60223
	.word	16382
	.word	0
	.type	_LN_SQRT_TWO_PI,@object
	.size	_LN_SQRT_TWO_PI,12
	.space 4, 0x00 	# pad
	.align 16
_W2:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16379
	.word	0
	.type	_W2,@object
	.size	_W2,12
	.space 4, 0x00 	# pad
	.align 16
_S10:
	.word	26777
	.word	63736
	.word	61426
	.word	52480
	.word	49148
	.word	0
	.type	_S10,@object
	.size	_S10,12
	.space 4, 0x00 	# pad
	.align 16
_S06:
	.word	26251
	.word	64205
	.word	25991
	.word	44448
	.word	49149
	.word	0
	.type	_S06,@object
	.size	_S06,12
	.space 4, 0x00 	# pad
	.align 16
_S02:
	.word	39072
	.word	38974
	.word	13074
	.word	53901
	.word	49151
	.word	0
	.type	_S02,@object
	.size	_S02,12
	.space 4, 0x00 	# pad
	.align 16
_S12:
	.word	13864
	.word	61389
	.word	41932
	.word	43701
	.word	49148
	.word	0
	.type	_S12,@object
	.size	_S12,12
	.space 4, 0x00 	# pad
	.align 16
_S08:
	.word	6565
	.word	12686
	.word	39772
	.word	32901
	.word	49149
	.word	0
	.type	_S08,@object
	.size	_S08,12
	.space 4, 0x00 	# pad
	.align 16
_S04:
	.word	60211
	.word	16071
	.word	37206
	.word	35465
	.word	49150
	.word	0
	.type	_S04,@object
	.size	_S04,12
	.space 4, 0x00 	# pad
	.align 16
_A:
	.long	0
	.long	1074790400
	.long	0
	.long	1075838976
	.long	0
	.long	1076887552
	.long	0
	.long	1077936128
	.long	0
	.long	1078984704
	.long	0
	.long	1080033280
	.long	0
	.long	1081081856
	.type	_A,@object
	.size	_A,56
	.space 8, 0x00 	# pad
	.align 16
_B:
	.long	0
	.long	1070596096
	.long	0
	.long	1069547520
	.long	0
	.long	1068498944
	.long	0
	.long	1067450368
	.long	0
	.long	1066401792
	.long	0
	.long	1065353216
	.long	0
	.long	1064304640
	.type	_B,@object
	.size	_B,56
	.space 8, 0x00 	# pad
	.align 16
_C5:
	.word	65250
	.word	31226
	.word	64195
	.word	51294
	.word	16385
	.word	0
	.word	2062
	.word	11523
	.word	15490
	.word	40735
	.word	49153
	.word	0
	.word	32754
	.word	31980
	.word	31403
	.word	49421
	.word	16385
	.word	0
	.word	43174
	.word	39951
	.word	44282
	.word	37916
	.word	49153
	.word	0
	.word	17740
	.word	24638
	.word	31711
	.word	45907
	.word	16385
	.word	0
	.word	40119
	.word	37337
	.word	13155
	.word	65076
	.word	49152
	.word	0
	.word	17604
	.word	23486
	.word	20029
	.word	41172
	.word	16385
	.word	0
	.word	33185
	.word	5727
	.word	44714
	.word	50187
	.word	49152
	.word	0
	.word	59315
	.word	25153
	.word	8859
	.word	35740
	.word	16385
	.word	0
	.word	56975
	.word	59828
	.word	57702
	.word	65082
	.word	49151
	.word	0
	.word	44451
	.word	24185
	.word	18874
	.word	60288
	.word	16384
	.word	0
	.word	63603
	.word	63450
	.word	63770
	.word	55108
	.word	49150
	.word	0
	.word	29224
	.word	39171
	.word	38378
	.word	49676
	.word	16384
	.word	0
	.word	11522
	.word	59843
	.word	6225
	.word	34695
	.word	16381
	.word	0
	.word	52920
	.word	16467
	.word	35974
	.word	40266
	.word	16384
	.word	0
	.word	32911
	.word	20526
	.word	49212
	.word	40083
	.word	16383
	.word	0
	.word	50633
	.word	19120
	.word	5716
	.word	64631
	.word	16383
	.word	0
	.word	27278
	.word	20509
	.word	60679
	.word	63371
	.word	16383
	.word	0
	.word	47711
	.word	23018
	.word	38994
	.word	51179
	.word	16383
	.word	0
	.word	57200
	.word	35818
	.word	16826
	.word	37630
	.word	16384
	.word	0
	.word	2413
	.word	19605
	.word	64315
	.word	49218
	.word	16381
	.word	0
	.word	41670
	.word	6403
	.word	34702
	.word	40993
	.word	16383
	.word	0
	.word	6011
	.word	2134
	.word	32659
	.word	52266
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	42605
	.word	32107
	.word	35187
	.word	43545
	.word	16382
	.word	0
	.word	3574
	.word	60450
	.word	45882
	.word	53610
	.word	16383
	.word	0
	.word	2808
	.word	20835
	.word	5027
	.word	56256
	.word	16383
	.word	0
	.word	3021
	.word	28181
	.word	33636
	.word	39028
	.word	16384
	.word	0
	.word	14493
	.word	33433
	.word	18281
	.word	35725
	.word	16384
	.word	0
	.word	39323
	.word	51299
	.word	2596
	.word	32961
	.word	16384
	.word	0
	.word	28336
	.word	24032
	.word	53553
	.word	44237
	.word	16384
	.word	0
	.word	52853
	.word	63658
	.word	36534
	.word	41882
	.word	16383
	.word	0
	.word	47917
	.word	51227
	.word	19034
	.word	54081
	.word	16384
	.word	0
	.word	51044
	.word	31166
	.word	18963
	.word	37856
	.word	16381
	.word	0
	.word	35962
	.word	40199
	.word	871
	.word	65049
	.word	16384
	.word	0
	.word	39889
	.word	54182
	.word	40011
	.word	55797
	.word	49150
	.word	0
	.word	29169
	.word	47361
	.word	8730
	.word	38268
	.word	16385
	.word	0
	.word	39706
	.word	37143
	.word	20168
	.word	33289
	.word	49152
	.word	0
	.word	16433
	.word	42895
	.word	21426
	.word	43820
	.word	16385
	.word	0
	.word	1625
	.word	9463
	.word	65082
	.word	51468
	.word	49152
	.word	0
	.word	58247
	.word	1594
	.word	2790
	.word	48648
	.word	16385
	.word	0
	.word	20868
	.word	31765
	.word	34026
	.word	33401
	.word	49153
	.word	0
	.word	23737
	.word	4448
	.word	39427
	.word	52225
	.word	16385
	.word	0
	.word	3456
	.word	40222
	.word	65015
	.word	38939
	.word	49153
	.word	0
	.word	35551
	.word	41260
	.word	40325
	.word	54131
	.word	16385
	.word	0
	.word	8973
	.word	47485
	.word	2472
	.word	41842
	.word	49153
	.word	0
	.word	43053
	.word	31703
	.word	19755
	.word	33274
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	36731
	.word	27611
	.word	11212
	.word	55816
	.word	16382
	.word	0
	.word	63893
	.word	30149
	.word	45246
	.word	60594
	.word	16383
	.word	0
	.word	47198
	.word	37666
	.word	57801
	.word	61119
	.word	16383
	.word	0
	.word	25856
	.word	56265
	.word	35798
	.word	40402
	.word	16384
	.word	0
	.word	6470
	.word	34102
	.word	54471
	.word	38654
	.word	16384
	.word	0
	.word	9295
	.word	39366
	.word	17179
	.word	34081
	.word	16384
	.word	0
	.word	45822
	.word	31766
	.word	42784
	.word	47587
	.word	16384
	.word	0
	.word	5059
	.word	11140
	.word	37473
	.word	43359
	.word	16383
	.word	0
	.word	58055
	.word	60744
	.word	5900
	.word	57832
	.word	16384
	.word	0
	.word	38212
	.word	45613
	.word	58947
	.word	40054
	.word	16381
	.word	0
	.word	7938
	.word	56610
	.word	27106
	.word	34591
	.word	16385
	.word	0
	.word	32824
	.word	16764
	.word	60057
	.word	56720
	.word	49150
	.word	0
	.word	10908
	.word	30532
	.word	62164
	.word	40510
	.word	16385
	.word	0
	.word	63750
	.word	13029
	.word	27501
	.word	34026
	.word	49152
	.word	0
	.word	33477
	.word	11068
	.word	13144
	.word	46215
	.word	16385
	.word	0
	.word	42530
	.word	57434
	.word	58521
	.word	52671
	.word	49152
	.word	0
	.word	18986
	.word	9605
	.word	39740
	.word	51163
	.word	16385
	.word	0
	.word	28928
	.word	13634
	.word	42974
	.word	34196
	.word	49153
	.word	0
	.word	35215
	.word	30142
	.word	47225
	.word	54824
	.word	16385
	.word	0
	.word	28719
	.word	4316
	.word	52012
	.word	39873
	.word	49153
	.word	0
	.word	4508
	.word	8703
	.word	27075
	.word	56773
	.word	16385
	.word	0
	.word	26288
	.word	30246
	.word	8808
	.word	42848
	.word	49153
	.word	0
	.word	44455
	.word	3575
	.word	25942
	.word	45691
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	2794
	.word	62330
	.word	8461
	.word	63853
	.word	16382
	.word	0
	.word	21479
	.word	44765
	.word	7865
	.word	64840
	.word	16383
	.word	0
	.word	31874
	.word	33681
	.word	56423
	.word	65041
	.word	16383
	.word	0
	.word	21473
	.word	44172
	.word	64358
	.word	41494
	.word	16384
	.word	0
	.word	32222
	.word	10523
	.word	55592
	.word	40956
	.word	16384
	.word	0
	.word	21434
	.word	30837
	.word	63797
	.word	34911
	.word	16384
	.word	0
	.word	39649
	.word	32992
	.word	36682
	.word	50257
	.word	16384
	.word	0
	.word	13095
	.word	39473
	.word	7384
	.word	44359
	.word	16383
	.word	0
	.word	31843
	.word	64809
	.word	59872
	.word	60895
	.word	16384
	.word	0
	.word	8672
	.word	5021
	.word	16314
	.word	40945
	.word	16381
	.word	0
	.word	39714
	.word	18801
	.word	4723
	.word	36324
	.word	16385
	.word	0
	.word	614
	.word	42734
	.word	5731
	.word	57950
	.word	49150
	.word	0
	.word	64434
	.word	24821
	.word	2679
	.word	42435
	.word	16385
	.word	0
	.word	12194
	.word	23846
	.word	20957
	.word	34750
	.word	49152
	.word	0
	.word	10830
	.word	21930
	.word	2400
	.word	48308
	.word	16385
	.word	0
	.word	22166
	.word	52453
	.word	43476
	.word	53777
	.word	49152
	.word	0
	.word	47257
	.word	46311
	.word	24367
	.word	53391
	.word	16385
	.word	0
	.word	54217
	.word	40926
	.word	61378
	.word	34907
	.word	49153
	.word	0
	.word	63951
	.word	56465
	.word	60729
	.word	57145
	.word	16385
	.word	0
	.word	26614
	.word	13946
	.word	18662
	.word	40698
	.word	49153
	.word	0
	.word	1838
	.word	12847
	.word	15941
	.word	59142
	.word	16385
	.word	0
	.word	4759
	.word	49273
	.word	38799
	.word	43731
	.word	49153
	.word	0
	.word	26568
	.word	56798
	.word	57629
	.word	34217
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	25804
	.word	39657
	.word	51638
	.word	34652
	.word	16383
	.word	0
	.word	2771
	.word	24731
	.word	11874
	.word	33806
	.word	16384
	.word	0
	.word	19474
	.word	34269
	.word	14010
	.word	33912
	.word	16384
	.word	0
	.word	56824
	.word	53459
	.word	23053
	.word	42279
	.word	16384
	.word	0
	.word	18742
	.word	13031
	.word	52011
	.word	42483
	.word	16384
	.word	0
	.word	5937
	.word	64580
	.word	50859
	.word	35501
	.word	16384
	.word	0
	.word	23055
	.word	38603
	.word	6524
	.word	51938
	.word	16384
	.word	0
	.word	3664
	.word	27625
	.word	50793
	.word	45121
	.word	16383
	.word	0
	.word	52489
	.word	3557
	.word	32438
	.word	62735
	.word	16384
	.word	0
	.word	894
	.word	16203
	.word	51477
	.word	42152
	.word	16381
	.word	0
	.word	34963
	.word	47118
	.word	6430
	.word	37322
	.word	16385
	.word	0
	.word	26422
	.word	42936
	.word	42081
	.word	58310
	.word	49150
	.word	0
	.word	13130
	.word	21399
	.word	54693
	.word	43505
	.word	16385
	.word	0
	.word	30323
	.word	50910
	.word	30337
	.word	35076
	.word	49152
	.word	0
	.word	38115
	.word	10576
	.word	10000
	.word	49442
	.word	16385
	.word	0
	.word	37599
	.word	18432
	.word	63354
	.word	54317
	.word	49152
	.word	0
	.word	58822
	.word	42699
	.word	64187
	.word	54575
	.word	16385
	.word	0
	.word	64288
	.word	61574
	.word	23831
	.word	35266
	.word	49153
	.word	0
	.word	11805
	.word	33285
	.word	40274
	.word	58365
	.word	16385
	.word	0
	.word	20167
	.word	739
	.word	36989
	.word	41119
	.word	49153
	.word	0
	.word	62447
	.word	46690
	.word	58491
	.word	60379
	.word	16385
	.word	0
	.word	1463
	.word	47000
	.word	35484
	.word	44185
	.word	49153
	.word	0
	.word	64817
	.word	40328
	.word	40776
	.word	61320
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	60250
	.word	42976
	.word	52322
	.word	36598
	.word	16383
	.word	0
	.word	26918
	.word	53639
	.word	1629
	.word	34777
	.word	16384
	.word	0
	.word	192
	.word	43979
	.word	65472
	.word	34978
	.word	16384
	.word	0
	.word	52261
	.word	56023
	.word	49807
	.word	42881
	.word	16384
	.word	0
	.word	1627
	.word	46939
	.word	43260
	.word	43630
	.word	16384
	.word	0
	.word	60557
	.word	13735
	.word	20430
	.word	35946
	.word	16384
	.word	0
	.word	37731
	.word	15708
	.word	47147
	.word	53188
	.word	16384
	.word	0
	.word	21753
	.word	29315
	.word	41089
	.word	45691
	.word	16383
	.word	0
	.word	6001
	.word	56966
	.word	64901
	.word	64096
	.word	16384
	.word	0
	.word	60203
	.word	9392
	.word	60017
	.word	43055
	.word	16381
	.word	0
	.word	63698
	.word	8838
	.word	668
	.word	38058
	.word	16385
	.word	0
	.word	63354
	.word	11979
	.word	38628
	.word	58569
	.word	49150
	.word	0
	.word	14495
	.word	23154
	.word	18917
	.word	44293
	.word	16385
	.word	0
	.word	21391
	.word	34042
	.word	43308
	.word	35313
	.word	49152
	.word	0
	.word	19364
	.word	56057
	.word	34969
	.word	50274
	.word	16385
	.word	0
	.word	24688
	.word	56191
	.word	53239
	.word	54710
	.word	49152
	.word	0
	.word	29387
	.word	5660
	.word	12964
	.word	55444
	.word	16385
	.word	0
	.word	35686
	.word	64227
	.word	46433
	.word	35526
	.word	49153
	.word	0
	.word	36666
	.word	59843
	.word	47733
	.word	59258
	.word	16385
	.word	0
	.word	36678
	.word	17293
	.word	20572
	.word	41425
	.word	49153
	.word	0
	.word	18431
	.word	63251
	.word	49151
	.word	61285
	.word	16385
	.word	0
	.word	60545
	.word	37455
	.word	56348
	.word	44514
	.word	49153
	.word	0
	.word	37261
	.word	1613
	.word	29792
	.word	57954
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	15271
	.word	17124
	.word	50042
	.word	38057
	.word	16383
	.word	0
	.word	7933
	.word	60764
	.word	31972
	.word	35498
	.word	16384
	.word	0
	.word	36698
	.word	903
	.word	21735
	.word	35834
	.word	16384
	.word	0
	.word	61921
	.word	64342
	.word	37428
	.word	43367
	.word	16384
	.word	0
	.word	25723
	.word	57108
	.word	17005
	.word	44538
	.word	16384
	.word	0
	.word	55361
	.word	30601
	.word	552
	.word	36302
	.word	16384
	.word	0
	.word	60144
	.word	14680
	.word	32635
	.word	54172
	.word	16384
	.word	0
	.word	56958
	.word	12456
	.word	10479
	.word	46147
	.word	16383
	.word	0
	.word	483
	.word	34108
	.word	14727
	.word	65164
	.word	16384
	.word	0
	.word	43794
	.word	49057
	.word	50532
	.word	43789
	.word	16381
	.word	0
	.word	12225
	.word	29228
	.word	35085
	.word	38633
	.word	16385
	.word	0
	.word	54219
	.word	64754
	.word	11030
	.word	58760
	.word	49150
	.word	0
	.word	52932
	.word	63137
	.word	8758
	.word	44908
	.word	16385
	.word	0
	.word	27270
	.word	37632
	.word	62601
	.word	35495
	.word	49152
	.word	0
	.word	53773
	.word	63879
	.word	47305
	.word	50923
	.word	16385
	.word	0
	.word	29965
	.word	53650
	.word	14162
	.word	55014
	.word	49152
	.word	0
	.word	10444
	.word	24307
	.word	52989
	.word	56120
	.word	16385
	.word	0
	.word	12949
	.word	6021
	.word	2059
	.word	35728
	.word	49153
	.word	0
	.word	41140
	.word	26313
	.word	26076
	.word	59954
	.word	16385
	.word	0
	.word	53675
	.word	12584
	.word	54309
	.word	41661
	.word	49153
	.word	0
	.word	28849
	.word	13964
	.word	11627
	.word	61991
	.word	16385
	.word	0
	.word	33057
	.word	63851
	.word	41026
	.word	44769
	.word	49153
	.word	0
	.word	61939
	.word	60366
	.word	37868
	.word	56319
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_C5,@object
	.size	_C5,2016
	.align 16
_C4:
	.word	39764
	.word	13333
	.word	46799
	.word	55432
	.word	16385
	.word	0
	.word	6306
	.word	35111
	.word	59607
	.word	63624
	.word	49153
	.word	0
	.word	40772
	.word	10749
	.word	40067
	.word	48311
	.word	16382
	.word	0
	.word	17977
	.word	41258
	.word	10043
	.word	63023
	.word	16381
	.word	0
	.word	17757
	.word	54940
	.word	38650
	.word	52104
	.word	16384
	.word	0
	.word	36152
	.word	14316
	.word	50442
	.word	43322
	.word	49148
	.word	0
	.word	48990
	.word	25502
	.word	42020
	.word	58640
	.word	16385
	.word	0
	.word	57431
	.word	49505
	.word	17362
	.word	46284
	.word	49151
	.word	0
	.word	34483
	.word	2420
	.word	54299
	.word	50782
	.word	16386
	.word	0
	.word	37575
	.word	3091
	.word	26415
	.word	50860
	.word	49152
	.word	0
	.word	5185
	.word	25440
	.word	63351
	.word	37946
	.word	16387
	.word	0
	.word	37934
	.word	12918
	.word	47534
	.word	41618
	.word	49153
	.word	0
	.word	59537
	.word	51974
	.word	16286
	.word	51369
	.word	16387
	.word	0
	.word	31611
	.word	51820
	.word	58572
	.word	58708
	.word	49153
	.word	0
	.word	57757
	.word	54077
	.word	23114
	.word	64558
	.word	16387
	.word	0
	.word	63584
	.word	48263
	.word	62815
	.word	37616
	.word	49154
	.word	0
	.word	281
	.word	14439
	.word	46939
	.word	38182
	.word	16388
	.word	0
	.word	63169
	.word	10307
	.word	53265
	.word	44896
	.word	49154
	.word	0
	.word	22654
	.word	54923
	.word	22649
	.word	42855
	.word	16388
	.word	0
	.word	37607
	.word	64061
	.word	25856
	.word	50581
	.word	49154
	.word	0
	.word	47296
	.word	11647
	.word	64103
	.word	45853
	.word	16388
	.word	0
	.word	30544
	.word	55384
	.word	11467
	.word	54194
	.word	49154
	.word	0
	.word	39553
	.word	55407
	.word	30581
	.word	33698
	.word	49111
	.word	0
	.type	_C4,@object
	.size	_C4,276
	.space 12, 0x00 	# pad
	.align 16
_C3:
	.word	28854
	.word	1182
	.word	12345
	.word	55415
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	12848
	.word	12413
	.word	26149
	.word	42266
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	13313
	.word	43952
	.word	210
	.word	35312
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	13187
	.word	60452
	.word	5475
	.word	43161
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	20972
	.word	51120
	.word	32272
	.word	61954
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	22296
	.word	60123
	.word	44502
	.word	16231
	.word	21517
	.word	49855
	.word	35525
	.word	48979
	.word	44698
	.word	63620
	.word	45930
	.word	16192
	.word	34147
	.word	51190
	.word	16340
	.word	48941
	.word	42301
	.word	55313
	.word	4730
	.word	16154
	.word	35999
	.word	24973
	.word	36325
	.word	48903
	.word	63277
	.word	55760
	.word	33014
	.word	16117
	.word	6314
	.word	46281
	.word	52205
	.word	48867
	.word	12513
	.word	17037
	.word	21130
	.word	16082
	.word	49946
	.word	1999
	.word	4813
	.word	48833
	.word	595
	.word	34534
	.word	265
	.word	16049
	.word	36690
	.word	64093
	.word	7546
	.word	48800
	.type	_C3,@object
	.size	_C3,176
	.align 16
_LM:
	.word	64512
	.word	46577
	.word	49946
	.word	47894
	.word	16383
	.word	0
	.type	_LM,@object
	.size	_LM,12
	.space 4, 0x00 	# pad
	.align 16
_LGAMMA_C2:
	.word	3599
	.word	7250
	.word	52710
	.word	63693
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	31159
	.word	473
	.word	17060
	.word	35466
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	23705
	.word	29125
	.word	24135
	.word	63417
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	8259
	.word	13793
	.word	15470
	.word	38689
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	1592
	.word	43035
	.word	5286
	.word	33882
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	34241
	.word	35161
	.word	18170
	.word	34381
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	17215
	.word	38817
	.word	26343
	.word	16274
	.word	43407
	.word	38166
	.word	8095
	.word	49029
	.word	9256
	.word	10755
	.word	64736
	.word	16248
	.word	19293
	.word	8064
	.word	12058
	.word	49006
	.word	55284
	.word	28504
	.word	33554
	.word	16226
	.word	50892
	.word	40251
	.word	63876
	.word	48982
	.word	34903
	.word	32886
	.word	50950
	.word	16204
	.word	5270
	.word	55866
	.word	9779
	.word	48962
	.word	8284
	.word	46907
	.word	3681
	.word	16183
	.word	12968
	.word	35363
	.word	2442
	.word	48942
	.word	52637
	.word	49970
	.word	50463
	.word	16163
	.word	28134
	.word	34361
	.word	26628
	.word	48914
	.type	_LGAMMA_C2,@object
	.size	_LGAMMA_C2,192
	.align 16
_C1:
	.word	51111
	.word	32176
	.word	26595
	.word	37828
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	38980
	.word	38974
	.word	13074
	.word	53901
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30416
	.word	21910
	.word	43743
	.word	52518
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	47709
	.word	16075
	.word	37206
	.word	35465
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	15719
	.word	21694
	.word	57533
	.word	54364
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	13628
	.word	45309
	.word	46092
	.word	16325
	.word	18814
	.word	7557
	.word	28730
	.word	49090
	.word	30522
	.word	27866
	.word	4275
	.word	16320
	.word	35905
	.word	26356
	.word	32871
	.word	49084
	.word	38506
	.word	60969
	.word	40989
	.word	16313
	.word	18199
	.word	53198
	.word	18595
	.word	49079
	.word	21817
	.word	41319
	.word	22094
	.word	16309
	.word	3261
	.word	2506
	.word	46646
	.word	49075
	.word	41261
	.word	44393
	.word	26269
	.word	16306
	.word	45347
	.word	5923
	.word	3109
	.word	49073
	.word	3843
	.word	55608
	.word	31192
	.word	16299
	.word	33944
	.word	58380
	.word	1271
	.word	49050
	.type	_C1,@object
	.size	_C1,176
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xc3300000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x40080000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_W4:
	.long	381774871
	.long	3211182444
	.type	_W4,@object
	.size	_W4,8
	.align 4
_S26:
	.long	1826776023
	.long	1070820717
	.type	_S26,@object
	.size	_S26,8
	.align 4
_S28:
	.long	2468013457
	.long	3218873328
	.type	_S28,@object
	.size	_S28,8
	.align 4
_S22:
	.long	342869935
	.long	3214497781
	.type	_S22,@object
	.size	_S22,8
	.align 4
_S24:
	.long	3872164012
	.long	3218225849
	.type	_S24,@object
	.size	_S24,8
	.align 4
_S18:
	.long	1060196486
	.long	3216761200
	.type	_S18,@object
	.size	_S18,8
	.align 4
_S20:
	.long	1339685656
	.long	3216855407
	.type	_S20,@object
	.size	_S20,8
	.align 4
_S14:
	.long	3265047626
	.long	3217180894
	.type	_S14,@object
	.size	_S14,8
	.align 4
_S16:
	.long	618803264
	.long	3217033139
	.type	_S16,@object
	.size	_S16,8
	.align 4
_PBIG:
	.long	4294967295
	.long	2146435071
	.type	_PBIG,@object
	.size	_PBIG,8
	.data
	.hidden fesetround
	.hidden fegetround
	.section .note.GNU-stack, ""
# End
