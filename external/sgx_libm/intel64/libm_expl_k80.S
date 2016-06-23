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
	.file "libm_expl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_expl_k80
	.text
       .align    16,0x90
	.globl __libm_expl_k80
__libm_expl_k80:
# parameter 1: %rdi
# parameter 2: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___libm_expl_k80.1:
..L2:

        fldt      (%rsi)
        movb      9(%rsi), %dl
        movzwl    8(%rsi), %eax
        andb      $-128, %dl
        andl      $32767, %eax
        shrb      $7, %dl
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      16(%rsi)
        cmpl      $32767, %eax
        je        ..B1.11
..B1.2:
        cmpl      $16358, %eax
        jge       ..B1.7
..B1.3:
        cmpl      $16183, %eax
        jge       ..B1.5
..B1.4:
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0(%rip)
        fstpt     (%rdi)
        fldt      .L_2il0floatpacket.1(%rip)
        fstpt     16(%rdi)
        fldt      16(%rdi)
        ret       
..B1.5:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       16+_P1(%rip), %rax
        lea       _P1(%rip), %rdx
        fldt      (%rax)
        fmul      %st(3), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmul      %st(3), %st
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.0(%rip)
        fadd      %st(3), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.1(%rip)
..B1.6:
        ret       
..B1.7:
        cmpl      $16405, %eax
        jge       ..B1.9
..B1.8:
        fldt      -24(%rsp)
        lea       _KLN2(%rip), %rdx
        lea       _TWO_63H(%rip), %rcx
        lea       _LN2K(%rip), %r8
        lea       16+_LN2K(%rip), %r9
        lea       64+_P(%rip), %r10
        lea       32+_P(%rip), %r11
        fldt      (%rdx)
        lea       _P(%rip), %rdx
        fmulp     %st, %st(3)
        fldt      (%rcx)
        lea       48+_P(%rip), %rcx
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     -56(%rsp)
        fldt      -56(%rsp)
        movl      -56(%rsp), %esi
        fsubp     %st, %st(3)
        fldt      (%r8)
        fmul      %st(3), %st
        fstpt     -56(%rsp)
        fxch      %st(2)
        fchs      
        fldt      -56(%rsp)
        movsbq    %sil, %rax
        fsubrp    %st, %st(3)
        fldt      (%r9)
        subl      %eax, %esi
        fmulp     %st, %st(1)
        fstpt     -56(%rsp)
        fldt      -56(%rsp)
        sarl      $8, %esi
        fadd      %st(2), %st
        fld       %st(0)
        movl      %esi, -40(%rsp)
        fsub      %st(3), %st
        fld       %st(1)
        lea       16+_P(%rip), %rsi
        shlq      $4, %rax
        fsub      %st(1), %st
        movq      __libm_expl_table_256@GOTPCREL(%rip), %r8
        fsubrp    %st, %st(4)
        fildl     -40(%rsp)
        fldt      -56(%rsp)
        fsubp     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(3)
        fsub      %st(2), %st
        fchs      
        fld       %st(0)
        fsubr     %st(3), %st
        fsubrp    %st, %st(5)
        fsubrp    %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      (%r10)
        fmul      %st(2), %st
        fldt      (%r11)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rdx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      (%rcx)
        fmul      %st(3), %st
        fldt      (%rsi)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      2056(%r8,%rax)
        fldl      2048(%r8,%rax)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rdi)
        ret       
..B1.9:
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.1(%rip)
        lea       _inf_zeros(%rip), %rax
        movzbl    %dl, %edx
        fstpt     16(%rdi)
        shlq      $4, %rdx
        fldt      (%rax,%rdx)
        fstpt     (%rdi)
        fldt      (%rdi)
..B1.10:
        ret       
..B1.11:
        fstp      %st(0)
        cmpl      $-2147483648, -20(%rsp)
        jne       ..B1.15
..B1.12:
        cmpl      $0, -24(%rsp)
        jne       ..B1.15
..B1.13:
        fstp      %st(0)
        movzbl    %dl, %edx
        lea       _inf_zeros(%rip), %rax
        shlq      $4, %rdx
        fldt      (%rax,%rdx)
..B1.15:
        fldt      .L_2il0floatpacket.1(%rip)
        fstpt     16(%rdi)
        fstpt     (%rdi)
        fldt      (%rdi)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_expl_k80,@function
	.size	__libm_expl_k80,.-__libm_expl_k80
	.data
# -- End  __libm_expl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 2
_P1:
	.word	43691
	.word	10
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	30584
	.word	43703
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.type	_P1,@object
	.size	_P1,32
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
_TWO_63H:
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.type	_TWO_63H,@object
	.size	_TWO_63H,16
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
_inf_zeros:
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
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
