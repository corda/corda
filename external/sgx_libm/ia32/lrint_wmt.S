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
	.file "lrint_wmt.c"
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
# -- Begin  lrint
	.text
       .align    16,0x90
	.globl lrint
lrint:
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
        movsd     %xmm0, 16(%esp)
        pextrw    $3, %xmm0, %ecx
        andl      $32767, %ecx
        cmpl      $16863, %ecx
        jge       .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_1.0.3:
        cvtsd2si  %xmm0, %eax
        addsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        comisd    (%ebx), %xmm0
        jp        .L_2TAG_PACKET_3.0.3
        ja        .L_2TAG_PACKET_4.0.3
        ucomisd   16(%ebx), %xmm0
        jb        .L_2TAG_PACKET_5.0.3
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_5.0.3:
        ucomisd   32(%ebx), %xmm0
        ja        .L_2TAG_PACKET_6.0.3
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_4.0.3:
        ucomisd   48(%ebx), %xmm0
        jb        .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_7.0.3:
        movl      $-2147483648, %eax
        movss     64(%ebx), %xmm1
        subss     %xmm1, %xmm1
        movl      %eax, 8(%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       40(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $184, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        movl      8(%esp), %eax
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        movl      $-2147483648, %eax
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_6.0.3:
        stmxcsr   (%esp)
        movl      (%esp), %eax
        andl      $-2, %eax
        movl      %eax, (%esp)
        ldmxcsr   (%esp)
        cvtsd2si  %xmm0, %eax
        stmxcsr   (%esp)
        movl      (%esp), %edx
        testl     $1, %edx
        jne       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_2.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	lrint,@function
	.size	lrint,.-lrint
	.data
# -- End  lrint
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
	.long	4290772992
	.long	1105199103
	.long	4290772992
	.long	1105199103
	.long	0
	.long	3252682752
	.long	0
	.long	3252682752
	.long	2097152
	.long	3252682752
	.long	2097152
	.long	3252682752
	.long	0
	.long	1105199104
	.long	0
	.long	1105199104
	.long	2139095040
	.long	2139095040
	.long	2139095040
	.long	2139095040
	.type	static_const_table,@object
	.size	static_const_table,80
	.data
	.section .note.GNU-stack, ""
# End
