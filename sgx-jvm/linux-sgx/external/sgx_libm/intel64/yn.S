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
# parameter 1: %edi
# parameter 2: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_yn.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        subq      $224, %rsp
	.cfi_def_cfa_offset 256
        movl      %edi, %r13d
        movsd     %xmm0, (%rsp)
        xorb      %r14b, %r14b
..B1.2:
        fnstcw    218(%rsp)
..B1.3:
        movzwl    6(%rsp), %ecx
        movl      $1, %r15d
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $2047, %ecx
        je        ..B1.58
..B1.4:
        lea       _zeros(%rip), %rax
        movsd     (%rsp), %xmm0
        movsd     (%rax), %xmm1
        comisd    %xmm0, %xmm1
        jb        ..B1.8
..B1.5:
        ucomisd   %xmm1, %xmm0
        jp        ..B1.6
        je        ..B1.37
..B1.6:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
..B1.7:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.8:
        testl     %r13d, %r13d
        je        ..B1.36
..B1.9:
        jge       ..B1.11
..B1.10:
        negl      %r13d
        movl      %r13d, %eax
        notl      %eax
        andl      $1, %eax
        lea       -1(%rax,%rax), %r15d
..B1.11:
        cmpl      $1, %r13d
        je        ..B1.50
..B1.12:
        movl      %r13d, %ecx
        sarl      $5, %ecx
        cmpl      $160, %ecx
        jg        ..B1.14
..B1.13:
        movslq    %ecx, %rcx
        lea       XINF(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm1
        jmp       ..B1.15
..B1.14:
        pxor      %xmm1, %xmm1
        lea       -640(,%rcx,4), %eax
        lea       (,%rax,8), %ecx
        subl      %eax, %ecx
        cvtsi2sd  %ecx, %xmm1
        addsd     .L_2il0floatpacket.0(%rip), %xmm1
..B1.15:
        comisd    %xmm0, %xmm1
        ja        ..B1.35
..B1.16:
        movzwl    218(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.17:
        orl       $-64768, %ecx
        movw      %cx, 216(%rsp)
..B1.18:
        fldcw     216(%rsp)
..B1.19:
        movsd     (%rsp), %xmm0
        movb      $1, %r14b
..B1.20:
        movsd     %xmm0, (%rsp)
        lea       _val_20_0L(%rip), %rax
        fldl      (%rsp)
        lea       _val_1000_0L(%rip), %rcx
        movl      %r13d, (%rsp)
        fildl     (%rsp)
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fcomip    %st(2), %st
        ja        ..B1.39
..B1.21:
        lea       _val_4_0L(%rip), %rcx
        fld       %st(0)
        lea       _val_8_0L(%rip), %rsi
        movl      $-1, %eax
        lea       _val_n1_0L(%rip), %r8
        lea       _val_3_0L(%rip), %r10
        lea       _val_p1_0L(%rip), %rdi
        lea       _val_5_0L(%rip), %r11
        lea       _val_2_0L(%rip), %r9
        fxch      %st(2)
        fstl      112(%rsp)
        fldt      (%rcx)
        xorb      %cl, %cl
        fstpt     136(%rsp)
        fldt      136(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     200(%rsp)
        fldt      200(%rsp)
        fldt      (%rsi)
        lea       _val_0_0L(%rip), %rsi
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     168(%rsp)
        fldt      168(%rsp)
        fldt      (%rdi)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     64(%rsp)
        fldt      (%r8)
        faddp     %st, %st(2)
        fdivrp    %st, %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r9)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     152(%rsp)
        fldt      (%r10)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstpt     96(%rsp)
        fldt      (%r11)
        fstpt     16(%rsp)
        fldt      (%rsi)
        fstpt     120(%rsp)
        fldt      16(%rsp)
        fldt      96(%rsp)
        fldt      32(%rsp)
        fldt      (%rsp)
..B1.22:
        fldt      200(%rsp)
        fld       %st(3)
        fmul      %st(4), %st
        testl     %eax, %eax
        fsubr     %st(1), %st
        fxch      %st(4)
        fstpt     96(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     184(%rsp)
        fldt      184(%rsp)
        fldt      80(%rsp)
        fldt      168(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fdivrp    %st, %st(5)
        fxch      %st(6)
        fmulp     %st, %st(4)
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        fdivrp    %st, %st(1)
        fmul      %st(2), %st
        fldt      96(%rsp)
        jle       ..B1.24
..B1.23:
        fldt      48(%rsp)
        fadd      %st(2), %st
        fstpt     48(%rsp)
        fxch      %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.25
..B1.24:
        fldt      48(%rsp)
        fsub      %st(2), %st
        fstpt     48(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(2)
..B1.25:
        fldt      152(%rsp)
        fadd      %st, %st(5)
        fldt      80(%rsp)
        faddp     %st, %st(1)
        fstpt     80(%rsp)
        fld       %st(0)
        fdiv      %st(2), %st
        fldt      136(%rsp)
        fadd      %st, %st(4)
        faddp     %st, %st(5)
        fld       %st(0)
        fchs      
        fldt      120(%rsp)
        fcomip    %st(2), %st
        fcmovbe   %st(1), %st
        fstp      %st(1)
        fldt      64(%rsp)
        fcomip    %st(1), %st
        jbe       ..B1.27
..B1.26:
        fstpt     64(%rsp)
        movb      $1, %cl
        jmp       ..B1.28
..B1.27:
        fstp      %st(0)
        testb     %cl, %cl
        jne       ..B1.38
..B1.28:
        fldt      16(%rsp)
        negl      %eax
        fucomip   %st(2), %st
        fxch      %st(4)
        fxch      %st(1)
        jne       ..B1.22
        jp        ..B1.22
..B1.29:
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(1)
        fldt      184(%rsp)
        fstpt     16(%rsp)
        fstpt     (%rsp)
        fstpt     32(%rsp)
        fldl      112(%rsp)
..B1.30:
        addl      %r13d, %r13d
        addq      $-16, %rsp
	.cfi_def_cfa_offset 272
        negl      %r13d
        lea       32(%rsp), %rdi
        decl      %r13d
        lea       48(%rsp), %rsi
        movl      %r13d, %edx
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpl     80(%rsi)
..___tag_value_yn.22:
        call      __libm_sincos_pi4l@PLT
..___tag_value_yn.23:
..B1.67:
        fldl      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 256
..B1.31:
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      32(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fdivp     %st, %st(3)
        fxch      %st(2)
        fsqrt     
        fldt      16(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..B1.32:
        testb     %r14b, %r14b
        je        ..B1.34
..B1.33:
        fldcw     218(%rsp)
..B1.34:
        fldt      16(%rsp)
        testl     %r15d, %r15d
        fstpl     (%rsp)
        movsd     (%rsp), %xmm1
        movaps    %xmm1, %xmm0
        xorps     .L_2il0floatpacket.1(%rip), %xmm0
        jl        ..L25
        movaps    %xmm1, %xmm0
..L25:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.35:
        movb      7(%rsp), %al
        lea       _large_value_64(%rip), %rcx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rcx,%rdx,8), %xmm0
        mulsd     (%rcx), %xmm0
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.36:
        call      y0@PLT
..B1.68:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.37:
        lea       8+_ones(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     %xmm1, %xmm0
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.38:
        fstp      %st(2)
        fstp      %st(3)
        fstp      %st(1)
        fldt      184(%rsp)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     32(%rsp)
        fldl      112(%rsp)
        jmp       ..B1.30
..B1.39:
        fstp      %st(0)
        lea       _val_36_0L(%rip), %rax
        fldt      (%rax)
        fcomi     %st(1), %st
        jbe       ..B1.43
..B1.40:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 272
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpl     128(%rsp)
..___tag_value_yn.71:
        call      __y0l@PLT
..___tag_value_yn.72:
..B1.69:
        fldl      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 256
..B1.41:
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 272
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpl     128(%rsp)
..___tag_value_yn.75:
        call      __y1l@PLT
..___tag_value_yn.76:
..B1.70:
        fldl      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 256
..B1.42:
        fxch      %st(1)
        jmp       ..B1.45
..B1.43:
        lea       _ones(%rip), %rax
        lea       _PP0(%rip), %rcx
        lea       16+_PP0(%rip), %r9
        lea       _PP1(%rip), %r8
        lea       16+_PP1(%rip), %r10
        lea       32+_PP0(%rip), %r11
        lea       48+_PP1(%rip), %rsi
        lea       64+_PP0(%rip), %rdi
        fldl      (%rax)
        lea       32+_PP1(%rip), %rax
        fdiv      %st(2), %st
        movl      $-1, %edx
        fmul      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(1)
        fstpt     (%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rcx)
        lea       48+_PP0(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%r8)
        lea       64+_PP1(%rip), %r8
        fmul      %st(3), %st
        fldt      (%r9)
        lea       80+_PP0(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        lea       80+_PP1(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        lea       96+_PP0(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       96+_PP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       112+_PP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       112+_PP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       128+_PP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       128+_PP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       144+_PP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rax)
        lea       144+_PP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 272
        lea       96(%rsp), %rdi
        lea       112(%rsp), %rsi
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpl     16(%rsi)
        fldt      (%rax)
        lea       _QP0(%rip), %rax
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     -32(%rsi)
        fldt      (%rax)
        lea       _QP1(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rax)
        lea       16+_QP0(%rip), %rax
        fmul      %st(2), %st
        fldt      (%rax)
        lea       16+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       32+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       32+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       48+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       48+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       64+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       64+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       80+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       80+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       96+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       96+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       112+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       112+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_QP1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       144+_QP0(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        lea       144+_QP1(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fstpt     -64(%rsi)
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -48(%rsi)
..___tag_value_yn.79:
        call      __libm_sincos_pi4l@PLT
..___tag_value_yn.80:
..B1.71:
        fldl      128(%rsp)
        fldt      80(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 256
..B1.44:
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      96(%rsp)
        fmul      %st, %st(2)
        fmulp     %st, %st(4)
        fldt      16(%rsp)
        fldt      (%rax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsqrt     
        fldt      80(%rsp)
        fmul      %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     32(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fsubp     %st, %st(2)
        fmulp     %st, %st(1)
..B1.45:
        lea       _val_2_0L(%rip), %rax
        cmpl      $1, %r13d
        fldt      (%rax)
        movl      $0, %eax
        fld       %st(0)
        jle       ..B1.49
..B1.46:
        fldt      32(%rsp)
        decl      %r13d
..B1.47:
        fld       %st(1)
        incl      %eax
        fdiv      %st(5), %st
        cmpl      %r13d, %eax
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(2)
        fsubp     %st, %st(1)
        fld       %st(3)
        jae       ..B1.48
..B1.73:
        fstp      %st(4)
        fxch      %st(3)
        jmp       ..B1.47
..B1.48:
        fstp      %st(5)
        fstp      %st(4)
        fstp      %st(0)
        fstp      %st(0)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        jmp       ..B1.32
..B1.49:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.32
..B1.50:
        testl     %ecx, %ecx
        jne       ..B1.57
..B1.51:
        testl     $1048575, 4(%rsp)
        jne       ..B1.57
..B1.52:
        cmpl      $1, (%rsp)
        jne       ..B1.57
..B1.53:
        lea       _large_value_64(%rip), %rax
        testl     %r15d, %r15d
        movsd     (%rax), %xmm1
        jl        ..B1.55
..B1.54:
        lea       8+_large_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.55:
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
..B1.56:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.57:
        call      y1@PLT
..B1.72:
        movaps    %xmm0, %xmm1
        testl     %r15d, %r15d
        xorps     .L_2il0floatpacket.1(%rip), %xmm1
        jge       ..L104
        movaps    %xmm1, %xmm0
..L104:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.58:
        testl     $1048575, 4(%rsp)
        jne       ..B1.64
..B1.59:
        cmpl      $0, (%rsp)
        jne       ..B1.64
..B1.60:
        testb     $-128, 7(%rsp)
        je        ..B1.62
..B1.61:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.62:
        lea       _zeros(%rip), %rdx
        movl      %r13d, %eax
        shrl      $31, %eax
        andl      %r13d, %eax
        movsd     (%rdx,%rax,8), %xmm0
..B1.63:
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 256
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.64:
        lea       _ones(%rip), %rax
        movsd     (%rsp), %xmm0
        mulsd     (%rax), %xmm0
        addq      $224, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	yn,@function
	.size	yn,.-yn
	.data
# -- End  yn
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
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
	.word	0
	.word	0
	.type	_val_20_0L,@object
	.size	_val_20_0L,16
	.align 2
_val_1000_0L:
	.word	0
	.word	0
	.word	0
	.word	64000
	.word	16392
	.word	0
	.word	0
	.word	0
	.type	_val_1000_0L,@object
	.size	_val_1000_0L,16
	.align 2
_val_4_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16385
	.word	0
	.word	0
	.word	0
	.type	_val_4_0L,@object
	.size	_val_4_0L,16
	.align 2
_val_8_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16386
	.word	0
	.word	0
	.word	0
	.type	_val_8_0L,@object
	.size	_val_8_0L,16
	.align 2
_val_n1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.type	_val_n1_0L,@object
	.size	_val_n1_0L,16
	.align 2
_val_3_0L:
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_val_3_0L,@object
	.size	_val_3_0L,16
	.align 2
_val_p1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_val_p1_0L,@object
	.size	_val_p1_0L,16
	.align 2
_val_5_0L:
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16385
	.word	0
	.word	0
	.word	0
	.type	_val_5_0L,@object
	.size	_val_5_0L,16
	.align 2
_val_2_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_val_2_0L,@object
	.size	_val_2_0L,16
	.align 2
_val_0_0L:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_val_0_0L,@object
	.size	_val_0_0L,16
	.align 2
_tonpi:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_tonpi,@object
	.size	_tonpi,16
	.align 2
_val_36_0L:
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_val_36_0L,@object
	.size	_val_36_0L,16
	.align 2
_PP0:
	.word	3555
	.word	4199
	.word	61716
	.word	36505
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	21640
	.word	56203
	.word	47336
	.word	57608
	.word	16322
	.word	0
	.word	0
	.word	0
	.word	3059
	.word	27554
	.word	42044
	.word	45622
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	55815
	.word	35656
	.word	54748
	.word	47307
	.word	16332
	.word	0
	.word	0
	.word	0
	.word	42025
	.word	6728
	.word	28939
	.word	34692
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	58943
	.word	11189
	.word	14960
	.word	38786
	.word	16344
	.word	0
	.word	0
	.word	0
	.word	43526
	.word	20005
	.word	29826
	.word	37014
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	58976
	.word	17109
	.word	36079
	.word	36708
	.word	16359
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_PP0,@object
	.size	_PP0,160
	.align 2
_PP1:
	.word	52008
	.word	23245
	.word	2457
	.word	38658
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	61876
	.word	57278
	.word	25234
	.word	61346
	.word	49090
	.word	0
	.word	0
	.word	0
	.word	33834
	.word	23040
	.word	39409
	.word	49003
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	54525
	.word	6554
	.word	40935
	.word	51421
	.word	49100
	.word	0
	.word	0
	.word	0
	.word	9848
	.word	9769
	.word	18248
	.word	38344
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	45790
	.word	60252
	.word	47538
	.word	43957
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	27022
	.word	41654
	.word	23333
	.word	43744
	.word	16351
	.word	0
	.word	0
	.word	0
	.word	12816
	.word	40723
	.word	46387
	.word	47196
	.word	49127
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_PP1,@object
	.size	_PP1,160
	.align 2
_QP0:
	.word	30466
	.word	14887
	.word	33885
	.word	35327
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	41139
	.word	65374
	.word	59488
	.word	50915
	.word	49088
	.word	0
	.word	0
	.word	0
	.word	35675
	.word	30946
	.word	22284
	.word	35505
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	65216
	.word	29458
	.word	42913
	.word	63176
	.word	49097
	.word	0
	.word	0
	.word	0
	.word	5130
	.word	24339
	.word	48915
	.word	38634
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	30040
	.word	27658
	.word	23448
	.word	34596
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	37816
	.word	17431
	.word	24401
	.word	49646
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	4377
	.word	35057
	.word	45578
	.word	33037
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	19863
	.word	65266
	.word	6202
	.word	53939
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	49142
	.word	0
	.word	0
	.word	0
	.type	_QP0,@object
	.size	_QP0,160
	.align 2
_QP1:
	.word	65402
	.word	6822
	.word	45128
	.word	37300
	.word	49084
	.word	0
	.word	0
	.word	0
	.word	41532
	.word	52948
	.word	50360
	.word	54021
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	21320
	.word	30785
	.word	26870
	.word	37955
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	55382
	.word	38611
	.word	29746
	.word	34115
	.word	16330
	.word	0
	.word	0
	.word	0
	.word	37194
	.word	6554
	.word	16235
	.word	42314
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	17886
	.word	15840
	.word	33918
	.word	38666
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	62905
	.word	55876
	.word	18072
	.word	57284
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	28423
	.word	64693
	.word	26579
	.word	40379
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	40135
	.word	58793
	.word	24002
	.word	37757
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_QP1,@object
	.size	_QP1,160
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
