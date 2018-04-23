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
	.file "ctanhl.c"
	.text
..TXTST0:
# -- Begin  ctanhl
	.text
       .align    16,0x90
	.globl ctanhl
ctanhl:
# parameter 1: 448 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_ctanhl.1:
..L2:

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
        subq      $408, %rsp
	.cfi_def_cfa_offset 448
        xorb      %r15b, %r15b
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 400(%rsp)
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fstpt     368(%rsp)
        fldt      368(%rsp)
        fstpt     384(%rsp)
..B1.2:
        fnstcw    226(%rsp)
..B1.3:
        movzwl    226(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 224(%rsp)
..B1.5:
        fldcw     224(%rsp)
..B1.6:
        movb      $1, %r15b
..B1.7:
        movzwl    456(%rsp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.52
..B1.8:
        movzwl    472(%rsp), %ebp
        andl      $32767, %ebp
        cmpl      $32767, %ebp
        jge       ..B1.52
..B1.9:
        cmpl      $0, 452(%rsp)
        jne       ..B1.11
..B1.10:
        cmpl      $0, 448(%rsp)
        je        ..B1.52
..B1.11:
        cmpl      $16396, %edx
        jl        ..B1.23
..B1.12:
        movb      457(%rsp), %dl
        lea       _CONSTANTS(%rip), %rax
        andb      $-128, %dl
        lea       ones(%rip), %rcx
        shrb      $7, %dl
        fldt      (%rax)
        movzbl    %dl, %ebx
        cmpl      $0, 468(%rsp)
        fldl      (%rcx,%rbx,8)
        fsub      %st(1), %st
        fstpt     368(%rsp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, 464(%rsp)
        je        ..B1.15
..B1.14:
        fmul      %st(0), %st
        fstpt     384(%rsp)
        jmp       ..B1.16
..B1.15:
        fstp      %st(0)
        fldt      208(%rsp)
        fstpt     384(%rsp)
..B1.16:
        testl     %ebp, %ebp
        jle       ..B1.76
..B1.17:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      480(%rsp)
        fstpt     (%rsp)
        call      tanl@PLT
..B1.85:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fstpt     192(%rsp)
        movb      393(%rsp), %dl
        movb      201(%rsp), %al
        andb      $127, %dl
        andb      $-128, %al
        orb       %al, %dl
..B1.18:
        movb      %dl, 393(%rsp)
        testb     %r15b, %r15b
        je        ..B1.20
..B1.19:
        fldcw     226(%rsp)
..B1.20:
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.75
..B1.21:
        fldt      368(%rsp)
        fldt      384(%rsp)
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.74
..B1.22:
        addq      $408, %rsp
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
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 448
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.23:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        lea       288(%rsp), %rdi
        fstpt     (%rsp)
..___tag_value_ctanhl.29:
        call      __libm_coshl_k80@PLT
..___tag_value_ctanhl.30:
..B1.86:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
..B1.24:
        fldt      288(%rsp)
        lea       32+_CONSTANTS(%rip), %r14
        movzwl    456(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16183, %eax
        fldt      (%r14)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      272(%rsp)
        fld       %st(0)
        movl      (%rsp), %r14d
        fadd      %st(2), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        jge       ..B1.26
..B1.25:
        fldt      448(%rsp)
        lea       16+_CONSTANTS(%rip), %rbp
        fldt      208(%rsp)
        lea       256(%rsp), %rdx
        fstpt     (%rdx)
        fldt      (%rbp)
        fmulp     %st, %st(1)
        fstpt     -16(%rdx)
        movzwl    -8(%rdx), %eax
        movl      %eax, %ebp
        andl      $-32768, %eax
        andl      $32767, %ebp
        orl       $-49153, %eax
        addl      $-16639, %ebp
        movw      %ax, -8(%rdx)
        jmp       ..B1.28
..B1.26:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        lea       256(%rsp), %rdi
        fstpt     (%rsp)
        fstpt     32(%rsp)
..___tag_value_ctanhl.33:
        call      __libm_sinhl_k80@PLT
..___tag_value_ctanhl.34:
..B1.87:
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
..B1.27:
        fldt      32(%rsp)
        lea       256(%rsp), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        movl      (%rsp), %ebp
..B1.28:
        fldt      240(%rsp)
        fld       %st(0)
        fldt      (%rdx)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fsubp     %st, %st(2)
        fsubp     %st, %st(1)
        fldt      480(%rsp)
        fstpt     (%rsp)
        lea       320(%rsp), %rdi
        fxch      %st(1)
        fstpt     32(%rsp)
        fstpt     112(%rsp)
..___tag_value_ctanhl.37:
        call      __libm_cosl_k80@PLT
..___tag_value_ctanhl.38:
..B1.88:
        fldt      112(%rsp)
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
..B1.29:
        fldt      304(%rsp)
        lea       _CONSTANTS(%rip), %rax
        fld       %st(0)
        lea       t33(%rip), %rdx
        fldt      320(%rsp)
        addl      %ebp, %r14d
        fadd      %st, %st(1)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      (%rax)
        movzwl    168(%rsp), %eax
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        andl      $32767, %eax
        fstpt     144(%rsp)
        fld       %st(0)
        fldt      160(%rsp)
        addl      %ebp, %eax
        fld       %st(0)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      (%rdx)
        fmul      %st, %st(3)
        fld       %st(0)
        fmul      %st(2), %st
        movzwl    152(%rsp), %edx
        fsub      %st, %st(3)
        andl      $32767, %edx
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fadd      %st(5), %st
        fxch      %st(1)
        fstpt     (%rsp)
        fld       %st(3)
        lea       64(%rdx), %ecx
        cmpl      %ecx, %eax
        fsubr     %st(3), %st
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fldt      80(%rsp)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(2)
        fsubp     %st, %st(1)
        fstpt     64(%rsp)
        fldt      112(%rsp)
        jge       ..B1.35
..B1.30:
        addl      $-64, %edx
        cmpl      %edx, %eax
        jle       ..B1.34
..B1.31:
        fstpt     112(%rsp)
        lea       (%rbp,%rbp), %ebx
        movzwl    120(%rsp), %eax
        movl      %eax, %edx
        movzwl    136(%rsp), %ecx
        subl      %ebp, %eax
        andl      $32767, %ecx
        andl      $-32768, %edx
        andl      $32767, %eax
        subl      %ebp, %ecx
        orl       %eax, %edx
        fxch      %st(1)
        fstpt     160(%rsp)
        testl     %ecx, %ecx
        movw      %dx, 120(%rsp)
        jle       ..B1.33
..B1.32:
        fldt      112(%rsp)
        movzwl    168(%rsp), %eax
        movl      %eax, %edx
        subl      %ebp, %eax
        andl      $-32768, %edx
        andl      $32767, %eax
        orl       %eax, %edx
        movw      %dx, 168(%rsp)
        jmp       ..B1.38
..B1.33:
        fldt      112(%rsp)
        jmp       ..B1.38
..B1.34:
        fstp      %st(1)
        fldt      208(%rsp)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fstpt     160(%rsp)
..B1.36:
        fldt      .L_2il0floatpacket.8(%rip)
        fxch      %st(1)
        fstpt     16(%rsp)
        xorl      %ebx, %ebx
        jmp       ..B1.38
..B1.35:
        fldt      208(%rsp)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        jl        ..B1.100
..B1.37:
        fstp      %st(3)
        fstp      %st(0)
        lea       (%rbp,%rbp), %ebx
        fxch      %st(1)
..B1.38:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 480
        fldt      48(%rsp)
        lea       368(%rsp), %rdi
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fstpt     128(%rsp)
..___tag_value_ctanhl.41:
        call      __libm_hypot2l_k80@PLT
..___tag_value_ctanhl.42:
..B1.89:
        fldt      128(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 448
..B1.39:
        fldt      32(%rsp)
        lea       t32(%rip), %rbp
        movzwl    8+.L_2il0floatpacket.9(%rip), %eax
        movl      %eax, %ecx
        andl      $-32768, %ecx
        subl      %ebx, %r14d
        movl      %r14d, %edi
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     176(%rsp)
        fldt      .L_2il0floatpacket.9(%rip)
        movl      176(%rsp), %edx
        addl      %eax, %edx
        andl      $32767, %edx
        fstpt     192(%rsp)
        orl       %edx, %ecx
        fldt      336(%rsp)
        fldt      352(%rsp)
        fldt      16(%rsp)
        fldt      160(%rsp)
        fldt      .L_2il0floatpacket.10(%rip)
        fmul      %st, %st(2)
        fxch      %st(5)
        fmulp     %st, %st(2)
        fldt      112(%rsp)
        movw      %cx, 200(%rsp)
        fmulp     %st, %st(5)
        fmulp     %st, %st(4)
        fldt      192(%rsp)
        fmul      %st, %st(3)
        fld       %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     352(%rsp)
        fldt      352(%rsp)
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fldt      48(%rsp)
        fld       %st(0)
        fdiv      %st(2), %st
        fmul      %st, %st(3)
        fldt      (%rbp)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        fsubr     %st(3), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(3), %st
        fxch      %st(2)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fmul      %st(5), %st
        fsubr     %st, %st(7)
        faddp     %st, %st(7)
        fld       %st(6)
        fsubrp    %st, %st(5)
        fld       %st(0)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st(5), %st
        faddp     %st, %st(7)
        fld       %st(6)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(7)
        faddp     %st, %st(2)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(1)
        fldt      64(%rsp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fld       %st(1)
        fldt      96(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 480
        fdivrp    %st, %st(2)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_ctanhl.45:
        call      __libm_scalbl_k80@PLT
..___tag_value_ctanhl.46:
..B1.90:
        addq      $32, %rsp
	.cfi_def_cfa_offset 448
..B1.40:
        fstpt     368(%rsp)
        cmpq      $0, 464(%rsp)
        jne       ..B1.46
..B1.41:
        fldt      464(%rsp)
        testb     %r15b, %r15b
        fstpt     384(%rsp)
        je        ..B1.43
..B1.42:
        fldcw     226(%rsp)
..B1.43:
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.75
..B1.44:
        fldt      368(%rsp)
        fldt      384(%rsp)
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.74
..B1.45:
        addq      $408, %rsp
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
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 448
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.46:
        movzwl    472(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16183, %eax
        jge       ..B1.48
..B1.47:
        fldt      464(%rsp)
        lea       16+_CONSTANTS(%rip), %rax
        fldt      208(%rsp)
        fstpt     32(%rsp)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        movzwl    24(%rsp), %edx
        movl      %edx, %edi
        andl      $-32768, %edx
        andl      $32767, %edi
        orl       $-49153, %edx
        addl      $-16639, %edi
        movw      %dx, 24(%rsp)
        fldt      16(%rsp)
        jmp       ..B1.50
..B1.48:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      480(%rsp)
        lea       32(%rsp), %rdi
        fstpt     (%rsp)
..___tag_value_ctanhl.63:
        call      __libm_sinl_k80@PLT
..___tag_value_ctanhl.64:
..B1.91:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
..B1.49:
        fldt      16(%rsp)
        xorl      %edi, %edi
        fmul      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      32(%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fstpt     32(%rsp)
..B1.50:
        fldt      208(%rsp)
        fld       %st(1)
        subl      %ebx, %edi
        fadd      %st(1), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fldt      (%rsp)
        fld       %st(0)
        fmul      %st(4), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fldt      144(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(5), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      336(%rsp)
        fld       %st(0)
        fdivr     %st(2), %st
        fld       %st(1)
        fldt      80(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fsubr     %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      352(%rsp)
        fmul      %st(3), %st
        fsubrp    %st, %st(2)
        fldt      336(%rsp)
        fdivrp    %st, %st(2)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 480
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_ctanhl.67:
        call      __libm_scalbl_k80@PLT
..___tag_value_ctanhl.68:
..B1.92:
        addq      $32, %rsp
	.cfi_def_cfa_offset 448
..B1.51:
        fstpt     384(%rsp)
        jmp       ..B1.69
..B1.52:
        cmpq      $0, 464(%rsp)
        jne       ..B1.54
..B1.53:
        movq      448(%rsp), %rbx
        jmp       ..B1.56
..B1.54:
        movzwl    472(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.60
..B1.55:
        movq      448(%rsp), %rbx
        cmpl      $32767, %edx
        je        ..B1.80
..B1.56:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        fstpt     (%rsp)
        call      tanhl@PLT
..B1.93:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fstpt     368(%rsp)
        testq     %rbx, %rbx
        jne       ..B1.58
..B1.57:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      480(%rsp)
        fstpt     (%rsp)
        call      tanl@PLT
..B1.94:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fstpt     384(%rsp)
        jmp       ..B1.69
..B1.58:
        movzwl    472(%rsp), %eax
        testl     $32767, %eax
        jle       ..B1.79
..B1.59:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      480(%rsp)
        fstpt     (%rsp)
        call      tanl@PLT
..B1.95:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fldt      208(%rsp)
        fmulp     %st, %st(1)
        fstpt     384(%rsp)
        jmp       ..B1.69
..B1.60:
        cmpl      $32767, %edx
        jge       ..B1.62
..B1.61:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      480(%rsp)
        fstpt     (%rsp)
        call      tanl@PLT
..B1.96:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fstpt     368(%rsp)
        fldt      368(%rsp)
        fstpt     384(%rsp)
        jmp       ..B1.69
..B1.62:
        cmpl      $-2147483648, 452(%rsp)
        jne       ..B1.64
..B1.63:
        cmpl      $0, 448(%rsp)
        je        ..B1.65
..B1.64:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        fstpt     (%rsp)
        call      tanhl@PLT
..B1.97:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fldt      464(%rsp)
        fmul      %st(1), %st
        fstpt     384(%rsp)
        fstpt     368(%rsp)
        jmp       ..B1.69
..B1.65:
        cmpl      $-2147483648, 468(%rsp)
        jne       ..B1.67
..B1.66:
        cmpl      $0, 464(%rsp)
        je        ..B1.68
..B1.67:
        fldt      464(%rsp)
        fmul      %st(0), %st
        fstpt     464(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        fstpt     (%rsp)
        call      tanhl@PLT
..B1.98:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        fldt      208(%rsp)
        fstpt     384(%rsp)
        fstpt     368(%rsp)
        jmp       ..B1.69
..B1.68:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 464
        fldt      464(%rsp)
        fstpt     (%rsp)
        call      tanhl@PLT
..B1.99:
        addq      $16, %rsp
	.cfi_def_cfa_offset 448
        lea       ones(%rip), %rdx
        movb      473(%rsp), %al
        pxor      %xmm0, %xmm0
        andb      $-128, %al
        fstpt     368(%rsp)
        shrb      $7, %al
        movzbl    %al, %ecx
        mulsd     (%rdx,%rcx,8), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     384(%rsp)
..B1.69:
        testb     %r15b, %r15b
        je        ..B1.71
..B1.70:
        fldcw     226(%rsp)
..B1.71:
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.75
..B1.72:
        fldt      368(%rsp)
        fldt      384(%rsp)
        movq      400(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.74
..B1.73:
        addq      $408, %rsp
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
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 448
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.74:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.75:
        call      __stack_chk_fail@PLT
..B1.76:
        movb      393(%rsp), %dl
        movb      473(%rsp), %al
        andb      $127, %dl
        andb      $-128, %al
        orb       %al, %dl
        jmp       ..B1.18
..B1.79:
        fldt      464(%rsp)
        fldt      208(%rsp)
        fmulp     %st, %st(1)
        fstpt     384(%rsp)
        jmp       ..B1.69
..B1.80:
        movq      $0x8000000000000000, %rax
        cmpq      %rax, %rbx
        je        ..B1.56
        jmp       ..B1.60
..B1.100:
        fstp      %st(0)
        fstp      %st(1)
        jmp       ..B1.36
        .align    16,0x90
	.cfi_endproc
	.type	ctanhl,@function
	.size	ctanhl,.-ctanhl
	.data
# -- End  ctanhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
t33:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	t33,@object
	.size	t33,32
	.align 16
t32:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	t32,@object
	.size	t32,32
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16639
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
