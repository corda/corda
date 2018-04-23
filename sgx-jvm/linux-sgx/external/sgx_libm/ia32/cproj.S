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
	.file "cproj.c"
	.text
..TXTST0:
# -- Begin  cproj
	.text
       .align    16,0x90
	.globl cproj
cproj:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
        fldz      
        movl      %gs:20, %ecx
        movl      8(%ebp), %eax
        xorl      %esp, %ecx
        movl      %ecx, 16(%esp)
        movzwl    18(%ebp), %ecx
        andl      $32752, %ecx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $32752, %ecx
        je        ..B1.15
..B1.2:
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        cmpl      $32752, %ecx
        je        ..B1.6
..B1.3:
        fstp      %st(0)
        movl      12(%ebp), %ecx
        movl      %ecx, (%eax)
        movl      16(%ebp), %ecx
        movl      %ecx, 4(%eax)
        movl      20(%ebp), %ecx
        movl      %ecx, 8(%eax)
        movl      24(%ebp), %ecx
        movl      %ecx, 12(%eax)
        movl      16(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.5
..B1.4:
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.5:
        movl      %edx, %ebx
        call      __stack_chk_fail@PLT
..B1.6:
        testl     $1048575, 24(%ebp)
        jne       ..B1.3
..B1.7:
        cmpl      $0, 20(%ebp)
        jne       ..B1.3
..B1.8:
        fstl      8(%esp)
        fstpl     (%esp)
..B1.9:
        fldl      DBL_PINFA@GOTOFF(%edx)
        movzbl    27(%ebp), %ecx
        testl     $128, %ecx
        fstpl     (%esp)
        je        ..B1.11
..B1.10:
        fldl      DBL_NZEROA@GOTOFF(%edx)
        fstpl     8(%esp)
        jmp       ..B1.12
..B1.11:
        fldl      DBL_PZEROA@GOTOFF(%edx)
        fstpl     8(%esp)
..B1.12:
        movl      (%esp), %ecx
        movl      %ecx, (%eax)
        movl      4(%esp), %ecx
        movl      %ecx, 4(%eax)
        movl      8(%esp), %ecx
        movl      %ecx, 8(%eax)
        movl      12(%esp), %ecx
        movl      %ecx, 12(%eax)
        movl      16(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.5
        jmp       ..B1.4
..B1.15:
        testl     $1048575, 16(%ebp)
        jne       ..B1.2
..B1.16:
        cmpl      $0, 12(%ebp)
        je        ..B1.8
        jmp       ..B1.2
        .align    16,0x90
	.type	cproj,@function
	.size	cproj,.-cproj
	.data
# -- End  cproj
	.section .rodata, "a"
	.align 16
	.align 16
DBL_PINFA:
	.long	0
	.long	2146435072
	.type	DBL_PINFA,@object
	.size	DBL_PINFA,8
	.space 8, 0x00 	# pad
	.align 16
DBL_NZEROA:
	.long	0
	.long	2147483648
	.type	DBL_NZEROA,@object
	.size	DBL_NZEROA,8
	.space 8, 0x00 	# pad
	.align 16
DBL_PZEROA:
	.long	0
	.long	0
	.type	DBL_PZEROA,@object
	.size	DBL_PZEROA,8
	.data
	.section .note.GNU-stack, ""
# End
