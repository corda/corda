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
	.file "fmod_wmt.c"
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
# -- Begin  fmod
	.text
       .align    16,0x90
	.globl fmod
fmod:
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
        movsd     120(%esp), %xmm1
        movsd     112(%esp), %xmm0
        fldl      120(%esp)
        fldl      112(%esp)
        unpcklpd  %xmm0, %xmm1
        movdqa    (%ebx), %xmm2
        pshufd    $212, %xmm1, %xmm1
        movdqa    16(%ebx), %xmm3
        pand      %xmm2, %xmm1
        pcmpeqd   %xmm1, %xmm3
        movmskps  %xmm3, %eax
        cmpl      $2, %eax
        jg        .L_2TAG_PACKET_0.0.3
.L_2TAG_PACKET_1.0.3:
        fprem     
        fstsw     %ax
        sahf      
        jp        .L_2TAG_PACKET_1.0.3
        fstp      %st(1)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        movl      %eax, %edx
        andl      $3, %eax
        cmpl      $3, %eax
        jne       .L_2TAG_PACKET_3.0.3
        movl      112(%esp), %edx
        movl      116(%esp), %ecx
        xorl      %eax, %eax
        andl      $2147483647, %ecx
        subl      %edx, %eax
        orl       %edx, %eax
        shrl      $31, %eax
        orl       %eax, %ecx
        cmpl      $2146435072, %ecx
        ja        .L_2TAG_PACKET_4.0.3
        fstp      %st(0)
        fstp      %st(0)
        movl      $2139095040, 8(%esp)
        fldz      
        flds      8(%esp)
        fmulp     
        fstpl     (%esp)
        movl      $121, %edx
.L_2TAG_PACKET_5.0.3:
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
        jmp       .L_2TAG_PACKET_6.0.3
.L_2TAG_PACKET_3.0.3:
        movl      112(%esp), %eax
        xorl      %ecx, %ecx
        subl      %eax, %ecx
        orl       %ecx, %eax
        shrl      $31, %eax
        movl      116(%esp), %ecx
        orl       %ecx, %eax
        andl      $2147483647, %eax
        movl      120(%esp), %edx
        xorl      %ecx, %ecx
        subl      %edx, %ecx
        orl       %ecx, %edx
        shrl      $31, %edx
        movl      124(%esp), %ecx
        orl       %ecx, %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %eax
        ja        .L_2TAG_PACKET_4.0.3
        cmpl      $2146435072, %edx
        ja        .L_2TAG_PACKET_4.0.3
        cmpl      $2146435072, %eax
        je        .L_2TAG_PACKET_7.0.3
        fstp      %st(1)
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_4.0.3:
        faddp     
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_7.0.3:
        fstp      %st(1)
        fldz      
        fmulp     
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_2.0.3:
.L_2TAG_PACKET_6.0.3:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	fmod,@function
	.size	fmod,.-fmod
	.data
# -- End  fmod
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
	.long	2146435072
	.long	2146435072
	.long	0
	.long	0
	.long	2146435072
	.long	2146435072
	.type	static_const_table,@object
	.size	static_const_table,32
	.data
	.section .note.GNU-stack, ""
# End
