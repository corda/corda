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
	.file "libm_scalbl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_scalbl_k80
	.text
       .align    16,0x90
	.globl __libm_scalbl_k80
__libm_scalbl_k80:
# parameter 1: 8 + %rsp
# parameter 2: 24 + %rsp
# parameter 3: %edi
..B1.1:
	.cfi_startproc
..___tag_value___libm_scalbl_k80.1:
..L2:

        fldt      24(%rsp)
        fldt      8(%rsp)
        fld       %st(0)
        cmpq      $0, 8(%rsp)
        fadd      %st(2), %st
        jne       ..B1.3
..B1.2:
        fstp      %st(2)
        fstp      %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        ret       
..B1.3:
        movzwl    16(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.2
..B1.4:
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        movzwl    -16(%rsp), %ecx
        movl      %ecx, %eax
        andl      $32767, %eax
        lea       -1(%rdi,%rax), %edx
        cmpl      $32765, %edx
        lea       (%rdi,%rax), %esi
        ja        ..B1.6
..B1.5:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        andl      $-32768, %ecx
        andl      $32767, %esi
        orl       %esi, %ecx
        movw      %cx, -16(%rsp)
        fldt      -24(%rsp)
        ret       
..B1.6:
        cmpl      $32766, %esi
        jle       ..B1.8
..B1.7:
        fstp      %st(2)
        fstp      %st(1)
        lea       _CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fmulp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        ret       
..B1.8:
        lea       16+_CONSTANTS(%rip), %rax
        cmpl      $-63, %esi
        fldt      (%rax)
        jge       ..B1.10
..B1.9:
        fstp      %st(3)
        fstp      %st(0)
        fxch      %st(1)
        fmul      %st(0), %st
        fmulp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        ret       
..B1.10:
        cmpl      $-19, %esi
        jge       ..B1.12
..B1.11:
        fstp      %st(2)
        fstp      %st(2)
        fldt      .L_2il0floatpacket.0(%rip)
        movzwl    8+.L_2il0floatpacket.0(%rip), %eax
        andl      $-32768, %eax
        fstpt     -56(%rsp)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fstpt     -24(%rsp)
        movzwl    -16(%rsp), %edx
        lea       256(%rdi,%rdx), %edi
        andl      $-32768, %edx
        andl      $32767, %edi
        orl       $-49409, %edx
        orl       %edi, %eax
        movw      %ax, -48(%rsp)
        fldt      -56(%rsp)
        movw      %dx, -16(%rsp)
        fldt      -24(%rsp)
        fmulp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
        ret       
..B1.12:
        fstp      %st(1)
        fmul      %st(0), %st
        lea       32+_CONSTANTS(%rip), %rcx
        movzwl    8+.L_2il0floatpacket.0(%rip), %r8d
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.1(%rip)
        andl      $-32768, %r8d
        fmul      %st(1), %st
        fld       %st(0)
        fsub      %st(2), %st
        fsubrp    %st, %st(1)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fsubrp    %st, %st(1)
        movzwl    -32(%rsp), %esi
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        lea       -16383(%rsi), %eax
        negl      %eax
        movl      %esi, %edx
        addl      $16383, %eax
        lea       -32512(%rdi,%rsi), %edi
        andl      $32767, %eax
        andl      $-32768, %edx
        fstpt     -56(%rsp)
        orl       %eax, %r8d
        fldt      (%rcx)
        andl      $32767, %edi
        fmulp     %st, %st(1)
        orl       $-49409, %edx
        movw      %r8w, -48(%rsp)
        andl      $-32768, %r8d
        fldt      -56(%rsp)
        orl       %edi, %r8d
        fmulp     %st, %st(1)
        movw      %dx, -32(%rsp)
        fldt      -40(%rsp)
        movw      %r8w, -48(%rsp)
        fldt      -56(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     -40(%rsp)
        fldt      -40(%rsp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     -24(%rsp)
        fldt      -24(%rsp)
..B1.13:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_scalbl_k80,@function
	.size	__libm_scalbl_k80,.-__libm_scalbl_k80
	.data
# -- End  __libm_scalbl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 2
_CONSTANTS:
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
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16127
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,48
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
