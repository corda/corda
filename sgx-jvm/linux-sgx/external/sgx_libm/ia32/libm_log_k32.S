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
	.file "libm_log_k32.c"
	.text
..TXTST0:
# -- Begin  __libm_log_k32
	.text
       .align    16,0x90
	.hidden __libm_log_k32
	.globl __libm_log_k32
__libm_log_k32:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        subl      $16, %esp
        movl      12(%ebp), %edx
        movl      %edx, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        andl      $2146435072, %eax
        negl      %eax
        addl      $2145386496, %eax
        fldl      .L_2il0floatpacket.0@GOTOFF(%ecx)
        movl      %eax, 12(%esp)
        lea       -1072676864(%edx), %eax
        movl      $0, 8(%esp)
        cmpl      $24576, %eax
        jae       ..B1.3
..B1.2:
        fldl      8(%ebp)
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(1)
        fstl      8(%ebp)
        fldl      32+_Q@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldl      24+_Q@GOTOFF(%ecx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     16+_Q@GOTOFF(%ecx)
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     8+_Q@GOTOFF(%ecx)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        faddl     _Q@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.3:
        fldl      8(%ebp)
        movl      %edx, %eax
        fmull     8(%esp)
        shrl      $12, %edx
        movzbl    %dl, %edx
        shrl      $20, %eax
        addl      $-1023, %eax
        movl      %eax, (%esp)
        fmuls     __libm_rcp_table_256@GOTOFF(%ecx,%edx,4)
        faddp     %st, %st(1)
        fstl      8(%ebp)
        fldl      8+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fildl     (%esp)
        fxch      %st(1)
        faddl     _P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fmull     _LN2@GOTOFF(%ecx)
        faddl     __libm_logf_table_256@GOTOFF(%ecx,%edx,8)
        faddp     %st, %st(1)
..B1.4:
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	__libm_log_k32,@function
	.size	__libm_log_k32,.-__libm_log_k32
	.data
# -- End  __libm_log_k32
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 4
_Q:
	.long	4294868184
	.long	3219128319
	.long	1443356411
	.long	1070945621
	.long	823147527
	.long	3218079745
	.long	748089218
	.long	1070176642
	.long	1053536005
	.long	3217353219
	.type	_Q,@object
	.size	_Q,40
	.align 4
_P:
	.long	3879088107
	.long	3219128321
	.long	1754324240
	.long	1070945626
	.type	_P,@object
	.size	_P,16
	.align 4
_LN2:
	.long	4277811695
	.long	1072049730
	.type	_LN2,@object
	.size	_LN2,8
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logf_table_256
	.section .note.GNU-stack, ""
# End
