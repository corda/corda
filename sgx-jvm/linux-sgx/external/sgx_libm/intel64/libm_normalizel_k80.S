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
	.file "libm_normalizel_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_normalizel_k80
	.text
       .align    16,0x90
	.globl __libm_normalizel_k80
__libm_normalizel_k80:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_normalizel_k80.1:
..L2:

        cmpl      $0, 4(%rdi)
        jne       ..B1.3
..B1.2:
        cmpl      $0, (%rdi)
        je        ..B1.14
..B1.3:
        movzwl    8(%rdi), %eax
        movl      %eax, %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        je        ..B1.14
..B1.4:
        testl     %esi, %esi
        jle       ..B1.16
..B1.5:
        andl      $-32768, %eax
        addl      $-16383, %esi
        orl       $-49153, %eax
..B1.6:
        movw      %ax, 8(%rdi)
        cmpl      $0, 20(%rdi)
        jne       ..B1.8
..B1.7:
        cmpl      $0, 16(%rdi)
        je        ..B1.13
..B1.8:
        movzwl    24(%rdi), %edx
        movl      %edx, %ecx
        andl      $32767, %ecx
        jle       ..B1.15
..B1.9:
        andl      $-32768, %edx
        addl      $-16383, %ecx
        orl       $-49153, %edx
..B1.10:
        lea       -256(%rsi), %eax
        cmpl      %eax, %ecx
        jle       ..B1.12
..B1.11:
        subl      %esi, %ecx
        andl      $-32768, %edx
        addl      $16383, %ecx
        andl      $32767, %ecx
        orl       %ecx, %edx
        movw      %dx, 24(%rdi)
        jmp       ..B1.13
..B1.12:
        lea       zeros(%rip), %rax
        fldt      (%rax)
        fstpt     16(%rdi)
..B1.13:
        addl      %esi, 32(%rdi)
..B1.14:
        ret       
..B1.15:
        fldt      16(%rdi)
        lea       _CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        movzwl    24(%rdi), %edx
        movl      %edx, %ecx
        andl      $32767, %ecx
        andl      $-32768, %edx
        addl      $-16639, %ecx
        orl       $-49153, %edx
        jmp       ..B1.10
..B1.16:
        fldt      (%rdi)
        lea       _CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rdi)
        movzwl    8(%rdi), %eax
        movl      %eax, %esi
        andl      $32767, %esi
        andl      $-32768, %eax
        addl      $-16639, %esi
        orl       $-49153, %eax
        jmp       ..B1.6
        .align    16,0x90
	.cfi_endproc
	.type	__libm_normalizel_k80,@function
	.size	__libm_normalizel_k80,.-__libm_normalizel_k80
	.data
# -- End  __libm_normalizel_k80
	.section .rodata, "a"
	.align 16
	.align 16
zeros:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	zeros,@object
	.size	zeros,32
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16639
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
