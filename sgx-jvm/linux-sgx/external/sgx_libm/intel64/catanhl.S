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
	.file "catanhl.c"
	.text
..TXTST0:
# -- Begin  catanhl
	.text
       .align    16,0x90
	.globl catanhl
catanhl:
# parameter 1: 80 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_catanhl.1:
..L2:

        pushq     %rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
        subq      $64, %rsp
	.cfi_def_cfa_offset 80
        xorb      %bpl, %bpl
        fldt      .L_2il0floatpacket.17(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 48(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     32(%rsp)
..B1.2:
        fnstcw    2(%rsp)
..B1.3:
        movzwl    2(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, (%rsp)
..B1.5:
        fldcw     (%rsp)
..B1.6:
        movb      $1, %bpl
..B1.7:
        movzwl    88(%rsp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.14
..B1.8:
        testl     %edx, %edx
        jle       ..B1.35
..B1.9:
        movzwl    104(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.15
..B1.10:
        testl     %eax, %eax
        jle       ..B1.33
..B1.11:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 112
        fldt      112(%rsp)
        fstpt     (%rsp)
        fldt      128(%rsp)
        fstpt     16(%rsp)
..___tag_value_catanhl.7:
        call      recatanhl
..___tag_value_catanhl.8:
..B1.56:
        addq      $32, %rsp
	.cfi_def_cfa_offset 80
..B1.12:
        fstpt     16(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 112
        fldt      112(%rsp)
        fstpt     (%rsp)
        fldt      128(%rsp)
        fstpt     16(%rsp)
..___tag_value_catanhl.11:
        call      imcatanhl
..___tag_value_catanhl.12:
..B1.57:
        addq      $32, %rsp
	.cfi_def_cfa_offset 80
..B1.13:
        fstpt     32(%rsp)
        jmp       ..B1.28
..B1.14:
        movzwl    104(%rsp), %eax
        andl      $32767, %eax
..B1.15:
        cmpl      $32767, %edx
        je        ..B1.47
..B1.16:
        cmpl      $32767, %eax
        je        ..B1.41
..B1.17:
        cmpl      $32767, %eax
        jge       ..B1.27
..B1.18:
        cmpl      $0, 84(%rsp)
        jne       ..B1.20
..B1.19:
        cmpl      $0, 80(%rsp)
        je        ..B1.27
..B1.20:
        cmpl      $16383, %edx
        jge       ..B1.22
..B1.21:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 96
        fldt      96(%rsp)
        fstpt     (%rsp)
        call      atanhl@PLT
..B1.58:
        addq      $16, %rsp
	.cfi_def_cfa_offset 80
        lea       64+_CONSTANTS(%rip), %rax
        movb      105(%rsp), %dl
        lea       _CONSTANTS(%rip), %rcx
        andb      $-128, %dl
        fstpt     16(%rsp)
        fldt      (%rax)
        shrb      $7, %dl
        movzbl    %dl, %esi
        shlq      $4, %rsi
        fldt      (%rcx,%rsi)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.28
..B1.22:
        je        ..B1.38
..B1.23:
        cmpl      $32767, %edx
        jl        ..B1.25
..B1.24:
        movb      89(%rsp), %dl
        lea       64+_CONSTANTS(%rip), %rax
        andb      $-128, %dl
        lea       _CONSTANTS(%rip), %rdi
        shrb      $7, %dl
        fldt      (%rax)
        movzbl    %dl, %ecx
        shlq      $4, %rcx
        fldt      (%rdi,%rcx)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 96
        movb      121(%rsp), %sil
        andb      $-128, %sil
        shrb      $7, %sil
        movzbl    %sil, %r8d
        shlq      $4, %r8
        fldt      (%rdi,%r8)
        fstpt     (%rsp)
        call      asinl@PLT
        jmp       ..B1.62
	.cfi_def_cfa_offset 80
..B1.25:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 112
        fldt      112(%rsp)
        fstpt     (%rsp)
        fldt      128(%rsp)
        fstpt     16(%rsp)
..___tag_value_catanhl.19:
        call      recatanhl
..___tag_value_catanhl.20:
..B1.60:
        addq      $32, %rsp
	.cfi_def_cfa_offset 80
..B1.26:
        fstpt     16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 96
        movb      121(%rsp), %al
        lea       _CONSTANTS(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        shlq      $4, %rcx
        fldt      (%rdx,%rcx)
        fstpt     (%rsp)
        call      asinl@PLT
        jmp       ..B1.62
	.cfi_def_cfa_offset 80
..B1.27:
        movb      89(%rsp), %dl
        lea       64+_CONSTANTS(%rip), %rax
        andb      $-128, %dl
        lea       _CONSTANTS(%rip), %rcx
        shrb      $7, %dl
        fldt      (%rax)
        movzbl    %dl, %esi
        shlq      $4, %rsi
        fldt      (%rcx,%rsi)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 96
        fldt      112(%rsp)
        fstpt     (%rsp)
        call      atanl@PLT
..B1.62:
        addq      $16, %rsp
	.cfi_def_cfa_offset 80
        fstpt     32(%rsp)
..B1.28:
        testb     %bpl, %bpl
        je        ..B1.30
..B1.29:
        fldcw     2(%rsp)
..B1.30:
        movq      48(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.40
..B1.31:
        fldt      16(%rsp)
        fldt      32(%rsp)
        movq      48(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.39
..B1.32:
        addq      $64, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 6, -16
..B1.33:
        cmpl      $0, 100(%rsp)
        jne       ..B1.11
..B1.34:
        cmpl      $0, 96(%rsp)
        jne       ..B1.11
        jmp       ..B1.15
..B1.35:
        cmpl      $0, 84(%rsp)
        jne       ..B1.9
..B1.36:
        cmpl      $0, 80(%rsp)
        jne       ..B1.9
        jmp       ..B1.14
..B1.38:
        movq      $0x8000000000000000, %rax
        cmpq      80(%rsp), %rax
        je        ..B1.21
        jmp       ..B1.23
..B1.39:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.40:
        call      __stack_chk_fail@PLT
..B1.41:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.43
..B1.42:
        cmpl      $0, 96(%rsp)
        je        ..B1.17
..B1.43:
        cmpl      $32767, %edx
        jge       ..B1.17
..B1.44:
        cmpl      $0, 84(%rsp)
        jne       ..B1.46
..B1.45:
        cmpl      $0, 80(%rsp)
        je        ..B1.17
..B1.46:
        fldt      96(%rsp)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     32(%rsp)
        jmp       ..B1.28
..B1.47:
        cmpl      $-2147483648, 84(%rsp)
        jne       ..B1.49
..B1.48:
        cmpl      $0, 80(%rsp)
        je        ..B1.16
..B1.49:
        cmpl      $32767, %eax
        jge       ..B1.51
..B1.50:
        fldt      80(%rsp)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     32(%rsp)
        jmp       ..B1.28
..B1.51:
        fldt      80(%rsp)
        movq      $0x8000000000000000, %rax
        cmpq      96(%rsp), %rax
        je        ..B1.53
..B1.52:
        fmul      %st(0), %st
        fstpt     16(%rsp)
        fldt      96(%rsp)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        jmp       ..B1.28
..B1.53:
        fmul      %st(0), %st
        lea       64+_CONSTANTS(%rip), %rax
        fstpt     80(%rsp)
        fldt      (%rax)
        fstpt     16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 96
        fldt      112(%rsp)
        fstpt     (%rsp)
        call      atanl@PLT
        jmp       ..B1.62
        .align    16,0x90
	.cfi_endproc
	.type	catanhl,@function
	.size	catanhl,.-catanhl
	.data
# -- End  catanhl
	.text
# -- Begin  recatanhl
	.text
       .align    16,0x90
recatanhl:
# parameter 1: 576 + %rsp
# parameter 2: 592 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value_recatanhl.33:
..L34:

        subq      $568, %rsp
	.cfi_def_cfa_offset 576
        lea       64+_CONSTANTS(%rip), %rdx
        fldt      576(%rsp)
        lea       128(%rsp), %rdi
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        fldt      (%rdx)
        movq      %rax, 432(%rdi)
        movl      $0, 32(%rdi)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        fstpt     (%rdi)
        fstpt     16(%rdi)
..___tag_value_recatanhl.36:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.37:
..B2.2:
        fldt      592(%rsp)
        lea       176(%rsp), %rdi
        movl      $0, 32(%rdi)
        fstpt     (%rdi)
        fldt      (%rsp)
        fstpt     16(%rdi)
..___tag_value_recatanhl.38:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.39:
..B2.3:
        fldt      (%rsp)
        lea       _CONSTANTS(%rip), %rax
        movzwl    136(%rsp), %edx
        movl      %edx, %esi
        andl      $32767, %esi
        xorl      %r9d, %r9d
        movl      $0, 256(%rsp)
        cmpl      $8383, %esi
        fstpt     240(%rsp)
        fldt      (%rax)
        fstpt     224(%rsp)
        jl        ..B2.7
..B2.4:
        cmpl      $24383, %esi
        jg        ..B2.7
..B2.5:
        movzwl    232(%rsp), %r8d
        movl      %r8d, %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.7
..B2.6:
        cmpl      $24383, %ecx
        jle       ..B2.10
..B2.7:
        lea       128(%rsp), %rdi
..___tag_value_recatanhl.40:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.41:
..B2.8:
        lea       224(%rsp), %rdi
..___tag_value_recatanhl.42:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.43:
..B2.9:
        movzwl    136(%rsp), %edx
        movl      %edx, %esi
        movzwl    232(%rsp), %r8d
        movl      %r8d, %ecx
        movl      256(%rsp), %r9d
        andl      $32767, %esi
        andl      $32767, %ecx
..B2.10:
        movl      160(%rsp), %edi
        lea       -134(%r9,%rcx), %r10d
        lea       (%rsi,%rdi), %eax
        cmpl      %r10d, %eax
        jle       ..B2.27
..B2.11:
        movl      132(%rsp), %r11d
        lea       134(%r9,%rcx), %r10d
        cmpl      %r10d, %eax
        jge       ..B2.28
..B2.12:
        testl     %r11d, %r11d
        jne       ..B2.14
..B2.13:
        cmpl      $0, 128(%rsp)
        je        ..B2.29
..B2.14:
        cmpl      $0, 228(%rsp)
        jne       ..B2.16
..B2.15:
        cmpl      $0, 224(%rsp)
        je        ..B2.28
..B2.16:
        cmpl      %r9d, %edi
        je        ..B2.18
..B2.17:
        fldt      128(%rsp)
        subl      %r9d, %edi
        fldt      144(%rsp)
        addl      $16383, %edi
        andb      $127, 9(%rsp)
        andl      $32767, %edi
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 160(%rsp)
        fxch      %st(2)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movzwl    136(%rsp), %edx
        jmp       ..B2.19
..B2.18:
        fldt      128(%rsp)
        fldt      144(%rsp)
..B2.19:
        fldt      224(%rsp)
        andl      $32767, %edx
        andl      $32767, %r8d
        fld       %st(2)
        movl      %r9d, 304(%rsp)
        cmpl      %r8d, %edx
        fadd      %st(1), %st
        jg        ..B2.24
..B2.20:
        jne       ..B2.25
..B2.21:
        movl      132(%rsp), %eax
        movl      228(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.24
..B2.22:
        jne       ..B2.25
..B2.23:
        movl      128(%rsp), %eax
        cmpl      224(%rsp), %eax
        jbe       ..B2.25
..B2.24:
        fldt      240(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B2.26
..B2.25:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      240(%rsp)
        faddp     %st, %st(2)
..B2.26:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     288(%rsp)
        jmp       ..B2.33
..B2.27:
        movl      132(%rsp), %r11d
..B2.28:
        testl     %r11d, %r11d
        jne       ..B2.30
..B2.162:
        cmpl      $0, 128(%rsp)
..B2.29:
        je        ..B2.31
..B2.30:
        lea       -16517(%r9,%rcx), %eax
        lea       -16383(%rdi,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B2.32
..B2.31:
        movaps    224(%rsp), %xmm0
        movaps    240(%rsp), %xmm1
        movaps    256(%rsp), %xmm2
        movaps    %xmm0, 272(%rsp)
        movaps    %xmm1, 288(%rsp)
        movaps    %xmm2, 304(%rsp)
        jmp       ..B2.33
..B2.32:
        movaps    128(%rsp), %xmm0
        movaps    144(%rsp), %xmm1
        movaps    160(%rsp), %xmm2
        movaps    %xmm0, 272(%rsp)
        movaps    %xmm1, 288(%rsp)
        movaps    %xmm2, 304(%rsp)
..B2.33:
        movzwl    280(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.35
..B2.34:
        cmpl      $24383, %eax
        jle       ..B2.37
..B2.35:
        lea       272(%rsp), %rdi
..___tag_value_recatanhl.44:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.45:
..B2.36:
        lea       272(%rsp), %rdi
..___tag_value_recatanhl.46:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.47:
..B2.37:
        fldt      272(%rsp)
        lea       80+_CONSTANTS(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      288(%rsp)
        fmulp     %st, %st(4)
        movl      304(%rsp), %eax
        fxch      %st(3)
        fadd      %st(0), %st
        fldt      (%rdx)
        addl      %eax, %eax
        fmul      %st, %st(3)
        movl      %eax, 352(%rsp)
        fxch      %st(2)
        fsub      %st(3), %st
        movzwl    184(%rsp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(4)
        fmul      %st(4), %st
        cmpl      $8383, %eax
        fadd      %st(0), %st
        fxch      %st(2)
        fstpt     16(%rsp)
        fxch      %st(3)
        fmul      %st(0), %st
        fld       %st(1)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     320(%rsp)
        fldt      320(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     336(%rsp)
        jl        ..B2.39
..B2.38:
        cmpl      $24383, %eax
        jle       ..B2.41
..B2.39:
        lea       176(%rsp), %rdi
..___tag_value_recatanhl.48:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.49:
..B2.40:
        lea       176(%rsp), %rdi
..___tag_value_recatanhl.50:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.51:
..B2.41:
        fldt      176(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%rsp)
        fldt      192(%rsp)
        fmul      %st(4), %st
        movzwl    328(%rsp), %edx
        movl      %edx, %esi
        andl      $32767, %esi
        fadd      %st(0), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        movl      208(%rsp), %r9d
        fsubr     %st, %st(2)
        addl      %r9d, %r9d
        faddp     %st, %st(2)
        fld       %st(1)
        cmpl      $8383, %esi
        fmul      %st(2), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        movl      %r9d, 400(%rsp)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     368(%rsp)
        fldt      368(%rsp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     384(%rsp)
        fstpt     32(%rsp)
        jl        ..B2.45
..B2.42:
        cmpl      $24383, %esi
        jg        ..B2.45
..B2.43:
        movzwl    376(%rsp), %r8d
        movl      %r8d, %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.45
..B2.44:
        cmpl      $24383, %ecx
        jle       ..B2.48
..B2.45:
        lea       320(%rsp), %rdi
..___tag_value_recatanhl.52:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.53:
..B2.46:
        lea       368(%rsp), %rdi
..___tag_value_recatanhl.54:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.55:
..B2.47:
        movzwl    328(%rsp), %edx
        movl      %edx, %esi
        movzwl    376(%rsp), %r8d
        movl      %r8d, %ecx
        movl      400(%rsp), %r9d
        andl      $32767, %esi
        andl      $32767, %ecx
..B2.48:
        movl      352(%rsp), %edi
        lea       -134(%r9,%rcx), %r10d
        lea       (%rsi,%rdi), %eax
        cmpl      %r10d, %eax
        jle       ..B2.65
..B2.49:
        movl      324(%rsp), %r11d
        lea       134(%r9,%rcx), %r10d
        cmpl      %r10d, %eax
        jge       ..B2.66
..B2.50:
        testl     %r11d, %r11d
        jne       ..B2.52
..B2.51:
        cmpl      $0, 320(%rsp)
        je        ..B2.67
..B2.52:
        cmpl      $0, 372(%rsp)
        jne       ..B2.54
..B2.53:
        cmpl      $0, 368(%rsp)
        je        ..B2.66
..B2.54:
        cmpl      %r9d, %edi
        je        ..B2.56
..B2.55:
        fldt      32(%rsp)
        subl      %r9d, %edi
        fstpt     (%rsp)
        addl      $16383, %edi
        fldt      320(%rsp)
        andl      $32767, %edi
        fldt      336(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r9d, 352(%rsp)
        fxch      %st(2)
        fstpt     320(%rsp)
        fldt      320(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        movzwl    328(%rsp), %edx
        jmp       ..B2.57
..B2.56:
        fldt      320(%rsp)
        fstpt     32(%rsp)
        fldt      336(%rsp)
..B2.57:
        fldt      368(%rsp)
        andl      $32767, %edx
        fldt      32(%rsp)
        andl      $32767, %r8d
        movl      %r9d, 448(%rsp)
        cmpl      %r8d, %edx
        fadd      %st(1), %st
        jg        ..B2.62
..B2.58:
        jne       ..B2.63
..B2.59:
        movl      324(%rsp), %eax
        movl      372(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.62
..B2.60:
        jne       ..B2.63
..B2.61:
        movl      320(%rsp), %eax
        cmpl      368(%rsp), %eax
        jbe       ..B2.63
..B2.62:
        fldt      32(%rsp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      384(%rsp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B2.64
..B2.63:
        fldt      32(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      384(%rsp)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
..B2.64:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     432(%rsp)
        jmp       ..B2.71
..B2.65:
        movl      324(%rsp), %r11d
..B2.66:
        testl     %r11d, %r11d
        jne       ..B2.68
..B2.164:
        cmpl      $0, 320(%rsp)
..B2.67:
        je        ..B2.69
..B2.68:
        lea       -16517(%r9,%rcx), %eax
        lea       -16383(%rdi,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B2.70
..B2.69:
        movaps    368(%rsp), %xmm0
        movaps    384(%rsp), %xmm1
        movaps    400(%rsp), %xmm2
        movaps    %xmm0, 416(%rsp)
        movaps    %xmm1, 432(%rsp)
        movaps    %xmm2, 448(%rsp)
        jmp       ..B2.71
..B2.70:
        movaps    320(%rsp), %xmm0
        movaps    336(%rsp), %xmm1
        movaps    352(%rsp), %xmm2
        movaps    %xmm0, 416(%rsp)
        movaps    %xmm1, 432(%rsp)
        movaps    %xmm2, 448(%rsp)
..B2.71:
        movzwl    136(%rsp), %edx
        movl      %edx, %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B2.75
..B2.72:
        cmpl      $24383, %esi
        jg        ..B2.75
..B2.73:
        movzwl    232(%rsp), %r9d
        movl      %r9d, %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.75
..B2.74:
        cmpl      $24383, %ecx
        jle       ..B2.78
..B2.75:
        lea       128(%rsp), %rdi
..___tag_value_recatanhl.56:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.57:
..B2.76:
        lea       224(%rsp), %rdi
..___tag_value_recatanhl.58:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.59:
..B2.77:
        movzwl    136(%rsp), %edx
        movl      %edx, %esi
        movzwl    232(%rsp), %r9d
        movl      %r9d, %ecx
        andl      $32767, %esi
        andl      $32767, %ecx
..B2.78:
        movl      160(%rsp), %edi
        movl      256(%rsp), %r8d
        lea       (%rsi,%rdi), %eax
        lea       -134(%r8,%rcx), %r10d
        cmpl      %r10d, %eax
        jle       ..B2.95
..B2.79:
        movl      132(%rsp), %r11d
        lea       134(%r8,%rcx), %r10d
        cmpl      %r10d, %eax
        jge       ..B2.96
..B2.80:
        testl     %r11d, %r11d
        jne       ..B2.82
..B2.81:
        cmpl      $0, 128(%rsp)
        je        ..B2.97
..B2.82:
        cmpl      $0, 228(%rsp)
        jne       ..B2.84
..B2.83:
        cmpl      $0, 224(%rsp)
        je        ..B2.96
..B2.84:
        cmpl      %r8d, %edi
        je        ..B2.86
..B2.85:
        fldt      32(%rsp)
        subl      %r8d, %edi
        fstpt     (%rsp)
        addl      $16383, %edi
        fldt      128(%rsp)
        andl      $32767, %edi
        fldt      144(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r8d, 160(%rsp)
        fxch      %st(2)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        movzwl    136(%rsp), %edx
        jmp       ..B2.87
..B2.86:
        fldt      128(%rsp)
        fldt      144(%rsp)
..B2.87:
        fldt      224(%rsp)
        andl      $32767, %edx
        andl      $32767, %r9d
        fld       %st(2)
        movl      %r8d, 304(%rsp)
        cmpl      %r9d, %edx
        fsub      %st(1), %st
        jg        ..B2.92
..B2.88:
        jne       ..B2.93
..B2.89:
        movl      132(%rsp), %eax
        movl      228(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.92
..B2.90:
        jne       ..B2.93
..B2.91:
        movl      128(%rsp), %eax
        cmpl      224(%rsp), %eax
        jbe       ..B2.93
..B2.92:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      240(%rsp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B2.94
..B2.93:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      240(%rsp)
        fsubrp    %st, %st(2)
..B2.94:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     272(%rsp)
        fldt      272(%rsp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     288(%rsp)
        jmp       ..B2.101
..B2.95:
        movl      132(%rsp), %r11d
..B2.96:
        testl     %r11d, %r11d
        jne       ..B2.98
..B2.161:
        cmpl      $0, 128(%rsp)
..B2.97:
        je        ..B2.99
..B2.98:
        lea       -16517(%r8,%rcx), %eax
        lea       -16383(%rdi,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B2.100
..B2.99:
        fldt      224(%rsp)
        fchs      
        movl      %r8d, 304(%rsp)
        fstpt     272(%rsp)
        fldt      240(%rsp)
        fchs      
        fstpt     288(%rsp)
        jmp       ..B2.101
..B2.100:
        movaps    128(%rsp), %xmm0
        movaps    144(%rsp), %xmm1
        movaps    160(%rsp), %xmm2
        movaps    %xmm0, 272(%rsp)
        movaps    %xmm1, 288(%rsp)
        movaps    %xmm2, 304(%rsp)
..B2.101:
        movzwl    280(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.103
..B2.102:
        cmpl      $24383, %eax
        jle       ..B2.105
..B2.103:
        lea       272(%rsp), %rdi
..___tag_value_recatanhl.60:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.61:
..B2.104:
        lea       272(%rsp), %rdi
..___tag_value_recatanhl.62:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.63:
..B2.105:
        fldt      272(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      16(%rsp)
        fldt      288(%rsp)
        fxch      %st(1)
        fmul      %st(4), %st
        movl      304(%rsp), %edi
        fsubr     %st, %st(3)
        addl      %edi, %edi
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        movl      %edi, 352(%rsp)
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
        fstpt     320(%rsp)
        fldt      320(%rsp)
        fsubrp    %st, %st(2)
        movzwl    328(%rsp), %edx
        movl      %edx, %esi
        andl      $32767, %esi
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     336(%rsp)
        cmpl      $8383, %esi
        fstpt     32(%rsp)
        jl        ..B2.109
..B2.106:
        cmpl      $24383, %esi
        jg        ..B2.109
..B2.107:
        movzwl    376(%rsp), %r9d
        movl      %r9d, %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.109
..B2.108:
        cmpl      $24383, %ecx
        jle       ..B2.112
..B2.109:
        lea       320(%rsp), %rdi
..___tag_value_recatanhl.64:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.65:
..B2.110:
        lea       368(%rsp), %rdi
..___tag_value_recatanhl.66:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.67:
..B2.111:
        movzwl    328(%rsp), %edx
        movl      %edx, %esi
        movzwl    376(%rsp), %r9d
        movl      %r9d, %ecx
        movl      352(%rsp), %edi
        andl      $32767, %esi
        andl      $32767, %ecx
..B2.112:
        movl      400(%rsp), %r8d
        lea       (%rdi,%rsi), %eax
        lea       -134(%r8,%rcx), %r10d
        cmpl      %r10d, %eax
        jle       ..B2.129
..B2.113:
        movl      324(%rsp), %r11d
        lea       134(%r8,%rcx), %r10d
        cmpl      %r10d, %eax
        jge       ..B2.130
..B2.114:
        testl     %r11d, %r11d
        jne       ..B2.116
..B2.115:
        cmpl      $0, 320(%rsp)
        je        ..B2.131
..B2.116:
        cmpl      $0, 372(%rsp)
        jne       ..B2.118
..B2.117:
        cmpl      $0, 368(%rsp)
        je        ..B2.130
..B2.118:
        cmpl      %r8d, %edi
        je        ..B2.120
..B2.119:
        fldt      32(%rsp)
        subl      %r8d, %edi
        fstpt     (%rsp)
        addl      $16383, %edi
        fldt      320(%rsp)
        andl      $32767, %edi
        fldt      336(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r8d, 352(%rsp)
        fxch      %st(2)
        fstpt     320(%rsp)
        fldt      320(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     336(%rsp)
        fldt      336(%rsp)
        movzwl    328(%rsp), %edx
        jmp       ..B2.121
..B2.120:
        fldt      320(%rsp)
        fldt      336(%rsp)
..B2.121:
        fldt      368(%rsp)
        andl      $32767, %edx
        andl      $32767, %r9d
        fld       %st(2)
        movl      %r8d, 496(%rsp)
        cmpl      %r9d, %edx
        fadd      %st(1), %st
        jg        ..B2.126
..B2.122:
        jne       ..B2.127
..B2.123:
        movl      324(%rsp), %eax
        movl      372(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B2.126
..B2.124:
        jne       ..B2.127
..B2.125:
        movl      320(%rsp), %eax
        cmpl      368(%rsp), %eax
        jbe       ..B2.127
..B2.126:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      384(%rsp)
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
        fldt      384(%rsp)
        faddp     %st, %st(1)
..B2.128:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     464(%rsp)
        fldt      464(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     480(%rsp)
        jmp       ..B2.135
..B2.129:
        movl      324(%rsp), %r11d
..B2.130:
        testl     %r11d, %r11d
        jne       ..B2.132
..B2.163:
        cmpl      $0, 320(%rsp)
..B2.131:
        je        ..B2.133
..B2.132:
        lea       -16517(%r8,%rcx), %eax
        lea       -16383(%rdi,%rsi), %edx
        cmpl      %edx, %eax
        jl        ..B2.134
..B2.133:
        movaps    368(%rsp), %xmm0
        movaps    384(%rsp), %xmm1
        movaps    400(%rsp), %xmm2
        movaps    %xmm0, 464(%rsp)
        movaps    %xmm1, 480(%rsp)
        movaps    %xmm2, 496(%rsp)
        jmp       ..B2.135
..B2.134:
        movaps    320(%rsp), %xmm0
        movaps    336(%rsp), %xmm1
        movaps    352(%rsp), %xmm2
        movl      352(%rsp), %r8d
        movaps    %xmm0, 464(%rsp)
        movaps    %xmm1, 480(%rsp)
        movaps    %xmm2, 496(%rsp)
..B2.135:
        movzwl    424(%rsp), %ecx
        movzwl    472(%rsp), %edi
        andl      $32767, %ecx
        andl      $32767, %edi
        movl      448(%rsp), %esi
        lea       (%rsi,%rcx), %edx
        lea       (%r8,%rdi), %eax
        subl      %eax, %edx
        cmpl      $-1, %edx
        jge       ..B2.145
..B2.136:
        cmpl      $8383, %ecx
        jl        ..B2.140
..B2.137:
        cmpl      $24383, %ecx
        jg        ..B2.140
..B2.138:
        cmpl      $8383, %edi
        jl        ..B2.140
..B2.139:
        cmpl      $24383, %edi
        jle       ..B2.143
..B2.140:
        lea       416(%rsp), %rdi
..___tag_value_recatanhl.68:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.69:
..B2.141:
        lea       464(%rsp), %rdi
..___tag_value_recatanhl.70:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.71:
..B2.142:
        movl      448(%rsp), %esi
        movl      496(%rsp), %r8d
..B2.143:
        fldt      464(%rsp)
        subl      %r8d, %esi
        fld       %st(0)
        fld       %st(1)
        fldt      416(%rsp)
        lea       512(%rsp), %rdi
        fld       %st(0)
        fdiv      %st(4), %st
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        movl      %esi, 64(%rsp)
        lea       32(%rsp), %rsi
        fsubr     %st(3), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(3), %st
        fxch      %st(2)
        fmul      %st(7), %st
        fsubr     %st, %st(6)
        faddp     %st, %st(6)
        fxch      %st(4)
        fsub      %st(5), %st
        fld       %st(4)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(6)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(1)
        fldt      400(%rsi)
        faddp     %st, %st(1)
        fldt      448(%rsi)
        fmul      %st(2), %st
        fsubrp    %st, %st(1)
        fdivp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rsi)
        fldt      (%rsi)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsi)
..___tag_value_recatanhl.72:
        call      __libm_log_k80@PLT
..___tag_value_recatanhl.73:
        jmp       ..B2.153
..B2.145:
        movzwl    136(%rsp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.149
..B2.146:
        cmpl      $24383, %eax
        jg        ..B2.149
..B2.147:
        cmpl      $8383, %edi
        jl        ..B2.149
..B2.148:
        cmpl      $24383, %edi
        jle       ..B2.152
..B2.149:
        lea       128(%rsp), %rdi
..___tag_value_recatanhl.74:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.75:
..B2.150:
        lea       464(%rsp), %rdi
..___tag_value_recatanhl.76:
        call      __libm_normalizel_k80@PLT
..___tag_value_recatanhl.77:
..B2.151:
        movl      496(%rsp), %r8d
..B2.152:
        fldt      128(%rsp)
        lea       512(%rsp), %rdi
        fld       %st(0)
        lea       80(%rsp), %rsi
        fldt      384(%rsi)
        fdivr     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fldt      -64(%rsi)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(0)
        movl      80(%rsi), %eax
        fsubr     %st(6), %st
        subl      %r8d, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        addl      $2, %eax
        movl      %eax, 32(%rsi)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fxch      %st(2)
        fsub      %st(3), %st
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      64(%rsi)
        faddp     %st, %st(3)
        fldt      400(%rsi)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rsi)
        fldt      (%rsi)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     16(%rsi)
        fstpt     (%rsp)
..___tag_value_recatanhl.78:
        call      __libm_log1p_k80@PLT
..___tag_value_recatanhl.79:
..B2.153:
        movl      544(%rsp), %edi
        addl      $-2, %edi
        movl      %edi, 544(%rsp)
..B2.154:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 608
        fldt      544(%rsp)
        fstpt     (%rsp)
        fldt      560(%rsp)
        fstpt     16(%rsp)
..___tag_value_recatanhl.81:
        call      __libm_scalbl_k80@PLT
..___tag_value_recatanhl.82:
..B2.160:
        addq      $32, %rsp
	.cfi_def_cfa_offset 576
..B2.155:
        movq      560(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B2.157
..B2.156:
        addq      $568, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 576
..B2.157:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	recatanhl,@function
	.size	recatanhl,.-recatanhl
	.data
# -- End  recatanhl
	.text
# -- Begin  imcatanhl
	.text
       .align    16,0x90
imcatanhl:
# parameter 1: 656 + %rsp
# parameter 2: 672 + %rsp
..B3.1:
	.cfi_startproc
..___tag_value_imcatanhl.87:
..L88:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        subq      $632, %rsp
	.cfi_def_cfa_offset 656
        lea       _CONSTANTS(%rip), %rbx
        fldt      656(%rsp)
        lea       64+_CONSTANTS(%rip), %r9
        movb      665(%rsp), %sil
        lea       288(%rsp), %rdi
        andb      $-128, %sil
        shrb      $7, %sil
        fldt      (%r9)
        movb      681(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %sil, %r8d
        shlq      $4, %r8
        movzbl    %dl, %ecx
        shlq      $4, %rcx
        movq      %fs:40, %rax
        fldt      (%rbx,%r8)
        xorq      %rsp, %rax
        fmulp     %st, %st(2)
        fldt      32(%rbx,%rcx)
        movq      %rax, 336(%rdi)
        fstpt     16(%rsp)
        movl      $0, 32(%rdi)
        fxch      %st(1)
        fstpt     (%rdi)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     16(%rdi)
..___tag_value_imcatanhl.94:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.95:
..B3.2:
        fldt      672(%rsp)
        lea       336(%rsp), %rdi
        fldt      (%rsp)
        movb      681(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fstpt     16(%rdi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        movl      $0, 32(%rdi)
        fldt      (%rbx,%rdx)
        fmulp     %st, %st(1)
        fstpt     (%rdi)
..___tag_value_imcatanhl.96:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.97:
..B3.3:
        fldt      (%rbx)
        movzwl    296(%rsp), %eax
        andl      $32767, %eax
        movl      $0, 416(%rsp)
        cmpl      $8383, %eax
        fstpt     384(%rsp)
        fldt      (%rsp)
        fstpt     400(%rsp)
        jl        ..B3.5
..B3.4:
        cmpl      $24383, %eax
        jle       ..B3.7
..B3.5:
        lea       288(%rsp), %rdi
..___tag_value_imcatanhl.98:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.99:
..B3.6:
        lea       288(%rsp), %rdi
..___tag_value_imcatanhl.100:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.101:
..B3.7:
        fldt      288(%rsp)
        lea       80+_CONSTANTS(%rip), %rdx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fld       %st(3)
        movl      320(%rsp), %eax
        fldt      (%rdx)
        addl      %eax, %eax
        fmul      %st, %st(4)
        movl      %eax, 464(%rsp)
        fxch      %st(3)
        fsubr     %st(4), %st
        fld       %st(0)
        fchs      
        movzwl    344(%rsp), %eax
        fadd      %st(5), %st
        fld       %st(0)
        andl      $32767, %eax
        fxch      %st(5)
        fstpt     (%rsp)
        cmpl      $8383, %eax
        fxch      %st(1)
        fsubrp    %st, %st(5)
        fxch      %st(3)
        fmul      %st(4), %st
        fxch      %st(2)
        fsub      %st(3), %st
        fxch      %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      304(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     432(%rsp)
        fldt      432(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     448(%rsp)
        jl        ..B3.9
..B3.8:
        cmpl      $24383, %eax
        jle       ..B3.11
..B3.9:
        lea       336(%rsp), %rdi
..___tag_value_imcatanhl.102:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.103:
..B3.10:
        lea       336(%rsp), %rdi
..___tag_value_imcatanhl.104:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.105:
..B3.11:
        fldt      336(%rsp)
        fld       %st(0)
        fld       %st(1)
        fldt      (%rsp)
        fmul      %st(3), %st
        movzwl    392(%rsp), %esi
        movl      %esi, %r10d
        andl      $32767, %r10d
        fsubr     %st, %st(2)
        movl      368(%rsp), %eax
        faddp     %st, %st(2)
        fld       %st(1)
        addl      %eax, %eax
        fmul      %st(2), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        cmpl      $8383, %r10d
        movl      %eax, 512(%rsp)
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
        fldt      352(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     480(%rsp)
        fldt      480(%rsp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     496(%rsp)
        fstpt     32(%rsp)
        jl        ..B3.15
..B3.12:
        cmpl      $24383, %r10d
        jg        ..B3.15
..B3.13:
        movzwl    440(%rsp), %edi
        movl      %edi, %r9d
        andl      $32767, %r9d
        cmpl      $8383, %r9d
        jl        ..B3.15
..B3.14:
        cmpl      $24383, %r9d
        jle       ..B3.18
..B3.15:
        lea       384(%rsp), %rdi
..___tag_value_imcatanhl.106:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.107:
..B3.16:
        lea       432(%rsp), %rdi
..___tag_value_imcatanhl.108:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.109:
..B3.17:
        movzwl    392(%rsp), %esi
        movl      %esi, %r10d
        movzwl    440(%rsp), %edi
        movl      %edi, %r9d
        andl      $32767, %r10d
        andl      $32767, %r9d
..B3.18:
        movl      416(%rsp), %eax
        movl      464(%rsp), %edx
        lea       (%r10,%rax), %ebx
        lea       -134(%rdx,%r9), %ecx
        cmpl      %ecx, %ebx
        jle       ..B3.35
..B3.19:
        movl      388(%rsp), %r8d
        lea       134(%rdx,%r9), %ecx
        cmpl      %ecx, %ebx
        jge       ..B3.36
..B3.20:
        testl     %r8d, %r8d
        jne       ..B3.22
..B3.21:
        cmpl      $0, 384(%rsp)
        je        ..B3.37
..B3.22:
        cmpl      $0, 436(%rsp)
        jne       ..B3.24
..B3.23:
        cmpl      $0, 432(%rsp)
        je        ..B3.36
..B3.24:
        cmpl      %edx, %eax
        je        ..B3.26
..B3.25:
        fldt      32(%rsp)
        subl      %edx, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      384(%rsp)
        andl      $32767, %eax
        fldt      400(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 416(%rsp)
        fxch      %st(2)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        movzwl    392(%rsp), %esi
        jmp       ..B3.27
..B3.26:
        fldt      384(%rsp)
        fstpt     32(%rsp)
        fldt      400(%rsp)
..B3.27:
        fldt      432(%rsp)
        andl      $32767, %esi
        fldt      32(%rsp)
        andl      $32767, %edi
        movl      %edx, 560(%rsp)
        cmpl      %edi, %esi
        fsub      %st(1), %st
        jg        ..B3.32
..B3.28:
        jne       ..B3.33
..B3.29:
        movl      388(%rsp), %eax
        movl      436(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B3.32
..B3.30:
        jne       ..B3.33
..B3.31:
        movl      384(%rsp), %eax
        cmpl      432(%rsp), %eax
        jbe       ..B3.33
..B3.32:
        fldt      32(%rsp)
        fsub      %st(1), %st
        fsubp     %st, %st(2)
        fldt      448(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B3.34
..B3.33:
        fldt      32(%rsp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      448(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
..B3.34:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     528(%rsp)
        fldt      528(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     544(%rsp)
        jmp       ..B3.41
..B3.35:
        movl      388(%rsp), %r8d
..B3.36:
        testl     %r8d, %r8d
        jne       ..B3.38
..B3.179:
        cmpl      $0, 384(%rsp)
..B3.37:
        je        ..B3.39
..B3.38:
        lea       -16517(%rdx,%r9), %ecx
        lea       -16383(%rax,%r10), %eax
        cmpl      %eax, %ecx
        jl        ..B3.40
..B3.39:
        fldt      432(%rsp)
        fchs      
        movl      %edx, 560(%rsp)
        fstpt     528(%rsp)
        fldt      448(%rsp)
        fchs      
        fstpt     544(%rsp)
        jmp       ..B3.41
..B3.40:
        movaps    384(%rsp), %xmm0
        movaps    400(%rsp), %xmm1
        movaps    416(%rsp), %xmm2
        movaps    %xmm0, 528(%rsp)
        movaps    %xmm1, 544(%rsp)
        movaps    %xmm2, 560(%rsp)
..B3.41:
        movzwl    536(%rsp), %esi
        movl      %esi, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B3.45
..B3.42:
        cmpl      $24383, %r10d
        jg        ..B3.45
..B3.43:
        movzwl    488(%rsp), %edi
        movl      %edi, %r9d
        andl      $32767, %r9d
        cmpl      $8383, %r9d
        jl        ..B3.45
..B3.44:
        cmpl      $24383, %r9d
        jle       ..B3.48
..B3.45:
        lea       528(%rsp), %rdi
..___tag_value_imcatanhl.110:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.111:
..B3.46:
        lea       480(%rsp), %rdi
..___tag_value_imcatanhl.112:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.113:
..B3.47:
        movzwl    536(%rsp), %esi
        movl      %esi, %r10d
        movzwl    488(%rsp), %edi
        movl      %edi, %r9d
        andl      $32767, %r10d
        andl      $32767, %r9d
..B3.48:
        movl      512(%rsp), %edx
        movl      560(%rsp), %eax
        lea       -134(%rdx,%r9), %ecx
        lea       (%r10,%rax), %ebx
        cmpl      %ecx, %ebx
        jle       ..B3.65
..B3.49:
        movl      532(%rsp), %r8d
        lea       134(%rdx,%r9), %ecx
        cmpl      %ecx, %ebx
        jge       ..B3.66
..B3.50:
        testl     %r8d, %r8d
        jne       ..B3.52
..B3.51:
        cmpl      $0, 528(%rsp)
        je        ..B3.67
..B3.52:
        cmpl      $0, 484(%rsp)
        jne       ..B3.54
..B3.53:
        cmpl      $0, 480(%rsp)
        je        ..B3.66
..B3.54:
        cmpl      %edx, %eax
        je        ..B3.56
..B3.55:
        fldt      32(%rsp)
        subl      %edx, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      528(%rsp)
        andl      $32767, %eax
        fldt      544(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edx, 560(%rsp)
        fxch      %st(2)
        fstpt     528(%rsp)
        fldt      528(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     544(%rsp)
        fldt      544(%rsp)
        fstpt     32(%rsp)
        movzwl    536(%rsp), %esi
        jmp       ..B3.57
..B3.56:
        fldt      528(%rsp)
        fldt      544(%rsp)
        fstpt     32(%rsp)
..B3.57:
        fldt      480(%rsp)
        andl      $32767, %esi
        andl      $32767, %edi
        fld       %st(1)
        movl      %edx, 608(%rsp)
        cmpl      %edi, %esi
        fsub      %st(1), %st
        jg        ..B3.62
..B3.58:
        jne       ..B3.63
..B3.59:
        movl      532(%rsp), %eax
        movl      484(%rsp), %ecx
        cmpl      %ecx, %eax
        ja        ..B3.62
..B3.60:
        jne       ..B3.63
..B3.61:
        movl      528(%rsp), %eax
        cmpl      480(%rsp), %eax
        jbe       ..B3.63
..B3.62:
        fldt      32(%rsp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      496(%rsp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
        jmp       ..B3.64
..B3.63:
        fldt      496(%rsp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      32(%rsp)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
..B3.64:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     576(%rsp)
        fldt      576(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     592(%rsp)
        jmp       ..B3.71
..B3.65:
        movl      532(%rsp), %r8d
..B3.66:
        testl     %r8d, %r8d
        jne       ..B3.68
..B3.180:
        cmpl      $0, 528(%rsp)
..B3.67:
        je        ..B3.69
..B3.68:
        lea       -16517(%rdx,%r9), %ecx
        lea       -16383(%rax,%r10), %ebx
        cmpl      %ebx, %ecx
        jl        ..B3.70
..B3.69:
        fldt      480(%rsp)
        fchs      
        movl      %edx, 608(%rsp)
        fstpt     576(%rsp)
        fldt      496(%rsp)
        fchs      
        fstpt     592(%rsp)
        jmp       ..B3.71
..B3.70:
        movaps    528(%rsp), %xmm0
        movl      %eax, %edx
        movaps    544(%rsp), %xmm1
        movaps    560(%rsp), %xmm2
        movaps    %xmm0, 576(%rsp)
        movaps    %xmm1, 592(%rsp)
        movaps    %xmm2, 608(%rsp)
..B3.71:
        movzwl    584(%rsp), %eax
        andl      $32767, %eax
        lea       -16383(%rdx,%rax), %edx
        cmpl      $-6, %edx
        jge       ..B3.164
..B3.72:
        movzwl    392(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B3.76
..B3.73:
        cmpl      $24383, %edx
        jg        ..B3.76
..B3.74:
        movzwl    296(%rsp), %ecx
        movl      %ecx, %r9d
        andl      $32767, %r9d
        cmpl      $8383, %r9d
        jl        ..B3.76
..B3.75:
        cmpl      $24383, %r9d
        jle       ..B3.79
..B3.76:
        lea       384(%rsp), %rdi
..___tag_value_imcatanhl.114:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.115:
..B3.77:
        lea       288(%rsp), %rdi
..___tag_value_imcatanhl.116:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.117:
..B3.78:
        movzwl    296(%rsp), %ecx
        movl      %ecx, %r9d
        movzwl    392(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %r9d
        andl      $32767, %edx
..B3.79:
        movl      320(%rsp), %r8d
        movl      416(%rsp), %edi
        lea       -134(%r8,%r9), %ebx
        lea       (%rdx,%rdi), %r10d
        cmpl      %r10d, %ebx
        lea       (%r9,%r8), %esi
        jge       ..B3.96
..B3.80:
        movl      388(%rsp), %r13d
        lea       134(%r8,%r9), %r11d
        cmpl      %r11d, %r10d
        jge       ..B3.97
..B3.81:
        testl     %r13d, %r13d
        jne       ..B3.83
..B3.82:
        cmpl      $0, 384(%rsp)
        je        ..B3.98
..B3.83:
        cmpl      $0, 292(%rsp)
        jne       ..B3.85
..B3.84:
        cmpl      $0, 288(%rsp)
        je        ..B3.97
..B3.85:
        cmpl      %r8d, %edi
        je        ..B3.87
..B3.86:
        fldt      32(%rsp)
        subl      %r8d, %edi
        fstpt     (%rsp)
        addl      $16383, %edi
        fldt      384(%rsp)
        andl      $32767, %edi
        fldt      400(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movl      %r8d, %edi
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    392(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
        movl      %r8d, 416(%rsp)
        movl      %edx, %r10d
        fstpt     400(%rsp)
        fldt      400(%rsp)
        jmp       ..B3.88
..B3.87:
        fldt      384(%rsp)
        movl      %eax, %r10d
        fldt      400(%rsp)
        andl      $32767, %r10d
..B3.88:
        fldt      288(%rsp)
        movl      %ecx, %r11d
        andl      $32767, %r11d
        fld       %st(2)
        movl      %r8d, 80(%rsp)
        cmpl      %r11d, %r10d
        fadd      %st(1), %st
        jg        ..B3.93
..B3.89:
        jne       ..B3.94
..B3.90:
        movl      388(%rsp), %r10d
        movl      292(%rsp), %r11d
        cmpl      %r11d, %r10d
        ja        ..B3.93
..B3.91:
        jne       ..B3.94
..B3.92:
        movl      384(%rsp), %r10d
        cmpl      288(%rsp), %r10d
        jbe       ..B3.94
..B3.93:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      304(%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B3.95
..B3.94:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      304(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
..B3.95:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     64(%rsp)
        jmp       ..B3.102
..B3.96:
        movl      388(%rsp), %r13d
..B3.97:
        testl     %r13d, %r13d
        jne       ..B3.99
..B3.178:
        cmpl      $0, 384(%rsp)
..B3.98:
        je        ..B3.100
..B3.99:
        lea       -16517(%r8,%r9), %r10d
        lea       -16383(%rdi,%rdx), %r11d
        cmpl      %r11d, %r10d
        jl        ..B3.101
..B3.100:
        movaps    288(%rsp), %xmm0
        movaps    304(%rsp), %xmm1
        movaps    320(%rsp), %xmm2
        movaps    %xmm0, 48(%rsp)
        movaps    %xmm1, 64(%rsp)
        movaps    %xmm2, 80(%rsp)
        jmp       ..B3.102
..B3.101:
        movaps    384(%rsp), %xmm0
        movaps    400(%rsp), %xmm1
        movaps    416(%rsp), %xmm2
        movaps    %xmm0, 48(%rsp)
        movaps    %xmm1, 64(%rsp)
        movaps    %xmm2, 80(%rsp)
..B3.102:
        cmpl      $8383, %edx
        jl        ..B3.106
..B3.103:
        cmpl      $24383, %edx
        jg        ..B3.106
..B3.104:
        cmpl      $8383, %r9d
        jl        ..B3.106
..B3.105:
        cmpl      $24383, %r9d
        jle       ..B3.109
..B3.106:
        lea       384(%rsp), %rdi
..___tag_value_imcatanhl.118:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.119:
..B3.107:
        lea       288(%rsp), %rdi
..___tag_value_imcatanhl.120:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.121:
..B3.108:
        movzwl    296(%rsp), %ecx
        movl      %ecx, %eax
        andl      $32767, %eax
        movl      320(%rsp), %r8d
        movl      416(%rsp), %edi
        lea       (%r8,%rax), %esi
        lea       -134(%r8,%rax), %ebx
        movzwl    392(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
..B3.109:
        lea       (%rdi,%rdx), %r9d
        cmpl      %r9d, %ebx
        jge       ..B3.126
..B3.110:
        movl      388(%rsp), %r10d
        lea       134(%rsi), %ebx
        cmpl      %ebx, %r9d
        jge       ..B3.127
..B3.111:
        testl     %r10d, %r10d
        jne       ..B3.113
..B3.112:
        cmpl      $0, 384(%rsp)
        je        ..B3.128
..B3.113:
        cmpl      $0, 292(%rsp)
        jne       ..B3.115
..B3.114:
        cmpl      $0, 288(%rsp)
        je        ..B3.127
..B3.115:
        cmpl      %r8d, %edi
        je        ..B3.117
..B3.116:
        fldt      32(%rsp)
        subl      %r8d, %edi
        fstpt     (%rsp)
        addl      $16383, %edi
        fldt      384(%rsp)
        andl      $32767, %edi
        fldt      400(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %r8d, 416(%rsp)
        fxch      %st(2)
        fstpt     384(%rsp)
        fldt      384(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     400(%rsp)
        fldt      400(%rsp)
        fxch      %st(1)
        fstpt     32(%rsp)
        movzwl    392(%rsp), %eax
        jmp       ..B3.118
..B3.117:
        fldt      384(%rsp)
        fstpt     32(%rsp)
        fldt      400(%rsp)
..B3.118:
        fldt      288(%rsp)
        andl      $32767, %eax
        fldt      32(%rsp)
        andl      $32767, %ecx
        movl      %r8d, 128(%rsp)
        cmpl      %ecx, %eax
        fsub      %st(1), %st
        jg        ..B3.123
..B3.119:
        jne       ..B3.124
..B3.120:
        movl      388(%rsp), %eax
        movl      292(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B3.123
..B3.121:
        jne       ..B3.124
..B3.122:
        movl      384(%rsp), %eax
        cmpl      288(%rsp), %eax
        jbe       ..B3.124
..B3.123:
        fldt      32(%rsp)
        fsub      %st(1), %st
        fsubp     %st, %st(2)
        fldt      304(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B3.125
..B3.124:
        fldt      32(%rsp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      304(%rsp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%rsp)
..B3.125:
        fldt      32(%rsp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     112(%rsp)
        jmp       ..B3.132
..B3.126:
        movl      388(%rsp), %r10d
..B3.127:
        testl     %r10d, %r10d
        jne       ..B3.129
..B3.177:
        cmpl      $0, 384(%rsp)
..B3.128:
        je        ..B3.130
..B3.129:
        addl      $-16517, %esi
        lea       -16383(%rdi,%rdx), %eax
        cmpl      %eax, %esi
        jl        ..B3.131
..B3.130:
        fldt      288(%rsp)
        fchs      
        movl      %r8d, 128(%rsp)
        fstpt     96(%rsp)
        fldt      304(%rsp)
        fchs      
        fstpt     112(%rsp)
        jmp       ..B3.132
..B3.131:
        movaps    384(%rsp), %xmm0
        movaps    400(%rsp), %xmm1
        movaps    416(%rsp), %xmm2
        movaps    %xmm0, 96(%rsp)
        movaps    %xmm1, 112(%rsp)
        movaps    %xmm2, 128(%rsp)
..B3.132:
        lea       144(%rsp), %rdi
        lea       336(%rsp), %rsi
        lea       48(%rsp), %rdx
..___tag_value_imcatanhl.122:
        call      __libm_atan2l_k80@PLT
..___tag_value_imcatanhl.123:
..B3.133:
        lea       192(%rsp), %rdi
        lea       336(%rsp), %rsi
        lea       96(%rsp), %rdx
..___tag_value_imcatanhl.124:
        call      __libm_atan2l_k80@PLT
..___tag_value_imcatanhl.125:
..B3.134:
        movzwl    152(%rsp), %ebx
        movl      %ebx, %r10d
        andl      $32767, %r10d
        cmpl      $8383, %r10d
        jl        ..B3.138
..B3.135:
        cmpl      $24383, %r10d
        jg        ..B3.138
..B3.136:
        movzwl    200(%rsp), %esi
        movl      %esi, %r9d
        andl      $32767, %r9d
        cmpl      $8383, %r9d
        jl        ..B3.138
..B3.137:
        cmpl      $24383, %r9d
        jle       ..B3.141
..B3.138:
        lea       144(%rsp), %rdi
..___tag_value_imcatanhl.126:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.127:
..B3.139:
        lea       192(%rsp), %rdi
..___tag_value_imcatanhl.128:
        call      __libm_normalizel_k80@PLT
..___tag_value_imcatanhl.129:
..B3.140:
        movzwl    152(%rsp), %ebx
        movl      %ebx, %r10d
        movzwl    200(%rsp), %esi
        movl      %esi, %r9d
        andl      $32767, %r10d
        andl      $32767, %r9d
..B3.141:
        movl      224(%rsp), %edi
        movl      176(%rsp), %eax
        lea       -134(%rdi,%r9), %edx
        lea       (%r10,%rax), %ecx
        cmpl      %edx, %ecx
        jle       ..B3.158
..B3.142:
        movl      148(%rsp), %r8d
        lea       134(%rdi,%r9), %edx
        cmpl      %edx, %ecx
        jge       ..B3.159
..B3.143:
        testl     %r8d, %r8d
        jne       ..B3.145
..B3.144:
        cmpl      $0, 144(%rsp)
        je        ..B3.160
..B3.145:
        cmpl      $0, 196(%rsp)
        jne       ..B3.147
..B3.146:
        cmpl      $0, 192(%rsp)
        je        ..B3.159
..B3.147:
        cmpl      %edi, %eax
        je        ..B3.149
..B3.148:
        fldt      32(%rsp)
        subl      %edi, %eax
        fstpt     (%rsp)
        addl      $16383, %eax
        fldt      144(%rsp)
        andl      $32767, %eax
        fldt      160(%rsp)
        andb      $127, 9(%rsp)
        movzwl    8(%rsp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 8(%rsp)
        movl      $-2147483648, 4(%rsp)
        movl      $0, (%rsp)
        fldt      (%rsp)
        fmul      %st, %st(2)
        movl      %edi, 176(%rsp)
        fxch      %st(2)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        movzwl    152(%rsp), %ebx
        jmp       ..B3.150
..B3.149:
        fldt      144(%rsp)
        fldt      160(%rsp)
..B3.150:
        fldt      192(%rsp)
        andl      $32767, %ebx
        andl      $32767, %esi
        fld       %st(2)
        movl      %edi, 272(%rsp)
        cmpl      %esi, %ebx
        fadd      %st(1), %st
        jg        ..B3.155
..B3.151:
        jne       ..B3.156
..B3.152:
        movl      148(%rsp), %eax
        movl      196(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B3.155
..B3.153:
        jne       ..B3.156
..B3.154:
        movl      144(%rsp), %eax
        cmpl      192(%rsp), %eax
        jbe       ..B3.156
..B3.155:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      208(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        jmp       ..B3.157
..B3.156:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      208(%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
..B3.157:
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B3.167
..B3.158:
        movl      148(%rsp), %r8d
..B3.159:
        testl     %r8d, %r8d
        jne       ..B3.161
..B3.181:
        cmpl      $0, 144(%rsp)
..B3.160:
        je        ..B3.162
..B3.161:
        lea       -16517(%rdi,%r9), %edx
        lea       -16383(%rax,%r10), %ecx
        cmpl      %ecx, %edx
        jl        ..B3.163
..B3.162:
        fldt      192(%rsp)
        fldt      208(%rsp)
        movaps    192(%rsp), %xmm0
        movaps    208(%rsp), %xmm1
        movaps    224(%rsp), %xmm2
        movaps    %xmm0, 240(%rsp)
        movaps    %xmm1, 256(%rsp)
        movaps    %xmm2, 272(%rsp)
        jmp       ..B3.167
..B3.163:
        fldt      144(%rsp)
        movl      %eax, %edi
        fldt      160(%rsp)
        movaps    144(%rsp), %xmm0
        movaps    160(%rsp), %xmm1
        movaps    176(%rsp), %xmm2
        movaps    %xmm0, 240(%rsp)
        movaps    %xmm1, 256(%rsp)
        movaps    %xmm2, 272(%rsp)
        jmp       ..B3.167
..B3.164:
        je        ..B3.171
..B3.165:
        incl      368(%rsp)
        lea       240(%rsp), %rdi
        lea       336(%rsp), %rsi
        lea       576(%rsp), %rdx
..___tag_value_imcatanhl.130:
        call      __libm_atan2l_k80@PLT
..___tag_value_imcatanhl.131:
..B3.166:
        fldt      240(%rsp)
        fldt      256(%rsp)
        movl      272(%rsp), %edi
..B3.167:
        fldt      16(%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     256(%rsp)
        fldt      256(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 688
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_imcatanhl.133:
        call      __libm_scalbl_k80@PLT
..___tag_value_imcatanhl.134:
..B3.176:
        addq      $32, %rsp
	.cfi_def_cfa_offset 656
..B3.168:
        movq      624(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B3.170
..B3.169:
        addq      $632, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 656
	.cfi_offset 3, -24
	.cfi_offset 13, -16
..B3.170:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B3.171:
        lea       96+_CONSTANTS(%rip), %rdx
        movl      532(%rsp), %eax
        cmpl      4(%rdx), %eax
        jb        ..B3.72
..B3.172:
        jne       ..B3.165
..B3.173:
        movl      528(%rsp), %eax
        cmpl      (%rdx), %eax
        jb        ..B3.72
        jmp       ..B3.165
        .align    16,0x90
	.cfi_endproc
	.type	imcatanhl,@function
	.size	imcatanhl,.-imcatanhl
	.data
# -- End  imcatanhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.17:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,16
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
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
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	32768
	.word	16415
	.word	0
	.word	0
	.word	0
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,112
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
