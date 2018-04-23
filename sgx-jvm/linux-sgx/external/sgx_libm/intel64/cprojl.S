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
	.file "cprojl.c"
	.text
..TXTST0:
# -- Begin  cprojl
	.text
       .align    16,0x90
	.globl cprojl
cprojl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cprojl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movzwl    56(%rsp), %edx
        andl      $32767, %edx
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 32(%rsp)
        cmpl      $32767, %edx
        je        ..B1.12
..B1.2:
        movzwl    72(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.9
..B1.3:
        fldt      48(%rsp)
        fstpt     (%rsp)
        fldt      64(%rsp)
        fstpt     16(%rsp)
..B1.4:
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.8
..B1.5:
        fldt      (%rsp)
        fldt      16(%rsp)
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.7
..B1.6:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 48
..B1.7:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.8:
        call      __stack_chk_fail@PLT
..B1.9:
        movq      $0x8000000000000000, %rax
        cmpq      64(%rsp), %rax
        jne       ..B1.3
..B1.10:
        fldt      .L_2il0floatpacket.7(%rip)
        fstpt     16(%rsp)
        fldt      .L_2il0floatpacket.7(%rip)
        fstpt     (%rsp)
..B1.11:
        fldt      .L_2il0floatpacket.8(%rip)
        movb      9+.L_2il0floatpacket.7(%rip), %cl
        movb      73(%rsp), %dl
        andb      $127, %cl
        andb      $-128, %dl
        movzwl    8+.L_2il0floatpacket.8(%rip), %eax
        orb       %dl, %cl
        fstpt     (%rsp)
        orl       $-32769, %eax
        fldt      .L_2il0floatpacket.7(%rip)
        fstpt     16(%rsp)
        movw      %ax, 8(%rsp)
        movb      %cl, 25(%rsp)
        jmp       ..B1.4
..B1.12:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.2
..B1.13:
        fldt      .L_2il0floatpacket.7(%rip)
        fstpt     16(%rsp)
        fldt      .L_2il0floatpacket.7(%rip)
        fstpt     (%rsp)
        jmp       ..B1.11
        .align    16,0x90
	.cfi_endproc
	.type	cprojl,@function
	.size	cprojl,.-cprojl
	.data
# -- End  cprojl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
