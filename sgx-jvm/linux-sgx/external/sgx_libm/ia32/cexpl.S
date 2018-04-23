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
	.file "cexpl.c"
	.text
..TXTST0:
# -- Begin  cexpl
	.text
       .align    16,0x90
	.globl cexpl
cexpl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        movl      %edx, 228(%esp)
        fstpt     96(%esp)
        fldt      96(%esp)
        fstpt     204(%esp)
        fldt      204(%esp)
        fstpt     216(%esp)
..B1.2:
        fnstcw    46(%esp)
..B1.3:
        movzwl    46(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.61
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 44(%esp)
..B1.5:
        fldcw     44(%esp)
..B1.6:
        movl      $1, %edi
..B1.7:
        movzwl    20(%ebp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.31
..B1.8:
        testl     %edx, %edx
        jle       ..B1.54
..B1.9:
        movzwl    32(%ebp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        jge       ..B1.31
..B1.10:
        testl     %ecx, %ecx
        jle       ..B1.52
..B1.11:
        fldt      12(%ebp)
        lea       180(%esp), %ecx
        fstpt     (%ecx)
        fldt      -84(%ecx)
        fstpt     12(%ecx)
        addl      $-16, %esp
        lea       172(%esp), %edx
        movl      %edx, (%esp)
        movl      %ecx, 4(%esp)
        call      __libm_expl_k80
..B1.64:
        fstpt     96(%esp)
        addl      $16, %esp
..B1.12:
        movzwl    164(%esp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        je        ..B1.51
..B1.13:
        testl     %edx, %edx
        je        ..B1.22
..B1.14:
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        fldt      80(%esp)
        faddp     %st, %st(1)
        fstpt     (%esp)
        fldt      156(%esp)
        fld       %st(0)
        fldt      168(%esp)
        movl      (%esp), %edx
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsub      %st, %st(3)
        movl      %edx, 28(%esp)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fld       %st(2)
        fsub      %st(4), %st
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     32(%esp)
        fldt      32(%esp)
        addl      $-16, %esp
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fldt      24(%ebp)
        fstpt     (%esp)
        lea       148(%esp), %ecx
        movl      %ecx, 12(%esp)
        fstpt     -84(%ecx)
        fstpt     -68(%ecx)
        call      __libm_cosl_k80
..B1.65:
        fldt      80(%esp)
        fldt      64(%esp)
        addl      $16, %esp
..B1.15:
        fldt      132(%esp)
        fld       %st(0)
        fldt      144(%esp)
        movzwl    32(%ebp), %edx
        fadd      %st, %st(1)
        andl      $32767, %edx
        fxch      %st(1)
        fsub      %st, %st(2)
        cmpl      $16183, %edx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(2)
        fmul      %st(2), %st
        fld       %st(0)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st(4), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      12+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(1)
        jge       ..B1.17
..B1.16:
        fldt      24(%ebp)
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     108(%esp)
        fldt      96(%esp)
        movzwl    116(%esp), %ecx
        movl      %ecx, %edx
        andl      $-32768, %ecx
        andl      $32767, %edx
        orl       $-49153, %ecx
        addl      $-16639, %edx
        movw      %cx, 116(%esp)
        fstpt     120(%esp)
        fldt      108(%esp)
        movl      %edx, 12(%esp)
        jmp       ..B1.19
..B1.17:
        fldt      24(%ebp)
        addl      $-16, %esp
        lea       124(%esp), %edx
        fstpt     (%esp)
        movl      %edx, 12(%esp)
        fxch      %st(3)
        fstpt     16(%esp)
        fxch      %st(2)
        fstpt     -92(%edx)
        fxch      %st(1)
        fstpt     -60(%edx)
        fstpt     -44(%edx)
        call      __libm_sinl_k80
..B1.66:
        fldt      80(%esp)
        fldt      64(%esp)
        fldt      32(%esp)
        fldt      16(%esp)
        addl      $16, %esp
..B1.18:
        fldt      108(%esp)
        fmul      %st(5), %st
        fstpt     108(%esp)
        fldt      108(%esp)
        fldt      120(%esp)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fstpt     96(%esp)
        fldt      96(%esp)
        fstpt     120(%esp)
        movl      $0, 12(%esp)
        fxch      %st(4)
..B1.19:
        fldt      96(%esp)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(4)
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(3)
        fsub      %st(2), %st
        fsubrp    %st, %st(4)
        fxch      %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(1)
        fld       %st(3)
        fmul      %st(5), %st
        fldt      32(%esp)
        addl      $-32, %esp
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st, %st(5)
        fxch      %st(5)
        fstpt     (%esp)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        movl      60(%esp), %edx
        movl      %edx, 24(%esp)
        fxch      %st(1)
        fstpt     32(%esp)
        fstpt     80(%esp)
        fstpt     96(%esp)
        call      __libm_scalbl_k80
..B1.67:
        fldt      96(%esp)
        fldt      80(%esp)
        fldt      32(%esp)
        addl      $32, %esp
..B1.20:
        fxch      %st(3)
        fstpt     204(%esp)
        fld       %st(1)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      32(%esp)
        addl      $-32, %esp
        fld       %st(0)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%esp)
        movl      60(%esp), %edx
        addl      44(%esp), %edx
        movl      %edx, 24(%esp)
        call      __libm_scalbl_k80
..B1.68:
        addl      $32, %esp
..B1.21:
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.22:
        fldt      12+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpt     16(%esp)
..B1.23:
        fldt      16(%esp)
        fstpt     156(%esp)
        fldt      24(%ebp)
        fstpt     (%esp)
        fldt      (%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      cosl@PLT
..B1.69:
        addl      $16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(1)
        movzwl    32(%ebp), %edx
        testl     $32767, %edx
        fstpt     204(%esp)
        jle       ..B1.25
..B1.24:
        addl      $-16, %esp
        fldt      16(%esp)
        fstpt     (%esp)
        call      sinl@PLT
..B1.70:
        addl      $16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fstpt     216(%esp)
        jmp       ..B1.26
..B1.25:
        fldt      (%esp)
        fldt      16(%esp)
        fmulp     %st, %st(1)
        fstpt     216(%esp)
..B1.26:
        testl     %edi, %edi
        je        ..B1.28
..B1.27:
        fldt      80(%esp)
        fstpt     (%esp)
..B1.71:
        fldcw     46(%esp)
..B1.28:
        movl      204(%esp), %edx
        movl      %edx, (%esi)
        movl      216(%esp), %edx
        movl      %edx, 12(%esi)
        movl      220(%esp), %edx
        movl      %edx, 16(%esi)
        movl      208(%esp), %ecx
        movl      212(%esp), %edi
        movl      224(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      228(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.50
..B1.29:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.31:
        movl      28(%ebp), %ecx
..B1.32:
        testl     %ecx, %ecx
        jne       ..B1.35
..B1.79:
        cmpl      $0, 24(%ebp)
..B1.33:
        jne       ..B1.35
..B1.34:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.72:
        fldt      24(%ebp)
        addl      $16, %esp
        fstpt     216(%esp)
        fstpt     204(%esp)
        jmp       ..B1.46
..B1.35:
        movzwl    32(%ebp), %ecx
        andl      $32767, %ecx
        movl      %ecx, 12(%esp)
        cmpl      $32767, %ecx
        jge       ..B1.38
..B1.36:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.74:
        fldt      24(%ebp)
        fxch      %st(1)
        fstpt     16(%esp)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     32(%esp)
        call      cosl@PLT
..B1.73:
        fldt      32(%esp)
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     204(%esp)
        cmpl      $0, 12(%esp)
        jle       ..B1.57
..B1.37:
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.75:
        addl      $16, %esp
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.38:
        cmpl      $32767, %edx
        jge       ..B1.40
..B1.39:
        fldt      24(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.76:
        addl      $16, %esp
        fstpt     204(%esp)
        fldt      204(%esp)
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.40:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.42
..B1.41:
        cmpl      $0, 12(%ebp)
        je        ..B1.43
..B1.42:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     204(%esp)
        fldt      204(%esp)
        fldt      24(%ebp)
        fmulp     %st, %st(1)
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.43:
        movzbl    21(%ebp), %edx
        testl     $128, %edx
        je        ..B1.45
..B1.44:
        fldt      24(%ebp)
        fmul      %st(0), %st
        fstpt     24(%ebp)
        fldt      96(%esp)
        fstpt     204(%esp)
        fldt      204(%esp)
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.45:
        fldt      12(%ebp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      expl@PLT
..B1.78:
        fldt      24(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     204(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      sinl@PLT
..B1.77:
        addl      $16, %esp
        fstpt     216(%esp)
..B1.46:
        testl     %edi, %edi
        je        ..B1.48
..B1.47:
        fldcw     46(%esp)
..B1.48:
        movl      204(%esp), %edx
        movl      %edx, (%esi)
        movl      216(%esp), %edx
        movl      %edx, 12(%esi)
        movl      220(%esp), %edx
        movl      %edx, 16(%esi)
        movl      208(%esp), %ecx
        movl      212(%esp), %edi
        movl      224(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 20(%esi)
        movl      228(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.50
..B1.49:
        movl      %esi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.50:
        call      __stack_chk_fail@PLT
..B1.51:
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fmul      %st(0), %st
        fstpt     16(%esp)
        jmp       ..B1.23
..B1.52:
        cmpl      $0, 28(%ebp)
        jne       ..B1.11
..B1.53:
        cmpl      $0, 24(%ebp)
        jne       ..B1.11
        jmp       ..B1.33
..B1.54:
        cmpl      $0, 16(%ebp)
        jne       ..B1.9
..B1.55:
        cmpl      $0, 12(%ebp)
        jne       ..B1.9
        jmp       ..B1.31
..B1.57:
        cmpl      $0, 16(%ebp)
        jne       ..B1.59
..B1.58:
        cmpl      $0, 12(%ebp)
        je        ..B1.37
..B1.59:
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     216(%esp)
        jmp       ..B1.46
..B1.61:
        xorl      %edi, %edi
        jmp       ..B1.7
        .align    16,0x90
	.type	cexpl,@function
	.size	cexpl,.-cexpl
	.data
# -- End  cexpl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
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
	.data
	.hidden __libm_cosl_k80
	.hidden __libm_sinl_k80
	.hidden __libm_expl_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
