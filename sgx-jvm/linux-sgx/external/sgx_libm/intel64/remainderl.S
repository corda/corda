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
	.file "remainderl.c"
	.text
..TXTST0:
# -- Begin  remainderl
	.text
       .align    16,0x90
	.globl remainderl
remainderl:
# parameter 1: 80 + %rsp
# parameter 2: 96 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_remainderl.1:
..L2:

        subq      $72, %rsp
	.cfi_def_cfa_offset 80
        xorb      %dl, %dl
        fldt      80(%rsp)
        fstpt     32(%rsp)
        fldt      96(%rsp)
        fstpt     48(%rsp)
..B1.2:
        fnstcw    66(%rsp)
..B1.3:
        movzwl    104(%rsp), %ecx
        andl      $32767, %ecx
        jne       ..B1.6
..B1.4:
        cmpl      $0, 100(%rsp)
        jne       ..B1.6
..B1.5:
        cmpl      $0, 96(%rsp)
        je        ..B1.42
..B1.6:
        movzwl    88(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        je        ..B1.40
..B1.7:
        cmpl      $32767, %ecx
        je        ..B1.17
..B1.8:
        movzwl    66(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        orl       $-64768, %ecx
        movw      %cx, 64(%rsp)
..B1.10:
        fldcw     64(%rsp)
..B1.11:
        movb      $1, %dl
..B1.12:
        fldt      48(%rsp)
        fldt      32(%rsp)
        movq      $1024, %rcx
.L_2TAG_PACKET_0.0.2:
        fprem1    
        fstsw     %ax
        andq      %rcx, %rax
        cmpq      $1024, %rax
        je        .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        fstpt     16(%rsp)
..B1.13:
        testb     %dl, %dl
        je        ..B1.15
..B1.14:
        fldcw     66(%rsp)
..B1.15:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.17:
        movq      $0x8000000000000000, %rax
        cmpq      96(%rsp), %rax
        je        ..B1.25
..B1.18:
        movzwl    66(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.24
..B1.19:
        orl       $-64768, %edx
        movw      %dx, 64(%rsp)
..B1.20:
        fldcw     64(%rsp)
..B1.21:
        fldt      80(%rsp)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.22:
        fldcw     66(%rsp)
..B1.23:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.24:
        fldt      80(%rsp)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.23
..B1.25:
        cmpl      $32767, %esi
        je        ..B1.31
..B1.26:
        testl     %esi, %esi
        jne       ..B1.30
..B1.27:
        cmpl      $0, 84(%rsp)
        jne       ..B1.29
..B1.28:
        cmpl      $0, 80(%rsp)
        je        ..B1.30
..B1.29:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 8(%rsp)
..B1.30:
        fldt      80(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.31:
        cmpl      $-2147483648, 84(%rsp)
        jne       ..B1.30
..B1.32:
        cmpl      $0, 80(%rsp)
        jne       ..B1.30
..B1.33:
        movzwl    66(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.37
..B1.34:
        orl       $-64768, %ecx
        movw      %cx, 64(%rsp)
..B1.35:
        fldcw     64(%rsp)
..B1.36:
        movb      $1, %dl
..B1.37:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        testb     %dl, %dl
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     16(%rsp)
        je        ..B1.39
..B1.38:
        fldcw     66(%rsp)
..B1.39:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.40:
        movq      $0x8000000000000000, %rax
        cmpq      80(%rsp), %rax
        jne       ..B1.18
..B1.41:
        cmpl      $32767, %ecx
        je        ..B1.17
        jmp       ..B1.25
..B1.42:
        movzwl    66(%rsp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.46
..B1.43:
        orl       $-64768, %ecx
        movw      %cx, 64(%rsp)
..B1.44:
        fldcw     64(%rsp)
..B1.45:
        movb      $1, %dl
..B1.46:
        movzwl    88(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.51
..B1.47:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rcx
        movsd     (%rax), %xmm0
        mulsd     (%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     16(%rsp)
..B1.48:
        testb     %dl, %dl
        je        ..B1.50
..B1.49:
        fldcw     66(%rsp)
..B1.50:
        fldt      16(%rsp)
        addq      $72, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
..B1.51:
        movq      $0x8000000000000000, %rax
        cmpq      80(%rsp), %rax
        je        ..B1.47
..B1.52:
        fldt      80(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
        jmp       ..B1.48
        .align    16,0x90
	.cfi_endproc
	.type	remainderl,@function
	.size	remainderl,.-remainderl
	.data
# -- End  remainderl
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End

	.globl dreml
	.equ dreml, remainderl

