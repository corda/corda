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
	.file "cbrtl.c"
	.text
..TXTST0:
# -- Begin  cbrtl
	.text
       .align    16,0x90
	.globl cbrtl
cbrtl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cbrtl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %r8b, %r8b
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    56(%rsp), %ecx
        movl      %ecx, %edi
        andl      $32767, %edi
        movzwl    34(%rsp), %r9d
        cmpl      $32767, %edi
        je        ..B1.20
..B1.4:
        movb      57(%rsp), %al
        movl      %r9d, %edx
        andb      $-128, %al
        andl      $768, %edx
        shrb      $7, %al
        xorl      %esi, %esi
        movzbl    %al, %eax
        cmpl      $768, %edx
        je        ..B1.8
..B1.5:
        orl       $-64768, %r9d
        movw      %r9w, 32(%rsp)
..B1.6:
        fldcw     32(%rsp)
..B1.7:
        movzwl    56(%rsp), %ecx
        movl      %ecx, %edi
        movb      $1, %r8b
        andl      $32767, %edi
..B1.8:
        testl     %edi, %edi
        jne       ..B1.14
..B1.9:
        cmpq      $0, 48(%rsp)
        jne       ..B1.13
..B1.10:
        lea       _zeros(%rip), %rdx
        testb     %r8b, %r8b
        movsd     (%rdx,%rax,8), %xmm0
        je        ..B1.12
..B1.11:
        fldcw     34(%rsp)
..B1.12:
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.13:
        fldt      48(%rsp)
        movl      $-25, %esi
        lea       _TWO_75(%rip), %rdx
        fmull     (%rdx)
        fstpt     48(%rsp)
        movzwl    56(%rsp), %ecx
        movl      %ecx, %edi
        andl      $32767, %edi
..B1.14:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       ones(%rip), %r9
        andl      $-32768, %ecx
        orl       $-49153, %ecx
        movw      %cx, 56(%rsp)
        fldl      (%r9,%rax,8)
        lea       96+_P(%rip), %r9
        movl      52(%rsp), %ecx
        shrl      $23, %ecx
        movzbl    %cl, %eax
        lea       _TWO_32H(%rip), %rcx
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %r11
        imull     $21845, %edi, %edx
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        flds      (%r11,%rax,4)
        addl      $21845, %edx
        fld       %st(0)
        lea       32+_P(%rip), %r11
        fldl      (%rcx)
        lea       80+_P(%rip), %rcx
        fld       %st(0)
        shrl      $16, %edx
        fadd      %st(4), %st
        shlq      $4, %rax
        lea       (%rdx,%rdx), %r10d
        subl      %r10d, %edi
        lea       64+_P(%rip), %r10
        fsubp     %st, %st(1)
        fmul      %st, %st(1)
        lea       10922(%rsi,%rdx), %esi
        fxch      %st(1)
        fsub      %st(4), %st
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldt      (%r9)
        lea       48+_P(%rip), %r9
        fmul      %st(2), %st
        fldt      (%r10)
        lea       16+_P(%rip), %r10
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%r11)
        lea       _P(%rip), %r11
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fmul      %st(1), %st
        fldt      (%rcx)
        fmul      %st(3), %st
        fldt      (%r9)
        movzwl    24(%rsp), %ecx
        faddp     %st, %st(1)
        fmul      %st(3), %st
        andl      $-32768, %ecx
        fldt      (%r10)
        orl       %esi, %ecx
        movw      %cx, 24(%rsp)
        subl      %edx, %edi
        movq      __libm_cbrtl_table_256@GOTPCREL(%rip), %rdx
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      (%r11)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rsp)
        fldl      8(%rdx,%rax)
        fldl      (%rdx,%rax)
        fldt      16(%rsp)
        jne       ..B1.16
..B1.15:
        fldt      (%rsp)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.17
..B1.16:
        fldt      (%rsp)
        lea       SH(%rip), %rax
        movslq    %edi, %rdi
        fxch      %st(3)
        fmull     -8(%rax,%rdi,8)
        fldl      24(%rax,%rdi,8)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmull     8(%rax,%rdi,8)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.17:
        testb     %r8b, %r8b
        je        ..B1.19
..B1.18:
        fldcw     34(%rsp)
..B1.19:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.20:
        movl      %r9d, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.26
..B1.21:
        orl       $-64768, %r9d
        movw      %r9w, 32(%rsp)
..B1.22:
        fldcw     32(%rsp)
..B1.23:
        fldt      48(%rsp)
        fstpt     (%rsp)
..B1.24:
        fldcw     34(%rsp)
..B1.25:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.26:
        fldt      48(%rsp)
        fstpt     (%rsp)
        jmp       ..B1.25
        .align    16,0x90
	.cfi_endproc
	.type	cbrtl,@function
	.size	cbrtl,.-cbrtl
	.data
# -- End  cbrtl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
SH:
	.long	0xf98d728b,0x3ff428a2
	.long	0xa53d6e3d,0x3ff965fe
	.long	0xf8000000,0x3ff428a2
	.long	0xa4000000,0x3ff965fe
	.long	0xae223ddb,0x3e38d728
	.long	0xc82b059a,0x3e33d6e3
	.type	SH,@object
	.size	SH,48
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_P:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	58320
	.word	36408
	.word	14563
	.word	58254
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	50474
	.word	25890
	.word	59872
	.word	64726
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	45217
	.word	37017
	.word	18069
	.word	43151
	.word	49146
	.word	0
	.word	0
	.word	0
	.word	45059
	.word	7616
	.word	35240
	.word	63288
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	54522
	.word	17175
	.word	48475
	.word	49224
	.word	49145
	.word	0
	.word	0
	.word	0
	.word	568
	.word	55426
	.word	44619
	.word	39848
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,112
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
