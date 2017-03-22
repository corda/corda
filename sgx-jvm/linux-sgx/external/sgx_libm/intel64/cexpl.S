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
# parameter 1: 320 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cexpl.1:
..L2:

        pushq     %r14
	.cfi_def_cfa_offset 16
	.cfi_offset 14, -16
        pushq     %r15
	.cfi_def_cfa_offset 24
	.cfi_offset 15, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        subq      $288, %rsp
	.cfi_def_cfa_offset 320
        xorb      %r15b, %r15b
        fldt      .L_2il0floatpacket.7(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 272(%rsp)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fstpt     256(%rsp)
..B1.2:
        fnstcw    98(%rsp)
..B1.3:
        movzwl    98(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 96(%rsp)
..B1.5:
        fldcw     96(%rsp)
..B1.6:
        movb      $1, %r15b
..B1.7:
        movzwl    328(%rsp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.31
..B1.8:
        testl     %edx, %edx
        jle       ..B1.54
..B1.9:
        movzwl    344(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.31
..B1.10:
        testl     %eax, %eax
        jle       ..B1.52
..B1.11:
        fldt      320(%rsp)
        lea       176(%rsp), %rdi
        fstpt     32(%rdi)
        lea       208(%rsp), %rsi
        fldt      -128(%rsi)
        fstpt     16(%rsi)
..___tag_value_cexpl.10:
        call      __libm_expl_k80@PLT
..___tag_value_cexpl.11:
..B1.62:
        fstpt     64(%rsp)
..B1.12:
        movzwl    184(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.51
..B1.13:
        testl     %eax, %eax
        je        ..B1.22
..B1.14:
        fldt      64(%rsp)
        lea       48+_CONSTANTS(%rip), %rax
        fldt      176(%rsp)
        fldt      192(%rsp)
        fldt      .L_2il0floatpacket.8(%rip)
        fldt      (%rax)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     (%rsp)
        fld       %st(1)
        movl      (%rsp), %r14d
        fadd      %st(1), %st
        fld       %st(0)
        fmulp     %st, %st(4)
        fld       %st(3)
        fsub      %st(1), %st
        fsubrp    %st, %st(4)
        fxch      %st(3)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubrp    %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      352(%rsp)
        fstpt     (%rsp)
        lea       160(%rsp), %rdi
        fstpt     -96(%rdi)
..___tag_value_cexpl.13:
        call      __libm_cosl_k80@PLT
..___tag_value_cexpl.14:
..B1.63:
        fldt      64(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
..B1.15:
        fldt      144(%rsp)
        lea       16+_CONSTANTS(%rip), %rax
        fld       %st(0)
        fldt      160(%rsp)
        movzwl    344(%rsp), %edx
        fadd      %st, %st(1)
        andl      $32767, %edx
        fxch      %st(1)
        fsub      %st, %st(2)
        cmpl      $16183, %edx
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.8(%rip)
        fldt      (%rax)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(0)
        fsub      %st(4), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(4), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        jge       ..B1.17
..B1.16:
        fldt      336(%rsp)
        lea       32+_CONSTANTS(%rip), %rbx
        fldt      80(%rsp)
        fstpt     128(%rsp)
        fldt      (%rbx)
        fmulp     %st, %st(1)
        fstpt     112(%rsp)
        movzwl    120(%rsp), %eax
        movl      %eax, %ebx
        andl      $-32768, %eax
        andl      $32767, %ebx
        orl       $-49153, %eax
        addl      $-16639, %ebx
        movw      %ax, 120(%rsp)
        fldt      112(%rsp)
        jmp       ..B1.19
..B1.17:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      352(%rsp)
        lea       128(%rsp), %rdi
        fstpt     (%rsp)
        fxch      %st(2)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     -96(%rdi)
        fstpt     -64(%rdi)
..___tag_value_cexpl.17:
        call      __libm_sinl_k80@PLT
..___tag_value_cexpl.18:
..B1.64:
        fldt      64(%rsp)
        fldt      32(%rsp)
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
..B1.18:
        fldt      112(%rsp)
        xorl      %ebx, %ebx
        fmul      %st(4), %st
        fstpt     112(%rsp)
        fldt      112(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(5)
        fxch      %st(4)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     128(%rsp)
        fxch      %st(3)
..B1.19:
        fldt      80(%rsp)
        fld       %st(1)
        movl      %r14d, %edi
        fadd      %st(1), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      32(%rsp)
        fldt      .L_2il0floatpacket.8(%rip)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 352
        fmul      %st(3), %st
        fld       %st(0)
        fsub      %st(4), %st
        fsubrp    %st, %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(5)
        fstpt     (%rsp)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     32(%rsp)
        fstpt     48(%rsp)
        fstpt     80(%rsp)
..___tag_value_cexpl.21:
        call      __libm_scalbl_k80@PLT
..___tag_value_cexpl.22:
..B1.65:
        fldt      80(%rsp)
        fldt      48(%rsp)
        fldt      32(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 320
..B1.20:
        fxch      %st(3)
        fstpt     240(%rsp)
        fld       %st(1)
        fmul      %st(3), %st
        addl      %ebx, %r14d
        fxch      %st(1)
        fmul      %st, %st(2)
        fldt      32(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 352
        fld       %st(0)
        movl      %r14d, %edi
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fmulp     %st, %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_cexpl.25:
        call      __libm_scalbl_k80@PLT
..___tag_value_cexpl.26:
..B1.66:
        addq      $32, %rsp
	.cfi_def_cfa_offset 320
..B1.21:
        fstpt     256(%rsp)
        jmp       ..B1.44
..B1.22:
        lea       16+_CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
..B1.23:
        fldt      16(%rsp)
        fstpt     176(%rsp)
        fldt      336(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fstpt     (%rsp)
        call      cosl@PLT
..B1.67:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        movzwl    344(%rsp), %eax
        testl     $32767, %eax
        fstpt     240(%rsp)
        jle       ..B1.25
..B1.24:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      16(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.68:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
        jmp       ..B1.26
..B1.25:
        fldt      (%rsp)
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
..B1.26:
        testb     %r15b, %r15b
        je        ..B1.28
..B1.27:
        fldt      64(%rsp)
        fstpt     (%rsp)
..B1.69:
        fldcw     98(%rsp)
..B1.28:
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.50
..B1.29:
        fldt      240(%rsp)
        fldt      256(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.49
..B1.30:
        addq      $288, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 320
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.31:
        cmpq      $0, 336(%rsp)
        jne       ..B1.33
..B1.32:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      336(%rsp)
        fstpt     (%rsp)
        call      expl@PLT
..B1.70:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      336(%rsp)
        fstpt     256(%rsp)
        fstpt     240(%rsp)
        jmp       ..B1.44
..B1.33:
        movzwl    344(%rsp), %ebx
        andl      $32767, %ebx
        cmpl      $32767, %ebx
        jge       ..B1.36
..B1.34:
        fldt      320(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fstpt     (%rsp)
        call      expl@PLT
..B1.72:
        fstpt     32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      336(%rsp)
        fstpt     (%rsp)
        fldt      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fstpt     (%rsp)
        call      cosl@PLT
..B1.71:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      16(%rsp)
        testl     %ebx, %ebx
        fmulp     %st, %st(1)
        fstpt     240(%rsp)
        jle       ..B1.56
..B1.35:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      16(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.73:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
        jmp       ..B1.44
..B1.36:
        cmpl      $32767, %edx
        jge       ..B1.38
..B1.37:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      352(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.74:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fstpt     256(%rsp)
        jmp       ..B1.44
..B1.38:
        cmpl      $-2147483648, 324(%rsp)
        jne       ..B1.40
..B1.39:
        cmpl      $0, 320(%rsp)
        je        ..B1.41
..B1.40:
        fldt      320(%rsp)
        fmul      %st(0), %st
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fldt      336(%rsp)
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
        jmp       ..B1.44
..B1.41:
        testb     $-128, 329(%rsp)
        je        ..B1.43
..B1.42:
        fldt      336(%rsp)
        fmul      %st(0), %st
        fstpt     336(%rsp)
        fldt      80(%rsp)
        fstpt     240(%rsp)
        fldt      240(%rsp)
        fstpt     256(%rsp)
        jmp       ..B1.44
..B1.43:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      336(%rsp)
        fstpt     (%rsp)
        call      expl@PLT
..B1.76:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fstpt     240(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 336
        fldt      352(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.75:
        addq      $16, %rsp
	.cfi_def_cfa_offset 320
        fstpt     256(%rsp)
..B1.44:
        testb     %r15b, %r15b
        je        ..B1.46
..B1.45:
        fldcw     98(%rsp)
..B1.46:
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.50
..B1.47:
        fldt      240(%rsp)
        fldt      256(%rsp)
        movq      272(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.49
..B1.48:
        addq      $288, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 16
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 320
	.cfi_offset 3, -32
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.49:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.50:
        call      __stack_chk_fail@PLT
..B1.51:
        lea       _CONSTANTS(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     16(%rsp)
        jmp       ..B1.23
..B1.52:
        cmpl      $0, 340(%rsp)
        jne       ..B1.11
..B1.53:
        cmpl      $0, 336(%rsp)
        jne       ..B1.11
        jmp       ..B1.31
..B1.54:
        cmpl      $0, 324(%rsp)
        jne       ..B1.9
..B1.55:
        cmpl      $0, 320(%rsp)
        jne       ..B1.9
        jmp       ..B1.31
..B1.56:
        cmpq      $0, 320(%rsp)
        je        ..B1.35
..B1.57:
        fldt      (%rsp)
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
        jmp       ..B1.44
        .align    16,0x90
	.cfi_endproc
	.type	cexpl,@function
	.size	cexpl,.-cexpl
	.data
# -- End  cexpl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 2
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
	.word	0
	.word	0
	.word	32768
	.word	1
	.word	0
	.word	0
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
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,64
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
