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
	.file "fma.c"
	.text
..TXTST0:
# -- Begin  fma
	.text
       .align    16,0x90
	.globl fma
fma:
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
..B1.2:
        fnstcw    80(%esp)
..B1.3:
        movl      12(%ebp), %esi
        movl      16(%ebp), %ecx
        movl      24(%ebp), %eax
        movzwl    80(%esp), %edx
        movl      %esi, 60(%esp)
        andl      $2147483647, %esi
        andl      $3072, %edx
        movl      %edx, 56(%esp)
        movl      20(%ebp), %edx
        movl      %edx, %edi
        movl      %ecx, 76(%esp)
        andl      $2147483647, %edi
        movl      28(%ebp), %ecx
        movl      %eax, 72(%esp)
        movl      %esi, %eax
        movl      8(%ebp), %ebx
        movl      %ecx, 68(%esp)
        andl      $2147483647, %ecx
        orl       %ebx, %eax
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        movl      %eax, 64(%esp)
        jne       ..B1.4
..B1.130:
        cmpl      $2146435072, %esi
        ja        ..B1.8
        jmp       ..B1.6
..B1.4:
        cmpl      $2146435072, %esi
        jb        ..B1.9
..B1.5:
        ja        ..B1.8
..B1.6:
        jne       ..B1.134
..B1.7:
        testl     %ebx, %ebx
        jne       ..B1.8
..B1.133:
        cmpl      $2146435072, %edi
        ja        ..B1.15
        jmp       ..B1.13
..B1.8:
        fldl      8(%ebp)
        faddl     16(%ebp)
        fmull     24(%ebp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        lea       -1072693248(%esi), %eax
        orl       %ebx, %eax
        je        ..B1.133
..B1.10:
        movl      %edi, %eax
        orl       76(%esp), %eax
        je        ..B1.133
..B1.11:
        cmpl      $2146435072, %edi
        jae       ..B1.12
        jmp       ..B1.16
..B1.134:
        cmpl      $2146435072, %edi
..B1.12:
        ja        ..B1.15
..B1.13:
        jne       ..B1.138
..B1.14:
        cmpl      $0, 76(%esp)
        jne       ..B1.15
..B1.137:
        cmpl      $2146435072, %ecx
        ja        ..B1.22
        jmp       ..B1.20
..B1.15:
        fldl      16(%ebp)
        faddl     24(%ebp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.16:
        lea       -1072693248(%edi), %eax
        orl       76(%esp), %eax
        je        ..B1.137
..B1.17:
        movl      %ecx, %eax
        orl       72(%esp), %eax
        je        ..B1.137
..B1.18:
        cmpl      $2146435072, %ecx
        jae       ..B1.19
        jmp       ..B1.28
..B1.138:
        cmpl      $2146435072, %ecx
..B1.19:
        ja        ..B1.22
..B1.20:
        jne       ..B1.23
..B1.21:
        cmpl      $0, 72(%esp)
        je        ..B1.23
..B1.22:
        fldl      24(%ebp)
        movl      64(%esp), %eax
        fmull     _ones@GOTOFF(%eax)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        cmpl      $2146435072, %esi
        jae       ..B1.27
..B1.24:
        cmpl      $2146435072, %edi
        jae       ..B1.27
..B1.25:
        cmpl      $2146435072, %ecx
        jb        ..B1.27
..B1.26:
        fldl      24(%ebp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.27:
        fldl      8(%ebp)
        fmull     16(%ebp)
        faddl     24(%ebp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.28:
        cmpl      $1048576, %esi
        jae       ..B1.30
..B1.29:
        movl      60(%esp), %ebx
        movl      %ebx, %eax
        shrl      $31, %eax
        orl       $1072693248, %ebx
        movl      64(%esp), %esi
        movl      %ebx, 12(%ebp)
        fldl      8(%ebp)
        fsubl     _ones@GOTOFF(%esi,%eax,8)
        fstpl     8(%ebp)
        movl      12(%ebp), %esi
        movl      %esi, 60(%esp)
        andl      $2147483647, %esi
        movl      %esi, %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      8(%ebp), %ebx
        movl      %eax, 4(%esp)
        jmp       ..B1.31
..B1.30:
        movl      %esi, %eax
        shrl      $20, %eax
        movl      %eax, 4(%esp)
..B1.31:
        cmpl      $1048576, %edi
        jae       ..B1.33
..B1.32:
        movl      %edx, %edi
        orl       $1072693248, %edx
        shrl      $31, %edi
        movl      %edx, 20(%ebp)
        fldl      16(%ebp)
        movl      64(%esp), %edx
        fsubl     _ones@GOTOFF(%edx,%edi,8)
        fstpl     16(%ebp)
        movl      20(%ebp), %edx
        movl      %edx, %edi
        movl      16(%ebp), %eax
        andl      $2147483647, %edi
        movl      %eax, 76(%esp)
        movl      %edi, %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      %eax, 16(%esp)
        jmp       ..B1.34
..B1.33:
        movl      %edi, %eax
        shrl      $20, %eax
        movl      %eax, 16(%esp)
..B1.34:
        cmpl      $1048576, %ecx
        jae       ..B1.36
..B1.35:
        movl      68(%esp), %eax
        movl      %eax, %ecx
        shrl      $31, %ecx
        orl       $1072693248, %eax
        movl      %eax, 28(%ebp)
        fldl      24(%ebp)
        movl      64(%esp), %eax
        fsubl     _ones@GOTOFF(%eax,%ecx,8)
        fstpl     24(%ebp)
        movl      28(%ebp), %ecx
        movl      24(%ebp), %eax
        movl      %ecx, 68(%esp)
        andl      $2147483647, %ecx
        movl      %eax, 72(%esp)
        movl      %ecx, %eax
        shrl      $20, %eax
        addl      $-1022, %eax
        movl      %eax, (%esp)
        jmp       ..B1.37
..B1.36:
        movl      %ecx, %eax
        shrl      $20, %eax
        movl      %eax, (%esp)
..B1.37:
        andl      $1048575, %ecx
        movl      %ebx, %eax
        orl       $1048576, %ecx
        andl      $1048575, %esi
        movl      %ecx, 20(%esp)
        orl       $1048576, %esi
        movl      60(%esp), %ecx
        andl      $1048575, %edi
        xorl      %edx, %ecx
        orl       $1048576, %edi
        andl      $-2147483648, %ecx
        movl      %ecx, 60(%esp)
        movl      4(%esp), %edx
        movl      16(%esp), %ecx
        lea       -1023(%edx,%ecx), %edx
        movl      76(%esp), %ecx
        movl      %edx, 32(%esp)
        mull      %ecx
        movl      %eax, 44(%esp)
        movl      %ecx, %eax
        movl      %edx, 24(%esp)
        mull      %esi
        movl      %eax, %ecx
        movl      %ebx, %eax
        movl      %edx, 28(%esp)
        mull      %edi
        movl      %eax, 52(%esp)
        movl      %esi, %eax
        movl      %edx, %ebx
        xorl      %esi, %esi
        mull      %edi
        addl      52(%esp), %ecx
        movl      28(%esp), %edi
        adcl      $0, %esi
        addl      24(%esp), %ecx
        movl      %ecx, 48(%esp)
        adcl      $0, %esi
        addl      %eax, %esi
        movl      $0, %eax
        movl      20(%esp), %ecx
        adcl      $0, %eax
        addl      %ebx, %edi
        movl      $0, %ebx
        adcl      $0, %ebx
        addl      %edi, %esi
        movl      %esi, 40(%esp)
        adcl      %ebx, %eax
        addl      %eax, %edx
        movl      %edx, 36(%esp)
        testl     $512, %edx
        je        ..B1.39
..B1.38:
        movl      %esi, %eax
        shll      $11, %edx
        shrl      $21, %eax
        orl       %eax, %edx
        movl      %edx, 36(%esp)
        movl      48(%esp), %eax
        movl      %eax, %ebx
        movl      44(%esp), %edx
        movl      %edx, %edi
        shll      $11, %esi
        shrl      $21, %ebx
        shll      $11, %eax
        orl       %ebx, %esi
        shrl      $21, %edi
        shll      $11, %edx
        orl       %edi, %eax
        incl      32(%esp)
        movl      %esi, 40(%esp)
        movl      %eax, 48(%esp)
        movl      %edx, 44(%esp)
        jmp       ..B1.40
..B1.39:
        movl      %esi, %eax
        shll      $12, %edx
        shrl      $20, %eax
        orl       %eax, %edx
        movl      %edx, 36(%esp)
        movl      48(%esp), %eax
        movl      %eax, %ebx
        movl      44(%esp), %edx
        movl      %edx, %edi
        shll      $12, %esi
        shrl      $20, %ebx
        shll      $12, %eax
        orl       %ebx, %esi
        shrl      $20, %edi
        shll      $12, %edx
        orl       %edi, %eax
        movl      %esi, 40(%esp)
        movl      %eax, 48(%esp)
        movl      %edx, 44(%esp)
..B1.40:
        movl      32(%esp), %eax
        cmpl      (%esp), %eax
        jg        ..B1.45
..B1.41:
        cmpl      (%esp), %eax
        jne       ..B1.46
..B1.42:
        cmpl      36(%esp), %ecx
        jb        ..B1.45
..B1.43:
        jne       ..B1.46
..B1.44:
        movl      72(%esp), %eax
        cmpl      40(%esp), %eax
        ja        ..B1.46
..B1.45:
        movl      72(%esp), %eax
        movl      32(%esp), %edx
        movl      68(%esp), %ebx
        movl      %eax, 24(%esp)
        andl      $-2147483648, %ebx
        xorl      %eax, %eax
        subl      (%esp), %edx
        movl      %ecx, 20(%esp)
        movl      %ebx, 16(%esp)
        movl      %eax, 28(%esp)
        jmp       ..B1.47
..B1.46:
        movl      36(%esp), %ebx
        movl      %ebx, 20(%esp)
        movl      72(%esp), %ebx
        movl      %ebx, 40(%esp)
        movl      (%esp), %eax
        movl      %eax, %edx
        movl      60(%esp), %ebx
        movl      48(%esp), %edi
        movl      %ebx, 16(%esp)
        subl      32(%esp), %edx
        movl      68(%esp), %ebx
        movl      %eax, 32(%esp)
        andl      $-2147483648, %ebx
        movl      44(%esp), %eax
        movl      %ebx, 60(%esp)
        xorl      %ebx, %ebx
        movl      %esi, 24(%esp)
        movl      %edi, 28(%esp)
        movl      %ecx, 36(%esp)
        movl      %ebx, 44(%esp)
        movl      %ebx, 48(%esp)
..B1.47:
        testl     %edx, %edx
        je        ..B1.57
..B1.48:
        cmpl      $32, %edx
        jge       ..B1.50
..B1.49:
        movl      %edx, %esi
        movl      %edx, %ecx
        negl      %esi
        movl      %eax, %ebx
        movl      %esi, (%esp)
        movl      $-1, %esi
        shrl      %cl, %esi
        movl      (%esp), %ecx
        notl      %esi
        shll      %cl, %ebx
        movl      28(%esp), %edi
        andl      %esi, %ebx
        movl      %ebx, 52(%esp)
        movl      %edi, %ebx
        shll      %cl, %ebx
        movl      %edx, %ecx
        shrl      %cl, %eax
        andl      %esi, %ebx
        orl       %ebx, %eax
        movl      %eax, 4(%esp)
        movl      24(%esp), %eax
        movl      %eax, %ebx
        movl      (%esp), %ecx
        shll      %cl, %ebx
        movl      %edx, %ecx
        shrl      %cl, %edi
        andl      %esi, %ebx
        orl       %ebx, %edi
        movl      20(%esp), %ebx
        movl      %edi, 28(%esp)
        movl      %ebx, %edi
        movl      (%esp), %ecx
        shll      %cl, %edi
        movl      %edx, %ecx
        shrl      %cl, %eax
        andl      %esi, %edi
        orl       %edi, %eax
        movl      %eax, 24(%esp)
        shrl      %cl, %ebx
        movl      %ebx, 20(%esp)
        movl      4(%esp), %eax
        jmp       ..B1.58
..B1.50:
        cmpl      $64, %edx
        jge       ..B1.52
..B1.51:
        movl      %edx, %esi
        movl      %edx, %ecx
        movl      $-1, %edi
        negl      %esi
        movl      %esi, (%esp)
        testl     %eax, %eax
        movl      28(%esp), %ebx
        movl      $0, %eax
        setne     %al
        shrl      %cl, %edi
        movl      (%esp), %ecx
        notl      %edi
        shll      %cl, %ebx
        andl      %edi, %ebx
        movl      24(%esp), %esi
        orl       %eax, %ebx
        movl      %esi, %eax
        shll      %cl, %eax
        movl      %edx, %ecx
        movl      %ebx, 52(%esp)
        andl      %edi, %eax
        movl      28(%esp), %ebx
        shrl      %cl, %ebx
        movl      (%esp), %ecx
        orl       %ebx, %eax
        movl      20(%esp), %ebx
        shll      %cl, %ebx
        movl      %edx, %ecx
        shrl      %cl, %esi
        andl      %edi, %ebx
        orl       %esi, %ebx
        movl      %ebx, 28(%esp)
        movl      20(%esp), %ebx
        shrl      %cl, %ebx
        movl      %ebx, 24(%esp)
        movl      $0, 20(%esp)
        jmp       ..B1.58
..B1.52:
        cmpl      $96, %edx
        jge       ..B1.54
..B1.53:
        movl      %edx, %edi
        movl      %edx, %ecx
        movl      $-1, %esi
        negl      %edi
        movl      %edi, (%esp)
        shrl      %cl, %esi
        orl       28(%esp), %eax
        notl      %esi
        movl      (%esp), %ecx
        movl      $0, %eax
        movl      24(%esp), %ebx
        setne     %al
        shll      %cl, %ebx
        andl      %esi, %ebx
        orl       %eax, %ebx
        movl      %ebx, 52(%esp)
        movl      20(%esp), %ebx
        movl      %ebx, %eax
        shll      %cl, %eax
        movl      %edx, %ecx
        andl      %esi, %eax
        xorl      %edx, %edx
        movl      24(%esp), %esi
        shrl      %cl, %esi
        shrl      %cl, %ebx
        orl       %esi, %eax
        movl      %ebx, 28(%esp)
        movl      %edx, 24(%esp)
        movl      %edx, 20(%esp)
        jmp       ..B1.58
..B1.54:
        cmpl      $128, %edx
        jge       ..B1.56
..B1.55:
        movl      %edx, %ebx
        movl      %edx, %ecx
        negl      %ebx
        movl      $-1, %edi
        movl      20(%esp), %esi
        shrl      %cl, %edi
        movl      %ebx, %ecx
        movl      %esi, %ebx
        notl      %edi
        shll      %cl, %ebx
        movl      24(%esp), %ecx
        andl      %edi, %ebx
        orl       28(%esp), %ecx
        orl       %eax, %ecx
        movl      $0, %eax
        movl      %edx, %ecx
        setne     %al
        xorl      %edx, %edx
        orl       %eax, %ebx
        movl      %esi, %eax
        movl      %ebx, 52(%esp)
        shrl      %cl, %eax
        movl      %edx, 28(%esp)
        movl      %edx, 24(%esp)
        movl      %edx, 20(%esp)
        jmp       ..B1.58
..B1.56:
        movl      20(%esp), %edi
        orl       24(%esp), %edi
        orl       28(%esp), %edi
        orl       %eax, %edi
        movl      $0, %eax
        setne     %al
        movl      %eax, 52(%esp)
        xorl      %eax, %eax
        movl      %eax, 28(%esp)
        movl      %eax, 24(%esp)
        movl      %eax, 20(%esp)
        jmp       ..B1.58
..B1.57:
        movl      $0, 52(%esp)
..B1.58:
        movl      60(%esp), %ecx
        cmpl      16(%esp), %ecx
        jne       ..B1.62
..B1.59:
        movl      44(%esp), %esi
        addl      %esi, %eax
        xorl      %ecx, %ecx
        cmpl      %esi, %eax
        movl      48(%esp), %ebx
        setb      %cl
        xorl      %edi, %edi
        addl      28(%esp), %ebx
        adcl      $0, %edi
        addl      %ecx, %ebx
        movl      40(%esp), %ecx
        adcl      $0, %edi
        addl      24(%esp), %ecx
        movl      %ebx, 48(%esp)
        movl      $0, %ebx
        adcl      $0, %ebx
        addl      %edi, %ecx
        movl      %ecx, 40(%esp)
        movl      36(%esp), %ecx
        adcl      $0, %ebx
        addl      20(%esp), %ecx
        addl      %ebx, %ecx
        movl      %ecx, 36(%esp)
        testl     $2097152, %ecx
        je        ..B1.61
..B1.60:
        orl       48(%esp), %eax
        orl       52(%esp), %eax
        movl      40(%esp), %esi
        movl      %esi, %edx
        movl      %ecx, %ebx
        movl      $0, %eax
        movl      %ebx, %ecx
        setne     %al
        shll      $31, %edx
        shll      $31, %ecx
        orl       %eax, %edx
        shrl      $1, %esi
        shrl      $1, %ebx
        orl       %ecx, %esi
        andl      $1048575, %ebx
        incl      32(%esp)
        movl      %edx, 20(%esp)
        movl      %esi, 40(%esp)
        movl      %ebx, 36(%esp)
        jmp       ..B1.84
..B1.61:
        orl       52(%esp), %eax
        movl      $0, %eax
        setne     %al
        orl       48(%esp), %eax
        andl      $1048575, 36(%esp)
        movl      %eax, 20(%esp)
        jmp       ..B1.84
..B1.62:
        xorl      %ebx, %ebx
        negl      52(%esp)
        movl      44(%esp), %esi
        setne     %bl
        xorl      %edi, %edi
        subl      %eax, %esi
        movl      $0, %eax
        sbbl      $0, %eax
        subl      %ebx, %esi
        movl      48(%esp), %ecx
        sbbl      $0, %eax
        subl      28(%esp), %ecx
        cltd      
        sbbl      $0, %edi
        addl      %eax, %ecx
        movl      40(%esp), %ebx
        adcl      %edx, %edi
        subl      24(%esp), %ebx
        movl      %edi, %eax
        movl      %esi, 44(%esp)
        movl      $0, %esi
        cltd      
        sbbl      $0, %esi
        addl      %ebx, %edi
        movl      36(%esp), %eax
        adcl      %edx, %esi
        subl      20(%esp), %eax
        movl      %ecx, 48(%esp)
        addl      %esi, %eax
        movl      %edi, 16(%esp)
        movl      %eax, 36(%esp)
        je        ..B1.64
..B1.63:
        shll      $11, %eax
        movl      $0, 4(%esp)
        jmp       ..B1.72
..B1.64:
        cmpl      $0, 16(%esp)
        je        ..B1.66
..B1.65:
        movl      %edi, %eax
        movl      $21, 4(%esp)
        jmp       ..B1.72
..B1.66:
        cmpl      $0, 48(%esp)
        je        ..B1.68
..B1.67:
        movl      %ecx, %eax
        movl      $53, 4(%esp)
        jmp       ..B1.72
..B1.68:
        cmpl      $0, 44(%esp)
        je        ..B1.70
..B1.69:
        movl      44(%esp), %eax
        movl      $85, 4(%esp)
        jmp       ..B1.72
..B1.70:
        cmpl      $0, 52(%esp)
        je        ..B1.123
..B1.71:
        movl      52(%esp), %eax
        movl      $117, 4(%esp)
..B1.72:
        testl     $-2147483648, %eax
        jne       ..B1.76
..B1.73:
        movl      4(%esp), %edx
..B1.74:
        addl      %eax, %eax
        incl      %edx
        testl     $-2147483648, %eax
        je        ..B1.74
..B1.75:
        movl      %edx, 4(%esp)
..B1.76:
        cmpl      $32, 4(%esp)
        jge       ..B1.78
..B1.77:
        movl      4(%esp), %ebx
        movl      %ebx, %esi
        movl      44(%esp), %eax
        negl      %esi
        orl       52(%esp), %eax
        movl      %ebx, %ecx
        movl      %esi, (%esp)
        movl      $0, %eax
        movl      48(%esp), %esi
        movl      %esi, %edx
        setne     %al
        movl      $1, %edi
        shll      %cl, %edx
        orl       %eax, %edx
        movl      %edx, 20(%esp)
        movl      16(%esp), %edx
        movl      %edx, %eax
        shll      %cl, %edi
        shll      %cl, %eax
        decl      %edi
        movl      (%esp), %ecx
        shrl      %cl, %esi
        movl      %ebx, %ecx
        andl      %edi, %esi
        orl       %esi, %eax
        movl      %eax, 40(%esp)
        movl      36(%esp), %eax
        shll      %cl, %eax
        movl      (%esp), %ecx
        shrl      %cl, %edx
        andl      %edi, %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, 36(%esp)
        jmp       ..B1.83
..B1.78:
        cmpl      $64, 4(%esp)
        jge       ..B1.80
..B1.79:
        movl      4(%esp), %ebx
        movl      %ebx, %esi
        cmpl      $0, 52(%esp)
        movl      %ebx, %ecx
        movl      $0, %eax
        setne     %al
        movl      $1, %edi
        negl      %esi
        movl      %esi, (%esp)
        movl      44(%esp), %esi
        movl      %esi, %edx
        shll      %cl, %edx
        orl       %eax, %edx
        movl      %edx, 20(%esp)
        movl      48(%esp), %edx
        movl      %edx, %eax
        shll      %cl, %edi
        shll      %cl, %eax
        decl      %edi
        movl      (%esp), %ecx
        shrl      %cl, %esi
        movl      %ebx, %ecx
        andl      %edi, %esi
        orl       %esi, %eax
        movl      %eax, 40(%esp)
        movl      16(%esp), %eax
        shll      %cl, %eax
        movl      (%esp), %ecx
        shrl      %cl, %edx
        andl      %edi, %edx
        orl       %edx, %eax
        andl      $1048575, %eax
        movl      %eax, 36(%esp)
        jmp       ..B1.83
..B1.80:
        cmpl      $96, 4(%esp)
        jge       ..B1.82
..B1.81:
        movl      4(%esp), %edi
        movl      %edi, %ebx
        negl      %ebx
        movl      %edi, %ecx
        movl      %ebx, (%esp)
        movl      $1, %ebx
        movl      44(%esp), %esi
        movl      %esi, %edx
        movl      52(%esp), %eax
        shll      %cl, %ebx
        shll      %cl, %eax
        decl      %ebx
        shll      %cl, %edx
        movl      (%esp), %ecx
        movl      %eax, 20(%esp)
        shrl      %cl, %eax
        movl      %edi, %ecx
        andl      %ebx, %eax
        orl       %eax, %edx
        movl      %edx, 40(%esp)
        movl      48(%esp), %edx
        shll      %cl, %edx
        movl      (%esp), %ecx
        shrl      %cl, %esi
        andl      %ebx, %esi
        orl       %esi, %edx
        andl      $1048575, %edx
        movl      %edx, 36(%esp)
        jmp       ..B1.83
..B1.82:
        movl      4(%esp), %ecx
        xorl      %eax, %eax
        movl      44(%esp), %edx
        shll      %cl, %edx
        andl      $1048575, %edx
        cmpl      $128, %ecx
        movl      %eax, 20(%esp)
        jl        ..L3
        movl      $0, %edx
..L3:
        movl      %eax, 40(%esp)
        movl      %edx, 36(%esp)
..B1.83:
        movl      32(%esp), %eax
        subl      4(%esp), %eax
        movl      %eax, 32(%esp)
..B1.84:
        movl      32(%esp), %eax
        lea       -1(%eax), %edx
        cmpl      $2046, %edx
        jb        ..B1.92
..B1.85:
        cmpl      $2047, 32(%esp)
        jge       ..B1.91
..B1.86:
        negl      %eax
        incl      %eax
        orl       $1048576, 36(%esp)
        movl      %eax, 32(%esp)
        cmpl      $32, %eax
        jge       ..B1.88
..B1.87:
        movl      %eax, %ecx
        movl      %ecx, %edx
        negl      %edx
        movl      $-1, %edi
        movl      20(%esp), %eax
        movl      %eax, %ebx
        shrl      %cl, %edi
        movl      %edx, %ecx
        movl      40(%esp), %esi
        notl      %edi
        shll      %cl, %esi
        movl      32(%esp), %ecx
        andl      %edi, %esi
        shrl      %cl, %ebx
        movl      %edx, %ecx
        shll      %cl, %eax
        orl       %ebx, %esi
        testl     %edi, %eax
        movl      $0, %eax
        setne     %al
        orl       %esi, %eax
        movl      %eax, 20(%esp)
        movl      36(%esp), %eax
        movl      %eax, %esi
        shll      %cl, %esi
        movl      32(%esp), %ecx
        andl      %edi, %esi
        movl      40(%esp), %edi
        shrl      %cl, %edi
        orl       %esi, %edi
        movl      %edi, 40(%esp)
        shrl      %cl, %eax
        jmp       ..B1.93
..B1.88:
        xorl      %eax, %eax
        cmpl      $53, 32(%esp)
        jg        ..B1.90
..B1.89:
        movl      %eax, (%esp)
        movl      32(%esp), %eax
        movl      %eax, %edx
        negl      %edx
        movl      %eax, %ecx
        movl      %edx, 4(%esp)
        movl      $-1, %edx
        shrl      %cl, %edx
        movl      4(%esp), %ecx
        notl      %edx
        movl      36(%esp), %edi
        movl      40(%esp), %esi
        movl      %esi, %ebx
        shll      %cl, %edi
        movl      %eax, %ecx
        shrl      %cl, %ebx
        andl      %edx, %edi
        movl      4(%esp), %ecx
        orl       %ebx, %edi
        shll      %cl, %esi
        movl      %eax, %ecx
        andl      %edx, %esi
        xorl      %edx, %edx
        orl       20(%esp), %esi
        movl      36(%esp), %esi
        setne     %dl
        shrl      %cl, %esi
        orl       %edi, %edx
        movl      (%esp), %eax
        movl      %edx, 20(%esp)
        movl      %esi, 40(%esp)
        jmp       ..B1.93
..B1.90:
        movl      $1, 20(%esp)
        movl      %eax, 40(%esp)
        jmp       ..B1.94
..B1.91:
        movl      60(%esp), %eax
        shrl      $31, %eax
        movl      64(%esp), %edx
        fldl      _large_value_64@GOTOFF(%edx,%eax,8)
        fmull     _large_value_64@GOTOFF(%edx)
        fstl      8(%esp)
        fstl      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.92:
        movl      %eax, %edx
        shll      $20, %edx
        movl      36(%esp), %eax
        orl       %edx, %eax
..B1.93:
        cmpl      $0, 20(%esp)
        je        ..B1.122
..B1.94:
        movl      64(%esp), %edx
        cmpl      $0, 56(%esp)
        fldl      _small_value_64@GOTOFF(%edx)
        fldl      _ones@GOTOFF(%edx)
        fadd      %st(1), %st
        fstpl     8(%esp)
        je        ..B1.124
..B1.95:
        cmpl      $3072, 56(%esp)
        je        ..B1.118
..B1.96:
        cmpl      $2048, 56(%esp)
        jne       ..B1.107
..B1.97:
        cmpl      $0, 60(%esp)
        je        ..B1.99
..B1.98:
        cmpl      $1048576, %eax
        jb        ..B1.104
        jmp       ..B1.139
..B1.99:
        cmpl      $1048575, %eax
        jb        ..B1.104
..B1.100:
        jne       ..B1.105
..B1.101:
        cmpl      $-1, 40(%esp)
        jb        ..B1.104
..B1.102:
        jne       ..B1.105
..B1.103:
        cmpl      $-2147483648, 20(%esp)
        jae       ..B1.105
..B1.104:
        fmul      %st(0), %st
        fstl      8(%esp)
        cmpl      $0, 60(%esp)
        jne       ..B1.139
..B1.105:
        fstp      %st(0)
        xorl      %edx, %edx
        incl      40(%esp)
        sete      %dl
        addl      %edx, %eax
        cmpl      $2146435072, %eax
        jb        ..B1.122
..B1.106:
        movl      60(%esp), %eax
        shrl      $31, %eax
        movl      64(%esp), %edx
        fldl      _large_value_64@GOTOFF(%edx,%eax,8)
        fmull     _large_value_64@GOTOFF(%edx)
        fstl      8(%esp)
        fstl      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.107:
        cmpl      $1024, 56(%esp)
        jne       ..B1.139
..B1.108:
        cmpl      $0, 60(%esp)
        jne       ..B1.110
..B1.109:
        cmpl      $1048576, %eax
        jb        ..B1.115
        jmp       ..B1.139
..B1.110:
        cmpl      $1048575, %eax
        jb        ..B1.115
..B1.111:
        jne       ..B1.116
..B1.112:
        cmpl      $-1, 40(%esp)
        jb        ..B1.115
..B1.113:
        jne       ..B1.116
..B1.114:
        cmpl      $-2147483648, 20(%esp)
        jae       ..B1.116
..B1.115:
        fmul      %st(0), %st
        fstl      8(%esp)
        cmpl      $0, 60(%esp)
        je        ..B1.139
..B1.116:
        fstp      %st(0)
        xorl      %edx, %edx
        incl      40(%esp)
        sete      %dl
        addl      %edx, %eax
        cmpl      $2146435072, %eax
        jb        ..B1.122
..B1.117:
        movl      60(%esp), %eax
        shrl      $31, %eax
        movl      64(%esp), %edx
        fldl      _large_value_64@GOTOFF(%edx,%eax,8)
        fmull     _large_value_64@GOTOFF(%edx)
        fstl      8(%esp)
        fstl      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.118:
        cmpl      $1048576, %eax
        jb        ..B1.121
..B1.119:
        lea       -1048576(%eax), %edx
        orl       40(%esp), %edx
        jne       ..B1.139
..B1.120:
        cmpl      $-2147483648, 20(%esp)
        jne       ..B1.139
..B1.121:
        fmul      %st(0), %st
        fstpl     8(%esp)
..B1.122:
        movl      60(%esp), %edx
        orl       %eax, %edx
        movl      40(%esp), %eax
        movl      %edx, 4(%esp)
        movl      %eax, (%esp)
        fldl      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.123:
        xorl      %eax, %eax
        cmpl      $1024, 56(%esp)
        movl      64(%esp), %edi
        sete      %al
        fldl      _zeros@GOTOFF(%edi,%eax,8)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.124:
        testl     $-2147483648, 20(%esp)
        je        ..B1.118
..B1.125:
        movl      40(%esp), %ecx
        movl      20(%esp), %edx
        andl      $1, %ecx
        andl      $2147483647, %edx
        orl       %edx, %ecx
        je        ..B1.118
..B1.126:
        xorl      %edx, %edx
        incl      40(%esp)
        sete      %dl
        addl      %edx, %eax
        cmpl      $2146435072, %eax
        jb        ..B1.118
..B1.127:
        fstp      %st(0)
        movl      60(%esp), %eax
        shrl      $31, %eax
        movl      64(%esp), %edx
        fldl      _large_value_64@GOTOFF(%edx,%eax,8)
        fmull     _large_value_64@GOTOFF(%edx)
        fstl      8(%esp)
        fstl      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.139:
        fstp      %st(0)
        jmp       ..B1.122
        .align    16,0x90
	.type	fma,@function
	.size	fma,.-fma
	.data
# -- End  fma
	.section .rodata, "a"
	.align 4
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.data
	.section .note.GNU-stack, ""
# End
