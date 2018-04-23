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
	.file "hypotl.c"
	.text
..TXTST0:
# -- Begin  hypotl
	.text
       .align    16,0x90
	.globl hypotl
hypotl:
# parameter 1: 32 + %rsp
# parameter 2: 48 + %rsp
..B1.1:
	.cfi_startproc
..___tag_value_hypotl.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        xorb      %sil, %sil
..B1.2:
        fnstcw    18(%rsp)
..B1.3:
        andb      $127, 41(%rsp)
        andb      $127, 57(%rsp)
        movzwl    40(%rsp), %edx
        andl      $32767, %edx
        movzwl    56(%rsp), %eax
        andl      $32767, %eax
        cmpl      $32767, %edx
        je        ..B1.67
..B1.4:
        cmpl      $32767, %eax
        je        ..B1.67
..B1.5:
        movl      %edx, %ecx
        subl      %eax, %ecx
        lea       64(%rcx), %edi
        cmpl      $128, %edi
        ja        ..B1.25
..B1.6:
        lea       (%rdx,%rax), %edi
        cmpl      $18376, %edi
        jge       ..B1.35
..B1.7:
        testl     %edx, %edx
        jne       ..B1.12
..B1.8:
        cmpq      $0, 32(%rsp)
        jne       ..B1.12
..B1.9:
        testl     %eax, %eax
        jne       ..B1.12
..B1.10:
        cmpq      $0, 48(%rsp)
        jne       ..B1.12
..B1.11:
        lea       _zeros(%rip), %rax
        fldl      (%rax)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.12:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.16
..B1.13:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.14:
        fldcw     16(%rsp)
..B1.15:
        movb      $1, %sil
..B1.16:
        fldt      32(%rsp)
        lea       _scales(%rip), %rax
        fldt      48(%rsp)
        lea       4(%rcx), %edx
        cmpl      $8, %edx
        fldt      (%rax)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        ja        ..B1.20
..B1.17:
        lea       _TWO_48H(%rip), %rax
        fld       %st(1)
        fld       %st(2)
        fld       %st(3)
        fld       %st(3)
        lea       _TWO_32H(%rip), %rdx
        fxch      %st(5)
        fcomi     %st(4), %st
        fldl      (%rax)
        fmul      %st, %st(4)
        fxch      %st(3)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fld       %st(5)
        fxch      %st(4)
        fmul      %st(6), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(4)
        fsubr     %st, %st(7)
        fxch      %st(3)
        fmul      %st(7), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(3)
        fmul      %st(0), %st
        fadd      %st, %st(3)
        fldl      (%rdx)
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        jbe       ..B1.19
..B1.18:
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        jmp       ..B1.22
..B1.19:
        fxch      %st(2)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        jmp       ..B1.22
..B1.20:
        lea       _TWO_48H(%rip), %rax
        lea       _TWO_32H(%rip), %rdx
        testl     %ecx, %ecx
        fldl      (%rax)
        fldl      (%rdx)
        jle       ..B1.34
..B1.21:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
..B1.22:
        fld       %st(0)
        lea       16+_scales(%rip), %rax
        testb     %sil, %sil
        fadd      %st(2), %st
        fsqrt     
        fmul      %st, %st(3)
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fmul      %st(4), %st
        fsubrp    %st, %st(2)
        fld       %st(3)
        fxch      %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(2), %st
        fdivrp    %st, %st(1)
        faddp     %st, %st(1)
        fldt      .L_2il0floatpacket.0(%rip)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.24
..B1.23:
        fldcw     18(%rsp)
..B1.24:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.25:
        movzwl    18(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.29
..B1.26:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.27:
        fldcw     16(%rsp)
..B1.28:
        movb      $1, %sil
..B1.29:
        fldt      32(%rsp)
        lea       16+_ranges(%rip), %rax
        fldt      48(%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        jae       ..B1.31
        jp        ..B1.31
..B1.30:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
..B1.31:
        testb     %sil, %sil
        je        ..B1.33
..B1.32:
        fldcw     18(%rsp)
..B1.33:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.34:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        jmp       ..B1.22
..B1.35:
        movzwl    18(%rsp), %edx
        cmpl      $47156, %edi
        jle       ..B1.51
..B1.36:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.40
..B1.37:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.38:
        fldcw     16(%rsp)
..B1.39:
        movb      $1, %sil
..B1.40:
        fldt      32(%rsp)
        lea       16+_scales(%rip), %rax
        fldt      48(%rsp)
        lea       4(%rcx), %edx
        cmpl      $8, %edx
        fldt      (%rax)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        ja        ..B1.44
..B1.41:
        lea       _TWO_48H(%rip), %rax
        fld       %st(1)
        fld       %st(2)
        fld       %st(3)
        fld       %st(3)
        lea       _TWO_32H(%rip), %rdx
        fxch      %st(5)
        fcomi     %st(4), %st
        fldl      (%rax)
        fmul      %st, %st(4)
        fxch      %st(3)
        fadd      %st(4), %st
        fsubp     %st, %st(4)
        fld       %st(3)
        fld       %st(5)
        fxch      %st(4)
        fmul      %st(6), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        fld       %st(3)
        fxch      %st(3)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(4)
        fsubr     %st, %st(7)
        fxch      %st(3)
        fmul      %st(7), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(3)
        fmul      %st(0), %st
        fadd      %st, %st(3)
        fldl      (%rdx)
        fld       %st(0)
        fmul      %st(5), %st
        fadd      %st, %st(5)
        fsubrp    %st, %st(5)
        jbe       ..B1.43
..B1.42:
        fxch      %st(1)
        fsubr     %st(4), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        jmp       ..B1.46
..B1.43:
        fxch      %st(2)
        fsubr     %st(4), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        jmp       ..B1.46
..B1.44:
        lea       _TWO_48H(%rip), %rax
        lea       _TWO_32H(%rip), %rdx
        testl     %ecx, %ecx
        fldl      (%rax)
        fldl      (%rdx)
        jle       ..B1.65
..B1.45:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
..B1.46:
        fldt      .L_2il0floatpacket.0(%rip)
        fld       %st(1)
        lea       _ranges(%rip), %rax
        fadd      %st(3), %st
        fsqrt     
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(3)
        fld       %st(4)
        fxch      %st(1)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(3)
        fadd      %st(3), %st
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        fldt      (%rsp)
        fldt      (%rax)
        fcomip    %st(1), %st
        fstp      %st(0)
        jp        ..B1.47
        jbe       ..B1.64
..B1.47:
        fldt      (%rsp)
        lea       _scales(%rip), %rax
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
..B1.48:
        testb     %sil, %sil
        je        ..B1.50
..B1.49:
        fldcw     18(%rsp)
..B1.50:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.51:
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.55
..B1.52:
        orl       $-64768, %edx
        movw      %dx, 16(%rsp)
..B1.53:
        fldcw     16(%rsp)
..B1.54:
        movb      $1, %sil
..B1.55:
        lea       4(%rcx), %eax
        cmpl      $8, %eax
        ja        ..B1.59
..B1.56:
        fldt      32(%rsp)
        lea       _TWO_48H(%rip), %rax
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        lea       _TWO_32H(%rip), %rdx
        fldt      48(%rsp)
        fld       %st(0)
        fxch      %st(5)
        fcomi     %st(1), %st
        fldl      (%rax)
        fmul      %st, %st(5)
        fxch      %st(4)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fld       %st(2)
        fxch      %st(3)
        fmul      %st, %st(5)
        fxch      %st(3)
        fadd      %st(5), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fxch      %st(4)
        fsub      %st(6), %st
        fmul      %st, %st(1)
        fxch      %st(5)
        fsubr     %st, %st(7)
        fxch      %st(4)
        fmul      %st(7), %st
        fxch      %st(2)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmul      %st(0), %st
        fld       %st(0)
        fxch      %st(2)
        fmul      %st(0), %st
        fadd      %st, %st(2)
        fldl      (%rdx)
        fld       %st(0)
        fmul      %st(4), %st
        fadd      %st, %st(4)
        fsubrp    %st, %st(4)
        jbe       ..B1.58
..B1.57:
        fxch      %st(1)
        fsubr     %st(3), %st
        fsubrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.61
..B1.58:
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubrp    %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fxch      %st(1)
        jmp       ..B1.61
..B1.59:
        fldt      32(%rsp)
        lea       _TWO_48H(%rip), %rax
        fldt      48(%rsp)
        lea       _TWO_32H(%rip), %rdx
        testl     %ecx, %ecx
        fldl      (%rax)
        fldl      (%rdx)
        jle       ..B1.66
..B1.60:
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(3)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(4)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(5)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st(0), %st
        fxch      %st(2)
        fxch      %st(1)
        fxch      %st(2)
..B1.61:
        fldt      .L_2il0floatpacket.0(%rip)
        fld       %st(1)
        testb     %sil, %sil
        fadd      %st(3), %st
        fsqrt     
        fmul      %st, %st(4)
        fld       %st(4)
        fadd      %st(1), %st
        fsubp     %st, %st(5)
        fld       %st(4)
        fmul      %st(5), %st
        fsubrp    %st, %st(3)
        fld       %st(4)
        fxch      %st(1)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(3)
        fadd      %st(3), %st
        fdivrp    %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%rsp)
        je        ..B1.63
..B1.62:
        fldcw     18(%rsp)
..B1.63:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.64:
        lea       _large_value_80(%rip), %rax
        fldt      (%rax)
        fmul      %st(0), %st
        fstpt     (%rsp)
        jmp       ..B1.48
..B1.65:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        jmp       ..B1.46
..B1.66:
        fxch      %st(1)
        fmul      %st(2), %st
        fld       %st(2)
        fadd      %st(1), %st
        fsubp     %st, %st(1)
        fld       %st(0)
        fld       %st(3)
        fsub      %st(2), %st
        fmul      %st, %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        jmp       ..B1.61
..B1.67:
        movzwl    18(%rsp), %edi
        movl      %edi, %ecx
        andl      $768, %ecx
        cmpl      $768, %ecx
        je        ..B1.71
..B1.68:
        orl       $-64768, %edi
        movw      %di, 16(%rsp)
..B1.69:
        fldcw     16(%rsp)
..B1.70:
        movb      $1, %sil
..B1.71:
        cmpl      $32767, %edx
        je        ..B1.87
..B1.72:
        cmpl      $32767, %eax
        je        ..B1.85
..B1.73:
        fldt      32(%rsp)
        fldt      48(%rsp)
        faddp     %st, %st(1)
        fstpt     (%rsp)
..B1.74:
        testb     %sil, %sil
        je        ..B1.76
..B1.75:
        fldcw     18(%rsp)
..B1.76:
        cmpl      $32767, %edx
        je        ..B1.82
..B1.77:
        cmpl      $32767, %eax
        jne       ..B1.81
..B1.78:
        cmpl      $-2147483648, 52(%rsp)
        jne       ..B1.81
..B1.79:
        cmpl      $0, 48(%rsp)
        jne       ..B1.81
..B1.80:
        fldt      48(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.81:
        fldt      (%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.82:
        cmpl      $-2147483648, 36(%rsp)
        jne       ..B1.77
..B1.83:
        cmpl      $0, 32(%rsp)
        jne       ..B1.77
..B1.84:
        fldt      32(%rsp)
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 32
..B1.85:
        movq      $0x8000000000000000, %rcx
        cmpq      48(%rsp), %rcx
        je        ..B1.73
..B1.86:
        fldt      32(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        jmp       ..B1.74
..B1.87:
        movq      $0x8000000000000000, %rcx
        cmpq      32(%rsp), %rcx
        jne       ..B1.86
        jmp       ..B1.72
        .align    16,0x90
	.cfi_endproc
	.type	hypotl,@function
	.size	hypotl,.-hypotl
	.data
# -- End  hypotl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_TWO_48H:
	.long	0
	.long	1123549184
	.type	_TWO_48H,@object
	.size	_TWO_48H,8
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 2
_scales:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24653
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8113
	.word	0
	.word	0
	.word	0
	.type	_scales,@object
	.size	_scales,32
	.align 2
_ranges:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	24497
	.word	0
	.word	0
	.word	0
	.word	65535
	.word	65535
	.word	65535
	.word	65535
	.word	32766
	.word	0
	.word	0
	.word	0
	.type	_ranges,@object
	.size	_ranges,32
	.align 2
_large_value_80:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	26383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	59151
	.word	0
	.word	0
	.word	0
	.type	_large_value_80,@object
	.size	_large_value_80,32
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
