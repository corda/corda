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
	.file "atan2dl.c"
	.text
..TXTST0:
# -- Begin  atand2l
	.text
       .align    16,0x90
	.globl atand2l
atand2l:
# parameter 1: 8 + %rsp
# parameter 2: 24 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_atand2l.1:
..L2:

        fldt      8(%rsp)
        fldt      24(%rsp)
        fstpt     24(%rsp)
        fstpt     8(%rsp)
        jmp       atan2dl@PLT
        .align    16,0x90
	.cfi_endproc
	.type	atand2l,@function
	.size	atand2l,.-atand2l
	.data
# -- End  atand2l
	.text
# -- Begin  atan2dl
	.text
       .align    16,0x90
	.globl atan2dl
atan2dl:
# parameter 1: 96 + %rsp
# parameter 2: 112 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value_atan2dl.4:
..L5:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %dl, %dl
..B2.2:
        fnstcw    74(%rsp)
..B2.3:
        movzbl    105(%rsp), %r8d
        andl      $128, %r8d
        movzbl    121(%rsp), %eax
        movzwl    104(%rsp), %esi
        andl      $128, %eax
        andl      $32767, %esi
        shrl      $7, %r8d
        movzwl    120(%rsp), %edi
        movl      %r8d, %r9d
        shrl      $7, %eax
        andl      $32767, %edi
        cmpl      $32767, %esi
        je        ..B2.105
..B2.4:
        cmpl      $32767, %edi
        je        ..B2.88
..B2.5:
        testl     %esi, %esi
        jne       ..B2.7
..B2.6:
        cmpq      $0, 96(%rsp)
        je        ..B2.11
..B2.7:
        testl     %edi, %edi
        jne       ..B2.23
..B2.8:
        cmpq      $0, 112(%rsp)
        jne       ..B2.23
..B2.9:
        testl     %esi, %esi
        jne       ..B2.22
..B2.10:
        cmpq      $0, 96(%rsp)
        jne       ..B2.18
..B2.11:
        testl     %edi, %edi
        jne       ..B2.14
..B2.12:
        cmpq      $0, 112(%rsp)
        je        ..B2.83
..B2.13:
        lea       _smallest_value_64(%rip), %rdx
        movq      (%rdx), %rcx
        movq      %rcx, 48(%rsp)
..B2.14:
        testl     %eax, %eax
        je        ..B2.16
..B2.15:
        lea       c180(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.16:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%r9,8)
..B2.17:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.18:
        testl     %edi, %edi
        jne       ..B2.23
..B2.19:
        cmpq      $0, 112(%rsp)
        jne       ..B2.23
..B2.20:
        testl     %esi, %esi
        jne       ..B2.22
..B2.21:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 48(%rsp)
..B2.22:
        lea       c90(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.23:
        cmpl      %esi, %edi
        jne       ..B2.29
..B2.24:
        movl      116(%rsp), %ecx
        cmpl      100(%rsp), %ecx
        jne       ..B2.29
..B2.25:
        movl      112(%rsp), %ecx
        cmpl      96(%rsp), %ecx
        jne       ..B2.29
..B2.26:
        orl       %esi, %edi
        jne       ..B2.28
..B2.27:
        lea       _smallest_value_64(%rip), %rdx
        movq      (%rdx), %rcx
        movq      %rcx, 48(%rsp)
..B2.28:
        movl      %eax, %eax
        lea       exact(%rip), %rdx
        lea       (%r9,%rax,2), %rcx
        fldl      (%rdx,%rcx,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.29:
        movzwl    74(%rsp), %r10d
        movl      %r10d, %ecx
        andl      $3840, %ecx
        cmpl      $768, %ecx
        je        ..B2.33
..B2.30:
        andl      $-3841, %r10d
        orl       $-64768, %r10d
        movw      %r10w, 72(%rsp)
..B2.31:
        fldcw     72(%rsp)
..B2.32:
        movb      $1, %dl
..B2.33:
        cmpl      $8191, %edi
        jg        ..B2.37
..B2.34:
        cmpl      $8191, %esi
        jg        ..B2.36
..B2.35:
        fldt      112(%rsp)
        lea       _TWO_8192(%rip), %rcx
        fldt      96(%rsp)
        fldt      (%rcx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        movzwl    120(%rsp), %edi
        movzwl    104(%rsp), %esi
        andl      $32767, %edi
        andl      $32767, %esi
        jmp       ..B2.38
..B2.36:
        fldt      112(%rsp)
        fldt      96(%rsp)
        jmp       ..B2.41
..B2.37:
        fldt      112(%rsp)
        fldt      96(%rsp)
..B2.38:
        cmpl      $24575, %edi
        jl        ..B2.41
..B2.39:
        cmpl      $24575, %esi
        jl        ..B2.41
..B2.40:
        lea       16+_TWO_8192(%rip), %rcx
        fldt      (%rcx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        movzwl    120(%rsp), %edi
        movzwl    104(%rsp), %esi
        andl      $32767, %edi
        andl      $32767, %esi
..B2.41:
        lea       _ones(%rip), %rcx
        fldl      (%rcx,%rax,8)
        fmulp     %st, %st(2)
        fld       %st(0)
        fldl      (%rcx,%r9,8)
        fmul      %st, %st(1)
        fxch      %st(3)
        fcomi     %st(1), %st
        jbe       ..B2.69
..B2.42:
        lea       12(%rsi), %r10d
        cmpl      %r10d, %edi
        jle       ..B2.65
..B2.43:
        lea       75(%rsi), %r10d
        cmpl      %r10d, %edi
        jle       ..B2.62
..B2.44:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(1)
        testl     %eax, %eax
        je        ..B2.46
..B2.45:
        fstp      %st(0)
        movq      %r9, %rcx
        lea       _small_value_80(%rip), %rax
        shlq      $4, %rcx
        lea       c180(%rip), %rsi
        fldt      (%rax,%rcx)
        fsubrl    (%rsi,%r9,8)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.46:
        testl     %esi, %esi
        jle       ..B2.87
..B2.47:
        fstp      %st(0)
        xorl      %esi, %esi
..B2.48:
        shll      $15, %eax
        orl       $-49153, %eax
        movw      %ax, 120(%rsp)
        lea       _TWO_32H(%rip), %rax
        fldt      112(%rsp)
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fldl      (%rax)
        fmul      %st, %st(3)
        shll      $15, %r8d
        fxch      %st(2)
        fsubr     %st(3), %st
        movzwl    104(%rsp), %r9d
        orl       $-49153, %r8d
        movw      %r8w, 104(%rsp)
        lea       8+_C180byPi(%rip), %r8
        andl      $32767, %r9d
        fsubrp    %st, %st(3)
        subl      %r9d, %edi
        fxch      %st(2)
        fsubr     %st, %st(3)
        fld       %st(1)
        cmpl      $16382, %edi
        fldl      (%rcx)
        lea       _C180byPi(%rip), %rcx
        fld       %st(0)
        fdivp     %st, %st(5)
        fxch      %st(1)
        fmul      %st(4), %st
        fld       %st(0)
        fsub      %st(5), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fsubr     %st(5), %st
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(6)
        fmul      %st, %st(7)
        fxch      %st(7)
        faddp     %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(2)
        fsub      %st, %st(3)
        fxch      %st(5)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        faddp     %st, %st(2)
        fldl      (%rcx)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fldl      (%r8)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      96(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(1)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fxch      %st(3)
        fmul      %st(5), %st
        fld       %st(0)
        fsub      %st(6), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmul      %st, %st(4)
        fld       %st(0)
        fsub      %st(3), %st
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(5)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        fchs      
        fsubrp    %st, %st(1)
        fstpt     56(%rsp)
        fldt      56(%rsp)
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        faddp     %st, %st(1)
        jl        ..B2.52
..B2.49:
        fldt      56(%rsp)
        lea       _small(%rip), %rax
        addl      $-16318, %edi
        cmpl      $16382, %edi
        fldt      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     56(%rsp)
        fmul      %st, %st(1)
        jl        ..B2.51
..B2.50:
        fldt      56(%rsp)
        addl      $-16318, %edi
        fmul      %st(1), %st
        fstpt     56(%rsp)
        fldt      56(%rsp)
        fstpt     (%rsp)
        fmulp     %st, %st(1)
        jmp       ..B2.53
..B2.51:
        fstp      %st(0)
        fldt      56(%rsp)
        fstpt     (%rsp)
        jmp       ..B2.53
..B2.52:
        fldt      56(%rsp)
        fstpt     (%rsp)
..B2.53:
        movl      %edi, %eax
        negl      %eax
        addl      $16383, %eax
        fxch      %st(1)
        fstpt     16(%rsp)
        testl     %esi, %esi
        movw      %ax, 24(%rsp)
        je        ..B2.55
..B2.54:
        fldt      16(%rsp)
        lea       8+_two64(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        jmp       ..B2.56
..B2.55:
        fldt      16(%rsp)
..B2.56:
        movzwl    8(%rsp), %eax
        andl      $32767, %eax
        subl      %esi, %eax
        subl      %edi, %eax
        cmpl      $-9, %eax
        jl        ..B2.59
..B2.57:
        testl     %eax, %eax
        jg        ..B2.59
..B2.58:
        fldt      56(%rsp)
        fmul      %st(1), %st
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.60
..B2.59:
        fldt      56(%rsp)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
..B2.60:
        fldt      56(%rsp)
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        fxch      %st(1)
        fucomip   %st(1), %st
        jp        ..B2.113
        je        ..B2.61
..B2.113:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B2.80
..B2.61:
        fldt      32(%rsp)
        fdivrp    %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.62:
        fstp      %st(2)
        testl     %eax, %eax
        je        ..B2.64
..B2.63:
        fdivp     %st, %st(1)
        lea       _TWO_32H(%rip), %rax
        lea       _C180byPi(%rip), %rcx
        lea       8+_C180byPi(%rip), %rsi
        lea       48+_P(%rip), %rdi
        lea       16+_P(%rip), %r8
        lea       32+_P(%rip), %r9
        lea       _P(%rip), %r10
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %r11
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldl      (%rax)
        fmul      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      (%rcx)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      (%rsi)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fldt      (%rdi)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        fmulp     %st, %st(3)
        fldt      (%r10)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fsubrl    8200(%r11)
        faddl     8192(%r11)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.64:
        fld       %st(0)
        lea       48+_P(%rip), %rax
        lea       16+_P(%rip), %rsi
        lea       32+_P(%rip), %rdi
        lea       _P(%rip), %r8
        lea       _TWO_32H(%rip), %r9
        lea       _C180byPi(%rip), %r10
        lea       8+_C180byPi(%rip), %r11
        fdiv      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(5)
        fstpl     32(%rsp)
        fldt      (%rax)
        fmul      %st(5), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rdi)
        fmulp     %st, %st(6)
        fldt      (%r8)
        faddp     %st, %st(6)
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%rsp)
        fld       %st(0)
        fld       %st(2)
        fld       %st(2)
        fld       %st(4)
        fldl      (%r9)
        fmul      %st, %st(4)
        fxch      %st(2)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(4)
        fsub      %st(3), %st
        fld       %st(2)
        fxch      %st(6)
        fmull     (%r11)
        fldl      (%r10)
        fmul      %st, %st(5)
        fxch      %st(5)
        fmul      %st, %st(7)
        fxch      %st(5)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(4)
        fadd      %st(2), %st
        fld       %st(4)
        fadd      %st(7), %st
        fsubp     %st, %st(7)
        fxch      %st(4)
        fsub      %st(6), %st
        faddp     %st, %st(1)
        fxch      %st(3)
        fdivrl    (%rcx)
        fmul      %st, %st(2)
        lea       8+_ones(%rip), %rcx
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(2)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(3), %st
        fldt      (%rsp)
        fxch      %st(1)
        faddl     (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(3)
        fadd      %st(5), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmull     32(%rsp)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.65:
        fstp      %st(2)
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rsi
        fmul      %st(1), %st
        lea       _TWO_48H(%rip), %rdi
        fdiv      %st(2), %st
        fld       %st(1)
        fxch      %st(4)
        fstpl     32(%rsp)
        fld       %st(2)
        fldl      (%rsi)
        lea       _C180byPi(%rip), %r8
        lea       8+_C180byPi(%rip), %r9
        lea       48+_P(%rip), %r10
        lea       16+_P(%rip), %r11
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(2)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        fld       %st(3)
        fldl      (%rdi)
        lea       _P(%rip), %rdi
        fmul      %st, %st(7)
        movl      16(%rsp), %esi
        fxch      %st(1)
        fadd      %st(7), %st
        addl      %esi, %esi
        fsubp     %st, %st(7)
        testl     %eax, %eax
        fxch      %st(4)
        fsub      %st(6), %st
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(5), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(5)
        fxch      %st(2)
        fadd      %st(5), %st
        fld       %st(6)
        fmul      %st(4), %st
        fadd      %st(3), %st
        fxch      %st(4)
        fmul      %st, %st(6)
        fxch      %st(6)
        fsubrp    %st, %st(2)
        fld       %st(3)
        fadd      %st(1), %st
        fxch      %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fsubrp    %st, %st(6)
        fld       %st(2)
        fxch      %st(2)
        fdivrl    (%rcx)
        fmul      %st, %st(4)
        lea       8+_ones(%rip), %rcx
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(5)
        fld       %st(4)
        fsubr     %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(4), %st
        faddl     (%rcx)
        lea       32+_P(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fadd      %st(2), %st
        fmul      %st, %st(4)
        fld       %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      (%r8)
        fmul      %st, %st(3)
        fmulp     %st, %st(4)
        fldl      (%r9)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldl      32(%rsp)
        je        ..B2.67
..B2.66:
        negl      %esi
        fld       %st(2)
        movslq    %esi, %rsi
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %rax
        fldl      8192(%rax,%rsi,8)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fldl      8200(%rax,%rsi,8)
        fsubp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.68
..B2.67:
        movslq    %esi, %rsi
        fld       %st(2)
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %rax
        fldl      (%rax,%rsi,8)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddl     8(%rax,%rsi,8)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     56(%rsp)
..B2.68:
        fldt      56(%rsp)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.69:
        fstp      %st(2)
        lea       12(%rdi), %r8d
        cmpl      %r8d, %esi
        jle       ..B2.76
..B2.70:
        addl      $75, %edi
        cmpl      %edi, %esi
        jle       ..B2.72
..B2.71:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movq      %r9, %rcx
        lea       _small_value_80(%rip), %rax
        shlq      $4, %rcx
        lea       c90(%rip), %rsi
        fldt      (%rax,%rcx)
        fsubrl    (%rsi,%r9,8)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.72:
        fdivrp    %st, %st(1)
        lea       _TWO_32H(%rip), %rcx
        lea       _C180byPi(%rip), %rsi
        lea       8+_C180byPi(%rip), %rdi
        lea       48+_P(%rip), %r8
        lea       16+_P(%rip), %r9
        lea       32+_P(%rip), %r10
        lea       _P(%rip), %r11
        fstpt     112(%rsp)
        fldt      112(%rsp)
        testl     %eax, %eax
        fldl      (%rcx)
        fmul      %st(1), %st
        fld       %st(0)
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %rcx
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      (%rsi)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      (%rdi)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fldt      (%r8)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      4096(%rcx)
        fstpt     56(%rsp)
        fldl      4104(%rcx)
        je        ..B2.74
..B2.73:
        fldt      56(%rsp)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.75
..B2.74:
        fldt      56(%rsp)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     56(%rsp)
..B2.75:
        fldt      56(%rsp)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.80
..B2.76:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_63H(%rip), %rsi
        fmul      %st(2), %st
        lea       _TWO_48H(%rip), %rdi
        fdiv      %st(1), %st
        fld       %st(2)
        fxch      %st(4)
        fstpl     32(%rsp)
        fld       %st(1)
        fldl      (%rsi)
        lea       _C180byPi(%rip), %r8
        lea       8+_C180byPi(%rip), %r9
        lea       48+_P(%rip), %r10
        lea       16+_P(%rip), %r11
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(2)
        fld       %st(3)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        fld       %st(3)
        fldl      (%rdi)
        lea       _P(%rip), %rdi
        fmul      %st, %st(7)
        movl      16(%rsp), %esi
        fxch      %st(2)
        fadd      %st(7), %st
        addl      %esi, %esi
        fsubp     %st, %st(7)
        fld       %st(6)
        testl     %eax, %eax
        fmul      %st(4), %st
        fxch      %st(6)
        fsub      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(1)
        fmul      %st(4), %st
        fadd      %st(5), %st
        fxch      %st(4)
        fmul      %st, %st(5)
        fxch      %st(5)
        fsubrp    %st, %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fld       %st(4)
        fxch      %st(1)
        fdivrl    (%rcx)
        lea       8+_ones(%rip), %rcx
        fmul      %st, %st(2)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddl     (%rcx)
        lea       32+_P(%rip), %rcx
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(4), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(4)
        fmulp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fadd      %st(3), %st
        fmul      %st, %st(4)
        fld       %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fxch      %st(4)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fldl      (%r8)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      (%r9)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        fmulp     %st, %st(3)
        fldt      (%rdi)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldl      32(%rsp)
        je        ..B2.78
..B2.77:
        movslq    %esi, %rsi
        fld       %st(1)
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %rax
        fldl      4096(%rax,%rsi,8)
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(3)
        faddl     4104(%rax,%rsi,8)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     56(%rsp)
        jmp       ..B2.79
..B2.78:
        negl      %esi
        fld       %st(1)
        movslq    %esi, %rsi
        movq      __libm_atandl_table_128@GOTPCREL(%rip), %rax
        fldl      4096(%rax,%rsi,8)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(3)
        fldl      4104(%rax,%rsi,8)
        fsubp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     56(%rsp)
..B2.79:
        fldt      56(%rsp)
        fmulp     %st, %st(1)
        fstpt     56(%rsp)
..B2.80:
        testb     %dl, %dl
        je        ..B2.82
..B2.81:
        fldcw     74(%rsp)
..B2.82:
        fldt      56(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.83:
        testl     %eax, %eax
        je        ..B2.85
..B2.84:
        lea       c180(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.85:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%r9,8)
..B2.86:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.87:
        lea       _two64(%rip), %rsi
        fldl      (%rsi)
        movl      $64, %esi
        fmulp     %st, %st(1)
        fstpt     96(%rsp)
        jmp       ..B2.48
..B2.88:
        movq      $0x8000000000000000, %rdx
        cmpq      112(%rsp), %rdx
        je        ..B2.90
..B2.89:
        fldt      112(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.90:
        testl     %esi, %esi
        jne       ..B2.96
..B2.91:
        cmpl      $0, 100(%rsp)
        jne       ..B2.93
..B2.92:
        cmpl      $0, 96(%rsp)
        je        ..B2.97
..B2.93:
        lea       _smallest_value_64(%rip), %rdx
        movq      (%rdx), %rcx
        movq      %rcx, 48(%rsp)
..B2.94:
        cmpl      $32767, %edi
        je        ..B2.96
..B2.95:
        lea       c90(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.96:
        cmpl      $32767, %esi
        je        ..B2.101
..B2.97:
        testl     %eax, %eax
        je        ..B2.99
..B2.98:
        lea       c180(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.99:
        lea       _zeros(%rip), %rax
        fldl      (%rax,%r9,8)
..B2.100:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.101:
        testl     %eax, %eax
        je        ..B2.103
..B2.102:
        lea       c135(%rip), %rax
        fldl      (%rax,%r9,8)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.103:
        lea       c45(%rip), %rax
        fldl      (%rax,%r9,8)
..B2.104:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.105:
        movq      $0x8000000000000000, %rdx
        cmpq      96(%rsp), %rdx
        jne       ..B2.89
..B2.106:
        cmpl      $32767, %edi
        je        ..B2.88
..B2.107:
        testl     %edi, %edi
        jne       ..B2.94
..B2.108:
        cmpl      $0, 116(%rsp)
        jne       ..B2.93
..B2.109:
        cmpl      $0, 112(%rsp)
        jne       ..B2.93
        jmp       ..B2.95
        .align    16,0x90
	.cfi_endproc
	.type	atan2dl,@function
	.size	atan2dl,.-atan2dl
	.data
# -- End  atan2dl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x08,0x00,0x80,0x13,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 16
_TWO_8192:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24575
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.word	0
	.word	0
	.type	_TWO_8192,@object
	.size	_TWO_8192,32
	.align 16
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 16
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
	.align 16
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.space 8, 0x00 	# pad
	.align 16
_C180byPi:
	.long	442499072
	.long	1078765020
	.long	3272251219
	.long	1042157501
	.type	_C180byPi,@object
	.size	_C180byPi,16
	.align 16
_small:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	65
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32769
	.word	0
	.word	0
	.word	0
	.type	_small,@object
	.size	_small,32
	.align 16
_two64:
	.long	0
	.long	1139802112
	.long	0
	.long	1005584384
	.type	_two64,@object
	.size	_two64,16
	.align 16
_P:
	.word	54228
	.word	26975
	.word	60215
	.word	39113
	.word	49155
	.word	0
	.word	0
	.word	0
	.word	30637
	.word	6142
	.word	46044
	.word	46936
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	47408
	.word	26349
	.word	14153
	.word	33526
	.word	49154
	.word	0
	.word	0
	.word	0
	.word	53934
	.word	32714
	.word	35699
	.word	52145
	.word	16385
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,64
	.align 16
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.space 8, 0x00 	# pad
	.align 16
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 8
c180:
	.long	0x00000000,0x40668000
	.long	0x00000000,0xc0668000
	.type	c180,@object
	.size	c180,16
	.align 8
c90:
	.long	0x00000000,0x40568000
	.long	0x00000000,0xc0568000
	.type	c90,@object
	.size	c90,16
	.align 8
exact:
	.long	0x00000000,0x40468000
	.long	0x00000000,0xc0468000
	.long	0x00000000,0x4060e000
	.long	0x00000000,0xc060e000
	.type	exact,@object
	.size	exact,32
	.align 8
c135:
	.long	0x00000000,0x4060e000
	.long	0x00000000,0xc060e000
	.type	c135,@object
	.size	c135,16
	.align 8
c45:
	.long	0x00000000,0x40468000
	.long	0x00000000,0xc0468000
	.type	c45,@object
	.size	c45,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
