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
	.file "cexpf.c"
	.text
..TXTST0:
# -- Begin  cexpf
	.text
       .align    16,0x90
	.globl cexpf
cexpf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cexpf.1:
..L2:

..B1.2:
        movq      %rsp, %rax
        subq      $216, %rsp
	.cfi_def_cfa_offset 224
        movq      %rax, 120(%rsp)
        movss     %xmm0, 96(%rsp)
        pshufd    $1, %xmm0, %xmm1
        movss     %xmm1, 104(%rsp)
        movq      %rbx, 128(%rsp)
        cvtss2sd  104(%rsp), %xmm0
        cvtss2sd  96(%rsp), %xmm7
        lea       cexpf_table(%rip), %r8
        movl      104(%rsp), %ecx
        andl      $2147483647, %ecx
        subl      $1, %ecx
        cmpl      $1202716671, %ecx
        movl      96(%rsp), %eax
        jae       .L_2TAG_PACKET_0.0.1
        movq      %xmm7, 72(%rsp)
        mulsd     5136(%r8), %xmm7
        andl      $2147483647, %eax
        unpcklpd  %xmm0, %xmm0
        movapd    5280(%r8), %xmm1
        cmpl      $1132462080, %eax
        addsd     5120(%r8), %xmm7
        jg        .L_2TAG_PACKET_1.0.1
        mulpd     %xmm0, %xmm1
        cvtsd2si  %xmm1, %edx
        addpd     5296(%r8), %xmm1
        movapd    5312(%r8), %xmm3
        subpd     5296(%r8), %xmm1
        movd      %xmm7, %ecx
        movq      %xmm7, %xmm4
        movapd    5328(%r8), %xmm2
        andq      $127, %rcx
        movq      (%r8,%rcx,8), %xmm5
        mulpd     %xmm1, %xmm3
        addl      $1865216, %edx
        andq      $63, %rdx
        lea       1024(%r8), %rax
        shlq      $6, %rdx
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm2
        paddd     5200(%r8), %xmm4
        subpd     %xmm3, %xmm0
        psllq     $45, %xmm4
        mulpd     5344(%r8), %xmm1
        pand      5216(%r8), %xmm4
        movapd    %xmm0, %xmm3
        subpd     %xmm2, %xmm0
        movapd    5248(%r8), %xmm6
        orpd      %xmm5, %xmm4
        movapd    16(%rax), %xmm5
        subpd     %xmm0, %xmm3
        movapd    %xmm0, 48(%rsp)
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subpd     %xmm2, %xmm3
        movapd    (%rax), %xmm2
        subpd     %xmm3, %xmm1
        movq      %xmm4, 64(%rsp)
        subsd     5120(%r8), %xmm7
        movapd    48(%rax), %xmm3
        addpd     %xmm3, %xmm2
        subpd     %xmm2, %xmm5
        mulpd     %xmm5, %xmm1
        movapd    16(%rax), %xmm5
        mulpd     48(%rsp), %xmm2
        movq      72(%rsp), %xmm4
        mulsd     5152(%r8), %xmm7
        mulpd     %xmm0, %xmm6
        mulpd     48(%rsp), %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm5
        subsd     %xmm7, %xmm4
        movq      5168(%r8), %xmm7
        mulpd     %xmm0, %xmm0
        movapd    %xmm5, 16(%rsp)
        movapd    5264(%r8), %xmm5
        mulsd     %xmm4, %xmm7
        addpd     5232(%r8), %xmm6
        mulpd     %xmm0, %xmm5
        movapd    %xmm3, 32(%rsp)
        addpd     16(%rax), %xmm3
        addsd     5184(%r8), %xmm7
        addpd     %xmm5, %xmm6
        movapd    (%rax), %xmm0
        movsd     %xmm6, %xmm5
        unpckhpd  %xmm6, %xmm6
        unpcklpd  %xmm5, %xmm5
        mulsd     %xmm4, %xmm7
        mulpd     16(%rsp), %xmm6
        mulpd     48(%rsp), %xmm0
        mulpd     %xmm5, %xmm2
        mulsd     %xmm4, %xmm7
        movapd    %xmm0, (%rsp)
        addpd     %xmm3, %xmm0
        movapd    16(%rax), %xmm5
        addsd     %xmm4, %xmm7
        subpd     %xmm3, %xmm5
        subpd     %xmm0, %xmm3
        addpd     32(%rax), %xmm1
        mulsd     64(%rsp), %xmm7
        addpd     32(%rsp), %xmm5
        addpd     (%rsp), %xmm3
        addpd     %xmm5, %xmm1
        addpd     %xmm3, %xmm1
        addsd     64(%rsp), %xmm7
        addpd     %xmm2, %xmm1
        addpd     %xmm6, %xmm1
        addpd     %xmm0, %xmm1
        movsd     %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
        mulsd     %xmm7, %xmm0
        mulsd     %xmm7, %xmm1
        cvtsd2ss  %xmm0, %xmm0
        cvtsd2ss  %xmm1, %xmm1
        movd      %xmm0, %edx
        movd      %xmm1, %eax
        movd      %xmm0, %ecx
        andl      $2139095040, %ecx
        je        .L_2TAG_PACKET_2.0.1
        movd      %xmm1, %ecx
        andl      $2139095040, %ecx
        je        .L_2TAG_PACKET_2.0.1
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_2.0.1:
        movss     _FLT_MIN_NORMAL(%rip), %xmm0
        mulss     %xmm0, %xmm0
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_0.0.1:
        cmpl      $-1, %ecx
        je        .L_2TAG_PACKET_3.0.1
        cmpl      $2139095039, %ecx
        jg        .L_2TAG_PACKET_4.0.1
        je        .L_2TAG_PACKET_5.0.1
        cvtss2sd  104(%rsp), %xmm0
        lea       80(%rsp), %rdi
        lea       88(%rsp), %rsi
        call      sincos@PLT
        cvtss2sd  96(%rsp), %xmm0
        call      exp@PLT
        movq      %xmm0, %xmm1
        mulsd     88(%rsp), %xmm0
        mulsd     80(%rsp), %xmm1
        cvtsd2ss  %xmm0, %xmm0
        cvtsd2ss  %xmm1, %xmm1
        movd      %xmm0, %eax
        movd      %xmm1, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_5.0.1:
        movl      $2147483647, %ecx
        movss     104(%rsp), %xmm1
        movss     96(%rsp), %xmm0
        andl      %eax, %ecx
        cmpl      $2139095040, %ecx
        jg        .L_2TAG_PACKET_6.0.1
        je        .L_2TAG_PACKET_7.0.1
        subss     %xmm1, %xmm1
        movd      %xmm1, %edx
        movl      %edx, %eax
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_7.0.1:
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_8.0.1
        movl      $0, %eax
        movl      $0, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_8.0.1:
        subss     %xmm1, %xmm1
        movl      $2139095040, %eax
        movd      %xmm1, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_6.0.1:
        mulss     %xmm0, %xmm0
        movd      %xmm0, %eax
        movl      %eax, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_4.0.1:
        movl      $2147483647, %ecx
        movss     104(%rsp), %xmm1
        andl      %eax, %ecx
        movss     96(%rsp), %xmm0
        cmpl      $2139095040, %ecx
        mulss     %xmm1, %xmm1
        jg        .L_2TAG_PACKET_9.0.1
        je        .L_2TAG_PACKET_10.0.1
        movd      %xmm1, %eax
        movl      %eax, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_10.0.1:
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_11.0.1
        movl      $0, %eax
        movl      $0, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_11.0.1:
        movl      $2139095040, %eax
        movd      %xmm1, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_9.0.1:
        mulss     %xmm0, %xmm0
        movd      %xmm0, %eax
        movd      %xmm1, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_3.0.1:
        movl      $2147483647, %ecx
        andl      %eax, %ecx
        je        .L_2TAG_PACKET_12.0.1
        cmpl      $2139095040, %ecx
        jg        .L_2TAG_PACKET_13.0.1
        je        .L_2TAG_PACKET_14.0.1
        cmpl      $1124073472, %ecx
        jg        .L_2TAG_PACKET_15.0.1
        movq      %xmm7, %xmm0
        mulsd     5136(%r8), %xmm7
        addsd     5120(%r8), %xmm7
        movd      %xmm7, %ecx
        movq      %xmm7, %xmm4
        andq      $127, %rcx
        movq      (%r8,%rcx,8), %xmm5
        paddd     5200(%r8), %xmm4
        subsd     5120(%r8), %xmm7
        mulsd     5152(%r8), %xmm7
        subsd     %xmm7, %xmm0
        movq      5168(%r8), %xmm7
        mulsd     %xmm0, %xmm7
        addsd     5184(%r8), %xmm7
        psllq     $45, %xmm4
        mulsd     %xmm0, %xmm7
        pand      5216(%r8), %xmm4
        mulsd     %xmm0, %xmm7
        orpd      %xmm5, %xmm4
        addsd     %xmm0, %xmm7
        mulsd     %xmm4, %xmm7
        addsd     %xmm4, %xmm7
        cvtsd2ss  %xmm7, %xmm7
        movd      %xmm7, %eax
        movl      104(%rsp), %edx
        andl      $-2147483648, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_12.0.1:
        movl      104(%rsp), %edx
        movl      $1065353216, %eax
        andl      $-2147483648, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_15.0.1:
        movl      $2139095039, %ecx
        testl     $-2147483648, %eax
        movl      $8388608, %eax
        cmovz     %ecx, %eax
        movl      104(%rsp), %edx
        andl      $2147483647, %eax
        andl      $-2147483648, %edx
        movd      %eax, %xmm0
        mulss     %xmm0, %xmm0
        movd      %xmm0, %eax
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_14.0.1:
        movl      $2139095040, %ecx
        andl      $-2147483648, %eax
        movl      104(%rsp), %edx
        cmovz     %ecx, %eax
        andl      $-2147483648, %edx
        andl      $2147483647, %eax
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_13.0.1:
        movss     96(%rsp), %xmm0
        movl      104(%rsp), %edx
        addss     %xmm0, %xmm0
        movd      %xmm0, %eax
        andl      $-2147483648, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_1.0.1:
        cmpl      $2139095040, %eax
        movl      96(%rsp), %ecx
        jg        .L_2TAG_PACKET_16.0.1
        movl      $8388608, %eax
        movl      $2139095039, %edx
        testl     $-2147483648, %ecx
        cmovz     %edx, %eax
        movl      $0, %edx
        cmpl      $-8388608, %ecx
        cmove     %edx, %eax
        cmpl      $2139095040, %ecx
        cmove     %ecx, %eax
        movd      %eax, %xmm7
        mulss     %xmm7, %xmm7
        mulpd     %xmm0, %xmm1
        cvtsd2si  %xmm1, %edx
        addpd     5296(%r8), %xmm1
        movapd    5312(%r8), %xmm3
        subpd     5296(%r8), %xmm1
        movapd    5328(%r8), %xmm2
        mulpd     %xmm1, %xmm3
        addl      $1865216, %edx
        andq      $63, %rdx
        lea       1024(%r8), %rax
        shlq      $6, %rdx
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm2
        subpd     %xmm3, %xmm0
        mulpd     5344(%r8), %xmm1
        movapd    %xmm0, %xmm3
        subpd     %xmm2, %xmm0
        movapd    5248(%r8), %xmm6
        movapd    16(%rax), %xmm5
        subpd     %xmm0, %xmm3
        movapd    %xmm0, 48(%rsp)
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subpd     %xmm2, %xmm3
        movapd    (%rax), %xmm2
        subpd     %xmm3, %xmm1
        movapd    48(%rax), %xmm3
        addpd     %xmm3, %xmm2
        subpd     %xmm2, %xmm5
        mulpd     %xmm5, %xmm1
        movapd    16(%rax), %xmm5
        mulpd     48(%rsp), %xmm2
        mulpd     %xmm0, %xmm6
        mulpd     48(%rsp), %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        movapd    %xmm5, 16(%rsp)
        movapd    5264(%r8), %xmm5
        addpd     5232(%r8), %xmm6
        mulpd     %xmm0, %xmm5
        movapd    %xmm3, 32(%rsp)
        addpd     16(%rax), %xmm3
        addpd     %xmm5, %xmm6
        movapd    (%rax), %xmm0
        movsd     %xmm6, %xmm5
        unpckhpd  %xmm6, %xmm6
        unpcklpd  %xmm5, %xmm5
        mulpd     16(%rsp), %xmm6
        mulpd     48(%rsp), %xmm0
        mulpd     %xmm5, %xmm2
        movapd    %xmm0, (%rsp)
        addpd     %xmm3, %xmm0
        movapd    16(%rax), %xmm5
        subpd     %xmm3, %xmm5
        subpd     %xmm0, %xmm3
        addpd     32(%rax), %xmm1
        addpd     32(%rsp), %xmm5
        addpd     (%rsp), %xmm3
        addpd     %xmm5, %xmm1
        addpd     %xmm3, %xmm1
        addpd     %xmm2, %xmm1
        addpd     %xmm6, %xmm1
        addpd     %xmm0, %xmm1
        movsd     %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
        cvtsd2ss  %xmm0, %xmm0
        cvtsd2ss  %xmm1, %xmm1
        mulss     %xmm7, %xmm0
        mulss     %xmm7, %xmm1
        movd      %xmm0, %edx
        movd      %xmm1, %eax
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
.L_2TAG_PACKET_16.0.1:
        movss     96(%rsp), %xmm0
        mulss     %xmm0, %xmm0
        movd      %xmm0, %eax
        movl      %eax, %edx
        shlq      $32, %rdx
        orq       %rdx, %rax
        shrq      $32, %rdx
        movd      %rax, %xmm0
        movq      128(%rsp), %rbx
        movq      120(%rsp), %rsp
        ret       
	.cfi_def_cfa_offset 8
..B1.3:
        .align    16,0x90
	.cfi_endproc
	.type	cexpf,@function
	.size	cexpf,.-cexpf
	.data
# -- End  cexpf
	.section .rodata, "a"
	.align 16
	.align 16
cexpf_table:
	.long	0
	.long	0
	.long	2851812149
	.long	5693
	.long	1048019041
	.long	11418
	.long	3899555717
	.long	17173
	.long	3541402996
	.long	22960
	.long	702412510
	.long	28779
	.long	410360776
	.long	34629
	.long	3402036099
	.long	40510
	.long	1828292879
	.long	46424
	.long	728909815
	.long	52370
	.long	852742562
	.long	58348
	.long	2952712987
	.long	64358
	.long	3490863953
	.long	70401
	.long	3228316108
	.long	76477
	.long	2930322912
	.long	82586
	.long	3366293073
	.long	88728
	.long	1014845819
	.long	94904
	.long	948735466
	.long	101113
	.long	3949972341
	.long	107355
	.long	2214878420
	.long	113632
	.long	828946858
	.long	119943
	.long	586995997
	.long	126288
	.long	2288159958
	.long	132667
	.long	2440944790
	.long	139081
	.long	1853186616
	.long	145530
	.long	1337108031
	.long	152014
	.long	1709341917
	.long	158533
	.long	3790955393
	.long	165087
	.long	4112506593
	.long	171677
	.long	3504003472
	.long	178303
	.long	2799960843
	.long	184965
	.long	2839424854
	.long	191663
	.long	171030293
	.long	198398
	.long	4232894513
	.long	205168
	.long	2992903935
	.long	211976
	.long	1603444721
	.long	218821
	.long	926591435
	.long	225703
	.long	1829099622
	.long	232622
	.long	887463927
	.long	239579
	.long	3272845541
	.long	246573
	.long	1276261410
	.long	253606
	.long	78413852
	.long	260677
	.long	569847338
	.long	267786
	.long	3645941911
	.long	274933
	.long	1617004845
	.long	282120
	.long	3978100823
	.long	289345
	.long	3049340112
	.long	296610
	.long	4040676318
	.long	303914
	.long	3577096743
	.long	311258
	.long	2583551245
	.long	318642
	.long	1990012071
	.long	326066
	.long	2731501122
	.long	333530
	.long	1453150082
	.long	341035
	.long	3395129871
	.long	348580
	.long	917841882
	.long	356167
	.long	3566716925
	.long	363794
	.long	3712504873
	.long	371463
	.long	2321106615
	.long	379174
	.long	363667784
	.long	386927
	.long	3111574537
	.long	394721
	.long	2956612997
	.long	402558
	.long	885834528
	.long	410438
	.long	2186617381
	.long	418360
	.long	3561793907
	.long	426325
	.long	1719614413
	.long	434334
	.long	1963711167
	.long	442386
	.long	1013258799
	.long	450482
	.long	4182873220
	.long	458621
	.long	3907805044
	.long	466805
	.long	1218806132
	.long	475034
	.long	1447192521
	.long	483307
	.long	1339972927
	.long	491625
	.long	1944781191
	.long	499988
	.long	19972402
	.long	508397
	.long	919555682
	.long	516851
	.long	1413356050
	.long	525351
	.long	2571947539
	.long	533897
	.long	1176749997
	.long	542490
	.long	2604962541
	.long	551129
	.long	3649726105
	.long	559815
	.long	1110089947
	.long	568549
	.long	380978316
	.long	577330
	.long	2568320822
	.long	586158
	.long	194117574
	.long	595035
	.long	2966275557
	.long	603959
	.long	3418903055
	.long	612932
	.long	2682146384
	.long	621954
	.long	1892288442
	.long	631025
	.long	2191782032
	.long	640145
	.long	434316067
	.long	649315
	.long	2069751141
	.long	658534
	.long	3964284211
	.long	667803
	.long	2990417245
	.long	677123
	.long	321958744
	.long	686494
	.long	1434058175
	.long	695915
	.long	3218338682
	.long	705387
	.long	2572866477
	.long	714911
	.long	697153126
	.long	724487
	.long	3092190715
	.long	734114
	.long	2380618042
	.long	743794
	.long	4076559943
	.long	753526
	.long	815859274
	.long	763312
	.long	2420883922
	.long	773150
	.long	1540824585
	.long	783042
	.long	3716502172
	.long	792987
	.long	1610600570
	.long	802987
	.long	777507147
	.long	813041
	.long	2483480501
	.long	823149
	.long	3706687593
	.long	833312
	.long	1432208378
	.long	843531
	.long	1242007932
	.long	853805
	.long	135105010
	.long	864135
	.long	3707479175
	.long	874520
	.long	382305176
	.long	884963
	.long	64696965
	.long	895462
	.long	4076975200
	.long	906017
	.long	863738719
	.long	916631
	.long	351641897
	.long	927302
	.long	3884662774
	.long	938030
	.long	4224142467
	.long	948817
	.long	2728693978
	.long	959663
	.long	764307441
	.long	970568
	.long	3999357479
	.long	981531
	.long	929806999
	.long	992555
	.long	1533953344
	.long	1003638
	.long	2912730644
	.long	1014781
	.long	2174652632
	.long	1025985
	.long	730821105
	.long	1037250
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	3217031168
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	3220176896
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	3219128320
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	3217031168
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	1069547520
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	1071644672
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	1072693248
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	1069547520
	.long	0
	.long	1127743488
	.long	0
	.long	0
	.long	1697350398
	.long	1080497479
	.long	0
	.long	0
	.long	4277811695
	.long	1064709698
	.long	0
	.long	0
	.long	3082479990
	.long	1069897045
	.long	0
	.long	0
	.long	4294966960
	.long	1071644671
	.long	0
	.long	0
	.long	130944
	.long	0
	.long	130944
	.long	0
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	1431655765
	.long	3217380693
	.long	0
	.long	3219128320
	.long	286331153
	.long	1065423121
	.long	1431655765
	.long	1067799893
	.long	436314138
	.long	3207201184
	.long	381774871
	.long	3210133868
	.long	1841940611
	.long	1076125488
	.long	1841940611
	.long	1076125488
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	1413480448
	.long	1069097467
	.long	1413480448
	.long	1069097467
	.long	442499072
	.long	1032893537
	.long	442499072
	.long	1032893537
	.long	771977331
	.long	996350346
	.long	771977331
	.long	996350346
	.type	cexpf_table,@object
	.size	cexpf_table,5360
	.align 16
_FLT_MIN_NORMAL:
	.long	8388608
	.long	8388608
	.type	_FLT_MIN_NORMAL,@object
	.size	_FLT_MIN_NORMAL,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
