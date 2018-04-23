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
	.file "ctanhl.c"
	.text
..TXTST0:
# -- Begin  ctanhl
	.text
       .align    16,0x90
	.globl ctanhl
ctanhl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $308, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        movl      %edx, 304(%esp)
        fstpt     280(%esp)
        fldt      280(%esp)
        fstpt     292(%esp)
..B1.2:
        fnstcw    94(%esp)
..B1.3:
        movzwl    94(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.81
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 92(%esp)
..B1.5:
        fldcw     92(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.50
..B1.8:
        movzwl    32(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.50
..B1.9:
        cmpl      $0, 16(%ebp)
        jne       ..B1.11
..B1.10:
        cmpl      $0, 12(%ebp)
        je        ..B1.50
..B1.11:
        cmpl      $16396, %edx
        jl        ..B1.22
..B1.12:
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        movzbl    21(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        cmpl      $0, 28(%ebp)
        lea       (,%eax,8), %edx
        lea       (%edx,%eax,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fsub      %st(1), %st
        fstpt     280(%esp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, 24(%ebp)
        je        ..B1.15
..B1.14:
        fmul      %st(0), %st
        fstpt     292(%esp)
        jmp       ..B1.16
..B1.15:
        fstp      %st(0)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fstpt     292(%esp)
..B1.16:
        testl     %ecx, %ecx
        jle       ..B1.74
..B1.17:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanl@PLT
..B1.84:
        addl      $16, %esp
        fstpt     172(%esp)
        movzbl    301(%esp), %edx
        movzbl    181(%esp), %ecx
        andl      $127, %edx
        andl      $-128, %ecx
        orl       %ecx, %edx
..B1.18:
        movb      %dl, 301(%esp)
        testl     %edi, %edi
        je        ..B1.20
..B1.19:
        fldcw     94(%esp)
..B1.20:
        movl      280(%esp), %edx
        movl      %edx, (%esi)
        movl      292(%esp), %edx
        movl      %edx, 12(%esi)
        movl      296(%esp), %edx
        movl      %edx, 16(%esi)
        movl      284(%esp), %ecx
        movl      288(%esp), %edi
        movl      300(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      304(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.73
..B1.21:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.22:
        fldt      12(%ebp)
        addl      $-16, %esp
        lea       224(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        call      __libm_coshl_k80
..B1.85:
        addl      $16, %esp
..B1.23:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fstpt     64(%esp)
        fldt      64(%esp)
        faddp     %st, %st(1)
        fstpt     (%esp)
        fldt      208(%esp)
        fld       %st(0)
        fldt      220(%esp)
        movzwl    20(%ebp), %ecx
        fadd      %st, %st(1)
        andl      $32767, %ecx
        fxch      %st(1)
        fsub      %st, %st(2)
        movl      (%esp), %edx
        cmpl      $16183, %ecx
        movl      %edx, 76(%esp)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fstpt     40(%esp)
        jge       ..B1.25
..B1.24:
        fldt      12(%ebp)
        lea       196(%esp), %eax
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     -12(%eax)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        movzwl    -4(%eax), %ecx
        movl      %ecx, %edx
        andl      $32767, %edx
        andl      $-32768, %ecx
        addl      $-16639, %edx
        orl       $-49153, %ecx
        fstpt     (%eax)
        movl      %edx, 52(%esp)
        movw      %cx, -4(%eax)
        jmp       ..B1.27
..B1.25:
        fldt      12(%ebp)
        addl      $-16, %esp
        lea       200(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        fstpt     32(%esp)
        call      __libm_sinhl_k80
..B1.86:
        fldt      32(%esp)
        addl      $16, %esp
..B1.26:
        fldt      64(%esp)
        lea       196(%esp), %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        movl      (%esp), %edx
        movl      %edx, 52(%esp)
..B1.27:
        fldt      184(%esp)
        fld       %st(0)
        fldt      (%eax)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     28(%esp)
        fldt      28(%esp)
        addl      $-16, %esp
        fsubp     %st, %st(2)
        fsubp     %st, %st(1)
        fldt      24(%ebp)
        fstpt     (%esp)
        lea       248(%esp), %edx
        movl      %edx, 12(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     -104(%edx)
        call      __libm_cosl_k80
..B1.87:
        fldt      144(%esp)
        fldt      32(%esp)
        addl      $16, %esp
..B1.28:
        fldt      232(%esp)
        fld       %st(0)
        fldt      244(%esp)
        movl      52(%esp), %ecx
        fadd      %st, %st(1)
        movzwl    36(%esp), %edx
        fxch      %st(1)
        fsub      %st, %st(2)
        andl      $32767, %edx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fmul      %st(4), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        addl      %ecx, 76(%esp)
        addl      %ecx, %edx
        fxch      %st(3)
        fstpt     (%esp)
        fldt      (%esp)
        fld       %st(1)
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpt     144(%esp)
        fldt      144(%esp)
        fstpt     16(%esp)
        fldt      28(%esp)
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fstpt     96(%esp)
        fldt      96(%esp)
        fldt      96+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        movzwl    24(%esp), %ecx
        fxch      %st(2)
        fsub      %st, %st(3)
        andl      $32767, %ecx
        fsubp     %st, %st(3)
        fsub      %st(2), %st
        fadd      %st(5), %st
        fld       %st(4)
        fmul      %st(2), %st
        fsub      %st, %st(7)
        fsubp     %st, %st(7)
        fxch      %st(4)
        fsub      %st(6), %st
        fxch      %st(1)
        fstpt     80(%esp)
        fld       %st(1)
        lea       64(%ecx), %eax
        fmul      %st(6), %st
        cmpl      %eax, %edx
        fldt      40(%esp)
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(5), %st
        fxch      %st(7)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(6)
        fmulp     %st, %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fstpt     112(%esp)
        fldt      112(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      144(%esp)
        jge       ..B1.33
..B1.29:
        addl      $-64, %ecx
        cmpl      %ecx, %edx
        jle       ..B1.100
..B1.30:
        movl      52(%esp), %edx
        fstpt     28(%esp)
        fstpt     40(%esp)
        lea       (%edx,%edx), %ecx
        movl      %ecx, 12(%esp)
        movzwl    36(%esp), %ecx
        movl      %ecx, %eax
        subl      %edx, %ecx
        andl      $-32768, %eax
        andl      $32767, %ecx
        orl       %ecx, %eax
        movzwl    8(%esp), %ecx
        andl      $32767, %ecx
        subl      %edx, %ecx
        movw      %ax, 36(%esp)
        testl     %ecx, %ecx
        jle       ..B1.32
..B1.31:
        fldt      28(%esp)
        movzwl    48(%esp), %edx
        movl      %edx, %ecx
        subl      52(%esp), %edx
        andl      $-32768, %ecx
        andl      $32767, %edx
        orl       %edx, %ecx
        movw      %cx, 48(%esp)
        fxch      %st(1)
        jmp       ..B1.36
..B1.32:
        fldt      28(%esp)
        fxch      %st(1)
        jmp       ..B1.36
..B1.33:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fstpt     28(%esp)
        fldt      28(%esp)
        fstpt     40(%esp)
        fldt      40(%esp)
        jl        ..B1.35
..B1.34:
        fstp      %st(2)
        fstp      %st(0)
        movl      52(%esp), %edx
        lea       (%edx,%edx), %ecx
        movl      %ecx, 12(%esp)
        fxch      %st(1)
        jmp       ..B1.36
..B1.35:
        fstp      %st(3)
        fstpt     96(%esp)
        movl      $0, 12(%esp)
..B1.36:
        addl      $-32, %esp
        lea       288(%esp), %edx
        movl      %edx, (%esp)
        fldt      128(%esp)
        fstpt     4(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fstpt     -128(%edx)
        fstpt     -112(%edx)
        call      __libm_hypot2l_k80
..B1.88:
        fldt      176(%esp)
        fldt      160(%esp)
        addl      $32, %esp
..B1.37:
        fldt      64(%esp)
        movzwl    8+_CONSTANTS@GOTOFF(%ebx), %edx
        movl      %edx, %eax
        andl      $-32768, %eax
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     52(%esp)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        movl      52(%esp), %ecx
        addl      %edx, %ecx
        andl      $32767, %ecx
        fstpt     172(%esp)
        orl       %ecx, %eax
        fldt      256(%esp)
        fldt      268(%esp)
        fldt      96(%esp)
        fldt      28(%esp)
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        movw      %ax, 180(%esp)
        fmul      %st, %st(2)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fmulp     %st, %st(5)
        fldt      172(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpt     144(%esp)
        fldt      144(%esp)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     256(%esp)
        fldt      256(%esp)
        fldt      40(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     268(%esp)
        fldt      268(%esp)
        fldt      112(%esp)
        fld       %st(0)
        fdiv      %st(4), %st
        fstpt     160(%esp)
        fldt      160(%esp)
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        fsubr     %st(3), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fstpt     128(%esp)
        fldt      128(%esp)
        fmul      %st(6), %st
        fld       %st(6)
        fsub      %st(1), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubrp    %st, %st(7)
        fld       %st(1)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st(7), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      160(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fld       %st(0)
        fldt      144(%esp)
        addl      $-32, %esp
        fdivrp    %st, %st(3)
        fadd      %st(2), %st
        fstpt     (%esp)
        fldt      (%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        movl      108(%esp), %edx
        subl      44(%esp), %edx
        movl      %edx, 24(%esp)
        call      __libm_scalbl_k80
..B1.89:
        addl      $32, %esp
..B1.38:
        fstpt     280(%esp)
        cmpl      $0, 28(%ebp)
        jne       ..B1.44
..B1.39:
        cmpl      $0, 24(%ebp)
        jne       ..B1.44
..B1.40:
        fldt      24(%ebp)
        testl     %edi, %edi
        fstpt     292(%esp)
        je        ..B1.42
..B1.41:
        fldcw     94(%esp)
..B1.42:
        movl      280(%esp), %edx
        movl      %edx, (%esi)
        movl      292(%esp), %edx
        movl      %edx, 12(%esi)
        movl      296(%esp), %edx
        movl      %edx, 16(%esi)
        movl      284(%esp), %ecx
        movl      288(%esp), %edi
        movl      300(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      304(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.73
..B1.43:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.44:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $16183, %edx
        jge       ..B1.46
..B1.45:
        fldt      24(%ebp)
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     52(%esp)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        movzwl    60(%esp), %edx
        movl      %edx, %ecx
        andl      $-32768, %edx
        andl      $32767, %ecx
        orl       $-49153, %edx
        addl      $-16639, %ecx
        movw      %dx, 60(%esp)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      52(%esp)
        fxch      %st(1)
        jmp       ..B1.48
..B1.46:
        fldt      24(%ebp)
        addl      $-16, %esp
        lea       68(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        call      __libm_sinl_k80
..B1.90:
        addl      $16, %esp
..B1.47:
        fldt      52(%esp)
        xorl      %ecx, %ecx
        fmul      %st(1), %st
        fstpt     52(%esp)
        fldt      52(%esp)
        fldt      64(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
..B1.48:
        fldt      80(%esp)
        fld       %st(0)
        fld       %st(3)
        fadd      %st(3), %st
        fmul      %st, %st(1)
        fsub      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(3)
        fld       %st(3)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsubr     %st(3), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fxch      %st(2)
        fstpt     28(%esp)
        fldt      28(%esp)
        fldt      (%esp)
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      256(%esp)
        fld       %st(0)
        fdivr     %st(2), %st
        fld       %st(1)
        fldt      128(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fsubr     %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      268(%esp)
        fmul      %st(3), %st
        fsubrp    %st, %st(2)
        fldt      256(%esp)
        fdivrp    %st, %st(2)
        addl      $-32, %esp
        fadd      %st(1), %st
        fstpt     (%esp)
        fldt      (%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        subl      44(%esp), %ecx
        movl      %ecx, 24(%esp)
        call      __libm_scalbl_k80
..B1.91:
        addl      $32, %esp
..B1.49:
        fstpt     292(%esp)
        jmp       ..B1.69
..B1.50:
        movl      28(%ebp), %eax
        testl     %eax, %eax
        jne       ..B1.53
..B1.51:
        cmpl      $0, 24(%ebp)
        jne       ..B1.53
..B1.52:
        movl      16(%ebp), %ecx
        jmp       ..B1.55
..B1.53:
        movzwl    32(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.60
..B1.54:
        movl      16(%ebp), %ecx
        cmpl      $32767, %edx
        je        ..B1.77
..B1.55:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %ecx, 16(%esp)
        call      tanhl@PLT
..B1.92:
        movl      16(%esp), %ecx
        addl      $16, %esp
        fstpt     280(%esp)
        testl     %ecx, %ecx
        jne       ..B1.57
..B1.56:
        cmpl      $0, 12(%ebp)
        je        ..B1.59
..B1.57:
        movzwl    32(%ebp), %edx
        testl     $32767, %edx
        jle       ..B1.76
..B1.58:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanl@PLT
..B1.93:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpt     292(%esp)
        jmp       ..B1.69
..B1.59:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanl@PLT
..B1.94:
        addl      $16, %esp
        fstpt     292(%esp)
        jmp       ..B1.69
..B1.60:
        cmpl      $32767, %edx
        jge       ..B1.62
..B1.61:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanl@PLT
..B1.95:
        addl      $16, %esp
        fstpt     280(%esp)
        fldt      280(%esp)
        fstpt     292(%esp)
        jmp       ..B1.69
..B1.62:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.64
..B1.63:
        cmpl      $0, 12(%ebp)
        je        ..B1.65
..B1.64:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanhl@PLT
..B1.96:
        fldt      24(%ebp)
        addl      $16, %esp
        fmul      %st(1), %st
        fstpt     292(%esp)
        fstpt     280(%esp)
        jmp       ..B1.69
..B1.65:
        cmpl      $-2147483648, %eax
        jne       ..B1.67
..B1.66:
        cmpl      $0, 24(%ebp)
        je        ..B1.68
..B1.67:
        fldt      24(%ebp)
        addl      $-16, %esp
        fmul      %st(0), %st
        fstpt     24(%ebp)
        fldt      12(%ebp)
        fstpt     (%esp)
        call      tanhl@PLT
..B1.97:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        addl      $16, %esp
        fstpt     292(%esp)
        fstpt     280(%esp)
        jmp       ..B1.69
..B1.68:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanhl@PLT
..B1.98:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        addl      $16, %esp
        movzbl    33(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fxch      %st(1)
        fstpt     280(%esp)
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fmulp     %st, %st(1)
        fstpt     292(%esp)
..B1.69:
        testl     %edi, %edi
        je        ..B1.71
..B1.70:
        fldcw     94(%esp)
..B1.71:
        movl      280(%esp), %edx
        movl      %edx, (%esi)
        movl      292(%esp), %edx
        movl      %edx, 12(%esi)
        movl      296(%esp), %edx
        movl      %edx, 16(%esi)
        movl      284(%esp), %ecx
        movl      288(%esp), %edi
        movl      300(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      304(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.73
..B1.72:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.73:
        call      __stack_chk_fail@PLT
..B1.74:
        movzbl    301(%esp), %edx
        movzbl    33(%ebp), %ecx
        andl      $127, %edx
        andl      $-128, %ecx
        orl       %ecx, %edx
        jmp       ..B1.18
..B1.76:
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fldt      24(%ebp)
        fmulp     %st, %st(1)
        fstpt     292(%esp)
        jmp       ..B1.69
..B1.77:
        cmpl      $-2147483648, %ecx
        jne       ..B1.60
..B1.78:
        cmpl      $0, 12(%ebp)
        jne       ..B1.60
..B1.79:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      tanhl@PLT
..B1.99:
        addl      $16, %esp
        fstpt     280(%esp)
        jmp       ..B1.57
..B1.81:
        xorl      %edi, %edi
        jmp       ..B1.7
..B1.100:
        cmpl      %eax, %edx
        jmp       ..B1.33
        .align    16,0x90
	.type	ctanhl,@function
	.size	ctanhl,.-ctanhl
	.data
# -- End  ctanhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
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
	.word	16639
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	32768
	.word	16415
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.word	0
	.word	16384
	.word	0
	.word	32768
	.word	16416
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,108
	.data
	.hidden __libm_sinl_k80
	.hidden __libm_cosl_k80
	.hidden __libm_sinhl_k80
	.hidden __libm_coshl_k80
	.hidden __libm_hypot2l_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
