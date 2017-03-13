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
	.file "exp10f_gen.c"
	.text
..TXTST0:
# -- Begin  exp10f
	.text
       .align    16,0x90
	.globl exp10f
exp10f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_exp10f.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        movl      %edx, %esi
        movl      %edx, %ecx
        andl      $2147483647, %esi
        shrl      $31, %ecx
        cmpl      $8388608, %esi
        jb        ..B1.17
..B1.2:
        movl      %ecx, %edi
        lea       range(%rip), %rax
        cmpl      (%rax,%rdi,4), %esi
        ja        ..B1.8
..B1.3:
        movss     .L_2il0floatpacket.0(%rip), %xmm1
        cmpl      $1065353216, %edx
        jl        ..B1.7
..B1.4:
        movl      %esi, %ecx
        shrl      $23, %ecx
        addl      $10, %ecx
        shll      %cl, %esi
        testl     %esi, %esi
        jne       ..B1.7
..B1.5:
        addss     %xmm1, %xmm0
        movss     %xmm0, -16(%rsp)
        movl      -16(%rsp), %eax
        shll      $10, %eax
        sarl      $10, %eax
        cmpl      $10, %eax
        jg        ..B1.7
..B1.6:
        movslq    %eax, %rax
        lea       exact_values(%rip), %rdx
        movss     (%rdx,%rax,4), %xmm0
        ret       
..B1.7:
        pxor      %xmm3, %xmm3
        movq      $0x3ff0000000000000, %rcx
        cvtss2sd  -8(%rsp), %xmm3
        movaps    %xmm3, %xmm0
        mulsd     .L_2il0floatpacket.1(%rip), %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movq      __libm_expf_table_64@GOTPCREL(%rip), %rsi
        addsd     .L_2il0floatpacket.2(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        movss     -16(%rsp), %xmm2
        movsd     .L_2il0floatpacket.4(%rip), %xmm0
        subss     %xmm1, %xmm2
        cvtss2sd  %xmm2, %xmm2
        mulsd     .L_2il0floatpacket.3(%rip), %xmm2
        movl      -16(%rsp), %edx
        addsd     %xmm2, %xmm3
        movaps    %xmm3, %xmm1
        andl      $4194303, %edx
        mulsd     %xmm3, %xmm1
        movl      %edx, %eax
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm3, %xmm4
        shll      $26, %eax
        addsd     %xmm4, %xmm0
        sarl      $26, %eax
        movslq    %eax, %rax
        subl      %eax, %edx
        shrl      $6, %edx
        movsd     256(%rsi,%rax,8), %xmm5
        mulsd     %xmm5, %xmm0
        shlq      $52, %rdx
        addsd     %xmm5, %xmm0
        addq      %rcx, %rdx
        movq      %rdx, -24(%rsp)
        mulsd     -24(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.8:
        cmpl      $2139095040, %esi
        jae       ..B1.13
..B1.9:
        testl     %ecx, %ecx
        je        ..B1.11
..B1.10:
        movss     .L_2il0floatpacket.8(%rip), %xmm0
        mulss     %xmm0, %xmm0
        ret       
..B1.11:
        movss     .L_2il0floatpacket.9(%rip), %xmm0
        mulss     %xmm0, %xmm0
..B1.12:
        ret       
..B1.13:
        jne       ..B1.15
..B1.14:
        lea       _inf_zero(%rip), %rax
        movss     (%rax,%rdi,4), %xmm0
        ret       
..B1.15:
        movss     -8(%rsp), %xmm0
..B1.16:
        ret       
..B1.17:
        movss     .L_2il0floatpacket.10(%rip), %xmm0
        addss     -8(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	exp10f,@function
	.size	exp10f,.-exp10f
	.data
# -- End  exp10f
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.1:
	.long	0x0979a371,0x406a934f
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x41680000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x509f79ff,0xbf734413
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xe2724acf,0x3ff2d784
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0xcd26a2f3,0x40053524
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xd3f4b8ec,0x400046f7
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0xbbb47127,0x40026bb1
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x4b400000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.8:
	.long	0x0d800000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,4
	.align 4
.L_2il0floatpacket.9:
	.long	0x71800000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,4
	.align 4
.L_2il0floatpacket.10:
	.long	0x3f800000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
	.align 4
range:
	.long	1109008539
	.long	1110823542
	.type	range,@object
	.size	range,8
	.align 4
exact_values:
	.long	0x00000000
	.long	0x41200000
	.long	0x42c80000
	.long	0x447a0000
	.long	0x461c4000
	.long	0x47c35000
	.long	0x49742400
	.long	0x4b189680
	.long	0x4cbebc20
	.long	0x4e6e6b28
	.long	0x501502f9
	.type	exact_values,@object
	.size	exact_values,44
	.align 4
_inf_zero:
	.long	2139095040
	.long	0
	.type	_inf_zero,@object
	.size	_inf_zero,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End

	.globl pow10f
	.equ pow10f, exp10f

