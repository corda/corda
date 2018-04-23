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
	.file "ccoshl.c"
	.text
..TXTST0:
# -- Begin  ccoshl
	.text
       .align    16,0x90
	.globl ccoshl
ccoshl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $308, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        movl      %edx, 244(%esp)
        fstpt     112(%esp)
        fldt      112(%esp)
        fstpt     220(%esp)
        fldt      220(%esp)
        fstpt     232(%esp)
..B1.2:
        fnstcw    62(%esp)
..B1.3:
        movzwl    62(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.65
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 60(%esp)
..B1.5:
        fldcw     60(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.33
..B1.8:
        testl     %ecx, %ecx
        jle       ..B1.60
..B1.9:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
        movl      %edx, 12(%esp)
        cmpl      $32767, %edx
        jge       ..B1.33
..B1.10:
        cmpl      $0, 12(%esp)
        jle       ..B1.58
..B1.11:
        cmpl      $16398, %ecx
        jl        ..B1.19
..B1.12:
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fmul      %st(0), %st
        fldt      24(%ebp)
        fstpt     (%esp)
        fldt      (%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        fstpt     32(%esp)
        call      cosl@PLT
..B1.68:
        fldt      32(%esp)
        addl      $16, %esp
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     220(%esp)
        fldt      12(%ebp)
        fmulp     %st, %st(1)
        cmpl      $0, 12(%esp)
        jle       ..B1.14
..B1.13:
        addl      $-16, %esp
        fldt      16(%esp)
        fstpt     (%esp)
        fstpt     32(%esp)
        call      sinl@PLT
..B1.69:
        fldt      32(%esp)
        addl      $16, %esp
        fmulp     %st, %st(1)
        jmp       ..B1.15
..B1.14:
        fldt      (%esp)
        fmulp     %st, %st(1)
..B1.15:
        fstpt     232(%esp)
        testl     %edi, %edi
        je        ..B1.17
..B1.16:
        fldcw     62(%esp)
..B1.17:
        movl      220(%esp), %edx
        movl      %edx, (%esi)
        movl      232(%esp), %edx
        movl      %edx, 12(%esi)
        movl      236(%esp), %edx
        movl      %edx, 16(%esi)
        movl      224(%esp), %ecx
        movl      228(%esp), %edi
        movl      240(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      244(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.57
..B1.18:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.19:
        fldt      12(%ebp)
        addl      $-16, %esp
        lea       164(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        call      __libm_coshl_k80
..B1.70:
        addl      $16, %esp
..B1.20:
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        movzwl    20(%ebp), %ecx
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        andl      $32767, %ecx
        fldt      148(%esp)
        cmpl      $16183, %ecx
        fld       %st(0)
        fldt      160(%esp)
        movl      (%esp), %edx
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsub      %st, %st(3)
        movl      %edx, 28(%esp)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fsub      %st, %st(3)
        fsubp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        jge       ..B1.22
..B1.21:
        fstp      %st(2)
        fldt      12(%ebp)
        lea       184(%esp), %eax
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     -12(%eax)
        fldt      -72(%eax)
        movzwl    -4(%eax), %ecx
        movl      %ecx, %edx
        andl      $32767, %edx
        andl      $-32768, %ecx
        addl      $-16639, %edx
        orl       $-49153, %ecx
        fstpt     (%eax)
        movl      %edx, 44(%esp)
        movw      %cx, -4(%eax)
        jmp       ..B1.24
..B1.22:
        fldt      12(%ebp)
        addl      $-16, %esp
        lea       188(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        fxch      %st(2)
        fstpt     16(%esp)
        fstpt     -124(%edx)
        fstpt     48(%esp)
        call      __libm_sinhl_k80
..B1.71:
        fldt      48(%esp)
        fldt      64(%esp)
        fldt      16(%esp)
        addl      $16, %esp
..B1.23:
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%esp)
        lea       184(%esp), %eax
        movl      (%esp), %edx
        movl      %edx, 44(%esp)
        fxch      %st(1)
..B1.24:
        fldt      172(%esp)
        fld       %st(0)
        fld       %st(3)
        fldt      (%eax)
        fadd      %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fsubr     %st(1), %st
        fsubrp    %st, %st(1)
        fstpt     64(%esp)
        fldt      64(%esp)
        addl      $-16, %esp
        fxch      %st(2)
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      24(%ebp)
        fstpt     (%esp)
        lea       212(%esp), %edx
        movl      %edx, 12(%esp)
        fxch      %st(1)
        fstpt     64(%esp)
        fstpt     -100(%edx)
        fstpt     48(%esp)
        call      __libm_cosl_k80
..B1.72:
        fldt      48(%esp)
        fxch      %st(1)
        fstpt     96(%esp)
        fldt      112(%esp)
        fldt      64(%esp)
        addl      $16, %esp
..B1.25:
        fldt      196(%esp)
        fld       %st(0)
        fldt      208(%esp)
        movzwl    32(%ebp), %edx
        fadd      %st, %st(1)
        fld       %st(1)
        andl      $32767, %edx
        cmpl      $16183, %edx
        fxch      %st(2)
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fld       %st(5)
        fmul      %st(3), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      80(%esp)
        fmul      %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%esp)
        fldt      12+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(1)
        jge       ..B1.27
..B1.26:
        fldt      24(%ebp)
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     124(%esp)
        fldt      112(%esp)
        movzwl    132(%esp), %ecx
        movl      %ecx, %edx
        andl      $-32768, %ecx
        andl      $32767, %edx
        orl       $-49153, %ecx
        addl      $-16639, %edx
        movw      %cx, 132(%esp)
        fstpt     136(%esp)
        fldt      124(%esp)
        movl      %edx, 12(%esp)
        jmp       ..B1.29
..B1.27:
        fldt      24(%ebp)
        addl      $-16, %esp
        lea       140(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        fxch      %st(1)
        fstpt     -76(%edx)
        fstpt     16(%esp)
        fstpt     -28(%edx)
        fstpt     -92(%edx)
        call      __libm_sinl_k80
..B1.73:
        fldt      48(%esp)
        fldt      112(%esp)
        fldt      16(%esp)
        fldt      64(%esp)
        addl      $16, %esp
..B1.28:
        fldt      124(%esp)
        fmul      %st(5), %st
        fstpt     124(%esp)
        fldt      124(%esp)
        fldt      136(%esp)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fstpt     112(%esp)
        fldt      112(%esp)
        fstpt     136(%esp)
        movl      $0, 12(%esp)
        fxch      %st(2)
        fxch      %st(3)
        fxch      %st(4)
..B1.29:
        fldt      112(%esp)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(6)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fsubr     %st(6), %st
        fsubrp    %st, %st(6)
        fxch      %st(1)
        fsub      %st(5), %st
        faddp     %st, %st(1)
        fstpt     (%esp)
        fld       %st(1)
        fldt      16(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(4)
        fmulp     %st, %st(3)
        fldt      80(%esp)
        addl      $-32, %esp
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      60(%esp), %edx
        movl      %edx, 24(%esp)
        fstpt     128(%esp)
        fstpt     64(%esp)
        call      __libm_scalbl_k80
..B1.74:
        fldt      64(%esp)
        fldt      128(%esp)
        addl      $32, %esp
..B1.30:
        fldt      64(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(2)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(4)
        fstpt     220(%esp)
        fldt      (%esp)
        addl      $-32, %esp
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      76(%esp), %edx
        addl      44(%esp), %edx
        movl      %edx, 24(%esp)
        call      __libm_scalbl_k80
..B1.75:
        addl      $32, %esp
..B1.31:
        fstpt     232(%esp)
        jmp       ..B1.53
..B1.33:
        movl      28(%ebp), %edx
..B1.34:
        testl     %edx, %edx
        jne       ..B1.40
..B1.85:
        cmpl      $0, 24(%ebp)
..B1.35:
        jne       ..B1.40
..B1.36:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %ecx, 16(%esp)
        call      coshl@PLT
..B1.76:
        fldt      24(%ebp)
        movl      16(%esp), %ecx
        addl      $16, %esp
        fstpt     232(%esp)
        cmpl      $32767, %ecx
        fstpt     220(%esp)
        jl        ..B1.39
..B1.37:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.53
..B1.38:
        cmpl      $0, 12(%ebp)
        jne       ..B1.53
..B1.39:
        movzbl    241(%esp), %eax
        movl      %eax, %edx
        movzbl    21(%ebp), %ecx
        andl      $127, %edx
        shrl      $7, %eax
        shrl      $7, %ecx
        xorl      %ecx, %eax
        shll      $7, %eax
        orl       %eax, %edx
        movb      %dl, 241(%esp)
        jmp       ..B1.53
..B1.40:
        movzwl    32(%ebp), %eax
        andl      $32767, %eax
        movl      %eax, 12(%esp)
        cmpl      $32767, %eax
        jge       ..B1.43
..B1.41:
        fldt      12(%ebp)
        fstpt     32(%esp)
        fldt      32(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      coshl@PLT
..B1.78:
        fstpt     16(%esp)
        addl      $16, %esp
        fldt      24(%ebp)
        fstpt     16(%esp)
        fldt      16(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      cosl@PLT
..B1.77:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     220(%esp)
        cmpl      $0, 12(%esp)
        jle       ..B1.63
..B1.42:
        addl      $-16, %esp
        fldt      48(%esp)
        fstpt     (%esp)
        call      sinhl@PLT
..B1.80:
        fstpt     16(%esp)
        fldt      32(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B1.79:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     232(%esp)
        jmp       ..B1.53
..B1.43:
        cmpl      $32767, %ecx
        jge       ..B1.45
..B1.44:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %edx, 16(%esp)
        call      cosl@PLT
        jmp       ..B1.82
..B1.45:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        movl      %edx, 16(%esp)
        call      coshl@PLT
..B1.82:
        movl      16(%esp), %edx
        addl      $16, %esp
        fstpt     220(%esp)
..B1.46:
        cmpl      $0, 16(%ebp)
        jne       ..B1.48
..B1.47:
        cmpl      $0, 12(%ebp)
        je        ..B1.49
..B1.48:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.83:
        addl      $16, %esp
        fstpt     232(%esp)
        jmp       ..B1.53
..B1.49:
        cmpl      $-2147483648, %edx
        jne       ..B1.52
..B1.50:
        cmpl      $0, 24(%ebp)
        jne       ..B1.52
..B1.51:
        movzbl    21(%ebp), %ecx
        fldz      
        movzbl    33(%ebp), %edx
        andl      $128, %ecx
        andl      $128, %edx
        shrl      $7, %ecx
        shrl      $7, %edx
        xorl      %edx, %ecx
        fmull     ones@GOTOFF(%ebx,%ecx,8)
        fstpt     232(%esp)
        jmp       ..B1.53
..B1.52:
        fldt      112(%esp)
        fstpt     232(%esp)
..B1.53:
        testl     %edi, %edi
        je        ..B1.55
..B1.54:
        fldcw     62(%esp)
..B1.55:
        movl      220(%esp), %edx
        movl      %edx, (%esi)
        movl      232(%esp), %edx
        movl      %edx, 12(%esi)
        movl      236(%esp), %edx
        movl      %edx, 16(%esi)
        movl      224(%esp), %ecx
        movl      228(%esp), %edi
        movl      240(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      244(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.57
..B1.56:
        movl      %esi, %eax
        addl      $308, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.57:
        call      __stack_chk_fail@PLT
..B1.58:
        movl      28(%ebp), %edx
        testl     %edx, %edx
        jne       ..B1.11
..B1.59:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
        jmp       ..B1.35
..B1.60:
        cmpl      $0, 16(%ebp)
        jne       ..B1.9
..B1.61:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
        jmp       ..B1.33
..B1.63:
        addl      $-16, %esp
        fldt      48(%esp)
        fstpt     (%esp)
        call      sinhl@PLT
..B1.84:
        addl      $16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fstpt     232(%esp)
        jmp       ..B1.53
..B1.65:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	ccoshl,@function
	.size	ccoshl,.-ccoshl
	.data
# -- End  ccoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16639
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,48
	.align 16
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.data
	.hidden __libm_sinl_k80
	.hidden __libm_cosl_k80
	.hidden __libm_sinhl_k80
	.hidden __libm_coshl_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
