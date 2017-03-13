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
	.file "modff.c"
	.text
..TXTST0:
# -- Begin  modff
	.text
       .align    16,0x90
	.globl modff
modff:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_modff.1:
..L2:

        movd      %xmm0, %esi
        movss     %xmm0, -8(%rsp)
        movl      %esi, %ecx
        andl      $2147483647, %ecx
        lea       -1065353216(%rcx), %eax
        cmpl      $192937984, %eax
        jae       ..B1.6
..B1.2:
        shrl      $23, %ecx
        movl      $-1, %edx
        negl      %ecx
        addl      $22, %ecx
        shll      %cl, %edx
        movl      %edx, %eax
        andl      %esi, %edx
        notl      %eax
        movl      %edx, (%rdi)
        testl     %eax, %esi
        je        ..B1.4
..B1.3:
        subss     (%rdi), %xmm0
        ret       
..B1.4:
        lea       _zeros(%rip), %rax
        shrl      $31, %esi
        movss     (%rax,%rsi,4), %xmm0
..B1.5:
        ret       
..B1.6:
        shrl      $31, %esi
        cmpl      $2139095040, %ecx
        ja        ..B1.11
..B1.7:
        cmpl      $1065353216, %ecx
        jbe       ..B1.9
..B1.8:
        movl      %esi, %esi
        lea       _zeros(%rip), %rdx
        movl      -8(%rsp), %eax
        movl      %eax, (%rdi)
        movss     (%rdx,%rsi,4), %xmm0
        ret       
..B1.9:
        lea       _zeros(%rip), %rax
        movss     -8(%rsp), %xmm0
        movl      (%rax,%rsi,4), %edx
        movl      %edx, (%rdi)
..B1.10:
        ret       
..B1.11:
        movss     -8(%rsp), %xmm0
        addss     %xmm0, %xmm0
        movss     %xmm0, (%rdi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	modff,@function
	.size	modff,.-modff
	.data
# -- End  modff
	.section .rodata, "a"
	.align 4
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
