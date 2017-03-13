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
	.file "scalbl.c"
	.text
..TXTST0:
# -- Begin  scalbl
	.text
       .align    16,0x90
	.globl scalbl
scalbl:
# parameter 1: 48 + %rsp
# parameter 2: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_scalbl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %dl, %dl
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        movzwl    72(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %eax
        je        ..B1.100
..B1.4:
        cmpl      $32767, %ecx
        je        ..B1.69
..B1.5:
        cmpl      $16383, %ecx
        jl        ..B1.32
..B1.6:
        movzwl    34(%rsp), %edi
        movl      %edi, %esi
        andl      $768, %esi
        cmpl      $768, %esi
        je        ..B1.10
..B1.7:
        orl       $-64768, %edi
        movw      %di, 32(%rsp)
..B1.8:
        fldcw     32(%rsp)
..B1.9:
        movb      $1, %dl
..B1.10:
        cmpl      $16414, %ecx
        jge       ..B1.15
..B1.11:
        fldt      64(%rsp)
        lea       _TWO_63H(%rip), %rcx
        fldl      (%rcx)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fildl     16(%rsp)
        movl      16(%rsp), %edi
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.12
        je        ..B1.20
..B1.12:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        je        ..B1.14
..B1.13:
        fldcw     34(%rsp)
..B1.14:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.15:
        movb      73(%rsp), %sil
        lea       large_integers(%rip), %rdi
        andb      $-128, %sil
        shrb      $7, %sil
        movzbl    %sil, %r8d
        cmpl      $16446, %ecx
        movl      (%rdi,%r8,4), %edi
        jge       ..B1.20
..B1.16:
        addl      $2, %ecx
        movl      64(%rsp), %esi
        shll      %cl, %esi
        testl     %esi, %esi
        je        ..B1.20
..B1.17:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        je        ..B1.19
..B1.18:
        fldcw     34(%rsp)
..B1.19:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.20:
        testl     %eax, %eax
        jne       ..B1.26
..B1.21:
        cmpq      $0, 48(%rsp)
        jne       ..B1.25
..B1.22:
        testb     %dl, %dl
        je        ..B1.24
..B1.23:
        fldcw     34(%rsp)
..B1.24:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.25:
        fldt      48(%rsp)
        lea       _TWO_75(%rip), %rax
        fmull     (%rax)
        fstpt     48(%rsp)
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        addl      $-75, %eax
..B1.26:
        movl      $65536, %ecx
        cmpl      $65536, %edi
        cmovg     %ecx, %edi
        movl      $-65536, %esi
        cmpl      $-65536, %edi
        cmovge    %edi, %esi
        lea       (%rax,%rsi), %ecx
        testl     %ecx, %ecx
        jle       ..B1.49
..B1.27:
        cmpl      $32767, %ecx
        jge       ..B1.48
..B1.28:
        movzwl    56(%rsp), %eax
        andl      $32767, %ecx
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 56(%rsp)
        fldt      48(%rsp)
..B1.29:
        fstpt     16(%rsp)
        testb     %dl, %dl
        je        ..B1.31
..B1.30:
        fldcw     34(%rsp)
..B1.31:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.32:
        testl     %ecx, %ecx
        jne       ..B1.40
..B1.33:
        cmpq      $0, 64(%rsp)
        jne       ..B1.40
..B1.34:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.55
..B1.35:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.36:
        fldcw     32(%rsp)
..B1.37:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
..B1.38:
        fldcw     34(%rsp)
..B1.39:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.40:
        cmpl      $32767, %eax
        je        ..B1.56
..B1.41:
        movzwl    34(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.42:
        orl       $-64768, %ecx
        movw      %cx, 32(%rsp)
..B1.43:
        fldcw     32(%rsp)
..B1.44:
        movb      $1, %dl
..B1.45:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        je        ..B1.47
..B1.46:
        fldcw     34(%rsp)
..B1.47:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.48:
        movb      57(%rsp), %al
        lea       _large_value_80(%rip), %rsi
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %ecx
        shlq      $4, %rcx
        fldt      (%rsi,%rcx)
        fmulp     %st, %st(1)
        jmp       ..B1.29
..B1.49:
        cmpl      $-63, %ecx
        jge       ..B1.51
..B1.50:
        movb      57(%rsp), %al
        lea       _small_value_80(%rip), %rsi
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %ecx
        shlq      $4, %rcx
        fldt      (%rsi,%rcx)
        fmulp     %st, %st(1)
        jmp       ..B1.52
..B1.51:
        fldt      48(%rsp)
        lea       75(%rax,%rsi), %eax
        movzwl    56(%rsp), %ecx
        andl      $32767, %eax
        lea       8+_TWO_75(%rip), %rsi
        andl      $-32768, %ecx
        fstpt     16(%rsp)
        orl       %eax, %ecx
        movw      %cx, 24(%rsp)
        fldt      16(%rsp)
        fmull     (%rsi)
..B1.52:
        fstpt     16(%rsp)
        testb     %dl, %dl
        je        ..B1.54
..B1.53:
        fldcw     34(%rsp)
..B1.54:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.55:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
        jmp       ..B1.39
..B1.56:
        testl     %ecx, %ecx
        jle       ..B1.61
..B1.57:
        cmpl      $1, %ecx
        jne       ..B1.68
..B1.58:
        movl      68(%rsp), %eax
        cmpl      $-2147483648, %eax
        jb        ..B1.61
..B1.59:
        jne       ..B1.68
..B1.60:
        cmpl      $0, 64(%rsp)
        ja        ..B1.68
..B1.61:
        movzwl    34(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.65
..B1.62:
        orl       $-64768, %ecx
        movw      %cx, 32(%rsp)
..B1.63:
        fldcw     32(%rsp)
..B1.64:
        movb      $1, %dl
..B1.65:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        je        ..B1.67
..B1.66:
        fldcw     34(%rsp)
..B1.67:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.68:
        movb      57(%rsp), %al
        lea       _infs(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.69:
        movq      $0x8000000000000000, %rcx
        cmpq      64(%rsp), %rcx
        je        ..B1.77
..B1.70:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.76
..B1.71:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.72:
        fldcw     32(%rsp)
..B1.73:
        fldt      48(%rsp)
        fldt      64(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.74:
        fldcw     34(%rsp)
..B1.75:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.76:
        fldt      48(%rsp)
        fldt      64(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.75
..B1.77:
        movzwl    34(%rsp), %esi
        movl      %esi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.81
..B1.78:
        orl       $-64768, %esi
        movw      %si, 32(%rsp)
..B1.79:
        fldcw     32(%rsp)
..B1.80:
        movb      $1, %dl
..B1.81:
        testl     %eax, %eax
        jne       ..B1.89
..B1.82:
        cmpl      $0, 52(%rsp)
        jne       ..B1.84
..B1.83:
        cmpl      $0, 48(%rsp)
        je        ..B1.85
..B1.84:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rcx
        movq      %rcx, (%rsp)
..B1.85:
        cmpq      $0, 48(%rsp)
        jne       ..B1.90
..B1.86:
        testb     $-128, 73(%rsp)
        je        ..B1.88
..B1.87:
        fldt      48(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.94
..B1.88:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.94
..B1.89:
        cmpl      $32767, %eax
        je        ..B1.97
..B1.90:
        movb      57(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        testb     $-128, 73(%rsp)
        je        ..B1.92
..B1.91:
        lea       _zeros(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm0
        jmp       ..B1.93
..B1.92:
        lea       _infs(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm0
..B1.93:
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
..B1.94:
        testb     %dl, %dl
        je        ..B1.96
..B1.95:
        fldcw     34(%rsp)
..B1.96:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.97:
        testb     $-128, 73(%rsp)
        jne       ..B1.99
..B1.98:
        fldt      48(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.94
..B1.99:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.94
..B1.100:
        movq      $0x8000000000000000, %rsi
        cmpq      48(%rsp), %rsi
        jne       ..B1.70
..B1.101:
        cmpl      $32767, %ecx
        je        ..B1.69
        jmp       ..B1.32
        .align    16,0x90
	.cfi_endproc
	.type	scalbl,@function
	.size	scalbl,.-scalbl
	.data
# -- End  scalbl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
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
	.align 4
large_integers:
	.long	65536
	.long	-65536
	.type	large_integers,@object
	.size	large_integers,8
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
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
