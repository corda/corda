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
	.file "sindl.c"
	.text
..TXTST0:
# -- Begin  sindl
	.text
       .align    16,0x90
	.globl sindl
sindl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $84, %esp
..B1.2:
        fnstcw    30(%esp)
..B1.3:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $3840, %eax
        cmpl      $768, %eax
        je        ..B1.53
..B1.4:
        andl      $-3841, %edx
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.5:
        fldcw     28(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzbl    17(%ebp), %esi
        movzwl    16(%ebp), %ecx
        andl      $128, %esi
        andl      $32767, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        shrl      $7, %esi
        movl      %eax, 12(%esp)
        cmpl      $16446, %ecx
        jge       ..B1.26
..B1.8:
        cmpl      $16318, %ecx
        jge       ..B1.19
..B1.9:
        fldt      8(%ebp)
        movl      %eax, %edx
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B1.10
        je        ..B1.43
..B1.10:
        testl     %ecx, %ecx
        jle       ..B1.16
..B1.11:
        cmpl      $6, %ecx
        jge       ..B1.15
..B1.12:
        fldt      16(%esp)
        testl     %edi, %edi
        movl      %edx, %eax
        fabs      
        fldt      _TWO5600@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_53H@GOTOFF(%eax)
        fmul      %st(1), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fldl      8+_sindl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(2)
        fldl      _sindl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(2)
        flds      ones@GOTOFF(%eax,%esi,4)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      fBreaker@GOTOFF(%eax)
        fmul      %st(1), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      12+_TWO5600@GOTOFF(%eax)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        fldt      _Underflow@GOTOFF(%eax)
        fldt      .L_2il0floatpacket.5@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpt     60(%esp)
        je        ..B1.14
..B1.13:
        fldcw     30(%esp)
..B1.14:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        je        ..B1.40
..B1.16:
        fldt      16(%esp)
        testl     %edi, %edi
        movl      %edx, %eax
        fabs      
        fldt      _TWO5600@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _TWO_53H@GOTOFF(%eax)
        fmul      %st(1), %st
        fadd      %st, %st(2)
        fsubrp    %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fldl      8+_sindl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(2)
        fldl      _sindl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(2)
        flds      ones@GOTOFF(%eax,%esi,4)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      fBreaker@GOTOFF(%eax)
        fmul      %st(1), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      12+_TWO5600@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.18
..B1.17:
        fldcw     30(%esp)
..B1.18:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        fldt      8(%ebp)
        movl      %eax, %ebx
        fabs      
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fldt      _Rcp90@GOTOFF(%ebx)
        fmul      %st(2), %st
        fadd      %st(1), %st
        fstpt     60(%esp)
        fldt      60(%esp)
        movl      60(%esp), %edx
        movl      %edx, %ecx
        andl      $2, %ecx
        fsubp     %st, %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        shrl      $1, %ecx
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        xorl      %ecx, %esi
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.20
        je        ..B1.46
..B1.20:
        movl      %ebx, %eax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fld       %st(3)
        testb     $1, %dl
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        fmul      %st(5), %st
        fsub      %st, %st(4)
        fsubp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fxch      %st(4)
        fsub      %st(5), %st
        fmul      %st, %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fldl      _TWO_53H@GOTOFF(%eax)
        fmul      %st, %st(3)
        fld       %st(0)
        fmul      %st(2), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsub      %st(4), %st
        faddp     %st, %st(5)
        fld       %st(5)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fstpt     48(%esp)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        flds      ones@GOTOFF(%eax,%esi,4)
        fstps     12(%esp)
        je        ..B1.22
..B1.21:
        fstp      %st(6)
        fstp      %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        fmul      %st, %st(1)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(4)
        fstpt     (%esp)
        fldt      (%esp)
        fld       %st(3)
        fmul      %st(7), %st
        fxch      %st(1)
        fmul      %st(6), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fldt      84+_cosdl_poly_coeff@GOTOFF(%eax)
        fmul      %st(5), %st
        fldt      60+_cosdl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      36+_cosdl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_cosdl_poly_coeff@GOTOFF(%eax)
        fmul      %st(6), %st
        fldt      48+_cosdl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(6), %st
        fldt      24+_cosdl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldl      24+_cosdl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fldl      8+_cosdl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldl      16+_cosdl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldl      _cosdl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmulp     %st, %st(2)
        fld       %st(1)
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        flds      12(%esp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.4@GOTOFF(%eax)
        fld       %st(0)
        fadd      %st(4), %st
        fmul      %st, %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        jmp       ..B1.23
..B1.22:
        fld       %st(6)
        fmul      %st(2), %st
        fstpt     16(%esp)
        fld       %st(4)
        fmul      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(6)
        fmul      %st(7), %st
        fxch      %st(4)
        fstpt     (%esp)
        fldt      48(%esp)
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fld       %st(5)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fldt      96+_sindl_poly_coeff@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      72+_sindl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_sindl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_sindl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      84+_sindl_poly_coeff@GOTOFF(%eax)
        fmul      %st(2), %st
        fldt      60+_sindl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_sindl_poly_coeff@GOTOFF(%eax)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%esp)
        fmul      %st, %st(1)
        fldl      24+_sindl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      8+_sindl_mp_poly_coeff@GOTOFF(%eax)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fldl      16+_sindl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldl      _sindl_mp_poly_coeff@GOTOFF(%eax)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        flds      12(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
..B1.23:
        testl     %edi, %edi
        je        ..B1.25
..B1.24:
        fldcw     30(%esp)
..B1.25:
        fldt      32(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        cmpl      $32767, %ecx
        je        ..B1.52
..B1.27:
        lea       -16446(%ecx), %eax
        movl      %eax, (%esp)
        cmpl      $14, %eax
        jle       ..B1.29
..B1.28:
        addl      $-16449, %ecx
        movl      $715827883, %eax
        imull     %ecx
        movl      %ecx, %ebx
        sarl      $1, %edx
        sarl      $31, %ebx
        subl      %ebx, %edx
        lea       (,%edx,8), %ebx
        lea       (%ebx,%edx,4), %eax
        subl      %eax, %ecx
        lea       3(%ecx), %ecx
        movl      %ecx, (%esp)
..B1.29:
        movl      12(%ebp), %ecx
        movl      %ecx, %ebx
        shll      $8, %ebx
        movl      $381774871, %eax
        movl      %ebx, %edx
        andl      $-16777216, %ecx
        shrl      $3, %edx
        mull      %edx
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        movl      %ecx, %edx
        shrl      $19, %ecx
        addl      %eax, %ebx
        movl      $381774871, %eax
        shrl      $16, %edx
        movl      %edx, 4(%esp)
        mull      %ecx
        shrl      $2, %edx
        imull     $-360, %edx, %ecx
        movl      4(%esp), %eax
        addl      %ecx, %eax
        movl      8(%ebp), %ecx
        movl      %ecx, %edx
        shrl      $3, %edx
        addl      %eax, %ebx
        movl      $381774871, %eax
        mull      %edx
        shrl      $2, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %ecx
        movl      $-1240768329, %eax
        addl      %ecx, %ebx
        movl      (%esp), %ecx
        shll      %cl, %ebx
        imull     %ebx
        addl      %ebx, %edx
        movl      %ebx, %ecx
        sarl      $8, %edx
        sarl      $31, %ecx
        subl      %ecx, %edx
        imull     $-360, %edx, %eax
        addl      %eax, %ebx
        cmpl      $180, %ebx
        jl        ..B1.31
..B1.30:
        addl      $-180, %ebx
        movl      $2, %eax
        jmp       ..B1.32
..B1.31:
        xorl      %eax, %eax
..B1.32:
        cmpl      $90, %ebx
        jl        ..B1.34
..B1.33:
        incl      %eax
        addl      $-90, %ebx
..B1.34:
        movl      %eax, %edx
        movl      %eax, %ecx
        andl      $2, %edx
        shrl      $1, %edx
        xorl      %edx, %esi
        orl       %ebx, %ecx
        je        ..B1.36
..B1.35:
        andl      $1, %eax
        shll      $4, %eax
        shll      $5, %ebx
        addl      %ebx, %eax
        movl      12(%esp), %edx
        fldl      __libm_sindl_cosdl_table@GOTOFF(%eax,%edx)
        faddl     8+__libm_sindl_cosdl_table@GOTOFF(%edx,%eax)
        fmuls     ones@GOTOFF(%edx,%esi,4)
        fstpt     (%esp)
        jmp       ..B1.37
..B1.36:
        movl      12(%esp), %eax
        fldt      .L_2il0floatpacket.0@GOTOFF(%eax)
        fstpt     (%esp)
..B1.37:
        testl     %edi, %edi
        je        ..B1.39
..B1.38:
        fldcw     30(%esp)
..B1.39:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.40:
        movl      12(%ebp), %eax
        cmpl      $-449912621, %eax
        jb        ..B1.12
..B1.41:
        jne       ..B1.16
..B1.42:
        cmpl      $504348098, 8(%ebp)
        jbe       ..B1.12
        jmp       ..B1.16
..B1.43:
        testl     %edi, %edi
        je        ..B1.45
..B1.44:
        fldcw     30(%esp)
..B1.45:
        fldt      16(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.46:
        fstp      %st(0)
        testb     $1, %dl
        je        ..B1.48
..B1.47:
        movl      %ebx, %eax
        flds      ones@GOTOFF(%eax,%esi,4)
        fstps     (%esp)
        jmp       ..B1.49
..B1.48:
        fldz      
        fstps     (%esp)
..B1.49:
        testl     %edi, %edi
        je        ..B1.51
..B1.50:
        fldcw     30(%esp)
..B1.51:
        flds      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.52:
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.37
..B1.53:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	sindl,@function
	.size	sindl,.-sindl
	.data
# -- End  sindl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0x3e,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xb4,0x05,0x40,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.space 4, 0x00 	# pad
	.align 16
_sindl_mp_poly_coeff:
	.long	2723323193
	.long	1066524486
	.long	2863989530
	.long	1008058840
	.long	227815288
	.long	3199056770
	.long	3752327299
	.long	3142458725
	.type	_sindl_mp_poly_coeff,@object
	.size	_sindl_mp_poly_coeff,32
	.align 16
_Underflow:
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.type	_Underflow,@object
	.size	_Underflow,12
	.space 4, 0x00 	# pad
	.align 16
_cosdl_poly_coeff:
	.word	52350
	.word	41213
	.word	3800
	.word	40885
	.word	49138
	.word	0
	.word	28613
	.word	7908
	.word	35668
	.word	34008
	.word	16355
	.word	0
	.word	19927
	.word	58728
	.word	49885
	.word	45261
	.word	49106
	.word	0
	.word	32884
	.word	22035
	.word	23267
	.word	64541
	.word	16320
	.word	0
	.word	36401
	.word	51897
	.word	8309
	.word	57265
	.word	49070
	.word	0
	.word	34286
	.word	2728
	.word	41564
	.word	34642
	.word	16284
	.word	0
	.word	63248
	.word	18030
	.word	35596
	.word	60796
	.word	49032
	.word	0
	.word	52149
	.word	21294
	.word	63985
	.word	40123
	.word	16245
	.word	0
	.type	_cosdl_poly_coeff,@object
	.size	_cosdl_poly_coeff,96
	.align 16
_cosdl_mp_poly_coeff:
	.long	3675529145
	.long	3206805153
	.long	2134983071
	.long	3151100167
	.long	1787026573
	.long	1043372817
	.long	205083639
	.long	988746860
	.type	_cosdl_mp_poly_coeff,@object
	.size	_cosdl_mp_poly_coeff,32
	.align 16
_sindl_poly_coeff:
	.word	51374
	.word	38121
	.word	13586
	.word	36602
	.word	16377
	.word	0
	.word	50116
	.word	41339
	.word	4204
	.word	60892
	.word	49130
	.word	0
	.word	33704
	.word	2155
	.word	42839
	.word	60780
	.word	16346
	.word	0
	.word	21250
	.word	19076
	.word	27901
	.word	57780
	.word	49097
	.word	0
	.word	9076
	.word	49244
	.word	613
	.word	64083
	.word	16311
	.word	0
	.word	40572
	.word	30418
	.word	36251
	.word	46520
	.word	49061
	.word	0
	.word	3227
	.word	25505
	.word	5540
	.word	47626
	.word	16274
	.word	0
	.word	60933
	.word	3300
	.word	57416
	.word	36218
	.word	49023
	.word	0
	.word	45811
	.word	42646
	.word	37125
	.word	42185
	.word	16235
	.word	0
	.type	_sindl_poly_coeff,@object
	.size	_sindl_poly_coeff,108
	.align 4
_TWO_53H:
	.long	0
	.long	1128792064
	.type	_TWO_53H,@object
	.size	_TWO_53H,8
	.align 4
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
fBreaker:
	.byte	0x00,0x00,0x00,0x00,0x00,0x08,0x00,0x80,0x13,0x40,0x00,0x00
	.type	fBreaker,@object
	.size	fBreaker,12
	.align 2
_TWO5600:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	21983
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	10783
	.word	0
	.type	_TWO5600,@object
	.size	_TWO5600,24
	.align 2
_Rcp90:
	.word	46603
	.word	2912
	.word	24758
	.word	46603
	.word	16376
	.word	0
	.type	_Rcp90,@object
	.size	_Rcp90,12
	.data
	.hidden __libm_sindl_cosdl_table
	.section .note.GNU-stack, ""
# End
