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
	.file "dpml_ux_log.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_log_poly__
	.text
       .align    16,0x90
	.globl __dpml_ux_log_poly__
__dpml_ux_log_poly__:
# parameter 1: %rdi
# parameter 2: %rsi
..B1.1:
	.cfi_startproc
..___tag_value___dpml_ux_log_poly__.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        movq      %rsi, %r13
        lea       128+__log_x_table(%rip), %rsi
        movl      $17, %edx
        movl      $6, %ecx
        movq      %r13, %r8
..___tag_value___dpml_ux_log_poly__.5:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_log_poly__.6:
..B1.2:
        movq      %r13, %rdi
        lea       472+__log_x_table(%rip), %rsi
        movq      %r13, %rdx
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        jmp       __dpml_multiply__@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_log_poly__,@function
	.size	__dpml_ux_log_poly__,.-__dpml_ux_log_poly__
	.data
# -- End  __dpml_ux_log_poly__
	.text
# -- Begin  __dpml_ux_log__
	.text
       .align    16,0x90
	.globl __dpml_ux_log__
__dpml_ux_log__:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B2.1:
	.cfi_startproc
..___tag_value___dpml_ux_log__.10:
..L11:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        subq      $56, %rsp
	.cfi_def_cfa_offset 96
        movq      %rdi, %r13
        lea       112+__log_x_table(%rip), %r8
        movq      %rdx, %r15
        movq      %rsi, %r14
        lea       424+__log_x_table(%rip), %rsi
        movl      $6, %edx
        lea       (%rsp), %rcx
        movl      4(%r13), %r10d
        movq      (%r8), %r9
        cmpq      8(%r13), %r9
        movq      %fs:40, %rax
        lea       -1(%r10), %ebx
        adcl      $0, %ebx
        xorq      %rsp, %rax
        movq      %rax, 48(%rcx)
        subl      %ebx, %r10d
        movl      %r10d, 4(%r13)
..___tag_value___dpml_ux_log__.21:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_log__.22:
..B2.2:
        movl      $2, %edx
        lea       24(%rsp), %rdi
        movq      %r15, %rcx
        lea       (%rsp), %rsi
        addl      %ebx, 4(%r13)
..___tag_value___dpml_ux_log__.23:
        call      __dpml_divide__@PLT
..___tag_value___dpml_ux_log__.24:
..B2.3:
        movq      %r15, %rdi
        lea       128+__log_x_table(%rip), %rsi
        movl      $17, %edx
        movl      $6, %ecx
        lea       (%rsp), %r8
..___tag_value___dpml_ux_log__.25:
        call      __dpml_evaluate_rational__@PLT
..___tag_value___dpml_ux_log__.26:
..B2.4:
        movq      %r15, %rdi
        movl      $1, %esi
        movslq    %ebx, %rbx
        movq      %rbx, 8(%r15)
..___tag_value___dpml_ux_log__.27:
        call      __dpml_ffs_and_shift__@PLT
..___tag_value___dpml_ux_log__.28:
..B2.5:
        movq      %r15, %rdi
        lea       (%rsp), %rsi
        movl      $8, %edx
        movq      %r15, %rcx
..___tag_value___dpml_ux_log__.29:
        call      __dpml_addsub__@PLT
..___tag_value___dpml_ux_log__.30:
..B2.6:
        testq     %r14, %r14
        je        ..B2.8
..B2.7:
        movq      %r15, %rdi
        movq      %r14, %rsi
        movq      %r15, %rdx
..___tag_value___dpml_ux_log__.31:
        call      __dpml_multiply__@PLT
..___tag_value___dpml_ux_log__.32:
..B2.8:
        xorl      %edx, %edx
        movl      $1, %eax
        xorl      %ecx, %ecx
        cmpl      $-262144, 28(%rsp)
        movq      48(%rsp), %rbx
        cmovne    %eax, %ecx
        testq     %r14, %r14
        cmovne    %eax, %edx
        xorq      %rsp, %rbx
        orl       %edx, %ecx
        cmpq      %fs:40, %rbx
        jne       ..B2.10
..B2.9:
        movq      %rcx, %rax
        addq      $56, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 96
	.cfi_offset 3, -40
	.cfi_offset 13, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B2.10:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.cfi_endproc
	.type	__dpml_ux_log__,@function
	.size	__dpml_ux_log__,.-__dpml_ux_log__
	.data
# -- End  __dpml_ux_log__
	.section .rodata, "a"
	.align 16
	.align 16
__log_x_table:
	.long	1088750600
	.long	519758862
	.long	58
	.long	0
	.long	59
	.long	0
	.long	60
	.long	0
	.long	1088750600
	.long	519758862
	.long	61
	.long	0
	.long	62
	.long	0
	.long	63
	.long	0
	.long	1088750600
	.long	519758862
	.long	64
	.long	0
	.long	65
	.long	0
	.long	66
	.long	0
	.long	15008776
	.long	336609536
	.long	58
	.long	0
	.long	4192101508
	.long	3037000499
	.long	4243534402
	.long	1518500249
	.long	1261892956
	.long	1305769775
	.long	706307789
	.long	114052365
	.long	2125978754
	.long	1401519948
	.long	2628436525
	.long	90640697
	.long	1075745124
	.long	4046945230
	.long	850547722
	.long	100185346
	.long	3858047057
	.long	2069326047
	.long	3415016796
	.long	106820841
	.long	196219186
	.long	3461778520
	.long	1283971156
	.long	114747264
	.long	133398994
	.long	3894487089
	.long	1548005786
	.long	123926548
	.long	4002430868
	.long	3905024888
	.long	1104860464
	.long	134702783
	.long	1396868873
	.long	2152947579
	.long	2102985887
	.long	147531619
	.long	361329122
	.long	2531697111
	.long	2794113080
	.long	163061263
	.long	2117398987
	.long	2352680084
	.long	3122626672
	.long	182244941
	.long	299366278
	.long	2801833386
	.long	1248329477
	.long	206544267
	.long	99864504
	.long	1693623838
	.long	1770762254
	.long	238320308
	.long	959885721
	.long	1462617677
	.long	2483170600
	.long	281651273
	.long	2353710846
	.long	1786830293
	.long	2080549112
	.long	344240445
	.long	2781107612
	.long	1683788319
	.long	2061424959
	.long	442594858
	.long	2924123738
	.long	1498310185
	.long	3744988402
	.long	619632801
	.long	2019498699
	.long	1065527877
	.long	1946680041
	.long	1032721336
	.long	1763524233
	.long	3196583632
	.long	1545072827
	.long	3098164009
	.long	2
	.long	0
	.long	0
	.long	1
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	2
	.long	0
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	0
	.long	3520035243
	.long	2977044471
	.long	66254511
	.long	3387143064
	.long	0
	.long	4294967295
	.long	4224710552
	.long	2585827972
	.long	192713080
	.long	2408143276
	.long	0
	.long	0
	.type	__log_x_table,@object
	.size	__log_x_table,528
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
