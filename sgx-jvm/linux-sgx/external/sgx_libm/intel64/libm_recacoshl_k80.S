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
	.file "libm_recacoshl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_recacoshl_k80
	.text
       .align    16,0x90
	.globl __libm_recacoshl_k80
__libm_recacoshl_k80:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 688 + %rsp
# parameter 8: 696 + %rsp
# parameter 9: 704 + %rsp
# parameter 10: 712 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___libm_recacoshl_k80.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbp
	.cfi_def_cfa_offset 32
	.cfi_offset 6, -32
        subq      $656, %rsp
	.cfi_def_cfa_offset 688
        movq      %r9, %rbp
        movzwl    8(%rdi), %r10d
        movq      %r8, %r15
        andl      $32767, %r10d
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 640(%rsp)
        cmpl      $16383, %r10d
        movq      704(%rsp), %r13
        jl        ..B1.31
..B1.2:
        movzwl    8(%r13), %r9d
        movl      %r9d, %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.6
..B1.3:
        cmpl      $24383, %eax
        jg        ..B1.6
..B1.4:
        movzwl    8(%r15), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B1.6
..B1.5:
        cmpl      $24383, %r11d
        jle       ..B1.9
..B1.6:
        movq      %r13, %rdi
..___tag_value___libm_recacoshl_k80.10:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.11:
..B1.7:
        movq      %r15, %rdi
..___tag_value___libm_recacoshl_k80.12:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.13:
..B1.8:
        movzwl    8(%r13), %r9d
        movl      %r9d, %eax
        movzwl    8(%r15), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B1.9:
        movl      32(%r15), %edx
        movl      32(%r13), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B1.26
..B1.10:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B1.26
..B1.11:
        cmpl      $0, 4(%r13)
        jne       ..B1.13
..B1.12:
        cmpl      $0, (%r13)
        je        ..B1.26
..B1.13:
        cmpl      $0, 4(%r15)
        jne       ..B1.15
..B1.14:
        cmpl      $0, (%r15)
        je        ..B1.26
..B1.15:
        cmpl      %edx, %ecx
        je        ..B1.17
..B1.16:
        fldt      (%r13)
        subl      %edx, %ecx
        fldt      16(%r13)
        addl      $16383, %ecx
        andb      $127, 9(%rsp)
        andl      $32767, %ecx
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%r13)
        fxch      %st(2)
        fstpt     (%r13)
        fldt      (%r13)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        fldt      16(%r13)
        movl      32(%r15), %edx
        movzwl    8(%r15), %r10d
        movzwl    8(%r13), %r9d
        jmp       ..B1.18
..B1.17:
        fldt      (%r13)
        fldt      16(%r13)
..B1.18:
        fldt      (%r15)
        andl      $32767, %r9d
        andl      $32767, %r10d
        fld       %st(2)
        movl      %edx, 96(%rsp)
        cmpl      %r10d, %r9d
        fadd      %st(1), %st
        jg        ..B1.23
..B1.19:
        jne       ..B1.24
..B1.20:
        movl      4(%r13), %eax
        cmpl      4(%r15), %eax
        ja        ..B1.23
..B1.21:
        jne       ..B1.24
..B1.22:
        movl      (%r13), %eax
        cmpl      (%r15), %eax
        jbe       ..B1.24
..B1.23:
        fldt      16(%r15)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B1.25
..B1.24:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%r15)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B1.25:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     80(%rsp)
        jmp       ..B1.30
..B1.26:
        cmpq      $0, (%r13)
        je        ..B1.28
..B1.27:
        lea       -16517(%rdx,%r11), %edx
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %edx
        jl        ..B1.29
..B1.28:
        movq      (%r15), %rax
        movq      8(%r15), %rdx
        movq      16(%r15), %rcx
        movq      24(%r15), %rsi
        movq      32(%r15), %r8
        movq      40(%r15), %r9
        movq      %rax, 64(%rsp)
        movq      %rdx, 72(%rsp)
        movq      %rcx, 80(%rsp)
        movq      %rsi, 88(%rsp)
        movq      %r8, 96(%rsp)
        movq      %r9, 104(%rsp)
        jmp       ..B1.30
..B1.29:
        movq      (%r13), %rax
        movq      8(%r13), %rdx
        movq      16(%r13), %rcx
        movq      24(%r13), %rsi
        movq      32(%r13), %r8
        movq      40(%r13), %r9
        movq      %rax, 64(%rsp)
        movq      %rdx, 72(%rsp)
        movq      %rcx, 80(%rsp)
        movq      %rsi, 88(%rsp)
        movq      %r8, 96(%rsp)
        movq      %r9, 104(%rsp)
..B1.30:
        lea       ones(%rip), %rax
        lea       zeros(%rip), %rdx
        movl      $0, 144(%rsp)
        fldt      (%rax)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      (%rdx)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        jmp       ..B1.59
..B1.31:
        movq      688(%rsp), %r9
        movq      (%r9), %rax
        movq      8(%r9), %rdx
        movq      16(%r9), %rcx
        movq      24(%r9), %rsi
        movq      32(%r9), %r8
        movq      40(%r9), %r10
        movzwl    8(%r13), %r9d
        movq      %rax, 64(%rsp)
        movl      %r9d, %eax
        andl      $32767, %eax
        movq      %rdx, 72(%rsp)
        cmpl      $8383, %eax
        movq      %rcx, 80(%rsp)
        movq      %rsi, 88(%rsp)
        movq      %r8, 96(%rsp)
        movq      %r10, 104(%rsp)
        jl        ..B1.35
..B1.32:
        cmpl      $24383, %eax
        jg        ..B1.35
..B1.33:
        movzwl    8(%r15), %r10d
        movl      %r10d, %r11d
        andl      $32767, %r11d
        cmpl      $8383, %r11d
        jl        ..B1.35
..B1.34:
        cmpl      $24383, %r11d
        jle       ..B1.38
..B1.35:
        movq      %r13, %rdi
..___tag_value___libm_recacoshl_k80.14:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.15:
..B1.36:
        movq      %r15, %rdi
..___tag_value___libm_recacoshl_k80.16:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.17:
..B1.37:
        movzwl    8(%r13), %r9d
        movl      %r9d, %eax
        movzwl    8(%r15), %r10d
        movl      %r10d, %r11d
        andl      $32767, %eax
        andl      $32767, %r11d
..B1.38:
        movl      32(%r15), %edx
        movl      32(%r13), %ecx
        lea       -134(%rdx,%r11), %esi
        lea       (%rax,%rcx), %r8d
        cmpl      %r8d, %esi
        jge       ..B1.55
..B1.39:
        lea       134(%rdx,%r11), %esi
        cmpl      %esi, %r8d
        jge       ..B1.55
..B1.40:
        cmpl      $0, 4(%r13)
        jne       ..B1.42
..B1.41:
        cmpl      $0, (%r13)
        je        ..B1.55
..B1.42:
        cmpl      $0, 4(%r15)
        jne       ..B1.44
..B1.43:
        cmpl      $0, (%r15)
        je        ..B1.55
..B1.44:
        cmpl      %edx, %ecx
        je        ..B1.46
..B1.45:
        fldt      (%r13)
        subl      %edx, %ecx
        fldt      16(%r13)
        addl      $16383, %ecx
        andb      $127, 9(%rsp)
        andl      $32767, %ecx
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 32(%r13)
        fxch      %st(2)
        fstpt     (%r13)
        fldt      (%r13)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        fldt      16(%r13)
        movl      32(%r15), %edx
        movzwl    8(%r15), %r10d
        movzwl    8(%r13), %r9d
        jmp       ..B1.47
..B1.46:
        fldt      (%r13)
        fldt      16(%r13)
..B1.47:
        fldt      (%r15)
        andl      $32767, %r9d
        andl      $32767, %r10d
        fld       %st(2)
        movl      %edx, 144(%rsp)
        cmpl      %r10d, %r9d
        fsub      %st(1), %st
        jg        ..B1.52
..B1.48:
        jne       ..B1.53
..B1.49:
        movl      4(%r13), %eax
        cmpl      4(%r15), %eax
        ja        ..B1.52
..B1.50:
        jne       ..B1.53
..B1.51:
        movl      (%r13), %eax
        cmpl      (%r15), %eax
        jbe       ..B1.53
..B1.52:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      16(%r15)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B1.54
..B1.53:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%r15)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B1.54:
        fld       %st(1)
        lea       ones(%rip), %rax
        lea       zeros(%rip), %rdx
        fadd      %st(1), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     128(%rsp)
        fldt      (%rax)
        fldt      (%rdx)
        jmp       ..B1.59
..B1.55:
        cmpq      $0, (%r13)
        je        ..B1.57
..B1.56:
        lea       -16517(%rdx,%r11), %esi
        lea       -16383(%rcx,%rax), %eax
        cmpl      %eax, %esi
        jl        ..B1.58
..B1.57:
        fldt      (%r15)
        lea       ones(%rip), %rax
        movl      %edx, 144(%rsp)
        lea       zeros(%rip), %rdx
        fchs      
        fstpt     112(%rsp)
        fldt      16(%r15)
        fchs      
        fstpt     128(%rsp)
        fldt      (%rax)
        fldt      (%rdx)
        jmp       ..B1.59
..B1.58:
        lea       ones(%rip), %r10
        lea       zeros(%rip), %r11
        movq      (%r13), %rax
        movq      8(%r13), %rdx
        movq      16(%r13), %rcx
        fldt      (%r10)
        fldt      (%r11)
        movq      24(%r13), %rsi
        movq      32(%r13), %r8
        movq      40(%r13), %r9
        movq      %rax, 112(%rsp)
        movq      %rdx, 120(%rsp)
        movq      %rcx, 128(%rsp)
        movq      %rsi, 136(%rsp)
        movq      %r8, 144(%rsp)
        movq      %r9, 152(%rsp)
..B1.59:
        movq      712(%rsp), %r13
        fxch      %st(1)
        fadd      %st(0), %st
        movzwl    8(%r13), %r8d
        movl      %r8d, %r11d
        andl      $32767, %r11d
        movl      $0, 288(%rsp)
        cmpl      $8383, %r11d
        fstpt     256(%rsp)
        fstpt     272(%rsp)
        jl        ..B1.63
..B1.60:
        cmpl      $24383, %r11d
        jg        ..B1.63
..B1.61:
        movzwl    264(%rsp), %edx
        movl      %edx, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B1.63
..B1.62:
        cmpl      $24383, %r10d
        jle       ..B1.66
..B1.63:
        movq      %r13, %rdi
..___tag_value___libm_recacoshl_k80.18:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.19:
..B1.64:
        lea       256(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.20:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.21:
..B1.65:
        movzwl    8(%r13), %r8d
        movl      %r8d, %r11d
        movzwl    264(%rsp), %edx
        movl      %edx, %r10d
        andl      $32767, %r11d
        andl      $32767, %r10d
..B1.66:
        movl      288(%rsp), %r9d
        movl      32(%r13), %eax
        lea       -134(%r9,%r10), %ecx
        lea       (%r11,%rax), %esi
        cmpl      %ecx, %esi
        jle       ..B1.83
..B1.67:
        lea       134(%r9,%r10), %ecx
        cmpl      %ecx, %esi
        jge       ..B1.83
..B1.68:
        cmpl      $0, 4(%r13)
        jne       ..B1.70
..B1.69:
        cmpl      $0, (%r13)
        je        ..B1.83
..B1.70:
        cmpl      $0, 260(%rsp)
        jne       ..B1.72
..B1.71:
        cmpl      $0, 256(%rsp)
        je        ..B1.83
..B1.72:
        cmpl      %r9d, %eax
        je        ..B1.74
..B1.73:
        fldt      (%r13)
        subl      %r9d, %eax
        fldt      16(%r13)
        addl      $16383, %eax
        andb      $127, 9(%rsp)
        andl      $32767, %eax
        movzwl    8(%rsp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 32(%r13)
        fxch      %st(2)
        fstpt     (%r13)
        fldt      (%r13)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        fldt      16(%r13)
        movzwl    8(%r13), %r8d
        jmp       ..B1.75
..B1.74:
        fldt      (%r13)
        fldt      16(%r13)
..B1.75:
        fldt      256(%rsp)
        movl      %edx, %eax
        andl      $32767, %r8d
        andl      $32767, %eax
        fld       %st(2)
        cmpl      %eax, %r8d
        movl      %r9d, 192(%rsp)
        fadd      %st(1), %st
        jg        ..B1.80
..B1.76:
        andl      $32767, %edx
        cmpl      %edx, %r8d
        jne       ..B1.81
..B1.77:
        movl      4(%r13), %eax
        cmpl      260(%rsp), %eax
        ja        ..B1.80
..B1.78:
        jne       ..B1.81
..B1.79:
        movl      (%r13), %eax
        cmpl      256(%rsp), %eax
        jbe       ..B1.81
..B1.80:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      272(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.82
..B1.81:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      272(%rsp)
        faddp     %st, %st(1)
..B1.82:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        jmp       ..B1.87
..B1.83:
        cmpq      $0, (%r13)
        je        ..B1.85
..B1.84:
        lea       -16517(%r9,%r10), %edx
        lea       -16383(%rax,%r11), %eax
        cmpl      %eax, %edx
        jl        ..B1.86
..B1.85:
        movaps    256(%rsp), %xmm0
        movaps    272(%rsp), %xmm1
        movaps    288(%rsp), %xmm2
        movaps    %xmm0, 160(%rsp)
        movaps    %xmm1, 176(%rsp)
        movaps    %xmm2, 192(%rsp)
        jmp       ..B1.87
..B1.86:
        movq      (%r13), %rax
        movq      8(%r13), %rdx
        movq      16(%r13), %rcx
        movq      24(%r13), %rsi
        movq      32(%r13), %r8
        movq      40(%r13), %r9
        movq      %rax, 160(%rsp)
        movq      %rdx, 168(%rsp)
        movq      %rcx, 176(%rsp)
        movq      %rsi, 184(%rsp)
        movq      %r8, 192(%rsp)
        movq      %r9, 200(%rsp)
..B1.87:
        movzwl    168(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.91
..B1.88:
        cmpl      $24383, %eax
        jg        ..B1.91
..B1.89:
        movzwl    72(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.91
..B1.90:
        cmpl      $24383, %eax
        jle       ..B1.93
..B1.91:
        lea       160(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.22:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.23:
..B1.92:
        lea       64(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.24:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.25:
..B1.93:
        fldt      160(%rsp)
        lea       t32(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        fldt      64(%rsp)
        fld       %st(0)
        fldt      (%rdx)
        fld       %st(0)
        fmul      %st(6), %st
        movq      696(%rsp), %r13
        fsubr     %st, %st(5)
        movzwl    8(%r13), %esi
        movl      %esi, %r11d
        movl      192(%rsp), %eax
        andl      $32767, %r11d
        addl      96(%rsp), %eax
        faddp     %st, %st(5)
        fld       %st(4)
        fld       %st(3)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        cmpl      $8383, %r11d
        fmul      %st(4), %st
        movl      %eax, 240(%rsp)
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(4)
        fsub      %st(5), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
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
        fadd      %st(1), %st
        fstpt     224(%rsp)
        fstpt     32(%rsp)
        jl        ..B1.97
..B1.94:
        cmpl      $24383, %r11d
        jg        ..B1.97
..B1.95:
        movzwl    8(%rbp), %r8d
        movl      %r8d, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B1.97
..B1.96:
        cmpl      $24383, %r10d
        jle       ..B1.100
..B1.97:
        movq      %r13, %rdi
..___tag_value___libm_recacoshl_k80.26:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.27:
..B1.98:
        movq      %rbp, %rdi
..___tag_value___libm_recacoshl_k80.28:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.29:
..B1.99:
        movzwl    8(%r13), %esi
        movl      %esi, %r11d
        movzwl    8(%rbp), %r8d
        movl      %r8d, %r10d
        andl      $32767, %r11d
        andl      $32767, %r10d
..B1.100:
        movl      32(%rbp), %r9d
        movl      32(%r13), %eax
        lea       -134(%r9,%r10), %edx
        lea       (%r11,%rax), %ecx
        cmpl      %edx, %ecx
        jle       ..B1.117
..B1.101:
        lea       134(%r9,%r10), %edx
        cmpl      %edx, %ecx
        jge       ..B1.117
..B1.102:
        cmpl      $0, 4(%r13)
        jne       ..B1.104
..B1.103:
        cmpl      $0, (%r13)
        je        ..B1.117
..B1.104:
        cmpl      $0, 4(%rbp)
        jne       ..B1.106
..B1.105:
        cmpl      $0, (%rbp)
        je        ..B1.117
..B1.106:
        cmpl      %r9d, %eax
        je        ..B1.108
..B1.107:
        fldt      32(%rsp)
        subl      %r9d, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      (%r13)
        andl      $32767, %eax
        fldt      16(%r13)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 32(%r13)
        fxch      %st(2)
        fstpt     (%r13)
        fldt      (%r13)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r13)
        fldt      16(%r13)
        movl      32(%rbp), %r9d
        movzwl    8(%rbp), %r8d
        movzwl    8(%r13), %esi
        jmp       ..B1.109
..B1.108:
        fldt      (%r13)
        fldt      16(%r13)
..B1.109:
        fldt      (%rbp)
        andl      $32767, %esi
        andl      $32767, %r8d
        fld       %st(2)
        movl      %r9d, 336(%rsp)
        cmpl      %r8d, %esi
        fadd      %st(1), %st
        jg        ..B1.114
..B1.110:
        jne       ..B1.115
..B1.111:
        movl      4(%r13), %eax
        cmpl      4(%rbp), %eax
        ja        ..B1.114
..B1.112:
        jne       ..B1.115
..B1.113:
        movl      (%r13), %eax
        cmpl      (%rbp), %eax
        jbe       ..B1.115
..B1.114:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      16(%rbp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.116
..B1.115:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      16(%rbp)
        faddp     %st, %st(1)
..B1.116:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     304(%rsp)
        fldt      304(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     320(%rsp)
        jmp       ..B1.121
..B1.117:
        cmpq      $0, (%r13)
        je        ..B1.119
..B1.118:
        lea       -16517(%r9,%r10), %edx
        lea       -16383(%rax,%r11), %eax
        cmpl      %eax, %edx
        jl        ..B1.120
..B1.119:
        movq      (%rbp), %rax
        movq      8(%rbp), %rdx
        movq      16(%rbp), %rcx
        movq      24(%rbp), %rsi
        movq      32(%rbp), %r8
        movq      40(%rbp), %rbp
        movq      %rax, 304(%rsp)
        movq      %rdx, 312(%rsp)
        movq      %rcx, 320(%rsp)
        movq      %rsi, 328(%rsp)
        movq      %r8, 336(%rsp)
        movq      %rbp, 344(%rsp)
        jmp       ..B1.121
..B1.120:
        movq      (%r13), %rax
        movq      8(%r13), %rdx
        movq      16(%r13), %rcx
        movq      24(%r13), %rbp
        movq      32(%r13), %rsi
        movq      40(%r13), %rdi
        movq      %rax, 304(%rsp)
        movq      %rdx, 312(%rsp)
        movq      %rcx, 320(%rsp)
        movq      %rbp, 328(%rsp)
        movq      %rsi, 336(%rsp)
        movq      %rdi, 344(%rsp)
..B1.121:
        movzwl    312(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.125
..B1.122:
        cmpl      $24383, %eax
        jg        ..B1.125
..B1.123:
        movzwl    120(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.125
..B1.124:
        cmpl      $24383, %eax
        jle       ..B1.127
..B1.125:
        lea       304(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.30:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.31:
..B1.126:
        lea       112(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.32:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.33:
..B1.127:
        fldt      304(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      336(%rsp), %ecx
        fsubr     %st, %st(3)
        movzwl    216(%rsp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      144(%rsp), %ecx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      112(%rsp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %ecx, 384(%rsp)
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
        fldt      320(%rsp)
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
        jl        ..B1.131
..B1.128:
        cmpl      $24383, %eax
        jg        ..B1.131
..B1.129:
        movzwl    360(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.131
..B1.130:
        cmpl      $24383, %eax
        jle       ..B1.134
..B1.131:
        fstp      %st(1)
        fstp      %st(0)
        lea       208(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.34:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.35:
..B1.132:
        lea       352(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.36:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.37:
..B1.133:
        fldt      352(%rsp)
        fldt      368(%rsp)
        movl      384(%rsp), %ecx
..B1.134:
        fldt      208(%rsp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      240(%rsp), %edx
        fsubr     %st(4), %st
        subl      %ecx, %edx
        faddp     %st, %st(1)
        fld       %st(0)
        decl      %edx
        movl      %edx, 432(%rsp)
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
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      224(%rsp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      48(%rsp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fsubrp    %st, %st(2)
        movzwl    408(%rsp), %esi
        movl      %esi, %eax
        andl      $32767, %eax
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     416(%rsp)
        cmpl      $8383, %eax
        fstpt     32(%rsp)
        jl        ..B1.138
..B1.135:
        cmpl      $24383, %eax
        jg        ..B1.138
..B1.136:
        movzwl    264(%rsp), %edi
        movl      %edi, %r9d
        andl      $32767, %r9d
        cmpl      $8383, %r9d
        jl        ..B1.138
..B1.137:
        cmpl      $24383, %r9d
        jle       ..B1.141
..B1.138:
        lea       400(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.38:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.39:
..B1.139:
        lea       256(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.40:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.41:
..B1.140:
        movzwl    264(%rsp), %edi
        movl      %edi, %r9d
        movzwl    408(%rsp), %esi
        movl      %esi, %eax
        movl      432(%rsp), %edx
        andl      $32767, %r9d
        andl      $32767, %eax
..B1.141:
        movl      288(%rsp), %r8d
        lea       (%rdx,%rax), %ebp
        lea       -134(%r8,%r9), %ecx
        cmpl      %ecx, %ebp
        jle       ..B1.158
..B1.142:
        lea       134(%r8,%r9), %ecx
        cmpl      %ecx, %ebp
        jge       ..B1.158
..B1.143:
        cmpl      $0, 404(%rsp)
        jne       ..B1.145
..B1.144:
        cmpl      $0, 400(%rsp)
        je        ..B1.158
..B1.145:
        cmpl      $0, 260(%rsp)
        jne       ..B1.147
..B1.146:
        cmpl      $0, 256(%rsp)
        je        ..B1.158
..B1.147:
        cmpl      %r8d, %edx
        je        ..B1.149
..B1.148:
        fldt      32(%rsp)
        subl      %r8d, %edx
        fstpt     (%rsp)
        addl      $16383, %edx
        fldt      400(%rsp)
        andl      $32767, %edx
        fldt      416(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edx, %eax
        movl      %r8d, %edx
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    408(%rsp), %esi
        movl      %esi, %eax
        andl      $32767, %eax
        movl      %r8d, 432(%rsp)
        movl      %eax, %ecx
        fstpt     416(%rsp)
        fldt      416(%rsp)
        jmp       ..B1.150
..B1.149:
        fldt      400(%rsp)
        movl      %esi, %ecx
        fldt      416(%rsp)
        andl      $32767, %ecx
..B1.150:
        fldt      256(%rsp)
        movl      %edi, %ebp
        andl      $32767, %ebp
        fld       %st(2)
        movl      %r8d, 480(%rsp)
        cmpl      %ebp, %ecx
        fadd      %st(1), %st
        jg        ..B1.155
..B1.151:
        andl      $32767, %esi
        andl      $32767, %edi
        cmpl      %edi, %esi
        jne       ..B1.156
..B1.152:
        movl      404(%rsp), %ecx
        cmpl      260(%rsp), %ecx
        ja        ..B1.155
..B1.153:
        jne       ..B1.156
..B1.154:
        movl      400(%rsp), %ecx
        cmpl      256(%rsp), %ecx
        jbe       ..B1.156
..B1.155:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      272(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.157
..B1.156:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      272(%rsp)
        faddp     %st, %st(1)
..B1.157:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     448(%rsp)
        fldt      448(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     464(%rsp)
        jmp       ..B1.162
..B1.158:
        cmpq      $0, 400(%rsp)
        je        ..B1.160
..B1.159:
        lea       -16517(%r8,%r9), %ecx
        lea       -16383(%rdx,%rax), %ebp
        cmpl      %ebp, %ecx
        jl        ..B1.161
..B1.160:
        movaps    256(%rsp), %xmm0
        movaps    272(%rsp), %xmm1
        movaps    288(%rsp), %xmm2
        movaps    %xmm0, 448(%rsp)
        movaps    %xmm1, 464(%rsp)
        movaps    %xmm2, 480(%rsp)
        jmp       ..B1.162
..B1.161:
        movaps    400(%rsp), %xmm0
        movaps    416(%rsp), %xmm1
        movaps    432(%rsp), %xmm2
        movaps    %xmm0, 448(%rsp)
        movaps    %xmm1, 464(%rsp)
        movaps    %xmm2, 480(%rsp)
..B1.162:
        cmpl      $8383, %eax
        jl        ..B1.166
..B1.163:
        cmpl      $24383, %eax
        jg        ..B1.166
..B1.164:
        movzwl    456(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.166
..B1.165:
        cmpl      $24383, %eax
        jle       ..B1.169
..B1.166:
        lea       400(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.42:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.43:
..B1.167:
        lea       448(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.44:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.45:
..B1.168:
        movl      432(%rsp), %edx
..B1.169:
        fldt      400(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        addl      480(%rsp), %edx
        fsubr     %st, %st(3)
        movl      %edx, 528(%rsp)
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      448(%rsp)
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
        fldt      464(%rsp)
        fmulp     %st, %st(4)
        fldt      416(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     496(%rsp)
        fldt      496(%rsp)
        fsubr     %st, %st(1)
        movzwl    504(%rsp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %eax
        fxch      %st(1)
        fstpt     512(%rsp)
        fldt      512(%rsp)
        cmpl      $383, %eax
        fxch      %st(1)
        fstpt     32(%rsp)
        jl        ..B1.171
..B1.170:
        cmpl      $32383, %eax
        jle       ..B1.173
..B1.171:
        fstp      %st(0)
        lea       496(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.46:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.47:
..B1.172:
        fldt      496(%rsp)
        fstpt     32(%rsp)
        fldt      512(%rsp)
        movl      528(%rsp), %edx
..B1.173:
        testb     $1, %dl
        jne       ..B1.208
..B1.174:
        lea       halfs(%rip), %rax
        fldt      (%rax)
..B1.175:
        fldt      32(%rsp)
        fld       %st(0)
        fsqrt     
        fldt      16(%rsp)
        fmul      %st(1), %st
        fld       %st(0)
        movzwl    408(%rsp), %ebp
        movl      %ebp, %r9d
        andl      $32767, %r9d
        fsubr     %st(2), %st
        sarl      $1, %edx
        faddp     %st, %st(1)
        fld       %st(0)
        cmpl      $8383, %r9d
        movl      %edx, 576(%rsp)
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
        fxch      %st(3)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     544(%rsp)
        fldt      544(%rsp)
        fsubrp    %st, %st(2)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     560(%rsp)
        fstpt     32(%rsp)
        jl        ..B1.179
..B1.176:
        cmpl      $24383, %r9d
        jg        ..B1.179
..B1.177:
        movzwl    552(%rsp), %esi
        movl      %esi, %edi
        andl      $32767, %edi
        cmpl      $8383, %edi
        jl        ..B1.179
..B1.178:
        cmpl      $24383, %edi
        jle       ..B1.182
..B1.179:
        lea       400(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.48:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.49:
..B1.180:
        lea       544(%rsp), %rdi
..___tag_value___libm_recacoshl_k80.50:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_recacoshl_k80.51:
..B1.181:
        movzwl    408(%rsp), %ebp
        movl      %ebp, %r9d
        movzwl    552(%rsp), %esi
        movl      %esi, %edi
        movl      576(%rsp), %edx
        andl      $32767, %r9d
        andl      $32767, %edi
..B1.182:
        movl      432(%rsp), %r8d
        lea       -134(%rdx,%rdi), %eax
        lea       (%r9,%r8), %ecx
        cmpl      %eax, %ecx
        jle       ..B1.199
..B1.183:
        lea       134(%rdx,%rdi), %eax
        cmpl      %eax, %ecx
        jge       ..B1.199
..B1.184:
        cmpl      $0, 404(%rsp)
        jne       ..B1.186
..B1.185:
        cmpl      $0, 400(%rsp)
        je        ..B1.199
..B1.186:
        cmpl      $0, 548(%rsp)
        jne       ..B1.188
..B1.187:
        cmpl      $0, 544(%rsp)
        je        ..B1.199
..B1.188:
        cmpl      %edx, %r8d
        je        ..B1.190
..B1.189:
        fldt      32(%rsp)
        subl      %edx, %r8d
        fstpt     (%rsp)
        addl      $16383, %r8d
        fldt      400(%rsp)
        andl      $32767, %r8d
        fldt      416(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %r8d, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %edx, 432(%rsp)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        movzwl    408(%rsp), %ebp
        jmp       ..B1.191
..B1.190:
        fldt      400(%rsp)
        fldt      416(%rsp)
..B1.191:
        fldt      544(%rsp)
        movl      %ebp, %eax
        movl      %edx, 48(%rsp)
        movl      %esi, %edx
        andl      $32767, %eax
        andl      $32767, %edx
        fld       %st(2)
        cmpl      %edx, %eax
        fadd      %st(1), %st
        jg        ..B1.196
..B1.192:
        andl      $32767, %ebp
        andl      $32767, %esi
        cmpl      %esi, %ebp
        jne       ..B1.197
..B1.193:
        movl      404(%rsp), %eax
        cmpl      548(%rsp), %eax
        ja        ..B1.196
..B1.194:
        jne       ..B1.197
..B1.195:
        movl      400(%rsp), %eax
        cmpl      544(%rsp), %eax
        jbe       ..B1.197
..B1.196:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      560(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B1.198
..B1.197:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      560(%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B1.198:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.203
..B1.199:
        cmpq      $0, 400(%rsp)
        je        ..B1.201
..B1.200:
        lea       -16517(%rdx,%rdi), %eax
        lea       -16383(%r8,%r9), %edx
        cmpl      %edx, %eax
        jl        ..B1.202
..B1.201:
        movaps    544(%rsp), %xmm0
        movaps    560(%rsp), %xmm1
        movaps    576(%rsp), %xmm2
        movaps    %xmm0, 16(%rsp)
        movaps    %xmm1, 32(%rsp)
        movaps    %xmm2, 48(%rsp)
        jmp       ..B1.203
..B1.202:
        movaps    400(%rsp), %xmm0
        movaps    416(%rsp), %xmm1
        movaps    432(%rsp), %xmm2
        movaps    %xmm0, 16(%rsp)
        movaps    %xmm1, 32(%rsp)
        movaps    %xmm2, 48(%rsp)
..B1.203:
        lea       592(%rsp), %rdi
        lea       16(%rsp), %rsi
..___tag_value___libm_recacoshl_k80.52:
        call      __libm_log1p_k80@PLT
..___tag_value___libm_recacoshl_k80.53:
..B1.204:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 720
        fldt      624(%rsp)
        movl      656(%rsp), %edi
        fstpt     (%rsp)
        fldt      640(%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_recacoshl_k80.55:
        call      __libm_scalbl_k80@PLT
..___tag_value___libm_recacoshl_k80.56:
..B1.211:
        addq      $32, %rsp
	.cfi_def_cfa_offset 688
..B1.205:
        movq      640(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.207
..B1.206:
        addq      $656, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 688
	.cfi_offset 6, -32
	.cfi_offset 13, -16
	.cfi_offset 15, -24
..B1.207:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.208:
        fldt      32(%rsp)
        incl      %edx
        lea       halfs(%rip), %rax
        movl      %edx, 528(%rsp)
        fldt      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     512(%rsp)
        fldt      512(%rsp)
        fxch      %st(1)
        fstpt     496(%rsp)
        fxch      %st(1)
        jmp       ..B1.175
        .align    16,0x90
	.cfi_endproc
	.type	__libm_recacoshl_k80,@function
	.size	__libm_recacoshl_k80,.-__libm_recacoshl_k80
	.data
# -- End  __libm_recacoshl_k80
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
