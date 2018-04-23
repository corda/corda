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
	.file "libm_logl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_logl_k80
	.text
       .align    16,0x90
	.hidden __libm_logl_k80
	.globl __libm_logl_k80
__libm_logl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
# parameter 3: 32 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        fldt      8(%ebp)
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        movzwl    16(%ebp), %ebx
        movl      %esi, %eax
        movl      %ebx, 12(%esp)
        andl      $32767, %ebx
        shll      $15, %eax
        movl      12(%ebp), %edx
        orl       %ebx, %eax
        movl      %edx, %edi
        shll      $16, %eax
        shrl      $16, %edi
        orl       %edi, %eax
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $2147450880, %eax
        fstpt     24(%esp)
        fldt      24(%esp)
        fldt      20(%ebp)
        jae       ..B1.14
..B1.2:
        lea       -1073674240(%eax), %esi
        cmpl      $36864, %esi
        ja        ..B1.7
..B1.3:
        addl      $-1073676287, %eax
        cmpl      $32769, %eax
        ja        ..B1.6
..B1.4:
        fldl      _ones@GOTOFF(%ecx)
        fucom     %st(2)
        fnstsw    %ax
        sahf      
        jp        ..B1.5
        je        ..B1.23
..B1.5:
        fldt      36+_Q1@GOTOFF(%ecx)
        fld       %st(3)
        movl      32(%ebp), %eax
        fsub      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubrp    %st, %st(5)
        faddp     %st, %st(4)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fxch      %st(1)
        fsub      %st, %st(5)
        fxch      %st(5)
        fsubrp    %st, %st(4)
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fldt      12+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      24+_Q1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      _Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.6:
        fldl      8+_ones@GOTOFF(%ecx)
        fld       %st(2)
        movl      32(%ebp), %eax
        fadd      %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fadd      %st, %st(1)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(3)
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldl      _TWO_52H@GOTOFF(%ecx)
        fmul      %st(2), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     (%esp)
        fldt      (%esp)
        fmul      %st, %st(3)
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fldt      156+_Q2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      132+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      108+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      144+_Q2@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      120+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      96+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _Q2@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      180+_Q2@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fld       %st(2)
        fldt      168+_Q2@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fldt      (%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fmul      %st(1), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        fldl      _ones@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.7:
        fldl      _zeros@GOTOFF(%ecx)
        testl     %ebx, %ebx
        jne       ..B1.10
..B1.8:
        fldl      _TWO_75@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        fucom     %st(1)
        fnstsw    %ax
        fxch      %st(3)
        fmulp     %st, %st(2)
        sahf      
        jp        ..B1.9
        je        ..B1.27
..B1.9:
        fxch      %st(2)
        fstpt     24(%esp)
        movl      $-16458, %esi
        movzwl    32(%esp), %ebx
        movl      28(%esp), %edx
        movl      %ebx, 12(%esp)
        andl      $32767, %ebx
        jmp       ..B1.11
..B1.10:
        fstp      %st(2)
        movl      $-16383, %esi
..B1.11:
        addl      %ebx, %esi
        fucom     %st(1)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jp        ..B1.12
        je        ..B1.13
..B1.12:
        fstpt     (%esp)
        movzwl    8(%esp), %eax
        movl      %eax, %edi
        subl      %ebx, %eax
        andl      $-32768, %edi
        addl      $16383, %eax
        andl      $32767, %eax
        orl       %eax, %edi
        movw      %di, 8(%esp)
        fldt      (%esp)
..B1.13:
        fldl      _TWO_32H@GOTOFF(%ecx)
        fldl      8+_ones@GOTOFF(%ecx)
        fldt      60+_P@GOTOFF(%ecx)
        movl      12(%esp), %eax
        andl      $-32768, %eax
        orl       $-49153, %eax
        movw      %ax, 32(%esp)
        fldt      24(%esp)
        fld       %st(0)
        shrl      $23, %edx
        fadd      %st(4), %st
        movzbl    %dl, %edx
        fsubp     %st, %st(4)
        movl      %esi, 16(%esp)
        fsub      %st(3), %st
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%edx,4)
        fmul      %st, %st(4)
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpt     (%esp)
        fldt      (%esp)
        fld       %st(2)
        shll      $4, %edx
        fadd      %st(4), %st
        movl      32(%ebp), %ebx
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(3)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        fmul      %st, %st(2)
        fxch      %st(4)
        fstpt     24(%esp)
        fldt      24(%esp)
        fildl     16(%esp)
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fmul      %st(5), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fmulp     %st, %st(1)
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(5), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fmulp     %st, %st(2)
        faddl     8+__libm_logl_table_256@GOTOFF(%ecx,%edx)
        faddp     %st, %st(3)
        faddl     __libm_logl_table_256@GOTOFF(%edx,%ecx)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%ebx)
        fldt      (%ebx)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%ebx)
        fldl      _ones@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        fstp      %st(0)
        testl     %esi, %esi
        jne       ..B1.16
..B1.15:
        fldl      _ones@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fldl      _zeros@GOTOFF(%ecx)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.22
..B1.16:
        cmpl      $32767, %ebx
        je        ..B1.25
..B1.17:
        fstp      %st(0)
        testl     %ebx, %ebx
        jne       ..B1.21
..B1.18:
        testl     %edx, %edx
        jne       ..B1.21
..B1.19:
        cmpl      $0, 24(%esp)
        jne       ..B1.21
..B1.20:
        fldl      _zeros@GOTOFF(%ecx)
        fldl      8+_ones@GOTOFF(%ecx)
        fdiv      %st(1), %st
        fldl      _ones@GOTOFF(%ecx)
        jmp       ..B1.22
..B1.21:
        fldl      _zeros@GOTOFF(%ecx)
        fldl      _infs@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldl      _ones@GOTOFF(%ecx)
..B1.22:
        movl      32(%ebp), %eax
        fxch      %st(1)
        fstpt     (%eax)
        fxch      %st(1)
        fstpt     12(%eax)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        fldl      _zeros@GOTOFF(%ecx)
        fucom     %st(2)
        fnstsw    %ax
        sahf      
        jp        ..B1.30
        je        ..B1.24
..B1.30:
        fstp      %st(0)
        jmp       ..B1.5
..B1.24:
        fstp      %st(1)
        fstp      %st(2)
        fstp      %st(0)
        movl      32(%ebp), %eax
        fld       %st(0)
        fxch      %st(1)
        fstpt     (%eax)
        fldt      (%eax)
        fstpt     12(%eax)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        cmpl      $-2147483648, %edx
        jne       ..B1.15
..B1.26:
        cmpl      $0, 24(%esp)
        jne       ..B1.15
        jmp       ..B1.31
..B1.27:
        fstp      %st(2)
        fstp      %st(0)
        fldl      8+_ones@GOTOFF(%ecx)
        fdiv      %st(1), %st
        movl      32(%ebp), %eax
        fstpt     (%eax)
        fstpt     12(%eax)
        fldl      _ones@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        fstp      %st(0)
        jmp       ..B1.21
        .align    16,0x90
	.type	__libm_logl_k80,@function
	.size	__libm_logl_k80,.-__libm_logl_k80
	.data
# -- End  __libm_logl_k80
	.text
# -- Begin  __libm_log_k80
	.text
       .align    16,0x90
	.hidden __libm_log_k80
	.globl __libm_log_k80
__libm_log_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $100, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 84(%esp)
        addl      $4, %esp
        movl      12(%ebp), %ebx
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.2:
        fldt      (%ebx)
        fldt      12(%ebx)
        movl      24(%ebx), %eax
        cmpl      $-1, %eax
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        je        ..B2.41
..B2.3:
        movl      %eax, 48(%esp)
        fildl     48(%esp)
..B2.4:
        fxch      %st(2)
        fstpt     32(%esp)
        fldt      32(%esp)
        movzbl    41(%esp), %esi
        andl      $128, %esi
        shrl      $7, %esi
        movzwl    40(%esp), %edx
        movl      %esi, %eax
        movl      %edx, 28(%esp)
        andl      $32767, %edx
        shll      $15, %eax
        movl      36(%esp), %ecx
        orl       %edx, %eax
        movl      %ecx, %edi
        shll      $16, %eax
        shrl      $16, %edi
        orl       %edi, %eax
        cmpl      $2147450880, %eax
        jae       ..B2.17
..B2.5:
        lea       -1073674240(%eax), %esi
        cmpl      $36864, %esi
        ja        ..B2.10
..B2.6:
        fldl      _ones@GOTOFF(%ebx)
        addl      $-1073676287, %eax
        cmpl      $32769, %eax
        ja        ..B2.9
..B2.7:
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.8
        je        ..B2.35
..B2.8:
        fldt      12+_Q1@GOTOFF(%ebx)
        fld       %st(2)
        fsub      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubrp    %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(3)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(4)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      24+_Q1@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      _Q1@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        jmp       ..B2.26
..B2.9:
        fldl      8+_ones@GOTOFF(%ebx)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        fstpt     32(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(5)
        fstpt     16(%esp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldl      _TWO_52H@GOTOFF(%ebx)
        fmul      %st(3), %st
        fadd      %st, %st(1)
        fsubrp    %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(2)
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpt     48(%esp)
        fldt      48(%esp)
        fmul      %st, %st(4)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fldt      156+_Q2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      132+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      108+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      84+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      60+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      36+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      12+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      144+_Q2@GOTOFF(%ebx)
        fmul      %st(2), %st
        fldt      120+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      96+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      72+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      48+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      24+_Q2@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fldt      _Q2@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      180+_Q2@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fld       %st(0)
        fldt      168+_Q2@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(3), %st
        faddp     %st, %st(5)
        fldt      48(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(4), %st
        fmul      %st(1), %st
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(2)
        fld       %st(0)
        fadd      %st(3), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        fldt      16(%esp)
        fxch      %st(3)
        fxch      %st(1)
        jmp       ..B2.26
..B2.10:
        fldl      _zeros@GOTOFF(%ebx)
        testl     %edx, %edx
        jne       ..B2.13
..B2.11:
        fldl      _TWO_75@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fucom     %st(1)
        fnstsw    %ax
        fxch      %st(2)
        fmulp     %st, %st(3)
        sahf      
        jp        ..B2.12
        je        ..B2.37
..B2.12:
        fxch      %st(1)
        fstpt     32(%esp)
        movl      $-16458, %esi
        movzwl    40(%esp), %edx
        movl      36(%esp), %ecx
        movl      %edx, 28(%esp)
        andl      $32767, %edx
        jmp       ..B2.14
..B2.13:
        fstp      %st(1)
        movl      $-16383, %esi
..B2.14:
        addl      %edx, %esi
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.15
        je        ..B2.16
..B2.15:
        fstpt     16(%esp)
        movzwl    24(%esp), %eax
        movl      %eax, %edi
        subl      %edx, %eax
        andl      $-32768, %edi
        addl      $16383, %eax
        andl      $32767, %eax
        orl       %eax, %edi
        movw      %di, 24(%esp)
        fldt      16(%esp)
..B2.16:
        fldl      _TWO_32H@GOTOFF(%ebx)
        fldt      36+_P@GOTOFF(%ebx)
        movl      28(%esp), %eax
        andl      $-32768, %eax
        orl       $-49153, %eax
        movw      %ax, 40(%esp)
        fldt      32(%esp)
        fld       %st(0)
        shrl      $23, %ecx
        fadd      %st(3), %st
        movzbl    %cl, %ecx
        fsubp     %st, %st(3)
        movl      %esi, 48(%esp)
        fsub      %st(2), %st
        fildl     48(%esp)
        flds      __libm_rcp_table_256@GOTOFF(%ebx,%ecx,4)
        fmul      %st, %st(4)
        fmul      %st, %st(2)
        fmulp     %st, %st(5)
        shll      $4, %ecx
        fxch      %st(1)
        faddp     %st, %st(4)
        fldl      8+_ones@GOTOFF(%ebx)
        fld       %st(0)
        fadd      %st(4), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(5)
        faddp     %st, %st(1)
        fadd      %st, %st(4)
        fld       %st(4)
        fmul      %st(5), %st
        fldt      60+_P@GOTOFF(%ebx)
        fmul      %st(1), %st
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      12+_P@GOTOFF(%ebx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fxch      %st(5)
        fmulp     %st, %st(3)
        fldt      48+_P@GOTOFF(%ebx)
        fmul      %st(5), %st
        fldt      24+_P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      _P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      .L_2il0floatpacket.3@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        faddl     8+__libm_logl_table_256@GOTOFF(%ebx,%ecx)
        faddp     %st, %st(3)
        fldl      _ones@GOTOFF(%ebx)
        fxch      %st(1)
        faddl     __libm_logl_table_256@GOTOFF(%ecx,%ebx)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        jmp       ..B2.26
..B2.17:
        fstp      %st(1)
        testl     %esi, %esi
        jne       ..B2.19
..B2.18:
        fldl      _ones@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fldl      _zeros@GOTOFF(%ebx)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B2.26
..B2.19:
        cmpl      $32767, %edx
        je        ..B2.39
..B2.20:
        fstp      %st(0)
        testl     %edx, %edx
        jne       ..B2.24
..B2.21:
        testl     %ecx, %ecx
        jne       ..B2.24
..B2.22:
        cmpl      $0, 32(%esp)
        jne       ..B2.24
..B2.23:
        fldl      _zeros@GOTOFF(%ebx)
        fldl      8+_ones@GOTOFF(%ebx)
        fdiv      %st(1), %st
        fldl      _ones@GOTOFF(%ebx)
        jmp       ..B2.26
..B2.24:
        fldl      _zeros@GOTOFF(%ebx)
        fldl      _infs@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      _ones@GOTOFF(%ebx)
..B2.26:
        fldt      12+_LN2_K80@GOTOFF(%ebx)
        lea       72(%esp), %edx
        fmul      %st(4), %st
        movl      8(%ebp), %eax
        fstpt     -44(%edx)
        fldt      -44(%edx)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     -12(%edx)
        fldt      -12(%edx)
        fxch      %st(3)
        fmulp     %st, %st(2)
        movzwl    -4(%edx), %esi
        andl      $32767, %esi
        fxch      %st(1)
        fstpt     (%edx)
        fldt      (%edx)
        fldt      _LN2_K80@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpt     -56(%edx)
        fldt      -56(%edx)
        fld       %st(2)
        movzwl    -48(%edx), %ecx
        fadd      %st(1), %st
        andl      $32767, %ecx
        movl      $0, 24(%eax)
        cmpl      %esi, %ecx
        jg        ..B2.31
..B2.27:
        jne       ..B2.32
..B2.28:
        movl      20(%esp), %edx
        movl      64(%esp), %ecx
        cmpl      %ecx, %edx
        ja        ..B2.31
..B2.29:
        jne       ..B2.32
..B2.30:
        movl      16(%esp), %edx
        cmpl      60(%esp), %edx
        jbe       ..B2.32
..B2.31:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B2.33
..B2.32:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
..B2.33:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        movl      84(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.38
..B2.34:
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.35:
        fldl      _zeros@GOTOFF(%ebx)
        fucom     %st(3)
        fnstsw    %ax
        sahf      
        jp        ..B2.44
        je        ..B2.36
..B2.44:
        fstp      %st(0)
        jmp       ..B2.8
..B2.36:
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B2.26
..B2.37:
        fstp      %st(1)
        fstp      %st(1)
        fldl      8+_ones@GOTOFF(%ebx)
        fdiv      %st(1), %st
        fldl      _ones@GOTOFF(%ebx)
        jmp       ..B2.26
..B2.38:
        call      __stack_chk_fail@PLT
..B2.39:
        cmpl      $-2147483648, %ecx
        jne       ..B2.18
..B2.40:
        cmpl      $0, 32(%esp)
        jne       ..B2.18
        jmp       ..B2.45
..B2.41:
        fldl      _zeros@GOTOFF(%ebx)
        fldt      halfs@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fmulp     %st, %st(2)
        jmp       ..B2.4
..B2.45:
        fstp      %st(0)
        jmp       ..B2.24
        .align    16,0x90
	.type	__libm_log_k80,@function
	.size	__libm_log_k80,.-__libm_log_k80
	.data
# -- End  __libm_log_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xdc,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
halfs:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0xbf,0x00,0x00
	.type	halfs,@object
	.size	halfs,24
	.align 2
_Q1:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	5458
	.word	43692
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	44682
	.word	49156
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	63914
	.word	24446
	.word	52434
	.word	52426
	.word	16380
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q2:
	.word	58950
	.word	11767
	.word	59949
	.word	40744
	.word	49076
	.word	0
	.word	44486
	.word	43690
	.word	43690
	.word	43690
	.word	16369
	.word	0
	.word	65514
	.word	65535
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	49718
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	36693
	.word	43691
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	41156
	.word	18852
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	14101
	.word	65290
	.word	65535
	.word	65535
	.word	49147
	.word	0
	.word	21744
	.word	48151
	.word	14557
	.word	58254
	.word	16379
	.word	0
	.word	57454
	.word	983
	.word	52353
	.word	52428
	.word	49147
	.word	0
	.word	54021
	.word	59409
	.word	39452
	.word	47662
	.word	16379
	.word	0
	.word	35608
	.word	4659
	.word	11214
	.word	43692
	.word	49147
	.word	0
	.word	35524
	.word	31332
	.word	8749
	.word	40326
	.word	16379
	.word	0
	.word	54832
	.word	20873
	.word	17674
	.word	36915
	.word	49147
	.word	0
	.word	48567
	.word	9481
	.word	46911
	.word	55870
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	43680
	.word	16381
	.word	0
	.type	_Q2,@object
	.size	_Q2,192
	.align 2
_P:
	.word	65
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	44010
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	58670
	.word	61414
	.word	65535
	.word	65535
	.word	49148
	.word	0
	.word	24905
	.word	46201
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	57053
	.word	27587
	.word	64478
	.word	43690
	.word	49148
	.word	0
	.word	41508
	.word	48914
	.word	32459
	.word	37449
	.word	16380
	.word	0
	.type	_P,@object
	.size	_P,72
	.align 2
_LN2_K80:
	.word	0
	.word	0
	.word	6136
	.word	45426
	.word	16382
	.word	0
	.word	12704
	.word	55409
	.word	6480
	.word	47298
	.word	49116
	.word	0
	.type	_LN2_K80,@object
	.size	_LN2_K80,24
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.hidden __libm_normalizel_k80
	.section .note.GNU-stack, ""
# End
