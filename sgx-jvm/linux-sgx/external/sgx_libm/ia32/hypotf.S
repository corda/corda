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
	.file "hypotf.c"
	.text
..TXTST0:
# -- Begin  hypotf
	.text
       .align    16,0x90
	.globl hypotf
hypotf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        movzwl    10(%ebp), %ecx
        andl      $32640, %ecx
        movzwl    14(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %ecx
        shrl      $7, %edx
        cmpl      $255, %ecx
        je        ..B1.9
..B1.2:
        cmpl      $255, %edx
        je        ..B1.9
..B1.3:
        movl      %ecx, %eax
        subl      %edx, %eax
        addl      $28, %eax
        cmpl      $56, %eax
        ja        ..B1.5
..B1.4:
        flds      8(%ebp)
        addl      %edx, %ecx
        fmul      %st(0), %st
        cmpl      $378, %ecx
        flds      12(%ebp)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fsqrt     
        jg        ..B1.7
        jmp       ..B1.6
..B1.5:
        flds      8(%ebp)
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        fabs      
        flds      12(%ebp)
        fabs      
        fldl      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstps     8(%esp)
        flds      8(%esp)
        fmuls     .L_2il0floatpacket.1@GOTOFF(%eax)
        cmpl      $2130706432, 8(%esp)
..B1.6:
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.7:
        fstl      (%esp)
        cmpl      $1206910976, 4(%esp)
        jl        ..B1.6
..B1.8:
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        andb      $127, 11(%ebp)
        andb      $127, 15(%ebp)
        cmpl      $255, %ecx
        je        ..B1.21
..B1.10:
        cmpl      $255, %edx
        je        ..B1.19
..B1.11:
        flds      8(%ebp)
        fld       %st(0)
        fadds     12(%ebp)
        fstps     8(%esp)
..B1.12:
        cmpl      $255, %ecx
        je        ..B1.17
..B1.13:
        fstp      %st(0)
        cmpl      $255, %edx
        jne       ..B1.16
..B1.14:
        testl     $8388607, 12(%ebp)
        jne       ..B1.16
..B1.15:
        flds      12(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        flds      8(%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        testl     $8388607, 8(%ebp)
        jne       ..B1.13
..B1.18:
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        testl     $8388607, 12(%ebp)
        je        ..B1.11
..B1.20:
        flds      8(%ebp)
        fld       %st(0)
        fmuls     12(%ebp)
        fstps     8(%esp)
        jmp       ..B1.12
..B1.21:
        testl     $8388607, 8(%ebp)
        jne       ..B1.20
        jmp       ..B1.10
        .align    16,0x90
	.type	hypotf,@function
	.size	hypotf,.-hypotf
	.data
# -- End  hypotf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 4
.L_2il0floatpacket.1:
	.long	0x40000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.data
	.section .note.GNU-stack, ""
# End
