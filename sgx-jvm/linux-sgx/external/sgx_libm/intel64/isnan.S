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
	.file "isnan.c"
	.text
..TXTST0:
# -- Begin  __isnand
	.text
       .align    16,0x90
	.globl __isnand
__isnand:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___isnand.1:
..L2:

        movq      $0x7ff0000000000000, %rdx
        movd      %xmm0, %rcx
        xorl      %eax, %eax
        btrq      $63, %rcx
        cmpq      %rcx, %rdx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isnand,@function
	.size	__isnand,.-__isnand
	.data
# -- End  __isnand
	.text
# -- Begin  isnand
	.text
       .align    16,0x90
	.globl isnand
isnand:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_isnand.4:
..L5:

        movq      $0x7ff0000000000000, %rdx
        movd      %xmm0, %rcx
        xorl      %eax, %eax
        btrq      $63, %rcx
        cmpq      %rcx, %rdx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isnand,@function
	.size	isnand,.-isnand
	.data
# -- End  isnand
	.text
# -- Begin  __isnan
	.text
       .align    16,0x90
	.globl __isnan
__isnan:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value___isnan.7:
..L8:

        movq      $0x7ff0000000000000, %rdx
        movd      %xmm0, %rcx
        xorl      %eax, %eax
        btrq      $63, %rcx
        cmpq      %rcx, %rdx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isnan,@function
	.size	__isnan,.-__isnan
	.data
# -- End  __isnan
	.text
# -- Begin  isnan
	.text
       .align    16,0x90
	.globl isnan
isnan:
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value_isnan.10:
..L11:

        movq      $0x7ff0000000000000, %rdx
        movd      %xmm0, %rcx
        xorl      %eax, %eax
        btrq      $63, %rcx
        cmpq      %rcx, %rdx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isnan,@function
	.size	isnan,.-isnan
	.data
# -- End  isnan
	.text
# -- Begin  __isnanf
	.text
       .align    16,0x90
	.globl __isnanf
__isnanf:
# parameter 1: %xmm0
..B5.1:
	.cfi_startproc
..___tag_value___isnanf.13:
..L14:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        seta      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isnanf,@function
	.size	__isnanf,.-__isnanf
	.data
# -- End  __isnanf
	.text
# -- Begin  isnanf
	.text
       .align    16,0x90
	.globl isnanf
isnanf:
# parameter 1: %xmm0
..B6.1:
	.cfi_startproc
..___tag_value_isnanf.16:
..L17:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        seta      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isnanf,@function
	.size	isnanf,.-isnanf
	.data
# -- End  isnanf
	.text
# -- Begin  __isnanl
	.text
       .align    16,0x90
	.globl __isnanl
__isnanl:
# parameter 1: 8 + %rsp
..B7.1:
	.cfi_startproc
..___tag_value___isnanl.19:
..L20:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B7.4
..B7.2:
        xorl      %eax, %eax
..B7.3:
        ret       
..B7.4:
        movq      $0x8000000000000000, %rdx
        movl      $1, %ecx
        xorl      %eax, %eax
        cmpq      8(%rsp), %rdx
        cmovne    %ecx, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__isnanl,@function
	.size	__isnanl,.-__isnanl
	.data
# -- End  __isnanl
	.text
# -- Begin  isnanl
	.text
       .align    16,0x90
	.globl isnanl
isnanl:
# parameter 1: 8 + %rsp
..B8.1:
	.cfi_startproc
..___tag_value_isnanl.22:
..L23:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B8.4
..B8.2:
        xorl      %eax, %eax
..B8.3:
        ret       
..B8.4:
        movq      $0x8000000000000000, %rdx
        movl      $1, %ecx
        xorl      %eax, %eax
        cmpq      8(%rsp), %rdx
        cmovne    %ecx, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	isnanl,@function
	.size	isnanl,.-isnanl
	.data
# -- End  isnanl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
