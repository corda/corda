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
	.file "fegetexceptflag.c"
	.text
..TXTST0:
# -- Begin  fegetexceptflag
	.text
       .align    16,0x90
	.globl fegetexceptflag
fegetexceptflag:
# parameter 1: 16 + %esp
# parameter 2: 20 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movl      20(%esp), %eax
        andl      $63, %eax
..B1.2:
        fnstsw    4(%esp)
..B1.3:
        stmxcsr   (%esp)
..B1.4:
        movl      16(%esp), %ecx
        movzwl    4(%esp), %edx
        orl       (%esp), %edx
        andl      %eax, %edx
        xorl      %eax, %eax
        movw      %dx, (%ecx)
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	fegetexceptflag,@function
	.size	fegetexceptflag,.-fegetexceptflag
	.data
# -- End  fegetexceptflag
	.data
	.section .note.GNU-stack, ""
# End
