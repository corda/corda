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
	.file "sin_gen.c"
	.text
..TXTST0:
# -- Begin  sin
	.text
       .align    16,0x90
	.globl sin
sin:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_sin.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        subq      $16, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, 8(%rsp)
..B1.2:
        pextrw    $3, %xmm0, %eax
        andw      $32767, %ax
        subw      $12336, %ax
        cmpw      $4293, %ax
        ja        .L_2TAG_PACKET_0.0.1
        movq      PI32INV(%rip), %xmm1
        mulsd     %xmm0, %xmm1
        movapd    ONEHALF(%rip), %xmm5
        movsd     SIGN_MASK(%rip), %xmm4
        andpd     %xmm0, %xmm4
        orps      %xmm4, %xmm5
        addpd     %xmm5, %xmm1
        cvttsd2si %xmm1, %edx
        cvtsi2sd  %edx, %xmm1
        movapd    P_2(%rip), %xmm6
        movq      $0x3fb921fb54400000, %r8
        movd      %r8, %xmm3
        movapd    SC_4(%rip), %xmm5
        pshufd    $68, %xmm0, %xmm4
        mulsd     %xmm1, %xmm3
        unpcklpd  %xmm1, %xmm1
        andl      $63, %edx
        shll      $5, %edx
        lea       Ctable(%rip), %rax
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm6
        mulsd     P_3(%rip), %xmm1
        subsd     %xmm3, %xmm4
        movq      8(%rax), %xmm7
        subsd     %xmm3, %xmm0
        movapd    %xmm4, %xmm3
        unpcklpd  %xmm4, %xmm3
        subsd     %xmm6, %xmm4
        pshufd    $68, %xmm0, %xmm0
        movapd    (%rax), %xmm2
        mulpd     %xmm0, %xmm5
        subpd     %xmm6, %xmm0
        mulsd     %xmm4, %xmm7
        subsd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subsd     %xmm6, %xmm3
        movapd    SC_2(%rip), %xmm6
        subsd     %xmm3, %xmm1
        movq      24(%rax), %xmm3
        addsd     %xmm3, %xmm2
        subsd     %xmm2, %xmm7
        mulsd     %xmm4, %xmm2
        mulpd     %xmm0, %xmm6
        mulsd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm0
        addpd     SC_3(%rip), %xmm5
        mulsd     (%rax), %xmm4
        addpd     SC_1(%rip), %xmm6
        mulpd     %xmm0, %xmm5
        movq      %xmm3, %xmm0
        addsd     8(%rax), %xmm3
        mulpd     %xmm7, %xmm1
        movq      %xmm4, %xmm7
        addsd     %xmm3, %xmm4
        addpd     %xmm5, %xmm6
        movq      8(%rax), %xmm5
        subsd     %xmm3, %xmm5
        subsd     %xmm4, %xmm3
        addsd     16(%rax), %xmm1
        mulpd     %xmm2, %xmm6
        addsd     %xmm0, %xmm5
        addsd     %xmm7, %xmm3
        addsd     %xmm5, %xmm1
        addsd     %xmm3, %xmm1
        addsd     %xmm6, %xmm1
        unpckhpd  %xmm6, %xmm6
        movq      %xmm4, %xmm0
        addsd     %xmm6, %xmm1
        addsd     %xmm1, %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_0.0.1:
        jg        .L_2TAG_PACKET_1.0.1
        shrl      $4, %eax
        cmpw      $3325, %ax
        jne       .L_2TAG_PACKET_2.0.1
        mulsd     ALL_ONES(%rip), %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_2.0.1:
        movq      TWO_POW_55(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        subsd     %xmm0, %xmm3
        mulsd     TWO_POW_M55(%rip), %xmm3
        jmp       ..B1.4
.L_2TAG_PACKET_1.0.1:
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_3.0.1
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        subl      $16224, %ecx
        shrl      $7, %ecx
        andl      $65532, %ecx
        lea       PI_INV_TABLE(%rip), %r11
        addq      %r11, %rcx
        movd      %xmm0, %rax
        movl      20(%rcx), %r10d
        movl      24(%rcx), %r8d
        movl      %eax, %edx
        shrq      $21, %rax
        orl       $-2147483648, %eax
        shrl      $11, %eax
        movl      %r10d, %r9d
        imulq     %rdx, %r10
        imulq     %rax, %r9
        imulq     %rax, %r8
        movl      16(%rcx), %esi
        movl      12(%rcx), %edi
        movl      %r10d, %r11d
        shrq      $32, %r10
        addq      %r10, %r9
        addq      %r8, %r11
        movl      %r11d, %r8d
        shrq      $32, %r11
        addq      %r11, %r9
        movl      %esi, %r10d
        imulq     %rdx, %rsi
        imulq     %rax, %r10
        movl      %edi, %r11d
        imulq     %rdx, %rdi
        movl      %esi, %ebx
        shrq      $32, %rsi
        addq      %rbx, %r9
        movl      %r9d, %ebx
        shrq      $32, %r9
        addq      %rsi, %r10
        addq      %r9, %r10
        shlq      $32, %rbx
        orq       %rbx, %r8
        imulq     %rax, %r11
        movl      8(%rcx), %r9d
        movl      4(%rcx), %esi
        movl      %edi, %ebx
        shrq      $32, %rdi
        addq      %rbx, %r10
        movl      %r10d, %ebx
        shrq      $32, %r10
        addq      %rdi, %r11
        addq      %r10, %r11
        movq      %r9, %rdi
        imulq     %rdx, %r9
        imulq     %rax, %rdi
        movl      %r9d, %r10d
        shrq      $32, %r9
        addq      %r10, %r11
        movl      %r11d, %r10d
        shrq      $32, %r11
        addq      %r9, %rdi
        addq      %r11, %rdi
        movq      %rsi, %r9
        imulq     %rdx, %rsi
        imulq     %rax, %r9
        shlq      $32, %r10
        orq       %rbx, %r10
        movl      (%rcx), %eax
        movl      %esi, %r11d
        shrq      $32, %rsi
        addq      %r11, %rdi
        movl      %edi, %r11d
        shrq      $32, %rdi
        addq      %rsi, %r9
        addq      %rdi, %r9
        imulq     %rax, %rdx
        pextrw    $3, %xmm0, %ebx
        lea       PI_INV_TABLE(%rip), %rdi
        subq      %rdi, %rcx
        addl      %ecx, %ecx
        addl      %ecx, %ecx
        addl      %ecx, %ecx
        addl      $19, %ecx
        movl      $32768, %esi
        andl      %ebx, %esi
        shrl      $4, %ebx
        andl      $2047, %ebx
        subl      $1023, %ebx
        subl      %ebx, %ecx
        addq      %rdx, %r9
        movl      %ecx, %edx
        addl      $32, %edx
        cmpl      $1, %ecx
        jl        .L_2TAG_PACKET_4.0.1
        negl      %ecx
        addl      $29, %ecx
        shll      %cl, %r9d
        movl      %r9d, %edi
        andl      $536870911, %r9d
        testl     $268435456, %r9d
        jne       .L_2TAG_PACKET_5.0.1
        shrl      %cl, %r9d
        movl      $0, %ebx
        shlq      $32, %r9
        orq       %r11, %r9
.L_2TAG_PACKET_6.0.1:
.L_2TAG_PACKET_7.0.1:
        cmpq      $0, %r9
        je        .L_2TAG_PACKET_8.0.1
.L_2TAG_PACKET_9.0.1:
        bsr       %r9, %r11
        movl      $29, %ecx
        subl      %r11d, %ecx
        jle       .L_2TAG_PACKET_10.0.1
        shlq      %cl, %r9
        movq      %r10, %rax
        shlq      %cl, %r10
        addl      %ecx, %edx
        negl      %ecx
        addl      $64, %ecx
        shrq      %cl, %rax
        shrq      %cl, %r8
        orq       %rax, %r9
        orq       %r8, %r10
.L_2TAG_PACKET_11.0.1:
        cvtsi2sdq %r9, %xmm0
        shrq      $1, %r10
        cvtsi2sdq %r10, %xmm3
        xorpd     %xmm4, %xmm4
        shll      $4, %edx
        negl      %edx
        addl      $16368, %edx
        orl       %esi, %edx
        xorl      %ebx, %edx
        pinsrw    $3, %edx, %xmm4
        movq      PI_4(%rip), %xmm2
        movq      8+PI_4(%rip), %xmm6
        xorpd     %xmm5, %xmm5
        subl      $1008, %edx
        pinsrw    $3, %edx, %xmm5
        mulsd     %xmm4, %xmm0
        shll      $16, %esi
        sarl      $31, %esi
        mulsd     %xmm5, %xmm3
        movq      %xmm0, %xmm1
        mulsd     %xmm2, %xmm0
        shrl      $29, %edi
        addsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm3
        addl      %esi, %edi
        xorl      %esi, %edi
        mulsd     %xmm1, %xmm6
        movl      %edi, %eax
        addsd     %xmm3, %xmm6
        movq      %xmm0, %xmm2
        addsd     %xmm6, %xmm0
        subsd     %xmm0, %xmm2
        addsd     %xmm2, %xmm6
.L_2TAG_PACKET_12.0.1:
        movq      PI32INV(%rip), %xmm1
        mulsd     %xmm0, %xmm1
        movq      ONEHALF(%rip), %xmm5
        movq      SIGN_MASK(%rip), %xmm4
        andpd     %xmm0, %xmm4
        orps      %xmm4, %xmm5
        addpd     %xmm5, %xmm1
        cvttsd2si %xmm1, %edx
        cvtsi2sd  %edx, %xmm1
        movq      P_1(%rip), %xmm3
        movapd    P_2(%rip), %xmm2
        mulsd     %xmm1, %xmm3
        unpcklpd  %xmm1, %xmm1
        shll      $3, %eax
        addl      $1865216, %edx
        movq      %xmm0, %xmm4
        addl      %eax, %edx
        andl      $63, %edx
        movapd    SC_4(%rip), %xmm5
        lea       Ctable(%rip), %rax
        shll      $5, %edx
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm2
        subsd     %xmm3, %xmm0
        mulsd     P_3(%rip), %xmm1
        subsd     %xmm3, %xmm4
        movq      8(%rax), %xmm7
        unpcklpd  %xmm0, %xmm0
        movq      %xmm4, %xmm3
        subsd     %xmm2, %xmm4
        mulpd     %xmm0, %xmm5
        subpd     %xmm2, %xmm0
        mulsd     %xmm4, %xmm7
        subsd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subsd     %xmm2, %xmm3
        movapd    (%rax), %xmm2
        subsd     %xmm3, %xmm1
        movq      24(%rax), %xmm3
        addsd     %xmm3, %xmm2
        subsd     %xmm2, %xmm7
        subsd     %xmm6, %xmm1
        movapd    SC_2(%rip), %xmm6
        mulsd     %xmm4, %xmm2
        mulpd     %xmm0, %xmm6
        mulsd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm0
        addpd     SC_3(%rip), %xmm5
        mulsd     (%rax), %xmm4
        addpd     SC_1(%rip), %xmm6
        mulpd     %xmm0, %xmm5
        movq      %xmm3, %xmm0
        addsd     8(%rax), %xmm3
        mulpd     %xmm7, %xmm1
        movq      %xmm4, %xmm7
        addsd     %xmm3, %xmm4
        addpd     %xmm5, %xmm6
        movq      8(%rax), %xmm5
        subsd     %xmm3, %xmm5
        subsd     %xmm4, %xmm3
        addsd     16(%rax), %xmm1
        mulpd     %xmm2, %xmm6
        addsd     %xmm0, %xmm5
        addsd     %xmm7, %xmm3
        addsd     %xmm5, %xmm1
        addsd     %xmm3, %xmm1
        addsd     %xmm6, %xmm1
        unpckhpd  %xmm6, %xmm6
        movq      %xmm4, %xmm0
        addsd     %xmm6, %xmm1
        addsd     %xmm1, %xmm0
        jmp       ..B1.4
.L_2TAG_PACKET_8.0.1:
        addl      $64, %edx
        movq      %r10, %r9
        movq      %r8, %r10
        movq      $0, %r8
        cmpq      $0, %r9
        jne       .L_2TAG_PACKET_9.0.1
        addl      $64, %edx
        movq      %r10, %r9
        movq      %r8, %r10
        cmpq      $0, %r9
        jne       .L_2TAG_PACKET_9.0.1
        xorpd     %xmm0, %xmm0
        xorpd     %xmm6, %xmm6
        jmp       .L_2TAG_PACKET_12.0.1
.L_2TAG_PACKET_10.0.1:
        je        .L_2TAG_PACKET_11.0.1
        negl      %ecx
        shrq      %cl, %r10
        movq      %r9, %rax
        shrq      %cl, %r9
        subl      %ecx, %edx
        negl      %ecx
        addl      $64, %ecx
        shlq      %cl, %rax
        orq       %rax, %r10
        jmp       .L_2TAG_PACKET_11.0.1
.L_2TAG_PACKET_4.0.1:
        negl      %ecx
        shlq      $32, %r9
        orq       %r11, %r9
        shlq      %cl, %r9
        movq      %r9, %rdi
        testl     $-2147483648, %r9d
        jne       .L_2TAG_PACKET_13.0.1
        shrl      %cl, %r9d
        movl      $0, %ebx
        shrq      $3, %rdi
        jmp       .L_2TAG_PACKET_7.0.1
.L_2TAG_PACKET_5.0.1:
        shrl      %cl, %r9d
        movl      $536870912, %ebx
        shrl      %cl, %ebx
        shlq      $32, %r9
        orq       %r11, %r9
        shlq      $32, %rbx
        addl      $536870912, %edi
        movq      $0, %rcx
        movq      $0, %r11
        subq      %r8, %rcx
        sbbq      %r10, %r11
        sbbq      %r9, %rbx
        movq      %rcx, %r8
        movq      %r11, %r10
        movq      %rbx, %r9
        movl      $32768, %ebx
        jmp       .L_2TAG_PACKET_6.0.1
.L_2TAG_PACKET_13.0.1:
        shrl      %cl, %r9d
        movq      $0x100000000, %rbx
        shrq      %cl, %rbx
        movq      $0, %rcx
        movq      $0, %r11
        subq      %r8, %rcx
        sbbq      %r10, %r11
        sbbq      %r9, %rbx
        movq      %rcx, %r8
        movq      %r11, %r10
        movq      %rbx, %r9
        movl      $32768, %ebx
        shrq      $3, %rdi
        addl      $536870912, %edi
        jmp       .L_2TAG_PACKET_7.0.1
.L_2TAG_PACKET_3.0.1:
        movq      8(%rsp), %xmm0
        mulsd     NEG_ZERO(%rip), %xmm0
        movq      %xmm0, (%rsp)
.L_2TAG_PACKET_14.0.1:
..B1.4:
        addq      $16, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sin,@function
	.size	sin,.-sin
	.data
# -- End  sin
	.section .rodata, "a"
	.align 16
	.align 16
ONEHALF:
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.type	ONEHALF,@object
	.size	ONEHALF,16
	.align 16
P_2:
	.long	442499072
	.long	1032893537
	.long	442499072
	.long	1032893537
	.type	P_2,@object
	.size	P_2,16
	.align 16
SC_4:
	.long	2773927732
	.long	1053236707
	.long	436314138
	.long	1056571808
	.type	SC_4,@object
	.size	SC_4,16
	.align 16
Ctable:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	393047345
	.long	3212032302
	.long	3156849708
	.long	1069094822
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1072693248
	.long	18115067
	.long	3214126342
	.long	1013556747
	.long	1070135480
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1072693248
	.long	2476548698
	.long	3215330282
	.long	785751814
	.long	1070765062
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1072693248
	.long	2255197647
	.long	3216211105
	.long	2796464483
	.long	1071152610
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1072693248
	.long	1945768569
	.long	3216915048
	.long	939980347
	.long	1071524701
	.long	536870912
	.long	1012796809
	.long	0
	.long	1072693248
	.long	1539668340
	.long	3217396327
	.long	967731400
	.long	1071761211
	.long	536870912
	.long	1015752157
	.long	0
	.long	1072693248
	.long	1403757309
	.long	3217886718
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1013450602
	.long	0
	.long	1072693248
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	2485417816
	.long	1069626316
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3162686945
	.long	0
	.long	1071644672
	.long	2598800519
	.long	1068266419
	.long	688824739
	.long	1072339814
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1071644672
	.long	2140183630
	.long	3214756396
	.long	4051746225
	.long	1072445618
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1071644672
	.long	1699043957
	.long	3216902261
	.long	3476196678
	.long	1072533611
	.long	536870912
	.long	1014257638
	.long	0
	.long	1071644672
	.long	1991047213
	.long	1067753521
	.long	1455828442
	.long	1072602945
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1070596096
	.long	240740309
	.long	3215727903
	.long	3489094832
	.long	1072652951
	.long	536870912
	.long	1014325783
	.long	0
	.long	1070596096
	.long	257503056
	.long	3214647653
	.long	2748392742
	.long	1072683149
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1069547520
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	257503056
	.long	1067164005
	.long	2748392742
	.long	1072683149
	.long	1073741824
	.long	3163061750
	.long	0
	.long	3217031168
	.long	240740309
	.long	1068244255
	.long	3489094832
	.long	1072652951
	.long	536870912
	.long	1014325783
	.long	0
	.long	3218079744
	.long	1991047213
	.long	3215237169
	.long	1455828442
	.long	1072602945
	.long	3758096384
	.long	1015505073
	.long	0
	.long	3218079744
	.long	1699043957
	.long	1069418613
	.long	3476196678
	.long	1072533611
	.long	536870912
	.long	1014257638
	.long	0
	.long	3219128320
	.long	2140183630
	.long	1067272748
	.long	4051746225
	.long	1072445618
	.long	2147483648
	.long	3161907377
	.long	0
	.long	3219128320
	.long	2598800519
	.long	3215750067
	.long	688824739
	.long	1072339814
	.long	3758096384
	.long	1010431536
	.long	0
	.long	3219128320
	.long	2485417816
	.long	3217109964
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3162686945
	.long	0
	.long	3219128320
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	0
	.long	3219128320
	.long	1403757309
	.long	1070403070
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1013450602
	.long	0
	.long	3220176896
	.long	1539668340
	.long	1069912679
	.long	967731400
	.long	1071761211
	.long	536870912
	.long	1015752157
	.long	0
	.long	3220176896
	.long	1945768569
	.long	1069431400
	.long	939980347
	.long	1071524701
	.long	536870912
	.long	1012796809
	.long	0
	.long	3220176896
	.long	2255197647
	.long	1068727457
	.long	2796464483
	.long	1071152610
	.long	3758096384
	.long	3160878317
	.long	0
	.long	3220176896
	.long	2476548698
	.long	1067846634
	.long	785751814
	.long	1070765062
	.long	2684354560
	.long	3161838221
	.long	0
	.long	3220176896
	.long	18115067
	.long	1066642694
	.long	1013556747
	.long	1070135480
	.long	3221225472
	.long	3160567065
	.long	0
	.long	3220176896
	.long	393047345
	.long	1064548654
	.long	3156849708
	.long	1069094822
	.long	3758096384
	.long	3158189848
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	393047345
	.long	1064548654
	.long	3156849708
	.long	3216578470
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3220176896
	.long	18115067
	.long	1066642694
	.long	1013556747
	.long	3217619128
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3220176896
	.long	2476548698
	.long	1067846634
	.long	785751814
	.long	3218248710
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3220176896
	.long	2255197647
	.long	1068727457
	.long	2796464483
	.long	3218636258
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3220176896
	.long	1945768569
	.long	1069431400
	.long	939980347
	.long	3219008349
	.long	536870912
	.long	3160280457
	.long	0
	.long	3220176896
	.long	1539668340
	.long	1069912679
	.long	967731400
	.long	3219244859
	.long	536870912
	.long	3163235805
	.long	0
	.long	3220176896
	.long	1403757309
	.long	1070403070
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3160934250
	.long	0
	.long	3220176896
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	2485417816
	.long	3217109964
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1015203297
	.long	0
	.long	3219128320
	.long	2598800519
	.long	3215750067
	.long	688824739
	.long	3219823462
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3219128320
	.long	2140183630
	.long	1067272748
	.long	4051746225
	.long	3219929266
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3219128320
	.long	1699043957
	.long	1069418613
	.long	3476196678
	.long	3220017259
	.long	536870912
	.long	3161741286
	.long	0
	.long	3219128320
	.long	1991047213
	.long	3215237169
	.long	1455828442
	.long	3220086593
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3218079744
	.long	240740309
	.long	1068244255
	.long	3489094832
	.long	3220136599
	.long	536870912
	.long	3161809431
	.long	0
	.long	3218079744
	.long	257503056
	.long	1067164005
	.long	2748392742
	.long	3220166797
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3217031168
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	257503056
	.long	3214647653
	.long	2748392742
	.long	3220166797
	.long	1073741824
	.long	1015578102
	.long	0
	.long	1069547520
	.long	240740309
	.long	3215727903
	.long	3489094832
	.long	3220136599
	.long	536870912
	.long	3161809431
	.long	0
	.long	1070596096
	.long	1991047213
	.long	1067753521
	.long	1455828442
	.long	3220086593
	.long	3758096384
	.long	3162988721
	.long	0
	.long	1070596096
	.long	1699043957
	.long	3216902261
	.long	3476196678
	.long	3220017259
	.long	536870912
	.long	3161741286
	.long	0
	.long	1071644672
	.long	2140183630
	.long	3214756396
	.long	4051746225
	.long	3219929266
	.long	2147483648
	.long	1014423729
	.long	0
	.long	1071644672
	.long	2598800519
	.long	1068266419
	.long	688824739
	.long	3219823462
	.long	3758096384
	.long	3157915184
	.long	0
	.long	1071644672
	.long	2485417816
	.long	1069626316
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1015203297
	.long	0
	.long	1071644672
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	0
	.long	1071644672
	.long	1403757309
	.long	3217886718
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3160934250
	.long	0
	.long	1072693248
	.long	1539668340
	.long	3217396327
	.long	967731400
	.long	3219244859
	.long	536870912
	.long	3163235805
	.long	0
	.long	1072693248
	.long	1945768569
	.long	3216915048
	.long	939980347
	.long	3219008349
	.long	536870912
	.long	3160280457
	.long	0
	.long	1072693248
	.long	2255197647
	.long	3216211105
	.long	2796464483
	.long	3218636258
	.long	3758096384
	.long	1013394669
	.long	0
	.long	1072693248
	.long	2476548698
	.long	3215330282
	.long	785751814
	.long	3218248710
	.long	2684354560
	.long	1014354573
	.long	0
	.long	1072693248
	.long	18115067
	.long	3214126342
	.long	1013556747
	.long	3217619128
	.long	3221225472
	.long	1013083417
	.long	0
	.long	1072693248
	.long	393047345
	.long	3212032302
	.long	3156849708
	.long	3216578470
	.long	3758096384
	.long	1010706200
	.long	0
	.long	1072693248
	.type	Ctable,@object
	.size	Ctable,2048
	.align 16
SC_2:
	.long	286331153
	.long	1065423121
	.long	1431655765
	.long	1067799893
	.type	SC_2,@object
	.size	SC_2,16
	.align 16
SC_3:
	.long	436314138
	.long	3207201184
	.long	381774871
	.long	3210133868
	.type	SC_3,@object
	.size	SC_3,16
	.align 16
SC_1:
	.long	1431655765
	.long	3217380693
	.long	0
	.long	3219128320
	.type	SC_1,@object
	.size	SC_1,16
	.align 16
PI_INV_TABLE:
	.long	0
	.long	0
	.long	2734261102
	.long	1313084713
	.long	4230436817
	.long	4113882560
	.long	3680671129
	.long	1011060801
	.long	4266746795
	.long	3736847713
	.long	3072618042
	.long	1112396512
	.long	105459434
	.long	164729372
	.long	4263373596
	.long	2972297022
	.long	3900847605
	.long	784024708
	.long	3919343654
	.long	3026157121
	.long	965858873
	.long	2203269620
	.long	2625920907
	.long	3187222587
	.long	536385535
	.long	3724908559
	.long	4012839307
	.long	1510632735
	.long	1832287951
	.long	667617719
	.long	1330003814
	.long	2657085997
	.long	1965537991
	.long	3957715323
	.long	1023883767
	.long	2320667370
	.long	1811636145
	.long	529358088
	.long	1443049542
	.long	4235946923
	.long	4040145953
	.type	PI_INV_TABLE,@object
	.size	PI_INV_TABLE,164
	.space 12, 0x00 	# pad
	.align 16
PI_4:
	.long	1073741824
	.long	1072243195
	.long	407279769
	.long	1046758445
	.type	PI_4,@object
	.size	PI_4,16
	.align 8
PI32INV:
	.long	1841940611
	.long	1076125488
	.type	PI32INV,@object
	.size	PI32INV,8
	.align 8
SIGN_MASK:
	.long	0
	.long	2147483648
	.type	SIGN_MASK,@object
	.size	SIGN_MASK,8
	.align 8
P_3:
	.long	771977331
	.long	996350346
	.type	P_3,@object
	.size	P_3,8
	.align 8
ALL_ONES:
	.long	4294967295
	.long	1072693247
	.type	ALL_ONES,@object
	.size	ALL_ONES,8
	.align 8
TWO_POW_55:
	.long	0
	.long	1130364928
	.type	TWO_POW_55,@object
	.size	TWO_POW_55,8
	.align 8
TWO_POW_M55:
	.long	0
	.long	1015021568
	.type	TWO_POW_M55,@object
	.size	TWO_POW_M55,8
	.align 8
P_1:
	.long	1413480448
	.long	1069097467
	.type	P_1,@object
	.size	P_1,8
	.align 8
NEG_ZERO:
	.long	0
	.long	2147483648
	.type	NEG_ZERO,@object
	.size	NEG_ZERO,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
