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
	.file "isfinite.c"
	.text
..TXTST0:
# -- Begin  isfinitel
	.text
       .align    16,0x90
	.globl isfinitel
isfinitel:
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        xorl      %eax, %eax
        cmpl      $2147418112, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	isfinitel,@function
	.size	isfinitel,.-isfinitel
	.data
# -- End  isfinitel
	.text
# -- Begin  __isfinitel
	.text
       .align    16,0x90
	.globl __isfinitel
__isfinitel:
# parameter 1: 4 + %esp
..B2.1:
..L2:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        xorl      %eax, %eax
        cmpl      $2147418112, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	__isfinitel,@function
	.size	__isfinitel,.-__isfinitel
	.data
# -- End  __isfinitel
	.text
# -- Begin  isfinitef
	.text
       .align    16,0x90
	.globl isfinitef
isfinitef:
# parameter 1: 4 + %esp
..B3.1:
..L3:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isfinitef,@function
	.size	isfinitef,.-isfinitef
	.data
# -- End  isfinitef
	.text
# -- Begin  __isfinitef
	.text
       .align    16,0x90
	.globl __isfinitef
__isfinitef:
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isfinitef,@function
	.size	__isfinitef,.-__isfinitef
	.data
# -- End  __isfinitef
	.text
# -- Begin  isfinite
	.text
       .align    16,0x90
	.globl isfinite
isfinite:
# parameter 1: 4 + %esp
..B5.1:
..L5:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isfinite,@function
	.size	isfinite,.-isfinite
	.data
# -- End  isfinite
	.text
# -- Begin  __isfinite
	.text
       .align    16,0x90
	.globl __isfinite
__isfinite:
# parameter 1: 4 + %esp
..B6.1:
..L6:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isfinite,@function
	.size	__isfinite,.-__isfinite
	.data
# -- End  __isfinite
	.text
# -- Begin  isfinited
	.text
       .align    16,0x90
	.globl isfinited
isfinited:
# parameter 1: 4 + %esp
..B7.1:
..L7:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	isfinited,@function
	.size	isfinited,.-isfinited
	.data
# -- End  isfinited
	.text
# -- Begin  __isfinited
	.text
       .align    16,0x90
	.globl __isfinited
__isfinited:
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__isfinited,@function
	.size	__isfinited,.-__isfinited
	.data
# -- End  __isfinited
	.text
# -- Begin  finitel
	.text
       .align    16,0x90
	.globl finitel
finitel:
# parameter 1: 4 + %esp
..B9.1:
..L9:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        xorl      %eax, %eax
        cmpl      $2147418112, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	finitel,@function
	.size	finitel,.-finitel
	.data
# -- End  finitel
	.text
# -- Begin  __finitel
	.text
       .align    16,0x90
	.globl __finitel
__finitel:
# parameter 1: 4 + %esp
..B10.1:
..L10:

        movzwl    12(%esp), %edx
        shll      $16, %edx
        movzwl    10(%esp), %eax
        orl       %eax, %edx
        andl      $2147483647, %edx
        xorl      %eax, %eax
        cmpl      $2147418112, %edx
        setb      %al
        ret       
        .align    16,0x90
	.type	__finitel,@function
	.size	__finitel,.-__finitel
	.data
# -- End  __finitel
	.text
# -- Begin  finitef
	.text
       .align    16,0x90
	.globl finitef
finitef:
# parameter 1: 4 + %esp
..B11.1:
..L11:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	finitef,@function
	.size	finitef,.-finitef
	.data
# -- End  finitef
	.text
# -- Begin  __finitef
	.text
       .align    16,0x90
	.globl __finitef
__finitef:
# parameter 1: 4 + %esp
..B12.1:
..L12:

        movl      4(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__finitef,@function
	.size	__finitef,.-__finitef
	.data
# -- End  __finitef
	.text
# -- Begin  finite
	.text
       .align    16,0x90
	.globl finite
finite:
# parameter 1: 4 + %esp
..B13.1:
..L13:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	finite,@function
	.size	finite,.-finite
	.data
# -- End  finite
	.text
# -- Begin  __finite
	.text
       .align    16,0x90
	.globl __finite
__finite:
# parameter 1: 4 + %esp
..B14.1:
..L14:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__finite,@function
	.size	__finite,.-__finite
	.data
# -- End  __finite
	.text
# -- Begin  finited
	.text
       .align    16,0x90
	.globl finited
finited:
# parameter 1: 4 + %esp
..B15.1:
..L15:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	finited,@function
	.size	finited,.-finited
	.data
# -- End  finited
	.text
# -- Begin  __finited
	.text
       .align    16,0x90
	.globl __finited
__finited:
# parameter 1: 4 + %esp
..B16.1:
..L16:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__finited,@function
	.size	__finited,.-__finited
	.data
# -- End  __finited
	.data
	.section .note.GNU-stack, ""
# End
