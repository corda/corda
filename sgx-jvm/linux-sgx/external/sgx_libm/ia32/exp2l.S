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
	.file "exp2l.c"
	.text
..TXTST0:
# -- Begin  exp2l
	.text
       .align    16,0x90
	.globl exp2l
exp2l:
# parameter 1: 80 + %esp
..B1.1:
..L1:

        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $64, %esp
..B1.2:
        fnstcw    54(%esp)
..B1.3:
        movzwl    88(%esp), %ebx
        movl      %ebx, %eax
        andl      $32767, %eax
        lea       -16383(%eax), %edx
        cmpl      $15, %edx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        jae       ..B1.10
..B1.4:
        movl      %eax, %ecx
        movl      $-1, %esi
        negl      %ecx
        addl      $30, %ecx
        shll      %cl, %esi
        notl      %esi
        andl      84(%esp), %esi
        orl       80(%esp), %esi
        jne       ..B1.10
..B1.5:
        fldt      80(%esp)
        faddl     _TWO_23H@GOTOFF(%edx)
        fstps     48(%esp)
        movl      48(%esp), %ecx
        shll      $10, %ecx
        sarl      $10, %ecx
        lea       16445(%ecx), %eax
        cmpl      $32828, %eax
        ja        ..B1.9
..B1.6:
        movl      %ecx, %edx
        andb      $127, 21(%esp)
        addl      $16383, %edx
        jle       ..B1.77
..B1.7:
        movzwl    20(%esp), %eax
        andl      $32767, %edx
        andl      $-32768, %eax
        orl       %edx, %eax
        movw      %ax, 20(%esp)
        movl      $-2147483648, 16(%esp)
        movl      $0, 12(%esp)
..B1.8:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.9:
        movl      %ebx, %eax
        andl      $32767, %eax
..B1.10:
        cmpl      $16389, %eax
        jge       ..B1.49
..B1.11:
        movzwl    54(%esp), %ecx
        cmpl      $16377, %eax
        jge       ..B1.42
..B1.12:
        cmpl      $16369, %eax
        jge       ..B1.35
..B1.13:
        cmpl      $16359, %eax
        jge       ..B1.28
..B1.14:
        cmpl      $16316, %eax
        jge       ..B1.21
..B1.15:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.80
..B1.16:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.17:
        fldcw     52(%esp)
..B1.18:
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fldt      80(%esp)
        faddp     %st, %st(1)
        fstpt     12(%esp)
..B1.19:
        fldcw     54(%esp)
..B1.20:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.21:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.81
..B1.22:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.23:
        fldcw     52(%esp)
..B1.24:
        movl      $1, %eax
..B1.25:
        fldt      80(%esp)
        testl     %eax, %eax
        fldt      12+_Q2@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        je        ..B1.27
..B1.26:
        fldcw     54(%esp)
..B1.27:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.28:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.82
..B1.29:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.30:
        fldcw     52(%esp)
..B1.31:
        movl      $1, %eax
..B1.32:
        fldt      80(%esp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      12+_Q1@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        je        ..B1.34
..B1.33:
        fldcw     54(%esp)
..B1.34:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.35:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.83
..B1.36:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.37:
        fldcw     52(%esp)
..B1.38:
        movl      $1, %eax
..B1.39:
        fldl      _TWO_48H@GOTOFF(%edx)
        testl     %eax, %eax
        fldt      80(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fld       %st(2)
        fsub      %st(4), %st
        fldt      84+_Q0@GOTOFF(%edx)
        fmul      %st(3), %st
        fldt      72+_Q0@GOTOFF(%edx)
        fmul      %st(4), %st
        fldt      60+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      48+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      36+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      24+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      12+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      _Q0@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      96+_Q0@GOTOFF(%edx)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        je        ..B1.100
..B1.40:
        fstpt     24(%esp)
..B1.96:
        fldcw     54(%esp)
        jmp       ..B1.41
..B1.100:
        fstp      %st(0)
..B1.41:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.42:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.84
..B1.43:
        orl       $-64768, %ecx
        movw      %cx, 52(%esp)
..B1.44:
        fldcw     52(%esp)
..B1.45:
        movl      $1, %ebx
..B1.46:
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      80(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(2)
        movl      24(%esp), %eax
        fsubp     %st, %st(1)
        fldl      _TWO_48H@GOTOFF(%edx)
        fld       %st(0)
        movsbl    %al, %ecx
        fadd      %st(2), %st
        subl      %ecx, %eax
        fsub      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        shll      $4, %ecx
        fxch      %st(2)
        fadd      %st(3), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldt      60+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%edx)
        shrl      $8, %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        shll      $23, %eax
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        addl      $1065353216, %eax
        fldt      12+_P@GOTOFF(%edx)
        testl     %ebx, %ebx
        movl      %eax, 36(%esp)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      72+_P@GOTOFF(%edx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%ecx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%ecx)
        fld       %st(0)
        fmul      %st(5), %st
        fxch      %st(4)
        fadd      %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(3)
        fmul      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmuls     36(%esp)
        fstpt     12(%esp)
        je        ..B1.101
..B1.47:
        fstpt     24(%esp)
..B1.97:
        fldcw     54(%esp)
        jmp       ..B1.48
..B1.101:
        fstp      %st(0)
..B1.48:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.49:
        movzbl    89(%esp), %ebx
        andl      $128, %ebx
        shrl      $7, %ebx
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ecx
        movzwl    8+_ranges@GOTOFF(%ecx,%edx), %esi
        andl      $32767, %esi
        cmpl      %esi, %eax
        jl        ..B1.54
..B1.50:
        jne       ..B1.66
..B1.51:
        movl      84(%esp), %esi
        cmpl      4+_ranges@GOTOFF(%ecx,%edx), %esi
        jb        ..B1.54
..B1.52:
        jne       ..B1.66
..B1.53:
        movl      80(%esp), %esi
        cmpl      _ranges@GOTOFF(%ecx,%edx), %esi
        jae       ..B1.66
..B1.54:
        movzwl    54(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.88
..B1.55:
        orl       $-64768, %esi
        movw      %si, 52(%esp)
..B1.56:
        fldcw     52(%esp)
..B1.57:
        movl      $1, %esi
..B1.58:
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fstpt     36(%esp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      80(%esp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(1), %st
        movl      %ecx, 12(%esp)
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmulp     %st, %st(2)
        movl      24(%esp), %eax
        movl      %eax, %ecx
        movsbl    %al, %edi
        addl      $4196735, %eax
        subl      %edi, %ecx
        fsubp     %st, %st(1)
        fldl      _TWO_48H@GOTOFF(%edx)
        fld       %st(0)
        shll      $4, %edi
        fadd      %st(2), %st
        shrl      $8, %ecx
        fsub      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsubr     %st, %st(3)
        addl      SC2_BIAS@GOTOFF(%edx,%ebx,4), %ecx
        fxch      %st(2)
        fadd      %st(3), %st
        fld       %st(0)
        andl      $32767, %ecx
        fmul      %st(1), %st
        fldt      60+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%edx)
        movl      %esi, 16(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        movzwl    8+.L_2il0floatpacket.2@GOTOFF(%edx), %esi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        andl      $-32768, %esi
        fldt      12+_P@GOTOFF(%edx)
        orl       %ecx, %esi
        movw      %si, 44(%esp)
        cmpl      $3070, %eax
        movl      16(%esp), %esi
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%edx)
        movl      12(%esp), %ecx
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      72+_P@GOTOFF(%edx)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(2)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%edi)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(3)
        fadd      %st, %st(4)
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fxch      %st(3)
        fmull     2056+__libm_expl_table_256@GOTOFF(%edx,%edi)
        faddp     %st, %st(2)
        fldt      80(%esp)
        fxch      %st(2)
        faddl     2056+__libm_expl_table_256@GOTOFF(%edx,%edi)
        ja        ..B1.61
..B1.59:
        fldt      _exp2but@GOTOFF(%edx)
        fcomp     %st(3)
        fnstsw    %ax
        sahf      
        jbe       ..B1.61
..B1.60:
        fstp      %st(1)
        fstp      %st(1)
        fldl      _TWO_12H@GOTOFF(%edx)
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fsub      %st, %st(1)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      _SC2@GOTOFF(%ecx,%edx)
        fldt      36(%esp)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmul      %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        jmp       ..B1.62
..B1.61:
        fstp      %st(2)
        fldt      36(%esp)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      _SC2@GOTOFF(%ecx,%edx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
..B1.62:
        fldt      _minnorm@GOTOFF(%edx)
        fcompp    
        fnstsw    %ax
        sahf      
        ja        ..B1.85
..B1.63:
        testl     %esi, %esi
        je        ..B1.102
..B1.64:
        fstpt     24(%esp)
..B1.98:
        fldcw     54(%esp)
        jmp       ..B1.65
..B1.102:
        fstp      %st(0)
..B1.65:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.66:
        movzwl    54(%esp), %esi
        movl      %esi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.93
..B1.67:
        orl       $-64768, %esi
        movw      %si, 52(%esp)
..B1.68:
        fldcw     52(%esp)
..B1.69:
        movzwl    88(%esp), %eax
        movl      $1, %ecx
        andl      $32767, %eax
..B1.70:
        cmpl      $32767, %eax
        je        ..B1.89
..B1.71:
        testl     %ebx, %ebx
        je        ..B1.73
..B1.72:
        fldt      _small_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     12(%esp)
        jmp       ..B1.74
..B1.73:
        fldt      _large_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     12(%esp)
..B1.74:
        testl     %ecx, %ecx
        je        ..B1.76
..B1.75:
        fldcw     54(%esp)
..B1.76:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.77:
        movzwl    20(%esp), %eax
        andl      $-32768, %eax
        movw      %ax, 20(%esp)
        cmpl      $-31, %edx
        jle       ..B1.79
..B1.78:
        addl      $16413, %ecx
        movl      $1, %eax
        shll      %cl, %eax
        movl      %eax, 16(%esp)
        movl      $0, 12(%esp)
        jmp       ..B1.8
..B1.79:
        addl      $16413, %ecx
        movl      $1, %eax
        shll      %cl, %eax
        movl      $0, 16(%esp)
        movl      %eax, 12(%esp)
        jmp       ..B1.8
..B1.80:
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fldt      80(%esp)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        jmp       ..B1.20
..B1.81:
        xorl      %eax, %eax
        jmp       ..B1.25
..B1.82:
        xorl      %eax, %eax
        jmp       ..B1.32
..B1.83:
        xorl      %eax, %eax
        jmp       ..B1.39
..B1.84:
        xorl      %ebx, %ebx
        jmp       ..B1.46
..B1.85:
        fldt      _small_value_80@GOTOFF(%edx)
        testl     %esi, %esi
        fmul      %st(0), %st
        fstpt     (%esp)
        je        ..B1.103
..B1.86:
        fstpt     24(%esp)
..B1.99:
        fldcw     54(%esp)
        jmp       ..B1.87
..B1.103:
        fstp      %st(0)
..B1.87:
        fldt      12(%esp)
        addl      $64, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        ret       
..B1.88:
        xorl      %esi, %esi
        jmp       ..B1.58
..B1.89:
        cmpl      $-2147483648, 84(%esp)
        jne       ..B1.92
..B1.90:
        cmpl      $0, 80(%esp)
        jne       ..B1.92
..B1.91:
        fldl      _inf_zeros@GOTOFF(%edx,%ebx,8)
        fstpt     12(%esp)
        jmp       ..B1.74
..B1.92:
        fldt      80(%esp)
        fstpt     12(%esp)
        jmp       ..B1.74
..B1.93:
        xorl      %ecx, %ecx
        jmp       ..B1.70
        .align    16,0x90
	.type	exp2l,@function
	.size	exp2l,.-exp2l
	.data
# -- End  exp2l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x07,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xf7,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.align 4
_TWO_23H:
	.long	0
	.long	1097334784
	.type	_TWO_23H,@object
	.size	_TWO_23H,8
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
SC2_BIAS:
	.long	8191
	.long	24575
	.type	SC2_BIAS,@object
	.size	SC2_BIAS,8
	.align 4
_TWO_12H:
	.long	0
	.long	1085800448
	.type	_TWO_12H,@object
	.size	_TWO_12H,8
	.align 4
_inf_zeros:
	.long	0
	.long	2146435072
	.long	0
	.long	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,16
	.align 2
_Q2:
	.word	32967
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	32541
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	30019
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.word	64504
	.word	31847
	.word	18104
	.word	58200
	.word	16378
	.word	0
	.word	27878
	.word	3845
	.word	23422
	.word	40341
	.word	16376
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q0:
	.word	20226
	.word	52574
	.word	36475
	.word	49086
	.word	16363
	.word	0
	.word	30019
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.word	64711
	.word	9477
	.word	18104
	.word	58200
	.word	16378
	.word	0
	.word	47522
	.word	53875
	.word	23421
	.word	40341
	.word	16376
	.word	0
	.word	43745
	.word	20212
	.word	65340
	.word	44739
	.word	16373
	.word	0
	.word	65207
	.word	12853
	.word	35196
	.word	41348
	.word	16370
	.word	0
	.word	16083
	.word	62062
	.word	13520
	.word	65510
	.word	16366
	.word	0
	.word	39312
	.word	30281
	.word	14077
	.word	45408
	.word	16363
	.word	0
	.word	0
	.word	0
	.word	0
	.word	45426
	.word	16382
	.word	0
	.type	_Q0,@object
	.size	_Q0,108
	.align 2
_P:
	.word	12039
	.word	52575
	.word	36475
	.word	49086
	.word	16363
	.word	0
	.word	30020
	.word	5676
	.word	61436
	.word	62973
	.word	16380
	.word	0
	.word	64966
	.word	9463
	.word	18104
	.word	58200
	.word	16378
	.word	0
	.word	46891
	.word	53866
	.word	23421
	.word	40341
	.word	16376
	.word	0
	.word	12114
	.word	63546
	.word	699
	.word	44740
	.word	16373
	.word	0
	.word	63250
	.word	29232
	.word	35994
	.word	41348
	.word	16370
	.word	0
	.word	0
	.word	0
	.word	0
	.word	45426
	.word	16382
	.word	0
	.type	_P,@object
	.size	_P,84
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16397
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	32894
	.word	16397
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
	.align 2
_exp2but:
	.word	0
	.word	0
	.word	0
	.word	65528
	.word	49164
	.word	0
	.type	_exp2but,@object
	.size	_exp2but,12
	.align 2
_SC2:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24575
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.type	_SC2,@object
	.size	_SC2,24
	.align 2
_minnorm:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.type	_minnorm,@object
	.size	_minnorm,12
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
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
