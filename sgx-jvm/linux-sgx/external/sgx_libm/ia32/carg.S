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
	.file "carg.c"
	.text
..TXTST0:
# -- Begin  carg
	.text
       .align    16,0x90
	.globl carg
carg:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %ebx
        subl      $28, %esp
        movzwl    14(%ebp), %eax
        andl      $32752, %eax
        cmpl      $32752, %eax
        je        ..B1.4
..B1.2:
        fldl      8(%ebp)
..B1.3:
        fldl      16(%ebp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fxch      %st(1)
        fstpl     8(%esp)
        fstpl     (%esp)
        call      atan2@PLT
..B1.10:
        addl      $28, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.4:
        testl     $1048575, 12(%ebp)
        jne       ..B1.6
..B1.5:
        cmpl      $0, 8(%ebp)
        je        ..B1.2
..B1.6:
        fldl      8(%ebp)
        fmul      %st(0), %st
        fstl      8(%ebp)
        jmp       ..B1.3
        .align    16,0x90
	.type	carg,@function
	.size	carg,.-carg
	.data
# -- End  carg
	.data
	.section .note.GNU-stack, ""
# End
