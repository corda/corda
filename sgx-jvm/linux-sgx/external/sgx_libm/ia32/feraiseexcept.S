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
	.file "feraiseexcept.c"
	.text
..TXTST0:
# -- Begin  feraiseexcept
	.text
       .align    16,0x90
	.globl feraiseexcept
feraiseexcept:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movl      16(%esp), %eax
        testb     $1, %al
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        je        ..B1.3
..B1.2:
        flds      d_zero@GOTOFF(%edx)
        fwait     
        flds      d_zero@GOTOFF(%edx)
        fwait     
        fdivrp    %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.3:
        testb     $4, %al
        je        ..B1.5
..B1.4:
        movl      p_one@GOTOFF(%edx), %ecx
        flds      (%ecx)
        fwait     
        movl      p_zero@GOTOFF(%edx), %ecx
        flds      (%ecx)
        fwait     
        fdivrp    %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.5:
        testb     $2, %al
        je        ..B1.7
..B1.6:
        flds      d_tiny@GOTOFF(%edx)
        fwait     
        flds      d_tiny@GOTOFF(%edx)
        fwait     
        faddp     %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.7:
        testb     $8, %al
        je        ..B1.9
..B1.8:
        flds      d_huge@GOTOFF(%edx)
        fwait     
        flds      d_huge@GOTOFF(%edx)
        fwait     
        fmulp     %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.9:
        testb     $16, %al
        je        ..B1.11
..B1.10:
        flds      d_norm@GOTOFF(%edx)
        fwait     
        flds      d_norm@GOTOFF(%edx)
        fwait     
        fmulp     %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.11:
        testb     $32, %al
        je        ..B1.13
..B1.12:
        movl      p_one@GOTOFF(%edx), %eax
        movl      p_norm@GOTOFF(%edx), %edx
        flds      (%eax)
        fwait     
        flds      (%edx)
        fwait     
        faddp     %st, %st(1)
        fwait     
        fstps     (%esp)
        fwait     
..B1.13:
        xorl      %eax, %eax
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	feraiseexcept,@function
	.size	feraiseexcept,.-feraiseexcept
	.data
# -- End  feraiseexcept
	.section .rodata, "a"
	.align 4
	.align 4
d_zero:
	.long	0
	.type	d_zero,@object
	.size	d_zero,4
	.align 4
d_one:
	.long	1065353216
	.type	d_one,@object
	.size	d_one,4
	.align 4
d_tiny:
	.long	8388607
	.type	d_tiny,@object
	.size	d_tiny,4
	.align 4
d_huge:
	.long	2139095039
	.type	d_huge,@object
	.size	d_huge,4
	.align 4
d_norm:
	.long	8388608
	.type	d_norm,@object
	.size	d_norm,4
	.section .data.rel.ro.local, "aw",@progbits
	.align 4
	.align 4
p_one:
	.long	d_one
	.type	p_one,@object
	.size	p_one,4
	.align 4
p_zero:
	.long	d_zero
	.type	p_zero,@object
	.size	p_zero,4
	.align 4
p_norm:
	.long	d_norm
	.type	p_norm,@object
	.size	p_norm,4
	.data
	.section .note.GNU-stack, ""
# End
