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
	.file "cprojl.c"
	.text
..TXTST0:
# -- Begin  cprojl
	.text
       .align    16,0x90
	.globl cprojl
cprojl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %ebx
        subl      $28, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        movl      %gs:20, %edx
        movl      8(%ebp), %eax
        xorl      %esp, %edx
        movl      %edx, 24(%esp)
        cmpl      $32767, %ecx
        movzbl    9+.L_2il0floatpacket.7@GOTOFF(%ebx), %edx
        je        ..B1.11
..B1.2:
        movzwl    32(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.7
..B1.3:
        fstp      %st(0)
        fldt      12(%ebp)
        fstpt     (%esp)
        fldt      24(%ebp)
        fstpt     12(%esp)
..B1.4:
        movl      (%esp), %edx
        movl      4(%esp), %ecx
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      8(%esp), %edx
        movl      12(%esp), %ecx
        movl      %edx, 8(%eax)
        movl      %ecx, 12(%eax)
        movl      16(%esp), %edx
        movl      20(%esp), %ecx
        movl      %edx, 16(%eax)
        movl      %ecx, 20(%eax)
        movl      24(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.6
..B1.5:
        addl      $28, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.6:
        call      __stack_chk_fail@PLT
..B1.7:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.3
..B1.8:
        cmpl      $0, 24(%ebp)
        jne       ..B1.3
..B1.9:
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpt     (%esp)
        fldt      (%esp)
..B1.10:
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        andl      $127, %edx
        movzwl    8+.L_2il0floatpacket.8@GOTOFF(%ebx), %ecx
        fstpt     (%esp)
        orl       $-32769, %ecx
        movw      %cx, 8(%esp)
        movzbl    33(%ebp), %ecx
        andl      $-128, %ecx
        fstpt     12(%esp)
        orl       %ecx, %edx
        movb      %dl, 21(%esp)
        jmp       ..B1.4
..B1.11:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.2
..B1.12:
        cmpl      $0, 12(%ebp)
        je        ..B1.9
        jmp       ..B1.2
        .align    16,0x90
	.type	cprojl,@function
	.size	cprojl,.-cprojl
	.data
# -- End  cprojl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.data
	.section .note.GNU-stack, ""
# End
