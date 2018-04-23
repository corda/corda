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
	.file "libm_atan2l_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_atan2l_k80
	.text
       .align    16,0x90
	.hidden __libm_atan2l_k80
	.globl __libm_atan2l_k80
__libm_atan2l_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        movl      16(%ebp), %edx
        movl      12(%ebp), %ecx
        movl      %gs:20, %eax
        movzbl    9(%edx), %esi
        xorl      %esp, %eax
        andl      $128, %esi
        movl      %eax, 172(%esp)
        shrl      $7, %esi
        movl      %esi, 44(%esp)
        movzbl    9(%ecx), %esi
        movzwl    8(%ecx), %eax
        andl      $128, %esi
        andl      $32767, %eax
        movzwl    8(%edx), %edi
        shrl      $7, %esi
        andl      $32767, %edi
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $32767, %eax
        je        ..B1.110
..B1.2:
        cmpl      $32767, %edi
        je        ..B1.97
..B1.3:
        movl      4(%ecx), %eax
        testl     %eax, %eax
        jne       ..B1.5
..B1.4:
        cmpl      $0, (%ecx)
        je        ..B1.8
..B1.5:
        cmpl      $0, 4(%edx)
        jne       ..B1.20
..B1.6:
        cmpl      $0, (%edx)
        jne       ..B1.20
..B1.7:
        testl     %eax, %eax
        jne       ..B1.17
..B1.116:
        cmpl      $0, (%ecx)
..B1.8:
        jne       ..B1.17
..B1.9:
        cmpl      $0, 4(%edx)
        jne       ..B1.14
..B1.10:
        cmpl      $0, (%edx)
        jne       ..B1.14
..B1.11:
        movl      8(%ebp), %eax
        cmpl      $0, 44(%esp)
        movl      $0, 24(%eax)
        je        ..B1.13
..B1.12:
        movl      8(%ebp), %ecx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %edx
        fldt      _pi_00l@GOTOFF(%edx,%ebx)
        fstpt     (%ecx)
        fldt      _pilo_00l@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%ecx)
        jmp       ..B1.18
..B1.13:
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fldz      
        fmull     ones@GOTOFF(%ebx,%esi,8)
        movl      8(%ebp), %eax
        fstpt     (%eax)
        fstpt     12(%eax)
        jmp       ..B1.18
..B1.14:
        movl      8(%ebp), %eax
        cmpl      $0, 44(%esp)
        movl      $0, 24(%eax)
        je        ..B1.16
..B1.15:
        movl      8(%ebp), %ecx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %edx
        fldt      _pi_00l@GOTOFF(%edx,%ebx)
        fstpt     (%ecx)
        fldt      _pilo_00l@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%ecx)
        jmp       ..B1.18
..B1.16:
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fldz      
        fmull     ones@GOTOFF(%ebx,%esi,8)
        movl      8(%ebp), %eax
        fstpt     (%eax)
        fstpt     12(%eax)
        jmp       ..B1.18
..B1.17:
        movl      8(%ebp), %edx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %esi
        fldt      _pi_02l@GOTOFF(%esi,%ebx)
        movl      $0, 24(%edx)
        fstpt     (%edx)
        fldt      _pilo_02l@GOTOFF(%esi,%ebx)
        fldt      _small_value_80@GOTOFF(%esi,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%edx)
..B1.18:
        movl      172(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.89
..B1.19:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.20:
        movl      (%edx), %edi
        lea       116(%esp), %eax
        movl      %edi, (%eax)
        movl      4(%edx), %edi
        movl      %edi, 4(%eax)
        movl      8(%edx), %edi
        movl      %edi, 8(%eax)
        movl      12(%edx), %edi
        movl      %edi, 12(%eax)
        movl      16(%edx), %edi
        movl      %edi, 16(%eax)
        movl      20(%edx), %edi
        movl      24(%edx), %edx
        movl      %edi, 20(%eax)
        movl      %edx, 24(%eax)
        movl      (%ecx), %edx
        movl      4(%ecx), %edi
        movl      %edx, 28(%eax)
        movl      %edi, 32(%eax)
        movl      8(%ecx), %edx
        movl      12(%ecx), %edi
        movl      %edx, 36(%eax)
        movl      %edi, 40(%eax)
        movl      16(%ecx), %edx
        movl      20(%ecx), %edi
        movl      24(%ecx), %ecx
        movl      %edx, 44(%eax)
        movl      %edi, 48(%eax)
        movl      %ecx, 52(%eax)
        addl      $4, %esp
        pushl     %eax
        call      __libm_normalizel_k80
..B1.21:
        addl      $4, %esp
        lea       140(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.22:
        fldt      116(%esp)
        fld       %st(0)
        fldt      128(%esp)
        movl      44(%esp), %eax
        fadd      %st, %st(1)
        movl      140(%esp), %ecx
        fxch      %st(1)
        fsub      %st, %st(2)
        movl      168(%esp), %edx
        cmpl      %edx, %ecx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      144(%esp)
        fld       %st(0)
        fldt      156(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldl      ones@GOTOFF(%ebx,%eax,8)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fldl      ones@GOTOFF(%ebx,%esi,8)
        fmul      %st, %st(2)
        fmul      %st, %st(1)
        jg        ..B1.25
..B1.23:
        jne       ..B1.75
..B1.24:
        fxch      %st(4)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        fxch      %st(4)
        jbe       ..B1.75
..B1.25:
        lea       12(%edx), %eax
        cmpl      %eax, %ecx
        jle       ..B1.70
..B1.26:
        lea       75(%edx), %eax
        cmpl      %eax, %ecx
        jle       ..B1.43
..B1.27:
        cmpl      $0, 44(%esp)
        je        ..B1.29
..B1.28:
        fstp      %st(0)
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      8(%ebp), %edx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %esi
        fldt      _pi_00l@GOTOFF(%esi,%ebx)
        movl      $0, 24(%edx)
        fstpt     (%edx)
        fldt      _pilo_00l@GOTOFF(%esi,%ebx)
        fldt      _small_value_80@GOTOFF(%esi,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%edx)
        jmp       ..B1.41
..B1.29:
        fxch      %st(2)
        fstpt     144(%esp)
        fldt      144(%esp)
        movzwl    152(%esp), %eax
        andl      $32767, %eax
        fxch      %st(4)
        fstpt     116(%esp)
        fldt      116(%esp)
        cmpl      $8383, %eax
        jge       ..B1.31
..B1.30:
        fstp      %st(4)
        fstp      %st(3)
        fxch      %st(2)
        fstpt     156(%esp)
        fstpt     128(%esp)
        jmp       ..B1.37
..B1.31:
        cmpl      $24383, %eax
        jg        ..B1.30
..B1.33:
        movzwl    124(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.30
..B1.35:
        cmpl      $24383, %eax
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(4)
        fxch      %st(1)
        jle       ..B1.40
..B1.36:
        fstp      %st(1)
        fstp      %st(2)
        fxch      %st(1)
        fstpt     156(%esp)
        fstpt     128(%esp)
..B1.37:
        addl      $4, %esp
        lea       140(%esp), %eax
        pushl     %eax
        fstpt     -40(%eax)
        call      __libm_normalizel_k80
..B1.38:
        fldt      104(%esp)
        addl      $4, %esp
        lea       112(%esp), %eax
        pushl     %eax
        fstpt     -12(%eax)
        call      __libm_normalizel_k80
..B1.39:
        fldt      104(%esp)
        fldt      116(%esp)
        fldt      128(%esp)
        fldt      144(%esp)
        fldt      156(%esp)
        movl      140(%esp), %ecx
        movl      168(%esp), %edx
..B1.40:
        fld       %st(1)
        subl      %ecx, %edx
        fdiv      %st(4), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fxch      %st(5)
        fstpt     104(%esp)
        fxch      %st(3)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.16@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(6), %st
        fld       %st(0)
        movl      8(%ebp), %eax
        fsubr     %st(7), %st
        movl      %edx, 24(%eax)
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fldt      32(%esp)
        fdivrp    %st, %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      104(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%eax)
        fmulp     %st, %st(1)
        fstpt     12(%eax)
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        fldt      _small_value_80@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fstpt     16(%esp)
..B1.41:
        movl      172(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.89
..B1.42:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        subl      %ecx, %edx
        movzwl    8+.L_2il0floatpacket.12@GOTOFF(%ebx), %eax
        addl      $16383, %edx
        movl      %eax, 28(%esp)
        andl      $-32768, %eax
        andl      $32767, %edx
        fstpt     88(%esp)
        fldt      88(%esp)
        orl       %edx, %eax
        movw      %ax, 96(%esp)
        xorl      %edx, %edx
        fxch      %st(5)
        fstpt     116(%esp)
        fldt      116(%esp)
        xorl      %eax, %eax
        fxch      %st(5)
        fstpt     64(%esp)
        xorl      %ecx, %ecx
        fldt      88(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     144(%esp)
        fldt      144(%esp)
        movzwl    152(%esp), %esi
        andl      $32767, %esi
        fxch      %st(3)
        fmulp     %st, %st(2)
        cmpl      $0, 44(%esp)
        je        ..B1.57
..B1.44:
        cmpl      $8383, %esi
        jge       ..B1.46
..B1.45:
        fstp      %st(2)
        fstp      %st(3)
        movl      %eax, 168(%esp)
        movl      %eax, 140(%esp)
        fxch      %st(2)
        fstpt     156(%esp)
        fstpt     128(%esp)
        jmp       ..B1.52
..B1.46:
        cmpl      $24383, %esi
        jg        ..B1.45
..B1.48:
        movzwl    124(%esp), %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B1.45
..B1.50:
        cmpl      $24383, %esi
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jle       ..B1.55
..B1.51:
        fstp      %st(1)
        fstp      %st(2)
        movl      %eax, 168(%esp)
        movl      %eax, 140(%esp)
        fxch      %st(1)
        fstpt     156(%esp)
        fstpt     128(%esp)
..B1.52:
        addl      $4, %esp
        lea       140(%esp), %eax
        pushl     %eax
        fstpt     -40(%eax)
        call      __libm_normalizel_k80
..B1.53:
        fldt      104(%esp)
        addl      $4, %esp
        lea       112(%esp), %eax
        pushl     %eax
        fstpt     -12(%eax)
        call      __libm_normalizel_k80
..B1.54:
        fldt      104(%esp)
        fldt      116(%esp)
        fldt      128(%esp)
        fldt      144(%esp)
        fldt      156(%esp)
        movl      140(%esp), %edx
        movl      168(%esp), %ecx
..B1.55:
        fld       %st(1)
        subl      %edx, %ecx
        fdiv      %st(4), %st
        addl      $16383, %ecx
        fstpt     48(%esp)
        fldt      48(%esp)
        andl      $32767, %ecx
        fxch      %st(5)
        fstpt     104(%esp)
        fxch      %st(3)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.16@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(6), %st
        fld       %st(0)
        movl      28(%esp), %eax
        fsubr     %st(7), %st
        andl      $-32768, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        orl       %ecx, %eax
        movl      8(%ebp), %edx
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fldt      32(%esp)
        fdivrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      64(%esp)
        fstpt     88(%esp)
        fldt      36+_P@GOTOFF(%ebx)
        fldt      12+_P@GOTOFF(%ebx)
        fldt      24+_P@GOTOFF(%ebx)
        fldt      _P@GOTOFF(%ebx)
        movw      %ax, 96(%esp)
        fldt      88(%esp)
        fmulp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fadd      %st(1), %st
        fxch      %st(1)
        fstpt     88(%esp)
        fldl      8192+__libm_atanl_table_128@GOTOFF(%ebx)
        fldl      8200+__libm_atanl_table_128@GOTOFF(%ebx)
        movl      $0, 24(%edx)
        fsubp     %st, %st(2)
        fldt      104(%esp)
        fmul      %st, %st(1)
        fld       %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fadd      %st(2), %st
        fstpt     (%edx)
        fldt      (%edx)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     12(%edx)
        movl      172(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.89
..B1.56:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.57:
        cmpl      $8383, %esi
        jge       ..B1.59
..B1.58:
        fstp      %st(2)
        fstp      %st(3)
        movl      %eax, 168(%esp)
        movl      %eax, 140(%esp)
        fxch      %st(2)
        fstpt     156(%esp)
        fstpt     128(%esp)
        jmp       ..B1.65
..B1.59:
        cmpl      $24383, %esi
        jg        ..B1.58
..B1.61:
        movzwl    124(%esp), %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B1.58
..B1.63:
        cmpl      $24383, %esi
        fxch      %st(4)
        fxch      %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jle       ..B1.68
..B1.64:
        fstp      %st(1)
        fstp      %st(2)
        movl      %eax, 168(%esp)
        movl      %eax, 140(%esp)
        fxch      %st(1)
        fstpt     156(%esp)
        fstpt     128(%esp)
..B1.65:
        addl      $4, %esp
        lea       140(%esp), %eax
        pushl     %eax
        fstpt     -40(%eax)
        call      __libm_normalizel_k80
..B1.66:
        fldt      104(%esp)
        addl      $4, %esp
        lea       112(%esp), %eax
        pushl     %eax
        fstpt     -12(%eax)
        call      __libm_normalizel_k80
..B1.67:
        fldt      104(%esp)
        fldt      116(%esp)
        fldt      128(%esp)
        fldt      144(%esp)
        fldt      156(%esp)
        movl      140(%esp), %edx
        movl      168(%esp), %ecx
..B1.68:
        fld       %st(1)
        subl      %edx, %ecx
        fdiv      %st(4), %st
        addl      $16383, %ecx
        fstpt     48(%esp)
        fldt      48(%esp)
        andl      $32767, %ecx
        fxch      %st(5)
        fstpt     104(%esp)
        fxch      %st(3)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.16@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(6), %st
        fld       %st(0)
        movl      28(%esp), %eax
        fsubr     %st(7), %st
        andl      $-32768, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        orl       %ecx, %eax
        movl      8(%ebp), %edx
        fsubrp    %st, %st(7)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        movl      $0, 24(%edx)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fsubr     %st, %st(2)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(5)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      48(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fldt      32(%esp)
        fdivrp    %st, %st(2)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      64(%esp)
        fstpt     88(%esp)
        fldt      36+_P@GOTOFF(%ebx)
        fldt      12+_P@GOTOFF(%ebx)
        fldt      _P@GOTOFF(%ebx)
        movw      %ax, 96(%esp)
        fldt      88(%esp)
        fmul      %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(4)
        fmul      %st(5), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(6)
        fmulp     %st, %st(6)
        fxch      %st(4)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldt      24+_P@GOTOFF(%ebx)
        fmulp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fldt      104(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%edx)
        fmulp     %st, %st(1)
        fstpt     12(%edx)
        movl      172(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.89
..B1.69:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.70:
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        subl      %ecx, %edx
        addl      $16383, %edx
        movzwl    8+.L_2il0floatpacket.12@GOTOFF(%ebx), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        fstpt     88(%esp)
        orl       %edx, %eax
        movw      %ax, 96(%esp)
        fld       %st(4)
        fxch      %st(1)
        fstpt     104(%esp)
        xorl      %edx, %edx
        fxch      %st(4)
        fstpt     116(%esp)
        fldt      116(%esp)
        fxch      %st(3)
        fstpt     128(%esp)
        fldt      128(%esp)
        fldt      88(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     144(%esp)
        fldt      144(%esp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      156(%esp)
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        fmul      %st(3), %st
        fdiv      %st(4), %st
        fldt      .L_2il0floatpacket.14@GOTOFF(%ebx)
        movl      %edx, 140(%esp)
        movl      %edx, 168(%esp)
        fxch      %st(1)
        faddl     _TWO_63H@GOTOFF(%ebx)
        fstpt     76(%esp)
        fldt      76(%esp)
        fldl      _TWO_48H@GOTOFF(%ebx)
        movl      76(%esp), %eax
        shll      $4, %eax
        fxch      %st(1)
        fsubl     _TWO_63H@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fld       %st(5)
        fmul      %st(1), %st
        cmpl      $0, 44(%esp)
        fadd      %st, %st(7)
        fxch      %st(7)
        fstpt     76(%esp)
        fldt      76(%esp)
        fsubp     %st, %st(7)
        fxch      %st(5)
        fsub      %st(6), %st
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(5), %st
        fstpt     76(%esp)
        fldt      76(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(1), %st
        fadd      %st(3), %st
        fld       %st(2)
        fmul      %st(2), %st
        fadd      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(5)
        fxch      %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fsubrp    %st, %st(1)
        fld       %st(4)
        fadd      %st(2), %st
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        fdivp     %st, %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     76(%esp)
        fxch      %st(4)
        fstpt     88(%esp)
        fldt      88(%esp)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      76(%esp)
        fsubp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(5), %st
        fld       %st(6)
        fmul      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st, %st(6)
        fldt      .L_2il0floatpacket.15@GOTOFF(%ebx)
        faddp     %st, %st(7)
        fxch      %st(1)
        faddp     %st, %st(6)
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(3)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fadd      %st(3), %st
        fld       %st(1)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fldt      36+_P@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _P@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      104(%esp)
        je        ..B1.72
..B1.71:
        negl      %eax
        fld       %st(2)
        fldl      8192+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldl      8200+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        fsubp     %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.73
..B1.72:
        fldl      __libm_atanl_table_128@GOTOFF(%eax,%ebx)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldl      8+__libm_atanl_table_128@GOTOFF(%eax,%ebx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
..B1.73:
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      8(%ebp), %eax
        fadd      %st, %st(1)
        movl      $0, 24(%eax)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fstpt     12(%eax)
        fstpt     (%eax)
        movl      172(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.89
..B1.74:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.75:
        lea       12(%ecx), %eax
        cmpl      %eax, %edx
        jle       ..B1.84
..B1.76:
        fstp      %st(3)
        fstp      %st(0)
        lea       75(%ecx), %eax
        cmpl      %eax, %edx
        jg        ..B1.82
..B1.77:
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        subl      %ecx, %edx
        addl      $16383, %edx
        movzwl    8+.L_2il0floatpacket.12@GOTOFF(%ebx), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        fstpt     88(%esp)
        orl       %edx, %eax
        fldt      36+_P@GOTOFF(%ebx)
        fldt      _P@GOTOFF(%ebx)
        movw      %ax, 96(%esp)
        fldt      88(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fdivrp    %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fldt      12+_P@GOTOFF(%ebx)
        movl      8(%ebp), %edx
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      24+_P@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        cmpl      $0, 44(%esp)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(2)
        fldl      4096+__libm_atanl_table_128@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldl      4104+__libm_atanl_table_128@GOTOFF(%ebx)
        movl      $0, 24(%edx)
        je        ..B1.79
..B1.78:
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        jmp       ..B1.80
..B1.79:
        fsubp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
..B1.80:
        fld       %st(0)
        movl      8(%ebp), %eax
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     12(%eax)
        movl      172(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.89
..B1.81:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.82:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      8(%ebp), %edx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %esi
        fldt      _pi_02l@GOTOFF(%esi,%ebx)
        movl      $0, 24(%edx)
        fstpt     (%edx)
        fldt      _pilo_02l@GOTOFF(%esi,%ebx)
        fldt      _small_value_80@GOTOFF(%esi,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%edx)
        movl      172(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.89
..B1.83:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.84:
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        subl      %ecx, %edx
        addl      $16383, %edx
        movzwl    8+.L_2il0floatpacket.12@GOTOFF(%ebx), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        fstpt     88(%esp)
        orl       %edx, %eax
        movw      %ax, 96(%esp)
        fld       %st(4)
        fxch      %st(1)
        fstpt     104(%esp)
        xorl      %edx, %edx
        fxch      %st(4)
        fstpt     116(%esp)
        fldt      116(%esp)
        fxch      %st(3)
        fstpt     128(%esp)
        fldt      128(%esp)
        fldt      88(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     144(%esp)
        fldt      144(%esp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      156(%esp)
        fldt      .L_2il0floatpacket.13@GOTOFF(%ebx)
        fmul      %st(4), %st
        fdiv      %st(3), %st
        fldt      .L_2il0floatpacket.14@GOTOFF(%ebx)
        movl      %edx, 140(%esp)
        movl      %edx, 168(%esp)
        fxch      %st(1)
        faddl     _TWO_63H@GOTOFF(%ebx)
        fstpt     76(%esp)
        fld       %st(4)
        fldt      76(%esp)
        movl      76(%esp), %eax
        shll      $4, %eax
        fsubl     _TWO_63H@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldl      _TWO_48H@GOTOFF(%ebx)
        fmul      %st, %st(7)
        cmpl      $0, 44(%esp)
        fxch      %st(1)
        fadd      %st(7), %st
        fstpt     76(%esp)
        fldt      76(%esp)
        fsubp     %st, %st(7)
        fxch      %st(5)
        fsub      %st(6), %st
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(4), %st
        fld       %st(0)
        fadd      %st(5), %st
        fstpt     76(%esp)
        fldt      76(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fld       %st(5)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     32(%esp)
        fld       %st(2)
        fmul      %st(1), %st
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      32(%esp)
        fadd      %st(2), %st
        fldt      32(%esp)
        fldt      .L_2il0floatpacket.12@GOTOFF(%ebx)
        fdivp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fstpt     76(%esp)
        fxch      %st(4)
        fstpt     88(%esp)
        fldt      88(%esp)
        fxch      %st(3)
        fmul      %st(4), %st
        fldt      76(%esp)
        fsubp     %st, %st(4)
        fld       %st(3)
        fsubr     %st(5), %st
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%esp)
        fmul      %st(4), %st
        fldt      .L_2il0floatpacket.15@GOTOFF(%ebx)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fsubp     %st, %st(3)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fadd      %st(4), %st
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fmul      %st(1), %st
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fld       %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%ebx)
        fmul      %st(1), %st
        fldt      12+_P@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fldt      _P@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      104(%esp)
        je        ..B1.86
..B1.85:
        fldl      4096+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldl      4104+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.87
..B1.86:
        negl      %eax
        fld       %st(2)
        fldl      4096+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        fsub      %st, %st(1)
        fsub      %st(1), %st
        fsubp     %st, %st(4)
        fxch      %st(3)
        fstpt     88(%esp)
        fldt      88(%esp)
        fldl      4104+__libm_atanl_table_128@GOTOFF(%ebx,%eax)
        fsubp     %st, %st(3)
        faddp     %st, %st(2)
..B1.87:
        fmul      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      8(%ebp), %eax
        fadd      %st, %st(1)
        movl      $0, 24(%eax)
        fxch      %st(1)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fstpt     12(%eax)
        fstpt     (%eax)
        movl      172(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.89
..B1.88:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.89:
        call      __stack_chk_fail@PLT
..B1.97:
        cmpl      $-2147483648, 4(%edx)
        jne       ..B1.99
..B1.98:
        cmpl      $0, (%edx)
        je        ..B1.100
..B1.99:
        fldt      (%edx)
        fldt      (%ecx)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        movl      8(%ebp), %eax
        movl      $0, 24(%eax)
        fstpt     12(%eax)
        fstpt     (%eax)
        jmp       ..B1.104
..B1.100:
        movl      8(%ebp), %edx
        cmpl      $32767, %eax
        movl      $0, 24(%edx)
        je        ..B1.107
..B1.101:
        cmpl      $0, 44(%esp)
        je        ..B1.103
..B1.102:
        movl      8(%ebp), %ecx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %edx
        fldt      _pi_00l@GOTOFF(%edx,%ebx)
        fstpt     (%ecx)
        fldt      _pilo_00l@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%ecx)
        jmp       ..B1.104
..B1.103:
        fldt      .L_2il0floatpacket.11@GOTOFF(%ebx)
        fldz      
        fmull     ones@GOTOFF(%ebx,%esi,8)
        movl      8(%ebp), %eax
        fstpt     (%eax)
        fstpt     12(%eax)
..B1.104:
        movl      172(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.89
        jmp       ..B1.19
..B1.107:
        cmpl      $0, 44(%esp)
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %edx
        je        ..B1.109
..B1.108:
        fldt      _pi_34l@GOTOFF(%edx,%ebx)
        fldt      _pilo_34l@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        movl      8(%ebp), %eax
        fsubrp    %st, %st(1)
        fstpt     12(%eax)
        fstpt     (%eax)
        jmp       ..B1.104
..B1.109:
        fldt      _pi_04l@GOTOFF(%edx,%ebx)
        fldt      _pilo_04l@GOTOFF(%edx,%ebx)
        fldt      _small_value_80@GOTOFF(%edx,%ebx)
        movl      8(%ebp), %eax
        fsubrp    %st, %st(1)
        fstpt     12(%eax)
        fstpt     (%eax)
        jmp       ..B1.104
..B1.110:
        cmpl      $-2147483648, 4(%ecx)
        jne       ..B1.99
..B1.111:
        cmpl      $0, (%ecx)
        jne       ..B1.99
..B1.112:
        cmpl      $32767, %edi
        je        ..B1.97
..B1.113:
        movl      8(%ebp), %edx
        lea       (,%esi,8), %eax
        lea       (%eax,%esi,4), %esi
        fldt      _pi_02l@GOTOFF(%esi,%ebx)
        movl      $0, 24(%edx)
        fstpt     (%edx)
        fldt      _pilo_02l@GOTOFF(%esi,%ebx)
        fldt      _small_value_80@GOTOFF(%esi,%ebx)
        fsubrp    %st, %st(1)
        fstpt     12(%edx)
        jmp       ..B1.104
        .align    16,0x90
	.type	__libm_atan2l_k80,@function
	.size	__libm_atan2l_k80,.-__libm_atan2l_k80
	.data
# -- End  __libm_atan2l_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.12:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x06,0x40,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.14:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf8,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.15:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.16:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,12
	.space 4, 0x00 	# pad
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 2
_pi_00l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16384
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49152
	.word	0
	.type	_pi_00l,@object
	.size	_pi_00l,24
	.align 2
_pilo_00l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49086
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16318
	.word	0
	.type	_pilo_00l,@object
	.size	_pilo_00l,24
	.align 2
_small_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	6383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39151
	.word	0
	.type	_small_value_80,@object
	.size	_small_value_80,24
	.align 2
_pi_02l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49151
	.word	0
	.type	_pi_02l,@object
	.size	_pi_02l,24
	.align 2
_pilo_02l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49085
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16317
	.word	0
	.type	_pilo_02l,@object
	.size	_pilo_02l,24
	.align 2
_P:
	.word	43664
	.word	43690
	.word	43690
	.word	43690
	.word	49149
	.word	0
	.word	43235
	.word	52379
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	59507
	.word	38278
	.word	9340
	.word	37449
	.word	49148
	.word	0
	.word	9132
	.word	55602
	.word	8665
	.word	58245
	.word	16379
	.word	0
	.type	_P,@object
	.size	_P,48
	.align 2
_pi_34l:
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	16384
	.word	0
	.word	37288
	.word	39182
	.word	58361
	.word	38603
	.word	49152
	.word	0
	.type	_pi_34l,@object
	.size	_pi_34l,24
	.align 2
_pilo_34l:
	.word	54470
	.word	48821
	.word	27694
	.word	55510
	.word	49087
	.word	0
	.word	54470
	.word	48821
	.word	27694
	.word	55510
	.word	16319
	.word	0
	.type	_pilo_34l,@object
	.size	_pilo_34l,24
	.align 2
_pi_04l:
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16382
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	49150
	.word	0
	.type	_pi_04l,@object
	.size	_pi_04l,24
	.align 2
_pilo_04l:
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49084
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	16316
	.word	0
	.type	_pilo_04l,@object
	.size	_pilo_04l,24
	.data
	.hidden __libm_atanl_table_128
	.hidden __libm_normalizel_k80
	.section .note.GNU-stack, ""
# End
