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
	.file "llroundf_wmt.c"
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
# -- Begin  llroundf
	.text
       .align    16,0x90
	.globl llroundf
llroundf:
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
        movl      16(%esp), %eax
        movl      $2147483647, %edx
        andl      %eax, %edx
        cmpl      $1258291200, %edx
        jge       .L_2TAG_PACKET_0.0.3
        cmpl      $1065353216, %edx
        jl        .L_2TAG_PACKET_1.0.3
        shrl      $23, %edx
        movl      $149, %ecx
        subl      %edx, %ecx
        movl      $8388607, %edx
        andl      %eax, %edx
        shrl      %cl, %eax
        orl       $8388608, %edx
        andl      $1, %eax
        shrl      %cl, %edx
        movl      $-2147483648, %ecx
        shrl      $1, %edx
        xorl      16(%esp), %ecx
        addl      %edx, %eax
        movl      16(%esp), %edx
        sarl      $31, %ecx
        sarl      $31, %edx
        andl      %eax, %edx
        andl      %ecx, %eax
        subl      %edx, %eax
        movl      16(%esp), %edx
        sarl      $31, %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        cmpl      $-553648128, %eax
        ja        .L_2TAG_PACKET_3.0.3
        cmpl      $1593835520, %eax
        jge       .L_2TAG_PACKET_3.0.3
        flds      16(%esp)
        subl      $8, %esp
        fistpll   (%esp)
        popl      %eax
        popl      %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        ucomiss   %xmm0, %xmm0
        subl      $1056964608, %edx
        xorl      $-2147483648, %eax
        movl      $1, %ecx
        shrl      $31, %edx
        shrl      $31, %eax
        xorl      %edx, %ecx
        orl       %eax, %edx
        andl      %ecx, %eax
        addl      %edx, %eax
        movl      $-1, %edx
        addl      %eax, %edx
        subl      $1, %eax
        sarl      $31, %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        comiss    %xmm0, %xmm0
        jp        .L_2TAG_PACKET_4.0.3
        pcmpeqd   %xmm1, %xmm1
        pslld     $23, %xmm1
        subss     %xmm1, %xmm1
        movl      $-2147483648, (%esp)
        movl      $0, 4(%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      $194, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        movl      (%esp), %eax
        movl      4(%esp), %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_4.0.3:
        movl      $-2147483648, %eax
.L_2TAG_PACKET_2.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	llroundf,@function
	.size	llroundf,.-llroundf
	.data
# -- End  llroundf
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
	.type	static_const_table,@object
	.size	static_const_table,4
	.data
	.section .note.GNU-stack, ""
# End
