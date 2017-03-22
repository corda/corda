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
	.file "clog.c"
	.text
..TXTST0:
# -- Begin  clog
	.text
       .align    16,0x90
	.globl clog
clog:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_clog.1:
..L2:

        movq      %fs:40, %rax
        subq      $120, %rsp
	.cfi_def_cfa_offset 128
        movsd     %xmm0, 40(%rsp)
        xorq      %rsp, %rax
        movsd     %xmm1, 48(%rsp)
        movzwl    46(%rsp), %edx
        andl      $32752, %edx
        movq      %rax, 104(%rsp)
        movzwl    54(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %edx
        shrl      $4, %eax
        cmpl      $2047, %edx
        jge       ..B1.26
..B1.2:
        cmpl      $2047, %eax
        jge       ..B1.62
..B1.3:
        movsd     40(%rsp), %xmm14
        movaps    %xmm14, %xmm7
        pxor      %xmm2, %xmm2
        movsd     48(%rsp), %xmm1
        andps     .L_2il0floatpacket.10(%rip), %xmm7
        movaps    %xmm1, %xmm6
        andps     .L_2il0floatpacket.10(%rip), %xmm6
        ucomisd   %xmm2, %xmm7
        jp        ..B1.4
        je        ..B1.40
..B1.4:
        comisd    %xmm7, %xmm6
        jbe       ..B1.6
..B1.5:
        movaps    %xmm7, %xmm2
        movsd     %xmm7, 32(%rsp)
        movaps    %xmm6, %xmm7
        movsd     %xmm6, (%rsp)
        movaps    %xmm2, %xmm6
        movsd     %xmm2, 8(%rsp)
        jmp       ..B1.7
..B1.6:
        movsd     %xmm6, 8(%rsp)
        movsd     %xmm7, (%rsp)
..B1.7:
        lea       8+_CONSTANTS(%rip), %rax
        comisd    (%rax), %xmm7
        jb        ..B1.19
..B1.8:
        lea       16+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm2
        comisd    %xmm7, %xmm2
        jb        ..B1.19
..B1.9:
        lea       40+_CONSTANTS(%rip), %rdx
        lea       _CONSTANTS(%rip), %rax
        movaps    %xmm7, %xmm5
        mulsd     %xmm7, %xmm5
        comisd    (%rdx), %xmm6
        movsd     .L_2il0floatpacket.11(%rip), %xmm2
        movsd     (%rax), %xmm4
        jbe       ..B1.12
..B1.10:
        movaps    %xmm6, %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm3, %xmm5
        subsd     %xmm2, %xmm5
        andps     .L_2il0floatpacket.10(%rip), %xmm5
        comisd    %xmm5, %xmm4
        ja        ..B1.13
        jmp       ..B1.18
..B1.12:
        subsd     %xmm2, %xmm5
        andps     .L_2il0floatpacket.10(%rip), %xmm5
        comisd    %xmm5, %xmm4
        jbe       ..B1.18
..B1.13:
        movzwl    14(%rsp), %eax
        andl      $32752, %eax
        cmpl      $14608, %eax
        jl        ..B1.15
..B1.14:
        movsd     .L_2il0floatpacket.8(%rip), %xmm13
        lea       56+_Q1(%rip), %rax
        movsd     .L_2il0floatpacket.8(%rip), %xmm10
        lea       48+_Q1(%rip), %rdx
        movsd     (%rsp), %xmm11
        lea       40+_Q1(%rip), %rcx
        movsd     .L_2il0floatpacket.9(%rip), %xmm4
        lea       32+_Q1(%rip), %rsi
        movsd     (%rax), %xmm6
        lea       24+_Q1(%rip), %rdi
        lea       16+_Q1(%rip), %r8
        lea       8+_Q1(%rip), %r9
        lea       _Q1(%rip), %r10
        mulsd     %xmm7, %xmm13
        movaps    %xmm13, %xmm15
        subsd     %xmm7, %xmm15
        movsd     %xmm15, 16(%rsp)
        movsd     16(%rsp), %xmm12
        subsd     %xmm12, %xmm13
        movsd     8(%rsp), %xmm12
        mulsd     %xmm12, %xmm10
        subsd     %xmm13, %xmm11
        movaps    %xmm10, %xmm9
        movaps    %xmm13, %xmm5
        mulsd     %xmm13, %xmm5
        subsd     %xmm12, %xmm9
        mulsd     %xmm4, %xmm13
        movsd     %xmm9, 16(%rsp)
        movaps    %xmm5, %xmm7
        movsd     16(%rsp), %xmm9
        mulsd     %xmm11, %xmm13
        subsd     %xmm9, %xmm10
        mulsd     %xmm11, %xmm11
        subsd     %xmm10, %xmm12
        movaps    %xmm10, %xmm9
        mulsd     %xmm10, %xmm9
        mulsd     %xmm4, %xmm10
        addsd     %xmm9, %xmm7
        mulsd     %xmm12, %xmm10
        mulsd     %xmm12, %xmm12
        movaps    %xmm7, %xmm4
        subsd     %xmm2, %xmm7
        subsd     %xmm5, %xmm4
        movsd     %xmm4, 16(%rsp)
        movaps    %xmm7, %xmm8
        movsd     16(%rsp), %xmm4
        subsd     %xmm4, %xmm9
        addsd     %xmm9, %xmm8
        movaps    %xmm8, %xmm4
        movaps    %xmm8, %xmm2
        movaps    %xmm8, %xmm5
        subsd     %xmm7, %xmm4
        addsd     %xmm13, %xmm2
        movsd     %xmm4, 16(%rsp)
        movaps    %xmm2, %xmm3
        movsd     16(%rsp), %xmm4
        movaps    %xmm2, %xmm15
        subsd     %xmm4, %xmm5
        addsd     %xmm10, %xmm3
        subsd     %xmm8, %xmm15
        movsd     %xmm5, 24(%rsp)
        subsd     %xmm15, %xmm13
        movsd     16(%rsp), %xmm4
        subsd     %xmm4, %xmm9
        movsd     24(%rsp), %xmm4
        subsd     %xmm4, %xmm7
        movaps    %xmm3, %xmm4
        addsd     %xmm7, %xmm9
        addsd     %xmm11, %xmm4
        movaps    %xmm4, %xmm5
        addsd     %xmm12, %xmm5
        mulsd     %xmm5, %xmm6
        movaps    %xmm5, %xmm7
        mulsd     %xmm5, %xmm7
        addsd     (%rdx), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%rcx), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%rsi), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%rdi), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%r8), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%r9), %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%r10), %xmm6
        mulsd     %xmm7, %xmm6
        movaps    %xmm2, %xmm7
        subsd     %xmm15, %xmm7
        movsd     %xmm7, 16(%rsp)
        movsd     16(%rsp), %xmm7
        subsd     %xmm7, %xmm8
        movaps    %xmm3, %xmm7
        addsd     %xmm8, %xmm13
        movaps    %xmm3, %xmm8
        subsd     %xmm2, %xmm8
        addsd     %xmm13, %xmm9
        subsd     %xmm8, %xmm7
        subsd     %xmm8, %xmm10
        movsd     %xmm7, 16(%rsp)
        movsd     16(%rsp), %xmm7
        subsd     %xmm7, %xmm2
        movaps    %xmm4, %xmm7
        subsd     %xmm3, %xmm7
        addsd     %xmm2, %xmm10
        subsd     %xmm7, %xmm11
        addsd     %xmm10, %xmm9
        movaps    %xmm4, %xmm2
        subsd     %xmm7, %xmm2
        movsd     %xmm2, 16(%rsp)
        movsd     16(%rsp), %xmm2
        subsd     %xmm2, %xmm3
        movaps    %xmm5, %xmm2
        addsd     %xmm3, %xmm11
        movaps    %xmm5, %xmm3
        subsd     %xmm4, %xmm3
        addsd     %xmm11, %xmm9
        subsd     %xmm3, %xmm2
        subsd     %xmm3, %xmm12
        movsd     %xmm2, 16(%rsp)
        movsd     16(%rsp), %xmm2
        subsd     %xmm2, %xmm4
        addsd     %xmm4, %xmm12
        addsd     %xmm12, %xmm9
        addsd     %xmm6, %xmm9
        addsd     %xmm9, %xmm5
        mulsd     .L_2il0floatpacket.7(%rip), %xmm5
        movsd     %xmm5, 32(%rsp)
        movsd     %xmm5, 56(%rsp)
        jmp       ..B1.22
..B1.15:
        movzwl    6(%rsp), %eax
        andl      $32752, %eax
        cmpl      $16368, %eax
        je        ..B1.31
..B1.16:
        movsd     %xmm5, 32(%rsp)
..B1.17:
        movsd     .L_2il0floatpacket.8(%rip), %xmm5
        lea       56+_Q1(%rip), %rax
        movsd     (%rsp), %xmm11
        lea       48+_Q1(%rip), %rdx
        lea       40+_Q1(%rip), %rcx
        lea       32+_Q1(%rip), %rsi
        lea       24+_Q1(%rip), %rdi
        lea       16+_Q1(%rip), %r8
        movsd     (%rax), %xmm13
        lea       8+_Q1(%rip), %r9
        lea       _Q1(%rip), %r10
        mulsd     %xmm7, %xmm5
        movaps    %xmm5, %xmm3
        subsd     %xmm7, %xmm3
        movsd     %xmm3, 16(%rsp)
        movsd     16(%rsp), %xmm4
        subsd     %xmm4, %xmm5
        movaps    %xmm5, %xmm8
        subsd     %xmm5, %xmm11
        mulsd     %xmm5, %xmm8
        subsd     %xmm2, %xmm8
        movsd     .L_2il0floatpacket.9(%rip), %xmm2
        mulsd     %xmm5, %xmm2
        mulsd     %xmm11, %xmm2
        movaps    %xmm8, %xmm15
        mulsd     %xmm11, %xmm11
        movaps    %xmm2, %xmm3
        addsd     %xmm11, %xmm3
        movaps    %xmm3, %xmm6
        addsd     %xmm3, %xmm15
        subsd     %xmm2, %xmm6
        mulsd     %xmm15, %xmm13
        movaps    %xmm15, %xmm9
        movaps    %xmm15, %xmm12
        movsd     %xmm6, 16(%rsp)
        subsd     %xmm8, %xmm9
        mulsd     %xmm15, %xmm12
        addsd     (%rdx), %xmm13
        mulsd     %xmm15, %xmm13
        movsd     16(%rsp), %xmm7
        movsd     %xmm9, 16(%rsp)
        subsd     %xmm7, %xmm11
        addsd     (%rcx), %xmm13
        mulsd     %xmm15, %xmm13
        movsd     16(%rsp), %xmm10
        addsd     (%rsi), %xmm13
        subsd     %xmm10, %xmm3
        mulsd     %xmm15, %xmm13
        addsd     %xmm11, %xmm3
        addsd     (%rdi), %xmm13
        mulsd     %xmm15, %xmm13
        addsd     (%r8), %xmm13
        mulsd     %xmm15, %xmm13
        addsd     (%r9), %xmm13
        mulsd     %xmm15, %xmm13
        addsd     (%r10), %xmm13
        mulsd     %xmm12, %xmm13
        addsd     %xmm13, %xmm3
        addsd     %xmm15, %xmm3
        mulsd     .L_2il0floatpacket.7(%rip), %xmm3
        movsd     %xmm3, 56(%rsp)
        jmp       ..B1.22
..B1.18:
        movsd     %xmm5, 32(%rsp)
..B1.19:
        movaps    %xmm14, %xmm0
        lea       72(%rsp), %rdi
..___tag_value_clog.4:
        call      __libm_hypot2_k64@PLT
..___tag_value_clog.5:
..B1.20:
        lea       24+_CONSTANTS(%rip), %rax
        lea       32+_CONSTANTS(%rip), %rdx
        lea       88(%rsp), %rdi
        movsd     (%rax), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     (%rdx), %xmm2
        mulsd     %xmm0, %xmm2
        movsd     %xmm1, -80(%rdi)
        movsd     -16(%rdi), %xmm0
        movsd     -8(%rdi), %xmm1
        movsd     %xmm2, (%rsp)
..___tag_value_clog.6:
        call      __libm_log_k64@PLT
..___tag_value_clog.7:
..B1.21:
        movsd     .L_2il0floatpacket.7(%rip), %xmm0
        movsd     88(%rsp), %xmm5
        mulsd     %xmm0, %xmm5
        movsd     8(%rsp), %xmm1
        movaps    %xmm1, %xmm6
        movsd     96(%rsp), %xmm4
        addsd     %xmm5, %xmm6
        mulsd     %xmm0, %xmm4
        movaps    %xmm6, %xmm2
        movsd     40(%rsp), %xmm14
        subsd     %xmm1, %xmm2
        movsd     %xmm2, 16(%rsp)
        movsd     16(%rsp), %xmm3
        movsd     48(%rsp), %xmm1
        subsd     %xmm3, %xmm5
        addsd     %xmm4, %xmm5
        addsd     (%rsp), %xmm5
        addsd     %xmm5, %xmm6
        movsd     %xmm6, 32(%rsp)
        movsd     %xmm6, 56(%rsp)
..B1.22:
        movaps    %xmm1, %xmm0
        movaps    %xmm14, %xmm1
        call      atan2@PLT
..B1.60:
        movsd     %xmm0, 64(%rsp)
        movzwl    70(%rsp), %eax
        testl     $32752, %eax
        jne       ..B1.29
..B1.23:
        testl     $1048575, 68(%rsp)
        jne       ..B1.25
..B1.24:
        cmpl      $0, 64(%rsp)
        je        ..B1.29
..B1.25:
        lea       56+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 16(%rsp)
        movsd     16(%rsp), %xmm1
        addsd     64(%rsp), %xmm1
        movsd     %xmm1, 64(%rsp)
        jmp       ..B1.29
..B1.26:
        je        ..B1.50
..B1.27:
        cmpl      $2047, %eax
        je        ..B1.43
..B1.28:
        lea       48+_CONSTANTS(%rip), %rax
        movsd     48(%rsp), %xmm0
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm1
        movsd     %xmm1, 56(%rsp)
        movsd     40(%rsp), %xmm1
        call      atan2@PLT
..B1.61:
        movsd     %xmm0, 64(%rsp)
..B1.29:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        movsd     56(%rsp), %xmm0
        movsd     64(%rsp), %xmm1
        cmpq      %fs:40, %rax
        jne       ..B1.42
..B1.30:
        addq      $120, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 128
..B1.31:
        testl     $1048575, 4(%rsp)
        jne       ..B1.16
..B1.33:
        cmpl      $0, (%rsp)
        jne       ..B1.16
..B1.35:
        movsd     .L_2il0floatpacket.7(%rip), %xmm2
        mulsd     %xmm6, %xmm2
        mulsd     %xmm2, %xmm6
        movsd     %xmm6, 32(%rsp)
        movzwl    38(%rsp), %eax
        testl     $32752, %eax
        jne       ..B1.39
..B1.36:
        testl     $1048575, 36(%rsp)
        jne       ..B1.38
..B1.37:
        cmpl      $0, 32(%rsp)
        je        ..B1.39
..B1.38:
        lea       56+_CONSTANTS(%rip), %rax
        movsd     (%rax), %xmm2
        mulsd     %xmm2, %xmm2
        movsd     %xmm2, 16(%rsp)
        movsd     16(%rsp), %xmm6
        addsd     32(%rsp), %xmm6
        movsd     %xmm6, 32(%rsp)
..B1.39:
        movsd     %xmm6, 56(%rsp)
        jmp       ..B1.22
..B1.40:
        ucomisd   %xmm2, %xmm6
        jne       ..B1.4
        jp        ..B1.4
..B1.41:
        movsd     .L_2il0floatpacket.6(%rip), %xmm3
        divsd     %xmm2, %xmm3
        movsd     %xmm6, 8(%rsp)
        movsd     %xmm7, (%rsp)
        movsd     %xmm3, 56(%rsp)
        jmp       ..B1.22
..B1.42:
        call      __stack_chk_fail@PLT
..B1.43:
        testl     $1048575, 52(%rsp)
        jne       ..B1.45
..B1.44:
        cmpl      $0, 48(%rsp)
        je        ..B1.28
..B1.45:
        movsd     48(%rsp), %xmm0
        cmpl      $2047, %edx
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 64(%rsp)
        je        ..B1.47
..B1.46:
        movsd     %xmm0, 56(%rsp)
        jmp       ..B1.29
..B1.47:
        testl     $1048575, 44(%rsp)
        jne       ..B1.46
..B1.48:
        cmpl      $0, 40(%rsp)
        jne       ..B1.46
..B1.49:
        movsd     40(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 56(%rsp)
        jmp       ..B1.29
..B1.50:
        testl     $1048575, 44(%rsp)
        jne       ..B1.52
..B1.51:
        cmpl      $0, 40(%rsp)
        je        ..B1.27
..B1.52:
        movsd     40(%rsp), %xmm1
        cmpl      $2047, %eax
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 64(%rsp)
        je        ..B1.54
..B1.53:
        movsd     48(%rsp), %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 56(%rsp)
        jmp       ..B1.29
..B1.54:
        testl     $1048575, 52(%rsp)
        jne       ..B1.53
..B1.55:
        cmpl      $0, 48(%rsp)
        jne       ..B1.53
..B1.56:
        movsd     48(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 56(%rsp)
        jmp       ..B1.29
..B1.62:
        cmpl      $2047, %edx
        je        ..B1.50
        jmp       ..B1.27
        .align    16,0x90
	.cfi_endproc
	.type	clog,@function
	.size	clog,.-clog
	.data
# -- End  clog
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.10:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
_CONSTANTS:
	.long	0
	.long	1065353216
	.long	1818952144
	.long	1072073202
	.long	133565931
	.long	1072697336
	.long	4277796864
	.long	1071001154
	.long	3164486458
	.long	1030551450
	.long	0
	.long	592445440
	.long	0
	.long	2146435072
	.long	0
	.long	1048576
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,64
	.align 16
_Q1:
	.long	0
	.long	3219128320
	.long	1431655765
	.long	1070945621
	.long	193
	.long	3218079744
	.long	2576980913
	.long	1070176665
	.long	1408920289
	.long	3217380693
	.long	2428535925
	.long	1069697316
	.long	1830244366
	.long	3217031283
	.long	3038314627
	.long	1069314732
	.type	_Q1,@object
	.size	_Q1,64
	.align 8
.L_2il0floatpacket.6:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x02000000,0x41a00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
