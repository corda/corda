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
	.file "libm_recacosl_recasinl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_carctrigprepare_k80
	.text
       .align    16,0x90
	.hidden __libm_carctrigprepare_k80
	.globl __libm_carctrigprepare_k80
__libm_carctrigprepare_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
# parameter 4: 20 + %ebp
# parameter 5: 24 + %ebp
# parameter 6: 28 + %ebp
# parameter 7: 32 + %ebp
# parameter 8: 36 + %ebp
# parameter 9: 40 + %ebp
# parameter 10: 44 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $212, %esp
        xorl      %ecx, %ecx
        movl      8(%ebp), %esi
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        movl      16(%ebp), %ebx
        movl      %edx, 60(%esp)
        fldt      _CONSTANTS@GOTOFF(%edx)
        fldt      24+_CONSTANTS@GOTOFF(%edx)
        movl      (%esi), %edi
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 200(%esp)
        movl      %edi, (%ebx)
        movl      4(%esi), %eax
        movl      %eax, 4(%ebx)
        movl      8(%esi), %edx
        movl      %edx, 8(%ebx)
        shrl      $8, %edx
        movl      %ecx, 112(%esp)
        fstpt     16(%esp)
        fldt      16(%esp)
        fxch      %st(1)
        fstpt     88(%esp)
        fstpt     100(%esp)
        fldt      100(%esp)
        addl      $4, %esp
        movzbl    %dl, %edx
        pushl     %ebx
        andl      $127, %edx
        movl      %ecx, 24(%ebx)
        movb      %dl, 9(%ebx)
        fstpt     12(%ebx)
        call      __libm_normalizel_k80
..B1.2:
        movl      12(%ebp), %edx
        addl      $4, %esp
        movl      20(%ebp), %esi
        pushl     %esi
        fldt      16(%esp)
        movl      (%edx), %edi
        movl      %edi, (%esi)
        movl      4(%edx), %eax
        movl      %eax, 4(%esi)
        movl      8(%edx), %ecx
        movl      %ecx, 8(%esi)
        shrl      $8, %ecx
        movzbl    %cl, %ecx
        andl      $127, %ecx
        movl      $0, 24(%esi)
        movb      %cl, 9(%esi)
        fstpt     12(%esi)
        call      __libm_normalizel_k80
..B1.3:
        fldt      (%esi)
        movl      60(%esp), %edx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      12(%esi)
        fmulp     %st, %st(4)
        movl      32(%ebp), %ecx
        fxch      %st(3)
        fadd      %st(0), %st
        fldt      36+_CONSTANTS@GOTOFF(%edx)
        fmul      %st, %st(3)
        movl      24(%esi), %eax
        fxch      %st(2)
        fsub      %st(3), %st
        addl      %eax, %eax
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(4)
        fmul      %st(4), %st
        movl      %eax, 24(%ecx)
        fadd      %st(0), %st
        fxch      %st(4)
        fmul      %st(0), %st
        fxch      %st(2)
        fstpt     32(%esp)
        fld       %st(3)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(1)
        movzwl    8(%ebx), %eax
        fadd      %st(1), %st
        movl      %eax, 68(%esp)
        andl      $32767, %eax
        fstpt     12(%ecx)
        cmpl      $8383, %eax
        fstpt     48(%esp)
        jl        ..B1.7
..B1.4:
        cmpl      $24383, %eax
        jg        ..B1.7
..B1.5:
        movzwl    96(%esp), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        movl      %edx, 28(%esp)
        cmpl      $8383, %edx
        jl        ..B1.7
..B1.6:
        cmpl      $24383, 28(%esp)
        jle       ..B1.10
..B1.7:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.8:
        addl      $4, %esp
        lea       84(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.9:
        movzwl    96(%esp), %edx
        movzwl    8(%ebx), %eax
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        movl      %eax, 68(%esp)
        andl      $32767, %eax
        movl      %edx, 28(%esp)
..B1.10:
        movl      24(%ebx), %ecx
        movl      112(%esp), %esi
        movl      %ecx, 64(%esp)
        lea       (%eax,%ecx), %edi
        movl      %edi, 20(%esp)
        lea       (%edx,%esi), %ecx
        lea       -134(%esi,%edx), %edx
        cmpl      %edi, %edx
        jge       ..B1.27
..B1.11:
        movl      4(%ebx), %edi
        movl      %edi, 16(%esp)
        movl      28(%esp), %edi
        lea       134(%esi,%edi), %edi
        cmpl      20(%esp), %edi
        jle       ..B1.28
..B1.12:
        cmpl      $0, 16(%esp)
        jne       ..B1.14
..B1.13:
        cmpl      $0, (%ebx)
        je        ..B1.29
..B1.14:
        cmpl      $0, 92(%esp)
        jne       ..B1.16
..B1.15:
        cmpl      $0, 88(%esp)
        je        ..B1.28
..B1.16:
        cmpl      64(%esp), %esi
        je        ..B1.18
..B1.17:
        fldt      48(%esp)
        movl      64(%esp), %eax
        fstpt     16(%esp)
        subl      %esi, %eax
        fldt      (%ebx)
        addl      $16383, %eax
        fldt      12(%ebx)
        andl      $32767, %eax
        andb      $127, 25(%esp)
        movzwl    24(%esp), %edi
        andl      $-32768, %edi
        orl       %eax, %edi
        movw      %di, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        movl      %esi, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        movzwl    8(%ebx), %eax
        fstpt     48(%esp)
        movl      %eax, 68(%esp)
        jmp       ..B1.19
..B1.18:
        fldt      (%ebx)
        fstpt     48(%esp)
..B1.19:
        fldt      88(%esp)
        fldt      48(%esp)
        movl      28(%ebp), %eax
        movl      68(%esp), %edi
        fadd      %st(1), %st
        movl      %esi, 24(%eax)
        andl      $32767, %edi
        fstpt     12(%eax)
        fldt      12(%eax)
        movl      44(%esp), %eax
        andl      $32767, %eax
        movl      %edi, 68(%esp)
        cmpl      %eax, %edi
        jg        ..B1.24
..B1.20:
        movl      44(%esp), %eax
        andl      $32767, %eax
        cmpl      68(%esp), %eax
        jne       ..B1.25
..B1.21:
        movl      4(%ebx), %eax
        cmpl      92(%esp), %eax
        ja        ..B1.24
..B1.22:
        jne       ..B1.25
..B1.23:
        movl      (%ebx), %eax
        cmpl      88(%esp), %eax
        jbe       ..B1.25
..B1.24:
        fldt      48(%esp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      100(%esp)
        faddp     %st, %st(2)
        fldt      12(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B1.26
..B1.25:
        fldt      48(%esp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      100(%esp)
        fldt      12(%ebx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B1.26:
        fldt      48(%esp)
        fld       %st(0)
        movl      28(%ebp), %eax
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        jmp       ..B1.33
..B1.27:
        movl      4(%ebx), %edi
        movl      %edi, 16(%esp)
..B1.28:
        cmpl      $0, 16(%esp)
        jne       ..B1.30
..B1.210:
        cmpl      $0, (%ebx)
..B1.29:
        je        ..B1.31
..B1.30:
        movl      28(%esp), %edi
        lea       -16517(%esi,%edi), %ebx
        movl      64(%esp), %edi
        lea       -16383(%edi,%eax), %eax
        cmpl      %eax, %ebx
        movl      16(%ebp), %ebx
        jl        ..B1.32
..B1.31:
        movl      28(%ebp), %eax
        movl      88(%esp), %edi
        movl      %edi, (%eax)
        movl      92(%esp), %edi
        movl      %edi, 4(%eax)
        movl      96(%esp), %edi
        movl      %edi, 8(%eax)
        movl      100(%esp), %edi
        movl      %edi, 12(%eax)
        movl      104(%esp), %edi
        movl      %edi, 16(%eax)
        movl      108(%esp), %edi
        movl      %edi, 20(%eax)
        movl      %esi, 24(%eax)
        jmp       ..B1.33
..B1.32:
        movl      28(%ebp), %eax
        movl      (%ebx), %edi
        movl      %edi, (%eax)
        movl      4(%ebx), %edi
        movl      %edi, 4(%eax)
        movl      8(%ebx), %edi
        movl      %edi, 8(%eax)
        movl      12(%ebx), %edi
        movl      %edi, 12(%eax)
        movl      16(%ebx), %edi
        movl      %edi, 16(%eax)
        movl      20(%ebx), %edi
        movl      %edi, 20(%eax)
        movl      24(%ebx), %edi
        movl      %edi, 24(%eax)
..B1.33:
        movzwl    8(%ebx), %eax
        movl      %eax, 64(%esp)
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.37
..B1.34:
        cmpl      $24383, %eax
        jg        ..B1.37
..B1.35:
        cmpl      $8383, 28(%esp)
        jl        ..B1.37
..B1.36:
        cmpl      $24383, 28(%esp)
        jle       ..B1.40
..B1.37:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.38:
        addl      $4, %esp
        lea       84(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.39:
        movzwl    96(%esp), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        movl      112(%esp), %esi
        movzwl    8(%ebx), %eax
        movl      %eax, 64(%esp)
        andl      $32767, %eax
        lea       (%esi,%edx), %ecx
        lea       -134(%esi,%edx), %edx
..B1.40:
        movl      24(%ebx), %edi
        movl      %edi, 72(%esp)
        addl      %eax, %edi
        movl      %edi, 68(%esp)
        cmpl      %edi, %edx
        jge       ..B1.57
..B1.41:
        movl      4(%ebx), %edx
        lea       134(%ecx), %edi
        movl      %edx, 28(%esp)
        cmpl      68(%esp), %edi
        jle       ..B1.58
..B1.42:
        cmpl      $0, 28(%esp)
        jne       ..B1.44
..B1.43:
        cmpl      $0, (%ebx)
        je        ..B1.59
..B1.44:
        cmpl      $0, 92(%esp)
        jne       ..B1.46
..B1.45:
        cmpl      $0, 88(%esp)
        je        ..B1.58
..B1.46:
        cmpl      72(%esp), %esi
        je        ..B1.48
..B1.47:
        fldt      48(%esp)
        movl      72(%esp), %eax
        fstpt     16(%esp)
        subl      %esi, %eax
        fldt      (%ebx)
        addl      $16383, %eax
        fldt      12(%ebx)
        andl      $32767, %eax
        andb      $127, 25(%esp)
        movzwl    24(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        movl      %esi, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        movzwl    8(%ebx), %ecx
        movl      %ecx, 64(%esp)
        jmp       ..B1.49
..B1.48:
        fldt      (%ebx)
..B1.49:
        fldt      88(%esp)
        fld       %st(1)
        movl      28(%ebp), %eax
        fadd      %st(1), %st
        movl      64(%esp), %edx
        movl      44(%esp), %ecx
        andl      $32767, %edx
        andl      $32767, %ecx
        movl      %esi, 24(%eax)
        cmpl      %ecx, %edx
        fstpt     12(%eax)
        fldt      12(%eax)
        movl      %edx, 64(%esp)
        jg        ..B1.54
..B1.50:
        movl      44(%esp), %eax
        andl      $32767, %eax
        cmpl      64(%esp), %eax
        jne       ..B1.55
..B1.51:
        movl      4(%ebx), %eax
        cmpl      92(%esp), %eax
        ja        ..B1.54
..B1.52:
        jne       ..B1.55
..B1.53:
        movl      (%ebx), %eax
        cmpl      88(%esp), %eax
        jbe       ..B1.55
..B1.54:
        fldt      100(%esp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      12(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.56
..B1.55:
        fldt      12(%ebx)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      100(%esp)
        faddp     %st, %st(1)
..B1.56:
        fld       %st(0)
        movl      28(%ebp), %eax
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        jmp       ..B1.63
..B1.57:
        movl      4(%ebx), %edx
        movl      %edx, 28(%esp)
..B1.58:
        cmpl      $0, 28(%esp)
        jne       ..B1.60
..B1.209:
        cmpl      $0, (%ebx)
..B1.59:
        je        ..B1.61
..B1.60:
        movl      72(%esp), %edx
        addl      $-16517, %ecx
        lea       -16383(%edx,%eax), %eax
        cmpl      %eax, %ecx
        jl        ..B1.62
..B1.61:
        movl      28(%ebp), %eax
        movl      88(%esp), %edx
        movl      92(%esp), %ecx
        movl      96(%esp), %edi
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      %edi, 8(%eax)
        movl      100(%esp), %edx
        movl      104(%esp), %ecx
        movl      108(%esp), %edi
        movl      %edx, 12(%eax)
        movl      %ecx, 16(%eax)
        movl      %edi, 20(%eax)
        movl      %esi, 24(%eax)
        jmp       ..B1.63
..B1.62:
        movl      28(%ebp), %eax
        movl      (%ebx), %edx
        movl      %edx, (%eax)
        movl      4(%ebx), %ecx
        movl      %ecx, 4(%eax)
        movl      8(%ebx), %esi
        movl      %esi, 8(%eax)
        movl      12(%ebx), %edi
        movl      %edi, 12(%eax)
        movl      16(%ebx), %edx
        movl      %edx, 16(%eax)
        movl      20(%ebx), %ecx
        movl      %ecx, 20(%eax)
        movl      24(%ebx), %esi
        movl      %esi, 24(%eax)
..B1.63:
        movl      28(%ebp), %eax
        movzwl    8(%eax), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.65
..B1.64:
        cmpl      $24383, %eax
        jle       ..B1.67
..B1.65:
        addl      $4, %esp
        pushl     28(%ebp)
        call      __libm_normalizel_k80
..B1.66:
        addl      $4, %esp
        pushl     28(%ebp)
        call      __libm_normalizel_k80
..B1.67:
        fldt      32(%esp)
        movl      28(%ebp), %eax
        fldt      (%eax)
        fmul      %st, %st(1)
        fld       %st(0)
        movl      24(%eax), %edi
        fsub      %st(2), %st
        addl      %edi, %edi
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        movl      %edi, 140(%esp)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fldt      12(%eax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     116(%esp)
        fldt      116(%esp)
        fsubrp    %st, %st(1)
        movzwl    124(%esp), %ecx
        movl      %ecx, %esi
        andl      $32767, %esi
        fadd      %st(1), %st
        fstpt     128(%esp)
        cmpl      $8383, %esi
        fstpt     48(%esp)
        jl        ..B1.71
..B1.68:
        cmpl      $24383, %esi
        jg        ..B1.71
..B1.69:
        movl      32(%ebp), %eax
        movzwl    8(%eax), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.71
..B1.70:
        cmpl      $24383, %edx
        jle       ..B1.74
..B1.71:
        addl      $4, %esp
        lea       112(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.72:
        addl      $4, %esp
        pushl     32(%ebp)
        call      __libm_normalizel_k80
..B1.73:
        movl      32(%ebp), %eax
        movzwl    124(%esp), %ecx
        movl      %ecx, %esi
        movl      140(%esp), %edi
        andl      $32767, %esi
        movzwl    8(%eax), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B1.74:
        movl      32(%ebp), %ebx
        lea       (%edi,%esi), %eax
        movl      %eax, 64(%esp)
        movl      24(%ebx), %ebx
        movl      %ebx, 68(%esp)
        lea       -134(%ebx,%edx), %ebx
        cmpl      %ebx, %eax
        movl      16(%ebp), %ebx
        jle       ..B1.91
..B1.75:
        movl      120(%esp), %eax
        movl      %eax, 28(%esp)
        movl      68(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      64(%esp), %eax
        jle       ..B1.92
..B1.76:
        cmpl      $0, 28(%esp)
        jne       ..B1.78
..B1.77:
        cmpl      $0, 116(%esp)
        je        ..B1.93
..B1.78:
        movl      32(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B1.80
..B1.79:
        movl      32(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B1.92
..B1.80:
        cmpl      68(%esp), %edi
        je        ..B1.82
..B1.81:
        fldt      48(%esp)
        movl      68(%esp), %edx
        subl      %edx, %edi
        fstpt     16(%esp)
        addl      $16383, %edi
        fldt      116(%esp)
        andl      $32767, %edi
        fldt      128(%esp)
        andb      $127, 25(%esp)
        movzwl    24(%esp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     116(%esp)
        fldt      116(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %edx, 140(%esp)
        fstpt     128(%esp)
        fldt      128(%esp)
        movzwl    124(%esp), %ecx
        jmp       ..B1.83
..B1.82:
        fldt      116(%esp)
        fldt      128(%esp)
..B1.83:
        movl      32(%ebp), %edx
        movl      %ecx, %esi
        movl      44(%esp), %edi
        andl      $32767, %esi
        andl      $32767, %edi
        fld       %st(1)
        fldt      (%edx)
        cmpl      %edi, %esi
        movl      68(%esp), %eax
        fadd      %st, %st(1)
        movl      %eax, 168(%esp)
        movl      %edi, 44(%esp)
        jg        ..B1.88
..B1.84:
        andl      $32767, %ecx
        cmpl      44(%esp), %ecx
        jne       ..B1.89
..B1.85:
        movl      32(%ebp), %eax
        movl      4(%eax), %edx
        cmpl      120(%esp), %edx
        jb        ..B1.88
..B1.86:
        jne       ..B1.89
..B1.87:
        movl      32(%ebp), %edx
        movl      116(%esp), %eax
        cmpl      (%edx), %eax
        jbe       ..B1.89
..B1.88:
        movl      32(%ebp), %eax
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      12(%eax)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B1.90
..B1.89:
        movl      32(%ebp), %eax
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%eax)
        faddp     %st, %st(2)
..B1.90:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     144(%esp)
        fldt      144(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     156(%esp)
        jmp       ..B1.97
..B1.91:
        movl      120(%esp), %eax
        movl      %eax, 28(%esp)
..B1.92:
        cmpl      $0, 28(%esp)
        jne       ..B1.94
..B1.212:
        cmpl      $0, 116(%esp)
..B1.93:
        je        ..B1.95
..B1.94:
        movl      68(%esp), %eax
        lea       -16383(%edi,%esi), %ecx
        lea       -16517(%eax,%edx), %edx
        cmpl      %ecx, %edx
        jl        ..B1.96
..B1.95:
        movl      32(%ebp), %eax
        movl      (%eax), %edx
        movl      4(%eax), %ecx
        movl      %edx, 144(%esp)
        movl      %ecx, 148(%esp)
        movl      8(%eax), %esi
        movl      12(%eax), %edi
        movl      16(%eax), %edx
        movl      20(%eax), %ecx
        movl      24(%eax), %eax
        movl      %esi, 152(%esp)
        movl      %edi, 156(%esp)
        movl      %edx, 160(%esp)
        movl      %ecx, 164(%esp)
        movl      %eax, 168(%esp)
        jmp       ..B1.97
..B1.96:
        movl      116(%esp), %eax
        movl      28(%esp), %edx
        movl      %eax, 144(%esp)
        movl      %edx, 148(%esp)
        movl      124(%esp), %ecx
        movl      128(%esp), %esi
        movl      132(%esp), %edi
        movl      136(%esp), %eax
        movl      140(%esp), %edx
        movl      %ecx, 152(%esp)
        movl      %esi, 156(%esp)
        movl      %edi, 160(%esp)
        movl      %eax, 164(%esp)
        movl      %edx, 168(%esp)
..B1.97:
        movzwl    152(%esp), %eax
        andl      $32767, %eax
        cmpl      $383, %eax
        jl        ..B1.99
..B1.98:
        cmpl      $32383, %eax
        jle       ..B1.100
..B1.99:
        addl      $4, %esp
        lea       140(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.100:
        movl      168(%esp), %edx
        testb     $1, %dl
        jne       ..B1.205
..B1.101:
        fldt      144(%esp)
        movl      60(%esp), %eax
        fstpt     64(%esp)
        fldt      156(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%eax)
        fstpt     48(%esp)
..B1.102:
        fldt      64(%esp)
        fld       %st(0)
        fsqrt     
        fldt      32(%esp)
        movl      36(%ebp), %eax
        fmul      %st(1), %st
        fld       %st(0)
        sarl      $1, %edx
        fsubr     %st(2), %st
        movl      %edx, 24(%eax)
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(2)
        movzwl    8(%ebx), %ecx
        movl      %ecx, %esi
        andl      $32767, %esi
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     12(%eax)
        cmpl      $8383, %esi
        fstpt     64(%esp)
        jl        ..B1.106
..B1.103:
        cmpl      $24383, %esi
        jg        ..B1.106
..B1.104:
        movzwl    96(%esp), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.106
..B1.105:
        cmpl      $24383, %edx
        jle       ..B1.109
..B1.106:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.107:
        addl      $4, %esp
        lea       84(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.108:
        movzwl    8(%ebx), %ecx
        movl      %ecx, %esi
        movzwl    96(%esp), %edx
        andl      $32767, %esi
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B1.109:
        movl      24(%ebx), %edi
        movl      %edi, 80(%esp)
        lea       (%esi,%edi), %eax
        movl      112(%esp), %edi
        movl      %edi, 84(%esp)
        movl      %eax, 76(%esp)
        lea       -134(%edi,%edx), %edi
        cmpl      %edi, %eax
        jle       ..B1.126
..B1.110:
        movl      84(%esp), %edi
        movl      4(%ebx), %eax
        movl      %eax, 28(%esp)
        lea       134(%edi,%edx), %eax
        cmpl      76(%esp), %eax
        jle       ..B1.127
..B1.111:
        cmpl      $0, 28(%esp)
        jne       ..B1.113
..B1.112:
        cmpl      $0, (%ebx)
        je        ..B1.128
..B1.113:
        cmpl      $0, 92(%esp)
        jne       ..B1.115
..B1.114:
        cmpl      $0, 88(%esp)
        je        ..B1.127
..B1.115:
        movl      %edi, %eax
        cmpl      80(%esp), %eax
        je        ..B1.117
..B1.116:
        fldt      64(%esp)
        movl      80(%esp), %eax
        movl      %edi, %ecx
        subl      %ecx, %eax
        fstpt     16(%esp)
        addl      $16383, %eax
        fldt      (%ebx)
        andl      $32767, %eax
        fldt      12(%ebx)
        andb      $127, 25(%esp)
        movzwl    24(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        movzwl    8(%ebx), %ecx
        jmp       ..B1.118
..B1.117:
        fldt      (%ebx)
..B1.118:
        fldt      88(%esp)
        fld       %st(1)
        movl      24(%ebp), %edx
        andl      $32767, %ecx
        movl      44(%esp), %esi
        fsub      %st(1), %st
        andl      $32767, %esi
        movl      %edi, %eax
        cmpl      %esi, %ecx
        movl      %eax, 24(%edx)
        fstpt     12(%edx)
        fldt      12(%edx)
        jg        ..B1.123
..B1.119:
        movl      44(%esp), %eax
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jne       ..B1.124
..B1.120:
        movl      4(%ebx), %eax
        cmpl      92(%esp), %eax
        ja        ..B1.123
..B1.121:
        jne       ..B1.124
..B1.122:
        movl      (%ebx), %eax
        cmpl      88(%esp), %eax
        jbe       ..B1.124
..B1.123:
        fldt      12(%ebx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      100(%esp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        jmp       ..B1.125
..B1.124:
        fldt      100(%esp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      12(%ebx)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
..B1.125:
        fld       %st(1)
        movl      24(%ebp), %eax
        fadd      %st(1), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        jmp       ..B1.132
..B1.126:
        movl      4(%ebx), %eax
        movl      %eax, 28(%esp)
..B1.127:
        cmpl      $0, 28(%esp)
        jne       ..B1.129
..B1.208:
        cmpl      $0, (%ebx)
..B1.128:
        je        ..B1.130
..B1.129:
        movl      84(%esp), %eax
        lea       -16517(%eax,%edx), %ecx
        movl      80(%esp), %edx
        lea       -16383(%edx,%esi), %esi
        cmpl      %esi, %ecx
        jl        ..B1.131
..B1.130:
        fldt      88(%esp)
        movl      24(%ebp), %edx
        fchs      
        movl      84(%esp), %eax
        movl      %eax, 24(%edx)
        fstpt     (%edx)
        fldt      100(%esp)
        fchs      
        fstpt     12(%edx)
        jmp       ..B1.132
..B1.131:
        movl      24(%ebp), %eax
        movl      (%ebx), %edx
        movl      %edx, (%eax)
        movl      4(%ebx), %ecx
        movl      %ecx, 4(%eax)
        movl      8(%ebx), %esi
        movl      %esi, 8(%eax)
        movl      12(%ebx), %edi
        movl      %edi, 12(%eax)
        movl      16(%ebx), %edx
        movl      %edx, 16(%eax)
        movl      20(%ebx), %ecx
        movl      %ecx, 20(%eax)
        movl      24(%ebx), %ebx
        movl      %ebx, 24(%eax)
..B1.132:
        movl      24(%ebp), %eax
        movzwl    8(%eax), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.134
..B1.133:
        cmpl      $24383, %eax
        jle       ..B1.136
..B1.134:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B1.135:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B1.136:
        fldt      32(%esp)
        movl      24(%ebp), %eax
        fldt      (%eax)
        fmul      %st, %st(1)
        fld       %st(0)
        movl      24(%eax), %esi
        fsub      %st(2), %st
        addl      %esi, %esi
        faddp     %st, %st(2)
        fld       %st(1)
        fmul      %st(2), %st
        fld       %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        movl      %esi, 140(%esp)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fldt      12(%eax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     116(%esp)
        fldt      116(%esp)
        fsubrp    %st, %st(1)
        movzwl    124(%esp), %ecx
        movl      %ecx, %ebx
        andl      $32767, %ebx
        fadd      %st(1), %st
        fstpt     128(%esp)
        cmpl      $8383, %ebx
        fstpt     64(%esp)
        jl        ..B1.140
..B1.137:
        cmpl      $24383, %ebx
        jg        ..B1.140
..B1.138:
        movl      32(%ebp), %eax
        movzwl    8(%eax), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.140
..B1.139:
        cmpl      $24383, %edx
        jle       ..B1.143
..B1.140:
        addl      $4, %esp
        lea       112(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.141:
        addl      $4, %esp
        pushl     32(%ebp)
        call      __libm_normalizel_k80
..B1.142:
        movl      32(%ebp), %eax
        movzwl    124(%esp), %ecx
        movl      %ecx, %ebx
        movl      140(%esp), %esi
        andl      $32767, %ebx
        movzwl    8(%eax), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B1.143:
        movl      32(%ebp), %edi
        lea       (%esi,%ebx), %eax
        movl      %eax, 76(%esp)
        movl      24(%edi), %edi
        movl      %edi, 80(%esp)
        lea       -134(%edi,%edx), %edi
        cmpl      %edi, %eax
        jle       ..B1.160
..B1.144:
        movl      80(%esp), %edi
        movl      120(%esp), %eax
        movl      %eax, 28(%esp)
        lea       134(%edi,%edx), %eax
        cmpl      76(%esp), %eax
        jle       ..B1.161
..B1.145:
        cmpl      $0, 28(%esp)
        jne       ..B1.147
..B1.146:
        cmpl      $0, 116(%esp)
        je        ..B1.162
..B1.147:
        movl      32(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B1.149
..B1.148:
        movl      32(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B1.161
..B1.149:
        cmpl      80(%esp), %esi
        je        ..B1.151
..B1.150:
        fldt      64(%esp)
        movl      %edi, %edx
        subl      %edx, %esi
        fstpt     16(%esp)
        addl      $16383, %esi
        fldt      116(%esp)
        andl      $32767, %esi
        fldt      128(%esp)
        andb      $127, 25(%esp)
        movzwl    24(%esp), %eax
        andl      $-32768, %eax
        orl       %esi, %eax
        movw      %ax, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     116(%esp)
        fldt      116(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %edx, 140(%esp)
        fstpt     128(%esp)
        fldt      128(%esp)
        movzwl    124(%esp), %ecx
        jmp       ..B1.152
..B1.151:
        fldt      116(%esp)
        fldt      128(%esp)
..B1.152:
        movl      32(%ebp), %edx
        movl      %ecx, %ebx
        movl      44(%esp), %esi
        andl      $32767, %ebx
        andl      $32767, %esi
        fld       %st(1)
        fldt      (%edx)
        cmpl      %esi, %ebx
        movl      %edi, %eax
        fadd      %st, %st(1)
        movl      %eax, 196(%esp)
        movl      %esi, 44(%esp)
        jg        ..B1.157
..B1.153:
        andl      $32767, %ecx
        cmpl      44(%esp), %ecx
        jne       ..B1.158
..B1.154:
        movl      32(%ebp), %eax
        movl      4(%eax), %edx
        cmpl      120(%esp), %edx
        jb        ..B1.157
..B1.155:
        jne       ..B1.158
..B1.156:
        movl      32(%ebp), %edx
        movl      116(%esp), %eax
        cmpl      (%edx), %eax
        jbe       ..B1.158
..B1.157:
        movl      32(%ebp), %eax
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      12(%eax)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B1.159
..B1.158:
        movl      32(%ebp), %eax
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%eax)
        faddp     %st, %st(2)
..B1.159:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     172(%esp)
        fldt      172(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     184(%esp)
        jmp       ..B1.166
..B1.160:
        movl      120(%esp), %eax
        movl      %eax, 28(%esp)
..B1.161:
        cmpl      $0, 28(%esp)
        jne       ..B1.163
..B1.211:
        cmpl      $0, 116(%esp)
..B1.162:
        je        ..B1.164
..B1.163:
        movl      80(%esp), %eax
        lea       -16383(%esi,%ebx), %ecx
        lea       -16517(%eax,%edx), %edx
        cmpl      %ecx, %edx
        jl        ..B1.165
..B1.164:
        movl      32(%ebp), %eax
        movl      (%eax), %edx
        movl      %edx, 172(%esp)
        movl      4(%eax), %ecx
        movl      8(%eax), %ebx
        movl      12(%eax), %esi
        movl      16(%eax), %edi
        movl      20(%eax), %edx
        movl      24(%eax), %eax
        movl      %ecx, 176(%esp)
        movl      %ebx, 180(%esp)
        movl      %esi, 184(%esp)
        movl      %edi, 188(%esp)
        movl      %edx, 192(%esp)
        movl      %eax, 196(%esp)
        jmp       ..B1.166
..B1.165:
        movl      116(%esp), %eax
        movl      %eax, 172(%esp)
        movl      28(%esp), %edx
        movl      124(%esp), %ecx
        movl      128(%esp), %ebx
        movl      132(%esp), %esi
        movl      136(%esp), %edi
        movl      140(%esp), %eax
        movl      %edx, 176(%esp)
        movl      %ecx, 180(%esp)
        movl      %ebx, 184(%esp)
        movl      %esi, 188(%esp)
        movl      %edi, 192(%esp)
        movl      %eax, 196(%esp)
..B1.166:
        movzwl    180(%esp), %eax
        andl      $32767, %eax
        cmpl      $383, %eax
        jl        ..B1.168
..B1.167:
        cmpl      $32383, %eax
        jle       ..B1.169
..B1.168:
        addl      $4, %esp
        lea       168(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.169:
        movl      196(%esp), %ecx
        testb     $1, %cl
        jne       ..B1.204
..B1.170:
        fldt      172(%esp)
        fstpt     64(%esp)
        fldt      184(%esp)
..B1.171:
        fldt      64(%esp)
        fld       %st(0)
        fsqrt     
        fldt      32(%esp)
        fmul      %st(1), %st
        fld       %st(0)
        movl      40(%ebp), %ebx
        fsubr     %st(2), %st
        movl      36(%ebp), %eax
        faddp     %st, %st(1)
        fld       %st(0)
        sarl      $1, %ecx
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%ebx)
        fldt      (%ebx)
        fsubrp    %st, %st(2)
        movzwl    8(%eax), %edx
        fadd      %st, %st(1)
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        fxch      %st(1)
        fstpt     12(%ebx)
        cmpl      $8383, %edx
        fstpt     64(%esp)
        jl        ..B1.175
..B1.172:
        cmpl      $24383, %edx
        jg        ..B1.175
..B1.173:
        movzwl    8(%ebx), %eax
        movl      %eax, 32(%esp)
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.175
..B1.174:
        cmpl      $24383, %eax
        jle       ..B1.178
..B1.175:
        addl      $4, %esp
        pushl     36(%ebp)
        call      __libm_normalizel_k80
..B1.176:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.177:
        movl      36(%ebp), %edx
        movzwl    8(%ebx), %eax
        movl      24(%ebx), %ecx
        movzwl    8(%edx), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        movl      %eax, 32(%esp)
        andl      $32767, %eax
..B1.178:
        movl      36(%ebp), %esi
        movl      24(%esi), %edi
        movl      %edi, 40(%esp)
        lea       (%edx,%edi), %esi
        movl      %esi, 36(%esp)
        lea       -134(%ecx,%eax), %edi
        cmpl      %edi, %esi
        jle       ..B1.195
..B1.179:
        movl      36(%ebp), %esi
        movl      4(%esi), %edi
        lea       134(%ecx,%eax), %esi
        movl      %edi, 28(%esp)
        cmpl      36(%esp), %esi
        jle       ..B1.196
..B1.180:
        cmpl      $0, 28(%esp)
        jne       ..B1.182
..B1.181:
        movl      36(%ebp), %esi
        cmpl      $0, (%esi)
        je        ..B1.197
..B1.182:
        cmpl      $0, 4(%ebx)
        jne       ..B1.184
..B1.183:
        cmpl      $0, (%ebx)
        je        ..B1.196
..B1.184:
        cmpl      40(%esp), %ecx
        je        ..B1.186
..B1.185:
        fldt      64(%esp)
        movl      40(%esp), %eax
        fstpt     16(%esp)
        subl      %ecx, %eax
        andb      $127, 25(%esp)
        addl      $16383, %eax
        movl      36(%ebp), %esi
        andl      $32767, %eax
        movzwl    24(%esp), %edx
        andl      $-32768, %edx
        fldt      (%esi)
        orl       %eax, %edx
        fldt      12(%esi)
        movw      %dx, 24(%esp)
        movl      $-2147483648, 20(%esp)
        movl      $0, 16(%esp)
        fldt      16(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%esi)
        fxch      %st(2)
        fstpt     (%esi)
        fldt      (%esi)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%esi)
        movzwl    8(%esi), %edi
        movzwl    8(%ebx), %eax
        movl      24(%ebx), %ecx
        movl      %edi, 44(%esp)
        movl      %eax, 32(%esp)
        jmp       ..B1.187
..B1.186:
        movl      36(%ebp), %eax
        fldt      (%eax)
..B1.187:
        fldt      (%ebx)
        fld       %st(1)
        movl      44(%ebp), %eax
        fadd      %st(1), %st
        movl      %ecx, 24(%eax)
        movl      44(%esp), %edx
        movl      32(%esp), %ecx
        andl      $32767, %edx
        andl      $32767, %ecx
        fstpt     12(%eax)
        fldt      12(%eax)
        cmpl      %ecx, %edx
        jg        ..B1.192
..B1.188:
        jne       ..B1.193
..B1.189:
        movl      36(%ebp), %eax
        movl      4(%eax), %edx
        cmpl      4(%ebx), %edx
        ja        ..B1.192
..B1.190:
        jne       ..B1.193
..B1.191:
        movl      36(%ebp), %eax
        movl      (%eax), %edx
        cmpl      (%ebx), %edx
        jbe       ..B1.193
..B1.192:
        fldt      12(%ebx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        movl      36(%ebp), %eax
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      12(%eax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        jmp       ..B1.194
..B1.193:
        movl      36(%ebp), %eax
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      12(%eax)
        faddp     %st, %st(1)
        fldt      12(%ebx)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
..B1.194:
        fld       %st(0)
        movl      44(%ebp), %eax
        fadd      %st(2), %st
        fstpt     (%eax)
        fldt      (%eax)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%eax)
        jmp       ..B1.201
..B1.195:
        movl      36(%ebp), %esi
        movl      4(%esi), %edi
        movl      %edi, 28(%esp)
..B1.196:
        cmpl      $0, 28(%esp)
        jne       ..B1.198
..B1.213:
        movl      36(%ebp), %esi
        cmpl      $0, (%esi)
..B1.197:
        je        ..B1.199
..B1.198:
        lea       -16517(%ecx,%eax), %ecx
        movl      40(%esp), %eax
        lea       -16383(%eax,%edx), %edx
        cmpl      %edx, %ecx
        jl        ..B1.200
..B1.199:
        movl      44(%ebp), %eax
        movl      (%ebx), %edx
        movl      %edx, (%eax)
        movl      4(%ebx), %ecx
        movl      %ecx, 4(%eax)
        movl      8(%ebx), %esi
        movl      %esi, 8(%eax)
        movl      12(%ebx), %edi
        movl      %edi, 12(%eax)
        movl      16(%ebx), %edx
        movl      %edx, 16(%eax)
        movl      20(%ebx), %ecx
        movl      %ecx, 20(%eax)
        movl      24(%ebx), %ebx
        movl      %ebx, 24(%eax)
        jmp       ..B1.201
..B1.200:
        movl      36(%ebp), %eax
        movl      44(%ebp), %edx
        movl      (%eax), %ecx
        movl      %ecx, (%edx)
        movl      4(%eax), %ebx
        movl      %ebx, 4(%edx)
        movl      8(%eax), %esi
        movl      %esi, 8(%edx)
        movl      12(%eax), %edi
        movl      %edi, 12(%edx)
        movl      16(%eax), %ecx
        movl      %ecx, 16(%edx)
        movl      20(%eax), %ebx
        movl      %ebx, 20(%edx)
        movl      24(%eax), %eax
        movl      %eax, 24(%edx)
..B1.201:
        movl      200(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.203
..B1.202:
        addl      $212, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.203:
        movl      60(%esp), %ebx
        call      __stack_chk_fail@PLT
..B1.204:
        fldt      172(%esp)
        incl      %ecx
        fldt      48(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fstpt     172(%esp)
        fldt      184(%esp)
        fmulp     %st, %st(1)
        movl      %ecx, 196(%esp)
        fstpt     184(%esp)
        fldt      184(%esp)
        jmp       ..B1.171
..B1.205:
        fldt      144(%esp)
        incl      %edx
        movl      60(%esp), %eax
        movl      %edx, 168(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%eax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fstpt     144(%esp)
        fstpt     48(%esp)
        fldt      48(%esp)
        fldt      156(%esp)
        fmulp     %st, %st(1)
        fstpt     156(%esp)
        fldt      156(%esp)
        jmp       ..B1.102
        .align    16,0x90
	.type	__libm_carctrigprepare_k80,@function
	.size	__libm_carctrigprepare_k80,.-__libm_carctrigprepare_k80
	.data
# -- End  __libm_carctrigprepare_k80
	.text
# -- Begin  __libm_recacosl_k80
	.text
       .align    16,0x90
	.hidden __libm_recacosl_k80
	.globl __libm_recacosl_k80
__libm_recacosl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
# parameter 4: 20 + %ebp
# parameter 5: 24 + %ebp
# parameter 6: 28 + %ebp
# parameter 7: 32 + %ebp
# parameter 8: 36 + %ebp
# parameter 9: 40 + %ebp
# parameter 10: 44 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $500, %esp
        movl      16(%ebp), %esi
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 488(%esp)
        movzwl    8(%esi), %eax
        andl      $32767, %eax
        movl      44(%ebp), %edi
        cmpl      $8383, %eax
        jl        ..B2.5
..B2.2:
        cmpl      $24383, %eax
        jg        ..B2.5
..B2.3:
        movzwl    8(%edi), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.5
..B2.4:
        cmpl      $24383, %eax
        jle       ..B2.7
..B2.5:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B2.6:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B2.7:
        fldt      (%esi)
        fld       %st(0)
        fldt      (%edi)
        fdivr     %st, %st(1)
        fld       %st(0)
        call      ..L4
..L4:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%eax), %eax
        fld       %st(1)
        movl      24(%esi), %ebx
        subl      24(%edi), %ebx
        fldt      36+_CONSTANTS@GOTOFF(%eax)
        fld       %st(0)
        fmul      %st(5), %st
        lea       1(%ebx), %edx
        fld       %st(0)
        movl      %eax, 92(%esp)
        fsubr     %st(6), %st
        movl      %edx, 484(%esp)
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fsub      %st(4), %st
        fxch      %st(3)
        fstpt     80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      12(%esi)
        faddp     %st, %st(3)
        fldt      12(%edi)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     460(%esp)
        fldt      460(%esp)
        fsubrp    %st, %st(1)
        movzwl    468(%esp), %ecx
        fadd      %st(1), %st
        andl      $32767, %ecx
        fstpt     472(%esp)
        fstpt     48(%esp)
        lea       1(%ecx,%ebx), %eax
        cmpl      $16382, %eax
        jl        ..B2.200
..B2.8:
        movl      8(%ebp), %eax
        movl      40(%ebp), %ebx
        movzwl    8(%eax), %edx
        andl      $32767, %edx
        cmpl      $16383, %edx
        jl        ..B2.39
..B2.9:
        movl      32(%ebp), %eax
        movl      (%eax), %edx
        movl      %edx, 264(%esp)
        movl      8(%eax), %edx
        movl      4(%eax), %ecx
        movl      %edx, 272(%esp)
        movl      16(%eax), %edx
        movl      %ecx, 268(%esp)
        movl      12(%eax), %ecx
        movl      %edx, 280(%esp)
        movzwl    8(%ebx), %edx
        movl      %ecx, 276(%esp)
        movl      20(%eax), %ecx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movl      24(%eax), %eax
        cmpl      $8383, %edx
        movl      %ecx, 284(%esp)
        movl      %eax, 288(%esp)
        jl        ..B2.13
..B2.10:
        cmpl      $24383, %edx
        jg        ..B2.13
..B2.11:
        movl      24(%ebp), %eax
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
        cmpl      $8383, %ecx
        jl        ..B2.13
..B2.12:
        cmpl      $24383, 40(%esp)
        jle       ..B2.16
..B2.13:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.14:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B2.15:
        movl      24(%ebp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
..B2.16:
        movl      24(%ebp), %ecx
        movl      24(%ecx), %eax
        movl      40(%esp), %ecx
        movl      %eax, 64(%esp)
        lea       -134(%eax,%ecx), %eax
        movl      24(%ebx), %ecx
        movl      %ecx, 60(%esp)
        addl      %edx, %ecx
        movl      %ecx, 36(%esp)
        cmpl      %ecx, %eax
        jge       ..B2.33
..B2.17:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       134(%ecx,%eax), %ecx
        cmpl      36(%esp), %ecx
        jle       ..B2.34
..B2.18:
        cmpl      $0, 32(%esp)
        jne       ..B2.20
..B2.19:
        cmpl      $0, (%ebx)
        je        ..B2.35
..B2.20:
        movl      24(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B2.22
..B2.21:
        movl      24(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B2.34
..B2.22:
        movl      64(%esp), %eax
        cmpl      60(%esp), %eax
        je        ..B2.24
..B2.23:
        fldt      48(%esp)
        movl      60(%esp), %eax
        movl      64(%esp), %ecx
        subl      %ecx, %eax
        fstpt     32(%esp)
        addl      $16383, %eax
        fldt      (%ebx)
        andl      $32767, %eax
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        movl      24(%ebp), %ecx
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fstpt     48(%esp)
        fmulp     %st, %st(1)
        movl      24(%ecx), %eax
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      %eax, 64(%esp)
        movzwl    8(%ebx), %edx
        movzwl    8(%ecx), %eax
        movl      %edx, 68(%esp)
        movl      %eax, 44(%esp)
        jmp       ..B2.25
..B2.24:
        fldt      (%ebx)
        fstpt     48(%esp)
        fldt      12(%ebx)
..B2.25:
        fldt      48(%esp)
        movl      24(%ebp), %edx
        movl      64(%esp), %eax
        movl      %eax, 316(%esp)
        fldt      (%edx)
        movl      68(%esp), %ecx
        fadd      %st, %st(1)
        movl      44(%esp), %eax
        andl      $32767, %ecx
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jg        ..B2.30
..B2.26:
        jne       ..B2.31
..B2.27:
        movl      24(%ebp), %edx
        movl      4(%ebx), %eax
        cmpl      4(%edx), %eax
        ja        ..B2.30
..B2.28:
        jne       ..B2.31
..B2.29:
        movl      24(%ebp), %edx
        movl      (%ebx), %eax
        cmpl      (%edx), %eax
        jbe       ..B2.31
..B2.30:
        fldt      48(%esp)
        movl      24(%ebp), %eax
        fsub      %st(2), %st
        faddp     %st, %st(1)
        fldt      12(%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B2.32
..B2.31:
        fldt      48(%esp)
        fxch      %st(1)
        fsub      %st(2), %st
        movl      24(%ebp), %eax
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      12(%eax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B2.32:
        fldt      48(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     292(%esp)
        fldt      292(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     304(%esp)
        jmp       ..B2.69
..B2.33:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
..B2.34:
        cmpl      $0, 32(%esp)
        jne       ..B2.36
..B2.241:
        cmpl      $0, (%ebx)
..B2.35:
        je        ..B2.37
..B2.36:
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       -16517(%ecx,%eax), %eax
        movl      60(%esp), %ecx
        lea       -16383(%ecx,%edx), %edx
        cmpl      %edx, %eax
        jl        ..B2.38
..B2.37:
        movl      24(%ebp), %eax
        movl      (%eax), %edx
        movl      4(%eax), %ecx
        movl      8(%eax), %ebx
        movl      %edx, 292(%esp)
        movl      %ecx, 296(%esp)
        movl      %ebx, 300(%esp)
        movl      12(%eax), %edx
        movl      16(%eax), %ecx
        movl      20(%eax), %ebx
        movl      24(%eax), %eax
        movl      %edx, 304(%esp)
        movl      %ecx, 308(%esp)
        movl      %ebx, 312(%esp)
        movl      %eax, 316(%esp)
        jmp       ..B2.69
..B2.38:
        movl      (%ebx), %eax
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      %eax, 292(%esp)
        movl      %edx, 296(%esp)
        movl      %ecx, 300(%esp)
        movl      12(%ebx), %eax
        movl      16(%ebx), %edx
        movl      20(%ebx), %ecx
        movl      24(%ebx), %ebx
        movl      %eax, 304(%esp)
        movl      %edx, 308(%esp)
        movl      %ecx, 312(%esp)
        movl      %ebx, 316(%esp)
        jmp       ..B2.69
..B2.39:
        movl      92(%esp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        fldt      _CONSTANTS@GOTOFF(%eax)
        cmpl      $8383, %edx
        movl      $0, 316(%esp)
        fstpt     292(%esp)
        fldt      24+_CONSTANTS@GOTOFF(%eax)
        fstpt     304(%esp)
        jl        ..B2.43
..B2.40:
        cmpl      $24383, %edx
        jg        ..B2.43
..B2.41:
        movl      24(%ebp), %eax
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
        cmpl      $8383, %ecx
        jl        ..B2.43
..B2.42:
        cmpl      $24383, 40(%esp)
        jle       ..B2.46
..B2.43:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.44:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B2.45:
        movl      24(%ebp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
..B2.46:
        movl      24(%ebp), %ecx
        movl      24(%ecx), %eax
        movl      40(%esp), %ecx
        movl      %eax, 64(%esp)
        lea       -134(%eax,%ecx), %eax
        movl      24(%ebx), %ecx
        movl      %ecx, 60(%esp)
        addl      %edx, %ecx
        movl      %ecx, 36(%esp)
        cmpl      %ecx, %eax
        jge       ..B2.63
..B2.47:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       134(%ecx,%eax), %ecx
        cmpl      36(%esp), %ecx
        jle       ..B2.64
..B2.48:
        cmpl      $0, 32(%esp)
        jne       ..B2.50
..B2.49:
        cmpl      $0, (%ebx)
        je        ..B2.65
..B2.50:
        movl      24(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B2.52
..B2.51:
        movl      24(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B2.64
..B2.52:
        movl      64(%esp), %eax
        cmpl      60(%esp), %eax
        je        ..B2.54
..B2.53:
        fldt      48(%esp)
        movl      60(%esp), %eax
        movl      64(%esp), %ecx
        subl      %ecx, %eax
        fstpt     32(%esp)
        addl      $16383, %eax
        fldt      (%ebx)
        andl      $32767, %eax
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        movl      24(%ebp), %ecx
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      24(%ecx), %eax
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      %eax, 64(%esp)
        movzwl    8(%ebx), %edx
        movzwl    8(%ecx), %eax
        fstpt     48(%esp)
        movl      %edx, 68(%esp)
        movl      %eax, 44(%esp)
        jmp       ..B2.55
..B2.54:
        fldt      (%ebx)
        fldt      12(%ebx)
        fstpt     48(%esp)
..B2.55:
        movl      24(%ebp), %edx
        fld       %st(0)
        movl      64(%esp), %eax
        movl      %eax, 288(%esp)
        fldt      (%edx)
        movl      68(%esp), %ecx
        fsubr     %st, %st(1)
        movl      44(%esp), %eax
        andl      $32767, %ecx
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jg        ..B2.60
..B2.56:
        jne       ..B2.61
..B2.57:
        movl      24(%ebp), %edx
        movl      4(%ebx), %eax
        cmpl      4(%edx), %eax
        ja        ..B2.60
..B2.58:
        jne       ..B2.61
..B2.59:
        movl      24(%ebp), %edx
        movl      (%ebx), %eax
        cmpl      (%edx), %eax
        jbe       ..B2.61
..B2.60:
        fldt      48(%esp)
        fxch      %st(2)
        fsubr     %st, %st(3)
        movl      24(%ebp), %eax
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      12(%eax)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B2.62
..B2.61:
        movl      24(%ebp), %eax
        fadd      %st(1), %st
        fsubrp    %st, %st(2)
        fldt      48(%esp)
        faddp     %st, %st(2)
        fldt      12(%eax)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B2.62:
        fldt      48(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     264(%esp)
        fldt      264(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     276(%esp)
        jmp       ..B2.69
..B2.63:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
..B2.64:
        cmpl      $0, 32(%esp)
        jne       ..B2.66
..B2.240:
        cmpl      $0, (%ebx)
..B2.65:
        je        ..B2.67
..B2.66:
        movl      64(%esp), %eax
        movl      40(%esp), %ecx
        movl      60(%esp), %ebx
        lea       -16517(%eax,%ecx), %eax
        lea       -16383(%ebx,%edx), %edx
        movl      40(%ebp), %ebx
        cmpl      %edx, %eax
        jl        ..B2.68
..B2.67:
        movl      24(%ebp), %edx
        movl      64(%esp), %eax
        movl      %eax, 288(%esp)
        fldt      (%edx)
        fchs      
        fstpt     264(%esp)
        fldt      12(%edx)
        fchs      
        fstpt     276(%esp)
        jmp       ..B2.69
..B2.68:
        movl      (%ebx), %eax
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      %eax, 264(%esp)
        movl      %edx, 268(%esp)
        movl      %ecx, 272(%esp)
        movl      12(%ebx), %eax
        movl      16(%ebx), %edx
        movl      20(%ebx), %ecx
        movl      24(%ebx), %ebx
        movl      %eax, 276(%esp)
        movl      %edx, 280(%esp)
        movl      %ecx, 284(%esp)
        movl      %ebx, 288(%esp)
..B2.69:
        movzwl    8(%esi), %eax
        movl      %eax, 68(%esp)
        andl      $32767, %eax
        movl      24(%esi), %ecx
        incl      %ecx
        movl      %ecx, 24(%esi)
        cmpl      $8383, %eax
        movl      %eax, 76(%esp)
        jl        ..B2.73
..B2.70:
        cmpl      $24383, 76(%esp)
        jg        ..B2.73
..B2.71:
        movzwl    8(%edi), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        movl      %eax, 72(%esp)
        cmpl      $8383, %eax
        jl        ..B2.73
..B2.72:
        cmpl      $24383, 72(%esp)
        jle       ..B2.76
..B2.73:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B2.74:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B2.75:
        movzwl    8(%esi), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movl      %edx, 76(%esp)
        movzwl    8(%edi), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        movl      %eax, 72(%esp)
        movl      24(%esi), %ecx
..B2.76:
        movl      76(%esp), %ebx
        movl      24(%edi), %edi
        movl      %edi, 64(%esp)
        lea       (%ecx,%ebx), %eax
        movl      72(%esp), %ebx
        movl      %eax, 60(%esp)
        lea       -134(%edi,%ebx), %edi
        cmpl      %edi, %eax
        movl      44(%ebp), %edi
        jle       ..B2.93
..B2.77:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
        movl      64(%esp), %ebx
        movl      72(%esp), %eax
        lea       134(%ebx,%eax), %ebx
        cmpl      60(%esp), %ebx
        jle       ..B2.94
..B2.78:
        cmpl      $0, 44(%esp)
        jne       ..B2.80
..B2.79:
        cmpl      $0, (%esi)
        je        ..B2.95
..B2.80:
        cmpl      $0, 4(%edi)
        jne       ..B2.82
..B2.81:
        cmpl      $0, (%edi)
        je        ..B2.94
..B2.82:
        cmpl      64(%esp), %ecx
        je        ..B2.84
..B2.83:
        fldt      48(%esp)
        movl      64(%esp), %edx
        subl      %edx, %ecx
        fstpt     32(%esp)
        addl      $16383, %ecx
        fldt      (%esi)
        andl      $32767, %ecx
        fldt      12(%esi)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movl      %edx, %ecx
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 24(%esi)
        fxch      %st(2)
        fstpt     (%esi)
        fldt      (%esi)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%esi)
        fldt      12(%esi)
        movl      24(%edi), %ebx
        movzwl    8(%esi), %eax
        movl      %ebx, 64(%esp)
        movl      %eax, 68(%esp)
        movzwl    8(%edi), %edx
        jmp       ..B2.85
..B2.84:
        fldt      (%esi)
        fldt      12(%esi)
..B2.85:
        fldt      (%edi)
        fld       %st(2)
        movl      64(%esp), %eax
        fadd      %st(1), %st
        movl      %eax, 344(%esp)
        movl      %edx, %eax
        movl      68(%esp), %ebx
        andl      $32767, %eax
        andl      $32767, %ebx
        cmpl      %eax, %ebx
        jg        ..B2.90
..B2.86:
        jne       ..B2.91
..B2.87:
        movl      4(%esi), %eax
        cmpl      4(%edi), %eax
        ja        ..B2.90
..B2.88:
        jne       ..B2.91
..B2.89:
        movl      (%esi), %eax
        cmpl      (%edi), %eax
        jbe       ..B2.91
..B2.90:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      12(%edi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B2.92
..B2.91:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%edi)
        faddp     %st, %st(2)
..B2.92:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     320(%esp)
        fldt      320(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     332(%esp)
        jmp       ..B2.99
..B2.93:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
..B2.94:
        cmpl      $0, 44(%esp)
        jne       ..B2.96
..B2.242:
        cmpl      $0, (%esi)
..B2.95:
        je        ..B2.97
..B2.96:
        movl      64(%esp), %ebx
        movl      72(%esp), %eax
        lea       -16517(%ebx,%eax), %eax
        movl      76(%esp), %ebx
        lea       -16383(%ecx,%ebx), %ebx
        cmpl      %ebx, %eax
        jl        ..B2.98
..B2.97:
        movl      (%edi), %eax
        movl      %eax, 320(%esp)
        movl      4(%edi), %ebx
        movl      8(%edi), %eax
        movl      %ebx, 324(%esp)
        movl      %eax, 328(%esp)
        movl      12(%edi), %ebx
        movl      16(%edi), %eax
        movl      %ebx, 332(%esp)
        movl      %eax, 336(%esp)
        movl      20(%edi), %ebx
        movl      24(%edi), %eax
        movl      %ebx, 340(%esp)
        movl      %eax, 344(%esp)
        jmp       ..B2.99
..B2.98:
        movl      (%esi), %eax
        movl      %eax, 320(%esp)
        movl      4(%esi), %ebx
        movl      8(%esi), %eax
        movl      %ebx, 324(%esp)
        movl      %eax, 328(%esp)
        movl      12(%esi), %ebx
        movl      16(%esi), %eax
        movl      %ebx, 332(%esp)
        movl      %eax, 336(%esp)
        movl      20(%esi), %ebx
        movl      24(%esi), %eax
        movl      %ebx, 340(%esp)
        movl      %eax, 344(%esp)
..B2.99:
        movzwl    328(%esp), %eax
        decl      %ecx
        andl      $32767, %eax
        movl      %ecx, 24(%esi)
        cmpl      $8383, %eax
        jl        ..B2.103
..B2.100:
        cmpl      $24383, %eax
        jg        ..B2.103
..B2.101:
        movzwl    272(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.103
..B2.102:
        cmpl      $24383, %eax
        jle       ..B2.105
..B2.103:
        addl      $4, %esp
        lea       316(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.104:
        addl      $4, %esp
        lea       260(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
        movzwl    8(%edi), %edx
..B2.105:
        fldt      320(%esp)
        andl      $32767, %edx
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      344(%esp), %eax
        fsubr     %st, %st(3)
        addl      288(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %edx
        movl      %eax, 372(%esp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      264(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      276(%esp)
        fmulp     %st, %st(4)
        fldt      332(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     348(%esp)
        fldt      348(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     360(%esp)
        jl        ..B2.109
..B2.106:
        cmpl      $24383, %edx
        jg        ..B2.109
..B2.107:
        movzwl    300(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.109
..B2.108:
        cmpl      $24383, %eax
        jle       ..B2.111
..B2.109:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B2.110:
        addl      $4, %esp
        lea       288(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.111:
        fldt      (%edi)
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      36(%ebp), %ebx
        fsubr     %st, %st(3)
        movl      316(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      292(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        addl      24(%edi), %eax
        fsub      %st(3), %st
        movl      28(%ebp), %esi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        movl      %eax, 400(%esp)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      304(%esp)
        fmulp     %st, %st(4)
        fldt      12(%edi)
        fmulp     %st, %st(1)
        movzwl    8(%ebx), %edi
        movl      %edi, %ecx
        andl      $32767, %ecx
        faddp     %st, %st(3)
        cmpl      $8383, %ecx
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     376(%esp)
        fldt      376(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     388(%esp)
        fstpt     48(%esp)
        jl        ..B2.115
..B2.112:
        cmpl      $24383, %ecx
        jg        ..B2.115
..B2.113:
        movzwl    8(%esi), %edx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B2.115
..B2.114:
        cmpl      $24383, %edx
        jle       ..B2.118
..B2.115:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.116:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B2.117:
        movzwl    8(%ebx), %edi
        movl      %edi, %ecx
        movzwl    8(%esi), %edx
        andl      $32767, %ecx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
..B2.118:
        movl      24(%esi), %eax
        movl      24(%ebx), %ebx
        movl      %eax, 72(%esp)
        movl      %ebx, 68(%esp)
        lea       -134(%eax,%edx), %eax
        addl      %ecx, %ebx
        movl      %ebx, 64(%esp)
        cmpl      %eax, %ebx
        movl      36(%ebp), %ebx
        jle       ..B2.135
..B2.119:
        movl      4(%ebx), %eax
        movl      %eax, 44(%esp)
        movl      72(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      64(%esp), %eax
        jle       ..B2.136
..B2.120:
        cmpl      $0, 44(%esp)
        jne       ..B2.122
..B2.121:
        cmpl      $0, (%ebx)
        je        ..B2.137
..B2.122:
        cmpl      $0, 4(%esi)
        jne       ..B2.124
..B2.123:
        cmpl      $0, (%esi)
        je        ..B2.136
..B2.124:
        movl      72(%esp), %eax
        cmpl      68(%esp), %eax
        je        ..B2.126
..B2.125:
        fldt      48(%esp)
        movl      68(%esp), %edi
        movl      %eax, %edx
        subl      %edx, %edi
        fstpt     32(%esp)
        addl      $16383, %edi
        fldt      (%ebx)
        andl      $32767, %edi
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      24(%esi), %ecx
        movzwl    8(%esi), %eax
        movl      %ecx, 72(%esp)
        movzwl    8(%ebx), %edi
        movl      %eax, 60(%esp)
        jmp       ..B2.127
..B2.126:
        fldt      (%ebx)
        fldt      12(%ebx)
..B2.127:
        fldt      (%esi)
        andl      $32767, %edi
        movl      60(%esp), %edx
        fld       %st(2)
        andl      $32767, %edx
        fadd      %st(1), %st
        movl      72(%esp), %eax
        cmpl      %edx, %edi
        movl      %eax, 428(%esp)
        jg        ..B2.132
..B2.128:
        jne       ..B2.133
..B2.129:
        movl      4(%ebx), %eax
        cmpl      4(%esi), %eax
        ja        ..B2.132
..B2.130:
        jne       ..B2.133
..B2.131:
        movl      (%ebx), %eax
        cmpl      (%esi), %eax
        jbe       ..B2.133
..B2.132:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      12(%esi)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.134
..B2.133:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      12(%esi)
        faddp     %st, %st(1)
..B2.134:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     404(%esp)
        fldt      404(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     416(%esp)
        jmp       ..B2.141
..B2.135:
        movl      4(%ebx), %eax
        movl      %eax, 44(%esp)
..B2.136:
        cmpl      $0, 44(%esp)
        jne       ..B2.138
..B2.243:
        cmpl      $0, (%ebx)
..B2.137:
        je        ..B2.139
..B2.138:
        movl      72(%esp), %eax
        lea       -16517(%eax,%edx), %edi
        movl      68(%esp), %edx
        lea       -16383(%edx,%ecx), %ecx
        cmpl      %ecx, %edi
        jl        ..B2.140
..B2.139:
        movl      (%esi), %eax
        movl      %eax, 404(%esp)
        movl      4(%esi), %edx
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      16(%esi), %edi
        movl      20(%esi), %eax
        movl      24(%esi), %esi
        movl      %edx, 408(%esp)
        movl      %ecx, 412(%esp)
        movl      %ebx, 416(%esp)
        movl      %edi, 420(%esp)
        movl      %eax, 424(%esp)
        movl      %esi, 428(%esp)
        jmp       ..B2.141
..B2.140:
        movl      (%ebx), %eax
        movl      %eax, 404(%esp)
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      12(%ebx), %esi
        movl      16(%ebx), %edi
        movl      20(%ebx), %eax
        movl      24(%ebx), %ebx
        movl      %edx, 408(%esp)
        movl      %ecx, 412(%esp)
        movl      %esi, 416(%esp)
        movl      %edi, 420(%esp)
        movl      %eax, 424(%esp)
        movl      %ebx, 428(%esp)
..B2.141:
        movzwl    384(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.145
..B2.142:
        cmpl      $24383, %eax
        jg        ..B2.145
..B2.143:
        movzwl    412(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.145
..B2.144:
        cmpl      $24383, %eax
        jle       ..B2.147
..B2.145:
        addl      $4, %esp
        lea       372(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.146:
        addl      $4, %esp
        lea       400(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.147:
        fldt      376(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      400(%esp), %edx
        fsubr     %st, %st(3)
        movzwl    356(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      428(%esp), %edx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      404(%esp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %edx, 456(%esp)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      416(%esp)
        fmulp     %st, %st(4)
        fldt      388(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     432(%esp)
        fldt      432(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     444(%esp)
        fldt      444(%esp)
        jl        ..B2.151
..B2.148:
        cmpl      $24383, %eax
        jg        ..B2.151
..B2.149:
        movzwl    440(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.151
..B2.150:
        cmpl      $24383, %eax
        jle       ..B2.154
..B2.151:
        fstp      %st(1)
        fstp      %st(0)
        addl      $4, %esp
        lea       344(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.152:
        addl      $4, %esp
        lea       428(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.153:
        fldt      432(%esp)
        fldt      444(%esp)
        movl      456(%esp), %edx
..B2.154:
        fldt      348(%esp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      372(%esp), %eax
        fsubr     %st(4), %st
        subl      %edx, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        movl      8(%ebp), %edx
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(3), %st
        movzbl    9(%edx), %ecx
        testl     $128, %ecx
        fsubr     %st, %st(7)
        faddp     %st, %st(7)
        fxch      %st(2)
        fsub      %st(6), %st
        fld       %st(2)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(7)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      360(%esp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      64(%esp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        fadd      %st, %st(2)
        fstpt     48(%esp)
        jne       ..B2.163
..B2.155:
        fstpt     32(%esp)
        fldt      32(%esp)
        decl      %eax
        movzwl    40(%esp), %edx
        andl      $32767, %edx
        movl      %eax, 56(%esp)
        cmpl      $383, %edx
        fxch      %st(1)
        fstpt     44(%esp)
        fldt      44(%esp)
        jl        ..B2.157
..B2.156:
        cmpl      $32383, %edx
        jle       ..B2.159
..B2.157:
        fstp      %st(0)
        fstp      %st(0)
        addl      $4, %esp
        lea       28(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.158:
        fldt      32(%esp)
        fldt      44(%esp)
        movl      56(%esp), %eax
..B2.159:
        testb     $1, %al
        jne       ..B2.234
..B2.160:
        movl      92(%esp), %edx
        fldt      48+_CONSTANTS@GOTOFF(%edx)
..B2.161:
        fldt      80(%esp)
        fld       %st(3)
        fsqrt     
        lea       236(%esp), %edx
        fmul      %st, %st(1)
        fld       %st(1)
        sarl      $1, %eax
        fsubr     %st(1), %st
        movl      %eax, 24(%edx)
        lea       208(%esp), %eax
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     28(%eax)
        fldt      28(%eax)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     40(%eax)
        call      asinpoly
..B2.162:
        fldt      208(%esp)
        fldt      220(%esp)
        movl      232(%esp), %eax
        incl      %eax
        movl      %eax, 232(%esp)
        jmp       ..B2.231
..B2.163:
        fstpt     124(%esp)
        fldt      124(%esp)
        decl      %eax
        movzwl    132(%esp), %edx
        andl      $32767, %edx
        movl      %eax, 148(%esp)
        cmpl      $383, %edx
        fxch      %st(1)
        fstpt     136(%esp)
        fldt      136(%esp)
        jl        ..B2.165
..B2.164:
        cmpl      $32383, %edx
        jle       ..B2.167
..B2.165:
        fstp      %st(0)
        fstp      %st(0)
        addl      $4, %esp
        lea       120(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.166:
        fldt      124(%esp)
        fldt      136(%esp)
        movl      148(%esp), %eax
..B2.167:
        testb     $1, %al
        jne       ..B2.235
..B2.168:
        movl      92(%esp), %edx
        fldt      48+_CONSTANTS@GOTOFF(%edx)
..B2.169:
        fldt      80(%esp)
        fld       %st(3)
        fsqrt     
        lea       152(%esp), %edx
        fmul      %st, %st(1)
        fld       %st(1)
        sarl      $1, %eax
        fsubr     %st(1), %st
        movl      %eax, 24(%edx)
        lea       96(%esp), %eax
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     56(%eax)
        fldt      56(%eax)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     68(%eax)
        call      asinpoly
..B2.170:
        movl      92(%esp), %edx
        movl      $1, %esi
        movl      120(%esp), %eax
        incl      %eax
        fldt      60+_CONSTANTS@GOTOFF(%edx)
        movzwl    68+_CONSTANTS@GOTOFF(%edx), %edi
        andl      $32767, %edi
        movl      %eax, 120(%esp)
        cmpl      $8383, %edi
        movl      %esi, 204(%esp)
        fstpt     180(%esp)
        fldt      72+_CONSTANTS@GOTOFF(%edx)
        fstpt     192(%esp)
        jl        ..B2.174
..B2.171:
        cmpl      $24383, %edi
        jg        ..B2.174
..B2.172:
        movzwl    104(%esp), %ebx
        andl      $32767, %ebx
        cmpl      $8383, %ebx
        jl        ..B2.174
..B2.173:
        cmpl      $24383, %ebx
        jle       ..B2.177
..B2.174:
        addl      $4, %esp
        lea       176(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.175:
        addl      $4, %esp
        lea       92(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.176:
        movzwl    188(%esp), %edi
        movzwl    104(%esp), %ebx
        andl      $32767, %edi
        movl      120(%esp), %eax
        andl      $32767, %ebx
        movl      204(%esp), %esi
..B2.177:
        lea       (%esi,%edi), %edx
        movl      %edx, 44(%esp)
        lea       -134(%eax,%ebx), %ecx
        cmpl      %ecx, %edx
        jle       ..B2.194
..B2.178:
        movl      184(%esp), %ecx
        lea       134(%eax,%ebx), %edx
        cmpl      44(%esp), %edx
        jle       ..B2.195
..B2.179:
        testl     %ecx, %ecx
        jne       ..B2.181
..B2.180:
        cmpl      $0, 180(%esp)
        je        ..B2.196
..B2.181:
        movl      100(%esp), %edx
        testl     %edx, %edx
        jne       ..B2.183
..B2.182:
        cmpl      $0, 96(%esp)
        je        ..B2.195
..B2.183:
        cmpl      %eax, %esi
        je        ..B2.185
..B2.184:
        fldt      48(%esp)
        subl      %eax, %esi
        fstpt     32(%esp)
        addl      $16383, %esi
        fldt      180(%esp)
        andl      $32767, %esi
        fldt      192(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %esi, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     180(%esp)
        fldt      180(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    188(%esp), %edi
        movl      %eax, 204(%esp)
        andl      $32767, %edi
        fstpt     192(%esp)
        fldt      192(%esp)
        jmp       ..B2.186
..B2.185:
        fldt      180(%esp)
        fldt      192(%esp)
..B2.186:
        fldt      96(%esp)
        fld       %st(2)
        movl      %eax, 232(%esp)
        cmpl      %ebx, %edi
        fsub      %st(1), %st
        jg        ..B2.191
..B2.187:
        jne       ..B2.192
..B2.188:
        movl      184(%esp), %ecx
        cmpl      %edx, %ecx
        ja        ..B2.191
..B2.189:
        jne       ..B2.192
..B2.190:
        movl      180(%esp), %edx
        cmpl      96(%esp), %edx
        jbe       ..B2.192
..B2.191:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      108(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B2.193
..B2.192:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      108(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B2.193:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     208(%esp)
        fldt      208(%esp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     220(%esp)
        fldt      220(%esp)
        jmp       ..B2.231
..B2.194:
        movl      184(%esp), %ecx
..B2.195:
        testl     %ecx, %ecx
        jne       ..B2.197
..B2.244:
        cmpl      $0, 180(%esp)
..B2.196:
        je        ..B2.198
..B2.197:
        lea       -16517(%eax,%ebx), %edx
        lea       -16383(%esi,%edi), %ebx
        cmpl      %ebx, %edx
        jl        ..B2.199
..B2.198:
        fldt      96(%esp)
        fchs      
        movl      %eax, 232(%esp)
        fstpt     208(%esp)
        fldt      208(%esp)
        fldt      108(%esp)
        fchs      
        fstpt     220(%esp)
        fldt      220(%esp)
        jmp       ..B2.231
..B2.199:
        movl      180(%esp), %eax
        movl      %ecx, 212(%esp)
        movl      188(%esp), %edx
        movl      192(%esp), %ecx
        movl      196(%esp), %ebx
        movl      200(%esp), %esi
        movl      %eax, 208(%esp)
        movl      %edx, 216(%esp)
        fldt      208(%esp)
        movl      204(%esp), %eax
        movl      %ecx, 220(%esp)
        movl      %ebx, 224(%esp)
        movl      %esi, 228(%esp)
        fldt      220(%esp)
        movl      %eax, 232(%esp)
        jmp       ..B2.231
..B2.200:
        movl      92(%esp), %eax
        lea       460(%esp), %edx
        movl      $0, 84(%esp)
        fldt      60+_CONSTANTS@GOTOFF(%eax)
        fstpt     60(%esp)
        fldt      72+_CONSTANTS@GOTOFF(%eax)
        lea       96(%esp), %eax
        fstpt     -24(%eax)
        call      asinpoly
..B2.201:
        fldt      96(%esp)
        fldt      108(%esp)
        movl      8(%ebp), %eax
        movl      92(%esp), %esi
        movzwl    68(%esp), %edi
        movzbl    9(%eax), %ecx
        andl      $32767, %edi
        andl      $128, %ecx
        shrl      $7, %ecx
        cmpl      $8383, %edi
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %ebx
        fldt      _CONSTANTS@GOTOFF(%ebx,%esi)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     96(%esp)
        fmulp     %st, %st(1)
        fstpt     108(%esp)
        jl        ..B2.205
..B2.202:
        cmpl      $24383, %edi
        jg        ..B2.205
..B2.203:
        movzwl    104(%esp), %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B2.205
..B2.204:
        cmpl      $24383, %esi
        jle       ..B2.208
..B2.205:
        addl      $4, %esp
        lea       56(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.206:
        addl      $4, %esp
        lea       92(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.207:
        movzwl    104(%esp), %esi
        movzwl    68(%esp), %edi
        andl      $32767, %esi
        andl      $32767, %edi
..B2.208:
        movl      120(%esp), %eax
        movl      84(%esp), %ebx
        lea       -134(%eax,%esi), %ecx
        lea       (%edi,%ebx), %edx
        movl      %edx, 32(%esp)
        cmpl      %ecx, %edx
        jle       ..B2.225
..B2.209:
        movl      64(%esp), %ecx
        lea       134(%eax,%esi), %edx
        cmpl      32(%esp), %edx
        jle       ..B2.226
..B2.210:
        testl     %ecx, %ecx
        jne       ..B2.212
..B2.211:
        cmpl      $0, 60(%esp)
        je        ..B2.227
..B2.212:
        movl      100(%esp), %edx
        testl     %edx, %edx
        jne       ..B2.214
..B2.213:
        cmpl      $0, 96(%esp)
        je        ..B2.226
..B2.214:
        cmpl      %eax, %ebx
        je        ..B2.216
..B2.215:
        fldt      48(%esp)
        subl      %eax, %ebx
        fstpt     32(%esp)
        addl      $16383, %ebx
        fldt      60(%esp)
        andl      $32767, %ebx
        fldt      72(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %ebx, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     60(%esp)
        fldt      60(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    68(%esp), %edi
        movl      %eax, 84(%esp)
        andl      $32767, %edi
        fstpt     72(%esp)
        fldt      72(%esp)
        jmp       ..B2.217
..B2.216:
        fldt      60(%esp)
        fldt      72(%esp)
..B2.217:
        fldt      96(%esp)
        fld       %st(2)
        movl      %eax, 232(%esp)
        cmpl      %esi, %edi
        fsub      %st(1), %st
        jg        ..B2.222
..B2.218:
        jne       ..B2.223
..B2.219:
        movl      64(%esp), %ecx
        cmpl      %edx, %ecx
        ja        ..B2.222
..B2.220:
        jne       ..B2.223
..B2.221:
        movl      60(%esp), %edx
        cmpl      96(%esp), %edx
        jbe       ..B2.223
..B2.222:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      108(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B2.224
..B2.223:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      108(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B2.224:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     208(%esp)
        fldt      208(%esp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     220(%esp)
        fldt      220(%esp)
        jmp       ..B2.231
..B2.225:
        movl      64(%esp), %ecx
..B2.226:
        testl     %ecx, %ecx
        jne       ..B2.228
..B2.245:
        cmpl      $0, 60(%esp)
..B2.227:
        je        ..B2.198
..B2.228:
        lea       -16517(%eax,%esi), %edx
        lea       -16383(%ebx,%edi), %esi
        cmpl      %esi, %edx
        jge       ..B2.198
..B2.230:
        movl      %ecx, 212(%esp)
        movl      60(%esp), %eax
        movl      68(%esp), %edx
        movl      72(%esp), %ecx
        movl      76(%esp), %esi
        movl      80(%esp), %edi
        movl      %eax, 208(%esp)
        movl      %ebx, %eax
        movl      %edx, 216(%esp)
        fldt      208(%esp)
        movl      %ecx, 220(%esp)
        movl      %esi, 224(%esp)
        movl      %edi, 228(%esp)
        fldt      220(%esp)
        movl      %eax, 232(%esp)
..B2.231:
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      %eax, 24(%esp)
        call      __libm_scalbl_k80
..B2.232:
        movl      488(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.236
..B2.233:
        addl      $500, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.234:
        movl      92(%esp), %edx
        incl      %eax
        movl      %eax, 56(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%edx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B2.161
..B2.235:
        movl      92(%esp), %edx
        incl      %eax
        movl      %eax, 148(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%edx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     124(%esp)
        fldt      124(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     136(%esp)
        fldt      136(%esp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B2.169
..B2.236:
        fstp      %st(0)
        movl      92(%esp), %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__libm_recacosl_k80,@function
	.size	__libm_recacosl_k80,.-__libm_recacosl_k80
	.data
# -- End  __libm_recacosl_k80
	.text
# -- Begin  asinpoly
	.text
       .align    16,0x90
asinpoly:
# parameter 1: %eax
# parameter 2: %edx
..B3.1:
..L5:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $164, %esp
        movl      %edx, %esi
        call      ..L6
..L6:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L6](%ecx), %ecx
        movl      %eax, %ebx
        movl      24(%esi), %edx
        fldt      _CONSTANTS@GOTOFF(%ecx)
        movzwl    8+_CONSTANTS@GOTOFF(%ecx), %eax
        andl      $-32768, %eax
        lea       16383(%edx), %edi
        andl      $32767, %edi
        orl       %edi, %eax
        movzwl    8(%esi), %edi
        andl      $32767, %edi
        addl      %edi, %edx
        fstpt     136(%esp)
        cmpl      $16343, %edx
        jl        ..B3.38
..B3.2:
        fldt      (%esi)
        fldt      12(%esi)
        fldt      264+_CONSTANTS@GOTOFF(%ecx)
        fldt      240+_CONSTANTS@GOTOFF(%ecx)
        fldt      216+_CONSTANTS@GOTOFF(%ecx)
        movw      %ax, 144(%esp)
        fldt      136(%esp)
        fmul      %st, %st(5)
        fld       %st(5)
        fmul      %st(6), %st
        fld       %st(0)
        fmul      %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fstpt     40(%esp)
        fldt      40(%esp)
        fxch      %st(6)
        fstpt     104(%esp)
        fldt      104(%esp)
        fstpt     (%esp)
        fldt      (%esp)
        fldt      288+_CONSTANTS@GOTOFF(%ecx)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      192+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      168+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      144+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fmul      %st, %st(2)
        fldt      276+_CONSTANTS@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      252+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      228+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      204+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      180+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      156+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      132+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fld       %st(1)
        fldt      36+_CONSTANTS@GOTOFF(%ecx)
        fstpt     72(%esp)
        fldt      72(%esp)
        fmul      %st(3), %st
        movzwl    20(%esp), %eax
        fsubr     %st, %st(1)
        andl      $32767, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        cmpl      $16377, %eax
        fsubr     %st(3), %st
        fstpt     56(%esp)
        fldt      56(%esp)
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(5)
        fmulp     %st, %st(4)
        fxch      %st(3)
        fadd      %st(0), %st
        fxch      %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fmul      %st(3), %st
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(5), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(6)
        fxch      %st(2)
        faddp     %st, %st(6)
        fxch      %st(4)
        fmul      %st(0), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     88(%esp)
        fldt      88(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     120(%esp)
        fldt      120+_CONSTANTS@GOTOFF(%ecx)
        fld       %st(0)
        fadd      %st(2), %st
        jg        ..B3.7
..B3.3:
        jne       ..B3.8
..B3.4:
        movl      16(%esp), %eax
        cmpl      124+_CONSTANTS@GOTOFF(%ecx), %eax
        ja        ..B3.7
..B3.5:
        jne       ..B3.8
..B3.6:
        movl      12(%esp), %eax
        cmpl      120+_CONSTANTS@GOTOFF(%ecx), %eax
        jbe       ..B3.8
..B3.7:
        fldt      336+_CONSTANTS@GOTOFF(%ecx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      24+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B3.9
..B3.8:
        fldt      24+_CONSTANTS@GOTOFF(%ecx)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      336+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
..B3.9:
        fldt      88(%esp)
        fld       %st(0)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(3)
        fmul      %st, %st(5)
        fldt      72(%esp)
        fld       %st(0)
        fmul      %st(6), %st
        fsubr     %st, %st(5)
        faddp     %st, %st(5)
        fxch      %st(2)
        fsub      %st(4), %st
        fld       %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     136(%esp)
        fldt      136(%esp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fstpt     152(%esp)
        fldt      152(%esp)
        fld       %st(3)
        fmul      %st(3), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      120(%esp)
        fmulp     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      152(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubr     %st, %st(2)
        movzwl    20(%esp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      108+_CONSTANTS@GOTOFF(%ecx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16378, %eax
        fadd      %st(2), %st
        jg        ..B3.14
..B3.10:
        jne       ..B3.15
..B3.11:
        movl      16(%esp), %eax
        cmpl      112+_CONSTANTS@GOTOFF(%ecx), %eax
        ja        ..B3.14
..B3.12:
        jne       ..B3.15
..B3.13:
        movl      12(%esp), %eax
        cmpl      108+_CONSTANTS@GOTOFF(%ecx), %eax
        jbe       ..B3.15
..B3.14:
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      324+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.16
..B3.15:
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      324+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(2)
..B3.16:
        fldt      136(%esp)
        fld       %st(0)
        fld       %st(3)
        fadd      %st(3), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(5)
        fxch      %st(6)
        faddp     %st, %st(5)
        fldt      72(%esp)
        fmul      %st(6), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      152(%esp)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      120(%esp)
        fmulp     %st, %st(5)
        fldt      88(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubr     %st, %st(1)
        movzwl    20(%esp), %eax
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      96+_CONSTANTS@GOTOFF(%ecx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16379, %eax
        fadd      %st(3), %st
        jg        ..B3.21
..B3.17:
        jne       ..B3.22
..B3.18:
        movl      16(%esp), %eax
        cmpl      100+_CONSTANTS@GOTOFF(%ecx), %eax
        ja        ..B3.21
..B3.19:
        jne       ..B3.22
..B3.20:
        movl      12(%esp), %eax
        cmpl      96+_CONSTANTS@GOTOFF(%ecx), %eax
        jbe       ..B3.22
..B3.21:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      312+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B3.23
..B3.22:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      312+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(1)
..B3.23:
        fldt      136(%esp)
        fld       %st(0)
        fld       %st(2)
        fadd      %st(4), %st
        fld       %st(0)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(6)
        fldt      72(%esp)
        fmul      %st(5), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fmul      %st, %st(2)
        fsubr     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(3)
        fldt      152(%esp)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      120(%esp)
        fmulp     %st, %st(3)
        fldt      88(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubr     %st, %st(2)
        movzwl    20(%esp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      84+_CONSTANTS@GOTOFF(%ecx)
        andl      $32767, %eax
        fld       %st(0)
        cmpl      $16380, %eax
        fadd      %st(2), %st
        jg        ..B3.28
..B3.24:
        jne       ..B3.29
..B3.25:
        movl      16(%esp), %eax
        cmpl      88+_CONSTANTS@GOTOFF(%ecx), %eax
        ja        ..B3.28
..B3.26:
        jne       ..B3.29
..B3.27:
        movl      12(%esp), %eax
        cmpl      84+_CONSTANTS@GOTOFF(%ecx), %eax
        jbe       ..B3.29
..B3.28:
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      300+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.30
..B3.29:
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      300+_CONSTANTS@GOTOFF(%ecx)
        faddp     %st, %st(2)
..B3.30:
        fldt      72(%esp)
        fld       %st(2)
        movzwl    8(%esp), %edx
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fld       %st(0)
        andl      $32767, %edx
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fsub      %st(1), %st
        fldt      136(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      152(%esp)
        fmul      %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      120(%esp)
        fmulp     %st, %st(5)
        fldt      88(%esp)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(2)
        fldt      72(%esp)
        fmul      %st(4), %st
        fsubr     %st, %st(1)
        faddp     %st, %st(1)
        fsubr     %st, %st(1)
        fldt      24(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      56(%esp)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fld       %st(0)
        fldt      40(%esp)
        fmulp     %st, %st(5)
        fldt      104(%esp)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(1)
        fadd      %st(3), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fsubr     %st, %st(1)
        movzwl    20(%esp), %eax
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fadd      %st(2), %st
        andl      $32767, %eax
        cmpl      %edx, %eax
        jg        ..B3.35
..B3.31:
        jne       ..B3.36
..B3.32:
        movl      16(%esp), %eax
        movl      4(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B3.35
..B3.33:
        jne       ..B3.36
..B3.34:
        movl      12(%esp), %eax
        cmpl      (%esp), %eax
        jbe       ..B3.36
..B3.35:
        fldt      104(%esp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      40(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B3.37
..B3.36:
        fldt      104(%esp)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      40(%esp)
        faddp     %st, %st(2)
..B3.37:
        fld       %st(1)
        movl      $0, 24(%ebx)
        fadd      %st(1), %st
        fstpt     (%ebx)
        fldt      (%ebx)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     12(%ebx)
        addl      $164, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.38:
        movl      (%esi), %eax
        movl      4(%esi), %edx
        movl      %eax, (%ebx)
        movl      %edx, 4(%ebx)
        movl      8(%esi), %ecx
        movl      12(%esi), %edi
        movl      16(%esi), %eax
        movl      20(%esi), %edx
        movl      24(%esi), %esi
        movl      %ecx, 8(%ebx)
        movl      %edi, 12(%ebx)
        movl      %eax, 16(%ebx)
        movl      %edx, 20(%ebx)
        movl      %esi, 24(%ebx)
..B3.39:
        addl      $164, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	asinpoly,@function
	.size	asinpoly,.-asinpoly
	.data
# -- End  asinpoly
	.text
# -- Begin  __libm_recasinl_k80
	.text
       .align    16,0x90
	.hidden __libm_recasinl_k80
	.globl __libm_recasinl_k80
__libm_recasinl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
# parameter 3: 16 + %ebp
# parameter 4: 20 + %ebp
# parameter 5: 24 + %ebp
# parameter 6: 28 + %ebp
# parameter 7: 32 + %ebp
# parameter 8: 36 + %ebp
# parameter 9: 40 + %ebp
# parameter 10: 44 + %ebp
..B4.1:
..L7:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $436, %esp
        movl      16(%ebp), %esi
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 432(%esp)
        movzwl    8(%esi), %eax
        andl      $32767, %eax
        movl      44(%ebp), %edi
        cmpl      $8383, %eax
        jl        ..B4.5
..B4.2:
        cmpl      $24383, %eax
        jg        ..B4.5
..B4.3:
        movzwl    8(%edi), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.5
..B4.4:
        cmpl      $24383, %eax
        jle       ..B4.7
..B4.5:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B4.6:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B4.7:
        fldt      (%esi)
        fld       %st(0)
        fldt      (%edi)
        fdivr     %st, %st(1)
        fld       %st(0)
        call      ..L8
..L8:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L8](%eax), %eax
        fld       %st(1)
        movl      24(%esi), %ebx
        subl      24(%edi), %ebx
        fldt      36+_CONSTANTS@GOTOFF(%eax)
        fld       %st(0)
        fmul      %st(5), %st
        lea       1(%ebx), %edx
        fld       %st(0)
        movl      %eax, 92(%esp)
        fsubr     %st(6), %st
        movl      %edx, 428(%esp)
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fsub      %st(4), %st
        fxch      %st(3)
        fstpt     80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(1)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      12(%esi)
        faddp     %st, %st(3)
        fldt      12(%edi)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     404(%esp)
        fldt      404(%esp)
        fsubrp    %st, %st(1)
        movzwl    412(%esp), %ecx
        fadd      %st(1), %st
        andl      $32767, %ecx
        fstpt     416(%esp)
        fstpt     48(%esp)
        lea       1(%ecx,%ebx), %eax
        cmpl      $16382, %eax
        jl        ..B4.191
..B4.8:
        movl      8(%ebp), %eax
        movl      40(%ebp), %ebx
        movzwl    8(%eax), %edx
        andl      $32767, %edx
        cmpl      $16383, %edx
        jl        ..B4.39
..B4.9:
        movl      32(%ebp), %eax
        movl      (%eax), %edx
        movl      %edx, 96(%esp)
        movl      8(%eax), %edx
        movl      4(%eax), %ecx
        movl      %edx, 104(%esp)
        movl      16(%eax), %edx
        movl      %ecx, 100(%esp)
        movl      12(%eax), %ecx
        movl      %edx, 112(%esp)
        movzwl    8(%ebx), %edx
        movl      %ecx, 108(%esp)
        movl      20(%eax), %ecx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movl      24(%eax), %eax
        cmpl      $8383, %edx
        movl      %ecx, 116(%esp)
        movl      %eax, 120(%esp)
        jl        ..B4.13
..B4.10:
        cmpl      $24383, %edx
        jg        ..B4.13
..B4.11:
        movl      24(%ebp), %eax
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
        cmpl      $8383, %ecx
        jl        ..B4.13
..B4.12:
        cmpl      $24383, 40(%esp)
        jle       ..B4.16
..B4.13:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B4.14:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B4.15:
        movl      24(%ebp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
..B4.16:
        movl      24(%ebp), %ecx
        movl      24(%ecx), %eax
        movl      40(%esp), %ecx
        movl      %eax, 64(%esp)
        lea       -134(%eax,%ecx), %eax
        movl      24(%ebx), %ecx
        movl      %ecx, 60(%esp)
        addl      %edx, %ecx
        movl      %ecx, 36(%esp)
        cmpl      %ecx, %eax
        jge       ..B4.33
..B4.17:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       134(%ecx,%eax), %ecx
        cmpl      36(%esp), %ecx
        jle       ..B4.34
..B4.18:
        cmpl      $0, 32(%esp)
        jne       ..B4.20
..B4.19:
        cmpl      $0, (%ebx)
        je        ..B4.35
..B4.20:
        movl      24(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B4.22
..B4.21:
        movl      24(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B4.34
..B4.22:
        movl      64(%esp), %eax
        cmpl      60(%esp), %eax
        je        ..B4.24
..B4.23:
        fldt      48(%esp)
        movl      60(%esp), %eax
        movl      64(%esp), %ecx
        subl      %ecx, %eax
        fstpt     32(%esp)
        addl      $16383, %eax
        fldt      (%ebx)
        andl      $32767, %eax
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        movl      24(%ebp), %ecx
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fstpt     48(%esp)
        fmulp     %st, %st(1)
        movl      24(%ecx), %eax
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      %eax, 64(%esp)
        movzwl    8(%ebx), %edx
        movzwl    8(%ecx), %eax
        movl      %edx, 68(%esp)
        movl      %eax, 44(%esp)
        jmp       ..B4.25
..B4.24:
        fldt      (%ebx)
        fstpt     48(%esp)
        fldt      12(%ebx)
..B4.25:
        fldt      48(%esp)
        movl      24(%ebp), %edx
        movl      64(%esp), %eax
        movl      %eax, 148(%esp)
        fldt      (%edx)
        movl      68(%esp), %ecx
        fadd      %st, %st(1)
        movl      44(%esp), %eax
        andl      $32767, %ecx
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jg        ..B4.30
..B4.26:
        jne       ..B4.31
..B4.27:
        movl      24(%ebp), %edx
        movl      4(%ebx), %eax
        cmpl      4(%edx), %eax
        ja        ..B4.30
..B4.28:
        jne       ..B4.31
..B4.29:
        movl      24(%ebp), %edx
        movl      (%ebx), %eax
        cmpl      (%edx), %eax
        jbe       ..B4.31
..B4.30:
        fldt      48(%esp)
        movl      24(%ebp), %eax
        fsub      %st(2), %st
        faddp     %st, %st(1)
        fldt      12(%eax)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
        jmp       ..B4.32
..B4.31:
        fldt      48(%esp)
        fxch      %st(1)
        fsub      %st(2), %st
        movl      24(%ebp), %eax
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldt      12(%eax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B4.32:
        fldt      48(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     124(%esp)
        fldt      124(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     136(%esp)
        jmp       ..B4.69
..B4.33:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
..B4.34:
        cmpl      $0, 32(%esp)
        jne       ..B4.36
..B4.202:
        cmpl      $0, (%ebx)
..B4.35:
        je        ..B4.37
..B4.36:
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       -16517(%ecx,%eax), %eax
        movl      60(%esp), %ecx
        lea       -16383(%ecx,%edx), %edx
        cmpl      %edx, %eax
        jl        ..B4.38
..B4.37:
        movl      24(%ebp), %eax
        movl      (%eax), %edx
        movl      4(%eax), %ecx
        movl      8(%eax), %ebx
        movl      %edx, 124(%esp)
        movl      %ecx, 128(%esp)
        movl      %ebx, 132(%esp)
        movl      12(%eax), %edx
        movl      16(%eax), %ecx
        movl      20(%eax), %ebx
        movl      24(%eax), %eax
        movl      %edx, 136(%esp)
        movl      %ecx, 140(%esp)
        movl      %ebx, 144(%esp)
        movl      %eax, 148(%esp)
        jmp       ..B4.69
..B4.38:
        movl      (%ebx), %eax
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      %eax, 124(%esp)
        movl      %edx, 128(%esp)
        movl      %ecx, 132(%esp)
        movl      12(%ebx), %eax
        movl      16(%ebx), %edx
        movl      20(%ebx), %ecx
        movl      24(%ebx), %ebx
        movl      %eax, 136(%esp)
        movl      %edx, 140(%esp)
        movl      %ecx, 144(%esp)
        movl      %ebx, 148(%esp)
        jmp       ..B4.69
..B4.39:
        movl      92(%esp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        fldt      _CONSTANTS@GOTOFF(%eax)
        cmpl      $8383, %edx
        movl      $0, 148(%esp)
        fstpt     124(%esp)
        fldt      24+_CONSTANTS@GOTOFF(%eax)
        fstpt     136(%esp)
        jl        ..B4.43
..B4.40:
        cmpl      $24383, %edx
        jg        ..B4.43
..B4.41:
        movl      24(%ebp), %eax
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
        cmpl      $8383, %ecx
        jl        ..B4.43
..B4.42:
        cmpl      $24383, 40(%esp)
        jle       ..B4.46
..B4.43:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B4.44:
        addl      $4, %esp
        pushl     24(%ebp)
        call      __libm_normalizel_k80
..B4.45:
        movl      24(%ebp), %eax
        movzwl    8(%ebx), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movzwl    8(%eax), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        movl      %ecx, 40(%esp)
..B4.46:
        movl      24(%ebp), %ecx
        movl      24(%ecx), %eax
        movl      40(%esp), %ecx
        movl      %eax, 64(%esp)
        lea       -134(%eax,%ecx), %eax
        movl      24(%ebx), %ecx
        movl      %ecx, 60(%esp)
        addl      %edx, %ecx
        movl      %ecx, 36(%esp)
        cmpl      %ecx, %eax
        jge       ..B4.63
..B4.47:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
        movl      64(%esp), %ecx
        movl      40(%esp), %eax
        lea       134(%ecx,%eax), %ecx
        cmpl      36(%esp), %ecx
        jle       ..B4.64
..B4.48:
        cmpl      $0, 32(%esp)
        jne       ..B4.50
..B4.49:
        cmpl      $0, (%ebx)
        je        ..B4.65
..B4.50:
        movl      24(%ebp), %eax
        cmpl      $0, 4(%eax)
        jne       ..B4.52
..B4.51:
        movl      24(%ebp), %eax
        cmpl      $0, (%eax)
        je        ..B4.64
..B4.52:
        movl      64(%esp), %eax
        cmpl      60(%esp), %eax
        je        ..B4.54
..B4.53:
        fldt      48(%esp)
        movl      60(%esp), %eax
        movl      64(%esp), %ecx
        subl      %ecx, %eax
        fstpt     32(%esp)
        addl      $16383, %eax
        fldt      (%ebx)
        andl      $32767, %eax
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 24(%ebx)
        movl      24(%ebp), %ecx
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      24(%ecx), %eax
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      %eax, 64(%esp)
        movzwl    8(%ebx), %edx
        movzwl    8(%ecx), %eax
        fstpt     48(%esp)
        movl      %edx, 68(%esp)
        movl      %eax, 44(%esp)
        jmp       ..B4.55
..B4.54:
        fldt      (%ebx)
        fldt      12(%ebx)
        fstpt     48(%esp)
..B4.55:
        movl      24(%ebp), %edx
        fld       %st(0)
        movl      64(%esp), %eax
        movl      %eax, 120(%esp)
        fldt      (%edx)
        movl      68(%esp), %ecx
        fsubr     %st, %st(1)
        movl      44(%esp), %eax
        andl      $32767, %ecx
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jg        ..B4.60
..B4.56:
        jne       ..B4.61
..B4.57:
        movl      24(%ebp), %edx
        movl      4(%ebx), %eax
        cmpl      4(%edx), %eax
        ja        ..B4.60
..B4.58:
        jne       ..B4.61
..B4.59:
        movl      24(%ebp), %edx
        movl      (%ebx), %eax
        cmpl      (%edx), %eax
        jbe       ..B4.61
..B4.60:
        fldt      48(%esp)
        fxch      %st(2)
        fsubr     %st, %st(3)
        movl      24(%ebp), %eax
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      12(%eax)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     48(%esp)
        jmp       ..B4.62
..B4.61:
        movl      24(%ebp), %eax
        fadd      %st(1), %st
        fsubrp    %st, %st(2)
        fldt      48(%esp)
        faddp     %st, %st(2)
        fldt      12(%eax)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     48(%esp)
..B4.62:
        fldt      48(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     96(%esp)
        fldt      96(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        jmp       ..B4.69
..B4.63:
        movl      4(%ebx), %eax
        movl      %eax, 32(%esp)
..B4.64:
        cmpl      $0, 32(%esp)
        jne       ..B4.66
..B4.201:
        cmpl      $0, (%ebx)
..B4.65:
        je        ..B4.67
..B4.66:
        movl      64(%esp), %eax
        movl      40(%esp), %ecx
        movl      60(%esp), %ebx
        lea       -16517(%eax,%ecx), %eax
        lea       -16383(%ebx,%edx), %edx
        movl      40(%ebp), %ebx
        cmpl      %edx, %eax
        jl        ..B4.68
..B4.67:
        movl      24(%ebp), %edx
        movl      64(%esp), %eax
        movl      %eax, 120(%esp)
        fldt      (%edx)
        fchs      
        fstpt     96(%esp)
        fldt      12(%edx)
        fchs      
        fstpt     108(%esp)
        jmp       ..B4.69
..B4.68:
        movl      (%ebx), %eax
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      %eax, 96(%esp)
        movl      %edx, 100(%esp)
        movl      %ecx, 104(%esp)
        movl      12(%ebx), %eax
        movl      16(%ebx), %edx
        movl      20(%ebx), %ecx
        movl      24(%ebx), %ebx
        movl      %eax, 108(%esp)
        movl      %edx, 112(%esp)
        movl      %ecx, 116(%esp)
        movl      %ebx, 120(%esp)
..B4.69:
        movzwl    8(%esi), %eax
        movl      %eax, 68(%esp)
        andl      $32767, %eax
        movl      24(%esi), %ecx
        incl      %ecx
        movl      %ecx, 24(%esi)
        cmpl      $8383, %eax
        movl      %eax, 76(%esp)
        jl        ..B4.73
..B4.70:
        cmpl      $24383, 76(%esp)
        jg        ..B4.73
..B4.71:
        movzwl    8(%edi), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        movl      %eax, 72(%esp)
        cmpl      $8383, %eax
        jl        ..B4.73
..B4.72:
        cmpl      $24383, 72(%esp)
        jle       ..B4.76
..B4.73:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B4.74:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B4.75:
        movzwl    8(%esi), %edx
        movl      %edx, 68(%esp)
        andl      $32767, %edx
        movl      %edx, 76(%esp)
        movzwl    8(%edi), %edx
        movl      %edx, %eax
        andl      $32767, %eax
        movl      %eax, 72(%esp)
        movl      24(%esi), %ecx
..B4.76:
        movl      76(%esp), %ebx
        movl      24(%edi), %edi
        movl      %edi, 64(%esp)
        lea       (%ecx,%ebx), %eax
        movl      72(%esp), %ebx
        movl      %eax, 60(%esp)
        lea       -134(%edi,%ebx), %edi
        cmpl      %edi, %eax
        movl      44(%ebp), %edi
        jle       ..B4.93
..B4.77:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
        movl      64(%esp), %ebx
        movl      72(%esp), %eax
        lea       134(%ebx,%eax), %ebx
        cmpl      60(%esp), %ebx
        jle       ..B4.94
..B4.78:
        cmpl      $0, 44(%esp)
        jne       ..B4.80
..B4.79:
        cmpl      $0, (%esi)
        je        ..B4.95
..B4.80:
        cmpl      $0, 4(%edi)
        jne       ..B4.82
..B4.81:
        cmpl      $0, (%edi)
        je        ..B4.94
..B4.82:
        cmpl      64(%esp), %ecx
        je        ..B4.84
..B4.83:
        fldt      48(%esp)
        movl      64(%esp), %edx
        subl      %edx, %ecx
        fstpt     32(%esp)
        addl      $16383, %ecx
        fldt      (%esi)
        andl      $32767, %ecx
        fldt      12(%esi)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %ecx, %eax
        movl      %edx, %ecx
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 24(%esi)
        fxch      %st(2)
        fstpt     (%esi)
        fldt      (%esi)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%esi)
        fldt      12(%esi)
        movl      24(%edi), %ebx
        movzwl    8(%esi), %eax
        movl      %ebx, 64(%esp)
        movl      %eax, 68(%esp)
        movzwl    8(%edi), %edx
        jmp       ..B4.85
..B4.84:
        fldt      (%esi)
        fldt      12(%esi)
..B4.85:
        fldt      (%edi)
        fld       %st(2)
        movl      64(%esp), %eax
        fadd      %st(1), %st
        movl      %eax, 176(%esp)
        movl      %edx, %eax
        movl      68(%esp), %ebx
        andl      $32767, %eax
        andl      $32767, %ebx
        cmpl      %eax, %ebx
        jg        ..B4.90
..B4.86:
        jne       ..B4.91
..B4.87:
        movl      4(%esi), %eax
        cmpl      4(%edi), %eax
        ja        ..B4.90
..B4.88:
        jne       ..B4.91
..B4.89:
        movl      (%esi), %eax
        cmpl      (%edi), %eax
        jbe       ..B4.91
..B4.90:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      12(%edi)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B4.92
..B4.91:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%edi)
        faddp     %st, %st(2)
..B4.92:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     152(%esp)
        fldt      152(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     164(%esp)
        jmp       ..B4.99
..B4.93:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
..B4.94:
        cmpl      $0, 44(%esp)
        jne       ..B4.96
..B4.203:
        cmpl      $0, (%esi)
..B4.95:
        je        ..B4.97
..B4.96:
        movl      64(%esp), %ebx
        movl      72(%esp), %eax
        lea       -16517(%ebx,%eax), %eax
        movl      76(%esp), %ebx
        lea       -16383(%ecx,%ebx), %ebx
        cmpl      %ebx, %eax
        jl        ..B4.98
..B4.97:
        movl      (%edi), %eax
        movl      %eax, 152(%esp)
        movl      4(%edi), %ebx
        movl      8(%edi), %eax
        movl      %ebx, 156(%esp)
        movl      %eax, 160(%esp)
        movl      12(%edi), %ebx
        movl      16(%edi), %eax
        movl      %ebx, 164(%esp)
        movl      %eax, 168(%esp)
        movl      20(%edi), %ebx
        movl      24(%edi), %eax
        movl      %ebx, 172(%esp)
        movl      %eax, 176(%esp)
        jmp       ..B4.99
..B4.98:
        movl      (%esi), %eax
        movl      %eax, 152(%esp)
        movl      4(%esi), %ebx
        movl      8(%esi), %eax
        movl      %ebx, 156(%esp)
        movl      %eax, 160(%esp)
        movl      12(%esi), %ebx
        movl      16(%esi), %eax
        movl      %ebx, 164(%esp)
        movl      %eax, 168(%esp)
        movl      20(%esi), %ebx
        movl      24(%esi), %eax
        movl      %ebx, 172(%esp)
        movl      %eax, 176(%esp)
..B4.99:
        movzwl    160(%esp), %eax
        decl      %ecx
        andl      $32767, %eax
        movl      %ecx, 24(%esi)
        cmpl      $8383, %eax
        jl        ..B4.103
..B4.100:
        cmpl      $24383, %eax
        jg        ..B4.103
..B4.101:
        movzwl    104(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.103
..B4.102:
        cmpl      $24383, %eax
        jle       ..B4.105
..B4.103:
        addl      $4, %esp
        lea       148(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.104:
        addl      $4, %esp
        lea       92(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
        movzwl    8(%edi), %edx
..B4.105:
        fldt      152(%esp)
        andl      $32767, %edx
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      176(%esp), %eax
        fsubr     %st, %st(3)
        addl      120(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        cmpl      $8383, %edx
        movl      %eax, 204(%esp)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      96(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      108(%esp)
        fmulp     %st, %st(4)
        fldt      164(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     180(%esp)
        fldt      180(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     192(%esp)
        jl        ..B4.109
..B4.106:
        cmpl      $24383, %edx
        jg        ..B4.109
..B4.107:
        movzwl    132(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.109
..B4.108:
        cmpl      $24383, %eax
        jle       ..B4.111
..B4.109:
        addl      $4, %esp
        pushl     %edi
        call      __libm_normalizel_k80
..B4.110:
        addl      $4, %esp
        lea       120(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.111:
        fldt      (%edi)
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      36(%ebp), %ebx
        fsubr     %st, %st(3)
        movl      148(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      124(%esp)
        fmul      %st, %st(2)
        fld       %st(0)
        addl      24(%edi), %eax
        fsub      %st(3), %st
        movl      28(%ebp), %esi
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        movl      %eax, 232(%esp)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      136(%esp)
        fmulp     %st, %st(4)
        fldt      12(%edi)
        fmulp     %st, %st(1)
        movzwl    8(%ebx), %edi
        movl      %edi, %ecx
        andl      $32767, %ecx
        faddp     %st, %st(3)
        cmpl      $8383, %ecx
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     208(%esp)
        fldt      208(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     220(%esp)
        fstpt     48(%esp)
        jl        ..B4.115
..B4.112:
        cmpl      $24383, %ecx
        jg        ..B4.115
..B4.113:
        movzwl    8(%esi), %edx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B4.115
..B4.114:
        cmpl      $24383, %edx
        jle       ..B4.118
..B4.115:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B4.116:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B4.117:
        movzwl    8(%ebx), %edi
        movl      %edi, %ecx
        movzwl    8(%esi), %edx
        andl      $32767, %ecx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
..B4.118:
        movl      24(%esi), %eax
        movl      24(%ebx), %ebx
        movl      %eax, 72(%esp)
        movl      %ebx, 68(%esp)
        lea       -134(%eax,%edx), %eax
        addl      %ecx, %ebx
        movl      %ebx, 64(%esp)
        cmpl      %eax, %ebx
        movl      36(%ebp), %ebx
        jle       ..B4.135
..B4.119:
        movl      4(%ebx), %eax
        movl      %eax, 44(%esp)
        movl      72(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      64(%esp), %eax
        jle       ..B4.136
..B4.120:
        cmpl      $0, 44(%esp)
        jne       ..B4.122
..B4.121:
        cmpl      $0, (%ebx)
        je        ..B4.137
..B4.122:
        cmpl      $0, 4(%esi)
        jne       ..B4.124
..B4.123:
        cmpl      $0, (%esi)
        je        ..B4.136
..B4.124:
        movl      72(%esp), %eax
        cmpl      68(%esp), %eax
        je        ..B4.126
..B4.125:
        fldt      48(%esp)
        movl      68(%esp), %edi
        movl      %eax, %edx
        subl      %edx, %edi
        fstpt     32(%esp)
        addl      $16383, %edi
        fldt      (%ebx)
        andl      $32767, %edi
        fldt      12(%ebx)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movl      24(%esi), %ecx
        movzwl    8(%esi), %eax
        movl      %ecx, 72(%esp)
        movzwl    8(%ebx), %edi
        movl      %eax, 60(%esp)
        jmp       ..B4.127
..B4.126:
        fldt      (%ebx)
        fldt      12(%ebx)
..B4.127:
        fldt      (%esi)
        andl      $32767, %edi
        movl      60(%esp), %edx
        fld       %st(2)
        andl      $32767, %edx
        fadd      %st(1), %st
        movl      72(%esp), %eax
        cmpl      %edx, %edi
        movl      %eax, 260(%esp)
        jg        ..B4.132
..B4.128:
        jne       ..B4.133
..B4.129:
        movl      4(%ebx), %eax
        cmpl      4(%esi), %eax
        ja        ..B4.132
..B4.130:
        jne       ..B4.133
..B4.131:
        movl      (%ebx), %eax
        cmpl      (%esi), %eax
        jbe       ..B4.133
..B4.132:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      12(%esi)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B4.134
..B4.133:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      12(%esi)
        faddp     %st, %st(1)
..B4.134:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     236(%esp)
        fldt      236(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     248(%esp)
        jmp       ..B4.141
..B4.135:
        movl      4(%ebx), %eax
        movl      %eax, 44(%esp)
..B4.136:
        cmpl      $0, 44(%esp)
        jne       ..B4.138
..B4.204:
        cmpl      $0, (%ebx)
..B4.137:
        je        ..B4.139
..B4.138:
        movl      72(%esp), %eax
        lea       -16517(%eax,%edx), %edi
        movl      68(%esp), %edx
        lea       -16383(%edx,%ecx), %ecx
        cmpl      %ecx, %edi
        jl        ..B4.140
..B4.139:
        movl      (%esi), %eax
        movl      %eax, 236(%esp)
        movl      4(%esi), %edx
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      16(%esi), %edi
        movl      20(%esi), %eax
        movl      24(%esi), %esi
        movl      %edx, 240(%esp)
        movl      %ecx, 244(%esp)
        movl      %ebx, 248(%esp)
        movl      %edi, 252(%esp)
        movl      %eax, 256(%esp)
        movl      %esi, 260(%esp)
        jmp       ..B4.141
..B4.140:
        movl      (%ebx), %eax
        movl      %eax, 236(%esp)
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      12(%ebx), %esi
        movl      16(%ebx), %edi
        movl      20(%ebx), %eax
        movl      24(%ebx), %ebx
        movl      %edx, 240(%esp)
        movl      %ecx, 244(%esp)
        movl      %esi, 248(%esp)
        movl      %edi, 252(%esp)
        movl      %eax, 256(%esp)
        movl      %ebx, 260(%esp)
..B4.141:
        movzwl    216(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.145
..B4.142:
        cmpl      $24383, %eax
        jg        ..B4.145
..B4.143:
        movzwl    244(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.145
..B4.144:
        cmpl      $24383, %eax
        jle       ..B4.147
..B4.145:
        addl      $4, %esp
        lea       204(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.146:
        addl      $4, %esp
        lea       232(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.147:
        fldt      208(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      232(%esp), %edx
        fsubr     %st, %st(3)
        movzwl    188(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      260(%esp), %edx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      236(%esp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %edx, 288(%esp)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fmul      %st, %st(3)
        fld       %st(2)
        fsub      %st(1), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st(2), %st
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      248(%esp)
        fmulp     %st, %st(4)
        fldt      220(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     264(%esp)
        fldt      264(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     276(%esp)
        fldt      276(%esp)
        jl        ..B4.151
..B4.148:
        cmpl      $24383, %eax
        jg        ..B4.151
..B4.149:
        movzwl    272(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B4.151
..B4.150:
        cmpl      $24383, %eax
        jle       ..B4.154
..B4.151:
        fstp      %st(1)
        fstp      %st(0)
        addl      $4, %esp
        lea       176(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.152:
        addl      $4, %esp
        lea       260(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.153:
        fldt      264(%esp)
        fldt      276(%esp)
        movl      288(%esp), %edx
..B4.154:
        fldt      180(%esp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      204(%esp), %ecx
        fsubr     %st(4), %st
        subl      %edx, %ecx
        faddp     %st, %st(1)
        fld       %st(0)
        decl      %ecx
        movl      %ecx, 316(%esp)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(3), %st
        fsubr     %st, %st(7)
        faddp     %st, %st(7)
        fxch      %st(2)
        fsub      %st(6), %st
        fld       %st(2)
        fmul      %st(7), %st
        fxch      %st(2)
        fmul      %st, %st(7)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(7)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(4)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      192(%esp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      64(%esp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     292(%esp)
        fldt      292(%esp)
        fsubr     %st, %st(2)
        movzwl    300(%esp), %eax
        fxch      %st(1)
        fadd      %st, %st(2)
        andl      $32767, %eax
        fxch      %st(2)
        fstpt     304(%esp)
        fldt      304(%esp)
        cmpl      $383, %eax
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(1)
        jl        ..B4.156
..B4.155:
        cmpl      $32383, %eax
        jle       ..B4.158
..B4.156:
        fstp      %st(0)
        fstp      %st(0)
        addl      $4, %esp
        lea       288(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.157:
        fldt      292(%esp)
        fldt      304(%esp)
        movl      316(%esp), %ecx
..B4.158:
        testb     $1, %cl
        jne       ..B4.196
..B4.159:
        movl      92(%esp), %eax
        fldt      48+_CONSTANTS@GOTOFF(%eax)
..B4.160:
        fldt      80(%esp)
        fld       %st(3)
        fsqrt     
        lea       320(%esp), %eax
        fmul      %st, %st(1)
        lea       348(%esp), %edx
        fld       %st(1)
        sarl      $1, %ecx
        fsubr     %st(1), %st
        movl      %ecx, 52(%eax)
        faddp     %st, %st(2)
        fld       %st(1)
        fsubr     %st(1), %st
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     28(%eax)
        fldt      28(%eax)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     40(%eax)
        call      asinpoly
..B4.161:
        movl      92(%esp), %eax
        xorl      %ebx, %ebx
        movl      344(%esp), %esi
        incl      %esi
        fldt      60+_CONSTANTS@GOTOFF(%eax)
        movzwl    68+_CONSTANTS@GOTOFF(%eax), %edi
        andl      $32767, %edi
        movl      %esi, 344(%esp)
        cmpl      $8383, %edi
        movl      %ebx, 400(%esp)
        fstpt     376(%esp)
        fldt      72+_CONSTANTS@GOTOFF(%eax)
        fstpt     388(%esp)
        jl        ..B4.165
..B4.162:
        cmpl      $24383, %edi
        jg        ..B4.165
..B4.163:
        movzwl    328(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B4.165
..B4.164:
        cmpl      $24383, %ecx
        jle       ..B4.168
..B4.165:
        addl      $4, %esp
        lea       372(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.166:
        addl      $4, %esp
        lea       316(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B4.167:
        movzwl    384(%esp), %edi
        movzwl    328(%esp), %ecx
        andl      $32767, %edi
        movl      344(%esp), %esi
        andl      $32767, %ecx
        movl      400(%esp), %ebx
..B4.168:
        lea       (%ebx,%edi), %eax
        movl      %eax, 44(%esp)
        lea       -134(%esi,%ecx), %edx
        cmpl      %edx, %eax
        jle       ..B4.185
..B4.169:
        movl      380(%esp), %edx
        lea       134(%esi,%ecx), %eax
        cmpl      44(%esp), %eax
        jle       ..B4.186
..B4.170:
        testl     %edx, %edx
        jne       ..B4.172
..B4.171:
        cmpl      $0, 376(%esp)
        je        ..B4.187
..B4.172:
        movl      324(%esp), %eax
        testl     %eax, %eax
        jne       ..B4.174
..B4.173:
        cmpl      $0, 320(%esp)
        je        ..B4.186
..B4.174:
        cmpl      %esi, %ebx
        je        ..B4.176
..B4.175:
        fldt      48(%esp)
        subl      %esi, %ebx
        fstpt     32(%esp)
        addl      $16383, %ebx
        fldt      376(%esp)
        andl      $32767, %ebx
        fldt      388(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %ebx, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     376(%esp)
        fldt      376(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    384(%esp), %edi
        movl      %esi, 400(%esp)
        andl      $32767, %edi
        fstpt     388(%esp)
        fldt      388(%esp)
        jmp       ..B4.177
..B4.176:
        fldt      376(%esp)
        fldt      388(%esp)
..B4.177:
        fldt      320(%esp)
        fld       %st(2)
        movl      %esi, 68(%esp)
        cmpl      %ecx, %edi
        fsub      %st(1), %st
        jg        ..B4.182
..B4.178:
        jne       ..B4.183
..B4.179:
        movl      380(%esp), %edx
        cmpl      %eax, %edx
        ja        ..B4.182
..B4.180:
        jne       ..B4.183
..B4.181:
        movl      376(%esp), %eax
        cmpl      320(%esp), %eax
        jbe       ..B4.183
..B4.182:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      332(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B4.184
..B4.183:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      332(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B4.184:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     44(%esp)
        fldt      44(%esp)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     56(%esp)
        fldt      56(%esp)
        jmp       ..B4.193
..B4.185:
        movl      380(%esp), %edx
..B4.186:
        testl     %edx, %edx
        jne       ..B4.188
..B4.205:
        cmpl      $0, 376(%esp)
..B4.187:
        je        ..B4.189
..B4.188:
        lea       -16517(%esi,%ecx), %eax
        lea       -16383(%ebx,%edi), %ecx
        cmpl      %ecx, %eax
        jl        ..B4.190
..B4.189:
        fldt      320(%esp)
        fchs      
        movl      %esi, 68(%esp)
        fstpt     44(%esp)
        fldt      44(%esp)
        fldt      332(%esp)
        fchs      
        fstpt     56(%esp)
        fldt      56(%esp)
        jmp       ..B4.193
..B4.190:
        movl      %edx, 48(%esp)
        movl      396(%esp), %esi
        movl      376(%esp), %eax
        movl      384(%esp), %edx
        movl      388(%esp), %ecx
        movl      392(%esp), %ebx
        movl      %esi, 64(%esp)
        movl      %eax, 44(%esp)
        movl      %edx, 52(%esp)
        fldt      44(%esp)
        movl      400(%esp), %esi
        movl      %ecx, 56(%esp)
        movl      %ebx, 60(%esp)
        fldt      56(%esp)
        movl      %esi, 68(%esp)
        jmp       ..B4.193
..B4.191:
        lea       44(%esp), %eax
        lea       404(%esp), %edx
        call      asinpoly
..B4.192:
        fldt      44(%esp)
        fldt      56(%esp)
        movl      68(%esp), %esi
..B4.193:
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      %esi, 24(%esp)
        call      __libm_scalbl_k80
..B4.194:
        movl      8(%ebp), %eax
        movl      92(%esp), %ebx
        movl      432(%esp), %edi
        movzbl    9(%eax), %ecx
        xorl      %esp, %edi
        andl      $128, %ecx
        shrl      $7, %ecx
        cmpl      %gs:20, %edi
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %esi
        fldt      _CONSTANTS@GOTOFF(%ebx,%esi)
        fmulp     %st, %st(1)
        jne       ..B4.197
..B4.195:
        addl      $436, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B4.196:
        movl      92(%esp), %eax
        incl      %ecx
        movl      %ecx, 316(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%eax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     292(%esp)
        fldt      292(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fstpt     304(%esp)
        fldt      304(%esp)
        fxch      %st(1)
        fxch      %st(2)
        jmp       ..B4.160
..B4.197:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__libm_recasinl_k80,@function
	.size	__libm_recasinl_k80,.-__libm_recasinl_k80
	.data
# -- End  __libm_recasinl_k80
	.section .rodata, "a"
	.align 16
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	32768
	.word	16415
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	49717
	.word	8552
	.word	55970
	.word	51471
	.word	16383
	.word	0
	.word	36027
	.word	64655
	.word	30161
	.word	60646
	.word	49085
	.word	0
	.word	0
	.word	0
	.word	43691
	.word	43690
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	39322
	.word	39321
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	28087
	.word	46811
	.word	16378
	.word	0
	.word	0
	.word	0
	.word	36409
	.word	63715
	.word	16377
	.word	0
	.word	62186
	.word	9163
	.word	53620
	.word	46917
	.word	16377
	.word	0
	.word	11943
	.word	18321
	.word	25215
	.word	36391
	.word	16377
	.word	0
	.word	12527
	.word	63486
	.word	51909
	.word	58572
	.word	16376
	.word	0
	.word	16437
	.word	53165
	.word	60799
	.word	48451
	.word	16376
	.word	0
	.word	39560
	.word	816
	.word	43435
	.word	40940
	.word	16376
	.word	0
	.word	47763
	.word	38463
	.word	31161
	.word	35220
	.word	16376
	.word	0
	.word	61035
	.word	37666
	.word	58653
	.word	60832
	.word	16375
	.word	0
	.word	4110
	.word	2419
	.word	35453
	.word	57515
	.word	16375
	.word	0
	.word	3126
	.word	47859
	.word	6163
	.word	61068
	.word	16374
	.word	0
	.word	56145
	.word	58008
	.word	18829
	.word	55579
	.word	16376
	.word	0
	.word	17944
	.word	24712
	.word	25688
	.word	38195
	.word	49145
	.word	0
	.word	27414
	.word	34244
	.word	64801
	.word	51841
	.word	16378
	.word	0
	.word	63442
	.word	25896
	.word	35822
	.word	55851
	.word	49146
	.word	0
	.word	37640
	.word	1996
	.word	32075
	.word	42748
	.word	16378
	.word	0
	.word	39460
	.word	64770
	.word	43690
	.word	43690
	.word	49115
	.word	0
	.word	48327
	.word	21484
	.word	52058
	.word	52428
	.word	49114
	.word	0
	.word	52692
	.word	45548
	.word	47571
	.word	37453
	.word	49112
	.word	0
	.word	9214
	.word	45875
	.word	56244
	.word	56360
	.word	49110
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,348
	.data
	.hidden __libm_normalizel_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
