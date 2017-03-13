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
	.file "y1l.c"
	.text
..TXTST0:
# -- Begin  y1l
	.text
       .align    16,0x90
	.globl y1l
y1l:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %edi
        pushl     %ebx
        subl      $56, %esp
        fldt      8(%ebp)
        lea       16(%esp), %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %edi, (%esp)
        fstpt     4(%esp)
        call      __ltoq@PLT
..B1.6:
        addl      $-32, %esp
        movl      (%edi), %ecx
        lea       64(%esp), %edx
        movl      %edx, (%esp)
        lea       16(%esp), %eax
        movl      4(%edi), %edx
        movl      %ecx, (%eax)
        movl      %edx, 4(%eax)
        movl      8(%edi), %ecx
        movl      12(%edi), %edx
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        call      __y1q@PLT
..B1.5:
        addl      $28, %esp
..B1.2:
        movl      32(%esp), %eax
        movl      %eax, (%edi)
        movl      36(%esp), %edx
        movl      40(%esp), %ecx
        movl      44(%esp), %eax
        addl      $4, %esp
        pushl     %edi
        movl      %edx, 4(%edi)
        movl      %ecx, 8(%edi)
        movl      %eax, 12(%edi)
        call      __qtol@PLT
..B1.7:
        addl      $56, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	y1l,@function
	.size	y1l,.-y1l
	.data
# -- End  y1l
	.data
	.section .note.GNU-stack, ""
# End
