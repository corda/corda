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
	.file "libm_hypot2l_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_hypot2l_k80
	.text
       .align    16,0x90
	.hidden __libm_hypot2l_k80
	.globl __libm_hypot2l_k80
__libm_hypot2l_k80:
# parameter 1: 64 + %esp
# parameter 2: 68 + %esp
# parameter 3: 80 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $48, %esp
        movzwl    76(%esp), %eax
        testl     $32767, %eax
        movl      64(%esp), %ecx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jne       ..B1.8
..B1.2:
        cmpl      $0, 72(%esp)
        jne       ..B1.8
..B1.3:
        cmpl      $0, 68(%esp)
        jne       ..B1.8
..B1.4:
        movzwl    88(%esp), %eax
        testl     $32767, %eax
        jne       ..B1.8
..B1.5:
        cmpl      $0, 84(%esp)
        jne       ..B1.8
..B1.6:
        cmpl      $0, 80(%esp)
        jne       ..B1.8
..B1.7:
        fldt      68(%esp)
        fstpt     (%ecx)
        fldt      80(%esp)
        fstpt     12(%ecx)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        addl      $48, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.8:
        fldt      68(%esp)
        fabs      
        fstpt     (%esp)
        fldt      (%esp)
        fldt      80(%esp)
        fabs      
        fstpt     12(%esp)
        fldt      12(%esp)
        movzwl    8(%esp), %edx
        movzwl    20(%esp), %eax
        andl      $32767, %edx
        andl      $32767, %eax
        cmpl      %eax, %edx
        jg        ..B1.13
..B1.9:
        jne       ..B1.14
..B1.10:
        movl      4(%esp), %esi
        movl      16(%esp), %edi
        cmpl      %edi, %esi
        ja        ..B1.65
..B1.11:
        jne       ..B1.64
..B1.12:
        movl      (%esp), %esi
        cmpl      12(%esp), %esi
        jbe       ..B1.64
..B1.65:
        cmpl      %eax, %edx
..B1.13:
        fxch      %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fld       %st(0)
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jl        ..B1.20
        jmp       ..B1.16
..B1.14:
        fstpt     24(%esp)
        fldt      24(%esp)
        fld       %st(0)
..B1.15:
        jl        ..B1.20
..B1.16:
        jne       ..B1.21
..B1.17:
        movl      4(%esp), %eax
        movl      16(%esp), %edx
        cmpl      %edx, %eax
        jb        ..B1.20
..B1.18:
        jne       ..B1.21
..B1.19:
        movl      (%esp), %eax
        cmpl      12(%esp), %eax
        jae       ..B1.21
..B1.20:
        fstp      %st(1)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        jmp       ..B1.22
..B1.21:
        fstp      %st(2)
        fstpt     12(%esp)
        fldt      12(%esp)
..B1.22:
        movswl    20(%esp), %eax
        testl     %eax, %eax
        je        ..B1.24
..B1.23:
        addl      $-16383, %eax
        jmp       ..B1.29
..B1.24:
        movzwl    20(%esp), %eax
        testl     $32767, %eax
        jne       ..B1.28
..B1.25:
        cmpl      $0, 16(%esp)
        jne       ..B1.28
..B1.26:
        cmpl      $0, 12(%esp)
        jne       ..B1.28
..B1.27:
        movl      $-16526, %eax
        jmp       ..B1.29
..B1.28:
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        movswl    8(%esp), %eax
        addl      $-32828, %eax
..B1.29:
        movswl    32(%esp), %edx
        testl     %edx, %edx
        je        ..B1.31
..B1.30:
        addl      $-16383, %edx
        jmp       ..B1.32
..B1.31:
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        movswl    8(%esp), %edx
        addl      $-32828, %edx
..B1.32:
        addl      $-67, %edx
        cmpl      %edx, %eax
        jg        ..B1.34
..B1.33:
        fstp      %st(0)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
..B1.34:
        movzwl    32(%esp), %eax
        andl      $32767, %eax
        cmpl      $24709, %eax
        jg        ..B1.39
..B1.35:
        jne       ..B1.40
..B1.36:
        movl      28(%esp), %edx
        cmpl      4+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.39
..B1.37:
        jne       ..B1.40
..B1.38:
        movl      24(%esp), %edx
        cmpl      _CONSTANTS@GOTOFF(%ebx), %edx
        jb        ..B1.40
..B1.39:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     24(%esp)
        fldt      24(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        jmp       ..B1.61
..B1.40:
        cmpl      $24573, %eax
        jg        ..B1.45
..B1.41:
        jne       ..B1.46
..B1.42:
        movl      28(%esp), %edx
        cmpl      16+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.45
..B1.43:
        jne       ..B1.46
..B1.44:
        movl      24(%esp), %edx
        cmpl      12+_CONSTANTS@GOTOFF(%ebx), %edx
        jb        ..B1.46
..B1.45:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     24(%esp)
        fldt      24(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      .L_2il0floatpacket.4@GOTOFF(%ebx)
        jmp       ..B1.61
..B1.46:
        cmpl      $8127, %eax
        jl        ..B1.51
..B1.47:
        jne       ..B1.52
..B1.48:
        movl      28(%esp), %edx
        cmpl      28+_CONSTANTS@GOTOFF(%ebx), %edx
        jb        ..B1.51
..B1.49:
        jne       ..B1.52
..B1.50:
        movl      24(%esp), %edx
        cmpl      24+_CONSTANTS@GOTOFF(%ebx), %edx
        ja        ..B1.52
..B1.51:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     24(%esp)
        fldt      24(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      .L_2il0floatpacket.5@GOTOFF(%ebx)
        jmp       ..B1.61
..B1.52:
        cmpl      $8323, %eax
        jl        ..B1.57
..B1.53:
        jne       ..B1.60
..B1.54:
        movl      28(%esp), %eax
        cmpl      40+_CONSTANTS@GOTOFF(%ebx), %eax
        jb        ..B1.57
..B1.55:
        jne       ..B1.60
..B1.56:
        movl      24(%esp), %eax
        cmpl      36+_CONSTANTS@GOTOFF(%ebx), %eax
        ja        ..B1.60
..B1.57:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     24(%esp)
        fldt      24(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      .L_2il0floatpacket.6@GOTOFF(%ebx)
        jmp       ..B1.61
..B1.60:
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
..B1.61:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fld       %st(3)
        fmul      %st(1), %st
        fld       %st(4)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(4)
        fxch      %st(2)
        fsubr     %st, %st(6)
        fxch      %st(3)
        fmul      %st(5), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(5)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        fmul      %st(7), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(1)
        fadd      %st(4), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fxch      %st(6)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fadd      %st(7), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmul      %st(0), %st
        fxch      %st(2)
        fsubp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%ecx)
        addl      $48, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.64:
        cmpl      %eax, %edx
        jmp       ..B1.14
        .align    16,0x90
	.type	__libm_hypot2l_k80,@function
	.size	__libm_hypot2l_k80,.-__libm_hypot2l_k80
	.data
# -- End  __libm_hypot2l_k80
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
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0e,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0d,0x40,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0e,0xc0,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0d,0xc0,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,12
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24709
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	24573
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	8127
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8323
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24576
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8190
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16445
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,96
	.data
	.section .note.GNU-stack, ""
# End
