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
	.file "casinhl.c"
	.text
..TXTST0:
# -- Begin  casinhl
	.text
       .align    16,0x90
	.globl casinhl
casinhl:
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
        movl      %edx, 272(%esp)
        fstpt     8(%esp)
        fldt      8(%esp)
        fstpt     248(%esp)
        fldt      248(%esp)
        fstpt     260(%esp)
..B1.2:
        fnstcw    22(%esp)
..B1.3:
        movzwl    22(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.62
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 20(%esp)
..B1.5:
        fldcw     20(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.16
..B1.8:
        testl     %ecx, %ecx
        jle       ..B1.41
..B1.9:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.16
..B1.10:
        testl     %edx, %edx
        jle       ..B1.39
..B1.11:
        addl      $-48, %esp
        lea       24(%ebp), %edx
        lea       12(%ebp), %ecx
        movl      %edx, (%esp)
        lea       268(%esp), %edx
        movl      %ecx, 4(%esp)
        movl      %edx, 8(%esp)
        lea       240(%esp), %edx
        movl      %edx, 12(%esp)
        lea       212(%esp), %edx
        movl      %edx, 16(%esp)
        lea       184(%esp), %edx
        movl      %edx, 20(%esp)
        lea       156(%esp), %edx
        movl      %edx, 24(%esp)
        lea       128(%esp), %edx
        movl      %edx, 28(%esp)
        lea       100(%esp), %edx
        movl      %edx, 32(%esp)
        lea       72(%esp), %edx
        movl      %edx, 36(%esp)
        call      __libm_carctrigprepare_k80
..B1.65:
        lea       72(%esp), %edx
        lea       12(%ebp), %ecx
..B1.12:
        lea       24(%ebp), %eax
        movl      %eax, (%esp)
        lea       268(%esp), %eax
        movl      %ecx, 4(%esp)
        movl      %eax, 8(%esp)
        lea       240(%esp), %eax
        movl      %eax, 12(%esp)
        lea       212(%esp), %eax
        movl      %eax, 16(%esp)
        lea       184(%esp), %eax
        movl      %eax, 20(%esp)
        lea       156(%esp), %eax
        movl      %eax, 24(%esp)
        lea       128(%esp), %eax
        movl      %eax, 28(%esp)
        lea       100(%esp), %eax
        movl      %eax, 32(%esp)
        movl      %edx, 36(%esp)
        call      __libm_recacoshl_k80
..B1.66:
        lea       72(%esp), %edx
        addl      $48, %esp
        lea       12(%ebp), %ecx
..B1.13:
        movzbl    9(%ecx), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fldl      ones@GOTOFF(%ebx,%eax,8)
        lea       24(%ebp), %eax
        fmulp     %st, %st(1)
        fstpt     248(%esp)
        addl      $-48, %esp
        movl      %eax, (%esp)
        movl      %ecx, 4(%esp)
        lea       268(%esp), %ecx
        movl      %ecx, 8(%esp)
        lea       240(%esp), %ecx
        movl      %ecx, 12(%esp)
        lea       212(%esp), %ecx
        movl      %ecx, 16(%esp)
        lea       184(%esp), %ecx
        movl      %ecx, 20(%esp)
        lea       156(%esp), %ecx
        movl      %ecx, 24(%esp)
        lea       128(%esp), %ecx
        movl      %ecx, 28(%esp)
        lea       100(%esp), %ecx
        movl      %ecx, 32(%esp)
        movl      %edx, 36(%esp)
        call      __libm_recasinl_k80
..B1.67:
        addl      $48, %esp
..B1.14:
        fstpt     260(%esp)
        jmp       ..B1.35
..B1.16:
        movl      28(%ebp), %edx
..B1.17:
        testl     %edx, %edx
        jne       ..B1.20
..B1.75:
        cmpl      $0, 24(%ebp)
..B1.18:
        jne       ..B1.20
..B1.19:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      asinhl@PLT
..B1.68:
        fldt      24(%ebp)
        addl      $16, %esp
        fstpt     260(%esp)
        fstpt     248(%esp)
        jmp       ..B1.35
..B1.20:
        movl      16(%ebp), %eax
        testl     %eax, %eax
        movl      %eax, (%esp)
        jne       ..B1.29
..B1.21:
        movzwl    32(%ebp), %eax
        andl      $32767, %eax
        cmpl      $0, 12(%ebp)
        jne       ..B1.30
..B1.22:
        cmpl      $32767, %eax
        jl        ..B1.25
..B1.23:
        cmpl      $-2147483648, %edx
        jne       ..B1.30
..B1.24:
        cmpl      $0, 24(%ebp)
        jne       ..B1.30
..B1.25:
        fldt      24(%ebp)
        cmpl      $16383, %eax
        jl        ..B1.27
..B1.26:
        movzbl    33(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      ones@GOTOFF(%ebx,%edx,8)
        fstl      (%esp)
        addl      $-16, %esp
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      acoshl@PLT
..B1.70:
        fstpt     24(%esp)
        fldl      16(%esp)
        fstpt     (%esp)
        call      asinl@PLT
        jmp       ..B1.71
..B1.27:
        addl      $-16, %esp
        fstpt     (%esp)
        call      asinl@PLT
..B1.71:
        addl      $16, %esp
        fstpt     260(%esp)
..B1.28:
        fldt      8(%esp)
        movzbl    21(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fmull     ones@GOTOFF(%ebx,%edx,8)
        fstpt     248(%esp)
        jmp       ..B1.35
..B1.29:
        movzwl    32(%ebp), %eax
        andl      $32767, %eax
..B1.30:
        cmpl      $32767, %ecx
        je        ..B1.52
..B1.31:
        cmpl      $32767, %eax
        je        ..B1.48
..B1.74:
        cmpl      $32767, %ecx
..B1.32:
        je        ..B1.45
..B1.33:
        fldt      24(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      12(%ebp)
        fxch      %st(2)
..B1.34:
        movzbl    21(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fldl      ones@GOTOFF(%ebx,%edx,8)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     248(%esp)
        addl      $-32, %esp
        fxch      %st(2)
        fmulp     %st, %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B1.72:
        addl      $32, %esp
        fstpt     260(%esp)
..B1.35:
        testl     %edi, %edi
        je        ..B1.37
..B1.36:
        fldcw     22(%esp)
..B1.37:
        movl      248(%esp), %edx
        movl      %edx, (%esi)
        movl      260(%esp), %edx
        movl      %edx, 12(%esi)
        movl      264(%esp), %edx
        movl      %edx, 16(%esi)
        movl      252(%esp), %ecx
        movl      256(%esp), %edi
        movl      268(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      272(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.44
..B1.38:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.39:
        movl      28(%ebp), %edx
        testl     %edx, %edx
        jne       ..B1.11
..B1.40:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
        jmp       ..B1.18
..B1.41:
        cmpl      $0, 16(%ebp)
        jne       ..B1.9
..B1.42:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
        jmp       ..B1.16
..B1.44:
        call      __stack_chk_fail@PLT
..B1.45:
        cmpl      $-2147483648, (%esp)
        jne       ..B1.33
..B1.46:
        cmpl      $0, 12(%ebp)
        jne       ..B1.33
..B1.47:
        fldt      12(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fldt      24(%ebp)
        jmp       ..B1.34
..B1.48:
        cmpl      $-2147483648, %edx
        jne       ..B1.50
..B1.49:
        cmpl      $0, 24(%ebp)
        je        ..B1.73
..B1.50:
        cmpl      $32767, %ecx
        jge       ..B1.32
..B1.51:
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     248(%esp)
        fldt      248(%esp)
        fstpt     260(%esp)
        jmp       ..B1.35
..B1.52:
        cmpl      $-2147483648, (%esp)
        jne       ..B1.54
..B1.53:
        cmpl      $0, 12(%ebp)
        je        ..B1.56
..B1.54:
        cmpl      $32767, %eax
        je        ..B1.59
..B1.55:
        cmpl      $-2147483648, (%esp)
        jne       ..B1.57
..B1.76:
        cmpl      $0, 12(%ebp)
..B1.56:
        je        ..B1.31
..B1.57:
        cmpl      $32767, %eax
        jge       ..B1.31
..B1.58:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     248(%esp)
        fldt      248(%esp)
        fstpt     260(%esp)
        jmp       ..B1.35
..B1.59:
        cmpl      $-2147483648, %edx
        jne       ..B1.61
..B1.60:
        cmpl      $0, 24(%ebp)
        je        ..B1.55
..B1.61:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     248(%esp)
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     260(%esp)
        jmp       ..B1.35
..B1.62:
        xorl      %edi, %edi
        jmp       ..B1.7
..B1.73:
        cmpl      $32767, %ecx
        je        ..B1.45
        jmp       ..B1.33
        .align    16,0x90
	.type	casinhl,@function
	.size	casinhl,.-casinhl
	.data
# -- End  casinhl
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
	.hidden __libm_recasinl_k80
	.hidden __libm_recacoshl_k80
	.hidden __libm_carctrigprepare_k80
	.section .note.GNU-stack, ""
# End
