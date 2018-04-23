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
	.file "cacosl.c"
	.text
..TXTST0:
# -- Begin  cacosl
	.text
       .align    16,0x90
	.globl cacosl
cacosl:
# parameter 1: 512 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_cacosl.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %rbx
	.cfi_def_cfa_offset 40
	.cfi_offset 3, -40
        pushq     %rbp
	.cfi_def_cfa_offset 48
	.cfi_offset 6, -48
        subq      $464, %rsp
	.cfi_def_cfa_offset 512
        xorb      %bl, %bl
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 448(%rsp)
        fstpt     8(%rsp)
        fldt      8(%rsp)
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
..B1.2:
        fnstcw    26(%rsp)
..B1.3:
        movzwl    26(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 24(%rsp)
..B1.5:
        fldcw     24(%rsp)
..B1.6:
        movb      $1, %bl
..B1.7:
        movzwl    520(%rsp), %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.15
..B1.8:
        testl     %edx, %edx
        jle       ..B1.36
..B1.9:
        movzwl    536(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jge       ..B1.15
..B1.10:
        testl     %eax, %eax
        jle       ..B1.34
..B1.11:
        lea       32(%rsp), %rbp
        lea       80(%rsp), %r12
        lea       176(%rsp), %r14
        lea       128(%rsp), %r13
        pushq     %rbp
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r13
	.cfi_def_cfa_offset 536
        pushq     %r14
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacosl.18:
        call      __libm_carctrigprepare_k80@PLT
..___tag_value_cacosl.19:
..B1.59:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.12:
        pushq     %rbp
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r13
	.cfi_def_cfa_offset 536
        pushq     %r14
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacosl.25:
        call      __libm_recacosl_k80@PLT
..___tag_value_cacosl.26:
..B1.60:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.13:
        fstpt     416(%rsp)
        pushq     %rbp
	.cfi_def_cfa_offset 520
        pushq     %r12
	.cfi_def_cfa_offset 528
        pushq     %r13
	.cfi_def_cfa_offset 536
        pushq     %r14
	.cfi_def_cfa_offset 544
        lea       544(%rsp), %rdi
        lea       560(%rsp), %rsi
        lea       400(%rsp), %rdx
        lea       352(%rsp), %rcx
        lea       304(%rsp), %r8
        lea       256(%rsp), %r9
..___tag_value_cacosl.32:
        call      __libm_recacoshl_k80@PLT
..___tag_value_cacosl.33:
..B1.61:
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
..B1.14:
        movb      537(%rsp), %al
        lea       ones(%rip), %rdx
        andb      $-128, %al
        shrb      $7, %al
        xorb      $1, %al
        movzbl    %al, %ecx
        fldl      (%rdx,%rcx,8)
        fmulp     %st, %st(1)
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.15:
        cmpq      $0, 512(%rsp)
        jne       ..B1.17
..B1.16:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fldt      24(%rsp)
        fstpt     (%rsp)
        call      acosl@PLT
..B1.63:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        fstpt     416(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fldt      544(%rsp)
        fstpt     (%rsp)
        call      asinhl@PLT
..B1.62:
        fldt      .L_2il0floatpacket.9(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        fmulp     %st, %st(1)
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.17:
        movzwl    536(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.53
..B1.69:
        cmpl      $32767, %edx
..B1.18:
        je        ..B1.43
..B1.19:
        cmpl      $32767, %eax
        je        ..B1.40
..B1.20:
        cmpq      $0, 528(%rsp)
        jne       ..B1.25
..B1.21:
        fldt      512(%rsp)
        cmpl      $16383, %edx
        jl        ..B1.23
..B1.22:
        movb      521(%rsp), %al
        lea       ones(%rip), %rbp
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        fldl      (%rbp,%rdx,8)
        fstl      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      acoshl@PLT
..B1.65:
        fstpt     24(%rsp)
        fldl      16(%rsp)
        fstpt     (%rsp)
        call      acosl@PLT
..B1.64:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        fstpt     416(%rsp)
        jmp       ..B1.24
..B1.23:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 528
        fstpt     (%rsp)
        call      acosl@PLT
..B1.66:
        addq      $16, %rsp
	.cfi_def_cfa_offset 512
        lea       ones(%rip), %rbp
        fstpt     416(%rsp)
..B1.24:
        fldt      8(%rsp)
        movb      537(%rsp), %al
        andb      $-128, %al
        shrb      $7, %al
        xorb      $1, %al
        movzbl    %al, %edx
        fmull     (%rbp,%rdx,8)
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.25:
        fldt      528(%rsp)
        cmpl      $32767, %eax
        fldt      512(%rsp)
        je        ..B1.27
..B1.26:
        fld       %st(0)
        fmul      %st(1), %st
        jmp       ..B1.28
..B1.27:
        fld       %st(1)
        fmul      %st(2), %st
..B1.28:
        movb      537(%rsp), %bpl
        lea       ones(%rip), %r12
        andb      $-128, %bpl
        shrb      $7, %bpl
        movb      %bpl, %al
        xorb      $1, %al
        movzbl    %al, %edx
        fldl      (%r12,%rdx,8)
        fmulp     %st, %st(1)
        fstpt     432(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 544
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
..B1.67:
        movzbl    %bpl, %eax
        addq      $32, %rsp
	.cfi_def_cfa_offset 512
        fmull     (%r12,%rax,8)
        fstpt     416(%rsp)
..B1.29:
        testb     %bl, %bl
        je        ..B1.31
..B1.30:
        fldcw     26(%rsp)
..B1.31:
        movq      448(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.39
..B1.32:
        fldt      416(%rsp)
        fldt      432(%rsp)
        movq      448(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.38
..B1.33:
        addq      $464, %rsp
	.cfi_def_cfa_offset 48
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 40
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 32
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
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
	.cfi_offset 13, -24
	.cfi_offset 14, -32
..B1.34:
        cmpl      $0, 532(%rsp)
        jne       ..B1.11
..B1.35:
        cmpl      $0, 528(%rsp)
        jne       ..B1.11
        jmp       ..B1.15
..B1.36:
        cmpl      $0, 516(%rsp)
        jne       ..B1.9
..B1.37:
        cmpl      $0, 512(%rsp)
        jne       ..B1.9
        jmp       ..B1.15
..B1.38:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.39:
        call      __stack_chk_fail@PLT
..B1.40:
        cmpl      $-2147483648, 532(%rsp)
        jne       ..B1.42
..B1.41:
        cmpl      $0, 528(%rsp)
        je        ..B1.20
..B1.42:
        fldt      528(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      512(%rsp)
        fmul      %st(0), %st
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.43:
        cmpl      $-2147483648, 516(%rsp)
        jne       ..B1.45
..B1.44:
        cmpl      $0, 512(%rsp)
        je        ..B1.47
..B1.45:
        cmpl      $32767, %eax
        jge       ..B1.47
..B1.46:
        fldt      512(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.47:
        cmpl      $-2147483648, 516(%rsp)
        jne       ..B1.49
..B1.48:
        cmpl      $0, 512(%rsp)
        je        ..B1.19
..B1.49:
        cmpl      $32767, %eax
        jne       ..B1.20
..B1.50:
        cmpl      $-2147483648, 532(%rsp)
        jne       ..B1.52
..B1.51:
        cmpl      $0, 528(%rsp)
        je        ..B1.40
..B1.52:
        fldt      512(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      528(%rsp)
        fmul      %st(0), %st
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.53:
        cmpl      $-2147483648, 532(%rsp)
        jne       ..B1.55
..B1.54:
        cmpl      $0, 528(%rsp)
        je        ..B1.68
..B1.55:
        cmpl      $32767, %edx
        jge       ..B1.18
..B1.56:
        fldt      528(%rsp)
        fmul      %st(0), %st
        fstpt     416(%rsp)
        fldt      416(%rsp)
        fstpt     432(%rsp)
        jmp       ..B1.29
..B1.68:
        cmpl      $32767, %edx
        je        ..B1.43
        jmp       ..B1.19
        .align    16,0x90
	.cfi_endproc
	.type	cacosl,@function
	.size	cacosl,.-cacosl
	.data
# -- End  cacosl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0xbf,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
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
