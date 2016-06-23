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
	.file "nan.c"
	.text
..TXTST0:
# -- Begin  nanf
	.text
       .align    16,0x90
	.globl nanf
nanf:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        subl      $24, %esp
        pushl     28(%esp)
        call      __libm_conv_strtoull
..B1.2:
        andl      $4194303, %eax
        orl       $2143289344, %eax
        movl      %eax, 16(%esp)
        flds      16(%esp)
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	nanf,@function
	.size	nanf,.-nanf
	.data
# -- End  nanf
	.text
# -- Begin  nan
	.text
       .align    16,0x90
	.globl nan
nan:
# parameter 1: 32 + %esp
..B2.1:
..L2:

        subl      $24, %esp
        pushl     28(%esp)
        call      __libm_conv_strtoull
..B2.2:
        andl      $524287, %edx
        orl       $2146959360, %edx
        movl      %eax, 16(%esp)
        movl      %edx, 20(%esp)
        fldl      16(%esp)
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	nan,@function
	.size	nan,.-nan
	.data
# -- End  nan
	.text
# -- Begin  nanl
	.text
       .align    16,0x90
	.globl nanl
nanl:
# parameter 1: 32 + %esp
..B3.1:
..L3:

        subl      $24, %esp
        pushl     28(%esp)
        call      __libm_conv_strtoull
..B3.2:
        movl      %eax, 16(%esp)
        orl       $-1073741824, %edx
        movl      $32767, %eax
        movl      %edx, 20(%esp)
        movw      %ax, 24(%esp)
        fldt      16(%esp)
        addl      $28, %esp
        ret       
        .align    16,0x90
	.type	nanl,@function
	.size	nanl,.-nanl
	.data
# -- End  nanl
	.data
	.hidden __libm_conv_strtoull
	.section .note.GNU-stack, ""
# End
