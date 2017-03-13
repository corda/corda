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
	.file "catanh.c"
	.text
..TXTST0:
# -- Begin  catanhf
	.text
       .align    16,0x90
	.globl catanhf
catanhf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_catanhf.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        cvtps2pd  %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
..___tag_value_catanhf.4:
        call      catanh@PLT
..___tag_value_catanhf.5:
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
	.type	catanhf,@function
	.size	catanhf,.-catanhf
	.data
# -- End  catanhf
	.text
# -- Begin  catanh
	.text
       .align    16,0x90
	.globl catanh
catanh:
# parameter 1: %xmm0
..B2.1:
	.cfi_startproc
..___tag_value_catanh.8:
..L9:

        pushq     %r15
	.cfi_def_cfa_offset 16
	.cfi_offset 15, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        xorb      %r15b, %r15b
        subq      $200, %rsp
	.cfi_def_cfa_offset 224
        movsd     %xmm0, (%rsp)
        movsd     %xmm1, 8(%rsp)
..B2.2:
        fnstcw    186(%rsp)
..B2.3:
        movzwl    6(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        jge       ..B2.26
..B2.4:
        testl     %eax, %eax
        jle       ..B2.54
..B2.5:
        movzwl    14(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        cmpl      $2047, %edx
        jge       ..B2.27
..B2.6:
        testl     %edx, %edx
        jle       ..B2.52
..B2.7:
        movzwl    186(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.11
..B2.8:
        orl       $-64768, %edx
        movw      %dx, 184(%rsp)
..B2.9:
        fldcw     184(%rsp)
..B2.10:
        movb      $1, %r15b
..B2.11:
        fldl      (%rsp)
        fstl      160(%rsp)
        fmul      %st(0), %st
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fldl      8(%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      .L_2il0floatpacket.13(%rip)
        fldt      pc_thresh(%rip)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fabs      
        fcomip    %st(2), %st
        fxch      %st(1)
        fstp      %st(0)
        jae       ..B2.13
        jp        ..B2.13
..B2.12:
        fldt      .L_2il0floatpacket.13(%rip)
        faddl     160(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 256
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        call      atan2l@PLT
..B2.79:
        fldt      32(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 224
        fchs      
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      .L_2il0floatpacket.13(%rip)
        fsubl     160(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 256
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     48(%rsp)
        call      atan2l@PLT
..B2.78:
        fldt      48(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 224
        fldt      .L_2il0floatpacket.14(%rip)
        fxch      %st(2)
        fstpt     128(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        fsubrp    %st, %st(1)
        fmul      %st(2), %st
        fstpl     176(%rsp)
        jmp       ..B2.14
..B2.13:
        fldt      96(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 256
        fldt      .L_2il0floatpacket.15(%rip)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
..B2.80:
        fldt      .L_2il0floatpacket.14(%rip)
        addq      $32, %rsp
	.cfi_def_cfa_offset 224
        fldt      .L_2il0floatpacket.13(%rip)
        fld       %st(0)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpl     176(%rsp)
        fldl      160(%rsp)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     80(%rsp)
        fsubp     %st, %st(1)
..B2.14:
        fldt      80(%rsp)
        lea       ones(%rip), %rdx
        fstpt     112(%rsp)
        fchs      
        fldt      112(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     144(%rsp)
        fxch      %st(1)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      144(%rsp)
        fldt      96(%rsp)
        fdivrp    %st, %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      .L_2il0floatpacket.13(%rip)
        fldt      pc_thresh(%rip)
        movb      121(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fxch      %st(2)
        fmull     (%rdx,%rcx,8)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsubp     %st, %st(1)
        fabs      
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B2.16
..B2.15:
        fstp      %st(0)
        fldt      112(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 240
        fstpt     (%rsp)
        call      logl@PLT
..B2.81:
        fldt      .L_2il0floatpacket.16(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 224
        fmulp     %st, %st(1)
        fstpl     168(%rsp)
        jmp       ..B2.17
..B2.16:
        fldt      48(%rsp)
        fldt      64(%rsp)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.13(%rip)
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      .L_2il0floatpacket.15(%rip)
        fmull     160(%rsp)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      96(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 240
        fdivrp    %st, %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atanhl@PLT
..B2.82:
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 224
        fmulp     %st, %st(1)
        fstpl     168(%rsp)
..B2.17:
        movzwl    174(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.20
..B2.18:
        testl     $1048575, 172(%rsp)
        jne       ..B2.23
..B2.19:
        cmpl      $0, 168(%rsp)
        jne       ..B2.23
..B2.20:
        movzwl    182(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.24
..B2.21:
        testl     $1048575, 180(%rsp)
        jne       ..B2.23
..B2.22:
        cmpl      $0, 176(%rsp)
        je        ..B2.24
..B2.23:
        lea       _LDB_MIN_NORMAL(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     32(%rsp)
..B2.24:
        testb     %r15b, %r15b
        je        ..B2.51
..B2.25:
        fldcw     186(%rsp)
        jmp       ..B2.51
..B2.26:
        movzwl    14(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
..B2.27:
        cmpl      $2047, %eax
        je        ..B2.68
..B2.28:
        cmpl      $2047, %edx
        je        ..B2.61
..B2.29:
        cmpl      $2047, %edx
        jge       ..B2.50
..B2.30:
        testl     %eax, %eax
        jle       ..B2.59
..B2.31:
        cmpl      $1023, %eax
        jge       ..B2.33
..B2.32:
        movsd     (%rsp), %xmm0
        call      atanh@PLT
..B2.83:
        movb      15(%rsp), %dl
        lea       zeros(%rip), %rax
        andb      $-128, %dl
        lea       ones(%rip), %rcx
        movsd     %xmm0, 168(%rsp)
        shrb      $7, %dl
        movsd     (%rax), %xmm1
        movzbl    %dl, %ebx
        mulsd     (%rcx,%rbx,8), %xmm1
        movsd     %xmm1, 176(%rsp)
        jmp       ..B2.51
..B2.33:
        je        ..B2.57
..B2.34:
        cmpl      $2047, %eax
        jge       ..B2.49
..B2.35:
        movzwl    186(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.39
..B2.36:
        orl       $-64768, %edx
        movw      %dx, 184(%rsp)
..B2.37:
        fldcw     184(%rsp)
..B2.38:
        movb      $1, %r15b
..B2.39:
        fldl      (%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fldl      8(%rsp)
        fmul      %st(0), %st
        fldt      .L_2il0floatpacket.13(%rip)
        fld       %st(0)
        fadd      %st(4), %st
        fstpt     112(%rsp)
        fld       %st(0)
        fsub      %st(4), %st
        fchs      
        fldt      112(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fadd      %st(3), %st
        fstpt     144(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        fadd      %st(2), %st
        fstpt     96(%rsp)
        fldt      144(%rsp)
        fldt      96(%rsp)
        fdivrp    %st, %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      pc_thresh(%rip)
        movb      121(%rsp), %bl
        andb      $-128, %bl
        shrb      $7, %bl
        movzbl    %bl, %eax
        lea       ones(%rip), %rbx
        fxch      %st(1)
        fmull     (%rbx,%rax,8)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fsub      %st(2), %st
        fabs      
        fcomip    %st(1), %st
        fstp      %st(0)
        jbe       ..B2.41
..B2.40:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(0)
        fldt      112(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 240
        fstpt     (%rsp)
        call      logl@PLT
..B2.84:
        fldt      .L_2il0floatpacket.16(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 224
        fmulp     %st, %st(1)
        fstpl     168(%rsp)
        jmp       ..B2.42
..B2.41:
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     96(%rsp)
        fldt      .L_2il0floatpacket.15(%rip)
        fmulp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fldt      96(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 240
        fdivrp    %st, %st(1)
        fstpt     (%rsp)
        call      atanhl@PLT
..B2.85:
        fldt      .L_2il0floatpacket.14(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 224
        fmulp     %st, %st(1)
        fstpl     168(%rsp)
..B2.42:
        movzwl    174(%rsp), %eax
        testl     $32752, %eax
        jne       ..B2.46
..B2.43:
        testl     $1048575, 172(%rsp)
        jne       ..B2.45
..B2.44:
        cmpl      $0, 168(%rsp)
        je        ..B2.46
..B2.45:
        lea       _LDB_MIN_NORMAL(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     32(%rsp)
..B2.46:
        testb     %r15b, %r15b
        je        ..B2.48
..B2.47:
        fldcw     186(%rsp)
..B2.48:
        movb      15(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rbx,%rdx,8), %xmm0
        call      asin@PLT
        jmp       ..B2.88
..B2.49:
        movb      7(%rsp), %al
        lea       ones(%rip), %rbx
        andb      $-128, %al
        shrb      $7, %al
        pxor      %xmm0, %xmm0
        movb      15(%rsp), %cl
        andb      $-128, %cl
        shrb      $7, %cl
        movzbl    %al, %edx
        movzbl    %cl, %esi
        mulsd     (%rbx,%rdx,8), %xmm0
        movsd     %xmm0, 168(%rsp)
        movsd     (%rbx,%rsi,8), %xmm0
        call      asin@PLT
        jmp       ..B2.88
..B2.50:
        movb      7(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        pxor      %xmm0, %xmm0
        movzbl    %al, %ecx
        mulsd     (%rdx,%rcx,8), %xmm0
        movsd     %xmm0, 168(%rsp)
        movsd     8(%rsp), %xmm0
        call      atan@PLT
..B2.88:
        movsd     %xmm0, 176(%rsp)
..B2.51:
        movsd     168(%rsp), %xmm0
        movsd     176(%rsp), %xmm1
        addq      $200, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 224
	.cfi_offset 3, -24
	.cfi_offset 15, -16
..B2.52:
        testl     $1048575, 12(%rsp)
        jne       ..B2.7
..B2.53:
        cmpl      $0, 8(%rsp)
        jne       ..B2.7
        jmp       ..B2.27
..B2.54:
        testl     $1048575, 4(%rsp)
        jne       ..B2.5
..B2.55:
        cmpl      $0, (%rsp)
        jne       ..B2.5
        jmp       ..B2.26
..B2.57:
        testl     $1048575, 4(%rsp)
        jne       ..B2.34
..B2.58:
        cmpl      $0, (%rsp)
        je        ..B2.32
        jmp       ..B2.34
..B2.59:
        testl     $1048575, 4(%rsp)
        jne       ..B2.31
..B2.60:
        cmpl      $0, (%rsp)
        jne       ..B2.31
        jmp       ..B2.50
..B2.61:
        testl     $1048575, 12(%rsp)
        jne       ..B2.63
..B2.62:
        cmpl      $0, 8(%rsp)
        je        ..B2.29
..B2.63:
        cmpl      $2047, %eax
        jge       ..B2.29
..B2.64:
        testl     %eax, %eax
        jle       ..B2.66
..B2.65:
        movsd     8(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 168(%rsp)
        movsd     %xmm0, 176(%rsp)
        jmp       ..B2.51
..B2.66:
        testl     $1048575, 4(%rsp)
        jne       ..B2.65
..B2.67:
        cmpl      $0, (%rsp)
        jne       ..B2.65
        jmp       ..B2.29
..B2.68:
        testl     $1048575, 4(%rsp)
        jne       ..B2.70
..B2.69:
        cmpl      $0, (%rsp)
        je        ..B2.28
..B2.70:
        cmpl      $2047, %edx
        jge       ..B2.72
..B2.71:
        movsd     (%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 168(%rsp)
        movsd     %xmm0, 176(%rsp)
        jmp       ..B2.51
..B2.72:
        testl     $1048575, 12(%rsp)
        jne       ..B2.75
..B2.73:
        cmpl      $0, 8(%rsp)
        jne       ..B2.75
..B2.74:
        movsd     (%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, (%rsp)
        movsd     8(%rsp), %xmm0
        movq      $0, 168(%rsp)
        call      atan@PLT
        jmp       ..B2.88
..B2.75:
        movsd     8(%rsp), %xmm1
        movsd     (%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        mulsd     %xmm1, %xmm1
        movsd     %xmm0, 168(%rsp)
        movsd     %xmm1, 176(%rsp)
        jmp       ..B2.51
        .align    16,0x90
	.cfi_endproc
	.type	catanh,@function
	.size	catanh,.-catanh
	.data
# -- End  catanh
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 16
pc_thresh:
	.byte	0x00,0xc0,0xf5,0x28,0x5c,0x8f,0xc2,0xf5,0xf9,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	pc_thresh,@object
	.size	pc_thresh,16
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
	.align 8
zeros:
	.long	0x00000000,0x00000000
	.long	0x00000000,0x00000000
	.type	zeros,@object
	.size	zeros,16
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
