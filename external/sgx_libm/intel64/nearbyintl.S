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
	.file "nearbyintl.c"
	.text
..TXTST0:
# -- Begin  nearbyintl
	.text
       .align    16,0x90
	.globl nearbyintl
nearbyintl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_nearbyintl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %dil, %dil
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    56(%rsp), %r8d
        andl      $32767, %r8d
        movzwl    26(%rsp), %r9d
        cmpl      $16446, %r8d
        jge       ..B1.95
..B1.4:
        movl      %r9d, %eax
        andl      $3072, %eax
        cmpl      $1024, %eax
        je        ..B1.77
..B1.5:
        cmpl      $2048, %eax
        jne       ..B1.24
..B1.6:
        cmpl      $16383, %r8d
        jl        ..B1.19
..B1.7:
        cmpl      $16415, %r8d
        jl        ..B1.9
..B1.8:
        negl      %r8d
        movl      $-1, %edx
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %edx
        movl      48(%rsp), %eax
        andl      %eax, %edx
        movl      %edx, 48(%rsp)
        xorl      %eax, %edx
        jmp       ..B1.10
..B1.9:
        negl      %r8d
        movl      $-1, %edx
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %edx
        movl      52(%rsp), %eax
        andl      %eax, %edx
        movl      %edx, 52(%rsp)
        xorl      %eax, %edx
        orl       48(%rsp), %edx
        movl      $0, 48(%rsp)
..B1.10:
        testb     $-128, 57(%rsp)
        jne       ..B1.18
..B1.11:
        testl     %edx, %edx
        je        ..B1.18
..B1.12:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.101
..B1.13:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.14:
        fldcw     24(%rsp)
..B1.15:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        faddl     (%rax)
        fstpt     (%rsp)
..B1.16:
        fldcw     26(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.18:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.19:
        testl     %r8d, %r8d
        jne       ..B1.23
..B1.20:
        cmpq      $0, 48(%rsp)
        jne       ..B1.22
..B1.21:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.22:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.23:
        movb      57(%rsp), %al
        lea       _pone_nzero(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.24:
        cmpl      $3072, %eax
        jne       ..B1.35
..B1.25:
        cmpl      $16383, %r8d
        jl        ..B1.30
..B1.26:
        cmpl      $16415, %r8d
        jl        ..B1.28
..B1.27:
        negl      %r8d
        movl      $-1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        andl      48(%rsp), %eax
        jmp       ..B1.29
..B1.28:
        negl      %r8d
        movl      $-1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        andl      %eax, 52(%rsp)
        xorl      %eax, %eax
..B1.29:
        movl      %eax, 48(%rsp)
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.30:
        testl     %r8d, %r8d
        jne       ..B1.34
..B1.31:
        cmpl      $0, 52(%rsp)
        jne       ..B1.33
..B1.32:
        cmpl      $0, 48(%rsp)
        je        ..B1.34
..B1.33:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.34:
        movb      57(%rsp), %al
        lea       _zeros(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.35:
        cmpl      $16383, %r8d
        jl        ..B1.69
..B1.36:
        movl      48(%rsp), %esi
        cmpl      $16414, %r8d
        jl        ..B1.38
..B1.37:
        movl      %esi, %edx
        lea       2(%r8), %ecx
        shll      %cl, %edx
        xorl      %eax, %eax
        jmp       ..B1.39
..B1.38:
        movl      52(%rsp), %edx
        lea       2(%r8), %ecx
        shll      %cl, %edx
        movl      %esi, %eax
..B1.39:
        cmpl      $16415, %r8d
        jl        ..B1.41
..B1.40:
        movl      %r8d, %ecx
        movl      $-1, %r10d
        negl      %ecx
        addl      $30, %ecx
        shll      %cl, %r10d
        andl      %r10d, %esi
        movl      %esi, 48(%rsp)
        jmp       ..B1.42
..B1.41:
        movl      %r8d, %ecx
        movl      $-1, %r10d
        negl      %ecx
        xorl      %esi, %esi
        addl      $30, %ecx
        shll      %cl, %r10d
        movl      $0, 48(%rsp)
        andl      %r10d, 52(%rsp)
..B1.42:
        testl     %edx, %edx
        jge       ..B1.68
..B1.43:
        andl      $2147483647, %edx
        orl       %eax, %edx
        je        ..B1.51
..B1.44:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.48
..B1.45:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.46:
        fldcw     24(%rsp)
..B1.47:
        movb      $1, %dil
..B1.48:
        fldt      48(%rsp)
        lea       _ones(%rip), %rdx
        movb      57(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        testb     %dil, %dil
        faddl     (%rdx,%rcx,8)
        fstpt     (%rsp)
        je        ..B1.50
..B1.49:
        fldcw     26(%rsp)
..B1.50:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.51:
        cmpl      $16415, %r8d
        jl        ..B1.60
..B1.52:
        negl      %r8d
        movl      $1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        testl     %eax, %esi
        je        ..B1.68
..B1.53:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.57
..B1.54:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.55:
        fldcw     24(%rsp)
..B1.56:
        movb      $1, %dil
..B1.57:
        fldt      48(%rsp)
        lea       _ones(%rip), %rdx
        movb      57(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        testb     %dil, %dil
        faddl     (%rdx,%rcx,8)
        fstpt     (%rsp)
        je        ..B1.59
..B1.58:
        fldcw     26(%rsp)
..B1.59:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.60:
        negl      %r8d
        movl      $1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        testl     %eax, 52(%rsp)
        je        ..B1.68
..B1.61:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.65
..B1.62:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.63:
        fldcw     24(%rsp)
..B1.64:
        movb      $1, %dil
..B1.65:
        fldt      48(%rsp)
        lea       _ones(%rip), %rdx
        movb      57(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        testb     %dil, %dil
        faddl     (%rdx,%rcx,8)
        fstpt     (%rsp)
        je        ..B1.67
..B1.66:
        fldcw     26(%rsp)
..B1.67:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.68:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.69:
        cmpl      $16382, %r8d
        jg        ..B1.76
..B1.70:
        je        ..B1.102
..B1.71:
        testl     %r8d, %r8d
        jne       ..B1.75
..B1.72:
        cmpl      $0, 52(%rsp)
        jne       ..B1.74
..B1.73:
        cmpl      $0, 48(%rsp)
        je        ..B1.75
..B1.74:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.75:
        movb      57(%rsp), %al
        lea       _zeros(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.76:
        movb      57(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.77:
        cmpl      $16383, %r8d
        jl        ..B1.90
..B1.78:
        cmpl      $16415, %r8d
        jl        ..B1.80
..B1.79:
        negl      %r8d
        movl      $-1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        movl      48(%rsp), %edx
        andl      %edx, %eax
        movl      %eax, 48(%rsp)
        xorl      %eax, %edx
        jmp       ..B1.81
..B1.80:
        negl      %r8d
        movl      $-1, %eax
        addl      $30, %r8d
        movl      %r8d, %ecx
        shll      %cl, %eax
        movl      52(%rsp), %edx
        andl      %edx, %eax
        xorl      %eax, %edx
        orl       48(%rsp), %edx
        movl      %eax, 52(%rsp)
        movl      $0, 48(%rsp)
..B1.81:
        testb     $-128, 57(%rsp)
        je        ..B1.89
..B1.82:
        testl     %edx, %edx
        je        ..B1.89
..B1.83:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.105
..B1.84:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.85:
        fldcw     24(%rsp)
..B1.86:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        faddl     (%rax)
        fstpt     (%rsp)
..B1.87:
        fldcw     26(%rsp)
..B1.88:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.89:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.90:
        testl     %r8d, %r8d
        jne       ..B1.94
..B1.91:
        cmpq      $0, 48(%rsp)
        jne       ..B1.93
..B1.92:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.93:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 16(%rsp)
..B1.94:
        movb      57(%rsp), %al
        lea       _pzero_none(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.95:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.106
..B1.96:
        orl       $-64768, %r9d
        movw      %r9w, 24(%rsp)
..B1.97:
        fldcw     24(%rsp)
..B1.98:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.99:
        fldcw     26(%rsp)
..B1.100:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.101:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        faddl     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.17
..B1.102:
        movl      52(%rsp), %eax
        cmpl      $-2147483648, %eax
        ja        ..B1.76
..B1.103:
        jne       ..B1.75
..B1.104:
        cmpl      $0, 48(%rsp)
        ja        ..B1.76
        jmp       ..B1.75
..B1.105:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        faddl     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.88
..B1.106:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.100
        .align    16,0x90
	.cfi_endproc
	.type	nearbyintl,@function
	.size	nearbyintl,.-nearbyintl
	.data
# -- End  nearbyintl
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
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
