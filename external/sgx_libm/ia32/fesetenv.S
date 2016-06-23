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
	.file "fesetenv.c"
	.text
..TXTST0:
# -- Begin  fesetenv
	.text
       .align    16,0x90
	.globl fesetenv
fesetenv:
# parameter 1: 64 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $48, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 32(%esp)
        stmxcsr   (%esp)
        movl      (%esp), %ecx
..B1.2:
        fnstenv   4(%esp)
..B1.3:
        movzwl    4(%esp), %edi
        andl      $-32704, %ecx
        movzwl    8(%esp), %eax
        andl      $-3136, %edi
        movl      64(%esp), %ebp
        andl      $-64, %eax
        cmpl      $-1, %ebp
        je        ..B1.10
..B1.4:
        movzwl    (%ebp), %edx
        movl      %edx, %ebx
        movzwl    4(%ebp), %ebp
        andl      $3135, %ebx
        andl      $63, %ebp
        orl       %ebx, %edi
        orl       %ebp, %eax
        movw      %ax, 8(%esp)
        movl      %edx, %eax
        andl      $63, %eax
        andl      $3072, %edx
        shll      $7, %eax
        shll      $3, %edx
        orl       %eax, %ecx
        orl       %edx, %ecx
        movw      %di, 4(%esp)
        orl       %ebp, %ecx
..B1.5:
        fnclex    
..B1.6:
        fldenv    4(%esp)
..B1.7:
        movl      %ecx, (%esp)
        movl      32(%esp), %eax
        xorl      %esp, %eax
        ldmxcsr   (%esp)
        cmpl      %gs:20, %eax
        jne       ..B1.9
..B1.8:
        xorl      %eax, %eax
        addl      $48, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.9:
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        call      __stack_chk_fail@PLT
..B1.10:
        orl       $63, %edi
        orl       $8064, %ecx
        movw      %ax, 8(%esp)
        movw      %di, 4(%esp)
        jmp       ..B1.5
        .align    16,0x90
	.type	fesetenv,@function
	.size	fesetenv,.-fesetenv
	.data
# -- End  fesetenv
	.data
	.section .note.GNU-stack, ""
# End
