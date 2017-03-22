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
	.file "exp10l.c"
	.text
..TXTST0:
# -- Begin  exp10l
	.text
       .align    16,0x90
	.globl exp10l
exp10l:
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
        fnstcw    74(%esp)
..B1.3:
        movzbl    17(%ebp), %edx
        movl      %edx, %ecx
        movzwl    16(%ebp), %ebx
        movl      %ebx, %esi
        shrl      $7, %ecx
        andl      $32767, %esi
        shll      $15, %ecx
        orl       %esi, %ecx
        shll      $16, %ecx
        movzwl    14(%ebp), %eax
        orl       %eax, %ecx
        addl      $-1073709056, %ecx
        cmpl      $284672, %ecx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        ja        ..B1.6
..B1.4:
        fldt      8(%ebp)
        fld       %st(0)
        fldl      _TWO_23H@GOTOFF(%ecx)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstps     68(%esp)
        flds      68(%esp)
        fsubp     %st, %st(1)
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B1.5
        je        ..B1.85
..B1.5:
        movl      %ebx, %esi
        andl      $32767, %esi
..B1.6:
        cmpl      $16388, %esi
        jge       ..B1.45
..B1.7:
        movzwl    74(%esp), %eax
        cmpl      $16375, %esi
        jge       ..B1.38
..B1.8:
        cmpl      $16368, %esi
        jge       ..B1.31
..B1.9:
        cmpl      $16357, %esi
        jge       ..B1.24
..B1.10:
        cmpl      $16316, %esi
        jge       ..B1.17
..B1.11:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.74
..B1.12:
        orl       $-64768, %eax
        movw      %ax, 72(%esp)
..B1.13:
        fldcw     72(%esp)
..B1.14:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fldt      8(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
..B1.15:
        fldcw     74(%esp)
..B1.16:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.17:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.75
..B1.18:
        orl       $-64768, %eax
        movw      %ax, 72(%esp)
..B1.19:
        fldcw     72(%esp)
..B1.20:
        movl      $1, %eax
..B1.21:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldt      12+_Q2@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      _Q2@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.23
..B1.22:
        fldcw     74(%esp)
..B1.23:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.24:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.76
..B1.25:
        orl       $-64768, %eax
        movw      %ax, 72(%esp)
..B1.26:
        fldcw     72(%esp)
..B1.27:
        movl      $1, %eax
..B1.28:
        fldt      8(%ebp)
        testl     %eax, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fldt      36+_Q1@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      24+_Q1@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      12+_Q1@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fldt      _Q1@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.30
..B1.29:
        fldcw     74(%esp)
..B1.30:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.31:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.77
..B1.32:
        orl       $-64768, %eax
        movw      %ax, 72(%esp)
..B1.33:
        fldcw     72(%esp)
..B1.34:
        movl      $1, %eax
..B1.35:
        fldl      _TWO_48H@GOTOFF(%ecx)
        testl     %eax, %eax
        fldt      8(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(1)
        fadd      %st(3), %st
        fsub      %st, %st(3)
        fld       %st(2)
        fsub      %st(4), %st
        fldt      84+_Q0@GOTOFF(%ecx)
        fmul      %st(3), %st
        fldt      72+_Q0@GOTOFF(%ecx)
        fmul      %st(4), %st
        fldt      60+_Q0@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      48+_Q0@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      36+_Q0@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      24+_Q0@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      12+_Q0@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      _Q0@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      96+_Q0@GOTOFF(%ecx)
        fmul      %st, %st(4)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        je        ..B1.91
..B1.36:
        fstpt     24(%esp)
..B1.88:
        fldcw     74(%esp)
        jmp       ..B1.37
..B1.91:
        fstp      %st(0)
..B1.37:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        movl      %eax, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.78
..B1.39:
        orl       $-64768, %eax
        movw      %ax, 72(%esp)
..B1.40:
        fldcw     72(%esp)
..B1.41:
        movl      $1, %ebx
..B1.42:
        fldl      _TWO_63H@GOTOFF(%ecx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmul      %st(2), %st
        movl      24(%esp), %eax
        fsubrp    %st, %st(1)
        fld       %st(0)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmulp     %st, %st(3)
        movsbl    %al, %edx
        fsub      %st(2), %st
        fldl      _TWO_48H@GOTOFF(%ecx)
        subl      %edx, %eax
        shll      $4, %edx
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
        fldt      60+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%ecx)
        movl      %eax, 36(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmulp     %st, %st(3)
        fldl      2056+__libm_expl_table_256@GOTOFF(%ecx,%edx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%ecx,%edx)
        fld       %st(0)
        fmul      %st(4), %st
        fxch      %st(4)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmuls     36(%esp)
        fstpt     (%esp)
        je        ..B1.92
..B1.43:
        fstpt     24(%esp)
..B1.89:
        fldcw     74(%esp)
        jmp       ..B1.44
..B1.92:
        fstp      %st(0)
..B1.44:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ebx
        movzwl    8+_ranges@GOTOFF(%ebx,%ecx), %eax
        andl      $32767, %eax
        cmpl      %eax, %esi
        jl        ..B1.50
..B1.46:
        jne       ..B1.63
..B1.47:
        movl      12(%ebp), %eax
        cmpl      4+_ranges@GOTOFF(%ebx,%ecx), %eax
        jb        ..B1.50
..B1.48:
        jne       ..B1.63
..B1.49:
        movl      8(%ebp), %eax
        cmpl      _ranges@GOTOFF(%ebx,%ecx), %eax
        jae       ..B1.63
..B1.50:
        movzwl    74(%esp), %esi
        movl      %esi, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.79
..B1.51:
        orl       $-64768, %esi
        movw      %si, 72(%esp)
..B1.52:
        fldcw     72(%esp)
..B1.53:
        movl      $1, %esi
..B1.54:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fstpt     (%esp)
        fldl      _TWO_63H@GOTOFF(%ecx)
        fldt      8(%ebp)
        fldt      .L_2il0floatpacket.0@GOTOFF(%ecx)
        fmul      %st(1), %st
        movl      %ebx, 36(%esp)
        fadd      %st(2), %st
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubp     %st, %st(2)
        fldt      .L_2il0floatpacket.1@GOTOFF(%ecx)
        fmul      %st(2), %st
        movl      24(%esp), %eax
        movl      %eax, %ebx
        movsbl    %al, %edi
        addl      $4196735, %eax
        subl      %edi, %ebx
        fsubr     %st(1), %st
        fld       %st(0)
        fldt      .L_2il0floatpacket.2@GOTOFF(%ecx)
        fmulp     %st, %st(4)
        shll      $4, %edi
        fsub      %st(3), %st
        fldl      _TWO_48H@GOTOFF(%ecx)
        shrl      $8, %ebx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     40(%esp)
        fldt      40(%esp)
        fsubp     %st, %st(1)
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(2)
        addl      SC2_BIAS@GOTOFF(%ecx,%edx,4), %ebx
        fxch      %st(4)
        fsubrp    %st, %st(2)
        movl      %esi, 52(%esp)
        andl      $32767, %ebx
        movzwl    8+.L_2il0floatpacket.3@GOTOFF(%ecx), %esi
        fadd      %st(1), %st
        fld       %st(0)
        andl      $-32768, %esi
        fmul      %st(1), %st
        orl       %ebx, %esi
        fldt      60+_P@GOTOFF(%ecx)
        cmpl      $3070, %eax
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%ecx)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%ecx)
        movw      %si, 8(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      24+_P@GOTOFF(%ecx)
        movl      52(%esp), %esi
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      12+_P@GOTOFF(%ecx)
        movl      36(%esp), %ebx
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fldl      2048+__libm_expl_table_256@GOTOFF(%ecx,%edi)
        fld       %st(0)
        fmul      %st(2), %st
        fxch      %st(2)
        fadd      %st(4), %st
        fxch      %st(1)
        fmul      %st, %st(4)
        faddp     %st, %st(4)
        fmull     2056+__libm_expl_table_256@GOTOFF(%ecx,%edi)
        faddp     %st, %st(1)
        faddl     2056+__libm_expl_table_256@GOTOFF(%ecx,%edi)
        ja        ..B1.57
..B1.55:
        fldt      _exp10but@GOTOFF(%ecx)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B1.57
..B1.56:
        fstp      %st(1)
        fldl      _TWO_12H@GOTOFF(%ecx)
        fld       %st(2)
        fadd      %st(2), %st
        fmul      %st, %st(1)
        fadd      %st(1), %st
        fstpt     40(%esp)
        fldt      40(%esp)
        fsubp     %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      _SC2@GOTOFF(%ebx,%ecx)
        fldt      (%esp)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     56(%esp)
        jmp       ..B1.58
..B1.57:
        fstp      %st(1)
        fldt      (%esp)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      _SC2@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     56(%esp)
..B1.58:
        fldt      _minnorm@GOTOFF(%ecx)
        fldt      56(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.60
        jp        ..B1.60
..B1.59:
        fldt      _small_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     12(%esp)
..B1.60:
        testl     %esi, %esi
        je        ..B1.62
..B1.61:
        fldt      40(%esp)
        fstpt     24(%esp)
..B1.90:
        fldcw     74(%esp)
..B1.62:
        fldt      56(%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.63:
        movzwl    74(%esp), %ebx
        movl      %ebx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.84
..B1.64:
        orl       $-64768, %ebx
        movw      %bx, 72(%esp)
..B1.65:
        fldcw     72(%esp)
..B1.66:
        movzwl    16(%ebp), %esi
        movl      $1, %eax
        andl      $32767, %esi
..B1.67:
        cmpl      $32767, %esi
        je        ..B1.80
..B1.68:
        testl     %edx, %edx
        je        ..B1.70
..B1.69:
        fldt      _small_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     (%esp)
        jmp       ..B1.71
..B1.70:
        fldt      _large_value_80@GOTOFF(%ecx)
        fmul      %st(0), %st
        fstpt     (%esp)
..B1.71:
        testl     %eax, %eax
        je        ..B1.73
..B1.72:
        fldcw     74(%esp)
..B1.73:
        fldt      (%esp)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.74:
        fldt      .L_2il0floatpacket.3@GOTOFF(%ecx)
        fldt      8(%ebp)
        faddp     %st, %st(1)
        fstpt     (%esp)
        jmp       ..B1.16
..B1.75:
        xorl      %eax, %eax
        jmp       ..B1.21
..B1.76:
        xorl      %eax, %eax
        jmp       ..B1.28
..B1.77:
        xorl      %eax, %eax
        jmp       ..B1.35
..B1.78:
        xorl      %ebx, %ebx
        jmp       ..B1.42
..B1.79:
        xorl      %esi, %esi
        jmp       ..B1.54
..B1.80:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.83
..B1.81:
        cmpl      $0, 8(%ebp)
        jne       ..B1.83
..B1.82:
        fldl      _inf_zeros@GOTOFF(%ecx,%edx,8)
        fstpt     (%esp)
        jmp       ..B1.71
..B1.83:
        fldt      8(%ebp)
        fstpt     (%esp)
        jmp       ..B1.71
..B1.84:
        xorl      %eax, %eax
        jmp       ..B1.67
..B1.85:
        movl      68(%esp), %edx
        andl      $4194303, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ebx
        fldt      -12+_exact_values@GOTOFF(%ecx,%ebx)
        addl      $84, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	exp10l,@function
	.size	exp10l,.-exp10l
	.data
# -- End  exp10l
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x88,0x1b,0xcd,0x4b,0x78,0x9a,0xd4,0x08,0x40,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x85,0x9a,0x20,0x9a,0xf5,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0xc8,0xd4,0x0e,0xee,0x0c,0x01,0x86,0xd0,0xbf,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.3:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,12
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
	.word	45097
	.word	43688
	.word	36317
	.word	37725
	.word	16384
	.word	0
	.word	18666
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.type	_Q2,@object
	.size	_Q2,24
	.align 2
_Q1:
	.word	44055
	.word	43688
	.word	36317
	.word	37725
	.word	16384
	.word	0
	.word	17466
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	8942
	.word	31610
	.word	11407
	.word	33336
	.word	16384
	.word	0
	.word	11423
	.word	35775
	.word	45136
	.word	38379
	.word	16383
	.word	0
	.type	_Q1,@object
	.size	_Q1,48
	.align 2
_Q0:
	.word	11092
	.word	42962
	.word	43694
	.word	58436
	.word	49135
	.word	0
	.word	17467
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	8985
	.word	61797
	.word	11406
	.word	33336
	.word	16384
	.word	0
	.word	8830
	.word	60614
	.word	45135
	.word	38379
	.word	16383
	.word	0
	.word	62265
	.word	59600
	.word	65512
	.word	35348
	.word	16382
	.word	0
	.word	36045
	.word	15298
	.word	47170
	.word	54262
	.word	16380
	.word	0
	.word	33342
	.word	6003
	.word	33899
	.word	35698
	.word	16379
	.word	0
	.word	20958
	.word	58269
	.word	28628
	.word	41099
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	0
	.word	37726
	.word	16384
	.word	0
	.type	_Q0,@object
	.size	_Q0,108
	.align 2
_P:
	.word	405
	.word	44055
	.word	43688
	.word	36317
	.word	16368
	.word	0
	.word	17467
	.word	59219
	.word	9785
	.word	43433
	.word	16384
	.word	0
	.word	7929
	.word	61789
	.word	11406
	.word	33336
	.word	16384
	.word	0
	.word	36964
	.word	60605
	.word	45135
	.word	38379
	.word	16383
	.word	0
	.word	55739
	.word	36897
	.word	684
	.word	35349
	.word	16382
	.word	0
	.word	13923
	.word	51509
	.word	48217
	.word	54262
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	37725
	.word	16384
	.word	0
	.type	_P,@object
	.size	_P,84
	.align 2
_ranges:
	.word	63385
	.word	64463
	.word	39556
	.word	39456
	.word	16395
	.word	0
	.word	55376
	.word	5815
	.word	21149
	.word	39608
	.word	16395
	.word	0
	.type	_ranges,@object
	.size	_ranges,24
	.align 2
_exp10but:
	.word	6424
	.word	10152
	.word	51584
	.word	39451
	.word	49163
	.word	0
	.type	_exp10but,@object
	.size	_exp10but,12
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
	.align 2
_exact_values:
	.word	0
	.word	0
	.word	0
	.word	40960
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	51200
	.word	16389
	.word	0
	.word	0
	.word	0
	.word	0
	.word	64000
	.word	16392
	.word	0
	.word	0
	.word	0
	.word	0
	.word	40000
	.word	16396
	.word	0
	.word	0
	.word	0
	.word	0
	.word	50000
	.word	16399
	.word	0
	.word	0
	.word	0
	.word	0
	.word	62500
	.word	16402
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	39062
	.word	16406
	.word	0
	.word	0
	.word	0
	.word	8192
	.word	48828
	.word	16409
	.word	0
	.word	0
	.word	0
	.word	10240
	.word	61035
	.word	16412
	.word	0
	.word	0
	.word	0
	.word	63744
	.word	38146
	.word	16416
	.word	0
	.word	0
	.word	0
	.word	46912
	.word	47683
	.word	16419
	.word	0
	.word	0
	.word	0
	.word	42256
	.word	59604
	.word	16422
	.word	0
	.word	0
	.word	0
	.word	59178
	.word	37252
	.word	16426
	.word	0
	.word	0
	.word	32768
	.word	8436
	.word	46566
	.word	16429
	.word	0
	.word	0
	.word	40960
	.word	43313
	.word	58207
	.word	16432
	.word	0
	.word	0
	.word	1024
	.word	51647
	.word	36379
	.word	16436
	.word	0
	.word	0
	.word	50432
	.word	48174
	.word	45474
	.word	16439
	.word	0
	.word	0
	.word	30272
	.word	27450
	.word	56843
	.word	16442
	.word	0
	.word	0
	.word	35304
	.word	8964
	.word	35527
	.word	16446
	.word	0
	.word	0
	.word	44130
	.word	60357
	.word	44408
	.word	16449
	.word	0
	.word	32768
	.word	6010
	.word	9911
	.word	55511
	.word	16452
	.word	0
	.word	36864
	.word	28332
	.word	30770
	.word	34694
	.word	16456
	.word	0
	.word	46080
	.word	2647
	.word	5695
	.word	43368
	.word	16459
	.word	0
	.word	41216
	.word	52461
	.word	7118
	.word	54210
	.word	16462
	.word	0
	.word	33952
	.word	16404
	.word	20833
	.word	33881
	.word	16466
	.word	0
	.word	42440
	.word	36889
	.word	42425
	.word	42351
	.word	16469
	.word	0
	.word	3898
	.word	62496
	.word	36647
	.word	52939
	.word	16472
	.word	0
	.type	_exact_values,@object
	.size	_exact_values,324
	.data
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End

	.globl pow10l
	.equ pow10l, exp10l

