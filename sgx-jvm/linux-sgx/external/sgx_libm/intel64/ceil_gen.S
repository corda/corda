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
	.file "ceil_gen.c"
	.text
..TXTST0:
# -- Begin  ceil
	.text
       .align    16,0x90
	.globl ceil
ceil:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_ceil.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        movl      %eax, %edi
        andl      $2147483647, %edi
        lea       -1072693248(%rdi), %edx
        cmpl      $54525952, %edx
        jae       ..B1.12
..B1.2:
        movl      %edi, %ecx
        shrl      $20, %ecx
        cmpl      $22020096, %edx
        jae       ..B1.8
..B1.3:
        negl      %ecx
        movl      $-1, %esi
        addl      $19, %ecx
        shll      %cl, %esi
        movl      %esi, %edx
        andl      %esi, %eax
        notl      %edx
        andl      %edx, %edi
        orl       -8(%rsp), %edi
        movl      $0, -8(%rsp)
        testl     $-2147483648, %eax
        jne       ..B1.6
..B1.4:
        testl     %edi, %edi
        je        ..B1.6
..B1.5:
        subl      %esi, %eax
        movl      %eax, -4(%rsp)
        jmp       ..B1.11
..B1.6:
        movl      %eax, -4(%rsp)
        jmp       ..B1.11
..B1.8:
        negl      %ecx
        movl      $-1, %esi
        addl      $19, %ecx
        shll      %cl, %esi
        movl      %esi, %ecx
        notl      %ecx
        movl      -8(%rsp), %edx
        andl      %edx, %ecx
        andl      %esi, %edx
        movl      %edx, -8(%rsp)
        testl     $-2147483648, %eax
        jne       ..B1.11
..B1.9:
        testl     %ecx, %ecx
        je        ..B1.11
..B1.10:
        movl      -4(%rsp), %eax
        shlq      $32, %rax
        movslq    %esi, %rsi
        orq       %rdx, %rax
        subq      %rsi, %rax
        movq      %rax, -8(%rsp)
..B1.11:
        movsd     -8(%rsp), %xmm0
        ret       
..B1.12:
        testl     $-2147483648, %edx
        je        ..B1.17
..B1.13:
        movl      -8(%rsp), %edx
        orl       %edx, %edi
        jne       ..B1.15
..B1.14:
        movsd     -8(%rsp), %xmm0
        ret       
..B1.15:
        lea       _pone_nzero(%rip), %rdx
        shrl      $31, %eax
        movsd     (%rdx,%rax,8), %xmm0
..B1.16:
        ret       
..B1.17:
        movsd     -8(%rsp), %xmm0
        cmpl      $1073741824, %edx
        jb        ..B1.20
..B1.19:
        lea       _ones(%rip), %rax
        mulsd     (%rax), %xmm0
..B1.20:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	ceil,@function
	.size	ceil,.-ceil
	.data
# -- End  ceil
	.section .rodata, "a"
	.align 4
	.align 4
_pone_nzero:
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
