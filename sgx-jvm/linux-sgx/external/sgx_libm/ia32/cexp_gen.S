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
	.file "cexp_gen.c"
	.text
..TXTST0:
# -- Begin  __libm_internal_cexp
	.text
       .align    16,0x90
	.hidden __libm_internal_cexp
	.globl __libm_internal_cexp
__libm_internal_cexp:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        fldz      
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 72(%esp)
        fsts      (%esp)
        fstl      56(%esp)
        fstpl     64(%esp)
..B1.2:
        fnstcw    42(%esp)
..B1.3:
        movzwl    18(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %edx
        jge       ..B1.18
..B1.4:
        testl     %edx, %edx
        jle       ..B1.46
..B1.5:
        movzwl    26(%ebp), %edi
        andl      $32752, %edi
        shrl      $4, %edi
        cmpl      $2047, %edi
        jge       ..B1.19
..B1.6:
        testl     %edi, %edi
        jle       ..B1.44
..B1.7:
        cmpl      $1034, %edx
        jl        ..B1.11
..B1.8:
        fldl      20(%ebp)
        movzbl    19(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fstl      16(%esp)
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _BIG_SMALL@GOTOFF(%ebx,%edx)
        fmul      %st(0), %st
        fstpt     (%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      cosl@PLT
..B1.56:
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jle       ..B1.10
..B1.9:
        addl      $-16, %esp
        fldl      32(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B1.57:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.10:
        fldl      16(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.11:
        movzwl    42(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.43
..B1.12:
        orl       $-64768, %ecx
        movw      %cx, 40(%esp)
..B1.13:
        fldcw     40(%esp)
..B1.14:
        movl      $1, 36(%esp)
..B1.15:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.59:
        fldl      20(%ebp)
        addl      $-16, %esp
        lea       32(%esp), %edx
        lea       44(%esp), %ecx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        movl      %ecx, 16(%esp)
        fstpt     24(%edx)
        call      sincosl@PLT
..B1.58:
        fldt      56(%esp)
        addl      $32, %esp
..B1.16:
        fldt      12(%esp)
        fmul      %st(1), %st
        fstpl     56(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        cmpl      $0, 36(%esp)
        je        ..B1.34
..B1.17:
        fldcw     42(%esp)
        jmp       ..B1.34
..B1.18:
        movzwl    26(%ebp), %edi
        andl      $32752, %edi
        shrl      $4, %edi
..B1.19:
        testl     %edi, %edi
        jne       ..B1.23
..B1.20:
        testl     $1048575, 24(%ebp)
        jne       ..B1.23
..B1.21:
        cmpl      $0, 20(%ebp)
        jne       ..B1.23
..B1.22:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.60:
        fldl      20(%ebp)
        addl      $16, %esp
        fstpl     64(%esp)
        fstpl     56(%esp)
        jmp       ..B1.34
..B1.23:
        cmpl      $2047, %edi
        jge       ..B1.26
..B1.24:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %edx, 28(%esp)
        call      expl@PLT
..B1.62:
        fstpt     16(%esp)
        fldl      20(%ebp)
        movl      28(%esp), %edx
        addl      $16, %esp
        fstl      16(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %edx, 28(%esp)
        call      cosl@PLT
..B1.61:
        movl      28(%esp), %edx
        addl      $16, %esp
        testl     %edi, %edi
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jle       ..B1.49
..B1.25:
        addl      $-16, %esp
        fldl      32(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B1.63:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.26:
        cmpl      $2047, %edx
        jge       ..B1.28
..B1.27:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.64:
        addl      $16, %esp
        fstl      56(%esp)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.28:
        testl     $1048575, 16(%ebp)
        jne       ..B1.30
..B1.29:
        cmpl      $0, 12(%ebp)
        je        ..B1.31
..B1.30:
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstl      56(%esp)
        fldl      20(%ebp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.31:
        movzbl    19(%ebp), %edx
        testl     $128, %edx
        je        ..B1.33
..B1.32:
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     20(%ebp)
        flds      (%esp)
        fstl      56(%esp)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.33:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.66:
        fldl      20(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpl     56(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.65:
        addl      $16, %esp
        fstpl     64(%esp)
..B1.34:
        movzwl    62(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.37
..B1.35:
        testl     $1048575, 60(%esp)
        jne       ..B1.40
..B1.36:
        cmpl      $0, 56(%esp)
        jne       ..B1.40
..B1.37:
        movzwl    70(%esp), %edx
        testl     $32752, %edx
        jne       ..B1.41
..B1.38:
        testl     $1048575, 68(%esp)
        jne       ..B1.40
..B1.39:
        cmpl      $0, 64(%esp)
        je        ..B1.41
..B1.40:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     48(%esp)
..B1.41:
        movl      56(%esp), %edx
        movl      %edx, (%esi)
        movl      60(%esp), %ecx
        movl      64(%esp), %edi
        movl      68(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      72(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.53
..B1.42:
        movl      %esi, %eax
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.43:
        movl      $0, 36(%esp)
        jmp       ..B1.15
..B1.44:
        testl     $1048575, 24(%ebp)
        jne       ..B1.7
..B1.45:
        cmpl      $0, 20(%ebp)
        jne       ..B1.7
        jmp       ..B1.19
..B1.46:
        testl     $1048575, 16(%ebp)
        jne       ..B1.5
..B1.47:
        cmpl      $0, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.18
..B1.49:
        testl     %edx, %edx
        jne       ..B1.52
..B1.50:
        testl     $1048575, 16(%ebp)
        jne       ..B1.52
..B1.51:
        cmpl      $0, 12(%ebp)
        je        ..B1.25
..B1.52:
        fldl      16(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        jmp       ..B1.34
..B1.53:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__libm_internal_cexp,@function
	.size	__libm_internal_cexp,.-__libm_internal_cexp
	.data
# -- End  __libm_internal_cexp
	.text
# -- Begin  cexp
	.text
       .align    16,0x90
	.globl cexp
cexp:
# parameter 1: 12 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $116, %esp
        fldz      
        fldl      12(%ebp)
        fldl      20(%ebp)
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 80(%esp)
        fstpl     72(%esp)
        fstpl     64(%esp)
        fsts      (%esp)
        fstl      48(%esp)
        fstpl     56(%esp)
..B2.2:
        fnstcw    38(%esp)
..B2.3:
        movzwl    70(%esp), %esi
        andl      $32752, %esi
        shrl      $4, %esi
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        cmpl      $2047, %esi
        jge       ..B2.18
..B2.4:
        testl     %esi, %esi
        jle       ..B2.46
..B2.5:
        movzwl    78(%esp), %edi
        andl      $32752, %edi
        shrl      $4, %edi
        cmpl      $2047, %edi
        jge       ..B2.19
..B2.6:
        testl     %edi, %edi
        jle       ..B2.44
..B2.7:
        cmpl      $1034, %esi
        jl        ..B2.11
..B2.8:
        fldl      72(%esp)
        movzbl    71(%esp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fstl      16(%esp)
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _BIG_SMALL@GOTOFF(%ebx,%ecx)
        fmul      %st(0), %st
        fstpt     (%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      cosl@PLT
..B2.56:
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fmulp     %st, %st(1)
        fstpl     48(%esp)
        jle       ..B2.10
..B2.9:
        addl      $-16, %esp
        fldl      32(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.57:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.10:
        fldl      16(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.11:
        movzwl    38(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.43
..B2.12:
        orl       $-64768, %edx
        movw      %dx, 36(%esp)
..B2.13:
        fldcw     36(%esp)
..B2.14:
        movl      $1, %esi
..B2.15:
        addl      $-16, %esp
        fldl      80(%esp)
        fstpt     (%esp)
        call      expl@PLT
..B2.59:
        addl      $-16, %esp
        fldl      104(%esp)
        lea       32(%esp), %eax
        lea       44(%esp), %edx
        fstpt     (%esp)
        movl      %eax, 12(%esp)
        movl      %edx, 16(%esp)
        fstpt     24(%eax)
        call      sincosl@PLT
..B2.58:
        fldt      56(%esp)
        addl      $32, %esp
..B2.16:
        fldt      12(%esp)
        testl     %esi, %esi
        fmul      %st(1), %st
        fstpl     48(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        je        ..B2.34
..B2.17:
        fldcw     38(%esp)
        jmp       ..B2.34
..B2.18:
        movzwl    78(%esp), %edi
        andl      $32752, %edi
        shrl      $4, %edi
..B2.19:
        testl     %edi, %edi
        jne       ..B2.23
..B2.20:
        testl     $1048575, 76(%esp)
        jne       ..B2.23
..B2.21:
        cmpl      $0, 72(%esp)
        jne       ..B2.23
..B2.22:
        addl      $-16, %esp
        fldl      80(%esp)
        fstpt     (%esp)
        call      expl@PLT
..B2.60:
        addl      $16, %esp
        fldl      72(%esp)
        fstpl     56(%esp)
        fstpl     48(%esp)
        jmp       ..B2.34
..B2.23:
        cmpl      $2047, %edi
        jge       ..B2.26
..B2.24:
        fldl      64(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B2.62:
        fstpt     16(%esp)
        addl      $16, %esp
        fldl      72(%esp)
        fstl      16(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      cosl@PLT
..B2.61:
        addl      $16, %esp
        fldt      (%esp)
        testl     %edi, %edi
        fmulp     %st, %st(1)
        fstpl     48(%esp)
        jle       ..B2.49
..B2.25:
        addl      $-16, %esp
        fldl      32(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.63:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.26:
        cmpl      $2047, %esi
        jge       ..B2.28
..B2.27:
        addl      $-16, %esp
        fldl      88(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.64:
        addl      $16, %esp
        fstl      48(%esp)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.28:
        testl     $1048575, 68(%esp)
        jne       ..B2.30
..B2.29:
        cmpl      $0, 64(%esp)
        je        ..B2.31
..B2.30:
        fldl      64(%esp)
        fmul      %st(0), %st
        fstl      48(%esp)
        fldl      72(%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.31:
        movzbl    71(%esp), %eax
        testl     $128, %eax
        je        ..B2.33
..B2.32:
        fldl      72(%esp)
        fmul      %st(0), %st
        fstpl     72(%esp)
        flds      (%esp)
        fstl      48(%esp)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.33:
        addl      $-16, %esp
        fldl      80(%esp)
        fstpt     (%esp)
        call      expl@PLT
..B2.66:
        addl      $16, %esp
        fstpl     48(%esp)
        addl      $-16, %esp
        fldl      88(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.65:
        addl      $16, %esp
        fstpl     56(%esp)
..B2.34:
        movzwl    54(%esp), %eax
        testl     $32752, %eax
        jne       ..B2.37
..B2.35:
        testl     $1048575, 52(%esp)
        jne       ..B2.40
..B2.36:
        cmpl      $0, 48(%esp)
        jne       ..B2.40
..B2.37:
        movzwl    62(%esp), %eax
        testl     $32752, %eax
        jne       ..B2.41
..B2.38:
        testl     $1048575, 60(%esp)
        jne       ..B2.40
..B2.39:
        cmpl      $0, 56(%esp)
        je        ..B2.41
..B2.40:
        fldl      _DBL_MIN_NORMAL@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     40(%esp)
..B2.41:
        fldl      48(%esp)
        movl      8(%ebp), %eax
        movl      52(%esp), %ecx
        movl      48(%esp), %edx
        movl      60(%esp), %edi
        movl      56(%esp), %esi
        fstp      %st(0)
        fldl      56(%esp)
        fstp      %st(0)
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      %esi, 8(%eax)
        movl      %edi, 12(%eax)
        movl      80(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.53
..B2.42:
        addl      $116, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.43:
        xorl      %esi, %esi
        jmp       ..B2.15
..B2.44:
        testl     $1048575, 76(%esp)
        jne       ..B2.7
..B2.45:
        cmpl      $0, 72(%esp)
        jne       ..B2.7
        jmp       ..B2.19
..B2.46:
        testl     $1048575, 68(%esp)
        jne       ..B2.5
..B2.47:
        cmpl      $0, 64(%esp)
        jne       ..B2.5
        jmp       ..B2.18
..B2.49:
        testl     %esi, %esi
        jne       ..B2.52
..B2.50:
        testl     $1048575, 68(%esp)
        jne       ..B2.52
..B2.51:
        cmpl      $0, 64(%esp)
        je        ..B2.25
..B2.52:
        fldl      16(%esp)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpl     56(%esp)
        jmp       ..B2.34
..B2.53:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	cexp,@function
	.size	cexp,.-cexp
	.data
# -- End  cexp
	.section .rodata, "a"
	.align 16
	.align 16
_BIG_SMALL:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.type	_BIG_SMALL,@object
	.size	_BIG_SMALL,24
	.space 8, 0x00 	# pad
	.align 16
_DBL_MIN_NORMAL:
	.long	0
	.long	1048576
	.type	_DBL_MIN_NORMAL,@object
	.size	_DBL_MIN_NORMAL,8
	.data
	.section .note.GNU-stack, ""
# End
