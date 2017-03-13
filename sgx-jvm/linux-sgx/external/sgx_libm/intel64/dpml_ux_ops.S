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
	.file "dpml_ux_ops.c"
	.text
..TXTST0:
# -- Begin  __dpml_unpack2__
	.text
       .align    16,0x90
	.globl __dpml_unpack2__
__dpml_unpack2__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
# parameter 7: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___dpml_unpack2__.1:
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
        pushq     %rsi
	.cfi_def_cfa_offset 64
        movq      %r9, %r12
        movq      %r8, %r13
        movq      %rcx, %rbp
        movq      %rsi, %r14
        xorl      %esi, %esi
        movq      %r13, %rcx
        movq      %r12, %r8
        movq      64(%rsp), %r9
        movq      %rdi, %r15
..___tag_value___dpml_unpack2__.16:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___dpml_unpack2__.17:
..B1.10:
        movq      %rax, %rbx
..B1.2:
        testq     %rbx, %rbx
        jl        ..B1.4
..B1.3:
        testq     %r14, %r14
        jne       ..B1.5
..B1.4:
        movq      %rbx, %rax
        popq      %rcx
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
	.cfi_def_cfa_offset 64
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B1.5:
        movq      8(%r13), %r10
        lea       (,%rbx,4), %rcx
        movq      %r15, %rdi
        movq      %r14, %rsi
        sarq      %cl, %r10
        movq      %rbp, %rdx
        andq      $15, %r10
        movq      %r12, %r8
        movq      64(%rsp), %r9
        lea       (%r13,%r10,8), %rcx
..___tag_value___dpml_unpack2__.38:
        call      __dpml_unpack_x_or_y__@PLT
..___tag_value___dpml_unpack2__.39:
..B1.6:
        shlq      $4, %rbx
        orq       %rax, %rbx
        movq      %rbx, %rax
        popq      %rcx
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
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_unpack2__,@function
	.size	__dpml_unpack2__,.-__dpml_unpack2__
	.data
# -- End  __dpml_unpack2__
	.text
# -- Begin  __dpml_unpack_x_or_y__
	.text
       .align    16,0x90
	.globl __dpml_unpack_x_or_y__
__dpml_unpack_x_or_y__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
..B2.1:
	.cfi_startproc
..___tag_value___dpml_unpack_x_or_y__.54:
..L55:

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
        movq      %rsi, %r12
        movl      $1, %r10d
        xorl      %r13d, %r13d
        testq     %r12, %r12
        movq      %r8, %rbp
        cmovne    %r10d, %r13d
        movq      %rdi, %r8
        movq      %rcx, %rbx
        testq     %r13, %r13
        movq      $0x8000000000000000, %r11
        movq      %fs:40, %rax
        jne       ..L69
        movq      %r8, %rsi
..L69:
        xorq      %rsp, %rax
        movq      %rax, 96(%rsp)
        movq      8(%rsi), %rax
        movq      %rax, %r10
        andq      %rax, %r11
        shlq      $15, %r10
        btsq      $63, %r10
        movq      (%rsi), %r14
        movq      %r14, %rcx
        movq      %rsi, 24(%r9,%r13,8)
        movq      %r14, %r15
        shrq      $49, %rcx
        shrq      $32, %r11
        lea       (%r10,%r10), %rsi
        orq       %r14, %rsi
        movq      $0x1000000000000, %r14
        orq       %rcx, %r10
        movq      $0xffff000000000000, %rcx
        movl      %r11d, (%rdx)
        movq      %rax, %r11
        shrq      $48, %r11
        andq      $32767, %r11
        lea       (%r14,%rax), %rdi
        movq      %rax, %r14
        addq      %rax, %rcx
        addq      $-16382, %r11
        shlq      $15, %r15
        shrq      $63, %r14
        movq      %r15, 16(%rdx)
        movl      $4, %r15d
        xorq      %rcx, %rdi
        jl        ..B2.33
..B2.2:
        movq      %r10, 8(%rdx)
        movl      %r11d, 4(%rdx)
..B2.3:
        addq      %r14, %r15
        movl      $1, %r10d
        movl      %r15d, %ecx
        shll      %cl, %r10d
        movq      (%r9), %rdx
        shlq      $10, %rdx
        movslq    %r10d, %r10
        orq       %r10, %rdx
        movq      %rdx, (%r9)
        testq     %r15, %r15
        jne       ..B2.5
..B2.4:
        movl      $2139095040, 28(%rsp)
        movss     28(%rsp), %xmm1
        movss     28(%rsp), %xmm0
        subss     %xmm0, %xmm1
        movss     %xmm1, 28(%rsp)
..B2.5:
        movq      (%rbx), %r11
        lea       (%r15,%r15,2), %rcx
        addq      %rcx, %rcx
        movq      %r11, %rdi
        sarq      $60, %r11
        sarq      %cl, %rdi
        movq      %rdi, %rdx
        sarq      $3, %rdi
        andq      $7, %rdx
        andq      $7, %rdi
        je        ..B2.26
..B2.6:
        movl      %r10d, %esi
        andl      $240, %esi
        cmpq      %r13, %rdx
        jg        ..B2.8
..B2.7:
        testq     %rdx, %rdx
        movl      %esi, %ebx
        cmove     %r8, %r12
        jmp       ..B2.9
..B2.8:
        andq      $15, %r11
        addq      %rdx, %r11
        movq      -8(%rbx,%r11,8), %rdx
        movl      $1, %ebx
        movl      %edx, %ecx
        movq      %rdx, %r12
        shlq      $4, %r12
        shll      %cl, %ebx
        addq      __x_constants__@GOTPCREL(%rip), %r12
        andl      $240, %ebx
..B2.9:
        cmpq      $7, %rdi
        je        ..B2.30
..B2.10:
        cmpq      $0, 8(%r9)
        je        ..B2.13
..B2.11:
        orl       %esi, %ebx
        je        ..B2.13
..B2.12:
        movl      $1182793727, 24(%rsp)
        movss     24(%rsp), %xmm1
        movss     24(%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, 24(%rsp)
..B2.13:
        movq      8(%r12), %rbx
        cmpq      $1, %rdi
        je        ..B2.29
..B2.14:
        cmpq      $3, %rdi
        je        ..B2.19
..B2.15:
        cmpq      $4, %rdi
        jne       ..B2.17
..B2.16:
        movq      $0x7fffffffffffffff, %rcx
        andq      %rcx, %rbx
        jmp       ..B2.20
..B2.17:
        cmpq      $5, %rdi
        jne       ..B2.20
..B2.18:
        movq      $0x7fffffffffffffff, %rcx
        andq      %rbx, %rcx
        movq      $0x8000000000000000, %rbx
        andq      8(%r8), %rbx
        orq       %rcx, %rbx
        jmp       ..B2.20
..B2.19:
        movq      $0x8000000000000000, %rcx
        xorq      %rcx, %rbx
..B2.20:
        movq      %rbx, 8(%rbp)
        testl     $192, %r10d
        movq      (%r12), %rcx
        movq      %rcx, (%rbp)
        je        ..B2.24
..B2.21:
        cmpq      %r13, %rdx
        jg        ..B2.24
..B2.22:
        cmpq      $0, 8(%r9)
        je        ..B2.24
..B2.23:
        movl      $8388608, 20(%rsp)
        movss     20(%rsp), %xmm0
        cvtss2sd  %xmm0, %xmm0
        mulsd     .L_2il0floatpacket.7(%rip), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movss     %xmm0, 20(%rsp)
..B2.24:
        movq      96(%rsp), %rcx
        movq      $0x8000000000000000, %rdx
        xorq      %rsp, %rcx
        orq       %rdx, %r15
        cmpq      %fs:40, %rcx
        jne       ..B2.28
..B2.25:
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
..B2.26:
        movq      96(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B2.28
..B2.27:
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
..B2.28:
        call      __stack_chk_fail@PLT
..B2.29:
        movq      $0x800000000000, %rcx
        orq       %rcx, %rbx
        jmp       ..B2.20
..B2.30:
        andq      $-2013265921, %rdx
        lea       32(%rsp), %rdi
        movq      (%rbp), %rcx
        orq       $268435456, %rdx
        movq      8(%rbp), %rbx
        movq      %rcx, 32(%rdi)
        movq      %rbx, 40(%rdi)
        movq      %rdx, (%rdi)
..___tag_value___dpml_unpack_x_or_y__.110:
        call      __dpml_exception@PLT
..___tag_value___dpml_unpack_x_or_y__.111:
..B2.31:
        movq      (%rax), %rdx
        movq      %rdx, (%rbp)
        movq      8(%rax), %rcx
        movq      %rcx, 8(%rbp)
        jmp       ..B2.24
..B2.33:
        xorq      %rax, %rcx
        jl        ..B2.35
..B2.34:
        shrq      $47, %rax
        xorl      %r15d, %r15d
        andq      $1, %rax
        testq     %rsi, %rsi
        movq      %r10, 8(%rdx)
        movl      %r11d, 4(%rdx)
        movl      $2, %edx
        cmove     %rdx, %r15
        cmovne    %rax, %r14
        jmp       ..B2.3
..B2.35:
        testq     %rsi, %rsi
        jne       ..B2.37
..B2.36:
        movq      %r10, 8(%rdx)
        movl      $8, %r15d
        movl      %r11d, 4(%rdx)
        jmp       ..B2.3
..B2.37:
        movq      $0x8000000000000000, %rax
        movq      %rdx, %rdi
        xorl      %esi, %esi
        addq      %rax, %r10
        incl      %r11d
        movq      %r10, 8(%rdx)
        movl      %r11d, 4(%rdx)
        movq      %r8, (%rsp)
        movq      %r9, 8(%rsp)
..___tag_value___dpml_unpack_x_or_y__.112:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_unpack_x_or_y__.113:
..B2.38:
        movl      $2048, 16(%rsp)
        movl      $6, %r15d
        movss     16(%rsp), %xmm1
        movss     16(%rsp), %xmm0
        movq      8(%rsp), %r9
        addss     %xmm0, %xmm1
        movq      (%rsp), %r8
        movss     %xmm1, 16(%rsp)
        jmp       ..B2.3
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_unpack_x_or_y__,@function
	.size	__dpml_unpack_x_or_y__,.-__dpml_unpack_x_or_y__
	.data
# -- End  __dpml_unpack_x_or_y__
	.text
# -- Begin  __dpml_pack__
	.text
       .align    16,0x90
	.globl __dpml_pack__
__dpml_pack__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
..B3.1:
	.cfi_startproc
..___tag_value___dpml_pack__.115:
..L116:

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
        subq      $120, %rsp
	.cfi_def_cfa_offset 176
        movq      %rsi, %r14
        xorl      %esi, %esi
        movq      %r8, %r15
        movq      %fs:40, %rax
        movq      %rcx, %r13
        xorq      %rsp, %rax
        movq      %rdx, %r12
        movq      %rax, 104(%rsp)
        movq      %rdi, %rbp
..___tag_value___dpml_pack__.130:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_pack__.131:
..B3.2:
        movslq    4(%rbp), %rax
        cmpq      $-262144, %rax
        je        ..B3.28
..B3.3:
        movq      %rax, %rdx
        negq      %rdx
        movq      %rdx, %rbx
        addq      $-16381, %rbx
        jle       ..B3.9
..B3.4:
        movl      (%rbp), %r9d
        lea       -16381(%rax,%rdx), %rax
        movq      %rbp, %rsi
        lea       80(%rsp), %rdi
        xorl      %edx, %edx
        movq      %rbp, %rcx
        movq      $0x8000000000000000, %r8
        movq      %r8, 8(%rdi)
        movq      $0, 16(%rdi)
        movl      %eax, 4(%rdi)
        movl      %r9d, (%rdi)
..___tag_value___dpml_pack__.132:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_pack__.133:
..B3.5:
        movq      $-16382, %rax
        cmpq      $113, %rbx
        jle       ..B3.9
..B3.6:
        cmpq      $245763, %rbx
        je        ..B3.8
..B3.7:
        movq      $-16383, %rax
        cmpq      $49155, %rbx
        jle       ..B3.9
..B3.8:
        decq      %rax
..B3.9:
        movq      16(%rbp), %r8
        xorl      %edx, %edx
        addq      $16384, %r8
        xorl      %r9d, %r9d
        cmpq      $16384, %r8
        lea       1(%rax), %r10
        movq      8(%rbp), %rcx
        movq      $0x1000000000000, %r11
        setb      %dl
        addq      %rdx, %rcx
        cmpq      %rdx, %rcx
        movq      %rcx, %rbx
        setb      %r9b
        shrq      $15, %rcx
        testl     %r9d, %r9d
        cmovne    %r10, %rax
        cmovne    %r11, %rcx
        shrq      $15, %r8
        shlq      $49, %rbx
        orq       %rbx, %r8
        movq      %r8, (%r14)
        lea       16381(%rax), %rdx
        movq      %rdx, %rbx
        shlq      $48, %rbx
        addq      %rcx, %rbx
        movslq    (%rbp), %rcx
        shlq      $32, %rcx
        orq       %rcx, %rbx
        cmpq      $32766, %rdx
        jae       ..B3.14
..B3.10:
        movq      %rbx, 8(%r14)
        cmpq      $0, 8(%r15)
        je        ..B3.12
..B3.11:
        movl      $1182793727, (%rsp)
        movss     (%rsp), %xmm1
        movss     (%rsp), %xmm0
        mulss     %xmm0, %xmm1
        movss     %xmm1, (%rsp)
..B3.12:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B3.24
..B3.13:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.14:
        testq     %rax, %rax
        cmovl     %r12, %r13
        addq      $16382, %rax
        testl     %eax, %eax
        jg        ..B3.20
..B3.15:
        jl        ..B3.26
..B3.16:
        movq      %rbx, 8(%r14)
        cmpq      $0, 8(%r15)
        jne       ..B3.19
..B3.17:
        movq      104(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B3.24
..B3.18:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.19:
        movq      $0x8000000000000000, %rax
        orq       %rax, %r13
        jmp       ..B3.21
..B3.20:
        movq      %rbx, 8(%r14)
..B3.21:
        andq      $-2013265921, %r13
        lea       16(%rsp), %rdi
        movq      (%r14), %rax
        orq       $268435456, %r13
        movq      8(%r14), %rdx
        movq      %rax, 32(%rdi)
        movq      %rdx, 40(%rdi)
        movq      %r13, (%rdi)
..___tag_value___dpml_pack__.174:
        call      __dpml_exception@PLT
..___tag_value___dpml_pack__.175:
..B3.33:
        movq      %rax, %rdx
..B3.22:
        movq      (%rdx), %rax
        movq      %rax, (%r14)
        movq      8(%rdx), %rdx
        movq      %rdx, 8(%r14)
        movq      104(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B3.24
..B3.23:
        addq      $120, %rsp
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
	.cfi_def_cfa_offset 176
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B3.24:
        call      __stack_chk_fail@PLT
..B3.26:
        movq      %rcx, 8(%r14)
        jmp       ..B3.21
..B3.28:
        movslq    (%rbp), %rax
        shlq      $32, %rax
        movq      $0, (%r14)
        movq      %rax, 8(%r14)
        movq      104(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B3.24
        jmp       ..B3.13
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_pack__,@function
	.size	__dpml_pack__,.-__dpml_pack__
	.data
# -- End  __dpml_pack__
	.text
# -- Begin  __dpml_evaluate_packed_poly__
	.text
       .align    16,0x90
	.globl __dpml_evaluate_packed_poly__
__dpml_evaluate_packed_poly__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
# parameter 5: %r8
# parameter 6: %r9
..B4.1:
	.cfi_startproc
..___tag_value___dpml_evaluate_packed_poly__.197:
..L198:

        subq      $104, %rsp
	.cfi_def_cfa_offset 112
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 88(%rsp)
        movq      %rcx, %rax
        movq      8(%rdx), %r11
        notq      %rax
        movq      (%rdx), %r10
        movq      %r11, 8(%r9)
        movq      %r10, %r11
        andq      %rax, %r11
        movq      %r11, 16(%r9)
        movq      %r10, %r11
        shrq      $1, %r10
        andq      $1, %r11
        andq      %rcx, %r10
        shlq      $31, %r11
        subq      %r8, %r10
        movl      %r11d, (%r9)
        decq      %rsi
        movl      %r10d, 4(%r9)
        js        ..B4.8
..B4.2:
        xorl      %r10d, %r10d
        movl      %r10d, 68(%rsp)
        movl      %r10d, 64(%rsp)
        movq      %rdi, 56(%rsp)
        movq      %rcx, 48(%rsp)
        movq      %r12, 40(%rsp)
	.cfi_offset 12, -72
        movq      %r9, %r12
        movq      %r13, 32(%rsp)
	.cfi_offset 13, -80
        movq      %rax, %r13
        movq      %r14, 24(%rsp)
	.cfi_offset 14, -88
        movq      %r8, %r14
        movq      %r15, 16(%rsp)
	.cfi_offset 15, -96
        movq      %rdx, %r15
        movq      %rbx, 8(%rsp)
        movq      %rbp, (%rsp)
	.cfi_offset 3, -104
	.cfi_offset 6, -112
        movq      %rsi, %rbp
..B4.3:
        movq      %r12, %rsi
        movq      %r12, %rdx
        movq      56(%rsp), %rdi
..___tag_value___dpml_evaluate_packed_poly__.206:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_evaluate_packed_poly__.207:
..B4.4:
        movq      %r12, %rdi
        xorl      %esi, %esi
..___tag_value___dpml_evaluate_packed_poly__.208:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_evaluate_packed_poly__.209:
..B4.5:
        addq      $16, %r15
        movq      %r13, %r10
        movq      %r12, %rdi
        lea       64(%rsp), %rsi
        movq      %r12, %rcx
        movq      8(%r15), %rbx
        movq      %rbx, 8(%rsi)
        movq      (%r15), %rbx
        andq      %rbx, %r10
        movq      %rbx, %rdx
        shrq      $1, %rbx
        andq      $1, %rdx
        andq      -16(%rsi), %rbx
        subq      %r14, %rbx
        movq      %r10, 16(%rsi)
..___tag_value___dpml_evaluate_packed_poly__.210:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_evaluate_packed_poly__.211:
..B4.6:
        addl      %ebx, 4(%r12)
        decq      %rbp
        jns       ..B4.3
..B4.7:
        movq      40(%rsp), %r12
	.cfi_restore 12
        movq      32(%rsp), %r13
	.cfi_restore 13
        movq      24(%rsp), %r14
	.cfi_restore 14
        movq      16(%rsp), %r15
	.cfi_restore 15
        movq      8(%rsp), %rbx
	.cfi_restore 3
        movq      (%rsp), %rbp
	.cfi_restore 6
..B4.8:
        movq      88(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B4.10
..B4.9:
        addq      $104, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
..B4.10:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_evaluate_packed_poly__,@function
	.size	__dpml_evaluate_packed_poly__,.-__dpml_evaluate_packed_poly__
	.data
# -- End  __dpml_evaluate_packed_poly__
	.text
# -- Begin  __dpml_addsub__
	.text
       .align    16,0x90
	.globl __dpml_addsub__
__dpml_addsub__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
# parameter 4: %rcx
..B5.1:
	.cfi_startproc
..___tag_value___dpml_addsub__.221:
..L222:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $40, %rsp
	.cfi_def_cfa_offset 80
        movq      %rdi, %r9
        movq      %rdx, %r15
        movq      %rsi, %rax
        movq      %r15, %rbx
        movq      %rcx, %r8
        movq      %r15, %rcx
        xorl      %ebp, %ebp
        movl      (%r9), %edx
        andq      $4, %rcx
        movslq    %edx, %rdi
        movslq    (%rax), %r11
        cmovne    %ebp, %edx
        shlq      $31, %rbx
        xorq      %rbx, %rdi
        xorq      %r11, %rdi
        testq     %rcx, %rcx
        movl      4(%r9), %esi
        cmovne    %rbx, %rdi
        movslq    %esi, %rbx
        sarq      $31, %rdi
        movq      %rbx, %rcx
        movq      %fs:40, %r10
        andq      $1, %rdi
        xorq      %rsp, %r10
        movq      %r10, 32(%rsp)
        movslq    4(%rax), %r10
        subq      %r10, %rcx
        jge       ..B5.3
..B5.2:
        negq      %rcx
        movq      %r9, %rbp
        addq      %rcx, %rbx
        movq      %rax, %r9
        movl      %ebx, %esi
        movq      %rdi, %rbx
        shlq      $31, %rbx
        movq      %rbp, %rax
        movslq    %edx, %rdx
        movl      $-2147483648, %ebp
        xorq      %rbx, %rdx
..B5.3:
        movq      16(%rax), %rbx
        movl      $2, %r11d
        movq      8(%rax), %rax
..B5.4:
        movq      %rcx, %r10
        negq      %r10
        addq      $64, %r10
        testq     %r10, %r10
        jg        ..B5.11
..B5.5:
        movq      %r10, %rcx
        movq      %rax, %rbx
        negq      %rcx
        xorl      %eax, %eax
        decq      %r11
        jne       ..B5.4
..B5.6:
        movq      8(%r9), %rcx
        testq     $2, %r15
        movq      %rcx, 8(%r8)
        movl      4(%r9), %eax
        movq      16(%r9), %rbx
        movl      %eax, 4(%r8)
        movq      %rbx, 16(%r8)
        movl      %edx, (%r8)
        je        ..B5.8
..B5.7:
        movq      8(%r9), %rcx
        xorl      %ebp, %edx
        movq      %rcx, 32(%r8)
        movl      4(%r9), %eax
        movq      16(%r9), %rbx
        movl      %eax, 28(%r8)
        movq      %rbx, 40(%r8)
        movl      %edx, 24(%r8)
..B5.8:
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B5.10
..B5.9:
        addq      $40, %rsp
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
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B5.10:
        call      __stack_chk_fail@PLT
..B5.11:
        testq     %rcx, %rcx
        je        ..B5.13
..B5.12:
        movl      %ecx, %r11d
        movl      %r11d, %ecx
        shrq      %cl, %rbx
        movl      %r10d, %ecx
        movq      %rax, %r10
        shlq      %cl, %r10
        movl      %r11d, %ecx
        shrq      %cl, %rax
        orq       %r10, %rbx
..B5.13:
        movq      %r8, %r14
        movq      %r12, 8(%rsp)
	.cfi_offset 12, -72
        movq      %rdi, %r12
        movq      %r13, (%rsp)
	.cfi_offset 13, -80
        movq      %r9, %r13
        jmp       ..B5.14
..B5.22:
        negq      %r12
        xorq      $2, %r15
        movl      4(%r13), %esi
        incq      %r12
        addq      $24, %r14
        xorl      %ebp, %edx
..B5.14:
        movq      16(%r13), %r11
        testq     %r12, %r12
        movq      8(%r13), %rdi
        jne       ..B5.17
..B5.15:
        xorl      %ecx, %ecx
        lea       (%r11,%rbx), %r10
        cmpq      %r11, %r10
        movl      %ecx, %r11d
        setb      %r11b
        xorl      %r9d, %r9d
        andq      $15, %r15
        movl      %r11d, %r8d
        lea       (%rax,%r8), %r11
        cmpq      %r8, %r11
        setb      %r9b
        addq      %rdi, %r11
        cmpq      %rdi, %r11
        setb      %cl
        addl      %ecx, %r9d
        je        ..B5.19
..B5.16:
        movq      %r11, %rcx
        incl      %esi
        shrq      $1, %r10
        shlq      $63, %rcx
        shrq      $1, %r11
        orq       %rcx, %r10
        btsq      $63, %r11
        jmp       ..B5.19
..B5.17:
        xorl      %ecx, %ecx
        xorl      %r10d, %r10d
        cmpq      %r11, %rbx
        seta      %r10b
        xorl      %r9d, %r9d
        addq      $-8, %r15
        movl      %r10d, %r8d
        movq      %r11, %r10
        subq      %rbx, %r10
        lea       (%rax,%r8), %r11
        cmpq      %r8, %r11
        setb      %r9b
        negq      %r11
        addq      %rdi, %r11
        cmpq      %r11, %rdi
        setb      %cl
        addl      %ecx, %r9d
        je        ..B5.19
..B5.18:
        movq      $-1, %rcx
        xorl      %edi, %edi
        negq      %r10
        movslq    %edx, %rdx
        cmovne    %rcx, %rdi
        btcq      $31, %rdx
        negq      %r11
        movl      $-2147483648, %ebp
        addq      %rdi, %r11
..B5.19:
        movq      %r11, 8(%r14)
        testq     $16, %r15
        movq      %r10, 16(%r14)
        movl      %esi, 4(%r14)
        movl      %edx, (%r14)
        je        ..B5.21
..B5.20:
        movq      %r14, %rdi
        xorl      %esi, %esi
        movl      %edx, 16(%rsp)
        movq      %rax, 24(%rsp)
..___tag_value___dpml_addsub__.248:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_addsub__.249:
..B5.28:
        movq      24(%rsp), %rax
        movl      16(%rsp), %edx
..B5.21:
        testq     $2, %r15
        jne       ..B5.22
..B5.23:
        movq      32(%rsp), %rax
        xorq      %rsp, %rax
        movq      8(%rsp), %r12
	.cfi_restore 12
        movq      (%rsp), %r13
	.cfi_restore 13
        cmpq      %fs:40, %rax
        jne       ..B5.10
        jmp       ..B5.9
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_addsub__,@function
	.size	__dpml_addsub__,.-__dpml_addsub__
	.data
# -- End  __dpml_addsub__
	.text
# -- Begin  __dpml_ffs_and_shift__
	.text
       .align    16,0x90
	.globl __dpml_ffs_and_shift__
__dpml_ffs_and_shift__:
# parameter 1: %rdi
# parameter 2: %rsi
..B6.1:
	.cfi_startproc
..___tag_value___dpml_ffs_and_shift__.253:
..L254:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        movq      %rdi, %r9
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, (%rsp)
        testq     %rsi, %rsi
        movq      8(%r9), %r8
        jne       ..B6.4
..B6.2:
        movslq    4(%r9), %rdi
        testq     %r8, %r8
        jl        ..B6.12
..B6.3:
        movq      16(%r9), %rsi
        jmp       ..B6.7
..B6.4:
        xorl      %ecx, %ecx
        movl      $64, %edi
        cmpq      $1, %rsi
        jne       ..B6.6
..B6.5:
        movq      %r8, %rdx
        movl      $-2147483648, %eax
        negq      %rdx
        testq     %r8, %r8
        cmovl     %eax, %ecx
        cmovl     %rdx, %r8
..B6.6:
        movq      %r8, 8(%r9)
        xorl      %esi, %esi
        movq      $0, 16(%r9)
        movl      %ecx, (%r9)
..B6.7:
        movl      $2, %edx
        xorl      %eax, %eax
..B6.8:
        testq     %r8, %r8
        jne       ..B6.16
..B6.9:
        addq      $64, %rax
        movq      %rsi, %r8
        xorl      %esi, %esi
        decq      %rdx
        jne       ..B6.8
..B6.10:
        movl      $-262144, 4(%r9)
        movq      (%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B6.14
..B6.11:
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B6.12:
        movq      (%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B6.14
..B6.13:
        xorl      %eax, %eax
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B6.14:
        call      __stack_chk_fail@PLT
..B6.16:
        jl        ..B6.21
..B6.17:
        movq      %r8, %rcx
        shrq      $58, %rcx
        andq      $30, %rcx
        je        ..B6.19
..B6.18:
        movl      $21932, %edx
        shrl      %cl, %edx
        andl      $3, %edx
        incq      %rdx
        jmp       ..B6.20
..B6.19:
        movq      %r8, %rdx
        pxor      %xmm0, %xmm0
        andq      $-256, %rdx
        cmove     %r8, %rdx
        cvtsi2sdq %rdx, %xmm0
        testq     %rdx, %rdx
        jge       ..B6.26
..B6.27:
        movq      %rdx, %rcx
        pxor      %xmm0, %xmm0
        shrq      $1, %rdx
        andq      $1, %rcx
        orq       %rdx, %rcx
        cvtsi2sdq %rcx, %xmm0
        addsd     %xmm0, %xmm0
..B6.26:
        movd      %xmm0, %rdx
        sarq      $52, %rdx
        negq      %rdx
        addq      $1086, %rdx
..B6.20:
        movl      %edx, %ecx
        movq      %rsi, %r10
        shlq      %cl, %r8
        movq      %rdx, %rcx
        negq      %rcx
        addq      %rdx, %rax
        shrq      %cl, %r10
        movl      %edx, %ecx
        orq       %r10, %r8
        shlq      %cl, %rsi
..B6.21:
        subq      %rax, %rdi
        movq      %r8, 8(%r9)
        movq      %rsi, 16(%r9)
        movl      %edi, 4(%r9)
        movq      (%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B6.14
        jmp       ..B6.11
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ffs_and_shift__,@function
	.size	__dpml_ffs_and_shift__,.-__dpml_ffs_and_shift__
	.data
# -- End  __dpml_ffs_and_shift__
	.section .rodata, "a"
	.align 16
	.align 16
	.globl __x_constants__
__x_constants__:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1073610752
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	0
	.long	0
	.long	0
	.long	1073741824
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073648159
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073779231
	.long	2479964490
	.long	592389929
	.long	3354604061
	.long	1073753495
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073713695
	.long	0
	.long	0
	.long	0
	.long	1074030592
	.long	0
	.long	0
	.long	0
	.long	1074096128
	.long	0
	.long	0
	.long	0
	.long	1074138624
	.long	0
	.long	0
	.long	0
	.long	1074161664
	.long	0
	.long	0
	.long	0
	.long	2147418112
	.type	__x_constants__,@object
	.size	__x_constants__,208
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0x3e600000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
