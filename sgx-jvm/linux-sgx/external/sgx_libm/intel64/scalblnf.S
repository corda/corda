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
	.file "scalblnf.c"
	.text
..TXTST0:
# -- Begin  scalblnf
	.text
       .align    16,0x90
	.globl scalblnf
scalblnf:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_scalblnf.1:
..L2:

        movq      %rdi, %r8
        movss     %xmm0, -8(%rsp)
        movzwl    -6(%rsp), %eax
        movl      %eax, %esi
        andl      $32640, %esi
        shrl      $7, %esi
        cmpl      $255, %esi
        je        ..B1.9
..B1.2:
        xorb      %dil, %dil
        testl     %esi, %esi
        jne       ..B1.5
..B1.3:
        testl     $8388607, -8(%rsp)
        je        ..B1.9
..B1.4:
        movss     -8(%rsp), %xmm0
        movb      $1, %dil
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        movss     %xmm0, -8(%rsp)
        movzwl    -6(%rsp), %eax
        movl      %eax, %esi
        andl      $32640, %esi
        shrl      $7, %esi
        addl      $-25, %esi
..B1.5:
        movl      $65536, %edx
        cmpq      $65536, %r8
        cmovg     %rdx, %r8
        movq      $-65536, %rcx
        cmpq      $-65536, %r8
        movslq    %esi, %rsi
        cmovl     %rcx, %r8
        addq      %r8, %rsi
        testl     %esi, %esi
        jle       ..B1.11
..B1.6:
        cmpl      $255, %esi
        jge       ..B1.10
..B1.7:
        movzbl    %sil, %esi
        andl      $-32641, %eax
        shll      $7, %esi
        orl       %esi, %eax
        movw      %ax, -6(%rsp)
        movss     -8(%rsp), %xmm0
..B1.8:
        ret       
..B1.9:
        movss     -8(%rsp), %xmm0
        ret       
..B1.10:
        movb      -5(%rsp), %al
        lea       _large_value_32(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        ret       
..B1.11:
        cmpl      $-23, %esi
        jl        ..B1.15
..B1.12:
        addl      $25, %esi
        andl      $-32641, %eax
        movzbl    %sil, %esi
        shll      $7, %esi
        orl       %esi, %eax
        movw      %ax, -6(%rsp)
        testb     %dil, %dil
        movss     -8(%rsp), %xmm0
        mulss     .L_2il0floatpacket.3(%rip), %xmm0
        je        ..B1.14
..B1.13:
        testq     %r8, %r8
        jl        ..B1.8
..B1.14:
        ret       
..B1.15:
        movb      -5(%rsp), %al
        lea       _small_value_32(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        mulss     .L_2il0floatpacket.2(%rip), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	scalblnf,@function
	.size	scalblnf,.-scalblnf
	.data
# -- End  scalblnf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4c000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x71800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x0d800000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x33000000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
_large_value_32:
	.long	1904214016
	.long	4051697664
	.type	_large_value_32,@object
	.size	_large_value_32,8
	.align 4
_small_value_32:
	.long	226492416
	.long	2373976064
	.type	_small_value_32,@object
	.size	_small_value_32,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
