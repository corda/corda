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
	.file "csin.c"
	.text
..TXTST0:
# -- Begin  csin
	.text
       .align    16,0x90
	.globl csin
csin:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %ebx
        subl      $72, %esp
        fldz      
        fldl      20(%ebp)
        fldl      12(%ebp)
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 64(%esp)
        cmpl      $32752, %ecx
        fstpl     56(%esp)
        fxch      %st(1)
        fstl      16(%esp)
        fstl      24(%esp)
        fstl      32(%esp)
        fstpl     40(%esp)
        fstpl     48(%esp)
        jl        ..B1.4
..B1.2:
        testl     $1048575, 24(%ebp)
        jne       ..B1.5
..B1.3:
        cmpl      $0, 20(%ebp)
        jne       ..B1.5
..B1.4:
        movzbl    27(%ebp), %edx
        notl      %edx
        movzbl    55(%esp), %ecx
        andl      $128, %edx
        andl      $127, %ecx
        orl       %edx, %ecx
        movb      %cl, 55(%esp)
..B1.5:
        addl      $-32, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        lea       32(%esp), %ecx
        movl      %ecx, (%esp)
        lea       4(%esp), %edx
        movl      48(%ecx), %ecx
        movl      %ecx, (%edx)
        movl      84(%esp), %ecx
        movl      %ecx, 4(%edx)
        movl      88(%esp), %ecx
        movl      %ecx, 8(%edx)
        movl      92(%esp), %ecx
        movl      %ecx, 12(%edx)
        call      csinh@PLT
..B1.14:
        addl      $28, %esp
        fldl      (%esp)
        movzwl    6(%esp), %edx
        andl      $32752, %edx
        fstl      16(%esp)
        cmpl      $32752, %edx
        fstpl     40(%esp)
        fldl      8(%esp)
        fstl      24(%esp)
        fstpl     32(%esp)
        jl        ..B1.8
..B1.6:
        testl     $1048575, 20(%esp)
        jne       ..B1.9
..B1.7:
        cmpl      $0, 16(%esp)
        jne       ..B1.9
..B1.8:
        movzbl    23(%esp), %edx
        notl      %edx
        movzbl    47(%esp), %ecx
        andl      $128, %edx
        andl      $127, %ecx
        orl       %edx, %ecx
        movb      %cl, 47(%esp)
..B1.9:
        movl      32(%esp), %edx
        movl      %edx, (%esi)
        movl      40(%esp), %edx
        movl      %edx, 8(%esi)
        movl      36(%esp), %ecx
        movl      44(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edx, 12(%esi)
        movl      64(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.11
..B1.10:
        movl      %esi, %eax
        addl      $72, %esp
        popl      %ebx
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.11:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	csin,@function
	.size	csin,.-csin
	.data
# -- End  csin
	.text
# -- Begin  csinf
	.text
       .align    16,0x90
	.globl csinf
csinf:
# parameter 1: 48 + %esp
..B2.1:
..L3:

        pushl     %ebx
        subl      $40, %esp
        xorl      %eax, %eax
        movl      %eax, 16(%esp)
        movl      %eax, 20(%esp)
        movl      %eax, 24(%esp)
        movl      %eax, 28(%esp)
        movzwl    54(%esp), %eax
        andl      $32640, %eax
        movl      52(%esp), %edx
        cmpl      $32640, %eax
        movl      48(%esp), %ecx
        movl      %edx, 32(%esp)
        movl      %ecx, 36(%esp)
        jl        ..B2.3
..B2.2:
        testl     $8388607, 52(%esp)
        jne       ..B2.4
..B2.3:
        movzbl    55(%esp), %eax
        notl      %eax
        movzbl    35(%esp), %edx
        andl      $128, %eax
        andl      $127, %edx
        orl       %eax, %edx
        movb      %dl, 35(%esp)
..B2.4:
        addl      $8, %esp
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        pushl     28(%esp)
        pushl     28(%esp)
        call      csinhf@PLT
..B2.10:
        movl      %eax, 16(%esp)
        movl      %eax, 28(%esp)
        shrl      $16, %eax
        andl      $32640, %eax
        cmpl      $32640, %eax
        movl      %edx, 20(%esp)
        movl      %edx, 24(%esp)
        jl        ..B2.6
..B2.5:
        testl     $8388607, 16(%esp)
        jne       ..B2.7
..B2.6:
        movzbl    19(%esp), %eax
        notl      %eax
        movzbl    31(%esp), %edx
        andl      $128, %eax
        andl      $127, %edx
        orl       %eax, %edx
        movb      %dl, 31(%esp)
..B2.7:
        movl      24(%esp), %eax
        movl      28(%esp), %edx
        addl      $40, %esp
        popl      %ebx
        ret       
        .align    16,0x90
	.type	csinf,@function
	.size	csinf,.-csinf
	.data
# -- End  csinf
	.data
	.section .note.GNU-stack, ""
# End
