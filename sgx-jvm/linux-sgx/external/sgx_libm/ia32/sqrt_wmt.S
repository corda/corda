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
	.file "sqrt_wmt.c"
	.text
..TXTST0:
# -- Begin  sqrt
	.text
       .align    16,0x90
	.globl sqrt
sqrt:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
        fldl      8(%ebp)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      12(%ebp), %ecx
        cmpl      $2146435072, %ecx
        movl      %edx, 16(%esp)
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        fstl      (%esp)
        ja        ..B1.5
..B1.2:
        fstpl     8(%esp)
        movsd     8(%esp), %xmm0
        sqrtsd    %xmm0, %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
        movl      16(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.4
..B1.3:
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.4:
        fstp      %st(0)
        movl      %eax, %ebx
        call      __stack_chk_fail@PLT
..B1.5:
        cmpl      $0, (%esp)
        movl      $0, %edx
        setne     %dl
        orl       %edx, %ecx
        movl      %ecx, %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        ja        ..B1.12
..B1.6:
        cmpl      $-2147483648, %ecx
        je        ..B1.8
..B1.7:
        fstp      %st(0)
        movsd     _ConstantTable@GOTOFF(%eax), %xmm0
        mulsd     16+_ConstantTable@GOTOFF(%eax), %xmm0
        movsd     %xmm0, (%esp)
        fldl      (%esp)
..B1.8:
        movl      16(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.4
        jmp       ..B1.3
..B1.12:
        movl      16(%esp), %edx
        fadd      %st(0), %st
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.4
        jmp       ..B1.3
        .align    16,0x90
	.type	sqrt,@function
	.size	sqrt,.-sqrt
	.data
# -- End  sqrt
	.section .rodata, "a"
	.align 16
	.align 16
_ConstantTable:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_ConstantTable,@object
	.size	_ConstantTable,32
	.data
	.section .note.GNU-stack, ""
# End
