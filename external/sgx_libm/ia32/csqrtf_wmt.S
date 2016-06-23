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
	.file "csqrtf_wmt.c"
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
# -- Begin  csqrtf
	.text
       .align    16,0x90
	.globl csqrtf
csqrtf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $216, %esp
        movl      %ebx, 160(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     224(%esp), %xmm0
        movss     228(%esp), %xmm1
        movss     %xmm0, (%esp)
        movss     %xmm1, 4(%esp)
        movl      (%esp), %eax
        movl      4(%esp), %ecx
        unpcklps  %xmm1, %xmm0
        movl      %ecx, %edx
        andl      $2139095040, %eax
        andl      $2139095040, %ecx
        subl      $8388608, %eax
        subl      $8388608, %ecx
        andl      $2139095040, %eax
        andl      $2139095040, %ecx
        subl      $2130706432, %eax
        subl      $2130706432, %ecx
        testl     %ecx, %eax
        jns       .L_2TAG_PACKET_0.0.2
        cvtps2pd  %xmm0, %xmm0
        pxor      %xmm4, %xmm4
        movl      $16, %eax
        movapd    %xmm0, %xmm1
        unpckhpd  %xmm1, %xmm1
        movapd    %xmm0, %xmm7
        mulsd     %xmm0, %xmm0
        movapd    %xmm7, %xmm6
        mulsd     %xmm1, %xmm1
        pinsrw    $3, %eax, %xmm4
        addsd     %xmm1, %xmm0
        andpd     (%ebx), %xmm7
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm7, %xmm0
        psubd     %xmm4, %xmm0
        movsd     %xmm0, %xmm7
        movdqa    %xmm0, %xmm1
        pand      16(%ebx), %xmm0
        movdqa    %xmm1, %xmm2
        paddd     32(%ebx), %xmm0
        psrld     $1, %xmm1
        psrlq     $29, %xmm0
        pand      48(%ebx), %xmm1
        rsqrtss   %xmm0, %xmm0
        psubd     64(%ebx), %xmm1
        psllq     $29, %xmm0
        movapd    80(%ebx), %xmm3
        psubd     %xmm1, %xmm0
        movapd    96(%ebx), %xmm1
        mulsd     %xmm0, %xmm2
        movapd    48(%ebx), %xmm4
        mulsd     %xmm0, %xmm2
        subsd     %xmm4, %xmm2
        mulsd     %xmm2, %xmm3
        addsd     %xmm1, %xmm3
        mulsd     %xmm2, %xmm3
        mulsd     %xmm0, %xmm3
        addsd     %xmm3, %xmm0
        mulpd     112(%ebx), %xmm7
        unpcklpd  %xmm0, %xmm0
.L_2TAG_PACKET_1.0.2:
        pextrw    $3, %xmm6, %eax
        mulpd     %xmm7, %xmm0
        andl      $-2147483648, %edx
        cvtpd2ps  %xmm0, %xmm1
        testl     $32768, %eax
        pshufd    $17, %xmm1, %xmm2
        je        .L_2TAG_PACKET_2.0.2
        movd      %xmm1, %ecx
        movd      %xmm2, %eax
        orl       %ecx, %edx
        testl     $2139095040, %eax
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
        movd      %xmm2, %ecx
        movd      %xmm1, %eax
        orl       %ecx, %edx
        testl     $2139095040, %ecx
.L_2TAG_PACKET_3.0.2:
        je        .L_2TAG_PACKET_4.0.2
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_4.0.2:
        testl     $2147483647, 4(%esp)
        jne       .L_2TAG_PACKET_6.0.2
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_6.0.2:
        movhpd    %xmm0, 112(%esp)
        testl     $2139095040, %eax
        fldl      112(%esp)
        fstps     112(%esp)
        je        .L_2TAG_PACKET_7.0.2
        andl      $-2147483648, %edx
        orl       112(%esp), %edx
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_7.0.2:
        movl      112(%esp), %eax
.L_2TAG_PACKET_8.0.2:
        movl      $8388608, 116(%esp)
        flds      112(%esp)
        fmul      %st(0), %st
        fstps     112(%esp)
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_0.0.2:
        movdqa    %xmm0, %xmm2
        movdqa    128(%ebx), %xmm4
        pshufd    $80, %xmm0, %xmm0
        pxor      %xmm5, %xmm5
        movdqa    %xmm2, %xmm3
        pand      144(%ebx), %xmm0
        pshufd    $115, %xmm2, %xmm2
        pcmpeqd   %xmm4, %xmm0
        movdqa    %xmm2, %xmm6
        movmskps  %xmm0, %eax
        pand      %xmm2, %xmm4
        testl     %eax, %eax
        jne       .L_2TAG_PACKET_9.0.2
        pxor      %xmm0, %xmm0
.L_2TAG_PACKET_10.0.2:
        pand      (%ebx), %xmm2
        pcmpeqd   %xmm5, %xmm4
        movdqa    %xmm4, %xmm3
        pand      160(%ebx), %xmm4
        psrlq     $3, %xmm2
        pand      176(%ebx), %xmm3
        por       %xmm4, %xmm2
        paddd     192(%ebx), %xmm3
        subpd     %xmm4, %xmm2
        paddd     %xmm3, %xmm2
        pandn     %xmm2, %xmm0
        pxor      %xmm4, %xmm4
        movl      $16, %eax
        movapd    %xmm0, %xmm1
        unpckhpd  %xmm1, %xmm1
        movapd    %xmm0, %xmm7
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        pinsrw    $3, %eax, %xmm4
        addsd     %xmm1, %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm7, %xmm0
        psubd     %xmm4, %xmm0
        movsd     %xmm0, %xmm7
        sqrtsd    %xmm0, %xmm1
        movapd    48(%ebx), %xmm0
        divsd     %xmm1, %xmm0
        mulpd     112(%ebx), %xmm7
        unpcklpd  %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_9.0.2:
        cmpl      $5, %eax
        je        .L_2TAG_PACKET_11.0.2
        testl     $10, %eax
        jne       .L_2TAG_PACKET_12.0.2
        pshufd    $160, %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_10.0.2
.L_2TAG_PACKET_11.0.2:
        xorl      %eax, %eax
        andl      $-2147483648, %edx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_12.0.2:
        movl      %edx, %ecx
        andl      $2147483647, %ecx
        movd      %xmm3, %eax
        cmpl      $2139095040, %ecx
        je        .L_2TAG_PACKET_13.0.2
        ja        .L_2TAG_PACKET_14.0.2
        andl      $-2147483648, %edx
        cmpl      $-8388608, %eax
        je        .L_2TAG_PACKET_15.0.2
        cmpl      $2139095040, %eax
        jne       .L_2TAG_PACKET_16.0.2
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_15.0.2:
        xorl      %eax, %eax
        orl       $2139095040, %edx
        jmp       .L_2TAG_PACKET_5.0.2
.L_2TAG_PACKET_14.0.2:
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_17.0.2
        cmpl      $-8388608, %eax
        je        .L_2TAG_PACKET_18.0.2
.L_2TAG_PACKET_16.0.2:
        mulss     %xmm3, %xmm1
        flds      (%esp)
        flds      4(%esp)
        movd      %xmm1, %eax
        movl      %eax, %edx
        jmp       .L_2TAG_PACKET_19.0.2
.L_2TAG_PACKET_17.0.2:
        flds      4(%esp)
        flds      4(%esp)
        orl       $4194304, %edx
        jmp       .L_2TAG_PACKET_19.0.2
.L_2TAG_PACKET_18.0.2:
        flds      4(%esp)
        flds      4(%esp)
        movl      %edx, %eax
        movl      $2139095040, %edx
        orl       $4194304, %eax
        jmp       .L_2TAG_PACKET_19.0.2
.L_2TAG_PACKET_13.0.2:
        flds      (%esp)
        fld1      
        movl      $2139095040, %eax
.L_2TAG_PACKET_19.0.2:
        fmulp     
        fstp      %st(0)
.L_2TAG_PACKET_5.0.2:
        movl      160(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	csqrtf,@function
	.size	csqrtf,.-csqrtf
	.data
# -- End  csqrtf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2097151
	.long	4294967295
	.long	2097151
	.long	0
	.long	132120576
	.long	0
	.long	132120576
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	1475346432
	.long	0
	.long	1475346432
	.long	2148429837
	.long	1071120401
	.long	2148429837
	.long	1071120401
	.long	195330
	.long	3219128325
	.long	195330
	.long	3219128325
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	0
	.long	2139095040
	.long	0
	.long	2139095040
	.long	2147483647
	.long	2139095040
	.long	2147483647
	.long	2139095040
	.long	0
	.long	133169152
	.long	0
	.long	133169152
	.long	0
	.long	4162846720
	.long	0
	.long	4162846720
	.long	0
	.long	939524096
	.long	0
	.long	939524096
	.type	static_const_table,@object
	.size	static_const_table,208
	.data
	.section .note.GNU-stack, ""
# End
