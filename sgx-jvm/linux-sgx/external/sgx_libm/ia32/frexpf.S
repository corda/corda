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
	.file "frexpf.c"
	.text
..TXTST0:
# -- Begin  frexpf
	.text
       .align    16,0x90
	.globl frexpf
frexpf:
# parameter 1: 8 + %esp
# parameter 2: 12 + %esp
..B1.1:
..L1:

        pushl     %edi
        movl      8(%esp), %ecx
        movl      %ecx, %edx
        andl      $2147483647, %edx
        movl      12(%esp), %eax
        lea       -8388608(%edx), %edi
        cmpl      $2130706432, %edi
        jb        ..B1.5
..B1.2:
        decl      %edx
        cmpl      $2139095039, %edx
        jae       ..B1.6
..B1.3:
        flds      8(%esp)
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        fmuls     .L_2il0floatpacket.0@GOTOFF(%edx)
        fstps     8(%esp)
        movl      8(%esp), %ecx
        movl      %ecx, %edi
        andl      $2147483647, %edi
        andl      $-2139095041, %ecx
        shrl      $23, %edi
        orl       $1056964608, %ecx
        movl      %ecx, 8(%esp)
        addl      $-151, %edi
        flds      8(%esp)
        movl      %edi, (%eax)
..B1.4:
        popl      %edi
        ret       
..B1.5:
        andl      $-2139095041, %ecx
        shrl      $23, %edi
        orl       $1056964608, %ecx
        movl      %ecx, 8(%esp)
        addl      $-125, %edi
        flds      8(%esp)
        movl      %edi, (%eax)
        popl      %edi
        ret       
..B1.6:
        flds      8(%esp)
        movl      $0, (%eax)
        popl      %edi
        ret       
        .align    16,0x90
	.type	frexpf,@function
	.size	frexpf,.-frexpf
	.data
# -- End  frexpf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4c000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.data
	.section .note.GNU-stack, ""
# End
