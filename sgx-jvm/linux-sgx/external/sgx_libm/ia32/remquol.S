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
	.file "remquol.c"
	.text
..TXTST0:
# -- Begin  remquol
	.text
       .align    16,0x90
	.globl remquol
remquol:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
# parameter 3: 32 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        movl      32(%ebp), %esi
..B1.2:
        fnstcw    30(%esp)
..B1.3:
        movzwl    28(%ebp), %eax
        movl      $0, (%esi)
        andl      $32767, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        jne       ..B1.6
..B1.4:
        cmpl      $0, 24(%ebp)
        jne       ..B1.6
..B1.5:
        cmpl      $0, 20(%ebp)
        je        ..B1.54
..B1.6:
        movzwl    16(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.51
..B1.7:
        cmpl      $32767, %eax
        je        ..B1.26
..B1.8:
        movzbl    17(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        movl      %eax, (%esp)
        movzwl    30(%esp), %eax
        movl      %eax, %ecx
        movzbl    29(%ebp), %edx
        andl      $3840, %ecx
        andl      $128, %edx
        shrl      $7, %edx
        movl      %edx, 4(%esp)
        cmpl      $768, %ecx
        je        ..B1.25
..B1.9:
        andl      $-3841, %eax
        orl       $-64768, %eax
        movw      %ax, 28(%esp)
..B1.10:
        fldcw     28(%esp)
..B1.11:
        movl      $1, %edi
..B1.12:
        fldt      8(%ebp)
        addl      $-32, %esp
        fstpt     (%esp)
        fldt      20(%ebp)
        fstpt     12(%esp)
        movl      %esi, 24(%esp)
        call      __libm_remquol
..B1.69:
        addl      $32, %esp
..B1.13:
        fstpt     16(%esp)
        testl     %edi, %edi
        je        ..B1.15
..B1.14:
        fldcw     30(%esp)
..B1.15:
        movl      (%esi), %edx
        testl     %edx, %edx
        je        ..B1.19
..B1.16:
        movl      4(%esp), %eax
        imull     iones@GOTOFF(%ebx,%eax,4), %edx
        cmpl      (%esp), %eax
        je        ..B1.18
..B1.17:
        orl       $-2147483648, %edx
        movl      %edx, (%esi)
        jmp       ..B1.19
..B1.18:
        andl      $2147483647, %edx
        movl      %edx, (%esi)
..B1.19:
        movzwl    24(%esp), %eax
        testl     $32767, %eax
        jne       ..B1.24
..B1.20:
        cmpl      $0, 20(%esp)
        jne       ..B1.23
..B1.21:
        cmpl      $0, 16(%esp)
        jne       ..B1.23
..B1.22:
        movl      (%esp), %eax
        fldl      _zeros@GOTOFF(%ebx,%eax,8)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.23:
        fldl      _small_value_64@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpl     8(%esp)
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.25:
        xorl      %edi, %edi
        jmp       ..B1.12
..B1.26:
        cmpl      $-2147483648, 24(%ebp)
        jne       ..B1.28
..B1.27:
        cmpl      $0, 20(%ebp)
        je        ..B1.34
..B1.28:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.40
..B1.29:
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.30:
        fldcw     28(%esp)
..B1.31:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     16(%esp)
..B1.32:
        fldcw     30(%esp)
..B1.33:
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.34:
        cmpl      $32767, %edx
        je        ..B1.41
..B1.35:
        testl     %edx, %edx
        jne       ..B1.39
..B1.36:
        cmpl      $0, 12(%ebp)
        jne       ..B1.38
..B1.37:
        cmpl      $0, 8(%ebp)
        je        ..B1.39
..B1.38:
        fldl      _smallest_value_64@GOTOFF(%ebx)
        fstpl     8(%esp)
..B1.39:
        fldt      8(%ebp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.40:
        fldt      8(%ebp)
        fldt      20(%ebp)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.33
..B1.41:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.39
..B1.42:
        cmpl      $0, 8(%ebp)
        jne       ..B1.39
..B1.43:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.50
..B1.44:
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.45:
        fldcw     28(%esp)
..B1.46:
        movl      $1, %eax
..B1.47:
        fldl      _infs@GOTOFF(%ebx)
        testl     %eax, %eax
        fmull     _zeros@GOTOFF(%ebx)
        fstpl     (%esp)
        fldl      (%esp)
        fstpt     16(%esp)
        je        ..B1.49
..B1.48:
        fldcw     30(%esp)
..B1.49:
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        xorl      %eax, %eax
        jmp       ..B1.47
..B1.51:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.28
..B1.52:
        cmpl      $0, 8(%ebp)
        jne       ..B1.28
..B1.53:
        cmpl      $32767, %eax
        je        ..B1.26
        jmp       ..B1.34
..B1.54:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.66
..B1.55:
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.56:
        fldcw     28(%esp)
..B1.57:
        movl      $1, %edx
..B1.58:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.63
..B1.59:
        fldl      _infs@GOTOFF(%ebx)
        fmull     _zeros@GOTOFF(%ebx)
        fstpl     (%esp)
        fldl      (%esp)
        fstpt     16(%esp)
..B1.60:
        testl     %edx, %edx
        je        ..B1.62
..B1.61:
        fldcw     30(%esp)
..B1.62:
        fldt      16(%esp)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.63:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.65
..B1.64:
        cmpl      $0, 8(%ebp)
        je        ..B1.59
..B1.65:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ebx)
        fstpt     16(%esp)
        jmp       ..B1.60
..B1.66:
        xorl      %edx, %edx
        jmp       ..B1.58
        .align    16,0x90
	.type	remquol,@function
	.size	remquol,.-remquol
	.data
# -- End  remquol
	.text
# -- Begin  __libm_remquol
	.text
       .align    16,0x90
__libm_remquol:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
# parameter 3: 32 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        fldt      8(%ebp)
        call      ..L4
..L4:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%esi), %esi
        fstpt     156(%esp)
        fldt      156(%esp)
        andb      $127, 29(%ebp)
        fldt      20(%ebp)
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        movzwl    28(%ebp), %edi
        andl      $32767, %edi
        fstpt     168(%esp)
        fldt      168(%esp)
        cmpl      $32703, %eax
        fldl      _ones@GOTOFF(%esi)
        movl      32(%ebp), %ebx
        movl      $0, 168(%esp)
        jl        ..B2.5
..B2.2:
        fstp      %st(0)
        cmpl      $32703, %edi
        jl        ..B2.9
..B2.3:
        fldl      8+_scales@GOTOFF(%esi)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%esp)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpt     20(%ebp)
        movl      %ebx, 24(%esp)
        call      __libm_remquol
..B2.4:
        fmull     _scales@GOTOFF(%esi)
        fstpt     156(%esp)
        fldt      156(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.5:
        cmpl      $64, %edi
        jge       ..B2.12
..B2.6:
        fstp      %st(0)
        fldl      _scales@GOTOFF(%esi)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     20(%ebp)
        fldt      20(%ebp)
        fxch      %st(1)
        fstpt     32(%esp)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      %ebx, 24(%esp)
        call      __libm_remquol
..B2.7:
        fldt      32(%esp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        fldt      20(%ebp)
        fstpt     12(%esp)
        movl      %ebx, 24(%esp)
        call      __libm_remquol
..B2.8:
        fmull     8+_scales@GOTOFF(%esi)
        fstpt     156(%esp)
        fldt      156(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.9:
        fldl      8+_scales@GOTOFF(%esi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      %ebx, 24(%esp)
        call      __libm_remquol
..B2.10:
        fldl      _scales@GOTOFF(%esi)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        fldt      20(%ebp)
        fstpt     12(%esp)
        movl      %ebx, 24(%esp)
        call      __libm_remquol
..B2.11:
        fstpt     156(%esp)
        fldt      156(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.12:
        lea       1(%eax), %edx
        cmpl      %edx, %edi
        jg        ..B2.34
..B2.13:
        lea       -32(%eax), %edx
        cmpl      %edx, %edi
        jle       ..B2.22
..B2.14:
        fstp      %st(0)
        fldt      168(%esp)
        fld       %st(2)
        fdiv      %st(2), %st
        fld       %st(2)
        fsub      %st(2), %st
        fldl      _TWO_63H@GOTOFF(%esi)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        movl      32(%esp), %edx
        fsubr     %st(3), %st
        fabs      
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fmul      %st(3), %st
        fsubrp    %st, %st(5)
        fldl      _halfs@GOTOFF(%esi)
        fucom     %st(1)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        jp        ..B2.15
        je        ..B2.16
..B2.15:
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
        fstp      %st(0)
        movl      %edx, (%ebx)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.16:
        fmul      %st(3), %st
        fld       %st(4)
        fabs      
        fcompp    
        fnstsw    %ax
        sahf      
        jp        ..B2.17
        jbe       ..B2.21
..B2.17:
        fcompp    
        fnstsw    %ax
        sahf      
        ja        ..B2.19
        jp        ..B2.19
..B2.18:
        decl      %edx
        faddp     %st, %st(1)
        movl      %edx, (%ebx)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.19:
        incl      %edx
        fsubrp    %st, %st(1)
        movl      %edx, (%ebx)
..B2.20:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.21:
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      %edx, (%ebx)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.22:
        fldt      168(%esp)
        subl      %edi, %eax
        fstpt     56(%esp)
        fldt      56(%esp)
        fld       %st(1)
        fdiv      %st(3), %st
        fld       %st(3)
        fsubp     %st, %st(2)
        addl      $-31, %eax
        movl      %eax, 80(%esp)
        jle       ..B2.35
..B2.23:
        fstpt     96(%esp)
        fldt      96(%esp)
        movzwl    104(%esp), %edx
        fstpt     44(%esp)
        fldt      44(%esp)
        movl      %edx, 144(%esp)
        movl      48(%esp), %edx
        movzwl    52(%esp), %ecx
        fxch      %st(2)
        fstpt     108(%esp)
        movl      %edx, 136(%esp)
        movzwl    176(%esp), %edx
        fstpt     84(%esp)
        fldt      84(%esp)
        movl      %ecx, 140(%esp)
        movzwl    116(%esp), %ecx
        movl      %edx, 128(%esp)
        movzwl    92(%esp), %edx
        fstpt     32(%esp)
        fldt      32(%esp)
        fldl      _TWO_63H@GOTOFF(%esi)
        movl      %ecx, 132(%esp)
        movl      172(%esp), %ecx
        movl      %edx, 68(%esp)
        movl      36(%esp), %edx
        movl      %ecx, 124(%esp)
        movzwl    40(%esp), %ecx
        andl      $32767, %ecx
        movl      %ecx, 148(%esp)
        movl      %esi, 76(%esp)
        movl      %edx, 120(%esp)
        xorl      %edx, %edx
        movl      %edi, 152(%esp)
        movl      %ebx, 72(%esp)
        movl      68(%esp), %esi
        movl      %eax, %ecx
..B2.24:
        movl      140(%esp), %ebx
        andl      $-32768, %esi
        subl      %eax, %ebx
        subl      %eax, %ecx
        movl      144(%esp), %edi
        andl      $32767, %ebx
        andl      $-32768, %edi
        orl       %ebx, %edi
        movl      136(%esp), %ebx
        movw      %di, 104(%esp)
        movl      %ebx, 100(%esp)
        fldt      96(%esp)
        movl      128(%esp), %ebx
        fmul      %st(5), %st
        movl      %edi, 144(%esp)
        addl      %eax, %ebx
        movl      132(%esp), %edi
        andl      $32767, %ebx
        andl      $-32768, %edi
        fadd      %st(1), %st
        orl       %ebx, %edi
        movl      %edi, 132(%esp)
        xorl      %ebx, %ebx
        movw      %di, 116(%esp)
        movl      124(%esp), %edi
        movl      %edi, 112(%esp)
        movl      148(%esp), %edi
        testl     %edi, %edi
        fstpt     32(%esp)
        fldt      32(%esp)
        fsub      %st(1), %st
        fldt      108(%esp)
        setne     %bl
        imull     %eax, %ebx
        fmul      %st(1), %st
        addl      %edi, %ebx
        fsubrp    %st, %st(6)
        andl      $32767, %ebx
        orl       %ebx, %esi
        movl      120(%esp), %ebx
        movw      %si, 92(%esp)
        movl      %ebx, 88(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        shll      %cl, %edx
        cmpl      $32, %ecx
        movl      %eax, %ecx
        fsubrp    %st, %st(5)
        jb        ..L5
        movl      $0, %edx
..L5:
        fxch      %st(4)
        fstpt     156(%esp)
        fldt      156(%esp)
        movzwl    164(%esp), %edi
        andl      $32767, %edi
        subl      152(%esp), %edi
        movl      %edi, %eax
        addl      32(%esp), %edx
        addl      $-31, %eax
        fxch      %st(4)
        jg        ..B2.24
..B2.25:
        movl      %ecx, 80(%esp)
        movl      72(%esp), %ebx
        movl      76(%esp), %esi
..B2.26:
        fldt      56(%esp)
        fld       %st(1)
        fxch      %st(4)
        fmul      %st(6), %st
        movl      80(%esp), %ecx
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     32(%esp)
        fldt      32(%esp)
        fld       %st(3)
        shll      %cl, %edx
        cmpl      $32, %ecx
        fxch      %st(1)
        fsub      %st(3), %st
        jb        ..L6
        movl      $0, %edx
..L6:
        fmul      %st, %st(2)
        addl      32(%esp), %edx
        fxch      %st(2)
        fsubrp    %st, %st(7)
        fmul      %st(1), %st
        fsubrp    %st, %st(6)
        fld       %st(5)
        fabs      
        fldl      _halfs@GOTOFF(%esi)
        fmul      %st(6), %st
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        ja        ..B2.33
..B2.27:
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B2.32
        jp        ..B2.32
..B2.28:
        fstp      %st(1)
        fstp      %st(1)
        movl      %edx, (%ebx)
        fcompp    
        fnstsw    %ax
        sahf      
        ja        ..B2.30
        jp        ..B2.30
..B2.29:
        fsubrp    %st, %st(1)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.30:
        faddp     %st, %st(1)
..B2.31:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.32:
        fstp      %st(0)
        fstp      %st(2)
        fldt      56(%esp)
        fxch      %st(3)
        fdivr     %st(4), %st
        fadd      %st(2), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        addl      32(%esp), %edx
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fmulp     %st, %st(1)
        movl      %edx, (%ebx)
        fsubrp    %st, %st(1)
        fstpt     156(%esp)
        fldt      156(%esp)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.33:
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
        movl      %edx, (%ebx)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.34:
        fstp      %st(0)
        fstp      %st(0)
        movl      $0, (%ebx)
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.35:
        fstp      %st(2)
        fldl      _TWO_63H@GOTOFF(%esi)
        xorl      %edx, %edx
        jmp       ..B2.26
        .align    16,0x90
	.type	__libm_remquol,@function
	.size	__libm_remquol,.-__libm_remquol
	.data
# -- End  __libm_remquol
	.section .rodata, "a"
	.align 4
	.align 4
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_scales:
	.long	0
	.long	1341128704
	.long	0
	.long	804257792
	.type	_scales,@object
	.size	_scales,16
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_halfs:
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.type	_halfs,@object
	.size	_halfs,16
	.data
	.section .note.GNU-stack, ""
# End
