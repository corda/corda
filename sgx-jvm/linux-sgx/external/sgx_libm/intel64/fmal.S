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
	.file "fmal.c"
	.text
..TXTST0:
# -- Begin  fmal
	.text

	.globl fmal
fmal:
# parameter 1: 16 + %rbp
# parameter 2: 32 + %rbp
# parameter 3: 48 + %rbp
..B1.1:
	.cfi_startproc
..___tag_value_fmal.1:
..L2:

        pushq     %rbp
	.cfi_def_cfa_offset 16
        movq      %rsp, %rbp
	.cfi_def_cfa 6, 16
	.cfi_offset 6, -16
        subq      $336, %rsp
        lea       16(%rbp), %rax
        movq      %rax, -128(%rbp)
        lea       32(%rbp), %rax
        movq      %rax, -120(%rbp)
        lea       48(%rbp), %rax
        movq      %rax, -112(%rbp)
        lea       -32(%rbp), %rax
        movq      %rax, -104(%rbp)
        movl      $0, -328(%rbp)
..B1.2:
        fnstcw    -336(%rbp)
..B1.3:
        fnstcw    -334(%rbp)
..B1.4:
        movzwl    -334(%rbp), %eax
        movzwl    %ax, %eax
        andl      $3072, %eax
        movl      %eax, -324(%rbp)
        movq      -128(%rbp), %rax
        movzbl    9(%rax), %eax
        movzbl    %al, %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, -320(%rbp)
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        movl      %eax, -316(%rbp)
        movq      -120(%rbp), %rax
        movzbl    9(%rax), %eax
        movzbl    %al, %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, -312(%rbp)
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        movl      %eax, -308(%rbp)
        movq      -112(%rbp), %rax
        movzbl    9(%rax), %eax
        movzbl    %al, %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, -304(%rbp)
        movq      -112(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        movl      %eax, -300(%rbp)
        movl      -316(%rbp), %eax
        cmpl      $32767, %eax
        je        ..B1.22
..B1.5:
        movl      -316(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.8
..B1.6:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.8
..B1.7:
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.22
..B1.8:
        movl      -316(%rbp), %eax
        cmpl      $16383, %eax
        jne       ..B1.11
..B1.9:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.11
..B1.10:
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.22
..B1.11:
        movl      -308(%rbp), %eax
        cmpl      $32767, %eax
        je        ..B1.22
..B1.12:
        movl      -308(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.15
..B1.13:
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.15
..B1.14:
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.22
..B1.15:
        movl      -308(%rbp), %eax
        cmpl      $16383, %eax
        jne       ..B1.18
..B1.16:
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.18
..B1.17:
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.22
..B1.18:
        movl      -300(%rbp), %eax
        cmpl      $32767, %eax
        je        ..B1.22
..B1.19:
        movl      -300(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.87
..B1.20:
        movq      -112(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.87
..B1.21:
        movq      -112(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        jne       ..B1.87
..B1.22:
        movl      -316(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.25
..B1.23:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.31
..B1.24:
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        jne       ..B1.31
..B1.25:
        movl      -308(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.28
..B1.26:
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.31
..B1.27:
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        jne       ..B1.31
..B1.28:
        movl      -300(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.32
..B1.29:
        movq      -112(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.31
..B1.30:
        movq      -112(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.32
..B1.31:
        lea       _smallest_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        movsd     %xmm0, -96(%rbp)
..B1.32:
        movl      -316(%rbp), %eax
        cmpl      $32767, %eax
        jne       ..B1.43
..B1.33:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.35
..B1.34:
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.43
..B1.35:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.39
..B1.36:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $-769, %eax
        orl       $768, %eax
        movw      %ax, -332(%rbp)
..B1.37:
        fldcw     -332(%rbp)
..B1.38:
        movl      $1, -328(%rbp)
..B1.39:
        fldt      16(%rbp)
        fldt      32(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        fldt      48(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        movl      -328(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.42
..B1.40:
        fldcw     -336(%rbp)
..B1.41:
        movl      $0, -328(%rbp)
..B1.42:
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.43:
        movl      -308(%rbp), %eax
        cmpl      $32767, %eax
        jne       ..B1.54
..B1.44:
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.46
..B1.45:
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.54
..B1.46:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.50
..B1.47:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $-769, %eax
        orl       $768, %eax
        movw      %ax, -332(%rbp)
..B1.48:
        fldcw     -332(%rbp)
..B1.49:
        movl      $1, -328(%rbp)
..B1.50:
        fldt      32(%rbp)
        fldt      48(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        fldt      16(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        movl      -328(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.53
..B1.51:
        fldcw     -336(%rbp)
..B1.52:
        movl      $0, -328(%rbp)
..B1.53:
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.54:
        movl      -300(%rbp), %eax
        cmpl      $32767, %eax
        jne       ..B1.65
..B1.55:
        movq      -112(%rbp), %rax
        movl      4(%rax), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.57
..B1.56:
        movq      -112(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.65
..B1.57:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.61
..B1.58:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $-769, %eax
        orl       $768, %eax
        movw      %ax, -332(%rbp)
..B1.59:
        fldcw     -332(%rbp)
..B1.60:
        movl      $1, -328(%rbp)
..B1.61:
        fldt      48(%rbp)
        fldt      16(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        fldt      32(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        movl      -328(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.64
..B1.62:
        fldcw     -336(%rbp)
..B1.63:
        movl      $0, -328(%rbp)
..B1.64:
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.65:
        movl      -316(%rbp), %eax
        cmpl      $32767, %eax
        jge       ..B1.69
..B1.66:
        movl      -308(%rbp), %eax
        cmpl      $32767, %eax
        jge       ..B1.69
..B1.67:
        movl      -300(%rbp), %eax
        cmpl      $32767, %eax
        jne       ..B1.69
..B1.68:
        fldt      48(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.69:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.73
..B1.70:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $-769, %eax
        orl       $768, %eax
        movw      %ax, -332(%rbp)
..B1.71:
        fldcw     -332(%rbp)
..B1.72:
        movl      $1, -328(%rbp)
..B1.73:
        fldt      16(%rbp)
        fldt      32(%rbp)
        fmulp     %st, %st(1)
        fstpt     -32(%rbp)
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.76
..B1.74:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.76
..B1.75:
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.82
..B1.76:
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.79
..B1.77:
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.79
..B1.78:
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.82
..B1.79:
        movq      -104(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.82
..B1.80:
        movq      -104(%rbp), %rax
        movl      4(%rax), %eax
        testl     %eax, %eax
        jne       ..B1.82
..B1.81:
        movq      -104(%rbp), %rax
        movl      (%rax), %eax
        testl     %eax, %eax
        je        ..B1.83
..B1.82:
        fldt      -32(%rbp)
        fldt      48(%rbp)
        faddp     %st, %st(1)
        fstpt     -32(%rbp)
..B1.83:
        movl      -328(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.86
..B1.84:
        fldcw     -336(%rbp)
..B1.85:
        movl      $0, -328(%rbp)
..B1.86:
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.87:
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        je        ..B1.90
..B1.88:
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        je        ..B1.90
..B1.89:
        movq      -112(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.103
..B1.90:
        lea       _smallest_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        movsd     %xmm0, -96(%rbp)
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.94
..B1.91:
        movzwl    -336(%rbp), %eax
        movzwl    %ax, %eax
        andl      $-769, %eax
        orl       $768, %eax
        movw      %ax, -332(%rbp)
..B1.92:
        fldcw     -332(%rbp)
..B1.93:
        movl      $1, -328(%rbp)
..B1.94:
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.96
..B1.95:
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $-32768, %eax
        orl       $16383, %eax
        movq      -128(%rbp), %rdx
        movw      %ax, 8(%rdx)
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        orl       $-2147483648, %eax
        movq      -128(%rbp), %rdx
        movl      %eax, 4(%rdx)
        movl      -320(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        movsd     %xmm0, -48(%rbp)
        fldl      -48(%rbp)
        fldt      16(%rbp)
        fsubp     %st, %st(1)
        fstpt     16(%rbp)
        movq      -128(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        addl      $-16382, %eax
        movl      %eax, -316(%rbp)
..B1.96:
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.98
..B1.97:
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $-32768, %eax
        orl       $16383, %eax
        movq      -120(%rbp), %rdx
        movw      %ax, 8(%rdx)
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        orl       $-2147483648, %eax
        movq      -120(%rbp), %rdx
        movl      %eax, 4(%rdx)
        movl      -312(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        movsd     %xmm0, -48(%rbp)
        fldl      -48(%rbp)
        fldt      32(%rbp)
        fsubp     %st, %st(1)
        fstpt     32(%rbp)
        movq      -120(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        addl      $-16382, %eax
        movl      %eax, -308(%rbp)
..B1.98:
        movq      -112(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        testl     $32767, %eax
        jne       ..B1.100
..B1.99:
        movq      -112(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $-32768, %eax
        orl       $16383, %eax
        movq      -112(%rbp), %rdx
        movw      %ax, 8(%rdx)
        movq      -112(%rbp), %rax
        movl      4(%rax), %eax
        orl       $-2147483648, %eax
        movq      -112(%rbp), %rdx
        movl      %eax, 4(%rdx)
        movl      -304(%rbp), %eax
        movslq    %eax, %rax
        shlq      $3, %rax
        lea       _ones(%rip), %rdx
        addq      %rax, %rdx
        movsd     (%rdx), %xmm0
        movsd     %xmm0, -48(%rbp)
        fldl      -48(%rbp)
        fldt      48(%rbp)
        fsubp     %st, %st(1)
        fstpt     48(%rbp)
        movq      -112(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $32767, %eax
        addl      $-16382, %eax
        movl      %eax, -300(%rbp)
..B1.100:
        movl      -328(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.103
..B1.101:
        fldcw     -336(%rbp)
..B1.102:
        movl      $0, -328(%rbp)
..B1.103:
        movq      -128(%rbp), %rax
        movl      4(%rax), %eax
        movl      %eax, -296(%rbp)
        movq      -128(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -292(%rbp)
        movq      -120(%rbp), %rax
        movl      4(%rax), %eax
        movl      %eax, -288(%rbp)
        movq      -120(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -284(%rbp)
        movq      -112(%rbp), %rax
        movl      4(%rax), %eax
        movl      %eax, -280(%rbp)
        movq      -112(%rbp), %rax
        movl      (%rax), %eax
        movl      %eax, -276(%rbp)
        movl      -312(%rbp), %eax
        xorl      -320(%rbp), %eax
        movl      %eax, -272(%rbp)
        movl      -308(%rbp), %eax
        addl      -316(%rbp), %eax
        addl      $-16383, %eax
        movl      %eax, -268(%rbp)
        movl      -284(%rbp), %eax
        movl      -292(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -88(%rbp)
        movq      -88(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -264(%rbp)
        movl      -88(%rbp), %eax
        movl      %eax, -260(%rbp)
        movl      -284(%rbp), %eax
        movl      -296(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -80(%rbp)
        movq      -80(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -256(%rbp)
        movl      -80(%rbp), %eax
        movl      %eax, -252(%rbp)
        movl      -288(%rbp), %eax
        movl      -292(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -72(%rbp)
        movq      -72(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -248(%rbp)
        movl      -72(%rbp), %eax
        movl      %eax, -244(%rbp)
        movl      -288(%rbp), %eax
        movl      -296(%rbp), %edx
        imulq     %rax, %rdx
        movq      %rdx, -64(%rbp)
        movq      -64(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -240(%rbp)
        movl      -64(%rbp), %eax
        movl      %eax, -236(%rbp)
        movl      -260(%rbp), %eax
        movl      %eax, -232(%rbp)
        movl      -264(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -252(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -244(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -228(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -224(%rbp)
        movl      -236(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -256(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -248(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -224(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -220(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -224(%rbp)
        movl      -224(%rbp), %eax
        addl      -240(%rbp), %eax
        movl      %eax, -216(%rbp)
        movl      -216(%rbp), %eax
        testl     $-2147483648, %eax
        je        ..B1.105
..B1.104:
        movl      $1, %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
        jmp       ..B1.106
..B1.105:
        movl      -216(%rbp), %eax
        shll      $1, %eax
        movl      -220(%rbp), %edx
        shrl      $31, %edx
        orl       %edx, %eax
        movl      %eax, -216(%rbp)
        movl      -220(%rbp), %eax
        shll      $1, %eax
        movl      -228(%rbp), %edx
        shrl      $31, %edx
        orl       %edx, %eax
        movl      %eax, -220(%rbp)
        movl      -228(%rbp), %eax
        shll      $1, %eax
        movl      -232(%rbp), %edx
        shrl      $31, %edx
        orl       %edx, %eax
        movl      %eax, -228(%rbp)
        shll      $1, -232(%rbp)
..B1.106:
        movl      -268(%rbp), %eax
        movl      -300(%rbp), %edx
        cmpl      %edx, %eax
        jg        ..B1.111
..B1.107:
        movl      -268(%rbp), %eax
        movl      -300(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.112
..B1.108:
        movl      -216(%rbp), %eax
        movl      -280(%rbp), %edx
        cmpl      %edx, %eax
        ja        ..B1.111
..B1.109:
        movl      -216(%rbp), %eax
        movl      -280(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.112
..B1.110:
        movl      -220(%rbp), %eax
        movl      -276(%rbp), %edx
        cmpl      %edx, %eax
        jb        ..B1.112
..B1.111:
        movl      -300(%rbp), %eax
        negl      %eax
        addl      -268(%rbp), %eax
        movl      %eax, -212(%rbp)
        movl      -280(%rbp), %eax
        movl      %eax, -208(%rbp)
        movl      -276(%rbp), %eax
        movl      %eax, -204(%rbp)
        movl      $0, %eax
        movl      %eax, -200(%rbp)
        movl      %eax, -196(%rbp)
        jmp       ..B1.113
..B1.112:
        movl      -268(%rbp), %eax
        negl      %eax
        addl      -300(%rbp), %eax
        movl      %eax, -212(%rbp)
        movl      -300(%rbp), %eax
        movl      %eax, -268(%rbp)
        movl      -216(%rbp), %eax
        movl      %eax, -208(%rbp)
        movl      -220(%rbp), %eax
        movl      %eax, -204(%rbp)
        movl      -228(%rbp), %eax
        movl      %eax, -196(%rbp)
        movl      -232(%rbp), %eax
        movl      %eax, -200(%rbp)
        movl      -280(%rbp), %eax
        movl      %eax, -216(%rbp)
        movl      -276(%rbp), %eax
        movl      %eax, -220(%rbp)
        movl      $0, %eax
        movl      %eax, -232(%rbp)
        movl      %eax, -228(%rbp)
        movl      -272(%rbp), %eax
        movl      %eax, -304(%rbp)
        movq      -112(%rbp), %rax
        movzbl    9(%rax), %eax
        movzbl    %al, %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, -272(%rbp)
..B1.113:
        movl      $0, -192(%rbp)
        movl      -212(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.123
..B1.114:
        movl      -212(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.116
..B1.115:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -200(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      %eax, -192(%rbp)
        movl      -196(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -200(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -200(%rbp)
        movl      -204(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -196(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -196(%rbp)
        movl      -208(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -204(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -204(%rbp)
        movl      -184(%rbp), %eax
        movl      %eax, %ecx
        shrl      %cl, -208(%rbp)
        jmp       ..B1.123
..B1.116:
        movl      -212(%rbp), %eax
        cmpl      $64, %eax
        jge       ..B1.118
..B1.117:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -188(%rbp)
        movl      $-32, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -196(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -200(%rbp), %edx
        movl      $1, %ecx
        testl     %edx, %edx
        cmovne    %ecx, %edx
        orl       %edx, %eax
        movl      %eax, -192(%rbp)
        movl      -204(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -196(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -200(%rbp)
        movl      -208(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -204(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -196(%rbp)
        movl      -208(%rbp), %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -204(%rbp)
        movl      $0, -208(%rbp)
        jmp       ..B1.123
..B1.118:
        movl      -212(%rbp), %eax
        cmpl      $96, %eax
        jge       ..B1.120
..B1.119:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $96, %eax
        movl      %eax, -188(%rbp)
        movl      $-64, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -204(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -200(%rbp), %edx
        orl       -196(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -208(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -204(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -200(%rbp)
        movl      -208(%rbp), %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -196(%rbp)
        movl      $0, %eax
        movl      %eax, -204(%rbp)
        movl      %eax, -208(%rbp)
        jmp       ..B1.123
..B1.120:
        movl      -212(%rbp), %eax
        cmpl      $128, %eax
        jge       ..B1.122
..B1.121:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $128, %eax
        movl      %eax, -188(%rbp)
        movl      $-96, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -208(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -196(%rbp), %edx
        orl       -204(%rbp), %edx
        orl       -200(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -208(%rbp), %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -200(%rbp)
        movl      $0, -196(%rbp)
        movl      $0, %eax
        movl      %eax, -204(%rbp)
        movl      %eax, -208(%rbp)
        jmp       ..B1.123
..B1.122:
        movl      -204(%rbp), %eax
        orl       -208(%rbp), %eax
        orl       -196(%rbp), %eax
        orl       -200(%rbp), %eax
        movl      $1, %eax
        movl      $0, %edx
        cmovne    %eax, %edx
        movl      %edx, -192(%rbp)
        movl      $0, -200(%rbp)
        movl      $0, -196(%rbp)
        movl      $0, %eax
        movl      %eax, -204(%rbp)
        movl      %eax, -208(%rbp)
..B1.123:
        movl      -272(%rbp), %eax
        movl      -304(%rbp), %edx
        cmpl      %edx, %eax
        jne       ..B1.130
..B1.124:
        movl      -232(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      -200(%rbp), %eax
        addl      -232(%rbp), %eax
        movl      %eax, -232(%rbp)
        movl      -232(%rbp), %eax
        movl      -176(%rbp), %edx
        cmpl      %edx, %eax
        jb        ..B1.126
..B1.125:
        movl      $0, -224(%rbp)
        jmp       ..B1.127
..B1.126:
        movl      $1, -224(%rbp)
..B1.127:
        movl      -228(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -196(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -224(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -228(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -224(%rbp)
        movl      -220(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -204(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -224(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -220(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -224(%rbp)
        movl      -216(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -208(%rbp), %eax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -224(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -216(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -224(%rbp)
        movl      -224(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.129
..B1.128:
        movl      -220(%rbp), %eax
        shll      $31, %eax
        movl      -232(%rbp), %edx
        orl       -228(%rbp), %edx
        orl       -192(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -216(%rbp), %eax
        shll      $31, %eax
        movl      -220(%rbp), %edx
        shrl      $1, %edx
        orl       %edx, %eax
        movl      %eax, -168(%rbp)
        movl      -216(%rbp), %eax
        shrl      $1, %eax
        orl       $-2147483648, %eax
        movl      %eax, -164(%rbp)
        movl      $1, %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
        jmp       ..B1.156
..B1.129:
        movl      -228(%rbp), %eax
        movl      -192(%rbp), %edx
        orl       -232(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -220(%rbp), %eax
        movl      %eax, -168(%rbp)
        movl      -216(%rbp), %eax
        movl      %eax, -164(%rbp)
        jmp       ..B1.156
..B1.130:
        movl      -192(%rbp), %eax
        negl      %eax
        movl      %eax, -192(%rbp)
        movl      -192(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.132
..B1.131:
        movl      $0, -172(%rbp)
        jmp       ..B1.133
..B1.132:
        movl      $1, -172(%rbp)
..B1.133:
        movl      -232(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -200(%rbp), %eax
        negq      %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -172(%rbp), %eax
        movslq    %eax, %rax
        negq      %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -232(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -172(%rbp)
        movl      -228(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -196(%rbp), %eax
        negq      %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -172(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -228(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -172(%rbp)
        movl      -220(%rbp), %eax
        movq      %rax, -56(%rbp)
        movl      -204(%rbp), %eax
        negq      %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -172(%rbp), %eax
        movslq    %eax, %rax
        addq      -56(%rbp), %rax
        movq      %rax, -56(%rbp)
        movl      -56(%rbp), %eax
        movl      %eax, -220(%rbp)
        movq      -56(%rbp), %rax
        shrq      $32, %rax
        movl      %eax, -172(%rbp)
        movl      -208(%rbp), %eax
        negl      %eax
        addl      -216(%rbp), %eax
        movl      %eax, -216(%rbp)
        movl      -172(%rbp), %eax
        addl      -216(%rbp), %eax
        movl      %eax, -216(%rbp)
        movl      -216(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.135
..B1.134:
        movl      -216(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      $0, -212(%rbp)
        jmp       ..B1.144
..B1.135:
        movl      -220(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.137
..B1.136:
        movl      -220(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      $32, -212(%rbp)
        jmp       ..B1.144
..B1.137:
        movl      -228(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.139
..B1.138:
        movl      -228(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      $64, -212(%rbp)
        jmp       ..B1.144
..B1.139:
        movl      -232(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.141
..B1.140:
        movl      -232(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      $96, -212(%rbp)
        jmp       ..B1.144
..B1.141:
        movl      -192(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.143
..B1.142:
        movl      -192(%rbp), %eax
        movl      %eax, -176(%rbp)
        movl      $128, -212(%rbp)
        jmp       ..B1.144
..B1.143:
        movl      -324(%rbp), %eax
        movl      $1, %edx
        movl      $0, %ecx
        cmpl      $1024, %eax
        cmove     %edx, %ecx
        movslq    %ecx, %rcx
        shlq      $3, %rcx
        lea       _zeros(%rip), %rax
        addq      %rcx, %rax
        movsd     (%rax), %xmm0
        movsd     %xmm0, -48(%rbp)
        fldl      -48(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.144:
        movl      -176(%rbp), %eax
        testl     $-2147483648, %eax
        jne       ..B1.146
..B1.145:
        shll      $1, -176(%rbp)
        movl      $1, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -212(%rbp)
        jmp       ..B1.144
..B1.146:
        movl      -212(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.148
..B1.147:
        movl      -212(%rbp), %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -184(%rbp)
        movl      $1, %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -160(%rbp)
        movl      -228(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -192(%rbp), %edx
        orl       -232(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -220(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -228(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -168(%rbp)
        movl      -216(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -220(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -164(%rbp)
        jmp       ..B1.155
..B1.148:
        movl      -212(%rbp), %eax
        cmpl      $64, %eax
        jge       ..B1.150
..B1.149:
        movl      $-32, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -184(%rbp)
        movl      $1, %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -160(%rbp)
        movl      -232(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -192(%rbp), %edx
        movl      $1, %ecx
        testl     %edx, %edx
        cmovne    %ecx, %edx
        orl       %edx, %eax
        movl      %eax, -192(%rbp)
        movl      -228(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -232(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -168(%rbp)
        movl      -220(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -228(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -164(%rbp)
        jmp       ..B1.155
..B1.150:
        movl      -212(%rbp), %eax
        cmpl      $96, %eax
        jge       ..B1.152
..B1.151:
        movl      $-64, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $96, %eax
        movl      %eax, -184(%rbp)
        movl      $1, %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -160(%rbp)
        movl      -188(%rbp), %eax
        movl      %eax, %ecx
        shll      %cl, -192(%rbp)
        movl      -232(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -192(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -168(%rbp)
        movl      -228(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -232(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -164(%rbp)
        jmp       ..B1.155
..B1.152:
        movl      -212(%rbp), %eax
        cmpl      $128, %eax
        jge       ..B1.154
..B1.153:
        movl      $-96, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $128, %eax
        movl      %eax, -184(%rbp)
        movl      $1, %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        decl      %eax
        movl      %eax, -160(%rbp)
        movl      $0, -192(%rbp)
        movl      -192(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      %eax, -168(%rbp)
        movl      -232(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      -192(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        andl      -160(%rbp), %edx
        orl       %edx, %eax
        movl      %eax, -164(%rbp)
        jmp       ..B1.155
..B1.154:
        movl      $-128, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -188(%rbp)
        movl      $0, %eax
        movl      %eax, -168(%rbp)
        movl      %eax, -192(%rbp)
        movl      -192(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      %eax, -164(%rbp)
..B1.155:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
..B1.156:
        movl      $-1, %eax
        addl      -268(%rbp), %eax
        cmpl      $32766, %eax
        jb        ..B1.167
..B1.157:
        movl      -268(%rbp), %eax
        cmpl      $32767, %eax
        jl        ..B1.159
..B1.158:
        movl      -272(%rbp), %eax
        movslq    %eax, %rax
        shlq      $4, %rax
        lea       _large_value_80(%rip), %rdx
        addq      %rax, %rdx
        fldt      (%rdx)
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
        fldt      -16(%rbp)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.159:
        movl      -268(%rbp), %eax
        negl      %eax
        incl      %eax
        movl      %eax, -212(%rbp)
        movl      -212(%rbp), %eax
        cmpl      $32, %eax
        jge       ..B1.161
..B1.160:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $32, %eax
        movl      %eax, -188(%rbp)
        movl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -168(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -192(%rbp), %edx
        movl      $1, %ecx
        testl     %edx, %edx
        cmovne    %ecx, %edx
        orl       %edx, %eax
        movl      %eax, -192(%rbp)
        movl      -164(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -168(%rbp), %edx
        movl      -184(%rbp), %ecx
        shrl      %cl, %edx
        orl       %edx, %eax
        movl      %eax, -168(%rbp)
        movl      -184(%rbp), %eax
        movl      %eax, %ecx
        shrl      %cl, -164(%rbp)
        jmp       ..B1.166
..B1.161:
        movl      -212(%rbp), %eax
        cmpl      $64, %eax
        jge       ..B1.163
..B1.162:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $64, %eax
        movl      %eax, -188(%rbp)
        movl      $-32, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -164(%rbp), %eax
        movl      -188(%rbp), %edx
        movl      %edx, %ecx
        shll      %cl, %eax
        andl      -180(%rbp), %eax
        movl      -192(%rbp), %edx
        orl       -168(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      -164(%rbp), %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      %eax, -168(%rbp)
        movl      $0, -164(%rbp)
        jmp       ..B1.166
..B1.163:
        movl      -212(%rbp), %eax
        cmpl      $96, %eax
        jge       ..B1.165
..B1.164:
        movl      -212(%rbp), %eax
        negl      %eax
        addl      $96, %eax
        movl      %eax, -188(%rbp)
        movl      $-64, %eax
        addl      -212(%rbp), %eax
        movl      %eax, -184(%rbp)
        movl      $-1, %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        notl      %eax
        movl      %eax, -180(%rbp)
        movl      -164(%rbp), %eax
        movl      -184(%rbp), %edx
        movl      %edx, %ecx
        shrl      %cl, %eax
        movl      -164(%rbp), %edx
        movl      -188(%rbp), %ecx
        shll      %cl, %edx
        andl      -180(%rbp), %edx
        orl       -168(%rbp), %edx
        orl       -192(%rbp), %edx
        movl      $1, %edx
        movl      $0, %ecx
        cmovne    %edx, %ecx
        orl       %ecx, %eax
        movl      %eax, -192(%rbp)
        movl      $0, %eax
        movl      %eax, -168(%rbp)
        movl      %eax, -164(%rbp)
        jmp       ..B1.166
..B1.165:
        movl      $1, -192(%rbp)
        movl      $0, %eax
        movl      %eax, -168(%rbp)
        movl      %eax, -164(%rbp)
..B1.166:
        movl      $0, -268(%rbp)
..B1.167:
        movl      -192(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.222
..B1.168:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm0
        movsd     %xmm0, -48(%rbp)
        fldl      -48(%rbp)
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        faddp     %st, %st(1)
        fstpt     -16(%rbp)
        movl      -324(%rbp), %eax
        movl      %eax, -156(%rbp)
        testl     %eax, %eax
        je        ..B1.172
..B1.169:
        movl      -156(%rbp), %eax
        cmpl      $3072, %eax
        je        ..B1.182
..B1.170:
        movl      -156(%rbp), %eax
        cmpl      $2048, %eax
        je        ..B1.186
..B1.171:
        movl      -156(%rbp), %eax
        cmpl      $1024, %eax
        je        ..B1.204
        jmp       ..B1.222
..B1.172:
        movl      -192(%rbp), %eax
        testl     $-2147483648, %eax
        je        ..B1.182
..B1.173:
        movl      -168(%rbp), %eax
        andl      $1, %eax
        movl      -192(%rbp), %edx
        andl      $2147483647, %edx
        orl       %edx, %eax
        je        ..B1.182
..B1.174:
        movl      $1, %eax
        addl      -168(%rbp), %eax
        movl      %eax, -168(%rbp)
        jne       ..B1.180
..B1.175:
        movl      $1, %eax
        addl      -164(%rbp), %eax
        movl      %eax, -152(%rbp)
        movl      %eax, -164(%rbp)
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.177
..B1.176:
        movl      $0, -148(%rbp)
        jmp       ..B1.178
..B1.177:
        movl      $-2147483648, -148(%rbp)
..B1.178:
        movl      -148(%rbp), %eax
        movl      -152(%rbp), %edx
        cmpl      %eax, %edx
        jne       ..B1.180
..B1.179:
        orl       $-2147483648, -164(%rbp)
        movl      $1, %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
..B1.180:
        movl      -268(%rbp), %eax
        cmpl      $32767, %eax
        jl        ..B1.182
..B1.181:
        movl      -272(%rbp), %eax
        movslq    %eax, %rax
        shlq      $4, %rax
        lea       _large_value_80(%rip), %rdx
        addq      %rax, %rdx
        fldt      (%rdx)
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
        fldt      -16(%rbp)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.182:
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.185
..B1.183:
        movl      $-1, %eax
        addl      -268(%rbp), %eax
        movl      $-2147483648, %edx
        negl      %edx
        addl      -164(%rbp), %edx
        orl       %edx, %eax
        orl       -168(%rbp), %eax
        jne       ..B1.222
..B1.184:
        movl      -192(%rbp), %eax
        cmpl      $-2147483648, %eax
        jne       ..B1.222
..B1.185:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
        jmp       ..B1.222
..B1.186:
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.195
..B1.187:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.194
..B1.188:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.195
..B1.189:
        movl      -164(%rbp), %eax
        cmpl      $2147483647, %eax
        jb        ..B1.194
..B1.190:
        movl      -164(%rbp), %eax
        cmpl      $2147483647, %eax
        jne       ..B1.195
..B1.191:
        movl      -168(%rbp), %eax
        cmpl      $-1, %eax
        jb        ..B1.194
..B1.192:
        movl      -168(%rbp), %eax
        cmpl      $-1, %eax
        jne       ..B1.195
..B1.193:
        movl      -192(%rbp), %eax
        cmpl      $-2147483648, %eax
        jae       ..B1.195
..B1.194:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
..B1.195:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.222
..B1.196:
        movl      $1, %eax
        addl      -168(%rbp), %eax
        movl      %eax, -168(%rbp)
        jne       ..B1.202
..B1.197:
        movl      $1, %eax
        addl      -164(%rbp), %eax
        movl      %eax, -144(%rbp)
        movl      %eax, -164(%rbp)
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.199
..B1.198:
        movl      $0, -140(%rbp)
        jmp       ..B1.200
..B1.199:
        movl      $-2147483648, -140(%rbp)
..B1.200:
        movl      -140(%rbp), %eax
        movl      -144(%rbp), %edx
        cmpl      %eax, %edx
        jne       ..B1.202
..B1.201:
        orl       $-2147483648, -164(%rbp)
        movl      $1, %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
..B1.202:
        movl      -268(%rbp), %eax
        cmpl      $32767, %eax
        jl        ..B1.222
..B1.203:
        movl      -272(%rbp), %eax
        movslq    %eax, %rax
        shlq      $4, %rax
        lea       _large_value_80(%rip), %rdx
        addq      %rax, %rdx
        fldt      (%rdx)
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
        fldt      -16(%rbp)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.204:
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        jne       ..B1.213
..B1.205:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.212
..B1.206:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.213
..B1.207:
        movl      -164(%rbp), %eax
        cmpl      $2147483647, %eax
        jb        ..B1.212
..B1.208:
        movl      -164(%rbp), %eax
        cmpl      $2147483647, %eax
        jne       ..B1.213
..B1.209:
        movl      -168(%rbp), %eax
        cmpl      $-1, %eax
        jb        ..B1.212
..B1.210:
        movl      -168(%rbp), %eax
        cmpl      $-1, %eax
        jne       ..B1.213
..B1.211:
        movl      -192(%rbp), %eax
        cmpl      $-2147483648, %eax
        jae       ..B1.213
..B1.212:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
..B1.213:
        movl      -272(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.222
..B1.214:
        movl      $1, %eax
        addl      -168(%rbp), %eax
        movl      %eax, -168(%rbp)
        jne       ..B1.220
..B1.215:
        movl      $1, %eax
        addl      -164(%rbp), %eax
        movl      %eax, -136(%rbp)
        movl      %eax, -164(%rbp)
        movl      -268(%rbp), %eax
        testl     %eax, %eax
        je        ..B1.217
..B1.216:
        movl      $0, -132(%rbp)
        jmp       ..B1.218
..B1.217:
        movl      $-2147483648, -132(%rbp)
..B1.218:
        movl      -132(%rbp), %eax
        movl      -136(%rbp), %edx
        cmpl      %eax, %edx
        jne       ..B1.220
..B1.219:
        orl       $-2147483648, -164(%rbp)
        movl      $1, %eax
        addl      -268(%rbp), %eax
        movl      %eax, -268(%rbp)
..B1.220:
        movl      -268(%rbp), %eax
        cmpl      $32767, %eax
        jl        ..B1.222
..B1.221:
        movl      -272(%rbp), %eax
        movslq    %eax, %rax
        shlq      $4, %rax
        lea       _large_value_80(%rip), %rdx
        addq      %rax, %rdx
        fldt      (%rdx)
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     -16(%rbp)
        fldt      -16(%rbp)
        fstpt     -32(%rbp)
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_offset 6, -16
..B1.222:
        movq      -104(%rbp), %rax
        movzbl    9(%rax), %eax
        movzbl    %al, %eax
        andl      $-129, %eax
        movl      -272(%rbp), %edx
        andl      $1, %edx
        shll      $7, %edx
        orl       %edx, %eax
        movq      -104(%rbp), %rdx
        movb      %al, 9(%rdx)
        movq      -104(%rbp), %rax
        movzwl    8(%rax), %eax
        movzwl    %ax, %eax
        andl      $-32768, %eax
        movl      -268(%rbp), %edx
        andl      $32767, %edx
        orl       %edx, %eax
        movq      -104(%rbp), %rdx
        movw      %ax, 8(%rdx)
        movq      -104(%rbp), %rax
        movl      -164(%rbp), %edx
        movl      %edx, 4(%rax)
        movq      -104(%rbp), %rax
        movl      -168(%rbp), %edx
        movl      %edx, (%rax)
        fldt      -32(%rbp)
        leave     
	.cfi_restore 6
        ret       
	.cfi_endproc
	.type	fmal,@function
	.size	fmal,.-fmal
	.data
# -- End  fmal
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
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
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.word	0
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
