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
	.file "nearbyint_gen.c"
	.text
..TXTST0:
# -- Begin  nearbyint
	.text
       .align    16,0x90
	.globl nearbyint
nearbyint:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_nearbyint.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        stmxcsr   8(%rsp)
..B1.3:
        movzwl    6(%rsp), %esi
        andl      $32752, %esi
        shrl      $4, %esi
        cmpl      $1075, %esi
        jge       ..B1.62
..B1.4:
        movl      8(%rsp), %eax
        andl      $24576, %eax
        cmpl      $8192, %eax
        je        ..B1.49
..B1.5:
        cmpl      $16384, %eax
        jne       ..B1.19
..B1.6:
        cmpl      $1023, %esi
        jl        ..B1.14
..B1.7:
        cmpl      $1043, %esi
        jle       ..B1.9
..B1.8:
        negl      %esi
        movl      $-1, %edi
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %edi
        movl      (%rsp), %eax
        andl      %eax, %edi
        movl      %edi, (%rsp)
        xorl      %eax, %edi
        jmp       ..B1.10
..B1.9:
        negl      %esi
        movl      $1048575, %eax
        addl      $19, %esi
        movl      4(%rsp), %edx
        movl      %edx, %edi
        movl      %esi, %ecx
        andl      $1048575, %edi
        shll      %cl, %eax
        andl      $-1048576, %edx
        andl      %edi, %eax
        orl       %eax, %edx
        movl      %edx, 4(%rsp)
        andl      $1048575, %edx
        xorl      %edx, %edi
        orl       (%rsp), %edi
        movl      $0, (%rsp)
..B1.10:
        testb     $-128, 7(%rsp)
        jne       ..B1.13
..B1.11:
        testl     %edi, %edi
        je        ..B1.13
..B1.12:
        lea       _ones(%rip), %rax
        movsd     (%rsp), %xmm0
        addsd     (%rax), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.13:
        movsd     (%rsp), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.14:
        testl     %esi, %esi
        jne       ..B1.18
..B1.15:
        testl     $1048575, 4(%rsp)
        jne       ..B1.18
..B1.16:
        cmpl      $0, (%rsp)
        je        ..B1.13
..B1.18:
        movb      7(%rsp), %al
        lea       _pone_nzero(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.19:
        cmpl      $24576, %eax
        jne       ..B1.26
..B1.20:
        cmpl      $1023, %esi
        jl        ..B1.25
..B1.21:
        cmpl      $1043, %esi
        jle       ..B1.23
..B1.22:
        negl      %esi
        movl      $-1, %eax
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %eax
        andl      (%rsp), %eax
        jmp       ..B1.24
..B1.23:
        negl      %esi
        movl      $1048575, %eax
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %eax
        movl      4(%rsp), %edx
        movl      %edx, %edi
        andl      %eax, %edx
        andl      $-1048576, %edi
        andl      $1048575, %edx
        xorl      %eax, %eax
        orl       %edx, %edi
        movl      %edi, 4(%rsp)
..B1.24:
        movl      %eax, (%rsp)
        movsd     (%rsp), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.25:
        movb      7(%rsp), %al
        lea       _zeros(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.26:
        cmpl      $1023, %esi
        jl        ..B1.42
..B1.27:
        movl      (%rsp), %eax
        cmpl      $1043, %esi
        jl        ..B1.29
..B1.28:
        movl      %eax, %edx
        lea       13(%rsi), %ecx
        shll      %cl, %edx
        xorl      %r9d, %r9d
        jmp       ..B1.30
..B1.29:
        movl      4(%rsp), %edx
        lea       13(%rsi), %ecx
        andl      $1048575, %edx
        movl      %eax, %r9d
        shll      %cl, %edx
..B1.30:
        cmpl      $1043, %esi
        jle       ..B1.32
..B1.31:
        negl      %esi
        movl      $-1, %edi
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %edi
        andl      %edi, %eax
        jmp       ..B1.33
..B1.32:
        negl      %esi
        movl      $1048575, %eax
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %eax
        movl      4(%rsp), %edi
        movl      %edi, %r8d
        andl      %eax, %edi
        andl      $-1048576, %r8d
        andl      $1048575, %edi
        xorl      %eax, %eax
        orl       %edi, %r8d
        movl      %r8d, 4(%rsp)
..B1.33:
        movl      %eax, (%rsp)
        testl     %edx, %edx
        jge       ..B1.13
..B1.34:
        andl      $2147483647, %edx
        orl       %r9d, %edx
        jne       ..B1.37
..B1.35:
        movzwl    6(%rsp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $1043, %ecx
        jle       ..B1.38
..B1.36:
        negl      %ecx
        movl      $1, %edx
        addl      $19, %ecx
        shll      %cl, %edx
        testl     %edx, %eax
        je        ..B1.13
..B1.37:
        movb      7(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movsd     (%rsp), %xmm0
        movzbl    %al, %ecx
        addsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.38:
        negl      %ecx
        movl      $1, %eax
        addl      $19, %ecx
        shll      %cl, %eax
        testl     %eax, 4(%rsp)
        je        ..B1.13
..B1.39:
        movb      7(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movsd     (%rsp), %xmm0
        movzbl    %al, %ecx
        addsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.42:
        cmpl      $1022, %esi
        jg        ..B1.48
..B1.43:
        jne       ..B1.25
..B1.44:
        movl      4(%rsp), %eax
        andl      $1048575, %eax
        jg        ..B1.48
..B1.45:
        jne       ..B1.25
..B1.46:
        cmpl      $0, (%rsp)
        jbe       ..B1.25
..B1.48:
        movb      7(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.49:
        cmpl      $1023, %esi
        jl        ..B1.57
..B1.50:
        cmpl      $1043, %esi
        jle       ..B1.52
..B1.51:
        negl      %esi
        movl      $-1, %eax
        addl      $19, %esi
        movl      %esi, %ecx
        shll      %cl, %eax
        movl      (%rsp), %edi
        andl      %edi, %eax
        movl      %eax, (%rsp)
        xorl      %eax, %edi
        jmp       ..B1.53
..B1.52:
        negl      %esi
        movl      $1048575, %eax
        addl      $19, %esi
        movl      4(%rsp), %edx
        movl      %edx, %edi
        movl      %esi, %ecx
        andl      $1048575, %edi
        shll      %cl, %eax
        andl      $-1048576, %edx
        andl      %edi, %eax
        orl       %eax, %edx
        movl      %edx, 4(%rsp)
        andl      $1048575, %edx
        xorl      %edx, %edi
        orl       (%rsp), %edi
        movl      $0, (%rsp)
..B1.53:
        testb     $-128, 7(%rsp)
        je        ..B1.13
..B1.54:
        testl     %edi, %edi
        je        ..B1.13
..B1.55:
        lea       8+_ones(%rip), %rax
        movsd     (%rsp), %xmm0
        addsd     (%rax), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.57:
        testl     %esi, %esi
        jne       ..B1.61
..B1.58:
        testl     $1048575, 4(%rsp)
        jne       ..B1.61
..B1.59:
        cmpl      $0, (%rsp)
        je        ..B1.13
..B1.61:
        movb      7(%rsp), %al
        lea       _pzero_none(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.62:
        lea       _ones(%rip), %rax
        movsd     (%rsp), %xmm0
        mulsd     (%rax), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nearbyint,@function
	.size	nearbyint,.-nearbyint
	.data
# -- End  nearbyint
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_pone_nzero:
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_pzero_none:
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.type	_pzero_none,@object
	.size	_pzero_none,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
