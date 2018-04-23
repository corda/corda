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
	.file "hypotf_gen.c"
	.text
..TXTST0:
# -- Begin  hypotf
	.text
       .align    16,0x90
	.globl hypotf
hypotf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_hypotf.1:
..L2:

        movaps    %xmm1, %xmm3
        movaps    %xmm0, %xmm1
        pxor      %xmm0, %xmm0
        pxor      %xmm2, %xmm2
        cvtss2sd  %xmm1, %xmm0
        cvtss2sd  %xmm3, %xmm2
        mulsd     %xmm0, %xmm0
        mulsd     %xmm2, %xmm2
        addsd     %xmm2, %xmm0
        sqrtsd    %xmm0, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movd      %xmm0, %eax
        cmpl      $2139095040, %eax
        jb        ..B1.8
..B1.2:
        movd      %xmm1, %edx
        movd      %xmm3, %ecx
        andl      $2147483647, %edx
        andl      $2147483647, %ecx
        cmpl      $2139095040, %eax
        je        ..B1.8
..B1.4:
        cmpl      $2139095040, %edx
        jne       ..B1.6
..B1.5:
        movl      %edx, -8(%rsp)
        movss     -8(%rsp), %xmm0
        ret       
..B1.6:
        cmpl      $2139095040, %ecx
        jne       ..B1.8
..B1.7:
        movl      %ecx, -8(%rsp)
        movss     -8(%rsp), %xmm0
..B1.8:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	hypotf,@function
	.size	hypotf,.-hypotf
	.data
# -- End  hypotf
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
