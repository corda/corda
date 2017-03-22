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
	.file "fabsl.c"
	.text
..TXTST0:
# -- Begin  fabsl
	.text
       .align    16,0x90
	.globl fabsl
fabsl:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        subl      $28, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.15
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.5:
        fldcw     12(%esp)
..B1.6:
        movl      $1, %eax
..B1.7:
        movzwl    40(%esp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.12
..B1.8:
        fldt      32(%esp)
        movzbl    41(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        fmull     ones@GOTOFF(%edx,%ecx,8)
        fstpt     (%esp)
..B1.9:
        testl     %eax, %eax
        je        ..B1.11
..B1.10:
        fldcw     14(%esp)
..B1.11:
        fldt      (%esp)
        addl      $28, %esp
        ret       
..B1.12:
        cmpl      $-2147483648, 36(%esp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, 32(%esp)
        je        ..B1.8
..B1.14:
        fldt      32(%esp)
        movzbl    41(%esp), %edx
        fstpt     (%esp)
        andl      $127, %edx
        movb      %dl, 9(%esp)
        jmp       ..B1.9
..B1.15:
        xorl      %eax, %eax
        jmp       ..B1.7
        .align    16,0x90
	.type	fabsl,@function
	.size	fabsl,.-fabsl
	.data
# -- End  fabsl
	.section .rodata, "a"
	.align 8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.data
	.section .note.GNU-stack, ""
# End
