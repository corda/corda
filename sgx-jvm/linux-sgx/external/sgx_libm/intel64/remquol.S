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
	.file "remquol.c"
	.text
..TXTST0:
# -- Begin  remquol
	.text
       .align    16,0x90
	.globl remquol
remquol:
# parameter 1: 80 + %rsp
# parameter 2: 96 + %rsp
# parameter 3: %rdi
..B1.1:
	.cfi_startproc
..___tag_value_remquol.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %rbx
	.cfi_def_cfa_offset 32
	.cfi_offset 3, -32
        pushq     %rbp
	.cfi_def_cfa_offset 40
	.cfi_offset 6, -40
        subq      $40, %rsp
	.cfi_def_cfa_offset 80
        movq      %rdi, %r13
        xorb      %r12b, %r12b
..B1.2:
        fnstcw    34(%rsp)
..B1.3:
        movzwl    104(%rsp), %edx
        movl      $0, (%r13)
        andl      $32767, %edx
        jne       ..B1.6
..B1.4:
        cmpl      $0, 100(%rsp)
        jne       ..B1.6
..B1.5:
        cmpl      $0, 96(%rsp)
        je        ..B1.50
..B1.6:
        movzwl    88(%rsp), %ecx
        andl      $32767, %ecx
        cmpl      $32767, %ecx
        je        ..B1.48
..B1.7:
        cmpl      $32767, %edx
        je        ..B1.25
..B1.8:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        movb      89(%rsp), %bpl
        andl      $3840, %eax
        movb      105(%rsp), %bl
        andb      $-128, %bpl
        andb      $-128, %bl
        shrb      $7, %bpl
        shrb      $7, %bl
        cmpl      $768, %eax
        je        ..B1.12
..B1.9:
        andl      $-3841, %edx
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.10:
        fldcw     32(%rsp)
..B1.11:
        movb      $1, %r12b
..B1.12:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 112
        movq      %r13, %rdi
        fldt      112(%rsp)
        fstpt     (%rsp)
        fldt      128(%rsp)
        fstpt     16(%rsp)
..___tag_value_remquol.13:
        call      __libm_remquol@PLT
..___tag_value_remquol.14:
..B1.63:
        addq      $32, %rsp
	.cfi_def_cfa_offset 80
..B1.13:
        fstpt     16(%rsp)
        testb     %r12b, %r12b
        je        ..B1.15
..B1.14:
        fldcw     34(%rsp)
..B1.15:
        movl      (%r13), %esi
        testl     %esi, %esi
        je        ..B1.19
..B1.16:
        movzbl    %bl, %edx
        lea       iones(%rip), %rax
        movl      (%rax,%rdx,4), %ecx
        imull     %ecx, %esi
        cmpb      %bpl, %bl
        je        ..B1.18
..B1.17:
        orl       $-2147483648, %esi
        movl      %esi, (%r13)
        jmp       ..B1.19
..B1.18:
        andl      $2147483647, %esi
        movl      %esi, (%r13)
..B1.19:
        movzwl    24(%rsp), %eax
        testl     $32767, %eax
        jne       ..B1.24
..B1.20:
        cmpq      $0, 16(%rsp)
        jne       ..B1.22
..B1.21:
        movzbl    %bpl, %edx
        lea       _zeros(%rip), %rax
        fldl      (%rax,%rdx,8)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.22:
        fldt      16(%rsp)
        lea       _small_value_64(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        movsd     %xmm0, 8(%rsp)
..B1.23:
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.24:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.25:
        movq      $0x8000000000000000, %rax
        cmpq      96(%rsp), %rax
        je        ..B1.33
..B1.26:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.32
..B1.27:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.28:
        fldcw     32(%rsp)
..B1.29:
        fldt      80(%rsp)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
..B1.30:
        fldcw     34(%rsp)
..B1.31:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.32:
        fldt      80(%rsp)
        fldt      96(%rsp)
        faddp     %st, %st(1)
        fstpt     16(%rsp)
        jmp       ..B1.31
..B1.33:
        cmpl      $32767, %ecx
        je        ..B1.39
..B1.34:
        testl     %ecx, %ecx
        jne       ..B1.38
..B1.35:
        cmpl      $0, 84(%rsp)
        jne       ..B1.37
..B1.36:
        cmpl      $0, 80(%rsp)
        je        ..B1.38
..B1.37:
        lea       _smallest_value_64(%rip), %rax
        movq      (%rax), %rdx
        movq      %rdx, 8(%rsp)
..B1.38:
        fldt      80(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.39:
        cmpl      $-2147483648, 84(%rsp)
        jne       ..B1.38
..B1.40:
        cmpl      $0, 80(%rsp)
        jne       ..B1.38
..B1.41:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.45
..B1.42:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.43:
        fldcw     32(%rsp)
..B1.44:
        movb      $1, %r12b
..B1.45:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        testb     %r12b, %r12b
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     16(%rsp)
        je        ..B1.47
..B1.46:
        fldcw     34(%rsp)
..B1.47:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.48:
        movq      $0x8000000000000000, %rax
        cmpq      80(%rsp), %rax
        jne       ..B1.26
..B1.49:
        cmpl      $32767, %edx
        je        ..B1.25
        jmp       ..B1.33
..B1.50:
        movzwl    34(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.54
..B1.51:
        orl       $-64768, %edx
        movw      %dx, 32(%rsp)
..B1.52:
        fldcw     32(%rsp)
..B1.53:
        movb      $1, %r12b
..B1.54:
        movzwl    88(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        je        ..B1.59
..B1.55:
        lea       _infs(%rip), %rax
        lea       _zeros(%rip), %rdx
        movsd     (%rax), %xmm0
        mulsd     (%rdx), %xmm0
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     16(%rsp)
..B1.56:
        testb     %r12b, %r12b
        je        ..B1.58
..B1.57:
        fldcw     34(%rsp)
..B1.58:
        fldt      16(%rsp)
        addq      $40, %rsp
	.cfi_def_cfa_offset 40
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 32
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 24
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 80
	.cfi_offset 3, -32
	.cfi_offset 6, -40
	.cfi_offset 12, -16
	.cfi_offset 13, -24
..B1.59:
        movq      $0x8000000000000000, %rax
        cmpq      80(%rsp), %rax
        je        ..B1.55
..B1.60:
        fldt      80(%rsp)
        lea       _ones(%rip), %rax
        fmull     (%rax)
        fstpt     16(%rsp)
        jmp       ..B1.56
        .align    16,0x90
	.cfi_endproc
	.type	remquol,@function
	.size	remquol,.-remquol
	.data
# -- End  remquol
	.text
# -- Begin  __libm_remquol
	.text
       .align    16,0x90
	.globl __libm_remquol
__libm_remquol:
# parameter 1: 208 + %rsp
# parameter 2: 224 + %rsp
# parameter 3: %rdi
..B2.1:
	.cfi_startproc
..___tag_value___libm_remquol.115:
..L116:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %r13
	.cfi_def_cfa_offset 24
	.cfi_offset 13, -24
        pushq     %r14
	.cfi_def_cfa_offset 32
	.cfi_offset 14, -32
        pushq     %r15
	.cfi_def_cfa_offset 40
	.cfi_offset 15, -40
        pushq     %rbx
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -48
        pushq     %rbp
	.cfi_def_cfa_offset 56
	.cfi_offset 6, -56
        subq      $152, %rsp
	.cfi_def_cfa_offset 208
        lea       _ones(%rip), %rax
        fldt      208(%rsp)
        movq      %rdi, %rbx
        andb      $127, 233(%rsp)
        movzwl    216(%rsp), %r15d
        andl      $32767, %r15d
        movzwl    232(%rsp), %r11d
        andl      $32767, %r11d
        fstpt     112(%rsp)
        fldt      112(%rsp)
        cmpl      $32703, %r15d
        fldt      224(%rsp)
        fstpt     128(%rsp)
        fldt      128(%rsp)
        fldl      (%rax)
        movl      $0, 128(%rsp)
        jl        ..B2.8
..B2.2:
        fstp      %st(0)
        cmpl      $32703, %r11d
        jl        ..B2.5
..B2.3:
        lea       8+_scales(%rip), %rax
        movq      %rbx, %rdi
        fldl      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 240
        fxch      %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_remquol.131:
        call      __libm_remquol@PLT
..___tag_value___libm_remquol.132:
..B2.35:
        addq      $32, %rsp
	.cfi_def_cfa_offset 208
..B2.4:
        lea       _scales(%rip), %rax
        fmull     (%rax)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.5:
        lea       8+_scales(%rip), %rax
        addq      $-32, %rsp
	.cfi_def_cfa_offset 240
        movq      %rbx, %rdi
        fldl      (%rax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_remquol.155:
        call      __libm_remquol@PLT
..___tag_value___libm_remquol.156:
..B2.6:
        lea       _scales(%rip), %rax
        movq      %rbx, %rdi
        fldl      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        fldt      256(%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_remquol.157:
        call      __libm_remquol@PLT
..___tag_value___libm_remquol.158:
..B2.37:
        addq      $32, %rsp
	.cfi_def_cfa_offset 208
..B2.7:
        fstpt     112(%rsp)
        fldt      112(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.8:
        cmpl      $64, %r11d
        jge       ..B2.12
..B2.9:
        fstp      %st(0)
        lea       _scales(%rip), %rax
        movq      %rbx, %rdi
        fldl      (%rax)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     224(%rsp)
        fldt      224(%rsp)
        fxch      %st(1)
        fstpt     (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 240
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_remquol.181:
        call      __libm_remquol@PLT
..___tag_value___libm_remquol.182:
..B2.38:
        addq      $32, %rsp
	.cfi_def_cfa_offset 208
..B2.10:
        fldt      (%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 240
        fmulp     %st, %st(1)
        movq      %rbx, %rdi
        fstpt     (%rsp)
        fldt      256(%rsp)
        fstpt     16(%rsp)
..___tag_value___libm_remquol.185:
        call      __libm_remquol@PLT
..___tag_value___libm_remquol.186:
..B2.39:
        addq      $32, %rsp
	.cfi_def_cfa_offset 208
..B2.11:
        lea       8+_scales(%rip), %rax
        fmull     (%rax)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.12:
        lea       1(%r15), %eax
        cmpl      %eax, %r11d
        jg        ..B2.31
..B2.13:
        lea       -32(%r15), %eax
        cmpl      %eax, %r11d
        jle       ..B2.22
..B2.14:
        fstp      %st(0)
        fldt      128(%rsp)
        fld       %st(2)
        fdiv      %st(2), %st
        lea       _TWO_63H(%rip), %rax
        fld       %st(2)
        lea       _halfs(%rip), %rdx
        fsub      %st(2), %st
        fldl      (%rax)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(1)
        fmul      %st, %st(3)
        movl      16(%rsp), %eax
        fxch      %st(3)
        fsubrp    %st, %st(5)
        fmul      %st(2), %st
        fsubrp    %st, %st(4)
        fld       %st(1)
        fsubr     %st(1), %st
        fabs      
        fldl      (%rdx)
        fucomi    %st(1), %st
        fxch      %st(1)
        fstp      %st(0)
        jp        ..B2.15
        je        ..B2.16
..B2.15:
        fstp      %st(3)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      %eax, (%rbx)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.16:
        fmul      %st(3), %st
        fld       %st(4)
        fabs      
        fcomip    %st(1), %st
        fstp      %st(0)
        jp        ..B2.17
        jbe       ..B2.21
..B2.17:
        fcomip    %st(1), %st
        fstp      %st(0)
        ja        ..B2.19
        jp        ..B2.19
..B2.18:
        decl      %eax
        faddp     %st, %st(1)
        movl      %eax, (%rbx)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.19:
        incl      %eax
        fsubrp    %st, %st(1)
        movl      %eax, (%rbx)
..B2.20:
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.21:
        fstp      %st(2)
        fstp      %st(0)
        fstp      %st(0)
        movl      %eax, (%rbx)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.22:
        fldt      128(%rsp)
        subl      %r11d, %r15d
        fstpt     32(%rsp)
        fldt      32(%rsp)
        fld       %st(1)
        fdiv      %st(3), %st
        fld       %st(3)
        fsubp     %st, %st(2)
        xorl      %r10d, %r10d
        addl      $-31, %r15d
        movl      %r15d, %ecx
        jle       ..B2.32
..B2.23:
        lea       _TWO_63H(%rip), %rdi
        fxch      %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      80(%rsp)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fxch      %st(2)
        fstpt     96(%rsp)
        fldl      (%rdi)
        movzwl    8(%rsp), %eax
        movzwl    88(%rsp), %r8d
        andl      $32767, %eax
        movzwl    24(%rsp), %esi
        movl      20(%rsp), %ebp
        movzwl    104(%rsp), %edx
        movzwl    136(%rsp), %r12d
        movl      132(%rsp), %r13d
        movzwl    72(%rsp), %r14d
        movl      4(%rsp), %r9d
        movq      %rbx, 48(%rsp)
        xorl      %ebx, %ebx
..B2.24:
        movl      %esi, %edi
        andl      $-32768, %r8d
        subl      %r15d, %edi
        andl      $-32768, %edx
        andl      $32767, %edi
        andl      $-32768, %r14d
        orl       %edi, %r8d
        lea       (%r12,%r15), %edi
        movw      %r8w, 88(%rsp)
        andl      $32767, %edi
        movl      %ebp, 84(%rsp)
        orl       %edi, %edx
        fldt      80(%rsp)
        movl      $1, %edi
        testl     %eax, %eax
        fmul      %st(5), %st
        cmove     %eax, %edi
        subl      %r15d, %ecx
        imull     %r15d, %edi
        fadd      %st(1), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        addl      %eax, %edi
        movw      %dx, 104(%rsp)
        andl      $32767, %edi
        movl      %r13d, 100(%rsp)
        orl       %edi, %r14d
        movw      %r14w, 72(%rsp)
        fsub      %st(1), %st
        fldt      96(%rsp)
        fmul      %st(1), %st
        movl      %r9d, 68(%rsp)
        fsubrp    %st, %st(6)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        shll      %cl, %r10d
        cmpl      $32, %ecx
        movl      %r15d, %ecx
        fsubrp    %st, %st(5)
        fxch      %st(4)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        cmovae    %ebx, %r10d
        movzwl    120(%rsp), %edi
        andl      $32767, %edi
        subl      %r11d, %edi
        movl      %edi, %r15d
        addl      16(%rsp), %r10d
        addl      $-31, %r15d
        fxch      %st(4)
        jg        ..B2.24
..B2.25:
        movq      48(%rsp), %rbx
..B2.26:
        fldt      32(%rsp)
        fld       %st(1)
        fxch      %st(4)
        fmul      %st(6), %st
        lea       _halfs(%rip), %rdx
        shll      %cl, %r10d
        xorl      %eax, %eax
        cmpl      $32, %ecx
        fadd      %st, %st(4)
        fxch      %st(4)
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fld       %st(3)
        cmovb     %r10d, %eax
        fxch      %st(1)
        fsub      %st(3), %st
        fmul      %st, %st(2)
        addl      16(%rsp), %eax
        fxch      %st(2)
        fsubrp    %st, %st(7)
        fmul      %st(1), %st
        fsubrp    %st, %st(6)
        fld       %st(5)
        fabs      
        fldl      (%rdx)
        fmul      %st(6), %st
        fstpt     (%rsp)
        fldt      (%rsp)
        fcomip    %st(1), %st
        ja        ..B2.30
..B2.27:
        fldt      (%rsp)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B2.29
        jp        ..B2.29
..B2.28:
        fstp      %st(2)
        fstp      %st(0)
        fxch      %st(1)
        fcomip    %st(1), %st
        fstp      %st(0)
        fld       %st(1)
        movl      %eax, (%rbx)
        fsub      %st(1), %st
        fxch      %st(1)
        faddp     %st, %st(2)
        fcmovb    %st(1), %st
        fstp      %st(1)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.29:
        fstp      %st(0)
        fstp      %st(2)
        fldt      32(%rsp)
        fxch      %st(3)
        fdivr     %st(4), %st
        fadd      %st(2), %st
        fstpt     16(%rsp)
        fldt      16(%rsp)
        fsubp     %st, %st(2)
        fxch      %st(1)
        fmul      %st, %st(2)
        addl      16(%rsp), %eax
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fmulp     %st, %st(1)
        movl      %eax, (%rbx)
        fsubrp    %st, %st(1)
        fstpt     112(%rsp)
        fldt      112(%rsp)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.30:
        fstp      %st(5)
        fstp      %st(4)
        fstp      %st(3)
        fstp      %st(1)
        fstp      %st(0)
        fstp      %st(0)
        movl      %eax, (%rbx)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.31:
        fstp      %st(1)
        fstp      %st(0)
        movl      $0, (%rbx)
        addq      $152, %rsp
	.cfi_def_cfa_offset 56
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 48
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 40
	.cfi_restore 15
        popq      %r15
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
        ret       
	.cfi_def_cfa_offset 208
	.cfi_offset 3, -48
	.cfi_offset 6, -56
	.cfi_offset 12, -16
	.cfi_offset 13, -24
	.cfi_offset 14, -32
	.cfi_offset 15, -40
..B2.32:
        fstp      %st(2)
        lea       _TWO_63H(%rip), %rax
        fldl      (%rax)
        jmp       ..B2.26
        .align    16,0x90
	.cfi_endproc
	.type	__libm_remquol,@function
	.size	__libm_remquol,.-__libm_remquol
	.data
# -- End  __libm_remquol
	.section .rodata, "a"
	.align 4
	.align 4
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_smallest_value_64:
	.long	1
	.long	0
	.long	1
	.long	2147483648
	.type	_smallest_value_64,@object
	.size	_smallest_value_64,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_scales:
	.long	0
	.long	1341128704
	.long	0
	.long	804257792
	.type	_scales,@object
	.size	_scales,16
	.align 4
_TWO_63H:
	.long	0
	.long	1139277824
	.type	_TWO_63H,@object
	.size	_TWO_63H,8
	.align 4
_halfs:
	.long	0
	.long	1071644672
	.long	0
	.long	3219128320
	.type	_halfs,@object
	.size	_halfs,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
