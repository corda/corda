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
	.file "libm_mul_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_mul_k64
	.text
       .align    16,0x90
	.globl __libm_mul_k64
__libm_mul_k64:
# parameter 1: %edi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B1.1:
	.cfi_startproc
..___tag_value___libm_mul_k64.1:
..L2:

        movq      %rdx, %rax
        movzwl    6(%rsi), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        movsd     (%rsi), %xmm3
        cmpl      $2047, %edx
        movsd     8(%rsi), %xmm7
        movsd     (%rax), %xmm2
        movsd     8(%rax), %xmm4
        je        ..B1.12
..B1.2:
        testl     %edx, %edx
        jne       ..B1.7
..B1.3:
        testl     $1048575, 4(%rsi)
        jne       ..B1.7
..B1.4:
        cmpl      $0, (%rsi)
        je        ..B1.12
..B1.7:
        movsd     %xmm2, -24(%rsp)
..B1.8:
        movzwl    -18(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        je        ..B1.12
..B1.9:
        testl     %eax, %eax
        jne       ..B1.13
..B1.10:
        testl     $1048575, -20(%rsp)
        jne       ..B1.13
..B1.11:
        cmpl      $0, -24(%rsp)
        jne       ..B1.13
..B1.12:
        mulsd     %xmm2, %xmm3
        movsd     %xmm3, (%rcx)
        ret       
..B1.13:
        testl     %edx, %edx
        jne       ..B1.15
..B1.14:
        lea       _CONSTANTS(%rip), %rdx
        movaps    %xmm3, %xmm1
        addl      $-1023, %edi
        movsd     (%rdx), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm7
        jmp       ..B1.16
..B1.15:
        lea       32+_CONSTANTS(%rip), %rsi
        movl      %edx, %r9d
        negl      %r9d
        movaps    %xmm3, %xmm1
        addl      $-2, %r9d
        lea       -1023(%rdi,%rdx), %edi
        andl      $2047, %r9d
        movq      (%rsi), %r8
        movq      %r8, -24(%rsp)
        shrq      $48, %r8
        shll      $4, %r9d
        andl      $-32753, %r8d
        orl       %r9d, %r8d
        movw      %r8w, -18(%rsp)
        movsd     -24(%rsp), %xmm0
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm7
..B1.16:
        testl     %eax, %eax
        jne       ..B1.18
..B1.17:
        lea       _CONSTANTS(%rip), %rax
        movaps    %xmm2, %xmm8
        addl      $-1023, %edi
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm8
        mulsd     %xmm0, %xmm4
        jmp       ..B1.19
..B1.18:
        lea       32+_CONSTANTS(%rip), %rdx
        movl      %eax, %r8d
        negl      %r8d
        movaps    %xmm2, %xmm8
        addl      $-2, %r8d
        lea       -1023(%rdi,%rax), %edi
        andl      $2047, %r8d
        movq      (%rdx), %rsi
        movq      %rsi, -24(%rsp)
        shrq      $48, %rsi
        shll      $4, %r8d
        andl      $-32753, %esi
        orl       %r8d, %esi
        movw      %si, -18(%rsp)
        movsd     -24(%rsp), %xmm0
        mulsd     %xmm0, %xmm8
        mulsd     %xmm0, %xmm4
..B1.19:
        movaps    %xmm7, %xmm0
        lea       16+_CONSTANTS(%rip), %rax
        movaps    %xmm4, %xmm10
        addsd     %xmm1, %xmm0
        addsd     %xmm8, %xmm10
        movaps    %xmm0, %xmm5
        movaps    %xmm0, %xmm9
        movaps    %xmm10, %xmm11
        subsd     %xmm1, %xmm5
        movsd     (%rax), %xmm1
        subsd     %xmm5, %xmm7
        mulsd     %xmm1, %xmm9
        mulsd     %xmm1, %xmm11
        movaps    %xmm9, %xmm6
        subsd     %xmm0, %xmm6
        subsd     %xmm6, %xmm9
        movaps    %xmm9, %xmm5
        subsd     %xmm9, %xmm0
        addsd     %xmm7, %xmm0
        movaps    %xmm10, %xmm7
        subsd     %xmm8, %xmm7
        movaps    %xmm11, %xmm8
        movaps    %xmm0, %xmm6
        subsd     %xmm10, %xmm8
        subsd     %xmm7, %xmm4
        subsd     %xmm8, %xmm11
        mulsd     %xmm11, %xmm0
        subsd     %xmm11, %xmm10
        mulsd     %xmm11, %xmm5
        addsd     %xmm4, %xmm10
        mulsd     %xmm10, %xmm6
        movaps    %xmm5, %xmm4
        mulsd     %xmm9, %xmm10
        addsd     %xmm10, %xmm6
        addsd     %xmm0, %xmm6
        addsd     %xmm6, %xmm4
        movsd     %xmm4, -16(%rsp)
        movzwl    -10(%rsp), %edx
        movl      %edx, %esi
        andl      $32752, %esi
        shrl      $4, %esi
        lea       (%rdi,%rsi), %eax
        cmpl      $2047, %eax
        jl        ..B1.21
..B1.20:
        lea       _CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm3, %xmm0
        mulsd     %xmm2, %xmm0
        movsd     %xmm0, (%rcx)
        ret       
..B1.21:
        testl     %eax, %eax
        jle       ..B1.23
..B1.22:
        andl      $2047, %eax
        andl      $-32753, %edx
        shll      $4, %eax
        orl       %eax, %edx
        movw      %dx, -10(%rsp)
        movq      -16(%rsp), %rax
        movq      %rax, (%rcx)
        ret       
..B1.23:
        cmpl      $-9, %eax
        jl        ..B1.25
..B1.24:
        lea       8+_CONSTANTS(%rip), %rax
        lea       32+_CONSTANTS(%rip), %rsi
        lea       24+_CONSTANTS(%rip), %rdx
        addl      $1423, %edi
        andl      $2047, %edi
        shll      $4, %edi
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movq      (%rsi), %r8
        addsd     %xmm0, %xmm5
        movaps    %xmm5, %xmm4
        movq      %r8, -24(%rsp)
        addsd     %xmm6, %xmm4
        mulsd     %xmm4, %xmm1
        movaps    %xmm4, %xmm2
        subsd     %xmm5, %xmm2
        movaps    %xmm1, %xmm5
        subsd     %xmm2, %xmm6
        subsd     %xmm4, %xmm5
        shrq      $48, %r8
        subsd     %xmm5, %xmm1
        andl      $-32753, %r8d
        subsd     %xmm1, %xmm4
        orl       %edi, %r8d
        addsd     %xmm6, %xmm4
        movsd     (%rdx), %xmm6
        mulsd     %xmm6, %xmm1
        mulsd     %xmm6, %xmm4
        movw      %r8w, -18(%rsp)
        movsd     -24(%rsp), %xmm3
        mulsd     %xmm3, %xmm1
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm1
        movsd     %xmm1, (%rcx)
        ret       
..B1.25:
        cmpl      $-62, %eax
        jle       ..B1.27
..B1.26:
        lea       32+_CONSTANTS(%rip), %rax
        lea       1022(%rdi,%rsi), %esi
        lea       8+_CONSTANTS(%rip), %rdi
        andl      $-32753, %edx
        orl       $16, %edx
        andl      $2047, %esi
        movw      %dx, -10(%rsp)
        movq      (%rax), %rdx
        movsd     (%rdi), %xmm0
        mulsd     %xmm0, %xmm0
        movq      %rdx, -24(%rsp)
        shrq      $48, %rdx
        shll      $4, %esi
        andl      $-32753, %edx
        orl       %esi, %edx
        movw      %dx, -18(%rsp)
        movsd     -16(%rsp), %xmm1
        addsd     -24(%rsp), %xmm0
        mulsd     %xmm0, %xmm1
        movsd     %xmm0, -24(%rsp)
        movsd     %xmm1, -16(%rsp)
        movsd     %xmm1, (%rcx)
        ret       
..B1.27:
        lea       8+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm3, %xmm0
        mulsd     %xmm2, %xmm0
        movsd     %xmm0, (%rcx)
..B1.28:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_mul_k64,@function
	.size	__libm_mul_k64,.-__libm_mul_k64
	.data
# -- End  __libm_mul_k64
	.section .rodata, "a"
	.align 16
	.align 16
_CONSTANTS:
	.long	0
	.long	2145386496
	.long	0
	.long	1048576
	.long	33554432
	.long	1101004800
	.long	0
	.long	653262848
	.long	0
	.long	1072693248
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,40
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
