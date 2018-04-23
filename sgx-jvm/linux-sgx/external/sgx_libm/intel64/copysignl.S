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
	.file "copysignl.c"
	.text
..TXTST0:
# -- Begin  copysignl
	.text
       .align    16,0x90
	.globl copysignl
copysignl:
# parameter 1: 64 + %rsp
# parameter 2: 80 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_copysignl.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        xorb      %sil, %sil
        fldt      64(%rsp)
        fstpt     (%rsp)
..B1.2:
        fnstcw    50(%rsp)
..B1.3:
        movzwl    50(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 48(%rsp)
..B1.5:
        fldcw     48(%rsp)
..B1.6:
        movb      $1, %sil
..B1.7:
        fldt      80(%rsp)
        lea       _ones(%rip), %rax
        fldt      (%rsp)
        movb      89(%rsp), %dl
        andb      $-128, %dl
        fldl      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     32(%rsp)
        fmulp     %st, %st(1)
        movb      41(%rsp), %cl
        andb      $127, %cl
        orb       %dl, %cl
        fstpt     16(%rsp)
        testb     %sil, %sil
        movb      %cl, 41(%rsp)
        je        ..B1.9
..B1.8:
        fldcw     50(%rsp)
..B1.9:
        fldt      32(%rsp)
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	copysignl,@function
	.size	copysignl,.-copysignl
	.data
# -- End  copysignl
	.section .rodata, "a"
	.align 4
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
