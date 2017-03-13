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
	.file "isnormal.c"
	.text
..TXTST0:
# -- Begin  __isnormald
	.text
       .align    16,0x90
	.globl __isnormald
__isnormald:
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        addl      $-1048576, %eax
        cmpl      $2145386496, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isnormald,@function
	.size	__isnormald,.-__isnormald
	.data
# -- End  __isnormald
	.text
# -- Begin  isnormald
	.text
       .align    16,0x90
	.globl isnormald
isnormald:
# parameter 1: 4 + %esp
..B2.1:
..L2:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        addl      $-1048576, %eax
        cmpl      $2145386496, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isnormald,@function
	.size	isnormald,.-isnormald
	.data
# -- End  isnormald
	.text
# -- Begin  __isnormal
	.text
       .align    16,0x90
	.globl __isnormal
__isnormal:
# parameter 1: 4 + %esp
..B3.1:
..L3:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        addl      $-1048576, %eax
        cmpl      $2145386496, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isnormal,@function
	.size	__isnormal,.-__isnormal
	.data
# -- End  __isnormal
	.text
# -- Begin  isnormal
	.text
       .align    16,0x90
	.globl isnormal
isnormal:
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        addl      $-1048576, %eax
        cmpl      $2145386496, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isnormal,@function
	.size	isnormal,.-isnormal
	.data
# -- End  isnormal
	.text
# -- Begin  __isnormalf
	.text
       .align    16,0x90
	.globl __isnormalf
__isnormalf:
# parameter 1: 4 + %esp
..B5.1:
..L5:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        addl      $-8388608, %eax
        cmpl      $2130706432, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isnormalf,@function
	.size	__isnormalf,.-__isnormalf
	.data
# -- End  __isnormalf
	.text
# -- Begin  isnormalf
	.text
       .align    16,0x90
	.globl isnormalf
isnormalf:
# parameter 1: 4 + %esp
..B6.1:
..L6:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        addl      $-8388608, %eax
        cmpl      $2130706432, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isnormalf,@function
	.size	isnormalf,.-isnormalf
	.data
# -- End  isnormalf
	.text
# -- Begin  __isnormall
	.text
       .align    16,0x90
	.globl __isnormall
__isnormall:
# parameter 1: 4 + %esp
..B7.1:
..L7:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        cmpl      $4096, %edx
        jb        ..B7.4
..B7.2:
        testl     $32768, %edx
        jne       ..B7.4
..B7.3:
        xorl      %eax, %eax
        ret       
..B7.4:
        addl      $-65536, %edx
        xorl      %eax, %eax
        cmpl      $2147352576, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	__isnormall,@function
	.size	__isnormall,.-__isnormall
	.data
# -- End  __isnormall
	.text
# -- Begin  isnormall
	.text
       .align    16,0x90
	.globl isnormall
isnormall:
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        cmpl      $4096, %edx
        jb        ..B8.4
..B8.2:
        testl     $32768, %edx
        jne       ..B8.4
..B8.3:
        xorl      %eax, %eax
        ret       
..B8.4:
        addl      $-65536, %edx
        xorl      %eax, %eax
        cmpl      $2147352576, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	isnormall,@function
	.size	isnormall,.-isnormall
	.data
# -- End  isnormall
	.data
	.section .note.GNU-stack, ""
# End
