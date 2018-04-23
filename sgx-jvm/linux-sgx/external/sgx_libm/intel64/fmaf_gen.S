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
	.file "fmaf_gen.c"
	.text
..TXTST0:
# -- Begin  fmaf
	.text
       .align    16,0x90
	.globl fmaf
fmaf:
# parameter 1: %xmm0
# parameter 2: %xmm1
# parameter 3: %xmm2
..B1.1:
	.cfi_startproc
..___tag_value_fmaf.1:
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
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $40, %rsp
	.cfi_def_cfa_offset 80
        movss     %xmm0, 8(%rsp)
        movss     %xmm1, 16(%rsp)
        movss     %xmm2, 24(%rsp)
..B1.2:
        stmxcsr   12(%rsp)
..B1.3:
        movl      16(%rsp), %r8d
        movl      %r8d, %ebp
        movl      24(%rsp), %r11d
        movl      %r11d, %r10d
        movl      8(%rsp), %esi
        movl      %esi, %ebx
        movzwl    12(%rsp), %edi
        andl      $2147483647, %ebp
        andl      $24576, %edi
        andl      $2147483647, %r10d
        andl      $2147483647, %ebx
        je        ..B1.86
..B1.4:
        cmpl      $2139095040, %ebx
        jae       ..B1.86
..B1.5:
        cmpl      $1065353216, %ebx
        je        ..B1.86
..B1.6:
        testl     %ebp, %ebp
        je        ..B1.86
..B1.7:
        cmpl      $2139095040, %ebp
        jae       ..B1.86
..B1.8:
        cmpl      $1065353216, %ebp
        je        ..B1.86
..B1.9:
        testl     %r10d, %r10d
        je        ..B1.86
..B1.10:
        cmpl      $2139095040, %r10d
        jae       ..B1.86
..B1.11:
        cmpl      $8388608, %ebx
        jae       ..B1.13
..B1.12:
        movl      %esi, %eax
        orl       $1065353216, %esi
        movl      %esi, 8(%rsp)
        lea       _ones(%rip), %rsi
        shrl      $31, %eax
        movss     8(%rsp), %xmm0
        subss     (%rsi,%rax,4), %xmm0
        movd      %xmm0, %esi
        movss     %xmm0, 8(%rsp)
        movl      %esi, %ebx
        andl      $2147483647, %ebx
        movl      %ebx, %ecx
        shrl      $23, %ecx
        addl      $-126, %ecx
        jmp       ..B1.14
..B1.13:
        movl      %ebx, %ecx
        shrl      $23, %ecx
..B1.14:
        cmpl      $8388608, %ebp
        jae       ..B1.16
..B1.15:
        movl      %r8d, %eax
        orl       $1065353216, %r8d
        movl      %r8d, 16(%rsp)
        lea       _ones(%rip), %r8
        shrl      $31, %eax
        movss     16(%rsp), %xmm0
        subss     (%r8,%rax,4), %xmm0
        movd      %xmm0, %r8d
        movss     %xmm0, 16(%rsp)
        movl      %r8d, %ebp
        andl      $2147483647, %ebp
        movl      %ebp, %edx
        shrl      $23, %edx
        addl      $-126, %edx
        jmp       ..B1.17
..B1.16:
        movl      %ebp, %edx
        shrl      $23, %edx
..B1.17:
        cmpl      $8388608, %r10d
        jae       ..B1.19
..B1.18:
        movl      %r11d, %eax
        orl       $1065353216, %r11d
        movl      %r11d, 24(%rsp)
        lea       _ones(%rip), %r11
        shrl      $31, %eax
        movss     24(%rsp), %xmm0
        subss     (%r11,%rax,4), %xmm0
        movd      %xmm0, %r11d
        movss     %xmm0, 24(%rsp)
        movl      %r11d, %r10d
        andl      $2147483647, %r10d
        movl      %r10d, %eax
        shrl      $23, %eax
        addl      $-126, %eax
        jmp       ..B1.20
..B1.19:
        movl      %r10d, %eax
        shrl      $23, %eax
..B1.20:
        lea       -127(%rcx,%rdx), %r9d
        andq      $8388607, %rbx
        andq      $8388607, %rbp
        orq       $8388608, %rbx
        orq       $8388608, %rbp
        andl      $8388607, %r10d
        imulq     %rbp, %rbx
        movq      %rbx, %rdx
        xorl      %r8d, %esi
        shrq      $32, %rdx
        orl       $8388608, %r10d
        andl      $-2147483648, %esi
        movl      %ebx, %ebp
        testl     $32768, %edx
        je        ..B1.22
..B1.21:
        shrq      $24, %rbx
        incl      %r9d
        shll      $8, %ebp
        jmp       ..B1.23
..B1.22:
        shrq      $23, %rbx
        shll      $9, %ebp
..B1.23:
        cmpl      %eax, %r9d
        jg        ..B1.26
..B1.24:
        jne       ..B1.27
..B1.25:
        cmpl      %r10d, %ebx
        jb        ..B1.27
..B1.26:
        movl      %r9d, %r8d
        xorl      %edx, %edx
        subl      %eax, %r8d
        movl      %r10d, %eax
        movl      %r11d, %r10d
        andl      $-2147483648, %r10d
        jmp       ..B1.28
..B1.27:
        movl      %eax, %r8d
        movl      %ebp, %edx
        subl      %r9d, %r8d
        movl      %eax, %r9d
        movl      %ebx, %eax
        movl      %r10d, %ebx
        movl      %esi, %r10d
        movl      %r11d, %esi
        xorl      %ebp, %ebp
        andl      $-2147483648, %esi
..B1.28:
        xorl      %r14d, %r14d
        testl     %r8d, %r8d
        je        ..B1.95
..B1.29:
        cmpl      $32, %r8d
        jge       ..B1.31
..B1.30:
        movl      %r8d, %r15d
        movl      %r8d, %ecx
        negl      %r15d
        movl      $-1, %r11d
        shrl      %cl, %r11d
        movl      %r15d, %ecx
        movl      %edx, %r14d
        notl      %r11d
        shll      %cl, %r14d
        movl      %r15d, %ecx
        movl      %eax, %r15d
        shll      %cl, %r15d
        movl      %r8d, %ecx
        shrl      %cl, %edx
        andl      %r11d, %r15d
        movl      %r8d, %ecx
        orl       %r15d, %edx
        shrl      %cl, %eax
        movl      $1, %r11d
        jmp       ..B1.34
..B1.31:
        cmpl      $64, %r8d
        jge       ..B1.33
..B1.32:
        movl      %r8d, %r14d
        movl      %r8d, %ecx
        negl      %r14d
        movl      $-1, %r11d
        shrl      %cl, %r11d
        movl      %r14d, %ecx
        movl      %eax, %r14d
        notl      %r11d
        shll      %cl, %r14d
        movl      %r8d, %ecx
        andl      %r11d, %r14d
        movl      $1, %r11d
        testl     %edx, %edx
        cmovne    %r11d, %edx
        orl       %edx, %r14d
        movl      %eax, %edx
        shrl      %cl, %edx
        xorl      %eax, %eax
        jmp       ..B1.34
..B1.33:
        movl      $1, %r11d
        orl       %eax, %edx
        cmovne    %r11d, %r14d
        xorl      %edx, %edx
        xorl      %eax, %eax
        jmp       ..B1.34
..B1.95:
        movl      $1, %r11d
..B1.34:
        cmpl      %r10d, %esi
        jne       ..B1.38
..B1.35:
        addl      %ebp, %edx
        addl      %eax, %ebx
        cmpl      %ebp, %edx
        movl      $0, %ebp
        setb      %bpl
        addl      %ebp, %ebx
        testl     $16777216, %ebx
        je        ..B1.37
..B1.36:
        orl       %r14d, %edx
        movl      %ebx, %eax
        movl      $0, %edx
        cmovne    %r11d, %edx
        incl      %r9d
        shll      $31, %eax
        shrl      $1, %ebx
        orl       %edx, %eax
        andl      $8388607, %ebx
        jmp       ..B1.54
..B1.37:
        testl     %r14d, %r14d
        movl      %edx, %eax
        cmovne    %r11d, %r14d
        andl      $8388607, %ebx
        orl       %r14d, %eax
        jmp       ..B1.54
..B1.38:
        xorl      %r8d, %r8d
        negl      %r14d
        cmovne    %r11d, %r8d
        negl      %edx
        addl      %ebp, %edx
        subl      %eax, %ebx
        subl      %r8d, %edx
        cmpl      %ebp, %edx
        movl      $0, %ebp
        seta      %bpl
        subl      %ebp, %ebx
        je        ..B1.40
..B1.39:
        movl      %ebx, %eax
        xorl      %ebp, %ebp
        shll      $8, %eax
        jmp       ..B1.44
..B1.40:
        testl     %edx, %edx
        je        ..B1.42
..B1.41:
        movl      %edx, %eax
        movl      $24, %ebp
        jmp       ..B1.44
..B1.42:
        testl     %r14d, %r14d
        je        ..B1.85
..B1.43:
        movl      %r14d, %eax
        movl      $56, %ebp
..B1.44:
        testl     $-2147483648, %eax
        jne       ..B1.48
..B1.46:
        addl      %eax, %eax
        incl      %ebp
        testl     $-2147483648, %eax
        je        ..B1.46
..B1.48:
        cmpl      $32, %ebp
        jge       ..B1.50
..B1.49:
        movl      %ebp, %eax
        movl      %ebp, %ecx
        negl      %eax
        movl      %edx, %r10d
        shll      %cl, %ebx
        movl      %eax, %ecx
        shrl      %cl, %r10d
        movl      %ebp, %ecx
        movl      %r11d, %r8d
        testl     %r14d, %r14d
        cmovne    %r11d, %r14d
        shll      %cl, %r8d
        movl      %ebp, %ecx
        decl      %r8d
        shll      %cl, %edx
        andl      %r8d, %r10d
        orl       %r10d, %ebx
        movl      %edx, %eax
        andl      $8388607, %ebx
        orl       %r14d, %eax
        jmp       ..B1.53
..B1.50:
        cmpl      $64, %ebp
        jge       ..B1.52
..B1.51:
        movl      %ebp, %eax
        movl      %ebp, %ecx
        negl      %eax
        movl      %r14d, %r8d
        shll      %cl, %edx
        movl      %eax, %ecx
        shrl      %cl, %r8d
        movl      %ebp, %ecx
        movl      %r11d, %ebx
        movl      %r14d, %eax
        shll      %cl, %ebx
        movl      %ebp, %ecx
        decl      %ebx
        andl      %ebx, %r8d
        orl       %r8d, %edx
        movl      %edx, %ebx
        shll      %cl, %eax
        andl      $8388607, %ebx
        jmp       ..B1.53
..B1.52:
        movl      %ebp, %ecx
        xorl      %eax, %eax
        shll      %cl, %r14d
        movl      %r14d, %ebx
        andl      $8388607, %ebx
..B1.53:
        subl      %ebp, %r9d
..B1.54:
        lea       -1(%r9), %edx
        cmpl      $254, %edx
        jb        ..B1.60
..B1.55:
        cmpl      $255, %r9d
        jge       ..B1.90
..B1.56:
        negl      %r9d
        incl      %r9d
        cmpl      $24, %r9d
        jg        ..B1.58
..B1.57:
        movl      %r9d, %edx
        movl      %r9d, %ecx
        negl      %edx
        movl      $-1, %ebp
        orl       $8388608, %ebx
        testl     %eax, %eax
        movl      %ebx, %r8d
        cmovne    %r11d, %eax
        shrl      %cl, %ebp
        movl      %edx, %ecx
        shll      %cl, %r8d
        notl      %ebp
        andl      %ebp, %r8d
        movl      %r9d, %ecx
        shrl      %cl, %ebx
        orl       %r8d, %eax
        jmp       ..B1.61
..B1.58:
        movl      %r11d, %eax
        xorl      %ebx, %ebx
        jmp       ..B1.62
..B1.60:
        shll      $23, %r9d
        orl       %r9d, %ebx
..B1.61:
        testl     %eax, %eax
        je        ..B1.84
..B1.62:
        movl      $1065353216, (%rsp)
        testl     %edi, %edi
        je        ..B1.87
..B1.63:
        cmpl      $24576, %edi
        je        ..B1.80
..B1.64:
        cmpl      $16384, %edi
        jne       ..B1.72
..B1.65:
        testl     %esi, %esi
        je        ..B1.67
..B1.66:
        cmpl      $8388608, %ebx
        jb        ..B1.70
        jmp       ..B1.84
..B1.67:
        cmpl      $8388607, %ebx
        jb        ..B1.70
..B1.68:
        jne       ..B1.71
..B1.69:
        cmpl      $-2147483648, %eax
        jae       ..B1.71
..B1.70:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        testl     %esi, %esi
        mulss     %xmm0, %xmm0
        movss     %xmm0, (%rsp)
        jne       ..B1.84
..B1.71:
        incl      %ebx
        cmpl      $2139095040, %ebx
        jae       ..B1.90
        jmp       ..B1.84
..B1.72:
        cmpl      $8192, %edi
        jne       ..B1.84
..B1.73:
        testl     %esi, %esi
        jne       ..B1.75
..B1.74:
        cmpl      $8388608, %ebx
        jb        ..B1.78
        jmp       ..B1.84
..B1.75:
        cmpl      $8388607, %ebx
        jb        ..B1.78
..B1.76:
        jne       ..B1.79
..B1.77:
        cmpl      $-2147483648, %eax
        jae       ..B1.79
..B1.78:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        testl     %esi, %esi
        mulss     %xmm0, %xmm0
        movss     %xmm0, (%rsp)
        je        ..B1.84
..B1.79:
        incl      %ebx
        cmpl      $2139095040, %ebx
        jae       ..B1.90
        jmp       ..B1.84
..B1.80:
        cmpl      $8388608, %ebx
        jb        ..B1.83
..B1.81:
        jne       ..B1.84
..B1.82:
        cmpl      $-2147483648, %eax
        jne       ..B1.84
..B1.83:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        mulss     %xmm0, %xmm0
        movss     %xmm0, (%rsp)
..B1.84:
        orl       %ebx, %esi
        movl      %esi, 4(%rsp)
        movss     4(%rsp), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.85:
        xorl      %edx, %edx
        cmpl      $8192, %edi
        lea       _zeros(%rip), %rcx
        sete      %dl
        movss     (%rcx,%rdx,4), %xmm0
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.86:
        movss     8(%rsp), %xmm0
        mulss     16(%rsp), %xmm0
        addss     24(%rsp), %xmm0
        movss     %xmm0, 4(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.87:
        testl     $-2147483648, %eax
        je        ..B1.80
..B1.88:
        movl      %ebx, %ecx
        movl      %eax, %edx
        andl      $1, %ecx
        andl      $2147483647, %edx
        orl       %edx, %ecx
        je        ..B1.80
..B1.89:
        incl      %ebx
        cmpl      $2139095040, %ebx
        jb        ..B1.80
..B1.90:
        lea       _large_value_32(%rip), %rax
        shrl      $31, %esi
        movss     (%rax,%rsi,4), %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, (%rsp)
        movss     %xmm0, 4(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
        .align    16,0x90
	.cfi_endproc
	.type	fmaf,@function
	.size	fmaf,.-fmaf
	.data
# -- End  fmaf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x71800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x0d800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x3f800000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
