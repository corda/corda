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
	.file "nearbyint_wmt.c"
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
# -- Begin  nearbyint
	.text
       .align    16,0x90
	.globl nearbyint
nearbyint:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 48(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     112(%esp), %xmm0
        pextrw    $3, %xmm0, %ecx
        movl      $32752, %eax
        andl      %ecx, %eax
        cmpl      $17200, %eax
        jge       .L_2TAG_PACKET_0.0.2
        fnstcw    8(%esp)
        subl      $16368, %eax
        jl        .L_2TAG_PACKET_1.0.2
        movzwl    8(%esp), %edx
        andl      $3072, %edx
        jne       .L_2TAG_PACKET_2.0.2
        movq      (%ebx), %xmm1
        movq      16(%ebx), %xmm2
        pand      %xmm0, %xmm1
        movq      32(%ebx), %xmm4
        movq      32(%ebx), %xmm3
        psubq     48(%ebx), %xmm1
        pand      %xmm0, %xmm2
        psrlq     $52, %xmm1
        movapd    %xmm0, %xmm5
        psllq     %xmm1, %xmm0
        por       48(%ebx), %xmm2
        pand      %xmm0, %xmm4
        pand      64(%ebx), %xmm0
        psrlq     %xmm1, %xmm3
        por       80(%ebx), %xmm0
        por       48(%ebx), %xmm4
        pandn     %xmm5, %xmm3
        cmpeqsd   96(%ebx), %xmm0
        cmpnlesd  96(%ebx), %xmm4
        por       %xmm4, %xmm0
        pand      %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
        cmpl      $3072, %edx
        je        .L_2TAG_PACKET_4.0.2
        testl     $1024, %edx
        je        .L_2TAG_PACKET_5.0.2
        movq      (%ebx), %xmm1
        pand      %xmm0, %xmm1
        xorpd     %xmm2, %xmm2
        movq      32(%ebx), %xmm3
        psubq     48(%ebx), %xmm1
        movapd    %xmm0, %xmm5
        xorpd     %xmm4, %xmm4
        psrlq     $52, %xmm1
        cmpnlesd  %xmm0, %xmm2
        psllq     %xmm1, %xmm0
        pand      32(%ebx), %xmm0
        pcmpeqd   %xmm4, %xmm0
        psrlq     %xmm1, %xmm3
        pshufd    $1, %xmm0, %xmm4
        pandn     %xmm5, %xmm3
        pand      %xmm4, %xmm0
        pandn     %xmm2, %xmm0
        pand      48(%ebx), %xmm0
        subsd     %xmm0, %xmm3
        movq      %xmm3, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_5.0.2:
        movq      (%ebx), %xmm1
        pand      %xmm0, %xmm1
        xorpd     %xmm2, %xmm2
        movq      32(%ebx), %xmm3
        psubq     48(%ebx), %xmm1
        movapd    %xmm0, %xmm5
        xorpd     %xmm4, %xmm4
        psrlq     $52, %xmm1
        cmplesd   %xmm0, %xmm2
        psllq     %xmm1, %xmm0
        pand      32(%ebx), %xmm0
        pcmpeqd   %xmm4, %xmm0
        psrlq     %xmm1, %xmm3
        pshufd    $1, %xmm0, %xmm4
        pandn     %xmm5, %xmm3
        pand      %xmm4, %xmm0
        pandn     %xmm2, %xmm0
        pand      48(%ebx), %xmm0
        addsd     %xmm3, %xmm0
        movq      %xmm0, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_4.0.2:
        movq      (%ebx), %xmm1
        pand      %xmm0, %xmm1
        movq      32(%ebx), %xmm3
        psubq     48(%ebx), %xmm1
        psrlq     $52, %xmm1
        psrlq     %xmm1, %xmm3
        pandn     %xmm0, %xmm3
        movq      %xmm3, (%esp)
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_1.0.2:
        movzwl    8(%esp), %edx
        andl      $3072, %edx
        jne       .L_2TAG_PACKET_6.0.2
        andpd     112(%ebx), %xmm0
        addl      $16, %eax
        shrl      $14, %ecx
        shrl      $31, %eax
        ucomisd   128(%ebx), %xmm0
        je        .L_2TAG_PACKET_7.0.2
        andl      $2, %ecx
        orl       %ecx, %eax
        flds      144(%ebx,%eax,4)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_7.0.2:
        shrl      $15, %ecx
        flds      160(%ebx,%ecx,4)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_6.0.2:
        cmpl      $3072, %edx
        je        .L_2TAG_PACKET_8.0.2
        testl     $1024, %edx
        je        .L_2TAG_PACKET_9.0.2
        pxor      %xmm1, %xmm1
        cmpnlesd  %xmm0, %xmm1
        movd      %xmm1, %eax
        shrl      $15, %ecx
        subl      %eax, %ecx
        flds      176(%ebx,%ecx,4)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_9.0.2:
        pxor      %xmm1, %xmm1
        cmpnltsd  %xmm0, %xmm1
        movd      %xmm1, %eax
        shrl      $15, %ecx
        subl      %ecx, %eax
        flds      200(%ebx,%eax,4)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_8.0.2:
        shrl      $15, %ecx
        flds      208(%ebx,%ecx,4)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_0.0.2:
        movq      %xmm0, (%esp)
        fldl      (%esp)
        fldz      
        faddp     
.L_2TAG_PACKET_3.0.2:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	nearbyint,@function
	.size	nearbyint,.-nearbyint
	.data
# -- End  nearbyint
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	4294967295
	.long	1048575
	.long	4294967295
	.long	1048575
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.long	4294967295
	.long	2097151
	.long	4294967295
	.long	2097151
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	0
	.long	1073217536
	.long	0
	.long	1073217536
	.long	4294967295
	.long	2147483647
	.long	0
	.long	0
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.long	1065353216
	.long	0
	.long	3212836864
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	3212836864
	.long	0
	.long	2147483648
	.long	0
	.long	1065353216
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,224
	.data
	.section .note.GNU-stack, ""
# End
