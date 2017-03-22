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
	.file "cacosh.c"
	.text
..TXTST0:
# -- Begin  cacoshf
	.text
       .align    16,0x90
	.globl cacoshf
cacoshf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cacoshf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_cacoshf.4:
        call      cacosh@PLT
..___tag_value_cacoshf.5:
..B1.10:
        unpcklpd  %xmm1, %xmm0
..B1.2:
        cvtpd2ps  %xmm0, %xmm0
        movsd     %xmm0, 4(%rsp)
        movzwl    6(%rsp), %eax
        testl     $32640, %eax
        jne       ..B1.4
..B1.3:
        testl     $8388607, 4(%rsp)
        jne       ..B1.6
..B1.4:
        movzwl    10(%rsp), %eax
        testl     $32640, %eax
        jne       ..B1.7
..B1.5:
        testl     $8388607, 8(%rsp)
        je        ..B1.7
..B1.6:
        movss     .L_2il0floatpacket.12(%rip), %xmm0
        mulss     %xmm0, %xmm0
        movss     %xmm0, (%rsp)
        movsd     4(%rsp), %xmm0
..B1.7:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	cacoshf,@function
	.size	cacoshf,.-cacoshf
	.data
# -- End  cacoshf
	.text
# -- Begin  cacosh
	.text
       .align    16,0x90
	.globl cacosh
cacosh:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_cacosh.8:
..L9:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        xorb      %r13b, %r13b
        subq      $160, %rsp
	.cfi_def_cfa_offset 176
        movsd     %xmm0, 136(%rsp)
        movsd     %xmm1, 144(%rsp)
..B2.2:
        fnstcw    154(%rsp)
..B2.3:
        movzwl    142(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B2.36
..B2.4:
        testl     %edx, %edx
        jle       ..B2.62
..B2.5:
        movzwl    150(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        jge       ..B2.37
..B2.6:
        testl     %eax, %eax
        jle       ..B2.60
..B2.7:
        movzwl    154(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.11
..B2.8:
        orl       $-64768, %edx
        movw      %dx, 152(%rsp)
..B2.9:
        fldcw     152(%rsp)
..B2.10:
        movb      $1, %r13b
..B2.11:
        fldt      .L_2il0floatpacket.13(%rip)
        lea       BcrossA(%rip), %rax
        movsd     136(%rsp), %xmm0
        fld       %st(0)
        fldt      .L_2il0floatpacket.14(%rip)
        movsd     %xmm0, 128(%rsp)
        fldt      (%rax)
        andps     .L_2il0floatpacket.17(%rip), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        movsd     144(%rsp), %xmm1
        fadd      %st, %st(3)
        movsd     %xmm1, 120(%rsp)
        andps     .L_2il0floatpacket.17(%rip), %xmm1
        movsd     %xmm1, (%rsp)
        fxch      %st(3)
        fstpt     104(%rsp)
        fldt      104(%rsp)
        fxch      %st(3)
        fsubr     %st, %st(4)
        fxch      %st(4)
        fstpt     88(%rsp)
        fldt      88(%rsp)
        fxch      %st(3)
        fmul      %st(0), %st
        fxch      %st(3)
        fmul      %st(0), %st
        fldl      (%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fadd      %st, %st(5)
        fxch      %st(5)
        fsqrt     
        fld       %st(0)
        fxch      %st(3)
        fadd      %st(6), %st
        fsqrt     
        fstpt     72(%rsp)
        fldt      72(%rsp)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fld       %st(5)
        fdiv      %st(2), %st
        fxch      %st(3)
        fcomip    %st(3), %st
        jbe       ..B2.13
..B2.12:
        fstp      %st(0)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fxch      %st(1)
        fstpt     (%rsp)
        fxch      %st(3)
        fstpl     80(%rsp)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        fstpt     48(%rsp)
        call      acosl@PLT
..B2.84:
        fldt      48(%rsp)
        fldt      32(%rsp)
        fldt      16(%rsp)
        fldl      80(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        jmp       ..B2.16
..B2.13:
        fstp      %st(2)
        fldt      .L_2il0floatpacket.13(%rip)
        fcomip    %st(5), %st
        jb        ..B2.15
..B2.14:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.14(%rip)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 208
        fld       %st(2)
        fld       %st(2)
        fadd      %st(6), %st
        fmulp     %st, %st(2)
        fldt      136(%rsp)
        faddp     %st, %st(1)
        fdivr     %st(4), %st
        fldt      104(%rsp)
        fldt      120(%rsp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fsqrt     
        fstpt     (%rsp)
        fxch      %st(3)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpl     96(%rsp)
        fstpt     32(%rsp)
        fstpt     48(%rsp)
        fstpt     64(%rsp)
        call      atan2l@PLT
        jmp       ..B2.86
	.cfi_def_cfa_offset 176
..B2.15:
        fxch      %st(2)
        fstpt     (%rsp)
        fldt      (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 208
        fld       %st(2)
        fadd      %st(5), %st
        fldt      136(%rsp)
        faddp     %st, %st(2)
        fdiv      %st, %st(1)
        fldt      104(%rsp)
        fldt      120(%rsp)
        fsubrp    %st, %st(1)
        fdivrp    %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.14(%rip)
        fmulp     %st, %st(1)
        fsqrt     
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        fxch      %st(2)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpl     96(%rsp)
        fstpt     48(%rsp)
        fstpt     64(%rsp)
        fldt      32(%rsp)
        fstpt     32(%rsp)
        call      atan2l@PLT
..B2.86:
        fldt      64(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        fldl      96(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 176
..B2.16:
        lea       AcrossA(%rip), %rax
        fldt      (%rax)
        fcomip    %st(4), %st
        jb        ..B2.21
..B2.17:
        fldt      104(%rsp)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.13(%rip)
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B2.19
..B2.18:
        fldt      72(%rsp)
        fxch      %st(1)
        fdivr     %st(2), %st
        fldt      88(%rsp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.14(%rip)
        fmulp     %st, %st(1)
        jmp       ..B2.20
..B2.19:
        fldt      88(%rsp)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        fldt      72(%rsp)
        fsubp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.14(%rip)
        fmulp     %st, %st(1)
..B2.20:
        fldt      .L_2il0floatpacket.13(%rip)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      log1pl@PLT
        jmp       ..B2.88
	.cfi_def_cfa_offset 176
..B2.21:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.13(%rip)
        fld       %st(1)
        fmul      %st(2), %st
        addq      $-16, %rsp
	.cfi_def_cfa_offset 192
        fsubp     %st, %st(1)
        fsqrt     
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      logl@PLT
..B2.88:
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
..B2.22:
        lea       ZEROA(%rip), %rax
        movsd     (%rax), %xmm0
        comisd    128(%rsp), %xmm0
        jbe       ..B2.24
..B2.23:
        fldt      .L_2il0floatpacket.15(%rip)
        lea       PIBY2HIA(%rip), %rax
        lea       PIBY2LOA(%rip), %rdx
        fldl      (%rax)
        faddl     (%rdx)
        fmulp     %st, %st(1)
        fsubp     %st, %st(1)
..B2.24:
        comisd    120(%rsp), %xmm0
        jbe       ..B2.26
..B2.25:
        fldt      .L_2il0floatpacket.16(%rip)
        fmulp     %st, %st(1)
..B2.26:
        movb      151(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        fxch      %st(1)
        fstpl     (%rsp)
        movsd     (%rsp), %xmm0
        fstpl     (%rsp)
        movsd     (%rsp), %xmm1
        andps     .L_2il0floatpacket.17(%rip), %xmm1
        andps     .L_2il0floatpacket.17(%rip), %xmm0
        movsd     %xmm0, 16(%rsp)
        movzbl    %al, %ecx
        movzwl    22(%rsp), %esi
        testl     $32752, %esi
        mulsd     (%rdx,%rcx,8), %xmm1
        movsd     %xmm1, 24(%rsp)
        jne       ..B2.29
..B2.27:
        testl     $1048575, 20(%rsp)
        jne       ..B2.32
..B2.28:
        cmpl      $0, 16(%rsp)
        jne       ..B2.32
..B2.29:
        movzwl    30(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.33
..B2.30:
        testl     $1048575, 28(%rsp)
        jne       ..B2.32
..B2.31:
        cmpl      $0, 24(%rsp)
        je        ..B2.33
..B2.32:
        lea       _LDB_MIN_NORMAL(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     48(%rsp)
..B2.33:
        testb     %r13b, %r13b
        je        ..B2.35
..B2.34:
        fldcw     154(%rsp)
..B2.35:
        movsd     16(%rsp), %xmm0
        movsd     24(%rsp), %xmm1
        addq      $160, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 13, -16
..B2.36:
        movzwl    150(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
..B2.37:
        testl     %eax, %eax
        jne       ..B2.47
..B2.38:
        testl     $1048575, 148(%rsp)
        jne       ..B2.47
..B2.39:
        cmpl      $0, 144(%rsp)
        jne       ..B2.47
..B2.40:
        cmpl      $2047, %edx
        jl        ..B2.43
..B2.41:
        testl     $1048575, 140(%rsp)
        jne       ..B2.47
..B2.42:
        cmpl      $0, 136(%rsp)
        jne       ..B2.47
..B2.43:
        cmpl      $1023, %edx
        jl        ..B2.45
..B2.44:
        movb      143(%rsp), %r13b
        andb      $-128, %r13b
        shrb      $7, %r13b
        movsd     136(%rsp), %xmm0
        movzbl    %r13b, %eax
        lea       ones(%rip), %r13
        movsd     (%r13,%rax,8), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm1, (%rsp)
        call      acosh@PLT
..B2.90:
        movsd     %xmm0, 16(%rsp)
        movsd     (%rsp), %xmm0
        call      acos@PLT
..B2.89:
        lea       24(%rsp), %rcx
        jmp       ..B2.46
..B2.45:
        movsd     136(%rsp), %xmm0
        movq      $0, 16(%rsp)
        call      acos@PLT
..B2.91:
        lea       ones(%rip), %r13
        lea       24(%rsp), %rcx
..B2.46:
        movb      151(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        mulsd     (%r13,%rdx,8), %xmm0
        movsd     %xmm0, (%rcx)
        movsd     16(%rsp), %xmm0
        movhpd    24(%rsp), %xmm0
        jmp       ..B2.59
..B2.47:
        testl     %edx, %edx
        jne       ..B2.54
..B2.48:
        testl     $1048575, 140(%rsp)
        jne       ..B2.55
..B2.49:
        cmpl      $0, 136(%rsp)
        jne       ..B2.55
..B2.50:
        cmpl      $2047, %eax
        jl        ..B2.53
..B2.51:
        testl     $1048575, 148(%rsp)
        jne       ..B2.55
..B2.52:
        cmpl      $0, 144(%rsp)
        jne       ..B2.55
..B2.53:
        movb      151(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movsd     144(%rsp), %xmm1
        movsd     %xmm1, (%rsp)
        movzbl    %al, %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        mulsd     %xmm1, %xmm0
        call      asinh@PLT
..B2.93:
        movsd     %xmm0, 16(%rsp)
        movsd     136(%rsp), %xmm1
        movsd     (%rsp), %xmm0
        call      atan2@PLT
        jmp       ..B2.94
..B2.54:
        cmpl      $2047, %edx
        je        ..B2.72
..B2.55:
        cmpl      $2047, %eax
        je        ..B2.68
..B2.96:
        cmpl      $2047, %edx
..B2.56:
        je        ..B2.65
..B2.57:
        movsd     144(%rsp), %xmm0
        movaps    %xmm0, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, 16(%rsp)
        movsd     136(%rsp), %xmm1
..B2.58:
        call      atan2@PLT
..B2.94:
        movsd     %xmm0, 24(%rsp)
        movsd     16(%rsp), %xmm0
        movhpd    24(%rsp), %xmm0
..B2.59:
        movaps    %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm1
        addq      $160, %rsp
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 13, -16
..B2.60:
        testl     $1048575, 148(%rsp)
        jne       ..B2.7
..B2.61:
        cmpl      $0, 144(%rsp)
        jne       ..B2.7
        jmp       ..B2.37
..B2.62:
        testl     $1048575, 140(%rsp)
        jne       ..B2.5
..B2.63:
        cmpl      $0, 136(%rsp)
        jne       ..B2.5
        jmp       ..B2.36
..B2.65:
        testl     $1048575, 140(%rsp)
        jne       ..B2.57
..B2.66:
        cmpl      $0, 136(%rsp)
        jne       ..B2.57
..B2.67:
        movsd     136(%rsp), %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movsd     144(%rsp), %xmm0
        jmp       ..B2.58
..B2.68:
        testl     $1048575, 148(%rsp)
        jne       ..B2.70
..B2.69:
        cmpl      $0, 144(%rsp)
        je        ..B2.95
..B2.70:
        cmpl      $2047, %edx
        jge       ..B2.56
..B2.71:
        movsd     144(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.59
..B2.72:
        testl     $1048575, 140(%rsp)
        jne       ..B2.74
..B2.73:
        cmpl      $0, 136(%rsp)
        je        ..B2.75
..B2.74:
        cmpl      $2047, %eax
        je        ..B2.79
..B2.75:
        testl     $1048575, 140(%rsp)
        jne       ..B2.77
..B2.76:
        cmpl      $0, 136(%rsp)
        je        ..B2.55
..B2.77:
        cmpl      $2047, %eax
        jge       ..B2.55
..B2.78:
        movsd     136(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.59
..B2.79:
        testl     $1048575, 148(%rsp)
        jne       ..B2.81
..B2.80:
        cmpl      $0, 144(%rsp)
        je        ..B2.75
..B2.81:
        movsd     144(%rsp), %xmm1
        movsd     136(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        unpcklpd  %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.59
..B2.95:
        cmpl      $2047, %edx
        je        ..B2.65
        jmp       ..B2.57
        .align    16,0x90
	.cfi_endproc
	.type	cacosh,@function
	.size	cacosh,.-cacosh
	.data
# -- End  cacosh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,16
	.align 16
.L_2il0floatpacket.14:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,16
	.align 16
.L_2il0floatpacket.15:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.16:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 16
.L_2il0floatpacket.17:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,16
	.align 16
BcrossA:
	.long	3621516424
	.long	2756080513
	.long	16382
	.long	0
	.type	BcrossA,@object
	.size	BcrossA,16
	.align 16
AcrossA:
	.long	0
	.long	3221225472
	.long	16383
	.long	0
	.type	AcrossA,@object
	.size	AcrossA,16
	.align 16
ZEROA:
	.long	0
	.long	0
	.type	ZEROA,@object
	.size	ZEROA,8
	.space 8, 0x00 	# pad
	.align 16
PIBY2HIA:
	.long	1413754136
	.long	1073291771
	.type	PIBY2HIA,@object
	.size	PIBY2HIA,8
	.space 8, 0x00 	# pad
	.align 16
PIBY2LOA:
	.long	856972295
	.long	1016178214
	.type	PIBY2LOA,@object
	.size	PIBY2LOA,8
	.space 8, 0x00 	# pad
	.align 16
_LDB_MIN_NORMAL:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.type	_LDB_MIN_NORMAL,@object
	.size	_LDB_MIN_NORMAL,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
.L_2il0floatpacket.12:
	.long	0x00800000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
