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
	.file "sqrtf_gen.c"
	.text
..TXTST0:
# -- Begin  sqrtf
	.text
       .align    16,0x90
	.globl sqrtf
sqrtf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_sqrtf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movss     %xmm0, 8(%rsp)
..B1.2:
        testb     $128, 11(%rsp)
        jne       .L_2TAG_PACKET_0.0.2
        sqrtss    %xmm0, %xmm0
.L_2TAG_PACKET_1.0.2:
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
        ucomiss   4+zeros(%rip), %xmm0
        sqrtss    %xmm0, %xmm0
        jp        .L_2TAG_PACKET_1.0.2
        je        .L_2TAG_PACKET_1.0.2
        movss     %xmm0, (%rsp)
..B1.3:
        movss     (%rsp), %xmm0
.L_2TAG_PACKET_2.0.2:
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sqrtf,@function
	.size	sqrtf,.-sqrtf
	.data
# -- End  sqrtf
	.section .rodata, "a"
	.align 16
	.align 16
zeros:
	.long	0
	.long	2147483648
	.type	zeros,@object
	.size	zeros,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
