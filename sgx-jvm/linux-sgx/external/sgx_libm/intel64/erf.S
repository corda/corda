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
	.file "erf.c"
	.text
..TXTST0:
# -- Begin  erf
	.text
       .align    16,0x90
	.globl erf
erf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_erf.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movb      -1(%rsp), %cl
        movb      %cl, %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        lea       _vmldErfHATab(%rip), %rax
        movzwl    -2(%rsp), %esi
        andl      $32752, %esi
        shrl      $4, %esi
        movsd     3632(%rax,%rdx,8), %xmm5
        cmpl      $2047, %esi
        je        ..B1.17
..B1.2:
        movsd     %xmm0, -8(%rsp)
        andb      $127, %cl
        movb      %cl, -1(%rsp)
        cmpl      $983, %esi
        movsd     -8(%rsp), %xmm4
        jl        ..B1.9
..B1.3:
        lea       3664+_vmldErfHATab(%rip), %rdx
        comisd    (%rdx), %xmm4
        jb        ..B1.8
..B1.4:
        lea       3672+_vmldErfHATab(%rip), %rdx
        movsd     (%rdx), %xmm0
        comisd    %xmm4, %xmm0
        jbe       ..B1.6
..B1.5:
        movl      -4(%rsp), %edx
        shll      $20, %esi
        andl      $1048575, %edx
        orl       %edx, %esi
        addl      $-1069547520, %esi
        sarl      $18, %esi
        lea       (%rsi,%rsi,4), %ecx
        lea       3616+_vmldErfHATab(%rip), %rsi
        shll      $2, %ecx
        movslq    %ecx, %rcx
        movsd     (%rsi), %xmm1
        movaps    %xmm1, %xmm6
        movsd     (%rax,%rcx,8), %xmm3
        mulsd     %xmm3, %xmm4
        movsd     152(%rax,%rcx,8), %xmm2
        movsd     48(%rax,%rcx,8), %xmm11
        movsd     56(%rax,%rcx,8), %xmm12
        addsd     8(%rax,%rcx,8), %xmm4
        mulsd     %xmm4, %xmm2
        mulsd     %xmm4, %xmm6
        addsd     144(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        movsd     %xmm6, -24(%rsp)
        movsd     -24(%rsp), %xmm7
        movsd     32(%rax,%rcx,8), %xmm6
        subsd     %xmm4, %xmm7
        addsd     136(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        movsd     %xmm7, -16(%rsp)
        movsd     -24(%rsp), %xmm9
        movsd     -16(%rsp), %xmm8
        addsd     128(%rax,%rcx,8), %xmm2
        subsd     %xmm8, %xmm9
        mulsd     %xmm4, %xmm2
        movsd     %xmm9, -24(%rsp)
        movsd     -24(%rsp), %xmm10
        addsd     120(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     112(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     104(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     96(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     88(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     80(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     72(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        addsd     64(%rax,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm2
        mulsd     %xmm4, %xmm2
        mulsd     %xmm4, %xmm2
        subsd     %xmm10, %xmm4
        movsd     %xmm4, -16(%rsp)
        movsd     -24(%rsp), %xmm4
        movsd     -16(%rsp), %xmm0
        movaps    %xmm4, %xmm3
        movaps    %xmm0, %xmm13
        mulsd     %xmm11, %xmm3
        mulsd     %xmm12, %xmm13
        mulsd     %xmm0, %xmm11
        mulsd     %xmm4, %xmm12
        addsd     %xmm11, %xmm13
        movaps    %xmm3, %xmm14
        addsd     %xmm12, %xmm13
        addsd     %xmm6, %xmm14
        movsd     %xmm13, -24(%rsp)
        movsd     -24(%rsp), %xmm7
        movsd     %xmm14, -24(%rsp)
        movsd     -24(%rsp), %xmm15
        subsd     %xmm15, %xmm6
        addsd     %xmm3, %xmm6
        movsd     %xmm6, -16(%rsp)
        movsd     -24(%rsp), %xmm11
        mulsd     %xmm11, %xmm1
        movsd     -16(%rsp), %xmm14
        movsd     %xmm1, -24(%rsp)
        addsd     %xmm7, %xmm14
        movsd     -24(%rsp), %xmm1
        subsd     %xmm11, %xmm1
        addsd     40(%rax,%rcx,8), %xmm14
        movsd     %xmm1, -16(%rsp)
        movaps    %xmm0, %xmm1
        movsd     -24(%rsp), %xmm9
        movsd     -16(%rsp), %xmm8
        subsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -24(%rsp), %xmm10
        subsd     %xmm10, %xmm11
        movsd     %xmm11, -16(%rsp)
        movsd     -24(%rsp), %xmm13
        movsd     -16(%rsp), %xmm12
        movaps    %xmm13, %xmm3
        mulsd     %xmm0, %xmm13
        addsd     %xmm12, %xmm14
        mulsd     %xmm4, %xmm3
        mulsd     %xmm14, %xmm1
        mulsd     %xmm14, %xmm4
        addsd     %xmm13, %xmm1
        movaps    %xmm3, %xmm0
        addsd     %xmm4, %xmm1
        movsd     %xmm1, -24(%rsp)
        movsd     16(%rax,%rcx,8), %xmm1
        movsd     -24(%rsp), %xmm15
        addsd     %xmm1, %xmm0
        movsd     %xmm0, -24(%rsp)
        movsd     -24(%rsp), %xmm4
        subsd     %xmm4, %xmm1
        addsd     %xmm3, %xmm1
        movsd     %xmm1, -16(%rsp)
        movsd     -24(%rsp), %xmm1
        movsd     -16(%rsp), %xmm0
        addsd     %xmm15, %xmm0
        addsd     24(%rax,%rcx,8), %xmm0
        addsd     %xmm0, %xmm2
        movsd     %xmm2, -24(%rsp)
        movsd     -24(%rsp), %xmm0
        addsd     %xmm1, %xmm0
        mulsd     %xmm5, %xmm0
        ret       
..B1.6:
        lea       3632+_vmldErfHATab(%rip), %rax
        lea       3608+_vmldErfHATab(%rip), %rdx
        movsd     (%rax), %xmm0
        addsd     (%rdx), %xmm0
        mulsd     %xmm5, %xmm0
..B1.7:
        ret       
..B1.8:
        movaps    %xmm4, %xmm6
        lea       3592+_vmldErfHATab(%rip), %rax
        mulsd     %xmm4, %xmm6
        lea       3584+_vmldErfHATab(%rip), %rdx
        movsd     (%rax), %xmm3
        lea       3576+_vmldErfHATab(%rip), %rcx
        mulsd     %xmm6, %xmm3
        movaps    %xmm4, %xmm12
        mulsd     %xmm6, %xmm12
        addsd     (%rdx), %xmm3
        mulsd     %xmm6, %xmm3
        lea       3616+_vmldErfHATab(%rip), %r10
        lea       3568+_vmldErfHATab(%rip), %rsi
        lea       3560+_vmldErfHATab(%rip), %rdi
        movsd     (%r10), %xmm7
        lea       3544+_vmldErfHATab(%rip), %r8
        mulsd     %xmm12, %xmm7
        addsd     (%rcx), %xmm3
        mulsd     %xmm6, %xmm3
        lea       3552+_vmldErfHATab(%rip), %r9
        movsd     %xmm7, -24(%rsp)
        lea       3528+_vmldErfHATab(%rip), %r11
        movsd     -24(%rsp), %xmm8
        lea       3536+_vmldErfHATab(%rip), %rax
        movsd     (%r8), %xmm14
        subsd     %xmm12, %xmm8
        addsd     (%rsi), %xmm3
        mulsd     %xmm6, %xmm3
        movaps    %xmm14, %xmm2
        movsd     %xmm8, -16(%rsp)
        movsd     -24(%rsp), %xmm10
        movsd     -16(%rsp), %xmm9
        movsd     (%r9), %xmm15
        subsd     %xmm9, %xmm10
        addsd     (%rdi), %xmm3
        mulsd     %xmm6, %xmm3
        movaps    %xmm4, %xmm6
        mulsd     (%r10), %xmm6
        mulsd     %xmm12, %xmm3
        movsd     %xmm10, -24(%rsp)
        movsd     -24(%rsp), %xmm11
        subsd     %xmm11, %xmm12
        movsd     %xmm12, -16(%rsp)
        movsd     -24(%rsp), %xmm0
        movsd     -16(%rsp), %xmm13
        movaps    %xmm13, %xmm1
        mulsd     %xmm15, %xmm1
        mulsd     %xmm13, %xmm14
        mulsd     %xmm0, %xmm2
        mulsd     %xmm15, %xmm0
        addsd     %xmm14, %xmm1
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -24(%rsp)
        movsd     -24(%rsp), %xmm1
        movsd     %xmm6, -24(%rsp)
        movsd     -24(%rsp), %xmm7
        movsd     (%r11), %xmm6
        movaps    %xmm6, %xmm0
        subsd     -8(%rsp), %xmm7
        movsd     %xmm7, -16(%rsp)
        movsd     -24(%rsp), %xmm9
        movsd     -16(%rsp), %xmm8
        movsd     (%rax), %xmm7
        subsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -24(%rsp), %xmm10
        subsd     %xmm10, %xmm4
        movaps    %xmm2, %xmm10
        addsd     %xmm3, %xmm10
        movsd     %xmm4, -16(%rsp)
        movsd     -24(%rsp), %xmm8
        movsd     -16(%rsp), %xmm4
        movaps    %xmm4, %xmm9
        mulsd     %xmm7, %xmm9
        mulsd     %xmm4, %xmm6
        mulsd     %xmm8, %xmm0
        mulsd     %xmm7, %xmm8
        addsd     %xmm6, %xmm9
        addsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -24(%rsp), %xmm12
        movsd     %xmm10, -24(%rsp)
        movsd     -24(%rsp), %xmm4
        subsd     %xmm4, %xmm2
        addsd     %xmm3, %xmm2
        movsd     %xmm2, -16(%rsp)
        movsd     -24(%rsp), %xmm3
        movsd     -16(%rsp), %xmm11
        addsd     %xmm1, %xmm11
        movaps    %xmm0, %xmm1
        addsd     %xmm3, %xmm1
        addsd     %xmm11, %xmm12
        movsd     %xmm1, -24(%rsp)
        movsd     -24(%rsp), %xmm2
        subsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        movsd     %xmm0, -16(%rsp)
        movsd     -24(%rsp), %xmm0
        movsd     -16(%rsp), %xmm13
        addsd     %xmm12, %xmm13
        addsd     %xmm13, %xmm0
        mulsd     %xmm5, %xmm0
        ret       
..B1.9:
        lea       3624+_vmldErfHATab(%rip), %rax
        ucomisd   (%rax), %xmm4
        jp        ..B1.10
        je        ..B1.7
..B1.10:
        testl     %esi, %esi
        jne       ..B1.16
..B1.11:
        lea       3656+_vmldErfHATab(%rip), %rax
        comisd    (%rax), %xmm4
        jae       ..B1.16
..B1.12:
        lea       3600+_vmldErfHATab(%rip), %rax
        lea       3648+_vmldErfHATab(%rip), %rdx
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        comisd    (%rdx), %xmm4
        jb        ..B1.14
..B1.13:
        lea       3616+_vmldErfHATab(%rip), %rax
        movaps    %xmm0, %xmm1
        lea       3536+_vmldErfHATab(%rip), %rcx
        lea       3528+_vmldErfHATab(%rip), %rdx
        lea       3608+_vmldErfHATab(%rip), %rsi
        movsd     (%rax), %xmm10
        movsd     (%rdx), %xmm8
        mulsd     %xmm10, %xmm1
        movsd     %xmm1, -24(%rsp)
        movsd     -24(%rsp), %xmm2
        subsd     %xmm0, %xmm2
        movsd     %xmm2, -16(%rsp)
        movsd     -24(%rsp), %xmm4
        movsd     -16(%rsp), %xmm3
        subsd     %xmm3, %xmm4
        movsd     %xmm4, -24(%rsp)
        movsd     -24(%rsp), %xmm5
        subsd     %xmm5, %xmm0
        movsd     %xmm0, -16(%rsp)
        movsd     -24(%rsp), %xmm6
        movsd     -16(%rsp), %xmm7
        movaps    %xmm6, %xmm15
        movsd     (%rcx), %xmm0
        movaps    %xmm7, %xmm9
        mulsd     %xmm0, %xmm9
        mulsd     %xmm0, %xmm6
        mulsd     %xmm8, %xmm15
        mulsd     %xmm7, %xmm8
        addsd     %xmm6, %xmm9
        mulsd     %xmm15, %xmm10
        addsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -24(%rsp), %xmm0
        movsd     %xmm10, -24(%rsp)
        movsd     -24(%rsp), %xmm11
        subsd     %xmm15, %xmm11
        movsd     %xmm11, -16(%rsp)
        movsd     -24(%rsp), %xmm13
        movsd     -16(%rsp), %xmm12
        subsd     %xmm12, %xmm13
        movsd     %xmm13, -24(%rsp)
        movsd     -24(%rsp), %xmm14
        subsd     %xmm14, %xmm15
        movsd     %xmm15, -16(%rsp)
        movsd     -24(%rsp), %xmm11
        movsd     -16(%rsp), %xmm10
        mulsd     (%rsi), %xmm11
        addsd     %xmm10, %xmm0
        mulsd     (%rsi), %xmm0
        movsd     %xmm11, -24(%rsp)
        movsd     %xmm0, -16(%rsp)
        movsd     -24(%rsp), %xmm2
        movsd     -16(%rsp), %xmm1
        addsd     %xmm1, %xmm2
        jmp       ..B1.15
..B1.14:
        lea       3520+_vmldErfHATab(%rip), %rax
        lea       3608+_vmldErfHATab(%rip), %rdx
        movsd     (%rax), %xmm2
        mulsd     %xmm0, %xmm2
        mulsd     (%rdx), %xmm2
..B1.15:
        movaps    %xmm2, %xmm0
        ret       
..B1.16:
        lea       3600+_vmldErfHATab(%rip), %rax
        lea       3616+_vmldErfHATab(%rip), %rdx
        lea       3528+_vmldErfHATab(%rip), %rcx
        lea       3536+_vmldErfHATab(%rip), %rsi
        lea       3608+_vmldErfHATab(%rip), %rdi
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     (%rdx), %xmm2
        mulsd     %xmm0, %xmm2
        movsd     %xmm2, -24(%rsp)
        movsd     -24(%rsp), %xmm3
        movsd     (%rcx), %xmm10
        subsd     %xmm0, %xmm3
        movsd     %xmm3, -16(%rsp)
        movsd     -24(%rsp), %xmm5
        movsd     -16(%rsp), %xmm4
        movsd     (%rsi), %xmm7
        subsd     %xmm4, %xmm5
        movsd     %xmm5, -24(%rsp)
        movsd     -24(%rsp), %xmm6
        subsd     %xmm6, %xmm0
        movsd     %xmm0, -16(%rsp)
        movaps    %xmm10, %xmm0
        movsd     -24(%rsp), %xmm8
        movsd     -16(%rsp), %xmm9
        movaps    %xmm9, %xmm11
        mulsd     %xmm8, %xmm0
        mulsd     %xmm7, %xmm11
        mulsd     %xmm7, %xmm8
        mulsd     %xmm9, %xmm10
        addsd     %xmm8, %xmm11
        addsd     %xmm10, %xmm11
        movsd     %xmm11, -24(%rsp)
        movsd     -24(%rsp), %xmm12
        addsd     %xmm12, %xmm0
        mulsd     (%rdi), %xmm0
        ret       
..B1.17:
        testl     $1048575, -4(%rsp)
        jne       ..B1.20
..B1.18:
        cmpl      $0, -8(%rsp)
        jne       ..B1.20
..B1.19:
        lea       3632+_vmldErfHATab(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm5, %xmm0
        ret       
..B1.20:
        movsd     -8(%rsp), %xmm0
        addsd     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	erf,@function
	.size	erf,.-erf
	.data
# -- End  erf
	.section .rodata, "a"
	.align 8
	.align 8
_vmldErfHATab:
	.long	0
	.long	1077936128
	.long	0
	.long	3222405120
	.long	3270920536
	.long	1069821311
	.long	1057270100
	.long	1014821379
	.long	337483220
	.long	1067561815
	.long	2929153520
	.long	1012421262
	.long	4026531840
	.long	3206801857
	.long	1030462481
	.long	3179971874
	.long	1486349066
	.long	3202788052
	.long	134277178
	.long	1047766669
	.long	1212351331
	.long	1042946157
	.long	1104268445
	.long	3183073920
	.long	2950952656
	.long	3177697379
	.long	1025949206
	.long	1022984782
	.long	1815731286
	.long	1017231760
	.long	3033581736
	.long	3157533247
	.long	2954897717
	.long	3151375491
	.long	1303182986
	.long	996834239
	.long	795652555
	.long	990371394
	.long	2343572077
	.long	3130906588
	.long	0
	.long	1077936128
	.long	0
	.long	3222667264
	.long	266066941
	.long	1070109953
	.long	2023669004
	.long	1013041695
	.long	3705654820
	.long	1067550541
	.long	3566009421
	.long	1011293070
	.long	134217728
	.long	3207076363
	.long	3355205190
	.long	3180252909
	.long	2219056158
	.long	3202743700
	.long	3264865953
	.long	1048027271
	.long	3807428681
	.long	1042858920
	.long	3066569234
	.long	3183403162
	.long	1059612996
	.long	3177583001
	.long	1631410288
	.long	1023296102
	.long	1209224421
	.long	1017119299
	.long	3957039910
	.long	3157768207
	.long	2748118263
	.long	3151198590
	.long	1787505922
	.long	997129044
	.long	167794763
	.long	990139564
	.long	2444798020
	.long	3131135431
	.long	0
	.long	1077936128
	.long	0
	.long	3222929408
	.long	3508744758
	.long	1070395510
	.long	3777053507
	.long	1014792775
	.long	2239870848
	.long	1067537158
	.long	80412370
	.long	1012585610
	.long	2550136832
	.long	3207341802
	.long	731471944
	.long	3177077062
	.long	620528720
	.long	3202691430
	.long	3841836855
	.long	1048273069
	.long	190893310
	.long	1042756870
	.long	3560172648
	.long	3183591033
	.long	3053707503
	.long	3177450205
	.long	2206487565
	.long	1023491751
	.long	3218422968
	.long	1016860737
	.long	598928797
	.long	3157970519
	.long	2475988840
	.long	3150996363
	.long	1159941103
	.long	997284625
	.long	3398924683
	.long	989876643
	.long	798935564
	.long	3131261061
	.long	0
	.long	1077936128
	.long	0
	.long	3223191552
	.long	1770174791
	.long	1070636781
	.long	4205036945
	.long	1015375813
	.long	2413196824
	.long	1067521741
	.long	2601975064
	.long	1005834579
	.long	2952790016
	.long	3207595360
	.long	3658041887
	.long	3181077562
	.long	4169926782
	.long	3202631738
	.long	2555230105
	.long	1048501852
	.long	2080004756
	.long	1042641364
	.long	4228979116
	.long	3183726688
	.long	3050753797
	.long	3177301266
	.long	157144205
	.long	1023611097
	.long	477563032
	.long	1016572798
	.long	1180941066
	.long	3158136250
	.long	103748847
	.long	3150576732
	.long	855028858
	.long	997378869
	.long	3717241463
	.long	989324501
	.long	2285599851
	.long	3131350893
	.long	0
	.long	1076887552
	.long	0
	.long	3222405120
	.long	2340732634
	.long	1070844330
	.long	1402414124
	.long	1012108932
	.long	4199089954
	.long	1068543572
	.long	2559319735
	.long	1013022575
	.long	2550136832
	.long	3209872415
	.long	1842851526
	.long	3180929664
	.long	3057689090
	.long	3205675264
	.long	4009292523
	.long	1052886727
	.long	2015516814
	.long	1047689133
	.long	520582071
	.long	3190189211
	.long	3416096878
	.long	3184261095
	.long	1113028140
	.long	1032138739
	.long	3929660173
	.long	1025537235
	.long	1947345592
	.long	3168795649
	.long	1874355335
	.long	3161325647
	.long	790309391
	.long	1010046575
	.long	3732702576
	.long	1001648133
	.long	3279395584
	.long	3146093738
	.long	0
	.long	1076887552
	.long	0
	.long	3222667264
	.long	1155126243
	.long	1071112537
	.long	4058417554
	.long	1015654408
	.long	4091643817
	.long	1068501692
	.long	992009585
	.long	1011189456
	.long	3355443200
	.long	3210088131
	.long	577555187
	.long	3183302762
	.long	3137228493
	.long	3205518745
	.long	1652609564
	.long	1053053423
	.long	3828684380
	.long	1047267014
	.long	859309674
	.long	3190350551
	.long	1723732682
	.long	3183536714
	.long	3655321812
	.long	1032243418
	.long	1849688920
	.long	1023985400
	.long	3853991388
	.long	3168837843
	.long	3217410018
	.long	1007305761
	.long	15852099
	.long	1010050248
	.long	1197806868
	.long	3148971557
	.long	1066481688
	.long	3146043461
	.long	0
	.long	1076887552
	.long	0
	.long	3222929408
	.long	3259520439
	.long	1071369470
	.long	2827660931
	.long	1015400118
	.long	2387383117
	.long	1068408154
	.long	3928547609
	.long	1012504486
	.long	2415919104
	.long	3210272729
	.long	2041761256
	.long	3182227901
	.long	2378546067
	.long	3205191821
	.long	2022373860
	.long	1053172536
	.long	1539712272
	.long	1046637816
	.long	2866781005
	.long	3190430666
	.long	1973204487
	.long	3181753511
	.long	471306766
	.long	1032252941
	.long	3917954587
	.long	3170909470
	.long	1575996247
	.long	3168793506
	.long	2080089276
	.long	1013809777
	.long	3478556236
	.long	1009933722
	.long	556567750
	.long	3150565473
	.long	3617504985
	.long	3145850711
	.long	0
	.long	1076887552
	.long	0
	.long	3223191552
	.long	2839711645
	.long	1071613690
	.long	2970658322
	.long	1014602120
	.long	3815538168
	.long	1068301377
	.long	1215242252
	.long	1012502501
	.long	3221225472
	.long	3210423421
	.long	1694424286
	.long	3182745304
	.long	2482618577
	.long	3204819422
	.long	1490180077
	.long	1053241721
	.long	716537363
	.long	1045509088
	.long	3220326238
	.long	3190429019
	.long	1649943177
	.long	1034362954
	.long	149820908
	.long	1032171009
	.long	2913777793
	.long	3172667222
	.long	2916685335
	.long	3168553958
	.long	3423621657
	.long	1014717380
	.long	3520470914
	.long	1009649189
	.long	1724012134
	.long	3151216830
	.long	973445297
	.long	3145359701
	.long	0
	.long	1075838976
	.long	0
	.long	3222405120
	.long	2293685631
	.long	1071799178
	.long	995883061
	.long	1015632714
	.long	3793057312
	.long	1069174900
	.long	303272667
	.long	1011854732
	.long	2013265920
	.long	3212679811
	.long	2434603502
	.long	3185139859
	.long	492809813
	.long	3207185437
	.long	3249638684
	.long	1057446891
	.long	3644008036
	.long	3197518090
	.long	2427527080
	.long	3196575624
	.long	382948573
	.long	1043637576
	.long	3944285033
	.long	1040289590
	.long	3216947186
	.long	3183007329
	.long	3738834740
	.long	3178455694
	.long	3578811002
	.long	1026832616
	.long	2077607461
	.long	1021301705
	.long	1329130827
	.long	3165226306
	.long	2937198399
	.long	3158390279
	.long	0
	.long	1075838976
	.long	0
	.long	3222667264
	.long	1026243227
	.long	1071999268
	.long	19842292
	.long	1014558455
	.long	615820035
	.long	1068925439
	.long	3077235950
	.long	1013851225
	.long	3489660928
	.long	3212767934
	.long	727482074
	.long	3183723353
	.long	1434284591
	.long	3204072020
	.long	1546025314
	.long	1057305153
	.long	3311043419
	.long	3199555764
	.long	1534557980
	.long	3196172205
	.long	1003545269
	.long	1044455453
	.long	982041524
	.long	1039309150
	.long	1046902208
	.long	3183487308
	.long	1293621534
	.long	3176225124
	.long	3225792447
	.long	1026964909
	.long	3923689281
	.long	3166944983
	.long	2903296563
	.long	3165127275
	.long	3175926173
	.long	1011691029
	.long	0
	.long	1075838976
	.long	0
	.long	3222929408
	.long	4209868920
	.long	1072167835
	.long	1271589702
	.long	1014394097
	.long	3991103290
	.long	1068673244
	.long	1964927110
	.long	1013592604
	.long	0
	.long	3212726887
	.long	4238629777
	.long	3183774781
	.long	1807339610
	.long	1059053689
	.long	35311788
	.long	1057028643
	.long	3165087585
	.long	3200229918
	.long	3586452917
	.long	3195213126
	.long	1777317359
	.long	1044610644
	.long	1848687836
	.long	1033934742
	.long	31916095
	.long	3183379115
	.long	185553482
	.long	1029904423
	.long	882484870
	.long	1026631250
	.long	3164391940
	.long	3169115287
	.long	3325423896
	.long	3164355775
	.long	3234707527
	.long	1012695165
	.long	0
	.long	1075838976
	.long	0
	.long	3223191552
	.long	2708123311
	.long	1072305489
	.long	1189046596
	.long	1013601674
	.long	1868283783
	.long	1068367010
	.long	1099775820
	.long	1012435153
	.long	1207959552
	.long	3212582104
	.long	3523250270
	.long	3180953827
	.long	1977223559
	.long	1059998873
	.long	469007091
	.long	1056393187
	.long	17425450
	.long	3200373232
	.long	391965510
	.long	3192157780
	.long	3314858537
	.long	1044490263
	.long	3600399866
	.long	3186407596
	.long	3215068466
	.long	3182787442
	.long	3697861179
	.long	1030898835
	.long	2771081374
	.long	1025463657
	.long	4146920243
	.long	3169520105
	.long	3077194481
	.long	3160459915
	.long	1463264555
	.long	1012730022
	.long	0
	.long	1074790400
	.long	0
	.long	3222405120
	.long	673836129
	.long	1072459181
	.long	1403851199
	.long	1016899858
	.long	3166402837
	.long	1068785305
	.long	375843704
	.long	1012382807
	.long	4026531840
	.long	3214338668
	.long	3760928269
	.long	3186707360
	.long	4037675931
	.long	1063570236
	.long	828875333
	.long	1058841864
	.long	1595734612
	.long	3205457361
	.long	3240025461
	.long	1053807046
	.long	2217213834
	.long	1050913991
	.long	1140208334
	.long	3195581500
	.long	1536578125
	.long	3189218053
	.long	1785608939
	.long	1041425703
	.long	3818468589
	.long	3183948101
	.long	4224809191
	.long	3181546992
	.long	1254902324
	.long	1030336836
	.long	2610385435
	.long	1025933392
	.long	0
	.long	1074790400
	.long	0
	.long	3222667264
	.long	3283395580
	.long	1072584552
	.long	1619691918
	.long	1013495334
	.long	1548049162
	.long	1067830873
	.long	1866614584
	.long	1009528830
	.long	3623878656
	.long	3213753274
	.long	3080383738
	.long	3186326900
	.long	2195737703
	.long	1063532365
	.long	1389581926
	.long	3206495117
	.long	1284275250
	.long	3204405036
	.long	1388678495
	.long	1054257519
	.long	2607787780
	.long	3195486202
	.long	1954028197
	.long	3195106308
	.long	1000142209
	.long	1043978813
	.long	4289390618
	.long	1039480054
	.long	1829994710
	.long	3184933148
	.long	1801900516
	.long	1031672376
	.long	994367613
	.long	1030081754
	.long	1886321575
	.long	3173571539
	.long	0
	.long	1074790400
	.long	0
	.long	3222929408
	.long	996654454
	.long	1072648041
	.long	2055905835
	.long	1016797060
	.long	2025629493
	.long	1066703316
	.long	3207138551
	.long	1007886082
	.long	2684354560
	.long	3212885244
	.long	3640404479
	.long	3184577086
	.long	3785526942
	.long	1063085617
	.long	914780829
	.long	3207165120
	.long	198538566
	.long	3200351555
	.long	743164606
	.long	1053656447
	.long	257739447
	.long	3197719723
	.long	4013344053
	.long	3192054846
	.long	2448984831
	.long	1043799255
	.long	301239699
	.long	3187102807
	.long	4198692195
	.long	3183507635
	.long	252775780
	.long	1033310081
	.long	3936202859
	.long	3174119617
	.long	2832995703
	.long	3173394688
	.long	0
	.long	1074790400
	.long	0
	.long	3223191552
	.long	4009021290
	.long	1072676449
	.long	2313960863
	.long	1015111149
	.long	939498435
	.long	1065430251
	.long	2354454115
	.long	1009400884
	.long	2147483648
	.long	3211794972
	.long	482970327
	.long	3184656008
	.long	3337941139
	.long	1062290385
	.long	1376485817
	.long	3206914838
	.long	3849340321
	.long	1055357361
	.long	3350371102
	.long	1051775129
	.long	2861188266
	.long	3197382603
	.long	2109605468
	.long	1045917685
	.long	3237589551
	.long	1041743271
	.long	485240880
	.long	3187249602
	.long	3692998039
	.long	1035162550
	.long	2284233074
	.long	1031989748
	.long	2141078118
	.long	3176508222
	.long	2775821538
	.long	1021961626
	.long	0
	.long	1073741824
	.long	0
	.long	3222405120
	.long	1979914676
	.long	1072690180
	.long	3909090605
	.long	1014961349
	.long	2850023777
	.long	1064124739
	.long	2716929087
	.long	1007836215
	.long	134217728
	.long	3211818166
	.long	661707185
	.long	3185457101
	.long	511854090
	.long	1063665398
	.long	846761866
	.long	3209923224
	.long	2075885217
	.long	1060492209
	.long	3563065698
	.long	3204958944
	.long	791923268
	.long	3201693745
	.long	905515084
	.long	1053843502
	.long	2412959807
	.long	3198694704
	.long	3378187023
	.long	3192392758
	.long	389556620
	.long	1046549597
	.long	4094604040
	.long	3191327001
	.long	2261901381
	.long	3186206862
	.long	3040498429
	.long	1038898667
	.long	0
	.long	1073741824
	.long	0
	.long	3222667264
	.long	4211364825
	.long	1072693036
	.long	2280492610
	.long	1015654639
	.long	3441679161
	.long	1060320780
	.long	631077559
	.long	1005402779
	.long	2550136832
	.long	3208276561
	.long	952708928
	.long	3179317867
	.long	1724702579
	.long	1060543732
	.long	1940663234
	.long	3207246018
	.long	1617314154
	.long	1058444121
	.long	3218201639
	.long	3204130645
	.long	1677675407
	.long	1054157666
	.long	983365761
	.long	3197588855
	.long	3697581654
	.long	3196234675
	.long	2384146406
	.long	1047319959
	.long	271521235
	.long	3191905541
	.long	1276705943
	.long	3186754722
	.long	2179097654
	.long	1039567487
	.long	301655052
	.long	3184379693
	.long	0
	.long	1073741824
	.long	0
	.long	3222929408
	.long	4193680981
	.long	1072693238
	.long	4060240663
	.long	1015946834
	.long	2365640378
	.long	1055824734
	.long	3526748952
	.long	995134091
	.long	3489660928
	.long	3203980860
	.long	2590051195
	.long	3175384383
	.long	2654771569
	.long	1056549448
	.long	4010094511
	.long	3203581909
	.long	3526856597
	.long	1055166628
	.long	1319720904
	.long	3201385798
	.long	499971761
	.long	1052133367
	.long	2524150070
	.long	3197462343
	.long	3595721810
	.long	1047211062
	.long	32165415
	.long	3189999948
	.long	131183479
	.long	3189246514
	.long	3500428453
	.long	1040184954
	.long	4217742894
	.long	3184647883
	.long	1659999320
	.long	3176152785
	.long	0
	.long	1073741824
	.long	0
	.long	3223191552
	.long	3270603235
	.long	1072693247
	.long	789463291
	.long	1015570140
	.long	3808393842
	.long	1050514289
	.long	2326429486
	.long	994919830
	.long	3221225472
	.long	3198925370
	.long	1345776679
	.long	3171106320
	.long	2968647484
	.long	1051768487
	.long	2248759888
	.long	3199010549
	.long	3387299329
	.long	1050887019
	.long	1430298481
	.long	3197384059
	.long	1348250416
	.long	1048645854
	.long	4055449617
	.long	3194443371
	.long	1237453215
	.long	1045012312
	.long	3542750561
	.long	3190150798
	.long	2477123228
	.long	1039795927
	.long	1515061420
	.long	3182966354
	.long	468112447
	.long	3181397693
	.long	3085255916
	.long	1032264152
	.long	0
	.long	1072693248
	.long	0
	.long	3222405120
	.long	4293196336
	.long	1072693247
	.long	2846765772
	.long	1013778335
	.long	708045282
	.long	1042226741
	.long	3617872673
	.long	984839576
	.long	3758096384
	.long	3191963901
	.long	3393404685
	.long	3164431480
	.long	678234459
	.long	1046059910
	.long	3154637626
	.long	3194706316
	.long	2318517263
	.long	1047966141
	.long	3341209390
	.long	3195954445
	.long	800124789
	.long	1048630880
	.long	2824228362
	.long	3196018487
	.long	785851381
	.long	1048106191
	.long	4129204204
	.long	3195061111
	.long	3370612561
	.long	1046690463
	.long	1422845730
	.long	3193098259
	.long	1920503482
	.long	1044382253
	.long	2798351277
	.long	3189961775
	.long	2515384124
	.long	1072782465
	.long	4217971979
	.long	3222778785
	.long	4294967193
	.long	1072693247
	.long	1248116507
	.long	1015814019
	.long	955504597
	.long	1027630151
	.long	942964606
	.long	972301410
	.long	268435456
	.long	3177488267
	.long	3130289062
	.long	3150952764
	.long	4292327811
	.long	1031864837
	.long	2929831361
	.long	3180636976
	.long	1961758009
	.long	1034139815
	.long	5545144
	.long	3182345703
	.long	485665603
	.long	1035212938
	.long	2600921430
	.long	3182853574
	.long	3983926904
	.long	1035334367
	.long	3203204633
	.long	3182602207
	.long	3287855196
	.long	1034606778
	.long	3286302033
	.long	3181514701
	.long	1856488612
	.long	1033626459
	.long	4001991753
	.long	3180127602
	.long	1346542445
	.long	1072827863
	.long	1342177280
	.long	1072827863
	.long	1147899556
	.long	1041278683
	.long	1744830464
	.long	3218608756
	.long	902814497
	.long	3190299599
	.long	436480833
	.long	1069343474
	.long	824474678
	.long	3214639822
	.long	3302084921
	.long	1064658364
	.long	3780041965
	.long	3209429686
	.long	3655817560
	.long	1059021901
	.long	0
	.long	1282408448
	.long	0
	.long	862978048
	.long	33554432
	.long	1101004800
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.long	0
	.long	256
	.long	1222277045
	.long	929276
	.long	0
	.long	1069547520
	.long	2378786344
	.long	1075294132
	.type	_vmldErfHATab,@object
	.size	_vmldErfHATab,3680
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
