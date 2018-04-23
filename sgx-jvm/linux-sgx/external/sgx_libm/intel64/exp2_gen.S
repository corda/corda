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
	.file "exp2_gen.c"
	.text
..TXTST0:
# -- Begin  exp2
	.text
       .align    16,0x90
	.globl exp2
exp2:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_exp2.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %sil, %sil
        movsd     %xmm0, 40(%rsp)
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movl      44(%rsp), %edx
        movl      %edx, %eax
        andl      $2147483647, %eax
        lea       -1072693248(%rax), %ecx
        cmpl      $10483712, %ecx
        ja        ..B1.9
..B1.4:
        movl      %eax, %ecx
        movl      $1048575, %edi
        shrl      $20, %ecx
        incl      %ecx
        shrl      %cl, %edi
        andl      %eax, %edi
        orl       40(%rsp), %edi
        jne       ..B1.9
..B1.5:
        lea       _TWO_52H(%rip), %rcx
        movsd     40(%rsp), %xmm0
        addsd     (%rcx), %xmm0
        movsd     %xmm0, 16(%rsp)
        movl      16(%rsp), %ecx
        cmpl      $-1074, %ecx
        jl        ..B1.9
..B1.6:
        lea       1023(%rcx), %eax
        testl     %eax, %eax
        jle       ..B1.37
..B1.7:
        movslq    %eax, %rax
        shlq      $52, %rax
        movq      %rax, 8(%rsp)
..B1.8:
        movsd     8(%rsp), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.9:
        cmpl      $1079984128, %eax
        jae       ..B1.14
..B1.10:
        cmpl      $1012924416, %eax
        jae       ..B1.12
..B1.11:
        movsd     .L_2il0floatpacket.11(%rip), %xmm0
        addsd     40(%rsp), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.12:
        movsd     40(%rsp), %xmm0
        pxor      %xmm13, %xmm13
        lea       _TWO_52H(%rip), %rax
        lea       _two_32p1(%rip), %rdx
        movsd     .L_2il0floatpacket.4(%rip), %xmm9
        lea       _two_32(%rip), %rcx
        movsd     .L_2il0floatpacket.7(%rip), %xmm10
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        movsd     (%rax), %xmm1
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     (%rdx), %xmm2
        addsd     %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movsd     16(%rsp), %xmm8
        movl      16(%rsp), %esi
        movl      %esi, %edi
        movsd     .L_2il0floatpacket.3(%rip), %xmm3
        subsd     %xmm1, %xmm8
        mulsd     .L_2il0floatpacket.1(%rip), %xmm8
        shll      $25, %edi
        addsd     40(%rsp), %xmm8
        movaps    %xmm8, %xmm7
        movaps    %xmm8, %xmm5
        mulsd     %xmm8, %xmm7
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm10
        addsd     .L_2il0floatpacket.5(%rip), %xmm9
        addsd     .L_2il0floatpacket.8(%rip), %xmm10
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm10
        addsd     .L_2il0floatpacket.6(%rip), %xmm9
        addsd     .L_2il0floatpacket.9(%rip), %xmm10
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm10
        movsd     %xmm8, (%rsp)
        movl      $0, (%rsp)
        movsd     (%rsp), %xmm6
        sarl      $25, %edi
        subsd     %xmm6, %xmm5
        mulsd     %xmm4, %xmm6
        addsd     .L_2il0floatpacket.10(%rip), %xmm10
        mulsd     %xmm4, %xmm5
        addsd     %xmm6, %xmm2
        mulsd     %xmm8, %xmm10
        addsd     %xmm5, %xmm9
        movsd     %xmm2, 16(%rsp)
        subl      %edi, %esi
        movsd     16(%rsp), %xmm11
        movslq    %edi, %rdi
        subsd     (%rcx), %xmm11
        shlq      $4, %rdi
        addsd     %xmm11, %xmm3
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r8
        subsd     %xmm3, %xmm6
        movsd     1032(%r8,%rdi), %xmm12
        addsd     %xmm6, %xmm9
        movaps    %xmm12, %xmm0
        addsd     %xmm9, %xmm10
        mulsd     %xmm11, %xmm12
        mulsd     %xmm10, %xmm0
        addsd     %xmm11, %xmm10
        mulsd     1024(%r8,%rdi), %xmm10
        shrl      $7, %esi
        addsd     %xmm10, %xmm0
        shll      $23, %esi
        addsd     %xmm12, %xmm0
        addl      $1065353216, %esi
        movl      %esi, 24(%rsp)
        cvtss2sd  24(%rsp), %xmm13
        mulsd     %xmm13, %xmm0
        movsd     %xmm11, (%rsp)
        movsd     %xmm0, 8(%rsp)
..B1.13:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.14:
        lea       _range(%rip), %r8
        movl      %edx, %ecx
        shrl      $31, %ecx
        cmpl      4(%r8,%rcx,8), %eax
        jb        ..B1.17
..B1.15:
        jne       ..B1.28
..B1.16:
        movl      40(%rsp), %edi
        cmpl      (%r8,%rcx,8), %edi
        ja        ..B1.28
..B1.17:
        movsd     40(%rsp), %xmm0
        lea       _TWO_52H(%rip), %rdi
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        lea       _two_32p1(%rip), %r9
        movsd     (%rdi), %xmm1
        lea       _two_32(%rip), %r10
        movsd     .L_2il0floatpacket.4(%rip), %xmm9
        lea       SC2_BIAS(%rip), %r11
        movsd     .L_2il0floatpacket.2(%rip), %xmm4
        addsd     %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movsd     16(%rsp), %xmm8
        movsd     (%r9), %xmm2
        subsd     %xmm1, %xmm8
        mulsd     .L_2il0floatpacket.1(%rip), %xmm8
        movsd     .L_2il0floatpacket.7(%rip), %xmm1
        movl      16(%rsp), %edx
        movl      %edx, %eax
        movsd     .L_2il0floatpacket.3(%rip), %xmm3
        movl      %edx, %r8d
        shll      $25, %eax
        addsd     40(%rsp), %xmm8
        movaps    %xmm8, %xmm7
        movaps    %xmm8, %xmm5
        mulsd     %xmm8, %xmm7
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm1
        addsd     .L_2il0floatpacket.5(%rip), %xmm9
        addsd     .L_2il0floatpacket.8(%rip), %xmm1
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm1
        addsd     .L_2il0floatpacket.6(%rip), %xmm9
        addsd     .L_2il0floatpacket.9(%rip), %xmm1
        mulsd     %xmm7, %xmm9
        mulsd     %xmm7, %xmm1
        movsd     %xmm8, (%rsp)
        movl      $0, (%rsp)
        movsd     (%rsp), %xmm6
        sarl      $25, %eax
        subsd     %xmm6, %xmm5
        mulsd     %xmm4, %xmm6
        addsd     .L_2il0floatpacket.10(%rip), %xmm1
        mulsd     %xmm4, %xmm5
        addsd     %xmm6, %xmm2
        mulsd     %xmm8, %xmm1
        addsd     %xmm5, %xmm9
        movsd     %xmm2, 16(%rsp)
        subl      %eax, %r8d
        movsd     16(%rsp), %xmm11
        movsd     %xmm6, (%rsp)
        movslq    %eax, %rax
        subsd     (%r10), %xmm11
        shlq      $4, %rax
        addsd     %xmm11, %xmm3
        movq      __libm_exp_table_128@GOTPCREL(%rip), %r9
        subsd     %xmm3, %xmm6
        movsd     1032(%r9,%rax), %xmm2
        addsd     %xmm6, %xmm9
        shrl      $7, %r8d
        addsd     %xmm9, %xmm1
        movaps    %xmm1, %xmm10
        mulsd     %xmm2, %xmm1
        addsd     %xmm11, %xmm10
        mulsd     %xmm11, %xmm2
        mulsd     1024(%r9,%rax), %xmm10
        movslq    (%r11,%rcx,4), %rdi
        addsd     %xmm10, %xmm1
        addq      %r8, %rdi
        movaps    %xmm1, %xmm0
        shlq      $52, %rdi
        addsd     %xmm2, %xmm0
        movq      %rdi, 24(%rsp)
        cmpl      $-130815, %edx
        jg        ..B1.26
..B1.18:
        movsd     %xmm0, (%rsp)
        andl      $-4096, (%rsp)
        movsd     (%rsp), %xmm0
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        subsd     %xmm0, %xmm2
        cmpl      $768, %eax
        addsd     %xmm1, %xmm2
        je        ..B1.22
..B1.19:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.20:
        fldcw     32(%rsp)
..B1.21:
        movsd     (%rsp), %xmm0
        movb      $1, %sil
..B1.22:
        fldl      24(%rsp)
        lea       _SC2(%rip), %rax
        movsd     %xmm0, (%rsp)
        testb     %sil, %sil
        fldl      (%rsp)
        movsd     %xmm2, (%rsp)
        fldl      (%rsp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmull     (%rax,%rcx,8)
        fstpl     8(%rsp)
        je        ..B1.24
..B1.23:
        fldcw     34(%rsp)
..B1.24:
        cmpl      $1048576, 12(%rsp)
        jl        ..B1.8
..B1.25:
        movsd     8(%rsp), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.26:
        movsd     24(%rsp), %xmm1
        lea       _SC2(%rip), %rax
        mulsd     %xmm1, %xmm0
        mulsd     (%rax,%rcx,8), %xmm0
        movsd     %xmm0, 8(%rsp)
..B1.27:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.28:
        cmpl      $2146435072, %eax
        jae       ..B1.33
..B1.29:
        testl     $-2147483648, %edx
        je        ..B1.31
..B1.30:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 8(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.31:
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 8(%rsp)
..B1.32:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.33:
        addl      $-2146435072, %eax
        orl       40(%rsp), %eax
        jne       ..B1.35
..B1.34:
        lea       _inf_zero(%rip), %rax
        movsd     (%rax,%rcx,8), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.35:
        movsd     40(%rsp), %xmm0
        addsd     %xmm0, %xmm0
..B1.36:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.37:
        addl      $1074, %ecx
        movl      $1, %eax
        shlq      %cl, %rax
        movq      %rax, 8(%rsp)
        jmp       ..B1.8
        .align    16,0x90
	.cfi_endproc
	.type	exp2,@function
	.size	exp2,.-exp2
	.data
# -- End  exp2
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x40600000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0xbf800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3fe62e42
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xac59a345,0x3f24308c
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x6fba31d1,0x3f83b2ab
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xff82c58f,0x3fcebfbd
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00cb0678,0x3ef0097a
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0xe87ed439,0x3f55d87f
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0xd704a0c3,0x3fac6b08
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x3de6af28,0x3e9fdf47
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_two_32p1:
	.long	1048576
	.long	1106247680
	.type	_two_32p1,@object
	.size	_two_32p1,8
	.align 4
_two_32:
	.long	0
	.long	1106247680
	.type	_two_32,@object
	.size	_two_32,8
	.align 4
_range:
	.long	4294967295
	.long	1083179007
	.long	4294967295
	.long	1083232255
	.type	_range,@object
	.size	_range,16
	.align 4
SC2_BIAS:
	.long	511
	.long	1535
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_SC2:
	.long	0
	.long	1609564160
	.long	0
	.long	535822336
	.type	_SC2,@object
	.size	_SC2,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_inf_zero:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zero,@object
	.size	_inf_zero,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
