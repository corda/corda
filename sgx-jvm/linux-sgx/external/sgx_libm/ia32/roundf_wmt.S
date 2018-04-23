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
	.file "roundf_wmt.c"
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
# -- Begin  roundf
	.text
       .align    16,0x90
	.globl roundf
roundf:
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
        movss     112(%esp), %xmm0
        movl      112(%esp), %eax
        testl     %eax, %eax
        jl        .L_2TAG_PACKET_0.0.2
        cmpl      $1056964608, %eax
        jl        .L_2TAG_PACKET_1.0.2
        cmpl      $1258291200, %eax
        jge       .L_2TAG_PACKET_2.0.2
        cmpl      $1065353216, %eax
        je        .L_2TAG_PACKET_2.0.2
        addss     (%ebx), %xmm0
        cvttss2si %xmm0, %eax
        movl      %eax, 24(%esp)
        fildl     24(%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_1.0.2:
        fldz      
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_4.0.2:
        flds      4(%ebx)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_0.0.2:
        andl      $2147483647, %eax
        cmpl      $1056964608, %eax
        jl        .L_2TAG_PACKET_4.0.2
        cmpl      $1258291200, %eax
        jge       .L_2TAG_PACKET_2.0.2
        cmpl      $1065353216, %eax
        je        .L_2TAG_PACKET_2.0.2
        subss     (%ebx), %xmm0
        cvttss2si %xmm0, %eax
        movl      %eax, 24(%esp)
        fildl     24(%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
.L_2TAG_PACKET_3.0.2:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	roundf,@function
	.size	roundf,.-roundf
	.data
# -- End  roundf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	1056964608
	.long	2147483648
	.type	static_const_table,@object
	.size	static_const_table,8
	.data
	.section .note.GNU-stack, ""
# End
