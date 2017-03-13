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
	.file "scalb_gen.c"
	.text
..TXTST0:
# -- Begin  scalb
	.text
       .align    16,0x90
	.globl scalb
scalb:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_scalb.1:
..L2:

        movaps    %xmm1, %xmm2
        movsd     %xmm0, -16(%rsp)
        movzwl    -10(%rsp), %eax
        movl      %eax, %edx
        movsd     %xmm2, -8(%rsp)
        andl      $32752, %edx
        movzwl    -2(%rsp), %ecx
        andl      $32752, %ecx
        shrl      $4, %edx
        shrl      $4, %ecx
        cmpl      $2047, %edx
        je        ..B1.56
..B1.2:
        cmpl      $2047, %ecx
        je        ..B1.37
..B1.3:
        cmpl      $1023, %ecx
        jl        ..B1.20
..B1.4:
        cmpl      $1044, %ecx
        jge       ..B1.7
..B1.5:
        lea       _TWO_52H(%rip), %rcx
        pxor      %xmm1, %xmm1
        movsd     (%rcx), %xmm0
        addsd     %xmm2, %xmm0
        movsd     %xmm0, -24(%rsp)
        movl      -24(%rsp), %edi
        cvtsi2sd  %edi, %xmm1
        ucomisd   %xmm2, %xmm1
        jp        ..B1.6
        je        ..B1.10
..B1.6:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        ret       
..B1.7:
        movb      -1(%rsp), %sil
        lea       large_integers(%rip), %rdi
        andb      $-128, %sil
        shrb      $7, %sil
        movzbl    %sil, %r8d
        cmpl      $1075, %ecx
        movl      (%rdi,%r8,4), %edi
        jge       ..B1.10
..B1.8:
        addl      $13, %ecx
        movl      -8(%rsp), %esi
        shll      %cl, %esi
        testl     %esi, %esi
        jne       ..B1.6
..B1.10:
        testl     %edx, %edx
        jne       ..B1.15
..B1.11:
        testl     $1048575, -12(%rsp)
        jne       ..B1.14
..B1.12:
        cmpl      $0, -16(%rsp)
        jne       ..B1.14
..B1.13:
        movsd     -16(%rsp), %xmm0
        ret       
..B1.14:
        lea       _TWO_55(%rip), %rax
        movsd     -16(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -16(%rsp)
        movzwl    -10(%rsp), %eax
        movl      %eax, %edx
        andl      $32752, %edx
        shrl      $4, %edx
        addl      $-55, %edx
..B1.15:
        movl      $65536, %ecx
        cmpl      $65536, %edi
        cmovg     %ecx, %edi
        movl      $-65536, %esi
        cmpl      $-65536, %edi
        cmovge    %edi, %esi
        lea       (%rdx,%rsi), %ecx
        testl     %ecx, %ecx
        jle       ..B1.33
..B1.16:
        cmpl      $2047, %ecx
        jl        ..B1.18
..B1.17:
        movb      -9(%rsp), %al
        lea       _large_value_64(%rip), %rcx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rcx,%rdx,8), %xmm0
        mulsd     (%rcx), %xmm0
        ret       
..B1.18:
        andl      $2047, %ecx
        andl      $-32753, %eax
        shll      $4, %ecx
        orl       %ecx, %eax
        movw      %ax, -10(%rsp)
        movsd     -16(%rsp), %xmm0
..B1.19:
        ret       
..B1.20:
        testl     %ecx, %ecx
        jne       ..B1.24
..B1.21:
        testl     $1048575, -4(%rsp)
        jne       ..B1.24
..B1.22:
        cmpl      $0, -8(%rsp)
        jne       ..B1.24
..B1.23:
        lea       _ones(%rip), %rax
        movsd     -16(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        ret       
..B1.24:
        cmpl      $2047, %edx
        jne       ..B1.6
..B1.25:
        testl     %ecx, %ecx
        jle       ..B1.6
..B1.26:
        cmpl      $1, %ecx
        jne       ..B1.30
..B1.27:
        movl      -4(%rsp), %eax
        andl      $1048575, %eax
..B1.28:
        jne       ..B1.30
..B1.29:
        cmpl      $0, -8(%rsp)
        jbe       ..B1.6
..B1.30:
        movb      -9(%rsp), %al
        lea       _infs(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        ret       
..B1.33:
        cmpl      $-52, %ecx
        jge       ..B1.35
..B1.34:
        movb      -9(%rsp), %al
        lea       _small_value_64(%rip), %rcx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rcx,%rdx,8), %xmm0
        mulsd     (%rcx), %xmm0
        ret       
..B1.35:
        andl      $-32753, %eax
        lea       55(%rdx,%rsi), %edx
        andl      $2047, %edx
        shll      $4, %edx
        orl       %edx, %eax
        movw      %ax, -10(%rsp)
        lea       8+_TWO_55(%rip), %rax
        movsd     -16(%rsp), %xmm0
        mulsd     (%rax), %xmm0
..B1.36:
        ret       
..B1.37:
        testl     $1048575, -4(%rsp)
        jne       ..B1.51
..B1.38:
        cmpl      $0, -8(%rsp)
        jne       ..B1.51
..B1.39:
        testl     %edx, %edx
        jne       ..B1.46
..B1.40:
        testl     $1048575, -12(%rsp)
        jne       ..B1.47
..B1.41:
        cmpl      $0, -16(%rsp)
        jne       ..B1.47
..B1.42:
        testb     $-128, -1(%rsp)
        je        ..B1.44
..B1.43:
        movsd     -16(%rsp), %xmm0
        ret       
..B1.44:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
..B1.45:
        ret       
..B1.46:
        cmpl      $2047, %edx
        je        ..B1.52
..B1.47:
        movb      -9(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        testb     $-128, -1(%rsp)
        je        ..B1.49
..B1.48:
        lea       _zeros(%rip), %rax
        movsd     (%rax,%rdx,8), %xmm0
        ret       
..B1.49:
        lea       _infs(%rip), %rax
        movsd     (%rax,%rdx,8), %xmm0
..B1.50:
        ret       
..B1.51:
        movsd     -16(%rsp), %xmm0
        addsd     -8(%rsp), %xmm0
        ret       
..B1.52:
        testb     $-128, -1(%rsp)
        jne       ..B1.54
..B1.53:
        movsd     -16(%rsp), %xmm0
        ret       
..B1.54:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
..B1.55:
        ret       
..B1.56:
        testl     $1048575, -12(%rsp)
        jne       ..B1.51
..B1.57:
        cmpl      $0, -16(%rsp)
        jne       ..B1.51
..B1.58:
        cmpl      $2047, %ecx
        je        ..B1.37
        jmp       ..B1.20
        .align    16,0x90
	.cfi_endproc
	.type	scalb,@function
	.size	scalb,.-scalb
	.data
# -- End  scalb
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
large_integers:
	.long	65536
	.long	-65536
	.type	large_integers,@object
	.size	large_integers,8
	.align 4
_TWO_55:
	.long	0
	.long	1130364928
	.long	0
	.long	1015021568
	.type	_TWO_55,@object
	.size	_TWO_55,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
