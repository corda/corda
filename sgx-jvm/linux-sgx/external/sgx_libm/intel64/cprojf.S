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
	.file "cprojf.c"
	.text
..TXTST0:
# -- Begin  cprojf
	.text
       .align    16,0x90
	.globl cprojf
cprojf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cprojf.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movzwl    -14(%rsp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        je        ..B1.9
..B1.2:
        movzwl    -10(%rsp), %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        jne       ..B1.8
..B1.3:
        testl     $8388607, -12(%rsp)
        jne       ..B1.8
..B1.4:
        movl      $2139095040, -24(%rsp)
        testb     $-128, -9(%rsp)
        je        ..B1.6
..B1.5:
        movl      $-2147483648, -20(%rsp)
        jmp       ..B1.7
..B1.6:
        movl      $0, -20(%rsp)
..B1.7:
        movsd     -24(%rsp), %xmm0
        ret       
..B1.8:
        movsd     -16(%rsp), %xmm0
        ret       
..B1.9:
        testl     $8388607, -16(%rsp)
        je        ..B1.4
        jmp       ..B1.2
        .align    16,0x90
	.cfi_endproc
	.type	cprojf,@function
	.size	cprojf,.-cprojf
	.data
# -- End  cprojf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.5:
	.long	0x7f800000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,4
	.align 4
.L_2il0floatpacket.6:
	.long	0x80000000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
