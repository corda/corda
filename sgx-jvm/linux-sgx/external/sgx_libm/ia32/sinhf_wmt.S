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
	.file "sinhf_wmt.c"
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
# -- Begin  sinhf
	.text
       .align    16,0x90
	.globl sinhf
sinhf:
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
        movsd     608(%ebx), %xmm3
        movsd     576(%ebx), %xmm6
        movd      %xmm0, %ecx
        movl      $-2147483648, %edx
        psllq     $33, %xmm0
        psrlq     $4, %xmm0
        movapd    512(%ebx), %xmm4
        andl      %ecx, %edx
        andl      $2147418112, %ecx
        subl      $1052246016, %ecx
        cmpl      $67633152, %ecx
        jae       .L_2TAG_PACKET_0.0.3
        mulsd     %xmm0, %xmm3
        movapd    %xmm3, %xmm2
        addsd     %xmm6, %xmm3
        xorpd     %xmm0, %xmm0
        movd      %xmm3, %eax
        subsd     %xmm6, %xmm3
        movapd    528(%ebx), %xmm1
        xorpd     %xmm5, %xmm5
        subsd     %xmm3, %xmm2
        shrl      $16, %edx
        orl       $16352, %edx
        cmpl      $59244544, %ecx
        jae       .L_2TAG_PACKET_1.0.3
        movl      $31, %ecx
        andl      %eax, %ecx
        shrl      $1, %eax
        addl      %ecx, %ecx
        andl      $65520, %eax
        addl      %edx, %eax
        pinsrw    $3, %eax, %xmm0
        movl      $32704, %edx
        subl      %eax, %edx
        movapd    544(%ebx), %xmm3
        movapd    (%ebx,%ecx,8), %xmm7
        pinsrw    $3, %edx, %xmm5
        pshufd    $68, %xmm2, %xmm2
        shufpd    $0, %xmm5, %xmm0
        movapd    560(%ebx), %xmm6
        mulpd     %xmm2, %xmm4
        mulpd     %xmm2, %xmm1
        mulpd     %xmm2, %xmm3
        mulpd     %xmm7, %xmm0
        mulpd     %xmm2, %xmm4
        addpd     %xmm1, %xmm6
        mulpd     %xmm0, %xmm3
        addpd     %xmm6, %xmm4
        mulpd     %xmm3, %xmm4
        addpd     %xmm4, %xmm0
        pshufd    $78, %xmm0, %xmm4
        subpd     %xmm4, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        addl      $1052246016, %ecx
        cmpl      $1052246016, %ecx
        ja        .L_2TAG_PACKET_3.0.3
        cmpl      $956301312, %ecx
        jae       .L_2TAG_PACKET_4.0.3
        cmpl      $8388608, %ecx
        jae       .L_2TAG_PACKET_5.0.3
        movss     112(%esp), %xmm0
        mulss     %xmm0, %xmm0
.L_2TAG_PACKET_5.0.3:
        movss     112(%esp), %xmm0
        movl      $1283457024, %eax
        movd      %eax, %xmm1
        movl      $847249408, %ecx
        movd      %ecx, %xmm2
        mulss     %xmm0, %xmm1
        addss     %xmm1, %xmm0
        mulss     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        movl      $31, %ecx
        andl      %eax, %ecx
        shrl      $1, %eax
        addl      %ecx, %ecx
        andl      $65520, %eax
        addl      %edx, %eax
        pinsrw    $3, %eax, %xmm0
        movsd     544(%ebx), %xmm3
        pshufd    $68, %xmm2, %xmm2
        mulsd     (%ebx,%ecx,8), %xmm0
        mulsd     %xmm2, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm4
        mulsd     %xmm3, %xmm4
        addsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm4
        addsd     %xmm4, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        pextrw    $1, %xmm0, %eax
        andl      $32640, %eax
        movl      $128, %edx
        cmpl      $32640, %eax
        je        .L_2TAG_PACKET_6.0.3
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_4.0.3:
        psrlq     $29, %xmm0
        movss     592(%ebx), %xmm7
        movss     %xmm0, %xmm5
        mulss     %xmm0, %xmm0
        movss     596(%ebx), %xmm2
        movss     %xmm0, %xmm1
        mulss     %xmm0, %xmm7
        movl      $1042983595, %eax
        movd      %eax, %xmm3
        mulss     %xmm0, %xmm0
        addss     %xmm2, %xmm7
        movd      %edx, %xmm4
        mulss     %xmm3, %xmm1
        mulss     %xmm7, %xmm0
        addss     %xmm1, %xmm0
        orps      %xmm4, %xmm5
        mulss     %xmm5, %xmm0
        addss     %xmm5, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        cmpl      $2139095040, %ecx
        jae       .L_2TAG_PACKET_7.0.3
        movl      $2130706432, %eax
        movd      %eax, %xmm0
        orl       %edx, %eax
        movd      %eax, %xmm1
        mulss     %xmm1, %xmm0
        movl      $128, %edx
.L_2TAG_PACKET_6.0.3:
        movss     %xmm0, (%esp)
        movss     112(%esp), %xmm0
        subl      $32, %esp
        lea       112(%esp), %eax
        movl      %eax, (%esp)
        lea       112(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        flds      (%esp)
        jmp       .L_2TAG_PACKET_8.0.3
.L_2TAG_PACKET_7.0.3:
        movss     112(%esp), %xmm0
        movss     %xmm0, %xmm1
        pslld     $1, %xmm0
        psrld     $1, %xmm0
        mulss     %xmm1, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_2.0.3:
        movss     %xmm0, 24(%esp)
        flds      24(%esp)
.L_2TAG_PACKET_8.0.3:
        movl      40(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	sinhf,@function
	.size	sinhf,.-sinhf
	.data
# -- End  sinhf
	.text
# -- Begin  __libm_error_support
	.text
       .align    16,0x90
__libm_error_support:
# parameter 1: 4 + %esp
# parameter 2: 8 + %esp
# parameter 3: 12 + %esp
# parameter 4: 16 + %esp
..B3.1:
..L4:

        ret       
        .align    16,0x90
	.type	__libm_error_support,@function
	.size	__libm_error_support,.-__libm_error_support
	.data
# -- End  __libm_error_support
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	3541402996
	.long	1072716208
	.long	1533953344
	.long	1072648310
	.long	1828292879
	.long	1072739672
	.long	2728693978
	.long	1072604335
	.long	3490863953
	.long	1072763649
	.long	863738719
	.long	1072561303
	.long	1014845819
	.long	1072788152
	.long	3707479175
	.long	1072519192
	.long	828946858
	.long	1072813191
	.long	3706687593
	.long	1072477984
	.long	1853186616
	.long	1072838778
	.long	3716502172
	.long	1072437659
	.long	4112506593
	.long	1072864925
	.long	4076559943
	.long	1072398198
	.long	171030293
	.long	1072891646
	.long	2572866477
	.long	1072359583
	.long	926591435
	.long	1072918951
	.long	2990417245
	.long	1072321795
	.long	1276261410
	.long	1072946854
	.long	2191782032
	.long	1072284817
	.long	1617004845
	.long	1072975368
	.long	2966275557
	.long	1072248631
	.long	3577096743
	.long	1073004506
	.long	1110089947
	.long	1072213221
	.long	1453150082
	.long	1073034283
	.long	2571947539
	.long	1072178569
	.long	3712504873
	.long	1073064711
	.long	1944781191
	.long	1072144660
	.long	2956612997
	.long	1073095806
	.long	3907805044
	.long	1072111477
	.long	1719614413
	.long	1073127582
	.long	1719614413
	.long	1072079006
	.long	3907805044
	.long	1073160053
	.long	2956612997
	.long	1072047230
	.long	1944781191
	.long	1073193236
	.long	3712504873
	.long	1072016135
	.long	2571947539
	.long	1073227145
	.long	1453150082
	.long	1071985707
	.long	1110089947
	.long	1073261797
	.long	3577096743
	.long	1071955930
	.long	2966275557
	.long	1073297207
	.long	1617004845
	.long	1071926792
	.long	2191782032
	.long	1073333393
	.long	1276261410
	.long	1071898278
	.long	2990417245
	.long	1073370371
	.long	926591435
	.long	1071870375
	.long	2572866477
	.long	1073408159
	.long	171030293
	.long	1071843070
	.long	4076559943
	.long	1073446774
	.long	4112506593
	.long	1071816349
	.long	3716502172
	.long	1073486235
	.long	1853186616
	.long	1071790202
	.long	3706687593
	.long	1073526560
	.long	828946858
	.long	1071764615
	.long	3707479175
	.long	1073567768
	.long	1014845819
	.long	1071739576
	.long	863738719
	.long	1073609879
	.long	3490863953
	.long	1071715073
	.long	2728693978
	.long	1073652911
	.long	1828292879
	.long	1071691096
	.long	1533953344
	.long	1073696886
	.long	3541402996
	.long	1071667632
	.long	4289495988
	.long	1058308051
	.long	4289495988
	.long	1058308051
	.long	4277811695
	.long	1065758274
	.long	4277811695
	.long	3213241922
	.long	4277811695
	.long	1066806850
	.long	4277811695
	.long	3214290498
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	1127743488
	.long	0
	.long	3275227136
	.long	961547521
	.long	1007192201
	.long	1042983595
	.long	0
	.long	1697350398
	.long	2017924423
	.type	static_const_table,@object
	.size	static_const_table,616
	.data
	.section .note.GNU-stack, ""
# End
