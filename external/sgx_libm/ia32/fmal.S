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
	.file "fmal.c"
	.text
..TXTST0:
# -- Begin  fmal
	.text
       .align    16,0x90
	.globl fmal
fmal:
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
        subl      $100, %esp
..B1.2:
        fnstcw    62(%esp)
..B1.3:
        fnstcw    88(%esp)
..B1.4:
        movzbl    17(%ebp), %edi
        movzbl    41(%ebp), %ebx
        andl      $128, %edi
        andl      $128, %ebx
        movzbl    29(%ebp), %edx
        shrl      $7, %edi
        andl      $128, %edx
        shrl      $7, %ebx
        movzwl    88(%esp), %ecx
        movl      %edi, 80(%esp)
        andl      $3072, %ecx
        movl      %ebx, 84(%esp)
        movzwl    16(%ebp), %edi
        movzwl    40(%ebp), %ebx
        movl      %ebx, %eax
        movl      %ecx, 72(%esp)
        movl      %edi, %ecx
        shrl      $7, %edx
        andl      $32767, %ecx
        movzwl    28(%ebp), %esi
        andl      $32767, %eax
        movl      %edx, 32(%esp)
        movl      %esi, %edx
        movl      %eax, 56(%esp)
        andl      $32767, %edx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $32767, %ecx
        movl      %eax, 76(%esp)
        je        ..B1.21
..B1.5:
        testl     %ecx, %ecx
        jne       ..B1.8
..B1.6:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
..B1.7:
        cmpl      $0, 8(%ebp)
        je        ..B1.20
        jmp       ..B1.9
..B1.8:
        cmpl      $16383, %ecx
        je        ..B1.204
..B1.9:
        cmpl      $32767, %edx
        je        ..B1.18
..B1.10:
        testl     %edx, %edx
        jne       ..B1.13
..B1.11:
        cmpl      $0, 24(%ebp)
        jne       ..B1.14
..B1.12:
        cmpl      $0, 20(%ebp)
        je        ..B1.18
        jmp       ..B1.14
..B1.13:
        cmpl      $16383, %edx
        je        ..B1.202
..B1.14:
        cmpl      $32767, 56(%esp)
        je        ..B1.18
..B1.15:
        cmpl      $0, 56(%esp)
        jne       ..B1.54
..B1.16:
        cmpl      $0, 36(%ebp)
        jne       ..B1.54
..B1.17:
        cmpl      $0, 32(%ebp)
        jne       ..B1.54
..B1.18:
        testl     %ecx, %ecx
        jne       ..B1.21
..B1.19:
        cmpl      $0, 12(%ebp)
        jne       ..B1.27
..B1.209:
        cmpl      $0, 8(%ebp)
..B1.20:
        jne       ..B1.27
..B1.21:
        testl     %edx, %edx
        jne       ..B1.24
..B1.22:
        cmpl      $0, 24(%ebp)
        jne       ..B1.27
..B1.23:
        cmpl      $0, 20(%ebp)
        jne       ..B1.27
..B1.24:
        cmpl      $0, 56(%esp)
        jne       ..B1.28
..B1.25:
        cmpl      $0, 36(%ebp)
        jne       ..B1.27
..B1.26:
        cmpl      $0, 32(%ebp)
        je        ..B1.28
..B1.27:
        fldl      _smallest_value_64@GOTOFF(%eax)
        fstpl     64(%esp)
..B1.28:
        cmpl      $32767, %ecx
        je        ..B1.185
..B1.29:
        cmpl      $32767, %edx
        je        ..B1.176
..B1.30:
        cmpl      $32767, 56(%esp)
        je        ..B1.167
..B1.31:
        cmpl      $32767, %ecx
        jge       ..B1.34
..B1.32:
        cmpl      $32767, %edx
        jge       ..B1.34
..B1.33:
        cmpl      $32767, 56(%esp)
        je        ..B1.206
..B1.34:
        movzwl    62(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.166
..B1.35:
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.36:
        fldcw     60(%esp)
..B1.37:
        movzwl    16(%ebp), %edi
        movl      $1, %edx
..B1.38:
        fldt      8(%ebp)
        testl     $32767, %edi
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        jne       ..B1.41
..B1.39:
        cmpl      $0, 12(%ebp)
        jne       ..B1.41
..B1.40:
        cmpl      $0, 8(%ebp)
        je        ..B1.50
..B1.41:
        movzwl    28(%ebp), %eax
        testl     $32767, %eax
        jne       ..B1.46
..B1.42:
        cmpl      $0, 24(%ebp)
        jne       ..B1.46
..B1.43:
        cmpl      $0, 20(%ebp)
        je        ..B1.50
..B1.46:
        fstpt     20(%esp)
        fldt      20(%esp)
..B1.47:
        movzwl    28(%esp), %eax
        testl     $32767, %eax
        jne       ..B1.50
..B1.48:
        cmpl      $0, 24(%esp)
        jne       ..B1.50
..B1.49:
        cmpl      $0, 20(%esp)
        je        ..B1.51
..B1.50:
        fldt      32(%ebp)
        faddp     %st, %st(1)
        fstpt     20(%esp)
        fldt      20(%esp)
..B1.51:
        fstp      %st(0)
        testl     %edx, %edx
        je        ..B1.53
..B1.52:
        fldcw     62(%esp)
..B1.53:
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.54:
        testl     %ecx, %ecx
        je        ..B1.57
..B1.55:
        testl     %edx, %edx
        je        ..B1.57
..B1.56:
        cmpl      $0, 56(%esp)
        jne       ..B1.69
..B1.57:
        fldl      _smallest_value_64@GOTOFF(%eax)
        movzwl    62(%esp), %eax
        movl      %eax, (%esp)
        andl      $768, %eax
        fstpl     64(%esp)
        cmpl      $768, %eax
        je        ..B1.201
..B1.58:
        movl      (%esp), %eax
        orl       $-64768, %eax
        movw      %ax, 60(%esp)
..B1.59:
        fldcw     60(%esp)
..B1.60:
        movzwl    16(%ebp), %edi
        movl      $1, %eax
        movzwl    28(%ebp), %esi
        movzwl    40(%ebp), %ebx
..B1.61:
        testl     $32767, %edi
        jne       ..B1.63
..B1.62:
        andl      $-32768, %edi
        orl       $-49153, %edi
        movw      %di, 16(%ebp)
        movl      76(%esp), %ecx
        movl      80(%esp), %edi
        orl       $-2147483648, 12(%ebp)
        fldt      8(%ebp)
        fsubl     _ones@GOTOFF(%ecx,%edi,8)
        fstpt     8(%ebp)
        movzwl    16(%ebp), %ecx
        andl      $32767, %ecx
        addl      $-16382, %ecx
..B1.63:
        testl     $32767, %esi
        jne       ..B1.65
..B1.64:
        andl      $-32768, %esi
        orl       $-49153, %esi
        movw      %si, 28(%ebp)
        movl      76(%esp), %edx
        movl      32(%esp), %esi
        orl       $-2147483648, 24(%ebp)
        fldt      20(%ebp)
        fsubl     _ones@GOTOFF(%edx,%esi,8)
        fstpt     20(%ebp)
        movzwl    28(%ebp), %edx
        andl      $32767, %edx
        addl      $-16382, %edx
..B1.65:
        testl     $32767, %ebx
        jne       ..B1.67
..B1.66:
        andl      $-32768, %ebx
        orl       $-49153, %ebx
        movw      %bx, 40(%ebp)
        movl      76(%esp), %ebx
        movl      84(%esp), %esi
        orl       $-2147483648, 36(%ebp)
        fldt      32(%ebp)
        fsubl     _ones@GOTOFF(%ebx,%esi,8)
        fstpt     32(%ebp)
        movzwl    40(%ebp), %edi
        andl      $32767, %edi
        addl      $-16382, %edi
        movl      %edi, 56(%esp)
..B1.67:
        testl     %eax, %eax
        je        ..B1.69
..B1.68:
        fldcw     62(%esp)
..B1.69:
        movl      36(%ebp), %eax
        lea       -16383(%ecx,%edx), %edx
        movl      %eax, 4(%esp)
        movl      32(%ebp), %eax
        movl      %eax, (%esp)
        movl      32(%esp), %eax
        movl      8(%ebp), %edi
        xorl      %eax, 80(%esp)
        movl      %edi, %eax
        movl      20(%ebp), %ebx
        movl      %edx, 52(%esp)
        mull      %ebx
        movl      %eax, 44(%esp)
        movl      %ebx, %eax
        movl      12(%ebp), %esi
        movl      %edx, 20(%esp)
        mull      %esi
        movl      %eax, %ecx
        movl      %edi, %eax
        movl      24(%ebp), %ebx
        movl      %edx, 24(%esp)
        mull      %ebx
        movl      %eax, %edi
        movl      %esi, %eax
        movl      %edx, 28(%esp)
        xorl      %esi, %esi
        addl      %edi, %ecx
        adcl      $0, %esi
        mull      %ebx
        addl      20(%esp), %ecx
        movl      %edx, %ebx
        movl      %ecx, 48(%esp)
        adcl      $0, %esi
        xorl      %edx, %edx
        addl      %eax, %esi
        movl      24(%esp), %ecx
        adcl      $0, %edx
        xorl      %eax, %eax
        addl      28(%esp), %ecx
        adcl      $0, %eax
        addl      %ecx, %esi
        adcl      %eax, %edx
        addl      %edx, %ebx
        movl      52(%esp), %edx
        testl     $-2147483648, %ebx
        je        ..B1.71
..B1.70:
        incl      %edx
        jmp       ..B1.72
..B1.71:
        movl      %esi, %edi
        addl      %ebx, %ebx
        shrl      $31, %edi
        addl      %esi, %esi
        movl      48(%esp), %eax
        orl       %edi, %ebx
        movl      %eax, %edi
        addl      %eax, %eax
        shrl      $31, %edi
        movl      44(%esp), %ecx
        orl       %edi, %esi
        movl      %ecx, %edi
        addl      %ecx, %ecx
        shrl      $31, %edi
        orl       %edi, %eax
        movl      %eax, 48(%esp)
        movl      %ecx, 44(%esp)
..B1.72:
        cmpl      56(%esp), %edx
        jg        ..B1.77
..B1.73:
        jne       ..B1.78
..B1.74:
        cmpl      4(%esp), %ebx
        ja        ..B1.77
..B1.75:
        jne       ..B1.78
..B1.76:
        cmpl      (%esp), %esi
        jb        ..B1.78
..B1.77:
        movl      %edx, %eax
        subl      56(%esp), %eax
        movl      4(%esp), %edi
        movl      %eax, 32(%esp)
        movl      (%esp), %eax
        movl      %edi, 28(%esp)
        xorl      %edi, %edi
        movl      %eax, 36(%esp)
        movl      %edi, 40(%esp)
        jmp       ..B1.79
..B1.78:
        movl      56(%esp), %eax
        movl      %eax, %edi
        subl      %edx, %edi
        movl      %eax, %edx
        movl      80(%esp), %eax
        movl      %eax, 84(%esp)
        movzbl    41(%ebp), %eax
        movl      %ebx, 28(%esp)
        andl      $128, %eax
        movl      48(%esp), %ebx
        movl      %edi, 32(%esp)
        shrl      $7, %eax
        movl      %esi, 36(%esp)
        movl      %ebx, 40(%esp)
        movl      44(%esp), %edi
        movl      %eax, 80(%esp)
        xorl      %eax, %eax
        movl      4(%esp), %ebx
        movl      (%esp), %esi
        movl      %eax, 44(%esp)
        movl      %eax, 48(%esp)
..B1.79:
        cmpl      $0, 32(%esp)
        je        ..B1.89
..B1.80:
        cmpl      $32, 32(%esp)
        jge       ..B1.82
..B1.81:
        movl      %esi, 20(%esp)
        movl      %edi, %eax
        movl      32(%esp), %esi
        movl      %esi, %ecx
        movl      %edx, 52(%esp)
        movl      %esi, %edx
        movl      %ebx, 24(%esp)
        negl      %edx
        movl      $-1, %ebx
        shrl      %cl, %ebx
        movl      %edx, %ecx
        shll      %cl, %eax
        notl      %ebx
        movl      %eax, 56(%esp)
        movl      40(%esp), %eax
        shll      %cl, %eax
        movl      %esi, %ecx
        shrl      %cl, %edi
        andl      %ebx, %eax
        orl       %eax, %edi
        movl      %edx, %ecx
        movl      %edi, 4(%esp)
        movl      36(%esp), %edi
        movl      %edi, %eax
        shll      %cl, %eax
        movl      %esi, %ecx
        andl      %ebx, %eax
        movl      %eax, (%esp)
        movl      40(%esp), %eax
        shrl      %cl, %eax
        movl      %edx, %ecx
        orl       (%esp), %eax
        movl      28(%esp), %edx
        movl      %eax, 40(%esp)
        movl      %edx, %eax
        shll      %cl, %eax
        movl      %esi, %ecx
        shrl      %cl, %edi
        andl      %ebx, %eax
        shrl      %cl, %edx
        orl       %eax, %edi
        movl      %edi, 36(%esp)
        movl      %edx, 28(%esp)
        movl      20(%esp), %esi
        movl      52(%esp), %edx
        movl      24(%esp), %ebx
        movl      4(%esp), %edi
        jmp       ..B1.90
..B1.82:
        cmpl      $64, 32(%esp)
        jge       ..B1.84
..B1.83:
        movl      %ebx, 24(%esp)
        movl      32(%esp), %ebx
        movl      %ebx, %ecx
        movl      %edx, 52(%esp)
        movl      %ebx, %edx
        movl      %esi, 20(%esp)
        negl      %edx
        movl      $-1, %esi
        shrl      %cl, %esi
        movl      %edx, %ecx
        movl      40(%esp), %eax
        notl      %esi
        shll      %cl, %eax
        xorl      %ecx, %ecx
        testl     %edi, %edi
        movl      %esi, (%esp)
        setne     %cl
        andl      (%esp), %eax
        movl      36(%esp), %esi
        orl       %ecx, %eax
        movl      %edx, %ecx
        movl      %esi, %edi
        shll      %cl, %edi
        movl      %ebx, %ecx
        movl      %eax, 56(%esp)
        movl      40(%esp), %eax
        andl      (%esp), %edi
        shrl      %cl, %eax
        movl      %edx, %ecx
        movl      28(%esp), %edx
        orl       %eax, %edi
        movl      %edx, %eax
        shll      %cl, %eax
        movl      %ebx, %ecx
        andl      (%esp), %eax
        shrl      %cl, %esi
        shrl      %cl, %edx
        orl       %esi, %eax
        movl      %edx, 36(%esp)
        movl      %eax, 40(%esp)
        movl      $0, 28(%esp)
        movl      20(%esp), %esi
        movl      52(%esp), %edx
        movl      24(%esp), %ebx
        jmp       ..B1.90
..B1.84:
        cmpl      $96, 32(%esp)
        jge       ..B1.86
..B1.85:
        movl      %edx, 52(%esp)
        movl      32(%esp), %edx
        movl      %edx, %ecx
        movl      %ebx, 24(%esp)
        movl      %edx, %ebx
        movl      %esi, 20(%esp)
        movl      $-1, %esi
        negl      %ebx
        movl      %ebx, (%esp)
        shrl      %cl, %esi
        xorl      %ebx, %ebx
        orl       40(%esp), %edi
        notl      %esi
        movl      (%esp), %ecx
        movl      36(%esp), %eax
        setne     %bl
        shll      %cl, %eax
        andl      %esi, %eax
        orl       %ebx, %eax
        movl      %eax, 56(%esp)
        movl      28(%esp), %eax
        movl      %eax, %edi
        shll      %cl, %edi
        movl      %edx, %ecx
        andl      %esi, %edi
        xorl      %edx, %edx
        movl      36(%esp), %esi
        shrl      %cl, %esi
        shrl      %cl, %eax
        orl       %esi, %edi
        movl      %edx, 36(%esp)
        movl      %edx, 28(%esp)
        movl      %eax, 40(%esp)
        movl      20(%esp), %esi
        movl      52(%esp), %edx
        movl      24(%esp), %ebx
        jmp       ..B1.90
..B1.86:
        cmpl      $128, 32(%esp)
        jge       ..B1.88
..B1.87:
        movl      %edx, 52(%esp)
        movl      32(%esp), %edx
        movl      %edx, %eax
        movl      %ebx, 24(%esp)
        negl      %eax
        movl      %edx, %ecx
        movl      $-1, %ebx
        movl      %esi, 20(%esp)
        movl      28(%esp), %esi
        shrl      %cl, %ebx
        movl      %eax, %ecx
        movl      %esi, %eax
        notl      %ebx
        shll      %cl, %eax
        movl      36(%esp), %ecx
        andl      %ebx, %eax
        orl       40(%esp), %ecx
        xorl      %ebx, %ebx
        orl       %edi, %ecx
        movl      %edx, %ecx
        movl      %esi, %edi
        setne     %bl
        xorl      %edx, %edx
        movl      %edx, 40(%esp)
        orl       %ebx, %eax
        movl      %edx, 36(%esp)
        movl      %edx, 28(%esp)
        movl      %eax, 56(%esp)
        shrl      %cl, %edi
        movl      20(%esp), %esi
        movl      52(%esp), %edx
        movl      24(%esp), %ebx
        jmp       ..B1.90
..B1.88:
        movl      28(%esp), %ecx
        orl       36(%esp), %ecx
        xorl      %eax, %eax
        orl       40(%esp), %ecx
        orl       %edi, %ecx
        setne     %al
        xorl      %edi, %edi
        movl      %eax, 56(%esp)
        movl      %edi, 40(%esp)
        movl      %edi, 36(%esp)
        movl      %edi, 28(%esp)
        jmp       ..B1.90
..B1.89:
        movl      $0, 56(%esp)
..B1.90:
        movl      80(%esp), %eax
        cmpl      84(%esp), %eax
        jne       ..B1.94
..B1.91:
        movl      44(%esp), %eax
        addl      %eax, %edi
        cmpl      %eax, %edi
        movl      $0, %eax
        movl      48(%esp), %ecx
        setb      %al
        addl      40(%esp), %ecx
        movl      %edi, 4(%esp)
        movl      $0, %edi
        adcl      $0, %edi
        addl      %eax, %ecx
        adcl      $0, %edi
        xorl      %eax, %eax
        addl      36(%esp), %esi
        movl      %ecx, 48(%esp)
        adcl      $0, %eax
        addl      %edi, %esi
        adcl      $0, %eax
        xorl      %edi, %edi
        addl      28(%esp), %ebx
        adcl      $0, %edi
        addl      %eax, %ebx
        adcl      $0, %edi
        movl      4(%esp), %edi
        je        ..B1.93
..B1.92:
        orl       48(%esp), %edi
        movl      %esi, %eax
        shll      $31, %eax
        incl      %edx
        orl       56(%esp), %edi
        movl      %eax, 36(%esp)
        movl      %ebx, %edi
        movl      $0, %eax
        setne     %al
        shll      $31, %edi
        shrl      $1, %esi
        shrl      $1, %ebx
        orl       %edi, %esi
        orl       %eax, 36(%esp)
        orl       $-2147483648, %ebx
        jmp       ..B1.116
..B1.93:
        orl       56(%esp), %edi
        movl      $0, %eax
        setne     %al
        orl       48(%esp), %eax
        movl      %eax, 36(%esp)
        jmp       ..B1.116
..B1.94:
        negl      56(%esp)
        movl      %edx, 52(%esp)
        movl      $0, %edx
        movl      44(%esp), %ecx
        setne     %dl
        xorl      %eax, %eax
        subl      %edi, %ecx
        movl      48(%esp), %edi
        sbbl      $0, %eax
        subl      %edx, %ecx
        movl      %ecx, 44(%esp)
        sbbl      $0, %eax
        xorl      %ecx, %ecx
        subl      40(%esp), %edi
        cltd      
        sbbl      $0, %ecx
        addl      %eax, %edi
        movl      %edi, 48(%esp)
        adcl      %edx, %ecx
        xorl      %edi, %edi
        subl      36(%esp), %esi
        movl      %ecx, %eax
        cltd      
        sbbl      $0, %edi
        addl      %esi, %ecx
        movl      %ecx, 32(%esp)
        adcl      %edx, %edi
        subl      28(%esp), %ebx
        movl      52(%esp), %edx
        addl      %edi, %ebx
        je        ..B1.96
..B1.95:
        movl      $0, 28(%esp)
        movl      %ebx, %eax
        jmp       ..B1.104
..B1.96:
        cmpl      $0, 32(%esp)
        je        ..B1.98
..B1.97:
        movl      %ecx, %eax
        movl      $32, 28(%esp)
        jmp       ..B1.104
..B1.98:
        cmpl      $0, 48(%esp)
        je        ..B1.100
..B1.99:
        movl      48(%esp), %eax
        movl      $64, 28(%esp)
        jmp       ..B1.104
..B1.100:
        cmpl      $0, 44(%esp)
        je        ..B1.102
..B1.101:
        movl      44(%esp), %eax
        movl      $96, 28(%esp)
        jmp       ..B1.104
..B1.102:
        cmpl      $0, 56(%esp)
        je        ..B1.165
..B1.103:
        movl      56(%esp), %eax
        movl      $128, 28(%esp)
..B1.104:
        testl     $-2147483648, %eax
        jne       ..B1.108
..B1.105:
        movl      28(%esp), %esi
..B1.106:
        addl      %eax, %eax
        incl      %esi
        testl     $-2147483648, %eax
        je        ..B1.106
..B1.107:
        movl      %esi, 28(%esp)
..B1.108:
        cmpl      $32, 28(%esp)
        jge       ..B1.110
..B1.109:
        movl      28(%esp), %edi
        movl      %edi, %esi
        movl      %edx, 52(%esp)
        movl      %edi, %ecx
        movl      $1, %edx
        negl      %esi
        movl      %esi, (%esp)
        shll      %cl, %edx
        movl      44(%esp), %esi
        decl      %edx
        orl       56(%esp), %esi
        movl      %ebx, 24(%esp)
        movl      48(%esp), %ebx
        movl      %ebx, %eax
        movl      %edx, 4(%esp)
        movl      $0, %edx
        setne     %dl
        shll      %cl, %eax
        orl       %edx, %eax
        movl      %eax, 36(%esp)
        movl      32(%esp), %eax
        movl      %eax, %esi
        shll      %cl, %esi
        movl      (%esp), %ecx
        shrl      %cl, %ebx
        movl      %edi, %ecx
        andl      4(%esp), %ebx
        orl       %ebx, %esi
        movl      24(%esp), %ebx
        shll      %cl, %ebx
        movl      (%esp), %ecx
        shrl      %cl, %eax
        andl      4(%esp), %eax
        movl      52(%esp), %edx
        orl       %eax, %ebx
        jmp       ..B1.115
..B1.110:
        cmpl      $64, 28(%esp)
        jge       ..B1.112
..B1.111:
        movl      28(%esp), %eax
        movl      %eax, %ecx
        movl      %edx, 52(%esp)
        movl      $1, %edx
        shll      %cl, %edx
        movl      %eax, %esi
        decl      %edx
        negl      %esi
        cmpl      $0, 56(%esp)
        movl      44(%esp), %edi
        movl      %edi, %ebx
        movl      %edx, 4(%esp)
        movl      $0, %edx
        setne     %dl
        shll      %cl, %ebx
        orl       %edx, %ebx
        movl      %ebx, 36(%esp)
        movl      %esi, (%esp)
        movl      48(%esp), %ebx
        movl      %ebx, %esi
        shll      %cl, %esi
        movl      (%esp), %ecx
        shrl      %cl, %edi
        movl      %eax, %ecx
        andl      4(%esp), %edi
        orl       %edi, %esi
        movl      32(%esp), %edi
        shll      %cl, %edi
        movl      (%esp), %ecx
        shrl      %cl, %ebx
        andl      4(%esp), %ebx
        movl      52(%esp), %edx
        orl       %edi, %ebx
        jmp       ..B1.115
..B1.112:
        cmpl      $96, 28(%esp)
        jge       ..B1.114
..B1.113:
        movl      28(%esp), %eax
        movl      %eax, %esi
        movl      %eax, %ecx
        movl      $1, %edi
        movl      %edx, 52(%esp)
        negl      %esi
        movl      56(%esp), %edx
        movl      44(%esp), %ebx
        shll      %cl, %edx
        movl      %esi, (%esp)
        movl      %ebx, %esi
        movl      %edx, 36(%esp)
        shll      %cl, %edi
        shll      %cl, %esi
        decl      %edi
        movl      (%esp), %ecx
        shrl      %cl, %edx
        movl      %eax, %ecx
        andl      %edi, %edx
        orl       %edx, %esi
        movl      48(%esp), %edx
        shll      %cl, %edx
        movl      (%esp), %ecx
        shrl      %cl, %ebx
        andl      %edi, %ebx
        orl       %edx, %ebx
        movl      52(%esp), %edx
        jmp       ..B1.115
..B1.114:
        movl      28(%esp), %ecx
        xorl      %eax, %eax
        movl      44(%esp), %ebx
        xorl      %esi, %esi
        shll      %cl, %ebx
        cmpl      $128, %ecx
        movl      %eax, 36(%esp)
        jl        ..L3
        movl      $0, %ebx
..L3:
..B1.115:
        subl      28(%esp), %edx
..B1.116:
        lea       -1(%edx), %edi
        cmpl      $32766, %edi
        jb        ..B1.126
..B1.117:
        cmpl      $32767, %edx
        jge       ..B1.164
..B1.118:
        negl      %edx
        incl      %edx
        cmpl      $32, %edx
        jge       ..B1.120
..B1.119:
        movl      %edx, %edi
        movl      %edx, %ecx
        negl      %edi
        xorl      %eax, %eax
        movl      %edi, (%esp)
        movl      $-1, %edi
        shrl      %cl, %edi
        cmpl      $0, 36(%esp)
        notl      %edi
        movl      (%esp), %ecx
        movl      %esi, 20(%esp)
        setne     %al
        shll      %cl, %esi
        andl      %edi, %esi
        orl       %esi, %eax
        movl      %eax, 36(%esp)
        movl      %ebx, %eax
        movl      (%esp), %ecx
        shll      %cl, %eax
        movl      %edx, %ecx
        movl      20(%esp), %esi
        andl      %edi, %eax
        shrl      %cl, %esi
        shrl      %cl, %ebx
        orl       %eax, %esi
        jmp       ..B1.125
..B1.120:
        cmpl      $64, %edx
        jge       ..B1.122
..B1.121:
        movl      %edx, %edi
        movl      %edx, %ecx
        negl      %edi
        movl      $-1, %eax
        shrl      %cl, %eax
        movl      %edi, %ecx
        movl      %ebx, %edi
        notl      %eax
        shll      %cl, %edi
        movl      %edx, %ecx
        andl      %eax, %edi
        xorl      %eax, %eax
        orl       36(%esp), %esi
        movl      %ebx, %esi
        setne     %al
        xorl      %ebx, %ebx
        orl       %edi, %eax
        movl      %eax, 36(%esp)
        shrl      %cl, %esi
        jmp       ..B1.125
..B1.122:
        cmpl      $96, %edx
        jge       ..B1.124
..B1.123:
        movl      %edx, %edi
        movl      %edx, %ecx
        negl      %edi
        movl      $-1, %eax
        movl      %ebx, %edx
        shrl      %cl, %eax
        shrl      %cl, %edx
        movl      %edi, %ecx
        shll      %cl, %ebx
        notl      %eax
        andl      %eax, %ebx
        orl       %esi, %ebx
        orl       36(%esp), %ebx
        movl      $0, %ebx
        setne     %bl
        xorl      %esi, %esi
        orl       %edx, %ebx
        movl      %ebx, 36(%esp)
        xorl      %ebx, %ebx
        jmp       ..B1.125
..B1.124:
        xorl      %esi, %esi
        movl      $1, 36(%esp)
        xorl      %ebx, %ebx
..B1.125:
        xorl      %edx, %edx
..B1.126:
        cmpl      $0, 36(%esp)
        je        ..B1.163
..B1.127:
        movl      76(%esp), %edi
        cmpl      $0, 72(%esp)
        fldt      _small_value_80@GOTOFF(%edi)
        fldl      _ones@GOTOFF(%edi)
        fadd      %st(1), %st
        fstpt     8(%esp)
        je        ..B1.194
..B1.128:
        cmpl      $3072, 72(%esp)
        je        ..B1.159
..B1.129:
        cmpl      $2048, 72(%esp)
        jne       ..B1.144
..B1.130:
        testl     %edx, %edx
        jne       ..B1.138
..B1.131:
        cmpl      $0, 80(%esp)
        jne       ..B1.137
..B1.132:
        cmpl      $2147483647, %ebx
        jb        ..B1.137
..B1.133:
        jne       ..B1.139
..B1.134:
        cmpl      $-1, %esi
        jb        ..B1.137
..B1.135:
        jne       ..B1.139
..B1.136:
        cmpl      $-2147483648, 36(%esp)
        jae       ..B1.139
..B1.137:
        fmul      %st(0), %st
        fstpt     8(%esp)
        fldt      8(%esp)
..B1.138:
        cmpl      $0, 80(%esp)
        jne       ..B1.211
..B1.139:
        fstp      %st(0)
        incl      %esi
        jne       ..B1.142
..B1.140:
        movl      $-2147483648, %edi
        testl     %edx, %edx
        je        ..L4
        movl      $0, %edi
..L4:
        incl      %ebx
        cmpl      %edi, %ebx
        jne       ..B1.142
..B1.141:
        orl       $-2147483648, %ebx
        incl      %edx
..B1.142:
        cmpl      $32767, %edx
        jl        ..B1.163
..B1.143:
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        lea       (,%edx,8), %eax
        fldt      _large_value_80@GOTOFF(%ebx)
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     8(%esp)
        fldt      8(%esp)
        fstpt     20(%esp)
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.144:
        cmpl      $1024, 72(%esp)
        jne       ..B1.211
..B1.145:
        testl     %edx, %edx
        jne       ..B1.153
..B1.146:
        cmpl      $0, 80(%esp)
        je        ..B1.152
..B1.147:
        cmpl      $2147483647, %ebx
        jb        ..B1.152
..B1.148:
        jne       ..B1.154
..B1.149:
        cmpl      $-1, %esi
        jb        ..B1.152
..B1.150:
        jne       ..B1.154
..B1.151:
        cmpl      $-2147483648, 36(%esp)
        jae       ..B1.154
..B1.152:
        fmul      %st(0), %st
        fstpt     8(%esp)
        fldt      8(%esp)
..B1.153:
        cmpl      $0, 80(%esp)
        je        ..B1.211
..B1.154:
        fstp      %st(0)
        incl      %esi
        jne       ..B1.157
..B1.155:
        movl      $-2147483648, %edi
        testl     %edx, %edx
        je        ..L5
        movl      $0, %edi
..L5:
        incl      %ebx
        cmpl      %edi, %ebx
        jne       ..B1.157
..B1.156:
        orl       $-2147483648, %ebx
        incl      %edx
..B1.157:
        cmpl      $32767, %edx
        jl        ..B1.163
..B1.158:
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        lea       (,%edx,8), %eax
        fldt      _large_value_80@GOTOFF(%ebx)
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     8(%esp)
        fldt      8(%esp)
        fstpt     20(%esp)
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.159:
        testl     %edx, %edx
        je        ..B1.162
..B1.160:
        lea       -1(%edx), %eax
        lea       -2147483648(%ebx), %edi
        orl       %edi, %eax
        orl       %esi, %eax
        jne       ..B1.211
..B1.161:
        cmpl      $-2147483648, 36(%esp)
        jne       ..B1.211
..B1.162:
        fmul      %st(0), %st
        fstpt     8(%esp)
..B1.163:
        movzbl    29(%esp), %eax
        andl      $32767, %edx
        movl      80(%esp), %edi
        andl      $127, %eax
        shll      $7, %edi
        orl       %edi, %eax
        movb      %al, 29(%esp)
        movzwl    28(%esp), %edi
        andl      $-32768, %edi
        orl       %edx, %edi
        movw      %di, 28(%esp)
        movl      %ebx, 24(%esp)
        movl      %esi, 20(%esp)
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.164:
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        lea       (,%edx,8), %eax
        fldt      _large_value_80@GOTOFF(%ebx)
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     8(%esp)
        fldt      8(%esp)
        fstpt     20(%esp)
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.165:
        xorl      %eax, %eax
        cmpl      $1024, 72(%esp)
        movl      76(%esp), %edi
        sete      %al
        fldl      _zeros@GOTOFF(%edi,%eax,8)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.166:
        xorl      %edx, %edx
        jmp       ..B1.38
..B1.167:
        cmpl      $-2147483648, 36(%ebp)
        jne       ..B1.169
..B1.168:
        cmpl      $0, 32(%ebp)
        je        ..B1.31
..B1.169:
        movzwl    62(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.175
..B1.170:
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.171:
        fldcw     60(%esp)
..B1.172:
        fldt      32(%ebp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
..B1.173:
        fldcw     62(%esp)
..B1.174:
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.175:
        fldt      32(%ebp)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
        jmp       ..B1.174
..B1.176:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.178
..B1.177:
        cmpl      $0, 20(%ebp)
        je        ..B1.30
..B1.178:
        movzwl    62(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.184
..B1.179:
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.180:
        fldcw     60(%esp)
..B1.181:
        fldt      20(%ebp)
        fldt      32(%ebp)
        fmulp     %st, %st(1)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
..B1.182:
        fldcw     62(%esp)
..B1.183:
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.184:
        fldt      20(%ebp)
        fldt      32(%ebp)
        fmulp     %st, %st(1)
        fldt      8(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
        jmp       ..B1.183
..B1.185:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.187
..B1.186:
        cmpl      $0, 8(%ebp)
        je        ..B1.29
..B1.187:
        movzwl    62(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.193
..B1.188:
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.189:
        fldcw     60(%esp)
..B1.190:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fldt      32(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
..B1.191:
        fldcw     62(%esp)
..B1.192:
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.193:
        fldt      8(%ebp)
        fldt      20(%ebp)
        fmulp     %st, %st(1)
        fldt      32(%ebp)
        fmulp     %st, %st(1)
        fstpt     20(%esp)
        jmp       ..B1.192
..B1.194:
        testl     $-2147483648, 36(%esp)
        je        ..B1.159
..B1.195:
        movl      %esi, %eax
        movl      36(%esp), %edi
        andl      $1, %eax
        andl      $2147483647, %edi
        orl       %edi, %eax
        je        ..B1.159
..B1.196:
        incl      %esi
        jne       ..B1.199
..B1.197:
        movl      $-2147483648, %edi
        testl     %edx, %edx
        je        ..L6
        movl      $0, %edi
..L6:
        incl      %ebx
        cmpl      %edi, %ebx
        jne       ..B1.199
..B1.198:
        orl       $-2147483648, %ebx
        incl      %edx
..B1.199:
        cmpl      $32767, %edx
        jl        ..B1.159
..B1.200:
        fstp      %st(0)
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        lea       (,%edx,8), %eax
        fldt      _large_value_80@GOTOFF(%ebx)
        lea       (%eax,%edx,4), %ecx
        fldt      _large_value_80@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     8(%esp)
        fldt      8(%esp)
        fstpt     20(%esp)
        fldt      20(%esp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.201:
        xorl      %eax, %eax
        jmp       ..B1.61
..B1.202:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.14
..B1.203:
        cmpl      $0, 20(%ebp)
        je        ..B1.18
        jmp       ..B1.14
..B1.204:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.9
..B1.205:
        cmpl      $0, 8(%ebp)
        je        ..B1.21
        jmp       ..B1.9
..B1.206:
        fldt      32(%ebp)
        addl      $100, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.211:
        fstp      %st(0)
        jmp       ..B1.163
        .align    16,0x90
	.type	fmal,@function
	.size	fmal,.-fmal
	.data
# -- End  fmal
	.section .rodata, "a"
	.align 4
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
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
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,24
	.data
	.section .note.GNU-stack, ""
# End
