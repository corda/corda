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
	.file "nexttowardf_wmt.c"
	.text
..TXTST0:
# -- Begin  nexttowardf
	.text
       .align    16,0x90
	.globl nexttowardf
nexttowardf:
# parameter 1: 32 + %esp
# parameter 2: 36 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        subl      $20, %esp
        movzwl    34(%esp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        je        ..B1.23
..B1.2:
        movzwl    44(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.20
..B1.3:
        flds      32(%esp)
        fldt      36(%esp)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.4
        je        ..B1.24
..B1.4:
        fstp      %st(0)
        movl      32(%esp), %eax
        testl     $2147483647, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        je        ..B1.19
..B1.5:
        fstpt     (%esp)
        movzbl    9(%esp), %edi
        movzbl    45(%esp), %ebx
        andl      $128, %edi
        andl      $128, %ebx
        shrl      $7, %edi
        shrl      $7, %ebx
        cmpl      %edi, %ebx
        jne       ..B1.11
..B1.6:
        movzwl    8(%esp), %ebx
        andl      $32767, %ebx
        cmpl      %ecx, %ebx
        jg        ..B1.11
..B1.7:
        jne       ..B1.12
..B1.8:
        movl      4(%esp), %ecx
        movl      40(%esp), %ebx
        cmpl      %ebx, %ecx
        ja        ..B1.11
..B1.9:
        jne       ..B1.12
..B1.10:
        movl      (%esp), %ecx
        cmpl      36(%esp), %ecx
        jbe       ..B1.12
..B1.11:
        decl      %eax
        movl      %eax, (%esp)
        jmp       ..B1.13
..B1.12:
        incl      %eax
        movl      %eax, (%esp)
..B1.13:
        andl      $2147483647, %eax
        lea       -8388608(%eax), %ecx
        cmpl      $2130706432, %ecx
        jb        ..B1.18
..B1.14:
        cmpl      $8388608, %eax
        jae       ..B1.16
..B1.15:
        flds      .L_2il0floatpacket.0@GOTOFF(%edx)
        fsts      12(%esp)
        flds      12(%esp)
        fmulp     %st, %st(1)
        fstps     12(%esp)
        flds      (%esp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        ret       
..B1.16:
        flds      .L_2il0floatpacket.1@GOTOFF(%edx)
        fsts      12(%esp)
        flds      12(%esp)
        fmulp     %st, %st(1)
        fstps     12(%esp)
        flds      (%esp)
..B1.17:
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        ret       
..B1.18:
        flds      (%esp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        ret       
..B1.19:
        fstp      %st(0)
        movzbl    45(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        flds      .L_2il0floatpacket.0@GOTOFF(%edx)
        fsts      12(%esp)
        flds      12(%esp)
        fmulp     %st, %st(1)
        movl      _smallest_value_32@GOTOFF(%edx,%eax,4), %ecx
        movl      %ecx, (%esp)
        fstps     12(%esp)
        jmp       ..B1.18
..B1.20:
        cmpl      $-2147483648, 40(%esp)
        jne       ..B1.22
..B1.21:
        cmpl      $0, 36(%esp)
        je        ..B1.3
..B1.22:
        fldt      36(%esp)
        fmuls     32(%esp)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        ret       
..B1.23:
        testl     $8388607, 32(%esp)
        jne       ..B1.22
        jmp       ..B1.2
..B1.24:
        fstp      %st(1)
        addl      $20, %esp
        popl      %ebx
        popl      %edi
        ret       
        .align    16,0x90
	.type	nexttowardf,@function
	.size	nexttowardf,.-nexttowardf
	.data
# -- End  nexttowardf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x0d800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_smallest_value_32:
	.long	1
	.long	2147483649
	.type	_smallest_value_32,@object
	.size	_smallest_value_32,8
	.data
	.section .note.GNU-stack, ""
# End
