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
	.file "csqrtl.c"
	.text
..TXTST0:
# -- Begin  csqrtl
	.text
       .align    16,0x90
	.globl csqrtl
csqrtl:
# parameter 1: 288 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_csqrtl.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        pushq     %rbp
	.cfi_def_cfa_offset 32
	.cfi_offset 6, -32
        subq      $256, %rsp
	.cfi_def_cfa_offset 288
        xorb      %r12b, %r12b
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 240(%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fstpt     224(%rsp)
..B1.2:
        fnstcw    66(%rsp)
..B1.3:
        movzwl    66(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 64(%rsp)
..B1.5:
        fldcw     64(%rsp)
..B1.6:
        movb      $1, %r12b
..B1.7:
        movzwl    296(%rsp), %eax
        movl      %eax, %ebp
        andl      $32767, %ebp
        cmpl      $32767, %ebp
        jge       ..B1.30
..B1.8:
        cmpl      $0, 292(%rsp)
        jne       ..B1.10
..B1.9:
        cmpl      $0, 288(%rsp)
        je        ..B1.30
..B1.10:
        movzwl    312(%rsp), %edx
        movl      %edx, %ebx
        andl      $32767, %ebx
        cmpl      $32767, %ebx
        jge       ..B1.46
..B1.11:
        cmpl      $0, 308(%rsp)
        jne       ..B1.13
..B1.12:
        cmpl      $0, 304(%rsp)
        je        ..B1.32
..B1.13:
        fldt      288(%rsp)
        testl     %ebp, %ebp
        jle       ..B1.55
..B1.14:
        andl      $-32768, %eax
        addl      $-16383, %ebp
        fstpt     176(%rsp)
        fldt      176(%rsp)
        orl       $-49153, %eax
        movw      %ax, 184(%rsp)
..B1.15:
        fldt      304(%rsp)
        testl     %ebx, %ebx
        movb      $63, 185(%rsp)
        jle       ..B1.54
..B1.16:
        andl      $-32768, %edx
        addl      $-16383, %ebx
        fstpt     80(%rsp)
        fldt      80(%rsp)
        orl       $-49153, %edx
        movw      %dx, 88(%rsp)
..B1.17:
        fldt      48(%rsp)
        movb      $63, 89(%rsp)
        fstpt     96(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 320
        lea       144(%rsp), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value_csqrtl.11:
        call      __libm_hypot2l_k80@PLT
..___tag_value_csqrtl.12:
..B1.69:
        addq      $32, %rsp
	.cfi_def_cfa_offset 288
..B1.18:
        fldt      128(%rsp)
        lea       32+_CONSTANTS(%rip), %rax
        lea       t32(%rip), %rdx
        fldt      (%rax)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%rsp)
        fldt      112(%rsp)
        fld       %st(0)
        fsqrt     
        fldt      (%rdx)
        fstpt     (%rsp)
        fldt      (%rsp)
        fmul      %st(1), %st
        fld       %st(0)
        movl      16(%rsp), %esi
        fsubr     %st(2), %st
        sarl      $1, %esi
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      .L_2il0floatpacket.9(%rip)
        fmulp     %st, %st(2)
        fdivr     %st, %st(1)
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        fsubr     %st, %st(1)
        movzwl    152(%rsp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %eax
        fxch      %st(1)
        fstpt     160(%rsp)
        fldt      160(%rsp)
        lea       -16464(%rax,%rsi), %ecx
        cmpl      %ecx, %ebp
        jle       ..B1.22
..B1.19:
        lea       -16302(%rax,%rsi), %eax
        cmpl      %eax, %ebp
        jge       ..B1.21
..B1.20:
        subl      %esi, %ebp
        fld       %st(1)
        addl      $16383, %ebp
        fld       %st(2)
        movzwl    184(%rsp), %eax
        andl      $32767, %ebp
        andl      $-32768, %eax
        orl       %ebp, %eax
        movl      %esi, %ebp
        movw      %ax, 184(%rsp)
        fldt      176(%rsp)
        fadd      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(5)
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%rsp)
        jmp       ..B1.23
..B1.21:
        fstp      %st(0)
        fstp      %st(0)
        fldt      176(%rsp)
        jmp       ..B1.23
..B1.22:
        fstpt     48(%rsp)
        movl      %esi, %ebp
..B1.23:
        fldt      48(%rsp)
        lea       1(%rbp), %eax
        fldt      (%rsp)
        andl      $1, %eax
        subl      %eax, %ebp
        lea       onetwo(%rip), %rdx
        shlq      $4, %rax
        decl      %ebp
        sarl      $1, %ebp
        movl      %ebp, %edi
        fldt      (%rax,%rdx)
        fmul      %st, %st(3)
        fld       %st(3)
        fsqrt     
        fmul      %st, %st(2)
        fld       %st(2)
        fsubr     %st(1), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        fld       %st(2)
        fmul      %st(3), %st
        fld       %st(1)
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(4)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fldt      .L_2il0floatpacket.9(%rip)
        fmulp     %st, %st(3)
        fdivr     %st, %st(2)
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 320
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fstpt     64(%rsp)
..___tag_value_csqrtl.15:
        call      __libm_scalbl_k80@PLT
..___tag_value_csqrtl.16:
..B1.70:
        fldt      64(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 288
..B1.24:
        fldt      80(%rsp)
        subl      %ebp, %ebx
        fldt      16(%rsp)
        movl      %ebx, %edi
        fld       %st(0)
        fdivr     %st(2), %st
        fmul      %st, %st(3)
        fld       %st(1)
        fldt      (%rsp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fsubr     %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      96(%rsp)
        faddp     %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldt      16(%rsp)
        fdivrp    %st, %st(2)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 320
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%rsp)
        fmulp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_csqrtl.19:
        call      __libm_scalbl_k80@PLT
..___tag_value_csqrtl.20:
..B1.71:
        addq      $32, %rsp
	.cfi_def_cfa_offset 288
..B1.25:
        testb     $-128, 297(%rsp)
        jne       ..B1.27
..B1.26:
        fldt      48(%rsp)
        lea       224(%rsp), %rbx
        fstpt     -16(%rbx)
        jmp       ..B1.28
..B1.27:
        fldt      48(%rsp)
        lea       224(%rsp), %rbx
        fxch      %st(1)
        fstpt     -16(%rbx)
..B1.28:
        movb      313(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fmulp     %st, %st(1)
        fstpt     (%rbx)
        jmp       ..B1.49
..B1.30:
        movzwl    312(%rsp), %ebx
        andl      $32767, %ebx
..B1.31:
        cmpl      $32767, %ebx
        jge       ..B1.46
..B1.32:
        movq      288(%rsp), %rax
        testq     %rax, %rax
        jne       ..B1.39
..B1.33:
        cmpl      $0, 308(%rsp)
        jne       ..B1.35
..B1.34:
        cmpl      $0, 304(%rsp)
        je        ..B1.39
..B1.35:
        fldt      304(%rsp)
        lea       ones(%rip), %rdx
        movb      313(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        cmpl      $2, %ebx
        fldl      (%rdx,%rcx,8)
        jl        ..B1.37
..B1.36:
        lea       halfs(%rip), %rax
        fldl      (%rax,%rcx,8)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsqrt     
        jmp       ..B1.38
..B1.37:
        lea       _CONSTANTS(%rip), %rax
        lea       64+_CONSTANTS(%rip), %rdx
        fldt      (%rax)
        fmulp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fldt      (%rdx)
        fmulp     %st, %st(1)
..B1.38:
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
        jmp       ..B1.49
..B1.39:
        cmpl      $32767, %ebp
        jge       ..B1.44
..B1.40:
        fldt      288(%rsp)
        lea       ones(%rip), %rcx
        movb      297(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        fmull     (%rcx,%rdx,8)
        fstpt     176(%rsp)
        fldt      176(%rsp)
        fsqrt     
        jne       ..B1.42
..B1.41:
        fstpt     208(%rsp)
        lea       224(%rsp), %rbx
        jmp       ..B1.43
..B1.42:
        fldt      48(%rsp)
        lea       224(%rsp), %rbx
        fstpt     -16(%rbx)
        fstpt     48(%rsp)
..B1.43:
        fldt      48(%rsp)
        movb      313(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        fmull     (%rcx,%rdx,8)
        fstpt     (%rbx)
        jmp       ..B1.49
..B1.44:
        movq      $0x8000000000000000, %rdx
        cmpq      %rdx, %rax
        je        ..B1.40
..B1.45:
        fldt      288(%rsp)
        fmul      %st(0), %st
        fstpt     208(%rsp)
        fldt      208(%rsp)
        fstpt     224(%rsp)
        jmp       ..B1.49
..B1.46:
        je        ..B1.62
..B1.47:
        cmpl      $32767, %ebp
        je        ..B1.58
..B1.48:
        fldt      288(%rsp)
        fld       %st(0)
        fldt      304(%rsp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     208(%rsp)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
..B1.49:
        testb     %r12b, %r12b
        je        ..B1.51
..B1.50:
        fldcw     66(%rsp)
..B1.51:
        movq      240(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.57
..B1.52:
        fldt      208(%rsp)
        fldt      224(%rsp)
        movq      240(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.56
..B1.53:
        addq      $256, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 288
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.54:
        lea       16+_CONSTANTS(%rip), %rbx
        fldt      (%rbx)
        fmul      %st(1), %st
        fstpt     80(%rsp)
        movzwl    88(%rsp), %eax
        movl      %eax, %ebx
        andl      $-32768, %eax
        andl      $32767, %ebx
        orl       $-49153, %eax
        addl      $-16639, %ebx
        movw      %ax, 88(%rsp)
        jmp       ..B1.17
..B1.55:
        lea       16+_CONSTANTS(%rip), %rbp
        fldt      (%rbp)
        fmul      %st(1), %st
        fstpt     176(%rsp)
        movzwl    184(%rsp), %eax
        movl      %eax, %ebp
        andl      $-32768, %eax
        andl      $32767, %ebp
        orl       $-49153, %eax
        addl      $-16639, %ebp
        movw      %ax, 184(%rsp)
        jmp       ..B1.15
..B1.56:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.57:
        call      __stack_chk_fail@PLT
..B1.58:
        movq      $0x8000000000000000, %rax
        cmpq      288(%rsp), %rax
        jne       ..B1.48
..B1.59:
        fldt      288(%rsp)
        fmul      %st(0), %st
        fldt      304(%rsp)
        fmul      %st(0), %st
        testb     $-128, 297(%rsp)
        jne       ..B1.61
..B1.60:
        fxch      %st(1)
        fstpt     208(%rsp)
        fstpt     224(%rsp)
        jmp       ..B1.49
..B1.61:
        fstpt     208(%rsp)
        fstpt     224(%rsp)
        jmp       ..B1.49
..B1.62:
        movq      $0x8000000000000000, %rax
        cmpq      304(%rsp), %rax
        jne       ..B1.47
..B1.63:
        fldt      304(%rsp)
        lea       ones(%rip), %rdx
        movb      313(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        fmul      %st(0), %st
        fstpt     208(%rsp)
        fldt      208(%rsp)
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fmulp     %st, %st(1)
        fstpt     224(%rsp)
        jmp       ..B1.49
        .align    16,0x90
	.cfi_endproc
	.type	csqrtl,@function
	.size	csqrtl,.-csqrtl
	.data
# -- End  csqrtl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 16
t32:
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x80,0x00,0x00,0x00,0x80,0x1f,0xc0,0x00,0x00,0x00,0x00,0x00,0x00
	.type	t32,@object
	.size	t32,32
	.align 16
onetwo:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	onetwo,@object
	.size	onetwo,32
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 8
halfs:
	.long	0x00000000,0x3fe00000
	.long	0x00000000,0xbfe00000
	.type	halfs,@object
	.size	halfs,16
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
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16127
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,80
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
