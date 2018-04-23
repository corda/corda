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
	.file "y0f.c"
	.text
..TXTST0:
# -- Begin  y0f
	.text
       .align    16,0x90
	.globl y0f
y0f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_y0f.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        movd      %xmm0, %ecx
        movss     %xmm0, 48(%rsp)
        lea       -1(%rcx), %eax
        cmpl      $2139095039, %eax
        jae       ..B1.13
..B1.2:
        cmpl      $1099978979, %ecx
        jae       ..B1.21
..B1.3:
        cmpl      $1094450314, %ecx
        jae       ..B1.12
..B1.4:
        cmpl      $1085128690, %ecx
        jae       ..B1.11
..B1.5:
        cmpl      $1074568695, %ecx
        jae       ..B1.10
..B1.6:
        cmpl      $1058642330, %ecx
        jb        ..B1.8
..B1.7:
        cvtss2sd  %xmm0, %xmm0
        lea       168+_P1(%rip), %rax
        lea       160+_P1(%rip), %rdx
        lea       144+_P1(%rip), %r9
        lea       128+_P1(%rip), %rcx
        lea       112+_P1(%rip), %r10
        lea       96+_P1(%rip), %rsi
        lea       80+_P1(%rip), %r11
        lea       64+_P1(%rip), %rdi
        movsd     (%rdx), %xmm6
        lea       32+_P1(%rip), %r8
        movsd     (%r9), %xmm1
        lea       16+_P1(%rip), %rdx
        lea       56+_P1(%rip), %r9
        subsd     (%rax), %xmm0
        movaps    %xmm0, %xmm2
        lea       48+_P1(%rip), %rax
        mulsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm3
        mulsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm1
        addsd     (%rcx), %xmm6
        addsd     (%r10), %xmm1
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm1
        addsd     (%rsi), %xmm6
        addsd     (%r11), %xmm1
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm1
        addsd     (%rdi), %xmm6
        addsd     (%rax), %xmm1
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm1
        addsd     (%r8), %xmm6
        addsd     (%rdx), %xmm1
        mulsd     %xmm3, %xmm6
        mulsd     %xmm2, %xmm1
        lea       152+_P1(%rip), %rsi
        lea       136+_P1(%rip), %r11
        lea       120+_P1(%rip), %rdi
        lea       104+_P1(%rip), %rax
        lea       88+_P1(%rip), %r8
        lea       72+_P1(%rip), %rdx
        lea       _P1(%rip), %rcx
        lea       24+_P1(%rip), %r10
        movsd     (%rsi), %xmm5
        lea       8+_P1(%rip), %rsi
        mulsd     %xmm3, %xmm5
        addsd     %xmm1, %xmm6
        movsd     (%r11), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     (%rdi), %xmm5
        addsd     (%rcx), %xmm6
        mulsd     %xmm3, %xmm5
        addsd     (%rax), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     (%r8), %xmm5
        mulsd     %xmm3, %xmm5
        addsd     (%rdx), %xmm4
        mulsd     %xmm3, %xmm4
        addsd     (%r9), %xmm5
        lea       40+_P1(%rip), %rcx
        mulsd     %xmm3, %xmm5
        addsd     (%rcx), %xmm4
        addsd     (%r10), %xmm5
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm5
        addsd     (%rsi), %xmm4
        mulsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm4
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.8:
        pxor      %xmm3, %xmm3
        cvtss2sd  48(%rsp), %xmm3
        movaps    %xmm3, %xmm0
        movsd     %xmm3, (%rsp)
..___tag_value_y0f.6:
        call      __libm_log_k32@PLT
..___tag_value_y0f.7:
..B1.25:
        movsd     (%rsp), %xmm3
..B1.9:
        mulsd     %xmm3, %xmm3
        lea       _tonpi(%rip), %rax
        movaps    %xmm3, %xmm2
        lea       32+_Q1(%rip), %rdx
        lea       24+_Q2(%rip), %r9
        lea       16+_Q2(%rip), %r11
        mulsd     %xmm3, %xmm2
        lea       24+_Q1(%rip), %rdi
        movsd     (%rax), %xmm6
        lea       16+_Q1(%rip), %rcx
        mulsd     %xmm0, %xmm6
        lea       8+_Q2(%rip), %r10
        movsd     (%rdx), %xmm0
        lea       _Q2(%rip), %rax
        movsd     (%r9), %xmm5
        lea       8+_Q1(%rip), %r8
        movsd     (%r11), %xmm4
        lea       _Q1(%rip), %rsi
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     (%rcx), %xmm0
        addsd     (%r10), %xmm5
        addsd     (%rax), %xmm4
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        mulsd     %xmm3, %xmm4
        addsd     (%rsi), %xmm0
        addsd     %xmm4, %xmm5
        movsd     (%rdi), %xmm1
        mulsd     %xmm2, %xmm1
        mulsd     %xmm6, %xmm5
        addsd     (%r8), %xmm1
        mulsd     %xmm3, %xmm1
        addsd     %xmm1, %xmm0
        addsd     %xmm5, %xmm0
        addsd     %xmm6, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.10:
        pxor      %xmm0, %xmm0
        lea       144+_P2(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        lea       136+_P2(%rip), %rdx
        lea       120+_P2(%rip), %r9
        lea       104+_P2(%rip), %rcx
        lea       88+_P2(%rip), %r10
        lea       72+_P2(%rip), %rsi
        lea       56+_P2(%rip), %r11
        lea       40+_P2(%rip), %rdi
        lea       8+_P2(%rip), %r8
        movsd     (%rdx), %xmm6
        lea       128+_P2(%rip), %rdx
        movsd     (%r9), %xmm1
        lea       112+_P2(%rip), %r9
        subsd     (%rax), %xmm0
        lea       24+_P2(%rip), %rax
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        addsd     (%rcx), %xmm6
        addsd     (%r10), %xmm1
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        addsd     (%rsi), %xmm6
        addsd     (%r11), %xmm1
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        addsd     (%rdi), %xmm6
        addsd     (%rax), %xmm1
        mulsd     %xmm2, %xmm6
        mulsd     %xmm3, %xmm1
        addsd     (%r8), %xmm6
        mulsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm6
        movsd     (%rdx), %xmm5
        lea       96+_P2(%rip), %rcx
        mulsd     %xmm2, %xmm5
        lea       80+_P2(%rip), %r10
        addsd     %xmm1, %xmm6
        addsd     (%rcx), %xmm5
        movsd     (%r9), %xmm4
        lea       64+_P2(%rip), %rsi
        mulsd     %xmm2, %xmm4
        lea       48+_P2(%rip), %r11
        mulsd     %xmm2, %xmm5
        addsd     (%r10), %xmm4
        addsd     (%rsi), %xmm5
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm5
        addsd     (%r11), %xmm4
        lea       32+_P2(%rip), %rdi
        lea       16+_P2(%rip), %rax
        mulsd     %xmm2, %xmm4
        lea       _P2(%rip), %r8
        addsd     (%rdi), %xmm5
        addsd     (%rax), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm3, %xmm4
        addsd     (%r8), %xmm5
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.11:
        cmpl      $1091143997, %ecx
        pxor      %xmm0, %xmm0
        lea       _P3(%rip), %rdx
        lea       _P4(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        jb        ..L12
        movq      %rax, %rdx
..L12:
        movsd     104(%rdx), %xmm6
        movsd     88(%rdx), %xmm1
        movsd     96(%rdx), %xmm5
        movsd     80(%rdx), %xmm4
        subsd     112(%rdx), %xmm0
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     72(%rdx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     56(%rdx), %xmm1
        addsd     64(%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        addsd     48(%rdx), %xmm4
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     40(%rdx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     24(%rdx), %xmm1
        addsd     32(%rdx), %xmm5
        mulsd     %xmm2, %xmm6
        addsd     16(%rdx), %xmm4
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm5
        addsd     8(%rdx), %xmm6
        mulsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm1
        addsd     (%rdx), %xmm5
        mulsd     %xmm0, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm6
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.12:
        cmpl      $1097751532, %ecx
        pxor      %xmm3, %xmm3
        lea       _P5(%rip), %rdx
        lea       _P6(%rip), %rax
        cvtss2sd  48(%rsp), %xmm3
        jb        ..L15
        movq      %rax, %rdx
..L15:
        movsd     80(%rdx), %xmm6
        movsd     88(%rdx), %xmm5
        movsd     96(%rdx), %xmm0
        movsd     72(%rdx), %xmm4
        subsd     104(%rdx), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm6
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm0
        addsd     48(%rdx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     56(%rdx), %xmm5
        addsd     64(%rdx), %xmm0
        mulsd     %xmm2, %xmm6
        addsd     40(%rdx), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm0
        addsd     16(%rdx), %xmm6
        mulsd     %xmm2, %xmm4
        addsd     24(%rdx), %xmm5
        addsd     32(%rdx), %xmm0
        mulsd     %xmm1, %xmm6
        addsd     8(%rdx), %xmm4
        mulsd     %xmm1, %xmm5
        mulsd     %xmm2, %xmm0
        addsd     (%rdx), %xmm6
        mulsd     %xmm3, %xmm4
        mulsd     %xmm3, %xmm5
        addsd     %xmm0, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.13:
        movl      %ecx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.20
..B1.14:
        testl     %eax, %eax
        je        ..B1.19
..B1.15:
        testl     $-2147483648, %ecx
        pxor      %xmm0, %xmm0
        je        ..B1.18
..B1.16:
        movss     .L_2il0floatpacket.1(%rip), %xmm1
        mulss     %xmm1, %xmm0
..B1.18:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.19:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.20:
        movss     48(%rsp), %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.21:
        pxor      %xmm0, %xmm0
        lea       _val_16_0(%rip), %rax
        cvtss2sd  48(%rsp), %xmm0
        movsd     .L_2il0floatpacket.2(%rip), %xmm1
        lea       (%rsp), %rdi
        divsd     %xmm0, %xmm1
        movsd     (%rax), %xmm2
        lea       8(%rsp), %rsi
        mulsd     %xmm1, %xmm2
        movl      $-1, %edx
        movsd     %xmm2, 8(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm2, 24(%rsi)
        mulsd     %xmm2, %xmm2
        movsd     %xmm1, 16(%rsi)
        movsd     %xmm2, 32(%rsi)
..___tag_value_y0f.24:
        call      __libm_sincos_k32@PLT
..___tag_value_y0f.25:
..B1.22:
        lea       40+_QP(%rip), %r10
        lea       40+_PP(%rip), %rdx
        movsd     40(%rsp), %xmm2
        lea       24+_QP(%rip), %r11
        lea       24+_PP(%rip), %rcx
        lea       32+_PP(%rip), %rdi
        lea       _tonpi(%rip), %rax
        lea       16+_PP(%rip), %r8
        movsd     (%r10), %xmm5
        lea       8+_PP(%rip), %rsi
        movsd     (%rdx), %xmm6
        lea       32+_QP(%rip), %rdx
        mulsd     %xmm2, %xmm5
        lea       _PP(%rip), %r9
        mulsd     %xmm2, %xmm6
        addsd     (%r11), %xmm5
        addsd     (%rcx), %xmm6
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm6
        movsd     (%rdx), %xmm4
        lea       16+_QP(%rip), %rcx
        mulsd     %xmm2, %xmm4
        addsd     (%rsi), %xmm6
        movsd     (%rdi), %xmm1
        lea       _QP(%rip), %rsi
        mulsd     %xmm2, %xmm1
        addsd     (%rcx), %xmm4
        movsd     (%rax), %xmm0
        lea       8+_QP(%rip), %rax
        mulsd     %xmm2, %xmm4
        addsd     (%r8), %xmm1
        mulsd     24(%rsp), %xmm0
        addsd     (%rax), %xmm5
        mulsd     %xmm2, %xmm1
        addsd     (%rsi), %xmm4
        sqrtsd    %xmm0, %xmm0
        addsd     (%r9), %xmm1
        movsd     32(%rsp), %xmm3
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        addsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm6
        mulsd     16(%rsp), %xmm5
        mulsd     (%rsp), %xmm6
        mulsd     8(%rsp), %xmm5
        addsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	y0f,@function
	.size	y0f,.-y0f
	.data
# -- End  y0f
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x7f800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_P1:
	.long	4195291908
	.long	3162175307
	.long	406986291
	.long	1072440375
	.long	2776221165
	.long	3219095096
	.long	1133938471
	.long	1070349075
	.long	2954828429
	.long	3217879437
	.long	2763399148
	.long	1070335612
	.long	808830809
	.long	3217701246
	.long	1570268664
	.long	1070154462
	.long	3296077481
	.long	3217609508
	.long	420330990
	.long	1070118798
	.long	773510171
	.long	3217614074
	.long	4056659129
	.long	1070174592
	.long	3219431954
	.long	3217750970
	.long	3785879815
	.long	1070358232
	.long	574751405
	.long	3217779900
	.long	4260549547
	.long	1069957964
	.long	2938320462
	.long	3216782740
	.long	3378329138
	.long	1068120728
	.long	1915664650
	.long	3213929269
	.long	3426344990
	.long	1063895342
	.long	349103460
	.long	3207738231
	.long	3100907498
	.long	1072470062
	.type	_P1,@object
	.size	_P1,176
	.align 4
_tonpi:
	.long	1841940611
	.long	1071931184
	.type	_tonpi,@object
	.size	_tonpi,8
	.align 4
_Q1:
	.long	2580172613
	.long	3216172246
	.long	1084453148
	.long	1069988811
	.long	2056290550
	.long	3213915569
	.long	1236657422
	.long	1061266913
	.long	2321867150
	.long	3202594720
	.type	_Q1,@object
	.size	_Q1,40
	.align 4
_Q2:
	.long	4294648676
	.long	3218079743
	.long	3841535415
	.long	1066401791
	.long	664070481
	.long	3208409496
	.long	1981210199
	.long	1054621065
	.type	_Q2,@object
	.size	_Q2,32
	.align 4
_P2:
	.long	1454683265
	.long	3163093641
	.long	1453379447
	.long	3218719554
	.long	687601567
	.long	1068108233
	.long	2612704149
	.long	1068365525
	.long	3658933664
	.long	3212579183
	.long	2932711330
	.long	3210863403
	.long	1642366143
	.long	1059690534
	.long	3271323791
	.long	1057665714
	.long	1907282597
	.long	3201598976
	.long	379771377
	.long	3197347542
	.long	4245487245
	.long	3193415108
	.long	3608497393
	.long	1045674492
	.long	2931231697
	.long	3190739602
	.long	990015633
	.long	1041359383
	.long	1316300206
	.long	3186258158
	.long	3640313785
	.long	3183477336
	.long	2399178872
	.long	3182342144
	.long	1279629276
	.long	1034489495
	.long	1301829276
	.long	1074768211
	.type	_P2,@object
	.size	_P2,152
	.align 4
_P3:
	.long	2564005090
	.long	1014927793
	.long	2691273771
	.long	1070806220
	.long	300870104
	.long	3214257910
	.long	1691761700
	.long	3215496860
	.long	2795355208
	.long	1063989012
	.long	2286168356
	.long	1063375710
	.long	3010782666
	.long	3206704598
	.long	629642940
	.long	3204978559
	.long	4085313989
	.long	1053206786
	.long	1695842824
	.long	1050888907
	.long	399213285
	.long	3194002865
	.long	3012936744
	.long	3191097259
	.long	3874678520
	.long	1039019405
	.long	2821019721
	.long	1035940102
	.long	3303481603
	.long	1075599389
	.type	_P3,@object
	.size	_P3,120
	.align 4
_P4:
	.long	3357785889
	.long	3165432791
	.long	3430075659
	.long	3218077237
	.long	1374220014
	.long	1065943908
	.long	2803440381
	.long	1067771495
	.long	3788987270
	.long	3210752606
	.long	378839180
	.long	3210732089
	.long	3385429871
	.long	1058535500
	.long	2609792408
	.long	1057402845
	.long	2316098656
	.long	3200270393
	.long	2785606124
	.long	3198332594
	.long	1489685130
	.long	1046034133
	.long	973177683
	.long	1043605688
	.long	113866720
	.long	3186126026
	.long	1355313597
	.long	3183357512
	.long	899972440
	.long	1076130263
	.type	_P4,@object
	.size	_P4,120
	.align 4
_P5:
	.long	1219042543
	.long	1017436670
	.long	659497129
	.long	1070330666
	.long	2213395954
	.long	3212885037
	.long	920472610
	.long	3215092907
	.long	483400048
	.long	1062596670
	.long	3012498212
	.long	1063037606
	.long	1375970636
	.long	3205547500
	.long	3836089736
	.long	3204771982
	.long	199192965
	.long	1052233910
	.long	194857414
	.long	1050781561
	.long	2407988550
	.long	3193126966
	.long	3806101946
	.long	3190982557
	.long	3164279536
	.long	1038193510
	.long	3298944842
	.long	1076541665
	.type	_P5,@object
	.size	_P5,112
	.align 4
_P6:
	.long	4139807906
	.long	1017960487
	.long	1501500641
	.long	3217630659
	.long	2447579003
	.long	1064854100
	.long	511348540
	.long	1067492433
	.long	1810619958
	.long	3209696179
	.long	1806624601
	.long	3210355872
	.long	4035698547
	.long	1057552651
	.long	467530437
	.long	1057185016
	.long	2985801821
	.long	3199346510
	.long	2460583618
	.long	3198194818
	.long	4117830692
	.long	1045310355
	.long	1133672721
	.long	1043441078
	.long	2717688296
	.long	3185336108
	.long	1946169876
	.long	1076920380
	.type	_P6,@object
	.size	_P6,112
	.align 4
_val_16_0:
	.long	0
	.long	1076887552
	.type	_val_16_0,@object
	.size	_val_16_0,8
	.align 4
_QP:
	.long	4294921804
	.long	3212836863
	.long	4153409851
	.long	1056096255
	.long	2226361937
	.long	3196916170
	.long	3849695089
	.long	1044094312
	.long	663042994
	.long	3187124278
	.long	3336948587
	.long	1035486718
	.type	_QP,@object
	.size	_QP,48
	.align 4
_PP:
	.long	4294966288
	.long	1072693247
	.long	4269912424
	.long	3207725055
	.long	1780799603
	.long	1052554744
	.long	2285933488
	.long	3194113879
	.long	3892950982
	.long	1041746526
	.long	150212775
	.long	3184818833
	.type	_PP,@object
	.size	_PP,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
