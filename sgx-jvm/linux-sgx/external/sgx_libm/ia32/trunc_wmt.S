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
	.file "trunc_wmt.c"
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
# -- Begin  trunc
	.text
       .align    16,0x90
	.globl trunc
trunc:
# parameter 1: 8 + %ebp
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
        movq      (%ebx), %xmm2
        movdqa    %xmm0, %xmm1
        psrlq     $52, %xmm1
        movq      8(%ebx), %xmm3
        movd      %xmm1, %eax
        andl      $2047, %eax
        subl      $1023, %eax
        js        .L_2TAG_PACKET_0.0.2
        cmpl      $52, %eax
        jae       .L_2TAG_PACKET_1.0.2
        pand      %xmm3, %xmm1
        psubd     %xmm1, %xmm2
        psrlq     %xmm2, %xmm0
        psllq     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_0.0.2:
        movsd     112(%esp), %xmm0
        movq      16(%ebx), %xmm1
        pand      %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_1.0.2:
        fldl      112(%esp)
        fld1      
        fxch      %st(1)
        fmulp     %st, %st(1)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
        movsd     %xmm0, 24(%esp)
        fldl      24(%esp)
.L_2TAG_PACKET_3.0.2:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	trunc,@function
	.size	trunc,.-trunc
	.data
# -- End  trunc
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1075
	.long	0
	.long	2047
	.long	0
	.long	0
	.long	2147483648
	.type	static_const_table,@object
	.size	static_const_table,24
	.data
	.section .note.GNU-stack, ""
# End
