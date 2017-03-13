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
	.file "tanl.c"
	.text
..TXTST0:
# -- Begin  tanl
	.text
       .align    16,0x90
	.globl tanl
tanl:
# parameter 1: 144 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_tanl.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        subq      $120, %rsp
	.cfi_def_cfa_offset 144
        xorb      %r13b, %r13b
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 104(%rsp)
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movzwl    152(%rsp), %eax
        andl      $32767, %eax
        movzwl    82(%rsp), %ecx
        cmpl      $16373, %eax
        jge       ..B1.41
..B1.4:
        cmpl      $16371, %eax
        jge       ..B1.33
..B1.5:
        cmpl      $16364, %eax
        jge       ..B1.25
..B1.6:
        cmpl      $16308, %eax
        jge       ..B1.17
..B1.7:
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.11
..B1.8:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.9:
        fldcw     80(%rsp)
..B1.10:
        movzwl    152(%rsp), %eax
        movb      $1, %r13b
        andl      $32767, %eax
..B1.11:
        testl     %eax, %eax
        jle       ..B1.83
..B1.12:
        fldt      144(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     (%rdx)
        fstpt     16(%rsp)
..B1.13:
        testb     %r13b, %r13b
        je        ..B1.15
..B1.14:
        fldcw     82(%rsp)
..B1.15:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.16:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.17:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.21
..B1.18:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.19:
        fldcw     80(%rsp)
..B1.20:
        movb      $1, %r13b
..B1.21:
        fldt      144(%rsp)
        lea       _P3(%rip), %rax
        fld       %st(0)
        testb     %r13b, %r13b
        fmul      %st(1), %st
        fldt      (%rax)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.23
..B1.22:
        fldcw     82(%rsp)
..B1.23:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.24:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.25:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.26:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.27:
        fldcw     80(%rsp)
..B1.28:
        movb      $1, %r13b
..B1.29:
        fldt      144(%rsp)
        lea       16+_P2(%rip), %rax
        fld       %st(0)
        lea       _P2(%rip), %rdx
        fmul      %st(1), %st
        testb     %r13b, %r13b
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.31
..B1.30:
        fldcw     82(%rsp)
..B1.31:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.32:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.33:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.37
..B1.34:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.35:
        fldcw     80(%rsp)
..B1.36:
        movb      $1, %r13b
..B1.37:
        fldt      144(%rsp)
        lea       48+_P1(%rip), %rax
        fld       %st(0)
        lea       32+_P1(%rip), %rcx
        fmul      %st(1), %st
        lea       16+_P1(%rip), %rdx
        fld       %st(0)
        lea       _P1(%rip), %rsi
        fmul      %st(1), %st
        testb     %r13b, %r13b
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmulp     %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.39
..B1.38:
        fldcw     82(%rsp)
..B1.39:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.40:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.41:
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.45
..B1.42:
        orl       $-64768, %ecx
        movw      %cx, 80(%rsp)
..B1.43:
        fldcw     80(%rsp)
..B1.44:
        movzwl    152(%rsp), %eax
        movb      $1, %r13b
        andl      $32767, %eax
..B1.45:
        movb      153(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %r12d
        cmpl      $16413, %eax
        jge       ..B1.52
..B1.46:
        fldt      144(%rsp)
        lea       _Pi4Inv(%rip), %rdx
        fld       %st(0)
        fabs      
        fldl      (%rdx)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        movzwl    72(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.48
..B1.47:
        fstp      %st(0)
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        lea       _KPi04(%rip), %rdx
        fldl      (%rax)
        fmul      %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(2)
        fsub      %st(2), %st
        fldt      (%rdx)
        fmul      %st(4), %st
        fstpt     64(%rsp)
        movl      $1, %edx
        fxch      %st(3)
        jmp       ..B1.63
..B1.48:
        fstp      %st(1)
        negl      %ecx
        lea       _TWO_32H(%rip), %rdi
        addl      $30, %ecx
        movl      68(%rsp), %edx
        shrl      %cl, %edx
        fldl      (%rdi)
        incl      %edx
        movl      %edx, %esi
        andl      $-2, %esi
        movl      %esi, 32(%rsp)
        cmpl      $16400, %eax
        fildl     32(%rsp)
        jge       ..B1.50
..B1.49:
        lea       _Pi04x3(%rip), %rax
        lea       8+_Pi04x3(%rip), %rcx
        lea       16+_Pi04x3(%rip), %rsi
        fldl      (%rax)
        fmul      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fldl      (%rcx)
        fmul      %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(0)
        fldl      (%rsi)
        fmulp     %st, %st(3)
        fsub      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        jmp       ..B1.51
..B1.50:
        lea       _Pi04x5(%rip), %rax
        lea       8+_Pi04x5(%rip), %rcx
        lea       16+_Pi04x5(%rip), %rsi
        lea       24+_Pi04x5(%rip), %rdi
        lea       32+_Pi04x5(%rip), %r8
        fldl      (%rax)
        fmul      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fldl      (%rcx)
        fmul      %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(0)
        fldl      (%rsi)
        fmul      %st(3), %st
        fsubr     %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(2)
        fldl      (%rdi)
        fmul      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      (%r8)
        fmulp     %st, %st(2)
        fld       %st(0)
        fsub      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
..B1.51:
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        lea       _ones(%rip), %rcx
        lea       _KPi04(%rip), %rsi
        fldl      (%rax)
        fmul      %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      (%rcx,%r12,8)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fadd      %st(3), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      (%rsi)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        jmp       ..B1.63
..B1.52:
        cmpl      $32767, %eax
        jne       ..B1.61
..B1.53:
        cmpl      $-2147483648, 148(%rsp)
        jne       ..B1.56
..B1.54:
        cmpl      $0, 144(%rsp)
        jne       ..B1.56
..B1.55:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.57
..B1.56:
        fldt      144(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
..B1.57:
        testb     %r13b, %r13b
        je        ..B1.59
..B1.58:
        fldcw     82(%rsp)
..B1.59:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.60:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.61:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 160
        xorl      %edi, %edi
        fldt      160(%rsp)
        lea       104(%rsp), %rsi
        fabs      
        fstpt     (%rsp)
..___tag_value_tanl.49:
        call      __libm_reduce_pi04l@PLT
..___tag_value_tanl.50:
..B1.97:
        movl      %eax, %edx
        addq      $16, %rsp
	.cfi_def_cfa_offset 144
..B1.62:
        fldl      88(%rsp)
        incl      %edx
        lea       _TWO_48H(%rip), %rcx
        fld       %st(0)
        movsd     88(%rsp), %xmm0
        lea       _ones(%rip), %rax
        fldl      (%rcx)
        fldl      (%rax,%r12,8)
        lea       _KPi04(%rip), %r12
        mulsd     (%rcx), %xmm0
        fldt      (%r12)
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fmul      %st(1), %st
        fldl      96(%rsp)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     64(%rsp)
        fxch      %st(2)
        fstpt     144(%rsp)
        fldt      144(%rsp)
..B1.63:
        movzwl    72(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $16383, %ecx
        jge       ..B1.73
..B1.64:
        testb     $2, %dl
        je        ..B1.69
..B1.65:
        lea       8+_ones(%rip), %rax
        fld       %st(2)
        fmul      %st(4), %st
        lea       80+_PG(%rip), %rdx
        fxch      %st(4)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        lea       64+_PG(%rip), %rcx
        fmul      %st(1), %st
        lea       48+_PG(%rip), %rsi
        lea       32+_PG(%rip), %rdi
        lea       16+_PG(%rip), %r8
        lea       _PG(%rip), %r9
        lea       _ones(%rip), %r10
        testb     %r13b, %r13b
        faddp     %st, %st(4)
        fxch      %st(2)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fldl      (%rax)
        fdiv      %st(3), %st
        fmul      %st, %st(2)
        fldt      (%rdx)
        fmul      %st(2), %st
        fldt      (%rcx)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fld       %st(3)
        fadd      %st(2), %st
        fsub      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(6)
        fldt      (%r9)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fld       %st(2)
        fsubr     %st(2), %st
        fxch      %st(6)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      32(%rsp)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        faddl     (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.102
..B1.66:
        fstpt     64(%rsp)
..B1.98:
        fldcw     82(%rsp)
        jmp       ..B1.67
..B1.102:
        fstp      %st(0)
..B1.67:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.68:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.69:
        fld       %st(0)
        lea       80+_PT(%rip), %rax
        fmul      %st(1), %st
        lea       64+_PT(%rip), %rdx
        fxch      %st(3)
        fstpt     48(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        lea       48+_PT(%rip), %rcx
        fldt      (%rax)
        lea       32+_PT(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_PT(%rip), %rdi
        fldt      (%rdx)
        lea       _PT(%rip), %r8
        fmul      %st(2), %st
        testb     %r13b, %r13b
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
        fmulp     %st, %st(2)
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fld       %st(2)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.103
..B1.70:
        fstpt     64(%rsp)
..B1.99:
        fldcw     82(%rsp)
        jmp       ..B1.71
..B1.103:
        fstp      %st(0)
..B1.71:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.72:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.73:
        fstp      %st(0)
        negl      %ecx
        pxor      %xmm2, %xmm2
        addl      $30, %ecx
        movl      68(%rsp), %eax
        shrl      %cl, %eax
        movl      %eax, %edi
        movzbl    153(%rsp), %esi
        negl      %edi
        testl     $128, %esi
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        cmovne    %edi, %eax
        testb     $2, %dl
        cvtsi2sd  %eax, %xmm2
        mulsd     %xmm2, %xmm0
        movsd     .L_2il0floatpacket.1(%rip), %xmm1
        mulsd     %xmm1, %xmm2
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        movsd     %xmm2, 32(%rsp)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldl      32(%rsp)
        fsubrp    %st, %st(4)
        fadd      %st(3), %st
        je        ..B1.78
..B1.74:
        fld       %st(2)
        lea       80+_PT(%rip), %rdx
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(2), %st
        lea       64+_PT(%rip), %rcx
        lea       48+_PT(%rip), %rsi
        lea       32+_PT(%rip), %rdi
        lea       16+_PT(%rip), %r8
        lea       _PT(%rip), %r9
        addl      %eax, %eax
        movslq    %eax, %rax
        lea       _TG(%rip), %r10
        lea       _TT(%rip), %r11
        lea       _ones(%rip), %r12
        testb     %r13b, %r13b
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(3)
        fstpt     32(%rsp)
        fxch      %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      (%rdx)
        fmul      %st(3), %st
        fldt      (%rcx)
        fmul      %st(4), %st
        fldt      (%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fldl      272(%r10,%rax,8)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(4)
        faddl     280(%r11,%rax,8)
        fxch      %st(3)
        faddl     272(%r11,%rax,8)
        fld       %st(0)
        fadd      %st(4), %st
        fxch      %st(3)
        fmull     280(%r10,%rax,8)
        lea       8+_ones(%rip), %r10
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(5), %st
        fldt      32(%rsp)
        fadd      %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fsubr     %st, %st(6)
        fxch      %st(6)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        faddp     %st, %st(3)
        fld       %st(0)
        fxch      %st(4)
        fdivrl    (%r12)
        fmul      %st, %st(5)
        fadd      %st, %st(4)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(1), %st
        faddp     %st, %st(6)
        fxch      %st(2)
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(7), %st
        fxch      %st(7)
        fmulp     %st, %st(5)
        faddl     (%r10)
        lea       _GG(%rip), %r10
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        fsubp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddl     272(%r10,%rax,8)
        fxch      %st(1)
        faddl     280(%r10,%rax,8)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        fldt      48(%rsp)
        je        ..B1.104
..B1.75:
        fstpt     64(%rsp)
..B1.100:
        fldcw     82(%rsp)
        jmp       ..B1.76
..B1.104:
        fstp      %st(0)
..B1.76:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.77:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.78:
        fld       %st(0)
        lea       80+_PT(%rip), %rdx
        fmul      %st(1), %st
        lea       64+_PT(%rip), %rcx
        fld       %st(0)
        lea       48+_PT(%rip), %rsi
        fmul      %st(1), %st
        lea       32+_PT(%rip), %rdi
        fxch      %st(4)
        fstpt     48(%rsp)
        lea       16+_PT(%rip), %r8
        fldt      (%rdx)
        lea       _PT(%rip), %r9
        fmul      %st(4), %st
        addl      %eax, %eax
        fldt      (%rcx)
        lea       _TG(%rip), %r10
        fmul      %st(5), %st
        lea       _GG(%rip), %r11
        fldt      (%rsi)
        lea       8+_ones(%rip), %r12
        movslq    %eax, %rax
        testb     %r13b, %r13b
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fld       %st(3)
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(4)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fld       %st(3)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(0)
        fldt      48(%rsp)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(4), %st
        fmul      %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(2), %st
        fldl      272(%r10,%rax,8)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(5), %st
        fxch      %st(4)
        faddl     280(%r11,%rax,8)
        fxch      %st(5)
        faddl     272(%r11,%rax,8)
        fxch      %st(2)
        fmull     280(%r10,%rax,8)
        lea       _ones(%rip), %r10
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(5), %st
        fdivrl    (%r12)
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(4), %st
        fld       %st(4)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(6), %st
        fxch      %st(6)
        fmulp     %st, %st(5)
        faddl     (%r10)
        lea       _TT(%rip), %r10
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      272(%r10,%rax,8)
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubr     %st(2), %st
        fxch      %st(3)
        faddl     280(%r10,%rax,8)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        je        ..B1.105
..B1.79:
        fstpt     64(%rsp)
..B1.101:
        fldcw     82(%rsp)
        jmp       ..B1.80
..B1.105:
        fstp      %st(0)
..B1.80:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.82
..B1.81:
        fldt      16(%rsp)
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.82:
        call      __stack_chk_fail@PLT
..B1.83:
        cmpl      $0, 148(%rsp)
        jne       ..B1.85
..B1.84:
        cmpl      $0, 144(%rsp)
        je        ..B1.86
..B1.85:
        fldt      144(%rsp)
        lea       _TWO_75(%rip), %rsi
        movb      153(%rsp), %al
        lea       _small_value_80(%rip), %rcx
        andb      $-128, %al
        lea       8+_TWO_75(%rip), %rdi
        shrb      $7, %al
        fldl      (%rsi)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldt      (%rcx)
        fxch      %st(1)
        fmull     (%rdi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fstpt     16(%rsp)
        fldt      (%rcx,%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.13
..B1.86:
        fldt      144(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.13
        .align    16,0x90
	.cfi_endproc
	.type	tanl,@function
	.size	tanl,.-tanl
	.data
# -- End  tanl
	.section .rodata, "a"
	.align 16
	.align 16
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 16
_P3:
	.word	13380
	.word	43827
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_P3,@object
	.size	_P3,16
	.align 16
_P2:
	.word	42806
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	23834
	.word	52331
	.word	35007
	.word	34952
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P2,@object
	.size	_P2,32
	.align 16
_P1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	31445
	.word	3491
	.word	53469
	.word	56589
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	16733
	.word	58542
	.word	44372
	.word	45863
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,64
	.align 16
_Pi4Inv:
	.long	1841940611
	.long	1072979760
	.type	_Pi4Inv,@object
	.size	_Pi4Inv,8
	.space 8, 0x00 	# pad
	.align 16
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.space 8, 0x00 	# pad
	.align 16
_KPi04:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16387
	.word	0
	.word	0
	.word	0
	.type	_KPi04,@object
	.size	_KPi04,16
	.align 16
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.space 8, 0x00 	# pad
	.align 16
_Pi04x3:
	.long	1413754112
	.long	1072243195
	.long	2563527040
	.long	1021855384
	.long	3417685868
	.long	3118450936
	.type	_Pi04x3,@object
	.size	_Pi04x3,24
	.space 8, 0x00 	# pad
	.align 16
_Pi04x5:
	.long	1413480448
	.long	1072243195
	.long	442499072
	.long	1036039265
	.long	771751936
	.long	999496074
	.long	622854144
	.long	963347354
	.long	1396597664
	.long	922906692
	.type	_Pi04x5,@object
	.size	_Pi04x5,40
	.space 8, 0x00 	# pad
	.align 16
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 16
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 16
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 16
_PG:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	46604
	.word	2912
	.word	24758
	.word	46603
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	64867
	.word	2218
	.word	21984
	.word	35507
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	37428
	.word	49066
	.word	48281
	.word	56811
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	28819
	.word	40692
	.word	30466
	.word	45908
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	58229
	.word	57025
	.word	27882
	.word	37211
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_PG,@object
	.size	_PG,96
	.align 16
_PT:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	35260
	.word	34952
	.word	34952
	.word	34952
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59431
	.word	3445
	.word	53469
	.word	56589
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	60805
	.word	19035
	.word	42054
	.word	45863
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	32963
	.word	17667
	.word	64505
	.word	37174
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	33271
	.word	14204
	.word	52730
	.word	60442
	.word	16374
	.word	0
	.word	0
	.word	0
	.type	_PT,@object
	.size	_PT,96
	.align 16
_TG:
	.long	2684354560
	.long	3221230545
	.long	1980244105
	.long	1046278452
	.long	0
	.long	3221225472
	.long	0
	.long	0
	.long	2684354560
	.long	3221230545
	.long	1980244105
	.long	1046278452
	.long	3758096384
	.long	3221246014
	.long	1513286675
	.long	1048381067
	.long	0
	.long	3221272655
	.long	4057975187
	.long	3193921290
	.long	2147483648
	.long	3221311866
	.long	2902494488
	.long	1046516509
	.long	3221225472
	.long	3221365863
	.long	1948335328
	.long	3196126810
	.long	2684354560
	.long	3221438007
	.long	3744091947
	.long	3192783219
	.long	2684354560
	.long	3221533379
	.long	3860423252
	.long	1047335024
	.long	2147483648
	.long	3221659806
	.long	862942416
	.long	1049198788
	.long	0
	.long	3221829776
	.long	543592927
	.long	3195468420
	.long	0
	.long	3222064283
	.long	2814267453
	.long	1048596082
	.long	536870912
	.long	3222337673
	.long	2980891112
	.long	3196362141
	.long	2684354560
	.long	3222595502
	.long	141458722
	.long	1049458206
	.long	3221225472
	.long	3223031588
	.long	2120823910
	.long	1049591613
	.long	3221225472
	.long	3223617753
	.long	663882580
	.long	3198186420
	.long	2147483648
	.long	3224659859
	.long	3051968734
	.long	3194791707
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1077176211
	.long	3051968734
	.long	1047308059
	.long	3221225472
	.long	1076134105
	.long	663882580
	.long	1050702772
	.long	3221225472
	.long	1075547940
	.long	2120823910
	.long	3197075261
	.long	2684354560
	.long	1075111854
	.long	141458722
	.long	3196941854
	.long	536870912
	.long	1074854025
	.long	2980891112
	.long	1048878493
	.long	0
	.long	1074580635
	.long	2814267453
	.long	3196079730
	.long	0
	.long	1074346128
	.long	543592927
	.long	1047984772
	.long	2147483648
	.long	1074176158
	.long	862942416
	.long	3196682436
	.long	2684354560
	.long	1074049731
	.long	3860423252
	.long	3194818672
	.long	2684354560
	.long	1073954359
	.long	3744091947
	.long	1045299571
	.long	3221225472
	.long	1073882215
	.long	1948335328
	.long	1048643162
	.long	2147483648
	.long	1073828218
	.long	2902494488
	.long	3194000157
	.long	0
	.long	1073789007
	.long	4057975187
	.long	1046437642
	.long	3758096384
	.long	1073762366
	.long	1513286675
	.long	3195864715
	.long	2684354560
	.long	1073746897
	.long	1980244105
	.long	3193762100
	.long	0
	.long	1073741824
	.long	0
	.long	0
	.long	2684354560
	.long	1073746897
	.long	1980244105
	.long	3193762100
	.type	_TG,@object
	.size	_TG,560
	.align 16
_TT:
	.long	1610612736
	.long	3220285245
	.long	1765132704
	.long	1046851815
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	3221225472
	.long	3219980491
	.long	3100042604
	.long	3192709428
	.long	1073741824
	.long	3219800832
	.long	2956254064
	.long	1046813655
	.long	0
	.long	3219635098
	.long	829070670
	.long	1046664815
	.long	0
	.long	3219481016
	.long	3358544266
	.long	3195362300
	.long	2147483648
	.long	3219336728
	.long	4185368031
	.long	3193047295
	.long	536870912
	.long	3219200695
	.long	1373988029
	.long	1045159724
	.long	0
	.long	3219014926
	.long	4104169752
	.long	3192540796
	.long	2147483648
	.long	3218768505
	.long	843197631
	.long	3193568495
	.long	0
	.long	3218531914
	.long	1839318447
	.long	1044402419
	.long	1073741824
	.long	3218303496
	.long	1215676724
	.long	1044727608
	.long	1610612736
	.long	3218081786
	.long	2922073518
	.long	3193275942
	.long	0
	.long	3217651190
	.long	2818065035
	.long	1045408462
	.long	2147483648
	.long	3217226924
	.long	2142552361
	.long	1043903475
	.long	2147483648
	.long	3216586427
	.long	1153689642
	.long	3190339163
	.long	2147483648
	.long	3215533863
	.long	3288774277
	.long	3189012002
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1068050215
	.long	3288774277
	.long	1041528354
	.long	2147483648
	.long	1069102779
	.long	1153689642
	.long	1042855515
	.long	2147483648
	.long	1069743276
	.long	2142552361
	.long	3191387123
	.long	0
	.long	1070167542
	.long	2818065035
	.long	3192892110
	.long	1610612736
	.long	1070598138
	.long	2922073518
	.long	1045792294
	.long	1073741824
	.long	1070819848
	.long	1215676724
	.long	3192211256
	.long	0
	.long	1071048266
	.long	1839318447
	.long	3191886067
	.long	2147483648
	.long	1071284857
	.long	843197631
	.long	1046084847
	.long	0
	.long	1071531278
	.long	4104169752
	.long	1045057148
	.long	536870912
	.long	1071717047
	.long	1373988029
	.long	3192643372
	.long	2147483648
	.long	1071853080
	.long	4185368031
	.long	1045563647
	.long	0
	.long	1071997368
	.long	3358544266
	.long	1047878652
	.long	0
	.long	1072151450
	.long	829070670
	.long	3194148463
	.long	1073741824
	.long	1072317184
	.long	2956254064
	.long	3194297303
	.long	3221225472
	.long	1072496843
	.long	3100042604
	.long	1045225780
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1610612736
	.long	1072801597
	.long	1765132704
	.long	3194335463
	.type	_TT,@object
	.size	_TT,560
	.align 16
_GG:
	.long	3221225472
	.long	1072496843
	.long	3100042604
	.long	1045225780
	.long	0
	.long	1072693248
	.long	2680413623
	.long	2803148914
	.long	1610612736
	.long	1072801597
	.long	1765132704
	.long	3194335463
	.long	2147483648
	.long	1072922365
	.long	4224648010
	.long	1047203520
	.long	0
	.long	1073058513
	.long	1429029132
	.long	1047610106
	.long	0
	.long	1073213977
	.long	2662307862
	.long	3195905419
	.long	2147483648
	.long	1073394115
	.long	2332932060
	.long	3196140229
	.long	3221225472
	.long	1073606419
	.long	4089189126
	.long	3194062807
	.long	3758096384
	.long	1073801761
	.long	295749197
	.long	3195130376
	.long	1073741824
	.long	1073958991
	.long	862942416
	.long	3195633860
	.long	0
	.long	1074158535
	.long	1551453531
	.long	3197746903
	.long	0
	.long	1074421594
	.long	590804010
	.long	3196516863
	.long	0
	.long	1074786323
	.long	1872593098
	.long	1046983523
	.long	3758096384
	.long	1075059710
	.long	2286165020
	.long	1046614587
	.long	2147483648
	.long	1075509055
	.long	2023416727
	.long	3198791271
	.long	1610612736
	.long	1076121196
	.long	3469402705
	.long	3196752904
	.long	0
	.long	1077172992
	.long	2869016295
	.long	3199393764
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3224656640
	.long	2869016295
	.long	1051910116
	.long	1610612736
	.long	3223604844
	.long	3469402705
	.long	1049269256
	.long	2147483648
	.long	3222992703
	.long	2023416727
	.long	1051307623
	.long	3758096384
	.long	3222543358
	.long	2286165020
	.long	3194098235
	.long	0
	.long	3222269971
	.long	1872593098
	.long	3194467171
	.long	0
	.long	3221905242
	.long	590804010
	.long	1049033215
	.long	0
	.long	3221642183
	.long	1551453531
	.long	1050263255
	.long	1073741824
	.long	3221442639
	.long	862942416
	.long	1048150212
	.long	3758096384
	.long	3221285409
	.long	295749197
	.long	1047646728
	.long	3221225472
	.long	3221090067
	.long	4089189126
	.long	1046579159
	.long	2147483648
	.long	3220877763
	.long	2332932060
	.long	1048656581
	.long	0
	.long	3220697625
	.long	2662307862
	.long	1048421771
	.long	0
	.long	3220542161
	.long	1429029132
	.long	3195093754
	.long	2147483648
	.long	3220406013
	.long	4224648010
	.long	3194687168
	.long	1610612736
	.long	3220285245
	.long	1765132704
	.long	1046851815
	.long	0
	.long	3220176896
	.long	2680413623
	.long	655665266
	.long	3221225472
	.long	3219980491
	.long	3100042604
	.long	3192709428
	.type	_GG,@object
	.size	_GG,560
	.align 16
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
	.align 8
.L_2il0floatpacket.0:
	.long	0x54400000,0x3fa921fb
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x1a626331,0x3d80b461
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
