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
	.file "dpml_ux_bessel.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_bessel__
	.text
       .align    16,0x90
__dpml_ux_bessel__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B1.1:
	.cfi_startproc
..___tag_value___dpml_ux_bessel__.1:
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
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $136, %rsp
	.cfi_def_cfa_offset 192
        movq      %rsi, %r13
        movq      %fs:40, %rax
        movq      %rcx, %r14
        xorq      %rsp, %rax
        movq      %rdx, %r15
        movq      %rax, 120(%rsp)
        movq      %rdi, %rbp
        cmpq      $2, %r13
        jge       ..B1.53
..B1.2:
        lea       __bessel_x_table(%rip), %r10
        lea       (%r13,%r15), %rbx
        lea       (%rbx,%rbx,2), %r11
        movl      4(%rbp), %ecx
        cmpl      $5, %ecx
        movq      8(%rbp), %rax
        jg        ..B1.8
..B1.3:
        je        ..B1.50
..B1.4:
        movq      %r10, %rbx
        addq      19552(%r10,%r11,8), %rbx
        testl     %ecx, %ecx
        jl        ..B1.23
..B1.5:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B1.6
..B1.7:
        movq      8(%rbx), %r10
        sarq      $32, %r10
        andq      $1023, %r10
        addq      %r10, %rbx
..B1.6:
        cmpq      (%rbx), %rax
        jbe       ..B1.23
        jmp       ..B1.7
..B1.8:
        xorl      %edi, %edi
        movq      %rbp, %rsi
        movl      $2, %edx
        lea       96(%rsp), %rcx
..___tag_value___dpml_ux_bessel__.16:
        call      __dpml_divide__@PLT
..___tag_value___dpml_ux_bessel__.17:
..B1.9:
        testq     %r13, %r13
        jne       ..B1.11
..B1.10:
        movl      $10, %ebx
        lea       18168+__bessel_x_table(%rip), %rsi
        lea       18504+__bessel_x_table(%rip), %r12
        jmp       ..B1.12
..B1.11:
        movl      $9, %ebx
        lea       18872+__bessel_x_table(%rip), %rsi
        lea       19208+__bessel_x_table(%rip), %r12
..B1.12:
        movl      $9, %edx
        lea       96(%rsp), %rdi
        movq      $0x1000000000000044, %rcx
        lea       (%rsp), %r8
..___tag_value___dpml_ux_bessel__.18:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_bessel__.19:
..B1.13:
        movq      %r12, %rsi
        lea       96(%rsp), %rdi
        movq      %rbx, %rdx
        movl      $70, %ecx
        lea       24(%rsp), %r8
..___tag_value___dpml_ux_bessel__.20:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_bessel__.21:
..B1.14:
        movq      %rbp, %rdi
        lea       (%r13,%r13), %rsi
        negq      %rsi
        movl      $3, %edx
        incq      %rsi
        lea       48(%rsp), %rcx
        subq      %r15, %rsi
..___tag_value___dpml_ux_bessel__.22:
        call      __dpml_ux_sincos@PLT
..___tag_value___dpml_ux_bessel__.23:
..B1.15:
        lea       (%rsp), %rdi
        movq      %rdi, %rdx
        lea       48(%rsp), %rsi
..___tag_value___dpml_ux_bessel__.24:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_bessel__.25:
..B1.16:
        lea       24(%rsp), %rdi
        movq      %rdi, %rdx
        lea       72(%rsp), %rsi
..___tag_value___dpml_ux_bessel__.26:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_bessel__.27:
..B1.17:
        xorl      %edx, %edx
        movl      $1, %eax
        testq     %r13, %r13
        lea       (%rsp), %rdi
        cmove     %rax, %rdx
        lea       24(%rsp), %rsi
        movq      %rdi, %rcx
..___tag_value___dpml_ux_bessel__.28:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.29:
..B1.18:
        movl      $1, %esi
        lea       96(%rsp), %rdi
        lea       24(%rsp), %rdx
..___tag_value___dpml_ux_bessel__.30:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___dpml_ux_bessel__.31:
..B1.19:
        movq      %r14, %rdx
        lea       (%rsp), %rdi
        lea       24(%rsp), %rsi
..___tag_value___dpml_ux_bessel__.32:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_bessel__.33:
..B1.20:
        movq      120(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.47
..B1.21:
        addq      $136, %rsp
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
	.cfi_def_cfa_offset 192
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.23:
        movq      8(%rbx), %r12
        testq     $8388608, %r12
        jne       ..B1.25
..B1.24:
        movq      %rbp, %r15
        jmp       ..B1.28
..B1.25:
        movq      16(%rbx), %rax
        movq      %rbp, %rdi
        movq      %rax, 32(%rsp)
        lea       24(%rsp), %rsi
        movq      24(%rbx), %r10
        movl      $1, %edx
        movq      %r10, 16(%rsi)
        lea       (%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___dpml_ux_bessel__.54:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.55:
..B1.26:
        movq      32(%rbx), %rax
        lea       (%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       24(%rsp), %rsi
        movq      40(%rbx), %r10
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r10, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___dpml_ux_bessel__.56:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.57:
..B1.27:
        lea       (%rsp), %r15
..B1.28:
        movq      %r12, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r12
        je        ..B1.30
..B1.29:
        movq      %r12, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r12, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        movq      %r14, %r9
..___tag_value___dpml_ux_bessel__.58:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___dpml_ux_bessel__.59:
        jmp       ..B1.31
..B1.30:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r12, %rcx
        movq      %r14, %r8
..___tag_value___dpml_ux_bessel__.60:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_bessel__.61:
..B1.31:
        movq      %r12, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B1.33
..B1.32:
        decq      %rdx
        lea       24(%r14), %rsi
        movq      %r14, %rdi
        movq      %r14, %rcx
..___tag_value___dpml_ux_bessel__.62:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.63:
..B1.33:
        testq     $4194304, %r12
        je        ..B1.35
..B1.34:
        movq      %r15, %rdi
        movq      %r14, %rsi
        movq      %r14, %rdx
..___tag_value___dpml_ux_bessel__.64:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_bessel__.65:
..B1.35:
        testq     $8192, %r12
        je        ..B1.37
..B1.36:
        movslq    (%r14), %rax
        btcq      $31, %rax
        movl      %eax, (%r14)
..B1.37:
        testq     $2097152, %r12
        je        ..B1.45
..B1.38:
        cmpq      %rbp, %r15
        jne       ..B1.40
..B1.39:
        sarq      $58, %r12
        subl      %r12d, 4(%rbp)
..B1.40:
        cmpq      $1, %r13
        je        ..B1.48
..B1.41:
        movq      %rbp, %rdi
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       (%rsp), %rdx
..___tag_value___dpml_ux_bessel__.66:
        call      __dpml_ux_log__@PLT
..___tag_value___dpml_ux_bessel__.67:
..B1.42:
        movq      %rbp, %rdi
        movq      %r13, %rsi
        xorl      %edx, %edx
        lea       24(%rsp), %rcx
..___tag_value___dpml_ux_bessel__.68:
        call      __dpml_ux_bessel__
..___tag_value___dpml_ux_bessel__.69:
..B1.43:
        lea       (%rsp), %rsi
        movq      %rsi, %rdx
        lea       24(%rsp), %rdi
..___tag_value___dpml_ux_bessel__.70:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_bessel__.71:
..B1.44:
        movq      %r14, %rsi
        lea       (%rsp), %rdi
        movl      $1, %edx
        movq      %r14, %rcx
..___tag_value___dpml_ux_bessel__.72:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.73:
..B1.45:
        movq      120(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        je        ..B1.21
..B1.47:
        call      __stack_chk_fail@PLT
..B1.48:
        lea       19640+__bessel_x_table(%rip), %rdi
        movq      %rbp, %rsi
        movl      $2, %edx
        lea       24(%rsp), %rcx
..___tag_value___dpml_ux_bessel__.74:
        call      __dpml_divide__@PLT
..___tag_value___dpml_ux_bessel__.75:
..B1.49:
        movq      %r14, %rdi
        lea       24(%rsp), %rsi
        xorl      %edx, %edx
        movq      %r14, %rcx
..___tag_value___dpml_ux_bessel__.76:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_bessel__.77:
        jmp       ..B1.41
..B1.50:
        cmpq      19544(%r10,%r11,8), %rax
        ja        ..B1.8
..B1.51:
        movq      %r10, %rbx
        addq      19552(%r10,%r11,8), %rbx
        jmp       ..B1.5
..B1.53:
        movq      %rbp, %rdi
        movq      %r13, %rsi
        movq      %r15, %rdx
        movq      %r14, %rcx
..___tag_value___dpml_ux_bessel__.78:
        call      __dpml_ux_large_order_bessel__
..___tag_value___dpml_ux_bessel__.79:
..B1.54:
        movq      120(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.47
        jmp       ..B1.21
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_bessel__,@function
	.size	__dpml_ux_bessel__,.-__dpml_ux_bessel__
	.data
# -- End  __dpml_ux_bessel__
	.text
# -- Begin  __dpml_ux_large_order_bessel__
	.text
       .align    16,0x90
__dpml_ux_large_order_bessel__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B2.1:
	.cfi_startproc
..___tag_value___dpml_ux_large_order_bessel__.81:
..L82:

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
        subq      $184, %rsp
	.cfi_def_cfa_offset 240
        movq      %rdi, %r13
        movq      %rcx, %rbx
        movq      %rdx, %r15
        movq      %rsi, %rbp
        xorl      %edi, %edi
        movq      %r13, %rsi
        movl      $2, %edx
        movq      %fs:40, %rax
        lea       120(%rsp), %rcx
        xorq      %rsp, %rax
        movq      %rax, 48(%rcx)
..___tag_value___dpml_ux_large_order_bessel__.96:
        call      __dpml_divide__@PLT
..___tag_value___dpml_ux_large_order_bessel__.97:
..B2.2:
        movl      $2, %esi
        lea       48(%rsp), %rax
        movq      %rax, %rdi
        lea       72(%rsp), %r14
        movq      %rax, -8(%rax)
        lea       96(%rsp), %r12
        movq      %rbp, 8(%rax)
..___tag_value___dpml_ux_large_order_bessel__.98:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_large_order_bessel__.99:
..B2.57:
        movq      %rax, %rcx
..B2.3:
        movl      4(%r13), %r10d
        negq      %rcx
        movslq    %r10d, %r8
        addq      $64, %rcx
        negq      %r8
        pxor      %xmm0, %xmm0
        addq      %rcx, %r8
        pxor      %xmm1, %xmm1
        cvtsi2sdq %r8, %xmm0
        cvtsi2sdq %rcx, %xmm1
        subsd     .L_2il0floatpacket.97(%rip), %xmm0
        subsd     .L_2il0floatpacket.98(%rip), %xmm1
        pxor      %xmm2, %xmm2
        movq      %rcx, %rax
        cvtsi2sdq %rbp, %xmm2
        mulsd     .L_2il0floatpacket.96(%rip), %xmm1
        mulsd     %xmm0, %xmm2
        negq      %rax
        subsd     %xmm1, %xmm2
        incq      %rax
        pxor      %xmm1, %xmm1
        cvtsi2sdq %rax, %xmm1
        cmpq      $2, %r15
        subsd     %xmm2, %xmm1
        je        ..B2.53
..B2.4:
        movsd     .L_2il0floatpacket.100(%rip), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B2.8
..B2.5:
        movl      $-65536, %edx
        xorl      %eax, %eax
..B2.6:
        movl      %eax, (%rbx)
        movq      $0x8000000000000000, %rax
        movl      %edx, 4(%rbx)
        movq      %rax, 8(%rbx)
        movq      $0, 16(%rbx)
        movq      168(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B2.32
..B2.7:
        addq      $184, %rsp
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
	.cfi_def_cfa_offset 240
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.8:
        negq      %rcx
        movq      8(%r13), %r9
        movq      %r9, %rax
        shrq      %cl, %rax
        testq     %r8, %r8
        jle       ..B2.38
..B2.9:
        shrq      $1, %r9
        pxor      %xmm0, %xmm0
        cvtsi2ssq %r9, %xmm0
        movss     %xmm0, 32(%rsp)
        pxor      %xmm0, %xmm0
        cvtsi2ssq %rbp, %xmm0
        movss     %xmm0, 16(%rsp)
        movl      %r10d, (%rsp)
..___tag_value___dpml_ux_large_order_bessel__.120:
        call      log2f@PLT
..___tag_value___dpml_ux_large_order_bessel__.121:
..B2.58:
        movl      (%rsp), %r10d
        movss     %xmm0, 8(%rsp)
..B2.10:
        movss     .L_2il0floatpacket.101(%rip), %xmm0
        mulss     8(%rsp), %xmm0
        movss     .L_2il0floatpacket.108(%rip), %xmm1
        movl      %r10d, (%rsp)
        addss     .L_2il0floatpacket.102(%rip), %xmm0
        addss     16(%rsp), %xmm1
        addss     %xmm0, %xmm1
        movss     32(%rsp), %xmm0
        movss     %xmm1, 24(%rsp)
..___tag_value___dpml_ux_large_order_bessel__.122:
        call      log2f@PLT
..___tag_value___dpml_ux_large_order_bessel__.123:
..B2.59:
        movl      (%rsp), %r10d
        movss     %xmm0, 32(%rsp)
..B2.11:
        addl      $-64, %r10d
        pxor      %xmm0, %xmm0
        cvtsi2ss  %r10d, %xmm0
        movss     32(%rsp), %xmm3
        pxor      %xmm1, %xmm1
        pxor      %xmm4, %xmm4
        addss     %xmm0, %xmm3
        cvtss2sd  16(%rsp), %xmm4
        addss     .L_2il0floatpacket.103(%rip), %xmm3
        movss     %xmm3, 32(%rsp)
        cvtss2sd  %xmm3, %xmm1
        subss     8(%rsp), %xmm3
        movsd     .L_2il0floatpacket.96(%rip), %xmm2
        cvtss2sd  %xmm3, %xmm3
        mulsd     %xmm2, %xmm1
        addsd     %xmm2, %xmm4
        movsd     .L_2il0floatpacket.104(%rip), %xmm5
        mulsd     %xmm3, %xmm4
        subsd     %xmm1, %xmm5
        movss     24(%rsp), %xmm0
        subsd     %xmm4, %xmm5
        cvtsd2ss  %xmm5, %xmm5
        movss     %xmm5, (%rsp)
..___tag_value___dpml_ux_large_order_bessel__.124:
        call      log2f@PLT
..___tag_value___dpml_ux_large_order_bessel__.125:
..B2.12:
        pxor      %xmm2, %xmm2
        pxor      %xmm1, %xmm1
        cvtss2sd  %xmm0, %xmm2
        cvtss2sd  (%rsp), %xmm1
        subss     32(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.105(%rip), %xmm2
        cvtss2sd  %xmm0, %xmm0
        addsd     %xmm1, %xmm2
        divsd     %xmm0, %xmm2
        cvtsd2ss  %xmm2, %xmm2
        movss     24(%rsp), %xmm0
        movsd     %xmm1, 16(%rsp)
        addss     %xmm2, %xmm0
        cvtss2sd  %xmm0, %xmm0
        mulsd     .L_2il0floatpacket.96(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 8(%rsp)
..___tag_value___dpml_ux_large_order_bessel__.126:
        call      log2f@PLT
..___tag_value___dpml_ux_large_order_bessel__.127:
..B2.61:
        movsd     16(%rsp), %xmm1
..B2.13:
        pxor      %xmm2, %xmm2
        cvtss2sd  %xmm0, %xmm2
        subss     32(%rsp), %xmm0
        mulsd     .L_2il0floatpacket.105(%rip), %xmm2
        cvtss2sd  %xmm0, %xmm0
        addsd     %xmm1, %xmm2
        divsd     %xmm0, %xmm2
        cvtsd2ss  %xmm2, %xmm2
        movss     8(%rsp), %xmm0
        movsd     %xmm1, 16(%rsp)
        addss     %xmm2, %xmm0
        cvtss2sd  %xmm0, %xmm0
        mulsd     .L_2il0floatpacket.96(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, (%rsp)
..___tag_value___dpml_ux_large_order_bessel__.128:
        call      log2f@PLT
..___tag_value___dpml_ux_large_order_bessel__.129:
..B2.62:
        movsd     16(%rsp), %xmm1
        movaps    %xmm0, %xmm3
..B2.14:
        pxor      %xmm2, %xmm2
        movq      $0x8000000000000000, %r13
        cvtss2sd  %xmm3, %xmm2
        subss     32(%rsp), %xmm3
        movsd     .L_2il0floatpacket.105(%rip), %xmm0
        xorl      %r8d, %r8d
        mulsd     %xmm2, %xmm0
        xorl      %r9d, %r9d
        cvtss2sd  %xmm3, %xmm3
        addsd     %xmm0, %xmm1
        divsd     %xmm3, %xmm1
        cvtsd2ss  %xmm1, %xmm1
        movss     (%rsp), %xmm4
        movsd     .L_2il0floatpacket.96(%rip), %xmm5
        addss     %xmm1, %xmm4
        cvtss2sd  %xmm4, %xmm4
        mulsd     %xmm4, %xmm5
        cvtsd2ss  %xmm5, %xmm5
        cvtss2sd  %xmm5, %xmm5
        movsd     .L_2il0floatpacket.109(%rip), %xmm7
        movaps    %xmm7, %xmm6
        addsd     .L_2il0floatpacket.106(%rip), %xmm5
        comisd    %xmm7, %xmm5
        cmplesd   %xmm5, %xmm6
        movaps    %xmm5, %xmm8
        movd      %xmm6, %r15
        subsd     %xmm7, %xmm8
        jae       ..L130
        movaps    %xmm5, %xmm8
..L130:
        cvttsd2si %xmm8, %rax
        andq      %r13, %r15
        addq      %r15, %rax
        lea       1(%rbp), %r15
        cmpq      %r15, %rax
        movl      %r8d, 48(%rsp)
        cmova     %rax, %r15
        movl      $-262144, 52(%rsp)
        testq     $1, %r15
        movq      %r9, 56(%rsp)
        movq      %r9, 64(%rsp)
        movl      %r8d, 72(%rsp)
        movl      $1, 76(%rsp)
        movq      %r13, 80(%rsp)
        movq      %r9, 88(%rsp)
        movl      %r8d, 8(%rsp)
        je        ..B2.16
..B2.15:
        movl      $-262144, 12(%rsp)
        movq      %r9, 16(%rsp)
        movq      %r9, 24(%rsp)
        jmp       ..B2.17
..B2.16:
        movl      $1, 12(%rsp)
        movq      %r13, 16(%rsp)
        movq      $0, 24(%rsp)
..B2.17:
        movl      $2, %esi
        lea       144(%rsp), %rdi
        lea       (%r15,%r15), %rax
        movq      %rax, 8(%rdi)
..___tag_value___dpml_ux_large_order_bessel__.131:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_large_order_bessel__.132:
..B2.18:
        movl      148(%rsp), %ecx
        movq      %r13, %rax
        addl      $62, %ecx
        shrq      %cl, %rax
        movq      %rbp, (%rsp)
        movq      %rax, %r13
        movq      40(%rsp), %rbp
..B2.19:
        movq      %r12, %rdx
        lea       120(%rsp), %rdi
        lea       144(%rsp), %rsi
..___tag_value___dpml_ux_large_order_bessel__.133:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_large_order_bessel__.134:
..B2.20:
        movq      %r14, %rdi
        movq      %r12, %rsi
        movq      %r12, %rdx
..___tag_value___dpml_ux_large_order_bessel__.135:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_large_order_bessel__.136:
..B2.21:
        movq      %r12, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_ux_large_order_bessel__.137:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_large_order_bessel__.138:
..B2.22:
        movq      %rbp, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_ux_large_order_bessel__.139:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_large_order_bessel__.140:
..B2.23:
        movq      %r12, %rdi
        movq      %rbp, %rsi
        movl      $1, %edx
        movq      %r12, %rcx
..___tag_value___dpml_ux_large_order_bessel__.141:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_large_order_bessel__.142:
..B2.24:
        decq      %r15
        je        ..B2.33
..B2.25:
        cmpq      (%rsp), %r15
        jne       ..B2.27
..B2.26:
        movl      (%r12), %r8d
        movl      4(%r12), %r9d
        movq      8(%r12), %r10
        movq      16(%r12), %r11
        movl      %r8d, (%rbx)
        movl      %r9d, 4(%rbx)
        movq      %r10, 8(%rbx)
        movq      %r11, 16(%rbx)
..B2.27:
        testq     $1, %r15
        jne       ..B2.29
..B2.28:
        movq      %r12, %rsi
        lea       8(%rsp), %rdi
        xorl      %edx, %edx
        movq      %rdi, %rcx
..___tag_value___dpml_ux_large_order_bessel__.143:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_large_order_bessel__.144:
..B2.29:
        movq      %rbp, %r8
        movq      %r14, %rbp
        movq      %r12, %r14
        movq      %r8, %r12
        movq      152(%rsp), %r8
        movq      $0x8000000000000000, %r9
        subq      %r13, %r8
        cmpq      %r9, %r8
        jae       ..B2.31
..B2.30:
        decl      148(%rsp)
        addq      %r8, %r8
        addq      %r13, %r13
..B2.31:
        movq      %r8, 152(%rsp)
        jmp       ..B2.19
..B2.32:
        call      __stack_chk_fail@PLT
..B2.33:
        movq      %r12, %rdi
        lea       8(%rsp), %rsi
        xorl      %edx, %edx
        movq      %rsi, %rcx
        incl      4(%rsi)
..___tag_value___dpml_ux_large_order_bessel__.145:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_large_order_bessel__.146:
..B2.34:
        movq      %rbx, %rdi
        lea       8(%rsp), %rsi
        movl      $2, %edx
        movq      %rbx, %rcx
..___tag_value___dpml_ux_large_order_bessel__.147:
        call      __dpml_divide__@PLT
..___tag_value___dpml_ux_large_order_bessel__.148:
..B2.35:
        movq      168(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.32
        jmp       ..B2.7
..B2.38:
        jne       ..B2.40
..B2.39:
        cmpq      %rbp, %rax
        jb        ..B2.9
..B2.40:
        movq      %r13, %rdi
        xorl      %esi, %esi
        movq      %r15, %rdx
        lea       48(%rsp), %rcx
..___tag_value___dpml_ux_large_order_bessel__.149:
        call      __dpml_ux_bessel__
..___tag_value___dpml_ux_large_order_bessel__.150:
..B2.41:
        movq      %r13, %rdi
        movl      $1, %esi
        movq      %r15, %rdx
        movq      %r14, %rcx
..___tag_value___dpml_ux_large_order_bessel__.151:
        call      __dpml_ux_bessel__
..___tag_value___dpml_ux_large_order_bessel__.152:
..B2.42:
        movq      $0x8000000000000000, %r15
        decq      %rbp
        movq      %rbx, (%rsp)
        movq      %r15, %r13
        movl      $0, 144(%rsp)
        movl      $2, 148(%rsp)
        movq      %r15, 152(%rsp)
        movq      $0, 160(%rsp)
        movq      40(%rsp), %rbx
..B2.43:
        movq      %r12, %rdx
        lea       120(%rsp), %rdi
        lea       144(%rsp), %rsi
..___tag_value___dpml_ux_large_order_bessel__.153:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_large_order_bessel__.154:
..B2.44:
        movq      %r14, %rdi
        movq      %r12, %rsi
        movq      %r12, %rdx
..___tag_value___dpml_ux_large_order_bessel__.155:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_large_order_bessel__.156:
..B2.45:
        movq      %r12, %rdi
        movq      %rbx, %rsi
        movl      $1, %edx
        movq      %r12, %rcx
..___tag_value___dpml_ux_large_order_bessel__.157:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_large_order_bessel__.158:
..B2.46:
        decq      %rbp
        testq     %rbp, %rbp
        jle       ..B2.50
..B2.47:
        movq      %rbx, %rax
        movq      %r14, %rbx
        movq      %r12, %r14
        movq      %rax, %r12
        movq      152(%rsp), %rax
        addq      %r13, %rax
        cmpq      %r13, %rax
        jae       ..B2.49
..B2.48:
        shrq      $1, %rax
        incl      148(%rsp)
        addq      %r15, %rax
        shrq      $1, %r13
..B2.49:
        movq      %rax, 152(%rsp)
        jmp       ..B2.43
..B2.50:
        movq      (%rsp), %rbx
        movl      (%r12), %eax
        movl      4(%r12), %ebp
        movq      8(%r12), %r8
        movq      16(%r12), %rdx
        movl      %eax, (%rbx)
        movl      %ebp, 4(%rbx)
        movq      %r8, 8(%rbx)
        movq      %rdx, 16(%rbx)
        movq      168(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B2.32
        jmp       ..B2.7
..B2.53:
        comisd    .L_2il0floatpacket.99(%rip), %xmm2
        jbe       ..B2.40
..B2.54:
        movl      $65536, %edx
        movq      $0x080000000, %rax
        jmp       ..B2.6
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_large_order_bessel__,@function
	.size	__dpml_ux_large_order_bessel__,.-__dpml_ux_large_order_bessel__
	.data
# -- End  __dpml_ux_large_order_bessel__
	.text
# -- Begin  __j0q
	.text
       .align    16,0x90
	.globl __j0q
__j0q:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value___j0q.160:
..L161:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        subq      $368, %rsp
	.cfi_def_cfa_offset 400
        xorl      %esi, %esi
        lea       __bessel_x_table(%rip), %rcx
        lea       48(%rsp), %rdi
        movq      %fs:40, %rax
        lea       304(%rsp), %rdx
        xorq      %rsp, %rax
        lea       336(%rsp), %r8
        movaps    %xmm0, (%rdi)
        lea       (%rsp), %r9
        movq      %rax, 48(%rdx)
        movq      $1, -40(%rdi)
        movq      $0, (%r9)
..___tag_value___j0q.169:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___j0q.170:
..B3.2:
        testq     %rax, %rax
        jl        ..B3.20
..B3.4:
        movl      308(%rsp), %ecx
        lea       19544+__bessel_x_table(%rip), %rdi
        movq      312(%rsp), %rax
        cmpl      $5, %ecx
        movl      $0, 304(%rsp)
        jg        ..B3.10
..B3.5:
        je        ..B3.44
..B3.6:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        testl     %ecx, %ecx
        jl        ..B3.23
..B3.7:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B3.8
..B3.9:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B3.8:
        cmpq      (%rbx), %rax
        jbe       ..B3.23
        jmp       ..B3.9
..B3.10:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___j0q.171:
        call      __dpml_divide__@PLT
..___tag_value___j0q.172:
..B3.11:
        lea       18168+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___j0q.173:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j0q.174:
..B3.12:
        lea       18504+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $10, %edx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___j0q.175:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j0q.176:
..B3.13:
        movl      $1, %esi
        lea       304(%rsp), %rdi
        movl      $3, %edx
        lea       112(%rsp), %rcx
..___tag_value___j0q.177:
        call      __dpml_ux_sincos@PLT
..___tag_value___j0q.178:
..B3.14:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___j0q.179:
        call      __dpml_multiply__@PLT
..___tag_value___j0q.180:
..B3.15:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___j0q.181:
        call      __dpml_multiply__@PLT
..___tag_value___j0q.182:
..B3.16:
        movl      $1, %edx
        lea       64(%rsp), %rdi
        movq      %rdi, %rcx
        lea       88(%rsp), %rsi
..___tag_value___j0q.183:
        call      __dpml_addsub__@PLT
..___tag_value___j0q.184:
..B3.17:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___j0q.185:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___j0q.186:
..B3.18:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___j0q.187:
        call      __dpml_multiply__@PLT
..___tag_value___j0q.188:
..B3.19:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        xorl      %ecx, %ecx
        lea       336(%rsp), %rsi
        lea       (%rsp), %r8
..___tag_value___j0q.189:
        call      __dpml_pack__@PLT
..___tag_value___j0q.190:
..B3.20:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B3.46
..B3.21:
        addq      $368, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 400
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B3.23:
        movq      8(%rbx), %r14
        testq     $8388608, %r14
        jne       ..B3.25
..B3.24:
        lea       304(%rsp), %r15
        jmp       ..B3.28
..B3.25:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___j0q.202:
        call      __dpml_addsub__@PLT
..___tag_value___j0q.203:
..B3.26:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___j0q.204:
        call      __dpml_addsub__@PLT
..___tag_value___j0q.205:
..B3.27:
        lea       184(%rsp), %r15
..B3.28:
        movq      %r14, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r14
        je        ..B3.30
..B3.29:
        movq      %r14, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r14, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___j0q.206:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___j0q.207:
        jmp       ..B3.31
..B3.30:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r14, %rcx
        lea       256(%rsp), %r8
..___tag_value___j0q.208:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j0q.209:
..B3.31:
        movq      %r14, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B3.33
..B3.32:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___j0q.210:
        call      __dpml_addsub__@PLT
..___tag_value___j0q.211:
..B3.33:
        testq     $4194304, %r14
        je        ..B3.35
..B3.34:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___j0q.212:
        call      __dpml_multiply__@PLT
..___tag_value___j0q.213:
..B3.35:
        testq     $8192, %r14
        je        ..B3.37
..B3.36:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B3.37:
        testq     $2097152, %r14
        je        ..B3.19
..B3.38:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B3.40
..B3.39:
        sarq      $58, %r14
        subl      %r14d, 308(%rsp)
..B3.40:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___j0q.214:
        call      __dpml_ux_log__@PLT
..___tag_value___j0q.215:
..B3.41:
        xorl      %esi, %esi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___j0q.216:
        call      __dpml_ux_bessel__
..___tag_value___j0q.217:
..B3.42:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___j0q.218:
        call      __dpml_multiply__@PLT
..___tag_value___j0q.219:
..B3.43:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___j0q.220:
        call      __dpml_addsub__@PLT
..___tag_value___j0q.221:
        jmp       ..B3.19
..B3.44:
        cmpq      (%rdi), %rax
        ja        ..B3.10
..B3.45:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        jmp       ..B3.7
..B3.46:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__j0q,@function
	.size	__j0q,.-__j0q
	.data
# -- End  __j0q
	.text
# -- Begin  __j1q
	.text
       .align    16,0x90
	.globl __j1q
__j1q:
# parameter 1: %xmm0
..B4.1:
	.cfi_startproc
..___tag_value___j1q.223:
..L224:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $360, %rsp
	.cfi_def_cfa_offset 400
        xorl      %esi, %esi
        movaps    %xmm0, 48(%rsp)
        lea       48(%rsp), %rdi
        movq      8(%rdi), %rbp
        lea       304(%rsp), %rdx
        shrq      $32, %rbp
        lea       8+__bessel_x_table(%rip), %rcx
        movq      %fs:40, %rax
        lea       336(%rsp), %r8
        xorq      %rsp, %rax
        lea       (%rsp), %r9
        movq      %rax, 48(%rdx)
        andl      $-2147483648, %ebp
        movq      $1, -40(%rdi)
        movq      $0, (%r9)
..___tag_value___j1q.234:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___j1q.235:
..B4.2:
        testq     %rax, %rax
        jge       ..B4.4
..B4.3:
        movq      $0x7fff000000000000, %rax
        movslq    %ebp, %rbp
        movq      %rax, %rdx
        movq      344(%rsp), %rcx
        andq      %rcx, %rdx
        shlq      $32, %rbp
        orq       %rcx, %rbp
        cmpq      %rdx, %rax
        cmovne    %rbp, %rcx
        movq      %rcx, 344(%rsp)
        jmp       ..B4.20
..B4.4:
        movl      308(%rsp), %ecx
        lea       19568+__bessel_x_table(%rip), %rdi
        movq      312(%rsp), %rax
        cmpl      $5, %ecx
        movl      $0, 304(%rsp)
        jg        ..B4.10
..B4.5:
        je        ..B4.46
..B4.6:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        testl     %ecx, %ecx
        jl        ..B4.23
..B4.7:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B4.8
..B4.9:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B4.8:
        cmpq      (%rbx), %rax
        jbe       ..B4.23
        jmp       ..B4.9
..B4.10:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___j1q.236:
        call      __dpml_divide__@PLT
..___tag_value___j1q.237:
..B4.11:
        lea       18872+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___j1q.238:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j1q.239:
..B4.12:
        lea       19208+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___j1q.240:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j1q.241:
..B4.13:
        movq      $-1, %rsi
        lea       304(%rsp), %rdi
        movl      $3, %edx
        lea       112(%rsp), %rcx
..___tag_value___j1q.242:
        call      __dpml_ux_sincos@PLT
..___tag_value___j1q.243:
..B4.14:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___j1q.244:
        call      __dpml_multiply__@PLT
..___tag_value___j1q.245:
..B4.15:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___j1q.246:
        call      __dpml_multiply__@PLT
..___tag_value___j1q.247:
..B4.16:
        xorl      %edx, %edx
        lea       64(%rsp), %rdi
        movq      %rdi, %rcx
        lea       88(%rsp), %rsi
..___tag_value___j1q.248:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.249:
..B4.17:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___j1q.250:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___j1q.251:
..B4.18:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___j1q.252:
        call      __dpml_multiply__@PLT
..___tag_value___j1q.253:
..B4.19:
        movl      $8, %eax
        xorl      %ecx, %ecx
        xorl      %ebp, 256(%rsp)
        cmovne    %eax, %ecx
        movl      $513, %ebx
        lea       __bessel_error_codes(%rip), %r8
        shrq      %cl, %rbx
        lea       256(%rsp), %rdi
        movzbl    %bl, %ebp
        lea       336(%rsp), %rsi
        movq      (%r8), %rcx
        movq      (%r8,%rbp,8), %rdx
        lea       (%rsp), %r8
..___tag_value___j1q.254:
        call      __dpml_pack__@PLT
..___tag_value___j1q.255:
..B4.20:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B4.48
..B4.21:
        addq      $360, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 400
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 15, -24
..B4.23:
        movq      8(%rbx), %r12
        testq     $8388608, %r12
        jne       ..B4.25
..B4.24:
        lea       304(%rsp), %r15
        jmp       ..B4.28
..B4.25:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___j1q.270:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.271:
..B4.26:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___j1q.272:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.273:
..B4.27:
        lea       184(%rsp), %r15
..B4.28:
        movq      %r12, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r12
        je        ..B4.30
..B4.29:
        movq      %r12, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r12, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___j1q.274:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___j1q.275:
        jmp       ..B4.31
..B4.30:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r12, %rcx
        lea       256(%rsp), %r8
..___tag_value___j1q.276:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___j1q.277:
..B4.31:
        movq      %r12, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B4.33
..B4.32:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___j1q.278:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.279:
..B4.33:
        testq     $4194304, %r12
        je        ..B4.35
..B4.34:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___j1q.280:
        call      __dpml_multiply__@PLT
..___tag_value___j1q.281:
..B4.35:
        testq     $8192, %r12
        je        ..B4.37
..B4.36:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B4.37:
        testq     $2097152, %r12
        je        ..B4.19
..B4.38:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B4.40
..B4.39:
        sarq      $58, %r12
        subl      %r12d, 308(%rsp)
..B4.40:
        lea       19640+__bessel_x_table(%rip), %rdi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       208(%rsp), %rcx
..___tag_value___j1q.282:
        call      __dpml_divide__@PLT
..___tag_value___j1q.283:
..B4.41:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       208(%rsp), %rsi
..___tag_value___j1q.284:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.285:
..B4.42:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___j1q.286:
        call      __dpml_ux_log__@PLT
..___tag_value___j1q.287:
..B4.43:
        movl      $1, %esi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___j1q.288:
        call      __dpml_ux_bessel__
..___tag_value___j1q.289:
..B4.44:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___j1q.290:
        call      __dpml_multiply__@PLT
..___tag_value___j1q.291:
..B4.45:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___j1q.292:
        call      __dpml_addsub__@PLT
..___tag_value___j1q.293:
        jmp       ..B4.19
..B4.46:
        cmpq      (%rdi), %rax
        ja        ..B4.10
..B4.47:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        jmp       ..B4.7
..B4.48:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__j1q,@function
	.size	__j1q,.-__j1q
	.data
# -- End  __j1q
	.text
# -- Begin  __jnq
	.text
       .align    16,0x90
	.globl __jnq
__jnq:
# parameter 1: %edi
# parameter 2: %xmm0
..B5.1:
	.cfi_startproc
..___tag_value___jnq.295:
..L296:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $368, %rsp
	.cfi_def_cfa_offset 416
        movq      $0x8000000000000000, %rbp
        movaps    %xmm0, 48(%rsp)
        andq      56(%rsp), %rbp
        movslq    %edi, %r13
        shrq      $32, %rbp
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 352(%rsp)
        testq     %r13, %r13
        movq      $1, 8(%rsp)
        movq      $0, (%rsp)
        jge       ..B5.3
..B5.2:
        negq      %r13
        xorl      $-2147483648, %ebp
..B5.3:
        xorl      %r12d, %r12d
        testq     $1, %r13
        lea       48(%rsp), %rdi
        cmove     %r12d, %ebp
        movl      $2, %eax
        cmpq      $2, %r13
        lea       __bessel_x_table(%rip), %r12
        cmovle    %r13, %rax
        xorl      %esi, %esi
        lea       304(%rsp), %rdx
        andl      $-2147483648, %ebp
        lea       336(%rsp), %r8
        lea       (%rsp), %r9
        lea       (%r12,%rax,8), %rcx
..___tag_value___jnq.308:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___jnq.309:
..B5.4:
        testq     %rax, %rax
        jge       ..B5.6
..B5.5:
        movq      $0x7fff000000000000, %rax
        movslq    %ebp, %rbp
        movq      %rax, %rdx
        movq      344(%rsp), %rcx
        andq      %rcx, %rdx
        shlq      $32, %rbp
        orq       %rcx, %rbp
        cmpq      %rdx, %rax
        cmovne    %rbp, %rcx
        movq      %rcx, 344(%rsp)
        jmp       ..B5.26
..B5.6:
        movl      $0, 304(%rsp)
        cmpq      $2, %r13
        jge       ..B5.56
..B5.7:
        movl      308(%rsp), %ecx
        lea       (%r13,%r13,2), %r8
        movq      312(%rsp), %rax
        cmpl      $5, %ecx
        jg        ..B5.13
..B5.8:
        je        ..B5.53
..B5.9:
        movq      %r12, %rbx
        addq      19552(%r12,%r8,8), %rbx
        testl     %ecx, %ecx
        jl        ..B5.29
..B5.10:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B5.11
..B5.12:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B5.11:
        cmpq      (%rbx), %rax
        jbe       ..B5.29
        jmp       ..B5.12
..B5.13:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___jnq.310:
        call      __dpml_divide__@PLT
..___tag_value___jnq.311:
..B5.14:
        testq     %r13, %r13
        jne       ..B5.16
..B5.15:
        movl      $10, %r12d
        lea       18168+__bessel_x_table(%rip), %rsi
        lea       18504+__bessel_x_table(%rip), %rbx
        jmp       ..B5.17
..B5.16:
        movl      $9, %r12d
        lea       18872+__bessel_x_table(%rip), %rsi
        lea       19208+__bessel_x_table(%rip), %rbx
..B5.17:
        movl      $9, %edx
        lea       160(%rsp), %rdi
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___jnq.312:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___jnq.313:
..B5.18:
        movq      %rbx, %rsi
        lea       160(%rsp), %rdi
        movq      %r12, %rdx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___jnq.314:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___jnq.315:
..B5.19:
        movl      $3, %edx
        lea       (%r13,%r13), %rsi
        negq      %rsi
        lea       304(%rsp), %rdi
        incq      %rsi
        lea       112(%rsp), %rcx
..___tag_value___jnq.316:
        call      __dpml_ux_sincos@PLT
..___tag_value___jnq.317:
..B5.20:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___jnq.318:
        call      __dpml_multiply__@PLT
..___tag_value___jnq.319:
..B5.21:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___jnq.320:
        call      __dpml_multiply__@PLT
..___tag_value___jnq.321:
..B5.22:
        movl      $1, %eax
        xorl      %edx, %edx
        testq     %r13, %r13
        lea       64(%rsp), %rdi
        cmove     %rax, %rdx
        lea       88(%rsp), %rsi
        movq      %rdi, %rcx
..___tag_value___jnq.322:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.323:
..B5.23:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___jnq.324:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___jnq.325:
..B5.24:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___jnq.326:
        call      __dpml_multiply__@PLT
..___tag_value___jnq.327:
..B5.25:
        xorl      %ecx, %ecx
        movl      $8, %eax
        xorl      %ebp, 256(%rsp)
        cmovne    %eax, %ecx
        movl      $1027, %ebx
        lea       __bessel_error_codes(%rip), %r8
        shrq      %cl, %rbx
        lea       256(%rsp), %rdi
        movzbl    %bl, %ebp
        lea       336(%rsp), %rsi
        movq      (%r8), %rcx
        movq      (%r8,%rbp,8), %rdx
        lea       (%rsp), %r8
..___tag_value___jnq.328:
        call      __dpml_pack__@PLT
..___tag_value___jnq.329:
..B5.26:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B5.55
..B5.27:
        addq      $368, %rsp
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
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 416
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 15, -32
..B5.29:
        movq      8(%rbx), %r12
        testq     $8388608, %r12
        jne       ..B5.31
..B5.30:
        lea       304(%rsp), %r15
        jmp       ..B5.34
..B5.31:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___jnq.347:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.348:
..B5.32:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___jnq.349:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.350:
..B5.33:
        lea       184(%rsp), %r15
..B5.34:
        movq      %r12, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r12
        je        ..B5.36
..B5.35:
        movq      %r12, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r12, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___jnq.351:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___jnq.352:
        jmp       ..B5.37
..B5.36:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r12, %rcx
        lea       256(%rsp), %r8
..___tag_value___jnq.353:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___jnq.354:
..B5.37:
        movq      %r12, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B5.39
..B5.38:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___jnq.355:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.356:
..B5.39:
        testq     $4194304, %r12
        je        ..B5.41
..B5.40:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___jnq.357:
        call      __dpml_multiply__@PLT
..___tag_value___jnq.358:
..B5.41:
        testq     $8192, %r12
        je        ..B5.43
..B5.42:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B5.43:
        testq     $2097152, %r12
        je        ..B5.25
..B5.44:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B5.46
..B5.45:
        sarq      $58, %r12
        subl      %r12d, 308(%rsp)
..B5.46:
        cmpq      $1, %r13
        je        ..B5.51
..B5.47:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___jnq.359:
        call      __dpml_ux_log__@PLT
..___tag_value___jnq.360:
..B5.48:
        movq      %r13, %rsi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___jnq.361:
        call      __dpml_ux_bessel__
..___tag_value___jnq.362:
..B5.49:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___jnq.363:
        call      __dpml_multiply__@PLT
..___tag_value___jnq.364:
..B5.50:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___jnq.365:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.366:
        jmp       ..B5.25
..B5.51:
        lea       19640+__bessel_x_table(%rip), %rdi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       208(%rsp), %rcx
..___tag_value___jnq.367:
        call      __dpml_divide__@PLT
..___tag_value___jnq.368:
..B5.52:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       208(%rsp), %rsi
..___tag_value___jnq.369:
        call      __dpml_addsub__@PLT
..___tag_value___jnq.370:
        jmp       ..B5.47
..B5.53:
        cmpq      19544(%r12,%r8,8), %rax
        ja        ..B5.13
..B5.54:
        movq      %r12, %rbx
        addq      19552(%r12,%r8,8), %rbx
        jmp       ..B5.10
..B5.55:
        call      __stack_chk_fail@PLT
..B5.56:
        movq      %r13, %rsi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       256(%rsp), %rcx
..___tag_value___jnq.371:
        call      __dpml_ux_large_order_bessel__
..___tag_value___jnq.372:
        jmp       ..B5.25
        .align    16,0x90
	.cfi_endproc
	.type	__jnq,@function
	.size	__jnq,.-__jnq
	.data
# -- End  __jnq
	.text
# -- Begin  __y0q
	.text
       .align    16,0x90
	.globl __y0q
__y0q:
# parameter 1: %xmm0
..B6.1:
	.cfi_startproc
..___tag_value___y0q.374:
..L375:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        subq      $368, %rsp
	.cfi_def_cfa_offset 400
        xorl      %esi, %esi
        lea       40+__bessel_x_table(%rip), %rcx
        lea       48(%rsp), %rdi
        movq      %fs:40, %rax
        lea       304(%rsp), %rdx
        xorq      %rsp, %rax
        lea       336(%rsp), %r8
        movaps    %xmm0, (%rdi)
        lea       (%rsp), %r9
        movq      %rax, 48(%rdx)
        movq      $1, -40(%rdi)
        movq      $0, (%r9)
..___tag_value___y0q.383:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___y0q.384:
..B6.2:
        testq     %rax, %rax
        jl        ..B6.20
..B6.4:
        movl      308(%rsp), %ecx
        lea       19592+__bessel_x_table(%rip), %rdi
        movq      312(%rsp), %rax
        cmpl      $5, %ecx
        movl      $0, 304(%rsp)
        jg        ..B6.10
..B6.5:
        je        ..B6.44
..B6.6:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        testl     %ecx, %ecx
        jl        ..B6.23
..B6.7:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B6.8
..B6.9:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B6.8:
        cmpq      (%rbx), %rax
        jbe       ..B6.23
        jmp       ..B6.9
..B6.10:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___y0q.385:
        call      __dpml_divide__@PLT
..___tag_value___y0q.386:
..B6.11:
        lea       18168+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___y0q.387:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y0q.388:
..B6.12:
        lea       18504+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $10, %edx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___y0q.389:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y0q.390:
..B6.13:
        movq      $-1, %rsi
        lea       304(%rsp), %rdi
        movl      $3, %edx
        lea       112(%rsp), %rcx
..___tag_value___y0q.391:
        call      __dpml_ux_sincos@PLT
..___tag_value___y0q.392:
..B6.14:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___y0q.393:
        call      __dpml_multiply__@PLT
..___tag_value___y0q.394:
..B6.15:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___y0q.395:
        call      __dpml_multiply__@PLT
..___tag_value___y0q.396:
..B6.16:
        movl      $1, %edx
        lea       64(%rsp), %rdi
        movq      %rdi, %rcx
        lea       88(%rsp), %rsi
..___tag_value___y0q.397:
        call      __dpml_addsub__@PLT
..___tag_value___y0q.398:
..B6.17:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___y0q.399:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___y0q.400:
..B6.18:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___y0q.401:
        call      __dpml_multiply__@PLT
..___tag_value___y0q.402:
..B6.19:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        xorl      %ecx, %ecx
        lea       336(%rsp), %rsi
        lea       (%rsp), %r8
..___tag_value___y0q.403:
        call      __dpml_pack__@PLT
..___tag_value___y0q.404:
..B6.20:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B6.46
..B6.21:
        addq      $368, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 400
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B6.23:
        movq      8(%rbx), %r14
        testq     $8388608, %r14
        jne       ..B6.25
..B6.24:
        lea       304(%rsp), %r15
        jmp       ..B6.28
..B6.25:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___y0q.416:
        call      __dpml_addsub__@PLT
..___tag_value___y0q.417:
..B6.26:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___y0q.418:
        call      __dpml_addsub__@PLT
..___tag_value___y0q.419:
..B6.27:
        lea       184(%rsp), %r15
..B6.28:
        movq      %r14, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r14
        je        ..B6.30
..B6.29:
        movq      %r14, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r14, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___y0q.420:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___y0q.421:
        jmp       ..B6.31
..B6.30:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r14, %rcx
        lea       256(%rsp), %r8
..___tag_value___y0q.422:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y0q.423:
..B6.31:
        movq      %r14, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B6.33
..B6.32:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___y0q.424:
        call      __dpml_addsub__@PLT
..___tag_value___y0q.425:
..B6.33:
        testq     $4194304, %r14
        je        ..B6.35
..B6.34:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___y0q.426:
        call      __dpml_multiply__@PLT
..___tag_value___y0q.427:
..B6.35:
        testq     $8192, %r14
        je        ..B6.37
..B6.36:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B6.37:
        testq     $2097152, %r14
        je        ..B6.19
..B6.38:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B6.40
..B6.39:
        sarq      $58, %r14
        subl      %r14d, 308(%rsp)
..B6.40:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___y0q.428:
        call      __dpml_ux_log__@PLT
..___tag_value___y0q.429:
..B6.41:
        xorl      %esi, %esi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___y0q.430:
        call      __dpml_ux_bessel__
..___tag_value___y0q.431:
..B6.42:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___y0q.432:
        call      __dpml_multiply__@PLT
..___tag_value___y0q.433:
..B6.43:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___y0q.434:
        call      __dpml_addsub__@PLT
..___tag_value___y0q.435:
        jmp       ..B6.19
..B6.44:
        cmpq      (%rdi), %rax
        ja        ..B6.10
..B6.45:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        jmp       ..B6.7
..B6.46:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__y0q,@function
	.size	__y0q,.-__y0q
	.data
# -- End  __y0q
	.text
# -- Begin  __y1q
	.text
       .align    16,0x90
	.globl __y1q
__y1q:
# parameter 1: %xmm0
..B7.1:
	.cfi_startproc
..___tag_value___y1q.437:
..L438:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $360, %rsp
	.cfi_def_cfa_offset 400
        xorl      %esi, %esi
        movaps    %xmm0, 48(%rsp)
        lea       48(%rsp), %rdi
        movq      8(%rdi), %rbp
        lea       304(%rsp), %rdx
        shrq      $32, %rbp
        lea       48+__bessel_x_table(%rip), %rcx
        movq      %fs:40, %rax
        lea       336(%rsp), %r8
        xorq      %rsp, %rax
        lea       (%rsp), %r9
        movq      %rax, 48(%rdx)
        andl      $-2147483648, %ebp
        movq      $1, -40(%rdi)
        movq      $0, (%r9)
..___tag_value___y1q.448:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___y1q.449:
..B7.2:
        testq     %rax, %rax
        jge       ..B7.4
..B7.3:
        movq      $0x7fff000000000000, %rax
        movslq    %ebp, %rbp
        movq      %rax, %rdx
        movq      344(%rsp), %rcx
        andq      %rcx, %rdx
        shlq      $32, %rbp
        orq       %rcx, %rbp
        cmpq      %rdx, %rax
        cmovne    %rbp, %rcx
        movq      %rcx, 344(%rsp)
        jmp       ..B7.20
..B7.4:
        movl      308(%rsp), %ecx
        lea       19616+__bessel_x_table(%rip), %rdi
        movq      312(%rsp), %rax
        cmpl      $5, %ecx
        movl      $0, 304(%rsp)
        jg        ..B7.10
..B7.5:
        je        ..B7.46
..B7.6:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        testl     %ecx, %ecx
        jl        ..B7.23
..B7.7:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B7.8
..B7.9:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B7.8:
        cmpq      (%rbx), %rax
        jbe       ..B7.23
        jmp       ..B7.9
..B7.10:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___y1q.450:
        call      __dpml_divide__@PLT
..___tag_value___y1q.451:
..B7.11:
        lea       18872+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___y1q.452:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y1q.453:
..B7.12:
        lea       19208+__bessel_x_table(%rip), %rsi
        lea       160(%rsp), %rdi
        movl      $9, %edx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___y1q.454:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y1q.455:
..B7.13:
        movq      $-3, %rsi
        lea       304(%rsp), %rdi
        movl      $3, %edx
        lea       112(%rsp), %rcx
..___tag_value___y1q.456:
        call      __dpml_ux_sincos@PLT
..___tag_value___y1q.457:
..B7.14:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___y1q.458:
        call      __dpml_multiply__@PLT
..___tag_value___y1q.459:
..B7.15:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___y1q.460:
        call      __dpml_multiply__@PLT
..___tag_value___y1q.461:
..B7.16:
        xorl      %edx, %edx
        lea       64(%rsp), %rdi
        movq      %rdi, %rcx
        lea       88(%rsp), %rsi
..___tag_value___y1q.462:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.463:
..B7.17:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___y1q.464:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___y1q.465:
..B7.18:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___y1q.466:
        call      __dpml_multiply__@PLT
..___tag_value___y1q.467:
..B7.19:
        movl      $24, %eax
        movl      $16, %ecx
        xorl      %ebp, 256(%rsp)
        cmovne    %eax, %ecx
        movl      $83886080, %ebx
        lea       __bessel_error_codes(%rip), %rbp
        shrq      %cl, %rbx
        lea       256(%rsp), %rdi
        movzbl    %bl, %r8d
        lea       336(%rsp), %rsi
        movq      (%rbp), %rdx
        movq      (%rbp,%r8,8), %rcx
        lea       (%rsp), %r8
..___tag_value___y1q.468:
        call      __dpml_pack__@PLT
..___tag_value___y1q.469:
..B7.20:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B7.48
..B7.21:
        addq      $360, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 400
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 15, -24
..B7.23:
        movq      8(%rbx), %r12
        testq     $8388608, %r12
        jne       ..B7.25
..B7.24:
        lea       304(%rsp), %r15
        jmp       ..B7.28
..B7.25:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___y1q.484:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.485:
..B7.26:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___y1q.486:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.487:
..B7.27:
        lea       184(%rsp), %r15
..B7.28:
        movq      %r12, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r12
        je        ..B7.30
..B7.29:
        movq      %r12, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r12, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___y1q.488:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___y1q.489:
        jmp       ..B7.31
..B7.30:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r12, %rcx
        lea       256(%rsp), %r8
..___tag_value___y1q.490:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___y1q.491:
..B7.31:
        movq      %r12, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B7.33
..B7.32:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___y1q.492:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.493:
..B7.33:
        testq     $4194304, %r12
        je        ..B7.35
..B7.34:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___y1q.494:
        call      __dpml_multiply__@PLT
..___tag_value___y1q.495:
..B7.35:
        testq     $8192, %r12
        je        ..B7.37
..B7.36:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B7.37:
        testq     $2097152, %r12
        je        ..B7.19
..B7.38:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B7.40
..B7.39:
        sarq      $58, %r12
        subl      %r12d, 308(%rsp)
..B7.40:
        lea       19640+__bessel_x_table(%rip), %rdi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       208(%rsp), %rcx
..___tag_value___y1q.496:
        call      __dpml_divide__@PLT
..___tag_value___y1q.497:
..B7.41:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       208(%rsp), %rsi
..___tag_value___y1q.498:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.499:
..B7.42:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___y1q.500:
        call      __dpml_ux_log__@PLT
..___tag_value___y1q.501:
..B7.43:
        movl      $1, %esi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___y1q.502:
        call      __dpml_ux_bessel__
..___tag_value___y1q.503:
..B7.44:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___y1q.504:
        call      __dpml_multiply__@PLT
..___tag_value___y1q.505:
..B7.45:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___y1q.506:
        call      __dpml_addsub__@PLT
..___tag_value___y1q.507:
        jmp       ..B7.19
..B7.46:
        cmpq      (%rdi), %rax
        ja        ..B7.10
..B7.47:
        lea       __bessel_x_table(%rip), %rbx
        addq      8(%rdi), %rbx
        jmp       ..B7.7
..B7.48:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__y1q,@function
	.size	__y1q,.-__y1q
	.data
# -- End  __y1q
	.text
# -- Begin  __ynq
	.text
       .align    16,0x90
	.globl __ynq
__ynq:
# parameter 1: %edi
# parameter 2: %xmm0
..B8.1:
	.cfi_startproc
..___tag_value___ynq.509:
..L510:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
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
        subq      $368, %rsp
	.cfi_def_cfa_offset 416
        movq      $0x8000000000000000, %rdx
        movaps    %xmm0, 48(%rsp)
        andq      56(%rsp), %rdx
        movslq    %edi, %r14
        shrq      $32, %rdx
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 352(%rsp)
        lea       40+__bessel_x_table(%rip), %rax
        testq     %r14, %r14
        movq      $1, 8(%rsp)
        movq      $0, (%rsp)
        jge       ..B8.3
..B8.2:
        negq      %r14
        xorl      $-2147483648, %edx
..B8.3:
        xorl      %ebp, %ebp
        testq     $1, %r14
        lea       48(%rsp), %rdi
        cmovne    %edx, %ebp
        movl      $2, %ebx
        cmpq      $2, %r14
        lea       304(%rsp), %rdx
        cmovle    %r14, %rbx
        xorl      %esi, %esi
        lea       336(%rsp), %r8
        andl      $-2147483648, %ebp
        lea       (%rsp), %r9
        lea       (%rax,%rbx,8), %rcx
..___tag_value___ynq.522:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___ynq.523:
..B8.4:
        testq     %rax, %rax
        jge       ..B8.6
..B8.5:
        movq      $0x7fff000000000000, %rax
        movslq    %ebp, %rbp
        movq      %rax, %rdx
        movq      344(%rsp), %rcx
        andq      %rcx, %rdx
        shlq      $32, %rbp
        orq       %rcx, %rbp
        cmpq      %rdx, %rax
        cmovne    %rbp, %rcx
        movq      %rcx, 344(%rsp)
        jmp       ..B8.26
..B8.6:
        movl      $0, 304(%rsp)
        cmpq      $2, %r14
        jge       ..B8.56
..B8.7:
        movl      308(%rsp), %ecx
        lea       (%r14,%r14,2), %r9
        movq      312(%rsp), %rax
        lea       __bessel_x_table(%rip), %r8
        cmpl      $5, %ecx
        jg        ..B8.13
..B8.8:
        je        ..B8.53
..B8.9:
        movq      %r8, %rbx
        addq      19600(%r8,%r9,8), %rbx
        testl     %ecx, %ecx
        jl        ..B8.29
..B8.10:
        negl      %ecx
        addl      $5, %ecx
        shrq      %cl, %rax
        jmp       ..B8.11
..B8.12:
        movq      8(%rbx), %rcx
        sarq      $32, %rcx
        andq      $1023, %rcx
        addq      %rcx, %rbx
..B8.11:
        cmpq      (%rbx), %rax
        jbe       ..B8.29
        jmp       ..B8.12
..B8.13:
        xorl      %edi, %edi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       160(%rsp), %rcx
..___tag_value___ynq.524:
        call      __dpml_divide__@PLT
..___tag_value___ynq.525:
..B8.14:
        testq     %r14, %r14
        jne       ..B8.16
..B8.15:
        movl      $10, %r13d
        lea       18168+__bessel_x_table(%rip), %rsi
        lea       18504+__bessel_x_table(%rip), %rbx
        jmp       ..B8.17
..B8.16:
        movl      $9, %r13d
        lea       18872+__bessel_x_table(%rip), %rsi
        lea       19208+__bessel_x_table(%rip), %rbx
..B8.17:
        movl      $9, %edx
        lea       160(%rsp), %rdi
        movq      $0x1000000000000044, %rcx
        lea       64(%rsp), %r8
..___tag_value___ynq.526:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___ynq.527:
..B8.18:
        movq      %rbx, %rsi
        lea       160(%rsp), %rdi
        movq      %r13, %rdx
        movl      $70, %ecx
        lea       88(%rsp), %r8
..___tag_value___ynq.528:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___ynq.529:
..B8.19:
        movl      $3, %edx
        lea       (%r14,%r14), %rsi
        negq      %rsi
        lea       304(%rsp), %rdi
        decq      %rsi
        lea       112(%rsp), %rcx
..___tag_value___ynq.530:
        call      __dpml_ux_sincos@PLT
..___tag_value___ynq.531:
..B8.20:
        lea       64(%rsp), %rdi
        movq      %rdi, %rdx
        lea       112(%rsp), %rsi
..___tag_value___ynq.532:
        call      __dpml_multiply__@PLT
..___tag_value___ynq.533:
..B8.21:
        lea       88(%rsp), %rdi
        movq      %rdi, %rdx
        lea       136(%rsp), %rsi
..___tag_value___ynq.534:
        call      __dpml_multiply__@PLT
..___tag_value___ynq.535:
..B8.22:
        movl      $1, %eax
        xorl      %edx, %edx
        testq     %r14, %r14
        lea       64(%rsp), %rdi
        cmove     %rax, %rdx
        lea       88(%rsp), %rsi
        movq      %rdi, %rcx
..___tag_value___ynq.536:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.537:
..B8.23:
        movl      $1, %esi
        lea       160(%rsp), %rdi
        lea       88(%rsp), %rdx
..___tag_value___ynq.538:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___ynq.539:
..B8.24:
        lea       64(%rsp), %rdi
        lea       88(%rsp), %rsi
        lea       256(%rsp), %rdx
..___tag_value___ynq.540:
        call      __dpml_multiply__@PLT
..___tag_value___ynq.541:
..B8.25:
        movl      $24, %eax
        movl      $16, %ecx
        xorl      %ebp, 256(%rsp)
        cmovne    %eax, %ecx
        movl      $117833728, %ebx
        lea       __bessel_error_codes(%rip), %rbp
        shrq      %cl, %rbx
        lea       256(%rsp), %rdi
        movzbl    %bl, %r8d
        lea       336(%rsp), %rsi
        movq      (%rbp), %rdx
        movq      (%rbp,%r8,8), %rcx
        lea       (%rsp), %r8
..___tag_value___ynq.542:
        call      __dpml_pack__@PLT
..___tag_value___ynq.543:
..B8.26:
        movq      352(%rsp), %rax
        xorq      %rsp, %rax
        movaps    336(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B8.55
..B8.27:
        addq      $368, %rsp
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
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 416
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B8.29:
        movq      8(%rbx), %r13
        testq     $8388608, %r13
        jne       ..B8.31
..B8.30:
        lea       304(%rsp), %r15
        jmp       ..B8.34
..B8.31:
        movq      16(%rbx), %rax
        lea       304(%rsp), %rdi
        movq      %rax, -88(%rdi)
        lea       208(%rsp), %rsi
        movq      24(%rbx), %r8
        movl      $1, %edx
        movq      %r8, 16(%rsi)
        lea       184(%rsp), %rcx
        movl      $0, 24(%rcx)
        movl      40(%rbx), %r15d
        andl      $7, %r15d
        movl      %r15d, 28(%rcx)
..___tag_value___ynq.561:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.562:
..B8.32:
        movq      32(%rbx), %rax
        lea       184(%rsp), %rdi
        movq      %rax, 32(%rdi)
        lea       208(%rsp), %rsi
        movq      40(%rbx), %r8
        movl      $1, %edx
        movq      %rdi, %rcx
        addl      $-128, %r15d
        movq      %r8, 16(%rsi)
        movl      %r15d, 4(%rsi)
..___tag_value___ynq.563:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.564:
..B8.33:
        lea       184(%rsp), %r15
..B8.34:
        movq      %r13, %rdx
        addq      $48, %rbx
        sarq      $14, %rdx
        andq      $127, %rdx
        testq     $16777216, %r13
        je        ..B8.36
..B8.35:
        movq      %r13, %rcx
        movl      $1, %eax
        shrq      $7, %rcx
        movq      %r13, %r8
        andq      $127, %r8
        movq      %r15, %rdi
        shlq      %cl, %rax
        movq      %rdx, %rsi
        decq      %rax
        movq      %rbx, %rdx
        movq      %rax, %rcx
        lea       256(%rsp), %r9
..___tag_value___ynq.565:
        call      __dpml_evaluate_packed_poly__@PLT
..___tag_value___ynq.566:
        jmp       ..B8.37
..B8.36:
        movq      %r15, %rdi
        movq      %rbx, %rsi
        movq      %r13, %rcx
        lea       256(%rsp), %r8
..___tag_value___ynq.567:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___ynq.568:
..B8.37:
        movq      %r13, %rdx
        sarq      $11, %rdx
        andq      $3, %rdx
        je        ..B8.39
..B8.38:
        decq      %rdx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       280(%rsp), %rsi
..___tag_value___ynq.569:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.570:
..B8.39:
        testq     $4194304, %r13
        je        ..B8.41
..B8.40:
        movq      %r15, %rdi
        lea       256(%rsp), %rsi
        movq      %rsi, %rdx
..___tag_value___ynq.571:
        call      __dpml_multiply__@PLT
..___tag_value___ynq.572:
..B8.41:
        testq     $8192, %r13
        je        ..B8.43
..B8.42:
        movslq    256(%rsp), %rax
        btcq      $31, %rax
        movl      %eax, 256(%rsp)
..B8.43:
        testq     $2097152, %r13
        je        ..B8.25
..B8.44:
        lea       304(%rsp), %rax
        cmpq      %rax, %r15
        jne       ..B8.46
..B8.45:
        sarq      $58, %r13
        subl      %r13d, 308(%rsp)
..B8.46:
        cmpq      $1, %r14
        je        ..B8.51
..B8.47:
        lea       19664+__bessel_x_table(%rip), %rsi
        lea       304(%rsp), %rdi
        lea       184(%rsp), %rdx
..___tag_value___ynq.573:
        call      __dpml_ux_log__@PLT
..___tag_value___ynq.574:
..B8.48:
        movq      %r14, %rsi
        lea       304(%rsp), %rdi
        xorl      %edx, %edx
        lea       208(%rsp), %rcx
..___tag_value___ynq.575:
        call      __dpml_ux_bessel__
..___tag_value___ynq.576:
..B8.49:
        lea       184(%rsp), %rsi
        movq      %rsi, %rdx
        lea       208(%rsp), %rdi
..___tag_value___ynq.577:
        call      __dpml_multiply__@PLT
..___tag_value___ynq.578:
..B8.50:
        movl      $1, %edx
        lea       256(%rsp), %rsi
        movq      %rsi, %rcx
        lea       184(%rsp), %rdi
..___tag_value___ynq.579:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.580:
        jmp       ..B8.25
..B8.51:
        lea       19640+__bessel_x_table(%rip), %rdi
        lea       304(%rsp), %rsi
        movl      $2, %edx
        lea       208(%rsp), %rcx
..___tag_value___ynq.581:
        call      __dpml_divide__@PLT
..___tag_value___ynq.582:
..B8.52:
        xorl      %edx, %edx
        lea       256(%rsp), %rdi
        movq      %rdi, %rcx
        lea       208(%rsp), %rsi
..___tag_value___ynq.583:
        call      __dpml_addsub__@PLT
..___tag_value___ynq.584:
        jmp       ..B8.47
..B8.53:
        cmpq      19592(%r8,%r9,8), %rax
        ja        ..B8.13
..B8.54:
        movq      %r8, %rbx
        addq      19600(%r8,%r9,8), %rbx
        jmp       ..B8.10
..B8.55:
        call      __stack_chk_fail@PLT
..B8.56:
        movq      %r14, %rsi
        lea       304(%rsp), %rdi
        movl      $2, %edx
        lea       256(%rsp), %rcx
..___tag_value___ynq.585:
        call      __dpml_ux_large_order_bessel__
..___tag_value___ynq.586:
        jmp       ..B8.25
        .align    16,0x90
	.cfi_endproc
	.type	__ynq,@function
	.size	__ynq,.-__ynq
	.data
# -- End  __ynq
	.section .rodata, "a"
	.align 16
	.align 16
__bessel_x_table:
	.long	4527112
	.long	882002208
	.long	4527112
	.long	609222656
	.long	4527112
	.long	340787200
	.long	0
	.long	0
	.long	2
	.long	0
	.long	2162758664
	.long	1056696334
	.long	15799304
	.long	796782607
	.long	2163807240
	.long	536868879
	.long	0
	.long	0
	.long	136
	.long	0
	.long	137
	.long	0
	.long	138
	.long	0
	.long	139
	.long	0
	.long	141
	.long	0
	.long	142
	.long	0
	.long	2943289511
	.long	514282869
	.long	12841452
	.long	4160750144
	.long	3101294049
	.long	2582161780
	.long	1312087176
	.long	1489306811
	.long	19644348
	.long	45583118
	.long	2057404378
	.long	3169098683
	.long	3035415578
	.long	893843444
	.long	337
	.long	0
	.long	1783633315
	.long	1777532950
	.long	15440
	.long	0
	.long	1098024042
	.long	3019602458
	.long	814258
	.long	0
	.long	225592541
	.long	3983311110
	.long	37212291
	.long	0
	.long	2852953733
	.long	2839658485
	.long	1458597808
	.long	0
	.long	4135355606
	.long	2153351721
	.long	1172250754
	.long	11
	.long	1418543880
	.long	1134849789
	.long	691229967
	.long	312
	.long	221999422
	.long	2942275987
	.long	892175906
	.long	7082
	.long	183528404
	.long	1091137495
	.long	33669418
	.long	128779
	.long	2062842702
	.long	3593466159
	.long	2350547321
	.long	1825862
	.long	3242024385
	.long	1732973365
	.long	1193716022
	.long	19485117
	.long	1037993584
	.long	3230003709
	.long	1091314384
	.long	149320065
	.long	865418991
	.long	2685299556
	.long	265703573
	.long	769292094
	.long	699915261
	.long	300027119
	.long	1754654657
	.long	2412548808
	.long	1339780591
	.long	830077839
	.long	3026944889
	.long	3889644321
	.long	4103150545
	.long	428322165
	.long	2828272473
	.long	2229721522
	.long	2
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	367384380
	.long	4326353
	.long	2148
	.long	0
	.long	3463681883
	.long	689773508
	.long	115915
	.long	0
	.long	1865635225
	.long	1707083401
	.long	5659280
	.long	0
	.long	57566505
	.long	2904667094
	.long	238211523
	.long	0
	.long	2506436672
	.long	4038967033
	.long	4242689712
	.long	1
	.long	900230770
	.long	2061385255
	.long	3485051853
	.long	59
	.long	3028033191
	.long	3312441247
	.long	2225862006
	.long	1485
	.long	2615755381
	.long	2826778077
	.long	1673380806
	.long	29835
	.long	355106258
	.long	2404984212
	.long	1016211595
	.long	472327
	.long	1503440334
	.long	2187913929
	.long	749248049
	.long	5704462
	.long	3147631426
	.long	2675016444
	.long	3447985920
	.long	50328354
	.long	3866449770
	.long	2131485908
	.long	579515915
	.long	305317757
	.long	1140039843
	.long	2720631446
	.long	456203296
	.long	1162742504
	.long	3233022265
	.long	2052397230
	.long	3523447208
	.long	2379802096
	.long	4168515076
	.long	4109278998
	.long	173166154
	.long	1854372776
	.long	2
	.long	0
	.long	1759813577
	.long	941616103
	.long	12816876
	.long	4227858976
	.long	1634871482
	.long	2963569369
	.long	683137648
	.long	2191944453
	.long	1580116130
	.long	3956925442
	.long	2076563715
	.long	714524096
	.long	1425042529
	.long	556010
	.long	0
	.long	0
	.long	2705967279
	.long	111702325
	.long	0
	.long	0
	.long	3713308765
	.long	3252247564
	.long	4
	.long	0
	.long	2100681787
	.long	1255672466
	.long	747
	.long	0
	.long	3619987580
	.long	2926178414
	.long	99420
	.long	0
	.long	2512861058
	.long	4217543322
	.long	11034432
	.long	0
	.long	2336326559
	.long	4282922616
	.long	1003296232
	.long	0
	.long	961767164
	.long	880412311
	.long	74952366
	.long	17
	.long	651638352
	.long	3978983768
	.long	3866178891
	.long	965
	.long	1596163871
	.long	1924711161
	.long	2529806704
	.long	41195
	.long	4143448985
	.long	965000726
	.long	2579935101
	.long	1257069
	.long	3252814079
	.long	2428688082
	.long	440599394
	.long	25575240
	.long	2037391762
	.long	3718324471
	.long	2593618161
	.long	310260222
	.long	3513168611
	.long	3970741434
	.long	128123702
	.long	1820672813
	.long	3402733470
	.long	3892867495
	.long	1270945360
	.long	2922852432
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1986947947
	.long	53265381
	.long	0
	.long	0
	.long	3270284272
	.long	1932314788
	.long	2
	.long	0
	.long	4084347788
	.long	3083682757
	.long	409
	.long	0
	.long	2552809164
	.long	4060325593
	.long	58254
	.long	0
	.long	3829133869
	.long	1310009699
	.long	6940915
	.long	0
	.long	3083277873
	.long	3078742755
	.long	680947971
	.long	0
	.long	3385783917
	.long	3611832980
	.long	2295622592
	.long	12
	.long	3171596108
	.long	2891115348
	.long	838843771
	.long	777
	.long	3771422218
	.long	1148182025
	.long	434950258
	.long	36482
	.long	393135174
	.long	80483983
	.long	3817092537
	.long	1235511
	.long	594123027
	.long	65162621
	.long	1218473398
	.long	28145623
	.long	3536617861
	.long	375824456
	.long	425129817
	.long	385237492
	.long	633237591
	.long	649365651
	.long	313906709
	.long	2545942344
	.long	873029334
	.long	423671385
	.long	1111949612
	.long	4235958077
	.long	4294967293
	.long	0
	.long	4159743234
	.long	1365459778
	.long	12825068
	.long	4227858976
	.long	1727418030
	.long	2322967398
	.long	1034782148
	.long	1761143608
	.long	748103118
	.long	681764742
	.long	1945985884
	.long	1037518188
	.long	2731139269
	.long	559058
	.long	0
	.long	0
	.long	3638286185
	.long	115784543
	.long	0
	.long	0
	.long	484918398
	.long	4013875548
	.long	4
	.long	0
	.long	868380236
	.long	106340102
	.long	775
	.long	0
	.long	3771022282
	.long	1666157217
	.long	103048
	.long	0
	.long	470645992
	.long	2100027425
	.long	11422638
	.long	0
	.long	1279250702
	.long	1788924811
	.long	1036227271
	.long	0
	.long	3067063928
	.long	3886781995
	.long	2181909072
	.long	17
	.long	103786080
	.long	119465851
	.long	1602069376
	.long	987
	.long	314091048
	.long	4120514941
	.long	1703984391
	.long	41665
	.long	3153492716
	.long	1769936992
	.long	3277791366
	.long	1248908
	.long	2480865882
	.long	357555572
	.long	1426486202
	.long	24641724
	.long	925215435
	.long	3875415602
	.long	2242310174
	.long	283170307
	.long	1206749920
	.long	1745236754
	.long	421850802
	.long	1512988987
	.long	554528472
	.long	4124376878
	.long	999726568
	.long	2331757501
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	844883384
	.long	48877615
	.long	0
	.long	0
	.long	613626429
	.long	1016694622
	.long	2
	.long	0
	.long	733791182
	.long	4037492605
	.long	370
	.long	0
	.long	2511602797
	.long	962949943
	.long	52209
	.long	0
	.long	2469661725
	.long	4226370589
	.long	6143972
	.long	0
	.long	2060721069
	.long	3581658067
	.long	593539814
	.long	0
	.long	2839602819
	.long	4222464368
	.long	3064291281
	.long	10
	.long	592311022
	.long	275206449
	.long	2221002398
	.long	647
	.long	3453980290
	.long	1100410175
	.long	2256369516
	.long	29367
	.long	965726650
	.long	341239821
	.long	1020304938
	.long	947922
	.long	2152956293
	.long	1358966229
	.long	796742083
	.long	20128900
	.long	2203770174
	.long	4010517667
	.long	1001215390
	.long	247246889
	.long	118635824
	.long	336180557
	.long	907042493
	.long	1379503488
	.long	1731363265
	.long	2376282645
	.long	3253758107
	.long	2155609720
	.long	4294967293
	.long	0
	.long	1133938837
	.long	1788275660
	.long	12816876
	.long	4227858976
	.long	328577348
	.long	3165265924
	.long	1527252342
	.long	1339392541
	.long	2394531985
	.long	243403399
	.long	1651082908
	.long	3768234999
	.long	1764410062
	.long	1127868
	.long	0
	.long	0
	.long	380965293
	.long	236501030
	.long	0
	.long	0
	.long	4057780011
	.long	296466121
	.long	10
	.long	0
	.long	1915520970
	.long	2413700929
	.long	1578
	.long	0
	.long	2515416277
	.long	1400529095
	.long	209346
	.long	0
	.long	1507502659
	.long	119133009
	.long	23121417
	.long	0
	.long	822245729
	.long	2123535665
	.long	2086838594
	.long	0
	.long	2761614169
	.long	2187408238
	.long	28089432
	.long	35
	.long	552668793
	.long	2572470762
	.long	825632309
	.long	1954
	.long	1089676525
	.long	4007660420
	.long	2284740089
	.long	81273
	.long	3790860620
	.long	4250011568
	.long	3911718864
	.long	2386138
	.long	1569325755
	.long	1413720016
	.long	751491568
	.long	45731817
	.long	955105128
	.long	1250521740
	.long	2097840313
	.long	506337017
	.long	1624392615
	.long	4258574603
	.long	3379569620
	.long	2624122564
	.long	4107144163
	.long	2368467334
	.long	1634973330
	.long	3993629493
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	818934838
	.long	89458952
	.long	0
	.long	0
	.long	1917175450
	.long	275918876
	.long	4
	.long	0
	.long	745599157
	.long	3172395316
	.long	667
	.long	0
	.long	2952822384
	.long	492051402
	.long	92937
	.long	0
	.long	2206545778
	.long	108664272
	.long	10789849
	.long	0
	.long	3593487476
	.long	2223761325
	.long	1025213015
	.long	0
	.long	4186460497
	.long	2115107000
	.long	546575231
	.long	18
	.long	4222869425
	.long	1697786641
	.long	1644425336
	.long	1067
	.long	2279245768
	.long	3297743944
	.long	144043411
	.long	46814
	.long	528690514
	.long	2305832912
	.long	3885610801
	.long	1446642
	.long	3948180674
	.long	1400240622
	.long	2848238597
	.long	29038477
	.long	365799999
	.long	3493109055
	.long	720503959
	.long	333431672
	.long	4157886487
	.long	4286999543
	.long	464978815
	.long	1767352013
	.long	3339500879
	.long	5789261
	.long	740497786
	.long	2709489262
	.long	4294967292
	.long	0
	.long	675685466
	.long	2210650544
	.long	12825068
	.long	4227858976
	.long	2475592912
	.long	4007987703
	.long	795233231
	.long	3544111414
	.long	3119445785
	.long	4019086602
	.long	1815298636
	.long	3651400695
	.long	250417896
	.long	1132895
	.long	0
	.long	0
	.long	1599660003
	.long	238893558
	.long	0
	.long	0
	.long	4178878964
	.long	640014589
	.long	10
	.long	0
	.long	3652653430
	.long	1664916825
	.long	1586
	.long	0
	.long	506609104
	.long	279824532
	.long	209578
	.long	0
	.long	3394823516
	.long	2710891899
	.long	23032181
	.long	0
	.long	1068742364
	.long	3396859346
	.long	2065409978
	.long	0
	.long	1532264156
	.long	1783373111
	.long	1537162281
	.long	34
	.long	3404313527
	.long	645034109
	.long	1238696626
	.long	1897
	.long	2815751506
	.long	1216048877
	.long	2381163022
	.long	77819
	.long	681356606
	.long	1742374831
	.long	1470157118
	.long	2245756
	.long	3583839604
	.long	3430133311
	.long	3428750273
	.long	42198880
	.long	2624104293
	.long	3836925937
	.long	2616934709
	.long	458497922
	.long	4091889117
	.long	2154475757
	.long	1334377092
	.long	2344404304
	.long	3811467832
	.long	4225344580
	.long	3014131639
	.long	3548440700
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2101683395
	.long	163038220
	.long	0
	.long	0
	.long	1150331703
	.long	1497034637
	.long	7
	.long	0
	.long	1295008257
	.long	2962522183
	.long	1195
	.long	0
	.long	193789761
	.long	1257830278
	.long	164530
	.long	0
	.long	3810960890
	.long	1878269909
	.long	18845080
	.long	0
	.long	2863024615
	.long	258783337
	.long	1761897164
	.long	0
	.long	2435461135
	.long	4254653612
	.long	2378780257
	.long	30
	.long	3635296493
	.long	4161741958
	.long	2074908190
	.long	1757
	.long	3250897880
	.long	2210089015
	.long	2132559608
	.long	74948
	.long	2166588251
	.long	3427170173
	.long	756319997
	.long	2241223
	.long	107629526
	.long	2814783011
	.long	580266263
	.long	43402774
	.long	2488417593
	.long	4037868436
	.long	2684236844
	.long	482393505
	.long	1636043988
	.long	2549915956
	.long	1055410979
	.long	2500896928
	.long	2063349270
	.long	3231350804
	.long	4156853657
	.long	3802515847
	.long	4294967291
	.long	0
	.long	191176124
	.long	2632795962
	.long	12816876
	.long	4227858976
	.long	1355787731
	.long	2425457148
	.long	539364904
	.long	1079897623
	.long	897043330
	.long	2066037045
	.long	1494461853
	.long	2159441332
	.long	2366379069
	.long	1131867
	.long	0
	.long	0
	.long	161545432
	.long	239220328
	.long	0
	.long	0
	.long	762722372
	.long	576200370
	.long	10
	.long	0
	.long	3468035492
	.long	1642011913
	.long	1578
	.long	0
	.long	4253764606
	.long	2733292311
	.long	207600
	.long	0
	.long	2811470101
	.long	3269311219
	.long	22691285
	.long	0
	.long	1825255966
	.long	1189284970
	.long	2021353589
	.long	0
	.long	717526141
	.long	2244953537
	.long	1522450794
	.long	33
	.long	693565287
	.long	2333767130
	.long	715945548
	.long	1824
	.long	2985180089
	.long	935432992
	.long	3298648348
	.long	73985
	.long	1392357808
	.long	2627674694
	.long	3107871798
	.long	2108973
	.long	3903806984
	.long	3473675411
	.long	4215247699
	.long	39146099
	.long	2236383345
	.long	2775867013
	.long	1051360344
	.long	420899965
	.long	715332657
	.long	3204539120
	.long	3177676604
	.long	2136936117
	.long	1567224951
	.long	2092241552
	.long	1304170706
	.long	3225156278
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3855812151
	.long	148081926
	.long	0
	.long	0
	.long	1028065361
	.long	2668697612
	.long	6
	.long	0
	.long	728439685
	.long	892227407
	.long	1067
	.long	0
	.long	615906957
	.long	1950415562
	.long	145255
	.long	0
	.long	3284206690
	.long	4202823521
	.long	16428718
	.long	0
	.long	1576659798
	.long	3860187404
	.long	1513740564
	.long	0
	.long	1223428711
	.long	1000755308
	.long	3492805977
	.long	25
	.long	1363712775
	.long	563079876
	.long	2891886022
	.long	1456
	.long	1912616499
	.long	4019273916
	.long	1170042322
	.long	60815
	.long	2578791056
	.long	3763338518
	.long	4013988507
	.long	1778302
	.long	596369036
	.long	4167829437
	.long	495223280
	.long	33706721
	.long	3069656582
	.long	1025646758
	.long	2029385299
	.long	368055588
	.long	4229256156
	.long	252497843
	.long	655863054
	.long	1886199584
	.long	3468886839
	.long	4222796798
	.long	2935312587
	.long	2855531945
	.long	4294967291
	.long	0
	.long	2797952740
	.long	3054806814
	.long	12825068
	.long	4227858976
	.long	2679935261
	.long	2846977675
	.long	1518593598
	.long	771365947
	.long	2901840529
	.long	102047653
	.long	404923885
	.long	709259131
	.long	475240934
	.long	1125214
	.long	0
	.long	0
	.long	1836195777
	.long	237921320
	.long	0
	.long	0
	.long	620715017
	.long	203920416
	.long	10
	.long	0
	.long	2730396855
	.long	4074598327
	.long	1558
	.long	0
	.long	149951721
	.long	1542475780
	.long	204126
	.long	0
	.long	895086698
	.long	26046181
	.long	22194201
	.long	0
	.long	1052098554
	.long	3499556639
	.long	1964976743
	.long	0
	.long	3912081837
	.long	3256319751
	.long	845978380
	.long	32
	.long	2103533506
	.long	1288917748
	.long	344362072
	.long	1747
	.long	586607141
	.long	2789855780
	.long	789521480
	.long	70264
	.long	1029756229
	.long	2597195099
	.long	1036923741
	.long	1985929
	.long	570319344
	.long	1441637551
	.long	3714173380
	.long	36574571
	.long	1955754200
	.long	3807195269
	.long	90410504
	.long	390763661
	.long	2874958444
	.long	3633243115
	.long	2931863300
	.long	1975635819
	.long	32243731
	.long	2591757144
	.long	3895319176
	.long	2976685396
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3502958424
	.long	134252990
	.long	0
	.long	0
	.long	2150545646
	.long	4111632521
	.long	5
	.long	0
	.long	2999463670
	.long	3120574364
	.long	951
	.long	0
	.long	681502082
	.long	4195687681
	.long	128253
	.long	0
	.long	442998991
	.long	979480158
	.long	14344565
	.long	0
	.long	151969242
	.long	4070078334
	.long	1305331397
	.long	0
	.long	1826390155
	.long	1249420678
	.long	4104872299
	.long	21
	.long	1355768955
	.long	3758834208
	.long	3412700795
	.long	1220
	.long	1644384830
	.long	2135273975
	.long	2887874099
	.long	50190
	.long	1150050798
	.long	2243957583
	.long	2798432622
	.long	1445747
	.long	3460890697
	.long	2393108922
	.long	2317365582
	.long	27037249
	.long	135336093
	.long	93459488
	.long	4047224670
	.long	292116325
	.long	2632066877
	.long	2478679361
	.long	3592211274
	.long	1486901023
	.long	4259160346
	.long	2383522946
	.long	1229950112
	.long	2245322564
	.long	4294967291
	.long	0
	.long	4131724546
	.long	247119503
	.long	229390
	.long	4227858728
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1920268374
	.long	676671
	.long	0
	.long	0
	.long	2648589321
	.long	145893005
	.long	0
	.long	0
	.long	86363579
	.long	792199897
	.long	6
	.long	0
	.long	3569661012
	.long	3338031321
	.long	964
	.long	0
	.long	3537357668
	.long	2546852361
	.long	127350
	.long	0
	.long	1674639321
	.long	989707703
	.long	14008565
	.long	0
	.long	633069455
	.long	3178427980
	.long	1260770870
	.long	0
	.long	2929308222
	.long	1214878390
	.long	581189477
	.long	21
	.long	3037977477
	.long	3608983388
	.long	2481839655
	.long	1183
	.long	1571270935
	.long	1253493999
	.long	1158050441
	.long	49710
	.long	2588063047
	.long	3245086386
	.long	381774870
	.long	1491308
	.long	2638540175
	.long	477218587
	.long	3340530119
	.long	29826161
	.long	1326835123
	.long	1431655765
	.long	1431655765
	.long	357913941
	.long	4293747565
	.long	4294967295
	.long	4294967295
	.long	2147483647
	.long	4294964933
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	0
	.long	0
	.long	105434711
	.long	715574136
	.long	12825068
	.long	4227858976
	.long	2071479615
	.long	4114262957
	.long	3040350773
	.long	3578447305
	.long	1647168078
	.long	978698920
	.long	1144287138
	.long	3827795094
	.long	3787403973
	.long	562609
	.long	0
	.long	0
	.long	1649578671
	.long	117913179
	.long	0
	.long	0
	.long	3082368152
	.long	40122077
	.long	5
	.long	0
	.long	602895375
	.long	3952322020
	.long	783
	.long	0
	.long	3838465116
	.long	2181233212
	.long	103846
	.long	0
	.long	851468155
	.long	4001763569
	.long	11469451
	.long	0
	.long	3659498813
	.long	523695164
	.long	1037041443
	.long	0
	.long	2978199351
	.long	773277203
	.long	2051066178
	.long	17
	.long	4092624060
	.long	3930860161
	.long	3294762475
	.long	984
	.long	1105652893
	.long	1066438988
	.long	3642064414
	.long	41652
	.long	2196971596
	.long	2146191353
	.long	3442355222
	.long	1259557
	.long	3918894073
	.long	188553237
	.long	818155667
	.long	25403502
	.long	852571760
	.long	3121326526
	.long	2509267963
	.long	306918409
	.long	2896427178
	.long	2552665716
	.long	2229539464
	.long	1835168886
	.long	1453277625
	.long	518768794
	.long	1711011538
	.long	3459676865
	.long	0
	.long	0
	.long	2062472710
	.long	69166
	.long	0
	.long	0
	.long	673615662
	.long	23833985
	.long	0
	.long	0
	.long	630248642
	.long	363685088
	.long	1
	.long	0
	.long	288759411
	.long	2697305718
	.long	181
	.long	0
	.long	2130785052
	.long	823708847
	.long	25870
	.long	0
	.long	1430316470
	.long	3023974030
	.long	3089586
	.long	0
	.long	3827036731
	.long	3302510044
	.long	304068797
	.long	0
	.long	3738696708
	.long	3274114857
	.long	2670490624
	.long	5
	.long	287265850
	.long	4109473262
	.long	3360573366
	.long	350
	.long	38249035
	.long	2812769293
	.long	2052670485
	.long	16622
	.long	3926290357
	.long	206759826
	.long	2443135158
	.long	571444
	.long	1809246839
	.long	3759763742
	.long	1694050886
	.long	13356266
	.long	1847069514
	.long	3357213528
	.long	2594247192
	.long	192045418
	.long	1427481913
	.long	3504276391
	.long	3430325004
	.long	1423790384
	.long	900295939
	.long	3843660490
	.long	2372410664
	.long	3611630842
	.long	4294967294
	.long	0
	.long	774013863
	.long	1145724988
	.long	12816876
	.long	4227858976
	.long	2744287014
	.long	3766464413
	.long	1053213460
	.long	3969062622
	.long	1083456455
	.long	3049621789
	.long	3365086587
	.long	561540346
	.long	2996396832
	.long	575091
	.long	0
	.long	0
	.long	42660608
	.long	117761546
	.long	0
	.long	0
	.long	3056183536
	.long	25400137
	.long	5
	.long	0
	.long	3539354956
	.long	964064098
	.long	784
	.long	0
	.long	1133846550
	.long	3354628177
	.long	103987
	.long	0
	.long	837408368
	.long	1875825334
	.long	11493772
	.long	0
	.long	4020356818
	.long	15105701
	.long	1039623118
	.long	0
	.long	2432213228
	.long	4116600081
	.long	2213395342
	.long	17
	.long	346536517
	.long	2329406249
	.long	1562759336
	.long	985
	.long	1904777685
	.long	777730941
	.long	3468653957
	.long	41521
	.long	2565172885
	.long	3392131685
	.long	216627896
	.long	1245806
	.long	777484384
	.long	1373172746
	.long	529073424
	.long	24734325
	.long	1189796029
	.long	1140288371
	.long	1770133260
	.long	289389693
	.long	2194668596
	.long	3631308278
	.long	3147219318
	.long	1613893367
	.long	344927104
	.long	1168140252
	.long	980702046
	.long	2577974684
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1881749350
	.long	46062706
	.long	0
	.long	0
	.long	2009952654
	.long	475434148
	.long	2
	.long	0
	.long	3595153284
	.long	3894518978
	.long	350
	.long	0
	.long	1401741016
	.long	800557075
	.long	49546
	.long	0
	.long	1250374609
	.long	1417939249
	.long	5854570
	.long	0
	.long	3066837935
	.long	3745800702
	.long	568662855
	.long	0
	.long	4187727968
	.long	1730013373
	.long	1461665574
	.long	10
	.long	3341641980
	.long	2588791038
	.long	1829968615
	.long	631
	.long	888818201
	.long	3523374130
	.long	852659386
	.long	29067
	.long	3905449900
	.long	720793762
	.long	1836357526
	.long	959598
	.long	711023215
	.long	2187868163
	.long	363719992
	.long	21124528
	.long	3782390642
	.long	1386816912
	.long	3690959132
	.long	275994811
	.long	3562870039
	.long	2887822114
	.long	2407074109
	.long	1720896075
	.long	723327461
	.long	1298307143
	.long	454424891
	.long	2939711024
	.long	4294967293
	.long	0
	.long	4220957082
	.long	1571153381
	.long	12825068
	.long	4227858976
	.long	4024519172
	.long	2730919557
	.long	1456036120
	.long	3256132268
	.long	2931624203
	.long	1339507405
	.long	1357793012
	.long	1094045067
	.long	1129712150
	.long	1138961
	.long	0
	.long	0
	.long	2372829177
	.long	237557530
	.long	0
	.long	0
	.long	534122416
	.long	423932365
	.long	10
	.long	0
	.long	4247263216
	.long	4283734547
	.long	1580
	.long	0
	.long	2387955306
	.long	607897802
	.long	209396
	.long	0
	.long	2812500241
	.long	994269493
	.long	23101510
	.long	0
	.long	3000333735
	.long	388858992
	.long	2083539857
	.long	0
	.long	1161363350
	.long	3153424268
	.long	4076649817
	.long	34
	.long	1819801357
	.long	1812729011
	.long	330148996
	.long	1953
	.long	3163500019
	.long	3864465446
	.long	4064814974
	.long	81467
	.long	3858761013
	.long	3692736596
	.long	1902533809
	.long	2406436
	.long	3293643408
	.long	375345917
	.long	2826147377
	.long	46631566
	.long	4164807482
	.long	4162856869
	.long	2610851470
	.long	525451848
	.long	2048614181
	.long	1569367797
	.long	3543942876
	.long	2777034414
	.long	3312093651
	.long	708453697
	.long	1978042629
	.long	4289897122
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2071065822
	.long	86614531
	.long	0
	.long	0
	.long	2391713785
	.long	4060239028
	.long	3
	.long	0
	.long	4029056008
	.long	1846783416
	.long	650
	.long	0
	.long	161647879
	.long	3413412248
	.long	90917
	.long	0
	.long	436274283
	.long	2466277660
	.long	10613189
	.long	0
	.long	671943584
	.long	553150431
	.long	1015563947
	.long	0
	.long	1753238306
	.long	1517906611
	.long	532738342
	.long	18
	.long	618689867
	.long	596492050
	.long	2460930076
	.long	1080
	.long	414131426
	.long	1367052230
	.long	3308909536
	.long	48209
	.long	4126473295
	.long	3381931776
	.long	1805298000
	.long	1525871
	.long	636148702
	.long	2186154781
	.long	2942928489
	.long	31683165
	.long	999999630
	.long	4126401816
	.long	259460410
	.long	380946890
	.long	231980038
	.long	363549591
	.long	883055596
	.long	2118559672
	.long	3622330114
	.long	1609129610
	.long	63100786
	.long	3373399958
	.long	4294967292
	.long	0
	.long	1588683414
	.long	1994957096
	.long	12816876
	.long	4227858976
	.long	2267877675
	.long	3576551320
	.long	1765045200
	.long	3464483214
	.long	1351689592
	.long	3801296041
	.long	352384788
	.long	331703635
	.long	605298272
	.long	1136492
	.long	0
	.long	0
	.long	2681104739
	.long	238950871
	.long	0
	.long	0
	.long	3149807504
	.long	621710226
	.long	10
	.long	0
	.long	2595166296
	.long	121246070
	.long	1585
	.long	0
	.long	2215012362
	.long	2482367452
	.long	209363
	.long	0
	.long	470943915
	.long	668008525
	.long	23014074
	.long	0
	.long	3763658407
	.long	3343124614
	.long	2065508072
	.long	0
	.long	249623666
	.long	2390602642
	.long	1795904366
	.long	34
	.long	1346725057
	.long	2927659279
	.long	1029310809
	.long	1906
	.long	3831989490
	.long	3024162362
	.long	457912312
	.long	78555
	.long	1623643704
	.long	979247650
	.long	4029398248
	.long	2282874
	.long	2791098952
	.long	2391841680
	.long	2696257895
	.long	43306505
	.long	262637107
	.long	3763159487
	.long	2938992385
	.long	475778611
	.long	3299175406
	.long	3677201670
	.long	2349553020
	.long	2458659840
	.long	206020084
	.long	2363120772
	.long	2638136209
	.long	3751386051
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3034634732
	.long	80259059
	.long	0
	.long	0
	.long	2987375659
	.long	2703374687
	.long	3
	.long	0
	.long	1215607278
	.long	4163686255
	.long	592
	.long	0
	.long	781938720
	.long	2086477677
	.long	82005
	.long	0
	.long	3556471813
	.long	3031971904
	.long	9451560
	.long	0
	.long	2005655437
	.long	2494233436
	.long	890598760
	.long	0
	.long	1167836918
	.long	114850616
	.long	2568442225
	.long	15
	.long	2351511177
	.long	1547877599
	.long	2724662709
	.long	908
	.long	1583056039
	.long	1830443712
	.long	1719394286
	.long	39382
	.long	1124627201
	.long	674092017
	.long	1868383046
	.long	1202161
	.long	4262245498
	.long	1815295967
	.long	3756878985
	.long	23872494
	.long	1013133814
	.long	1390137141
	.long	1845508344
	.long	272711216
	.long	2632746813
	.long	493954138
	.long	4257964097
	.long	1450886530
	.long	1659393003
	.long	4235627183
	.long	3605632322
	.long	2252460395
	.long	4294967292
	.long	0
	.long	1932081087
	.long	2418003218
	.long	12825068
	.long	4227858976
	.long	675685466
	.long	2210650544
	.long	2635457388
	.long	3001216681
	.long	2383987892
	.long	1958452314
	.long	200318573
	.long	1670080688
	.long	1772035109
	.long	1132935
	.long	0
	.long	0
	.long	2224132738
	.long	239067824
	.long	0
	.long	0
	.long	2504702558
	.long	548466910
	.long	10
	.long	0
	.long	120798343
	.long	3554901277
	.long	1577
	.long	0
	.long	2434954499
	.long	1660365081
	.long	207659
	.long	0
	.long	2744534538
	.long	1767005024
	.long	22722744
	.long	0
	.long	1354085614
	.long	573653951
	.long	2027648348
	.long	0
	.long	2302238895
	.long	940817979
	.long	2333007942
	.long	33
	.long	4044378246
	.long	2424318367
	.long	54614345
	.long	1841
	.long	3277428544
	.long	937510890
	.long	2353849014
	.long	75024
	.long	4005612974
	.long	1580435742
	.long	1601652905
	.long	2151432
	.long	3782038889
	.long	2892349902
	.long	3709353570
	.long	40211969
	.long	2209533886
	.long	1632935844
	.long	1972351296
	.long	435469920
	.long	2489865929
	.long	3249608891
	.long	2176055770
	.long	2225282526
	.long	4163770951
	.long	1426482641
	.long	75146285
	.long	3375249381
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2220915002
	.long	147504303
	.long	0
	.long	0
	.long	3383683424
	.long	2662024159
	.long	6
	.long	0
	.long	4203741875
	.long	3065385408
	.long	1071
	.long	0
	.long	94574798
	.long	3694012699
	.long	146649
	.long	0
	.long	548197575
	.long	3705365680
	.long	16693691
	.long	0
	.long	2971661595
	.long	3995690294
	.long	1550219778
	.long	0
	.long	3364157924
	.long	536970539
	.long	2951498969
	.long	26
	.long	3606982515
	.long	2998613798
	.long	1572386296
	.long	1523
	.long	54159713
	.long	2382254602
	.long	1960159626
	.long	64476
	.long	164299812
	.long	2501105432
	.long	2657580848
	.long	1915496
	.long	4191466314
	.long	1475491836
	.long	409725690
	.long	36942237
	.long	2000214888
	.long	3808326374
	.long	4191271566
	.long	410491162
	.long	3021924263
	.long	3759042010
	.long	567945948
	.long	2137524835
	.long	2922089864
	.long	3203830329
	.long	752382353
	.long	3278805360
	.long	4294967291
	.long	0
	.long	223335381
	.long	2840633637
	.long	12816876
	.long	4227858976
	.long	191176124
	.long	2632795962
	.long	792829484
	.long	4194095148
	.long	1561179743
	.long	586327156
	.long	2807878837
	.long	3545818423
	.long	1415596998
	.long	1125924
	.long	0
	.long	0
	.long	2335397241
	.long	237899312
	.long	0
	.long	0
	.long	898966976
	.long	220219689
	.long	10
	.long	0
	.long	3140042207
	.long	3171383055
	.long	1560
	.long	0
	.long	2021920923
	.long	1061467961
	.long	204590
	.long	0
	.long	1780915469
	.long	3394528573
	.long	22279398
	.long	0
	.long	2938289575
	.long	3726010345
	.long	1976658045
	.long	0
	.long	1742882295
	.long	3191343047
	.long	2047550512
	.long	32
	.long	3647296052
	.long	2253947992
	.long	1300129447
	.long	1768
	.long	2864334604
	.long	1571083743
	.long	2473956825
	.long	71411
	.long	902177211
	.long	1309134586
	.long	1494319522
	.long	2027873
	.long	1978202312
	.long	2898857432
	.long	1838265108
	.long	37534288
	.long	440015974
	.long	4230775195
	.long	1255344562
	.long	402981746
	.long	1205443830
	.long	4140044432
	.long	3282462476
	.long	2046231097
	.long	2809323527
	.long	1074462473
	.long	1051546203
	.long	3093465233
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1994591040
	.long	134874738
	.long	0
	.long	0
	.long	1154796671
	.long	33466848
	.long	6
	.long	0
	.long	944609558
	.long	611410016
	.long	964
	.long	0
	.long	3394269576
	.long	3253723903
	.long	130617
	.long	0
	.long	1604599336
	.long	2571734283
	.long	14700043
	.long	0
	.long	2720136884
	.long	1760790051
	.long	1347449133
	.long	0
	.long	3340661604
	.long	3195040324
	.long	3680379603
	.long	22
	.long	3451695816
	.long	2081337828
	.long	1427065647
	.long	1283
	.long	2755833528
	.long	3578899323
	.long	1127406403
	.long	53342
	.long	2103438216
	.long	2925829331
	.long	133275521
	.long	1554875
	.long	3003940918
	.long	1302060887
	.long	2008749614
	.long	29436400
	.long	4279932662
	.long	1473928468
	.long	533396292
	.long	321840330
	.long	3248261436
	.long	2035472962
	.long	372258839
	.long	1655927148
	.long	3127294521
	.long	3128066919
	.long	2865156314
	.long	2523236171
	.long	4294967291
	.long	0
	.long	1890690921
	.long	3263011055
	.long	12825068
	.long	4227858976
	.long	2797952740
	.long	3054806814
	.long	1894667006
	.long	214237447
	.long	1296756846
	.long	4060330468
	.long	1098085821
	.long	3604642180
	.long	1726893371
	.long	1115219
	.long	0
	.long	0
	.long	494710856
	.long	235638241
	.long	0
	.long	0
	.long	1193585816
	.long	3981686648
	.long	9
	.long	0
	.long	143797405
	.long	762868814
	.long	1536
	.long	0
	.long	898881458
	.long	2220674218
	.long	200569
	.long	0
	.long	271788931
	.long	1836576498
	.long	21741122
	.long	0
	.long	3732597210
	.long	2567748600
	.long	1918751890
	.long	0
	.long	3683010637
	.long	2674911154
	.long	1453366689
	.long	31
	.long	3314792629
	.long	3590832641
	.long	792084225
	.long	1695
	.long	710884882
	.long	1421882797
	.long	453673326
	.long	67984
	.long	4289998525
	.long	1545801705
	.long	2082770245
	.long	1917055
	.long	1427259630
	.long	3087470940
	.long	1747456958
	.long	35251400
	.long	2597591904
	.long	3049486553
	.long	2056710479
	.long	376393410
	.long	2213091204
	.long	2641098163
	.long	3971809407
	.long	1903717223
	.long	3244722564
	.long	177715146
	.long	3090222263
	.long	2872209565
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	4193727727
	.long	246007177
	.long	0
	.long	0
	.long	768822418
	.long	3782320021
	.long	10
	.long	0
	.long	2196079823
	.long	201893182
	.long	1732
	.long	0
	.long	1661061158
	.long	4260475543
	.long	232537
	.long	0
	.long	3959757821
	.long	1648015928
	.long	25908825
	.long	0
	.long	2136418328
	.long	2790862444
	.long	2348699355
	.long	0
	.long	14984150
	.long	3359899422
	.long	1557173094
	.long	39
	.long	2579163115
	.long	4209094591
	.long	2975956643
	.long	2181
	.long	809874233
	.long	1287653529
	.long	1784169535
	.long	89477
	.long	836980350
	.long	209256478
	.long	3015626027
	.long	2573945
	.long	2016221741
	.long	1745099515
	.long	1018088912
	.long	48139690
	.long	3329474215
	.long	1010710723
	.long	642962579
	.long	521003091
	.long	801578433
	.long	3224757113
	.long	3341503980
	.long	2660978634
	.long	2199935228
	.long	4052003304
	.long	2221814426
	.long	4038241008
	.long	4294967290
	.long	0
	.long	3178730751
	.long	116147812
	.long	14854636
	.long	416
	.long	416371481
	.long	2823641499
	.long	2506915568
	.long	4124207639
	.long	950494682
	.long	256731865
	.long	619428904
	.long	3037288495
	.long	4208811624
	.long	884105690
	.long	0
	.long	0
	.long	3410106176
	.long	4245208708
	.long	126
	.long	0
	.long	3116091236
	.long	3070791485
	.long	44137
	.long	0
	.long	1379902983
	.long	2256834745
	.long	12232777
	.long	0
	.long	2097914520
	.long	545273602
	.long	2619411277
	.long	0
	.long	215423817
	.long	3507318412
	.long	256238311
	.long	97
	.long	3668117219
	.long	4022295396
	.long	2420542464
	.long	10890
	.long	3822143167
	.long	3930114069
	.long	1325150521
	.long	798528
	.long	3699121161
	.long	791807715
	.long	67391017
	.long	33958538
	.long	3257898816
	.long	678025854
	.long	1894699409
	.long	674718922
	.long	1201340557
	.long	2594901103
	.long	3933905415
	.long	3704843775
	.long	4294967294
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	982743186
	.long	1840382683
	.long	8
	.long	0
	.long	2770471380
	.long	1232287742
	.long	3186
	.long	0
	.long	2689925901
	.long	4291161637
	.long	993651
	.long	0
	.long	1865452386
	.long	278667130
	.long	243198017
	.long	0
	.long	941914362
	.long	1579381843
	.long	2208801237
	.long	10
	.long	4030005343
	.long	1439537596
	.long	2913266490
	.long	1415
	.long	897709932
	.long	2611261457
	.long	3831192043
	.long	129715
	.long	2599986828
	.long	562288571
	.long	873738186
	.long	7347842
	.long	1676792497
	.long	2643238970
	.long	1059645264
	.long	218261805
	.long	3968061936
	.long	1814410518
	.long	4215318874
	.long	2360677848
	.long	4294967294
	.long	0
	.long	1965065697
	.long	125333813
	.long	29671681
	.long	368
	.long	2696892906
	.long	3837883846
	.long	4163818448
	.long	2636579655
	.long	352076257
	.long	2995873374
	.long	2356022824
	.long	2864685933
	.long	507647155
	.long	87534215
	.long	364999993
	.long	2297262027
	.long	3804603314
	.long	421232362
	.long	623004025
	.long	2436698064
	.long	857209363
	.long	4104727312
	.long	1300779075
	.long	2300222395
	.long	3549420260
	.long	815695431
	.long	3490474032
	.long	2175015012
	.long	273829883
	.long	837019448
	.long	1150859439
	.long	4129001094
	.long	166114890
	.long	262582295
	.long	2200440235
	.long	3934411649
	.long	4260765987
	.long	1496016656
	.long	613554758
	.long	3765455772
	.long	1537995402
	.long	3112915065
	.long	3831973801
	.long	3621881222
	.long	371259347
	.long	563988192
	.long	2577068580
	.long	3504039397
	.long	3892719810
	.long	2774136061
	.long	761863973
	.long	3413069278
	.long	932429307
	.long	3533847854
	.long	670352073
	.long	3351189942
	.long	332608650
	.long	3102655069
	.long	2958547113
	.long	3322177573
	.long	146996155
	.long	3411645617
	.long	2215850181
	.long	3332037184
	.long	590982538
	.long	2197714658
	.long	1764878165
	.long	3390200984
	.long	952267699
	.long	417090737
	.long	3487943763
	.long	3519763458
	.long	1338819266
	.long	2605877443
	.long	347561299
	.long	3761505334
	.long	3379093939
	.long	2405540353
	.long	1506019226
	.long	3884741458
	.long	2785505456
	.long	3086386584
	.long	2046080941
	.long	3789068741
	.long	1518238185
	.long	1088338119
	.long	1804079185
	.long	4226925859
	.long	2303172266
	.long	2741263628
	.long	633622237
	.long	3777083586
	.long	3757196004
	.long	294895316
	.long	27640459
	.long	432
	.long	2828420652
	.long	3361833042
	.long	1412248364
	.long	2211183849
	.long	0
	.long	3547577142
	.long	1
	.long	0
	.long	2851890648
	.long	450899591
	.long	287024625
	.long	4159553752
	.long	3788600515
	.long	2561792324
	.long	3962766183
	.long	3512577319
	.long	2861699545
	.long	2196522185
	.long	1554209519
	.long	4198958626
	.long	3194489346
	.long	2786967510
	.long	3304318871
	.long	3344542374
	.long	4105608152
	.long	867728568
	.long	2708443992
	.long	3505223656
	.long	1032912387
	.long	3711167273
	.long	1214238009
	.long	2611213035
	.long	3368625691
	.long	5584441
	.long	2611408711
	.long	2384597091
	.long	1152098306
	.long	3591611965
	.long	2962228819
	.long	3281323244
	.long	3071980506
	.long	3920864562
	.long	3855393805
	.long	2582452787
	.long	3343479107
	.long	1168797990
	.long	3053975971
	.long	3222544278
	.long	3909139483
	.long	1083468961
	.long	2101774947
	.long	2160722632
	.long	2110801092
	.long	4019214560
	.long	1350065479
	.long	2376260138
	.long	42240602
	.long	3769900699
	.long	2854857645
	.long	2686999005
	.long	4198697477
	.long	4081736268
	.long	2846299562
	.long	2478988733
	.long	427675101
	.long	2445602520
	.long	4233423029
	.long	2355264427
	.long	1558515652
	.long	2279564152
	.long	62666641
	.long	3298296210
	.long	1417642332
	.long	61859471
	.long	2115062510
	.long	2696409612
	.long	326492679
	.long	2534854374
	.long	1679925266
	.long	2160906787
	.long	3251033635
	.long	2671487866
	.long	2862247032
	.long	3574405396
	.long	3009660033
	.long	339349497
	.long	3007548001
	.long	2840034127
	.long	2252903388
	.long	1330394071
	.long	2157393054
	.long	2200692478
	.long	621144332
	.long	3987984878
	.long	1728372573
	.long	3841939891
	.long	2649374871
	.long	2008708732
	.long	1354882191
	.long	2904204888
	.long	3398077461
	.long	3906455313
	.long	2501808239
	.long	2340852904
	.long	257973487
	.long	728759453
	.long	30163463
	.long	848
	.long	3266633231
	.long	4249524844
	.long	2065907802
	.long	2501410748
	.long	2077120351
	.long	1559182036
	.long	2148606178
	.long	4119882265
	.long	315045067
	.long	14812806
	.long	1274331647
	.long	3156365682
	.long	238086315
	.long	2517423111
	.long	2041601736
	.long	2711078025
	.long	3652406632
	.long	753691269
	.long	2734041400
	.long	3820625006
	.long	2380832782
	.long	158270193
	.long	2340065183
	.long	2273321385
	.long	393890025
	.long	2894667190
	.long	1573700686
	.long	4092887025
	.long	464562897
	.long	303515614
	.long	3848216904
	.long	3328255744
	.long	1036006886
	.long	2121885428
	.long	3704818897
	.long	3211297370
	.long	1325824312
	.long	3493125121
	.long	3338748802
	.long	3232443331
	.long	3112594497
	.long	3307298736
	.long	2809691794
	.long	3731360010
	.long	3573632495
	.long	2312925567
	.long	629450595
	.long	3622381915
	.long	989010185
	.long	3398558103
	.long	4153621291
	.long	3607201927
	.long	2629100272
	.long	1953994464
	.long	1943384301
	.long	2759420002
	.long	2305022315
	.long	2391039855
	.long	612094863
	.long	3951603967
	.long	1340433416
	.long	3334026995
	.long	1491763737
	.long	4186248678
	.long	4198464715
	.long	87224683
	.long	4220145159
	.long	3881216024
	.long	2007772778
	.long	1722917095
	.long	2388984076
	.long	3799859053
	.long	732951595
	.long	3919415462
	.long	528829945
	.long	3610037081
	.long	3992395594
	.long	4037851002
	.long	1844118200
	.long	3714769074
	.long	3432638667
	.long	3108186388
	.long	746542694
	.long	3818486016
	.long	557823754
	.long	2427510280
	.long	3516120961
	.long	3892142094
	.long	1703329579
	.long	996501429
	.long	4081661750
	.long	3974028169
	.long	480847786
	.long	3006112619
	.long	1403431905
	.long	4065364793
	.long	1900483787
	.long	2649330773
	.long	651669812
	.long	4162988739
	.long	1619173352
	.long	1864819711
	.long	1131374484
	.long	4267744761
	.long	1834860043
	.long	2423625113
	.long	288099716
	.long	2190363742
	.long	2708503338
	.long	4126097102
	.long	3654529011
	.long	2251412339
	.long	3753107947
	.long	3496449780
	.long	2610603179
	.long	2317533940
	.long	3862344650
	.long	1030964414
	.long	451338797
	.long	2389337002
	.long	2524196363
	.long	569491811
	.long	2946926522
	.long	2467527406
	.long	196512138
	.long	497579000
	.long	2195809742
	.long	2552928386
	.long	1662952651
	.long	780129586
	.long	2880189481
	.long	2646511583
	.long	1545658730
	.long	789982365
	.long	191310475
	.long	2749424166
	.long	1470909419
	.long	1234100077
	.long	2044585165
	.long	2862876216
	.long	928663178
	.long	2102054363
	.long	3690288476
	.long	2988326180
	.long	2216922123
	.long	2305139484
	.long	4174545259
	.long	3130608048
	.long	1755153610
	.long	918723953
	.long	2738745609
	.long	3294047809
	.long	721728203
	.long	1773737866
	.long	4101484425
	.long	3433511350
	.long	1857351114
	.long	2879235855
	.long	1363799643
	.long	3536910801
	.long	4061718601
	.long	908917583
	.long	3005712244
	.long	4215915540
	.long	243897838
	.long	7660553
	.long	2731769662
	.long	2727825412
	.long	1695068807
	.long	2615038300
	.long	234665196
	.long	3149409338
	.long	2025012071
	.long	1264893574
	.long	1155162898
	.long	2643506208
	.long	3781832745
	.long	2126832235
	.long	1395780981
	.long	2754722035
	.long	4274723882
	.long	199777100
	.long	1853759306
	.long	3583381494
	.long	3072425958
	.long	993425135
	.long	3004341385
	.long	3435215656
	.long	3198694093
	.long	620059688
	.long	2085750463
	.long	2400804391
	.long	1026440073
	.long	2116228661
	.long	3422860059
	.long	3767238370
	.long	376557070
	.long	669608662
	.long	2826299222
	.long	4021726431
	.long	97861608
	.long	623049618
	.long	457143492
	.long	3494791496
	.long	647336461
	.long	20390326
	.long	156262610
	.long	3457815221
	.long	2242302115
	.long	1153736377
	.long	29917573
	.long	608
	.long	956831540
	.long	3804294695
	.long	1233656524
	.long	1169011973
	.long	3512247603
	.long	731707668
	.long	3804813427
	.long	3532496868
	.long	3861713254
	.long	3920998321
	.long	3249242362
	.long	2983748142
	.long	4254521127
	.long	3419828215
	.long	3612752470
	.long	3427243214
	.long	1865130836
	.long	1528519591
	.long	3104638828
	.long	2778394947
	.long	2428343797
	.long	2183879732
	.long	1050296790
	.long	3773933110
	.long	3652345540
	.long	2906583603
	.long	1426288469
	.long	3900013225
	.long	3885984869
	.long	2566242072
	.long	2597085889
	.long	3387828672
	.long	3402407620
	.long	2004411987
	.long	1378225957
	.long	3062031935
	.long	3648787989
	.long	92338370
	.long	4236899602
	.long	2817389734
	.long	1952242004
	.long	2304097414
	.long	127945474
	.long	2582972800
	.long	4281889637
	.long	2697225196
	.long	2199799063
	.long	2368525439
	.long	2882311446
	.long	2124741744
	.long	2454581530
	.long	2175676414
	.long	1363442533
	.long	235950345
	.long	3278607019
	.long	3991475143
	.long	2338347060
	.long	892652237
	.long	3886541672
	.long	3654822706
	.long	2206999749
	.long	359442761
	.long	1344250324
	.long	3448127457
	.long	2316542980
	.long	3829127353
	.long	220674587
	.long	3338303659
	.long	280815351
	.long	3269930602
	.long	4246425196
	.long	2475208849
	.long	3336161088
	.long	2206265573
	.long	1212344344
	.long	2473891454
	.long	491190659
	.long	2156317952
	.long	2981239741
	.long	2515194909
	.long	3609278228
	.long	1234146757
	.long	1026744012
	.long	2624029530
	.long	2704274272
	.long	1975162568
	.long	3276945619
	.long	2578639571
	.long	1034382165
	.long	2590969465
	.long	713762072
	.long	2201806508
	.long	4228198051
	.long	3980119693
	.long	4056525840
	.long	3097891989
	.long	2284336932
	.long	1932639591
	.long	1092540108
	.long	4171705026
	.long	1025654624
	.long	2806138305
	.long	3009647326
	.long	4248184906
	.long	1890367717
	.long	2289438980
	.long	2899757860
	.long	2719293081
	.long	2021534083
	.long	143536689
	.long	3592569555
	.long	2232683115
	.long	1092535430
	.long	1236892532
	.long	4153044331
	.long	2589276857
	.long	3416977538
	.long	1252322112
	.long	2546684106
	.long	3041465242
	.long	1082461383
	.long	209063033
	.long	689512483
	.long	3233542783
	.long	2486702483
	.long	195380297
	.long	3370408231
	.long	2473507057
	.long	2649928714
	.long	3477888857
	.long	4011137876
	.long	2392519727
	.long	735960098
	.long	892635115
	.long	2839435691
	.long	3648562506
	.long	4276128797
	.long	1399354710
	.long	1604489874
	.long	3300188966
	.long	3618086195
	.long	2363035047
	.long	3798645379
	.long	2910302351
	.long	2872403096
	.long	1995870309
	.long	1272108261
	.long	2577818883
	.long	1355290640
	.long	1576944867
	.long	29835783
	.long	528
	.long	602586217
	.long	2744039853
	.long	1836018387
	.long	1812008391
	.long	2257334696
	.long	1529386301
	.long	1071982020
	.long	1483132946
	.long	4031485769
	.long	2132222880
	.long	3196630878
	.long	2436179952
	.long	499786504
	.long	1744292863
	.long	2290711216
	.long	2504292584
	.long	1896329063
	.long	3237324739
	.long	2330780121
	.long	3744707929
	.long	1734827082
	.long	3296507779
	.long	823995655
	.long	3906271181
	.long	1416288288
	.long	2226659999
	.long	3321781362
	.long	3715853563
	.long	2544842791
	.long	504837419
	.long	2958966103
	.long	2231720942
	.long	750175301
	.long	4084077163
	.long	2549224941
	.long	3250807824
	.long	1369606438
	.long	3223323496
	.long	2083589876
	.long	3384767572
	.long	4248382916
	.long	4144081438
	.long	3229998485
	.long	3165770641
	.long	3223334087
	.long	2485373121
	.long	3818931504
	.long	3388848075
	.long	1252536709
	.long	1652370242
	.long	2624409840
	.long	2964694704
	.long	823791654
	.long	1167018965
	.long	2939238153
	.long	2953436515
	.long	884228550
	.long	292276865
	.long	734484637
	.long	2205305082
	.long	1913113287
	.long	3765099297
	.long	3745471985
	.long	4167966771
	.long	2931697765
	.long	3673300473
	.long	2182221826
	.long	2649024091
	.long	1975627272
	.long	3727134676
	.long	3712743943
	.long	2356773214
	.long	1995177638
	.long	1132588444
	.long	1959425557
	.long	2466886350
	.long	1844669961
	.long	3530525712
	.long	760398624
	.long	4132176514
	.long	1979460773
	.long	796554174
	.long	3170959411
	.long	3438043671
	.long	1989653354
	.long	84033345
	.long	407935807
	.long	2705661215
	.long	1743925380
	.long	3102969454
	.long	3987162576
	.long	3388329870
	.long	1765097355
	.long	2209606903
	.long	1187505799
	.long	2507493218
	.long	161532103
	.long	2079767141
	.long	2713931831
	.long	2181209439
	.long	3726577836
	.long	2678974047
	.long	4042300458
	.long	3044993725
	.long	569618662
	.long	2942019385
	.long	2267421895
	.long	3217189858
	.long	2072134893
	.long	690975306
	.long	3699981379
	.long	4279355559
	.long	3941553639
	.long	955448001
	.long	4102577460
	.long	2173892384
	.long	1871059570
	.long	2340022155
	.long	2864020166
	.long	2805153080
	.long	3271562790
	.long	928990650
	.long	4206766333
	.long	3357221519
	.long	685259147
	.long	3780305127
	.long	2513405378
	.long	4289834595
	.long	2048010913
	.long	1999500835
	.long	12816876
	.long	4227858976
	.long	255479044
	.long	3586592293
	.long	3058876998
	.long	264515361
	.long	3124897887
	.long	2490659405
	.long	1648870444
	.long	499533912
	.long	41235212
	.long	1135645
	.long	0
	.long	0
	.long	1870875844
	.long	237807939
	.long	0
	.long	0
	.long	1220251845
	.long	534075739
	.long	10
	.long	0
	.long	1566916386
	.long	2806728368
	.long	1584
	.long	0
	.long	359612094
	.long	1988210364
	.long	209791
	.long	0
	.long	4018390684
	.long	3143652161
	.long	23114812
	.long	0
	.long	1385310342
	.long	3191552623
	.long	2079730388
	.long	0
	.long	391712962
	.long	3496232483
	.long	3188676879
	.long	34
	.long	712670475
	.long	2384468018
	.long	3612170541
	.long	1928
	.long	2944407817
	.long	162857838
	.long	3871824152
	.long	79642
	.long	1897150183
	.long	2209645725
	.long	1648691441
	.long	2316814
	.long	4189666301
	.long	2103634775
	.long	2832836870
	.long	43914735
	.long	100148761
	.long	3544145710
	.long	882453749
	.long	480946030
	.long	743895637
	.long	1440146064
	.long	1167801510
	.long	2472892869
	.long	2527288726
	.long	3429197137
	.long	3389012105
	.long	3751366970
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3328931461
	.long	85211143
	.long	0
	.long	0
	.long	1806098240
	.long	3730920547
	.long	3
	.long	0
	.long	1452363271
	.long	241457122
	.long	632
	.long	0
	.long	2408523024
	.long	2523801313
	.long	87488
	.long	0
	.long	2245774023
	.long	1447128763
	.long	10087333
	.long	0
	.long	741575833
	.long	1516712297
	.long	950654827
	.long	0
	.long	188657751
	.long	2834063720
	.long	2756671478
	.long	16
	.long	1878890455
	.long	1600190830
	.long	298103694
	.long	968
	.long	2704961479
	.long	3096990934
	.long	62735862
	.long	41831
	.long	4224033960
	.long	872206772
	.long	3368762420
	.long	1269793
	.long	1493275548
	.long	4226875193
	.long	156115358
	.long	24976936
	.long	3675413693
	.long	3939858976
	.long	1188049005
	.long	281407884
	.long	4246420658
	.long	2584806369
	.long	1500891563
	.long	1472264476
	.long	3550606718
	.long	2355970614
	.long	848338822
	.long	2246143015
	.long	4294967292
	.long	0
	.long	4176598204
	.long	2421744458
	.long	12825068
	.long	4227858976
	.long	26255947
	.long	2214716320
	.long	3008850950
	.long	1148166722
	.long	3688411584
	.long	3263035913
	.long	28940749
	.long	823708345
	.long	2778463383
	.long	1133123
	.long	0
	.long	0
	.long	3375994644
	.long	239287696
	.long	0
	.long	0
	.long	1307882943
	.long	652616536
	.long	10
	.long	0
	.long	1617272991
	.long	332362201
	.long	1584
	.long	0
	.long	1120342694
	.long	786793736
	.long	208818
	.long	0
	.long	1754116211
	.long	4140995168
	.long	22886713
	.long	0
	.long	1065416575
	.long	2365212708
	.long	2045489262
	.long	0
	.long	4094841
	.long	1336050951
	.long	3805124179
	.long	33
	.long	2857461896
	.long	1181810454
	.long	3505581240
	.long	1861
	.long	317401726
	.long	1588400515
	.long	2236524720
	.long	75909
	.long	1498310037
	.long	3569634182
	.long	656796315
	.long	2175950
	.long	1147851401
	.long	3112077225
	.long	3624068591
	.long	40608505
	.long	3302439284
	.long	1412013598
	.long	1600554036
	.long	438608161
	.long	1138396370
	.long	2245711018
	.long	3467973442
	.long	2233633025
	.long	867533488
	.long	947112014
	.long	1198607031
	.long	3375241932
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1112979214
	.long	155431282
	.long	0
	.long	0
	.long	751906521
	.long	4198567473
	.long	6
	.long	0
	.long	1613582890
	.long	3858662071
	.long	1129
	.long	0
	.long	750469660
	.long	730914801
	.long	154615
	.long	0
	.long	3704035494
	.long	424802513
	.long	17595297
	.long	0
	.long	3547752153
	.long	653012047
	.long	1632696395
	.long	0
	.long	3895749118
	.long	1312132433
	.long	283540480
	.long	28
	.long	69337464
	.long	2039495909
	.long	480366968
	.long	1598
	.long	3999973752
	.long	512131576
	.long	3264765906
	.long	67376
	.long	1470627556
	.long	3527817596
	.long	506357093
	.long	1990112
	.long	1150644769
	.long	1706533685
	.long	1646793404
	.long	38078290
	.long	2272838918
	.long	315551185
	.long	931114118
	.long	418976801
	.long	799519248
	.long	833405850
	.long	3700723307
	.long	2157812839
	.long	1885423178
	.long	2894024424
	.long	986247108
	.long	3272778907
	.long	4294967291
	.long	0
	.long	205941362
	.long	2843814473
	.long	12816876
	.long	4227858976
	.long	4284396776
	.long	2636211962
	.long	2729248679
	.long	228940808
	.long	2721630221
	.long	4033427380
	.long	3518215373
	.long	3318265016
	.long	1583232401
	.long	1129188
	.long	0
	.long	0
	.long	2240090200
	.long	238747566
	.long	0
	.long	0
	.long	2182354368
	.long	422793662
	.long	10
	.long	0
	.long	2344384525
	.long	3611967473
	.long	1569
	.long	0
	.long	2363261376
	.long	55742926
	.long	206011
	.long	0
	.long	2169713945
	.long	2081106389
	.long	22457193
	.long	0
	.long	808249952
	.long	3928968053
	.long	1994189663
	.long	0
	.long	2498399120
	.long	610170797
	.long	3374767919
	.long	32
	.long	2499128949
	.long	806866088
	.long	2667994898
	.long	1785
	.long	1293127093
	.long	1533235693
	.long	3364558966
	.long	72094
	.long	3484449920
	.long	4142457389
	.long	393414345
	.long	2045553
	.long	1815566734
	.long	3205349654
	.long	1699048752
	.long	37804868
	.long	885836718
	.long	797019709
	.long	3601921623
	.long	405043970
	.long	1414187968
	.long	4238799387
	.long	2244838147
	.long	2051616278
	.long	2091776204
	.long	3735546130
	.long	1436809887
	.long	3093461814
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	194035164
	.long	141017056
	.long	0
	.long	0
	.long	3307569967
	.long	1206991859
	.long	6
	.long	0
	.long	3663448937
	.long	3391377880
	.long	1007
	.long	0
	.long	4126701083
	.long	2994857527
	.long	136467
	.long	0
	.long	1667733955
	.long	4058943102
	.long	15345719
	.long	0
	.long	2611265876
	.long	3831769830
	.long	1404776386
	.long	0
	.long	209321266
	.long	573187068
	.long	3359330694
	.long	23
	.long	1677262110
	.long	637572110
	.long	2122511782
	.long	1331
	.long	3496561941
	.long	3652813901
	.long	3559168047
	.long	55128
	.long	1750715127
	.long	4228815409
	.long	1050316
	.long	1598795
	.long	1840387030
	.long	2463042611
	.long	3094434052
	.long	30079053
	.long	2629602118
	.long	2049759618
	.long	945167723
	.long	326505049
	.long	3298129216
	.long	3410065465
	.long	1686243051
	.long	1666911635
	.long	3383305094
	.long	1693255217
	.long	2403972032
	.long	2519963779
	.long	4294967291
	.long	0
	.long	3238416553
	.long	3265778049
	.long	12825068
	.long	4227858976
	.long	3177200532
	.long	3057752043
	.long	1963397648
	.long	3926923754
	.long	1015831100
	.long	3054277238
	.long	214260357
	.long	3388682645
	.long	329260597
	.long	1120080
	.long	0
	.long	0
	.long	1129382972
	.long	236791308
	.long	0
	.long	0
	.long	200550114
	.long	4225205647
	.long	9
	.long	0
	.long	3987740840
	.long	662463985
	.long	1546
	.long	0
	.long	3625695977
	.long	942803776
	.long	202014
	.long	0
	.long	4082097994
	.long	2934588234
	.long	21910543
	.long	0
	.long	2081364333
	.long	826431917
	.long	1934499234
	.long	0
	.long	1761358361
	.long	1915853684
	.long	2581201468
	.long	31
	.long	1458338458
	.long	480335293
	.long	641318311
	.long	1709
	.long	2934587087
	.long	1935466844
	.long	4209158963
	.long	68508
	.long	2290297886
	.long	2520183995
	.long	2347470795
	.long	1930078
	.long	1214972608
	.long	3678131539
	.long	56536087
	.long	35444212
	.long	1624218450
	.long	2947499939
	.long	75007111
	.long	377830189
	.long	794554122
	.long	1484978356
	.long	1550658172
	.long	1907426674
	.long	1950966402
	.long	3741059307
	.long	468440577
	.long	2872207806
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2513119405
	.long	255601926
	.long	0
	.long	0
	.long	1340053149
	.long	1293535308
	.long	11
	.long	0
	.long	2520596056
	.long	243966073
	.long	1798
	.long	0
	.long	1240269441
	.long	1692289976
	.long	241208
	.long	0
	.long	486505533
	.long	4176826391
	.long	26843964
	.long	0
	.long	1085031321
	.long	2229862700
	.long	2429583838
	.long	0
	.long	3607912919
	.long	1284443184
	.long	2708483256
	.long	40
	.long	4286170020
	.long	2109173108
	.long	2872722597
	.long	2245
	.long	3765965368
	.long	2873757161
	.long	2280026745
	.long	91776
	.long	975034122
	.long	907445528
	.long	4183785771
	.long	2628820
	.long	2401876752
	.long	1790794470
	.long	4120636181
	.long	48923208
	.long	3456079977
	.long	3312351896
	.long	2533182876
	.long	526592659
	.long	300202939
	.long	3214183676
	.long	2700781633
	.long	2674019932
	.long	3409370037
	.long	142796896
	.long	590138558
	.long	4034348900
	.long	4294967290
	.long	0
	.long	2441521498
	.long	242594037
	.long	2326542
	.long	4227858728
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	292802143
	.long	2930268
	.long	0
	.long	0
	.long	535415153
	.long	618901269
	.long	0
	.long	0
	.long	1086559041
	.long	2797303288
	.long	25
	.long	0
	.long	4140762130
	.long	803311064
	.long	3903
	.long	0
	.long	3260682281
	.long	805348165
	.long	501094
	.long	0
	.long	946699523
	.long	2655018074
	.long	53417808
	.long	0
	.long	3184188054
	.long	357148693
	.long	343191017
	.long	1
	.long	2991785955
	.long	1885266767
	.long	2475080337
	.long	74
	.long	3202232014
	.long	1390745085
	.long	1906741299
	.long	3974
	.long	2460394114
	.long	145617371
	.long	1237048646
	.long	157131
	.long	2499420999
	.long	890518540
	.long	2984098479
	.long	4365826
	.long	4044160094
	.long	3502963765
	.long	4084883256
	.long	78771967
	.long	1855836510
	.long	1773383726
	.long	696038818
	.long	812348145
	.long	1371130586
	.long	4224739114
	.long	2913286396
	.long	3734813411
	.long	3022964429
	.long	4251306649
	.long	3856945722
	.long	3368235169
	.long	4294967295
	.long	0
	.long	3259021111
	.long	321006842
	.long	29835651
	.long	528
	.long	4287764257
	.long	2359162534
	.long	538435925
	.long	1893177075
	.long	2620595140
	.long	1125567552
	.long	2991073306
	.long	2159239491
	.long	623213113
	.long	2440954329
	.long	3454983919
	.long	2690441248
	.long	1981777827
	.long	3070641374
	.long	600396448
	.long	3989904562
	.long	503111015
	.long	2398913263
	.long	3729609173
	.long	2560895207
	.long	4264522276
	.long	1827510236
	.long	1453587439
	.long	2856034995
	.long	3530335573
	.long	2910390539
	.long	3830755809
	.long	3395214488
	.long	581887236
	.long	170861812
	.long	3783767784
	.long	3801720292
	.long	3874334995
	.long	3776894534
	.long	3941576344
	.long	4151651145
	.long	672178724
	.long	1876155966
	.long	2300153670
	.long	2280168405
	.long	2930820789
	.long	310363638
	.long	2337608734
	.long	2504022121
	.long	679615860
	.long	1866145491
	.long	925134179
	.long	2749395452
	.long	4164495461
	.long	1691717726
	.long	3504269286
	.long	3018609133
	.long	4091749652
	.long	2913918638
	.long	75440378
	.long	3313861766
	.long	2260863989
	.long	1783565696
	.long	3478989690
	.long	3637556251
	.long	386210930
	.long	165743778
	.long	2898550894
	.long	3992273458
	.long	138300613
	.long	2474605972
	.long	1444508693
	.long	2190379430
	.long	3716173044
	.long	211495398
	.long	2076026756
	.long	2402948622
	.long	884026613
	.long	3273453185
	.long	2774793032
	.long	2635331838
	.long	862601524
	.long	3461868441
	.long	1569319307
	.long	2889017487
	.long	2379289701
	.long	4014175908
	.long	4105967090
	.long	3165345439
	.long	4280005796
	.long	3658970897
	.long	1734788832
	.long	3465258574
	.long	1142155173
	.long	2128929843
	.long	1454912392
	.long	3791483352
	.long	1572825138
	.long	4100167805
	.long	2209491991
	.long	4149468636
	.long	1302034421
	.long	2240019943
	.long	2903825538
	.long	2241250238
	.long	1470684676
	.long	2909933371
	.long	1614391894
	.long	2346034557
	.long	3663942245
	.long	150553254
	.long	581996432
	.long	2853498413
	.long	2851485478
	.long	1062112106
	.long	2360862824
	.long	4080591848
	.long	3037166561
	.long	3942859811
	.long	2491765597
	.long	2637624859
	.long	3670749589
	.long	1175994297
	.long	2568760650
	.long	2257943156
	.long	4233645441
	.long	2769385451
	.long	3183971100
	.long	4072128784
	.long	1432427446
	.long	3380851706
	.long	2013943478
	.long	2236760609
	.long	4221662466
	.long	494326959
	.long	27640328
	.long	432
	.long	4152930537
	.long	3261335210
	.long	1647304022
	.long	2421513631
	.long	0
	.long	2182303040
	.long	2
	.long	0
	.long	2440707372
	.long	2648025301
	.long	1920343642
	.long	4033309261
	.long	228472035
	.long	951557190
	.long	1587559315
	.long	2974493459
	.long	2596925677
	.long	961292909
	.long	2340517507
	.long	4069972126
	.long	3800775586
	.long	2435894136
	.long	1723862257
	.long	2750240234
	.long	1983413582
	.long	438211295
	.long	1298530652
	.long	3389699362
	.long	2457352961
	.long	2834143237
	.long	3251433817
	.long	4162244914
	.long	2624345295
	.long	2198133979
	.long	1723202388
	.long	2299670239
	.long	3808420354
	.long	1821904843
	.long	3390094422
	.long	2531701946
	.long	1851152974
	.long	2701629785
	.long	1724191120
	.long	2482272915
	.long	648872229
	.long	865545185
	.long	2559674717
	.long	2406027884
	.long	910915727
	.long	2144582723
	.long	1642929274
	.long	4137212570
	.long	2336042754
	.long	239465408
	.long	3865561517
	.long	3439416156
	.long	2422534192
	.long	3569472253
	.long	2978472155
	.long	2559905612
	.long	2875291331
	.long	4046158321
	.long	3949085588
	.long	3503570860
	.long	2128927569
	.long	1225899162
	.long	2147335523
	.long	2230618794
	.long	2283389284
	.long	1333752198
	.long	1465231956
	.long	2336128911
	.long	2753251442
	.long	2966939997
	.long	2922138241
	.long	2536578790
	.long	2666112773
	.long	3385813147
	.long	623901210
	.long	3462131994
	.long	668414005
	.long	3701008851
	.long	3126176950
	.long	3345113301
	.long	358440900
	.long	3717742396
	.long	3947881290
	.long	3263003015
	.long	2610140914
	.long	3090322344
	.long	447998310
	.long	4170300383
	.long	397141160
	.long	2942373728
	.long	3824192808
	.long	2641349034
	.long	2658982609
	.long	4233303079
	.long	2524818000
	.long	3312990274
	.long	228893295
	.long	2515147602
	.long	3476745001
	.long	2698739548
	.long	2970265182
	.long	931672352
	.long	30032391
	.long	720
	.long	1031893951
	.long	2915037812
	.long	3514686481
	.long	3480585297
	.long	183393903
	.long	2624056858
	.long	28546027
	.long	1683875686
	.long	92539635
	.long	470440642
	.long	3104544566
	.long	2542111380
	.long	2423082850
	.long	4223302981
	.long	1880783390
	.long	3172707188
	.long	2733291596
	.long	4028584963
	.long	3133521808
	.long	2155982074
	.long	1545518760
	.long	2360167907
	.long	1514977624
	.long	3361750727
	.long	1775829359
	.long	1268098089
	.long	293245362
	.long	2922889930
	.long	4260567272
	.long	2130738070
	.long	78442230
	.long	3811451694
	.long	3185114409
	.long	1528069252
	.long	1469017227
	.long	3009687218
	.long	817620842
	.long	541098266
	.long	3429120157
	.long	3247469802
	.long	1386496969
	.long	2684347079
	.long	284179228
	.long	4184596161
	.long	3638765322
	.long	371021313
	.long	2606681771
	.long	2672602875
	.long	1153440841
	.long	681666828
	.long	2588485140
	.long	3667250000
	.long	2323000426
	.long	796579017
	.long	4170606170
	.long	2503045697
	.long	3134166249
	.long	3700272898
	.long	3134839549
	.long	3388945252
	.long	703661802
	.long	1462722757
	.long	1341451622
	.long	2296227379
	.long	443948971
	.long	1838486354
	.long	1829780795
	.long	3113397699
	.long	3423557800
	.long	3677683794
	.long	2988390605
	.long	4220311422
	.long	2364782699
	.long	377168398
	.long	988912816
	.long	2859792771
	.long	2272449576
	.long	2425114493
	.long	3394168911
	.long	3875037301
	.long	2614813611
	.long	1136311064
	.long	3219605340
	.long	2624778863
	.long	2109052744
	.long	2469309985
	.long	1975730813
	.long	3554907757
	.long	1295201579
	.long	391105893
	.long	1554957189
	.long	2406549547
	.long	576394664
	.long	1543244524
	.long	1882413337
	.long	3257006239
	.long	1017054059
	.long	1977139902
	.long	2097363325
	.long	2203699043
	.long	1908366122
	.long	67963273
	.long	641406479
	.long	2982177629
	.long	3746196521
	.long	1016392834
	.long	2077695946
	.long	4016838075
	.long	2069372298
	.long	3280381472
	.long	2122130600
	.long	2685921991
	.long	1883255817
	.long	3264982359
	.long	4098768927
	.long	3758603957
	.long	3506210954
	.long	1836307129
	.long	2822017765
	.long	2764063917
	.long	2012804049
	.long	1836928984
	.long	1054120456
	.long	2619585574
	.long	1055724672
	.long	2194227394
	.long	2447214543
	.long	2687690031
	.long	1327427687
	.long	1655178620
	.long	3028234167
	.long	3556180690
	.long	2927424394
	.long	229520228
	.long	65594667
	.long	3227047264
	.long	1689555076
	.long	4247943379
	.long	172770419
	.long	3675678736
	.long	657796043
	.long	288113065
	.long	2256116365
	.long	2460956283
	.long	2381283399
	.long	3352776929
	.long	3687288864
	.long	3224422563
	.long	672976042
	.long	857803139
	.long	2956970725
	.long	3269238901
	.long	2326138918
	.long	3946484787
	.long	3349422960
	.long	2579382025
	.long	3310332749
	.long	822582390
	.long	2446752418
	.long	2381540812
	.long	4287345289
	.long	449373617
	.long	1629474989
	.long	2287047921
	.long	3156879118
	.long	2932312680
	.long	4059116599
	.long	3501115759
	.long	2653478600
	.long	1047527497
	.long	2387670671
	.long	2153577515
	.long	4278907533
	.long	1522300626
	.long	2248985742
	.long	2923309751
	.long	2050095503
	.long	1358740372
	.long	29884806
	.long	576
	.long	189636935
	.long	2307472755
	.long	73850715
	.long	2629855449
	.long	3883672823
	.long	618265067
	.long	3875053116
	.long	3917969758
	.long	1449807750
	.long	2492768441
	.long	781111022
	.long	3190435238
	.long	1482976409
	.long	3746699123
	.long	673296220
	.long	2543135664
	.long	1097012132
	.long	2731166546
	.long	2691838079
	.long	3591085575
	.long	4108503415
	.long	2072634685
	.long	3936623385
	.long	2185856579
	.long	1923690262
	.long	3646023575
	.long	4216469600
	.long	2465929342
	.long	431460727
	.long	596938224
	.long	2153081083
	.long	2604407262
	.long	2280668022
	.long	3134357508
	.long	2589033740
	.long	2773504679
	.long	2807242375
	.long	2817121462
	.long	2773778968
	.long	2997091783
	.long	1733040470
	.long	818736373
	.long	2513276750
	.long	3273702286
	.long	3677662487
	.long	322072717
	.long	1865747051
	.long	3233516951
	.long	2915555540
	.long	937584400
	.long	659945040
	.long	2820584615
	.long	2133192181
	.long	2691571362
	.long	4196086611
	.long	2727659532
	.long	896759672
	.long	1769906504
	.long	3035342266
	.long	2750209132
	.long	1576632880
	.long	1478621509
	.long	1238707153
	.long	3507491969
	.long	126298229
	.long	3524093915
	.long	2681332129
	.long	2656870434
	.long	2282193125
	.long	2123250942
	.long	2537777848
	.long	2313555892
	.long	2236573588
	.long	3014805274
	.long	1599407648
	.long	4251279069
	.long	1359728578
	.long	2438133930
	.long	1558056398
	.long	2562927734
	.long	3023480919
	.long	1448378982
	.long	1701562892
	.long	2340149056
	.long	1506041285
	.long	779806113
	.long	4234929211
	.long	2470659521
	.long	3801687606
	.long	3749889093
	.long	4112153792
	.long	4129315601
	.long	3087235106
	.long	3558690450
	.long	4001893244
	.long	3474539130
	.long	508945001
	.long	2957303106
	.long	3788800591
	.long	2700334225
	.long	1697795651
	.long	4047812965
	.long	4036703836
	.long	3519182687
	.long	4240516712
	.long	1475971627
	.long	1575184661
	.long	2515949915
	.long	1673437412
	.long	2377231640
	.long	3395057130
	.long	2344040799
	.long	1065264411
	.long	1317525997
	.long	664446688
	.long	3082671496
	.long	1521403251
	.long	50174553
	.long	2566979079
	.long	3666263822
	.long	3888565980
	.long	425393181
	.long	383974
	.long	2211658859
	.long	3190477332
	.long	1452652764
	.long	881590408
	.long	2658571543
	.long	2768922591
	.long	607349007
	.long	1340513656
	.long	2982866930
	.long	2042743989
	.long	2907958666
	.long	2030930082
	.long	2170145182
	.long	1493033626
	.long	3329402172
	.long	1968890141
	.long	2331822590
	.long	2408886146
	.long	1783184274
	.long	12825068
	.long	4227858976
	.long	2710581281
	.long	3153889734
	.long	3238022506
	.long	4176533952
	.long	1531340198
	.long	4025201404
	.long	2485789300
	.long	650490227
	.long	2321085797
	.long	625172
	.long	0
	.long	0
	.long	1069281709
	.long	251624587
	.long	0
	.long	0
	.long	4197074614
	.long	91585281
	.long	10
	.long	0
	.long	2135656897
	.long	267376913
	.long	1588
	.long	0
	.long	3403656332
	.long	967418652
	.long	209472
	.long	0
	.long	463318414
	.long	1993660658
	.long	23089564
	.long	0
	.long	1930917064
	.long	2053446879
	.long	2077215423
	.long	0
	.long	657157488
	.long	2477268698
	.long	3166180846
	.long	34
	.long	1532275274
	.long	3903843069
	.long	3017665745
	.long	1932
	.long	3296114418
	.long	3153699036
	.long	1621212020
	.long	80134
	.long	1258014930
	.long	2176092298
	.long	1175504463
	.long	2347208
	.long	1382073311
	.long	570037013
	.long	1129603108
	.long	44970025
	.long	2202866424
	.long	1579426883
	.long	3017508354
	.long	499448209
	.long	4088025423
	.long	891205158
	.long	1193661411
	.long	2604580528
	.long	2532457727
	.long	3946349630
	.long	1951385266
	.long	3993662730
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2589520468
	.long	104644116
	.long	0
	.long	0
	.long	3884787564
	.long	2761065855
	.long	3
	.long	0
	.long	3187602994
	.long	4135562377
	.long	626
	.long	0
	.long	1724989792
	.long	1713856870
	.long	86304
	.long	0
	.long	2484708115
	.long	4089533751
	.long	10036248
	.long	0
	.long	275209371
	.long	2697520380
	.long	952228456
	.long	0
	.long	3236472558
	.long	1226316501
	.long	3598685087
	.long	16
	.long	3284813591
	.long	1063192554
	.long	3944393543
	.long	991
	.long	1278466978
	.long	2628971736
	.long	1168074903
	.long	43599
	.long	848711725
	.long	2087159795
	.long	759297680
	.long	1353656
	.long	1425592604
	.long	1129358307
	.long	2754552431
	.long	27432831
	.long	3204498819
	.long	300826889
	.long	3605445756
	.long	320203780
	.long	1698536723
	.long	3065896691
	.long	309908814
	.long	1734061231
	.long	3912468499
	.long	396401915
	.long	3674073704
	.long	2719285114
	.long	4294967292
	.long	0
	.long	3981914375
	.long	2206547233
	.long	12816876
	.long	4227858976
	.long	4096021827
	.long	3999001670
	.long	1063371522
	.long	3989528078
	.long	874562813
	.long	2447814409
	.long	1858302676
	.long	4162682718
	.long	1605953959
	.long	1135435
	.long	0
	.long	0
	.long	2549031326
	.long	239160205
	.long	0
	.long	0
	.long	454697744
	.long	620180210
	.long	10
	.long	0
	.long	2436299213
	.long	3355768607
	.long	1582
	.long	0
	.long	351390256
	.long	799610387
	.long	208707
	.long	0
	.long	25800287
	.long	698915934
	.long	22891066
	.long	0
	.long	4130988338
	.long	676217935
	.long	2048657779
	.long	0
	.long	2657270905
	.long	3829767903
	.long	59128891
	.long	34
	.long	1037582638
	.long	2935003680
	.long	713523288
	.long	1875
	.long	901580945
	.long	1268517119
	.long	2358220346
	.long	76826
	.long	1912766477
	.long	222206216
	.long	632792841
	.long	2216812
	.long	4065471431
	.long	1410808044
	.long	2022595908
	.long	41710361
	.long	438075994
	.long	598703158
	.long	1085011105
	.long	454516776
	.long	604410846
	.long	4058435110
	.long	2236814241
	.long	2333657313
	.long	2448294355
	.long	2704068860
	.long	1114425723
	.long	3548452336
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3769919769
	.long	153956907
	.long	0
	.long	0
	.long	646070119
	.long	4028818701
	.long	6
	.long	0
	.long	2588679469
	.long	1052177761
	.long	1128
	.long	0
	.long	2209274989
	.long	523514771
	.long	155204
	.long	0
	.long	2126396778
	.long	3565792859
	.long	17775584
	.long	0
	.long	2013180833
	.long	1908602229
	.long	1662519428
	.long	0
	.long	3793993839
	.long	1441646378
	.long	3693455390
	.long	28
	.long	3379423244
	.long	3965194379
	.long	1988414356
	.long	1663
	.long	1712981619
	.long	805217952
	.long	1789525478
	.long	71195
	.long	1327601011
	.long	2853644807
	.long	2041759317
	.long	2141557
	.long	944030315
	.long	2463564559
	.long	1071870408
	.long	41839542
	.long	3377497270
	.long	1387686562
	.long	1015294515
	.long	470457582
	.long	3802633795
	.long	4019363939
	.long	1055805497
	.long	2472026772
	.long	1903142729
	.long	1756051618
	.long	411357926
	.long	3811072860
	.long	4294967291
	.long	0
	.long	2342180209
	.long	2629357739
	.long	12825068
	.long	4227858976
	.long	4176598204
	.long	2421744458
	.long	608412369
	.long	593975024
	.long	2402997778
	.long	853372562
	.long	4106233885
	.long	999432807
	.long	1956711771
	.long	1129902
	.long	0
	.long	0
	.long	3668311682
	.long	238634701
	.long	0
	.long	0
	.long	1969498866
	.long	413457374
	.long	10
	.long	0
	.long	2816017558
	.long	1616523229
	.long	1570
	.long	0
	.long	3757647515
	.long	3035182130
	.long	206269
	.long	0
	.long	2187487164
	.long	1784472528
	.long	22516356
	.long	0
	.long	559031239
	.long	1740008181
	.long	2003371129
	.long	0
	.long	661394403
	.long	1757559962
	.long	108872688
	.long	33
	.long	2108184885
	.long	2675738113
	.long	243072689
	.long	1805
	.long	1896660970
	.long	944386947
	.long	1395829566
	.long	73206
	.long	2645692570
	.long	1073605584
	.long	391090218
	.long	2088195
	.long	3576186609
	.long	249080280
	.long	1318613440
	.long	38820429
	.long	1698387538
	.long	1714182690
	.long	2486672428
	.long	418377280
	.long	786186663
	.long	550325920
	.long	34733209
	.long	2130294753
	.long	123703415
	.long	1830333669
	.long	555170427
	.long	3225161241
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	372615672
	.long	141112893
	.long	0
	.long	0
	.long	3315900643
	.long	1327290262
	.long	6
	.long	0
	.long	3811139837
	.long	3724885846
	.long	1016
	.long	0
	.long	989130504
	.long	3252199933
	.long	138436
	.long	0
	.long	1221640456
	.long	1442402103
	.long	15666709
	.long	0
	.long	4152936768
	.long	1813348380
	.long	1445091735
	.long	0
	.long	4193896088
	.long	3387134533
	.long	2948009907
	.long	24
	.long	2262704738
	.long	1989650954
	.long	3803792063
	.long	1396
	.long	2783464578
	.long	434957745
	.long	1316597970
	.long	58551
	.long	1368551733
	.long	528983851
	.long	2424303193
	.long	1721511
	.long	2488297655
	.long	3464245304
	.long	3652031881
	.long	32861225
	.long	3506409524
	.long	3965838759
	.long	4041530878
	.long	361842100
	.long	1191665382
	.long	3299555859
	.long	1239122770
	.long	1871471448
	.long	3889602930
	.long	4175372373
	.long	3489971328
	.long	2859914059
	.long	4294967291
	.long	0
	.long	4097644266
	.long	3051847358
	.long	12816876
	.long	4227858976
	.long	205941362
	.long	2843814473
	.long	1373054629
	.long	908624394
	.long	572230967
	.long	3772158671
	.long	2295107357
	.long	875890232
	.long	1676909203
	.long	1121011
	.long	0
	.long	0
	.long	3140758008
	.long	236890113
	.long	0
	.long	0
	.long	78883718
	.long	4270561461
	.long	9
	.long	0
	.long	653004353
	.long	1003106748
	.long	1549
	.long	0
	.long	1997655276
	.long	2810156938
	.long	202673
	.long	0
	.long	816790074
	.long	126500350
	.long	22018895
	.long	0
	.long	483718758
	.long	1693602023
	.long	1948244742
	.long	0
	.long	1719888843
	.long	3024114415
	.long	3912173698
	.long	31
	.long	857585795
	.long	2024050081
	.long	2152155389
	.long	1731
	.long	2161391665
	.long	4153338473
	.long	3453597175
	.long	69665
	.long	690916841
	.long	3408680089
	.long	1481878010
	.long	1970802
	.long	4244781337
	.long	1956485181
	.long	3004849658
	.long	36347304
	.long	3508046261
	.long	2375922782
	.long	3796433011
	.long	389053187
	.long	1871421647
	.long	319248902
	.long	2844007792
	.long	1971197416
	.long	814256252
	.long	534724998
	.long	4096580698
	.long	2976687819
	.long	4294967295
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1862921762
	.long	128829141
	.long	0
	.long	0
	.long	2226943868
	.long	3083060057
	.long	5
	.long	0
	.long	3790004556
	.long	3473372331
	.long	913
	.long	0
	.long	1912405170
	.long	4207391429
	.long	123223
	.long	0
	.long	2818206388
	.long	546560525
	.long	13795947
	.long	0
	.long	926024359
	.long	466562391
	.long	1257280106
	.long	0
	.long	3443963035
	.long	4102181261
	.long	824629355
	.long	21
	.long	1095781235
	.long	2590304783
	.long	3003378070
	.long	1181
	.long	3706684271
	.long	2607807817
	.long	1026954074
	.long	48765
	.long	2542600140
	.long	2410120938
	.long	1491060906
	.long	1411271
	.long	1675138874
	.long	3651173547
	.long	1198416212
	.long	26539599
	.long	842200953
	.long	401492709
	.long	2211503215
	.long	288538794
	.long	1943642431
	.long	940486719
	.long	4288006473
	.long	1478519853
	.long	2666730960
	.long	3798148012
	.long	941219805
	.long	2247821888
	.long	4294967291
	.long	0
	.long	3590128009
	.long	612654688
	.long	716769122
	.long	2
	.long	1828204245
	.long	1775926740
	.long	862431319
	.long	1672
	.long	437955120
	.long	677313132
	.long	3681074436
	.long	185717
	.long	2884200252
	.long	2090641138
	.long	3992138721
	.long	6428191
	.long	3221531446
	.long	2371910721
	.long	4237081542
	.long	91796659
	.long	3709028830
	.long	1986863955
	.long	2283696654
	.long	618613015
	.long	1322699753
	.long	485003501
	.long	4170891366
	.long	2092077992
	.long	1956040154
	.long	1117518145
	.long	962574367
	.long	3571830532
	.long	3307700780
	.long	4011234461
	.long	1774218710
	.long	2881343143
	.long	391584861
	.long	529954976
	.long	2825691418
	.long	856722023
	.long	0
	.long	0
	.long	3298396499
	.long	2495110602
	.long	2303999958
	.long	1
	.long	2542348583
	.long	3627482097
	.long	3224340298
	.long	1074
	.long	967783320
	.long	1406672934
	.long	4087851085
	.long	117404
	.long	4177416572
	.long	3106181469
	.long	261241013
	.long	4043478
	.long	202539330
	.long	3294545774
	.long	2496277142
	.long	57629426
	.long	720582238
	.long	1814713548
	.long	3091376339
	.long	388014697
	.long	146526450
	.long	4271301162
	.long	2338306966
	.long	1311627297
	.long	2070656804
	.long	1921999658
	.long	2258254113
	.long	2238807899
	.long	242296837
	.long	3253310178
	.long	207686852
	.long	1805761504
	.long	0
	.long	0
	.long	0
	.long	536870912
	.long	3
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3266800725
	.long	2413762801
	.long	3330613244
	.long	98
	.long	1201359419
	.long	2714872150
	.long	3208385380
	.long	20210
	.long	1956238602
	.long	4223228119
	.long	2784422263
	.long	1028397
	.long	3439473030
	.long	2410851869
	.long	3292470576
	.long	20253447
	.long	1686397776
	.long	592553810
	.long	969547707
	.long	185741930
	.long	3468583933
	.long	1250725515
	.long	2117116206
	.long	869064170
	.long	3909100790
	.long	1843251452
	.long	3871208694
	.long	2155104570
	.long	4148130288
	.long	2464912491
	.long	1300300682
	.long	2809133898
	.long	4014582188
	.long	344247779
	.long	2796123058
	.long	1784697576
	.long	195792426
	.long	264977488
	.long	3560329357
	.long	428361011
	.long	4294967290
	.long	0
	.long	258978617
	.long	1401350217
	.long	414758348
	.long	0
	.long	3858563520
	.long	693344438
	.long	929988021
	.long	164
	.long	3412169140
	.long	2002911005
	.long	1900957617
	.long	27838
	.long	201299732
	.long	2653786175
	.long	836100553
	.long	1342443
	.long	112595646
	.long	24843945
	.long	2773043450
	.long	25894040
	.long	839565763
	.long	2401088019
	.long	1862800896
	.long	235227666
	.long	241900887
	.long	2111181527
	.long	559246656
	.long	1095314964
	.long	1679323588
	.long	170716610
	.long	3844392689
	.long	2709031418
	.long	2888224069
	.long	91827283
	.long	2484028115
	.long	3525834755
	.long	3046356865
	.long	465368430
	.long	2789327597
	.long	2238015503
	.long	0
	.long	0
	.long	0
	.long	536870912
	.long	3
	.long	0
	.long	1629800756
	.long	211083125
	.long	74954925
	.long	0
	.long	1545370875
	.long	1864504884
	.long	4276848488
	.long	99
	.long	535444102
	.long	452832597
	.long	341934841
	.long	25395
	.long	995111971
	.long	3604718273
	.long	313159131
	.long	1483348
	.long	467529526
	.long	927033629
	.long	3631579950
	.long	30995691
	.long	2550211567
	.long	3003753623
	.long	1495846756
	.long	281435025
	.long	1095077789
	.long	2892497672
	.long	2221613791
	.long	1215372727
	.long	1191801615
	.long	1667552124
	.long	2428434537
	.long	2551716115
	.long	1201465291
	.long	1874197394
	.long	578557072
	.long	2461680298
	.long	391584923
	.long	529954976
	.long	2825691418
	.long	856722023
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	677405811
	.long	113109491
	.long	129649144
	.long	114
	.long	3825428504
	.long	3691782001
	.long	3484121609
	.long	31057
	.long	2790365057
	.long	1503600960
	.long	2128362517
	.long	1842108
	.long	1815120706
	.long	3864377339
	.long	1520525307
	.long	38689413
	.long	2352056310
	.long	3376817110
	.long	3997253088
	.long	352036787
	.long	1198120374
	.long	2050750776
	.long	3970142361
	.long	1521787235
	.long	3363842978
	.long	1133863107
	.long	832963017
	.long	3196692150
	.long	3821983599
	.long	1263968260
	.long	873004851
	.long	3084767199
	.long	0
	.long	0
	.long	0
	.long	1073741824
	.long	2
	.long	0
	.long	2651322308
	.long	3386393833
	.long	2831116449
	.long	5
	.long	4289850283
	.long	2368640529
	.long	3021143330
	.long	3284
	.long	1866387201
	.long	153493322
	.long	2018528397
	.long	295326
	.long	712913332
	.long	628654228
	.long	3881360594
	.long	8668773
	.long	2507640802
	.long	239249479
	.long	2566710297
	.long	108181087
	.long	2758175823
	.long	567857163
	.long	2134567225
	.long	650389544
	.long	3640757112
	.long	2804269303
	.long	645858985
	.long	1991928169
	.long	804414238
	.long	2280600022
	.long	3914463340
	.long	3114948090
	.long	1937917503
	.long	2252804771
	.long	1940556944
	.long	2322010631
	.long	293688586
	.long	2544949880
	.long	3193010387
	.long	642541517
	.long	4294967291
	.long	0
	.long	453133727
	.long	877370797
	.long	1558279443
	.long	6
	.long	2547899573
	.long	3360184680
	.long	1178296802
	.long	2954
	.long	1755214995
	.long	2061159704
	.long	3531983056
	.long	253712
	.long	543505238
	.long	128612626
	.long	629116815
	.long	7334610
	.long	100838038
	.long	3022980312
	.long	2298747174
	.long	90954203
	.long	3014766307
	.long	96539771
	.long	3590315363
	.long	545180116
	.long	2964798914
	.long	2705356255
	.long	3013291713
	.long	1667101568
	.long	3407956254
	.long	2146783552
	.long	2206958848
	.long	2604739160
	.long	1922050233
	.long	3165283613
	.long	2563978608
	.long	1940712607
	.long	0
	.long	0
	.long	0
	.long	536870912
	.long	3
	.long	0
	.long	2797952740
	.long	3054806814
	.long	120
	.long	0
	.long	18168
	.long	0
	.long	1890690921
	.long	3263011055
	.long	3960
	.long	0
	.long	18872
	.long	0
	.long	3238416553
	.long	3265778049
	.long	8064
	.long	0
	.long	18168
	.long	0
	.long	4097644266
	.long	3051847358
	.long	13440
	.long	0
	.long	18872
	.long	0
	.long	0
	.long	0
	.long	1313084713
	.long	2734261102
	.long	4113882560
	.long	4230436817
	.long	0
	.long	4294967295
	.long	4105493977
	.long	3790490747
	.long	2885543867
	.long	1813033052
	.long	0
	.long	0
	.type	__bessel_x_table,@object
	.size	__bessel_x_table,19696
	.align 8
.L_2il0floatpacket.96:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.96,@object
	.size	.L_2il0floatpacket.96,8
	.align 8
.L_2il0floatpacket.97:
	.long	0x5a31a4be,0x3ff71547
	.type	.L_2il0floatpacket.97,@object
	.size	.L_2il0floatpacket.97,8
	.align 8
.L_2il0floatpacket.98:
	.long	0x9a49f2dc,0x400d3643
	.type	.L_2il0floatpacket.98,@object
	.size	.L_2il0floatpacket.98,8
	.align 8
.L_2il0floatpacket.99:
	.long	0x00000000,0x40d00000
	.type	.L_2il0floatpacket.99,@object
	.size	.L_2il0floatpacket.99,8
	.align 8
.L_2il0floatpacket.100:
	.long	0x00000000,0xc0d01b80
	.type	.L_2il0floatpacket.100,@object
	.size	.L_2il0floatpacket.100,8
	.align 8
.L_2il0floatpacket.104:
	.long	0x20000000,0x405cdc55
	.type	.L_2il0floatpacket.104,@object
	.size	.L_2il0floatpacket.104,8
	.align 8
.L_2il0floatpacket.105:
	.long	0x00000000,0x40140000
	.type	.L_2il0floatpacket.105,@object
	.size	.L_2il0floatpacket.105,8
	.align 8
.L_2il0floatpacket.106:
	.long	0xe0000000,0x3fefffff
	.type	.L_2il0floatpacket.106,@object
	.size	.L_2il0floatpacket.106,8
	.align 8
.L_2il0floatpacket.109:
	.long	0x00000000,0x43e00000
	.type	.L_2il0floatpacket.109,@object
	.size	.L_2il0floatpacket.109,8
	.align 8
__bessel_error_codes:
	.long	0x00000000,0x00000000
	.long	0x00000081,0x00000000
	.long	0x00000082,0x00000000
	.long	0x00000083,0x00000000
	.long	0x00000084,0x00000000
	.long	0x0000008c,0x00000000
	.long	0x00000090,0x00000000
	.long	0x0000008f,0x00000000
	.type	__bessel_error_codes,@object
	.size	__bessel_error_codes,64
	.align 4
.L_2il0floatpacket.101:
	.long	0x410f95e2
	.type	.L_2il0floatpacket.101,@object
	.size	.L_2il0floatpacket.101,4
	.align 4
.L_2il0floatpacket.102:
	.long	0x41a3dd91
	.type	.L_2il0floatpacket.102,@object
	.size	.L_2il0floatpacket.102,4
	.align 4
.L_2il0floatpacket.103:
	.long	0x3fb8aa3b
	.type	.L_2il0floatpacket.103,@object
	.size	.L_2il0floatpacket.103,4
	.align 4
.L_2il0floatpacket.107:
	.long	0x5f000000
	.type	.L_2il0floatpacket.107,@object
	.size	.L_2il0floatpacket.107,4
	.align 4
.L_2il0floatpacket.108:
	.long	0x3f800000
	.type	.L_2il0floatpacket.108,@object
	.size	.L_2il0floatpacket.108,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
