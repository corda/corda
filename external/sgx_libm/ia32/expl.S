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
	.file "expl.c"
	.text
..TXTST0:
# -- Begin  expl
	.text
       .align    16,0x90
	.globl expl
expl:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $84, %esp
..B1.2:
        fnstcw    70(%esp)
..B1.3:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $16389, %eax
        jge       ..B1.50
..B1.4:
        movzwl    70(%esp), %ecx
        cmpl      $16378, %eax
        jge       ..B1.43
..B1.5:
        cmpl      $16372, %eax
        jge       ..B1.36
..B1.6:
        cmpl      $16365, %eax
        jge       ..B1.29
..B1.7:
        cmpl      $16357, %eax
        jge       ..B1.22
..B1.8:
        cmpl      $16316, %eax
        jge       ..B1.15
..B1.9:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.78
..B1.10:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.11:
        fldcw     68(%esp)
..B1.12:
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fldt      8(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.13:
        fldcw     70(%esp)
..B1.14:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.15:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.79
..B1.16:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.17:
        fldcw     68(%esp)
..B1.18:
        movl      $1, %eax
..B1.19:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      _Q3@GOTOFF(%edx)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.21
..B1.20:
        fldcw     70(%esp)
..B1.21:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.22:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.80
..B1.23:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.24:
        fldcw     68(%esp)
..B1.25:
        movl      $1, %eax
..B1.26:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      12+_Q2@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fmul      %st(1), %st
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.28
..B1.27:
        fldcw     70(%esp)
..B1.28:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.81
..B1.30:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.31:
        fldcw     68(%esp)
..B1.32:
        movl      $1, %eax
..B1.33:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fldt      36+_Q1@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_Q1@GOTOFF(%edx)
        fmul      %st(3), %st
        fldt      12+_Q1@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldt      _Q1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fadd      %st, %st(2)
        fsubr     %st(2), %st
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.35
..B1.34:
        fldcw     70(%esp)
..B1.35:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.36:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.82
..B1.37:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.38:
        fldcw     68(%esp)
..B1.39:
        movl      $1, %eax
..B1.40:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      84+_Q0@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      72+_Q0@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      60+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      48+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      36+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmul      %st(3), %st
        fldt      _Q0@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fadd      %st, %st(1)
        fsubr     %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.42
..B1.41:
        fldcw     70(%esp)
..B1.42:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.43:
        movl      %ecx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.83
..B1.44:
        orl       $-64768, %ecx
        movw      %cx, 68(%esp)
..B1.45:
        fldcw     68(%esp)
..B1.46:
        movl      $1, %ebx
..B1.47:
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(2), %st
        movl      24(%esp), %eax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(3)
        movsbl    %al, %ecx
        fsub      %st(2), %st
        fldl      _TWO_32H@GOTOFF(%edx)
        subl      %ecx, %eax
        shll      $4, %ecx
        fadd      %st, %st(1)
        shrl      $8, %eax
        fsubr     %st(1), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        shll      $23, %eax
        fxch      %st(4)
        fsubrp    %st, %st(3)
        addl      $1065353216, %eax
        fadd      %st(2), %st
        fld       %st(0)
        testl     %ebx, %ebx
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        movl      %eax, 36(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      _P@GOTOFF(%edx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%ecx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%ecx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmuls     36(%esp)
        fstpt     (%esp)
        je        ..B1.95
..B1.48:
        fstpt     24(%esp)
..B1.93:
        fldcw     70(%esp)
        jmp       ..B1.49
..B1.95:
        fstp      %st(0)
..B1.49:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.50:
        movzbl    17(%ebp), %ebx
        andl      $128, %ebx
        shrl      $7, %ebx
        lea       (,%ebx,8), %ecx
        lea       (%ecx,%ebx,4), %ecx
        movzwl    8+_ranges@GOTOFF(%ecx,%edx), %esi
        andl      $32767, %esi
        cmpl      %esi, %eax
        jl        ..B1.55
..B1.51:
        jne       ..B1.67
..B1.52:
        movl      12(%ebp), %esi
        cmpl      4+_ranges@GOTOFF(%ecx,%edx), %esi
        jb        ..B1.55
..B1.53:
        jne       ..B1.67
..B1.54:
        movl      8(%ebp), %esi
        cmpl      _ranges@GOTOFF(%ecx,%edx), %esi
        jae       ..B1.67
..B1.55:
        movzwl    70(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.85
..B1.56:
        orl       $-64768, %esi
        movw      %si, 68(%esp)
..B1.57:
        fldcw     68(%esp)
..B1.58:
        movl      $1, %esi
..B1.59:
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fstpt     (%esp)
        fldl      _TWO_63H@GOTOFF(%edx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        fmul      %st(1), %st
        movl      %ecx, 36(%esp)
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fmul      %st(2), %st
        movl      24(%esp), %eax
        movl      %eax, %ecx
        movsbl    %al, %edi
        addl      $4196735, %eax
        subl      %edi, %ecx
        fsubr     %st(1), %st
        fld       %st(0)
        fldt      .L_2il0floatpacket.2@GOTOFF(%edx)
        fmulp     %st, %st(4)
        shll      $4, %edi
        fsub      %st(3), %st
        fldl      _TWO_32H@GOTOFF(%edx)
        shrl      $8, %ecx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        addl      SC2_BIAS@GOTOFF(%edx,%ebx,4), %ecx
        fxch      %st(4)
        fsubrp    %st, %st(2)
        movl      %esi, 52(%esp)
        andl      $32767, %ecx
        movzwl    8+.L_2il0floatpacket.3@GOTOFF(%edx), %esi
        fadd      %st(1), %st
        fld       %st(0)
        andl      $-32768, %esi
        fmul      %st(1), %st
        orl       %ecx, %esi
        fldt      48+_P@GOTOFF(%edx)
        cmpl      $3070, %eax
        fmul      %st(1), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%edx)
        movw      %si, 8(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%edx)
        movl      52(%esp), %esi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fmulp     %st, %st(3)
        movl      36(%esp), %ecx
        faddp     %st, %st(2)
        fldt      _P@GOTOFF(%edx)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%edi)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmull     2056+__libm_expl_table_256@GOTOFF(%edx,%edi)
        faddp     %st, %st(2)
        faddl     2056+__libm_expl_table_256@GOTOFF(%edx,%edi)
        faddp     %st, %st(1)
        ja        ..B1.62
..B1.60:
        fldt      _expbut@GOTOFF(%edx)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B1.62
..B1.61:
        fstp      %st(1)
        fldl      _TWO_12H@GOTOFF(%edx)
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fstpt     40(%esp)
        fldt      40(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      _SC2@GOTOFF(%ecx,%edx)
        fldt      (%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     56(%esp)
        jmp       ..B1.63
..B1.62:
        fstp      %st(1)
        fldt      (%esp)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      _SC2@GOTOFF(%ecx,%edx)
        fmulp     %st, %st(1)
        fstpt     56(%esp)
..B1.63:
        fldt      _minnorm@GOTOFF(%edx)
        fldt      56(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jp        ..B1.64
        jb        ..B1.84
..B1.64:
        testl     %esi, %esi
        je        ..B1.66
..B1.65:
        fldt      40(%esp)
        fstpt     24(%esp)
..B1.94:
        fldcw     70(%esp)
..B1.66:
        fldt      56(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.67:
        movzwl    70(%esp), %esi
        movl      %esi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.90
..B1.68:
        orl       $-64768, %esi
        movw      %si, 68(%esp)
..B1.69:
        fldcw     68(%esp)
..B1.70:
        movzwl    16(%ebp), %eax
        movl      $1, %ecx
        andl      $32767, %eax
..B1.71:
        cmpl      $32767, %eax
        je        ..B1.86
..B1.72:
        testl     %ebx, %ebx
        je        ..B1.74
..B1.73:
        fldt      _small_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.75
..B1.74:
        fldt      _large_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.75:
        testl     %ecx, %ecx
        je        ..B1.77
..B1.76:
        fldcw     70(%esp)
..B1.77:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.78:
        fldt      .L_2il0floatpacket.3@GOTOFF(%edx)
        fldt      8(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.14
..B1.79:
        xorl      %eax, %eax
        jmp       ..B1.19
..B1.80:
        xorl      %eax, %eax
        jmp       ..B1.26
..B1.81:
        xorl      %eax, %eax
        jmp       ..B1.33
..B1.82:
        xorl      %eax, %eax
        jmp       ..B1.40
..B1.83:
        xorl      %ebx, %ebx
        jmp       ..B1.47
..B1.84:
        fldt      _small_value_80@GOTOFF(%edx)
        fmul      %st(0), %st
        fstpt     12(%esp)
        jmp       ..B1.64
..B1.85:
        xorl      %esi, %esi
        jmp       ..B1.59
..B1.86:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.89
..B1.87:
        cmpl      $0, 8(%ebp)
        jne       ..B1.89
..B1.88:
        fldl      _inf_zeros@GOTOFF(%edx,%ebx,8)
        fstpt     (%esp)
        jmp       ..B1.75
..B1.89:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.75
..B1.90:
        xorl      %ecx, %ecx
        jmp       ..B1.71
        .align    16,0x90
	.type	expl,@function
	.size	expl,.-expl
	.data
# -- End  expl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0xf0,0x17,0x5c,0x29,0x3b,0xaa,0xb8,0x07,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0xf8,0x17,0x72,0xb1,0xf6,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x30,0x71,0xd8,0x50,0x19,0xc2,0xb8,0xd4,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
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
_Q3:
	.word	1
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.type	_Q3,@object
	.size	_Q3,12
	.align 2
_Q2:
	.word	21845
	.word	85
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	13107
	.word	43827
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	65172
	.word	65535
	.word	65535
	.word	65535
	.word	16381
	.word	0
	.word	43275
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	49902
	.word	27670
	.word	43713
	.word	43690
	.word	16378
	.word	0
	.word	39976
	.word	2496
	.word	34972
	.word	34952
	.word	16376
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q0:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	62628
	.word	43690
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	53890
	.word	34952
	.word	34952
	.word	34952
	.word	16376
	.word	0
	.word	62001
	.word	21104
	.word	24757
	.word	46603
	.word	16373
	.word	0
	.word	21793
	.word	10330
	.word	207
	.word	53261
	.word	16370
	.word	0
	.word	18027
	.word	53571
	.word	10421
	.word	53262
	.word	16367
	.word	0
	.word	54394
	.word	23739
	.word	10794
	.word	47344
	.word	16364
	.word	0
	.type	_Q0,@object
	.size	_Q0,96
	.align 2
_P:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.type	_P,@object
	.size	_P,60
	.align 2
_ranges:
	.word	31148
	.word	53711
	.word	6135
	.word	45426
	.word	16396
	.word	0
	.word	488
	.word	27074
	.word	50247
	.word	45600
	.word	16396
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
	.align 2
_expbut:
	.word	60207
	.word	4624
	.word	35943
	.word	45420
	.word	49164
	.word	0
	.type	_expbut,@object
	.size	_expbut,12
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
