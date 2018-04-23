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
	.file "signbit.c"
	.text
..TXTST0:
# -- Begin  __signbitd
	.text
       .align    16,0x90
	.globl __signbitd
__signbitd:
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      8(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	__signbitd,@function
	.size	__signbitd,.-__signbitd
	.data
# -- End  __signbitd
	.text
# -- Begin  signbitd
	.text
       .align    16,0x90
	.globl signbitd
signbitd:
# parameter 1: 4 + %esp
..B2.1:
..L2:

        movl      8(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	signbitd,@function
	.size	signbitd,.-signbitd
	.data
# -- End  signbitd
	.text
# -- Begin  __signbit
	.text
       .align    16,0x90
	.globl __signbit
__signbit:
# parameter 1: 4 + %esp
..B3.1:
..L3:

        movl      8(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	__signbit,@function
	.size	__signbit,.-__signbit
	.data
# -- End  __signbit
	.text
# -- Begin  signbit
	.text
       .align    16,0x90
	.globl signbit
signbit:
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      8(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	signbit,@function
	.size	signbit,.-signbit
	.data
# -- End  signbit
	.text
# -- Begin  __signbitf
	.text
       .align    16,0x90
	.globl __signbitf
__signbitf:
# parameter 1: 4 + %esp
..B5.1:
..L5:

        movl      4(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	__signbitf,@function
	.size	__signbitf,.-__signbitf
	.data
# -- End  __signbitf
	.text
# -- Begin  signbitf
	.text
       .align    16,0x90
	.globl signbitf
signbitf:
# parameter 1: 4 + %esp
..B6.1:
..L6:

        movl      4(%esp), %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	signbitf,@function
	.size	signbitf,.-signbitf
	.data
# -- End  signbitf
	.text
# -- Begin  __signbitl
	.text
       .align    16,0x90
	.globl __signbitl
__signbitl:
# parameter 1: 4 + %esp
..B7.1:
..L7:

        movzbl    13(%esp), %eax
        shrl      $7, %eax
        movzwl    12(%esp), %edx
        shll      $15, %eax
        andl      $32767, %edx
        orl       %edx, %eax
        shll      $16, %eax
        movzwl    10(%esp), %ecx
        orl       %ecx, %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	__signbitl,@function
	.size	__signbitl,.-__signbitl
	.data
# -- End  __signbitl
	.text
# -- Begin  signbitl
	.text
       .align    16,0x90
	.globl signbitl
signbitl:
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movzbl    13(%esp), %eax
        shrl      $7, %eax
        movzwl    12(%esp), %edx
        shll      $15, %eax
        andl      $32767, %edx
        orl       %edx, %eax
        shll      $16, %eax
        movzwl    10(%esp), %ecx
        orl       %ecx, %eax
        shrl      $31, %eax
        ret       
        .align    16,0x90
	.type	signbitl,@function
	.size	signbitl,.-signbitl
	.data
# -- End  signbitl
	.data
	.section .note.GNU-stack, ""
# End
