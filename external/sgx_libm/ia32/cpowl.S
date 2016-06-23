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
	.file "cpowl.c"
	.text
..TXTST0:
# -- Begin  cpowl
	.text
       .align    16,0x90
	.globl cpowl
cpowl:
# parameter 1: 12 + %ebp
# parameter 2: 36 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $104, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %eax
        xorl      %esp, %eax
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        movl      %eax, 100(%esp)
        fstpt     4(%esp)
        fldt      4(%esp)
        fstpt     16(%esp)
..B1.2:
        fnstcw    2(%esp)
..B1.3:
        movzwl    2(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.4:
        orl       $-64768, %edx
        movw      %dx, (%esp)
..B1.5:
        fldcw     (%esp)
..B1.6:
        movl      $1, %esi
..B1.7:
        addl      $-32, %esp
        movl      12(%ebp), %ecx
        lea       60(%esp), %edx
        movl      %edx, (%esp)
        lea       4(%esp), %eax
        movl      16(%ebp), %edx
        movl      %ecx, (%eax)
        movl      %edx, 4(%eax)
        movl      20(%ebp), %ecx
        movl      24(%ebp), %edx
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        movl      28(%ebp), %ecx
        movl      32(%ebp), %edx
        movl      %ecx, 16(%eax)
        movl      %edx, 20(%eax)
        call      clogl@PLT
..B1.16:
        fldt      36(%ebp)
        addl      $28, %esp
        fld       %st(0)
        fldt      48(%ebp)
        fld       %st(0)
        fldt      28(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        fldt      40(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     76(%esp)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     88(%esp)
        addl      $-32, %esp
        movl      108(%esp), %ecx
        lea       84(%esp), %edx
        movl      %edx, (%esp)
        lea       4(%esp), %eax
        movl      28(%edx), %edx
        movl      %ecx, (%eax)
        movl      %edx, 4(%eax)
        movl      116(%esp), %ecx
        movl      120(%esp), %edx
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        movl      124(%esp), %ecx
        movl      128(%esp), %edx
        movl      %ecx, 16(%eax)
        movl      %edx, 20(%eax)
        call      cexpl@PLT
..B1.15:
        addl      $28, %esp
        fldt      52(%esp)
        testl     %esi, %esi
        fstpt     4(%esp)
        fldt      64(%esp)
        fstpt     16(%esp)
        je        ..B1.9
..B1.8:
        fldcw     2(%esp)
..B1.9:
        movl      8(%ebp), %eax
        movl      4(%esp), %edx
        movl      8(%esp), %ecx
        movl      12(%esp), %esi
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      %esi, 8(%eax)
        movl      16(%esp), %edx
        movl      20(%esp), %ecx
        movl      24(%esp), %esi
        movl      %edx, 12(%eax)
        movl      %ecx, 16(%eax)
        movl      %esi, 20(%eax)
        movl      100(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.11
..B1.10:
        addl      $104, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.11:
        call      __stack_chk_fail@PLT
..B1.12:
        xorl      %esi, %esi
        jmp       ..B1.7
        .align    16,0x90
	.type	cpowl,@function
	.size	cpowl,.-cpowl
	.data
# -- End  cpowl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.data
	.section .note.GNU-stack, ""
# End
