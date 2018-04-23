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
	.file "acoshl.c"
	.text
..TXTST0:
# -- Begin  acoshl
	.text
       .align    16,0x90
	.globl acoshl
acoshl:
# parameter 1: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_acoshl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %cl, %cl
..B1.2:
        fnstcw    50(%rsp)
..B1.3:
        movzwl    72(%rsp), %edi
        movl      %edi, %edx
        movzbl    73(%rsp), %esi
        andl      $32767, %edx
        shrl      $7, %esi
        shll      $15, %esi
        orl       %edx, %esi
        shll      $16, %esi
        movzwl    70(%rsp), %eax
        orl       %eax, %esi
        addl      $-1073709056, %esi
        cmpl      $1073741824, %esi
        jae       ..B1.40
..B1.4:
        cmpl      $8192, %esi
        jae       ..B1.21
..B1.5:
        cmpl      $64, %esi
        jae       ..B1.14
..B1.6:
        cmpl      $16383, %edx
        je        ..B1.38
..B1.7:
        movzwl    50(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.11
..B1.8:
        orl       $-64768, %edx
        movw      %dx, 48(%rsp)
..B1.9:
        fldcw     48(%rsp)
..B1.10:
        movb      $1, %cl
..B1.11:
        fldt      64(%rsp)
        lea       8+_ones(%rip), %rax
        lea       _TWO_52H(%rip), %rdx
        lea       80+_Q1(%rip), %rsi
        lea       48+_Q1(%rip), %rdi
        lea       16+_Q1(%rip), %r8
        lea       64+_Q1(%rip), %r9
        lea       32+_Q1(%rip), %r10
        lea       _Q1(%rip), %r11
        testb     %cl, %cl
        fldl      (%rdx)
        fld       %st(0)
        fxch      %st(2)
        faddl     (%rax)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fsub      %st(4), %st
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fadd      %st(4), %st
        fsqrt     
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(6)
        fld       %st(4)
        fmul      %st(1), %st
        fsubrp    %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(5)
        fdivrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.13
..B1.12:
        fldcw     50(%rsp)
..B1.13:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.14:
        movzwl    50(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.18
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 48(%rsp)
..B1.16:
        fldcw     48(%rsp)
..B1.17:
        movb      $1, %cl
..B1.18:
        fldt      64(%rsp)
        lea       8+_ones(%rip), %rax
        lea       _TWO_52H(%rip), %rdx
        lea       208+_Q(%rip), %rsi
        lea       176+_Q(%rip), %rdi
        lea       144+_Q(%rip), %r8
        lea       112+_Q(%rip), %r9
        lea       80+_Q(%rip), %r10
        lea       48+_Q(%rip), %r11
        testb     %cl, %cl
        fldl      (%rdx)
        lea       192+_Q(%rip), %rdx
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      (%rdi)
        lea       128+_Q(%rip), %rdi
        fldt      (%r8)
        lea       96+_Q(%rip), %r8
        fxch      %st(3)
        faddl     (%rax)
        lea       16+_Q(%rip), %rax
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(2)
        fld       %st(1)
        fsub      %st(4), %st
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(5)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(0), %st
        fadd      %st, %st(5)
        fxch      %st(3)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rsi)
        lea       160+_Q(%rip), %rsi
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(6)
        fxch      %st(4)
        fmul      %st, %st(5)
        fldt      (%r9)
        lea       64+_Q(%rip), %r9
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      (%r10)
        lea       32+_Q(%rip), %r10
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      (%r11)
        lea       _Q(%rip), %r11
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      (%rax)
        lea       240+_Q(%rip), %rax
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      (%rdx)
        lea       224+_Q(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
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
        fmulp     %st, %st(1)
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fmulp     %st, %st(1)
        fldt      (%rdx)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fldt      32(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st, %st(4)
        fld       %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fadd      %st(2), %st
        fsqrt     
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(1), %st
        fsubrp    %st, %st(4)
        fxch      %st(5)
        faddp     %st, %st(3)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(0)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(4)
        fadd      %st(3), %st
        fmulp     %st, %st(4)
        fld       %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.20
..B1.19:
        fldcw     50(%rsp)
..B1.20:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.21:
        movzwl    50(%rsp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.25
..B1.22:
        orl       $-64768, %esi
        movw      %si, 48(%rsp)
..B1.23:
        fldcw     48(%rsp)
..B1.24:
        movzwl    72(%rsp), %edi
        movl      %edi, %edx
        movb      $1, %cl
        andl      $32767, %edx
..B1.25:
        cmpl      $16417, %edx
        jge       ..B1.34
..B1.26:
        fldt      64(%rsp)
        cmpl      $16388, %edx
        jge       ..B1.28
..B1.27:
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        lea       8+_ones(%rip), %r8
        lea       _ones(%rip), %rdx
        fldl      (%rax)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(3)
        fsubr     %st, %st(3)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        faddl     (%r8)
        fld       %st(0)
        fadd      %st(4), %st
        fsqrt     
        fadd      %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fsubrp    %st, %st(1)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      (%rdx)
        jmp       ..B1.33
..B1.28:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _TWO_32H(%rip), %rax
        fmul      %st(1), %st
        lea       _ones(%rip), %rsi
        fld       %st(0)
        lea       8+_ones(%rip), %r8
        fldl      (%rax)
        cmpl      $16394, %edx
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubr     %st(1), %st
        fldl      (%rsi)
        fldl      (%r8)
        jge       ..B1.30
..B1.29:
        fstp      %st(3)
        fld       %st(3)
        fadd      %st(3), %st
        fld       %st(4)
        fadd      %st(2), %st
        fmulp     %st, %st(1)
        fsqrt     
        faddp     %st, %st(4)
        fxch      %st(3)
        fdivrp    %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.33
..B1.30:
        fstp      %st(4)
        cmpl      $16401, %edx
        jge       ..B1.32
..B1.31:
        fld       %st(3)
        fdiv      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.33
..B1.32:
        fxch      %st(2)
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
..B1.33:
        fldt      16(%rsp)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     64(%rsp)
        movzwl    72(%rsp), %edx
        andl      $32767, %edx
        movl      %edx, %eax
        addl      $-16383, %edx
        negl      %eax
        fxch      %st(1)
        fstpt     (%rsp)
        addl      $-2, %eax
        movzwl    8(%rsp), %esi
        andl      $32767, %eax
        andl      $-32768, %esi
        orl       %eax, %esi
        movw      %si, 8(%rsp)
        fldt      (%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%rsp)
        fmulp     %st, %st(1)
        jmp       ..B1.35
..B1.34:
        lea       _TWO_52H(%rip), %rax
        andl      $-32768, %edi
        orl       $-49153, %edi
        addl      $-16382, %edx
        movw      %di, 72(%rsp)
        lea       8+_ones(%rip), %r8
        fldt      64(%rsp)
        fld       %st(0)
        fldl      (%rax)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubrp    %st, %st(1)
..B1.35:
        fldt      16(%rsp)
        lea       16+_P(%rip), %r9
        movl      68(%rsp), %esi
        lea       64+_P(%rip), %r10
        shrl      $23, %esi
        lea       32+_P(%rip), %r11
        movzbl    %sil, %eax
        fldt      (%r9)
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        movl      %edx, 32(%rsp)
        lea       80+_P(%rip), %rdx
        fildl     32(%rsp)
        flds      (%rdi,%rax,4)
        fmul      %st, %st(3)
        fmulp     %st, %st(4)
        fxch      %st(2)
        faddl     (%r8)
        fld       %st(0)
        lea       48+_P(%rip), %r8
        shlq      $4, %rax
        fadd      %st(4), %st
        fld       %st(0)
        testb     %cl, %cl
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       _P(%rip), %rdx
        fmul      %st(2), %st
        fldt      (%r8)
        movq      __libm_logl_table_256@GOTPCREL(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%r10)
        fmul      %st(1), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.3(%rip)
        fmulp     %st, %st(3)
        faddl     8(%rsi,%rax)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddl     (%rax,%rsi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        je        ..B1.37
..B1.36:
        fldcw     50(%rsp)
..B1.37:
        fldt      16(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.38:
        movq      $0x8000000000000000, %rax
        cmpq      64(%rsp), %rax
        jne       ..B1.7
..B1.39:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.40:
        movzwl    50(%rsp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.44
..B1.41:
        orl       $-64768, %esi
        movw      %si, 48(%rsp)
..B1.42:
        fldcw     48(%rsp)
..B1.43:
        movzwl    72(%rsp), %edx
        movb      $1, %cl
        andl      $32767, %edx
..B1.44:
        cmpl      $32767, %edx
        jne       ..B1.47
..B1.45:
        movq      $0x8000000000000000, %rax
        cmpq      64(%rsp), %rax
        je        ..B1.51
..B1.46:
        fldt      64(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
        jmp       ..B1.48
..B1.47:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     (%rsp)
..B1.48:
        testb     %cl, %cl
        je        ..B1.50
..B1.49:
        fldcw     50(%rsp)
..B1.50:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.51:
        testb     $-128, 73(%rsp)
        je        ..B1.46
        jmp       ..B1.47
        .align    16,0x90
	.cfi_endproc
	.type	acoshl,@function
	.size	acoshl,.-acoshl
	.data
# -- End  acoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
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
_Q1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	37372
	.word	39321
	.word	39321
	.word	39321
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	18586
	.word	55971
	.word	28086
	.word	46811
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	15813
	.word	59837
	.word	36396
	.word	63715
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	53727
	.word	45078
	.word	40330
	.word	46917
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	48396
	.word	8155
	.word	33124
	.word	36271
	.word	16371
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,96
	.align 2
_Q:
	.word	48098
	.word	43690
	.word	43690
	.word	43690
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	20923
	.word	52462
	.word	52428
	.word	52428
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	38527
	.word	56172
	.word	28086
	.word	46811
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	47050
	.word	58060
	.word	36408
	.word	63715
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	28121
	.word	12720
	.word	53620
	.word	46917
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	5738
	.word	23392
	.word	25199
	.word	36391
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	9822
	.word	24561
	.word	52037
	.word	58572
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	32045
	.word	31633
	.word	41878
	.word	48451
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	16262
	.word	24754
	.word	13643
	.word	40941
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	36557
	.word	45689
	.word	21168
	.word	35169
	.word	16366
	.word	0
	.word	0
	.word	0
	.word	29793
	.word	40103
	.word	23573
	.word	60969
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	51189
	.word	17117
	.word	14297
	.word	51855
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	6461
	.word	12463
	.word	55551
	.word	38957
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	34939
	.word	19802
	.word	20012
	.word	37205
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43696
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39328
	.word	16377
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
