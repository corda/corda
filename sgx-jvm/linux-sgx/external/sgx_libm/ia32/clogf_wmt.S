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
	.file "clogf_wmt.c"
	.text
..TXTST0:
# -- Begin  static_func
	.text
       .align    16,0x90
static_func:
..B1.1:
..L1:

        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        lea       static_const_table@GOTOFF(%eax), %eax
        ret       
        .align    16,0x90
	.type	static_func,@function
	.size	static_func,.-static_func
	.data
# -- End  static_func
	.text
# -- Begin  clogf
	.text
       .align    16,0x90
	.globl clogf
clogf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $136, %esp
        movl      %esi, 80(%esp)
        call      static_func
        movl      %eax, %esi
        movss     144(%esp), %xmm0
        movss     148(%esp), %xmm1
        movss     %xmm0, (%esp)
        movss     %xmm1, 4(%esp)
        movl      %ebx, 8(%esp)
        movaps    %xmm0, %xmm2
        cvtss2sd  %xmm1, %xmm0
        cvtss2sd  %xmm2, %xmm1
        movsd     %xmm0, 40(%esp)
        movsd     %xmm1, 32(%esp)
        andpd     3248(%esi), %xmm0
        andpd     3248(%esi), %xmm1
        movl      4(%esp), %edx
        movl      (%esp), %ecx
        movl      $-2147483648, %eax
        andl      %edx, %eax
        andl      $2139095040, %edx
        subl      $8388608, %edx
        cmpl      $2130706432, %edx
        jae       .L_2TAG_PACKET_0.0.2
        movl      %ecx, %edx
        andl      $-2147483648, %ecx
        andl      $2139095040, %edx
        subl      $8388608, %edx
        cmpl      $2130706432, %edx
        jae       .L_2TAG_PACKET_1.0.2
        movapd    %xmm0, %xmm3
        divsd     %xmm1, %xmm0
        movq      3232(%esi), %xmm2
        mulsd     %xmm1, %xmm1
        mulsd     %xmm3, %xmm3
        addsd     %xmm3, %xmm1
        movq      %xmm1, 16(%esp)
        pand      %xmm1, %xmm2
        pextrw    $3, %xmm1, %edx
        subsd     4800(%esi), %xmm1
        pand      3248(%esi), %xmm1
        cmpltsd   2048(%esi), %xmm1
        movmskpd  %xmm1, %ebx
        por       4800(%esi), %xmm2
        testl     $1, %ebx
        jne       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_3.0.2:
        xorl      %ecx, %eax
        movl      20(%esp), %ebx
        sarl      $4, %edx
        andl      $1044480, %ebx
        movsd     4816(%esi), %xmm3
        xorpd     %xmm4, %xmm4
        subl      $1023, %edx
        xorpd     %xmm7, %xmm7
        sarl      $31, %ecx
        movl      %edx, 12(%esp)
        movd      %ecx, %xmm6
        pshufd    $0, %xmm6, %xmm6
        andpd     4848(%esi), %xmm6
        andpd     %xmm0, %xmm3
        pextrw    $3, %xmm0, %edx
        shrl      $9, %ebx
        movsd     %xmm0, %xmm1
        subl      $16288, %edx
        cmpl      $1119, %edx
        ja        .L_2TAG_PACKET_4.0.2
        movl      $16464, %ecx
        pinsrw    $3, %ecx, %xmm4
        shrl      $16, %eax
        orpd      4832(%esi), %xmm3
        movsd     %xmm4, %xmm7
        mulsd     (%esi,%ebx), %xmm2
        cmpsd     $6, %xmm3, %xmm4
        movsd     4800(%esi), %xmm5
        minsd     %xmm7, %xmm3
        andpd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm1
        andpd     %xmm4, %xmm5
        subsd     %xmm3, %xmm0
        subsd     4800(%esi), %xmm2
        addsd     %xmm5, %xmm1
        movq      3296(%esi), %xmm5
        divsd     %xmm1, %xmm0
        cvtsi2sdl 12(%esp), %xmm1
        movl      $176, %ecx
        cmpl      $176, %edx
        cmova     %ecx, %edx
        addl      $1, %edx
        movsd     4880(%esi), %xmm4
        pinsrw    $3, %eax, %xmm7
        addsd     3376(%esi,%edx,8), %xmm6
        xorpd     %xmm7, %xmm6
        mulsd     %xmm2, %xmm5
        addsd     3288(%esi), %xmm5
        mulsd     %xmm2, %xmm5
        addsd     3280(%esi), %xmm5
        mulsd     %xmm2, %xmm5
        xorpd     %xmm0, %xmm7
        mulsd     3264(%esi), %xmm1
        mulsd     %xmm0, %xmm0
        mulsd     %xmm7, %xmm4
        addsd     %xmm6, %xmm7
        mulsd     %xmm4, %xmm0
        addsd     4976(%esi,%ebx), %xmm1
        addsd     %xmm7, %xmm0
        addsd     %xmm1, %xmm5
        mulsd     2064(%esi), %xmm5
        cvtpd2ps  %xmm0, %xmm0
        cvtsd2ss  %xmm5, %xmm5
        movd      %xmm0, %edx
        movd      %xmm5, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_4.0.2:
        addl      $944, %edx
        cmpl      $2063, %edx
        ja        .L_2TAG_PACKET_6.0.2
        movq      4864(%esi), %xmm3
        shrl      $16, %eax
        movsd     4872(%esi), %xmm4
        mulsd     (%esi,%ebx), %xmm2
        pinsrw    $3, %eax, %xmm7
        mulsd     %xmm1, %xmm1
        subsd     4800(%esi), %xmm2
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm3
        movsd     %xmm1, %xmm5
        mulsd     %xmm1, %xmm1
        addsd     %xmm6, %xmm0
        cvtsi2sdl 12(%esp), %xmm6
        mulsd     %xmm5, %xmm4
        mulsd     %xmm3, %xmm1
        movq      3296(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     3288(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        mulsd     3264(%esi), %xmm6
        addsd     3280(%esi), %xmm3
        addsd     4976(%esi,%ebx), %xmm6
        mulsd     %xmm2, %xmm3
        addsd     %xmm4, %xmm1
        addsd     %xmm6, %xmm3
        addsd     %xmm1, %xmm0
        xorpd     %xmm7, %xmm0
        mulsd     2064(%esi), %xmm3
        cvtpd2ps  %xmm0, %xmm0
        cvtsd2ss  %xmm3, %xmm3
        movd      %xmm0, %edx
        movd      %xmm3, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_6.0.2:
        addl      $15344, %edx
        cmpl      $17392, %edx
        mulsd     (%esi,%ebx), %xmm2
        subsd     4800(%esi), %xmm2
        jae       .L_2TAG_PACKET_7.0.2
        shrl      $16, %eax
        pinsrw    $3, %eax, %xmm7
        addsd     %xmm6, %xmm0
        cvtsi2sdl 12(%esp), %xmm6
        movq      3296(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        pextrw    $3, %xmm0, %edx
        addsd     3288(%esi), %xmm3
        xorpd     %xmm7, %xmm0
        andl      $32752, %edx
        mulsd     %xmm2, %xmm3
        mulsd     3264(%esi), %xmm6
        addsd     3280(%esi), %xmm3
        movl      $1065353217, %eax
        movl      $8388608, %ecx
        addsd     4976(%esi,%ebx), %xmm6
        mulsd     %xmm2, %xmm3
        cmpl      $14352, %edx
        cmovb     %ecx, %eax
        movd      %eax, %xmm1
        addsd     %xmm6, %xmm3
        mulss     %xmm1, %xmm1
        mulsd     2064(%esi), %xmm3
        cvtpd2ps  %xmm0, %xmm0
        cvtsd2ss  %xmm3, %xmm3
        movd      %xmm0, %edx
        movd      %xmm3, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_7.0.2:
        movss     4944(%esi), %xmm4
        movss     4948(%esi), %xmm1
        cvtsi2sdl 12(%esp), %xmm6
        shrl      $16, %eax
        andl      $32768, %ecx
        movq      3296(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     3288(%esi), %xmm3
        xorl      %ecx, %eax
        mulsd     %xmm2, %xmm3
        xorps     %xmm0, %xmm0
        mulsd     3264(%esi), %xmm6
        addsd     3280(%esi), %xmm3
        pinsrw    $1, %eax, %xmm0
        addsd     4976(%esi,%ebx), %xmm6
        mulsd     %xmm2, %xmm3
        orps      %xmm0, %xmm4
        addsd     %xmm6, %xmm3
        orps      %xmm1, %xmm0
        mulsd     2064(%esi), %xmm3
        cvtsd2ss  %xmm3, %xmm3
        addss     %xmm4, %xmm0
        movd      %xmm0, %edx
        movd      %xmm3, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_2.0.2:
        movsd     32(%esp), %xmm5
        mulsd     %xmm5, %xmm5
        movq      16(%esp), %xmm1
        movq      16(%esp), %xmm6
        movq      16(%esp), %xmm2
        subsd     %xmm5, %xmm1
        subsd     %xmm1, %xmm6
        subsd     %xmm1, %xmm3
        subsd     %xmm6, %xmm5
        addsd     %xmm3, %xmm5
        xorl      %ecx, %eax
        movsd     4816(%esi), %xmm3
        xorpd     %xmm4, %xmm4
        xorpd     %xmm7, %xmm7
        sarl      $31, %ecx
        movd      %ecx, %xmm6
        pshufd    $0, %xmm6, %xmm6
        andpd     4848(%esi), %xmm6
        subsd     4800(%esi), %xmm2
        andpd     %xmm0, %xmm3
        pextrw    $3, %xmm0, %edx
        movsd     %xmm0, %xmm1
        addsd     %xmm5, %xmm2
        subl      $16288, %edx
        cmpl      $1119, %edx
        ja        .L_2TAG_PACKET_8.0.2
        movl      $16464, %ecx
        pinsrw    $3, %ecx, %xmm4
        shrl      $16, %eax
        orpd      4832(%esi), %xmm3
        movsd     %xmm4, %xmm7
        cmpsd     $6, %xmm3, %xmm4
        movsd     4800(%esi), %xmm5
        minsd     %xmm7, %xmm3
        andpd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm1
        andpd     %xmm4, %xmm5
        subsd     %xmm3, %xmm0
        movq      3344(%esi), %xmm3
        addsd     %xmm5, %xmm1
        divsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm3
        movl      $176, %ecx
        cmpl      $176, %edx
        cmova     %ecx, %edx
        addl      $1, %edx
        movsd     4880(%esi), %xmm4
        addsd     3336(%esi), %xmm3
        pinsrw    $3, %eax, %xmm7
        addsd     3376(%esi,%edx,8), %xmm6
        mulsd     %xmm2, %xmm3
        xorpd     %xmm7, %xmm6
        addsd     3328(%esi), %xmm3
        xorpd     %xmm0, %xmm7
        mulsd     %xmm2, %xmm3
        mulsd     %xmm0, %xmm0
        addsd     3320(%esi), %xmm3
        mulsd     %xmm7, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm6, %xmm7
        addsd     3312(%esi), %xmm3
        mulsd     %xmm4, %xmm0
        mulsd     %xmm2, %xmm3
        addsd     %xmm7, %xmm0
        cvtsd2ss  %xmm3, %xmm3
        cvtpd2ps  %xmm0, %xmm0
        movd      %xmm3, %eax
        movd      %xmm0, %edx
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_8.0.2:
        addl      $944, %edx
        cmpl      $2063, %edx
        ja        .L_2TAG_PACKET_9.0.2
        movq      4864(%esi), %xmm3
        shrl      $16, %eax
        movsd     4872(%esi), %xmm4
        pinsrw    $3, %eax, %xmm7
        mulsd     %xmm1, %xmm1
        mulsd     %xmm0, %xmm4
        mulsd     %xmm0, %xmm3
        movsd     %xmm1, %xmm5
        mulsd     %xmm1, %xmm1
        addsd     %xmm6, %xmm0
        movq      3344(%esi), %xmm6
        mulsd     %xmm2, %xmm6
        addsd     3336(%esi), %xmm6
        mulsd     %xmm5, %xmm4
        mulsd     %xmm2, %xmm6
        addsd     3328(%esi), %xmm6
        mulsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm6
        addsd     3320(%esi), %xmm6
        addsd     %xmm4, %xmm1
        mulsd     %xmm2, %xmm6
        addsd     %xmm1, %xmm0
        addsd     3312(%esi), %xmm6
        xorpd     %xmm7, %xmm0
        mulsd     %xmm2, %xmm6
        cvtpd2ps  %xmm0, %xmm0
        cvtsd2ss  %xmm6, %xmm6
        movd      %xmm0, %edx
        movd      %xmm6, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_9.0.2:
        addl      $15344, %edx
        cmpl      $17392, %edx
        jae       .L_2TAG_PACKET_10.0.2
        movq      3344(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        shrl      $16, %eax
        addsd     3336(%esi), %xmm3
        pinsrw    $3, %eax, %xmm7
        mulsd     %xmm2, %xmm3
        addsd     %xmm6, %xmm0
        addsd     3328(%esi), %xmm3
        pextrw    $3, %xmm0, %edx
        mulsd     %xmm2, %xmm3
        xorpd     %xmm7, %xmm0
        addsd     3320(%esi), %xmm3
        andl      $32752, %edx
        movl      $1065353217, %eax
        movl      8(%esp), %ecx
        movl      $8388608, %ecx
        mulsd     %xmm2, %xmm3
        cmpl      $14352, %edx
        cmovb     %ecx, %eax
        movd      %eax, %xmm1
        addsd     3312(%esi), %xmm3
        mulss     %xmm1, %xmm1
        mulsd     %xmm2, %xmm3
        cvtpd2ps  %xmm0, %xmm0
        cvtsd2ss  %xmm3, %xmm3
        movd      %xmm0, %edx
        movd      %xmm3, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_10.0.2:
        movss     4944(%esi), %xmm4
        movq      3344(%esi), %xmm3
        mulsd     %xmm2, %xmm3
        movss     4948(%esi), %xmm1
        addsd     3336(%esi), %xmm3
        shrl      $16, %eax
        mulsd     %xmm2, %xmm3
        andl      $32768, %ecx
        addsd     3328(%esi), %xmm3
        xorl      %ecx, %eax
        mulsd     %xmm2, %xmm3
        xorps     %xmm0, %xmm0
        addsd     3320(%esi), %xmm3
        pinsrw    $1, %eax, %xmm0
        mulsd     %xmm2, %xmm3
        orps      %xmm0, %xmm4
        addsd     3312(%esi), %xmm3
        orps      %xmm1, %xmm0
        mulsd     %xmm2, %xmm3
        addss     %xmm4, %xmm0
        cvtsd2ss  %xmm3, %xmm3
        movd      %xmm0, %edx
        movd      %xmm3, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_0.0.2:
        andl      $-2147483648, %ecx
.L_2TAG_PACKET_1.0.2:
        movl      4(%esp), %edx
        andl      $2147483647, %edx
        je        .L_2TAG_PACKET_11.0.2
        andl      $2139095040, %edx
        jne       .L_2TAG_PACKET_11.0.2
        movss     4(%esp), %xmm0
        orps      4896(%esi), %xmm0
        cvtss2sd  %xmm0, %xmm0
        movsd     4928(%esi), %xmm2
        andpd     %xmm0, %xmm2
        orpd      4912(%esi), %xmm2
        subsd     %xmm2, %xmm0
        movsd     %xmm0, 40(%esp)
.L_2TAG_PACKET_11.0.2:
        movl      (%esp), %edx
        andl      $2147483647, %edx
        je        .L_2TAG_PACKET_12.0.2
        andl      $2139095040, %edx
        jne       .L_2TAG_PACKET_12.0.2
        movss     (%esp), %xmm1
        orps      4896(%esi), %xmm1
        cvtss2sd  %xmm1, %xmm1
        movsd     4928(%esi), %xmm2
        andpd     %xmm1, %xmm2
        orpd      4912(%esi), %xmm2
        subsd     %xmm2, %xmm1
        movsd     %xmm1, 32(%esp)
.L_2TAG_PACKET_12.0.2:
        movsd     40(%esp), %xmm0
        movsd     32(%esp), %xmm1
        pextrw    $3, %xmm0, %edx
        andl      $32752, %edx
        subl      $16, %edx
        cmpl      $32736, %edx
        jae       .L_2TAG_PACKET_13.0.2
        pextrw    $3, %xmm1, %edx
        andl      $32752, %edx
        subl      $16, %edx
        cmpl      $32736, %edx
        jae       .L_2TAG_PACKET_14.0.2
        movq      %xmm0, %xmm3
        divsd     %xmm1, %xmm0
        movq      3232(%esi), %xmm2
        mulsd     %xmm1, %xmm1
        mulsd     %xmm3, %xmm3
        addsd     %xmm3, %xmm1
        movq      %xmm1, 16(%esp)
        pand      %xmm1, %xmm2
        pextrw    $3, %xmm1, %edx
        subsd     4800(%esi), %xmm1
        pand      3248(%esi), %xmm1
        cmpltsd   2048(%esi), %xmm1
        movmskpd  %xmm1, %ebx
        por       4800(%esi), %xmm2
        testl     $1, %ebx
        movl      8(%esp), %ebx
        psllq     $1, %xmm0
        psrlq     $1, %xmm0
        jne       .L_2TAG_PACKET_2.0.2
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_13.0.2:
        addl      $16, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_15.0.2
        movsd     %xmm0, %xmm2
        psllq     $12, %xmm2
        xorpd     %xmm3, %xmm3
        pcmpeqd   %xmm3, %xmm2
        psrlq     $16, %xmm2
        movd      %xmm2, %edx
        cmpl      $-1, %edx
        je        .L_2TAG_PACKET_16.0.2
        movss     4(%esp), %xmm0
        movl      (%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        mulss     %xmm0, %xmm0
        jg        .L_2TAG_PACKET_17.0.2
        je        .L_2TAG_PACKET_18.0.2
        movd      %xmm0, %edx
        movd      %xmm0, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_18.0.2:
        movd      %xmm0, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_17.0.2:
        movss     (%esp), %xmm1
        mulss     %xmm0, %xmm0
        mulss     %xmm1, %xmm1
        movd      %xmm0, %edx
        movd      %xmm1, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_15.0.2:
        pextrw    $3, %xmm1, %edx
        andl      $32752, %edx
        cmpl      $32752, %edx
        jne       .L_2TAG_PACKET_19.0.2
        movsd     %xmm1, %xmm2
        psllq     $12, %xmm2
        xorpd     %xmm3, %xmm3
        pcmpeqd   %xmm3, %xmm2
        psrlq     $16, %xmm2
        movd      %xmm2, %edx
        cmpl      $-1, %edx
        jne       .L_2TAG_PACKET_20.0.2
.L_2TAG_PACKET_19.0.2:
        cmpl      $0, %ecx
        jne       .L_2TAG_PACKET_21.0.2
        movl      %eax, %edx
        movl      (%esp), %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_22.0.2
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_23.0.2
        movl      (%esp), %eax
        movl      %edx, 28(%esp)
        andl      $2147483647, %eax
        movl      %eax, 24(%esp)
        jmp       .L_2TAG_PACKET_24.0.2
.L_2TAG_PACKET_21.0.2:
        movss     4952(%esi), %xmm2
        movss     4956(%esi), %xmm3
        addss     %xmm3, %xmm2
        movd      %xmm2, %edx
        orl       %eax, %edx
        movl      (%esp), %eax
        cmpl      $-2147483648, %eax
        je        .L_2TAG_PACKET_22.0.2
        cmpl      $-8388608, %eax
        je        .L_2TAG_PACKET_23.0.2
        movl      (%esp), %eax
        movl      %edx, 28(%esp)
        andl      $2147483647, %eax
        movl      %eax, 24(%esp)
        jmp       .L_2TAG_PACKET_24.0.2
.L_2TAG_PACKET_22.0.2:
        pxor      %xmm1, %xmm1
        movss     3216(%esi), %xmm0
        divss     %xmm1, %xmm0
        movl      $-8388608, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_23.0.2:
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_16.0.2:
        pextrw    $3, %xmm1, %edx
        andl      $32752, %edx
        cmpl      $32752, %edx
        je        .L_2TAG_PACKET_25.0.2
        movss     4944(%esi), %xmm2
        movss     4948(%esi), %xmm3
        addss     %xmm3, %xmm2
        movd      %xmm2, %edx
        orl       %eax, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_25.0.2:
        movsd     %xmm1, %xmm2
        psllq     $12, %xmm2
        xorpd     %xmm3, %xmm3
        pcmpeqd   %xmm3, %xmm2
        psrlq     $16, %xmm2
        movd      %xmm2, %edx
        cmpl      $-1, %edx
        jne       .L_2TAG_PACKET_26.0.2
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_27.0.2
        movss     4944(%esi), %xmm2
        movss     4948(%esi), %xmm3
        movss     4960(%esi), %xmm4
        movss     4964(%esi), %xmm5
        addss     %xmm3, %xmm2
        addss     %xmm5, %xmm4
        addss     %xmm4, %xmm2
        movd      %xmm2, %edx
        orl       %eax, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_27.0.2:
        movss     4960(%esi), %xmm4
        movss     4964(%esi), %xmm5
        addss     %xmm5, %xmm4
        movd      %xmm4, %edx
        orl       %eax, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_14.0.2:
        addl      $16, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_28.0.2
        movsd     %xmm1, %xmm2
        psllq     $12, %xmm2
        xorps     %xmm3, %xmm3
        pcmpeqd   %xmm3, %xmm2
        psrlq     $16, %xmm2
        movd      %xmm2, %edx
        cmpl      $-1, %edx
        jne       .L_2TAG_PACKET_20.0.2
        cmpl      $0, %ecx
        jne       .L_2TAG_PACKET_29.0.2
        movl      %eax, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_29.0.2:
        movss     4952(%esi), %xmm2
        movss     4956(%esi), %xmm3
        addss     %xmm3, %xmm2
        movd      %xmm2, %edx
        orl       %eax, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_28.0.2:
        movss     4944(%esi), %xmm2
        movss     4948(%esi), %xmm3
        addss     %xmm3, %xmm2
        movd      %xmm2, %edx
        orl       %eax, %edx
        movl      4(%esp), %eax
        movl      %edx, 28(%esp)
        andl      $2147483647, %eax
        movl      %eax, 24(%esp)
        jmp       .L_2TAG_PACKET_24.0.2
.L_2TAG_PACKET_20.0.2:
        addsd     %xmm1, %xmm1
        cvtpd2ps  %xmm1, %xmm0
        movd      %xmm0, %edx
        movl      %edx, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_26.0.2:
        addsd     %xmm1, %xmm1
        cvtpd2ps  %xmm1, %xmm0
        movd      %xmm0, %edx
        movl      $2139095040, %eax
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_24.0.2:
        movss     24(%esp), %xmm0
        movsd     3168(%esi), %xmm1
        movsd     3184(%esi), %xmm3
        pextrw    $1, %xmm0, %eax
        movaps    %xmm0, %xmm4
        psllq     $29, %xmm0
        andpd     %xmm0, %xmm1
        orpd      %xmm3, %xmm1
        movsd     2080(%esi), %xmm5
        movsd     2088(%esi), %xmm6
        movsd     2096(%esi), %xmm2
        movl      %eax, %edx
        movl      $32639, %ecx
        subl      %edx, %ecx
        subl      $128, %edx
        orl       %ecx, %edx
        cmpl      $32768, %edx
        jae       .L_2TAG_PACKET_30.0.2
        movl      $16191, %ecx
.L_2TAG_PACKET_31.0.2:
        movd      %ecx, %xmm0
        movsd     2640(%esi), %xmm3
        andl      $127, %eax
        addl      $1, %eax
        andl      $254, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        movsd     2640(%esi,%eax), %xmm7
        psrlq     $16, %xmm4
        psubd     %xmm0, %xmm4
        psrad     $7, %xmm4
        cvtdq2pd  %xmm4, %xmm4
        movsd     2112(%esi,%eax), %xmm0
        mulsd     %xmm7, %xmm1
        subsd     %xmm3, %xmm1
        movsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm5
        movsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm1
        addsd     %xmm6, %xmm5
        mulsd     3264(%esi), %xmm4
        mulsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm2
        addsd     %xmm3, %xmm2
        mulsd     %xmm5, %xmm7
        addsd     %xmm4, %xmm0
        addsd     %xmm7, %xmm2
        addsd     %xmm2, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        movd      %xmm0, %eax
        movl      28(%esp), %edx
        movl      8(%esp), %ebx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_30.0.2:
        movl      24(%esp), %edx
        mulss     3200(%esi), %xmm4
        movsd     3168(%esi), %xmm1
        pextrw    $1, %xmm4, %eax
        cvtps2pd  %xmm4, %xmm0
        andpd     %xmm0, %xmm1
        orpd      %xmm3, %xmm1
        movl      $24383, %ecx
        jmp       .L_2TAG_PACKET_31.0.2
.L_2TAG_PACKET_5.0.2:
        movl      80(%esp), %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	clogf,@function
	.size	clogf,.-clogf
	.data
# -- End  clogf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	1072689152
	.long	0
	.long	1072680960
	.long	0
	.long	1072672768
	.long	0
	.long	1072664576
	.long	0
	.long	1072657408
	.long	0
	.long	1072649216
	.long	0
	.long	1072641024
	.long	0
	.long	1072633856
	.long	0
	.long	1072625664
	.long	0
	.long	1072618496
	.long	0
	.long	1072610304
	.long	0
	.long	1072603136
	.long	0
	.long	1072595968
	.long	0
	.long	1072587776
	.long	0
	.long	1072580608
	.long	0
	.long	1072573440
	.long	0
	.long	1072566272
	.long	0
	.long	1072559104
	.long	0
	.long	1072551936
	.long	0
	.long	1072544768
	.long	0
	.long	1072537600
	.long	0
	.long	1072530432
	.long	0
	.long	1072524288
	.long	0
	.long	1072517120
	.long	0
	.long	1072509952
	.long	0
	.long	1072502784
	.long	0
	.long	1072496640
	.long	0
	.long	1072489472
	.long	0
	.long	1072483328
	.long	0
	.long	1072476160
	.long	0
	.long	1072470016
	.long	0
	.long	1072463872
	.long	0
	.long	1072456704
	.long	0
	.long	1072450560
	.long	0
	.long	1072444416
	.long	0
	.long	1072438272
	.long	0
	.long	1072431104
	.long	0
	.long	1072424960
	.long	0
	.long	1072418816
	.long	0
	.long	1072412672
	.long	0
	.long	1072406528
	.long	0
	.long	1072400384
	.long	0
	.long	1072394240
	.long	0
	.long	1072389120
	.long	0
	.long	1072382976
	.long	0
	.long	1072376832
	.long	0
	.long	1072370688
	.long	0
	.long	1072364544
	.long	0
	.long	1072359424
	.long	0
	.long	1072353280
	.long	0
	.long	1072348160
	.long	0
	.long	1072342016
	.long	0
	.long	1072335872
	.long	0
	.long	1072330752
	.long	0
	.long	1072325632
	.long	0
	.long	1072319488
	.long	0
	.long	1072314368
	.long	0
	.long	1072308224
	.long	0
	.long	1072303104
	.long	0
	.long	1072297984
	.long	0
	.long	1072292864
	.long	0
	.long	1072286720
	.long	0
	.long	1072281600
	.long	0
	.long	1072276480
	.long	0
	.long	1072271360
	.long	0
	.long	1072266240
	.long	0
	.long	1072261120
	.long	0
	.long	1072256000
	.long	0
	.long	1072250880
	.long	0
	.long	1072245760
	.long	0
	.long	1072240640
	.long	0
	.long	1072235520
	.long	0
	.long	1072230400
	.long	0
	.long	1072225280
	.long	0
	.long	1072220160
	.long	0
	.long	1072216064
	.long	0
	.long	1072210944
	.long	0
	.long	1072205824
	.long	0
	.long	1072200704
	.long	0
	.long	1072196608
	.long	0
	.long	1072191488
	.long	0
	.long	1072186368
	.long	0
	.long	1072182272
	.long	0
	.long	1072177152
	.long	0
	.long	1072173056
	.long	0
	.long	1072167936
	.long	0
	.long	1072163840
	.long	0
	.long	1072158720
	.long	0
	.long	1072154624
	.long	0
	.long	1072149504
	.long	0
	.long	1072145408
	.long	0
	.long	1072141312
	.long	0
	.long	1072136192
	.long	0
	.long	1072132096
	.long	0
	.long	1072128000
	.long	0
	.long	1072123904
	.long	0
	.long	1072118784
	.long	0
	.long	1072114688
	.long	0
	.long	1072110592
	.long	0
	.long	1072106496
	.long	0
	.long	1072102400
	.long	0
	.long	1072098304
	.long	0
	.long	1072093184
	.long	0
	.long	1072089088
	.long	0
	.long	1072084992
	.long	0
	.long	1072080896
	.long	0
	.long	1072076800
	.long	0
	.long	1072072704
	.long	0
	.long	1072068608
	.long	0
	.long	1072064512
	.long	0
	.long	1072061440
	.long	0
	.long	1072057344
	.long	0
	.long	1072053248
	.long	0
	.long	1072049152
	.long	0
	.long	1072045056
	.long	0
	.long	1072040960
	.long	0
	.long	1072036864
	.long	0
	.long	1072033792
	.long	0
	.long	1072029696
	.long	0
	.long	1072025600
	.long	0
	.long	1072022528
	.long	0
	.long	1072018432
	.long	0
	.long	1072014336
	.long	0
	.long	1072011264
	.long	0
	.long	1072007168
	.long	0
	.long	1072003072
	.long	0
	.long	1072000000
	.long	0
	.long	1071995904
	.long	0
	.long	1071992832
	.long	0
	.long	1071988736
	.long	0
	.long	1071985664
	.long	0
	.long	1071981568
	.long	0
	.long	1071978496
	.long	0
	.long	1071974400
	.long	0
	.long	1071971328
	.long	0
	.long	1071967232
	.long	0
	.long	1071964160
	.long	0
	.long	1071960064
	.long	0
	.long	1071956992
	.long	0
	.long	1071953920
	.long	0
	.long	1071949824
	.long	0
	.long	1071946752
	.long	0
	.long	1071943680
	.long	0
	.long	1071939584
	.long	0
	.long	1071936512
	.long	0
	.long	1071933440
	.long	0
	.long	1071930368
	.long	0
	.long	1071926272
	.long	0
	.long	1071923200
	.long	0
	.long	1071920128
	.long	0
	.long	1071917056
	.long	0
	.long	1071913984
	.long	0
	.long	1071909888
	.long	0
	.long	1071906816
	.long	0
	.long	1071903744
	.long	0
	.long	1071900672
	.long	0
	.long	1071897600
	.long	0
	.long	1071894528
	.long	0
	.long	1071891456
	.long	0
	.long	1071888384
	.long	0
	.long	1071885312
	.long	0
	.long	1071882240
	.long	0
	.long	1071879168
	.long	0
	.long	1071876096
	.long	0
	.long	1071873024
	.long	0
	.long	1071869952
	.long	0
	.long	1071866880
	.long	0
	.long	1071863808
	.long	0
	.long	1071860736
	.long	0
	.long	1071857664
	.long	0
	.long	1071854592
	.long	0
	.long	1071851520
	.long	0
	.long	1071849472
	.long	0
	.long	1071846400
	.long	0
	.long	1071843328
	.long	0
	.long	1071840256
	.long	0
	.long	1071837184
	.long	0
	.long	1071834112
	.long	0
	.long	1071832064
	.long	0
	.long	1071828992
	.long	0
	.long	1071825920
	.long	0
	.long	1071822848
	.long	0
	.long	1071820800
	.long	0
	.long	1071817728
	.long	0
	.long	1071814656
	.long	0
	.long	1071812608
	.long	0
	.long	1071809536
	.long	0
	.long	1071806464
	.long	0
	.long	1071804416
	.long	0
	.long	1071801344
	.long	0
	.long	1071798272
	.long	0
	.long	1071796224
	.long	0
	.long	1071793152
	.long	0
	.long	1071790080
	.long	0
	.long	1071788032
	.long	0
	.long	1071784960
	.long	0
	.long	1071782912
	.long	0
	.long	1071779840
	.long	0
	.long	1071777792
	.long	0
	.long	1071774720
	.long	0
	.long	1071771648
	.long	0
	.long	1071769600
	.long	0
	.long	1071766528
	.long	0
	.long	1071764480
	.long	0
	.long	1071762432
	.long	0
	.long	1071759360
	.long	0
	.long	1071757312
	.long	0
	.long	1071754240
	.long	0
	.long	1071752192
	.long	0
	.long	1071749120
	.long	0
	.long	1071747072
	.long	0
	.long	1071744000
	.long	0
	.long	1071741952
	.long	0
	.long	1071739904
	.long	0
	.long	1071736832
	.long	0
	.long	1071734784
	.long	0
	.long	1071732736
	.long	0
	.long	1071729664
	.long	0
	.long	1071727616
	.long	0
	.long	1071725568
	.long	0
	.long	1071722496
	.long	0
	.long	1071720448
	.long	0
	.long	1071718400
	.long	0
	.long	1071715328
	.long	0
	.long	1071713280
	.long	0
	.long	1071711232
	.long	0
	.long	1071709184
	.long	0
	.long	1071706112
	.long	0
	.long	1071704064
	.long	0
	.long	1071702016
	.long	0
	.long	1071699968
	.long	0
	.long	1071696896
	.long	0
	.long	1071694848
	.long	0
	.long	1071692800
	.long	0
	.long	1071690752
	.long	0
	.long	1071688704
	.long	0
	.long	1071686656
	.long	0
	.long	1071683584
	.long	0
	.long	1071681536
	.long	0
	.long	1071679488
	.long	0
	.long	1071677440
	.long	0
	.long	1071675392
	.long	0
	.long	1071673344
	.long	0
	.long	1071671296
	.long	0
	.long	1071669248
	.long	0
	.long	1071666176
	.long	0
	.long	1071664128
	.long	0
	.long	1071662080
	.long	0
	.long	1071660032
	.long	0
	.long	1071657984
	.long	0
	.long	1071655936
	.long	0
	.long	1071653888
	.long	0
	.long	1071651840
	.long	0
	.long	1071649792
	.long	0
	.long	1071647744
	.long	0
	.long	1071645696
	.long	3951369912
	.long	1067366481
	.long	3951369912
	.long	3214850129
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.long	0
	.long	3218079744
	.long	1431655765
	.long	1070945621
	.long	0
	.long	3219128320
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2969299638
	.long	1066385576
	.long	242759278
	.long	1067418267
	.long	4130528649
	.long	1067938904
	.long	3222363743
	.long	1068436016
	.long	2518406343
	.long	1068712407
	.long	2330290349
	.long	1068953810
	.long	977983493
	.long	1069191789
	.long	1848308976
	.long	1069426439
	.long	2094042057
	.long	1069602686
	.long	4162844547
	.long	1069716818
	.long	1825274911
	.long	1069829419
	.long	1890030541
	.long	1069940528
	.long	34658761
	.long	1070050185
	.long	2633258952
	.long	1070158426
	.long	1317059912
	.long	1070265289
	.long	3348798148
	.long	1070370807
	.long	1848308976
	.long	1070475015
	.long	1834892534
	.long	1070577944
	.long	3799224489
	.long	1070637860
	.long	2880153273
	.long	1070688092
	.long	4224678190
	.long	1070737729
	.long	3135315492
	.long	1070786786
	.long	1432962655
	.long	1070835276
	.long	3256639515
	.long	1070883211
	.long	2267936667
	.long	1070930605
	.long	3210218142
	.long	1070977469
	.long	513480033
	.long	1071023816
	.long	4139503392
	.long	1071069655
	.long	998420681
	.long	1071115000
	.long	2260812359
	.long	1071159859
	.long	471930696
	.long	1071204244
	.long	288677044
	.long	3218237794
	.long	1666870396
	.long	3218194329
	.long	2183652263
	.long	3218151310
	.long	1615681869
	.long	3218108728
	.long	1823715893
	.long	3218053404
	.long	4293333700
	.long	3217969934
	.long	4066729029
	.long	3217887287
	.long	943445964
	.long	3217805447
	.long	1015733272
	.long	3217724397
	.long	3707174790
	.long	3217644122
	.long	1699848676
	.long	3217564609
	.long	2339728692
	.long	3217485842
	.long	1800683248
	.long	3217407808
	.long	2201203741
	.long	3217330493
	.long	2954203727
	.long	3217253884
	.long	709476838
	.long	3217177969
	.long	2183652973
	.long	3217102734
	.long	970708496
	.long	3217025169
	.long	1669614391
	.long	3216877351
	.long	2376949106
	.long	3216730824
	.long	1566133827
	.long	3216585566
	.long	183157569
	.long	3216441555
	.long	1562411141
	.long	3216298769
	.long	2756121236
	.long	3216157188
	.long	3047046473
	.long	3216016792
	.long	3453858674
	.long	3215772531
	.long	346008798
	.long	3215496362
	.long	3559775130
	.long	3215222446
	.long	2313636156
	.long	3214950749
	.long	684243850
	.long	3214428453
	.long	1486053291
	.long	3213893718
	.long	1971906393
	.long	3212840981
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	528611360
	.long	1072660984
	.long	4034666248
	.long	1072629697
	.long	2884679527
	.long	1072599345
	.long	505290270
	.long	1072569886
	.long	1991868891
	.long	1072541280
	.long	490853405
	.long	1072513492
	.long	2298714891
	.long	1072486486
	.long	477218588
	.long	1072460231
	.long	58835168
	.long	1072434695
	.long	348240592
	.long	1072409849
	.long	3035110223
	.long	1072385665
	.long	3164712744
	.long	1072362118
	.long	1617585086
	.long	1072339183
	.long	440509466
	.long	1072316836
	.long	2500867033
	.long	1072295054
	.long	2576980378
	.long	1072273817
	.long	4241943008
	.long	1072253104
	.long	2409371898
	.long	1072232897
	.long	258732970
	.long	1072213177
	.long	409044504
	.long	1072193926
	.long	404232216
	.long	1072175128
	.long	1098712564
	.long	1072156767
	.long	2172167368
	.long	1072138828
	.long	1952257862
	.long	1072121297
	.long	1544257904
	.long	1072104160
	.long	381774871
	.long	1072087404
	.long	377579543
	.long	1072071016
	.long	1493901668
	.long	1072054984
	.long	1616385542
	.long	1072039297
	.long	731058263
	.long	1072023944
	.long	813783277
	.long	1072008914
	.long	1431655765
	.long	1071994197
	.long	3940743189
	.long	1071979783
	.long	2804876601
	.long	1071965664
	.long	2689777499
	.long	1071951830
	.long	1202590843
	.long	1071938273
	.long	1700977147
	.long	1071924984
	.long	336860180
	.long	1071911956
	.long	3169102082
	.long	1071899180
	.long	330382100
	.long	1071886651
	.long	327235604
	.long	1071874360
	.long	4213930177
	.long	1071862300
	.long	1244336319
	.long	1071850467
	.long	3181457256
	.long	1071838852
	.long	1300311200
	.long	1071827451
	.long	702812830
	.long	1071816257
	.long	3095471925
	.long	1071805264
	.long	2454267026
	.long	1071794468
	.long	2166487928
	.long	1071783863
	.long	2109808496
	.long	1071773444
	.long	2913108253
	.long	1071763206
	.long	1629125526
	.long	1071753145
	.long	293672978
	.long	1071743256
	.long	1601513229
	.long	1071733534
	.long	288737297
	.long	1071723976
	.long	286331153
	.long	1071714577
	.long	1810275472
	.long	1071705333
	.long	1337776699
	.long	1071696241
	.long	174592167
	.long	1071687297
	.long	138547332
	.long	1071678497
	.long	3539053052
	.long	1071669837
	.long	272696336
	.long	1071661316
	.long	2164392968
	.long	1071652928
	.long	0
	.long	1071644672
	.long	0
	.long	0
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	1602224128
	.long	1602224128
	.long	0
	.long	0
	.long	1065353216
	.long	1065353216
	.long	0
	.long	0
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4277811695
	.long	1072049730
	.long	4277811695
	.long	1072049730
	.long	0
	.long	1072693248
	.long	3879088107
	.long	3219128321
	.long	1754324240
	.long	1070945626
	.long	0
	.long	0
	.long	12982
	.long	1071644672
	.long	3503368358
	.long	3218079743
	.long	300625100
	.long	1069897045
	.long	3513684914
	.long	3217031970
	.long	1735899863
	.long	1069129695
	.long	0
	.long	0
	.long	2147483647
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3819695742
	.long	1067482761
	.long	2998791009
	.long	1067548225
	.long	3339424991
	.long	1067613680
	.long	2710002256
	.long	1067679126
	.long	3275701428
	.long	1067744562
	.long	2908636881
	.long	1067809988
	.long	3777889398
	.long	1067875403
	.long	3759667419
	.long	1067940807
	.long	732369940
	.long	1068006200
	.long	1166616461
	.long	1068071580
	.long	2945472892
	.long	1068136947
	.long	3954480976
	.long	1068202301
	.long	2081752829
	.long	1068267642
	.long	3807999788
	.long	1068332968
	.long	2731789884
	.long	1068398280
	.long	1044477961
	.long	1068463577
	.long	1486930287
	.long	1068530218
	.long	2293016881
	.long	1068595466
	.long	201518157
	.long	1068660680
	.long	4054234584
	.long	1068725856
	.long	1246477213
	.long	1068790995
	.long	678186699
	.long	1068856093
	.long	2690594995
	.long	1068921148
	.long	3362611517
	.long	1068986159
	.long	3102162111
	.long	1069051124
	.long	2352611067
	.long	1069116041
	.long	1594134794
	.long	1069180908
	.long	1345079306
	.long	1069245723
	.long	2163300970
	.long	1069310484
	.long	352522716
	.long	1069375190
	.long	848541647
	.long	1069439838
	.long	40647312
	.long	1069504427
	.long	2216766270
	.long	1069574357
	.long	1090914384
	.long	1069638757
	.long	387601244
	.long	1069703022
	.long	3991640484
	.long	1069767144
	.long	3322489502
	.long	1069831118
	.long	3121698570
	.long	1069894936
	.long	4289964660
	.long	1069958591
	.long	3903312386
	.long	1070022077
	.long	3818449864
	.long	1070085387
	.long	2097480306
	.long	1070148515
	.long	1611694502
	.long	1070211454
	.long	1464694796
	.long	1070274198
	.long	1299612775
	.long	1070336741
	.long	1310544789
	.long	1070399077
	.long	2253168030
	.long	1070461200
	.long	1159567373
	.long	1070523105
	.long	1359373750
	.long	1070605818
	.long	908341706
	.long	1070667034
	.long	1743027350
	.long	1070727765
	.long	2055355646
	.long	1070787992
	.long	690426164
	.long	1070847697
	.long	1483247847
	.long	1070906862
	.long	392040270
	.long	1070965472
	.long	2673846014
	.long	1071023511
	.long	1384215810
	.long	1071080967
	.long	3101660631
	.long	1071137826
	.long	2094057058
	.long	1071194078
	.long	1712750594
	.long	1071249712
	.long	1411515787
	.long	1071304719
	.long	931538085
	.long	1071359091
	.long	179139065
	.long	1071412821
	.long	3387721259
	.long	1071465902
	.long	2132236852
	.long	1071544299
	.long	1942070284
	.long	1071645596
	.long	1532707802
	.long	1071695380
	.long	2294184979
	.long	1071743834
	.long	3805060714
	.long	1071790961
	.long	2215037898
	.long	1071836770
	.long	483661594
	.long	1071881273
	.long	1534679894
	.long	1071924486
	.long	1538714628
	.long	1071966430
	.long	527642555
	.long	1072007128
	.long	291339150
	.long	1072046605
	.long	2450210201
	.long	1072084888
	.long	2411367951
	.long	1072122007
	.long	681549971
	.long	1072157992
	.long	1466745541
	.long	1072192873
	.long	2845622366
	.long	1072226682
	.long	2838871438
	.long	1072275456
	.long	4200275274
	.long	1072337034
	.long	3034733530
	.long	1072394897
	.long	3207412993
	.long	1072449290
	.long	624461478
	.long	1072500450
	.long	767665908
	.long	1072548600
	.long	1110773639
	.long	1072593952
	.long	1940828530
	.long	1072636704
	.long	1911329388
	.long	1072677041
	.long	1764715788
	.long	1072704191
	.long	3332979233
	.long	1072722195
	.long	1321870254
	.long	1072739231
	.long	3657429030
	.long	1072755365
	.long	4197624557
	.long	1072770661
	.long	1512059493
	.long	1072785177
	.long	453379037
	.long	1072798965
	.long	1942345162
	.long	1072818388
	.long	4210176273
	.long	1072842164
	.long	4185644010
	.long	1072863795
	.long	679688788
	.long	1072883543
	.long	29432865
	.long	1072901630
	.long	4070721092
	.long	1072918247
	.long	2252468843
	.long	1072933561
	.long	2929724825
	.long	1072947712
	.long	1377513368
	.long	1072960824
	.long	1031632908
	.long	1072973003
	.long	2516508130
	.long	1072984342
	.long	3792452178
	.long	1072994923
	.long	3147791459
	.long	1073004818
	.long	999189752
	.long	1073014090
	.long	711011011
	.long	1073022794
	.long	15640363
	.long	1073030980
	.long	1218463589
	.long	1073042382
	.long	2538470555
	.long	1073056144
	.long	1229720947
	.long	1073068489
	.long	3115427016
	.long	1073079621
	.long	4030612557
	.long	1073089709
	.long	2728521257
	.long	1073098892
	.long	1118696283
	.long	1073107285
	.long	2682711255
	.long	1073114984
	.long	2073898081
	.long	1073122072
	.long	1403700297
	.long	1073128618
	.long	2502685617
	.long	1073134681
	.long	1531926851
	.long	1073140313
	.long	3572814411
	.long	1073145557
	.long	1695536111
	.long	1073150453
	.long	2363057203
	.long	1073155033
	.long	2873365682
	.long	1073159327
	.long	1053384691
	.long	1073165288
	.long	3270542712
	.long	1073172451
	.long	1353631484
	.long	1073178850
	.long	3511218460
	.long	1073184599
	.long	4121259284
	.long	1073189793
	.long	1193862106
	.long	1073194509
	.long	3861949790
	.long	1073198808
	.long	1486904578
	.long	1073202745
	.long	2879153715
	.long	1073206362
	.long	385353253
	.long	1073209698
	.long	1125865839
	.long	1073212783
	.long	1221361475
	.long	1073215645
	.long	2077323573
	.long	1073218307
	.long	215611373
	.long	1073220790
	.long	2347419265
	.long	1073223110
	.long	1379112765
	.long	1073225284
	.long	3891198463
	.long	1073228298
	.long	3395914051
	.long	1073231917
	.long	2799919478
	.long	1073235146
	.long	1138673476
	.long	1073238045
	.long	3408855940
	.long	1073240661
	.long	2044858738
	.long	1073243035
	.long	2578795176
	.long	1073245198
	.long	4196285314
	.long	1073247177
	.long	224877747
	.long	1073248996
	.long	3271386490
	.long	1073250671
	.long	813635989
	.long	1073252221
	.long	369829519
	.long	1073253658
	.long	1187679052
	.long	1073254994
	.long	4232586098
	.long	1073256239
	.long	426690558
	.long	1073257404
	.long	1624065902
	.long	1073258494
	.long	84029729
	.long	1073260006
	.long	700437654
	.long	1073261820
	.long	1437260555
	.long	1073263438
	.long	2813372195
	.long	1073264890
	.long	1607849324
	.long	1073266201
	.long	1011572952
	.long	1073267390
	.long	2039290225
	.long	1073268473
	.long	2435855138
	.long	1073269464
	.long	3389310384
	.long	1073270374
	.long	2790682322
	.long	1073271213
	.long	960266323
	.long	1073271989
	.long	1766929455
	.long	1073272708
	.long	627058932
	.long	1073273377
	.long	2388650377
	.long	1073274000
	.long	422252385
	.long	1073274583
	.long	2848853896
	.long	1073275128
	.long	1413754136
	.long	1073291771
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	2147418112
	.long	0
	.long	2147418112
	.long	0
	.long	32768
	.long	0
	.long	32768
	.long	1413754136
	.long	3221823995
	.long	0
	.long	0
	.long	2576980378
	.long	1070176665
	.long	1431655765
	.long	3218429269
	.long	1431655765
	.long	3218429269
	.long	1431655765
	.long	3218429269
	.long	8388608
	.long	0
	.long	0
	.long	0
	.long	0
	.long	940572672
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	1070141402
	.long	866263400
	.long	1078530010
	.long	874652008
	.long	1061752794
	.long	857874792
	.long	0
	.long	0
	.long	1440057502
	.long	1063257089
	.long	341338964
	.long	1064833554
	.long	4184355568
	.long	1065621801
	.long	2227658503
	.long	1066152307
	.long	449262470
	.long	1066510009
	.long	1570422842
	.long	1066777242
	.long	121841140
	.long	1067045544
	.long	630691842
	.long	1067281191
	.long	2711485097
	.long	1067500943
	.long	2026622491
	.long	1067619660
	.long	766078039
	.long	1067755853
	.long	4074027864
	.long	1067875476
	.long	3056660584
	.long	1067995528
	.long	3195252556
	.long	1068133258
	.long	116861598
	.long	1068254238
	.long	325086471
	.long	1068375655
	.long	296456961
	.long	1068497513
	.long	2623383041
	.long	1068559379
	.long	1681725707
	.long	1068620754
	.long	2231263004
	.long	1068682354
	.long	2815553124
	.long	1068744181
	.long	2057310944
	.long	1068806237
	.long	2095798018
	.long	1068859611
	.long	2302040698
	.long	1068922096
	.long	745232013
	.long	1068984815
	.long	659581512
	.long	1069047769
	.long	1566132617
	.long	1069101918
	.long	550036981
	.long	1069165314
	.long	2887419564
	.long	1069219844
	.long	1990183441
	.long	1069283688
	.long	3220050856
	.long	1069338605
	.long	1669613218
	.long	1069393703
	.long	2145455994
	.long	1069458213
	.long	4275830246
	.long	1069513705
	.long	1392453108
	.long	1069558451
	.long	1469915213
	.long	1069586382
	.long	2183652476
	.long	1069619086
	.long	2109656884
	.long	1069647220
	.long	636640577
	.long	1069675449
	.long	510517079
	.long	1069703773
	.long	210141498
	.long	1069732193
	.long	2537624622
	.long	1069760709
	.long	1733817001
	.long	1069789323
	.long	137854980
	.long	1069813243
	.long	3905576828
	.long	1069842036
	.long	4131810788
	.long	1069870929
	.long	3760956694
	.long	1069899922
	.long	1473082581
	.long	1069929016
	.long	926113142
	.long	1069953338
	.long	3669742467
	.long	1069982617
	.long	3711755290
	.long	1070007095
	.long	311386407
	.long	1070036564
	.long	705364525
	.long	1070066136
	.long	1973375279
	.long	1070090859
	.long	3589645072
	.long	1070115655
	.long	2425772059
	.long	1070145508
	.long	1074899574
	.long	1070170467
	.long	118003300
	.long	1070200516
	.long	691310403
	.long	1070225639
	.long	3273036717
	.long	1070250837
	.long	1226436560
	.long	1070276112
	.long	2481153995
	.long	1070306542
	.long	3227997535
	.long	1070331985
	.long	1421812646
	.long	1070357506
	.long	3386588242
	.long	1070383104
	.long	2580043380
	.long	1070408781
	.long	1068692560
	.long	1070434537
	.long	938143489
	.long	1070460372
	.long	4293333010
	.long	1070486286
	.long	373864817
	.long	1070512282
	.long	4208958430
	.long	1070538357
	.long	783121462
	.long	1070564515
	.long	870975437
	.long	1070590754
	.long	3338886172
	.long	1070606585
	.long	3772343130
	.long	1070619787
	.long	2408245143
	.long	1070630379
	.long	3811383569
	.long	1070643656
	.long	1622638721
	.long	1070656976
	.long	1290798655
	.long	1070670338
	.long	2457529556
	.long	1070681058
	.long	2398194399
	.long	1070694497
	.long	3190801979
	.long	1070707979
	.long	3352241197
	.long	1070718796
	.long	1829681364
	.long	1070732357
	.long	2568927817
	.long	1070743237
	.long	2676427515
	.long	1070756877
	.long	2882402820
	.long	1070767821
	.long	44944622
	.long	1070781542
	.long	2957945300
	.long	1070792550
	.long	1261787704
	.long	1070806352
	.long	1589923677
	.long	1070817426
	.long	3274931829
	.long	1070828529
	.long	1999843716
	.long	1070842450
	.long	1673619518
	.long	1070853620
	.long	601221319
	.long	1070864820
	.long	3763576649
	.long	1070876049
	.long	1974923437
	.long	1070890129
	.long	1096100617
	.long	1070901427
	.long	2424897952
	.long	1070912755
	.long	2376202600
	.long	1070924114
	.long	1665651958
	.long	1070935504
	.long	1014729354
	.long	1070946925
	.long	722545241
	.long	1070961245
	.long	1800682930
	.long	1070972736
	.long	1029453230
	.long	1070984259
	.long	3450874911
	.long	1070995813
	.long	1228255906
	.long	1071007400
	.long	3711032981
	.long	1071019018
	.long	3075102917
	.long	1071030669
	.long	92696812
	.long	1071042353
	.long	3897749690
	.long	1071051136
	.long	1907922160
	.long	1071062877
	.long	4023807324
	.long	1071074650
	.long	2452345726
	.long	1071086457
	.long	2292128226
	.long	1071098297
	.long	58635942
	.long	1071110171
	.long	864186929
	.long	1071122078
	.long	3555691467
	.long	1071131030
	.long	2003684347
	.long	1071142997
	.long	1479703759
	.long	1071154998
	.long	2537208015
	.long	1071164021
	.long	3699909861
	.long	1071176082
	.long	3934098706
	.long	1071188178
	.long	3885764182
	.long	1071197273
	.long	1357790931
	.long	1071209431
	.long	295956059
	.long	1071221624
	.long	4080877676
	.long	1071230791
	.long	202970280
	.long	1071243047
	.long	3977575845
	.long	1071252261
	.long	35977849
	.long	1071264580
	.long	1585496963
	.long	1071273842
	.long	375247257
	.long	1071286224
	.long	1812199574
	.long	1071295534
	.long	1876912769
	.long	1071307980
	.long	1051846544
	.long	1071317339
	.long	979589448
	.long	1071329850
	.long	71706889
	.long	1071339258
	.long	2790898835
	.long	1071351834
	.long	4013797733
	.long	1071361291
	.long	2541231151
	.long	1071370770
	.long	921733145
	.long	1071383442
	.long	508375805
	.long	1071392971
	.long	3091669954
	.long	1071402521
	.long	3343209673
	.long	1071415289
	.long	1693047557
	.long	1071424891
	.long	173861808
	.long	1071434515
	.long	3515822703
	.long	1071444160
	.long	1974837761
	.long	1071457056
	.long	1189018219
	.long	1071466754
	.long	2441946573
	.long	1071476474
	.long	1887102255
	.long	1071486217
	.long	4271031636
	.long	1071495982
	.long	876995759
	.long	1071509039
	.long	438773454
	.long	1071518858
	.long	172621632
	.long	1071528700
	.long	544035182
	.long	1071538565
	.long	2021801298
	.long	1071548453
	.long	783063308
	.long	1071558365
	.long	1598254051
	.long	1071568300
	.long	651258553
	.long	1071578259
	.long	2719315456
	.long	1071588241
	.long	3993180461
	.long	1071598247
	.long	667093936
	.long	1071608278
	.long	1823716217
	.long	1071618332
	.long	3664357648
	.long	1071628410
	.long	2393914686
	.long	1071638513
	.long	1405419566
	.long	1071646656
	.long	563652001
	.long	1071651732
	.long	1074554091
	.long	1071656820
	.long	3195405229
	.long	1071661920
	.long	2890399284
	.long	1071667033
	.long	420597586
	.long	1071672159
	.long	87278952
	.long	1071675583
	.long	1753119502
	.long	1071680729
	.long	1959759897
	.long	1071685888
	.long	975389815
	.long	1071691060
	.long	3365155278
	.long	1071696244
	.long	811309190
	.long	1071701442
	.long	1269811480
	.long	1071704914
	.long	1108583542
	.long	1071710133
	.long	1034517919
	.long	1071715365
	.long	1327340287
	.long	1071720610
	.long	1388251237
	.long	1071724114
	.long	1488234797
	.long	1071729381
	.long	2711385691
	.long	1071734661
	.long	952179194
	.long	1071738189
	.long	3255685427
	.long	1071743491
	.long	3159039803
	.long	1071748807
	.long	951558972
	.long	1071752359
	.long	3234424125
	.long	1071757697
	.long	3904573547
	.long	1071763049
	.long	2648729924
	.long	1071766625
	.long	2729250212
	.long	1071772000
	.long	2000712574
	.long	1071777389
	.long	3125257108
	.long	1071780989
	.long	3160324803
	.long	1071786401
	.long	2216316974
	.long	1071790017
	.long	3875016090
	.long	1071795452
	.long	1439520610
	.long	1071799084
	.long	1298103361
	.long	1071804543
	.long	2217517956
	.long	1071810016
	.long	1191753587
	.long	1071813673
	.long	1728773900
	.long	1071819170
	.long	747445441
	.long	1071822843
	.long	1670772213
	.long	1071826522
	.long	1471209283
	.long	1071832053
	.long	3287905877
	.long	1071835748
	.long	594038131
	.long	1071841304
	.long	3920147917
	.long	1071845015
	.long	3957142762
	.long	1071850595
	.long	826988438
	.long	1071854324
	.long	242509008
	.long	1071859929
	.long	4173610350
	.long	1071863673
	.long	2518313625
	.long	1071867425
	.long	2671523434
	.long	1071873065
	.long	387544807
	.long	1071876834
	.long	1470497767
	.long	1071880609
	.long	4269259217
	.long	1071886284
	.long	1345663925
	.long	1071890077
	.long	2159323299
	.long	1071893876
	.long	957937516
	.long	1071899588
	.long	2994656967
	.long	1071903404
	.long	556124819
	.long	1071907228
	.long	1631098658
	.long	1071912976
	.long	1368257861
	.long	1071916817
	.long	1309849279
	.long	1071920665
	.long	1567156841
	.long	1071924520
	.long	1179420986
	.long	1071930316
	.long	569448444
	.long	1071934189
	.long	669557459
	.long	1071938069
	.long	1593834700
	.long	1071941956
	.long	3166083125
	.long	1071947800
	.long	211641815
	.long	1071951706
	.long	2780675719
	.long	1071955618
	.long	2400234567
	.long	1071959538
	.long	3482927192
	.long	1071963465
	.long	1852089988
	.long	1071967400
	.long	1020849000
	.long	1071973316
	.long	1718090839
	.long	1071977269
	.long	121152472
	.long	1071981230
	.long	646346839
	.long	1071985198
	.long	3415709705
	.long	1071989173
	.long	4257004898
	.long	1071993156
	.long	3293664199
	.long	1071997147
	.long	649825382
	.long	1072001146
	.long	248887552
	.long	1072007158
	.long	2541522737
	.long	1072011175
	.long	3593780107
	.long	1072015200
	.long	3533041935
	.long	1072019233
	.long	2487426809
	.long	1072023274
	.long	585795318
	.long	1072027323
	.long	2252723088
	.long	1072031379
	.long	3323604690
	.long	1072035443
	.long	3929561382
	.long	1072039515
	.long	4202479723
	.long	1072043595
	.long	4275017539
	.long	1072047683
	.type	static_const_table,@object
	.size	static_const_table,7024
	.data
	.section .note.GNU-stack, ""
# End
