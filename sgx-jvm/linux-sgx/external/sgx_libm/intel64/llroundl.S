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
	.file "llroundl.c"
	.text
..TXTST0:
# -- Begin  llroundl
	.text
       .align    16,0x90
	.globl llroundl
llroundl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_llroundl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %dl, %dl
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movb      57(%rsp), %r8b
        lea       _range(%rip), %rdi
        andb      $-128, %r8b
        shrb      $7, %r8b
        movzbl    %r8b, %eax
        movq      %rax, %rsi
        shlq      $4, %rsi
        movzwl    56(%rsp), %r9d
        andl      $32767, %r9d
        movzwl    8(%rsi,%rdi), %ecx
        andl      $32767, %ecx
        cmpl      %ecx, %r9d
        jl        ..B1.8
..B1.4:
        jne       ..B1.16
..B1.5:
        movl      52(%rsp), %ecx
        cmpl      4(%rsi,%rdi), %ecx
        jb        ..B1.8
..B1.6:
        jne       ..B1.16
..B1.7:
        movl      48(%rsp), %ecx
        cmpl      (%rsi,%rdi), %ecx
        ja        ..B1.16
..B1.8:
        cmpl      $16383, %r9d
        jl        ..B1.10
..B1.9:
        movl      %r9d, %edi
        negl      %edi
        movl      52(%rsp), %r10d
        movl      %r10d, %eax
        movl      48(%rsp), %edx
        movl      %edx, %esi
        shlq      $32, %r10
        lea       29(%rdi), %ecx
        shrl      %cl, %eax
        addl      $62, %edi
        shrl      %cl, %esi
        cmpl      $16414, %r9d
        movl      %edi, %ecx
        cmovl     %eax, %esi
        orq       %rdx, %r10
        andl      $1, %esi
        shrq      %cl, %r10
        addq      %rsi, %r10
        movq      %r10, %r9
        negq      %r9
        testb     %r8b, %r8b
        cmovne    %r9, %r10
        movq      %r10, %rax
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.10:
        cmpl      $16382, %r9d
        je        ..B1.23
..B1.11:
        testl     %r9d, %r9d
        jne       ..B1.15
..B1.12:
        cmpl      $0, 52(%rsp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, 48(%rsp)
        je        ..B1.15
..B1.14:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, (%rsp)
..B1.15:
        xorl      %eax, %eax
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.16:
        movzwl    34(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.20
..B1.17:
        orl       $-64768, %ecx
        movw      %cx, 32(%rsp)
..B1.18:
        fldcw     32(%rsp)
..B1.19:
        movb      $1, %dl
..B1.20:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, 8(%rsp)
        fldl      8(%rsp)
        fstpt     16(%rsp)
        je        ..B1.22
..B1.21:
        fldcw     34(%rsp)
..B1.22:
        movq      $0x8000000000000000, %rax
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.23:
        lea       iones(%rip), %rdx
        movslq    (%rdx,%rax,4), %rax
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	llroundl,@function
	.size	llroundl,.-llroundl
	.data
# -- End  llroundl
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
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 2
_range:
	.word	65534
	.word	65535
	.word	65535
	.word	65535
	.word	16445
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49214
	.word	0
	.word	0
	.word	0
	.type	_range,@object
	.size	_range,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
