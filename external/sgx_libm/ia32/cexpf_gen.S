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
	.file "cexpf_gen.c"
	.text
..TXTST0:
# -- Begin  __libm_internal_cexpf
	.text
       .align    16,0x90
	.hidden __libm_internal_cexpf
	.globl __libm_internal_cexpf
__libm_internal_cexpf:
# parameter 1: 64 + %esp
..B1.1:
..L1:

        pushl     %ebx
        subl      $56, %esp
        xorl      %ecx, %ecx
        flds      64(%esp)
        flds      68(%esp)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 44(%esp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fstpl     36(%esp)
        movl      %ecx, 4(%esp)
        movl      %ecx, 8(%esp)
        fstpl     28(%esp)
        addl      $-32, %esp
        movl      60(%esp), %edx
        lea       44(%esp), %eax
        movl      20(%eax), %ecx
        movl      %eax, (%esp)
        lea       4(%esp), %eax
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      68(%esp), %edx
        movl      72(%esp), %ecx
        movl      %edx, 8(%eax)
        movl      %ecx, 12(%eax)
        call      cexp@PLT
..B1.11:
        addl      $28, %esp
        fldl      12(%esp)
        fsts      4(%esp)
        fldl      20(%esp)
        movzwl    6(%esp), %eax
        testl     $32640, %eax
        fsts      8(%esp)
        jne       ..B1.3
..B1.2:
        testl     $8388607, 4(%esp)
        jne       ..B1.5
..B1.3:
        movzwl    10(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.6
..B1.4:
        testl     $8388607, 8(%esp)
        je        ..B1.6
..B1.5:
        fstp      %st(0)
        fstp      %st(0)
        flds      .L_2il0floatpacket.4@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     (%esp)
        flds      4(%esp)
        flds      8(%esp)
..B1.6:
        fxch      %st(1)
        fstps     4(%esp)
        fstps     8(%esp)
        movl      44(%esp), %ecx
        xorl      %esp, %ecx
        movl      4(%esp), %eax
        movl      8(%esp), %edx
        cmpl      %gs:20, %ecx
        jne       ..B1.8
..B1.7:
        addl      $56, %esp
        popl      %ebx
        ret       
..B1.8:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__libm_internal_cexpf,@function
	.size	__libm_internal_cexpf,.-__libm_internal_cexpf
	.data
# -- End  __libm_internal_cexpf
	.text
# -- Begin  cexpf
	.text
       .align    16,0x90
	.globl cexpf
cexpf:
# parameter 1: 64 + %esp
..B2.1:
..L3:

        pushl     %ebx
        subl      $56, %esp
        xorl      %ecx, %ecx
        flds      64(%esp)
        flds      68(%esp)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 44(%esp)
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        fstpl     36(%esp)
        movl      %ecx, 4(%esp)
        movl      %ecx, 8(%esp)
        fstpl     28(%esp)
        addl      $-32, %esp
        movl      60(%esp), %edx
        lea       44(%esp), %eax
        movl      20(%eax), %ecx
        movl      %eax, (%esp)
        lea       4(%esp), %eax
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      68(%esp), %edx
        movl      72(%esp), %ecx
        movl      %edx, 8(%eax)
        movl      %ecx, 12(%eax)
        call      cexp@PLT
..B2.11:
        addl      $28, %esp
        fldl      12(%esp)
        fsts      4(%esp)
        fldl      20(%esp)
        movzwl    6(%esp), %eax
        testl     $32640, %eax
        fsts      8(%esp)
        jne       ..B2.3
..B2.2:
        testl     $8388607, 4(%esp)
        jne       ..B2.5
..B2.3:
        movzwl    10(%esp), %eax
        testl     $32640, %eax
        jne       ..B2.6
..B2.4:
        testl     $8388607, 8(%esp)
        je        ..B2.6
..B2.5:
        fstp      %st(0)
        fstp      %st(0)
        flds      .L_2il0floatpacket.4@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     (%esp)
        flds      4(%esp)
        flds      8(%esp)
..B2.6:
        fxch      %st(1)
        fstps     4(%esp)
        fstps     8(%esp)
        movl      44(%esp), %ecx
        xorl      %esp, %ecx
        movl      4(%esp), %eax
        movl      8(%esp), %edx
        cmpl      %gs:20, %ecx
        jne       ..B2.8
..B2.7:
        addl      $56, %esp
        popl      %ebx
        ret       
..B2.8:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	cexpf,@function
	.size	cexpf,.-cexpf
	.data
# -- End  cexpf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.4:
	.long	0x00800000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.data
	.section .note.GNU-stack, ""
# End
