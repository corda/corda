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
	.file "catanhf.c"
	.text
..TXTST0:
# -- Begin  catanhf
	.text
       .align    16,0x90
	.globl catanhf
catanhf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %edi
        pushl     %ebx
        subl      $120, %esp
        xorl      %edi, %edi
        movzwl    10(%ebp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %eax
        movl      %edi, 108(%esp)
        movl      %edi, 112(%esp)
        jge       ..B1.11
..B1.2:
        testl     %eax, %eax
        jle       ..B1.53
..B1.3:
        movzwl    14(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
        cmpl      $255, %edx
        jge       ..B1.12
..B1.4:
        testl     %edx, %edx
        jle       ..B1.52
..B1.5:
        flds      8(%ebp)
        fsts      100(%esp)
        fld1      
        fsts      96(%esp)
        fxch      %st(1)
        fmul      %st(0), %st
        fstl      32(%esp)
        fsubrp    %st, %st(1)
        flds      12(%ebp)
        fld       %st(0)
        fmul      %st(1), %st
        fstl      72(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpl     40(%esp)
        fldl      40(%esp)
        fabs      
        fldl      .L_2il0floatpacket.6@GOTOFF(%ebx)
        fstl      80(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jbe       ..B1.7
..B1.6:
        flds      100(%esp)
        fadds     96(%esp)
        fstl      88(%esp)
        fstpl     8(%esp)
        fstl      (%esp)
        fstpl     16(%esp)
        call      atan2@PLT
..B1.74:
        fldl      16(%esp)
        fchs      
        fxch      %st(1)
        fstpl     48(%esp)
        flds      96(%esp)
        fsubs     100(%esp)
        fstl      8(%esp)
        fxch      %st(1)
        fstpl     (%esp)
        fstpl     24(%esp)
        call      atan2@PLT
..B1.73:
        fldl      24(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      48(%esp)
        fldl      56(%esp)
        fsubrp    %st, %st(1)
        fldl      .L_2il0floatpacket.7@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstps     112(%esp)
        fxch      %st(1)
        jmp       ..B1.8
..B1.7:
        fldl      40(%esp)
        fstpl     8(%esp)
        fldl      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpl     (%esp)
        call      atan2@PLT
..B1.75:
        fldl      .L_2il0floatpacket.7@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstps     112(%esp)
        flds      100(%esp)
        fld       %st(0)
        flds      96(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     88(%esp)
        fsubp     %st, %st(1)
..B1.8:
        fldl      88(%esp)
        fstpl     48(%esp)
        fchs      
        fldl      48(%esp)
        fldl      48(%esp)
        fmulp     %st, %st(1)
        fldl      72(%esp)
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpl     64(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fldl      56(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpl     40(%esp)
        fldl      64(%esp)
        fldl      40(%esp)
        fsubrp    %st, %st(1)
        fabs      
        fldl      40(%esp)
        fmull     80(%esp)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.10
        jp        ..B1.10
..B1.9:
        fstp      %st(0)
        fldl      64(%esp)
        fldl      40(%esp)
        fdivrp    %st, %st(1)
        fstpl     (%esp)
        call      log@PLT
..B1.76:
        fmull     .L_2il0floatpacket.9@GOTOFF(%ebx)
        fstps     108(%esp)
        jmp       ..B1.46
..B1.10:
        fldl      72(%esp)
        fldl      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmuls     100(%esp)
        fxch      %st(1)
        faddl     32(%esp)
        fadds     96(%esp)
        fstpl     40(%esp)
        fstpl     64(%esp)
        fldl      64(%esp)
        fldl      40(%esp)
        fdivrp    %st, %st(1)
        fstpl     (%esp)
        fstpl     16(%esp)
        call      atanh@PLT
..B1.77:
        fldl      16(%esp)
        fmulp     %st, %st(1)
        fstps     108(%esp)
        jmp       ..B1.46
..B1.11:
        movzwl    14(%ebp), %edx
        andl      $32640, %edx
        shrl      $7, %edx
..B1.12:
        cmpl      $255, %eax
        je        ..B1.62
..B1.13:
        cmpl      $255, %edx
        je        ..B1.57
..B1.14:
        cmpl      $255, %edx
        jge       ..B1.39
..B1.15:
        testl     %eax, %eax
        jle       ..B1.56
..B1.16:
        cmpl      $127, %eax
        jge       ..B1.20
..B1.17:
        addl      $4, %esp
        pushl     8(%ebp)
        call      atanhf@PLT
..B1.78:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        fstps     108(%esp)
        je        ..B1.19
..B1.18:
        movl      $-2147483648, 112(%esp)
        jmp       ..B1.46
..B1.19:
        movl      %edi, 112(%esp)
        jmp       ..B1.46
..B1.20:
        je        ..B1.55
..B1.21:
        cmpl      $255, %eax
        jge       ..B1.29
..B1.22:
        flds      8(%ebp)
        fld       %st(0)
        fld       %st(1)
        fld1      
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpl     48(%esp)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(1)
        fsubr     %st(2), %st
        fchs      
        flds      12(%ebp)
        fmul      %st(0), %st
        fldl      48(%esp)
        fldl      48(%esp)
        fmulp     %st, %st(1)
        fadd      %st(1), %st
        fstpl     64(%esp)
        fxch      %st(1)
        fstpl     56(%esp)
        fldl      56(%esp)
        fldl      56(%esp)
        fmulp     %st, %st(1)
        fadd      %st(1), %st
        fstpl     40(%esp)
        fldl      64(%esp)
        fldl      40(%esp)
        fsubrp    %st, %st(1)
        fabs      
        fldl      40(%esp)
        fmull     .L_2il0floatpacket.6@GOTOFF(%ebx)
        fcompp    
        fnstsw    %ax
        sahf      
        jae       ..B1.24
        jp        ..B1.24
..B1.23:
        fstp      %st(2)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        fldl      64(%esp)
        fldl      40(%esp)
        fdivrp    %st, %st(1)
        fstpl     (%esp)
        call      log@PLT
..B1.79:
        fmull     .L_2il0floatpacket.9@GOTOFF(%ebx)
        fstps     108(%esp)
        jmp       ..B1.25
..B1.24:
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpl     40(%esp)
        fldl      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpl     64(%esp)
        fldl      64(%esp)
        fldl      40(%esp)
        fdivrp    %st, %st(1)
        fstpl     (%esp)
        call      atanh@PLT
..B1.80:
        fmull     .L_2il0floatpacket.7@GOTOFF(%ebx)
        fstps     108(%esp)
..B1.25:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.27
..B1.26:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        jmp       ..B1.28
..B1.27:
        flds      .L_2il0floatpacket.11@GOTOFF(%ebx)
..B1.28:
        flds      .L_2il0floatpacket.14@GOTOFF(%ebx)
        fxch      %st(1)
        fsts      112(%esp)
        faddp     %st, %st(1)
        fstps     104(%esp)
        jmp       ..B1.46
..B1.29:
        testl     %edx, %edx
        jne       ..B1.32
..B1.30:
        testl     $8388607, 12(%ebp)
        je        ..B1.32
..B1.31:
        flds      .L_2il0floatpacket.12@GOTOFF(%ebx)
        fadds     12(%ebp)
        fstps     104(%esp)
..B1.32:
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        je        ..B1.34
..B1.33:
        movl      $-2147483648, 108(%esp)
        jmp       ..B1.35
..B1.34:
        movl      %edi, 108(%esp)
..B1.35:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        je        ..B1.37
..B1.36:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        jmp       ..B1.38
..B1.37:
        flds      .L_2il0floatpacket.11@GOTOFF(%ebx)
..B1.38:
        flds      .L_2il0floatpacket.14@GOTOFF(%ebx)
        fxch      %st(1)
        fsts      112(%esp)
        faddp     %st, %st(1)
        fstps     104(%esp)
        jmp       ..B1.46
..B1.39:
        testl     %eax, %eax
        jne       ..B1.42
..B1.40:
        testl     $8388607, 8(%ebp)
        je        ..B1.42
..B1.41:
        flds      .L_2il0floatpacket.12@GOTOFF(%ebx)
        fadds     8(%ebp)
        fstps     104(%esp)
..B1.42:
        movzbl    11(%ebp), %eax
        testl     $128, %eax
        je        ..B1.44
..B1.43:
        movl      $-2147483648, 108(%esp)
        jmp       ..B1.45
..B1.44:
        movl      %edi, 108(%esp)
..B1.45:
        movl      12(%ebp), %eax
        movl      %eax, (%esp)
        call      atanf@PLT
..B1.81:
        fstps     112(%esp)
..B1.46:
        movzwl    110(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.48
..B1.47:
        testl     $8388607, 108(%esp)
        jne       ..B1.50
..B1.48:
        movzwl    114(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.51
..B1.49:
        testl     $8388607, 112(%esp)
        je        ..B1.51
..B1.50:
        flds      .L_2il0floatpacket.14@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     104(%esp)
..B1.51:
        movl      108(%esp), %eax
        movl      112(%esp), %edx
        addl      $120, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.52:
        testl     $8388607, 12(%ebp)
        jne       ..B1.5
        jmp       ..B1.12
..B1.53:
        testl     $8388607, 8(%ebp)
        jne       ..B1.3
        jmp       ..B1.11
..B1.55:
        testl     $8388607, 8(%ebp)
        je        ..B1.17
        jmp       ..B1.21
..B1.56:
        testl     $8388607, 8(%ebp)
        jne       ..B1.16
        jmp       ..B1.39
..B1.57:
        testl     $8388607, 12(%ebp)
        je        ..B1.14
..B1.58:
        cmpl      $255, %eax
        jge       ..B1.14
..B1.59:
        testl     %eax, %eax
        jle       ..B1.61
..B1.60:
        flds      12(%ebp)
        fmul      %st(0), %st
        fsts      108(%esp)
        fstps     112(%esp)
        jmp       ..B1.46
..B1.61:
        testl     $8388607, 8(%ebp)
        jne       ..B1.60
        jmp       ..B1.14
..B1.62:
        testl     $8388607, 8(%ebp)
        je        ..B1.13
..B1.63:
        flds      8(%ebp)
        cmpl      $255, %edx
        fmul      %st(0), %st
        jge       ..B1.65
..B1.64:
        fsts      8(%ebp)
        fmul      %st(0), %st
        fsts      108(%esp)
        fstps     112(%esp)
        jmp       ..B1.46
..B1.65:
        testl     $8388607, 12(%ebp)
        jne       ..B1.70
..B1.66:
        fmul      %st(0), %st
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        fstps     8(%ebp)
        movl      %edi, 108(%esp)
        je        ..B1.68
..B1.67:
        flds      .L_2il0floatpacket.10@GOTOFF(%ebx)
        jmp       ..B1.69
..B1.68:
        flds      .L_2il0floatpacket.11@GOTOFF(%ebx)
..B1.69:
        flds      .L_2il0floatpacket.14@GOTOFF(%ebx)
        fxch      %st(1)
        fsts      112(%esp)
        faddp     %st, %st(1)
        fstps     104(%esp)
        jmp       ..B1.46
..B1.70:
        fsts      8(%ebp)
        fmul      %st(0), %st
        fstps     108(%esp)
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     112(%esp)
        jmp       ..B1.46
        .align    16,0x90
	.type	catanhf,@function
	.size	catanhf,.-catanhf
	.data
# -- End  catanhf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.6:
	.long	0xe0000000,0x3f9eb851
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x00000000,0x3fd00000
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 4
.L_2il0floatpacket.10:
	.long	0xbfc90fdb
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
	.align 4
.L_2il0floatpacket.11:
	.long	0x3fc90fdb
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,4
	.align 4
.L_2il0floatpacket.12:
	.long	0x3f000000
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,4
	.align 4
.L_2il0floatpacket.13:
	.long	0x80000000
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,4
	.align 4
.L_2il0floatpacket.14:
	.long	0x00800000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,4
	.data
	.section .note.GNU-stack, ""
# End
