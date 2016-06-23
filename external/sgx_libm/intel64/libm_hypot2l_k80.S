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
	.globl __libm_hypot2l_k80
__libm_hypot2l_k80:
# parameter 1: %rdi
# parameter 2: 8 + %rsp
# parameter 3: 24 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value___libm_hypot2l_k80.1:
..L2:

        movzwl    16(%rsp), %eax
        testl     $32767, %eax
        jne       ..B1.6
..B1.2:
        cmpq      $0, 8(%rsp)
        jne       ..B1.6
..B1.3:
        movzwl    32(%rsp), %eax
        testl     $32767, %eax
        jne       ..B1.6
..B1.4:
        cmpq      $0, 24(%rsp)
        jne       ..B1.6
..B1.5:
        fldt      8(%rsp)
        fstpt     (%rdi)
        fldt      24(%rsp)
        fstpt     16(%rdi)
        fldt      .L_2il0floatpacket.0(%rip)
        ret       
..B1.6:
        fldt      8(%rsp)
        fabs      
        fstpt     -56(%rsp)
        fldt      -56(%rsp)
        fldt      24(%rsp)
        fabs      
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        movzwl    -48(%rsp), %ecx
        movzwl    -16(%rsp), %esi
        andl      $32767, %ecx
        andl      $32767, %esi
        cmpl      %esi, %ecx
        jg        ..B1.11
..B1.7:
        jne       ..B1.12
..B1.8:
        movl      -52(%rsp), %eax
        movl      -20(%rsp), %edx
        cmpl      %edx, %eax
        ja        ..B1.57
..B1.9:
        jne       ..B1.56
..B1.10:
        movl      -56(%rsp), %eax
        cmpl      -24(%rsp), %eax
        jbe       ..B1.56
..B1.57:
        cmpl      %esi, %ecx
..B1.11:
        fxch      %st(1)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fld       %st(0)
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
        jl        ..B1.18
        jmp       ..B1.14
..B1.12:
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fld       %st(0)
..B1.13:
        jl        ..B1.18
..B1.14:
        jne       ..B1.19
..B1.15:
        movl      -52(%rsp), %eax
        movl      -20(%rsp), %edx
        cmpl      %edx, %eax
        jb        ..B1.18
..B1.16:
        jne       ..B1.19
..B1.17:
        movl      -56(%rsp), %eax
        cmpl      -24(%rsp), %eax
        jae       ..B1.19
..B1.18:
        fstp      %st(1)
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        jmp       ..B1.20
..B1.19:
        fstp      %st(2)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
..B1.20:
        movswl    -16(%rsp), %ecx
        testl     %ecx, %ecx
        je        ..B1.22
..B1.21:
        addl      $-16383, %ecx
        jmp       ..B1.26
..B1.22:
        movzwl    -16(%rsp), %eax
        testl     $32767, %eax
        jne       ..B1.25
..B1.23:
        cmpq      $0, -24(%rsp)
        jne       ..B1.25
..B1.24:
        movl      $-16526, %ecx
        jmp       ..B1.26
..B1.25:
        lea       96+_CONSTANTS(%rip), %rax
        lea       112+_CONSTANTS(%rip), %rdx
        fldt      (%rax)
        fmul      %st(1), %st
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     -56(%rsp)
        movswl    -48(%rsp), %ecx
        addl      $-32828, %ecx
..B1.26:
        movswl    -32(%rsp), %eax
        testl     %eax, %eax
        je        ..B1.28
..B1.27:
        addl      $-16383, %eax
        jmp       ..B1.29
..B1.28:
        lea       96+_CONSTANTS(%rip), %rax
        lea       112+_CONSTANTS(%rip), %rdx
        fldt      (%rax)
        fmul      %st(2), %st
        fldt      (%rdx)
        fmulp     %st, %st(1)
        fstpt     -56(%rsp)
        movswl    -48(%rsp), %eax
        addl      $-32828, %eax
..B1.29:
        fldt      .L_2il0floatpacket.0(%rip)
        addl      $-67, %eax
        cmpl      %eax, %ecx
        movzwl    -32(%rsp), %eax
        jg        ..L3
        fst       %st(1)
..L3:
        andl      $32767, %eax
        cmpl      $24709, %eax
        jg        ..B1.34
..B1.30:
        jne       ..B1.35
..B1.31:
        lea       _CONSTANTS(%rip), %rcx
        movl      -36(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.34
..B1.32:
        jne       ..B1.35
..B1.33:
        movl      -40(%rsp), %edx
        cmpl      (%rcx), %edx
        jb        ..B1.35
..B1.34:
        fstp      %st(0)
        lea       80+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(2)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      .L_2il0floatpacket.1(%rip)
        jmp       ..B1.53
..B1.35:
        cmpl      $24573, %eax
        jg        ..B1.40
..B1.36:
        jne       ..B1.41
..B1.37:
        lea       16+_CONSTANTS(%rip), %rcx
        movl      -36(%rsp), %edx
        cmpl      4(%rcx), %edx
        ja        ..B1.40
..B1.38:
        jne       ..B1.41
..B1.39:
        movl      -40(%rsp), %edx
        cmpl      (%rcx), %edx
        jb        ..B1.41
..B1.40:
        fstp      %st(0)
        lea       80+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      .L_2il0floatpacket.4(%rip)
        jmp       ..B1.53
..B1.41:
        cmpl      $8127, %eax
        jl        ..B1.46
..B1.42:
        jne       ..B1.47
..B1.43:
        lea       32+_CONSTANTS(%rip), %rcx
        movl      -36(%rsp), %edx
        cmpl      4(%rcx), %edx
        jb        ..B1.46
..B1.44:
        jne       ..B1.47
..B1.45:
        movl      -40(%rsp), %edx
        cmpl      (%rcx), %edx
        ja        ..B1.47
..B1.46:
        fstp      %st(0)
        lea       64+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(2)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      .L_2il0floatpacket.5(%rip)
        jmp       ..B1.53
..B1.47:
        cmpl      $8323, %eax
        jl        ..B1.52
..B1.48:
        jne       ..B1.53
..B1.49:
        lea       48+_CONSTANTS(%rip), %rdx
        movl      -36(%rsp), %eax
        cmpl      4(%rdx), %eax
        jb        ..B1.52
..B1.50:
        jne       ..B1.53
..B1.51:
        movl      -40(%rsp), %eax
        cmpl      (%rdx), %eax
        ja        ..B1.53
..B1.52:
        fstp      %st(0)
        lea       64+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        fldt      .L_2il0floatpacket.6(%rip)
..B1.53:
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(3), %st
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(3)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fldt      .L_2il0floatpacket.2(%rip)
        fmul      %st(5), %st
        fsub      %st, %st(3)
        fsubp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(5)
        fldt      .L_2il0floatpacket.3(%rip)
        fmul      %st, %st(2)
        fxch      %st(2)
        fmul      %st(7), %st
        fxch      %st(2)
        fmulp     %st, %st(1)
        fmul      %st(5), %st
        fld       %st(2)
        fadd      %st(2), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(6)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(3), %st
        fld       %st(0)
        fadd      %st(7), %st
        fstpt     (%rdi)
        fldt      (%rdi)
        fxch      %st(1)
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmul      %st(0), %st
        fxch      %st(4)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rdi)
        ret       
..B1.56:
        cmpl      %esi, %ecx
        jmp       ..B1.12
        .align    16,0x90
	.cfi_endproc
	.type	__libm_hypot2l_k80,@function
	.size	__libm_hypot2l_k80,.-__libm_hypot2l_k80
	.data
# -- End  __libm_hypot2l_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0e,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,16
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0d,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,16
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0e,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,16
	.align 16
.L_2il0floatpacket.6:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x04,0x80,0x0d,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24709
	.word	0
	.word	0
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	24573
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	8323
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	8190
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	32768
	.word	16445
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,128
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
