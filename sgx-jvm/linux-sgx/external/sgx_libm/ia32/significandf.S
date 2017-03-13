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
	.file "significandf.c"
	.text
..TXTST0:
# -- Begin  significandf
	.text
       .align    16,0x90
	.globl significandf
significandf:
# parameter 1: 4 + %esp
..B1.1:
..L1:

        movl      4(%esp), %ecx
        movl      %ecx, %edx
        andl      $2147483647, %edx
        lea       -8388608(%edx), %eax
        cmpl      $2130706432, %eax
        jb        ..B1.6
..B1.2:
        decl      %edx
        cmpl      $2139095039, %edx
        jae       ..B1.4
..B1.3:
        flds      4(%esp)
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        fmuls     .L_2il0floatpacket.0@GOTOFF(%eax)
        fstps     4(%esp)
        movl      4(%esp), %edx
        andl      $-2139095041, %edx
        orl       $1065353216, %edx
        movl      %edx, 4(%esp)
        flds      4(%esp)
        ret       
..B1.4:
        flds      4(%esp)
..B1.5:
        ret       
..B1.6:
        andl      $-2139095041, %ecx
        orl       $1065353216, %ecx
        movl      %ecx, 4(%esp)
        flds      4(%esp)
        ret       
        .align    16,0x90
	.type	significandf,@function
	.size	significandf,.-significandf
	.data
# -- End  significandf
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
