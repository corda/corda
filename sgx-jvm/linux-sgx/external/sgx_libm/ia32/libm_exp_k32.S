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
	.file "libm_exp_k32.c"
	.text
..TXTST0:
# -- Begin  __libm_exp_k32
	.text
       .align    16,0x90
	.hidden __libm_exp_k32
	.globl __libm_exp_k32
__libm_exp_k32:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $24, %esp
        movl      12(%ebp), %eax
        movl      %eax, %edx
        andl      $2147483647, %edx
        shrl      $31, %eax
        cmpl      $1012924416, %edx
        jb        ..B1.3
..B1.2:
        fldl      8(%ebp)
        xorl      %edx, %edx
        call      ..L2
..L2:
        popl      %edi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edi), %edi
        fld1      
        fldl      _TWO_52H@GOTOFF(%edi)
        fldl      .L_2il0floatpacket.0@GOTOFF(%edi)
        fmul      %st(3), %st
        movl      %edx, 16(%esp)
        fadd      %st(1), %st
        fstpl     (%esp)
        fldl      (%esp)
        movl      (%esp), %ecx
        movl      %ecx, %ebx
        shll      $25, %ebx
        fsubp     %st, %st(1)
        fldl      .L_2il0floatpacket.1@GOTOFF(%edi)
        fmul      %st(1), %st
        sarl      $25, %ebx
        fsubrp    %st, %st(3)
        fld       %st(2)
        subl      %ebx, %ecx
        fxch      %st(3)
        fstl      8(%ebp)
        fldl      .L_2il0floatpacket.2@GOTOFF(%edi)
        fmulp     %st, %st(2)
        shll      $4, %ebx
        fxch      %st(1)
        fsubr     %st, %st(3)
        fld       %st(3)
        fxch      %st(2)
        fsub      %st(4), %st
        shrl      $7, %ecx
        fsubp     %st, %st(1)
        addl      SC2_BIAS@GOTOFF(%edi,%eax,4), %ecx
        fadd      %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fstpl     8(%esp)
        fldl      .L_2il0floatpacket.3@GOTOFF(%edi)
        fldl      .L_2il0floatpacket.6@GOTOFF(%edi)
        movl      %edx, 8(%esp)
        fmul      %st(4), %st
        fldl      8(%esp)
        shll      $20, %ecx
        fadd      %st, %st(2)
        movl      %ecx, 20(%esp)
        fxch      %st(2)
        fsubrp    %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(2)
        fldl      .L_2il0floatpacket.4@GOTOFF(%edi)
        fmul      %st(4), %st
        fxch      %st(5)
        faddl     .L_2il0floatpacket.7@GOTOFF(%edi)
        fmulp     %st, %st(3)
        fxch      %st(4)
        faddl     .L_2il0floatpacket.5@GOTOFF(%edi)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldl      .L_2il0floatpacket.8@GOTOFF(%edi)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldl      1032+__libm_exp_table_128@GOTOFF(%edi,%ebx)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fxch      %st(3)
        fmulp     %st, %st(1)
        fxch      %st(2)
        fmull     1024+__libm_exp_table_128@GOTOFF(%edi,%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fmull     16(%esp)
        fmull     _SC2@GOTOFF(%edi,%eax,8)
        addl      $24, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.3:
        fld1      
        faddl     8(%ebp)
..B1.4:
        addl      $24, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	__libm_exp_k32,@function
	.size	__libm_exp_k32,.-__libm_exp_k32
	.data
# -- End  __libm_exp_k32
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x652b82fe,0x40671547
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3f762e42
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x3de6af28,0x3e2fdf47
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x87372663,0x3f56c16c
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x5555541d,0x3fa55555
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x6887cd7c,0x3f811111
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x55555405,0x3fc55555
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
SC2_BIAS:
	.long	511
	.long	1535
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_SC2:
	.long	0
	.long	1609564160
	.long	0
	.long	535822336
	.type	_SC2,@object
	.size	_SC2,16
	.data
	.hidden __libm_exp_table_128
	.section .note.GNU-stack, ""
# End
