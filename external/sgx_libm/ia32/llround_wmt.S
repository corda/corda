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
	.file "llround_wmt.c"
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
# -- Begin  llround
	.text
       .align    16,0x90
	.globl llround
llround:
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
        pextrw    $3, %xmm0, %eax
        movl      $32767, %edx
        andl      %eax, %edx
        cmpl      $17200, %edx
        jge       .L_2TAG_PACKET_0.0.3
        cmpl      $16368, %edx
        jl        .L_2TAG_PACKET_1.0.3
        movapd    %xmm0, %xmm5
        movapd    112(%ebx), %xmm4
        pxor      %xmm6, %xmm6
        pand      %xmm0, %xmm4
        movapd    %xmm0, %xmm1
        psrlq     $52, %xmm0
        cmpltsd   %xmm6, %xmm5
        pand      64(%ebx), %xmm0
        movss     80(%ebx), %xmm2
        movss     96(%ebx), %xmm3
        pcmpeqd   %xmm6, %xmm6
        psubd     %xmm0, %xmm2
        psubd     %xmm0, %xmm3
        psrlq     %xmm3, %xmm1
        por       128(%ebx), %xmm4
        pand      144(%ebx), %xmm1
        psrlq     %xmm2, %xmm4
        pxor      %xmm5, %xmm6
        paddq     %xmm1, %xmm4
        pand      %xmm4, %xmm5
        pand      %xmm4, %xmm6
        psubq     %xmm5, %xmm6
        movd      %xmm6, %eax
        psrlq     $32, %xmm6
        movd      %xmm6, %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        comisd    16(%ebx), %xmm0
        jc        .L_2TAG_PACKET_3.0.3
        comisd    (%ebx), %xmm0
        jnc       .L_2TAG_PACKET_3.0.3
.L_2TAG_PACKET_4.0.3:
        fldl      16(%esp)
        subl      $8, %esp
        fistpll   (%esp)
        popl      %eax
        popl      %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        subl      $16352, %edx
        xorl      $32768, %eax
        movl      $1, %ecx
        shrl      $31, %edx
        shrl      $15, %eax
        xorl      %edx, %ecx
        orl       %eax, %edx
        andl      %ecx, %eax
        shll      $31, %ecx
        addl      %edx, %eax
        movl      20(%esp), %edx
        subl      $1, %eax
        andl      %ecx, %edx
        sarl      $31, %edx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        jp        .L_2TAG_PACKET_5.0.3
        pcmpeqd   %xmm1, %xmm1
        pslld     $23, %xmm1
        subss     %xmm1, %xmm1
        movl      $0, (%esp)
        movl      $-2147483648, 4(%esp)
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
.L_2TAG_PACKET_5.0.3:
        movl      $-2147483648, %edx
        movl      $0, %eax
.L_2TAG_PACKET_2.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	llround,@function
	.size	llround,.-llround
	.data
# -- End  llround
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
	.long	1138753536
	.long	0
	.long	1138753536
	.long	0
	.long	3286237184
	.long	0
	.long	3286237184
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	2047
	.long	0
	.long	2047
	.long	0
	.long	1075
	.long	1075
	.long	1075
	.long	1075
	.long	1074
	.long	1074
	.long	1074
	.long	1074
	.long	4294967295
	.long	1048575
	.long	0
	.long	0
	.long	0
	.long	1048576
	.long	0
	.long	0
	.long	1
	.long	0
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,160
	.data
	.section .note.GNU-stack, ""
# End
