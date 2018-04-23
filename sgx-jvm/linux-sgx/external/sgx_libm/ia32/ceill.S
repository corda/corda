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
	.file "ceill.c"
	.text
..TXTST0:
# -- Begin  ceill
	.text
       .align    16,0x90
	.globl ceill
ceill:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $16445, %eax
        jge       ..B1.21
..B1.4:
        cmpl      $16383, %eax
        jl        ..B1.15
..B1.5:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.36
..B1.6:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.7:
        fldcw     12(%esp)
..B1.8:
        movl      $1, %ecx
..B1.9:
        fldt      8(%ebp)
        fld       %st(0)
        fldl      _TWO_63H@GOTOFF(%edx)
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.12
        jp        ..B1.12
..B1.10:
        fldt      (%esp)
        faddl     _ones@GOTOFF(%edx)
        fstpt     (%esp)
..B1.12:
        testl     %ecx, %ecx
        je        ..B1.14
..B1.13:
        fldcw     14(%esp)
..B1.14:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        testl     %eax, %eax
        jne       ..B1.20
..B1.16:
        cmpl      $0, 12(%ebp)
        jne       ..B1.19
..B1.17:
        cmpl      $0, 8(%ebp)
        jne       ..B1.19
..B1.18:
        fldt      8(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        fldl      _smallest_value_64@GOTOFF(%edx)
        fstpl     16(%esp)
..B1.20:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      _pone_nzero@GOTOFF(%edx,%eax,8)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        cmpl      $16446, %eax
        jge       ..B1.30
..B1.22:
        testb     $1, 8(%ebp)
        je        ..B1.29
..B1.23:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.37
..B1.24:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.25:
        fldcw     12(%esp)
..B1.26:
        fldt      8(%ebp)
        faddl     _halfs@GOTOFF(%edx)
        fstpt     (%esp)
..B1.27:
        fldcw     14(%esp)
..B1.28:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        fldt      8(%ebp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.30:
        movzwl    14(%esp), %ecx
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.31:
        orl       $-64768, %ecx
        movw      %cx, 12(%esp)
..B1.32:
        fldcw     12(%esp)
..B1.33:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%edx)
        fstpt     (%esp)
..B1.34:
        fldcw     14(%esp)
..B1.35:
        fldt      (%esp)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        xorl      %ecx, %ecx
        jmp       ..B1.9
..B1.37:
        fldt      8(%ebp)
        faddl     _halfs@GOTOFF(%edx)
        fstpt     (%esp)
        jmp       ..B1.28
..B1.38:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%edx)
        fstpt     (%esp)
        jmp       ..B1.35
        .align    16,0x90
	.type	ceill,@function
	.size	ceill,.-ceill
	.data
# -- End  ceill
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_pone_nzero:
	.long	0
	.long	1072693248
	.long	0
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,16
	.align 4
_halfs:
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.type	_halfs,@object
	.size	_halfs,16
	.data
	.section .note.GNU-stack, ""
# End
