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
	.file "libm_sinhl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_sinhl_k80
	.text
       .align    16,0x90
	.globl __libm_sinhl_k80
__libm_sinhl_k80:
# parameter 1: 8 + %rsp
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_sinhl_k80.1:
..L2:

        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $16405, %eax
        jge       ..B1.20
..B1.2:
        cmpl      $16382, %eax
        jge       ..B1.13
..B1.3:
        fldt      8(%rsp)
        cmpl      $16378, %eax
        jge       ..B1.11
..B1.4:
        cmpl      $16373, %eax
        jge       ..B1.10
..B1.5:
        cmpl      $16366, %eax
        jge       ..B1.9
..B1.6:
        fstpt     (%rdi)
        fldt      (%rdi)
        cmpl      $16308, %eax
        jge       ..B1.8
..B1.7:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     16(%rdi)
        fldt      16(%rdi)
        ret       
..B1.8:
        lea       _Q3(%rip), %rax
        fldt      (%rax)
        fmul      %st(1), %st
        fmul      %st(1), %st
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.9:
        fld       %st(0)
        lea       16+_Q2(%rip), %rax
        lea       _Q2(%rip), %rdx
        fmul      %st(1), %st
        fxch      %st(1)
        fstpt     (%rdi)
        fldt      (%rdi)
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.10:
        fld       %st(0)
        lea       48+_Q1(%rip), %rax
        lea       32+_Q1(%rip), %rcx
        lea       16+_Q1(%rip), %rdx
        lea       _Q1(%rip), %rsi
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fstpt     (%rdi)
        fldt      (%rdi)
        fldt      (%rax)
        fmul      %st(3), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%rcx)
        fmulp     %st, %st(4)
        fldt      (%rsi)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.11:
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fmul      %st(1), %st
        lea       112+_Q(%rip), %rdx
        fld       %st(0)
        lea       80+_Q(%rip), %rcx
        lea       48+_Q(%rip), %rsi
        lea       96+_Q(%rip), %r9
        lea       16+_Q(%rip), %r8
        lea       64+_Q(%rip), %r10
        lea       32+_Q(%rip), %r11
        fmul      %st(1), %st
        fld       %st(2)
        fxch      %st(3)
        fstpt     -88(%rsp)
        fldt      -88(%rsp)
        fldl      (%rax)
        lea       _Q(%rip), %rax
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fld       %st(1)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(4)
        fstpt     -72(%rsp)
        fldt      -72(%rsp)
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
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fldt      (%r9)
        fmul      %st(4), %st
        fldt      (%r10)
        faddp     %st, %st(1)
        fmul      %st(4), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fldt      (%rax)
        faddp     %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(0)
        fldt      (%rdx)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      -88(%rsp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      -72(%rsp)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
..B1.12:
        ret       
..B1.13:
        fldt      8(%rsp)
        lea       ones(%rip), %rax
        movb      17(%rsp), %dl
        lea       _KLN2(%rip), %r8
        andb      $-128, %dl
        lea       _TWO_63H(%rip), %rsi
        shrb      $7, %dl
        lea       _LN2K(%rip), %r9
        fldt      (%r8)
        fldl      (%rsi)
        fldt      (%r9)
        movzbl    %dl, %ecx
        lea       16+_LN2K(%rip), %r10
        lea       _TWO_32P(%rip), %r11
        lea       64+_P(%rip), %rsi
        lea       32+_P(%rip), %r8
        lea       _P(%rip), %r9
        fldl      (%rax,%rcx,8)
        lea       _TWO_32(%rip), %rcx
        fmul      %st, %st(4)
        fxch      %st(3)
        fmul      %st(4), %st
        fadd      %st(2), %st
        fstpt     -24(%rsp)
        fxch      %st(3)
        fstpt     8(%rsp)
        fldt      8(%rsp)
        fldt      -24(%rsp)
        movl      -24(%rsp), %edx
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(3)
        movsbq    %dl, %rax
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(0)
        subl      %eax, %edx
        fldt      (%r10)
        lea       48+_P(%rip), %r10
        fmulp     %st, %st(4)
        sarl      $8, %edx
        fsub      %st(3), %st
        fldl      (%r11)
        lea       16+_P(%rip), %r11
        fmul      %st(1), %st
        fldl      (%rcx)
        fmulp     %st, %st(2)
        movzwl    16(%rsp), %ecx
        fsubp     %st, %st(1)
        fld       %st(0)
        andl      $32767, %ecx
        cmpl      $16387, %ecx
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      (%rsi)
        fmul      %st(1), %st
        fldt      (%r8)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r9)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        jg        ..B1.19
..B1.14:
        jne       ..B1.18
..B1.15:
        movl      12(%rsp), %ecx
        cmpl      $-782985146, %ecx
        ja        ..B1.19
..B1.16:
        jne       ..B1.18
..B1.17:
        cmpl      $1501741449, 8(%rsp)
        ja        ..B1.19
..B1.18:
        fldt      .L_2il0floatpacket.1(%rip)
        movl      %edx, %esi
        negl      %esi
        lea       -1(%rdx), %ecx
        addl      $16383, %esi
        shlq      $4, %rax
        subl      %edx, %esi
        movq      __libm_expl_table_256@GOTPCREL(%rip), %rdx
        andl      $32767, %esi
        fstpt     -40(%rsp)
        fld       %st(0)
        movzwl    8+.L_2il0floatpacket.1(%rip), %r8d
        fadd      %st(2), %st
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fxch      %st(2)
        fstpt     -88(%rsp)
        fld       %st(2)
        fldl      2056(%rdx,%rax)
        andl      $-32768, %r8d
        fldl      2048(%rdx,%rax)
        negq      %rax
        fstpt     -72(%rsp)
        fldt      -72(%rsp)
        orl       %esi, %r8d
        movw      %r8w, -32(%rsp)
        fadd      %st(1), %st
        fmulp     %st, %st(3)
        fld       %st(4)
        fmul      %st(1), %st
        movl      %ecx, -8(%rsp)
        faddp     %st, %st(3)
        fldl      2056(%rdx,%rax)
        fldl      2048(%rdx,%rax)
        fld       %st(0)
        fadd      %st(2), %st
        fmulp     %st, %st(6)
        fld       %st(6)
        fmul      %st(2), %st
        fsubrp    %st, %st(6)
        fxch      %st(5)
        fstpt     -56(%rsp)
        fxch      %st(4)
        fmul      %st, %st(5)
        fldt      -72(%rsp)
        fmul      %st, %st(3)
        fld       %st(0)
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
        fsub      %st(5), %st
        fsubr     %st, %st(2)
        fxch      %st(5)
        fsubrp    %st, %st(2)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fldt      -88(%rsp)
        fmul      %st, %st(2)
        fxch      %st(2)
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
        fadd      %st(6), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(5)
        fld       %st(0)
        fadd      %st(6), %st
        fsubr     %st, %st(1)
        fxch      %st(6)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     (%rdi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rdi)
        ret       
..B1.20:
        movb      17(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        cmpl      $32767, %eax
        je        ..B1.23
..B1.21:
        fldt      .L_2il0floatpacket.0(%rip)
        movzbl    %dl, %eax
        lea       _infs(%rip), %rdx
        shlq      $4, %rax
        fstpt     16(%rdi)
        fldt      (%rdx,%rax)
        fstpt     (%rdi)
        fldt      (%rdx)
..B1.22:
        ret       
..B1.23:
        fldt      8(%rsp)
        fstpt     (%rdi)
        fldt      (%rdi)
        fmul      %st(0), %st
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     16(%rdi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sinhl_k80,@function
	.size	__libm_sinhl_k80,.-__libm_sinhl_k80
	.data
# -- End  __libm_sinhl_k80
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
	.word	44373
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q3,@object
	.size	_Q3,16
	.align 2
_Q2:
	.word	29491
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	1838
	.word	3745
	.word	35050
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.type	_Q2,@object
	.size	_Q2,32
	.align 2
_Q1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	56472
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	6470
	.word	8609
	.word	207
	.word	53261
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	51515
	.word	9159
	.word	10794
	.word	47344
	.word	16364
	.word	0
	.word	0
	.word	0
	.type	_Q1,@object
	.size	_Q1,64
	.align 2
_Q:
	.word	43620
	.word	43690
	.word	43690
	.word	43690
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	34953
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	0
	.word	0
	.word	52382
	.word	3328
	.word	208
	.word	53261
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	44665
	.word	46658
	.word	7466
	.word	47343
	.word	16364
	.word	0
	.word	0
	.word	0
	.word	15678
	.word	35942
	.word	11071
	.word	55090
	.word	16357
	.word	0
	.word	0
	.word	0
	.word	10412
	.word	23121
	.word	12507
	.word	45202
	.word	16350
	.word	0
	.word	0
	.word	0
	.word	37213
	.word	41879
	.word	65084
	.word	55102
	.word	16342
	.word	0
	.word	0
	.word	0
	.word	1721
	.word	42148
	.word	19309
	.word	52099
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_Q,@object
	.size	_Q,144
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
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	65535
	.word	0
	.word	0
	.word	0
	.type	_infs,@object
	.size	_infs,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
