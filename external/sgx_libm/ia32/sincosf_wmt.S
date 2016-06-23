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
	.file "sincosf_wmt.c"
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
# -- Begin  sincosf
	.text
       .align    16,0x90
	.globl sincosf
sincosf:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $136, %esp
        movl      %ebx, 80(%esp)
        call      static_func
        movl      %eax, %ebx
        movss     144(%esp), %xmm0
        movl      144(%esp), %ecx
        movl      148(%esp), %eax
        movl      %eax, 32(%esp)
        movl      152(%esp), %eax
        movl      %eax, 36(%esp)
        movaps    %xmm0, %xmm1
        cvtps2pd  %xmm1, %xmm1
        movsd     1360(%ebx), %xmm2
        movl      $2147483647, %eax
        movl      $1241513983, %edx
        andl      %ecx, %eax
        subl      %eax, %edx
        subl      $964689920, %eax
        orl       %eax, %edx
        jl        .L_2TAG_PACKET_0.0.2
        movsd     1376(%ebx), %xmm4
        mulsd     %xmm1, %xmm2
        movsd     1392(%ebx), %xmm3
        movapd    %xmm2, %xmm5
        addsd     %xmm4, %xmm2
        movd      %xmm2, %ecx
        subsd     %xmm4, %xmm2
        mulsd     %xmm3, %xmm1
.L_2TAG_PACKET_1.0.2:
        movl      $256, %eax
        movapd    1040(%ebx), %xmm4
        subsd     %xmm2, %xmm5
        addl      %ecx, %ecx
        movl      %ecx, %edx
        shll      $24, %ecx
        sarl      $31, %ecx
        andl      %edx, %eax
        addl      %ecx, %edx
        xorl      %ecx, %edx
        addsd     %xmm5, %xmm1
        movsd     1440(%ebx), %xmm6
        andl      $254, %edx
        movapd    (%ebx,%edx,8), %xmm3
        pshufd    $68, %xmm1, %xmm2
        shrl      $4, %eax
        movapd    1072(%ebx,%eax), %xmm7
        movapd    1056(%ebx), %xmm5
        mulpd     %xmm2, %xmm2
        mulsd     %xmm6, %xmm1
        andl      $1, %ecx
        movd      %ecx, %xmm0
        psllq     $63, %xmm0
        xorpd     %xmm0, %xmm3
        xorpd     %xmm7, %xmm3
        mulpd     %xmm4, %xmm2
        pshufd    $78, %xmm3, %xmm6
        mulsd     %xmm1, %xmm3
        addpd     %xmm2, %xmm5
        mulsd     %xmm1, %xmm6
        mulpd     %xmm5, %xmm3
        mulpd     %xmm5, %xmm6
        pshufd    $238, %xmm3, %xmm0
        pshufd    $238, %xmm6, %xmm1
        addsd     %xmm3, %xmm0
        subsd     %xmm6, %xmm1
        cvtpd2ps  %xmm0, %xmm0
        cvtpd2ps  %xmm1, %xmm1
        movl      32(%esp), %eax
        movss     %xmm0, (%eax)
        movl      36(%esp), %edx
        movss     %xmm1, (%edx)
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_0.0.2:
        andl      $2147483647, %ecx
        cmpl      $1040187392, %ecx
        ja        .L_2TAG_PACKET_3.0.2
        cvtss2sd  %xmm0, %xmm0
        mulsd     1344(%ebx), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        movl      32(%esp), %eax
        movss     %xmm0, (%eax)
        movl      36(%esp), %edx
        movl      $1065353216, %ecx
        movl      %ecx, (%edx)
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_3.0.2:
        movd      %xmm0, %eax
        andl      $2139095040, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_4.0.2
        shrl      $23, %ecx
        subl      $142, %ecx
        andl      $65528, %ecx
        movsd     1104(%ebx,%ecx,2), %xmm3
        movsd     1112(%ebx,%ecx,2), %xmm5
        movsd     1424(%ebx), %xmm2
        xorpd     %xmm4, %xmm4
        movl      $17208, %eax
        pinsrw    $3, %eax, %xmm4
        andpd     %xmm3, %xmm2
        psllq     $40, %xmm3
        mulsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm3
        mulsd     %xmm5, %xmm1
        movapd    %xmm2, %xmm0
        addsd     %xmm3, %xmm2
        movapd    %xmm2, %xmm5
        subsd     %xmm2, %xmm0
        addsd     %xmm4, %xmm2
        addsd     %xmm0, %xmm3
        movd      %xmm2, %ecx
        subsd     %xmm4, %xmm2
        addsd     %xmm3, %xmm1
        jmp       .L_2TAG_PACKET_1.0.2
.L_2TAG_PACKET_4.0.2:
        subss     %xmm0, %xmm0
        movl      32(%esp), %eax
        movss     %xmm0, (%eax)
        movl      36(%esp), %edx
        movss     %xmm0, (%edx)
.L_2TAG_PACKET_2.0.2:
        movl      80(%esp), %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	sincosf,@function
	.size	sincosf,.-sincosf
	.data
# -- End  sincosf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	1619315981
	.long	1072692616
	.long	4154680958
	.long	1067000149
	.long	3816385918
	.long	1072690721
	.long	4044216340
	.long	1068048229
	.long	2902558778
	.long	1072687565
	.long	2462980598
	.long	1068684576
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	1069094822
	.long	1893834707
	.long	1072677476
	.long	1453945614
	.long	1069504078
	.long	2131272983
	.long	1072670549
	.long	1854824762
	.long	1069729808
	.long	4201728937
	.long	1072662372
	.long	1149976518
	.long	1069933076
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	1070135480
	.long	2892293513
	.long	1072642291
	.long	1786662755
	.long	1070336898
	.long	4220661975
	.long	1072630398
	.long	2065784603
	.long	1070537209
	.long	3882371427
	.long	1072617280
	.long	1655830135
	.long	1070666194
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	1070765062
	.long	3964199186
	.long	1072587400
	.long	2484561409
	.long	1070863196
	.long	83265253
	.long	1072570657
	.long	1974149085
	.long	1070960538
	.long	3066872380
	.long	1072552723
	.long	821708191
	.long	1071057029
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	1071152610
	.long	3511535930
	.long	1072513332
	.long	1135144330
	.long	1071247225
	.long	4097623923
	.long	1072491898
	.long	165764288
	.long	1071340816
	.long	557060597
	.long	1072469323
	.long	2570175582
	.long	1071433326
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	1071524701
	.long	2892993834
	.long	1072420800
	.long	3690502842
	.long	1071614884
	.long	1159294526
	.long	1072394883
	.long	2569207790
	.long	1071674247
	.long	1477771776
	.long	1072367882
	.long	1411074851
	.long	1071718067
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	1071761211
	.long	2688952194
	.long	1072310695
	.long	1289556703
	.long	1071803653
	.long	1207142209
	.long	1072280544
	.long	4242632757
	.long	1071845367
	.long	1234330619
	.long	1072249378
	.long	689983673
	.long	1071886330
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	1071926515
	.long	201299822
	.long	1072184078
	.long	3152274921
	.long	1071965898
	.long	577417135
	.long	1072149983
	.long	881650848
	.long	1072004457
	.long	938475414
	.long	1072114952
	.long	1427975391
	.long	1072042167
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	1072079006
	.long	1427975391
	.long	1072042167
	.long	938475414
	.long	1072114952
	.long	881650848
	.long	1072004457
	.long	577417135
	.long	1072149983
	.long	3152274921
	.long	1071965898
	.long	201299822
	.long	1072184078
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	1072217216
	.long	689983673
	.long	1071886330
	.long	1234330619
	.long	1072249378
	.long	4242632757
	.long	1071845367
	.long	1207142209
	.long	1072280544
	.long	1289556703
	.long	1071803653
	.long	2688952194
	.long	1072310695
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	1072339814
	.long	1411074851
	.long	1071718067
	.long	1477771776
	.long	1072367882
	.long	2569207790
	.long	1071674247
	.long	1159294526
	.long	1072394883
	.long	3690502842
	.long	1071614884
	.long	2892993834
	.long	1072420800
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	1072445618
	.long	2570175582
	.long	1071433326
	.long	557060597
	.long	1072469323
	.long	165764288
	.long	1071340816
	.long	4097623923
	.long	1072491898
	.long	1135144330
	.long	1071247225
	.long	3511535930
	.long	1072513332
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	1072533611
	.long	821708191
	.long	1071057029
	.long	3066872380
	.long	1072552723
	.long	1974149085
	.long	1070960538
	.long	83265253
	.long	1072570657
	.long	2484561409
	.long	1070863196
	.long	3964199186
	.long	1072587400
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	1072602945
	.long	1655830135
	.long	1070666194
	.long	3882371427
	.long	1072617280
	.long	2065784603
	.long	1070537209
	.long	4220661975
	.long	1072630398
	.long	1786662755
	.long	1070336898
	.long	2892293513
	.long	1072642291
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	1072652951
	.long	1149976518
	.long	1069933076
	.long	4201728937
	.long	1072662372
	.long	1854824762
	.long	1069729808
	.long	2131272983
	.long	1072670549
	.long	1453945614
	.long	1069504078
	.long	1893834707
	.long	1072677476
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	1072683149
	.long	2462980598
	.long	1068684576
	.long	2902558778
	.long	1072687565
	.long	4044216340
	.long	1068048229
	.long	3816385918
	.long	1072690721
	.long	4154680958
	.long	1067000149
	.long	1619315981
	.long	1072692616
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	1649608659
	.long	3206173094
	.long	3384690142
	.long	3207839036
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	2147483648
	.long	1832810809
	.long	1078222640
	.long	4036845384
	.long	1030771879
	.long	1832810809
	.long	1078222640
	.long	4036845384
	.long	1030771879
	.long	1916669064
	.long	1072155675
	.long	660066805
	.long	1024797180
	.long	3829231700
	.long	1062745828
	.long	2884172442
	.long	1017445907
	.long	540874025
	.long	1055617650
	.long	4198409884
	.long	1011844330
	.long	2839315448
	.long	1046639136
	.long	4204424928
	.long	1000582120
	.long	1329339431
	.long	1038229673
	.long	1295478839
	.long	993391741
	.long	658191860
	.long	1024797180
	.long	3691886121
	.long	979325773
	.long	1966800710
	.long	1020436418
	.long	1882642597
	.long	977095991
	.long	2100977488
	.long	1012908661
	.long	1840335565
	.long	967470816
	.long	4198093645
	.long	1000582120
	.long	3307942521
	.long	957055414
	.long	2839123165
	.long	996851343
	.long	1387472776
	.long	952638316
	.long	2788761024
	.long	988712873
	.long	3005712498
	.long	944467026
	.long	3694676832
	.long	979325773
	.long	1326507024
	.long	934847846
	.long	221775557
	.long	970149340
	.long	1133527550
	.long	924162364
	.long	4286578688
	.long	1072693247
	.long	4286578688
	.long	1072693247
	.long	1845493760
	.long	1078222640
	.long	1845493760
	.long	1078222640
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.long	2908765579
	.long	3193641918
	.long	2908765579
	.long	3193641918
	.long	2308576600
	.long	1086551075
	.long	1646446368
	.long	1084878895
	.long	4278190080
	.long	4294967295
	.long	0
	.long	0
	.long	1413754136
	.long	1067000315
	.long	1413754136
	.long	1067000315
	.type	static_const_table,@object
	.size	static_const_table,1456
	.data
	.section .note.GNU-stack, ""
# End
