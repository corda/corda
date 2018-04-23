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
	.file "powf_wmt.c"
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
# -- Begin  powf
	.text
       .align    16,0x90
	.globl powf
powf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 48(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     112(%esp), %xmm0
        movss     116(%esp), %xmm1
        movl      112(%esp), %eax
        movaps    %xmm1, %xmm5
        movss     %xmm1, 8(%esp)
        movsd     1424(%ebx), %xmm1
        movsd     1440(%ebx), %xmm7
        movss     %xmm0, 4(%esp)
        psllq     $29, %xmm0
        movapd    1312(%ebx), %xmm6
        cvtss2sd  %xmm5, %xmm5
        movsd     528(%ebx), %xmm2
        orpd      %xmm0, %xmm1
        movl      $2130706431, %ecx
        movl      %eax, %edx
        subl      $8388608, %edx
        cmpl      %edx, %ecx
        jb        .L_2TAG_PACKET_0.0.3
        movl      $0, %ecx
        shrl      $16, %eax
.L_2TAG_PACKET_1.0.3:
.L_2TAG_PACKET_2.0.3:
        movl      $127, %edx
        andl      %eax, %edx
        addl      $1, %edx
        andl      $254, %edx
        movsd     528(%ebx,%edx,4), %xmm3
        movsd     (%ebx,%edx,4), %xmm0
        addl      $65, %eax
        sarl      $7, %eax
        subl      $127, %eax
        cvtsi2sdl %eax, %xmm4
        andpd     %xmm3, %xmm7
        subsd     %xmm7, %xmm3
        mulsd     %xmm1, %xmm7
        mulsd     %xmm3, %xmm1
        movl      %eax, %edx
        sarl      $31, %eax
        addl      %eax, %edx
        xorl      %edx, %eax
        subsd     %xmm2, %xmm7
        movapd    1328(%ebx), %xmm2
        addsd     %xmm7, %xmm1
        movsd     1472(%ebx), %xmm3
        pshufd    $68, %xmm1, %xmm7
        addsd     %xmm1, %xmm0
        bsr       %eax, %eax
        mulpd     %xmm7, %xmm6
        movzwl    10(%esp), %edx
        mulpd     %xmm7, %xmm7
        addsd     %xmm4, %xmm0
        addpd     %xmm2, %xmm6
        andl      $32752, %edx
        shrl      $7, %edx
        subl      $97, %eax
        addl      %edx, %eax
        cmpl      $35, %eax
        jae       .L_2TAG_PACKET_3.0.3
        mulsd     %xmm5, %xmm0
        mulsd     %xmm7, %xmm7
        movapd    %xmm0, %xmm4
        addsd     %xmm3, %xmm0
        mulpd     %xmm7, %xmm6
        movd      %xmm0, %eax
        subsd     %xmm3, %xmm0
        pshufd    $238, %xmm6, %xmm7
        subsd     %xmm0, %xmm4
        addsd     %xmm7, %xmm6
        movapd    1344(%ebx), %xmm7
        mulsd     %xmm6, %xmm5
        movapd    1360(%ebx), %xmm2
        xorpd     %xmm6, %xmm6
        addsd     %xmm5, %xmm4
        pshufd    $68, %xmm4, %xmm1
        movl      %eax, %edx
        sarl      $1, %edx
        andl      $31, %eax
        movsd     1056(%ebx,%eax,8), %xmm3
        orl       $16368, %ecx
        andl      $-16, %edx
        addl      %edx, %ecx
        pinsrw    $3, %ecx, %xmm6
        mulpd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm1
        mulsd     %xmm3, %xmm4
        addpd     %xmm7, %xmm2
        mulsd     %xmm6, %xmm3
        mulsd     %xmm6, %xmm4
        mulsd     %xmm2, %xmm1
        pshufd    $238, %xmm2, %xmm0
        addsd     %xmm1, %xmm0
        mulsd     %xmm4, %xmm0
        addsd     %xmm0, %xmm3
        cvtpd2ps  %xmm3, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_5.0.3:
        movss     112(%esp), %xmm0
        movss     116(%esp), %xmm1
        mulss     %xmm1, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_0.0.3:
        cmpl      $1065353216, 8(%esp)
        je        .L_2TAG_PACKET_5.0.3
        movsd     1392(%ebx), %xmm3
        movd      %xmm5, %edx
        movapd    %xmm5, %xmm0
        movapd    %xmm5, %xmm7
        andpd     %xmm3, %xmm0
        psrlq     $32, %xmm0
        movd      %xmm0, %ecx
        cmpl      $2146435072, %ecx
        jae       .L_2TAG_PACKET_6.0.3
        orl       %ecx, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_7.0.3
        movl      4(%esp), %edx
        cmpl      $2139095039, %edx
        jbe       .L_2TAG_PACKET_8.0.3
        psllq     $52, %xmm3
        orpd      %xmm3, %xmm7
        movl      $1011, %ecx
        movd      %ecx, %xmm3
        psrlq     $20, %xmm0
        psubd     %xmm3, %xmm0
        pxor      %xmm3, %xmm3
        pmaxsw    %xmm3, %xmm0
        psllq     %xmm0, %xmm7
        pcmpeqd   %xmm3, %xmm7
        pmovmskb  %xmm7, %ecx
        andl      $255, %ecx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jae       .L_2TAG_PACKET_9.0.3
        cmpl      $255, %ecx
        jne       .L_2TAG_PACKET_10.0.3
        movsd     1392(%ebx), %xmm3
        movapd    %xmm5, %xmm0
        movapd    %xmm5, %xmm7
        andpd     %xmm3, %xmm0
        movl      $1012, %ecx
        movd      %ecx, %xmm3
        andpd     1392(%ebx), %xmm0
        psrlq     $52, %xmm0
        psubd     %xmm3, %xmm0
        movsd     1488(%ebx), %xmm3
        psllq     %xmm0, %xmm7
        pcmpeqd   %xmm3, %xmm7
        pmovmskb  %xmm7, %ecx
        andl      $255, %ecx
        addl      $32513, %ecx
        andl      $32768, %ecx
        movsd     1440(%ebx), %xmm7
        cmpl      $8388608, %edx
        jb        .L_2TAG_PACKET_11.0.3
        movl      4(%esp), %eax
        shrl      $16, %eax
        andl      $32767, %eax
        jmp       .L_2TAG_PACKET_1.0.3
.L_2TAG_PACKET_6.0.3:
        movss     4(%esp), %xmm0
        movd      %xmm0, %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        ja        .L_2TAG_PACKET_12.0.3
        jmp       .L_2TAG_PACKET_13.0.3
.L_2TAG_PACKET_12.0.3:
        movss     4(%esp), %xmm0
        addss     %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_8.0.3:
        movl      $0, %ecx
.L_2TAG_PACKET_11.0.3:
        xorpd     %xmm0, %xmm0
        movl      $24448, %edx
        pinsrw    $1, %edx, %xmm0
        movss     4(%esp), %xmm4
        movsd     1424(%ebx), %xmm1
        mulss     %xmm0, %xmm4
        movd      %xmm4, %edx
        andl      $2147483647, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_14.0.3
        pextrw    $1, %xmm4, %eax
        movaps    %xmm4, %xmm0
        andps     1376(%ebx), %xmm4
        psllq     $29, %xmm0
        orpd      %xmm0, %xmm1
        andl      $32767, %eax
        subl      $8192, %eax
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_15.0.3:
        movl      8(%esp), %edx
        andl      $-2147483648, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_16.0.3
        movss     1536(%ebx), %xmm0
        xorps     %xmm4, %xmm4
        divss     %xmm4, %xmm0
        movl      $33, %edx
        jmp       .L_2TAG_PACKET_17.0.3
.L_2TAG_PACKET_14.0.3:
        movl      8(%esp), %edx
        andl      $-2147483648, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_18.0.3
        shll      $16, %ecx
        movl      4(%esp), %edx
        andl      %ecx, %edx
        orl       $2139095040, %edx
        movd      %edx, %xmm0
        movss     1536(%ebx), %xmm1
        divss     %xmm4, %xmm1
        movl      $33, %edx
        jmp       .L_2TAG_PACKET_17.0.3
.L_2TAG_PACKET_18.0.3:
        shll      $16, %ecx
        movd      %xmm4, %edx
        andl      %ecx, %edx
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_19.0.3
        fldz      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_9.0.3:
        movl      4(%esp), %edx
        cmpl      $2139095040, %edx
        je        .L_2TAG_PACKET_20.0.3
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        ja        .L_2TAG_PACKET_12.0.3
        cmpl      $255, %ecx
        jne       .L_2TAG_PACKET_21.0.3
        movapd    %xmm5, %xmm1
        movapd    %xmm5, %xmm2
        movl      $1012, %ecx
        movd      %ecx, %xmm3
        andpd     1392(%ebx), %xmm1
        psrlq     $52, %xmm1
        psubd     %xmm3, %xmm1
        pxor      %xmm3, %xmm3
        psllq     %xmm1, %xmm2
        pcmpeqd   %xmm3, %xmm2
        pmovmskb  %xmm2, %eax
        andl      $255, %eax
        cmpl      $255, %eax
        je        .L_2TAG_PACKET_21.0.3
        pextrw    $3, %xmm5, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_22.0.3
.L_2TAG_PACKET_19.0.3:
        flds      1552(%ebx)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_22.0.3:
        flds      1520(%ebx)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_21.0.3:
        pextrw    $3, %xmm5, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_23.0.3
        fldz      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_20.0.3:
        pextrw    $3, %xmm5, %eax
        andl      $32768, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_23.0.3
        fldz      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_10.0.3:
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_15.0.3
        movss     1568(%ebx), %xmm0
        mulss     %xmm0, %xmm0
        movl      $34, %edx
        jmp       .L_2TAG_PACKET_17.0.3
.L_2TAG_PACKET_13.0.3:
        movss     4(%esp), %xmm4
        movss     8(%esp), %xmm5
        movl      8(%esp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        .L_2TAG_PACKET_24.0.3
        andl      $-2147483648, %ecx
        movd      %xmm4, %edx
        movl      $2147483647, %eax
        andl      %edx, %eax
        je        .L_2TAG_PACKET_14.0.3
        xorl      $-1082130432, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_25.0.3
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_26.0.3
        movd      %xmm4, %eax
        andl      $2147483647, %eax
        cmpl      $1065353216, %eax
        jae       .L_2TAG_PACKET_16.0.3
        jmp       .L_2TAG_PACKET_23.0.3
.L_2TAG_PACKET_16.0.3:
        fldz      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_25.0.3:
        movss     116(%esp), %xmm1
        xorps     %xmm0, %xmm0
        addss     %xmm1, %xmm0
        fld1      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_26.0.3:
        pextrw    $1, %xmm4, %eax
        andl      $32640, %eax
        cmpl      $16256, %eax
        jae       .L_2TAG_PACKET_23.0.3
        fldz      
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_23.0.3:
        flds      1504(%ebx)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_24.0.3:
        movss     8(%esp), %xmm5
        addss     %xmm5, %xmm5
        movss     %xmm5, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_7.0.3:
        movss     4(%esp), %xmm4
        movss     8(%esp), %xmm5
        movd      %xmm4, %eax
        movss     1536(%ebx), %xmm0
        movl      $32, %edx
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_17.0.3
        movd      %xmm4, %eax
        xorps     %xmm2, %xmm2
        addss     %xmm2, %xmm4
        movl      $35, %edx
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        .L_2TAG_PACKET_17.0.3
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_27.0.3:
        mulsd     %xmm7, %xmm7
        mulpd     %xmm7, %xmm6
        pshufd    $238, %xmm6, %xmm7
        addsd     %xmm7, %xmm0
        mulsd     %xmm5, %xmm0
        movapd    1344(%ebx), %xmm7
        mulsd     %xmm6, %xmm5
        movapd    %xmm0, %xmm4
        addsd     %xmm3, %xmm0
        movd      %xmm0, %eax
        subsd     %xmm3, %xmm0
        subsd     %xmm0, %xmm4
        movapd    1360(%ebx), %xmm2
        xorpd     %xmm6, %xmm6
        addsd     %xmm5, %xmm4
        pshufd    $68, %xmm4, %xmm1
        movl      %eax, %edx
        sarl      $1, %edx
        andl      $31, %eax
        movsd     1056(%ebx,%eax,8), %xmm3
        orl       $16368, %ecx
        andl      $-16, %edx
        addl      %edx, %ecx
        pinsrw    $3, %ecx, %xmm6
        mulpd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm1
        mulsd     %xmm3, %xmm4
        addpd     %xmm7, %xmm2
        mulsd     %xmm6, %xmm3
        mulsd     %xmm6, %xmm4
        mulsd     %xmm2, %xmm1
        pshufd    $238, %xmm2, %xmm0
        addsd     %xmm1, %xmm0
        mulsd     %xmm4, %xmm0
        addsd     %xmm3, %xmm0
        cvtpd2ps  %xmm0, %xmm0
        pextrw    $1, %xmm0, %eax
        andl      $32767, %eax
        movl      $30, %edx
        cmpl      $32640, %eax
        jae       .L_2TAG_PACKET_17.0.3
        movl      $31, %edx
        cmpl      $16, %eax
        jb        .L_2TAG_PACKET_17.0.3
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_28.0.3:
        movl      $30, %edx
        orl       $32639, %ecx
        xorpd     %xmm0, %xmm0
        pinsrw    $1, %ecx, %xmm0
        addss     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_17.0.3
.L_2TAG_PACKET_29.0.3:
        movl      $31, %edx
        movl      $8388608, %eax
        movd      %eax, %xmm1
        mulss     %xmm1, %xmm1
        xorpd     %xmm0, %xmm0
        pinsrw    $1, %ecx, %xmm0
.L_2TAG_PACKET_17.0.3:
        movss     %xmm0, (%esp)
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       116(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        flds      (%esp)
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_3.0.3:
        movl      4(%esp), %edx
        cmpl      $1065353216, %edx
        je        .L_2TAG_PACKET_25.0.3
        movl      8(%esp), %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jae       .L_2TAG_PACKET_13.0.3
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_7.0.3
        cmpl      $0, %eax
        jl        .L_2TAG_PACKET_30.0.3
        cmpl      $38, %eax
        jb        .L_2TAG_PACKET_27.0.3
        movl      4(%esp), %edx
        andl      $2139095040, %edx
        subl      $1056964608, %edx
        cmpl      $8388608, %edx
        jbe       .L_2TAG_PACKET_31.0.3
.L_2TAG_PACKET_32.0.3:
        movl      4(%esp), %eax
        movl      8(%esp), %edx
        andl      $2139095040, %eax
        subl      $1065353216, %eax
        xorl      %edx, %eax
        andl      $-2147483648, %eax
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_28.0.3
        jmp       .L_2TAG_PACKET_29.0.3
.L_2TAG_PACKET_31.0.3:
        movss     4(%esp), %xmm4
        movl      $1065353216, %eax
        movd      %eax, %xmm2
        pslld     $1, %xmm4
        psrld     $1, %xmm4
        subss     %xmm4, %xmm2
        movd      %xmm2, %edx
        andl      $2139095040, %edx
        je        .L_2TAG_PACKET_30.0.3
        movapd    %xmm5, %xmm4
        mulsd     %xmm0, %xmm4
        pextrw    $3, %xmm4, %eax
        andl      $32752, %eax
        cmpl      $16496, %eax
        jb        .L_2TAG_PACKET_27.0.3
        jmp       .L_2TAG_PACKET_32.0.3
.L_2TAG_PACKET_30.0.3:
        shll      $16, %ecx
        orl       $1065353216, %ecx
        movd      %ecx, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
.L_2TAG_PACKET_4.0.3:
        movl      48(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	powf,@function
	.size	powf,.-powf
	.data
# -- End  powf
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
	.long	2244132864
	.long	1066854294
	.long	1972305920
	.long	1067891411
	.long	2678235136
	.long	1068559160
	.long	4207480832
	.long	1068917750
	.long	1085124608
	.long	1069271106
	.long	2363127808
	.long	1069583448
	.long	3799560192
	.long	1069755113
	.long	972806144
	.long	1069924378
	.long	3492741120
	.long	1070091307
	.long	766832640
	.long	1070255966
	.long	1278367744
	.long	1070418414
	.long	3103942656
	.long	1070578710
	.long	3671264256
	.long	1070666503
	.long	2869823488
	.long	1070744583
	.long	3466906624
	.long	1070821668
	.long	1271733248
	.long	1070897784
	.long	972806144
	.long	1070972954
	.long	3449621504
	.long	1071047201
	.long	1662393344
	.long	1071120549
	.long	2300516352
	.long	1071193018
	.long	176119808
	.long	1071264630
	.long	4147676160
	.long	1071335403
	.long	2905168896
	.long	1071405359
	.long	3761433600
	.long	1071474515
	.long	2716292096
	.long	1071542890
	.long	1758136320
	.long	1071610501
	.long	2341968384
	.long	1071661018
	.long	14039552
	.long	1071694085
	.long	4204914176
	.long	1071726793
	.long	513134592
	.long	1071759153
	.long	3559335936
	.long	1071791169
	.long	1904282624
	.long	3218771961
	.long	714524672
	.long	3218709255
	.long	180337664
	.long	3218647192
	.long	47131648
	.long	3218585759
	.long	1751500800
	.long	3218524943
	.long	4058951680
	.long	3218464732
	.long	2999895040
	.long	3218405115
	.long	398715904
	.long	3218346080
	.long	3815829504
	.long	3218287614
	.long	722717696
	.long	3218229709
	.long	1399058432
	.long	3218172352
	.long	227933184
	.long	3218115534
	.long	2835759104
	.long	3218038744
	.long	3554951168
	.long	3217927202
	.long	2818570240
	.long	3217816679
	.long	3041732608
	.long	3217707156
	.long	180338688
	.long	3217598616
	.long	835166208
	.long	3217491040
	.long	704622592
	.long	3217384412
	.long	1695342592
	.long	3217278715
	.long	3266000896
	.long	3217173933
	.long	2363885568
	.long	3217070051
	.long	3908239360
	.long	3216902938
	.long	448159744
	.long	3216698681
	.long	2530197504
	.long	3216496132
	.long	4268273664
	.long	3216295264
	.long	2785038336
	.long	3216096050
	.long	522182656
	.long	3215814333
	.long	3386466304
	.long	3215422356
	.long	3838140416
	.long	3215033528
	.long	2250194944
	.long	3214361582
	.long	2718924800
	.long	3213307003
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1697350398
	.long	1073157447
	.long	3983912013
	.long	1073134173
	.long	3077571303
	.long	1073111605
	.long	787997771
	.long	1073089711
	.long	2608086797
	.long	1073068460
	.long	4250927815
	.long	1073047825
	.long	4006130247
	.long	1073027780
	.long	2255915958
	.long	1073008300
	.long	1031537321
	.long	1072989361
	.long	3606154084
	.long	1072970940
	.long	1235818328
	.long	1072953018
	.long	2880061438
	.long	1072935573
	.long	525144062
	.long	1072918588
	.long	3251485016
	.long	1072902043
	.long	2604098795
	.long	1072885923
	.long	2408035495
	.long	1072870211
	.long	1357880318
	.long	1072854892
	.long	1394140652
	.long	1072839951
	.long	3210356685
	.long	1072825374
	.long	1774519652
	.long	1072811149
	.long	3338441873
	.long	1072797262
	.long	3804456356
	.long	1072783702
	.long	1462910403
	.long	1072770458
	.long	1149890700
	.long	1072757518
	.long	1234436653
	.long	1072744872
	.long	2089211650
	.long	1072732510
	.long	1684223316
	.long	1072720423
	.long	4072784951
	.long	1072708601
	.long	2114454037
	.long	1072697037
	.long	2012861074
	.long	1072678195
	.long	2037138800
	.long	1072656045
	.long	3417210877
	.long	1072634361
	.long	3694789629
	.long	1072613129
	.long	2992528814
	.long	1072592335
	.long	3882342941
	.long	1072571965
	.long	2671772639
	.long	1072552007
	.long	2172608510
	.long	1072532448
	.long	3001586108
	.long	1072513276
	.long	3477449063
	.long	1072494480
	.long	3818975826
	.long	1072476049
	.long	1758664544
	.long	1072457973
	.long	1046539701
	.long	1072440241
	.long	2778964739
	.long	1072422843
	.long	1026978211
	.long	1072405771
	.long	4238694625
	.long	1072389014
	.long	811117725
	.long	1072372566
	.long	1975098645
	.long	1072356416
	.long	3079413536
	.long	1072340557
	.long	712695513
	.long	1072324982
	.long	1238508315
	.long	1072309682
	.long	3563503614
	.long	1072294650
	.long	3383126459
	.long	1072279880
	.long	1724836050
	.long	1072265365
	.long	608820196
	.long	1072251098
	.long	3005930546
	.long	1072237072
	.long	4207804139
	.long	1072223282
	.long	378851326
	.long	1072209723
	.long	1405092650
	.long	1072196387
	.long	795096448
	.long	1072183270
	.long	1417164049
	.long	1072170366
	.long	2583387630
	.long	1072157670
	.long	20099889
	.long	1072145178
	.long	3019600065
	.long	1072132883
	.long	53601996
	.long	1072120783
	.long	1697350398
	.long	1072108871
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	3541402996
	.long	1072716208
	.long	1828292879
	.long	1072739672
	.long	3490863953
	.long	1072763649
	.long	1014845819
	.long	1072788152
	.long	828946858
	.long	1072813191
	.long	1853186616
	.long	1072838778
	.long	4112506593
	.long	1072864925
	.long	171030293
	.long	1072891646
	.long	926591435
	.long	1072918951
	.long	1276261410
	.long	1072946854
	.long	1617004845
	.long	1072975368
	.long	3577096743
	.long	1073004506
	.long	1453150082
	.long	1073034283
	.long	3712504873
	.long	1073064711
	.long	2956612997
	.long	1073095806
	.long	1719614413
	.long	1073127582
	.long	3907805044
	.long	1073160053
	.long	1944781191
	.long	1073193236
	.long	2571947539
	.long	1073227145
	.long	1110089947
	.long	1073261797
	.long	2966275557
	.long	1073297207
	.long	2191782032
	.long	1073333393
	.long	2990417245
	.long	1073370371
	.long	2572866477
	.long	1073408159
	.long	4076559943
	.long	1073446774
	.long	3716502172
	.long	1073486235
	.long	3706687593
	.long	1073526560
	.long	3707479175
	.long	1073567768
	.long	863738719
	.long	1073609879
	.long	2728693978
	.long	1073652911
	.long	1533953344
	.long	1073696886
	.long	531389993
	.long	1067950900
	.long	4289495988
	.long	1069842387
	.long	2705553552
	.long	3216330822
	.long	4277811695
	.long	3218484802
	.long	1874480759
	.long	1065595563
	.long	4286760334
	.long	1070514109
	.long	3607404735
	.long	1068264200
	.long	4277811695
	.long	1072049730
	.long	2147483647
	.long	0
	.long	0
	.long	0
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	1048575
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	4227858432
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	1077936128
	.long	0
	.long	0
	.long	0
	.long	1122500608
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	2139095040
	.long	0
	.long	0
	.long	0
	.long	4286578688
	.long	0
	.long	0
	.long	0
	.long	1065353216
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	2139095041
	.long	0
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,1584
	.data
	.section .note.GNU-stack, ""
# End
