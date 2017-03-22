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
	.file "j0.c"
	.text
..TXTST0:
# -- Begin  j0
	.text
       .align    16,0x90
	.globl j0
j0:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_j0.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        subq      $32, %rsp
	.cfi_def_cfa_offset 48
        xorb      %bl, %bl
        movsd     %xmm0, 8(%rsp)
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movl      12(%rsp), %eax
        andl      $2147483647, %eax
        cmpl      $2146435072, %eax
        jge       ..B1.14
..B1.4:
        cmpl      $1012924416, %eax
        jl        ..B1.13
..B1.5:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.7:
        fldcw     16(%rsp)
..B1.8:
        movb      $1, %bl
..B1.9:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 64
        movsd     24(%rsp), %xmm0
        andps     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
..___tag_value_j0.7:
        call      __j0l@PLT
..___tag_value_j0.8:
..B1.20:
        addq      $16, %rsp
	.cfi_def_cfa_offset 48
..B1.10:
        fstpl     (%rsp)
        testb     %bl, %bl
        movsd     (%rsp), %xmm0
        je        ..B1.12
..B1.11:
        fldcw     18(%rsp)
..B1.12:
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -16
..B1.13:
        lea       _ones(%rip), %rax
        movsd     8(%rsp), %xmm1
        andps     .L_2il0floatpacket.0(%rip), %xmm1
        movsd     (%rax), %xmm0
        subsd     %xmm1, %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -16
..B1.14:
        andl      $-2146435073, %eax
        orl       8(%rsp), %eax
        je        ..B1.16
..B1.15:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     8(%rsp), %xmm0
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -16
..B1.16:
        lea       _zeros(%rip), %rax
        movsd     (%rax), %xmm0
..B1.17:
        addq      $32, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	j0,@function
	.size	j0,.-j0
	.data
# -- End  j0
	.text
# -- Begin  __j0l
	.text
       .align    16,0x90
	.globl __j0l
__j0l:
# parameter 1: 96 + %rsp
..B2.1:
	.cfi_startproc
..___tag_value___j0l.29:
..L30:

        subq      $88, %rsp
	.cfi_def_cfa_offset 96
        lea       176+_S(%rip), %rax
        fldt      96(%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        jb        ..B2.9
..B2.2:
        lea       _S(%rip), %rcx
        fldt      (%rcx)
        fcomip    %st(1), %st
        jbe       ..B2.4
..B2.3:
        lea       _Z(%rip), %rax
        lea       _P1(%rip), %rdx
        lea       16+_P1(%rip), %rcx
        lea       32+_P1(%rip), %rsi
        lea       48+_P1(%rip), %rdi
        lea       64+_P1(%rip), %r8
        lea       80+_P1(%rip), %r9
        lea       96+_P1(%rip), %r10
        fldt      (%rax)
        lea       112+_P1(%rip), %r11
        lea       128+_P1(%rip), %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      (%rdx)
        lea       144+_P1(%rip), %rdx
        fmul      %st(1), %st
        fldt      (%rcx)
        lea       160+_P1(%rip), %rcx
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       176+_P1(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       192+_P1(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       208+_P1(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       224+_P1(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       240+_P1(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       256+_P1(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       272+_P1(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       288+_P1(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       304+_P1(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       320+_P1(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      (%rsi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.4:
        movl      $1, %edx
        movl      $16, %eax
..B2.5:
        fldt      (%rax,%rcx)
        fcomip    %st(1), %st
        ja        ..B2.8
        jp        ..B2.8
..B2.6:
        incq      %rdx
        addq      $16, %rax
        cmpq      $11, %rdx
        jl        ..B2.5
..B2.7:
        movq      %rdx, %rax
        shlq      $4, %rax
..B2.8:
        lea       _Z(%rip), %rcx
        fldt      (%rax,%rcx)
        lea       (%rdx,%rdx,4), %rax
        shlq      $6, %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        lea       _P(%rip), %rdx
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      -320(%rax,%rdx)
        fmul      %st(1), %st
        fldt      -304(%rax,%rdx)
        fmul      %st(2), %st
        fldt      -288(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -272(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -256(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -240(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -224(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -208(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -192(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -176(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -160(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -144(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -128(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -112(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -96(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -80(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -64(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      -48(%rax,%rdx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      -32(%rax,%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      -16(%rax,%rdx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.9:
        lea       _ones(%rip), %rax
        lea       _val_36_0L(%rip), %rdx
        lea       _PP(%rip), %rcx
        lea       _QP(%rip), %rsi
        lea       16+_PP(%rip), %rdi
        lea       16+_QP(%rip), %r8
        lea       32+_PP(%rip), %r9
        lea       32+_QP(%rip), %r10
        fldl      (%rax)
        lea       48+_PP(%rip), %r11
        fdiv      %st(1), %st
        lea       48+_QP(%rip), %rax
        fxch      %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdx)
        lea       64+_PP(%rip), %rdx
        fmulp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rcx)
        lea       64+_QP(%rip), %rcx
        fmul      %st(1), %st
        fldt      (%rsi)
        lea       80+_PP(%rip), %rsi
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80+_QP(%rip), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        lea       96+_PP(%rip), %r8
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        lea       96+_QP(%rip), %r9
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        lea       112+_PP(%rip), %r10
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       112+_QP(%rip), %r11
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        lea       128+_PP(%rip), %rax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       128+_QP(%rip), %rdx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        lea       144+_PP(%rip), %rcx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        lea       144+_QP(%rip), %rsi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        lea       80(%rsp), %rdi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r8)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r10)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        movl      $-1, %edx
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rcx)
        faddp     %st, %st(1)
        fstpt     -48(%rdi)
        fldt      (%rsi)
        lea       64(%rsp), %rsi
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     -16(%rsi)
..___tag_value___j0l.37:
        call      __libm_sincos_pi4l@PLT
..___tag_value___j0l.38:
..B2.18:
        fldt      112(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
..B2.10:
        fldt      48(%rsp)
        lea       _val_0_03L(%rip), %rax
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fmulp     %st, %st(2)
        fsub      %st, %st(1)
        fld       %st(1)
        fabs      
        fxch      %st(1)
        fabs      
        fldt      (%rax)
        fmulp     %st, %st(1)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B2.12
        jp        ..B2.12
..B2.11:
        fstp      %st(1)
        fldt      (%rsp)
        lea       _tonpi(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fsqrt     
        fmulp     %st, %st(1)
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
..B2.12:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 112
        lea       16(%rsp), %rdi
        fstpt     (%rsp)
        call      __ltoq@PLT
..B2.20:
        addq      $16, %rsp
	.cfi_def_cfa_offset 96
        movaps    (%rsp), %xmm0
..___tag_value___j0l.44:
        call      __j0q@PLT
..___tag_value___j0l.45:
..B2.13:
        movaps    %xmm0, (%rsp)
        lea       (%rsp), %rdi
        call      __qtol@PLT
..B2.21:
        fstpl     8(%rsp)
        movsd     8(%rsp), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
..B2.14:
        addq      $88, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__j0l,@function
	.size	__j0l,.-__j0l
	.data
# -- End  __j0l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
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
	.align 2
_S:
	.word	17728
	.word	31608
	.word	43949
	.word	62778
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	32551
	.word	41874
	.word	44957
	.word	57471
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	18949
	.word	61409
	.word	34437
	.word	41670
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	4396
	.word	34605
	.word	55192
	.word	54573
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	9307
	.word	10310
	.word	55728
	.word	33731
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	7613
	.word	2917
	.word	18234
	.word	40173
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	24292
	.word	42693
	.word	42782
	.word	46612
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12570
	.word	29960
	.word	47214
	.word	53050
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	24415
	.word	36838
	.word	59299
	.word	59487
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	32322
	.word	22879
	.word	15219
	.word	32962
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	56404
	.word	12107
	.word	18542
	.word	36180
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	18639
	.word	44635
	.word	10526
	.word	39398
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_S,@object
	.size	_S,192
	.align 2
_Z:
	.word	64993
	.word	47321
	.word	43380
	.word	39400
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	10427
	.word	24946
	.word	31449
	.word	45220
	.word	16385
	.word	0
	.word	0
	.word	0
	.word	20142
	.word	26358
	.word	43878
	.word	35445
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	45380
	.word	5013
	.word	8196
	.word	48298
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	36049
	.word	37774
	.word	2551
	.word	61157
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	44499
	.word	20687
	.word	35324
	.word	37009
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	37149
	.word	40892
	.word	28299
	.word	43441
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	59921
	.word	2742
	.word	56472
	.word	49873
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	35749
	.word	60152
	.word	42287
	.word	56306
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	36553
	.word	24054
	.word	44174
	.word	62739
	.word	16387
	.word	0
	.word	0
	.word	0
	.word	12707
	.word	13183
	.word	28829
	.word	34586
	.word	16388
	.word	0
	.word	0
	.word	0
	.word	24309
	.word	45263
	.word	7124
	.word	37803
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_Z,@object
	.size	_Z,192
	.align 2
_P1:
	.word	41974
	.word	50359
	.word	55
	.word	49432
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	46729
	.word	24287
	.word	10077
	.word	43682
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	54556
	.word	53463
	.word	26566
	.word	47408
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	61625
	.word	30678
	.word	34691
	.word	56786
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	32205
	.word	55323
	.word	47622
	.word	59679
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	29650
	.word	64976
	.word	4011
	.word	64391
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	31057
	.word	21065
	.word	6935
	.word	59041
	.word	16343
	.word	0
	.word	0
	.word	0
	.word	36662
	.word	39842
	.word	13910
	.word	57058
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	21879
	.word	21964
	.word	7382
	.word	44566
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	35904
	.word	38403
	.word	56995
	.word	38056
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	18301
	.word	44371
	.word	51380
	.word	49148
	.word	16358
	.word	0
	.word	0
	.word	0
	.word	24372
	.word	2933
	.word	6164
	.word	36455
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	49391
	.word	5716
	.word	15337
	.word	37270
	.word	49133
	.word	0
	.word	0
	.word	0
	.word	25965
	.word	16194
	.word	56568
	.word	46953
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	45667
	.word	13925
	.word	5584
	.word	35484
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	49022
	.word	26773
	.word	37576
	.word	36812
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	36832
	.word	53763
	.word	58864
	.word	36312
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	33543
	.word	46187
	.word	17185
	.word	59351
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	40102
	.word	5284
	.word	63312
	.word	56590
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	857
	.word	43156
	.word	55730
	.word	34022
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	52121
	.word	9280
	.word	35775
	.word	47190
	.word	16318
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,336
	.align 2
_P:
	.word	25701
	.word	56152
	.word	43593
	.word	44746
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	48832
	.word	40943
	.word	62738
	.word	40407
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	27954
	.word	18691
	.word	56371
	.word	61331
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	59673
	.word	34431
	.word	10026
	.word	51320
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	19628
	.word	6656
	.word	43961
	.word	34852
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	10797
	.word	38877
	.word	18617
	.word	49740
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	55276
	.word	31862
	.word	41256
	.word	61817
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	56169
	.word	58452
	.word	6548
	.word	36482
	.word	16351
	.word	0
	.word	0
	.word	0
	.word	22526
	.word	12680
	.word	38609
	.word	41195
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	35707
	.word	29316
	.word	48923
	.word	38609
	.word	49126
	.word	0
	.word	0
	.word	0
	.word	54924
	.word	17159
	.word	27854
	.word	39283
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	7410
	.word	20104
	.word	60324
	.word	54971
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	42961
	.word	8606
	.word	41997
	.word	49951
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	20439
	.word	51893
	.word	4000
	.word	47026
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	59638
	.word	54459
	.word	36852
	.word	37873
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	53889
	.word	4789
	.word	65352
	.word	38847
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	1714
	.word	3910
	.word	34394
	.word	55562
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	303
	.word	16967
	.word	38717
	.word	64635
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	5713
	.word	19393
	.word	12368
	.word	44599
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	21960
	.word	14969
	.word	53843
	.word	43675
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	52500
	.word	174
	.word	33388
	.word	44907
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	36805
	.word	62602
	.word	43329
	.word	35133
	.word	16327
	.word	0
	.word	0
	.word	0
	.word	27761
	.word	25841
	.word	3607
	.word	63286
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	6612
	.word	54609
	.word	58140
	.word	43860
	.word	49103
	.word	0
	.word	0
	.word	0
	.word	37993
	.word	17870
	.word	56109
	.word	35856
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	61675
	.word	27140
	.word	56422
	.word	41440
	.word	16343
	.word	0
	.word	0
	.word	0
	.word	56971
	.word	52432
	.word	57814
	.word	63191
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	25412
	.word	37118
	.word	3118
	.word	58735
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	38291
	.word	36319
	.word	26004
	.word	41665
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	50251
	.word	22788
	.word	9163
	.word	59245
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	15018
	.word	28
	.word	26139
	.word	39028
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	33331
	.word	48247
	.word	16919
	.word	39314
	.word	49132
	.word	0
	.word	0
	.word	0
	.word	9684
	.word	33609
	.word	24106
	.word	48128
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	51011
	.word	47822
	.word	659
	.word	60363
	.word	16369
	.word	0
	.word	0
	.word	0
	.word	37534
	.word	11575
	.word	45084
	.word	34566
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	45912
	.word	27680
	.word	6912
	.word	42099
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	56715
	.word	12873
	.word	49782
	.word	46172
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	26728
	.word	49648
	.word	65144
	.word	32891
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	40426
	.word	15254
	.word	52157
	.word	35579
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	38384
	.word	45177
	.word	47511
	.word	58357
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	58158
	.word	44133
	.word	45474
	.word	44841
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	25822
	.word	50828
	.word	25446
	.word	60617
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	15324
	.word	12408
	.word	38243
	.word	63699
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	14774
	.word	16677
	.word	5572
	.word	37105
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	22149
	.word	54660
	.word	59654
	.word	35846
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	11697
	.word	52785
	.word	2274
	.word	34156
	.word	49111
	.word	0
	.word	0
	.word	0
	.word	53627
	.word	64285
	.word	10253
	.word	62534
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	24801
	.word	65406
	.word	2005
	.word	46814
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	32190
	.word	19121
	.word	50201
	.word	40636
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	54751
	.word	12899
	.word	38715
	.word	45207
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	43490
	.word	42082
	.word	27556
	.word	37283
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	22028
	.word	56865
	.word	50900
	.word	56715
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	16346
	.word	12991
	.word	64075
	.word	44659
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	52492
	.word	22409
	.word	7105
	.word	40702
	.word	49137
	.word	0
	.word	0
	.word	0
	.word	6702
	.word	59476
	.word	47051
	.word	61808
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	62316
	.word	14189
	.word	19866
	.word	53935
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	9195
	.word	51568
	.word	61124
	.word	40040
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	6439
	.word	11299
	.word	34414
	.word	41343
	.word	49144
	.word	0
	.word	0
	.word	0
	.word	46739
	.word	24947
	.word	62261
	.word	60937
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	58365
	.word	23430
	.word	12962
	.word	38007
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	58251
	.word	41189
	.word	49383
	.word	44339
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	48598
	.word	60621
	.word	33089
	.word	52046
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	15609
	.word	33598
	.word	37151
	.word	63031
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	47302
	.word	16218
	.word	41542
	.word	62539
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	57122
	.word	24762
	.word	37625
	.word	35182
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	14554
	.word	11813
	.word	4690
	.word	56239
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	54525
	.word	1724
	.word	15173
	.word	60713
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	63047
	.word	59633
	.word	16097
	.word	37474
	.word	49118
	.word	0
	.word	0
	.word	0
	.word	47674
	.word	64044
	.word	50935
	.word	38909
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	10733
	.word	57181
	.word	7346
	.word	35019
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	60139
	.word	34186
	.word	61790
	.word	35089
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	42252
	.word	41404
	.word	34184
	.word	42385
	.word	49131
	.word	0
	.word	0
	.word	0
	.word	1522
	.word	6058
	.word	55347
	.word	41209
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	8896
	.word	65510
	.word	60684
	.word	58885
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	5547
	.word	57306
	.word	64532
	.word	55968
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	14723
	.word	16104
	.word	43168
	.word	38160
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	64145
	.word	20360
	.word	50512
	.word	35772
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	36202
	.word	63428
	.word	51591
	.word	58021
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	65464
	.word	45991
	.word	59516
	.word	54144
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	24568
	.word	20436
	.word	61634
	.word	37862
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	45319
	.word	25104
	.word	37206
	.word	43484
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	21849
	.word	55910
	.word	13070
	.word	44688
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	3252
	.word	24429
	.word	41684
	.word	61678
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	18877
	.word	5474
	.word	3532
	.word	52835
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	29000
	.word	47276
	.word	64838
	.word	34154
	.word	49108
	.word	0
	.word	0
	.word	0
	.word	20967
	.word	63980
	.word	13404
	.word	46613
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	52562
	.word	53463
	.word	21966
	.word	58373
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	9777
	.word	2939
	.word	17549
	.word	60815
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	26283
	.word	15936
	.word	57935
	.word	36992
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	53769
	.word	29752
	.word	63351
	.word	55571
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	39733
	.word	64164
	.word	46820
	.word	32952
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	176
	.word	24001
	.word	47175
	.word	32916
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	42514
	.word	53223
	.word	40190
	.word	38228
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	32337
	.word	51109
	.word	43299
	.word	44928
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	59799
	.word	62803
	.word	25577
	.word	51379
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	14970
	.word	20015
	.word	15936
	.word	57562
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	65161
	.word	31438
	.word	7531
	.word	65214
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	20513
	.word	44789
	.word	62889
	.word	43571
	.word	49143
	.word	0
	.word	0
	.word	0
	.word	4307
	.word	19900
	.word	64182
	.word	49211
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	23601
	.word	8473
	.word	7427
	.word	49494
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	7362
	.word	8526
	.word	7254
	.word	42403
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	35188
	.word	37258
	.word	23196
	.word	38516
	.word	16326
	.word	0
	.word	0
	.word	0
	.word	28716
	.word	28394
	.word	59353
	.word	59952
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	5380
	.word	17306
	.word	43095
	.word	44938
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	62603
	.word	29353
	.word	43042
	.word	32969
	.word	16340
	.word	0
	.word	0
	.word	0
	.word	14219
	.word	29360
	.word	8286
	.word	39065
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	51755
	.word	8774
	.word	37073
	.word	55906
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	56892
	.word	44416
	.word	43797
	.word	50190
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	61329
	.word	48733
	.word	6023
	.word	35132
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	7541
	.word	9021
	.word	40244
	.word	45179
	.word	16356
	.word	0
	.word	0
	.word	0
	.word	26533
	.word	29344
	.word	18926
	.word	62060
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	30572
	.word	2598
	.word	8389
	.word	52807
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	51943
	.word	22643
	.word	23287
	.word	35717
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	64388
	.word	35284
	.word	47919
	.word	35658
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	29632
	.word	11036
	.word	42088
	.word	47700
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	31422
	.word	44089
	.word	40511
	.word	45376
	.word	49140
	.word	0
	.word	0
	.word	0
	.word	24082
	.word	23937
	.word	40663
	.word	60291
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	35871
	.word	18767
	.word	59204
	.word	34260
	.word	16375
	.word	0
	.word	0
	.word	0
	.word	55945
	.word	59437
	.word	40276
	.word	45420
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	36087
	.word	44447
	.word	31316
	.word	65259
	.word	16318
	.word	0
	.word	0
	.word	0
	.word	33015
	.word	55199
	.word	13485
	.word	41205
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	28040
	.word	38678
	.word	23200
	.word	33402
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	24126
	.word	8131
	.word	32863
	.word	58069
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	35392
	.word	16287
	.word	64004
	.word	38564
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	24419
	.word	65021
	.word	26451
	.word	63502
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	50428
	.word	39809
	.word	46675
	.word	33151
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	43497
	.word	65182
	.word	62592
	.word	53510
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	13653
	.word	47792
	.word	51483
	.word	42124
	.word	16349
	.word	0
	.word	0
	.word	0
	.word	51670
	.word	53417
	.word	2690
	.word	33418
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	18048
	.word	20017
	.word	2806
	.word	37530
	.word	49124
	.word	0
	.word	0
	.word	0
	.word	28377
	.word	1564
	.word	33730
	.word	58682
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	50365
	.word	41139
	.word	10086
	.word	43478
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	61280
	.word	6173
	.word	58518
	.word	33596
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	37780
	.word	41188
	.word	55897
	.word	58318
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	54954
	.word	62311
	.word	27470
	.word	44684
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	57442
	.word	38159
	.word	64608
	.word	36945
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	24087
	.word	55434
	.word	11948
	.word	56328
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	3958
	.word	29640
	.word	49133
	.word	55700
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	27700
	.word	45302
	.word	5983
	.word	42389
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	40427
	.word	54367
	.word	12482
	.word	37240
	.word	16320
	.word	0
	.word	0
	.word	0
	.word	59852
	.word	29007
	.word	23004
	.word	39969
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	34211
	.word	57602
	.word	36659
	.word	58360
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	44442
	.word	16364
	.word	9911
	.word	56161
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	28102
	.word	25155
	.word	37530
	.word	33414
	.word	49102
	.word	0
	.word	0
	.word	0
	.word	6555
	.word	37232
	.word	8513
	.word	61132
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	19389
	.word	63961
	.word	54328
	.word	56964
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	46901
	.word	14299
	.word	54235
	.word	51269
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	21562
	.word	53008
	.word	37762
	.word	35900
	.word	49117
	.word	0
	.word	0
	.word	0
	.word	39053
	.word	49830
	.word	37166
	.word	63741
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	45681
	.word	41863
	.word	1807
	.word	63504
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	32524
	.word	20075
	.word	31047
	.word	55727
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	19491
	.word	55333
	.word	6932
	.word	36559
	.word	49130
	.word	0
	.word	0
	.word	0
	.word	44244
	.word	33552
	.word	34722
	.word	63577
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	16901
	.word	42071
	.word	41001
	.word	48810
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	62438
	.word	12223
	.word	44142
	.word	42160
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	23223
	.word	6493
	.word	11343
	.word	61664
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	62523
	.word	30008
	.word	29610
	.word	53050
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	14947
	.word	31100
	.word	26993
	.word	46432
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	54892
	.word	55125
	.word	25696
	.word	39893
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	64089
	.word	10154
	.word	50994
	.word	64131
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	25208
	.word	35455
	.word	35497
	.word	38745
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	22601
	.word	23897
	.word	16529
	.word	51375
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	39957
	.word	27913
	.word	24611
	.word	54302
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	45382
	.word	20134
	.word	36574
	.word	58452
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	42298
	.word	30543
	.word	37555
	.word	58890
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	22768
	.word	53677
	.word	2298
	.word	49504
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	58111
	.word	6687
	.word	35097
	.word	49208
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	10603
	.word	28285
	.word	57192
	.word	62022
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	32868
	.word	54034
	.word	283
	.word	60966
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	45349
	.word	58038
	.word	8163
	.word	54564
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	55920
	.word	35455
	.word	60237
	.word	53133
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	60123
	.word	63321
	.word	17152
	.word	62548
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	39317
	.word	30736
	.word	60380
	.word	60458
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	49393
	.word	44945
	.word	51803
	.word	41616
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	20738
	.word	26362
	.word	9659
	.word	40012
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	60223
	.word	27976
	.word	18717
	.word	52467
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	54339
	.word	42843
	.word	18062
	.word	50282
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	34001
	.word	60559
	.word	43103
	.word	39476
	.word	49142
	.word	0
	.word	0
	.word	0
	.word	26526
	.word	28531
	.word	16125
	.word	37792
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	6095
	.word	56607
	.word	38079
	.word	40348
	.word	49087
	.word	0
	.word	0
	.word	0
	.word	15971
	.word	7192
	.word	49983
	.word	37562
	.word	16323
	.word	0
	.word	0
	.word	0
	.word	50443
	.word	47213
	.word	11879
	.word	45566
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	11802
	.word	10368
	.word	62578
	.word	52530
	.word	49099
	.word	0
	.word	0
	.word	0
	.word	31461
	.word	32590
	.word	25910
	.word	51580
	.word	49101
	.word	0
	.word	0
	.word	0
	.word	23034
	.word	4503
	.word	56149
	.word	56800
	.word	16339
	.word	0
	.word	0
	.word	0
	.word	23084
	.word	49532
	.word	8891
	.word	43466
	.word	16341
	.word	0
	.word	0
	.word	0
	.word	52278
	.word	51985
	.word	56128
	.word	47325
	.word	49115
	.word	0
	.word	0
	.word	0
	.word	64046
	.word	3409
	.word	24460
	.word	54209
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	21815
	.word	55080
	.word	59949
	.word	58476
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	52803
	.word	16320
	.word	8321
	.word	47500
	.word	16355
	.word	0
	.word	0
	.word	0
	.word	52718
	.word	62993
	.word	9405
	.word	50844
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	54524
	.word	45491
	.word	53115
	.word	54272
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	17147
	.word	2195
	.word	64554
	.word	57739
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	25716
	.word	30593
	.word	61559
	.word	36022
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	34582
	.word	27844
	.word	9712
	.word	38156
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	11089
	.word	32571
	.word	7121
	.word	45346
	.word	49139
	.word	0
	.word	0
	.word	0
	.word	19256
	.word	10500
	.word	41195
	.word	47904
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	34688
	.word	57357
	.word	16578
	.word	34099
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	33812
	.word	7938
	.word	37351
	.word	35991
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	39461
	.word	41125
	.word	38968
	.word	43650
	.word	16319
	.word	0
	.word	0
	.word	0
	.word	44025
	.word	18687
	.word	5475
	.word	36436
	.word	49091
	.word	0
	.word	0
	.word	0
	.word	23695
	.word	42035
	.word	32968
	.word	40701
	.word	49093
	.word	0
	.word	0
	.word	0
	.word	38927
	.word	50812
	.word	3019
	.word	50863
	.word	16331
	.word	0
	.word	0
	.word	0
	.word	61445
	.word	40975
	.word	17938
	.word	45887
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	29652
	.word	52899
	.word	3582
	.word	54866
	.word	49107
	.word	0
	.word	0
	.word	0
	.word	20058
	.word	59066
	.word	2994
	.word	38517
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	39274
	.word	56137
	.word	36322
	.word	45609
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	15638
	.word	5070
	.word	53701
	.word	47866
	.word	16348
	.word	0
	.word	0
	.word	0
	.word	51166
	.word	27629
	.word	45907
	.word	56238
	.word	49122
	.word	0
	.word	0
	.word	0
	.word	29387
	.word	28474
	.word	13265
	.word	41814
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	6001
	.word	55757
	.word	63641
	.word	48808
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	65207
	.word	5152
	.word	25385
	.word	47657
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	16879
	.word	18288
	.word	49778
	.word	55345
	.word	49135
	.word	0
	.word	0
	.word	0
	.word	63014
	.word	23411
	.word	42365
	.word	63147
	.word	49134
	.word	0
	.word	0
	.word	0
	.word	41060
	.word	50204
	.word	58630
	.word	36532
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	38440
	.word	64136
	.word	63792
	.word	39699
	.word	16371
	.word	0
	.word	0
	.word	0
	.word	22495
	.word	32960
	.word	60573
	.word	45833
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	59007
	.word	19608
	.word	21801
	.word	59681
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	39231
	.word	52779
	.word	63106
	.word	34425
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	40257
	.word	55273
	.word	33242
	.word	51859
	.word	16320
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,3520
	.align 2
_val_36_0L:
	.word	0
	.word	0
	.word	0
	.word	36864
	.word	16388
	.word	0
	.word	0
	.word	0
	.type	_val_36_0L,@object
	.size	_val_36_0L,16
	.align 2
_PP:
	.word	3555
	.word	4199
	.word	61716
	.word	36505
	.word	49086
	.word	0
	.word	0
	.word	0
	.word	21640
	.word	56203
	.word	47336
	.word	57608
	.word	16322
	.word	0
	.word	0
	.word	0
	.word	3059
	.word	27554
	.word	42044
	.word	45622
	.word	49095
	.word	0
	.word	0
	.word	0
	.word	55815
	.word	35656
	.word	54748
	.word	47307
	.word	16332
	.word	0
	.word	0
	.word	0
	.word	42025
	.word	6728
	.word	28939
	.word	34692
	.word	49106
	.word	0
	.word	0
	.word	0
	.word	58943
	.word	11189
	.word	14960
	.word	38786
	.word	16344
	.word	0
	.word	0
	.word	0
	.word	43526
	.word	20005
	.word	29826
	.word	37014
	.word	49119
	.word	0
	.word	0
	.word	0
	.word	58976
	.word	17109
	.word	36079
	.word	36708
	.word	16359
	.word	0
	.word	0
	.word	0
	.word	58243
	.word	36408
	.word	14563
	.word	58254
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_PP,@object
	.size	_PP,160
	.align 2
_QP:
	.word	30466
	.word	14887
	.word	33885
	.word	35327
	.word	16316
	.word	0
	.word	0
	.word	0
	.word	41139
	.word	65374
	.word	59488
	.word	50915
	.word	49088
	.word	0
	.word	0
	.word	0
	.word	35675
	.word	30946
	.word	22284
	.word	35505
	.word	16325
	.word	0
	.word	0
	.word	0
	.word	65216
	.word	29458
	.word	42913
	.word	63176
	.word	49097
	.word	0
	.word	0
	.word	0
	.word	5130
	.word	24339
	.word	48915
	.word	38634
	.word	16335
	.word	0
	.word	0
	.word	0
	.word	30040
	.word	27658
	.word	23448
	.word	34596
	.word	49109
	.word	0
	.word	0
	.word	0
	.word	37816
	.word	17431
	.word	24401
	.word	49646
	.word	16347
	.word	0
	.word	0
	.word	0
	.word	4377
	.word	35057
	.word	45578
	.word	33037
	.word	49123
	.word	0
	.word	0
	.word	0
	.word	19863
	.word	65266
	.word	6202
	.word	53939
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	49142
	.word	0
	.word	0
	.word	0
	.type	_QP,@object
	.size	_QP,160
	.align 2
_val_0_03L:
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_val_0_03L,@object
	.size	_val_0_03L,16
	.align 2
_tonpi:
	.word	5418
	.word	20036
	.word	33646
	.word	41721
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_tonpi,@object
	.size	_tonpi,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
