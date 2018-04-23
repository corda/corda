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
	.file "nexttoward_wmt.c"
	.text
..TXTST0:
# -- Begin  nexttoward
	.text
       .align    16,0x90
	.globl nexttoward
nexttoward:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $20, %esp
        movzwl    14(%ebp), %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        ..B1.23
..B1.2:
        movzwl    24(%ebp), %ebx
        andl      $32767, %ebx
        cmpl      $32767, %ebx
        je        ..B1.20
..B1.3:
        fldl      8(%ebp)
        fldt      16(%ebp)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.4
        je        ..B1.25
..B1.4:
        fstp      %st(0)
        movl      12(%ebp), %ecx
        movl      %ecx, %eax
        andl      $2147483647, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        orl       8(%ebp), %eax
        je        ..B1.19
..B1.5:
        fstpt     8(%esp)
        movzbl    17(%esp), %edi
        movzbl    25(%ebp), %esi
        andl      $128, %edi
        andl      $128, %esi
        shrl      $7, %edi
        shrl      $7, %esi
        movl      8(%ebp), %eax
        cmpl      %edi, %esi
        jne       ..B1.11
..B1.6:
        movzwl    16(%esp), %esi
        andl      $32767, %esi
        cmpl      %ebx, %esi
        jg        ..B1.11
..B1.7:
        jne       ..B1.12
..B1.8:
        movl      12(%esp), %ebx
        movl      20(%ebp), %esi
        cmpl      %esi, %ebx
        ja        ..B1.11
..B1.9:
        jne       ..B1.12
..B1.10:
        movl      8(%esp), %ebx
        cmpl      16(%ebp), %ebx
        jbe       ..B1.12
..B1.11:
        addl      $-1, %eax
        movl      %eax, 8(%esp)
        adcl      $-1, %ecx
        movl      %ecx, 12(%esp)
        jmp       ..B1.13
..B1.12:
        addl      $1, %eax
        movl      %eax, 8(%esp)
        adcl      $0, %ecx
        movl      %ecx, 12(%esp)
..B1.13:
        movl      12(%esp), %ecx
        andl      $2147483647, %ecx
        lea       -1048576(%ecx), %eax
        cmpl      $2145386496, %eax
        jb        ..B1.18
..B1.14:
        cmpl      $1048576, %ecx
        jae       ..B1.16
..B1.15:
        fldl      _small_value_64@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpl     (%esp)
        fldl      8(%esp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        fldl      _large_value_64@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpl     (%esp)
        fldl      8(%esp)
..B1.17:
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.18:
        fldl      8(%esp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        fstp      %st(0)
        movzbl    25(%ebp), %eax
        fldl      _small_value_64@GOTOFF(%edx)
        andl      $128, %eax
        shrl      $7, %eax
        fmul      %st(0), %st
        movl      _smallest_value_64@GOTOFF(%edx,%eax,8), %ecx
        movl      4+_smallest_value_64@GOTOFF(%edx,%eax,8), %ebx
        movl      %ecx, 8(%esp)
        movl      %ebx, 12(%esp)
        fstpl     (%esp)
        jmp       ..B1.18
..B1.20:
        cmpl      $-2147483648, 20(%ebp)
        jne       ..B1.22
..B1.21:
        cmpl      $0, 16(%ebp)
        je        ..B1.3
..B1.22:
        fldt      16(%ebp)
        fmull     8(%ebp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        testl     $1048575, 12(%ebp)
        jne       ..B1.22
..B1.24:
        cmpl      $0, 8(%ebp)
        jne       ..B1.22
        jmp       ..B1.2
..B1.25:
        fstp      %st(1)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	nexttoward,@function
	.size	nexttoward,.-nexttoward
	.data
# -- End  nexttoward
	.section .rodata, "a"
	.align 4
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.data
	.section .note.GNU-stack, ""
# End
