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
	.file "log2f_gen.c"
	.text
..TXTST0:
# -- Begin  log2f
	.text
       .align    16,0x90
	.globl log2f
log2f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log2f.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        cmpl      $2139095040, %edx
        jae       ..B1.14
..B1.2:
        lea       -1065222144(%rdx), %eax
        cmpl      $196608, %eax
        jae       ..B1.7
..B1.3:
        cmpl      $1065353216, %edx
        jne       ..B1.5
..B1.4:
        pxor      %xmm3, %xmm3
        jmp       ..B1.6
..B1.5:
        movss     -8(%rsp), %xmm0
        lea       40+_Q(%rip), %rax
        lea       32+_Q(%rip), %rsi
        addss     .L_2il0floatpacket.0(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        movaps    %xmm0, %xmm1
        lea       24+_Q(%rip), %rdx
        movsd     (%rax), %xmm3
        lea       16+_Q(%rip), %rdi
        movsd     (%rsi), %xmm2
        lea       8+_Q(%rip), %rcx
        lea       _Q(%rip), %r8
        mulsd     %xmm0, %xmm1
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm3
        addsd     (%rdi), %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm3
        addsd     (%r8), %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     %xmm2, %xmm3
        cvtsd2ss  %xmm3, %xmm3
..B1.6:
        movaps    %xmm3, %xmm0
        ret       
..B1.7:
        movl      %edx, %r9d
        movl      %edx, %eax
        shrl      $23, %r9d
        andl      $8388607, %eax
        addl      $-127, %r9d
        movl      %eax, -8(%rsp)
        cmpl      $8388608, %edx
        jae       ..B1.10
..B1.8:
        testl     %edx, %edx
        je        ..B1.22
..B1.9:
        movss     -8(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        movl      %eax, %r9d
        movl      %eax, %edx
        shrl      $23, %r9d
        addl      $-152, %r9d
..B1.10:
        testl     $8388607, %edx
        jne       ..B1.12
..B1.11:
        pxor      %xmm0, %xmm0
        cvtsi2ss  %r9d, %xmm0
        ret       
..B1.12:
        shrl      $15, %edx
        orl       $1065353216, %eax
        movzbl    %dl, %edi
        pxor      %xmm2, %xmm2
        movl      %eax, -8(%rsp)
        pxor      %xmm0, %xmm0
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rax
        lea       16+_P(%rip), %rdx
        lea       8+_P(%rip), %rcx
        lea       _P(%rip), %r8
        movq      __libm_logf_table_256@GOTPCREL(%rip), %rsi
        pxor      %xmm3, %xmm3
        cvtss2sd  -8(%rsp), %xmm2
        cvtss2sd  (%rax,%rdi,4), %xmm0
        cvtsi2sd  %r9d, %xmm3
        mulsd     %xmm0, %xmm2
        movsd     (%rdx), %xmm0
        movsd     (%rsi,%rdi,8), %xmm1
        addsd     .L_2il0floatpacket.2(%rip), %xmm2
        mulsd     %xmm2, %xmm0
        addsd     (%rcx), %xmm0
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm0
        addsd     %xmm1, %xmm2
        mulsd     (%r8), %xmm2
        addsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        cvtsd2ss  %xmm0, %xmm0
..B1.13:
        ret       
..B1.14:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.20
..B1.15:
        testl     $-2147483648, %edx
        je        ..B1.20
..B1.16:
        testl     %eax, %eax
        jne       ..B1.18
..B1.17:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        ret       
..B1.18:
        movss     .L_2il0floatpacket.3(%rip), %xmm1
        pxor      %xmm0, %xmm0
        mulss     %xmm1, %xmm0
..B1.19:
        ret       
..B1.20:
        movss     -8(%rsp), %xmm0
        ret       
..B1.22:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	log2f,@function
	.size	log2f,.-log2f
	.data
# -- End  log2f
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.2:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
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
.L_2il0floatpacket.3:
	.long	0x7f800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
_Q:
	.long	1697350398
	.long	1073157447
	.long	1697278904
	.long	3219592519
	.long	3711670093
	.long	1071564553
	.long	491294680
	.long	3218543945
	.long	1795286032
	.long	1070757723
	.long	557036084
	.long	3217959989
	.type	_Q,@object
	.size	_Q,48
	.align 4
_P:
	.long	1697350398
	.long	1073157447
	.long	605117704
	.long	3219592522
	.long	782203563
	.long	1071564561
	.type	_P,@object
	.size	_P,24
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
