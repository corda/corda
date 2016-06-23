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
	.file "intel_quad_exception.c"
	.text
..TXTST0:
# -- Begin  __dpml_exception
	.text
       .align    16,0x90
	.globl __dpml_exception
__dpml_exception:
# parameter 1: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___dpml_exception.1:
..L2:

        movq      $0x7fffffffffffffff, %rdx
        movq      (%rdi), %rsi
        andq      %rsi, %rdx
        testq     %rsi, %rsi
        lea       7+__dpml_response_table(%rip), %rax
        lea       6+__dpml_response_table(%rip), %rcx
        lea       32(%rdi), %r11
        cmovl     %rdx, %rsi
        movq      $0xffffffff07ffffff, %rdx
        andq      %rsi, %rdx
        movq      %rsi, %r9
        shrq      $27, %r9
        movq      __dpml_globals_table@GOTPCREL(%rip), %r10
        movzbl    (%rax,%rdx,8), %r8d
        shlq      $5, %r8
        movzbl    (%rcx,%rdx,8), %esi
        addq      $-4, %rdx
        lea       (%r8,%r9,8), %rcx
        addq      %r10, %rcx
        cmpl      $4, %esi
        cmove     %r11, %rcx
        cmpq      $171, %rdx
        ja        ..B1.69
..B1.2:
        movl      %edx, %r8d
        lea       .2.9_2.switchtab.1(%rip), %rdx
        movzbl    (%rdx,%r8), %edx
        lea       ..1.1_0.TAG.12.0.1(%rip), %rax
        addq      %rdx, %rax
        jmp       *%rax
..1.1_0.TAG.12.0.1:
..1.1_0.TAG.9.0.1:
..1.1_0.TAG.11.0.1:
..1.1_0.TAG.8.0.1:
..B1.59:
        xorl      %esi, %esi
        lea       32(%rdi), %rcx
        jmp       ..B1.70
..1.1_0.TAG.171.0.1:
..1.1_0.TAG.170.0.1:
..1.1_0.TAG.145.0.1:
..1.1_0.TAG.144.0.1:
..1.1_0.TAG.140.0.1:
..1.1_0.TAG.139.0.1:
..1.1_0.TAG.138.0.1:
..1.1_0.TAG.136.0.1:
..1.1_0.TAG.135.0.1:
..1.1_0.TAG.133.0.1:
..1.1_0.TAG.128.0.1:
..1.1_0.TAG.127.0.1:
..1.1_0.TAG.126.0.1:
..1.1_0.TAG.125.0.1:
..1.1_0.TAG.116.0.1:
..1.1_0.TAG.115.0.1:
..1.1_0.TAG.114.0.1:
..1.1_0.TAG.112.0.1:
..1.1_0.TAG.111.0.1:
..1.1_0.TAG.104.0.1:
..1.1_0.TAG.103.0.1:
..1.1_0.TAG.102.0.1:
..1.1_0.TAG.101.0.1:
..1.1_0.TAG.100.0.1:
..1.1_0.TAG.99.0.1:
..1.1_0.TAG.98.0.1:
..1.1_0.TAG.97.0.1:
..1.1_0.TAG.79.0.1:
..1.1_0.TAG.76.0.1:
..1.1_0.TAG.67.0.1:
..1.1_0.TAG.64.0.1:
..1.1_0.TAG.62.0.1:
..1.1_0.TAG.61.0.1:
..1.1_0.TAG.59.0.1:
..1.1_0.TAG.58.0.1:
..1.1_0.TAG.56.0.1:
..1.1_0.TAG.55.0.1:
..1.1_0.TAG.34.0.1:
..1.1_0.TAG.33.0.1:
..1.1_0.TAG.32.0.1:
..1.1_0.TAG.31.0.1:
..1.1_0.TAG.30.0.1:
..1.1_0.TAG.29.0.1:
..1.1_0.TAG.28.0.1:
..1.1_0.TAG.26.0.1:
..1.1_0.TAG.25.0.1:
..1.1_0.TAG.24.0.1:
..1.1_0.TAG.23.0.1:
..1.1_0.TAG.22.0.1:
..1.1_0.TAG.19.0.1:
..1.1_0.TAG.18.0.1:
..1.1_0.TAG.13.0.1:
..1.1_0.TAG.10.0.1:
..1.1_0.TAG.7.0.1:
..1.1_0.TAG.6.0.1:
..1.1_0.TAG.4.0.1:
..1.1_0.TAG.3.0.1:
..1.1_0.TAG.2.0.1:
..1.1_0.SWBASE.0.1:
..1.1_0.TAG.0.0.1:
..B1.69:
        cmpl      $4, %esi
        ja        ..B1.81
..B1.70:
        lea       .2.9_2.switchtab.2(%rip), %rdx
        movzbl    (%rdx,%rsi), %edx
        lea       ..1.1_1.TAG.4.0.1(%rip), %rax
        addq      %rdx, %rax
        jmp       *%rax
..1.1_1.TAG.4.0.1:
..B1.72:
        movl      $8388608, -16(%rsp)
        movss     -16(%rsp), %xmm0
        cvtss2sd  %xmm0, %xmm0
        mulsd     .L_2il0floatpacket.2(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, -16(%rsp)
        jmp       ..B1.81
..1.1_1.TAG.3.0.1:
..B1.74:
        movl      $2130706432, -20(%rsp)
        movss     -20(%rsp), %xmm1
        movss     -20(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, -20(%rsp)
        jmp       ..B1.81
..1.1_1.TAG.2.0.1:
..B1.76:
        movl      $0, -8(%rsp)
..B1.77:
        movss     -8(%rsp), %xmm0
        cvtss2sd  %xmm0, %xmm0
        movsd     .L_2il0floatpacket.3(%rip), %xmm1
        divsd     %xmm0, %xmm1
        cvtsd2ss  %xmm1, %xmm1
        movss     %xmm1, -8(%rsp)
        jmp       ..B1.81
..1.1_1.TAG.1.0.1:
..B1.79:
        movl      $2139095040, -24(%rsp)
        movss     -24(%rsp), %xmm1
        movss     -24(%rsp), %xmm0
        subss     %xmm0, %xmm1
        movss     %xmm1, -24(%rsp)
..1.1_1.TAG.0.0.1:
..B1.81:
        movq      %rcx, %rax
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_exception,@function
	.size	__dpml_exception,.-__dpml_exception
	.section .rodata, "a"
	.align 8
	.align 1
.2.9_2.switchtab.1:
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.align 1
.2.9_2.switchtab.2:
	.byte	..1.1_1.TAG.0.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.1.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.2.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.3.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.4.0.1 - ..1.1_1.TAG.4.0.1
	.data
# -- End  __dpml_exception
	.text
# -- Begin  __dpml_intel_atan_fixup
	.text
       .align    16,0x90
	.globl __dpml_intel_atan_fixup
__dpml_intel_atan_fixup:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B2.1:
	.cfi_startproc
..___tag_value___dpml_intel_atan_fixup.4:
..L5:

        movq      %rcx, %r9
        movq      %rdx, %rcx
        movq      %rdi, %rdx
        btrq      $63, %rdx
        movl      $4, %r10d
        movq      %rdx, %rax
        movq      %rdx, %r11
        sarq      $4, %r11
        andq      $15, %rax
        testq     %r9, %r9
        cmovne    %r11, %rdx
        cmove     %r10, %rax
        xorb      %dil, %dil
        lea       (%rdx,%rdx,4), %r9
        lea       -22(%rax,%r9,2), %rax
        cmpq      $77, %rax
        ja        ..B2.56
..B2.2:
        movl      %eax, %eax
        lea       .2.10_2.switchtab.4(%rip), %rdx
        movzwl    (%rdx,%rax,2), %edx
        lea       ..1.2_0.TAG.77.0.2(%rip), %rax
        addq      %rdx, %rax
        jmp       *%rax
..1.2_0.TAG.77.0.2:
..B2.4:
        movl      $5, %eax
        movl      $11, %r8d
        movb      $3, %dil
        testq     %rcx, %rcx
        cmovne    %rax, %r8
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.76.0.2:
..B2.6:
        movq      __x_constants__@GOTPCREL(%rip), %r8
        movq      8(%r8), %rax
        jmp       ..B2.54
..1.2_0.TAG.75.0.2:
..1.2_0.TAG.73.0.2:
..1.2_0.TAG.71.0.2:
..1.2_0.TAG.65.0.2:
..1.2_0.TAG.63.0.2:
..1.2_0.TAG.61.0.2:
..1.2_0.TAG.57.0.2:
..1.2_0.TAG.56.0.2:
..1.2_0.TAG.51.0.2:
..1.2_0.TAG.47.0.2:
..1.2_0.TAG.46.0.2:
..1.2_0.TAG.41.0.2:
..1.2_0.TAG.37.0.2:
..1.2_0.TAG.36.0.2:
..1.2_0.TAG.31.0.2:
..1.2_0.TAG.27.0.2:
..1.2_0.TAG.26.0.2:
..1.2_0.TAG.21.0.2:
..1.2_0.TAG.17.0.2:
..1.2_0.TAG.16.0.2:
..1.2_0.TAG.15.0.2:
..1.2_0.TAG.14.0.2:
..1.2_0.TAG.13.0.2:
..1.2_0.TAG.12.0.2:
..1.2_0.TAG.7.0.2:
..1.2_0.TAG.6.0.2:
..1.2_0.TAG.5.0.2:
..1.2_0.TAG.4.0.2:
..1.2_0.TAG.3.0.2:
..1.2_0.TAG.2.0.2:
..B2.7:
        testq     %rcx, %rcx
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.67.0.2:
..B2.11:
        movl      $5, %eax
        movl      $11, %r8d
        movb      $2, %dil
        testq     %rcx, %rcx
        cmovne    %rax, %r8
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.66.0.2:
..B2.13:
        movq      __x_constants__@GOTPCREL(%rip), %r8
        movq      8(%r8), %rax
        jmp       ..B2.55
..1.2_0.TAG.11.0.2:
..B2.36:
        movl      $6, %eax
        movl      $10, %r8d
        movb      $3, %dil
        testq     %rcx, %rcx
        cmovne    %rax, %r8
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.10.0.2:
..B2.38:
        movl      $8, %r8d
        movb      $3, %dil
        testq     %rcx, %rcx
        cmovne    %r10, %r8
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.1.0.2:
..B2.47:
        movl      $6, %eax
        movl      $10, %r8d
        movb      $2, %dil
        testq     %rcx, %rcx
        cmovne    %rax, %r8
        jne       ..B2.51
        jmp       ..B2.52
..1.2_0.TAG.0.0.2:
..B2.49:
        movl      $8, %r8d
        movb      $2, %dil
        testq     %rcx, %rcx
        cmovne    %r10, %r8
..B2.50:
        je        ..B2.52
..B2.51:
        movl      $1182793727, -8(%rsp)
        movss     -8(%rsp), %xmm1
        movss     -8(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, -8(%rsp)
..B2.52:
        testb     %dil, %dil
        je        ..B2.56
..B2.53:
        shlq      $4, %r8
        movq      __x_constants__@GOTPCREL(%rip), %rax
        addq      %rax, %r8
        cmpb      $3, %dil
        movq      8(%r8), %rax
        jne       ..B2.55
..B2.54:
        btcq      $63, %rax
..B2.55:
        movq      %rax, 8(%rsi)
        movq      (%r8), %rax
        movq      %rax, (%rsi)
        ret       
..1.2_0.SWBASE.0.2:
..B2.56:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_intel_atan_fixup,@function
	.size	__dpml_intel_atan_fixup,.-__dpml_intel_atan_fixup
	.section .rodata, "a"
	.space 1, 0x00 	# pad
	.align 2
.2.10_2.switchtab.4:
	.word	..1.2_0.TAG.0.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.1.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.10.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.11.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.66.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.67.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.76.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.77.0.2 - ..1.2_0.TAG.77.0.2
	.data
# -- End  __dpml_intel_atan_fixup
	.text
# -- Begin  __dpml_intel_pow_fixup
	.text
       .align    16,0x90
	.globl __dpml_intel_pow_fixup
__dpml_intel_pow_fixup:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 96 + %rsp
..B3.1:
	.cfi_startproc
..___tag_value___dpml_intel_pow_fixup.7:
..L8:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $48, %rsp
	.cfi_def_cfa_offset 96
        movq      %rdi, %r12
        movq      %rcx, %r11
        movq      %r12, %rcx
        sarq      $4, %rcx
        movq      %r12, %r10
        andq      $15, %rcx
        andq      $15, %r10
        movq      %r9, %rbx
        movq      %rsi, %rbp
        testq     %r12, %r12
        lea       (%rcx,%rcx,4), %rcx
        lea       (%r10,%rcx,2), %r9
        jl        ..B3.20
..B3.2:
        movq      $0x8000000000000000, %r14
        movl      $1, %r15d
        xorl      %ecx, %ecx
        cmpq      8(%r11), %r14
        cmove     %r15d, %ecx
        xorl      %eax, %eax
        cmpq      $0, 16(%r11)
        cmove     %r15d, %eax
        andl      %eax, %ecx
        cmpq      $94, %r9
        je        ..B3.19
..B3.3:
        lea       -42(%r9), %rax
        cmpq      $1, %rax
        jbe       ..B3.13
..B3.4:
        lea       -52(%r9), %rax
        cmpq      $1, %rax
        jbe       ..B3.13
..B3.5:
        lea       -34(%r9), %rcx
        cmpq      $1, %rcx
        ja        ..B3.7
..B3.6:
        xorl      %ebx, %ebx
        cmpq      $5, %r10
        movq      $0x7fff000000000000, %rdi
        movq      __x_constants__@GOTPCREL(%rip), %rdx
        cmove     %rbx, %rdi
        movq      96(%rsp), %r10
        cmpq      $3, %r10
        cmove     %r14, %rbx
        orq       %r14, %r12
        movq      (%rdx), %rcx
        xorq      %rbx, %rdi
        movq      %rcx, (%rbp)
        movq      %rdi, 8(%rbp)
        jmp       ..B3.16
..B3.7:
        cmpq      $44, %r9
        jne       ..B3.16
..B3.8:
        movq      __x_constants__@GOTPCREL(%rip), %r9
        movq      8(%r8), %rcx
        cmpq      24(%r9), %rcx
        jne       ..B3.16
..B3.9:
        movq      (%r8), %rcx
        cmpq      16(%r9), %rcx
        jne       ..B3.16
..B3.10:
        movq      %r11, %rdi
        movl      $7, %esi
        movq      %rbx, %rdx
..___tag_value___dpml_intel_pow_fixup.20:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___dpml_intel_pow_fixup.21:
..B3.11:
        xorl      %r9d, %r9d
        testq     %rax, %rax
        movq      %rbx, %rdi
        cmove     %r15d, %r9d
        xorl      %edx, %edx
        movq      %rbp, %rsi
        xorl      %ecx, %ecx
        lea       (%rsp), %r8
        movq      %r9, 8(%r8)
..___tag_value___dpml_intel_pow_fixup.22:
        call      __dpml_pack__@PLT
..___tag_value___dpml_intel_pow_fixup.23:
        jmp       ..B3.15
..B3.13:
        movq      __x_constants__@GOTPCREL(%rip), %rdx
        movq      32(%rdx), %rbx
        movq      40(%rdx), %r8
        movl      4(%r11), %edx
        cmpl      $1, %edx
        movq      %rbx, (%rbp)
        je        ..B3.17
..B3.14:
        testl     %edx, %edx
        movl      $0, %edx
        movq      $0x7fff000000000000, %rcx
        cmovle    %r15d, %edx
        xorl      %ebx, %ebx
        cmpq      $2, %r10
        movl      $0, %r10d
        cmove     %r15d, %r10d
        cmpl      %edx, %r10d
        cmovne    %rcx, %rbx
        movq      %rbx, 8(%rbp)
..B3.15:
        orq       %r14, %r12
..B3.16:
        movq      %r12, %rax
        addq      $48, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B3.17:
        testl     %ecx, %ecx
        je        ..B3.14
..B3.18:
        movq      %r8, 8(%rbp)
        jmp       ..B3.15
..B3.19:
        movq      96(%rsp), %rcx
        xorl      %ebx, %ebx
        cmpq      $3, %rcx
        cmove     %r14, %rbx
        orq       %r14, %r12
        movq      %rbx, 8(%rbp)
        movq      (%rdx), %rdx
        movq      %rdx, (%rbp)
        jmp       ..B3.16
..B3.20:
        cmpq      $95, %r9
        je        ..B3.32
..B3.21:
        lea       -40(%r9), %rcx
        cmpq      $1, %rcx
        jbe       ..B3.29
..B3.22:
        lea       -80(%r9), %rcx
        cmpq      $1, %rcx
        jbe       ..B3.28
..B3.23:
        lea       -90(%r9), %rcx
        cmpq      $1, %rcx
        jbe       ..B3.28
..B3.24:
        addq      $-46, %r9
        cmpq      $1, %r9
        ja        ..B3.16
..B3.25:
        movq      __x_constants__@GOTPCREL(%rip), %rbx
        movq      8(%rdx), %rcx
        cmpq      40(%rbx), %rcx
        jne       ..B3.27
..B3.26:
        movq      (%rdx), %rdx
        cmpq      32(%rbx), %rdx
        je        ..B3.16
..B3.27:
        movl      $1182793727, 40(%rsp)
        movss     40(%rsp), %xmm1
        movss     40(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, 40(%rsp)
        jmp       ..B3.16
..B3.28:
        movq      8(%r8), %rdx
        btsq      $47, %rdx
        movq      %rdx, 8(%rbp)
        movq      (%r8), %rcx
        movq      %rcx, (%rbp)
        jmp       ..B3.16
..B3.29:
        movq      __x_constants__@GOTPCREL(%rip), %rcx
        movq      40(%rcx), %rbx
        cmpq      8(%rdx), %rbx
        jne       ..B3.28
..B3.30:
        movq      32(%rcx), %rcx
        cmpq      (%rdx), %rcx
        jne       ..B3.28
..B3.31:
        movq      %rbx, 8(%rbp)
        movq      %rcx, (%rbp)
        jmp       ..B3.16
..B3.32:
        movq      96(%rsp), %rdx
        testq     %rdx, %rdx
        je        ..B3.16
..B3.33:
        movq      96(%rsp), %rdx
        testq     $2, %rdx
        je        ..B3.16
..B3.34:
        movq      8(%rbp), %rdx
        btcq      $63, %rdx
        movq      %rdx, 8(%rbp)
        jmp       ..B3.16
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_intel_pow_fixup,@function
	.size	__dpml_intel_pow_fixup,.-__dpml_intel_pow_fixup
	.data
# -- End  __dpml_intel_pow_fixup
	.text
# -- Begin  __dpml_d_intel_order_fixup
	.text
       .align    16,0x90
	.globl __dpml_d_intel_order_fixup
__dpml_d_intel_order_fixup:
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value___dpml_d_intel_order_fixup.42:
..L43:

        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_d_intel_order_fixup,@function
	.size	__dpml_d_intel_order_fixup,.-__dpml_d_intel_order_fixup
	.data
# -- End  __dpml_d_intel_order_fixup
	.section .rodata, "a"
	.space 2, 0x00 	# pad
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0x3e600000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 4
__dpml_response_table:
	.long	0
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	1
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	2
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	3
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	3
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	4
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	4
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	6
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	7
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	8
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	8
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	8
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	9
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	9
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	9
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	10
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	10
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	10
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	11
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	11
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	39
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	39
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	12
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	13
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	14
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	33
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	33
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	33
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	33
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	33
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	33
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	34
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	34
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	34
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	34
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	34
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	34
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	34
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	34
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	16
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	16
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	16
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	16
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	17
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	17
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	17
	.byte	0
	.byte	12
	.byte	0
	.byte	12
	.long	38
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	38
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	38
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	47
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	47
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	47
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	47
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	47
	.byte	0
	.byte	8
	.byte	0
	.byte	8
	.long	47
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	47
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	47
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	37
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	18
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	18
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	18
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	19
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	19
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	19
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	20
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	20
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	20
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	45
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	45
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	21
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	21
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	21
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	40
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	40
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	40
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	40
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	22
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	22
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	22
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	0
	.byte	11
	.byte	0
	.byte	11
	.long	22
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	8
	.byte	0
	.byte	8
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	22
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	41
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	41
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	41
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	41
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	41
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	41
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	41
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	48
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	48
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	23
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	23
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	23
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	24
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	31
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	32
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	32
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	25
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	25
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	26
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	26
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	26
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	27
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	63
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	63
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	28
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	29
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	29
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	29
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	29
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	29
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	30
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	30
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	35
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	36
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	36
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	49
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	50
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	51
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	50
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	50
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	51
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	51
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	42
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	43
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	44
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	42
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	42
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	43
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	43
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	43
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	44
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	44
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	44
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	44
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	46
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	46
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	46
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	46
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	46
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	53
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	78
	.byte	0
	.byte	1
	.byte	0
	.byte	0
	.long	64
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	64
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	64
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	64
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	66
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	66
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	66
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	67
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	67
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	67
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	65
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	65
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	65
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	65
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	65
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	65
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	65
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	68
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	69
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	70
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	71
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	72
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	72
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	72
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	73
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	73
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	73
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	73
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	74
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	74
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	77
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	77
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	77
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	77
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	77
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	77
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	79
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	79
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.type	__dpml_response_table,@object
	.size	__dpml_response_table,1520
	.align 4
	.globl __dpml_globals_table
__dpml_globals_table:
	.long	4290772992
	.long	0
	.long	0
	.long	4294443008
	.long	0
	.long	0
	.long	0
	.long	4294934528
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	1
	.long	0
	.long	1
	.long	0
	.long	0
	.long	0
	.long	2147483649
	.long	0
	.long	1
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	2147483648
	.long	2139095039
	.long	0
	.long	4294967295
	.long	2146435071
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	2147418111
	.long	4286578687
	.long	0
	.long	4294967295
	.long	4293918719
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294901759
	.long	2139095040
	.long	0
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	2147418112
	.long	4286578688
	.long	0
	.long	0
	.long	4293918720
	.long	0
	.long	0
	.long	0
	.long	4294901760
	.long	872415232
	.long	0
	.long	0
	.long	1018167296
	.long	0
	.long	0
	.long	0
	.long	1066336256
	.long	3019898880
	.long	0
	.long	0
	.long	3165650944
	.long	0
	.long	0
	.long	0
	.long	3213819904
	.long	1065353216
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	3212836864
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	3221159936
	.type	__dpml_globals_table,@object
	.size	__dpml_globals_table,416
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
