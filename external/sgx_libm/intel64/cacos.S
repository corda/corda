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
	.file "cacos.c"
	.text
..TXTST0:
# -- Begin  cacosf
	.text
       .align    16,0x90
	.globl cacosf
cacosf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_cacosf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_cacosf.4:
        call      cacos@PLT
..___tag_value_cacosf.5:
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
	.type	cacosf,@function
	.size	cacosf,.-cacosf
	.data
# -- End  cacosf
	.text
# -- Begin  cacos
	.text
       .align    16,0x90
	.globl cacos
cacos:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_cacos.8:
..L9:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        xorb      %r12b, %r12b
        subq      $152, %rsp
	.cfi_def_cfa_offset 176
        movsd     %xmm0, 32(%rsp)
        movsd     %xmm1, 40(%rsp)
..B2.2:
        fnstcw    138(%rsp)
..B2.3:
        movzwl    38(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B2.36
..B2.4:
        testl     %edx, %edx
        jle       ..B2.57
..B2.5:
        movzwl    46(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        jge       ..B2.36
..B2.6:
        testl     %eax, %eax
        jle       ..B2.55
..B2.7:
        movzwl    138(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.11
..B2.8:
        orl       $-64768, %edx
        movw      %dx, 136(%rsp)
..B2.9:
        fldcw     136(%rsp)
..B2.10:
        movb      $1, %r12b
..B2.11:
        fldt      .L_2il0floatpacket.14(%rip)
        lea       BcrossA(%rip), %rax
        movsd     32(%rsp), %xmm0
        fld       %st(0)
        fldt      .L_2il0floatpacket.15(%rip)
        movsd     %xmm0, 120(%rsp)
        fldt      (%rax)
        andps     .L_2il0floatpacket.18(%rip), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        movsd     40(%rsp), %xmm1
        fadd      %st, %st(3)
        movsd     %xmm1, 128(%rsp)
        andps     .L_2il0floatpacket.18(%rip), %xmm1
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
..B2.78:
        fldt      48(%rsp)
        fldt      32(%rsp)
        fldt      16(%rsp)
        fldl      80(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
        jmp       ..B2.16
..B2.13:
        fstp      %st(2)
        fldt      .L_2il0floatpacket.14(%rip)
        fcomip    %st(5), %st
        jb        ..B2.15
..B2.14:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.15(%rip)
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
        jmp       ..B2.80
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
        fldt      .L_2il0floatpacket.15(%rip)
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
..B2.80:
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
        fldt      .L_2il0floatpacket.14(%rip)
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
        fldt      .L_2il0floatpacket.15(%rip)
        fmulp     %st, %st(1)
        jmp       ..B2.20
..B2.19:
        fldt      88(%rsp)
        fxch      %st(1)
        fdivrp    %st, %st(2)
        fldt      72(%rsp)
        fsubp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.15(%rip)
        fmulp     %st, %st(1)
..B2.20:
        fldt      .L_2il0floatpacket.14(%rip)
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
        jmp       ..B2.82
	.cfi_def_cfa_offset 176
..B2.21:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.14(%rip)
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
..B2.82:
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 176
..B2.22:
        lea       ZEROA(%rip), %rax
        movsd     (%rax), %xmm1
        comisd    120(%rsp), %xmm1
        jbe       ..B2.24
..B2.23:
        fldt      .L_2il0floatpacket.16(%rip)
        lea       PIBY2HIA(%rip), %rax
        lea       PIBY2LOA(%rip), %rdx
        fldl      (%rax)
        faddl     (%rdx)
        fmulp     %st, %st(1)
        fsubp     %st, %st(1)
..B2.24:
        movsd     128(%rsp), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B2.26
..B2.25:
        fldt      .L_2il0floatpacket.17(%rip)
        fmulp     %st, %st(2)
..B2.26:
        fstpl     16(%rsp)
        movzwl    22(%rsp), %eax
        testl     $32752, %eax
        fstpl     24(%rsp)
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
        testb     %r12b, %r12b
        je        ..B2.35
..B2.34:
        fldcw     138(%rsp)
..B2.35:
        movsd     16(%rsp), %xmm0
        movsd     24(%rsp), %xmm1
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B2.36:
        testl     %edx, %edx
        jne       ..B2.40
..B2.37:
        testl     $1048575, 36(%rsp)
        jne       ..B2.40
..B2.38:
        cmpl      $0, 32(%rsp)
        jne       ..B2.40
..B2.39:
        movsd     40(%rsp), %xmm0
        movq      $0x3ff921fb54442d18, %rax
        movq      %rax, 16(%rsp)
        call      asinh@PLT
..B2.83:
        xorps     .L_2il0floatpacket.19(%rip), %xmm0
        movsd     %xmm0, 24(%rsp)
        movsd     16(%rsp), %xmm0
        movhpd    24(%rsp), %xmm0
        jmp       ..B2.54
..B2.40:
        movzwl    46(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        je        ..B2.72
..B2.89:
        cmpl      $2047, %edx
..B2.41:
        je        ..B2.62
..B2.42:
        cmpl      $2047, %eax
        je        ..B2.59
..B2.43:
        testl     %eax, %eax
        jne       ..B2.50
..B2.44:
        testl     $1048575, 44(%rsp)
        jne       ..B2.50
..B2.45:
        cmpl      $0, 40(%rsp)
        jne       ..B2.50
..B2.46:
        movsd     32(%rsp), %xmm0
        cmpl      $1023, %edx
        jl        ..B2.48
..B2.47:
        movb      39(%rsp), %r12b
        andb      $-128, %r12b
        shrb      $7, %r12b
        movzbl    %r12b, %eax
        lea       ones(%rip), %r12
        movsd     (%r12,%rax,8), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm1, (%rsp)
        call      acosh@PLT
..B2.85:
        movsd     %xmm0, 8(%rsp)
        movsd     (%rsp), %xmm0
        call      acos@PLT
..B2.84:
        movsd     %xmm0, 16(%rsp)
        jmp       ..B2.49
..B2.48:
        pxor      %xmm1, %xmm1
        movsd     %xmm1, 8(%rsp)
        call      acos@PLT
..B2.86:
        movsd     %xmm0, 16(%rsp)
        lea       ones(%rip), %r12
..B2.49:
        movb      47(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movsd     8(%rsp), %xmm0
        xorb      $1, %al
        movzbl    %al, %edx
        mulsd     (%r12,%rdx,8), %xmm0
        movsd     %xmm0, 24(%rsp)
        movsd     16(%rsp), %xmm0
        movhpd    24(%rsp), %xmm0
        jmp       ..B2.54
..B2.50:
        movsd     40(%rsp), %xmm0
        cmpl      $2047, %eax
        movsd     32(%rsp), %xmm1
        je        ..B2.52
..B2.51:
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        jmp       ..B2.53
..B2.52:
        movaps    %xmm0, %xmm2
        mulsd     %xmm0, %xmm2
..B2.53:
        movb      47(%rsp), %r13b
        andb      $-128, %r13b
        shrb      $7, %r13b
        movb      %r13b, %r12b
        xorb      $1, %r12b
        movzbl    %r12b, %eax
        lea       ones(%rip), %r12
        mulsd     (%r12,%rax,8), %xmm2
        movsd     %xmm2, 24(%rsp)
        call      atan2@PLT
..B2.87:
        movzbl    %r13b, %eax
        mulsd     (%r12,%rax,8), %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    24(%rsp), %xmm0
..B2.54:
        movaps    %xmm0, %xmm1
        unpckhpd  %xmm0, %xmm1
        addq      $152, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 176
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B2.55:
        testl     $1048575, 44(%rsp)
        jne       ..B2.7
..B2.56:
        cmpl      $0, 40(%rsp)
        jne       ..B2.7
        jmp       ..B2.36
..B2.57:
        testl     $1048575, 36(%rsp)
        jne       ..B2.5
..B2.58:
        cmpl      $0, 32(%rsp)
        jne       ..B2.5
        jmp       ..B2.36
..B2.59:
        testl     $1048575, 44(%rsp)
        jne       ..B2.61
..B2.60:
        cmpl      $0, 40(%rsp)
        je        ..B2.50
..B2.61:
        movsd     32(%rsp), %xmm1
        movsd     40(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        unpcklpd  %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.54
..B2.62:
        testl     $1048575, 36(%rsp)
        jne       ..B2.64
..B2.63:
        cmpl      $0, 32(%rsp)
        je        ..B2.66
..B2.64:
        cmpl      $2047, %eax
        jge       ..B2.66
..B2.65:
        movsd     32(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.54
..B2.66:
        testl     $1048575, 36(%rsp)
        jne       ..B2.68
..B2.67:
        cmpl      $0, 32(%rsp)
        je        ..B2.42
..B2.68:
        cmpl      $2047, %eax
        jne       ..B2.43
..B2.69:
        testl     $1048575, 44(%rsp)
        jne       ..B2.71
..B2.70:
        cmpl      $0, 40(%rsp)
        je        ..B2.59
..B2.71:
        movsd     32(%rsp), %xmm0
        movsd     40(%rsp), %xmm1
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        unpcklpd  %xmm1, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.54
..B2.72:
        testl     $1048575, 44(%rsp)
        jne       ..B2.74
..B2.73:
        cmpl      $0, 40(%rsp)
        je        ..B2.88
..B2.74:
        cmpl      $2047, %edx
        jge       ..B2.41
..B2.75:
        movsd     40(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        unpcklpd  %xmm0, %xmm0
        movsd     %xmm0, 16(%rsp)
        movhpd    %xmm0, 24(%rsp)
        jmp       ..B2.54
..B2.88:
        cmpl      $2047, %edx
        je        ..B2.62
        jmp       ..B2.42
        .align    16,0x90
	.cfi_endproc
	.type	cacos,@function
	.size	cacos,.-cacos
	.data
# -- End  cacos
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.14:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,16
	.align 16
.L_2il0floatpacket.15:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.16:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 16
.L_2il0floatpacket.17:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,16
	.align 16
.L_2il0floatpacket.18:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.18,@object
	.size	.L_2il0floatpacket.18,16
	.align 16
.L_2il0floatpacket.19:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.19,@object
	.size	.L_2il0floatpacket.19,16
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
.L_2il0floatpacket.13:
	.long	0x54442d18,0x3ff921fb
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
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
