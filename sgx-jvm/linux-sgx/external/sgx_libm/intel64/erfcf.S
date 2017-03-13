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
	.file "erfcf.c"
	.text
..TXTST0:
# -- Begin  erfcf
	.text
       .align    16,0x90
	.globl erfcf
erfcf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_erfcf.1:
..L2:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        subq      $32, %rsp
	.cfi_def_cfa_offset 48
        movd      %xmm0, %r14d
        movss     %xmm0, 16(%rsp)
        movl      %r14d, %ecx
        andl      $2147483647, %ecx
        shrl      $31, %r14d
        cmpl      $2139095040, %ecx
        jae       ..B1.24
..B1.2:
        movl      %r14d, %eax
        lea       range(%rip), %rdx
        cmpl      (%rdx,%rax,4), %ecx
        jae       ..B1.21
..B1.3:
        cmpl      $1081081856, %ecx
        jae       ..B1.15
..B1.4:
        cmpl      $1073741824, %ecx
        jae       ..B1.11
..B1.5:
        cmpl      $1056964608, %ecx
        jae       ..B1.10
..B1.6:
        cmpl      $847249408, %ecx
        jae       ..B1.8
..B1.7:
        movss     .L_2il0floatpacket.3(%rip), %xmm4
        subss     %xmm0, %xmm4
        jmp       ..B1.9
..B1.8:
        cvtss2sd  %xmm0, %xmm0
        movaps    %xmm0, %xmm1
        lea       40+_A(%rip), %rax
        lea       48+_A(%rip), %rsi
        lea       24+_A(%rip), %rdx
        lea       32+_A(%rip), %rdi
        lea       8+_A(%rip), %rcx
        lea       16+_A(%rip), %r8
        lea       _A(%rip), %r9
        movsd     (%rax), %xmm4
        movsd     (%rsi), %xmm3
        mulsd     %xmm0, %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     (%rdx), %xmm4
        addsd     (%rdi), %xmm3
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     (%rcx), %xmm4
        addsd     (%r8), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm3
        mulsd     %xmm0, %xmm4
        addsd     (%r9), %xmm3
        addsd     .L_2il0floatpacket.4(%rip), %xmm4
        mulsd     %xmm0, %xmm3
        addsd     %xmm3, %xmm4
        cvtsd2ss  %xmm4, %xmm4
..B1.9:
        movaps    %xmm4, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.10:
        pxor      %xmm1, %xmm1
        lea       48+_AP(%rip), %rdx
        cvtss2sd  16(%rsp), %xmm1
        andps     .L_2il0floatpacket.5(%rip), %xmm1
        lea       32+_AP(%rip), %rcx
        movsd     (%rdx), %xmm6
        lea       16+_AP(%rip), %rsi
        lea       _AP(%rip), %rdi
        lea       56+_AP(%rip), %r8
        lea       48+_AQ(%rip), %r14
        lea       40+_AP(%rip), %r9
        lea       32+_AQ(%rip), %rdx
        lea       24+_AP(%rip), %r10
        lea       8+_AP(%rip), %r11
        pxor      %xmm5, %xmm5
        movsd     (%r8), %xmm2
        lea       40+_AQ(%rip), %r8
        movsd     (%r14), %xmm4
        lea       _erfc1(%rip), %r14
        subsd     .L_2il0floatpacket.4(%rip), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        mulsd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm4
        addsd     (%rcx), %xmm6
        addsd     (%r9), %xmm2
        addsd     (%rdx), %xmm4
        mulsd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm4
        addsd     (%rsi), %xmm6
        addsd     (%r10), %xmm2
        mulsd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm2
        addsd     (%rdi), %xmm6
        addsd     (%r11), %xmm2
        lea       56+_AQ(%rip), %rdi
        lea       24+_AQ(%rip), %r9
        lea       16+_AQ(%rip), %rcx
        lea       8+_AQ(%rip), %r10
        lea       _AQ(%rip), %rsi
        lea       _ones(%rip), %r11
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm4
        cvtss2sd  (%r11,%rax,4), %xmm5
        addsd     %xmm2, %xmm6
        mulsd     %xmm0, %xmm4
        movsd     (%rdi), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     (%rsi), %xmm4
        addsd     (%r8), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     (%r9), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     (%r10), %xmm3
        mulsd     %xmm1, %xmm3
        addsd     %xmm3, %xmm4
        divsd     %xmm4, %xmm6
        mulsd     %xmm5, %xmm6
        addsd     (%r14,%rax,8), %xmm6
        cvtsd2ss  %xmm6, %xmm6
        movaps    %xmm6, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.11:
        pxor      %xmm1, %xmm1
        testl     %r14d, %r14d
        cvtss2sd  16(%rsp), %xmm1
        andps     .L_2il0floatpacket.5(%rip), %xmm1
        addsd     .L_2il0floatpacket.2(%rip), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        je        ..B1.13
..B1.12:
        lea       48+_BP1(%rip), %rdx
        lea       32+_BP1(%rip), %rcx
        lea       16+_BP1(%rip), %rsi
        lea       40+_BP1(%rip), %r8
        lea       48+_BQ1(%rip), %r11
        lea       32+_BQ1(%rip), %r14
        lea       _BP1(%rip), %rdi
        lea       24+_BP1(%rip), %r9
        movsd     (%rdx), %xmm5
        lea       16+_BQ1(%rip), %rdx
        movsd     (%r8), %xmm2
        lea       8+_BP1(%rip), %r10
        movsd     (%r11), %xmm4
        lea       8+_BQ1(%rip), %r8
        mulsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     (%rcx), %xmm5
        addsd     (%r14), %xmm4
        addsd     (%r9), %xmm2
        mulsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm2
        addsd     (%rsi), %xmm5
        addsd     (%rdx), %xmm4
        addsd     (%r10), %xmm2
        mulsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     (%rdi), %xmm5
        lea       40+_BQ1(%rip), %rsi
        lea       24+_BQ1(%rip), %rdi
        lea       _BQ1(%rip), %rcx
        movsd     (%rsi), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     (%rcx), %xmm4
        addsd     (%rdi), %xmm3
        mulsd     %xmm0, %xmm3
        addsd     (%r8), %xmm3
        mulsd     %xmm1, %xmm3
        jmp       ..B1.14
..B1.13:
        lea       80+_BP2(%rip), %rdx
        lea       64+_BP2(%rip), %rcx
        lea       88+_BP2(%rip), %r10
        lea       48+_BP2(%rip), %rsi
        lea       72+_BP2(%rip), %r11
        lea       32+_BP2(%rip), %rdi
        lea       56+_BP2(%rip), %r14
        lea       16+_BP2(%rip), %r8
        movsd     (%rdx), %xmm5
        lea       40+_BP2(%rip), %rdx
        mulsd     %xmm0, %xmm5
        lea       _BP2(%rip), %r9
        movsd     (%r10), %xmm2
        lea       16+_BQ2(%rip), %r10
        mulsd     %xmm0, %xmm2
        addsd     (%rcx), %xmm5
        mulsd     %xmm0, %xmm5
        addsd     (%r11), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     (%rsi), %xmm5
        mulsd     %xmm0, %xmm5
        addsd     (%r14), %xmm2
        lea       56+_BQ2(%rip), %r14
        addsd     (%rdi), %xmm5
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm5
        addsd     (%rdx), %xmm2
        addsd     (%r8), %xmm5
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm5
        lea       64+_BQ2(%rip), %rdi
        lea       48+_BQ2(%rip), %r8
        movsd     (%r14), %xmm3
        lea       40+_BQ2(%rip), %rdx
        lea       24+_BP2(%rip), %rcx
        lea       8+_BP2(%rip), %rsi
        mulsd     %xmm0, %xmm3
        addsd     (%r9), %xmm5
        addsd     (%rcx), %xmm2
        addsd     (%rdx), %xmm3
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm3
        addsd     (%rsi), %xmm2
        movsd     (%rdi), %xmm4
        lea       32+_BQ2(%rip), %r9
        lea       24+_BQ2(%rip), %rcx
        lea       8+_BQ2(%rip), %rsi
        lea       _BQ2(%rip), %r11
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm3
        addsd     (%r8), %xmm4
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm4
        addsd     (%rsi), %xmm3
        addsd     (%r9), %xmm4
        mulsd     %xmm1, %xmm3
        mulsd     %xmm0, %xmm4
        addsd     (%r10), %xmm4
        mulsd     %xmm0, %xmm4
        addsd     (%r11), %xmm4
..B1.14:
        lea       _ones(%rip), %rdx
        pxor      %xmm0, %xmm0
        lea       _erfc4(%rip), %rcx
        addsd     %xmm2, %xmm5
        addsd     %xmm3, %xmm4
        cvtss2sd  (%rdx,%rax,4), %xmm0
        divsd     %xmm4, %xmm5
        mulsd     %xmm0, %xmm5
        addsd     (%rcx,%rax,8), %xmm5
        cvtsd2ss  %xmm5, %xmm5
        movaps    %xmm5, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.15:
        pxor      %xmm0, %xmm0
        cvtss2sd  16(%rsp), %xmm0
        andps     .L_2il0floatpacket.5(%rip), %xmm0
        movsd     .L_2il0floatpacket.4(%rip), %xmm1
        divsd     %xmm0, %xmm1
        mulsd     %xmm0, %xmm0
        xorps     .L_2il0floatpacket.6(%rip), %xmm0
        movsd     %xmm1, 8(%rsp)
..___tag_value_erfcf.21:
        call      __libm_exp_k32@PLT
..___tag_value_erfcf.22:
..B1.32:
        movaps    %xmm0, %xmm4
..B1.16:
        movsd     8(%rsp), %xmm0
        lea       56+__R1(%rip), %rax
        mulsd     %xmm0, %xmm4
        lea       48+__R1(%rip), %rdi
        mulsd     %xmm0, %xmm0
        movaps    %xmm0, %xmm1
        lea       40+__R1(%rip), %rdx
        mulsd     %xmm0, %xmm1
        lea       32+__R1(%rip), %r8
        movsd     (%rax), %xmm3
        lea       24+__R1(%rip), %rcx
        mulsd     %xmm1, %xmm3
        lea       16+__R1(%rip), %r9
        movsd     (%rdi), %xmm2
        lea       8+__R1(%rip), %rsi
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm3
        mulsd     %xmm1, %xmm3
        addsd     (%r8), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm3
        mulsd     %xmm1, %xmm3
        addsd     (%r9), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rsi), %xmm3
        lea       __R1(%rip), %r10
        testl     %r14d, %r14d
        mulsd     %xmm0, %xmm3
        addsd     (%r10), %xmm2
        addsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        jne       ..B1.20
..B1.17:
        movd      %xmm4, %eax
        movss     %xmm4, 4(%rsp)
        cmpl      $8388608, %eax
        jl        ..B1.28
..B1.19:
        movaps    %xmm4, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.20:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        subss     %xmm4, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.21:
        testl     %r14d, %r14d
        je        ..B1.29
..B1.22:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
..B1.23:
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.24:
        jne       ..B1.26
..B1.25:
        lea       zero_two(%rip), %rax
        movss     (%rax,%r14,4), %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.26:
        movss     16(%rsp), %xmm0
..B1.27:
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 14, -16
..B1.28:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        mulss     %xmm0, %xmm0
        movss     %xmm0, (%rsp)
        movss     4(%rsp), %xmm4
        jmp       ..B1.19
..B1.29:
        movss     .L_2il0floatpacket.1(%rip), %xmm0
        mulss     %xmm0, %xmm0
        movss     %xmm0, 4(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	erfcf,@function
	.size	erfcf,.-erfcf
	.data
# -- End  erfcf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.5:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 16
.L_2il0floatpacket.6:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0xc0000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 4
.L_2il0floatpacket.0:
	.long	0x40000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x0d800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x3f800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
range:
	.long	1092825907
	.long	1081791557
	.type	range,@object
	.size	range,8
	.align 4
_A:
	.long	1346541978
	.long	3220311511
	.long	1794662342
	.long	1071125108
	.long	250944106
	.long	3216827122
	.long	215131671
	.long	1067156170
	.long	548330146
	.long	3212141823
	.long	76350191
	.long	1061937114
	.long	2772654316
	.long	3206307213
	.type	_A,@object
	.size	_A,56
	.align 4
_AP:
	.long	4064916629
	.long	3190878451
	.long	2877165718
	.long	3218164236
	.long	2764139057
	.long	1068232842
	.long	168293639
	.long	3215329394
	.long	881203975
	.long	3215292554
	.long	342818617
	.long	1065638313
	.long	817546319
	.long	3212004140
	.long	1510911299
	.long	3202390104
	.type	_AP,@object
	.size	_AP,64
	.align 4
_AQ:
	.long	1883560946
	.long	1071960885
	.long	853592313
	.long	1071685196
	.long	191605458
	.long	1071250674
	.long	2183121159
	.long	1070390698
	.long	3347411101
	.long	1068928985
	.long	273856425
	.long	1067608207
	.long	671820230
	.long	1065177551
	.long	2414119437
	.long	1063328696
	.type	_AQ,@object
	.size	_AQ,64
	.align 4
_erfc1:
	.long	1879048192
	.long	1069818465
	.long	3523215360
	.long	1073576883
	.type	_erfc1,@object
	.size	_erfc1,16
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_BP1:
	.long	4019088381
	.long	1060143686
	.long	4231883845
	.long	3209038463
	.long	1510730124
	.long	1061755778
	.long	2929202078
	.long	3208591153
	.long	1409179897
	.long	1059475758
	.long	439372548
	.long	3204538649
	.long	3357266387
	.long	1053229132
	.type	_BP1,@object
	.size	_BP1,56
	.align 4
_BQ1:
	.long	3444570556
	.long	1068208773
	.long	4145425017
	.long	1069029647
	.long	1145740212
	.long	1069192522
	.long	2413502193
	.long	1068797491
	.long	2712383784
	.long	1068007659
	.long	3941762314
	.long	1066743858
	.long	3272105283
	.long	1065447630
	.type	_BQ1,@object
	.size	_BQ1,56
	.align 4
_BP2:
	.long	3138848853
	.long	1062211012
	.long	544119287
	.long	3211644465
	.long	2662071917
	.long	1064935492
	.long	2106254088
	.long	3212505578
	.long	377059754
	.long	1064517936
	.long	3523110848
	.long	3210978388
	.long	64932799
	.long	1062023575
	.long	2232373525
	.long	3207568747
	.long	3004568351
	.long	1057518327
	.long	1553969795
	.long	3201939481
	.long	865068135
	.long	1050709866
	.long	348213498
	.long	3193161330
	.type	_BP2,@object
	.size	_BP2,96
	.align 4
_BQ2:
	.long	2220299070
	.long	1070256111
	.long	1607355279
	.long	1069737707
	.long	3222185356
	.long	1069664669
	.long	1988590515
	.long	1068489586
	.long	2612211906
	.long	1067468794
	.long	3802943557
	.long	1065572613
	.long	4286646379
	.long	1063895282
	.long	45248763
	.long	1061257684
	.long	3494068347
	.long	1058887001
	.type	_BQ2,@object
	.size	_BQ2,72
	.align 4
_erfc4:
	.long	0
	.long	1045463040
	.long	4225761280
	.long	1073741823
	.type	_erfc4,@object
	.size	_erfc4,16
	.align 4
__R1:
	.long	1340517714
	.long	1071779287
	.long	1760660317
	.long	3218214358
	.long	3277598851
	.long	1071322187
	.long	1776282588
	.long	3220235438
	.long	2229586924
	.long	1074588991
	.long	2042215417
	.long	3224150558
	.long	1374528972
	.long	1078534706
	.long	1825320511
	.long	3227067102
	.type	__R1,@object
	.size	__R1,64
	.align 4
zero_two:
	.long	0x00000000
	.long	0x40000000
	.type	zero_two,@object
	.size	zero_two,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
