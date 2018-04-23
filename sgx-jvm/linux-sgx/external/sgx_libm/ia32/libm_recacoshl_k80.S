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
	.file "libm_recacoshl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_recacoshl_k80
	.text
       .align    16,0x90
	.hidden __libm_recacoshl_k80
	.globl __libm_recacoshl_k80
__libm_recacoshl_k80:
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
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $436, %esp
        movl      8(%ebp), %ecx
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        movl      %eax, 76(%esp)
        movzwl    8(%ecx), %eax
        andl      $32767, %eax
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 428(%esp)
        cmpl      $16383, %eax
        movl      24(%ebp), %ebx
        movl      40(%ebp), %esi
        jl        ..B1.33
..B1.2:
        movzwl    8(%esi), %ecx
        movl      %ecx, %edi
        andl      $32767, %edi
        cmpl      $8383, %edi
        jl        ..B1.6
..B1.3:
        cmpl      $24383, %edi
        jg        ..B1.6
..B1.4:
        movzwl    8(%ebx), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.6
..B1.5:
        cmpl      $24383, %edx
        jle       ..B1.9
..B1.6:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B1.7:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.8:
        movzwl    8(%esi), %ecx
        movl      %ecx, %edi
        movzwl    8(%ebx), %edx
        andl      $32767, %edi
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B1.9:
        movl      24(%ebx), %eax
        movl      24(%esi), %esi
        movl      %eax, 52(%esp)
        movl      %esi, 48(%esp)
        lea       -134(%eax,%edx), %eax
        addl      %edi, %esi
        movl      %esi, 36(%esp)
        cmpl      %esi, %eax
        movl      40(%ebp), %esi
        jge       ..B1.26
..B1.10:
        movl      4(%esi), %eax
        movl      %eax, 32(%esp)
        movl      52(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      36(%esp), %eax
        jle       ..B1.27
..B1.11:
        cmpl      $0, 32(%esp)
        jne       ..B1.13
..B1.12:
        cmpl      $0, (%esi)
        je        ..B1.28
..B1.13:
        cmpl      $0, 4(%ebx)
        jne       ..B1.15
..B1.14:
        cmpl      $0, (%ebx)
        je        ..B1.27
..B1.15:
        movl      52(%esp), %eax
        cmpl      48(%esp), %eax
        je        ..B1.17
..B1.16:
        fldt      (%esi)
        fldt      12(%esi)
        movl      48(%esp), %ecx
        movl      %eax, %edx
        subl      %edx, %ecx
        andb      $127, 41(%esp)
        addl      $16383, %ecx
        movzwl    40(%esp), %eax
        andl      $32767, %ecx
        andl      $-32768, %eax
        orl       %ecx, %eax
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
        movl      24(%ebx), %edi
        movzwl    8(%ebx), %eax
        movl      %edi, 52(%esp)
        movzwl    8(%esi), %ecx
        movl      %eax, 44(%esp)
        jmp       ..B1.18
..B1.17:
        fldt      (%esi)
        fldt      12(%esi)
..B1.18:
        fldt      (%ebx)
        andl      $32767, %ecx
        movl      44(%esp), %edx
        fld       %st(2)
        andl      $32767, %edx
        fadd      %st(1), %st
        movl      52(%esp), %eax
        cmpl      %edx, %ecx
        movl      %eax, 116(%esp)
        jg        ..B1.23
..B1.19:
        jne       ..B1.24
..B1.20:
        movl      4(%esi), %eax
        cmpl      4(%ebx), %eax
        ja        ..B1.23
..B1.21:
        jne       ..B1.24
..B1.22:
        movl      (%esi), %eax
        cmpl      (%ebx), %eax
        jbe       ..B1.24
..B1.23:
        fldt      12(%ebx)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B1.25
..B1.24:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B1.25:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     92(%esp)
        fldt      92(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     104(%esp)
        jmp       ..B1.32
..B1.26:
        movl      4(%esi), %eax
        movl      %eax, 32(%esp)
..B1.27:
        cmpl      $0, 32(%esp)
        jne       ..B1.29
..B1.225:
        cmpl      $0, (%esi)
..B1.28:
        je        ..B1.30
..B1.29:
        movl      52(%esp), %eax
        lea       -16517(%eax,%edx), %ecx
        movl      48(%esp), %edx
        lea       -16383(%edx,%edi), %edi
        cmpl      %edi, %ecx
        jl        ..B1.31
..B1.30:
        movl      (%ebx), %eax
        movl      %eax, 92(%esp)
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      12(%ebx), %esi
        movl      16(%ebx), %edi
        movl      20(%ebx), %eax
        movl      24(%ebx), %ebx
        movl      %edx, 96(%esp)
        movl      %ecx, 100(%esp)
        movl      %esi, 104(%esp)
        movl      %edi, 108(%esp)
        movl      %eax, 112(%esp)
        movl      %ebx, 116(%esp)
        jmp       ..B1.32
..B1.31:
        movl      (%esi), %eax
        movl      %eax, 92(%esp)
        movl      4(%esi), %edx
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      16(%esi), %edi
        movl      20(%esi), %eax
        movl      24(%esi), %esi
        movl      %edx, 96(%esp)
        movl      %ecx, 100(%esp)
        movl      %ebx, 104(%esp)
        movl      %edi, 108(%esp)
        movl      %eax, 112(%esp)
        movl      %esi, 116(%esp)
..B1.32:
        movl      76(%esp), %eax
        movl      $0, 144(%esp)
        fldt      .L_2il0floatpacket.10@GOTOFF(%eax)
        fstpt     120(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%eax)
        fstpt     132(%esp)
        fldt      132(%esp)
        jmp       ..B1.63
..B1.33:
        movl      32(%ebp), %eax
        movl      4(%eax), %ecx
        movl      8(%eax), %edi
        movl      %ecx, 96(%esp)
        movl      16(%eax), %ecx
        movl      %edi, 100(%esp)
        movl      20(%eax), %edi
        movl      %ecx, 108(%esp)
        movl      (%eax), %edx
        movzwl    8(%esi), %ecx
        movl      %edi, 112(%esp)
        movl      %ecx, %edi
        movl      %edx, 92(%esp)
        andl      $32767, %edi
        movl      12(%eax), %edx
        cmpl      $8383, %edi
        movl      24(%eax), %eax
        movl      %edx, 104(%esp)
        movl      %eax, 116(%esp)
        jl        ..B1.37
..B1.34:
        cmpl      $24383, %edi
        jg        ..B1.37
..B1.35:
        movzwl    8(%ebx), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.37
..B1.36:
        cmpl      $24383, %edx
        jle       ..B1.40
..B1.37:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B1.38:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.39:
        movzwl    8(%esi), %ecx
        movl      %ecx, %edi
        movzwl    8(%ebx), %edx
        andl      $32767, %edi
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B1.40:
        movl      24(%ebx), %eax
        movl      24(%esi), %esi
        movl      %eax, 52(%esp)
        movl      %esi, 48(%esp)
        lea       -134(%eax,%edx), %eax
        addl      %edi, %esi
        movl      %esi, 36(%esp)
        cmpl      %esi, %eax
        movl      40(%ebp), %esi
        jge       ..B1.57
..B1.41:
        movl      4(%esi), %eax
        movl      %eax, 32(%esp)
        movl      52(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      36(%esp), %eax
        jle       ..B1.58
..B1.42:
        cmpl      $0, 32(%esp)
        jne       ..B1.44
..B1.43:
        cmpl      $0, (%esi)
        je        ..B1.59
..B1.44:
        cmpl      $0, 4(%ebx)
        jne       ..B1.46
..B1.45:
        cmpl      $0, (%ebx)
        je        ..B1.58
..B1.46:
        movl      52(%esp), %eax
        cmpl      48(%esp), %eax
        je        ..B1.48
..B1.47:
        fldt      (%esi)
        fldt      12(%esi)
        movl      48(%esp), %ecx
        movl      %eax, %edx
        subl      %edx, %ecx
        andb      $127, 41(%esp)
        addl      $16383, %ecx
        movzwl    40(%esp), %eax
        andl      $32767, %ecx
        andl      $-32768, %eax
        orl       %ecx, %eax
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
        movl      24(%ebx), %edi
        movzwl    8(%ebx), %eax
        movl      %edi, 52(%esp)
        movzwl    8(%esi), %ecx
        movl      %eax, 44(%esp)
        jmp       ..B1.49
..B1.48:
        fldt      (%esi)
        fldt      12(%esi)
..B1.49:
        fldt      (%ebx)
        andl      $32767, %ecx
        movl      44(%esp), %edx
        fld       %st(2)
        andl      $32767, %edx
        fsub      %st(1), %st
        movl      52(%esp), %eax
        cmpl      %edx, %ecx
        movl      %eax, 144(%esp)
        jg        ..B1.54
..B1.50:
        jne       ..B1.55
..B1.51:
        movl      4(%esi), %eax
        cmpl      4(%ebx), %eax
        ja        ..B1.54
..B1.52:
        jne       ..B1.55
..B1.53:
        movl      (%esi), %eax
        cmpl      (%ebx), %eax
        jbe       ..B1.55
..B1.54:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      12(%ebx)
        fsubrp    %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B1.56
..B1.55:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%ebx)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B1.56:
        fld       %st(1)
        movl      76(%esp), %eax
        fadd      %st(1), %st
        fstpt     120(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     132(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%eax)
        jmp       ..B1.63
..B1.57:
        movl      4(%esi), %eax
        movl      %eax, 32(%esp)
..B1.58:
        cmpl      $0, 32(%esp)
        jne       ..B1.60
..B1.224:
        cmpl      $0, (%esi)
..B1.59:
        je        ..B1.61
..B1.60:
        movl      52(%esp), %eax
        lea       -16517(%eax,%edx), %ecx
        movl      48(%esp), %edx
        lea       -16383(%edx,%edi), %edi
        cmpl      %edi, %ecx
        jl        ..B1.62
..B1.61:
        fldt      (%ebx)
        movl      76(%esp), %edx
        fchs      
        movl      52(%esp), %eax
        movl      %eax, 144(%esp)
        fstpt     120(%esp)
        fldt      12(%ebx)
        fchs      
        fstpt     132(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%edx)
        jmp       ..B1.63
..B1.62:
        movl      (%esi), %eax
        movl      %eax, 120(%esp)
        movl      20(%esi), %eax
        movl      %eax, 140(%esp)
        movl      76(%esp), %eax
        movl      4(%esi), %edx
        movl      8(%esi), %ecx
        fldt      .L_2il0floatpacket.9@GOTOFF(%eax)
        movl      12(%esi), %ebx
        movl      16(%esi), %edi
        movl      24(%esi), %esi
        movl      %edx, 124(%esp)
        movl      %ecx, 128(%esp)
        movl      %ebx, 132(%esp)
        movl      %edi, 136(%esp)
        movl      %esi, 144(%esp)
..B1.63:
        movl      44(%ebp), %edx
        movl      76(%esp), %eax
        movl      $0, 172(%esp)
        movzwl    8(%edx), %ecx
        movl      %ecx, %ebx
        fldt      .L_2il0floatpacket.11@GOTOFF(%eax)
        andl      $32767, %ebx
        fstpt     148(%esp)
        cmpl      $8383, %ebx
        fstpt     160(%esp)
        jl        ..B1.67
..B1.64:
        cmpl      $24383, %ebx
        jg        ..B1.67
..B1.65:
        movzwl    156(%esp), %edx
        movl      %edx, 48(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.67
..B1.66:
        cmpl      $24383, %edx
        jle       ..B1.70
..B1.67:
        addl      $4, %esp
        pushl     44(%ebp)
        call      __libm_normalizel_k80
..B1.68:
        addl      $4, %esp
        lea       144(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.69:
        movl      44(%ebp), %ecx
        movzwl    156(%esp), %edx
        movl      %edx, 48(%esp)
        andl      $32767, %edx
        movzwl    8(%ecx), %ecx
        movl      %ecx, %ebx
        andl      $32767, %ebx
..B1.70:
        movl      44(%ebp), %esi
        movl      172(%esp), %eax
        movl      24(%esi), %edi
        movl      %edi, 56(%esp)
        lea       (%ebx,%edi), %esi
        movl      %esi, 52(%esp)
        lea       -134(%eax,%edx), %edi
        cmpl      %edi, %esi
        jle       ..B1.87
..B1.71:
        movl      44(%ebp), %esi
        movl      4(%esi), %edi
        lea       134(%eax,%edx), %esi
        movl      %edi, 44(%esp)
        cmpl      52(%esp), %esi
        jle       ..B1.88
..B1.72:
        cmpl      $0, 44(%esp)
        jne       ..B1.74
..B1.73:
        movl      44(%ebp), %esi
        cmpl      $0, (%esi)
        je        ..B1.89
..B1.74:
        cmpl      $0, 152(%esp)
        jne       ..B1.76
..B1.75:
        cmpl      $0, 148(%esp)
        je        ..B1.88
..B1.76:
        cmpl      56(%esp), %eax
        je        ..B1.78
..B1.77:
        movl      56(%esp), %edx
        subl      %eax, %edx
        andb      $127, 41(%esp)
        addl      $16383, %edx
        movl      44(%ebp), %ebx
        andl      $32767, %edx
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        fldt      (%ebx)
        orl       %edx, %ecx
        fldt      12(%ebx)
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %eax, 24(%ebx)
        fxch      %st(2)
        fstpt     (%ebx)
        fldt      (%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     12(%ebx)
        fldt      12(%ebx)
        movzwl    8(%ebx), %ecx
        jmp       ..B1.79
..B1.78:
        movl      44(%ebp), %edx
        fldt      (%edx)
        fldt      12(%edx)
..B1.79:
        fldt      148(%esp)
        andl      $32767, %ecx
        movl      %eax, 200(%esp)
        fld       %st(2)
        movl      48(%esp), %eax
        fadd      %st(1), %st
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jg        ..B1.84
..B1.80:
        movl      48(%esp), %eax
        andl      $32767, %eax
        cmpl      %eax, %ecx
        jne       ..B1.85
..B1.81:
        movl      44(%ebp), %eax
        movl      4(%eax), %edx
        cmpl      152(%esp), %edx
        ja        ..B1.84
..B1.82:
        jne       ..B1.85
..B1.83:
        movl      44(%ebp), %eax
        movl      (%eax), %edx
        cmpl      148(%esp), %edx
        jbe       ..B1.85
..B1.84:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      160(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.86
..B1.85:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      160(%esp)
        faddp     %st, %st(1)
..B1.86:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     176(%esp)
        fldt      176(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     188(%esp)
        jmp       ..B1.93
..B1.87:
        movl      44(%ebp), %ecx
        movl      4(%ecx), %esi
        movl      %esi, 44(%esp)
..B1.88:
        cmpl      $0, 44(%esp)
        jne       ..B1.90
..B1.226:
        movl      44(%ebp), %ecx
        cmpl      $0, (%ecx)
..B1.89:
        je        ..B1.91
..B1.90:
        lea       -16517(%eax,%edx), %ecx
        movl      56(%esp), %edx
        lea       -16383(%edx,%ebx), %ebx
        cmpl      %ebx, %ecx
        jl        ..B1.92
..B1.91:
        movl      148(%esp), %edx
        movl      %edx, 176(%esp)
        movl      152(%esp), %ecx
        movl      156(%esp), %ebx
        movl      160(%esp), %esi
        movl      164(%esp), %edi
        movl      168(%esp), %edx
        movl      %ecx, 180(%esp)
        movl      %ebx, 184(%esp)
        movl      %esi, 188(%esp)
        movl      %edi, 192(%esp)
        movl      %edx, 196(%esp)
        movl      %eax, 200(%esp)
        jmp       ..B1.93
..B1.92:
        movl      44(%ebp), %eax
        movl      (%eax), %edx
        movl      %edx, 176(%esp)
        movl      4(%eax), %ecx
        movl      8(%eax), %ebx
        movl      12(%eax), %esi
        movl      16(%eax), %edi
        movl      20(%eax), %edx
        movl      24(%eax), %eax
        movl      %ecx, 180(%esp)
        movl      %ebx, 184(%esp)
        movl      %esi, 188(%esp)
        movl      %edi, 192(%esp)
        movl      %edx, 196(%esp)
        movl      %eax, 200(%esp)
..B1.93:
        movzwl    184(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.97
..B1.94:
        cmpl      $24383, %eax
        jg        ..B1.97
..B1.95:
        movzwl    100(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.97
..B1.96:
        cmpl      $24383, %eax
        jle       ..B1.99
..B1.97:
        addl      $4, %esp
        lea       172(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.98:
        addl      $4, %esp
        lea       88(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.99:
        fldt      176(%esp)
        movl      76(%esp), %edx
        fld       %st(0)
        fld       %st(1)
        fldt      92(%esp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.12@GOTOFF(%edx)
        fld       %st(0)
        fmul      %st(6), %st
        movl      36(%ebp), %esi
        fsubr     %st, %st(5)
        movzwl    8(%esi), %edi
        movl      %edi, %ecx
        movl      200(%esp), %eax
        andl      $32767, %ecx
        addl      116(%esp), %eax
        faddp     %st, %st(5)
        fld       %st(4)
        fld       %st(3)
        fxch      %st(2)
        fstpt     48(%esp)
        fldt      48(%esp)
        cmpl      $8383, %ecx
        fmul      %st(4), %st
        movl      28(%ebp), %ebx
        fsubr     %st, %st(3)
        movl      %eax, 228(%esp)
        faddp     %st, %st(3)
        fmul      %st(2), %st
        fxch      %st(4)
        fsub      %st(5), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(5)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(4), %st
        fsub      %st, %st(4)
        fxch      %st(4)
        fchs      
        faddp     %st, %st(5)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fldt      104(%esp)
        fmulp     %st, %st(4)
        fldt      188(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     204(%esp)
        fldt      204(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     216(%esp)
        fstpt     64(%esp)
        jl        ..B1.103
..B1.100:
        cmpl      $24383, %ecx
        jg        ..B1.103
..B1.101:
        movzwl    8(%ebx), %edx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.103
..B1.102:
        cmpl      $24383, %edx
        jle       ..B1.106
..B1.103:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B1.104:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B1.105:
        movzwl    8(%esi), %edi
        movl      %edi, %ecx
        movzwl    8(%ebx), %edx
        andl      $32767, %ecx
        movl      %edx, 60(%esp)
        andl      $32767, %edx
..B1.106:
        movl      24(%ebx), %eax
        movl      24(%esi), %esi
        movl      %eax, 88(%esp)
        movl      %esi, 84(%esp)
        lea       -134(%eax,%edx), %eax
        addl      %ecx, %esi
        movl      %esi, 80(%esp)
        cmpl      %eax, %esi
        movl      36(%ebp), %esi
        jle       ..B1.123
..B1.107:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
        movl      88(%esp), %eax
        lea       134(%eax,%edx), %eax
        cmpl      80(%esp), %eax
        jle       ..B1.124
..B1.108:
        cmpl      $0, 44(%esp)
        jne       ..B1.110
..B1.109:
        cmpl      $0, (%esi)
        je        ..B1.125
..B1.110:
        cmpl      $0, 4(%ebx)
        jne       ..B1.112
..B1.111:
        cmpl      $0, (%ebx)
        je        ..B1.124
..B1.112:
        movl      88(%esp), %eax
        cmpl      84(%esp), %eax
        je        ..B1.114
..B1.113:
        fldt      64(%esp)
        movl      84(%esp), %edi
        movl      %eax, %edx
        subl      %edx, %edi
        fstpt     32(%esp)
        addl      $16383, %edi
        fldt      (%esi)
        andl      $32767, %edi
        fldt      12(%esi)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %edi, %eax
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
        movl      24(%ebx), %ecx
        movzwl    8(%ebx), %eax
        movl      %ecx, 88(%esp)
        movzwl    8(%esi), %edi
        movl      %eax, 60(%esp)
        jmp       ..B1.115
..B1.114:
        fldt      (%esi)
        fldt      12(%esi)
..B1.115:
        fldt      (%ebx)
        andl      $32767, %edi
        movl      60(%esp), %edx
        fld       %st(2)
        andl      $32767, %edx
        fadd      %st(1), %st
        movl      88(%esp), %eax
        cmpl      %edx, %edi
        movl      %eax, 256(%esp)
        jg        ..B1.120
..B1.116:
        jne       ..B1.121
..B1.117:
        movl      4(%esi), %eax
        cmpl      4(%ebx), %eax
        ja        ..B1.120
..B1.118:
        jne       ..B1.121
..B1.119:
        movl      (%esi), %eax
        cmpl      (%ebx), %eax
        jbe       ..B1.121
..B1.120:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      12(%ebx)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.122
..B1.121:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      12(%ebx)
        faddp     %st, %st(1)
..B1.122:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     232(%esp)
        fldt      232(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     244(%esp)
        jmp       ..B1.129
..B1.123:
        movl      4(%esi), %eax
        movl      %eax, 44(%esp)
..B1.124:
        cmpl      $0, 44(%esp)
        jne       ..B1.126
..B1.227:
        cmpl      $0, (%esi)
..B1.125:
        je        ..B1.127
..B1.126:
        movl      88(%esp), %eax
        lea       -16517(%eax,%edx), %edi
        movl      84(%esp), %edx
        lea       -16383(%edx,%ecx), %ecx
        cmpl      %ecx, %edi
        jl        ..B1.128
..B1.127:
        movl      (%ebx), %eax
        movl      %eax, 232(%esp)
        movl      4(%ebx), %edx
        movl      8(%ebx), %ecx
        movl      12(%ebx), %esi
        movl      16(%ebx), %edi
        movl      20(%ebx), %eax
        movl      24(%ebx), %ebx
        movl      %edx, 236(%esp)
        movl      %ecx, 240(%esp)
        movl      %esi, 244(%esp)
        movl      %edi, 248(%esp)
        movl      %eax, 252(%esp)
        movl      %ebx, 256(%esp)
        jmp       ..B1.129
..B1.128:
        movl      (%esi), %eax
        movl      %eax, 232(%esp)
        movl      4(%esi), %edx
        movl      8(%esi), %ecx
        movl      12(%esi), %ebx
        movl      16(%esi), %edi
        movl      20(%esi), %eax
        movl      24(%esi), %esi
        movl      %edx, 236(%esp)
        movl      %ecx, 240(%esp)
        movl      %ebx, 244(%esp)
        movl      %edi, 248(%esp)
        movl      %eax, 252(%esp)
        movl      %esi, 256(%esp)
..B1.129:
        movzwl    240(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.133
..B1.130:
        cmpl      $24383, %eax
        jg        ..B1.133
..B1.131:
        movzwl    128(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.133
..B1.132:
        cmpl      $24383, %eax
        jle       ..B1.135
..B1.133:
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.134:
        addl      $4, %esp
        lea       116(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.135:
        fldt      232(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        movl      256(%esp), %edx
        fsubr     %st, %st(3)
        movzwl    212(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        addl      144(%esp), %edx
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      120(%esp)
        cmpl      $8383, %eax
        fmul      %st, %st(2)
        fld       %st(0)
        movl      %edx, 284(%esp)
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
        fldt      132(%esp)
        fmulp     %st, %st(4)
        fldt      244(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     260(%esp)
        fldt      260(%esp)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     272(%esp)
        fldt      272(%esp)
        jl        ..B1.139
..B1.136:
        cmpl      $24383, %eax
        jg        ..B1.139
..B1.137:
        movzwl    268(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B1.139
..B1.138:
        cmpl      $24383, %eax
        jle       ..B1.142
..B1.139:
        fstp      %st(1)
        fstp      %st(0)
        addl      $4, %esp
        lea       200(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.140:
        addl      $4, %esp
        lea       256(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.141:
        fldt      260(%esp)
        fldt      272(%esp)
        movl      284(%esp), %edx
..B1.142:
        fldt      204(%esp)
        fld       %st(2)
        fdivr     %st(1), %st
        fmul      %st, %st(2)
        fld       %st(3)
        fxch      %st(4)
        fstpt     80(%esp)
        fldt      80(%esp)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        movl      228(%esp), %eax
        fsubr     %st(4), %st
        subl      %edx, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        decl      %eax
        movl      %eax, 312(%esp)
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
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fsubrp    %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      216(%esp)
        faddp     %st, %st(1)
        fsubp     %st, %st(1)
        fldt      80(%esp)
        fdivrp    %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     288(%esp)
        fldt      288(%esp)
        fsubrp    %st, %st(2)
        movzwl    296(%esp), %esi
        movl      %esi, %ebx
        andl      $32767, %ebx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     300(%esp)
        cmpl      $8383, %ebx
        fstpt     64(%esp)
        jl        ..B1.146
..B1.143:
        cmpl      $24383, %ebx
        jg        ..B1.146
..B1.144:
        movzwl    156(%esp), %ecx
        movl      %ecx, 60(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B1.146
..B1.145:
        cmpl      $24383, %ecx
        jle       ..B1.149
..B1.146:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.147:
        addl      $4, %esp
        lea       144(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.148:
        movzwl    296(%esp), %esi
        movl      %esi, %ebx
        movzwl    156(%esp), %ecx
        andl      $32767, %ebx
        movl      312(%esp), %eax
        movl      %ecx, 60(%esp)
        andl      $32767, %ecx
..B1.149:
        movl      172(%esp), %edi
        lea       (%eax,%ebx), %edx
        movl      %edi, 84(%esp)
        movl      %edx, 80(%esp)
        lea       -134(%edi,%ecx), %edi
        cmpl      %edi, %edx
        jle       ..B1.166
..B1.150:
        movl      84(%esp), %edi
        movl      292(%esp), %edx
        movl      %edx, 44(%esp)
        lea       134(%edi,%ecx), %edx
        cmpl      80(%esp), %edx
        jle       ..B1.167
..B1.151:
        cmpl      $0, 44(%esp)
        jne       ..B1.153
..B1.152:
        cmpl      $0, 288(%esp)
        je        ..B1.168
..B1.153:
        cmpl      $0, 152(%esp)
        jne       ..B1.155
..B1.154:
        cmpl      $0, 148(%esp)
        je        ..B1.167
..B1.155:
        cmpl      84(%esp), %eax
        je        ..B1.157
..B1.156:
        fldt      64(%esp)
        movl      %edi, %ecx
        subl      %ecx, %eax
        fstpt     32(%esp)
        addl      $16383, %eax
        fldt      288(%esp)
        andl      $32767, %eax
        fldt      300(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movl      %ecx, %eax
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     288(%esp)
        fldt      288(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movzwl    296(%esp), %esi
        movl      %esi, %ebx
        andl      $32767, %ebx
        movl      %ecx, 312(%esp)
        movl      %ebx, %ecx
        fstpt     300(%esp)
        fldt      300(%esp)
        jmp       ..B1.158
..B1.157:
        fldt      288(%esp)
        movl      %esi, %ecx
        fldt      300(%esp)
        andl      $32767, %ecx
..B1.158:
        fldt      148(%esp)
        fld       %st(2)
        movl      60(%esp), %edi
        fadd      %st(1), %st
        andl      $32767, %edi
        movl      84(%esp), %edx
        cmpl      %edi, %ecx
        movl      %edx, 340(%esp)
        jg        ..B1.163
..B1.159:
        movl      60(%esp), %edx
        andl      $32767, %esi
        andl      $32767, %edx
        cmpl      %edx, %esi
        jne       ..B1.164
..B1.160:
        movl      292(%esp), %edx
        cmpl      152(%esp), %edx
        ja        ..B1.163
..B1.161:
        jne       ..B1.164
..B1.162:
        movl      288(%esp), %edx
        cmpl      148(%esp), %edx
        jbe       ..B1.164
..B1.163:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      160(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.165
..B1.164:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      160(%esp)
        faddp     %st, %st(1)
..B1.165:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     316(%esp)
        fldt      316(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     328(%esp)
        jmp       ..B1.172
..B1.166:
        movl      292(%esp), %edx
        movl      %edx, 44(%esp)
..B1.167:
        cmpl      $0, 44(%esp)
        jne       ..B1.169
..B1.229:
        cmpl      $0, 288(%esp)
..B1.168:
        je        ..B1.170
..B1.169:
        movl      84(%esp), %edx
        lea       -16383(%eax,%ebx), %esi
        lea       -16517(%edx,%ecx), %ecx
        cmpl      %esi, %ecx
        jl        ..B1.171
..B1.170:
        movl      148(%esp), %edx
        movl      152(%esp), %ecx
        movl      156(%esp), %esi
        movl      %edx, 316(%esp)
        movl      %ecx, 320(%esp)
        movl      %esi, 324(%esp)
        movl      160(%esp), %edi
        movl      164(%esp), %edx
        movl      168(%esp), %ecx
        movl      84(%esp), %esi
        movl      %edi, 328(%esp)
        movl      %edx, 332(%esp)
        movl      %ecx, 336(%esp)
        movl      %esi, 340(%esp)
        jmp       ..B1.172
..B1.171:
        movl      288(%esp), %edx
        movl      44(%esp), %ecx
        movl      296(%esp), %esi
        movl      %edx, 316(%esp)
        movl      %ecx, 320(%esp)
        movl      %esi, 324(%esp)
        movl      300(%esp), %edi
        movl      304(%esp), %edx
        movl      308(%esp), %ecx
        movl      312(%esp), %esi
        movl      %edi, 328(%esp)
        movl      %edx, 332(%esp)
        movl      %ecx, 336(%esp)
        movl      %esi, 340(%esp)
..B1.172:
        cmpl      $8383, %ebx
        jl        ..B1.176
..B1.173:
        cmpl      $24383, %ebx
        jg        ..B1.176
..B1.174:
        movzwl    324(%esp), %edx
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B1.176
..B1.175:
        cmpl      $24383, %edx
        jle       ..B1.179
..B1.176:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.177:
        addl      $4, %esp
        lea       312(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.178:
        movl      312(%esp), %eax
..B1.179:
        fldt      288(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(4), %st
        addl      340(%esp), %eax
        fsubr     %st, %st(3)
        movl      %eax, 368(%esp)
        faddp     %st, %st(3)
        fld       %st(2)
        fxch      %st(2)
        fsub      %st(3), %st
        fldt      316(%esp)
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
        fldt      328(%esp)
        fmulp     %st, %st(4)
        fldt      300(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     344(%esp)
        fldt      344(%esp)
        fsubr     %st, %st(1)
        movzwl    352(%esp), %edx
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %edx
        fxch      %st(1)
        fstpt     356(%esp)
        fldt      356(%esp)
        cmpl      $383, %edx
        fxch      %st(1)
        fstpt     64(%esp)
        jl        ..B1.181
..B1.180:
        cmpl      $32383, %edx
        jle       ..B1.183
..B1.181:
        fstp      %st(0)
        addl      $4, %esp
        lea       340(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.182:
        fldt      344(%esp)
        fstpt     64(%esp)
        fldt      356(%esp)
        movl      368(%esp), %eax
..B1.183:
        testb     $1, %al
        jne       ..B1.220
..B1.184:
        movl      76(%esp), %edx
        fldt      .L_2il0floatpacket.13@GOTOFF(%edx)
..B1.185:
        fldt      64(%esp)
        fld       %st(0)
        fsqrt     
        fldt      48(%esp)
        fmul      %st(1), %st
        fld       %st(0)
        movzwl    296(%esp), %ebx
        movl      %ebx, %esi
        andl      $32767, %esi
        fsubr     %st(2), %st
        sarl      $1, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        cmpl      $8383, %esi
        movl      %eax, 396(%esp)
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
        fxch      %st(3)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fdiv      %st(1), %st
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     372(%esp)
        fldt      372(%esp)
        fsubrp    %st, %st(2)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     384(%esp)
        fstpt     64(%esp)
        jl        ..B1.189
..B1.186:
        cmpl      $24383, %esi
        jg        ..B1.189
..B1.187:
        movzwl    380(%esp), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B1.189
..B1.188:
        cmpl      $24383, %ecx
        jle       ..B1.192
..B1.189:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.190:
        addl      $4, %esp
        lea       368(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B1.191:
        movzwl    296(%esp), %ebx
        movl      %ebx, %esi
        movzwl    380(%esp), %ecx
        andl      $32767, %esi
        movl      396(%esp), %eax
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
..B1.192:
        movl      312(%esp), %edx
        movl      %edx, 52(%esp)
        lea       (%esi,%edx), %edi
        movl      %edi, 48(%esp)
        lea       -134(%eax,%ecx), %edx
        cmpl      %edx, %edi
        jle       ..B1.209
..B1.193:
        movl      292(%esp), %edx
        lea       134(%eax,%ecx), %edi
        cmpl      48(%esp), %edi
        jle       ..B1.210
..B1.194:
        testl     %edx, %edx
        jne       ..B1.196
..B1.195:
        cmpl      $0, 288(%esp)
        je        ..B1.211
..B1.196:
        cmpl      $0, 376(%esp)
        jne       ..B1.198
..B1.197:
        cmpl      $0, 372(%esp)
        je        ..B1.210
..B1.198:
        cmpl      52(%esp), %eax
        je        ..B1.200
..B1.199:
        fldt      64(%esp)
        movl      52(%esp), %edx
        fstpt     32(%esp)
        subl      %eax, %edx
        fldt      288(%esp)
        addl      $16383, %edx
        fldt      300(%esp)
        andl      $32767, %edx
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %edx, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     288(%esp)
        fldt      288(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      %eax, 312(%esp)
        fstpt     300(%esp)
        fldt      300(%esp)
        movzwl    296(%esp), %ebx
        jmp       ..B1.201
..B1.200:
        fldt      288(%esp)
        fldt      300(%esp)
..B1.201:
        fldt      372(%esp)
        fld       %st(2)
        movl      %eax, 424(%esp)
        movl      %ebx, %eax
        movl      44(%esp), %edx
        andl      $32767, %eax
        andl      $32767, %edx
        fadd      %st(1), %st
        cmpl      %edx, %eax
        jg        ..B1.206
..B1.202:
        movl      44(%esp), %eax
        andl      $32767, %ebx
        andl      $32767, %eax
        cmpl      %eax, %ebx
        jne       ..B1.207
..B1.203:
        movl      292(%esp), %eax
        cmpl      376(%esp), %eax
        ja        ..B1.206
..B1.204:
        jne       ..B1.207
..B1.205:
        movl      288(%esp), %eax
        cmpl      372(%esp), %eax
        jbe       ..B1.207
..B1.206:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      384(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B1.208
..B1.207:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      384(%esp)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B1.208:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     400(%esp)
        fldt      400(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     412(%esp)
        jmp       ..B1.215
..B1.209:
        movl      292(%esp), %edx
..B1.210:
        testl     %edx, %edx
        jne       ..B1.212
..B1.228:
        cmpl      $0, 288(%esp)
..B1.211:
        je        ..B1.213
..B1.212:
        lea       -16517(%eax,%ecx), %ecx
        movl      52(%esp), %eax
        lea       -16383(%eax,%esi), %ebx
        cmpl      %ebx, %ecx
        jl        ..B1.214
..B1.213:
        movl      372(%esp), %eax
        movl      %eax, 400(%esp)
        movl      376(%esp), %edx
        movl      380(%esp), %ecx
        movl      384(%esp), %ebx
        movl      388(%esp), %esi
        movl      392(%esp), %edi
        movl      396(%esp), %eax
        movl      %edx, 404(%esp)
        movl      %ecx, 408(%esp)
        movl      %ebx, 412(%esp)
        movl      %esi, 416(%esp)
        movl      %edi, 420(%esp)
        movl      %eax, 424(%esp)
        jmp       ..B1.215
..B1.214:
        movl      %edx, 404(%esp)
        movl      288(%esp), %eax
        movl      296(%esp), %edx
        movl      300(%esp), %ecx
        movl      304(%esp), %ebx
        movl      308(%esp), %esi
        movl      52(%esp), %edi
        movl      %eax, 400(%esp)
        movl      %edx, 408(%esp)
        movl      %ecx, 412(%esp)
        movl      %ebx, 416(%esp)
        movl      %esi, 420(%esp)
        movl      %edi, 424(%esp)
..B1.215:
        addl      $8, %esp
        lea       392(%esp), %eax
        lea       24(%esp), %edx
        pushl     %eax
        pushl     %edx
        call      __libm_log1p_k80
..B1.216:
        fldt      32(%esp)
        movl      56(%esp), %eax
        fstpt     (%esp)
        fldt      44(%esp)
        fstpt     12(%esp)
        movl      %eax, 24(%esp)
        call      __libm_scalbl_k80
..B1.217:
        movl      428(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.219
..B1.218:
        addl      $436, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.219:
        fstp      %st(0)
        movl      76(%esp), %ebx
        call      __stack_chk_fail@PLT
..B1.220:
        fldt      64(%esp)
        incl      %eax
        movl      76(%esp), %edx
        movl      %eax, 368(%esp)
        fldt      .L_2il0floatpacket.13@GOTOFF(%edx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     356(%esp)
        fldt      356(%esp)
        fxch      %st(1)
        fstpt     344(%esp)
        fxch      %st(1)
        jmp       ..B1.185
        .align    16,0x90
	.type	__libm_recacoshl_k80,@function
	.size	__libm_recacoshl_k80,.-__libm_recacoshl_k80
	.data
# -- End  __libm_recacoshl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.11:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.12:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.13:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,12
	.data
	.hidden __libm_log1p_k80
	.hidden __libm_normalizel_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
