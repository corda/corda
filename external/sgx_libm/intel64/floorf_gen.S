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
	.file "floorf_gen.c"
	.text
..TXTST0:
# -- Begin  floorf
	.text
       .align    16,0x90
	.globl floorf
floorf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_floorf.1:
..L2:

        movaps    %xmm0, %xmm2
        movss     %xmm2, -16(%rsp)
        movzwl    -14(%rsp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        cmpl      $149, %eax
        jge       ..B1.8
..B1.2:
        cmpl      $127, %eax
        jl        ..B1.4
..B1.3:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        movaps    %xmm1, %xmm0
        addss     %xmm2, %xmm0
        movss     %xmm0, -24(%rsp)
        movss     -24(%rsp), %xmm3
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        subss     %xmm1, %xmm3
        comiss    %xmm2, %xmm3
        addss     %xmm3, %xmm0
        ja        ..L3
        movaps    %xmm3, %xmm0
..L3:
        ret       
..B1.4:
        testl     %eax, %eax
        jne       ..B1.7
..B1.5:
        testl     $8388607, -16(%rsp)
        jne       ..B1.7
..B1.6:
        movss     -16(%rsp), %xmm0
        ret       
..B1.7:
        movb      -13(%rsp), %dl
        lea       _pzero_none(%rip), %rcx
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        movl      -16(%rsp), %eax
        movl      %eax, -24(%rsp)
        movss     (%rcx,%rsi,4), %xmm0
        ret       
..B1.8:
        cmpl      $150, %eax
        jge       ..B1.6
..B1.9:
        movss     -16(%rsp), %xmm1
        movss     .L_2il0floatpacket.2(%rip), %xmm0
        testb     $1, -16(%rsp)
        addss     %xmm1, %xmm0
        jne       ..L4
        movaps    %xmm1, %xmm0
..L4:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	floorf,@function
	.size	floorf,.-floorf
	.data
# -- End  floorf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4b400000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0xbf800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0xbf000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
_pzero_none:
	.long	0
	.long	3212836864
	.type	_pzero_none,@object
	.size	_pzero_none,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
