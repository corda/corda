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
	.file "log1pl.c"
	.text
..TXTST0:
# -- Begin  log1pl
	.text
       .align    16,0x90
	.globl log1pl
log1pl:
# parameter 1: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_log1pl.1:
..L2:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        xorb      %cl, %cl
..B1.2:
        fnstcw    82(%rsp)
..B1.3:
        movzbl    105(%rsp), %r8d
        andl      $128, %r8d
        shrl      $7, %r8d
        movl      %r8d, %edi
        movzwl    104(%rsp), %esi
        shll      $15, %edi
        andl      $32767, %esi
        orl       %esi, %edi
        shll      $16, %edi
        movzwl    102(%rsp), %eax
        orl       %eax, %edi
        movzwl    82(%rsp), %eax
        cmpl      $32767, %esi
        je        ..B1.71
..B1.4:
        cmpl      $-1073774592, %edi
        jae       ..B1.57
..B1.5:
        cmpl      $16379, %esi
        jge       ..B1.47
..B1.6:
        cmpl      $16369, %esi
        jge       ..B1.40
..B1.7:
        cmpl      $16358, %esi
        jge       ..B1.33
..B1.8:
        cmpl      $16345, %esi
        jge       ..B1.26
..B1.9:
        cmpl      $16308, %esi
        jge       ..B1.19
..B1.10:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.14
..B1.11:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.12:
        fldcw     80(%rsp)
..B1.13:
        movzwl    104(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.14:
        testl     %esi, %esi
        jle       ..B1.67
..B1.15:
        fldt      96(%rsp)
        lea       _TWO_75(%rip), %rax
        movb      105(%rsp), %dl
        lea       _ones(%rip), %rsi
        andb      $-128, %dl
        lea       8+_TWO_75(%rip), %r8
        shrb      $7, %dl
        fldl      (%rax)
        fmul      %st(1), %st
        movzbl    %dl, %edi
        fxch      %st(1)
        fmull     (%rsi,%rdi,8)
        fsubrp    %st, %st(1)
        fmull     (%r8)
        fstpt     (%rsp)
..B1.16:
        testb     %cl, %cl
        je        ..B1.18
..B1.17:
        fldcw     82(%rsp)
..B1.18:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.19:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.23
..B1.20:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.21:
        fldcw     80(%rsp)
..B1.22:
        movb      $1, %cl
..B1.23:
        fldt      96(%rsp)
        lea       _Q3(%rip), %rax
        lea       _TWO_75(%rip), %rdx
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldl      (%rdx)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.25
..B1.24:
        fldcw     82(%rsp)
..B1.25:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.26:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.30
..B1.27:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.28:
        fldcw     80(%rsp)
..B1.29:
        movb      $1, %cl
..B1.30:
        fldt      96(%rsp)
        lea       16+_Q2(%rip), %rax
        lea       _Q2(%rip), %rdx
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.32
..B1.31:
        fldcw     82(%rsp)
..B1.32:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.33:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.37
..B1.34:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.35:
        fldcw     80(%rsp)
..B1.36:
        movb      $1, %cl
..B1.37:
        fldt      96(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rdx
        fldt      (%rax)
        lea       _Q1(%rip), %rdi
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.39
..B1.38:
        fldcw     82(%rsp)
..B1.39:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.40:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.44
..B1.41:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.42:
        fldcw     80(%rsp)
..B1.43:
        movb      $1, %cl
..B1.44:
        fldt      96(%rsp)
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       208+_Q(%rip), %rdi
        lea       176+_Q(%rip), %r8
        lea       144+_Q(%rip), %r9
        fldl      (%rax)
        lea       112+_Q(%rip), %r10
        fmul      %st(4), %st
        lea       80+_Q(%rip), %r11
        lea       48+_Q(%rip), %rax
        lea       224+_Q(%rip), %rsi
        lea       240+_Q(%rip), %rdx
        testb     %cl, %cl
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(0), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        faddp     %st, %st(3)
        fstpt     48(%rsp)
        fldt      (%rdx)
        lea       16+_Q(%rip), %rdx
        fmul      %st, %st(1)
        fldt      (%rsi)
        lea       192+_Q(%rip), %rsi
        fldt      (%rdi)
        lea       160+_Q(%rip), %rdi
        fmul      %st(4), %st
        fldt      (%r8)
        lea       128+_Q(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        lea       96+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        lea       64+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r11)
        lea       32+_Q(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rax)
        lea       _Q(%rip), %rax
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%rsi)
        fmul      %st(5), %st
        fldt      (%rdi)
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
        fmulp     %st, %st(5)
        fld       %st(1)
        fldt      (%rax)
        faddp     %st, %st(6)
        fxch      %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      48(%rsp)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(4)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fldt      32(%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.46
..B1.45:
        fldcw     82(%rsp)
..B1.46:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.47:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.51
..B1.48:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.49:
        fldcw     80(%rsp)
..B1.50:
        movzbl    105(%rsp), %r8d
        movb      $1, %cl
        andl      $128, %r8d
        shrl      $7, %r8d
..B1.51:
        lea       ranges(%rip), %rax
        cmpl      (%rax,%r8,4), %edi
        jae       ..B1.53
..B1.52:
        fldt      96(%rsp)
        xorl      %edi, %edi
        movzwl    104(%rsp), %eax
        lea       _shifters(%rip), %rsi
        andl      $32767, %eax
        fld       %st(0)
        addl      $-16415, %eax
        lea       zero_one(%rip), %r8
        lea       _ones(%rip), %r9
        setl      %dil
        fldl      (%r9)
        fstpt     (%rsp)
        fldl      (%rsi,%rdi,8)
        fmul      %st(2), %st
        movzwl    8(%rsp), %r11d
        fadd      %st, %st(1)
        andl      $-32768, %r11d
        fsubrp    %st, %st(1)
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rsi
        fsubr     %st, %st(1)
        faddl     (%r8,%rdi,8)
        xorl      $1, %edi
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(1)
        faddl     (%r8,%rdi,8)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     96(%rsp)
        movzwl    104(%rsp), %edx
        andl      $32767, %edx
        movl      %edx, %r10d
        addl      $-16383, %edx
        negl      %r10d
        movl      100(%rsp), %eax
        addl      $-2, %r10d
        shrl      $23, %eax
        andl      $32767, %r10d
        movzbl    %al, %eax
        orl       %r10d, %r11d
        movw      %r11w, 8(%rsp)
        fldt      (%rsp)
        fmuls     (%rsi,%rax,4)
        jmp       ..B1.54
..B1.53:
        fldt      96(%rsp)
        lea       zero_one(%rip), %rdx
        lea       _TWO_32H(%rip), %rdi
        faddl     (%rdx,%r8,8)
        fstpt     96(%rsp)
        fldl      (%rdi)
        movzwl    104(%rsp), %eax
        movl      %eax, %edx
        andl      $-32768, %eax
        andl      $32767, %edx
        orl       $-49153, %eax
        addl      $-16383, %edx
        movw      %ax, 104(%rsp)
        fldt      96(%rsp)
        fld       %st(0)
        movl      100(%rsp), %esi
        fadd      %st(2), %st
        shrl      $23, %esi
        fsubp     %st, %st(2)
        movzbl    %sil, %eax
        fsub      %st(1), %st
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r8
        fxch      %st(1)
        fstpt     32(%rsp)
        flds      (%r8,%rax,4)
..B1.54:
        fldt      32(%rsp)
        lea       8+_ones(%rip), %rsi
        fmul      %st(1), %st
        lea       48+_P(%rip), %rdi
        fxch      %st(2)
        fmulp     %st, %st(1)
        fxch      %st(1)
        faddl     (%rsi)
        fld       %st(0)
        lea       16+_P(%rip), %r8
        movl      %edx, 48(%rsp)
        lea       80+_P(%rip), %rdx
        lea       64+_P(%rip), %r9
        lea       32+_P(%rip), %r10
        lea       _P(%rip), %r11
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fildl     48(%rsp)
        fldt      (%rdx)
        fmul      %st(3), %st
        fldt      (%rdi)
        shlq      $4, %rax
        faddp     %st, %st(1)
        fmul      %st(3), %st
        testb     %cl, %cl
        fldt      (%r8)
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmulp     %st, %st(2)
        fldt      (%r9)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(2)
        faddl     8(%rdx,%rax)
        faddp     %st, %st(3)
        faddl     (%rax,%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%rsp)
        je        ..B1.56
..B1.55:
        fldcw     82(%rsp)
..B1.56:
        fldt      32(%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.57:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.61
..B1.58:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.59:
        fldcw     80(%rsp)
..B1.60:
        movb      $1, %cl
..B1.61:
        fldt      96(%rsp)
        lea       8+_ones(%rip), %rdx
        lea       _zeros(%rip), %rax
        fldl      (%rdx)
        movsd     (%rdx), %xmm0
        movsd     (%rax), %xmm1
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.62
        je        ..B1.63
..B1.62:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 48(%rsp)
        fldl      48(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.64
..B1.63:
        divsd     %xmm1, %xmm0
        movsd     %xmm0, 48(%rsp)
        fldl      48(%rsp)
        fstpt     (%rsp)
..B1.64:
        testb     %cl, %cl
        je        ..B1.66
..B1.65:
        fldcw     82(%rsp)
..B1.66:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.67:
        cmpl      $0, 100(%rsp)
        jne       ..B1.69
..B1.68:
        cmpl      $0, 96(%rsp)
        je        ..B1.70
..B1.69:
        fldt      96(%rsp)
        lea       _TWO_75(%rip), %rdx
        movb      105(%rsp), %sil
        lea       _ones(%rip), %rdi
        andb      $-128, %sil
        lea       _small_value_80(%rip), %rax
        lea       8+_TWO_75(%rip), %r9
        fldl      (%rdx)
        shrb      $7, %sil
        fmul      %st(1), %st
        fldt      (%rax)
        fmul      %st(0), %st
        movzbl    %sil, %r8d
        fstpt     16(%rsp)
        fxch      %st(1)
        fmull     (%rdi,%r8,8)
        fsubrp    %st, %st(1)
        fmull     (%r9)
        fstpt     (%rsp)
        jmp       ..B1.16
..B1.70:
        fldt      96(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.16
..B1.71:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.75
..B1.72:
        orl       $-64768, %eax
        movw      %ax, 80(%rsp)
..B1.73:
        fldcw     80(%rsp)
..B1.74:
        movzbl    105(%rsp), %r8d
        movb      $1, %cl
        andl      $128, %r8d
        shrl      $7, %r8d
..B1.75:
        testl     %r8d, %r8d
        je        ..B1.78
..B1.76:
        cmpl      $-2147483648, 100(%rsp)
        jne       ..B1.78
..B1.77:
        cmpl      $0, 96(%rsp)
        je        ..B1.82
..B1.78:
        fldt      96(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.79:
        testb     %cl, %cl
        je        ..B1.81
..B1.80:
        fldcw     82(%rsp)
..B1.81:
        fldt      (%rsp)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B1.82:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 48(%rsp)
        fldl      48(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.79
        .align    16,0x90
	.cfi_endproc
	.type	log1pl,@function
	.size	log1pl,.-log1pl
	.data
# -- End  log1pl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 8
zero_one:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x3ff00000
	.type	zero_one,@object
	.size	zero_one,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
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
ranges:
	.long	1078558720
	.long	3221127168
	.type	ranges,@object
	.size	ranges,8
	.align 4
_shifters:
	.long	0
	.long	1098383360
	.long	0
	.long	1123549184
	.type	_shifters,@object
	.size	_shifters,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_Q3:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	4096
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	53521
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	65509
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	43631
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	43690
	.word	6
	.word	32768
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	60885
	.word	52536
	.word	52444
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	14363
	.word	62279
	.word	27629
	.word	62293
	.word	49080
	.word	0
	.word	0
	.word	0
	.word	44555
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	64968
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	50395
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	39590
	.word	43714
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	15749
	.word	18782
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	61040
	.word	7204
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	10454
	.word	61175
	.word	14561
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	57771
	.word	22430
	.word	52985
	.word	52428
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	7036
	.word	15828
	.word	36565
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	17194
	.word	46991
	.word	54030
	.word	43687
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	63541
	.word	11931
	.word	21910
	.word	40326
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	36457
	.word	1697
	.word	61895
	.word	37932
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	65454
	.word	16107
	.word	6024
	.word	35469
	.word	16379
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
	.type	_Q,@object
	.size	_Q,256
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
