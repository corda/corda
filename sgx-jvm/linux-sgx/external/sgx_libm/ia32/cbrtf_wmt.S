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
	.file "cbrtf_wmt.c"
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
# -- Begin  cbrtf
	.text
       .align    16,0x90
	.globl cbrtf
cbrtf:
# parameter 1: 8 + %ebp
..B2.1:
..L3:

..B2.2:
        pushl     %ebp
        movl      %esp, %ebp
        subl      $104, %esp
        movl      %esi, 44(%esp)
        call      static_func
        movl      %eax, %esi
        movss     112(%esp), %xmm0
        movss     %xmm0, 4(%esp)
        movl      %ebx, 8(%esp)
        movl      $8388607, %edx
        movd      %edx, %xmm5
        movl      $-1081999360, %ebx
        movd      %ebx, %xmm3
        movl      $-1082130432, %ebx
        movd      %ebx, %xmm1
        movl      $8257536, %edx
        movd      %edx, %xmm2
        pextrw    $1, %xmm0, %ecx
        movl      %ecx, %eax
        andl      $124, %ecx
        movss     16(%ecx,%esi), %xmm4
        shrl      $7, %eax
        movl      $255, %edx
        andl      %eax, %edx
        movl      %eax, %ebx
        cmpl      $0, %edx
        je        .L_2TAG_PACKET_0.0.2
        cmpl      $255, %edx
        je        .L_2TAG_PACKET_1.0.2
        andps     %xmm0, %xmm2
        andps     %xmm5, %xmm0
        orps      %xmm2, %xmm3
        orps      %xmm0, %xmm1
        movss     (%esi), %xmm5
        movss     4(%esi), %xmm6
        movl      %edx, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %edx, %eax
        movl      %eax, %edx
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %edx
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %eax, %eax
        addl      %edx, %eax
        movl      %ebx, %edx
        andl      $255, %ebx
        subl      $1, %ebx
        shrl      $12, %eax
        andl      $256, %edx
        subl      %eax, %ebx
        subl      %eax, %ebx
        subl      %eax, %ebx
        shll      $7, %ebx
        addl      $85, %eax
        orl       %edx, %eax
        movd      %eax, %xmm7
        addl      %ebx, %ecx
        psllq     $23, %xmm7
.L_2TAG_PACKET_2.0.2:
        movss     8(%esi), %xmm2
        movss     12(%esi), %xmm0
        subss     %xmm3, %xmm1
        movaps    %xmm7, %xmm3
        mulss     144(%ecx,%esi), %xmm7
        mulss     %xmm4, %xmm1
        mulss     528(%ecx,%esi), %xmm3
        movss     %xmm1, %xmm4
        mulss     %xmm1, %xmm5
        mulss     %xmm1, %xmm6
        mulss     %xmm1, %xmm1
        addss     %xmm5, %xmm2
        addss     %xmm6, %xmm0
        mulss     %xmm1, %xmm2
        mulss     %xmm7, %xmm4
        addss     %xmm2, %xmm0
        movl      8(%esp), %ebx
        mulss     %xmm4, %xmm0
        addss     %xmm3, %xmm0
        addss     %xmm7, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_0.0.2:
        movq      %xmm0, %xmm7
        movd      %xmm0, %eax
        movl      %eax, %ebx
        shll      $9, %eax
        movl      $23, %ecx
        shrl      $23, %ebx
.L_2TAG_PACKET_4.0.2:
        cmpl      $-2147483648, %eax
        jae       .L_2TAG_PACKET_5.0.2
        shll      $1, %eax
        addl      $1, %edx
        loop      .L_2TAG_PACKET_4.0.2
        cmpl      $0, %eax
        je        .L_2TAG_PACKET_6.0.2
.L_2TAG_PACKET_5.0.2:
        movl      %edx, %ecx
        incl      %ecx
        movd      %ecx, %xmm7
        orl       %edx, %ebx
        psllq     %xmm7, %xmm0
        shrl      $24, %eax
        andl      $124, %eax
        movsd     16(%esi,%eax), %xmm4
        movl      %eax, %ecx
        andps     %xmm0, %xmm2
        andps     %xmm5, %xmm0
        orps      %xmm2, %xmm3
        orps      %xmm0, %xmm1
        movss     (%esi), %xmm5
        movl      $1366, %eax
        movss     4(%esi), %xmm6
        mull      %edx
        movl      %ebx, %edx
        andl      $255, %ebx
        addl      $1, %ebx
        shrl      $12, %eax
        andl      $256, %edx
        subl      %eax, %ebx
        subl      %eax, %ebx
        subl      %eax, %ebx
        cmpl      $0, %ebx
        je        .L_2TAG_PACKET_7.0.2
        incl      %eax
        subl      $3, %ebx
        negl      %ebx
.L_2TAG_PACKET_7.0.2:
        shll      $7, %ebx
        addl      %ebx, %ecx
        movl      $85, %ebx
        subl      %eax, %ebx
        orl       %edx, %ebx
        movd      %ebx, %xmm7
        psllq     $23, %xmm7
        jmp       .L_2TAG_PACKET_2.0.2
.L_2TAG_PACKET_6.0.2:
        cmpl      $0, %ebx
        jne       .L_2TAG_PACKET_8.0.2
        movl      8(%esp), %ebx
        fldl      944(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_8.0.2:
        movl      8(%esp), %ebx
        fldl      960(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_1.0.2:
        movl      8(%esp), %ebx
        movl      4(%esp), %eax
        movl      %eax, %ecx
        andl      $2147483647, %ecx
        cmpl      $2139095040, %ecx
        ja        .L_2TAG_PACKET_9.0.2
        cmpl      $2139095040, %eax
        jne       .L_2TAG_PACKET_10.0.2
        fldl      912(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_10.0.2:
        fldl      928(%esi)
        jmp       .L_2TAG_PACKET_3.0.2
.L_2TAG_PACKET_9.0.2:
        movss     4(%esp), %xmm0
        addss     %xmm0, %xmm0
        movss     %xmm0, (%esp)
        flds      (%esp)
.L_2TAG_PACKET_3.0.2:
        movl      44(%esp), %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.3:
        .align    16,0x90
	.type	cbrtf,@function
	.size	cbrtf,.-cbrtf
	.data
# -- End  cbrtf
	.section .rodata, "a"
	.align 16
	.align 16
static_const_table:
	.long	3173551943
	.long	3185806905
	.long	1031591658
	.long	1051372203
	.long	3212578753
	.long	3212085645
	.long	3211621124
	.long	3211182772
	.long	3210768440
	.long	3210376206
	.long	3210004347
	.long	3209651317
	.long	3209315720
	.long	3208996296
	.long	3208691905
	.long	3208401508
	.long	3208124163
	.long	3207859009
	.long	3207605259
	.long	3207362194
	.long	3207129151
	.long	3206905525
	.long	3206690755
	.long	3206484326
	.long	3206285761
	.long	3206094618
	.long	3205910490
	.long	3205732998
	.long	3205561788
	.long	3205396533
	.long	3205236929
	.long	3205082689
	.long	3204933547
	.long	3204789256
	.long	3204649583
	.long	3204514308
	.long	1065396681
	.long	1065482291
	.long	1065566215
	.long	1065648532
	.long	1065729317
	.long	1065808640
	.long	1065886565
	.long	1065963152
	.long	1066038457
	.long	1066112533
	.long	1066185428
	.long	1066257188
	.long	1066327857
	.long	1066397474
	.long	1066466079
	.long	1066533708
	.long	1066600394
	.long	1066666169
	.long	1066731064
	.long	1066795108
	.long	1066858329
	.long	1066920751
	.long	1066982401
	.long	1067043301
	.long	1067103474
	.long	1067162941
	.long	1067221722
	.long	1067279837
	.long	1067337305
	.long	1067394143
	.long	1067450368
	.long	1067505996
	.long	1067588354
	.long	1067696217
	.long	1067801953
	.long	1067905666
	.long	1068007450
	.long	1068107390
	.long	1068205570
	.long	1068302063
	.long	1068396942
	.long	1068490271
	.long	1068582113
	.long	1068672525
	.long	1068761562
	.long	1068849275
	.long	1068935712
	.long	1069020919
	.long	1069104937
	.long	1069187809
	.long	1069269572
	.long	1069350263
	.long	1069429915
	.long	1069508563
	.long	1069586236
	.long	1069662966
	.long	1069738778
	.long	1069813702
	.long	1069887762
	.long	1069960982
	.long	1070033387
	.long	1070104998
	.long	1070175837
	.long	1070245925
	.long	1070349689
	.long	1070485588
	.long	1070618808
	.long	1070749478
	.long	1070877717
	.long	1071003634
	.long	1071127332
	.long	1071248907
	.long	1071368446
	.long	1071486034
	.long	1071601747
	.long	1071715659
	.long	1071827839
	.long	1071938350
	.long	1072047254
	.long	1072154608
	.long	1072260465
	.long	1072364876
	.long	1072467891
	.long	1072569555
	.long	1072669911
	.long	1072769001
	.long	1072866863
	.long	1072963536
	.long	1073059054
	.long	1073153452
	.long	1073246762
	.long	1073339014
	.long	1073430238
	.long	1073520462
	.long	1073609714
	.long	1073698019
	.long	839340838
	.long	867750258
	.long	851786446
	.long	853949398
	.long	864938789
	.long	864102364
	.long	864209792
	.long	865422805
	.long	867593594
	.long	854482593
	.long	848298042
	.long	860064854
	.long	844792593
	.long	870701309
	.long	872023170
	.long	860255342
	.long	849966899
	.long	863561479
	.long	869115319
	.long	871961375
	.long	859537336
	.long	871954398
	.long	863817578
	.long	861687921
	.long	849594757
	.long	816486846
	.long	858183533
	.long	864500406
	.long	850523240
	.long	808125243
	.long	514020693
	.long	861173761
	.long	859000219
	.long	823158129
	.long	871826232
	.long	871183196
	.long	839030530
	.long	867690638
	.long	840440923
	.long	868033274
	.long	855856030
	.long	865094453
	.long	860418487
	.long	866225006
	.long	866458226
	.long	865124659
	.long	864837702
	.long	811742505
	.long	869432099
	.long	864584201
	.long	864183978
	.long	844810573
	.long	869245699
	.long	859556409
	.long	870675446
	.long	814190139
	.long	870686941
	.long	861800510
	.long	855649163
	.long	869347119
	.long	864252033
	.long	867276215
	.long	868189817
	.long	849541095
	.long	866633177
	.long	843967686
	.long	857522493
	.long	862339487
	.long	850054662
	.long	864048556
	.long	868027089
	.long	848093931
	.long	865355299
	.long	848111485
	.long	865557362
	.long	870297525
	.long	863416216
	.long	869675693
	.long	865888071
	.long	825332584
	.long	843309506
	.long	870885636
	.long	869119784
	.long	865466648
	.long	867459244
	.long	861192764
	.long	871247716
	.long	864927982
	.long	869195129
	.long	864849564
	.long	840005936
	.long	852579258
	.long	860852782
	.long	869711141
	.long	862506141
	.long	837959274
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	4293918720
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.type	static_const_table,@object
	.size	static_const_table,976
	.data
	.section .note.GNU-stack, ""
# End
