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
	.file "j1.c"
	.text
..TXTST0:
# -- Begin  j1
	.text
       .align    16,0x90
	.globl j1
j1:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_j1.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        subq      $40, %rsp
	.cfi_def_cfa_offset 64
        xorb      %r13b, %r13b
        movsd     %xmm0, 24(%rsp)
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movl      28(%rsp), %r12d
        movl      %r12d, %edx
        andl      $2147483647, %edx
        shrl      $31, %r12d
        cmpl      $2146435072, %edx
        jge       ..B1.20
..B1.4:
        cmpl      $1012924416, %edx
        jge       ..B1.12
..B1.5:
        cmpl      $1048576, %edx
        jle       ..B1.7
..B1.6:
        movl      %r12d, %r12d
        lea       _small_value_64(%rip), %rax
        movsd     24(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     (%rax,%r12,8), %xmm1
        mulsd     (%rax), %xmm1
        subsd     %xmm1, %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.7:
        movl      24(%rsp), %eax
        je        ..B1.24
..B1.8:
        orl       %eax, %edx
        je        ..B1.10
..B1.9:
        movl      %r12d, %r12d
        lea       _small_value_64(%rip), %rax
        movsd     (%rax,%r12,8), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, (%rsp)
        movsd     24(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     (%rsp), %xmm1
        subsd     %xmm1, %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.10:
        movsd     24(%rsp), %xmm0
..B1.11:
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.12:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.16
..B1.13:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.14:
        fldcw     16(%rsp)
..B1.15:
        movb      $1, %r13b
..B1.16:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 80
        movsd     40(%rsp), %xmm0
        andps     .L_2il0floatpacket.1(%rip), %xmm0
        movsd     %xmm0, 24(%rsp)
        fldl      24(%rsp)
        fstpt     (%rsp)
..___tag_value_j1.33:
        call      __j1l@PLT
..___tag_value_j1.34:
..B1.27:
        addq      $16, %rsp
	.cfi_def_cfa_offset 64
..B1.17:
        fstpl     8(%rsp)
        testb     %r13b, %r13b
        movsd     8(%rsp), %xmm1
        je        ..B1.19
..B1.18:
        fldcw     18(%rsp)
..B1.19:
        movaps    %xmm1, %xmm0
        testl     %r12d, %r12d
        xorps     .L_2il0floatpacket.2(%rip), %xmm0
        jne       ..L36
        movaps    %xmm1, %xmm0
..L36:
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.20:
        andl      $-2146435073, %edx
        orl       24(%rsp), %edx
        je        ..B1.22
..B1.21:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     24(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.22:
        lea       _zeros(%rip), %rax
        movsd     (%rax,%r12,8), %xmm0
..B1.23:
        addq      $40, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.24:
        testl     %eax, %eax
        jne       ..B1.6
        jmp       ..B1.8
        .align    16,0x90
	.cfi_endproc
	.type	j1,@function
	.size	j1,.-j1
	.data
# -- End  j1
	.text
# -- Begin  __j1l
	.text
       .align    16,0x90
	.globl __j1l
__j1l:
# parameter 1: 96 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___j1l.62:
..L63:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        lea       176+_S(%rip), %rax
        fldt      96(%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        jbe       ..B2.9
..B2.2:
        lea       _S(%rip), %rcx
        fldt      (%rcx)
        fcomip    %st(1), %st
        jbe       ..B2.4
..B2.3:
        fld       %st(0)
        lea       _P1(%rip), %rax
        fmul      %st(1), %st
        lea       16+_P1(%rip), %rdx
        fld       %st(0)
        lea       32+_P1(%rip), %rcx
        fmul      %st(1), %st
        lea       48+_P1(%rip), %rsi
        fstpt     64(%rsp)
        lea       64+_P1(%rip), %rdi
        lea       80+_P1(%rip), %r8
        lea       96+_P1(%rip), %r9
        lea       112+_P1(%rip), %r10
        lea       128+_P1(%rip), %r11
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%rax)
        lea       144+_P1(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       160+_P1(%rip), %rdx
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       176+_P1(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       192+_P1(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       208+_P1(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       224+_P1(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       240+_P1(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       256+_P1(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       272+_P1(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       288+_P1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.4:
        movb      $1, %dl
        movl      $16, %eax
..B2.5:
        fldt      (%rax,%rcx)
        fcomip    %st(1), %st
        ja        ..B2.8
        jp        ..B2.8
..B2.6:
        incb      %dl
        addq      $16, %rax
        cmpb      $11, %dl
        jl        ..B2.5
..B2.8:
        decb      %dl
        movzbl    %dl, %ecx
        lea       _Z(%rip), %rax
        movq      %rcx, %rdx
        lea       _P(%rip), %rdi
        shlq      $4, %rdx
        lea       (%rcx,%rcx,4), %rsi
        shlq      $6, %rsi
        fldt      (%rax,%rdx)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%rsi,%rdi)
        fmul      %st(1), %st
        fldt      16(%rsi,%rdi)
        fmul      %st(2), %st
        fldt      32(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      64(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      80(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      96(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      112(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      128(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      144(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      160(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      176(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      192(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      208(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      224(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      240(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      256(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      272(%rsi,%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      288(%rsi,%rdi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      304(%rsi,%rdi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.9:
        lea       _ones(%rip), %rax
        lea       _val_36_0L(%rip), %rdx
        lea       _PP(%rip), %rcx
        lea       _QP(%rip), %rsi
        lea       16+_PP(%rip), %rdi
        lea       16+_QP(%rip), %r8
        lea       32+_PP(%rip), %r9
        lea       32+_QP(%rip), %r10
        fldl      (%rax)
        lea       48+_PP(%rip), %r11
        fdiv      %st(1), %st
        lea       48+_QP(%rip), %rax
        fxch      %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdx)
        lea       64+_PP(%rip), %rdx
        fmulp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rcx)
        lea       64+_QP(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       80+_PP(%rip), %rsi
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80+_QP(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       96+_PP(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       96+_QP(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       112+_PP(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       112+_QP(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_PP(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       128+_QP(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       144+_PP(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       144+_QP(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       64(%rsp), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        movl      $-3, %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fstpt     -32(%rdi)
        fldt      (%rsi)
        lea       80(%rsp), %rsi
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -32(%rsi)
..___tag_value___j1l.70:
        call      __libm_sincos_pi4l@PLT
..___tag_value___j1l.71:
..B2.18:
        fldt      112(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.10:
        fldt      64(%rsp)
        lea       _val_0_03L(%rip), %rax
        fmulp     %st, %st(1)
        fldt      48(%rsp)
        fmulp     %st, %st(2)
        fsub      %st, %st(1)
        fld       %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fldt      (%rax)
        fmulp     %st, %st(1)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B2.12
        jp        ..B2.12
..B2.11:
        fstp      %st(1)
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fsqrt     
        fmulp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.12:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
        call      __ltoq@PLT
..B2.20:
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
        movaps    (%rsp), %xmm0
..___tag_value___j1l.77:
        call      __j1q@PLT
..___tag_value___j1l.78:
..B2.13:
        movaps    %xmm0, (%rsp)
        lea       (%rsp), %rdi
        call      __qtol@PLT
..B2.21:
        fstpl     8(%rsp)
        movsd     8(%rsp), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
..B2.14:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__j1l,@function
	.size	__j1l,.-__j1l
	.data
# -- End  __j1l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 2
_S:
	.word	64993
	.word	47321
	.word	43380
	.word	39400
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	10427
	.word	24946
	.word	31449
	.word	45220
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	20142
	.word	26358
	.word	43878
	.word	35445
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	45380
	.word	5013
	.word	8196
	.word	48298
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	36049
	.word	37774
	.word	2551
	.word	61157
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	44499
	.word	20687
	.word	35324
	.word	37009
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	37149
	.word	40892
	.word	28299
	.word	43441
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	59921
	.word	2742
	.word	56472
	.word	49873
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	35749
	.word	60152
	.word	42287
	.word	56306
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	36553
	.word	24054
	.word	44174
	.word	62739
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12707
	.word	13183
	.word	28829
	.word	34586
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	24309
	.word	45263
	.word	7124
	.word	37803
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_S,@object
	.size	_S,192
	.align 2
_P1:
	.word	26041
	.word	45178
	.word	34801
	.word	41495
	.word	49090
	.word	0
	.word	0
	.word	0
	.word	65370
	.word	9956
	.word	39004
	.word	61276
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	9622
	.word	32383
	.word	51277
	.word	45692
	.word	16332
	.word	0
	.word	0
	.word	0
	.word	53955
	.word	64596
	.word	5222
	.word	35592
	.word	49100
	.word	0
	.word	0
	.word	0
	.word	34986
	.word	10535
	.word	24469
	.word	42796
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	2569
	.word	3934
	.word	58827
	.word	40937
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	16535
	.word	14264
	.word	30755
	.word	37884
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	39497
	.word	52694
	.word	37205
	.word	63078
	.word	49104
	.word	0
	.word	0
	.word	0
	.word	50010
	.word	60332
	.word	8210
	.word	49710
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	10498
	.word	63852
	.word	64151
	.word	35274
	.word	49105
	.word	0
	.word	0
	.word	0
	.word	55591
	.word	43795
	.word	24807
	.word	46603
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	15642
	.word	32907
	.word	52420
	.word	55914
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	53151
	.word	24867
	.word	14563
	.word	58254
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	37497
	.word	10412
	.word	6022
	.word	55715
	.word	49100
	.word	0
	.word	0
	.word	0
	.word	14397
	.word	43702
	.word	43690
	.word	43690
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	57427
	.word	55015
	.word	8445
	.word	53508
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	64958
	.word	65535
	.word	65535
	.word	65535
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	64696
	.word	39566
	.word	31598
	.word	54025
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,304
	.align 2
_Z:
	.word	17728
	.word	31608
	.word	43949
	.word	62778
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	32551
	.word	41874
	.word	44957
	.word	57471
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	18949
	.word	61409
	.word	34437
	.word	41670
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	4396
	.word	34605
	.word	55192
	.word	54573
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	9307
	.word	10310
	.word	55728
	.word	33731
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	7613
	.word	2917
	.word	18234
	.word	40173
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	24292
	.word	42693
	.word	42782
	.word	46612
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12570
	.word	29960
	.word	47214
	.word	53050
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	24415
	.word	36838
	.word	59299
	.word	59487
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	32322
	.word	22879
	.word	15219
	.word	32962
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	56404
	.word	12107
	.word	18542
	.word	36180
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_Z,@object
	.size	_Z,176
	.align 2
_P:
	.word	43464
	.word	52019
	.word	16370
	.word	39301
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	45212
	.word	20213
	.word	4367
	.word	36322
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	62182
	.word	43016
	.word	65056
	.word	63104
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	45004
	.word	1793
	.word	2432
	.word	46043
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	10697
	.word	46113
	.word	34013
	.word	35792
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	9065
	.word	41098
	.word	5532
	.word	44900
	.word	16343
	.word	0
	.word	0
	.word	0
	.word	26003
	.word	49876
	.word	4534
	.word	63025
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	42133
	.word	20669
	.word	62629
	.word	33244
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	10663
	.word	14825
	.word	55564
	.word	41652
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	21046
	.word	62601
	.word	18713
	.word	35715
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	28431
	.word	25084
	.word	11881
	.word	39361
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	40049
	.word	63841
	.word	60004
	.word	52172
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	992
	.word	25066
	.word	14104
	.word	49616
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	44948
	.word	43537
	.word	5801
	.word	46886
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	65381
	.word	44162
	.word	41036
	.word	37465
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	14125
	.word	39149
	.word	41569
	.word	43450
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	9425
	.word	2504
	.word	59629
	.word	56004
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	7469
	.word	36200
	.word	7418
	.word	55109
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	63186
	.word	26107
	.word	31425
	.word	52790
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	52143
	.word	25611
	.word	44594
	.word	35227
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	35717
	.word	29166
	.word	20585
	.word	41467
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	2832
	.word	5439
	.word	21266
	.word	33717
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	5378
	.word	24417
	.word	51356
	.word	63335
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	48412
	.word	30659
	.word	29190
	.word	42335
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	9054
	.word	19776
	.word	30431
	.word	35870
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	1836
	.word	17934
	.word	4966
	.word	40411
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	4836
	.word	40500
	.word	17629
	.word	63063
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	23076
	.word	41820
	.word	25860
	.word	58134
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	39832
	.word	57656
	.word	52920
	.word	41521
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	22342
	.word	1014
	.word	59094
	.word	59974
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	25371
	.word	16474
	.word	28775
	.word	38931
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	48127
	.word	54624
	.word	55306
	.word	41258
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	60537
	.word	50282
	.word	14991
	.word	48309
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	15691
	.word	57338
	.word	50654
	.word	33690
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	40468
	.word	19472
	.word	58347
	.word	35325
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	2985
	.word	7922
	.word	38039
	.word	52517
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	33464
	.word	30509
	.word	7663
	.word	49252
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	63790
	.word	6933
	.word	28208
	.word	44856
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	21342
	.word	14964
	.word	50588
	.word	39336
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	56226
	.word	19816
	.word	23757
	.word	47758
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	41064
	.word	34118
	.word	40187
	.word	42472
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	9004
	.word	37522
	.word	42571
	.word	60097
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	40290
	.word	31235
	.word	60424
	.word	63498
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	59943
	.word	36153
	.word	34778
	.word	37099
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	23867
	.word	38881
	.word	17085
	.word	35787
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	43845
	.word	22338
	.word	8742
	.word	34578
	.word	16343
	.word	0
	.word	0
	.word	0
	.word	21920
	.word	9728
	.word	29300
	.word	62498
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	43241
	.word	30911
	.word	50314
	.word	48209
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	5869
	.word	26658
	.word	63775
	.word	40733
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	50245
	.word	25343
	.word	31579
	.word	47683
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	51932
	.word	37189
	.word	37317
	.word	37600
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	35305
	.word	45319
	.word	11991
	.word	61881
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	43727
	.word	7380
	.word	41898
	.word	45538
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	55996
	.word	31666
	.word	20048
	.word	46502
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	20142
	.word	56563
	.word	4676
	.word	64142
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	42758
	.word	26948
	.word	20336
	.word	64653
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	18102
	.word	54076
	.word	11950
	.word	42374
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	55144
	.word	962
	.word	65430
	.word	51473
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	35076
	.word	30182
	.word	41634
	.word	65458
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	45599
	.word	55541
	.word	39885
	.word	63330
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	11397
	.word	64373
	.word	55097
	.word	42716
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	7278
	.word	1186
	.word	16759
	.word	52647
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	55821
	.word	1941
	.word	7835
	.word	62966
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	43692
	.word	51803
	.word	23893
	.word	63855
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	7306
	.word	740
	.word	42177
	.word	35243
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	20304
	.word	26112
	.word	14593
	.word	58152
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	44429
	.word	26104
	.word	43204
	.word	60999
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	43100
	.word	25366
	.word	26069
	.word	39382
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	53318
	.word	3475
	.word	36258
	.word	39277
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	61289
	.word	34432
	.word	35705
	.word	37567
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	47881
	.word	43207
	.word	60352
	.word	35669
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	3664
	.word	62231
	.word	63343
	.word	46625
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	41733
	.word	11611
	.word	33384
	.word	42291
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	22414
	.word	28668
	.word	61827
	.word	33289
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	39839
	.word	31083
	.word	29085
	.word	58078
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	45039
	.word	64406
	.word	35589
	.word	44277
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	21856
	.word	35851
	.word	11264
	.word	37516
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	38193
	.word	55017
	.word	53611
	.word	34369
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	50065
	.word	40254
	.word	39875
	.word	57241
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	21434
	.word	36257
	.word	28860
	.word	44273
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	24899
	.word	55058
	.word	27384
	.word	42408
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	23952
	.word	61552
	.word	63266
	.word	45785
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	20309
	.word	44505
	.word	41003
	.word	61821
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	9759
	.word	19768
	.word	52219
	.word	54624
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	3188
	.word	41981
	.word	52169
	.word	34347
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	7467
	.word	53297
	.word	24491
	.word	48747
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	26956
	.word	25629
	.word	26154
	.word	58912
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	2746
	.word	62708
	.word	29606
	.word	64476
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	23526
	.word	40632
	.word	17955
	.word	37512
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	58003
	.word	45489
	.word	17648
	.word	59859
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	12684
	.word	55359
	.word	8573
	.word	33616
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	29881
	.word	6055
	.word	26438
	.word	36076
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	37160
	.word	17925
	.word	32887
	.word	39269
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	52986
	.word	52867
	.word	51415
	.word	50108
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	29602
	.word	44157
	.word	62211
	.word	53157
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	14158
	.word	17332
	.word	5318
	.word	65232
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	63939
	.word	33203
	.word	7646
	.word	33955
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	29120
	.word	11480
	.word	39280
	.word	50030
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	42029
	.word	1146
	.word	14309
	.word	51502
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	13934
	.word	45324
	.word	64176
	.word	62054
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	407
	.word	40447
	.word	21497
	.word	41724
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	29047
	.word	47840
	.word	58616
	.word	39773
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	45982
	.word	37340
	.word	60343
	.word	60272
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	61708
	.word	5158
	.word	50916
	.word	46783
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	15241
	.word	33011
	.word	52635
	.word	33255
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	27136
	.word	17183
	.word	21500
	.word	41066
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	41313
	.word	44818
	.word	44575
	.word	56585
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	1149
	.word	8286
	.word	16932
	.word	53342
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	29145
	.word	29789
	.word	51640
	.word	35705
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	48645
	.word	17912
	.word	55357
	.word	48589
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	34105
	.word	33794
	.word	2760
	.word	63371
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	30589
	.word	55184
	.word	63547
	.word	57492
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	36766
	.word	25700
	.word	37915
	.word	36654
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	54115
	.word	65102
	.word	9808
	.word	39287
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	15191
	.word	22168
	.word	7058
	.word	49192
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	21631
	.word	11360
	.word	58072
	.word	50534
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	52725
	.word	34636
	.word	1139
	.word	62446
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	53409
	.word	43718
	.word	34635
	.word	38501
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	21083
	.word	16045
	.word	34961
	.word	47202
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	38780
	.word	63942
	.word	22964
	.word	35475
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	58574
	.word	20162
	.word	19964
	.word	40809
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	36576
	.word	45489
	.word	13327
	.word	34648
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	40869
	.word	46352
	.word	6352
	.word	58511
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	38766
	.word	2767
	.word	13055
	.word	40283
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	11002
	.word	40490
	.word	24288
	.word	64180
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	21330
	.word	2672
	.word	53484
	.word	34906
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	11637
	.word	63948
	.word	58709
	.word	54245
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	16451
	.word	23295
	.word	46139
	.word	44738
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	40013
	.word	16865
	.word	3459
	.word	33992
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	28055
	.word	62424
	.word	59084
	.word	40217
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	37015
	.word	7577
	.word	64481
	.word	59907
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	8219
	.word	9822
	.word	27279
	.word	47011
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	859
	.word	2558
	.word	12826
	.word	34425
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	34289
	.word	12934
	.word	665
	.word	63599
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	15142
	.word	54455
	.word	30227
	.word	45946
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	13599
	.word	50987
	.word	20426
	.word	40603
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	224
	.word	55674
	.word	54991
	.word	58096
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	12803
	.word	33902
	.word	43760
	.word	61618
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	3255
	.word	47153
	.word	28829
	.word	43826
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	57503
	.word	42370
	.word	48259
	.word	34977
	.word	16317
	.word	0
	.word	0
	.word	0
	.word	28464
	.word	31222
	.word	4873
	.word	39771
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	7076
	.word	4355
	.word	19850
	.word	60674
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	58695
	.word	18024
	.word	41082
	.word	56674
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	1100
	.word	13540
	.word	64577
	.word	34943
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	25873
	.word	27686
	.word	11323
	.word	61856
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	12188
	.word	35264
	.word	2250
	.word	59962
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	59387
	.word	26772
	.word	9493
	.word	52010
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	50361
	.word	26279
	.word	61391
	.word	38047
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	40446
	.word	22175
	.word	52931
	.word	64839
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	16288
	.word	22519
	.word	41833
	.word	33884
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	22795
	.word	59507
	.word	36930
	.word	56848
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	9090
	.word	34727
	.word	62738
	.word	39279
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	6548
	.word	61899
	.word	57259
	.word	65037
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	20477
	.word	44577
	.word	947
	.word	52780
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	9901
	.word	21929
	.word	34492
	.word	43241
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	52812
	.word	31464
	.word	11463
	.word	33533
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	16273
	.word	3725
	.word	28705
	.word	54534
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	28551
	.word	65013
	.word	38971
	.word	50753
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	55907
	.word	45101
	.word	33737
	.word	41084
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	43426
	.word	25495
	.word	20291
	.word	49461
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	1975
	.word	51775
	.word	24779
	.word	38683
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	29807
	.word	50650
	.word	32397
	.word	53456
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	5564
	.word	52273
	.word	7778
	.word	54851
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	7750
	.word	4868
	.word	38403
	.word	61120
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	15407
	.word	2698
	.word	46605
	.word	59622
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	137
	.word	48802
	.word	34590
	.word	52040
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	61391
	.word	58349
	.word	18865
	.word	49927
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	11732
	.word	37228
	.word	55154
	.word	32778
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	21770
	.word	7395
	.word	32621
	.word	61994
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	14091
	.word	47411
	.word	37523
	.word	57988
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	50358
	.word	51525
	.word	37270
	.word	54151
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	22903
	.word	22329
	.word	47941
	.word	33412
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	47512
	.word	65334
	.word	39843
	.word	61750
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	35590
	.word	32984
	.word	45077
	.word	44683
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	56839
	.word	21073
	.word	60125
	.word	40948
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	16525
	.word	17598
	.word	3587
	.word	56588
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	56933
	.word	2767
	.word	44815
	.word	51549
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	35966
	.word	59943
	.word	1117
	.word	42745
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	40172
	.word	7271
	.word	14707
	.word	38800
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	8476
	.word	4419
	.word	45780
	.word	62512
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	49137
	.word	16671
	.word	19871
	.word	37593
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	63934
	.word	59521
	.word	24158
	.word	47409
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	31258
	.word	33926
	.word	28141
	.word	53090
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	17384
	.word	36966
	.word	994
	.word	53889
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	55527
	.word	7910
	.word	53076
	.word	57518
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	18447
	.word	54969
	.word	49626
	.word	45611
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	13282
	.word	62682
	.word	5627
	.word	48010
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	7550
	.word	10759
	.word	6881
	.word	57138
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	65344
	.word	23540
	.word	39388
	.word	59430
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	2956
	.word	21512
	.word	45255
	.word	50285
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	29054
	.word	47408
	.word	36565
	.word	51766
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	19154
	.word	63758
	.word	53467
	.word	57697
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	22082
	.word	2480
	.word	53967
	.word	58888
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	52243
	.word	32283
	.word	44909
	.word	38447
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	7267
	.word	45206
	.word	61308
	.word	38976
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	10717
	.word	24270
	.word	52189
	.word	48572
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	40118
	.word	3643
	.word	65534
	.word	49002
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	44194
	.word	5661
	.word	50617
	.word	36641
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	26911
	.word	20494
	.word	63331
	.word	36858
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	20013
	.word	62028
	.word	17108
	.word	33468
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	9949
	.word	25572
	.word	4049
	.word	36529
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	39945
	.word	24442
	.word	45585
	.word	42322
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	59756
	.word	52868
	.word	48949
	.word	51417
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	35066
	.word	41141
	.word	44695
	.word	47881
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	42453
	.word	21162
	.word	44569
	.word	55557
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	30136
	.word	39390
	.word	37319
	.word	40338
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	60101
	.word	48639
	.word	6972
	.word	46254
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	56660
	.word	41165
	.word	63413
	.word	50313
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	13109
	.word	12767
	.word	29074
	.word	57119
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	2453
	.word	26502
	.word	15952
	.word	44109
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	44844
	.word	50072
	.word	22431
	.word	49646
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	33416
	.word	16999
	.word	40271
	.word	50445
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	26560
	.word	32264
	.word	54945
	.word	56373
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	58100
	.word	55846
	.word	35515
	.word	33528
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	44190
	.word	42353
	.word	4741
	.word	37259
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	47772
	.word	5308
	.word	50468
	.word	42281
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	53822
	.word	6159
	.word	32580
	.word	46797
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	5509
	.word	51700
	.word	62457
	.word	63728
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	52274
	.word	35926
	.word	43999
	.word	35182
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	33647
	.word	41025
	.word	45844
	.word	37383
	.word	16317
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,3520
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
_PP:
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
	.type	_PP,@object
	.size	_PP,160
	.align 2
_QP:
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
	.type	_QP,@object
	.size	_QP,160
	.align 2
_val_0_03L:
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_val_0_03L,@object
	.size	_val_0_03L,16
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
