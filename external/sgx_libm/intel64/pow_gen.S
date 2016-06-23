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
	.file "pow_gen.c"
	.text
..TXTST0:
# -- Begin  pow
	.text
       .align    16,0x90
	.globl pow
pow:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_pow.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movsd     %xmm0, 8(%rsp)
        movsd     %xmm1, 16(%rsp)
..B1.2:
        movlpd    SIGMASK(%rip), %xmm7
        movlpd    ONEMASK(%rip), %xmm2
        andpd     %xmm0, %xmm7
        movq      %xmm0, %xmm4
        psrlq     $44, %xmm0
        pextrw    $0, %xmm0, %eax
        orpd      %xmm2, %xmm7
        pextrw    $3, %xmm4, %ecx
        andl      $255, %eax
        addl      $1, %eax
        andl      $510, %eax
        lea       rcp_t1(%rip), %r8
        mulsd     (%r8,%rax,4), %xmm7
        movlpd    (%r8,%rax,4), %xmm5
        addl      %eax, %eax
        lea       log2_t1(%rip), %r8
        movapd    (%r8,%rax,4), %xmm6
        movl      $32751, %edx
        subl      %ecx, %edx
        subl      $16, %ecx
        orl       %edx, %ecx
        cmpl      $-2147483648, %ecx
        jae       .L_2TAG_PACKET_0.0.2
        movl      $0, %ecx
        movl      $261759, %edx
.L_2TAG_PACKET_1.0.2:
.L_2TAG_PACKET_2.0.2:
        movd      %edx, %xmm1
        psubq     %xmm1, %xmm0
        psrlq     $8, %xmm0
        cvtdq2pd  %xmm0, %xmm0
        movlpd    HALFSIG(%rip), %xmm1
        movq      %xmm7, %xmm3
        psrlq     $38, %xmm7
        pextrw    $0, %xmm7, %eax
        andpd     SIGMASK(%rip), %xmm4
        andl      $255, %eax
        addl      $1, %eax
        andl      $510, %eax
        lea       rcp_t2(%rip), %r8
        mulsd     (%r8,%rax,4), %xmm3
        mulsd     (%r8,%rax,4), %xmm5
        addl      %eax, %eax
        lea       log2_t2(%rip), %r8
        addpd     (%r8,%rax,4), %xmm6
        orpd      ONEMASK(%rip), %xmm4
        addsd     %xmm0, %xmm6
        andpd     %xmm4, %xmm1
        movq      %xmm3, %xmm2
        psrlq     $31, %xmm3
        pextrw    $0, %xmm3, %eax
        movlpd    HALFSIG(%rip), %xmm0
        subsd     %xmm1, %xmm4
        movlpd    LHN(%rip), %xmm7
        andl      $511, %eax
        addl      $1, %eax
        andl      $1022, %eax
        lea       rcp_t3(%rip), %r8
        mulsd     (%r8,%rax,4), %xmm5
        mulsd     (%r8,%rax,4), %xmm2
        lea       log2_t3(%rip), %r8
        addpd     (%r8,%rax,8), %xmm6
        andpd     %xmm5, %xmm0
        subsd     %xmm0, %xmm5
        addsd     %xmm2, %xmm7
        movq      %xmm0, %xmm3
        mulsd     %xmm1, %xmm0
        mulsd     %xmm5, %xmm1
        mulsd     %xmm4, %xmm3
        subsd     %xmm0, %xmm2
        mulsd     %xmm5, %xmm4
        movq      %xmm6, %xmm0
        subsd     %xmm1, %xmm2
        addsd     %xmm7, %xmm6
        movlpd    16(%rsp), %xmm1
        pextrw    $3, %xmm1, %eax
        subsd     %xmm3, %xmm2
        subsd     %xmm6, %xmm0
        movlpd    HALFSIG(%rip), %xmm3
        pextrw    $3, %xmm6, %edx
        subsd     %xmm4, %xmm2
        movq      %xmm6, %xmm4
        addsd     %xmm7, %xmm0
        subsd     %xmm2, %xmm7
        subsd     %xmm2, %xmm6
        unpcklpd  %xmm7, %xmm7
        andl      $32752, %eax
        cmpl      $32752, %eax
        jae       .L_2TAG_PACKET_3.0.2
        andl      $32752, %edx
        subl      $16368, %eax
        addl      %edx, %eax
        movl      $16544, %edx
        subl      %eax, %edx
        subl      $15472, %eax
        orl       %eax, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_5.0.2:
        subsd     %xmm6, %xmm4
        subsd     %xmm4, %xmm2
        movlpd    HALFSIG(%rip), %xmm4
        andpd     %xmm1, %xmm3
        andpd     %xmm6, %xmm4
        subsd     %xmm2, %xmm0
        xorpd     %xmm2, %xmm2
        movl      $16480, %edx
        pinsrw    $3, %edx, %xmm2
        movq      %xmm3, %xmm5
        mulsd     %xmm4, %xmm3
        subsd     %xmm4, %xmm6
        subsd     %xmm5, %xmm1
        mulsd     %xmm2, %xmm3
        mulsd     %xmm6, %xmm5
        movapd    clv(%rip), %xmm2
        mulsd     %xmm1, %xmm4
        cvtsd2si  %xmm3, %eax
        mulsd     %xmm1, %xmm6
        movapd    16+clv(%rip), %xmm1
        addsd     %xmm4, %xmm5
        pshufd    $238, %xmm6, %xmm4
        addsd     %xmm6, %xmm5
        movl      $130943, %edx
        subl      %eax, %edx
        addl      $123391, %eax
        orl       %eax, %edx
        subl      $123391, %eax
        cmpl      $0, %edx
        jle       .L_2TAG_PACKET_6.0.2
        addl      %eax, %ecx
        andl      $127, %eax
        andl      $-128, %ecx
        addl      $130944, %ecx
        addsd     %xmm0, %xmm4
        movq      %xmm3, %xmm0
        addsd     Shifter(%rip), %xmm3
        mulpd     %xmm7, %xmm2
        subsd     Shifter(%rip), %xmm3
        mulpd     %xmm7, %xmm7
        subsd     %xmm3, %xmm0
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        lea       exp2_tbl(%rip), %r8
        movapd    (%r8,%rax), %xmm3
        movapd    cev(%rip), %xmm6
        mulpd     %xmm7, %xmm1
        addpd     %xmm1, %xmm2
        pshufd    $238, %xmm2, %xmm1
        mulsd     %xmm7, %xmm2
        xorpd     %xmm7, %xmm7
        movl      $16256, %edx
        addsd     %xmm1, %xmm2
        pinsrw    $3, %edx, %xmm7
        addsd     %xmm4, %xmm2
        movlpd    32+cev(%rip), %xmm4
        movd      %ecx, %xmm1
        mulsd     16(%rsp), %xmm2
        mulsd     %xmm7, %xmm0
        psllq     $45, %xmm1
        pshufd    $68, %xmm1, %xmm1
        movapd    16+cev(%rip), %xmm7
        addsd     %xmm2, %xmm5
        addsd     %xmm5, %xmm0
        unpcklpd  %xmm0, %xmm0
        mulpd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm4
        pshufd    $238, %xmm3, %xmm5
        mulpd     %xmm0, %xmm0
        addpd     %xmm6, %xmm7
        mulpd     %xmm0, %xmm7
        mulsd     %xmm3, %xmm0
        pshufd    $238, %xmm7, %xmm6
        mulsd     %xmm7, %xmm0
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm4
        addsd     %xmm5, %xmm0
        addsd     %xmm6, %xmm0
        addsd     %xmm4, %xmm0
        addsd     %xmm3, %xmm0
        mulsd     %xmm1, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
        movlpd    16(%rsp), %xmm1
        movlpd    ABSVALMASK(%rip), %xmm3
        movd      %xmm1, %eax
        movq      %xmm1, %xmm2
        andpd     %xmm3, %xmm1
        psrlq     $32, %xmm1
        movd      %xmm1, %ecx
        cmpl      $2146435072, %ecx
        jae       .L_2TAG_PACKET_7.0.2
        orl       %ecx, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_8.0.2
        cmpl      $0, %edx
        jge       .L_2TAG_PACKET_9.0.2
        negl      %edx
        addl      $32751, %edx
        psllq     $52, %xmm3
        orpd      %xmm3, %xmm2
        movl      $1011, %ecx
        movd      %ecx, %xmm3
        psrlq     $20, %xmm1
        psubd     %xmm3, %xmm1
        pxor      %xmm3, %xmm3
        pmaxsw    %xmm3, %xmm1
        psllq     %xmm1, %xmm2
        pcmpeqd   %xmm3, %xmm2
        pmovmskb  %xmm2, %eax
        movl      %edx, %ecx
        andl      $32767, %edx
        cmpl      $32752, %edx
        jae       .L_2TAG_PACKET_10.0.2
        andl      $255, %eax
        cmpl      $255, %eax
        jne       .L_2TAG_PACKET_11.0.2
        movlpd    16(%rsp), %xmm1
        movlpd    16(%rsp), %xmm2
        movl      $1012, %ecx
        movd      %ecx, %xmm3
        andpd     ABSVALMASK(%rip), %xmm1
        psrlq     $52, %xmm1
        psubd     %xmm3, %xmm1
        movlpd    NEG_ZERO(%rip), %xmm3
        psllq     %xmm1, %xmm2
        pcmpeqd   %xmm3, %xmm2
        pmovmskb  %xmm2, %eax
        andl      $255, %eax
        movl      $261889, %ecx
        addl      %eax, %ecx
        andl      $262144, %ecx
        cmpl      $16, %edx
        jb        .L_2TAG_PACKET_12.0.2
        movl      $786047, %edx
        movlpd    SIGMASK(%rip), %xmm3
        movlpd    ONEMASK(%rip), %xmm2
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_7.0.2:
        movlpd    8(%rsp), %xmm7
        movlpd    8(%rsp), %xmm4
        movd      %xmm7, %edx
        psrlq     $32, %xmm7
        movd      %xmm7, %eax
        movl      %eax, %ecx
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jb        .L_2TAG_PACKET_13.0.2
        ja        .L_2TAG_PACKET_14.0.2
        cmpl      $0, %edx
        ja        .L_2TAG_PACKET_14.0.2
        jmp       .L_2TAG_PACKET_13.0.2
.L_2TAG_PACKET_9.0.2:
        movl      $0, %ecx
.L_2TAG_PACKET_12.0.2:
        xorpd     %xmm0, %xmm0
        movl      $17392, %eax
        pinsrw    $3, %eax, %xmm0
        movlpd    SIGMASK(%rip), %xmm7
        movlpd    ONEMASK(%rip), %xmm2
        mulsd     %xmm4, %xmm0
        movd      %xmm4, %edx
        psrlq     $32, %xmm4
        movd      %xmm4, %eax
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_15.0.2
        movq      16(%rsp), %xmm4
        pextrw    $3, %xmm4, %eax
        andl      $49152, %eax
        cmpl      $16384, %eax
        je        .L_2TAG_PACKET_16.0.2
.L_2TAG_PACKET_17.0.2:
        andpd     %xmm0, %xmm7
        movq      %xmm0, %xmm4
        andpd     ABSVALMASK(%rip), %xmm0
        psrlq     $44, %xmm0
        pextrw    $0, %xmm0, %eax
        orpd      %xmm2, %xmm7
        andl      $255, %eax
        addl      $1, %eax
        andl      $510, %eax
        lea       rcp_t1(%rip), %r8
        mulsd     (%r8,%rax,4), %xmm7
        movlpd    (%r8,%rax,4), %xmm5
        addl      %eax, %eax
        lea       log2_t1(%rip), %r8
        movapd    (%r8,%rax,4), %xmm6
        movl      $278143, %edx
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_15.0.2:
        movl      %eax, %edx
        andl      $2147483647, %edx
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_17.0.2
        movl      20(%rsp), %edx
        andl      $-2147483648, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_18.0.2
        shll      $13, %ecx
        andl      %ecx, %eax
        movl      $2146435072, %edx
        orl       %eax, %edx
        movd      %edx, %xmm0
        psllq     $32, %xmm0
        movlpd    ONEMASK(%rip), %xmm1
        movlpd    8(%rsp), %xmm2
        divsd     %xmm2, %xmm1
.L_2TAG_PACKET_19.0.2:
        movl      $27, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_18.0.2:
        shll      $13, %ecx
        andl      %ecx, %eax
        cmpl      $0, %eax
        jne       .L_2TAG_PACKET_21.0.2
        pxor      %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_10.0.2:
        movlpd    SIGMASK(%rip), %xmm3
        xorpd     %xmm1, %xmm1
        andpd     %xmm4, %xmm3
        pcmpeqd   %xmm3, %xmm1
        pmovmskb  %xmm1, %ecx
        andl      $255, %ecx
        cmpl      $255, %ecx
        jne       .L_2TAG_PACKET_14.0.2
        pextrw    $3, %xmm4, %ecx
        andl      $32768, %ecx
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_22.0.2
        andl      $255, %eax
        cmpl      $255, %eax
        jne       .L_2TAG_PACKET_23.0.2
        movlpd    16(%rsp), %xmm1
        movlpd    16(%rsp), %xmm2
        movl      $1012, %ecx
        movd      %ecx, %xmm3
        andpd     ABSVALMASK(%rip), %xmm1
        psrlq     $52, %xmm1
        psubd     %xmm3, %xmm1
        pxor      %xmm3, %xmm3
        psllq     %xmm1, %xmm2
        pcmpeqd   %xmm3, %xmm2
        pmovmskb  %xmm2, %eax
        andl      $255, %eax
        cmpl      $255, %eax
        je        .L_2TAG_PACKET_23.0.2
        movlpd    16(%rsp), %xmm1
        pextrw    $3, %xmm1, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_24.0.2
.L_2TAG_PACKET_21.0.2:
        movq      NEG_ZERO(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_24.0.2:
        movq      NEG_INF(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_23.0.2:
        movlpd    16(%rsp), %xmm1
        pextrw    $3, %xmm1, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_25.0.2
        pxor      %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_22.0.2:
        movlpd    16(%rsp), %xmm1
        pextrw    $3, %xmm1, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_25.0.2
        pxor      %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_14.0.2:
        addsd     %xmm4, %xmm4
        movq      %xmm4, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_11.0.2:
        movlpd    8(%rsp), %xmm2
        movd      %xmm2, %eax
        psrlq     $32, %xmm2
        movd      %xmm2, %edx
        andl      $2147483647, %edx
        orl       %edx, %eax
        movl      $0, %ecx
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_15.0.2
        movlpd    sNaN(%rip), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $28, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_3.0.2:
        movlpd    8(%rsp), %xmm4
        movlpd    16(%rsp), %xmm2
        movd      %xmm4, %eax
        cmpl      $0, %eax
        jne       .L_2TAG_PACKET_13.0.2
        psrlq     $32, %xmm4
        movd      %xmm4, %edx
        cmpl      $1072693248, %edx
        je        .L_2TAG_PACKET_26.0.2
        cmpl      $-1074790400, %edx
        jne       .L_2TAG_PACKET_13.0.2
        movlpd    SIGMASK(%rip), %xmm3
        xorpd     %xmm1, %xmm1
        andpd     %xmm2, %xmm3
        pcmpeqd   %xmm3, %xmm1
        pmovmskb  %xmm1, %eax
        cmpl      $255, %eax
        jne       .L_2TAG_PACKET_27.0.2
        movq      ONEMASK(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_13.0.2:
        movlpd    SIGMASK(%rip), %xmm3
        xorpd     %xmm1, %xmm1
        andpd     %xmm2, %xmm3
        pcmpeqd   %xmm3, %xmm1
        pmovmskb  %xmm1, %eax
        andl      $255, %eax
        cmpl      $255, %eax
        jne       .L_2TAG_PACKET_27.0.2
        pextrw    $3, %xmm2, %eax
        movlpd    8(%rsp), %xmm4
        andl      $32768, %eax
        xorl      $-1074790400, %ecx
        orl       %ecx, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_26.0.2
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_28.0.2
        pextrw    $3, %xmm4, %eax
        andl      $32752, %eax
        cmpl      $16368, %eax
        jae       .L_2TAG_PACKET_29.0.2
        movd      %xmm4, %eax
        psrlq     $32, %xmm4
        movd      %xmm4, %edx
        andl      $2147483647, %edx
        orl       %eax, %edx
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_25.0.2
        xorpd     %xmm1, %xmm1
        xorpd     %xmm4, %xmm4
        movl      $16368, %eax
        pinsrw    $3, %eax, %xmm1
        divsd     %xmm4, %xmm1
        movq      %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_19.0.2
.L_2TAG_PACKET_29.0.2:
        pxor      %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_28.0.2:
        pextrw    $3, %xmm4, %eax
        andl      $32752, %eax
        cmpl      $16368, %eax
        jae       .L_2TAG_PACKET_25.0.2
        pxor      %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_25.0.2:
        movq      INF(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_27.0.2:
        addsd     %xmm2, %xmm2
        movq      %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_30.0.2:
        movl      $26, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_31.0.2:
        movl      $29, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_8.0.2:
        movd      %xmm4, %eax
        psrlq     $32, %xmm4
        movd      %xmm4, %edx
        andl      $2147483647, %edx
        movl      %eax, %ecx
        orl       %edx, %eax
        movlpd    ONEMASK(%rip), %xmm0
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_30.0.2
        movd      %xmm4, %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        ja        .L_2TAG_PACKET_31.0.2
        jb        .L_2TAG_PACKET_32.0.2
        cmpl      $0, %ecx
        ja        .L_2TAG_PACKET_31.0.2
.L_2TAG_PACKET_32.0.2:
.L_2TAG_PACKET_26.0.2:
        movq      ONEMASK(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_33.0.2:
        movl      $24, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_34.0.2:
        movl      $25, (%rsp)
        jmp       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_6.0.2:
        cmpl      $0, %eax
        jle       .L_2TAG_PACKET_35.0.2
        cmpl      $262144, %eax
        jae       .L_2TAG_PACKET_36.0.2
        movl      %eax, %edx
        andl      $127, %eax
        addl      $261888, %ecx
        subl      $128, %edx
        andl      $-128, %edx
        movl      $16368, %edi
        jmp       .L_2TAG_PACKET_37.0.2
.L_2TAG_PACKET_35.0.2:
        cmpl      $-261632, %eax
        jle       .L_2TAG_PACKET_16.0.2
        movl      %eax, %edx
        andl      $127, %eax
        addl      $128, %ecx
        andl      $-128, %edx
        addl      $261760, %edx
        movl      $0, %edi
.L_2TAG_PACKET_37.0.2:
        addsd     %xmm0, %xmm4
        movq      %xmm3, %xmm0
        addsd     Shifter(%rip), %xmm3
        movl      %edx, %esi
        subl      $130944, %esi
        mulpd     %xmm7, %xmm2
        subsd     Shifter(%rip), %xmm3
        mulpd     %xmm7, %xmm7
        subsd     %xmm3, %xmm0
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        lea       exp2_tbl(%rip), %r8
        movapd    (%r8,%rax), %xmm3
        movapd    cev(%rip), %xmm6
        mulpd     %xmm7, %xmm1
        addpd     %xmm1, %xmm2
        pshufd    $238, %xmm2, %xmm1
        mulsd     %xmm7, %xmm2
        addsd     %xmm1, %xmm2
        addsd     %xmm4, %xmm2
        movlpd    32+cev(%rip), %xmm4
        movd      %edx, %xmm1
        subl      $130944, %edx
        negl      %edx
        sarl      $7, %edx
        addl      $2, %edx
        movl      %edx, %eax
        andl      $32, %eax
        addl      %eax, %edx
        xorpd     %xmm7, %xmm7
        movl      $16256, %eax
        pinsrw    $3, %eax, %xmm7
        mulsd     16(%rsp), %xmm2
        mulsd     %xmm7, %xmm0
        psllq     $45, %xmm1
        pshufd    $68, %xmm1, %xmm1
        movapd    16+cev(%rip), %xmm7
        addsd     %xmm2, %xmm5
        mulpd     %xmm1, %xmm3
        addsd     %xmm5, %xmm0
        unpcklpd  %xmm0, %xmm0
        mulpd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm4
        mulpd     %xmm0, %xmm0
        addpd     %xmm6, %xmm7
        mulpd     %xmm0, %xmm7
        mulsd     %xmm3, %xmm0
        pshufd    $238, %xmm7, %xmm6
        mulsd     %xmm7, %xmm0
        pshufd    $238, %xmm3, %xmm5
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm4
        movd      %ecx, %xmm7
        psllq     $45, %xmm7
        movd      %edx, %xmm2
        pcmpeqd   %xmm1, %xmm1
        psllq     %xmm2, %xmm1
        addsd     %xmm5, %xmm0
        addsd     %xmm6, %xmm0
        andpd     %xmm3, %xmm1
        addsd     %xmm4, %xmm0
        xorpd     %xmm6, %xmm6
        pcmpeqd   %xmm4, %xmm4
        psllq     %xmm2, %xmm4
        subsd     %xmm1, %xmm3
        movq      %xmm1, %xmm2
        addsd     %xmm0, %xmm1
        andpd     %xmm4, %xmm1
        pinsrw    $3, %edi, %xmm6
        subsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        cmpl      $0, %esi
        jge       .L_2TAG_PACKET_38.0.2
        mulsd     %xmm7, %xmm0
        mulsd     %xmm7, %xmm1
        addsd     %xmm1, %xmm0
        mulsd     %xmm0, %xmm6
        addsd     %xmm6, %xmm0
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_33.0.2
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_34.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_38.0.2:
        addsd     %xmm1, %xmm0
        mulsd     %xmm7, %xmm0
        mulsd     %xmm0, %xmm6
        addsd     %xmm6, %xmm0
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_33.0.2
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_39.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_39.0.2:
        xorpd     %xmm2, %xmm2
        movl      $1, %eax
        movd      %eax, %xmm2
        mulsd     %xmm2, %xmm2
        jmp       .L_2TAG_PACKET_34.0.2
.L_2TAG_PACKET_16.0.2:
        movlpd    EMIN(%rip), %xmm0
        movd      %ecx, %xmm1
        mulsd     %xmm0, %xmm0
        psllq     $45, %xmm1
        orpd      %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_34.0.2
.L_2TAG_PACKET_36.0.2:
        xorpd     %xmm1, %xmm1
        movl      $32751, %eax
        pinsrw    $3, %eax, %xmm1
        mulsd     %xmm1, %xmm1
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_40.0.2
        movlpd    NEG_INF(%rip), %xmm0
        jmp       .L_2TAG_PACKET_33.0.2
.L_2TAG_PACKET_40.0.2:
        movlpd    INF(%rip), %xmm0
        jmp       .L_2TAG_PACKET_33.0.2
.L_2TAG_PACKET_41.0.2:
        movlpd    8(%rsp), %xmm4
        pextrw    $3, %xmm4, %edx
        andl      $32752, %edx
        subl      $16368, %edx
        pextrw    $3, %xmm1, %eax
        xorl      %edx, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        jne       .L_2TAG_PACKET_16.0.2
        jmp       .L_2TAG_PACKET_36.0.2
.L_2TAG_PACKET_4.0.2:
        pshufd    $68, %xmm1, %xmm4
        mulpd     %xmm6, %xmm4
        pextrw    $3, %xmm4, %eax
        andl      $32752, %eax
        movl      $16544, %edx
        subl      %eax, %edx
        subl      $15472, %eax
        orl       %eax, %edx
        cmpl      $-2147483648, %edx
        jb        .L_2TAG_PACKET_5.0.2
        cmpl      $-2147483648, %eax
        jb        .L_2TAG_PACKET_41.0.2
        orl       $130944, %ecx
        movd      %ecx, %xmm0
        psllq     $45, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_20.0.2:
        movq      %xmm0, 24(%rsp)
..B1.3:
        movq      24(%rsp), %xmm0
.L_2TAG_PACKET_42.0.2:
..B1.5:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	pow,@function
	.size	pow,.-pow
	.data
# -- End  pow
	.section .rodata, "a"
	.align 16
	.align 16
SIGMASK:
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.type	SIGMASK,@object
	.size	SIGMASK,16
	.align 16
ONEMASK:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.type	ONEMASK,@object
	.size	ONEMASK,16
	.align 16
log2_t1:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1641152512
	.long	1065811444
	.long	1188689655
	.long	1027383036
	.long	830078976
	.long	1066853925
	.long	1683363035
	.long	1027948302
	.long	2542927872
	.long	1067522658
	.long	3621009110
	.long	1028493916
	.long	2686386176
	.long	1067891457
	.long	1949948784
	.long	1027381598
	.long	1667006464
	.long	1068256947
	.long	3184186889
	.long	1028293010
	.long	2150858752
	.long	1068558987
	.long	2486115301
	.long	1028528023
	.long	1655242752
	.long	1068739056
	.long	773151300
	.long	1026560495
	.long	1341652992
	.long	1068918120
	.long	2376679343
	.long	1026589938
	.long	861732864
	.long	1069095356
	.long	3976461951
	.long	1025794178
	.long	3619700736
	.long	1069270713
	.long	1113925208
	.long	1027979240
	.long	3789324288
	.long	1069445746
	.long	590586460
	.long	1028271785
	.long	2182004736
	.long	1069583575
	.long	297009670
	.long	1026900933
	.long	976470016
	.long	1069669519
	.long	2876077132
	.long	1027955084
	.long	665796608
	.long	1069755258
	.long	4251263665
	.long	1023912865
	.long	1282670592
	.long	1069839958
	.long	614174576
	.long	1028506527
	.long	1687183360
	.long	1069924424
	.long	2120169064
	.long	1026082260
	.long	4017643520
	.long	1070008228
	.long	1495131896
	.long	1028622058
	.long	4207116288
	.long	1070091353
	.long	1370573431
	.long	1027824851
	.long	1789706240
	.long	1070173781
	.long	3512135184
	.long	1026825352
	.long	53207040
	.long	1070255920
	.long	3737096549
	.long	1026438963
	.long	330194944
	.long	1070337330
	.long	2978734829
	.long	1028330940
	.long	3604373504
	.long	1070418425
	.long	3265342761
	.long	1026602327
	.long	2318204928
	.long	1070498761
	.long	3507296498
	.long	1027877621
	.long	3818315776
	.long	1070578756
	.long	677794871
	.long	1028109305
	.long	154591232
	.long	1070627028
	.long	135420162
	.long	1027142134
	.long	632459264
	.long	1070666446
	.long	256228069
	.long	1027376412
	.long	2011426816
	.long	1070705675
	.long	184843939
	.long	1027577616
	.long	2429726720
	.long	1070744485
	.long	3907638365
	.long	1027382133
	.long	2912112640
	.long	1070783319
	.long	596296254
	.long	1022964981
	.long	3197370368
	.long	1070821720
	.long	1122235614
	.long	1028265681
	.long	1147121664
	.long	1070859908
	.long	1274805357
	.long	1028037537
	.long	2702757888
	.long	1070897876
	.long	1929563301
	.long	1027984695
	.long	3792781312
	.long	1070935387
	.long	260587083
	.long	1027244900
	.long	1736605696
	.long	1070972899
	.long	1038777690
	.long	1028224348
	.long	2490597376
	.long	1071010174
	.long	574252996
	.long	1028557352
	.long	2465140736
	.long	1071047207
	.long	243175481
	.long	1026641700
	.long	2144403456
	.long	1071083992
	.long	835940059
	.long	1025870511
	.long	1797877760
	.long	1071120523
	.long	2362104182
	.long	1024853432
	.long	1487233024
	.long	1071156794
	.long	1462661599
	.long	1025546239
	.long	2657701888
	.long	1071193041
	.long	3841377895
	.long	1028504382
	.long	2881359872
	.long	1071229019
	.long	174174934
	.long	1028491418
	.long	1607143424
	.long	1071264722
	.long	3643575766
	.long	1028610165
	.long	2395561984
	.long	1071300143
	.long	2430782595
	.long	1027372849
	.long	658427904
	.long	1071335525
	.long	161357664
	.long	1028306250
	.long	242749440
	.long	1071370366
	.long	470667040
	.long	1025936066
	.long	3127775232
	.long	1071405408
	.long	2529919486
	.long	1027847357
	.long	2341548032
	.long	1071440150
	.long	89987554
	.long	1026534819
	.long	539168768
	.long	1071474585
	.long	2531816708
	.long	1025043792
	.long	234864640
	.long	1071508706
	.long	753765034
	.long	1027859052
	.long	2905993216
	.long	1071542763
	.long	3767440865
	.long	1023481353
	.long	3544465408
	.long	1071576755
	.long	756421882
	.long	1027650395
	.long	2658430976
	.long	1071610420
	.long	2178519328
	.long	1028288112
	.long	2660024320
	.long	1071644012
	.long	780560797
	.long	1023043397
	.long	2121920512
	.long	1071660969
	.long	3880750062
	.long	1025990162
	.long	2371102720
	.long	1071677556
	.long	2026769115
	.long	1027312699
	.long	1355743232
	.long	1071694102
	.long	3943781029
	.long	1028003666
	.long	649166848
	.long	1071710472
	.long	2315768408
	.long	1028563913
	.long	3712673792
	.long	1071726796
	.long	427741889
	.long	1025493722
	.long	1031030784
	.long	1071743075
	.long	132151873
	.long	1025660183
	.long	1854838784
	.long	1071759170
	.long	1812291413
	.long	1027042047
	.long	1572052992
	.long	1071775215
	.long	2231490673
	.long	1027883742
	.long	3357544448
	.long	1071791208
	.long	2435425658
	.long	1025969268
	.long	2690285568
	.long	1071807011
	.long	816977062
	.long	1028436202
	.long	473251840
	.long	3218771869
	.long	1330616403
	.long	3175482613
	.long	2934157312
	.long	3218740486
	.long	3685911680
	.long	3175415699
	.long	2326081536
	.long	3218709220
	.long	4062709
	.long	3171839703
	.long	1474240512
	.long	3218678073
	.long	3223011199
	.long	3174963306
	.long	2315530240
	.long	3218647330
	.long	3482179715
	.long	3175726112
	.long	3209805824
	.long	3218616430
	.long	1850213782
	.long	3175467046
	.long	92246016
	.long	3218585658
	.long	2672047941
	.long	3174429943
	.long	939302912
	.long	3218555302
	.long	2683312141
	.long	3175097844
	.long	3886694400
	.long	3218525081
	.long	3584491562
	.long	3175164762
	.long	3897806848
	.long	3218494709
	.long	2353689255
	.long	3175401216
	.long	2446540800
	.long	3218464767
	.long	3549477525
	.long	3175044174
	.long	863473664
	.long	3218434969
	.long	834332530
	.long	3174636992
	.long	1568866304
	.long	3218405023
	.long	3528175174
	.long	3174626157
	.long	558092288
	.long	3218375520
	.long	2586999089
	.long	3168836697
	.long	2316124160
	.long	3218346169
	.long	686294058
	.long	3172297276
	.long	3825598464
	.long	3218316675
	.long	1136723030
	.long	3175382806
	.long	4172640256
	.long	3218287637
	.long	3760034353
	.long	3171774178
	.long	143781888
	.long	3218258460
	.long	1815979570
	.long	3174536455
	.long	2912956416
	.long	3218229746
	.long	4273313359
	.long	3173677065
	.long	1821376512
	.long	3218200896
	.long	1051181106
	.long	3175433943
	.long	3545214976
	.long	3218172213
	.long	881689764
	.long	3173077446
	.long	977260544
	.long	3218144009
	.long	607141
	.long	3173756076
	.long	2363125760
	.long	3218115672
	.long	1333321633
	.long	3175335724
	.long	2364801024
	.long	3218087202
	.long	3356747073
	.long	3174852978
	.long	2121375744
	.long	3218038698
	.long	549802689
	.long	3174897014
	.long	155041792
	.long	3217983102
	.long	4093639872
	.long	3174461350
	.long	4268572672
	.long	3217927248
	.long	3146719241
	.long	3173737604
	.long	3896410112
	.long	3217871768
	.long	3834824761
	.long	3171284429
	.long	492560384
	.long	3217816668
	.long	239252792
	.long	3173483664
	.long	4219723776
	.long	3217761952
	.long	3911150007
	.long	3175766937
	.long	1522245632
	.long	3217706989
	.long	2153956895
	.long	3175454574
	.long	3796140032
	.long	3217653061
	.long	1733676290
	.long	3175095395
	.long	155754496
	.long	3217598893
	.long	1693604437
	.long	3175909818
	.long	2753691648
	.long	3217544480
	.long	3490227517
	.long	3173083716
	.long	3246325760
	.long	3217491126
	.long	1834407662
	.long	3175168640
	.long	2629189632
	.long	3217437536
	.long	394182940
	.long	3174297893
	.long	4285202432
	.long	3217384365
	.long	127148739
	.long	3175942199
	.long	2197757952
	.long	3217331621
	.long	326147828
	.long	3175005587
	.long	623484928
	.long	3217278646
	.long	2190917468
	.long	3175273136
	.long	1792204800
	.long	3217226104
	.long	3259512355
	.long	3174781320
	.long	41181184
	.long	3217174003
	.long	3260046653
	.long	3174058211
	.long	1413185536
	.long	3217121676
	.long	2746620881
	.long	3175463126
	.long	2720399360
	.long	3217069797
	.long	590176758
	.long	3175866008
	.long	1549172736
	.long	3217005579
	.long	3081069142
	.long	3175953539
	.long	2465087488
	.long	3216902292
	.long	4241850246
	.long	3175110025
	.long	3847749632
	.long	3216799930
	.long	59228567
	.long	3174380829
	.long	4210442240
	.long	3216698507
	.long	2884243012
	.long	3174896587
	.long	1231667200
	.long	3216596658
	.long	2996426567
	.long	3174508538
	.long	1101037568
	.long	3216495763
	.long	3170347605
	.long	3176066808
	.long	2861760512
	.long	3216395836
	.long	52313802
	.long	3174148818
	.long	3538583552
	.long	3216295495
	.long	291262391
	.long	3169534916
	.long	2493661184
	.long	3216196139
	.long	979886872
	.long	3175226185
	.long	3478798336
	.long	3216096373
	.long	329155479
	.long	3175972274
	.long	4175200256
	.long	3215997608
	.long	3920581672
	.long	3172282657
	.long	4103045120
	.long	3215814286
	.long	1366711740
	.long	3174574396
	.long	3613917184
	.long	3215617982
	.long	3682391844
	.long	3173719142
	.long	3246555136
	.long	3215423741
	.long	4071576370
	.long	3174315914
	.long	575995904
	.long	3215228718
	.long	2479189453
	.long	3173663302
	.long	2771812352
	.long	3215032905
	.long	1608471371
	.long	3175357197
	.long	3995074560
	.long	3214744373
	.long	766833229
	.long	3175292855
	.long	830078976
	.long	3214361213
	.long	1258533011
	.long	3175547121
	.long	219152384
	.long	3213976530
	.long	1716511551
	.long	3175540921
	.long	1290797056
	.long	3213306911
	.long	1477436787
	.long	3173730612
	.long	1836580864
	.long	3212249540
	.long	1456335140
	.long	3175441338
	.long	0
	.long	0
	.long	0
	.long	0
	.type	log2_t1,@object
	.size	log2_t1,2064
	.align 16
log2_t2:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2088763392
	.long	1059147817
	.long	4130365781
	.long	1025575177
	.long	3758096384
	.long	1060385579
	.long	3761435677
	.long	1024020876
	.long	1623195648
	.long	1061142175
	.long	1460419383
	.long	1028311484
	.long	2740977664
	.long	1061528887
	.long	1629990790
	.long	1027810560
	.long	3418357760
	.long	1061907277
	.long	3164482262
	.long	1027367455
	.long	372244480
	.long	1062246601
	.long	749537322
	.long	1027570412
	.long	1678770176
	.long	1062435842
	.long	1420381118
	.long	1028382503
	.long	3506438144
	.long	1062625106
	.long	2539787500
	.long	1028059273
	.long	1584398336
	.long	1062814394
	.long	4277851274
	.long	1025918452
	.long	230686720
	.long	1063003705
	.long	2279601279
	.long	1027165899
	.long	3765436416
	.long	1063193038
	.long	1648733663
	.long	1027693874
	.long	3958898688
	.long	1063319229
	.long	3585544732
	.long	1028203366
	.long	4208984064
	.long	1063413919
	.long	395326031
	.long	1026777160
	.long	2644508672
	.long	1063508621
	.long	3080139365
	.long	1026686561
	.long	3572498432
	.long	1063603334
	.long	3213016626
	.long	1028477442
	.long	2711093248
	.long	1063698059
	.long	439612993
	.long	1027235725
	.long	71303168
	.long	1063792796
	.long	847494235
	.long	1028613481
	.long	4256169984
	.long	1063887543
	.long	1915821278
	.long	1028326816
	.long	2392850432
	.long	1063982303
	.long	2225307780
	.long	1026401230
	.long	3082813440
	.long	1064077074
	.long	3438929084
	.long	1027103624
	.long	2043674624
	.long	1064171857
	.long	2082386238
	.long	1027684439
	.long	3582459904
	.long	1064266651
	.long	1897021122
	.long	1028567773
	.long	3855876096
	.long	1064333048
	.long	1828790394
	.long	1028459899
	.long	2926837760
	.long	1064380457
	.long	4113329424
	.long	1027931731
	.long	1157627904
	.long	1064427872
	.long	1702728849
	.long	1027539743
	.long	2849243136
	.long	1064475292
	.long	981368224
	.long	1027987814
	.long	3713007616
	.long	1064522718
	.long	3588215231
	.long	1027706406
	.long	3754950656
	.long	1064570150
	.long	2599429676
	.long	1027136642
	.long	2981101568
	.long	1064617588
	.long	3033621989
	.long	1027640910
	.long	1397751808
	.long	1064665032
	.long	1866428510
	.long	1026593332
	.long	3305635840
	.long	1064712481
	.long	3898020334
	.long	1028538281
	.long	121372672
	.long	1064759937
	.long	2418247211
	.long	1028479303
	.long	440926208
	.long	1064807398
	.long	2151876465
	.long	1027654537
	.long	4270325760
	.long	1064854864
	.long	638259760
	.long	1025479387
	.long	3025666048
	.long	1064902337
	.long	3300197061
	.long	1024786488
	.long	1007943680
	.long	1064949816
	.long	2066224929
	.long	1028181309
	.long	2518679552
	.long	1064997300
	.long	1194091706
	.long	1027784991
	.long	3268673536
	.long	1065044790
	.long	3941768658
	.long	1028458821
	.long	3264479232
	.long	1065092286
	.long	2285092471
	.long	1025632049
	.long	2511601664
	.long	1065139788
	.long	274659519
	.long	1028009304
	.long	1016594432
	.long	1065187296
	.long	2660244528
	.long	1028621601
	.long	3080716288
	.long	1065234809
	.long	2948866684
	.long	1027363323
	.long	119799808
	.long	1065282329
	.long	2305553338
	.long	1027606374
	.long	730071040
	.long	1065329854
	.long	1656362908
	.long	1027822979
	.long	2458910720
	.long	1065365300
	.long	1598786858
	.long	1026178251
	.long	2587623424
	.long	1065383126
	.long	2378958549
	.long	1028197240
	.long	839778304
	.long	1065406897
	.long	1456429431
	.long	1026244901
	.long	3036413952
	.long	1065430670
	.long	1932205944
	.long	1027981879
	.long	590872576
	.long	1065454447
	.long	4023608769
	.long	1027958429
	.long	2096103424
	.long	1065478226
	.long	3732079947
	.long	1027967416
	.long	3260284928
	.long	1065502008
	.long	336608668
	.long	1026095801
	.long	4086300672
	.long	1065525793
	.long	136967562
	.long	1028120471
	.long	282460160
	.long	1065549582
	.long	3624814115
	.long	1028328839
	.long	441843712
	.long	1065573373
	.long	3566723455
	.long	1022759747
	.long	272236544
	.long	1065597167
	.long	4015454571
	.long	1028257780
	.long	4072013824
	.long	1065620963
	.long	4162319098
	.long	1028151765
	.long	3254255616
	.long	1065644763
	.long	3888083220
	.long	1027286836
	.long	2116943872
	.long	1065668566
	.long	1025436577
	.long	1027036048
	.long	663224320
	.long	1065692372
	.long	2834872887
	.long	1025744984
	.long	3191078912
	.long	1065716180
	.long	1809631811
	.long	1027064556
	.long	1113718784
	.long	1065739992
	.long	1168962244
	.long	1027829789
	.long	3024224256
	.long	1065763806
	.long	2016586535
	.long	1027435289
	.long	335675392
	.long	1065787624
	.long	1431812300
	.long	1027676827
	.long	1836580864
	.long	3212249540
	.long	1456335140
	.long	3175441338
	.long	2446065664
	.long	3212225994
	.long	1539749707
	.long	3174539680
	.long	1201405952
	.long	3212202447
	.long	1821058149
	.long	3175488506
	.long	2397044736
	.long	3212178898
	.long	3567155664
	.long	3175677071
	.long	1737228288
	.long	3212155348
	.long	3350760572
	.long	3175687587
	.long	3516137472
	.long	3212131796
	.long	54506174
	.long	3175826950
	.long	3438280704
	.long	3212108243
	.long	1906648915
	.long	3172999433
	.long	1502347264
	.long	3212084689
	.long	2084064274
	.long	3175617184
	.long	2003042304
	.long	3212061133
	.long	2608241578
	.long	3175876387
	.long	644612096
	.long	3212037576
	.long	1051263361
	.long	3175383946
	.long	1721237504
	.long	3212014017
	.long	3354885735
	.long	3173549367
	.long	936902656
	.long	3211990457
	.long	87463564
	.long	3175445923
	.long	2586312704
	.long	3211966895
	.long	506361461
	.long	3173795297
	.long	2373189632
	.long	3211943332
	.long	3280686310
	.long	3176104310
	.long	297533440
	.long	3211919768
	.long	3617245347
	.long	3172626443
	.long	652738560
	.long	3211896202
	.long	579141699
	.long	3175545653
	.long	3438542848
	.long	3211872634
	.long	3721652079
	.long	3176073447
	.long	64225280
	.long	3211849066
	.long	4212107268
	.long	3176133288
	.long	3413901312
	.long	3211825495
	.long	2932346116
	.long	3176017856
	.long	601882624
	.long	3211801924
	.long	3484155727
	.long	3176018735
	.long	435159040
	.long	3211768414
	.long	1225556500
	.long	3173101246
	.long	224395264
	.long	3211721265
	.long	1151472644
	.long	3175433137
	.long	570949632
	.long	3211674113
	.long	169440649
	.long	3175424350
	.long	1473249280
	.long	3211626958
	.long	767490525
	.long	3174092077
	.long	2929197056
	.long	3211579800
	.long	3954462340
	.long	3175313650
	.long	642777088
	.long	3211532640
	.long	293664658
	.long	3175782850
	.long	3202875392
	.long	3211485476
	.long	2810753961
	.long	3172053070
	.long	2016935936
	.long	3211438310
	.long	68049842
	.long	3174903053
	.long	1379401728
	.long	3211391141
	.long	2125367929
	.long	3172998826
	.long	1288175616
	.long	3211343969
	.long	2899939163
	.long	3173729914
	.long	1741684736
	.long	3211296794
	.long	3555933291
	.long	3175823424
	.long	2739404800
	.long	3211249616
	.long	3666032509
	.long	3173379218
	.long	4278714368
	.long	3211202435
	.long	836003693
	.long	3174279974
	.long	2063597568
	.long	3211155252
	.long	1236704766
	.long	3174628627
	.long	387448832
	.long	3211108066
	.long	2756688253
	.long	3175196858
	.long	3543662592
	.long	3211060876
	.long	3160170483
	.long	3175973516
	.long	2941255680
	.long	3211013684
	.long	2830353174
	.long	3175364122
	.long	2873098240
	.long	3210966489
	.long	4144291481
	.long	3175737650
	.long	3338141696
	.long	3210919291
	.long	3260331491
	.long	3175267671
	.long	39845888
	.long	3210872091
	.long	1922313952
	.long	3172367859
	.long	1566048256
	.long	3210824887
	.long	3310171853
	.long	3174855326
	.long	3620732928
	.long	3210777680
	.long	3325808113
	.long	3175681701
	.long	3815768064
	.long	3210721230
	.long	3926816425
	.long	3173875772
	.long	1439694848
	.long	3210626806
	.long	2528441557
	.long	3175954827
	.long	112197632
	.long	3210532376
	.long	2735049661
	.long	3176047308
	.long	4125097984
	.long	3210437939
	.long	2814107016
	.long	3175038571
	.long	589299712
	.long	3210343498
	.long	84420116
	.long	3175362012
	.long	2387607552
	.long	3210249050
	.long	886636141
	.long	3175106949
	.long	926941184
	.long	3210154597
	.long	4249864732
	.long	3174015648
	.long	498073600
	.long	3210060138
	.long	2571744411
	.long	3176070508
	.long	1099956224
	.long	3209965673
	.long	191977345
	.long	3175708074
	.long	2728394752
	.long	3209871202
	.long	1836848871
	.long	3175824104
	.long	1086324736
	.long	3209776726
	.long	3259464767
	.long	3174065342
	.long	929038336
	.long	3209673352
	.long	885961784
	.long	3173053400
	.long	1719664640
	.long	3209484376
	.long	3633085090
	.long	3175298882
	.long	245366784
	.long	3209295389
	.long	622998640
	.long	3175676299
	.long	794820608
	.long	3209106390
	.long	183843539
	.long	3175700419
	.long	3361734656
	.long	3208917379
	.long	319142191
	.long	3175621005
	.long	3644850176
	.long	3208728357
	.long	4064393657
	.long	3175686713
	.long	3279945728
	.long	3208436088
	.long	921315957
	.long	3167773730
	.long	3263168512
	.long	3208057998
	.long	1191737347
	.long	3175145444
	.long	2940207104
	.long	3207679885
	.long	676306098
	.long	3174972367
	.long	293601280
	.long	3207009515
	.long	3713153157
	.long	3176043736
	.long	989855744
	.long	3205960985
	.long	847981310
	.long	3172455261
	.long	0
	.long	0
	.long	0
	.long	0
	.type	log2_t2,@object
	.size	log2_t2,2064
	.align 16
log2_t3:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3221225472
	.long	1052246016
	.long	10217817
	.long	1028072176
	.long	2684354560
	.long	1053294593
	.long	1823923246
	.long	1027991127
	.long	2684354560
	.long	1053884417
	.long	2844515178
	.long	1028130658
	.long	0
	.long	1054277635
	.long	1230948249
	.long	1025710668
	.long	2952790016
	.long	1054670852
	.long	1764281763
	.long	1028204164
	.long	1744830464
	.long	1054965763
	.long	2682119783
	.long	1027942224
	.long	1879048192
	.long	1055129604
	.long	690677466
	.long	1027931089
	.long	3623878656
	.long	1055326213
	.long	2988899587
	.long	1028585756
	.long	2013265920
	.long	1055522823
	.long	2646883118
	.long	1027643276
	.long	1207959552
	.long	1055719433
	.long	3152640337
	.long	1026797173
	.long	4026531840
	.long	1055883274
	.long	2707659781
	.long	1026392505
	.long	2348810240
	.long	1055997958
	.long	2715649106
	.long	1027422884
	.long	3087007744
	.long	1056096263
	.long	285997833
	.long	1028488126
	.long	0
	.long	1056194569
	.long	2595623046
	.long	1027865547
	.long	1610612736
	.long	1056292874
	.long	3757543522
	.long	1027743124
	.long	2550136832
	.long	1056374795
	.long	1685325986
	.long	1027848425
	.long	603979776
	.long	1056473101
	.long	971111892
	.long	1028643022
	.long	3422552064
	.long	1056571406
	.long	1310768194
	.long	1027840696
	.long	2348810240
	.long	1056669712
	.long	647785154
	.long	1027472771
	.long	335544320
	.long	1056751634
	.long	3447719136
	.long	1027384110
	.long	0
	.long	1056849940
	.long	2473353221
	.long	1028109426
	.long	134217728
	.long	1056948246
	.long	3908013601
	.long	1026650988
	.long	335544320
	.long	1057005580
	.long	3241725099
	.long	1023809487
	.long	4261412864
	.long	1057046540
	.long	3592425610
	.long	1028549905
	.long	637534208
	.long	1057095694
	.long	1951774861
	.long	1025790338
	.long	1476395008
	.long	1057144847
	.long	2386717008
	.long	1027825275
	.long	2550136832
	.long	1057194000
	.long	936867399
	.long	1026725625
	.long	3825205248
	.long	1057243153
	.long	3781996145
	.long	1026256374
	.long	33554432
	.long	1057284115
	.long	1781441569
	.long	1026554167
	.long	1677721600
	.long	1057333268
	.long	541855954
	.long	1027837629
	.long	3556769792
	.long	1057382421
	.long	3808194027
	.long	1026230743
	.long	1342177280
	.long	1057431575
	.long	1679773493
	.long	1024039205
	.long	2516582400
	.long	1057472536
	.long	4034037987
	.long	1022229492
	.long	671088640
	.long	1057521690
	.long	1732823594
	.long	1026728033
	.long	3321888768
	.long	1057570843
	.long	2629452715
	.long	1028246558
	.long	1912602624
	.long	1057619997
	.long	2171860443
	.long	1027730387
	.long	704643072
	.long	1057669151
	.long	1413946002
	.long	1027714921
	.long	2717908992
	.long	1057710112
	.long	3583986569
	.long	1027909771
	.long	1912602624
	.long	1057759266
	.long	2993702118
	.long	1024684384
	.long	1275068416
	.long	1057808420
	.long	3380540156
	.long	1028118518
	.long	872415232
	.long	1057857574
	.long	3787132338
	.long	1027925509
	.long	3556769792
	.long	1057898535
	.long	399829967
	.long	1027972484
	.long	3556769792
	.long	1057947689
	.long	99635239
	.long	1022741119
	.long	3724541952
	.long	1057996843
	.long	3258250598
	.long	1027826609
	.long	4211081216
	.long	1058029590
	.long	3506657137
	.long	1027308398
	.long	3741319168
	.long	1058050071
	.long	541288431
	.long	1027107230
	.long	4127195136
	.long	1058074648
	.long	3646427416
	.long	1027904075
	.long	335544320
	.long	1058099226
	.long	246496071
	.long	1025659624
	.long	922746880
	.long	1058123803
	.long	2177775776
	.long	1028406323
	.long	1627389952
	.long	1058148380
	.long	1181919501
	.long	1028360402
	.long	1577058304
	.long	1058168861
	.long	1426415599
	.long	1028530121
	.long	2483027968
	.long	1058193438
	.long	1417624514
	.long	1027006858
	.long	3489660928
	.long	1058218015
	.long	3323042516
	.long	1021955058
	.long	285212672
	.long	1058242593
	.long	710383729
	.long	1028457251
	.long	570425344
	.long	1058263074
	.long	179923617
	.long	1028480130
	.long	1879048192
	.long	1058287651
	.long	1513572732
	.long	1026553379
	.long	3271557120
	.long	1058312228
	.long	3860576606
	.long	1028277345
	.long	486539264
	.long	1058336806
	.long	742310640
	.long	1027879207
	.long	1107296256
	.long	1058357287
	.long	2654634259
	.long	1027755563
	.long	2801795072
	.long	1058381864
	.long	2910466432
	.long	1028276410
	.long	318767104
	.long	1058406442
	.long	4054947979
	.long	1026798334
	.long	2231369728
	.long	1058431019
	.long	878023543
	.long	1023510847
	.long	4227858432
	.long	1058455596
	.long	263450436
	.long	1028652562
	.long	989855744
	.long	1058476078
	.long	3244478756
	.long	1024589954
	.long	3187671040
	.long	1058500655
	.long	1529358695
	.long	1027496060
	.long	1207959552
	.long	1058525233
	.long	1449606828
	.long	1025374457
	.long	3607101440
	.long	1058549810
	.long	1121865491
	.long	1028651213
	.long	704643072
	.long	1058570292
	.long	76632162
	.long	1015281729
	.long	3305111552
	.long	1058594869
	.long	1705840257
	.long	1026852007
	.long	1711276032
	.long	1058619447
	.long	2189122428
	.long	1028401790
	.long	234881024
	.long	1058644025
	.long	3860440574
	.long	1027979737
	.long	1946157056
	.long	1058664506
	.long	3805699399
	.long	1027836413
	.long	654311424
	.long	1058689084
	.long	3144514508
	.long	1028333941
	.long	3774873600
	.long	1058713661
	.long	1551239808
	.long	1026867407
	.long	2701131776
	.long	1058738239
	.long	3872647589
	.long	1023700698
	.long	1711276032
	.long	1058762817
	.long	2939649020
	.long	1028642081
	.long	3858759680
	.long	1058783298
	.long	3869973501
	.long	1024259487
	.long	3070230528
	.long	1058807876
	.long	3430235573
	.long	1027394848
	.long	2399141888
	.long	1058832454
	.long	2837866956
	.long	1024798194
	.long	1811939328
	.long	1058857032
	.long	3195008471
	.long	1028558077
	.long	4278190080
	.long	1058877513
	.long	925127753
	.long	1028542902
	.long	3909091328
	.long	1058902091
	.long	3453656528
	.long	1026590044
	.long	3623878656
	.long	1058926669
	.long	2547020848
	.long	1028250625
	.long	3456106496
	.long	1058951247
	.long	3173507594
	.long	1027808713
	.long	1962934272
	.long	1058971729
	.long	624230052
	.long	1027649088
	.long	1979711488
	.long	1058996307
	.long	1896525098
	.long	1028127351
	.long	2113929216
	.long	1059020885
	.long	3776127051
	.long	1026276788
	.long	2332033024
	.long	1059045463
	.long	1850790470
	.long	1028492603
	.long	3481272320
	.long	1059065900
	.long	111238633
	.long	1028379644
	.long	2944401408
	.long	1059076141
	.long	1234093769
	.long	1028494241
	.long	3212836864
	.long	1059088430
	.long	4181043184
	.long	1026804589
	.long	3523215360
	.long	1059100719
	.long	2569064410
	.long	1028514056
	.long	3892314112
	.long	1059113008
	.long	312914348
	.long	1028228528
	.long	3523215360
	.long	1059123249
	.long	3115983504
	.long	1028199393
	.long	3992977408
	.long	1059135538
	.long	2038492604
	.long	1024861349
	.long	209715200
	.long	1059147828
	.long	152199155
	.long	1027874535
	.long	780140544
	.long	1059160117
	.long	1978960733
	.long	1027229157
	.long	578813952
	.long	1059170358
	.long	505645676
	.long	1026884061
	.long	1241513984
	.long	1059182647
	.long	514239020
	.long	1027707324
	.long	1962934272
	.long	1059194936
	.long	2032269232
	.long	1021518627
	.long	2726297600
	.long	1059207225
	.long	3497533428
	.long	1028043129
	.long	3548381184
	.long	1059219514
	.long	3658695522
	.long	1027915933
	.long	3556769792
	.long	1059229755
	.long	501547381
	.long	1028018915
	.long	184549376
	.long	1059242045
	.long	3705047602
	.long	1024687479
	.long	1149239296
	.long	1059254334
	.long	675335170
	.long	1028011771
	.long	2172649472
	.long	1059266623
	.long	2543593460
	.long	1027713249
	.long	2348810240
	.long	1059276864
	.long	2216047412
	.long	1027673535
	.long	3464495104
	.long	1059289153
	.long	712855157
	.long	1028296196
	.long	343932928
	.long	1059301443
	.long	930266665
	.long	1027043929
	.long	1568669696
	.long	1059313732
	.long	780927773
	.long	1025654091
	.long	1912602624
	.long	1059323973
	.long	3399331078
	.long	1024229490
	.long	3229614080
	.long	1059336262
	.long	3996204339
	.long	1026644611
	.long	301989888
	.long	1059348552
	.long	143359080
	.long	1028079044
	.long	1728053248
	.long	1059360841
	.long	2849827553
	.long	1027273550
	.long	3204448256
	.long	1059373130
	.long	2690987141
	.long	1026998795
	.long	3758096384
	.long	1059383371
	.long	2222711807
	.long	1027188284
	.long	1031798784
	.long	1059395661
	.long	1353013621
	.long	1028180599
	.long	2659188736
	.long	1059407950
	.long	756253115
	.long	1027136298
	.long	41943040
	.long	1059420240
	.long	532576643
	.long	1026486854
	.long	763363328
	.long	1059430481
	.long	3289135452
	.long	1026299195
	.long	2533359616
	.long	1059442770
	.long	1513734706
	.long	1027630332
	.long	67108864
	.long	1059455060
	.long	2085312894
	.long	1023872287
	.long	1937768448
	.long	1059467349
	.long	3183199319
	.long	1028270313
	.long	2826960896
	.long	1059477590
	.long	3972158195
	.long	1028082057
	.long	503316480
	.long	1059489880
	.long	264248663
	.long	1028526857
	.long	2533359616
	.long	1059502169
	.long	3362227203
	.long	1027150375
	.long	318767104
	.long	1059514459
	.long	4064547853
	.long	1024810530
	.long	268435456
	.long	3205959634
	.long	691182318
	.long	3173304996
	.long	3456106496
	.long	3205947346
	.long	2237848631
	.long	3175922029
	.long	1291845632
	.long	3205939155
	.long	2610806712
	.long	3173520353
	.long	150994944
	.long	3205926868
	.long	1392581829
	.long	3174351268
	.long	3271557120
	.long	3205914580
	.long	3292400060
	.long	3175929370
	.long	2080374784
	.long	3205902293
	.long	4178356410
	.long	3175965902
	.long	872415232
	.long	3205890006
	.long	330416743
	.long	3174570455
	.long	3925868544
	.long	3205877718
	.long	1897771058
	.long	3174145242
	.long	2650800128
	.long	3205865431
	.long	3340394865
	.long	3175328080
	.long	1358954496
	.long	3205853144
	.long	4006478009
	.long	3174644517
	.long	33554432
	.long	3205840857
	.long	2338318098
	.long	3175328560
	.long	1996488704
	.long	3205832665
	.long	908420916
	.long	3175917031
	.long	637534208
	.long	3205820378
	.long	1000116813
	.long	3175123051
	.long	3539992576
	.long	3205808090
	.long	4281444920
	.long	3175253057
	.long	2130706432
	.long	3205795803
	.long	376816689
	.long	3172256578
	.long	687865856
	.long	3205783516
	.long	196000653
	.long	3170433881
	.long	3506438144
	.long	3205771228
	.long	2744030195
	.long	3174702892
	.long	2013265920
	.long	3205758941
	.long	2211786449
	.long	3173892702
	.long	486539264
	.long	3205746654
	.long	4020273651
	.long	3175077302
	.long	2315255808
	.long	3205738462
	.long	3572678407
	.long	3175733405
	.long	754974720
	.long	3205726175
	.long	1564087220
	.long	3174977341
	.long	3456106496
	.long	3205713887
	.long	1784641315
	.long	3175256025
	.long	1845493760
	.long	3205701600
	.long	679907493
	.long	3173008683
	.long	201326592
	.long	3205689313
	.long	2119625261
	.long	3172818812
	.long	2818572288
	.long	3205677025
	.long	2101485035
	.long	3175178180
	.long	1124073472
	.long	3205664738
	.long	2832998731
	.long	3174717836
	.long	3690987520
	.long	3205652450
	.long	3610578708
	.long	3175551729
	.long	1946157056
	.long	3205640163
	.long	2398766049
	.long	3174966407
	.long	3623878656
	.long	3205631971
	.long	3995335152
	.long	3175656959
	.long	1828716544
	.long	3205619684
	.long	183250326
	.long	3175973344
	.long	16777216
	.long	3205607397
	.long	4019803825
	.long	3175116509
	.long	2466250752
	.long	3205595109
	.long	1578128721
	.long	3175183606
	.long	603979776
	.long	3205582822
	.long	112661265
	.long	3170010307
	.long	2986344448
	.long	3205570534
	.long	3756900035
	.long	3175992425
	.long	1073741824
	.long	3205558247
	.long	3996691019
	.long	3174185852
	.long	3422552064
	.long	3205545959
	.long	4247306560
	.long	3172221188
	.long	1442840576
	.long	3205533672
	.long	920037013
	.long	3174307610
	.long	2969567232
	.long	3205525480
	.long	2901018317
	.long	3175306400
	.long	956301312
	.long	3205513193
	.long	4209407270
	.long	3173953969
	.long	3204448256
	.long	3205500905
	.long	4232478211
	.long	3174316659
	.long	2248146944
	.long	3205480404
	.long	3976793802
	.long	3175786245
	.long	2348810240
	.long	3205455829
	.long	223141518
	.long	3175696845
	.long	2415919104
	.long	3205431254
	.long	2202058045
	.long	3173520944
	.long	2415919104
	.long	3205406679
	.long	780543142
	.long	3170584573
	.long	2348810240
	.long	3205382104
	.long	1379108477
	.long	3174272970
	.long	2248146944
	.long	3205357529
	.long	875461665
	.long	3170488084
	.long	704643072
	.long	3205341146
	.long	2105849568
	.long	3174271106
	.long	503316480
	.long	3205316571
	.long	1835672135
	.long	3174776330
	.long	234881024
	.long	3205291996
	.long	3252628102
	.long	3176108718
	.long	4227858432
	.long	3205267420
	.long	2720009520
	.long	3176111930
	.long	3892314112
	.long	3205242845
	.long	850238367
	.long	3174795595
	.long	3489660928
	.long	3205218270
	.long	3536085073
	.long	3174303192
	.long	3019898880
	.long	3205193695
	.long	3558909717
	.long	3175373323
	.long	2516582400
	.long	3205169120
	.long	920693007
	.long	3174667265
	.long	704643072
	.long	3205152737
	.long	4071589563
	.long	3175526566
	.long	100663296
	.long	3205128162
	.long	3392361480
	.long	3175871650
	.long	3758096384
	.long	3205103586
	.long	2972601747
	.long	3174998742
	.long	3053453312
	.long	3205079011
	.long	1605892160
	.long	3175139069
	.long	2315255808
	.long	3205054436
	.long	1553740054
	.long	3169146216
	.long	1476395008
	.long	3205029861
	.long	3751995331
	.long	3176004720
	.long	637534208
	.long	3205005286
	.long	3091908485
	.long	3174267031
	.long	4026531840
	.long	3204980710
	.long	3333214935
	.long	3172771610
	.long	3053453312
	.long	3204956135
	.long	790487160
	.long	3174501480
	.long	939524096
	.long	3204939752
	.long	1992793420
	.long	3175422045
	.long	4194304000
	.long	3204915176
	.long	3717748377
	.long	3174284044
	.long	3087007744
	.long	3204890601
	.long	105034497
	.long	3174659776
	.long	1912602624
	.long	3204866026
	.long	4120994791
	.long	3175985638
	.long	704643072
	.long	3204841451
	.long	136721814
	.long	3175923992
	.long	3758096384
	.long	3204816875
	.long	4178760049
	.long	3174289887
	.long	2449473536
	.long	3204792300
	.long	1385241146
	.long	3173295534
	.long	1073741824
	.long	3204767725
	.long	614436572
	.long	3174892815
	.long	3959422976
	.long	3204743149
	.long	3717352206
	.long	3173503234
	.long	1543503872
	.long	3204726766
	.long	2074792065
	.long	3174982293
	.long	33554432
	.long	3204702191
	.long	1160049312
	.long	3175315117
	.long	2785017856
	.long	3204677615
	.long	947382601
	.long	3173470865
	.long	1174405120
	.long	3204653040
	.long	1784674375
	.long	3173592242
	.long	3791650816
	.long	3204628464
	.long	3966380974
	.long	3175406146
	.long	2080374784
	.long	3204603889
	.long	90923019
	.long	3175186916
	.long	301989888
	.long	3204579314
	.long	2809276197
	.long	3175891468
	.long	2785017856
	.long	3204554738
	.long	2488581129
	.long	3175422649
	.long	100663296
	.long	3204538355
	.long	4207284753
	.long	3176089346
	.long	2516582400
	.long	3204513779
	.long	1763582947
	.long	3173239999
	.long	536870912
	.long	3204489204
	.long	1591642683
	.long	3175659480
	.long	2818572288
	.long	3204464628
	.long	1247815734
	.long	3175781625
	.long	1543503872
	.long	3204431850
	.long	3175237814
	.long	3174372638
	.long	1610612736
	.long	3204382699
	.long	3136020064
	.long	3174117678
	.long	1543503872
	.long	3204333548
	.long	145926225
	.long	3175399182
	.long	1409286144
	.long	3204284397
	.long	1146502365
	.long	3174956000
	.long	1140850688
	.long	3204235246
	.long	1806880589
	.long	3175568698
	.long	3825205248
	.long	3204202478
	.long	748557588
	.long	3171070759
	.long	3355443200
	.long	3204153327
	.long	3616893112
	.long	3175503309
	.long	2818572288
	.long	3204104176
	.long	3332544853
	.long	3175717064
	.long	2214592512
	.long	3204055025
	.long	59215988
	.long	3174426688
	.long	1476395008
	.long	3204005874
	.long	2509632280
	.long	3174354848
	.long	603979776
	.long	3203956723
	.long	1669980294
	.long	3175609302
	.long	3959422976
	.long	3203907571
	.long	231858041
	.long	3175323695
	.long	2885681152
	.long	3203858420
	.long	192153543
	.long	3175961815
	.long	1744830464
	.long	3203809269
	.long	507582953
	.long	3175426507
	.long	3758096384
	.long	3203776501
	.long	1672537466
	.long	3176048847
	.long	2483027968
	.long	3203727350
	.long	3522791035
	.long	3172632365
	.long	1006632960
	.long	3203678199
	.long	2149811783
	.long	3175485759
	.long	3758096384
	.long	3203629047
	.long	1999469990
	.long	3175541209
	.long	2147483648
	.long	3203579896
	.long	2226222860
	.long	3173477057
	.long	402653184
	.long	3203530745
	.long	4286112849
	.long	3172408839
	.long	2818572288
	.long	3203481593
	.long	713604598
	.long	3174828560
	.long	872415232
	.long	3203432442
	.long	2347361448
	.long	3173841417
	.long	1879048192
	.long	3203366901
	.long	1438571646
	.long	3174899801
	.long	402653184
	.long	3203301366
	.long	3419522004
	.long	3175593882
	.long	268435456
	.long	3203203063
	.long	4288382338
	.long	3174545520
	.long	4160749568
	.long	3203104759
	.long	272162157
	.long	3174838779
	.long	3489660928
	.long	3203006456
	.long	2794103335
	.long	3176033766
	.long	2684354560
	.long	3202908153
	.long	2632246937
	.long	3175930608
	.long	1744830464
	.long	3202809850
	.long	1508829515
	.long	3174219824
	.long	536870912
	.long	3202711547
	.long	3295780812
	.long	3172985552
	.long	3355443200
	.long	3202613243
	.long	1122596525
	.long	3174655334
	.long	805306368
	.long	3202547708
	.long	2645694757
	.long	3175532338
	.long	3355443200
	.long	3202449404
	.long	1159177678
	.long	3174604459
	.long	2684354560
	.long	3202351098
	.long	1034248407
	.long	3175079694
	.long	2684354560
	.long	3202154491
	.long	1158806475
	.long	3171580989
	.long	2147483648
	.long	3201957884
	.long	1293148021
	.long	3171385480
	.long	1073741824
	.long	3201761277
	.long	3828985497
	.long	3175006371
	.long	4026531840
	.long	3201564669
	.long	3153776129
	.long	3174482233
	.long	2147483648
	.long	3201368062
	.long	171710315
	.long	3175446758
	.long	0
	.long	3201040382
	.long	1900250098
	.long	3174781524
	.long	2147483648
	.long	3200778238
	.long	2246244539
	.long	3175572705
	.long	536870912
	.long	3200385023
	.long	3628536486
	.long	3175901120
	.long	1073741824
	.long	3199729663
	.long	231762015
	.long	3175023909
	.long	2147483648
	.long	3198681087
	.long	1401818636
	.long	3175134547
	.long	0
	.long	0
	.long	0
	.long	0
	.type	log2_t3,@object
	.size	log2_t3,4112
	.space 496, 0x00 	# pad
	.align 16
clv:
	.long	1655945238
	.long	1069842388
	.long	2523158510
	.long	1048167334
	.long	713798755
	.long	3216330823
	.long	1181303047
	.long	3218484803
	.type	clv,@object
	.size	clv,32
	.align 16
exp2_tbl:
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	2851812149
	.long	1072698941
	.long	2595802551
	.long	1016815913
	.long	1048019041
	.long	1072704666
	.long	1398474845
	.long	3161559171
	.long	3899555717
	.long	1072710421
	.long	427280750
	.long	3163595548
	.long	3541402996
	.long	1072716208
	.long	2759177317
	.long	1015903202
	.long	702412510
	.long	1072722027
	.long	3803266086
	.long	3163328991
	.long	410360776
	.long	1072727877
	.long	1269990655
	.long	1013024446
	.long	3402036099
	.long	1072733758
	.long	405889333
	.long	1016154232
	.long	1828292879
	.long	1072739672
	.long	1255956746
	.long	1016636974
	.long	728909815
	.long	1072745618
	.long	383930225
	.long	1016078044
	.long	852742562
	.long	1072751596
	.long	667253586
	.long	1010842135
	.long	2952712987
	.long	1072757606
	.long	3293494651
	.long	3161168877
	.long	3490863953
	.long	1072763649
	.long	960797497
	.long	3163997456
	.long	3228316108
	.long	1072769725
	.long	3010241991
	.long	3159471380
	.long	2930322912
	.long	1072775834
	.long	2599499422
	.long	3163762623
	.long	3366293073
	.long	1072781976
	.long	3119426313
	.long	1015169130
	.long	1014845819
	.long	1072788152
	.long	3117910645
	.long	3162607681
	.long	948735466
	.long	1072794361
	.long	3516338027
	.long	3163623459
	.long	3949972341
	.long	1072800603
	.long	2068408548
	.long	1015962444
	.long	2214878420
	.long	1072806880
	.long	892270087
	.long	3164164998
	.long	828946858
	.long	1072813191
	.long	10642492
	.long	1016988014
	.long	586995997
	.long	1072819536
	.long	41662347
	.long	3163676568
	.long	2288159958
	.long	1072825915
	.long	2169144468
	.long	1015924597
	.long	2440944790
	.long	1072832329
	.long	2492769773
	.long	1015196030
	.long	1853186616
	.long	1072838778
	.long	3066496370
	.long	1016705150
	.long	1337108031
	.long	1072845262
	.long	3203724452
	.long	1015726421
	.long	1709341917
	.long	1072851781
	.long	2571168217
	.long	1015201075
	.long	3790955393
	.long	1072858335
	.long	2352942461
	.long	3164228666
	.long	4112506593
	.long	1072864925
	.long	2947355221
	.long	1015419624
	.long	3504003472
	.long	1072871551
	.long	3594001059
	.long	3158379228
	.long	2799960843
	.long	1072878213
	.long	1423655380
	.long	1016070727
	.long	2839424854
	.long	1072884911
	.long	1171596163
	.long	1014090255
	.long	171030293
	.long	1072891646
	.long	3526460132
	.long	1015477354
	.long	4232894513
	.long	1072898416
	.long	2383938684
	.long	1015717095
	.long	2992903935
	.long	1072905224
	.long	2218154405
	.long	1016276769
	.long	1603444721
	.long	1072912069
	.long	1548633640
	.long	3163249902
	.long	926591435
	.long	1072918951
	.long	3208833761
	.long	3163962090
	.long	1829099622
	.long	1072925870
	.long	1016661180
	.long	3164509581
	.long	887463927
	.long	1072932827
	.long	3596744162
	.long	3161842742
	.long	3272845541
	.long	1072939821
	.long	928852419
	.long	3164536824
	.long	1276261410
	.long	1072946854
	.long	300981947
	.long	1015732745
	.long	78413852
	.long	1072953925
	.long	4183226867
	.long	3164065827
	.long	569847338
	.long	1072961034
	.long	472945272
	.long	3160339305
	.long	3645941911
	.long	1072968181
	.long	3814685080
	.long	3162621917
	.long	1617004845
	.long	1072975368
	.long	82804943
	.long	1011391354
	.long	3978100823
	.long	1072982593
	.long	3513027190
	.long	1016894539
	.long	3049340112
	.long	1072989858
	.long	3062915824
	.long	1014219171
	.long	4040676318
	.long	1072997162
	.long	4090609238
	.long	1016712034
	.long	3577096743
	.long	1073004506
	.long	2951496418
	.long	1014842263
	.long	2583551245
	.long	1073011890
	.long	3161094195
	.long	1016655067
	.long	1990012071
	.long	1073019314
	.long	3529070563
	.long	3163861769
	.long	2731501122
	.long	1073026778
	.long	1774031854
	.long	3163518597
	.long	1453150082
	.long	1073034283
	.long	498154668
	.long	3162536638
	.long	3395129871
	.long	1073041828
	.long	4025345434
	.long	3163383964
	.long	917841882
	.long	1073049415
	.long	18715564
	.long	1016707884
	.long	3566716925
	.long	1073057042
	.long	1536826855
	.long	1015191009
	.long	3712504873
	.long	1073064711
	.long	88491948
	.long	1016476236
	.long	2321106615
	.long	1073072422
	.long	2171176610
	.long	1010584347
	.long	363667784
	.long	1073080175
	.long	813753949
	.long	1016833785
	.long	3111574537
	.long	1073087969
	.long	2606161479
	.long	3163808322
	.long	2956612997
	.long	1073095806
	.long	2118169750
	.long	3163784129
	.long	885834528
	.long	1073103686
	.long	1973258546
	.long	3163310140
	.long	2186617381
	.long	1073111608
	.long	2270764083
	.long	3164321289
	.long	3561793907
	.long	1073119573
	.long	1157054052
	.long	1012938926
	.long	1719614413
	.long	1073127582
	.long	330458197
	.long	3164331316
	.long	1963711167
	.long	1073135634
	.long	1744767756
	.long	3161622870
	.long	1013258799
	.long	1073143730
	.long	1748797610
	.long	3161177658
	.long	4182873220
	.long	1073151869
	.long	629542646
	.long	3163044879
	.long	3907805044
	.long	1073160053
	.long	2257091225
	.long	3162598983
	.long	1218806132
	.long	1073168282
	.long	1818613051
	.long	3163597017
	.long	1447192521
	.long	1073176555
	.long	1462857171
	.long	3163563097
	.long	1339972927
	.long	1073184873
	.long	167908908
	.long	1016620728
	.long	1944781191
	.long	1073193236
	.long	3993278767
	.long	3162772855
	.long	19972402
	.long	1073201645
	.long	3507899861
	.long	1017057868
	.long	919555682
	.long	1073210099
	.long	3121969534
	.long	1013996802
	.long	1413356050
	.long	1073218599
	.long	1651349290
	.long	3163716742
	.long	2571947539
	.long	1073227145
	.long	3558159063
	.long	3164425245
	.long	1176749997
	.long	1073235738
	.long	2738998779
	.long	3163084420
	.long	2604962541
	.long	1073244377
	.long	2614425274
	.long	3164587768
	.long	3649726105
	.long	1073253063
	.long	4085036346
	.long	1016698050
	.long	1110089947
	.long	1073261797
	.long	1451641638
	.long	1016523249
	.long	380978316
	.long	1073270578
	.long	854188970
	.long	3161511262
	.long	2568320822
	.long	1073279406
	.long	2732824428
	.long	1015401491
	.long	194117574
	.long	1073288283
	.long	777528611
	.long	3164460665
	.long	2966275557
	.long	1073297207
	.long	2176155323
	.long	3160891335
	.long	3418903055
	.long	1073306180
	.long	2527457337
	.long	3161869180
	.long	2682146384
	.long	1073315202
	.long	2082178512
	.long	3164411995
	.long	1892288442
	.long	1073324273
	.long	2446255666
	.long	3163648957
	.long	2191782032
	.long	1073333393
	.long	2960257726
	.long	1014791238
	.long	434316067
	.long	1073342563
	.long	2028358766
	.long	1014506698
	.long	2069751141
	.long	1073351782
	.long	1562170674
	.long	3163773257
	.long	3964284211
	.long	1073361051
	.long	2111583915
	.long	1016475740
	.long	2990417245
	.long	1073370371
	.long	3683467745
	.long	3164417902
	.long	321958744
	.long	1073379742
	.long	3401933766
	.long	1016843134
	.long	1434058175
	.long	1073389163
	.long	251133233
	.long	1016134345
	.long	3218338682
	.long	1073398635
	.long	3404164304
	.long	3163525684
	.long	2572866477
	.long	1073408159
	.long	878562433
	.long	1016570317
	.long	697153126
	.long	1073417735
	.long	1283515428
	.long	3164331765
	.long	3092190715
	.long	1073427362
	.long	814012167
	.long	3160571998
	.long	2380618042
	.long	1073437042
	.long	3149557219
	.long	3164369375
	.long	4076559943
	.long	1073446774
	.long	2119478330
	.long	3161806927
	.long	815859274
	.long	1073456560
	.long	240396590
	.long	3164536019
	.long	2420883922
	.long	1073466398
	.long	2049810052
	.long	1015168464
	.long	1540824585
	.long	1073476290
	.long	1064017010
	.long	3164536266
	.long	3716502172
	.long	1073486235
	.long	2303740125
	.long	1015091301
	.long	1610600570
	.long	1073496235
	.long	3766732298
	.long	1016808759
	.long	777507147
	.long	1073506289
	.long	4282924204
	.long	1016236109
	.long	2483480501
	.long	1073516397
	.long	1216371780
	.long	1014082748
	.long	3706687593
	.long	1073526560
	.long	3521726939
	.long	1014301643
	.long	1432208378
	.long	1073536779
	.long	1401068914
	.long	3163412539
	.long	1242007932
	.long	1073547053
	.long	1132034716
	.long	3164388407
	.long	135105010
	.long	1073557383
	.long	1906148727
	.long	3164424315
	.long	3707479175
	.long	1073567768
	.long	3613079302
	.long	1015213314
	.long	382305176
	.long	1073578211
	.long	2347622376
	.long	3163627201
	.long	64696965
	.long	1073588710
	.long	1768797490
	.long	1016865536
	.long	4076975200
	.long	1073599265
	.long	2029000898
	.long	1016257111
	.long	863738719
	.long	1073609879
	.long	1326992219
	.long	3163661773
	.long	351641897
	.long	1073620550
	.long	2172261526
	.long	3164059175
	.long	3884662774
	.long	1073631278
	.long	2158611599
	.long	1015258761
	.long	4224142467
	.long	1073642065
	.long	3389820385
	.long	1016255778
	.long	2728693978
	.long	1073652911
	.long	396109971
	.long	3164511267
	.long	764307441
	.long	1073663816
	.long	3021057420
	.long	3164378099
	.long	3999357479
	.long	1073674779
	.long	2258941616
	.long	1016973300
	.long	929806999
	.long	1073685803
	.long	3205336643
	.long	1016308133
	.long	1533953344
	.long	1073696886
	.long	769171850
	.long	1016714209
	.long	2912730644
	.long	1073708029
	.long	3490067721
	.long	3164453650
	.long	2174652632
	.long	1073719233
	.long	4087714590
	.long	1015498835
	.long	730821105
	.long	1073730498
	.long	2523232743
	.long	1013115764
	.type	exp2_tbl,@object
	.size	exp2_tbl,2048
	.align 16
cev:
	.long	3884607281
	.long	1062590591
	.long	3607404735
	.long	1068264200
	.long	1874480759
	.long	1065595563
	.long	4286760334
	.long	1070514109
	.long	4277811695
	.long	1072049730
	.type	cev,@object
	.size	cev,40
	.space 8, 0x00 	# pad
	.align 16
ABSVALMASK:
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.type	ABSVALMASK,@object
	.size	ABSVALMASK,16
	.align 4
rcp_t1:
	.long	0
	.long	1072693248
	.long	0
	.long	1072676992
	.long	0
	.long	1072660992
	.long	0
	.long	1072645248
	.long	0
	.long	1072629696
	.long	0
	.long	1072614400
	.long	0
	.long	1072599360
	.long	0
	.long	1072584512
	.long	0
	.long	1072569856
	.long	0
	.long	1072555456
	.long	0
	.long	1072541312
	.long	0
	.long	1072527296
	.long	0
	.long	1072513472
	.long	0
	.long	1072499904
	.long	0
	.long	1072486464
	.long	0
	.long	1072473280
	.long	0
	.long	1072460224
	.long	0
	.long	1072447360
	.long	0
	.long	1072434688
	.long	0
	.long	1072422208
	.long	0
	.long	1072409856
	.long	0
	.long	1072397696
	.long	0
	.long	1072385664
	.long	0
	.long	1072373824
	.long	0
	.long	1072362112
	.long	0
	.long	1072350592
	.long	0
	.long	1072339200
	.long	0
	.long	1072327936
	.long	0
	.long	1072316864
	.long	0
	.long	1072305856
	.long	0
	.long	1072295040
	.long	0
	.long	1072284352
	.long	0
	.long	1072273792
	.long	0
	.long	1072263424
	.long	0
	.long	1072253120
	.long	0
	.long	1072242944
	.long	0
	.long	1072232896
	.long	0
	.long	1072222976
	.long	0
	.long	1072213184
	.long	0
	.long	1072203520
	.long	0
	.long	1072193920
	.long	0
	.long	1072184448
	.long	0
	.long	1072175104
	.long	0
	.long	1072165888
	.long	0
	.long	1072156736
	.long	0
	.long	1072147776
	.long	0
	.long	1072138816
	.long	0
	.long	1072129984
	.long	0
	.long	1072121280
	.long	0
	.long	1072112704
	.long	0
	.long	1072104192
	.long	0
	.long	1072095744
	.long	0
	.long	1072087424
	.long	0
	.long	1072079168
	.long	0
	.long	1072071040
	.long	0
	.long	1072062976
	.long	0
	.long	1072054976
	.long	0
	.long	1072047104
	.long	0
	.long	1072039296
	.long	0
	.long	1072031552
	.long	0
	.long	1072023936
	.long	0
	.long	1072016384
	.long	0
	.long	1072008896
	.long	0
	.long	1072001536
	.long	0
	.long	1071994176
	.long	0
	.long	1071986944
	.long	0
	.long	1071979776
	.long	0
	.long	1071972672
	.long	0
	.long	1071965696
	.long	0
	.long	1071958720
	.long	0
	.long	1071951808
	.long	0
	.long	1071945024
	.long	0
	.long	1071938304
	.long	0
	.long	1071931584
	.long	0
	.long	1071924992
	.long	0
	.long	1071918464
	.long	0
	.long	1071911936
	.long	0
	.long	1071905536
	.long	0
	.long	1071899200
	.long	0
	.long	1071892864
	.long	0
	.long	1071886656
	.long	0
	.long	1071880448
	.long	0
	.long	1071874368
	.long	0
	.long	1071868288
	.long	0
	.long	1071862272
	.long	0
	.long	1071856384
	.long	0
	.long	1071850496
	.long	0
	.long	1071844608
	.long	0
	.long	1071838848
	.long	0
	.long	1071833152
	.long	0
	.long	1071827456
	.long	0
	.long	1071821824
	.long	0
	.long	1071816256
	.long	0
	.long	1071810752
	.long	0
	.long	1071805248
	.long	0
	.long	1071799872
	.long	0
	.long	1071794496
	.long	0
	.long	1071789120
	.long	0
	.long	1071783872
	.long	0
	.long	1071778624
	.long	0
	.long	1071773440
	.long	0
	.long	1071768320
	.long	0
	.long	1071763200
	.long	0
	.long	1071758144
	.long	0
	.long	1071753152
	.long	0
	.long	1071748160
	.long	0
	.long	1071743232
	.long	0
	.long	1071738368
	.long	0
	.long	1071733504
	.long	0
	.long	1071728704
	.long	0
	.long	1071723968
	.long	0
	.long	1071719232
	.long	0
	.long	1071714560
	.long	0
	.long	1071709952
	.long	0
	.long	1071705344
	.long	0
	.long	1071700800
	.long	0
	.long	1071696256
	.long	0
	.long	1071691776
	.long	0
	.long	1071687296
	.long	0
	.long	1071682880
	.long	0
	.long	1071678528
	.long	0
	.long	1071674176
	.long	0
	.long	1071669824
	.long	0
	.long	1071665536
	.long	0
	.long	1071661312
	.long	0
	.long	1071657088
	.long	0
	.long	1071652928
	.long	0
	.long	1071648768
	.long	0
	.long	1071644672
	.long	0
	.long	0
	.type	rcp_t1,@object
	.size	rcp_t1,1040
	.align 4
HALFSIG:
	.long	4160749568
	.long	4294967295
	.type	HALFSIG,@object
	.size	HALFSIG,8
	.align 4
rcp_t2:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693056
	.long	0
	.long	1072692800
	.long	0
	.long	1072692544
	.long	0
	.long	1072692288
	.long	0
	.long	1072692032
	.long	0
	.long	1072691776
	.long	0
	.long	1072691520
	.long	0
	.long	1072691264
	.long	0
	.long	1072691008
	.long	0
	.long	1072690752
	.long	0
	.long	1072690496
	.long	0
	.long	1072690240
	.long	0
	.long	1072689984
	.long	0
	.long	1072689728
	.long	0
	.long	1072689472
	.long	0
	.long	1072689216
	.long	0
	.long	1072688960
	.long	0
	.long	1072688704
	.long	0
	.long	1072688448
	.long	0
	.long	1072688192
	.long	0
	.long	1072687936
	.long	0
	.long	1072687680
	.long	0
	.long	1072687424
	.long	0
	.long	1072687168
	.long	0
	.long	1072686912
	.long	0
	.long	1072686656
	.long	0
	.long	1072686400
	.long	0
	.long	1072686144
	.long	0
	.long	1072685888
	.long	0
	.long	1072685632
	.long	0
	.long	1072685376
	.long	0
	.long	1072685120
	.long	0
	.long	1072684864
	.long	0
	.long	1072684608
	.long	0
	.long	1072684352
	.long	0
	.long	1072684096
	.long	0
	.long	1072683840
	.long	0
	.long	1072683584
	.long	0
	.long	1072683328
	.long	0
	.long	1072683072
	.long	0
	.long	1072682816
	.long	0
	.long	1072682560
	.long	0
	.long	1072682304
	.long	0
	.long	1072682048
	.long	0
	.long	1072681792
	.long	0
	.long	1072681600
	.long	0
	.long	1072681344
	.long	0
	.long	1072681088
	.long	0
	.long	1072680832
	.long	0
	.long	1072680576
	.long	0
	.long	1072680320
	.long	0
	.long	1072680064
	.long	0
	.long	1072679808
	.long	0
	.long	1072679552
	.long	0
	.long	1072679296
	.long	0
	.long	1072679040
	.long	0
	.long	1072678784
	.long	0
	.long	1072678528
	.long	0
	.long	1072678272
	.long	0
	.long	1072678016
	.long	0
	.long	1072677760
	.long	0
	.long	1072677504
	.long	0
	.long	1072677248
	.long	0
	.long	1072697344
	.long	0
	.long	1072697280
	.long	0
	.long	1072697216
	.long	0
	.long	1072697152
	.long	0
	.long	1072697088
	.long	0
	.long	1072697024
	.long	0
	.long	1072696960
	.long	0
	.long	1072696896
	.long	0
	.long	1072696832
	.long	0
	.long	1072696768
	.long	0
	.long	1072696704
	.long	0
	.long	1072696640
	.long	0
	.long	1072696576
	.long	0
	.long	1072696512
	.long	0
	.long	1072696448
	.long	0
	.long	1072696384
	.long	0
	.long	1072696320
	.long	0
	.long	1072696256
	.long	0
	.long	1072696192
	.long	0
	.long	1072696128
	.long	0
	.long	1072696064
	.long	0
	.long	1072696000
	.long	0
	.long	1072695936
	.long	0
	.long	1072695872
	.long	0
	.long	1072695808
	.long	0
	.long	1072695744
	.long	0
	.long	1072695680
	.long	0
	.long	1072695616
	.long	0
	.long	1072695552
	.long	0
	.long	1072695488
	.long	0
	.long	1072695424
	.long	0
	.long	1072695360
	.long	0
	.long	1072695296
	.long	0
	.long	1072695232
	.long	0
	.long	1072695168
	.long	0
	.long	1072695104
	.long	0
	.long	1072695040
	.long	0
	.long	1072694976
	.long	0
	.long	1072694912
	.long	0
	.long	1072694848
	.long	0
	.long	1072694784
	.long	0
	.long	1072694720
	.long	0
	.long	1072694656
	.long	0
	.long	1072694592
	.long	0
	.long	1072694528
	.long	0
	.long	1072694464
	.long	0
	.long	1072694400
	.long	0
	.long	1072694336
	.long	0
	.long	1072694272
	.long	0
	.long	1072694208
	.long	0
	.long	1072694144
	.long	0
	.long	1072694080
	.long	0
	.long	1072694016
	.long	0
	.long	1072693952
	.long	0
	.long	1072693888
	.long	0
	.long	1072693824
	.long	0
	.long	1072693760
	.long	0
	.long	1072693696
	.long	0
	.long	1072693632
	.long	0
	.long	1072693568
	.long	0
	.long	1072693504
	.long	0
	.long	1072693440
	.long	0
	.long	1072693376
	.long	0
	.long	1072693312
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.type	rcp_t2,@object
	.size	rcp_t2,1040
	.align 4
LHN:
	.long	1073741824
	.long	3220641095
	.type	LHN,@object
	.size	LHN,8
	.align 4
rcp_t3:
	.long	1073741824
	.long	1073157447
	.long	3221225472
	.long	1073157445
	.long	1073741824
	.long	1073157444
	.long	0
	.long	1073157443
	.long	2147483648
	.long	1073157441
	.long	0
	.long	1073157440
	.long	2147483648
	.long	1073157438
	.long	1073741824
	.long	1073157437
	.long	3221225472
	.long	1073157435
	.long	1073741824
	.long	1073157434
	.long	3221225472
	.long	1073157432
	.long	2147483648
	.long	1073157431
	.long	0
	.long	1073157430
	.long	2147483648
	.long	1073157428
	.long	0
	.long	1073157427
	.long	2147483648
	.long	1073157425
	.long	1073741824
	.long	1073157424
	.long	3221225472
	.long	1073157422
	.long	1073741824
	.long	1073157421
	.long	3221225472
	.long	1073157419
	.long	2147483648
	.long	1073157418
	.long	0
	.long	1073157417
	.long	2147483648
	.long	1073157415
	.long	0
	.long	1073157414
	.long	3221225472
	.long	1073157412
	.long	1073741824
	.long	1073157411
	.long	3221225472
	.long	1073157409
	.long	1073741824
	.long	1073157408
	.long	3221225472
	.long	1073157406
	.long	2147483648
	.long	1073157405
	.long	0
	.long	1073157404
	.long	2147483648
	.long	1073157402
	.long	0
	.long	1073157401
	.long	3221225472
	.long	1073157399
	.long	1073741824
	.long	1073157398
	.long	3221225472
	.long	1073157396
	.long	1073741824
	.long	1073157395
	.long	3221225472
	.long	1073157393
	.long	2147483648
	.long	1073157392
	.long	0
	.long	1073157391
	.long	2147483648
	.long	1073157389
	.long	0
	.long	1073157388
	.long	3221225472
	.long	1073157386
	.long	1073741824
	.long	1073157385
	.long	3221225472
	.long	1073157383
	.long	1073741824
	.long	1073157382
	.long	0
	.long	1073157381
	.long	2147483648
	.long	1073157379
	.long	0
	.long	1073157378
	.long	2147483648
	.long	1073157376
	.long	0
	.long	1073157375
	.long	3221225472
	.long	1073157373
	.long	1073741824
	.long	1073157372
	.long	3221225472
	.long	1073157370
	.long	1073741824
	.long	1073157369
	.long	0
	.long	1073157368
	.long	2147483648
	.long	1073157366
	.long	0
	.long	1073157365
	.long	2147483648
	.long	1073157363
	.long	1073741824
	.long	1073157362
	.long	3221225472
	.long	1073157360
	.long	1073741824
	.long	1073157359
	.long	3221225472
	.long	1073157357
	.long	1073741824
	.long	1073157356
	.long	0
	.long	1073157355
	.long	2147483648
	.long	1073157353
	.long	0
	.long	1073157352
	.long	2147483648
	.long	1073157350
	.long	1073741824
	.long	1073157349
	.long	3221225472
	.long	1073157347
	.long	1073741824
	.long	1073157346
	.long	3221225472
	.long	1073157344
	.long	2147483648
	.long	1073157343
	.long	0
	.long	1073157342
	.long	2147483648
	.long	1073157340
	.long	0
	.long	1073157339
	.long	2147483648
	.long	1073157337
	.long	1073741824
	.long	1073157336
	.long	3221225472
	.long	1073157334
	.long	1073741824
	.long	1073157333
	.long	3221225472
	.long	1073157331
	.long	2147483648
	.long	1073157330
	.long	0
	.long	1073157329
	.long	2147483648
	.long	1073157327
	.long	0
	.long	1073157326
	.long	3221225472
	.long	1073157324
	.long	1073741824
	.long	1073157323
	.long	3221225472
	.long	1073157321
	.long	1073741824
	.long	1073157320
	.long	3221225472
	.long	1073157318
	.long	2147483648
	.long	1073157317
	.long	0
	.long	1073157316
	.long	2147483648
	.long	1073157314
	.long	0
	.long	1073157313
	.long	3221225472
	.long	1073157311
	.long	1073741824
	.long	1073157310
	.long	3221225472
	.long	1073157308
	.long	1073741824
	.long	1073157307
	.long	0
	.long	1073157306
	.long	2147483648
	.long	1073157304
	.long	0
	.long	1073157303
	.long	2147483648
	.long	1073157301
	.long	0
	.long	1073157300
	.long	3221225472
	.long	1073157298
	.long	1073741824
	.long	1073157297
	.long	3221225472
	.long	1073157295
	.long	1073741824
	.long	1073157294
	.long	0
	.long	1073157293
	.long	2147483648
	.long	1073157291
	.long	0
	.long	1073157290
	.long	2147483648
	.long	1073157288
	.long	1073741824
	.long	1073157287
	.long	3221225472
	.long	1073157285
	.long	1073741824
	.long	1073157284
	.long	3221225472
	.long	1073157282
	.long	1073741824
	.long	1073157281
	.long	0
	.long	1073157280
	.long	2147483648
	.long	1073157278
	.long	0
	.long	1073157277
	.long	2147483648
	.long	1073157275
	.long	1073741824
	.long	1073157274
	.long	3221225472
	.long	1073157272
	.long	1073741824
	.long	1073157271
	.long	3221225472
	.long	1073157269
	.long	2147483648
	.long	1073157268
	.long	0
	.long	1073157267
	.long	2147483648
	.long	1073157265
	.long	0
	.long	1073157264
	.long	2147483648
	.long	1073157539
	.long	3221225472
	.long	1073157538
	.long	1073741824
	.long	1073157538
	.long	2147483648
	.long	1073157537
	.long	3221225472
	.long	1073157536
	.long	0
	.long	1073157536
	.long	1073741824
	.long	1073157535
	.long	2147483648
	.long	1073157534
	.long	3221225472
	.long	1073157533
	.long	0
	.long	1073157533
	.long	1073741824
	.long	1073157532
	.long	3221225472
	.long	1073157531
	.long	0
	.long	1073157531
	.long	1073741824
	.long	1073157530
	.long	2147483648
	.long	1073157529
	.long	3221225472
	.long	1073157528
	.long	0
	.long	1073157528
	.long	1073741824
	.long	1073157527
	.long	2147483648
	.long	1073157526
	.long	0
	.long	1073157526
	.long	1073741824
	.long	1073157525
	.long	2147483648
	.long	1073157524
	.long	3221225472
	.long	1073157523
	.long	0
	.long	1073157523
	.long	1073741824
	.long	1073157522
	.long	2147483648
	.long	1073157521
	.long	3221225472
	.long	1073157520
	.long	0
	.long	1073157520
	.long	2147483648
	.long	1073157519
	.long	3221225472
	.long	1073157518
	.long	0
	.long	1073157518
	.long	1073741824
	.long	1073157517
	.long	2147483648
	.long	1073157516
	.long	3221225472
	.long	1073157515
	.long	0
	.long	1073157515
	.long	1073741824
	.long	1073157514
	.long	2147483648
	.long	1073157513
	.long	0
	.long	1073157513
	.long	1073741824
	.long	1073157512
	.long	2147483648
	.long	1073157511
	.long	3221225472
	.long	1073157510
	.long	0
	.long	1073157510
	.long	1073741824
	.long	1073157509
	.long	2147483648
	.long	1073157508
	.long	3221225472
	.long	1073157507
	.long	0
	.long	1073157507
	.long	2147483648
	.long	1073157506
	.long	3221225472
	.long	1073157505
	.long	0
	.long	1073157505
	.long	1073741824
	.long	1073157504
	.long	2147483648
	.long	1073157503
	.long	3221225472
	.long	1073157502
	.long	0
	.long	1073157502
	.long	1073741824
	.long	1073157501
	.long	3221225472
	.long	1073157500
	.long	0
	.long	1073157500
	.long	1073741824
	.long	1073157499
	.long	2147483648
	.long	1073157498
	.long	3221225472
	.long	1073157497
	.long	0
	.long	1073157497
	.long	1073741824
	.long	1073157496
	.long	2147483648
	.long	1073157495
	.long	3221225472
	.long	1073157494
	.long	1073741824
	.long	1073157494
	.long	2147483648
	.long	1073157493
	.long	3221225472
	.long	1073157492
	.long	0
	.long	1073157492
	.long	1073741824
	.long	1073157491
	.long	2147483648
	.long	1073157490
	.long	3221225472
	.long	1073157489
	.long	0
	.long	1073157489
	.long	1073741824
	.long	1073157488
	.long	3221225472
	.long	1073157487
	.long	0
	.long	1073157487
	.long	1073741824
	.long	1073157486
	.long	2147483648
	.long	1073157485
	.long	3221225472
	.long	1073157484
	.long	0
	.long	1073157484
	.long	1073741824
	.long	1073157483
	.long	2147483648
	.long	1073157482
	.long	0
	.long	1073157482
	.long	1073741824
	.long	1073157481
	.long	2147483648
	.long	1073157480
	.long	3221225472
	.long	1073157479
	.long	0
	.long	1073157479
	.long	1073741824
	.long	1073157478
	.long	2147483648
	.long	1073157477
	.long	3221225472
	.long	1073157476
	.long	0
	.long	1073157476
	.long	2147483648
	.long	1073157475
	.long	3221225472
	.long	1073157474
	.long	0
	.long	1073157474
	.long	1073741824
	.long	1073157473
	.long	2147483648
	.long	1073157472
	.long	3221225472
	.long	1073157471
	.long	0
	.long	1073157471
	.long	1073741824
	.long	1073157470
	.long	2147483648
	.long	1073157469
	.long	0
	.long	1073157469
	.long	1073741824
	.long	1073157468
	.long	2147483648
	.long	1073157467
	.long	3221225472
	.long	1073157466
	.long	0
	.long	1073157466
	.long	1073741824
	.long	1073157465
	.long	2147483648
	.long	1073157464
	.long	3221225472
	.long	1073157463
	.long	0
	.long	1073157463
	.long	2147483648
	.long	1073157462
	.long	3221225472
	.long	1073157461
	.long	0
	.long	1073157461
	.long	1073741824
	.long	1073157460
	.long	2147483648
	.long	1073157459
	.long	3221225472
	.long	1073157458
	.long	0
	.long	1073157458
	.long	1073741824
	.long	1073157457
	.long	3221225472
	.long	1073157456
	.long	0
	.long	1073157456
	.long	1073741824
	.long	1073157455
	.long	2147483648
	.long	1073157454
	.long	3221225472
	.long	1073157453
	.long	0
	.long	1073157453
	.long	1073741824
	.long	1073157452
	.long	2147483648
	.long	1073157451
	.long	3221225472
	.long	1073157450
	.long	1073741824
	.long	1073157450
	.long	2147483648
	.long	1073157449
	.long	3221225472
	.long	1073157448
	.long	0
	.long	1073157448
	.long	1073741824
	.long	1073157447
	.long	0
	.long	0
	.type	rcp_t3,@object
	.size	rcp_t3,2064
	.align 4
Shifter:
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.type	Shifter,@object
	.size	Shifter,16
	.align 4
NEG_ZERO:
	.long	0
	.long	2147483648
	.type	NEG_ZERO,@object
	.size	NEG_ZERO,8
	.align 4
NEG_INF:
	.long	0
	.long	4293918720
	.type	NEG_INF,@object
	.size	NEG_INF,8
	.align 4
sNaN:
	.long	1
	.long	2146435072
	.type	sNaN,@object
	.size	sNaN,8
	.align 4
INF:
	.long	0
	.long	2146435072
	.type	INF,@object
	.size	INF,8
	.align 4
EMIN:
	.long	0
	.long	1048576
	.type	EMIN,@object
	.size	EMIN,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
