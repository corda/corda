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
	.file "dpml_ux_sqrt.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_sqrt_evaluation__
	.text
       .align    16,0x90
	.globl __dpml_ux_sqrt_evaluation__
__dpml_ux_sqrt_evaluation__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B1.1:
	.cfi_startproc
..___tag_value___dpml_ux_sqrt_evaluation__.1:
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
        subq      $104, %rsp
	.cfi_def_cfa_offset 160
        movq      %rdi, %rbx
        movq      %fs:40, %rax
        lea       (%rsp), %rdi
        xorq      %rsp, %rax
        movq      %rdx, %r12
        movq      %rax, 88(%rdi)
        movq      %rsi, %rbp
..___tag_value___dpml_ux_sqrt_evaluation__.16:
        call      fegetenv@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.17:
..B1.2:
        movq      8(%rbx), %r8
        movq      %r8, %r10
        shrq      $11, %r10
        movq      $0x3fd0000000000000, %r9
        movl      4(%rbx), %eax
        addq      %r9, %r10
        movl      %eax, %r9d
        movq      %r8, %r13
        andl      $1, %r9d
        movq      %r8, %r15
        addl      %r9d, %eax
        pxor      %xmm2, %xmm2
        shrq      $56, %r8
        pxor      %xmm4, %xmm4
        movq      %r10, 80(%rsp)
        pxor      %xmm7, %xmm7
        movq      __sqrt_t_table@GOTPCREL(%rip), %r10
        lea       40(%r9), %r14d
        shll      $7, %r9d
        movl      %r14d, %ecx
        xorq      %r9, %r8
        negl      %ecx
        shlq      $4, %r8
        pxor      %xmm6, %xmm6
        shlq      %cl, %r13
        movl      %r14d, %ecx
        movsd     80(%rsp), %xmm1
        lea       56(%rsp), %rdx
        movq      16(%rbx), %r11
        movaps    %xmm1, %xmm0
        cvtss2sd  4(%r10,%r8), %xmm2
        cvtss2sd  (%r10,%r8), %xmm4
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm2
        mulsd     %xmm0, %xmm4
        addsd     8(%r10,%r8), %xmm2
        shrq      %cl, %r11
        movl      %r14d, %ecx
        shrq      %cl, %r15
        orq       %r11, %r13
        shrq      $11, %r13
        lea       128+__sqrt_x_table(%rip), %r11
        cvtsi2sdq %r15, %xmm7
        cvtsi2sdq %r13, %xmm6
        addsd     %xmm2, %xmm4
        mulsd     (%r11), %xmm7
        lea       136+__sqrt_x_table(%rip), %r13
        lea       96+__sqrt_x_table(%rip), %r8
        movaps    %xmm7, %xmm8
        lea       152+__sqrt_x_table(%rip), %r15
        lea       104+__sqrt_x_table(%rip), %r14
        lea       32(%rsp), %rdi
        movsd     .L_2il0floatpacket.21(%rip), %xmm12
        movq      %rbx, %rsi
        mulsd     (%r13), %xmm6
        mulsd     (%r8), %xmm4
        addsd     %xmm6, %xmm8
        movaps    %xmm8, %xmm3
        lea       144+__sqrt_x_table(%rip), %r8
        mulsd     %xmm4, %xmm3
        cvtsd2ss  %xmm4, %xmm4
        cvtsd2ss  %xmm3, %xmm3
        cvtss2sd  %xmm4, %xmm4
        cvtss2sd  %xmm3, %xmm3
        mulsd     %xmm4, %xmm7
        mulsd     %xmm4, %xmm6
        subsd     %xmm3, %xmm7
        movsd     (%r15), %xmm10
        movaps    %xmm4, %xmm9
        mulsd     %xmm8, %xmm10
        movaps    %xmm3, %xmm5
        addsd     %xmm6, %xmm7
        mulsd     %xmm4, %xmm9
        mulsd     %xmm4, %xmm5
        mulsd     %xmm4, %xmm7
        mulsd     %xmm9, %xmm10
        movsd     (%r14), %xmm11
        movsd     (%r8), %xmm15
        lea       112+__sqrt_x_table(%rip), %r8
        sarl      $1, %eax
        subsd     %xmm5, %xmm11
        subsd     %xmm10, %xmm15
        subsd     %xmm7, %xmm11
        mulsd     %xmm4, %xmm15
        movsd     (%r8), %xmm13
        lea       120+__sqrt_x_table(%rip), %r8
        mulsd     %xmm4, %xmm13
        negl      %eax
        mulsd     %xmm11, %xmm15
        comisd    %xmm12, %xmm13
        mulsd     (%r8), %xmm15
        movaps    %xmm13, %xmm14
        cvttsd2si %xmm15, %r9
        subsd     %xmm12, %xmm14
        jae       ..L18
        movaps    %xmm13, %xmm14
..L18:
        cvttsd2si %xmm14, %r10
        movq      %r9, %r8
        incl      %eax
        shlq      $39, %r10
        sarq      $12, %r8
        sarq      $11, %r9
        addq      %r8, %r10
        andq      $1, %r9
        movq      $0x4000000000000000, %r8
        addq      %r9, %r10
        andq      %r10, %r8
        testq     %r10, %r10
        movl      $0, -24(%rdx)
        movl      %eax, -20(%rdx)
        movq      $0, -8(%rdx)
        lea       -1(,%r8,2), %r8
        cmovns    %r8, %r10
        movq      %r10, -16(%rdx)
..___tag_value___dpml_ux_sqrt_evaluation__.19:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.20:
..B1.3:
        movq      %r12, %rdx
        lea       32(%rsp), %rdi
        lea       56(%rsp), %rsi
..___tag_value___dpml_ux_sqrt_evaluation__.21:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.22:
..B1.4:
        lea       160+__sqrt_x_table(%rip), %rdi
        movq      %r12, %rsi
        movl      $9, %edx
        movq      %r12, %rcx
..___tag_value___dpml_ux_sqrt_evaluation__.23:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.24:
..B1.5:
        testq     $1, %rbp
        lea       32(%rsp), %rsi
        movq      %r12, %rdi
        lea       56(%rsp), %rax
        cmovne    %rax, %rsi
        movq      %r12, %rdx
..___tag_value___dpml_ux_sqrt_evaluation__.25:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.26:
..B1.6:
        decl      4(%r12)
        lea       (%rsp), %rdi
..___tag_value___dpml_ux_sqrt_evaluation__.27:
        call      fesetenv@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.28:
..B1.7:
        xorl      %r15d, %r15d
        testq     $2, %rbp
        jne       ..B1.11
..B1.8:
        movq      88(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B1.10
..B1.9:
        movq      %r15, %rax
        addq      $104, %rsp
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
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.10:
        call      __stack_chk_fail@PLT
..B1.11:
        movq      %r12, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_ux_sqrt_evaluation__.49:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.50:
..B1.12:
        movq      16(%r12), %r14
        lea       8(%r14), %r8
        testq     $16368, %r8
        jne       ..B1.8
..B1.13:
        andq      $-32768, %r14
        movq      %r12, %rdi
        movq      %r12, %rsi
        lea       56(%rsp), %rdx
        lea       32(%rsp), %rcx
        lea       16384(%r14), %r13
        movq      %r13, 16(%r12)
..___tag_value___dpml_ux_sqrt_evaluation__.51:
        call      __dpml_extended_multiply__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.52:
..B1.14:
        movq      %rbx, %rdi
        lea       56(%rsp), %rsi
        movl      $1, %edx
        movq      %rsi, %rcx
..___tag_value___dpml_ux_sqrt_evaluation__.53:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.54:
..B1.15:
        movl      $1, %edx
        lea       56(%rsp), %rdi
        movq      %rdi, %rcx
        lea       32(%rsp), %rsi
..___tag_value___dpml_ux_sqrt_evaluation__.55:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.56:
..B1.16:
        movl      56(%rsp), %r8d
        testl     %r8d, %r8d
        cmove     %r13, %r14
        testq     $4, %rbp
        movq      %r14, 16(%r12)
        je        ..B1.8
..B1.17:
        movl      4(%r12), %ebx
        xorl      %edx, %edx
        addl      $-113, %ebx
        lea       56(%rsp), %rdi
        subl      %ebx, 4(%rdi)
        movl      $1, %ebx
        testl     %r8d, %r8d
        movq      %r12, %rsi
        cmove     %ebx, %edx
        movq      %rdi, %rcx
        orl       $8, %edx
..___tag_value___dpml_ux_sqrt_evaluation__.57:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_sqrt_evaluation__.58:
..B1.18:
        xorl      %r15d, %r15d
        cmpq      $8192, 72(%rsp)
        cmove     %ebx, %r15d
        jmp       ..B1.8
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_sqrt_evaluation__,@function
	.size	__dpml_ux_sqrt_evaluation__,.-__dpml_ux_sqrt_evaluation__
	.data
# -- End  __dpml_ux_sqrt_evaluation__
	.text
# -- Begin  __sqrtq
	.text
       .align    16,0x90
	.globl __sqrtq
__sqrtq:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value___sqrtq.60:
..L61:

        subq      $136, %rsp
	.cfi_def_cfa_offset 144
        xorl      %esi, %esi
        lea       __sqrt_x_table(%rip), %rcx
        lea       48(%rsp), %rdi
        movq      %fs:40, %rax
        lea       88(%rsp), %rdx
        xorq      %rsp, %rax
        lea       112(%rsp), %r8
        movaps    %xmm0, -40(%rdx)
        lea       (%rsp), %r9
        movq      %rax, 40(%rdx)
        movq      $1, -80(%rdx)
        movq      $0, (%r9)
..___tag_value___sqrtq.63:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___sqrtq.64:
..B2.2:
        testq     %rax, %rax
        jl        ..B2.5
..B2.3:
        movl      $7, %esi
        lea       88(%rsp), %rdi
        lea       64(%rsp), %rdx
..___tag_value___sqrtq.65:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___sqrtq.66:
..B2.4:
        movl      $1, %edx
        xorl      %ecx, %ecx
        testq     %rax, %rax
        lea       64(%rsp), %rdi
        cmove     %edx, %ecx
        xorl      %edx, %edx
        lea       112(%rsp), %rsi
        movq      %rcx, -104(%rsi)
        xorl      %ecx, %ecx
        lea       (%rsp), %r8
..___tag_value___sqrtq.67:
        call      __dpml_pack__@PLT
..___tag_value___sqrtq.68:
..B2.5:
        movq      128(%rsp), %rax
        xorq      %rsp, %rax
        movaps    112(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B2.7
..B2.6:
        addq      $136, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
..B2.7:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__sqrtq,@function
	.size	__sqrtq,.-__sqrtq
	.data
# -- End  __sqrtq
	.text
# -- Begin  __rsqrtq
	.text
       .align    16,0x90
	.globl __rsqrtq
__rsqrtq:
# parameter 1: %xmm0
..B3.1:
	.cfi_startproc
..___tag_value___rsqrtq.72:
..L73:

        subq      $136, %rsp
	.cfi_def_cfa_offset 144
        xorl      %esi, %esi
        lea       8+__sqrt_x_table(%rip), %rcx
        lea       48(%rsp), %rdi
        movq      %fs:40, %rax
        lea       88(%rsp), %rdx
        xorq      %rsp, %rax
        lea       112(%rsp), %r8
        movaps    %xmm0, -40(%rdx)
        lea       (%rsp), %r9
        movq      %rax, 40(%rdx)
        movq      $1, -80(%rdx)
        movq      $0, (%r9)
..___tag_value___rsqrtq.75:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___rsqrtq.76:
..B3.2:
        testq     %rax, %rax
        jl        ..B3.5
..B3.3:
        xorl      %esi, %esi
        lea       88(%rsp), %rdi
        lea       64(%rsp), %rdx
..___tag_value___rsqrtq.77:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___rsqrtq.78:
..B3.4:
        movl      $1, %edx
        xorl      %ecx, %ecx
        testq     %rax, %rax
        lea       64(%rsp), %rdi
        cmove     %edx, %ecx
        xorl      %edx, %edx
        lea       112(%rsp), %rsi
        movq      %rcx, -104(%rsi)
        xorl      %ecx, %ecx
        lea       (%rsp), %r8
..___tag_value___rsqrtq.79:
        call      __dpml_pack__@PLT
..___tag_value___rsqrtq.80:
..B3.5:
        movq      128(%rsp), %rax
        xorq      %rsp, %rax
        movaps    112(%rsp), %xmm0
        cmpq      %fs:40, %rax
        jne       ..B3.7
..B3.6:
        addq      $136, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
..B3.7:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__rsqrtq,@function
	.size	__rsqrtq,.-__rsqrtq
	.data
# -- End  __rsqrtq
	.text
# -- Begin  __dpml_ux_hypot__
	.text
       .align    16,0x90
	.globl __dpml_ux_hypot__
__dpml_ux_hypot__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B4.1:
	.cfi_startproc
..___tag_value___dpml_ux_hypot__.84:
..L85:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        subq      $88, %rsp
	.cfi_def_cfa_offset 112
        movq      %rdx, %r12
        movq      %rsi, %rbp
        movq      %rdi, %rsi
        movq      %fs:40, %rax
        lea       (%rsp), %rdx
        xorq      %rsp, %rax
        movq      %rax, 72(%rdx)
..___tag_value___dpml_ux_hypot__.91:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_hypot__.92:
..B4.2:
        movq      %rbp, %rdi
        movq      %rbp, %rsi
        lea       24(%rsp), %rdx
..___tag_value___dpml_ux_hypot__.93:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_hypot__.94:
..B4.3:
        xorl      %edx, %edx
        lea       (%rsp), %rdi
        lea       24(%rsp), %rsi
        lea       48(%rsp), %rcx
..___tag_value___dpml_ux_hypot__.95:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_hypot__.96:
..B4.4:
        xorl      %esi, %esi
        lea       48(%rsp), %rdi
..___tag_value___dpml_ux_hypot__.97:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_hypot__.98:
..B4.5:
        movl      $7, %esi
        lea       48(%rsp), %rdi
        movq      %r12, %rdx
..___tag_value___dpml_ux_hypot__.99:
        call      __dpml_ux_sqrt_evaluation__@PLT
..___tag_value___dpml_ux_hypot__.100:
..B4.6:
        testq     %rax, %rax
        je        ..B4.18
..B4.7:
        movl      4(%rsp), %ecx
        lea       (%rsp), %rbp
        subl      28(%rbp), %ecx
        movslq    %ecx, %rcx
        testq     %rcx, %rcx
        jns       ..B4.9
..B4.8:
        negq      %rcx
        lea       24(%rsp), %rbp
..B4.9:
        cmpq      $128, %rcx
        jle       ..B4.11
..B4.10:
        xorl      %eax, %eax
        jmp       ..B4.18
..B4.11:
        movl      $1, %edx
..B4.12:
        movq      8(%rbp,%rdx,8), %rax
        testq     %rax, %rax
        jne       ..B4.10
..B4.13:
        cmpq      $64, %rcx
        jl        ..B4.16
..B4.14:
        addq      $-64, %rcx
        decq      %rdx
        jns       ..B4.12
..B4.16:
        movq      %rax, %rdx
        movl      $1, %ebp
        shrq      %cl, %rdx
        xorl      %esi, %esi
        shlq      %cl, %rdx
        cmpq      %rdx, %rax
        cmove     %ebp, %esi
        movl      %esi, %eax
..B4.18:
        movq      72(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B4.20
..B4.19:
        addq      $88, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
	.cfi_offset 6, -24
	.cfi_offset 12, -16
..B4.20:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_hypot__,@function
	.size	__dpml_ux_hypot__,.-__dpml_ux_hypot__
	.data
# -- End  __dpml_ux_hypot__
	.section .rodata, "a"
	.align 16
	.align 16
__sqrt_x_table:
	.long	1088750600
	.long	605086734
	.long	1088766984
	.long	519758862
	.long	112
	.long	0
	.long	113
	.long	0
	.long	114
	.long	0
	.long	0
	.long	0
	.long	0
	.long	268435456
	.long	858997845
	.long	34
	.long	1636176969
	.long	409044504
	.long	1099306057
	.long	404751376
	.long	545392672
	.long	404783624
	.long	142938632
	.long	302522498
	.long	1719614413
	.long	1073127582
	.long	0
	.long	1072693248
	.long	0
	.long	1097859072
	.long	0
	.long	1151336448
	.long	0
	.long	1047527424
	.long	0
	.long	991952896
	.long	0
	.long	1072431104
	.long	0
	.long	1071120384
	.long	0
	.long	2
	.long	0
	.long	3221225472
	.long	0
	.long	0
	.long	0
	.long	0
	.type	__sqrt_x_table,@object
	.size	__sqrt_x_table,192
	.align 8
.L_2il0floatpacket.21:
	.long	0x00000000,0x43e00000
	.type	.L_2il0floatpacket.21,@object
	.size	.L_2il0floatpacket.21,8
	.align 4
.L_2il0floatpacket.20:
	.long	0x5f000000
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
