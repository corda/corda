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
	.hidden __dpml_ux_log_poly__
	.globl __dpml_ux_log_poly__
__dpml_ux_log_poly__:
# parameter 1: 48 + %esp
# parameter 2: 52 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        xorl      %eax, %eax
        movl      52(%esp), %edi
        addl      $28, %esp
        pushl     %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        pushl     %eax
        pushl     $6
        pushl     %eax
        pushl     $17
        lea       128+__log_x_table@GOTOFF(%ebx), %edx
        pushl     %edx
        pushl     44(%esp)
        call      __dpml_evaluate_rational__
..B1.2:
        addl      $12, %esp
        lea       472+__log_x_table@GOTOFF(%ebx), %eax
        pushl     %edi
        pushl     %eax
        pushl     %edi
        call      __dpml_multiply__
..B1.3:
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        ret       
        .align    16,0x90
	.type	__dpml_ux_log_poly__,@function
	.size	__dpml_ux_log_poly__,.-__dpml_ux_log_poly__
	.data
# -- End  __dpml_ux_log_poly__
	.text
# -- Begin  __dpml_ux_log__
	.text
       .align    16,0x90
	.hidden __dpml_ux_log__
	.globl __dpml_ux_log__
__dpml_ux_log__:
# parameter 1: 112 + %esp
# parameter 2: 116 + %esp
# parameter 3: 120 + %esp
..B2.1:
..L3:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $92, %esp
        movl      112(%esp), %ecx
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        movl      8(%ecx), %esi
        movl      %gs:20, %eax
        xorl      %esp, %eax
        subl      112+__log_x_table@GOTOFF(%ebx), %esi
        movl      12(%ecx), %ebp
        movl      4(%ecx), %edx
        movl      %edx, %edi
        movl      %eax, 80(%esp)
        sbbl      116+__log_x_table@GOTOFF(%ebx), %ebp
        jb        ..B2.2
..B2.19:
        orl       %ebp, %esi
        jne       ..B2.3
..B2.2:
        lea       -1(%edx), %edi
..B2.3:
        addl      $20, %esp
        lea       424+__log_x_table@GOTOFF(%ebx), %ebp
        subl      %edi, %edx
        lea       12(%esp), %esi
        pushl     %esi
        pushl     $0
        pushl     $6
        pushl     %ebp
        pushl     %ecx
        movl      %edx, 4(%ecx)
        call      __dpml_addsub__
..B2.4:
        movl      112(%esp), %ecx
        movl      120(%esp), %ebp
        addl      $20, %esp
        addl      %edi, 4(%ecx)
        lea       36(%esp), %edx
        pushl     %ebp
        pushl     $0
        pushl     $2
        pushl     %esi
        pushl     %edx
        call      __dpml_divide__
..B2.5:
        addl      $28, %esp
        xorl      %edx, %edx
        pushl     %esi
        pushl     %edx
        pushl     $6
        pushl     %edx
        pushl     $17
        lea       128+__log_x_table@GOTOFF(%ebx), %ecx
        pushl     %ecx
        pushl     %ebp
        call      __dpml_evaluate_rational__
..B2.6:
        addl      $12, %esp
        movl      %edi, %eax
        pushl     $0
        pushl     $1
        cltd      
        pushl     %ebp
        movl      %edi, 8(%ebp)
        movl      %edx, 12(%ebp)
        call      __dpml_ffs_and_shift__
..B2.7:
        addl      $20, %esp
        pushl     %ebp
        pushl     $0
        pushl     $8
        pushl     %esi
        pushl     %ebp
        call      __dpml_addsub__
..B2.8:
        movl      116(%esp), %esi
        testl     %esi, %esi
        je        ..B2.10
..B2.9:
        addl      $12, %esp
        pushl     %ebp
        pushl     %esi
        pushl     %ebp
        call      __dpml_multiply__
..B2.10:
        cmpl      $-262144, 60(%esp)
        je        ..B2.15
..B2.11:
        movl      $1, %eax
..B2.12:
        movl      80(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.14
..B2.13:
        xorl      %edx, %edx
        addl      $92, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B2.14:
        call      __stack_chk_fail@PLT
..B2.15:
        testl     %esi, %esi
        jne       ..B2.11
..B2.16:
        xorl      %eax, %eax
        jmp       ..B2.12
        .align    16,0x90
	.type	__dpml_ux_log__,@function
	.size	__dpml_ux_log__,.-__dpml_ux_log__
	.data
# -- End  __dpml_ux_log__
	.section .rodata, "a"
	.align 4
	.align 4
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
	.hidden __dpml_multiply__
	.hidden __dpml_divide__
	.hidden __dpml_ffs_and_shift__
	.hidden __dpml_addsub__
	.hidden __dpml_evaluate_rational__
	.section .note.GNU-stack, ""
# End
