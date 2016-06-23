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
# parameter 1: 8 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_isfinitel.1:
..L2:

        xorl      %eax, %eax
        movzwl    16(%rsp), %ecx
        shll      $16, %ecx
        movzwl    14(%rsp), %edx
        orl       %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $2147418112, %ecx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: 8 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___isfinitel.4:
..L5:

        xorl      %eax, %eax
        movzwl    16(%rsp), %ecx
        shll      $16, %ecx
        movzwl    14(%rsp), %edx
        orl       %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $2147418112, %ecx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value_isfinitef.7:
..L8:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value___isfinitef.10:
..L11:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B5.1:
	.cfi_startproc
..___tag_value_isfinite.13:
..L14:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B6.1:
	.cfi_startproc
..___tag_value___isfinite.16:
..L17:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B7.1:
	.cfi_startproc
..___tag_value_isfinited.19:
..L20:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B8.1:
	.cfi_startproc
..___tag_value___isfinited.22:
..L23:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: 8 + %rsp
..B9.1:
	.cfi_startproc
..___tag_value_finitel.25:
..L26:

        xorl      %eax, %eax
        movzwl    16(%rsp), %ecx
        shll      $16, %ecx
        movzwl    14(%rsp), %edx
        orl       %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $2147418112, %ecx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: 8 + %rsp
..B10.1:
	.cfi_startproc
..___tag_value___finitel.28:
..L29:

        xorl      %eax, %eax
        movzwl    16(%rsp), %ecx
        shll      $16, %ecx
        movzwl    14(%rsp), %edx
        orl       %edx, %ecx
        andl      $2147483647, %ecx
        cmpl      $2147418112, %ecx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B11.1:
	.cfi_startproc
..___tag_value_finitef.31:
..L32:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B12.1:
	.cfi_startproc
..___tag_value___finitef.34:
..L35:

        xorl      %eax, %eax
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B13.1:
	.cfi_startproc
..___tag_value_finite.37:
..L38:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B14.1:
	.cfi_startproc
..___tag_value___finite.40:
..L41:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B15.1:
	.cfi_startproc
..___tag_value_finited.43:
..L44:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
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
# parameter 1: %xmm0
..B16.1:
	.cfi_startproc
..___tag_value___finited.46:
..L47:

        xorl      %eax, %eax
        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        setb      %al
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__finited,@function
	.size	__finited,.-__finited
	.data
# -- End  __finited
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
