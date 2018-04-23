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
	.file "dpml_ux_trig.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_radian_reduce__
	.text
       .align    16,0x90
__dpml_ux_radian_reduce__:
# parameter 1: 176 + %esp
# parameter 2: 180 + %esp
# parameter 3: 188 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $156, %esp
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        movl      %eax, 76(%esp)
        movl      176(%esp), %eax
        movl      8(%eax), %edx
        movl      12(%eax), %ecx
        movl      %edx, 108(%esp)
        movl      %ecx, 104(%esp)
        movl      16(%eax), %edx
        movl      20(%eax), %ecx
        movl      4(%eax), %ebp
        testl     %ebp, %ebp
        movl      (%eax), %ebx
        movl      %edx, 128(%esp)
        movl      %ecx, 132(%esp)
        movl      %ebp, 112(%esp)
        movl      %ebx, 80(%esp)
        jl        ..B1.120
..B1.2:
        movl      %ebp, %eax
        xorl      %ebp, %ebp
        cltd      
        addl      $8, %eax
        movl      %eax, %edi
        adcl      $0, %edx
        andl      $63, %eax
        movl      76(%esp), %ebx
        shll      $26, %edx
        shrl      $6, %edi
        orl       %edx, %edi
        movl      __four_over_pi@GOT(%ebx), %esi
        movl      %edi, 72(%esp)
        movl      %eax, 112(%esp)
        lea       (%esi,%edi,8), %ecx
        movl      (%ecx), %ebx
        movl      4(%ecx), %esi
        movl      %ebx, 60(%esp)
        movl      %esi, 56(%esp)
        movl      8(%ecx), %edx
        movl      12(%ecx), %edi
        movl      16(%ecx), %ebx
        movl      20(%ecx), %esi
        movl      %edx, 68(%esp)
        movl      %edi, 64(%esp)
        movl      %ebx, 36(%esp)
        movl      %esi, 32(%esp)
        movl      24(%ecx), %ebx
        movl      28(%ecx), %esi
        movl      32(%ecx), %edx
        movl      36(%ecx), %edi
        addl      $40, %ecx
        movl      %edx, 120(%esp)
        orl       %eax, %ebp
        movl      %edi, 124(%esp)
        movl      %ecx, 116(%esp)
        je        ..B1.28
..B1.3:
        movl      %eax, %ecx
        movl      %ecx, %edx
        negl      %edx
        addl      $64, %edx
        movl      %edx, 72(%esp)
        cmpl      $31, %ecx
        jbe       ..B1.5
..B1.4:
        xorl      %edx, %edx
        movl      60(%esp), %ebp
        shll      %cl, %ebp
        movl      %ebp, 56(%esp)
        jmp       ..B1.6
..B1.5:
        movl      56(%esp), %ebp
        movl      60(%esp), %edx
        shldl     %cl, %edx, %ebp
        movl      %ebp, 56(%esp)
        shll      %cl, %edx
..B1.6:
        cmpl      $31, 72(%esp)
        jbe       ..B1.8
..B1.7:
        movl      72(%esp), %ecx
        xorl      %ebp, %ebp
        movl      64(%esp), %edi
        shrl      %cl, %edi
        movl      %edi, 60(%esp)
        jmp       ..B1.9
..B1.8:
        movl      72(%esp), %ecx
        movl      68(%esp), %edi
        movl      64(%esp), %ebp
        shrdl     %cl, %ebp, %edi
        movl      %edi, 60(%esp)
        shrl      %cl, %ebp
..B1.9:
        orl       %edx, 60(%esp)
        orl       %ebp, 56(%esp)
        cmpl      $31, 112(%esp)
        jbe       ..B1.11
..B1.10:
        movl      %eax, %ecx
        xorl      %edx, %edx
        movl      68(%esp), %ebp
        shll      %cl, %ebp
        movl      %ebp, 64(%esp)
        jmp       ..B1.12
..B1.11:
        movl      %eax, %ecx
        movl      64(%esp), %ebp
        movl      68(%esp), %edx
        shldl     %cl, %edx, %ebp
        movl      %ebp, 64(%esp)
        shll      %cl, %edx
..B1.12:
        cmpl      $31, 72(%esp)
        jbe       ..B1.14
..B1.13:
        movl      72(%esp), %ecx
        xorl      %ebp, %ebp
        movl      32(%esp), %edi
        shrl      %cl, %edi
        movl      %edi, 68(%esp)
        jmp       ..B1.15
..B1.14:
        movl      72(%esp), %ecx
        movl      36(%esp), %edi
        movl      32(%esp), %ebp
        shrdl     %cl, %ebp, %edi
        movl      %edi, 68(%esp)
        shrl      %cl, %ebp
..B1.15:
        orl       %edx, 68(%esp)
        orl       %ebp, 64(%esp)
        cmpl      $31, 112(%esp)
        jbe       ..B1.17
..B1.16:
        movl      %eax, %ecx
        xorl      %edx, %edx
        movl      36(%esp), %ebp
        shll      %cl, %ebp
        movl      %ebp, 32(%esp)
        jmp       ..B1.18
..B1.17:
        movl      %eax, %ecx
        movl      32(%esp), %ebp
        movl      36(%esp), %edx
        shldl     %cl, %edx, %ebp
        movl      %ebp, 32(%esp)
        shll      %cl, %edx
..B1.18:
        cmpl      $31, 72(%esp)
        jbe       ..B1.20
..B1.19:
        movl      %esi, %edi
        xorl      %ebp, %ebp
        movl      72(%esp), %ecx
        shrl      %cl, %edi
        movl      %edi, 36(%esp)
        jmp       ..B1.21
..B1.20:
        movl      %ebx, %ebp
        movl      72(%esp), %ecx
        shrdl     %cl, %esi, %ebp
        movl      %ebp, 36(%esp)
        movl      %esi, %ebp
        shrl      %cl, %ebp
..B1.21:
        orl       %edx, 36(%esp)
        orl       %ebp, 32(%esp)
        cmpl      $31, 112(%esp)
        jbe       ..B1.23
..B1.22:
        movl      %ebx, %esi
        xorl      %edx, %edx
        movl      %eax, %ecx
        shll      %cl, %esi
        jmp       ..B1.24
..B1.23:
        movl      %ebx, %edx
        movl      %eax, %ecx
        shldl     %cl, %ebx, %esi
        shll      %cl, %edx
..B1.24:
        cmpl      $31, 72(%esp)
        jbe       ..B1.26
..B1.25:
        movl      72(%esp), %ecx
        xorl      %ebp, %ebp
        movl      124(%esp), %ebx
        shrl      %cl, %ebx
        jmp       ..B1.27
..B1.26:
        movl      72(%esp), %ecx
        movl      120(%esp), %ebx
        movl      124(%esp), %ebp
        shrdl     %cl, %ebp, %ebx
        shrl      %cl, %ebp
..B1.27:
        orl       %edx, %ebx
        orl       %ebp, %esi
..B1.28:
        movl      %ebx, %eax
        movl      128(%esp), %ecx
        mull      %ecx
        movl      %ebx, %eax
        movl      %edx, %ebp
        mull      132(%esp)
        addl      %eax, %ebp
        movl      %ecx, %eax
        movl      %edx, %edi
        adcl      $0, %edi
        mull      %esi
        addl      %ebp, %eax
        movl      %edx, %ecx
        adcl      %edi, %ecx
        subl      %ebp, %eax
        movl      %ecx, %edx
        sbbl      %edi, %edx
        jae       ..B1.30
..B1.29:
        movl      $1, %ebp
        jmp       ..B1.31
..B1.30:
        xorl      %ebp, %ebp
..B1.31:
        movl      %esi, %eax
        movl      132(%esp), %edi
        mull      %edi
        addl      %edx, %ebp
        addl      %ecx, %eax
        movl      %esi, %ecx
        movl      %eax, 88(%esp)
        adcl      $0, %ebp
        movl      128(%esp), %eax
        imull     %ebx, %edi
        imull     %eax, %ecx
        mull      %ebx
        addl      %ecx, %edi
        movl      104(%esp), %ecx
        addl      %edi, %edx
        movl      %edx, 100(%esp)
        movl      %esi, %edi
        movl      %ecx, %edx
        movl      %ebp, 92(%esp)
        movl      108(%esp), %ebp
        imull     %ebp, %edi
        imull     %ebx, %edx
        movl      %eax, 96(%esp)
        movl      %ebx, %eax
        addl      %edx, %edi
        mull      %ebp
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 44(%esp)
        mull      %ebp
        movl      %ebx, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %ebp, %eax
        movl      %edx, %ebx
        adcl      $0, %ebx
        mull      %esi
        addl      %edi, %eax
        movl      92(%esp), %ebp
        adcl      %ebx, %edx
        subl      %edi, %eax
        movl      %edx, 48(%esp)
        sbbl      %ebx, %edx
        movl      88(%esp), %ebx
        jae       ..B1.33
..B1.32:
        movl      $1, %ecx
        jmp       ..B1.34
..B1.33:
        xorl      %ecx, %ecx
..B1.34:
        movl      %esi, %eax
        mull      104(%esp)
        movl      %eax, %esi
        addl      %edx, %ecx
        addl      48(%esp), %esi
        movl      40(%esp), %eax
        adcl      $0, %ecx
        addl      %eax, %ebx
        movl      %ebx, %edx
        movl      44(%esp), %edi
        adcl      %edi, %ebp
        subl      %eax, %edx
        movl      %ebp, %eax
        sbbl      %edi, %eax
        jae       ..B1.36
..B1.35:
        movl      $1, %eax
        jmp       ..B1.37
..B1.36:
        xorl      %eax, %eax
..B1.37:
        addl      %eax, %esi
        movl      %ebx, 88(%esp)
        movl      %esi, 84(%esp)
        adcl      $0, %ecx
        movl      132(%esp), %ebx
        movl      %ebx, %edi
        movl      32(%esp), %esi
        movl      %esi, %edx
        movl      %ebp, 92(%esp)
        movl      %ecx, 136(%esp)
        movl      36(%esp), %ecx
        movl      128(%esp), %ebp
        movl      %ebp, %eax
        imull     %ecx, %edi
        imull     %ebp, %edx
        addl      %edx, %edi
        mull      %ecx
        movl      %eax, 44(%esp)
        movl      %ebp, %eax
        addl      %edi, %edx
        movl      %edx, 48(%esp)
        mull      %ecx
        movl      %ecx, %eax
        movl      %edx, %edi
        mull      %ebx
        addl      %eax, %edi
        movl      %ebp, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %esi
        addl      %edi, %eax
        movl      84(%esp), %esi
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 52(%esp)
        movl      88(%esp), %ebx
        sbbl      %ecx, %edx
        movl      136(%esp), %ecx
        movl      92(%esp), %ebp
        jae       ..B1.39
..B1.38:
        movl      $1, %edi
        jmp       ..B1.40
..B1.39:
        xorl      %edi, %edi
..B1.40:
        movl      32(%esp), %eax
        mull      132(%esp)
        addl      %edx, %edi
        addl      52(%esp), %eax
        movl      %eax, 40(%esp)
        movl      44(%esp), %eax
        adcl      $0, %edi
        addl      %eax, %ebx
        movl      %ebx, %edx
        adcl      48(%esp), %ebp
        subl      %eax, %edx
        movl      %ebp, %eax
        sbbl      48(%esp), %eax
        jae       ..B1.42
..B1.41:
        movl      $1, 44(%esp)
        jmp       ..B1.43
..B1.42:
        movl      $0, 44(%esp)
..B1.43:
        movl      44(%esp), %eax
        addl      %eax, %esi
        movl      %esi, %edx
        adcl      $0, %ecx
        subl      %eax, %edx
        movl      $0, %eax
        movl      %ecx, %edx
        sbbl      %eax, %edx
        jae       ..B1.45
..B1.44:
        movl      $1, 52(%esp)
        jmp       ..B1.46
..B1.45:
        movl      $0, 52(%esp)
..B1.46:
        movl      40(%esp), %eax
        addl      %eax, %esi
        movl      %esi, %edx
        adcl      %edi, %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      %edi, %eax
        jae       ..B1.48
..B1.47:
        movl      $1, %eax
        jmp       ..B1.49
..B1.48:
        xorl      %eax, %eax
..B1.49:
        movl      %esi, 84(%esp)
        movl      %ebx, 88(%esp)
        movl      104(%esp), %ebx
        movl      %ebx, %edi
        movl      32(%esp), %esi
        movl      %esi, %edx
        movl      %ebp, 92(%esp)
        movl      %ecx, 136(%esp)
        movl      36(%esp), %ecx
        movl      108(%esp), %ebp
        imull     %ecx, %edi
        imull     %ebp, %edx
        addl      %eax, 52(%esp)
        movl      %ebp, %eax
        addl      %edx, %edi
        mull      %ecx
        movl      %eax, 40(%esp)
        movl      %ebp, %eax
        addl      %edi, %edx
        movl      %edx, 44(%esp)
        mull      %ecx
        movl      %ecx, %eax
        movl      %edx, %edi
        mull      %ebx
        addl      %eax, %edi
        movl      %ebp, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %esi
        addl      %edi, %eax
        movl      88(%esp), %ebx
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 48(%esp)
        movl      84(%esp), %esi
        sbbl      %ecx, %edx
        movl      136(%esp), %ecx
        movl      92(%esp), %ebp
        jae       ..B1.51
..B1.50:
        movl      $1, %edi
        jmp       ..B1.52
..B1.51:
        xorl      %edi, %edi
..B1.52:
        movl      32(%esp), %eax
        mull      104(%esp)
        addl      %edx, %edi
        addl      48(%esp), %eax
        movl      %eax, 36(%esp)
        movl      40(%esp), %eax
        adcl      $0, %edi
        addl      %eax, %esi
        movl      %esi, %edx
        adcl      44(%esp), %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      44(%esp), %eax
        jae       ..B1.54
..B1.53:
        movl      $1, %eax
        jmp       ..B1.55
..B1.54:
        xorl      %eax, %eax
..B1.55:
        movl      52(%esp), %edx
        addl      36(%esp), %edx
        movl      %ebp, 92(%esp)
        adcl      $0, %edi
        addl      %eax, %edx
        movl      %ecx, 136(%esp)
        movl      132(%esp), %ecx
        adcl      $0, %edi
        movl      64(%esp), %ebp
        movl      %esi, 84(%esp)
        movl      %ecx, %esi
        movl      %edx, 52(%esp)
        movl      %ebp, %edx
        movl      %ebx, 88(%esp)
        movl      %edi, 48(%esp)
        movl      68(%esp), %edi
        movl      128(%esp), %ebx
        movl      %ebx, %eax
        imull     %edi, %esi
        imull     %ebx, %edx
        addl      %edx, %esi
        mull      %edi
        movl      %eax, 32(%esp)
        movl      %ebx, %eax
        addl      %esi, %edx
        movl      %edx, 40(%esp)
        mull      %edi
        movl      %edi, %eax
        movl      %edx, %esi
        mull      %ecx
        addl      %eax, %esi
        movl      %ebx, %eax
        movl      %edx, %edi
        adcl      $0, %edi
        mull      %ebp
        addl      %esi, %eax
        movl      88(%esp), %ebx
        adcl      %edi, %edx
        subl      %esi, %eax
        movl      %edx, 44(%esp)
        movl      136(%esp), %ecx
        sbbl      %edi, %edx
        movl      48(%esp), %edi
        movl      84(%esp), %esi
        movl      92(%esp), %ebp
        jae       ..B1.57
..B1.56:
        movl      $1, 48(%esp)
        jmp       ..B1.58
..B1.57:
        movl      $0, 48(%esp)
..B1.58:
        movl      64(%esp), %eax
        mull      132(%esp)
        movl      %ebx, 88(%esp)
        movl      48(%esp), %ebx
        addl      %edx, %ebx
        addl      44(%esp), %eax
        movl      %eax, 36(%esp)
        movl      32(%esp), %eax
        adcl      $0, %ebx
        addl      %eax, %esi
        movl      %esi, %edx
        movl      %ebx, 48(%esp)
        movl      40(%esp), %ebx
        adcl      %ebx, %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      %ebx, %eax
        movl      88(%esp), %ebx
        jae       ..B1.60
..B1.59:
        movl      $1, 32(%esp)
        jmp       ..B1.61
..B1.60:
        movl      $0, 32(%esp)
..B1.61:
        movl      %esi, 84(%esp)
        movl      %ebx, 88(%esp)
        movl      104(%esp), %esi
        movl      68(%esp), %ebx
        movl      108(%esp), %eax
        movl      64(%esp), %edx
        imull     %ebx, %esi
        imull     %eax, %edx
        addl      %edx, %esi
        mull      %ebx
        movl      %ecx, 136(%esp)
        movl      %eax, %ecx
        movl      %edx, %ebx
        addl      %esi, %ebx
        addl      52(%esp), %ecx
        movl      60(%esp), %esi
        movl      56(%esp), %edx
        adcl      %edi, %ebx
        movl      132(%esp), %edi
        movl      128(%esp), %eax
        imull     %esi, %edi
        imull     %eax, %edx
        addl      %edx, %edi
        mull      %esi
        addl      36(%esp), %ecx
        adcl      48(%esp), %ebx
        addl      %edi, %edx
        addl      32(%esp), %ecx
        adcl      $0, %ebx
        addl      %ecx, %eax
        movl      180(%esp), %ecx
        movl      %ecx, %edi
        adcl      %ebx, %edx
        negl      %edi
        cmpl      $0, 80(%esp)
        movl      %eax, 140(%esp)
        jne       ..L3
        movl      %ecx, %edi
..L3:
        xorl      %eax, %eax
        shll      $29, %edi
        addl      %edi, %edx
        movl      %eax, 44(%esp)
        movl      88(%esp), %ebx
        movl      %edx, 152(%esp)
        movl      %eax, 40(%esp)
        jmp       ..B1.62
..B1.96:
        addl      $64, 44(%esp)
        movl      152(%esp), %ecx
        movl      136(%esp), %edx
        movl      84(%esp), %eax
        movl      32(%esp), %esi
        movl      36(%esp), %edi
        movl      %ebx, 84(%esp)
        movl      %ebp, 136(%esp)
        movl      96(%esp), %ebx
        movl      100(%esp), %ebp
        adcl      $0, 40(%esp)
        andl      $-536870912, %ecx
        andl      $536870911, %edx
        orl       %edx, %ecx
        movl      %eax, 140(%esp)
        movl      %ecx, 152(%esp)
        movl      %esi, 96(%esp)
        movl      %edi, 100(%esp)
..B1.62:
        movl      152(%esp), %eax
        xorl      %edx, %edx
        lea       4194304(%eax), %ecx
        andl      $1065353216, %ecx
        orl       %edx, %ecx
        jne       ..B1.119
..B1.63:
        movl      116(%esp), %eax
        movl      120(%esp), %esi
        movl      124(%esp), %edi
        movl      (%eax), %edx
        movl      %edx, 120(%esp)
        xorl      %edx, %edx
        movl      4(%eax), %ecx
        addl      $8, %eax
        movl      %ecx, 124(%esp)
        movl      %eax, 116(%esp)
        orl       112(%esp), %edx
        je        ..B1.71
..B1.64:
        cmpl      $31, 112(%esp)
        jbe       ..B1.66
..B1.65:
        movl      %esi, %edi
        xorl      %edx, %edx
        movl      112(%esp), %ecx
        shll      %cl, %edi
        jmp       ..B1.67
..B1.66:
        movl      %esi, %edx
        movl      112(%esp), %ecx
        shldl     %cl, %esi, %edi
        shll      %cl, %edx
..B1.67:
        cmpl      $31, 72(%esp)
        jbe       ..B1.69
..B1.68:
        movl      72(%esp), %ecx
        xorl      %eax, %eax
        movl      124(%esp), %esi
        shrl      %cl, %esi
        jmp       ..B1.70
..B1.69:
        movl      72(%esp), %ecx
        movl      120(%esp), %esi
        movl      124(%esp), %eax
        shrdl     %cl, %eax, %esi
        shrl      %cl, %eax
..B1.70:
        orl       %edx, %esi
        orl       %eax, %edi
..B1.71:
        movl      132(%esp), %ecx
        movl      %edi, %edx
        movl      %ebx, 88(%esp)
        movl      %ecx, %ebx
        movl      %ebp, 92(%esp)
        movl      128(%esp), %ebp
        movl      %ebp, %eax
        imull     %esi, %ebx
        imull     %ebp, %edx
        addl      %edx, %ebx
        mull      %esi
        movl      %eax, 32(%esp)
        movl      %ebp, %eax
        addl      %ebx, %edx
        movl      %edx, 36(%esp)
        mull      %esi
        movl      %esi, %eax
        movl      %edx, %ebx
        mull      %ecx
        addl      %eax, %ebx
        movl      %ebp, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %edi
        addl      %ebx, %eax
        movl      92(%esp), %ebp
        adcl      %ecx, %edx
        subl      %ebx, %eax
        movl      %edx, 56(%esp)
        movl      88(%esp), %ebx
        sbbl      %ecx, %edx
        jae       ..B1.73
..B1.72:
        movl      $1, %ecx
        jmp       ..B1.74
..B1.73:
        xorl      %ecx, %ecx
..B1.74:
        movl      132(%esp), %eax
        mull      %edi
        addl      %edx, %ecx
        addl      56(%esp), %eax
        movl      96(%esp), %edx
        adcl      $0, %ecx
        addl      %eax, %edx
        movl      %ebx, 88(%esp)
        movl      100(%esp), %ebx
        adcl      %ecx, %ebx
        movl      %edx, 96(%esp)
        subl      %eax, %edx
        movl      %ebx, 100(%esp)
        sbbl      %ecx, %ebx
        movl      88(%esp), %ebx
        jae       ..B1.76
..B1.75:
        movl      $1, %ecx
        jmp       ..B1.77
..B1.76:
        xorl      %ecx, %ecx
..B1.77:
        movl      104(%esp), %edx
        addl      %ecx, %ebx
        movl      108(%esp), %eax
        movl      %edi, 64(%esp)
        adcl      $0, %ebp
        imull     %esi, %edx
        imull     %eax, %edi
        addl      %edi, %edx
        movl      %ebx, %edi
        movl      %edx, 68(%esp)
        mull      %esi
        addl      68(%esp), %edx
        subl      %ecx, %edi
        movl      $0, %ecx
        movl      %edx, 52(%esp)
        movl      %ebp, %edx
        movl      %eax, 48(%esp)
        sbbl      %ecx, %edx
        movl      64(%esp), %edi
        jae       ..B1.80
..B1.78:
        movl      $1, %ecx
..B1.80:
        movl      108(%esp), %eax
        mull      %esi
        movl      %esi, %eax
        movl      %ebx, 88(%esp)
        movl      %edx, %ebx
        mull      104(%esp)
        addl      %eax, %ebx
        movl      %edx, %esi
        movl      108(%esp), %eax
        adcl      $0, %esi
        mull      %edi
        addl      %ebx, %eax
        adcl      %esi, %edx
        subl      %ebx, %eax
        movl      %edx, 60(%esp)
        movl      88(%esp), %ebx
        sbbl      %esi, %edx
        jae       ..B1.82
..B1.81:
        movl      $1, %esi
        jmp       ..B1.83
..B1.82:
        xorl      %esi, %esi
..B1.83:
        movl      104(%esp), %eax
        mull      %edi
        addl      %edx, %esi
        addl      60(%esp), %eax
        movl      %eax, 144(%esp)
        movl      48(%esp), %edx
        adcl      $0, %esi
        movl      96(%esp), %eax
        addl      %edx, %eax
        movl      %esi, 148(%esp)
        movl      52(%esp), %esi
        movl      100(%esp), %edi
        adcl      %esi, %edi
        movl      %eax, 96(%esp)
        subl      %edx, %eax
        movl      %edi, 100(%esp)
        movl      144(%esp), %eax
        sbbl      %esi, %edi
        movl      148(%esp), %esi
        jae       ..B1.85
..B1.84:
        movl      $1, %edx
        jmp       ..B1.86
..B1.85:
        xorl      %edx, %edx
..B1.86:
        addl      %edx, %ebx
        movl      %ebx, %edi
        adcl      $0, %ebp
        subl      %edx, %edi
        movl      $0, %edx
        movl      %ebp, %edi
        sbbl      %edx, %edi
        jae       ..B1.89
..B1.87:
        movl      $1, %edx
..B1.89:
        addl      %eax, %ebx
        adcl      %esi, %ebp
        addl      %edx, %ecx
        movl      %ebx, %edx
        subl      %eax, %edx
        movl      %ebp, %eax
        sbbl      %esi, %eax
        jae       ..B1.91
..B1.90:
        movl      $1, %eax
        jmp       ..B1.92
..B1.91:
        xorl      %eax, %eax
..B1.92:
        addl      %eax, %ecx
        xorl      %eax, %eax
        orl       %eax, %ecx
        je        ..B1.95
..B1.93:
        movl      84(%esp), %eax
        addl      $1, %eax
        movl      136(%esp), %edx
        adcl      $0, %edx
        movl      %eax, 84(%esp)
        orl       %edx, %eax
        movl      %edx, 136(%esp)
        jne       ..B1.95
..B1.94:
        addl      $1, 140(%esp)
        adcl      $0, 152(%esp)
..B1.95:
        movl      140(%esp), %ecx
        movl      %ecx, %eax
        movl      152(%esp), %esi
        shrl      $23, %eax
        shll      $9, %esi
        orl       %esi, %eax
        movl      136(%esp), %edx
        movl      %eax, %edi
        shrl      $23, %edx
        shll      $9, %ecx
        sarl      $31, %edi
        orl       %ecx, %edx
        xorl      %edi, %edx
        xorl      %edi, %eax
        orl       %eax, %edx
        je        ..B1.96
..B1.97:
        movl      136(%esp), %ecx
        movl      84(%esp), %esi
        movl      152(%esp), %edx
..B1.98:
        movl      %ebx, 88(%esp)
        lea       (,%edx,4), %edi
        movl      140(%esp), %ebx
        movl      %ebx, %eax
        sarl      $2, %edi
        subl      %ebx, %eax
        movl      %eax, 32(%esp)
        movl      %edi, %eax
        sbbl      %edi, %edx
        sarl      $31, %eax
        subl      %eax, %ebx
        negl      %eax
        addl      %edi, %eax
        movl      %edi, 36(%esp)
        orl       %eax, %ebx
        movl      88(%esp), %ebx
        jne       ..B1.100
..B1.99:
        movl      %esi, 140(%esp)
        movl      %ebx, %esi
        movl      %ecx, 36(%esp)
        movl      %ebp, %ecx
        movl      96(%esp), %ebx
        movl      100(%esp), %ebp
        addl      $64, 44(%esp)
..B1.100:
        xorl      %eax, %eax
        subl      %eax, %edi
        jge       ..B1.103
..B1.102:
        movl      $-2147483648, %eax
..B1.103:
        testl     %eax, %eax
        je        ..B1.111
..B1.104:
        notl      %ebx
        notl      %ebp
        addl      $1, %ebx
        notl      %esi
        movl      %ebx, %edi
        notl      %ecx
        adcl      $0, %ebp
        notl      140(%esp)
        orl       %ebp, %edi
        notl      36(%esp)
        jne       ..B1.106
..B1.105:
        movl      $1, %edi
        jmp       ..B1.107
..B1.106:
        xorl      %edi, %edi
..B1.107:
        addl      %edi, %esi
        movl      %esi, %edi
        adcl      $0, %ecx
        orl       %ecx, %edi
        jne       ..B1.109
..B1.108:
        movl      $1, %edi
        jmp       ..B1.110
..B1.109:
        xorl      %edi, %edi
..B1.110:
        addl      %edi, 140(%esp)
        adcl      $0, 36(%esp)
..B1.111:
        movl      %ebp, 92(%esp)
        xorl      %edi, %edi
        movl      %ebx, 88(%esp)
        addl      $12, %esp
        pushl     %edi
        pushl     %edi
        movl      184(%esp), %ebx
        xorl      %ebp, %ebp
        pushl     %ebx
        subl      32(%esp), %ebp
        movl      80(%esp), %ebp
        sbbl      %edx, %edi
        testl     %ebp, %ebp
        movl      $3, 4(%ebx)
        jne       ..L4
        movl      %edx, %edi
..L4:
        xorl      %ebp, %eax
        movl      %eax, (%ebx)
        movl      140(%esp), %eax
        movl      36(%esp), %edx
        movl      %eax, 8(%ebx)
        movl      %edx, 12(%ebx)
        movl      %esi, 16(%ebx)
        movl      %ecx, 20(%ebx)
        movl      %edi, 40(%esp)
        movl      88(%esp), %ebx
        movl      92(%esp), %ebp
        call      __dpml_ffs_and_shift__
..B1.112:
        movl      188(%esp), %ecx
        movl      4(%ecx), %eax
        movl      %eax, %ecx
        cltd      
        addl      $-3, %ecx
        adcl      $-1, %edx
        orl       %ecx, %edx
        je        ..B1.117
..B1.113:
        movl      188(%esp), %esi
        addl      $64, %ecx
        cmpl      $31, %ecx
        movl      16(%esi), %edx
        movl      20(%esi), %esi
        jbe       ..B1.115
..B1.114:
        movl      %ebp, %ebx
        xorl      %edi, %edi
        shrl      %cl, %ebx
        jmp       ..B1.116
..B1.115:
        movl      %ebp, %edi
        shrdl     %cl, %ebp, %ebx
        shrl      %cl, %edi
..B1.116:
        movl      188(%esp), %ecx
        orl       %ebx, %edx
        orl       %edi, %esi
        movl      %edx, 16(%ecx)
        movl      %esi, 20(%ecx)
..B1.117:
        addl      $12, %esp
        movl      176(%esp), %ebx
        pushl     %ebx
        movl      68(%esp), %edx
        lea       1008+__trig_x_table@GOTOFF(%edx), %ecx
        pushl     %ecx
        pushl     %ebx
        subl      44(%esp), %eax
        movl      %eax, 4(%ebx)
        call      __dpml_multiply__
..B1.118:
        movl      40(%esp), %eax
        xorl      %edx, %edx
        shrl      $30, %eax
        addl      $156, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.119:
        movl      136(%esp), %ecx
        movl      84(%esp), %esi
        movl      %eax, %edx
        jmp       ..B1.98
..B1.120:
        movl      %ebx, %eax
        sarl      $31, %eax
        movl      180(%esp), %ecx
        cltd      
        addl      %ecx, %eax
        movl      %eax, %ebp
        movl      184(%esp), %ebx
        adcl      %ebx, %edx
        andl      $1, %ebp
        addl      %ebp, %eax
        movl      %eax, %edi
        adcl      $0, %edx
        subl      %eax, %ecx
        movl      %edx, %ebp
        movl      %edx, %esi
        sbbl      %edx, %ebx
        shll      $31, %ebp
        shrl      $1, %edi
        sarl      $1, %esi
        orl       %edi, %ebp
        movl      %esi, 32(%esp)
        orl       %ebx, %ecx
        movl      %ebp, 36(%esp)
        je        ..B1.125
..B1.121:
        xorl      %eax, %eax
        subl      %eax, %ebx
        jge       ..B1.124
..B1.123:
        movl      $1, %eax
..B1.124:
        addl      $20, %esp
        pushl     168(%esp)
        pushl     $0
        pushl     %eax
        movl      68(%esp), %eax
        lea       1008+__trig_x_table@GOTOFF(%eax), %edx
        pushl     %edx
        pushl     172(%esp)
        call      __dpml_addsub__
        jmp       ..B1.126
..B1.125:
        movl      188(%esp), %eax
        movl      176(%esp), %esi
        movl      108(%esp), %ebx
        movl      104(%esp), %ebp
        movl      80(%esp), %edx
        movl      %ebx, 8(%eax)
        movl      %ebp, 12(%eax)
        movl      %edx, (%eax)
        movl      112(%esp), %ecx
        movl      16(%esi), %edi
        movl      20(%esi), %edx
        movl      %ecx, 4(%eax)
        movl      %edi, 16(%eax)
        movl      %edx, 20(%eax)
..B1.126:
        movl      36(%esp), %eax
        movl      32(%esp), %edx
        addl      $156, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_ux_radian_reduce__,@function
	.size	__dpml_ux_radian_reduce__,.-__dpml_ux_radian_reduce__
	.data
# -- End  __dpml_ux_radian_reduce__
	.text
# -- Begin  __dpml_ux_degree_reduce__
	.text
       .align    16,0x90
__dpml_ux_degree_reduce__:
# parameter 1: 128 + %esp
# parameter 2: 132 + %esp
# parameter 3: 140 + %esp
..B2.1:
..L5:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $108, %esp
        movl      128(%esp), %ecx
        call      ..L6
..L6:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L6](%eax), %eax
        movl      (%ecx), %edx
        movl      4(%ecx), %ebx
        cmpl      $142, %ebx
        movl      %eax, 52(%esp)
        movl      %edx, 48(%esp)
        jle       ..B2.6
..B2.2:
        movl      %eax, %ebp
        lea       32641(%ebx), %esi
        movl      %esi, %eax
        cltd      
        movl      %edx, %ecx
        mull      264+__trig_x_table@GOTOFF(%ebp)
        movl      %esi, %eax
        movl      268+__trig_x_table@GOTOFF(%ebp), %edi
        movl      %edx, %ebp
        mull      %edi
        movl      %edx, %esi
        addl      %eax, %ebp
        movl      52(%esp), %edx
        adcl      $0, %esi
        movl      264+__trig_x_table@GOTOFF(%edx), %eax
        mull      %ecx
        addl      %ebp, %eax
        adcl      %esi, %edx
        subl      %ebp, %eax
        movl      %edx, 16(%esp)
        sbbl      %esi, %edx
..B2.5:
        movl      %edi, %eax
        mull      %ecx
        addl      16(%esp), %eax
        movl      128(%esp), %edi
        lea       (,%eax,8), %ebp
        lea       (%ebp,%eax,4), %esi
        negl      %esi
        lea       32772(%esi,%ebx), %ebx
        movl      %ebx, 4(%edi)
..B2.6:
        cmpl      $16, %ebx
        jl        ..B2.61
..B2.7:
        movl      %ebx, %eax
        movl      %ebx, %ebp
        cltd      
        addl      $-15, %ebp
        movl      %edx, %esi
        movl      %ebp, %ecx
        adcl      $-1, %esi
        xorl      %edx, %edx
        andl      $63, %ecx
        movl      %esi, %edi
        subl      %ecx, %ebx
        shll      $26, %edi
        shrl      $6, %ebp
        sarl      $6, %esi
        orl       %ebp, %edi
        movl      %edi, 56(%esp)
        orl       %ecx, %edx
        je        ..B2.21
..B2.8:
        movl      128(%esp), %edx
        movl      %ecx, %edi
        negl      %edi
        addl      $64, %edi
        movl      20(%edx), %ebp
        cmpl      $31, %ecx
        movl      %edi, 28(%esp)
        movl      16(%edx), %edi
        movl      %ebp, 16(%esp)
        jbe       ..B2.10
..B2.9:
        movl      %edi, %edx
        xorl      %ebp, %ebp
        shll      %cl, %edx
        jmp       ..B2.11
..B2.10:
        movl      %edi, %ebp
        movl      16(%esp), %edx
        shldl     %cl, %edi, %edx
        shll      %cl, %ebp
..B2.11:
        movl      128(%esp), %eax
        cmpl      $31, %ecx
        movl      %ebp, 16(%eax)
        movl      12(%eax), %ebp
        movl      %edx, 20(%eax)
        movl      8(%eax), %edx
        movl      %ebp, 24(%esp)
        jbe       ..B2.13
..B2.12:
        movl      %edx, %ebp
        movl      $0, 32(%esp)
        shll      %cl, %ebp
        jmp       ..B2.14
..B2.13:
        movl      %edx, %eax
        shll      %cl, %eax
        shldl     %cl, %edx, %ebp
        movl      %eax, 32(%esp)
..B2.14:
        cmpl      $31, 28(%esp)
        jbe       ..B2.16
..B2.15:
        movl      28(%esp), %ecx
        movl      16(%esp), %edi
        movl      $0, 20(%esp)
        shrl      %cl, %edi
        jmp       ..B2.17
..B2.16:
        movl      28(%esp), %ecx
        movl      16(%esp), %eax
        shrdl     %cl, %eax, %edi
        shrl      %cl, %eax
        movl      %eax, 20(%esp)
..B2.17:
        movl      32(%esp), %eax
        orl       %edi, %eax
        movl      128(%esp), %edi
        orl       20(%esp), %ebp
        cmpl      $31, 28(%esp)
        movl      %eax, 8(%edi)
        movl      %ebp, 12(%edi)
        jbe       ..B2.19
..B2.18:
        xorl      %ebp, %ebp
        movl      24(%esp), %edx
        shrl      %cl, %edx
        jmp       ..B2.22
..B2.19:
        movl      24(%esp), %ebp
        shrdl     %cl, %ebp, %edx
        shrl      %cl, %ebp
        jmp       ..B2.22
..B2.21:
        xorl      %edx, %edx
        xorl      %ebp, %ebp
..B2.22:
        movl      128(%esp), %edi
        movl      %esi, %ecx
        movl      %edx, (%edi)
        movl      56(%esp), %edx
        movl      %ebp, 4(%edi)
        movl      8(%edi,%edx,8), %eax
        movl      12(%edi,%edx,8), %edi
        movl      %edi, %ebp
        xorl      %edx, %edx
        shrl      $20, %ebp
        subl      %edx, %ecx
        jl        ..B2.55
..B2.23:
        movl      56(%esp), %edi
        addl      $1, %edi
        movl      %edi, 32(%esp)
        adcl      $0, %esi
        movl      %esi, %eax
        movl      %edi, 72(%esp)
        testl     %eax, %eax
        jge       ..B2.25
..B2.24:
        addl      $1, 72(%esp)
        adcl      $0, %eax
..B2.25:
        movl      72(%esp), %edi
        shrdl     $1, %eax, %edi
        sarl      $1, %eax
        movl      %edi, 72(%esp)
        xorl      %edi, %edi
        movl      %eax, %ecx
        movl      %edi, 28(%esp)
        subl      %edi, %ecx
        movl      %edi, 24(%esp)
        jb        ..B2.104
..B2.107:
        orl       72(%esp), %ecx
        je        ..B2.104
..B2.26:
        xorl      %ecx, %ecx
        movl      %ecx, 64(%esp)
        movl      %ecx, 60(%esp)
        movl      %eax, 76(%esp)
        movl      %edi, 68(%esp)
        movl      %ebx, 20(%esp)
        movl      %esi, 16(%esp)
        movl      %edi, 80(%esp)
        movl      28(%esp), %ecx
        movl      24(%esp), %eax
..B2.27:
        movl      56(%esp), %edi
        movl      64(%esp), %ebx
        movl      128(%esp), %esi
        addl      %edi, %ebx
        movl      %ebx, 84(%esp)
        movl      (%esi,%ebx,8), %edi
        movl      %edi, 100(%esp)
        movl      4(%esi,%ebx,8), %edi
        movl      %edi, 88(%esp)
        xorl      %edi, %edi
        movl      %edi, (%esi,%ebx,8)
        movl      %edi, 4(%esi,%ebx,8)
        movl      %ecx, %esi
        movl      100(%esp), %ebx
        orl       %eax, %esi
        je        ..B2.35
..B2.28:
        cmpl      $31, %ecx
        jbe       ..B2.30
..B2.29:
        xorl      %esi, %esi
        jmp       ..B2.31
..B2.30:
        movl      %ebx, %esi
        shll      %cl, %esi
..B2.31:
        andl      $4095, %esi
        xorl      %edi, %edi
        addl      %esi, %ebp
        adcl      $0, %edx
        movl      $12, %esi
        subl      %ecx, %esi
        sbbl      %eax, %edi
        movl      %edi, 36(%esp)
        cmpl      $31, %esi
        jbe       ..B2.33
..B2.32:
        movl      %esi, %ecx
        xorl      %eax, %eax
        movl      88(%esp), %ebx
        shrl      %cl, %ebx
        jmp       ..B2.34
..B2.33:
        movl      %esi, %ecx
        movl      88(%esp), %eax
        shrdl     %cl, %eax, %ebx
        shrl      %cl, %eax
..B2.34:
        movl      %eax, 88(%esp)
        xorl      %eax, %eax
        xorl      %ecx, %ecx
        subl      %esi, %ecx
        sbbl      36(%esp), %eax
..B2.35:
        movl      88(%esp), %edi
        movl      %edi, %esi
        shrl      $28, %edi
        andl      $268435455, %esi
        addl      %edi, %ebx
        adcl      $0, %esi
        addl      %ebp, %ebx
        adcl      %edx, %esi
        addl      $4, %ecx
        movl      %esi, 96(%esp)
        movl      128(%esp), %esi
        adcl      $0, %eax
        movl      84(%esp), %edx
        movl      -4(%esi,%edx,8), %edi
        movl      -8(%esi,%edx,8), %ebp
        movl      %edi, 92(%esp)
        xorl      %edi, %edi
        movl      %edi, -8(%esi,%edx,8)
        movl      %edi, -4(%esi,%edx,8)
        movl      %ecx, %edx
        orl       %eax, %edx
        je        ..B2.43
..B2.36:
        cmpl      $31, %ecx
        jbe       ..B2.38
..B2.37:
        movl      $0, 44(%esp)
        jmp       ..B2.39
..B2.38:
        movl      %ebp, %edx
        shll      %cl, %edx
        movl      %edx, 44(%esp)
..B2.39:
        movl      44(%esp), %edx
        xorl      %esi, %esi
        andl      $4095, %edx
        addl      %edx, %ebx
        adcl      $0, 96(%esp)
        movl      $12, %edx
        subl      %ecx, %edx
        sbbl      %eax, %esi
        movl      %esi, 40(%esp)
        cmpl      $31, %edx
        jbe       ..B2.41
..B2.40:
        movl      %edx, %ecx
        xorl      %eax, %eax
        movl      92(%esp), %ebp
        shrl      %cl, %ebp
        jmp       ..B2.42
..B2.41:
        movl      %edx, %ecx
        movl      92(%esp), %eax
        shrdl     %cl, %eax, %ebp
        shrl      %cl, %eax
..B2.42:
        movl      %eax, 92(%esp)
        xorl      %eax, %eax
        xorl      %ecx, %ecx
        subl      %edx, %ecx
        sbbl      40(%esp), %eax
..B2.43:
        movl      92(%esp), %esi
        movl      %esi, %edx
        shrl      $28, %esi
        andl      $268435455, %edx
        addl      %esi, %ebp
        movl      68(%esp), %edi
        adcl      $0, %edx
        addl      %ebx, %ebp
        movl      80(%esp), %ebx
        adcl      96(%esp), %edx
        addl      $4, %ecx
        adcl      $0, %eax
        addl      $-2, 64(%esp)
        adcl      $-1, 60(%esp)
        addl      $1, %ebx
        movl      %ebx, 80(%esp)
        adcl      $0, %edi
        subl      72(%esp), %ebx
        movl      %edi, 68(%esp)
        sbbl      76(%esp), %edi
        jb        ..B2.27
..B2.44:
        movl      80(%esp), %edi
        movl      %eax, 24(%esp)
        movl      %edi, %eax
        movl      %ecx, 28(%esp)
        addl      %edi, %edi
        movl      68(%esp), %ecx
        shrl      $31, %eax
        addl      %ecx, %ecx
        orl       %ecx, %eax
        addl      $1, %edi
        movl      20(%esp), %ebx
        movl      16(%esp), %esi
        adcl      $0, %eax
..B2.45:
        movl      %edi, %ecx
        addl      $-1, %ecx
        adcl      $-1, %eax
        subl      %ecx, 32(%esp)
        sbbl      %eax, %esi
        jb        ..B2.103
..B2.108:
        movl      32(%esp), %eax
        orl       %esi, %eax
        je        ..B2.103
..B2.46:
        negl      %edi
        xorl      %ecx, %ecx
        addl      56(%esp), %edi
        movl      128(%esp), %eax
        movl      8(%eax,%edi,8), %esi
        movl      %esi, 16(%esp)
        movl      12(%eax,%edi,8), %esi
        movl      %ecx, 8(%eax,%edi,8)
        movl      %ecx, 12(%eax,%edi,8)
        movl      24(%esp), %edi
        orl       28(%esp), %edi
        je        ..B2.54
..B2.47:
        cmpl      $31, 28(%esp)
        jbe       ..B2.49
..B2.48:
        xorl      %edi, %edi
        jmp       ..B2.50
..B2.49:
        movl      28(%esp), %ecx
        movl      16(%esp), %edi
        shll      %cl, %edi
..B2.50:
        andl      $4095, %edi
        addl      %edi, %ebp
        movl      28(%esp), %edi
        adcl      $0, %edx
        negl      %edi
        addl      $12, %edi
        movl      %edi, 28(%esp)
        cmpl      $31, %edi
        jbe       ..B2.52
..B2.51:
        movl      %edi, %ecx
        xorl      %edi, %edi
        shrl      %cl, %esi
        movl      %esi, 16(%esp)
        jmp       ..B2.53
..B2.52:
        movl      %edi, %ecx
        movl      16(%esp), %edi
        shrdl     %cl, %esi, %edi
        movl      %edi, 16(%esp)
        movl      %esi, %edi
        shrl      %cl, %edi
..B2.53:
        movl      %edi, %esi
..B2.54:
        movl      %esi, %eax
        shrl      $28, %esi
        andl      $268435455, %eax
        movl      16(%esp), %edi
        addl      %esi, %edi
        movl      128(%esp), %esi
        adcl      $0, %eax
        addl      %edi, %ebp
        movl      56(%esp), %edi
        adcl      %eax, %edx
        movl      8(%esi,%edi,8), %eax
        movl      12(%esi,%edi,8), %edi
..B2.55:
        movl      %edx, %esi
        movl      %ebp, %ecx
        shll      $20, %esi
        shrl      $12, %ecx
        orl       %ecx, %esi
        shrl      $12, %edx
        movl      %esi, %ecx
        orl       %edx, %ecx
        je        ..B2.59
..B2.57:
        andl      $4095, %ebp
        addl      %esi, %ebp
        movl      %ebp, %ecx
        adcl      $0, %edx
        movl      %edx, %esi
        shll      $20, %esi
        shrl      $12, %ecx
        orl       %ecx, %esi
        shrl      $12, %edx
        movl      %esi, %ecx
        orl       %edx, %ecx
        jne       ..B2.57
..B2.59:
        addl      $12, %esp
        xorl      %esi, %esi
        pushl     %esi
        pushl     %esi
        movl      124(%esp), %edx
        andl      $1048575, %edi
        pushl     %edx
        shll      $20, %ebp
        orl       %ebp, %edi
        movl      56(%esp), %ebp
        movl      %ebx, 4(%edx)
        movl      %eax, 8(%edx,%ebp,8)
        movl      %edi, 12(%edx,%ebp,8)
        call      __dpml_ffs_and_shift__
..B2.60:
        subl      %eax, %ebx
..B2.61:
        movl      %ebx, %eax
        addl      $-5, %ebx
        cltd      
        movl      128(%esp), %esi
        adcl      $-1, %edx
        xorl      %edi, %edi
        subl      %edi, %edx
        movl      %edi, (%esi)
        movl      8(%esi), %ebp
        movl      12(%esi), %esi
        jl        ..B2.66
..B2.109:
        orl       %ebx, %edx
        je        ..B2.66
..B2.62:
        movl      52(%esp), %edi
        movl      %ebp, %eax
        mull      256+__trig_x_table@GOTOFF(%edi)
        movl      %ebp, %eax
        movl      260+__trig_x_table@GOTOFF(%edi), %ecx
        movl      %edx, %edi
        mull      %ecx
        movl      %ecx, 16(%esp)
        movl      %edx, %ecx
        movl      52(%esp), %edx
        addl      %eax, %edi
        adcl      $0, %ecx
        movl      256+__trig_x_table@GOTOFF(%edx), %eax
        mull      %esi
        addl      %edi, %eax
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 20(%esp)
        sbbl      %ecx, %edx
        jae       ..B2.64
..B2.63:
        movl      $1, 28(%esp)
        jmp       ..B2.65
..B2.64:
        movl      $0, 28(%esp)
..B2.65:
        movl      16(%esp), %eax
        mull      %esi
        movl      28(%esp), %edi
        addl      %edx, %edi
        addl      20(%esp), %eax
        movl      %eax, 24(%esp)
        adcl      $0, %edi
        movl      %edi, 28(%esp)
        jmp       ..B2.67
..B2.66:
        xorl      %edx, %edx
        movl      $1, %ebx
        movl      %edx, 24(%esp)
        movl      %edx, 28(%esp)
..B2.67:
        negl      %ebx
        movl      132(%esp), %eax
        andl      $1, %eax
        incl      %eax
        lea       64(%ebx), %edx
        addl      $63, %ebx
        movl      %edx, 16(%esp)
        cmpl      $31, %ebx
        jbe       ..B2.69
..B2.68:
        movl      %ebx, %ecx
        movl      %eax, %edi
        shll      %cl, %edi
        xorl      %edx, %edx
        jmp       ..B2.70
..B2.69:
        movl      %ebx, %ecx
        xorl      %edi, %edi
        movl      %eax, %edx
        shldl     %cl, %eax, %edi
        shll      %cl, %edx
..B2.70:
        addl      24(%esp), %edx
        adcl      28(%esp), %edi
        cmpl      $31, 16(%esp)
        jbe       ..B2.72
..B2.71:
        movl      $1, %ebx
        xorl      %eax, %eax
        movl      16(%esp), %ecx
        shll      %cl, %ebx
        jmp       ..B2.73
..B2.72:
        xorl      %ebx, %ebx
        movl      $1, %eax
        movl      16(%esp), %ecx
        shldl     %cl, %eax, %ebx
        shll      %cl, %eax
..B2.73:
        addl      $-1, %eax
        notl      %eax
        adcl      $-1, %ebx
        andl      %eax, %edx
        notl      %ebx
        andl      %ebx, %edi
        cmpl      $31, 16(%esp)
        jbe       ..B2.75
..B2.74:
        movl      %edi, %eax
        shrl      %cl, %eax
        jmp       ..B2.76
..B2.75:
        movl      %edx, %eax
        shrdl     %cl, %edi, %eax
..B2.76:
        movl      %eax, %ebx
        negl      %ebx
        cmpl      $0, 48(%esp)
        jne       ..L7
        movl      %eax, %ebx
..L7:
        movl      132(%esp), %eax
        shrl      $1, %eax
        addl      %ebx, %eax
        movl      %eax, 24(%esp)
        movl      $-1275068416, %eax
        mull      %edx
        movl      $-1275068416, %eax
        movl      %edx, %ecx
        mull      %edi
        movl      %edx, %ebx
        movl      %esi, %edi
        movl      %ebp, %edx
        addl      %ecx, %eax
        adcl      $0, %ebx
        andl      $3, %ebp
        shll      $30, %edi
        shrl      $2, %edx
        orl       %edx, %edi
        shrl      $2, %esi
        subl      %eax, %edi
        sbbl      %ebx, %esi
        movl      %edi, %ebx
        shll      $2, %edi
        shrl      $30, %ebx
        orl       %edi, %ebp
        xorl      %edi, %edi
        lea       (,%esi,4), %ecx
        orl       %ecx, %ebx
        subl      %edi, %esi
        jl        ..B2.99
..B2.77:
        addl      $12, %esp
        xorl      %eax, %eax
        pushl     %eax
        pushl     %eax
        movl      124(%esp), %edx
        pushl     %edx
        movl      %ebp, 8(%edx)
        movl      %ebx, 12(%edx)
        call      __dpml_ffs_and_shift__
..B2.78:
        movl      128(%esp), %esi
        xorl      %ebx, %ebx
        andl      $536870911, 24(%esp)
        movl      8(%esi), %edi
        movl      %edi, %eax
        movl      4(%esi), %ecx
        movl      12(%esi), %esi
        orl       %esi, %eax
        jne       ..B2.81
..B2.79:
        movl      24(%esp), %ecx
        xorl      %eax, %eax
        andl      $2, %ecx
        movl      $536870912, %ebx
        orl       %eax, %ecx
        je        ..B2.97
..B2.80:
        movl      128(%esp), %eax
        xorl      $-2147483648, (%eax)
        jmp       ..B2.97
..B2.81:
        cmpl      $5, %ecx
        jl        ..B2.96
..B2.82:
        movl      128(%esp), %eax
        movl      16(%eax), %ebp
        orl       20(%eax), %ebp
        jne       ..B2.96
..B2.83:
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B2.85
..B2.84:
        movl      %esi, %ebp
        movl      $0, 16(%esp)
        shrl      %cl, %ebp
        jmp       ..B2.86
..B2.85:
        movl      %esi, %eax
        movl      %edi, %ebp
        shrl      %cl, %eax
        shrdl     %cl, %esi, %ebp
        movl      %eax, 16(%esp)
..B2.86:
        cmpl      $31, %ecx
        jbe       ..B2.88
..B2.87:
        movl      %ebp, %eax
        movl      $0, 20(%esp)
        shll      %cl, %eax
        jmp       ..B2.89
..B2.88:
        movl      %ebp, %edx
        movl      16(%esp), %eax
        shll      %cl, %edx
        shldl     %cl, %ebp, %eax
        movl      %edx, 20(%esp)
..B2.89:
        subl      20(%esp), %edi
        subl      %eax, %esi
        orl       %esi, %edi
        jne       ..B2.96
..B2.90:
        movl      $30, %eax
        negl      %eax
        addl      %ebp, %eax
        orl       16(%esp), %eax
        jne       ..B2.94
..B2.91:
        movl      24(%esp), %ecx
        xorl      %eax, %eax
        andl      $1, %ecx
        orl       %eax, %ecx
        jne       ..B2.96
..B2.92:
        movl      128(%esp), %ecx
        movl      $1073741824, %ebx
        movl      48(%esp), %eax
        xorl      %eax, (%ecx)
        jmp       ..B2.97
..B2.94:
        movl      $45, %eax
        subl      %eax, %ebp
        orl       16(%esp), %ebp
        jne       ..B2.96
..B2.95:
        movl      $-2147483648, %ebx
..B2.96:
        movl      128(%esp), %ecx
        movl      48(%esp), %eax
        xorl      %eax, (%ecx)
..B2.97:
        addl      $12, %esp
        pushl     128(%esp)
        movl      44(%esp), %eax
        lea       216+__trig_x_table@GOTOFF(%eax), %ecx
        pushl     %ecx
        pushl     124(%esp)
        call      __dpml_multiply__
..B2.98:
        movl      24(%esp), %eax
        movl      %ebx, %edx
        addl      $108, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B2.99:
        movl      128(%esp), %edx
        xorl      %eax, %eax
        xorl      $-2147483648, 48(%esp)
        movl      16(%edx), %ecx
        movl      20(%edx), %esi
        xorl      %edx, %edx
        subl      %ecx, %edx
        sbbl      %esi, %eax
        orl       %esi, %ecx
        je        ..B2.101
..B2.100:
        movl      $1, %ecx
        jmp       ..B2.102
..B2.101:
        xorl      %ecx, %ecx
..B2.102:
        movl      128(%esp), %esi
        addl      %ecx, %ebp
        adcl      $0, %ebx
        movl      %edx, 16(%esi)
        xorl      %edx, %edx
        movl      %eax, 20(%esi)
        movl      %ebp, %eax
        xorl      %ebp, %ebp
        movl      %ebx, %ecx
        xorl      %ebx, %ebx
        subl      %eax, %ebp
        sbbl      %ecx, %ebx
        jmp       ..B2.77
..B2.103:
        movl      56(%esp), %edi
        movl      128(%esp), %esi
        movl      8(%esi,%edi,8), %eax
        movl      12(%esi,%edi,8), %edi
        jmp       ..B2.55
..B2.104:
        movl      $1, %edi
        xorl      %eax, %eax
        jmp       ..B2.45
        .align    16,0x90
	.type	__dpml_ux_degree_reduce__,@function
	.size	__dpml_ux_degree_reduce__,.-__dpml_ux_degree_reduce__
	.data
# -- End  __dpml_ux_degree_reduce__
	.text
# -- Begin  __dpml_ux_sincos
	.text
       .align    16,0x90
	.hidden __dpml_ux_sincos
	.globl __dpml_ux_sincos
__dpml_ux_sincos:
# parameter 1: 192 + %esp
# parameter 2: 196 + %esp
# parameter 3: 204 + %esp
# parameter 4: 212 + %esp
..B3.1:
..L8:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $172, %esp
        xorl      %ecx, %ecx
        call      ..L9
..L9:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L9](%eax), %eax
        movl      %eax, 148(%esp)
        movl      204(%esp), %eax
        andl      $16, %eax
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 156(%esp)
        orl       %ecx, %eax
        je        ..B3.3
..B3.2:
        movl      148(%esp), %eax
        lea       __dpml_ux_degree_reduce__@GOTOFF(%eax), %edx
        movl      %edx, %eax
        jmp       ..B3.4
..B3.3:
        movl      148(%esp), %eax
        lea       __dpml_ux_radian_reduce__@GOTOFF(%eax), %edx
        lea       __dpml_ux_degree_reduce__@GOTOFF(%eax), %eax
..B3.4:
        cmpl      %eax, %edx
        jne       ..B3.119
..B3.5:
        movl      192(%esp), %edx
        movl      (%edx), %eax
        movl      4(%edx), %ebx
        cmpl      $142, %ebx
        movl      %eax, 68(%esp)
        jle       ..B3.10
..B3.6:
        movl      148(%esp), %ebp
        lea       32641(%ebx), %esi
        movl      %esi, %eax
        cltd      
        movl      %edx, %ecx
        mull      264+__trig_x_table@GOTOFF(%ebp)
        movl      %esi, %eax
        movl      268+__trig_x_table@GOTOFF(%ebp), %edi
        movl      %edx, %ebp
        mull      %edi
        movl      %edx, %esi
        addl      %eax, %ebp
        movl      148(%esp), %edx
        adcl      $0, %esi
        movl      264+__trig_x_table@GOTOFF(%edx), %eax
        mull      %ecx
        addl      %ebp, %eax
        adcl      %esi, %edx
        subl      %ebp, %eax
        movl      %edx, 32(%esp)
        sbbl      %esi, %edx
..B3.9:
        movl      %edi, %eax
        mull      %ecx
        addl      32(%esp), %eax
        movl      192(%esp), %edi
        lea       (,%eax,8), %ebp
        lea       (%ebp,%eax,4), %esi
        negl      %esi
        lea       32772(%esi,%ebx), %ebx
        movl      %ebx, 4(%edi)
..B3.10:
        cmpl      $16, %ebx
        jl        ..B3.65
..B3.11:
        movl      %ebx, %eax
        movl      %ebx, %ebp
        cltd      
        addl      $-15, %ebp
        movl      %edx, %esi
        movl      %ebp, %ecx
        adcl      $-1, %esi
        xorl      %edx, %edx
        andl      $63, %ecx
        movl      %esi, %edi
        subl      %ecx, %ebx
        shll      $26, %edi
        shrl      $6, %ebp
        sarl      $6, %esi
        orl       %ebp, %edi
        movl      %edi, 72(%esp)
        orl       %ecx, %edx
        je        ..B3.25
..B3.12:
        movl      192(%esp), %edx
        movl      %ecx, %edi
        negl      %edi
        addl      $64, %edi
        movl      20(%edx), %ebp
        cmpl      $31, %ecx
        movl      %edi, 44(%esp)
        movl      16(%edx), %edi
        movl      %ebp, 32(%esp)
        jbe       ..B3.14
..B3.13:
        movl      %edi, %edx
        xorl      %ebp, %ebp
        shll      %cl, %edx
        jmp       ..B3.15
..B3.14:
        movl      %edi, %ebp
        movl      32(%esp), %edx
        shldl     %cl, %edi, %edx
        shll      %cl, %ebp
..B3.15:
        movl      192(%esp), %eax
        cmpl      $31, %ecx
        movl      %ebp, 16(%eax)
        movl      12(%eax), %ebp
        movl      %edx, 20(%eax)
        movl      8(%eax), %edx
        movl      %ebp, 40(%esp)
        jbe       ..B3.17
..B3.16:
        movl      %edx, %ebp
        movl      $0, 48(%esp)
        shll      %cl, %ebp
        jmp       ..B3.18
..B3.17:
        movl      %edx, %eax
        shll      %cl, %eax
        shldl     %cl, %edx, %ebp
        movl      %eax, 48(%esp)
..B3.18:
        cmpl      $31, 44(%esp)
        jbe       ..B3.20
..B3.19:
        movl      44(%esp), %ecx
        movl      32(%esp), %edi
        movl      $0, 36(%esp)
        shrl      %cl, %edi
        jmp       ..B3.21
..B3.20:
        movl      44(%esp), %ecx
        movl      32(%esp), %eax
        shrdl     %cl, %eax, %edi
        shrl      %cl, %eax
        movl      %eax, 36(%esp)
..B3.21:
        movl      48(%esp), %eax
        orl       %edi, %eax
        movl      192(%esp), %edi
        orl       36(%esp), %ebp
        cmpl      $31, 44(%esp)
        movl      %eax, 8(%edi)
        movl      %ebp, 12(%edi)
        jbe       ..B3.23
..B3.22:
        xorl      %ebp, %ebp
        movl      40(%esp), %edx
        shrl      %cl, %edx
        jmp       ..B3.26
..B3.23:
        movl      40(%esp), %ebp
        shrdl     %cl, %ebp, %edx
        shrl      %cl, %ebp
        jmp       ..B3.26
..B3.25:
        xorl      %edx, %edx
        xorl      %ebp, %ebp
..B3.26:
        movl      192(%esp), %edi
        movl      %edx, (%edi)
        movl      72(%esp), %edx
        movl      %ebp, 4(%edi)
        movl      8(%edi,%edx,8), %eax
        movl      12(%edi,%edx,8), %edi
        movl      %edi, %ebp
        shrl      $20, %ebp
        xorl      %edx, %edx
        movl      %ebp, 48(%esp)
        movl      %esi, %ebp
        subl      %edx, %ebp
        jl        ..B3.59
..B3.27:
        movl      72(%esp), %eax
        addl      $1, %eax
        movl      %eax, 52(%esp)
        adcl      $0, %esi
        movl      %esi, %ebp
        testl     %ebp, %ebp
        jge       ..B3.29
..B3.28:
        addl      $1, %eax
        adcl      $0, %ebp
..B3.29:
        shrdl     $1, %ebp, %eax
        sarl      $1, %ebp
        xorl      %edi, %edi
        movl      %ebp, %ecx
        movl      %edi, 44(%esp)
        subl      %edi, %ecx
        movl      %edi, 40(%esp)
        jb        ..B3.229
..B3.262:
        orl       %eax, %ecx
        je        ..B3.229
..B3.30:
        xorl      %ecx, %ecx
        movl      %ecx, 80(%esp)
        movl      %ecx, 76(%esp)
        movl      %ebp, 92(%esp)
        movl      %eax, 88(%esp)
        movl      %edi, 84(%esp)
        movl      %ebx, 32(%esp)
        movl      %esi, 36(%esp)
        movl      %edi, 100(%esp)
        movl      44(%esp), %ecx
        movl      40(%esp), %eax
        movl      48(%esp), %ebp
..B3.31:
        movl      72(%esp), %edi
        movl      80(%esp), %ebx
        movl      192(%esp), %esi
        addl      %edi, %ebx
        movl      %ebx, 96(%esp)
        movl      (%esi,%ebx,8), %edi
        movl      %edi, 116(%esp)
        movl      4(%esi,%ebx,8), %edi
        movl      %edi, 104(%esp)
        xorl      %edi, %edi
        movl      %edi, (%esi,%ebx,8)
        movl      %edi, 4(%esi,%ebx,8)
        movl      %ecx, %esi
        movl      116(%esp), %ebx
        orl       %eax, %esi
        je        ..B3.39
..B3.32:
        cmpl      $31, %ecx
        jbe       ..B3.34
..B3.33:
        xorl      %esi, %esi
        jmp       ..B3.35
..B3.34:
        movl      %ebx, %esi
        shll      %cl, %esi
..B3.35:
        andl      $4095, %esi
        xorl      %edi, %edi
        addl      %esi, %ebp
        adcl      $0, %edx
        movl      $12, %esi
        subl      %ecx, %esi
        sbbl      %eax, %edi
        movl      %edi, 56(%esp)
        cmpl      $31, %esi
        jbe       ..B3.37
..B3.36:
        movl      %esi, %ecx
        xorl      %eax, %eax
        movl      104(%esp), %ebx
        shrl      %cl, %ebx
        jmp       ..B3.38
..B3.37:
        movl      %esi, %ecx
        movl      104(%esp), %eax
        shrdl     %cl, %eax, %ebx
        shrl      %cl, %eax
..B3.38:
        movl      %eax, 104(%esp)
        xorl      %eax, %eax
        xorl      %ecx, %ecx
        subl      %esi, %ecx
        sbbl      56(%esp), %eax
..B3.39:
        movl      104(%esp), %edi
        movl      %edi, %esi
        shrl      $28, %edi
        andl      $268435455, %esi
        addl      %edi, %ebx
        adcl      $0, %esi
        addl      %ebp, %ebx
        adcl      %edx, %esi
        addl      $4, %ecx
        movl      %esi, 112(%esp)
        movl      192(%esp), %esi
        adcl      $0, %eax
        movl      96(%esp), %edx
        movl      -4(%esi,%edx,8), %edi
        movl      -8(%esi,%edx,8), %ebp
        movl      %edi, 108(%esp)
        xorl      %edi, %edi
        movl      %edi, -8(%esi,%edx,8)
        movl      %edi, -4(%esi,%edx,8)
        movl      %ecx, %edx
        orl       %eax, %edx
        je        ..B3.47
..B3.40:
        cmpl      $31, %ecx
        jbe       ..B3.42
..B3.41:
        movl      $0, 64(%esp)
        jmp       ..B3.43
..B3.42:
        movl      %ebp, %edx
        shll      %cl, %edx
        movl      %edx, 64(%esp)
..B3.43:
        movl      64(%esp), %edx
        xorl      %esi, %esi
        andl      $4095, %edx
        addl      %edx, %ebx
        adcl      $0, 112(%esp)
        movl      $12, %edx
        subl      %ecx, %edx
        sbbl      %eax, %esi
        movl      %esi, 60(%esp)
        cmpl      $31, %edx
        jbe       ..B3.45
..B3.44:
        movl      %edx, %ecx
        xorl      %eax, %eax
        movl      108(%esp), %ebp
        shrl      %cl, %ebp
        jmp       ..B3.46
..B3.45:
        movl      %edx, %ecx
        movl      108(%esp), %eax
        shrdl     %cl, %eax, %ebp
        shrl      %cl, %eax
..B3.46:
        movl      %eax, 108(%esp)
        xorl      %eax, %eax
        xorl      %ecx, %ecx
        subl      %edx, %ecx
        sbbl      60(%esp), %eax
..B3.47:
        movl      108(%esp), %esi
        movl      %esi, %edx
        shrl      $28, %esi
        andl      $268435455, %edx
        addl      %esi, %ebp
        movl      84(%esp), %edi
        adcl      $0, %edx
        addl      %ebx, %ebp
        movl      100(%esp), %ebx
        adcl      112(%esp), %edx
        addl      $4, %ecx
        adcl      $0, %eax
        addl      $-2, 80(%esp)
        adcl      $-1, 76(%esp)
        addl      $1, %ebx
        movl      %ebx, 100(%esp)
        adcl      $0, %edi
        subl      88(%esp), %ebx
        movl      %edi, 84(%esp)
        sbbl      92(%esp), %edi
        jb        ..B3.31
..B3.48:
        movl      100(%esp), %edi
        movl      %eax, 40(%esp)
        movl      %edi, %eax
        movl      %ebp, 48(%esp)
        addl      %edi, %edi
        movl      84(%esp), %ebp
        shrl      $31, %eax
        addl      %ebp, %ebp
        orl       %ebp, %eax
        addl      $1, %edi
        movl      32(%esp), %ebx
        movl      36(%esp), %esi
        adcl      $0, %eax
        movl      %ecx, 44(%esp)
..B3.49:
        movl      %edi, %ebp
        addl      $-1, %ebp
        adcl      $-1, %eax
        subl      %ebp, 52(%esp)
        sbbl      %eax, %esi
        jb        ..B3.228
..B3.263:
        movl      52(%esp), %ebp
        orl       %esi, %ebp
        je        ..B3.228
..B3.50:
        negl      %edi
        xorl      %esi, %esi
        addl      72(%esp), %edi
        movl      192(%esp), %eax
        movl      8(%eax,%edi,8), %ebp
        movl      %ebp, 32(%esp)
        movl      12(%eax,%edi,8), %ebp
        movl      %esi, 8(%eax,%edi,8)
        movl      %esi, 12(%eax,%edi,8)
        movl      40(%esp), %edi
        orl       44(%esp), %edi
        je        ..B3.58
..B3.51:
        cmpl      $31, 44(%esp)
        ja        ..B3.54
..B3.53:
        movl      44(%esp), %ecx
        movl      32(%esp), %esi
        shll      %cl, %esi
..B3.54:
        andl      $4095, %esi
        addl      %esi, 48(%esp)
        movl      44(%esp), %esi
        adcl      $0, %edx
        negl      %esi
        addl      $12, %esi
        movl      %esi, 44(%esp)
        cmpl      $31, %esi
        jbe       ..B3.56
..B3.55:
        movl      %esi, %ecx
        xorl      %esi, %esi
        shrl      %cl, %ebp
        movl      %ebp, 32(%esp)
        jmp       ..B3.57
..B3.56:
        movl      %esi, %ecx
        movl      32(%esp), %esi
        shrdl     %cl, %ebp, %esi
        movl      %esi, 32(%esp)
        movl      %ebp, %esi
        shrl      %cl, %esi
..B3.57:
        movl      %esi, %ebp
..B3.58:
        movl      %ebp, %edi
        shrl      $28, %ebp
        andl      $268435455, %edi
        movl      32(%esp), %esi
        addl      %ebp, %esi
        movl      192(%esp), %ebp
        adcl      $0, %edi
        addl      %esi, 48(%esp)
        movl      72(%esp), %esi
        adcl      %edi, %edx
        movl      8(%ebp,%esi,8), %eax
        movl      12(%ebp,%esi,8), %edi
..B3.59:
        movl      %edx, %esi
        movl      48(%esp), %ebp
        shll      $20, %esi
        shrl      $12, %ebp
        orl       %ebp, %esi
        shrl      $12, %edx
        movl      %esi, %ebp
        orl       %edx, %ebp
        je        ..B3.63
..B3.60:
        movl      48(%esp), %ebp
..B3.61:
        andl      $4095, %ebp
        addl      %esi, %ebp
        movl      %ebp, %ecx
        adcl      $0, %edx
        movl      %edx, %esi
        shll      $20, %esi
        shrl      $12, %ecx
        orl       %ecx, %esi
        shrl      $12, %edx
        movl      %esi, %ecx
        orl       %edx, %ecx
        jne       ..B3.61
..B3.62:
        movl      %ebp, 48(%esp)
..B3.63:
        addl      $12, %esp
        xorl      %ebp, %ebp
        pushl     %ebp
        pushl     %ebp
        movl      188(%esp), %edx
        andl      $1048575, %edi
        pushl     %edx
        movl      48(%esp), %esi
        shll      $20, %esi
        orl       %esi, %edi
        movl      72(%esp), %esi
        movl      %ebx, 4(%edx)
        movl      %eax, 8(%edx,%esi,8)
        movl      %edi, 12(%edx,%esi,8)
        call      __dpml_ffs_and_shift__
..B3.64:
        subl      %eax, %ebx
..B3.65:
        movl      %ebx, %eax
        addl      $-5, %ebx
        cltd      
        movl      192(%esp), %esi
        adcl      $-1, %edx
        xorl      %edi, %edi
        subl      %edi, %edx
        movl      %edi, (%esi)
        movl      8(%esi), %ebp
        movl      12(%esi), %esi
        jl        ..B3.70
..B3.264:
        orl       %ebx, %edx
        je        ..B3.70
..B3.66:
        movl      148(%esp), %edi
        movl      %ebp, %eax
        mull      256+__trig_x_table@GOTOFF(%edi)
        movl      %ebp, %eax
        movl      260+__trig_x_table@GOTOFF(%edi), %ecx
        movl      %edx, %edi
        mull      %ecx
        movl      %ecx, 32(%esp)
        movl      %edx, %ecx
        movl      148(%esp), %edx
        addl      %eax, %edi
        adcl      $0, %ecx
        movl      256+__trig_x_table@GOTOFF(%edx), %eax
        mull      %esi
        addl      %edi, %eax
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 36(%esp)
        sbbl      %ecx, %edx
        jae       ..B3.68
..B3.67:
        movl      $1, 44(%esp)
        jmp       ..B3.69
..B3.68:
        movl      $0, 44(%esp)
..B3.69:
        movl      32(%esp), %eax
        mull      %esi
        movl      44(%esp), %edi
        addl      %edx, %edi
        addl      36(%esp), %eax
        movl      %eax, 40(%esp)
        adcl      $0, %edi
        movl      %edi, 44(%esp)
        jmp       ..B3.71
..B3.70:
        xorl      %edx, %edx
        movl      $1, %ebx
        movl      %edx, 40(%esp)
        movl      %edx, 44(%esp)
..B3.71:
        negl      %ebx
        movl      196(%esp), %eax
        andl      $1, %eax
        incl      %eax
        lea       64(%ebx), %edx
        addl      $63, %ebx
        movl      %edx, 32(%esp)
        cmpl      $31, %ebx
        jbe       ..B3.73
..B3.72:
        movl      %ebx, %ecx
        movl      %eax, %edi
        shll      %cl, %edi
        xorl      %edx, %edx
        jmp       ..B3.74
..B3.73:
        movl      %ebx, %ecx
        xorl      %edi, %edi
        movl      %eax, %edx
        shldl     %cl, %eax, %edi
        shll      %cl, %edx
..B3.74:
        addl      40(%esp), %edx
        adcl      44(%esp), %edi
        cmpl      $31, 32(%esp)
        jbe       ..B3.76
..B3.75:
        movl      $1, %ebx
        xorl      %eax, %eax
        movl      32(%esp), %ecx
        shll      %cl, %ebx
        jmp       ..B3.77
..B3.76:
        xorl      %ebx, %ebx
        movl      $1, %eax
        movl      32(%esp), %ecx
        shldl     %cl, %eax, %ebx
        shll      %cl, %eax
..B3.77:
        addl      $-1, %eax
        notl      %eax
        adcl      $-1, %ebx
        andl      %eax, %edx
        notl      %ebx
        andl      %ebx, %edi
        cmpl      $31, 32(%esp)
        jbe       ..B3.79
..B3.78:
        movl      %edi, %eax
        shrl      %cl, %eax
        jmp       ..B3.80
..B3.79:
        movl      %edx, %eax
        shrdl     %cl, %edi, %eax
..B3.80:
        movl      %eax, %ebx
        negl      %ebx
        cmpl      $0, 68(%esp)
        jne       ..L10
        movl      %eax, %ebx
..L10:
        movl      196(%esp), %eax
        shrl      $1, %eax
        addl      %ebx, %eax
        movl      %eax, 44(%esp)
        movl      $-1275068416, %eax
        mull      %edx
        movl      $-1275068416, %eax
        movl      %edx, %ecx
        mull      %edi
        movl      %edx, %ebx
        movl      %esi, %edi
        movl      %ebp, %edx
        addl      %ecx, %eax
        adcl      $0, %ebx
        andl      $3, %ebp
        shll      $30, %edi
        shrl      $2, %edx
        orl       %edx, %edi
        shrl      $2, %esi
        subl      %eax, %edi
        sbbl      %ebx, %esi
        movl      %edi, %ebx
        shll      $2, %edi
        shrl      $30, %ebx
        orl       %edi, %ebp
        xorl      %edi, %edi
        lea       (,%esi,4), %ecx
        orl       %ecx, %ebx
        subl      %edi, %esi
        jl        ..B3.224
..B3.81:
        addl      $12, %esp
        xorl      %eax, %eax
        pushl     %eax
        pushl     %eax
        movl      188(%esp), %edx
        pushl     %edx
        movl      %ebp, 8(%edx)
        movl      %ebx, 12(%edx)
        call      __dpml_ffs_and_shift__
..B3.82:
        movl      192(%esp), %ebp
        xorl      %ebx, %ebx
        movl      8(%ebp), %esi
        movl      %esi, %eax
        movl      4(%ebp), %ecx
        movl      12(%ebp), %ebp
        orl       %ebp, %eax
        jne       ..B3.85
..B3.83:
        movl      44(%esp), %edx
        xorl      %eax, %eax
        andl      $2, %edx
        movl      $536870912, %ebx
        orl       %eax, %edx
        je        ..B3.101
..B3.84:
        movl      192(%esp), %eax
        xorl      $-2147483648, (%eax)
        jmp       ..B3.101
..B3.85:
        cmpl      $5, %ecx
        jl        ..B3.100
..B3.86:
        movl      192(%esp), %eax
        movl      16(%eax), %edx
        orl       20(%eax), %edx
        jne       ..B3.100
..B3.87:
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B3.89
..B3.88:
        movl      %ebp, %edx
        movl      $0, 32(%esp)
        shrl      %cl, %edx
        jmp       ..B3.90
..B3.89:
        movl      %ebp, %eax
        movl      %esi, %edx
        shrl      %cl, %eax
        shrdl     %cl, %ebp, %edx
        movl      %eax, 32(%esp)
..B3.90:
        cmpl      $31, %ecx
        jbe       ..B3.92
..B3.91:
        movl      %edx, %eax
        movl      $0, 36(%esp)
        shll      %cl, %eax
        jmp       ..B3.93
..B3.92:
        movl      %edx, %edi
        movl      32(%esp), %eax
        shll      %cl, %edi
        shldl     %cl, %edx, %eax
        movl      %edi, 36(%esp)
..B3.93:
        subl      36(%esp), %esi
        subl      %eax, %ebp
        orl       %ebp, %esi
        jne       ..B3.100
..B3.94:
        movl      $30, %eax
        negl      %eax
        addl      %edx, %eax
        orl       32(%esp), %eax
        jne       ..B3.98
..B3.95:
        movl      44(%esp), %edx
        xorl      %eax, %eax
        andl      $1, %edx
        orl       %eax, %edx
        jne       ..B3.100
..B3.96:
        movl      192(%esp), %edx
        movl      $1073741824, %ebx
        movl      68(%esp), %eax
        xorl      %eax, (%edx)
        jmp       ..B3.101
..B3.98:
        movl      $45, %eax
        subl      %eax, %edx
        orl       32(%esp), %edx
        jne       ..B3.100
..B3.99:
        movl      $-2147483648, %ebx
..B3.100:
        movl      192(%esp), %edx
        movl      68(%esp), %eax
        xorl      %eax, (%edx)
..B3.101:
        addl      $12, %esp
        lea       36(%esp), %eax
        pushl     %eax
        movl      100(%eax), %edx
        lea       216+__trig_x_table@GOTOFF(%edx), %ecx
        pushl     %ecx
        pushl     188(%esp)
        call      __dpml_multiply__
..B3.103:
        movl      204(%esp), %esi
        movl      $3, %eax
        andl      $-17, %esi
        movl      %esi, %edx
        subl      %eax, %edx
        orl       208(%esp), %edx
        jne       ..B3.106
..B3.105:
        movl      $1, %eax
..B3.106:
        sarl      $29, %ebx
        andl      %eax, %ebx
        xorl      %eax, %eax
        orl       %eax, %ebx
        jne       ..B3.108
..B3.107:
        movl      $1, %ebp
        jmp       ..B3.109
..B3.108:
        xorl      %ebp, %ebp
..B3.109:
        movl      %esi, %ecx
        movl      $3, %edx
        subl      %edx, %ecx
        movl      44(%esp), %ebx
        andl      $1, %ebx
        orl       208(%esp), %ecx
        je        ..B3.220
..B3.110:
        xorl      %edx, %edx
        orl       %edx, %ebx
        je        ..B3.112
..B3.111:
        movl      $1216, %ecx
        jmp       ..B3.113
..B3.112:
        movl      $1038, %ecx
..B3.113:
        addl      $28, %esp
        xorl      %edx, %edx
        movl      184(%esp), %ebx
        pushl     %ebx
        pushl     %edx
        pushl     %ecx
        pushl     %edx
        pushl     $13
        movl      140(%esp), %ecx
        lea       272+__trig_x_table@GOTOFF(%ecx), %edi
        pushl     %edi
        lea       44(%esp), %edx
        pushl     %edx
        call      __dpml_evaluate_rational__
..B3.114:
        movl      44(%esp), %ecx
        xorl      %edx, %edx
        andl      $2, %ecx
        orl       %edx, %ecx
        je        ..B3.116
..B3.115:
        xorl      $-2147483648, (%ebx)
..B3.116:
        movl      $3, %edx
        subl      %edx, %esi
        orl       208(%esp), %esi
        je        ..B3.216
..B3.117:
        movl      156(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B3.215
..B3.118:
        movl      %ebp, %eax
        xorl      %edx, %edx
        addl      $172, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B3.119:
        movl      192(%esp), %ebp
        movl      8(%ebp), %eax
        movl      12(%ebp), %edx
        movl      16(%ebp), %ecx
        movl      20(%ebp), %ebx
        movl      4(%ebp), %edi
        testl     %edi, %edi
        movl      (%ebp), %esi
        movl      %eax, 104(%esp)
        movl      %edx, 100(%esp)
        movl      %ecx, 108(%esp)
        movl      %ebx, 112(%esp)
        movl      %edi, 128(%esp)
        movl      %esi, 76(%esp)
        jl        ..B3.253
..B3.120:
        movl      %edi, %eax
        cltd      
        addl      $8, %eax
        movl      %eax, %edi
        adcl      $0, %edx
        andl      $63, %eax
        movl      148(%esp), %ebp
        shll      $26, %edx
        shrl      $6, %edi
        orl       %edx, %edi
        movl      __four_over_pi@GOT(%ebp), %esi
        movl      %edi, 72(%esp)
        movl      %eax, 128(%esp)
        lea       (%esi,%edi,8), %ecx
        movl      (%ecx), %ebx
        movl      4(%ecx), %ebp
        movl      8(%ecx), %esi
        movl      12(%ecx), %edi
        movl      20(%ecx), %edx
        movl      %ebx, 60(%esp)
        movl      %ebp, 56(%esp)
        movl      %esi, 68(%esp)
        movl      %edi, 64(%esp)
        movl      %edx, 32(%esp)
        movl      16(%ecx), %ebp
        movl      24(%ecx), %edi
        movl      28(%ecx), %esi
        movl      32(%ecx), %ebx
        movl      36(%ecx), %edx
        addl      $40, %ecx
        movl      %ecx, 120(%esp)
        xorl      %ecx, %ecx
        movl      %ebx, 116(%esp)
        orl       %eax, %ecx
        movl      %edx, 124(%esp)
        je        ..B3.146
..B3.121:
        movl      %eax, %ecx
        movl      %ecx, %edx
        negl      %edx
        addl      $64, %edx
        movl      %edx, 72(%esp)
        cmpl      $31, %ecx
        jbe       ..B3.123
..B3.122:
        xorl      %eax, %eax
        movl      60(%esp), %edx
        shll      %cl, %edx
        movl      %edx, 56(%esp)
        jmp       ..B3.124
..B3.123:
        movl      56(%esp), %edx
        movl      60(%esp), %eax
        shldl     %cl, %eax, %edx
        movl      %edx, 56(%esp)
        shll      %cl, %eax
..B3.124:
        cmpl      $31, 72(%esp)
        jbe       ..B3.126
..B3.125:
        movl      72(%esp), %ecx
        xorl      %edx, %edx
        movl      64(%esp), %ebx
        shrl      %cl, %ebx
        movl      %ebx, 60(%esp)
        jmp       ..B3.127
..B3.126:
        movl      72(%esp), %ecx
        movl      68(%esp), %ebx
        movl      64(%esp), %edx
        shrdl     %cl, %edx, %ebx
        movl      %ebx, 60(%esp)
        shrl      %cl, %edx
..B3.127:
        orl       %eax, 60(%esp)
        orl       %edx, 56(%esp)
        cmpl      $31, 128(%esp)
        jbe       ..B3.129
..B3.128:
        movl      128(%esp), %ecx
        xorl      %eax, %eax
        movl      68(%esp), %edx
        shll      %cl, %edx
        movl      %edx, 64(%esp)
        jmp       ..B3.130
..B3.129:
        movl      128(%esp), %ecx
        movl      64(%esp), %edx
        movl      68(%esp), %eax
        shldl     %cl, %eax, %edx
        movl      %edx, 64(%esp)
        shll      %cl, %eax
..B3.130:
        cmpl      $31, 72(%esp)
        jbe       ..B3.132
..B3.131:
        movl      72(%esp), %ecx
        xorl      %edx, %edx
        movl      32(%esp), %ebx
        shrl      %cl, %ebx
        movl      %ebx, 68(%esp)
        jmp       ..B3.133
..B3.132:
        movl      %ebp, %ebx
        movl      72(%esp), %ecx
        movl      32(%esp), %edx
        shrdl     %cl, %edx, %ebx
        movl      %ebx, 68(%esp)
        shrl      %cl, %edx
..B3.133:
        orl       %eax, 68(%esp)
        orl       %edx, 64(%esp)
        cmpl      $31, 128(%esp)
        jbe       ..B3.135
..B3.134:
        movl      128(%esp), %ecx
        xorl      %edx, %edx
        shll      %cl, %ebp
        movl      %ebp, 32(%esp)
        jmp       ..B3.136
..B3.135:
        movl      128(%esp), %ecx
        movl      32(%esp), %edx
        shldl     %cl, %ebp, %edx
        movl      %edx, 32(%esp)
        movl      %ebp, %edx
        shll      %cl, %edx
..B3.136:
        cmpl      $31, 72(%esp)
        jbe       ..B3.138
..B3.137:
        movl      %esi, %ebp
        xorl      %ebx, %ebx
        movl      72(%esp), %ecx
        shrl      %cl, %ebp
        jmp       ..B3.139
..B3.138:
        movl      %edi, %ebp
        movl      %esi, %ebx
        movl      72(%esp), %ecx
        shrdl     %cl, %esi, %ebp
        shrl      %cl, %ebx
..B3.139:
        orl       %ebx, 32(%esp)
        orl       %edx, %ebp
        cmpl      $31, 128(%esp)
        jbe       ..B3.141
..B3.140:
        movl      %edi, %esi
        xorl      %edx, %edx
        movl      128(%esp), %ecx
        shll      %cl, %esi
        jmp       ..B3.142
..B3.141:
        movl      %edi, %edx
        movl      128(%esp), %ecx
        shldl     %cl, %edi, %esi
        shll      %cl, %edx
..B3.142:
        cmpl      $31, 72(%esp)
        jbe       ..B3.144
..B3.143:
        movl      72(%esp), %ecx
        xorl      %ebx, %ebx
        movl      124(%esp), %edi
        shrl      %cl, %edi
        jmp       ..B3.145
..B3.144:
        movl      72(%esp), %ecx
        movl      116(%esp), %edi
        movl      124(%esp), %ebx
        shrdl     %cl, %ebx, %edi
        shrl      %cl, %ebx
..B3.145:
        orl       %edx, %edi
        orl       %ebx, %esi
..B3.146:
        movl      %edi, %eax
        mull      108(%esp)
        movl      %edi, %eax
        movl      %edx, %ecx
        mull      112(%esp)
        addl      %eax, %ecx
        movl      %edx, %ebx
        movl      108(%esp), %eax
        adcl      $0, %ebx
        mull      %esi
        addl      %ecx, %eax
        adcl      %ebx, %edx
        subl      %ecx, %eax
        movl      %edx, 48(%esp)
        sbbl      %ebx, %edx
        jae       ..B3.148
..B3.147:
        movl      $1, %ebx
        jmp       ..B3.149
..B3.148:
        xorl      %ebx, %ebx
..B3.149:
        movl      %esi, %eax
        movl      112(%esp), %ecx
        mull      %ecx
        addl      %edx, %ebx
        addl      48(%esp), %eax
        movl      %eax, 84(%esp)
        adcl      $0, %ebx
        movl      %ebx, 88(%esp)
        movl      %esi, %ebx
        movl      108(%esp), %eax
        imull     %edi, %ecx
        imull     %eax, %ebx
        mull      %edi
        addl      %ebx, %ecx
        movl      100(%esp), %ebx
        addl      %ecx, %edx
        movl      %ebp, 52(%esp)
        movl      %esi, %ebp
        movl      %edx, 96(%esp)
        movl      %ebx, %edx
        movl      104(%esp), %ecx
        imull     %ecx, %ebp
        imull     %edi, %edx
        movl      %eax, 92(%esp)
        movl      %edi, %eax
        addl      %edx, %ebp
        mull      %ecx
        movl      %eax, 36(%esp)
        movl      %edi, %eax
        addl      %ebp, %edx
        movl      %edx, 40(%esp)
        mull      %ecx
        movl      %edi, %eax
        movl      %edx, %ebp
        mull      %ebx
        addl      %eax, %ebp
        movl      %ecx, %eax
        movl      %edx, %edi
        adcl      $0, %edi
        mull      %esi
        addl      %ebp, %eax
        movl      88(%esp), %ebx
        adcl      %edi, %edx
        subl      %ebp, %eax
        movl      %edx, 44(%esp)
        movl      52(%esp), %ebp
        sbbl      %edi, %edx
        jae       ..B3.151
..B3.150:
        movl      $1, %ecx
        jmp       ..B3.152
..B3.151:
        xorl      %ecx, %ecx
..B3.152:
        movl      %esi, %eax
        mull      100(%esp)
        movl      %eax, %esi
        addl      %edx, %ecx
        addl      44(%esp), %esi
        movl      36(%esp), %edi
        movl      84(%esp), %eax
        adcl      $0, %ecx
        addl      %edi, %eax
        movl      40(%esp), %edx
        adcl      %edx, %ebx
        movl      %eax, 84(%esp)
        subl      %edi, %eax
        movl      %ebx, %edi
        sbbl      %edx, %edi
        jae       ..B3.154
..B3.153:
        movl      $1, %edx
        jmp       ..B3.155
..B3.154:
        xorl      %edx, %edx
..B3.155:
        addl      %edx, %esi
        movl      %esi, 80(%esp)
        adcl      $0, %ecx
        movl      %ecx, 132(%esp)
        movl      112(%esp), %ecx
        movl      %ecx, %edi
        movl      32(%esp), %esi
        movl      %esi, %edx
        movl      %ebx, 88(%esp)
        movl      108(%esp), %ebx
        movl      %ebx, %eax
        imull     %ebp, %edi
        imull     %ebx, %edx
        addl      %edx, %edi
        mull      %ebp
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 44(%esp)
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %ebx, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %esi
        addl      %edi, %eax
        movl      80(%esp), %esi
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 48(%esp)
        movl      88(%esp), %ebx
        sbbl      %ecx, %edx
        movl      132(%esp), %ecx
        jae       ..B3.157
..B3.156:
        movl      $1, %edi
        jmp       ..B3.158
..B3.157:
        xorl      %edi, %edi
..B3.158:
        movl      32(%esp), %eax
        mull      112(%esp)
        addl      %edx, %edi
        addl      48(%esp), %eax
        movl      %eax, 36(%esp)
        adcl      $0, %edi
        movl      %edi, 52(%esp)
        movl      40(%esp), %edi
        movl      84(%esp), %eax
        addl      %edi, %eax
        movl      44(%esp), %edx
        adcl      %edx, %ebx
        movl      %eax, 84(%esp)
        subl      %edi, %eax
        movl      %ebx, %edi
        sbbl      %edx, %edi
        movl      52(%esp), %edi
        jae       ..B3.160
..B3.159:
        movl      $1, 40(%esp)
        jmp       ..B3.161
..B3.160:
        movl      $0, 40(%esp)
..B3.161:
        movl      40(%esp), %edx
        addl      %edx, %esi
        movl      %esi, %eax
        adcl      $0, %ecx
        subl      %edx, %eax
        movl      $0, %edx
        movl      %ecx, %eax
        sbbl      %edx, %eax
        jae       ..B3.163
..B3.162:
        movl      $1, 48(%esp)
        jmp       ..B3.164
..B3.163:
        movl      $0, 48(%esp)
..B3.164:
        movl      36(%esp), %edx
        addl      %edx, %esi
        movl      %esi, %eax
        adcl      %edi, %ecx
        subl      %edx, %eax
        movl      %ecx, %edx
        sbbl      %edi, %edx
        jae       ..B3.166
..B3.165:
        movl      $1, %edx
        jmp       ..B3.167
..B3.166:
        xorl      %edx, %edx
..B3.167:
        movl      %esi, 80(%esp)
        movl      %ecx, 132(%esp)
        movl      100(%esp), %ecx
        movl      %ecx, %edi
        movl      32(%esp), %esi
        addl      %edx, 48(%esp)
        movl      %esi, %edx
        movl      %ebx, 88(%esp)
        movl      104(%esp), %ebx
        movl      %ebx, %eax
        imull     %ebp, %edi
        imull     %ebx, %edx
        addl      %edx, %edi
        mull      %ebp
        movl      %eax, 36(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 40(%esp)
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %ebx, %eax
        movl      %edx, %ebp
        adcl      $0, %ebp
        mull      %esi
        addl      %edi, %eax
        movl      132(%esp), %ecx
        adcl      %ebp, %edx
        subl      %edi, %eax
        movl      %edx, 44(%esp)
        movl      80(%esp), %esi
        sbbl      %ebp, %edx
        movl      88(%esp), %ebx
        jae       ..B3.169
..B3.168:
        movl      $1, %ebp
        jmp       ..B3.170
..B3.169:
        xorl      %ebp, %ebp
..B3.170:
        movl      32(%esp), %eax
        mull      100(%esp)
        addl      %edx, %ebp
        addl      44(%esp), %eax
        movl      36(%esp), %edi
        adcl      $0, %ebp
        addl      %edi, %esi
        movl      %esi, %edx
        adcl      40(%esp), %ecx
        subl      %edi, %edx
        movl      %ecx, %edi
        sbbl      40(%esp), %edi
        jae       ..B3.172
..B3.171:
        movl      $1, %edx
        jmp       ..B3.173
..B3.172:
        xorl      %edx, %edx
..B3.173:
        movl      %ebx, 88(%esp)
        movl      48(%esp), %ebx
        addl      %eax, %ebx
        movl      %esi, 80(%esp)
        adcl      $0, %ebp
        addl      %edx, %ebx
        movl      %ecx, 132(%esp)
        movl      112(%esp), %ecx
        movl      %ecx, %edi
        movl      64(%esp), %esi
        movl      %esi, %edx
        adcl      $0, %ebp
        movl      %ebx, 48(%esp)
        movl      %ebp, 44(%esp)
        movl      68(%esp), %ebp
        movl      108(%esp), %ebx
        movl      %ebx, %eax
        imull     %ebp, %edi
        imull     %ebx, %edx
        addl      %edx, %edi
        mull      %ebp
        movl      %eax, 32(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 36(%esp)
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %ebx, %eax
        movl      %edx, %ebp
        adcl      $0, %ebp
        mull      %esi
        addl      %edi, %eax
        movl      132(%esp), %ecx
        adcl      %ebp, %edx
        subl      %edi, %eax
        movl      %edx, 40(%esp)
        movl      80(%esp), %esi
        sbbl      %ebp, %edx
        movl      44(%esp), %ebp
        movl      88(%esp), %ebx
        jae       ..B3.175
..B3.174:
        movl      $1, 52(%esp)
        jmp       ..B3.176
..B3.175:
        movl      $0, 52(%esp)
..B3.176:
        movl      64(%esp), %eax
        mull      112(%esp)
        movl      52(%esp), %edi
        addl      %edx, %edi
        addl      40(%esp), %eax
        movl      %eax, 44(%esp)
        movl      32(%esp), %eax
        adcl      $0, %edi
        addl      %eax, %esi
        movl      %esi, %edx
        movl      %edi, 52(%esp)
        movl      36(%esp), %edi
        adcl      %edi, %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      %edi, %eax
        jae       ..B3.178
..B3.177:
        movl      $1, 32(%esp)
        jmp       ..B3.179
..B3.178:
        movl      $0, 32(%esp)
..B3.179:
        movl      %esi, 80(%esp)
        movl      100(%esp), %edi
        movl      68(%esp), %esi
        movl      104(%esp), %eax
        movl      64(%esp), %edx
        imull     %esi, %edi
        imull     %eax, %edx
        addl      %edx, %edi
        mull      %esi
        movl      %ecx, 132(%esp)
        movl      %eax, %ecx
        movl      %edx, %esi
        addl      %edi, %esi
        addl      48(%esp), %ecx
        movl      60(%esp), %edi
        movl      56(%esp), %edx
        adcl      %ebp, %esi
        movl      112(%esp), %ebp
        movl      108(%esp), %eax
        imull     %edi, %ebp
        imull     %eax, %edx
        addl      %edx, %ebp
        mull      %edi
        addl      44(%esp), %ecx
        adcl      52(%esp), %esi
        addl      %ebp, %edx
        addl      32(%esp), %ecx
        adcl      $0, %esi
        addl      %ecx, %eax
        movl      196(%esp), %ecx
        movl      %ecx, %ebp
        adcl      %esi, %edx
        negl      %ebp
        cmpl      $0, 76(%esp)
        movl      %eax, 136(%esp)
        jne       ..L11
        movl      %ecx, %ebp
..L11:
        xorl      %eax, %eax
        shll      $29, %ebp
        addl      %ebp, %edx
        movl      %eax, 40(%esp)
        movl      84(%esp), %ebp
        movl      %edx, 152(%esp)
        movl      %eax, 36(%esp)
        jmp       ..B3.180
..B3.214:
        addl      $64, 40(%esp)
        movl      152(%esp), %ecx
        movl      132(%esp), %edx
        movl      80(%esp), %eax
        movl      32(%esp), %esi
        movl      44(%esp), %edi
        movl      %ebp, 80(%esp)
        movl      %ebx, 132(%esp)
        movl      92(%esp), %ebp
        movl      96(%esp), %ebx
        adcl      $0, 36(%esp)
        andl      $-536870912, %ecx
        andl      $536870911, %edx
        orl       %edx, %ecx
        movl      %eax, 136(%esp)
        movl      %ecx, 152(%esp)
        movl      %esi, 92(%esp)
        movl      %edi, 96(%esp)
..B3.180:
        movl      152(%esp), %eax
        xorl      %edx, %edx
        lea       4194304(%eax), %ecx
        andl      $1065353216, %ecx
        orl       %edx, %ecx
        jne       ..B3.252
..B3.181:
        movl      120(%esp), %eax
        movl      116(%esp), %esi
        movl      124(%esp), %edi
        movl      (%eax), %edx
        movl      %edx, 116(%esp)
        xorl      %edx, %edx
        movl      4(%eax), %ecx
        addl      $8, %eax
        movl      %ecx, 124(%esp)
        movl      %eax, 120(%esp)
        orl       128(%esp), %edx
        je        ..B3.189
..B3.182:
        cmpl      $31, 128(%esp)
        jbe       ..B3.184
..B3.183:
        movl      %esi, %edi
        xorl      %edx, %edx
        movl      128(%esp), %ecx
        shll      %cl, %edi
        jmp       ..B3.185
..B3.184:
        movl      %esi, %edx
        movl      128(%esp), %ecx
        shldl     %cl, %esi, %edi
        shll      %cl, %edx
..B3.185:
        cmpl      $31, 72(%esp)
        jbe       ..B3.187
..B3.186:
        movl      72(%esp), %ecx
        xorl      %eax, %eax
        movl      124(%esp), %esi
        shrl      %cl, %esi
        jmp       ..B3.188
..B3.187:
        movl      72(%esp), %ecx
        movl      116(%esp), %esi
        movl      124(%esp), %eax
        shrdl     %cl, %eax, %esi
        shrl      %cl, %eax
..B3.188:
        orl       %edx, %esi
        orl       %eax, %edi
..B3.189:
        movl      112(%esp), %ecx
        movl      %edi, %edx
        movl      %ebx, 88(%esp)
        movl      %ecx, %ebx
        movl      %ebp, 84(%esp)
        movl      108(%esp), %ebp
        movl      %ebp, %eax
        imull     %esi, %ebx
        imull     %ebp, %edx
        addl      %edx, %ebx
        mull      %esi
        movl      %eax, 32(%esp)
        movl      %ebp, %eax
        addl      %ebx, %edx
        movl      %edx, 44(%esp)
        mull      %esi
        movl      %esi, %eax
        movl      %edx, %ebx
        mull      %ecx
        addl      %eax, %ebx
        movl      %ebp, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %edi
        addl      %ebx, %eax
        movl      84(%esp), %ebp
        adcl      %ecx, %edx
        subl      %ebx, %eax
        movl      %edx, 56(%esp)
        movl      88(%esp), %ebx
        sbbl      %ecx, %edx
        jae       ..B3.191
..B3.190:
        movl      $1, %ecx
        jmp       ..B3.192
..B3.191:
        xorl      %ecx, %ecx
..B3.192:
        movl      112(%esp), %eax
        mull      %edi
        addl      %edx, %ecx
        addl      56(%esp), %eax
        movl      92(%esp), %edx
        adcl      $0, %ecx
        addl      %eax, %edx
        movl      %ebp, 84(%esp)
        movl      96(%esp), %ebp
        adcl      %ecx, %ebp
        movl      %edx, 92(%esp)
        subl      %eax, %edx
        movl      %ebp, 96(%esp)
        sbbl      %ecx, %ebp
        movl      84(%esp), %ebp
        jae       ..B3.194
..B3.193:
        movl      $1, %ecx
        jmp       ..B3.195
..B3.194:
        xorl      %ecx, %ecx
..B3.195:
        movl      100(%esp), %edx
        addl      %ecx, %ebp
        movl      104(%esp), %eax
        movl      %edi, 64(%esp)
        adcl      $0, %ebx
        imull     %esi, %edx
        imull     %eax, %edi
        addl      %edi, %edx
        movl      %ebp, %edi
        movl      %edx, 68(%esp)
        mull      %esi
        addl      68(%esp), %edx
        subl      %ecx, %edi
        movl      $0, %ecx
        movl      %edx, 52(%esp)
        movl      %ebx, %edx
        movl      %eax, 48(%esp)
        sbbl      %ecx, %edx
        movl      64(%esp), %edi
        jae       ..B3.198
..B3.196:
        movl      $1, %ecx
..B3.198:
        movl      104(%esp), %eax
        mull      %esi
        movl      %esi, %eax
        movl      %ebp, 84(%esp)
        movl      %edx, %ebp
        mull      100(%esp)
        addl      %eax, %ebp
        movl      %edx, %esi
        movl      104(%esp), %eax
        adcl      $0, %esi
        mull      %edi
        addl      %ebp, %eax
        adcl      %esi, %edx
        subl      %ebp, %eax
        movl      %edx, 60(%esp)
        movl      84(%esp), %ebp
        sbbl      %esi, %edx
        jae       ..B3.200
..B3.199:
        movl      $1, %esi
        jmp       ..B3.201
..B3.200:
        xorl      %esi, %esi
..B3.201:
        movl      100(%esp), %eax
        mull      %edi
        addl      %edx, %esi
        addl      60(%esp), %eax
        movl      %eax, 140(%esp)
        movl      48(%esp), %edx
        adcl      $0, %esi
        movl      92(%esp), %eax
        addl      %edx, %eax
        movl      %esi, 144(%esp)
        movl      52(%esp), %esi
        movl      96(%esp), %edi
        adcl      %esi, %edi
        movl      %eax, 92(%esp)
        subl      %edx, %eax
        movl      %edi, 96(%esp)
        movl      140(%esp), %eax
        sbbl      %esi, %edi
        movl      144(%esp), %esi
        jae       ..B3.203
..B3.202:
        movl      $1, %edx
        jmp       ..B3.204
..B3.203:
        xorl      %edx, %edx
..B3.204:
        addl      %edx, %ebp
        movl      %ebp, %edi
        adcl      $0, %ebx
        subl      %edx, %edi
        movl      $0, %edx
        movl      %ebx, %edi
        sbbl      %edx, %edi
        jae       ..B3.207
..B3.205:
        movl      $1, %edx
..B3.207:
        addl      %eax, %ebp
        adcl      %esi, %ebx
        addl      %edx, %ecx
        movl      %ebp, %edx
        subl      %eax, %edx
        movl      %ebx, %eax
        sbbl      %esi, %eax
        jae       ..B3.209
..B3.208:
        movl      $1, %eax
        jmp       ..B3.210
..B3.209:
        xorl      %eax, %eax
..B3.210:
        addl      %eax, %ecx
        xorl      %eax, %eax
        orl       %eax, %ecx
        je        ..B3.213
..B3.211:
        movl      80(%esp), %eax
        addl      $1, %eax
        movl      132(%esp), %edx
        adcl      $0, %edx
        movl      %eax, 80(%esp)
        orl       %edx, %eax
        movl      %edx, 132(%esp)
        jne       ..B3.213
..B3.212:
        addl      $1, 136(%esp)
        adcl      $0, 152(%esp)
..B3.213:
        movl      136(%esp), %ecx
        movl      %ecx, %eax
        movl      152(%esp), %esi
        shrl      $23, %eax
        shll      $9, %esi
        orl       %esi, %eax
        movl      132(%esp), %edx
        movl      %eax, %edi
        shrl      $23, %edx
        shll      $9, %ecx
        sarl      $31, %edi
        orl       %ecx, %edx
        xorl      %edi, %edx
        xorl      %edi, %eax
        orl       %eax, %edx
        jne       ..B3.230
        jmp       ..B3.214
..B3.215:
        movl      148(%esp), %ebx
        call      __stack_chk_fail@PLT
..B3.216:
        movl      32(%ebx), %edx
        orl       36(%ebx), %edx
        jne       ..B3.218
..B3.217:
        movl      $0, 24(%ebx)
        jmp       ..B3.117
..B3.218:
        movl      44(%esp), %ecx
        xorl      %edx, %edx
        incl      %ecx
        andl      $2, %ecx
        orl       %edx, %ecx
        je        ..B3.117
..B3.219:
        xorl      $-2147483648, 24(%ebx)
        jmp       ..B3.117
..B3.220:
        xorl      %edx, %edx
        orl       %edx, %ebx
        je        ..B3.222
..B3.221:
        movl      $512, %ecx
        jmp       ..B3.223
..B3.222:
        xorl      %ecx, %ecx
..B3.223:
        orl       $462, %ecx
        jmp       ..B3.113
..B3.224:
        movl      192(%esp), %edx
        xorl      %eax, %eax
        xorl      $-2147483648, 68(%esp)
        movl      16(%edx), %ecx
        movl      20(%edx), %esi
        xorl      %edx, %edx
        subl      %ecx, %edx
        sbbl      %esi, %eax
        orl       %esi, %ecx
        je        ..B3.226
..B3.225:
        movl      $1, %ecx
        jmp       ..B3.227
..B3.226:
        xorl      %ecx, %ecx
..B3.227:
        movl      192(%esp), %esi
        addl      %ecx, %ebp
        adcl      $0, %ebx
        movl      %edx, 16(%esi)
        xorl      %edx, %edx
        movl      %eax, 20(%esi)
        movl      %ebp, %eax
        xorl      %ebp, %ebp
        movl      %ebx, %ecx
        xorl      %ebx, %ebx
        subl      %eax, %ebp
        sbbl      %ecx, %ebx
        jmp       ..B3.81
..B3.228:
        movl      72(%esp), %esi
        movl      192(%esp), %ebp
        movl      8(%ebp,%esi,8), %eax
        movl      12(%ebp,%esi,8), %edi
        jmp       ..B3.59
..B3.229:
        movl      $1, %edi
        xorl      %eax, %eax
        jmp       ..B3.49
..B3.230:
        movl      %ebp, 84(%esp)
        movl      132(%esp), %ecx
        movl      80(%esp), %esi
        movl      152(%esp), %edx
..B3.231:
        movl      136(%esp), %edi
        lea       (,%edx,4), %eax
        movl      %edi, %ebp
        sarl      $2, %eax
        subl      %edi, %ebp
        movl      %ebp, 32(%esp)
        movl      %eax, %ebp
        sbbl      %eax, %edx
        sarl      $31, %ebp
        subl      %ebp, %edi
        negl      %ebp
        addl      %eax, %ebp
        movl      %eax, 36(%esp)
        orl       %ebp, %edi
        jne       ..B3.233
..B3.232:
        movl      %ecx, %eax
        movl      %ebx, %ecx
        movl      92(%esp), %ebx
        movl      %esi, 136(%esp)
        movl      84(%esp), %esi
        movl      %ebx, 84(%esp)
        movl      96(%esp), %ebx
        addl      $64, 40(%esp)
..B3.233:
        xorl      %ebp, %ebp
        movl      36(%esp), %edi
        subl      %ebp, %edi
        jge       ..B3.236
..B3.235:
        movl      $-2147483648, %ebp
..B3.236:
        testl     %ebp, %ebp
        je        ..B3.244
..B3.237:
        movl      84(%esp), %edi
        notl      %ebx
        notl      %edi
        notl      %eax
        addl      $1, %edi
        notl      %esi
        movl      %edi, 84(%esp)
        notl      %ecx
        adcl      $0, %ebx
        notl      136(%esp)
        orl       %ebx, %edi
        jne       ..B3.239
..B3.238:
        movl      $1, %edi
        jmp       ..B3.240
..B3.239:
        xorl      %edi, %edi
..B3.240:
        addl      %edi, %esi
        movl      %esi, %edi
        adcl      $0, %ecx
        orl       %ecx, %edi
        jne       ..B3.242
..B3.241:
        movl      $1, %edi
        jmp       ..B3.243
..B3.242:
        xorl      %edi, %edi
..B3.243:
        addl      %edi, 136(%esp)
        adcl      $0, %eax
..B3.244:
        xorl      %edi, %edi
        movl      %ebx, 88(%esp)
        xorl      %ebx, %ebx
        subl      32(%esp), %ebx
        movl      76(%esp), %ebx
        sbbl      %edx, %edi
        testl     %ebx, %ebx
        movl      $3, 52(%esp)
        jne       ..L12
        movl      %edx, %edi
..L12:
        xorl      %ebx, %ebp
        movl      %ebp, 48(%esp)
        movl      136(%esp), %ebp
        movl      %edi, 44(%esp)
        movl      %ebp, 56(%esp)
        movl      %eax, 60(%esp)
        movl      %esi, 64(%esp)
        movl      %ecx, 68(%esp)
        addl      $12, %esp
        xorl      %ecx, %ecx
        lea       36(%esp), %esi
        pushl     %ecx
        pushl     %ecx
        pushl     %esi
        movl      40(%esi), %ebx
        call      __dpml_ffs_and_shift__
..B3.245:
        movl      52(%esp), %eax
        movl      %eax, %ecx
        cltd      
        addl      $-3, %ecx
        adcl      $-1, %edx
        orl       %ecx, %edx
        je        ..B3.250
..B3.246:
        addl      $64, %ecx
        movl      64(%esp), %esi
        cmpl      $31, %ecx
        movl      68(%esp), %ebp
        jbe       ..B3.248
..B3.247:
        shrl      %cl, %ebx
        xorl      %edx, %edx
        movl      %ebx, 84(%esp)
        jmp       ..B3.249
..B3.248:
        movl      84(%esp), %edx
        shrdl     %cl, %ebx, %edx
        movl      %edx, 84(%esp)
        movl      %ebx, %edx
        shrl      %cl, %edx
..B3.249:
        orl       84(%esp), %esi
        orl       %edx, %ebp
        movl      %esi, 64(%esp)
        movl      %ebp, 68(%esp)
..B3.250:
        subl      40(%esp), %eax
        movl      %eax, 52(%esp)
        addl      $12, %esp
        lea       36(%esp), %ecx
        pushl     %ecx
        movl      100(%ecx), %eax
        lea       1008+__trig_x_table@GOTOFF(%eax), %edx
        pushl     %edx
        pushl     %ecx
        call      __dpml_multiply__
..B3.251:
        shrl      $30, 44(%esp)
        xorl      %ebx, %ebx
        jmp       ..B3.103
..B3.252:
        movl      %ebp, 84(%esp)
        movl      132(%esp), %ecx
        movl      80(%esp), %esi
        movl      %eax, %edx
        jmp       ..B3.231
..B3.253:
        movl      %esi, %eax
        sarl      $31, %eax
        movl      196(%esp), %ebp
        cltd      
        addl      %ebp, %eax
        movl      %eax, %ebx
        adcl      200(%esp), %edx
        andl      $1, %ebx
        addl      %ebx, %eax
        movl      %eax, %ecx
        adcl      $0, %edx
        shrl      $1, %ecx
        subl      %eax, %ebp
        movl      %ecx, 44(%esp)
        movl      %edx, %ebx
        movl      200(%esp), %ecx
        sbbl      %edx, %ecx
        sarl      $1, %ebx
        orl       %ecx, %ebp
        je        ..B3.258
..B3.254:
        xorl      %eax, %eax
        subl      %eax, %ecx
        jge       ..B3.257
..B3.256:
        movl      $1, %eax
..B3.257:
        addl      $20, %esp
        lea       28(%esp), %edx
        pushl     %edx
        pushl     $0
        pushl     %eax
        movl      100(%edx), %eax
        lea       1008+__trig_x_table@GOTOFF(%eax), %ecx
        pushl     %ecx
        pushl     188(%esp)
        call      __dpml_addsub__
        jmp       ..B3.103
..B3.258:
        movl      %esi, %eax
        movl      %edi, %edx
        movl      104(%esp), %ecx
        movl      100(%esp), %ebp
        movl      108(%esp), %esi
        movl      112(%esp), %edi
        movl      %eax, 48(%esp)
        movl      %edx, 52(%esp)
        movl      %ecx, 56(%esp)
        movl      %ebp, 60(%esp)
        movl      %esi, 64(%esp)
        movl      %edi, 68(%esp)
        jmp       ..B3.103
        .align    16,0x90
	.type	__dpml_ux_sincos,@function
	.size	__dpml_ux_sincos,.-__dpml_ux_sincos
	.data
# -- End  __dpml_ux_sincos
	.section .rodata, "a"
	.align 4
	.align 4
__trig_x_table:
	.long	15442952
	.long	1678786816
	.long	15709192
	.long	1414612240
	.long	15975432
	.long	1141915904
	.long	16241672
	.long	873463808
	.long	16507912
	.long	609305872
	.long	16774152
	.long	336592896
	.long	2
	.long	0
	.long	103
	.long	0
	.long	22
	.long	0
	.long	104
	.long	0
	.long	107
	.long	0
	.long	23
	.long	0
	.long	105
	.long	0
	.long	15176712
	.long	336609536
	.long	115
	.long	0
	.long	15176712
	.long	336592896
	.long	118
	.long	0
	.long	15176712
	.long	519700480
	.long	28
	.long	0
	.long	29
	.long	0
	.long	30
	.long	0
	.long	15176712
	.long	528281504
	.long	34
	.long	0
	.long	32
	.long	0
	.long	33
	.long	0
	.long	35
	.long	0
	.long	36
	.long	0
	.long	0
	.long	4294967291
	.long	2498349230
	.long	2398762258
	.long	2491794649
	.long	247854702
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	95443718
	.long	1527099483
	.long	1431655766
	.long	357913941
	.long	2657305954
	.long	3
	.long	0
	.long	0
	.long	3705764381
	.long	2553
	.long	0
	.long	0
	.long	138031221
	.long	1532340
	.long	0
	.long	0
	.long	2983678984
	.long	775364077
	.long	0
	.long	0
	.long	20739419
	.long	3530365445
	.long	75
	.long	0
	.long	674366505
	.long	499172021
	.long	25931
	.long	0
	.long	1191312077
	.long	2630803580
	.long	7053263
	.long	0
	.long	2382099329
	.long	2712937970
	.long	1481185358
	.long	0
	.long	2412211635
	.long	2311528398
	.long	3431649258
	.long	53
	.long	3364759590
	.long	865053377
	.long	3819263687
	.long	5917
	.long	1745256079
	.long	2154301056
	.long	109078534
	.long	426088
	.long	286331142
	.long	286331153
	.long	286331153
	.long	17895697
	.long	1431655765
	.long	1431655765
	.long	1431655765
	.long	357913941
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	2851833826
	.long	97
	.long	0
	.long	0
	.long	1768459388
	.long	63846
	.long	0
	.long	0
	.long	2009193996
	.long	35243820
	.long	0
	.long	0
	.long	1368406537
	.long	3397743743
	.long	3
	.long	0
	.long	3946395699
	.long	2652434014
	.long	1440
	.long	0
	.long	2553743692
	.long	4190957063
	.long	440828
	.long	0
	.long	595011843
	.long	807348040
	.long	105798954
	.long	0
	.long	883371940
	.long	908455249
	.long	2075540478
	.long	4
	.long	761938407
	.long	3951975904
	.long	3388403475
	.long	591
	.long	217841633
	.long	3490513104
	.long	13634816
	.long	53261
	.long	3626846480
	.long	2195205506
	.long	763549741
	.long	2982616
	.long	1431655403
	.long	1431655765
	.long	1431655765
	.long	89478485
	.long	4294967292
	.long	4294967295
	.long	4294967295
	.long	1073741823
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	2877741414
	.long	48456580
	.long	4555740
	.long	0
	.long	3312728015
	.long	4268104823
	.long	3744444631
	.long	1
	.long	6483522
	.long	3858350617
	.long	1187701358
	.long	879
	.long	3780777519
	.long	2626127639
	.long	2801410624
	.long	158172
	.long	3864078256
	.long	3316933375
	.long	1275824684
	.long	11667904
	.long	1744149096
	.long	2725495384
	.long	3617949641
	.long	318163395
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	3422290646
	.long	426546810
	.long	43450
	.long	0
	.long	3479121894
	.long	3493673009
	.long	236644653
	.long	0
	.long	4247003031
	.long	1084451344
	.long	1285072157
	.long	46
	.long	1927283714
	.long	2972340862
	.long	2243521359
	.long	13184
	.long	739625925
	.long	4125699428
	.long	1446479475
	.long	1522115
	.long	2250255868
	.long	2030226330
	.long	3149913922
	.long	70000510
	.long	312493331
	.long	1293839619
	.long	2186293876
	.long	1033991278
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.long	1
	.long	0
	.long	0
	.long	0
	.long	560513588
	.long	3373259426
	.long	2161908945
	.long	3301335691
	.long	0
	.long	0
	.type	__trig_x_table,@object
	.size	__trig_x_table,1040
	.data
	.hidden __dpml_multiply__
	.hidden __dpml_ffs_and_shift__
	.hidden __dpml_addsub__
	.hidden __dpml_evaluate_rational__
	.section .note.GNU-stack, ""
# End
