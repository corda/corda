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
	.file "atan2l.c"
	.text
..TXTST0:
# -- Begin  atan2l
	.text
       .align    16,0x90
	.globl atan2l
atan2l:
# parameter 1: 80 + %rsp
# parameter 2: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_atan2l.1:
..L2:

        subq      $72, %rsp
	.cfi_def_cfa_offset 80
        xorb      %r8b, %r8b
..B1.2:
        fnstcw    66(%rsp)
..B1.3:
        movb      89(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movb      105(%rsp), %dil
        movzbl    %al, %esi
        andb      $-128, %dil
        movzwl    88(%rsp), %eax
        andl      $32767, %eax
        movzwl    104(%rsp), %ecx
        shrb      $7, %dil
        andl      $32767, %ecx
        cmpl      $32767, %eax
        je        ..B1.80
..B1.4:
        cmpl      $32767, %ecx
        je        ..B1.80
..B1.5:
        testl     %eax, %eax
        jne       ..B1.7
..B1.6:
        cmpq      $0, 80(%rsp)
        je        ..B1.9
..B1.7:
        testl     %ecx, %ecx
        jne       ..B1.26
..B1.8:
        cmpq      $0, 96(%rsp)
        jne       ..B1.26
..B1.9:
        movzwl    66(%rsp), %r9d
        movl      %r9d, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.13
..B1.10:
        orl       $-64768, %r9d
        movw      %r9w, 64(%rsp)
..B1.11:
        fldcw     64(%rsp)
..B1.12:
        movb      $1, %r8b
..B1.13:
        testl     %eax, %eax
        jne       ..B1.22
..B1.14:
        cmpq      $0, 80(%rsp)
        jne       ..B1.21
..B1.15:
        testl     %ecx, %ecx
        jne       ..B1.18
..B1.16:
        cmpq      $0, 96(%rsp)
        je        ..B1.77
..B1.17:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 56(%rsp)
..B1.18:
        testb     %dil, %dil
        je        ..B1.20
..B1.19:
        shlq      $4, %rsi
        lea       _pi_00l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.23
..B1.20:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     (%rsp)
        jmp       ..B1.23
..B1.21:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 56(%rsp)
..B1.22:
        shlq      $4, %rsi
        lea       _pi_02l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
..B1.23:
        testb     %r8b, %r8b
        je        ..B1.25
..B1.24:
        fldcw     66(%rsp)
..B1.25:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.26:
        movzwl    66(%rsp), %r9d
        movl      %r9d, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.30
..B1.27:
        orl       $-64768, %r9d
        movw      %r9w, 64(%rsp)
..B1.28:
        fldcw     64(%rsp)
..B1.29:
        movb      $1, %r8b
..B1.30:
        cmpl      $8191, %ecx
        jg        ..B1.34
..B1.31:
        cmpl      $8191, %eax
        jg        ..B1.33
..B1.32:
        fldt      96(%rsp)
        lea       _TWO_8192(%rip), %rax
        fldt      80(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        movzwl    104(%rsp), %ecx
        movzwl    88(%rsp), %eax
        andl      $32767, %ecx
        fstpt     (%rsp)
        andl      $32767, %eax
        jmp       ..B1.35
..B1.33:
        fldt      96(%rsp)
        fldt      80(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.38
..B1.34:
        fldt      96(%rsp)
        fldt      80(%rsp)
        fstpt     (%rsp)
..B1.35:
        cmpl      $24575, %ecx
        jl        ..B1.38
..B1.36:
        cmpl      $24575, %eax
        jl        ..B1.38
..B1.37:
        fldt      (%rsp)
        lea       16+_TWO_8192(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        movzwl    104(%rsp), %ecx
        movzwl    88(%rsp), %eax
        andl      $32767, %ecx
        fstpt     (%rsp)
        andl      $32767, %eax
..B1.38:
        fldt      (%rsp)
        lea       _ones(%rip), %rdx
        movzbl    %dil, %r9d
        fldl      (%rdx,%rsi,8)
        fmul      %st, %st(1)
        fldl      (%rdx,%r9,8)
        fmul      %st(3), %st
        fcomi     %st(2), %st
        jbe       ..B1.60
..B1.39:
        lea       12(%rax), %r9d
        cmpl      %r9d, %ecx
        jle       ..B1.54
..B1.40:
        addl      $75, %eax
        cmpl      %eax, %ecx
        jle       ..B1.47
..B1.41:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        testb     %dil, %dil
        je        ..B1.43
..B1.42:
        fstp      %st(0)
        shlq      $4, %rsi
        lea       _pi_00l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.44
..B1.43:
        fldt      (%rsp)
        fdivp     %st, %st(1)
        fstpt     (%rsp)
..B1.44:
        testb     %r8b, %r8b
        je        ..B1.46
..B1.45:
        fldcw     66(%rsp)
..B1.46:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.47:
        fstp      %st(3)
        testb     %dil, %dil
        je        ..B1.51
..B1.48:
        fxch      %st(2)
        fdivrp    %st, %st(1)
        lea       48+_P(%rip), %rax
        fld       %st(0)
        lea       32+_P(%rip), %rcx
        fmul      %st(1), %st
        lea       16+_P(%rip), %rdx
        fld       %st(0)
        lea       _P(%rip), %rsi
        fmul      %st(1), %st
        testb     %r8b, %r8b
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rdi
        fxch      %st(2)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rax)
        fmul      %st(3), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        fmulp     %st, %st(4)
        fldt      (%rsi)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        fsubrl    8200(%rdi)
        faddl     8192(%rdi)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.50
..B1.49:
        fldcw     66(%rsp)
..B1.50:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.51:
        fld       %st(1)
        lea       48+_P(%rip), %rax
        fdiv      %st(3), %st
        lea       16+_P(%rip), %rcx
        fld       %st(0)
        lea       32+_P(%rip), %rsi
        fmul      %st(1), %st
        lea       _P(%rip), %rdi
        fxch      %st(2)
        fstpl     48(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        lea       _TWO_32H(%rip), %r9
        fldt      (%rax)
        testb     %r8b, %r8b
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmulp     %st, %st(2)
        fld       %st(4)
        fldt      (%rdi)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
        fld       %st(2)
        fld       %st(3)
        fld       %st(3)
        fldl      (%r9)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fld       %st(5)
        fadd      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fld       %st(3)
        fxch      %st(2)
        fdivrl    (%rdx)
        fmul      %st, %st(3)
        lea       8+_ones(%rip), %rdx
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st, %st(4)
        fld       %st(5)
        fmul      %st(1), %st
        fxch      %st(2)
        fadd      %st, %st(6)
        fmulp     %st, %st(1)
        fxch      %st(4)
        faddl     (%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%rsp)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fmull     48(%rsp)
        fstpt     (%rsp)
        je        ..B1.53
..B1.52:
        fldt      16(%rsp)
        fstpt     32(%rsp)
..B1.110:
        fldcw     66(%rsp)
..B1.53:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.54:
        fstp      %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rax
        fmul      %st(2), %st
        lea       _TWO_48H(%rip), %rcx
        fdiv      %st(3), %st
        fld       %st(2)
        fxch      %st(2)
        fstpl     48(%rsp)
        fld       %st(2)
        fld       %st(4)
        fld       %st(5)
        fldl      (%rax)
        lea       48+_P(%rip), %rsi
        lea       16+_P(%rip), %r9
        lea       32+_P(%rip), %r10
        lea       _P(%rip), %r11
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubp     %st, %st(4)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(4)
        fldl      (%rcx)
        fmul      %st, %st(3)
        movslq    32(%rsp), %rax
        fxch      %st(3)
        fadd      %st, %st(5)
        shlq      $4, %rax
        fsubrp    %st, %st(5)
        fld       %st(4)
        testb     %dil, %dil
        fmul      %st(4), %st
        fxch      %st(5)
        fsubr     %st, %st(6)
        fxch      %st(2)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(6)
        fld       %st(5)
        fmul      %st(4), %st
        fadd      %st(7), %st
        fxch      %st(4)
        fmul      %st, %st(7)
        fxch      %st(7)
        fsubrp    %st, %st(6)
        fmul      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fdivrl    (%rdx)
        lea       8+_ones(%rip), %rdx
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(4)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(4)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(6)
        fadd      %st(7), %st
        fxch      %st(7)
        fmulp     %st, %st(1)
        fxch      %st(4)
        faddl     (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      48(%rsp)
        je        ..B1.56
..B1.55:
        negq      %rax
        fld       %st(2)
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rdx
        fldl      8192(%rdx,%rax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      8200(%rdx,%rax)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.57
..B1.56:
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rdx
        fld       %st(2)
        fldl      (%rax,%rdx)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     8(%rax,%rdx)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..B1.57:
        testb     %r8b, %r8b
        je        ..B1.59
..B1.58:
        fldt      (%rsp)
        fstpt     32(%rsp)
..B1.111:
        fldcw     66(%rsp)
..B1.59:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.60:
        fstp      %st(3)
        lea       12(%rcx), %r9d
        cmpl      %r9d, %eax
        jle       ..B1.71
..B1.61:
        addl      $75, %ecx
        cmpl      %ecx, %eax
        jle       ..B1.65
..B1.62:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        shlq      $4, %rsi
        lea       _pi_02l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        testb     %r8b, %r8b
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.64
..B1.63:
        fldcw     66(%rsp)
..B1.64:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.65:
        fxch      %st(1)
        fdivrp    %st, %st(2)
        lea       48+_P(%rip), %rax
        fld       %st(1)
        lea       32+_P(%rip), %rcx
        fmul      %st(2), %st
        lea       16+_P(%rip), %rdx
        fld       %st(0)
        lea       _P(%rip), %rsi
        fmul      %st(1), %st
        testb     %dil, %dil
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %r9
        fxch      %st(3)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rax)
        fmul      %st(4), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rcx)
        fmulp     %st, %st(5)
        fldt      (%rsi)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        faddp     %st, %st(2)
        fldl      4096(%r9)
        fstpt     (%rsp)
        fldl      4104(%r9)
        je        ..B1.67
..B1.66:
        faddp     %st, %st(2)
        fldt      (%rsp)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.68
..B1.67:
        fsubp     %st, %st(2)
        fldt      (%rsp)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.68:
        testb     %r8b, %r8b
        je        ..B1.70
..B1.69:
        fldcw     66(%rsp)
..B1.70:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.71:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rax
        fmul      %st(3), %st
        lea       _TWO_48H(%rip), %rcx
        fdiv      %st(2), %st
        fld       %st(3)
        fxch      %st(2)
        fstpl     48(%rsp)
        fld       %st(2)
        fld       %st(3)
        lea       48+_P(%rip), %rsi
        fldl      (%rax)
        lea       16+_P(%rip), %r9
        lea       32+_P(%rip), %r10
        lea       _P(%rip), %r11
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubp     %st, %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        fld       %st(5)
        fldl      (%rcx)
        fmul      %st, %st(5)
        movslq    32(%rsp), %rax
        fxch      %st(1)
        fadd      %st(5), %st
        shlq      $4, %rax
        fsubp     %st, %st(5)
        testb     %dil, %dil
        fxch      %st(4)
        fsubr     %st, %st(6)
        fld       %st(6)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(5)
        fxch      %st(2)
        fadd      %st(5), %st
        fld       %st(1)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(4)
        fmul      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(7)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fsubp     %st, %st(4)
        fld       %st(1)
        fadd      %st(1), %st
        fdivrl    (%rdx)
        lea       8+_ones(%rip), %rdx
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(7), %st
        fxch      %st(6)
        fadd      %st, %st(7)
        fmulp     %st, %st(5)
        faddl     (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      48(%rsp)
        je        ..B1.73
..B1.72:
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rdx
        fld       %st(2)
        fldl      4096(%rdx,%rax)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     4104(%rdx,%rax)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.74
..B1.73:
        negq      %rax
        fld       %st(2)
        movq      __libm_atanl_table_128@GOTPCREL(%rip), %rdx
        fldl      4096(%rdx,%rax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      4104(%rdx,%rax)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..B1.74:
        testb     %r8b, %r8b
        je        ..B1.76
..B1.75:
        fldt      (%rsp)
        fstpt     32(%rsp)
..B1.112:
        fldcw     66(%rsp)
..B1.76:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.77:
        testb     %dil, %dil
        je        ..B1.79
..B1.78:
        shlq      $4, %rsi
        lea       _pi_00l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.23
..B1.79:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     (%rsp)
        jmp       ..B1.23
..B1.80:
        movzwl    66(%rsp), %r9d
        movl      %r9d, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.84
..B1.81:
        orl       $-64768, %r9d
        movw      %r9w, 64(%rsp)
..B1.82:
        fldcw     64(%rsp)
..B1.83:
        movb      $1, %r8b
..B1.84:
        cmpl      $32767, %eax
        je        ..B1.107
..B1.85:
        cmpl      $32767, %ecx
        je        ..B1.105
..B1.86:
        testl     %eax, %eax
        jne       ..B1.89
..B1.87:
        cmpl      $0, 84(%rsp)
        jne       ..B1.92
..B1.88:
        cmpl      $0, 80(%rsp)
        jne       ..B1.92
..B1.89:
        testl     %ecx, %ecx
        jne       ..B1.93
..B1.90:
        cmpl      $0, 100(%rsp)
        jne       ..B1.92
..B1.91:
        cmpl      $0, 96(%rsp)
        je        ..B1.94
..B1.92:
        lea       _smallest_value_64(%rip), %rdx
        movq      (%rdx), %r9
        movq      %r9, 56(%rsp)
..B1.93:
        cmpl      $32767, %ecx
        je        ..B1.98
..B1.94:
        shlq      $4, %rsi
        lea       _pi_02l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
..B1.95:
        testb     %r8b, %r8b
        je        ..B1.97
..B1.96:
        fldcw     66(%rsp)
..B1.97:
        fldt      (%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.98:
        cmpl      $32767, %eax
        je        ..B1.102
..B1.99:
        testb     %dil, %dil
        je        ..B1.101
..B1.100:
        shlq      $4, %rsi
        lea       _pi_00l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.95
..B1.101:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     (%rsp)
        jmp       ..B1.95
..B1.102:
        shlq      $4, %rsi
        testb     %dil, %dil
        je        ..B1.104
..B1.103:
        lea       _pi_34l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.95
..B1.104:
        lea       _pi_04l(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        fldt      (%rsi,%rax)
        fldt      (%rsi,%rdx)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.95
..B1.105:
        movq      $0x8000000000000000, %rdx
        cmpq      96(%rsp), %rdx
        je        ..B1.86
..B1.106:
        fldt      96(%rsp)
        fldt      80(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.95
..B1.107:
        movq      $0x8000000000000000, %rdx
        cmpq      80(%rsp), %rdx
        jne       ..B1.106
        jmp       ..B1.85
        .align    16,0x90
	.cfi_endproc
	.type	atan2l,@function
	.size	atan2l,.-atan2l
	.data
# -- End  atan2l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 2
_pi_00l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49152
	.word	0
	.word	0
	.word	0
	.type	_pi_00l,@object
	.size	_pi_00l,32
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.word	0
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,32
	.align 2
_pi_02l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49151
	.word	0
	.word	0
	.word	0
	.type	_pi_02l,@object
	.size	_pi_02l,32
	.align 2
_TWO_8192:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24575
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.word	0
	.word	0
	.type	_TWO_8192,@object
	.size	_TWO_8192,32
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,64
	.align 2
_pi_34l:
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	49152
	.word	0
	.word	0
	.word	0
	.type	_pi_34l,@object
	.size	_pi_34l,32
	.align 2
_pi_04l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49150
	.word	0
	.word	0
	.word	0
	.type	_pi_04l,@object
	.size	_pi_04l,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
