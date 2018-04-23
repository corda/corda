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
	.file "llround_gen.c"
	.text
..TXTST0:
# -- Begin  llround
	.text
       .align    16,0x90
	.globl llround
llround:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_llround.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
        movq      $0, 8(%rsp)
        movl      $0, 16(%rsp)
..B1.2:
        stmxcsr   16(%rsp)
        movsd     (%rsp), %xmm1
        xorpd     %xmm2, %xmm2
        andl      $-2, 16(%rsp)
        ldmxcsr   16(%rsp)
        cvtsd2si  %xmm1, %rax
        addsd     %xmm1, %xmm2
        movq      %rax, 8(%rsp)
        stmxcsr   16(%rsp)
..B1.3:
        testb     $1, 16(%rsp)
        jne       ..B1.6
..B1.4:
        pxor      %xmm2, %xmm2
        movq      8(%rsp), %rax
        cvtsi2sdq %rax, %xmm2
        movsd     (%rsp), %xmm1
        movaps    %xmm1, %xmm0
        subsd     %xmm2, %xmm0
        andps     .L_2il0floatpacket.1(%rip), %xmm0
        ucomisd   .L_2il0floatpacket.0(%rip), %xmm0
        jp        ..B1.5
        je        ..B1.7
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.6:
        movq      8(%rsp), %rax
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.7:
        pxor      %xmm0, %xmm0
        comisd    %xmm0, %xmm1
        jbe       ..B1.10
..B1.8:
        comisd    %xmm2, %xmm1
        jbe       ..B1.5
..B1.9:
        incq      %rax
        movq      %rax, 8(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.10:
        comisd    %xmm1, %xmm2
        jbe       ..B1.5
..B1.11:
        decq      %rax
        movq      %rax, 8(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	llround,@function
	.size	llround,.-llround
	.data
# -- End  llround
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
