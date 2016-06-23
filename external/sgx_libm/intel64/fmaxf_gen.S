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
	.file "fmaxf_gen.c"
	.text
..TXTST0:
# -- Begin  fmaxf
	.text
       .align    16,0x90
	.globl fmaxf
fmaxf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_fmaxf.1:
..L2:

        movss     %xmm0, -24(%rsp)
        movzwl    -22(%rsp), %eax
        andl      $32640, %eax
        movss     %xmm1, -16(%rsp)
        cmpl      $32640, %eax
        je        ..B1.6
..B1.2:
        movzwl    -14(%rsp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        jne       ..B1.5
..B1.3:
        testl     $8388607, -16(%rsp)
        je        ..B1.5
..B1.4:
        movss     -24(%rsp), %xmm0
        ret       
..B1.5:
        movss     -24(%rsp), %xmm0
        maxss     -16(%rsp), %xmm0
        ret       
..B1.6:
        testl     $8388607, -24(%rsp)
        je        ..B1.2
..B1.7:
        movss     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	fmaxf,@function
	.size	fmaxf,.-fmaxf
	.data
# -- End  fmaxf
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
