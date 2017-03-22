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
	.file "libm_cosl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_cosl_k80
	.text
       .align    16,0x90
	.globl __libm_cosl_k80
__libm_cosl_k80:
# parameter 1: 144 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_cosl_k80.1:
..L2:

        pushq     %rbx
	.cfi_def_cfa_offset 16
	.cfi_offset 3, -16
        pushq     %rbp
	.cfi_def_cfa_offset 24
	.cfi_offset 6, -24
        subq      $120, %rsp
	.cfi_def_cfa_offset 144
        movq      %rdi, %rbp
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 112(%rsp)
        movzwl    152(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16378, %eax
        jge       ..B1.18
..B1.2:
        cmpl      $16372, %eax
        jge       ..B1.16
..B1.3:
        cmpl      $16364, %eax
        jge       ..B1.14
..B1.4:
        cmpl      $16308, %eax
        jge       ..B1.12
..B1.5:
        testl     %eax, %eax
        jne       ..B1.8
..B1.6:
        cmpl      $0, 148(%rsp)
        jne       ..B1.8
..B1.7:
        cmpl      $0, 144(%rsp)
        je        ..B1.9
..B1.8:
        lea       _ones(%rip), %rax
        lea       _small_value_80(%rip), %rdx
        lea       _zeros(%rip), %rcx
        fldl      (%rax)
        fldt      (%rdx)
        fsubr     %st(1), %st
        fstpt     (%rbp)
        fldl      (%rcx)
        fstpt     16(%rbp)
        jmp       ..B1.10
..B1.9:
        lea       _ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        fldl      (%rax)
        fstpt     (%rbp)
        fldt      (%rbp)
        fldl      (%rdx)
        fstpt     16(%rbp)
..B1.10:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.28
..B1.11:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.12:
        fldt      144(%rsp)
        lea       _CP3(%rip), %rdx
        lea       _ones(%rip), %rax
        fldt      (%rdx)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        fldl      (%rax)
        fstpt     (%rbp)
        fldt      (%rbp)
        movq      112(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B1.28
..B1.13:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.14:
        fldt      144(%rsp)
        lea       16+_CP2(%rip), %rdx
        fmul      %st(0), %st
        lea       _CP2(%rip), %rcx
        fldt      (%rdx)
        lea       _ones(%rip), %rax
        fmul      %st(1), %st
        fldl      (%rax)
        fstpt     (%rbp)
        fldt      (%rbp)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        movq      112(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        jne       ..B1.28
..B1.15:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.16:
        fldt      144(%rsp)
        lea       48+_CP1(%rip), %rdx
        fmul      %st(0), %st
        lea       32+_CP1(%rip), %rbx
        fld       %st(0)
        lea       16+_CP1(%rip), %rcx
        fmul      %st(1), %st
        lea       _CP1(%rip), %rsi
        fldt      (%rdx)
        lea       _ones(%rip), %rax
        fmul      %st(1), %st
        fldl      (%rax)
        fstpt     (%rbp)
        fldt      (%rbp)
        fldt      (%rcx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      (%rbx)
        fmulp     %st, %st(3)
        fldt      (%rsi)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rbp)
        movq      112(%rsp), %rbp
        xorq      %rsp, %rbp
        cmpq      %fs:40, %rbp
        jne       ..B1.28
..B1.17:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.18:
        cmpl      $16382, %eax
        jge       ..B1.20
..B1.19:
        fldt      144(%rsp)
        xorl      %ecx, %ecx
        movb      153(%rsp), %bl
        xorl      %esi, %esi
        andb      $-128, %bl
        lea       _TWO_52H(%rip), %rdx
        shrb      $7, %bl
        fldl      (%rdx)
        movzbl    %bl, %eax
        lea       _ones(%rip), %rbx
        fxch      %st(1)
        fmull     (%rbx,%rax,8)
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fstpt     16(%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        jmp       ..B1.23
..B1.20:
        je        ..B1.33
..B1.21:
        fldt      144(%rsp)
        xorl      %edi, %edi
        movb      153(%rsp), %bl
        andb      $-128, %bl
        shrb      $7, %bl
        movzbl    %bl, %eax
        lea       _ones(%rip), %rbx
        fmull     (%rbx,%rax,8)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 160
        lea       112(%rsp), %rsi
        fstpt     (%rsp)
..___tag_value___libm_cosl_k80.41:
        call      __libm_reduce_pi04l@PLT
..___tag_value___libm_cosl_k80.42:
..B1.38:
        movl      %eax, %esi
        addq      $16, %rsp
	.cfi_def_cfa_offset 144
..B1.22:
        fldl      96(%rsp)
        lea       3(%rsi), %ecx
        lea       _TWO_52H(%rip), %rax
        fld       %st(0)
        fldl      104(%rsp)
        movsd     96(%rsp), %xmm0
        shrl      $2, %ecx
        mulsd     (%rax), %xmm0
        andl      $1, %ecx
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        faddp     %st, %st(1)
..B1.23:
        fldt      16(%rsp)
        fld       %st(2)
        fmul      %st(1), %st
        lea       _TWO_53H(%rip), %rax
        fxch      %st(1)
        fmul      %st(2), %st
        incl      %esi
        faddp     %st, %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fld       %st(2)
        fmul      %st(3), %st
        testl     $2, %esi
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(0)
        faddp     %st, %st(2)
        fldl      (%rax)
        fstpt     48(%rsp)
        fldl      (%rbx,%rcx,8)
        je        ..B1.25
..B1.24:
        lea       112+_SP(%rip), %rax
        lea       80+_SP(%rip), %rdx
        fstpt     (%rsp)
        lea       48+_SP(%rip), %rcx
        lea       128+_SP(%rip), %rdi
        lea       16+_SP(%rip), %rsi
        lea       96+_SP(%rip), %r8
        lea       64+_SP(%rip), %r9
        fldt      (%rax)
        lea       32+_SP(%rip), %r10
        lea       _SP(%rip), %r11
        lea       160+_SP(%rip), %rax
        fmul      %st(2), %st
        fldt      (%rdx)
        lea       144+_SP(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fldt      32(%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      64(%rsp)
        fldt      80(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      (%rdx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fldt      48(%rsp)
        fmul      %st(1), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fld       %st(2)
        fldt      16(%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%rbp)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
        fldl      (%rbx)
        jmp       ..B1.26
..B1.25:
        fstp      %st(4)
        fstp      %st(4)
        lea       112+_CP(%rip), %rax
        lea       80+_CP(%rip), %rdx
        lea       48+_CP(%rip), %rcx
        lea       16+_CP(%rip), %rsi
        lea       128+_CP(%rip), %rdi
        lea       96+_CP(%rip), %r8
        lea       64+_CP(%rip), %r9
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
        fldt      32(%rsp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      64(%rsp)
        fldt      80(%rsp)
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fldt      (%rdx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fld       %st(4)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fldt      48(%rsp)
        fmul      %st(2), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldl      (%rbx)
        fadd      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     (%rbp)
        fmulp     %st, %st(1)
        fstpt     16(%rbp)
..B1.26:
        movq      112(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.28
..B1.27:
        addq      $120, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 16
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 144
	.cfi_offset 3, -16
	.cfi_offset 6, -24
..B1.28:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.33:
        movl      148(%rsp), %eax
        cmpl      $-921707870, %eax
        jb        ..B1.19
..B1.34:
        jne       ..B1.21
..B1.35:
        cmpl      $560513589, 144(%rsp)
        jbe       ..B1.19
        jmp       ..B1.21
        .align    16,0x90
	.cfi_endproc
	.type	__libm_cosl_k80,@function
	.size	__libm_cosl_k80,.-__libm_cosl_k80
	.data
# -- End  __libm_cosl_k80
	.section .rodata, "a"
	.align 4
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
_CP3:
	.word	0
	.word	65450
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.type	_CP3,@object
	.size	_CP3,16
	.align 2
_CP2:
	.word	63855
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	18238
	.word	17476
	.word	43656
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_CP2,@object
	.size	_CP2,32
	.align 2
_CP1:
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	47533
	.word	43689
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	14131
	.word	49466
	.word	24756
	.word	46603
	.word	49141
	.word	0
	.word	0
	.word	0
	.word	37142
	.word	18013
	.word	35855
	.word	53259
	.word	16367
	.word	0
	.word	0
	.word	0
	.type	_CP1,@object
	.size	_CP1,64
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
