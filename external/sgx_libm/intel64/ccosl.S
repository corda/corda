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
	.file "ccosl.c"
	.text
..TXTST0:
# -- Begin  ccosl
	.text
       .align    16,0x90
	.globl ccosl
ccosl:
# parameter 1: 112 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_ccosl.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        subq      $96, %rsp
	.cfi_def_cfa_offset 112
        xorb      %bl, %bl
        fldt      .L_2il0floatpacket.5(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 80(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fstpt     64(%rsp)
..B1.2:
        fnstcw    2(%rsp)
..B1.3:
        movzwl    2(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, (%rsp)
..B1.5:
        fldcw     (%rsp)
..B1.6:
        movb      $1, %bl
..B1.7:
        fldt      112(%rsp)
        movzwl    136(%rsp), %eax
        andl      $32767, %eax
        fstpt     64(%rsp)
        cmpl      $32767, %eax
        fldt      128(%rsp)
        fstpt     48(%rsp)
        jl        ..B1.9
..B1.8:
        movq      $0x8000000000000000, %rax
        cmpq      128(%rsp), %rax
        jne       ..B1.10
..B1.9:
        movb      137(%rsp), %al
        notb      %al
        movb      57(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 57(%rsp)
..B1.10:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 144
        movq      %rsp, %rdi
        movq      80(%rsp), %rax
        movq      88(%rsp), %rdx
        movq      96(%rsp), %rcx
        movq      104(%rsp), %rsi
        movq      %rax, (%rdi)
        movq      %rdx, 8(%rdi)
        movq      %rcx, 16(%rdi)
        movq      %rsi, 24(%rdi)
        call      ccoshl@PLT
..B1.19:
        fxch      %st(1)
        addq      $32, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(1)
        fstpt     16(%rsp)
        testb     %bl, %bl
        fstpt     32(%rsp)
        je        ..B1.12
..B1.11:
        fldcw     2(%rsp)
..B1.12:
        movq      80(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.16
..B1.13:
        fldt      16(%rsp)
        fldt      32(%rsp)
        movq      80(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.15
..B1.14:
        addq      $96, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 112
	.cfi_offset 3, -16
..B1.15:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.16:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	ccosl,@function
	.size	ccosl,.-ccosl
	.data
# -- End  ccosl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
