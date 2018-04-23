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
	.file "conj.c"
	.text
..TXTST0:
# -- Begin  conj
	.text
       .align    16,0x90
	.globl conj
conj:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $32, %esp
        fldl      12(%ebp)
        fldl      20(%ebp)
        movl      8(%ebp), %eax
        fchs      
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 16(%esp)
        movl      16(%ebp), %edx
        movl      12(%ebp), %ecx
        fstpl     8(%esp)
        movl      %ecx, (%eax)
        movl      %edx, 4(%eax)
        movl      8(%esp), %ecx
        movl      12(%esp), %edx
        fstpl     (%esp)
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        movl      16(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.3
..B1.2:
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.3:
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	conj,@function
	.size	conj,.-conj
	.data
# -- End  conj
	.data
	.section .note.GNU-stack, ""
# End
