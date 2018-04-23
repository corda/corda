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
	.file "clogl.c"
	.text
..TXTST0:
# -- Begin  clogl
	.text
       .align    16,0x90
	.globl clogl
clogl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        movl      %edx, 124(%esp)
        fstpt     100(%esp)
        fldt      100(%esp)
        fstpt     112(%esp)
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    14(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.78
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.5:
        fldcw     12(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %ecx
        jge       ..B1.47
..B1.8:
        cmpl      $32767, %edx
        jge       ..B1.89
..B1.9:
        testl     %ecx, %ecx
        jne       ..B1.15
..B1.10:
        cmpl      $0, 16(%ebp)
        jne       ..B1.15
..B1.11:
        cmpl      $0, 12(%ebp)
        jne       ..B1.15
..B1.12:
        testl     %edx, %edx
        jne       ..B1.15
..B1.13:
        cmpl      $0, 28(%ebp)
        jne       ..B1.15
..B1.14:
        cmpl      $0, 24(%ebp)
        je        ..B1.46
..B1.15:
        fldt      12(%ebp)
        fld       %st(0)
        fabs      
        fstpt     (%esp)
        fldt      (%esp)
        fldt      24(%ebp)
        fld       %st(0)
        fabs      
        fstpt     16(%esp)
        fldt      16(%esp)
        movzwl    8(%esp), %ecx
        movzwl    24(%esp), %edx
        andl      $32767, %ecx
        andl      $32767, %edx
        cmpl      %edx, %ecx
        jl        ..B1.20
..B1.16:
        jne       ..B1.21
..B1.17:
        movl      4(%esp), %edx
        movl      20(%esp), %eax
        cmpl      %eax, %edx
        jb        ..B1.20
..B1.18:
        jne       ..B1.21
..B1.19:
        movl      (%esp), %edx
        cmpl      16(%esp), %edx
        jae       ..B1.21
..B1.20:
        fstpt     (%esp)
        fldt      (%esp)
        movzwl    8(%esp), %ecx
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        andl      $32767, %ecx
..B1.21:
        cmpl      $16382, %ecx
        jg        ..B1.26
..B1.22:
        jne       ..B1.43
..B1.23:
        movl      4(%esp), %edx
        cmpl      16+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.26
..B1.24:
        jne       ..B1.43
..B1.25:
        movl      12+_CONSTANTS@GOTOFF(%ebx), %edx
        cmpl      (%esp), %edx
        ja        ..B1.43
..B1.26:
        cmpl      $16383, %ecx
        jl        ..B1.31
..B1.27:
        jne       ..B1.43
..B1.28:
        movl      4(%esp), %edx
        cmpl      28+_CONSTANTS@GOTOFF(%ebx), %edx
        jb        ..B1.31
..B1.29:
        jne       ..B1.43
..B1.30:
        movl      (%esp), %edx
        cmpl      24+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.43
..B1.31:
        movzwl    24(%esp), %edx
        andl      $32767, %edx
        cmpl      $8256, %edx
        jg        ..B1.36
..B1.32:
        jne       ..B1.37
..B1.33:
        movl      20(%esp), %eax
        cmpl      64+_CONSTANTS@GOTOFF(%ebx), %eax
        ja        ..B1.36
..B1.34:
        jne       ..B1.37
..B1.35:
        movl      16(%esp), %eax
        cmpl      60+_CONSTANTS@GOTOFF(%ebx), %eax
        jbe       ..B1.37
..B1.36:
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fabs      
        fxch      %st(2)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(1)
        ja        ..B1.38
        jmp       ..B1.90
..B1.37:
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(4), %st
        fsub      %st(1), %st
        fabs      
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B1.90
..B1.38:
        cmpl      $16183, %edx
        jl        ..B1.40
..B1.39:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fld       %st(4)
        fxch      %st(4)
        fstpt     (%esp)
        fxch      %st(5)
        fstpt     16(%esp)
        fld       %st(3)
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsub      %st, %st(4)
        fsubp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(3)
        fxch      %st(5)
        fsubr     %st, %st(6)
        fxch      %st(2)
        fmul      %st(4), %st
        fsub      %st, %st(5)
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fxch      %st(4)
        fsub      %st(5), %st
        fxch      %st(5)
        fmul      %st(7), %st
        fmul      %st(5), %st
        fstpt     56(%esp)
        fldt      56(%esp)
        fxch      %st(5)
        fmul      %st(0), %st
        fstpt     72(%esp)
        fld       %st(0)
        fadd      %st(4), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(6)
        fmulp     %st, %st(1)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubrp    %st, %st(5)
        fld       %st(4)
        fadd      %st(2), %st
        fld       %st(0)
        fsub      %st(6), %st
        fsubr     %st, %st(3)
        fld       %st(1)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(2)
        fld       %st(4)
        fadd      %st(1), %st
        fld       %st(0)
        faddp     %st, %st(4)
        fld       %st(3)
        fxch      %st(5)
        fmul      %st(0), %st
        fstpt     40(%esp)
        fldt      40(%esp)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldt      72(%esp)
        faddp     %st, %st(1)
        fldt      12+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(1)
        fld       %st(5)
        fsub      %st(7), %st
        fsubr     %st, %st(3)
        fsubr     %st(6), %st
        fsubrp    %st, %st(7)
        fxch      %st(6)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(2)
        fsub      %st(4), %st
        fldt      56(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      88(%esp)
        fld       %st(0)
        fsub      %st(3), %st
        fldt      40(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fsub      %st(2), %st
        fldt      72(%esp)
        fsub      %st(1), %st
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
        fstpt     100(%esp)
        addl      $-32, %esp
        fldt      32(%esp)
        fstpt     (%esp)
        fldt      48(%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.87
..B1.40:
        cmpl      $16383, %ecx
        je        ..B1.54
..B1.41:
        fstp      %st(2)
        fxch      %st(1)
        fstpt     28(%esp)
..B1.42:
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fmul      %st(3), %st
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fsubp     %st, %st(2)
        fsubr     %st, %st(3)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fxch      %st(4)
        fmul      %st(0), %st
        fadd      %st, %st(4)
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        fld       %st(1)
        fadd      %st(4), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12+_Q1@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     100(%esp)
        addl      $-32, %esp
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.87
..B1.43:
        fstp      %st(2)
        fstp      %st(1)
        addl      $-32, %esp
        lea       72(%esp), %edx
        movl      %edx, (%esp)
        fxch      %st(1)
        fstpt     4(%esp)
        fstpt     16(%esp)
        call      __libm_hypot2l_k80
..B1.83:
        addl      $32, %esp
..B1.44:
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     16(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        addl      $-32, %esp
        fldt      72(%esp)
        lea       96(%esp), %edx
        fstpt     (%esp)
        fldt      -12(%edx)
        fstpt     12(%esp)
        movl      %edx, 24(%esp)
        call      __libm_logl_k80
..B1.84:
        addl      $32, %esp
..B1.45:
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fldt      64(%esp)
        fmul      %st(1), %st
        fldt      76(%esp)
        fmulp     %st, %st(2)
        fldt      16(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      (%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
        fstpt     100(%esp)
        addl      $-32, %esp
        fldt      24(%ebp)
        fstpt     (%esp)
        fldt      12(%ebp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.87
..B1.46:
        fldt      24(%ebp)
        fldz      
        fldt      12(%ebp)
        fxch      %st(1)
        fdivrl    .L_2il0floatpacket.8@GOTOFF(%ebx)
        fstpt     100(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.87
..B1.47:
        je        ..B1.71
..B1.48:
        cmpl      $32767, %edx
        je        ..B1.64
..B1.49:
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpt     100(%esp)
        addl      $-32, %esp
        fldt      24(%ebp)
        fstpt     (%esp)
        fldt      12(%ebp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B1.87:
        addl      $32, %esp
        fstpt     112(%esp)
..B1.50:
        testl     %edi, %edi
        je        ..B1.52
..B1.51:
        fldcw     14(%esp)
..B1.52:
        movl      100(%esp), %edx
        movl      %edx, (%esi)
        movl      112(%esp), %edx
        movl      %edx, 12(%esi)
        movl      116(%esp), %edx
        movl      %edx, 16(%esi)
        movl      104(%esp), %ecx
        movl      108(%esp), %edi
        movl      120(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      124(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.63
..B1.53:
        movl      %esi, %eax
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.54:
        cmpl      $-2147483648, 4(%esp)
        jne       ..B1.41
..B1.56:
        cmpl      $0, (%esp)
        jne       ..B1.41
..B1.58:
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(2)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
        movzwl    36(%esp), %edx
        testl     $32767, %edx
        jne       ..B1.62
..B1.59:
        cmpl      $0, 32(%esp)
        jne       ..B1.61
..B1.60:
        cmpl      $0, 28(%esp)
        je        ..B1.62
..B1.61:
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
..B1.62:
        fstpt     100(%esp)
        addl      $-32, %esp
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.87
..B1.63:
        call      __stack_chk_fail@PLT
..B1.64:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.66
..B1.65:
        cmpl      $0, 24(%ebp)
        je        ..B1.49
..B1.66:
        fldt      24(%ebp)
        cmpl      $32767, %ecx
        fmul      %st(0), %st
        fstpt     112(%esp)
        fldt      112(%esp)
        je        ..B1.68
..B1.67:
        fstpt     100(%esp)
        jmp       ..B1.50
..B1.68:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.67
..B1.69:
        cmpl      $0, 12(%ebp)
        jne       ..B1.67
..B1.70:
        fstp      %st(0)
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     100(%esp)
        jmp       ..B1.50
..B1.71:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.73
..B1.72:
        cmpl      $0, 12(%ebp)
        je        ..B1.48
..B1.73:
        fldt      12(%ebp)
        cmpl      $32767, %edx
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     112(%esp)
        je        ..B1.75
..B1.74:
        fldt      24(%ebp)
        fmulp     %st, %st(1)
        fstpt     100(%esp)
        jmp       ..B1.50
..B1.75:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.74
..B1.76:
        cmpl      $0, 24(%ebp)
        jne       ..B1.74
..B1.77:
        fstp      %st(0)
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     100(%esp)
        jmp       ..B1.50
..B1.78:
        xorl      %edi, %edi
        jmp       ..B1.7
..B1.89:
        cmpl      $32767, %ecx
        je        ..B1.71
        jmp       ..B1.48
..B1.90:
        fstp      %st(0)
        fstp      %st(0)
        jmp       ..B1.43
        .align    16,0x90
	.type	clogl,@function
	.size	clogl,.-clogl
	.data
# -- End  clogl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,12
	.space 4, 0x00 	# pad
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16358
	.word	0
	.word	37342
	.word	47265
	.word	62214
	.word	46340
	.word	16382
	.word	0
	.word	64512
	.word	65535
	.word	31
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	53710
	.word	6135
	.word	45426
	.word	16381
	.word	0
	.word	505
	.word	55756
	.word	58609
	.word	48341
	.word	16334
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8256
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,96
	.align 16
_Q1:
	.word	3134
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	49331
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.type	_Q1,@object
	.size	_Q1,24
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.data
	.hidden __libm_logl_k80
	.hidden __libm_hypot2l_k80
	.section .note.GNU-stack, ""
# End
