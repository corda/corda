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
	.file "logbl.c"
	.text
..TXTST0:
# -- Begin  logbl
	.text
       .align    16,0x90
	.globl logbl
logbl:
# parameter 1: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_logbl.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        xorb      %al, %al
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    26(%rsp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.7
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 24(%rsp)
..B1.5:
        fldcw     24(%rsp)
..B1.6:
        movb      $1, %al
..B1.7:
        movzwl    56(%rsp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.16
..B1.8:
        testl     %edx, %edx
        jne       ..B1.11
..B1.9:
        cmpq      $0, 48(%rsp)
        je        ..B1.15
..B1.10:
        fldt      48(%rsp)
        lea       _TWO_75(%rip), %rdx
        fmull     (%rdx)
        fstpt     48(%rsp)
        movzwl    56(%rsp), %ecx
        andl      $32767, %ecx
        addl      $-16458, %ecx
        movl      %ecx, (%rsp)
        fildl     (%rsp)
        fstpt     8(%rsp)
        jmp       ..B1.12
..B1.11:
        addl      $-16383, %edx
        movl      %edx, (%rsp)
        fildl     (%rsp)
        fstpt     8(%rsp)
..B1.12:
        testb     %al, %al
        je        ..B1.14
..B1.13:
        fldcw     26(%rsp)
..B1.14:
        fldt      8(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
..B1.15:
        lea       8+_ones(%rip), %rdx
        lea       _zeros(%rip), %rcx
        movsd     (%rdx), %xmm0
        divsd     (%rcx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     8(%rsp)
        jmp       ..B1.12
..B1.16:
        fldt      48(%rsp)
        lea       _ones(%rip), %rcx
        movb      57(%rsp), %dl
        andb      $-128, %dl
        shrb      $7, %dl
        movzbl    %dl, %esi
        fmull     (%rcx,%rsi,8)
        fstpt     8(%rsp)
        jmp       ..B1.12
        .align    16,0x90
	.cfi_endproc
	.type	logbl,@function
	.size	logbl,.-logbl
	.data
# -- End  logbl
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
