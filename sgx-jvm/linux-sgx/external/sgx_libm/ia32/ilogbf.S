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
	.file "ilogbf.c"
	.text
..TXTST0:
# -- Begin  ilogbf
	.text
       .align    16,0x90
	.globl ilogbf
ilogbf:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movl      16(%esp), %eax
        andl      $2147483647, %eax
        addl      $-8388608, %eax
        cmpl      $2130706432, %eax
        jb        ..B1.6
..B1.2:
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        testl     %eax, %eax
        jl        ..B1.7
..B1.3:
        fldz      
        cmpl      $2130706432, %eax
        fmuls     .L_2il0floatpacket.0@GOTOFF(%edx)
        fstps     (%esp)
        je        ..B1.5
..B1.4:
        flds      16(%esp)
        movl      $-2147483648, %eax
        fmul      %st(0), %st
        fstps     16(%esp)
        movl      16(%esp), %edx
        andl      $2147483647, %edx
        cmpl      $2139095040, %edx
        jae       ..L3
        movl      $0, %eax
..L3:
        addl      $12, %esp
        ret       
..B1.5:
        movl      $2147483647, %eax
        addl      $12, %esp
        ret       
..B1.6:
        shrl      $23, %eax
        addl      $-126, %eax
        addl      $12, %esp
        ret       
..B1.7:
        cmpl      $-8388608, %eax
        je        ..B1.9
..B1.8:
        flds      16(%esp)
        fmuls     .L_2il0floatpacket.1@GOTOFF(%edx)
        fstps     16(%esp)
        movl      16(%esp), %eax
        andl      $2147483647, %eax
        shrl      $23, %eax
        addl      $-152, %eax
        addl      $12, %esp
        ret       
..B1.9:
        fldz      
        movl      $-2147483648, %eax
        fmuls     .L_2il0floatpacket.0@GOTOFF(%edx)
        fstps     (%esp)
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	ilogbf,@function
	.size	ilogbf,.-ilogbf
	.data
# -- End  ilogbf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x7f800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x4c000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.data
	.section .note.GNU-stack, ""
# End
