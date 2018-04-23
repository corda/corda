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
	.file "logbf.c"
	.text
..TXTST0:
# -- Begin  logbf
	.text
       .align    16,0x90
	.globl logbf
logbf:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movl      16(%esp), %eax
        movl      %eax, %edx
        andl      $2147483647, %edx
        addl      $-8388608, %edx
        cmpl      $2130706432, %edx
        jb        ..B1.7
..B1.2:
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        testl     %edx, %edx
        jge       ..B1.6
..B1.3:
        cmpl      $-8388608, %edx
        je        ..B1.8
..B1.4:
        flds      16(%esp)
        fmuls     .L_2il0floatpacket.1@GOTOFF(%ecx)
        fstps     16(%esp)
        movl      16(%esp), %eax
        andl      $2147483647, %eax
        shrl      $23, %eax
        addl      $-152, %eax
        movl      %eax, (%esp)
        fildl     (%esp)
..B1.5:
        addl      $12, %esp
        ret       
..B1.6:
        flds      16(%esp)
        shrl      $31, %eax
        fmuls     _ones@GOTOFF(%ecx,%eax,4)
        addl      $12, %esp
        ret       
..B1.7:
        shrl      $23, %edx
        addl      $-126, %edx
        movl      %edx, (%esp)
        fildl     (%esp)
        addl      $12, %esp
        ret       
..B1.8:
        fldz      
        fdivrs    .L_2il0floatpacket.0@GOTOFF(%ecx)
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	logbf,@function
	.size	logbf,.-logbf
	.data
# -- End  logbf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0x4c000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.data
	.section .note.GNU-stack, ""
# End
