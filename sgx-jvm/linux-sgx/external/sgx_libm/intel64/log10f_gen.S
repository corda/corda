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
	.file "log10f_gen.c"
	.text
..TXTST0:
# -- Begin  log10f
	.text
       .align    16,0x90
	.globl log10f
log10f:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_log10f.1:
..L2:

        movd      %xmm0, %edi
        movss     %xmm0, -8(%rsp)
        cmpl      $2139095040, %edi
        jae       ..B1.14
..B1.2:
        lea       -1065222144(%rdi), %eax
        cmpl      $196608, %eax
        jae       ..B1.7
..B1.3:
        cmpl      $1065353216, %edi
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
        movl      %edi, %eax
        movl      %edi, %esi
        shrl      $23, %eax
        andl      $8388607, %esi
        addl      $-127, %eax
        movl      %esi, -8(%rsp)
        cmpl      $8388608, %edi
        jae       ..B1.10
..B1.8:
        testl     %edi, %edi
        je        ..B1.22
..B1.9:
        movss     -8(%rsp), %xmm0
        mulss     .L_2il0floatpacket.1(%rip), %xmm0
        movd      %xmm0, %esi
        movss     %xmm0, -8(%rsp)
        movl      %esi, %eax
        movl      %esi, %edi
        shrl      $23, %eax
        addl      $-152, %eax
..B1.10:
        imull     $21846, %eax, %edx
        sarl      $15, %edx
        cmpl      $34, %eax
        ja        ..B1.13
..B1.11:
        andl      $-2, %edx
        lea       _exact_values(%rip), %rcx
        movslq    %edx, %rdx
        cmpl      (%rcx,%rdx,4), %edi
        jne       ..B1.13
..B1.12:
        movss     4(%rcx,%rdx,4), %xmm0
        ret       
..B1.13:
        shrl      $15, %edi
        orl       $1065353216, %esi
        movzbl    %dil, %r8d
        pxor      %xmm2, %xmm2
        movl      %esi, -8(%rsp)
        pxor      %xmm0, %xmm0
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rsi
        lea       16+_P(%rip), %rdx
        cvtss2sd  -8(%rsp), %xmm2
        cvtss2sd  (%rsi,%r8,4), %xmm0
        mulsd     %xmm0, %xmm2
        lea       8+_P(%rip), %rcx
        movsd     (%rdx), %xmm0
        lea       _P(%rip), %r9
        movq      __libm_logf_table_256@GOTPCREL(%rip), %rdi
        pxor      %xmm3, %xmm3
        cvtsi2sd  %eax, %xmm3
        addsd     .L_2il0floatpacket.2(%rip), %xmm2
        mulsd     %xmm2, %xmm0
        lea       _LOG102(%rip), %rax
        mulsd     (%rax), %xmm3
        addsd     (%rcx), %xmm0
        mulsd     %xmm2, %xmm0
        movsd     (%rdi,%r8,8), %xmm1
        mulsd     %xmm2, %xmm0
        addsd     %xmm1, %xmm2
        mulsd     (%r9), %xmm2
        addsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.14:
        movl      %edi, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.20
..B1.15:
        testl     $-2147483648, %edi
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
	.type	log10f,@function
	.size	log10f,.-log10f
	.data
# -- End  log10f
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
	.long	354870542
	.long	1071369083
	.long	354784454
	.long	3217804155
	.long	1678399179
	.long	1069713319
	.long	656012052
	.long	3216755581
	.long	509767151
	.long	1068907598
	.long	3001134819
	.long	3216124527
	.type	_Q,@object
	.size	_Q,48
	.align 4
_exact_values:
	.long	4294967295
	.long	4294967295
	.long	1092616192
	.long	1065353216
	.long	1120403456
	.long	1073741824
	.long	1148846080
	.long	1077936128
	.long	1176256512
	.long	1082130432
	.long	1203982336
	.long	1084227584
	.long	1232348160
	.long	1086324736
	.long	1259902592
	.long	1088421888
	.long	1287568416
	.long	1090519040
	.long	1315859240
	.long	1091567616
	.long	4294967295
	.long	4294967295
	.long	1343554297
	.long	1092616192
	.type	_exact_values,@object
	.size	_exact_values,96
	.align 4
_P:
	.long	354870542
	.long	1071369083
	.long	1669757279
	.long	3217804158
	.long	3421439185
	.long	1069713323
	.type	_P,@object
	.size	_P,24
	.align 4
_LOG102:
	.long	1352628735
	.long	1070810131
	.type	_LOG102,@object
	.size	_LOG102,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
