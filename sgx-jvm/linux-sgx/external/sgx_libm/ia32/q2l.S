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
	.file "q2l.c"
	.text
..TXTST0:
# -- Begin  __qtol
	.text
       .align    16,0x90
	.globl __qtol
__qtol:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        movl      8(%ebp), %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        movl      12(%eax), %edx
        movl      8(%eax), %esi
        movl      %ecx, 16(%esp)
        movl      (%eax), %ecx
        movl      4(%eax), %ebx
        movl      %edx, %eax
        movl      %edx, 12(%esp)
        movzwl    %dx, %edi
        movl      %esi, %edx
        shrl      $17, %edx
        shll      $15, %edi
        orl       %edi, %edx
        movl      %ebx, %edi
        shll      $15, %esi
        shrl      $17, %edi
        orl       %edi, %esi
        movl      %ecx, %edi
        shrl      $16, %eax
        shrl      $17, %edi
        shll      $15, %ebx
        movl      %eax, 24(%esp)
        andl      $32767, %eax
        shll      $15, %ecx
        orl       %ebx, %edi
        movl      %edi, 28(%esp)
        cmpl      $32767, %eax
        jne       ..B1.7
..B1.2:
        movl      %edi, %eax
        orl       %esi, %ecx
        orl       %edx, %eax
        orl       %eax, %ecx
        je        ..B1.6
..B1.3:
        movl      %edx, %eax
        xorl      %ecx, %ecx
        andl      $1073741824, %eax
        orl       %eax, %ecx
        jne       ..B1.5
..B1.4:
        movl      16(%esp), %eax
        movl      p_zero@GOTOFF(%eax), %ecx
        fldl      (%ecx)
        fdiv      %st(0), %st
        fstpl     (%esp)
..B1.5:
        orl       $-1073741824, %edx
..B1.6:
        movl      24(%esp), %eax
        orl       $-2147483648, %edx
        movl      %eax, 16(%esp)
        movl      %esi, 8(%esp)
        movl      %edx, 12(%esp)
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.7:
        testl     %eax, %eax
        je        ..B1.9
..B1.8:
        orl       $-2147483648, %edx
        jmp       ..B1.11
..B1.9:
        movl      %esi, %edi
        movl      %edx, %ebx
        orl       28(%esp), %ebx
        orl       %ecx, %edi
        orl       %ebx, %edi
        je        ..B1.22
..B1.10:
        movl      16(%esp), %edi
        movl      p_tiny@GOTOFF(%edi), %ebx
        fldl      (%ebx)
        movl      p_one@GOTOFF(%edi), %ebx
        fmull     (%ebx)
        fstpl     (%esp)
..B1.11:
        movl      %ecx, %ebx
        orl       28(%esp), %ebx
        je        ..B1.21
..B1.12:
        fnstcw    20(%esp)
..B1.13:
        movl      20(%esp), %ebx
        andl      $3072, %ebx
        movl      %ebx, 8(%esp)
        jne       ..B1.15
..B1.14:
        xorl      %ebx, %ebx
        movl      28(%esp), %edi
        subl      %ebx, %edi
        jl        ..B1.23
        jmp       ..B1.18
..B1.15:
        testl     $-2147483648, 12(%esp)
        je        ..B1.17
..B1.16:
        cmpl      $1024, 8(%esp)
        je        ..B1.23
        jmp       ..B1.18
..B1.17:
        cmpl      $2048, 8(%esp)
        je        ..B1.23
..B1.18:
        testl     %eax, %eax
        jne       ..B1.20
..B1.19:
        movl      16(%esp), %eax
        movl      p_norm@GOTOFF(%eax), %ecx
        fldl      (%ecx)
        fmul      %st(0), %st
        fstpl     (%esp)
        jmp       ..B1.21
..B1.20:
        movl      16(%esp), %eax
        movl      p_one@GOTOFF(%eax), %ecx
        movl      p_norm@GOTOFF(%eax), %ebx
        fldl      (%ecx)
        faddl     (%ebx)
        fstpl     (%esp)
..B1.21:
        movl      24(%esp), %eax
        movl      %eax, 16(%esp)
        movl      %esi, 8(%esp)
        movl      %edx, 12(%esp)
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        movl      24(%esp), %eax
        xorl      %edx, %edx
        movl      %eax, 16(%esp)
        movl      %edx, 8(%esp)
        movl      %edx, 12(%esp)
        fldt      8(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        addl      $1, %esi
        adcl      $0, %edx
        testl     %eax, %eax
        je        ..B1.32
..B1.24:
        movl      %esi, %eax
        orl       %edx, %eax
        jne       ..B1.28
..B1.25:
        movl      24(%esp), %eax
        xorl      %esi, %esi
        incl      %eax
        movl      $-2147483648, %edx
        movl      %eax, 24(%esp)
        andl      $32767, %eax
..B1.26:
        cmpl      $32767, %eax
        jne       ..B1.18
..B1.27:
        movl      16(%esp), %eax
        movl      p_huge@GOTOFF(%eax), %ecx
        fldl      (%ecx)
        fmul      %st(0), %st
        fstpl     (%esp)
        jmp       ..B1.21
..B1.28:
        movl      $-2147483648, %eax
        movl      28(%esp), %ebx
        subl      %eax, %ebx
        orl       %ebx, %ecx
        jne       ..B1.30
..B1.29:
        movl      $1, %eax
        jmp       ..B1.31
..B1.30:
        xorl      %eax, %eax
..B1.31:
        xorl      %ecx, %ecx
        cmpl      $0, 8(%esp)
        sete      %cl
        andl      %ecx, %eax
        notl      %eax
        andl      %eax, %esi
        movl      16(%esp), %eax
        movl      p_one@GOTOFF(%eax), %ebx
        movl      p_norm@GOTOFF(%eax), %eax
        fldl      (%ebx)
        faddl     (%eax)
        fstpl     (%esp)
        jmp       ..B1.21
..B1.32:
        movl      $-2147483648, %eax
        movl      28(%esp), %ebx
        subl      %eax, %ebx
        orl       %ebx, %ecx
        jne       ..B1.34
..B1.33:
        movl      $1, %ecx
        jmp       ..B1.35
..B1.34:
        xorl      %ecx, %ecx
..B1.35:
        xorl      %edi, %edi
        xorl      %eax, %eax
        cmpl      $0, 8(%esp)
        sete      %al
        andl      %eax, %ecx
        notl      %ecx
        andl      %ecx, %esi
        movl      %edx, %ecx
        subl      %edi, %ecx
        jge       ..B1.19
..B1.36:
        movl      24(%esp), %eax
        incl      %eax
        movl      %eax, 24(%esp)
        andl      $32767, %eax
        jmp       ..B1.26
        .align    16,0x90
	.type	__qtol,@function
	.size	__qtol,.-__qtol
	.data
# -- End  __qtol
	.data
	.align 4
	.align 4
p_zero:
	.long	d_zero
	.type	p_zero,@object
	.size	p_zero,4
	.align 4
p_tiny:
	.long	d_tiny
	.type	p_tiny,@object
	.size	p_tiny,4
	.align 4
p_one:
	.long	d_one
	.type	p_one,@object
	.size	p_one,4
	.align 4
p_norm:
	.long	d_norm
	.type	p_norm,@object
	.size	p_norm,4
	.align 4
p_huge:
	.long	d_huge
	.type	p_huge,@object
	.size	p_huge,4
	.section .rodata, "a"
	.align 8
	.align 8
d_zero:
	.long	0x00000000,0x00000000
	.type	d_zero,@object
	.size	d_zero,8
	.align 8
d_tiny:
	.long	0x00000001,0x00000000
	.type	d_tiny,@object
	.size	d_tiny,8
	.align 8
d_one:
	.long	0x00000000,0x3ff00000
	.type	d_one,@object
	.size	d_one,8
	.align 8
d_norm:
	.long	0x00000000,0x00100000
	.type	d_norm,@object
	.size	d_norm,8
	.align 8
d_huge:
	.long	0xffffffff,0x7fefffff
	.type	d_huge,@object
	.size	d_huge,8
	.data
	.section .note.GNU-stack, ""
# End
