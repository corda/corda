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
	.file "nearbyintf_gen.c"
	.text
..TXTST0:
# -- Begin  nearbyintf
	.text
       .align    16,0x90
	.globl nearbyintf
nearbyintf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_nearbyintf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movss     %xmm0, 8(%rsp)
..B1.2:
        stmxcsr   4(%rsp)
..B1.3:
        movzwl    10(%rsp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        cmpl      $150, %eax
        jge       ..B1.10
..B1.4:
        movl      4(%rsp), %edx
        andl      $24576, %edx
        cmpl      $8192, %edx
        je        ..B1.32
..B1.5:
        cmpl      $16384, %edx
        jne       ..B1.15
..B1.6:
        cmpl      $127, %eax
        jl        ..B1.11
..B1.7:
        negl      %eax
        movl      $8388607, %edx
        addl      $22, %eax
        movl      8(%rsp), %esi
        movl      %esi, %edi
        movl      %eax, %ecx
        andl      $8388607, %edi
        shll      %cl, %edx
        andl      $-8388608, %esi
        andl      %edi, %edx
        orl       %edx, %esi
        movl      %esi, %eax
        andl      $8388607, %eax
        movl      %esi, 8(%rsp)
        testl     $-2147483648, %esi
        jne       ..B1.10
..B1.8:
        cmpl      %edi, %eax
        je        ..B1.10
..B1.9:
        movss     8(%rsp), %xmm0
        addss     .L_2il0floatpacket.1(%rip), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.10:
        movss     8(%rsp), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.11:
        testl     %eax, %eax
        jne       ..B1.14
..B1.12:
        testl     $8388607, 8(%rsp)
        je        ..B1.10
..B1.14:
        movb      11(%rsp), %dl
        lea       _pone_nzero(%rip), %rcx
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        movl      8(%rsp), %eax
        movl      %eax, (%rsp)
        movss     (%rcx,%rsi,4), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.15:
        cmpl      $24576, %edx
        jne       ..B1.20
..B1.16:
        cmpl      $127, %eax
        jl        ..B1.18
..B1.17:
        negl      %eax
        movl      $8388607, %edx
        addl      $22, %eax
        movl      %eax, %ecx
        shll      %cl, %edx
        movl      8(%rsp), %esi
        movl      %esi, %edi
        andl      %edx, %esi
        andl      $-8388608, %edi
        andl      $8388607, %esi
        orl       %esi, %edi
        movl      %edi, 8(%rsp)
        movss     8(%rsp), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.18:
        movb      11(%rsp), %dl
        lea       _zeros(%rip), %rcx
        andb      $-128, %dl
        movl      8(%rsp), %eax
        movl      %eax, (%rsp)
        shrb      $7, %dl
        movzbl    %dl, %esi
        movss     (%rcx,%rsi,4), %xmm0
..B1.19:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.20:
        cmpl      $127, %eax
        jl        ..B1.27
..B1.21:
        movl      8(%rsp), %edi
        movl      %edi, %esi
        andl      $8388607, %esi
        lea       10(%rax), %ecx
        negl      %eax
        movl      %esi, %r8d
        addl      $22, %eax
        movl      $8388607, %edx
        shll      %cl, %r8d
        movl      %eax, %ecx
        shll      %cl, %edx
        andl      $-8388608, %edi
        andl      %edx, %esi
        orl       %esi, %edi
        movl      %edi, 8(%rsp)
        testl     %r8d, %r8d
        jge       ..B1.10
..B1.22:
        cmpl      $-2147483648, %r8d
        jne       ..B1.24
..B1.23:
        movzwl    10(%rsp), %ecx
        movl      $1, %eax
        shrl      $7, %ecx
        negl      %ecx
        addl      $22, %ecx
        shll      %cl, %eax
        testl     %eax, 8(%rsp)
        je        ..B1.10
..B1.24:
        movb      11(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movss     8(%rsp), %xmm0
        movzbl    %al, %ecx
        addss     (%rdx,%rcx,4), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.27:
        cmpl      $126, %eax
        jg        ..B1.31
..B1.28:
        jne       ..B1.30
..B1.29:
        testl     $8388607, 8(%rsp)
        jg        ..B1.31
..B1.30:
        movb      11(%rsp), %dl
        lea       _zeros(%rip), %rcx
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        movl      8(%rsp), %eax
        movl      %eax, (%rsp)
        movss     (%rcx,%rsi,4), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.31:
        movb      11(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movss     (%rdx,%rcx,4), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.32:
        cmpl      $127, %eax
        jl        ..B1.37
..B1.33:
        negl      %eax
        movl      $8388607, %edx
        addl      $22, %eax
        movl      8(%rsp), %esi
        movl      %esi, %edi
        movl      %eax, %ecx
        andl      $8388607, %edi
        shll      %cl, %edx
        andl      $-8388608, %esi
        andl      %edi, %edx
        orl       %edx, %esi
        movl      %esi, %eax
        andl      $8388607, %eax
        movl      %esi, 8(%rsp)
        testl     $-2147483648, %esi
        je        ..B1.10
..B1.34:
        cmpl      %edi, %eax
        je        ..B1.10
..B1.35:
        movss     8(%rsp), %xmm0
        addss     .L_2il0floatpacket.0(%rip), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.37:
        testl     %eax, %eax
        jne       ..B1.40
..B1.38:
        testl     $8388607, 8(%rsp)
        je        ..B1.10
..B1.40:
        movb      11(%rsp), %dl
        lea       _pzero_none(%rip), %rcx
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        movl      8(%rsp), %eax
        movl      %eax, (%rsp)
        movss     (%rcx,%rsi,4), %xmm0
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	nearbyintf,@function
	.size	nearbyintf,.-nearbyintf
	.data
# -- End  nearbyintf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x3f800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_pone_nzero:
	.long	1065353216
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_pzero_none:
	.long	0
	.long	3212836864
	.type	_pzero_none,@object
	.size	_pzero_none,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
