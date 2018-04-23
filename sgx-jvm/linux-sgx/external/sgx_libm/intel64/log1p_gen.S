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
	.file "log1p_gen.c"
	.text
..TXTST0:
# -- Begin  log1p
	.text
       .align    16,0x90
	.globl log1p
log1p:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log1p.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movl      -4(%rsp), %edi
        movl      %edi, %edx
        andl      $2147483647, %edx
        cmpl      $2146435072, %edx
        jae       ..B1.24
..B1.2:
        cmpl      $-1074790400, %edi
        jae       ..B1.20
..B1.3:
        cmpl      $1068646400, %edx
        jae       ..B1.16
..B1.4:
        cmpl      $1066401792, %edx
        jae       ..B1.15
..B1.5:
        cmpl      $1059061760, %edx
        jae       ..B1.14
..B1.6:
        cmpl      $1012924416, %edx
        jae       ..B1.13
..B1.7:
        cmpl      $1048576, %edx
        jb        ..B1.9
..B1.8:
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm1
        subsd     %xmm1, %xmm0
        ret       
..B1.9:
        movl      -8(%rsp), %eax
        orl       %eax, %edx
        je        ..B1.11
..B1.10:
        lea       _small_value_64(%rip), %rax
        movsd     -8(%rsp), %xmm0
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm1
        movsd     %xmm1, -40(%rsp)
        subsd     %xmm1, %xmm0
        ret       
..B1.11:
        movsd     -8(%rsp), %xmm0
..B1.12:
        ret       
..B1.13:
        movsd     -8(%rsp), %xmm3
        lea       24+_Q3(%rip), %rax
        movaps    %xmm3, %xmm1
        lea       16+_Q3(%rip), %rcx
        mulsd     %xmm3, %xmm1
        lea       8+_Q3(%rip), %rdx
        movsd     (%rax), %xmm0
        lea       _Q3(%rip), %rsi
        movsd     (%rcx), %xmm2
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm2
        addsd     (%rdx), %xmm0
        addsd     (%rsi), %xmm2
        mulsd     %xmm1, %xmm0
        mulsd     %xmm3, %xmm2
        movsd     %xmm1, -16(%rsp)
        addsd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm0
        addsd     %xmm3, %xmm0
        ret       
..B1.14:
        movsd     -8(%rsp), %xmm3
        lea       56+_Q2(%rip), %rdx
        movaps    %xmm3, %xmm5
        lea       64+_Q2(%rip), %r8
        mulsd     %xmm3, %xmm5
        lea       40+_Q2(%rip), %rcx
        movsd     (%rdx), %xmm6
        lea       48+_Q2(%rip), %r9
        movsd     (%r8), %xmm4
        lea       24+_Q2(%rip), %rsi
        mulsd     %xmm5, %xmm6
        lea       32+_Q2(%rip), %r10
        mulsd     %xmm5, %xmm4
        addsd     (%rcx), %xmm6
        addsd     (%r9), %xmm4
        mulsd     %xmm5, %xmm6
        mulsd     %xmm5, %xmm4
        addsd     (%rsi), %xmm6
        addsd     (%r10), %xmm4
        mulsd     %xmm5, %xmm6
        mulsd     %xmm5, %xmm4
        xorl      %eax, %eax
        movaps    %xmm3, %xmm10
        movl      %eax, -8(%rsp)
        lea       8+_Q2(%rip), %rdi
        movsd     -8(%rsp), %xmm0
        lea       16+_Q2(%rip), %r11
        movaps    %xmm0, %xmm1
        movaps    %xmm0, %xmm7
        mulsd     %xmm0, %xmm7
        subsd     %xmm0, %xmm10
        addsd     (%rdi), %xmm6
        addsd     (%r11), %xmm4
        addsd     %xmm10, %xmm1
        mulsd     %xmm3, %xmm6
        mulsd     %xmm5, %xmm4
        mulsd     %xmm10, %xmm1
        addsd     %xmm4, %xmm6
        movaps    %xmm0, %xmm2
        mulsd     %xmm10, %xmm2
        mulsd     %xmm5, %xmm6
        addsd     %xmm1, %xmm2
        addsd     %xmm6, %xmm10
        movsd     %xmm7, -16(%rsp)
        movl      %eax, -16(%rsp)
        lea       _Q2(%rip), %rax
        movsd     -16(%rsp), %xmm8
        subsd     %xmm8, %xmm7
        movsd     (%rax), %xmm9
        addsd     %xmm2, %xmm7
        mulsd     %xmm9, %xmm7
        mulsd     %xmm8, %xmm9
        addsd     %xmm7, %xmm10
        addsd     %xmm9, %xmm0
        movsd     %xmm0, -8(%rsp)
        addsd     %xmm10, %xmm0
        movsd     %xmm10, -24(%rsp)
        ret       
..B1.15:
        movsd     -8(%rsp), %xmm12
        lea       96+_Q1(%rip), %rcx
        movaps    %xmm12, %xmm1
        lea       88+_Q1(%rip), %r11
        mulsd     %xmm12, %xmm1
        lea       _TWO_32P(%rip), %rdx
        movsd     (%rcx), %xmm2
        lea       80+_Q1(%rip), %rsi
        movsd     (%r11), %xmm7
        lea       64+_Q1(%rip), %rdi
        mulsd     %xmm1, %xmm2
        lea       56+_Q1(%rip), %rcx
        mulsd     %xmm1, %xmm7
        addsd     (%rsi), %xmm2
        movsd     (%rdx), %xmm4
        lea       72+_Q1(%rip), %rdx
        mulsd     %xmm1, %xmm2
        lea       48+_Q1(%rip), %r8
        addsd     (%rdx), %xmm7
        addsd     (%rdi), %xmm2
        mulsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm2
        addsd     (%rcx), %xmm7
        addsd     (%r8), %xmm2
        mulsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm2
        lea       40+_Q1(%rip), %rsi
        lea       32+_Q1(%rip), %r9
        lea       24+_Q1(%rip), %rdi
        lea       _TWO_32(%rip), %rax
        movaps    %xmm12, %xmm0
        movaps    %xmm12, %xmm6
        mulsd     %xmm4, %xmm0
        lea       16+_Q1(%rip), %r10
        addsd     (%rsi), %xmm7
        mulsd     (%rax), %xmm6
        addsd     (%r9), %xmm2
        mulsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm2
        addsd     (%rdi), %xmm7
        addsd     (%r10), %xmm2
        mulsd     %xmm1, %xmm7
        mulsd     %xmm1, %xmm2
        lea       8+_Q1(%rip), %r8
        lea       112+_Q1(%rip), %r9
        movsd     %xmm0, -40(%rsp)
        movaps    %xmm12, %xmm3
        movsd     -40(%rsp), %xmm5
        lea       104+_Q1(%rip), %r10
        movaps    %xmm4, %xmm9
        subsd     %xmm6, %xmm5
        addsd     (%r8), %xmm7
        subsd     %xmm5, %xmm3
        mulsd     %xmm12, %xmm7
        mulsd     %xmm3, %xmm12
        addsd     %xmm7, %xmm2
        movsd     (%r9), %xmm11
        movaps    %xmm5, %xmm15
        movaps    %xmm11, %xmm8
        mulsd     %xmm5, %xmm11
        mulsd     %xmm3, %xmm8
        mulsd     %xmm3, %xmm15
        addsd     (%r10), %xmm11
        addsd     %xmm8, %xmm2
        addsd     %xmm12, %xmm15
        movaps    %xmm11, %xmm10
        movaps    %xmm15, %xmm14
        movsd     (%rax), %xmm13
        addsd     %xmm2, %xmm10
        mulsd     %xmm10, %xmm9
        mulsd     %xmm13, %xmm10
        movsd     %xmm9, -40(%rsp)
        movsd     -40(%rsp), %xmm0
        movsd     %xmm5, -8(%rsp)
        subsd     %xmm10, %xmm0
        movaps    %xmm5, %xmm10
        subsd     %xmm0, %xmm11
        mulsd     %xmm5, %xmm10
        addsd     %xmm11, %xmm2
        addsd     %xmm10, %xmm14
        mulsd     %xmm1, %xmm2
        mulsd     %xmm14, %xmm4
        mulsd     %xmm13, %xmm14
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm4
        movsd     %xmm3, -24(%rsp)
        subsd     %xmm14, %xmm4
        movsd     %xmm1, -16(%rsp)
        subsd     %xmm4, %xmm10
        addsd     %xmm15, %xmm10
        mulsd     %xmm0, %xmm10
        mulsd     %xmm4, %xmm0
        addsd     %xmm2, %xmm10
        addsd     %xmm5, %xmm0
        addsd     %xmm3, %xmm10
        movsd     %xmm0, -32(%rsp)
        addsd     %xmm10, %xmm0
        ret       
..B1.16:
        cmpl      $1130364928, %edx
        jae       ..B1.18
..B1.17:
        lea       _ones(%rip), %rcx
        pxor      %xmm1, %xmm1
        movsd     -8(%rsp), %xmm7
        xorl      %esi, %esi
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rdi
        lea       8+_ones(%rip), %r8
        movl      %esi, -8(%rsp)
        lea       _TWO_32(%rip), %r9
        movsd     (%rcx), %xmm0
        movsd     -8(%rsp), %xmm5
        addsd     %xmm7, %xmm0
        subsd     %xmm5, %xmm7
        movsd     %xmm0, -32(%rsp)
        movl      -28(%rsp), %eax
        movl      %eax, %edx
        andl      $2146435072, %edx
        movl      %eax, %ecx
        negl      %edx
        shrl      $12, %eax
        addl      $2145386496, %edx
        movl      %edx, -28(%rsp)
        movzbl    %al, %edx
        movl      %esi, -32(%rsp)
        movsd     (%r8), %xmm2
        movsd     (%r9), %xmm3
        cvtss2sd  (%rdi,%rdx,4), %xmm1
        mulsd     -32(%rsp), %xmm1
        mulsd     %xmm1, %xmm5
        addsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm7
        addsd     %xmm2, %xmm5
        movaps    %xmm5, %xmm4
        movsd     %xmm5, -16(%rsp)
        addsd     %xmm7, %xmm4
        movsd     %xmm4, -8(%rsp)
        addsd     %xmm3, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm6
        shrl      $20, %ecx
        subsd     (%r9), %xmm6
        movsd     %xmm7, -24(%rsp)
        addl      $-1023, %ecx
        movsd     %xmm2, -32(%rsp)
        subsd     %xmm6, %xmm5
        addsd     %xmm5, %xmm7
        jmp       ..B1.19
..B1.18:
        movl      %edx, %ecx
        pxor      %xmm0, %xmm0
        shrl      $12, %edx
        andl      $1048575, %edi
        movzbl    %dl, %edx
        orl       $1072693248, %edi
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rax
        lea       8+_ones(%rip), %r8
        movl      %edi, -4(%rsp)
        shlq      $32, %rdi
        movl      -8(%rsp), %esi
        orq       %rsi, %rdi
        cvtss2sd  (%rax,%rdx,4), %xmm0
        movq      %rdi, -24(%rsp)
        movl      $0, -8(%rsp)
        movsd     -8(%rsp), %xmm6
        movsd     -24(%rsp), %xmm7
        shrl      $20, %ecx
        subsd     %xmm6, %xmm7
        mulsd     %xmm0, %xmm6
        mulsd     %xmm0, %xmm7
        addsd     (%r8), %xmm6
        movsd     %xmm0, -32(%rsp)
        addl      $-1023, %ecx
..B1.19:
        movaps    %xmm6, %xmm0
        lea       32+_P(%rip), %rsi
        lea       24+_P(%rip), %r9
        lea       16+_P(%rip), %rdi
        lea       8+_P(%rip), %r10
        lea       _P(%rip), %r8
        lea       8+_LN2(%rip), %r11
        addsd     %xmm7, %xmm0
        movaps    %xmm0, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     (%rsi), %xmm3
        mulsd     %xmm1, %xmm3
        movsd     (%r9), %xmm2
        mulsd     %xmm1, %xmm2
        addsd     (%rdi), %xmm3
        mulsd     %xmm1, %xmm3
        addsd     (%r10), %xmm2
        mulsd     %xmm0, %xmm2
        addsd     (%r8), %xmm3
        pxor      %xmm0, %xmm0
        cvtsi2sd  %ecx, %xmm0
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm2
        lea       _LN2(%rip), %rcx
        addsd     %xmm2, %xmm3
        movsd     (%rcx), %xmm4
        addsd     %xmm3, %xmm7
        mulsd     %xmm0, %xmm4
        movsd     (%r11), %xmm5
        mulsd     %xmm5, %xmm0
        shlq      $4, %rdx
        movq      __libm_log_table_256@GOTPCREL(%rip), %rax
        movsd     %xmm1, -16(%rsp)
        addsd     (%rax,%rdx), %xmm4
        addsd     8(%rax,%rdx), %xmm0
        addsd     %xmm4, %xmm7
        addsd     %xmm6, %xmm0
        movsd     %xmm0, -8(%rsp)
        addsd     %xmm7, %xmm0
        movsd     %xmm7, -24(%rsp)
        ret       
..B1.20:
        lea       _zeros(%rip), %rax
        addl      $-1072693248, %edx
        orl       -8(%rsp), %edx
        movsd     (%rax), %xmm1
        jne       ..B1.22
..B1.21:
        lea       8+_ones(%rip), %rax
        movsd     (%rax), %xmm0
        divsd     %xmm1, %xmm0
        ret       
..B1.22:
        lea       _infs(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm1, %xmm0
..B1.23:
        ret       
..B1.24:
        addl      $1048576, %edi
        orl       -8(%rsp), %edi
        je        ..B1.27
..B1.25:
        lea       _ones(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
..B1.26:
        ret       
..B1.27:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	log1p,@function
	.size	log1p,.-log1p
	.data
# -- End  log1p
	.section .rodata, "a"
	.align 4
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_Q3:
	.long	0
	.long	3219128320
	.long	1431655765
	.long	1070945621
	.long	55924054
	.long	3218079744
	.long	2711205044
	.long	1070176665
	.type	_Q3,@object
	.size	_Q3,32
	.align 4
_Q2:
	.long	0
	.long	3219128320
	.long	1431655765
	.long	1070945621
	.long	4294967292
	.long	3218079743
	.long	2577017633
	.long	1070176665
	.long	1431726806
	.long	3217380693
	.long	2027016470
	.long	1069697316
	.long	3108735575
	.long	3217031167
	.long	1449976333
	.long	1069315434
	.long	60833854
	.long	3216612762
	.type	_Q2,@object
	.size	_Q2,72
	.align 4
_Q1:
	.long	3234281536
	.long	3150348867
	.long	1430600241
	.long	1047876949
	.long	4294967294
	.long	3218079743
	.long	2576981961
	.long	1070176665
	.long	1431660017
	.long	3217380693
	.long	2452398325
	.long	1069697316
	.long	4287730462
	.long	3217031167
	.long	2576343554
	.long	1069314503
	.long	1474253516
	.long	3216611738
	.long	2720006255
	.long	1068975428
	.long	1874652295
	.long	3216331940
	.long	2893691300
	.long	1068761010
	.long	1325424864
	.long	3216153938
	.long	0
	.long	3219128320
	.long	0
	.long	1070945621
	.type	_Q1,@object
	.size	_Q1,120
	.align 4
_TWO_32P:
	.long	1048576
	.long	1106247680
	.type	_TWO_32P,@object
	.size	_TWO_32P,8
	.align 4
_TWO_32:
	.long	0
	.long	1106247680
	.type	_TWO_32,@object
	.size	_TWO_32,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_P:
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
	.type	_P,@object
	.size	_P,40
	.align 4
_LN2:
	.long	897137782
	.long	1038760431
	.long	4276092928
	.long	1072049730
	.type	_LN2,@object
	.size	_LN2,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
