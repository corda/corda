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
	.file "fmodf.c"
	.text
..TXTST0:
# -- Begin  fmodf
	.text
       .align    16,0x90
	.globl fmodf
fmodf:
# parameter 1: 16 + %esp
# parameter 2: 20 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movzwl    22(%esp), %ecx
        andl      $32640, %ecx
        shrl      $7, %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        testl     %ecx, %ecx
        jne       ..B1.3
..B1.2:
        testl     $8388607, 20(%esp)
        je        ..B1.8
..B1.3:
        movzwl    18(%esp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        cmpl      $255, %edx
        je        ..B1.22
..B1.4:
        cmpl      $255, %ecx
        je        ..B1.12
..B1.6:
        flds      20(%esp)
        flds      16(%esp)
.L_2TAG_PACKET_0.0.2:
        fprem     
        fstsw     %ax
        sahf      
        jp        .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        fstps     (%esp)
..B1.7:
        flds      (%esp)
        addl      $12, %esp
        ret       
..B1.8:
        movzwl    18(%esp), %edx
        andl      $32640, %edx
        cmpl      $32640, %edx
        jne       ..B1.11
..B1.9:
        testl     $8388607, 16(%esp)
        je        ..B1.11
..B1.10:
        flds      16(%esp)
        fadds     20(%esp)
        addl      $12, %esp
        ret       
..B1.11:
        fldz      
        fmuls     .L_2il0floatpacket.0@GOTOFF(%eax)
        fsts      (%esp)
        addl      $12, %esp
        ret       
..B1.12:
        testl     $8388607, 20(%esp)
        jne       ..B1.16
..B1.13:
        cmpl      $255, %edx
        je        ..B1.20
..B1.14:
        cmpl      $255, %ecx
        je        ..B1.17
        jmp       ..B1.6
..B1.16:
        flds      16(%esp)
        fadds     20(%esp)
        addl      $12, %esp
        ret       
..B1.17:
        testl     $8388607, 20(%esp)
        jne       ..B1.6
..B1.18:
        flds      16(%esp)
        addl      $12, %esp
        ret       
..B1.20:
        testl     $8388607, 16(%esp)
        jne       ..B1.14
..B1.21:
        fldz      
        fmuls     .L_2il0floatpacket.0@GOTOFF(%eax)
        addl      $12, %esp
        ret       
..B1.22:
        testl     $8388607, 16(%esp)
        jne       ..B1.16
..B1.23:
        cmpl      $255, %ecx
        je        ..B1.12
        jmp       ..B1.13
        .align    16,0x90
	.type	fmodf,@function
	.size	fmodf,.-fmodf
	.data
# -- End  fmodf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x7f800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.data
	.section .note.GNU-stack, ""
# End
