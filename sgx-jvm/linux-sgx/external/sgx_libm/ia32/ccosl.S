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
	.file "ccosl.c"
	.text
..TXTST0:
# -- Begin  ccosl
	.text
       .align    16,0x90
	.globl ccosl
ccosl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $84, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.5@GOTOFF(%ebx)
        movl      %edx, 76(%esp)
        fstpt     4(%esp)
        fldt      4(%esp)
        fstpt     16(%esp)
        fldt      16(%esp)
        fstpt     28(%esp)
        fldt      28(%esp)
        fstpt     40(%esp)
..B1.2:
        fnstcw    2(%esp)
..B1.3:
        movzwl    2(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.16
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, (%esp)
..B1.5:
        fldcw     (%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        fldt      12(%ebp)
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        fstpt     40(%esp)
        cmpl      $32767, %edx
        fldt      24(%ebp)
        fstpt     28(%esp)
        jl        ..B1.10
..B1.8:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.11
..B1.9:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
..B1.10:
        movzbl    33(%ebp), %ecx
        notl      %ecx
        movzbl    37(%esp), %edx
        andl      $128, %ecx
        andl      $127, %edx
        orl       %ecx, %edx
        movb      %dl, 37(%esp)
..B1.11:
        addl      $-32, %esp
        lea       84(%esp), %ecx
        movl      %ecx, (%esp)
        lea       4(%esp), %edx
        movl      -24(%ecx), %ecx
        movl      %ecx, (%edx)
        movl      64(%esp), %ecx
        movl      %ecx, 4(%edx)
        movl      68(%esp), %ecx
        movl      %ecx, 8(%edx)
        movl      72(%esp), %ecx
        movl      %ecx, 12(%edx)
        movl      76(%esp), %ecx
        movl      %ecx, 16(%edx)
        movl      80(%esp), %ecx
        movl      %ecx, 20(%edx)
        call      ccoshl@PLT
..B1.19:
        addl      $28, %esp
        fldt      52(%esp)
        testl     %edi, %edi
        fstpt     4(%esp)
        fldt      64(%esp)
        fstpt     16(%esp)
        je        ..B1.13
..B1.12:
        fldcw     2(%esp)
..B1.13:
        movl      4(%esp), %edx
        movl      %edx, (%esi)
        movl      16(%esp), %edx
        movl      %edx, 12(%esi)
        movl      20(%esp), %edx
        movl      %edx, 16(%esi)
        movl      8(%esp), %ecx
        movl      12(%esp), %edi
        movl      24(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      76(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.15
..B1.14:
        movl      %esi, %eax
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.15:
        call      __stack_chk_fail@PLT
..B1.16:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	ccosl,@function
	.size	ccosl,.-ccosl
	.data
# -- End  ccosl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.data
	.section .note.GNU-stack, ""
# End
