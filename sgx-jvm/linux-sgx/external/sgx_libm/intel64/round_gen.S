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
	.file "round_gen.c"
	.text
..TXTST0:
# -- Begin  round
	.text
       .align    16,0x90
	.globl round
round:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_round.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, (%rsp)
..B1.2:
        movapd    bns(%rip), %xmm2
        movapd    %xmm0, %xmm3
        movapd    %xmm0, %xmm1
        psrlq     $52, %xmm0
        movd      %xmm0, %eax
        andpd     s(%rip), %xmm0
        psubd     %xmm0, %xmm2
        psrlq     %xmm2, %xmm1
        psllq     %xmm2, %xmm1
        testl     $2048, %eax
        jne       .L_2TAG_PACKET_0.0.1
        cmpl      $1023, %eax
        jl        .L_2TAG_PACKET_1.0.1
        cmpl      $1074, %eax
        jg        .L_2TAG_PACKET_2.0.1
        subsd     %xmm1, %xmm3
        movq      %xmm1, %xmm0
        cmppd     $5, p_half(%rip), %xmm3
        andpd     p_one(%rip), %xmm3
        addsd     %xmm3, %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_1.0.1:
        cmpl      $1022, %eax
        jl        .L_2TAG_PACKET_3.0.1
        movq      p_one(%rip), %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_3.0.1:
        pxor      %xmm0, %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_2.0.1:
        movq      (%rsp), %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_0.0.1:
        cmpl      $3071, %eax
        jl        .L_2TAG_PACKET_4.0.1
        cmpl      $3122, %eax
        jg        .L_2TAG_PACKET_2.0.1
        subsd     %xmm1, %xmm3
        movq      %xmm1, %xmm0
        cmppd     $2, n_half(%rip), %xmm3
        andpd     n_one(%rip), %xmm3
        addsd     %xmm3, %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_4.0.1:
        cmpl      $3070, %eax
        jl        .L_2TAG_PACKET_5.0.1
        movq      n_one(%rip), %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_5.0.1:
        movq      8+zeros(%rip), %xmm0
        movq      %xmm0, 8(%rsp)
.L_2TAG_PACKET_6.0.1:
..B1.4:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	round,@function
	.size	round,.-round
	.data
# -- End  round
	.section .rodata, "a"
	.align 16
	.align 16
bns:
	.long	1075
	.long	0
	.long	1075
	.long	0
	.type	bns,@object
	.size	bns,16
	.align 16
s:
	.long	2047
	.long	0
	.long	0
	.long	0
	.type	s,@object
	.size	s,16
	.align 16
p_half:
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.type	p_half,@object
	.size	p_half,16
	.align 16
p_one:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.type	p_one,@object
	.size	p_one,16
	.align 16
n_half:
	.long	0
	.long	3219128320
	.long	0
	.long	1127219200
	.type	n_half,@object
	.size	n_half,16
	.align 16
n_one:
	.long	0
	.long	3220176896
	.long	0
	.long	1127219200
	.type	n_one,@object
	.size	n_one,16
	.align 16
zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	zeros,@object
	.size	zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
