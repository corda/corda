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
	.file "erff.c"
	.text
..TXTST0:
# -- Begin  erff
	.text
       .align    16,0x90
	.globl erff
erff:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_erff.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -16(%rsp)
        movl      %edx, %eax
        andl      $2147483647, %edx
        andl      $-2147483648, %eax
        orl       $1065353216, %eax
        movl      %eax, -24(%rsp)
        cmpl      $2139095040, %edx
        movss     -24(%rsp), %xmm0
        jge       ..B1.8
..B1.2:
        cmpl      $1081791744, %edx
        jge       ..B1.7
..B1.3:
        cmpl      $1065353216, %edx
        jl        ..B1.5
..B1.4:
        movl      %edx, %eax
        andl      $8388607, %edx
        orl       $1065353216, %edx
        pxor      %xmm4, %xmm4
        movl      %edx, -24(%rsp)
        lea       _dbA(%rip), %rsi
        shrl      $23, %eax
        cvtss2sd  -24(%rsp), %xmm4
        cvtss2sd  %xmm0, %xmm0
        subsd     .L_2il0floatpacket.0(%rip), %xmm4
        movaps    %xmm4, %xmm3
        lea       -1016(,%rax,8), %edx
        mulsd     %xmm4, %xmm3
        lea       -508(%rdx,%rax,4), %ecx
        movslq    %ecx, %rcx
        movaps    %xmm3, %xmm7
        mulsd     %xmm3, %xmm7
        movsd     152(%rsi,%rcx,8), %xmm6
        mulsd     %xmm4, %xmm6
        movsd     120(%rsi,%rcx,8), %xmm8
        mulsd     %xmm4, %xmm8
        addsd     144(%rsi,%rcx,8), %xmm6
        movsd     136(%rsi,%rcx,8), %xmm5
        mulsd     %xmm3, %xmm6
        addsd     112(%rsi,%rcx,8), %xmm8
        mulsd     %xmm4, %xmm5
        mulsd     %xmm3, %xmm8
        addsd     %xmm5, %xmm6
        movsd     88(%rsi,%rcx,8), %xmm9
        movsd     104(%rsi,%rcx,8), %xmm2
        mulsd     %xmm4, %xmm9
        addsd     128(%rsi,%rcx,8), %xmm6
        mulsd     %xmm4, %xmm2
        mulsd     %xmm7, %xmm6
        addsd     80(%rsi,%rcx,8), %xmm9
        addsd     %xmm2, %xmm8
        mulsd     %xmm3, %xmm9
        addsd     96(%rsi,%rcx,8), %xmm8
        movsd     72(%rsi,%rcx,8), %xmm1
        addsd     %xmm6, %xmm8
        mulsd     %xmm4, %xmm1
        mulsd     %xmm7, %xmm8
        addsd     %xmm1, %xmm9
        addsd     64(%rsi,%rcx,8), %xmm9
        addsd     %xmm8, %xmm9
        mulsd     %xmm0, %xmm9
        cvtsd2ss  %xmm9, %xmm9
        jmp       ..B1.6
..B1.5:
        pxor      %xmm3, %xmm3
        lea       56+_dbA(%rip), %rdi
        lea       48+_dbA(%rip), %rax
        lea       40+_dbA(%rip), %r8
        lea       32+_dbA(%rip), %rdx
        lea       24+_dbA(%rip), %r9
        lea       16+_dbA(%rip), %rcx
        lea       8+_dbA(%rip), %r10
        movsd     (%rdi), %xmm2
        lea       _dbA(%rip), %rsi
        movsd     (%rax), %xmm9
        cvtss2sd  -16(%rsp), %xmm3
        movaps    %xmm3, %xmm1
        mulsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm9
        addsd     (%r8), %xmm2
        addsd     (%rdx), %xmm9
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm9
        addsd     (%r9), %xmm2
        addsd     (%rcx), %xmm9
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm9
        addsd     (%r10), %xmm2
        addsd     (%rsi), %xmm9
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm9
        mulsd     %xmm3, %xmm9
        cvtsd2ss  %xmm9, %xmm9
..B1.6:
        movaps    %xmm9, %xmm0
        ret       
..B1.7:
        cvtss2sd  %xmm0, %xmm0
        lea       256+_dbA(%rip), %rax
        mulsd     (%rax), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.8:
        movss     -16(%rsp), %xmm1
        addss     %xmm1, %xmm1
        je        ..L3
        movaps    %xmm1, %xmm0
..L3:
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	erff,@function
	.size	erff,.-erff
	.data
# -- End  erff
	.section .rodata, "a"
	.align 16
	.align 16
_dbA:
	.long	1346373606
	.long	1072827863
	.long	1710987328
	.long	3218608756
	.long	2039146822
	.long	1069343472
	.long	2052989911
	.long	3214639770
	.long	3741021613
	.long	1064657550
	.long	2828932584
	.long	3209415657
	.long	556515485
	.long	1058898537
	.long	1493771156
	.long	3202627648
	.long	1460823802
	.long	1072622165
	.long	647757120
	.long	1069445687
	.long	1478820123
	.long	3217479081
	.long	827266439
	.long	1069662880
	.long	4153551678
	.long	3215381932
	.long	3249389370
	.long	3213783616
	.long	3875823656
	.long	1066650297
	.long	2479749796
	.long	3212012677
	.long	4079794357
	.long	3210967401
	.long	3292960271
	.long	1062989018
	.long	1338933997
	.long	3204757561
	.long	4209598023
	.long	3207545998
	.long	2886434981
	.long	1072693201
	.long	1721637260
	.long	1060257935
	.long	960688093
	.long	3210436681
	.long	1128797080
	.long	1064950642
	.long	798939459
	.long	3213958757
	.long	937205111
	.long	1067493282
	.long	3504975339
	.long	3215465534
	.long	3388113641
	.long	1068080152
	.long	9542307
	.long	3215088353
	.long	1310184383
	.long	1065080747
	.long	3264801679
	.long	1066837484
	.long	3394984936
	.long	3214223095
	.long	4294967295
	.long	1072693247
	.type	_dbA,@object
	.size	_dbA,264
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3ff80000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
