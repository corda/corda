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
	.file "dpml_ux_ops.c"
	.text
..TXTST0:
# -- Begin  __dpml_ffs_and_shift__
	.text
       .align    16,0x90
	.hidden __dpml_ffs_and_shift__
	.globl __dpml_ffs_and_shift__
__dpml_ffs_and_shift__:
# parameter 1: 64 + %esp
# parameter 2: 68 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $44, %esp
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        movl      %eax, 28(%esp)
        movl      64(%esp), %ecx
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 40(%esp)
        movl      72(%esp), %eax
        movl      68(%esp), %edx
        movl      8(%ecx), %ebp
        orl       %eax, %edx
        movl      12(%ecx), %ebx
        jne       ..B1.4
..B1.2:
        movl      64(%esp), %eax
        xorl      %ecx, %ecx
        movl      %ebx, %esi
        subl      %ecx, %esi
        movl      4(%eax), %edx
        movl      %edx, 20(%esp)
        jl        ..B1.13
..B1.3:
        movl      64(%esp), %eax
        movl      16(%eax), %esi
        movl      20(%eax), %edx
        jmp       ..B1.9
..B1.4:
        movl      $1, %edx
        movl      68(%esp), %ecx
        subl      %edx, %ecx
        orl       %eax, %ecx
        jne       ..B1.7
..B1.5:
        xorl      %eax, %eax
        movl      %ebx, %edx
        subl      %eax, %edx
        jl        ..B1.16
        jmp       ..B1.8
..B1.7:
        xorl      %eax, %eax
..B1.8:
        movl      64(%esp), %edx
        xorl      %esi, %esi
        movl      $64, 20(%esp)
        movl      %ebp, 8(%edx)
        movl      %ebx, 12(%edx)
        movl      %esi, 16(%edx)
        movl      %esi, 20(%edx)
        movl      %eax, (%edx)
        xorl      %edx, %edx
..B1.9:
        xorl      %ecx, %ecx
        movl      $2, %edi
        xorl      %eax, %eax
        movl      %eax, 24(%esp)
        movl      %ebx, (%esp)
        movl      %ebp, 4(%esp)
        movl      %eax, %ebx
        jmp       ..B1.10
..B1.12:
        xorl      %esi, %esi
        xorl      %edx, %edx
..B1.10:
        movl      4(%esp), %ebp
        orl       (%esp), %ebp
        jne       ..B1.20
..B1.11:
        addl      $64, %eax
        movl      %edx, (%esp)
        adcl      $0, %ebx
        addl      $-1, %edi
        movl      %edi, %edx
        adcl      $-1, %ecx
        movl      %esi, 4(%esp)
        orl       %ecx, %edx
        jne       ..B1.12
        jmp       ..B1.17
..B1.13:
        movl      40(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.15
..B1.14:
        xorl      %eax, %eax
        xorl      %edx, %edx
        addl      $44, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.15:
        movl      28(%esp), %ebx
        call      __stack_chk_fail@PLT
..B1.16:
        xorl      %edx, %edx
        movl      %ebp, %eax
        xorl      %ebp, %ebp
        movl      %ebx, %ecx
        xorl      %ebx, %ebx
        subl      %eax, %ebp
        sbbl      %ecx, %ebx
        movl      $-2147483648, %eax
        jmp       ..B1.8
..B1.17:
        movl      64(%esp), %edx
        movl      %ebx, 24(%esp)
        movl      $-262144, 4(%edx)
        movl      40(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B1.15
..B1.18:
        movl      %ebx, %edx
        addl      $44, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.20:
        movl      %ebx, 24(%esp)
        xorl      %ecx, %ecx
        movl      (%esp), %ebx
        movl      %ebx, %edi
        movl      4(%esp), %ebp
        subl      %ecx, %edi
        jl        ..B1.36
..B1.21:
        movl      %ebx, %ecx
        xorl      %edi, %edi
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %edi
        je        ..B1.23
..B1.22:
        movl      $21932, %edi
        shrl      %cl, %edi
        andl      $3, %edi
        incl      %edi
        movl      %edi, 16(%esp)
        movl      $0, 12(%esp)
        jmp       ..B1.26
..B1.23:
        movl      %ebp, %edi
        movl      %ebx, %ecx
        andl      $-256, %edi
        orl       %edi, %ecx
        jne       ..B1.25
..B1.24:
        movl      %ebp, %edi
..B1.25:
        movl      %edi, (%esp)
        movl      %ebx, %edi
        movl      %ebx, 4(%esp)
        fildll    (%esp)
        shrl      $31, %edi
        movl      28(%esp), %ecx
        movl      %edx, 8(%esp)
        faddl     .L_2il0floatpacket.1@GOTOFF(%ecx,%edi,8)
        movl      $1086, %edi
        fstpl     32(%esp)
        movl      36(%esp), %edx
        movl      %edx, %ecx
        sarl      $20, %ecx
        sarl      $31, %edx
        subl      %ecx, %edi
        movl      $0, %ecx
        sbbl      %edx, %ecx
        movl      8(%esp), %edx
        movl      %edi, 16(%esp)
        movl      %ecx, 12(%esp)
..B1.26:
        cmpl      $31, 16(%esp)
        jbe       ..B1.28
..B1.27:
        movl      %ebp, %ebx
        movl      %edi, %ecx
        movl      $0, 4(%esp)
        shll      %cl, %ebx
        jmp       ..B1.29
..B1.28:
        movl      %edi, %ecx
        shldl     %cl, %ebp, %ebx
        shll      %cl, %ebp
        movl      %ebp, 4(%esp)
..B1.29:
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B1.31
..B1.30:
        movl      %edx, %ebp
        movl      $0, (%esp)
        shrl      %cl, %ebp
        jmp       ..B1.32
..B1.31:
        movl      %edx, %edi
        movl      %esi, %ebp
        shrl      %cl, %edi
        shrdl     %cl, %edx, %ebp
        movl      %edi, (%esp)
..B1.32:
        orl       4(%esp), %ebp
        orl       (%esp), %ebx
        movl      %esi, 8(%esp)
        cmpl      $31, 16(%esp)
        jbe       ..B1.34
..B1.33:
        movl      16(%esp), %ecx
        xorl      %esi, %esi
        movl      8(%esp), %edx
        shll      %cl, %edx
        jmp       ..B1.35
..B1.34:
        movl      16(%esp), %ecx
        shldl     %cl, %esi, %edx
        shll      %cl, %esi
..B1.35:
        addl      16(%esp), %eax
        movl      24(%esp), %ecx
        adcl      12(%esp), %ecx
        movl      %ecx, 24(%esp)
..B1.36:
        movl      64(%esp), %ecx
        movl      %edx, 20(%ecx)
        movl      20(%esp), %edx
        subl      %eax, %edx
        movl      %ebp, 8(%ecx)
        movl      %ebx, 12(%ecx)
        movl      %esi, 16(%ecx)
        movl      %edx, 4(%ecx)
        movl      40(%esp), %ebx
        xorl      %esp, %ebx
        cmpl      %gs:20, %ebx
        jne       ..B1.15
..B1.37:
        movl      24(%esp), %edx
        addl      $44, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_ffs_and_shift__,@function
	.size	__dpml_ffs_and_shift__,.-__dpml_ffs_and_shift__
	.data
# -- End  __dpml_ffs_and_shift__
	.text
# -- Begin  __dpml_unpack2__
	.text
       .align    16,0x90
	.hidden __dpml_unpack2__
	.globl __dpml_unpack2__
__dpml_unpack2__:
# parameter 1: 64 + %esp
# parameter 2: 68 + %esp
# parameter 3: 72 + %esp
# parameter 4: 76 + %esp
# parameter 5: 80 + %esp
# parameter 6: 84 + %esp
# parameter 7: 88 + %esp
..B2.1:
..L3:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $44, %esp
        movl      80(%esp), %ebx
        movl      84(%esp), %eax
        movl      88(%esp), %edx
        movl      %ebx, 12(%esp)
        movl      %eax, 16(%esp)
        movl      %edx, 20(%esp)
        xorl      %edx, %edx
        movl      64(%esp), %eax
        movl      72(%esp), %ecx
        call      __dpml_unpack_x_or_y__..0
..B2.13:
        movl      %eax, %esi
        movl      %edx, %ebp
..B2.2:
        xorl      %ecx, %ecx
        movl      %ebp, %edi
        subl      %ecx, %edi
        jl        ..B2.9
..B2.3:
        cmpl      $0, 68(%esp)
        je        ..B2.9
..B2.4:
        addl      $-32, %esp
        lea       (,%esi,4), %ecx
        movl      8(%ebx), %edi
        cmpl      $31, %ecx
        movl      12(%ebx), %eax
        jbe       ..B2.6
..B2.5:
        sarl      %cl, %eax
        jmp       ..B2.7
..B2.6:
        movl      %eax, %edx
        movl      %edi, %eax
        shrdl     %cl, %edx, %eax
..B2.7:
        andl      $15, %eax
        movl      116(%esp), %edi
        movl      120(%esp), %ecx
        movl      100(%esp), %edx
        lea       (%ebx,%eax,8), %ebx
        movl      %ebx, 12(%esp)
        movl      %edi, 16(%esp)
        movl      %ecx, 20(%esp)
        movl      96(%esp), %eax
        movl      108(%esp), %ecx
        call      __dpml_unpack_x_or_y__.
..B2.8:
        movl      %esi, %ecx
        shrl      $28, %ecx
        shll      $4, %esi
        shll      $4, %ebp
        orl       %eax, %esi
        orl       %ebp, %ecx
        movl      %esi, %eax
        orl       %ecx, %edx
        addl      $76, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B2.9:
        movl      %esi, %eax
        movl      %ebp, %edx
        addl      $44, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_unpack2__,@function
	.size	__dpml_unpack2__,.-__dpml_unpack2__
	.data
# -- End  __dpml_unpack2__
	.text
# -- Begin  __dpml_unpack_x_or_y__..0
	.text
       .align    16,0x90
__dpml_unpack_x_or_y__..0:
# parameter 1: %eax
# parameter 2: %edx
# parameter 3: %ecx
# parameter 4: 156 + %esp
# parameter 5: 160 + %esp
# parameter 6: 164 + %esp
..B3.1:
..L4:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $124, %esp
        call      ..L5
..L5:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L5](%edx), %edx
        movl      %edx, 52(%esp)
        movl      12(%eax), %edx
        movl      %ecx, 64(%esp)
        movl      %edx, %edi
        movl      %eax, 56(%esp)
        andl      $-2147483648, %edi
        movl      %gs:20, %ebp
        xorl      %esp, %ebp
        movl      %ebp, 116(%esp)
        movl      164(%esp), %ebx
        movl      %edi, (%ecx)
        movl      %edx, %ecx
        shrl      $16, %ecx
        andl      $32767, %ecx
        movl      8(%eax), %esi
        movl      %eax, 20(%ebx)
        movl      %edx, %ebx
        movl      %ecx, 60(%esp)
        movl      4(%eax), %edi
        lea       -16382(%ecx), %ebp
        movl      %ebp, 16(%esp)
        movl      %esi, %ebp
        shrl      $17, %ebp
        shll      $15, %esi
        shll      $15, %ebx
        movl      (%eax), %ecx
        movl      %edi, %eax
        shrl      $17, %eax
        orl       %ebx, %ebp
        movl      %esi, %ebx
        orl       $-2147483648, %ebp
        addl      %esi, %ebx
        movl      %ebp, 20(%esp)
        adcl      %ebp, %ebp
        orl       %eax, %esi
        movl      %ecx, %eax
        orl       %edi, %ebp
        shll      $15, %eax
        orl       %ecx, %ebx
        movl      %esi, 76(%esp)
        movl      %ecx, %esi
        movl      %eax, 72(%esp)
        movl      %edi, %eax
        shrl      $17, %esi
        lea       -65536(%edx), %edi
        shll      $15, %eax
        xorl      %ecx, %ecx
        orl       %eax, %esi
        lea       65536(%edx), %eax
        movl      %esi, 68(%esp)
        movl      %edx, %esi
        xorl      %edi, %eax
        shrl      $31, %esi
        movl      %edi, 24(%esp)
        subl      %ecx, %eax
        movl      76(%esp), %edi
        jl        ..B3.39
..B3.2:
        movl      64(%esp), %eax
        movl      72(%esp), %edx
        movl      68(%esp), %ebx
        movl      %edi, 8(%eax)
        movl      20(%esp), %edi
        movl      16(%esp), %ebp
        movl      %edx, 16(%eax)
        movl      $4, %edx
        movl      %ebx, 20(%eax)
        movl      %edi, 12(%eax)
        movl      %ebp, 4(%eax)
..B3.3:
        movl      164(%esp), %eax
        addl      %edx, %esi
        movl      $1, %edx
        movl      %esi, %ecx
        movl      %edx, 32(%esp)
        movl      (%eax), %ebp
        movl      %ebp, %edi
        shll      %cl, %edx
        shll      $10, %ebp
        movl      4(%eax), %ebx
        orl       %edx, %ebp
        shrl      $22, %edi
        shll      $10, %ebx
        movl      %edx, 36(%esp)
        xorl      %edx, %edx
        orl       %ebx, %edi
        movl      %ebp, (%eax)
        orl       %esi, %edx
        movl      %edi, 4(%eax)
        jne       ..B3.5
..B3.4:
        movl      $2139095040, 48(%esp)
        flds      48(%esp)
        flds      48(%esp)
        fsubrp    %st, %st(1)
        fstps     48(%esp)
..B3.5:
        movl      $6, %eax
        mull      %esi
        movl      156(%esp), %ebx
        cmpl      $31, %eax
        movl      (%ebx), %edi
        movl      4(%ebx), %ebx
        jbe       ..B3.7
..B3.6:
        movl      %eax, %ecx
        movl      %ebx, %edi
        sarl      %cl, %edi
        jmp       ..B3.8
..B3.7:
        movl      %eax, %ecx
        shrdl     %cl, %ebx, %edi
..B3.8:
        movl      %edi, %ecx
        xorl      %edx, %edx
        shrl      $3, %edi
        xorl      %eax, %eax
        andl      $7, %edi
        andl      $7, %ecx
        shrl      $28, %ebx
        orl       %edi, %eax
        je        ..B3.32
..B3.9:
        movl      36(%esp), %ebp
        movl      %edx, %eax
        andl      $240, %ebp
        movl      %ebp, 24(%esp)
        subl      %edx, %eax
        jl        ..B3.10
..B3.72:
        orl       %ecx, %eax
        jne       ..B3.14
..B3.10:
        movl      %ecx, %ebx
        orl       %edx, %ebx
        jne       ..B3.12
..B3.11:
        movl      56(%esp), %ebx
        movl      %ebx, 20(%esp)
        jmp       ..B3.13
..B3.12:
        movl      $0, 20(%esp)
..B3.13:
        movl      %ebp, %ebx
        movl      %ebx, 16(%esp)
        jmp       ..B3.15
..B3.14:
        addl      %ecx, %ebx
        movl      156(%esp), %edx
        movl      52(%esp), %ebp
        movl      -8(%edx,%ebx,8), %ecx
        movl      -4(%edx,%ebx,8), %edx
        movl      32(%esp), %ebx
        shll      %cl, %ebx
        andl      $240, %ebx
        movl      %ebx, 16(%esp)
        movl      %ecx, %ebx
        shll      $4, %ebx
        addl      __x_constants__@GOT(%ebp), %ebx
        movl      %ebx, 20(%esp)
..B3.15:
        movl      $7, %ebp
        xorl      %ebx, %ebx
        negl      %ebp
        addl      %edi, %ebp
        orl       %ebx, %ebp
        je        ..B3.36
..B3.16:
        movl      164(%esp), %ebx
        movl      8(%ebx), %ebp
        orl       12(%ebx), %ebp
        je        ..B3.19
..B3.17:
        movl      16(%esp), %ebp
        xorl      %ebx, %ebx
        orl       24(%esp), %ebp
        orl       %ebx, %ebp
        je        ..B3.19
..B3.18:
        movl      $1182793727, 44(%esp)
        flds      44(%esp)
        flds      44(%esp)
        fmulp     %st, %st(1)
        fstps     44(%esp)
..B3.19:
        movl      20(%esp), %ebx
        movl      32(%esp), %eax
        negl      %eax
        movl      8(%ebx), %ebp
        addl      %edi, %eax
        movl      %ebp, 16(%esp)
        movl      12(%ebx), %ebp
        xorl      %ebx, %ebx
        orl       %ebx, %eax
        je        ..B3.35
..B3.20:
        movl      $3, %eax
        negl      %eax
        addl      %edi, %eax
        orl       %ebx, %eax
        je        ..B3.25
..B3.21:
        movl      $4, %eax
        negl      %eax
        addl      %edi, %eax
        orl       %ebx, %eax
        jne       ..B3.23
..B3.22:
        andl      $2147483647, %ebp
        jmp       ..B3.26
..B3.23:
        movl      $5, %ebx
        subl      %ebx, %edi
        xorl      %ebx, %ebx
        orl       %ebx, %edi
        jne       ..B3.26
..B3.24:
        movl      56(%esp), %ebx
        andl      $2147483647, %ebp
        movl      12(%ebx), %edi
        andl      $-2147483648, %edi
        orl       %edi, %ebp
        jmp       ..B3.26
..B3.25:
        xorl      $-2147483648, %ebp
..B3.26:
        movl      160(%esp), %ebx
        movl      16(%esp), %edi
        testl     $192, 36(%esp)
        movl      %ebp, 12(%ebx)
        movl      20(%esp), %ebp
        movl      %edi, 8(%ebx)
        movl      (%ebp), %edi
        movl      4(%ebp), %ebp
        movl      %edi, (%ebx)
        movl      %ebp, 4(%ebx)
        je        ..B3.30
..B3.27:
        xorl      %ebx, %ebx
        subl      %ebx, %edx
        jl        ..B3.28
..B3.73:
        orl       %edx, %ecx
        jne       ..B3.30
..B3.28:
        movl      164(%esp), %edx
        movl      8(%edx), %ebx
        orl       12(%edx), %ebx
        je        ..B3.30
..B3.29:
        movl      52(%esp), %edx
        movl      $8388608, 40(%esp)
        flds      40(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstps     40(%esp)
..B3.30:
        movl      116(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B3.34
..B3.31:
        movl      %esi, %eax
        movl      $-2147483648, %edx
        addl      $124, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B3.32:
        movl      116(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B3.34
..B3.33:
        movl      %esi, %eax
        xorl      %edx, %edx
        addl      $124, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B3.34:
        movl      52(%esp), %ebx
        call      __stack_chk_fail@PLT
..B3.35:
        orl       $32768, %ebp
        jmp       ..B3.26
..B3.36:
        movl      160(%esp), %edi
        andl      $-2013265921, %ecx
        orl       $268435456, %ecx
        movl      %ecx, 56(%esp)
        movl      (%edi), %ebx
        movl      4(%edi), %ebp
        movl      %ebx, 84(%esp)
        movl      %ebp, 88(%esp)
        movl      8(%edi), %ebx
        movl      12(%edi), %ebp
        movl      %ebx, 92(%esp)
        movl      %ebp, 96(%esp)
        movl      %edx, 60(%esp)
        addl      $4, %esp
        lea       52(%esp), %edx
        pushl     %edx
        call      __dpml_exception
..B3.37:
        movl      160(%esp), %ecx
        movl      (%eax), %edx
        movl      4(%eax), %ebx
        movl      %edx, (%ecx)
        movl      %ebx, 4(%ecx)
        movl      8(%eax), %ebp
        movl      12(%eax), %edi
        movl      %ebp, 8(%ecx)
        movl      %edi, 12(%ecx)
        jmp       ..B3.30
..B3.39:
        movl      24(%esp), %ecx
        xorl      %eax, %eax
        xorl      %edx, %ecx
        subl      %eax, %ecx
        jl        ..B3.43
..B3.40:
        movl      64(%esp), %eax
        orl       %ebp, %ebx
        movl      72(%esp), %ecx
        movl      %edi, 8(%eax)
        movl      20(%esp), %edi
        movl      %ecx, 16(%eax)
        movl      %edi, 12(%eax)
        movl      68(%esp), %ecx
        movl      16(%esp), %edi
        movl      %ecx, 20(%eax)
        movl      %edi, 4(%eax)
        jne       ..B3.42
..B3.41:
        movl      $2, %edx
        jmp       ..B3.3
..B3.42:
        shrl      $15, %edx
        movl      %edx, %esi
        xorl      %edx, %edx
        andl      $1, %esi
        jmp       ..B3.3
..B3.43:
        orl       %ebp, %ebx
        jne       ..B3.45
..B3.44:
        movl      64(%esp), %eax
        movl      72(%esp), %edx
        movl      68(%esp), %ebx
        movl      %edi, 8(%eax)
        movl      20(%esp), %edi
        movl      16(%esp), %ebp
        movl      %edx, 16(%eax)
        movl      $8, %edx
        movl      %ebx, 20(%eax)
        movl      %edi, 12(%eax)
        movl      %ebp, 4(%eax)
        jmp       ..B3.3
..B3.45:
        movl      20(%esp), %ebx
        xorl      %edx, %edx
        addl      $-2147483648, %ebx
        movl      %ebx, %eax
        addl      $-16381, 60(%esp)
        movl      %ebx, 20(%esp)
        subl      %edx, %eax
        movl      %edi, 36(%esp)
        jl        ..B3.69
..B3.46:
        movl      72(%esp), %edx
        xorl      %ecx, %ecx
        movl      %edx, 24(%esp)
        movl      $2, %eax
        movl      %edi, 76(%esp)
        xorl      %ebp, %ebp
        movl      68(%esp), %edx
        movl      24(%esp), %edi
        movl      %ecx, 32(%esp)
        movl      %esi, 16(%esp)
        jmp       ..B3.47
..B3.49:
        xorl      %edi, %edi
        xorl      %edx, %edx
..B3.47:
        movl      36(%esp), %esi
        orl       %ebx, %esi
        jne       ..B3.52
..B3.48:
        addl      $64, 32(%esp)
        movl      %edx, %ebx
        movl      %edi, 36(%esp)
        adcl      $0, %ebp
        addl      $-1, %eax
        movl      %eax, %edx
        adcl      $-1, %ecx
        orl       %ecx, %edx
        jne       ..B3.49
..B3.50:
        movl      64(%esp), %ebp
        movl      76(%esp), %edi
        movl      72(%esp), %edx
        movl      %edi, 8(%ebp)
        movl      68(%esp), %ebx
        movl      20(%esp), %edi
        movl      16(%esp), %esi
        movl      %edx, 16(%ebp)
        movl      %ebx, 20(%ebp)
        movl      %edi, 12(%ebp)
        movl      $-262144, 4(%ebp)
..B3.51:
        movl      $2048, 28(%esp)
        movl      $6, %edx
        flds      28(%esp)
        flds      28(%esp)
        faddp     %st, %st(1)
        fstps     28(%esp)
        jmp       ..B3.3
..B3.52:
        movl      %edi, 24(%esp)
        movl      %ebx, %edi
        xorl      %ebp, %ebp
        movl      16(%esp), %esi
        subl      %ebp, %edi
        jl        ..B3.68
..B3.53:
        movl      %ebx, %ecx
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %ebp
        je        ..B3.55
..B3.54:
        movl      $21932, %eax
        shrl      %cl, %eax
        andl      $3, %eax
        incl      %eax
        jmp       ..B3.58
..B3.55:
        movl      36(%esp), %edi
        movl      %ebx, %ebp
        andl      $-256, %edi
        orl       %edi, %ebp
        jne       ..B3.57
..B3.56:
        movl      36(%esp), %edi
..B3.57:
        movl      %edi, 16(%esp)
        movl      %ebx, %edi
        movl      %ebx, 20(%esp)
        fildll    16(%esp)
        shrl      $31, %edi
        movl      52(%esp), %ebp
        faddl     .L_2il0floatpacket.1@GOTOFF(%ebp,%edi,8)
        fstpl     68(%esp)
        movl      72(%esp), %eax
        sarl      $20, %eax
        negl      %eax
        addl      $1086, %eax
..B3.58:
        cmpl      $31, %eax
        jbe       ..B3.60
..B3.59:
        movl      %eax, %ecx
        xorl      %edi, %edi
        movl      36(%esp), %ebx
        shll      %cl, %ebx
        jmp       ..B3.61
..B3.60:
        movl      %eax, %ecx
        movl      36(%esp), %edi
        shldl     %cl, %edi, %ebx
        shll      %cl, %edi
..B3.61:
        movl      %eax, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B3.63
..B3.62:
        movl      %edx, %ebp
        shrl      %cl, %ebp
        movl      $0, 16(%esp)
        movl      %ebp, 36(%esp)
        jmp       ..B3.64
..B3.63:
        movl      24(%esp), %ebp
        shrdl     %cl, %edx, %ebp
        movl      %ebp, 36(%esp)
        movl      %edx, %ebp
        shrl      %cl, %ebp
        movl      %ebp, 16(%esp)
..B3.64:
        orl       %edi, 36(%esp)
        orl       16(%esp), %ebx
        movl      24(%esp), %ebp
        cmpl      $31, %eax
        jbe       ..B3.66
..B3.65:
        movl      %eax, %ecx
        movl      %ebp, %edx
        movl      $0, 24(%esp)
        shll      %cl, %edx
        jmp       ..B3.67
..B3.66:
        movl      %eax, %ecx
        shldl     %cl, %ebp, %edx
        shll      %cl, %ebp
        movl      %ebp, 24(%esp)
..B3.67:
        addl      %eax, 32(%esp)
..B3.68:
        movl      64(%esp), %edi
        movl      36(%esp), %ebp
        movl      %edx, 20(%edi)
        movl      60(%esp), %edx
        movl      %ebx, 12(%edi)
        movl      24(%esp), %ebx
        subl      32(%esp), %edx
        movl      %ebp, 8(%edi)
        movl      %ebx, 16(%edi)
        movl      %edx, 4(%edi)
        jmp       ..B3.51
..B3.69:
        movl      64(%esp), %eax
        movl      72(%esp), %edx
        movl      68(%esp), %ebx
        movl      %edi, 8(%eax)
        movl      20(%esp), %edi
        movl      60(%esp), %ebp
        movl      %edx, 16(%eax)
        movl      %ebx, 20(%eax)
        movl      %edi, 12(%eax)
        movl      %ebp, 4(%eax)
        jmp       ..B3.51
        .align    16,0x90
	.type	__dpml_unpack_x_or_y__..0,@function
	.size	__dpml_unpack_x_or_y__..0,.-__dpml_unpack_x_or_y__..0
	.data
# -- End  __dpml_unpack_x_or_y__..0
	.text
# -- Begin  __dpml_unpack_x_or_y__
	.text
       .align    16,0x90
	.hidden __dpml_unpack_x_or_y__
	.globl __dpml_unpack_x_or_y__
__dpml_unpack_x_or_y__:
# parameter 1: %eax
# parameter 2: %edx
# parameter 3: %ecx
# parameter 4: 172 + %esp
# parameter 5: 176 + %esp
# parameter 6: 180 + %esp
..B4.1:
..L6:

        movl      4(%esp), %eax
        movl      8(%esp), %edx
        movl      12(%esp), %ecx
	.hidden __dpml_unpack_x_or_y__.
	.globl __dpml_unpack_x_or_y__.
__dpml_unpack_x_or_y__.:
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $140, %esp
        xorl      %edi, %edi
        testl     %edx, %edx
        movl      %eax, 68(%esp)
        movl      %edi, %eax
        movl      %ecx, 88(%esp)
        setne     %al
        movl      %edx, 60(%esp)
        movl      %gs:20, %ebp
        xorl      %esp, %ebp
        movl      %ebp, 128(%esp)
        orl       %eax, %edi
        movl      %eax, 56(%esp)
        je        ..B4.3
..B4.2:
        movl      %edx, %eax
        jmp       ..B4.4
..B4.3:
        movl      68(%esp), %eax
..B4.4:
        movl      180(%esp), %edx
        movl      56(%esp), %ebx
        movl      8(%eax), %esi
        movl      %eax, 20(%edx,%ebx,4)
        movl      12(%eax), %edx
        movl      %edx, %edi
        movl      %edx, %ebp
        shrl      $16, %edi
        andl      $-2147483648, %ebp
        movl      %ecx, %ebx
        andl      $32767, %edi
        movl      %edx, %ecx
        shll      $15, %ecx
        movl      %ebp, (%ebx)
        movl      %esi, %ebx
        shrl      $17, %ebx
        lea       -16382(%edi), %ebp
        movl      %edi, 72(%esp)
        orl       %ecx, %ebx
        movl      (%eax), %edi
        orl       $-2147483648, %ebx
        movl      4(%eax), %eax
        movl      %eax, %ecx
        shll      $15, %esi
        movl      %ebp, 24(%esp)
        movl      %esi, %ebp
        shrl      $17, %ecx
        addl      %esi, %ebp
        movl      %ebx, 20(%esp)
        adcl      %ebx, %ebx
        orl       %ecx, %esi
        movl      %esi, 84(%esp)
        movl      %edi, %esi
        shll      $15, %esi
        movl      %edi, %ecx
        movl      %esi, 80(%esp)
        movl      %eax, %esi
        shrl      $17, %ecx
        orl       %edi, %ebp
        shll      $15, %esi
        orl       %eax, %ebx
        orl       %esi, %ecx
        lea       -65536(%edx), %edi
        movl      %edx, %esi
        lea       65536(%edx), %eax
        movl      %edi, 16(%esp)
        xorl      %edi, %eax
        xorl      %edi, %edi
        shrl      $31, %esi
        movl      %ecx, 76(%esp)
        subl      %edi, %eax
        call      ..L7
..L7:
        popl      %edi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L7](%edi), %edi
        movl      %edi, 64(%esp)
        movl      20(%esp), %edi
        jl        ..B4.42
..B4.5:
        movl      88(%esp), %eax
        movl      80(%esp), %edx
        movl      %ecx, %ebx
        movl      %edi, 12(%eax)
        movl      84(%esp), %ebp
        movl      24(%esp), %edi
        movl      %edx, 16(%eax)
        movl      $4, %edx
        movl      %ebx, 20(%eax)
        movl      %ebp, 8(%eax)
        movl      %edi, 4(%eax)
..B4.6:
        movl      180(%esp), %eax
        addl      %edx, %esi
        movl      $1, %edx
        movl      %esi, %ecx
        movl      %edx, 28(%esp)
        movl      (%eax), %ebp
        movl      %ebp, %edi
        shll      %cl, %edx
        shll      $10, %ebp
        movl      4(%eax), %ebx
        orl       %edx, %ebp
        shrl      $22, %edi
        shll      $10, %ebx
        movl      %edx, 36(%esp)
        xorl      %edx, %edx
        orl       %ebx, %edi
        movl      %ebp, (%eax)
        orl       %esi, %edx
        movl      %edi, 4(%eax)
        jne       ..B4.8
..B4.7:
        movl      $2139095040, 52(%esp)
        flds      52(%esp)
        flds      52(%esp)
        fsubrp    %st, %st(1)
        fstps     52(%esp)
..B4.8:
        movl      $6, %eax
        mull      %esi
        movl      172(%esp), %ebx
        cmpl      $31, %eax
        movl      (%ebx), %edx
        movl      4(%ebx), %ebp
        jbe       ..B4.10
..B4.9:
        movl      %eax, %ecx
        movl      %ebp, %edx
        sarl      %cl, %edx
        jmp       ..B4.11
..B4.10:
        movl      %eax, %ecx
        shrdl     %cl, %ebp, %edx
..B4.11:
        movl      %edx, %ecx
        xorl      %ebx, %ebx
        shrl      $3, %edx
        xorl      %edi, %edi
        andl      $7, %edx
        andl      $7, %ecx
        shrl      $28, %ebp
        orl       %edx, %edi
        je        ..B4.35
..B4.12:
        movl      %ecx, %edi
        movl      36(%esp), %eax
        andl      $240, %eax
        subl      56(%esp), %edi
        movl      %eax, 16(%esp)
        movl      %edi, 24(%esp)
        movl      %ebx, %edi
        sbbl      %ebx, %edi
        movl      %edi, 20(%esp)
        jl        ..B4.13
..B4.75:
        movl      24(%esp), %eax
        orl       20(%esp), %eax
        jne       ..B4.17
..B4.13:
        movl      %ecx, %ebp
        orl       %ebx, %ebp
        jne       ..B4.16
..B4.14:
        movl      68(%esp), %ebp
        movl      %ebp, 60(%esp)
..B4.16:
        movl      16(%esp), %ebp
        jmp       ..B4.18
..B4.17:
        addl      %ecx, %ebp
        movl      172(%esp), %ebx
        movl      64(%esp), %eax
        movl      -8(%ebx,%ebp,8), %ecx
        movl      %ecx, %edi
        shll      $4, %edi
        movl      -4(%ebx,%ebp,8), %ebx
        movl      28(%esp), %ebp
        shll      %cl, %ebp
        addl      __x_constants__@GOT(%eax), %edi
        andl      $240, %ebp
        movl      %edi, 60(%esp)
..B4.18:
        movl      $7, %edi
        xorl      %eax, %eax
        negl      %edi
        addl      %edx, %edi
        orl       %eax, %edi
        je        ..B4.39
..B4.19:
        movl      180(%esp), %eax
        movl      8(%eax), %edi
        orl       12(%eax), %edi
        je        ..B4.22
..B4.20:
        orl       16(%esp), %ebp
        xorl      %eax, %eax
        orl       %eax, %ebp
        je        ..B4.22
..B4.21:
        movl      $1182793727, 48(%esp)
        flds      48(%esp)
        flds      48(%esp)
        fmulp     %st, %st(1)
        fstps     48(%esp)
..B4.22:
        movl      60(%esp), %eax
        xorl      %edi, %edi
        movl      8(%eax), %ebp
        movl      %ebp, 16(%esp)
        movl      12(%eax), %ebp
        movl      28(%esp), %eax
        negl      %eax
        addl      %edx, %eax
        orl       %edi, %eax
        je        ..B4.38
..B4.23:
        movl      $3, %edi
        xorl      %eax, %eax
        negl      %edi
        addl      %edx, %edi
        orl       %eax, %edi
        je        ..B4.28
..B4.24:
        movl      $4, %edi
        negl      %edi
        addl      %edx, %edi
        orl       %eax, %edi
        jne       ..B4.26
..B4.25:
        andl      $2147483647, %ebp
        jmp       ..B4.29
..B4.26:
        movl      $5, %eax
        xorl      %edi, %edi
        subl      %eax, %edx
        orl       %edi, %edx
        jne       ..B4.29
..B4.27:
        movl      68(%esp), %edx
        andl      $2147483647, %ebp
        movl      12(%edx), %edx
        andl      $-2147483648, %edx
        orl       %edx, %ebp
        jmp       ..B4.29
..B4.28:
        xorl      $-2147483648, %ebp
..B4.29:
        movl      176(%esp), %edx
        movl      16(%esp), %eax
        testl     $192, 36(%esp)
        movl      %ebp, 12(%edx)
        movl      60(%esp), %ebp
        movl      %eax, 8(%edx)
        movl      (%ebp), %edi
        movl      4(%ebp), %ebp
        movl      %edi, (%edx)
        movl      %ebp, 4(%edx)
        je        ..B4.33
..B4.30:
        xorl      %edx, %edx
        subl      56(%esp), %ecx
        sbbl      %edx, %ebx
        jl        ..B4.31
..B4.76:
        orl       %ebx, %ecx
        jne       ..B4.33
..B4.31:
        movl      180(%esp), %edx
        movl      8(%edx), %ebx
        orl       12(%edx), %ebx
        je        ..B4.33
..B4.32:
        movl      64(%esp), %edx
        movl      $8388608, 44(%esp)
        flds      44(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%edx)
        fmulp     %st, %st(1)
        fstps     44(%esp)
..B4.33:
        movl      128(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B4.37
..B4.34:
        movl      %esi, %eax
        movl      $-2147483648, %edx
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B4.35:
        movl      128(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B4.37
..B4.36:
        movl      %esi, %eax
        xorl      %edx, %edx
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B4.37:
        movl      64(%esp), %ebx
        call      __stack_chk_fail@PLT
..B4.38:
        orl       $32768, %ebp
        jmp       ..B4.29
..B4.39:
        movl      176(%esp), %eax
        andl      $-2013265921, %ecx
        orl       $268435456, %ecx
        movl      %ecx, 68(%esp)
        movl      (%eax), %edx
        movl      4(%eax), %ebp
        movl      %edx, 96(%esp)
        movl      %ebp, 100(%esp)
        movl      8(%eax), %edx
        movl      12(%eax), %ebp
        movl      %edx, 104(%esp)
        movl      %ebp, 108(%esp)
        movl      %ebx, 72(%esp)
        addl      $4, %esp
        lea       64(%esp), %ebx
        pushl     %ebx
        call      __dpml_exception
..B4.40:
        movl      176(%esp), %ecx
        movl      (%eax), %edx
        movl      4(%eax), %ebx
        movl      %edx, (%ecx)
        movl      %ebx, 4(%ecx)
        movl      8(%eax), %ebp
        movl      12(%eax), %edi
        movl      %ebp, 8(%ecx)
        movl      %edi, 12(%ecx)
        jmp       ..B4.33
..B4.42:
        movl      16(%esp), %ecx
        xorl      %eax, %eax
        xorl      %edx, %ecx
        subl      %eax, %ecx
        jl        ..B4.46
..B4.43:
        movl      88(%esp), %eax
        orl       %ebx, %ebp
        movl      80(%esp), %ecx
        movl      %ecx, 16(%eax)
        movl      76(%esp), %ecx
        movl      %ecx, 20(%eax)
        movl      %edi, 12(%eax)
        movl      84(%esp), %ecx
        movl      24(%esp), %edi
        movl      %ecx, 8(%eax)
        movl      %edi, 4(%eax)
        jne       ..B4.45
..B4.44:
        movl      $2, %edx
        jmp       ..B4.6
..B4.45:
        shrl      $15, %edx
        movl      %edx, %esi
        xorl      %edx, %edx
        andl      $1, %esi
        jmp       ..B4.6
..B4.46:
        orl       %ebx, %ebp
        jne       ..B4.48
..B4.47:
        movl      88(%esp), %eax
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        movl      %edi, 12(%eax)
        movl      84(%esp), %ebp
        movl      24(%esp), %edi
        movl      %edx, 16(%eax)
        movl      $8, %edx
        movl      %ebx, 20(%eax)
        movl      %ebp, 8(%eax)
        movl      %edi, 4(%eax)
        jmp       ..B4.6
..B4.48:
        addl      $-2147483648, %edi
        movl      %edi, %ebp
        addl      $-16381, 72(%esp)
        movl      84(%esp), %edx
        subl      %eax, %ebp
        movl      %edi, 40(%esp)
        jl        ..B4.72
..B4.49:
        movl      80(%esp), %ebp
        movl      76(%esp), %ebx
        movl      %ebp, 28(%esp)
        movl      $2, %ebp
        movl      %ebx, 24(%esp)
        xorl      %ebx, %ebx
        movl      %edi, 20(%esp)
        movl      %esi, 16(%esp)
        movl      %eax, 36(%esp)
        movl      28(%esp), %esi
        movl      24(%esp), %edi
        jmp       ..B4.50
..B4.52:
        xorl      %esi, %esi
        xorl      %edi, %edi
..B4.50:
        movl      %edx, %ecx
        orl       40(%esp), %ecx
        jne       ..B4.55
..B4.51:
        addl      $64, 36(%esp)
        movl      %esi, %edx
        movl      %edi, 40(%esp)
        adcl      $0, %ebx
        addl      $-1, %ebp
        movl      %ebp, %ecx
        adcl      $-1, %eax
        orl       %eax, %ecx
        jne       ..B4.52
..B4.53:
        movl      88(%esp), %eax
        movl      20(%esp), %edi
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        movl      84(%esp), %ebp
        movl      16(%esp), %esi
        movl      %edx, 16(%eax)
        movl      %ebx, 20(%eax)
        movl      %ebp, 8(%eax)
        movl      %edi, 12(%eax)
        movl      $-262144, 4(%eax)
..B4.54:
        movl      $2048, 32(%esp)
        movl      $6, %edx
        flds      32(%esp)
        flds      32(%esp)
        faddp     %st, %st(1)
        fstps     32(%esp)
        jmp       ..B4.6
..B4.55:
        xorl      %ebx, %ebx
        movl      40(%esp), %ebp
        movl      %esi, 28(%esp)
        subl      %ebx, %ebp
        movl      %edi, 24(%esp)
        movl      16(%esp), %esi
        jl        ..B4.71
..B4.56:
        movl      40(%esp), %ecx
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %ebx
        je        ..B4.58
..B4.57:
        movl      $21932, %ebp
        shrl      %cl, %ebp
        andl      $3, %ebp
        incl      %ebp
        jmp       ..B4.61
..B4.58:
        movl      %edx, %ebp
        andl      $-256, %ebp
        movl      40(%esp), %ebx
        orl       %ebp, %ebx
        jne       ..B4.60
..B4.59:
        movl      %edx, %ebp
..B4.60:
        movl      40(%esp), %ebx
        movl      %ebp, 16(%esp)
        movl      %ebx, 20(%esp)
        fildll    16(%esp)
        shrl      $31, %ebx
        movl      64(%esp), %ebp
        faddl     .L_2il0floatpacket.1@GOTOFF(%ebp,%ebx,8)
        fstpl     76(%esp)
        movl      80(%esp), %ebp
        sarl      $20, %ebp
        negl      %ebp
        addl      $1086, %ebp
..B4.61:
        cmpl      $31, %ebp
        jbe       ..B4.63
..B4.62:
        movl      %ebp, %ecx
        xorl      %ebx, %ebx
        shll      %cl, %edx
        movl      %edx, 40(%esp)
        jmp       ..B4.64
..B4.63:
        movl      %ebp, %ecx
        movl      40(%esp), %ebx
        shldl     %cl, %edx, %ebx
        movl      %ebx, 40(%esp)
        movl      %edx, %ebx
        shll      %cl, %ebx
..B4.64:
        movl      %ebp, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B4.66
..B4.65:
        movl      %edi, %edx
        xorl      %edi, %edi
        shrl      %cl, %edx
        jmp       ..B4.67
..B4.66:
        movl      28(%esp), %edx
        shrdl     %cl, %edi, %edx
        shrl      %cl, %edi
..B4.67:
        orl       %edi, 40(%esp)
        orl       %ebx, %edx
        movl      28(%esp), %ebx
        cmpl      $31, %ebp
        jbe       ..B4.69
..B4.68:
        movl      %ebp, %ecx
        shll      %cl, %ebx
        movl      $0, 28(%esp)
        movl      %ebx, 24(%esp)
        jmp       ..B4.70
..B4.69:
        movl      %ebp, %ecx
        movl      24(%esp), %edi
        shldl     %cl, %ebx, %edi
        shll      %cl, %ebx
        movl      %edi, 24(%esp)
        movl      %ebx, 28(%esp)
..B4.70:
        addl      %ebp, 36(%esp)
..B4.71:
        movl      88(%esp), %eax
        movl      72(%esp), %edi
        movl      28(%esp), %ebx
        movl      %edx, 8(%eax)
        movl      40(%esp), %edx
        movl      24(%esp), %ebp
        subl      36(%esp), %edi
        movl      %edx, 12(%eax)
        movl      %ebx, 16(%eax)
        movl      %ebp, 20(%eax)
        movl      %edi, 4(%eax)
        jmp       ..B4.54
..B4.72:
        movl      88(%esp), %eax
        movl      80(%esp), %edx
        movl      76(%esp), %ebx
        movl      %edi, 12(%eax)
        movl      84(%esp), %ebp
        movl      72(%esp), %edi
        movl      %edx, 16(%eax)
        movl      %ebx, 20(%eax)
        movl      %ebp, 8(%eax)
        movl      %edi, 4(%eax)
        jmp       ..B4.54
        .align    16,0x90
	.type	__dpml_unpack_x_or_y__,@function
	.size	__dpml_unpack_x_or_y__,.-__dpml_unpack_x_or_y__
	.data
# -- End  __dpml_unpack_x_or_y__
	.text
# -- Begin  __dpml_pack__
	.text
       .align    16,0x90
	.hidden __dpml_pack__
	.globl __dpml_pack__
__dpml_pack__:
# parameter 1: 160 + %esp
# parameter 2: 164 + %esp
# parameter 3: 168 + %esp
# parameter 4: 176 + %esp
# parameter 5: 184 + %esp
..B5.1:
..L8:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $140, %esp
        movl      160(%esp), %edx
        call      ..L9
..L9:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L9](%ecx), %ecx
        movl      %ecx, 48(%esp)
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 136(%esp)
        movl      12(%edx), %esi
        movl      %esi, %edi
        movl      8(%edx), %ecx
        movl      %ecx, %ebp
        movl      4(%edx), %eax
        xorl      %edx, %edx
        movl      %esi, %ebx
        subl      %edx, %edi
        jl        ..B5.7
..B5.2:
        movl      160(%esp), %edi
        movl      $2, 32(%esp)
        movl      %ebx, 68(%esp)
        movl      16(%edi), %edx
        movl      %edx, 56(%esp)
        movl      20(%edi), %edx
        xorl      %edi, %edi
        movl      %edi, 52(%esp)
        movl      %esi, 44(%esp)
        movl      %ecx, 36(%esp)
        movl      %eax, 60(%esp)
        movl      %ebp, 64(%esp)
        movl      56(%esp), %esi
        movl      %edi, %ebx
        movl      52(%esp), %eax
        movl      32(%esp), %ecx
        jmp       ..B5.3
..B5.5:
        xorl      %esi, %esi
        xorl      %edx, %edx
..B5.3:
        movl      64(%esp), %ebp
        orl       68(%esp), %ebp
        jne       ..B5.47
..B5.4:
        addl      $64, %eax
        movl      %edx, 68(%esp)
        adcl      $0, %ebx
        addl      $-1, %ecx
        movl      %ecx, %edx
        adcl      $-1, %edi
        movl      %esi, 64(%esp)
        orl       %edi, %edx
        jne       ..B5.5
..B5.6:
        movl      160(%esp), %eax
        movl      44(%esp), %esi
        movl      36(%esp), %ecx
        movl      $-262144, 4(%eax)
        movl      $-262144, %eax
..B5.7:
        cmpl      $-262144, %eax
        je        ..B5.44
..B5.8:
        movl      $-16381, %ebx
        movl      $-1, %ebp
        cltd      
        subl      %eax, %ebx
        sbbl      %edx, %ebp
        xorl      %edi, %edi
        movl      %ebp, %edx
        subl      %edi, %edx
        jl        ..B5.16
..B5.66:
        orl       %ebx, %edx
        je        ..B5.16
..B5.9:
        movl      160(%esp), %edx
        addl      %ebx, %eax
        movl      %eax, 116(%esp)
        lea       112(%esp), %eax
        movl      %edx, %ecx
        movl      (%edx), %esi
        movl      %edi, 8(%esp)
        movl      %edi, 8(%eax)
        movl      $-2147483648, 12(%eax)
        movl      %edi, 16(%eax)
        movl      %edi, 20(%eax)
        movl      %esi, (%eax)
        movl      %edi, 12(%esp)
        call      __dpml_addsub__..0
..B5.10:
        movl      $113, %eax
        movl      %ebx, %ecx
        subl      %eax, %ecx
        movl      %ebp, %eax
        sbbl      %edi, %eax
        jl        ..B5.15
..B5.67:
        orl       %eax, %ecx
        je        ..B5.15
..B5.11:
        movl      $245763, %eax
        negl      %eax
        addl      %ebx, %eax
        orl       %ebp, %eax
        je        ..B5.39
..B5.12:
        movl      $49155, %edx
        movl      $-16383, %eax
        subl      %edx, %ebx
        sbbl      %edi, %ebp
        jl        ..B5.14
..B5.68:
        orl       %ebp, %ebx
        je        ..B5.14
..B5.13:
        movl      160(%esp), %edx
        decl      %eax
        movl      8(%edx), %ecx
        movl      12(%edx), %esi
        jmp       ..B5.16
..B5.14:
        movl      160(%esp), %edx
        movl      8(%edx), %ecx
        movl      12(%edx), %esi
        jmp       ..B5.16
..B5.15:
        movl      160(%esp), %edx
        movl      $-16382, %eax
        movl      8(%edx), %ecx
        movl      12(%edx), %esi
..B5.16:
        movl      160(%esp), %ebx
        movl      $16384, %edx
        movl      16(%ebx), %edi
        addl      $16384, %edi
        movl      %edi, %ebp
        movl      20(%ebx), %ebx
        adcl      $0, %ebx
        subl      %edx, %ebp
        movl      $0, %edx
        movl      %ebx, %ebp
        sbbl      %edx, %ebp
        jae       ..B5.18
..B5.17:
        movl      $1, %ebp
        jmp       ..B5.19
..B5.18:
        xorl      %ebp, %ebp
..B5.19:
        movl      %ebx, %edx
        addl      %ebp, %ecx
        adcl      $0, %esi
        shll      $17, %edx
        shrl      $15, %edi
        orl       %edi, %edx
        movl      %ecx, %edi
        shrl      $15, %ebx
        subl      %ebp, %edi
        movl      $0, %ebp
        movl      %esi, %edi
        sbbl      %ebp, %edi
        jae       ..B5.22
..B5.20:
        movl      $1, %ebp
..B5.22:
        movl      %ecx, %edi
        shll      $17, %edi
        orl       %edi, %ebx
        movl      164(%esp), %edi
        shrl      $15, %ecx
        movl      %ebx, 4(%edi)
        movl      %esi, %ebx
        shll      $17, %ebx
        movl      %edx, (%edi)
        xorl      %edx, %edx
        shrl      $15, %esi
        orl       %ecx, %ebx
        orl       %edx, %ebp
        je        ..B5.24
..B5.23:
        incl      %eax
        xorl      %ebx, %ebx
        movl      $65536, %esi
..B5.24:
        movl      %eax, %ecx
        cltd      
        addl      $16381, %ecx
        movl      %edx, %ebp
        movl      %ecx, %edx
        adcl      $0, %ebp
        shll      $16, %edx
        addl      %esi, %edx
        movl      160(%esp), %esi
        movl      (%esi), %edi
        orl       %edi, %edx
        movl      %edi, 32(%esp)
        movl      $32766, %esi
        movl      %ecx, %edi
        subl      %esi, %edi
        movl      $0, %esi
        sbbl      %esi, %ebp
        jae       ..B5.29
..B5.25:
        movl      164(%esp), %eax
        movl      %edx, 12(%eax)
        movl      184(%esp), %edx
        movl      %ebx, 8(%eax)
        movl      8(%edx), %ecx
        orl       12(%edx), %ecx
        je        ..B5.27
..B5.26:
        movl      $1182793727, 40(%esp)
        flds      40(%esp)
        flds      40(%esp)
        fmulp     %st, %st(1)
        fstps     40(%esp)
..B5.27:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B5.40
..B5.28:
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B5.29:
        testl     %eax, %eax
        movl      168(%esp), %ebp
        movl      172(%esp), %eax
        jl        ..L10
        movl      176(%esp), %ebp
..L10:
        jl        ..L11
        movl      180(%esp), %eax
..L11:
        incl      %ecx
        testl     %ecx, %ecx
        jg        ..B5.35
..B5.30:
        js        ..B5.42
..B5.31:
        movl      164(%esp), %ecx
        movl      %edx, 12(%ecx)
        movl      184(%esp), %edx
        movl      %ebx, 8(%ecx)
        movl      8(%edx), %ebx
        orl       12(%edx), %ebx
        je        ..B5.33
..B5.32:
        orl       $-2147483648, %eax
        jmp       ..B5.36
..B5.33:
        movl      136(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B5.40
..B5.34:
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B5.35:
        movl      164(%esp), %ecx
        movl      %ebx, 8(%ecx)
        movl      %edx, 12(%ecx)
..B5.36:
        movl      164(%esp), %ebx
        andl      $-2013265921, %ebp
        orl       $268435456, %ebp
        movl      %ebp, 52(%esp)
        movl      (%ebx), %edx
        movl      4(%ebx), %ecx
        movl      8(%ebx), %esi
        movl      12(%ebx), %edi
        movl      %edx, 80(%esp)
        movl      %ecx, 84(%esp)
        movl      %esi, 88(%esp)
        movl      %edi, 92(%esp)
        movl      %eax, 56(%esp)
        addl      $4, %esp
        lea       48(%esp), %eax
        pushl     %eax
        call      __dpml_exception
..B5.69:
        movl      %eax, %ecx
..B5.37:
        movl      164(%esp), %ebp
        movl      (%ecx), %eax
        movl      4(%ecx), %edx
        movl      %eax, (%ebp)
        movl      %edx, 4(%ebp)
        movl      8(%ecx), %ebx
        movl      12(%ecx), %ecx
        movl      %ebx, 8(%ebp)
        movl      %ecx, 12(%ebp)
        movl      136(%esp), %esi
        xorl      %esp, %esi
        cmpl      %gs:20, %esi
        jne       ..B5.40
..B5.38:
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B5.39:
        movl      $-16382, %eax
        jmp       ..B5.13
..B5.40:
        movl      48(%esp), %ebx
        call      __stack_chk_fail@PLT
..B5.42:
        movl      164(%esp), %ecx
        movl      32(%esp), %edx
        movl      $0, 8(%ecx)
        movl      %edx, 12(%ecx)
        jmp       ..B5.36
..B5.44:
        movl      160(%esp), %eax
        xorl      %edx, %edx
        movl      164(%esp), %ebx
        movl      (%eax), %ecx
        movl      %edx, (%ebx)
        movl      %edx, 4(%ebx)
        movl      %edx, 8(%ebx)
        movl      %ecx, 12(%ebx)
        movl      136(%esp), %ebp
        xorl      %esp, %ebp
        cmpl      %gs:20, %ebp
        jne       ..B5.40
        jmp       ..B5.28
..B5.47:
        movl      68(%esp), %ebx
        xorl      %ecx, %ecx
        movl      %esi, 56(%esp)
        movl      %ebx, %esi
        movl      %eax, 52(%esp)
        subl      %ecx, %esi
        movl      60(%esp), %eax
        movl      64(%esp), %ebp
        jl        ..B5.63
..B5.48:
        movl      %ebx, %ecx
        xorl      %esi, %esi
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %esi
        je        ..B5.50
..B5.49:
        movl      $21932, %esi
        shrl      %cl, %esi
        andl      $3, %esi
        incl      %esi
        jmp       ..B5.53
..B5.50:
        movl      %ebp, %esi
        movl      %ebx, %ecx
        andl      $-256, %esi
        orl       %esi, %ecx
        jne       ..B5.52
..B5.51:
        movl      %ebp, %esi
..B5.52:
        movl      %esi, 32(%esp)
        movl      %ebx, %esi
        movl      %ebx, 36(%esp)
        fildll    32(%esp)
        shrl      $31, %esi
        movl      48(%esp), %ecx
        faddl     .L_2il0floatpacket.1@GOTOFF(%ecx,%esi,8)
        fstpl     60(%esp)
        movl      64(%esp), %esi
        sarl      $20, %esi
        negl      %esi
        addl      $1086, %esi
..B5.53:
        cmpl      $31, %esi
        jbe       ..B5.55
..B5.54:
        movl      %esi, %ecx
        movl      %ebp, %ebx
        movl      $0, 32(%esp)
        shll      %cl, %ebx
        jmp       ..B5.56
..B5.55:
        movl      %esi, %ecx
        shldl     %cl, %ebp, %ebx
        shll      %cl, %ebp
        movl      %ebp, 32(%esp)
..B5.56:
        movl      %esi, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B5.58
..B5.57:
        movl      %edx, %ebp
        movl      $0, 36(%esp)
        shrl      %cl, %ebp
        jmp       ..B5.59
..B5.58:
        movl      %edx, %edi
        movl      56(%esp), %ebp
        shrl      %cl, %edi
        shrdl     %cl, %edx, %ebp
        movl      %edi, 36(%esp)
..B5.59:
        orl       32(%esp), %ebp
        orl       36(%esp), %ebx
        movl      56(%esp), %edi
        cmpl      $31, %esi
        jbe       ..B5.61
..B5.60:
        movl      %esi, %ecx
        movl      %edi, %edx
        movl      $0, 56(%esp)
        shll      %cl, %edx
        jmp       ..B5.62
..B5.61:
        movl      %esi, %ecx
        shldl     %cl, %edi, %edx
        shll      %cl, %edi
        movl      %edi, 56(%esp)
..B5.62:
        addl      %esi, 52(%esp)
..B5.63:
        movl      160(%esp), %edi
        movl      %ebx, %esi
        subl      52(%esp), %eax
        movl      %ebp, %ecx
        movl      %ebx, 12(%edi)
        movl      56(%esp), %ebx
        movl      %ebp, 8(%edi)
        movl      %ebx, 16(%edi)
        movl      %edx, 20(%edi)
        movl      %eax, 4(%edi)
        jmp       ..B5.7
        .align    16,0x90
	.type	__dpml_pack__,@function
	.size	__dpml_pack__,.-__dpml_pack__
	.data
# -- End  __dpml_pack__
	.text
# -- Begin  __dpml_addsub__..0
	.text
       .align    16,0x90
__dpml_addsub__..0:
# parameter 1: %eax
# parameter 2: %edx
# parameter 3: 184 + %esp
# parameter 4: %ecx
..B6.1:
..L12:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $156, %esp
        movl      %edx, %edi
        movl      %eax, %ebx
        movl      %ecx, 132(%esp)
        movl      %gs:20, %ebp
        xorl      %esp, %ebp
        movl      %ebp, 144(%esp)
        movl      (%ebx), %ebp
        movl      (%edi), %eax
        xorl      %ebp, %eax
        shrl      $31, %eax
        movl      4(%ebx), %ecx
        movl      %eax, 124(%esp)
        movl      %ecx, %eax
        cltd      
        movl      4(%edi), %eax
        movl      %edx, %esi
        cltd      
        movl      %edx, %eax
        movl      %ecx, %edx
        subl      4(%edi), %edx
        movl      %ecx, 104(%esp)
        sbbl      %eax, %esi
        xorl      %eax, %eax
        movl      %esi, %ecx
        movl      $0, 112(%esp)
        subl      %eax, %ecx
        movl      104(%esp), %eax
        jl        ..B6.3
..B6.2:
        movl      $0, 92(%esp)
        jmp       ..B6.7
..B6.3:
        movl      %ebx, %ecx
        movl      %edi, %ebx
        movl      %ecx, %edi
        movl      %edx, %ecx
        xorl      %edx, %edx
        subl      %ecx, %edx
        movl      %esi, %ecx
        movl      $0, %esi
        sbbl      %ecx, %esi
        addl      %edx, %eax
        movl      124(%esp), %ecx
        orl       112(%esp), %ecx
        jne       ..B6.5
..B6.4:
        xorl      %ecx, %ecx
        jmp       ..B6.6
..B6.5:
        movl      $-2147483648, %ecx
..B6.6:
        movl      $-2147483648, 92(%esp)
        xorl      %ecx, %ebp
..B6.7:
        movl      16(%edi), %ecx
        movl      %ecx, 100(%esp)
        movl      20(%edi), %ecx
        movl      %ecx, 96(%esp)
        movl      8(%edi), %ecx
        movl      12(%edi), %edi
        movl      %ecx, 120(%esp)
        movl      %edi, 116(%esp)
        movl      $2, %edi
        movl      $0, (%esp)
        call      ..L13
..L13:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L13](%ecx), %ecx
        movl      %ebp, 108(%esp)
        movl      %ecx, 8(%esp)
        movl      %edi, 4(%esp)
        movl      %eax, 104(%esp)
        movl      %ebx, 128(%esp)
        movl      (%esp), %ebp
        jmp       ..B6.8
..B6.10:
        xorl      %eax, %eax
        movl      %eax, 120(%esp)
        movl      %eax, 116(%esp)
..B6.8:
        movl      $64, %ebx
        xorl      %edi, %edi
        xorl      %eax, %eax
        subl      %edx, %ebx
        sbbl      %esi, %eax
        movl      %eax, %ecx
        subl      %edi, %ecx
        jl        ..B6.9
..B6.85:
        orl       %ebx, %ecx
        jne       ..B6.14
..B6.9:
        movl      116(%esp), %esi
        movl      120(%esp), %edx
        movl      %esi, 96(%esp)
        xorl      %esi, %esi
        movl      %edx, 100(%esp)
        xorl      %edx, %edx
        subl      %ebx, %edx
        sbbl      %eax, %esi
        movl      4(%esp), %eax
        addl      $-1, %eax
        movl      %eax, 4(%esp)
        adcl      $-1, %ebp
        orl       %ebp, %eax
        jne       ..B6.10
..B6.11:
        movl      132(%esp), %eax
        movl      128(%esp), %ebx
        movl      8(%ebx), %ecx
        movl      12(%ebx), %esi
        movl      %ecx, 8(%eax)
        movl      %esi, 12(%eax)
        movl      4(%ebx), %edx
        movl      16(%ebx), %edi
        movl      20(%ebx), %ebx
        movl      108(%esp), %ebp
        movl      %edx, 4(%eax)
        movl      %edi, 16(%eax)
        movl      %ebx, 20(%eax)
        movl      %ebp, (%eax)
        movl      144(%esp), %ebp
        xorl      %esp, %ebp
        cmpl      %gs:20, %ebp
        jne       ..B6.13
..B6.12:
        addl      $156, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B6.13:
        movl      8(%esp), %ebx
        call      __stack_chk_fail@PLT
..B6.14:
        movl      %ebx, 4(%esp)
        orl       %edx, %esi
        movl      108(%esp), %ebp
        movl      104(%esp), %eax
        movl      128(%esp), %ebx
        je        ..B6.25
..B6.15:
        cmpl      $31, %edx
        jbe       ..B6.17
..B6.16:
        movl      %edx, %ecx
        xorl      %esi, %esi
        movl      96(%esp), %edi
        shrl      %cl, %edi
        movl      %edi, 100(%esp)
        jmp       ..B6.18
..B6.17:
        movl      %edx, %ecx
        movl      100(%esp), %edi
        movl      96(%esp), %esi
        shrdl     %cl, %esi, %edi
        movl      %edi, 100(%esp)
        shrl      %cl, %esi
..B6.18:
        cmpl      $31, 4(%esp)
        jbe       ..B6.20
..B6.19:
        movl      4(%esp), %ecx
        movl      120(%esp), %edi
        shll      %cl, %edi
        movl      $0, (%esp)
        movl      %edi, 96(%esp)
        jmp       ..B6.21
..B6.20:
        movl      %ebp, 108(%esp)
        movl      4(%esp), %ecx
        movl      116(%esp), %edi
        movl      120(%esp), %ebp
        shldl     %cl, %ebp, %edi
        shll      %cl, %ebp
        movl      %ebp, (%esp)
        movl      %edi, 96(%esp)
        movl      108(%esp), %ebp
..B6.21:
        movl      (%esp), %edi
        orl       %edi, 100(%esp)
        orl       %esi, 96(%esp)
        movl      116(%esp), %esi
        cmpl      $31, %edx
        jbe       ..B6.23
..B6.22:
        movl      %edx, %ecx
        shrl      %cl, %esi
        movl      $0, 116(%esp)
        movl      %esi, 120(%esp)
..B6.25:
        xorl      %esi, %esi
        movl      %ebp, 108(%esp)
        movl      %esi, 48(%esp)
        movl      %eax, 104(%esp)
        movl      %ebx, 128(%esp)
        jmp       ..B6.26
..B6.23:
        movl      %edx, %ecx
        movl      120(%esp), %edi
        shrdl     %cl, %esi, %edi
        shrl      %cl, %esi
        movl      %edi, 120(%esp)
        movl      %esi, 116(%esp)
        jmp       ..B6.25
..B6.55:
        movl      128(%esp), %edi
        movl      $1, %edx
        movl      124(%esp), %eax
        xorl      %ecx, %ecx
        subl      %eax, %edx
        movl      108(%esp), %ebp
        movl      4(%edi), %eax
        movl      112(%esp), %ebx
        sbbl      %ebx, %ecx
        xorl      92(%esp), %ebp
        xorl      $2, %esi
        addl      $24, 132(%esp)
        movl      %edx, 124(%esp)
        movl      %ecx, 112(%esp)
        movl      %ebp, 108(%esp)
        movl      %eax, 104(%esp)
..B6.26:
        movl      128(%esp), %edx
        movl      124(%esp), %eax
        orl       112(%esp), %eax
        movl      16(%edx), %ebx
        movl      20(%edx), %edx
        jne       ..B6.38
..B6.27:
        movl      %ebx, %ebp
        movl      %edx, %ecx
        addl      100(%esp), %ebp
        movl      %ebp, %eax
        adcl      96(%esp), %ecx
        andl      $15, %esi
        subl      %ebx, %eax
        movl      %ecx, %ebx
        movl      $0, 48(%esp)
        sbbl      %edx, %ebx
        jae       ..B6.29
..B6.28:
        movl      $1, 72(%esp)
        jmp       ..B6.30
..B6.29:
        movl      $0, 72(%esp)
..B6.30:
        movl      128(%esp), %edi
        movl      72(%esp), %edx
        movl      8(%edi), %ebx
        movl      %ebx, 52(%esp)
        movl      120(%esp), %ebx
        movl      12(%edi), %eax
        addl      %edx, %ebx
        movl      %eax, 80(%esp)
        movl      %ebx, %eax
        movl      116(%esp), %edi
        adcl      $0, %edi
        subl      %edx, %eax
        movl      $0, %edx
        movl      %edi, %eax
        sbbl      %edx, %eax
        jae       ..B6.32
..B6.31:
        movl      $1, 68(%esp)
        jmp       ..B6.33
..B6.32:
        movl      $0, 68(%esp)
..B6.33:
        movl      52(%esp), %eax
        addl      %eax, %ebx
        movl      %ebx, %edx
        adcl      80(%esp), %edi
        subl      %eax, %edx
        movl      %edi, %eax
        sbbl      80(%esp), %eax
        jae       ..B6.35
..B6.34:
        movl      $1, %edx
        jmp       ..B6.36
..B6.35:
        xorl      %edx, %edx
..B6.36:
        movl      68(%esp), %eax
        addl      %edx, %eax
        xorl      %edx, %edx
        orl       %edx, %eax
        je        ..B6.52
..B6.37:
        movl      %ecx, %eax
        movl      %ebx, %edx
        shll      $31, %eax
        shrl      $1, %ebp
        orl       %eax, %ebp
        movl      %edi, %eax
        shrl      $1, %ecx
        shll      $31, %edx
        shll      $31, %eax
        orl       %edx, %ecx
        shrl      $1, %edi
        shrl      $1, %ebx
        orl       $-2147483648, %edi
        incl      104(%esp)
        orl       %eax, %ebx
        jmp       ..B6.52
..B6.38:
        addl      $-8, %esi
        movl      100(%esp), %ecx
        adcl      $-1, 48(%esp)
        subl      %ebx, %ecx
        movl      96(%esp), %eax
        sbbl      %edx, %eax
        jb        ..B6.40
..B6.86:
        orl       %eax, %ecx
        je        ..B6.40
..B6.39:
        movl      $1, %eax
        jmp       ..B6.41
..B6.40:
        xorl      %eax, %eax
..B6.41:
        movl      128(%esp), %edi
        movl      %ebx, %ebp
        subl      100(%esp), %ebp
        movl      %edx, %ecx
        movl      116(%esp), %ebx
        movl      8(%edi), %edx
        movl      %edx, 60(%esp)
        movl      12(%edi), %edx
        movl      120(%esp), %edi
        sbbl      96(%esp), %ecx
        addl      %eax, %edi
        movl      %edi, 56(%esp)
        adcl      $0, %ebx
        subl      %eax, %edi
        movl      $0, %eax
        movl      %ebx, 76(%esp)
        sbbl      %eax, %ebx
        jae       ..B6.43
..B6.42:
        movl      $1, 64(%esp)
        jmp       ..B6.44
..B6.43:
        movl      $0, 64(%esp)
..B6.44:
        movl      60(%esp), %eax
        movl      %eax, %ebx
        subl      56(%esp), %ebx
        movl      %edx, %edi
        sbbl      76(%esp), %edi
        subl      %ebx, %eax
        sbbl      %edi, %edx
        jae       ..B6.46
..B6.45:
        movl      $1, %edx
        jmp       ..B6.47
..B6.46:
        xorl      %edx, %edx
..B6.47:
        movl      64(%esp), %eax
        addl      %edx, %eax
        xorl      %edx, %edx
        orl       %edx, %eax
        je        ..B6.52
..B6.48:
        xorl      %eax, %eax
        movl      %ebp, %edx
        xorl      %ebp, %ebp
        subl      %edx, %ebp
        movl      %ecx, %edx
        movl      %eax, %ecx
        movl      %ebp, %eax
        sbbl      %edx, %ecx
        xorl      $-2147483648, 108(%esp)
        orl       %ecx, %eax
        jne       ..B6.50
..B6.49:
        movl      $0, 44(%esp)
        jmp       ..B6.51
..B6.50:
        movl      $-1, 44(%esp)
..B6.51:
        movl      44(%esp), %eax
        cltd      
        movl      %ebx, %eax
        movl      44(%esp), %ebx
        subl      %eax, %ebx
        movl      %edi, %eax
        movl      %edx, %edi
        movl      $-2147483648, 92(%esp)
        sbbl      %eax, %edi
..B6.52:
        movl      132(%esp), %edx
        movl      108(%esp), %eax
        movl      %eax, (%edx)
        movl      %esi, %edx
        andl      $16, %edx
        xorl      %eax, %eax
        orl       %eax, %edx
        jne       ..B6.56
..B6.53:
        movl      132(%esp), %edx
        movl      104(%esp), %eax
        movl      %eax, 4(%edx)
        movl      %ebp, 16(%edx)
        movl      %ecx, 20(%edx)
        movl      %ebx, 8(%edx)
        movl      %edi, 12(%edx)
..B6.54:
        movl      %esi, %edx
        xorl      %eax, %eax
        andl      $2, %edx
        orl       %eax, %edx
        jne       ..B6.55
        jmp       ..B6.61
..B6.56:
        movl      %edi, %edx
        movl      %ebx, 84(%esp)
        subl      %eax, %edx
        movl      %edi, 88(%esp)
        jl        ..B6.53
..B6.57:
        movl      $2, %edx
        movl      %ebp, 36(%esp)
        movl      %ecx, 40(%esp)
        movl      %eax, 32(%esp)
        movl      %ebx, 12(%esp)
        movl      %esi, 28(%esp)
        movl      %edi, 20(%esp)
        movl      %ecx, 24(%esp)
        movl      %ebp, 16(%esp)
        movl      36(%esp), %esi
        movl      40(%esp), %edi
        movl      %eax, %ebx
        movl      32(%esp), %ecx
        jmp       ..B6.58
..B6.60:
        xorl      %esi, %esi
        xorl      %edi, %edi
..B6.58:
        movl      84(%esp), %ebp
        orl       88(%esp), %ebp
        jne       ..B6.65
..B6.59:
        addl      $64, %ecx
        movl      %esi, 84(%esp)
        adcl      $0, %ebx
        addl      $-1, %edx
        movl      %edx, %ebp
        adcl      $-1, %eax
        movl      %edi, 88(%esp)
        orl       %eax, %ebp
        jne       ..B6.60
        jmp       ..B6.64
..B6.61:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B6.13
        jmp       ..B6.12
..B6.64:
        movl      132(%esp), %eax
        movl      12(%esp), %ebx
        movl      16(%esp), %ebp
        movl      20(%esp), %edi
        movl      24(%esp), %ecx
        movl      28(%esp), %esi
        movl      %ebp, 16(%eax)
        movl      %ecx, 20(%eax)
        movl      %ebx, 8(%eax)
        movl      %edi, 12(%eax)
        movl      $-262144, 4(%eax)
        jmp       ..B6.54
..B6.65:
        xorl      %eax, %eax
        movl      88(%esp), %edx
        movl      %esi, 36(%esp)
        subl      %eax, %edx
        movl      %edi, 40(%esp)
        movl      %ecx, 32(%esp)
        movl      28(%esp), %esi
        jl        ..B6.81
..B6.66:
        movl      88(%esp), %ecx
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %eax
        je        ..B6.68
..B6.67:
        movl      $21932, %edx
        shrl      %cl, %edx
        andl      $3, %edx
        incl      %edx
        jmp       ..B6.71
..B6.68:
        movl      84(%esp), %edx
        andl      $-256, %edx
        movl      88(%esp), %eax
        orl       %edx, %eax
        jne       ..B6.70
..B6.69:
        movl      84(%esp), %edx
..B6.70:
        movl      88(%esp), %eax
        movl      %edx, (%esp)
        movl      %eax, 4(%esp)
        fildll    (%esp)
        shrl      $31, %eax
        movl      8(%esp), %edx
        faddl     .L_2il0floatpacket.1@GOTOFF(%edx,%eax,8)
        fstpl     136(%esp)
        movl      140(%esp), %edx
        sarl      $20, %edx
        negl      %edx
        addl      $1086, %edx
..B6.71:
        cmpl      $31, %edx
        jbe       ..B6.73
..B6.72:
        movl      %edx, %ecx
        xorl      %eax, %eax
        movl      84(%esp), %ebx
        shll      %cl, %ebx
        movl      %ebx, 88(%esp)
        jmp       ..B6.74
..B6.73:
        movl      %edx, %ecx
        movl      84(%esp), %eax
        movl      88(%esp), %ebx
        shldl     %cl, %eax, %ebx
        movl      %ebx, 88(%esp)
        shll      %cl, %eax
..B6.74:
        movl      %edx, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B6.76
..B6.75:
        movl      %edi, %ebx
        xorl      %ebp, %ebp
        shrl      %cl, %ebx
        movl      %ebx, 84(%esp)
        jmp       ..B6.77
..B6.76:
        movl      36(%esp), %ebx
        movl      %edi, %ebp
        shrdl     %cl, %ebp, %ebx
        movl      %ebx, 84(%esp)
        shrl      %cl, %ebp
..B6.77:
        orl       %eax, 84(%esp)
        orl       %ebp, 88(%esp)
        movl      36(%esp), %ebx
        cmpl      $31, %edx
        jbe       ..B6.79
..B6.78:
        movl      %edx, %ecx
        shll      %cl, %ebx
        movl      $0, 36(%esp)
        movl      %ebx, 40(%esp)
        jmp       ..B6.80
..B6.79:
        movl      %edx, %ecx
        movl      %edi, %eax
        shldl     %cl, %ebx, %eax
        shll      %cl, %ebx
        movl      %eax, 40(%esp)
        movl      %ebx, 36(%esp)
..B6.80:
        addl      %edx, 32(%esp)
..B6.81:
        movl      132(%esp), %edi
        movl      104(%esp), %ebp
        movl      84(%esp), %eax
        movl      88(%esp), %edx
        movl      36(%esp), %ecx
        movl      40(%esp), %ebx
        subl      32(%esp), %ebp
        movl      %eax, 8(%edi)
        movl      %edx, 12(%edi)
        movl      %ecx, 16(%edi)
        movl      %ebx, 20(%edi)
        movl      %ebp, 4(%edi)
        jmp       ..B6.54
        .align    16,0x90
	.type	__dpml_addsub__..0,@function
	.size	__dpml_addsub__..0,.-__dpml_addsub__..0
	.data
# -- End  __dpml_addsub__..0
	.text
# -- Begin  __dpml_evaluate_packed_poly__
	.text
       .align    16,0x90
	.globl __dpml_evaluate_packed_poly__
__dpml_evaluate_packed_poly__:
# parameter 1: 160 + %esp
# parameter 2: 164 + %esp
# parameter 3: 172 + %esp
# parameter 4: 176 + %esp
# parameter 5: 184 + %esp
# parameter 6: 192 + %esp
..B7.1:
..L14:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $140, %esp
        movl      172(%esp), %esi
        movl      192(%esp), %ebp
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 124(%esp)
        movl      12(%esi), %ecx
        movl      (%esi), %ebx
        movl      %ebx, %edx
        movl      176(%esp), %edi
        notl      %edi
        movl      %ecx, 12(%ebp)
        andl      %edi, %edx
        movl      8(%esi), %eax
        movl      4(%esi), %ecx
        movl      %edi, 68(%esp)
        movl      %ecx, %edi
        movl      %edx, 16(%ebp)
        movl      %ebx, %edx
        shll      $31, %ecx
        andl      $1, %edx
        shrl      $1, %ebx
        movl      %eax, 8(%ebp)
        orl       %ebx, %ecx
        movl      180(%esp), %eax
        notl      %eax
        andl      176(%esp), %ecx
        andl      %eax, %edi
        movl      %eax, 64(%esp)
        xorl      %eax, %eax
        subl      184(%esp), %ecx
        movl      %edi, 20(%ebp)
        orl       %eax, %edx
        jne       ..B7.3
..B7.2:
        movl      %eax, %edx
        jmp       ..B7.4
..B7.3:
        movl      $-2147483648, %edx
..B7.4:
        movl      164(%esp), %edi
        addl      $-1, %edi
        movl      %ecx, 4(%ebp)
        movl      168(%esp), %ecx
        adcl      $-1, %ecx
        movl      %edx, (%ebp)
        movl      %ecx, %edx
        call      ..L15
..L15:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L15](%ebx), %ebx
        subl      %eax, %edx
        jl        ..B7.16
..B7.5:
        movl      %eax, 104(%esp)
        movl      %eax, 100(%esp)
        movl      %ecx, 80(%esp)
        movl      %ebx, 40(%esp)
        movl      %edi, 76(%esp)
        movl      %esi, 72(%esp)
..B7.6:
        addl      $12, %esp
        movl      180(%esp), %eax
        pushl     %eax
        pushl     %eax
        pushl     156(%esp)
        call      __dpml_multiply__
..B7.7:
        movl      192(%esp), %eax
        xorl      %ecx, %ecx
        movl      12(%eax), %ebp
        movl      %ebp, %ebx
        movl      4(%eax), %edx
        subl      %ecx, %ebx
        movl      8(%eax), %esi
        movl      %edx, 56(%esp)
        jl        ..B7.13
..B7.8:
        movl      192(%esp), %edx
        xorl      %eax, %eax
        movl      %eax, 60(%esp)
        movl      %ebp, 88(%esp)
        movl      16(%edx), %ebx
        movl      20(%edx), %edi
        movl      $2, %edx
        movl      %esi, 84(%esp)
        movl      %eax, %ebp
        movl      60(%esp), %ecx
        jmp       ..B7.9
..B7.11:
        xorl      %ebx, %ebx
        xorl      %edi, %edi
..B7.9:
        movl      84(%esp), %esi
        orl       88(%esp), %esi
        jne       ..B7.19
..B7.10:
        addl      $64, %ecx
        movl      %ebx, 84(%esp)
        adcl      $0, %ebp
        addl      $-1, %edx
        movl      %edx, %ebx
        adcl      $-1, %eax
        movl      %edi, 88(%esp)
        orl       %eax, %ebx
        jne       ..B7.11
..B7.12:
        movl      192(%esp), %eax
        movl      $-262144, 4(%eax)
..B7.13:
        movl      72(%esp), %esi
        addl      $16, %esi
        movl      68(%esp), %edi
        movl      64(%esp), %edx
        movl      %esi, 72(%esp)
        movl      8(%esi), %ebx
        movl      %ebx, 108(%esp)
        movl      (%esi), %eax
        andl      %eax, %edi
        movl      4(%esi), %ebx
        andl      %ebx, %edx
        shll      $31, %ebx
        movl      %eax, %ecx
        shrl      $1, %eax
        andl      $1, %ecx
        orl       %eax, %ebx
        movl      192(%esp), %eax
        andl      176(%esp), %ebx
        movl      12(%esi), %ebp
        movl      %edx, 120(%esp)
        lea       100(%esp), %edx
        movl      %ecx, 8(%esp)
        movl      %eax, %ecx
        movl      %ebp, 12(%edx)
        movl      %edi, 16(%edx)
        subl      184(%esp), %ebx
        movl      $0, 12(%esp)
        call      __dpml_addsub__.
..B7.14:
        movl      192(%esp), %eax
        xorl      %ecx, %ecx
        addl      $-1, 76(%esp)
        movl      80(%esp), %edx
        adcl      $-1, %edx
        addl      %ebx, 4(%eax)
        movl      %edx, 80(%esp)
        subl      %ecx, %edx
        jge       ..B7.6
..B7.15:
        movl      40(%esp), %ebx
..B7.16:
        movl      124(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B7.18
..B7.17:
        addl      $140, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B7.18:
        call      __stack_chk_fail@PLT
..B7.19:
        movl      88(%esp), %ebp
        movl      %ebp, %edx
        xorl      %eax, %eax
        movl      84(%esp), %esi
        subl      %eax, %edx
        movl      %ecx, 60(%esp)
        jl        ..B7.35
..B7.20:
        movl      %ebp, %ecx
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %eax
        je        ..B7.22
..B7.21:
        movl      $21932, %eax
        shrl      %cl, %eax
        andl      $3, %eax
        incl      %eax
        jmp       ..B7.25
..B7.22:
        movl      %esi, %edx
        movl      %ebp, %eax
        andl      $-256, %edx
        orl       %edx, %eax
        jne       ..B7.24
..B7.23:
        movl      %esi, %edx
..B7.24:
        movl      %edx, 32(%esp)
        movl      %ebp, %edx
        movl      %ebp, 36(%esp)
        fildll    32(%esp)
        shrl      $31, %edx
        movl      40(%esp), %eax
        faddl     .L_2il0floatpacket.1@GOTOFF(%eax,%edx,8)
        fstpl     92(%esp)
        movl      96(%esp), %eax
        sarl      $20, %eax
        negl      %eax
        addl      $1086, %eax
..B7.25:
        cmpl      $31, %eax
        jbe       ..B7.27
..B7.26:
        movl      %eax, %ecx
        movl      %esi, %ebp
        movl      $0, 48(%esp)
        shll      %cl, %ebp
        jmp       ..B7.28
..B7.27:
        movl      %eax, %ecx
        shldl     %cl, %esi, %ebp
        shll      %cl, %esi
        movl      %esi, 48(%esp)
..B7.28:
        movl      %eax, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B7.30
..B7.29:
        movl      %edi, %esi
        movl      $0, 44(%esp)
        shrl      %cl, %esi
        jmp       ..B7.31
..B7.30:
        movl      %edi, %edx
        movl      %ebx, %esi
        shrl      %cl, %edx
        shrdl     %cl, %edi, %esi
        movl      %edx, 44(%esp)
..B7.31:
        orl       48(%esp), %esi
        orl       44(%esp), %ebp
        movl      %ebx, 52(%esp)
        cmpl      $31, %eax
        jbe       ..B7.33
..B7.32:
        movl      %eax, %ecx
        xorl      %ebx, %ebx
        movl      52(%esp), %edi
        shll      %cl, %edi
        jmp       ..B7.34
..B7.33:
        movl      %eax, %ecx
        shldl     %cl, %ebx, %edi
        shll      %cl, %ebx
..B7.34:
        addl      %eax, 60(%esp)
..B7.35:
        movl      192(%esp), %eax
        movl      %ebx, 16(%eax)
        movl      56(%esp), %ebx
        subl      60(%esp), %ebx
        movl      %esi, 8(%eax)
        movl      %ebp, 12(%eax)
        movl      %edi, 20(%eax)
        movl      %ebx, 4(%eax)
        jmp       ..B7.13
        .align    16,0x90
	.type	__dpml_evaluate_packed_poly__,@function
	.size	__dpml_evaluate_packed_poly__,.-__dpml_evaluate_packed_poly__
	.data
# -- End  __dpml_evaluate_packed_poly__
	.text
# -- Begin  __dpml_addsub__
	.text
       .align    16,0x90
	.hidden __dpml_addsub__
	.globl __dpml_addsub__
__dpml_addsub__:
# parameter 1: %eax
# parameter 2: %edx
# parameter 3: 184 + %esp
# parameter 4: %ecx
..B8.1:
..L16:

        movl      4(%esp), %eax
        movl      8(%esp), %edx
        movl      20(%esp), %ecx
	.hidden __dpml_addsub__.
	.globl __dpml_addsub__.
__dpml_addsub__.:
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $156, %esp
        movl      %eax, %esi
        movl      %edx, %ebp
        movl      188(%esp), %edx
        movl      184(%esp), %edi
        movl      %edi, %ebx
        movl      %edx, 48(%esp)
        movl      %edi, %edx
        movl      %ecx, 132(%esp)
        andl      $4, %ebx
        movl      (%esi), %eax
        shll      $31, %edx
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 144(%esp)
        xorl      %ecx, %ecx
        movl      %eax, 108(%esp)
        xorl      %edx, %eax
        xorl      (%ebp), %eax
        orl       %ecx, %ebx
        je        ..B8.3
..B8.2:
        movl      $0, 108(%esp)
        jmp       ..B8.4
..B8.3:
        movl      %eax, %edx
..B8.4:
        movl      4(%esi), %ecx
        movl      %ecx, %eax
        shrl      $31, %edx
        movl      %edx, 124(%esp)
        cltd      
        movl      4(%ebp), %eax
        movl      %edx, %ebx
        cltd      
        movl      %ecx, %eax
        subl      4(%ebp), %eax
        movl      %ecx, 104(%esp)
        sbbl      %edx, %ebx
        xorl      %edx, %edx
        movl      %ebx, %ecx
        movl      $0, 112(%esp)
        subl      %edx, %ecx
        movl      124(%esp), %edx
        jl        ..B8.6
..B8.5:
        movl      $0, 92(%esp)
        jmp       ..B8.10
..B8.6:
        movl      %esi, %ecx
        movl      %ebp, %esi
        movl      %ecx, %ebp
        movl      %eax, %ecx
        xorl      %eax, %eax
        subl      %ecx, %eax
        movl      %ebx, %ecx
        movl      $0, %ebx
        sbbl      %ecx, %ebx
        movl      %edx, %ecx
        addl      %eax, 104(%esp)
        orl       112(%esp), %ecx
        jne       ..B8.8
..B8.7:
        xorl      %ecx, %ecx
        jmp       ..B8.9
..B8.8:
        movl      $-2147483648, %ecx
..B8.9:
        xorl      %ecx, 108(%esp)
        movl      $-2147483648, 92(%esp)
..B8.10:
        movl      16(%ebp), %ecx
        movl      %ecx, 100(%esp)
        movl      20(%ebp), %ecx
        movl      %ecx, 96(%esp)
        movl      8(%ebp), %ecx
        movl      12(%ebp), %ebp
        movl      %ecx, 120(%esp)
        movl      %ebp, 116(%esp)
        movl      $2, %ebp
        movl      $0, (%esp)
        call      ..L17
..L17:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L17](%ecx), %ecx
        movl      %esi, 128(%esp)
        movl      %ecx, 8(%esp)
        movl      %edi, 40(%esp)
        movl      %ebp, 4(%esp)
        movl      %edx, 124(%esp)
        movl      (%esp), %esi
        jmp       ..B8.11
..B8.13:
        xorl      %edx, %edx
        movl      %edx, 120(%esp)
        movl      %edx, 116(%esp)
..B8.11:
        movl      $64, %ebp
        xorl      %edi, %edi
        xorl      %edx, %edx
        subl      %eax, %ebp
        sbbl      %ebx, %edx
        movl      %edx, %ecx
        subl      %edi, %ecx
        jl        ..B8.12
..B8.90:
        orl       %ebp, %ecx
        jne       ..B8.19
..B8.12:
        movl      116(%esp), %ebx
        movl      120(%esp), %eax
        movl      %ebx, 96(%esp)
        xorl      %ebx, %ebx
        movl      %eax, 100(%esp)
        xorl      %eax, %eax
        subl      %ebp, %eax
        sbbl      %edx, %ebx
        movl      4(%esp), %edx
        addl      $-1, %edx
        movl      %edx, 4(%esp)
        adcl      $-1, %esi
        orl       %esi, %edx
        jne       ..B8.13
..B8.14:
        movl      132(%esp), %eax
        movl      128(%esp), %esi
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      4(%esi), %edx
        movl      %ecx, 8(%eax)
        movl      %ebx, 12(%eax)
        movl      %edx, 4(%eax)
        movl      16(%esi), %ebp
        movl      20(%esi), %edx
        movl      108(%esp), %ecx
        movl      40(%esp), %edi
        movl      %ebp, 16(%eax)
        andl      $2, %edi
        movl      %edx, 20(%eax)
        movl      %ecx, (%eax)
        xorl      %eax, %eax
        orl       %eax, %edi
        je        ..B8.16
..B8.15:
        movl      132(%esp), %eax
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      %ecx, 32(%eax)
        movl      %ebx, 36(%eax)
        movl      108(%esp), %edi
        movl      4(%esi), %edx
        movl      16(%esi), %ebp
        movl      20(%esi), %esi
        xorl      92(%esp), %edi
        movl      %edx, 28(%eax)
        movl      %ebp, 40(%eax)
        movl      %esi, 44(%eax)
        movl      %edi, 24(%eax)
..B8.16:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B8.18
..B8.17:
        addl      $156, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B8.18:
        movl      8(%esp), %ebx
        call      __stack_chk_fail@PLT
..B8.19:
        movl      %ebp, 4(%esp)
        orl       %eax, %ebx
        movl      40(%esp), %edi
        movl      124(%esp), %edx
        movl      128(%esp), %esi
        je        ..B8.30
..B8.20:
        cmpl      $31, %eax
        jbe       ..B8.22
..B8.21:
        movl      %eax, %ecx
        xorl      %ebx, %ebx
        movl      96(%esp), %ebp
        shrl      %cl, %ebp
        movl      %ebp, 100(%esp)
        jmp       ..B8.23
..B8.22:
        movl      %eax, %ecx
        movl      100(%esp), %ebp
        movl      96(%esp), %ebx
        shrdl     %cl, %ebx, %ebp
        movl      %ebp, 100(%esp)
        shrl      %cl, %ebx
..B8.23:
        cmpl      $31, 4(%esp)
        jbe       ..B8.25
..B8.24:
        movl      4(%esp), %ecx
        movl      120(%esp), %ebp
        shll      %cl, %ebp
        movl      $0, (%esp)
        movl      %ebp, 96(%esp)
        jmp       ..B8.26
..B8.25:
        movl      %edi, 40(%esp)
        movl      4(%esp), %ecx
        movl      116(%esp), %edi
        movl      120(%esp), %ebp
        shldl     %cl, %ebp, %edi
        movl      %edi, 96(%esp)
        shll      %cl, %ebp
        movl      %ebp, (%esp)
        movl      40(%esp), %edi
..B8.26:
        movl      (%esp), %ebp
        orl       %ebp, 100(%esp)
        orl       %ebx, 96(%esp)
        movl      116(%esp), %ebx
        cmpl      $31, %eax
        jbe       ..B8.28
..B8.27:
        movl      %eax, %ecx
        shrl      %cl, %ebx
        movl      $0, 116(%esp)
        movl      %ebx, 120(%esp)
..B8.30:
        movl      %edx, 124(%esp)
        movl      %esi, 128(%esp)
        jmp       ..B8.31
..B8.28:
        movl      %eax, %ecx
        movl      120(%esp), %ebp
        shrdl     %cl, %ebx, %ebp
        shrl      %cl, %ebx
        movl      %ebp, 120(%esp)
        movl      %ebx, 116(%esp)
        jmp       ..B8.30
..B8.60:
        movl      128(%esp), %esi
        movl      $1, %edx
        movl      124(%esp), %eax
        xorl      %ecx, %ecx
        subl      %eax, %edx
        movl      108(%esp), %ebp
        movl      4(%esi), %eax
        movl      112(%esp), %ebx
        sbbl      %ebx, %ecx
        xorl      92(%esp), %ebp
        xorl      $2, %edi
        addl      $24, 132(%esp)
        movl      %edx, 124(%esp)
        movl      %ecx, 112(%esp)
        movl      %ebp, 108(%esp)
        movl      %eax, 104(%esp)
..B8.31:
        movl      128(%esp), %edx
        movl      124(%esp), %eax
        orl       112(%esp), %eax
        movl      16(%edx), %ebx
        movl      20(%edx), %edx
        jne       ..B8.43
..B8.32:
        movl      %ebx, %ebp
        movl      %edx, %ecx
        addl      100(%esp), %ebp
        movl      %ebp, %eax
        adcl      96(%esp), %ecx
        andl      $15, %edi
        subl      %ebx, %eax
        movl      %ecx, %ebx
        movl      $0, 48(%esp)
        sbbl      %edx, %ebx
        jae       ..B8.34
..B8.33:
        movl      $1, 72(%esp)
        jmp       ..B8.35
..B8.34:
        movl      $0, 72(%esp)
..B8.35:
        movl      128(%esp), %esi
        movl      72(%esp), %edx
        movl      8(%esi), %ebx
        movl      %ebx, 52(%esp)
        movl      120(%esp), %ebx
        movl      12(%esi), %eax
        addl      %edx, %ebx
        movl      %eax, 80(%esp)
        movl      %ebx, %eax
        movl      116(%esp), %esi
        adcl      $0, %esi
        subl      %edx, %eax
        movl      $0, %edx
        movl      %esi, %eax
        sbbl      %edx, %eax
        jae       ..B8.37
..B8.36:
        movl      $1, 68(%esp)
        jmp       ..B8.38
..B8.37:
        movl      $0, 68(%esp)
..B8.38:
        movl      52(%esp), %eax
        addl      %eax, %ebx
        movl      %ebx, %edx
        adcl      80(%esp), %esi
        subl      %eax, %edx
        movl      %esi, %eax
        sbbl      80(%esp), %eax
        jae       ..B8.40
..B8.39:
        movl      $1, %edx
        jmp       ..B8.41
..B8.40:
        xorl      %edx, %edx
..B8.41:
        movl      68(%esp), %eax
        addl      %edx, %eax
        xorl      %edx, %edx
        orl       %edx, %eax
        je        ..B8.57
..B8.42:
        movl      %ecx, %eax
        movl      %ebx, %edx
        shll      $31, %eax
        shrl      $1, %ebp
        orl       %eax, %ebp
        movl      %esi, %eax
        shrl      $1, %ecx
        shll      $31, %edx
        shll      $31, %eax
        orl       %edx, %ecx
        shrl      $1, %esi
        shrl      $1, %ebx
        orl       $-2147483648, %esi
        incl      104(%esp)
        orl       %eax, %ebx
        jmp       ..B8.57
..B8.43:
        addl      $-8, %edi
        movl      100(%esp), %ecx
        adcl      $-1, 48(%esp)
        subl      %ebx, %ecx
        movl      96(%esp), %eax
        sbbl      %edx, %eax
        jb        ..B8.45
..B8.91:
        orl       %eax, %ecx
        je        ..B8.45
..B8.44:
        movl      $1, %eax
        jmp       ..B8.46
..B8.45:
        xorl      %eax, %eax
..B8.46:
        movl      128(%esp), %esi
        movl      %ebx, %ebp
        subl      100(%esp), %ebp
        movl      %edx, %ecx
        movl      116(%esp), %ebx
        movl      8(%esi), %edx
        movl      %edx, 60(%esp)
        movl      12(%esi), %edx
        movl      120(%esp), %esi
        sbbl      96(%esp), %ecx
        addl      %eax, %esi
        movl      %esi, 56(%esp)
        adcl      $0, %ebx
        subl      %eax, %esi
        movl      $0, %eax
        movl      %ebx, 76(%esp)
        sbbl      %eax, %ebx
        jae       ..B8.48
..B8.47:
        movl      $1, 64(%esp)
        jmp       ..B8.49
..B8.48:
        movl      $0, 64(%esp)
..B8.49:
        movl      60(%esp), %eax
        movl      %eax, %ebx
        subl      56(%esp), %ebx
        movl      %edx, %esi
        sbbl      76(%esp), %esi
        subl      %ebx, %eax
        sbbl      %esi, %edx
        jae       ..B8.51
..B8.50:
        movl      $1, %edx
        jmp       ..B8.52
..B8.51:
        xorl      %edx, %edx
..B8.52:
        movl      64(%esp), %eax
        addl      %edx, %eax
        xorl      %edx, %edx
        orl       %edx, %eax
        je        ..B8.57
..B8.53:
        xorl      %eax, %eax
        movl      %ebp, %edx
        xorl      %ebp, %ebp
        subl      %edx, %ebp
        movl      %ecx, %edx
        movl      %eax, %ecx
        movl      %ebp, %eax
        sbbl      %edx, %ecx
        xorl      $-2147483648, 108(%esp)
        orl       %ecx, %eax
        jne       ..B8.55
..B8.54:
        movl      $0, 44(%esp)
        jmp       ..B8.56
..B8.55:
        movl      $-1, 44(%esp)
..B8.56:
        movl      44(%esp), %eax
        cltd      
        movl      %ebx, %eax
        movl      44(%esp), %ebx
        subl      %eax, %ebx
        movl      %esi, %eax
        movl      %edx, %esi
        movl      $-2147483648, 92(%esp)
        sbbl      %eax, %esi
..B8.57:
        movl      132(%esp), %edx
        movl      108(%esp), %eax
        movl      %eax, (%edx)
        movl      %edi, %edx
        andl      $16, %edx
        xorl      %eax, %eax
        orl       %eax, %edx
        jne       ..B8.61
..B8.58:
        movl      132(%esp), %edx
        movl      104(%esp), %eax
        movl      %eax, 4(%edx)
        movl      %ebp, 16(%edx)
        movl      %ecx, 20(%edx)
        movl      %ebx, 8(%edx)
        movl      %esi, 12(%edx)
..B8.59:
        movl      %edi, %edx
        xorl      %eax, %eax
        andl      $2, %edx
        orl       %eax, %edx
        jne       ..B8.60
        jmp       ..B8.66
..B8.61:
        movl      %esi, %edx
        movl      %ebx, 84(%esp)
        subl      %eax, %edx
        movl      %esi, 88(%esp)
        jl        ..B8.58
..B8.62:
        movl      $2, %edx
        movl      %ebp, 32(%esp)
        movl      %ecx, 36(%esp)
        movl      %eax, 28(%esp)
        movl      %ebx, 12(%esp)
        movl      %edi, 40(%esp)
        movl      %esi, 20(%esp)
        movl      %ecx, 24(%esp)
        movl      %ebp, 16(%esp)
        movl      32(%esp), %esi
        movl      36(%esp), %edi
        movl      %eax, %ebx
        movl      28(%esp), %ecx
        jmp       ..B8.63
..B8.65:
        xorl      %esi, %esi
        xorl      %edi, %edi
..B8.63:
        movl      84(%esp), %ebp
        orl       88(%esp), %ebp
        jne       ..B8.70
..B8.64:
        addl      $64, %ecx
        movl      %esi, 84(%esp)
        adcl      $0, %ebx
        addl      $-1, %edx
        movl      %edx, %ebp
        adcl      $-1, %eax
        movl      %edi, 88(%esp)
        orl       %eax, %ebp
        jne       ..B8.65
        jmp       ..B8.69
..B8.66:
        movl      144(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B8.18
        jmp       ..B8.17
..B8.69:
        movl      132(%esp), %eax
        movl      12(%esp), %ebx
        movl      16(%esp), %ebp
        movl      20(%esp), %esi
        movl      24(%esp), %ecx
        movl      40(%esp), %edi
        movl      %ebp, 16(%eax)
        movl      %ecx, 20(%eax)
        movl      %ebx, 8(%eax)
        movl      %esi, 12(%eax)
        movl      $-262144, 4(%eax)
        jmp       ..B8.59
..B8.70:
        xorl      %eax, %eax
        movl      88(%esp), %edx
        movl      %edi, 36(%esp)
        subl      %eax, %edx
        movl      %esi, 32(%esp)
        movl      %ecx, 28(%esp)
        movl      40(%esp), %edi
        jl        ..B8.86
..B8.71:
        movl      88(%esp), %ecx
        shrl      $26, %ecx
        andl      $30, %ecx
        orl       %ecx, %eax
        je        ..B8.73
..B8.72:
        movl      $21932, %edx
        shrl      %cl, %edx
        andl      $3, %edx
        incl      %edx
        jmp       ..B8.76
..B8.73:
        movl      84(%esp), %edx
        andl      $-256, %edx
        movl      88(%esp), %eax
        orl       %edx, %eax
        jne       ..B8.75
..B8.74:
        movl      84(%esp), %edx
..B8.75:
        movl      88(%esp), %eax
        movl      %edx, (%esp)
        movl      %eax, 4(%esp)
        fildll    (%esp)
        shrl      $31, %eax
        movl      8(%esp), %edx
        faddl     .L_2il0floatpacket.1@GOTOFF(%edx,%eax,8)
        fstpl     136(%esp)
        movl      140(%esp), %edx
        sarl      $20, %edx
        negl      %edx
        addl      $1086, %edx
..B8.76:
        cmpl      $31, %edx
        jbe       ..B8.78
..B8.77:
        movl      %edx, %ecx
        xorl      %eax, %eax
        movl      84(%esp), %ebx
        shll      %cl, %ebx
        movl      %ebx, 88(%esp)
        jmp       ..B8.79
..B8.78:
        movl      %edx, %ecx
        movl      84(%esp), %eax
        movl      88(%esp), %ebx
        shldl     %cl, %eax, %ebx
        movl      %ebx, 88(%esp)
        shll      %cl, %eax
..B8.79:
        movl      %edx, %ecx
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B8.81
..B8.80:
        movl      36(%esp), %ebx
        xorl      %ebp, %ebp
        shrl      %cl, %ebx
        movl      %ebx, 84(%esp)
        jmp       ..B8.82
..B8.81:
        movl      %esi, %ebx
        movl      36(%esp), %ebp
        shrdl     %cl, %ebp, %ebx
        movl      %ebx, 84(%esp)
        shrl      %cl, %ebp
..B8.82:
        orl       %eax, 84(%esp)
        orl       %ebp, 88(%esp)
        movl      %esi, %ebx
        cmpl      $31, %edx
        jbe       ..B8.84
..B8.83:
        movl      %edx, %ecx
        shll      %cl, %ebx
        movl      $0, 32(%esp)
        movl      %ebx, 36(%esp)
        jmp       ..B8.85
..B8.84:
        movl      %edx, %ecx
        movl      36(%esp), %eax
        shldl     %cl, %ebx, %eax
        shll      %cl, %ebx
        movl      %eax, 36(%esp)
        movl      %ebx, 32(%esp)
..B8.85:
        addl      %edx, 28(%esp)
..B8.86:
        movl      132(%esp), %esi
        movl      104(%esp), %ebp
        movl      84(%esp), %eax
        movl      88(%esp), %edx
        movl      32(%esp), %ecx
        movl      36(%esp), %ebx
        subl      28(%esp), %ebp
        movl      %eax, 8(%esi)
        movl      %edx, 12(%esi)
        movl      %ecx, 16(%esi)
        movl      %ebx, 20(%esi)
        movl      %ebp, 4(%esi)
        jmp       ..B8.59
        .align    16,0x90
	.type	__dpml_addsub__,@function
	.size	__dpml_addsub__,.-__dpml_addsub__
	.data
# -- End  __dpml_addsub__
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.long	0x00000000,0x00000000,0x00000000,0x43f00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xe6,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.align 4
	.globl __x_constants__
__x_constants__:
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	0
	.long	1073610752
	.long	0
	.long	0
	.long	0
	.long	1073676288
	.long	0
	.long	0
	.long	0
	.long	1073741824
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073648159
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073779231
	.long	2479964490
	.long	592389929
	.long	3354604061
	.long	1073753495
	.long	3306619320
	.long	2221509004
	.long	3041149649
	.long	1073713695
	.long	0
	.long	0
	.long	0
	.long	1074030592
	.long	0
	.long	0
	.long	0
	.long	1074096128
	.long	0
	.long	0
	.long	0
	.long	1074138624
	.long	0
	.long	0
	.long	0
	.long	1074161664
	.long	0
	.long	0
	.long	0
	.long	2147418112
	.type	__x_constants__,@object
	.size	__x_constants__,208
	.data
	.hidden __dpml_multiply__
	.hidden __dpml_exception
	.section .note.GNU-stack, ""
# End
