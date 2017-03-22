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
	.file "clog10.c"
	.text
..TXTST0:
# -- Begin  clog10f
	.text
       .align    16,0x90
	.globl clog10f
clog10f:
# parameter 1: 64 + %esp
..B1.1:
..L1:

        pushl     %ebx
        subl      $56, %esp
        flds      64(%esp)
        flds      68(%esp)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 40(%esp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fstpl     32(%esp)
        fstpl     24(%esp)
        addl      $-32, %esp
        movl      56(%esp), %edx
        lea       40(%esp), %ecx
        movl      %ecx, (%esp)
        lea       4(%esp), %eax
        movl      20(%ecx), %ecx
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      64(%esp), %edx
        movl      68(%esp), %ecx
        movl      %edx, 8(%eax)
        movl      %ecx, 12(%eax)
        call      clog10@PLT
..B1.7:
        addl      $28, %esp
..B1.2:
        fldl      8(%esp)
        fstps     (%esp)
        fldl      16(%esp)
        fstps     4(%esp)
        movl      40(%esp), %ecx
        xorl      %esp, %ecx
        movl      (%esp), %eax
        movl      4(%esp), %edx
        cmpl      %gs:20, %ecx
        jne       ..B1.4
..B1.3:
        addl      $56, %esp
        popl      %ebx
        ret       
..B1.4:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	clog10f,@function
	.size	clog10f,.-clog10f
	.data
# -- End  clog10f
	.text
# -- Begin  clog10
	.text
       .align    16,0x90
	.globl clog10
clog10:
# parameter 1: 12 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        fldz      
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 176(%esp)
        fstl      160(%esp)
        fstpl     168(%esp)
..B2.2:
        fnstcw    150(%esp)
..B2.3:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnan
..B2.67:
        addl      $16, %esp
..B2.4:
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        testl     %eax, %eax
        jne       ..B2.15
..B2.5:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnan
..B2.68:
        addl      $16, %esp
..B2.6:
        testl     %eax, %eax
        jne       ..B2.15
..B2.7:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.69:
        addl      $16, %esp
..B2.8:
        testl     %eax, %eax
        jne       ..B2.15
..B2.9:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.70:
        addl      $16, %esp
..B2.10:
        testl     %eax, %eax
        jne       ..B2.15
..B2.11:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_iszero
..B2.71:
        addl      $16, %esp
..B2.12:
        testl     %eax, %eax
        je        ..B2.45
..B2.13:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_iszero
..B2.72:
        addl      $16, %esp
..B2.14:
        testl     %eax, %eax
        je        ..B2.45
..B2.15:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnan
..B2.73:
        addl      $16, %esp
..B2.16:
        testl     %eax, %eax
        je        ..B2.21
..B2.17:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.74:
        addl      $16, %esp
..B2.18:
        testl     %eax, %eax
        je        ..B2.20
..B2.19:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     160(%esp)
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.20:
        fldl      12(%ebp)
        fmull     20(%ebp)
        fstl      160(%esp)
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.21:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnan
..B2.75:
        addl      $16, %esp
..B2.22:
        testl     %eax, %eax
        je        ..B2.28
..B2.23:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.76:
        addl      $16, %esp
..B2.24:
        testl     %eax, %eax
        je        ..B2.26
..B2.25:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     160(%esp)
        fldl      20(%ebp)
        fmul      %st(0), %st
        jmp       ..B2.27
..B2.26:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstl      160(%esp)
..B2.27:
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.28:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.77:
        addl      $16, %esp
..B2.29:
        testl     %eax, %eax
        je        ..B2.35
..B2.30:
        fldl      12(%ebp)
        fld1      
        fsts      12(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstl      8(%esp)
        fstpl     16(%esp)
        call      copysign@PLT
..B2.78:
        fldl      16(%esp)
        fmul      %st, %st(1)
        addl      $16, %esp
        fldl      20(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpl     160(%esp)
        fldt      CLOG10RECA@GOTOFF(%ebx)
        fldl      NEGINFA@GOTOFF(%ebx)
        fucomp    %st(2)
        fnstsw    %ax
        sahf      
        jp        ..B2.31
        je        ..B2.62
..B2.31:
        fldl      POSINFA@GOTOFF(%ebx)
        fucomp    %st(2)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jne       ..B2.34
        jp        ..B2.34
..B2.33:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B2.34:
        addl      $-16, %esp
        flds      28(%esp)
        fstpl     (%esp)
        fxch      %st(1)
        fstpl     8(%esp)
        fstpt     16(%esp)
        call      copysign@PLT
..B2.79:
        fldt      16(%esp)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.35:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isinf
..B2.80:
        addl      $16, %esp
..B2.36:
        testl     %eax, %eax
        je        ..B2.41
..B2.37:
        fldl      12(%ebp)
        fldl      POSINFA@GOTOFF(%ebx)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.38
        je        ..B2.39
..B2.38:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fldt      CLOG10RECA@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        jmp       ..B2.40
..B2.39:
        fldl      ZEROA@GOTOFF(%ebx)
..B2.40:
        fldl      20(%ebp)
        fxch      %st(2)
        fmul      %st(0), %st
        fadd      %st(2), %st
        fstpl     160(%esp)
        fstl      168(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpl     8(%esp)
        call      copysign@PLT
..B2.81:
        addl      $16, %esp
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.41:
        fldl      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fdivl     ZEROA@GOTOFF(%ebx)
        fstpl     160(%esp)
        addl      $-16, %esp
        fldl      12(%ebp)
        fstpl     (%esp)
        call      __libm_internal_isnegzero
..B2.82:
        addl      $16, %esp
..B2.42:
        testl     %eax, %eax
        je        ..B2.44
..B2.43:
        fldl      20(%ebp)
        addl      $-16, %esp
        fld1      
        fstpl     (%esp)
        fstpl     8(%esp)
        call      copysign@PLT
..B2.83:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fldt      CLOG10RECA@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fmulp     %st, %st(1)
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.44:
        fldl      20(%ebp)
        fstpl     168(%esp)
        jmp       ..B2.60
..B2.45:
        movzwl    150(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B2.64
..B2.46:
        orl       $-64768, %ecx
        movw      %cx, 148(%esp)
..B2.47:
        fldcw     148(%esp)
..B2.48:
        movl      $1, %edi
..B2.49:
        fldl      12(%ebp)
        fld       %st(0)
        fabs      
        fldl      20(%ebp)
        fld       %st(0)
        fabs      
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jae       ..B2.51
        jp        ..B2.51
..B2.50:
        fld       %st(2)
        jmp       ..B2.52
..B2.51:
        fld       %st(0)
..B2.52:
        fxch      %st(1)
        fcom      %st(3)
        fnstsw    %ax
        sahf      
        jbe       ..B2.54
..B2.53:
        fstp      %st(0)
        jmp       ..B2.55
..B2.54:
        fstp      %st(3)
..B2.55:
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fld       %st(4)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fld1      
        fsubr     %st(2), %st
        fabs      
        fcompl    .L_2il0floatpacket.4@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jbe       ..B2.57
..B2.56:
        fstp      %st(5)
        fstp      %st(2)
        fstp      %st(3)
        fstp      %st(2)
        addl      $-16, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpl     16(%esp)
        fstpl     168(%esp)
        call      logl@PLT
..B2.84:
        fldl      168(%esp)
        fldl      16(%esp)
        addl      $16, %esp
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        jmp       ..B2.58
..B2.57:
        fstp      %st(4)
        fstp      %st(5)
        fstp      %st(4)
        fldt      .L_2il0floatpacket.5@GOTOFF(%ebx)
        fld       %st(1)
        fmulp     %st, %st(1)
        fxch      %st(4)
        fstpt     16(%esp)
        fxch      %st(3)
        fstpt     28(%esp)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      28(%esp)
        fldt      28(%esp)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     52(%esp)
        fldt      40(%esp)
        fldt      40(%esp)
        fmulp     %st, %st(1)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
        fldt      52(%esp)
        fldt      40(%esp)
        fldt      52(%esp)
        fldt      52(%esp)
        fmulp     %st, %st(1)
        fldt      (%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     64(%esp)
        fxch      %st(2)
        fstpt     76(%esp)
        fldt      .L_2il0floatpacket.5@GOTOFF(%ebx)
        fmul      %st(2), %st
        fstpt     28(%esp)
        fldt      28(%esp)
        fldt      28(%esp)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fstpt     88(%esp)
        fldt      88(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     100(%esp)
        fldt      88(%esp)
        fldt      88(%esp)
        fmulp     %st, %st(1)
        fldt      76(%esp)
        fsubrp    %st, %st(1)
        fldt      100(%esp)
        fmulp     %st, %st(2)
        fldt      88(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      100(%esp)
        fldt      100(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     112(%esp)
        fldt      16(%esp)
        fldt      76(%esp)
        faddp     %st, %st(1)
        fstpt     124(%esp)
        fldt      124(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
        fstpt     136(%esp)
        fldt      16(%esp)
        fldt      124(%esp)
        fldt      136(%esp)
        fsubrp    %st, %st(1)
        fsubrp    %st, %st(1)
        fldt      76(%esp)
        fldt      136(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     136(%esp)
        fldt      124(%esp)
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        fsubrp    %st, %st(1)
        fstpt     124(%esp)
        fldt      124(%esp)
        fldt      136(%esp)
        fldt      64(%esp)
        faddp     %st, %st(1)
        fldt      112(%esp)
        addl      $-16, %esp
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      log1pl@PLT
..B2.85:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fldl      12(%ebp)
        fldl      20(%ebp)
..B2.58:
        fldt      INVLN10A@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpl     160(%esp)
        fxch      %st(2)
        fstpt     (%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B2.86:
        addl      $32, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fmulp     %st, %st(1)
        fstpl     168(%esp)
        je        ..B2.60
..B2.59:
        fldcw     150(%esp)
..B2.60:
        movl      160(%esp), %edx
        movl      %edx, (%esi)
        movl      164(%esp), %ecx
        movl      168(%esp), %edi
        movl      172(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      176(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.63
..B2.61:
        movl      %esi, %eax
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.62:
        fstp      %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        jmp       ..B2.34
..B2.63:
        call      __stack_chk_fail@PLT
..B2.64:
        xorl      %edi, %edi
        jmp       ..B2.49
        .align    16,0x90
	.type	clog10,@function
	.size	clog10,.-clog10
	.data
# -- End  clog10
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0xc0,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,12
	.space 4, 0x00 	# pad
	.align 16
CLOG10RECA:
	.long	2547844487
	.long	2929975909
	.long	16382
	.long	0
	.type	CLOG10RECA,@object
	.size	CLOG10RECA,16
	.align 16
NEGINFA:
	.long	0
	.long	4293918720
	.type	NEGINFA,@object
	.size	NEGINFA,8
	.space 8, 0x00 	# pad
	.align 16
POSINFA:
	.long	0
	.long	2146435072
	.type	POSINFA,@object
	.size	POSINFA,8
	.space 8, 0x00 	# pad
	.align 16
ZEROA:
	.long	0
	.long	0
	.type	ZEROA,@object
	.size	ZEROA,8
	.space 8, 0x00 	# pad
	.align 16
INVLN10A:
	.long	925397397
	.long	3730561193
	.long	16381
	.long	0
	.type	INVLN10A,@object
	.size	INVLN10A,16
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xeb851eb8,0x3f9eb851
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.data
	.hidden __libm_internal_isnegzero
	.hidden __libm_internal_iszero
	.hidden __libm_internal_isnan
	.hidden __libm_internal_isinf
	.section .note.GNU-stack, ""
# End
