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
	.file "sinl.c"
	.text
..TXTST0:
# -- Begin  sinl
	.text
       .align    16,0x90
	.globl sinl
sinl:
# parameter 1: 192 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_sinl.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        pushq     %rbp
	.cfi_def_cfa_offset 32
	.cfi_offset 6, -32
        subq      $160, %rsp
	.cfi_def_cfa_offset 192
        xorb      %r12b, %r12b
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 152(%rsp)
..B1.2:
        fnstcw    130(%rsp)
..B1.3:
        movzwl    200(%rsp), %edx
        andl      $32767, %edx
        movzwl    130(%rsp), %ecx
        cmpl      $32767, %edx
        je        ..B1.70
..B1.4:
        cmpl      $16378, %edx
        jge       ..B1.42
..B1.5:
        cmpl      $16372, %edx
        jge       ..B1.34
..B1.6:
        cmpl      $16364, %edx
        jge       ..B1.26
..B1.7:
        cmpl      $16308, %edx
        jge       ..B1.18
..B1.8:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.10:
        fldcw     128(%rsp)
..B1.11:
        movzwl    200(%rsp), %edx
        movb      $1, %r12b
        andl      $32767, %edx
..B1.12:
        testl     %edx, %edx
        jle       ..B1.59
..B1.13:
        fldt      192(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fmull     (%rdx)
        fstpt     16(%rsp)
..B1.14:
        testb     %r12b, %r12b
        je        ..B1.16
..B1.15:
        fldcw     130(%rsp)
..B1.16:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.17:
        fldt      16(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.18:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.22
..B1.19:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.20:
        fldcw     128(%rsp)
..B1.21:
        movb      $1, %r12b
..B1.22:
        fldt      192(%rsp)
        lea       _SP3(%rip), %rax
        testb     %r12b, %r12b
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.24
..B1.23:
        fldcw     130(%rsp)
..B1.24:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.25:
        fldt      16(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.26:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.30
..B1.27:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.28:
        fldcw     128(%rsp)
..B1.29:
        movb      $1, %r12b
..B1.30:
        fldt      192(%rsp)
        lea       16+_SP2(%rip), %rax
        fld       %st(0)
        lea       _SP2(%rip), %rdx
        fmul      %st(1), %st
        testb     %r12b, %r12b
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.32
..B1.31:
        fldcw     130(%rsp)
..B1.32:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.33:
        fldt      16(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.34:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.35:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.36:
        fldcw     128(%rsp)
..B1.37:
        movb      $1, %r12b
..B1.38:
        fldt      192(%rsp)
        lea       48+_SP1(%rip), %rax
        fld       %st(0)
        lea       32+_SP1(%rip), %rcx
        fmul      %st(1), %st
        lea       16+_SP1(%rip), %rdx
        fld       %st(0)
        lea       _SP1(%rip), %rbx
        fmul      %st(1), %st
        testb     %r12b, %r12b
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        fmulp     %st, %st(2)
        fldt      (%rbx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.40
..B1.39:
        fldcw     130(%rsp)
..B1.40:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.41:
        fldt      16(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.42:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.46
..B1.43:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.44:
        fldcw     128(%rsp)
..B1.45:
        movzwl    200(%rsp), %edx
        movb      $1, %r12b
        andl      $32767, %edx
..B1.46:
        cmpl      $16382, %edx
        jge       ..B1.48
..B1.47:
        fldt      192(%rsp)
        xorl      %ecx, %ecx
        movb      201(%rsp), %bpl
        lea       _TWO_52H(%rip), %rdx
        andb      $-128, %bpl
        shrb      $7, %bpl
        fldl      (%rdx)
        movzbl    %bpl, %eax
        lea       _ones(%rip), %rbp
        fldl      (%rbp,%rax,8)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fstpt     16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fxch      %st(2)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        jmp       ..B1.51
..B1.48:
        je        ..B1.67
..B1.49:
        fldt      192(%rsp)
        lea       _ones(%rip), %rbp
        movzbl    201(%rsp), %ebx
        xorl      %edi, %edi
        andl      $128, %ebx
        shrl      $7, %ebx
        fmull     (%rbp,%rbx,8)
        fstpt     192(%rsp)
        fldt      192(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 208
        lea       152(%rsp), %rsi
        fstpt     (%rsp)
..___tag_value_sinl.55:
        call      __libm_reduce_pi04l@PLT
..___tag_value_sinl.56:
..B1.85:
        movl      %eax, %ecx
        addq      $16, %rsp
	.cfi_def_cfa_offset 192
..B1.50:
        fldl      136(%rsp)
        lea       1(%rcx), %eax
        lea       _TWO_52H(%rip), %rdx
        fld       %st(0)
        movsd     136(%rsp), %xmm0
        shrl      $2, %eax
        xorl      %eax, %ebx
        mulsd     (%rdx), %xmm0
        andl      $1, %ebx
        fldl      (%rbp,%rbx,8)
        movsd     %xmm0, 32(%rsp)
        fstpt     16(%rsp)
        fldl      32(%rsp)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        fldl      144(%rsp)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
..B1.51:
        fld       %st(1)
        lea       _TWO_53H(%rip), %rax
        fmul      %st(3), %st
        fld       %st(3)
        fmul      %st(2), %st
        incl      %ecx
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        testb     $2, %cl
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      (%rax)
        fstpt     48(%rsp)
        je        ..B1.53
..B1.52:
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(2)
        lea       128+_CP(%rip), %rsi
        lea       112+_CP(%rip), %rax
        lea       96+_CP(%rip), %rdi
        lea       80+_CP(%rip), %rdx
        lea       64+_CP(%rip), %r8
        lea       48+_CP(%rip), %rcx
        lea       32+_CP(%rip), %r9
        lea       16+_CP(%rip), %rbx
        fldt      (%rax)
        lea       _CP(%rip), %r10
        lea       160+_CP(%rip), %r11
        lea       144+_CP(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r10)
        faddp     %st, %st(2)
        fldt      80(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fldt      64(%rsp)
        fldt      112(%rsp)
        fldt      48(%rsp)
        fldt      (%r11)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(5)
        fldt      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldl      (%rbp)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      16(%rsp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.54
..B1.53:
        lea       112+_SP(%rip), %rax
        lea       80+_SP(%rip), %rdx
        lea       48+_SP(%rip), %rcx
        lea       128+_SP(%rip), %rbp
        lea       16+_SP(%rip), %rbx
        lea       96+_SP(%rip), %rsi
        lea       64+_SP(%rip), %rdi
        lea       32+_SP(%rip), %r8
        fldt      (%rax)
        lea       _SP(%rip), %r9
        lea       160+_SP(%rip), %r10
        lea       144+_SP(%rip), %r11
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rbp)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r9)
        faddp     %st, %st(3)
        fldt      80(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        fldt      64(%rsp)
        fldt      (%r10)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%r11)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(5)
        fldt      48(%rsp)
        fmul      %st(2), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
..B1.54:
        testb     %r12b, %r12b
        je        ..B1.56
..B1.55:
        fldcw     130(%rsp)
..B1.56:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.57:
        fldt      32(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.58:
        call      __stack_chk_fail@PLT
..B1.59:
        cmpl      $0, 196(%rsp)
        jne       ..B1.61
..B1.60:
        cmpl      $0, 192(%rsp)
        je        ..B1.62
..B1.61:
        fldt      192(%rsp)
        lea       _TWO_75(%rip), %rbx
        movb      201(%rsp), %al
        lea       _small_value_80(%rip), %rcx
        andb      $-128, %al
        lea       8+_TWO_75(%rip), %rbp
        shrb      $7, %al
        fldl      (%rbx)
        fmul      %st(1), %st
        fsubp     %st, %st(1)
        fldt      (%rcx)
        fxch      %st(1)
        fmull     (%rbp)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fstpt     16(%rsp)
        fldt      (%rcx,%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.14
..B1.62:
        fldt      192(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.14
..B1.67:
        movl      196(%rsp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.47
..B1.68:
        jne       ..B1.49
..B1.69:
        cmpl      $560513589, 192(%rsp)
        jbe       ..B1.47
        jmp       ..B1.49
..B1.70:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.74
..B1.71:
        orl       $-64768, %ecx
        movw      %cx, 128(%rsp)
..B1.72:
        fldcw     128(%rsp)
..B1.73:
        movb      $1, %r12b
..B1.74:
        cmpl      $-2147483648, 196(%rsp)
        jne       ..B1.77
..B1.75:
        cmpl      $0, 192(%rsp)
        jne       ..B1.77
..B1.76:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.78
..B1.77:
        fldt      192(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
..B1.78:
        testb     %r12b, %r12b
        je        ..B1.80
..B1.79:
        fldcw     130(%rsp)
..B1.80:
        movq      152(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.58
..B1.81:
        fldt      16(%rsp)
        addq      $160, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sinl,@function
	.size	sinl,.-sinl
	.data
# -- End  sinl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 2
_SP3:
	.word	17476
	.word	43656
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_SP3,@object
	.size	_SP3,16
	.align 2
_SP2:
	.word	43210
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	1587
	.word	57422
	.word	34932
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP2,@object
	.size	_SP2,32
	.align 2
_SP1:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	12518
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	43088
	.word	7396
	.word	207
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	55827
	.word	48618
	.word	3602
	.word	47342
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_SP1,@object
	.size	_SP1,64
	.align 2
_CP:
	.word	51631
	.word	16464
	.word	16497
	.word	44012
	.word	16306
	.word	0
	.word	0
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	46557
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	28380
	.word	3328
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	23816
	.word	50302
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	49866
	.word	64527
	.word	51070
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	65056
	.word	3638
	.word	41889
	.word	51659
	.word	49114
	.word	0
	.word	0
	.word	0
	.word	13834
	.word	11317
	.word	33607
	.word	55101
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	40709
	.word	42374
	.word	64411
	.word	45709
	.word	49098
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43008
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP,@object
	.size	_CP,176
	.align 2
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	34948
	.word	34952
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	53243
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	30577
	.word	46649
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	11760
	.word	43464
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	47000
	.word	43232
	.word	12444
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	52983
	.word	47009
	.word	40440
	.word	55103
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	35433
	.word	14927
	.word	40941
	.word	51860
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	1340
	.word	16361
	.word	50747
	.word	38523
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP,@object
	.size	_SP,176
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
