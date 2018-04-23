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
	.file "nextafter_wmt.c"
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
# -- Begin  nextafter
	.text
       .align    16,0x90
	.globl nextafter
nextafter:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 40(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     112(%esp), %xmm0
        movsd     120(%esp), %xmm1
        ucomisd   %xmm1, %xmm0
        jp        .L_2TAG_PACKET_0.0.3
        je        .L_2TAG_PACKET_1.0.3
        cmpltsd   %xmm0, %xmm1
        comisd    (%ebx), %xmm0
        je        .L_2TAG_PACKET_2.0.3
        xorpd     %xmm0, %xmm1
        paddq     48(%ebx), %xmm0
        psrlq     $63, %xmm1
        psllq     $1, %xmm1
        psubq     %xmm1, %xmm0
        pextrw    $3, %xmm0, %eax
        andl      $32767, %eax
        subl      $16, %eax
        cmpl      $32736, %eax
        jae       .L_2TAG_PACKET_3.0.3
        movsd     %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_0.0.3:
        movsd     %xmm1, (%esp)
        fldl      (%esp)
        movsd     %xmm0, (%esp)
        faddl     (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_1.0.3:
        movsd     %xmm1, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_2.0.3:
        movsd     16(%ebx), %xmm0
        andpd     32(%ebx), %xmm1
        orpd      %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_3.0.3:
        testl     %eax, %eax
        jns       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_5.0.3:
        movsd     80(%ebx), %xmm1
        mulsd     %xmm1, %xmm1
        movsd     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       120(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $268, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_6.0.3:
        movsd     64(%ebx), %xmm1
        mulsd     %xmm1, %xmm1
        movsd     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       120(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $154, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_4.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	nextafter,@function
	.size	nextafter,.-nextafter
	.data
# -- End  nextafter
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
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	1
	.long	0
	.long	0
	.long	2121269248
	.long	1
	.long	0
	.long	0
	.long	24117248
	.long	1
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,96
	.data
	.section .note.GNU-stack, ""
# End
