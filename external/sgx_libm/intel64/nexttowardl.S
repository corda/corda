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
	.file "nexttowardl.c"
	.text
..TXTST0:
# -- Begin  nexttowardl
	.text
       .align    16,0x90
	.globl nexttowardl
nexttowardl:
# parameter 1: 48 + %rsp
# parameter 2: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_nexttowardl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    56(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        je        ..B1.53
..B1.4:
        movzwl    72(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.45
..B1.5:
        fldt      48(%rsp)
        fldt      64(%rsp)
        fucomi    %st(1), %st
        jp        ..B1.6
        je        ..B1.54
..B1.6:
        fstp      %st(0)
        testl     %esi, %esi
        jne       ..B1.9
..B1.7:
        cmpl      $0, 52(%rsp)
        jne       ..B1.9
..B1.8:
        cmpl      $0, 48(%rsp)
        je        ..B1.44
..B1.9:
        movb      73(%rsp), %al
        movb      57(%rsp), %dl
        andb      $-128, %al
        andb      $-128, %dl
        shrb      $7, %al
        shrb      $7, %dl
        fstpt     16(%rsp)
        cmpb      %dl, %al
        jne       ..B1.15
..B1.10:
        cmpl      %ecx, %esi
        jg        ..B1.15
..B1.11:
        jne       ..B1.29
..B1.12:
        movl      52(%rsp), %eax
        movl      68(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B1.15
..B1.13:
        jne       ..B1.29
..B1.14:
        movl      48(%rsp), %eax
        cmpl      64(%rsp), %eax
        jbe       ..B1.29
..B1.15:
        movzwl    24(%rsp), %edx
        movl      %edx, %edi
        movl      16(%rsp), %eax
        andl      $32767, %edi
        decl      %eax
        movl      %eax, 16(%rsp)
        cmpl      $-1, %eax
        jne       ..B1.34
..B1.16:
        movl      20(%rsp), %eax
        decl      %eax
        testl     %edi, %edi
        je        ..B1.22
..B1.17:
        cmpl      $2147483647, %eax
        jne       ..B1.21
..B1.18:
        decl      %edi
        andl      $-32768, %edx
        andl      $32767, %edi
        orl       %edi, %edx
        movw      %dx, 24(%rsp)
        testl     %edi, %edi
        je        ..B1.20
..B1.19:
        movl      %edx, %edi
        orl       $-2147483648, %eax
        movl      %eax, 20(%rsp)
        andl      $32767, %edi
        jmp       ..B1.34
..B1.20:
        movl      %edx, %edi
        movl      %eax, 20(%rsp)
        andl      $32767, %edi
        jmp       ..B1.34
..B1.21:
        movl      %eax, 20(%rsp)
        jmp       ..B1.35
..B1.22:
        movl      %eax, 20(%rsp)
..B1.23:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.24:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.25:
        fldcw     32(%rsp)
..B1.26:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
..B1.27:
        fldcw     34(%rsp)
..B1.28:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.29:
        movzwl    24(%rsp), %ecx
        movl      %ecx, %edi
        movl      16(%rsp), %eax
        andl      $32767, %edi
        incl      %eax
        movl      %eax, 16(%rsp)
        jne       ..B1.33
..B1.30:
        movl      $-2147483648, %eax
        xorl      %edx, %edx
        testl     %edi, %edi
        movl      20(%rsp), %esi
        cmove     %eax, %edx
        incl      %esi
        cmpl      %edx, %esi
        jne       ..B1.32
..B1.31:
        incl      %edi
        andl      $-32768, %ecx
        andl      $32767, %edi
        orl       $-2147483648, %esi
        orl       %edi, %ecx
        movl      %ecx, %edi
        movl      %esi, 20(%rsp)
        andl      $32767, %edi
        movw      %cx, 24(%rsp)
        jmp       ..B1.33
..B1.32:
        movl      %esi, 20(%rsp)
..B1.33:
        cmpl      $32767, %edi
        je        ..B1.37
..B1.34:
        testl     %edi, %edi
        je        ..B1.23
..B1.35:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.36:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.28
..B1.37:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.43
..B1.38:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.39:
        fldcw     32(%rsp)
..B1.40:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
..B1.41:
        fldcw     34(%rsp)
..B1.42:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.43:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.42
..B1.44:
        fstp      %st(0)
        movb      73(%rsp), %al
        lea       _smallest_value_80(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        shlq      $4, %rcx
        fldt      (%rdx,%rcx)
        movzwl    8(%rdx,%rcx), %edi
        fstpt     16(%rsp)
        andl      $32767, %edi
        jmp       ..B1.34
..B1.45:
        movq      $0x8000000000000000, %rax
        cmpq      64(%rsp), %rax
        je        ..B1.5
..B1.46:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.52
..B1.47:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.48:
        fldcw     32(%rsp)
..B1.49:
        fldt      48(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..B1.50:
        fldcw     34(%rsp)
..B1.51:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.52:
        fldt      48(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.51
..B1.53:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.46
        jmp       ..B1.4
..B1.54:
        fstp      %st(1)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nexttowardl,@function
	.size	nexttowardl,.-nexttowardl
	.data
# -- End  nexttowardl
	.section .rodata, "a"
	.align 4
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
_smallest_value_80:
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	1
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	0
	.word	0
	.type	_smallest_value_80,@object
	.size	_smallest_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
