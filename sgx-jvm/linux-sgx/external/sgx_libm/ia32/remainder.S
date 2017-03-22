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
	.file "remainder.c"
	.text
..TXTST0:
# -- Begin  remainder
	.text
       .align    16,0x90
	.globl remainder
remainder:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        movzwl    22(%ebp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        testl     %ecx, %ecx
        jne       ..B1.4
..B1.2:
        testl     $1048575, 20(%ebp)
        jne       ..B1.4
..B1.3:
        cmpl      $0, 16(%ebp)
        je        ..B1.9
..B1.4:
        movzwl    14(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        je        ..B1.28
..B1.5:
        cmpl      $2047, %ecx
        je        ..B1.14
..B1.7:
        fldl      16(%ebp)
        fldl      8(%ebp)
.L_2TAG_PACKET_0.0.2:
        fprem1    
        fstsw     %ax
        sahf      
        jp        .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        fstpl     (%esp)
..B1.8:
        fldl      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        movzwl    14(%ebp), %edx
        andl      $32752, %edx
        cmpl      $32752, %edx
        je        ..B1.11
..B1.10:
        fldl      _infs@GOTOFF(%eax)
        fmull     _zeros@GOTOFF(%eax)
        fstl      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        testl     $1048575, 12(%ebp)
        jne       ..B1.13
..B1.12:
        cmpl      $0, 8(%ebp)
        je        ..B1.10
..B1.13:
        fldl      8(%ebp)
        faddl     16(%ebp)
        fstl      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        testl     $1048575, 20(%ebp)
        jne       ..B1.19
..B1.15:
        cmpl      $0, 16(%ebp)
        jne       ..B1.19
..B1.16:
        cmpl      $2047, %edx
        je        ..B1.25
..B1.17:
        cmpl      $2047, %ecx
        je        ..B1.20
        jmp       ..B1.7
..B1.19:
        fldl      8(%ebp)
        faddl     16(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        testl     $1048575, 20(%ebp)
        jne       ..B1.7
..B1.21:
        cmpl      $0, 16(%ebp)
        jne       ..B1.7
..B1.22:
        fldl      8(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        testl     $1048575, 12(%ebp)
        jne       ..B1.17
..B1.26:
        cmpl      $0, 8(%ebp)
        jne       ..B1.17
..B1.27:
        fldl      _infs@GOTOFF(%eax)
        fmull     _zeros@GOTOFF(%eax)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        testl     $1048575, 12(%ebp)
        jne       ..B1.19
..B1.29:
        cmpl      $0, 8(%ebp)
        jne       ..B1.19
..B1.30:
        cmpl      $2047, %ecx
        je        ..B1.14
        jmp       ..B1.16
        .align    16,0x90
	.type	remainder,@function
	.size	remainder,.-remainder
	.data
# -- End  remainder
	.section .rodata, "a"
	.align 4
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
# End

	.globl drem
	.equ drem, remainder

