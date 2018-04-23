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
	.file "log2l.c"
	.text
..TXTST0:
# -- Begin  log2l
	.text
       .align    16,0x90
	.globl log2l
log2l:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_log2l.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %sil, %sil
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzbl    57(%rsp), %r8d
        andl      $128, %r8d
        shrl      $7, %r8d
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        movl      %r8d, %edi
        andl      $32767, %edx
        shll      $15, %edi
        orl       %edx, %edi
        shll      $16, %edi
        movzwl    54(%rsp), %ecx
        orl       %ecx, %edi
        cmpl      $2147450880, %edi
        jae       ..B1.28
..B1.4:
        addl      $-1073676224, %edi
        cmpl      $32863, %edi
        ja        ..B1.13
..B1.5:
        cmpl      $16383, %edx
        je        ..B1.37
..B1.6:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.10
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.8:
        fldcw     24(%rsp)
..B1.9:
        movb      $1, %sil
..B1.10:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        lea       112+_P(%rip), %rcx
        lea       80+_P(%rip), %rdi
        lea       48+_P(%rip), %r8
        lea       96+_P(%rip), %r10
        lea       _TWO_32H(%rip), %rdx
        lea       64+_P(%rip), %r11
        lea       16+_P(%rip), %r9
        testb     %sil, %sil
        fldt      (%rcx)
        lea       128+_P(%rip), %rcx
        fldt      (%rdi)
        fldl      (%rdx)
        lea       _P(%rip), %rdx
        fldt      (%r8)
        fxch      %st(4)
        faddl     (%rax)
        lea       32+_P(%rip), %rax
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fld       %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(1)
        fsub      %st(3), %st
        fxch      %st(2)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r9)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        fmul      %st(5), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      (%rax)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      (%rdx)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      (%rcx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.12
..B1.11:
        fldcw     26(%rsp)
..B1.12:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.13:
        movzwl    26(%rsp), %edi
        movl      %edi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.17
..B1.14:
        orl       $-64768, %edi
        movw      %di, 24(%rsp)
..B1.15:
        fldcw     24(%rsp)
..B1.16:
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        movb      $1, %sil
        andl      $32767, %edx
..B1.17:
        movl      $-16383, %ecx
        testl     %edx, %edx
        jne       ..B1.20
..B1.18:
        fldt      48(%rsp)
        lea       _TWO_75(%rip), %rcx
        lea       _zeros(%rip), %rax
        fmull     (%rcx)
        movl      $-16458, %ecx
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldl      (%rax)
        movsd     (%rax), %xmm0
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.19
        je        ..B1.39
..B1.19:
        movzwl    56(%rsp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
..B1.20:
        movl      52(%rsp), %edi
        cmpl      $-2147483648, %edi
        jne       ..B1.25
..B1.21:
        cmpl      $0, 48(%rsp)
        jne       ..B1.25
..B1.22:
        addl      %edx, %ecx
        movl      %ecx, 16(%rsp)
        testb     %sil, %sil
        fildl     16(%rsp)
        fstpt     (%rsp)
        je        ..B1.24
..B1.23:
        fldcw     26(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.25:
        addl      %edx, %ecx
        lea       _TWO_32H(%rip), %rdx
        andl      $-32768, %eax
        lea       8+_ones(%rip), %r8
        orl       $-49153, %eax
        lea       112+_P(%rip), %r9
        movw      %ax, 56(%rsp)
        lea       80+_P(%rip), %r10
        fldt      48(%rsp)
        lea       48+_P(%rip), %r11
        fld       %st(0)
        fldl      (%rdx)
        lea       16+_P(%rip), %rdx
        shrl      $23, %edi
        fadd      %st, %st(1)
        movzbl    %dil, %eax
        fsubrp    %st, %st(1)
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        fsubr     %st, %st(1)
        flds      (%rdi,%rax,4)
        lea       96+_P(%rip), %rdi
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddl     (%r8)
        fld       %st(0)
        lea       64+_P(%rip), %r8
        movl      %ecx, 16(%rsp)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r9)
        lea       32+_P(%rip), %r9
        fmul      %st(2), %st
        fldt      (%r10)
        lea       _P(%rip), %r10
        shlq      $4, %rax
        faddp     %st, %st(1)
        fmul      %st(2), %st
        testb     %sil, %sil
        fldt      (%r11)
        lea       128+_P(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        movq      __libm_log2l_table_256@GOTPCREL(%rip), %rdx
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdi)
        fmul      %st(3), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r10)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%r11)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fildl     16(%rsp)
        fxch      %st(2)
        faddl     8(%rdx,%rax)
        fxch      %st(2)
        faddl     (%rax,%rdx)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.27
..B1.26:
        fldcw     26(%rsp)
..B1.27:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.28:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.32
..B1.29:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.30:
        fldcw     24(%rsp)
..B1.31:
        movzbl    57(%rsp), %r8d
        movb      $1, %sil
        andl      $128, %r8d
        shrl      $7, %r8d
..B1.32:
        testl     %r8d, %r8d
        jne       ..B1.42
..B1.33:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.34:
        testb     %sil, %sil
        je        ..B1.36
..B1.35:
        fldcw     26(%rsp)
..B1.36:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.37:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.6
..B1.38:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.39:
        lea       8+_ones(%rip), %rax
        testb     %sil, %sil
        movsd     (%rax), %xmm1
        divsd     %xmm0, %xmm1
        je        ..B1.41
..B1.40:
        fldcw     26(%rsp)
..B1.41:
        movsd     %xmm1, 16(%rsp)
        fldl      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.42:
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.44
..B1.43:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.33
        jmp       ..B1.47
..B1.44:
        testl     %eax, %eax
        jne       ..B1.47
..B1.45:
        cmpq      $0, 48(%rsp)
        jne       ..B1.47
..B1.46:
        lea       8+_ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        divsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.34
..B1.47:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.34
        .align    16,0x90
	.cfi_endproc
	.type	log2l,@function
	.size	log2l,.-log2l
	.data
# -- End  log2l
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
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
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
_P:
	.word	17354
	.word	24031
	.word	3064
	.word	38062
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	60493
	.word	53279
	.word	20193
	.word	63032
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	62043
	.word	23575
	.word	15145
	.word	47274
	.word	49149
	.word	0
	.word	0
	.word	0
	.word	33329
	.word	29325
	.word	25223
	.word	37819
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	5068
	.word	45194
	.word	20193
	.word	63032
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	43239
	.word	63403
	.word	6517
	.word	54028
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	25259
	.word	45714
	.word	45135
	.word	47274
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	14848
	.word	47274
	.word	16383
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,144
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
