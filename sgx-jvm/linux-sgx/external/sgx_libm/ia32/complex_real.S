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
	.file "complex_real.c"
	.text
..TXTST0:
# -- Begin  __libm_internal_isinf
	.text
       .align    16,0x90
	.hidden __libm_internal_isinf
	.globl __libm_internal_isinf
__libm_internal_isinf:
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      8(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jne       ..B1.4
..B1.2:
        cmpl      $0, 4(%esp)
        jne       ..B1.4
..B1.3:
        movl      $1, %eax
        ret       
..B1.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isinf,@function
	.size	__libm_internal_isinf,.-__libm_internal_isinf
	.data
# -- End  __libm_internal_isinf
	.text
# -- Begin  __libm_internal_ispinf
	.text
       .align    16,0x90
	.hidden __libm_internal_ispinf
	.globl __libm_internal_ispinf
__libm_internal_ispinf:
# parameter 1: 4 + %esp
..B2.1:
..L2:

        cmpl      $2146435072, 8(%esp)
        jne       ..B2.4
..B2.2:
        cmpl      $0, 4(%esp)
        jne       ..B2.4
..B2.3:
        movl      $1, %eax
        ret       
..B2.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_ispinf,@function
	.size	__libm_internal_ispinf,.-__libm_internal_ispinf
	.data
# -- End  __libm_internal_ispinf
	.text
# -- Begin  __libm_internal_isninf
	.text
       .align    16,0x90
	.hidden __libm_internal_isninf
	.globl __libm_internal_isninf
__libm_internal_isninf:
# parameter 1: 4 + %esp
..B3.1:
..L3:

        cmpl      $-1048576, 8(%esp)
        jne       ..B3.4
..B3.2:
        cmpl      $0, 4(%esp)
        jne       ..B3.4
..B3.3:
        movl      $1, %eax
        ret       
..B3.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isninf,@function
	.size	__libm_internal_isninf,.-__libm_internal_isninf
	.data
# -- End  __libm_internal_isninf
	.text
# -- Begin  __libm_internal_isnan
	.text
       .align    16,0x90
	.hidden __libm_internal_isnan
	.globl __libm_internal_isnan
__libm_internal_isnan:
# parameter 1: 4 + %esp
..B4.1:
..L4:

        movl      8(%esp), %edx
        movl      %edx, %eax
        andl      $2146435072, %eax
        cmpl      $2146435072, %eax
        jne       ..B4.5
..B4.2:
        testl     $1048575, %edx
        jne       ..B4.4
..B4.3:
        cmpl      $0, 4(%esp)
        je        ..B4.5
..B4.4:
        movl      $1, %eax
        ret       
..B4.5:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isnan,@function
	.size	__libm_internal_isnan,.-__libm_internal_isnan
	.data
# -- End  __libm_internal_isnan
	.text
# -- Begin  __libm_internal_isposzero
	.text
       .align    16,0x90
	.hidden __libm_internal_isposzero
	.globl __libm_internal_isposzero
__libm_internal_isposzero:
# parameter 1: 4 + %esp
..B5.1:
..L5:

        cmpl      $0, 8(%esp)
        jne       ..B5.4
..B5.2:
        cmpl      $0, 4(%esp)
        jne       ..B5.4
..B5.3:
        movl      $1, %eax
        ret       
..B5.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isposzero,@function
	.size	__libm_internal_isposzero,.-__libm_internal_isposzero
	.data
# -- End  __libm_internal_isposzero
	.text
# -- Begin  __libm_internal_iszero
	.text
       .align    16,0x90
	.hidden __libm_internal_iszero
	.globl __libm_internal_iszero
__libm_internal_iszero:
# parameter 1: 4 + %esp
..B6.1:
..L6:

        testl     $2147483647, 8(%esp)
        jne       ..B6.4
..B6.2:
        cmpl      $0, 4(%esp)
        jne       ..B6.4
..B6.3:
        movl      $1, %eax
        ret       
..B6.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_iszero,@function
	.size	__libm_internal_iszero,.-__libm_internal_iszero
	.data
# -- End  __libm_internal_iszero
	.text
# -- Begin  __libm_internal_isnegzero
	.text
       .align    16,0x90
	.hidden __libm_internal_isnegzero
	.globl __libm_internal_isnegzero
__libm_internal_isnegzero:
# parameter 1: 4 + %esp
..B7.1:
..L7:

        cmpl      $-2147483648, 8(%esp)
        jne       ..B7.4
..B7.2:
        cmpl      $0, 4(%esp)
        jne       ..B7.4
..B7.3:
        movl      $1, %eax
        ret       
..B7.4:
        xorl      %eax, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isnegzero,@function
	.size	__libm_internal_isnegzero,.-__libm_internal_isnegzero
	.data
# -- End  __libm_internal_isnegzero
	.text
# -- Begin  __libm_internal_isnzfinite
	.text
       .align    16,0x90
	.hidden __libm_internal_isnzfinite
	.globl __libm_internal_isnzfinite
__libm_internal_isnzfinite:
# parameter 1: 4 + %esp
..B8.1:
..L8:

        movl      8(%esp), %edx
        movl      %edx, %eax
        andl      $2146435072, %eax
        cmpl      $2146435072, %eax
        jae       ..B8.3
..B8.2:
        andl      $2147483647, %edx
        orl       4(%esp), %edx
        jne       ..B8.4
..B8.3:
        xorl      %eax, %eax
        ret       
..B8.4:
        movl      $1, %eax
        ret       
        .align    16,0x90
	.type	__libm_internal_isnzfinite,@function
	.size	__libm_internal_isnzfinite,.-__libm_internal_isnzfinite
	.data
# -- End  __libm_internal_isnzfinite
	.text
# -- Begin  __libm_internal_isfinite
	.text
       .align    16,0x90
	.hidden __libm_internal_isfinite
	.globl __libm_internal_isfinite
__libm_internal_isfinite:
# parameter 1: 4 + %esp
..B9.1:
..L9:

        movl      8(%esp), %eax
        andl      $2146435072, %eax
        cmpl      $2146435072, %eax
        movl      $0, %eax
        setb      %al
        ret       
        .align    16,0x90
	.type	__libm_internal_isfinite,@function
	.size	__libm_internal_isfinite,.-__libm_internal_isfinite
	.data
# -- End  __libm_internal_isfinite
	.data
	.section .note.GNU-stack, ""
# End
