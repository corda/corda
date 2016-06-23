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
	.file "acoshl.c"
	.text
..TXTST0:
# -- Begin  acoshl
	.text
       .align    16,0x90
	.globl acoshl
acoshl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        movl      %eax, %ebx
        movzbl    17(%ebp), %esi
        andl      $32767, %ebx
        shrl      $7, %esi
        shll      $15, %esi
        movl      12(%ebp), %edi
        orl       %ebx, %esi
        movl      %edi, %edx
        shll      $16, %esi
        shrl      $16, %edx
        orl       %edx, %esi
        addl      $-1073709056, %esi
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $1073741824, %esi
        jae       ..B1.44
..B1.4:
        cmpl      $8192, %esi
        jae       ..B1.21
..B1.5:
        cmpl      $64, %esi
        jae       ..B1.14
..B1.6:
        cmpl      $16383, %ebx
        je        ..B1.39
..B1.7:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.8:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.9:
        fldcw     12(%esp)
..B1.10:
        movl      $1, %eax
..B1.11:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_52H@GOTOFF(%ecx)
        fld       %st(0)
        fxch      %st(2)
        faddl     8+_ones@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fsub      %st(4), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      60+_Q1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      36+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      12+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_Q1@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      24+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fldt      _Q1@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(1)
        fmulp     %st, %st(5)
        fadd      %st(4), %st
        fsqrt     
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(1), %st
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(6)
        fld       %st(4)
        fmul      %st(1), %st
        fsubrp    %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(5)
        fdivrp    %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        fadd      %st(1), %st
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.13
..B1.12:
        fldcw     14(%esp)
..B1.13:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.42
..B1.15:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.16:
        fldcw     12(%esp)
..B1.17:
        movl      $1, %eax
..B1.18:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_52H@GOTOFF(%ecx)
        fldt      132+_Q@GOTOFF(%ecx)
        fxch      %st(2)
        faddl     8+_ones@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fld       %st(1)
        fsub      %st(3), %st
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fxch      %st(4)
        fstpt     16(%esp)
        fldt      16(%esp)
        fmul      %st(0), %st
        fadd      %st, %st(4)
        fxch      %st(3)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      156+_Q@GOTOFF(%ecx)
        fmul      %st(5), %st
        faddp     %st, %st(6)
        fxch      %st(4)
        fmul      %st, %st(5)
        fldt      108+_Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      84+_Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      60+_Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      36+_Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      12+_Q@GOTOFF(%ecx)
        faddp     %st, %st(6)
        fmul      %st, %st(5)
        fldt      144+_Q@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      120+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      96+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      72+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      48+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      _Q@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fldt      180+_Q@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fmulp     %st, %st(1)
        fldt      168+_Q@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fldt      16(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      _TWO_52H@GOTOFF(%ecx)
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(5)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st, %st(4)
        fld       %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fadd      %st(2), %st
        fsqrt     
        fmul      %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fsubp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(1), %st
        fsubrp    %st, %st(4)
        fxch      %st(5)
        faddp     %st, %st(3)
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(0)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(4)
        fadd      %st(3), %st
        fmulp     %st, %st(4)
        fld       %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.20
..B1.19:
        fldcw     14(%esp)
..B1.20:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        movzwl    14(%esp), %esi
        movl      %esi, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.43
..B1.22:
        orl       $-64768, %esi
        movw      %si, 12(%esp)
..B1.23:
        fldcw     12(%esp)
..B1.24:
        movzwl    16(%ebp), %eax
        movl      %eax, %ebx
        movl      $1, %edx
        andl      $32767, %ebx
..B1.25:
        cmpl      $16417, %ebx
        jge       ..B1.34
..B1.26:
        fldt      8(%ebp)
        cmpl      $16388, %ebx
        jge       ..B1.28
..B1.27:
        fldl      _TWO_52H@GOTOFF(%ecx)
        lea       8+_ones@GOTOFF(%ecx), %eax
        fld       %st(0)
        fld       %st(2)
        fadd      %st(2), %st
        fsub      %st(2), %st
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        faddl     (%eax)
        fld       %st(0)
        fadd      %st(2), %st
        fsqrt     
        fadd      %st, %st(4)
        fxch      %st(5)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(1)
        fld       %st(3)
        fxch      %st(4)
        fsubr     %st, %st(5)
        fxch      %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(4), %st
        fdivrp    %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      -8(%eax)
        jmp       ..B1.33
..B1.28:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        lea       _ones@GOTOFF(%ecx), %eax
        fmul      %st(1), %st
        fld       %st(0)
        fldl      _TWO_32H@GOTOFF(%ecx)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubr     %st(1), %st
        fldl      (%eax)
        addl      $8, %eax
        cmpl      $16394, %ebx
        fldl      (%eax)
        jge       ..B1.30
..B1.29:
        fstp      %st(3)
        fld       %st(3)
        fadd      %st(3), %st
        fld       %st(4)
        fadd      %st(2), %st
        fmulp     %st, %st(1)
        fsqrt     
        faddp     %st, %st(4)
        fxch      %st(3)
        fdivrp    %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.33
..B1.30:
        fstp      %st(4)
        cmpl      $16401, %ebx
        jge       ..B1.32
..B1.31:
        fld       %st(3)
        fdiv      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.33
..B1.32:
        fxch      %st(2)
        fdivrp    %st, %st(3)
        faddp     %st, %st(2)
..B1.33:
        fldt      16(%esp)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     8(%ebp)
        movzwl    16(%ebp), %ebx
        andl      $32767, %ebx
        movl      %ebx, %esi
        addl      $-16383, %ebx
        negl      %esi
        fxch      %st(1)
        fstpt     (%esp)
        addl      $-2, %esi
        movzwl    8(%esp), %edi
        andl      $32767, %esi
        andl      $-32768, %edi
        orl       %esi, %edi
        movw      %di, 8(%esp)
        fldt      (%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     16(%esp)
        fmulp     %st, %st(1)
        jmp       ..B1.35
..B1.34:
        fldl      _TWO_52H@GOTOFF(%ecx)
        andl      $-32768, %eax
        orl       $-49153, %eax
        addl      $-16382, %ebx
        movw      %ax, 16(%ebp)
        lea       8+_ones@GOTOFF(%ecx), %eax
        fldt      8(%ebp)
        fld       %st(0)
        fadd      %st(2), %st
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(1)
..B1.35:
        fldt      16(%esp)
        fldt      60+_P@GOTOFF(%ecx)
        fldt      36+_P@GOTOFF(%ecx)
        fldt      12+_P@GOTOFF(%ecx)
        movl      12(%ebp), %esi
        shrl      $23, %esi
        andl      $255, %esi
        movl      %ebx, (%esp)
        fildl     (%esp)
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%esi,4)
        fmul      %st, %st(5)
        fmulp     %st, %st(6)
        fxch      %st(4)
        faddl     (%eax)
        fld       %st(0)
        shll      $4, %esi
        fadd      %st(6), %st
        fld       %st(0)
        testl     %edx, %edx
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fstpt     8(%ebp)
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fmulp     %st, %st(3)
        faddl     8+__libm_logl_table_256@GOTOFF(%ecx,%esi)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddl     __libm_logl_table_256@GOTOFF(%esi,%ecx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        je        ..B1.37
..B1.36:
        fldcw     14(%esp)
..B1.37:
        fldt      16(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        xorl      %eax, %eax
        jmp       ..B1.11
..B1.39:
        cmpl      $-2147483648, %edi
        jne       ..B1.7
..B1.40:
        cmpl      $0, 8(%ebp)
        jne       ..B1.7
..B1.41:
        fldl      _zeros@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.42:
        xorl      %eax, %eax
        jmp       ..B1.18
..B1.43:
        xorl      %edx, %edx
        jmp       ..B1.25
..B1.44:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.57
..B1.45:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.46:
        fldcw     12(%esp)
..B1.47:
        movzwl    16(%ebp), %ebx
        movl      $1, %edx
        andl      $32767, %ebx
..B1.48:
        cmpl      $32767, %ebx
        jne       ..B1.53
..B1.49:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.52
..B1.50:
        cmpl      $0, 8(%ebp)
        jne       ..B1.52
..B1.51:
        movzbl    17(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.53
..B1.52:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.54
..B1.53:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.54:
        testl     %edx, %edx
        je        ..B1.56
..B1.55:
        fldcw     14(%esp)
..B1.56:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        xorl      %edx, %edx
        jmp       ..B1.48
        .align    16,0x90
	.type	acoshl,@function
	.size	acoshl,.-acoshl
	.data
# -- End  acoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
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
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_Q1:
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49147
	.word	0
	.word	37372
	.word	39321
	.word	39321
	.word	39321
	.word	16377
	.word	0
	.word	18586
	.word	55971
	.word	28086
	.word	46811
	.word	49143
	.word	0
	.word	15813
	.word	59837
	.word	36396
	.word	63715
	.word	16373
	.word	0
	.word	53727
	.word	45078
	.word	40330
	.word	46917
	.word	49140
	.word	0
	.word	48396
	.word	8155
	.word	33124
	.word	36271
	.word	16371
	.word	0
	.type	_Q1,@object
	.size	_Q1,72
	.align 2
_Q:
	.word	48098
	.word	43690
	.word	43690
	.word	43690
	.word	16366
	.word	0
	.word	20923
	.word	52462
	.word	52428
	.word	52428
	.word	49132
	.word	0
	.word	38527
	.word	56172
	.word	28086
	.word	46811
	.word	49143
	.word	0
	.word	47050
	.word	58060
	.word	36408
	.word	63715
	.word	16373
	.word	0
	.word	28121
	.word	12720
	.word	53620
	.word	46917
	.word	49140
	.word	0
	.word	5738
	.word	23392
	.word	25199
	.word	36391
	.word	16371
	.word	0
	.word	9822
	.word	24561
	.word	52037
	.word	58572
	.word	49137
	.word	0
	.word	32045
	.word	31633
	.word	41878
	.word	48451
	.word	16368
	.word	0
	.word	16262
	.word	24754
	.word	13643
	.word	40941
	.word	49135
	.word	0
	.word	36557
	.word	45689
	.word	21168
	.word	35169
	.word	16366
	.word	0
	.word	29793
	.word	40103
	.word	23573
	.word	60969
	.word	49132
	.word	0
	.word	51189
	.word	17117
	.word	14297
	.word	51855
	.word	16363
	.word	0
	.word	6461
	.word	12463
	.word	55551
	.word	38957
	.word	49130
	.word	0
	.word	34939
	.word	19802
	.word	20012
	.word	37205
	.word	16360
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43696
	.word	49147
	.word	0
	.word	0
	.word	0
	.word	0
	.word	39328
	.word	16377
	.word	0
	.type	_Q,@object
	.size	_Q,192
	.align 2
_P:
	.word	65
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	44010
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	58670
	.word	61414
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	24905
	.word	46201
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	57053
	.word	27587
	.word	64478
	.word	43690
	.word	49148
	.word	0
	.word	41508
	.word	48914
	.word	32459
	.word	37449
	.word	16380
	.word	0
	.type	_P,@object
	.size	_P,72
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.section .note.GNU-stack, ""
# End
