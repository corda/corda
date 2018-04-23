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
	.file "fmaxl.c"
	.text
..TXTST0:
# -- Begin  fmaxl
	.text
       .align    16,0x90
	.globl fmaxl
fmaxl:
# parameter 1: 48 + %rsp
# parameter 2: 64 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_fmaxl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %sil, %sil
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    56(%rsp), %ecx
        movl      %ecx, %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.34
..B1.4:
        movzwl    72(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.17
..B1.5:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.9
..B1.6:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.7:
        fldcw     32(%rsp)
..B1.8:
        movb      $1, %sil
..B1.9:
        fldt      48(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fldt      64(%rsp)
        fxch      %st(1)
        fcomip    %st(1), %st
        jbe       ..B1.11
..B1.10:
        fstp      %st(0)
        jmp       ..B1.13
..B1.11:
        fldt      16(%rsp)
        fucomip   %st(1), %st
        jp        ..B1.12
        je        ..B1.16
..B1.12:
        fstpt     16(%rsp)
..B1.13:
        testb     %sil, %sil
        je        ..B1.15
..B1.14:
        fldcw     34(%rsp)
..B1.15:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.16:
        testb     $-128, 73(%rsp)
        jne       ..B1.10
        jmp       ..B1.12
..B1.17:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.19
..B1.18:
        cmpl      $0, 64(%rsp)
        je        ..B1.5
..B1.19:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.33
..B1.20:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.21:
        fldcw     32(%rsp)
..B1.22:
        fldt      48(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.23:
        fldcw     34(%rsp)
        movzwl    56(%rsp), %ecx
..B1.24:
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jne       ..B1.27
..B1.25:
        cmpl      $-2147483648, 52(%rsp)
        jne       ..B1.28
..B1.26:
        cmpl      $0, 48(%rsp)
        jne       ..B1.28
..B1.27:
        fldt      48(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.28:
        movzwl    72(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.31
..B1.29:
        cmpl      $-2147483648, 68(%rsp)
        jne       ..B1.32
..B1.30:
        cmpl      $0, 64(%rsp)
        jne       ..B1.32
..B1.31:
        fldt      64(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.32:
        fldt      (%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.33:
        fldt      48(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.24
..B1.34:
        cmpl      $-2147483648, 52(%rsp)
        jne       ..B1.19
..B1.35:
        cmpl      $0, 48(%rsp)
        je        ..B1.4
        jmp       ..B1.19
        .align    16,0x90
	.cfi_endproc
	.type	fmaxl,@function
	.size	fmaxl,.-fmaxl
	.data
# -- End  fmaxl
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
