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
# parameter 1: 336 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_ccoshl.1:
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
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $296, %rsp
	.cfi_def_cfa_offset 336
        xorb      %bl, %bl
        fldt      .L_2il0floatpacket.8(%rip)
        movq      %fs:40, %rax
        xorq      %rsp, %rax
        movq      %rax, 288(%rsp)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     256(%rsp)
        fldt      256(%rsp)
        fstpt     272(%rsp)
..B1.2:
        fnstcw    114(%rsp)
..B1.3:
        movzwl    114(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 112(%rsp)
..B1.5:
        fldcw     112(%rsp)
..B1.6:
        movb      $1, %bl
..B1.7:
        movzwl    344(%rsp), %ebp
        andl      $32767, %ebp
        cmpl      $32767, %ebp
        jge       ..B1.33
..B1.8:
        testl     %ebp, %ebp
        jle       ..B1.57
..B1.9:
        movzwl    360(%rsp), %r14d
        andl      $32767, %r14d
        cmpl      $32767, %r14d
        jge       ..B1.33
..B1.10:
        testl     %r14d, %r14d
        jle       ..B1.55
..B1.11:
        cmpl      $16398, %ebp
        jl        ..B1.20
..B1.12:
        fldt      352(%rsp)
        lea       _CONSTANTS(%rip), %rax
        fstpt     (%rsp)
        fldt      (%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     (%rsp)
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     32(%rsp)
        call      cosl@PLT
..B1.65:
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fmul      %st, %st(1)
        testl     %r14d, %r14d
        fxch      %st(1)
        fstpt     256(%rsp)
        fldt      336(%rsp)
        fmulp     %st, %st(1)
        jle       ..B1.14
..B1.13:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      16(%rsp)
        fstpt     (%rsp)
        fstpt     32(%rsp)
        call      sinl@PLT
..B1.66:
        fldt      32(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fmulp     %st, %st(1)
        jmp       ..B1.15
..B1.14:
        fldt      (%rsp)
        fmulp     %st, %st(1)
..B1.15:
        fstpt     272(%rsp)
        testb     %bl, %bl
        je        ..B1.17
..B1.16:
        fldcw     114(%rsp)
..B1.17:
        movq      288(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.54
..B1.18:
        fldt      256(%rsp)
        fldt      272(%rsp)
        movq      288(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.53
..B1.19:
        addq      $296, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.20:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      352(%rsp)
        lea       176(%rsp), %rdi
        fstpt     (%rsp)
..___tag_value_ccoshl.31:
        call      __libm_coshl_k80@PLT
..___tag_value_ccoshl.32:
..B1.67:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B1.21:
        fldt      176(%rsp)
        lea       48+_CONSTANTS(%rip), %r15
        fldt      .L_2il0floatpacket.9(%rip)
        movzwl    344(%rsp), %eax
        andl      $32767, %eax
        fldt      (%r15)
        cmpl      $16183, %eax
        fadd      %st, %st(3)
        fxch      %st(3)
        fstpt     (%rsp)
        fldt      160(%rsp)
        fld       %st(0)
        movl      (%rsp), %r15d
        fadd      %st(3), %st
        fmul      %st, %st(2)
        fld       %st(0)
        fsubr     %st(3), %st
        fsubrp    %st, %st(3)
        fxch      %st(2)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fxch      %st(1)
        fsubr     %st(2), %st
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        jge       ..B1.23
..B1.22:
        fstp      %st(1)
        fldt      336(%rsp)
        lea       32+_CONSTANTS(%rip), %r14
        fldt      96(%rsp)
        lea       208(%rsp), %rdx
        fstpt     (%rdx)
        fldt      (%r14)
        fmulp     %st, %st(1)
        fstpt     -16(%rdx)
        movzwl    -8(%rdx), %eax
        movl      %eax, %r14d
        andl      $-32768, %eax
        andl      $32767, %r14d
        orl       $-49153, %eax
        addl      $-16639, %r14d
        movw      %ax, -8(%rdx)
        jmp       ..B1.25
..B1.23:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      352(%rsp)
        lea       208(%rsp), %rdi
        fstpt     (%rsp)
        fxch      %st(1)
        fstpt     16(%rsp)
        fstpt     -128(%rdi)
..___tag_value_ccoshl.35:
        call      __libm_sinhl_k80@PLT
..___tag_value_ccoshl.36:
..B1.68:
        fldt      80(%rsp)
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B1.24:
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        lea       208(%rsp), %rdx
        movl      (%rsp), %r14d
..B1.25:
        fldt      192(%rsp)
        fld       %st(0)
        fldt      (%rdx)
        fadd      %st, %st(1)
        fld       %st(1)
        fxch      %st(2)
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fmul      %st(3), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      368(%rsp)
        fstpt     (%rsp)
        lea       240(%rsp), %rdi
        fxch      %st(1)
        fstpt     80(%rsp)
        fstpt     96(%rsp)
..___tag_value_ccoshl.39:
        call      __libm_cosl_k80@PLT
..___tag_value_ccoshl.40:
..B1.69:
        fldt      96(%rsp)
        fldt      80(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B1.26:
        fldt      224(%rsp)
        lea       16+_CONSTANTS(%rip), %rax
        fld       %st(0)
        fldt      240(%rsp)
        movzwl    360(%rsp), %edx
        fadd      %st, %st(1)
        fld       %st(1)
        andl      $32767, %edx
        cmpl      $16183, %edx
        fxch      %st(2)
        fsub      %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fmul      %st(3), %st
        fsub      %st, %st(2)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st(4), %st
        fxch      %st(1)
        fmulp     %st, %st(4)
        fldt      (%rax)
        faddp     %st, %st(1)
        jge       ..B1.28
..B1.27:
        fldt      352(%rsp)
        lea       32+_CONSTANTS(%rip), %rbp
        fldt      96(%rsp)
        fstpt     144(%rsp)
        fldt      (%rbp)
        fmulp     %st, %st(1)
        fstpt     128(%rsp)
        movzwl    136(%rsp), %eax
        movl      %eax, %ebp
        andl      $-32768, %eax
        andl      $32767, %ebp
        orl       $-49153, %eax
        addl      $-16639, %ebp
        movw      %ax, 136(%rsp)
        fldt      128(%rsp)
        jmp       ..B1.30
..B1.28:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      368(%rsp)
        lea       144(%rsp), %rdi
        fstpt     (%rsp)
        fxch      %st(3)
        fstpt     16(%rsp)
        fstpt     -64(%rdi)
        fxch      %st(1)
        fstpt     -112(%rdi)
        fstpt     -48(%rdi)
..___tag_value_ccoshl.43:
        call      __libm_sinl_k80@PLT
..___tag_value_ccoshl.44:
..B1.70:
        fldt      96(%rsp)
        fldt      32(%rsp)
        fldt      80(%rsp)
        fldt      16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
..B1.29:
        fldt      128(%rsp)
        xorl      %ebp, %ebp
        fmul      %st(5), %st
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldt      144(%rsp)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fstpt     96(%rsp)
        fldt      96(%rsp)
        fstpt     144(%rsp)
        fxch      %st(4)
        fxch      %st(1)
        fxch      %st(2)
        fxch      %st(1)
..B1.30:
        fldt      96(%rsp)
        fld       %st(1)
        movl      %r15d, %edi
        fadd      %st(1), %st
        fsub      %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fld       %st(1)
        fldt      .L_2il0floatpacket.9(%rip)
        fmul      %st(3), %st
        fsub      %st, %st(1)
        fsubp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fmul      %st(5), %st
        fldt      32(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        fld       %st(0)
        fmul      %st(4), %st
        fstpt     (%rsp)
        fmulp     %st, %st(6)
        faddp     %st, %st(5)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     16(%rsp)
        fxch      %st(1)
        fstpt     48(%rsp)
        fstpt     112(%rsp)
..___tag_value_ccoshl.47:
        call      __libm_scalbl_k80@PLT
..___tag_value_ccoshl.48:
..B1.71:
        fldt      112(%rsp)
        fldt      48(%rsp)
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B1.31:
        fldt      (%rsp)
        fld       %st(2)
        fmul      %st(2), %st
        addl      %ebp, %r14d
        fxch      %st(4)
        fstpt     256(%rsp)
        movl      %r14d, %edi
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 368
        fld       %st(0)
        fmul      %st(2), %st
        fstpt     (%rsp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..___tag_value_ccoshl.51:
        call      __libm_scalbl_k80@PLT
..___tag_value_ccoshl.52:
..B1.72:
        addq      $32, %rsp
	.cfi_def_cfa_offset 336
..B1.32:
        fstpt     272(%rsp)
        jmp       ..B1.48
..B1.33:
        movq      352(%rsp), %r14
        testq     %r14, %r14
        jne       ..B1.37
..B1.34:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      352(%rsp)
        fstpt     (%rsp)
        call      coshl@PLT
..B1.73:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fldt      352(%rsp)
        cmpl      $32767, %ebp
        fstpt     272(%rsp)
        fstpt     256(%rsp)
        jl        ..B1.36
..B1.35:
        movq      $0x8000000000000000, %rax
        cmpq      336(%rsp), %rax
        jne       ..B1.48
..B1.36:
        movb      281(%rsp), %dl
        movb      %dl, %cl
        movb      345(%rsp), %al
        andb      $127, %cl
        shrb      $7, %dl
        shrb      $7, %al
        xorb      %al, %dl
        shlb      $7, %dl
        orb       %dl, %cl
        movb      %cl, 281(%rsp)
        jmp       ..B1.48
..B1.37:
        movzwl    360(%rsp), %r15d
        andl      $32767, %r15d
        cmpl      $32767, %r15d
        jge       ..B1.40
..B1.38:
        fldt      336(%rsp)
        fstpt     32(%rsp)
        fldt      32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     (%rsp)
        call      coshl@PLT
..B1.75:
        fstpt     16(%rsp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fldt      352(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     (%rsp)
        call      cosl@PLT
..B1.74:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fldt      (%rsp)
        testl     %r15d, %r15d
        fmulp     %st, %st(1)
        fstpt     256(%rsp)
        jle       ..B1.59
..B1.39:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      48(%rsp)
        fstpt     (%rsp)
        call      sinhl@PLT
..B1.77:
        fstpt     16(%rsp)
        fldt      32(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.76:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fldt      (%rsp)
        fmulp     %st, %st(1)
        fstpt     272(%rsp)
        jmp       ..B1.48
..B1.40:
        cmpl      $32767, %ebp
        jge       ..B1.42
..B1.41:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      368(%rsp)
        fstpt     (%rsp)
        call      cosl@PLT
        jmp       ..B1.79
	.cfi_def_cfa_offset 336
..B1.42:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      352(%rsp)
        fstpt     (%rsp)
        call      coshl@PLT
..B1.79:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fstpt     256(%rsp)
..B1.43:
        cmpl      $0, 340(%rsp)
        jne       ..B1.45
..B1.44:
        cmpl      $0, 336(%rsp)
        je        ..B1.46
..B1.45:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      368(%rsp)
        fstpt     (%rsp)
        call      sinl@PLT
..B1.80:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fstpt     272(%rsp)
        jmp       ..B1.48
..B1.46:
        movq      $0x8000000000000000, %rax
        cmpq      %rax, %r14
        je        ..B1.62
..B1.47:
        fldt      96(%rsp)
        fstpt     272(%rsp)
..B1.48:
        testb     %bl, %bl
        je        ..B1.50
..B1.49:
        fldcw     114(%rsp)
..B1.50:
        movq      288(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.54
..B1.51:
        fldt      256(%rsp)
        fldt      272(%rsp)
        movq      288(%rsp), %rax
        xorq      %rsp, %rax
        cmpq      %fs:40, %rax
        jne       ..B1.53
..B1.52:
        addq      $296, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
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
	.cfi_def_cfa_offset 336
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 14, -16
	.cfi_offset 15, -24
..B1.53:
        fstp      %st(1)
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B1.54:
        call      __stack_chk_fail@PLT
..B1.55:
        cmpl      $0, 356(%rsp)
        jne       ..B1.11
..B1.56:
        cmpl      $0, 352(%rsp)
        jne       ..B1.11
        jmp       ..B1.33
..B1.57:
        cmpl      $0, 340(%rsp)
        jne       ..B1.9
..B1.58:
        cmpl      $0, 336(%rsp)
        jne       ..B1.9
        jmp       ..B1.33
..B1.59:
        addq      $-16, %rsp
	.cfi_def_cfa_offset 352
        fldt      48(%rsp)
        fstpt     (%rsp)
        call      sinhl@PLT
..B1.81:
        addq      $16, %rsp
	.cfi_def_cfa_offset 336
        fldt      16(%rsp)
        fmulp     %st, %st(1)
        fstpt     272(%rsp)
        jmp       ..B1.48
..B1.62:
        movb      345(%rsp), %dl
        lea       ones(%rip), %rcx
        movb      361(%rsp), %al
        andb      $-128, %dl
        andb      $-128, %al
        pxor      %xmm0, %xmm0
        shrb      $7, %dl
        shrb      $7, %al
        xorb      %al, %dl
        movzbl    %dl, %ebp
        mulsd     (%rcx,%rbp,8), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     272(%rsp)
        jmp       ..B1.48
        .align    16,0x90
	.cfi_endproc
	.type	ccoshl,@function
	.size	ccoshl,.-ccoshl
	.data
# -- End  ccoshl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,16
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,16
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
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
