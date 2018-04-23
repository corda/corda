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
	.file "log10l.c"
	.text
..TXTST0:
# -- Begin  log10l
	.text
       .align    16,0x90
	.globl log10l
log10l:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_log10l.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %r8b, %r8b
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzbl    57(%rsp), %r9d
        andl      $128, %r9d
        shrl      $7, %r9d
        movzwl    56(%rsp), %esi
        movl      %esi, %edi
        movl      %r9d, %edx
        andl      $32767, %edi
        shll      $15, %edx
        movl      52(%rsp), %ecx
        orl       %edi, %edx
        movl      %ecx, %eax
        shll      $16, %edx
        shrl      $16, %eax
        orl       %eax, %edx
        cmpl      $2147450880, %edx
        jae       ..B1.29
..B1.4:
        addl      $-1073676224, %edx
        cmpl      $32863, %edx
        ja        ..B1.13
..B1.5:
        cmpl      $16383, %edi
        je        ..B1.38
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
        movb      $1, %r8b
..B1.10:
        fldt      48(%rsp)
        lea       8+_ones(%rip), %rax
        lea       112+_P(%rip), %rcx
        lea       80+_P(%rip), %rsi
        lea       48+_P(%rip), %rdi
        lea       96+_P(%rip), %r10
        lea       _TWO_32H(%rip), %rdx
        lea       64+_P(%rip), %r11
        lea       16+_P(%rip), %r9
        testb     %r8b, %r8b
        fldt      (%rcx)
        lea       128+_P(%rip), %rcx
        fldt      (%rsi)
        fldl      (%rdx)
        lea       _P(%rip), %rdx
        fldt      (%rdi)
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
        cmpl      $16383, %edi
        jl        ..B1.19
..B1.14:
        cmpl      $16471, %edi
        jge       ..B1.19
..B1.15:
        movl      $1431655766, %eax
        lea       -16383(%rdi), %r9d
        imull     %r9d
        sarl      $31, %r9d
        subl      %r9d, %edx
        lea       _exact_values(%rip), %r9
        addl      %edx, %edx
        movslq    %edx, %rdx
        shlq      $4, %rdx
        movzwl    8(%rdx,%r9), %r10d
        andl      $32767, %r10d
        cmpl      %r10d, %edi
        jne       ..B1.19
..B1.16:
        cmpl      4(%rdx,%r9), %ecx
        jne       ..B1.19
..B1.17:
        movl      48(%rsp), %eax
        cmpl      (%rdx,%r9), %eax
        jne       ..B1.19
..B1.18:
        fldt      16(%r9,%rdx)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.19:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.23
..B1.20:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.21:
        fldcw     24(%rsp)
..B1.22:
        movzwl    56(%rsp), %esi
        movl      %esi, %edi
        movb      $1, %r8b
        andl      $32767, %edi
..B1.23:
        movl      $-16383, %eax
        testl     %edi, %edi
        jne       ..B1.26
..B1.24:
        fldt      48(%rsp)
        lea       _TWO_75(%rip), %rax
        lea       _zeros(%rip), %rdx
        fmull     (%rax)
        movl      $-16458, %eax
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldl      (%rdx)
        movsd     (%rdx), %xmm0
        fucomip   %st(1), %st
        fstp      %st(0)
        jp        ..B1.25
        je        ..B1.40
..B1.25:
        movzwl    56(%rsp), %esi
        movl      %esi, %edi
        andl      $32767, %edi
..B1.26:
        lea       _TWO_32H(%rip), %rcx
        andl      $-32768, %esi
        orl       $-49153, %esi
        addl      %eax, %edi
        movw      %si, 56(%rsp)
        lea       8+_ones(%rip), %r9
        fldt      48(%rsp)
        lea       112+_P(%rip), %r10
        fld       %st(0)
        lea       80+_P(%rip), %r11
        fldl      (%rcx)
        lea       16+_P(%rip), %rcx
        movl      52(%rsp), %edx
        fadd      %st, %st(1)
        shrl      $23, %edx
        fsubrp    %st, %st(1)
        movzbl    %dl, %eax
        lea       48+_P(%rip), %rdx
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rsi
        fsubr     %st, %st(1)
        flds      (%rsi,%rax,4)
        lea       96+_P(%rip), %rsi
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddl     (%r9)
        fld       %st(0)
        lea       64+_P(%rip), %r9
        movl      %edi, 16(%rsp)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r10)
        lea       32+_P(%rip), %r10
        fmul      %st(2), %st
        fldt      (%r11)
        lea       _P(%rip), %r11
        shlq      $4, %rax
        faddp     %st, %st(1)
        fmul      %st(2), %st
        testb     %r8b, %r8b
        fldt      (%rdx)
        lea       128+_P(%rip), %rdx
        movq      __libm_log10l_table_256@GOTPCREL(%rip), %rdi
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        fmul      %st(3), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      (%r11)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%rdx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fildl     16(%rsp)
        fldt      .L_2il0floatpacket.0(%rip)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(2)
        faddl     8(%rdi,%rax)
        faddp     %st, %st(3)
        faddl     (%rax,%rdi)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.28
..B1.27:
        fldcw     26(%rsp)
..B1.28:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.29:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.33
..B1.30:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.31:
        fldcw     24(%rsp)
..B1.32:
        movzbl    57(%rsp), %r9d
        movb      $1, %r8b
        andl      $128, %r9d
        shrl      $7, %r9d
..B1.33:
        testl     %r9d, %r9d
        jne       ..B1.43
..B1.34:
        fldt      48(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     (%rsp)
..B1.35:
        testb     %r8b, %r8b
        je        ..B1.37
..B1.36:
        fldcw     26(%rsp)
..B1.37:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.38:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.6
..B1.39:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.40:
        lea       8+_ones(%rip), %rax
        testb     %r8b, %r8b
        movsd     (%rax), %xmm1
        divsd     %xmm0, %xmm1
        je        ..B1.42
..B1.41:
        fldcw     26(%rsp)
..B1.42:
        movsd     %xmm1, 16(%rsp)
        fldl      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.43:
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.45
..B1.44:
        movq      $0x8000000000000000, %rax
        cmpq      48(%rsp), %rax
        jne       ..B1.34
        jmp       ..B1.48
..B1.45:
        testl     %eax, %eax
        jne       ..B1.48
..B1.46:
        cmpq      $0, 48(%rsp)
        jne       ..B1.48
..B1.47:
        lea       8+_ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        divsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.35
..B1.48:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, 16(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.35
        .align    16,0x90
	.cfi_endproc
	.type	log10l,@function
	.size	log10l,.-log10l
	.data
# -- End  log10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0xc8,0xd4,0x0e,0xee,0x0c,0x01,0x86,0xd8,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x85,0x9a,0x20,0x9a,0xfd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
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
	.word	23339
	.word	38197
	.word	10353
	.word	43319
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	29077
	.word	14120
	.word	55465
	.word	56923
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	63176
	.word	31258
	.word	15131
	.word	37949
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	29577
	.word	14120
	.word	55465
	.word	56923
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	61319
	.word	34328
	.word	5050
	.word	45539
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	52415
	.word	26390
	.word	15131
	.word	37949
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	16765
	.word	7621
	.word	20590
	.word	65056
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	14162
	.word	11829
	.word	26041
	.word	56924
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	55296
	.word	56923
	.word	16381
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,144
	.align 2
_exact_values:
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
	.word	49151
	.word	0
	.word	0
	.word	0
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
	.word	32768
	.word	16383
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
	.word	32768
	.word	16384
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
	.word	49152
	.word	16384
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
	.word	32768
	.word	16385
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
	.word	40960
	.word	16385
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
	.word	0
	.word	49152
	.word	16385
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
	.word	0
	.word	57344
	.word	16385
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
	.word	0
	.word	32768
	.word	16386
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
	.word	0
	.word	36864
	.word	16386
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
	.word	49151
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
	.word	0
	.word	40960
	.word	16386
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
	.word	0
	.word	45056
	.word	16386
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
	.word	0
	.word	49152
	.word	16386
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
	.word	0
	.word	0
	.word	53248
	.word	16386
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
	.word	0
	.word	0
	.word	57344
	.word	16386
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
	.word	0
	.word	0
	.word	61440
	.word	16386
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
	.word	0
	.word	0
	.word	32768
	.word	16387
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
	.word	0
	.word	0
	.word	34816
	.word	16387
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
	.word	0
	.word	0
	.word	36864
	.word	16387
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
	.word	49151
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
	.word	0
	.word	0
	.word	38912
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	43008
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	45056
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	47104
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	53248
	.word	16387
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
	.word	0
	.word	0
	.word	0
	.word	55296
	.word	16387
	.word	0
	.word	0
	.word	0
	.type	_exact_values,@object
	.size	_exact_values,960
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
