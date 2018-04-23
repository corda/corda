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
	.file "isinf.c"
	.text
..TXTST0:
# -- Begin  __isinfd
	.text
       .align    16,0x90
	.globl __isinfd
__isinfd:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___isinfd.1:
..L2:

        movq      $0x7ff0000000000000, %rax
        movd      %xmm0, %rcx
        cmpq      %rax, %rcx
        jne       ..B1.3
..B1.2:
        movl      $1, %eax
        ret       
..B1.3:
        movq      $0xfff0000000000000, %rdx
        movl      $-1, %esi
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        cmove     %esi, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isinfd,@function
	.size	__isinfd,.-__isinfd
	.data
# -- End  __isinfd
	.text
# -- Begin  isinfd
	.text
       .align    16,0x90
	.globl isinfd
isinfd:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_isinfd.4:
..L5:

        movq      $0x7ff0000000000000, %rax
        movd      %xmm0, %rcx
        cmpq      %rax, %rcx
        jne       ..B2.3
..B2.2:
        movl      $1, %eax
        ret       
..B2.3:
        movq      $0xfff0000000000000, %rdx
        movl      $-1, %esi
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        cmove     %esi, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isinfd,@function
	.size	isinfd,.-isinfd
	.data
# -- End  isinfd
	.text
# -- Begin  __isinf
	.text
       .align    16,0x90
	.globl __isinf
__isinf:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value___isinf.7:
..L8:

        movq      $0x7ff0000000000000, %rax
        movd      %xmm0, %rcx
        cmpq      %rax, %rcx
        jne       ..B3.3
..B3.2:
        movl      $1, %eax
        ret       
..B3.3:
        movq      $0xfff0000000000000, %rdx
        movl      $-1, %esi
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        cmove     %esi, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isinf,@function
	.size	__isinf,.-__isinf
	.data
# -- End  __isinf
	.text
# -- Begin  isinf
	.text
       .align    16,0x90
	.globl isinf
isinf:
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value_isinf.10:
..L11:

        movq      $0x7ff0000000000000, %rax
        movd      %xmm0, %rcx
        cmpq      %rax, %rcx
        jne       ..B4.3
..B4.2:
        movl      $1, %eax
        ret       
..B4.3:
        movq      $0xfff0000000000000, %rdx
        movl      $-1, %esi
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        cmove     %esi, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isinf,@function
	.size	isinf,.-isinf
	.data
# -- End  isinf
	.text
# -- Begin  __isinff
	.text
       .align    16,0x90
	.globl __isinff
__isinff:
# parameter 1: %xmm0
..B5.1:
	.cfi_startproc
..___tag_value___isinff.13:
..L14:

        movd      %xmm0, %edx
        cmpl      $2139095040, %edx
        jne       ..B5.3
..B5.2:
        movl      $1, %eax
        ret       
..B5.3:
        movl      $-1, %ecx
        xorl      %eax, %eax
        cmpl      $-8388608, %edx
        cmove     %ecx, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isinff,@function
	.size	__isinff,.-__isinff
	.data
# -- End  __isinff
	.text
# -- Begin  isinff
	.text
       .align    16,0x90
	.globl isinff
isinff:
# parameter 1: %xmm0
..B6.1:
	.cfi_startproc
..___tag_value_isinff.16:
..L17:

        movd      %xmm0, %edx
        cmpl      $2139095040, %edx
        jne       ..B6.3
..B6.2:
        movl      $1, %eax
        ret       
..B6.3:
        movl      $-1, %ecx
        xorl      %eax, %eax
        cmpl      $-8388608, %edx
        cmove     %ecx, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isinff,@function
	.size	isinff,.-isinff
	.data
# -- End  isinff
	.text
# -- Begin  __isinfl
	.text
       .align    16,0x90
	.globl __isinfl
__isinfl:
# parameter 1: 8 + %rsp
..B7.1:
	.cfi_startproc
..___tag_value___isinfl.19:
..L20:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B7.5
..B7.2:
        cmpl      $-2147483648, 12(%rsp)
        jne       ..B7.5
..B7.3:
        cmpl      $0, 8(%rsp)
        jne       ..B7.5
..B7.4:
        movzbl    17(%rsp), %eax
        notl      %eax
        andl      $128, %eax
        shrl      $6, %eax
        decl      %eax
        ret       
..B7.5:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isinfl,@function
	.size	__isinfl,.-__isinfl
	.data
# -- End  __isinfl
	.text
# -- Begin  isinfl
	.text
       .align    16,0x90
	.globl isinfl
isinfl:
# parameter 1: 8 + %rsp
..B8.1:
	.cfi_startproc
..___tag_value_isinfl.22:
..L23:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B8.5
..B8.2:
        cmpl      $-2147483648, 12(%rsp)
        jne       ..B8.5
..B8.3:
        cmpl      $0, 8(%rsp)
        jne       ..B8.5
..B8.4:
        movzbl    17(%rsp), %eax
        notl      %eax
        andl      $128, %eax
        shrl      $6, %eax
        decl      %eax
        ret       
..B8.5:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isinfl,@function
	.size	isinfl,.-isinfl
	.data
# -- End  isinfl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
