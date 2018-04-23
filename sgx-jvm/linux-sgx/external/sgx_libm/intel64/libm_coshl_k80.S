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
	.file "libm_coshl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_coshl_k80
	.text
       .align    16,0x90
	.globl __libm_coshl_k80
__libm_coshl_k80:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_coshl_k80.1:
..L2:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16405, %eax
        jge       ..B1.20
..B1.2:
        cmpl      $16382, %eax
        jge       ..B1.13
..B1.3:
        cmpl      $16378, %eax
        jge       ..B1.11
..B1.4:
        fldt      .L_2il0floatpacket.1(%rip)
        cmpl      $16372, %eax
        jge       ..B1.10
..B1.5:
        cmpl      $16365, %eax
        jge       ..B1.9
..B1.6:
        fstpt     (%rdi)
        cmpl      $16308, %eax
        jge       ..B1.8
..B1.7:
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     16(%rdi)
        fldt      16(%rdi)
        ret       
..B1.8:
        fldt      8(%rsp)
        lea       _Q3(%rip), %rax
        fldt      (%rax)
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.9:
        fldt      8(%rsp)
        lea       16+_Q2(%rip), %rax
        lea       _Q2(%rip), %rdx
        fmul      %st(0), %st
        fxch      %st(1)
        fstpt     (%rdi)
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.10:
        fldt      8(%rsp)
        lea       48+_Q1(%rip), %rax
        lea       32+_Q1(%rip), %rcx
        lea       16+_Q1(%rip), %rdx
        lea       _Q1(%rip), %rsi
        fmul      %st(0), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     (%rdi)
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        fmulp     %st, %st(3)
        fldt      (%rsi)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.11:
        fldt      8(%rsp)
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       96+_Q(%rip), %rdx
        fmul      %st(3), %st
        lea       64+_Q(%rip), %rcx
        fld       %st(0)
        lea       112+_Q(%rip), %r8
        lea       32+_Q(%rip), %rsi
        lea       80+_Q(%rip), %r9
        lea       48+_Q(%rip), %r10
        lea       16+_Q(%rip), %r11
        fmul      %st(1), %st
        fldl      (%rax)
        lea       144+_Q(%rip), %rax
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fsub      %st(4), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(6)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmul      %st(0), %st
        fldt      (%rdx)
        lea       128+_Q(%rip), %rdx
        fmul      %st(3), %st
        fldt      (%rcx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r8)
        fmul      %st(4), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fld       %st(5)
        fldt      (%r11)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      (%rax)
        fld       %st(0)
        fmul      %st(7), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fldt      (%rdx)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(2)
        fmulp     %st, %st(4)
        fld       %st(1)
        fadd      %st(4), %st
        fadd      %st(3), %st
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.1(%rip)
        faddp     %st, %st(1)
        fstpt     (%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
..B1.12:
        ret       
..B1.13:
        fldt      8(%rsp)
        lea       _KLN2(%rip), %rsi
        movb      17(%rsp), %cl
        lea       _TWO_63H(%rip), %rax
        andb      $-128, %cl
        lea       _LN2K(%rip), %r8
        shrb      $7, %cl
        lea       16+_LN2K(%rip), %r9
        fldt      (%rsi)
        fldl      (%rax)
        fldt      (%r8)
        fldt      (%r9)
        movzbl    %cl, %edx
        lea       ones(%rip), %rcx
        lea       _TWO_32P(%rip), %r10
        lea       _TWO_32(%rip), %r11
        lea       64+_P(%rip), %rsi
        lea       32+_P(%rip), %r8
        lea       _P(%rip), %r9
        fxch      %st(4)
        fmull     (%rcx,%rdx,8)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     -24(%rsp)
        fxch      %st(1)
        fstpt     8(%rsp)
        fldt      8(%rsp)
        fldt      -24(%rsp)
        movl      -24(%rsp), %edx
        fsubp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        movsbq    %dl, %rax
        fsubrp    %st, %st(1)
        fld       %st(0)
        subl      %eax, %edx
        fxch      %st(3)
        fmulp     %st, %st(2)
        sarl      $8, %edx
        fxch      %st(1)
        fsubr     %st, %st(2)
        fldl      (%r10)
        lea       48+_P(%rip), %r10
        fmul      %st(3), %st
        fldl      (%r11)
        lea       16+_P(%rip), %r11
        fmulp     %st, %st(4)
        fsubp     %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fsubp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fldt      (%rsi)
        fmul      %st(1), %st
        fldt      (%r8)
        movzwl    16(%rsp), %esi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        andl      $32767, %esi
        fldt      (%r9)
        cmpl      $16387, %esi
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        jg        ..B1.19
..B1.14:
        jne       ..B1.18
..B1.15:
        movl      12(%rsp), %esi
        cmpl      $-782985146, %esi
        ja        ..B1.19
..B1.16:
        jne       ..B1.18
..B1.17:
        cmpl      $1501741449, 8(%rsp)
        ja        ..B1.19
..B1.18:
        fldt      .L_2il0floatpacket.1(%rip)
        movl      %edx, %r8d
        negl      %r8d
        lea       -1(%rdx), %esi
        addl      $16383, %r8d
        shlq      $4, %rax
        subl      %edx, %r8d
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        andl      $32767, %r8d
        fstpt     -40(%rsp)
        fld       %st(1)
        movzwl    8+.L_2il0floatpacket.1(%rip), %r9d
        fadd      %st(1), %st
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(2)
        andl      $-32768, %r9d
        fldl      2056(%rdx,%rax)
        orl       %r8d, %r9d
        fldl      2048(%rdx,%rax)
        negq      %rax
        fstpt     -72(%rsp)
        fldt      -72(%rsp)
        movw      %r9w, -32(%rsp)
        fadd      %st(1), %st
        fmulp     %st, %st(3)
        fld       %st(4)
        fmul      %st(1), %st
        movl      %esi, -8(%rsp)
        faddp     %st, %st(3)
        fldl      2056(%rdx,%rax)
        fmul      %st, %st(2)
        fldl      2048(%rdx,%rax)
        fld       %st(0)
        movb      17(%rsp), %al
        fadd      %st(2), %st
        fmulp     %st, %st(6)
        andb      $-128, %al
        shrb      $7, %al
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fxch      %st(4)
        fstpt     -56(%rsp)
        fld       %st(4)
        fxch      %st(2)
        fmul      %st, %st(5)
        fldt      -72(%rsp)
        fmul      %st, %st(3)
        fld       %st(0)
        movzbl    %al, %r10d
        fadd      %st(4), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(2)
        fsub      %st(5), %st
        fsubr     %st, %st(3)
        fxch      %st(5)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(0)
        fldt      -56(%rsp)
        faddp     %st, %st(4)
        fldt      -40(%rsp)
        fmul      %st, %st(5)
        fxch      %st(1)
        fadd      %st(5), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      (%rcx,%r10,8)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%rdi)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fildl     -8(%rsp)
        ret       
..B1.19:
        shlq      $4, %rax
        decl      %edx
        movl      %edx, -8(%rsp)
        faddp     %st, %st(1)
        fildl     -8(%rsp)
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        fldl      2056(%rdx,%rax)
        fldl      2048(%rdx,%rax)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        movb      17(%rsp), %al
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(0)
        andb      $-128, %al
        shrb      $7, %al
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        movzbl    %al, %esi
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      (%rcx,%rsi,8)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     (%rdi)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        ret       
..B1.20:
        cmpl      $32767, %eax
        je        ..B1.23
..B1.21:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       _infs(%rip), %rax
        fstpt     16(%rdi)
        fldt      (%rax)
        fstpt     (%rdi)
        fldt      (%rdi)
..B1.22:
        ret       
..B1.23:
        fldt      8(%rsp)
        fmul      %st(0), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     16(%rdi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_coshl_k80,@function
	.size	__libm_coshl_k80,.-__libm_coshl_k80
	.data
# -- End  __libm_coshl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
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
_TWO_32P:
	.long	1048576
	.long	1106247680
	.type	_TWO_32P,@object
	.size	_TWO_32P,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.align 2
_Q3:
	.word	21845
	.word	213
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	62430
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	22894
	.word	21845
	.word	43733
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	65531
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	0
	.word	0
	.word	7054
	.word	43693
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	25016
	.word	58228
	.word	24755
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	57397
	.word	35800
	.word	53031
	.word	53262
	.word	16367
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	18360
	.word	43637
	.word	28980
	.word	40373
	.word	49076
	.word	0
	.word	0
	.word	0
	.word	16302
	.word	43691
	.word	43690
	.word	43690
	.word	16362
	.word	0
	.word	0
	.word	0
	.word	46125
	.word	2912
	.word	24758
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.word	29965
	.word	3335
	.word	208
	.word	53261
	.word	16367
	.word	0
	.word	0
	.word	0
	.word	54950
	.word	46751
	.word	32187
	.word	37874
	.word	16361
	.word	0
	.word	0
	.word	0
	.word	30733
	.word	25775
	.word	51110
	.word	36726
	.word	16354
	.word	0
	.word	0
	.word	0
	.word	49850
	.word	450
	.word	9929
	.word	51659
	.word	16346
	.word	0
	.word	0
	.word	0
	.word	2776
	.word	45191
	.word	37201
	.word	55334
	.word	16338
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,160
	.align 2
_KLN2:
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	16391
	.word	0
	.word	0
	.word	0
	.type	_KLN2,@object
	.size	_KLN2,16
	.align 2
_LN2K:
	.word	0
	.word	0
	.word	6134
	.word	45426
	.word	16374
	.word	0
	.word	0
	.word	0
	.word	55756
	.word	58609
	.word	48341
	.word	59623
	.word	16343
	.word	0
	.word	0
	.word	0
	.type	_LN2K,@object
	.size	_LN2K,32
	.align 2
_P:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.word	0
	.word	0
	.type	_P,@object
	.size	_P,80
	.align 2
_infs:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.type	_infs,@object
	.size	_infs,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
