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
	.file "fmaf_wmt.c"
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
# -- Begin  fmaf
	.text
       .align    16,0x90
	.globl fmaf
fmaf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $136, %esp
        movl      %ebx, 80(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     144(%esp), %xmm0
        movss     148(%esp), %xmm1
        movss     152(%esp), %xmm2
        movss     %xmm0, 8(%esp)
        movss     %xmm1, 16(%esp)
        movss     %xmm2, 24(%esp)
        movl      8(%esp), %eax
        ucomiss   %xmm1, %xmm0
        movl      16(%esp), %ecx
        jp        .L_2TAG_PACKET_0.0.2
        movl      24(%esp), %edx
        ucomiss   %xmm2, %xmm2
        jp        .L_2TAG_PACKET_1.0.2
        andl      $2147483647, %eax
        je        .L_2TAG_PACKET_2.0.2
        cmpl      $1065353216, %eax
        je        .L_2TAG_PACKET_3.0.2
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_4.0.2
        andl      $2147483647, %ecx
        je        .L_2TAG_PACKET_2.0.2
        cmpl      $1065353216, %ecx
        je        .L_2TAG_PACKET_3.0.2
        cmpl      $2139095040, %ecx
        je        .L_2TAG_PACKET_4.0.2
        andl      $2147483647, %edx
        je        .L_2TAG_PACKET_5.0.2
        cmpl      $2139095040, %edx
        je        .L_2TAG_PACKET_6.0.2
        cmpl      $8388608, %eax
        jl        .L_2TAG_PACKET_7.0.2
        cvtps2pd  %xmm0, %xmm3
.L_2TAG_PACKET_8.0.2:
        cmpl      $8388608, %ecx
        jl        .L_2TAG_PACKET_9.0.2
        cvtps2pd  %xmm1, %xmm4
.L_2TAG_PACKET_10.0.2:
        cmpl      $8388608, %edx
        jl        .L_2TAG_PACKET_11.0.2
        cvtps2pd  %xmm2, %xmm0
.L_2TAG_PACKET_12.0.2:
        mulsd     %xmm4, %xmm3
        pextrw    $3, %xmm3, %edx
        andl      $32752, %edx
        movl      $96, %eax
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        addl      %edx, %eax
        subl      %ecx, %eax
        cmpl      $560, %eax
        jae       .L_2TAG_PACKET_13.0.2
        addsd     %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_0.0.2:
.L_2TAG_PACKET_2.0.2:
.L_2TAG_PACKET_3.0.2:
.L_2TAG_PACKET_4.0.2:
.L_2TAG_PACKET_5.0.2:
        flds      8(%esp)
        fmuls     16(%esp)
        fadds     24(%esp)
        jmp       .L_2TAG_PACKET_15.0.2
.L_2TAG_PACKET_1.0.2:
        flds      8(%esp)
        fadds     24(%esp)
        jmp       .L_2TAG_PACKET_15.0.2
.L_2TAG_PACKET_6.0.2:
        flds      24(%esp)
        jmp       .L_2TAG_PACKET_15.0.2
.L_2TAG_PACKET_7.0.2:
        movaps    (%ebx), %xmm3
        pand      %xmm0, %xmm3
        movaps    48(%ebx), %xmm5
        orpd      16(%ebx), %xmm3
        pand      %xmm0, %xmm5
        subsd     16(%ebx), %xmm3
        psllq     $32, %xmm5
        mulsd     32(%ebx), %xmm3
        orpd      %xmm5, %xmm3
        jmp       .L_2TAG_PACKET_8.0.2
.L_2TAG_PACKET_9.0.2:
        movaps    (%ebx), %xmm4
        pand      %xmm1, %xmm4
        movaps    48(%ebx), %xmm5
        orpd      16(%ebx), %xmm4
        pand      %xmm1, %xmm5
        subsd     16(%ebx), %xmm4
        psllq     $32, %xmm5
        mulsd     32(%ebx), %xmm4
        orpd      %xmm5, %xmm4
        jmp       .L_2TAG_PACKET_10.0.2
.L_2TAG_PACKET_11.0.2:
        movaps    (%ebx), %xmm0
        pand      %xmm2, %xmm0
        movaps    48(%ebx), %xmm5
        orpd      16(%ebx), %xmm0
        pand      %xmm2, %xmm5
        subsd     16(%ebx), %xmm0
        psllq     $32, %xmm5
        mulsd     32(%ebx), %xmm0
        orpd      %xmm5, %xmm0
        jmp       .L_2TAG_PACKET_12.0.2
.L_2TAG_PACKET_13.0.2:
        pextrw    $1, %xmm2, %ecx
        pextrw    $3, %xmm3, %edx
        sarl      $4, %eax
        xorl      %edx, %ecx
        testl     $32768, %ecx
        jne       .L_2TAG_PACKET_16.0.2
        cmpl      $53, %eax
        jge       .L_2TAG_PACKET_17.0.2
        cmpl      $-19, %eax
        jle       .L_2TAG_PACKET_18.0.2
        cmpl      $6, %eax
        jge       .L_2TAG_PACKET_19.0.2
        movl      $6, %ecx
        subl      %eax, %ecx
        addl      $58, %eax
        movsd     64(%ebx), %xmm1
        pand      64(%ebx), %xmm3
        pxor      %xmm5, %xmm5
        por       96(%ebx), %xmm3
        pxor      %xmm2, %xmm2
        pinsrw    $0, %eax, %xmm5
        pinsrw    $0, %ecx, %xmm2
        pand      %xmm0, %xmm1
        pand      80(%ebx), %xmm0
        movdqa    %xmm3, %xmm4
        psllq     %xmm5, %xmm3
        por       96(%ebx), %xmm1
        psrlq     %xmm2, %xmm4
        psrlq     $40, %xmm3
        paddq     %xmm4, %xmm1
        movdqa    %xmm1, %xmm5
        psrlq     $53, %xmm1
        movdqa    %xmm5, %xmm4
        psrlq     %xmm1, %xmm5
        pand      %xmm1, %xmm4
        psllq     $52, %xmm1
        por       %xmm3, %xmm5
        paddq     %xmm1, %xmm0
        por       %xmm4, %xmm5
        pand      64(%ebx), %xmm5
        por       %xmm5, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_17.0.2:
        movapd    112(%ebx), %xmm0
        orpd      %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_18.0.2:
        orpd      112(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_19.0.2:
        movl      $70, %ecx
        subl      %eax, %ecx
        subl      $6, %eax
        movsd     64(%ebx), %xmm1
        pand      64(%ebx), %xmm0
        pxor      %xmm5, %xmm5
        por       96(%ebx), %xmm0
        pxor      %xmm2, %xmm2
        pinsrw    $0, %ecx, %xmm5
        pinsrw    $0, %eax, %xmm2
        pand      %xmm3, %xmm1
        pand      80(%ebx), %xmm3
        movdqa    %xmm0, %xmm4
        psllq     %xmm5, %xmm0
        por       96(%ebx), %xmm1
        psrlq     %xmm2, %xmm4
        pxor      %xmm2, %xmm2
        psrlq     $18, %xmm0
        psubq     %xmm0, %xmm2
        paddq     %xmm4, %xmm1
        psrlq     $63, %xmm2
        movdqa    %xmm1, %xmm0
        psrlq     $53, %xmm1
        movdqa    %xmm0, %xmm4
        psrlq     %xmm1, %xmm0
        pand      %xmm1, %xmm4
        psllq     $52, %xmm1
        por       %xmm2, %xmm0
        paddq     %xmm1, %xmm3
        por       %xmm4, %xmm0
        pand      64(%ebx), %xmm0
        por       %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_16.0.2:
        cmpl      $53, %eax
        jge       .L_2TAG_PACKET_20.0.2
        cmpl      $-22, %eax
        jle       .L_2TAG_PACKET_21.0.2
        cmpl      $6, %eax
        jge       .L_2TAG_PACKET_22.0.2
        movl      $6, %ecx
        subl      %eax, %ecx
        addl      $58, %eax
        movsd     64(%ebx), %xmm1
        pand      64(%ebx), %xmm3
        pxor      %xmm5, %xmm5
        por       96(%ebx), %xmm3
        pxor      %xmm2, %xmm2
        pinsrw    $0, %eax, %xmm5
        pinsrw    $0, %ecx, %xmm2
        pand      %xmm0, %xmm1
        pand      80(%ebx), %xmm0
        movdqa    %xmm3, %xmm4
        psllq     %xmm5, %xmm3
        por       96(%ebx), %xmm1
        psrlq     %xmm2, %xmm4
        pxor      %xmm2, %xmm2
        psrlq     $37, %xmm3
        psubq     %xmm3, %xmm2
        psubq     %xmm4, %xmm1
        psrlq     $63, %xmm2
        psubq     %xmm2, %xmm1
        movdqa    %xmm1, %xmm3
        movsd     112(%ebx), %xmm2
        psrlq     $52, %xmm1
        psubq     %xmm1, %xmm2
        movdqa    %xmm2, %xmm1
        psllq     $52, %xmm2
        psllq     %xmm1, %xmm3
        pand      64(%ebx), %xmm3
        psubq     %xmm2, %xmm0
        por       %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_20.0.2:
        movsd     64(%ebx), %xmm1
        pand      %xmm3, %xmm1
        por       96(%ebx), %xmm1
        psubq     112(%ebx), %xmm1
        movapd    %xmm1, %xmm0
        psrlq     $52, %xmm1
        movapd    112(%ebx), %xmm4
        psubq     %xmm1, %xmm4
        psllq     %xmm4, %xmm0
        psllq     $52, %xmm4
        pand      80(%ebx), %xmm3
        psubq     %xmm4, %xmm3
        pand      64(%ebx), %xmm0
        por       %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_21.0.2:
        movsd     64(%ebx), %xmm1
        pand      %xmm0, %xmm1
        por       96(%ebx), %xmm1
        psubq     112(%ebx), %xmm1
        movapd    %xmm1, %xmm2
        psrlq     $52, %xmm1
        movapd    112(%ebx), %xmm3
        psubq     %xmm1, %xmm3
        psllq     %xmm3, %xmm2
        psllq     $52, %xmm3
        pand      80(%ebx), %xmm0
        psubq     %xmm3, %xmm0
        pand      64(%ebx), %xmm2
        por       %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_22.0.2:
        movl      $70, %ecx
        subl      %eax, %ecx
        subl      $6, %eax
        movsd     64(%ebx), %xmm1
        pand      64(%ebx), %xmm0
        pxor      %xmm5, %xmm5
        por       96(%ebx), %xmm0
        pxor      %xmm2, %xmm2
        pinsrw    $0, %ecx, %xmm5
        pinsrw    $0, %eax, %xmm2
        pand      %xmm3, %xmm1
        pand      80(%ebx), %xmm3
        movdqa    %xmm0, %xmm4
        psllq     %xmm5, %xmm0
        por       96(%ebx), %xmm1
        psrlq     %xmm2, %xmm4
        pxor      %xmm2, %xmm2
        psrlq     $18, %xmm0
        psubq     %xmm0, %xmm2
        psubq     %xmm4, %xmm1
        psrlq     $63, %xmm2
        psubq     %xmm2, %xmm1
        movdqa    %xmm1, %xmm0
        movsd     112(%ebx), %xmm2
        psrlq     $52, %xmm1
        psubq     %xmm1, %xmm2
        movdqa    %xmm2, %xmm1
        psllq     $52, %xmm2
        psllq     %xmm1, %xmm0
        pand      64(%ebx), %xmm0
        psubq     %xmm2, %xmm3
        por       %xmm3, %xmm0
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_14.0.2:
        movq      %xmm0, (%esp)
        fldl      (%esp)
        fstps     32(%esp)
        flds      32(%esp)
.L_2TAG_PACKET_15.0.2:
        movl      80(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	fmaf,@function
	.size	fmaf,.-fmaf
	.data
# -- End  fmaf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	2147483647
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	970981376
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	4294967295
	.long	1048575
	.long	0
	.long	0
	.long	0
	.long	4293918720
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
	.size	static_const_table,128
	.data
	.section .note.GNU-stack, ""
# End
