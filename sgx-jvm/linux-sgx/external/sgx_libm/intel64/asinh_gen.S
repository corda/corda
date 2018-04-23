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
	.file "asinh_gen.c"
	.text
..TXTST0:
# -- Begin  asinh
	.text
       .align    16,0x90
	.globl asinh
asinh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_asinh.1:
..L2:

        movsd     %xmm0, -16(%rsp)
        movl      -12(%rsp), %esi
        movl      %esi, %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        jae       ..B1.18
..B1.2:
        cmpl      $1102053376, %edx
        jae       ..B1.16
..B1.3:
        cmpl      $1074987008, %edx
        jae       ..B1.14
..B1.4:
        cmpl      $1068498944, %edx
        jae       ..B1.13
..B1.5:
        cmpl      $1012924416, %edx
        jae       ..B1.12
..B1.6:
        shrl      $31, %esi
        cmpl      $1048576, %edx
        jb        ..B1.8
..B1.7:
        lea       _SCALE(%rip), %rax
        lea       _MINNORM(%rip), %rdx
        movl      %esi, %esi
        lea       8+_SCALE(%rip), %rcx
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        subsd     (%rdx,%rsi,8), %xmm0
        mulsd     (%rcx), %xmm0
        ret       
..B1.8:
        movl      -16(%rsp), %eax
        orl       %eax, %edx
        je        ..B1.10
..B1.9:
        lea       _small_value_64(%rip), %rax
        movsd     -16(%rsp), %xmm0
        movsd     (%rax,%rsi,8), %xmm1
        mulsd     (%rax), %xmm1
        movsd     %xmm1, -40(%rsp)
        subsd     %xmm1, %xmm0
        ret       
..B1.10:
        movsd     -16(%rsp), %xmm0
..B1.11:
        ret       
..B1.12:
        movsd     -16(%rsp), %xmm0
        lea       32+_PA(%rip), %rax
        movaps    %xmm0, %xmm4
        lea       24+_PA(%rip), %rcx
        mulsd     %xmm0, %xmm4
        lea       16+_PA(%rip), %rdx
        movaps    %xmm4, %xmm1
        lea       8+_PA(%rip), %rsi
        mulsd     %xmm4, %xmm1
        lea       _PA(%rip), %rdi
        movsd     (%rax), %xmm3
        movsd     (%rcx), %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm3
        addsd     (%rsi), %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm4, %xmm2
        mulsd     %xmm0, %xmm4
        addsd     %xmm2, %xmm3
        addsd     (%rdi), %xmm3
        mulsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm0
        ret       
..B1.13:
        movsd     -16(%rsp), %xmm5
        xorl      %eax, %eax
        andps     .L_2il0floatpacket.2(%rip), %xmm5
        movsd     %xmm5, -16(%rsp)
        movaps    %xmm5, %xmm1
        movl      %eax, -16(%rsp)
        movsd     -16(%rsp), %xmm9
        movaps    %xmm9, %xmm7
        subsd     %xmm9, %xmm1
        mulsd     %xmm9, %xmm7
        addsd     %xmm9, %xmm5
        movsd     .L_2il0floatpacket.3(%rip), %xmm0
        mulsd     %xmm1, %xmm5
        addsd     %xmm0, %xmm7
        movaps    %xmm7, %xmm2
        addsd     %xmm5, %xmm2
        sqrtsd    %xmm2, %xmm2
        movsd     %xmm2, -24(%rsp)
        movl      %eax, -24(%rsp)
        movsd     -24(%rsp), %xmm8
        movaps    %xmm8, %xmm3
        movaps    %xmm8, %xmm4
        mulsd     %xmm8, %xmm3
        subsd     %xmm8, %xmm2
        addsd     %xmm8, %xmm9
        mulsd     %xmm2, %xmm4
        subsd     %xmm3, %xmm7
        movaps    %xmm8, %xmm6
        subsd     %xmm4, %xmm7
        addsd     %xmm2, %xmm6
        addsd     %xmm5, %xmm7
        divsd     %xmm6, %xmm7
        addsd     %xmm7, %xmm2
        mulsd     .L_2il0floatpacket.1(%rip), %xmm2
        addsd     %xmm2, %xmm1
        jmp       ..B1.15
..B1.14:
        movsd     -16(%rsp), %xmm1
        andps     .L_2il0floatpacket.2(%rip), %xmm1
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm2
        movsd     .L_2il0floatpacket.3(%rip), %xmm0
        movaps    %xmm0, %xmm4
        addsd     %xmm0, %xmm2
        sqrtsd    %xmm2, %xmm2
        movsd     .L_2il0floatpacket.0(%rip), %xmm3
        addsd     %xmm1, %xmm2
        mulsd     %xmm3, %xmm1
        divsd     %xmm2, %xmm4
        movsd     %xmm1, -16(%rsp)
        movl      $0, -16(%rsp)
        movsd     -16(%rsp), %xmm9
        subsd     %xmm9, %xmm1
        addsd     %xmm4, %xmm1
..B1.15:
        movaps    %xmm1, %xmm2
        movq      $0x3ff0000000000000, %rax
        addsd     %xmm9, %xmm2
        movsd     %xmm2, -16(%rsp)
        movl      $0, -16(%rsp)
        movsd     -16(%rsp), %xmm8
        movzwl    -10(%rsp), %ecx
        subsd     %xmm8, %xmm9
        andl      $32752, %ecx
        addsd     %xmm9, %xmm1
        shrl      $4, %ecx
        addl      $-1023, %ecx
        movslq    %ecx, %rdx
        negq      %rdx
        shlq      $52, %rdx
        addq      %rax, %rdx
        movq      %rdx, -32(%rsp)
        movsd     -32(%rsp), %xmm3
        mulsd     %xmm3, %xmm1
        mulsd     %xmm3, %xmm8
        movaps    %xmm1, %xmm4
        addsd     %xmm8, %xmm4
        movsd     %xmm4, -24(%rsp)
        movl      -20(%rsp), %edi
        shrl      $12, %edi
        movzbl    %dil, %edx
        jmp       ..B1.17
..B1.16:
        movsd     -16(%rsp), %xmm0
        andps     .L_2il0floatpacket.2(%rip), %xmm0
        movsd     %xmm0, -16(%rsp)
        movzwl    -10(%rsp), %eax
        movl      %eax, %ecx
        andl      $-32753, %eax
        andl      $32752, %ecx
        orl       $-49168, %eax
        movw      %ax, -10(%rsp)
        movsd     -16(%rsp), %xmm1
        movl      $0, -16(%rsp)
        movl      -12(%rsp), %edx
        movsd     -16(%rsp), %xmm8
        shrl      $4, %ecx
        subsd     %xmm8, %xmm1
        shrl      $12, %edx
        addl      $-1022, %ecx
        movzbl    %dl, %edx
        movsd     .L_2il0floatpacket.3(%rip), %xmm0
..B1.17:
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        pxor      %xmm2, %xmm2
        lea       32+_PL(%rip), %r8
        lea       16+_PL(%rip), %r9
        lea       24+_PL(%rip), %r11
        lea       _PL(%rip), %r10
        cvtss2sd  (%rdi,%rdx,4), %xmm2
        mulsd     %xmm2, %xmm8
        lea       8+_PL(%rip), %rdi
        mulsd     %xmm2, %xmm1
        subsd     %xmm0, %xmm8
        movaps    %xmm8, %xmm3
        movsd     (%r8), %xmm5
        addsd     %xmm1, %xmm3
        movaps    %xmm3, %xmm0
        mulsd     %xmm3, %xmm0
        mulsd     %xmm0, %xmm5
        movsd     (%r11), %xmm4
        mulsd     %xmm0, %xmm4
        addsd     (%r9), %xmm5
        mulsd     %xmm0, %xmm5
        addsd     (%rdi), %xmm4
        mulsd     %xmm0, %xmm4
        addsd     (%r10), %xmm5
        mulsd     %xmm0, %xmm5
        pxor      %xmm0, %xmm0
        cvtsi2sd  %ecx, %xmm0
        mulsd     %xmm3, %xmm4
        lea       _LN2(%rip), %rcx
        addsd     %xmm4, %xmm5
        movsd     (%rcx), %xmm6
        lea       8+_LN2(%rip), %rcx
        mulsd     %xmm0, %xmm6
        addsd     %xmm5, %xmm1
        movsd     (%rcx), %xmm7
        mulsd     %xmm7, %xmm0
        shlq      $4, %rdx
        movq      __libm_log_table_256@GOTPCREL(%rip), %rax
        shrl      $31, %esi
        addsd     (%rax,%rdx), %xmm6
        addsd     8(%rax,%rdx), %xmm0
        addsd     %xmm6, %xmm1
        addsd     %xmm8, %xmm0
        lea       ones(%rip), %rdx
        movsd     %xmm0, -16(%rsp)
        movsd     (%rdx,%rsi,8), %xmm8
        mulsd     %xmm8, %xmm0
        mulsd     %xmm8, %xmm1
        movsd     %xmm8, -24(%rsp)
        addsd     %xmm1, %xmm0
        ret       
..B1.18:
        movsd     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	asinh,@function
	.size	asinh,.-asinh
	.data
# -- End  asinh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
_SCALE:
	.long	0
	.long	1177550848
	.long	0
	.long	967835648
	.type	_SCALE,@object
	.size	_SCALE,16
	.align 16
_MINNORM:
	.long	0
	.long	1048576
	.long	0
	.long	2148532224
	.type	_MINNORM,@object
	.size	_MINNORM,16
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
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_PA:
	.long	1431655746
	.long	3217380693
	.long	858875226
	.long	1068708659
	.long	2809361764
	.long	3215383405
	.long	1241898124
	.long	1067392054
	.long	966918340
	.long	3214324669
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
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
