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
	.file "casin.c"
	.text
..TXTST0:
# -- Begin  casin
	.text
       .align    16,0x90
	.globl casin
casin:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_casin.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movsd     %xmm1, 24(%rsp)
        movzwl    30(%rsp), %eax
        andl      $32752, %eax
        movsd     %xmm0, 16(%rsp)
        cmpl      $32752, %eax
        unpcklpd  %xmm0, %xmm1
        jl        ..B1.5
..B1.3:
        testl     $1048575, 28(%rsp)
        jne       ..B1.7
..B1.4:
        cmpl      $0, 24(%rsp)
        jne       ..B1.7
..B1.5:
        movsd     %xmm1, (%rsp)
        movhpd    %xmm1, 8(%rsp)
..B1.6:
        movb      31(%rsp), %al
        notb      %al
        movb      7(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 7(%rsp)
        movsd     (%rsp), %xmm1
        movhpd    8(%rsp), %xmm1
..B1.7:
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
        call      casinh@PLT
..B1.16:
        movsd     %xmm0, 16(%rsp)
        movzwl    22(%rsp), %eax
        andl      $32752, %eax
        movsd     %xmm1, 24(%rsp)
        cmpl      $32752, %eax
        unpcklpd  %xmm0, %xmm1
        jl        ..B1.11
..B1.9:
        testl     $1048575, 20(%rsp)
        jne       ..B1.13
..B1.10:
        cmpl      $0, 16(%rsp)
        jne       ..B1.13
..B1.11:
        movsd     %xmm1, (%rsp)
        movhpd    %xmm1, 8(%rsp)
..B1.12:
        movb      23(%rsp), %al
        notb      %al
        movb      15(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movsd     (%rsp), %xmm1
        movb      %dl, 15(%rsp)
        movhpd    8(%rsp), %xmm1
..B1.13:
        movaps    %xmm1, %xmm2
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm2
        movaps    %xmm2, %xmm1
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	casin,@function
	.size	casin,.-casin
	.data
# -- End  casin
	.text
# -- Begin  casinf
	.text
       .align    16,0x90
	.globl casinf
casinf:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_casinf.6:
..L7:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, 8(%rsp)
        movzwl    14(%rsp), %eax
        andl      $32640, %eax
        movss     12(%rsp), %xmm0
        cmpl      $32640, %eax
        movss     8(%rsp), %xmm1
        unpcklps  %xmm1, %xmm0
        jl        ..B2.4
..B2.3:
        testl     $8388607, 12(%rsp)
        jne       ..B2.6
..B2.4:
        movsd     %xmm0, (%rsp)
..B2.5:
        movb      15(%rsp), %al
        notb      %al
        movb      3(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 3(%rsp)
        movsd     (%rsp), %xmm0
..B2.6:
        call      casinhf@PLT
..B2.14:
        movsd     %xmm0, 8(%rsp)
        movzwl    10(%rsp), %eax
        andl      $32640, %eax
        movss     12(%rsp), %xmm0
        cmpl      $32640, %eax
        movss     8(%rsp), %xmm1
        unpcklps  %xmm1, %xmm0
        jl        ..B2.9
..B2.8:
        testl     $8388607, 8(%rsp)
        jne       ..B2.11
..B2.9:
        movsd     %xmm0, (%rsp)
..B2.10:
        movb      11(%rsp), %al
        notb      %al
        movb      7(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 7(%rsp)
        movsd     (%rsp), %xmm0
..B2.11:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	casinf,@function
	.size	casinf,.-casinf
	.data
# -- End  casinf
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
