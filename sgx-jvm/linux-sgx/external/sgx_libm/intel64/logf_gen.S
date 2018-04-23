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
	.file "logf_gen.c"
	.text
..TXTST0:
# -- Begin  logf
	.text
       .align    16,0x90
	.globl logf
logf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_logf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -8(%rsp)
        cmpl      $2139095040, %edx
        jae       ..B1.11
..B1.2:
        lea       -1065091072(%rdx), %eax
        cmpl      $524288, %eax
        jae       ..B1.7
..B1.3:
        cmpl      $1065353216, %edx
        jne       ..B1.5
..B1.4:
        pxor      %xmm3, %xmm3
        jmp       ..B1.6
..B1.5:
        movss     -8(%rsp), %xmm0
        lea       32+_Q(%rip), %rax
        lea       24+_Q(%rip), %rcx
        addss     .L_2il0floatpacket.0(%rip), %xmm0
        cvtss2sd  %xmm0, %xmm0
        movaps    %xmm0, %xmm2
        lea       16+_Q(%rip), %rdx
        movsd     (%rax), %xmm3
        lea       8+_Q(%rip), %rsi
        movsd     (%rcx), %xmm1
        lea       _Q(%rip), %rdi
        mulsd     %xmm0, %xmm2
        mulsd     %xmm2, %xmm3
        mulsd     %xmm2, %xmm1
        addsd     (%rdx), %xmm3
        addsd     (%rsi), %xmm1
        mulsd     %xmm2, %xmm3
        mulsd     %xmm0, %xmm1
        addsd     %xmm1, %xmm3
        addsd     (%rdi), %xmm3
        mulsd     %xmm2, %xmm3
        addsd     %xmm0, %xmm3
        cvtsd2ss  %xmm3, %xmm3
..B1.6:
        movaps    %xmm3, %xmm0
        ret       
..B1.7:
        movl      %edx, %esi
        movl      %edx, %eax
        shrl      $23, %esi
        andl      $8388607, %eax
        addl      $-127, %esi
        cmpl      $8388608, %edx
        jae       ..B1.10
..B1.8:
        movl      %eax, -8(%rsp)
        testl     %edx, %edx
        je        ..B1.19
..B1.9:
        movss     -8(%rsp), %xmm0
        mulss     .L_2il0floatpacket.2(%rip), %xmm0
        movd      %xmm0, %eax
        movss     %xmm0, -8(%rsp)
        movl      %eax, %esi
        movl      %eax, %edx
        shrl      $23, %esi
        addl      $-152, %esi
..B1.10:
        shrl      $15, %edx
        orl       $1065353216, %eax
        movzbl    %dl, %r8d
        pxor      %xmm1, %xmm1
        movl      %eax, -8(%rsp)
        pxor      %xmm0, %xmm0
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rax
        lea       8+_P(%rip), %rdx
        cvtss2sd  -8(%rsp), %xmm1
        cvtss2sd  (%rax,%r8,4), %xmm0
        mulsd     %xmm0, %xmm1
        lea       _P(%rip), %rcx
        movsd     (%rdx), %xmm0
        pxor      %xmm2, %xmm2
        cvtsi2sd  %esi, %xmm2
        addsd     .L_2il0floatpacket.1(%rip), %xmm1
        mulsd     %xmm1, %xmm0
        lea       _LN2(%rip), %rsi
        mulsd     (%rsi), %xmm2
        addsd     (%rcx), %xmm0
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movq      __libm_logf_table_256@GOTPCREL(%rip), %rdi
        addsd     %xmm1, %xmm0
        addsd     (%rdi,%r8,8), %xmm2
        addsd     %xmm2, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.11:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.17
..B1.12:
        testl     $-2147483648, %edx
        je        ..B1.17
..B1.13:
        testl     %eax, %eax
        jne       ..B1.15
..B1.14:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        ret       
..B1.15:
        movss     .L_2il0floatpacket.3(%rip), %xmm1
        pxor      %xmm0, %xmm0
        mulss     %xmm1, %xmm0
..B1.16:
        ret       
..B1.17:
        movss     -8(%rsp), %xmm0
        ret       
..B1.19:
        movss     .L_2il0floatpacket.0(%rip), %xmm0
        pxor      %xmm1, %xmm1
        divss     %xmm1, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	logf,@function
	.size	logf,.-logf
	.data
# -- End  logf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
.L_2il0floatpacket.2:
	.long	0x4c000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,4
	.align 4
.L_2il0floatpacket.3:
	.long	0x7f800000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,4
	.align 4
_Q:
	.long	4294868184
	.long	3219128319
	.long	1443356411
	.long	1070945621
	.long	823147527
	.long	3218079745
	.long	748089218
	.long	1070176642
	.long	1053536005
	.long	3217353219
	.type	_Q,@object
	.size	_Q,40
	.align 4
_P:
	.long	3879088107
	.long	3219128321
	.long	1754324240
	.long	1070945626
	.type	_P,@object
	.size	_P,16
	.align 4
_LN2:
	.long	4277811695
	.long	1072049730
	.type	_LN2,@object
	.size	_LN2,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
