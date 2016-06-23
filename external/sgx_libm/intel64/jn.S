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
# parameter 1: %edi
# parameter 2: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_jn.1:
..L2:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        subq      $208, %rsp
	.cfi_def_cfa_offset 240
        movl      %edi, %ebx
        movsd     %xmm0, (%rsp)
        movl      $1, %r14d
        xorb      %r15b, %r15b
..B1.2:
        fnstcw    194(%rsp)
..B1.3:
        movzwl    6(%rsp), %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        ..B1.74
..B1.4:
        testl     %ebx, %ebx
        je        ..B1.55
..B1.5:
        lea       _zeros(%rip), %rsi
        movsd     (%rsp), %xmm2
        movsd     (%rsi), %xmm0
        ucomisd   %xmm0, %xmm2
        jp        ..B1.6
        je        ..B1.79
..B1.6:
        testl     %ebx, %ebx
        jge       ..B1.8
..B1.7:
        movl      %ebx, %eax
        negl      %ebx
        notl      %eax
        andl      $1, %eax
        lea       -1(%rax,%rax), %r14d
..B1.8:
        comisd    %xmm2, %xmm0
        jbe       ..B1.10
..B1.9:
        movl      %r14d, %eax
        negl      %eax
        testb     $1, %bl
        xorps     .L_2il0floatpacket.1(%rip), %xmm2
        movsd     %xmm2, (%rsp)
        cmovne    %eax, %r14d
..B1.10:
        cmpl      $1, %ebx
        je        ..B1.78
..B1.11:
        movl      %ebx, %ecx
        sarl      $5, %ecx
        cmpl      $160, %ecx
        jg        ..B1.13
..B1.12:
        movslq    %ecx, %rcx
        lea       XZERO(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm1
        jmp       ..B1.14
..B1.13:
        pxor      %xmm1, %xmm1
        lea       -640(,%rcx,4), %eax
        lea       (,%rax,8), %ecx
        subl      %eax, %ecx
        cvtsi2sd  %ecx, %xmm1
        addsd     .L_2il0floatpacket.0(%rip), %xmm1
..B1.14:
        comisd    %xmm2, %xmm1
        ja        ..B1.83
..B1.15:
        movzwl    194(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.19
..B1.16:
        orl       $-64768, %ecx
        movw      %cx, 192(%rsp)
..B1.17:
        fldcw     192(%rsp)
..B1.18:
        movsd     (%rsp), %xmm2
        movb      $1, %r15b
..B1.19:
        movsd     %xmm2, 80(%rsp)
        cmpl      $1753, %ebx
        fldl      80(%rsp)
        movl      %ebx, 80(%rsp)
        fildl     80(%rsp)
        jg        ..B1.32
..B1.20:
        fldt      val_0_4(%rip)
        fmul      %st(1), %st
        fcomip    %st(2), %st
        jbe       ..B1.32
..B1.21:
        fstp      %st(0)
        lea       _val_2_0L(%rip), %rax
        lea       _val_1_0L(%rip), %rcx
        testl     %ebx, %ebx
        fldt      (%rax)
        fdivrp    %st, %st(1)
        fldt      (%rcx)
        movl      $0, %ecx
        fld       %st(0)
        movl      %ecx, %eax
        fld       %st(0)
        fld       %st(2)
        fld       %st(3)
        jle       ..B1.25
..B1.23:
        incl      %ecx
        fmul      %st(5), %st
        cmpl      %ebx, %ecx
        jb        ..B1.23
..B1.25:
        movl      %eax, %ecx
        testl     %ebx, %ebx
        jle       ..B1.28
..B1.27:
        incl      %ecx
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(4), %st
        cmpl      %ebx, %ecx
        fxch      %st(3)
        fxch      %st(1)
        jb        ..B1.27
..B1.28:
        fstp      %st(3)
..B1.29:
        fxch      %st(4)
        fmul      %st(0), %st
        fchs      
..B1.30:
        incl      %eax
        incl      %ebx
        movl      %eax, %ecx
        fld       %st(3)
        imull     %ebx, %ecx
        fxch      %st(1)
        fmul      %st, %st(2)
        movl      %ecx, 80(%rsp)
        fildl     80(%rsp)
        fdivrp    %st, %st(3)
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(1)
        fucomip   %st(4), %st
        fxch      %st(1)
        jne       ..B1.30
        jp        ..B1.30
..B1.31:
        fstp      %st(0)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fdivrp    %st, %st(1)
        fxch      %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.45
..B1.32:
        fcomi     %st(1), %st
        ja        ..B1.51
..B1.33:
        lea       _val_20_0L(%rip), %rax
        lea       _val_1000_0L(%rip), %rcx
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fcomip    %st(2), %st
        ja        ..B1.57
..B1.34:
        lea       _val_4_0L(%rip), %rax
        fld       %st(0)
        lea       _val_1_0L(%rip), %rdi
        lea       _val_8_0L(%rip), %rsi
        lea       _val_2_0L(%rip), %r8
        lea       _val_3_0L(%rip), %r9
        lea       _val_5_0L(%rip), %r10
        movl      $-1, %ecx
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      (%rax)
        xorb      %al, %al
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fldt      (%rsi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fldt      (%rdi)
        fld       %st(0)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     80(%rsp)
        fldt      (%r8)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fstpt     32(%rsp)
        fldt      (%r9)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldt      (%r10)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      96(%rsp)
        fldt      32(%rsp)
        fldt      16(%rsp)
..B1.35:
        fldt      160(%rsp)
        fld       %st(3)
        fmul      %st(4), %st
        testl     %ecx, %ecx
        fxch      %st(4)
        fstpt     96(%rsp)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      176(%rsp)
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
        fldt      96(%rsp)
        fldt      32(%rsp)
        jle       ..B1.37
..B1.36:
        fxch      %st(3)
        faddp     %st, %st(6)
        fldt      48(%rsp)
        fadd      %st(2), %st
        fstpt     48(%rsp)
        jmp       ..B1.38
..B1.37:
        fxch      %st(3)
        fsubrp    %st, %st(6)
        fldt      48(%rsp)
        fsub      %st(2), %st
        fstpt     48(%rsp)
..B1.38:
        fldt      128(%rsp)
        fadd      %st, %st(3)
        faddp     %st, %st(5)
        fldt      144(%rsp)
        fadd      %st, %st(1)
        faddp     %st, %st(4)
        fld       %st(1)
        fdiv      %st(6), %st
        fabs      
        fldt      80(%rsp)
        fcomip    %st(1), %st
        jbe       ..B1.40
..B1.39:
        fstpt     80(%rsp)
        movb      $1, %al
        jmp       ..B1.41
..B1.40:
        fstp      %st(0)
        testb     %al, %al
        jne       ..B1.56
..B1.41:
        fldt      (%rsp)
        negl      %ecx
        fucomip   %st(6), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(5)
        jne       ..B1.35
        jp        ..B1.35
..B1.42:
        fstp      %st(4)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        fldt      64(%rsp)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        fldt      112(%rsp)
..B1.43:
        addl      %ebx, %ebx
        addq      $-16, %rsp
	.cfi_def_cfa_offset 256
        negl      %ebx
        lea       16(%rsp), %rdi
        decl      %ebx
        lea       48(%rsp), %rsi
        movl      %ebx, %edx
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     80(%rsi)
..___tag_value_jn.11:
        call      __libm_sincos_pi4l@PLT
..___tag_value_jn.12:
..B1.82:
        fldt      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.44:
        fldt      16(%rsp)
        lea       _tonpi(%rip), %rax
        fldt      (%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fdivp     %st, %st(3)
        fxch      %st(2)
        fsqrt     
        fldt      32(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(1)
..B1.45:
        testl     %r14d, %r14d
        jl        ..B1.47
..B1.46:
        fstpt     (%rsp)
        jmp       ..B1.48
..B1.47:
        fchs      
        fstpt     (%rsp)
..B1.48:
        testb     %r15b, %r15b
        je        ..B1.50
..B1.49:
        fldcw     194(%rsp)
..B1.50:
        fldt      (%rsp)
        fstpl     16(%rsp)
        movsd     16(%rsp), %xmm0
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.51:
        fstp      %st(0)
        fld       %st(0)
        lea       (%rbx,%rbx), %esi
        movl      %esi, 80(%rsp)
        lea       _val_1_0L(%rip), %rcx
        lea       _val_2_0L(%rip), %rdi
        lea       _val_0_0L(%rip), %rax
        fmul      %st(1), %st
        fchs      
        fstpt     64(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      (%rax)
        fldt      (%rcx)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fstpt     16(%rsp)
        fildl     80(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdi)
        fstpt     48(%rsp)
        fldt      (%rsp)
        jmp       ..B1.52
..B1.53:
        fstp      %st(3)
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(1)
..B1.52:
        fldt      48(%rsp)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(1), %st
        fld       %st(2)
        fmul      %st(2), %st
        fldt      64(%rsp)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fld       %st(0)
        fxch      %st(1)
        fdiv      %st(5), %st
        fucomi    %st(4), %st
        fxch      %st(3)
        fstpt     32(%rsp)
        jne       ..B1.53
        jp        ..B1.53
        jmp       ..B1.67
..B1.55:
        movsd     (%rsp), %xmm0
        call      j0@PLT
..B1.83:
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.56:
        fstp      %st(4)
        fstp      %st(3)
        fstp      %st(0)
        fstp      %st(0)
        fldt      64(%rsp)
        fstpt     (%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        fldt      112(%rsp)
        jmp       ..B1.43
..B1.57:
        fstp      %st(0)
        lea       _val_36_0L(%rip), %rax
        fldt      (%rax)
        fcomi     %st(1), %st
        jbe       ..B1.61
..B1.58:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 256
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     128(%rsp)
..___tag_value_jn.37:
        call      __j0l@PLT
..___tag_value_jn.38:
..B1.84:
        fldt      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.59:
        fxch      %st(1)
        fstpt     32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 256
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     128(%rsp)
..___tag_value_jn.41:
        call      __j1l@PLT
..___tag_value_jn.42:
..B1.85:
        fldt      128(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.60:
        fxch      %st(1)
        jmp       ..B1.63
..B1.61:
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
	.cfi_def_cfa_offset 256
        lea       112(%rsp), %rdi
        lea       96(%rsp), %rsi
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     32(%rsi)
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
        fstpt     -48(%rsi)
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -16(%rsi)
..___tag_value_jn.45:
        call      __libm_sincos_pi4l@PLT
..___tag_value_jn.46:
..B1.86:
        fldt      128(%rsp)
        fldt      80(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.62:
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      80(%rsp)
        fldt      16(%rsp)
        fmul      %st(1), %st
        fxch      %st(5)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsqrt     
        fldt      96(%rsp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpt     32(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.63:
        lea       _val_2_0L(%rip), %rax
        cmpl      $1, %ebx
        fldt      (%rax)
        movl      $0, %eax
        fld       %st(0)
        jle       ..B1.91
..B1.64:
        fldt      32(%rsp)
        decl      %ebx
..B1.65:
        fld       %st(1)
        incl      %eax
        fdiv      %st(5), %st
        cmpl      %ebx, %eax
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(2)
        fsubp     %st, %st(1)
        fld       %st(3)
        jae       ..B1.66
..B1.92:
        fstp      %st(4)
        fxch      %st(3)
        jmp       ..B1.65
..B1.66:
        fstp      %st(5)
        fstp      %st(4)
        fstp      %st(0)
        fstp      %st(0)
        fstpt     32(%rsp)
        jmp       ..B1.45
..B1.67:
        fstp      %st(2)
        fstp      %st(4)
        fstp      %st(0)
        fstp      %st(1)
        fstp      %st(1)
        fldt      48(%rsp)
        decl      %ebx
        fldt      16(%rsp)
        xorl      %eax, %eax
        movl      %ebx, 80(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fdiv      %st, %st(3)
        fildl     80(%rsp)
        fmul      %st(3), %st
        fldt      112(%rsp)
        fdivr     %st, %st(3)
..B1.68:
        fld       %st(5)
        incl      %eax
        fmul      %st(2), %st
        fxch      %st(2)
        fsub      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        cmpl      %ebx, %eax
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(5)
        fld       %st(1)
        jge       ..B1.69
..B1.93:
        fstp      %st(7)
        fstp      %st(1)
        fxch      %st(2)
        jmp       ..B1.68
..B1.69:
        fstp      %st(0)
        fstp      %st(4)
        fstp      %st(4)
        fstp      %st(0)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fcomip    %st(1), %st
        fstp      %st(0)
        fxch      %st(1)
        fstpt     (%rsp)
        jae       ..B1.72
        jp        ..B1.72
..B1.70:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 256
        fstpt     (%rsp)
..___tag_value_jn.49:
        call      __j1l@PLT
..___tag_value_jn.50:
..B1.87:
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.71:
        fldt      (%rsp)
        fdivrp    %st, %st(1)
        jmp       ..B1.45
..B1.72:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 256
        fstpt     (%rsp)
..___tag_value_jn.53:
        call      __j0l@PLT
..___tag_value_jn.54:
..B1.88:
        addq      $16, %rsp
	.cfi_def_cfa_offset 240
..B1.73:
        fldt      32(%rsp)
        fdivrp    %st, %st(1)
        jmp       ..B1.45
..B1.74:
        testl     $1048575, 4(%rsp)
        jne       ..B1.77
..B1.75:
        cmpl      $0, (%rsp)
        jne       ..B1.77
..B1.76:
        movzbl    7(%rsp), %ecx
        movl      %ebx, %eax
        andl      $128, %ecx
        lea       _zeros(%rip), %rdx
        shrl      $7, %ecx
        shrl      $31, %eax
        xorl      %eax, %ecx
        andl      %ebx, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.77:
        movsd     (%rsp), %xmm0
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.78:
        movaps    %xmm2, %xmm0
        call      j1@PLT
..B1.89:
        movaps    %xmm0, %xmm1
        testl     %r14d, %r14d
        xorps     .L_2il0floatpacket.1(%rip), %xmm1
        jge       ..L78
        movaps    %xmm1, %xmm0
..L78:
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.79:
        movzbl    7(%rsp), %ecx
        movl      %ebx, %eax
        andl      $128, %ecx
        shrl      $7, %ecx
        shrl      $31, %eax
        xorl      %eax, %ecx
        andl      %ebx, %ecx
        movsd     (%rsi,%rcx,8), %xmm0
        addq      $208, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.91:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(1)
        jmp       ..B1.45
        .align    16,0x90
	.cfi_endproc
	.type	jn,@function
	.size	jn,.-jn
	.data
# -- End  jn
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
val_0_4:
	.byte	0x00,0xd0,0xcc,0xcc,0xcc,0xcc,0xcc,0xcc,0xfd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	val_0_4,@object
	.size	val_0_4,16
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
	.word	0
	.word	0
	.type	_val_2_0L,@object
	.size	_val_2_0L,16
	.align 2
_val_1_0L:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_val_1_0L,@object
	.size	_val_1_0L,16
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
