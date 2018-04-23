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
	.file "fma_gen.c"
	.text
..TXTST0:
# -- Begin  fma
	.text

	.globl fma
fma:
# parameter 1: %xmm0
# parameter 2: %xmm1
# parameter 3: %xmm2
..B1.1:
	.cfi_startproc
..___tag_value_fma.1:
..L2:

        pushq     %rbp
	.cfi_def_cfa_offset 16
        movq      %rsp, %rbp
	.cfi_def_cfa 6, 16
	.cfi_offset 6, -16
        subq      $272, %rsp
        movsd     %xmm0, -80(%rbp)
        movsd     %xmm1, -72(%rbp)
        movsd     %xmm2, -64(%rbp)
..B1.2:
        stmxcsr   -272(%rbp)
..B1.3:
        movl      -272(%rbp), %eax
        andl      $24576, %eax
        movl      %eax, -268(%rbp)
        lea       -80(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -264(%rbp)
        lea       -80(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -260(%rbp)
        lea       -72(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -256(%rbp)
        lea       -72(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -252(%rbp)
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -248(%rbp)
        lea       -64(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -244(%rbp)
        movl      -260(%rbp), %eax
        orl       -264(%rbp), %eax
        je        ..B1.11
..B1.4:
        movl      -264(%rbp), %eax
        cmpl      $2146435072, %eax
        jae       ..B1.11
..B1.5:
        movl      $-1072693248, %eax
        addl      -264(%rbp), %eax
        orl       -260(%rbp), %eax
        je        ..B1.11
..B1.6:
        movl      -252(%rbp), %eax
        orl       -256(%rbp), %eax
        je        ..B1.11
..B1.7:
        movl      -256(%rbp), %eax
        cmpl      $2146435072, %eax
        jae       ..B1.11
..B1.8:
        movl      $-1072693248, %eax
        addl      -256(%rbp), %eax
        orl       -252(%rbp), %eax
        je        ..B1.11
..B1.9:
        movl      -244(%rbp), %eax
        orl       -248(%rbp), %eax
        je        ..B1.11
..B1.10:
        movl      -248(%rbp), %eax
        cmpl      $2146435072, %eax
        jb        ..B1.28
..B1.11:
        movl      -264(%rbp), %eax
        cmpl      $2146435072, %eax
        ja        ..B1.14
..B1.12:
        movl      -264(%rbp), %eax
        cmpl      $2146435072, %eax
        jne       ..B1.15
..B1.13:
        movl      -260(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.15
..B1.14:
        movsd     -80(%rbp), %xmm0
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.15:
        movl      -256(%rbp), %eax
        cmpl      $2146435072, %eax
        ja        ..B1.18
..B1.16:
        movl      -256(%rbp), %eax
        cmpl      $2146435072, %eax
        jne       ..B1.19
..B1.17:
        movl      -252(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.19
..B1.18:
        movsd     -72(%rbp), %xmm0
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.19:
        movl      -248(%rbp), %eax
        cmpl      $2146435072, %eax
        ja        ..B1.22
..B1.20:
        movl      -248(%rbp), %eax
        cmpl      $2146435072, %eax
        jne       ..B1.23
..B1.21:
        movl      -244(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.23
..B1.22:
        movsd     -64(%rbp), %xmm0
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.23:
        movl      -264(%rbp), %eax
        cmpl      $2146435072, %eax
        jae       ..B1.27
..B1.24:
        movl      -256(%rbp), %eax
        cmpl      $2146435072, %eax
        jae       ..B1.27
..B1.25:
        movl      -248(%rbp), %eax
        cmpl      $2146435072, %eax
        jb        ..B1.27
..B1.26:
        movsd     -64(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.27:
        movsd     -80(%rbp), %xmm0
        movsd     -72(%rbp), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     -64(%rbp), %xmm1
        addsd     %xmm1, %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.28:
        movl      -264(%rbp), %eax
        cmpl      $1048576, %eax
        jae       ..B1.30
..B1.29:
        lea       -80(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        shrl      $31, %eax
        movl      %eax, -240(%rbp)
        lea       -80(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        orl       $1072693248, %eax
        lea       -80(%rbp), %rdx
        addq      $4, %rdx
        movl      %eax, (%rdx)
        movl      -240(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     -80(%rbp), %xmm0
        movsd     (%rdx), %xmm1
        subsd     %xmm1, %xmm0
        movsd     %xmm0, -80(%rbp)
        lea       -80(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -264(%rbp)
        lea       -80(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -260(%rbp)
        movl      -264(%rbp), %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      %eax, -236(%rbp)
        jmp       ..B1.31
..B1.30:
        movl      -264(%rbp), %eax
        shrl      $20, %eax
        movl      %eax, -236(%rbp)
..B1.31:
        movl      -256(%rbp), %eax
        cmpl      $1048576, %eax
        jae       ..B1.33
..B1.32:
        lea       -72(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        shrl      $31, %eax
        movl      %eax, -232(%rbp)
        lea       -72(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        orl       $1072693248, %eax
        lea       -72(%rbp), %rdx
        addq      $4, %rdx
        movl      %eax, (%rdx)
        movl      -232(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     -72(%rbp), %xmm0
        movsd     (%rdx), %xmm1
        subsd     %xmm1, %xmm0
        movsd     %xmm0, -72(%rbp)
        lea       -72(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -256(%rbp)
        lea       -72(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -252(%rbp)
        movl      -256(%rbp), %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      %eax, -228(%rbp)
        jmp       ..B1.34
..B1.33:
        movl      -256(%rbp), %eax
        shrl      $20, %eax
        movl      %eax, -228(%rbp)
..B1.34:
        movl      -248(%rbp), %eax
        cmpl      $1048576, %eax
        jae       ..B1.36
..B1.35:
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        shrl      $31, %eax
        movl      %eax, -224(%rbp)
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        orl       $1072693248, %eax
        lea       -64(%rbp), %rdx
        addq      $4, %rdx
        movl      %eax, (%rdx)
        movl      -224(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     -64(%rbp), %xmm0
        movsd     (%rdx), %xmm1
        subsd     %xmm1, %xmm0
        movsd     %xmm0, -64(%rbp)
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $2147483647, %eax
        movl      %eax, -248(%rbp)
        lea       -64(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -244(%rbp)
        movl      -248(%rbp), %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      %eax, -220(%rbp)
        jmp       ..B1.37
..B1.36:
        movl      -248(%rbp), %eax
        shrl      $20, %eax
        movl      %eax, -220(%rbp)
..B1.37:
        movl      -264(%rbp), %eax
        andl      $1048575, %eax
        orl       $1048576, %eax
        movl      %eax, -216(%rbp)
        movl      -256(%rbp), %eax
        andl      $1048575, %eax
        orl       $1048576, %eax
        movl      %eax, -212(%rbp)
        movl      -248(%rbp), %eax
        andl      $1048575, %eax
        orl       $1048576, %eax
        movl      %eax, -208(%rbp)
        lea       -80(%rbp), %rax
        addq      $4, %rax
        lea       -72(%rbp), %rdx
        addq      $4, %rdx
        movl      (%rdx), %edx
        xorl      (%rax), %edx
        andl      $-2147483648, %edx
        movl      %edx, -204(%rbp)
        movl      -228(%rbp), %eax
        addl      -236(%rbp), %eax
        addl      $-1023, %eax
        movl      %eax, -200(%rbp)
        movl      -252(%rbp), %eax
        movl      -260(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -56(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -196(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -192(%rbp)
        movl      -252(%rbp), %eax
        movl      -216(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -48(%rbp)
        movq      -48(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -188(%rbp)
        movl      -48(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      -212(%rbp), %eax
        movl      -260(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -40(%rbp)
        movq      -40(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -180(%rbp)
        movl      -40(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      -212(%rbp), %eax
        movl      -216(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -32(%rbp)
        movq      -32(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -172(%rbp)
        movl      -32(%rbp), %eax
        movl      %eax, -168(%rbp)
        movl      -192(%rbp), %eax
        movl      %eax, -164(%rbp)
        movl      -196(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -184(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -176(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -160(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -156(%rbp)
        movl      -168(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -188(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -180(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -156(%rbp), %eax
        movslq    %eax, %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -152(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -156(%rbp)
        movl      -156(%rbp), %eax
        addl      -172(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -148(%rbp), %eax
        testl     $512, %eax
        je        ..B1.39
..B1.38:
        movl      -148(%rbp), %eax
        shll      $11, %eax
        movl      -152(%rbp), %edx
        shrl      $21, %edx
        orl       %edx, %eax
        movl      %eax, -148(%rbp)
        movl      -152(%rbp), %eax
        shll      $11, %eax
        movl      -160(%rbp), %edx
        shrl      $21, %edx
        orl       %edx, %eax
        movl      %eax, -152(%rbp)
        movl      -160(%rbp), %eax
        shll      $11, %eax
        movl      -164(%rbp), %edx
        shrl      $21, %edx
        orl       %edx, %eax
        movl      %eax, -160(%rbp)
        shll      $11, -164(%rbp)
        movl      $1, %eax
        addl      -200(%rbp), %eax
        movl      %eax, -200(%rbp)
        jmp       ..B1.40
..B1.39:
        movl      -148(%rbp), %eax
        shll      $12, %eax
        movl      -152(%rbp), %edx
        shrl      $20, %edx
        orl       %edx, %eax
        movl      %eax, -148(%rbp)
        movl      -152(%rbp), %eax
        shll      $12, %eax
        movl      -160(%rbp), %edx
        shrl      $20, %edx
        orl       %edx, %eax
        movl      %eax, -152(%rbp)
        movl      -160(%rbp), %eax
        shll      $12, %eax
        movl      -164(%rbp), %edx
        shrl      $20, %edx
        orl       %edx, %eax
        movl      %eax, -160(%rbp)
        shll      $12, -164(%rbp)
..B1.40:
        movl      -200(%rbp), %eax
        movl      -220(%rbp), %edx
        cmpl      %edx, %eax
        jg        ..B1.45
..B1.41:
        movl      -200(%rbp), %eax
        movl      -220(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.46
..B1.42:
        movl      -148(%rbp), %eax
        movl      -208(%rbp), %edx
        cmpl      %edx, %eax
        ja        ..B1.45
..B1.43:
        movl      -148(%rbp), %eax
        movl      -208(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.46
..B1.44:
        movl      -152(%rbp), %eax
        movl      -244(%rbp), %edx
        cmpl      %edx, %eax
        jb        ..B1.46
..B1.45:
        movl      -220(%rbp), %eax
        negl      %eax
        addl      -200(%rbp), %eax
        movl      %eax, -144(%rbp)
        movl      -208(%rbp), %eax
        movl      %eax, -140(%rbp)
        movl      -244(%rbp), %eax
        movl      %eax, -136(%rbp)
        movl      $0, %eax
        movl      %eax, -132(%rbp)
        movl      %eax, -128(%rbp)
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $-2147483648, %eax
        movl      %eax, -224(%rbp)
        jmp       ..B1.47
..B1.46:
        movl      -200(%rbp), %eax
        negl      %eax
        addl      -220(%rbp), %eax
        movl      %eax, -144(%rbp)
        movl      -220(%rbp), %eax
        movl      %eax, -200(%rbp)
        movl      -148(%rbp), %eax
        movl      %eax, -140(%rbp)
        movl      -152(%rbp), %eax
        movl      %eax, -136(%rbp)
        movl      -160(%rbp), %eax
        movl      %eax, -128(%rbp)
        movl      -164(%rbp), %eax
        movl      %eax, -132(%rbp)
        movl      -208(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -244(%rbp), %eax
        movl      %eax, -152(%rbp)
        movl      $0, %eax
        movl      %eax, -164(%rbp)
        movl      %eax, -160(%rbp)
        movl      -204(%rbp), %eax
        movl      %eax, -224(%rbp)
        lea       -64(%rbp), %rax
        addq      $4, %rax
        movl      (%rax), %eax
        andl      $-2147483648, %eax
        movl      %eax, -204(%rbp)
..B1.47:
        movl      $0, -124(%rbp)
        movl      -144(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.57
..B1.48:
        movl      -144(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.50
..B1.49:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -132(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      %eax, -124(%rbp)
        movl      -128(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -132(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -132(%rbp)
        movl      -136(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -128(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -128(%rbp)
        movl      -140(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -136(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -136(%rbp)
        movl      -116(%rbp), %eax
        movl      %eax, %ecx
        shrl      %cl, -140(%rbp)
        jmp       ..B1.57
..B1.50:
        movl      -144(%rbp), %eax
        cmpl      $64, %eax
        jge       ..B1.52
..B1.51:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -120(%rbp)
        movl      $-32, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -128(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -132(%rbp), %edx
        movl      $1, %ecx
        testl     %edx, %edx
        cmovne    %ecx, %edx
        orl       %edx, %eax
        movl      %eax, -124(%rbp)
        movl      -136(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -128(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -132(%rbp)
        movl      -140(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -136(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -128(%rbp)
        movl      -140(%rbp), %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -136(%rbp)
        movl      $0, -140(%rbp)
        jmp       ..B1.57
..B1.52:
        movl      -144(%rbp), %eax
        cmpl      $96, %eax
        jge       ..B1.54
..B1.53:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $96, %eax
        movl      %eax, -120(%rbp)
        movl      $-64, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -136(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -132(%rbp), %edx
        orl       -128(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -140(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -136(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -132(%rbp)
        movl      -140(%rbp), %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -128(%rbp)
        movl      $0, %eax
        movl      %eax, -136(%rbp)
        movl      %eax, -140(%rbp)
        jmp       ..B1.57
..B1.54:
        movl      -144(%rbp), %eax
        cmpl      $128, %eax
        jge       ..B1.56
..B1.55:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $128, %eax
        movl      %eax, -120(%rbp)
        movl      $-96, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -140(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -128(%rbp), %edx
        orl       -136(%rbp), %edx
        orl       -132(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -140(%rbp), %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -132(%rbp)
        movl      $0, -128(%rbp)
        movl      $0, %eax
        movl      %eax, -136(%rbp)
        movl      %eax, -140(%rbp)
        jmp       ..B1.57
..B1.56:
        movl      -136(%rbp), %eax
        orl       -140(%rbp), %eax
        orl       -128(%rbp), %eax
        orl       -132(%rbp), %eax
        movl      $1, %eax
        movl      $0, %edx
        cmovne    %eax, %edx
        movl      %edx, -124(%rbp)
        movl      $0, -132(%rbp)
        movl      $0, -128(%rbp)
        movl      $0, %eax
        movl      %eax, -136(%rbp)
        movl      %eax, -140(%rbp)
..B1.57:
        movl      -204(%rbp), %eax
        movl      -224(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.64
..B1.58:
        movl      -164(%rbp), %eax
        movl      %eax, -108(%rbp)
        movl      -132(%rbp), %eax
        addl      -164(%rbp), %eax
        movl      %eax, -164(%rbp)
        movl      -164(%rbp), %eax
        movl      -108(%rbp), %edx
        cmpl      %edx, %eax
        jb        ..B1.60
..B1.59:
        movl      $0, -156(%rbp)
        jmp       ..B1.61
..B1.60:
        movl      $1, -156(%rbp)
..B1.61:
        movl      -160(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -128(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -156(%rbp), %eax
        movslq    %eax, %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -160(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -156(%rbp)
        movl      -152(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -136(%rbp), %eax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -156(%rbp), %eax
        movslq    %eax, %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -152(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -156(%rbp)
        movl      -140(%rbp), %eax
        addl      -148(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -156(%rbp), %eax
        addl      -148(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -148(%rbp), %eax
        testl     $2097152, %eax
        je        ..B1.63
..B1.62:
        movl      -152(%rbp), %eax
        shll      $31, %eax
        movl      -164(%rbp), %edx
        orl       -160(%rbp), %edx
        orl       -124(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -148(%rbp), %eax
        shll      $31, %eax
        movl      -152(%rbp), %edx
        shrl      $1, %edx
        orl       %edx, %eax
        movl      %eax, -100(%rbp)
        movl      -148(%rbp), %eax
        shrl      $1, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        movl      $1, %eax
        addl      -200(%rbp), %eax
        movl      %eax, -200(%rbp)
        jmp       ..B1.90
..B1.63:
        movl      -160(%rbp), %eax
        movl      -124(%rbp), %edx
        orl       -164(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -152(%rbp), %eax
        movl      %eax, -100(%rbp)
        movl      -148(%rbp), %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        jmp       ..B1.90
..B1.64:
        movl      -124(%rbp), %eax
        negl      %eax
        movl      %eax, -124(%rbp)
        movl      -124(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.66
..B1.65:
        movl      $0, -104(%rbp)
        jmp       ..B1.67
..B1.66:
        movl      $1, -104(%rbp)
..B1.67:
        movl      -164(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -132(%rbp), %eax
        negq      %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -104(%rbp), %eax
        movslq    %eax, %rax
        negq      %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -164(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -104(%rbp)
        movl      -160(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -128(%rbp), %eax
        negq      %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -104(%rbp), %eax
        movslq    %eax, %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -160(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -104(%rbp)
        movl      -152(%rbp), %eax
        movq      %rax, -24(%rbp)
        movl      -136(%rbp), %eax
        negq      %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -104(%rbp), %eax
        movslq    %eax, %rax
        addq      -24(%rbp), %rax
        movq      %rax, -24(%rbp)
        movl      -24(%rbp), %eax
        movl      %eax, -152(%rbp)
        movq      -24(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -104(%rbp)
        movl      -140(%rbp), %eax
        negl      %eax
        addl      -148(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -104(%rbp), %eax
        addl      -148(%rbp), %eax
        movl      %eax, -148(%rbp)
        movl      -148(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.69
..B1.68:
        movl      -148(%rbp), %eax
        shll      $11, %eax
        movl      %eax, -108(%rbp)
        movl      $0, -144(%rbp)
        jmp       ..B1.78
..B1.69:
        movl      -152(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.71
..B1.70:
        movl      -152(%rbp), %eax
        movl      %eax, -108(%rbp)
        movl      $21, -144(%rbp)
        jmp       ..B1.78
..B1.71:
        movl      -160(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.73
..B1.72:
        movl      -160(%rbp), %eax
        movl      %eax, -108(%rbp)
        movl      $53, -144(%rbp)
        jmp       ..B1.78
..B1.73:
        movl      -164(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.75
..B1.74:
        movl      -164(%rbp), %eax
        movl      %eax, -108(%rbp)
        movl      $85, -144(%rbp)
        jmp       ..B1.78
..B1.75:
        movl      -124(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.77
..B1.76:
        movl      -124(%rbp), %eax
        movl      %eax, -108(%rbp)
        movl      $117, -144(%rbp)
        jmp       ..B1.78
..B1.77:
        movl      -268(%rbp), %eax
        movl      $1, %edx
        movl      $0, %ecx
        cmpl      $8192, %eax
        cmove     %edx, %ecx
        movslq    %ecx, %rcx
        shlq      $3, %rcx
        lea       _zeros(%rip), %rax
        addq      %rcx, %rax
        movsd     (%rax), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.78:
        movl      -108(%rbp), %eax
        testl     $-2147483648, %eax
        jne       ..B1.80
..B1.79:
        shll      $1, -108(%rbp)
        movl      $1, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -144(%rbp)
        jmp       ..B1.78
..B1.80:
        movl      -144(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.82
..B1.81:
        movl      -144(%rbp), %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -116(%rbp)
        movl      $1, %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -92(%rbp)
        movl      -160(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -124(%rbp), %edx
        orl       -164(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -152(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -160(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -100(%rbp)
        movl      -148(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -152(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        jmp       ..B1.89
..B1.82:
        movl      -144(%rbp), %eax
        cmpl      $64, %eax
        jge       ..B1.84
..B1.83:
        movl      $-32, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -116(%rbp)
        movl      $1, %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -92(%rbp)
        movl      -164(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -124(%rbp), %edx
        movl      $1, %ecx
        testl     %edx, %edx
        cmovne    %ecx, %edx
        orl       %edx, %eax
        movl      %eax, -124(%rbp)
        movl      -160(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -164(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -100(%rbp)
        movl      -152(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -160(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        jmp       ..B1.89
..B1.84:
        movl      -144(%rbp), %eax
        cmpl      $96, %eax
        jge       ..B1.86
..B1.85:
        movl      $-64, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $96, %eax
        movl      %eax, -116(%rbp)
        movl      $1, %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -92(%rbp)
        movl      -120(%rbp), %eax
        movl      %eax, %ecx
        shll      %cl, -124(%rbp)
        movl      -164(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -124(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -100(%rbp)
        movl      -160(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -164(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        jmp       ..B1.89
..B1.86:
        movl      -144(%rbp), %eax
        cmpl      $128, %eax
        jge       ..B1.88
..B1.87:
        movl      $-96, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $128, %eax
        movl      %eax, -116(%rbp)
        movl      $1, %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -92(%rbp)
        movl      $0, -124(%rbp)
        movl      -124(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      %eax, -100(%rbp)
        movl      -164(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -124(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        andl      -92(%rbp), %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
        jmp       ..B1.89
..B1.88:
        movl      $-128, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -120(%rbp)
        movl      $0, %eax
        movl      %eax, -100(%rbp)
        movl      %eax, -124(%rbp)
        movl      -124(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      $1048575, %eax
        movl      %eax, -96(%rbp)
..B1.89:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      -200(%rbp), %eax
        movl      %eax, -200(%rbp)
..B1.90:
        movl      $-1, %eax
        addl      -200(%rbp), %eax
        cmpl      $2046, %eax
        jb        ..B1.98
..B1.91:
        movl      -200(%rbp), %eax
        cmpl      $2047, %eax
        jl        ..B1.93
..B1.92:
        shrl      $31, -204(%rbp)
        movl      -204(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _large_value_64(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        movsd     %xmm0, -8(%rbp)
        movsd     -8(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.93:
        orl       $1048576, -96(%rbp)
        movl      -200(%rbp), %eax
        negl      %eax
        incl      %eax
        movl      %eax, -144(%rbp)
        movl      -144(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.95
..B1.94:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -120(%rbp)
        movl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -100(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -124(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      -124(%rbp), %edx
        movl      -120(%rbp), %ecx
        shll      %cl, %edx
        movl      -112(%rbp), %ecx
        testl     %ecx, %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -96(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -100(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -100(%rbp)
        movl      -116(%rbp), %eax
        movl      %eax, %ecx
        shrl      %cl, -96(%rbp)
        jmp       ..B1.99
..B1.95:
        movl      -144(%rbp), %eax
        cmpl      $53, %eax
        jg        ..B1.97
..B1.96:
        movl      -144(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -120(%rbp)
        movl      $-32, %eax
        addl      -144(%rbp), %eax
        movl      %eax, -116(%rbp)
        movl      $-1, %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -112(%rbp)
        movl      -96(%rbp), %eax
        movl      -120(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -112(%rbp), %eax
        movl      -100(%rbp), %edx
        movl      -116(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      -100(%rbp), %edx
        movl      -120(%rbp), %ecx
        shll      %cl, %edx
        andl      -112(%rbp), %edx
        orl       -124(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -124(%rbp)
        movl      -96(%rbp), %eax
        movl      -116(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -100(%rbp)
        movl      $0, -96(%rbp)
        jmp       ..B1.99
..B1.97:
        movl      $1, -124(%rbp)
        movl      $0, %eax
        movl      %eax, -100(%rbp)
        movl      %eax, -96(%rbp)
        jmp       ..B1.99
..B1.98:
        movl      -200(%rbp), %eax
        shll      $20, %eax
        orl       -96(%rbp), %eax
        movl      %eax, -96(%rbp)
..B1.99:
        movl      -124(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.136
..B1.100:
        lea       _ones(%rip), %rax
        lea       _small_value_64(%rip), %rdx
        movsd     (%rax), %xmm0
        movsd     (%rdx), %xmm1
        addsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        movl      -268(%rbp), %eax
        movl      %eax, -88(%rbp)
        testl     %eax, %eax
        je        ..B1.104
..B1.101:
        movl      -88(%rbp), %eax
        cmpl      $24576, %eax
        je        ..B1.108
..B1.102:
        movl      -88(%rbp), %eax
        cmpl      $16384, %eax
        je        ..B1.112
..B1.103:
        movl      -88(%rbp), %eax
        cmpl      $8192, %eax
        je        ..B1.124
        jmp       ..B1.136
..B1.104:
        movl      -124(%rbp), %eax
        testl     $-2147483648, %eax
        je        ..B1.108
..B1.105:
        movl      -100(%rbp), %eax
        andl      $1, %eax
        movl      -124(%rbp), %edx
        andl      $2147483647, %edx
        orl       %edx, %eax
        je        ..B1.108
..B1.106:
        movl      $1, %eax
        addl      -100(%rbp), %eax
        movl      %eax, -100(%rbp)
        movl      $1, %eax
        movl      $0, %edx
        cmove     %eax, %edx
        addl      -96(%rbp), %edx
        movl      %edx, -96(%rbp)
        movl      -96(%rbp), %eax
        cmpl      $2146435072, %eax
        jb        ..B1.108
..B1.107:
        shrl      $31, -204(%rbp)
        movl      -204(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _large_value_64(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        movsd     %xmm0, -8(%rbp)
        movsd     -8(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.108:
        movl      -96(%rbp), %eax
        cmpl      $1048576, %eax
        jb        ..B1.111
..B1.109:
        movl      $-1048576, %eax
        addl      -96(%rbp), %eax
        orl       -100(%rbp), %eax
        jne       ..B1.136
..B1.110:
        movl      -124(%rbp), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.136
..B1.111:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        jmp       ..B1.136
..B1.112:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.114
..B1.113:
        movl      -96(%rbp), %eax
        cmpl      $1048576, %eax
        jb        ..B1.120
..B1.114:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.121
..B1.115:
        movl      -96(%rbp), %eax
        cmpl      $1048575, %eax
        jb        ..B1.120
..B1.116:
        movl      -96(%rbp), %eax
        cmpl      $1048575, %eax
        jne       ..B1.121
..B1.117:
        movl      -100(%rbp), %eax
        cmpl      $-1, %eax
        jb        ..B1.120
..B1.118:
        movl      -100(%rbp), %eax
        cmpl      $-1, %eax
        jne       ..B1.121
..B1.119:
        movl      -124(%rbp), %eax
        cmpl      $-2147483648, %eax
        jae       ..B1.121
..B1.120:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
..B1.121:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.136
..B1.122:
        movl      $1, %eax
        addl      -100(%rbp), %eax
        movl      %eax, -100(%rbp)
        movl      $1, %eax
        movl      $0, %edx
        cmove     %eax, %edx
        addl      -96(%rbp), %edx
        movl      %edx, -96(%rbp)
        movl      -96(%rbp), %eax
        cmpl      $2146435072, %eax
        jb        ..B1.136
..B1.123:
        shrl      $31, -204(%rbp)
        movl      -204(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _large_value_64(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        movsd     %xmm0, -8(%rbp)
        movsd     -8(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.124:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.126
..B1.125:
        movl      -96(%rbp), %eax
        cmpl      $1048576, %eax
        jb        ..B1.132
..B1.126:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.133
..B1.127:
        movl      -96(%rbp), %eax
        cmpl      $1048575, %eax
        jb        ..B1.132
..B1.128:
        movl      -96(%rbp), %eax
        cmpl      $1048575, %eax
        jne       ..B1.133
..B1.129:
        movl      -100(%rbp), %eax
        cmpl      $-1, %eax
        jb        ..B1.132
..B1.130:
        movl      -100(%rbp), %eax
        cmpl      $-1, %eax
        jne       ..B1.133
..B1.131:
        movl      -124(%rbp), %eax
        cmpl      $-2147483648, %eax
        jae       ..B1.133
..B1.132:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
..B1.133:
        movl      -204(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.136
..B1.134:
        movl      $1, %eax
        addl      -100(%rbp), %eax
        movl      %eax, -100(%rbp)
        movl      $1, %eax
        movl      $0, %edx
        cmove     %eax, %edx
        addl      -96(%rbp), %edx
        movl      %edx, -96(%rbp)
        movl      -96(%rbp), %eax
        cmpl      $2146435072, %eax
        jb        ..B1.136
..B1.135:
        shrl      $31, -204(%rbp)
        movl      -204(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _large_value_64(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        lea       _large_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -16(%rbp)
        movsd     %xmm0, -8(%rbp)
        movsd     -8(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.136:
        movl      -96(%rbp), %eax
        orl       -204(%rbp), %eax
        lea       -8(%rbp), %rdx
        addq      $4, %rdx
        movl      %eax, (%rdx)
        lea       -8(%rbp), %rax
        movl      -100(%rbp), %edx
        movl      %edx, (%rax)
        movsd     -8(%rbp), %xmm0
        leave     
	.cfi_restore 6
        ret       
	.cfi_endproc
	.type	fma,@function
	.size	fma,.-fma
	.data
# -- End  fma
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
