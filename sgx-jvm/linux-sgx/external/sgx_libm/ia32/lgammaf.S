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
	.file "lgammaf.c"
	.text
..TXTST0:
# -- Begin  lgammaf
	.text
       .align    16,0x90
	.globl lgammaf
lgammaf:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        subl      $20, %esp
        lea       8(%esp), %eax
        pushl     %eax
        pushl     28(%esp)
        call      __libm_lgammaf
..B1.2:
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	lgammaf,@function
	.size	lgammaf,.-lgammaf
	.data
# -- End  lgammaf
	.text
# -- Begin  __libm_lgammaf
	.text
       .align    16,0x90
__libm_lgammaf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B2.1:
..L2:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        movzwl    10(%ebp), %ebx
        andl      $32640, %ebx
        shrl      $7, %ebx
        movl      %ebx, %edi
        movzbl    11(%ebp), %ecx
        movl      8(%ebp), %edx
        andl      $128, %ecx
        movl      12(%ebp), %eax
        andl      $8388607, %edx
        shll      $23, %edi
        shrl      $7, %ecx
        orl       %edx, %edi
        movl      %eax, 12(%esp)
        movl      %ecx, (%esp)
..B2.2:
        fnstcw    90(%esp)
..B2.3:
        call      fegetround
..B2.164:
        movl      %eax, 84(%esp)
..B2.4:
        cmpl      $0, 84(%esp)
        jne       ..B2.6
..B2.5:
        xorl      %edx, %edx
        jmp       ..B2.8
..B2.6:
        addl      $-16, %esp
        movl      $0, (%esp)
        call      fesetround
..B2.165:
        addl      $16, %esp
..B2.7:
        movl      $1, %edx
..B2.8:
        movzwl    90(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.161
..B2.9:
        orl       $-64768, %ecx
        movw      %cx, 88(%esp)
..B2.10:
        fldcw     88(%esp)
..B2.11:
        movl      $1, %esi
..B2.12:
        cmpl      $255, %ebx
        jne       ..B2.18
..B2.13:
        movl      12(%esp), %eax
        testl     %esi, %esi
        movl      $1, (%eax)
        je        ..B2.15
..B2.14:
        fldcw     90(%esp)
..B2.15:
        testl     %edx, %edx
        je        ..B2.17
..B2.16:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.166:
        addl      $16, %esp
..B2.17:
        flds      8(%ebp)
        fmul      %st(0), %st
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.18:
        testl     %edi, %edi
        je        ..B2.153
..B2.19:
        movl      12(%esp), %eax
        call      ..L3
..L3:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L3](%ebx), %ebx
        movl      $1, (%eax)
        cmpl      $0, (%esp)
        jne       ..B2.76
..B2.20:
        cmpl      $2084876174, %edi
        jae       ..B2.140
..B2.21:
        flds      8(%ebp)
        fld1      
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.22
        je        ..B2.135
..B2.22:
        cmpl      $1174405120, %edi
        jb        ..B2.28
..B2.23:
        fldt      _LN_SQRT_TWO_PI@GOTOFF(%ebx)
        fstpt     (%esp)
        addl      $-16, %esp
        fstl      (%esp)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        movl      %edx, 96(%esp)
        fsubrp    %st, %st(1)
        fstpt     48(%esp)
        call      log@PLT
..B2.167:
        fldt      32(%esp)
        fldt      48(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fmulp     %st, %st(2)
        testl     %edx, %edx
        fxch      %st(1)
        je        ..B2.25
..B2.24:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpt     48(%esp)
        fstpt     32(%esp)
        call      fesetround
..B2.168:
        fldt      32(%esp)
        fldt      48(%esp)
        addl      $16, %esp
..B2.25:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.27
..B2.26:
        fldcw     90(%esp)
..B2.27:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.28:
        cmpl      $1082130432, %edi
        jb        ..B2.35
..B2.29:
        addl      $-1082130432, %edi
        sarl      $23, %edi
        movl      %edi, %eax
        shll      $6, %eax
        testl     %edx, %edx
        fsubs     _A@GOTOFF(%ebx,%edi,4)
        fmuls     _B@GOTOFF(%ebx,%edi,4)
        lea       (%edi,%edi,2), %ecx
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8+_C5@GOTOFF(%eax,%ebx)
        fmul      %st(1), %st
        faddl     40+_C5@GOTOFF(%eax,%ebx)
        fmul      %st(1), %st
        faddl     8+_C5_0@GOTOFF(%ebx,%ecx,8)
        fmul      %st(1), %st
        faddl     56+_C5@GOTOFF(%eax,%ebx)
        fmul      %st(1), %st
        faddl     24+_C5@GOTOFF(%eax,%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldl      _C5@GOTOFF(%eax,%ebx)
        fmul      %st(2), %st
        faddl     32+_C5@GOTOFF(%eax,%ebx)
        fmul      %st(2), %st
        faddl     _C5_0@GOTOFF(%ebx,%ecx,8)
        fmul      %st(2), %st
        faddl     48+_C5@GOTOFF(%eax,%ebx)
        fmul      %st(2), %st
        faddl     16+_C5@GOTOFF(%eax,%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     16+_C5_0@GOTOFF(%ebx,%ecx,8)
        fstpt     (%esp)
        je        ..B2.32
..B2.30:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.169:
        addl      $16, %esp
..B2.31:
        fldt      36(%esp)
..B2.32:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.34
..B2.33:
        fldcw     90(%esp)
..B2.34:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.35:
        flds      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.36
        je        ..B2.130
..B2.36:
        cmpl      $1073741824, %edi
        jbe       ..B2.42
..B2.37:
        fldl      8+_C4@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testl     %edx, %edx
        fmul      %st, %st(1)
        fldl      _C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     40+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     32+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     72+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     64+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     56+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     48+_C4@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     24+_C4@GOTOFF(%ebx)
        fmul      %st(3), %st
        fstpt     36(%esp)
        fldt      36(%esp)
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddl     16+_C4@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        faddl     80+_C4@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        je        ..B2.39
..B2.38:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.170:
        fldt      16(%esp)
        addl      $16, %esp
..B2.39:
        fstps     76(%esp)
        testl     %esi, %esi
        je        ..B2.41
..B2.40:
        fldcw     90(%esp)
..B2.41:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.42:
        cmpl      $1071644672, %edi
        jb        ..B2.49
..B2.43:
        fldl      16+_C3@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testl     %edx, %edx
        fmul      %st, %st(1)
        fxch      %st(1)
        faddl     48+_C3@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     32+_C3@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     64+_C3@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldl      8+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     40+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     24+_C3@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     56+_C3@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     _C3@GOTOFF(%ebx)
        fstpt     (%esp)
        je        ..B2.46
..B2.44:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.171:
        addl      $16, %esp
..B2.45:
        fldt      36(%esp)
..B2.46:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.48
..B2.47:
        fldcw     90(%esp)
..B2.48:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.49:
        cmpl      $1069547520, %edi
        jb        ..B2.56
..B2.50:
        fldl      16+_LGAMMAF_C2@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testl     %edx, %edx
        fmul      %st, %st(1)
        fxch      %st(1)
        faddl     48+_LGAMMAF_C2@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     32+_LGAMMAF_C2@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     64+_LGAMMAF_C2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldl      8+_LGAMMAF_C2@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     40+_LGAMMAF_C2@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     24+_LGAMMAF_C2@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     56+_LGAMMAF_C2@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     _LGAMMAF_C2@GOTOFF(%ebx)
        fstpt     (%esp)
        je        ..B2.53
..B2.51:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.172:
        addl      $16, %esp
..B2.52:
        fldt      36(%esp)
..B2.53:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.55
..B2.54:
        fldcw     90(%esp)
..B2.55:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.56:
        cmpl      $1067450368, %edi
        jb        ..B2.63
..B2.57:
        fldl      16+_C1@GOTOFF(%ebx)
        testl     %edx, %edx
        fxch      %st(1)
        fsubl     _LM@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        faddl     48+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     32+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     64+_C1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldl      8+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     40+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     24+_C1@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     56+_C1@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     _C1@GOTOFF(%ebx)
        fstpt     (%esp)
        je        ..B2.60
..B2.58:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.173:
        addl      $16, %esp
..B2.59:
        fldt      36(%esp)
..B2.60:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.62
..B2.61:
        fldcw     90(%esp)
..B2.62:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.63:
        cmpl      $1065353216, %edi
        jb        ..B2.70
..B2.64:
        fldl      16+_C0@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        testl     %edx, %edx
        fmul      %st, %st(1)
        fxch      %st(1)
        faddl     48+_C0@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     32+_C0@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     64+_C0@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fldl      8+_C0@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     40+_C0@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     24+_C0@GOTOFF(%ebx)
        fmul      %st(2), %st
        faddl     56+_C0@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     _C0@GOTOFF(%ebx)
        fstpt     (%esp)
        je        ..B2.67
..B2.65:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.174:
        addl      $16, %esp
..B2.66:
        fldt      36(%esp)
..B2.67:
        fldt      (%esp)
        testl     %esi, %esi
        faddp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.69
..B2.68:
        fldcw     90(%esp)
..B2.69:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.70:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        addl      $-16, %esp
        fadd      %st(1), %st
        fstpt     (%esp)
        fstpt     32(%esp)
        movl      %edx, 96(%esp)
        call      lgammaf_pos
..B2.176:
        fldt      32(%esp)
        movl      96(%esp), %edx
        fxch      %st(1)
        fstpt     16(%esp)
        fstpl     (%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.175:
        movl      96(%esp), %edx
        addl      $16, %esp
        fstpt     36(%esp)
        fldt      36(%esp)
        testl     %edx, %edx
        je        ..B2.73
..B2.71:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.177:
        addl      $16, %esp
..B2.72:
        fldt      36(%esp)
..B2.73:
        fldt      (%esp)
        testl     %esi, %esi
        fsubp     %st, %st(1)
        fstps     76(%esp)
        je        ..B2.75
..B2.74:
        fldcw     90(%esp)
..B2.75:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.76:
        addl      $-16, %esp
        movl      8(%ebp), %eax
        movl      %eax, (%esp)
        movl      %edx, 96(%esp)
        call      nearbyintf@PLT
..B2.178:
        flds      8(%ebp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fxch      %st(1)
        fstps     76(%esp)
        flds      76(%esp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.77
        je        ..B2.148
..B2.77:
        fcoms     .L_2il0floatpacket.0@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jbe       ..B2.80
..B2.78:
        fld       %st(0)
        frndint   
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jnb       ..L4
        fld1      
        faddp     %st, %st(1)
..L4:
        fistpl    16(%esp)
        movl      16(%esp), %eax
        testb     $1, %al
        jne       ..B2.80
..B2.79:
        movl      12(%esp), %eax
        movl      $-1, (%eax)
..B2.80:
        cmpl      $1073741824, %edi
        jae       ..B2.91
..B2.81:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.145
..B2.82:
        fld       %st(0)
..B2.83:
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st, %st(2)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B2.83
..B2.84:
        fstp      %st(1)
        fstpt     36(%esp)
..B2.85:
        andb      $127, 45(%esp)
        fldt      36(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpt     80(%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.180:
        fldt      80(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        movl      %edx, 96(%esp)
        call      lgammaf_pos
..B2.179:
        fldt      16(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        testl     %edx, %edx
        je        ..B2.88
..B2.86:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.181:
        fldt      16(%esp)
        addl      $16, %esp
..B2.87:
        fldt      36(%esp)
..B2.88:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.90
..B2.89:
        fldcw     90(%esp)
..B2.90:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.91:
        cmpl      $1082130432, %edi
        jae       ..B2.108
..B2.92:
        flds      .L_2il0floatpacket.4@GOTOFF(%ebx)
        fsub      %st(1), %st
        fstps     76(%esp)
        flds      76(%esp)
        flds      .L_2il0floatpacket.5@GOTOFF(%ebx)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstps     76(%esp)
        flds      76(%esp)
        movl      76(%esp), %edi
        fsubp     %st, %st(1)
        andl      $1, %edi
        fstps     76(%esp)
        lea       (%edi,%edi,2), %ecx
        fcoml     _LRIB@GOTOFF(%ebx,%ecx,8)
        fnstsw    %ax
        sahf      
        jbe       ..B2.95
..B2.93:
        fldl      16+_LRIB@GOTOFF(%ebx,%ecx,8)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.95
..B2.94:
        fldl      8+_LRIB@GOTOFF(%ebx,%ecx,8)
        shll      $5, %edi
        fsubrp    %st, %st(1)
        fldl      _LRP@GOTOFF(%edi,%ebx)
        fmul      %st(1), %st
        faddl     8+_LRP@GOTOFF(%edi,%ebx)
        fmul      %st(1), %st
        faddl     16+_LRP@GOTOFF(%edi,%ebx)
        fmulp     %st, %st(1)
        faddl     24+_LRP@GOTOFF(%edi,%ebx)
        jmp       ..B2.103
..B2.95:
        fcoml     _RRIB@GOTOFF(%ebx,%ecx,8)
        fnstsw    %ax
        sahf      
        jbe       ..B2.98
..B2.96:
        fldl      16+_RRIB@GOTOFF(%ebx,%ecx,8)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.98
..B2.97:
        fldl      8+_RRIB@GOTOFF(%ebx,%ecx,8)
        shll      $5, %edi
        fsubrp    %st, %st(1)
        fldl      _RRP@GOTOFF(%edi,%ebx)
        fmul      %st(1), %st
        faddl     8+_RRP@GOTOFF(%edi,%ebx)
        fmul      %st(1), %st
        faddl     16+_RRP@GOTOFF(%edi,%ebx)
        fmulp     %st, %st(1)
        faddl     24+_RRP@GOTOFF(%edi,%ebx)
        jmp       ..B2.103
..B2.98:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.146
..B2.99:
        fld       %st(0)
..B2.100:
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st, %st(2)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B2.100
..B2.101:
        fstp      %st(1)
        fstpt     36(%esp)
..B2.102:
        andb      $127, 45(%esp)
        fldt      36(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpt     80(%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.183:
        fldt      80(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fxch      %st(1)
        fstl      (%esp)
        fstpt     36(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %edx, 96(%esp)
        call      lgammaf_pos
..B2.182:
        movl      96(%esp), %edx
        addl      $16, %esp
        fsubl     (%esp)
..B2.103:
        testl     %edx, %edx
        je        ..B2.105
..B2.104:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.184:
        fldt      16(%esp)
        addl      $16, %esp
..B2.105:
        fstps     76(%esp)
        testl     %esi, %esi
        je        ..B2.107
..B2.106:
        fldcw     90(%esp)
..B2.107:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.108:
        cmpl      $1091567616, %edi
        jae       ..B2.119
..B2.109:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.147
..B2.110:
        fld       %st(0)
..B2.111:
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st, %st(2)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B2.111
..B2.112:
        fstp      %st(1)
        fstpt     36(%esp)
..B2.113:
        andb      $127, 45(%esp)
        fldt      36(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpt     80(%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.186:
        fldt      80(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     16(%esp)
        movl      %edx, 96(%esp)
        call      lgammaf_pos
..B2.185:
        fldt      16(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        testl     %edx, %edx
        je        ..B2.116
..B2.114:
        fstp      %st(0)
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpt     16(%esp)
        call      fesetround
..B2.187:
        fldt      16(%esp)
        addl      $16, %esp
..B2.115:
        fldt      36(%esp)
..B2.116:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.118
..B2.117:
        fldcw     90(%esp)
..B2.118:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.119:
        flds      .L_2il0floatpacket.5@GOTOFF(%ebx)
        cmpl      $1174405120, %edi
        jb        ..B2.125
..B2.120:
        fldt      _LN_SQRT_TWO_PI@GOTOFF(%ebx)
        fstpt     24(%esp)
        addl      $-16, %esp
        fld       %st(1)
        fchs      
        fstpl     (%esp)
        fstps     16(%esp)
        fstpt     80(%esp)
        fldt      80(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        movl      %edx, 96(%esp)
        fsubrp    %st, %st(1)
        fstpt     24(%esp)
        call      log@PLT
..B2.189:
        fldt      80(%esp)
        flds      16(%esp)
        fldt      24(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        fmulp     %st, %st(3)
        fsts      76(%esp)
        fldt      24(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fchs      
        flds      76(%esp)
        fldl      _S16@GOTOFF(%ebx)
        fxch      %st(1)
        fsubs     8(%ebp)
        fstps     76(%esp)
        flds      76(%esp)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstps     76(%esp)
        flds      76(%esp)
        fsubrp    %st, %st(1)
        fstpt     52(%esp)
        fldt      52(%esp)
        fmul      %st(0), %st
        fmul      %st, %st(1)
        andb      $127, 61(%esp)
        fxch      %st(1)
        faddl     _S14@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S12@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S10@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S08@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S06@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S04@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddl     _S02@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fsubrp    %st, %st(1)
        fldt      52(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpt     24(%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.188:
        fldt      24(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        testl     %edx, %edx
        fxch      %st(1)
        je        ..B2.122
..B2.121:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpl     16(%esp)
        fstpt     24(%esp)
        call      fesetround
..B2.190:
        fldt      24(%esp)
        fldl      16(%esp)
        addl      $16, %esp
..B2.122:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.124
..B2.123:
        fldcw     90(%esp)
..B2.124:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.125:
        fld       %st(0)
        fsub      %st(2), %st
        fstps     76(%esp)
        flds      76(%esp)
        fsubp     %st, %st(1)
        fstps     76(%esp)
        fld       %st(0)
        fchs      
        fstpt     40(%esp)
        fldt      40(%esp)
        fxch      %st(1)
        fchs      
        flds      76(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     52(%esp)
        fldt      52(%esp)
        fmul      %st(0), %st
        fstpt     24(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.193:
        movl      96(%esp), %edx
        fchs      
        fldt      56(%esp)
        fstpt     (%esp)
        fstpt     24(%esp)
        movl      %edx, 96(%esp)
        call      lgammaf_pos
..B2.192:
        fldt      24(%esp)
        movl      96(%esp), %edx
        fsubp     %st, %st(1)
        addl      $16, %esp
        fldt      24(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      _S14@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      _S16@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _S10@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _S12@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _S06@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _S08@GOTOFF(%ebx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _S02@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        faddl     _S04@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        andb      $127, 61(%esp)
        faddp     %st, %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fsubrp    %st, %st(1)
        fldt      52(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpt     24(%esp)
        movl      %edx, 96(%esp)
        call      log@PLT
..B2.191:
        fldt      24(%esp)
        movl      96(%esp), %edx
        addl      $16, %esp
        testl     %edx, %edx
        fxch      %st(1)
        je        ..B2.127
..B2.126:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        fstpl     16(%esp)
        fstpt     24(%esp)
        call      fesetround
..B2.194:
        fldt      24(%esp)
        fldl      16(%esp)
        addl      $16, %esp
..B2.127:
        testl     %esi, %esi
        fsubrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.129
..B2.128:
        fldcw     90(%esp)
..B2.129:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.130:
        fstp      %st(0)
        testl     %esi, %esi
        je        ..B2.132
..B2.131:
        fldcw     90(%esp)
..B2.132:
        testl     %edx, %edx
        je        ..B2.134
..B2.133:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.195:
        addl      $16, %esp
..B2.134:
        fldz      
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.135:
        fstp      %st(0)
        testl     %esi, %esi
        je        ..B2.137
..B2.136:
        fldcw     90(%esp)
..B2.137:
        testl     %edx, %edx
        je        ..B2.139
..B2.138:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.196:
        addl      $16, %esp
..B2.139:
        fldz      
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.140:
        testl     %edx, %edx
        je        ..B2.142
..B2.141:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.197:
        addl      $16, %esp
..B2.142:
        fldl      _PBIG@GOTOFF(%ebx)
        testl     %esi, %esi
        fmul      %st(0), %st
        fstps     76(%esp)
        je        ..B2.144
..B2.143:
        fldcw     90(%esp)
..B2.144:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.145:
        fstpt     36(%esp)
        jmp       ..B2.85
..B2.146:
        fstpt     36(%esp)
        jmp       ..B2.102
..B2.147:
        fstpt     36(%esp)
        jmp       ..B2.113
..B2.148:
        fstp      %st(0)
        testl     %edx, %edx
        je        ..B2.150
..B2.149:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.198:
        addl      $16, %esp
..B2.150:
        fld1      
        movl      $0, 76(%esp)
        testl     %esi, %esi
        flds      76(%esp)
        fdivrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.152
..B2.151:
        fldcw     90(%esp)
..B2.152:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.153:
        cmpl      $0, (%esp)
        je        ..B2.155
..B2.154:
        movl      12(%esp), %eax
        movl      $-1, (%eax)
        jmp       ..B2.156
..B2.155:
        movl      12(%esp), %eax
        movl      $1, (%eax)
..B2.156:
        testl     %edx, %edx
        je        ..B2.158
..B2.157:
        addl      $-16, %esp
        movl      100(%esp), %eax
        movl      %eax, (%esp)
        call      fesetround
..B2.199:
        addl      $16, %esp
..B2.158:
        fld1      
        movl      $0, 76(%esp)
        testl     %esi, %esi
        flds      76(%esp)
        fdivrp    %st, %st(1)
        fstps     76(%esp)
        je        ..B2.160
..B2.159:
        fldcw     90(%esp)
..B2.160:
        flds      76(%esp)
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.161:
        xorl      %esi, %esi
        jmp       ..B2.12
        .align    16,0x90
	.type	__libm_lgammaf,@function
	.size	__libm_lgammaf,.-__libm_lgammaf
	.data
# -- End  __libm_lgammaf
	.text
# -- Begin  lgammaf_pos
	.text
       .align    16,0x90
lgammaf_pos:
# parameter 1: 8 + %ebp
..B3.1:
..L5:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        fldt      8(%ebp)
        fstl      (%esp)
        movzwl    6(%esp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        movl      4(%esp), %eax
        shll      $20, %edx
        andl      $1048575, %eax
        orl       %eax, %edx
        call      ..L6
..L6:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L6](%ecx), %ecx
        cmpl      $1074790400, %edx
        jb        ..B3.3
..B3.2:
        addl      $-1074790400, %edx
        sarl      $20, %edx
        movl      %edx, %eax
        shll      $6, %eax
        flds      _A@GOTOFF(%ecx,%edx,4)
        fsubrp    %st, %st(1)
        fmuls     _B@GOTOFF(%ecx,%edx,4)
        lea       (%edx,%edx,2), %edx
        fld       %st(0)
        fmul      %st(1), %st
        fldl      _C5@GOTOFF(%eax,%ecx)
        fmul      %st(1), %st
        fldl      8+_C5@GOTOFF(%ecx,%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_C5@GOTOFF(%ecx,%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_C5@GOTOFF(%ecx,%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     _C5_0@GOTOFF(%ecx,%edx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_C5_0@GOTOFF(%ecx,%edx,8)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48+_C5@GOTOFF(%ecx,%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56+_C5@GOTOFF(%ecx,%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_C5@GOTOFF(%ecx,%eax)
        fmulp     %st, %st(2)
        faddl     24+_C5@GOTOFF(%ecx,%eax)
        fmulp     %st, %st(2)
        faddl     16+_C5_0@GOTOFF(%ecx,%edx,8)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.3:
        cmpl      $1073741824, %edx
        jne       ..B3.5
..B3.4:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.7@GOTOFF(%ecx)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.5:
        jbe       ..B3.7
..B3.6:
        fldl      _C4@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fldl      8+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     32+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     40+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     64+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     72+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     48+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     56+_C4@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     16+_C4@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     24+_C4@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     80+_C4@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.6@GOTOFF(%ecx)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.7:
        cmpl      $1073479680, %edx
        jb        ..B3.9
..B3.8:
        fldl      8+_C3@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fldl      16+_C3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     40+_C3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     48+_C3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     24+_C3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     32+_C3@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     56+_C3@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_C3@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     _C3@GOTOFF(%ecx)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.9:
        cmpl      $1073217536, %edx
        jb        ..B3.11
..B3.10:
        fldl      8+_LGAMMAF_C2@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fldl      16+_LGAMMAF_C2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     40+_LGAMMAF_C2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     48+_LGAMMAF_C2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     24+_LGAMMAF_C2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     32+_LGAMMAF_C2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     56+_LGAMMAF_C2@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_LGAMMAF_C2@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     _LGAMMAF_C2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.11:
        cmpl      $1072955392, %edx
        jb        ..B3.13
..B3.12:
        fldl      _LM@GOTOFF(%ecx)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8+_C1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldl      16+_C1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_C1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     48+_C1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_C1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_C1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     56+_C1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     64+_C1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     _C1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.13:
        cmpl      $1072693248, %edx
        jb        ..B3.15
..B3.14:
        fldl      8+_C0@GOTOFF(%ecx)
        fld       %st(1)
        fmul      %st(2), %st
        fmul      %st, %st(1)
        fldl      16+_C0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     40+_C0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     48+_C0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     24+_C0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     32+_C0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fxch      %st(2)
        faddl     56+_C0@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     64+_C0@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     _C0@GOTOFF(%ecx)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.15:
        fstp      %st(0)
        fldz      
..B3.16:
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	lgammaf_pos,@function
	.size	lgammaf_pos,.-lgammaf_pos
	.data
# -- End  lgammaf_pos
	.text
# -- Begin  gammaf
	.text
       .align    16,0x90
	.globl gammaf
gammaf:
# parameter 1: 32 + %esp
..B4.1:
..L7:

        subl      $20, %esp
        lea       8(%esp), %eax
        pushl     %eax
        pushl     28(%esp)
        call      __libm_lgammaf
..B4.2:
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	gammaf,@function
	.size	gammaf,.-gammaf
	.data
# -- End  gammaf
	.text
# -- Begin  lgammaf_r
	.text
       .align    16,0x90
	.globl lgammaf_r
lgammaf_r:
# parameter 1: 4 + %esp
# parameter 2: 8 + %esp
..B5.1:
..L8:

        jmp       __libm_lgammaf
        .align    16,0x90
	.type	lgammaf_r,@function
	.size	lgammaf_r,.-lgammaf_r
	.data
# -- End  lgammaf_r
	.text
# -- Begin  gammaf_r
	.text
       .align    16,0x90
	.globl gammaf_r
gammaf_r:
# parameter 1: 4 + %esp
# parameter 2: 8 + %esp
..B6.1:
..L9:

        jmp       __libm_lgammaf
        .align    16,0x90
	.type	gammaf_r,@function
	.size	gammaf_r,.-gammaf_r
	.data
# -- End  gammaf_r
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,12
	.space 4, 0x00 	# pad
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
_A:
	.long	1082130432
	.long	1090519040
	.long	1098907648
	.long	1107296256
	.long	1115684864
	.long	1124073472
	.long	1132462080
	.long	1140850688
	.long	1149239296
	.long	1157627904
	.long	1166016512
	.type	_A,@object
	.size	_A,44
	.space 4, 0x00 	# pad
	.align 16
_B:
	.long	1048576000
	.long	1040187392
	.long	1031798784
	.long	1023410176
	.long	1015021568
	.long	1006632960
	.long	998244352
	.long	989855744
	.long	981467136
	.long	973078528
	.long	964689920
	.type	_B,@object
	.size	_B,44
	.space 4, 0x00 	# pad
	.align 16
_C5:
	.long	2219950285
	.long	1064025046
	.long	657069815
	.long	3214307011
	.long	2744833897
	.long	1073883684
	.long	3746154693
	.long	1075058958
	.long	24044097
	.long	1068577021
	.long	1852002888
	.long	3217117620
	.long	426096126
	.long	1071550305
	.long	3945160253
	.long	3219869968
	.long	889741930
	.long	1064529657
	.long	2918550257
	.long	3214927323
	.long	3306758232
	.long	1074858655
	.long	2801363753
	.long	1076895752
	.long	216130741
	.long	1069082446
	.long	2022127190
	.long	3217667546
	.long	4043317980
	.long	1072274701
	.long	948933764
	.long	3220711872
	.long	1837011561
	.long	1065353117
	.long	614133169
	.long	3215620106
	.long	128884864
	.long	1075872424
	.long	1749057555
	.long	1078324632
	.long	1278187959
	.long	1069867493
	.long	1878987920
	.long	3218464301
	.long	2508500615
	.long	1073176520
	.long	3716705454
	.long	3221664954
	.long	1971591775
	.long	1066198339
	.long	3489274850
	.long	3216507307
	.long	2978044890
	.long	1076904104
	.long	399657850
	.long	1079744937
	.long	2176842243
	.long	1070795947
	.long	1642919051
	.long	3219397359
	.long	325839939
	.long	1074155724
	.long	1189620011
	.long	3222667808
	.long	583706808
	.long	1067149757
	.long	1974968851
	.long	3217478792
	.long	3433705325
	.long	1077944360
	.long	3946626575
	.long	1081121452
	.long	1495561758
	.long	1071787114
	.long	4062466621
	.long	3220390645
	.long	1556755231
	.long	1075170624
	.long	75679593
	.long	3223694033
	.long	3504606470
	.long	1068150172
	.long	1348305099
	.long	3218489288
	.long	3580634696
	.long	1078988808
	.long	1259291510
	.long	1082352761
	.long	652214916
	.long	1072807425
	.long	4143445962
	.long	3221412067
	.long	2123749472
	.long	1076202613
	.long	4248403985
	.long	3224731560
	.long	1507106795
	.long	1069174188
	.long	3519298167
	.long	3219518582
	.long	3633366982
	.long	1080035328
	.long	2867629808
	.long	1083583554
	.long	3975256969
	.long	1073841792
	.long	2533063588
	.long	3222447083
	.long	3711656185
	.long	1077242956
	.long	3206083648
	.long	3225774644
	.long	4129446490
	.long	1070209876
	.long	3121668168
	.long	3220557159
	.long	3645659330
	.long	1081082878
	.long	1254870306
	.long	1084814091
	.long	1589013900
	.long	1074883091
	.long	485315198
	.long	3223488795
	.long	402512060
	.long	1078287430
	.long	1922980292
	.long	3226820482
	.long	157052617
	.long	1071251449
	.long	2992469705
	.long	3221600396
	.long	1492808525
	.long	1082130942
	.long	3131814131
	.long	1086044499
	.long	2683002247
	.long	1075927855
	.long	2649378904
	.long	3224533843
	.long	3018380877
	.long	1079333956
	.long	653917322
	.long	3227867691
	.long	2713966257
	.long	1072296037
	.long	4209563533
	.long	3222646006
	.long	943575735
	.long	1083179262
	.long	512478091
	.long	1087274844
	.long	3856346814
	.long	1076974372
	.long	1324813592
	.long	3225580569
	.long	1409229319
	.long	1080381507
	.long	3787450294
	.long	3228915583
	.long	3333539597
	.long	1073342203
	.long	2943809629
	.long	3223692845
	.long	794946536
	.long	1084227710
	.long	1068889795
	.long	1088463522
	.long	3231716378
	.long	1078021787
	.long	4072054083
	.long	3226628144
	.long	1998042203
	.long	1081429569
	.long	1148344549
	.long	3229963818
	.type	_C5,@object
	.size	_C5,704
	.align 16
_C5_0:
	.long	2415979356
	.long	1070304454
	.long	2697119297
	.long	3218348439
	.long	4195263586
	.long	1073523467
	.long	3725211212
	.long	1070931684
	.long	2560716363
	.long	3219131775
	.long	4079479268
	.long	1075907809
	.long	1732432466
	.long	1071796064
	.long	3508892908
	.long	3219947991
	.long	2788858919
	.long	1077667382
	.long	2647999156
	.long	1072759450
	.long	547175205
	.long	3220886396
	.long	4254853098
	.long	1079215590
	.long	2099688739
	.long	1073767129
	.long	1386213411
	.long	3221881825
	.long	1373988558
	.long	1080631372
	.long	3012547841
	.long	1074795638
	.long	3012018428
	.long	3222904295
	.long	3968063321
	.long	1082046682
	.long	2372211290
	.long	1075829504
	.long	1852200321
	.long	3223939920
	.long	822603622
	.long	1083320025
	.long	2940107565
	.long	1076868074
	.long	1118670349
	.long	3224982033
	.long	4034033966
	.long	1084551076
	.long	3328910691
	.long	1077911588
	.long	4065388177
	.long	3226027369
	.long	3374537656
	.long	1085781831
	.long	3604631852
	.long	1078957575
	.long	786281660
	.long	3227074315
	.long	3320933858
	.long	1087012393
	.long	4128496013
	.long	1080004805
	.long	820316847
	.long	3228122065
	.long	596318476
	.long	1088242837
	.type	_C5_0,@object
	.size	_C5_0,264
	.space 8, 0x00 	# pad
	.align 16
_C4:
	.long	951661919
	.long	3199388794
	.long	406044492
	.long	1057185581
	.long	2144499589
	.long	3220511658
	.long	1533861631
	.long	1073519965
	.long	4190848711
	.long	3208779382
	.long	4195628742
	.long	1064581106
	.long	501584638
	.long	3218581252
	.long	4276665641
	.long	1072296199
	.long	2679252139
	.long	3214810152
	.long	1146917984
	.long	1069511714
	.long	3597634954
	.long	3220184694
	.type	_C4,@object
	.size	_C4,88
	.space 8, 0x00 	# pad
	.align 16
_C3:
	.long	953601802
	.long	1073782987
	.long	53392282
	.long	1061851682
	.long	2280564131
	.long	3213671573
	.long	126074441
	.long	1073190208
	.long	1099663909
	.long	3221770684
	.long	2017365083
	.long	1069271442
	.long	1709742889
	.long	3219105406
	.long	4029656968
	.long	1074967560
	.long	3888843833
	.long	3222480972
	.type	_C3,@object
	.size	_C3,72
	.space 8, 0x00 	# pad
	.align 16
_LGAMMAF_C2:
	.long	4192182476
	.long	1073863432
	.long	3945858175
	.long	1063638439
	.long	2616885214
	.long	3215216300
	.long	3886441432
	.long	1074077679
	.long	3758761250
	.long	3222447567
	.long	984616456
	.long	1070653784
	.long	2508733349
	.long	3220210863
	.long	2272977058
	.long	1075337602
	.long	3074119607
	.long	3222665277
	.type	_LGAMMAF_C2,@object
	.size	_LGAMMAF_C2,72
	.space 8, 0x00 	# pad
	.align 16
_C1:
	.long	3166931522
	.long	3216972217
	.long	900077338
	.long	1065586189
	.long	3591574455
	.long	3213040571
	.long	425993680
	.long	1068534592
	.long	2772351024
	.long	3217220647
	.long	3693139651
	.long	1066567133
	.long	2619486276
	.long	3214985484
	.long	3371509884
	.long	1071576875
	.long	2931564028
	.long	1032391485
	.type	_C1,@object
	.size	_C1,72
	.space 8, 0x00 	# pad
	.align 16
_LM:
	.long	1666629183
	.long	1073177304
	.type	_LM,@object
	.size	_LM,8
	.space 8, 0x00 	# pad
	.align 16
_C0:
	.long	561403216
	.long	1074066324
	.long	3155481408
	.long	1068272863
	.long	4228834524
	.long	3219267730
	.long	2107372888
	.long	1076349696
	.long	3392182390
	.long	3224133636
	.long	3282054928
	.long	1074040136
	.long	4253763640
	.long	3223020498
	.long	1528000942
	.long	1076400076
	.long	4059292707
	.long	3223275314
	.type	_C0,@object
	.size	_C0,72
	.space 8, 0x00 	# pad
	.align 16
_LRIB:
	.long	2838661661
	.long	3221617475
	.long	169597185
	.long	3221617473
	.long	1795500005
	.long	3221617470
	.long	3503748181
	.long	3222250619
	.long	1417424869
	.long	3222250609
	.long	2147483648
	.long	3222250589
	.type	_LRIB,@object
	.size	_LRIB,48
	.align 16
_LRP:
	.long	3272807679
	.long	3224639578
	.long	1009311715
	.long	1076045439
	.long	2835657108
	.long	3221135661
	.long	817526409
	.long	1017707397
	.long	2658191496
	.long	3232572725
	.long	2929834182
	.long	1081046750
	.long	2523682372
	.long	3224680861
	.long	915934040
	.long	3166559235
	.type	_LRP,@object
	.size	_LRP,64
	.align 16
_RRIB:
	.long	890743176
	.long	3221465087
	.long	2516645996
	.long	3221465084
	.long	4142548816
	.long	3221465081
	.long	1538591435
	.long	3221825040
	.long	3164494255
	.long	3221825037
	.long	495429779
	.long	3221825035
	.type	_RRIB,@object
	.size	_RRIB,48
	.align 16
_RRP:
	.long	93164946
	.long	1073124518
	.long	2953029931
	.long	1075015403
	.long	1722766176
	.long	1073233897
	.long	1830807661
	.long	1016083003
	.long	308861496
	.long	1079775543
	.long	2541562915
	.long	1077531858
	.long	1596959842
	.long	1075781798
	.long	3819862626
	.long	1021220357
	.type	_RRP,@object
	.size	_RRP,64
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,12
	.align 4
.L_2il0floatpacket.0:
	.long	0xcb400000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x40000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0xc0200000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
.L_2il0floatpacket.5:
	.long	0x4b400000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,4
	.align 4
_S16:
	.long	2760304672
	.long	3218442033
	.type	_S16,@object
	.size	_S16,8
	.align 4
_S14:
	.long	3264091561
	.long	3215213868
	.type	_S14,@object
	.size	_S14,8
	.align 4
_S12:
	.long	1381164920
	.long	3217582605
	.type	_S12,@object
	.size	_S12,8
	.align 4
_S10:
	.long	3387828299
	.long	3217636872
	.type	_S10,@object
	.size	_S10,8
	.align 4
_S08:
	.long	3786959951
	.long	3218084854
	.type	_S08,@object
	.size	_S08,8
	.align 4
_S06:
	.long	2675586079
	.long	3218453489
	.type	_S06,@object
	.size	_S06,8
	.align 4
_S04:
	.long	1432133341
	.long	3219214642
	.type	_S04,@object
	.size	_S04,8
	.align 4
_S02:
	.long	1648889397
	.long	3220853158
	.type	_S02,@object
	.size	_S02,8
	.align 4
_PBIG:
	.long	2139095039
	.long	2139095039
	.type	_PBIG,@object
	.size	_PBIG,8
	.data
	.hidden fesetround
	.hidden fegetround
	.section .note.GNU-stack, ""
# End
