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
	.file "csqrt.c"
	.text
..TXTST0:
# -- Begin  csqrt
	.text
       .align    16,0x90
	.globl csqrt
csqrt:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_csqrt.1:
..L2:

..B1.2:
        movq      %rsp, %rax
        subq      $152, %rsp
	.cfi_def_cfa_offset 160
        movq      %rax, 56(%rsp)
        movq      %xmm0, 32(%rsp)
        movq      %xmm1, 40(%rsp)
        movq      %rbx, 64(%rsp)
        movq      32(%rsp), %xmm0
        movq      40(%rsp), %xmm1
        lea       csqrt_table(%rip), %r8
        movq      (%r8), %xmm2
        movq      (%r8), %xmm3
        movq      144(%r8), %xmm7
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        pand      16(%r8), %xmm0
        pand      16(%r8), %xmm1
        movq      %xmm2, %xmm4
        movq      %xmm3, %xmm5
        movl      36(%rsp), %eax
        movl      44(%rsp), %ebx
        andl      $2147483647, %eax
        andl      $2147483647, %ebx
        subl      $591396864, %eax
        movq      %xmm0, 8(%rsp)
        subl      $591396864, %ebx
        cmpl      $1017118720, %eax
        movq      %xmm1, 16(%rsp)
        jae       .L_2TAG_PACKET_0.0.1
        cmpl      $1017118720, %ebx
        jae       .L_2TAG_PACKET_0.0.1
        addl      $591396864, %eax
        addl      $591396864, %ebx
        movl      %eax, %edx
        subl      %ebx, %edx
        cmpl      $60817408, %edx
        jg        .L_2TAG_PACKET_1.0.1
        cmpl      $-60817408, %edx
        jl        .L_2TAG_PACKET_2.0.1
.L_2TAG_PACKET_3.0.1:
        subsd     %xmm2, %xmm0
        subsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm3, %xmm3
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm5
        movq      %xmm2, %xmm6
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        movq      %xmm7, 24(%rsp)
        movq      %xmm2, %xmm7
        addsd     %xmm3, %xmm2
        addsd     %xmm4, %xmm0
        subsd     %xmm2, %xmm7
        addsd     %xmm5, %xmm1
        movq      %xmm2, (%rsp)
        addsd     %xmm7, %xmm2
        addsd     %xmm7, %xmm3
        movq      (%rsp), %xmm7
        addsd     %xmm4, %xmm0
        subsd     %xmm2, %xmm6
        addsd     %xmm5, %xmm1
        movq      (%rsp), %xmm5
        movq      %xmm0, %xmm2
        movq      %xmm0, %xmm4
        addsd     %xmm3, %xmm6
        addsd     %xmm1, %xmm0
        subsd     %xmm0, %xmm2
        movq      %xmm0, %xmm3
        addsd     %xmm2, %xmm0
        addsd     %xmm2, %xmm1
        pshufd    $221, %xmm7, %xmm2
        subsd     %xmm0, %xmm4
        movq      32(%r8), %xmm0
        addsd     %xmm4, %xmm1
        pand      %xmm7, %xmm0
        addsd     %xmm3, %xmm7
        addsd     %xmm6, %xmm1
        subsd     %xmm7, %xmm5
        subsd     %xmm0, %xmm7
        movq      %xmm0, %xmm4
        addsd     %xmm5, %xmm3
        movq      %xmm0, %xmm5
        addsd     %xmm7, %xmm1
        movq      %xmm0, %xmm7
        psrlq     $29, %xmm0
        addsd     %xmm3, %xmm1
        pand      48(%r8), %xmm0
        movq      152(%r8), %xmm3
        pxor      64(%r8), %xmm0
        addsd     %xmm1, %xmm4
        paddd     80(%r8), %xmm0
        psubd     96(%r8), %xmm2
        rsqrtss   %xmm0, %xmm0
        pand      112(%r8), %xmm2
        psrld     $3, %xmm0
        psrld     $1, %xmm2
        paddd     128(%r8), %xmm0
        psubd     %xmm2, %xmm0
        psllq     $32, %xmm0
        movq      %xmm0, %xmm2
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm7
        mulsd     %xmm1, %xmm0
        subsd     144(%r8), %xmm7
        addsd     %xmm0, %xmm7
        movq      160(%r8), %xmm0
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm0
        addsd     168(%r8), %xmm3
        addsd     176(%r8), %xmm0
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm0
        addsd     184(%r8), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm0, %xmm3
        movq      8(%rsp), %xmm0
        mulsd     %xmm2, %xmm3
        mulsd     %xmm4, %xmm3
        movq      8(%rsp), %xmm4
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        movq      %xmm5, %xmm2
        addsd     8(%rsp), %xmm5
        movq      32(%r8), %xmm7
        addsd     %xmm3, %xmm1
        subsd     %xmm5, %xmm4
        movq      %xmm5, %xmm3
        addsd     %xmm4, %xmm5
        addsd     %xmm2, %xmm4
        subsd     %xmm5, %xmm0
        addsd     %xmm0, %xmm4
        movq      208(%r8), %xmm0
        addsd     %xmm1, %xmm4
        movq      %xmm3, %xmm1
        movq      %xmm3, %xmm2
        addsd     %xmm4, %xmm3
        subsd     %xmm3, %xmm1
        mulsd     %xmm3, %xmm0
        pand      %xmm0, %xmm7
        addsd     %xmm1, %xmm3
        addsd     %xmm4, %xmm1
        movq      %xmm7, %xmm4
        subsd     %xmm2, %xmm3
        movq      %xmm7, %xmm5
        pshufd    $221, %xmm0, %xmm2
        subsd     %xmm7, %xmm0
        subsd     %xmm3, %xmm1
        mulsd     208(%r8), %xmm1
        addsd     %xmm0, %xmm1
        movq      %xmm7, %xmm0
        psrlq     $29, %xmm7
        movq      152(%r8), %xmm3
        pand      48(%r8), %xmm7
        psubd     96(%r8), %xmm2
        pxor      64(%r8), %xmm7
        addsd     %xmm1, %xmm4
        paddd     80(%r8), %xmm7
        rsqrtss   %xmm7, %xmm7
        psrld     $3, %xmm7
        pand      112(%r8), %xmm2
        psrld     $1, %xmm2
        paddd     128(%r8), %xmm7
        psubd     %xmm2, %xmm7
        psllq     $32, %xmm7
        movq      %xmm7, %xmm2
        mulsd     %xmm7, %xmm7
        mulsd     %xmm7, %xmm0
        mulsd     %xmm1, %xmm7
        subsd     144(%r8), %xmm0
        addsd     %xmm7, %xmm0
        movq      160(%r8), %xmm7
        movq      %xmm0, %xmm6
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm7
        addsd     168(%r8), %xmm3
        addsd     176(%r8), %xmm7
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm7
        addsd     184(%r8), %xmm3
        mulsd     %xmm6, %xmm3
        movq      16(%rsp), %xmm6
        addsd     %xmm7, %xmm3
        mulsd     208(%r8), %xmm6
        mulsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm4
        pxor      %xmm7, %xmm7
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        cmpnlesd  32(%rsp), %xmm7
        addsd     %xmm4, %xmm1
        movq      32(%r8), %xmm4
        pand      %xmm6, %xmm4
        subsd     %xmm4, %xmm6
        addsd     %xmm5, %xmm1
        movq      %xmm2, %xmm5
        mulsd     %xmm4, %xmm2
        mulsd     %xmm3, %xmm4
        movq      %xmm1, %xmm0
        pand      %xmm7, %xmm0
        mulsd     %xmm6, %xmm3
        mulsd     %xmm5, %xmm6
        movq      40(%rsp), %xmm5
        addsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm6
        addsd     %xmm6, %xmm2
        movq      %xmm7, %xmm6
        pandn     %xmm2, %xmm6
        pand      %xmm7, %xmm2
        pandn     %xmm1, %xmm7
        pand      192(%r8), %xmm5
        por       %xmm0, %xmm6
        por       %xmm7, %xmm2
        por       %xmm5, %xmm6
        movq      24(%rsp), %xmm0
        movq      24(%rsp), %xmm1
        mulsd     %xmm2, %xmm0
        mulsd     %xmm6, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_0.0.1:
        addl      $591396864, %eax
        addl      $591396864, %ebx
        cmpl      $2146435072, %eax
        jge       .L_2TAG_PACKET_4.0.1
        cmpl      $2146435072, %ebx
        jge       .L_2TAG_PACKET_5.0.1
        movl      32(%rsp), %edx
        orl       %eax, %edx
        movl      40(%rsp), %edx
        je        .L_2TAG_PACKET_6.0.1
        orl       %ebx, %edx
        je        .L_2TAG_PACKET_7.0.1
        movl      %eax, %edx
        subl      %ebx, %edx
        cmpl      $60817408, %edx
        jg        .L_2TAG_PACKET_1.0.1
        cmpl      $-60817408, %edx
        jl        .L_2TAG_PACKET_2.0.1
        cmpl      $1072693248, %eax
        jl        .L_2TAG_PACKET_8.0.1
        mulsd     216(%r8), %xmm0
        mulsd     216(%r8), %xmm1
        movq      224(%r8), %xmm7
        movq      32(%r8), %xmm2
        movq      32(%r8), %xmm3
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        movq      %xmm2, %xmm4
        movq      %xmm3, %xmm5
        movq      %xmm0, 8(%rsp)
        movq      %xmm1, 16(%rsp)
        jmp       .L_2TAG_PACKET_3.0.1
.L_2TAG_PACKET_8.0.1:
        mulsd     232(%r8), %xmm0
        mulsd     232(%r8), %xmm1
        movq      240(%r8), %xmm7
        movq      32(%r8), %xmm2
        movq      32(%r8), %xmm3
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        movq      %xmm2, %xmm4
        movq      %xmm3, %xmm5
        movq      %xmm0, 8(%rsp)
        movq      %xmm1, 16(%rsp)
        jmp       .L_2TAG_PACKET_3.0.1
.L_2TAG_PACKET_2.0.1:
.L_2TAG_PACKET_6.0.1:
        cmpl      $2097152, %ebx
        jl        .L_2TAG_PACKET_9.0.1
        mulsd     208(%r8), %xmm1
        sqrtsd    %xmm1, %xmm0
        movsd     40(%rsp), %xmm1
        pand      192(%r8), %xmm1
        por       %xmm0, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_9.0.1:
        mulsd     248(%r8), %xmm1
        sqrtsd    %xmm1, %xmm0
        movsd     40(%rsp), %xmm1
        pand      192(%r8), %xmm1
        mulsd     256(%r8), %xmm0
        por       %xmm0, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_7.0.1:
        sqrtsd    %xmm0, %xmm0
        movl      36(%rsp), %eax
        cmpl      $0, %eax
        jl        .L_2TAG_PACKET_10.0.1
        movsd     40(%rsp), %xmm1
        pand      192(%r8), %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_10.0.1:
        movsd     40(%rsp), %xmm1
        pand      192(%r8), %xmm1
        por       %xmm0, %xmm1
        pxor      %xmm0, %xmm0
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_1.0.1:
        cmpl      $57671680, %ebx
        movsd     %xmm0, %xmm4
        subsd     %xmm2, %xmm0
        movq      %xmm2, %xmm5
        movq      %xmm2, %xmm7
        pshufd    $221, %xmm2, %xmm1
        jl        .L_2TAG_PACKET_11.0.1
        mulsd     288(%r8), %xmm2
        mulsd     288(%r8), %xmm0
        mulsd     288(%r8), %xmm4
        movsd     %xmm2, %xmm5
        movsd     %xmm2, %xmm7
        pshufd    $221, %xmm2, %xmm1
        shrl      $21, %eax
        shrl      $20, %ebx
        psrlq     $29, %xmm2
        pand      48(%r8), %xmm2
        pxor      64(%r8), %xmm2
        paddd     80(%r8), %xmm2
        rsqrtss   %xmm2, %xmm2
        subl      $511, %eax
        subl      $1023, %ebx
        psubd     96(%r8), %xmm1
        psrld     $3, %xmm2
        pand      112(%r8), %xmm1
        psrld     $1, %xmm1
        paddd     128(%r8), %xmm2
        psubd     %xmm1, %xmm2
        subl      %eax, %ebx
        psllq     $32, %xmm2
        movq      %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm2, %xmm7
        mulsd     %xmm0, %xmm2
        subsd     144(%r8), %xmm7
        cmpl      $-1020, %ebx
        addsd     %xmm2, %xmm7
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        movq      152(%r8), %xmm3
        movq      160(%r8), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     168(%r8), %xmm3
        addsd     176(%r8), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     184(%r8), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm2, %xmm3
        mulsd     %xmm1, %xmm3
        mulsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm0
        mulsd     304(%r8), %xmm5
        mulsd     304(%r8), %xmm0
        mulsd     296(%r8), %xmm1
        mulsd     296(%r8), %xmm3
        jl        .L_2TAG_PACKET_12.0.1
        movsd     16(%rsp), %xmm2
        addsd     %xmm5, %xmm0
        mulsd     208(%r8), %xmm1
        mulsd     208(%r8), %xmm3
        movq      32(%r8), %xmm4
        pand      %xmm2, %xmm4
        subsd     %xmm4, %xmm2
        movq      %xmm1, %xmm5
        mulsd     %xmm4, %xmm1
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm3
        mulsd     %xmm5, %xmm2
        addsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm2
        addsd     %xmm2, %xmm1
.L_2TAG_PACKET_13.0.1:
        movsd     40(%rsp), %xmm3
        pand      192(%r8), %xmm3
        movl      36(%rsp), %eax
        cmpl      $0, %eax
        jl        .L_2TAG_PACKET_14.0.1
        por       %xmm3, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_14.0.1:
        por       %xmm0, %xmm3
        movq      %xmm1, %xmm0
        movq      %xmm3, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_11.0.1:
        psrlq     $29, %xmm2
        pand      48(%r8), %xmm2
        pxor      64(%r8), %xmm2
        paddd     80(%r8), %xmm2
        rsqrtss   %xmm2, %xmm2
        psubd     96(%r8), %xmm1
        psrld     $3, %xmm2
        pand      112(%r8), %xmm1
        psrld     $1, %xmm1
        paddd     128(%r8), %xmm2
        psubd     %xmm1, %xmm2
        psllq     $32, %xmm2
        movq      %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm2, %xmm7
        mulsd     %xmm0, %xmm2
        subsd     144(%r8), %xmm7
        addsd     %xmm2, %xmm7
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        movq      152(%r8), %xmm3
        movq      160(%r8), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     168(%r8), %xmm3
        addsd     176(%r8), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     184(%r8), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm2, %xmm3
        mulsd     %xmm1, %xmm3
        mulsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm0
.L_2TAG_PACKET_12.0.1:
        addsd     %xmm5, %xmm0
        movq      16(%rsp), %xmm2
        mulsd     264(%r8), %xmm2
        mulsd     248(%r8), %xmm2
        movq      32(%r8), %xmm4
        pand      %xmm2, %xmm4
        subsd     %xmm4, %xmm2
        movq      %xmm1, %xmm5
        mulsd     %xmm4, %xmm1
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm3
        mulsd     %xmm5, %xmm2
        addsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm2
        movq      %xmm1, %xmm3
        addsd     %xmm2, %xmm1
        pextrw    $3, %xmm1, %eax
        mulsd     272(%r8), %xmm1
        mulsd     280(%r8), %xmm1
        cmpl      $19216, %eax
        jge       .L_2TAG_PACKET_13.0.1
        movq      272(%r8), %xmm5
        mulsd     %xmm5, %xmm5
        cmpl      $19056, %eax
        jle       .L_2TAG_PACKET_13.0.1
        movq      32(%r8), %xmm1
        pand      %xmm3, %xmm1
        subsd     %xmm1, %xmm3
        addsd     %xmm3, %xmm2
        mulsd     272(%r8), %xmm1
        mulsd     272(%r8), %xmm2
        mulsd     280(%r8), %xmm1
        mulsd     280(%r8), %xmm2
        addsd     %xmm2, %xmm1
        jmp       .L_2TAG_PACKET_13.0.1
.L_2TAG_PACKET_5.0.1:
.L_2TAG_PACKET_15.0.1:
        movq      144(%r8), %xmm0
        addsd     40(%rsp), %xmm0
        movq      %xmm0, %xmm1
        mulsd     %xmm0, %xmm0
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_4.0.1:
        movsd     32(%rsp), %xmm1
        mulsd     %xmm1, %xmm1
        cmpl      $2146435072, %ebx
        jl        .L_2TAG_PACKET_16.0.1
        movl      40(%rsp), %ebx
        jg        .L_2TAG_PACKET_17.0.1
        andl      %ebx, %ebx
        jne       .L_2TAG_PACKET_17.0.1
        jmp       .L_2TAG_PACKET_15.0.1
.L_2TAG_PACKET_17.0.1:
        movq      144(%r8), %xmm0
        addsd     40(%rsp), %xmm0
        cmpl      $2146435072, %eax
        movl      32(%rsp), %ebx
        jg        .L_2TAG_PACKET_18.0.1
        andl      %ebx, %ebx
        movl      36(%rsp), %ebx
        jne       .L_2TAG_PACKET_18.0.1
        cmpl      $2146435072, %ebx
        je        .L_2TAG_PACKET_19.0.1
        mulsd     %xmm1, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_18.0.1:
.L_2TAG_PACKET_19.0.1:
        movq      32(%rsp), %xmm0
        movq      40(%rsp), %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_16.0.1:
        cmpl      $2146435072, %eax
        movl      32(%rsp), %eax
        jg        .L_2TAG_PACKET_20.0.1
        andl      %eax, %eax
        jne       .L_2TAG_PACKET_20.0.1
        pxor      %xmm2, %xmm2
        movl      36(%rsp), %eax
        movq      40(%rsp), %xmm3
        testl     $-2147483648, %eax
        pand      192(%r8), %xmm3
        jne       .L_2TAG_PACKET_21.0.1
        por       %xmm3, %xmm2
        movq      %xmm1, %xmm0
        movq      %xmm2, %xmm1
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_21.0.1:
        por       %xmm3, %xmm1
        movq      %xmm2, %xmm0
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
.L_2TAG_PACKET_20.0.1:
        movq      %xmm1, %xmm0
        movq      64(%rsp), %rbx
        movq      56(%rsp), %rsp
        ret       
	.cfi_def_cfa_offset 8
..B1.3:
        .align    16,0x90
	.cfi_endproc
	.type	csqrt,@function
	.size	csqrt,.-csqrt
	.data
# -- End  csqrt
	.section .rodata, "a"
	.align 16
	.align 16
csqrt_table:
	.long	4160749568
	.long	2147483647
	.long	4160749568
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4160749568
	.long	4294967295
	.long	4160749568
	.long	4294967295
	.long	16777215
	.long	16777215
	.long	16777215
	.long	16777215
	.long	8388608
	.long	8388608
	.long	8388608
	.long	8388608
	.long	1065353216
	.long	1065353216
	.long	1065353216
	.long	1065353216
	.long	1048576
	.long	1048576
	.long	1048576
	.long	1048576
	.long	4292870144
	.long	4292870144
	.long	4292870144
	.long	4292870144
	.long	1475346432
	.long	1475346432
	.long	1475346432
	.long	1475346432
	.long	0
	.long	1072693248
	.long	0
	.long	3218046976
	.long	0
	.long	1070694400
	.long	0
	.long	3218341888
	.long	0
	.long	1071120384
	.long	0
	.long	3219128320
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	1071644672
	.long	0
	.long	533725184
	.long	0
	.long	1342177280
	.long	0
	.long	1722810368
	.long	0
	.long	747634688
	.long	0
	.long	1281359872
	.long	0
	.long	967835648
	.long	0
	.long	2121269248
	.long	0
	.long	24117248
	.long	0
	.long	862978048
	.long	0
	.long	1062207488
	.long	0
	.long	1067450368
	.long	0
	.long	1077936128
	.type	csqrt_table,@object
	.size	csqrt_table,312
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
