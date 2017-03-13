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
	.file "ccos.c"
	.text
..TXTST0:
# -- Begin  ccos
	.text
       .align    16,0x90
	.globl ccos
ccos:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        fldl      12(%ebp)
        fldl      20(%ebp)
        movzwl    26(%ebp), %ecx
        andl      $32752, %ecx
        movl      %gs:20, %edx
        movl      8(%ebp), %edi
        xorl      %esp, %edx
        movl      %edx, 32(%esp)
        cmpl      $32752, %ecx
        fstpl     16(%esp)
        fstpl     24(%esp)
        jl        ..B1.4
..B1.2:
        testl     $1048575, 24(%ebp)
        jne       ..B1.5
..B1.3:
        cmpl      $0, 20(%ebp)
        jne       ..B1.5
..B1.4:
        movzbl    27(%ebp), %esi
        notl      %esi
        movzbl    23(%esp), %edx
        andl      $128, %esi
        andl      $127, %edx
        orl       %esi, %edx
        movb      %dl, 23(%esp)
..B1.5:
        addl      $-32, %esp
        movl      48(%esp), %ecx
        lea       32(%esp), %esi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %esi, (%esp)
        lea       4(%esp), %edx
        movl      %ecx, (%edx)
        movl      20(%esi), %ecx
        movl      %ecx, 4(%edx)
        movl      24(%esi), %ecx
        movl      %ecx, 8(%edx)
        movl      28(%esi), %ecx
        movl      %ecx, 12(%edx)
        call      ccosh@PLT
..B1.10:
        movl      (%esi), %edx
        addl      $28, %esp
        movl      %edx, (%edi)
        movl      4(%esi), %ecx
        movl      8(%esi), %edx
        movl      12(%esi), %esi
        movl      %ecx, 4(%edi)
        movl      %edx, 8(%edi)
        movl      %esi, 12(%edi)
        movl      32(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.7
..B1.6:
        movl      %edi, %eax
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.7:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	ccos,@function
	.size	ccos,.-ccos
	.data
# -- End  ccos
	.text
# -- Begin  ccosf
	.text
       .align    16,0x90
	.globl ccosf
ccosf:
# parameter 1: 32 + %esp
..B2.1:
..L3:

        pushl     %ebx
        subl      $24, %esp
        movzwl    38(%esp), %ecx
        andl      $32640, %ecx
        movl      32(%esp), %eax
        cmpl      $32640, %ecx
        movl      36(%esp), %edx
        movl      %eax, 20(%esp)
        movl      %edx, 16(%esp)
        jl        ..B2.3
..B2.2:
        testl     $8388607, 36(%esp)
        jne       ..B2.4
..B2.3:
        movzbl    39(%esp), %eax
        notl      %eax
        movzbl    19(%esp), %edx
        andl      $128, %eax
        andl      $127, %edx
        orl       %eax, %edx
        movb      %dl, 19(%esp)
..B2.4:
        addl      $8, %esp
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        pushl     12(%esp)
        pushl     12(%esp)
        call      ccoshf@PLT
..B2.7:
        addl      $24, %esp
        popl      %ebx
        ret       
        .align    16,0x90
	.type	ccosf,@function
	.size	ccosf,.-ccosf
	.data
# -- End  ccosf
	.data
	.section .note.GNU-stack, ""
# End
