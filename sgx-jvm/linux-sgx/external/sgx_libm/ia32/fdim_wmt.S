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
	.file "fdim_wmt.c"
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
# -- Begin  fdim
	.text
       .align    16,0x90
	.globl fdim
fdim:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 48(%esp)
        call      static_func
        movl      %eax, %ebx
        stmxcsr   8(%esp)
        movsd     112(%esp), %xmm0
        movsd     120(%esp), %xmm1
        movapd    %xmm0, %xmm2
        ucomisd   %xmm0, %xmm1
        movl      8(%esp), %eax
        jp        .L_2TAG_PACKET_0.0.3
        testl     $8, %eax
        cmpnlesd  %xmm1, %xmm0
        andpd     %xmm0, %xmm1
        andpd     %xmm2, %xmm0
        jne       .L_2TAG_PACKET_1.0.3
        subsd     %xmm1, %xmm0
        andpd     (%ebx), %xmm0
        pextrw    $3, %xmm0, %eax
        cmpl      $32751, %eax
        jb        .L_2TAG_PACKET_2.0.3
        stmxcsr   12(%esp)
        movl      12(%esp), %eax
        testl     $8, %eax
        jne       .L_2TAG_PACKET_3.0.3
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        andl      $-9, %eax
        movl      %eax, 12(%esp)
        ldmxcsr   12(%esp)
        cmpnlesd  %xmm1, %xmm0
        andpd     %xmm0, %xmm1
        andpd     %xmm2, %xmm0
        subsd     %xmm1, %xmm0
        andpd     (%ebx), %xmm0
        stmxcsr   12(%esp)
        movl      12(%esp), %eax
        testl     $8, %eax
        jne       .L_2TAG_PACKET_3.0.3
        ldmxcsr   8(%esp)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        fldl      112(%esp)
        fldl      120(%esp)
        faddp     %st, %st(1)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_3.0.3:
        movl      $196, %edx
.L_2TAG_PACKET_5.0.3:
        movsd     %xmm0, (%esp)
        movsd     112(%esp), %xmm0
        movsd     120(%esp), %xmm1
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       120(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_2.0.3:
        movsd     %xmm0, 32(%esp)
        fldl      32(%esp)
.L_2TAG_PACKET_4.0.3:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	fdim,@function
	.size	fdim,.-fdim
	.data
# -- End  fdim
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
	.type	static_const_table,@object
	.size	static_const_table,16
	.data
	.section .note.GNU-stack, ""
# End
