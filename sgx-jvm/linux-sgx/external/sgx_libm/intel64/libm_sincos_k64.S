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
	.file "libm_sincos_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_sincos_k64
	.text
       .align    16,0x90
	.globl __libm_sincos_k64
__libm_sincos_k64:
# parameter 1: %xmm0
# parameter 2: %rdi
# parameter 3: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___libm_sincos_k64.1:
..L2:

        pushq     %r15
	.cfi_def_cfa_offset 16
	.cfi_offset 15, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        subq      $56, %rsp
	.cfi_def_cfa_offset 80
        movq      %rsi, %r15
        movsd     %xmm0, (%rsp)
        movq      %rdi, %rbx
        movzwl    6(%rsp), %edx
        movq      %fs:40, %rax
        andl      $32752, %edx
        movsd     (%rsp), %xmm0
        xorq      %rsp, %rax
        movq      %rax, 48(%rsp)
        movb      7(%rsp), %al
        andb      $127, %al
        movsd     %xmm0, 8(%rsp)
        cmpl      $32752, %edx
        je        ..B1.13
..B1.2:
        movb      %al, 15(%rsp)
        movzwl    14(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $771, %eax
        jl        ..B1.9
..B1.3:
        cmpl      $1040, %eax
        jge       ..B1.5
..B1.4:
        lea       2176+sincos_table(%rip), %rax
        movsd     (%rax), %xmm1
        xorl      %eax, %eax
        movsd     %xmm1, 40(%rsp)
        jmp       ..B1.7
..B1.5:
        lea       32(%rsp), %rdi
..___tag_value___libm_sincos_k64.8:
        call      __libm_reduce_pio2d@PLT
..___tag_value___libm_sincos_k64.9:
..B1.6:
        movsd     32(%rsp), %xmm0
        movsd     40(%rsp), %xmm1
..B1.7:
        lea       2048+sincos_table(%rip), %rcx
        lea       2056+sincos_table(%rip), %rsi
        lea       2064+sincos_table(%rip), %r8
        lea       2072+sincos_table(%rip), %r9
        lea       2112+sincos_table(%rip), %r11
        lea       2080+sincos_table(%rip), %r10
        shll      $4, %eax
        movsd     (%rcx), %xmm13
        lea       2104+sincos_table(%rip), %rcx
        mulsd     %xmm0, %xmm13
        movsd     (%r8), %xmm11
        lea       2144+sincos_table(%rip), %r8
        movsd     (%r9), %xmm12
        lea       2136+sincos_table(%rip), %r9
        movsd     (%r11), %xmm15
        lea       2120+sincos_table(%rip), %r11
        addsd     (%rsi), %xmm13
        movsd     %xmm13, (%rsp)
        movl      (%rsp), %edi
        subsd     (%rsi), %xmm13
        lea       2096+sincos_table(%rip), %rsi
        mulsd     %xmm13, %xmm11
        lea       (%rdi,%rax), %edx
        mulsd     %xmm13, %xmm12
        mulsd     (%r10), %xmm13
        subsd     %xmm11, %xmm0
        movaps    %xmm0, %xmm10
        lea       2128+sincos_table(%rip), %r10
        movsd     (%r8), %xmm11
        lea       16(%rdi,%rax), %eax
        lea       2088+sincos_table(%rip), %rdi
        andl      $63, %edx
        shll      $2, %edx
        andl      $63, %eax
        shll      $2, %eax
        subsd     %xmm12, %xmm10
        movaps    %xmm10, %xmm14
        subsd     %xmm10, %xmm0
        mulsd     %xmm10, %xmm14
        subsd     %xmm12, %xmm0
        mulsd     %xmm14, %xmm11
        mulsd     %xmm14, %xmm15
        subsd     %xmm13, %xmm0
        addsd     (%r9), %xmm11
        addsd     (%rcx), %xmm15
        addsd     %xmm1, %xmm0
        mulsd     %xmm14, %xmm11
        mulsd     %xmm14, %xmm15
        addsd     (%r10), %xmm11
        addsd     (%rsi), %xmm15
        mulsd     %xmm14, %xmm11
        mulsd     %xmm14, %xmm15
        addsd     (%r11), %xmm11
        addsd     (%rdi), %xmm15
        mulsd     %xmm14, %xmm11
        mulsd     %xmm14, %xmm15
        lea       sincos_table(%rip), %rcx
        movaps    %xmm10, %xmm12
        mulsd     %xmm10, %xmm15
        movsd     8(%rcx,%rdx,8), %xmm2
        movaps    %xmm2, %xmm1
        mulsd     %xmm11, %xmm1
        mulsd     %xmm2, %xmm12
        movsd     (%rcx,%rdx,8), %xmm9
        movsd     (%rcx,%rax,8), %xmm6
        movaps    %xmm9, %xmm8
        movsd     24(%rcx,%rdx,8), %xmm4
        movaps    %xmm6, %xmm3
        movsd     24(%rcx,%rax,8), %xmm5
        addsd     %xmm4, %xmm8
        mulsd     %xmm10, %xmm4
        addsd     %xmm5, %xmm3
        mulsd     %xmm10, %xmm5
        mulsd     %xmm10, %xmm9
        mulsd     %xmm10, %xmm6
        movsd     %xmm1, 16(%rsp)
        movaps    %xmm4, %xmm13
        movsd     8(%rcx,%rax,8), %xmm1
        movaps    %xmm5, %xmm14
        mulsd     %xmm1, %xmm10
        addsd     %xmm2, %xmm13
        addsd     %xmm1, %xmm14
        mulsd     %xmm1, %xmm11
        subsd     %xmm13, %xmm2
        subsd     %xmm14, %xmm1
        addsd     %xmm4, %xmm2
        addsd     %xmm5, %xmm1
        movaps    %xmm8, %xmm7
        subsd     %xmm12, %xmm8
        mulsd     %xmm15, %xmm7
        mulsd     %xmm3, %xmm15
        subsd     %xmm10, %xmm3
        mulsd     %xmm0, %xmm8
        mulsd     %xmm3, %xmm0
        addsd     16(%rcx,%rdx,8), %xmm8
        addsd     16(%rcx,%rax,8), %xmm0
        addsd     %xmm2, %xmm8
        addsd     %xmm1, %xmm0
        movsd     %xmm11, 24(%rsp)
        movaps    %xmm13, %xmm11
        movsd     %xmm15, 8(%rsp)
        movaps    %xmm14, %xmm15
        addsd     %xmm9, %xmm11
        addsd     %xmm6, %xmm15
        subsd     %xmm11, %xmm13
        subsd     %xmm15, %xmm14
        addsd     %xmm13, %xmm9
        addsd     %xmm14, %xmm6
        addsd     %xmm9, %xmm8
        addsd     %xmm6, %xmm0
        addsd     %xmm7, %xmm8
        addsd     8(%rsp), %xmm0
        addsd     16(%rsp), %xmm8
        addsd     24(%rsp), %xmm0
        movsd     %xmm11, (%rbx)
        movsd     %xmm8, 8(%rbx)
        movsd     %xmm15, (%r15)
        movsd     %xmm0, 8(%r15)
        movq      48(%rsp), %rbx
        xorq      %rsp, %rbx
        cmpq      %fs:40, %rbx
        jne       ..B1.11
..B1.8:
        addq      $56, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -24
	.cfi_offset 15, -16
..B1.9:
        xorl      %eax, %eax
        movsd     %xmm0, (%rbx)
        movq      %rax, 8(%rbx)
        movq      $0x3ff0000000000000, %rbx
        movq      %rbx, (%r15)
        movq      %rax, 8(%r15)
        movq      48(%rsp), %rdx
        xorq      %rsp, %rdx
        cmpq      %fs:40, %rdx
        jne       ..B1.11
..B1.10:
        addq      $56, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -24
	.cfi_offset 15, -16
..B1.11:
        call      __stack_chk_fail@PLT
..B1.13:
        lea       2176+sincos_table(%rip), %rax
        xorl      %edx, %edx
        movq      %rdx, 8(%rbx)
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        movsd     %xmm0, (%rbx)
        movsd     %xmm0, (%r15)
        movq      %rdx, 8(%r15)
        movq      48(%rsp), %rcx
        xorq      %rsp, %rcx
        cmpq      %fs:40, %rcx
        jne       ..B1.11
        jmp       ..B1.8
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sincos_k64,@function
	.size	__libm_sincos_k64,.-__libm_sincos_k64
	.data
# -- End  __libm_sincos_k64
	.section .rodata, "a"
	.align 16
	.align 16
sincos_table:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	393047345
	.long	3212032302
	.long	3156849708
	.long	1069094822
	.long	3758096384
	.long	3158189848
	.long	0
	.long	1072693248
	.long	18115067
	.long	3214126342
	.long	1013556747
	.long	1070135480
	.long	3221225472
	.long	3160567065
	.long	0
	.long	1072693248
	.long	2476548698
	.long	3215330282
	.long	785751814
	.long	1070765062
	.long	2684354560
	.long	3161838221
	.long	0
	.long	1072693248
	.long	2255197647
	.long	3216211105
	.long	2796464483
	.long	1071152610
	.long	3758096384
	.long	3160878317
	.long	0
	.long	1072693248
	.long	1945768569
	.long	3216915048
	.long	939980347
	.long	1071524701
	.long	536870912
	.long	1012796809
	.long	0
	.long	1072693248
	.long	1539668340
	.long	3217396327
	.long	967731400
	.long	1071761211
	.long	536870912
	.long	1015752157
	.long	0
	.long	1072693248
	.long	1403757309
	.long	3217886718
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1013450602
	.long	0
	.long	1072693248
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	0
	.long	1071644672
	.long	2485417816
	.long	1069626316
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3162686945
	.long	0
	.long	1071644672
	.long	2598800519
	.long	1068266419
	.long	688824739
	.long	1072339814
	.long	3758096384
	.long	1010431536
	.long	0
	.long	1071644672
	.long	2140183630
	.long	3214756396
	.long	4051746225
	.long	1072445618
	.long	2147483648
	.long	3161907377
	.long	0
	.long	1071644672
	.long	1699043957
	.long	3216902261
	.long	3476196678
	.long	1072533611
	.long	536870912
	.long	1014257638
	.long	0
	.long	1071644672
	.long	1991047213
	.long	1067753521
	.long	1455828442
	.long	1072602945
	.long	3758096384
	.long	1015505073
	.long	0
	.long	1070596096
	.long	240740309
	.long	3215727903
	.long	3489094832
	.long	1072652951
	.long	536870912
	.long	1014325783
	.long	0
	.long	1070596096
	.long	257503056
	.long	3214647653
	.long	2748392742
	.long	1072683149
	.long	1073741824
	.long	3163061750
	.long	0
	.long	1069547520
	.long	0
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	257503056
	.long	1067164005
	.long	2748392742
	.long	1072683149
	.long	1073741824
	.long	3163061750
	.long	0
	.long	3217031168
	.long	240740309
	.long	1068244255
	.long	3489094832
	.long	1072652951
	.long	536870912
	.long	1014325783
	.long	0
	.long	3218079744
	.long	1991047213
	.long	3215237169
	.long	1455828442
	.long	1072602945
	.long	3758096384
	.long	1015505073
	.long	0
	.long	3218079744
	.long	1699043957
	.long	1069418613
	.long	3476196678
	.long	1072533611
	.long	536870912
	.long	1014257638
	.long	0
	.long	3219128320
	.long	2140183630
	.long	1067272748
	.long	4051746225
	.long	1072445618
	.long	2147483648
	.long	3161907377
	.long	0
	.long	3219128320
	.long	2598800519
	.long	3215750067
	.long	688824739
	.long	1072339814
	.long	3758096384
	.long	1010431536
	.long	0
	.long	3219128320
	.long	2485417816
	.long	3217109964
	.long	1796544321
	.long	1072217216
	.long	536870912
	.long	3162686945
	.long	0
	.long	3219128320
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	1072079006
	.long	536870912
	.long	3163282740
	.long	0
	.long	3219128320
	.long	1403757309
	.long	1070403070
	.long	621354454
	.long	1071926515
	.long	536870912
	.long	1013450602
	.long	0
	.long	3220176896
	.long	1539668340
	.long	1069912679
	.long	967731400
	.long	1071761211
	.long	536870912
	.long	1015752157
	.long	0
	.long	3220176896
	.long	1945768569
	.long	1069431400
	.long	939980347
	.long	1071524701
	.long	536870912
	.long	1012796809
	.long	0
	.long	3220176896
	.long	2255197647
	.long	1068727457
	.long	2796464483
	.long	1071152610
	.long	3758096384
	.long	3160878317
	.long	0
	.long	3220176896
	.long	2476548698
	.long	1067846634
	.long	785751814
	.long	1070765062
	.long	2684354560
	.long	3161838221
	.long	0
	.long	3220176896
	.long	18115067
	.long	1066642694
	.long	1013556747
	.long	1070135480
	.long	3221225472
	.long	3160567065
	.long	0
	.long	3220176896
	.long	393047345
	.long	1064548654
	.long	3156849708
	.long	1069094822
	.long	3758096384
	.long	3158189848
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.long	393047345
	.long	1064548654
	.long	3156849708
	.long	3216578470
	.long	3758096384
	.long	1010706200
	.long	0
	.long	3220176896
	.long	18115067
	.long	1066642694
	.long	1013556747
	.long	3217619128
	.long	3221225472
	.long	1013083417
	.long	0
	.long	3220176896
	.long	2476548698
	.long	1067846634
	.long	785751814
	.long	3218248710
	.long	2684354560
	.long	1014354573
	.long	0
	.long	3220176896
	.long	2255197647
	.long	1068727457
	.long	2796464483
	.long	3218636258
	.long	3758096384
	.long	1013394669
	.long	0
	.long	3220176896
	.long	1945768569
	.long	1069431400
	.long	939980347
	.long	3219008349
	.long	536870912
	.long	3160280457
	.long	0
	.long	3220176896
	.long	1539668340
	.long	1069912679
	.long	967731400
	.long	3219244859
	.long	536870912
	.long	3163235805
	.long	0
	.long	3220176896
	.long	1403757309
	.long	1070403070
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3160934250
	.long	0
	.long	3220176896
	.long	2583490354
	.long	3217719929
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	0
	.long	3219128320
	.long	2485417816
	.long	3217109964
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1015203297
	.long	0
	.long	3219128320
	.long	2598800519
	.long	3215750067
	.long	688824739
	.long	3219823462
	.long	3758096384
	.long	3157915184
	.long	0
	.long	3219128320
	.long	2140183630
	.long	1067272748
	.long	4051746225
	.long	3219929266
	.long	2147483648
	.long	1014423729
	.long	0
	.long	3219128320
	.long	1699043957
	.long	1069418613
	.long	3476196678
	.long	3220017259
	.long	536870912
	.long	3161741286
	.long	0
	.long	3219128320
	.long	1991047213
	.long	3215237169
	.long	1455828442
	.long	3220086593
	.long	3758096384
	.long	3162988721
	.long	0
	.long	3218079744
	.long	240740309
	.long	1068244255
	.long	3489094832
	.long	3220136599
	.long	536870912
	.long	3161809431
	.long	0
	.long	3218079744
	.long	257503056
	.long	1067164005
	.long	2748392742
	.long	3220166797
	.long	1073741824
	.long	1015578102
	.long	0
	.long	3217031168
	.long	0
	.long	2147483648
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	257503056
	.long	3214647653
	.long	2748392742
	.long	3220166797
	.long	1073741824
	.long	1015578102
	.long	0
	.long	1069547520
	.long	240740309
	.long	3215727903
	.long	3489094832
	.long	3220136599
	.long	536870912
	.long	3161809431
	.long	0
	.long	1070596096
	.long	1991047213
	.long	1067753521
	.long	1455828442
	.long	3220086593
	.long	3758096384
	.long	3162988721
	.long	0
	.long	1070596096
	.long	1699043957
	.long	3216902261
	.long	3476196678
	.long	3220017259
	.long	536870912
	.long	3161741286
	.long	0
	.long	1071644672
	.long	2140183630
	.long	3214756396
	.long	4051746225
	.long	3219929266
	.long	2147483648
	.long	1014423729
	.long	0
	.long	1071644672
	.long	2598800519
	.long	1068266419
	.long	688824739
	.long	3219823462
	.long	3758096384
	.long	3157915184
	.long	0
	.long	1071644672
	.long	2485417816
	.long	1069626316
	.long	1796544321
	.long	3219700864
	.long	536870912
	.long	1015203297
	.long	0
	.long	1071644672
	.long	2583490354
	.long	1070236281
	.long	1719614413
	.long	3219562654
	.long	536870912
	.long	1015799092
	.long	0
	.long	1071644672
	.long	1403757309
	.long	3217886718
	.long	621354454
	.long	3219410163
	.long	536870912
	.long	3160934250
	.long	0
	.long	1072693248
	.long	1539668340
	.long	3217396327
	.long	967731400
	.long	3219244859
	.long	536870912
	.long	3163235805
	.long	0
	.long	1072693248
	.long	1945768569
	.long	3216915048
	.long	939980347
	.long	3219008349
	.long	536870912
	.long	3160280457
	.long	0
	.long	1072693248
	.long	2255197647
	.long	3216211105
	.long	2796464483
	.long	3218636258
	.long	3758096384
	.long	1013394669
	.long	0
	.long	1072693248
	.long	2476548698
	.long	3215330282
	.long	785751814
	.long	3218248710
	.long	2684354560
	.long	1014354573
	.long	0
	.long	1072693248
	.long	18115067
	.long	3214126342
	.long	1013556747
	.long	3217619128
	.long	3221225472
	.long	1013083417
	.long	0
	.long	1072693248
	.long	393047345
	.long	3212032302
	.long	3156849708
	.long	3216578470
	.long	3758096384
	.long	1010706200
	.long	0
	.long	1072693248
	.long	1841940611
	.long	1076125488
	.long	0
	.long	1127743488
	.long	1413480448
	.long	1069097467
	.long	442499072
	.long	1032893537
	.long	771977331
	.long	996350346
	.long	1431655765
	.long	3217380693
	.long	286331153
	.long	1065423121
	.long	436314138
	.long	3207201184
	.long	2773927732
	.long	1053236707
	.long	0
	.long	3219128320
	.long	1431655765
	.long	1067799893
	.long	381774871
	.long	3210133868
	.long	436314138
	.long	1056571808
	.long	0
	.long	1130364928
	.long	0
	.long	1015021568
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	2146435072
	.type	sincos_table,@object
	.size	sincos_table,2192
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
