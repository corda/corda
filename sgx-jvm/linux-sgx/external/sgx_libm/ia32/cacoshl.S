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
	.file "cacoshl.c"
	.text
..TXTST0:
# -- Begin  cacoshl
	.text
       .align    16,0x90
	.globl cacoshl
cacoshl:
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
        movl      8(%ebp), %eax
        movl      %eax, 12(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 268(%esp)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     244(%esp)
        fldt      244(%esp)
        fstpt     256(%esp)
..B1.2:
        fnstcw    18(%esp)
..B1.3:
        movzwl    18(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.67
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 16(%esp)
..B1.5:
        fldcw     16(%esp)
..B1.6:
        movl      $1, %esi
..B1.7:
        movzwl    20(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.16
..B1.8:
        testl     %eax, %eax
        jle       ..B1.46
..B1.9:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.16
..B1.10:
        testl     %edx, %edx
        jle       ..B1.44
..B1.11:
        addl      $-48, %esp
        lea       12(%ebp), %eax
        lea       24(%ebp), %edx
        lea       264(%esp), %ecx
        lea       236(%esp), %edi
        movl      %eax, (%esp)
        lea       208(%esp), %eax
        movl      %edx, 4(%esp)
        lea       180(%esp), %edx
        movl      %ecx, 8(%esp)
        lea       152(%esp), %ecx
        movl      %edi, 12(%esp)
        lea       68(%esp), %edi
        movl      %eax, 16(%esp)
        lea       96(%esp), %eax
        movl      %edx, 20(%esp)
        lea       124(%esp), %edx
        movl      %ecx, 24(%esp)
        movl      %edx, 28(%esp)
        movl      %eax, 32(%esp)
        movl      %edi, 36(%esp)
        call      __libm_carctrigprepare_k80
..B1.70:
        lea       96(%esp), %eax
        lea       124(%esp), %edx
..B1.12:
        lea       12(%ebp), %ecx
        movl      %ecx, (%esp)
        lea       24(%ebp), %ecx
        movl      %ecx, 4(%esp)
        lea       264(%esp), %ecx
        movl      %ecx, 8(%esp)
        lea       236(%esp), %ecx
        movl      %ecx, 12(%esp)
        lea       208(%esp), %ecx
        movl      %ecx, 16(%esp)
        lea       180(%esp), %ecx
        movl      %ecx, 20(%esp)
        lea       152(%esp), %ecx
        movl      %ecx, 24(%esp)
        movl      %edx, 28(%esp)
        movl      %eax, 32(%esp)
        movl      %edi, 36(%esp)
        call      __libm_recacoshl_k80
..B1.71:
        lea       96(%esp), %eax
        lea       124(%esp), %edx
        addl      $48, %esp
..B1.13:
        fstpt     244(%esp)
        addl      $-48, %esp
        lea       12(%ebp), %ecx
        movl      %ecx, (%esp)
        lea       24(%ebp), %ecx
        movl      %ecx, 4(%esp)
        lea       264(%esp), %ecx
        movl      %ecx, 8(%esp)
        lea       236(%esp), %ecx
        movl      %ecx, 12(%esp)
        lea       208(%esp), %ecx
        movl      %ecx, 16(%esp)
        lea       180(%esp), %ecx
        movl      %ecx, 20(%esp)
        lea       152(%esp), %ecx
        movl      %ecx, 24(%esp)
        movl      %edx, 28(%esp)
        movl      %eax, 32(%esp)
        movl      %edi, 36(%esp)
        call      __libm_recacosl_k80
..B1.72:
        addl      $48, %esp
..B1.14:
        movzbl    33(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      ones@GOTOFF(%ebx,%eax,8)
        fmulp     %st, %st(1)
        fstpt     256(%esp)
        jmp       ..B1.40
..B1.16:
        movl      28(%ebp), %ecx
..B1.17:
        testl     %ecx, %ecx
        jne       ..B1.27
..B1.81:
        cmpl      $0, 24(%ebp)
..B1.18:
        jne       ..B1.27
..B1.19:
        cmpl      $32767, %eax
        jl        ..B1.22
..B1.20:
        movl      16(%ebp), %edi
        cmpl      $-2147483648, %edi
        jne       ..B1.28
..B1.21:
        cmpl      $0, 12(%ebp)
        jne       ..B1.34
..B1.22:
        cmpl      $16383, %eax
        jl        ..B1.24
..B1.23:
        fldt      12(%ebp)
        movzbl    21(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      ones@GOTOFF(%ebx,%eax,8)
        fstl      (%esp)
        addl      $-16, %esp
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      acoshl@PLT
..B1.74:
        addl      $16, %esp
        fstpt     244(%esp)
        addl      $-16, %esp
        fldl      16(%esp)
        fstpt     (%esp)
        call      acosl@PLT
        jmp       ..B1.75
..B1.24:
        fldt      (%esp)
        fstpt     244(%esp)
        addl      $-16, %esp
        fldt      12(%ebp)
        fstpt     (%esp)
        call      acosl@PLT
..B1.75:
        addl      $16, %esp
        lea       256(%esp), %edx
..B1.25:
        movzbl    33(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmull     ones@GOTOFF(%ebx,%eax,8)
        fstpt     (%edx)
        jmp       ..B1.40
..B1.27:
        movl      16(%ebp), %edi
..B1.28:
        testl     %edi, %edi
        jne       ..B1.34
..B1.29:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $0, 12(%ebp)
        jne       ..B1.35
..B1.30:
        cmpl      $32767, %edx
        jl        ..B1.33
..B1.31:
        cmpl      $-2147483648, %ecx
        jne       ..B1.35
..B1.32:
        cmpl      $0, 24(%ebp)
        jne       ..B1.35
..B1.33:
        fldt      24(%ebp)
        movzbl    33(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fstpt     (%esp)
        fldt      (%esp)
        addl      $-16, %esp
        fldl      ones@GOTOFF(%ebx,%eax,8)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      asinhl@PLT
..B1.77:
        fldt      12(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     244(%esp)
        addl      $-32, %esp
        fldt      32(%esp)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
        jmp       ..B1.78
..B1.34:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
..B1.35:
        cmpl      $32767, %eax
        je        ..B1.57
..B1.36:
        cmpl      $32767, %edx
        je        ..B1.53
..B1.80:
        cmpl      $32767, %eax
..B1.37:
        je        ..B1.50
..B1.38:
        fldt      24(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     244(%esp)
        fldt      12(%ebp)
        fxch      %st(1)
..B1.39:
        addl      $-32, %esp
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B1.78:
        addl      $32, %esp
        fstpt     256(%esp)
..B1.40:
        testl     %esi, %esi
        je        ..B1.42
..B1.41:
        fldcw     18(%esp)
..B1.42:
        movl      12(%esp), %eax
        movl      244(%esp), %edx
        movl      248(%esp), %ecx
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      252(%esp), %esi
        movl      256(%esp), %edi
        movl      260(%esp), %edx
        movl      264(%esp), %ecx
        movl      %esi, 8(%eax)
        movl      %edi, 12(%eax)
        movl      %edx, 16(%eax)
        movl      %ecx, 20(%eax)
        movl      268(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.49
..B1.43:
        movl      12(%esp), %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.44:
        movl      28(%ebp), %ecx
        testl     %ecx, %ecx
        jne       ..B1.11
..B1.45:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
        jmp       ..B1.18
..B1.46:
        cmpl      $0, 16(%ebp)
        jne       ..B1.9
..B1.47:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
        jmp       ..B1.16
..B1.49:
        call      __stack_chk_fail@PLT
..B1.50:
        cmpl      $-2147483648, %edi
        jne       ..B1.38
..B1.51:
        cmpl      $0, 12(%ebp)
        jne       ..B1.38
..B1.52:
        fldt      12(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     244(%esp)
        fldt      24(%ebp)
        jmp       ..B1.39
..B1.53:
        cmpl      $-2147483648, %ecx
        jne       ..B1.55
..B1.54:
        cmpl      $0, 24(%ebp)
        je        ..B1.79
..B1.55:
        cmpl      $32767, %eax
        jge       ..B1.37
..B1.56:
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     244(%esp)
        fldt      244(%esp)
        fstpt     256(%esp)
        jmp       ..B1.40
..B1.57:
        cmpl      $-2147483648, %edi
        jne       ..B1.59
..B1.58:
        cmpl      $0, 12(%ebp)
        je        ..B1.61
..B1.59:
        cmpl      $32767, %edx
        je        ..B1.64
..B1.60:
        cmpl      $-2147483648, %edi
        jne       ..B1.62
..B1.82:
        cmpl      $0, 12(%ebp)
..B1.61:
        je        ..B1.36
..B1.62:
        cmpl      $32767, %edx
        jge       ..B1.36
..B1.63:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     244(%esp)
        fldt      244(%esp)
        fstpt     256(%esp)
        jmp       ..B1.40
..B1.64:
        cmpl      $-2147483648, %ecx
        jne       ..B1.66
..B1.65:
        cmpl      $0, 24(%ebp)
        je        ..B1.60
..B1.66:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     244(%esp)
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     256(%esp)
        jmp       ..B1.40
..B1.67:
        xorl      %esi, %esi
        jmp       ..B1.7
..B1.79:
        cmpl      $32767, %eax
        je        ..B1.50
        jmp       ..B1.38
        .align    16,0x90
	.type	cacoshl,@function
	.size	cacoshl,.-cacoshl
	.data
# -- End  cacoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.data
	.hidden __libm_recacosl_k80
	.hidden __libm_recacoshl_k80
	.hidden __libm_carctrigprepare_k80
	.section .note.GNU-stack, ""
# End
