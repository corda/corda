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
	.file "scalbf.c"
	.text
..TXTST0:
# -- Begin  scalbf
	.text
       .align    16,0x90
	.globl scalbf
scalbf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_scalbf.1:
..L2:

        movss     %xmm0, -16(%rsp)
        movzwl    -14(%rsp), %esi
        movl      %esi, %edx
        movss     %xmm1, -24(%rsp)
        andl      $32640, %edx
        movzwl    -22(%rsp), %r8d
        andl      $32640, %r8d
        shrl      $7, %edx
        shrl      $7, %r8d
        cmpl      $255, %edx
        je        ..B1.48
..B1.2:
        cmpl      $255, %r8d
        je        ..B1.31
..B1.3:
        cmpl      $127, %r8d
        jl        ..B1.16
..B1.4:
        cmpl      $150, %r8d
        jge       ..B1.7
..B1.5:
        movd      %xmm1, %edi
        movl      %r8d, %ecx
        andl      $8388607, %edi
        negl      %ecx
        movl      %edi, %eax
        addl      $22, %ecx
        orl       $8388608, %eax
        addl      $10, %r8d
        shrl      %cl, %eax
        movl      %r8d, %ecx
        shll      %cl, %edi
        testl     %edi, %edi
        je        ..B1.8
..B1.6:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
        ret       
..B1.7:
        movl      $65536, %eax
..B1.8:
        testl     %edx, %edx
        jne       ..B1.11
..B1.9:
        testl     $8388607, -16(%rsp)
        je        ..B1.15
..B1.10:
        movss     -16(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        movss     %xmm0, -16(%rsp)
        movzwl    -14(%rsp), %esi
        movl      %esi, %edx
        andl      $32640, %edx
        shrl      $7, %edx
        addl      $-25, %edx
..B1.11:
        movl      $65536, %ecx
        cmpl      $65536, %eax
        movzbl    -21(%rsp), %edi
        cmovg     %ecx, %eax
        movl      %eax, %r8d
        negl      %r8d
        testl     $128, %edi
        cmovne    %r8d, %eax
        lea       (%rdx,%rax), %ecx
        testl     %ecx, %ecx
        jle       ..B1.27
..B1.12:
        cmpl      $255, %ecx
        jge       ..B1.26
..B1.13:
        movzbl    %cl, %ecx
        andl      $-32641, %esi
        shll      $7, %ecx
        orl       %ecx, %esi
        movw      %si, -14(%rsp)
        movss     -16(%rsp), %xmm0
..B1.14:
        ret       
..B1.15:
        movss     -16(%rsp), %xmm0
        ret       
..B1.16:
        testl     %r8d, %r8d
        jne       ..B1.19
..B1.17:
        testl     $8388607, -24(%rsp)
        je        ..B1.15
..B1.19:
        cmpl      $255, %edx
        jne       ..B1.6
..B1.20:
        testl     %r8d, %r8d
        jle       ..B1.6
..B1.21:
        cmpl      $1, %r8d
        jne       ..B1.23
..B1.22:
        testl     $8388607, -24(%rsp)
        jle       ..B1.6
..B1.23:
        movb      -13(%rsp), %al
        lea       _infs(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        ret       
..B1.26:
        movb      -13(%rsp), %al
        lea       _large_value_32(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        mulss     .L_2il0floatpacket.2(%rip), %xmm0
        movss     %xmm0, -24(%rsp)
        ret       
..B1.27:
        cmpl      $-23, %ecx
        jge       ..B1.29
..B1.28:
        movb      -13(%rsp), %al
        lea       _small_value_32(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        mulss     .L_2il0floatpacket.3(%rip), %xmm0
        jmp       ..B1.30
..B1.29:
        movl      -16(%rsp), %ecx
        lea       25(%rdx,%rax), %eax
        movl      %ecx, -24(%rsp)
        shrl      $16, %ecx
        movzbl    %al, %eax
        andl      $-32641, %ecx
        shll      $7, %eax
        orl       %eax, %ecx
        movw      %cx, -22(%rsp)
        movss     -24(%rsp), %xmm0
        mulss     .L_2il0floatpacket.4(%rip), %xmm0
..B1.30:
        movss     %xmm0, -24(%rsp)
        ret       
..B1.31:
        testl     $8388607, -24(%rsp)
        jne       ..B1.43
..B1.32:
        testl     %edx, %edx
        jne       ..B1.38
..B1.33:
        testl     $8388607, -16(%rsp)
        jne       ..B1.39
..B1.34:
        testb     $-128, -21(%rsp)
        je        ..B1.36
..B1.35:
        movss     -16(%rsp), %xmm0
        ret       
..B1.36:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
..B1.37:
        ret       
..B1.38:
        cmpl      $255, %edx
        je        ..B1.44
..B1.39:
        movb      -13(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        testb     $-128, -21(%rsp)
        je        ..B1.41
..B1.40:
        lea       _zeros(%rip), %rax
        movss     (%rax,%rdx,4), %xmm0
        ret       
..B1.41:
        lea       _infs(%rip), %rax
        movss     (%rax,%rdx,4), %xmm0
..B1.42:
        ret       
..B1.43:
        movss     -16(%rsp), %xmm0
        addss     -24(%rsp), %xmm0
        ret       
..B1.44:
        testb     $-128, -21(%rsp)
        jne       ..B1.46
..B1.45:
        movss     -16(%rsp), %xmm0
        ret       
..B1.46:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.0(%rip), %xmm0
..B1.47:
        ret       
..B1.48:
        testl     $8388607, -16(%rsp)
        jne       ..B1.43
..B1.49:
        cmpl      $255, %r8d
        je        ..B1.31
        jmp       ..B1.16
        .align    16,0x90
	.cfi_endproc
	.type	scalbf,@function
	.size	scalbf,.-scalbf
	.data
# -- End  scalbf
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
	.align 4
.L_2il0floatpacket.2:
	.long	0x71800000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x0d800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
.L_2il0floatpacket.4:
	.long	0x33000000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,4
	.align 4
_infs:
	.long	2139095040
	.long	4286578688
	.type	_infs,@object
	.size	_infs,8
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
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
