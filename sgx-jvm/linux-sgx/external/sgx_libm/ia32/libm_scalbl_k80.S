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
	.file "libm_scalbl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_scalbl_k80
	.text
       .align    16,0x90
	.hidden __libm_scalbl_k80
	.globl __libm_scalbl_k80
__libm_scalbl_k80:
# parameter 1: 48 + %esp
# parameter 2: 60 + %esp
# parameter 3: 72 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %ebx
        subl      $36, %esp
        fldt      60(%esp)
        fldt      48(%esp)
        fld       %st(0)
        movl      72(%esp), %ebx
        fadd      %st(2), %st
        cmpl      $0, 52(%esp)
        jne       ..B1.4
..B1.2:
        cmpl      $0, 48(%esp)
        jne       ..B1.4
..B1.3:
        fstp      %st(2)
        fstp      %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
..B1.4:
        movzwl    56(%esp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.3
..B1.5:
        fstpt     24(%esp)
        fldt      24(%esp)
        movzwl    32(%esp), %esi
        movl      %esi, %edx
        andl      $32767, %edx
        lea       -1(%ebx,%edx), %ecx
        cmpl      $32765, %ecx
        lea       (%ebx,%edx), %eax
        ja        ..B1.7
..B1.6:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        andl      $-32768, %esi
        andl      $32767, %eax
        orl       %eax, %esi
        movw      %si, 32(%esp)
        fldt      24(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
..B1.7:
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $32766, %eax
        jle       ..B1.9
..B1.8:
        fstp      %st(2)
        fstp      %st(1)
        fldt      _CONSTANTS@GOTOFF(%ecx)
        fmul      %st(0), %st
        fmulp     %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
..B1.9:
        cmpl      $-63, %eax
        fldt      12+_CONSTANTS@GOTOFF(%ecx)
        jge       ..B1.11
..B1.10:
        fstp      %st(3)
        fstp      %st(0)
        fxch      %st(1)
        fmul      %st(0), %st
        fmulp     %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
..B1.11:
        cmpl      $-19, %eax
        jge       ..B1.13
..B1.12:
        fstp      %st(2)
        fstp      %st(2)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        movzwl    8+.L_2il0floatpacket.0@GOTOFF(%ecx), %eax
        andl      $-32768, %eax
        fstpt     (%esp)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fstpt     24(%esp)
        movzwl    32(%esp), %edx
        lea       256(%ebx,%edx), %ebx
        andl      $-32768, %edx
        andl      $32767, %ebx
        orl       $-49409, %edx
        orl       %ebx, %eax
        movw      %ax, 8(%esp)
        fldt      (%esp)
        movw      %dx, 32(%esp)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
..B1.13:
        fstp      %st(1)
        fldt      24+_CONSTANTS@GOTOFF(%ecx)
        fxch      %st(1)
        fmul      %st(0), %st
        movzwl    8+.L_2il0floatpacket.0@GOTOFF(%ecx), %eax
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        andl      $-32768, %eax
        fmul      %st(2), %st
        fld       %st(0)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubrp    %st, %st(2)
        movzwl    20(%esp), %edx
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        lea       -16383(%edx), %esi
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        negl      %esi
        addl      $16383, %esi
        lea       -32512(%ebx,%edx), %ecx
        andl      $32767, %esi
        andl      $32767, %ecx
        fstpt     (%esp)
        orl       %esi, %eax
        movw      %ax, 8(%esp)
        movl      %edx, %esi
        fldt      (%esp)
        andl      $-32768, %esi
        fmulp     %st, %st(1)
        andl      $-32768, %eax
        orl       $-49409, %esi
        orl       %ecx, %eax
        movw      %si, 20(%esp)
        fldt      12(%esp)
        movw      %ax, 8(%esp)
        fldt      (%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
..B1.14:
        addl      $36, %esp
        popl      %ebx
        popl      %esi
        ret       
        .align    16,0x90
	.type	__libm_scalbl_k80,@function
	.size	__libm_scalbl_k80,.-__libm_scalbl_k80
	.data
# -- End  __libm_scalbl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 2
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16127
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,36
	.data
	.section .note.GNU-stack, ""
# End
