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
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_logbf.1:
..L2:

        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        movl      %eax, %edx
        andl      $2147483647, %edx
        addl      $-8388608, %edx
        cmpl      $2130706432, %edx
        jb        ..B1.7
..B1.2:
        testl     %edx, %edx
        jge       ..B1.6
..B1.3:
        cmpl      $-8388608, %edx
        je        ..B1.8
..B1.4:
        movss     -8(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        pxor      %xmm0, %xmm0
        andl      $2147483647, %eax
        shrl      $23, %eax
        addl      $-152, %eax
        cvtsi2ss  %eax, %xmm0
..B1.5:
        ret       
..B1.6:
        lea       _ones(%rip), %rdx
        shrl      $31, %eax
        movss     (%rdx,%rax,4), %xmm1
        mulss     %xmm1, %xmm0
        ret       
..B1.7:
        shrl      $23, %edx
        pxor      %xmm0, %xmm0
        addl      $-126, %edx
        cvtsi2ss  %edx, %xmm0
        ret       
..B1.8:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
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
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
