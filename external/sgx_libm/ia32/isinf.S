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
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      $2146435072, %ecx
        negl      %ecx
        movl      8(%esp), %eax
        addl      %eax, %ecx
        movl      4(%esp), %edx
        orl       %edx, %ecx
        jne       ..B1.3
..B1.2:
        movl      $1, %eax
        ret       
..B1.3:
        movl      $-1048576, %ecx
        subl      %ecx, %eax
        orl       %eax, %edx
        jne       ..B1.5
..B1.4:
        movl      $-1, %eax
        ret       
..B1.5:
        xorl      %eax, %eax
..B1.6:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B2.1:
..L2:

        movl      $2146435072, %ecx
        negl      %ecx
        movl      8(%esp), %eax
        addl      %eax, %ecx
        movl      4(%esp), %edx
        orl       %edx, %ecx
        jne       ..B2.3
..B2.2:
        movl      $1, %eax
        ret       
..B2.3:
        movl      $-1048576, %ecx
        subl      %ecx, %eax
        orl       %eax, %edx
        jne       ..B2.5
..B2.4:
        movl      $-1, %eax
        ret       
..B2.5:
        xorl      %eax, %eax
..B2.6:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B3.1:
..L3:

        movl      $2146435072, %ecx
        negl      %ecx
        movl      8(%esp), %eax
        addl      %eax, %ecx
        movl      4(%esp), %edx
        orl       %edx, %ecx
        jne       ..B3.3
..B3.2:
        movl      $1, %eax
        ret       
..B3.3:
        movl      $-1048576, %ecx
        subl      %ecx, %eax
        orl       %eax, %edx
        jne       ..B3.5
..B3.4:
        movl      $-1, %eax
        ret       
..B3.5:
        xorl      %eax, %eax
..B3.6:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      $2146435072, %ecx
        negl      %ecx
        movl      8(%esp), %eax
        addl      %eax, %ecx
        movl      4(%esp), %edx
        orl       %edx, %ecx
        jne       ..B4.3
..B4.2:
        movl      $1, %eax
        ret       
..B4.3:
        movl      $-1048576, %ecx
        subl      %ecx, %eax
        orl       %eax, %edx
        jne       ..B4.5
..B4.4:
        movl      $-1, %eax
        ret       
..B4.5:
        xorl      %eax, %eax
..B4.6:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B5.1:
..L5:

        movl      4(%esp), %edx
        cmpl      $2139095040, %edx
        jne       ..B5.3
..B5.2:
        movl      $1, %eax
        ret       
..B5.3:
        cmpl      $-8388608, %edx
        movl      $0, %eax
        setne     %al
        decl      %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B6.1:
..L6:

        movl      4(%esp), %edx
        cmpl      $2139095040, %edx
        jne       ..B6.3
..B6.2:
        movl      $1, %eax
        ret       
..B6.3:
        cmpl      $-8388608, %edx
        movl      $0, %eax
        setne     %al
        decl      %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B7.1:
..L7:

        movzwl    12(%esp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B7.3
..B7.2:
        xorl      %eax, %eax
        ret       
..B7.3:
        cmpl      $-2147483648, 8(%esp)
        jne       ..B7.2
..B7.4:
        cmpl      $0, 4(%esp)
        jne       ..B7.2
..B7.5:
        movzbl    13(%esp), %eax
        notl      %eax
        andl      $128, %eax
        shrl      $6, %eax
        decl      %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movzwl    12(%esp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B8.3
..B8.2:
        xorl      %eax, %eax
        ret       
..B8.3:
        cmpl      $-2147483648, 8(%esp)
        jne       ..B8.2
..B8.4:
        cmpl      $0, 4(%esp)
        jne       ..B8.2
..B8.5:
        movzbl    13(%esp), %eax
        notl      %eax
        andl      $128, %eax
        shrl      $6, %eax
        decl      %eax
        ret       
        .align    16,0x90
	.type	isinfl,@function
	.size	isinfl,.-isinfl
	.data
# -- End  isinfl
	.data
	.section .note.GNU-stack, ""
# End
