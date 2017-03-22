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
	.file "exp_gen.c"
	.text
..TXTST0:
# -- Begin  exp
	.text
       .align    16,0x90
	.globl exp
exp:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_exp.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movsd     %xmm0, 8(%rsp)
..B1.2:
        unpcklpd  %xmm0, %xmm0
        movapd    cv(%rip), %xmm1
        movapd    Shifter(%rip), %xmm6
        movapd    16+cv(%rip), %xmm2
        movapd    32+cv(%rip), %xmm3
        pextrw    $3, %xmm0, %eax
        andl      $32767, %eax
        movl      $16527, %edx
        subl      %eax, %edx
        subl      $15504, %eax
        orl       %eax, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_0.0.2
        lea       Tbl_addr(%rip), %r8
        mulpd     %xmm0, %xmm1
        addpd     %xmm6, %xmm1
        movapd    %xmm1, %xmm7
        subpd     %xmm6, %xmm1
        mulpd     %xmm1, %xmm2
        movapd    64+cv(%rip), %xmm4
        mulpd     %xmm1, %xmm3
        movapd    80+cv(%rip), %xmm5
        subpd     %xmm2, %xmm0
        movd      %xmm7, %eax
        movl      %eax, %ecx
        andl      $63, %ecx
        shll      $4, %ecx
        sarl      $6, %eax
        movl      %eax, %edx
        movdqa    mmask(%rip), %xmm6
        pand      %xmm6, %xmm7
        movdqa    bias(%rip), %xmm6
        paddq     %xmm6, %xmm7
        psllq     $46, %xmm7
        subpd     %xmm3, %xmm0
        movapd    (%rcx,%r8), %xmm2
        mulpd     %xmm0, %xmm4
        movapd    %xmm0, %xmm6
        movapd    %xmm0, %xmm1
        mulpd     %xmm6, %xmm6
        mulpd     %xmm6, %xmm0
        addpd     %xmm4, %xmm5
        mulsd     %xmm6, %xmm0
        mulpd     48+cv(%rip), %xmm6
        addsd     %xmm2, %xmm1
        unpckhpd  %xmm2, %xmm2
        mulpd     %xmm5, %xmm0
        addsd     %xmm0, %xmm1
        orpd      %xmm7, %xmm2
        unpckhpd  %xmm0, %xmm0
        addsd     %xmm1, %xmm0
        addsd     %xmm6, %xmm0
        addl      $894, %edx
        cmpl      $1916, %edx
        ja        .L_2TAG_PACKET_1.0.2
        mulsd     %xmm2, %xmm0
        addsd     %xmm2, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_1.0.2:
        xorpd     %xmm3, %xmm3
        movapd    ALLONES(%rip), %xmm4
        movl      $-1022, %edx
        subl      %eax, %edx
        movd      %edx, %xmm5
        psllq     %xmm5, %xmm4
        movl      %eax, %ecx
        sarl      $1, %eax
        pinsrw    $3, %eax, %xmm3
        movapd    ebias(%rip), %xmm6
        psllq     $4, %xmm3
        psubd     %xmm3, %xmm2
        mulsd     %xmm2, %xmm0
        cmpl      $52, %edx
        jg        .L_2TAG_PACKET_2.0.2
        andpd     %xmm2, %xmm4
        paddd     %xmm6, %xmm3
        subsd     %xmm4, %xmm2
        addsd     %xmm2, %xmm0
        cmpl      $1023, %ecx
        jge       .L_2TAG_PACKET_3.0.2
        pextrw    $3, %xmm0, %ecx
        andl      $32768, %ecx
        orl       %ecx, %edx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_4.0.2
        movapd    %xmm0, %xmm6
        addsd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm0
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_5.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_5.0.2:
        mulsd     %xmm3, %xmm6
        mulsd     %xmm3, %xmm4
        movsd     %xmm6, %xmm0
        pxor      %xmm4, %xmm6
        psrad     $31, %xmm6
        pshufd    $85, %xmm6, %xmm6
        psllq     $1, %xmm0
        psrlq     $1, %xmm0
        pxor      %xmm6, %xmm0
        psrlq     $63, %xmm6
        paddq     %xmm6, %xmm0
        paddq     %xmm4, %xmm0
        movl      $15, (%rsp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_4.0.2:
        addsd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_3.0.2:
        addsd     %xmm4, %xmm0
        mulsd     %xmm3, %xmm0
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      $32752, %ecx
        jnb       .L_2TAG_PACKET_7.0.2
        jmp       ..B1.5
.L_2TAG_PACKET_2.0.2:
        paddd     %xmm6, %xmm3
        addpd     %xmm2, %xmm0
        mulsd     %xmm3, %xmm0
        movl      $15, (%rsp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_8.0.2:
        cmpl      $2146435072, %eax
        jae       .L_2TAG_PACKET_9.0.2
        movl      12(%rsp), %eax
        cmpl      $-2147483648, %eax
        jae       .L_2TAG_PACKET_10.0.2
        movsd     XMAX(%rip), %xmm0
        mulsd     %xmm0, %xmm0
.L_2TAG_PACKET_7.0.2:
        movl      $14, (%rsp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_10.0.2:
        movsd     XMIN(%rip), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $15, (%rsp)
        jmp       .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_9.0.2:
        movl      8(%rsp), %edx
        cmpl      $2146435072, %eax
        ja        .L_2TAG_PACKET_11.0.2
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_11.0.2
        movl      12(%rsp), %eax
        cmpl      $2146435072, %eax
        jne       .L_2TAG_PACKET_12.0.2
        movsd     INF(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_12.0.2:
        movsd     ZERO(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_11.0.2:
        movsd     8(%rsp), %xmm0
        addsd     %xmm0, %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_0.0.2:
        movl      12(%rsp), %eax
        andl      $2147483647, %eax
        cmpl      $1083179008, %eax
        jae       .L_2TAG_PACKET_8.0.2
        movsd     8(%rsp), %xmm0
        addsd     ONE_val(%rip), %xmm0
        jmp       ..B1.5
.L_2TAG_PACKET_6.0.2:
        movsd     %xmm0, 16(%rsp)
..B1.3:
        movq      16(%rsp), %xmm0
.L_2TAG_PACKET_13.0.2:
..B1.5:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	exp,@function
	.size	exp,.-exp
	.data
# -- End  exp
	.section .rodata, "a"
	.align 16
	.align 16
cv:
	.long	1697350398
	.long	1079448903
	.long	1697350398
	.long	1079448903
	.long	4277796864
	.long	1065758274
	.long	4277796864
	.long	1065758274
	.long	3164486458
	.long	1025308570
	.long	3164486458
	.long	1025308570
	.long	4294967294
	.long	1071644671
	.long	4294967294
	.long	1071644671
	.long	3811088480
	.long	1062650204
	.long	1432067621
	.long	1067799893
	.long	3230715663
	.long	1065423125
	.long	1431604129
	.long	1069897045
	.type	cv,@object
	.size	cv,96
	.align 16
Shifter:
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.type	Shifter,@object
	.size	Shifter,16
	.align 16
Tbl_addr:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	235107661
	.long	1018002367
	.long	1048019040
	.long	11418
	.long	896005651
	.long	1015861842
	.long	3541402996
	.long	22960
	.long	1642514529
	.long	1012987726
	.long	410360776
	.long	34629
	.long	1568897900
	.long	1016568486
	.long	1828292879
	.long	46424
	.long	1882168529
	.long	1010744893
	.long	852742562
	.long	58348
	.long	509852888
	.long	1017336174
	.long	3490863952
	.long	70401
	.long	653277307
	.long	1017431380
	.long	2930322911
	.long	82586
	.long	1649557430
	.long	1017729363
	.long	1014845818
	.long	94904
	.long	1058231231
	.long	1015777676
	.long	3949972341
	.long	107355
	.long	1044000607
	.long	1016786167
	.long	828946858
	.long	119943
	.long	1151779725
	.long	1015705409
	.long	2288159958
	.long	132667
	.long	3819481236
	.long	1016499965
	.long	1853186616
	.long	145530
	.long	2552227826
	.long	1015039787
	.long	1709341917
	.long	158533
	.long	1829350193
	.long	1015216097
	.long	4112506593
	.long	171677
	.long	1913391795
	.long	1015756674
	.long	2799960843
	.long	184965
	.long	1303423926
	.long	1015238005
	.long	171030293
	.long	198398
	.long	1574172746
	.long	1016061241
	.long	2992903935
	.long	211976
	.long	3424156969
	.long	1017196428
	.long	926591434
	.long	225703
	.long	1938513547
	.long	1017631273
	.long	887463926
	.long	239579
	.long	2804567149
	.long	1015390024
	.long	1276261410
	.long	253606
	.long	631083525
	.long	1017690182
	.long	569847337
	.long	267786
	.long	1623370770
	.long	1011049453
	.long	1617004845
	.long	282120
	.long	3667985273
	.long	1013894369
	.long	3049340112
	.long	296610
	.long	3145379760
	.long	1014403278
	.long	3577096743
	.long	311258
	.long	2603100681
	.long	1017152460
	.long	1990012070
	.long	326066
	.long	3249202951
	.long	1017448880
	.long	1453150081
	.long	341035
	.long	419288974
	.long	1016280325
	.long	917841882
	.long	356167
	.long	3793507337
	.long	1016095713
	.long	3712504873
	.long	371463
	.long	728023093
	.long	1016345318
	.long	363667784
	.long	386927
	.long	2582678538
	.long	1017123460
	.long	2956612996
	.long	402558
	.long	7592966
	.long	1016721543
	.long	2186617380
	.long	418360
	.long	228611441
	.long	1016696141
	.long	1719614412
	.long	434334
	.long	2261665670
	.long	1017457593
	.long	1013258798
	.long	450482
	.long	544148907
	.long	1017323666
	.long	3907805043
	.long	466805
	.long	2383914918
	.long	1017143586
	.long	1447192520
	.long	483307
	.long	1176412038
	.long	1017267372
	.long	1944781190
	.long	499988
	.long	2882956373
	.long	1013312481
	.long	919555682
	.long	516851
	.long	3154077648
	.long	1016528543
	.long	2571947538
	.long	533897
	.long	348651999
	.long	1016405780
	.long	2604962540
	.long	551129
	.long	3253791412
	.long	1015920431
	.long	1110089947
	.long	568549
	.long	1509121860
	.long	1014756995
	.long	2568320822
	.long	586158
	.long	2617649212
	.long	1017340090
	.long	2966275556
	.long	603959
	.long	553214634
	.long	1016457425
	.long	2682146383
	.long	621954
	.long	730975783
	.long	1014083580
	.long	2191782032
	.long	640145
	.long	1486499517
	.long	1016818996
	.long	2069751140
	.long	658534
	.long	2595788928
	.long	1016407932
	.long	2990417244
	.long	677123
	.long	1853053619
	.long	1015310724
	.long	1434058175
	.long	695915
	.long	2462790535
	.long	1015814775
	.long	2572866477
	.long	714911
	.long	3693944214
	.long	1017259110
	.long	3092190714
	.long	734114
	.long	2979333550
	.long	1017188654
	.long	4076559942
	.long	753526
	.long	174054861
	.long	1014300631
	.long	2420883922
	.long	773150
	.long	816778419
	.long	1014197934
	.long	3716502172
	.long	792987
	.long	3507050924
	.long	1015341199
	.long	777507147
	.long	813041
	.long	1821514088
	.long	1013410604
	.long	3706687593
	.long	833312
	.long	920623539
	.long	1016295433
	.long	1242007931
	.long	853805
	.long	2789017511
	.long	1014276997
	.long	3707479175
	.long	874520
	.long	3586233004
	.long	1015962192
	.long	64696965
	.long	895462
	.long	474650514
	.long	1016642419
	.long	863738718
	.long	916631
	.long	1614448851
	.long	1014281732
	.long	3884662774
	.long	938030
	.long	2450082086
	.long	1016164135
	.long	2728693977
	.long	959663
	.long	1101668360
	.long	1015989180
	.long	3999357479
	.long	981531
	.long	835814894
	.long	1015702697
	.long	1533953344
	.long	1003638
	.long	1301400989
	.long	1014466875
	.long	2174652632
	.long	1025985
	.type	Tbl_addr,@object
	.size	Tbl_addr,1024
	.align 16
mmask:
	.long	4294967232
	.long	0
	.long	4294967232
	.long	0
	.type	mmask,@object
	.size	mmask,16
	.align 16
bias:
	.long	65472
	.long	0
	.long	65472
	.long	0
	.type	bias,@object
	.size	bias,16
	.align 16
ALLONES:
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.type	ALLONES,@object
	.size	ALLONES,16
	.align 16
ebias:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.type	ebias,@object
	.size	ebias,16
	.align 4
XMAX:
	.long	4294967295
	.long	2146435071
	.type	XMAX,@object
	.size	XMAX,8
	.align 4
XMIN:
	.long	0
	.long	1048576
	.type	XMIN,@object
	.size	XMIN,8
	.align 4
INF:
	.long	0
	.long	2146435072
	.type	INF,@object
	.size	INF,8
	.align 4
ZERO:
	.long	0
	.long	0
	.type	ZERO,@object
	.size	ZERO,8
	.align 4
ONE_val:
	.long	0
	.long	1072693248
	.type	ONE_val,@object
	.size	ONE_val,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
