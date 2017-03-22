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
	.file "cosl.c"
	.text
..TXTST0:
# -- Begin  cosl
	.text
       .align    16,0x90
	.globl cosl
cosl:
# parameter 1: 176 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cosl.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        subq      $152, %rsp
	.cfi_def_cfa_offset 176
        xorb      %bpl, %bpl
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 136(%rsp)
..B1.2:
        fnstcw    114(%rsp)
..B1.3:
        movzwl    184(%rsp), %edx
        andl      $32767, %edx
        movzwl    114(%rsp), %ecx
        cmpl      $32767, %edx
        je        ..B1.72
..B1.4:
        cmpl      $16378, %edx
        jge       ..B1.48
..B1.5:
        cmpl      $16372, %edx
        jge       ..B1.40
..B1.6:
        cmpl      $16364, %edx
        jge       ..B1.32
..B1.7:
        cmpl      $16308, %edx
        jge       ..B1.24
..B1.8:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.10:
        fldcw     112(%rsp)
..B1.11:
        movzwl    184(%rsp), %edx
        movb      $1, %bpl
        andl      $32767, %edx
..B1.12:
        testl     %edx, %edx
        jne       ..B1.18
..B1.13:
        cmpl      $0, 180(%rsp)
        jne       ..B1.15
..B1.14:
        cmpl      $0, 176(%rsp)
        je        ..B1.19
..B1.88:
        cmpl      $0, 180(%rsp)
..B1.15:
        jne       ..B1.17
..B1.16:
        cmpl      $0, 176(%rsp)
        je        ..B1.18
..B1.17:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, (%rsp)
..B1.18:
        lea       _small_value_80(%rip), %rax
        lea       _ones(%rip), %rdx
        fldt      (%rax)
        fsubrl    (%rdx)
        fstpt     8(%rsp)
        jmp       ..B1.20
..B1.19:
        lea       _ones(%rip), %rax
        fldl      (%rax)
        fstpt     8(%rsp)
..B1.20:
        testb     %bpl, %bpl
        je        ..B1.22
..B1.21:
        fldcw     114(%rsp)
..B1.22:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.23:
        fldt      8(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.24:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.28
..B1.25:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.26:
        fldcw     112(%rsp)
..B1.27:
        movb      $1, %bpl
..B1.28:
        fldt      176(%rsp)
        lea       _CP3(%rip), %rax
        lea       _ones(%rip), %rdx
        testb     %bpl, %bpl
        fldt      (%rax)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        faddl     (%rdx)
        fstpt     8(%rsp)
        je        ..B1.30
..B1.29:
        fldcw     114(%rsp)
..B1.30:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.31:
        fldt      8(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.32:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.33:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.34:
        fldcw     112(%rsp)
..B1.35:
        movb      $1, %bpl
..B1.36:
        fldt      176(%rsp)
        lea       16+_CP2(%rip), %rax
        fmul      %st(0), %st
        lea       _CP2(%rip), %rdx
        fldt      (%rax)
        lea       _ones(%rip), %rcx
        fmul      %st(1), %st
        testb     %bpl, %bpl
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddl     (%rcx)
        fstpt     8(%rsp)
        je        ..B1.38
..B1.37:
        fldcw     114(%rsp)
..B1.38:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.39:
        fldt      8(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.40:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.44
..B1.41:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.42:
        fldcw     112(%rsp)
..B1.43:
        movb      $1, %bpl
..B1.44:
        fldt      176(%rsp)
        lea       48+_CP1(%rip), %rax
        fmul      %st(0), %st
        lea       32+_CP1(%rip), %rcx
        fld       %st(0)
        lea       16+_CP1(%rip), %rdx
        fmul      %st(1), %st
        lea       _CP1(%rip), %rbx
        fldt      (%rax)
        lea       _ones(%rip), %rsi
        fmul      %st(1), %st
        testb     %bpl, %bpl
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
        faddl     (%rsi)
        fstpt     8(%rsp)
        je        ..B1.46
..B1.45:
        fldcw     114(%rsp)
..B1.46:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.47:
        fldt      8(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.48:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.52
..B1.49:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.50:
        fldcw     112(%rsp)
..B1.51:
        movzwl    184(%rsp), %edx
        movb      $1, %bpl
        andl      $32767, %edx
..B1.52:
        cmpl      $16382, %edx
        jge       ..B1.54
..B1.53:
        fldt      176(%rsp)
        xorl      %ecx, %ecx
        movb      185(%rsp), %bl
        xorl      %esi, %esi
        andb      $-128, %bl
        lea       _TWO_52H(%rip), %rdx
        shrb      $7, %bl
        fldl      (%rdx)
        movzbl    %bl, %eax
        lea       _ones(%rip), %rbx
        fxch      %st(1)
        fmull     (%rbx,%rax,8)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.57
..B1.54:
        je        ..B1.69
..B1.55:
        fldt      176(%rsp)
        xorl      %edi, %edi
        movb      185(%rsp), %bl
        andb      $-128, %bl
        shrb      $7, %bl
        movzbl    %bl, %eax
        lea       _ones(%rip), %rbx
        fmull     (%rbx,%rax,8)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        lea       136(%rsp), %rsi
        fstpt     (%rsp)
..___tag_value_cosl.41:
        call      __libm_reduce_pi04l@PLT
..___tag_value_cosl.42:
..B1.87:
        movl      %eax, %esi
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
..B1.56:
        fldl      120(%rsp)
        lea       3(%rsi), %ecx
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        movsd     120(%rsp), %xmm0
        shrl      $2, %ecx
        andl      $1, %ecx
        mulsd     (%rax), %xmm0
        movsd     %xmm0, 24(%rsp)
        fldl      24(%rsp)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fldl      128(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
..B1.57:
        fld       %st(1)
        lea       _TWO_53H(%rip), %rax
        fmul      %st(3), %st
        fld       %st(3)
        fmul      %st(2), %st
        incl      %esi
        faddp     %st, %st(1)
        fstpt     88(%rsp)
        fldt      88(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        testl     $2, %esi
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     40(%rsp)
        fldt      40(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     72(%rsp)
        fldt      72(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      (%rax)
        fstpt     56(%rsp)
        fldl      (%rbx,%rcx,8)
        fstpl     104(%rsp)
        je        ..B1.59
..B1.58:
        lea       112+_SP(%rip), %rax
        lea       80+_SP(%rip), %rdx
        lea       48+_SP(%rip), %rcx
        lea       128+_SP(%rip), %rsi
        fxch      %st(4)
        fstpt     8(%rsp)
        lea       16+_SP(%rip), %rbx
        lea       96+_SP(%rip), %rdi
        lea       64+_SP(%rip), %r8
        fldt      (%rax)
        lea       32+_SP(%rip), %r9
        lea       _SP(%rip), %r10
        lea       160+_SP(%rip), %r11
        lea       144+_SP(%rip), %rax
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
        fldt      40(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      72(%rsp)
        fldt      88(%rsp)
        fldt      (%r11)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fldt      (%rax)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(4)
        fldt      56(%rsp)
        fmul      %st(3), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      8(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      104(%rsp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     24(%rsp)
        jmp       ..B1.60
..B1.59:
        fstp      %st(3)
        fstp      %st(4)
        fstp      %st(2)
        lea       112+_CP(%rip), %rax
        lea       80+_CP(%rip), %rdx
        lea       128+_CP(%rip), %rdi
        lea       96+_CP(%rip), %r8
        lea       48+_CP(%rip), %rcx
        lea       64+_CP(%rip), %r9
        lea       16+_CP(%rip), %rsi
        lea       32+_CP(%rip), %r10
        fldt      (%rax)
        lea       _CP(%rip), %r11
        lea       160+_CP(%rip), %rax
        fmul      %st(3), %st
        fldt      (%rdx)
        lea       144+_CP(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      (%r11)
        faddp     %st, %st(4)
        fldt      40(%rsp)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      72(%rsp)
        fldt      88(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fldt      56(%rsp)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      (%rbx)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      104(%rsp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     24(%rsp)
..B1.60:
        testb     %bpl, %bpl
        je        ..B1.62
..B1.61:
        fldcw     114(%rsp)
..B1.62:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.63:
        fldt      24(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.64:
        call      __stack_chk_fail@PLT
..B1.69:
        movl      180(%rsp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.53
..B1.70:
        jne       ..B1.55
..B1.71:
        cmpl      $560513589, 176(%rsp)
        jbe       ..B1.53
        jmp       ..B1.55
..B1.72:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.76
..B1.73:
        orl       $-64768, %ecx
        movw      %cx, 112(%rsp)
..B1.74:
        fldcw     112(%rsp)
..B1.75:
        movb      $1, %bpl
..B1.76:
        cmpl      $-2147483648, 180(%rsp)
        jne       ..B1.79
..B1.77:
        cmpl      $0, 176(%rsp)
        jne       ..B1.79
..B1.78:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 24(%rsp)
        fldl      24(%rsp)
        fstpt     8(%rsp)
        jmp       ..B1.80
..B1.79:
        fldt      176(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     8(%rsp)
..B1.80:
        testb     %bpl, %bpl
        je        ..B1.82
..B1.81:
        fldcw     114(%rsp)
..B1.82:
        movq      136(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.64
..B1.83:
        fldt      8(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	cosl,@function
	.size	cosl,.-cosl
	.data
# -- End  cosl
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
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
_CP3:
	.word	0
	.word	65450
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.type	_CP3,@object
	.size	_CP3,16
	.align 2
_CP2:
	.word	63855
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	18238
	.word	17476
	.word	43656
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP2,@object
	.size	_CP2,32
	.align 2
_CP1:
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	47533
	.word	43689
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	14131
	.word	49466
	.word	24756
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	37142
	.word	18013
	.word	35855
	.word	53259
	.word	16367
	.word	0
	.word	0
	.word	0
	.type	_CP1,@object
	.size	_CP1,64
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
