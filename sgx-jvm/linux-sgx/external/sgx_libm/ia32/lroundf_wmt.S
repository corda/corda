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
	.file "lroundf_wmt.c"
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
# -- Begin  lroundf
	.text
       .align    16,0x90
	.globl lroundf
lroundf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 64(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     128(%esp), %xmm0
        movss     %xmm0, 16(%esp)
        movd      16(%ebx), %xmm4
        movd      64(%ebx), %xmm5
        movd      112(%ebx), %xmm3
        movd      (%ebx), %xmm1
        movdqa    %xmm4, %xmm2
        pandn     %xmm0, %xmm4
        pand      %xmm0, %xmm2
        por       %xmm5, %xmm2
        pextrw    $1, %xmm0, %eax
        andl      $32640, %eax
        subl      $16256, %eax
        cmpl      $2944, %eax
        jae       .L_2TAG_PACKET_0.0.3
        psrld     $23, %xmm4
        psubd     %xmm4, %xmm3
        pslld     %xmm3, %xmm1
        addss     %xmm2, %xmm0
        pand      %xmm1, %xmm0
        cvttss2si %xmm0, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_0.0.3:
        js        .L_2TAG_PACKET_2.0.3
        cmpl      $3840, %eax
        jae       .L_2TAG_PACKET_3.0.3
        cvttss2si %xmm0, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_3.0.3:
        movdqa    %xmm2, %xmm6
        movd      32(%ebx), %xmm7
        psrld     $31, %xmm6
        paddd     %xmm6, %xmm7
        cmpl      $4096, %eax
        jge       .L_2TAG_PACKET_4.0.3
        movd      96(%ebx), %xmm5
        movd      80(%ebx), %xmm2
        psrld     $23, %xmm4
        pand      %xmm0, %xmm5
        psubd     %xmm3, %xmm4
        por       %xmm2, %xmm5
        movd      %xmm6, %edx
        psllq     %xmm4, %xmm5
        movd      %xmm5, %eax
        psubd     %xmm5, %xmm7
        pmovmskb  %xmm7, %ecx
        andl      $136, %ecx
        jne       .L_2TAG_PACKET_4.0.3
        testl     %edx, %edx
        je        .L_2TAG_PACKET_5.0.3
        negl      %eax
.L_2TAG_PACKET_5.0.3:
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_2.0.3:
        addl      $128, %eax
        js        .L_2TAG_PACKET_6.0.3
        addss     %xmm2, %xmm2
        cvttss2si %xmm2, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_6.0.3:
        xorl      %eax, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_4.0.3:
        movd      48(%ebx), %xmm1
        movl      $-2147483648, %eax
        pxor      %xmm2, %xmm2
        movd      %eax, %xmm7
        mulss     %xmm2, %xmm1
        ucomiss   %xmm0, %xmm0
        jc        .L_2TAG_PACKET_7.0.3
        movq      %xmm7, 8(%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       40(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $191, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        movl      8(%esp), %eax
.L_2TAG_PACKET_7.0.3:
.L_2TAG_PACKET_1.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	lroundf,@function
	.size	lroundf,.-lroundf
	.data
# -- End  lroundf
	.text
# -- Begin  __libm_error_support
	.text
       .align    16,0x90
__libm_error_support:
# parameter 1: 4 + %esp
# parameter 2: 8 + %esp
# parameter 3: 12 + %esp
# parameter 4: 16 + %esp
..B3.1:
..L4:

        ret       
        .align    16,0x90
	.type	__libm_error_support,@function
	.size	__libm_error_support,.-__libm_error_support
	.data
# -- End  __libm_error_support
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	2147483648
	.long	2147483648
	.long	2147483648
	.long	2147483648
	.long	2147483647
	.long	2147483647
	.long	2147483647
	.long	2147483647
	.long	2139095040
	.long	2139095040
	.long	2139095040
	.long	2139095040
	.long	1056964608
	.long	1056964608
	.long	1056964608
	.long	1056964608
	.long	8388608
	.long	8388608
	.long	8388608
	.long	8388608
	.long	8388607
	.long	8388607
	.long	8388607
	.long	8388607
	.long	150
	.long	150
	.long	150
	.long	150
	.type	static_const_table,@object
	.size	static_const_table,128
	.data
	.section .note.GNU-stack, ""
# End
