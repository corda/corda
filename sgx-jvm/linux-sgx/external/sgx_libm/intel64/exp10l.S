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
	.file "exp10l.c"
	.text
..TXTST0:
# -- Begin  exp10l
	.text
       .align    16,0x90
	.globl exp10l
exp10l:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_exp10l.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %dil, %dil
..B1.2:
        fnstcw    86(%rsp)
..B1.3:
        movzwl    104(%rsp), %esi
        movl      %esi, %eax
        movzbl    105(%rsp), %ecx
        andl      $32767, %eax
        shrl      $7, %ecx
        shll      $15, %ecx
        orl       %eax, %ecx
        shll      $16, %ecx
        movzwl    102(%rsp), %edx
        orl       %edx, %ecx
        addl      $-1073709056, %ecx
        cmpl      $284672, %ecx
        ja        ..B1.6
..B1.4:
        fldt      96(%rsp)
        lea       _TWO_23H(%rip), %rax
        fld       %st(0)
        fldl      (%rax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstps     80(%rsp)
        movss     80(%rsp), %xmm0
        movss     %xmm0, 16(%rsp)
        flds      16(%rsp)
        fsubp     %st, %st(1)
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.5
        je        ..B1.79
..B1.5:
        movl      %esi, %eax
        andl      $32767, %eax
..B1.6:
        cmpl      $16388, %eax
        jge       ..B1.45
..B1.7:
        movzwl    86(%rsp), %edx
        cmpl      $16375, %eax
        jge       ..B1.38
..B1.8:
        cmpl      $16368, %eax
        jge       ..B1.31
..B1.9:
        cmpl      $16357, %eax
        jge       ..B1.24
..B1.10:
        cmpl      $16316, %eax
        jge       ..B1.17
..B1.11:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.73
..B1.12:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.13:
        fldcw     84(%rsp)
..B1.14:
        fldt      .L_2il0floatpacket.3(%rip)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.15:
        fldcw     86(%rsp)
..B1.16:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.17:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.21
..B1.18:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.19:
        fldcw     84(%rsp)
..B1.20:
        movb      $1, %dil
..B1.21:
        fldt      96(%rsp)
        lea       16+_Q2(%rip), %rax
        fldt      .L_2il0floatpacket.3(%rip)
        lea       _Q2(%rip), %rdx
        testb     %dil, %dil
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.23
..B1.22:
        fldcw     86(%rsp)
..B1.23:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.24:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.28
..B1.25:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.26:
        fldcw     84(%rsp)
..B1.27:
        movb      $1, %dil
..B1.28:
        fldt      96(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rdx
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rcx
        fldt      (%rax)
        lea       _Q1(%rip), %rsi
        fmul      %st(1), %st
        testb     %dil, %dil
        fldt      (%rdx)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.3(%rip)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.30
..B1.29:
        fldcw     86(%rsp)
..B1.30:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.31:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.35
..B1.32:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.33:
        fldcw     84(%rsp)
..B1.34:
        movb      $1, %dil
..B1.35:
        fldt      96(%rsp)
        lea       112+_Q0(%rip), %rdx
        fld       %st(0)
        lea       96+_Q0(%rip), %rcx
        fmul      %st(1), %st
        lea       80+_Q0(%rip), %rsi
        lea       64+_Q0(%rip), %r8
        lea       48+_Q0(%rip), %r9
        lea       32+_Q0(%rip), %r10
        lea       _TWO_48H(%rip), %rax
        lea       16+_Q0(%rip), %r11
        fld       %st(1)
        fld       %st(2)
        testb     %dil, %dil
        fldt      (%rdx)
        lea       128+_Q0(%rip), %rdx
        fmul      %st(3), %st
        fldl      (%rax)
        lea       _Q0(%rip), %rax
        fadd      %st, %st(3)
        fsubr     %st(3), %st
        fsubr     %st, %st(2)
        fldt      (%rcx)
        fmul      %st(5), %st
        fldt      (%rsi)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      (%r8)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      (%r9)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      (%r10)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(5), %st
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(5)
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      (%rdx)
        fmul      %st, %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.3(%rip)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        je        ..B1.85
..B1.36:
        fstpt     48(%rsp)
..B1.82:
        fldcw     86(%rsp)
        jmp       ..B1.37
..B1.85:
        fstp      %st(0)
..B1.37:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.38:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.42
..B1.39:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.40:
        fldcw     84(%rsp)
..B1.41:
        movb      $1, %dil
..B1.42:
        fldt      96(%rsp)
        lea       _TWO_63H(%rip), %rdx
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_48H(%rip), %rcx
        fmul      %st(1), %st
        lea       80+_P(%rip), %r8
        fldl      (%rdx)
        lea       48+_P(%rip), %r10
        lea       64+_P(%rip), %r9
        lea       32+_P(%rip), %r11
        lea       16+_P(%rip), %rdx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        movl      48(%rsp), %esi
        fsubrp    %st, %st(2)
        fld       %st(1)
        fldt      .L_2il0floatpacket.2(%rip)
        fmulp     %st, %st(2)
        movsbq    %sil, %rax
        fsub      %st(1), %st
        fldl      (%rcx)
        lea       _P(%rip), %rcx
        subl      %eax, %esi
        fadd      %st, %st(1)
        shrl      $8, %esi
        fsubr     %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        shll      $23, %esi
        fxch      %st(3)
        fsubrp    %st, %st(4)
        addl      $1065353216, %esi
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%r8)
        fmul      %st(1), %st
        fldt      (%r9)
        fmul      %st(2), %st
        fldt      (%r10)
        movl      %esi, 32(%rsp)
        lea       96+_P(%rip), %rsi
        shlq      $4, %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        testb     %dil, %dil
        fldt      (%r11)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rsi)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fldl      2056(%r8,%rax)
        fldl      2048(%r8,%rax)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(4)
        fadd      %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmuls     32(%rsp)
        fstpt     16(%rsp)
        je        ..B1.86
..B1.43:
        fstpt     48(%rsp)
..B1.83:
        fldcw     86(%rsp)
        jmp       ..B1.44
..B1.86:
        fstp      %st(0)
..B1.44:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.45:
        movb      105(%rsp), %dl
        lea       _ranges(%rip), %r8
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        movq      %rsi, %rcx
        shlq      $4, %rcx
        movzwl    8(%rcx,%r8), %edx
        andl      $32767, %edx
        cmpl      %edx, %eax
        jl        ..B1.50
..B1.46:
        jne       ..B1.62
..B1.47:
        movl      100(%rsp), %edx
        cmpl      4(%rcx,%r8), %edx
        jb        ..B1.50
..B1.48:
        jne       ..B1.62
..B1.49:
        movl      96(%rsp), %edx
        cmpl      (%rcx,%r8), %edx
        jae       ..B1.62
..B1.50:
        movzwl    86(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.54
..B1.51:
        orl       $-64768, %edx
        movw      %dx, 84(%rsp)
..B1.52:
        fldcw     84(%rsp)
..B1.53:
        movb      $1, %dil
..B1.54:
        fldt      .L_2il0floatpacket.3(%rip)
        lea       _TWO_63H(%rip), %r8
        fstpt     64(%rsp)
        lea       _TWO_48H(%rip), %r9
        fldt      96(%rsp)
        lea       SC2_BIAS(%rip), %r10
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st(1), %st
        fldl      (%r8)
        movzwl    8+.L_2il0floatpacket.3(%rip), %r8d
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        andl      $-32768, %r8d
        movl      48(%rsp), %edx
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmul      %st(1), %st
        movsbq    %dl, %rax
        fsubr     %st(2), %st
        fld       %st(0)
        movl      %eax, %r11d
        fldt      .L_2il0floatpacket.2(%rip)
        negl      %r11d
        fmulp     %st, %st(3)
        addl      %edx, %r11d
        shrl      $8, %r11d
        addl      $4196735, %edx
        addl      (%r10,%rsi,4), %r11d
        lea       80+_P(%rip), %rsi
        andl      $32767, %r11d
        lea       16+_P(%rip), %r10
        orl       %r11d, %r8d
        lea       _P(%rip), %r11
        movw      %r8w, 72(%rsp)
        lea       48+_P(%rip), %r8
        shlq      $4, %rax
        fsub      %st(2), %st
        fldl      (%r9)
        lea       32+_P(%rip), %r9
        cmpl      $3070, %edx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       64+_P(%rip), %rsi
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       96+_P(%rip), %rsi
        fmul      %st(2), %st
        fldt      (%r8)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rsi)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldl      2048(%r8,%rax)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        fmull     2056(%r8,%rax)
        faddp     %st, %st(1)
        faddl     2056(%r8,%rax)
        ja        ..B1.57
..B1.55:
        lea       _exp10but(%rip), %rax
        fldt      (%rax)
        fcomip    %st(3), %st
        jbe       ..B1.57
..B1.56:
        fstp      %st(2)
        fld       %st(0)
        lea       _TWO_12H(%rip), %rax
        lea       _SC2(%rip), %rdx
        fadd      %st(2), %st
        fldl      (%rax)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      (%rcx,%rdx)
        fldt      64(%rsp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        jmp       ..B1.58
..B1.57:
        fstp      %st(2)
        fldt      64(%rsp)
        fxch      %st(2)
        faddp     %st, %st(1)
        lea       _SC2(%rip), %rax
        fmulp     %st, %st(1)
        fldt      (%rcx,%rax)
        fmulp     %st, %st(1)
        fstpt     32(%rsp)
..B1.58:
        fldt      32(%rsp)
        lea       _minnorm(%rip), %rax
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        ja        ..B1.74
..B1.59:
        testb     %dil, %dil
        je        ..B1.61
..B1.60:
        fldt      16(%rsp)
        fstpt     48(%rsp)
..B1.84:
        fldcw     86(%rsp)
..B1.61:
        fldt      32(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.62:
        movzwl    86(%rsp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.66
..B1.63:
        orl       $-64768, %ecx
        movw      %cx, 84(%rsp)
..B1.64:
        fldcw     84(%rsp)
..B1.65:
        movzwl    104(%rsp), %eax
        movb      $1, %dil
        andl      $32767, %eax
..B1.66:
        cmpl      $32767, %eax
        je        ..B1.75
..B1.67:
        testq     %rsi, %rsi
        je        ..B1.69
..B1.68:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        jmp       ..B1.70
..B1.69:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
..B1.70:
        testb     %dil, %dil
        je        ..B1.72
..B1.71:
        fldcw     86(%rsp)
..B1.72:
        fldt      16(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.73:
        fldt      .L_2il0floatpacket.3(%rip)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.16
..B1.74:
        lea       _small_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.59
..B1.75:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.78
..B1.76:
        cmpl      $0, 96(%rsp)
        jne       ..B1.78
..B1.77:
        lea       _inf_zeros(%rip), %rax
        fldl      (%rax,%rsi,8)
        fstpt     16(%rsp)
        jmp       ..B1.70
..B1.78:
        fldt      96(%rsp)
        fstpt     16(%rsp)
        jmp       ..B1.70
..B1.79:
        movl      80(%rsp), %eax
        lea       _exact_values(%rip), %rdx
        andl      $4194303, %eax
        shlq      $4, %rax
        fldt      -16(%rdx,%rax)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	exp10l,@function
	.size	exp10l,.-exp10l
	.data
# -- End  exp10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x88,0x1b,0xcd,0x4b,0x78,0x9a,0xd4,0x08,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x85,0x9a,0x20,0x9a,0xf5,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0xc8,0xd4,0x0e,0xee,0x0c,0x01,0x86,0xd0,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 4
_TWO_23H:
	.long	0
	.long	1097334784
	.type	_TWO_23H,@object
	.size	_TWO_23H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
SC2_BIAS:
	.long	8191
	.long	24575
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_TWO_12H:
	.long	0
	.long	1085800448
	.type	_TWO_12H,@object
	.size	_TWO_12H,8
	.align 4
_inf_zeros:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,16
	.align 2
_Q2:
	.word	45097
	.word	43688
	.word	36317
	.word	37725
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	18666
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	44055
	.word	43688
	.word	36317
	.word	37725
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	17466
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	8942
	.word	31610
	.word	11407
	.word	33336
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	11423
	.word	35775
	.word	45136
	.word	38379
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q0:
	.word	11092
	.word	42962
	.word	43694
	.word	58436
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	17467
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	8985
	.word	61797
	.word	11406
	.word	33336
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	8830
	.word	60614
	.word	45135
	.word	38379
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	62265
	.word	59600
	.word	65512
	.word	35348
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	36045
	.word	15298
	.word	47170
	.word	54262
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	33342
	.word	6003
	.word	33899
	.word	35698
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	20958
	.word	58269
	.word	28628
	.word	41099
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	37726
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_Q0,@object
	.size	_Q0,144
	.align 2
_P:
	.word	405
	.word	44055
	.word	43688
	.word	36317
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	17467
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	7929
	.word	61789
	.word	11406
	.word	33336
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	36964
	.word	60605
	.word	45135
	.word	38379
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	55739
	.word	36897
	.word	684
	.word	35349
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	13923
	.word	51509
	.word	48217
	.word	54262
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	37725
	.word	16384
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,112
	.align 2
_ranges:
	.word	63385
	.word	64463
	.word	39556
	.word	39456
	.word	16395
	.word	0
	.word	0
	.word	0
	.word	55376
	.word	5815
	.word	21149
	.word	39608
	.word	16395
	.word	0
	.word	0
	.word	0
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_exp10but:
	.word	6424
	.word	10152
	.word	51584
	.word	39451
	.word	49163
	.word	0
	.word	0
	.word	0
	.type	_exp10but,@object
	.size	_exp10but,16
	.align 2
_SC2:
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
	.type	_SC2,@object
	.size	_SC2,32
	.align 2
_minnorm:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.type	_minnorm,@object
	.size	_minnorm,16
	.align 2
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
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.align 2
_exact_values:
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	64000
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40000
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	50000
	.word	16399
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	62500
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39062
	.word	16406
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	8192
	.word	48828
	.word	16409
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	10240
	.word	61035
	.word	16412
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	63744
	.word	38146
	.word	16416
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	46912
	.word	47683
	.word	16419
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	42256
	.word	59604
	.word	16422
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	59178
	.word	37252
	.word	16426
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8436
	.word	46566
	.word	16429
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	43313
	.word	58207
	.word	16432
	.word	0
	.word	0
	.word	0
	.word	0
	.word	1024
	.word	51647
	.word	36379
	.word	16436
	.word	0
	.word	0
	.word	0
	.word	0
	.word	50432
	.word	48174
	.word	45474
	.word	16439
	.word	0
	.word	0
	.word	0
	.word	0
	.word	30272
	.word	27450
	.word	56843
	.word	16442
	.word	0
	.word	0
	.word	0
	.word	0
	.word	35304
	.word	8964
	.word	35527
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	44130
	.word	60357
	.word	44408
	.word	16449
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6010
	.word	9911
	.word	55511
	.word	16452
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	28332
	.word	30770
	.word	34694
	.word	16456
	.word	0
	.word	0
	.word	0
	.word	46080
	.word	2647
	.word	5695
	.word	43368
	.word	16459
	.word	0
	.word	0
	.word	0
	.word	41216
	.word	52461
	.word	7118
	.word	54210
	.word	16462
	.word	0
	.word	0
	.word	0
	.word	33952
	.word	16404
	.word	20833
	.word	33881
	.word	16466
	.word	0
	.word	0
	.word	0
	.word	42440
	.word	36889
	.word	42425
	.word	42351
	.word	16469
	.word	0
	.word	0
	.word	0
	.word	3898
	.word	62496
	.word	36647
	.word	52939
	.word	16472
	.word	0
	.word	0
	.word	0
	.type	_exact_values,@object
	.size	_exact_values,432
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End

	.globl pow10l
	.equ pow10l, exp10l

