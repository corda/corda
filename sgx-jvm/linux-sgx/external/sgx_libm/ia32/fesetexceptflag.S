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
	.file "fesetexceptflag.c"
	.text
..TXTST0:
# -- Begin  fesetexceptflag
	.text
       .align    16,0x90
	.globl fesetexceptflag
fesetexceptflag:
# parameter 1: 48 + %esp
# parameter 2: 52 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        movl      %gs:20, %eax
        movl      52(%esp), %ecx
        xorl      %esp, %eax
        movl      %eax, 32(%esp)
        andl      $63, %ecx
        je        ..B1.8
..B1.2:
        fnstenv   4(%esp)
..B1.3:
        movl      48(%esp), %edx
        movl      %ecx, %eax
        notl      %eax
        movzwl    8(%esp), %edi
        movzwl    (%edx), %ebx
        andl      %eax, %edi
        andl      %ecx, %ebx
        orl       %ebx, %edi
        movw      %di, 8(%esp)
..B1.4:
        fnclex    
..B1.5:
        fldenv    4(%esp)
..B1.6:
        stmxcsr   (%esp)
        movl      (%esp), %ebx
..B1.7:
        andl      %eax, %ebx
        movzwl    (%edx), %eax
        andl      %ecx, %eax
        orl       %eax, %ebx
        movl      %ebx, (%esp)
        ldmxcsr   (%esp)
..B1.8:
        movl      32(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.10
..B1.9:
        xorl      %eax, %eax
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        ret       
..B1.10:
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	fesetexceptflag,@function
	.size	fesetexceptflag,.-fesetexceptflag
	.data
# -- End  fesetexceptflag
	.data
	.section .note.GNU-stack, ""
# End
