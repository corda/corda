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
	.file "csqrt_wmt.c"
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
# -- Begin  csqrt
	.text
       .align    16,0x90
	.globl csqrt
csqrt:
# parameter 1: 12 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $184, %esp
        movl      %esi, 128(%esp)
        call      static_func
        movl      %eax, %esi
        movl      192(%esp), %edx
        movsd     196(%esp), %xmm0
        movsd     204(%esp), %xmm1
        movl      %edx, (%esp)
        movsd     %xmm0, 16(%esp)
        movsd     %xmm1, 24(%esp)
        lea       16(%esp), %ecx
        movq      (%esi), %xmm2
        movl      %edx, (%esp)
        movl      %ebx, 40(%esp)
        movl      %ecx, 44(%esp)
        movq      (%esi), %xmm3
        movq      32(%esi), %xmm7
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        pand      16(%esi), %xmm0
        pand      16(%esi), %xmm1
        movq      %xmm2, %xmm4
        movq      %xmm3, %xmm5
        movl      4(%ecx), %eax
        movl      12(%ecx), %ebx
        andl      $2147483647, %eax
        andl      $2147483647, %ebx
        subl      $591396864, %eax
        movq      %xmm0, 64(%esp)
        subl      $591396864, %ebx
        cmpl      $1017118720, %eax
        movq      %xmm1, 72(%esp)
        jae       .L_2TAG_PACKET_0.0.2
        cmpl      $1017118720, %ebx
        jae       .L_2TAG_PACKET_0.0.2
        addl      $591396864, %eax
        addl      $591396864, %ebx
        movl      %eax, %edx
        subl      %ebx, %edx
        cmpl      $60817408, %edx
        jg        .L_2TAG_PACKET_1.0.2
        cmpl      $-60817408, %edx
        movl      (%esp), %edx
        jl        .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_3.0.2:
        subsd     %xmm2, %xmm0
        subsd     %xmm3, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm3, %xmm3
        mulsd     %xmm0, %xmm4
        mulsd     %xmm1, %xmm5
        movq      %xmm2, %xmm6
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        movq      %xmm7, 80(%esp)
        movq      %xmm2, %xmm7
        addsd     %xmm3, %xmm2
        addsd     %xmm4, %xmm0
        subsd     %xmm2, %xmm7
        addsd     %xmm5, %xmm1
        movq      %xmm2, 56(%esp)
        addsd     %xmm7, %xmm2
        addsd     %xmm7, %xmm3
        movq      56(%esp), %xmm7
        addsd     %xmm4, %xmm0
        subsd     %xmm2, %xmm6
        addsd     %xmm5, %xmm1
        movq      56(%esp), %xmm5
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
        movq      48(%esi), %xmm0
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
        pand      64(%esi), %xmm0
        movq      160(%esi), %xmm3
        pxor      80(%esi), %xmm0
        addsd     %xmm1, %xmm4
        paddd     96(%esi), %xmm0
        psubd     112(%esi), %xmm2
        rsqrtss   %xmm0, %xmm0
        pand      128(%esi), %xmm2
        psrld     $3, %xmm0
        psrld     $1, %xmm2
        paddd     144(%esi), %xmm0
        psubd     %xmm2, %xmm0
        psllq     $32, %xmm0
        movq      %xmm0, %xmm2
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm7
        mulsd     %xmm1, %xmm0
        subsd     32(%esi), %xmm7
        addsd     %xmm0, %xmm7
        movq      176(%esi), %xmm0
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm0
        addsd     192(%esi), %xmm3
        addsd     208(%esi), %xmm0
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm0
        addsd     224(%esi), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm0, %xmm3
        movq      64(%esp), %xmm0
        mulsd     %xmm2, %xmm3
        mulsd     %xmm4, %xmm3
        movq      64(%esp), %xmm4
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        movq      %xmm5, %xmm2
        addsd     64(%esp), %xmm5
        movq      48(%esi), %xmm7
        addsd     %xmm3, %xmm1
        subsd     %xmm5, %xmm4
        movq      %xmm5, %xmm3
        addsd     %xmm4, %xmm5
        addsd     %xmm2, %xmm4
        subsd     %xmm5, %xmm0
        addsd     %xmm0, %xmm4
        movq      240(%esi), %xmm0
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
        mulsd     240(%esi), %xmm1
        addsd     %xmm0, %xmm1
        movq      %xmm7, %xmm0
        psrlq     $29, %xmm7
        movq      160(%esi), %xmm3
        pand      64(%esi), %xmm7
        psubd     112(%esi), %xmm2
        pxor      80(%esi), %xmm7
        addsd     %xmm1, %xmm4
        paddd     96(%esi), %xmm7
        rsqrtss   %xmm7, %xmm7
        psrld     $3, %xmm7
        pand      128(%esi), %xmm2
        psrld     $1, %xmm2
        paddd     144(%esi), %xmm7
        psubd     %xmm2, %xmm7
        psllq     $32, %xmm7
        movq      %xmm7, %xmm2
        mulsd     %xmm7, %xmm7
        mulsd     %xmm7, %xmm0
        mulsd     %xmm1, %xmm7
        subsd     32(%esi), %xmm0
        addsd     %xmm7, %xmm0
        movq      176(%esi), %xmm7
        movq      %xmm0, %xmm6
        mulsd     %xmm0, %xmm0
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm7
        addsd     192(%esi), %xmm3
        addsd     208(%esi), %xmm7
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm7
        addsd     224(%esi), %xmm3
        mulsd     %xmm6, %xmm3
        movq      72(%esp), %xmm6
        addsd     %xmm7, %xmm3
        mulsd     240(%esi), %xmm6
        mulsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm4
        pxor      %xmm7, %xmm7
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm5
        cmpnlesd  (%ecx), %xmm7
        addsd     %xmm4, %xmm1
        movq      48(%esi), %xmm4
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
        movq      8(%ecx), %xmm5
        addsd     %xmm4, %xmm3
        addsd     %xmm3, %xmm6
        addsd     %xmm6, %xmm2
        movq      %xmm7, %xmm6
        pandn     %xmm2, %xmm6
        pand      %xmm7, %xmm2
        pandn     %xmm1, %xmm7
        pand      256(%esi), %xmm5
        por       %xmm6, %xmm0
        por       %xmm7, %xmm2
        por       %xmm5, %xmm0
        mulsd     80(%esp), %xmm2
        mulsd     80(%esp), %xmm0
        movsd     %xmm2, (%edx)
        movsd     %xmm0, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_0.0.2:
        addl      $591396864, %eax
        addl      $591396864, %ebx
        cmpl      $2146435072, %eax
        jge       .L_2TAG_PACKET_5.0.2
        cmpl      $2146435072, %ebx
        jge       .L_2TAG_PACKET_6.0.2
        movl      (%ecx), %edx
        orl       %eax, %edx
        movl      8(%ecx), %edx
        je        .L_2TAG_PACKET_7.0.2
        orl       %ebx, %edx
        je        .L_2TAG_PACKET_8.0.2
        movl      %eax, %edx
        subl      %ebx, %edx
        cmpl      $60817408, %edx
        jg        .L_2TAG_PACKET_1.0.2
        cmpl      $-60817408, %edx
        jl        .L_2TAG_PACKET_2.0.2
        cmpl      $1072693248, %eax
        jl        .L_2TAG_PACKET_9.0.2
        mulsd     272(%esi), %xmm0
        mulsd     272(%esi), %xmm1
        movq      288(%esi), %xmm7
        movq      48(%esi), %xmm2
        movq      48(%esi), %xmm3
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        movq      %xmm2, %xmm4
        movl      (%esp), %edx
        movq      %xmm3, %xmm5
        movq      %xmm0, 64(%esp)
        movq      %xmm1, 72(%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_9.0.2:
        mulsd     304(%esi), %xmm0
        mulsd     304(%esi), %xmm1
        movq      320(%esi), %xmm7
        movq      48(%esi), %xmm2
        movq      48(%esi), %xmm3
        pand      %xmm0, %xmm2
        pand      %xmm1, %xmm3
        movq      %xmm2, %xmm4
        movl      (%esp), %edx
        movq      %xmm3, %xmm5
        movq      %xmm0, 64(%esp)
        movq      %xmm1, 72(%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_2.0.2:
.L_2TAG_PACKET_7.0.2:
        cmpl      $2097152, %ebx
        movl      (%esp), %edx
        jl        .L_2TAG_PACKET_10.0.2
        mulsd     240(%esi), %xmm1
        sqrtsd    %xmm1, %xmm1
        movsd     8(%ecx), %xmm2
        pand      256(%esi), %xmm2
        movsd     %xmm1, (%edx)
        por       %xmm2, %xmm1
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_10.0.2:
        mulsd     336(%esi), %xmm1
        sqrtsd    %xmm1, %xmm1
        movsd     8(%ecx), %xmm2
        pand      256(%esi), %xmm2
        mulsd     352(%esi), %xmm1
        movsd     %xmm1, (%edx)
        por       %xmm2, %xmm1
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_8.0.2:
        sqrtsd    %xmm0, %xmm0
        movl      4(%ecx), %eax
        cmpl      $0, %eax
        movl      (%esp), %edx
        jl        .L_2TAG_PACKET_11.0.2
        movsd     8(%ecx), %xmm2
        pand      256(%esi), %xmm2
        movsd     %xmm2, 8(%edx)
        movsd     %xmm0, (%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_11.0.2:
        pxor      %xmm3, %xmm3
        movsd     %xmm3, (%edx)
        movsd     8(%ecx), %xmm2
        pand      256(%esi), %xmm2
        por       %xmm0, %xmm2
        movsd     %xmm2, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_1.0.2:
        cmpl      $57671680, %ebx
        movsd     %xmm0, %xmm4
        subsd     %xmm2, %xmm0
        movq      %xmm2, %xmm5
        movq      %xmm2, %xmm7
        pshufd    $221, %xmm2, %xmm1
        jl        .L_2TAG_PACKET_12.0.2
        mulsd     416(%esi), %xmm2
        mulsd     416(%esi), %xmm0
        mulsd     416(%esi), %xmm4
        movsd     %xmm2, %xmm5
        movsd     %xmm2, %xmm7
        pshufd    $221, %xmm2, %xmm1
        shrl      $21, %eax
        shrl      $20, %ebx
        psrlq     $29, %xmm2
        pand      64(%esi), %xmm2
        pxor      80(%esi), %xmm2
        paddd     96(%esi), %xmm2
        rsqrtss   %xmm2, %xmm2
        subl      $511, %eax
        subl      $1023, %ebx
        psubd     112(%esi), %xmm1
        psrld     $3, %xmm2
        pand      128(%esi), %xmm1
        psrld     $1, %xmm1
        paddd     144(%esi), %xmm2
        psubd     %xmm1, %xmm2
        subl      %eax, %ebx
        psllq     $32, %xmm2
        movq      %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm2, %xmm7
        mulsd     %xmm0, %xmm2
        subsd     32(%esi), %xmm7
        cmpl      $-1020, %ebx
        addsd     %xmm2, %xmm7
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        movq      160(%esi), %xmm3
        movq      176(%esi), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     192(%esi), %xmm3
        addsd     208(%esi), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     224(%esi), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm2, %xmm3
        mulsd     %xmm1, %xmm3
        mulsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm0
        mulsd     448(%esi), %xmm5
        mulsd     448(%esi), %xmm0
        mulsd     432(%esi), %xmm1
        mulsd     432(%esi), %xmm3
        jl        .L_2TAG_PACKET_13.0.2
        movsd     72(%esp), %xmm2
        addsd     %xmm5, %xmm0
        mulsd     240(%esi), %xmm1
        mulsd     240(%esi), %xmm3
        movq      48(%esi), %xmm4
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
.L_2TAG_PACKET_14.0.2:
        movsd     8(%ecx), %xmm3
        pand      256(%esi), %xmm3
        movl      4(%ecx), %eax
        cmpl      $0, %eax
        movl      (%esp), %edx
        jl        .L_2TAG_PACKET_15.0.2
        por       %xmm3, %xmm1
        movsd     %xmm0, (%edx)
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_15.0.2:
        por       %xmm3, %xmm0
        movsd     %xmm1, (%edx)
        movsd     %xmm0, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_12.0.2:
        psrlq     $29, %xmm2
        pand      64(%esi), %xmm2
        pxor      80(%esi), %xmm2
        paddd     96(%esi), %xmm2
        rsqrtss   %xmm2, %xmm2
        psubd     112(%esi), %xmm1
        psrld     $3, %xmm2
        pand      128(%esi), %xmm1
        psrld     $1, %xmm1
        paddd     144(%esi), %xmm2
        psubd     %xmm1, %xmm2
        psllq     $32, %xmm2
        movq      %xmm2, %xmm1
        mulsd     %xmm2, %xmm2
        mulsd     %xmm2, %xmm7
        mulsd     %xmm0, %xmm2
        subsd     32(%esi), %xmm7
        addsd     %xmm2, %xmm7
        movq      %xmm7, %xmm6
        mulsd     %xmm7, %xmm7
        movq      160(%esi), %xmm3
        movq      176(%esi), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     192(%esi), %xmm3
        addsd     208(%esi), %xmm2
        mulsd     %xmm7, %xmm3
        mulsd     %xmm7, %xmm2
        addsd     224(%esi), %xmm3
        mulsd     %xmm6, %xmm3
        addsd     %xmm2, %xmm3
        mulsd     %xmm1, %xmm3
        mulsd     %xmm3, %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm0
.L_2TAG_PACKET_13.0.2:
        addsd     %xmm5, %xmm0
        movq      72(%esp), %xmm2
        mulsd     368(%esi), %xmm2
        mulsd     336(%esi), %xmm2
        movq      48(%esi), %xmm4
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
        mulsd     384(%esi), %xmm1
        mulsd     400(%esi), %xmm1
        cmpl      $19216, %eax
        jge       .L_2TAG_PACKET_14.0.2
        movq      384(%esi), %xmm5
        mulsd     %xmm5, %xmm5
        cmpl      $19056, %eax
        jle       .L_2TAG_PACKET_14.0.2
        movq      48(%esi), %xmm1
        pand      %xmm3, %xmm1
        subsd     %xmm1, %xmm3
        addsd     %xmm3, %xmm2
        mulsd     384(%esi), %xmm1
        mulsd     384(%esi), %xmm2
        mulsd     400(%esi), %xmm1
        mulsd     400(%esi), %xmm2
        addsd     %xmm2, %xmm1
        jmp       .L_2TAG_PACKET_14.0.2
.L_2TAG_PACKET_6.0.2:
.L_2TAG_PACKET_16.0.2:
        movq      32(%esi), %xmm0
        addsd     8(%ecx), %xmm0
        movsd     %xmm0, 8(%edx)
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, (%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_5.0.2:
        movsd     (%ecx), %xmm1
        mulsd     %xmm1, %xmm1
        cmpl      $2146435072, %ebx
        jl        .L_2TAG_PACKET_17.0.2
        movl      8(%ecx), %ebx
        jg        .L_2TAG_PACKET_18.0.2
        andl      %ebx, %ebx
        jne       .L_2TAG_PACKET_18.0.2
        jmp       .L_2TAG_PACKET_16.0.2
.L_2TAG_PACKET_18.0.2:
        movq      32(%esi), %xmm0
        addsd     8(%ecx), %xmm0
        cmpl      $2146435072, %eax
        movl      (%ecx), %ebx
        jg        .L_2TAG_PACKET_19.0.2
        andl      %ebx, %ebx
        movl      4(%ecx), %ebx
        jne       .L_2TAG_PACKET_19.0.2
        cmpl      $2146435072, %ebx
        je        .L_2TAG_PACKET_20.0.2
        mulsd     %xmm1, %xmm1
        movsd     %xmm0, (%edx)
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_19.0.2:
.L_2TAG_PACKET_20.0.2:
        movsd     %xmm1, (%edx)
        movsd     %xmm0, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_17.0.2:
        cmpl      $2146435072, %eax
        movl      (%ecx), %eax
        jg        .L_2TAG_PACKET_21.0.2
        andl      %eax, %eax
        jne       .L_2TAG_PACKET_21.0.2
        pxor      %xmm2, %xmm2
        movl      4(%ecx), %eax
        movq      8(%ecx), %xmm3
        testl     $-2147483648, %eax
        pand      256(%esi), %xmm3
        jne       .L_2TAG_PACKET_22.0.2
        por       %xmm3, %xmm2
        movsd     %xmm1, (%edx)
        movsd     %xmm2, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_22.0.2:
        por       %xmm3, %xmm1
        movsd     %xmm2, (%edx)
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
        jmp       .L_2TAG_PACKET_4.0.2
.L_2TAG_PACKET_21.0.2:
        movsd     %xmm1, (%edx)
        movsd     %xmm1, 8(%edx)
        movl      40(%esp), %ebx
.L_2TAG_PACKET_4.0.2:
        movl      128(%esp), %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.3:
        .align    16,0x90
	.type	csqrt,@function
	.size	csqrt,.-csqrt
	.data
# -- End  csqrt
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	4160749568
	.long	2147483647
	.long	4160749568
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	4294967295
	.long	2147483647
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
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
	.long	3218046976
	.long	0
	.long	3218046976
	.long	0
	.long	1070694400
	.long	0
	.long	1070694400
	.long	0
	.long	3218341888
	.long	0
	.long	3218341888
	.long	0
	.long	1071120384
	.long	0
	.long	1071120384
	.long	0
	.long	3219128320
	.long	0
	.long	3219128320
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	0
	.long	533725184
	.long	0
	.long	533725184
	.long	0
	.long	1342177280
	.long	0
	.long	1342177280
	.long	0
	.long	1722810368
	.long	0
	.long	1722810368
	.long	0
	.long	747634688
	.long	0
	.long	747634688
	.long	0
	.long	1281359872
	.long	0
	.long	1281359872
	.long	0
	.long	967835648
	.long	0
	.long	967835648
	.long	0
	.long	2121269248
	.long	0
	.long	2121269248
	.long	0
	.long	24117248
	.long	0
	.long	24117248
	.long	0
	.long	862978048
	.long	0
	.long	862978048
	.long	0
	.long	1062207488
	.long	0
	.long	1062207488
	.long	0
	.long	1067450368
	.long	0
	.long	1067450368
	.long	0
	.long	1077936128
	.long	0
	.long	1077936128
	.type	static_const_table,@object
	.size	static_const_table,464
	.data
	.section .note.GNU-stack, ""
# End
