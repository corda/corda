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
	.file "catanhl.c"
	.text
..TXTST0:
# -- Begin  catanhl
	.text
       .align    16,0x90
	.globl catanhl
catanhl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.17@GOTOFF(%ebx)
        movl      %edx, 28(%esp)
        fstpt     4(%esp)
        fldt      4(%esp)
        fstpt     16(%esp)
..B1.2:
        fnstcw    2(%esp)
..B1.3:
        movzwl    2(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.54
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, (%esp)
..B1.5:
        fldcw     (%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.14
..B1.8:
        testl     %ecx, %ecx
        jle       ..B1.34
..B1.9:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.15
..B1.10:
        testl     %edx, %edx
        jle       ..B1.32
..B1.11:
        fldt      12(%ebp)
        addl      $-32, %esp
        fstpt     (%esp)
        fldt      24(%ebp)
        fstpt     12(%esp)
        call      recatanhl
..B1.57:
        addl      $32, %esp
..B1.12:
        fldt      24(%ebp)
        fxch      %st(1)
        fstpt     4(%esp)
        addl      $-32, %esp
        fldt      12(%ebp)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      imcatanhl
..B1.58:
        addl      $32, %esp
..B1.13:
        fstpt     16(%esp)
        jmp       ..B1.28
..B1.14:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
..B1.15:
        cmpl      $32767, %ecx
        je        ..B1.46
..B1.16:
        cmpl      $32767, %edx
        je        ..B1.40
..B1.17:
        cmpl      $32767, %edx
        jge       ..B1.27
..B1.18:
        movl      16(%ebp), %edx
        testl     %edx, %edx
        jne       ..B1.20
..B1.19:
        cmpl      $0, 12(%ebp)
        je        ..B1.27
..B1.20:
        cmpl      $16383, %ecx
        jge       ..B1.22
..B1.21:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      atanhl@PLT
..B1.59:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        addl      $16, %esp
        movzbl    33(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fxch      %st(1)
        fstpt     4(%esp)
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fmulp     %st, %st(1)
        fstpt     16(%esp)
        jmp       ..B1.28
..B1.22:
        je        ..B1.37
..B1.23:
        cmpl      $32767, %ecx
        jl        ..B1.25
..B1.24:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        movzbl    21(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        movzbl    33(%ebp), %ecx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        andl      $128, %ecx
        fmulp     %st, %st(1)
        shrl      $7, %ecx
        fstpt     4(%esp)
        addl      $-16, %esp
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fstpt     (%esp)
        call      asinl@PLT
        jmp       ..B1.63
..B1.25:
        fldt      12(%ebp)
        addl      $-32, %esp
        fstpt     (%esp)
        fldt      24(%ebp)
        fstpt     12(%esp)
        call      recatanhl
..B1.61:
        addl      $32, %esp
..B1.26:
        movzbl    33(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        fstpt     4(%esp)
        addl      $-16, %esp
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fstpt     (%esp)
        call      asinl@PLT
        jmp       ..B1.63
..B1.27:
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fldt      24(%ebp)
        movzbl    21(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        lea       (,%ecx,8), %edx
        lea       (%edx,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     4(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      atanl@PLT
..B1.63:
        addl      $16, %esp
        fstpt     16(%esp)
..B1.28:
        testl     %edi, %edi
        je        ..B1.30
..B1.29:
        fldcw     2(%esp)
..B1.30:
        movl      4(%esp), %edx
        movl      %edx, (%esi)
        movl      16(%esp), %edx
        movl      %edx, 12(%esi)
        movl      20(%esp), %edx
        movl      %edx, 16(%esi)
        movl      8(%esp), %ecx
        movl      12(%esp), %edi
        movl      24(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      28(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.39
..B1.31:
        movl      %esi, %eax
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.32:
        cmpl      $0, 28(%ebp)
        jne       ..B1.11
..B1.33:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
        jmp       ..B1.15
..B1.34:
        cmpl      $0, 16(%ebp)
        jne       ..B1.9
..B1.35:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
        jmp       ..B1.14
..B1.37:
        cmpl      $-2147483648, %edx
        jne       ..B1.23
..B1.38:
        cmpl      $0, 12(%ebp)
        je        ..B1.21
        jmp       ..B1.23
..B1.39:
        call      __stack_chk_fail@PLT
..B1.40:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.42
..B1.41:
        cmpl      $0, 24(%ebp)
        je        ..B1.17
..B1.42:
        cmpl      $32767, %ecx
        jge       ..B1.17
..B1.43:
        cmpl      $0, 16(%ebp)
        jne       ..B1.45
..B1.44:
        cmpl      $0, 12(%ebp)
        je        ..B1.17
..B1.45:
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     4(%esp)
        fldt      4(%esp)
        fstpt     16(%esp)
        jmp       ..B1.28
..B1.46:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.48
..B1.47:
        cmpl      $0, 12(%ebp)
        je        ..B1.16
..B1.48:
        cmpl      $32767, %edx
        jge       ..B1.50
..B1.49:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     4(%esp)
        fldt      4(%esp)
        fstpt     16(%esp)
        jmp       ..B1.28
..B1.50:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.53
..B1.51:
        cmpl      $0, 24(%ebp)
        jne       ..B1.53
..B1.52:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     12(%ebp)
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        fstpt     4(%esp)
        addl      $-16, %esp
        fldt      24(%ebp)
        fstpt     (%esp)
        call      atanl@PLT
        jmp       ..B1.63
..B1.53:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     4(%esp)
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     16(%esp)
        jmp       ..B1.28
..B1.54:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	catanhl,@function
	.size	catanhl,.-catanhl
	.data
# -- End  catanhl
	.text
# -- Begin  recatanhl
	.text
       .align    16,0x90
recatanhl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $436, %esp
        fldt      8(%ebp)
        call      ..L4
..L4:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%edx), %edx
        movl      %edx, 76(%esp)
        movl      %gs:20, %eax
        fldt      48+_CONSTANTS@GOTOFF(%edx)
        xorl      %esp, %eax
        movl      %eax, 388(%esp)
        movl      $0, 160(%esp)
        fstpt     32(%esp)
        fldt      32(%esp)
        fxch      %st(1)
        fstpt     136(%esp)
        fstpt     148(%esp)
        addl      $4, %esp
        lea       132(%esp), %ecx
        pushl     %ecx
        call      __libm_normalizel_k80
..B2.2:
        fldt      20(%ebp)
        movl      $0, 188(%esp)
        fstpt     164(%esp)
        fldt      32(%esp)
        fstpt     176(%esp)
        addl      $4, %esp
        lea       160(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.3:
        fldt      32(%esp)
        xorl      %esi, %esi
        movl      76(%esp), %eax
        movzwl    144(%esp), %ecx
        movl      %ecx, %ebx
        andl      $32767, %ebx
        movl      %esi, 216(%esp)
        cmpl      $8383, %ebx
        fstpt     204(%esp)
        fldt      _CONSTANTS@GOTOFF(%eax)
        fstpt     192(%esp)
        jl        ..B2.7
..B2.4:
        cmpl      $24383, %ebx
        jg        ..B2.7
..B2.5:
        movzwl    200(%esp), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B2.7
..B2.6:
        cmpl      $24383, %edx
        jle       ..B2.10
..B2.7:
        addl      $4, %esp
        lea       132(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.8:
        addl      $4, %esp
        lea       188(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.9:
        movzwl    144(%esp), %ecx
        movl      %ecx, %ebx
        movzwl    200(%esp), %edx
        andl      $32767, %ebx
        movl      216(%esp), %esi
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B2.10:
        movl      160(%esp), %eax
        movl      %eax, 48(%esp)
        lea       (%ebx,%eax), %edi
        movl      %edi, 32(%esp)
        lea       -134(%esi,%edx), %eax
        cmpl      %eax, %edi
        jle       ..B2.27
..B2.11:
        movl      140(%esp), %eax
        lea       134(%esi,%edx), %edi
        cmpl      32(%esp), %edi
        jle       ..B2.28
..B2.12:
        testl     %eax, %eax
        jne       ..B2.14
..B2.13:
        cmpl      $0, 136(%esp)
        je        ..B2.29
..B2.14:
        cmpl      $0, 196(%esp)
        jne       ..B2.16
..B2.15:
        cmpl      $0, 192(%esp)
        je        ..B2.28
..B2.16:
        cmpl      48(%esp), %esi
        je        ..B2.18
..B2.17:
        fldt      136(%esp)
        fldt      148(%esp)
        movl      48(%esp), %eax
        subl      %esi, %eax
        andb      $127, 41(%esp)
        addl      $16383, %eax
        movzwl    40(%esp), %edx
        andl      $32767, %eax
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %esi, 160(%esp)
        fxch      %st(2)
        fstpt     136(%esp)
        fldt      136(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     148(%esp)
        fldt      148(%esp)
        movzwl    144(%esp), %ecx
        jmp       ..B2.19
..B2.18:
        fldt      136(%esp)
        fldt      148(%esp)
..B2.19:
        fldt      192(%esp)
        andl      $32767, %ecx
        movl      44(%esp), %eax
        fld       %st(2)
        andl      $32767, %eax
        fadd      %st(1), %st
        movl      %esi, 244(%esp)
        cmpl      %eax, %ecx
        jg        ..B2.24
..B2.20:
        jne       ..B2.25
..B2.21:
        movl      140(%esp), %eax
        movl      196(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.24
..B2.22:
        jne       ..B2.25
..B2.23:
        movl      136(%esp), %eax
        cmpl      192(%esp), %eax
        jbe       ..B2.25
..B2.24:
        fldt      204(%esp)
        fxch      %st(1)
        fsubr     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B2.26
..B2.25:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      204(%esp)
        faddp     %st, %st(2)
..B2.26:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     220(%esp)
        fldt      220(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     232(%esp)
        jmp       ..B2.33
..B2.27:
        movl      140(%esp), %eax
..B2.28:
        testl     %eax, %eax
        jne       ..B2.30
..B2.162:
        cmpl      $0, 136(%esp)
..B2.29:
        je        ..B2.31
..B2.30:
        lea       -16517(%esi,%edx), %ecx
        movl      48(%esp), %edx
        lea       -16383(%edx,%ebx), %ebx
        cmpl      %ebx, %ecx
        jl        ..B2.32
..B2.31:
        movl      192(%esp), %eax
        movl      %eax, 220(%esp)
        movl      196(%esp), %edx
        movl      200(%esp), %ecx
        movl      204(%esp), %ebx
        movl      208(%esp), %esi
        movl      212(%esp), %edi
        movl      216(%esp), %eax
        movl      %edx, 224(%esp)
        movl      %ecx, 228(%esp)
        movl      %ebx, 232(%esp)
        movl      %esi, 236(%esp)
        movl      %edi, 240(%esp)
        movl      %eax, 244(%esp)
        jmp       ..B2.33
..B2.32:
        movl      %eax, 224(%esp)
        movl      136(%esp), %edx
        movl      144(%esp), %eax
        movl      148(%esp), %ecx
        movl      152(%esp), %ebx
        movl      156(%esp), %esi
        movl      48(%esp), %edi
        movl      %edx, 220(%esp)
        movl      %eax, 228(%esp)
        movl      %ecx, 232(%esp)
        movl      %ebx, 236(%esp)
        movl      %esi, 240(%esp)
        movl      %edi, 244(%esp)
..B2.33:
        movzwl    228(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.35
..B2.34:
        cmpl      $24383, %eax
        jle       ..B2.37
..B2.35:
        addl      $4, %esp
        lea       216(%esp), %ebx
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.36:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.37:
        fldt      220(%esp)
        movl      76(%esp), %edx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fldt      232(%esp)
        fmulp     %st, %st(4)
        movl      244(%esp), %eax
        fxch      %st(3)
        fadd      %st(0), %st
        fldt      60+_CONSTANTS@GOTOFF(%edx)
        addl      %eax, %eax
        fmul      %st, %st(3)
        movl      %eax, 272(%esp)
        fxch      %st(2)
        fsub      %st(3), %st
        movzwl    172(%esp), %eax
        faddp     %st, %st(3)
        fld       %st(2)
        andl      $32767, %eax
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(4)
        fmul      %st(4), %st
        cmpl      $8383, %eax
        fadd      %st(0), %st
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(3)
        fmul      %st(0), %st
        fld       %st(1)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     248(%esp)
        fldt      248(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     260(%esp)
        jl        ..B2.39
..B2.38:
        cmpl      $24383, %eax
        jle       ..B2.41
..B2.39:
        addl      $4, %esp
        lea       160(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.40:
        addl      $4, %esp
        lea       160(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.41:
        fldt      164(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%esp)
        fldt      176(%esp)
        fmul      %st(4), %st
        movzwl    256(%esp), %ecx
        movl      %ecx, %ebx
        andl      $32767, %ebx
        fadd      %st(0), %st
        fxch      %st(4)
        fmulp     %st, %st(1)
        movl      188(%esp), %esi
        fsubr     %st, %st(2)
        addl      %esi, %esi
        faddp     %st, %st(2)
        fld       %st(1)
        cmpl      $8383, %ebx
        fmul      %st(2), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        movl      %esi, 300(%esp)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     276(%esp)
        fldt      276(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     288(%esp)
        fstpt     64(%esp)
        jl        ..B2.45
..B2.42:
        cmpl      $24383, %ebx
        jg        ..B2.45
..B2.43:
        movzwl    284(%esp), %edx
        movl      %edx, 44(%esp)
        andl      $32767, %edx
        cmpl      $8383, %edx
        jl        ..B2.45
..B2.44:
        cmpl      $24383, %edx
        jle       ..B2.48
..B2.45:
        addl      $4, %esp
        lea       244(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.46:
        addl      $4, %esp
        lea       272(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.47:
        movzwl    256(%esp), %ecx
        movl      %ecx, %ebx
        movzwl    284(%esp), %edx
        andl      $32767, %ebx
        movl      300(%esp), %esi
        movl      %edx, 44(%esp)
        andl      $32767, %edx
..B2.48:
        movl      272(%esp), %eax
        movl      %eax, 80(%esp)
        lea       (%ebx,%eax), %edi
        movl      %edi, 60(%esp)
        lea       -134(%esi,%edx), %eax
        cmpl      %eax, %edi
        jle       ..B2.65
..B2.49:
        movl      252(%esp), %eax
        lea       134(%esi,%edx), %edi
        cmpl      60(%esp), %edi
        jle       ..B2.66
..B2.50:
        testl     %eax, %eax
        jne       ..B2.52
..B2.51:
        cmpl      $0, 248(%esp)
        je        ..B2.67
..B2.52:
        cmpl      $0, 280(%esp)
        jne       ..B2.54
..B2.53:
        cmpl      $0, 276(%esp)
        je        ..B2.66
..B2.54:
        cmpl      80(%esp), %esi
        je        ..B2.56
..B2.55:
        fldt      64(%esp)
        movl      80(%esp), %eax
        fstpt     32(%esp)
        subl      %esi, %eax
        fldt      248(%esp)
        addl      $16383, %eax
        fldt      260(%esp)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        movzwl    40(%esp), %edx
        andl      $-32768, %edx
        orl       %eax, %edx
        movw      %dx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %esi, 272(%esp)
        fxch      %st(2)
        fstpt     248(%esp)
        fldt      248(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     260(%esp)
        fldt      260(%esp)
        fxch      %st(1)
        fstpt     64(%esp)
        movzwl    256(%esp), %ecx
        jmp       ..B2.57
..B2.56:
        fldt      248(%esp)
        fstpt     64(%esp)
        fldt      260(%esp)
..B2.57:
        fldt      276(%esp)
        andl      $32767, %ecx
        fldt      64(%esp)
        movl      44(%esp), %eax
        andl      $32767, %eax
        fadd      %st(1), %st
        movl      %esi, 328(%esp)
        cmpl      %eax, %ecx
        jg        ..B2.62
..B2.58:
        jne       ..B2.63
..B2.59:
        movl      252(%esp), %eax
        movl      280(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.62
..B2.60:
        jne       ..B2.63
..B2.61:
        movl      248(%esp), %eax
        cmpl      276(%esp), %eax
        jbe       ..B2.63
..B2.62:
        fldt      64(%esp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fldt      288(%esp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B2.64
..B2.63:
        fldt      64(%esp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      288(%esp)
        faddp     %st, %st(1)
        fstpt     64(%esp)
..B2.64:
        fldt      64(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     304(%esp)
        fldt      304(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     316(%esp)
        jmp       ..B2.71
..B2.65:
        movl      252(%esp), %eax
..B2.66:
        testl     %eax, %eax
        jne       ..B2.68
..B2.164:
        cmpl      $0, 248(%esp)
..B2.67:
        je        ..B2.69
..B2.68:
        lea       -16517(%esi,%edx), %ecx
        movl      80(%esp), %edx
        lea       -16383(%edx,%ebx), %ebx
        cmpl      %ebx, %ecx
        jl        ..B2.70
..B2.69:
        movl      276(%esp), %eax
        movl      %eax, 304(%esp)
        movl      280(%esp), %edx
        movl      284(%esp), %ecx
        movl      288(%esp), %ebx
        movl      292(%esp), %esi
        movl      296(%esp), %edi
        movl      300(%esp), %eax
        movl      %edx, 308(%esp)
        movl      %ecx, 312(%esp)
        movl      %ebx, 316(%esp)
        movl      %esi, 320(%esp)
        movl      %edi, 324(%esp)
        movl      %eax, 328(%esp)
        jmp       ..B2.71
..B2.70:
        movl      %eax, 308(%esp)
        movl      248(%esp), %edx
        movl      256(%esp), %eax
        movl      260(%esp), %ecx
        movl      264(%esp), %ebx
        movl      268(%esp), %esi
        movl      80(%esp), %edi
        movl      %edx, 304(%esp)
        movl      %eax, 312(%esp)
        movl      %ecx, 316(%esp)
        movl      %ebx, 320(%esp)
        movl      %esi, 324(%esp)
        movl      %edi, 328(%esp)
..B2.71:
        movzwl    144(%esp), %ebx
        movl      %ebx, %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B2.75
..B2.72:
        cmpl      $24383, %esi
        jg        ..B2.75
..B2.73:
        movzwl    200(%esp), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.75
..B2.74:
        cmpl      $24383, %ecx
        jle       ..B2.78
..B2.75:
        addl      $4, %esp
        lea       132(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.76:
        addl      $4, %esp
        lea       188(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.77:
        movzwl    144(%esp), %ebx
        movl      %ebx, %esi
        movzwl    200(%esp), %ecx
        andl      $32767, %esi
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
..B2.78:
        movl      160(%esp), %eax
        movl      216(%esp), %edx
        movl      %eax, 80(%esp)
        lea       (%esi,%eax), %edi
        movl      %edi, 60(%esp)
        lea       -134(%edx,%ecx), %eax
        cmpl      %eax, %edi
        jle       ..B2.95
..B2.79:
        movl      140(%esp), %eax
        lea       134(%edx,%ecx), %edi
        cmpl      60(%esp), %edi
        jle       ..B2.96
..B2.80:
        testl     %eax, %eax
        jne       ..B2.82
..B2.81:
        cmpl      $0, 136(%esp)
        je        ..B2.97
..B2.82:
        cmpl      $0, 196(%esp)
        jne       ..B2.84
..B2.83:
        cmpl      $0, 192(%esp)
        je        ..B2.96
..B2.84:
        cmpl      80(%esp), %edx
        je        ..B2.86
..B2.85:
        fldt      64(%esp)
        movl      80(%esp), %eax
        fstpt     32(%esp)
        subl      %edx, %eax
        fldt      136(%esp)
        addl      $16383, %eax
        fldt      148(%esp)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 160(%esp)
        fxch      %st(2)
        fstpt     136(%esp)
        fldt      136(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     148(%esp)
        fldt      148(%esp)
        movzwl    144(%esp), %ebx
        jmp       ..B2.87
..B2.86:
        fldt      136(%esp)
        fldt      148(%esp)
..B2.87:
        fldt      192(%esp)
        andl      $32767, %ebx
        movl      44(%esp), %eax
        fld       %st(2)
        andl      $32767, %eax
        fsub      %st(1), %st
        movl      %edx, 244(%esp)
        cmpl      %eax, %ebx
        jg        ..B2.92
..B2.88:
        jne       ..B2.93
..B2.89:
        movl      140(%esp), %eax
        movl      196(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.92
..B2.90:
        jne       ..B2.93
..B2.91:
        movl      136(%esp), %eax
        cmpl      192(%esp), %eax
        jbe       ..B2.93
..B2.92:
        fsubr     %st, %st(3)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fldt      204(%esp)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        jmp       ..B2.94
..B2.93:
        fadd      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      204(%esp)
        fsubrp    %st, %st(2)
..B2.94:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     220(%esp)
        fldt      220(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     232(%esp)
        jmp       ..B2.101
..B2.95:
        movl      140(%esp), %eax
..B2.96:
        testl     %eax, %eax
        jne       ..B2.98
..B2.161:
        cmpl      $0, 136(%esp)
..B2.97:
        je        ..B2.99
..B2.98:
        lea       -16517(%edx,%ecx), %ebx
        movl      80(%esp), %ecx
        lea       -16383(%ecx,%esi), %esi
        cmpl      %esi, %ebx
        jl        ..B2.100
..B2.99:
        fldt      192(%esp)
        fchs      
        movl      %edx, 244(%esp)
        fstpt     220(%esp)
        fldt      204(%esp)
        fchs      
        fstpt     232(%esp)
        jmp       ..B2.101
..B2.100:
        movl      %eax, 224(%esp)
        movl      136(%esp), %edx
        movl      144(%esp), %eax
        movl      148(%esp), %ecx
        movl      152(%esp), %ebx
        movl      156(%esp), %esi
        movl      80(%esp), %edi
        movl      %edx, 220(%esp)
        movl      %eax, 228(%esp)
        movl      %ecx, 232(%esp)
        movl      %ebx, 236(%esp)
        movl      %esi, 240(%esp)
        movl      %edi, 244(%esp)
..B2.101:
        movzwl    228(%esp), %eax
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.103
..B2.102:
        cmpl      $24383, %eax
        jle       ..B2.105
..B2.103:
        addl      $4, %esp
        lea       216(%esp), %ebx
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.104:
        addl      $4, %esp
        pushl     %ebx
        call      __libm_normalizel_k80
..B2.105:
        fldt      220(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%esp)
        fldt      232(%esp)
        fxch      %st(1)
        fmul      %st(4), %st
        movl      244(%esp), %ebx
        fsubr     %st, %st(3)
        addl      %ebx, %ebx
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fsub      %st(3), %st
        fmul      %st, %st(3)
        movl      %ebx, 272(%esp)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fxch      %st(3)
        fchs      
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     248(%esp)
        fldt      248(%esp)
        fsubrp    %st, %st(2)
        movzwl    256(%esp), %ecx
        fadd      %st, %st(1)
        movl      %ecx, 84(%esp)
        andl      $32767, %ecx
        fxch      %st(1)
        fstpt     260(%esp)
        cmpl      $8383, %ecx
        fstpt     64(%esp)
        jl        ..B2.109
..B2.106:
        cmpl      $24383, %ecx
        jg        ..B2.109
..B2.107:
        movzwl    284(%esp), %eax
        movl      %eax, 60(%esp)
        andl      $32767, %eax
        cmpl      $8383, %eax
        jl        ..B2.109
..B2.108:
        cmpl      $24383, %eax
        jle       ..B2.112
..B2.109:
        addl      $4, %esp
        lea       244(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.110:
        addl      $4, %esp
        lea       272(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.111:
        movzwl    256(%esp), %ecx
        movzwl    284(%esp), %eax
        movl      272(%esp), %ebx
        movl      %ecx, 84(%esp)
        andl      $32767, %ecx
        movl      %eax, 60(%esp)
        andl      $32767, %eax
..B2.112:
        movl      300(%esp), %edx
        lea       (%ebx,%ecx), %esi
        movl      %esi, 80(%esp)
        lea       -134(%edx,%eax), %edi
        cmpl      %edi, %esi
        jle       ..B2.129
..B2.113:
        movl      252(%esp), %esi
        lea       134(%edx,%eax), %edi
        movl      %esi, 44(%esp)
        cmpl      80(%esp), %edi
        jle       ..B2.130
..B2.114:
        cmpl      $0, 44(%esp)
        jne       ..B2.116
..B2.115:
        cmpl      $0, 248(%esp)
        je        ..B2.131
..B2.116:
        cmpl      $0, 280(%esp)
        jne       ..B2.118
..B2.117:
        cmpl      $0, 276(%esp)
        je        ..B2.130
..B2.118:
        cmpl      %edx, %ebx
        je        ..B2.120
..B2.119:
        fldt      64(%esp)
        subl      %edx, %ebx
        fstpt     32(%esp)
        addl      $16383, %ebx
        fldt      248(%esp)
        andl      $32767, %ebx
        fldt      260(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %ebx, %eax
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 272(%esp)
        fxch      %st(2)
        fstpt     248(%esp)
        fldt      248(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     260(%esp)
        fldt      260(%esp)
        movzwl    256(%esp), %ecx
        movl      %ecx, 84(%esp)
        jmp       ..B2.121
..B2.120:
        fldt      248(%esp)
        fldt      260(%esp)
..B2.121:
        fldt      276(%esp)
        fld       %st(2)
        movl      84(%esp), %eax
        fadd      %st(1), %st
        movl      60(%esp), %ecx
        andl      $32767, %eax
        andl      $32767, %ecx
        movl      %edx, 356(%esp)
        cmpl      %ecx, %eax
        jg        ..B2.126
..B2.122:
        jne       ..B2.127
..B2.123:
        movl      252(%esp), %eax
        movl      280(%esp), %ecx
        cmpl      %ecx, %eax
        ja        ..B2.126
..B2.124:
        jne       ..B2.127
..B2.125:
        movl      248(%esp), %eax
        cmpl      276(%esp), %eax
        jbe       ..B2.127
..B2.126:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      288(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.128
..B2.127:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      288(%esp)
        faddp     %st, %st(1)
..B2.128:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     332(%esp)
        fldt      332(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     344(%esp)
        jmp       ..B2.135
..B2.129:
        movl      252(%esp), %esi
        movl      %esi, 44(%esp)
..B2.130:
        cmpl      $0, 44(%esp)
        jne       ..B2.132
..B2.163:
        cmpl      $0, 248(%esp)
..B2.131:
        je        ..B2.133
..B2.132:
        lea       -16517(%edx,%eax), %eax
        lea       -16383(%ebx,%ecx), %ecx
        cmpl      %ecx, %eax
        jl        ..B2.134
..B2.133:
        movl      276(%esp), %eax
        movl      %eax, 332(%esp)
        movl      280(%esp), %ecx
        movl      284(%esp), %ebx
        movl      288(%esp), %esi
        movl      292(%esp), %edi
        movl      296(%esp), %eax
        movl      %ecx, 336(%esp)
        movl      %ebx, 340(%esp)
        movl      %esi, 344(%esp)
        movl      %edi, 348(%esp)
        movl      %eax, 352(%esp)
        movl      %edx, 356(%esp)
        jmp       ..B2.135
..B2.134:
        movl      %esi, %edx
        movl      %edx, 336(%esp)
        movl      248(%esp), %eax
        movl      256(%esp), %ecx
        movl      260(%esp), %ebx
        movl      264(%esp), %esi
        movl      268(%esp), %edi
        movl      272(%esp), %edx
        movl      %eax, 332(%esp)
        movl      %ecx, 340(%esp)
        movl      %ebx, 344(%esp)
        movl      %esi, 348(%esp)
        movl      %edi, 352(%esp)
        movl      %edx, 356(%esp)
..B2.135:
        movzwl    312(%esp), %esi
        movzwl    340(%esp), %eax
        andl      $32767, %esi
        andl      $32767, %eax
        movl      328(%esp), %edi
        lea       (%edi,%esi), %ebx
        lea       (%edx,%eax), %ecx
        subl      %ecx, %ebx
        cmpl      $-1, %ebx
        jge       ..B2.145
..B2.136:
        cmpl      $8383, %esi
        jl        ..B2.140
..B2.137:
        cmpl      $24383, %esi
        jg        ..B2.140
..B2.138:
        cmpl      $8383, %eax
        jl        ..B2.140
..B2.139:
        cmpl      $24383, %eax
        jle       ..B2.143
..B2.140:
        addl      $4, %esp
        lea       300(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.141:
        addl      $4, %esp
        lea       328(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.142:
        movl      328(%esp), %edi
        movl      356(%esp), %edx
..B2.143:
        fldt      332(%esp)
        subl      %edx, %edi
        fld       %st(0)
        fld       %st(1)
        fldt      304(%esp)
        fld       %st(0)
        fdiv      %st(4), %st
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fld       %st(0)
        movl      %edi, 104(%esp)
        fsubr     %st(3), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(3), %st
        fxch      %st(2)
        fmul      %st(7), %st
        fsubr     %st, %st(6)
        faddp     %st, %st(6)
        fxch      %st(4)
        fsub      %st(5), %st
        fld       %st(4)
        fmul      %st(6), %st
        fxch      %st(2)
        fmul      %st, %st(6)
        fxch      %st(1)
        fmul      %st, %st(5)
        fxch      %st(6)
        faddp     %st, %st(5)
        fld       %st(4)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fsubp     %st, %st(1)
        fldt      316(%esp)
        faddp     %st, %st(1)
        fldt      344(%esp)
        fmul      %st(2), %st
        fsubrp    %st, %st(1)
        fdivp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     80(%esp)
        fldt      80(%esp)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        faddp     %st, %st(1)
        fstpt     92(%esp)
        addl      $8, %esp
        lea       72(%esp), %eax
        lea       352(%esp), %edx
        pushl     %eax
        pushl     %edx
        call      __libm_log_k80
        jmp       ..B2.153
..B2.145:
        movzwl    144(%esp), %ecx
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B2.149
..B2.146:
        cmpl      $24383, %ecx
        jg        ..B2.149
..B2.147:
        cmpl      $8383, %eax
        jl        ..B2.149
..B2.148:
        cmpl      $24383, %eax
        jle       ..B2.152
..B2.149:
        addl      $4, %esp
        lea       132(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.150:
        addl      $4, %esp
        lea       328(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B2.151:
        movl      356(%esp), %edx
..B2.152:
        fldt      136(%esp)
        fld       %st(0)
        fldt      332(%esp)
        fdivr     %st, %st(1)
        fld       %st(0)
        fld       %st(1)
        fldt      48(%esp)
        fld       %st(0)
        fmul      %st(5), %st
        fld       %st(0)
        movl      160(%esp), %eax
        fsubr     %st(6), %st
        subl      %edx, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        addl      $2, %eax
        movl      %eax, 132(%esp)
        fsubr     %st(6), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fxch      %st(2)
        fsub      %st(3), %st
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(2)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fldt      148(%esp)
        faddp     %st, %st(3)
        fldt      344(%esp)
        fmul      %st(2), %st
        fsubrp    %st, %st(3)
        fdivrp    %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     108(%esp)
        fldt      108(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     120(%esp)
        fstpt     32(%esp)
        addl      $8, %esp
        lea       100(%esp), %edx
        lea       352(%esp), %ecx
        pushl     %edx
        pushl     %ecx
        call      __libm_log1p_k80
..B2.153:
        movl      384(%esp), %eax
        addl      $-2, %eax
        movl      %eax, 384(%esp)
..B2.154:
        fldt      360(%esp)
        fstpt     (%esp)
        fldt      372(%esp)
        fstpt     12(%esp)
        movl      %eax, 24(%esp)
        call      __libm_scalbl_k80
..B2.155:
        movl      388(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.157
..B2.156:
        addl      $436, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.157:
        fstp      %st(0)
        movl      76(%esp), %ebx
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	recatanhl,@function
	.size	recatanhl,.-recatanhl
	.data
# -- End  recatanhl
	.text
# -- Begin  imcatanhl
	.text
       .align    16,0x90
imcatanhl:
# parameter 1: 8 + %ebp
# parameter 2: 20 + %ebp
..B3.1:
..L5:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $436, %esp
        fldt      8(%ebp)
        movzbl    17(%ebp), %esi
        andl      $128, %esi
        call      ..L6
..L6:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L6](%eax), %eax
        shrl      $7, %esi
        movl      %eax, 88(%esp)
        fldt      48+_CONSTANTS@GOTOFF(%eax)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        lea       (,%esi,8), %ebx
        movl      %edx, 428(%esp)
        lea       (%ebx,%esi,4), %edi
        fldt      _CONSTANTS@GOTOFF(%eax,%edi)
        movzbl    29(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fmulp     %st, %st(2)
        movl      $0, 256(%esp)
        lea       (,%edx,8), %ecx
        fxch      %st(1)
        fstpt     232(%esp)
        lea       (%ecx,%edx,4), %ecx
        fstpt     32(%esp)
        fldt      32(%esp)
        fstpt     244(%esp)
        fldt      24+_CONSTANTS@GOTOFF(%eax,%ecx)
        fstpt     48(%esp)
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.2:
        fldt      20(%ebp)
        fldt      32(%esp)
        movzbl    29(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        movl      88(%esp), %ecx
        movl      $0, 284(%esp)
        fstpt     272(%esp)
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ebx
        fldt      _CONSTANTS@GOTOFF(%ecx,%ebx)
        fmulp     %st, %st(1)
        fstpt     260(%esp)
        addl      $4, %esp
        lea       256(%esp), %esi
        pushl     %esi
        call      __libm_normalizel_k80
..B3.3:
        fldt      32(%esp)
        movl      88(%esp), %eax
        movl      $0, 312(%esp)
        fstpt     300(%esp)
        fldt      _CONSTANTS@GOTOFF(%eax)
        movzwl    240(%esp), %eax
        andl      $32767, %eax
        fstpt     288(%esp)
        cmpl      $8383, %eax
        jl        ..B3.5
..B3.4:
        cmpl      $24383, %eax
        jle       ..B3.7
..B3.5:
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.6:
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.7:
        fldt      232(%esp)
        movl      88(%esp), %edx
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fld       %st(3)
        fldt      60+_CONSTANTS@GOTOFF(%edx)
        fmul      %st, %st(4)
        movl      256(%esp), %eax
        fxch      %st(3)
        fsubr     %st(4), %st
        fld       %st(0)
        addl      %eax, %eax
        fchs      
        movl      %eax, 340(%esp)
        fadd      %st(5), %st
        fld       %st(0)
        fxch      %st(2)
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fmul      %st(5), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsub      %st(5), %st
        fmul      %st, %st(2)
        fxch      %st(3)
        fmul      %st, %st(5)
        movzwl    268(%esp), %eax
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fxch      %st(2)
        fstpt     32(%esp)
        fld       %st(1)
        andl      $32767, %eax
        fadd      %st(3), %st
        cmpl      $8383, %eax
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      244(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     316(%esp)
        fldt      316(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     328(%esp)
        jl        ..B3.9
..B3.8:
        cmpl      $24383, %eax
        jle       ..B3.11
..B3.9:
        addl      $4, %esp
        lea       256(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.10:
        addl      $4, %esp
        lea       256(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.11:
        fldt      260(%esp)
        fld       %st(0)
        fld       %st(1)
        fldt      32(%esp)
        fmul      %st(3), %st
        movzwl    296(%esp), %ebx
        movl      %ebx, %esi
        andl      $32767, %esi
        fsubr     %st, %st(2)
        movl      284(%esp), %eax
        faddp     %st, %st(2)
        fld       %st(1)
        addl      %eax, %eax
        fmul      %st(2), %st
        fxch      %st(1)
        fsub      %st(2), %st
        fmul      %st, %st(2)
        cmpl      $8383, %esi
        movl      %eax, 368(%esp)
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
        fldt      272(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     344(%esp)
        fldt      344(%esp)
        fsubrp    %st, %st(1)
        fadd      %st(1), %st
        fstpt     356(%esp)
        fstpt     64(%esp)
        jl        ..B3.15
..B3.12:
        cmpl      $24383, %esi
        jg        ..B3.15
..B3.13:
        movzwl    324(%esp), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B3.15
..B3.14:
        cmpl      $24383, %ecx
        jle       ..B3.18
..B3.15:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.16:
        addl      $4, %esp
        lea       312(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.17:
        movzwl    296(%esp), %ebx
        movl      %ebx, %esi
        movzwl    324(%esp), %ecx
        andl      $32767, %esi
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
..B3.18:
        movl      312(%esp), %eax
        movl      340(%esp), %edx
        movl      %eax, 60(%esp)
        lea       (%esi,%eax), %edi
        movl      %edi, 32(%esp)
        lea       -134(%edx,%ecx), %eax
        cmpl      %eax, %edi
        jle       ..B3.35
..B3.19:
        movl      292(%esp), %eax
        lea       134(%edx,%ecx), %edi
        cmpl      32(%esp), %edi
        jle       ..B3.36
..B3.20:
        testl     %eax, %eax
        jne       ..B3.22
..B3.21:
        cmpl      $0, 288(%esp)
        je        ..B3.37
..B3.22:
        cmpl      $0, 320(%esp)
        jne       ..B3.24
..B3.23:
        cmpl      $0, 316(%esp)
        je        ..B3.36
..B3.24:
        cmpl      60(%esp), %edx
        je        ..B3.26
..B3.25:
        fldt      64(%esp)
        movl      60(%esp), %eax
        fstpt     32(%esp)
        subl      %edx, %eax
        fldt      288(%esp)
        addl      $16383, %eax
        fldt      300(%esp)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 312(%esp)
        fxch      %st(2)
        fstpt     288(%esp)
        fldt      288(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     300(%esp)
        fldt      300(%esp)
        fxch      %st(1)
        fstpt     64(%esp)
        movzwl    296(%esp), %ebx
        jmp       ..B3.27
..B3.26:
        fldt      288(%esp)
        fstpt     64(%esp)
        fldt      300(%esp)
..B3.27:
        fldt      316(%esp)
        andl      $32767, %ebx
        fldt      64(%esp)
        movl      44(%esp), %eax
        andl      $32767, %eax
        fsub      %st(1), %st
        movl      %edx, 396(%esp)
        cmpl      %eax, %ebx
        jg        ..B3.32
..B3.28:
        jne       ..B3.33
..B3.29:
        movl      292(%esp), %eax
        movl      320(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B3.32
..B3.30:
        jne       ..B3.33
..B3.31:
        movl      288(%esp), %eax
        cmpl      316(%esp), %eax
        jbe       ..B3.33
..B3.32:
        fldt      64(%esp)
        fsub      %st(1), %st
        fsubp     %st, %st(2)
        fldt      328(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B3.34
..B3.33:
        fldt      64(%esp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      328(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
..B3.34:
        fldt      64(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     372(%esp)
        fldt      372(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     384(%esp)
        jmp       ..B3.41
..B3.35:
        movl      292(%esp), %eax
..B3.36:
        testl     %eax, %eax
        jne       ..B3.38
..B3.179:
        cmpl      $0, 288(%esp)
..B3.37:
        je        ..B3.39
..B3.38:
        lea       -16517(%edx,%ecx), %ebx
        movl      60(%esp), %ecx
        lea       -16383(%ecx,%esi), %esi
        cmpl      %esi, %ebx
        jl        ..B3.40
..B3.39:
        fldt      316(%esp)
        fchs      
        movl      %edx, 396(%esp)
        fstpt     372(%esp)
        fldt      328(%esp)
        fchs      
        fstpt     384(%esp)
        jmp       ..B3.41
..B3.40:
        movl      %eax, 376(%esp)
        movl      288(%esp), %edx
        movl      296(%esp), %eax
        movl      300(%esp), %ecx
        movl      304(%esp), %ebx
        movl      308(%esp), %esi
        movl      60(%esp), %edi
        movl      %edx, 372(%esp)
        movl      %eax, 380(%esp)
        movl      %ecx, 384(%esp)
        movl      %ebx, 388(%esp)
        movl      %esi, 392(%esp)
        movl      %edi, 396(%esp)
..B3.41:
        movzwl    380(%esp), %ebx
        movl      %ebx, %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B3.45
..B3.42:
        cmpl      $24383, %esi
        jg        ..B3.45
..B3.43:
        movzwl    352(%esp), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B3.45
..B3.44:
        cmpl      $24383, %ecx
        jle       ..B3.48
..B3.45:
        addl      $4, %esp
        lea       368(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.46:
        addl      $4, %esp
        lea       340(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.47:
        movzwl    380(%esp), %ebx
        movl      %ebx, %esi
        movzwl    352(%esp), %ecx
        andl      $32767, %esi
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
..B3.48:
        movl      368(%esp), %edx
        movl      396(%esp), %eax
        movl      %eax, 76(%esp)
        lea       (%esi,%eax), %edi
        movl      %edi, 60(%esp)
        lea       -134(%edx,%ecx), %eax
        cmpl      %eax, %edi
        jle       ..B3.65
..B3.49:
        movl      376(%esp), %eax
        lea       134(%edx,%ecx), %edi
        cmpl      60(%esp), %edi
        jle       ..B3.66
..B3.50:
        testl     %eax, %eax
        jne       ..B3.52
..B3.51:
        cmpl      $0, 372(%esp)
        je        ..B3.67
..B3.52:
        cmpl      $0, 348(%esp)
        jne       ..B3.54
..B3.53:
        cmpl      $0, 344(%esp)
        je        ..B3.66
..B3.54:
        cmpl      76(%esp), %edx
        je        ..B3.56
..B3.55:
        fldt      64(%esp)
        movl      76(%esp), %eax
        fstpt     32(%esp)
        subl      %edx, %eax
        fldt      372(%esp)
        addl      $16383, %eax
        fldt      384(%esp)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 396(%esp)
        fxch      %st(2)
        fstpt     372(%esp)
        fldt      372(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     384(%esp)
        fldt      384(%esp)
        fstpt     64(%esp)
        movzwl    380(%esp), %ebx
        jmp       ..B3.57
..B3.56:
        fldt      372(%esp)
        fldt      384(%esp)
        fstpt     64(%esp)
..B3.57:
        fldt      344(%esp)
        andl      $32767, %ebx
        movl      44(%esp), %eax
        fld       %st(1)
        andl      $32767, %eax
        fsub      %st(1), %st
        movl      %edx, 424(%esp)
        cmpl      %eax, %ebx
        jg        ..B3.62
..B3.58:
        jne       ..B3.63
..B3.59:
        movl      376(%esp), %eax
        movl      348(%esp), %ecx
        cmpl      %ecx, %eax
        ja        ..B3.62
..B3.60:
        jne       ..B3.63
..B3.61:
        movl      372(%esp), %eax
        cmpl      344(%esp), %eax
        jbe       ..B3.63
..B3.62:
        fldt      64(%esp)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      356(%esp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
        jmp       ..B3.64
..B3.63:
        fldt      356(%esp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      64(%esp)
        faddp     %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
..B3.64:
        fldt      64(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     400(%esp)
        fldt      400(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     412(%esp)
        jmp       ..B3.71
..B3.65:
        movl      376(%esp), %eax
..B3.66:
        testl     %eax, %eax
        jne       ..B3.68
..B3.180:
        cmpl      $0, 372(%esp)
..B3.67:
        je        ..B3.69
..B3.68:
        lea       -16517(%edx,%ecx), %ebx
        movl      76(%esp), %ecx
        lea       -16383(%ecx,%esi), %esi
        cmpl      %esi, %ebx
        jl        ..B3.70
..B3.69:
        fldt      344(%esp)
        fchs      
        movl      %edx, 424(%esp)
        fstpt     400(%esp)
        fldt      356(%esp)
        fchs      
        fstpt     412(%esp)
        jmp       ..B3.71
..B3.70:
        movl      372(%esp), %edx
        movl      %edx, 400(%esp)
        movl      %eax, 404(%esp)
        movl      380(%esp), %eax
        movl      384(%esp), %ecx
        movl      388(%esp), %ebx
        movl      392(%esp), %esi
        movl      76(%esp), %edx
        movl      %eax, 408(%esp)
        movl      %ecx, 412(%esp)
        movl      %ebx, 416(%esp)
        movl      %esi, 420(%esp)
        movl      %edx, 424(%esp)
..B3.71:
        movzwl    408(%esp), %eax
        andl      $32767, %eax
        lea       -16383(%edx,%eax), %edx
        cmpl      $-6, %edx
        jge       ..B3.164
..B3.72:
        movzwl    296(%esp), %esi
        movl      %esi, 84(%esp)
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B3.76
..B3.73:
        cmpl      $24383, %esi
        jg        ..B3.76
..B3.74:
        movzwl    240(%esp), %eax
        movl      %eax, 60(%esp)
        andl      $32767, %eax
        movl      %eax, 76(%esp)
        cmpl      $8383, %eax
        jl        ..B3.76
..B3.75:
        cmpl      $24383, 76(%esp)
        jle       ..B3.79
..B3.76:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.77:
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.78:
        movzwl    240(%esp), %eax
        movzwl    296(%esp), %esi
        movl      %eax, 60(%esp)
        andl      $32767, %eax
        movl      %eax, 76(%esp)
        movl      %esi, 84(%esp)
        andl      $32767, %esi
..B3.79:
        movl      256(%esp), %ecx
        movl      312(%esp), %edx
        lea       (%eax,%ecx), %ebx
        lea       -134(%ecx,%eax), %edi
        movl      %edi, 80(%esp)
        lea       (%esi,%edx), %eax
        cmpl      %eax, %edi
        jge       ..B3.96
..B3.80:
        movl      292(%esp), %edi
        movl      %edi, 44(%esp)
        movl      76(%esp), %edi
        lea       134(%ecx,%edi), %edi
        cmpl      %edi, %eax
        jge       ..B3.97
..B3.81:
        cmpl      $0, 44(%esp)
        jne       ..B3.83
..B3.82:
        cmpl      $0, 288(%esp)
        je        ..B3.98
..B3.83:
        cmpl      $0, 236(%esp)
        jne       ..B3.85
..B3.84:
        cmpl      $0, 232(%esp)
        je        ..B3.97
..B3.85:
        cmpl      %ecx, %edx
        je        ..B3.87
..B3.86:
        fldt      64(%esp)
        subl      %ecx, %edx
        fstpt     32(%esp)
        addl      $16383, %edx
        fldt      288(%esp)
        andl      $32767, %edx
        fldt      300(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %edx, %eax
        movl      %ecx, %edx
        movw      %ax, 40(%esp)
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
        movl      %esi, 84(%esp)
        andl      $32767, %esi
        movl      %ecx, 312(%esp)
        movl      %esi, %eax
        fstpt     300(%esp)
        fldt      300(%esp)
        jmp       ..B3.88
..B3.87:
        fldt      288(%esp)
        fldt      300(%esp)
        movl      84(%esp), %eax
        andl      $32767, %eax
..B3.88:
        fldt      232(%esp)
        fld       %st(2)
        movl      60(%esp), %edi
        fadd      %st(1), %st
        andl      $32767, %edi
        movl      %ecx, 116(%esp)
        cmpl      %edi, %eax
        jg        ..B3.93
..B3.89:
        jne       ..B3.94
..B3.90:
        movl      292(%esp), %eax
        movl      236(%esp), %edi
        cmpl      %edi, %eax
        ja        ..B3.93
..B3.91:
        jne       ..B3.94
..B3.92:
        movl      288(%esp), %eax
        cmpl      232(%esp), %eax
        jbe       ..B3.94
..B3.93:
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      244(%esp)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B3.95
..B3.94:
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      244(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
..B3.95:
        fldt      64(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     92(%esp)
        fldt      92(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     104(%esp)
        jmp       ..B3.102
..B3.96:
        movl      292(%esp), %eax
        movl      %eax, 44(%esp)
..B3.97:
        cmpl      $0, 44(%esp)
        jne       ..B3.99
..B3.178:
        cmpl      $0, 288(%esp)
..B3.98:
        je        ..B3.100
..B3.99:
        movl      76(%esp), %eax
        lea       -16517(%ecx,%eax), %edi
        lea       -16383(%edx,%esi), %eax
        cmpl      %eax, %edi
        jl        ..B3.101
..B3.100:
        movl      232(%esp), %eax
        movl      236(%esp), %edi
        movl      %eax, 92(%esp)
        movl      %edi, 96(%esp)
        movl      240(%esp), %eax
        movl      244(%esp), %edi
        movl      %eax, 100(%esp)
        movl      %edi, 104(%esp)
        movl      248(%esp), %eax
        movl      252(%esp), %edi
        movl      %eax, 108(%esp)
        movl      %edi, 112(%esp)
        movl      %ecx, 116(%esp)
        jmp       ..B3.102
..B3.101:
        movl      288(%esp), %eax
        movl      44(%esp), %edi
        movl      %eax, 92(%esp)
        movl      %edi, 96(%esp)
        movl      296(%esp), %eax
        movl      300(%esp), %edi
        movl      %eax, 100(%esp)
        movl      %edi, 104(%esp)
        movl      304(%esp), %eax
        movl      308(%esp), %edi
        movl      %eax, 108(%esp)
        movl      %edi, 112(%esp)
        movl      %edx, 116(%esp)
..B3.102:
        cmpl      $8383, %esi
        jl        ..B3.106
..B3.103:
        cmpl      $24383, %esi
        jg        ..B3.106
..B3.104:
        cmpl      $8383, 76(%esp)
        jl        ..B3.106
..B3.105:
        cmpl      $24383, 76(%esp)
        jle       ..B3.109
..B3.106:
        addl      $4, %esp
        lea       284(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.107:
        addl      $4, %esp
        lea       228(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.108:
        movzwl    240(%esp), %eax
        movl      %eax, 60(%esp)
        andl      $32767, %eax
        movl      256(%esp), %ecx
        movl      312(%esp), %edx
        lea       -134(%ecx,%eax), %esi
        movl      %esi, 80(%esp)
        movzwl    296(%esp), %esi
        lea       (%ecx,%eax), %ebx
        movl      %esi, 84(%esp)
        andl      $32767, %esi
..B3.109:
        lea       (%edx,%esi), %eax
        movl      %eax, 44(%esp)
        cmpl      80(%esp), %eax
        jle       ..B3.126
..B3.110:
        movl      292(%esp), %eax
        lea       134(%ebx), %edi
        cmpl      44(%esp), %edi
        jle       ..B3.127
..B3.111:
        testl     %eax, %eax
        jne       ..B3.113
..B3.112:
        cmpl      $0, 288(%esp)
        je        ..B3.128
..B3.113:
        cmpl      $0, 236(%esp)
        jne       ..B3.115
..B3.114:
        cmpl      $0, 232(%esp)
        je        ..B3.127
..B3.115:
        cmpl      %ecx, %edx
        je        ..B3.117
..B3.116:
        fldt      64(%esp)
        subl      %ecx, %edx
        fstpt     32(%esp)
        addl      $16383, %edx
        fldt      288(%esp)
        andl      $32767, %edx
        fldt      300(%esp)
        andb      $127, 41(%esp)
        movzwl    40(%esp), %eax
        andl      $-32768, %eax
        orl       %edx, %eax
        movw      %ax, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %ecx, 312(%esp)
        fxch      %st(2)
        fstpt     288(%esp)
        fldt      288(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     300(%esp)
        fldt      300(%esp)
        movzwl    296(%esp), %edx
        fxch      %st(1)
        fstpt     64(%esp)
        movl      %edx, 84(%esp)
        jmp       ..B3.118
..B3.117:
        fldt      288(%esp)
        fstpt     64(%esp)
        fldt      300(%esp)
..B3.118:
        fldt      232(%esp)
        fldt      64(%esp)
        movl      84(%esp), %eax
        movl      60(%esp), %edx
        andl      $32767, %eax
        andl      $32767, %edx
        fsub      %st(1), %st
        movl      %ecx, 144(%esp)
        cmpl      %edx, %eax
        jg        ..B3.123
..B3.119:
        jne       ..B3.124
..B3.120:
        movl      292(%esp), %eax
        movl      236(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B3.123
..B3.121:
        jne       ..B3.124
..B3.122:
        movl      288(%esp), %eax
        cmpl      232(%esp), %eax
        jbe       ..B3.124
..B3.123:
        fldt      64(%esp)
        fsub      %st(1), %st
        fsubp     %st, %st(2)
        fldt      244(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     64(%esp)
        jmp       ..B3.125
..B3.124:
        fldt      64(%esp)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fldt      244(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     64(%esp)
..B3.125:
        fldt      64(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     120(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     132(%esp)
        jmp       ..B3.132
..B3.126:
        movl      292(%esp), %eax
..B3.127:
        testl     %eax, %eax
        jne       ..B3.129
..B3.177:
        cmpl      $0, 288(%esp)
..B3.128:
        je        ..B3.130
..B3.129:
        addl      $-16517, %ebx
        lea       -16383(%edx,%esi), %edx
        cmpl      %edx, %ebx
        jl        ..B3.131
..B3.130:
        fldt      232(%esp)
        fchs      
        movl      %ecx, 144(%esp)
        fstpt     120(%esp)
        fldt      244(%esp)
        fchs      
        fstpt     132(%esp)
        jmp       ..B3.132
..B3.131:
        movl      %eax, 124(%esp)
        movl      288(%esp), %edx
        movl      296(%esp), %eax
        movl      300(%esp), %ecx
        movl      304(%esp), %ebx
        movl      308(%esp), %esi
        movl      312(%esp), %edi
        movl      %edx, 120(%esp)
        movl      %eax, 128(%esp)
        movl      %ecx, 132(%esp)
        movl      %ebx, 136(%esp)
        movl      %esi, 140(%esp)
        movl      %edi, 144(%esp)
..B3.132:
        addl      $12, %esp
        lea       80(%esp), %eax
        lea       136(%esp), %ecx
        pushl     %eax
        lea       252(%esp), %edx
        pushl     %edx
        pushl     %ecx
        call      __libm_atan2l_k80
..B3.133:
        addl      $12, %esp
        lea       108(%esp), %eax
        lea       164(%esp), %ecx
        pushl     %eax
        lea       252(%esp), %edx
        pushl     %edx
        pushl     %ecx
        call      __libm_atan2l_k80
..B3.134:
        movzwl    156(%esp), %ebx
        movl      %ebx, %esi
        andl      $32767, %esi
        cmpl      $8383, %esi
        jl        ..B3.138
..B3.135:
        cmpl      $24383, %esi
        jg        ..B3.138
..B3.136:
        movzwl    184(%esp), %ecx
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
        cmpl      $8383, %ecx
        jl        ..B3.138
..B3.137:
        cmpl      $24383, %ecx
        jle       ..B3.141
..B3.138:
        addl      $4, %esp
        lea       144(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.139:
        addl      $4, %esp
        lea       172(%esp), %eax
        pushl     %eax
        call      __libm_normalizel_k80
..B3.140:
        movzwl    156(%esp), %ebx
        movl      %ebx, %esi
        movzwl    184(%esp), %ecx
        andl      $32767, %esi
        movl      %ecx, 44(%esp)
        andl      $32767, %ecx
..B3.141:
        movl      200(%esp), %edx
        movl      172(%esp), %eax
        movl      %eax, 76(%esp)
        lea       (%esi,%eax), %edi
        movl      %edi, 60(%esp)
        lea       -134(%edx,%ecx), %eax
        cmpl      %eax, %edi
        jle       ..B3.158
..B3.142:
        movl      152(%esp), %eax
        lea       134(%edx,%ecx), %edi
        cmpl      60(%esp), %edi
        jle       ..B3.159
..B3.143:
        testl     %eax, %eax
        jne       ..B3.145
..B3.144:
        cmpl      $0, 148(%esp)
        je        ..B3.160
..B3.145:
        cmpl      $0, 180(%esp)
        jne       ..B3.147
..B3.146:
        cmpl      $0, 176(%esp)
        je        ..B3.159
..B3.147:
        cmpl      76(%esp), %edx
        je        ..B3.149
..B3.148:
        fldt      64(%esp)
        movl      76(%esp), %eax
        fstpt     32(%esp)
        subl      %edx, %eax
        fldt      148(%esp)
        addl      $16383, %eax
        fldt      160(%esp)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        movzwl    40(%esp), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        movw      %cx, 40(%esp)
        movl      $-2147483648, 36(%esp)
        movl      $0, 32(%esp)
        fldt      32(%esp)
        fmul      %st, %st(2)
        movl      %edx, 172(%esp)
        fxch      %st(2)
        fstpt     148(%esp)
        fldt      148(%esp)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fstpt     160(%esp)
        fldt      160(%esp)
        movzwl    156(%esp), %ebx
        jmp       ..B3.150
..B3.149:
        fldt      148(%esp)
        fldt      160(%esp)
..B3.150:
        fldt      176(%esp)
        andl      $32767, %ebx
        movl      44(%esp), %eax
        fld       %st(2)
        andl      $32767, %eax
        fadd      %st(1), %st
        movl      %edx, 228(%esp)
        cmpl      %eax, %ebx
        jg        ..B3.155
..B3.151:
        jne       ..B3.156
..B3.152:
        movl      152(%esp), %eax
        movl      180(%esp), %ecx
        cmpl      %ecx, %eax
        ja        ..B3.155
..B3.153:
        jne       ..B3.156
..B3.154:
        movl      148(%esp), %eax
        cmpl      176(%esp), %eax
        jbe       ..B3.156
..B3.155:
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fldt      188(%esp)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
        jmp       ..B3.157
..B3.156:
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      188(%esp)
        faddp     %st, %st(1)
        fstpt     32(%esp)
        fldt      32(%esp)
..B3.157:
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B3.167
..B3.158:
        movl      152(%esp), %eax
..B3.159:
        testl     %eax, %eax
        jne       ..B3.161
..B3.181:
        cmpl      $0, 148(%esp)
..B3.160:
        je        ..B3.162
..B3.161:
        lea       -16517(%edx,%ecx), %ebx
        movl      76(%esp), %ecx
        lea       -16383(%ecx,%esi), %esi
        cmpl      %esi, %ebx
        jl        ..B3.163
..B3.162:
        movl      176(%esp), %eax
        movl      %eax, 204(%esp)
        movl      180(%esp), %ecx
        movl      184(%esp), %ebx
        movl      188(%esp), %esi
        movl      192(%esp), %edi
        movl      196(%esp), %eax
        movl      %ecx, 208(%esp)
        movl      %ebx, 212(%esp)
        fldt      204(%esp)
        movl      %esi, 216(%esp)
        movl      %edi, 220(%esp)
        movl      %eax, 224(%esp)
        fldt      216(%esp)
        movl      %edx, 228(%esp)
        jmp       ..B3.167
..B3.163:
        movl      148(%esp), %edx
        movl      %eax, 208(%esp)
        movl      156(%esp), %eax
        movl      160(%esp), %ecx
        movl      164(%esp), %ebx
        movl      168(%esp), %esi
        movl      %edx, 204(%esp)
        movl      %eax, 212(%esp)
        fldt      204(%esp)
        movl      76(%esp), %edx
        movl      %ecx, 216(%esp)
        movl      %ebx, 220(%esp)
        movl      %esi, 224(%esp)
        fldt      216(%esp)
        movl      %edx, 228(%esp)
        jmp       ..B3.167
..B3.164:
        je        ..B3.171
..B3.165:
        incl      284(%esp)
        addl      $12, %esp
        lea       388(%esp), %eax
        lea       192(%esp), %ecx
        pushl     %eax
        lea       252(%esp), %edx
        pushl     %edx
        pushl     %ecx
        call      __libm_atan2l_k80
..B3.166:
        fldt      204(%esp)
        fldt      216(%esp)
        movl      228(%esp), %edx
..B3.167:
        fldt      48(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fxch      %st(1)
        fstpt     204(%esp)
        fstpt     216(%esp)
        movl      %edx, 24(%esp)
        call      __libm_scalbl_k80
..B3.168:
        movl      428(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B3.170
..B3.169:
        addl      $436, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B3.170:
        fstp      %st(0)
        movl      88(%esp), %ebx
        call      __stack_chk_fail@PLT
..B3.171:
        movl      88(%esp), %edx
        movl      376(%esp), %eax
        cmpl      76+_CONSTANTS@GOTOFF(%edx), %eax
        jb        ..B3.72
..B3.172:
        jne       ..B3.165
..B3.173:
        movl      372(%esp), %eax
        cmpl      72+_CONSTANTS@GOTOFF(%edx), %eax
        jb        ..B3.72
        jmp       ..B3.165
        .align    16,0x90
	.type	imcatanhl,@function
	.size	imcatanhl,.-imcatanhl
	.data
# -- End  imcatanhl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.17:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,12
	.space 4, 0x00 	# pad
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
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
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
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,84
	.data
	.hidden __libm_normalizel_k80
	.hidden __libm_log_k80
	.hidden __libm_log1p_k80
	.hidden __libm_atan2l_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
