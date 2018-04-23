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
	.file "fminl.c"
	.text
..TXTST0:
# -- Begin  fminl
	.text
       .align    16,0x90
	.globl fminl
fminl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.33
..B1.4:
        movzwl    28(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.16
..B1.5:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.15
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.7:
        fldcw     12(%esp)
..B1.8:
        movl      $1, %edx
..B1.9:
        fldt      8(%ebp)
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      20(%ebp)
        fcom      %st(1)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jbe       ..B1.11
..B1.10:
        fstp      %st(0)
        jmp       ..B1.12
..B1.11:
        fstpt     16(%esp)
..B1.12:
        testl     %edx, %edx
        je        ..B1.14
..B1.13:
        fldcw     14(%esp)
..B1.14:
        fldt      16(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        xorl      %edx, %edx
        jmp       ..B1.9
..B1.16:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.18
..B1.17:
        cmpl      $0, 20(%ebp)
        je        ..B1.5
..B1.18:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.32
..B1.19:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.20:
        fldcw     12(%esp)
..B1.21:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
..B1.22:
        fldcw     14(%esp)
        movzwl    16(%ebp), %eax
..B1.23:
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.25
..B1.24:
        fldt      8(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.27
..B1.26:
        cmpl      $0, 8(%ebp)
        je        ..B1.24
..B1.27:
        movzwl    28(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.29
..B1.28:
        fldt      20(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.31
..B1.30:
        cmpl      $0, 20(%ebp)
        je        ..B1.28
..B1.31:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.32:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.23
..B1.33:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.18
..B1.34:
        cmpl      $0, 8(%ebp)
        je        ..B1.4
        jmp       ..B1.18
        .align    16,0x90
	.type	fminl,@function
	.size	fminl,.-fminl
	.data
# -- End  fminl
	.data
	.section .note.GNU-stack, ""
# End
