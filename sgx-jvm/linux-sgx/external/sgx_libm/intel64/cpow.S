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
	.file "cpow.c"
	.text
..TXTST0:
# -- Begin  cpowf
	.text
       .align    16,0x90
	.globl cpowf
cpowf:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_cpowf.1:
..L2:

        pushq     %rsi
	.cfi_def_cfa_offset 16
        movaps    %xmm1, %xmm3
        cvtps2pd  %xmm0, %xmm1
        cvtps2pd  %xmm3, %xmm3
        movaps    %xmm1, %xmm0
        movaps    %xmm3, %xmm2
        unpckhpd  %xmm1, %xmm1
        unpckhpd  %xmm3, %xmm3
..___tag_value_cpowf.4:
        call      cpow@PLT
..___tag_value_cpowf.5:
..B1.5:
        unpcklpd  %xmm1, %xmm0
..B1.2:
        cvtpd2ps  %xmm0, %xmm0
        popq      %rcx
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	cpowf,@function
	.size	cpowf,.-cpowf
	.data
# -- End  cpowf
	.text
# -- Begin  cpow
	.text
       .align    16,0x90
	.globl cpow
cpow:
# parameter 1: %xmm0
# parameter 2: %xmm2
..B2.1:
	.cfi_startproc
..___tag_value_cpow.8:
..L9:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        pushq     %r14
	.cfi_def_cfa_offset 24
	.cfi_offset 14, -24
        xorb      %r14b, %r14b
        subq      $328, %rsp
	.cfi_def_cfa_offset 352
        movsd     %xmm1, 312(%rsp)
        movsd     %xmm0, 304(%rsp)
        movsd     %xmm2, 288(%rsp)
        movsd     %xmm3, 296(%rsp)
..B2.2:
        fnstcw    10(%rsp)
..B2.3:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.7
..B2.4:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B2.5:
        fldcw     8(%rsp)
..B2.6:
        movb      $1, %r14b
..B2.7:
        movsd     304(%rsp), %xmm0
..___tag_value_cpow.15:
        call      __libm_internal_isnzfinite@PLT
..___tag_value_cpow.16:
..B2.8:
        testl     %eax, %eax
        jne       ..B2.29
..B2.9:
        lea       ZEROA(%rip), %r13
        movsd     312(%rsp), %xmm3
        movsd     (%r13), %xmm2
        ucomisd   %xmm2, %xmm3
        jp        ..B2.10
        je        ..B2.25
..B2.10:
        movsd     288(%rsp), %xmm1
        ucomisd   %xmm2, %xmm1
        jp        ..B2.11
        je        ..B2.23
..B2.11:
        ucomisd   %xmm2, %xmm3
        jp        ..B2.12
        je        ..B2.18
..B2.12:
        movsd     296(%rsp), %xmm0
        ucomisd   %xmm2, %xmm0
        jp        ..B2.13
        je        ..B2.17
..B2.13:
        fldl      304(%rsp)
        fstpt     48(%rsp)
        fldl      312(%rsp)
        fstpt     64(%rsp)
        fldt      48(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fstpt     (%rsp)
        call      logl@PLT
..B2.46:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2dl@PLT
..B2.45:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.44:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        lea       _LD_PI_DIV_180(%rip), %rax
        fstpt     208(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.43:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     224(%rsp)
        fldt      208(%rsp)
        fldt      224(%rsp)
        fdivrp    %st, %st(1)
        fstpt     96(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosl@PLT
..B2.42:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     176(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosdl@PLT
..B2.41:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fxch      %st(1)
        fmull     312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sinl@PLT
..B2.40:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     192(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sindl@PLT
..B2.39:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     128(%rsp)
        fldt      176(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      192(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     240(%rsp)
        fldt      192(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      128(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     256(%rsp)
        fldt      240(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      256(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
..B2.14:
        testb     %r14b, %r14b
        je        ..B2.16
..B2.15:
        fldcw     10(%rsp)
..B2.16:
        movsd     272(%rsp), %xmm0
        movsd     280(%rsp), %xmm1
        addq      $328, %rsp
	.cfi_def_cfa_offset 24
	.cfi_restore 14
        popq      %r14
	.cfi_def_cfa_offset 16
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 352
	.cfi_offset 13, -16
	.cfi_offset 14, -24
..B2.17:
        fldl      304(%rsp)
        fstpt     48(%rsp)
        fldl      312(%rsp)
        fstpt     64(%rsp)
        fldt      48(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        movsd     .L_2il0floatpacket.1(%rip), %xmm0
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        mulsd     320(%rsp), %xmm0
        fstpt     (%rsp)
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     16(%rsp)
        call      powl@PLT
..B2.50:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     96(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2dl@PLT
..B2.49:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     80(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosdl@PLT
..B2.48:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     176(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sindl@PLT
..B2.47:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     192(%rsp)
        fldt      96(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      96(%rsp)
        fldt      192(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
        jmp       ..B2.14
..B2.18:
        movsd     304(%rsp), %xmm0
        comisd    %xmm2, %xmm0
        jbe       ..B2.20
..B2.19:
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        movsd     %xmm0, 32(%rsp)
        fldl      32(%rsp)
        fstpt     (%rsp)
        movsd     %xmm1, 32(%rsp)
        fldl      32(%rsp)
        fstpt     16(%rsp)
        call      powl@PLT
..B2.54:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     96(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      320(%rsp)
        fstpt     (%rsp)
        call      logl@PLT
..B2.53:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     32(%rsp)
        fldt      32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosl@PLT
..B2.52:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     176(%rsp)
        fldt      32(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sinl@PLT
..B2.51:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     192(%rsp)
        fldt      176(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      192(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
        jmp       ..B2.14
..B2.20:
        comisd    %xmm0, %xmm2
        jbe       ..B2.22
..B2.21:
        fldl      312(%rsp)
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fchs      
        fstpt     16(%rsp)
        fstpt     64(%rsp)
        fldt      16(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fstpt     (%rsp)
        call      logl@PLT
..B2.62:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2dl@PLT
..B2.61:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     80(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.60:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        lea       _LD_PI_DIV_180(%rip), %rax
        fstpt     208(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.59:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     224(%rsp)
        fldt      208(%rsp)
        fldt      224(%rsp)
        fdivrp    %st, %st(1)
        fstpt     96(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosl@PLT
..B2.58:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     176(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosdl@PLT
..B2.57:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     112(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sinl@PLT
..B2.56:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     192(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sindl@PLT
..B2.55:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     128(%rsp)
        fldt      176(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      192(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     240(%rsp)
        fldt      192(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      128(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     256(%rsp)
        fldt      240(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      256(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
        jmp       ..B2.14
..B2.22:
        movq      304(%rsp), %rax
        movq      312(%rsp), %rdx
        movq      %rax, 272(%rsp)
        movq      %rdx, 280(%rsp)
        jmp       ..B2.14
..B2.23:
        movsd     296(%rsp), %xmm0
        ucomisd   %xmm2, %xmm0
        jne       ..B2.11
        jp        ..B2.11
..B2.24:
        fldl      304(%rsp)
        fstpt     48(%rsp)
        fldl      312(%rsp)
        fstpt     64(%rsp)
        fldt      48(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fstpt     (%rsp)
        call      logl@PLT
..B2.65:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2l@PLT
..B2.64:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     80(%rsp)
        fldt      160(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        faddp     %st, %st(1)
        fstpt     (%rsp)
        call      sinl@PLT
..B2.63:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        movq      $0x3ff0000000000000, %rax
        fstpl     (%rsp)
        movsd     (%rsp), %xmm1
        movsd     (%r13), %xmm0
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
        andps     %xmm2, %xmm1
        andnps    %xmm0, %xmm2
        orps      %xmm1, %xmm2
        movq      %rax, 272(%rsp)
        movsd     %xmm2, 280(%rsp)
        jmp       ..B2.14
..B2.25:
        movsd     296(%rsp), %xmm0
        ucomisd   %xmm2, %xmm0
        jne       ..B2.10
        jp        ..B2.10
..B2.26:
        movsd     304(%rsp), %xmm0
        comisd    %xmm0, %xmm2
        jbe       ..B2.28
..B2.27:
        fldl      312(%rsp)
        movsd     %xmm0, (%rsp)
        fldl      (%rsp)
        fstpt     48(%rsp)
        fldt      48(%rsp)
        fchs      
        fstpt     16(%rsp)
        fstpt     64(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2dl@PLT
..B2.69:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     80(%rsp)
        fldt      16(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fstpt     (%rsp)
        fldl      320(%rsp)
        fstpt     16(%rsp)
        call      powl@PLT
..B2.68:
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fstpt     96(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosdl@PLT
..B2.67:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     112(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sindl@PLT
..B2.66:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     128(%rsp)
        fldt      96(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      96(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
        jmp       ..B2.14
..B2.28:
        movsd     288(%rsp), %xmm1
        movsd     %xmm3, (%rsp)
        call      pow@PLT
..B2.70:
        movsd     (%rsp), %xmm3
        movsd     %xmm0, 272(%rsp)
        movsd     %xmm3, 280(%rsp)
        jmp       ..B2.14
..B2.29:
        movsd     312(%rsp), %xmm0
..___tag_value_cpow.87:
        call      __libm_internal_isnzfinite@PLT
..___tag_value_cpow.88:
..B2.30:
        testl     %eax, %eax
        je        ..B2.9
..B2.31:
        movsd     288(%rsp), %xmm0
..___tag_value_cpow.89:
        call      __libm_internal_isnzfinite@PLT
..___tag_value_cpow.90:
..B2.32:
        testl     %eax, %eax
        je        ..B2.9
..B2.33:
        movsd     296(%rsp), %xmm0
..___tag_value_cpow.91:
        call      __libm_internal_isnzfinite@PLT
..___tag_value_cpow.92:
..B2.34:
        testl     %eax, %eax
        je        ..B2.9
..B2.35:
        fldl      304(%rsp)
        fstpt     48(%rsp)
        fldl      312(%rsp)
        fstpt     64(%rsp)
        fldt      48(%rsp)
        fldt      48(%rsp)
        fmulp     %st, %st(1)
        fldt      64(%rsp)
        fldt      64(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     144(%rsp)
        fldt      144(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fstpt     (%rsp)
        call      logl@PLT
..B2.81:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     160(%rsp)
        fldt      64(%rsp)
        fldt      48(%rsp)
        addq      $-32, %rsp
	.cfi_def_cfa_offset 384
        fxch      %st(1)
        fstpt     (%rsp)
        fstpt     16(%rsp)
        call      atan2dl@PLT
..B2.80:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $32, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     80(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.79:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        lea       _LD_PI_DIV_180(%rip), %rax
        fstpt     208(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(1)
        fldt      (%rax)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      expl@PLT
..B2.78:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     224(%rsp)
        fldt      208(%rsp)
        fldt      224(%rsp)
        fdivrp    %st, %st(1)
        fstpt     96(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      312(%rsp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosl@PLT
..B2.77:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     176(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      cosdl@PLT
..B2.76:
        fldt      .L_2il0floatpacket.2(%rip)
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fxch      %st(1)
        fstpt     112(%rsp)
        fldt      160(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fxch      %st(1)
        fmull     312(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sinl@PLT
..B2.75:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     192(%rsp)
        fldt      80(%rsp)
        addq      $-16, %rsp
	.cfi_def_cfa_offset 368
        fldl      304(%rsp)
        fmulp     %st, %st(1)
        fstpt     (%rsp)
        call      sindl@PLT
..B2.74:
        addq      $16, %rsp
	.cfi_def_cfa_offset 352
        fstpt     128(%rsp)
        fldt      176(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      192(%rsp)
        fldt      128(%rsp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     240(%rsp)
        fldt      192(%rsp)
        fldt      112(%rsp)
        fmulp     %st, %st(1)
        fldt      128(%rsp)
        fldt      176(%rsp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     256(%rsp)
        fldt      240(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     272(%rsp)
        fldt      256(%rsp)
        fldt      96(%rsp)
        fmulp     %st, %st(1)
        fstpl     280(%rsp)
        jmp       ..B2.14
        .align    16,0x90
	.cfi_endproc
	.type	cpow,@function
	.size	cpow,.-cpow
	.data
# -- End  cpow
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 16
ZEROA:
	.long	0
	.long	0
	.type	ZEROA,@object
	.size	ZEROA,8
	.space 8, 0x00 	# pad
	.align 16
_LD_PI_DIV_180:
	.word	51374
	.word	38121
	.word	13586
	.word	36602
	.word	16377
	.word	0
	.word	0
	.word	0
	.type	_LD_PI_DIV_180,@object
	.size	_LD_PI_DIV_180,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x80000000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
