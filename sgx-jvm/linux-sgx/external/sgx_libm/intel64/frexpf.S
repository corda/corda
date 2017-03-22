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
	.file "frexpf.c"
	.text
..TXTST0:
# -- Begin  frexpf
	.text
       .align    16,0x90
	.globl frexpf
frexpf:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_frexpf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %eax
        lea       -8388608(%rax), %ecx
        cmpl      $2130706432, %ecx
        jb        ..B1.5
..B1.2:
        decl      %eax
        cmpl      $2139095039, %eax
        jae       ..B1.6
..B1.3:
        movl      %edx, -20(%rsp)
        orl       $1056964608, %edx
        movl      %edx, -24(%rsp)
        andl      $-2147483648, %edx
        orl       $1056964608, %edx
        movl      %edx, -16(%rsp)
        movss     -24(%rsp), %xmm2
        movss     -20(%rsp), %xmm1
        movss     -20(%rsp), %xmm0
        subss     -16(%rsp), %xmm2
        addss     %xmm0, %xmm1
        movd      %xmm2, %eax
        movss     %xmm1, -20(%rsp)
        movl      %eax, %edx
        andl      $-2139095041, %eax
        andl      $2139095040, %edx
        orl       $1056964608, %eax
        shrl      $23, %edx
        movl      %eax, -24(%rsp)
        addl      $-251, %edx
        movl      %edx, (%rdi)
        movss     -24(%rsp), %xmm0
..B1.4:
        ret       
..B1.5:
        andl      $-2139095041, %edx
        shrl      $23, %ecx
        orl       $1056964608, %edx
        movl      %edx, -24(%rsp)
        addl      $-125, %ecx
        movl      %ecx, (%rdi)
        movss     -24(%rsp), %xmm0
        ret       
..B1.6:
        movss     -8(%rsp), %xmm0
        movl      $0, (%rdi)
        addss     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	frexpf,@function
	.size	frexpf,.-frexpf
	.data
# -- End  frexpf
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
