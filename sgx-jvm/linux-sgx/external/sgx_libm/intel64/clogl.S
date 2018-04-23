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
	.file "clogl.c"
	.text
..TXTST0:
# -- Begin  clogl
	.text
       .align    16,0x90
	.globl clogl
clogl:
# parameter 1: 192 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_clogl.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        subq      $176, %rsp
	.cfi_def_cfa_offset 192
        xorb      %r13b, %r13b
        fldt      .L_2il0floatpacket.6(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 160(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fstpt     144(%rsp)
..B1.2:
        fnstcw    114(%rsp)
..B1.3:
        movzwl    114(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 112(%rsp)
..B1.5:
        fldcw     112(%rsp)
..B1.6:
        movb      $1, %r13b
..B1.7:
        movzwl    200(%rsp), %edx
        andl      $32767, %edx
        movzwl    216(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %edx
        jge       ..B1.47
..B1.8:
        cmpl      $32767, %eax
        jge       ..B1.88
..B1.9:
        testl     %edx, %edx
        jne       ..B1.15
..B1.10:
        cmpl      $0, 196(%rsp)
        jne       ..B1.15
..B1.11:
        cmpl      $0, 192(%rsp)
        jne       ..B1.15
..B1.12:
        testl     %eax, %eax
        jne       ..B1.15
..B1.13:
        cmpl      $0, 212(%rsp)
        jne       ..B1.15
..B1.14:
        cmpl      $0, 208(%rsp)
        je        ..B1.46
..B1.15:
        fldt      192(%rsp)
        fld       %st(0)
        fabs      
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      208(%rsp)
        fld       %st(0)
        fabs      
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movzwl    8(%rsp), %eax
        movzwl    24(%rsp), %edx
        andl      $32767, %eax
        andl      $32767, %edx
        cmpl      %edx, %eax
        jl        ..B1.20
..B1.16:
        jne       ..B1.21
..B1.17:
        movl      4(%rsp), %edx
        movl      20(%rsp), %ecx
        cmpl      %ecx, %edx
        jb        ..B1.20
..B1.18:
        jne       ..B1.21
..B1.19:
        movl      (%rsp), %edx
        cmpl      16(%rsp), %edx
        jae       ..B1.21
..B1.20:
        fstpt     (%rsp)
        fldt      (%rsp)
        movzwl    8(%rsp), %eax
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        andl      $32767, %eax
..B1.21:
        cmpl      $16382, %eax
        jg        ..B1.26
..B1.22:
        jne       ..B1.43
..B1.23:
        lea       16+_CONSTANTS(%rip), %rcx
        movl      4(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.26
..B1.24:
        jne       ..B1.43
..B1.25:
        movl      (%rcx), %edx
        cmpl      (%rsp), %edx
        ja        ..B1.43
..B1.26:
        cmpl      $16383, %eax
        jl        ..B1.31
..B1.27:
        jne       ..B1.43
..B1.28:
        lea       32+_CONSTANTS(%rip), %rcx
        movl      4(%rsp), %edx
        cmpl      4(%rcx), %edx
        jb        ..B1.31
..B1.29:
        jne       ..B1.43
..B1.30:
        movl      (%rsp), %edx
        cmpl      (%rcx), %edx
        ja        ..B1.43
..B1.31:
        movzwl    24(%rsp), %esi
        andl      $32767, %esi
        cmpl      $8256, %esi
        jg        ..B1.36
..B1.32:
        jne       ..B1.37
..B1.33:
        lea       80+_CONSTANTS(%rip), %rcx
        movl      20(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.36
..B1.34:
        jne       ..B1.37
..B1.35:
        movl      16(%rsp), %edx
        cmpl      (%rcx), %edx
        jbe       ..B1.37
..B1.36:
        fld       %st(2)
        lea       _CONSTANTS(%rip), %rdx
        fmul      %st(3), %st
        fld       %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.7(%rip)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fabs      
        fldt      (%rdx)
        fcomip    %st(1), %st
        ja        ..B1.38
        jmp       ..B1.89
..B1.37:
        fldt      .L_2il0floatpacket.7(%rip)
        fld       %st(3)
        fmul      %st(4), %st
        lea       _CONSTANTS(%rip), %rdx
        fsub      %st(1), %st
        fabs      
        fldt      (%rdx)
        fcomip    %st(1), %st
        jbe       ..B1.89
..B1.38:
        cmpl      $16183, %esi
        jl        ..B1.40
..B1.39:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.11(%rip)
        fld       %st(4)
        fxch      %st(6)
        fstpt     16(%rsp)
        lea       16+_Q1(%rip), %rax
        fxch      %st(3)
        fstpt     (%rsp)
        lea       _Q1(%rip), %rdx
        fldt      .L_2il0floatpacket.10(%rip)
        fmul      %st(4), %st
        fsub      %st, %st(5)
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(2)
        fxch      %st(5)
        fsub      %st(6), %st
        fxch      %st(4)
        fmul      %st, %st(6)
        fxch      %st(4)
        fmul      %st, %st(6)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      .L_2il0floatpacket.10(%rip)
        fmul      %st(4), %st
        fsub      %st, %st(6)
        fsubp     %st, %st(6)
        fld       %st(5)
        fmul      %st(6), %st
        fxch      %st(4)
        fsub      %st(6), %st
        fxch      %st(5)
        fmulp     %st, %st(6)
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(5)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(5)
        fmul      %st(0), %st
        fstpt     64(%rsp)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fsub      %st(2), %st
        fsubr     %st, %st(3)
        fld       %st(1)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(5), %st
        fld       %st(0)
        faddp     %st, %st(5)
        fld       %st(4)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      64(%rsp)
        faddp     %st, %st(1)
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(1)
        fld       %st(4)
        fsub      %st(3), %st
        fsubr     %st, %st(7)
        fsubr     %st(5), %st
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(2)
        fld       %st(3)
        fsub      %st(3), %st
        fldt      48(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(5), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        fld       %st(0)
        fsub      %st(4), %st
        fldt      32(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(3)
        fsub      %st(3), %st
        fldt      64(%rsp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(5), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     128(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fldt      32(%rsp)
        fstpt     (%rsp)
        fldt      48(%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.86
	.cfi_def_cfa_offset 192
..B1.40:
        cmpl      $16383, %eax
        je        ..B1.55
..B1.41:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
..B1.42:
        fldt      .L_2il0floatpacket.10(%rip)
        lea       16+_Q1(%rip), %rax
        fmul      %st(3), %st
        fld       %st(3)
        lea       _Q1(%rip), %rdx
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fsubp     %st, %st(2)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.11(%rip)
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fxch      %st(4)
        fmul      %st(0), %st
        fadd      %st, %st(4)
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        fld       %st(1)
        fadd      %st(4), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.9(%rip)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     128(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.86
	.cfi_def_cfa_offset 192
..B1.43:
        fstp      %st(2)
        fstp      %st(1)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        lea       64(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_clogl.11:
        call      __libm_hypot2l_k80@PLT
..___tag_value_clogl.12:
..B1.82:
        addq      $32, %rsp
	.cfi_def_cfa_offset 192
..B1.44:
        lea       48+_CONSTANTS(%rip), %rax
        lea       64+_CONSTANTS(%rip), %rdx
        fldt      (%rax)
        fmul      %st(1), %st
        fstpt     16(%rsp)
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fldt      64(%rsp)
        lea       96(%rsp), %rdi
        fstpt     (%rsp)
        fldt      -16(%rdi)
        fstpt     16(%rsp)
..___tag_value_clogl.15:
        call      __libm_logl_k80@PLT
..___tag_value_clogl.16:
..B1.83:
        addq      $32, %rsp
	.cfi_def_cfa_offset 192
..B1.45:
        fldt      .L_2il0floatpacket.9(%rip)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fmul      %st(1), %st
        fldt      80(%rsp)
        fmulp     %st, %st(2)
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     128(%rsp)
        fldt      208(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fstpt     (%rsp)
        fldt      224(%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.86
	.cfi_def_cfa_offset 192
..B1.46:
        fldt      208(%rsp)
        movsd     .L_2il0floatpacket.8(%rip), %xmm1
        pxor      %xmm0, %xmm0
        divsd     %xmm0, %xmm1
        movsd     %xmm1, (%rsp)
        fldl      (%rsp)
        fstpt     128(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fstpt     (%rsp)
        fldt      224(%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.86
	.cfi_def_cfa_offset 192
..B1.47:
        je        ..B1.72
..B1.48:
        cmpl      $32767, %eax
        je        ..B1.66
..B1.49:
        fldt      208(%rsp)
        lea       96+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     128(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fstpt     (%rsp)
        fldt      224(%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
..B1.86:
        addq      $32, %rsp
	.cfi_def_cfa_offset 192
        fstpt     144(%rsp)
..B1.50:
        testb     %r13b, %r13b
        je        ..B1.52
..B1.51:
        fldcw     114(%rsp)
..B1.52:
        movq      160(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.65
..B1.53:
        fldt      128(%rsp)
        fldt      144(%rsp)
        movq      160(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.54:
        addq      $176, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 13, -16
..B1.55:
        cmpl      $-2147483648, 4(%rsp)
        jne       ..B1.41
..B1.57:
        cmpl      $0, (%rsp)
        jne       ..B1.41
..B1.59:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(2)
        fldt      .L_2il0floatpacket.9(%rip)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        movzwl    104(%rsp), %eax
        testl     $32767, %eax
        jne       ..B1.63
..B1.60:
        cmpl      $0, 100(%rsp)
        jne       ..B1.62
..B1.61:
        cmpl      $0, 96(%rsp)
        je        ..B1.63
..B1.62:
        lea       112+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
..B1.63:
        fstpt     128(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 224
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.86
	.cfi_def_cfa_offset 192
..B1.64:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.65:
        call      __stack_chk_fail@PLT
..B1.66:
        cmpl      $-2147483648, 212(%rsp)
        jne       ..B1.68
..B1.67:
        cmpl      $0, 208(%rsp)
        je        ..B1.49
..B1.68:
        fldt      208(%rsp)
        cmpl      $32767, %edx
        fmul      %st(0), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        je        ..B1.70
..B1.69:
        fstpt     128(%rsp)
        jmp       ..B1.50
..B1.70:
        movq      $0x8000000000000000, %rax
        cmpq      192(%rsp), %rax
        jne       ..B1.69
..B1.71:
        fstp      %st(0)
        fldt      192(%rsp)
        fmul      %st(0), %st
        fstpt     128(%rsp)
        jmp       ..B1.50
..B1.72:
        cmpl      $-2147483648, 196(%rsp)
        jne       ..B1.74
..B1.73:
        cmpl      $0, 192(%rsp)
        je        ..B1.48
..B1.74:
        fldt      192(%rsp)
        cmpl      $32767, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     144(%rsp)
        je        ..B1.76
..B1.75:
        fldt      208(%rsp)
        fmulp     %st, %st(1)
        fstpt     128(%rsp)
        jmp       ..B1.50
..B1.76:
        movq      $0x8000000000000000, %rax
        cmpq      208(%rsp), %rax
        jne       ..B1.75
..B1.77:
        fstp      %st(0)
        fldt      208(%rsp)
        fmul      %st(0), %st
        fstpt     128(%rsp)
        jmp       ..B1.50
..B1.88:
        cmpl      $32767, %edx
        je        ..B1.72
        jmp       ..B1.48
..B1.89:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B1.43
        .align    16,0x90
	.cfi_endproc
	.type	clogl,@function
	.size	clogl,.-clogl
	.data
# -- End  clogl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	37342
	.word	47265
	.word	62214
	.word	46340
	.word	16382
	.word	0
	.word	0
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
	.word	53710
	.word	6135
	.word	45426
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	505
	.word	55756
	.word	58609
	.word	48341
	.word	16334
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,128
	.align 2
_Q1:
	.word	3134
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	49331
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
