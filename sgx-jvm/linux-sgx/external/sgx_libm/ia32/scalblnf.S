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
	.file "scalblnf.c"
	.text
..TXTST0:
# -- Begin  scalblnf
	.text
       .align    16,0x90
	.globl scalblnf
scalblnf:
# parameter 1: 16 + %esp
# parameter 2: 20 + %esp
..B1.1:
..L1:

        pushl     %ebx
        pushl     %ebp
        pushl     %esi
        movzwl    18(%esp), %eax
        movl      %eax, %ecx
        andl      $32640, %ecx
        shrl      $7, %ecx
        movl      20(%esp), %edx
        cmpl      $255, %ecx
        je        ..B1.15
..B1.2:
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        testl     %ecx, %ecx
        jne       ..B1.5
..B1.3:
        testl     $8388607, 16(%esp)
        je        ..B1.9
..B1.4:
        flds      16(%esp)
        fmuls     .L_2il0floatpacket.0@GOTOFF(%ebx)
        fstps     16(%esp)
        movzwl    18(%esp), %eax
        movl      %eax, %ecx
        andl      $32640, %ecx
        shrl      $7, %ecx
        addl      $-25, %ecx
..B1.5:
        cmpl      $65536, %edx
        jle       ..L3
        movl      $65536, %edx
..L3:
        cmpl      $-65536, %edx
        jg        ..L4
        movl      $-65536, %edx
..L4:
        lea       (%ecx,%edx), %ebp
        testl     %ebp, %ebp
        jle       ..B1.11
..B1.6:
        cmpl      $255, %ebp
        jge       ..B1.10
..B1.7:
        andl      $255, %ebp
        andl      $-32641, %eax
        shll      $7, %ebp
        orl       %ebp, %eax
        movw      %ax, 18(%esp)
        flds      16(%esp)
..B1.8:
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
..B1.9:
        flds      16(%esp)
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
..B1.10:
        movzbl    19(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        flds      _large_value_32@GOTOFF(%ebx,%eax,4)
        fmuls     .L_2il0floatpacket.1@GOTOFF(%ebx)
        fsts      (%esp)
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
..B1.11:
        cmpl      $-23, %ebp
        jl        ..B1.14
..B1.12:
        movl      16(%esp), %eax
        lea       25(%ecx,%edx), %edx
        movl      %eax, (%esp)
        shrl      $16, %eax
        movzbl    %dl, %edx
        andl      $-32641, %eax
        shll      $7, %edx
        orl       %edx, %eax
        movw      %ax, 2(%esp)
        flds      (%esp)
        fmuls     .L_2il0floatpacket.3@GOTOFF(%ebx)
        fsts      (%esp)
..B1.13:
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
..B1.14:
        movzbl    19(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        flds      _small_value_32@GOTOFF(%ebx,%eax,4)
        fmuls     .L_2il0floatpacket.2@GOTOFF(%ebx)
        fsts      (%esp)
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
..B1.15:
        flds      16(%esp)
        popl      %ecx
        popl      %ebp
        popl      %ebx
        ret       
        .align    16,0x90
	.type	scalblnf,@function
	.size	scalblnf,.-scalblnf
	.data
# -- End  scalblnf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4c000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x0d800000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x33000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.data
	.section .note.GNU-stack, ""
# End
