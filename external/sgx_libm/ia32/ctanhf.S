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
	.file "ctanhf.c"
	.text
..TXTST0:
# -- Begin  ctanhf
	.text
       .align    16,0x90
	.globl ctanhf
ctanhf:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        fldz      
        fsts      16(%esp)
        fsts      36(%esp)
        fstps     40(%esp)
..B1.2:
        fnstcw    30(%esp)
..B1.3:
        movzwl    10(%ebp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $255, %eax
        jge       ..B1.28
..B1.4:
        testl     %eax, %eax
        jle       ..B1.46
..B1.5:
        movzwl    14(%ebp), %ecx
        andl      $32640, %ecx
        shrl      $7, %ecx
        cmpl      $255, %ecx
        jge       ..B1.29
..B1.6:
        cmpl      $133, %eax
        jl        ..B1.21
..B1.7:
        flds      .L_2il0floatpacket.6@GOTOFF(%ebx)
        movzbl    11(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        testl     %ecx, %ecx
        flds      ones@GOTOFF(%ebx,%eax,4)
        fsub      %st(1), %st
        fstps     36(%esp)
        jne       ..B1.13
..B1.8:
        testl     $8388607, 12(%ebp)
        jne       ..B1.12
..B1.9:
        fstp      %st(0)
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.10:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.11:
        movl      $-2147483648, 40(%esp)
        jmp       ..B1.39
..B1.12:
        flds      12(%ebp)
        jmp       ..B1.14
..B1.13:
        flds      12(%ebp)
        jne       ..B1.17
..B1.14:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        fadd      %st(0), %st
        fstps     32(%esp)
        jne       ..B1.19
..B1.15:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.20
..B1.17:
        addl      $-16, %esp
        fsts      (%esp)
        fstps     24(%esp)
        fstps     28(%esp)
        call      sinf@PLT
..B1.86:
        flds      28(%esp)
        flds      24(%esp)
        addl      $16, %esp
        fxch      %st(2)
        fstps     (%esp)
        addl      $-16, %esp
        fxch      %st(1)
        fstps     (%esp)
        fstps     28(%esp)
        call      cosf@PLT
..B1.85:
        flds      28(%esp)
        addl      $16, %esp
        flds      (%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstps     4(%esp)
        movzbl    7(%esp), %eax
        testl     $128, %eax
        jne       ..B1.19
..B1.18:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.20
..B1.19:
        movl      $-2147483648, 40(%esp)
..B1.20:
        fmul      %st(0), %st
        fstps     32(%esp)
        jmp       ..B1.39
..B1.21:
        movzwl    30(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.22:
        orl       $-64768, %edx
        movw      %dx, 28(%esp)
..B1.23:
        fldcw     28(%esp)
..B1.24:
        movl      $1, %esi
..B1.25:
        flds      8(%ebp)
        addl      $-16, %esp
        lea       16(%esp), %eax
        lea       24(%esp), %edx
        fstpl     (%esp)
        movl      %eax, 8(%esp)
        movl      %edx, 12(%esp)
        call      sinhcosh@PLT
..B1.87:
        addl      $16, %esp
..B1.26:
        flds      12(%ebp)
        fsts      24(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      sin@PLT
..B1.89:
        addl      $16, %esp
        fstpl     16(%esp)
        addl      $-16, %esp
        flds      40(%esp)
        fstpl     (%esp)
        call      cos@PLT
..B1.88:
        addl      $16, %esp
        fld       %st(0)
        testl     %esi, %esi
        fmul      %st(1), %st
        fxch      %st(1)
        fmull     16(%esp)
        fldl      (%esp)
        fld       %st(0)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fdiv      %st(2), %st
        fstps     40(%esp)
        fmull     8(%esp)
        fdivp     %st, %st(1)
        fstps     36(%esp)
        je        ..B1.39
..B1.27:
        fldcw     30(%esp)
        jmp       ..B1.39
..B1.28:
        movzwl    14(%ebp), %ecx
        andl      $32640, %ecx
        shrl      $7, %ecx
..B1.29:
        cmpl      $255, %eax
        je        ..B1.51
..B1.30:
        cmpl      $255, %ecx
        je        ..B1.48
..B1.31:
        testl     %eax, %eax
        jne       ..B1.38
..B1.32:
        testl     $8388607, 8(%ebp)
        jne       ..B1.38
..B1.33:
        testl     %ecx, %ecx
        jne       ..B1.36
..B1.34:
        testl     $8388607, 12(%ebp)
        jne       ..B1.36
..B1.35:
        movl      8(%ebp), %eax
        movl      12(%ebp), %edx
        movl      %eax, 36(%esp)
        movl      %edx, 40(%esp)
        jmp       ..B1.39
..B1.36:
        cmpl      $255, %ecx
        jge       ..B1.39
..B1.37:
        movl      8(%ebp), %eax
        movl      %eax, 36(%esp)
        addl      $-16, %esp
        movl      12(%ebp), %edx
        movl      %edx, (%esp)
        call      tanf@PLT
..B1.90:
        addl      $16, %esp
        fstps     40(%esp)
        jmp       ..B1.39
..B1.38:
        addl      $-16, %esp
        movl      8(%ebp), %eax
        movl      %eax, (%esp)
        call      tanhf@PLT
..B1.91:
        movl      12(%ebp), %eax
        addl      $16, %esp
        fstps     36(%esp)
        movl      %eax, 40(%esp)
..B1.39:
        movzwl    38(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.41
..B1.40:
        testl     $8388607, 36(%esp)
        jne       ..B1.43
..B1.41:
        movzwl    42(%esp), %eax
        testl     $32640, %eax
        jne       ..B1.44
..B1.42:
        testl     $8388607, 40(%esp)
        je        ..B1.44
..B1.43:
        flds      .L_2il0floatpacket.6@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstps     32(%esp)
..B1.44:
        movl      36(%esp), %eax
        movl      40(%esp), %edx
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        xorl      %esi, %esi
        jmp       ..B1.25
..B1.46:
        testl     $8388607, 8(%ebp)
        jne       ..B1.5
        jmp       ..B1.28
..B1.48:
        testl     $8388607, 12(%ebp)
        jne       ..B1.50
..B1.49:
        addl      $-16, %esp
        movl      12(%ebp), %eax
        movl      %eax, (%esp)
        call      tanf@PLT
..B1.92:
        addl      $16, %esp
        fsts      36(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.50:
        flds      12(%ebp)
        fmul      %st(0), %st
        fsts      36(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.51:
        testl     $8388607, 8(%ebp)
        jne       ..B1.67
..B1.52:
        movzbl    11(%ebp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        testl     %ecx, %ecx
        movl      ones@GOTOFF(%ebx,%eax,4), %edx
        movl      %edx, 36(%esp)
        jne       ..B1.57
..B1.53:
        testl     $8388607, 12(%ebp)
        jne       ..B1.58
..B1.54:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.55:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.57:
        cmpl      $255, %ecx
        je        ..B1.76
..B1.58:
        cmpl      $127, %ecx
        jge       ..B1.64
..B1.59:
        testl     %ecx, %ecx
        jne       ..B1.61
..B1.60:
        flds      12(%ebp)
        fadd      %st(0), %st
        fstps     32(%esp)
..B1.61:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.62:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.64:
        addl      $-16, %esp
        movl      12(%ebp), %esi
        movl      %esi, (%esp)
        call      sinf@PLT
..B1.94:
        addl      $16, %esp
        fstps     (%esp)
        addl      $-16, %esp
        movl      %esi, (%esp)
        call      cosf@PLT
..B1.93:
        addl      $16, %esp
        flds      (%esp)
        fmulp     %st, %st(1)
        fstps     4(%esp)
        movzbl    7(%esp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.65:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.67:
        je        ..B1.30
..B1.68:
        flds      8(%ebp)
        testl     %ecx, %ecx
        fmul      %st(0), %st
        fsts      36(%esp)
        jne       ..B1.74
..B1.69:
        testl     $8388607, 12(%ebp)
        jne       ..B1.75
..B1.70:
        fstp      %st(0)
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.71:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.74:
        cmpl      $255, %ecx
        je        ..B1.81
..B1.75:
        fstps     40(%esp)
        jmp       ..B1.39
..B1.76:
        testl     $8388607, 12(%ebp)
        jne       ..B1.80
..B1.77:
        movzbl    15(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.11
..B1.78:
        flds      16(%esp)
        fstps     40(%esp)
        jmp       ..B1.39
..B1.80:
        flds      16(%esp)
        fstps     40(%esp)
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     32(%esp)
        jmp       ..B1.39
..B1.81:
        testl     $8388607, 12(%ebp)
        je        ..B1.75
..B1.82:
        fstp      %st(0)
        flds      12(%ebp)
        fmul      %st(0), %st
        fstps     40(%esp)
        jmp       ..B1.39
        .align    16,0x90
	.type	ctanhf,@function
	.size	ctanhf,.-ctanhf
	.data
# -- End  ctanhf
	.section .rodata, "a"
	.align 16
	.align 16
ones:
	.long	0x3f800000
	.long	0xbf800000
	.type	ones,@object
	.size	ones,8
	.align 4
.L_2il0floatpacket.6:
	.long	0x00800000
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,4
	.align 4
.L_2il0floatpacket.7:
	.long	0x80000000
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,4
	.data
	.section .note.GNU-stack, ""
# End
