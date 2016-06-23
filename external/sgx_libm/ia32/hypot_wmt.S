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
	.file "hypot_wmt.c"
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
# -- Begin  hypot
	.text
       .align    16,0x90
	.globl hypot
hypot:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $152, %esp
        movl      %ebx, 96(%esp)
        call      static_func
        movl      %eax, %ebx
        movapd    (%ebx), %xmm3
        movsd     160(%esp), %xmm0
        movsd     168(%esp), %xmm1
        andpd     %xmm3, %xmm0
        andpd     %xmm3, %xmm1
        pextrw    $3, %xmm0, %eax
        pextrw    $3, %xmm1, %edx
        cmpl      $24528, %eax
        ja        .L_2TAG_PACKET_0.0.3
        cmpl      $24528, %edx
        ja        .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_1.0.3:
        fldl      160(%esp)
        fldl      168(%esp)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(1)
        nop       
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fsqrt     
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        cmpl      $32752, %eax
        movl      %eax, %ecx
        jae       .L_2TAG_PACKET_3.0.3
        subl      %edx, %ecx
        cmpl      $32752, %edx
        jae       .L_2TAG_PACKET_3.0.3
        addl      $928, %ecx
        addl      %edx, %eax
        cmpl      $1856, %ecx
        ja        .L_2TAG_PACKET_4.0.3
        cmpl      $49056, %eax
        jb        .L_2TAG_PACKET_1.0.3
        fldl      160(%esp)
        fldl      168(%esp)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(1)
        nop       
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fsqrt     
.L_2TAG_PACKET_5.0.3:
        fstl      (%esp)
        fstpt     16(%esp)
        xorl      %eax, %eax
        movw      24(%esp), %ax
        cmpl      $17407, %eax
        jae       .L_2TAG_PACKET_6.0.3
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_4.0.3:
        movsd     %xmm0, 32(%esp)
        movsd     %xmm1, 40(%esp)
        fldl      32(%esp)
        faddl     40(%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_6.0.3:
        movl      $46, %edx
.L_2TAG_PACKET_8.0.3:
        movsd     160(%esp), %xmm0
        movsd     168(%esp), %xmm1
        subl      $32, %esp
        lea       160(%esp), %eax
        movl      %eax, (%esp)
        lea       168(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_3.0.3:
        shufpd    $0, %xmm1, %xmm0
        movdqa    %xmm0, %xmm2
        movdqa    16(%ebx), %xmm3
        movsd     %xmm0, 32(%esp)
        movsd     %xmm1, 40(%esp)
        cmppd     $3, %xmm0, %xmm2
        cmppd     $0, %xmm0, %xmm3
        movmskpd  %xmm2, %edx
        movmskpd  %xmm3, %eax
        testl     %edx, %edx
        je        .L_2TAG_PACKET_9.0.3
        fldl      32(%esp)
        fmull     40(%esp)
        testl     $1, %eax
        jne       .L_2TAG_PACKET_10.0.3
        testl     $2, %eax
        jne       .L_2TAG_PACKET_11.0.3
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_9.0.3:
        fldl      32(%esp)
        faddl     40(%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_10.0.3:
        fstpl     40(%esp)
        fldl      32(%esp)
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_11.0.3:
        fstpl     32(%esp)
        fldl      40(%esp)
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_2.0.3:
.L_2TAG_PACKET_7.0.3:
        movl      96(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	hypot,@function
	.size	hypot,.-hypot
	.data
# -- End  hypot
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
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.type	static_const_table,@object
	.size	static_const_table,32
	.data
	.section .note.GNU-stack, ""
# End
