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
	.file "lgammal.c"
	.text
..TXTST0:
# -- Begin  lgammal
	.text
       .align    16,0x90
	.globl lgammal
lgammal:
# parameter 1: 16 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_lgammal.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        fldt      16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 32
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
..___tag_value_lgammal.5:
        call      __libm_lgammal
..___tag_value_lgammal.6:
..B1.2:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	lgammal,@function
	.size	lgammal,.-lgammal
	.data
# -- End  lgammal
	.text
# -- Begin  __libm_lgammal
	.text
       .align    16,0x90
__libm_lgammal:
# parameter 1: 336 + %rsp
# parameter 2: %rdi
..B2.1:
	.cfi_startproc
..___tag_value___libm_lgammal.9:
..L10:

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
        subq      $280, %rsp
	.cfi_def_cfa_offset 336
        movq      %rdi, %rbp
        movq      %fs:40, %rax
        xorl      %r12d, %r12d
        xorq      %rsp, %rax
        xorb      %r14b, %r14b
        movq      %rax, 272(%rsp)
..B2.2:
        fnstcw    194(%rsp)
..B2.3:
..___tag_value___libm_lgammal.24:
        call      fegetround@PLT
..___tag_value___libm_lgammal.25:
..B2.275:
        movl      %eax, %r13d
..B2.4:
        testl     %r13d, %r13d
        je        ..B2.6
..B2.5:
        xorl      %edi, %edi
        movl      $1, %r12d
..___tag_value___libm_lgammal.26:
        call      fesetround@PLT
..___tag_value___libm_lgammal.27:
..B2.6:
        movzwl    194(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.10
..B2.7:
        orl       $-64768, %edx
        movw      %dx, 192(%rsp)
..B2.8:
        fldcw     192(%rsp)
..B2.9:
        movb      $1, %r14b
..B2.10:
        movzbl    345(%rsp), %ecx
        lea       ones(%rip), %rax
        movl      %ecx, %r15d
        andl      $128, %r15d
        shrl      $7, %r15d
        movzwl    344(%rsp), %ebx
        movss     (%rax,%r15,4), %xmm0
        andl      $32767, %ebx
        shll      $15, %r15d
        orl       %ebx, %r15d
        shll      $16, %r15d
        movzwl    342(%rsp), %edx
        orl       %edx, %r15d
        cmpl      $32767, %ebx
        je        ..B2.259
..B2.11:
        fldt      336(%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        fucomi    %st(1), %st
        jp        ..B2.12
        je        ..B2.252
..B2.12:
        lea       _OFW_BOUND(%rip), %rax
        fldt      (%rax)
        fcomip    %st(2), %st
        jb        ..B2.244
..B2.13:
        fldt      .L_2il0floatpacket.1(%rip)
        fucomip   %st(2), %st
        jp        ..B2.14
        je        ..B2.235
..B2.14:
        fldt      .L_2il0floatpacket.2(%rip)
        fucomip   %st(2), %st
        jp        ..B2.15
        je        ..B2.235
..B2.15:
        testl     %r15d, %r15d
        jl        ..B2.227
..B2.16:
        cmpl      $16382, %ebx
        jl        ..B2.163
..B2.17:
        cmpl      $16463, %ebx
        jge       ..B2.156
..B2.18:
        fldt      .L_2il0floatpacket.4(%rip)
        fcomip    %st(2), %st
        jbe       ..B2.146
..B2.19:
        fldt      .L_2il0floatpacket.5(%rip)
        fcomip    %st(2), %st
        jae       ..B2.113
        jp        ..B2.113
..B2.20:
        movl      %r15d, %eax
        andl      $2147483647, %eax
        cmpl      $1073659904, %eax
        jl        ..B2.104
..B2.21:
        fcomi     %st(1), %st
        jae       ..B2.24
        jp        ..B2.24
..B2.22:
        fldt      .L_2il0floatpacket.10(%rip)
        fcomip    %st(2), %st
        jbe       ..B2.24
..B2.23:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.11(%rip)
        movl      $1, (%rbp)
        jmp       ..B2.27
..B2.24:
        fldt      .L_2il0floatpacket.11(%rip)
        fcomi     %st(2), %st
        jae       ..B2.37
        jp        ..B2.37
..B2.25:
        fldt      .L_2il0floatpacket.12(%rip)
        fcomip    %st(3), %st
        jbe       ..B2.37
..B2.26:
        fstp      %st(1)
        movl      $1, (%rbp)
..B2.27:
        lea       2800+_ldbA(%rip), %rdx
        lea       480+_dbA(%rip), %rcx
        fcomip    %st(1), %st
        jae       ..B2.29
        jp        ..B2.29
..B2.28:
        fldt      .L_2il0floatpacket.2(%rip)
        lea       656+_dbA(%rip), %rcx
        lea       2912+_ldbA(%rip), %rdx
        fsubrp    %st, %st(1)
        jmp       ..B2.30
..B2.29:
        fldt      .L_2il0floatpacket.1(%rip)
        fsubp     %st, %st(1)
..B2.30:
        fldl      88(%rcx)
        lea       _TWO_32H(%rip), %rax
        fmul      %st(1), %st
        fld       %st(1)
        fld       %st(2)
        testl     %r12d, %r12d
        fldl      (%rax)
        fmul      %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(3)
        fsub      %st(2), %st
        fldt      (%rdx)
        fxch      %st(4)
        faddl     104(%rcx)
        fmul      %st(5), %st
        fldt      48(%rdx)
        fldt      16(%rdx)
        fxch      %st(2)
        faddl     96(%rcx)
        fmul      %st(7), %st
        faddl     80(%rcx)
        fmul      %st(7), %st
        faddl     152(%rcx)
        fmul      %st(7), %st
        faddl     120(%rcx)
        fmul      %st(7), %st
        faddl     112(%rcx)
        fmul      %st(7), %st
        faddl     136(%rcx)
        fmul      %st(7), %st
        faddl     128(%rcx)
        fmul      %st(7), %st
        faddl     40(%rcx)
        fmul      %st(7), %st
        faddl     32(%rcx)
        fmul      %st(7), %st
        faddl     56(%rcx)
        fmul      %st(7), %st
        faddl     48(%rcx)
        fmul      %st(7), %st
        faddl     144(%rcx)
        fmul      %st(7), %st
        faddp     %st, %st(6)
        fxch      %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        fldt      64(%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      32(%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      80(%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fld       %st(1)
        fldt      96(%rdx)
        faddp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      (%rcx)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     72(%rcx)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      16(%rcx)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(1)
        faddl     168(%rcx)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      64(%rcx)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(1)
        faddl     8(%rcx)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      160(%rcx)
        fld       %st(0)
        fadd      %st(3), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddl     24(%rcx)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        je        ..B2.33
..B2.31:
        fstp      %st(1)
        fstp      %st(0)
        movl      %r13d, %edi
..___tag_value___libm_lgammal.28:
        call      fesetround@PLT
..___tag_value___libm_lgammal.29:
..B2.32:
        fldt      (%rsp)
        fldt      16(%rsp)
..B2.33:
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.35
..B2.34:
        fldcw     194(%rsp)
..B2.35:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.36:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.37:
        fstp      %st(0)
        fcomi     %st(1), %st
        jae       ..B2.46
        jp        ..B2.46
..B2.38:
        fldt      .L_2il0floatpacket.2(%rip)
        fcomip    %st(2), %st
        jbe       ..B2.46
..B2.39:
        fstp      %st(0)
        lea       3024+_ldbA(%rip), %rax
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        lea       3040+_ldbA(%rip), %rdi
        lea       832+_dbA(%rip), %rsi
        movl      $1, (%rbp)
        lea       16(%rsp), %rdx
        fldt      (%rax)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
..___tag_value___libm_lgammal.51:
        call      __libm_p25
..___tag_value___libm_lgammal.52:
..B2.276:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.40:
        testl     %r12d, %r12d
        je        ..B2.42
..B2.41:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.54:
        call      fesetround@PLT
..___tag_value___libm_lgammal.55:
..B2.42:
        fldt      (%rsp)
        testb     %r14b, %r14b
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.44
..B2.43:
        fldcw     194(%rsp)
..B2.44:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.45:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.46:
        fcomi     %st(1), %st
        jbe       ..B2.95
..B2.47:
        fldt      .L_2il0floatpacket.14(%rip)
        fcomi     %st(2), %st
        jae       ..B2.56
        jp        ..B2.56
..B2.48:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.15(%rip)
        fcomi     %st(2), %st
        jbe       ..B2.56
..B2.49:
        fstp      %st(1)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        lea       3424+_ldbA(%rip), %rdi
        lea       1360+_dbA(%rip), %rsi
        lea       16(%rsp), %rdx
        movl      $-1, (%rbp)
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
..___tag_value___libm_lgammal.77:
        call      __libm_p25
..___tag_value___libm_lgammal.78:
..B2.277:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.50:
        testl     %r12d, %r12d
        je        ..B2.52
..B2.51:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.80:
        call      fesetround@PLT
..___tag_value___libm_lgammal.81:
..B2.52:
        fldt      (%rsp)
        testb     %r14b, %r14b
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.54
..B2.53:
        fldcw     194(%rsp)
..B2.54:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.55:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.56:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fstp      %st(0)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     64(%rsp)
        call      nearbyintl@PLT
..B2.278:
        fldt      64(%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fxch      %st(2)
        fucomi    %st(1), %st
        jp        ..B2.57
        je        ..B2.191
..B2.57:
        fldt      .L_2il0floatpacket.17(%rip)
        fcomip    %st(2), %st
        ja        ..B2.69
        jp        ..B2.69
..B2.58:
        fldt      .L_2il0floatpacket.18(%rip)
        fcomip    %st(2), %st
        jb        ..B2.69
..B2.59:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.19(%rip)
        fcomip    %st(1), %st
        jae       ..B2.61
        jp        ..B2.61
..B2.60:
        movl      $-1, (%rbp)
        jmp       ..B2.62
..B2.61:
        movl      $1, (%rbp)
..B2.62:
        fldt      .L_2il0floatpacket.1(%rip)
        lea       ones(%rip), %rdx
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fld       %st(0)
        movb      105(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fmul      %st(1), %st
        fstpt     128(%rsp)
        fld       %st(0)
        fchs      
        movzbl    %al, %ecx
        fstpt     16(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        lea       240(%rsp), %rdi
        fmuls     (%rdx,%rcx,4)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_lgammal.105:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.106:
..B2.279:
        fstp      %st(0)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.63:
        fldt      96(%rsp)
        lea       1560+_dbA(%rip), %rax
        fldt      16(%rsp)
        lea       1552+_dbA(%rip), %rdx
        lea       1544+_dbA(%rip), %rcx
        lea       1536+_dbA(%rip), %rbx
        lea       3600+_ldbA(%rip), %rbp
        lea       3584+_ldbA(%rip), %rsi
        fldl      (%rax)
        lea       1568+_dbA(%rip), %r10
        fmul      %st(2), %st
        lea       1576+_dbA(%rip), %r11
        fldt      (%rbp)
        lea       1584+_dbA(%rip), %r15
        fldt      (%rsi)
        lea       1592+_dbA(%rip), %rax
        lea       3568+_ldbA(%rip), %r8
        lea       3552+_ldbA(%rip), %r9
        testl     %r12d, %r12d
        fxch      %st(2)
        faddl     (%rdx)
        fmul      %st(4), %st
        fldt      (%r8)
        fldt      (%r9)
        fxch      %st(2)
        faddl     (%rcx)
        fmul      %st(6), %st
        faddl     (%rbx)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldl      (%r10)
        fmul      %st(1), %st
        fldt      128(%rsp)
        fxch      %st(1)
        faddl     (%r11)
        fmul      %st(2), %st
        faddl     (%r15)
        fmul      %st(2), %st
        faddl     (%rax)
        lea       1600+_dbA(%rip), %rax
        fmul      %st(2), %st
        faddl     (%rax)
        lea       1608+_dbA(%rip), %rax
        fmul      %st(2), %st
        faddl     (%rax)
        lea       1616+_dbA(%rip), %rax
        fmul      %st(2), %st
        faddl     (%rax)
        lea       1624+_dbA(%rip), %rax
        fmul      %st(2), %st
        faddl     (%rax)
        lea       3664+_ldbA(%rip), %rax
        fmul      %st(2), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        faddp     %st, %st(2)
        fldt      (%rax)
        lea       3648+_ldbA(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rax)
        lea       3632+_ldbA(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        lea       3616+_ldbA(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B2.65
..B2.64:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.108:
        call      fesetround@PLT
..___tag_value___libm_lgammal.109:
..B2.65:
        fldt      224(%rsp)
        testb     %r14b, %r14b
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fldt      208(%rsp)
        fsubrp    %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.67
..B2.66:
        fldcw     194(%rsp)
..B2.67:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.68:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.69:
        fnstcw    (%rsp)
        movzwl    (%rsp), %eax
        orl       $3072, %eax
        movl      %eax, 8(%rsp)
        fldcw     8(%rsp)
        fistl     16(%rsp)
        fldcw     (%rsp)
        fld       %st(1)
        lea       848+_ldbA(%rip), %r8
        movl      16(%rsp), %edx
        fsubp     %st, %st(1)
        negl      %edx
        lea       -2(%rdx), %eax
        andl      $1, %edx
        jne       ..B2.72
..B2.70:
        fcomi     %st(2), %st
        jae       ..B2.73
        jp        ..B2.73
..B2.71:
        movl      $-1, (%rbp)
        jmp       ..B2.74
..B2.72:
        jne       ..B2.188
..B2.73:
        movl      $1, (%rbp)
..B2.74:
        fldt      .L_2il0floatpacket.20(%rip)
        xorl      %edx, %edx
        fxch      %st(2)
        fcomi     %st(2), %st
        fxch      %st(2)
        fstp      %st(0)
        cmova     %edx, %eax
        movl      $17, %ecx
        xorl      %ebp, %ebp
        lea       1424+_ldbA(%rip), %rdx
        fcomi     %st(2), %st
        cmova     %ecx, %ebp
        addl      %ebp, %eax
        movslq    %eax, %rsi
        shlq      $4, %rsi
        fldt      (%rsi,%r8)
        lea       (%rax,%rax), %r8d
        movslq    %r8d, %r8
        fsubr     %st(2), %st
        shlq      $4, %r8
        fldt      (%r8,%rdx)
        fcomip    %st(3), %st
        jae       ..B2.84
        jp        ..B2.84
..B2.75:
        fldt      16(%r8,%rdx)
        fcomip    %st(3), %st
        jbe       ..B2.84
..B2.77:
        fstp      %st(3)
        fstp      %st(1)
        lea       _ldbA(%rip), %rbx
        lea       (%rax,%rax,2), %ecx
        movslq    %ecx, %rcx
        lea       (%rax,%rax,4), %eax
        shll      $2, %eax
        lea       _dbA(%rip), %rbp
        shlq      $4, %rcx
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        movslq    %eax, %rax
        lea       256(%rsp), %rdx
        fxch      %st(1)
        fstpt     (%rsp)
        lea       4480(%rbx,%rcx), %rdi
        fstpt     16(%rsp)
        lea       2432(%rbp,%rax,8), %rsi
..___tag_value___libm_lgammal.131:
        call      __libm_p19root
..___tag_value___libm_lgammal.132:
..B2.280:
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.78:
        testl     %r12d, %r12d
        je        ..B2.80
..B2.79:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value___libm_lgammal.134:
        call      fesetround@PLT
..___tag_value___libm_lgammal.135:
..B2.305:
        fldt      (%rsp)
..B2.80:
        fldt      240(%rsp)
        testb     %r14b, %r14b
        fldt      256(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.311
..B2.81:
        fstpt     96(%rsp)
..B2.281:
        fldcw     194(%rsp)
        jmp       ..B2.82
..B2.311:
        fstp      %st(0)
..B2.82:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.83:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.84:
        fstp      %st(0)
        fstpt     96(%rsp)
..B2.85:
        lea       -16382(%rbx), %eax
        cmpl      $2, %eax
        jne       ..B2.87
..B2.86:
        fldt      .L_2il0floatpacket.21(%rip)
        addl      $-16380, %ebx
        fcomip    %st(1), %st
        cmova     %ebx, %eax
..B2.87:
        fldt      .L_2il0floatpacket.19(%rip)
        lea       (%rax,%rax,4), %edx
        fmul      %st, %st(1)
        lea       (%rdx,%rdx), %eax
        fstp      %st(2)
        fstpt     80(%rsp)
        lea       _dbA(%rip), %rsi
        fldt      .L_2il0floatpacket.16(%rip)
        lea       _ldbA(%rip), %rbx
        movzwl    88(%rsp), %ecx
        lea       _TWO_32H(%rip), %rbp
        andl      $-32768, %ecx
        orl       $-49153, %ecx
        movw      %cx, 88(%rsp)
        fldt      80(%rsp)
        shll      $2, %edx
        fsubp     %st, %st(1)
        movslq    %edx, %rdx
        movslq    %eax, %rax
        shlq      $4, %rax
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldl      1784(%rsi,%rdx,8)
        fmul      %st(1), %st
        fldt      3824(%rbx,%rax)
        fldt      3808(%rbx,%rax)
        fldt      3792(%rbx,%rax)
        fldt      3776(%rbx,%rax)
        fldt      3760(%rbx,%rax)
        fxch      %st(5)
        faddl     1776(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1768(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1760(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1752(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1744(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1736(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1728(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1720(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1712(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1704(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddl     1696(%rsi,%rdx,8)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      3744(%rbx,%rax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      3728(%rbx,%rax)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fld       %st(1)
        fldt      3712(%rbx,%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      3696(%rbx,%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      3680(%rbx,%rax)
        faddp     %st, %st(1)
        fldl      (%rbp)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fld       %st(2)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(6)
        fxch      %st(1)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      1632(%rsi,%rdx,8)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     1640(%rsi,%rdx,8)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      1680(%rsi,%rdx,8)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     1688(%rsi,%rdx,8)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      1664(%rsi,%rdx,8)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fxch      %st(1)
        faddl     1672(%rsi,%rdx,8)
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(2), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldl      1648(%rsi,%rdx,8)
        fld       %st(0)
        fadd      %st(4), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        fxch      %st(3)
        faddl     1656(%rsi,%rdx,8)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      336(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        fmulp     %st, %st(3)
        lea       272(%rsp), %rdi
        fxch      %st(2)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fstpt     64(%rsp)
..___tag_value___libm_lgammal.157:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.158:
..B2.282:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        fldt      64(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.88:
        fldt      96(%rsp)
        lea       ones(%rip), %rdx
        movb      105(%rsp), %al
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        andb      $-128, %al
        lea       240(%rsp), %rdi
        shrb      $7, %al
        movzbl    %al, %ecx
        fmuls     (%rdx,%rcx,4)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(2)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fstpt     64(%rsp)
..___tag_value___libm_lgammal.161:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.162:
..B2.283:
        fstp      %st(0)
        fldt      64(%rsp)
        fldt      80(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.89:
        fld       %st(0)
        lea       544+_ldbA(%rip), %rcx
        fmul      %st(2), %st
        fld       %st(1)
        lea       528+_ldbA(%rip), %rbx
        lea       512+_ldbA(%rip), %rbp
        lea       496+_ldbA(%rip), %rsi
        lea       480+_ldbA(%rip), %r8
        lea       464+_ldbA(%rip), %r9
        lea       448+_ldbA(%rip), %r10
        lea       432+_ldbA(%rip), %r11
        lea       416+_ldbA(%rip), %r15
        lea       304+_ldbA(%rip), %rax
        lea       48+_dbA(%rip), %rdx
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(1)
        fsub      %st(1), %st
        fxch      %st(2)
        fmul      %st(0), %st
        fldt      (%rcx)
        lea       400+_ldbA(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r15)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       384+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       368+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       352+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       336+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fldt      (%rcx)
        lea       320+_ldbA(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rax)
        lea       64+_dbA(%rip), %rax
        faddp     %st, %st(2)
        fld       %st(0)
        fldt      .L_2il0floatpacket.2(%rip)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        fld       %st(5)
        fmul      %st(3), %st
        fxch      %st(5)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(5)
        fmul      %st(2), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fld       %st(3)
        fadd      %st(6), %st
        fsubp     %st, %st(6)
        fmul      %st(5), %st
        fxch      %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(5), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fldl      (%rax)
        fld       %st(0)
        lea       72+_dbA(%rip), %rax
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(6)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(4)
        fxch      %st(2)
        faddl     (%rax)
        lea       56+_dbA(%rip), %rax
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fldl      (%rdx)
        fld       %st(0)
        lea       ones(%rip), %rdx
        fadd      %st(3), %st
        fmul      %st, %st(6)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(6), %st
        fsubp     %st, %st(6)
        fxch      %st(2)
        fsub      %st(5), %st
        fld       %st(3)
        fmul      %st(6), %st
        fstpt     64(%rsp)
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(2)
        faddl     (%rax)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      16(%rsp)
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      (%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      240(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(5)
        movb      137(%rsp), %al
        fxch      %st(4)
        fsub      %st(5), %st
        andb      $-128, %al
        fadd      %st(4), %st
        shrb      $7, %al
        fadd      %st(3), %st
        fxch      %st(4)
        fsub      %st(5), %st
        movzbl    %al, %eax
        faddp     %st, %st(6)
        fxch      %st(1)
        fmuls     (%rdx,%rax,4)
        fldt      256(%rsp)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fxch      %st(2)
        fadd      %st, %st(6)
        movb      153(%rsp), %al
        fxch      %st(3)
        faddp     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      208(%rsp)
        andb      $-128, %al
        fld       %st(0)
        fldt      64(%rsp)
        shrb      $7, %al
        fadd      %st, %st(1)
        fxch      %st(1)
        fchs      
        fadd      %st, %st(2)
        movzbl    %al, %eax
        fxch      %st(2)
        fchs      
        fsubp     %st, %st(1)
        fld       %st(1)
        fsub      %st(6), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(3)
        fmuls     (%rdx,%rax,4)
        fxch      %st(4)
        fcomip    %st(4), %st
        fxch      %st(3)
        fstp      %st(0)
        fxch      %st(5)
        fcmovnbe  %st(3), %st
        fstp      %st(3)
        fld       %st(4)
        fldt      224(%rsp)
        fsubrp    %st, %st(3)
        fld       %st(4)
        fadd      %st(4), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      80(%rsp)
        fsubrp    %st, %st(4)
        fxch      %st(1)
        fadd      %st(3), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fld       %st(6)
        movb      137(%rsp), %al
        fsub      %st(3), %st
        andb      $-128, %al
        fsub      %st(6), %st
        shrb      $7, %al
        fadd      %st(4), %st
        movzbl    %al, %eax
        fsub      %st(5), %st
        fxch      %st(3)
        faddp     %st, %st(6)
        fxch      %st(5)
        fchs      
        faddp     %st, %st(6)
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmuls     (%rdx,%rax,4)
        movb      153(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fstpt     128(%rsp)
        fldt      128(%rsp)
        movzbl    %al, %eax
        fxch      %st(1)
        fmuls     (%rdx,%rax,4)
        fxch      %st(1)
        fcomip    %st(1), %st
        fxch      %st(2)
        fcmovnbe  %st(1), %st
        fstp      %st(1)
        fstpt     32(%rsp)
        testl     %r12d, %r12d
        je        ..B2.91
..B2.90:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value___libm_lgammal.164:
        call      fesetround@PLT
..___tag_value___libm_lgammal.165:
..B2.303:
        fldt      (%rsp)
..B2.91:
        fldt      32(%rsp)
        testb     %r14b, %r14b
        fldt      48(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.312
..B2.92:
        fstpt     144(%rsp)
..B2.284:
        fldcw     194(%rsp)
        jmp       ..B2.93
..B2.312:
        fstp      %st(0)
..B2.93:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.94:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.95:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.13(%rip)
        lea       3168+_ldbA(%rip), %rdi
        movl      $1, (%rbp)
        lea       1008+_dbA(%rip), %rsi
        fmul      %st, %st(1)
        cmpl      $16385, %ebx
        jne       ..B2.97
..B2.96:
        fmul      %st, %st(1)
        lea       1184+_dbA(%rip), %rsi
        lea       3296+_ldbA(%rip), %rdi
..B2.97:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.16(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fsubrp    %st, %st(1)
        fstpt     (%rsp)
        lea       16(%rsp), %rdx
..___tag_value___libm_lgammal.187:
        call      __libm_p25
..___tag_value___libm_lgammal.188:
..B2.285:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.98:
        testl     %r12d, %r12d
        je        ..B2.100
..B2.99:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.190:
        call      fesetround@PLT
..___tag_value___libm_lgammal.191:
..B2.100:
        fldt      (%rsp)
        testb     %r14b, %r14b
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.102
..B2.101:
        fldcw     194(%rsp)
..B2.102:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.103:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.104:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.8(%rip)
        lea       2544+_ldbA(%rip), %rdi
        lea       128+_dbA(%rip), %rsi
        testl     %r15d, %r15d
        fsubr     %st(1), %st
        jl        ..B2.200
..B2.105:
        fstp      %st(1)
        movl      $1, (%rbp)
..B2.106:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        lea       16(%rsp), %rdx
        fstpt     (%rsp)
..___tag_value___libm_lgammal.213:
        call      __libm_p25
..___tag_value___libm_lgammal.214:
..B2.286:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.107:
        testl     %r12d, %r12d
        je        ..B2.109
..B2.108:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.216:
        call      fesetround@PLT
..___tag_value___libm_lgammal.217:
..B2.109:
        fldt      (%rsp)
        testb     %r14b, %r14b
        fldt      16(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.111
..B2.110:
        fldcw     194(%rsp)
..B2.111:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.112:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.113:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fstp      %st(0)
        fstpt     (%rsp)
        fldt      (%rsp)
        movss     %xmm0, 48(%rsp)
        fstpt     64(%rsp)
        call      nearbyintl@PLT
..B2.287:
        fldt      64(%rsp)
        fld       %st(0)
        movss     48(%rsp), %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fsub      %st(2), %st
        fldt      .L_2il0floatpacket.0(%rip)
        fucomi    %st(1), %st
        jp        ..B2.114
        je        ..B2.209
..B2.114:
        fldt      .L_2il0floatpacket.9(%rip)
        fcomip    %st(3), %st
        jae       ..B2.131
        jp        ..B2.131
..B2.115:
        fxch      %st(3)
        fnstcw    (%rsp)
        movzwl    (%rsp), %eax
        orl       $3072, %eax
        movl      %eax, 8(%rsp)
        fldcw     8(%rsp)
        fistl     16(%rsp)
        fldcw     (%rsp)
        lea       848+_ldbA(%rip), %rbx
        movl      16(%rsp), %r8d
        movl      %r8d, %edx
        negl      %edx
        lea       -2(%rdx), %eax
        addl      $15, %edx
        fxch      %st(1)
        fcomi     %st(3), %st
        cmova     %edx, %eax
        lea       1424+_ldbA(%rip), %rdx
        movslq    %eax, %rcx
        shlq      $4, %rcx
        lea       (%rax,%rax), %esi
        movslq    %esi, %rsi
        fldt      (%rcx,%rbx)
        shlq      $4, %rsi
        fsubr     %st(3), %st
        fldt      (%rsi,%rdx)
        fcomip    %st(4), %st
        jae       ..B2.129
        jp        ..B2.129
..B2.116:
        fldt      16(%rsi,%rdx)
        fcomip    %st(4), %st
        jbe       ..B2.129
..B2.117:
        fstp      %st(2)
        fstp      %st(2)
        negl      %r8d
        andl      $1, %r8d
        jne       ..B2.120
..B2.118:
        fxch      %st(2)
        fcomi     %st(1), %st
        fxch      %st(2)
        jbe       ..B2.121
..B2.119:
        fstp      %st(2)
        movl      $-1, (%rbp)
        jmp       ..B2.122
..B2.120:
        jne       ..B2.202
..B2.121:
        fstp      %st(2)
        movl      $1, (%rbp)
..B2.122:
        lea       _ldbA(%rip), %rbx
        lea       (%rax,%rax,2), %ecx
        movslq    %ecx, %rcx
        lea       (%rax,%rax,4), %eax
        shll      $2, %eax
        lea       _dbA(%rip), %rbp
        shlq      $4, %rcx
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        movslq    %eax, %rax
        lea       256(%rsp), %rdx
        fxch      %st(1)
        fstpt     (%rsp)
        lea       4480(%rbx,%rcx), %rdi
        fstpt     16(%rsp)
        lea       2432(%rbp,%rax,8), %rsi
..___tag_value___libm_lgammal.241:
        call      __libm_p19root
..___tag_value___libm_lgammal.242:
..B2.288:
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B2.123:
        testl     %r12d, %r12d
        je        ..B2.125
..B2.124:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value___libm_lgammal.244:
        call      fesetround@PLT
..___tag_value___libm_lgammal.245:
..B2.307:
        fldt      (%rsp)
..B2.125:
        fldt      240(%rsp)
        testb     %r14b, %r14b
        fldt      256(%rsp)
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.313
..B2.126:
        fstpt     96(%rsp)
..B2.289:
        fldcw     194(%rsp)
        jmp       ..B2.127
..B2.313:
        fstp      %st(0)
..B2.127:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.128:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.129:
        fstp      %st(0)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fxch      %st(1)
        fxch      %st(3)
        fxch      %st(1)
        jmp       ..B2.132
..B2.131:
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
..B2.132:
        fldt      .L_2il0floatpacket.13(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fmulp     %st, %st(4)
        fstp      %st(1)
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     32(%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        movss     %xmm0, 48(%rsp)
        fstpt     64(%rsp)
        call      nearbyintl@PLT
..B2.290:
        fldt      64(%rsp)
        fldt      16(%rsp)
        fldt      32(%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        movss     48(%rsp), %xmm0
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fxch      %st(4)
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B2.133
        je        ..B2.206
..B2.133:
        fcomip    %st(2), %st
        jbe       ..B2.135
..B2.134:
        movl      $-1, (%rbp)
        jmp       ..B2.136
..B2.135:
        movl      $1, (%rbp)
..B2.136:
        movss     %xmm0, (%rsp)
        fxch      %st(1)
        flds      (%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        lea       272(%rsp), %rdi
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_lgammal.269:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.270:
..B2.291:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.137:
        fldt      240(%rsp)
        lea       _TWO_32H(%rip), %rax
        fldt      256(%rsp)
        fstp      %st(2)
        lea       560+_ldbA(%rip), %rdx
        fldt      80(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldl      (%rax)
        lea       80+_dbA(%rip), %rax
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fld       %st(2)
        fsub      %st(1), %st
        fldt      .L_2il0floatpacket.13(%rip)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fstpt     48(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fsubrp    %st, %st(6)
        fxch      %st(3)
        fmul      %st(5), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fld       %st(5)
        fadd      %st(1), %st
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(5)
        fxch      %st(6)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fld       %st(5)
        fmul      %st(2), %st
        fxch      %st(4)
        fmul      %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fld       %st(3)
        fadd      %st(3), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(2)
        fldt      48(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      .L_2il0floatpacket.7(%rip)
        fcomip    %st(1), %st
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        jbe       ..B2.139
..B2.138:
        lea       704+_ldbA(%rip), %rdx
        lea       96+_dbA(%rip), %rax
..B2.139:
        fldt      .L_2il0floatpacket.1(%rip)
        fld       %st(3)
        fstp      %st(2)
        movb      105(%rsp), %cl
        lea       ones(%rip), %rbx
        andb      $-128, %cl
        fxch      %st(1)
        fadd      %st(2), %st
        shrb      $7, %cl
        fdivr     %st(1), %st
        fld       %st(3)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movzbl    %cl, %ebp
        fadd      %st(4), %st
        fsubp     %st, %st(1)
        fmul      %st, %st(6)
        fxch      %st(4)
        fsubrp    %st, %st(6)
        fxch      %st(2)
        fsub      %st(3), %st
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st(0), %st
        fldt      128(%rdx)
        fmul      %st(1), %st
        fldt      112(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      96(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      80(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      64(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      32(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      16(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       120+_dbA(%rip), %rdx
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st, %st(5)
        fld       %st(0)
        fadd      %st(6), %st
        fsubp     %st, %st(6)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(5), %st
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fxch      %st(2)
        fsub      %st(5), %st
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(1)
        faddl     8(%rax)
        lea       112+_dbA(%rip), %rax
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     256(%rsp)
        fldt      256(%rsp)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      96(%rsp)
        fmuls     (%rbx,%rbp,4)
        fxch      %st(2)
        faddl     (%rdx)
        faddp     %st, %st(1)
        fstpt     48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        lea       240(%rsp), %rdi
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     16(%rsp)
        fstpt     -128(%rdi)
..___tag_value___libm_lgammal.273:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.274:
..B2.292:
        fstp      %st(0)
        fldt      112(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.140:
        fldt      16(%rsp)
        lea       544+_ldbA(%rip), %rcx
        fld       %st(1)
        lea       528+_ldbA(%rip), %rbx
        lea       512+_ldbA(%rip), %rbp
        lea       496+_ldbA(%rip), %rsi
        fmul      %st(1), %st
        fld       %st(2)
        lea       480+_ldbA(%rip), %r8
        lea       464+_ldbA(%rip), %r9
        lea       448+_ldbA(%rip), %r10
        lea       432+_ldbA(%rip), %r11
        lea       416+_ldbA(%rip), %r15
        lea       304+_ldbA(%rip), %rax
        lea       48+_dbA(%rip), %rdx
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(2)
        fsub      %st(1), %st
        fxch      %st(3)
        fmul      %st(0), %st
        fldt      (%rcx)
        lea       400+_ldbA(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rbp)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r15)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       384+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       368+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       352+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       336+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       320+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fld       %st(3)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fmul      %st, %st(2)
        fldt      (%rax)
        lea       64+_dbA(%rip), %rax
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.2(%rip)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(6)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fld       %st(2)
        fadd      %st(7), %st
        fsubp     %st, %st(7)
        fmul      %st(6), %st
        fxch      %st(3)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(3)
        fxch      %st(2)
        fsub      %st(6), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        lea       72+_dbA(%rip), %rax
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(5)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fld       %st(4)
        fxch      %st(2)
        faddl     (%rax)
        lea       56+_dbA(%rip), %rax
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmul      %st(2), %st
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      (%rdx)
        fld       %st(0)
        lea       ones(%rip), %rdx
        fadd      %st(2), %st
        fmul      %st, %st(5)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fxch      %st(1)
        fsub      %st(4), %st
        fld       %st(3)
        fmul      %st(5), %st
        fxch      %st(5)
        fmul      %st(6), %st
        fxch      %st(2)
        faddl     (%rax)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      208(%rsp)
        fld       %st(0)
        fchs      
        fldt      (%rsp)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fld       %st(1)
        movb      185(%rsp), %al
        fsub      %st(1), %st
        andb      $-128, %al
        fadd      %st(4), %st
        shrb      $7, %al
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        fadd      %st, %st(4)
        fcomip    %st(3), %st
        fxch      %st(2)
        fstp      %st(0)
        fldt      32(%rsp)
        movzbl    %al, %eax
        fadd      %st, %st(2)
        fldt      224(%rsp)
        fadd      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        fcmovnbe  %st(1), %st
        fstp      %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fld       %st(2)
        fsub      %st(1), %st
        fadd      %st(4), %st
        fadd      %st(2), %st
        flds      (%rdx,%rax,4)
        fmul      %st(4), %st
        movb      169(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      64(%rsp)
        fadd      %st, %st(2)
        movzbl    %al, %eax
        flds      (%rdx,%rax,4)
        fmul      %st(7), %st
        fxch      %st(2)
        fcomip    %st(2), %st
        fxch      %st(1)
        fstpt     112(%rsp)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fcmovbe   %st(1), %st
        fstp      %st(1)
        fstpt     96(%rsp)
        testl     %r12d, %r12d
        je        ..B2.142
..B2.141:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.276:
        call      fesetround@PLT
..___tag_value___libm_lgammal.277:
..B2.142:
        fldt      80(%rsp)
        testb     %r14b, %r14b
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fchs      
        fstpt     176(%rsp)
        je        ..B2.144
..B2.143:
        fldt      112(%rsp)
        fstpt     144(%rsp)
..B2.293:
        fldcw     194(%rsp)
..B2.144:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.145:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.146:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        movl      $1, (%rbp)
        lea       272(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_lgammal.299:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.300:
..B2.294:
        fstp      %st(0)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.147:
        fldt      336(%rsp)
        lea       _TWO_32H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       560+_ldbA(%rip), %rdx
        fldt      240(%rsp)
        fldl      (%rax)
        lea       80+_dbA(%rip), %rax
        fmul      %st, %st(4)
        fxch      %st(3)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fsubr     %st, %st(1)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      .L_2il0floatpacket.6(%rip)
        fmul      %st(4), %st
        fldt      .L_2il0floatpacket.6(%rip)
        fldt      256(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fsubrp    %st, %st(6)
        fxch      %st(2)
        fmul      %st(5), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(5)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fmul      %st, %st(3)
        fsubr     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.7(%rip)
        fcomip    %st(2), %st
        jbe       ..B2.149
..B2.148:
        lea       704+_ldbA(%rip), %rdx
        lea       96+_dbA(%rip), %rax
..B2.149:
        fldt      48(%rsp)
        testl     %r12d, %r12d
        fstpt     48(%rsp)
        fstpt     80(%rsp)
        fldt      32(%rsp)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fldt      64(%rsp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.1(%rip)
        fdiv      %st, %st(3)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fadd      %st(4), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fmul      %st, %st(6)
        fld       %st(5)
        fmul      %st(5), %st
        faddp     %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st(0), %st
        fldt      128(%rdx)
        fmul      %st(1), %st
        fldt      112(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      96(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      80(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      64(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      32(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      16(%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       120+_dbA(%rip), %rdx
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st, %st(5)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fxch      %st(1)
        fsub      %st(4), %st
        fld       %st(3)
        fmul      %st(5), %st
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(2)
        faddl     8(%rax)
        lea       112+_dbA(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     256(%rsp)
        fldt      256(%rsp)
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      48(%rsp)
        fxch      %st(1)
        faddl     (%rdx)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      80(%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     48(%rsp)
        je        ..B2.152
..B2.150:
        movl      %r13d, %edi
        fstpt     176(%rsp)
..___tag_value___libm_lgammal.302:
        call      fesetround@PLT
..___tag_value___libm_lgammal.303:
..B2.151:
        fldt      176(%rsp)
..B2.152:
        fldt      48(%rsp)
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.154
..B2.153:
        fldt      32(%rsp)
        fstpt     160(%rsp)
        fldt      (%rsp)
        fstpt     144(%rsp)
        fldt      16(%rsp)
        fstpt     128(%rsp)
..B2.295:
        fldcw     194(%rsp)
..B2.154:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.155:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.156:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        movl      $1, (%rbp)
        lea       272(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_lgammal.325:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.326:
..B2.296:
        fstp      %st(0)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.157:
        fldt      336(%rsp)
        lea       16+_TWO5600(%rip), %rax
        fldt      240(%rsp)
        lea       _TWO_32H(%rip), %rdx
        fldt      .L_2il0floatpacket.1(%rip)
        testl     %r12d, %r12d
        fsubrp    %st, %st(1)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fldt      (%rax)
        fmulp     %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fldl      (%rdx)
        fld       %st(0)
        fmul      %st(2), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fsubr     %st(2), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(3)
        fadd      %st(1), %st
        fld       %st(0)
        fsubp     %st, %st(2)
        fld       %st(1)
        fmul      %st(6), %st
        fstpt     64(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fmulp     %st, %st(2)
        fxch      %st(4)
        fstpt     48(%rsp)
        fldt      256(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        je        ..B2.159
..B2.158:
        movl      %r13d, %edi
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_lgammal.328:
        call      fesetround@PLT
..___tag_value___libm_lgammal.329:
..B2.304:
        fldt      16(%rsp)
        fldt      (%rsp)
..B2.159:
        fldt      64(%rsp)
        lea       _TWO5600(%rip), %rax
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.314
..B2.160:
        fldt      48(%rsp)
        fstpt     160(%rsp)
        fstpt     144(%rsp)
        fldt      32(%rsp)
        fstpt     128(%rsp)
..B2.297:
        fldcw     194(%rsp)
        jmp       ..B2.161
..B2.314:
        fstp      %st(0)
..B2.161:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.162:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.163:
        fldt      .L_2il0floatpacket.3(%rip)
        fcomip    %st(2), %st
        jbe       ..B2.165
..B2.164:
        movl      $-1, (%rbp)
        jmp       ..B2.166
..B2.165:
        movl      $1, (%rbp)
..B2.166:
        movss     %xmm0, (%rsp)
        flds      (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        fmulp     %st, %st(2)
        lea       272(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     80(%rsp)
..___tag_value___libm_lgammal.351:
        call      __libm_logl_k80@PLT
..___tag_value___libm_lgammal.352:
..B2.298:
        fstp      %st(0)
        fldt      80(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B2.167:
        cmpl      $16313, %ebx
        jge       ..B2.174
..B2.168:
        fldt      240(%rsp)
        testl     %r12d, %r12d
        fldt      256(%rsp)
        faddp     %st, %st(1)
        fchs      
        fstpt     176(%rsp)
        je        ..B2.170
..B2.169:
        movl      %r13d, %edi
        fstpt     48(%rsp)
..___tag_value___libm_lgammal.354:
        call      fesetround@PLT
..___tag_value___libm_lgammal.355:
..B2.310:
        fldt      48(%rsp)
..B2.170:
        testb     %r14b, %r14b
        je        ..B2.315
..B2.171:
        fstpt     80(%rsp)
..B2.299:
        fldcw     194(%rsp)
        jmp       ..B2.172
..B2.315:
        fstp      %st(0)
..B2.172:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.173:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.174:
        lea       288+_ldbA(%rip), %rax
        lea       272+_ldbA(%rip), %rdx
        lea       256+_ldbA(%rip), %rcx
        lea       240+_ldbA(%rip), %rbx
        lea       224+_ldbA(%rip), %rbp
        lea       208+_ldbA(%rip), %rsi
        lea       192+_ldbA(%rip), %r8
        lea       176+_ldbA(%rip), %r9
        fldt      (%rax)
        lea       160+_ldbA(%rip), %r10
        fmul      %st(1), %st
        lea       144+_ldbA(%rip), %r11
        lea       128+_ldbA(%rip), %rax
        testl     %r15d, %r15d
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fldt      (%rdx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rbx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rbp)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rsi)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r8)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r9)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r10)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%r11)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       112+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       96+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       80+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       64+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       48+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      (%rax)
        lea       32+_ldbA(%rip), %rax
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fld       %st(0)
        fldt      (%rax)
        lea       16+_ldbA(%rip), %rax
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      (%rax)
        lea       _ldbA(%rip), %rax
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fld       %st(0)
        fldt      (%rax)
        lea       _TWO_32H(%rip), %rax
        faddp     %st, %st(4)
        fld       %st(3)
        fldl      (%rax)
        lea       32+_dbA(%rip), %rax
        fld       %st(0)
        fmul      %st(6), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(2)
        fsub      %st(4), %st
        fld       %st(5)
        fsub      %st(2), %st
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        fldl      (%rax)
        lea       40+_dbA(%rip), %rax
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     (%rax)
        lea       16+_dbA(%rip), %rax
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(2)
        fmul      %st(6), %st
        fxch      %st(3)
        fmul      %st(7), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        lea       24+_dbA(%rip), %rax
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     (%rax)
        lea       _dbA(%rip), %rax
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st(7), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        lea       8+_dbA(%rip), %rax
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(1)
        faddl     (%rax)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(6), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st(7), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fldt      240(%rsp)
        jl        ..B2.220
..B2.175:
        fstp      %st(2)
        fstp      %st(2)
        fldt      256(%rsp)
        fld       %st(2)
        testl     %r12d, %r12d
        fsub      %st(2), %st
        fadd      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fxch      %st(2)
        je        ..B2.178
..B2.176:
        movl      %r13d, %edi
        fstpt     176(%rsp)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
..___tag_value___libm_lgammal.376:
        call      fesetround@PLT
..___tag_value___libm_lgammal.377:
..B2.177:
        fldt      32(%rsp)
        fldt      16(%rsp)
        fldt      176(%rsp)
..B2.178:
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.316
..B2.179:
        fldt      48(%rsp)
        fstpt     144(%rsp)
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fstpt     128(%rsp)
..B2.300:
        fldcw     194(%rsp)
        jmp       ..B2.180
..B2.316:
        fstp      %st(0)
..B2.180:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
..B2.181:
        addq      $280, %rsp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.182:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B2.188:
        fcomi     %st(2), %st
        ja        ..B2.71
        jmp       ..B2.73
..B2.191:
        fstp      %st(0)
        fstp      %st(1)
        fstp      %st(0)
        movl      $1, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.193
..B2.192:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.398:
        call      fesetround@PLT
..___tag_value___libm_lgammal.399:
..B2.193:
        movss     .L_2il0floatpacket.22(%rip), %xmm1
        testb     %r14b, %r14b
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, (%rsp)
        flds      (%rsp)
        fstpt     176(%rsp)
        je        ..B2.195
..B2.194:
        fldcw     194(%rsp)
..B2.195:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.200:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.8(%rip)
        lea       304+_dbA(%rip), %rsi
        movl      $-1, (%rbp)
        lea       2672+_ldbA(%rip), %rdi
        faddp     %st, %st(1)
        jmp       ..B2.106
..B2.202:
        fxch      %st(2)
        fcomip    %st(1), %st
        jae       ..B2.204
        jp        ..B2.204
..B2.203:
        movl      $-1, (%rbp)
        jmp       ..B2.122
..B2.204:
        movl      $1, (%rbp)
        jmp       ..B2.122
..B2.206:
        fcomip    %st(2), %st
        jae       ..B2.135
        jp        ..B2.135
        jmp       ..B2.134
..B2.209:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(0)
        movl      $1, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.211
..B2.210:
        movl      %r13d, %edi
        fstpt     (%rsp)
..___tag_value___libm_lgammal.400:
        call      fesetround@PLT
..___tag_value___libm_lgammal.401:
..B2.306:
        fldt      (%rsp)
..B2.211:
        movss     .L_2il0floatpacket.22(%rip), %xmm1
        testb     %r14b, %r14b
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, (%rsp)
        flds      (%rsp)
        fstpt     176(%rsp)
        je        ..B2.317
..B2.212:
        fstpt     96(%rsp)
..B2.301:
        fldcw     194(%rsp)
        jmp       ..B2.213
..B2.317:
        fstp      %st(0)
..B2.213:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.220:
        lea       544+_ldbA(%rip), %rcx
        lea       528+_ldbA(%rip), %rbx
        fxch      %st(5)
        fstpt     (%rsp)
        fld       %st(2)
        lea       512+_ldbA(%rip), %rbp
        lea       496+_ldbA(%rip), %rsi
        lea       480+_ldbA(%rip), %r8
        lea       464+_ldbA(%rip), %r9
        lea       448+_ldbA(%rip), %r10
        lea       432+_ldbA(%rip), %r11
        lea       416+_ldbA(%rip), %r15
        lea       304+_ldbA(%rip), %rax
        lea       48+_dbA(%rip), %rdx
        testl     %r12d, %r12d
        fsub      %st(4), %st
        fxch      %st(3)
        fmul      %st(0), %st
        fxch      %st(5)
        fstpt     16(%rsp)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldt      (%rcx)
        lea       400+_ldbA(%rip), %rcx
        fmul      %st(5), %st
        fldt      (%rbx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rbp)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%r15)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       384+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       368+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       352+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       336+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        lea       320+_ldbA(%rip), %rcx
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      (%rax)
        lea       64+_dbA(%rip), %rax
        faddp     %st, %st(5)
        fld       %st(4)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.2(%rip)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(5)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fxch      %st(3)
        fsub      %st(5), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(7), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(6)
        fmul      %st(1), %st
        fld       %st(4)
        fmul      %st(7), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fldl      (%rax)
        fld       %st(0)
        lea       72+_dbA(%rip), %rax
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(1)
        faddl     (%rax)
        lea       56+_dbA(%rip), %rax
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(6), %st
        fld       %st(4)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(2)
        fldl      (%rdx)
        fld       %st(0)
        fadd      %st(2), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddl     (%rax)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      16(%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fchs      
        fadd      %st, %st(1)
        fxch      %st(1)
        fchs      
        fsubp     %st, %st(3)
        fldt      256(%rsp)
        fsubrp    %st, %st(3)
        fldt      (%rsp)
        fsubrp    %st, %st(3)
        fld       %st(0)
        fsub      %st(4), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fchs      
        fsubp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fldt      32(%rsp)
        fxch      %st(2)
        je        ..B2.222
..B2.221:
        movl      %r13d, %edi
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
..___tag_value___libm_lgammal.402:
        call      fesetround@PLT
..___tag_value___libm_lgammal.403:
..B2.308:
        fldt      32(%rsp)
        fldt      16(%rsp)
        fldt      (%rsp)
..B2.222:
        testb     %r14b, %r14b
        faddp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.318
..B2.223:
        fldt      48(%rsp)
        fstpt     144(%rsp)
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fstpt     128(%rsp)
..B2.302:
        fldcw     194(%rsp)
        jmp       ..B2.224
..B2.318:
        fstp      %st(0)
..B2.224:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.227:
        cmpl      $16446, %ebx
        jle       ..B2.16
..B2.228:
        fstp      %st(0)
        fstp      %st(0)
        movl      $1, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.230
..B2.229:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.404:
        call      fesetround@PLT
..___tag_value___libm_lgammal.405:
..B2.230:
        movss     .L_2il0floatpacket.22(%rip), %xmm1
        testb     %r14b, %r14b
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        movss     %xmm1, (%rsp)
        flds      (%rsp)
        fstpt     176(%rsp)
        je        ..B2.232
..B2.231:
        fldcw     194(%rsp)
..B2.232:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.235:
        fstp      %st(1)
        movl      $1, (%rbp)
..B2.236:
        testl     %r12d, %r12d
        je        ..B2.238
..B2.237:
        movl      %r13d, %edi
        fstp      %st(0)
..___tag_value___libm_lgammal.406:
        call      fesetround@PLT
..___tag_value___libm_lgammal.407:
..B2.309:
        fldt      .L_2il0floatpacket.0(%rip)
..B2.238:
        fstpt     176(%rsp)
        testb     %r14b, %r14b
        je        ..B2.240
..B2.239:
        fldcw     194(%rsp)
..B2.240:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.244:
        fstp      %st(0)
        movl      $1, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.247
..B2.245:
        fstp      %st(0)
        movl      %r13d, %edi
..___tag_value___libm_lgammal.408:
        call      fesetround@PLT
..___tag_value___libm_lgammal.409:
..B2.246:
        fldt      336(%rsp)
..B2.247:
        fld       %st(0)
        testb     %r14b, %r14b
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     176(%rsp)
        je        ..B2.249
..B2.248:
        fldcw     194(%rsp)
..B2.249:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.252:
        fstp      %st(0)
        fstp      %st(0)
        testl     %r12d, %r12d
        je        ..B2.254
..B2.253:
        movl      %r13d, %edi
        movl      $1, (%rbp)
..___tag_value___libm_lgammal.410:
        call      fesetround@PLT
..___tag_value___libm_lgammal.411:
        movzbl    345(%rsp), %ecx
..B2.254:
        movss     .L_2il0floatpacket.22(%rip), %xmm1
        andl      $128, %ecx
        pxor      %xmm0, %xmm0
        divss     %xmm0, %xmm1
        shrl      $7, %ecx
        addl      %ecx, %ecx
        movss     %xmm1, (%rsp)
        negl      %ecx
        flds      (%rsp)
        incl      %ecx
        fstpt     176(%rsp)
        testb     %r14b, %r14b
        movl      %ecx, (%rbp)
        je        ..B2.256
..B2.255:
        fldcw     194(%rsp)
..B2.256:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.259:
        movq      $0x8000000000000000, %rax
        cmpq      336(%rsp), %rax
        je        ..B2.270
..B2.260:
        movl      $0, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.262
..B2.261:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.412:
        call      fesetround@PLT
..___tag_value___libm_lgammal.413:
..B2.262:
        fldt      336(%rsp)
        fadd      %st(0), %st
        fstpt     176(%rsp)
..B2.263:
        testl     %r12d, %r12d
        je        ..B2.265
..B2.264:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.414:
        call      fesetround@PLT
..___tag_value___libm_lgammal.415:
..B2.265:
        testb     %r14b, %r14b
        je        ..B2.267
..B2.266:
        fldcw     194(%rsp)
..B2.267:
        fldt      176(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.182
        jmp       ..B2.36
..B2.270:
        movl      $1, (%rbp)
        testl     %r12d, %r12d
        je        ..B2.272
..B2.271:
        movl      %r13d, %edi
..___tag_value___libm_lgammal.416:
        call      fesetround@PLT
..___tag_value___libm_lgammal.417:
..B2.272:
        fldt      336(%rsp)
        fmul      %st(0), %st
        fstpt     176(%rsp)
        jmp       ..B2.263
        .align    16,0x90
	.cfi_endproc
	.type	__libm_lgammal,@function
	.size	__libm_lgammal,.-__libm_lgammal
	.data
# -- End  __libm_lgammal
	.text
# -- Begin  __libm_p25
	.text
       .align    16,0x90
__libm_p25:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
# parameter 3: %rsi
# parameter 4: %rdx
..B3.1:
	.cfi_startproc
..___tag_value___libm_p25.419:
..L420:

        lea       _TWO_32H(%rip), %rax
        fldt      8(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldl      56(%rsi)
        fmul      %st(3), %st
        fldl      (%rax)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(3)
        fsub      %st(3), %st
        fldt      64(%rdi)
        fxch      %st(2)
        faddl     48(%rsi)
        fmul      %st(5), %st
        fldt      (%rdi)
        fldt      80(%rdi)
        fxch      %st(2)
        faddl     152(%rsi)
        fmul      %st(7), %st
        faddl     144(%rsi)
        fmul      %st(7), %st
        faddl     168(%rsi)
        fmul      %st(7), %st
        faddl     160(%rsi)
        fmul      %st(7), %st
        faddl     104(%rsi)
        fmul      %st(7), %st
        faddl     96(%rsi)
        fmul      %st(7), %st
        faddl     120(%rsi)
        fmul      %st(7), %st
        faddl     112(%rsi)
        fmul      %st(7), %st
        faddl     136(%rsi)
        fmul      %st(7), %st
        faddl     128(%rsi)
        fmul      %st(7), %st
        faddl     40(%rsi)
        fmul      %st(7), %st
        faddl     32(%rsi)
        fmul      %st(7), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      16(%rdi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      32(%rdi)
        fldt      96(%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      112(%rdi)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fld       %st(2)
        fldt      48(%rdi)
        faddp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fldl      (%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     88(%rsi)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldl      64(%rsi)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     72(%rsi)
        faddp     %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(5), %st
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldl      16(%rsi)
        fld       %st(0)
        fadd      %st(3), %st
        fmul      %st, %st(5)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fxch      %st(4)
        fmul      %st, %st(5)
        fsubr     %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(3)
        faddl     24(%rsi)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      80(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rdx)
        fldt      (%rdx)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        faddl     8(%rsi)
        faddp     %st, %st(1)
        fstpt     16(%rdx)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_p25,@function
	.size	__libm_p25,.-__libm_p25
	.data
# -- End  __libm_p25
	.text
# -- Begin  __libm_p19root
	.text
       .align    16,0x90
__libm_p19root:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
# parameter 3: %rsi
# parameter 4: %rdx
..B4.1:
	.cfi_startproc
..___tag_value___libm_p19root.422:
..L423:

        lea       _TWO_32H(%rip), %rax
        fldt      8(%rsp)
        lea       ones(%rip), %r10
        fld       %st(0)
        fld       %st(1)
        fldl      152(%rsi)
        fmul      %st(3), %st
        fldl      (%rax)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(3)
        fsub      %st(3), %st
        fldt      32(%rdi)
        fxch      %st(2)
        faddl     144(%rsi)
        fmul      %st(5), %st
        fldt      16(%rdi)
        fldt      (%rdi)
        fxch      %st(2)
        faddl     136(%rsi)
        fmul      %st(7), %st
        faddl     128(%rsi)
        fmul      %st(7), %st
        faddl     120(%rsi)
        fmul      %st(7), %st
        faddl     112(%rsi)
        fmul      %st(7), %st
        faddl     104(%rsi)
        fmul      %st(7), %st
        faddl     96(%rsi)
        fmul      %st(7), %st
        faddl     88(%rsi)
        fmul      %st(7), %st
        faddl     80(%rsi)
        fmul      %st(7), %st
        faddl     72(%rsi)
        fmul      %st(7), %st
        faddl     64(%rsi)
        fmul      %st(7), %st
        faddl     56(%rsi)
        fmul      %st(7), %st
        faddl     48(%rsi)
        fmul      %st(7), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      32(%rsi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(4)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40(%rsi)
        faddp     %st, %st(4)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(4)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      16(%rsi)
        fld       %st(0)
        fadd      %st(3), %st
        fmul      %st, %st(5)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(2)
        fsub      %st(4), %st
        fxch      %st(4)
        fmulp     %st, %st(5)
        faddl     24(%rsi)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fldl      (%rsi)
        fld       %st(0)
        fldl      8(%rsi)
        movb      -31(%rsp), %cl
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        andb      $-128, %cl
        movb      -15(%rsp), %r9b
        andb      $-128, %r9b
        shrb      $7, %r9b
        shrb      $7, %cl
        movzbl    %r9b, %r11d
        movzbl    %cl, %r8d
        flds      (%r10,%r11,4)
        fmulp     %st, %st(1)
        fld       %st(2)
        fadd      %st(5), %st
        fstpt     (%rdx)
        fldt      (%rdx)
        flds      (%r10,%r8,4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fcomi     %st(1), %st
        fxch      %st(1)
        fstp      %st(0)
        fld       %st(3)
        fsubr     %st(5), %st
        fadd      %st(3), %st
        fadd      %st(6), %st
        fadd      %st(2), %st
        fxch      %st(1)
        fstpt     -40(%rsp)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fcmovnbe  %st(1), %st
        fstp      %st(1)
        fstpt     16(%rdx)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_p19root,@function
	.size	__libm_p19root,.-__libm_p19root
	.data
# -- End  __libm_p19root
	.text
# -- Begin  gammal
	.text
       .align    16,0x90
	.globl gammal
gammal:
# parameter 1: 16 + %rsp
..B5.1:
	.cfi_startproc
..___tag_value_gammal.425:
..L426:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        fldt      16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 32
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
..___tag_value_gammal.429:
        call      __libm_lgammal
..___tag_value_gammal.430:
..B5.2:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	gammal,@function
	.size	gammal,.-gammal
	.data
# -- End  gammal
	.text
# -- Begin  lgammal_r
	.text
       .align    16,0x90
	.globl lgammal_r
lgammal_r:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
..B6.1:
	.cfi_startproc
..___tag_value_lgammal_r.433:
..L434:

        fldt      8(%rsp)
        fstpt     8(%rsp)
        jmp       __libm_lgammal
        .align    16,0x90
	.cfi_endproc
	.type	lgammal_r,@function
	.size	lgammal_r,.-lgammal_r
	.data
# -- End  lgammal_r
	.text
# -- Begin  gammal_r
	.text
       .align    16,0x90
	.globl gammal_r
gammal_r:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
..B7.1:
	.cfi_startproc
..___tag_value_gammal_r.436:
..L437:

        fldt      8(%rsp)
        fstpt     8(%rsp)
        jmp       __libm_lgammal
        .align    16,0x90
	.cfi_endproc
	.type	gammal_r,@function
	.size	gammal_r,.-gammal_r
	.data
# -- End  gammal_r
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x01,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x01,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xd0,0x02,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xa0,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x9c,0x03,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xb0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc8,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,16
	.align 16
.L_2il0floatpacket.12:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x90,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,16
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,16
	.align 16
.L_2il0floatpacket.14:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x70,0xa6,0x00,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,16
	.align 16
.L_2il0floatpacket.15:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xa0,0x00,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.16:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 16
.L_2il0floatpacket.17:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x81,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,16
	.align 16
.L_2il0floatpacket.18:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xfe,0xfe,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,16
	.align 16
.L_2il0floatpacket.19:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,16
	.align 16
.L_2il0floatpacket.20:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,16
	.align 16
.L_2il0floatpacket.21:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x00,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.21,@object
	.size	.L_2il0floatpacket.21,16
	.align 16
_ldbA:
	.word	48403
	.word	16065
	.word	37206
	.word	35465
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	32498
	.word	21186
	.word	57533
	.word	54364
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	48455
	.word	64043
	.word	25991
	.word	44448
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	36906
	.word	8596
	.word	53485
	.word	37761
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	16786
	.word	63789
	.word	39740
	.word	32901
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	43334
	.word	50722
	.word	13591
	.word	58371
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	51242
	.word	42271
	.word	52839
	.word	52480
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	22272
	.word	27707
	.word	58025
	.word	47684
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	48537
	.word	42093
	.word	143
	.word	43693
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	5437
	.word	23105
	.word	19142
	.word	40288
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	43105
	.word	25781
	.word	60600
	.word	37244
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	64996
	.word	46896
	.word	33038
	.word	34212
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	8055
	.word	35794
	.word	25027
	.word	61223
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	26586
	.word	4739
	.word	1082
	.word	51475
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	65450
	.word	65402
	.word	39793
	.word	38562
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	18875
	.word	24550
	.word	64026
	.word	48031
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	3811
	.word	11690
	.word	33485
	.word	45433
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	21488
	.word	64404
	.word	56511
	.word	56861
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	64157
	.word	44249
	.word	62935
	.word	34811
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	1503
	.word	1560
	.word	25992
	.word	44448
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	53495
	.word	49976
	.word	39767
	.word	32901
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	21693
	.word	55175
	.word	61890
	.word	52480
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	18005
	.word	14879
	.word	27421
	.word	43701
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	4785
	.word	48109
	.word	28463
	.word	37451
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	17404
	.word	30303
	.word	36440
	.word	32768
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	19413
	.word	44833
	.word	30216
	.word	58252
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	61231
	.word	61633
	.word	10791
	.word	52455
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	18500
	.word	21868
	.word	57423
	.word	47400
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	24302
	.word	39700
	.word	47161
	.word	45652
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	48514
	.word	20900
	.word	46081
	.word	58518
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	11885
	.word	12460
	.word	35625
	.word	41968
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	41690
	.word	14982
	.word	21018
	.word	53469
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	2894
	.word	27339
	.word	226
	.word	41728
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	26025
	.word	18501
	.word	20978
	.word	46979
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	53900
	.word	55553
	.word	17653
	.word	38916
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	46603
	.word	2912
	.word	24758
	.word	46603
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	53261
	.word	3328
	.word	208
	.word	53261
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	39946
	.word	2496
	.word	49308
	.word	39945
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	6840
	.word	51193
	.word	61784
	.word	56488
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	58384
	.word	51683
	.word	34508
	.word	64341
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	53773
	.word	3360
	.word	8402
	.word	53773
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	48865
	.word	31388
	.word	13912
	.word	61972
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	65489
	.word	61491
	.word	45504
	.word	47092
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	53102
	.word	49401
	.word	14344
	.word	45627
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	2166
	.word	2831
	.word	24758
	.word	46603
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	9580
	.word	21681
	.word	206
	.word	53261
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	30177
	.word	46479
	.word	48966
	.word	39945
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	52333
	.word	51482
	.word	18491
	.word	56488
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	40942
	.word	14796
	.word	26057
	.word	64313
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	50177
	.word	16139
	.word	15070
	.word	53362
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	2931
	.word	59423
	.word	29748
	.word	57837
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	12931
	.word	12185
	.word	50840
	.word	32873
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	60991
	.word	64091
	.word	63067
	.word	49777
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	24747
	.word	1987
	.word	58544
	.word	40255
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	31726
	.word	62157
	.word	28132
	.word	51504
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	1560
	.word	52396
	.word	29634
	.word	33090
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	51301
	.word	34230
	.word	21183
	.word	41027
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	26476
	.word	19428
	.word	22828
	.word	49163
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	62911
	.word	28656
	.word	40943
	.word	57345
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	51702
	.word	17823
	.word	6657
	.word	32768
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	55405
	.word	47943
	.word	739
	.word	36864
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	47506
	.word	63803
	.word	73
	.word	40960
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	21270
	.word	47505
	.word	6
	.word	45056
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	51059
	.word	36726
	.word	0
	.word	49152
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	8970
	.word	2825
	.word	0
	.word	53248
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	52133
	.word	201
	.word	0
	.word	57344
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	29690
	.word	13
	.word	0
	.word	61440
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	27552
	.word	0
	.word	0
	.word	32768
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	1621
	.word	0
	.word	0
	.word	34816
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	90
	.word	0
	.word	0
	.word	36864
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	5
	.word	0
	.word	0
	.word	38912
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	1630
	.word	57032
	.word	2128
	.word	45018
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	10332
	.word	57727
	.word	35491
	.word	64803
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	59042
	.word	30551
	.word	48083
	.word	40890
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	27388
	.word	36768
	.word	38828
	.word	49140
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	32744
	.word	23607
	.word	24507
	.word	57342
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	30841
	.word	2766
	.word	52220
	.word	65535
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	33024
	.word	16990
	.word	64796
	.word	36863
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	64973
	.word	1725
	.word	65462
	.word	40959
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	40731
	.word	18030
	.word	65529
	.word	45055
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	14452
	.word	28809
	.word	65535
	.word	49151
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	56566
	.word	62710
	.word	65535
	.word	53247
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	13403
	.word	65334
	.word	65535
	.word	57343
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	35846
	.word	65522
	.word	65535
	.word	61439
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	10432
	.word	65535
	.word	65535
	.word	65535
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	63915
	.word	65535
	.word	65535
	.word	34815
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65446
	.word	65535
	.word	65535
	.word	36863
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65531
	.word	65535
	.word	65535
	.word	38911
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	38912
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	47937
	.word	50671
	.word	32821
	.word	51896
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	8469
	.word	62640
	.word	24113
	.word	51168
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	23666
	.word	39283
	.word	12728
	.word	33144
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	1338
	.word	16824
	.word	25401
	.word	33044
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	38006
	.word	50858
	.word	35636
	.word	41038
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	6523
	.word	10616
	.word	46244
	.word	41017
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	30889
	.word	22666
	.word	15710
	.word	49165
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	22694
	.word	63464
	.word	47653
	.word	49161
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	7860
	.word	665
	.word	58690
	.word	57345
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	35199
	.word	5838
	.word	25732
	.word	57345
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	27551
	.word	53562
	.word	7766
	.word	32768
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	34954
	.word	15277
	.word	5706
	.word	32768
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	10026
	.word	1321
	.word	863
	.word	36864
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	38128
	.word	3645
	.word	634
	.word	36864
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	22656
	.word	19824
	.word	86
	.word	40960
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	3271
	.word	26602
	.word	63
	.word	40960
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	57583
	.word	55422
	.word	7
	.word	45056
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	8869
	.word	50081
	.word	5
	.word	45056
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	59569
	.word	42847
	.word	0
	.word	49152
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	6316
	.word	31480
	.word	0
	.word	49152
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	65078
	.word	3295
	.word	0
	.word	53248
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	35775
	.word	2421
	.word	0
	.word	53248
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	28054
	.word	235
	.word	0
	.word	57344
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	63410
	.word	172
	.word	0
	.word	57344
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	45561
	.word	15
	.word	0
	.word	61440
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	34811
	.word	11
	.word	0
	.word	61440
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	32144
	.word	0
	.word	0
	.word	32768
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	23616
	.word	0
	.word	0
	.word	32768
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	1891
	.word	0
	.word	0
	.word	34816
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	1389
	.word	0
	.word	0
	.word	34816
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	105
	.word	0
	.word	0
	.word	36864
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	77
	.word	0
	.word	0
	.word	36864
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	6
	.word	0
	.word	0
	.word	38912
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	5
	.word	0
	.word	0
	.word	38912
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	30094
	.word	17612
	.word	34825
	.word	45621
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	27787
	.word	64487
	.word	61865
	.word	44498
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	45180
	.word	62215
	.word	30788
	.word	64910
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	56909
	.word	5419
	.word	21954
	.word	64678
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	26983
	.word	61012
	.word	55414
	.word	40900
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	17042
	.word	26812
	.word	58543
	.word	40878
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	17649
	.word	49100
	.word	16895
	.word	49142
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	47753
	.word	6644
	.word	42105
	.word	49138
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	56665
	.word	15855
	.word	40064
	.word	57342
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	9214
	.word	11959
	.word	6291
	.word	57342
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	44995
	.word	64071
	.word	54163
	.word	65535
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	35553
	.word	53091
	.word	49943
	.word	65535
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	39205
	.word	16423
	.word	64904
	.word	36863
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	61074
	.word	52942
	.word	64669
	.word	36863
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	11000
	.word	54094
	.word	65472
	.word	40959
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	41911
	.word	24986
	.word	65449
	.word	40959
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	33350
	.word	16833
	.word	65530
	.word	45055
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	41532
	.word	8229
	.word	65528
	.word	45055
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	52046
	.word	34170
	.word	65535
	.word	49151
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	9080
	.word	22531
	.word	65535
	.word	49151
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	19128
	.word	63123
	.word	65535
	.word	53247
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	61194
	.word	62227
	.word	65535
	.word	53247
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	43497
	.word	65363
	.word	65535
	.word	57343
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	46501
	.word	65299
	.word	65535
	.word	57343
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	33483
	.word	65524
	.word	65535
	.word	61439
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	16207
	.word	65520
	.word	65535
	.word	61439
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	18477
	.word	65535
	.word	65535
	.word	65535
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	1013
	.word	65535
	.word	65535
	.word	65535
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	64152
	.word	65535
	.word	65535
	.word	34815
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	63638
	.word	65535
	.word	65535
	.word	34815
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65459
	.word	65535
	.word	65535
	.word	36863
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65431
	.word	65535
	.word	65535
	.word	36863
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65532
	.word	65535
	.word	65535
	.word	38911
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	65531
	.word	65535
	.word	65535
	.word	38911
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	16368
	.word	23276
	.word	14028
	.word	45039
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	59281
	.word	7220
	.word	1358
	.word	44002
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	209
	.word	31529
	.word	17251
	.word	45971
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	22774
	.word	54591
	.word	46864
	.word	55119
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	36309
	.word	6992
	.word	45734
	.word	65508
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	57509
	.word	44567
	.word	44472
	.word	62561
	.word	49153
	.word	0
	.word	0
	.word	0
	.word	24352
	.word	36914
	.word	35595
	.word	62910
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	36298
	.word	16311
	.word	10079
	.word	34683
	.word	49152
	.word	0
	.word	0
	.word	0
	.word	8745
	.word	24487
	.word	32401
	.word	58540
	.word	16393
	.word	0
	.word	0
	.word	0
	.word	64710
	.word	25493
	.word	17440
	.word	41602
	.word	16391
	.word	0
	.word	0
	.word	0
	.word	9540
	.word	49275
	.word	24345
	.word	64260
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	32123
	.word	43450
	.word	42726
	.word	58824
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	56179
	.word	18517
	.word	55467
	.word	35137
	.word	49163
	.word	0
	.word	0
	.word	0
	.word	59411
	.word	7122
	.word	11027
	.word	48002
	.word	49160
	.word	0
	.word	0
	.word	0
	.word	11599
	.word	47165
	.word	19459
	.word	34091
	.word	49158
	.word	0
	.word	0
	.word	0
	.word	3871
	.word	11740
	.word	51838
	.word	51028
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	54329
	.word	49239
	.word	6514
	.word	47686
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	32133
	.word	56
	.word	14391
	.word	58371
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	29265
	.word	29887
	.word	53486
	.word	37761
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	40734
	.word	56364
	.word	61890
	.word	52480
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	62017
	.word	50151
	.word	39767
	.word	32901
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	62465
	.word	1557
	.word	25992
	.word	44448
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	46350
	.word	21258
	.word	57533
	.word	54364
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	28472
	.word	57318
	.word	11755
	.word	48239
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	64030
	.word	27012
	.word	42555
	.word	59902
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	15295
	.word	64624
	.word	11797
	.word	40022
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	40088
	.word	48672
	.word	55416
	.word	53395
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	32150
	.word	7351
	.word	22467
	.word	34203
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	32397
	.word	56161
	.word	46934
	.word	48494
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	35896
	.word	51119
	.word	32272
	.word	61954
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	64369
	.word	46577
	.word	49946
	.word	47894
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	13507
	.word	11243
	.word	36867
	.word	37912
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	13500
	.word	7397
	.word	345
	.word	51175
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	36308
	.word	1299
	.word	15549
	.word	37687
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	1494
	.word	43036
	.word	5286
	.word	33882
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	51612
	.word	44256
	.word	49203
	.word	47051
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	24103
	.word	45476
	.word	53751
	.word	61816
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	18204
	.word	4207
	.word	64680
	.word	43260
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	39634
	.word	35210
	.word	18170
	.word	34381
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	30073
	.word	2874
	.word	60159
	.word	61890
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	21923
	.word	37483
	.word	63587
	.word	45930
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	17610
	.word	34235
	.word	25953
	.word	38432
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	27391
	.word	45200
	.word	21903
	.word	41572
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	8472
	.word	18376
	.word	37439
	.word	36904
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	15138
	.word	28105
	.word	43771
	.word	52749
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	19597
	.word	13484
	.word	38971
	.word	41168
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	3303
	.word	60299
	.word	50447
	.word	38102
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	47075
	.word	52558
	.word	55475
	.word	37762
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	52173
	.word	35461
	.word	11437
	.word	59897
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	36076
	.word	14373
	.word	35326
	.word	54659
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	59562
	.word	13882
	.word	310
	.word	33073
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	21162
	.word	13390
	.word	51910
	.word	43836
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	25864
	.word	5553
	.word	52105
	.word	33196
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	50214
	.word	4354
	.word	50978
	.word	55906
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	22538
	.word	16515
	.word	54348
	.word	57115
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	25669
	.word	8964
	.word	45428
	.word	52429
	.word	16390
	.word	0
	.word	0
	.word	0
	.word	42066
	.word	12451
	.word	5346
	.word	32773
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	59247
	.word	49506
	.word	42126
	.word	43753
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	41212
	.word	16190
	.word	63627
	.word	33236
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	43030
	.word	44942
	.word	52710
	.word	59310
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	4241
	.word	60350
	.word	9519
	.word	55057
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	13201
	.word	63684
	.word	48400
	.word	58952
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	43405
	.word	43520
	.word	7800
	.word	38026
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	51109
	.word	32176
	.word	26595
	.word	37828
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	39193
	.word	38974
	.word	13074
	.word	53901
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	18403
	.word	21914
	.word	43743
	.word	52518
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	11905
	.word	16066
	.word	37206
	.word	35465
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	39192
	.word	38974
	.word	13074
	.word	53901
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	16822
	.word	16066
	.word	37206
	.word	35465
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	12453
	.word	1560
	.word	25992
	.word	44448
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	46918
	.word	49948
	.word	39767
	.word	32901
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	993
	.word	37711
	.word	1229
	.word	53967
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	22241
	.word	58002
	.word	54887
	.word	56142
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	8639
	.word	23392
	.word	41786
	.word	61781
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	824
	.word	14446
	.word	39837
	.word	35166
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	4398
	.word	47415
	.word	48788
	.word	40961
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	53320
	.word	18702
	.word	33902
	.word	48514
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	47965
	.word	49933
	.word	53838
	.word	58200
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	46987
	.word	9286
	.word	62309
	.word	35268
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	6461
	.word	640
	.word	17377
	.word	43104
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	30338
	.word	25067
	.word	12218
	.word	53050
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	3327
	.word	27408
	.word	8301
	.word	61564
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	59387
	.word	42287
	.word	60059
	.word	60716
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	13826
	.word	21215
	.word	7405
	.word	64741
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	26137
	.word	29234
	.word	53880
	.word	36165
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	10231
	.word	52926
	.word	36203
	.word	41655
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	23407
	.word	27273
	.word	49305
	.word	49005
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	11641
	.word	23288
	.word	44339
	.word	58553
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	25249
	.word	6483
	.word	6272
	.word	35397
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	42921
	.word	12425
	.word	13798
	.word	43198
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	50188
	.word	64017
	.word	41333
	.word	53119
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	25097
	.word	45200
	.word	21903
	.word	41572
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	10972
	.word	60304
	.word	50447
	.word	38102
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	15639
	.word	33828
	.word	25953
	.word	38432
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	37131
	.word	35786
	.word	38971
	.word	41168
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	11219
	.word	39714
	.word	63573
	.word	45930
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	59109
	.word	9738
	.word	44211
	.word	52749
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	51163
	.word	1079
	.word	49343
	.word	61890
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	19090
	.word	15031
	.word	12079
	.word	36906
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	28236
	.word	6956
	.word	155
	.word	44549
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	54475
	.word	22231
	.word	8036
	.word	54711
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	43885
	.word	13882
	.word	310
	.word	33073
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	49382
	.word	16501
	.word	54348
	.word	57115
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	54884
	.word	14552
	.word	35326
	.word	54659
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	24455
	.word	7515
	.word	50978
	.word	55906
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	57687
	.word	611
	.word	11437
	.word	59897
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	26110
	.word	1729
	.word	52102
	.word	33196
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	49827
	.word	25553
	.word	55506
	.word	37762
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	45356
	.word	51283
	.word	52132
	.word	43836
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	37526
	.word	52421
	.word	6331
	.word	51720
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	23504
	.word	7355
	.word	4713
	.word	61833
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	28030
	.word	45200
	.word	21903
	.word	41572
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	64882
	.word	60298
	.word	50447
	.word	38102
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	36628
	.word	34230
	.word	25953
	.word	38432
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	2186
	.word	13495
	.word	38971
	.word	41168
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	42048
	.word	38474
	.word	63587
	.word	45930
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	44984
	.word	21655
	.word	43771
	.word	52749
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	10340
	.word	31180
	.word	60154
	.word	61890
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	29368
	.word	33685
	.word	37418
	.word	36904
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	632
	.word	28858
	.word	25336
	.word	44574
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	47657
	.word	7722
	.word	28520
	.word	54386
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	48861
	.word	21442
	.word	12332
	.word	46245
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	55836
	.word	1266
	.word	27621
	.word	35724
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	19864
	.word	22843
	.word	65282
	.word	47516
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	55755
	.word	27362
	.word	47250
	.word	57481
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	27703
	.word	47960
	.word	491
	.word	37689
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	41421
	.word	53711
	.word	6590
	.word	52435
	.word	16394
	.word	0
	.word	0
	.word	0
	.word	57789
	.word	21731
	.word	35033
	.word	43725
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	55298
	.word	55134
	.word	50960
	.word	52072
	.word	16399
	.word	0
	.word	0
	.word	0
	.word	30636
	.word	39282
	.word	62891
	.word	33072
	.word	16404
	.word	0
	.word	0
	.word	0
	.word	7959
	.word	64403
	.word	49841
	.word	37534
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	5854
	.word	54131
	.word	61334
	.word	53522
	.word	16408
	.word	0
	.word	0
	.word	0
	.word	5803
	.word	45364
	.word	48027
	.word	40704
	.word	16415
	.word	0
	.word	0
	.word	0
	.word	1370
	.word	7138
	.word	7454
	.word	61224
	.word	16409
	.word	0
	.word	0
	.word	0
	.word	57336
	.word	44117
	.word	55954
	.word	64739
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	54670
	.word	41483
	.word	43064
	.word	36510
	.word	16428
	.word	0
	.word	0
	.word	0
	.word	42312
	.word	7696
	.word	38319
	.word	40746
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	12302
	.word	62594
	.word	5410
	.word	37618
	.word	16430
	.word	0
	.word	0
	.word	0
	.word	9293
	.word	14879
	.word	6832
	.word	37045
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	2958
	.word	50542
	.word	15469
	.word	40704
	.word	16427
	.word	0
	.word	0
	.word	0
	.word	42649
	.word	12819
	.word	63050
	.word	37565
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	6063
	.word	43732
	.word	62723
	.word	36980
	.word	16457
	.word	0
	.word	0
	.word	0
	.word	22453
	.word	18298
	.word	44915
	.word	57947
	.word	16436
	.word	0
	.word	0
	.word	0
	.word	20554
	.word	96
	.word	8145
	.word	60162
	.word	16454
	.word	0
	.word	0
	.word	0
	.word	27743
	.word	60960
	.word	41480
	.word	33312
	.word	16473
	.word	0
	.word	0
	.word	0
	.word	22238
	.word	6527
	.word	38867
	.word	56588
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	38516
	.word	42313
	.word	15095
	.word	36719
	.word	16468
	.word	0
	.word	0
	.word	0
	.word	36587
	.word	28039
	.word	41391
	.word	50829
	.word	16489
	.word	0
	.word	0
	.word	0
	.word	29867
	.word	13204
	.word	65176
	.word	36776
	.word	16457
	.word	0
	.word	0
	.word	0
	.word	31042
	.word	28858
	.word	52725
	.word	32812
	.word	16482
	.word	0
	.word	0
	.word	0
	.word	10384
	.word	4515
	.word	18607
	.word	62455
	.word	16506
	.word	0
	.word	0
	.word	0
	.word	9765
	.word	184
	.word	10999
	.word	62061
	.word	16467
	.word	0
	.word	0
	.word	0
	.word	31509
	.word	19231
	.word	45642
	.word	41528
	.word	16496
	.word	0
	.word	0
	.word	0
	.word	9807
	.word	42010
	.word	46528
	.word	59283
	.word	16524
	.word	0
	.word	0
	.word	0
	.word	34508
	.word	54054
	.word	11762
	.word	33288
	.word	16479
	.word	0
	.word	0
	.word	0
	.word	35651
	.word	17126
	.word	61083
	.word	36196
	.word	16511
	.word	0
	.word	0
	.word	0
	.word	61937
	.word	2245
	.word	55342
	.word	41983
	.word	16543
	.word	0
	.word	0
	.word	0
	.word	55800
	.word	52004
	.word	63053
	.word	44600
	.word	16490
	.word	0
	.word	0
	.word	0
	.word	1794
	.word	6965
	.word	62764
	.word	42435
	.word	16526
	.word	0
	.word	0
	.word	0
	.word	31259
	.word	48342
	.word	51287
	.word	43067
	.word	16562
	.word	0
	.word	0
	.word	0
	.word	51054
	.word	24828
	.word	3408
	.word	36750
	.word	16502
	.word	0
	.word	0
	.word	0
	.word	10851
	.word	65289
	.word	49628
	.word	32780
	.word	16542
	.word	0
	.word	0
	.word	0
	.word	55922
	.word	53580
	.word	2985
	.word	62379
	.word	16581
	.word	0
	.word	0
	.word	0
	.word	3514
	.word	59781
	.word	19773
	.word	36749
	.word	16514
	.word	0
	.word	0
	.word	0
	.word	6233
	.word	41678
	.word	56685
	.word	32779
	.word	16558
	.word	0
	.word	0
	.word	0
	.word	7397
	.word	40156
	.word	60492
	.word	62376
	.word	16601
	.word	0
	.word	0
	.word	0
	.word	64764
	.word	30182
	.word	28471
	.word	44055
	.word	16526
	.word	0
	.word	0
	.word	0
	.word	47394
	.word	43518
	.word	20307
	.word	41745
	.word	16574
	.word	0
	.word	0
	.word	0
	.word	48961
	.word	13130
	.word	26792
	.word	42193
	.word	16622
	.word	0
	.word	0
	.word	0
	.word	13149
	.word	49204
	.word	39329
	.word	62843
	.word	16538
	.word	0
	.word	0
	.word	0
	.word	63753
	.word	8765
	.word	38452
	.word	33516
	.word	16591
	.word	0
	.word	0
	.word	0
	.word	60000
	.word	22498
	.word	28021
	.word	38134
	.word	16643
	.word	0
	.word	0
	.word	0
	.word	10643
	.word	26389
	.word	15903
	.word	44739
	.word	16551
	.word	0
	.word	0
	.word	0
	.word	24943
	.word	57881
	.word	5976
	.word	53687
	.word	16607
	.word	0
	.word	0
	.word	0
	.word	62568
	.word	44908
	.word	48389
	.word	34359
	.word	16664
	.word	0
	.word	0
	.word	0
	.word	36352
	.word	25695
	.word	54808
	.word	41154
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	53950
	.word	20364
	.word	22118
	.word	64130
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	29016
	.word	1013
	.word	8897
	.word	49860
	.word	49158
	.word	0
	.word	0
	.word	0
	.word	48790
	.word	22905
	.word	44266
	.word	59689
	.word	49162
	.word	0
	.word	0
	.word	0
	.word	10097
	.word	41173
	.word	5368
	.word	62588
	.word	16398
	.word	0
	.word	0
	.word	0
	.word	18460
	.word	26935
	.word	48131
	.word	34999
	.word	49171
	.word	0
	.word	0
	.word	0
	.word	39812
	.word	13033
	.word	49143
	.word	34463
	.word	49170
	.word	0
	.word	0
	.word	0
	.word	11756
	.word	24889
	.word	14253
	.word	47765
	.word	16408
	.word	0
	.word	0
	.word	0
	.word	27397
	.word	56992
	.word	59569
	.word	35306
	.word	49183
	.word	0
	.word	0
	.word	0
	.word	1499
	.word	3328
	.word	1093
	.word	60276
	.word	49177
	.word	0
	.word	0
	.word	0
	.word	54443
	.word	57316
	.word	38982
	.word	63406
	.word	16418
	.word	0
	.word	0
	.word	0
	.word	46810
	.word	23189
	.word	13701
	.word	35573
	.word	49196
	.word	0
	.word	0
	.word	0
	.word	40551
	.word	19489
	.word	60782
	.word	40648
	.word	49186
	.word	0
	.word	0
	.word	0
	.word	16371
	.word	31130
	.word	60187
	.word	37497
	.word	16430
	.word	0
	.word	0
	.word	0
	.word	56271
	.word	55717
	.word	16120
	.word	36897
	.word	49210
	.word	0
	.word	0
	.word	0
	.word	11488
	.word	11046
	.word	17817
	.word	40691
	.word	49195
	.word	0
	.word	0
	.word	0
	.word	54617
	.word	39007
	.word	658
	.word	37550
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	32140
	.word	49295
	.word	21536
	.word	36961
	.word	49225
	.word	0
	.word	0
	.word	0
	.word	5702
	.word	58081
	.word	34597
	.word	57945
	.word	49204
	.word	0
	.word	0
	.word	0
	.word	48280
	.word	56894
	.word	9030
	.word	60159
	.word	16454
	.word	0
	.word	0
	.word	0
	.word	61535
	.word	4768
	.word	37086
	.word	33310
	.word	49241
	.word	0
	.word	0
	.word	0
	.word	53564
	.word	21787
	.word	24446
	.word	56588
	.word	49214
	.word	0
	.word	0
	.word	0
	.word	35771
	.word	11952
	.word	2619
	.word	36719
	.word	16468
	.word	0
	.word	0
	.word	0
	.word	47624
	.word	53808
	.word	19802
	.word	50829
	.word	49257
	.word	0
	.word	0
	.word	0
	.word	42706
	.word	15946
	.word	64291
	.word	36776
	.word	49225
	.word	0
	.word	0
	.word	0
	.word	8317
	.word	44716
	.word	51672
	.word	32812
	.word	16482
	.word	0
	.word	0
	.word	0
	.word	31251
	.word	20899
	.word	16102
	.word	62455
	.word	49274
	.word	0
	.word	0
	.word	0
	.word	2105
	.word	17517
	.word	10872
	.word	62061
	.word	49235
	.word	0
	.word	0
	.word	0
	.word	60728
	.word	14335
	.word	45529
	.word	41528
	.word	16496
	.word	0
	.word	0
	.word	0
	.word	1336
	.word	56903
	.word	46326
	.word	59283
	.word	49292
	.word	0
	.word	0
	.word	0
	.word	34501
	.word	54054
	.word	11762
	.word	33288
	.word	49247
	.word	0
	.word	0
	.word	0
	.word	35638
	.word	17126
	.word	61083
	.word	36196
	.word	16511
	.word	0
	.word	0
	.word	0
	.word	12489
	.word	2246
	.word	55342
	.word	41983
	.word	49311
	.word	0
	.word	0
	.word	0
	.word	55790
	.word	52004
	.word	63053
	.word	44600
	.word	49258
	.word	0
	.word	0
	.word	0
	.word	1778
	.word	6965
	.word	62764
	.word	42435
	.word	16526
	.word	0
	.word	0
	.word	0
	.word	47762
	.word	48342
	.word	51287
	.word	43067
	.word	49330
	.word	0
	.word	0
	.word	0
	.word	51047
	.word	24828
	.word	3408
	.word	36750
	.word	49270
	.word	0
	.word	0
	.word	0
	.word	10839
	.word	65289
	.word	49628
	.word	32780
	.word	16542
	.word	0
	.word	0
	.word	0
	.word	14289
	.word	53581
	.word	2985
	.word	62379
	.word	49349
	.word	0
	.word	0
	.word	0
	.word	3506
	.word	59781
	.word	19773
	.word	36749
	.word	49282
	.word	0
	.word	0
	.word	0
	.word	6220
	.word	41678
	.word	56685
	.word	32779
	.word	16558
	.word	0
	.word	0
	.word	0
	.word	31299
	.word	40156
	.word	60492
	.word	62376
	.word	49369
	.word	0
	.word	0
	.word	0
	.word	64754
	.word	30182
	.word	28471
	.word	44055
	.word	49294
	.word	0
	.word	0
	.word	0
	.word	47368
	.word	43518
	.word	20307
	.word	41745
	.word	16574
	.word	0
	.word	0
	.word	0
	.word	65114
	.word	13130
	.word	26792
	.word	42193
	.word	49390
	.word	0
	.word	0
	.word	0
	.word	13136
	.word	49204
	.word	39329
	.word	62843
	.word	49306
	.word	0
	.word	0
	.word	0
	.word	63757
	.word	8765
	.word	38452
	.word	33516
	.word	16591
	.word	0
	.word	0
	.word	0
	.word	9085
	.word	22499
	.word	28021
	.word	38134
	.word	49411
	.word	0
	.word	0
	.word	0
	.word	10631
	.word	26389
	.word	15903
	.word	44739
	.word	49319
	.word	0
	.word	0
	.word	0
	.word	24915
	.word	57881
	.word	5976
	.word	53687
	.word	16607
	.word	0
	.word	0
	.word	0
	.word	16910
	.word	44909
	.word	48389
	.word	34359
	.word	49432
	.word	0
	.word	0
	.word	0
	.type	_ldbA,@object
	.size	_ldbA,6160
	.align 16
_dbA:
	.long	4235179544
	.long	3219290252
	.long	3295226828
	.long	3164508998
	.long	1649608659
	.long	1072320934
	.long	3173846476
	.long	1014071527
	.long	1542107862
	.long	3218711765
	.long	2540989727
	.long	3163202496
	.long	1649608660
	.long	3220853158
	.long	859914669
	.long	1017880115
	.long	717740104
	.long	3219214642
	.long	2376945980
	.long	3162254827
	.long	1431655765
	.long	1068848469
	.long	1431655765
	.long	1012225365
	.long	1431655765
	.long	1068848469
	.long	42583435
	.long	1011709190
	.long	3362045620
	.long	1072523249
	.long	4064281872
	.long	1016386130
	.long	4002465326
	.long	3220678216
	.long	1750589542
	.long	1014054706
	.long	2343010478
	.long	3220651595
	.long	2022209519
	.long	3165213628
	.long	3582788241
	.long	1077376220
	.long	3584976419
	.long	3225506388
	.long	2237448112
	.long	1085305363
	.long	634573353
	.long	3233318238
	.long	2248101888
	.long	1073427871
	.long	1861873466
	.long	1016835790
	.long	2560862412
	.long	1071060948
	.long	30404090
	.long	3163588833
	.long	1673151062
	.long	1081107801
	.long	641486499
	.long	3229145757
	.long	2847476088
	.long	1079825078
	.long	4109693297
	.long	3227890617
	.long	80699903
	.long	1078574114
	.long	2114691410
	.long	3226679074
	.long	1354791574
	.long	1083527322
	.long	1238563653
	.long	3231741049
	.long	2909836308
	.long	1082322123
	.long	2961745666
	.long	3230453807
	.long	4254370204
	.long	3222569838
	.long	2523116623
	.long	1016608103
	.long	3036354677
	.long	3220338375
	.long	4197032384
	.long	3163739097
	.long	618767413
	.long	1086658447
	.long	4224046954
	.long	3235503748
	.long	918515347
	.long	1104466354
	.long	1558545744
	.long	3253448772
	.long	3598672851
	.long	1075147148
	.long	1862579612
	.long	1019557195
	.long	814211565
	.long	1073042505
	.long	3816489657
	.long	3166119701
	.long	1438601496
	.long	1094947899
	.long	3932640262
	.long	3243813893
	.long	1025077628
	.long	1092155011
	.long	4084583552
	.long	3241103782
	.long	2491631644
	.long	1089473568
	.long	3212116569
	.long	3238261210
	.long	764386880
	.long	1100022671
	.long	1839771210
	.long	3248755462
	.long	3990369246
	.long	1097905463
	.long	1112302030
	.long	3246751353
	.long	717740104
	.long	1070682418
	.long	230143214
	.long	1014072542
	.long	1542107862
	.long	1071228117
	.long	2869986399
	.long	1017025354
	.long	2620920409
	.long	1068568872
	.long	3005931917
	.long	1068498895
	.long	3773030413
	.long	1068741081
	.long	221735135
	.long	1068648815
	.long	1649608659
	.long	1072320934
	.long	2259103138
	.long	1014410184
	.long	1798481678
	.long	1068036068
	.long	1435858614
	.long	1066348979
	.long	2798813559
	.long	1068114295
	.long	1711004053
	.long	1067747678
	.long	2010902109
	.long	1068166444
	.long	2474987820
	.long	1068059813
	.long	780818753
	.long	1068375890
	.long	4117616766
	.long	1068267614
	.long	1604677386
	.long	1068848813
	.long	1854392195
	.long	1067982657
	.long	4235179544
	.long	1071806604
	.long	2606438347
	.long	1015960145
	.long	2893907075
	.long	1066734370
	.long	2294698909
	.long	1014073149
	.long	441808390
	.long	3216064000
	.long	1880662273
	.long	1012321168
	.long	3253204732
	.long	3200326446
	.long	3649195207
	.long	1051723342
	.long	1676415034
	.long	3202599881
	.long	856428669
	.long	1053972858
	.long	3299217318
	.long	1070900044
	.long	4151699616
	.long	1010498715
	.long	1123572530
	.long	1044951237
	.long	3942090667
	.long	3191111442
	.long	179320158
	.long	3191607973
	.long	648955671
	.long	3189543958
	.long	338963396
	.long	3195730567
	.long	729244107
	.long	1047109933
	.long	491939632
	.long	3198035439
	.long	2441649049
	.long	1049392674
	.long	4000663347
	.long	1056276700
	.long	3497022620
	.long	3193423889
	.long	119575502
	.long	1071320806
	.long	3893526980
	.long	3160417465
	.long	2378611747
	.long	3217220647
	.long	3721041488
	.long	3160948136
	.long	212213087
	.long	1003805565
	.long	3707885569
	.long	946762142
	.long	954325311
	.long	1061996326
	.long	2572405945
	.long	3208784108
	.long	2047107390
	.long	1053958905
	.long	2157300588
	.long	3201555925
	.long	3371055275
	.long	1071576875
	.long	1069844800
	.long	1015429887
	.long	3166931521
	.long	3216972217
	.long	3220968514
	.long	3161728497
	.long	4141120019
	.long	1057975373
	.long	3048513049
	.long	3204734413
	.long	1172170140
	.long	1059250739
	.long	2600146741
	.long	3206041877
	.long	2751605624
	.long	1060573214
	.long	1663282963
	.long	3207426590
	.long	3895324829
	.long	1055298027
	.long	157415216
	.long	3202171477
	.long	3734613381
	.long	1056602430
	.long	277770430
	.long	3203481600
	.long	3198889327
	.long	3217706325
	.long	2705408501
	.long	1014676637
	.long	59787751
	.long	1073579891
	.long	1470716836
	.long	1013369721
	.long	2045320829
	.long	1061536716
	.long	93552279
	.long	3208285776
	.long	1578930691
	.long	1053381091
	.long	2541023
	.long	3199217833
	.long	2303467340
	.long	1072252569
	.long	567354353
	.long	1016170573
	.long	4277811695
	.long	1072049730
	.long	3950020367
	.long	3160819724
	.long	2523318625
	.long	1057215902
	.long	2302510246
	.long	3203993877
	.long	298082853
	.long	1058616118
	.long	2097322096
	.long	3205466603
	.long	1798601470
	.long	1060132265
	.long	1407285416
	.long	3206822737
	.long	4077669298
	.long	1054450862
	.long	2333929458
	.long	3201533971
	.long	2747507284
	.long	1055881541
	.long	2839080721
	.long	3202576178
	.long	3383958356
	.long	3218498155
	.long	613656875
	.long	1017795528
	.long	173059452
	.long	1075530818
	.long	704088483
	.long	1015836313
	.long	669591746
	.long	1061765380
	.long	2029568326
	.long	3208524070
	.long	4157429832
	.long	1054150757
	.long	704852271
	.long	3201323640
	.long	846452204
	.long	1073165719
	.long	1707052239
	.long	1015225852
	.long	1011120585
	.long	1074996836
	.long	2354399398
	.long	3161432137
	.long	954697072
	.long	1057288366
	.long	1270411324
	.long	3204047380
	.long	711769130
	.long	1058758297
	.long	1128721
	.long	3205559284
	.long	179011054
	.long	1060257504
	.long	4204770871
	.long	3206955261
	.long	3137275514
	.long	1053955134
	.long	762311517
	.long	3199983289
	.long	728345252
	.long	1056011172
	.long	1369631397
	.long	3202845593
	.long	3569027386
	.long	3214047105
	.long	3629871173
	.long	3159432582
	.long	2480864277
	.long	1072801415
	.long	765976303
	.long	1016615598
	.long	759560957
	.long	1082479958
	.long	4267700141
	.long	3214355674
	.long	519344805
	.long	3246817843
	.long	220815467
	.long	3246677566
	.long	585197945
	.long	1074992152
	.long	3068616556
	.long	1020233496
	.long	4125953813
	.long	3215772665
	.long	1932096513
	.long	3151829739
	.long	891522464
	.long	1087323966
	.long	2489260670
	.long	3237524288
	.long	198576582
	.long	1086294508
	.long	1118764770
	.long	3231025522
	.long	4028480604
	.long	1084377135
	.long	1979567970
	.long	3223360994
	.long	4063308394
	.long	3244561236
	.long	4166146281
	.long	3246077527
	.long	2664161993
	.long	3240072782
	.long	259025260
	.long	3242645417
	.long	378360574
	.long	1070238620
	.long	2959696752
	.long	1069921292
	.long	573154909
	.long	1069707436
	.long	4044344316
	.long	1069551882
	.long	477218588
	.long	1069314503
	.long	0
	.long	3217031168
	.long	2454267026
	.long	1069697316
	.long	1431655765
	.long	3217380693
	.long	2576980378
	.long	1070176665
	.long	0
	.long	3218079744
	.long	1431655765
	.long	1070945621
	.long	0
	.long	3219128320
	.long	2759451285
	.long	3216787420
	.long	439852373
	.long	3160440428
	.long	925239789
	.long	1070204188
	.long	1052235825
	.long	1014694615
	.long	2825585883
	.long	3219218351
	.long	1250510261
	.long	3158266606
	.long	4280873151
	.long	1070880196
	.long	3382653115
	.long	1014364358
	.long	3432120556
	.long	1060112640
	.long	1689864936
	.long	3206804514
	.long	2559966835
	.long	1058598642
	.long	1675881979
	.long	3205446725
	.long	3157807190
	.long	1057195995
	.long	83437824
	.long	3203930944
	.long	714759041
	.long	1055696489
	.long	94115283
	.long	3202310560
	.long	231342353
	.long	1053665780
	.long	2283895727
	.long	3199602663
	.long	2514471110
	.long	1050042839
	.long	1783553765
	.long	3194406773
	.long	2977985131
	.long	3217141333
	.long	1199854581
	.long	3162366396
	.long	2962538897
	.long	3216960405
	.long	4143040985
	.long	3159573871
	.long	1505583242
	.long	1067626192
	.long	1685935487
	.long	1007379916
	.long	1307717362
	.long	1071507942
	.long	2592844004
	.long	1015649171
	.long	799685378
	.long	1060113448
	.long	3181808512
	.long	3206805166
	.long	237118685
	.long	1058600582
	.long	1021521704
	.long	3205452843
	.long	562863096
	.long	1057199158
	.long	124222188
	.long	3203968611
	.long	666083857
	.long	1055962453
	.long	2573104645
	.long	3202709164
	.long	3241565485
	.long	1053885149
	.long	1335355988
	.long	3200533016
	.long	1040080838
	.long	1054134824
	.long	2847172487
	.long	3201012249
	.long	3198889327
	.long	3217706325
	.long	1543330549
	.long	3160864782
	.long	4277811695
	.long	1072049730
	.long	993295933
	.long	1014676638
	.long	59787751
	.long	1073579891
	.long	130295530
	.long	1013369744
	.long	2303467340
	.long	1072252569
	.long	3943765603
	.long	1016170278
	.long	3608922566
	.long	1060045649
	.long	1453569939
	.long	3207287740
	.long	3338057825
	.long	3206066477
	.long	3038862383
	.long	3208900206
	.long	2094875998
	.long	3209983096
	.long	1016858122
	.long	3211012944
	.long	2854570659
	.long	3211659217
	.long	1144696552
	.long	3211904901
	.long	2176258130
	.long	3211749311
	.long	3478125247
	.long	3210994876
	.long	2355567962
	.long	3209692799
	.long	3598755244
	.long	3207186288
	.long	3383958356
	.long	3218498155
	.long	141035778
	.long	3162876606
	.long	1011120585
	.long	1074996836
	.long	1865540452
	.long	1017795522
	.long	173059452
	.long	1075530818
	.long	1582816653
	.long	1015838421
	.long	846452204
	.long	1073165719
	.long	2382960778
	.long	1015259319
	.long	3627221817
	.long	1060257525
	.long	782594551
	.long	3206955283
	.long	1981242537
	.long	1058758037
	.long	1755079229
	.long	3205559658
	.long	2436997517
	.long	1057278666
	.long	3384076381
	.long	3204067450
	.long	2476719666
	.long	1056224805
	.long	337705071
	.long	3201230508
	.long	776493412
	.long	3197801902
	.long	2139883963
	.long	3204685556
	.long	1475852201
	.long	3204789337
	.long	945638604
	.long	3203947238
	.long	3198889327
	.long	3217706325
	.long	4282828456
	.long	3160832800
	.long	4277811695
	.long	1072049730
	.long	993624082
	.long	1014676638
	.long	59787751
	.long	1073579891
	.long	1881378167
	.long	1013369744
	.long	2303467340
	.long	1072252569
	.long	2285482445
	.long	1016170301
	.long	2583886559
	.long	1060132251
	.long	2972085422
	.long	3206822622
	.long	1867851267
	.long	1058615355
	.long	680445507
	.long	3205461644
	.long	1455562209
	.long	1057201491
	.long	2171135678
	.long	3203931082
	.long	973516116
	.long	1055683749
	.long	442346719
	.long	3202274605
	.long	3339219778
	.long	1053603010
	.long	4261370927
	.long	3199529466
	.long	1389725956
	.long	1049937931
	.long	3421055527
	.long	3194266408
	.long	3440444171
	.long	3149749917
	.long	2683862484
	.long	3093774621
	.long	1722766174
	.long	1073233897
	.long	1535800986
	.long	1017782563
	.long	2952983900
	.long	1075015403
	.long	1613333390
	.long	1019450778
	.long	1747624399
	.long	1077465903
	.long	1323746741
	.long	1077414384
	.long	2170768622
	.long	1079283370
	.long	3681271869
	.long	1079585146
	.long	2395326938
	.long	1081247483
	.long	1534348539
	.long	1081774816
	.long	339892429
	.long	1083288020
	.long	3528406654
	.long	1083985153
	.long	2588043907
	.long	1085376283
	.long	2346296707
	.long	1086214596
	.long	207705063
	.long	1087509517
	.long	2560418979
	.long	1088486188
	.long	2868926017
	.long	1088759609
	.long	1843351610
	.long	1091968574
	.long	2991121454
	.long	1008150290
	.long	2149211495
	.long	952864023
	.long	1596959828
	.long	1075781798
	.long	2172289614
	.long	1019977377
	.long	2540785903
	.long	1077531858
	.long	1236674410
	.long	1020936929
	.long	4153228343
	.long	1087542211
	.long	2696338690
	.long	1090238842
	.long	878599865
	.long	1092951715
	.long	1553131440
	.long	1095784438
	.long	1380037893
	.long	1098488966
	.long	2667481451
	.long	1101284640
	.long	2282526011
	.long	1104162081
	.long	2355356013
	.long	1106903006
	.long	2198000318
	.long	1109722332
	.long	415847444
	.long	1112609215
	.long	1244368631
	.long	1115409383
	.long	1504872183
	.long	1118250351
	.long	1483597343
	.long	1121215185
	.long	2372686104
	.long	1123791138
	.long	2950620603
	.long	1012416763
	.long	1854687498
	.long	952811571
	.long	4103214658
	.long	1077594716
	.long	2773032534
	.long	1020189917
	.long	3447799694
	.long	1081361429
	.long	400789830
	.long	1023147708
	.long	3214535591
	.long	1099259453
	.long	1237622722
	.long	1103959294
	.long	3800166525
	.long	1108620252
	.long	3349752687
	.long	1113359257
	.long	1736707866
	.long	1118068670
	.long	1604218008
	.long	1122855127
	.long	472376766
	.long	1127573289
	.long	2849635624
	.long	1132420823
	.long	1552594705
	.long	1137123494
	.long	3251899291
	.long	1141973158
	.long	523050174
	.long	1146711894
	.long	2820496385
	.long	1151553661
	.long	507113183
	.long	1156515491
	.long	3382661695
	.long	1160984436
	.long	1797993220
	.long	1014085677
	.long	1007534641
	.long	957814894
	.long	136949250
	.long	1079957294
	.long	360284395
	.long	1016466469
	.long	784478107
	.long	1086123203
	.long	2737766812
	.long	1029538613
	.long	1541190179
	.long	1113554317
	.long	209153222
	.long	1120512208
	.long	2353533988
	.long	1127569405
	.long	445988592
	.long	1134692642
	.long	3579939511
	.long	1141824664
	.long	1580626808
	.long	1148890066
	.long	214151816
	.long	1156005346
	.long	2068712408
	.long	1163158821
	.long	839406419
	.long	1170342165
	.long	3247543387
	.long	1177546316
	.long	4025420415
	.long	1184654254
	.long	956971024
	.long	1191825477
	.long	2268897124
	.long	1199100768
	.long	3197790250
	.long	1206020453
	.long	2712668744
	.long	1014362371
	.long	420246125
	.long	958137372
	.long	2654663338
	.long	1082564068
	.long	4161996347
	.long	1025251297
	.long	1213005072
	.long	1091554851
	.long	789699257
	.long	1035400076
	.long	800710857
	.long	1129640478
	.long	2496483081
	.long	1139363849
	.long	599798751
	.long	1149188652
	.long	1701185240
	.long	1158910290
	.long	2369516264
	.long	1168692319
	.long	283492325
	.long	1178587937
	.long	3183141054
	.long	1188335795
	.long	3641040163
	.long	1198179274
	.long	561697907
	.long	1208059224
	.long	1909654917
	.long	1217858291
	.long	2831089635
	.long	1227776627
	.long	2923368563
	.long	1237613769
	.long	3277782853
	.long	1247623400
	.long	2305379544
	.long	1257247294
	.long	2173558120
	.long	3167899773
	.long	3928519072
	.long	3112134976
	.long	2855828427
	.long	1085518855
	.long	2402042833
	.long	1029906467
	.long	3668949703
	.long	1097350789
	.long	1498999354
	.long	1041534216
	.long	1894732623
	.long	1147309596
	.long	3157447236
	.long	1159959522
	.long	1106557451
	.long	1172641345
	.long	2830063624
	.long	1185354419
	.long	1827524850
	.long	1198100435
	.long	3148494391
	.long	1210882790
	.long	408406249
	.long	1223697282
	.long	1409028207
	.long	1236424336
	.long	3586220107
	.long	1249179206
	.long	1522157876
	.long	1261965775
	.long	1820647987
	.long	1274787293
	.long	141517691
	.long	1287671841
	.long	1169226569
	.long	1300517027
	.long	1216765362
	.long	1313047128
	.long	1448028998
	.long	1021264728
	.long	3859033985
	.long	964258238
	.long	4275468065
	.long	1088663688
	.long	2839015045
	.long	1031960699
	.long	2471825140
	.long	1103641144
	.long	2116308392
	.long	1045704405
	.long	320063693
	.long	1166181438
	.long	1786259137
	.long	1181976509
	.long	2022503488
	.long	1197803342
	.long	3654410397
	.long	1213661261
	.long	3612544687
	.long	1229551918
	.long	2586390227
	.long	1245478664
	.long	4037720966
	.long	1261441627
	.long	425603198
	.long	1277313397
	.long	2335359117
	.long	1293212748
	.long	663099774
	.long	1309143515
	.long	3275152075
	.long	1325108897
	.long	3939307299
	.long	1341142935
	.long	2389223216
	.long	1357131850
	.long	3001851795
	.long	1372807567
	.long	2843660044
	.long	1027797837
	.long	4081430736
	.long	972886614
	.long	59873159
	.long	1091970578
	.long	89491691
	.long	1036070212
	.long	800587417
	.long	1110354163
	.long	1316695415
	.long	1054362062
	.long	1053415956
	.long	1186120592
	.long	1951112295
	.long	1205224339
	.long	3452306653
	.long	1224406940
	.long	3087408826
	.long	1243649885
	.long	3141065505
	.long	1262791580
	.long	1611223017
	.long	1282016047
	.long	2457220276
	.long	1301315843
	.long	567478369
	.long	1320490698
	.long	744018712
	.long	1339759601
	.long	3767838035
	.long	1359052970
	.long	2731826519
	.long	1378265330
	.long	2965944329
	.long	1397620918
	.long	3792836018
	.long	1416954415
	.long	4140163483
	.long	1435938859
	.long	3803251356
	.long	1031449026
	.long	1468351707
	.long	974519095
	.long	1510773204
	.long	1095479170
	.long	3459876679
	.long	1039900066
	.long	2329980675
	.long	1117254636
	.long	1442547585
	.long	1060380811
	.long	3793321184
	.long	1207035105
	.long	3851928555
	.long	1229621800
	.long	221632261
	.long	1252267869
	.long	2942953492
	.long	1274996464
	.long	3017520070
	.long	1297616871
	.long	239470767
	.long	1320348902
	.long	1043025700
	.long	1343095998
	.long	252688637
	.long	1365768517
	.long	3880214591
	.long	1388528317
	.long	3579258508
	.long	1411323545
	.long	4034496078
	.long	1434003397
	.long	4007330572
	.long	1456787727
	.long	4167750282
	.long	1479686237
	.long	1263363363
	.long	1502124177
	.long	1204128515
	.long	3180787222
	.long	711751003
	.long	3124411148
	.long	655669005
	.long	1099106472
	.long	1779452985
	.long	1041271036
	.long	3714137145
	.long	1124508819
	.long	3422050113
	.long	1068643358
	.long	2129754319
	.long	1228815190
	.long	1696310114
	.long	1255068559
	.long	1933048134
	.long	1281362741
	.long	1387613391
	.long	1307637530
	.long	3120180336
	.long	1333930483
	.long	2543548174
	.long	1360241860
	.long	1743952085
	.long	1386572739
	.long	830746048
	.long	1412924879
	.long	3823372438
	.long	1439300663
	.long	3126160514
	.long	1465701841
	.long	3650788872
	.long	1492126330
	.long	1753940109
	.long	1518485640
	.long	4158688957
	.long	1544943454
	.long	2419002512
	.long	1571054314
	.long	1387068573
	.long	1036196119
	.long	693317055
	.long	975620966
	.long	85037501
	.long	1102875900
	.long	2766547698
	.long	1047046221
	.long	692715848
	.long	1132034342
	.long	4084350207
	.long	1074738271
	.long	3670967869
	.long	1251313085
	.long	877136010
	.long	1281389973
	.long	2367669872
	.long	1311355635
	.long	127681551
	.long	1341415835
	.long	294488793
	.long	1371561390
	.long	2268228308
	.long	1401588863
	.long	1971903155
	.long	1431680659
	.long	3068569563
	.long	1461838328
	.long	2810042085
	.long	1491968471
	.long	1803405805
	.long	1522051425
	.long	2718559511
	.long	1552192554
	.long	1282364655
	.long	1582403523
	.long	2077022624
	.long	1612690417
	.long	3574791831
	.long	1642467624
	.long	1679930357
	.long	3189181796
	.long	2680901783
	.long	3131938295
	.long	3217922759
	.long	1106719372
	.long	512421297
	.long	1047804811
	.long	767402551
	.long	1139855610
	.long	1780644693
	.long	1084244525
	.long	2568107092
	.long	1274594461
	.long	3742446639
	.long	1308542877
	.long	2828203709
	.long	1342408213
	.long	1035153137
	.long	1376332101
	.long	135830143
	.long	1410361976
	.long	3493601403
	.long	1444258562
	.long	2859691870
	.long	1478279541
	.long	289914903
	.long	1512259079
	.long	388251426
	.long	1546250265
	.long	3047966471
	.long	1580303536
	.long	3861742472
	.long	1614266653
	.long	1260166508
	.long	1648392316
	.long	904399223
	.long	1682450578
	.long	3334685854
	.long	1716212562
	.long	883499133
	.long	1045834798
	.long	1040153219
	.long	987496666
	.long	789418155
	.long	1110723643
	.long	2045920566
	.long	1052287721
	.long	682953991
	.long	1147781119
	.long	96584340
	.long	1092099645
	.long	666163210
	.long	1298545479
	.long	1527740296
	.long	1336421526
	.long	43027861
	.long	1374344588
	.long	1893757912
	.long	1412317817
	.long	3310921752
	.long	1450264132
	.long	1086815150
	.long	1488186400
	.long	2822587825
	.long	1526147814
	.long	1629445394
	.long	1564156239
	.long	2622810410
	.long	1602221540
	.long	2876661682
	.long	1640163607
	.long	151499969
	.long	1678143447
	.long	3591180187
	.long	1716205976
	.long	126024331
	.long	1754364017
	.long	3251093187
	.long	1792100966
	.long	2455853998
	.long	3194846833
	.long	2105329433
	.long	3137419111
	.long	1677091468
	.long	1114834807
	.long	3103559369
	.long	1057563919
	.long	156965555
	.long	1155965375
	.long	1332523955
	.long	1098438055
	.long	2734085455
	.long	1323184128
	.long	3116950081
	.long	1365165587
	.long	2683234966
	.long	1407189808
	.long	2373753488
	.long	1449192848
	.long	1367022160
	.long	1491213979
	.long	3828829922
	.long	1533253445
	.long	649201501
	.long	1575312310
	.long	1349232455
	.long	1617392311
	.long	2871919995
	.long	1659495809
	.long	3481654166
	.long	1701624529
	.long	3617101938
	.long	1743779051
	.long	431481593
	.long	1785867563
	.long	121308995
	.long	1828052659
	.long	357611834
	.long	1869892289
	.long	294133886
	.long	3202139689
	.long	3712546231
	.long	3146198591
	.long	3880935161
	.long	1119029102
	.long	59549724
	.long	1063684005
	.long	3647913141
	.long	1164353962
	.long	4227156178
	.long	1109331949
	.long	3682674216
	.long	1348349871
	.long	2807072537
	.long	1394525619
	.long	2124002856
	.long	1440744183
	.long	2605559380
	.long	1486941516
	.long	2185296140
	.long	1533156938
	.long	3364132884
	.long	1579390693
	.long	3636842097
	.long	1625643843
	.long	1564856349
	.long	1671918127
	.long	282911834
	.long	1718215904
	.long	224295158
	.long	1764538898
	.long	1397797422
	.long	1810887684
	.long	130160665
	.long	1857170584
	.long	3494254532
	.long	1903549984
	.long	2087544159
	.long	1949584056
	.long	2070009724
	.long	3207114478
	.long	3313155961
	.long	3151739743
	.long	1799064076
	.long	1123301109
	.long	3318052585
	.long	1067728611
	.long	1398608221
	.long	1172933154
	.long	3195587656
	.long	1117275768
	.long	3966862011
	.long	1374007527
	.long	3094801078
	.long	1424457063
	.long	201966774
	.long	1474951107
	.long	1216746698
	.long	1525492144
	.long	4169038329
	.long	1576047750
	.long	2574869838
	.long	1626540825
	.long	3353136223
	.long	1677069826
	.long	246421124
	.long	1727641760
	.long	3497001028
	.long	1778265406
	.long	1014877316
	.long	1828833334
	.long	2620327651
	.long	1879376562
	.long	1343179361
	.long	1929994932
	.long	3499694378
	.long	1980775534
	.long	852481763
	.long	2031094897
	.long	858300611
	.long	1060903333
	.long	1069421656
	.long	1004554972
	.long	381774873
	.long	1127661932
	.long	2890562600
	.long	1072008419
	.long	3230239600
	.long	1181757061
	.long	1093463150
	.long	1126994478
	.long	2041459920
	.long	1400246667
	.long	1834149192
	.long	1455089424
	.long	3431867819
	.long	1509997896
	.long	673297555
	.long	1564813682
	.long	2939272129
	.long	1619727956
	.long	2561583193
	.long	1674674892
	.long	1592460776
	.long	1729549373
	.long	4229377564
	.long	1784543364
	.long	3147274091
	.long	1839450750
	.long	199676655
	.long	1894400365
	.long	515597810
	.long	1949398723
	.long	2826001657
	.long	2004337665
	.long	1832191040
	.long	2059473164
	.long	486033509
	.long	2114113687
	.long	2615285763
	.long	3215685247
	.long	3139164341
	.long	3160067829
	.long	2576980377
	.long	1132042649
	.long	2136544456
	.long	1076438044
	.long	1202590842
	.long	1190427361
	.long	3058408830
	.long	1135356281
	.long	2163381214
	.long	1426480799
	.long	2353327718
	.long	1485745575
	.long	3794661129
	.long	1544911246
	.long	3594844771
	.long	1604225727
	.long	3801864141
	.long	1663434111
	.long	1474426882
	.long	1722809436
	.long	2103181697
	.long	1782019277
	.long	1306699518
	.long	1841386098
	.long	2394149058
	.long	1900657427
	.long	2802357954
	.long	1959997432
	.long	4190579671
	.long	2019374921
	.long	1589399104
	.long	2078657782
	.long	459261522
	.long	2137266825
	.long	4294967295
	.long	2146435071
	.long	158971950
	.long	3154658393
	.long	3459173435
	.long	3099360488
	.long	2835657100
	.long	3221135661
	.long	3134582638
	.long	3163662002
	.long	1009146449
	.long	1076045439
	.long	2177907437
	.long	1020724564
	.long	2093419253
	.long	1082406719
	.long	3184966549
	.long	3231785529
	.long	3799028631
	.long	1086175845
	.long	81768581
	.long	3235523877
	.long	1017495356
	.long	1089951767
	.long	1546026610
	.long	3239380678
	.long	980092412
	.long	1093867758
	.long	4064795288
	.long	3243341671
	.long	3027586419
	.long	1097863735
	.long	1002816472
	.long	3247290528
	.long	985745927
	.long	1101758613
	.long	1714999460
	.long	3251222455
	.long	808706777
	.long	1105883774
	.long	2540867906
	.long	3255308994
	.long	1548702939
	.long	3157340334
	.long	1276328330
	.long	3102548777
	.long	2523682374
	.long	3224680861
	.long	1002691513
	.long	3168986622
	.long	2919511741
	.long	1081046750
	.long	3737147325
	.long	1024759629
	.long	2879373926
	.long	1098115315
	.long	103788827
	.long	3250052030
	.long	3732555687
	.long	1107111798
	.long	3845853773
	.long	3259114079
	.long	3808164400
	.long	1116131626
	.long	1738785984
	.long	3268214722
	.long	581652
	.long	1125291233
	.long	3382894442
	.long	3277322952
	.long	2879368705
	.long	1134502288
	.long	2391566275
	.long	3286518813
	.long	274553400
	.long	1143638273
	.long	512789562
	.long	3295790146
	.long	3583864087
	.long	1152986324
	.long	2337425402
	.long	3304798727
	.long	1854575809
	.long	3159867196
	.long	1765059742
	.long	3103980390
	.long	1056564255
	.long	3227329098
	.long	957604928
	.long	3170301297
	.long	1737111893
	.long	1086018364
	.long	3821479224
	.long	1030262355
	.long	1397067068
	.long	1113230428
	.long	1054232731
	.long	3267692171
	.long	1978243918
	.long	1127284645
	.long	3722409577
	.long	3281775634
	.long	1918858489
	.long	1141323813
	.long	3391721543
	.long	3295903979
	.long	2607178807
	.long	1155564765
	.long	3212344015
	.long	3310103974
	.long	3369531970
	.long	1169698149
	.long	335451402
	.long	3324302881
	.long	431247382
	.long	1183973580
	.long	3717434926
	.long	3338657222
	.long	1904523769
	.long	1198380992
	.long	8765522
	.long	3352646819
	.long	2089515505
	.long	1016996008
	.long	1260660699
	.long	961055715
	.long	2755288594
	.long	3230032374
	.long	651647436
	.long	3174928677
	.long	3079991920
	.long	1091533276
	.long	3443268597
	.long	1035583207
	.long	3925746702
	.long	1129598292
	.long	1087270140
	.long	3286788174
	.long	494941268
	.long	1149105228
	.long	1413067450
	.long	3306335280
	.long	2943951130
	.long	1168609830
	.long	1917094202
	.long	3325955583
	.long	3282582259
	.long	1188254230
	.long	205065768
	.long	3345548222
	.long	3975827413
	.long	1207978573
	.long	608024766
	.long	3365228477
	.long	514952666
	.long	1227617187
	.long	1683103546
	.long	3384985256
	.long	3324939681
	.long	1247457575
	.long	4078451799
	.long	3404527118
	.long	3100987862
	.long	1019853288
	.long	2384335067
	.long	962691388
	.long	2781784351
	.long	3233000439
	.long	1271614660
	.long	3176489869
	.long	626016822
	.long	1097348250
	.long	3842822006
	.long	1040353260
	.long	2239739676
	.long	1147303774
	.long	189136319
	.long	3307436007
	.long	2396687400
	.long	1172632530
	.long	1908223243
	.long	3332827221
	.long	2121588596
	.long	1198087089
	.long	3983173167
	.long	3358350016
	.long	2487337699
	.long	1223686165
	.long	3249965797
	.long	3383895554
	.long	3887284480
	.long	1249163916
	.long	138948171
	.long	3409430498
	.long	1463100005
	.long	1274763805
	.long	34738943
	.long	3435142323
	.long	2889101777
	.long	1300502035
	.long	2665757401
	.long	3460504734
	.long	987318156
	.long	3170486179
	.long	252831213
	.long	3113594951
	.long	4266405771
	.long	3236147062
	.long	52757777
	.long	3180182741
	.long	1823142101
	.long	1103640807
	.long	3806357955
	.long	1046313461
	.long	875359927
	.long	1166180665
	.long	1815208659
	.long	3329459206
	.long	1303129141
	.long	1197802172
	.long	4280462790
	.long	3361143469
	.long	626701262
	.long	1229550147
	.long	2303856491
	.long	3392960132
	.long	2759522360
	.long	1261440286
	.long	145515022
	.long	3424795397
	.long	2201193688
	.long	1293210725
	.long	2057332964
	.long	3456624552
	.long	3539517099
	.long	1325105434
	.long	1364231459
	.long	3488625947
	.long	3421843712
	.long	1357133388
	.long	200896422
	.long	3520281853
	.long	951171540
	.long	1026768452
	.long	3090769089
	.long	971487574
	.long	4234403032
	.long	3239454189
	.long	1460268972
	.long	3184474466
	.long	1346899734
	.long	1110354113
	.long	145456861
	.long	1052864217
	.long	2924701438
	.long	1186120500
	.long	3023147446
	.long	3352707860
	.long	1486479387
	.long	1224406765
	.long	1193997181
	.long	3391133412
	.long	2679629000
	.long	1262791412
	.long	2670450976
	.long	3429499462
	.long	1831795719
	.long	1301315682
	.long	3473315319
	.long	3467974125
	.long	2616420704
	.long	1339759302
	.long	1949536880
	.long	3506536322
	.long	2518396463
	.long	1378264708
	.long	142869669
	.long	3545106524
	.long	804820494
	.long	1416958345
	.long	2985125801
	.long	3583414179
	.long	2729716642
	.long	3174389784
	.long	1779261930
	.long	3115790859
	.long	2784196366
	.long	3242962813
	.long	2825451289
	.long	3185986297
	.long	2032114899
	.long	1117254632
	.long	1505002334
	.long	1059738942
	.long	3277658686
	.long	1207035096
	.long	498115686
	.long	3377105433
	.long	1711172328
	.long	1252267855
	.long	261417437
	.long	3422480089
	.long	1133240684
	.long	1297616851
	.long	1456800292
	.long	3467832532
	.long	2145797446
	.long	1343095967
	.long	2516644728
	.long	3513252141
	.long	725820954
	.long	1388528302
	.long	3986994814
	.long	3558807006
	.long	1955265405
	.long	1434003000
	.long	122646123
	.long	3604272992
	.long	1491943337
	.long	1479689885
	.long	1036478018
	.long	3649599135
	.long	523859850
	.long	3181400160
	.long	3846079041
	.long	3121433884
	.long	3639230521
	.long	3246590119
	.long	2638130497
	.long	3191278647
	.long	2154057324
	.long	1124508819
	.long	3236410824
	.long	1069265219
	.long	175871336
	.long	1228815189
	.long	2852522281
	.long	3402552205
	.long	1806384070
	.long	1281362740
	.long	423509286
	.long	3455121177
	.long	1160294737
	.long	1333930482
	.long	3543120866
	.long	3507725506
	.long	1036273485
	.long	1386572737
	.long	2942188771
	.long	3560408527
	.long	2777944578
	.long	1439300670
	.long	2738100981
	.long	3613185349
	.long	527942274
	.long	1492126097
	.long	1294011372
	.long	3665970729
	.long	3274455694
	.long	1544947882
	.long	1889769429
	.long	3718530789
	.long	136073262
	.long	1038797747
	.long	502134597
	.long	982165232
	.long	4211775762
	.long	3250359547
	.long	1347037926
	.long	3194312374
	.long	543769047
	.long	1132034342
	.long	1153606166
	.long	1076402838
	.long	3293430877
	.long	1251313085
	.long	540290119
	.long	3428873621
	.long	1766589444
	.long	1311355635
	.long	3887512491
	.long	3488899482
	.long	4113271272
	.long	1371561389
	.long	1216453194
	.long	3549072511
	.long	761821280
	.long	1431680659
	.long	3412859739
	.long	3609321978
	.long	1736216465
	.long	1491968482
	.long	1379671921
	.long	3669534956
	.long	871564305
	.long	1552192257
	.long	3755076681
	.long	3729888559
	.long	465380849
	.long	1612696812
	.long	1801521440
	.long	3789943511
	.long	926050541
	.long	3187761455
	.long	2363428923
	.long	3130505121
	.long	3212464044
	.long	3254203020
	.long	1767044225
	.long	3196691034
	.long	767402551
	.long	1139855610
	.long	3251527812
	.long	1084244526
	.long	2568107110
	.long	1274594461
	.long	3742439917
	.long	3456026525
	.long	2828194370
	.long	1342408213
	.long	1036563767
	.long	3523815749
	.long	138125806
	.long	1410361976
	.long	3332253618
	.long	3591742210
	.long	2265969007
	.long	1478279541
	.long	2044799860
	.long	3659742729
	.long	3159788672
	.long	1546250274
	.long	3040087996
	.long	3727787101
	.long	3566786041
	.long	1614266310
	.long	3445702274
	.long	3795877266
	.long	1647738551
	.long	1682455451
	.long	820553625
	.long	3863686600
	.long	2466985759
	.long	1045839115
	.long	2115301394
	.long	988248469
	.long	789067623
	.long	3258207291
	.long	2594430310
	.long	3200300918
	.long	682953991
	.long	1147781119
	.long	334308353
	.long	1092099647
	.long	666163226
	.long	1298545479
	.long	1527735017
	.long	3483905174
	.long	43015025
	.long	1374344588
	.long	1895454381
	.long	3559801465
	.long	3313337462
	.long	1450264132
	.long	938251762
	.long	3635670048
	.long	2344246077
	.long	1526147814
	.long	3329926839
	.long	3711639890
	.long	2189826955
	.long	1602221552
	.long	307911546
	.long	3787647166
	.long	4271816317
	.long	1678143122
	.long	3455709496
	.long	3863691777
	.long	509127304
	.long	1754367541
	.long	2246034954
	.long	3939578533
	.long	309332673
	.long	3194846686
	.long	4050026421
	.long	3139306075
	.long	1677069013
	.long	3262318455
	.long	407665306
	.long	3207206552
	.long	156965555
	.long	1155965375
	.long	2320115828
	.long	1098438062
	.long	2734085477
	.long	1323184128
	.long	3116943360
	.long	3512649235
	.long	2683227306
	.long	1407189808
	.long	2374702532
	.long	3596676496
	.long	1369556142
	.long	1491213979
	.long	3682738445
	.long	3680737093
	.long	208207094
	.long	1575312310
	.long	1070212038
	.long	3764875962
	.long	1332751409
	.long	1659495819
	.long	2960239586
	.long	3849108041
	.long	1328259946
	.long	1743778590
	.long	2152673987
	.long	3933352648
	.long	2459568424
	.long	1828057070
	.long	1053026559
	.long	4017368800
	.long	4272716315
	.long	3202139688
	.long	2347479448
	.long	3145750572
	.long	3880933726
	.long	3266512750
	.long	1526392126
	.long	3209922805
	.long	3647913141
	.long	1164353962
	.long	3796124307
	.long	1109331951
	.long	3682674239
	.long	1348349871
	.long	2807065817
	.long	3542009267
	.long	2123995152
	.long	1440744183
	.long	2606508288
	.long	3634425164
	.long	2187838465
	.long	1533156938
	.long	3218070138
	.long	3726874341
	.long	3194985081
	.long	1625643843
	.long	1282672122
	.long	3819401778
	.long	3085269549
	.long	1718215913
	.long	4171823927
	.long	3912022409
	.long	2286527342
	.long	1810887222
	.long	4263058773
	.long	4004655668
	.long	276869381
	.long	1903554397
	.long	2981609009
	.long	4097060567
	.long	2069415824
	.long	3207114478
	.long	3815897610
	.long	3150201997
	.long	1799063985
	.long	3270784757
	.long	609928762
	.long	3210962773
	.long	1398608221
	.long	1172933154
	.long	2839394180
	.long	1117275773
	.long	3966862031
	.long	1374007527
	.long	3094795956
	.long	3571940711
	.long	201952387
	.long	1474951107
	.long	1218377639
	.long	3672975792
	.long	4171572408
	.long	1576047750
	.long	2433360787
	.long	3774024473
	.long	2872117748
	.long	1677069826
	.long	1125668053
	.long	3875125411
	.long	1838424462
	.long	1778265418
	.long	1923560800
	.long	3976316898
	.long	2192672438
	.long	1879376253
	.long	2795231406
	.long	4077480577
	.long	3134618972
	.long	1980778833
	.long	2542206299
	.long	4178570441
	.long	858317418
	.long	1060903333
	.long	3120786235
	.long	1004681431
	.long	381774867
	.long	3275145580
	.long	1575769843
	.long	3219836452
	.long	3230239600
	.long	1181757061
	.long	1694309796
	.long	1126994475
	.long	2041459926
	.long	1400246667
	.long	1834143305
	.long	3602573072
	.long	3431862699
	.long	1509997896
	.long	674489055
	.long	3712297330
	.long	2942256815
	.long	1619727956
	.long	2430104127
	.long	3822158540
	.long	1181772448
	.long	1729549373
	.long	3316918483
	.long	3932027016
	.long	2290692518
	.long	1839450757
	.long	717742192
	.long	4041883887
	.long	2723262413
	.long	1949398481
	.long	726994291
	.long	4151823214
	.long	2316520017
	.long	2059476550
	.long	3506192554
	.long	4261590717
	.long	2615285756
	.long	3215685247
	.long	3456994573
	.long	3158988097
	.long	2576980377
	.long	3279526297
	.long	457772008
	.long	3222963352
	.long	1202590842
	.long	1190427361
	.long	3150210795
	.long	1135356295
	.long	2163381170
	.long	1426480799
	.long	2353315751
	.long	3633229223
	.long	3794716365
	.long	1544911246
	.long	3598226895
	.long	3751709375
	.long	3778743776
	.long	1663434111
	.long	1038551128
	.long	3870293084
	.long	2177305923
	.long	1782019278
	.long	316150431
	.long	3988869748
	.long	3666573938
	.long	1900657340
	.long	1225202613
	.long	4107481261
	.long	4106613245
	.long	2019377222
	.long	3685171119
	.long	4226128894
	.long	3001847201
	.long	2137308914
	.long	4294967295
	.long	4293918719
	.type	_dbA,@object
	.size	_dbA,8032
	.space 672, 0x00 	# pad
	.align 4
.L_2il0floatpacket.22:
	.long	0x3f800000
	.type	.L_2il0floatpacket.22,@object
	.size	.L_2il0floatpacket.22,4
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_OFW_BOUND:
	.word	60404
	.word	65533
	.word	19595
	.word	47317
	.word	32753
	.word	0
	.word	0
	.word	0
	.type	_OFW_BOUND,@object
	.size	_OFW_BOUND,16
	.align 2
_TWO5600:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	21983
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	10783
	.word	0
	.word	0
	.word	0
	.type	_TWO5600,@object
	.size	_TWO5600,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
