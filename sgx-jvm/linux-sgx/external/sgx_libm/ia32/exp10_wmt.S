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
	.file "exp10_wmt.c"
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
# -- Begin  exp10
	.text
       .align    16,0x90
	.globl exp10
exp10:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $120, %esp
        movl      %ebx, 64(%esp)
        call      static_func
        movl      %eax, %ebx
        movsd     128(%esp), %xmm0
        unpcklpd  %xmm0, %xmm0
        movapd    64(%ebx), %xmm1
        movapd    48(%ebx), %xmm6
        movapd    80(%ebx), %xmm2
        movapd    96(%ebx), %xmm3
        pextrw    $3, %xmm0, %eax
        andl      $32767, %eax
        movl      $16500, %edx
        subl      %eax, %edx
        subl      $15472, %eax
        orl       %eax, %edx
        cmpl      $-2147483648, %edx
        jae       .L_2TAG_PACKET_0.0.3
        mulpd     %xmm0, %xmm1
        addpd     %xmm6, %xmm1
        movapd    %xmm1, %xmm7
        subpd     %xmm6, %xmm1
        mulpd     %xmm1, %xmm2
        movapd    112(%ebx), %xmm4
        mulpd     %xmm1, %xmm3
        movapd    1168(%ebx), %xmm1
        movapd    128(%ebx), %xmm5
        subpd     %xmm2, %xmm0
        movd      %xmm7, %eax
        movl      %eax, %ecx
        andl      $63, %ecx
        shll      $4, %ecx
        sarl      $6, %eax
        movl      %eax, %edx
        subpd     %xmm3, %xmm0
        movapd    144(%ebx,%ecx), %xmm2
        mulpd     %xmm0, %xmm4
        mulpd     %xmm0, %xmm1
        mulpd     %xmm0, %xmm0
        addpd     %xmm4, %xmm5
        mulsd     %xmm0, %xmm0
        addsd     %xmm2, %xmm1
        unpckhpd  %xmm2, %xmm2
        movdqa    16(%ebx), %xmm6
        pand      %xmm6, %xmm7
        movdqa    32(%ebx), %xmm6
        paddq     %xmm6, %xmm7
        psllq     $46, %xmm7
        mulpd     %xmm5, %xmm0
        addsd     %xmm0, %xmm1
        orpd      %xmm7, %xmm2
        unpckhpd  %xmm0, %xmm0
        addsd     %xmm1, %xmm0
        addl      $894, %edx
        cmpl      $1916, %edx
        ja        .L_2TAG_PACKET_1.0.3
        mulsd     %xmm2, %xmm0
        addsd     %xmm2, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_1.0.3:
        fstcw     24(%esp)
        movzwl    24(%esp), %edx
        orl       $768, %edx
        movw      %dx, 28(%esp)
        fldcw     28(%esp)
        movl      %eax, %edx
        sarl      $1, %eax
        subl      %eax, %edx
        movdqa    (%ebx), %xmm6
        pandn     %xmm2, %xmm6
        addl      $1023, %eax
        movd      %eax, %xmm3
        psllq     $52, %xmm3
        orpd      %xmm3, %xmm6
        addl      $1023, %edx
        movd      %edx, %xmm4
        psllq     $52, %xmm4
        movsd     %xmm0, 8(%esp)
        fldl      8(%esp)
        movsd     %xmm6, 16(%esp)
        fldl      16(%esp)
        fmul      %st, %st(1)
        faddp     %st, %st(1)
        movsd     %xmm4, 8(%esp)
        fldl      8(%esp)
        fmulp     %st, %st(1)
        fstpl     8(%esp)
        movsd     8(%esp), %xmm0
        fldcw     24(%esp)
        pextrw    $3, %xmm0, %ecx
        andl      $32752, %ecx
        cmpl      $32752, %ecx
        jae       .L_2TAG_PACKET_3.0.3
        movl      $265, %edx
        cmpl      $0, %ecx
        je        .L_2TAG_PACKET_4.0.3
        jmp       .L_2TAG_PACKET_2.0.3
        cmpl      $-2147483648, %ecx
        jb        .L_2TAG_PACKET_3.0.3
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_3.0.3:
        movl      $166, %edx
.L_2TAG_PACKET_4.0.3:
        movsd     %xmm0, (%esp)
        movsd     128(%esp), %xmm0
        subl      $32, %esp
        lea       128(%esp), %eax
        movl      %eax, (%esp)
        lea       128(%esp), %eax
        movl      %eax, 4(%esp)
        lea       32(%esp), %eax
        movl      %eax, 8(%esp)
        movl      %edx, %eax
        movl      %eax, 12(%esp)
        call      __libm_error_support
        addl      $32, %esp
        fldl      (%esp)
        jmp       .L_2TAG_PACKET_5.0.3
.L_2TAG_PACKET_6.0.3:
        cmpl      $2146435072, %eax
        jae       .L_2TAG_PACKET_7.0.3
        movl      132(%esp), %eax
        cmpl      $-2147483648, %eax
        jae       .L_2TAG_PACKET_8.0.3
        movsd     1208(%ebx), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $166, %edx
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_8.0.3:
        movsd     1216(%ebx), %xmm0
        mulsd     %xmm0, %xmm0
        movl      $265, %edx
        jmp       .L_2TAG_PACKET_4.0.3
.L_2TAG_PACKET_7.0.3:
        movl      128(%esp), %edx
        cmpl      $2146435072, %eax
        ja        .L_2TAG_PACKET_9.0.3
        cmpl      $0, %edx
        jne       .L_2TAG_PACKET_9.0.3
        movl      132(%esp), %eax
        cmpl      $2146435072, %eax
        jne       .L_2TAG_PACKET_10.0.3
        movsd     1192(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_10.0.3:
        movsd     1200(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_9.0.3:
        movsd     128(%esp), %xmm0
        addsd     %xmm0, %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_0.0.3:
        movl      132(%esp), %eax
        andl      $2147483647, %eax
        cmpl      $1081344000, %eax
        jae       .L_2TAG_PACKET_6.0.3
        movsd     128(%esp), %xmm0
        addsd     1184(%ebx), %xmm0
        jmp       .L_2TAG_PACKET_2.0.3
.L_2TAG_PACKET_2.0.3:
        movsd     %xmm0, 48(%esp)
        fldl      48(%esp)
.L_2TAG_PACKET_5.0.3:
        movl      64(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	exp10,@function
	.size	exp10,.-exp10
	.data
# -- End  exp10
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
	.long	4293918720
	.long	0
	.long	4293918720
	.long	4294967232
	.long	0
	.long	4294967232
	.long	0
	.long	65472
	.long	0
	.long	65472
	.long	0
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	158966641
	.long	1080726351
	.long	158966641
	.long	1080726351
	.long	1352531968
	.long	1064518675
	.long	1352531968
	.long	1064518675
	.long	824119987
	.long	1027055599
	.long	824119987
	.long	1027055599
	.long	3549356600
	.long	1071727285
	.long	2447043358
	.long	1073760005
	.long	2974317904
	.long	1072872823
	.long	3342652200
	.long	1074083108
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
	.long	3149223190
	.long	1073900465
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	4294967295
	.long	2146435071
	.long	0
	.long	1048576
	.type	static_const_table,@object
	.size	static_const_table,1224
	.data
	.section .note.GNU-stack, ""
# End

	.globl pow10
	.equ pow10, exp10

