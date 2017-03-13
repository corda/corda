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
	.file "libm_sinl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_sinl_k80
	.text
       .align    16,0x90
	.globl __libm_sinl_k80
__libm_sinl_k80:
# parameter 1: 160 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_sinl_k80.1:
..L2:

        pushq     %r15
	.cfi_def_cfa_offset 16
	.cfi_offset 15, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        pushq     %rbp
	.cfi_def_cfa_offset 32
	.cfi_offset 6, -32
        subq      $128, %rsp
	.cfi_def_cfa_offset 160
        movq      %rdi, %r15
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 120(%rsp)
        movzwl    168(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16378, %eax
        jge       ..B1.13
..B1.2:
        fldt      160(%rsp)
        cmpl      $16372, %eax
        jge       ..B1.11
..B1.3:
        cmpl      $16364, %eax
        jge       ..B1.9
..B1.4:
        fstpt     (%r15)
        fldt      (%r15)
        cmpl      $16308, %eax
        jge       ..B1.6
..B1.5:
        fstp      %st(0)
        lea       _zeros(%rip), %rax
        lea       _ones(%rip), %rdx
        fldl      (%rax)
        fstpt     16(%r15)
        fldl      (%rdx)
        jmp       ..B1.7
..B1.6:
        lea       _SP3(%rip), %rax
        lea       _ones(%rip), %rdx
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        fldl      (%rdx)
..B1.7:
        movq      120(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.23
..B1.8:
        addq      $128, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 15, -16
..B1.9:
        fld       %st(0)
        lea       16+_SP2(%rip), %rax
        fmul      %st(1), %st
        lea       _SP2(%rip), %rdx
        lea       _ones(%rip), %rcx
        fxch      %st(1)
        fstpt     (%r15)
        fldt      (%r15)
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        movsd     (%rcx), %xmm0
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        movq      120(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        jne       ..B1.24
..B1.10:
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        addq      $128, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 15, -16
..B1.11:
        fld       %st(0)
        lea       48+_SP1(%rip), %rax
        fmul      %st(1), %st
        lea       32+_SP1(%rip), %rcx
        fld       %st(0)
        lea       16+_SP1(%rip), %rdx
        fmul      %st(1), %st
        lea       _SP1(%rip), %rbx
        lea       _ones(%rip), %rbp
        fxch      %st(2)
        fstpt     (%r15)
        fldt      (%r15)
        fldt      (%rax)
        fmul      %st(3), %st
        fldt      (%rdx)
        movsd     (%rbp), %xmm0
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        fmulp     %st, %st(4)
        fldt      (%rbx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        movq      120(%rsp), %rsi
        xorq      %rsp, %rsi
        cmpq      %fs:40, %rsi
        jne       ..B1.24
..B1.12:
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        addq      $128, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 15, -16
..B1.13:
        cmpl      $16382, %eax
        jge       ..B1.15
..B1.14:
        fldt      160(%rsp)
        xorl      %ecx, %ecx
        movb      169(%rsp), %bl
        lea       _ones(%rip), %rbp
        andb      $-128, %bl
        lea       _TWO_52H(%rip), %rax
        shrb      $7, %bl
        fldl      (%rax)
        movzbl    %bl, %ebx
        fxch      %st(1)
        fmull     (%rbp,%rbx,8)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fstpt     24(%rsp)
        fldt      24(%rsp)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.18
..B1.15:
        je        ..B1.27
..B1.16:
        fldt      160(%rsp)
        lea       _ones(%rip), %rbp
        movb      169(%rsp), %al
        xorl      %edi, %edi
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ebx
        fmull     (%rbp,%rbx,8)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 176
        lea       120(%rsp), %rsi
        fstpt     (%rsp)
..___tag_value___libm_sinl_k80.44:
        call      __libm_reduce_pi04l@PLT
..___tag_value___libm_sinl_k80.45:
..B1.32:
        movl      %eax, %ecx
        addq      $16, %rsp
	.cfi_def_cfa_offset 160
..B1.17:
        fldl      104(%rsp)
        lea       1(%rcx), %eax
        lea       _TWO_52H(%rip), %rdx
        fld       %st(0)
        movsd     104(%rsp), %xmm0
        sarl      $2, %eax
        movslq    %eax, %rax
        mulsd     (%rdx), %xmm0
        xorq      %rax, %rbx
        movsd     %xmm0, 16(%rsp)
        andq      $1, %rbx
        fldl      16(%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fldl      112(%rsp)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     24(%rsp)
..B1.18:
        fldt      24(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fmulp     %st, %st(2)
        lea       _TWO_53H(%rip), %rax
        incl      %ecx
        testb     $2, %cl
        faddp     %st, %st(1)
        fstpt     40(%rsp)
        fldt      40(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     72(%rsp)
        fldt      72(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     56(%rsp)
        fldt      56(%rsp)
        fxch      %st(1)
        fstpt     88(%rsp)
        fldt      88(%rsp)
        fmul      %st(0), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      (%rax)
        je        ..B1.20
..B1.19:
        fstp      %st(4)
        fstp      %st(2)
        lea       112+_CP(%rip), %rax
        lea       80+_CP(%rip), %rdx
        lea       128+_CP(%rip), %rdi
        lea       96+_CP(%rip), %r8
        lea       48+_CP(%rip), %rcx
        lea       64+_CP(%rip), %r9
        lea       16+_CP(%rip), %rsi
        lea       32+_CP(%rip), %r10
        fldt      (%rax)
        lea       _CP(%rip), %r11
        lea       160+_CP(%rip), %rax
        fmul      %st(1), %st
        fldt      (%rdx)
        lea       144+_CP(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%rdi)
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
        fldt      72(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      56(%rsp)
        fldt      40(%rsp)
        fldt      88(%rsp)
        fldt      (%rax)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(4)
        fldt      (%rdx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fadd      %st(1), %st
        fmul      %st, %st(3)
        fadd      %st(3), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(1)
        fldl      (%rbp)
        fld       %st(0)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldl      (%rbp,%rbx,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%r15)
        jmp       ..B1.21
..B1.20:
        lea       112+_SP(%rip), %rax
        lea       80+_SP(%rip), %rdx
        lea       48+_SP(%rip), %rcx
        lea       128+_SP(%rip), %rdi
        lea       16+_SP(%rip), %rsi
        lea       96+_SP(%rip), %r8
        lea       64+_SP(%rip), %r9
        lea       32+_SP(%rip), %r10
        fldt      (%rax)
        lea       _SP(%rip), %r11
        lea       160+_SP(%rip), %rax
        fmul      %st(3), %st
        fldt      (%rdx)
        lea       144+_SP(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rdi)
        fmul      %st(4), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      (%r11)
        faddp     %st, %st(4)
        fldt      72(%rsp)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      56(%rsp)
        fldt      40(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      (%rdx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fldt      88(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(3)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      24(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      (%rbp,%rbx,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%r15)
        fmulp     %st, %st(1)
        fstpt     16(%r15)
        fldl      (%rbp)
..B1.21:
        movq      120(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.23
..B1.22:
        addq      $128, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 160
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 15, -16
..B1.23:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.24:
        call      __stack_chk_fail@PLT
..B1.27:
        movl      164(%rsp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.14
..B1.28:
        jne       ..B1.16
..B1.29:
        cmpl      $560513589, 160(%rsp)
        jbe       ..B1.14
        jmp       ..B1.16
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sinl_k80,@function
	.size	__libm_sinl_k80,.-__libm_sinl_k80
	.data
# -- End  __libm_sinl_k80
	.section .rodata, "a"
	.align 4
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
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 2
_SP3:
	.word	17476
	.word	43656
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.type	_SP3,@object
	.size	_SP3,16
	.align 2
_SP2:
	.word	43210
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	1587
	.word	57422
	.word	34932
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP2,@object
	.size	_SP2,32
	.align 2
_SP1:
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	12518
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	43088
	.word	7396
	.word	207
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	55827
	.word	48618
	.word	3602
	.word	47342
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_SP1,@object
	.size	_SP1,64
	.align 2
_CP:
	.word	51631
	.word	16464
	.word	16497
	.word	44012
	.word	16306
	.word	0
	.word	0
	.word	0
	.word	43685
	.word	43690
	.word	43690
	.word	43690
	.word	16372
	.word	0
	.word	0
	.word	0
	.word	46557
	.word	2912
	.word	24758
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	28380
	.word	3328
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	23816
	.word	50302
	.word	32187
	.word	37874
	.word	49129
	.word	0
	.word	0
	.word	0
	.word	49866
	.word	64527
	.word	51070
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	65056
	.word	3638
	.word	41889
	.word	51659
	.word	49114
	.word	0
	.word	0
	.word	0
	.word	13834
	.word	11317
	.word	33607
	.word	55101
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	40709
	.word	42374
	.word	64411
	.word	45709
	.word	49098
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
	.word	43008
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP,@object
	.size	_CP,176
	.align 2
_SP:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49136
	.word	0
	.word	0
	.word	0
	.word	34948
	.word	34952
	.word	34952
	.word	34952
	.word	16368
	.word	0
	.word	0
	.word	0
	.word	53243
	.word	3328
	.word	208
	.word	53261
	.word	49138
	.word	0
	.word	0
	.word	0
	.word	30577
	.word	46649
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	11760
	.word	43464
	.word	11071
	.word	55090
	.word	49125
	.word	0
	.word	0
	.word	0
	.word	47000
	.word	43232
	.word	12444
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	52983
	.word	47009
	.word	40440
	.word	55103
	.word	49110
	.word	0
	.word	0
	.word	0
	.word	35433
	.word	14927
	.word	40941
	.word	51860
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	1340
	.word	16361
	.word	50747
	.word	38523
	.word	49094
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	34816
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_SP,@object
	.size	_SP,176
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
