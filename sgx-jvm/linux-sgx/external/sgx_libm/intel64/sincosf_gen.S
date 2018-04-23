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
	.file "sincosf_gen.c"
	.text
..TXTST0:
# -- Begin  sincosf
	.text
       .align    16,0x90
	.globl sincosf
sincosf:
# parameter 1: %xmm0
# parameter 2: %rdi
# parameter 3: %rsi
..B1.1:
	.cfi_startproc
..___tag_value_sincosf.1:
..L2:

        subq      $40, %rsp
	.cfi_def_cfa_offset 48
        movq      %rdi, (%rsp)
        movq      %rsi, 8(%rsp)
..B1.2:
        pextrw    $1, %xmm0, %eax
        andw      $32767, %ax
        subw      $128, %ax
        cmpw      $17791, %ax
        ja        .L_2TAG_PACKET_0.0.1
        movq      PI_32_RECIP(%rip), %xmm1
        mulss     %xmm0, %xmm1
        movapd    BIT_31(%rip), %xmm3
        cvtss2si  %xmm1, %edx
        movl      %edx, %ecx
        andpd     %xmm0, %xmm3
        cvtss2sd  %xmm0, %xmm0
        movlhps   %xmm0, %xmm0
        addss     SHIFTER(%rip), %xmm1
        subss     SHIFTER(%rip), %xmm1
        movapd    %xmm3, %xmm4
        psllq     $32, %xmm3
        psllq     $29, %xmm4
        xorpd     %xmm4, %xmm3
        addl      $1865216, %edx
        shll      $4, %edx
        addl      $1865232, %ecx
        shll      $4, %ecx
        movq      P_1(%rip), %xmm2
        xorpd     %xmm3, %xmm2
        xorpd     P_2(%rip), %xmm3
        movlhps   %xmm2, %xmm2
        movlhps   %xmm3, %xmm3
        lea       Ctable(%rip), %rax
        andq      $1008, %rdx
        andq      $1008, %rcx
        addq      %rax, %rcx
        addq      %rdx, %rax
        psllq     $29, %xmm1
        movlhps   %xmm1, %xmm1
        mulpd     %xmm1, %xmm2
        mulpd     %xmm1, %xmm3
        subpd     %xmm2, %xmm0
        addpd     %xmm0, %xmm3
        mulpd     %xmm0, %xmm0
        movapd    C_1(%rip), %xmm4
        movapd    S_1(%rip), %xmm5
        movapd    ONE(%rip), %xmm1
        mulpd     %xmm0, %xmm4
        mulpd     %xmm0, %xmm5
        movapd    C_2(%rip), %xmm6
        movapd    S_2(%rip), %xmm7
        mulpd     %xmm0, %xmm0
        addpd     %xmm1, %xmm4
        mulpd     %xmm0, %xmm6
        addpd     %xmm1, %xmm5
        mulpd     %xmm0, %xmm7
        addpd     %xmm6, %xmm4
        addpd     %xmm7, %xmm5
        movq      (%rax), %xmm2
        movq      8(%rax), %xmm1
        movhpd    (%rcx), %xmm2
        movhpd    8(%rcx), %xmm1
        mulpd     %xmm1, %xmm3
        mulpd     %xmm4, %xmm2
        mulpd     %xmm5, %xmm3
        addpd     %xmm3, %xmm2
        cvtpd2ps  %xmm2, %xmm0
        movq      (%rsp), %rax
        movss     %xmm0, (%rax)
        movq      8(%rsp), %rax
        pshufd    $1, %xmm0, %xmm0
        movss     %xmm0, (%rax)
        jmp       ..B1.7
.L_2TAG_PACKET_0.0.1:
        jg        .L_2TAG_PACKET_1.0.1
        cvtss2sd  %xmm0, %xmm0
        movq      ONE(%rip), %xmm1
        movq      %xmm0, %xmm2
        mulsd     ONE_M230(%rip), %xmm0
        mulsd     %xmm2, %xmm2
        subsd     %xmm2, %xmm1
        cvtsd2ss  %xmm0, %xmm0
        cvtsd2ss  %xmm1, %xmm1
        movq      (%rsp), %rax
        movss     %xmm0, (%rax)
        movq      8(%rsp), %rax
        movss     %xmm1, (%rax)
        jmp       ..B1.7
.L_2TAG_PACKET_1.0.1:
        movd      %xmm0, %eax
        andl      $2139095040, %eax
        cmpl      $2139095040, %eax
        je        .L_2TAG_PACKET_2.0.1
        cvtss2sd  %xmm0, %xmm0
        movq      %xmm0, 16(%rsp)
..B1.3:
        movsd     16(%rsp), %xmm0
..___tag_value_sincosf.4:
        call      __libm_sse2_sincos@PLT
..___tag_value_sincosf.5:
..B1.4:
        movsd     %xmm0, 24(%rsp)
..B1.5:
        movq      24(%rsp), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        cvtsd2ss  %xmm1, %xmm1
        movq      (%rsp), %rax
        movss     %xmm0, (%rax)
        movq      8(%rsp), %rax
        movss     %xmm1, (%rax)
        jmp       ..B1.7
.L_2TAG_PACKET_2.0.1:
        pxor      %xmm1, %xmm1
        mulss     %xmm1, %xmm0
        movq      (%rsp), %rax
        movss     %xmm0, (%rax)
        movq      8(%rsp), %rax
        movss     %xmm0, (%rax)
.L_2TAG_PACKET_3.0.1:
..B1.7:
        addq      $40, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	sincosf,@function
	.size	sincosf,.-sincosf
	.data
# -- End  sincosf
	.section .rodata, "a"
	.align 16
	.align 16
BIT_31:
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.type	BIT_31,@object
	.size	BIT_31,16
	.align 16
P_1:
	.long	1413758976
	.long	2008621563
	.type	P_1,@object
	.size	P_1,8
	.space 8, 0x00 	# pad
	.align 16
P_2:
	.long	1734816687
	.long	1966270393
	.type	P_2,@object
	.size	P_2,8
	.space 8, 0x00 	# pad
	.align 16
Ctable:
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	1072683149
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	1072652951
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	1072602945
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	1072533611
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	1072445618
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	1072339814
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	1072217216
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	1072079006
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	1071926515
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	1071761211
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	1071524701
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	1071152610
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	1070765062
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	1070135480
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	1069094822
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	2748392742
	.long	1072683149
	.long	3156849708
	.long	3216578470
	.long	3489094832
	.long	1072652951
	.long	1013556747
	.long	3217619128
	.long	1455828442
	.long	1072602945
	.long	785751814
	.long	3218248710
	.long	3476196678
	.long	1072533611
	.long	2796464483
	.long	3218636258
	.long	4051746225
	.long	1072445618
	.long	939980347
	.long	3219008349
	.long	688824739
	.long	1072339814
	.long	967731400
	.long	3219244859
	.long	1796544321
	.long	1072217216
	.long	621354454
	.long	3219410163
	.long	1719614413
	.long	1072079006
	.long	1719614413
	.long	3219562654
	.long	621354454
	.long	1071926515
	.long	1796544321
	.long	3219700864
	.long	967731400
	.long	1071761211
	.long	688824739
	.long	3219823462
	.long	939980347
	.long	1071524701
	.long	4051746225
	.long	3219929266
	.long	2796464483
	.long	1071152610
	.long	3476196678
	.long	3220017259
	.long	785751814
	.long	1070765062
	.long	1455828442
	.long	3220086593
	.long	1013556747
	.long	1070135480
	.long	3489094832
	.long	3220136599
	.long	3156849708
	.long	1069094822
	.long	2748392742
	.long	3220166797
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	3220166797
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	3220136599
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	3220086593
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	3220017259
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	3219929266
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	3219823462
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	3219700864
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	3219562654
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	3219410163
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	3219244859
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	3219008349
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	3218636258
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	3218248710
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	3217619128
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	3216578470
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	2748392742
	.long	3220166797
	.long	3156849708
	.long	1069094822
	.long	3489094832
	.long	3220136599
	.long	1013556747
	.long	1070135480
	.long	1455828442
	.long	3220086593
	.long	785751814
	.long	1070765062
	.long	3476196678
	.long	3220017259
	.long	2796464483
	.long	1071152610
	.long	4051746225
	.long	3219929266
	.long	939980347
	.long	1071524701
	.long	688824739
	.long	3219823462
	.long	967731400
	.long	1071761211
	.long	1796544321
	.long	3219700864
	.long	621354454
	.long	1071926515
	.long	1719614413
	.long	3219562654
	.long	1719614413
	.long	1072079006
	.long	621354454
	.long	3219410163
	.long	1796544321
	.long	1072217216
	.long	967731400
	.long	3219244859
	.long	688824739
	.long	1072339814
	.long	939980347
	.long	3219008349
	.long	4051746225
	.long	1072445618
	.long	2796464483
	.long	3218636258
	.long	3476196678
	.long	1072533611
	.long	785751814
	.long	3218248710
	.long	1455828442
	.long	1072602945
	.long	1013556747
	.long	3217619128
	.long	3489094832
	.long	1072652951
	.long	3156849708
	.long	3216578470
	.long	2748392742
	.long	1072683149
	.type	Ctable,@object
	.size	Ctable,1024
	.align 16
C_1:
	.long	0
	.long	3219128320
	.long	0
	.long	3219128320
	.type	C_1,@object
	.size	C_1,16
	.align 16
S_1:
	.long	1431655765
	.long	3217380693
	.long	1431655765
	.long	3217380693
	.type	S_1,@object
	.size	S_1,16
	.align 16
ONE:
	.long	0
	.long	1072693248
	.long	0
	.long	1072693248
	.type	ONE,@object
	.size	ONE,16
	.align 16
C_2:
	.long	1431655765
	.long	1067799893
	.long	1431655765
	.long	1067799893
	.type	C_2,@object
	.size	C_2,16
	.align 16
S_2:
	.long	286331153
	.long	1065423121
	.long	286331153
	.long	1065423121
	.type	S_2,@object
	.size	S_2,16
	.align 8
PI_32_RECIP:
	.long	1092811139
	.long	0
	.type	PI_32_RECIP,@object
	.size	PI_32_RECIP,8
	.align 8
SHIFTER:
	.long	1262485504
	.type	SHIFTER,@object
	.size	SHIFTER,4
	.space 4, 0x00 	# pad
	.align 8
ONE_M230:
	.long	4286578688
	.long	1072693247
	.type	ONE_M230,@object
	.size	ONE_M230,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
