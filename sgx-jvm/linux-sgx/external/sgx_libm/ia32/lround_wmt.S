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
	.file "lround_wmt.c"
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
# -- Begin  lround
	.text
       .align    16,0x90
	.globl lround
lround:
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
        movsd     128(%esp), %xmm0
        movq      %xmm0, 16(%esp)
        movq      16(%ebx), %xmm4
        movq      64(%ebx), %xmm5
        movq      96(%ebx), %xmm3
        movq      (%ebx), %xmm1
        movdqa    %xmm4, %xmm2
        pandn     %xmm0, %xmm4
        pand      %xmm0, %xmm2
        por       %xmm5, %xmm2
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        subl      $16368, %eax
        cmpl      $480, %eax
        jae       .L_2TAG_PACKET_0.0.3
        psrlq     $52, %xmm4
        psubq     %xmm4, %xmm3
        psllq     %xmm3, %xmm1
        addsd     %xmm2, %xmm0
        pand      %xmm1, %xmm0
        cvttsd2si %xmm0, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_0.0.3:
        js        .L_2TAG_PACKET_2.0.3
        movdqa    %xmm2, %xmm6
        movq      80(%ebx), %xmm7
        psrlq     $63, %xmm6
        paddq     %xmm6, %xmm7
        cmpl      $512, %eax
        jge       .L_2TAG_PACKET_3.0.3
        movq      48(%ebx), %xmm2
        addsd     %xmm4, %xmm5
        psrlq     $52, %xmm4
        psubq     %xmm4, %xmm3
        movd      %xmm6, %edx
        psllq     %xmm3, %xmm1
        pand      %xmm1, %xmm5
        addsd     %xmm2, %xmm5
        por       %xmm7, %xmm2
        movd      %xmm5, %eax
        psubd     %xmm5, %xmm2
        pmovmskb  %xmm2, %ecx
        andl      $136, %ecx
        jne       .L_2TAG_PACKET_3.0.3
        testl     %edx, %edx
        je        .L_2TAG_PACKET_4.0.3
        negl      %eax
.L_2TAG_PACKET_4.0.3:
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_2.0.3:
        addl      $16, %eax
        js        .L_2TAG_PACKET_5.0.3
        addsd     %xmm2, %xmm2
        cvttsd2si %xmm2, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_5.0.3:
        xorl      %eax, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_3.0.3:
        movq      32(%ebx), %xmm1
        movl      $-2147483648, %eax
        pxor      %xmm2, %xmm2
        movd      %eax, %xmm7
        mulsd     %xmm2, %xmm1
        ucomisd   %xmm0, %xmm0
        jc        .L_2TAG_PACKET_6.0.3
        movq      %xmm7, 8(%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       40(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $190, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        movl      8(%esp), %eax
.L_2TAG_PACKET_6.0.3:
.L_2TAG_PACKET_1.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	lround,@function
	.size	lround,.-lround
	.data
# -- End  lround
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
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	2147483647
	.long	0
	.long	2147483647
	.long	0
	.long	1075
	.long	0
	.long	1075
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,112
	.data
	.section .note.GNU-stack, ""
# End
