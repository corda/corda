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
	.file "libm_logl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_log_k80
	.text
       .align    16,0x90
	.globl __libm_log_k80
__libm_log_k80:
# parameter 1: %rdi
# parameter 2: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___libm_log_k80.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        subq      $88, %rsp
	.cfi_def_cfa_offset 112
        movq      %rsi, %r12
        movq      %rdi, %r13
        movq      %r12, %rdi
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 80(%rsp)
..___tag_value___libm_log_k80.8:
        call      __libm_normalizel_k80@PLT
..___tag_value___libm_log_k80.9:
..B1.2:
        fldt      (%r12)
        fldt      16(%r12)
        movl      32(%r12), %eax
        cmpl      $-1, %eax
        je        ..B1.15
..B1.3:
        movl      %eax, 16(%rsp)
        fildl     16(%rsp)
        fstpt     (%rsp)
..B1.4:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 144
        lea       48(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_log_k80.11:
        call      __libm_logl_k80@PLT
..___tag_value___libm_log_k80.12:
..B1.18:
        addq      $32, %rsp
	.cfi_def_cfa_offset 112
..B1.5:
        fldt      16(%rsp)
        lea       _LN2_K80(%rip), %rax
        lea       16+_LN2_K80(%rip), %rdx
        fmul      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      32(%rsp)
        fmulp     %st, %st(2)
        movzwl    24(%rsp), %esi
        andl      $32767, %esi
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      (%rax)
        fldt      (%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fld       %st(0)
        movzwl    56(%rsp), %ecx
        fadd      %st(4), %st
        fldt      (%rdx)
        andl      $32767, %ecx
        fmulp     %st, %st(3)
        cmpl      %esi, %ecx
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        movl      $0, 32(%r13)
        jg        ..B1.10
..B1.6:
        jne       ..B1.11
..B1.7:
        movl      52(%rsp), %eax
        movl      20(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B1.10
..B1.8:
        jne       ..B1.11
..B1.9:
        movl      48(%rsp), %eax
        cmpl      16(%rsp), %eax
        jbe       ..B1.11
..B1.10:
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.12
..B1.11:
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
..B1.12:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%r13)
        fldt      (%r13)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%r13)
        movq      80(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.14
..B1.13:
        addq      $88, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 112
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.14:
        call      __stack_chk_fail@PLT
..B1.15:
        lea       _zeros(%rip), %rax
        lea       halfs(%rip), %rdx
        fldl      (%rax)
        fstpt     (%rsp)
        fldt      (%rdx)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        jmp       ..B1.4
        .align    16,0x90
	.cfi_endproc
	.type	__libm_log_k80,@function
	.size	__libm_log_k80,.-__libm_log_k80
	.data
# -- End  __libm_log_k80
	.text
# -- Begin  __libm_logl_k80
	.text
       .align    16,0x90
	.globl __libm_logl_k80
__libm_logl_k80:
# parameter 1: 8 + %rsp
# parameter 2: 24 + %rsp
# parameter 3: %rdi
..B2.1:
	.cfi_startproc
..___tag_value___libm_logl_k80.23:
..L24:

        fldt      8(%rsp)
        movzbl    17(%rsp), %r9d
        andl      $128, %r9d
        shrl      $7, %r9d
        movzwl    16(%rsp), %ecx
        movl      %ecx, %eax
        movl      %r9d, %r8d
        andl      $32767, %eax
        shll      $15, %r8d
        movl      12(%rsp), %edx
        orl       %eax, %r8d
        movl      %edx, %esi
        shll      $16, %r8d
        shrl      $16, %esi
        orl       %esi, %r8d
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        cmpl      $2147450880, %r8d
        fldt      24(%rsp)
        jae       ..B2.14
..B2.2:
        lea       -1073674240(%r8), %esi
        cmpl      $36864, %esi
        ja        ..B2.8
..B2.3:
        addl      $-1073676287, %r8d
        cmpl      $32769, %r8d
        ja        ..B2.7
..B2.4:
        lea       _ones(%rip), %rax
        fldl      (%rax)
        fucomi    %st(2), %st
        jne       ..B2.6
        jp        ..B2.6
..B2.5:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        fucomi    %st(2), %st
        jp        ..B2.27
        je        ..B2.23
..B2.27:
        fstp      %st(0)
..B2.6:
        fld       %st(2)
        lea       48+_Q1(%rip), %rax
        lea       16+_Q1(%rip), %rdx
        lea       32+_Q1(%rip), %rcx
        lea       _Q1(%rip), %rsi
        fsub      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsub      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(3)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(1)
        fldt      (%rcx)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        ret       
..B2.7:
        lea       8+_ones(%rip), %rax
        fld       %st(1)
        lea       _TWO_52H(%rip), %rdx
        lea       208+_Q2(%rip), %rcx
        lea       176+_Q2(%rip), %rsi
        lea       144+_Q2(%rip), %r8
        lea       112+_Q2(%rip), %r9
        lea       80+_Q2(%rip), %r10
        fldl      (%rax)
        lea       48+_Q2(%rip), %r11
        lea       16+_Q2(%rip), %rax
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldl      (%rdx)
        fmul      %st(4), %st
        lea       192+_Q2(%rip), %rdx
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     -56(%rsp)
        fldt      -56(%rsp)
        fmul      %st, %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fldt      (%rcx)
        fmul      %st(1), %st
        lea       160+_Q2(%rip), %rcx
        fldt      (%rsi)
        lea       128+_Q2(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       96+_Q2(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        lea       64+_Q2(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        lea       32+_Q2(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        lea       _Q2(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       240+_Q2(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       224+_Q2(%rip), %rdx
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fmulp     %st, %st(1)
        fld       %st(1)
        fldt      (%rdx)
        fld       %st(0)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fldt      -56(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(2)
        fadd      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        lea       _ones(%rip), %rdi
        fldl      (%rdi)
        ret       
..B2.8:
        lea       _zeros(%rip), %r8
        movl      $-16383, %esi
        testl     %eax, %eax
        fldl      (%r8)
        movsd     (%r8), %xmm0
        jne       ..B2.11
..B2.9:
        lea       _TWO_75(%rip), %rax
        movl      $-16458, %esi
        fldl      (%rax)
        fmul      %st, %st(3)
        fxch      %st(3)
        fucomi    %st(1), %st
        fxch      %st(3)
        fmulp     %st, %st(2)
        jp        ..B2.10
        je        ..B2.24
..B2.10:
        fxch      %st(2)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        movzwl    -16(%rsp), %ecx
        movl      %ecx, %eax
        movl      -20(%rsp), %edx
        andl      $32767, %eax
        fxch      %st(2)
..B2.11:
        fstp      %st(2)
        addl      %eax, %esi
        fucomi    %st(1), %st
        fxch      %st(1)
        fstp      %st(0)
        jp        ..B2.12
        je        ..B2.13
..B2.12:
        fstpt     -56(%rsp)
        movzwl    -48(%rsp), %r8d
        movl      %r8d, %r9d
        subl      %eax, %r8d
        andl      $-32768, %r9d
        addl      $16383, %r8d
        andl      $32767, %r8d
        orl       %r8d, %r9d
        movw      %r9w, -48(%rsp)
        fldt      -56(%rsp)
..B2.13:
        shrl      $23, %edx
        andl      $-32768, %ecx
        movzbl    %dl, %eax
        lea       _TWO_32H(%rip), %rdx
        orl       $-49153, %ecx
        lea       8+_ones(%rip), %r8
        movw      %cx, -16(%rsp)
        lea       48+_P(%rip), %r9
        fldt      -24(%rsp)
        lea       16+_P(%rip), %r10
        fld       %st(0)
        lea       64+_P(%rip), %r11
        fldl      (%rdx)
        lea       32+_P(%rip), %rdx
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rcx
        fadd      %st, %st(1)
        movl      %esi, -40(%rsp)
        lea       80+_P(%rip), %rsi
        fsubrp    %st, %st(1)
        fsubr     %st, %st(1)
        flds      (%rcx,%rax,4)
        fmul      %st, %st(1)
        fmul      %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     -56(%rsp)
        fldt      -56(%rsp)
        lea       _P(%rip), %rcx
        shlq      $4, %rax
        faddp     %st, %st(1)
        fldl      (%r8)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fildl     -40(%rsp)
        fldt      (%rsi)
        fmul      %st(3), %st
        fldt      (%r9)
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmulp     %st, %st(2)
        fldt      (%r11)
        fmul      %st(3), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.3(%rip)
        fmulp     %st, %st(2)
        faddl     8(%rsi,%rax)
        faddp     %st, %st(2)
        faddl     (%rax,%rsi)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        lea       _ones(%rip), %rdi
        fldl      (%rdi)
        ret       
..B2.14:
        fstp      %st(0)
        testl     %r9d, %r9d
        jne       ..B2.16
..B2.15:
        lea       _ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        fldl      (%rax)
        fmul      %st, %st(1)
        movsd     (%rdx), %xmm1
        fxch      %st(1)
        jmp       ..B2.21
..B2.16:
        cmpl      $32767, %eax
        je        ..B2.22
..B2.17:
        fstp      %st(0)
        testl     %eax, %eax
        jne       ..B2.20
..B2.18:
        cmpq      $0, -24(%rsp)
        jne       ..B2.20
..B2.19:
        lea       _zeros(%rip), %rax
        lea       8+_ones(%rip), %rdx
        lea       _ones(%rip), %rcx
        movsd     (%rax), %xmm1
        fldl      (%rcx)
        movsd     (%rdx), %xmm0
        divsd     %xmm1, %xmm0
        movsd     %xmm0, -40(%rsp)
        fldl      -40(%rsp)
        jmp       ..B2.21
..B2.20:
        lea       _zeros(%rip), %rax
        lea       _infs(%rip), %rdx
        lea       _ones(%rip), %rcx
        movsd     (%rax), %xmm1
        fldl      (%rcx)
        movsd     (%rdx), %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, -40(%rsp)
        fldl      -40(%rsp)
..B2.21:
        movsd     %xmm1, -40(%rsp)
        fstpt     (%rdi)
        fldl      -40(%rsp)
        fstpt     16(%rdi)
        ret       
..B2.22:
        movq      $0x8000000000000000, %rax
        cmpq      -24(%rsp), %rax
        jne       ..B2.15
        jmp       ..B2.28
..B2.23:
        fstp      %st(1)
        fstp      %st(2)
        fstp      %st(0)
        fstpt     (%rdi)
        fldt      (%rdi)
        fld       %st(0)
        fxch      %st(1)
        fstpt     16(%rdi)
        ret       
..B2.24:
        fstp      %st(2)
        fstp      %st(0)
        lea       8+_ones(%rip), %rax
        lea       _ones(%rip), %rdx
        fstpt     16(%rdi)
        movsd     (%rax), %xmm1
        divsd     %xmm0, %xmm1
        movsd     %xmm1, -40(%rsp)
        fldl      -40(%rsp)
        fstpt     (%rdi)
        fldl      (%rdx)
        ret       
..B2.28:
        fstp      %st(0)
        jmp       ..B2.20
        .align    16,0x90
	.cfi_endproc
	.type	__libm_logl_k80,@function
	.size	__libm_logl_k80,.-__libm_logl_k80
	.data
# -- End  __libm_logl_k80
	.section .rodata, "a"
	.align 16
	.align 16
halfs:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	halfs,@object
	.size	halfs,32
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_LN2_K80:
	.word	0
	.word	0
	.word	6136
	.word	45426
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	12704
	.word	55409
	.word	6480
	.word	47298
	.word	49116
	.word	0
	.word	0
	.word	0
	.type	_LN2_K80,@object
	.size	_LN2_K80,32
	.align 2
_Q1:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	5458
	.word	43692
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	44682
	.word	49156
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	63914
	.word	24446
	.word	52434
	.word	52426
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q2:
	.word	58950
	.word	11767
	.word	59949
	.word	40744
	.word	49076
	.word	0
	.word	0
	.word	0
	.word	44486
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	65514
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	49718
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	36693
	.word	43691
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	41156
	.word	18852
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	14101
	.word	65290
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	21744
	.word	48151
	.word	14557
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	57454
	.word	983
	.word	52353
	.word	52428
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	54021
	.word	59409
	.word	39452
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	35608
	.word	4659
	.word	11214
	.word	43692
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	35524
	.word	31332
	.word	8749
	.word	40326
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	54832
	.word	20873
	.word	17674
	.word	36915
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	48567
	.word	9481
	.word	46911
	.word	55870
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,256
	.align 2
_P:
	.word	65
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	44010
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	58670
	.word	61414
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	24905
	.word	46201
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	57053
	.word	27587
	.word	64478
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	41508
	.word	48914
	.word	32459
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,96
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
