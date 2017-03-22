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
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      $2146435072, %eax
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        subl      %eax, %edx
        jb        ..B1.3
..B1.7:
        orl       %edx, %ecx
        je        ..B1.3
..B1.2:
        movl      $1, %eax
        ret       
..B1.3:
        xorl      %eax, %eax
..B1.4:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B2.1:
..L2:

        movl      $2146435072, %eax
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        subl      %eax, %edx
        jb        ..B2.3
..B2.7:
        orl       %edx, %ecx
        je        ..B2.3
..B2.2:
        movl      $1, %eax
        ret       
..B2.3:
        xorl      %eax, %eax
..B2.4:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B3.1:
..L3:

        movl      $2146435072, %eax
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        subl      %eax, %edx
        jb        ..B3.3
..B3.7:
        orl       %edx, %ecx
        je        ..B3.3
..B3.2:
        movl      $1, %eax
        ret       
..B3.3:
        xorl      %eax, %eax
..B3.4:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      $2146435072, %eax
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        movl      4(%esp), %ecx
        subl      %eax, %edx
        jb        ..B4.3
..B4.7:
        orl       %edx, %ecx
        je        ..B4.3
..B4.2:
        movl      $1, %eax
        ret       
..B4.3:
        xorl      %eax, %eax
..B4.4:
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B5.1:
..L5:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        seta      %al
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B6.1:
..L6:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        seta      %al
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B7.1:
..L7:

        movzwl    12(%esp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B7.4
..B7.2:
        xorl      %eax, %eax
..B7.3:
        ret       
..B7.4:
        cmpl      $-2147483648, 8(%esp)
        jne       ..B7.6
..B7.5:
        cmpl      $0, 4(%esp)
        je        ..B7.2
..B7.6:
        movl      $1, %eax
        ret       
        .align    16,0x90
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
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movzwl    12(%esp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B8.4
..B8.2:
        xorl      %eax, %eax
..B8.3:
        ret       
..B8.4:
        cmpl      $-2147483648, 8(%esp)
        jne       ..B8.6
..B8.5:
        cmpl      $0, 4(%esp)
        je        ..B8.2
..B8.6:
        movl      $1, %eax
        ret       
        .align    16,0x90
	.type	isnanl,@function
	.size	isnanl,.-isnanl
	.data
# -- End  isnanl
	.data
	.section .note.GNU-stack, ""
# End
