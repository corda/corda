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
	.file "libm_sse2_sincos.c"
	.text
..TXTST0:
# -- Begin  __libm_sse2_sincos
	.text
       .align    16,0x90
	.globl __libm_sse2_sincos
__libm_sse2_sincos:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value___libm_sse2_sincos.1:
..L2:

        subq      $56, %rsp
	.cfi_def_cfa_offset 64
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movsd     %xmm0, (%rsp)
        movq      %rax, 40(%rsp)
..B1.2:
        pextrw    $3, %xmm0, %eax
        andw      $32767, %ax
        subw      $12336, %ax
        cmpw      $4293, %ax
        ja        .L_2TAG_PACKET_0.0.1
        unpcklpd  %xmm0, %xmm0
        movapd    PI32INV(%rip), %xmm1
        mulpd     %xmm0, %xmm1
        movapd    SHIFTER(%rip), %xmm2
        cvtsd2si  %xmm1, %edx
        addpd     %xmm2, %xmm1
        movapd    P_1(%rip), %xmm3
        subpd     %xmm2, %xmm1
        movapd    P_2(%rip), %xmm2
        mulpd     %xmm1, %xmm3
        addq      $1865216, %rdx
        movapd    %xmm0, %xmm4
        andq      $63, %rdx
        movapd    SC_4(%rip), %xmm5
        lea       Ctable(%rip), %rax
        shlq      $6, %rdx
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm2
        subpd     %xmm3, %xmm0
        mulpd     P_3(%rip), %xmm1
        subpd     %xmm3, %xmm4
        movapd    16(%rax), %xmm7
        movapd    %xmm4, %xmm3
        subpd     %xmm2, %xmm4
        mulpd     %xmm0, %xmm5
        subpd     %xmm2, %xmm0
        movapd    SC_2(%rip), %xmm6
        mulpd     %xmm4, %xmm7
        subpd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subpd     %xmm2, %xmm3
        movapd    (%rax), %xmm2
        subpd     %xmm3, %xmm1
        movapd    48(%rax), %xmm3
        addpd     %xmm3, %xmm2
        subpd     %xmm2, %xmm7
        mulpd     %xmm7, %xmm1
        movapd    16(%rax), %xmm7
        mulpd     %xmm4, %xmm2
        mulpd     %xmm0, %xmm6
        mulpd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm7
        mulpd     %xmm0, %xmm0
        addpd     SC_3(%rip), %xmm5
        mulpd     (%rax), %xmm4
        addpd     SC_1(%rip), %xmm6
        mulpd     %xmm0, %xmm5
        movapd    %xmm3, %xmm0
        addpd     16(%rax), %xmm3
        addpd     %xmm5, %xmm6
        movq      %xmm6, %xmm5
        unpckhpd  %xmm6, %xmm6
        unpcklpd  %xmm5, %xmm5
        mulpd     %xmm7, %xmm6
        mulpd     %xmm5, %xmm2
        movapd    %xmm4, %xmm7
        addpd     %xmm3, %xmm4
        movapd    16(%rax), %xmm5
        subpd     %xmm3, %xmm5
        subpd     %xmm4, %xmm3
        addpd     32(%rax), %xmm1
        addpd     %xmm0, %xmm5
        addpd     %xmm7, %xmm3
        addpd     %xmm5, %xmm1
        addpd     %xmm3, %xmm1
        addpd     %xmm2, %xmm1
        addpd     %xmm6, %xmm1
        addpd     %xmm4, %xmm1
        movq      %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
        jmp       ..B1.7
.L_2TAG_PACKET_0.0.1:
        jg        .L_2TAG_PACKET_1.0.1
        movq      %xmm0, %xmm1
        pextrw    $3, %xmm0, %eax
        orw       $32768, %ax
        pinsrw    $3, %eax, %xmm1
        addsd     ONE(%rip), %xmm1
        cmpw      $32784, %ax
        jge       .L_2TAG_PACKET_2.0.1
        mulsd     ALL_ONES(%rip), %xmm0
        jmp       ..B1.7
.L_2TAG_PACKET_2.0.1:
        movq      TWO_POW_55(%rip), %xmm3
        mulsd     %xmm0, %xmm3
        subsd     %xmm3, %xmm0
        mulsd     TWO_POW_M55(%rip), %xmm0
        jmp       ..B1.7
.L_2TAG_PACKET_1.0.1:
        pextrw    $3, %xmm0, %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        .L_2TAG_PACKET_3.0.1
..B1.3:
        movsd     (%rsp), %xmm0
        lea       24(%rsp), %rdi
..___tag_value___libm_sse2_sincos.4:
        call      __libm_reduce_pio2d@PLT
..___tag_value___libm_sse2_sincos.5:
..B1.4:
        movl      %eax, 16(%rsp)
..B1.5:
        movq      24(%rsp), %xmm0
        movq      32(%rsp), %xmm6
        unpcklpd  %xmm0, %xmm0
        unpcklpd  %xmm6, %xmm6
        movapd    PI32INV(%rip), %xmm1
        mulpd     %xmm0, %xmm1
        movapd    SHIFTER(%rip), %xmm2
        cvtsd2si  %xmm1, %edx
        addpd     %xmm2, %xmm1
        movapd    P_1(%rip), %xmm3
        subpd     %xmm2, %xmm1
        movapd    P_2(%rip), %xmm2
        mulpd     %xmm1, %xmm3
        movl      16(%rsp), %eax
        shlq      $4, %rax
        addq      $1865216, %rdx
        movapd    %xmm0, %xmm4
        addq      %rax, %rdx
        andq      $63, %rdx
        movapd    SC_4(%rip), %xmm5
        lea       Ctable(%rip), %rax
        shlq      $6, %rdx
        addq      %rdx, %rax
        mulpd     %xmm1, %xmm2
        subpd     %xmm3, %xmm0
        mulpd     P_3(%rip), %xmm1
        subpd     %xmm3, %xmm4
        movapd    16(%rax), %xmm7
        movapd    %xmm4, %xmm3
        subpd     %xmm2, %xmm4
        mulpd     %xmm0, %xmm5
        subpd     %xmm2, %xmm0
        mulpd     %xmm4, %xmm7
        subpd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm5
        mulpd     %xmm0, %xmm0
        subpd     %xmm2, %xmm3
        movapd    (%rax), %xmm2
        subpd     %xmm3, %xmm1
        movapd    48(%rax), %xmm3
        addpd     %xmm3, %xmm2
        subpd     %xmm2, %xmm7
        subpd     %xmm6, %xmm1
        movapd    SC_2(%rip), %xmm6
        mulpd     %xmm7, %xmm1
        movapd    16(%rax), %xmm7
        mulpd     %xmm4, %xmm2
        mulpd     %xmm0, %xmm6
        mulpd     %xmm4, %xmm3
        mulpd     %xmm0, %xmm2
        mulpd     %xmm0, %xmm7
        mulpd     %xmm0, %xmm0
        addpd     SC_3(%rip), %xmm5
        mulpd     (%rax), %xmm4
        addpd     SC_1(%rip), %xmm6
        mulpd     %xmm0, %xmm5
        movapd    %xmm3, %xmm0
        addpd     16(%rax), %xmm3
        addpd     %xmm5, %xmm6
        movq      %xmm6, %xmm5
        unpckhpd  %xmm6, %xmm6
        unpcklpd  %xmm5, %xmm5
        mulpd     %xmm7, %xmm6
        mulpd     %xmm5, %xmm2
        movapd    %xmm4, %xmm7
        addpd     %xmm3, %xmm4
        movapd    16(%rax), %xmm5
        subpd     %xmm3, %xmm5
        subpd     %xmm4, %xmm3
        addpd     32(%rax), %xmm1
        addpd     %xmm0, %xmm5
        addpd     %xmm7, %xmm3
        addpd     %xmm5, %xmm1
        addpd     %xmm3, %xmm1
        addpd     %xmm2, %xmm1
        addpd     %xmm6, %xmm1
        addpd     %xmm4, %xmm1
        movq      %xmm1, %xmm0
        unpckhpd  %xmm1, %xmm1
        jmp       ..B1.7
.L_2TAG_PACKET_3.0.1:
        movq      (%rsp), %xmm0
        pxor      %xmm1, %xmm1
        mulsd     %xmm1, %xmm0
        movq      %xmm0, %xmm1
        movq      %xmm0, 8(%rsp)
        jmp       ..B1.7
.L_2TAG_PACKET_4.0.1:
..B1.7:
        movq      40(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.9
..B1.8:
        addq      $56, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 64
..B1.9:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sse2_sincos,@function
	.size	__libm_sse2_sincos,.-__libm_sse2_sincos
	.data
# -- End  __libm_sse2_sincos
	.section .rodata, "a"
	.align 16
	.align 16
PI32INV:
	.long	1841940611
	.long	1076125488
	.long	1841940611
	.long	1076125488
	.type	PI32INV,@object
	.size	PI32INV,16
	.align 16
SHIFTER:
	.long	0
	.long	1127743488
	.long	0
	.long	1127743488
	.type	SHIFTER,@object
	.size	SHIFTER,16
	.align 16
P_1:
	.long	1413480448
	.long	1069097467
	.long	1413480448
	.long	1069097467
	.type	P_1,@object
	.size	P_1,16
	.align 16
P_2:
	.long	442499072
	.long	1032893537
	.long	442499072
	.long	1032893537
	.type	P_2,@object
	.size	P_2,16
	.align 16
SC_4:
	.long	2773927732
	.long	1053236707
	.long	436314138
	.long	1056571808
	.type	SC_4,@object
	.size	SC_4,16
	.align 16
Ctable:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	3217031168
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	3218079744
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	3219128320
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	3220176896
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	3220176896
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	1064548654
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	3163061750
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	3220176896
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	1066642694
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	1014325783
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	1067846634
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	1015505073
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	3220176896
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	1068727457
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	1014257638
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	1069431400
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	3161907377
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	1069912679
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	1010431536
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	1070403070
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3162686945
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	3220176896
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	3163282740
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	3219128320
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	3217109964
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1013450602
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	3215750067
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	1015752157
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	1067272748
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	1012796809
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	1069418613
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	3160878317
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	3219128320
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	3215237169
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	3161838221
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	1068244255
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	3160567065
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	3218079744
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	1067164005
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	3158189848
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	3217031168
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	393047345
	.long	1064548654
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	3220166797
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3220176896
	.long	0
	.long	1069547520
	.long	18115067
	.long	1066642694
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	3220136599
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	3161809431
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2476548698
	.long	1067846634
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	3220086593
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3220176896
	.long	0
	.long	1070596096
	.long	2255197647
	.long	1068727457
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	3220017259
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	3161741286
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1945768569
	.long	1069431400
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	3219929266
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1539668340
	.long	1069912679
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	3219823462
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	1403757309
	.long	1070403070
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	1015203297
	.long	0
	.long	3220176896
	.long	0
	.long	1071644672
	.long	2583490354
	.long	3217719929
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	0
	.long	1071644672
	.long	2485417816
	.long	3217109964
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	3160934250
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2598800519
	.long	3215750067
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	3219244859
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	3163235805
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	2140183630
	.long	1067272748
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	3219008349
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	3160280457
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1699043957
	.long	1069418613
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	3218636258
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3219128320
	.long	0
	.long	1072693248
	.long	1991047213
	.long	3215237169
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	3218248710
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	240740309
	.long	1068244255
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	3217619128
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3218079744
	.long	0
	.long	1072693248
	.long	257503056
	.long	1067164005
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	3216578470
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3217031168
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	257503056
	.long	3214647653
	.long	393047345
	.long	3212032302
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	1069094822
	.long	1073741824
	.long	1015578102
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1069547520
	.long	0
	.long	1072693248
	.long	240740309
	.long	3215727903
	.long	18115067
	.long	3214126342
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	1070135480
	.long	536870912
	.long	3161809431
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1991047213
	.long	1067753521
	.long	2476548698
	.long	3215330282
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	1070765062
	.long	3758096384
	.long	3162988721
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1070596096
	.long	0
	.long	1072693248
	.long	1699043957
	.long	3216902261
	.long	2255197647
	.long	3216211105
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	1071152610
	.long	536870912
	.long	3161741286
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2140183630
	.long	3214756396
	.long	1945768569
	.long	3216915048
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	1071524701
	.long	2147483648
	.long	1014423729
	.long	536870912
	.long	1012796809
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2598800519
	.long	1068266419
	.long	1539668340
	.long	3217396327
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	1071761211
	.long	3758096384
	.long	3157915184
	.long	536870912
	.long	1015752157
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2485417816
	.long	1069626316
	.long	1403757309
	.long	3217886718
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1015203297
	.long	536870912
	.long	1013450602
	.long	0
	.long	1071644672
	.long	0
	.long	1072693248
	.long	2583490354
	.long	1070236281
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	1015799092
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	0
	.long	1071644672
	.long	1403757309
	.long	3217886718
	.long	2485417816
	.long	1069626316
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3160934250
	.long	536870912
	.long	3162686945
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1539668340
	.long	3217396327
	.long	2598800519
	.long	1068266419
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	1072339814
	.long	536870912
	.long	3163235805
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	1945768569
	.long	3216915048
	.long	2140183630
	.long	3214756396
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	1072445618
	.long	536870912
	.long	3160280457
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2255197647
	.long	3216211105
	.long	1699043957
	.long	3216902261
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	1072533611
	.long	3758096384
	.long	1013394669
	.long	536870912
	.long	1014257638
	.long	0
	.long	1072693248
	.long	0
	.long	1071644672
	.long	2476548698
	.long	3215330282
	.long	1991047213
	.long	1067753521
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	1072602945
	.long	2684354560
	.long	1014354573
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	18115067
	.long	3214126342
	.long	240740309
	.long	3215727903
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	1072652951
	.long	3221225472
	.long	1013083417
	.long	536870912
	.long	1014325783
	.long	0
	.long	1072693248
	.long	0
	.long	1070596096
	.long	393047345
	.long	3212032302
	.long	257503056
	.long	3214647653
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	1072683149
	.long	3758096384
	.long	1010706200
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1072693248
	.long	0
	.long	1069547520
	.type	Ctable,@object
	.size	Ctable,4096
	.space 512, 0x00 	# pad
	.align 16
P_3:
	.long	771977331
	.long	996350346
	.long	771977331
	.long	996350346
	.type	P_3,@object
	.size	P_3,16
	.align 16
SC_2:
	.long	286331153
	.long	1065423121
	.long	1431655765
	.long	1067799893
	.type	SC_2,@object
	.size	SC_2,16
	.align 16
SC_3:
	.long	436314138
	.long	3207201184
	.long	381774871
	.long	3210133868
	.type	SC_3,@object
	.size	SC_3,16
	.align 16
SC_1:
	.long	1431655765
	.long	3217380693
	.long	0
	.long	3219128320
	.type	SC_1,@object
	.size	SC_1,16
	.align 16
TWO_POW_55:
	.long	0
	.long	1130364928
	.type	TWO_POW_55,@object
	.size	TWO_POW_55,8
	.align 8
ONE:
	.long	0
	.long	1072693248
	.type	ONE,@object
	.size	ONE,8
	.align 8
ALL_ONES:
	.long	4294967295
	.long	1072693247
	.type	ALL_ONES,@object
	.size	ALL_ONES,8
	.align 8
TWO_POW_M55:
	.long	0
	.long	3162505216
	.type	TWO_POW_M55,@object
	.size	TWO_POW_M55,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
