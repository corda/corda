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
	.file "dpml_ux_ops_64.c"
	.text
..TXTST0:
# -- Begin  __dpml_multiply__
	.text
       .align    16,0x90
	.hidden __dpml_multiply__
	.globl __dpml_multiply__
__dpml_multiply__:
# parameter 1: 64 + %esp
# parameter 2: 68 + %esp
# parameter 3: 72 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $44, %esp
        movl      68(%esp), %edi
        movl      64(%esp), %esi
        movl      12(%edi), %ebx
        movl      %ebx, %edx
        movl      12(%esi), %ebp
        movl      8(%esi), %eax
        movl      8(%edi), %ecx
        movl      %ebp, (%esp)
        imull     %ecx, %ebp
        imull     %eax, %edx
        movl      %eax, 4(%esp)
        addl      %edx, %ebp
        mull      %ecx
        addl      %ebp, %edx
        movl      %eax, %esi
        movl      %edx, 24(%esp)
        movl      %ecx, %eax
        movl      64(%esp), %edx
        movl      68(%esp), %ebp
        movl      64(%esp), %edi
        mull      16(%edx)
        movl      16(%ebp), %ebp
        movl      %ecx, %eax
        movl      %ebp, 20(%esp)
        movl      68(%esp), %ebp
        movl      20(%edi), %edi
        movl      %ecx, 8(%esp)
        movl      20(%ebp), %ebp
        movl      %ebp, 16(%esp)
        movl      %edx, %ebp
        mull      %edi
        movl      64(%esp), %ecx
        addl      %eax, %ebp
        movl      %edi, 36(%esp)
        movl      %edx, %edi
        adcl      $0, %edi
        movl      16(%ecx), %eax
        mull      %ebx
        addl      %ebp, %eax
        movl      %ebx, 12(%esp)
        adcl      %edi, %edx
        subl      %ebp, %eax
        movl      %edx, 28(%esp)
        movl      24(%esp), %ebx
        sbbl      %edi, %edx
        jae       ..B1.3
..B1.2:
        movl      $1, %ebp
        jmp       ..B1.4
..B1.3:
        xorl      %ebp, %ebp
..B1.4:
        movl      36(%esp), %eax
        mull      12(%esp)
        addl      %edx, %ebp
        addl      28(%esp), %eax
        movl      %eax, 32(%esp)
        movl      64(%esp), %eax
        adcl      $0, %ebp
        movl      68(%esp), %edi
        movl      72(%esp), %ecx
        movl      (%eax), %edx
        xorl      (%edi), %edx
        movl      %edx, (%ecx)
        movl      4(%eax), %edx
        addl      4(%edi), %edx
        movl      4(%esp), %eax
        movl      20(%esp), %edi
        movl      %edx, 4(%ecx)
        mull      %edi
        movl      %edi, %eax
        movl      %edx, %ecx
        mull      (%esp)
        addl      %eax, %ecx
        movl      %edx, %edi
        movl      4(%esp), %eax
        adcl      $0, %edi
        mull      16(%esp)
        addl      %ecx, %eax
        adcl      %edi, %edx
        subl      %ecx, %eax
        movl      %edx, 24(%esp)
        sbbl      %edi, %edx
        jae       ..B1.6
..B1.5:
        movl      $1, %ecx
        jmp       ..B1.7
..B1.6:
        xorl      %ecx, %ecx
..B1.7:
        movl      16(%esp), %eax
        mull      (%esp)
        addl      %edx, %ecx
        addl      24(%esp), %eax
        movl      32(%esp), %edi
        adcl      $0, %ecx
        addl      %edi, %esi
        movl      %eax, 20(%esp)
        movl      %esi, %eax
        adcl      %ebp, %ebx
        subl      %edi, %eax
        movl      %ebx, %edx
        sbbl      %ebp, %edx
        jae       ..B1.9
..B1.8:
        movl      $1, 24(%esp)
        jmp       ..B1.10
..B1.9:
        movl      $0, 24(%esp)
..B1.10:
        movl      8(%esp), %edi
        movl      %edi, %eax
        mull      4(%esp)
        movl      %edi, %eax
        movl      %edx, %ebp
        mull      (%esp)
        addl      %eax, %ebp
        movl      %edx, %edi
        movl      4(%esp), %eax
        adcl      $0, %edi
        mull      12(%esp)
        addl      %ebp, %eax
        adcl      %edi, %edx
        subl      %ebp, %eax
        movl      %edx, 16(%esp)
        sbbl      %edi, %edx
        jae       ..B1.12
..B1.11:
        movl      $1, %ebp
        jmp       ..B1.13
..B1.12:
        xorl      %ebp, %ebp
..B1.13:
        movl      (%esp), %eax
        mull      12(%esp)
        addl      %edx, %ebp
        addl      16(%esp), %eax
        movl      20(%esp), %edi
        adcl      $0, %ebp
        addl      %edi, %esi
        movl      %esi, %edx
        adcl      %ecx, %ebx
        subl      %edi, %edx
        movl      %ebx, %edi
        sbbl      %ecx, %edi
        jae       ..B1.15
..B1.14:
        movl      $1, %edx
        jmp       ..B1.16
..B1.15:
        xorl      %edx, %edx
..B1.16:
        movl      24(%esp), %ecx
        addl      %edx, %ecx
        movl      72(%esp), %edx
        addl      %ecx, %eax
        adcl      $0, %ebp
        movl      %esi, 16(%edx)
        movl      %ebx, 20(%edx)
        movl      %eax, 8(%edx)
        movl      %ebp, 12(%edx)
        addl      $44, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_multiply__,@function
	.size	__dpml_multiply__,.-__dpml_multiply__
	.data
# -- End  __dpml_multiply__
	.text
# -- Begin  __dpml_extended_multiply__
	.text
       .align    16,0x90
	.hidden __dpml_extended_multiply__
	.globl __dpml_extended_multiply__
__dpml_extended_multiply__:
# parameter 1: 80 + %esp
# parameter 2: 84 + %esp
# parameter 3: 88 + %esp
# parameter 4: 92 + %esp
..B2.1:
..L2:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $60, %esp
        movl      80(%esp), %esi
        movl      84(%esp), %edi
        movl      20(%esi), %ebx
        movl      20(%edi), %edx
        movl      16(%edi), %ecx
        movl      16(%esi), %ebp
        movl      %ebp, %eax
        movl      %ebx, 24(%esp)
        movl      %edx, 16(%esp)
        imull     %ecx, %ebx
        imull     %ebp, %edx
        addl      %edx, %ebx
        mull      %ecx
        movl      %eax, 36(%esp)
        movl      %ecx, %eax
        addl      %ebx, %edx
        movl      %edx, 40(%esp)
        mull      %ebp
        movl      8(%esi), %ebx
        movl      %ecx, %eax
        movl      12(%esi), %esi
        movl      %esi, (%esp)
        movl      8(%edi), %esi
        movl      %esi, 12(%esp)
        movl      12(%edi), %esi
        movl      %edx, %edi
        mull      24(%esp)
        addl      %eax, %edi
        movl      %ebp, %eax
        movl      %esi, 4(%esp)
        movl      %edx, %esi
        adcl      $0, %esi
        mull      16(%esp)
        addl      %edi, %eax
        movl      %ebx, 8(%esp)
        movl      %edx, %ebx
        adcl      %esi, %ebx
        subl      %edi, %eax
        movl      %ecx, 20(%esp)
        movl      %ebx, %ecx
        sbbl      %esi, %ecx
        jae       ..B2.3
..B2.2:
        movl      $1, %ecx
        jmp       ..B2.4
..B2.3:
        xorl      %ecx, %ecx
..B2.4:
        movl      24(%esp), %eax
        movl      16(%esp), %edi
        mull      %edi
        addl      %edx, %ecx
        addl      %ebx, %eax
        movl      92(%esp), %esi
        movl      %edi, %ebx
        adcl      $0, %ecx
        movl      %ebp, 44(%esp)
        movl      %ecx, 52(%esp)
        movl      36(%esp), %ebp
        movl      40(%esp), %ecx
        movl      %ebp, 16(%esi)
        movl      %ecx, 20(%esi)
        movl      80(%esp), %ebp
        movl      84(%esp), %esi
        movl      %eax, 48(%esp)
        movl      4(%ebp), %edx
        movl      4(%esi), %eax
        movl      (%ebp), %ecx
        xorl      (%esi), %ecx
        lea       -128(%edx,%eax), %esi
        movl      %esi, 56(%esp)
        movl      (%esp), %esi
        lea       (%edx,%eax), %ebp
        movl      8(%esp), %edx
        movl      20(%esp), %eax
        imull     %edx, %ebx
        imull     %eax, %esi
        mull      %edx
        addl      %esi, %ebx
        movl      %edx, %edi
        addl      %ebx, %edi
        movl      %eax, %esi
        movl      88(%esp), %ebx
        movl      12(%esp), %edx
        movl      4(%esp), %eax
        movl      %ecx, (%ebx)
        movl      %ebp, 4(%ebx)
        movl      24(%esp), %ebx
        movl      44(%esp), %ebp
        imull     %edx, %ebx
        imull     %ebp, %eax
        addl      %eax, %ebx
        movl      %ebp, %eax
        mull      %edx
        addl      %ebx, %edx
        movl      92(%esp), %ebx
        movl      %edx, 32(%esp)
        movl      %eax, 28(%esp)
        movl      %ecx, (%ebx)
        movl      56(%esp), %ecx
        movl      %ecx, 4(%ebx)
        movl      48(%esp), %ebx
        addl      %esi, %ebx
        movl      %ebx, %edx
        movl      52(%esp), %ecx
        adcl      %edi, %ecx
        subl      %esi, %edx
        movl      %ecx, %esi
        sbbl      %edi, %esi
        jae       ..B2.6
..B2.5:
        movl      $1, %esi
        jmp       ..B2.7
..B2.6:
        xorl      %esi, %esi
..B2.7:
        movl      %esi, 44(%esp)
        movl      (%esp), %edi
        movl      12(%esp), %esi
        movl      4(%esp), %edx
        movl      8(%esp), %eax
        imull     %esi, %edi
        imull     %eax, %edx
        addl      %edx, %edi
        mull      %esi
        movl      28(%esp), %esi
        addl      %edi, %edx
        addl      %esi, %ebx
        movl      %edx, 40(%esp)
        movl      %ebx, %edx
        movl      32(%esp), %edi
        adcl      %edi, %ecx
        subl      %esi, %edx
        movl      %ecx, %edx
        movl      44(%esp), %esi
        sbbl      %edi, %edx
        movl      %eax, 36(%esp)
        jae       ..B2.9
..B2.8:
        movl      $1, %edx
        jmp       ..B2.10
..B2.9:
        xorl      %edx, %edx
..B2.10:
        addl      %edx, %esi
        movl      %ebp, %eax
        movl      92(%esp), %edx
        movl      %ecx, 12(%edx)
        movl      12(%esp), %ecx
        movl      %ebx, 8(%edx)
        mull      %ecx
        movl      %ecx, %eax
        movl      %edx, %edi
        mull      24(%esp)
        addl      %eax, %edi
        movl      %ebp, %eax
        movl      %edx, %ebx
        adcl      $0, %ebx
        mull      4(%esp)
        addl      %edi, %eax
        movl      %edx, %ecx
        adcl      %ebx, %ecx
        subl      %edi, %eax
        movl      %ecx, %ebp
        sbbl      %ebx, %ebp
        jae       ..B2.12
..B2.11:
        movl      $1, %edi
        jmp       ..B2.13
..B2.12:
        xorl      %edi, %edi
..B2.13:
        movl      24(%esp), %eax
        mull      4(%esp)
        addl      %edx, %edi
        addl      %ecx, %eax
        movl      36(%esp), %ebx
        adcl      $0, %edi
        addl      %ebx, %esi
        movl      %eax, 28(%esp)
        movl      %esi, %ebp
        movl      40(%esp), %eax
        movl      %eax, %ecx
        adcl      $0, %ecx
        subl      %ebx, %ebp
        movl      %ecx, %edx
        sbbl      %eax, %edx
        jae       ..B2.15
..B2.14:
        movl      $1, %ebp
        jmp       ..B2.16
..B2.15:
        xorl      %ebp, %ebp
..B2.16:
        movl      20(%esp), %ebx
        movl      %ebx, %eax
        mull      8(%esp)
        movl      %ebx, %eax
        movl      %ecx, 32(%esp)
        movl      %edx, %ecx
        mull      (%esp)
        addl      %eax, %ecx
        movl      %edx, %ebx
        movl      8(%esp), %eax
        adcl      $0, %ebx
        mull      16(%esp)
        addl      %ecx, %eax
        adcl      %ebx, %edx
        subl      %ecx, %eax
        movl      %edx, 24(%esp)
        movl      32(%esp), %ecx
        sbbl      %ebx, %edx
        jae       ..B2.18
..B2.17:
        movl      $1, %ebx
        jmp       ..B2.19
..B2.18:
        xorl      %ebx, %ebx
..B2.19:
        movl      16(%esp), %eax
        mull      (%esp)
        addl      %edx, %ebx
        addl      24(%esp), %eax
        movl      %eax, 20(%esp)
        movl      28(%esp), %eax
        adcl      $0, %ebx
        addl      %eax, %esi
        movl      %esi, %edx
        adcl      %edi, %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      %edi, %eax
        jae       ..B2.21
..B2.20:
        movl      $1, %eax
        jmp       ..B2.22
..B2.21:
        xorl      %eax, %eax
..B2.22:
        movl      12(%esp), %edi
        addl      %eax, %ebp
        movl      %edi, %eax
        mull      8(%esp)
        movl      %edi, %eax
        movl      %ebp, 24(%esp)
        movl      %edx, %ebp
        mull      (%esp)
        addl      %eax, %ebp
        movl      %edx, %edi
        movl      8(%esp), %eax
        adcl      $0, %edi
        mull      4(%esp)
        addl      %ebp, %eax
        adcl      %edi, %edx
        subl      %ebp, %eax
        movl      %edx, 16(%esp)
        movl      24(%esp), %ebp
        sbbl      %edi, %edx
        jae       ..B2.24
..B2.23:
        movl      $1, %edi
        jmp       ..B2.25
..B2.24:
        xorl      %edi, %edi
..B2.25:
        movl      (%esp), %eax
        mull      4(%esp)
        addl      %edx, %edi
        addl      16(%esp), %eax
        movl      %eax, 8(%esp)
        movl      20(%esp), %eax
        adcl      $0, %edi
        addl      %eax, %esi
        movl      %esi, %edx
        adcl      %ebx, %ecx
        subl      %eax, %edx
        movl      %ecx, %eax
        sbbl      %ebx, %eax
        jae       ..B2.27
..B2.26:
        movl      $1, %eax
        jmp       ..B2.28
..B2.27:
        xorl      %eax, %eax
..B2.28:
        addl      %eax, %ebp
        movl      88(%esp), %eax
        movl      %ecx, 20(%eax)
        movl      8(%esp), %ecx
        addl      %ebp, %ecx
        movl      %esi, 16(%eax)
        adcl      $0, %edi
        movl      %ecx, 8(%eax)
        movl      %edi, 12(%eax)
        addl      $60, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
        .align    16,0x90
	.type	__dpml_extended_multiply__,@function
	.size	__dpml_extended_multiply__,.-__dpml_extended_multiply__
	.data
# -- End  __dpml_extended_multiply__
	.text
# -- Begin  __dpml_divide__
	.text
       .align    16,0x90
	.hidden __dpml_divide__
	.globl __dpml_divide__
__dpml_divide__:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
# parameter 4: 24 + %ebp
..B3.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $148, %esp
        call      ..L4
..L4:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ecx), %ecx
        movl      12(%ebp), %edi
        testl     %edi, %edi
        movl      8(%ebp), %ebx
        movl      %ecx, 92(%esp)
        lea       __ux_one__@GOTOFF(%ecx), %edx
        jne       ..L5
        movl      %edx, %edi
..L5:
        testl     %ebx, %ebx
        movl      %gs:20, %eax
        jne       ..L6
        movl      %edx, %ebx
..L6:
        xorl      %esp, %eax
        movl      %eax, 140(%esp)
        cmpl      %edx, %edi
        movl      16(%edi), %esi
        movl      8(%edi), %eax
        movl      12(%edi), %ecx
        movl      %esi, 104(%esp)
        movl      %ebx, 108(%esp)
        movl      %eax, 96(%esp)
        movl      %ecx, 100(%esp)
        movl      20(%edi), %esi
        je        ..B3.69
..B3.2:
        xorl      %edx, %edx
        subl      %edx, %ecx
        jl        ..B3.5
..B3.3:
        addl      $12, %esp
        pushl     %edx
        pushl     %edx
        pushl     %edi
        call      __dpml_ffs_and_shift__
..B3.4:
        movl      8(%edi), %eax
        movl      12(%edi), %edx
        movl      16(%edi), %ecx
        movl      20(%edi), %esi
        movl      %eax, 96(%esp)
        movl      %edx, 100(%esp)
        movl      %ecx, 104(%esp)
..B3.5:
        addl      $4, %esp
        lea       108(%esp), %edx
        pushl     %edx
        movl      -20(%edx), %ebx
        call      fegetenv@PLT
..B3.6:
        movl      %esi, 56(%esp)
        movl      100(%esp), %esi
        movl      %esi, %eax
        movl      96(%esp), %ecx
        movl      %ecx, %edx
        movl      %edi, 28(%esp)
        movl      %esi, %edi
        shll      $31, %eax
        shrl      $1, %edx
        shrl      $1, %edi
        orl       %edx, %eax
        movl      %eax, 32(%esp)
        movl      %esi, %eax
        movl      %edi, 36(%esp)
        andl      $-64, %eax
        fildll    32(%esp)
        andl      $63, %esi
        fstpl     48(%esp)
        xorl      %edx, %edx
        fldl      48(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fdiv      %st, %st(1)
        shrl      $1, %eax
        movl      %eax, 36(%esp)
        movl      %ecx, %eax
        movl      108(%esp), %edi
        shrl      $17, %eax
        shll      $15, %esi
        movl      %edx, 32(%esp)
        orl       %esi, %eax
        movl      56(%esp), %esi
        shll      $15, %ecx
        shrl      $17, %esi
        orl       %esi, %ecx
        movl      12(%edi), %esi
        movl      %esi, 80(%esp)
        fxch      %st(1)
        fstpl     48(%esp)
        fldl      48(%esp)
        movl      (%edi), %esi
        fld       %st(0)
        fildll    32(%esp)
        movl      %eax, 36(%esp)
        movl      %ecx, 32(%esp)
        movl      8(%edi), %eax
        movl      16(%edi), %ecx
        movl      %eax, 84(%esp)
        movl      %ecx, 72(%esp)
        movl      20(%edi), %eax
        movl      28(%esp), %ecx
        movl      %eax, 76(%esp)
        movl      24(%ebp), %eax
        xorl      (%ecx), %esi
        fstpl     48(%esp)
        fldl      48(%esp)
        fildll    32(%esp)
        movl      %esi, (%eax)
        fstpl     48(%esp)
        fxch      %st(1)
        fnstcw    16(%esp)
        movzwl    16(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 24(%esp)
        fldcw     24(%esp)
        fistpll   40(%esp)
        fldcw     16(%esp)
        movl      %edx, 32(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fldl      48(%esp)
        movl      40(%esp), %eax
        addl      $-1280, %eax
        movl      44(%esp), %esi
        adcl      $-1, %esi
        fmul      %st(1), %st
        andl      $-16, %esi
        movl      %esi, 36(%esp)
        fstpl     48(%esp)
        fldl      48(%esp)
        fildll    32(%esp)
        fstpl     48(%esp)
        fldl      48(%esp)
        fmul      %st, %st(3)
        movl      4(%edi), %edi
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fmul      %st(2), %st
        movl      %esi, 88(%esp)
        fsubrp    %st, %st(4)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        movl      80(%esp), %eax
        movl      %eax, %ebx
        movl      84(%esp), %esi
        subl      4(%ecx), %edi
        movl      %esi, %ecx
        shll      $21, %ebx
        shrl      $11, %ecx
        fmulp     %st, %st(3)
        orl       %ecx, %ebx
        movl      %edi, 60(%esp)
        movl      %eax, %edi
        shrl      $1, %edi
        movl      %ebx, %ecx
        shrl      $22, %ecx
        andl      $-1024, %edi
        shll      $10, %ebx
        orl       %edi, %ecx
        movl      %ebx, 32(%esp)
        movl      %eax, %ebx
        movl      %ecx, 36(%esp)
        andl      $-64, %ebx
        fxch      %st(2)
        fmulp     %st, %st(3)
        andl      $63, %eax
        shrl      $1, %ebx
        movl      76(%esp), %ecx
        shll      $15, %eax
        shrl      $17, %ecx
        fxch      %st(2)
        fstpl     48(%esp)
        fldl      48(%esp)
        fildll    32(%esp)
        movl      %edx, 32(%esp)
        movl      %esi, %edx
        movl      %ebx, 36(%esp)
        shrl      $17, %edx
        shll      $15, %esi
        orl       %eax, %edx
        orl       %ecx, %esi
        fstpl     48(%esp)
        fldl      48(%esp)
        movl      92(%esp), %eax
        fmul      %st(1), %st
        fildll    32(%esp)
        movl      %esi, 32(%esp)
        movl      %edx, 36(%esp)
        fstpl     48(%esp)
        fldl      48(%esp)
        fildll    32(%esp)
        fstpl     48(%esp)
        fldl      48(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpl     48(%esp)
        fldl      48(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpl     48(%esp)
        fldl      48(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpl     48(%esp)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      48(%esp)
        fxch      %st(2)
        fstpl     48(%esp)
        fldl      48(%esp)
        fldt      .L_2il0floatpacket.4@GOTOFF(%eax)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fnstcw    16(%esp)
        movzwl    16(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 24(%esp)
        fldcw     24(%esp)
        fistpll   40(%esp)
        fldcw     16(%esp)
        movl      40(%esp), %ebx
        movl      %ebx, %edx
        movl      44(%esp), %ecx
        movl      %ecx, %edi
        fxch      %st(1)
        fnstcw    16(%esp)
        movzwl    16(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 24(%esp)
        fldcw     24(%esp)
        fistpll   40(%esp)
        fldcw     16(%esp)
        shrl      $30, %edx
        shll      $2, %ebx
        shll      $2, %ecx
        movl      40(%esp), %esi
        orl       %ecx, %edx
        addl      %esi, %ebx
        movl      %ebx, %ecx
        movl      44(%esp), %eax
        adcl      %eax, %edx
        shrl      $30, %edi
        subl      %esi, %ecx
        movl      %edx, 64(%esp)
        movl      %edi, 68(%esp)
        sbbl      %eax, %edx
        movl      56(%esp), %esi
        movl      88(%esp), %edx
        jae       ..B3.8
..B3.7:
        movl      $1, %ecx
        jmp       ..B3.9
..B3.8:
        xorl      %ecx, %ecx
..B3.9:
        addl      %ecx, 68(%esp)
        movl      $1, %ecx
        movl      16(%ebp), %edi
        subl      %ecx, %edi
        orl       20(%ebp), %edi
        jne       ..B3.11
..B3.10:
        fstp      %st(0)
        xorl      %eax, %eax
        xorl      %edx, %edx
        jmp       ..B3.59
..B3.11:
        movl      92(%esp), %ecx
        fldt      .L_2il0floatpacket.5@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fnstcw    16(%esp)
        movzwl    16(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 24(%esp)
        fldcw     24(%esp)
        fistpll   40(%esp)
        fldcw     16(%esp)
        movl      44(%esp), %edi
        movl      40(%esp), %ecx
        movl      %ecx, 28(%esp)
        lea       (%edi,%edx,4), %edx
        movl      %edx, 32(%esp)
        orl       %edx, %ecx
        jne       ..B3.13
..B3.12:
        movl      $-1, %edx
        movl      %edx, 28(%esp)
        movl      %edx, 32(%esp)
..B3.13:
        movl      %ebx, %eax
        xorl      %edi, %edi
        mull      104(%esp)
        xorl      %ecx, %ecx
        movl      %ebx, %eax
        subl      68(%esp), %ecx
        movl      %ecx, 16(%esp)
        movl      %edx, %ecx
        sbbl      $0, %edi
        mull      %esi
        addl      %eax, %ecx
        movl      104(%esp), %eax
        movl      %edi, 52(%esp)
        movl      %edx, %edi
        adcl      $0, %edi
        mull      64(%esp)
        addl      %ecx, %eax
        adcl      %edi, %edx
        subl      %ecx, %eax
        movl      %edx, 20(%esp)
        movl      52(%esp), %ecx
        sbbl      %edi, %edx
        jae       ..B3.15
..B3.14:
        movl      $1, 48(%esp)
        jmp       ..B3.16
..B3.15:
        movl      $0, 48(%esp)
..B3.16:
        movl      %esi, %eax
        movl      %esi, 56(%esp)
        movl      64(%esp), %esi
        mull      %esi
        movl      %ecx, 52(%esp)
        movl      48(%esp), %ecx
        addl      %edx, %ecx
        movl      %esi, %edx
        addl      20(%esp), %eax
        movl      96(%esp), %esi
        adcl      $0, %ecx
        movl      %ecx, 48(%esp)
        movl      100(%esp), %ecx
        movl      %ecx, %edi
        imull     %ebx, %edi
        imull     %esi, %edx
        movl      %eax, 44(%esp)
        movl      %esi, %eax
        addl      %edx, %edi
        mull      %ebx
        movl      %eax, 24(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 36(%esp)
        mull      %esi
        movl      %ebx, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %esi, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      64(%esp)
        addl      %edi, %eax
        movl      56(%esp), %esi
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 40(%esp)
        sbbl      %ecx, %edx
        movl      52(%esp), %ecx
        jae       ..B3.18
..B3.17:
        movl      $1, %edi
        jmp       ..B3.19
..B3.18:
        xorl      %edi, %edi
..B3.19:
        movl      %ebx, 20(%esp)
        andl      %ecx, %esi
        movl      100(%esp), %ebx
        movl      %ebx, %eax
        mull      64(%esp)
        addl      %edx, %edi
        andl      %ebx, %ecx
        addl      40(%esp), %eax
        movl      %eax, 56(%esp)
        movl      104(%esp), %edx
        adcl      $0, %edi
        movl      16(%esp), %eax
        andl      %eax, %edx
        movl      44(%esp), %ebx
        andl      96(%esp), %eax
        addl      %ebx, %edx
        movl      %eax, 16(%esp)
        movl      48(%esp), %eax
        adcl      %eax, %esi
        movl      %edx, 104(%esp)
        subl      %ebx, %edx
        movl      %esi, %ebx
        sbbl      %eax, %ebx
        movl      20(%esp), %ebx
        jae       ..B3.21
..B3.20:
        movl      $1, %edx
        jmp       ..B3.22
..B3.21:
        xorl      %edx, %edx
..B3.22:
        movl      %ecx, 52(%esp)
        movl      24(%esp), %ecx
        movl      104(%esp), %eax
        addl      %ecx, %eax
        movl      %ebx, 20(%esp)
        movl      36(%esp), %ebx
        adcl      %ebx, %esi
        movl      %eax, 104(%esp)
        subl      %ecx, %eax
        movl      %esi, %ecx
        sbbl      %ebx, %ecx
        movl      20(%esp), %ebx
        movl      52(%esp), %ecx
        jae       ..B3.24
..B3.23:
        movl      $1, %eax
        jmp       ..B3.25
..B3.24:
        xorl      %eax, %eax
..B3.25:
        addl      %eax, %edx
        movl      %edx, 24(%esp)
        movl      56(%esp), %edx
        movl      16(%esp), %eax
        addl      %edx, %eax
        movl      %eax, 16(%esp)
        adcl      %edi, %ecx
        subl      %edx, %eax
        movl      %ecx, %edx
        sbbl      %edi, %edx
        movl      24(%esp), %edx
        jae       ..B3.27
..B3.26:
        movl      $1, 24(%esp)
        jmp       ..B3.28
..B3.27:
        movl      $0, 24(%esp)
..B3.28:
        movl      16(%esp), %edi
        addl      %edx, %edi
        movl      %edi, 16(%esp)
        adcl      $0, %ecx
        subl      %edx, %edi
        movl      $0, %edx
        movl      %ecx, %edi
        sbbl      %edx, %edi
        jae       ..B3.30
..B3.29:
        movl      $1, %edi
        jmp       ..B3.31
..B3.30:
        xorl      %edi, %edi
..B3.31:
        xorl      %eax, %eax
        movl      24(%esp), %edx
        addl      %edi, %edx
        xorl      %edi, %edi
        subl      %edx, %edi
        movl      %eax, %edx
        movl      72(%esp), %eax
        sbbl      $0, %edx
        subl      104(%esp), %eax
        movl      76(%esp), %eax
        sbbl      %esi, %eax
        jae       ..B3.33
..B3.32:
        movl      $1, 40(%esp)
        jmp       ..B3.34
..B3.33:
        movl      $0, 40(%esp)
..B3.34:
        movl      72(%esp), %eax
        subl      104(%esp), %eax
        movl      %eax, 36(%esp)
        movl      76(%esp), %eax
        sbbl      %esi, %eax
        movl      84(%esp), %esi
        subl      16(%esp), %esi
        movl      80(%esp), %esi
        movl      %eax, 24(%esp)
        sbbl      %ecx, %esi
        jae       ..B3.36
..B3.35:
        movl      $1, %esi
        jmp       ..B3.37
..B3.36:
        xorl      %esi, %esi
..B3.37:
        subl      %esi, %edi
        movl      84(%esp), %esi
        sbbl      $0, %edx
        subl      16(%esp), %esi
        movl      80(%esp), %eax
        movl      %esi, 44(%esp)
        sbbl      %ecx, %eax
        xorl      %ecx, %ecx
        subl      40(%esp), %esi
        movl      %eax, %esi
        sbbl      %ecx, %esi
        jae       ..B3.40
..B3.38:
        movl      $1, %ecx
..B3.40:
        subl      %ecx, %edi
        movl      44(%esp), %ecx
        sbbl      $0, %edx
        subl      40(%esp), %ecx
        sbbl      $0, %eax
        orl       %eax, %ecx
        je        ..B3.42
..B3.41:
        movl      $1, %esi
        jmp       ..B3.43
..B3.42:
        xorl      %esi, %esi
..B3.43:
        orl       %edi, %esi
        movl      %esi, %ecx
        movl      %edx, 40(%esp)
        orl       %edx, %ecx
        jne       ..B3.46
..B3.44:
        movl      96(%esp), %edi
        movl      100(%esp), %edx
..B3.46:
        movl      96(%esp), %ecx
        xorl      %edi, %ecx
        movl      100(%esp), %eax
        movl      36(%esp), %edi
        xorl      %edx, %eax
        subl      %ecx, %edi
        movl      %ebx, 20(%esp)
        movl      24(%esp), %ebx
        sbbl      %eax, %ebx
        movl      28(%esp), %eax
        mull      %edi
        movl      28(%esp), %eax
        movl      %edx, %ecx
        mull      %ebx
        addl      %eax, %ecx
        movl      %edi, %eax
        movl      %ebx, 24(%esp)
        movl      %edx, %ebx
        adcl      $0, %ebx
        mull      32(%esp)
        addl      %ecx, %eax
        adcl      %ebx, %edx
        subl      %ecx, %eax
        movl      %edx, 16(%esp)
        sbbl      %ebx, %edx
        movl      20(%esp), %ebx
        jae       ..B3.48
..B3.47:
        movl      $1, %ecx
        jmp       ..B3.49
..B3.48:
        xorl      %ecx, %ecx
..B3.49:
        movl      24(%esp), %eax
        xorl      %edi, %edi
        mull      32(%esp)
        addl      %edx, %ecx
        addl      16(%esp), %eax
        adcl      $0, %ecx
        movl      %ecx, %edx
        subl      %edi, %edx
        jl        ..B3.51
..B3.50:
        xorl      %edx, %edx
        jmp       ..B3.52
..B3.51:
        movl      $1, %edx
..B3.52:
        movl      84(%esp), %edi
        addl      %edx, %esi
        movl      80(%esp), %edx
        adcl      $0, 40(%esp)
        orl       72(%esp), %edi
        orl       76(%esp), %edx
        orl       %edx, %edi
        je        ..B3.54
..B3.53:
        movl      $1, %edi
        jmp       ..B3.55
..B3.54:
        xorl      %edi, %edi
..B3.55:
        movl      %eax, %edx
        addl      %eax, %eax
        shrl      $31, %edx
        addl      %ecx, %ecx
        orl       %ecx, %edx
        addl      %edi, %eax
        movl      40(%esp), %ecx
        adcl      $0, %edx
        addl      %esi, %ebx
        movl      %ebx, %edi
        adcl      %ecx, 64(%esp)
        subl      %esi, %edi
        movl      64(%esp), %esi
        sbbl      %ecx, %esi
        jae       ..B3.57
..B3.56:
        movl      $1, %ecx
        jmp       ..B3.58
..B3.57:
        xorl      %ecx, %ecx
..B3.58:
        movl      40(%esp), %esi
        sarl      $31, %esi
        movl      68(%esp), %edi
        addl      %esi, %edi
        addl      %ecx, %edi
        movl      %edi, 68(%esp)
..B3.59:
        movl      68(%esp), %ecx
        movl      %ecx, %esi
        shll      $31, %esi
        cmpl      $31, %ecx
        jbe       ..B3.61
..B3.60:
        movl      64(%esp), %edi
        shrl      %cl, %edi
        movl      $0, 20(%esp)
        movl      %edi, 24(%esp)
        jmp       ..B3.62
..B3.61:
        movl      %ebx, %edi
        movl      %eax, 16(%esp)
        movl      64(%esp), %eax
        shrdl     %cl, %eax, %edi
        shrl      %cl, %eax
        movl      %eax, 20(%esp)
        movl      %edi, 24(%esp)
        movl      16(%esp), %eax
..B3.62:
        movl      24(%ebp), %edi
        orl       20(%esp), %esi
        movl      24(%esp), %ecx
        movl      %esi, 12(%edi)
        movl      68(%esp), %esi
        andl      %esi, %ebx
        shll      $31, %ebx
        movl      %ecx, 8(%edi)
        cmpl      $31, %esi
        jbe       ..B3.64
..B3.63:
        movl      %edx, %eax
        xorl      %esi, %esi
        movl      68(%esp), %ecx
        shrl      %cl, %eax
        jmp       ..B3.65
..B3.64:
        movl      %edx, %esi
        movl      68(%esp), %ecx
        shrdl     %cl, %edx, %eax
        shrl      %cl, %esi
..B3.65:
        addl      $4, %esp
        orl       %esi, %ebx
        movl      24(%ebp), %ecx
        lea       108(%esp), %edx
        pushl     %edx
        movl      %eax, 16(%ecx)
        movl      -52(%edx), %eax
        addl      -44(%edx), %eax
        movl      %ebx, 20(%ecx)
        movl      -20(%edx), %ebx
        movl      %eax, 4(%ecx)
        call      fesetenv@PLT
..B3.66:
        movl      140(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B3.68
..B3.67:
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.68:
        call      __stack_chk_fail@PLT
..B3.69:
        movl      %ebx, %eax
        movl      24(%ebp), %edx
        movl      8(%eax), %esi
        movl      12(%eax), %edi
        movl      (%eax), %ecx
        movl      %esi, 8(%edx)
        movl      %edi, 12(%edx)
        movl      %ecx, (%edx)
        movl      4(%eax), %ebx
        movl      16(%eax), %ecx
        movl      20(%eax), %eax
        movl      %ebx, 4(%edx)
        movl      %ecx, 16(%edx)
        movl      %eax, 20(%edx)
        movl      140(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        je        ..B3.67
..B3.71:
        movl      92(%esp), %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__dpml_divide__,@function
	.size	__dpml_divide__,.-__dpml_divide__
	.data
# -- End  __dpml_divide__
	.text
# -- Begin  __dpml_evaluate_rational__
	.text
       .align    16,0x90
	.hidden __dpml_evaluate_rational__
	.globl __dpml_evaluate_rational__
__dpml_evaluate_rational__:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
# parameter 4: 24 + %ebp
# parameter 5: 32 + %ebp
..B4.1:
..L7:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $196, %esp
        movl      12(%ebp), %eax
        movl      %eax, 96(%esp)
        xorl      %eax, %eax
        movl      8(%ebp), %ecx
        movl      %gs:20, %edx
        movl      24(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 180(%esp)
        movl      %esi, %edx
        movl      28(%ebp), %edi
        andl      $68, %edx
        sarl      $26, %edi
        addl      4(%ecx), %edi
        movl      %esi, 104(%esp)
        orl       %eax, %edx
        movl      %edi, 4(%ecx)
        jne       ..B4.3
..B4.2:
        movl      8(%ebp), %ebx
        movl      $136, %edx
        cmpl      $0, (%ebx)
        jne       ..L8
        movl      $0, %edx
..L8:
        xorl      %esi, %edx
        movl      %edx, 104(%esp)
        jmp       ..B4.19
..B4.3:
        movl      %edi, 56(%esp)
        movl      8(%ebp), %edi
        movl      %esi, 88(%esp)
        movl      12(%edi), %esi
        movl      %esi, %ebx
        movl      8(%edi), %ecx
        movl      %ecx, %eax
        imull     %ecx, %ebx
        mull      %ecx
        addl      %ebx, %ebx
        addl      %ebx, %edx
        movl      16(%edi), %ebx
        movl      %eax, 36(%esp)
        movl      %ebx, %eax
        movl      %edx, 40(%esp)
        mull      %ecx
        movl      20(%edi), %edi
        movl      %edi, 48(%esp)
        movl      %edx, %edi
        movl      48(%esp), %eax
        mull      %ecx
        addl      %eax, %edi
        movl      %esi, %eax
        movl      %ecx, 44(%esp)
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %ebx
        addl      %edi, %eax
        movl      %esi, 32(%esp)
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 52(%esp)
        movl      %ebx, 64(%esp)
        lea       156(%esp), %ebx
        movl      -68(%ebx), %esi
        sbbl      %ecx, %edx
        movl      -100(%ebx), %edi
        jae       ..B4.5
..B4.4:
        movl      $1, %ecx
        jmp       ..B4.6
..B4.5:
        xorl      %ecx, %ecx
..B4.6:
        movl      %ebx, 72(%esp)
        addl      %edi, %edi
        movl      32(%esp), %eax
        movl      48(%esp), %ebx
        mull      %ebx
        addl      %edx, %ecx
        addl      52(%esp), %eax
        movl      %eax, 60(%esp)
        adcl      $0, %ecx
        movl      %ecx, 68(%esp)
        movl      64(%esp), %ecx
        movl      %ecx, %eax
        movl      %edi, 160(%esp)
        movl      44(%esp), %edi
        mull      %edi
        movl      32(%esp), %eax
        movl      %esi, 88(%esp)
        movl      %edx, %esi
        mull      %ecx
        addl      %eax, %esi
        movl      %ebx, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      %edi
        addl      %esi, %eax
        movl      $0, 156(%esp)
        adcl      %ecx, %edx
        subl      %esi, %eax
        movl      %edx, 56(%esp)
        movl      88(%esp), %esi
        sbbl      %ecx, %edx
        movl      72(%esp), %ebx
        movl      68(%esp), %ecx
        jae       ..B4.8
..B4.7:
        movl      $1, %edi
        jmp       ..B4.9
..B4.8:
        xorl      %edi, %edi
..B4.9:
        movl      32(%esp), %eax
        mull      48(%esp)
        addl      %edx, %edi
        addl      56(%esp), %eax
        movl      %eax, 52(%esp)
        adcl      $0, %edi
        movl      %edi, 64(%esp)
        movl      36(%esp), %eax
        movl      60(%esp), %edi
        addl      %edi, %eax
        movl      40(%esp), %edx
        adcl      %ecx, %edx
        movl      %eax, 36(%esp)
        subl      %edi, %eax
        movl      %edx, 40(%esp)
        movl      64(%esp), %edi
        sbbl      %ecx, %edx
        jae       ..B4.11
..B4.10:
        movl      $1, 48(%esp)
        jmp       ..B4.12
..B4.11:
        movl      $0, 48(%esp)
..B4.12:
        movl      32(%esp), %eax
        mull      44(%esp)
        movl      %esi, 88(%esp)
        movl      %eax, %esi
        movl      44(%esp), %eax
        movl      %edx, %ecx
        mull      %eax
        movl      %edx, %eax
        movl      %ecx, %edx
        addl      %esi, %eax
        adcl      $0, %edx
        addl      %eax, %esi
        adcl      %edx, %ecx
        subl      %eax, %esi
        movl      %ecx, 56(%esp)
        movl      88(%esp), %esi
        sbbl      %edx, %ecx
        jae       ..B4.14
..B4.13:
        movl      $1, %ecx
        jmp       ..B4.15
..B4.14:
        xorl      %ecx, %ecx
..B4.15:
        movl      32(%esp), %eax
        mull      %eax
        addl      %edx, %ecx
        addl      56(%esp), %eax
        movl      %eax, 44(%esp)
        adcl      $0, %ecx
        movl      %ecx, 60(%esp)
        movl      36(%esp), %eax
        movl      52(%esp), %ecx
        addl      %ecx, %eax
        movl      40(%esp), %edx
        adcl      %edi, %edx
        movl      %eax, 36(%esp)
        subl      %ecx, %eax
        movl      %edx, 40(%esp)
        movl      60(%esp), %ecx
        sbbl      %edi, %edx
        jae       ..B4.17
..B4.16:
        movl      $1, %edi
        jmp       ..B4.18
..B4.17:
        xorl      %edi, %edi
..B4.18:
        movl      48(%esp), %edx
        addl      %edi, %edx
        movl      36(%esp), %edi
        movl      %edi, 172(%esp)
        movl      40(%esp), %edi
        movl      %edi, 176(%esp)
        movl      44(%esp), %edi
        addl      %edx, %edi
        movl      %edi, 164(%esp)
        adcl      $0, %ecx
        movl      %ecx, 168(%esp)
..B4.19:
        addl      $12, %esp
        xorl      %ecx, %ecx
        pushl     %ecx
        pushl     %ecx
        pushl     %ebx
        call      __dpml_ffs_and_shift__
..B4.20:
        xorl      %ecx, %ecx
        movl      4(%ebx), %eax
        cltd      
        xorl      %eax, %eax
        movl      16(%ebp), %edi
        subl      %edi, %eax
        sbbl      20(%ebp), %ecx
        imull     4(%ebx), %ecx
        imull     %eax, %edx
        addl      %edx, %ecx
        mull      4(%ebx)
        addl      %ecx, %edx
        movl      %esi, %ecx
        movl      %edx, 112(%esp)
        andl      $512, %ecx
        xorl      %edx, %edx
        shll      $4, %edi
        movl      %eax, 108(%esp)
        orl       %edx, %ecx
        movl      %edi, 100(%esp)
        je        ..B4.22
..B4.21:
        movl      %esi, %ecx
        andl      $1024, %ecx
        orl       %edx, %ecx
        je        ..B4.23
..B4.22:
        xorl      %edx, %edx
        jmp       ..B4.24
..B4.23:
        movl      $1, %edx
..B4.24:
        movl      32(%ebp), %ecx
        lea       (%edx,%edx,2), %edx
        movl      %esi, %eax
        lea       (,%edx,8), %edi
        andl      $15, %eax
        lea       (%ecx,%edx,8), %ecx
        xorl      %edx, %edx
        orl       %edx, %eax
        call      ..L9
..L9:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L9](%edx), %edx
        movl      %edx, 124(%esp)
        je        ..B4.52
..B4.25:
        negl      %edi
        xorl      %edx, %edx
        addl      32(%ebp), %edi
        movl      104(%esp), %eax
        addl      $24, %edi
        andl      $8, %eax
        orl       %edx, %eax
        je        ..B4.27
..B4.26:
        movl      124(%esp), %edx
        lea       __eval_neg_poly@GOTOFF(%edx), %edx
        movl      %edx, 32(%esp)
        jmp       ..B4.28
..B4.27:
        movl      124(%esp), %eax
        lea       __eval_pos_poly@GOTOFF(%eax), %edx
        lea       __eval_neg_poly@GOTOFF(%eax), %eax
        movl      %eax, 32(%esp)
..B4.28:
        movl      %esi, 88(%esp)
        andl      $240, %esi
        xorl      %eax, %eax
        movl      %esi, 116(%esp)
        orl       %esi, %eax
        movl      88(%esp), %esi
        jne       ..B4.30
..B4.29:
        movl      32(%ebp), %ecx
..B4.30:
        cmpl      32(%esp), %edx
        jne       ..B4.32
..B4.31:
        movl      108(%esp), %edx
        movl      %ebx, %eax
        movl      %edx, 4(%esp)
        movl      112(%esp), %edx
        movl      %edx, 8(%esp)
        movl      16(%ebp), %edx
        movl      %edx, 16(%esp)
        movl      20(%ebp), %edx
        movl      %edx, 20(%esp)
        movl      96(%esp), %edx
        movl      %ecx, 32(%esp)
        call      __eval_neg_poly.
        jmp       ..B4.153
..B4.32:
        movl      108(%esp), %edx
        movl      %ebx, %eax
        movl      %edx, 4(%esp)
        movl      112(%esp), %edx
        movl      %edx, 8(%esp)
        movl      16(%ebp), %edx
        movl      %edx, 16(%esp)
        movl      20(%ebp), %edx
        movl      %edx, 20(%esp)
        movl      96(%esp), %edx
        movl      %ecx, 32(%esp)
        call      __eval_pos_poly.
..B4.153:
        movl      32(%esp), %ecx
..B4.33:
        movl      %esi, %eax
        xorl      %edx, %edx
        andl      $2, %eax
        orl       %edx, %eax
        jne       ..B4.35
..B4.34:
        movl      4(%ecx), %edx
        movl      %edx, 92(%esp)
        jmp       ..B4.51
..B4.35:
        movl      8(%ebp), %edx
        movl      %edi, 48(%esp)
        movl      %esi, 88(%esp)
        movl      12(%edx), %esi
        movl      12(%ecx), %edi
        movl      8(%edx), %eax
        movl      %ebx, 72(%esp)
        movl      8(%ecx), %ebx
        movl      %esi, 32(%esp)
        movl      %edi, 36(%esp)
        imull     %ebx, %esi
        imull     %eax, %edi
        movl      %eax, 52(%esp)
        addl      %edi, %esi
        mull      %ebx
        addl      %esi, %edx
        movl      %edx, 44(%esp)
        movl      8(%ebp), %edx
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        movl      8(%ebp), %edi
        mull      16(%edx)
        movl      16(%ecx), %esi
        movl      %ebx, %eax
        movl      %esi, 64(%esp)
        movl      20(%ecx), %esi
        movl      20(%edi), %edi
        movl      %esi, 60(%esp)
        movl      %edx, %esi
        mull      %edi
        movl      %ebx, 56(%esp)
        addl      %eax, %esi
        movl      8(%ebp), %ebx
        movl      %edi, 84(%esp)
        movl      %edx, %edi
        adcl      $0, %edi
        movl      16(%ebx), %eax
        mull      36(%esp)
        addl      %esi, %eax
        movl      72(%esp), %ebx
        adcl      %edi, %edx
        subl      %esi, %eax
        movl      %edx, 68(%esp)
        movl      88(%esp), %esi
        sbbl      %edi, %edx
        movl      48(%esp), %edi
        jae       ..B4.37
..B4.36:
        movl      $1, 80(%esp)
        jmp       ..B4.38
..B4.37:
        movl      $0, 80(%esp)
..B4.38:
        movl      84(%esp), %eax
        mull      36(%esp)
        movl      %esi, 88(%esp)
        movl      80(%esp), %esi
        addl      %edx, %esi
        addl      68(%esp), %eax
        movl      %edi, 48(%esp)
        adcl      $0, %esi
        movl      %esi, 80(%esp)
        movl      8(%ebp), %esi
        movl      %eax, 76(%esp)
        movl      52(%esp), %eax
        movl      4(%esi), %edx
        movl      (%esi), %edi
        addl      4(%ecx), %edx
        xorl      %edi, (%ecx)
        movl      64(%esp), %edi
        movl      %edx, 92(%esp)
        movl      %edx, 4(%ecx)
        mull      %edi
        movl      %edi, %eax
        movl      %edx, %esi
        mull      32(%esp)
        addl      %eax, %esi
        movl      %edx, %edi
        movl      52(%esp), %eax
        adcl      $0, %edi
        mull      60(%esp)
        addl      %esi, %eax
        adcl      %edi, %edx
        subl      %esi, %eax
        movl      %edx, 72(%esp)
        movl      88(%esp), %esi
        sbbl      %edi, %edx
        movl      48(%esp), %edi
        jae       ..B4.40
..B4.39:
        movl      $1, 68(%esp)
        jmp       ..B4.41
..B4.40:
        movl      $0, 68(%esp)
..B4.41:
        movl      60(%esp), %eax
        mull      32(%esp)
        movl      %esi, 88(%esp)
        movl      68(%esp), %esi
        addl      %edx, %esi
        addl      72(%esp), %eax
        movl      %eax, 64(%esp)
        adcl      $0, %esi
        movl      %esi, 68(%esp)
        movl      76(%esp), %esi
        movl      40(%esp), %eax
        addl      %esi, %eax
        movl      %edi, 48(%esp)
        movl      44(%esp), %edi
        movl      80(%esp), %edx
        adcl      %edx, %edi
        movl      %eax, 40(%esp)
        subl      %esi, %eax
        movl      %edi, 44(%esp)
        movl      88(%esp), %esi
        sbbl      %edx, %edi
        movl      48(%esp), %edi
        jae       ..B4.43
..B4.42:
        movl      $1, 72(%esp)
        jmp       ..B4.44
..B4.43:
        movl      $0, 72(%esp)
..B4.44:
        movl      %edi, 48(%esp)
        movl      56(%esp), %edi
        movl      %edi, %eax
        mull      52(%esp)
        movl      %edi, %eax
        movl      %esi, 88(%esp)
        movl      %edx, %esi
        mull      32(%esp)
        addl      %eax, %esi
        movl      %edx, %edi
        movl      52(%esp), %eax
        adcl      $0, %edi
        mull      36(%esp)
        addl      %esi, %eax
        adcl      %edi, %edx
        subl      %esi, %eax
        movl      %edx, 60(%esp)
        movl      88(%esp), %esi
        sbbl      %edi, %edx
        movl      48(%esp), %edi
        jae       ..B4.46
..B4.45:
        movl      $1, 56(%esp)
        jmp       ..B4.47
..B4.46:
        movl      $0, 56(%esp)
..B4.47:
        movl      32(%esp), %eax
        mull      36(%esp)
        movl      %esi, 88(%esp)
        movl      56(%esp), %esi
        addl      %edx, %esi
        addl      60(%esp), %eax
        movl      %eax, 52(%esp)
        adcl      $0, %esi
        movl      %esi, 56(%esp)
        movl      64(%esp), %esi
        movl      40(%esp), %eax
        addl      %esi, %eax
        movl      %edi, 48(%esp)
        movl      44(%esp), %edi
        movl      68(%esp), %edx
        adcl      %edx, %edi
        movl      %eax, 40(%esp)
        subl      %esi, %eax
        movl      %edi, 44(%esp)
        movl      88(%esp), %esi
        sbbl      %edx, %edi
        movl      48(%esp), %edi
        jae       ..B4.49
..B4.48:
        movl      $1, %edx
        jmp       ..B4.50
..B4.49:
        xorl      %edx, %edx
..B4.50:
        movl      72(%esp), %eax
        addl      %edx, %eax
        movl      40(%esp), %edx
        movl      %edx, 16(%ecx)
        movl      44(%esp), %edx
        movl      %edx, 20(%ecx)
        movl      52(%esp), %edx
        addl      %eax, %edx
        movl      56(%esp), %eax
        adcl      $0, %eax
        movl      %edx, 8(%ecx)
        movl      %eax, 12(%ecx)
..B4.51:
        movl      96(%esp), %edx
        movl      100(%esp), %eax
        lea       24(%edx,%eax), %edx
        movl      92(%esp), %eax
        addl      -8(%edx), %eax
        movl      %edx, 96(%esp)
        movl      %eax, 4(%ecx)
        jmp       ..B4.54
..B4.52:
        orl       $-1792, %esi
        movl      %esi, %edx
        movl      %esi, %eax
        andl      $240, %edx
        andl      $1024, %eax
        movl      %edx, 116(%esp)
        xorl      %edx, %edx
        movl      32(%ebp), %edi
        orl       %edx, %eax
        je        ..B4.54
..B4.53:
        movl      96(%esp), %edx
        movl      100(%esp), %eax
        lea       24(%edx,%eax), %edx
        movl      %edx, 96(%esp)
..B4.54:
        xorl      %edx, %edx
        movl      116(%esp), %eax
        orl       %edx, %eax
        je        ..B4.82
..B4.55:
        movl      104(%esp), %eax
        andl      $128, %eax
        orl       %edx, %eax
        je        ..B4.57
..B4.56:
        movl      124(%esp), %edx
        lea       __eval_neg_poly@GOTOFF(%edx), %eax
        movl      %eax, %edx
        jmp       ..B4.58
..B4.57:
        movl      124(%esp), %edx
        lea       __eval_pos_poly@GOTOFF(%edx), %eax
        lea       __eval_neg_poly@GOTOFF(%edx), %edx
..B4.58:
        cmpl      %edx, %eax
        jne       ..B4.60
..B4.59:
        movl      108(%esp), %edx
        movl      %ebx, %eax
        movl      %edx, 4(%esp)
        movl      %edi, %ecx
        movl      112(%esp), %edx
        movl      %edx, 8(%esp)
        movl      16(%ebp), %edx
        movl      %edx, 16(%esp)
        movl      20(%ebp), %edx
        movl      %edx, 20(%esp)
        movl      96(%esp), %edx
        call      __eval_neg_poly.
        jmp       ..B4.61
..B4.60:
        movl      108(%esp), %edx
        movl      %ebx, %eax
        movl      %edx, 4(%esp)
        movl      %edi, %ecx
        movl      112(%esp), %edx
        movl      %edx, 8(%esp)
        movl      16(%ebp), %edx
        movl      %edx, 16(%esp)
        movl      20(%ebp), %edx
        movl      %edx, 20(%esp)
        movl      96(%esp), %edx
        call      __eval_pos_poly.
..B4.61:
        movl      %esi, %edx
        xorl      %eax, %eax
        andl      $32, %edx
        orl       %eax, %edx
        jne       ..B4.63
..B4.62:
        movl      4(%edi), %ebx
        jmp       ..B4.79
..B4.63:
        movl      8(%ebp), %ebx
        movl      %esi, 88(%esp)
        movl      12(%edi), %edx
        movl      12(%ebx), %esi
        movl      8(%ebx), %eax
        movl      8(%edi), %ebx
        movl      %esi, 32(%esp)
        movl      %edx, 36(%esp)
        imull     %ebx, %esi
        imull     %eax, %edx
        movl      %eax, 48(%esp)
        addl      %edx, %esi
        mull      %ebx
        addl      %esi, %edx
        movl      %edx, 44(%esp)
        movl      8(%ebp), %edx
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        movl      8(%ebp), %esi
        mull      16(%edx)
        movl      20(%esi), %ecx
        movl      %ebx, %eax
        movl      16(%edi), %esi
        movl      %esi, 68(%esp)
        movl      20(%edi), %esi
        movl      %esi, 56(%esp)
        movl      %edx, %esi
        mull      %ecx
        movl      %ebx, 52(%esp)
        movl      %edx, %ebx
        movl      8(%ebp), %edx
        addl      %eax, %esi
        movl      %ecx, 76(%esp)
        adcl      $0, %ebx
        movl      16(%edx), %eax
        mull      36(%esp)
        addl      %esi, %eax
        adcl      %ebx, %edx
        subl      %esi, %eax
        movl      %edx, 72(%esp)
        movl      88(%esp), %esi
        sbbl      %ebx, %edx
        jae       ..B4.65
..B4.64:
        movl      $1, %ecx
        jmp       ..B4.66
..B4.65:
        xorl      %ecx, %ecx
..B4.66:
        movl      76(%esp), %eax
        mull      36(%esp)
        addl      %edx, %ecx
        addl      72(%esp), %eax
        movl      %eax, 64(%esp)
        movl      8(%ebp), %eax
        adcl      $0, %ecx
        movl      %ecx, 80(%esp)
        movl      68(%esp), %ecx
        movl      4(%eax), %edx
        addl      4(%edi), %edx
        movl      (%eax), %ebx
        movl      48(%esp), %eax
        movl      %edx, 84(%esp)
        movl      %edx, 4(%edi)
        mull      %ecx
        movl      %ecx, %eax
        xorl      %ebx, (%edi)
        movl      %edx, %ebx
        mull      32(%esp)
        addl      %eax, %ebx
        movl      %edx, %ecx
        movl      48(%esp), %eax
        adcl      $0, %ecx
        mull      56(%esp)
        addl      %ebx, %eax
        adcl      %ecx, %edx
        subl      %ebx, %eax
        movl      %edx, 60(%esp)
        movl      84(%esp), %ebx
        sbbl      %ecx, %edx
        movl      80(%esp), %ecx
        jae       ..B4.68
..B4.67:
        movl      $1, 72(%esp)
        jmp       ..B4.69
..B4.68:
        movl      $0, 72(%esp)
..B4.69:
        movl      56(%esp), %eax
        mull      32(%esp)
        movl      %esi, 88(%esp)
        movl      72(%esp), %esi
        addl      %edx, %esi
        addl      60(%esp), %eax
        movl      %eax, 68(%esp)
        adcl      $0, %esi
        movl      %esi, 72(%esp)
        movl      40(%esp), %eax
        movl      64(%esp), %esi
        addl      %esi, %eax
        movl      44(%esp), %edx
        adcl      %ecx, %edx
        movl      %eax, 40(%esp)
        subl      %esi, %eax
        movl      %edx, 44(%esp)
        movl      88(%esp), %esi
        sbbl      %ecx, %edx
        jae       ..B4.71
..B4.70:
        movl      $1, 60(%esp)
        jmp       ..B4.72
..B4.71:
        movl      $0, 60(%esp)
..B4.72:
        movl      52(%esp), %ecx
        movl      %ecx, %eax
        mull      48(%esp)
        movl      %ecx, %eax
        movl      %esi, 88(%esp)
        movl      %edx, %esi
        mull      32(%esp)
        addl      %eax, %esi
        movl      %edx, %ecx
        movl      48(%esp), %eax
        adcl      $0, %ecx
        mull      36(%esp)
        addl      %esi, %eax
        adcl      %ecx, %edx
        subl      %esi, %eax
        movl      %edx, 56(%esp)
        movl      88(%esp), %esi
        sbbl      %ecx, %edx
        jae       ..B4.74
..B4.73:
        movl      $1, %ecx
        jmp       ..B4.75
..B4.74:
        xorl      %ecx, %ecx
..B4.75:
        movl      32(%esp), %eax
        mull      36(%esp)
        addl      %edx, %ecx
        addl      56(%esp), %eax
        movl      %esi, 88(%esp)
        adcl      $0, %ecx
        movl      %ecx, 52(%esp)
        movl      68(%esp), %esi
        movl      40(%esp), %ecx
        addl      %esi, %ecx
        movl      %eax, 48(%esp)
        movl      44(%esp), %edx
        movl      72(%esp), %eax
        adcl      %eax, %edx
        movl      %ecx, 40(%esp)
        subl      %esi, %ecx
        movl      %edx, 44(%esp)
        movl      52(%esp), %ecx
        sbbl      %eax, %edx
        movl      88(%esp), %esi
        jae       ..B4.77
..B4.76:
        movl      $1, %edx
        jmp       ..B4.78
..B4.77:
        xorl      %edx, %edx
..B4.78:
        movl      60(%esp), %eax
        addl      %edx, %eax
        movl      40(%esp), %edx
        movl      %edx, 16(%edi)
        movl      44(%esp), %edx
        movl      %edx, 20(%edi)
        movl      48(%esp), %edx
        addl      %eax, %edx
        movl      %edx, 8(%edi)
        adcl      $0, %ecx
        movl      %ecx, 12(%edi)
..B4.79:
        movl      96(%esp), %edx
        xorl      %ecx, %ecx
        movl      100(%esp), %eax
        addl      16(%eax,%edx), %ebx
        movl      %ebx, 4(%edi)
        movl      %esi, %ebx
        andl      $1024, %ebx
        orl       %ecx, %ebx
        je        ..B4.83
..B4.80:
        movl      180(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B4.148
..B4.81:
        addl      $196, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B4.82:
        orl       $-256, %esi
..B4.83:
        andl      $256, %esi
        xorl      %eax, %eax
        orl       %eax, %esi
        jne       ..B4.146
..B4.84:
        movl      124(%esp), %eax
        movl      32(%ebp), %ecx
        movl      %ecx, %edi
        addl      $24, %edi
        lea       __ux_one__@GOTOFF(%eax), %edx
        jne       ..L10
        movl      %edx, %edi
..L10:
        testl     %ecx, %ecx
        movl      12(%edi), %esi
        jne       ..L11
        movl      %edx, %ecx
..L11:
        movl      8(%edi), %ebx
        cmpl      %edx, %edi
        movl      16(%edi), %eax
        movl      %esi, 112(%esp)
        movl      %ecx, 120(%esp)
        movl      %ebx, 108(%esp)
        movl      %eax, 116(%esp)
        movl      20(%edi), %esi
        je        ..B4.150
..B4.85:
        xorl      %edx, %edx
        movl      112(%esp), %ecx
        subl      %edx, %ecx
        jl        ..B4.88
..B4.86:
        addl      $12, %esp
        pushl     %edx
        pushl     %edx
        pushl     %edi
        call      __dpml_ffs_and_shift__
..B4.87:
        movl      8(%edi), %eax
        movl      12(%edi), %edx
        movl      16(%edi), %ecx
        movl      20(%edi), %esi
        movl      %eax, 108(%esp)
        movl      %edx, 112(%esp)
        movl      %ecx, 116(%esp)
..B4.88:
        addl      $4, %esp
        lea       124(%esp), %edx
        pushl     %edx
        movl      -4(%edx), %ebx
        call      fegetenv@PLT
..B4.89:
        movl      %esi, 72(%esp)
        movl      112(%esp), %esi
        movl      %esi, %eax
        movl      108(%esp), %ecx
        movl      %ecx, %edx
        movl      %edi, 44(%esp)
        movl      %esi, %edi
        shll      $31, %eax
        shrl      $1, %edx
        shrl      $1, %edi
        orl       %edx, %eax
        movl      %eax, 48(%esp)
        movl      %esi, %eax
        movl      %edi, 52(%esp)
        andl      $-64, %eax
        fildll    48(%esp)
        andl      $63, %esi
        fstpl     64(%esp)
        xorl      %edx, %edx
        fldl      64(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fdiv      %st, %st(1)
        shrl      $1, %eax
        movl      %eax, 52(%esp)
        movl      %ecx, %eax
        movl      120(%esp), %edi
        shrl      $17, %eax
        shll      $15, %esi
        movl      %edx, 48(%esp)
        orl       %esi, %eax
        movl      72(%esp), %esi
        shll      $15, %ecx
        shrl      $17, %esi
        orl       %esi, %ecx
        movl      12(%edi), %esi
        movl      %esi, 88(%esp)
        fxch      %st(1)
        fstpl     64(%esp)
        fldl      64(%esp)
        movl      (%edi), %esi
        fld       %st(0)
        fildll    48(%esp)
        movl      %eax, 52(%esp)
        movl      %ecx, 48(%esp)
        movl      8(%edi), %eax
        movl      16(%edi), %ecx
        movl      %eax, 92(%esp)
        movl      %ecx, 80(%esp)
        movl      20(%edi), %eax
        movl      44(%esp), %ecx
        movl      %eax, 84(%esp)
        movl      32(%ebp), %eax
        xorl      (%ecx), %esi
        fstpl     64(%esp)
        fldl      64(%esp)
        fildll    48(%esp)
        movl      %esi, (%eax)
        fstpl     64(%esp)
        fxch      %st(1)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   56(%esp)
        fldcw     32(%esp)
        movl      %edx, 48(%esp)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        fldl      64(%esp)
        movl      56(%esp), %eax
        addl      $-1280, %eax
        movl      60(%esp), %esi
        adcl      $-1, %esi
        fmul      %st(1), %st
        andl      $-16, %esi
        movl      %esi, 52(%esp)
        fstpl     64(%esp)
        fldl      64(%esp)
        fildll    48(%esp)
        fstpl     64(%esp)
        fldl      64(%esp)
        fmul      %st, %st(3)
        movl      4(%edi), %edi
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fmul      %st(2), %st
        movl      %esi, 96(%esp)
        fsubrp    %st, %st(4)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ebx)
        movl      88(%esp), %eax
        movl      %eax, %ebx
        movl      92(%esp), %esi
        subl      4(%ecx), %edi
        movl      %esi, %ecx
        shll      $21, %ebx
        shrl      $11, %ecx
        fmulp     %st, %st(3)
        orl       %ecx, %ebx
        movl      %edi, 100(%esp)
        movl      %eax, %edi
        shrl      $1, %edi
        movl      %ebx, %ecx
        shrl      $22, %ecx
        andl      $-1024, %edi
        shll      $10, %ebx
        orl       %edi, %ecx
        movl      %ebx, 48(%esp)
        movl      %eax, %ebx
        movl      %ecx, 52(%esp)
        andl      $-64, %ebx
        fxch      %st(2)
        fmulp     %st, %st(3)
        andl      $63, %eax
        shrl      $1, %ebx
        movl      84(%esp), %ecx
        shll      $15, %eax
        shrl      $17, %ecx
        fxch      %st(2)
        fstpl     64(%esp)
        fldl      64(%esp)
        fildll    48(%esp)
        movl      %edx, 48(%esp)
        movl      %esi, %edx
        movl      %ebx, 52(%esp)
        shrl      $17, %edx
        shll      $15, %esi
        orl       %eax, %edx
        orl       %ecx, %esi
        fstpl     64(%esp)
        fldl      64(%esp)
        movl      124(%esp), %eax
        fmul      %st(1), %st
        fildll    48(%esp)
        movl      %esi, 48(%esp)
        movl      %edx, 52(%esp)
        fstpl     64(%esp)
        fldl      64(%esp)
        fildll    48(%esp)
        fstpl     64(%esp)
        fldl      64(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fstpl     64(%esp)
        fldl      64(%esp)
        fldt      .L_2il0floatpacket.3@GOTOFF(%eax)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpl     64(%esp)
        fldl      64(%esp)
        fmul      %st, %st(3)
        fxch      %st(3)
        fstpl     64(%esp)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldl      64(%esp)
        fxch      %st(2)
        fstpl     64(%esp)
        fldl      64(%esp)
        fldt      .L_2il0floatpacket.4@GOTOFF(%eax)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   56(%esp)
        fldcw     32(%esp)
        movl      56(%esp), %ebx
        movl      %ebx, %edx
        movl      60(%esp), %ecx
        movl      %ecx, %edi
        fxch      %st(1)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   56(%esp)
        fldcw     32(%esp)
        shrl      $30, %edx
        shll      $2, %ebx
        shll      $2, %ecx
        movl      56(%esp), %esi
        orl       %ecx, %edx
        addl      %esi, %ebx
        movl      %ebx, %ecx
        movl      60(%esp), %eax
        adcl      %eax, %edx
        shrl      $30, %edi
        subl      %esi, %ecx
        movl      %edx, 76(%esp)
        movl      %edi, 104(%esp)
        sbbl      %eax, %edx
        movl      72(%esp), %esi
        movl      96(%esp), %edx
        jae       ..B4.91
..B4.90:
        movl      $1, %ecx
        jmp       ..B4.92
..B4.91:
        xorl      %ecx, %ecx
..B4.92:
        addl      %ecx, 104(%esp)
        movl      124(%esp), %ecx
        fldt      .L_2il0floatpacket.5@GOTOFF(%ecx)
        fmulp     %st, %st(1)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   56(%esp)
        fldcw     32(%esp)
        movl      60(%esp), %edi
        movl      56(%esp), %ecx
        movl      %ecx, 44(%esp)
        lea       (%edi,%edx,4), %edx
        movl      %edx, 48(%esp)
        orl       %edx, %ecx
        jne       ..B4.94
..B4.93:
        movl      $-1, %edx
        movl      %edx, 44(%esp)
        movl      %edx, 48(%esp)
..B4.94:
        movl      %ebx, %eax
        xorl      %edi, %edi
        mull      116(%esp)
        xorl      %ecx, %ecx
        movl      %ebx, %eax
        subl      104(%esp), %ecx
        movl      %ecx, 32(%esp)
        movl      %edx, %ecx
        sbbl      $0, %edi
        mull      %esi
        addl      %eax, %ecx
        movl      116(%esp), %eax
        movl      %edi, 68(%esp)
        movl      %edx, %edi
        adcl      $0, %edi
        mull      76(%esp)
        addl      %ecx, %eax
        adcl      %edi, %edx
        subl      %ecx, %eax
        movl      %edx, 36(%esp)
        movl      68(%esp), %ecx
        sbbl      %edi, %edx
        jae       ..B4.96
..B4.95:
        movl      $1, 64(%esp)
        jmp       ..B4.97
..B4.96:
        movl      $0, 64(%esp)
..B4.97:
        movl      %esi, %eax
        movl      %esi, 72(%esp)
        movl      76(%esp), %esi
        mull      %esi
        movl      %ecx, 68(%esp)
        movl      64(%esp), %ecx
        addl      %edx, %ecx
        movl      %esi, %edx
        addl      36(%esp), %eax
        movl      108(%esp), %esi
        adcl      $0, %ecx
        movl      %ecx, 64(%esp)
        movl      112(%esp), %ecx
        movl      %ecx, %edi
        imull     %ebx, %edi
        imull     %esi, %edx
        movl      %eax, 60(%esp)
        movl      %esi, %eax
        addl      %edx, %edi
        mull      %ebx
        movl      %eax, 40(%esp)
        movl      %ebx, %eax
        addl      %edi, %edx
        movl      %edx, 52(%esp)
        mull      %esi
        movl      %ebx, %eax
        movl      %edx, %edi
        mull      %ecx
        addl      %eax, %edi
        movl      %esi, %eax
        movl      %edx, %ecx
        adcl      $0, %ecx
        mull      76(%esp)
        addl      %edi, %eax
        movl      72(%esp), %esi
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 56(%esp)
        sbbl      %ecx, %edx
        movl      68(%esp), %ecx
        jae       ..B4.99
..B4.98:
        movl      $1, %edi
        jmp       ..B4.100
..B4.99:
        xorl      %edi, %edi
..B4.100:
        movl      %ebx, 36(%esp)
        andl      %ecx, %esi
        movl      112(%esp), %ebx
        movl      %ebx, %eax
        mull      76(%esp)
        addl      %edx, %edi
        andl      %ebx, %ecx
        addl      56(%esp), %eax
        movl      %eax, 72(%esp)
        movl      116(%esp), %edx
        adcl      $0, %edi
        movl      32(%esp), %eax
        andl      %eax, %edx
        movl      60(%esp), %ebx
        andl      108(%esp), %eax
        addl      %ebx, %edx
        movl      %eax, 32(%esp)
        movl      64(%esp), %eax
        adcl      %eax, %esi
        movl      %edx, 116(%esp)
        subl      %ebx, %edx
        movl      %esi, %ebx
        sbbl      %eax, %ebx
        movl      36(%esp), %ebx
        jae       ..B4.102
..B4.101:
        movl      $1, %edx
        jmp       ..B4.103
..B4.102:
        xorl      %edx, %edx
..B4.103:
        movl      %ecx, 68(%esp)
        movl      40(%esp), %ecx
        movl      116(%esp), %eax
        addl      %ecx, %eax
        movl      %ebx, 36(%esp)
        movl      52(%esp), %ebx
        adcl      %ebx, %esi
        movl      %eax, 116(%esp)
        subl      %ecx, %eax
        movl      %esi, %ecx
        sbbl      %ebx, %ecx
        movl      36(%esp), %ebx
        movl      68(%esp), %ecx
        jae       ..B4.105
..B4.104:
        movl      $1, %eax
        jmp       ..B4.106
..B4.105:
        xorl      %eax, %eax
..B4.106:
        addl      %eax, %edx
        movl      %edx, 40(%esp)
        movl      72(%esp), %edx
        movl      32(%esp), %eax
        addl      %edx, %eax
        movl      %eax, 32(%esp)
        adcl      %edi, %ecx
        subl      %edx, %eax
        movl      %ecx, %edx
        sbbl      %edi, %edx
        movl      40(%esp), %edx
        jae       ..B4.108
..B4.107:
        movl      $1, 40(%esp)
        jmp       ..B4.109
..B4.108:
        movl      $0, 40(%esp)
..B4.109:
        movl      32(%esp), %edi
        addl      %edx, %edi
        movl      %edi, 32(%esp)
        adcl      $0, %ecx
        subl      %edx, %edi
        movl      $0, %edx
        movl      %ecx, %edi
        sbbl      %edx, %edi
        jae       ..B4.111
..B4.110:
        movl      $1, %edi
        jmp       ..B4.112
..B4.111:
        xorl      %edi, %edi
..B4.112:
        xorl      %eax, %eax
        movl      40(%esp), %edx
        addl      %edi, %edx
        xorl      %edi, %edi
        subl      %edx, %edi
        movl      %eax, %edx
        movl      80(%esp), %eax
        sbbl      $0, %edx
        subl      116(%esp), %eax
        movl      84(%esp), %eax
        sbbl      %esi, %eax
        jae       ..B4.114
..B4.113:
        movl      $1, 56(%esp)
        jmp       ..B4.115
..B4.114:
        movl      $0, 56(%esp)
..B4.115:
        movl      80(%esp), %eax
        subl      116(%esp), %eax
        movl      %eax, 52(%esp)
        movl      84(%esp), %eax
        sbbl      %esi, %eax
        movl      92(%esp), %esi
        subl      32(%esp), %esi
        movl      88(%esp), %esi
        movl      %eax, 40(%esp)
        sbbl      %ecx, %esi
        jae       ..B4.117
..B4.116:
        movl      $1, %esi
        jmp       ..B4.118
..B4.117:
        xorl      %esi, %esi
..B4.118:
        subl      %esi, %edi
        movl      92(%esp), %esi
        sbbl      $0, %edx
        subl      32(%esp), %esi
        movl      88(%esp), %eax
        sbbl      %ecx, %eax
        movl      %esi, %ecx
        subl      56(%esp), %ecx
        movl      $0, %ecx
        movl      %eax, 60(%esp)
        sbbl      %ecx, %eax
        jae       ..B4.121
..B4.119:
        movl      $1, %ecx
..B4.121:
        subl      %ecx, %edi
        movl      60(%esp), %ecx
        sbbl      $0, %edx
        subl      56(%esp), %esi
        sbbl      $0, %ecx
        orl       %ecx, %esi
        je        ..B4.123
..B4.122:
        movl      $1, %ecx
        jmp       ..B4.124
..B4.123:
        xorl      %ecx, %ecx
..B4.124:
        orl       %edi, %ecx
        movl      %ecx, %esi
        movl      %edx, 56(%esp)
        orl       %edx, %esi
        jne       ..B4.127
..B4.125:
        movl      108(%esp), %edi
        movl      112(%esp), %edx
..B4.127:
        movl      %ebx, 36(%esp)
        movl      108(%esp), %ebx
        xorl      %edi, %ebx
        movl      112(%esp), %eax
        movl      52(%esp), %edi
        xorl      %edx, %eax
        subl      %ebx, %edi
        movl      40(%esp), %esi
        sbbl      %eax, %esi
        movl      44(%esp), %eax
        mull      %edi
        movl      44(%esp), %eax
        movl      %edx, %ebx
        mull      %esi
        addl      %eax, %ebx
        movl      %edi, %eax
        movl      %esi, 40(%esp)
        movl      %edx, %esi
        adcl      $0, %esi
        mull      48(%esp)
        addl      %ebx, %eax
        adcl      %esi, %edx
        subl      %ebx, %eax
        movl      %edx, 32(%esp)
        movl      36(%esp), %ebx
        sbbl      %esi, %edx
        jae       ..B4.129
..B4.128:
        movl      $1, %esi
        jmp       ..B4.130
..B4.129:
        xorl      %esi, %esi
..B4.130:
        movl      40(%esp), %eax
        xorl      %edi, %edi
        mull      48(%esp)
        addl      %edx, %esi
        addl      32(%esp), %eax
        adcl      $0, %esi
        movl      %esi, %edx
        subl      %edi, %edx
        jl        ..B4.132
..B4.131:
        xorl      %edx, %edx
        jmp       ..B4.133
..B4.132:
        movl      $1, %edx
..B4.133:
        movl      92(%esp), %edi
        addl      %edx, %ecx
        movl      88(%esp), %edx
        adcl      $0, 56(%esp)
        orl       80(%esp), %edi
        orl       84(%esp), %edx
        orl       %edx, %edi
        je        ..B4.135
..B4.134:
        movl      $1, %edx
        jmp       ..B4.136
..B4.135:
        xorl      %edx, %edx
..B4.136:
        movl      %eax, %edi
        addl      %eax, %eax
        shrl      $31, %edi
        addl      %esi, %esi
        orl       %esi, %edi
        addl      %edx, %eax
        movl      56(%esp), %edx
        adcl      $0, %edi
        addl      %ecx, %ebx
        movl      %ebx, %esi
        movl      %edi, 32(%esp)
        movl      76(%esp), %edi
        adcl      %edx, %edi
        subl      %ecx, %esi
        movl      %edi, 76(%esp)
        sbbl      %edx, %edi
        jae       ..B4.138
..B4.137:
        movl      $1, %edx
        jmp       ..B4.139
..B4.138:
        xorl      %edx, %edx
..B4.139:
        movl      56(%esp), %edi
        sarl      $31, %edi
        movl      104(%esp), %ecx
        addl      %edi, %ecx
        addl      %edx, %ecx
        movl      %ecx, %edi
        shll      $31, %edi
        movl      %ecx, 104(%esp)
        cmpl      $31, %ecx
        jbe       ..B4.141
..B4.140:
        xorl      %edx, %edx
        movl      76(%esp), %esi
        shrl      %cl, %esi
        jmp       ..B4.142
..B4.141:
        movl      %ebx, %esi
        movl      76(%esp), %edx
        shrdl     %cl, %edx, %esi
        shrl      %cl, %edx
..B4.142:
        orl       %edx, %edi
        movl      32(%ebp), %edx
        andl      %ecx, %ebx
        shll      $31, %ebx
        movl      %esi, 8(%edx)
        cmpl      $31, %ecx
        movl      %edi, 12(%edx)
        jbe       ..B4.144
..B4.143:
        xorl      %edx, %edx
        movl      32(%esp), %eax
        shrl      %cl, %eax
        jmp       ..B4.145
..B4.144:
        movl      32(%esp), %edx
        shrdl     %cl, %edx, %eax
        shrl      %cl, %edx
..B4.145:
        addl      $4, %esp
        orl       %edx, %ebx
        movl      32(%ebp), %edx
        lea       124(%esp), %ecx
        pushl     %ecx
        movl      %eax, 16(%edx)
        movl      -28(%ecx), %eax
        addl      -24(%ecx), %eax
        movl      %ebx, 20(%edx)
        movl      -4(%ecx), %ebx
        movl      %eax, 4(%edx)
        call      fesetenv@PLT
..B4.146:
        movl      180(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B4.148
..B4.147:
        addl      $196, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B4.148:
        movl      124(%esp), %ebx
        call      __stack_chk_fail@PLT
..B4.150:
        movl      %ecx, %eax
        movl      32(%ebp), %edx
        movl      8(%eax), %esi
        movl      12(%eax), %edi
        movl      (%eax), %ecx
        movl      %esi, 8(%edx)
        movl      %edi, 12(%edx)
        movl      %ecx, (%edx)
        movl      4(%eax), %ebx
        movl      16(%eax), %ecx
        movl      20(%eax), %eax
        movl      %ebx, 4(%edx)
        movl      %ecx, 16(%edx)
        movl      %eax, 20(%edx)
        jmp       ..B4.146
        .align    16,0x90
	.type	__dpml_evaluate_rational__,@function
	.size	__dpml_evaluate_rational__,.-__dpml_evaluate_rational__
	.data
# -- End  __dpml_evaluate_rational__
	.text
# -- Begin  __eval_neg_poly
	.text
       .align    16,0x90
__eval_neg_poly:
# parameter 1: %eax
# parameter 2: 148 + %esp
# parameter 3: %edx
# parameter 4: 160 + %esp
# parameter 5: %ecx
..B5.1:
..L12:

        movl      4(%esp), %eax
        movl      16(%esp), %edx
        movl      28(%esp), %ecx
__eval_neg_poly.:
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $124, %esp
        movl      %edx, 88(%esp)
        movl      8(%eax), %edx
        movl      %edx, 72(%esp)
        movl      12(%eax), %edx
        movl      %edx, 80(%esp)
        movl      16(%eax), %edx
        movl      %edx, 76(%esp)
        movl      20(%eax), %edx
        movl      4(%eax), %eax
        movl      164(%esp), %esi
        movl      %edx, 92(%esp)
        cltd      
        movl      160(%esp), %edi
        movl      %esi, 96(%esp)
        xorl      %esi, %esi
        movl      %edi, 84(%esp)
        movl      %edx, %edi
        xorl      %edx, %edx
        subl      %eax, %edx
        movl      148(%esp), %ebx
        sbbl      %edi, %esi
        movl      %ecx, (%esp)
        movl      %ebx, %ecx
        movl      %esi, 4(%esp)
        movl      $128, %esi
        subl      %esi, %ecx
        movl      $0, %esi
        movl      152(%esp), %ebp
        movl      %ebp, %ecx
        sbbl      %esi, %ecx
        jge       ..B5.4
..B5.2:
        movl      $1, %esi
..B5.4:
        testl     %esi, %esi
        jne       ..B5.8
..B5.5:
        movl      %edx, 16(%esp)
        movl      %eax, 64(%esp)
        movl      %edi, 68(%esp)
        movl      96(%esp), %edx
        movl      84(%esp), %esi
        movl      88(%esp), %eax
..B5.6:
        addl      64(%esp), %ebx
        movl      %ebx, %ecx
        adcl      68(%esp), %ebp
        movl      $128, %edi
        addl      $-1, %esi
        adcl      $-1, %edx
        addl      $16, %eax
        subl      %edi, %ecx
        movl      $0, %edi
        movl      %ebp, %ecx
        sbbl      %edi, %ecx
        jge       ..B5.6
..B5.7:
        movl      %edx, 96(%esp)
        movl      %eax, 88(%esp)
        movl      %esi, 84(%esp)
        movl      16(%esp), %edx
        movl      68(%esp), %edi
        movl      64(%esp), %eax
..B5.8:
        movl      $64, %esi
        movl      %ebx, %ecx
        subl      %esi, %ecx
        movl      $0, %esi
        movl      %ebp, %ecx
        sbbl      %esi, %ecx
        jge       ..B5.11
..B5.9:
        movl      $1, %esi
..B5.11:
        testl     %esi, %esi
        je        ..B5.13
..B5.12:
        xorl      %esi, %esi
        movl      %esi, 100(%esp)
        movl      %esi, 104(%esp)
        jmp       ..B5.28
..B5.13:
        movl      %edi, 68(%esp)
        movl      %edx, 16(%esp)
        movl      %eax, 64(%esp)
        movl      96(%esp), %esi
        movl      88(%esp), %edi
..B5.14:
        movl      8(%edi), %edx
        lea       -64(%ebx), %ecx
        movl      12(%edi), %eax
        cmpl      $31, %ecx
        jbe       ..B5.16
..B5.15:
        movl      %eax, %edx
        movl      $0, 104(%esp)
        shrl      %cl, %edx
        jmp       ..B5.17
..B5.16:
        shrdl     %cl, %eax, %edx
        shrl      %cl, %eax
        movl      %eax, 104(%esp)
..B5.17:
        addl      64(%esp), %ebx
        movl      %ebx, %ecx
        adcl      68(%esp), %ebp
        movl      $64, %eax
        addl      $16, %edi
        addl      $-1, 84(%esp)
        adcl      $-1, %esi
        subl      %eax, %ecx
        movl      $0, %eax
        movl      %ebp, %ecx
        sbbl      %eax, %ecx
        jl        ..B5.102
..B5.18:
        movl      %edx, %eax
        orl       104(%esp), %eax
        je        ..B5.14
..B5.19:
        movl      %edx, 100(%esp)
        movl      %edi, 88(%esp)
        movl      %esi, 96(%esp)
        movl      16(%esp), %edx
        movl      104(%esp), %esi
        movl      100(%esp), %edi
        movl      %ebp, 36(%esp)
..B5.20:
        movl      72(%esp), %eax
        mull      %edi
        movl      %edi, %eax
        movl      %edx, %ebp
        mull      80(%esp)
        addl      %eax, %ebp
        movl      %edx, %ecx
        movl      72(%esp), %eax
        adcl      $0, %ecx
        mull      %esi
        addl      %ebp, %eax
        movl      %edx, %edi
        adcl      %ecx, %edi
        subl      %ebp, %eax
        movl      %edi, %edx
        sbbl      %ecx, %edx
        jae       ..B5.22
..B5.21:
        movl      $1, %ebp
        jmp       ..B5.23
..B5.22:
        xorl      %ebp, %ebp
..B5.23:
        movl      80(%esp), %eax
        lea       -64(%ebx), %ecx
        mull      %esi
        movl      88(%esp), %esi
        addl      %edx, %ebp
        addl      %edi, %eax
        adcl      $0, %ebp
        movl      8(%esi), %edi
        cmpl      $31, %ecx
        movl      12(%esi), %edx
        jbe       ..B5.25
..B5.24:
        movl      %edx, %edi
        xorl      %esi, %esi
        shrl      %cl, %edi
        jmp       ..B5.26
..B5.25:
        movl      %edx, %esi
        shrdl     %cl, %edx, %edi
        shrl      %cl, %esi
..B5.26:
        addl      64(%esp), %ebx
        movl      36(%esp), %edx
        adcl      68(%esp), %edx
        addl      $-1, 84(%esp)
        movl      %edx, 36(%esp)
        adcl      $-1, 96(%esp)
        subl      %eax, %edi
        movl      %ebx, %eax
        sbbl      %ebp, %esi
        movl      $64, %ebp
        addl      $16, 88(%esp)
        subl      %ebp, %eax
        movl      $0, %ebp
        sbbl      %ebp, %edx
        jge       ..B5.20
..B5.27:
        movl      %edi, 100(%esp)
        movl      %esi, 104(%esp)
        movl      36(%esp), %ebp
        movl      16(%esp), %edx
        movl      68(%esp), %edi
        movl      64(%esp), %eax
..B5.28:
        xorl      %ecx, %ecx
        xorl      %esi, %esi
        subl      %ebx, %esi
        movl      %esi, 8(%esp)
        movl      %ebx, %esi
        sbbl      %ebp, %ecx
        movl      %ecx, 12(%esp)
        orl       %ebp, %esi
        jne       ..B5.30
..B5.29:
        xorl      %edx, %edx
        xorl      %esi, %esi
        jmp       ..B5.78
..B5.30:
        movl      %ebp, 36(%esp)
        movl      %edx, 16(%esp)
        movl      %edi, 68(%esp)
        movl      %eax, 64(%esp)
..B5.31:
        movl      72(%esp), %esi
        movl      %esi, %eax
        movl      100(%esp), %ebp
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      80(%esp)
        addl      %eax, %edi
        movl      %esi, %eax
        movl      %edx, %ebp
        adcl      $0, %ebp
        mull      104(%esp)
        addl      %edi, %eax
        movl      %edx, %esi
        adcl      %ebp, %esi
        subl      %edi, %eax
        movl      %esi, %eax
        sbbl      %ebp, %eax
        jae       ..B5.33
..B5.32:
        movl      $1, %ebp
        jmp       ..B5.34
..B5.33:
        xorl      %ebp, %ebp
..B5.34:
        movl      104(%esp), %eax
        mull      80(%esp)
        movl      88(%esp), %edi
        addl      %edx, %ebp
        addl      %esi, %eax
        movl      %eax, 32(%esp)
        movl      12(%edi), %esi
        adcl      $0, %ebp
        movl      %esi, 20(%esp)
        cmpl      $31, %ebx
        movl      8(%edi), %edx
        movl      (%edi), %eax
        movl      4(%edi), %esi
        jbe       ..B5.36
..B5.35:
        movl      %ebx, %ecx
        movl      %esi, %eax
        shrl      %cl, %eax
        xorl      %edi, %edi
        jmp       ..B5.37
..B5.36:
        movl      %ebx, %ecx
        movl      %esi, %edi
        shrdl     %cl, %esi, %eax
        shrl      %cl, %edi
..B5.37:
        movl      8(%esp), %esi
        lea       64(%esi), %ecx
        cmpl      $31, %ecx
        jbe       ..B5.39
..B5.38:
        movl      %edx, %esi
        shll      %cl, %esi
        movl      $0, 24(%esp)
        movl      %esi, 28(%esp)
        jmp       ..B5.40
..B5.39:
        movl      20(%esp), %esi
        shldl     %cl, %edx, %esi
        movl      %esi, 28(%esp)
        movl      %edx, %esi
        shll      %cl, %esi
        movl      %esi, 24(%esp)
..B5.40:
        orl       24(%esp), %eax
        orl       28(%esp), %edi
        cmpl      $31, %ebx
        jbe       ..B5.42
..B5.41:
        movl      %ebx, %ecx
        xorl      %esi, %esi
        movl      20(%esp), %edx
        shrl      %cl, %edx
        jmp       ..B5.43
..B5.42:
        movl      %ebx, %ecx
        movl      20(%esp), %esi
        shrdl     %cl, %esi, %edx
        shrl      %cl, %esi
..B5.43:
        movl      8(%esp), %ecx
        addl      16(%esp), %ecx
        movl      %ecx, 8(%esp)
        movl      12(%esp), %ecx
        adcl      4(%esp), %ecx
        addl      64(%esp), %ebx
        movl      %ecx, 12(%esp)
        movl      36(%esp), %ecx
        adcl      68(%esp), %ecx
        addl      $-1, 84(%esp)
        movl      %ecx, 36(%esp)
        movl      %eax, %ecx
        adcl      $-1, 96(%esp)
        subl      32(%esp), %ecx
        movl      %ecx, 100(%esp)
        movl      %edi, %ecx
        sbbl      %ebp, %ecx
        addl      $16, 88(%esp)
        movl      100(%esp), %ebp
        subl      %eax, %ebp
        movl      %ecx, 104(%esp)
        movl      %ecx, %eax
        sbbl      %edi, %eax
        jb        ..B5.45
..B5.105:
        orl       %eax, %ebp
        je        ..B5.45
..B5.44:
        movl      $1, %eax
        jmp       ..B5.46
..B5.45:
        xorl      %eax, %eax
..B5.46:
        subl      %eax, %edx
        movl      %ebx, %eax
        sbbl      $0, %esi
        orl       36(%esp), %eax
        je        ..B5.78
..B5.47:
        movl      %edx, %eax
        orl       %esi, %eax
        je        ..B5.31
..B5.48:
        movl      %esi, 108(%esp)
        movl      %edx, 112(%esp)
        movl      %ebx, 12(%esp)
..B5.49:
        movl      80(%esp), %ecx
        movl      112(%esp), %esi
        movl      108(%esp), %ebx
        movl      72(%esp), %eax
        imull     %esi, %ecx
        imull     %eax, %ebx
        mull      %esi
        movl      88(%esp), %ebp
        addl      %ebx, %ecx
        addl      %ecx, %edx
        movl      %eax, 8(%esp)
        movl      %edx, 4(%esp)
        movl      8(%ebp), %ebx
        movl      12(%ebp), %eax
        movl      (%ebp), %esi
        movl      4(%ebp), %edx
        cmpl      $31, 12(%esp)
        jbe       ..B5.51
..B5.50:
        movl      %edx, %esi
        movl      12(%esp), %ecx
        movl      $0, 52(%esp)
        shrl      %cl, %esi
        jmp       ..B5.52
..B5.51:
        movl      12(%esp), %ecx
        shrdl     %cl, %edx, %esi
        shrl      %cl, %edx
        movl      %edx, 52(%esp)
..B5.52:
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B5.54
..B5.53:
        movl      %ebx, %ebp
        xorl      %edx, %edx
        shll      %cl, %ebp
        jmp       ..B5.55
..B5.54:
        movl      %eax, %ebp
        movl      %ebx, %edx
        shldl     %cl, %ebx, %ebp
        shll      %cl, %edx
..B5.55:
        orl       %ebp, 52(%esp)
        orl       %edx, %esi
        cmpl      $31, 12(%esp)
        jbe       ..B5.57
..B5.56:
        movl      %eax, %ebx
        xorl      %ebp, %ebp
        movl      12(%esp), %ecx
        shrl      %cl, %ebx
        jmp       ..B5.58
..B5.57:
        movl      %eax, %ebp
        movl      12(%esp), %ecx
        shrdl     %cl, %eax, %ebx
        shrl      %cl, %ebp
..B5.58:
        movl      112(%esp), %ecx
        movl      %ecx, %eax
        mull      76(%esp)
        movl      %ecx, %eax
        movl      %edx, %edi
        mull      92(%esp)
        addl      %eax, %edi
        movl      %edx, %ecx
        movl      76(%esp), %eax
        adcl      $0, %ecx
        mull      108(%esp)
        addl      %edi, %eax
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 32(%esp)
        sbbl      %ecx, %edx
        jae       ..B5.60
..B5.59:
        movl      $1, %edi
        jmp       ..B5.61
..B5.60:
        xorl      %edi, %edi
..B5.61:
        movl      92(%esp), %eax
        mull      108(%esp)
        addl      %edx, %edi
        addl      32(%esp), %eax
        movl      %eax, 48(%esp)
        movl      %esi, %eax
        adcl      $0, %edi
        subl      8(%esp), %eax
        movl      52(%esp), %ecx
        movl      %ecx, %edx
        sbbl      4(%esp), %edx
        movl      %eax, 20(%esp)
        subl      %esi, %eax
        movl      %edx, 16(%esp)
        sbbl      %ecx, %edx
        jb        ..B5.63
..B5.106:
        orl       %edx, %eax
        je        ..B5.63
..B5.62:
        movl      $1, %eax
        jmp       ..B5.64
..B5.63:
        xorl      %eax, %eax
..B5.64:
        movl      100(%esp), %ecx
        subl      %eax, %ebx
        movl      72(%esp), %eax
        sbbl      $0, %ebp
        mull      %ecx
        movl      %ecx, %eax
        movl      %edx, %esi
        mull      80(%esp)
        addl      $-1, 84(%esp)
        movl      %edx, %ecx
        adcl      $-1, 96(%esp)
        addl      %eax, %esi
        movl      72(%esp), %eax
        adcl      $0, %ecx
        mull      104(%esp)
        addl      %esi, %eax
        adcl      %ecx, %edx
        subl      %esi, %eax
        movl      %edx, 40(%esp)
        sbbl      %ecx, %edx
        jae       ..B5.66
..B5.65:
        movl      $1, %ecx
        jmp       ..B5.67
..B5.66:
        xorl      %ecx, %ecx
..B5.67:
        movl      104(%esp), %eax
        mull      80(%esp)
        addl      %edx, %ecx
        addl      40(%esp), %eax
        movl      %eax, 56(%esp)
        movl      20(%esp), %eax
        movl      %eax, %esi
        adcl      $0, %ecx
        subl      48(%esp), %esi
        movl      %esi, 24(%esp)
        movl      16(%esp), %esi
        movl      %esi, %edx
        sbbl      %edi, %edx
        movl      24(%esp), %edi
        subl      %eax, %edi
        movl      %edx, 28(%esp)
        sbbl      %esi, %edx
        jb        ..B5.69
..B5.107:
        orl       %edx, %edi
        je        ..B5.69
..B5.68:
        movl      $1, %eax
        jmp       ..B5.70
..B5.69:
        xorl      %eax, %eax
..B5.70:
        subl      %eax, %ebx
        movl      12(%esp), %esi
        sbbl      $0, %ebp
        addl      64(%esp), %esi
        movl      36(%esp), %edx
        adcl      68(%esp), %edx
        movl      %esi, 12(%esp)
        movl      72(%esp), %eax
        movl      112(%esp), %esi
        movl      %edx, 36(%esp)
        mull      %esi
        movl      %esi, %eax
        movl      %edx, %edi
        mull      80(%esp)
        addl      %eax, %edi
        movl      %edx, %esi
        movl      72(%esp), %eax
        adcl      $0, %esi
        mull      108(%esp)
        addl      %edi, %eax
        adcl      %esi, %edx
        subl      %edi, %eax
        movl      %edx, 44(%esp)
        sbbl      %esi, %edx
        jae       ..B5.72
..B5.71:
        movl      $1, %esi
        jmp       ..B5.73
..B5.72:
        xorl      %esi, %esi
..B5.73:
        movl      80(%esp), %eax
        mull      108(%esp)
        addl      %edx, %esi
        addl      44(%esp), %eax
        movl      24(%esp), %edi
        adcl      $0, %esi
        movl      %esi, 60(%esp)
        movl      %edi, %esi
        subl      56(%esp), %esi
        movl      %esi, 100(%esp)
        movl      28(%esp), %esi
        movl      %esi, %edx
        sbbl      %ecx, %edx
        movl      %edx, 104(%esp)
        movl      100(%esp), %edx
        subl      %edi, %edx
        movl      104(%esp), %ecx
        sbbl      %esi, %ecx
        movl      60(%esp), %esi
        jb        ..B5.75
..B5.108:
        orl       %ecx, %edx
        je        ..B5.75
..B5.74:
        movl      $1, %edx
        jmp       ..B5.76
..B5.75:
        xorl      %edx, %edx
..B5.76:
        subl      %edx, %ebx
        sbbl      $0, %ebp
        subl      %eax, %ebx
        movl      12(%esp), %eax
        sbbl      %esi, %ebp
        addl      $16, 88(%esp)
        movl      %ebx, 112(%esp)
        movl      %ebp, 108(%esp)
        orl       36(%esp), %eax
        jne       ..B5.49
..B5.77:
        movl      %ebp, %esi
        movl      %ebx, %edx
..B5.78:
        xorl      %eax, %eax
        movl      96(%esp), %ecx
        subl      %eax, %ecx
        jl        ..B5.100
..B5.79:
        movl      %esi, 108(%esp)
        movl      %edx, 112(%esp)
..B5.80:
        movl      108(%esp), %ebp
        movl      %ebp, %edx
        movl      80(%esp), %ecx
        movl      112(%esp), %edi
        movl      72(%esp), %eax
        imull     %edi, %ecx
        imull     %eax, %edx
        addl      %edx, %ecx
        mull      %edi
        movl      88(%esp), %esi
        addl      %ecx, %edx
        movl      %edx, 20(%esp)
        movl      %eax, 12(%esp)
        movl      %edi, %eax
        movl      8(%esi), %ecx
        movl      4(%esi), %edx
        movl      %ecx, 52(%esp)
        movl      12(%esi), %ecx
        movl      (%esi), %ebx
        movl      76(%esp), %esi
        movl      %edx, 40(%esp)
        mull      %esi
        movl      %edi, %eax
        movl      %ebx, 28(%esp)
        movl      %edx, %ebx
        mull      92(%esp)
        addl      %eax, %ebx
        movl      %esi, %eax
        movl      %edx, %edi
        adcl      $0, %edi
        mull      %ebp
        addl      %ebx, %eax
        movl      %edx, %ebp
        adcl      %edi, %ebp
        subl      %ebx, %eax
        movl      %ebp, %ebx
        sbbl      %edi, %ebx
        jae       ..B5.82
..B5.81:
        movl      $1, %ebx
        jmp       ..B5.83
..B5.82:
        xorl      %ebx, %ebx
..B5.83:
        movl      92(%esp), %eax
        mull      108(%esp)
        addl      %edx, %ebx
        addl      %ebp, %eax
        movl      28(%esp), %edi
        movl      %edi, %esi
        adcl      $0, %ebx
        subl      12(%esp), %esi
        movl      40(%esp), %ebp
        movl      %eax, 44(%esp)
        movl      %ebp, %eax
        sbbl      20(%esp), %eax
        movl      %esi, 8(%esp)
        subl      %edi, %esi
        movl      %eax, 4(%esp)
        sbbl      %ebp, %eax
        jb        ..B5.85
..B5.109:
        orl       %eax, %esi
        je        ..B5.85
..B5.84:
        movl      $1, %eax
        jmp       ..B5.86
..B5.85:
        xorl      %eax, %eax
..B5.86:
        movl      72(%esp), %esi
        subl      %eax, 52(%esp)
        movl      %esi, %eax
        movl      100(%esp), %ebp
        sbbl      $0, %ecx
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      80(%esp)
        addl      $-1, 84(%esp)
        movl      %edx, %ebp
        adcl      $-1, 96(%esp)
        addl      %eax, %edi
        movl      %esi, %eax
        adcl      $0, %ebp
        mull      104(%esp)
        addl      %edi, %eax
        adcl      %ebp, %edx
        subl      %edi, %eax
        movl      %edx, 36(%esp)
        sbbl      %ebp, %edx
        jae       ..B5.88
..B5.87:
        movl      $1, %ebp
        jmp       ..B5.89
..B5.88:
        xorl      %ebp, %ebp
..B5.89:
        movl      104(%esp), %eax
        mull      80(%esp)
        addl      %edx, %ebp
        addl      36(%esp), %eax
        movl      8(%esp), %edi
        movl      %eax, 48(%esp)
        movl      %edi, %eax
        adcl      $0, %ebp
        subl      44(%esp), %eax
        movl      4(%esp), %edx
        movl      %edx, %esi
        sbbl      %ebx, %esi
        movl      %eax, 16(%esp)
        subl      %edi, %eax
        movl      %esi, 24(%esp)
        sbbl      %edx, %esi
        jb        ..B5.91
..B5.110:
        orl       %esi, %eax
        je        ..B5.91
..B5.90:
        movl      $1, %eax
        jmp       ..B5.92
..B5.91:
        xorl      %eax, %eax
..B5.92:
        movl      72(%esp), %esi
        subl      %eax, 52(%esp)
        movl      %esi, %eax
        movl      112(%esp), %ebx
        sbbl      $0, %ecx
        mull      %ebx
        movl      %ebx, %eax
        movl      %edx, %edi
        mull      80(%esp)
        addl      %eax, %edi
        movl      %esi, %eax
        movl      %edx, %ebx
        adcl      $0, %ebx
        mull      108(%esp)
        addl      %edi, %eax
        adcl      %ebx, %edx
        subl      %edi, %eax
        movl      %edx, 32(%esp)
        sbbl      %ebx, %edx
        jae       ..B5.94
..B5.93:
        movl      $1, %ebx
        jmp       ..B5.95
..B5.94:
        xorl      %ebx, %ebx
..B5.95:
        movl      80(%esp), %eax
        mull      108(%esp)
        addl      %edx, %ebx
        addl      32(%esp), %eax
        movl      16(%esp), %edi
        movl      %edi, %esi
        adcl      $0, %ebx
        subl      48(%esp), %esi
        movl      %esi, 100(%esp)
        movl      24(%esp), %esi
        movl      %esi, %edx
        sbbl      %ebp, %edx
        movl      100(%esp), %ebp
        subl      %edi, %ebp
        movl      %edx, 104(%esp)
        sbbl      %esi, %edx
        jb        ..B5.97
..B5.111:
        orl       %edx, %ebp
        je        ..B5.97
..B5.96:
        movl      $1, %edx
        jmp       ..B5.98
..B5.97:
        xorl      %edx, %edx
..B5.98:
        movl      52(%esp), %ebp
        subl      %edx, %ebp
        movl      96(%esp), %edx
        sbbl      $0, %ecx
        subl      %eax, %ebp
        sbbl      %ebx, %ecx
        xorl      %eax, %eax
        addl      $16, 88(%esp)
        movl      %ebp, 112(%esp)
        subl      %eax, %edx
        movl      %ecx, 108(%esp)
        jge       ..B5.80
..B5.99:
        movl      %ecx, %esi
        movl      %ebp, %edx
..B5.100:
        movl      (%esp), %ebx
        movl      100(%esp), %eax
        movl      104(%esp), %ecx
        movl      %edx, 8(%ebx)
        xorl      %edx, %edx
        movl      %eax, 16(%ebx)
        movl      %ecx, 20(%ebx)
        movl      %esi, 12(%ebx)
        movl      %edx, 4(%ebx)
        movl      %edx, (%ebx)
        addl      $124, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B5.102:
        movl      %edx, 100(%esp)
        movl      %edi, 88(%esp)
        movl      %esi, 96(%esp)
        movl      16(%esp), %edx
        movl      68(%esp), %edi
        movl      64(%esp), %eax
        jmp       ..B5.28
        .align    16,0x90
	.type	__eval_neg_poly,@function
	.size	__eval_neg_poly,.-__eval_neg_poly
	.data
# -- End  __eval_neg_poly
	.text
# -- Begin  __eval_pos_poly
	.text
       .align    16,0x90
__eval_pos_poly:
# parameter 1: %eax
# parameter 2: 180 + %esp
# parameter 3: %edx
# parameter 4: 192 + %esp
# parameter 5: %ecx
..B6.1:
..L13:

        movl      4(%esp), %eax
        movl      16(%esp), %edx
        movl      28(%esp), %ecx
__eval_pos_poly.:
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $156, %esp
        movl      192(%esp), %esi
        movl      %esi, 84(%esp)
        movl      8(%eax), %esi
        movl      %esi, 68(%esp)
        movl      12(%eax), %esi
        movl      %esi, 72(%esp)
        movl      16(%eax), %esi
        movl      %esi, 76(%esp)
        movl      20(%eax), %esi
        movl      %esi, 80(%esp)
        xorl      %esi, %esi
        movl      %ecx, 48(%esp)
        xorl      %ecx, %ecx
        movl      196(%esp), %edi
        movl      4(%eax), %eax
        subl      %eax, %ecx
        movl      %edx, 64(%esp)
        cltd      
        movl      %edi, 140(%esp)
        movl      %esi, %edi
        movl      180(%esp), %ebx
        sbbl      %edx, %edi
        movl      %eax, 148(%esp)
        movl      $128, %eax
        movl      %edx, 144(%esp)
        movl      %ebx, %edx
        subl      %eax, %edx
        movl      184(%esp), %ebp
        movl      %ebp, %edx
        sbbl      %esi, %edx
        jge       ..B6.3
..B6.2:
        movl      $1, %edx
        jmp       ..B6.4
..B6.3:
        xorl      %edx, %edx
..B6.4:
        testl     %edx, %edx
        jne       ..B6.8
..B6.5:
        movl      %edi, 8(%esp)
        movl      %ecx, 12(%esp)
        movl      140(%esp), %esi
        movl      84(%esp), %eax
        movl      64(%esp), %edx
..B6.6:
        addl      148(%esp), %ebx
        movl      %ebx, %ecx
        adcl      144(%esp), %ebp
        movl      $128, %edi
        addl      $-1, %eax
        adcl      $-1, %esi
        addl      $16, %edx
        subl      %edi, %ecx
        movl      $0, %edi
        movl      %ebp, %ecx
        sbbl      %edi, %ecx
        jge       ..B6.6
..B6.7:
        movl      %esi, 140(%esp)
        movl      %eax, 84(%esp)
        movl      %edx, 64(%esp)
        movl      8(%esp), %edi
        movl      12(%esp), %ecx
..B6.8:
        movl      $64, %eax
        movl      %ebx, %edx
        xorl      %esi, %esi
        subl      %eax, %edx
        movl      %ebp, %edx
        sbbl      %esi, %edx
        jge       ..B6.10
..B6.9:
        movl      $1, %edx
        jmp       ..B6.11
..B6.10:
        xorl      %edx, %edx
..B6.11:
        testl     %edx, %edx
        je        ..B6.13
..B6.12:
        xorl      %edx, %edx
        xorl      %esi, %esi
        movl      %esi, 88(%esp)
        movl      %esi, 96(%esp)
        jmp       ..B6.31
..B6.13:
        movl      %edi, 8(%esp)
        movl      %ecx, 12(%esp)
        movl      140(%esp), %edx
        movl      64(%esp), %esi
..B6.14:
        movl      8(%esi), %edi
        lea       -64(%ebx), %ecx
        movl      12(%esi), %eax
        cmpl      $31, %ecx
        jbe       ..B6.16
..B6.15:
        movl      %eax, %edi
        movl      $0, 96(%esp)
        shrl      %cl, %edi
        jmp       ..B6.17
..B6.16:
        shrdl     %cl, %eax, %edi
        shrl      %cl, %eax
        movl      %eax, 96(%esp)
..B6.17:
        addl      148(%esp), %ebx
        movl      %ebx, %ecx
        adcl      144(%esp), %ebp
        movl      $64, %eax
        addl      $16, %esi
        addl      $-1, 84(%esp)
        adcl      $-1, %edx
        subl      %eax, %ecx
        movl      $0, %eax
        movl      %ebp, %ecx
        sbbl      %eax, %ecx
        jl        ..B6.126
..B6.18:
        movl      %edi, %eax
        orl       96(%esp), %eax
        je        ..B6.14
..B6.19:
        movl      %edi, 88(%esp)
        movl      %ebx, 60(%esp)
        movl      %ebp, 56(%esp)
        movl      %edx, 140(%esp)
        movl      %esi, 64(%esp)
        movl      96(%esp), %ebx
        movl      88(%esp), %ebp
..B6.20:
        movl      68(%esp), %esi
        movl      %esi, %eax
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %ecx
        mull      72(%esp)
        addl      %eax, %ecx
        movl      %esi, %eax
        movl      %edx, %edi
        adcl      $0, %edi
        mull      %ebx
        addl      %ecx, %eax
        movl      %edx, %ebp
        adcl      %edi, %ebp
        subl      %ecx, %eax
        movl      %ebp, %edx
        sbbl      %edi, %edx
        jae       ..B6.22
..B6.21:
        movl      $1, %esi
        jmp       ..B6.23
..B6.22:
        xorl      %esi, %esi
..B6.23:
        movl      72(%esp), %eax
        mull      %ebx
        movl      64(%esp), %ecx
        addl      %edx, %esi
        movl      60(%esp), %ebx
        addl      %ebp, %eax
        movl      8(%ecx), %ebp
        adcl      $0, %esi
        movl      12(%ecx), %edx
        lea       -64(%ebx), %ecx
        cmpl      $31, %ecx
        jbe       ..B6.25
..B6.24:
        movl      %edx, %ebp
        xorl      %ebx, %ebx
        shrl      %cl, %ebp
        jmp       ..B6.26
..B6.25:
        movl      %edx, %ebx
        shrdl     %cl, %edx, %ebp
        shrl      %cl, %ebx
..B6.26:
        movl      60(%esp), %ecx
        movl      $64, %edi
        addl      148(%esp), %ecx
        movl      56(%esp), %edx
        adcl      144(%esp), %edx
        addl      $-1, 84(%esp)
        movl      %ecx, 60(%esp)
        adcl      $-1, 140(%esp)
        addl      %eax, %ebp
        movl      %edx, 56(%esp)
        adcl      %esi, %ebx
        addl      $16, 64(%esp)
        subl      %edi, %ecx
        movl      $0, %edi
        sbbl      %edi, %edx
        jge       ..B6.20
..B6.27:
        movl      %ebp, 88(%esp)
        movl      %ebp, %edx
        subl      %eax, %edx
        movl      %ebx, 96(%esp)
        movl      %ebx, %eax
        movl      60(%esp), %ebx
        sbbl      %esi, %eax
        movl      56(%esp), %ebp
        movl      8(%esp), %edi
        movl      12(%esp), %ecx
        jae       ..B6.29
..B6.28:
        movl      $1, %edx
        jmp       ..B6.30
..B6.29:
        xorl      %edx, %edx
..B6.30:
        xorl      %esi, %esi
..B6.31:
        movl      %esi, 92(%esp)
        xorl      %esi, %esi
        xorl      %eax, %eax
        subl      %ebx, %eax
        movl      %eax, 4(%esp)
        movl      %ebx, %eax
        sbbl      %ebp, %esi
        movl      %esi, (%esp)
        orl       %ebp, %eax
        movl      92(%esp), %esi
        je        ..B6.54
..B6.32:
        xorl      %eax, %eax
        orl       %edx, %eax
        jne       ..B6.52
..B6.33:
        movl      %edi, 8(%esp)
        movl      %ebp, 56(%esp)
        movl      %ecx, 12(%esp)
        movl      96(%esp), %esi
        movl      88(%esp), %edi
..B6.34:
        movl      68(%esp), %eax
        mull      %edi
        movl      %edi, %eax
        movl      %edx, %ebp
        mull      72(%esp)
        addl      %eax, %ebp
        movl      %edx, %ecx
        movl      68(%esp), %eax
        adcl      $0, %ecx
        mull      %esi
        addl      %ebp, %eax
        movl      %edx, %edi
        adcl      %ecx, %edi
        subl      %ebp, %eax
        movl      %edi, %edx
        sbbl      %ecx, %edx
        jae       ..B6.36
..B6.35:
        movl      $1, %ebp
        jmp       ..B6.37
..B6.36:
        xorl      %ebp, %ebp
..B6.37:
        movl      72(%esp), %eax
        mull      %esi
        movl      64(%esp), %esi
        addl      %edx, %ebp
        addl      %edi, %eax
        movl      %eax, 28(%esp)
        movl      12(%esi), %edi
        adcl      $0, %ebp
        movl      %edi, 16(%esp)
        cmpl      $31, %ebx
        movl      8(%esi), %edx
        movl      (%esi), %edi
        movl      4(%esi), %eax
        jbe       ..B6.39
..B6.38:
        movl      %ebx, %ecx
        movl      %eax, %edi
        shrl      %cl, %edi
        xorl      %esi, %esi
        jmp       ..B6.40
..B6.39:
        movl      %ebx, %ecx
        movl      %eax, %esi
        shrdl     %cl, %eax, %edi
        shrl      %cl, %esi
..B6.40:
        movl      4(%esp), %eax
        lea       64(%eax), %ecx
        cmpl      $31, %ecx
        jbe       ..B6.42
..B6.41:
        movl      %edx, %eax
        shll      %cl, %eax
        movl      $0, 20(%esp)
        movl      %eax, 24(%esp)
        jmp       ..B6.43
..B6.42:
        movl      16(%esp), %eax
        shldl     %cl, %edx, %eax
        movl      %eax, 24(%esp)
        movl      %edx, %eax
        shll      %cl, %eax
        movl      %eax, 20(%esp)
..B6.43:
        orl       20(%esp), %edi
        orl       24(%esp), %esi
        cmpl      $31, %ebx
        jbe       ..B6.45
..B6.44:
        movl      %ebx, %ecx
        movl      16(%esp), %edx
        movl      $0, 92(%esp)
        shrl      %cl, %edx
        jmp       ..B6.46
..B6.45:
        movl      %ebx, %ecx
        movl      16(%esp), %eax
        shrdl     %cl, %eax, %edx
        shrl      %cl, %eax
        movl      %eax, 92(%esp)
..B6.46:
        movl      4(%esp), %eax
        addl      12(%esp), %eax
        movl      (%esp), %ecx
        adcl      8(%esp), %ecx
        addl      148(%esp), %ebx
        movl      %eax, 4(%esp)
        movl      56(%esp), %eax
        adcl      144(%esp), %eax
        addl      $-1, 84(%esp)
        movl      %ecx, (%esp)
        movl      28(%esp), %ecx
        adcl      $-1, 140(%esp)
        addl      %ecx, %edi
        movl      %eax, 56(%esp)
        movl      %edi, %eax
        adcl      %ebp, %esi
        addl      $16, 64(%esp)
        subl      %ecx, %eax
        movl      %esi, %ecx
        sbbl      %ebp, %ecx
        jae       ..B6.48
..B6.47:
        movl      $1, %ebp
        jmp       ..B6.49
..B6.48:
        xorl      %ebp, %ebp
..B6.49:
        addl      %ebp, %edx
        movl      %ebx, %ebp
        adcl      $0, 92(%esp)
        orl       56(%esp), %ebp
        je        ..B6.123
..B6.50:
        movl      %edx, %ebp
        orl       92(%esp), %ebp
        je        ..B6.34
..B6.51:
        movl      %esi, 96(%esp)
        movl      %edi, 88(%esp)
        movl      56(%esp), %ebp
        movl      92(%esp), %esi
..B6.52:
        xorl      %eax, %eax
        movl      140(%esp), %edi
        subl      %eax, %edi
        jge       ..B6.124
..B6.53:
        movl      48(%esp), %ebp
        movl      88(%esp), %ecx
        movl      96(%esp), %ebx
        movl      %ecx, 16(%ebp)
        movl      %ebx, 20(%ebp)
        movl      %edx, 8(%ebp)
        movl      %esi, 12(%ebp)
        movl      %eax, 4(%ebp)
        movl      $0, (%ebp)
        addl      $156, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B6.54:
        xorl      %eax, %eax
        movl      %esi, 92(%esp)
        movl      %eax, 52(%esp)
        movl      %edx, 112(%esp)
..B6.55:
        xorl      %eax, %eax
        movl      140(%esp), %edx
        subl      %eax, %edx
        jl        ..B6.125
..B6.56:
        movl      92(%esp), %esi
        movl      %esi, %ebp
        movl      112(%esp), %ecx
        movl      68(%esp), %eax
        imull     %eax, %ebp
        mull      %ecx
        movl      72(%esp), %edi
        imull     %ecx, %edi
        addl      %ebp, %edi
        movl      %edx, %ebp
        movl      64(%esp), %edx
        addl      %edi, %ebp
        movl      %eax, 104(%esp)
        movl      %ecx, %eax
        movl      8(%edx), %edi
        movl      12(%edx), %ebx
        movl      %edi, 100(%esp)
        movl      %ebx, 108(%esp)
        movl      (%edx), %edi
        movl      4(%edx), %ebx
        mull      76(%esp)
        movl      %ecx, %eax
        movl      %ebx, 136(%esp)
        movl      %edx, %ebx
        mull      80(%esp)
        addl      %eax, %ebx
        movl      %edx, %ecx
        movl      76(%esp), %eax
        adcl      $0, %ecx
        mull      %esi
        addl      %ebx, %eax
        adcl      %ecx, %edx
        subl      %ebx, %eax
        movl      %edx, 128(%esp)
        movl      136(%esp), %ebx
        sbbl      %ecx, %edx
        jae       ..B6.58
..B6.57:
        movl      $1, %ecx
        jmp       ..B6.59
..B6.58:
        xorl      %ecx, %ecx
..B6.59:
        movl      80(%esp), %eax
        mull      92(%esp)
        addl      %edx, %ecx
        addl      128(%esp), %eax
        movl      104(%esp), %esi
        adcl      $0, %ecx
        addl      %esi, %edi
        movl      %eax, 132(%esp)
        movl      %edi, %eax
        adcl      %ebp, %ebx
        subl      %esi, %eax
        movl      %ebx, %edx
        sbbl      %ebp, %edx
        jae       ..B6.61
..B6.60:
        movl      $1, 116(%esp)
        jmp       ..B6.62
..B6.61:
        movl      $0, 116(%esp)
..B6.62:
        movl      68(%esp), %eax
        movl      88(%esp), %ebp
        mull      %ebp
        movl      %ebp, %eax
        movl      %edx, %esi
        mull      72(%esp)
        addl      $-1, 84(%esp)
        movl      %edx, %ebp
        adcl      $-1, 140(%esp)
        addl      %eax, %esi
        movl      68(%esp), %eax
        adcl      $0, %ebp
        mull      96(%esp)
        addl      %esi, %eax
        adcl      %ebp, %edx
        subl      %esi, %eax
        movl      %edx, 124(%esp)
        sbbl      %ebp, %edx
        jae       ..B6.64
..B6.63:
        movl      $1, %ebp
        jmp       ..B6.65
..B6.64:
        xorl      %ebp, %ebp
..B6.65:
        movl      96(%esp), %eax
        mull      72(%esp)
        movl      %eax, %esi
        addl      %edx, %ebp
        addl      124(%esp), %esi
        movl      132(%esp), %eax
        adcl      $0, %ebp
        addl      %eax, %edi
        movl      %edi, %edx
        adcl      %ecx, %ebx
        subl      %eax, %edx
        movl      %ebx, %eax
        sbbl      %ecx, %eax
        jae       ..B6.67
..B6.66:
        movl      $1, %eax
        jmp       ..B6.68
..B6.67:
        xorl      %eax, %eax
..B6.68:
        movl      112(%esp), %ecx
        addl      %eax, 116(%esp)
        movl      %ecx, %eax
        mull      68(%esp)
        movl      %ecx, %eax
        movl      %ebx, 136(%esp)
        movl      %edx, %ebx
        mull      72(%esp)
        addl      %eax, %ebx
        movl      %edx, %ecx
        movl      68(%esp), %eax
        adcl      $0, %ecx
        mull      92(%esp)
        addl      %ebx, %eax
        adcl      %ecx, %edx
        subl      %ebx, %eax
        movl      %edx, 120(%esp)
        movl      136(%esp), %ebx
        sbbl      %ecx, %edx
        jae       ..B6.70
..B6.69:
        movl      $1, %ecx
        jmp       ..B6.71
..B6.70:
        xorl      %ecx, %ecx
..B6.71:
        movl      72(%esp), %eax
        mull      92(%esp)
        addl      %edx, %ecx
        addl      120(%esp), %eax
        adcl      $0, %ecx
        addl      %esi, %edi
        movl      %edi, 88(%esp)
        adcl      %ebp, %ebx
        subl      %esi, %edi
        movl      %ebx, 96(%esp)
        sbbl      %ebp, %ebx
        jae       ..B6.73
..B6.72:
        movl      $1, %edx
        jmp       ..B6.74
..B6.73:
        xorl      %edx, %edx
..B6.74:
        movl      116(%esp), %ebx
        xorl      %esi, %esi
        addl      %edx, %ebx
        movl      100(%esp), %edx
        addl      %ebx, %edx
        movl      108(%esp), %ebp
        adcl      $0, %ebp
        movl      %edx, 100(%esp)
        subl      %ebx, %edx
        movl      %ebp, 108(%esp)
        sbbl      %esi, %ebp
        jae       ..B6.76
..B6.75:
        movl      $1, %edx
        jmp       ..B6.77
..B6.76:
        xorl      %edx, %edx
..B6.77:
        movl      %eax, %ebx
        movl      %ecx, %ebp
        addl      100(%esp), %ebx
        movl      %ebx, 112(%esp)
        adcl      108(%esp), %ebp
        addl      $16, 64(%esp)
        subl      %eax, %ebx
        movl      %ebp, 92(%esp)
        sbbl      %ecx, %ebp
        jae       ..B6.79
..B6.78:
        movl      $1, %eax
        jmp       ..B6.80
..B6.79:
        xorl      %eax, %eax
..B6.80:
        addl      %eax, %edx
        xorl      %eax, %eax
        orl       %eax, %edx
        je        ..B6.55
..B6.81:
        movl      96(%esp), %ebx
        movl      %ebx, %eax
        movl      112(%esp), %esi
        movl      %esi, %ecx
        movl      92(%esp), %edi
        movl      %edi, %ebp
        movl      88(%esp), %edx
        shll      $31, %eax
        shrl      $1, %edx
        shrl      $1, %ebx
        orl       %eax, %edx
        shll      $31, %ecx
        xorl      %eax, %eax
        shll      $31, %ebp
        orl       %ecx, %ebx
        shrl      $1, %edi
        shrl      $1, %esi
        orl       $-2147483648, %edi
        movl      %edx, 88(%esp)
        orl       %ebp, %esi
        incl      52(%esp)
        movl      140(%esp), %edx
        movl      %ebx, 96(%esp)
        subl      %eax, %edx
        movl      %esi, 112(%esp)
        movl      %edi, 92(%esp)
        jge       ..B6.83
..B6.82:
        movl      %edi, %esi
        movl      52(%esp), %eax
        movl      112(%esp), %edx
        jmp       ..B6.53
..B6.83:
        movl      $1, %ebx
        xorl      %ebp, %ebp
..B6.129:
        movl      %ebx, 60(%esp)
        movl      %ebp, 56(%esp)
        movl      92(%esp), %esi
..B6.84:
        movl      %esi, %ecx
        movl      72(%esp), %ebx
        movl      112(%esp), %ebp
        movl      68(%esp), %eax
        imull     %ebp, %ebx
        imull     %eax, %ecx
        mull      %ebp
        addl      %ecx, %ebx
        addl      %ebx, %edx
        movl      %edx, 8(%esp)
        movl      64(%esp), %edx
        movl      %eax, (%esp)
        cmpl      $31, 60(%esp)
        movl      8(%edx), %edi
        movl      12(%edx), %eax
        movl      (%edx), %ebx
        movl      4(%edx), %edx
        movl      %edi, 24(%esp)
        jbe       ..B6.86
..B6.85:
        movl      %edx, %ebx
        xorl      %ebp, %ebp
        movl      60(%esp), %ecx
        shrl      %cl, %ebx
        jmp       ..B6.87
..B6.86:
        movl      %edx, %ebp
        movl      60(%esp), %ecx
        shrdl     %cl, %edx, %ebx
        shrl      %cl, %ebp
..B6.87:
        negl      %ecx
        addl      $64, %ecx
        cmpl      $31, %ecx
        jbe       ..B6.89
..B6.88:
        xorl      %edx, %edx
        shll      %cl, %edi
        movl      %edi, 44(%esp)
        jmp       ..B6.90
..B6.89:
        movl      %eax, %edi
        movl      24(%esp), %edx
        shldl     %cl, %edx, %edi
        movl      %edi, 44(%esp)
        shll      %cl, %edx
..B6.90:
        orl       44(%esp), %ebp
        orl       %edx, %ebx
        cmpl      $31, 60(%esp)
        jbe       ..B6.92
..B6.91:
        movl      60(%esp), %ecx
        shrl      %cl, %eax
        movl      $0, 4(%esp)
        movl      %eax, 24(%esp)
        jmp       ..B6.93
..B6.92:
        movl      60(%esp), %ecx
        movl      24(%esp), %edx
        shrdl     %cl, %eax, %edx
        shrl      %cl, %eax
        movl      %edx, 24(%esp)
        movl      %eax, 4(%esp)
..B6.93:
        movl      112(%esp), %ecx
        movl      %ecx, %eax
        mull      76(%esp)
        movl      %ecx, %eax
        movl      %edx, %edi
        mull      80(%esp)
        addl      %eax, %edi
        movl      %edx, %ecx
        movl      76(%esp), %eax
        adcl      $0, %ecx
        mull      %esi
        addl      %edi, %eax
        adcl      %ecx, %edx
        subl      %edi, %eax
        movl      %edx, 40(%esp)
        sbbl      %ecx, %edx
        jae       ..B6.95
..B6.94:
        movl      $1, %edi
        jmp       ..B6.96
..B6.95:
        xorl      %edi, %edi
..B6.96:
        movl      80(%esp), %eax
        mull      %esi
        addl      %edx, %edi
        addl      40(%esp), %eax
        movl      %eax, 32(%esp)
        movl      (%esp), %eax
        adcl      $0, %edi
        addl      %eax, %ebx
        movl      %ebx, %edx
        movl      8(%esp), %ecx
        adcl      %ecx, %ebp
        subl      %eax, %edx
        movl      %ebp, %eax
        sbbl      %ecx, %eax
        jae       ..B6.98
..B6.97:
        movl      $1, 12(%esp)
        jmp       ..B6.99
..B6.98:
        movl      $0, 12(%esp)
..B6.99:
        movl      68(%esp), %eax
        movl      88(%esp), %ecx
        mull      %ecx
        movl      %ecx, %eax
        movl      %ebp, 36(%esp)
        movl      %edx, %ebp
        mull      72(%esp)
        addl      $-1, 84(%esp)
        movl      %edx, %ecx
        adcl      $-1, 140(%esp)
        addl      %eax, %ebp
        movl      68(%esp), %eax
        adcl      $0, %ecx
        mull      96(%esp)
        addl      %ebp, %eax
        adcl      %ecx, %edx
        subl      %ebp, %eax
        movl      %edx, 16(%esp)
        movl      36(%esp), %ebp
        sbbl      %ecx, %edx
        jae       ..B6.101
..B6.100:
        movl      $1, %ecx
        jmp       ..B6.102
..B6.101:
        xorl      %ecx, %ecx
..B6.102:
        movl      96(%esp), %eax
        mull      72(%esp)
        addl      %edx, %ecx
        addl      16(%esp), %eax
        movl      %eax, 28(%esp)
        movl      32(%esp), %eax
        adcl      $0, %ecx
        addl      %eax, %ebx
        movl      %ebx, %edx
        adcl      %edi, %ebp
        subl      %eax, %edx
        movl      %ebp, %eax
        sbbl      %edi, %eax
        jae       ..B6.104
..B6.103:
        movl      $1, %eax
        jmp       ..B6.105
..B6.104:
        xorl      %eax, %eax
..B6.105:
        movl      %ebp, 36(%esp)
        movl      60(%esp), %ebp
        addl      148(%esp), %ebp
        movl      56(%esp), %edx
        movl      %ebp, 60(%esp)
        adcl      144(%esp), %edx
        movl      112(%esp), %ebp
        addl      %eax, 12(%esp)
        movl      %ebp, %eax
        movl      %edx, 56(%esp)
        mull      68(%esp)
        movl      %ebp, %eax
        movl      %edx, %edi
        mull      72(%esp)
        addl      %eax, %edi
        movl      %edx, %ebp
        movl      68(%esp), %eax
        adcl      $0, %ebp
        mull      %esi
        addl      %edi, %eax
        adcl      %ebp, %edx
        subl      %edi, %eax
        movl      %edx, 20(%esp)
        sbbl      %ebp, %edx
        movl      36(%esp), %ebp
        jae       ..B6.107
..B6.106:
        movl      $1, %edi
        jmp       ..B6.108
..B6.107:
        xorl      %edi, %edi
..B6.108:
        movl      72(%esp), %eax
        mull      %esi
        addl      %edx, %edi
        addl      20(%esp), %eax
        movl      28(%esp), %esi
        adcl      $0, %edi
        addl      %esi, %ebx
        movl      %ebx, 88(%esp)
        adcl      %ecx, %ebp
        subl      %esi, %ebx
        movl      %ebp, 96(%esp)
        sbbl      %ecx, %ebp
        jae       ..B6.110
..B6.109:
        movl      $1, %edx
        jmp       ..B6.111
..B6.110:
        xorl      %edx, %edx
..B6.111:
        movl      12(%esp), %ecx
        xorl      %ebp, %ebp
        addl      %edx, %ecx
        movl      24(%esp), %edx
        addl      %ecx, %edx
        movl      4(%esp), %ebx
        adcl      $0, %ebx
        movl      %edx, 24(%esp)
        subl      %ecx, %edx
        movl      %ebx, 4(%esp)
        sbbl      %ebp, %ebx
        jae       ..B6.113
..B6.112:
        movl      $1, %edx
        jmp       ..B6.114
..B6.113:
        xorl      %edx, %edx
..B6.114:
        movl      %eax, %ecx
        movl      %edi, %esi
        addl      24(%esp), %ecx
        movl      %ecx, 112(%esp)
        adcl      4(%esp), %esi
        addl      $16, 64(%esp)
        subl      %eax, %ecx
        movl      %esi, %eax
        sbbl      %edi, %eax
        jae       ..B6.116
..B6.115:
        movl      $1, %eax
        jmp       ..B6.117
..B6.116:
        xorl      %eax, %eax
..B6.117:
        addl      %eax, %edx
        xorl      %eax, %eax
        orl       %eax, %edx
        je        ..B6.119
..B6.118:
        movl      96(%esp), %ebx
        movl      %ebx, %eax
        movl      112(%esp), %edi
        movl      %edi, %ecx
        movl      %esi, %ebp
        addl      $1, 60(%esp)
        movl      88(%esp), %edx
        adcl      $0, 56(%esp)
        shll      $31, %eax
        shrl      $1, %ebx
        shrl      $1, %edx
        shll      $31, %ecx
        orl       %eax, %edx
        shll      $31, %ebp
        orl       %ecx, %ebx
        shrl      $1, %edi
        shrl      $1, %esi
        orl       %ebp, %edi
        incl      52(%esp)
        orl       $-2147483648, %esi
        movl      %edx, 88(%esp)
        movl      %ebx, 96(%esp)
        movl      %edi, 112(%esp)
..B6.119:
        movl      60(%esp), %eax
        orl       56(%esp), %eax
        je        ..B6.122
..B6.120:
        xorl      %eax, %eax
        movl      140(%esp), %edx
        subl      %eax, %edx
        jge       ..B6.84
..B6.121:
        movl      52(%esp), %eax
        movl      112(%esp), %edx
        jmp       ..B6.53
..B6.122:
        movl      %esi, 92(%esp)
        jmp       ..B6.55
..B6.123:
        movl      %esi, 96(%esp)
        movl      %edi, 88(%esp)
        movl      92(%esp), %esi
        jmp       ..B6.54
..B6.124:
        movl      %esi, 92(%esp)
        movl      %eax, 52(%esp)
        movl      %edx, 112(%esp)
        jmp       ..B6.129
..B6.125:
        movl      92(%esp), %esi
        movl      52(%esp), %eax
        movl      112(%esp), %edx
        jmp       ..B6.53
..B6.126:
        movl      %edx, 140(%esp)
        xorl      %edx, %edx
        movl      %edi, 88(%esp)
        movl      %esi, 64(%esp)
        xorl      %esi, %esi
        movl      8(%esp), %edi
        movl      12(%esp), %ecx
        jmp       ..B6.31
        .align    16,0x90
	.type	__eval_pos_poly,@function
	.size	__eval_pos_poly,.-__eval_pos_poly
	.data
# -- End  __eval_pos_poly
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x7b,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xef,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x47,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xc3,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.4:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfd,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.5:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x3d,0x40,0x00,0x00
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,12
	.align 4
__ux_one__:
	.long	0
	.long	1
	.long	0x00000000,0x80000000
	.long	0x00000000,0x00000000
	.type	__ux_one__,@object
	.size	__ux_one__,24
	.data
	.hidden __dpml_ffs_and_shift__
	.section .note.GNU-stack, ""
# End
