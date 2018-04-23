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
	.file "nextafterf_gen.c"
	.text
..TXTST0:
# -- Begin  nextafterf
	.text
       .align    16,0x90
	.globl nextafterf
nextafterf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_nextafterf.1:
..L2:

        movss     %xmm0, -8(%rsp)
        movzwl    -6(%rsp), %edi
        andl      $32640, %edi
        shrl      $7, %edi
        movss     %xmm1, -16(%rsp)
        cmpl      $255, %edi
        je        ..B1.20
..B1.2:
        movzwl    -14(%rsp), %esi
        andl      $32640, %esi
        shrl      $7, %esi
        cmpl      $255, %esi
        je        ..B1.18
..B1.3:
        movss     -16(%rsp), %xmm0
        movss     -8(%rsp), %xmm1
        ucomiss   %xmm0, %xmm1
        jp        ..B1.4
        je        ..B1.15
..B1.4:
        movl      -8(%rsp), %ecx
        testl     $2147483647, %ecx
        je        ..B1.17
..B1.5:
        movl      %ecx, %eax
        movl      -16(%rsp), %edx
        xorl      %edx, %eax
        testl     $-2147483648, %eax
        jne       ..B1.9
..B1.6:
        cmpl      %esi, %edi
        jg        ..B1.9
..B1.7:
        jne       ..B1.10
..B1.8:
        movl      %ecx, %eax
        andl      $8388607, %edx
        andl      $8388607, %eax
        cmpl      %edx, %eax
        jle       ..B1.10
..B1.9:
        decl      %ecx
        movl      %ecx, -20(%rsp)
        jmp       ..B1.11
..B1.10:
        incl      %ecx
        movl      %ecx, -20(%rsp)
..B1.11:
        andl      $2147483647, %ecx
        lea       -8388608(%rcx), %eax
        cmpl      $2130706432, %eax
        jb        ..B1.16
..B1.12:
        cmpl      $8388608, %ecx
        jae       ..B1.14
..B1.13:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        movss     %xmm1, -24(%rsp)
        movss     -24(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     -20(%rsp), %xmm0
        movss     %xmm1, -24(%rsp)
        ret       
..B1.14:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        movss     %xmm0, -24(%rsp)
        movss     -24(%rsp), %xmm1
        mulss     %xmm0, %xmm1
        movss     -20(%rsp), %xmm0
        movss     %xmm1, -24(%rsp)
..B1.15:
        ret       
..B1.16:
        movss     -20(%rsp), %xmm0
        ret       
..B1.17:
        movb      -13(%rsp), %al
        lea       _smallest_value_32(%rip), %rdx
        andb      $-128, %al
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, -24(%rsp)
        shrb      $7, %al
        movss     -24(%rsp), %xmm1
        mulss     %xmm0, %xmm1
        movss     %xmm1, -24(%rsp)
        movzbl    %al, %ecx
        movl      (%rdx,%rcx,4), %esi
        movl      %esi, -20(%rsp)
        jmp       ..B1.16
..B1.18:
        testl     $8388607, -16(%rsp)
        je        ..B1.3
..B1.19:
        movss     -8(%rsp), %xmm0
        addss     -16(%rsp), %xmm0
        ret       
..B1.20:
        testl     $8388607, -8(%rsp)
        jne       ..B1.19
        jmp       ..B1.2
        .align    16,0x90
	.cfi_endproc
	.type	nextafterf,@function
	.size	nextafterf,.-nextafterf
	.data
# -- End  nextafterf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x0d800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_smallest_value_32:
	.long	1
	.long	2147483649
	.type	_smallest_value_32,@object
	.size	_smallest_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
