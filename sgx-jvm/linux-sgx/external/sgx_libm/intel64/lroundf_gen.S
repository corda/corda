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
	.file "lroundf_gen.c"
	.text
..TXTST0:
# -- Begin  lroundf
	.text
       .align    16,0x90
	.globl lroundf
lroundf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_lroundf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movss     %xmm0, 16(%rsp)
        movq      $0, (%rsp)
        movl      $0, 8(%rsp)
..B1.2:
        stmxcsr   8(%rsp)
        movsd     16(%rsp), %xmm1
        xorpd     %xmm2, %xmm2
        andl      $-2, 8(%rsp)
        ldmxcsr   8(%rsp)
        cvtss2si  %xmm1, %rax
        addss     %xmm1, %xmm2
        movq      %rax, (%rsp)
        stmxcsr   8(%rsp)
..B1.3:
        testb     $1, 8(%rsp)
        jne       ..B1.6
..B1.4:
        pxor      %xmm2, %xmm2
        movq      (%rsp), %rax
        cvtsi2ssq %rax, %xmm2
        movss     16(%rsp), %xmm1
        movaps    %xmm1, %xmm0
        subss     %xmm2, %xmm0
        andps     .L_2il0floatpacket.1(%rip), %xmm0
        ucomiss   .L_2il0floatpacket.0(%rip), %xmm0
        jp        ..B1.5
        je        ..B1.7
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.6:
        movq      (%rsp), %rax
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.7:
        pxor      %xmm0, %xmm0
        comiss    %xmm0, %xmm1
        jbe       ..B1.10
..B1.8:
        comiss    %xmm2, %xmm1
        jbe       ..B1.5
..B1.9:
        incq      %rax
        movq      %rax, (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.10:
        comiss    %xmm1, %xmm2
        jbe       ..B1.5
..B1.11:
        decq      %rax
        movq      %rax, (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	lroundf,@function
	.size	lroundf,.-lroundf
	.data
# -- End  lroundf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x7fffffff,0x00000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 4
.L_2il0floatpacket.0:
	.long	0x3f000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
