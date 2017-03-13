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
	.file "cacoshl.c"
	.text
..TXTST0:
# -- Begin  cacoshl
	.text
       .align    16,0x90
	.globl cacoshl
cacoshl:
# parameter 1: 512 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cacoshl.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        pushq     %r15
	.cfi_def_cfa_offset 32
	.cfi_offset 15, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $464, %rsp
	.cfi_def_cfa_offset 512
        xorb      %bpl, %bpl
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 448(%rsp)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.5:
        fldcw     16(%rsp)
..B1.6:
        movb      $1, %bpl
..B1.7:
        movzwl    520(%rsp), %esi
        andl      $32767, %esi
        cmpl      $32767, %esi
        jge       ..B1.15
..B1.8:
        testl     %esi, %esi
        jle       ..B1.39
..B1.9:
        movzwl    536(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.15
..B1.10:
        testl     %eax, %eax
        jle       ..B1.37
..B1.11:
        lea       32(%rsp), %rbx
        lea       80(%rsp), %r12
        lea       176(%rsp), %r15
        lea       128(%rsp), %r14
        pushq     %rbx
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r14
	.cfi_def_cfa_offset 536
        pushq     %r15
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacoshl.18:
        call      __libm_carctrigprepare_k80@PLT
..___tag_value_cacoshl.19:
..B1.61:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.12:
        pushq     %rbx
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r14
	.cfi_def_cfa_offset 536
        pushq     %r15
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacoshl.25:
        call      __libm_recacoshl_k80@PLT
..___tag_value_cacoshl.26:
..B1.62:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.13:
        fstpt     416(%rsp)
        pushq     %rbx
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r14
	.cfi_def_cfa_offset 536
        pushq     %r15
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacoshl.32:
        call      __libm_recacosl_k80@PLT
..___tag_value_cacoshl.33:
..B1.63:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.14:
        movb      537(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fmulp     %st, %st(1)
        fstpt     432(%rsp)
        jmp       ..B1.32
..B1.15:
        movq      528(%rsp), %rdx
        testq     %rdx, %rdx
        jne       ..B1.22
..B1.16:
        cmpl      $32767, %esi
        jge       ..B1.21
..B1.17:
        cmpl      $16383, %esi
        jl        ..B1.19
..B1.18:
        fldt      512(%rsp)
        movb      521(%rsp), %bl
        andb      $-128, %bl
        shrb      $7, %bl
        movzbl    %bl, %eax
        lea       ones(%rip), %rbx
        fldl      (%rbx,%rax,8)
        fstl      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      acoshl@PLT
..B1.65:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        fstpt     416(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fldl      16(%rsp)
        fstpt     (%rsp)
        call      acosl@PLT
..B1.64:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        lea       432(%rsp), %rcx
        jmp       ..B1.20
..B1.19:
        fldt      .L_2il0floatpacket.8(%rip)
        fstpt     416(%rsp)
        fldt      512(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fstpt     (%rsp)
        call      acosl@PLT
..B1.66:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        lea       ones(%rip), %rbx
        lea       432(%rsp), %rcx
..B1.20:
        movb      537(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        fldl      (%rbx,%rdx,8)
        fmulp     %st, %st(1)
        fstpt     (%rcx)
        jmp       ..B1.32
..B1.21:
        movq      $0x8000000000000000, %rax
        movq      512(%rsp), %rbx
        cmpq      %rax, %rbx
        je        ..B1.17
        jmp       ..B1.23
..B1.22:
        movq      512(%rsp), %rbx
..B1.23:
        movzwl    536(%rsp), %ecx
        andl      $32767, %ecx
        testq     %rbx, %rbx
        jne       ..B1.27
..B1.24:
        cmpl      $32767, %ecx
        jge       ..B1.26
..B1.25:
        fldt      528(%rsp)
        lea       ones(%rip), %rdx
        fstpt     (%rsp)
        fldt      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        movb      553(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      asinhl@PLT
..B1.68:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        fstpt     416(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 544
        fldt      32(%rsp)
        fstpt     (%rsp)
        fldt      544(%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
        jmp       ..B1.69
	.cfi_def_cfa_offset 512
..B1.26:
        movq      $0x8000000000000000, %rax
        cmpq      %rax, %rdx
        je        ..B1.25
..B1.27:
        cmpl      $32767, %esi
        je        ..B1.49
..B1.73:
        cmpl      $32767, %ecx
..B1.28:
        je        ..B1.45
..B1.71:
        cmpl      $32767, %esi
..B1.29:
        je        ..B1.43
..B1.30:
        fldt      528(%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     416(%rsp)
        fldt      512(%rsp)
        fxch      %st(1)
..B1.31:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 544
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
..B1.69:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
        fstpt     432(%rsp)
..B1.32:
        testb     %bpl, %bpl
        je        ..B1.34
..B1.33:
        fldcw     18(%rsp)
..B1.34:
        movq      448(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.42
..B1.35:
        fldt      416(%rsp)
        fldt      432(%rsp)
        movq      448(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.41
..B1.36:
        addq      $464, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 15
        popq      %r15
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        fxch      %st(1)
        ret       
	.cfi_def_cfa_offset 512
	.cfi_offset 3, -40
	.cfi_offset 6, -48
	.cfi_offset 12, -16
	.cfi_offset 14, -24
	.cfi_offset 15, -32
..B1.37:
        cmpl      $0, 532(%rsp)
        jne       ..B1.11
..B1.38:
        cmpl      $0, 528(%rsp)
        jne       ..B1.11
        jmp       ..B1.15
..B1.39:
        cmpl      $0, 516(%rsp)
        jne       ..B1.9
..B1.40:
        cmpl      $0, 512(%rsp)
        jne       ..B1.9
        jmp       ..B1.15
..B1.41:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.42:
        call      __stack_chk_fail@PLT
..B1.43:
        movq      $0x8000000000000000, %rax
        cmpq      %rax, %rbx
        jne       ..B1.30
..B1.44:
        fldt      512(%rsp)
        fld       %st(0)
        fmul      %st(1), %st
        fstpt     416(%rsp)
        fldt      528(%rsp)
        jmp       ..B1.31
..B1.45:
        cmpl      $-2147483648, 532(%rsp)
        jne       ..B1.47
..B1.46:
        cmpl      $0, 528(%rsp)
        je        ..B1.70
..B1.47:
        cmpl      $32767, %esi
        jge       ..B1.29
..B1.48:
        fldt      528(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
        jmp       ..B1.32
..B1.49:
        cmpl      $-2147483648, 516(%rsp)
        jne       ..B1.51
..B1.50:
        cmpl      $0, 512(%rsp)
        je        ..B1.52
..B1.51:
        cmpl      $32767, %ecx
        je        ..B1.56
..B1.52:
        cmpl      $-2147483648, 516(%rsp)
        jne       ..B1.54
..B1.53:
        cmpl      $0, 512(%rsp)
        je        ..B1.72
..B1.54:
        cmpl      $32767, %ecx
        jge       ..B1.28
..B1.55:
        fldt      512(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
        jmp       ..B1.32
..B1.56:
        cmpl      $-2147483648, 532(%rsp)
        jne       ..B1.58
..B1.57:
        cmpl      $0, 528(%rsp)
        je        ..B1.52
..B1.58:
        fldt      512(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      528(%rsp)
        fmul      %st(0), %st
        fstpt     432(%rsp)
        jmp       ..B1.32
..B1.70:
        cmpl      $32767, %esi
        je        ..B1.43
        jmp       ..B1.30
..B1.72:
        cmpl      $32767, %ecx
        je        ..B1.45
        jmp       ..B1.71
        .align    16,0x90
	.cfi_endproc
	.type	cacoshl,@function
	.size	cacoshl,.-cacoshl
	.data
# -- End  cacoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
