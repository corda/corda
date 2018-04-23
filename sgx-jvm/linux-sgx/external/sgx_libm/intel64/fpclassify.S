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
	.file "fpclassify.c"
	.text
..TXTST0:
# -- Begin  __fpclassifyd
	.text
       .align    16,0x90
	.globl __fpclassifyd
__fpclassifyd:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___fpclassifyd.1:
..L2:

        movd      %xmm0, %rcx
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        btrq      $63, %rcx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jb        ..B1.3
..B1.2:
        movq      $0x7ff0000000000000, %rdx
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        setae     %al
        ret       
..B1.3:
        cmpl      $1048576, %eax
        jae       ..B1.5
..B1.4:
        movl      $3, %edx
        movl      $2, %eax
        testq     %rcx, %rcx
        cmovne    %edx, %eax
        ret       
..B1.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__fpclassifyd,@function
	.size	__fpclassifyd,.-__fpclassifyd
	.data
# -- End  __fpclassifyd
	.text
# -- Begin  fpclassifyd
	.text
       .align    16,0x90
	.globl fpclassifyd
fpclassifyd:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_fpclassifyd.4:
..L5:

        movd      %xmm0, %rcx
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        btrq      $63, %rcx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jb        ..B2.3
..B2.2:
        movq      $0x7ff0000000000000, %rdx
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        setae     %al
        ret       
..B2.3:
        cmpl      $1048576, %eax
        jae       ..B2.5
..B2.4:
        movl      $3, %edx
        movl      $2, %eax
        testq     %rcx, %rcx
        cmovne    %edx, %eax
        ret       
..B2.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fpclassifyd,@function
	.size	fpclassifyd,.-fpclassifyd
	.data
# -- End  fpclassifyd
	.text
# -- Begin  __fpclassify
	.text
       .align    16,0x90
	.globl __fpclassify
__fpclassify:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value___fpclassify.7:
..L8:

        movd      %xmm0, %rcx
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        btrq      $63, %rcx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jb        ..B3.3
..B3.2:
        movq      $0x7ff0000000000000, %rdx
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        setae     %al
        ret       
..B3.3:
        cmpl      $1048576, %eax
        jae       ..B3.5
..B3.4:
        movl      $3, %edx
        movl      $2, %eax
        testq     %rcx, %rcx
        cmovne    %edx, %eax
        ret       
..B3.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__fpclassify,@function
	.size	__fpclassify,.-__fpclassify
	.data
# -- End  __fpclassify
	.text
# -- Begin  fpclassify
	.text
       .align    16,0x90
	.globl fpclassify
fpclassify:
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value_fpclassify.10:
..L11:

        movd      %xmm0, %rcx
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        btrq      $63, %rcx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jb        ..B4.3
..B4.2:
        movq      $0x7ff0000000000000, %rdx
        xorl      %eax, %eax
        cmpq      %rcx, %rdx
        setae     %al
        ret       
..B4.3:
        cmpl      $1048576, %eax
        jae       ..B4.5
..B4.4:
        movl      $3, %edx
        movl      $2, %eax
        testq     %rcx, %rcx
        cmovne    %edx, %eax
        ret       
..B4.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fpclassify,@function
	.size	fpclassify,.-fpclassify
	.data
# -- End  fpclassify
	.text
# -- Begin  __fpclassifyf
	.text
       .align    16,0x90
	.globl __fpclassifyf
__fpclassifyf:
# parameter 1: %xmm0
..B5.1:
	.cfi_startproc
..___tag_value___fpclassifyf.13:
..L14:

        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jb        ..B5.3
..B5.2:
        movl      $0, %eax
        setbe     %al
        ret       
..B5.3:
        cmpl      $8388608, %edx
        jae       ..B5.5
..B5.4:
        movl      $3, %ecx
        movl      $2, %eax
        testl     %edx, %edx
        cmovne    %ecx, %eax
        ret       
..B5.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__fpclassifyf,@function
	.size	__fpclassifyf,.-__fpclassifyf
	.data
# -- End  __fpclassifyf
	.text
# -- Begin  fpclassifyf
	.text
       .align    16,0x90
	.globl fpclassifyf
fpclassifyf:
# parameter 1: %xmm0
..B6.1:
	.cfi_startproc
..___tag_value_fpclassifyf.16:
..L17:

        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jb        ..B6.3
..B6.2:
        movl      $0, %eax
        setbe     %al
        ret       
..B6.3:
        cmpl      $8388608, %edx
        jae       ..B6.5
..B6.4:
        movl      $3, %ecx
        movl      $2, %eax
        testl     %edx, %edx
        cmovne    %ecx, %eax
        ret       
..B6.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fpclassifyf,@function
	.size	fpclassifyf,.-fpclassifyf
	.data
# -- End  fpclassifyf
	.text
# -- Begin  __fpclassifyl
	.text
       .align    16,0x90
	.globl __fpclassifyl
__fpclassifyl:
# parameter 1: 8 + %rsp
..B7.1:
	.cfi_startproc
..___tag_value___fpclassifyl.19:
..L20:

        movzwl    16(%rsp), %ecx
        andl      $32767, %ecx
        movl      12(%rsp), %esi
        movl      %ecx, %eax
        movl      %esi, %edx
        shll      $16, %eax
        shrl      $16, %edx
        orl       %edx, %eax
        cmpl      $2147418112, %eax
        jb        ..B7.5
..B7.2:
        cmpl      $32767, %ecx
        je        ..B7.12
..B7.3:
        xorl      %eax, %eax
..B7.4:
        ret       
..B7.5:
        cmpl      $65536, %eax
        jae       ..B7.11
..B7.6:
        testl     %esi, %esi
        jne       ..B7.8
..B7.7:
        cmpl      $0, 8(%rsp)
        je        ..B7.9
..B7.8:
        movl      $3, %eax
        ret       
..B7.9:
        movl      $2, %eax
..B7.10:
        ret       
..B7.11:
        andl      $32768, %eax
        shrl      $15, %eax
        addl      $3, %eax
        ret       
..B7.12:
        cmpl      $-2147483648, %esi
        jne       ..B7.3
..B7.13:
        cmpl      $0, 8(%rsp)
        jne       ..B7.3
..B7.14:
        movl      $1, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__fpclassifyl,@function
	.size	__fpclassifyl,.-__fpclassifyl
	.data
# -- End  __fpclassifyl
	.text
# -- Begin  fpclassifyl
	.text
       .align    16,0x90
	.globl fpclassifyl
fpclassifyl:
# parameter 1: 8 + %rsp
..B8.1:
	.cfi_startproc
..___tag_value_fpclassifyl.22:
..L23:

        movzwl    16(%rsp), %ecx
        andl      $32767, %ecx
        movl      12(%rsp), %esi
        movl      %ecx, %eax
        movl      %esi, %edx
        shll      $16, %eax
        shrl      $16, %edx
        orl       %edx, %eax
        cmpl      $2147418112, %eax
        jb        ..B8.5
..B8.2:
        cmpl      $32767, %ecx
        je        ..B8.12
..B8.3:
        xorl      %eax, %eax
..B8.4:
        ret       
..B8.5:
        cmpl      $65536, %eax
        jae       ..B8.11
..B8.6:
        testl     %esi, %esi
        jne       ..B8.8
..B8.7:
        cmpl      $0, 8(%rsp)
        je        ..B8.9
..B8.8:
        movl      $3, %eax
        ret       
..B8.9:
        movl      $2, %eax
..B8.10:
        ret       
..B8.11:
        andl      $32768, %eax
        shrl      $15, %eax
        addl      $3, %eax
        ret       
..B8.12:
        cmpl      $-2147483648, %esi
        jne       ..B8.3
..B8.13:
        cmpl      $0, 8(%rsp)
        jne       ..B8.3
..B8.14:
        movl      $1, %eax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fpclassifyl,@function
	.size	fpclassifyl,.-fpclassifyl
	.data
# -- End  fpclassifyl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
