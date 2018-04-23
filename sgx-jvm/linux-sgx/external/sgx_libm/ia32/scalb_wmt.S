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
	.file "scalb_wmt.c"
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
# -- Begin  scalb
	.text
       .align    16,0x90
	.globl scalb
scalb:
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
        movsd     128(%esp), %xmm0
        movsd     136(%esp), %xmm1
        movq      %xmm0, (%esp)
        movq      %xmm1, 8(%esp)
        pextrw    $3, %xmm0, %eax
        pextrw    $3, %xmm1, %edx
        andl      $32752, %eax
        andl      $32752, %edx
        subl      $16, %eax
        subl      $16368, %edx
        cmpl      $32736, %eax
        jae       .L_2TAG_PACKET_0.0.3
        cmpl      $256, %edx
        jae       .L_2TAG_PACKET_0.0.3
        movq      144(%ebx), %xmm7
.L_2TAG_PACKET_1.0.3:
        movdqa    %xmm1, %xmm2
        addsd     %xmm7, %xmm2
        movd      %xmm2, %ecx
        shll      $4, %ecx
        addl      %ecx, %eax
        movdqa    %xmm2, %xmm3
        subsd     %xmm7, %xmm3
        comisd    %xmm1, %xmm3
        jne       .L_2TAG_PACKET_2.0.3
        cmpl      $32736, %eax
        jae       .L_2TAG_PACKET_3.0.3
        psllq     $52, %xmm2
        paddq     %xmm2, %xmm0
.L_2TAG_PACKET_4.0.3:
        movq      %xmm0, 16(%esp)
        fldl      16(%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_6.0.3:
        fldl      (%esp)
        faddl     8(%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_2.0.3:
        fldl      48(%ebx)
        fmull     (%ebx)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_7.0.3:
        pextrw    $3, %xmm1, %ecx
        comisd    %xmm7, %xmm0
        je        .L_2TAG_PACKET_8.0.3
        cmpl      $32736, %eax
        je        .L_2TAG_PACKET_9.0.3
        pextrw    $3, %xmm0, %eax
        shrl      $12, %eax
        andl      $8, %eax
        testl     $32768, %ecx
        je        .L_2TAG_PACKET_10.0.3
        fldl      48(%ebx,%eax)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_9.0.3:
        xorl      $32768, %ecx
.L_2TAG_PACKET_8.0.3:
        testl     $32768, %ecx
        je        .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_11.0.3:
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_12.0.3:
        movq      80(%ebx), %xmm7
        movq      96(%ebx), %xmm6
        pandn     %xmm1, %xmm7
        comisd    %xmm6, %xmm7
        jc        .L_2TAG_PACKET_2.0.3
        pextrw    $3, %xmm0, %eax
        shrl      $12, %eax
        andl      $8, %eax
.L_2TAG_PACKET_10.0.3:
        fldl      (%ebx,%eax)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_0.0.3:
        movl      %edx, %ecx
        pxor      %xmm7, %xmm7
        shrl      $4, %ecx
        ucomisd   %xmm1, %xmm0
        jp        .L_2TAG_PACKET_6.0.3
        addl      $12, %ecx
        cmpl      $16384, %edx
        je        .L_2TAG_PACKET_7.0.3
        movdqa    %xmm1, %xmm2
        comisd    %xmm7, %xmm1
        je        .L_2TAG_PACKET_11.0.3
        movd      %ecx, %xmm3
        cmpl      $32736, %eax
        je        .L_2TAG_PACKET_12.0.3
        psllq     %xmm3, %xmm2
        testl     %edx, %edx
        jl        .L_2TAG_PACKET_2.0.3
        pcmpeqd   %xmm7, %xmm2
        cmpl      $832, %edx
        jae       .L_2TAG_PACKET_13.0.3
        pmovmskb  %xmm2, %ecx
        cmpl      $65535, %ecx
        jne       .L_2TAG_PACKET_2.0.3
        comisd    %xmm7, %xmm0
        je        .L_2TAG_PACKET_11.0.3
        cmpl      $256, %edx
        jae       .L_2TAG_PACKET_14.0.3
        mulsd     112(%ebx), %xmm0
        movq      160(%ebx), %xmm7
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        subl      $16, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_3.0.3:
        jge       .L_2TAG_PACKET_15.0.3
        sarl      $4, %eax
        cmpl      $-53, %eax
        jl        .L_2TAG_PACKET_16.0.3
        movd      176(%ebx), %xmm3
        movq      128(%ebx), %xmm4
        pxor      %xmm7, %xmm7
        pshufd    $236, %xmm2, %xmm2
        paddd     %xmm3, %xmm2
        psllq     $52, %xmm2
        paddq     %xmm2, %xmm0
        mulsd     %xmm4, %xmm0
        comisd    %xmm7, %xmm0
        jne       .L_2TAG_PACKET_4.0.3
        movl      $53, %ecx
        jmp       .L_2TAG_PACKET_17.0.3
.L_2TAG_PACKET_13.0.3:
        comisd    %xmm7, %xmm0
        je        .L_2TAG_PACKET_11.0.3
.L_2TAG_PACKET_14.0.3:
        pextrw    $3, %xmm1, %edx
        testl     $32768, %edx
        jne       .L_2TAG_PACKET_16.0.3
.L_2TAG_PACKET_15.0.3:
        lea       16(%ebx), %edx
        movl      $53, %ecx
        pxor      %xmm1, %xmm1
        jmp       .L_2TAG_PACKET_18.0.3
.L_2TAG_PACKET_16.0.3:
        lea       32(%ebx), %edx
        movl      $54, %ecx
        movq      64(%ebx), %xmm1
        jmp       .L_2TAG_PACKET_18.0.3
.L_2TAG_PACKET_18.0.3:
        pxor      %xmm7, %xmm7
        pextrw    $3, %xmm0, %eax
        shrl      $12, %eax
        andl      $8, %eax
        addl      %edx, %eax
        movq      (%edx), %xmm0
        mulsd     (%eax), %xmm0
        pand      %xmm0, %xmm1
        comisd    %xmm7, %xmm1
        jne       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_17.0.3:
        movq      %xmm0, 16(%esp)
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       136(%esp), %eax
        movl      %eax, 4(%esp)
        lea       48(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %ecx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      16(%esp)
.L_2TAG_PACKET_5.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	scalb,@function
	.size	scalb,.-scalb
	.data
# -- End  scalb
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
	.long	2146435072
	.long	0
	.long	4293918720
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	1
	.long	1048576
	.long	1
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
	.long	1127743488
	.long	0
	.long	1127743488
	.long	4294967241
	.long	1127743487
	.long	4294967241
	.long	1127743487
	.long	55
	.long	55
	.long	55
	.long	55
	.type	static_const_table,@object
	.size	static_const_table,192
	.data
	.section .note.GNU-stack, ""
# End
