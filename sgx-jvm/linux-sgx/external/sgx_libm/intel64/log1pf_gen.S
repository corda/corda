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
	.file "log1pf_gen.c"
	.text
..TXTST0:
# -- Begin  log1pf
	.text
       .align    16,0x90
	.globl log1pf
log1pf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log1pf.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        pxor      %xmm2, %xmm2
        cvtss2sd  %xmm0, %xmm2
        movd      %xmm0, %eax
        movaps    %xmm2, %xmm1
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        andps     .L_2il0floatpacket.6(%rip), %xmm1
        ja        ..B1.9
..B1.2:
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        comisd    %xmm2, %xmm0
        jb        ..B1.5
..B1.3:
        ucomisd   %xmm0, %xmm2
        jp        ..B1.4
        je        ..B1.10
..B1.4:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.3(%rip), %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B1.5:
        comisd    .L_2il0floatpacket.1(%rip), %xmm1
        jb        ..B1.7
..B1.6:
        movsd     .L_2il0floatpacket.5(%rip), %xmm0
        addsd     %xmm2, %xmm0
        call      log@PLT
..B1.13:
        cvtsd2ss  %xmm0, %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B1.7:
        movaps    %xmm2, %xmm1
        pxor      %xmm0, %xmm0
        mulsd     %xmm2, %xmm1
        mulsd     .L_2il0floatpacket.4(%rip), %xmm1
        subsd     %xmm1, %xmm2
        cvtsd2ss  %xmm2, %xmm0
..B1.8:
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B1.9:
        addss     %xmm0, %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
..B1.10:
        movss     .L_2il0floatpacket.2(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	log1pf,@function
	.size	log1pf,.-log1pf
	.data
# -- End  log1pf
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.6:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3e300000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 4
.L_2il0floatpacket.2:
	.long	0xbf800000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x7f800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
