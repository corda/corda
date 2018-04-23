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
	.file "clog10l.c"
	.text
..TXTST0:
# -- Begin  clog10l
	.text
       .align    16,0x90
	.globl clog10l
clog10l:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %edi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        movl      %edx, 224(%esp)
        fstpt     80(%esp)
        fldt      80(%esp)
        fstpt     200(%esp)
        fldt      200(%esp)
        fstpt     212(%esp)
..B1.2:
        fnstcw    46(%esp)
..B1.3:
        movzwl    46(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.77
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 44(%esp)
..B1.5:
        fldcw     44(%esp)
..B1.6:
        movl      $1, %esi
..B1.7:
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %ecx
        jge       ..B1.46
..B1.8:
        cmpl      $32767, %edx
        jge       ..B1.85
..B1.9:
        testl     %ecx, %ecx
        jne       ..B1.15
..B1.10:
        cmpl      $0, 16(%ebp)
        jne       ..B1.15
..B1.11:
        cmpl      $0, 12(%ebp)
        jne       ..B1.15
..B1.12:
        testl     %edx, %edx
        jne       ..B1.15
..B1.13:
        cmpl      $0, 28(%ebp)
        jne       ..B1.15
..B1.14:
        cmpl      $0, 24(%ebp)
        je        ..B1.45
..B1.15:
        fldt      12(%ebp)
        fld       %st(0)
        fabs      
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      24(%ebp)
        fld       %st(0)
        fabs      
        fstpt     (%esp)
        fldt      (%esp)
        movzwl    24(%esp), %ecx
        movzwl    8(%esp), %edx
        andl      $32767, %ecx
        andl      $32767, %edx
        cmpl      %edx, %ecx
        jl        ..B1.20
..B1.16:
        jne       ..B1.21
..B1.17:
        movl      20(%esp), %edx
        movl      4(%esp), %eax
        cmpl      %eax, %edx
        jb        ..B1.20
..B1.18:
        jne       ..B1.21
..B1.19:
        movl      16(%esp), %edx
        cmpl      (%esp), %edx
        jae       ..B1.21
..B1.20:
        fstpt     16(%esp)
        fldt      16(%esp)
        movzwl    24(%esp), %ecx
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        andl      $32767, %ecx
..B1.21:
        cmpl      $16382, %ecx
        jg        ..B1.26
..B1.22:
        jne       ..B1.42
..B1.23:
        movl      20(%esp), %edx
        cmpl      16+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.26
..B1.24:
        jne       ..B1.42
..B1.25:
        movl      12+_CONSTANTS@GOTOFF(%ebx), %edx
        cmpl      16(%esp), %edx
        ja        ..B1.42
..B1.26:
        cmpl      $16383, %ecx
        jl        ..B1.31
..B1.27:
        jne       ..B1.42
..B1.28:
        movl      20(%esp), %edx
        cmpl      28+_CONSTANTS@GOTOFF(%ebx), %edx
        jb        ..B1.31
..B1.29:
        jne       ..B1.42
..B1.30:
        movl      16(%esp), %edx
        cmpl      24+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.42
..B1.31:
        movzwl    8(%esp), %edx
        andl      $32767, %edx
        cmpl      $8256, %edx
        jg        ..B1.36
..B1.32:
        jne       ..B1.37
..B1.33:
        movl      4(%esp), %eax
        cmpl      40+_CONSTANTS@GOTOFF(%ebx), %eax
        ja        ..B1.36
..B1.34:
        jne       ..B1.37
..B1.35:
        movl      (%esp), %eax
        cmpl      36+_CONSTANTS@GOTOFF(%ebx), %eax
        jbe       ..B1.37
..B1.36:
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fabs      
        fcomp     %st(2)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jae       ..B1.86
        jp        ..B1.86
        jmp       ..B1.38
..B1.37:
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(4), %st
        fsub      %st(1), %st
        fabs      
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.86
..B1.38:
        fstp      %st(2)
        cmpl      $16183, %edx
        jl        ..B1.40
..B1.39:
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(1), %st
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fxch      %st(5)
        fstpt     (%esp)
        fld       %st(3)
        fsubr     %st(5), %st
        fsubrp    %st, %st(5)
        fld       %st(4)
        lea       72(%eax), %edx
        fmul      %st(5), %st
        fld       %st(2)
        fmulp     %st, %st(2)
        fxch      %st(4)
        fsub      %st(5), %st
        fld       %st(2)
        addl      $84, %eax
        movl      %edx, 12(%esp)
        fsubr     %st(2), %st
        fsubrp    %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(4)
        fmul      %st(0), %st
        fstpt     48(%esp)
        fld       %st(5)
        fadd      %st(3), %st
        fsub      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(3)
        fmulp     %st, %st(6)
        fmul      %st, %st(5)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fld       %st(0)
        fsub      %st(5), %st
        fsubr     %st, %st(2)
        fld       %st(1)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(5), %st
        fld       %st(0)
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(4)
        fmul      %st(0), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      48(%esp)
        faddp     %st, %st(1)
        fldt      12+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(1)
        fld       %st(4)
        fsub      %st(6), %st
        fsubr     %st, %st(7)
        fsubr     %st(5), %st
        fsubrp    %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fld       %st(1)
        fsub      %st(3), %st
        fldt      32(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(3)
        fldt      64(%esp)
        fld       %st(0)
        fsub      %st(3), %st
        fldt      16(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(4)
        fsub      %st(2), %st
        fldt      48(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(6), %st
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%edx)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      (%eax)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     200(%esp)
        fldt      (%esp)
        fxch      %st(1)
        jmp       ..B1.49
..B1.40:
        cmpl      $16383, %ecx
        je        ..B1.55
..B1.41:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fld       %st(2)
        fmul      %st(1), %st
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fmul      %st(1), %st
        lea       72(%eax), %edx
        addl      $84, %eax
        fsubp     %st, %st(3)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fld       %st(3)
        fxch      %st(5)
        fmul      %st(0), %st
        movl      %edx, 12(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fadd      %st, %st(5)
        fxch      %st(4)
        fsubr     %st(5), %st
        fxch      %st(2)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      12+_Q1@GOTOFF(%ebx)
        fmul      %st(4), %st
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%edx)
        fld       %st(0)
        fmul      %st(3), %st
        fldt      (%eax)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     200(%esp)
        jmp       ..B1.49
..B1.42:
        fstp      %st(0)
        fstp      %st(1)
        addl      $-32, %esp
        lea       80(%esp), %edx
        movl      %edx, (%esp)
        fxch      %st(1)
        fstpt     4(%esp)
        fstpt     16(%esp)
        call      __libm_hypot2l_k80
..B1.80:
        addl      $32, %esp
..B1.43:
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fstpt     32(%esp)
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     16(%esp)
        fldt      132+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        addl      $-32, %esp
        fldt      80(%esp)
        lea       124(%esp), %edx
        fstpt     (%esp)
        fldt      -32(%edx)
        fstpt     12(%esp)
        movl      %edx, 24(%esp)
        call      __libm_logl_k80
..B1.81:
        addl      $32, %esp
..B1.44:
        fldt      32(%esp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fmulp     %st, %st(1)
        lea       72(%eax), %edx
        fldt      92(%esp)
        addl      $84, %eax
        movl      %edx, 12(%esp)
        fmul      %st(1), %st
        fldt      104(%esp)
        fmulp     %st, %st(2)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      (%edx)
        fld       %st(0)
        fmul      %st(4), %st
        fldt      (%eax)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      16(%esp)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      (%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     200(%esp)
        fldt      12(%ebp)
        fxch      %st(1)
        jmp       ..B1.49
..B1.45:
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fldz      
        fdivrl    .L_2il0floatpacket.12@GOTOFF(%ebx)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fstpt     200(%esp)
        lea       72(%eax), %edx
        fldt      12(%ebp)
        addl      $84, %eax
        movl      %edx, 12(%esp)
        fxch      %st(1)
        jmp       ..B1.49
..B1.46:
        je        ..B1.70
..B1.47:
        cmpl      $32767, %edx
        je        ..B1.63
..B1.48:
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fldt      48(%eax)
        lea       72(%eax), %edx
        fmul      %st(0), %st
        addl      $84, %eax
        fstpt     200(%esp)
        fldt      12(%ebp)
        movl      %edx, 12(%esp)
        fxch      %st(1)
..B1.49:
        fldt      80(%esp)
        xorl      %edx, %edx
        movl      %edx, 140(%esp)
        fstpt     128(%esp)
        fldt      128(%esp)
        fxch      %st(2)
        fstpt     116(%esp)
        movl      %edx, 168(%esp)
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      24(%ebp)
        fstpt     144(%esp)
        addl      $-16, %esp
        lea       188(%esp), %ecx
        lea       160(%esp), %edx
        movl      %ecx, (%esp)
        lea       132(%esp), %ecx
        movl      %edx, 4(%esp)
        movl      %ecx, 8(%esp)
        movl      %eax, -116(%edx)
        fstpt     -128(%edx)
        call      __libm_atan2l_k80
..B1.82:
        fldt      32(%esp)
        movl      44(%esp), %eax
        addl      $16, %esp
..B1.50:
        fldt      172(%esp)
        fmul      %st, %st(1)
        fld       %st(0)
        movl      12(%esp), %edx
        fsubr     %st(2), %st
        movl      196(%esp), %ecx
        fsubrp    %st, %st(2)
        fsub      %st(1), %st
        fldt      184(%esp)
        addl      $-32, %esp
        faddp     %st, %st(1)
        fldt      (%edx)
        fld       %st(0)
        fmul      %st(3), %st
        fstpt     (%esp)
        fldt      (%eax)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        movl      %ecx, 24(%esp)
        call      __libm_scalbl_k80
..B1.83:
        addl      $32, %esp
..B1.51:
        fstpt     212(%esp)
        testl     %esi, %esi
        je        ..B1.53
..B1.52:
        fldcw     46(%esp)
..B1.53:
        movl      200(%esp), %edx
        movl      %edx, (%edi)
        movl      212(%esp), %edx
        movl      %edx, 12(%edi)
        movl      216(%esp), %edx
        movl      %edx, 16(%edi)
        movl      204(%esp), %ecx
        movl      208(%esp), %esi
        movl      220(%esp), %edx
        movl      %ecx, 4(%edi)
        movl      %esi, 8(%edi)
        movl      %edx, 20(%edi)
        movl      224(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.62
..B1.54:
        movl      %edi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.55:
        cmpl      $-2147483648, 20(%esp)
        jne       ..B1.41
..B1.56:
        cmpl      $0, 16(%esp)
        jne       ..B1.41
..B1.57:
        fstp      %st(2)
        fstp      %st(0)
        cmpl      $0, 4(%esp)
        jne       ..B1.60
..B1.58:
        cmpl      $0, (%esp)
        jne       ..B1.60
..B1.59:
        fstp      %st(0)
        fldt      80(%esp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fstpt     200(%esp)
        lea       72(%eax), %edx
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        addl      $84, %eax
        movl      %edx, 12(%esp)
        jmp       ..B1.49
..B1.60:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fldt      108(%eax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        movzwl    8(%esp), %ecx
        movl      %ecx, %edx
        andl      $-32768, %ecx
        fld       %st(0)
        orl       $-49153, %ecx
        fld       %st(1)
        movw      %cx, 8(%esp)
        lea       72(%eax), %ecx
        fldt      (%esp)
        addl      $84, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %ecx, 12(%esp)
        addl      $-32, %esp
        andl      $32767, %edx
        fsubr     %st(3), %st
        fsubrp    %st, %st(3)
        fld       %st(2)
        lea       -33167(%edx,%edx), %edx
        fmul      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(2)
        fsub      %st(1), %st
        fsubrp    %st, %st(3)
        fsub      %st(2), %st
        fxch      %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        fxch      %st(5)
        fmulp     %st, %st(3)
        fld       %st(4)
        fmulp     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      (%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fstpt     (%esp)
        fldt      (%eax)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        movl      %edx, 24(%esp)
        movl      %eax, 60(%esp)
        fstpt     48(%esp)
        call      __libm_scalbl_k80
..B1.84:
        fldt      48(%esp)
        movl      60(%esp), %eax
        addl      $32, %esp
..B1.61:
        fxch      %st(1)
        fstpt     200(%esp)
        fldt      12(%ebp)
        fxch      %st(1)
        jmp       ..B1.49
..B1.62:
        call      __stack_chk_fail@PLT
..B1.63:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.65
..B1.64:
        cmpl      $0, 24(%ebp)
        je        ..B1.48
..B1.65:
        cmpl      $32767, %ecx
        je        ..B1.67
..B1.66:
        fldt      24(%ebp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fmul      %st(0), %st
        lea       72(%eax), %edx
        fstpt     200(%esp)
        addl      $84, %eax
        fldt      12(%ebp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        movl      %edx, 12(%esp)
        jmp       ..B1.49
..B1.67:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.66
..B1.68:
        cmpl      $0, 12(%ebp)
        jne       ..B1.66
..B1.69:
        fldt      12(%ebp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fld       %st(0)
        lea       72(%eax), %edx
        fmul      %st(1), %st
        addl      $84, %eax
        fstpt     200(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        movl      %edx, 12(%esp)
        jmp       ..B1.49
..B1.70:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.72
..B1.71:
        cmpl      $0, 12(%ebp)
        je        ..B1.47
..B1.72:
        cmpl      $32767, %edx
        je        ..B1.74
..B1.73:
        fldt      12(%ebp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fldt      24(%ebp)
        lea       72(%eax), %edx
        fmul      %st(1), %st
        addl      $84, %eax
        fstpt     200(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        movl      %edx, 12(%esp)
        jmp       ..B1.49
..B1.74:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.73
..B1.75:
        cmpl      $0, 24(%ebp)
        jne       ..B1.73
..B1.76:
        fldt      24(%ebp)
        lea       _CONSTANTS@GOTOFF(%ebx), %eax
        fmul      %st(0), %st
        lea       72(%eax), %edx
        fstpt     200(%esp)
        addl      $84, %eax
        fldt      12(%ebp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        movl      %edx, 12(%esp)
        jmp       ..B1.49
..B1.77:
        xorl      %esi, %esi
        jmp       ..B1.7
..B1.85:
        cmpl      $32767, %ecx
        je        ..B1.70
        jmp       ..B1.47
..B1.86:
        fstp      %st(0)
        jmp       ..B1.42
        .align    16,0x90
	.type	clog10l,@function
	.size	clog10l,.-clog10l
	.data
# -- End  clog10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,12
	.space 4, 0x00 	# pad
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16358
	.word	0
	.word	37342
	.word	47265
	.word	62214
	.word	46340
	.word	16382
	.word	0
	.word	64512
	.word	65535
	.word	31
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8256
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.word	55465
	.word	56923
	.word	16381
	.word	0
	.word	43711
	.word	54638
	.word	50772
	.word	56481
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16583
	.word	0
	.word	0
	.word	0
	.word	39557
	.word	39456
	.word	16381
	.word	0
	.word	51839
	.word	3796
	.word	3310
	.word	34305
	.word	49112
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,144
	.align 16
_Q1:
	.word	3134
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	49331
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.type	_Q1,@object
	.size	_Q1,24
	.align 8
.L_2il0floatpacket.12:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.data
	.hidden __libm_logl_k80
	.hidden __libm_hypot2l_k80
	.hidden __libm_scalbl_k80
	.hidden __libm_atan2l_k80
	.section .note.GNU-stack, ""
# End
