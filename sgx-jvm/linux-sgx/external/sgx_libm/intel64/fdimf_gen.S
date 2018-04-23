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
	.file "fdimf_gen.c"
	.text
..TXTST0:
# -- Begin  fdimf
	.text
       .align    16,0x90
	.globl fdimf
fdimf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_fdimf.1:
..L2:

        movss     %xmm0, -16(%rsp)
        movss     %xmm1, -8(%rsp)
        movzwl    -14(%rsp), %edx
        andl      $32640, %edx
        movzwl    -6(%rsp), %eax
        andl      $32640, %eax
        shrl      $7, %edx
        shrl      $7, %eax
        cmpl      $255, %edx
        je        ..B1.13
..B1.2:
        cmpl      $255, %eax
        je        ..B1.11
..B1.3:
        movss     -16(%rsp), %xmm0
        movss     -8(%rsp), %xmm2
        comiss    %xmm2, %xmm0
        jbe       ..B1.10
..B1.4:
        cmpl      $255, %edx
        je        ..B1.8
..B1.5:
        cmpl      $255, %eax
        je        ..B1.8
..B1.6:
        cmpl      $254, %edx
        je        ..B1.9
..B1.7:
        cmpl      $254, %eax
        je        ..B1.9
..B1.8:
        subss     %xmm2, %xmm0
        ret       
..B1.9:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        mulss     %xmm1, %xmm0
        mulss     %xmm1, %xmm2
        subss     %xmm2, %xmm0
        movss     %xmm0, -24(%rsp)
        movss     -24(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        ret       
..B1.10:
        pxor      %xmm0, %xmm0
        ret       
..B1.11:
        testl     $8388607, -8(%rsp)
        je        ..B1.3
..B1.12:
        movss     -8(%rsp), %xmm0
        ret       
..B1.13:
        testl     $8388607, -16(%rsp)
        je        ..B1.2
..B1.14:
        movss     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fdimf,@function
	.size	fdimf,.-fdimf
	.data
# -- End  fdimf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x3f000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x40000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
