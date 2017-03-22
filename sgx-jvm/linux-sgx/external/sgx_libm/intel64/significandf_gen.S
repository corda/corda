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
	.file "significandf_gen.c"
	.text
..TXTST0:
# -- Begin  significandf
	.text
       .align    16,0x90
	.globl significandf
significandf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_significandf.1:
..L2:

        movaps    %xmm0, %xmm1
        movd      %xmm1, %ecx
        movss     %xmm1, -8(%rsp)
        movl      %ecx, %edx
        andl      $2147483647, %edx
        lea       -8388608(%rdx), %eax
        cmpl      $2130706432, %eax
        jb        ..B1.6
..B1.2:
        decl      %edx
        cmpl      $2139095039, %edx
        jae       ..B1.4
..B1.3:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        mulss     %xmm0, %xmm1
        movd      %xmm1, %eax
        andl      $-2139095041, %eax
        orl       $1065353216, %eax
        movl      %eax, -8(%rsp)
        movss     -8(%rsp), %xmm0
        ret       
..B1.4:
        movss     -8(%rsp), %xmm0
..B1.5:
        ret       
..B1.6:
        andl      $-2139095041, %ecx
        orl       $1065353216, %ecx
        movl      %ecx, -8(%rsp)
        movss     -8(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	significandf,@function
	.size	significandf,.-significandf
	.data
# -- End  significandf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4c000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
