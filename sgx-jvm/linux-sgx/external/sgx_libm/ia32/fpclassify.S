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
# parameter 1: 4 + %esp
..B1.1:
..L1:

        fldl      4(%esp)
        fwait     
        fstp      %st(0)
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        cmpl      $2146435072, %edx
        jb        ..B1.6
..B1.2:
        movl      $2146435072, %eax
        subl      %eax, %edx
        jb        ..B1.4
..B1.14:
        orl       %edx, %ecx
        je        ..B1.4
..B1.3:
        xorl      %eax, %eax
        ret       
..B1.4:
        movl      $1, %eax
..B1.5:
        ret       
..B1.6:
        cmpl      $1048576, %edx
        jae       ..B1.11
..B1.7:
        orl       %edx, %ecx
        je        ..B1.9
..B1.8:
        movl      $3, %eax
        ret       
..B1.9:
        movl      $2, %eax
..B1.10:
        ret       
..B1.11:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B2.1:
..L2:

        fldl      4(%esp)
        fwait     
        fstp      %st(0)
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        cmpl      $2146435072, %edx
        jb        ..B2.6
..B2.2:
        movl      $2146435072, %eax
        subl      %eax, %edx
        jb        ..B2.4
..B2.14:
        orl       %edx, %ecx
        je        ..B2.4
..B2.3:
        xorl      %eax, %eax
        ret       
..B2.4:
        movl      $1, %eax
..B2.5:
        ret       
..B2.6:
        cmpl      $1048576, %edx
        jae       ..B2.11
..B2.7:
        orl       %edx, %ecx
        je        ..B2.9
..B2.8:
        movl      $3, %eax
        ret       
..B2.9:
        movl      $2, %eax
..B2.10:
        ret       
..B2.11:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B3.1:
..L3:

        fldl      4(%esp)
        fwait     
        fstp      %st(0)
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        cmpl      $2146435072, %edx
        jb        ..B3.6
..B3.2:
        movl      $2146435072, %eax
        subl      %eax, %edx
        jb        ..B3.4
..B3.14:
        orl       %edx, %ecx
        je        ..B3.4
..B3.3:
        xorl      %eax, %eax
        ret       
..B3.4:
        movl      $1, %eax
..B3.5:
        ret       
..B3.6:
        cmpl      $1048576, %edx
        jae       ..B3.11
..B3.7:
        orl       %edx, %ecx
        je        ..B3.9
..B3.8:
        movl      $3, %eax
        ret       
..B3.9:
        movl      $2, %eax
..B3.10:
        ret       
..B3.11:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B4.1:
..L4:

        fldl      4(%esp)
        fwait     
        fstp      %st(0)
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        cmpl      $2146435072, %edx
        jb        ..B4.6
..B4.2:
        movl      $2146435072, %eax
        subl      %eax, %edx
        jb        ..B4.4
..B4.14:
        orl       %edx, %ecx
        je        ..B4.4
..B4.3:
        xorl      %eax, %eax
        ret       
..B4.4:
        movl      $1, %eax
..B4.5:
        ret       
..B4.6:
        cmpl      $1048576, %edx
        jae       ..B4.11
..B4.7:
        orl       %edx, %ecx
        je        ..B4.9
..B4.8:
        movl      $3, %eax
        ret       
..B4.9:
        movl      $2, %eax
..B4.10:
        ret       
..B4.11:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B5.1:
..L5:

        flds      4(%esp)
        fwait     
        fstp      %st(0)
        movl      4(%esp), %edx
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
        testl     %edx, %edx
        movl      $0, %eax
        setne     %al
        addl      $2, %eax
        ret       
..B5.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B6.1:
..L6:

        flds      4(%esp)
        fwait     
        fstp      %st(0)
        movl      4(%esp), %edx
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
        testl     %edx, %edx
        movl      $0, %eax
        setne     %al
        addl      $2, %eax
        ret       
..B6.5:
        movl      $4, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 8 + %esp
..B7.1:
..L7:

        pushl     %ebx
        movzwl    16(%esp), %ecx
        andl      $32767, %ecx
        movl      12(%esp), %ebx
        movl      %ecx, %eax
        movl      %ebx, %edx
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
        popl      %ebx
        ret       
..B7.5:
        cmpl      $65536, %eax
        jae       ..B7.11
..B7.6:
        testl     %ebx, %ebx
        jne       ..B7.8
..B7.7:
        cmpl      $0, 8(%esp)
        je        ..B7.9
..B7.8:
        movl      $3, %eax
        popl      %ebx
        ret       
..B7.9:
        movl      $2, %eax
..B7.10:
        popl      %ebx
        ret       
..B7.11:
        andl      $32768, %eax
        shrl      $15, %eax
        addl      $3, %eax
        popl      %ebx
        ret       
..B7.12:
        cmpl      $-2147483648, %ebx
        jne       ..B7.3
..B7.13:
        cmpl      $0, 8(%esp)
        jne       ..B7.3
..B7.14:
        movl      $1, %eax
        popl      %ebx
        ret       
        .align    16,0x90
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
# parameter 1: 8 + %esp
..B8.1:
..L8:

        pushl     %ebx
        movzwl    16(%esp), %ecx
        andl      $32767, %ecx
        movl      12(%esp), %ebx
        movl      %ecx, %eax
        movl      %ebx, %edx
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
        popl      %ebx
        ret       
..B8.5:
        cmpl      $65536, %eax
        jae       ..B8.11
..B8.6:
        testl     %ebx, %ebx
        jne       ..B8.8
..B8.7:
        cmpl      $0, 8(%esp)
        je        ..B8.9
..B8.8:
        movl      $3, %eax
        popl      %ebx
        ret       
..B8.9:
        movl      $2, %eax
..B8.10:
        popl      %ebx
        ret       
..B8.11:
        andl      $32768, %eax
        shrl      $15, %eax
        addl      $3, %eax
        popl      %ebx
        ret       
..B8.12:
        cmpl      $-2147483648, %ebx
        jne       ..B8.3
..B8.13:
        cmpl      $0, 8(%esp)
        jne       ..B8.3
..B8.14:
        movl      $1, %eax
        popl      %ebx
        ret       
        .align    16,0x90
	.type	fpclassifyl,@function
	.size	fpclassifyl,.-fpclassifyl
	.data
# -- End  fpclassifyl
	.data
	.section .note.GNU-stack, ""
# End
