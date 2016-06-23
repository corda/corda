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
	.file "casinhf.c"
	.text
..TXTST0:
# -- Begin  casinhf
	.text
       .align    16,0x90
	.globl casinhf
casinhf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $120, %esp
        fldz      
        movzwl    10(%ebp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %eax
        fsts      104(%esp)
        fsts      108(%esp)
        jge       ..B1.28
..B1.2:
        testl     %eax, %eax
        jle       ..B1.57
..B1.3:
        movzwl    14(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        cmpl      $255, %edx
        jge       ..B1.29
..B1.4:
        testl     %edx, %edx
        jle       ..B1.56
..B1.5:
        flds      12(%ebp)
        fsts      100(%esp)
        fld1      
        fsts      92(%esp)
        fxch      %st(1)
        fabs      
        fld       %st(0)
        fsub      %st(2), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(2)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstl      48(%esp)
        fmul      %st(0), %st
        fxch      %st(1)
        fstl      72(%esp)
        flds      8(%ebp)
        fsts      96(%esp)
        fabs      
        fld       %st(0)
        fmul      %st(1), %st
        fstl      56(%esp)
        fadd      %st, %st(5)
        fxch      %st(5)
        fsqrt     
        fstl      64(%esp)
        fxch      %st(5)
        faddp     %st, %st(3)
        fxch      %st(2)
        fsqrt     
        fld       %st(0)
        fadd      %st(5), %st
        fxch      %st(5)
        fucomp    %st(2)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        fldl      .L_2il0floatpacket.6@GOTOFF(%ebx)
        fmul      %st, %st(4)
        sahf      
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
        fmul      %st(5), %st
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jb        ..B1.10
..B1.9:
        fstp      %st(2)
        fld       %st(2)
        fdiv      %st(4), %st
        fstpl     (%esp)
        fxch      %st(2)
        fstps     80(%esp)
        fxch      %st(1)
        fstpl     16(%esp)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        call      asin@PLT
        jmp       ..B1.85
..B1.10:
        flds      92(%esp)
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jb        ..B1.12
..B1.11:
        fstp      %st(2)
        fldl      48(%esp)
        fadd      %st(1), %st
        fld       %st(4)
        fadd      %st(4), %st
        fmul      %st(3), %st
        fxch      %st(1)
        fdivrl    56(%esp)
        faddl     40(%esp)
        fmulp     %st, %st(1)
        fsqrt     
        fstpl     8(%esp)
        fxch      %st(2)
        fstl      (%esp)
        fstps     80(%esp)
        fxch      %st(1)
        fstpl     16(%esp)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        call      atan2@PLT
        jmp       ..B1.85
..B1.12:
        fldl      48(%esp)
        fadd      %st(2), %st
        fxch      %st(2)
        fstpl     16(%esp)
        fld       %st(4)
        fadd      %st(4), %st
        fdiv      %st, %st(2)
        fldl      72(%esp)
        faddl     64(%esp)
        fdivrp    %st, %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpl     8(%esp)
        fxch      %st(1)
        fstl      (%esp)
        fstps     80(%esp)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstps     84(%esp)
        fldl      16(%esp)
        fstpl     16(%esp)
        call      atan2@PLT
..B1.85:
        flds      84(%esp)
        fxch      %st(1)
        fstps     108(%esp)
        fldl      32(%esp)
        fldl      24(%esp)
        fldl      16(%esp)
        flds      80(%esp)
..B1.13:
        fldl      _Across@GOTOFF(%ebx)
        fcomp     %st(4)
        fnstsw    %ax
        sahf      
        jb        ..B1.18
..B1.14:
        flds      92(%esp)
        fcompp    
        fnstsw    %ax
        faddl     48(%esp)
        fdivrl    56(%esp)
        sahf      
        jbe       ..B1.16
..B1.15:
        fldl      56(%esp)
        fdivl     40(%esp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        jmp       ..B1.17
..B1.16:
        fldl      72(%esp)
        faddl     64(%esp)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.17:
        fxch      %st(1)
        fadds     92(%esp)
        fmul      %st(1), %st
        fsqrt     
        faddp     %st, %st(1)
        fstpl     (%esp)
        fstps     84(%esp)
        call      log1p@PLT
        jmp       ..B1.87
..B1.18:
        fstp      %st(1)
        fstp      %st(1)
        fstp      %st(0)
        fld       %st(0)
        fmul      %st(1), %st
        fsubs     92(%esp)
        fsqrt     
        faddp     %st, %st(1)
        fstpl     (%esp)
        fstps     84(%esp)
        call      log@PLT
..B1.87:
        flds      84(%esp)
        fxch      %st(1)
        fstps     104(%esp)
..B1.19:
        fcoms     100(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.21
..B1.20:
        movzbl    111(%esp), %edi
        movl      %edi, %eax
        shrl      $7, %edi
        andl      $127, %eax
        notl      %edi
        shll      $7, %edi
        orl       %edi, %eax
        movb      %al, 111(%esp)
..B1.21:
        fcomps    96(%esp)
        fnstsw    %ax
        sahf      
        jbe       ..B1.23
..B1.22:
        movzbl    107(%esp), %edi
        movl      %edi, %eax
        shrl      $7, %edi
        andl      $127, %eax
        notl      %edi
        shll      $7, %edi
        orl       %edi, %eax
        movb      %al, 107(%esp)
..B1.23:
        movzwl    106(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.25
..B1.24:
        testl     $8388607, 104(%esp)
        jne       ..B1.27
..B1.25:
        movzwl    110(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.55
..B1.26:
        testl     $8388607, 108(%esp)
        je        ..B1.55
..B1.27:
        flds      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     88(%esp)
        jmp       ..B1.55
..B1.28:
        movzwl    14(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
..B1.29:
        testl     %edx, %edx
        jne       ..B1.32
..B1.30:
        testl     $8388607, 12(%ebp)
        jne       ..B1.32
..B1.31:
        fstp      %st(0)
        addl      $4, %esp
        pushl     8(%ebp)
        call      asinhf@PLT
..B1.88:
        movl      12(%ebp), %eax
        fstps     104(%esp)
        movl      %eax, 108(%esp)
        jmp       ..B1.55
..B1.32:
        testl     %eax, %eax
        jne       ..B1.43
..B1.33:
        testl     $8388607, 8(%ebp)
        jne       ..B1.44
..B1.34:
        cmpl      $255, %edx
        jl        ..B1.36
..B1.35:
        testl     $8388607, 12(%ebp)
        jne       ..B1.44
..B1.36:
        cmpl      $127, %edx
        jl        ..B1.41
..B1.37:
        fstp      %st(0)
        flds      12(%ebp)
        movzbl    15(%ebp), %edi
        andl      $128, %edi
        shrl      $7, %edi
        fmuls     ones@GOTOFF(%ebx,%edi,4)
        fstps     (%esp)
        call      acoshf@PLT
..B1.89:
        fstps     104(%esp)
        testl     %edi, %edi
        je        ..B1.39
..B1.38:
        flds      .L_2il0floatpacket.7@GOTOFF(%ebx)
        jmp       ..B1.40
..B1.39:
        flds      .L_2il0floatpacket.8@GOTOFF(%ebx)
..B1.40:
        flds      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fadd      %st(1), %st
        fstps     88(%esp)
        fstps     108(%esp)
        flds      104(%esp)
        jmp       ..B1.42
..B1.41:
        movl      12(%ebp), %eax
        movl      %eax, (%esp)
        fstps     84(%esp)
        call      asinf@PLT
..B1.90:
        flds      84(%esp)
        fxch      %st(1)
        fstps     108(%esp)
..B1.42:
        movzbl    11(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fmuls     ones@GOTOFF(%ebx,%eax,4)
        fstps     104(%esp)
        jmp       ..B1.55
..B1.43:
        cmpl      $255, %eax
        je        ..B1.74
..B1.44:
        cmpl      $255, %edx
        je        ..B1.71
..B1.93:
        cmpl      $255, %eax
..B1.45:
        je        ..B1.59
..B1.96:
        fstp      %st(0)
..B1.46:
        jge       ..B1.54
..B1.47:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.49
..B1.48:
        flds      .L_2il0floatpacket.7@GOTOFF(%ebx)
        jmp       ..B1.50
..B1.49:
        flds      .L_2il0floatpacket.8@GOTOFF(%ebx)
..B1.50:
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        fsts      108(%esp)
        je        ..B1.52
..B1.51:
        movl      $-8388608, %eax
        jmp       ..B1.53
..B1.52:
        movl      $2139095040, %eax
..B1.53:
        movl      %eax, 104(%esp)
        movl      8(%ebp), %eax
        fadds     .L_2il0floatpacket.9@GOTOFF(%ebx)
        fstps     88(%esp)
        movl      %eax, 88(%esp)
        jmp       ..B1.55
..B1.54:
        flds      8(%ebp)
        fmul      %st(0), %st
        movl      12(%ebp), %eax
        movl      %eax, 104(%esp)
        fstps     108(%esp)
..B1.55:
        movl      104(%esp), %eax
        movl      108(%esp), %edx
        addl      $120, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.56:
        testl     $8388607, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.29
..B1.57:
        testl     $8388607, 8(%ebp)
        jne       ..B1.3
        jmp       ..B1.28
..B1.59:
        testl     $8388607, 8(%ebp)
        jne       ..B1.92
..B1.60:
        movl      8(%ebp), %eax
        cmpl      $255, %edx
        movl      %eax, 104(%esp)
        jge       ..B1.65
..B1.61:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.64
..B1.62:
        fstp      %st(0)
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
..B1.64:
        movl      12(%ebp), %eax
        fstps     108(%esp)
        movl      %eax, 88(%esp)
        jmp       ..B1.55
..B1.65:
        fstp      %st(0)
        testl     $8388607, 12(%ebp)
        jne       ..B1.70
..B1.66:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.68
..B1.67:
        flds      .L_2il0floatpacket.11@GOTOFF(%ebx)
        jmp       ..B1.69
..B1.68:
        flds      .L_2il0floatpacket.12@GOTOFF(%ebx)
..B1.69:
        flds      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fadd      %st(1), %st
        fstps     88(%esp)
        fstps     108(%esp)
        jmp       ..B1.55
..B1.70:
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     108(%esp)
        jmp       ..B1.55
..B1.71:
        testl     $8388607, 12(%ebp)
        je        ..B1.91
..B1.72:
        cmpl      $255, %eax
        jge       ..B1.45
..B1.73:
        fstp      %st(0)
        flds      12(%ebp)
        fmul      %st(0), %st
        fsts      104(%esp)
        fstps     108(%esp)
        jmp       ..B1.55
..B1.74:
        testl     $8388607, 8(%ebp)
        je        ..B1.76
..B1.75:
        cmpl      $255, %edx
        je        ..B1.79
..B1.95:
        testl     $8388607, 8(%ebp)
..B1.76:
        je        ..B1.44
..B1.77:
        cmpl      $255, %edx
        jge       ..B1.44
..B1.78:
        fstp      %st(0)
        flds      8(%ebp)
        fmul      %st(0), %st
        fsts      104(%esp)
        fstps     108(%esp)
        jmp       ..B1.55
..B1.79:
        testl     $8388607, 12(%ebp)
        je        ..B1.94
..B1.80:
        fstp      %st(0)
        flds      8(%ebp)
        fmul      %st(0), %st
        fstps     104(%esp)
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     108(%esp)
        jmp       ..B1.55
..B1.91:
        cmpl      $255, %eax
        je        ..B1.59
        jmp       ..B1.96
..B1.92:
        fstp      %st(0)
        cmpl      $255, %eax
        jl        ..B1.47
        jmp       ..B1.54
..B1.94:
        testl     $8388607, 8(%ebp)
        jne       ..B1.77
        jmp       ..B1.44
        .align    16,0x90
	.type	casinhf,@function
	.size	casinhf,.-casinhf
	.data
# -- End  casinhf
	.section .rodata, "a"
	.align 16
	.align 16
_Bcross:
	.long	-858993459
	.long	1072483532
	.type	_Bcross,@object
	.size	_Bcross,8
	.space 8, 0x00 	# pad
	.align 16
_Across:
	.long	0
	.long	1073217536
	.type	_Across,@object
	.size	_Across,8
	.space 8, 0x00 	# pad
	.align 16
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 8
.L_2il0floatpacket.6:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 4
.L_2il0floatpacket.7:
	.long	0xbfc90fdb
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,4
	.align 4
.L_2il0floatpacket.8:
	.long	0x3fc90fdb
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,4
	.align 4
.L_2il0floatpacket.9:
	.long	0x00800000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,4
	.align 4
.L_2il0floatpacket.10:
	.long	0x80000000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
	.align 4
.L_2il0floatpacket.11:
	.long	0xbf490fdb
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,4
	.align 4
.L_2il0floatpacket.12:
	.long	0x3f490fdb
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.align 4
.L_2il0floatpacket.13:
	.long	0xff800000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,4
	.align 4
.L_2il0floatpacket.14:
	.long	0x7f800000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,4
	.data
	.section .note.GNU-stack, ""
# End
