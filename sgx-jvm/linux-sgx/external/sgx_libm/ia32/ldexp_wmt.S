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
	.file "ldexp_wmt.c"
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
# -- Begin  ldexp
	.text
       .align    16,0x90
	.globl ldexp
ldexp:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 64(%esp)
        call      static_func
        movl      %eax, %ebx
        movq      64(%ebx), %xmm4
        movsd     128(%esp), %xmm0
        movsd     %xmm0, 8(%esp)
        movl      136(%esp), %ecx
        movl      $65536, %eax
        movl      $-65536, %edx
        cmpl      %eax, %ecx
        cmovg     %eax, %ecx
        cmpl      %edx, %ecx
        cmovl     %edx, %ecx
        andpd     %xmm0, %xmm4
        xorpd     %xmm4, %xmm0
        ucomisd   128(%ebx), %xmm0
        jp        .L_2TAG_PACKET_0.0.3
        je        .L_2TAG_PACKET_0.0.3
        comisd    80(%ebx), %xmm0
        jc        .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_2.0.3:
        movapd    %xmm0, %xmm2
        movapd    (%ebx), %xmm3
        psrlq     $52, %xmm2
        movd      %ecx, %xmm1
        andpd     %xmm3, %xmm2
        paddq     %xmm1, %xmm2
        movd      %xmm2, %eax
        movapd    16(%ebx), %xmm3
        testl     %eax, %eax
        jle       .L_2TAG_PACKET_3.0.3
        cmpl      $2047, %eax
        jge       .L_2TAG_PACKET_4.0.3
        andpd     %xmm3, %xmm0
        psllq     $52, %xmm2
        xorpd     %xmm4, %xmm0
        orpd      %xmm2, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_0.0.3:
        fldl      8(%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_1.0.3:
        comisd    144(%ebx), %xmm0
        je        .L_2TAG_PACKET_0.0.3
        mulsd     96(%ebx), %xmm0
        subl      $55, %ecx
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        cmpl      $-52, %eax
        jl        .L_2TAG_PACKET_6.0.3
        xorpd     %xmm5, %xmm5
        andpd     %xmm3, %xmm0
        shufpd    $1, %xmm3, %xmm3
        paddq     %xmm3, %xmm2
        xorpd     %xmm4, %xmm0
        psllq     $52, %xmm2
        orpd      %xmm2, %xmm0
        mulsd     112(%ebx), %xmm0
        comisd    %xmm5, %xmm0
        je        .L_2TAG_PACKET_6.0.3
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_4.0.3:
        lea       32(%ebx), %edx
        movl      $146, %ecx
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_6.0.3:
        lea       48(%ebx), %edx
        movl      $147, %ecx
        jmp       .L_2TAG_PACKET_7.0.3
.L_2TAG_PACKET_7.0.3:
        pextrw    $3, %xmm4, %eax
        shrl      $12, %eax
        addl      %edx, %eax
        movq      (%edx), %xmm0
        mulsd     (%eax), %xmm0
        movq      %xmm0, (%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       136(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %ecx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
.L_2TAG_PACKET_5.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	ldexp,@function
	.size	ldexp,.-ldexp
	.data
# -- End  ldexp
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
	.long	2047
	.long	0
	.long	4294967241
	.long	4294967295
	.long	4294967295
	.long	2148532223
	.long	55
	.long	0
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	1048576
	.long	0
	.long	1048576
	.long	0
	.long	1130364928
	.long	0
	.long	1130364928
	.long	0
	.long	1015021568
	.long	0
	.long	1015021568
	.long	0
	.long	2146435072
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,160
	.data
	.section .note.GNU-stack, ""
# End
