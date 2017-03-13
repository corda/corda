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
	.file "libm_log_k32.c"
	.text
..TXTST0:
# -- Begin  __libm_log_k32
	.text
       .align    16,0x90
	.globl __libm_log_k32
__libm_log_k32:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___libm_log_k32.1:
..L2:

        movsd     %xmm0, -24(%rsp)
        movl      -20(%rsp), %ecx
        movl      %ecx, %eax
        andl      $2146435072, %eax
        negl      %eax
        lea       -1072676864(%rcx), %edx
        addl      $2145386496, %eax
        movl      %eax, -12(%rsp)
        cmpl      $24576, %edx
        movl      $0, -16(%rsp)
        movsd     .L_2il0floatpacket.0(%rip), %xmm1
        jae       ..B1.3
..B1.2:
        lea       32+_Q(%rip), %rax
        addsd     %xmm1, %xmm0
        movaps    %xmm0, %xmm2
        lea       24+_Q(%rip), %rcx
        movsd     (%rax), %xmm3
        lea       16+_Q(%rip), %rdx
        lea       8+_Q(%rip), %rsi
        lea       _Q(%rip), %rdi
        movsd     %xmm0, -24(%rsp)
        movsd     (%rcx), %xmm1
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
        addsd     %xmm3, %xmm0
        ret       
..B1.3:
        movl      %ecx, %esi
        pxor      %xmm0, %xmm0
        shrl      $12, %ecx
        lea       8+_P(%rip), %rax
        movzbl    %cl, %r9d
        lea       _P(%rip), %rdx
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rcx
        lea       _LN2(%rip), %rdi
        movsd     -24(%rsp), %xmm2
        shrl      $20, %esi
        addl      $-1023, %esi
        cvtss2sd  (%rcx,%r9,4), %xmm0
        mulsd     -16(%rsp), %xmm2
        mulsd     %xmm0, %xmm2
        movsd     (%rax), %xmm0
        addsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm0
        pxor      %xmm1, %xmm1
        cvtsi2sd  %esi, %xmm1
        addsd     (%rdx), %xmm0
        mulsd     (%rdi), %xmm1
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm0
        movq      __libm_logf_table_256@GOTPCREL(%rip), %r8
        addsd     %xmm2, %xmm0
        addsd     (%r8,%r9,8), %xmm1
        movsd     %xmm2, -24(%rsp)
        addsd     %xmm1, %xmm0
..B1.4:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_log_k32,@function
	.size	__libm_log_k32,.-__libm_log_k32
	.data
# -- End  __libm_log_k32
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
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
