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
	.file "remquo.c"
	.text
..TXTST0:
# -- Begin  remquo
	.text
       .align    16,0x90
	.globl remquo
remquo:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
# parameter 3: 24 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $84, %esp
        movl      24(%ebp), %ecx
        movl      %ecx, 56(%esp)
        movl      12(%ebp), %ecx
        movl      %ecx, %edx
        movl      20(%ebp), %esi
        movl      %esi, %eax
        andl      $-2147483648, %edx
        andl      $-2147483648, %eax
        cmpl      %edx, %eax
        movl      $-1, %eax
        movl      %edx, 48(%esp)
        jne       ..L2
        movl      $1, %eax
..L2:
        andl      $2147483647, %ecx
        andl      $2147483647, %esi
        movl      %ecx, %edi
        movl      %esi, %ebx
        andl      $2146435072, %edi
        andl      $2146435072, %ebx
        movl      %eax, 52(%esp)
        shrl      $20, %edi
        movl      8(%ebp), %eax
        movl      16(%ebp), %edx
        shrl      $20, %ebx
        movl      %eax, 8(%esp)
        cmpl      $2047, %edi
        movl      %ecx, 12(%esp)
        movl      %edx, 68(%esp)
        movl      %edx, 40(%esp)
        movl      %esi, 44(%esp)
        movl      %edi, 60(%esp)
        movl      %ebx, 64(%esp)
        je        ..B1.5
..B1.2:
        cmpl      $2047, 64(%esp)
        je        ..B1.5
..B1.3:
        movl      %eax, %edx
        orl       %ecx, %edx
        je        ..B1.5
..B1.4:
        movl      68(%esp), %edx
        orl       %esi, %edx
        jne       ..B1.6
..B1.5:
        movl      $1, %edi
        jmp       ..B1.7
..B1.6:
        xorl      %edi, %edi
..B1.7:
        fldl      8(%esp)
        movl      %ecx, %ebx
        fldl      40(%esp)
        movl      %esi, %edx
        andl      $1048575, %ebx
        andl      $1048575, %edx
        movl      %edx, 16(%esp)
        testl     %edi, %edi
        je        ..B1.16
..B1.8:
        fstp      %st(1)
        fstp      %st(0)
        movl      56(%esp), %edx
        movl      %ecx, %ebx
        movl      $0, (%edx)
        movl      $2146435072, %edx
        subl      %edx, %ebx
        jb        ..B1.9
..B1.86:
        orl       %eax, %ebx
        jne       ..B1.10
..B1.9:
        movl      %esi, %ebx
        subl      %edx, %ebx
        jb        ..B1.11
..B1.87:
        orl       68(%esp), %ebx
        je        ..B1.11
..B1.10:
        fldl      8(%ebp)
        faddl     16(%ebp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        movl      68(%esp), %ebx
        orl       %esi, %ebx
        je        ..B1.13
..B1.12:
        subl      %edx, %ecx
        orl       %ecx, %eax
        jne       ..B1.14
..B1.13:
        fldz      
        fdiv      %st(0), %st
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.14:
        fldl      8(%ebp)
..B1.15:
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        movl      68(%esp), %edx
        movl      %edx, 32(%esp)
        movl      60(%esp), %edx
        subl      64(%esp), %edx
        movl      16(%esp), %edi
        cmpl      $31, %edx
        movl      %edi, 36(%esp)
        fxch      %st(1)
        fstpl     24(%esp)
        jle       ..B1.21
..B1.17:
        cmpl      $2016, 64(%esp)
        jge       ..B1.21
..B1.18:
        movl      %eax, 8(%esp)
        call      ..L3
..L3:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L3](%eax), %eax
        fstl      16(%esp)
        movl      %ebx, 12(%esp)
        fldl      .L_2il0floatpacket.0@GOTOFF(%eax)
        fmulp     %st, %st(1)
        fstpl     (%esp)
..B1.19:
        fldl      (%esp)
        fldl      24(%esp)
.L_2TAG_PACKET_0.0.2:
        fprem     
        fstsw     %ax
        sahf      
        jp        .L_2TAG_PACKET_0.0.2
        fstp      %st(1)
        fstpl     24(%esp)
..B1.20:
        fldl      16(%esp)
        movl      28(%esp), %ecx
        movl      %ecx, %ebx
        andl      $2146435072, %ebx
        shrl      $20, %ebx
        movl      40(%esp), %edx
        movl      %ebx, 60(%esp)
        movl      %ecx, %ebx
        movl      24(%esp), %eax
        andl      $1048575, %ebx
        movl      44(%esp), %esi
        movl      %edx, 68(%esp)
..B1.21:
        movl      %eax, %edi
        movl      %ecx, %edx
        subl      68(%esp), %edi
        sbbl      %esi, %edx
        movl      %edx, 16(%esp)
        jb        ..B1.22
..B1.88:
        orl       16(%esp), %edi
        jne       ..B1.34
..B1.22:
        subl      68(%esp), %eax
        subl      %esi, %ecx
        orl       %ecx, %eax
        je        ..B1.33
..B1.23:
        fldl      24(%esp)
        cmpl      $2046, 60(%esp)
        je        ..B1.29
..B1.24:
        fld       %st(0)
        fadd      %st(1), %st
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        ja        ..B1.29
        jp        ..B1.29
..B1.25:
        fstp      %st(1)
        movl      56(%esp), %eax
        cmpl      $0, 48(%esp)
        movl      $0, (%eax)
        je        ..B1.28
..B1.26:
        fchs      
..B1.28:
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        movl      56(%esp), %edx
        movl      52(%esp), %eax
        movl      %eax, (%edx)
..B1.30:
        cmpl      $0, 48(%esp)
        fsubp     %st, %st(1)
        je        ..B1.32
..B1.31:
        fchs      
..B1.32:
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.33:
        fstp      %st(0)
        fldz      
        fmull     8(%ebp)
        movl      56(%esp), %edx
        movl      52(%esp), %eax
        movl      %eax, (%edx)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.34:
        cmpl      $0, 60(%esp)
        jne       ..B1.43
..B1.35:
        movl      %eax, %edx
        orl       %ebx, %edx
        je        ..B1.43
..B1.36:
        movl      %eax, 8(%esp)
        testl     %ebx, %ebx
        movl      %ebx, 12(%esp)
        je        ..B1.38
..B1.37:
        bsr       %ebx, %edx
        negl      %edx
        addl      $20, %edx
        movl      %edx, 60(%esp)
        jmp       ..B1.39
..B1.38:
        bsr       8(%esp), %edx
        negl      %edx
        addl      $52, %edx
        movl      %edx, 60(%esp)
..B1.39:
        cmpl      $31, 60(%esp)
        jbe       ..B1.41
..B1.40:
        movl      %eax, %ebx
        xorl      %esi, %esi
        movl      %edx, %ecx
        shll      %cl, %ebx
..B1.42:
        movl      %edx, %eax
        negl      %eax
        incl      %eax
        movl      %eax, 60(%esp)
        jmp       ..B1.44
..B1.41:
        movl      %eax, %esi
        movl      %edx, %ecx
        shldl     %cl, %eax, %ebx
        shll      %cl, %esi
        jmp       ..B1.42
..B1.43:
        movl      %eax, %esi
        orl       $1048576, %ebx
..B1.44:
        movl      36(%esp), %edx
        movl      32(%esp), %eax
        movl      %edx, 20(%esp)
        cmpl      $0, 64(%esp)
        jne       ..B1.53
..B1.45:
        movl      %eax, %edx
        orl       20(%esp), %edx
        je        ..B1.53
..B1.46:
        cmpl      $0, 20(%esp)
        je        ..B1.48
..B1.47:
        bsr       20(%esp), %edx
        negl      %edx
        addl      $20, %edx
        movl      %edx, 64(%esp)
        jmp       ..B1.49
..B1.48:
        bsr       32(%esp), %edx
        negl      %edx
        addl      $52, %edx
        movl      %edx, 64(%esp)
..B1.49:
        cmpl      $31, 64(%esp)
        jbe       ..B1.51
..B1.50:
        movl      %edx, %ecx
        shll      %cl, %eax
        movl      $0, 16(%esp)
        movl      %eax, 20(%esp)
..B1.52:
        movl      %ecx, %eax
        negl      %eax
        incl      %eax
        movl      %eax, 64(%esp)
        jmp       ..B1.54
..B1.51:
        movl      %edx, %ecx
        movl      20(%esp), %edx
        shldl     %cl, %eax, %edx
        shll      %cl, %eax
        movl      %edx, 20(%esp)
        movl      %eax, 16(%esp)
        jmp       ..B1.52
..B1.53:
        movl      %eax, 16(%esp)
        orl       $1048576, 20(%esp)
..B1.54:
        movl      60(%esp), %eax
        xorl      %edx, %edx
        subl      64(%esp), %eax
        xorl      %ecx, %ecx
        movl      %eax, 60(%esp)
        cmpl      $-1, %eax
        je        ..B1.60
..B1.56:
        movl      %edx, %edi
        addl      %ecx, %ecx
        shrl      $31, %edi
        addl      %edx, %edx
        orl       %edi, %ecx
        movl      %esi, %edi
        subl      16(%esp), %edi
        movl      %ebx, %edi
        sbbl      20(%esp), %edi
        jb        ..B1.58
..B1.57:
        subl      16(%esp), %esi
        sbbl      20(%esp), %ebx
        addl      $1, %edx
        adcl      $0, %ecx
..B1.58:
        movl      %esi, %edi
        addl      %ebx, %ebx
        shrl      $31, %edi
        decl      %eax
        addl      %esi, %esi
        orl       %edi, %ebx
        cmpl      $-1, %eax
        jne       ..B1.56
..B1.60:
        movl      %esi, %eax
        orl       %ebx, %eax
        je        ..B1.83
..B1.61:
        movl      %ebx, %eax
        shll      $31, %eax
        shrl      $1, %esi
        shrl      $1, %ebx
        orl       %esi, %eax
        testl     %ebx, %ebx
        movl      %eax, 8(%esp)
        movl      %ebx, 12(%esp)
        je        ..B1.63
..B1.62:
        bsr       %ebx, %ecx
        negl      %ecx
        addl      $20, %ecx
        jmp       ..B1.64
..B1.63:
        bsr       8(%esp), %ecx
        negl      %ecx
        addl      $52, %ecx
..B1.64:
        cmpl      $31, %ecx
        jbe       ..B1.66
..B1.65:
        movl      %eax, %ebx
        xorl      %eax, %eax
        shll      %cl, %ebx
        jmp       ..B1.67
..B1.66:
        shldl     %cl, %eax, %ebx
        shll      %cl, %eax
..B1.67:
        movl      64(%esp), %esi
        subl      %ecx, %esi
        movl      %esi, 64(%esp)
        testl     %esi, %esi
        jle       ..B1.69
..B1.68:
        movl      %ebx, %esi
        andl      $1048575, %esi
        jmp       ..B1.73
..B1.69:
        movl      %esi, %ecx
        negl      %ecx
        incl      %ecx
        movl      %ecx, 64(%esp)
        cmpl      $31, %ecx
        jbe       ..B1.71
..B1.70:
        movl      %ebx, %eax
        xorl      %esi, %esi
        shrl      %cl, %eax
        jmp       ..B1.72
..B1.71:
        movl      %ebx, %esi
        shrdl     %cl, %ebx, %eax
        shrl      %cl, %esi
..B1.72:
        movl      $0, 64(%esp)
..B1.73:
        movl      64(%esp), %ecx
        shll      $20, %ecx
        orl       %ecx, %esi
        movl      %eax, 8(%esp)
        movl      %esi, 12(%esp)
        fldl      8(%esp)
        fld       %st(0)
        fadd      %st(1), %st
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jb        ..B1.89
..B1.74:
        fucomp    %st(2)
        fnstsw    %ax
        sahf      
        jp        ..B1.75
        je        ..B1.81
..B1.75:
        incl      %edx
        fsubp     %st, %st(1)
        jmp       ..B1.76
..B1.89:
        fstp      %st(2)
        fstp      %st(1)
..B1.76:
        andl      $2147483647, %edx
        movl      52(%esp), %eax
        imull     %edx, %eax
        movl      56(%esp), %edx
        cmpl      $0, 48(%esp)
        movl      %eax, (%edx)
        je        ..B1.78
..B1.77:
        fchs      
..B1.78:
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.81:
        fstp      %st(1)
        movl      %edx, %ecx
        xorl      %eax, %eax
        andl      $1, %ecx
        orl       %eax, %ecx
        je        ..B1.76
..B1.82:
        fchs      
        incl      %edx
        jmp       ..B1.76
..B1.83:
        fstp      %st(0)
        fldz      
        andl      $2147483647, %edx
        fmull     8(%ebp)
        movl      52(%esp), %eax
        imull     %edx, %eax
        movl      56(%esp), %edx
        movl      %eax, (%edx)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	remquo,@function
	.size	remquo,.-remquo
	.data
# -- End  remquo
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xc1e00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
# End
