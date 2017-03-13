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
	.file "ilogbf.c"
	.text
..TXTST0:
# -- Begin  ilogbf
	.text
       .align    16,0x90
	.globl ilogbf
ilogbf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_ilogbf.1:
..L2:

        movd      %xmm0, %ecx
        movss     %xmm0, -16(%rsp)
        andl      $2147483647, %ecx
        addl      $-8388608, %ecx
        cmpl      $2130706432, %ecx
        jb        ..B1.4
..B1.2:
        testl     %ecx, %ecx
        jl        ..B1.5
..B1.3:
        movl      $2147483647, %edx
        movl      $-2147483648, %eax
        cmpl      $2130706432, %ecx
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        cmove     %edx, %eax
        movss     %xmm0, -24(%rsp)
        ret       
..B1.4:
        shrl      $23, %ecx
        addl      $-126, %ecx
        movl      %ecx, %eax
        ret       
..B1.5:
        cmpl      $-8388608, %ecx
        je        ..B1.7
..B1.6:
        movss     -16(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        movd      %xmm0, %eax
        movss     %xmm0, -16(%rsp)
        andl      $2147483647, %eax
        shrl      $23, %eax
        addl      $-152, %eax
        ret       
..B1.7:
        movl      $-2147483648, %eax
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, -24(%rsp)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	ilogbf,@function
	.size	ilogbf,.-ilogbf
	.data
# -- End  ilogbf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x7f800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x4c000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
