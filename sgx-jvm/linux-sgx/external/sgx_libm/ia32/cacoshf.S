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
	.file "cacoshf.c"
	.text
..TXTST0:
# -- Begin  cacoshf
	.text
       .align    16,0x90
	.globl cacoshf
cacoshf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %ebx
        subl      $108, %esp
        fldz      
        movzwl    10(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %edx
        fsts      100(%esp)
        fsts      104(%esp)
        jge       ..B1.26
..B1.2:
        testl     %edx, %edx
        jle       ..B1.55
..B1.3:
        movzwl    14(%ebp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        cmpl      $255, %eax
        jge       ..B1.27
..B1.4:
        testl     %eax, %eax
        jle       ..B1.54
..B1.5:
        flds      8(%ebp)
        fsts      92(%esp)
        fld1      
        fsts      88(%esp)
        fxch      %st(1)
        fabs      
        fld       %st(0)
        fsub      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(2)
        faddp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fxch      %st(2)
        fstl      72(%esp)
        flds      12(%ebp)
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fstl      56(%esp)
        fadd      %st, %st(3)
        fxch      %st(3)
        fsqrt     
        fstl      64(%esp)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(3)
        fsqrt     
        fstl      48(%esp)
        fxch      %st(1)
        fucomp    %st(2)
        fnstsw    %ax
        faddp     %st, %st(1)
        fldl      .L_2il0floatpacket.6@GOTOFF(%ebx)
        sahf      
        fmul      %st, %st(1)
        jp        ..B1.6
        je        ..B1.7
..B1.6:
        fldl      64(%esp)
        fsubl     72(%esp)
        fstpl     40(%esp)
        jmp       ..B1.8
..B1.7:
        fxch      %st(2)
        fstl      40(%esp)
        fxch      %st(2)
..B1.8:
        fldl      _Bcross@GOTOFF(%ebx)
        fmul      %st(2), %st
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jbe       ..B1.10
..B1.9:
        fstp      %st(2)
        fld       %st(2)
        fdiv      %st(1), %st
        fstpl     (%esp)
        fxch      %st(2)
        fstps     80(%esp)
        fxch      %st(2)
        fstpl     16(%esp)
        fxch      %st(1)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        call      acos@PLT
        jmp       ..B1.89
..B1.10:
        flds      88(%esp)
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jb        ..B1.12
..B1.11:
        fstp      %st(2)
        fld       %st(3)
        fld       %st(1)
        fadd      %st(4), %st
        fmul      %st(3), %st
        fxch      %st(1)
        faddl     48(%esp)
        fdivrl    56(%esp)
        faddl     40(%esp)
        fmulp     %st, %st(1)
        fsqrt     
        fxch      %st(3)
        fstl      8(%esp)
        fxch      %st(3)
        fstpl     (%esp)
        fxch      %st(2)
        fstps     80(%esp)
        fxch      %st(2)
        fstpl     16(%esp)
        fxch      %st(1)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        call      atan2@PLT
        jmp       ..B1.89
..B1.12:
        fldl      72(%esp)
        fld       %st(2)
        fadd      %st(5), %st
        fxch      %st(6)
        fstl      16(%esp)
        fxch      %st(1)
        faddl     64(%esp)
        fxch      %st(1)
        faddl     48(%esp)
        fdivr     %st(6), %st
        fxch      %st(1)
        fdivrp    %st, %st(6)
        faddp     %st, %st(5)
        fmul      %st, %st(4)
        fxch      %st(4)
        fsqrt     
        fmulp     %st, %st(2)
        fxch      %st(2)
        fstl      8(%esp)
        fxch      %st(1)
        fstpl     (%esp)
        fstps     80(%esp)
        fxch      %st(1)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        fldl      16(%esp)
        fstpl     16(%esp)
        call      atan2@PLT
..B1.89:
        flds      84(%esp)
        fldl      32(%esp)
        fldl      24(%esp)
        fldl      16(%esp)
        flds      80(%esp)
..B1.13:
        fxch      %st(4)
        fcomps    92(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.15
..B1.14:
        fxch      %st(4)
        fsubrl    _DBL_PPI@GOTOFF(%ebx)
        fxch      %st(4)
..B1.15:
        fxch      %st(4)
        fstps     104(%esp)
        fldl      _Across@GOTOFF(%ebx)
        movzbl    107(%esp), %ecx
        fcomp     %st(2)
        fnstsw    %ax
        movzbl    15(%ebp), %edx
        andl      $127, %ecx
        andl      $-128, %edx
        orl       %edx, %ecx
        movb      %cl, 107(%esp)
        sahf      
        jb        ..B1.20
..B1.16:
        flds      88(%esp)
        fcomp     %st(3)
        fnstsw    %ax
        fxch      %st(2)
        fstp      %st(0)
        fxch      %st(2)
        faddl     48(%esp)
        fdivrl    56(%esp)
        sahf      
        jbe       ..B1.18
..B1.17:
        fldl      56(%esp)
        fdivl     40(%esp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        jmp       ..B1.19
..B1.18:
        fldl      72(%esp)
        faddl     64(%esp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.19:
        fxch      %st(1)
        fadds     88(%esp)
        fmul      %st(1), %st
        fsqrt     
        faddp     %st, %st(1)
        fstpl     (%esp)
        call      log1p@PLT
        jmp       ..B1.91
..B1.20:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(0)
        fld       %st(0)
        fmul      %st(1), %st
        fsubs     88(%esp)
        fsqrt     
        faddp     %st, %st(1)
        fstpl     (%esp)
        call      log@PLT
..B1.91:
        fstps     100(%esp)
..B1.21:
        movzwl    102(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.23
..B1.22:
        testl     $8388607, 100(%esp)
        jne       ..B1.25
..B1.23:
        movzwl    106(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.53
..B1.24:
        testl     $8388607, 104(%esp)
        je        ..B1.53
..B1.25:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     96(%esp)
        jmp       ..B1.53
..B1.26:
        movzwl    14(%ebp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
..B1.27:
        cmpl      $255, %edx
        je        ..B1.83
..B1.28:
        testl     %eax, %eax
        jne       ..B1.38
..B1.29:
        testl     $8388607, 12(%ebp)
        jne       ..B1.38
..B1.30:
        cmpl      $255, %edx
        jl        ..B1.32
..B1.31:
        testl     $8388607, 8(%ebp)
        jne       ..B1.38
..B1.32:
        cmpl      $127, %edx
        jl        ..B1.36
..B1.33:
        flds      8(%ebp)
        fabs      
        fstps     (%esp)
        fstps     84(%esp)
        call      acoshf@PLT
..B1.92:
        flds      84(%esp)
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        fxch      %st(1)
        fstps     100(%esp)
        je        ..B1.35
..B1.34:
        fstp      %st(0)
        movl      $1078530011, %eax
        movl      %eax, 104(%esp)
        movl      %eax, 96(%esp)
        jmp       ..B1.37
..B1.35:
        fstps     104(%esp)
        jmp       ..B1.37
..B1.36:
        movl      8(%ebp), %eax
        fstps     100(%esp)
        movl      %eax, (%esp)
        call      acosf@PLT
..B1.93:
        fstps     104(%esp)
..B1.37:
        movzbl    107(%esp), %edx
        movzbl    15(%ebp), %eax
        andl      $127, %edx
        andl      $-128, %eax
        orl       %eax, %edx
        movb      %dl, 107(%esp)
        jmp       ..B1.53
..B1.38:
        testl     %edx, %edx
        jne       ..B1.43
..B1.39:
        testl     $8388607, 8(%ebp)
        jne       ..B1.44
..B1.40:
        cmpl      $255, %eax
        jl        ..B1.42
..B1.41:
        testl     $8388607, 12(%ebp)
        jne       ..B1.44
..B1.42:
        fstp      %st(0)
        flds      12(%ebp)
        fsts      16(%esp)
        fabs      
        fstps     (%esp)
        call      asinhf@PLT
..B1.95:
        movl      8(%ebp), %eax
        movl      %eax, 4(%esp)
        flds      16(%esp)
        fstps     (%esp)
        fstps     100(%esp)
        call      atan2f@PLT
..B1.94:
        fstps     104(%esp)
        jmp       ..B1.53
..B1.43:
        cmpl      $255, %edx
        je        ..B1.76
..B1.44:
        cmpl      $255, %eax
        je        ..B1.73
..B1.45:
        cmpl      $255, %edx
        je        ..B1.57
..B1.46:
        fstp      %st(0)
        cmpl      $255, %edx
        jge       ..B1.51
..B1.47:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.49
..B1.48:
        flds      .L_2il0floatpacket.8@GOTOFF(%ebx)
        jmp       ..B1.50
..B1.49:
        flds      .L_2il0floatpacket.9@GOTOFF(%ebx)
..B1.50:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        movl      8(%ebp), %eax
        fadd      %st(1), %st
        fstps     96(%esp)
        fstps     104(%esp)
        movl      %eax, 96(%esp)
        jmp       ..B1.52
..B1.51:
        movl      8(%ebp), %eax
        movl      %eax, 104(%esp)
..B1.52:
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     100(%esp)
..B1.53:
        movl      100(%esp), %eax
        movl      104(%esp), %edx
        addl      $108, %esp
        popl      %ebx
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.54:
        testl     $8388607, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.27
..B1.55:
        testl     $8388607, 8(%ebp)
        jne       ..B1.3
        jmp       ..B1.26
..B1.57:
        testl     $8388607, 8(%ebp)
        jne       ..B1.46
..B1.58:
        flds      8(%ebp)
        cmpl      $255, %eax
        fmul      %st(0), %st
        fstps     100(%esp)
        jge       ..B1.65
..B1.59:
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        je        ..B1.61
..B1.60:
        fstp      %st(0)
        movl      $1078530011, %eax
        movl      %eax, 104(%esp)
        movl      %eax, 96(%esp)
        jmp       ..B1.62
..B1.61:
        fstps     104(%esp)
..B1.62:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.64
..B1.63:
        movzbl    107(%esp), %eax
        movl      %eax, %edx
        shrl      $7, %eax
        andl      $127, %edx
        notl      %eax
        shll      $7, %eax
        orl       %eax, %edx
        movb      %dl, 107(%esp)
..B1.64:
        movl      12(%ebp), %eax
        movl      %eax, 96(%esp)
        jmp       ..B1.53
..B1.65:
        fstp      %st(0)
        testl     $8388607, 12(%ebp)
        jne       ..B1.72
..B1.66:
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        je        ..B1.68
..B1.67:
        movl      $1075235812, %edx
        jmp       ..B1.69
..B1.68:
        movl      $1061752795, %edx
..B1.69:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        movl      %edx, 104(%esp)
        je        ..B1.71
..B1.70:
        shrl      $24, %edx
        orl       $-128, %edx
        movb      %dl, 107(%esp)
..B1.71:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fadds     104(%esp)
        fstps     96(%esp)
        jmp       ..B1.53
..B1.72:
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     104(%esp)
        jmp       ..B1.53
..B1.73:
        testl     $8388607, 12(%ebp)
        je        ..B1.45
..B1.74:
        cmpl      $255, %edx
        jge       ..B1.45
..B1.75:
        fstp      %st(0)
        flds      12(%ebp)
        fmul      %st(0), %st
        fsts      100(%esp)
        fstps     104(%esp)
        jmp       ..B1.53
..B1.76:
        testl     $8388607, 8(%ebp)
        je        ..B1.78
..B1.77:
        cmpl      $255, %eax
        je        ..B1.81
..B1.97:
        testl     $8388607, 8(%ebp)
..B1.78:
        je        ..B1.44
..B1.79:
        cmpl      $255, %eax
        jge       ..B1.44
..B1.80:
        fstp      %st(0)
        flds      8(%ebp)
        fmul      %st(0), %st
        fsts      100(%esp)
        fstps     104(%esp)
        jmp       ..B1.53
..B1.81:
        testl     $8388607, 12(%ebp)
        je        ..B1.96
..B1.82:
        fstp      %st(0)
        flds      8(%ebp)
        fmul      %st(0), %st
        fstps     100(%esp)
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     104(%esp)
        jmp       ..B1.53
..B1.83:
        testl     $8388607, 8(%ebp)
        je        ..B1.28
..B1.84:
        flds      8(%ebp)
        fadd      %st(0), %st
        fstps     8(%ebp)
        movzwl    10(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        jmp       ..B1.28
..B1.96:
        testl     $8388607, 8(%ebp)
        jne       ..B1.79
        jmp       ..B1.44
        .align    16,0x90
	.type	cacoshf,@function
	.size	cacoshf,.-cacoshf
	.data
# -- End  cacoshf
	.section .rodata, "a"
	.align 16
	.align 16
_Bcross:
	.long	1882913663
	.long	1071941838
	.type	_Bcross,@object
	.size	_Bcross,8
	.space 8, 0x00 	# pad
	.align 16
_DBL_PPI:
	.long	1413754136
	.long	1074340347
	.type	_DBL_PPI,@object
	.size	_DBL_PPI,8
	.space 8, 0x00 	# pad
	.align 16
_Across:
	.long	0
	.long	1073217536
	.type	_Across,@object
	.size	_Across,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 4
.L_2il0floatpacket.7:
	.long	0x40490fdb
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,4
	.align 4
.L_2il0floatpacket.8:
	.long	0xbfc90fdb
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,4
	.align 4
.L_2il0floatpacket.9:
	.long	0x3fc90fdb
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,4
	.align 4
.L_2il0floatpacket.10:
	.long	0x00800000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
	.align 4
.L_2il0floatpacket.11:
	.long	0x4016cbe4
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,4
	.align 4
.L_2il0floatpacket.12:
	.long	0x3f490fdb
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.data
	.section .note.GNU-stack, ""
# End
