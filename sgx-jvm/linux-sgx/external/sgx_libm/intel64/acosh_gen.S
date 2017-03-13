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
	.file "acosh_gen.c"
	.text
..TXTST0:
# -- Begin  acosh
	.text
       .align    16,0x90
	.globl acosh
acosh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_acosh.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %eax
        lea       -1072693248(%rax), %edx
        cmpl      $1073741824, %edx
        jae       ..B1.14
..B1.2:
        cmpl      $6144, %edx
        jae       ..B1.6
..B1.3:
        movl      -8(%rsp), %eax
        orl       %eax, %edx
        jne       ..B1.5
..B1.4:
        lea       _zeros(%rip), %rax
        movsd     (%rax), %xmm0
        ret       
..B1.5:
        lea       8+_ones(%rip), %rax
        pxor      %xmm3, %xmm3
        movsd     -8(%rsp), %xmm5
        xorl      %r9d, %r9d
        movsd     .L_2il0floatpacket.0(%rip), %xmm4
        lea       32+_PA(%rip), %rdx
        lea       16+_PA(%rip), %rcx
        lea       24+_PA(%rip), %rdi
        lea       _PA(%rip), %rsi
        addsd     (%rax), %xmm5
        mulsd     %xmm5, %xmm4
        movaps    %xmm5, %xmm6
        mulsd     %xmm5, %xmm6
        sqrtsd    %xmm4, %xmm3
        movsd     %xmm3, -16(%rsp)
        movaps    %xmm3, %xmm0
        movl      %r9d, -16(%rsp)
        lea       8+_PA(%rip), %r8
        movsd     -16(%rsp), %xmm11
        movaps    %xmm11, %xmm1
        movaps    %xmm11, %xmm2
        movsd     (%rdx), %xmm8
        subsd     %xmm11, %xmm0
        mulsd     %xmm11, %xmm1
        mulsd     %xmm6, %xmm8
        mulsd     %xmm0, %xmm2
        subsd     %xmm1, %xmm4
        addsd     (%rcx), %xmm8
        subsd     %xmm2, %xmm4
        mulsd     %xmm6, %xmm8
        divsd     %xmm3, %xmm4
        movsd     (%rdi), %xmm7
        addsd     %xmm4, %xmm0
        mulsd     %xmm6, %xmm7
        addsd     (%rsi), %xmm8
        mulsd     .L_2il0floatpacket.1(%rip), %xmm0
        addsd     (%r8), %xmm7
        mulsd     %xmm5, %xmm8
        mulsd     %xmm6, %xmm7
        addsd     %xmm7, %xmm8
        movsd     %xmm8, -24(%rsp)
        movaps    %xmm8, %xmm9
        movl      %r9d, -24(%rsp)
        movsd     -24(%rsp), %xmm10
        mulsd     %xmm0, %xmm8
        subsd     %xmm10, %xmm9
        mulsd     %xmm11, %xmm10
        mulsd     %xmm11, %xmm9
        movsd     %xmm10, -24(%rsp)
        addsd     %xmm8, %xmm9
        addsd     %xmm9, %xmm0
        addsd     %xmm10, %xmm0
        addsd     %xmm11, %xmm0
        ret       
..B1.6:
        cmpl      $29360128, %edx
        jae       ..B1.11
..B1.7:
        movsd     -8(%rsp), %xmm7
        cmpl      $2293760, %edx
        jae       ..B1.9
..B1.8:
        xorl      %edx, %edx
        movaps    %xmm7, %xmm1
        movl      %edx, -8(%rsp)
        lea       8+_ones(%rip), %rax
        movsd     -8(%rsp), %xmm8
        movaps    %xmm8, %xmm4
        subsd     %xmm8, %xmm1
        mulsd     %xmm8, %xmm4
        addsd     %xmm8, %xmm7
        mulsd     %xmm1, %xmm7
        addsd     (%rax), %xmm4
        movaps    %xmm4, %xmm0
        addsd     %xmm7, %xmm0
        sqrtsd    %xmm0, %xmm0
        movsd     %xmm0, -16(%rsp)
        movl      %edx, -16(%rsp)
        movsd     -16(%rsp), %xmm6
        movaps    %xmm6, %xmm2
        movaps    %xmm6, %xmm3
        mulsd     %xmm6, %xmm2
        subsd     %xmm6, %xmm0
        addsd     %xmm6, %xmm8
        mulsd     %xmm0, %xmm3
        subsd     %xmm2, %xmm4
        movaps    %xmm6, %xmm5
        subsd     %xmm3, %xmm4
        addsd     %xmm0, %xmm5
        addsd     %xmm4, %xmm7
        divsd     %xmm5, %xmm7
        addsd     %xmm7, %xmm0
        mulsd     .L_2il0floatpacket.1(%rip), %xmm0
        addsd     %xmm0, %xmm1
        jmp       ..B1.10
..B1.9:
        movaps    %xmm7, %xmm0
        lea       8+_ones(%rip), %rax
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
        mulsd     %xmm7, %xmm0
        movsd     (%rax), %xmm1
        addsd     %xmm1, %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm7, %xmm0
        mulsd     %xmm2, %xmm7
        divsd     %xmm0, %xmm1
        movsd     %xmm7, -8(%rsp)
        movl      $0, -8(%rsp)
        movsd     -8(%rsp), %xmm8
        subsd     %xmm8, %xmm7
        addsd     %xmm7, %xmm1
..B1.10:
        movaps    %xmm1, %xmm0
        movq      $0x3ff0000000000000, %rax
        addsd     %xmm8, %xmm0
        movsd     %xmm0, -8(%rsp)
        movl      $0, -8(%rsp)
        movsd     -8(%rsp), %xmm7
        movzwl    -2(%rsp), %ecx
        subsd     %xmm7, %xmm8
        andl      $32752, %ecx
        addsd     %xmm8, %xmm1
        shrl      $4, %ecx
        addl      $-1023, %ecx
        movslq    %ecx, %rdx
        negq      %rdx
        shlq      $52, %rdx
        addq      %rax, %rdx
        movq      %rdx, -24(%rsp)
        movsd     -24(%rsp), %xmm2
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm7
        movaps    %xmm1, %xmm3
        addsd     %xmm7, %xmm3
        movsd     %xmm3, -16(%rsp)
        movl      -12(%rsp), %esi
        shrl      $12, %esi
        movzbl    %sil, %edx
        jmp       ..B1.12
..B1.11:
        movzwl    -2(%rsp), %eax
        movl      %eax, %ecx
        andl      $-32753, %eax
        andl      $32752, %ecx
        orl       $-49168, %eax
        movw      %ax, -2(%rsp)
        movsd     -8(%rsp), %xmm1
        movl      $0, -8(%rsp)
        movl      -4(%rsp), %edx
        movsd     -8(%rsp), %xmm7
        shrl      $4, %ecx
        subsd     %xmm7, %xmm1
        shrl      $12, %edx
        addl      $-1022, %ecx
        movzbl    %dl, %edx
..B1.12:
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rsi
        pxor      %xmm0, %xmm0
        lea       _ones(%rip), %rdi
        lea       32+_PL(%rip), %r8
        lea       24+_PL(%rip), %r11
        lea       16+_PL(%rip), %r9
        lea       _PL(%rip), %r10
        pxor      %xmm6, %xmm6
        movq      __libm_log_table_256@GOTPCREL(%rip), %rax
        movsd     (%r8), %xmm5
        movsd     (%r11), %xmm4
        cvtss2sd  (%rsi,%rdx,4), %xmm0
        cvtsi2sd  %ecx, %xmm6
        mulsd     %xmm0, %xmm7
        mulsd     %xmm0, %xmm1
        subsd     (%rdi), %xmm7
        movaps    %xmm7, %xmm3
        lea       _LN2(%rip), %rcx
        lea       8+_PL(%rip), %rsi
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     (%r9), %xmm5
        addsd     (%rsi), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     (%r10), %xmm5
        mulsd     %xmm3, %xmm4
        mulsd     %xmm2, %xmm5
        movsd     (%rcx), %xmm0
        addsd     %xmm4, %xmm5
        mulsd     %xmm6, %xmm0
        addsd     %xmm5, %xmm1
        shlq      $4, %rdx
        lea       8+_LN2(%rip), %rcx
        movsd     %xmm3, -16(%rsp)
        addsd     (%rax,%rdx), %xmm0
        addsd     %xmm1, %xmm0
        movsd     (%rcx), %xmm1
        mulsd     %xmm1, %xmm6
        addsd     8(%rax,%rdx), %xmm6
        movsd     %xmm6, -24(%rsp)
        addsd     %xmm6, %xmm7
        movsd     %xmm7, -8(%rsp)
        addsd     %xmm7, %xmm0
..B1.13:
        ret       
..B1.14:
        movl      %eax, %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        jbe       ..B1.16
..B1.15:
        movsd     -8(%rsp), %xmm0
        addsd     %xmm0, %xmm0
        ret       
..B1.16:
        cmpl      $2146435072, %eax
        jne       ..B1.18
..B1.17:
        cmpl      $0, -8(%rsp)
        je        ..B1.15
..B1.18:
        cmpl      $2146435072, %edx
        jne       ..B1.20
..B1.19:
        cmpl      $0, -8(%rsp)
        jne       ..B1.15
..B1.20:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	acosh,@function
	.size	acosh,.-acosh
	.data
# -- End  acosh
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_PA:
	.long	1431655760
	.long	3216332117
	.long	858954823
	.long	1066611507
	.long	2956777430
	.long	3212237677
	.long	4158004615
	.long	1063197775
	.long	2173429939
	.long	3209085130
	.type	_PA,@object
	.size	_PA,40
	.align 4
_PL:
	.long	0
	.long	3219128320
	.long	1431621855
	.long	1070945621
	.long	4294842013
	.long	3218079743
	.long	1289448124
	.long	1070176674
	.long	2077359316
	.long	3217380703
	.type	_PL,@object
	.size	_PL,40
	.align 4
_LN2:
	.long	897137782
	.long	1038760431
	.long	4276092928
	.long	1072049730
	.type	_LN2,@object
	.size	_LN2,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
