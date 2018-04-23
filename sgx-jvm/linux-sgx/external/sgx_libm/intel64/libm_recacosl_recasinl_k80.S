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
	.file "libm_recacosl_recasinl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_carctrigprepare_k80
	.text
       .align    16,0x90
	.globl __libm_carctrigprepare_k80
__libm_carctrigprepare_k80:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 320 + %rsp
# parameter 8: 328 + %rsp
# parameter 9: 336 + %rsp
# parameter 10: 344 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___libm_carctrigprepare_k80.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $272, %rsp
	.cfi_def_cfa_offset 320
        lea       ones(%rip), %r10
        movq      %rdx, %rbp
        lea       zeros(%rip), %r11
        movq      %fs:40, %rax
        movq      %rcx, %r13
        xorq      %rsp, %rax
        movq      %r9, %r14
        fldt      (%r10)
        movq      %r8, %r12
        fldt      (%r11)
        movq      %rsi, %rbx
        movq      (%rdi), %r10
        movq      %rax, 256(%rsp)
        xorl      %eax, %eax
        movq      %r10, (%rbp)
        movq      8(%rdi), %rdx
        movb      9(%rdi), %cl
        movq      %rbp, %rdi
        movq      %rdx, 8(%rbp)
        andb      $127, %cl
        movl      %eax, 96(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        fstpt     64(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        movl      %eax, 32(%rbp)
        movb      %cl, 9(%rbp)
        fstpt     16(%rbp)
..___tag_value___libm_carctrigprepare_k80.14:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.15:
..B1.2:
        fldt      (%rsp)
        movq      %r13, %rdi
        movq      (%rbx), %rax
        movq      %rax, (%r13)
        movq      8(%rbx), %rdx
        movb      9(%rbx), %cl
        movq      %rdx, 8(%r13)
        andb      $127, %cl
        movl      $0, 32(%r13)
        movb      %cl, 9(%r13)
        fstpt     16(%r13)
..___tag_value___libm_carctrigprepare_k80.16:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.17:
..B1.3:
        fldt      (%r13)
        lea       t32(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      16(%r13)
        fmulp     %st, %st(4)
        movq      320(%rsp), %rbx
        fxch      %st(3)
        fadd      %st(0), %st
        fldt      (%rdx)
        fmul      %st, %st(3)
        movl      32(%r13), %eax
        fxch      %st(2)
        fsub      %st(3), %st
        addl      %eax, %eax
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(4)
        fmul      %st(4), %st
        movl      %eax, 32(%rbx)
        fadd      %st(0), %st
        fxch      %st(4)
        fmul      %st(0), %st
        fxch      %st(2)
        fstpt     16(%rsp)
        fld       %st(3)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rbx)
        fldt      (%rbx)
        fsubrp    %st, %st(1)
        movzwl    8(%rbp), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        fadd      %st(1), %st
        fstpt     16(%rbx)
        cmpl      $8383, %eax
        fstpt     32(%rsp)
        jl        ..B1.7
..B1.4:
        cmpl      $24383, %eax
        jg        ..B1.7
..B1.5:
        movzwl    72(%rsp), %esi
        movl      %esi, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B1.7
..B1.6:
        cmpl      $24383, %r8d
        jle       ..B1.10
..B1.7:
        movq      %rbp, %rdi
..___tag_value___libm_carctrigprepare_k80.18:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.19:
..B1.8:
        lea       64(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.20:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.21:
..B1.9:
        movzwl    8(%rbp), %edx
        movl      %edx, %eax
        movzwl    72(%rsp), %esi
        movl      %esi, %r8d
        andl      $32767, %eax
        andl      $32767, %r8d
..B1.10:
        movl      32(%rbp), %ecx
        movl      96(%rsp), %r9d
        lea       (%rax,%rcx), %r13d
        lea       -134(%r9,%r8), %r11d
        cmpl      %r13d, %r11d
        lea       (%r8,%r9), %r10d
        jge       ..B1.27
..B1.11:
        lea       134(%r9,%r8), %edi
        cmpl      %edi, %r13d
        jge       ..B1.27
..B1.12:
        cmpl      $0, 4(%rbp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, (%rbp)
        je        ..B1.27
..B1.14:
        cmpl      $0, 68(%rsp)
        jne       ..B1.16
..B1.15:
        cmpl      $0, 64(%rsp)
        je        ..B1.27
..B1.16:
        cmpl      %r9d, %ecx
        je        ..B1.18
..B1.17:
        fldt      32(%rsp)
        subl      %r9d, %ecx
        fstpt     (%rsp)
        addl      $16383, %ecx
        fldt      (%rbp)
        andl      $32767, %ecx
        fldt      16(%rbp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 32(%rbp)
        fxch      %st(2)
        fstpt     (%rbp)
        fldt      (%rbp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        fstpt     32(%rsp)
        movzwl    8(%rbp), %edx
        jmp       ..B1.19
..B1.18:
        fldt      (%rbp)
        fstpt     32(%rsp)
..B1.19:
        fldt      64(%rsp)
        movl      %esi, %eax
        fldt      32(%rsp)
        andl      $32767, %edx
        andl      $32767, %eax
        movl      %r9d, 32(%r14)
        cmpl      %eax, %edx
        fadd      %st(1), %st
        fstpt     16(%r14)
        fldt      16(%r14)
        jg        ..B1.24
..B1.20:
        movl      %esi, %eax
        andl      $32767, %eax
        cmpl      %eax, %edx
        jne       ..B1.25
..B1.21:
        movl      4(%rbp), %eax
        cmpl      68(%rsp), %eax
        ja        ..B1.24
..B1.22:
        jne       ..B1.25
..B1.23:
        movl      (%rbp), %eax
        cmpl      64(%rsp), %eax
        jbe       ..B1.25
..B1.24:
        fldt      32(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      80(%rsp)
        faddp     %st, %st(2)
        fldt      16(%rbp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.26
..B1.25:
        fldt      32(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      80(%rsp)
        fldt      16(%rbp)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
..B1.26:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%r14)
        fldt      (%r14)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r14)
        jmp       ..B1.31
..B1.27:
        cmpq      $0, (%rbp)
        je        ..B1.29
..B1.28:
        lea       -16517(%r9,%r8), %edx
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %edx
        jl        ..B1.30
..B1.29:
        movq      64(%rsp), %rax
        movq      %rax, (%r14)
        movq      96(%rsp), %rax
        movq      %rax, 32(%r14)
        movq      72(%rsp), %rdx
        movq      80(%rsp), %rcx
        movq      88(%rsp), %r13
        movq      104(%rsp), %rax
        movq      %rdx, 8(%r14)
        movq      %rcx, 16(%r14)
        movq      %r13, 24(%r14)
        movq      %rax, 40(%r14)
        jmp       ..B1.31
..B1.30:
        movq      (%rbp), %rax
        movq      %rax, (%r14)
        movq      8(%rbp), %rdx
        movq      %rdx, 8(%r14)
        movq      16(%rbp), %rcx
        movq      %rcx, 16(%r14)
        movq      24(%rbp), %r13
        movq      %r13, 24(%r14)
        movq      32(%rbp), %rax
        movq      %rax, 32(%r14)
        movq      40(%rbp), %rax
        movq      %rax, 40(%r14)
..B1.31:
        movzwl    8(%rbp), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.35
..B1.32:
        cmpl      $24383, %eax
        jg        ..B1.35
..B1.33:
        cmpl      $8383, %r8d
        jl        ..B1.35
..B1.34:
        cmpl      $24383, %r8d
        jle       ..B1.38
..B1.35:
        movq      %rbp, %rdi
..___tag_value___libm_carctrigprepare_k80.22:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.23:
..B1.36:
        lea       64(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.24:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.25:
..B1.37:
        movzwl    72(%rsp), %esi
        movl      %esi, %ecx
        andl      $32767, %ecx
        movl      96(%rsp), %r9d
        movzwl    8(%rbp), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        lea       (%r9,%rcx), %r10d
        lea       -134(%r9,%rcx), %r11d
..B1.38:
        movl      32(%rbp), %r13d
        lea       (%rax,%r13), %ecx
        cmpl      %ecx, %r11d
        jge       ..B1.55
..B1.39:
        lea       134(%r10), %r8d
        cmpl      %r8d, %ecx
        jge       ..B1.55
..B1.40:
        cmpl      $0, 4(%rbp)
        jne       ..B1.42
..B1.41:
        cmpl      $0, (%rbp)
        je        ..B1.55
..B1.42:
        cmpl      $0, 68(%rsp)
        jne       ..B1.44
..B1.43:
        cmpl      $0, 64(%rsp)
        je        ..B1.55
..B1.44:
        cmpl      %r9d, %r13d
        je        ..B1.46
..B1.45:
        fldt      32(%rsp)
        subl      %r9d, %r13d
        fstpt     (%rsp)
        addl      $16383, %r13d
        fldt      (%rbp)
        andl      $32767, %r13d
        fldt      16(%rbp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %r13d, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 32(%rbp)
        fxch      %st(2)
        fstpt     (%rbp)
        fldt      (%rbp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        movzwl    8(%rbp), %edx
        jmp       ..B1.47
..B1.46:
        fldt      (%rbp)
..B1.47:
        fldt      64(%rsp)
        fld       %st(1)
        movl      %esi, %eax
        andl      $32767, %edx
        andl      $32767, %eax
        fadd      %st(1), %st
        movl      %r9d, 32(%r14)
        cmpl      %eax, %edx
        fstpt     16(%r14)
        fldt      16(%r14)
        jg        ..B1.52
..B1.48:
        andl      $32767, %esi
        cmpl      %esi, %edx
        jne       ..B1.53
..B1.49:
        movl      4(%rbp), %eax
        cmpl      68(%rsp), %eax
        ja        ..B1.52
..B1.50:
        jne       ..B1.53
..B1.51:
        movl      (%rbp), %eax
        cmpl      64(%rsp), %eax
        jbe       ..B1.53
..B1.52:
        fldt      80(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      16(%rbp)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.54
..B1.53:
        fldt      16(%rbp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        faddp     %st, %st(1)
..B1.54:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%r14)
        fldt      (%r14)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r14)
        jmp       ..B1.59
..B1.55:
        cmpq      $0, (%rbp)
        je        ..B1.57
..B1.56:
        addl      $-16517, %r10d
        lea       -16383(%r13,%rax), %eax
        cmpl      %eax, %r10d
        jl        ..B1.58
..B1.57:
        movq      64(%rsp), %rax
        movq      72(%rsp), %rdx
        movq      80(%rsp), %rcx
        movq      88(%rsp), %rsi
        movq      96(%rsp), %r8
        movq      104(%rsp), %r9
        movq      %rax, (%r14)
        movq      %rdx, 8(%r14)
        movq      %rcx, 16(%r14)
        movq      %rsi, 24(%r14)
        movq      %r8, 32(%r14)
        movq      %r9, 40(%r14)
        jmp       ..B1.59
..B1.58:
        movq      (%rbp), %rax
        movq      %rax, (%r14)
        movq      8(%rbp), %rdx
        movq      %rdx, 8(%r14)
        movq      16(%rbp), %rcx
        movq      %rcx, 16(%r14)
        movq      24(%rbp), %rsi
        movq      %rsi, 24(%r14)
        movq      32(%rbp), %r8
        movq      %r8, 32(%r14)
        movq      40(%rbp), %r9
        movq      %r9, 40(%r14)
..B1.59:
        movzwl    8(%r14), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.61
..B1.60:
        cmpl      $24383, %eax
        jle       ..B1.63
..B1.61:
        movq      %r14, %rdi
..___tag_value___libm_carctrigprepare_k80.26:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.27:
..B1.62:
        movq      %r14, %rdi
..___tag_value___libm_carctrigprepare_k80.28:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.29:
..B1.63:
        fldt      (%r14)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%r14)
        fldt      16(%rsp)
        movl      32(%r14), %eax
        fmul      %st(4), %st
        addl      %eax, %eax
        movl      %eax, 144(%rsp)
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubrp    %st, %st(2)
        movzwl    120(%rsp), %esi
        movl      %esi, %r11d
        andl      $32767, %r11d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     128(%rsp)
        cmpl      $8383, %r11d
        fstpt     32(%rsp)
        jl        ..B1.67
..B1.64:
        cmpl      $24383, %r11d
        jg        ..B1.67
..B1.65:
        movzwl    8(%rbx), %r8d
        movl      %r8d, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B1.67
..B1.66:
        cmpl      $24383, %r10d
        jle       ..B1.70
..B1.67:
        lea       112(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.30:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.31:
..B1.68:
        movq      %rbx, %rdi
..___tag_value___libm_carctrigprepare_k80.32:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.33:
..B1.69:
        movzwl    120(%rsp), %esi
        movl      %esi, %r11d
        movzwl    8(%rbx), %r8d
        movl      %r8d, %r10d
        movl      144(%rsp), %eax
        andl      $32767, %r11d
        andl      $32767, %r10d
..B1.70:
        movl      32(%rbx), %r9d
        lea       (%rax,%r11), %ecx
        lea       -134(%r9,%r10), %edx
        cmpl      %edx, %ecx
        jle       ..B1.87
..B1.71:
        lea       134(%r9,%r10), %edx
        cmpl      %edx, %ecx
        jge       ..B1.87
..B1.72:
        cmpl      $0, 116(%rsp)
        jne       ..B1.74
..B1.73:
        cmpl      $0, 112(%rsp)
        je        ..B1.87
..B1.74:
        cmpl      $0, 4(%rbx)
        jne       ..B1.76
..B1.75:
        cmpl      $0, (%rbx)
        je        ..B1.87
..B1.76:
        cmpl      %r9d, %eax
        je        ..B1.78
..B1.77:
        fldt      32(%rsp)
        subl      %r9d, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      112(%rsp)
        andl      $32767, %eax
        fldt      128(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %r9d, 144(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        movzwl    120(%rsp), %esi
        jmp       ..B1.79
..B1.78:
        fldt      112(%rsp)
        fldt      128(%rsp)
..B1.79:
        fldt      (%rbx)
        movl      %esi, %eax
        andl      $32767, %r8d
        andl      $32767, %eax
        fld       %st(2)
        cmpl      %r8d, %eax
        movl      %r9d, 192(%rsp)
        fadd      %st(1), %st
        jg        ..B1.84
..B1.80:
        andl      $32767, %esi
        cmpl      %r8d, %esi
        jne       ..B1.85
..B1.81:
        movl      4(%rbx), %eax
        cmpl      116(%rsp), %eax
        jb        ..B1.84
..B1.82:
        jne       ..B1.85
..B1.83:
        movl      112(%rsp), %eax
        cmpl      (%rbx), %eax
        jbe       ..B1.85
..B1.84:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%rbx)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.86
..B1.85:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%rbx)
        faddp     %st, %st(1)
..B1.86:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        jmp       ..B1.91
..B1.87:
        cmpq      $0, 112(%rsp)
        je        ..B1.89
..B1.88:
        lea       -16517(%r9,%r10), %edx
        lea       -16383(%rax,%r11), %eax
        cmpl      %eax, %edx
        jl        ..B1.90
..B1.89:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rsi
        movq      32(%rbx), %r8
        movq      40(%rbx), %r9
        movq      %rax, 160(%rsp)
        movq      %rdx, 168(%rsp)
        movq      %rcx, 176(%rsp)
        movq      %rsi, 184(%rsp)
        movq      %r8, 192(%rsp)
        movq      %r9, 200(%rsp)
        jmp       ..B1.91
..B1.90:
        movaps    112(%rsp), %xmm0
        movaps    128(%rsp), %xmm1
        movaps    144(%rsp), %xmm2
        movaps    %xmm0, 160(%rsp)
        movaps    %xmm1, 176(%rsp)
        movaps    %xmm2, 192(%rsp)
..B1.91:
        movzwl    168(%rsp), %eax
        andl      $32767, %eax
        cmpl      $383, %eax
        jl        ..B1.93
..B1.92:
        cmpl      $32383, %eax
        jle       ..B1.94
..B1.93:
        lea       160(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.34:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.35:
..B1.94:
        movl      192(%rsp), %edx
        testb     $1, %dl
        jne       ..B1.193
..B1.95:
        fldt      160(%rsp)
        lea       halfs(%rip), %rax
        fstpt     48(%rsp)
        fldt      176(%rsp)
        fldt      (%rax)
        fstpt     32(%rsp)
..B1.96:
        fldt      48(%rsp)
        fld       %st(0)
        fsqrt     
        fldt      16(%rsp)
        movq      328(%rsp), %r13
        fmul      %st(1), %st
        fld       %st(0)
        sarl      $1, %edx
        fsubr     %st(2), %st
        movl      %edx, 32(%r13)
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%r13)
        fldt      (%r13)
        fsubrp    %st, %st(2)
        movzwl    8(%rbp), %r9d
        movl      %r9d, %r11d
        andl      $32767, %r11d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%r13)
        cmpl      $8383, %r11d
        fstpt     48(%rsp)
        jl        ..B1.100
..B1.97:
        cmpl      $24383, %r11d
        jg        ..B1.100
..B1.98:
        movzwl    72(%rsp), %ecx
        movl      %ecx, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B1.100
..B1.99:
        cmpl      $24383, %r10d
        jle       ..B1.103
..B1.100:
        movq      %rbp, %rdi
..___tag_value___libm_carctrigprepare_k80.36:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.37:
..B1.101:
        lea       64(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.38:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.39:
..B1.102:
        movzwl    8(%rbp), %r9d
        movl      %r9d, %r11d
        movzwl    72(%rsp), %ecx
        movl      %ecx, %r10d
        andl      $32767, %r11d
        andl      $32767, %r10d
..B1.103:
        movl      32(%rbp), %eax
        movl      96(%rsp), %edx
        lea       (%r11,%rax), %r8d
        lea       -134(%rdx,%r10), %esi
        cmpl      %esi, %r8d
        jle       ..B1.120
..B1.104:
        lea       134(%rdx,%r10), %esi
        cmpl      %esi, %r8d
        jge       ..B1.120
..B1.105:
        cmpl      $0, 4(%rbp)
        jne       ..B1.107
..B1.106:
        cmpl      $0, (%rbp)
        je        ..B1.120
..B1.107:
        cmpl      $0, 68(%rsp)
        jne       ..B1.109
..B1.108:
        cmpl      $0, 64(%rsp)
        je        ..B1.120
..B1.109:
        cmpl      %edx, %eax
        je        ..B1.111
..B1.110:
        fldt      48(%rsp)
        subl      %edx, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      (%rbp)
        andl      $32767, %eax
        fldt      16(%rbp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %esi
        andl      $-32768, %esi
        orl       %eax, %esi
        movw      %si, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%rbp)
        fxch      %st(2)
        fstpt     (%rbp)
        fldt      (%rbp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        movzwl    8(%rbp), %r9d
        jmp       ..B1.112
..B1.111:
        fldt      (%rbp)
..B1.112:
        fldt      64(%rsp)
        fld       %st(1)
        movl      %ecx, %eax
        andl      $32767, %r9d
        andl      $32767, %eax
        fsub      %st(1), %st
        movl      %edx, 32(%r12)
        cmpl      %eax, %r9d
        fstpt     16(%r12)
        fldt      16(%r12)
        jg        ..B1.117
..B1.113:
        andl      $32767, %ecx
        cmpl      %ecx, %r9d
        jne       ..B1.118
..B1.114:
        movl      4(%rbp), %eax
        cmpl      68(%rsp), %eax
        ja        ..B1.117
..B1.115:
        jne       ..B1.118
..B1.116:
        movl      (%rbp), %eax
        cmpl      64(%rsp), %eax
        jbe       ..B1.118
..B1.117:
        fldt      16(%rbp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      80(%rsp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.119
..B1.118:
        fldt      80(%rsp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      16(%rbp)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
..B1.119:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%r12)
        fldt      (%r12)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%r12)
        jmp       ..B1.124
..B1.120:
        cmpq      $0, (%rbp)
        je        ..B1.122
..B1.121:
        lea       -16517(%rdx,%r10), %ecx
        lea       -16383(%rax,%r11), %eax
        cmpl      %eax, %ecx
        jl        ..B1.123
..B1.122:
        fldt      64(%rsp)
        fchs      
        movl      %edx, 32(%r12)
        fstpt     (%r12)
        fldt      80(%rsp)
        fchs      
        fstpt     16(%r12)
        jmp       ..B1.124
..B1.123:
        movq      (%rbp), %rax
        movq      %rax, (%r12)
        movq      8(%rbp), %rdx
        movq      %rdx, 8(%r12)
        movq      16(%rbp), %rcx
        movq      %rcx, 16(%r12)
        movq      24(%rbp), %rsi
        movq      %rsi, 24(%r12)
        movq      32(%rbp), %r8
        movq      %r8, 32(%r12)
        movq      40(%rbp), %rbp
        movq      %rbp, 40(%r12)
..B1.124:
        movzwl    8(%r12), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.126
..B1.125:
        cmpl      $24383, %eax
        jle       ..B1.128
..B1.126:
        movq      %r12, %rdi
..___tag_value___libm_carctrigprepare_k80.40:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.41:
..B1.127:
        movq      %r12, %rdi
..___tag_value___libm_carctrigprepare_k80.42:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.43:
..B1.128:
        fldt      (%r12)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%r12)
        fldt      16(%rsp)
        movl      32(%r12), %r9d
        fmul      %st(4), %st
        addl      %r9d, %r9d
        movl      %r9d, 144(%rsp)
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubrp    %st, %st(2)
        movzwl    120(%rsp), %ecx
        movl      %ecx, %r10d
        andl      $32767, %r10d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     128(%rsp)
        cmpl      $8383, %r10d
        fstpt     48(%rsp)
        jl        ..B1.132
..B1.129:
        cmpl      $24383, %r10d
        jg        ..B1.132
..B1.130:
        movzwl    8(%rbx), %ebp
        movl      %ebp, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B1.132
..B1.131:
        cmpl      $24383, %r8d
        jle       ..B1.135
..B1.132:
        lea       112(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.44:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.45:
..B1.133:
        movq      %rbx, %rdi
..___tag_value___libm_carctrigprepare_k80.46:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.47:
..B1.134:
        movzwl    120(%rsp), %ecx
        movl      %ecx, %r10d
        movzwl    8(%rbx), %ebp
        movl      %ebp, %r8d
        movl      144(%rsp), %r9d
        andl      $32767, %r10d
        andl      $32767, %r8d
..B1.135:
        movl      32(%rbx), %esi
        lea       (%r9,%r10), %edx
        lea       -134(%rsi,%r8), %eax
        cmpl      %eax, %edx
        jle       ..B1.152
..B1.136:
        lea       134(%rsi,%r8), %eax
        cmpl      %eax, %edx
        jge       ..B1.152
..B1.137:
        cmpl      $0, 116(%rsp)
        jne       ..B1.139
..B1.138:
        cmpl      $0, 112(%rsp)
        je        ..B1.152
..B1.139:
        cmpl      $0, 4(%rbx)
        jne       ..B1.141
..B1.140:
        cmpl      $0, (%rbx)
        je        ..B1.152
..B1.141:
        cmpl      %esi, %r9d
        je        ..B1.143
..B1.142:
        fldt      48(%rsp)
        subl      %esi, %r9d
        fstpt     (%rsp)
        addl      $16383, %r9d
        fldt      112(%rsp)
        andl      $32767, %r9d
        fldt      128(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %r9d, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %esi, 144(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        movzwl    120(%rsp), %ecx
        jmp       ..B1.144
..B1.143:
        fldt      112(%rsp)
        fldt      128(%rsp)
..B1.144:
        fldt      (%rbx)
        movl      %ecx, %eax
        andl      $32767, %ebp
        andl      $32767, %eax
        fld       %st(2)
        cmpl      %ebp, %eax
        movl      %esi, 240(%rsp)
        fadd      %st(1), %st
        jg        ..B1.149
..B1.145:
        andl      $32767, %ecx
        cmpl      %ebp, %ecx
        jne       ..B1.150
..B1.146:
        movl      4(%rbx), %eax
        cmpl      116(%rsp), %eax
        jb        ..B1.149
..B1.147:
        jne       ..B1.150
..B1.148:
        movl      112(%rsp), %eax
        cmpl      (%rbx), %eax
        jbe       ..B1.150
..B1.149:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%rbx)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.151
..B1.150:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%rbx)
        faddp     %st, %st(1)
..B1.151:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     224(%rsp)
        jmp       ..B1.156
..B1.152:
        cmpq      $0, 112(%rsp)
        je        ..B1.154
..B1.153:
        lea       -16517(%rsi,%r8), %eax
        lea       -16383(%r9,%r10), %edx
        cmpl      %edx, %eax
        jl        ..B1.155
..B1.154:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 208(%rsp)
        movq      %rdx, 216(%rsp)
        movq      %rcx, 224(%rsp)
        movq      %rbp, 232(%rsp)
        movq      %rsi, 240(%rsp)
        movq      %rbx, 248(%rsp)
        jmp       ..B1.156
..B1.155:
        movaps    112(%rsp), %xmm0
        movaps    128(%rsp), %xmm1
        movaps    144(%rsp), %xmm2
        movaps    %xmm0, 208(%rsp)
        movaps    %xmm1, 224(%rsp)
        movaps    %xmm2, 240(%rsp)
..B1.156:
        movzwl    216(%rsp), %eax
        andl      $32767, %eax
        cmpl      $383, %eax
        jl        ..B1.158
..B1.157:
        cmpl      $32383, %eax
        jle       ..B1.159
..B1.158:
        lea       208(%rsp), %rdi
..___tag_value___libm_carctrigprepare_k80.48:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.49:
..B1.159:
        movl      240(%rsp), %r11d
        testl     $1, %r11d
        jne       ..B1.192
..B1.160:
        fldt      208(%rsp)
        fstpt     48(%rsp)
        fldt      224(%rsp)
..B1.161:
        fldt      48(%rsp)
        fld       %st(0)
        fsqrt     
        fldt      16(%rsp)
        fmul      %st(1), %st
        fld       %st(0)
        movq      336(%rsp), %rbx
        fsubr     %st(2), %st
        sarl      $1, %r11d
        faddp     %st, %st(1)
        fld       %st(0)
        movl      %r11d, 32(%rbx)
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rbx)
        fldt      (%rbx)
        fsubrp    %st, %st(2)
        movzwl    8(%r13), %ebp
        movl      %ebp, %r10d
        andl      $32767, %r10d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rbx)
        cmpl      $8383, %r10d
        fstpt     48(%rsp)
        jl        ..B1.165
..B1.162:
        cmpl      $24383, %r10d
        jg        ..B1.165
..B1.163:
        movzwl    8(%rbx), %esi
        movl      %esi, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B1.165
..B1.164:
        cmpl      $24383, %r8d
        jle       ..B1.168
..B1.165:
        movq      %r13, %rdi
..___tag_value___libm_carctrigprepare_k80.50:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.51:
..B1.166:
        movq      %rbx, %rdi
..___tag_value___libm_carctrigprepare_k80.52:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_carctrigprepare_k80.53:
..B1.167:
        movzwl    8(%r13), %ebp
        movl      %ebp, %r10d
        movzwl    8(%rbx), %esi
        movl      %esi, %r8d
        movl      32(%rbx), %r11d
        andl      $32767, %r10d
        andl      $32767, %r8d
..B1.168:
        movl      32(%r13), %r9d
        lea       -134(%r11,%r8), %edx
        movq      344(%rsp), %rax
        lea       (%r10,%r9), %ecx
        cmpl      %edx, %ecx
        jle       ..B1.185
..B1.169:
        lea       134(%r11,%r8), %edx
        cmpl      %edx, %ecx
        jge       ..B1.185
..B1.170:
        cmpl      $0, 4(%r13)
        jne       ..B1.172
..B1.171:
        cmpl      $0, (%r13)
        je        ..B1.185
..B1.172:
        cmpl      $0, 4(%rbx)
        jne       ..B1.174
..B1.173:
        cmpl      $0, (%rbx)
        je        ..B1.185
..B1.174:
        cmpl      %r11d, %r9d
        je        ..B1.176
..B1.175:
        fldt      48(%rsp)
        subl      %r11d, %r9d
        fstpt     (%rsp)
        addl      $16383, %r9d
        fldt      (%r13)
        andl      $32767, %r9d
        fldt      16(%r13)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %edx
        andl      $-32768, %edx
        orl       %r9d, %edx
        movw      %dx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r11d, 32(%r13)
        fxch      %st(2)
        fstpt     (%r13)
        fldt      (%r13)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        movl      32(%rbx), %r11d
        movzwl    8(%rbx), %esi
        movzwl    8(%r13), %ebp
        jmp       ..B1.177
..B1.176:
        fldt      (%r13)
..B1.177:
        fldt      (%rbx)
        fld       %st(1)
        andl      $32767, %ebp
        andl      $32767, %esi
        movl      %r11d, 32(%rax)
        cmpl      %esi, %ebp
        fadd      %st(1), %st
        fstpt     16(%rax)
        fldt      16(%rax)
        jg        ..B1.182
..B1.178:
        jne       ..B1.183
..B1.179:
        movl      4(%r13), %edx
        cmpl      4(%rbx), %edx
        ja        ..B1.182
..B1.180:
        jne       ..B1.183
..B1.181:
        movl      (%r13), %edx
        cmpl      (%rbx), %edx
        jbe       ..B1.183
..B1.182:
        fldt      16(%rbx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      16(%r13)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B1.184
..B1.183:
        fldt      16(%r13)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%rbx)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B1.184:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rax)
        fldt      (%rax)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rax)
        jmp       ..B1.189
..B1.185:
        cmpq      $0, (%r13)
        je        ..B1.187
..B1.186:
        lea       -16517(%r11,%r8), %edx
        lea       -16383(%r9,%r10), %ecx
        cmpl      %ecx, %edx
        jl        ..B1.188
..B1.187:
        movq      (%rbx), %rdx
        movq      %rdx, (%rax)
        movq      8(%rbx), %rcx
        movq      %rcx, 8(%rax)
        movq      16(%rbx), %rbp
        movq      %rbp, 16(%rax)
        movq      24(%rbx), %rsi
        movq      %rsi, 24(%rax)
        movq      32(%rbx), %r8
        movq      %r8, 32(%rax)
        movq      40(%rbx), %rbx
        movq      %rbx, 40(%rax)
        jmp       ..B1.189
..B1.188:
        movq      (%r13), %rdx
        movq      %rdx, (%rax)
        movq      8(%r13), %rcx
        movq      %rcx, 8(%rax)
        movq      16(%r13), %rbx
        movq      %rbx, 16(%rax)
        movq      24(%r13), %rbp
        movq      %rbp, 24(%rax)
        movq      32(%r13), %rsi
        movq      %rsi, 32(%rax)
        movq      40(%r13), %rdi
        movq      %rdi, 40(%rax)
..B1.189:
        movq      256(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.191
..B1.190:
        addq      $272, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 320
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.191:
        call      __stack_chk_fail@PLT
..B1.192:
        fldt      208(%rsp)
        incl      %r11d
        fldt      32(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fstpt     208(%rsp)
        fldt      224(%rsp)
        fmulp     %st, %st(1)
        movl      %r11d, 240(%rsp)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        jmp       ..B1.161
..B1.193:
        fldt      160(%rsp)
        incl      %edx
        lea       halfs(%rip), %rax
        movl      %edx, 192(%rsp)
        fldt      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fstpt     160(%rsp)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        jmp       ..B1.96
        .align    16,0x90
	.cfi_endproc
	.type	__libm_carctrigprepare_k80,@function
	.size	__libm_carctrigprepare_k80,.-__libm_carctrigprepare_k80
	.data
# -- End  __libm_carctrigprepare_k80
	.text
# -- Begin  __libm_recacosl_k80
	.text
       .align    16,0x90
	.globl __libm_recacosl_k80
__libm_recacosl_k80:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 864 + %rsp
# parameter 8: 872 + %rsp
# parameter 9: 880 + %rsp
# parameter 10: 888 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___libm_recacosl_k80.72:
..L73:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $808, %rsp
	.cfi_def_cfa_offset 864
        movq      %rdx, %r12
        movq      %fs:40, %rax
        movq      %r9, %r15
        xorq      %rsp, %rax
        movq      %r8, %rbp
        movq      %rax, 800(%rsp)
        movq      %rdi, %r14
        movzwl    8(%r12), %eax
        andl      $32767, %eax
        movq      888(%rsp), %r13
        cmpl      $8383, %eax
        jl        ..B2.5
..B2.2:
        cmpl      $24383, %eax
        jg        ..B2.5
..B2.3:
        movzwl    8(%r13), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.5
..B2.4:
        cmpl      $24383, %eax
        jle       ..B2.7
..B2.5:
        movq      %r12, %rdi
..___tag_value___libm_recacosl_k80.87:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.88:
..B2.6:
        movq      %r13, %rdi
..___tag_value___libm_recacosl_k80.89:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.90:
..B2.7:
        fldt      (%r12)
        lea       t32(%rip), %rax
        fld       %st(0)
        fldt      (%r13)
        fdivr     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fldt      (%rax)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(0)
        movl      32(%r12), %ebx
        fsubr     %st(6), %st
        subl      32(%r13), %ebx
        faddp     %st, %st(1)
        fld       %st(0)
        lea       1(%rbx), %edx
        movl      %edx, 784(%rsp)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fsub      %st(4), %st
        fxch      %st(3)
        fstpt     48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      16(%r12)
        faddp     %st, %st(3)
        fldt      16(%r13)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     752(%rsp)
        fldt      752(%rsp)
        fsubrp    %st, %st(1)
        movzwl    760(%rsp), %ecx
        fadd      %st(1), %st
        andl      $32767, %ecx
        fstpt     768(%rsp)
        fstpt     16(%rsp)
        lea       1(%rcx,%rbx), %esi
        cmpl      $16382, %esi
        jl        ..B2.192
..B2.8:
        movzwl    8(%r14), %eax
        andl      $32767, %eax
        movq      880(%rsp), %rbx
        cmpl      $16383, %eax
        jl        ..B2.37
..B2.9:
        movq      864(%rsp), %r9
        movq      (%r9), %rax
        movq      8(%r9), %rdx
        movq      16(%r9), %rcx
        movq      24(%r9), %rsi
        movq      32(%r9), %r8
        movq      40(%r9), %r10
        movzwl    8(%rbx), %r9d
        movq      %rax, 416(%rsp)
        movl      %r9d, %eax
        andl      $32767, %eax
        movq      %rdx, 424(%rsp)
        cmpl      $8383, %eax
        movq      %rcx, 432(%rsp)
        movq      %rsi, 440(%rsp)
        movq      %r8, 448(%rsp)
        movq      %r10, 456(%rsp)
        jl        ..B2.13
..B2.10:
        cmpl      $24383, %eax
        jg        ..B2.13
..B2.11:
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B2.13
..B2.12:
        cmpl      $24383, %r11d
        jle       ..B2.16
..B2.13:
        movq      %rbx, %rdi
..___tag_value___libm_recacosl_k80.91:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.92:
..B2.14:
        movq      %rbp, %rdi
..___tag_value___libm_recacosl_k80.93:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.94:
..B2.15:
        movzwl    8(%rbx), %r9d
        movl      %r9d, %eax
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B2.16:
        movl      32(%rbp), %edx
        movl      32(%rbx), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B2.33
..B2.17:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B2.33
..B2.18:
        cmpl      $0, 4(%rbx)
        jne       ..B2.20
..B2.19:
        cmpl      $0, (%rbx)
        je        ..B2.33
..B2.20:
        cmpl      $0, 4(%rbp)
        jne       ..B2.22
..B2.21:
        cmpl      $0, (%rbp)
        je        ..B2.33
..B2.22:
        cmpl      %edx, %ecx
        je        ..B2.24
..B2.23:
        fldt      16(%rsp)
        subl      %edx, %ecx
        fstpt     (%rsp)
        addl      $16383, %ecx
        fldt      (%rbx)
        andl      $32767, %ecx
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        fxch      %st(1)
        fstpt     16(%rsp)
        movl      32(%rbp), %edx
        movzwl    8(%rbp), %r10d
        movzwl    8(%rbx), %r9d
        jmp       ..B2.25
..B2.24:
        fldt      (%rbx)
        fstpt     16(%rsp)
        fldt      16(%rbx)
..B2.25:
        fldt      (%rbp)
        andl      $32767, %r9d
        fldt      16(%rsp)
        andl      $32767, %r10d
        movl      %edx, 496(%rsp)
        cmpl      %r10d, %r9d
        fadd      %st(1), %st
        jg        ..B2.30
..B2.26:
        jne       ..B2.31
..B2.27:
        movl      4(%rbx), %eax
        cmpl      4(%rbp), %eax
        ja        ..B2.30
..B2.28:
        jne       ..B2.31
..B2.29:
        movl      (%rbx), %eax
        cmpl      (%rbp), %eax
        jbe       ..B2.31
..B2.30:
        fldt      16(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      16(%rbp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B2.32
..B2.31:
        fldt      16(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      16(%rbp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B2.32:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     464(%rsp)
        fldt      464(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     480(%rsp)
        jmp       ..B2.65
..B2.33:
        cmpq      $0, (%rbx)
        je        ..B2.35
..B2.34:
        lea       -16517(%rdx,%r11), %edx
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %edx
        jl        ..B2.36
..B2.35:
        movq      (%rbp), %rax
        movq      8(%rbp), %rdx
        movq      16(%rbp), %rcx
        movq      24(%rbp), %rbx
        movq      32(%rbp), %rsi
        movq      40(%rbp), %rbp
        movq      %rax, 464(%rsp)
        movq      %rdx, 472(%rsp)
        movq      %rcx, 480(%rsp)
        movq      %rbx, 488(%rsp)
        movq      %rsi, 496(%rsp)
        movq      %rbp, 504(%rsp)
        jmp       ..B2.65
..B2.36:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 464(%rsp)
        movq      %rdx, 472(%rsp)
        movq      %rcx, 480(%rsp)
        movq      %rbp, 488(%rsp)
        movq      %rsi, 496(%rsp)
        movq      %rbx, 504(%rsp)
        jmp       ..B2.65
..B2.37:
        lea       ones(%rip), %rax
        lea       zeros(%rip), %rdx
        movzwl    8(%rbx), %r9d
        movl      $0, 496(%rsp)
        fldt      (%rax)
        movl      %r9d, %eax
        andl      $32767, %eax
        fstpt     464(%rsp)
        cmpl      $8383, %eax
        fldt      (%rdx)
        fstpt     480(%rsp)
        jl        ..B2.41
..B2.38:
        cmpl      $24383, %eax
        jg        ..B2.41
..B2.39:
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B2.41
..B2.40:
        cmpl      $24383, %r11d
        jle       ..B2.44
..B2.41:
        movq      %rbx, %rdi
..___tag_value___libm_recacosl_k80.95:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.96:
..B2.42:
        movq      %rbp, %rdi
..___tag_value___libm_recacosl_k80.97:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.98:
..B2.43:
        movzwl    8(%rbx), %r9d
        movl      %r9d, %eax
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B2.44:
        movl      32(%rbp), %edx
        movl      32(%rbx), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B2.61
..B2.45:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B2.61
..B2.46:
        cmpl      $0, 4(%rbx)
        jne       ..B2.48
..B2.47:
        cmpl      $0, (%rbx)
        je        ..B2.61
..B2.48:
        cmpl      $0, 4(%rbp)
        jne       ..B2.50
..B2.49:
        cmpl      $0, (%rbp)
        je        ..B2.61
..B2.50:
        cmpl      %edx, %ecx
        je        ..B2.52
..B2.51:
        fldt      16(%rsp)
        subl      %edx, %ecx
        fstpt     (%rsp)
        addl      $16383, %ecx
        fldt      (%rbx)
        andl      $32767, %ecx
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        fstpt     16(%rsp)
        movl      32(%rbp), %edx
        movzwl    8(%rbp), %r10d
        movzwl    8(%rbx), %r9d
        jmp       ..B2.53
..B2.52:
        fldt      (%rbx)
        fldt      16(%rbx)
        fstpt     16(%rsp)
..B2.53:
        fldt      (%rbp)
        andl      $32767, %r9d
        andl      $32767, %r10d
        fld       %st(1)
        movl      %edx, 448(%rsp)
        cmpl      %r10d, %r9d
        fsub      %st(1), %st
        jg        ..B2.58
..B2.54:
        jne       ..B2.59
..B2.55:
        movl      4(%rbx), %eax
        cmpl      4(%rbp), %eax
        ja        ..B2.58
..B2.56:
        jne       ..B2.59
..B2.57:
        movl      (%rbx), %eax
        cmpl      (%rbp), %eax
        jbe       ..B2.59
..B2.58:
        fldt      16(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      16(%rbp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        jmp       ..B2.60
..B2.59:
        fldt      16(%rbp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      16(%rsp)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
..B2.60:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     432(%rsp)
        jmp       ..B2.65
..B2.61:
        cmpq      $0, (%rbx)
        je        ..B2.63
..B2.62:
        lea       -16517(%rdx,%r11), %esi
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %esi
        jl        ..B2.64
..B2.63:
        fldt      (%rbp)
        fchs      
        movl      %edx, 448(%rsp)
        fstpt     416(%rsp)
        fldt      16(%rbp)
        fchs      
        fstpt     432(%rsp)
        jmp       ..B2.65
..B2.64:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 416(%rsp)
        movq      %rdx, 424(%rsp)
        movq      %rcx, 432(%rsp)
        movq      %rbp, 440(%rsp)
        movq      %rsi, 448(%rsp)
        movq      %rbx, 456(%rsp)
..B2.65:
        movzwl    8(%r12), %ebp
        movl      %ebp, %r9d
        andl      $32767, %r9d
        movl      32(%r12), %edx
        incl      %edx
        movl      %edx, 32(%r12)
        cmpl      $8383, %r9d
        jl        ..B2.69
..B2.66:
        cmpl      $24383, %r9d
        jg        ..B2.69
..B2.67:
        movzwl    8(%r13), %eax
        movl      %eax, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B2.69
..B2.68:
        cmpl      $24383, %r8d
        jle       ..B2.72
..B2.69:
        movq      %r12, %rdi
..___tag_value___libm_recacosl_k80.99:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.100:
..B2.70:
        movq      %r13, %rdi
..___tag_value___libm_recacosl_k80.101:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.102:
..B2.71:
        movzwl    8(%r12), %ebp
        movl      %ebp, %r9d
        movzwl    8(%r13), %eax
        movl      %eax, %r8d
        movl      32(%r12), %edx
        andl      $32767, %r9d
        andl      $32767, %r8d
..B2.72:
        movl      32(%r13), %esi
        lea       (%rdx,%r9), %ebx
        lea       -134(%rsi,%r8), %ecx
        cmpl      %ecx, %ebx
        jle       ..B2.89
..B2.73:
        lea       134(%rsi,%r8), %ecx
        cmpl      %ecx, %ebx
        jge       ..B2.89
..B2.74:
        cmpl      $0, 4(%r12)
        jne       ..B2.76
..B2.75:
        cmpl      $0, (%r12)
        je        ..B2.89
..B2.76:
        cmpl      $0, 4(%r13)
        jne       ..B2.78
..B2.77:
        cmpl      $0, (%r13)
        je        ..B2.89
..B2.78:
        cmpl      %esi, %edx
        je        ..B2.80
..B2.79:
        fldt      16(%rsp)
        subl      %esi, %edx
        fstpt     (%rsp)
        addl      $16383, %edx
        fldt      (%r12)
        andl      $32767, %edx
        fldt      16(%r12)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edx, %eax
        movl      %esi, %edx
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %esi, 32(%r12)
        fxch      %st(2)
        fstpt     (%r12)
        fldt      (%r12)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r12)
        fldt      16(%r12)
        movl      32(%r13), %esi
        movzwl    8(%r13), %eax
        movzwl    8(%r12), %ebp
        jmp       ..B2.81
..B2.80:
        fldt      (%r12)
        fldt      16(%r12)
..B2.81:
        fldt      (%r13)
        movl      %eax, %ecx
        andl      $32767, %ebp
        andl      $32767, %ecx
        fld       %st(2)
        cmpl      %ecx, %ebp
        movl      %esi, 544(%rsp)
        fadd      %st(1), %st
        jg        ..B2.86
..B2.82:
        jne       ..B2.87
..B2.83:
        movl      4(%r12), %ecx
        cmpl      4(%r13), %ecx
        ja        ..B2.86
..B2.84:
        jne       ..B2.87
..B2.85:
        movl      (%r12), %ecx
        cmpl      (%r13), %ecx
        jbe       ..B2.87
..B2.86:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      16(%r13)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B2.88
..B2.87:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%r13)
        faddp     %st, %st(2)
..B2.88:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     512(%rsp)
        fldt      512(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     528(%rsp)
        jmp       ..B2.93
..B2.89:
        cmpq      $0, (%r12)
        je        ..B2.91
..B2.90:
        lea       -16517(%rsi,%r8), %ecx
        lea       -16383(%rdx,%r9), %ebx
        cmpl      %ebx, %ecx
        jl        ..B2.92
..B2.91:
        movq      (%r13), %rcx
        movq      8(%r13), %rbx
        movq      16(%r13), %rbp
        movq      24(%r13), %rsi
        movq      32(%r13), %r8
        movq      40(%r13), %r9
        movq      %rcx, 512(%rsp)
        movq      %rbx, 520(%rsp)
        movq      %rbp, 528(%rsp)
        movq      %rsi, 536(%rsp)
        movq      %r8, 544(%rsp)
        movq      %r9, 552(%rsp)
        jmp       ..B2.93
..B2.92:
        movq      (%r12), %rcx
        movq      8(%r12), %rbx
        movq      16(%r12), %rbp
        movq      24(%r12), %rsi
        movq      32(%r12), %r8
        movq      40(%r12), %r9
        movq      %rcx, 512(%rsp)
        movq      %rbx, 520(%rsp)
        movq      %rbp, 528(%rsp)
        movq      %rsi, 536(%rsp)
        movq      %r8, 544(%rsp)
        movq      %r9, 552(%rsp)
..B2.93:
        decl      %edx
        movl      %edx, 32(%r12)
        movzwl    520(%rsp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B2.97
..B2.94:
        cmpl      $24383, %edx
        jg        ..B2.97
..B2.95:
        movzwl    424(%rsp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B2.97
..B2.96:
        cmpl      $24383, %edx
        jle       ..B2.99
..B2.97:
        lea       512(%rsp), %rdi
..___tag_value___libm_recacosl_k80.103:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.104:
..B2.98:
        lea       416(%rsp), %rdi
..___tag_value___libm_recacosl_k80.105:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.106:
        movzwl    8(%r13), %eax
..B2.99:
        fldt      512(%rsp)
        andl      $32767, %eax
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      544(%rsp), %edx
        fsubr     %st, %st(3)
        addl      448(%rsp), %edx
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %eax
        movl      %edx, 592(%rsp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      416(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      432(%rsp)
        fmulp     %st, %st(4)
        fldt      528(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     560(%rsp)
        fldt      560(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     576(%rsp)
        jl        ..B2.103
..B2.100:
        cmpl      $24383, %eax
        jg        ..B2.103
..B2.101:
        movzwl    472(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.103
..B2.102:
        cmpl      $24383, %eax
        jle       ..B2.105
..B2.103:
        movq      %r13, %rdi
..___tag_value___libm_recacosl_k80.107:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.108:
..B2.104:
        lea       464(%rsp), %rdi
..___tag_value___libm_recacosl_k80.109:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.110:
..B2.105:
        fldt      (%r13)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movq      872(%rsp), %rbx
        fsubr     %st, %st(3)
        movzwl    8(%rbx), %ecx
        movl      %ecx, %r10d
        movl      496(%rsp), %eax
        andl      $32767, %r10d
        addl      32(%r13), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %r10d
        movl      %eax, 640(%rsp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      464(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      480(%rsp)
        fmulp     %st, %st(4)
        fldt      16(%r13)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     608(%rsp)
        fldt      608(%rsp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     624(%rsp)
        fstpt     16(%rsp)
        jl        ..B2.109
..B2.106:
        cmpl      $24383, %r10d
        jg        ..B2.109
..B2.107:
        movzwl    8(%r15), %ebp
        movl      %ebp, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B2.109
..B2.108:
        cmpl      $24383, %r8d
        jle       ..B2.112
..B2.109:
        movq      %rbx, %rdi
..___tag_value___libm_recacosl_k80.111:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.112:
..B2.110:
        movq      %r15, %rdi
..___tag_value___libm_recacosl_k80.113:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.114:
..B2.111:
        movzwl    8(%rbx), %ecx
        movl      %ecx, %r10d
        movzwl    8(%r15), %ebp
        movl      %ebp, %r8d
        andl      $32767, %r10d
        andl      $32767, %r8d
..B2.112:
        movl      32(%r15), %esi
        movl      32(%rbx), %r9d
        lea       -134(%rsi,%r8), %eax
        lea       (%r10,%r9), %edx
        cmpl      %eax, %edx
        jle       ..B2.129
..B2.113:
        lea       134(%rsi,%r8), %eax
        cmpl      %eax, %edx
        jge       ..B2.129
..B2.114:
        cmpl      $0, 4(%rbx)
        jne       ..B2.116
..B2.115:
        cmpl      $0, (%rbx)
        je        ..B2.129
..B2.116:
        cmpl      $0, 4(%r15)
        jne       ..B2.118
..B2.117:
        cmpl      $0, (%r15)
        je        ..B2.129
..B2.118:
        cmpl      %esi, %r9d
        je        ..B2.120
..B2.119:
        fldt      16(%rsp)
        subl      %esi, %r9d
        fstpt     (%rsp)
        addl      $16383, %r9d
        fldt      (%rbx)
        andl      $32767, %r9d
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %r9d, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %esi, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        movl      32(%r15), %esi
        movzwl    8(%r15), %ebp
        movzwl    8(%rbx), %ecx
        jmp       ..B2.121
..B2.120:
        fldt      (%rbx)
        fldt      16(%rbx)
..B2.121:
        fldt      (%r15)
        andl      $32767, %ecx
        andl      $32767, %ebp
        fld       %st(2)
        movl      %esi, 688(%rsp)
        cmpl      %ebp, %ecx
        fadd      %st(1), %st
        jg        ..B2.126
..B2.122:
        jne       ..B2.127
..B2.123:
        movl      4(%rbx), %eax
        cmpl      4(%r15), %eax
        ja        ..B2.126
..B2.124:
        jne       ..B2.127
..B2.125:
        movl      (%rbx), %eax
        cmpl      (%r15), %eax
        jbe       ..B2.127
..B2.126:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%r15)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.128
..B2.127:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%r15)
        faddp     %st, %st(1)
..B2.128:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     656(%rsp)
        fldt      656(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     672(%rsp)
        jmp       ..B2.133
..B2.129:
        cmpq      $0, (%rbx)
        je        ..B2.131
..B2.130:
        lea       -16517(%rsi,%r8), %eax
        lea       -16383(%r9,%r10), %edx
        cmpl      %edx, %eax
        jl        ..B2.132
..B2.131:
        movq      (%r15), %rax
        movq      8(%r15), %rdx
        movq      16(%r15), %rcx
        movq      24(%r15), %rbx
        movq      32(%r15), %rbp
        movq      40(%r15), %rsi
        movq      %rax, 656(%rsp)
        movq      %rdx, 664(%rsp)
        movq      %rcx, 672(%rsp)
        movq      %rbx, 680(%rsp)
        movq      %rbp, 688(%rsp)
        movq      %rsi, 696(%rsp)
        jmp       ..B2.133
..B2.132:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 656(%rsp)
        movq      %rdx, 664(%rsp)
        movq      %rcx, 672(%rsp)
        movq      %rbp, 680(%rsp)
        movq      %rsi, 688(%rsp)
        movq      %rbx, 696(%rsp)
..B2.133:
        movzwl    616(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.137
..B2.134:
        cmpl      $24383, %eax
        jg        ..B2.137
..B2.135:
        movzwl    664(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.137
..B2.136:
        cmpl      $24383, %eax
        jle       ..B2.139
..B2.137:
        lea       608(%rsp), %rdi
..___tag_value___libm_recacosl_k80.115:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.116:
..B2.138:
        lea       656(%rsp), %rdi
..___tag_value___libm_recacosl_k80.117:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.118:
..B2.139:
        fldt      608(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      640(%rsp), %edx
        fsubr     %st, %st(3)
        movzwl    568(%rsp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      688(%rsp), %edx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      656(%rsp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %edx, 736(%rsp)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      672(%rsp)
        fmulp     %st, %st(4)
        fldt      624(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     704(%rsp)
        fldt      704(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     720(%rsp)
        fldt      720(%rsp)
        jl        ..B2.143
..B2.140:
        cmpl      $24383, %eax
        jg        ..B2.143
..B2.141:
        movzwl    712(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.143
..B2.142:
        cmpl      $24383, %eax
        jle       ..B2.146
..B2.143:
        fstp      %st(1)
        fstp      %st(0)
        lea       560(%rsp), %rdi
..___tag_value___libm_recacosl_k80.119:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.120:
..B2.144:
        lea       704(%rsp), %rdi
..___tag_value___libm_recacosl_k80.121:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.122:
..B2.145:
        fldt      704(%rsp)
        fldt      720(%rsp)
        movl      736(%rsp), %edx
..B2.146:
        fldt      560(%rsp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      592(%rsp), %ecx
        fsubr     %st(4), %st
        subl      %edx, %ecx
        faddp     %st, %st(1)
        fld       %st(0)
        testb     $-128, 9(%r14)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(3), %st
        fsubr     %st, %st(7)
        faddp     %st, %st(7)
        fxch      %st(2)
        fsub      %st(6), %st
        fld       %st(2)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(7)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      576(%rsp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      32(%rsp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fstpt     16(%rsp)
        jne       ..B2.155
..B2.147:
        fstpt     320(%rsp)
        fldt      320(%rsp)
        decl      %ecx
        movzwl    328(%rsp), %eax
        andl      $32767, %eax
        movl      %ecx, 352(%rsp)
        cmpl      $383, %eax
        fxch      %st(1)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        jl        ..B2.149
..B2.148:
        cmpl      $32383, %eax
        jle       ..B2.151
..B2.149:
        fstp      %st(0)
        fstp      %st(0)
        lea       320(%rsp), %rdi
..___tag_value___libm_recacosl_k80.123:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.124:
..B2.150:
        fldt      320(%rsp)
        fldt      336(%rsp)
        movl      352(%rsp), %ecx
..B2.151:
        testb     $1, %cl
        jne       ..B2.226
..B2.152:
        lea       halfs(%rip), %rax
        fldt      (%rax)
..B2.153:
        fldt      48(%rsp)
        fld       %st(3)
        fsqrt     
        lea       224(%rsp), %rdi
        fmul      %st, %st(1)
        lea       368(%rsp), %rsi
        fld       %st(1)
        sarl      $1, %ecx
        fsubr     %st(1), %st
        movl      %ecx, 32(%rsi)
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rsi)
        fldt      (%rsi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsi)
..___tag_value___libm_recacosl_k80.125:
        call      asinpoly
..___tag_value___libm_recacosl_k80.126:
..B2.154:
        fldt      224(%rsp)
        fldt      240(%rsp)
        movl      256(%rsp), %edi
        incl      %edi
        movl      %edi, 256(%rsp)
        jmp       ..B2.223
..B2.155:
        fstpt     128(%rsp)
        fldt      128(%rsp)
        decl      %ecx
        movzwl    136(%rsp), %eax
        andl      $32767, %eax
        movl      %ecx, 160(%rsp)
        cmpl      $383, %eax
        fxch      %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        jl        ..B2.157
..B2.156:
        cmpl      $32383, %eax
        jle       ..B2.159
..B2.157:
        fstp      %st(0)
        fstp      %st(0)
        lea       128(%rsp), %rdi
..___tag_value___libm_recacosl_k80.127:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.128:
..B2.158:
        fldt      128(%rsp)
        fldt      144(%rsp)
        movl      160(%rsp), %ecx
..B2.159:
        testb     $1, %cl
        jne       ..B2.227
..B2.160:
        lea       halfs(%rip), %rax
        fldt      (%rax)
..B2.161:
        fldt      48(%rsp)
        fld       %st(3)
        fsqrt     
        lea       80(%rsp), %rdi
        fmul      %st, %st(1)
        lea       176(%rsp), %rsi
        fld       %st(1)
        sarl      $1, %ecx
        fsubr     %st(1), %st
        movl      %ecx, 32(%rsi)
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rsi)
        fldt      (%rsi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsi)
..___tag_value___libm_recacosl_k80.129:
        call      asinpoly
..___tag_value___libm_recacosl_k80.130:
..B2.162:
        lea       16+_PIO2_K80(%rip), %rax
        lea       32+_PIO2_K80(%rip), %rdx
        movl      112(%rsp), %edi
        movl      $1, %ebp
        incl      %edi
        movl      %edi, 112(%rsp)
        fldt      (%rax)
        movzwl    8(%rax), %esi
        andl      $32767, %esi
        movl      $1, 304(%rsp)
        cmpl      $8383, %esi
        fstpt     272(%rsp)
        fldt      (%rdx)
        fstpt     288(%rsp)
        jl        ..B2.166
..B2.163:
        cmpl      $24383, %esi
        jg        ..B2.166
..B2.164:
        movzwl    88(%rsp), %ebx
        andl      $32767, %ebx
        cmpl      $8383, %ebx
        jl        ..B2.166
..B2.165:
        cmpl      $24383, %ebx
        jle       ..B2.169
..B2.166:
        lea       272(%rsp), %rdi
..___tag_value___libm_recacosl_k80.131:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.132:
..B2.167:
        lea       80(%rsp), %rdi
..___tag_value___libm_recacosl_k80.133:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.134:
..B2.168:
        movzwl    280(%rsp), %esi
        movzwl    88(%rsp), %ebx
        andl      $32767, %esi
        movl      112(%rsp), %edi
        andl      $32767, %ebx
        movl      304(%rsp), %ebp
..B2.169:
        lea       (%rbp,%rsi), %edx
        lea       -134(%rdi,%rbx), %eax
        cmpl      %eax, %edx
        jle       ..B2.186
..B2.170:
        movl      276(%rsp), %ecx
        lea       134(%rdi,%rbx), %eax
        cmpl      %eax, %edx
        jge       ..B2.187
..B2.171:
        testl     %ecx, %ecx
        jne       ..B2.173
..B2.172:
        cmpl      $0, 272(%rsp)
        je        ..B2.188
..B2.173:
        movl      84(%rsp), %edx
        testl     %edx, %edx
        jne       ..B2.175
..B2.174:
        cmpl      $0, 80(%rsp)
        je        ..B2.187
..B2.175:
        cmpl      %edi, %ebp
        je        ..B2.177
..B2.176:
        fldt      16(%rsp)
        subl      %edi, %ebp
        fstpt     (%rsp)
        addl      $16383, %ebp
        fldt      272(%rsp)
        andl      $32767, %ebp
        fldt      288(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ebp, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    280(%rsp), %esi
        movl      %edi, 304(%rsp)
        andl      $32767, %esi
        fstpt     288(%rsp)
        fldt      288(%rsp)
        jmp       ..B2.178
..B2.177:
        fldt      272(%rsp)
        fldt      288(%rsp)
..B2.178:
        fldt      80(%rsp)
        fld       %st(2)
        movl      %edi, 256(%rsp)
        cmpl      %ebx, %esi
        fsub      %st(1), %st
        jg        ..B2.183
..B2.179:
        jne       ..B2.184
..B2.180:
        movl      276(%rsp), %eax
        cmpl      %edx, %eax
        ja        ..B2.183
..B2.181:
        jne       ..B2.184
..B2.182:
        movl      272(%rsp), %eax
        cmpl      80(%rsp), %eax
        jbe       ..B2.184
..B2.183:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      96(%rsp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B2.185
..B2.184:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B2.185:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        jmp       ..B2.223
..B2.186:
        movl      276(%rsp), %ecx
..B2.187:
        testl     %ecx, %ecx
        jne       ..B2.189
..B2.232:
        cmpl      $0, 272(%rsp)
..B2.188:
        je        ..B2.190
..B2.189:
        lea       -16517(%rdi,%rbx), %eax
        lea       -16383(%rbp,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B2.191
..B2.190:
        fldt      80(%rsp)
        fchs      
        movl      %edi, 256(%rsp)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fldt      96(%rsp)
        fchs      
        fstpt     240(%rsp)
        fldt      240(%rsp)
        jmp       ..B2.223
..B2.191:
        fldt      272(%rsp)
        fldt      288(%rsp)
        movaps    272(%rsp), %xmm0
        movaps    288(%rsp), %xmm1
        movaps    304(%rsp), %xmm2
        movl      304(%rsp), %edi
        movaps    %xmm0, 224(%rsp)
        movaps    %xmm1, 240(%rsp)
        movaps    %xmm2, 256(%rsp)
        jmp       ..B2.223
..B2.192:
        lea       16+_PIO2_K80(%rip), %rax
        lea       32+_PIO2_K80(%rip), %rdx
        movl      $0, 64(%rsp)
        lea       80(%rsp), %rdi
        lea       752(%rsp), %rsi
        fldt      (%rax)
        fstpt     -48(%rdi)
        fldt      (%rdx)
        fstpt     -32(%rdi)
..___tag_value___libm_recacosl_k80.135:
        call      asinpoly
..___tag_value___libm_recacosl_k80.136:
..B2.193:
        fldt      80(%rsp)
        lea       ones(%rip), %rcx
        fldt      96(%rsp)
        movb      9(%r14), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        shlq      $4, %rdx
        movzwl    40(%rsp), %ebp
        andl      $32767, %ebp
        cmpl      $8383, %ebp
        fldt      (%rdx,%rcx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     80(%rsp)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        jl        ..B2.197
..B2.194:
        cmpl      $24383, %ebp
        jg        ..B2.197
..B2.195:
        movzwl    88(%rsp), %ebx
        andl      $32767, %ebx
        cmpl      $8383, %ebx
        jl        ..B2.197
..B2.196:
        cmpl      $24383, %ebx
        jle       ..B2.200
..B2.197:
        lea       32(%rsp), %rdi
..___tag_value___libm_recacosl_k80.137:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.138:
..B2.198:
        lea       80(%rsp), %rdi
..___tag_value___libm_recacosl_k80.139:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacosl_k80.140:
..B2.199:
        movzwl    88(%rsp), %ebx
        movzwl    40(%rsp), %ebp
        andl      $32767, %ebx
        andl      $32767, %ebp
..B2.200:
        movl      112(%rsp), %edi
        movl      64(%rsp), %esi
        lea       -134(%rdi,%rbx), %eax
        lea       (%rbp,%rsi), %edx
        cmpl      %eax, %edx
        jle       ..B2.217
..B2.201:
        movl      36(%rsp), %ecx
        lea       134(%rdi,%rbx), %eax
        cmpl      %eax, %edx
        jge       ..B2.218
..B2.202:
        testl     %ecx, %ecx
        jne       ..B2.204
..B2.203:
        cmpl      $0, 32(%rsp)
        je        ..B2.219
..B2.204:
        movl      84(%rsp), %edx
        testl     %edx, %edx
        jne       ..B2.206
..B2.205:
        cmpl      $0, 80(%rsp)
        je        ..B2.218
..B2.206:
        cmpl      %edi, %esi
        je        ..B2.208
..B2.207:
        fldt      16(%rsp)
        subl      %edi, %esi
        fstpt     (%rsp)
        addl      $16383, %esi
        fldt      32(%rsp)
        andl      $32767, %esi
        fldt      48(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %esi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    40(%rsp), %ebp
        movl      %edi, 64(%rsp)
        andl      $32767, %ebp
        fstpt     48(%rsp)
        fldt      48(%rsp)
        jmp       ..B2.209
..B2.208:
        fldt      32(%rsp)
        fldt      48(%rsp)
..B2.209:
        fldt      80(%rsp)
        fld       %st(2)
        movl      %edi, 256(%rsp)
        cmpl      %ebx, %ebp
        fsub      %st(1), %st
        jg        ..B2.214
..B2.210:
        jne       ..B2.215
..B2.211:
        movl      36(%rsp), %eax
        cmpl      %edx, %eax
        ja        ..B2.214
..B2.212:
        jne       ..B2.215
..B2.213:
        movl      32(%rsp), %eax
        cmpl      80(%rsp), %eax
        jbe       ..B2.215
..B2.214:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      96(%rsp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B2.216
..B2.215:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B2.216:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        jmp       ..B2.223
..B2.217:
        movl      36(%rsp), %ecx
..B2.218:
        testl     %ecx, %ecx
        jne       ..B2.220
..B2.233:
        cmpl      $0, 32(%rsp)
..B2.219:
        je        ..B2.190
..B2.220:
        lea       -16517(%rdi,%rbx), %eax
        lea       -16383(%rsi,%rbp), %edx
        cmpl      %edx, %eax
        jge       ..B2.190
..B2.222:
        fldt      32(%rsp)
        movl      %esi, %edi
        fldt      48(%rsp)
        movaps    32(%rsp), %xmm0
        movaps    48(%rsp), %xmm1
        movaps    64(%rsp), %xmm2
        movaps    %xmm0, 224(%rsp)
        movaps    %xmm1, 240(%rsp)
        movaps    %xmm2, 256(%rsp)
..B2.223:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 896
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_recacosl_k80.142:
        call      __libm_scalbl_k80@PLT
..___tag_value___libm_recacosl_k80.143:
..B2.231:
        addq      $32, %rsp
	.cfi_def_cfa_offset 864
..B2.224:
        movq      800(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.228
..B2.225:
        addq      $808, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 864
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.226:
        lea       halfs(%rip), %rax
        incl      %ecx
        movl      %ecx, 352(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     320(%rsp)
        fldt      320(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B2.153
..B2.227:
        lea       halfs(%rip), %rax
        incl      %ecx
        movl      %ecx, 160(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B2.161
..B2.228:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__libm_recacosl_k80,@function
	.size	__libm_recacosl_k80,.-__libm_recacosl_k80
	.data
# -- End  __libm_recacosl_k80
	.text
# -- Begin  asinpoly
	.text
       .align    16,0x90
asinpoly:
# parameter 1: %rdi
# parameter 2: %rsi
..B3.1:
	.cfi_startproc
..___tag_value_asinpoly.166:
..L167:

        subq      $184, %rsp
	.cfi_def_cfa_offset 192
        lea       ones(%rip), %rax
        movl      32(%rsi), %r8d
        movzwl    8(%rsi), %ecx
        andl      $32767, %ecx
        fldt      (%rax)
        lea       16383(%r8), %edx
        movzwl    8(%rax), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        addl      %ecx, %r8d
        orl       %edx, %eax
        fstpt     112(%rsp)
        cmpl      $16343, %r8d
        jl        ..B3.38
..B3.2:
        fldt      (%rsi)
        lea       240+_ASIN_POLY(%rip), %rdx
        fldt      16(%rsi)
        lea       208+_ASIN_POLY(%rip), %rcx
        movw      %ax, 120(%rsp)
        lea       272+_ASIN_POLY(%rip), %rax
        fldt      112(%rsp)
        lea       176+_ASIN_POLY(%rip), %rsi
        fmul      %st, %st(2)
        lea       144+_ASIN_POLY(%rip), %r8
        fld       %st(2)
        lea       112+_ASIN_POLY(%rip), %r9
        fmul      %st(3), %st
        lea       80+_ASIN_POLY(%rip), %r10
        fld       %st(0)
        lea       256+_ASIN_POLY(%rip), %r11
        fmul      %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(3)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      (%rax)
        lea       224+_ASIN_POLY(%rip), %rax
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       192+_ASIN_POLY(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       160+_ASIN_POLY(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       128+_ASIN_POLY(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       96+_ASIN_POLY(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       64+_ASIN_POLY(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       t32(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        fmul      %st(3), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdx)
        lea       48+_ASIN_POLY(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r9)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fld       %st(1)
        fldt      (%r10)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fmul      %st(3), %st
        movzwl    168(%rsp), %eax
        fsubr     %st, %st(1)
        andl      $32767, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        cmpl      $16377, %eax
        fsubr     %st(3), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fadd      %st(0), %st
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(3), %st
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(5), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(6)
        fxch      %st(4)
        fmul      %st(0), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      (%rdx)
        fld       %st(0)
        fadd      %st(2), %st
        jg        ..B3.7
..B3.3:
        jne       ..B3.8
..B3.4:
        movl      164(%rsp), %eax
        cmpl      4(%rdx), %eax
        ja        ..B3.7
..B3.5:
        jne       ..B3.8
..B3.6:
        movl      160(%rsp), %eax
        cmpl      (%rdx), %eax
        jbe       ..B3.8
..B3.7:
        lea       336+_ASIN_POLY(%rip), %rdx
        fsubr     %st, %st(2)
        lea       zeros(%rip), %rax
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rax)
        fldt      (%rdx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B3.9
..B3.8:
        lea       zeros(%rip), %rax
        fsubr     %st, %st(1)
        lea       336+_ASIN_POLY(%rip), %rdx
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rax)
        faddp     %st, %st(1)
        fldt      (%rdx)
        faddp     %st, %st(1)
..B3.9:
        fldt      64(%rsp)
        lea       32+_ASIN_POLY(%rip), %rdx
        fld       %st(0)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(3)
        fmul      %st, %st(5)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(6), %st
        fsubr     %st, %st(5)
        faddp     %st, %st(5)
        fxch      %st(2)
        fsub      %st(4), %st
        fld       %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(2)
        movzwl    168(%rsp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rdx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16378, %eax
        fadd      %st(2), %st
        jg        ..B3.14
..B3.10:
        jne       ..B3.15
..B3.11:
        movl      164(%rsp), %eax
        cmpl      4(%rdx), %eax
        ja        ..B3.14
..B3.12:
        jne       ..B3.15
..B3.13:
        movl      160(%rsp), %eax
        cmpl      (%rdx), %eax
        jbe       ..B3.15
..B3.14:
        lea       320+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.16
..B3.15:
        lea       320+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
..B3.16:
        fldt      112(%rsp)
        lea       16+_ASIN_POLY(%rip), %rdx
        fld       %st(0)
        fld       %st(3)
        fadd      %st(3), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fxch      %st(6)
        faddp     %st, %st(5)
        fldt      48(%rsp)
        fmul      %st(6), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      128(%rsp)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(5)
        fldt      64(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(1)
        movzwl    168(%rsp), %eax
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rdx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16379, %eax
        fadd      %st(3), %st
        jg        ..B3.21
..B3.17:
        jne       ..B3.22
..B3.18:
        movl      164(%rsp), %eax
        cmpl      4(%rdx), %eax
        ja        ..B3.21
..B3.19:
        jne       ..B3.22
..B3.20:
        movl      160(%rsp), %eax
        cmpl      (%rdx), %eax
        jbe       ..B3.22
..B3.21:
        lea       304+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      (%rax)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B3.23
..B3.22:
        lea       304+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rax)
        faddp     %st, %st(1)
..B3.23:
        fldt      112(%rsp)
        lea       _ASIN_POLY(%rip), %rdx
        fld       %st(0)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fldt      48(%rsp)
        fmul      %st(5), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      128(%rsp)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(3)
        fldt      64(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(2)
        movzwl    168(%rsp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rdx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16380, %eax
        fadd      %st(2), %st
        jg        ..B3.28
..B3.24:
        jne       ..B3.29
..B3.25:
        movl      164(%rsp), %eax
        cmpl      4(%rdx), %eax
        ja        ..B3.28
..B3.26:
        jne       ..B3.29
..B3.27:
        movl      160(%rsp), %eax
        cmpl      (%rdx), %eax
        jbe       ..B3.29
..B3.28:
        lea       288+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.30
..B3.29:
        lea       288+_ASIN_POLY(%rip), %rax
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      (%rax)
        faddp     %st, %st(2)
..B3.30:
        fldt      48(%rsp)
        fld       %st(2)
        movzwl    152(%rsp), %edx
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fld       %st(0)
        andl      $32767, %edx
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fsub      %st(1), %st
        fldt      112(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      128(%rsp)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      96(%rsp)
        fmulp     %st, %st(5)
        fldt      64(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fldt      48(%rsp)
        fmul      %st(4), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fsubr     %st, %st(1)
        fldt      (%rsp)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      32(%rsp)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(0)
        fldt      16(%rsp)
        fmulp     %st, %st(5)
        fldt      80(%rsp)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fadd      %st(3), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(1)
        movzwl    168(%rsp), %eax
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fadd      %st(2), %st
        andl      $32767, %eax
        cmpl      %edx, %eax
        jg        ..B3.35
..B3.31:
        jne       ..B3.36
..B3.32:
        movl      164(%rsp), %eax
        movl      148(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B3.35
..B3.33:
        jne       ..B3.36
..B3.34:
        movl      160(%rsp), %eax
        cmpl      144(%rsp), %eax
        jbe       ..B3.36
..B3.35:
        fldt      80(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.37
..B3.36:
        fldt      80(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        faddp     %st, %st(2)
..B3.37:
        fld       %st(1)
        movl      $0, 32(%rdi)
        fadd      %st(1), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        addq      $184, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 192
..B3.38:
        movq      (%rsi), %rax
        movq      8(%rsi), %rdx
        movq      16(%rsi), %rcx
        movq      24(%rsi), %r8
        movq      32(%rsi), %r9
        movq      40(%rsi), %rsi
        movq      %rax, (%rdi)
        movq      %rdx, 8(%rdi)
        movq      %rcx, 16(%rdi)
        movq      %r8, 24(%rdi)
        movq      %r9, 32(%rdi)
        movq      %rsi, 40(%rdi)
..B3.39:
        addq      $184, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	asinpoly,@function
	.size	asinpoly,.-asinpoly
	.data
# -- End  asinpoly
	.text
# -- Begin  __libm_recasinl_k80
	.text
       .align    16,0x90
	.globl __libm_recasinl_k80
__libm_recasinl_k80:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 704 + %rsp
# parameter 8: 712 + %rsp
# parameter 9: 720 + %rsp
# parameter 10: 728 + %rsp
..B4.1:
	.cfi_startproc
..___tag_value___libm_recasinl_k80.173:
..L174:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $648, %rsp
	.cfi_def_cfa_offset 704
        movq      %rdx, %r12
        movq      %fs:40, %rax
        movq      %r9, %r14
        xorq      %rsp, %rax
        movq      %r8, %rbp
        movq      %rax, 640(%rsp)
        movq      %rdi, %r15
        movzwl    8(%r12), %eax
        andl      $32767, %eax
        movq      728(%rsp), %r13
        cmpl      $8383, %eax
        jl        ..B4.5
..B4.2:
        cmpl      $24383, %eax
        jg        ..B4.5
..B4.3:
        movzwl    8(%r13), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.5
..B4.4:
        cmpl      $24383, %eax
        jle       ..B4.7
..B4.5:
        movq      %r12, %rdi
..___tag_value___libm_recasinl_k80.188:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.189:
..B4.6:
        movq      %r13, %rdi
..___tag_value___libm_recasinl_k80.190:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.191:
..B4.7:
        fldt      (%r12)
        lea       t32(%rip), %rax
        fld       %st(0)
        fldt      (%r13)
        fdivr     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fldt      (%rax)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(0)
        movl      32(%r12), %ebx
        fsubr     %st(6), %st
        subl      32(%r13), %ebx
        faddp     %st, %st(1)
        fld       %st(0)
        lea       1(%rbx), %edx
        movl      %edx, 624(%rsp)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fsub      %st(4), %st
        fxch      %st(3)
        fstpt     48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      16(%r12)
        faddp     %st, %st(3)
        fldt      16(%r13)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     592(%rsp)
        fldt      592(%rsp)
        fsubrp    %st, %st(1)
        movzwl    600(%rsp), %ecx
        fadd      %st(1), %st
        andl      $32767, %ecx
        fstpt     608(%rsp)
        fstpt     16(%rsp)
        lea       1(%rcx,%rbx), %esi
        cmpl      $16382, %esi
        jl        ..B4.183
..B4.8:
        movzwl    8(%r15), %eax
        andl      $32767, %eax
        movq      720(%rsp), %rbx
        cmpl      $16383, %eax
        jl        ..B4.37
..B4.9:
        movq      704(%rsp), %r9
        movq      (%r9), %rax
        movq      8(%r9), %rdx
        movq      16(%r9), %rcx
        movq      24(%r9), %rsi
        movq      32(%r9), %r8
        movq      40(%r9), %r10
        movzwl    8(%rbx), %r9d
        movq      %rax, 64(%rsp)
        movl      %r9d, %eax
        andl      $32767, %eax
        movq      %rdx, 72(%rsp)
        cmpl      $8383, %eax
        movq      %rcx, 80(%rsp)
        movq      %rsi, 88(%rsp)
        movq      %r8, 96(%rsp)
        movq      %r10, 104(%rsp)
        jl        ..B4.13
..B4.10:
        cmpl      $24383, %eax
        jg        ..B4.13
..B4.11:
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B4.13
..B4.12:
        cmpl      $24383, %r11d
        jle       ..B4.16
..B4.13:
        movq      %rbx, %rdi
..___tag_value___libm_recasinl_k80.192:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.193:
..B4.14:
        movq      %rbp, %rdi
..___tag_value___libm_recasinl_k80.194:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.195:
..B4.15:
        movzwl    8(%rbx), %r9d
        movl      %r9d, %eax
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B4.16:
        movl      32(%rbp), %edx
        movl      32(%rbx), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B4.33
..B4.17:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B4.33
..B4.18:
        cmpl      $0, 4(%rbx)
        jne       ..B4.20
..B4.19:
        cmpl      $0, (%rbx)
        je        ..B4.33
..B4.20:
        cmpl      $0, 4(%rbp)
        jne       ..B4.22
..B4.21:
        cmpl      $0, (%rbp)
        je        ..B4.33
..B4.22:
        cmpl      %edx, %ecx
        je        ..B4.24
..B4.23:
        fldt      16(%rsp)
        subl      %edx, %ecx
        fstpt     (%rsp)
        addl      $16383, %ecx
        fldt      (%rbx)
        andl      $32767, %ecx
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        fxch      %st(1)
        fstpt     16(%rsp)
        movl      32(%rbp), %edx
        movzwl    8(%rbp), %r10d
        movzwl    8(%rbx), %r9d
        jmp       ..B4.25
..B4.24:
        fldt      (%rbx)
        fstpt     16(%rsp)
        fldt      16(%rbx)
..B4.25:
        fldt      (%rbp)
        andl      $32767, %r9d
        fldt      16(%rsp)
        andl      $32767, %r10d
        movl      %edx, 144(%rsp)
        cmpl      %r10d, %r9d
        fadd      %st(1), %st
        jg        ..B4.30
..B4.26:
        jne       ..B4.31
..B4.27:
        movl      4(%rbx), %eax
        cmpl      4(%rbp), %eax
        ja        ..B4.30
..B4.28:
        jne       ..B4.31
..B4.29:
        movl      (%rbx), %eax
        cmpl      (%rbp), %eax
        jbe       ..B4.31
..B4.30:
        fldt      16(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      16(%rbp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B4.32
..B4.31:
        fldt      16(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      16(%rbp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B4.32:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     128(%rsp)
        jmp       ..B4.65
..B4.33:
        cmpq      $0, (%rbx)
        je        ..B4.35
..B4.34:
        lea       -16517(%rdx,%r11), %edx
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %edx
        jl        ..B4.36
..B4.35:
        movq      (%rbp), %rax
        movq      8(%rbp), %rdx
        movq      16(%rbp), %rcx
        movq      24(%rbp), %rbx
        movq      32(%rbp), %rsi
        movq      40(%rbp), %rbp
        movq      %rax, 112(%rsp)
        movq      %rdx, 120(%rsp)
        movq      %rcx, 128(%rsp)
        movq      %rbx, 136(%rsp)
        movq      %rsi, 144(%rsp)
        movq      %rbp, 152(%rsp)
        jmp       ..B4.65
..B4.36:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 112(%rsp)
        movq      %rdx, 120(%rsp)
        movq      %rcx, 128(%rsp)
        movq      %rbp, 136(%rsp)
        movq      %rsi, 144(%rsp)
        movq      %rbx, 152(%rsp)
        jmp       ..B4.65
..B4.37:
        lea       ones(%rip), %rax
        lea       zeros(%rip), %rdx
        movzwl    8(%rbx), %r9d
        movl      $0, 144(%rsp)
        fldt      (%rax)
        movl      %r9d, %eax
        andl      $32767, %eax
        fstpt     112(%rsp)
        cmpl      $8383, %eax
        fldt      (%rdx)
        fstpt     128(%rsp)
        jl        ..B4.41
..B4.38:
        cmpl      $24383, %eax
        jg        ..B4.41
..B4.39:
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B4.41
..B4.40:
        cmpl      $24383, %r11d
        jle       ..B4.44
..B4.41:
        movq      %rbx, %rdi
..___tag_value___libm_recasinl_k80.196:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.197:
..B4.42:
        movq      %rbp, %rdi
..___tag_value___libm_recasinl_k80.198:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.199:
..B4.43:
        movzwl    8(%rbx), %r9d
        movl      %r9d, %eax
        movzwl    8(%rbp), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B4.44:
        movl      32(%rbp), %edx
        movl      32(%rbx), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B4.61
..B4.45:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B4.61
..B4.46:
        cmpl      $0, 4(%rbx)
        jne       ..B4.48
..B4.47:
        cmpl      $0, (%rbx)
        je        ..B4.61
..B4.48:
        cmpl      $0, 4(%rbp)
        jne       ..B4.50
..B4.49:
        cmpl      $0, (%rbp)
        je        ..B4.61
..B4.50:
        cmpl      %edx, %ecx
        je        ..B4.52
..B4.51:
        fldt      16(%rsp)
        subl      %edx, %ecx
        fstpt     (%rsp)
        addl      $16383, %ecx
        fldt      (%rbx)
        andl      $32767, %ecx
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        fstpt     16(%rsp)
        movl      32(%rbp), %edx
        movzwl    8(%rbp), %r10d
        movzwl    8(%rbx), %r9d
        jmp       ..B4.53
..B4.52:
        fldt      (%rbx)
        fldt      16(%rbx)
        fstpt     16(%rsp)
..B4.53:
        fldt      (%rbp)
        andl      $32767, %r9d
        andl      $32767, %r10d
        fld       %st(1)
        movl      %edx, 96(%rsp)
        cmpl      %r10d, %r9d
        fsub      %st(1), %st
        jg        ..B4.58
..B4.54:
        jne       ..B4.59
..B4.55:
        movl      4(%rbx), %eax
        cmpl      4(%rbp), %eax
        ja        ..B4.58
..B4.56:
        jne       ..B4.59
..B4.57:
        movl      (%rbx), %eax
        cmpl      (%rbp), %eax
        jbe       ..B4.59
..B4.58:
        fldt      16(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      16(%rbp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        jmp       ..B4.60
..B4.59:
        fldt      16(%rbp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      16(%rsp)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
..B4.60:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     80(%rsp)
        jmp       ..B4.65
..B4.61:
        cmpq      $0, (%rbx)
        je        ..B4.63
..B4.62:
        lea       -16517(%rdx,%r11), %esi
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %esi
        jl        ..B4.64
..B4.63:
        fldt      (%rbp)
        fchs      
        movl      %edx, 96(%rsp)
        fstpt     64(%rsp)
        fldt      16(%rbp)
        fchs      
        fstpt     80(%rsp)
        jmp       ..B4.65
..B4.64:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 64(%rsp)
        movq      %rdx, 72(%rsp)
        movq      %rcx, 80(%rsp)
        movq      %rbp, 88(%rsp)
        movq      %rsi, 96(%rsp)
        movq      %rbx, 104(%rsp)
..B4.65:
        movzwl    8(%r12), %ebp
        movl      %ebp, %r9d
        andl      $32767, %r9d
        movl      32(%r12), %edx
        incl      %edx
        movl      %edx, 32(%r12)
        cmpl      $8383, %r9d
        jl        ..B4.69
..B4.66:
        cmpl      $24383, %r9d
        jg        ..B4.69
..B4.67:
        movzwl    8(%r13), %eax
        movl      %eax, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B4.69
..B4.68:
        cmpl      $24383, %r8d
        jle       ..B4.72
..B4.69:
        movq      %r12, %rdi
..___tag_value___libm_recasinl_k80.200:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.201:
..B4.70:
        movq      %r13, %rdi
..___tag_value___libm_recasinl_k80.202:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.203:
..B4.71:
        movzwl    8(%r12), %ebp
        movl      %ebp, %r9d
        movzwl    8(%r13), %eax
        movl      %eax, %r8d
        movl      32(%r12), %edx
        andl      $32767, %r9d
        andl      $32767, %r8d
..B4.72:
        movl      32(%r13), %esi
        lea       (%rdx,%r9), %ebx
        lea       -134(%rsi,%r8), %ecx
        cmpl      %ecx, %ebx
        jle       ..B4.89
..B4.73:
        lea       134(%rsi,%r8), %ecx
        cmpl      %ecx, %ebx
        jge       ..B4.89
..B4.74:
        cmpl      $0, 4(%r12)
        jne       ..B4.76
..B4.75:
        cmpl      $0, (%r12)
        je        ..B4.89
..B4.76:
        cmpl      $0, 4(%r13)
        jne       ..B4.78
..B4.77:
        cmpl      $0, (%r13)
        je        ..B4.89
..B4.78:
        cmpl      %esi, %edx
        je        ..B4.80
..B4.79:
        fldt      16(%rsp)
        subl      %esi, %edx
        fstpt     (%rsp)
        addl      $16383, %edx
        fldt      (%r12)
        andl      $32767, %edx
        fldt      16(%r12)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edx, %eax
        movl      %esi, %edx
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %esi, 32(%r12)
        fxch      %st(2)
        fstpt     (%r12)
        fldt      (%r12)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r12)
        fldt      16(%r12)
        movl      32(%r13), %esi
        movzwl    8(%r13), %eax
        movzwl    8(%r12), %ebp
        jmp       ..B4.81
..B4.80:
        fldt      (%r12)
        fldt      16(%r12)
..B4.81:
        fldt      (%r13)
        movl      %eax, %ecx
        andl      $32767, %ebp
        andl      $32767, %ecx
        fld       %st(2)
        cmpl      %ecx, %ebp
        movl      %esi, 192(%rsp)
        fadd      %st(1), %st
        jg        ..B4.86
..B4.82:
        jne       ..B4.87
..B4.83:
        movl      4(%r12), %ecx
        cmpl      4(%r13), %ecx
        ja        ..B4.86
..B4.84:
        jne       ..B4.87
..B4.85:
        movl      (%r12), %ecx
        cmpl      (%r13), %ecx
        jbe       ..B4.87
..B4.86:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      16(%r13)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B4.88
..B4.87:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%r13)
        faddp     %st, %st(2)
..B4.88:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        jmp       ..B4.93
..B4.89:
        cmpq      $0, (%r12)
        je        ..B4.91
..B4.90:
        lea       -16517(%rsi,%r8), %ecx
        lea       -16383(%rdx,%r9), %ebx
        cmpl      %ebx, %ecx
        jl        ..B4.92
..B4.91:
        movq      (%r13), %rcx
        movq      8(%r13), %rbx
        movq      16(%r13), %rbp
        movq      24(%r13), %rsi
        movq      32(%r13), %r8
        movq      40(%r13), %r9
        movq      %rcx, 160(%rsp)
        movq      %rbx, 168(%rsp)
        movq      %rbp, 176(%rsp)
        movq      %rsi, 184(%rsp)
        movq      %r8, 192(%rsp)
        movq      %r9, 200(%rsp)
        jmp       ..B4.93
..B4.92:
        movq      (%r12), %rcx
        movq      8(%r12), %rbx
        movq      16(%r12), %rbp
        movq      24(%r12), %rsi
        movq      32(%r12), %r8
        movq      40(%r12), %r9
        movq      %rcx, 160(%rsp)
        movq      %rbx, 168(%rsp)
        movq      %rbp, 176(%rsp)
        movq      %rsi, 184(%rsp)
        movq      %r8, 192(%rsp)
        movq      %r9, 200(%rsp)
..B4.93:
        decl      %edx
        movl      %edx, 32(%r12)
        movzwl    168(%rsp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B4.97
..B4.94:
        cmpl      $24383, %edx
        jg        ..B4.97
..B4.95:
        movzwl    72(%rsp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B4.97
..B4.96:
        cmpl      $24383, %edx
        jle       ..B4.99
..B4.97:
        lea       160(%rsp), %rdi
..___tag_value___libm_recasinl_k80.204:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.205:
..B4.98:
        lea       64(%rsp), %rdi
..___tag_value___libm_recasinl_k80.206:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.207:
        movzwl    8(%r13), %eax
..B4.99:
        fldt      160(%rsp)
        andl      $32767, %eax
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      192(%rsp), %edx
        fsubr     %st, %st(3)
        addl      96(%rsp), %edx
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %eax
        movl      %edx, 240(%rsp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      64(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      80(%rsp)
        fmulp     %st, %st(4)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     224(%rsp)
        jl        ..B4.103
..B4.100:
        cmpl      $24383, %eax
        jg        ..B4.103
..B4.101:
        movzwl    120(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.103
..B4.102:
        cmpl      $24383, %eax
        jle       ..B4.105
..B4.103:
        movq      %r13, %rdi
..___tag_value___libm_recasinl_k80.208:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.209:
..B4.104:
        lea       112(%rsp), %rdi
..___tag_value___libm_recasinl_k80.210:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.211:
..B4.105:
        fldt      (%r13)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movq      712(%rsp), %rbx
        fsubr     %st, %st(3)
        movzwl    8(%rbx), %ecx
        movl      %ecx, %r10d
        movl      144(%rsp), %eax
        andl      $32767, %r10d
        addl      32(%r13), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %r10d
        movl      %eax, 288(%rsp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      112(%rsp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      128(%rsp)
        fmulp     %st, %st(4)
        fldt      16(%r13)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     256(%rsp)
        fldt      256(%rsp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     272(%rsp)
        fstpt     16(%rsp)
        jl        ..B4.109
..B4.106:
        cmpl      $24383, %r10d
        jg        ..B4.109
..B4.107:
        movzwl    8(%r14), %ebp
        movl      %ebp, %r8d
        andl      $32767, %r8d
        cmpl      $8383, %r8d
        jl        ..B4.109
..B4.108:
        cmpl      $24383, %r8d
        jle       ..B4.112
..B4.109:
        movq      %rbx, %rdi
..___tag_value___libm_recasinl_k80.212:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.213:
..B4.110:
        movq      %r14, %rdi
..___tag_value___libm_recasinl_k80.214:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.215:
..B4.111:
        movzwl    8(%rbx), %ecx
        movl      %ecx, %r10d
        movzwl    8(%r14), %ebp
        movl      %ebp, %r8d
        andl      $32767, %r10d
        andl      $32767, %r8d
..B4.112:
        movl      32(%r14), %esi
        movl      32(%rbx), %r9d
        lea       -134(%rsi,%r8), %eax
        lea       (%r10,%r9), %edx
        cmpl      %eax, %edx
        jle       ..B4.129
..B4.113:
        lea       134(%rsi,%r8), %eax
        cmpl      %eax, %edx
        jge       ..B4.129
..B4.114:
        cmpl      $0, 4(%rbx)
        jne       ..B4.116
..B4.115:
        cmpl      $0, (%rbx)
        je        ..B4.129
..B4.116:
        cmpl      $0, 4(%r14)
        jne       ..B4.118
..B4.117:
        cmpl      $0, (%r14)
        je        ..B4.129
..B4.118:
        cmpl      %esi, %r9d
        je        ..B4.120
..B4.119:
        fldt      16(%rsp)
        subl      %esi, %r9d
        fstpt     (%rsp)
        addl      $16383, %r9d
        fldt      (%rbx)
        andl      $32767, %r9d
        fldt      16(%rbx)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %r9d, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %esi, 32(%rbx)
        fxch      %st(2)
        fstpt     (%rbx)
        fldt      (%rbx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbx)
        fldt      16(%rbx)
        movl      32(%r14), %esi
        movzwl    8(%r14), %ebp
        movzwl    8(%rbx), %ecx
        jmp       ..B4.121
..B4.120:
        fldt      (%rbx)
        fldt      16(%rbx)
..B4.121:
        fldt      (%r14)
        andl      $32767, %ecx
        andl      $32767, %ebp
        fld       %st(2)
        movl      %esi, 336(%rsp)
        cmpl      %ebp, %ecx
        fadd      %st(1), %st
        jg        ..B4.126
..B4.122:
        jne       ..B4.127
..B4.123:
        movl      4(%rbx), %eax
        cmpl      4(%r14), %eax
        ja        ..B4.126
..B4.124:
        jne       ..B4.127
..B4.125:
        movl      (%rbx), %eax
        cmpl      (%r14), %eax
        jbe       ..B4.127
..B4.126:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%r14)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B4.128
..B4.127:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%r14)
        faddp     %st, %st(1)
..B4.128:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     304(%rsp)
        fldt      304(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     320(%rsp)
        jmp       ..B4.133
..B4.129:
        cmpq      $0, (%rbx)
        je        ..B4.131
..B4.130:
        lea       -16517(%rsi,%r8), %eax
        lea       -16383(%r9,%r10), %edx
        cmpl      %edx, %eax
        jl        ..B4.132
..B4.131:
        movq      (%r14), %rax
        movq      8(%r14), %rdx
        movq      16(%r14), %rcx
        movq      24(%r14), %rbx
        movq      32(%r14), %rbp
        movq      40(%r14), %rsi
        movq      %rax, 304(%rsp)
        movq      %rdx, 312(%rsp)
        movq      %rcx, 320(%rsp)
        movq      %rbx, 328(%rsp)
        movq      %rbp, 336(%rsp)
        movq      %rsi, 344(%rsp)
        jmp       ..B4.133
..B4.132:
        movq      (%rbx), %rax
        movq      8(%rbx), %rdx
        movq      16(%rbx), %rcx
        movq      24(%rbx), %rbp
        movq      32(%rbx), %rsi
        movq      40(%rbx), %rbx
        movq      %rax, 304(%rsp)
        movq      %rdx, 312(%rsp)
        movq      %rcx, 320(%rsp)
        movq      %rbp, 328(%rsp)
        movq      %rsi, 336(%rsp)
        movq      %rbx, 344(%rsp)
..B4.133:
        movzwl    264(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.137
..B4.134:
        cmpl      $24383, %eax
        jg        ..B4.137
..B4.135:
        movzwl    312(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.137
..B4.136:
        cmpl      $24383, %eax
        jle       ..B4.139
..B4.137:
        lea       256(%rsp), %rdi
..___tag_value___libm_recasinl_k80.216:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.217:
..B4.138:
        lea       304(%rsp), %rdi
..___tag_value___libm_recasinl_k80.218:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.219:
..B4.139:
        fldt      256(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      288(%rsp), %edx
        fsubr     %st, %st(3)
        movzwl    216(%rsp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      336(%rsp), %edx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      304(%rsp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %edx, 384(%rsp)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      320(%rsp)
        fmulp     %st, %st(4)
        fldt      272(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     352(%rsp)
        fldt      352(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     368(%rsp)
        fldt      368(%rsp)
        jl        ..B4.143
..B4.140:
        cmpl      $24383, %eax
        jg        ..B4.143
..B4.141:
        movzwl    360(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.143
..B4.142:
        cmpl      $24383, %eax
        jle       ..B4.146
..B4.143:
        fstp      %st(1)
        fstp      %st(0)
        lea       208(%rsp), %rdi
..___tag_value___libm_recasinl_k80.220:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.221:
..B4.144:
        lea       352(%rsp), %rdi
..___tag_value___libm_recasinl_k80.222:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.223:
..B4.145:
        fldt      352(%rsp)
        fldt      368(%rsp)
        movl      384(%rsp), %edx
..B4.146:
        fldt      208(%rsp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      48(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      240(%rsp), %ecx
        fsubr     %st(4), %st
        subl      %edx, %ecx
        faddp     %st, %st(1)
        fld       %st(0)
        decl      %ecx
        movl      %ecx, 432(%rsp)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(3), %st
        fsubr     %st, %st(7)
        faddp     %st, %st(7)
        fxch      %st(2)
        fsub      %st(6), %st
        fld       %st(2)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(7)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      224(%rsp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      32(%rsp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fsubr     %st, %st(2)
        movzwl    408(%rsp), %eax
        fxch      %st(1)
        fadd      %st, %st(2)
        andl      $32767, %eax
        fxch      %st(2)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        cmpl      $383, %eax
        fxch      %st(2)
        fstpt     16(%rsp)
        fxch      %st(1)
        jl        ..B4.148
..B4.147:
        cmpl      $32383, %eax
        jle       ..B4.150
..B4.148:
        fstp      %st(0)
        fstp      %st(0)
        lea       400(%rsp), %rdi
..___tag_value___libm_recasinl_k80.224:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.225:
..B4.149:
        fldt      400(%rsp)
        fldt      416(%rsp)
        movl      432(%rsp), %ecx
..B4.150:
        testb     $1, %cl
        jne       ..B4.188
..B4.151:
        lea       halfs(%rip), %rax
        fldt      (%rax)
..B4.152:
        fldt      48(%rsp)
        fld       %st(3)
        fsqrt     
        lea       448(%rsp), %rdi
        fmul      %st, %st(1)
        lea       496(%rsp), %rsi
        fld       %st(1)
        sarl      $1, %ecx
        fsubr     %st(1), %st
        movl      %ecx, 32(%rsi)
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rsi)
        fldt      (%rsi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsi)
..___tag_value___libm_recasinl_k80.226:
        call      asinpoly
..___tag_value___libm_recasinl_k80.227:
..B4.153:
        lea       16+_PIO2_K80(%rip), %rax
        lea       32+_PIO2_K80(%rip), %rdx
        movl      480(%rsp), %edi
        xorl      %ebp, %ebp
        incl      %edi
        movl      %edi, 480(%rsp)
        fldt      (%rax)
        movzwl    8(%rax), %esi
        andl      $32767, %esi
        movl      $0, 576(%rsp)
        cmpl      $8383, %esi
        fstpt     544(%rsp)
        fldt      (%rdx)
        fstpt     560(%rsp)
        jl        ..B4.157
..B4.154:
        cmpl      $24383, %esi
        jg        ..B4.157
..B4.155:
        movzwl    456(%rsp), %ebx
        andl      $32767, %ebx
        cmpl      $8383, %ebx
        jl        ..B4.157
..B4.156:
        cmpl      $24383, %ebx
        jle       ..B4.160
..B4.157:
        lea       544(%rsp), %rdi
..___tag_value___libm_recasinl_k80.228:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.229:
..B4.158:
        lea       448(%rsp), %rdi
..___tag_value___libm_recasinl_k80.230:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recasinl_k80.231:
..B4.159:
        movzwl    552(%rsp), %esi
        movzwl    456(%rsp), %ebx
        andl      $32767, %esi
        movl      480(%rsp), %edi
        andl      $32767, %ebx
        movl      576(%rsp), %ebp
..B4.160:
        lea       (%rbp,%rsi), %edx
        lea       -134(%rdi,%rbx), %eax
        cmpl      %eax, %edx
        jle       ..B4.177
..B4.161:
        movl      548(%rsp), %ecx
        lea       134(%rdi,%rbx), %eax
        cmpl      %eax, %edx
        jge       ..B4.178
..B4.162:
        testl     %ecx, %ecx
        jne       ..B4.164
..B4.163:
        cmpl      $0, 544(%rsp)
        je        ..B4.179
..B4.164:
        movl      452(%rsp), %edx
        testl     %edx, %edx
        jne       ..B4.166
..B4.165:
        cmpl      $0, 448(%rsp)
        je        ..B4.178
..B4.166:
        cmpl      %edi, %ebp
        je        ..B4.168
..B4.167:
        fldt      16(%rsp)
        subl      %edi, %ebp
        fstpt     (%rsp)
        addl      $16383, %ebp
        fldt      544(%rsp)
        andl      $32767, %ebp
        fldt      560(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ebp, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     544(%rsp)
        fldt      544(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    552(%rsp), %esi
        movl      %edi, 576(%rsp)
        andl      $32767, %esi
        fstpt     560(%rsp)
        fldt      560(%rsp)
        jmp       ..B4.169
..B4.168:
        fldt      544(%rsp)
        fldt      560(%rsp)
..B4.169:
        fldt      448(%rsp)
        fld       %st(2)
        movl      %edi, 48(%rsp)
        cmpl      %ebx, %esi
        fsub      %st(1), %st
        jg        ..B4.174
..B4.170:
        jne       ..B4.175
..B4.171:
        movl      548(%rsp), %eax
        cmpl      %edx, %eax
        ja        ..B4.174
..B4.172:
        jne       ..B4.175
..B4.173:
        movl      544(%rsp), %eax
        cmpl      448(%rsp), %eax
        jbe       ..B4.175
..B4.174:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      464(%rsp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B4.176
..B4.175:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      464(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B4.176:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        jmp       ..B4.185
..B4.177:
        movl      548(%rsp), %ecx
..B4.178:
        testl     %ecx, %ecx
        jne       ..B4.180
..B4.193:
        cmpl      $0, 544(%rsp)
..B4.179:
        je        ..B4.181
..B4.180:
        lea       -16517(%rdi,%rbx), %eax
        lea       -16383(%rbp,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B4.182
..B4.181:
        fldt      448(%rsp)
        fchs      
        movl      %edi, 48(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      464(%rsp)
        fchs      
        fstpt     32(%rsp)
        fldt      32(%rsp)
        jmp       ..B4.185
..B4.182:
        fldt      544(%rsp)
        fldt      560(%rsp)
        movaps    544(%rsp), %xmm0
        movaps    560(%rsp), %xmm1
        movaps    576(%rsp), %xmm2
        movl      576(%rsp), %edi
        movaps    %xmm0, 16(%rsp)
        movaps    %xmm1, 32(%rsp)
        movaps    %xmm2, 48(%rsp)
        jmp       ..B4.185
..B4.183:
        lea       16(%rsp), %rdi
        lea       592(%rsp), %rsi
..___tag_value___libm_recasinl_k80.232:
        call      asinpoly
..___tag_value___libm_recasinl_k80.233:
..B4.184:
        fldt      16(%rsp)
        fldt      32(%rsp)
        movl      48(%rsp), %edi
..B4.185:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 736
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_recasinl_k80.235:
        call      __libm_scalbl_k80@PLT
..___tag_value___libm_recasinl_k80.236:
..B4.192:
        addq      $32, %rsp
	.cfi_def_cfa_offset 704
..B4.186:
        movb      9(%r15), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        shlq      $4, %rcx
        movq      640(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        fldt      (%rdx,%rcx)
        fmulp     %st, %st(1)
        jne       ..B4.189
..B4.187:
        addq      $648, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 704
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B4.188:
        lea       halfs(%rip), %rax
        incl      %ecx
        movl      %ecx, 432(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B4.152
..B4.189:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__libm_recasinl_k80,@function
	.size	__libm_recasinl_k80,.-__libm_recasinl_k80
	.data
# -- End  __libm_recasinl_k80
	.section .rodata, "a"
	.align 16
	.align 16
ones:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	ones,@object
	.size	ones,32
	.align 16
zeros:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	zeros,@object
	.size	zeros,32
	.align 16
t32:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	t32,@object
	.size	t32,32
	.align 16
halfs:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	halfs,@object
	.size	halfs,32
	.align 2
_PIO2_K80:
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49085
	.word	0
	.word	0
	.word	0
	.type	_PIO2_K80,@object
	.size	_PIO2_K80,48
	.align 2
_ASIN_POLY:
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39322
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	28087
	.word	46811
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	36409
	.word	63715
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	62186
	.word	9163
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	11943
	.word	18321
	.word	25215
	.word	36391
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	12527
	.word	63486
	.word	51909
	.word	58572
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	16437
	.word	53165
	.word	60799
	.word	48451
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	39560
	.word	816
	.word	43435
	.word	40940
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	47763
	.word	38463
	.word	31161
	.word	35220
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	61035
	.word	37666
	.word	58653
	.word	60832
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	4110
	.word	2419
	.word	35453
	.word	57515
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	3126
	.word	47859
	.word	6163
	.word	61068
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	56145
	.word	58008
	.word	18829
	.word	55579
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	17944
	.word	24712
	.word	25688
	.word	38195
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	27414
	.word	34244
	.word	64801
	.word	51841
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	63442
	.word	25896
	.word	35822
	.word	55851
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	37640
	.word	1996
	.word	32075
	.word	42748
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	39460
	.word	64770
	.word	43690
	.word	43690
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	48327
	.word	21484
	.word	52058
	.word	52428
	.word	49114
	.word	0
	.word	0
	.word	0
	.word	52692
	.word	45548
	.word	47571
	.word	37453
	.word	49112
	.word	0
	.word	0
	.word	0
	.word	9214
	.word	45875
	.word	56244
	.word	56360
	.word	49110
	.word	0
	.word	0
	.word	0
	.type	_ASIN_POLY,@object
	.size	_ASIN_POLY,352
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
