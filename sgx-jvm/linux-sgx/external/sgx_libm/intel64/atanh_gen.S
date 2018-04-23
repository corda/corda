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
	.file "atanh_gen.c"
	.text
..TXTST0:
# -- Begin  atanh
	.text
       .align    16,0x90
	.globl atanh
atanh:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_atanh.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %esi
        movl      %esi, %edx
        andl      $2147483647, %edx
        cmpl      $1072693248, %edx
        jae       ..B1.12
..B1.2:
        cmpl      $1068498944, %edx
        jae       ..B1.11
..B1.3:
        cmpl      $1012924416, %edx
        jae       ..B1.10
..B1.4:
        shrl      $31, %esi
        cmpl      $1048576, %edx
        jb        ..B1.6
..B1.5:
        lea       _ones(%rip), %rax
        movsd     (%rax), %xmm1
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm2
        mulsd     %xmm2, %xmm0
        ret       
..B1.6:
        movl      -8(%rsp), %eax
        orl       %eax, %edx
        je        ..B1.8
..B1.7:
        lea       _small_value_64(%rip), %rax
        movsd     -8(%rsp), %xmm0
        movsd     (%rax,%rsi,8), %xmm1
        mulsd     (%rax), %xmm1
        movsd     %xmm1, -40(%rsp)
        addsd     %xmm1, %xmm0
        ret       
..B1.8:
        movsd     -8(%rsp), %xmm0
..B1.9:
        ret       
..B1.10:
        movsd     -8(%rsp), %xmm4
        lea       40+_PA(%rip), %rax
        movaps    %xmm4, %xmm3
        lea       32+_PA(%rip), %rsi
        mulsd     %xmm4, %xmm3
        lea       24+_PA(%rip), %rdx
        movaps    %xmm3, %xmm1
        lea       16+_PA(%rip), %rdi
        mulsd     %xmm3, %xmm1
        lea       8+_PA(%rip), %rcx
        movsd     (%rax), %xmm0
        lea       _PA(%rip), %r8
        mulsd     %xmm1, %xmm0
        movsd     (%rsi), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm0
        mulsd     %xmm1, %xmm0
        addsd     (%rdi), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm0
        mulsd     %xmm3, %xmm0
        addsd     (%r8), %xmm2
        addsd     %xmm2, %xmm0
        mulsd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm0
        addsd     %xmm4, %xmm0
        ret       
..B1.11:
        movsd     -8(%rsp), %xmm9
        lea       8+_ones(%rip), %rdi
        andps     .L_2il0floatpacket.0(%rip), %xmm9
        lea       _ones(%rip), %r8
        movsd     %xmm9, -8(%rsp)
        movq      $0x3ff0000000000000, %r10
        andl      $-268435456, -8(%rsp)
        pxor      %xmm15, %xmm15
        movsd     -8(%rsp), %xmm12
        movsd     (%rdi), %xmm0
        movaps    %xmm12, %xmm6
        movaps    %xmm0, %xmm8
        lea       32+_PL(%rip), %rdi
        movsd     (%r8), %xmm4
        lea       16+_PL(%rip), %r8
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rax
        addsd     %xmm0, %xmm6
        subsd     %xmm12, %xmm9
        addsd     %xmm4, %xmm12
        movaps    %xmm6, %xmm2
        movaps    %xmm6, %xmm5
        movaps    %xmm9, %xmm3
        movaps    %xmm12, %xmm1
        shrl      $31, %esi
        addsd     %xmm9, %xmm2
        divsd     %xmm2, %xmm8
        movsd     %xmm8, -32(%rsp)
        pxor      %xmm2, %xmm2
        andl      $-268435456, -32(%rsp)
        movsd     -32(%rsp), %xmm10
        movaps    %xmm10, %xmm7
        subsd     %xmm10, %xmm8
        mulsd     %xmm10, %xmm6
        mulsd     %xmm10, %xmm12
        mulsd     %xmm8, %xmm5
        addsd     %xmm8, %xmm7
        addsd     %xmm4, %xmm6
        mulsd     %xmm7, %xmm3
        addsd     %xmm3, %xmm5
        addsd     %xmm5, %xmm6
        mulsd     %xmm6, %xmm7
        addsd     %xmm7, %xmm8
        mulsd     %xmm8, %xmm1
        addsd     %xmm10, %xmm8
        mulsd     %xmm8, %xmm9
        addsd     %xmm9, %xmm1
        movaps    %xmm1, %xmm11
        addsd     %xmm12, %xmm11
        movsd     %xmm11, -8(%rsp)
        movl      $0, -8(%rsp)
        movsd     -8(%rsp), %xmm3
        movl      -4(%rsp), %ecx
        subsd     %xmm3, %xmm12
        sarl      $20, %ecx
        addsd     %xmm12, %xmm1
        addl      $-1023, %ecx
        movslq    %ecx, %r9
        shlq      $52, %r9
        subq      %r9, %r10
        lea       _PL(%rip), %r9
        movq      %r10, -24(%rsp)
        lea       24+_PL(%rip), %r10
        movsd     -24(%rsp), %xmm13
        mulsd     %xmm13, %xmm1
        cvtsi2sd  %ecx, %xmm2
        mulsd     %xmm13, %xmm3
        movaps    %xmm1, %xmm14
        lea       _LN2(%rip), %rcx
        movsd     (%r10), %xmm13
        addsd     %xmm3, %xmm14
        movsd     %xmm14, -16(%rsp)
        movl      -12(%rsp), %r11d
        shrl      $12, %r11d
        movzbl    %r11b, %edx
        lea       8+_PL(%rip), %r11
        movsd     (%rdi), %xmm14
        cvtss2sd  (%rax,%rdx,4), %xmm15
        mulsd     %xmm15, %xmm3
        mulsd     %xmm15, %xmm1
        addsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm12
        shlq      $4, %rdx
        addsd     %xmm1, %xmm12
        movaps    %xmm12, %xmm0
        mulsd     %xmm12, %xmm0
        mulsd     %xmm0, %xmm14
        mulsd     %xmm0, %xmm13
        addsd     (%r8), %xmm14
        addsd     (%r11), %xmm13
        mulsd     %xmm0, %xmm14
        mulsd     %xmm0, %xmm13
        addsd     (%r9), %xmm14
        mulsd     %xmm12, %xmm13
        mulsd     %xmm0, %xmm14
        movsd     (%rcx), %xmm0
        lea       halfs(%rip), %rcx
        mulsd     %xmm2, %xmm0
        addsd     %xmm13, %xmm14
        movq      __libm_log_table_256@GOTPCREL(%rip), %rax
        addsd     %xmm14, %xmm1
        addsd     (%rax,%rdx), %xmm0
        movsd     (%rcx,%rsi,8), %xmm4
        lea       8+_LN2(%rip), %rcx
        movsd     %xmm4, -16(%rsp)
        addsd     %xmm1, %xmm0
        movsd     (%rcx), %xmm1
        mulsd     %xmm1, %xmm2
        addsd     8(%rax,%rdx), %xmm2
        movsd     %xmm2, -32(%rsp)
        addsd     %xmm2, %xmm3
        movsd     %xmm3, -8(%rsp)
        addsd     %xmm3, %xmm0
        mulsd     %xmm4, %xmm0
        ret       
..B1.12:
        jne       ..B1.15
..B1.13:
        cmpl      $0, -8(%rsp)
        je        ..B1.19
..B1.14:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        ret       
..B1.15:
        cmpl      $2146435072, %edx
        jb        ..B1.14
..B1.16:
        jne       ..B1.18
..B1.17:
        cmpl      $0, -8(%rsp)
        je        ..B1.14
..B1.18:
        lea       _ones(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        ret       
..B1.19:
        lea       _ones(%rip), %rax
        lea       _zeros(%rip), %rdx
        shrl      $31, %esi
        movsd     (%rax,%rsi,8), %xmm0
        divsd     (%rdx), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atanh,@function
	.size	atanh,.-atanh
	.data
# -- End  atanh
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 8
halfs:
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbfe00000
	.type	halfs,@object
	.size	halfs,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
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
	.long	1431655765
	.long	1070945621
	.long	2576980801
	.long	1070176665
	.long	2453616913
	.long	1069697316
	.long	1427436931
	.long	1069314503
	.long	2129349532
	.long	1068975486
	.long	1629438381
	.long	1068756329
	.type	_PA,@object
	.size	_PA,48
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
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
