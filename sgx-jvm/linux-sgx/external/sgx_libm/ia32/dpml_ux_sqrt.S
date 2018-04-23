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
	.file "dpml_ux_sqrt.c"
	.text
..TXTST0:
# -- Begin  __dpml_ux_sqrt_evaluation__
	.text
       .align    16,0x90
	.hidden __dpml_ux_sqrt_evaluation__
	.globl __dpml_ux_sqrt_evaluation__
__dpml_ux_sqrt_evaluation__:
# parameter 1: %eax
# parameter 2: 12 + %ebp
# parameter 3: %edx
..B1.1:
..L1:

        movl      4(%esp), %eax
        movl      16(%esp), %edx
	.hidden __dpml_ux_sqrt_evaluation__.
	.globl __dpml_ux_sqrt_evaluation__.
__dpml_ux_sqrt_evaluation__.:
        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $148, %esp
        movl      %edx, %esi
        movl      %eax, 44(%esp)
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 136(%esp)
        addl      $4, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        lea       80(%esp), %eax
        pushl     %eax
        call      fegetenv@PLT
..B1.2:
        movl      44(%esp), %edx
        movl      %ebx, 80(%esp)
        movl      %esi, 40(%esp)
        movl      8(%edx), %edi
        movl      %edi, %ecx
        movl      12(%edx), %eax
        movl      %eax, %ebx
        shll      $21, %ebx
        shrl      $11, %ecx
        orl       %ecx, %ebx
        movl      4(%edx), %ecx
        movl      20(%edx), %esi
        movl      %ecx, %edx
        andl      $1, %edx
        movl      %esi, 36(%esp)
        movl      %eax, %esi
        shrl      $11, %esi
        addl      %edx, %ecx
        addl      $1070596096, %esi
        movl      %esi, 68(%esp)
        lea       8(%edx), %esi
        movl      %esi, 32(%esp)
        negl      %esi
        sarl      $1, %ecx
        movl      %ecx, 72(%esp)
        movl      %esi, %ecx
        negl      %ecx
        movl      %ebx, 64(%esp)
        addl      $31, %ecx
        movl      %edi, %ebx
        shrl      %cl, %ebx
        movl      %esi, %ecx
        movl      %eax, 76(%esp)
        shrl      $1, %ebx
        shll      %cl, %eax
        shll      %cl, %edi
        orl       %ebx, %eax
        movl      80(%esp), %ebx
        movl      40(%esp), %esi
..B1.3:
        movl      32(%esp), %ecx
        shrl      %cl, 36(%esp)
..B1.5:
        fldl      64(%esp)
        movl      %eax, %ecx
        orl       36(%esp), %edi
        shll      $21, %ecx
        shrl      $11, %edi
        shrl      $11, %eax
        orl       %edi, %ecx
        movl      %ecx, 48(%esp)
        movl      %eax, 52(%esp)
        fildll    48(%esp)
        fstpl     56(%esp)
        fldl      56(%esp)
..B1.6:
        movl      32(%esp), %ecx
        movl      76(%esp), %eax
        shrl      %cl, %eax
..B1.8:
        fmull     136+__sqrt_x_table@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        movl      76(%esp), %ecx
        shrl      $24, %ecx
        shll      $7, %edx
        xorl      %edx, %ecx
        shll      $4, %ecx
        movl      __sqrt_t_table@GOT(%ebx), %edx
        movl      %eax, 48(%esp)
        fildl     48(%esp)
        fstpl     56(%esp)
        fldl      56(%esp)
        fmull     128+__sqrt_x_table@GOTOFF(%ebx)
        fstpl     56(%esp)
        fldl      56(%esp)
        fxch      %st(2)
        fstpl     56(%esp)
        fldl      56(%esp)
        fld       %st(0)
        movl      %ebx, 80(%esp)
        fadd      %st(3), %st
        fxch      %st(2)
        fmuls     (%edx,%ecx)
        flds      4(%edx,%ecx)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddl     8(%edx,%ecx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpl     56(%esp)
        fldl      56(%esp)
        fmull     96+__sqrt_x_table@GOTOFF(%ebx)
        fstpl     56(%esp)
        fldl      56(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fstps     48(%esp)
        flds      48(%esp)
        fxch      %st(2)
        fstps     48(%esp)
        flds      48(%esp)
        fmul      %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fld       %st(3)
        fmulp     %st, %st(2)
        fldl      152+__sqrt_x_table@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        fmulp     %st, %st(3)
        fxch      %st(1)
        fsubrl    104+__sqrt_x_table@GOTOFF(%ebx)
        fsubp     %st, %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fldl      112+__sqrt_x_table@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        fsubrl    144+__sqrt_x_table@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        flds      .L_2il0floatpacket.20@GOTOFF(%ebx)
        fld       %st(0)
        fsubr     %st(3), %st
        fxch      %st(1)
        fcomp     %st(3)
        fnstsw    %ax
        fxch      %st(1)
        fmull     120+__sqrt_x_table@GOTOFF(%ebx)
        sahf      
        fxch      %st(2)
        jp        ..L4
        jbe       ..L3
..L4:
        fst       %st(1)
..L3:
        fstp      %st(0)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   64(%esp)
        fldcw     32(%esp)
        movl      64(%esp), %edx
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   64(%esp)
        fldcw     32(%esp)
        shll      $7, %edx
        movl      64(%esp), %ecx
        movl      %ecx, %ebx
        movl      68(%esp), %edi
        movl      %edi, %eax
        shll      $20, %eax
        shrl      $12, %ebx
        shrl      $11, %ecx
        orl       %ebx, %eax
        sarl      $12, %edi
        andl      $1, %ecx
        addl      %edi, %edx
        addl      %ecx, %eax
        adcl      $0, %edx
        xorl      %ebx, %ebx
        movl      %edx, %ecx
        andl      $1073741824, %ecx
        orl       %ebx, %ecx
        movl      80(%esp), %ebx
        je        ..B1.10
..B1.9:
        movl      $2147483647, 32(%esp)
        jmp       ..B1.11
..B1.10:
        movl      $-1, 32(%esp)
..B1.11:
        movl      %edx, %edi
        xorl      %ecx, %ecx
        subl      %ecx, %edi
        jl        ..B1.13
..B1.12:
        movl      32(%esp), %edx
        movl      $-1, %eax
..B1.13:
        movl      72(%esp), %ecx
        xorl      %edi, %edi
        negl      %ecx
        incl      %ecx
        movl      %edi, 48(%esp)
        movl      %ecx, 52(%esp)
        movl      %eax, 56(%esp)
        movl      %edx, 60(%esp)
        movl      %edi, 64(%esp)
        movl      %edi, 68(%esp)
        addl      $12, %esp
        lea       100(%esp), %eax
        lea       36(%esp), %edx
        pushl     %eax
        pushl     36(%esp)
        pushl     %edx
        call      __dpml_multiply__
..B1.14:
        addl      $12, %esp
        pushl     %esi
        lea       104(%esp), %eax
        pushl     %eax
        lea       44(%esp), %edx
        pushl     %edx
        call      __dpml_multiply__
..B1.15:
        addl      $20, %esp
        lea       160+__sqrt_x_table@GOTOFF(%ebx), %eax
        pushl     %esi
        pushl     $0
        pushl     $9
        pushl     %esi
        pushl     %eax
        call      __dpml_addsub__
..B1.16:
        movl      12(%ebp), %edx
        andl      $1, %edx
        orl       %edi, %edx
        je        ..B1.18
..B1.17:
        lea       112(%esp), %eax
        jmp       ..B1.19
..B1.18:
        lea       48(%esp), %eax
..B1.19:
        addl      $12, %esp
        pushl     %esi
        pushl     %eax
        pushl     %esi
        call      __dpml_multiply__
..B1.20:
        addl      $4, %esp
        decl      4(%esi)
        lea       80(%esp), %eax
        pushl     %eax
        call      fesetenv@PLT
..B1.21:
        movl      12(%ebp), %edx
        xorl      %eax, %eax
        andl      $2, %edx
        orl       %eax, %edx
        jne       ..B1.26
..B1.23:
        movl      136(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.25
..B1.24:
        xorl      %edx, %edx
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        call      __stack_chk_fail@PLT
..B1.26:
        addl      $12, %esp
        pushl     %eax
        pushl     %eax
        pushl     %esi
        call      __dpml_ffs_and_shift__
..B1.27:
        movl      16(%esi), %edx
        xorl      %ecx, %ecx
        movl      20(%esi), %eax
        movl      %edx, 40(%esp)
        movl      %eax, 36(%esp)
        lea       8(%edx), %edi
        andl      $16368, %edi
        orl       %ecx, %edi
        je        ..B1.29
..B1.28:
        xorl      %eax, %eax
        jmp       ..B1.23
..B1.29:
        lea       48(%esp), %edi
        pushl     %edi
        lea       116(%esp), %eax
        pushl     %eax
        pushl     %esi
        pushl     %esi
        movl      %edx, %edi
        andl      $-32768, %edi
        movl      %edi, -72(%eax)
        addl      $16384, %edi
        movl      -76(%eax), %edx
        adcl      $0, %edx
        movl      %edx, -80(%eax)
        movl      %edi, 16(%esi)
        movl      %edx, 20(%esi)
        call      __dpml_extended_multiply__
..B1.30:
        addl      $36, %esp
        lea       92(%esp), %eax
        pushl     %eax
        pushl     $0
        pushl     $1
        pushl     %eax
        pushl     40(%esp)
        call      __dpml_addsub__
..B1.31:
        addl      $20, %esp
        lea       92(%esp), %edx
        pushl     %edx
        pushl     $0
        pushl     $1
        lea       40(%esp), %eax
        pushl     %eax
        pushl     %edx
        call      __dpml_addsub__
..B1.32:
        movl      112(%esp), %eax
        testl     %eax, %eax
        movl      32(%esp), %edx
        je        ..L5
        movl      40(%esp), %edi
..L5:
        je        ..L6
        movl      36(%esp), %edx
..L6:
        xorl      %ecx, %ecx
        movl      %edi, 16(%esi)
        movl      12(%ebp), %edi
        andl      $4, %edi
        movl      %edx, 20(%esi)
        orl       %ecx, %edi
        je        ..B1.28
..B1.33:
        movl      4(%esi), %edx
        testl     %eax, %eax
        movl      $0, %eax
        sete      %al
        addl      $-113, %edx
        subl      %edx, 116(%esp)
        addl      $20, %esp
        orl       $8, %eax
        lea       92(%esp), %ecx
        pushl     %ecx
        pushl     $0
        pushl     %eax
        pushl     %esi
        pushl     %ecx
        call      __dpml_addsub__
..B1.34:
        movl      $8192, %eax
        movl      128(%esp), %edx
        subl      %eax, %edx
        orl       132(%esp), %edx
        jne       ..B1.28
..B1.35:
        movl      $1, %eax
        jmp       ..B1.23
        .align    16,0x90
	.type	__dpml_ux_sqrt_evaluation__,@function
	.size	__dpml_ux_sqrt_evaluation__,.-__dpml_ux_sqrt_evaluation__
	.data
# -- End  __dpml_ux_sqrt_evaluation__
	.text
# -- Begin  __sqrtq
	.text
       .align    16,0x90
	.globl __sqrtq
__sqrtq:
# parameter 1: 24 + %ebp
..B2.1:
..L7:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $132, %esp
        xorl      %esi, %esi
        movl      %gs:20, %edx
        movl      8(%ebp), %edi
        xorl      %esp, %edx
        movl      %edx, 128(%esp)
        call      ..L8
..L8:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L8](%ebx), %ebx
        movl      $1, 40(%esp)
        movl      %esi, 44(%esp)
        movl      %esi, 32(%esp)
        lea       __sqrt_x_table@GOTOFF(%ebx), %ecx
        movl      %esi, 36(%esp)
        addl      $24, %esp
        lea       8(%esp), %eax
        lea       60(%esp), %edx
        pushl     %eax
        lea       92(%esp), %eax
        pushl     %eax
        pushl     %ecx
        pushl     %edx
        pushl     %esi
        lea       24(%ebp), %ecx
        pushl     %ecx
        call      __dpml_unpack_x_or_y__
..B2.2:
        subl      %esi, %edx
        jl        ..B2.8
..B2.3:
        movl      $7, 4(%esp)
        lea       60(%esp), %edx
        movl      $0, 8(%esp)
        lea       84(%esp), %eax
        call      __dpml_ux_sqrt_evaluation__.
..B2.4:
        orl       %edx, %eax
        jne       ..B2.6
..B2.5:
        movl      $1, %edx
        jmp       ..B2.7
..B2.6:
        movl      %esi, %edx
..B2.7:
        movl      %edx, 40(%esp)
        movl      %esi, 44(%esp)
        addl      $28, %esp
        lea       4(%esp), %edx
        pushl     %edx
        pushl     %esi
        pushl     %esi
        pushl     %esi
        pushl     %esi
        lea       104(%esp), %ecx
        pushl     %ecx
        lea       56(%esp), %esi
        pushl     %esi
        call      __dpml_pack__
..B2.8:
        movl      112(%esp), %edx
        movl      %edx, (%edi)
        movl      116(%esp), %ecx
        movl      120(%esp), %esi
        movl      124(%esp), %edx
        movl      %ecx, 4(%edi)
        movl      %esi, 8(%edi)
        movl      %edx, 12(%edi)
        movl      128(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.10
..B2.9:
        movl      %edi, %eax
        addl      $132, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.10:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__sqrtq,@function
	.size	__sqrtq,.-__sqrtq
	.data
# -- End  __sqrtq
	.text
# -- Begin  __rsqrtq
	.text
       .align    16,0x90
	.globl __rsqrtq
__rsqrtq:
# parameter 1: 24 + %ebp
..B3.1:
..L9:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $132, %esp
        xorl      %esi, %esi
        movl      %gs:20, %edx
        movl      8(%ebp), %edi
        xorl      %esp, %edx
        movl      %edx, 128(%esp)
        call      ..L10
..L10:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L10](%ebx), %ebx
        movl      $1, 40(%esp)
        movl      %esi, 44(%esp)
        movl      %esi, 32(%esp)
        lea       8+__sqrt_x_table@GOTOFF(%ebx), %ecx
        movl      %esi, 36(%esp)
        addl      $24, %esp
        lea       8(%esp), %eax
        lea       60(%esp), %edx
        pushl     %eax
        lea       92(%esp), %eax
        pushl     %eax
        pushl     %ecx
        pushl     %edx
        pushl     %esi
        lea       24(%ebp), %ecx
        pushl     %ecx
        call      __dpml_unpack_x_or_y__
..B3.2:
        subl      %esi, %edx
        jl        ..B3.8
..B3.3:
        movl      %esi, 4(%esp)
        lea       60(%esp), %edx
        movl      %esi, 8(%esp)
        lea       84(%esp), %eax
        call      __dpml_ux_sqrt_evaluation__.
..B3.4:
        orl       %edx, %eax
        jne       ..B3.6
..B3.5:
        movl      $1, %edx
        jmp       ..B3.7
..B3.6:
        movl      %esi, %edx
..B3.7:
        movl      %edx, 40(%esp)
        movl      %esi, 44(%esp)
        addl      $28, %esp
        lea       4(%esp), %edx
        pushl     %edx
        pushl     %esi
        pushl     %esi
        pushl     %esi
        pushl     %esi
        lea       104(%esp), %ecx
        pushl     %ecx
        lea       56(%esp), %esi
        pushl     %esi
        call      __dpml_pack__
..B3.8:
        movl      112(%esp), %edx
        movl      %edx, (%edi)
        movl      116(%esp), %ecx
        movl      120(%esp), %esi
        movl      124(%esp), %edx
        movl      %ecx, 4(%edi)
        movl      %esi, 8(%edi)
        movl      %edx, 12(%edi)
        movl      128(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B3.10
..B3.9:
        movl      %edi, %eax
        addl      $132, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B3.10:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__rsqrtq,@function
	.size	__rsqrtq,.-__rsqrtq
	.data
# -- End  __rsqrtq
	.text
# -- Begin  __dpml_ux_hypot__
	.text
       .align    16,0x90
	.globl __dpml_ux_hypot__
__dpml_ux_hypot__:
# parameter 1: 144 + %esp
# parameter 2: 148 + %esp
# parameter 3: 152 + %esp
..B4.1:
..L11:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $124, %esp
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      144(%esp), %edx
        movl      %eax, 112(%esp)
        addl      $12, %esp
        lea       28(%esp), %esi
        pushl     %esi
        pushl     %edx
        pushl     %edx
        call      __dpml_multiply__
..B4.2:
        addl      $12, %esp
        movl      136(%esp), %eax
        lea       52(%esp), %ebx
        pushl     %ebx
        pushl     %eax
        pushl     %eax
        call      __dpml_multiply__
..B4.3:
        addl      $20, %esp
        xorl      %ebp, %ebp
        lea       68(%esp), %edi
        pushl     %edi
        pushl     %ebp
        pushl     %ebp
        pushl     %ebx
        pushl     %esi
        call      __dpml_addsub__
..B4.4:
        addl      $12, %esp
        pushl     %ebp
        pushl     %ebp
        pushl     %edi
        call      __dpml_ffs_and_shift__
..B4.5:
        movl      %edi, %eax
        movl      $7, 4(%esp)
        movl      152(%esp), %edx
        movl      $0, 8(%esp)
        call      __dpml_ux_sqrt_evaluation__..0
..B4.6:
        movl      %eax, %ecx
        orl       %edx, %ecx
        je        ..B4.27
..B4.7:
        movl      44(%esp), %eax
        subl      68(%esp), %eax
        cltd      
        movl      %edx, %edi
        subl      %ebp, %edi
        jge       ..B4.9
..B4.8:
        movl      %eax, %esi
        movl      %ebp, %eax
        subl      %esi, %eax
        movl      %edx, %edi
        movl      %ebp, %edx
        movl      %ebx, %esi
        sbbl      %edi, %edx
..B4.9:
        movl      $128, %ebx
        movl      %eax, %edi
        subl      %ebx, %edi
        movl      %edx, %ebx
        sbbl      %ebp, %ebx
        jl        ..B4.11
..B4.34:
        orl       %ebx, %edi
        je        ..B4.11
..B4.10:
        movl      %ebp, %eax
        movl      %eax, %edx
        jmp       ..B4.27
..B4.11:
        movl      $1, %ecx
        movl      %ebp, %edi
..B4.12:
        movl      8(%esi,%ecx,8), %ebp
        movl      12(%esi,%ecx,8), %ebx
        movl      %ebp, 36(%esp)
        orl       %ebx, %ebp
        movl      %ebx, 32(%esp)
        jne       ..B4.31
..B4.13:
        movl      $64, %ebx
        movl      %eax, %ebp
        subl      %ebx, %ebp
        movl      $0, %ebx
        movl      %edx, %ebp
        sbbl      %ebx, %ebp
        jl        ..B4.15
..B4.14:
        addl      $-64, %eax
        adcl      $-1, %edx
        addl      $-1, %ecx
        adcl      $-1, %edi
        movl      %edi, %ebp
        subl      %ebx, %ebp
        jge       ..B4.12
..B4.15:
        movl      36(%esp), %esi
        xorl      %ebp, %ebp
..B4.16:
        cmpl      $31, %eax
        jbe       ..B4.18
..B4.17:
        movl      %eax, %ecx
        movl      %ebp, %edx
        movl      32(%esp), %ebx
        shrl      %cl, %ebx
        jmp       ..B4.19
..B4.18:
        movl      %eax, %ecx
        movl      %esi, %ebx
        movl      32(%esp), %edx
        shrdl     %cl, %edx, %ebx
        shrl      %cl, %edx
..B4.19:
        cmpl      $31, %eax
        jbe       ..B4.21
..B4.20:
        movl      %eax, %ecx
        movl      %ebx, %edx
        shll      %cl, %edx
        movl      %ebp, %edi
        jmp       ..B4.22
..B4.21:
        movl      %eax, %ecx
        movl      %ebx, %edi
        shldl     %cl, %ebx, %edx
        shll      %cl, %edi
..B4.22:
        movl      32(%esp), %eax
        subl      %edi, %esi
        subl      %edx, %eax
        orl       %eax, %esi
        jne       ..B4.24
..B4.23:
        movl      $1, %eax
        jmp       ..B4.25
..B4.24:
        movl      %ebp, %eax
..B4.25:
        movl      %ebp, %edx
..B4.27:
        movl      112(%esp), %ecx
        xorl      %esp, %ecx
        cmpl      %gs:20, %ecx
        jne       ..B4.29
..B4.28:
        addl      $124, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B4.29:
        call      ..L12
..L12:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L12](%ebx), %ebx
        call      __stack_chk_fail@PLT
..B4.31:
        xorl      %ebp, %ebp
        movl      %ebp, %eax
        xorl      %edx, %edx
        jmp       ..B4.27
        .align    16,0x90
	.type	__dpml_ux_hypot__,@function
	.size	__dpml_ux_hypot__,.-__dpml_ux_hypot__
	.data
# -- End  __dpml_ux_hypot__
	.text
# -- Begin  __dpml_ux_sqrt_evaluation__..0
	.text
       .align    16,0x90
__dpml_ux_sqrt_evaluation__..0:
# parameter 1: %eax
# parameter 2: 12 + %ebp
# parameter 3: %edx
..B5.1:
..L13:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $148, %esp
        movl      %edx, 76(%esp)
        movl      %eax, 72(%esp)
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 136(%esp)
        addl      $4, %esp
        call      ..L14
..L14:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L14](%ebx), %ebx
        lea       80(%esp), %eax
        pushl     %eax
        call      fegetenv@PLT
..B5.2:
        movl      72(%esp), %esi
        movl      %ebx, 36(%esp)
        movl      8(%esi), %edi
        movl      %edi, %edx
        movl      12(%esi), %eax
        movl      %eax, %ecx
        shll      $21, %ecx
        shrl      $11, %edx
        orl       %edx, %ecx
        movl      4(%esi), %edx
        movl      20(%esi), %ebx
        movl      %edx, %esi
        andl      $1, %esi
        movl      %ebx, 40(%esp)
        movl      %eax, %ebx
        shrl      $11, %ebx
        addl      %esi, %edx
        addl      $1070596096, %ebx
        movl      %ebx, 68(%esp)
        lea       8(%esi), %ebx
        movl      %ebx, 32(%esp)
        negl      %ebx
        movl      %ecx, 64(%esp)
        movl      %ebx, %ecx
        negl      %ecx
        sarl      $1, %edx
        addl      $31, %ecx
        movl      %edx, 44(%esp)
        movl      %edi, %edx
        shrl      %cl, %edx
        movl      %ebx, %ecx
        movl      %eax, 80(%esp)
        shrl      $1, %edx
        shll      %cl, %eax
        shll      %cl, %edi
        orl       %edx, %eax
        movl      40(%esp), %edx
        movl      36(%esp), %ebx
..B5.3:
        movl      32(%esp), %ecx
        shrl      %cl, %edx
..B5.5:
        fldl      64(%esp)
        orl       %edx, %edi
        movl      %eax, %edx
        shll      $21, %edx
        shrl      $11, %edi
        shrl      $11, %eax
        orl       %edi, %edx
        movl      %edx, 48(%esp)
        movl      %eax, 52(%esp)
        fildll    48(%esp)
        fstpl     56(%esp)
        fldl      56(%esp)
..B5.6:
        movl      80(%esp), %eax
        shrl      %cl, %eax
..B5.8:
        fmull     136+__sqrt_x_table@GOTOFF(%ebx)
        fld       %st(1)
        fmul      %st(2), %st
        movl      80(%esp), %ecx
        shrl      $24, %ecx
        shll      $7, %esi
        xorl      %esi, %ecx
        shll      $4, %ecx
        movl      __sqrt_t_table@GOT(%ebx), %edx
        movl      %eax, 48(%esp)
        fildl     48(%esp)
        fstpl     56(%esp)
        fldl      56(%esp)
        fmull     128+__sqrt_x_table@GOTOFF(%ebx)
        fstpl     56(%esp)
        fldl      56(%esp)
        fxch      %st(2)
        fstpl     56(%esp)
        fldl      56(%esp)
        fld       %st(0)
        fadd      %st(3), %st
        fxch      %st(2)
        fmuls     (%edx,%ecx)
        flds      4(%edx,%ecx)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddl     8(%edx,%ecx)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpl     56(%esp)
        fldl      56(%esp)
        fmull     96+__sqrt_x_table@GOTOFF(%ebx)
        fstpl     56(%esp)
        fldl      56(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fstps     48(%esp)
        flds      48(%esp)
        fxch      %st(2)
        fstps     48(%esp)
        flds      48(%esp)
        fmul      %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(3), %st
        fld       %st(3)
        fmulp     %st, %st(2)
        fldl      152+__sqrt_x_table@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fld       %st(3)
        fmul      %st(4), %st
        fmulp     %st, %st(3)
        fxch      %st(1)
        fsubrl    104+__sqrt_x_table@GOTOFF(%ebx)
        fsubp     %st, %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fldl      112+__sqrt_x_table@GOTOFF(%ebx)
        fmul      %st(3), %st
        fxch      %st(2)
        fsubrl    144+__sqrt_x_table@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        flds      .L_2il0floatpacket.20@GOTOFF(%ebx)
        fld       %st(0)
        fsubr     %st(3), %st
        fxch      %st(1)
        fcomp     %st(3)
        fnstsw    %ax
        fxch      %st(1)
        fmull     120+__sqrt_x_table@GOTOFF(%ebx)
        sahf      
        fxch      %st(2)
        jp        ..L16
        jbe       ..L15
..L16:
        fst       %st(1)
..L15:
        fstp      %st(0)
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   64(%esp)
        fldcw     32(%esp)
        movl      64(%esp), %edx
        fnstcw    32(%esp)
        movzwl    32(%esp), %eax
        orl       $3072, %eax
        movl      %eax, 40(%esp)
        fldcw     40(%esp)
        fistpll   64(%esp)
        fldcw     32(%esp)
        shll      $7, %edx
        movl      64(%esp), %ecx
        movl      %ecx, %esi
        movl      68(%esp), %edi
        movl      %edi, %eax
        shll      $20, %eax
        shrl      $12, %esi
        shrl      $11, %ecx
        orl       %esi, %eax
        sarl      $12, %edi
        andl      $1, %ecx
        addl      %edi, %edx
        addl      %ecx, %eax
        adcl      $0, %edx
        xorl      %esi, %esi
        movl      %edx, %ecx
        andl      $1073741824, %ecx
        orl       %esi, %ecx
        je        ..B5.10
..B5.9:
        movl      $2147483647, %edi
        jmp       ..B5.11
..B5.10:
        movl      $-1, %edi
..B5.11:
        movl      %edx, %esi
        xorl      %ecx, %ecx
        subl      %ecx, %esi
        jl        ..B5.13
..B5.12:
        movl      $-1, %eax
        movl      %edi, %edx
..B5.13:
        movl      44(%esp), %esi
        xorl      %ecx, %ecx
        negl      %esi
        incl      %esi
        movl      %ecx, 48(%esp)
        movl      %esi, 52(%esp)
        movl      %eax, 56(%esp)
        movl      %edx, 60(%esp)
        movl      %ecx, 64(%esp)
        movl      %ecx, 68(%esp)
        addl      $12, %esp
        lea       100(%esp), %esi
        lea       36(%esp), %eax
        pushl     %esi
        pushl     64(%esp)
        pushl     %eax
        call      __dpml_multiply__
..B5.14:
        addl      $12, %esp
        pushl     64(%esp)
        pushl     %esi
        lea       44(%esp), %eax
        pushl     %eax
        call      __dpml_multiply__
..B5.15:
        addl      $20, %esp
        lea       160+__sqrt_x_table@GOTOFF(%ebx), %edx
        movl      56(%esp), %eax
        pushl     %eax
        pushl     $0
        pushl     $9
        pushl     %eax
        pushl     %edx
        call      __dpml_addsub__
..B5.16:
        addl      $12, %esp
        movl      64(%esp), %eax
        pushl     %eax
        pushl     %esi
        pushl     %eax
        call      __dpml_multiply__
..B5.17:
        addl      $4, %esp
        lea       80(%esp), %eax
        pushl     %eax
        movl      -8(%eax), %edx
        decl      4(%edx)
        call      fesetenv@PLT
..B5.18:
        addl      $12, %esp
        xorl      %ecx, %ecx
        pushl     %ecx
        pushl     %ecx
        pushl     72(%esp)
        call      __dpml_ffs_and_shift__
..B5.19:
        movl      76(%esp), %eax
        xorl      %edi, %edi
        movl      16(%eax), %ecx
        movl      20(%eax), %edx
        movl      %ecx, 44(%esp)
        movl      %edx, 40(%esp)
        lea       8(%ecx), %eax
        andl      $16368, %eax
        orl       %edi, %eax
        jne       ..B5.32
..B5.21:
        lea       48(%esp), %eax
        pushl     %eax
        pushl     %esi
        movl      28(%eax), %edi
        pushl     %edi
        pushl     %edi
        movl      %ecx, %edx
        andl      $-32768, %edx
        movl      %edx, -4(%eax)
        addl      $16384, %edx
        movl      -8(%eax), %ecx
        adcl      $0, %ecx
        movl      %edx, -12(%eax)
        movl      %ecx, -16(%eax)
        movl      %edx, 16(%edi)
        movl      %ecx, 20(%edi)
        call      __dpml_extended_multiply__
..B5.22:
        addl      $36, %esp
        pushl     %esi
        pushl     $0
        pushl     $1
        pushl     %esi
        pushl     68(%esp)
        call      __dpml_addsub__
..B5.23:
        addl      $20, %esp
        pushl     %esi
        pushl     $0
        pushl     $1
        lea       40(%esp), %eax
        pushl     %eax
        pushl     %esi
        call      __dpml_addsub__
..B5.24:
        movl      112(%esp), %eax
        testl     %eax, %eax
        jne       ..B5.26
..B5.25:
        movl      %edi, %ecx
        movl      36(%esp), %eax
        movl      32(%esp), %edx
        movl      4(%ecx), %edi
        addl      $-113, %edi
        movl      %eax, 16(%ecx)
        movl      %edx, 20(%ecx)
        subl      %edi, 116(%esp)
        jmp       ..B5.27
..B5.26:
        movl      44(%esp), %edx
        movl      40(%esp), %ecx
        movl      %edx, 16(%edi)
        movl      4(%edi), %edx
        addl      $-113, %edx
        subl      %edx, 116(%esp)
        movl      %ecx, 20(%edi)
        testl     %eax, %eax
        jne       ..B5.28
..B5.27:
        movl      $1, %eax
        jmp       ..B5.29
..B5.28:
        xorl      %eax, %eax
..B5.29:
        addl      $20, %esp
        orl       $8, %eax
        pushl     %esi
        pushl     $0
        pushl     %eax
        pushl     68(%esp)
        pushl     %esi
        call      __dpml_addsub__
..B5.30:
        movl      $8192, %eax
        movl      128(%esp), %edx
        subl      %eax, %edx
        orl       132(%esp), %edx
        jne       ..B5.32
..B5.31:
        movl      $1, %eax
        jmp       ..B5.34
..B5.32:
        xorl      %eax, %eax
..B5.34:
        movl      136(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B5.36
..B5.35:
        xorl      %edx, %edx
        addl      $148, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B5.36:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__dpml_ux_sqrt_evaluation__..0,@function
	.size	__dpml_ux_sqrt_evaluation__..0,.-__dpml_ux_sqrt_evaluation__..0
	.data
# -- End  __dpml_ux_sqrt_evaluation__..0
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.20:
	.long	0x5f000000
	.type	.L_2il0floatpacket.20,@object
	.size	.L_2il0floatpacket.20,4
	.align 4
__sqrt_x_table:
	.long	1088750600
	.long	605086734
	.long	1088766984
	.long	519758862
	.long	112
	.long	0
	.long	113
	.long	0
	.long	114
	.long	0
	.long	0
	.long	0
	.long	0
	.long	268435456
	.long	858997845
	.long	34
	.long	1636176969
	.long	409044504
	.long	1099306057
	.long	404751376
	.long	545392672
	.long	404783624
	.long	142938632
	.long	302522498
	.long	1719614413
	.long	1073127582
	.long	0
	.long	1072693248
	.long	0
	.long	1097859072
	.long	0
	.long	1151336448
	.long	0
	.long	1047527424
	.long	0
	.long	991952896
	.long	0
	.long	1072431104
	.long	0
	.long	1071120384
	.long	0
	.long	2
	.long	0
	.long	3221225472
	.long	0
	.long	0
	.long	0
	.long	0
	.type	__sqrt_x_table,@object
	.size	__sqrt_x_table,192
	.data
	.hidden __dpml_extended_multiply__
	.hidden __dpml_multiply__
	.hidden __dpml_ffs_and_shift__
	.hidden __dpml_addsub__
	.hidden __dpml_pack__
	.hidden __dpml_unpack_x_or_y__
	.section .note.GNU-stack, ""
# End
