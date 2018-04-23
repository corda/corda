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
	.file "casinl.c"
	.text
..TXTST0:
# -- Begin  casinl
	.text
       .align    16,0x90
	.globl casinl
casinl:
# parameter 1: 144 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_casinl.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        subq      $128, %rsp
	.cfi_def_cfa_offset 144
        xorb      %r13b, %r13b
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 112(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     96(%rsp)
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
        movb      $1, %r13b
..B1.7:
        fldt      160(%rsp)
        movzwl    168(%rsp), %eax
        andl      $32767, %eax
        fstpt     80(%rsp)
        cmpl      $32767, %eax
        fldt      144(%rsp)
        fstpt     96(%rsp)
        jl        ..B1.9
..B1.8:
        movq      $0x8000000000000000, %rax
        cmpq      160(%rsp), %rax
        jne       ..B1.10
..B1.9:
        movb      169(%rsp), %al
        notb      %al
        movb      89(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 89(%rsp)
..B1.10:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 176
        movq      %rsp, %rdi
        movq      112(%rsp), %rax
        movq      120(%rsp), %rdx
        movq      128(%rsp), %rcx
        movq      136(%rsp), %rsi
        movq      %rax, (%rdi)
        movq      %rdx, 8(%rdi)
        movq      %rcx, 16(%rdi)
        movq      %rsi, 24(%rdi)
        call      casinhl@PLT
..B1.22:
        fxch      %st(1)
        addq      $32, %rsp
	.cfi_def_cfa_offset 144
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        movzwl    24(%rsp), %eax
        andl      $32767, %eax
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        cmpl      $32767, %eax
        fstpt     48(%rsp)
        fstpt     64(%rsp)
        jl        ..B1.12
..B1.11:
        movq      $0x8000000000000000, %rax
        cmpq      16(%rsp), %rax
        jne       ..B1.13
..B1.12:
        movb      25(%rsp), %al
        notb      %al
        movb      73(%rsp), %dl
        andb      $-128, %al
        andb      $127, %dl
        orb       %al, %dl
        movb      %dl, 73(%rsp)
..B1.13:
        testb     %r13b, %r13b
        je        ..B1.15
..B1.14:
        fldcw     2(%rsp)
..B1.15:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.19
..B1.16:
        fldt      48(%rsp)
        fldt      64(%rsp)
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.18
..B1.17:
        addq      $128, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 13, -16
..B1.18:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.19:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	casinl,@function
	.size	casinl,.-casinl
	.data
# -- End  casinl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
