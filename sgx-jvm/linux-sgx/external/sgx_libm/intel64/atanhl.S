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
	.file "atanhl.c"
	.text
..TXTST0:
# -- Begin  atanhl
	.text
       .align    16,0x90
	.globl atanhl
atanhl:
# parameter 1: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_atanhl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %cl, %cl
..B1.2:
        fnstcw    50(%rsp)
..B1.3:
        movzwl    72(%rsp), %esi
        andl      $32767, %esi
        movzwl    50(%rsp), %eax
        cmpl      $16383, %esi
        jge       ..B1.53
..B1.4:
        cmpl      $16381, %esi
        jge       ..B1.46
..B1.5:
        cmpl      $16376, %esi
        jge       ..B1.39
..B1.6:
        cmpl      $16371, %esi
        jge       ..B1.32
..B1.7:
        cmpl      $16365, %esi
        jge       ..B1.25
..B1.8:
        cmpl      $16308, %esi
        jge       ..B1.18
..B1.9:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.13
..B1.10:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.11:
        fldcw     48(%rsp)
..B1.12:
        movzwl    72(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.13:
        testl     %esi, %esi
        jle       ..B1.64
..B1.14:
        fldt      64(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       8+_TWO_75(%rip), %rdx
        fldl      (%rax)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fmull     (%rdx)
        fstpt     (%rsp)
..B1.15:
        testb     %cl, %cl
        je        ..B1.17
..B1.16:
        fldcw     50(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.18:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.22
..B1.19:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.20:
        fldcw     48(%rsp)
..B1.21:
        movb      $1, %cl
..B1.22:
        fldt      64(%rsp)
        lea       _Q3(%rip), %rax
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.24
..B1.23:
        fldcw     50(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.25:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.29
..B1.26:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.27:
        fldcw     48(%rsp)
..B1.28:
        movb      $1, %cl
..B1.29:
        fldt      64(%rsp)
        lea       16+_Q2(%rip), %rax
        fld       %st(0)
        lea       _Q2(%rip), %rdx
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.31
..B1.30:
        fldcw     50(%rsp)
..B1.31:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.32:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.36
..B1.33:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.34:
        fldcw     48(%rsp)
..B1.35:
        movb      $1, %cl
..B1.36:
        fldt      64(%rsp)
        lea       48+_Q1(%rip), %rax
        fld       %st(0)
        lea       32+_Q1(%rip), %rsi
        fmul      %st(1), %st
        lea       16+_Q1(%rip), %rdx
        fld       %st(0)
        lea       _Q1(%rip), %rdi
        fmul      %st(1), %st
        testb     %cl, %cl
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmulp     %st, %st(2)
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.38
..B1.37:
        fldcw     50(%rsp)
..B1.38:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.39:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.43
..B1.40:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.41:
        fldcw     48(%rsp)
..B1.42:
        movb      $1, %cl
..B1.43:
        fldt      64(%rsp)
        lea       _TWO_52H(%rip), %rax
        lea       176+_Q(%rip), %rdx
        lea       144+_Q(%rip), %rsi
        lea       112+_Q(%rip), %rdi
        lea       80+_Q(%rip), %r8
        lea       48+_Q(%rip), %r9
        lea       160+_Q(%rip), %r11
        fldl      (%rax)
        lea       16+_Q(%rip), %r10
        fmul      %st(1), %st
        lea       128+_Q(%rip), %rax
        fld       %st(0)
        testb     %cl, %cl
        fadd      %st(2), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fldt      (%rdx)
        fmul      %st(1), %st
        lea       96+_Q(%rip), %rdx
        fldt      (%rsi)
        lea       64+_Q(%rip), %rsi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
        lea       32+_Q(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r8)
        lea       _Q(%rip), %r8
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        lea       208+_Q(%rip), %r9
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        lea       192+_Q(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r11)
        lea       _TWO_48H(%rip), %r11
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      (%r9)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fldt      (%r10)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      32(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fldt      16(%rsp)
        fld       %st(0)
        fldl      (%r11)
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fldt      64(%rsp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      (%rsp)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.45
..B1.44:
        fldcw     50(%rsp)
..B1.45:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.46:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.50
..B1.47:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.48:
        fldcw     48(%rsp)
..B1.49:
        movb      $1, %cl
..B1.50:
        fldt      64(%rsp)
        lea       _ones(%rip), %rdi
        movb      73(%rsp), %sil
        lea       _TWO_48H(%rip), %r8
        andb      $-128, %sil
        shrb      $7, %sil
        fldl      (%rdi)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldl      (%r8)
        movzbl    %sil, %edx
        lea       48+_P(%rip), %r8
        movzwl    24(%rsp), %r10d
        andl      $-32768, %r10d
        fxch      %st(4)
        fmull     (%rdi,%rdx,8)
        lea       80+_P(%rip), %rdi
        fsubr     %st, %st(2)
        fld       %st(2)
        fmul      %st(5), %st
        fadd      %st, %st(3)
        fsubrp    %st, %st(3)
        fld       %st(2)
        fsubr     %st(2), %st
        fsub      %st(1), %st
        fld       %st(3)
        fadd      %st(1), %st
        fxch      %st(2)
        fadd      %st, %st(5)
        fld       %st(5)
        fmul      %st(7), %st
        fadd      %st, %st(6)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubr     %st(3), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fdivr     %st(3), %st
        fmul      %st, %st(6)
        fld       %st(0)
        fadd      %st(7), %st
        fsubp     %st, %st(7)
        fxch      %st(1)
        fmul      %st(6), %st
        fld       %st(1)
        fsub      %st(7), %st
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(4)
        fmul      %st(6), %st
        fsubrp    %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fldt      (%rsp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     64(%rsp)
        fldt      (%rdi)
        lea       32+_P(%rip), %rdi
        fldt      (%r8)
        lea       _P(%rip), %r8
        movzwl    72(%rsp), %esi
        andl      $32767, %esi
        movl      %esi, %r9d
        addl      $-16383, %esi
        negl      %r9d
        addl      $-2, %r9d
        andl      $32767, %r9d
        orl       %r9d, %r10d
        movw      %r10w, 24(%rsp)
        lea       8+_ones(%rip), %r10
        fldt      16(%rsp)
        movl      68(%rsp), %r11d
        shrl      $23, %r11d
        fmul      %st, %st(3)
        fmulp     %st, %st(4)
        movzbl    %r11b, %eax
        lea       16+_P(%rip), %r11
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r9
        movl      %esi, 32(%rsp)
        lea       64+_P(%rip), %rsi
        fildl     32(%rsp)
        fxch      %st(3)
        fmuls     (%r9,%rax,4)
        fxch      %st(4)
        fmuls     (%r9,%rax,4)
        fxch      %st(4)
        faddl     (%r10)
        fld       %st(0)
        shlq      $4, %rax
        fadd      %st(5), %st
        fld       %st(0)
        testb     %cl, %cl
        fmul      %st(1), %st
        fmul      %st, %st(4)
        movq      __libm_logl_table_256@GOTPCREL(%rip), %r9
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%r11)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fldt      (%rsi)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(3)
        faddl     8(%r9,%rax)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddl     (%rax,%r9)
        lea       halfs(%rip), %rax
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     (%rax,%rdx,8)
        fstpt     (%rsp)
        je        ..B1.52
..B1.51:
        fldcw     50(%rsp)
..B1.52:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.53:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.57
..B1.54:
        orl       $-64768, %eax
        movw      %ax, 48(%rsp)
..B1.55:
        fldcw     48(%rsp)
..B1.56:
        movzwl    72(%rsp), %esi
        movb      $1, %cl
        andl      $32767, %esi
..B1.57:
        cmpl      $32767, %esi
        jl        ..B1.68
..B1.58:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.60
..B1.59:
        cmpl      $0, 64(%rsp)
        je        ..B1.68
..B1.60:
        fldt      64(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.61:
        testb     %cl, %cl
        je        ..B1.63
..B1.62:
        fldcw     50(%rsp)
..B1.63:
        fldt      (%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.64:
        cmpl      $0, 68(%rsp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 64(%rsp)
        je        ..B1.67
..B1.66:
        fldt      64(%rsp)
        lea       _small_value_80(%rip), %rsi
        movb      73(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fldt      (%rsi)
        movzbl    %al, %edx
        shlq      $4, %rdx
        fldt      (%rsi,%rdx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.67:
        fldt      64(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.68:
        cmpl      $16383, %esi
        jne       ..B1.72
..B1.69:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.72
..B1.70:
        cmpl      $0, 64(%rsp)
        jne       ..B1.72
..B1.71:
        movb      73(%rsp), %al
        lea       _ones(%rip), %rdx
        andb      $-128, %al
        lea       _zeros(%rip), %rdi
        shrb      $7, %al
        movzbl    %al, %esi
        movsd     (%rdx,%rsi,8), %xmm0
        divsd     (%rdi), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.61
..B1.72:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.61
        .align    16,0x90
	.cfi_endproc
	.type	atanhl,@function
	.size	atanhl,.-atanhl
	.data
# -- End  atanhl
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
halfs:
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbfe00000
	.type	halfs,@object
	.size	halfs,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
	.word	43691
	.word	45330
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	33124
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	58478
	.word	29023
	.word	52648
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	43686
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	5258
	.word	52447
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	10439
	.word	34405
	.word	9349
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	25253
	.word	49895
	.word	32943
	.word	58261
	.word	16379
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	43101
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	4657
	.word	52441
	.word	52428
	.word	52428
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	42633
	.word	18723
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	11223
	.word	36719
	.word	14563
	.word	58254
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	64465
	.word	27664
	.word	35746
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	6969
	.word	8211
	.word	55486
	.word	40329
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	15522
	.word	40807
	.word	33480
	.word	34952
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	44124
	.word	7207
	.word	21918
	.word	61682
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	13236
	.word	62073
	.word	35694
	.word	55158
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	859
	.word	55468
	.word	55479
	.word	50356
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	19186
	.word	33607
	.word	822
	.word	41715
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	51623
	.word	9477
	.word	49456
	.word	61855
	.word	16378
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
	.word	0
	.word	0
	.word	0
	.word	52416
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,224
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
