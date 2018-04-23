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
	.file "intel_quad_exception.c"
	.text
..TXTST0:
# -- Begin  __dpml_exception
	.text
       .align    16,0x90
	.hidden __dpml_exception
	.globl __dpml_exception
__dpml_exception:
# parameter 1: 48 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $28, %esp
        xorl      %eax, %eax
        movl      48(%esp), %edx
        movl      (%edx), %ecx
        movl      4(%edx), %edx
        movl      %edx, %ebx
        subl      %eax, %ebx
        jge       ..B1.3
..B1.2:
        andl      $2147483647, %edx
..B1.3:
        call      ..L2
..L2:
        popl      %ebp
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebp), %ebp
        movl      %ecx, %ebx
        andl      $134217727, %ecx
        movl      %edx, %esi
        shll      $5, %esi
        shrl      $27, %ebx
        orl       %ebx, %esi
        movzbl    7+__dpml_response_table@GOTOFF(%ebp,%ecx,8), %edi
        movzbl    6+__dpml_response_table@GOTOFF(%ebp,%ecx,8), %ebx
        movl      %ebx, %eax
        movl      %edi, 16(%esp)
        movl      $4, %edi
        subl      %edi, %eax
        xorl      %edi, %edi
        orl       %edi, %eax
        jne       ..B1.5
..B1.4:
        movl      48(%esp), %eax
        lea       28(%eax), %edi
        jmp       ..B1.6
..B1.5:
        movl      16(%esp), %eax
        shll      $5, %eax
        lea       (%eax,%esi,8), %edi
        addl      __dpml_globals_table@GOT(%ebp), %edi
..B1.6:
        addl      $-4, %ecx
        movl      %ecx, %esi
        adcl      $-1, %edx
        movl      $171, %eax
        subl      %eax, %esi
        movl      $0, %eax
        sbbl      %eax, %edx
        jb        ..B1.7
..B1.89:
        orl       %edx, %esi
        jne       ..B1.74
..B1.7:
        movzbl    .2.9_2.switchtab.1@GOTOFF(%ebp,%ecx), %eax
        lea       ..1.1_0.TAG.12.0.1@GOTOFF(%ebp,%eax), %eax
        jmp       *%eax
..1.1_0.TAG.12.0.1:
..1.1_0.TAG.9.0.1:
..1.1_0.TAG.11.0.1:
..1.1_0.TAG.8.0.1:
..B1.64:
        movl      48(%esp), %edi
        xorl      %ebx, %ebx
        lea       28(%edi), %edi
        jmp       ..B1.75
..1.1_0.TAG.171.0.1:
..1.1_0.TAG.170.0.1:
..1.1_0.TAG.145.0.1:
..1.1_0.TAG.144.0.1:
..1.1_0.TAG.140.0.1:
..1.1_0.TAG.139.0.1:
..1.1_0.TAG.138.0.1:
..1.1_0.TAG.136.0.1:
..1.1_0.TAG.135.0.1:
..1.1_0.TAG.133.0.1:
..1.1_0.TAG.128.0.1:
..1.1_0.TAG.127.0.1:
..1.1_0.TAG.126.0.1:
..1.1_0.TAG.125.0.1:
..1.1_0.TAG.116.0.1:
..1.1_0.TAG.115.0.1:
..1.1_0.TAG.114.0.1:
..1.1_0.TAG.112.0.1:
..1.1_0.TAG.111.0.1:
..1.1_0.TAG.104.0.1:
..1.1_0.TAG.103.0.1:
..1.1_0.TAG.102.0.1:
..1.1_0.TAG.101.0.1:
..1.1_0.TAG.100.0.1:
..1.1_0.TAG.99.0.1:
..1.1_0.TAG.98.0.1:
..1.1_0.TAG.97.0.1:
..1.1_0.TAG.79.0.1:
..1.1_0.TAG.76.0.1:
..1.1_0.TAG.67.0.1:
..1.1_0.TAG.64.0.1:
..1.1_0.TAG.62.0.1:
..1.1_0.TAG.61.0.1:
..1.1_0.TAG.59.0.1:
..1.1_0.TAG.58.0.1:
..1.1_0.TAG.56.0.1:
..1.1_0.TAG.55.0.1:
..1.1_0.TAG.34.0.1:
..1.1_0.TAG.33.0.1:
..1.1_0.TAG.32.0.1:
..1.1_0.TAG.31.0.1:
..1.1_0.TAG.30.0.1:
..1.1_0.TAG.29.0.1:
..1.1_0.TAG.28.0.1:
..1.1_0.TAG.26.0.1:
..1.1_0.TAG.25.0.1:
..1.1_0.TAG.24.0.1:
..1.1_0.TAG.23.0.1:
..1.1_0.TAG.22.0.1:
..1.1_0.TAG.19.0.1:
..1.1_0.TAG.18.0.1:
..1.1_0.TAG.13.0.1:
..1.1_0.TAG.10.0.1:
..1.1_0.TAG.7.0.1:
..1.1_0.TAG.6.0.1:
..1.1_0.TAG.4.0.1:
..1.1_0.TAG.3.0.1:
..1.1_0.TAG.2.0.1:
..1.1_0.SWBASE.0.1:
..1.1_0.TAG.0.0.1:
..B1.74:
        movl      %ebx, %esi
        movl      $4, %edx
        xorl      %ecx, %ecx
        subl      %edx, %esi
        sbbl      %ecx, %ecx
        jb        ..B1.75
..B1.90:
        orl       %ecx, %esi
        jne       ..B1.86
..B1.75:
        movzbl    .2.9_2.switchtab.2@GOTOFF(%ebp,%ebx), %eax
        lea       ..1.1_1.TAG.4.0.1@GOTOFF(%ebp,%eax), %eax
        jmp       *%eax
..1.1_1.TAG.4.0.1:
..B1.77:
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebp)
        movl      $8388608, 12(%esp)
        flds      12(%esp)
        fmulp     %st, %st(1)
        fstps     12(%esp)
        jmp       ..B1.86
..1.1_1.TAG.3.0.1:
..B1.79:
        movl      $2130706432, 8(%esp)
        flds      8(%esp)
        flds      8(%esp)
        fmulp     %st, %st(1)
        fstps     8(%esp)
        jmp       ..B1.86
..1.1_1.TAG.2.0.1:
..B1.81:
        movl      $0, 4(%esp)
..B1.82:
        flds      4(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebp)
        fdivp     %st, %st(1)
        fstps     4(%esp)
        jmp       ..B1.86
..1.1_1.TAG.1.0.1:
..B1.84:
        movl      $2139095040, (%esp)
        flds      (%esp)
        flds      (%esp)
        fsubrp    %st, %st(1)
        fstps     (%esp)
..1.1_1.TAG.0.0.1:
..B1.86:
        movl      %edi, %eax
        addl      $28, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_exception,@function
	.size	__dpml_exception,.-__dpml_exception
	.section .rodata, "a"
	.align 16
	.align 1
.2.9_2.switchtab.1:
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.12.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.byte	..1.1_0.TAG.171.0.1 - ..1.1_0.TAG.12.0.1
	.align 1
.2.9_2.switchtab.2:
	.byte	..1.1_1.TAG.0.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.1.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.2.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.3.0.1 - ..1.1_1.TAG.4.0.1
	.byte	..1.1_1.TAG.4.0.1 - ..1.1_1.TAG.4.0.1
	.data
# -- End  __dpml_exception
	.text
# -- Begin  __dpml_intel_atan_fixup
	.text
       .align    16,0x90
	.globl __dpml_intel_atan_fixup
__dpml_intel_atan_fixup:
# parameter 1: 32 + %esp
# parameter 2: 40 + %esp
# parameter 3: 44 + %esp
# parameter 4: 52 + %esp
..B2.1:
..L3:

        pushl     %esi
        pushl     %edi
        pushl     %ebp
        subl      $16, %esp
        movl      52(%esp), %eax
        movl      36(%esp), %esi
        andl      $2147483647, %esi
        movl      32(%esp), %edx
        orl       56(%esp), %eax
        je        ..B2.3
..B2.2:
        movl      %esi, %eax
        movl      %edx, %ecx
        shll      $28, %eax
        andl      $15, %ecx
        shrl      $4, %edx
        shrl      $4, %esi
        orl       %eax, %edx
        jmp       ..B2.4
..B2.3:
        movl      $4, %ecx
..B2.4:
        movl      $10, %eax
        lea       (%esi,%esi,4), %edi
        mull      %edx
        movl      %eax, %esi
        lea       (%edx,%edi,2), %eax
        addl      %ecx, %esi
        adcl      $0, %eax
        movl      $77, %ecx
        xorl      %ebp, %ebp
        addl      $-22, %esi
        movl      %esi, %edx
        adcl      $-1, %eax
        subl      %ecx, %edx
        sbbl      %ebp, %eax
        jb        ..B2.5
..B2.110:
        orl       %eax, %edx
        jne       ..B2.107
..B2.5:
        call      ..L4
..L4:
        popl      %edi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%edi), %edi
        movl      44(%esp), %ecx
        movl      48(%esp), %edx
        movl      __x_constants__@GOT(%edi), %ebp
        movzwl    .2.10_2.switchtab.4@GOTOFF(%edi,%esi,2), %eax
        lea       ..1.2_0.TAG.77.0.2@GOTOFF(%edi,%eax), %eax
        jmp       *%eax
..1.2_0.TAG.77.0.2:
..B2.7:
        orl       %edx, %ecx
        je        ..B2.9
..B2.8:
        movl      $5, %esi
        jmp       ..B2.10
..B2.9:
        movl      $11, %esi
..B2.10:
        movl      $3, %eax
        jmp       ..B2.101
..1.2_0.TAG.76.0.2:
..B2.12:
        movl      $3, %eax
        xorl      %esi, %esi
        jmp       ..B2.104
..1.2_0.TAG.75.0.2:
..1.2_0.TAG.73.0.2:
..1.2_0.TAG.71.0.2:
..1.2_0.TAG.65.0.2:
..1.2_0.TAG.63.0.2:
..1.2_0.TAG.61.0.2:
..1.2_0.TAG.57.0.2:
..1.2_0.TAG.56.0.2:
..1.2_0.TAG.51.0.2:
..1.2_0.TAG.47.0.2:
..1.2_0.TAG.46.0.2:
..1.2_0.TAG.41.0.2:
..1.2_0.TAG.37.0.2:
..1.2_0.TAG.36.0.2:
..1.2_0.TAG.31.0.2:
..1.2_0.TAG.27.0.2:
..1.2_0.TAG.26.0.2:
..1.2_0.TAG.21.0.2:
..1.2_0.TAG.17.0.2:
..1.2_0.TAG.16.0.2:
..1.2_0.TAG.15.0.2:
..1.2_0.TAG.14.0.2:
..1.2_0.TAG.13.0.2:
..1.2_0.TAG.12.0.2:
..1.2_0.TAG.7.0.2:
..1.2_0.TAG.6.0.2:
..1.2_0.TAG.5.0.2:
..1.2_0.TAG.4.0.2:
..1.2_0.TAG.3.0.2:
..1.2_0.TAG.2.0.2:
..B2.14:
        xorl      %eax, %eax
        orl       %edx, %ecx
        jmp       ..B2.101
..1.2_0.TAG.67.0.2:
..B2.20:
        orl       %edx, %ecx
        je        ..B2.22
..B2.21:
        movl      $5, %esi
        jmp       ..B2.100
..B2.22:
        movl      $11, %esi
        jmp       ..B2.100
..1.2_0.TAG.66.0.2:
..B2.25:
        movl      $2, %eax
        xorl      %esi, %esi
        jmp       ..B2.104
..1.2_0.TAG.11.0.2:
..B2.69:
        orl       %edx, %ecx
        je        ..B2.71
..B2.70:
        movl      $6, %esi
        jmp       ..B2.72
..B2.71:
        movl      $10, %esi
..B2.72:
        movl      $3, %eax
        jmp       ..B2.101
..1.2_0.TAG.10.0.2:
..B2.74:
        orl       %edx, %ecx
        je        ..B2.76
..B2.75:
        movl      $4, %esi
        jmp       ..B2.77
..B2.76:
        movl      $8, %esi
..B2.77:
        movl      $3, %eax
        jmp       ..B2.101
..1.2_0.TAG.1.0.2:
..B2.92:
        orl       %edx, %ecx
        je        ..B2.94
..B2.93:
        movl      $6, %esi
        jmp       ..B2.100
..B2.94:
        movl      $10, %esi
        jmp       ..B2.100
..1.2_0.TAG.0.0.2:
..B2.97:
        orl       %edx, %ecx
        je        ..B2.99
..B2.98:
        movl      $4, %esi
        jmp       ..B2.100
..B2.99:
        movl      $8, %esi
..B2.100:
        movl      $2, %eax
..B2.101:
        je        ..B2.103
..B2.102:
        movl      $1182793727, (%esp)
        flds      (%esp)
        flds      (%esp)
        fmulp     %st, %st(1)
        fstps     (%esp)
..B2.103:
        xorl      %edx, %edx
        orl       %eax, %edx
        je        ..B2.107
..B2.104:
        shll      $4, %esi
        movl      $3, %edi
        subl      %edi, %eax
        xorl      %edi, %edi
        orl       %edi, %eax
        movl      8(%ebp,%esi), %ecx
        movl      12(%ebp,%esi), %edx
        jne       ..B2.106
..B2.105:
        xorl      $-2147483648, %edx
..B2.106:
        movl      40(%esp), %eax
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        movl      (%ebp,%esi), %edx
        movl      4(%ebp,%esi), %ecx
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        addl      $16, %esp
        popl      %ebp
        popl      %edi
        popl      %esi
        ret       
..1.2_0.SWBASE.0.2:
..B2.107:
        addl      $16, %esp
        popl      %ebp
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_intel_atan_fixup,@function
	.size	__dpml_intel_atan_fixup,.-__dpml_intel_atan_fixup
	.section .rodata, "a"
	.space 1, 0x00 	# pad
	.align 2
.2.10_2.switchtab.4:
	.word	..1.2_0.TAG.0.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.1.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.10.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.11.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.66.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.67.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.SWBASE.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.75.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.76.0.2 - ..1.2_0.TAG.77.0.2
	.word	..1.2_0.TAG.77.0.2 - ..1.2_0.TAG.77.0.2
	.data
# -- End  __dpml_intel_atan_fixup
	.text
# -- Begin  __dpml_intel_pow_fixup
	.text
       .align    16,0x90
	.globl __dpml_intel_pow_fixup
__dpml_intel_pow_fixup:
# parameter 1: 96 + %esp
# parameter 2: 104 + %esp
# parameter 3: 108 + %esp
# parameter 4: 112 + %esp
# parameter 5: 116 + %esp
# parameter 6: 120 + %esp
# parameter 7: 124 + %esp
..B3.1:
..L5:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $76, %esp
        xorl      %ecx, %ecx
        call      ..L6
..L6:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L6](%eax), %eax
        movl      96(%esp), %esi
        movl      %esi, %ebp
        movl      100(%esp), %ebx
        andl      $15, %ebp
        shrl      $4, %esi
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        andl      $15, %esi
        subl      %ecx, %eax
        jl        ..B3.41
..B3.2:
        movl      112(%esp), %ecx
        movl      $-2147483648, %eax
        movl      12(%ecx), %edi
        subl      %eax, %edi
        orl       8(%ecx), %edi
        jne       ..B3.4
..B3.3:
        movl      $1, %ecx
        jmp       ..B3.5
..B3.4:
        xorl      %ecx, %ecx
..B3.5:
        movl      112(%esp), %eax
        movl      16(%eax), %edi
        orl       20(%eax), %edi
        jne       ..B3.7
..B3.6:
        movl      $1, %eax
        jmp       ..B3.8
..B3.7:
        xorl      %eax, %eax
..B3.8:
        andl      %eax, %ecx
        movl      $10, %eax
        mull      %esi
        movl      $94, %esi
        addl      %ebp, %eax
        adcl      $0, %edx
        negl      %esi
        addl      %eax, %esi
        orl       %edx, %esi
        je        ..B3.37
..B3.9:
        movl      %eax, %esi
        movl      $1, %edi
        addl      $-42, %esi
        movl      %ebp, 36(%esp)
        movl      %edx, %ebp
        adcl      $-1, %ebp
        subl      %edi, %esi
        movl      $0, %edi
        sbbl      %edi, %ebp
        movl      %ebp, 32(%esp)
        movl      36(%esp), %ebp
        jb        ..B3.28
..B3.58:
        orl       32(%esp), %esi
        je        ..B3.28
..B3.10:
        movl      %eax, %esi
        movl      $1, %edi
        addl      $-52, %esi
        movl      %ebp, 36(%esp)
        movl      %edx, %ebp
        adcl      $-1, %ebp
        subl      %edi, %esi
        movl      $0, %edi
        sbbl      %edi, %ebp
        movl      %ebp, 32(%esp)
        movl      36(%esp), %ebp
        jb        ..B3.28
..B3.59:
        orl       32(%esp), %esi
        je        ..B3.28
..B3.11:
        movl      %eax, %esi
        movl      %edx, %ecx
        addl      $-34, %esi
        adcl      $-1, %ecx
        movl      $1, %edi
        subl      %edi, %esi
        movl      $0, %edi
        sbbl      %edi, %ecx
        jb        ..B3.12
..B3.60:
        orl       %ecx, %esi
        jne       ..B3.19
..B3.12:
        movl      40(%esp), %eax
        movl      __x_constants__@GOT(%eax), %ecx
        movl      104(%esp), %eax
        movl      (%ecx), %esi
        movl      4(%ecx), %edi
        movl      %esi, (%eax)
        movl      %edi, 4(%eax)
        movl      $5, %eax
        subl      %eax, %ebp
        xorl      %eax, %eax
        orl       %eax, %ebp
        je        ..B3.15
..B3.13:
        movl      $2147418112, %eax
..B3.15:
        movl      $3, %ecx
        movl      124(%esp), %esi
        subl      %ecx, %esi
        movl      128(%esp), %ebp
        orl       %ebp, %esi
        je        ..B3.17
..B3.16:
        xorl      %ecx, %ecx
        jmp       ..B3.18
..B3.17:
        movl      $-2147483648, %ecx
..B3.18:
        xorl      %ecx, %eax
        orl       $-2147483648, %ebx
        movl      104(%esp), %ecx
        movl      $0, 8(%ecx)
        movl      %eax, 12(%ecx)
        jmp       ..B3.34
..B3.19:
        movl      $44, %ecx
        subl      %ecx, %eax
        orl       %edx, %eax
        jne       ..B3.34
..B3.20:
        movl      116(%esp), %eax
        movl      %eax, %ecx
        movl      40(%esp), %ebp
        movl      8(%eax), %edi
        movl      __x_constants__@GOT(%ebp), %eax
        movl      12(%ecx), %esi
        subl      24(%eax), %edi
        subl      28(%eax), %esi
        orl       %esi, %edi
        jne       ..B3.34
..B3.21:
        movl      116(%esp), %ecx
        movl      %ecx, %ebp
        movl      (%ecx), %edi
        movl      4(%ebp), %esi
        subl      16(%eax), %edi
        subl      20(%eax), %esi
        orl       %esi, %edi
        jne       ..B3.34
..B3.22:
        movl      120(%esp), %ebp
        pushl     %ebp
        pushl     $0
        pushl     $7
        pushl     124(%esp)
        call      __dpml_ux_sqrt_evaluation__
..B3.65:
        addl      $16, %esp
..B3.23:
        orl       %edx, %eax
        jne       ..B3.25
..B3.24:
        movl      $1, %eax
        jmp       ..B3.26
..B3.25:
        xorl      %eax, %eax
..B3.26:
        xorl      %ecx, %ecx
        movl      %eax, 40(%esp)
        movl      %ecx, 44(%esp)
        addl      $28, %esp
        lea       4(%esp), %eax
        pushl     %eax
        pushl     %ecx
        pushl     %ecx
        pushl     %ecx
        pushl     %ecx
        pushl     96(%esp)
        pushl     %ebp
        call      __dpml_pack__
        jmp       ..B3.33
..B3.28:
        movl      40(%esp), %esi
        movl      __x_constants__@GOT(%esi), %edx
        movl      40(%edx), %edi
        movl      %edi, 32(%esp)
        movl      44(%edx), %esi
        movl      32(%edx), %eax
        movl      36(%edx), %edi
        movl      104(%esp), %edx
        movl      %eax, (%edx)
        movl      112(%esp), %eax
        movl      %edi, 4(%edx)
        movl      4(%eax), %eax
        cmpl      $1, %eax
        je        ..B3.35
..B3.29:
        movl      $2, %ecx
        xorl      %esi, %esi
        subl      %ecx, %ebp
        orl       %esi, %ebp
        je        ..B3.31
..B3.30:
        xorl      %ecx, %ecx
        jmp       ..B3.32
..B3.31:
        movl      $1, %ecx
..B3.32:
        testl     %eax, %eax
        movl      $0, %eax
        movl      104(%esp), %esi
        setle     %al
        cmpl      %eax, %ecx
        movl      $2147418112, %ecx
        movl      $0, 8(%esi)
        jne       ..L7
        movl      $0, %ecx
..L7:
        movl      %ecx, 12(%esi)
..B3.33:
        orl       $-2147483648, %ebx
..B3.34:
        movl      96(%esp), %eax
        movl      %ebx, %edx
        addl      $76, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B3.35:
        xorl      %edi, %edi
        orl       %edi, %ecx
        je        ..B3.29
..B3.36:
        movl      104(%esp), %ecx
        movl      32(%esp), %eax
        movl      %eax, 8(%ecx)
        movl      %esi, 12(%ecx)
        jmp       ..B3.33
..B3.37:
        movl      $3, %eax
        movl      124(%esp), %ebp
        subl      %eax, %ebp
        movl      128(%esp), %ecx
        orl       %ecx, %ebp
        je        ..B3.39
..B3.38:
        xorl      %eax, %eax
        jmp       ..B3.40
..B3.39:
        movl      $-2147483648, %eax
..B3.40:
        movl      104(%esp), %edi
        orl       $-2147483648, %ebx
        movl      %eax, 12(%edi)
        movl      108(%esp), %eax
        movl      %eax, %ecx
        movl      $0, 8(%edi)
        movl      (%eax), %ebp
        movl      4(%ecx), %esi
        movl      %ebp, (%edi)
        movl      %esi, 4(%edi)
        jmp       ..B3.34
..B3.41:
        movl      $10, %eax
        movl      $95, %ecx
        mull      %esi
        addl      %eax, %ebp
        adcl      $0, %edx
        negl      %ecx
        addl      %ebp, %ecx
        orl       %edx, %ecx
        je        ..B3.53
..B3.42:
        movl      %ebp, %edi
        movl      %edx, %esi
        addl      $-40, %edi
        adcl      $-1, %esi
        movl      $1, %eax
        xorl      %ecx, %ecx
        subl      %eax, %edi
        sbbl      %ecx, %esi
        jb        ..B3.50
..B3.61:
        orl       %esi, %edi
        je        ..B3.50
..B3.43:
        movl      %ebp, %edi
        movl      %edx, %esi
        addl      $-80, %edi
        adcl      $-1, %esi
        subl      %eax, %edi
        sbbl      %ecx, %esi
        jb        ..B3.49
..B3.62:
        orl       %esi, %edi
        je        ..B3.49
..B3.44:
        movl      %ebp, %edi
        movl      %edx, %esi
        addl      $-90, %edi
        adcl      $-1, %esi
        subl      %eax, %edi
        sbbl      %ecx, %esi
        jb        ..B3.49
..B3.63:
        orl       %esi, %edi
        je        ..B3.49
..B3.45:
        addl      $-46, %ebp
        adcl      $-1, %edx
        subl      %eax, %ebp
        sbbl      %ecx, %edx
        jb        ..B3.46
..B3.64:
        orl       %edx, %ebp
        jne       ..B3.34
..B3.46:
        movl      108(%esp), %eax
        movl      %eax, %ecx
        movl      40(%esp), %ebp
        movl      8(%eax), %edi
        movl      __x_constants__@GOT(%ebp), %eax
        movl      12(%ecx), %esi
        subl      40(%eax), %edi
        subl      44(%eax), %esi
        orl       %esi, %edi
        jne       ..B3.48
..B3.47:
        movl      108(%esp), %ecx
        movl      %ecx, %ebp
        movl      (%ecx), %edi
        movl      4(%ebp), %esi
        subl      32(%eax), %edi
        subl      36(%eax), %esi
        orl       %esi, %edi
        je        ..B3.34
..B3.48:
        movl      $1182793727, 60(%esp)
        flds      60(%esp)
        flds      60(%esp)
        fmulp     %st, %st(1)
        fstps     60(%esp)
        jmp       ..B3.34
..B3.49:
        movl      116(%esp), %ecx
        movl      %ecx, %ebp
        movl      104(%esp), %eax
        movl      8(%ecx), %esi
        movl      12(%ebp), %edi
        orl       $32768, %edi
        movl      %esi, 8(%eax)
        movl      %edi, 12(%eax)
        movl      (%ecx), %ebp
        movl      4(%ecx), %ecx
        movl      %ebp, (%eax)
        movl      %ecx, 4(%eax)
        jmp       ..B3.34
..B3.50:
        movl      40(%esp), %eax
        movl      108(%esp), %ecx
        movl      108(%esp), %ebp
        movl      __x_constants__@GOT(%eax), %eax
        movl      8(%ecx), %ecx
        movl      12(%ebp), %ebp
        movl      40(%eax), %edi
        subl      %edi, %ecx
        movl      44(%eax), %esi
        subl      %esi, %ebp
        orl       %ebp, %ecx
        jne       ..B3.49
..B3.51:
        movl      32(%eax), %ebp
        movl      36(%eax), %ecx
        movl      108(%esp), %eax
        movl      108(%esp), %edx
        movl      (%eax), %eax
        movl      4(%edx), %edx
        subl      %ebp, %eax
        subl      %ecx, %edx
        orl       %edx, %eax
        jne       ..B3.49
..B3.52:
        movl      104(%esp), %eax
        movl      %esi, 12(%eax)
        movl      %ebp, %esi
        movl      %edi, 8(%eax)
        movl      %esi, (%eax)
        movl      %ecx, 4(%eax)
        jmp       ..B3.34
..B3.53:
        movl      128(%esp), %ecx
        movl      124(%esp), %eax
        orl       %eax, %ecx
        je        ..B3.34
..B3.54:
        movl      124(%esp), %ecx
        xorl      %eax, %eax
        andl      $2, %ecx
        orl       %eax, %ecx
        je        ..B3.34
..B3.55:
        movl      104(%esp), %eax
        xorl      $-2147483648, 12(%eax)
        jmp       ..B3.34
        .align    16,0x90
	.type	__dpml_intel_pow_fixup,@function
	.size	__dpml_intel_pow_fixup,.-__dpml_intel_pow_fixup
	.data
# -- End  __dpml_intel_pow_fixup
	.text
# -- Begin  __dpml_d_intel_order_fixup
	.text
       .align    16,0x90
	.globl __dpml_d_intel_order_fixup
__dpml_d_intel_order_fixup:
# parameter 1: 4 + %esp
..B4.1:
..L8:

        fldl      4(%esp)
        ret       
        .align    16,0x90
	.type	__dpml_d_intel_order_fixup,@function
	.size	__dpml_d_intel_order_fixup,.-__dpml_d_intel_order_fixup
	.data
# -- End  __dpml_d_intel_order_fixup
	.section .rodata, "a"
	.space 2, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xe6,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.align 4
__dpml_response_table:
	.long	0
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	1
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	2
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	3
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	3
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	4
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	4
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	6
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	7
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	8
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	8
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	8
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	9
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	9
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	9
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	10
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	10
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	10
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	11
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	11
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	39
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	39
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	12
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	13
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	14
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	33
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	33
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	33
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	33
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	33
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	33
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	34
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	34
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	34
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	34
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	34
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	34
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	34
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	34
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	16
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	16
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	16
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	16
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	17
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	17
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	17
	.byte	0
	.byte	12
	.byte	0
	.byte	12
	.long	38
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	38
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	38
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	47
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	47
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	47
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	47
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	47
	.byte	0
	.byte	8
	.byte	0
	.byte	8
	.long	47
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	47
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	47
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	37
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	18
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	18
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	18
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	19
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	19
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	19
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	20
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	20
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	20
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	45
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	45
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	21
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	21
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	21
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	40
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	40
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	40
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	40
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	22
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	22
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	22
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	0
	.byte	11
	.byte	0
	.byte	11
	.long	22
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	22
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	8
	.byte	0
	.byte	8
	.long	22
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	22
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	22
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	41
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	41
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	41
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	41
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	41
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	41
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	41
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	48
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	48
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	23
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	23
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	23
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	24
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	31
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	32
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	32
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	25
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	25
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	26
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	26
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	26
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	27
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	63
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	63
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	28
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	29
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	29
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	29
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	29
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	29
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	30
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	30
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	35
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	36
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	36
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	49
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	50
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	51
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	50
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	50
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	51
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	51
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	42
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	43
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	44
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	42
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	42
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	43
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	43
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	43
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	44
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	44
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	44
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	44
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	46
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	46
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	46
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	46
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	46
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	53
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	78
	.byte	0
	.byte	1
	.byte	0
	.byte	0
	.long	64
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	64
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	64
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	64
	.byte	0
	.byte	1
	.byte	0
	.byte	1
	.long	66
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	66
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	66
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	67
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	67
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	67
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	65
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	65
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	65
	.byte	0
	.byte	7
	.byte	0
	.byte	7
	.long	65
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	65
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	65
	.byte	2
	.byte	6
	.byte	2
	.byte	8
	.long	65
	.byte	2
	.byte	5
	.byte	2
	.byte	7
	.long	68
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	69
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	70
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	71
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	72
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	72
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	72
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	73
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	73
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	73
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	73
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	74
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	74
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	77
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	77
	.byte	4
	.byte	2
	.byte	4
	.byte	2
	.long	77
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.long	77
	.byte	3
	.byte	6
	.byte	3
	.byte	8
	.long	77
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	77
	.byte	1
	.byte	1
	.byte	1
	.byte	0
	.long	79
	.byte	4
	.byte	1
	.byte	4
	.byte	1
	.long	79
	.byte	3
	.byte	5
	.byte	3
	.byte	7
	.type	__dpml_response_table,@object
	.size	__dpml_response_table,1520
	.align 4
	.globl __dpml_globals_table
__dpml_globals_table:
	.long	4290772992
	.long	0
	.long	0
	.long	4294443008
	.long	0
	.long	0
	.long	0
	.long	4294934528
	.long	0
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
	.long	2147483648
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	1
	.long	0
	.long	1
	.long	0
	.long	0
	.long	0
	.long	2147483649
	.long	0
	.long	1
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	2147483648
	.long	2139095039
	.long	0
	.long	4294967295
	.long	2146435071
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	2147418111
	.long	4286578687
	.long	0
	.long	4294967295
	.long	4293918719
	.long	4294967295
	.long	4294967295
	.long	4294967295
	.long	4294901759
	.long	2139095040
	.long	0
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.long	0
	.long	2147418112
	.long	4286578688
	.long	0
	.long	0
	.long	4293918720
	.long	0
	.long	0
	.long	0
	.long	4294901760
	.long	872415232
	.long	0
	.long	0
	.long	1018167296
	.long	0
	.long	0
	.long	0
	.long	1066336256
	.long	3019898880
	.long	0
	.long	0
	.long	3165650944
	.long	0
	.long	0
	.long	0
	.long	3213819904
	.long	1065353216
	.long	0
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	3212836864
	.long	0
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	3221159936
	.type	__dpml_globals_table,@object
	.size	__dpml_globals_table,416
	.data
	.hidden __dpml_ux_sqrt_evaluation__
	.hidden __dpml_pack__
	.section .note.GNU-stack, ""
# End
