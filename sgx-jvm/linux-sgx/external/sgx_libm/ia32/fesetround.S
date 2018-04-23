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
	.file "fesetround.c"
	.text
..TXTST0:
# -- Begin  fesetround
	.text
       .align    16,0x90
	.globl fesetround
fesetround:
# parameter 1: 48 + %esp
..B1.1:
..L1:

        subl      $44, %esp
        movl      %gs:20, %eax
        movl      48(%esp), %edx
        xorl      %esp, %eax
        movl      %eax, 32(%esp)
        testl     $-3073, %edx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        jne       ..B1.9
..B1.2:
        fnstenv   4(%esp)
..B1.3:
        movzwl    4(%esp), %eax
        andl      $-3073, %eax
        orl       %edx, %eax
        movw      %ax, 4(%esp)
..B1.4:
        fnclex    
..B1.5:
        fldenv    4(%esp)
..B1.6:
        stmxcsr   (%esp)
        movl      (%esp), %eax
..B1.7:
        andl      $-24577, %eax
        shll      $3, %edx
        orl       %edx, %eax
        movl      %eax, (%esp)
        movl      32(%esp), %eax
        xorl      %esp, %eax
        ldmxcsr   (%esp)
        cmpl      %gs:20, %eax
        jne       ..B1.11
..B1.8:
        xorl      %eax, %eax
        addl      $44, %esp
        ret       
..B1.9:
        movl      32(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.11
..B1.10:
        movl      $1, %eax
        addl      $44, %esp
        ret       
..B1.11:
        movl      %ecx, %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	fesetround,@function
	.size	fesetround,.-fesetround
	.data
# -- End  fesetround
	.data
	.section .note.GNU-stack, ""
# End
