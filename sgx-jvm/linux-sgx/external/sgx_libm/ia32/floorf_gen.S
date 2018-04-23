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
	.file "floorf_gen.c"
	.text
..TXTST0:
# -- Begin  floorf
	.text
       .align    16,0x90
	.globl floorf
floorf:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movzwl    18(%esp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $149, %eax
        jge       ..B1.11
..B1.2:
        cmpl      $127, %eax
        jl        ..B1.7
..B1.3:
        flds      .L_2il0floatpacket.0@GOTOFF(%edx)
        flds      16(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstps     (%esp)
        flds      (%esp)
        fsubp     %st, %st(2)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B1.6
        jp        ..B1.6
..B1.4:
        fadds     .L_2il0floatpacket.1@GOTOFF(%edx)
..B1.6:
        addl      $12, %esp
        ret       
..B1.7:
        testl     %eax, %eax
        jne       ..B1.10
..B1.8:
        testl     $8388607, 16(%esp)
        jne       ..B1.10
..B1.9:
        flds      16(%esp)
        addl      $12, %esp
        ret       
..B1.10:
        movzbl    19(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        movl      16(%esp), %eax
        movl      %eax, (%esp)
        flds      _pzero_none@GOTOFF(%edx,%ecx,4)
        addl      $12, %esp
        ret       
..B1.11:
        cmpl      $150, %eax
        jge       ..B1.16
..B1.12:
        flds      16(%esp)
        testb     $1, 16(%esp)
        je        ..B1.15
..B1.13:
        fadds     .L_2il0floatpacket.2@GOTOFF(%edx)
..B1.15:
        addl      $12, %esp
        ret       
..B1.16:
        flds      16(%esp)
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	floorf,@function
	.size	floorf,.-floorf
	.data
# -- End  floorf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0x4b400000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.1:
	.long	0xbf800000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,4
	.align 4
.L_2il0floatpacket.2:
	.long	0xbf000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
_pzero_none:
	.long	0
	.long	3212836864
	.type	_pzero_none,@object
	.size	_pzero_none,8
	.data
	.section .note.GNU-stack, ""
# End
