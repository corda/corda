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
	.file "fdiml.c"
	.text
..TXTST0:
# -- Begin  fdiml
	.text
       .align    16,0x90
	.globl fdiml
fdiml:
# parameter 1: 32 + %rsp
# parameter 2: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_fdiml.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        xorb      %al, %al
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movzwl    40(%rsp), %edi
        andl      $32767, %edi
        movzwl    56(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %edi
        je        ..B1.38
..B1.4:
        cmpl      $32767, %esi
        je        ..B1.29
..B1.5:
        movzwl    18(%rsp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.9
..B1.6:
        orl       $-64768, %ecx
        movw      %cx, 16(%rsp)
..B1.7:
        fldcw     16(%rsp)
..B1.8:
        movb      $1, %al
..B1.9:
        fldt      48(%rsp)
        lea       _zeros(%rip), %rdx
        fldl      (%rdx)
        fstpt     (%rsp)
        fldt      32(%rsp)
        fcomi     %st(1), %st
        jbe       ..B1.42
..B1.10:
        cmpl      $32767, %edi
        je        ..B1.14
..B1.11:
        cmpl      $32767, %esi
        je        ..B1.14
..B1.12:
        cmpl      $32766, %edi
        je        ..B1.26
..B1.13:
        cmpl      $32766, %esi
        je        ..B1.18
..B1.14:
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.42:
        fstp      %st(0)
        fstp      %st(0)
..B1.15:
        testb     %al, %al
        je        ..B1.17
..B1.16:
        fldcw     18(%rsp)
..B1.17:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.18:
        testl     %edi, %edi
        jne       ..B1.22
..B1.19:
        cmpl      $0, 36(%rsp)
        jne       ..B1.21
..B1.20:
        cmpl      $0, 32(%rsp)
        je        ..B1.22
..B1.21:
        lea       _ranges(%rip), %rdx
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rdx)
        fucomip   %st(1), %st
        fstp      %st(0)
        jne       ..B1.15
        jp        ..B1.15
        jmp       ..B1.24
..B1.22:
        fldt      .L_2il0floatpacket.0(%rip)
        lea       16+_ranges(%rip), %rdx
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fldt      (%rdx)
        fcomip    %st(1), %st
        jp        ..B1.23
        jbe       ..B1.25
..B1.23:
        fldt      .L_2il0floatpacket.1(%rip)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.24:
        lea       _large_value_80(%rip), %rdx
        fldt      (%rdx)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.25:
        fstp      %st(0)
        lea       _large_value_80(%rip), %rdx
        fldt      (%rdx)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.15
..B1.26:
        testl     %esi, %esi
        jne       ..B1.22
..B1.27:
        cmpl      $0, 52(%rsp)
        jne       ..B1.21
..B1.28:
        cmpl      $0, 48(%rsp)
        jne       ..B1.21
        jmp       ..B1.22
..B1.29:
        cmpl      $-2147483648, 52(%rsp)
        jne       ..B1.31
..B1.30:
        cmpl      $0, 48(%rsp)
        je        ..B1.5
..B1.31:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.37
..B1.32:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.33:
        fldcw     16(%rsp)
..B1.34:
        fldt      32(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.35:
        fldcw     18(%rsp)
..B1.36:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.37:
        fldt      32(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.36
..B1.38:
        cmpl      $-2147483648, 36(%rsp)
        jne       ..B1.31
..B1.39:
        cmpl      $0, 32(%rsp)
        je        ..B1.4
        jmp       ..B1.31
        .align    16,0x90
	.cfi_endproc
	.type	fdiml,@function
	.size	fdiml,.-fdiml
	.data
# -- End  fdiml
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
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
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
