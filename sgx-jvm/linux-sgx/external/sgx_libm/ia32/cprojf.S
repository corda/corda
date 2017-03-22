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
	.file "cprojf.c"
	.text
..TXTST0:
# -- Begin  cprojf
	.text
       .align    16,0x90
	.globl cprojf
cprojf:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movzwl    18(%esp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        je        ..B1.10
..B1.2:
        movzwl    22(%esp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        je        ..B1.4
..B1.3:
        movl      16(%esp), %eax
        movl      20(%esp), %edx
        addl      $12, %esp
        ret       
..B1.4:
        testl     $8388607, 20(%esp)
        jne       ..B1.3
..B1.5:
        xorl      %eax, %eax
        movl      %eax, 4(%esp)
        movl      %eax, (%esp)
..B1.6:
        movzbl    23(%esp), %eax
        testl     $128, %eax
        movl      $2139095040, (%esp)
        je        ..B1.8
..B1.7:
        movl      $-2147483648, 4(%esp)
        jmp       ..B1.9
..B1.8:
        xorl      %eax, %eax
        movl      %eax, 4(%esp)
..B1.9:
        movl      (%esp), %eax
        movl      4(%esp), %edx
        addl      $12, %esp
        ret       
..B1.10:
        testl     $8388607, 16(%esp)
        je        ..B1.5
        jmp       ..B1.2
        .align    16,0x90
	.type	cprojf,@function
	.size	cprojf,.-cprojf
	.data
# -- End  cprojf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.5:
	.long	0x7f800000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,4
	.align 4
.L_2il0floatpacket.6:
	.long	0x80000000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,4
	.data
	.section .note.GNU-stack, ""
# End
