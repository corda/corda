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
	.file "tanhf_wmt.c"
	.text
..TXTST0:
# -- Begin  static_func
	.text
       .align    16,0x90
static_func:
..B1.1:
..L1:

        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        lea       static_const_table@GOTOFF(%eax), %eax
        ret       
        .align    16,0x90
	.type	static_func,@function
	.size	static_func,.-static_func
	.data
# -- End  static_func
	.text
# -- Begin  tanhf
	.text
       .align    16,0x90
	.globl tanhf
tanhf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %ebx, 40(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     112(%esp), %xmm0
        xorpd     %xmm1, %xmm1
        movsd     304(%ebx), %xmm3
        movl      $14336, %eax
        pinsrw    $3, %eax, %xmm1
        movsd     288(%ebx), %xmm6
        pextrw    $1, %xmm0, %ecx
        psllq     $33, %xmm0
        psrlq     $4, %xmm0
        paddw     %xmm0, %xmm1
        movapd    256(%ebx), %xmm4
        movl      $32768, %edx
        andl      %ecx, %edx
        andl      $32767, %ecx
        subl      $15744, %ecx
        cmpl      $1056, %ecx
        jae       .L_2TAG_PACKET_0.0.2
        mulsd     %xmm1, %xmm3
        cvtsd2si  %xmm3, %eax
        movapd    %xmm3, %xmm2
        addsd     %xmm6, %xmm3
        xorpd     %xmm0, %xmm0
        subsd     %xmm6, %xmm3
        movapd    272(%ebx), %xmm1
        subsd     %xmm3, %xmm2
        movl      $31, %ecx
        andl      %eax, %ecx
        movsd     (%ebx,%ecx,8), %xmm5
        shrl      $1, %eax
        andl      $65520, %eax
        subl      $16368, %eax
        negl      %eax
        pshufd    $68, %xmm2, %xmm2
        pinsrw    $3, %eax, %xmm0
        mulpd     %xmm2, %xmm4
        mulpd     %xmm2, %xmm1
        movsd     312(%ebx), %xmm7
        xorpd     %xmm3, %xmm3
        mulpd     %xmm2, %xmm4
        mulpd     %xmm2, %xmm2
        mulsd     %xmm5, %xmm0
        addpd     %xmm4, %xmm1
        pinsrw    $3, %edx, %xmm3
        mulsd     %xmm2, %xmm1
        movapd    %xmm7, %xmm6
        pshufd    $238, %xmm1, %xmm2
        addsd     %xmm0, %xmm7
        addsd     %xmm2, %xmm1
        subsd     %xmm0, %xmm6
        mulsd     %xmm1, %xmm0
        addsd     %xmm0, %xmm7
        subsd     %xmm0, %xmm6
        xorpd     %xmm3, %xmm7
        divsd     %xmm7, %xmm6
        cvtsd2ss  %xmm6, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_0.0.2:
        addl      $1152, %ecx
        cmpl      $1152, %ecx
        jae       .L_2TAG_PACKET_2.0.2
        movl      $-1117975087, %eax
        movd      %eax, %xmm1
        movss     112(%esp), %xmm0
        movl      $-1096111445, %ecx
        movd      %ecx, %xmm3
        movl      $1040746633, %edx
        movd      %edx, %xmm2
        pshufd    $68, %xmm0, %xmm4
        mulss     %xmm0, %xmm0
        mulss     %xmm0, %xmm1
        mulss     %xmm0, %xmm3
        mulss     %xmm0, %xmm0
        addss     %xmm2, %xmm1
        mulss     %xmm1, %xmm0
        addss     %xmm3, %xmm0
        mulss     %xmm4, %xmm0
        addss     %xmm4, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_2.0.2:
        addl      $14592, %ecx
        cmpl      $15744, %ecx
        jae       .L_2TAG_PACKET_3.0.2
        movss     112(%esp), %xmm0
        cmpl      $128, %ecx
        jb        .L_2TAG_PACKET_4.0.2
        movl      $1333788672, %eax
        movd      %eax, %xmm2
        mulss     %xmm0, %xmm2
        addss     %xmm0, %xmm2
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_4.0.2:
        movss     %xmm0, %xmm2
        mulss     %xmm2, %xmm2
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_3.0.2:
        cmpl      $32640, %ecx
        jae       .L_2TAG_PACKET_5.0.2
        movl      $796917760, %eax
        movd      %eax, %xmm2
        movss     %xmm2, %xmm3
        mulss     %xmm2, %xmm2
        addss     %xmm3, %xmm2
.L_2TAG_PACKET_6.0.2:
        xorps     %xmm0, %xmm0
        orl       $16256, %edx
        pinsrw    $1, %edx, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_5.0.2:
        movl      112(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_6.0.2
        movss     112(%esp), %xmm0
        addss     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_1.0.2:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	tanhf,@function
	.size	tanhf,.-tanhf
	.data
# -- End  tanhf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	1072693248
	.long	1533953344
	.long	1072648310
	.long	2728693978
	.long	1072604335
	.long	863738719
	.long	1072561303
	.long	3707479175
	.long	1072519192
	.long	3706687593
	.long	1072477984
	.long	3716502172
	.long	1072437659
	.long	4076559943
	.long	1072398198
	.long	2572866477
	.long	1072359583
	.long	2990417245
	.long	1072321795
	.long	2191782032
	.long	1072284817
	.long	2966275557
	.long	1072248631
	.long	1110089947
	.long	1072213221
	.long	2571947539
	.long	1072178569
	.long	1944781191
	.long	1072144660
	.long	3907805044
	.long	1072111477
	.long	1719614413
	.long	1072079006
	.long	2956612997
	.long	1072047230
	.long	3712504873
	.long	1072016135
	.long	1453150082
	.long	1071985707
	.long	3577096743
	.long	1071955930
	.long	1617004845
	.long	1071926792
	.long	1276261410
	.long	1071898278
	.long	926591435
	.long	1071870375
	.long	171030293
	.long	1071843070
	.long	4112506593
	.long	1071816349
	.long	1853186616
	.long	1071790202
	.long	828946858
	.long	1071764615
	.long	1014845819
	.long	1071739576
	.long	3490863953
	.long	1071715073
	.long	1828292879
	.long	1071691096
	.long	3541402996
	.long	1071667632
	.long	1874480759
	.long	1044624043
	.long	4286760334
	.long	1060028349
	.long	3607404735
	.long	3200019208
	.long	4277811695
	.long	3214290498
	.long	0
	.long	1127743488
	.long	0
	.long	3275227136
	.long	1697350398
	.long	1079448903
	.long	0
	.long	1072693248
	.type	static_const_table,@object
	.size	static_const_table,320
	.data
	.section .note.GNU-stack, ""
# End
