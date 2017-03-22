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
	.file "erfc.c"
	.text
..TXTST0:
# -- Begin  erfc
	.text
       .align    16,0x90
	.globl erfc
erfc:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_erfc.1:
..L2:

        movaps    %xmm0, %xmm1
        movsd     %xmm1, -8(%rsp)
        movzwl    -2(%rsp), %eax
        andl      $32752, %eax
        shrl      $4, %eax
        cmpl      $2047, %eax
        je        ..B1.18
..B1.2:
        cmpl      $953, %eax
        jl        ..B1.16
..B1.3:
        lea       5016+_vmldErfcHATab(%rip), %rax
        movb      -1(%rsp), %dil
        comisd    (%rax), %xmm1
        jbe       ..B1.15
..B1.4:
        lea       4992+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm0
        comisd    %xmm1, %xmm0
        jbe       ..B1.25
..B1.5:
        lea       4968+_vmldErfcHATab(%rip), %rcx
        movb      %dil, %sil
        movsd     %xmm1, -48(%rsp)
        andb      $127, %sil
        movb      %sil, -41(%rsp)
        movsd     -48(%rsp), %xmm3
        movsd     (%rcx), %xmm2
        movaps    %xmm3, %xmm7
        addsd     %xmm2, %xmm7
        mulsd     %xmm7, %xmm7
        mulsd     %xmm7, %xmm7
        movsd     %xmm7, -16(%rsp)
        movzwl    -10(%rsp), %edx
        andl      $32752, %edx
        shrl      $4, %edx
        lea       -1023(%rdx), %esi
        lea       -3069(%rdx,%rdx,2), %ecx
        lea       _vmldErfcHATab(%rip), %rdx
        lea       (,%rcx,8), %eax
        lea       1(,%rcx,8), %r9d
        lea       23(,%rcx,8), %r10d
        movsd     (%rdx,%rax,8), %xmm12
        lea       22(,%rcx,8), %r11d
        mulsd     %xmm3, %xmm12
        lea       21(,%rcx,8), %eax
        movsd     (%rdx,%r9,8), %xmm4
        movaps    %xmm12, %xmm6
        addsd     %xmm12, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        subsd     %xmm5, %xmm6
        movsd     %xmm6, -32(%rsp)
        movsd     -40(%rsp), %xmm9
        movsd     -32(%rsp), %xmm8
        addsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -32(%rsp), %xmm10
        addsd     (%rdx,%r9,8), %xmm10
        movsd     %xmm10, -32(%rsp)
        lea       20(,%rcx,8), %r9d
        movsd     -24(%rsp), %xmm11
        subsd     %xmm11, %xmm12
        movsd     (%rdx,%r10,8), %xmm11
        movsd     %xmm12, -24(%rsp)
        lea       19(,%rcx,8), %r10d
        movsd     -32(%rsp), %xmm14
        movsd     -24(%rsp), %xmm13
        addsd     %xmm13, %xmm14
        movsd     %xmm14, -24(%rsp)
        movsd     -40(%rsp), %xmm5
        mulsd     %xmm5, %xmm11
        movaps    %xmm5, %xmm0
        movaps    %xmm5, %xmm15
        addsd     (%rdx,%r11,8), %xmm11
        mulsd     %xmm5, %xmm15
        mulsd     %xmm5, %xmm11
        mulsd     %xmm15, %xmm15
        addsd     (%rdx,%rax,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       18(,%rcx,8), %eax
        movsd     -24(%rsp), %xmm6
        lea       16(,%rcx,8), %r11d
        movsd     %xmm15, -16(%rsp)
        addsd     (%rdx,%r9,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       17(,%rcx,8), %r9d
        addsd     (%rdx,%r10,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       12(,%rcx,8), %r10d
        movsd     (%rdx,%r10,8), %xmm10
        lea       8(,%rcx,8), %r10d
        movaps    %xmm10, %xmm8
        addsd     (%rdx,%rax,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       15(,%rcx,8), %eax
        addsd     (%rdx,%r9,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       14(,%rcx,8), %r9d
        addsd     (%rdx,%r11,8), %xmm11
        mulsd     %xmm5, %xmm11
        lea       10(,%rcx,8), %r11d
        addsd     (%rdx,%rax,8), %xmm11
        lea       4944+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm7
        mulsd     %xmm7, %xmm0
        mulsd     %xmm5, %xmm11
        movsd     %xmm0, -40(%rsp)
        movsd     -40(%rsp), %xmm1
        subsd     %xmm5, %xmm1
        addsd     (%rdx,%r9,8), %xmm11
        movsd     %xmm1, -32(%rsp)
        lea       13(,%rcx,8), %r9d
        movsd     -40(%rsp), %xmm3
        movsd     -32(%rsp), %xmm15
        mulsd     %xmm5, %xmm11
        subsd     %xmm15, %xmm3
        movsd     %xmm3, -40(%rsp)
        addsd     %xmm11, %xmm8
        movsd     -40(%rsp), %xmm4
        movsd     (%rdx,%r11,8), %xmm15
        lea       6(,%rcx,8), %r11d
        subsd     %xmm4, %xmm5
        movsd     %xmm5, -32(%rsp)
        movsd     -40(%rsp), %xmm0
        movsd     -32(%rsp), %xmm1
        movsd     %xmm8, -40(%rsp)
        addsd     %xmm6, %xmm1
        movsd     -40(%rsp), %xmm9
        subsd     %xmm9, %xmm10
        addsd     %xmm10, %xmm11
        movsd     %xmm11, -32(%rsp)
        movsd     -40(%rsp), %xmm3
        movaps    %xmm3, %xmm12
        mulsd     %xmm7, %xmm12
        movsd     -32(%rsp), %xmm4
        movsd     %xmm12, -40(%rsp)
        movsd     -40(%rsp), %xmm13
        subsd     %xmm3, %xmm13
        addsd     (%rdx,%r9,8), %xmm4
        movsd     %xmm13, -32(%rsp)
        lea       11(,%rcx,8), %r9d
        movsd     -40(%rsp), %xmm12
        movsd     -32(%rsp), %xmm14
        subsd     %xmm14, %xmm12
        movsd     %xmm12, -40(%rsp)
        movsd     -40(%rsp), %xmm13
        subsd     %xmm13, %xmm3
        movsd     %xmm3, -32(%rsp)
        movsd     -40(%rsp), %xmm5
        movsd     -32(%rsp), %xmm6
        movaps    %xmm5, %xmm11
        mulsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm6
        mulsd     %xmm0, %xmm11
        movaps    %xmm6, %xmm8
        movaps    %xmm11, %xmm9
        mulsd     %xmm1, %xmm8
        addsd     %xmm15, %xmm9
        mulsd     %xmm0, %xmm6
        addsd     %xmm5, %xmm8
        addsd     %xmm6, %xmm8
        movsd     %xmm8, -40(%rsp)
        movsd     -40(%rsp), %xmm14
        movsd     %xmm9, -40(%rsp)
        movsd     -40(%rsp), %xmm10
        subsd     %xmm10, %xmm15
        addsd     %xmm11, %xmm15
        movsd     %xmm15, -32(%rsp)
        movsd     -40(%rsp), %xmm8
        movaps    %xmm8, %xmm3
        mulsd     %xmm7, %xmm3
        movsd     -32(%rsp), %xmm11
        movsd     %xmm3, -40(%rsp)
        movaps    %xmm0, %xmm3
        movsd     -40(%rsp), %xmm4
        addsd     %xmm14, %xmm11
        subsd     %xmm8, %xmm4
        addsd     (%rdx,%r9,8), %xmm11
        movsd     %xmm4, -32(%rsp)
        lea       9(,%rcx,8), %r9d
        movsd     -40(%rsp), %xmm5
        movsd     -32(%rsp), %xmm14
        movsd     (%rdx,%r10,8), %xmm4
        lea       4(,%rcx,8), %r10d
        subsd     %xmm14, %xmm5
        movsd     %xmm5, -40(%rsp)
        movsd     -40(%rsp), %xmm6
        subsd     %xmm6, %xmm8
        movsd     %xmm8, -32(%rsp)
        movsd     -40(%rsp), %xmm10
        movsd     -32(%rsp), %xmm9
        mulsd     %xmm10, %xmm3
        addsd     %xmm9, %xmm11
        mulsd     %xmm1, %xmm10
        movaps    %xmm11, %xmm12
        movaps    %xmm3, %xmm13
        mulsd     %xmm1, %xmm12
        addsd     %xmm4, %xmm13
        mulsd     %xmm0, %xmm11
        addsd     %xmm10, %xmm12
        addsd     %xmm11, %xmm12
        movsd     %xmm12, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        movsd     %xmm13, -40(%rsp)
        movsd     -40(%rsp), %xmm15
        subsd     %xmm15, %xmm4
        addsd     %xmm3, %xmm4
        movsd     %xmm4, -32(%rsp)
        movsd     -40(%rsp), %xmm12
        movaps    %xmm12, %xmm6
        mulsd     %xmm7, %xmm6
        movsd     -32(%rsp), %xmm3
        movsd     %xmm6, -40(%rsp)
        movaps    %xmm0, %xmm6
        movsd     -40(%rsp), %xmm8
        addsd     %xmm5, %xmm3
        subsd     %xmm12, %xmm8
        addsd     (%rdx,%r9,8), %xmm3
        movsd     %xmm8, -32(%rsp)
        lea       7(,%rcx,8), %r9d
        movsd     -40(%rsp), %xmm10
        movsd     -32(%rsp), %xmm9
        movsd     (%rdx,%r11,8), %xmm8
        lea       2(,%rcx,8), %r11d
        subsd     %xmm9, %xmm10
        movsd     %xmm10, -40(%rsp)
        movsd     -40(%rsp), %xmm11
        subsd     %xmm11, %xmm12
        movsd     %xmm12, -32(%rsp)
        movsd     -40(%rsp), %xmm14
        movsd     -32(%rsp), %xmm13
        mulsd     %xmm14, %xmm6
        addsd     %xmm13, %xmm3
        mulsd     %xmm1, %xmm14
        movaps    %xmm3, %xmm4
        movaps    %xmm6, %xmm5
        mulsd     %xmm1, %xmm4
        addsd     %xmm8, %xmm5
        mulsd     %xmm0, %xmm3
        addsd     %xmm14, %xmm4
        addsd     %xmm3, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm3
        movsd     %xmm5, -40(%rsp)
        movsd     -40(%rsp), %xmm15
        subsd     %xmm15, %xmm8
        addsd     %xmm6, %xmm8
        movsd     %xmm8, -32(%rsp)
        movaps    %xmm0, %xmm8
        movsd     -40(%rsp), %xmm12
        movaps    %xmm12, %xmm9
        mulsd     %xmm7, %xmm9
        movsd     -32(%rsp), %xmm6
        movsd     %xmm9, -40(%rsp)
        addsd     %xmm3, %xmm6
        movsd     -40(%rsp), %xmm10
        movsd     (%rdx,%r10,8), %xmm9
        subsd     %xmm12, %xmm10
        addsd     (%rdx,%r9,8), %xmm6
        movsd     %xmm10, -32(%rsp)
        lea       5(,%rcx,8), %r9d
        movsd     -40(%rsp), %xmm11
        movsd     -32(%rsp), %xmm4
        subsd     %xmm4, %xmm11
        movsd     %xmm11, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        subsd     %xmm5, %xmm12
        movsd     %xmm12, -32(%rsp)
        movsd     -40(%rsp), %xmm3
        movsd     -32(%rsp), %xmm13
        mulsd     %xmm3, %xmm8
        addsd     %xmm13, %xmm6
        mulsd     %xmm1, %xmm3
        movaps    %xmm6, %xmm4
        movaps    %xmm8, %xmm5
        mulsd     %xmm1, %xmm4
        addsd     %xmm9, %xmm5
        mulsd     %xmm0, %xmm6
        addsd     %xmm3, %xmm4
        addsd     %xmm6, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm15
        movsd     %xmm5, -40(%rsp)
        movsd     -40(%rsp), %xmm14
        subsd     %xmm14, %xmm9
        addsd     %xmm8, %xmm9
        movsd     %xmm9, -32(%rsp)
        movsd     -40(%rsp), %xmm4
        movaps    %xmm4, %xmm10
        mulsd     %xmm7, %xmm10
        movsd     -32(%rsp), %xmm8
        movsd     %xmm10, -40(%rsp)
        movaps    %xmm0, %xmm10
        movsd     -40(%rsp), %xmm11
        addsd     %xmm15, %xmm8
        subsd     %xmm4, %xmm11
        addsd     (%rdx,%r9,8), %xmm8
        movsd     %xmm11, -32(%rsp)
        movsd     -40(%rsp), %xmm12
        movsd     -32(%rsp), %xmm15
        subsd     %xmm15, %xmm12
        movsd     %xmm12, -40(%rsp)
        movsd     -40(%rsp), %xmm3
        subsd     %xmm3, %xmm4
        movsd     (%rdx,%r11,8), %xmm3
        movsd     %xmm4, -32(%rsp)
        movsd     -40(%rsp), %xmm6
        movsd     -32(%rsp), %xmm5
        mulsd     %xmm6, %xmm10
        addsd     %xmm5, %xmm8
        movaps    %xmm8, %xmm9
        mulsd     %xmm1, %xmm9
        mulsd     %xmm6, %xmm1
        mulsd     %xmm8, %xmm0
        addsd     %xmm1, %xmm9
        addsd     %xmm0, %xmm9
        movaps    %xmm10, %xmm0
        addsd     %xmm3, %xmm0
        movsd     %xmm9, -40(%rsp)
        movsd     -40(%rsp), %xmm4
        movsd     %xmm0, -40(%rsp)
        movsd     -40(%rsp), %xmm1
        subsd     %xmm1, %xmm3
        addsd     %xmm10, %xmm3
        movsd     %xmm3, -32(%rsp)
        movsd     -40(%rsp), %xmm6
        movsd     -32(%rsp), %xmm5
        addsd     %xmm4, %xmm5
..B1.28:
        movaps    %xmm6, %xmm1
        lea       3(,%rcx,8), %ecx
        movsd     (%rdx,%rcx,8), %xmm0
        cmpl      $6, %esi
        addsd     %xmm0, %xmm5
        addsd     %xmm5, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm3
        subsd     %xmm3, %xmm6
        addsd     %xmm5, %xmm6
        movsd     %xmm6, -32(%rsp)
        movsd     -40(%rsp), %xmm1
        movsd     -32(%rsp), %xmm0
        jae       ..B1.7
..B1.6:
        movsd     %xmm2, -48(%rsp)
        jmp       ..B1.8
..B1.7:
        movaps    %xmm7, %xmm8
        lea       4976+_vmldErfcHATab(%rip), %r8
        mulsd     %xmm1, %xmm8
        lea       4920+_vmldErfcHATab(%rip), %rcx
        movsd     %xmm8, -40(%rsp)
        lea       4936+_vmldErfcHATab(%rip), %rsi
        movsd     -40(%rsp), %xmm9
        lea       4904+_vmldErfcHATab(%rip), %r9
        movsd     -8(%rsp), %xmm4
        lea       4912+_vmldErfcHATab(%rip), %r10
        movaps    %xmm4, %xmm14
        lea       3872+_vmldErfcHATab(%rip), %r11
        mulsd     (%rax), %xmm14
        subsd     %xmm1, %xmm9
        movsd     %xmm9, -32(%rsp)
        movsd     -40(%rsp), %xmm11
        movsd     -32(%rsp), %xmm10
        subsd     %xmm10, %xmm11
        movsd     %xmm11, -40(%rsp)
        movsd     -40(%rsp), %xmm12
        movsd     (%rcx), %xmm11
        lea       3864+_vmldErfcHATab(%rip), %rcx
        subsd     %xmm12, %xmm1
        movsd     (%r9), %xmm12
        movsd     %xmm1, -32(%rsp)
        lea       3848+_vmldErfcHATab(%rip), %r9
        movsd     -40(%rsp), %xmm6
        movsd     -32(%rsp), %xmm13
        movsd     %xmm14, -40(%rsp)
        addsd     %xmm13, %xmm0
        movsd     -40(%rsp), %xmm15
        subsd     -8(%rsp), %xmm15
        movsd     %xmm15, -32(%rsp)
        movsd     -40(%rsp), %xmm1
        movsd     -32(%rsp), %xmm2
        subsd     %xmm2, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm3
        subsd     %xmm3, %xmm4
        movsd     %xmm4, -32(%rsp)
        movsd     -40(%rsp), %xmm8
        movaps    %xmm8, %xmm5
        mulsd     %xmm8, %xmm5
        movsd     -32(%rsp), %xmm9
        mulsd     %xmm9, %xmm8
        mulsd     %xmm9, %xmm9
        movsd     (%r8), %xmm4
        addsd     %xmm8, %xmm9
        mulsd     %xmm4, %xmm5
        addsd     %xmm8, %xmm9
        mulsd     %xmm5, %xmm11
        movsd     %xmm9, -40(%rsp)
        movsd     -40(%rsp), %xmm10
        mulsd     %xmm10, %xmm4
        addsd     (%rsi), %xmm11
        movsd     %xmm11, -56(%rsp)
        movsd     -56(%rsp), %xmm3
        movl      -56(%rsp), %r8d
        movl      %r8d, %eax
        andl      $63, %eax
        subsd     (%rsi), %xmm3
        mulsd     %xmm3, %xmm12
        lea       3856+_vmldErfcHATab(%rip), %rsi
        xorps     .L_2il0floatpacket.166(%rip), %xmm3
        subsd     %xmm12, %xmm5
        mulsd     (%r10), %xmm3
        movaps    %xmm5, %xmm13
        movaps    %xmm5, %xmm2
        lea       3840+_vmldErfcHATab(%rip), %r10
        addsd     %xmm3, %xmm13
        movsd     %xmm13, -40(%rsp)
        movsd     -40(%rsp), %xmm14
        shrl      $6, %r8d
        subsd     %xmm14, %xmm2
        movsd     %xmm2, -32(%rsp)
        addl      $1023, %r8d
        movsd     -40(%rsp), %xmm1
        movsd     -32(%rsp), %xmm15
        addsd     %xmm15, %xmm1
        movsd     %xmm1, -24(%rsp)
        movsd     -32(%rsp), %xmm2
        addsd     %xmm2, %xmm3
        movsd     %xmm3, -32(%rsp)
        movsd     -24(%rsp), %xmm8
        subsd     %xmm8, %xmm5
        movsd     %xmm5, -24(%rsp)
        movsd     -32(%rsp), %xmm2
        movsd     -24(%rsp), %xmm5
        addsd     %xmm5, %xmm2
        movsd     %xmm2, -24(%rsp)
        movsd     -40(%rsp), %xmm12
        movaps    %xmm12, %xmm1
        movaps    %xmm12, %xmm8
        movsd     -24(%rsp), %xmm3
        addsd     %xmm4, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        subsd     %xmm5, %xmm8
        movsd     (%r11), %xmm5
        movsd     %xmm8, -32(%rsp)
        movaps    %xmm7, %xmm8
        movsd     -40(%rsp), %xmm10
        lea       485(%rax,%rax), %r11d
        movsd     -32(%rsp), %xmm9
        lea       486(%rax,%rax), %eax
        addsd     %xmm9, %xmm10
        movsd     %xmm10, -24(%rsp)
        movsd     -32(%rsp), %xmm11
        addsd     %xmm11, %xmm4
        movsd     %xmm4, -32(%rsp)
        movsd     -24(%rsp), %xmm4
        subsd     %xmm4, %xmm12
        movsd     %xmm12, -24(%rsp)
        movsd     -32(%rsp), %xmm14
        movsd     -24(%rsp), %xmm13
        addsd     %xmm13, %xmm14
        movsd     %xmm14, -24(%rsp)
        movsd     -40(%rsp), %xmm4
        mulsd     %xmm4, %xmm5
        movaps    %xmm4, %xmm2
        movsd     -24(%rsp), %xmm15
        addsd     (%rcx), %xmm5
        movl      %r8d, %ecx
        addsd     %xmm15, %xmm3
        mulsd     %xmm4, %xmm5
        andl      $2047, %ecx
        addsd     (%rsi), %xmm5
        mulsd     %xmm4, %xmm5
        shll      $4, %ecx
        addsd     (%r9), %xmm5
        mulsd     %xmm4, %xmm5
        addsd     (%r10), %xmm5
        mulsd     %xmm4, %xmm5
        mulsd     %xmm4, %xmm5
        addsd     %xmm5, %xmm2
        movsd     %xmm2, -32(%rsp)
        movsd     -32(%rsp), %xmm1
        subsd     %xmm1, %xmm5
        addsd     %xmm4, %xmm5
        movsd     %xmm5, -24(%rsp)
        movsd     -32(%rsp), %xmm13
        mulsd     %xmm13, %xmm8
        movsd     -24(%rsp), %xmm1
        movsd     %xmm8, -40(%rsp)
        movsd     -40(%rsp), %xmm9
        movsd     (%rdx,%r11,8), %xmm5
        subsd     %xmm13, %xmm9
        movsd     %xmm9, -32(%rsp)
        movsd     -40(%rsp), %xmm11
        movsd     -32(%rsp), %xmm10
        subsd     %xmm10, %xmm11
        movsd     %xmm11, -40(%rsp)
        movsd     -40(%rsp), %xmm12
        subsd     %xmm12, %xmm13
        movaps    %xmm5, %xmm12
        movsd     %xmm13, -32(%rsp)
        movsd     -40(%rsp), %xmm2
        movsd     -32(%rsp), %xmm14
        mulsd     %xmm2, %xmm12
        addsd     %xmm14, %xmm1
        movaps    %xmm12, %xmm8
        addsd     %xmm3, %xmm1
        movsd     (%rdx,%rax,8), %xmm3
        movaps    %xmm3, %xmm4
        mulsd     %xmm1, %xmm4
        mulsd     %xmm5, %xmm1
        addsd     %xmm12, %xmm5
        mulsd     %xmm3, %xmm2
        addsd     %xmm1, %xmm4
        addsd     %xmm2, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm2
        movsd     %xmm5, -40(%rsp)
        movsd     -40(%rsp), %xmm1
        subsd     %xmm1, %xmm8
        movaps    %xmm7, %xmm1
        movsd     %xmm8, -32(%rsp)
        movsd     -40(%rsp), %xmm9
        movsd     -32(%rsp), %xmm3
        addsd     %xmm3, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -32(%rsp), %xmm10
        addsd     (%rdx,%r11,8), %xmm10
        movsd     %xmm10, -32(%rsp)
        movsd     -24(%rsp), %xmm11
        subsd     %xmm11, %xmm12
        movsd     %xmm12, -24(%rsp)
        movsd     -32(%rsp), %xmm14
        movsd     -24(%rsp), %xmm13
        addsd     %xmm13, %xmm14
        movsd     %xmm14, -24(%rsp)
        movsd     -40(%rsp), %xmm8
        mulsd     %xmm8, %xmm1
        movsd     -24(%rsp), %xmm15
        movsd     %xmm1, -40(%rsp)
        movaps    %xmm6, %xmm1
        movsd     -40(%rsp), %xmm3
        subsd     %xmm8, %xmm3
        addsd     (%rdx,%rax,8), %xmm15
        movsd     %xmm3, -32(%rsp)
        addsd     %xmm15, %xmm2
        movsd     -40(%rsp), %xmm4
        lea       4952+_vmldErfcHATab(%rip), %rdx
        movsd     -32(%rsp), %xmm15
        subsd     %xmm15, %xmm4
        movsd     %xmm4, -40(%rsp)
        movsd     -40(%rsp), %xmm5
        movq      (%rdx), %rax
        subsd     %xmm5, %xmm8
        movsd     %xmm8, -32(%rsp)
        movsd     -40(%rsp), %xmm10
        movsd     -32(%rsp), %xmm9
        mulsd     %xmm10, %xmm1
        addsd     %xmm9, %xmm2
        mulsd     %xmm0, %xmm10
        movaps    %xmm2, %xmm11
        mulsd     %xmm0, %xmm11
        mulsd     %xmm6, %xmm2
        movq      %rax, -48(%rsp)
        addsd     %xmm2, %xmm11
        shrq      $48, %rax
        addsd     %xmm10, %xmm11
        andl      $-32753, %eax
        movsd     %xmm11, -40(%rsp)
        orl       %ecx, %eax
        movsd     -40(%rsp), %xmm0
        movw      %ax, -42(%rsp)
..B1.8:
        andb      $-128, %dil
        cmpb      $-128, %dil
        je        ..B1.17
..B1.9:
        lea       5008+_vmldErfcHATab(%rip), %rax
        movsd     -8(%rsp), %xmm3
        movsd     (%rax), %xmm2
        comisd    %xmm3, %xmm2
        jbe       ..B1.11
..B1.10:
        addsd     %xmm1, %xmm0
        mulsd     -48(%rsp), %xmm0
        ret       
..B1.11:
        lea       4952+_vmldErfcHATab(%rip), %rax
        addl      $200, %r8d
        andl      $2047, %r8d
        lea       5000+_vmldErfcHATab(%rip), %rcx
        shll      $4, %r8d
        movq      (%rax), %rdx
        movq      %rdx, -48(%rsp)
        shrq      $48, %rdx
        andl      $-32753, %edx
        orl       %r8d, %edx
        movw      %dx, -42(%rsp)
        movsd     -48(%rsp), %xmm2
        mulsd     %xmm2, %xmm1
        comisd    (%rcx), %xmm3
        mulsd     %xmm2, %xmm0
        jbe       ..B1.13
..B1.12:
        lea       4928+_vmldErfcHATab(%rip), %rax
        addsd     %xmm1, %xmm0
        mulsd     (%rax), %xmm0
        movaps    %xmm0, %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm2
        addsd     %xmm2, %xmm0
        ret       
..B1.13:
        movaps    %xmm0, %xmm2
        lea       4928+_vmldErfcHATab(%rip), %rax
        addsd     %xmm1, %xmm2
        movsd     %xmm2, -40(%rsp)
        movsd     -40(%rsp), %xmm3
        subsd     %xmm3, %xmm1
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -32(%rsp)
        movsd     -40(%rsp), %xmm5
        mulsd     %xmm5, %xmm7
        movsd     -32(%rsp), %xmm9
        movsd     %xmm7, -40(%rsp)
        movsd     -40(%rsp), %xmm0
        subsd     %xmm5, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -40(%rsp), %xmm1
        movsd     -32(%rsp), %xmm7
        subsd     %xmm7, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm4
        subsd     %xmm4, %xmm5
        movsd     %xmm5, -32(%rsp)
        movsd     -40(%rsp), %xmm8
        movsd     -32(%rsp), %xmm6
        mulsd     (%rax), %xmm8
        addsd     %xmm6, %xmm9
        mulsd     (%rax), %xmm9
        movsd     %xmm8, -40(%rsp)
        movsd     %xmm9, -32(%rsp)
        movsd     -40(%rsp), %xmm0
        movsd     -32(%rsp), %xmm10
        addsd     %xmm10, %xmm0
..B1.14:
        ret       
..B1.15:
        lea       4984+_vmldErfcHATab(%rip), %rax
        lea       4928+_vmldErfcHATab(%rip), %rdx
        movsd     (%rax), %xmm0
        subsd     (%rdx), %xmm0
        ret       
..B1.16:
        lea       4968+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm0
        addsd     -8(%rsp), %xmm0
        ret       
..B1.17:
        movsd     -48(%rsp), %xmm2
        lea       4984+_vmldErfcHATab(%rip), %rax
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm0
        movsd     (%rax), %xmm5
        xorps     .L_2il0floatpacket.166(%rip), %xmm1
        movaps    %xmm5, %xmm3
        xorps     .L_2il0floatpacket.166(%rip), %xmm0
        addsd     %xmm1, %xmm3
        movsd     %xmm3, -40(%rsp)
        movsd     -40(%rsp), %xmm4
        subsd     %xmm4, %xmm5
        addsd     %xmm1, %xmm5
        movsd     %xmm5, -32(%rsp)
        movsd     -40(%rsp), %xmm11
        movaps    %xmm11, %xmm1
        movaps    %xmm11, %xmm7
        movsd     -32(%rsp), %xmm14
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -40(%rsp)
        movsd     -40(%rsp), %xmm6
        subsd     %xmm6, %xmm7
        movsd     %xmm7, -32(%rsp)
        movsd     -40(%rsp), %xmm9
        movsd     -32(%rsp), %xmm8
        addsd     %xmm8, %xmm9
        movsd     %xmm9, -24(%rsp)
        movsd     -32(%rsp), %xmm10
        addsd     %xmm10, %xmm0
        movsd     %xmm0, -32(%rsp)
        movsd     -24(%rsp), %xmm0
        subsd     %xmm0, %xmm11
        movsd     %xmm11, -24(%rsp)
        movsd     -32(%rsp), %xmm13
        movsd     -24(%rsp), %xmm12
        addsd     %xmm12, %xmm13
        movsd     %xmm13, -24(%rsp)
        movsd     -40(%rsp), %xmm15
        movsd     -24(%rsp), %xmm0
        addsd     %xmm14, %xmm0
        addsd     %xmm15, %xmm0
        ret       
..B1.18:
        testl     $1048575, -4(%rsp)
        jne       ..B1.23
..B1.19:
        cmpl      $0, -8(%rsp)
        jne       ..B1.23
..B1.20:
        movb      -1(%rsp), %al
        andb      $-128, %al
        cmpb      $-128, %al
        je        ..B1.24
..B1.21:
        lea       4952+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm0
..B1.22:
        ret       
..B1.23:
        movsd     -8(%rsp), %xmm0
        mulsd     %xmm0, %xmm0
        ret       
..B1.24:
        lea       4984+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm0
        ret       
..B1.25:
        lea       4960+_vmldErfcHATab(%rip), %rax
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	erfc,@function
	.size	erfc,.-erfc
	.data
# -- End  erfc
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.166:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.166,@object
	.size	.L_2il0floatpacket.166,16
	.align 8
_vmldErfcHATab:
	.long	0
	.long	1074790400
	.long	0
	.long	3219128320
	.long	2536042781
	.long	1072398983
	.long	1399772821
	.long	1016356498
	.long	2716363190
	.long	3218196015
	.long	2515012741
	.long	3161253520
	.long	2716363190
	.long	1065469487
	.long	2240747611
	.long	1008526987
	.long	3687592758
	.long	1064760658
	.long	803907102
	.long	1004882786
	.long	3448634865
	.long	3207698121
	.long	750050903
	.long	3152352039
	.long	601957364
	.long	3206196134
	.long	1555639211
	.long	3149699171
	.long	1774552010
	.long	1054291178
	.long	3884532135
	.long	1052283480
	.long	3233761315
	.long	3195467393
	.long	1379327181
	.long	3193076019
	.long	242623580
	.long	1041378609
	.long	2275269623
	.long	1038572401
	.long	254615366
	.long	3181902490
	.long	2447494219
	.long	3178851847
	.long	604424981
	.long	1027296749
	.long	50341065
	.long	1024134801
	.long	0
	.long	1073741824
	.long	0
	.long	3219390464
	.long	1599080160
	.long	1071977136
	.long	688395245
	.long	1016124311
	.long	3971924631
	.long	3219152851
	.long	3564228032
	.long	3163583509
	.long	3891163965
	.long	1068791752
	.long	3480798202
	.long	1012474408
	.long	1621928818
	.long	1067553147
	.long	4020082186
	.long	1012007724
	.long	685042962
	.long	3213042343
	.long	3489060295
	.long	3153732341
	.long	2848795668
	.long	3210759248
	.long	3762448119
	.long	3156070926
	.long	1324211555
	.long	1061670518
	.long	1476477459
	.long	1058441900
	.long	2515812913
	.long	3204852392
	.long	3304760842
	.long	3200696323
	.long	716797578
	.long	1052801360
	.long	2283992921
	.long	1047370885
	.long	2268754290
	.long	3195296397
	.long	1012519479
	.long	3185657836
	.long	944669356
	.long	1042638534
	.long	1931303816
	.long	3183131001
	.long	0
	.long	1072693248
	.long	0
	.long	3219390464
	.long	1180573078
	.long	1071127762
	.long	4113175178
	.long	1014965748
	.long	2484292509
	.long	3219680918
	.long	959898156
	.long	3163955503
	.long	957881989
	.long	1071548988
	.long	442195505
	.long	1012747532
	.long	35029730
	.long	1068269829
	.long	976783580
	.long	1012161104
	.long	2319046290
	.long	3217462844
	.long	10747389
	.long	3161497233
	.long	3758081287
	.long	1067192898
	.long	1126055042
	.long	1009745370
	.long	1760060230
	.long	1067789094
	.long	3251266701
	.long	3213655257
	.long	2806967453
	.long	3212534516
	.long	1864685761
	.long	1064158132
	.long	3738348856
	.long	1061687624
	.long	790737257
	.long	3209050005
	.long	1943644508
	.long	3204796481
	.long	2914584687
	.long	1058674792
	.long	2574038108
	.long	3201168805
	.long	1252008665
	.long	3203024355
	.long	0
	.long	1071644672
	.long	0
	.long	3219390464
	.long	866709712
	.long	1068743889
	.long	2877058776
	.long	1013060082
	.long	4210332368
	.long	3219015250
	.long	1459199107
	.long	3163298467
	.long	3688727889
	.long	1072884723
	.long	2792291460
	.long	1014241070
	.long	855534605
	.long	3220533712
	.long	759913640
	.long	3163946628
	.long	2131284961
	.long	1069103746
	.long	2597396527
	.long	1013374934
	.long	989201780
	.long	1073227786
	.long	3318708226
	.long	1013855758
	.long	699280746
	.long	3220558000
	.long	993015799
	.long	3218982987
	.long	1559561232
	.long	1073166816
	.long	680189340
	.long	3219046342
	.long	3200850606
	.long	3220010182
	.long	1492278808
	.long	1071125031
	.long	4108347350
	.long	3218518333
	.long	2186414716
	.long	3220448795
	.long	2803204773
	.long	3219471098
	.long	1021304598
	.long	3216041452
	.long	0
	.long	1072693248
	.long	0
	.long	3220701184
	.long	2395493008
	.long	1067539114
	.long	2347713276
	.long	1010696759
	.long	649597580
	.long	3216929335
	.long	3715599898
	.long	3160841752
	.long	1561014649
	.long	1069995433
	.long	3047088181
	.long	1014230941
	.long	735510274
	.long	3217146528
	.long	1352249772
	.long	3161795800
	.long	1629994682
	.long	1067898281
	.long	867814168
	.long	1012540688
	.long	3242132826
	.long	1066299959
	.long	1473115860
	.long	1010684527
	.long	1350993397
	.long	3214133819
	.long	212467593
	.long	1064529314
	.long	1581296174
	.long	1063476333
	.long	1019617056
	.long	3210475870
	.long	326531339
	.long	1058667915
	.long	2560102974
	.long	1060207995
	.long	1059830545
	.long	3205421344
	.long	73331091
	.long	1053435953
	.long	3519680827
	.long	1056963274
	.long	287235298
	.long	1054771565
	.long	0
	.long	1072693248
	.long	0
	.long	3220701184
	.long	2395492868
	.long	1067539114
	.long	3682704431
	.long	1012674156
	.long	649602216
	.long	3216929335
	.long	3411089016
	.long	3161142609
	.long	1560943486
	.long	1069995433
	.long	373233254
	.long	1014373936
	.long	736848572
	.long	3217146528
	.long	1866442275
	.long	3160326248
	.long	1560943474
	.long	1067898281
	.long	3576330307
	.long	1009307384
	.long	649623715
	.long	1066299959
	.long	725533065
	.long	1006865126
	.long	1925526991
	.long	3214133821
	.long	2782059224
	.long	1064529267
	.long	3902564706
	.long	1063475940
	.long	2706862450
	.long	3210478464
	.long	1817950372
	.long	1058560819
	.long	1279984267
	.long	1060154640
	.long	1456324877
	.long	3205782300
	.long	3219859931
	.long	3203609651
	.long	1354896425
	.long	1055916872
	.long	153053737
	.long	3200605489
	.long	0
	.long	1070596096
	.long	0
	.long	3219390464
	.long	4088869944
	.long	1070267315
	.long	2176293176
	.long	1014815941
	.long	2130816332
	.long	3218278509
	.long	1120272801
	.long	3163324298
	.long	2812844367
	.long	1071221080
	.long	3953643227
	.long	1013638927
	.long	1988706352
	.long	3219153696
	.long	4268394231
	.long	3159871631
	.long	1288160248
	.long	1071920576
	.long	2922427679
	.long	1016153595
	.long	2977979581
	.long	3219655118
	.long	3750808263
	.long	3157405326
	.long	985562119
	.long	1072408739
	.long	2519169024
	.long	3220102519
	.long	68429100
	.long	1072741226
	.long	3577866702
	.long	3220290005
	.long	2564607388
	.long	1072813459
	.long	2939200104
	.long	3220467302
	.long	4218579581
	.long	1071794210
	.long	1377164546
	.long	3221614222
	.long	2324734477
	.long	3221337613
	.long	1839603542
	.long	3222378369
	.long	0
	.long	1071644672
	.long	0
	.long	3220701184
	.long	2099420460
	.long	1070000514
	.long	2802853043
	.long	1014611496
	.long	1293201774
	.long	3216758446
	.long	754100791
	.long	3161260779
	.long	446218444
	.long	1068516350
	.long	4119686598
	.long	1008896294
	.long	464017249
	.long	3215087878
	.long	960597079
	.long	3159504610
	.long	785622302
	.long	1066666444
	.long	3339954945
	.long	1008950591
	.long	2636124640
	.long	3213180396
	.long	2995891561
	.long	3157221870
	.long	742584182
	.long	1064691513
	.long	2101069570
	.long	3211133521
	.long	2181624698
	.long	1062573543
	.long	880779555
	.long	3208950245
	.long	3229614298
	.long	1060332156
	.long	2776250631
	.long	3206681158
	.long	1726624674
	.long	1057845172
	.long	2932756839
	.long	3204700218
	.long	968482455
	.long	3200538336
	.long	839487434
	.long	3203407115
	.long	0
	.long	1071644672
	.long	0
	.long	3220701184
	.long	2099420460
	.long	1070000514
	.long	3971839787
	.long	1014611494
	.long	1293201774
	.long	3216758446
	.long	1164040504
	.long	3161242013
	.long	446218443
	.long	1068516350
	.long	752423430
	.long	1013624741
	.long	464016831
	.long	3215087878
	.long	1037460728
	.long	3159893622
	.long	785625671
	.long	1066666444
	.long	2777520670
	.long	1010462846
	.long	2633474472
	.long	3213180396
	.long	224500896
	.long	3158129422
	.long	768707698
	.long	1064691513
	.long	3292721506
	.long	3211133520
	.long	578140564
	.long	1062573549
	.long	2030948072
	.long	3208950024
	.long	703221627
	.long	1060333354
	.long	3302679767
	.long	3206661604
	.long	1902201635
	.long	1057974954
	.long	4048348141
	.long	3204024333
	.long	436255103
	.long	1054912980
	.long	2856127569
	.long	3199800416
	.long	0
	.long	1069547520
	.long	0
	.long	3219390464
	.long	2660514860
	.long	1069307683
	.long	3910050887
	.long	1010798723
	.long	1232972210
	.long	3217414211
	.long	3319476861
	.long	3162069233
	.long	728349866
	.long	1070632184
	.long	2957516014
	.long	1014996223
	.long	2614894425
	.long	3218648001
	.long	3908389506
	.long	3161696982
	.long	2191212410
	.long	1071782386
	.long	2431020086
	.long	1016759180
	.long	3218723210
	.long	3219794568
	.long	3000611168
	.long	3164060285
	.long	3635880648
	.long	1072866249
	.long	536114836
	.long	3220846078
	.long	772319782
	.long	1073876386
	.long	455323837
	.long	3221871435
	.long	2867783196
	.long	1074413781
	.long	2591697711
	.long	3223680483
	.long	3707131585
	.long	3224082365
	.long	1191412344
	.long	3226597368
	.long	26961465
	.long	3227158611
	.long	3399174677
	.long	3227576866
	.long	0
	.long	1070596096
	.long	0
	.long	3220701184
	.long	2223626006
	.long	1069006900
	.long	1459366060
	.long	1012973801
	.long	1839010552
	.long	3215906806
	.long	2068820260
	.long	3160057563
	.long	1249919926
	.long	1067698038
	.long	962503917
	.long	1012536115
	.long	2100991944
	.long	3214479466
	.long	3825857555
	.long	3158728160
	.long	2359368913
	.long	1066362061
	.long	743880428
	.long	1009085061
	.long	1096261871
	.long	3213062353
	.long	3975107283
	.long	3156829710
	.long	3079668469
	.long	1064816845
	.long	1037052812
	.long	3211583355
	.long	3759245614
	.long	1063340491
	.long	3200735738
	.long	3210008934
	.long	4161299971
	.long	1061559762
	.long	2934206128
	.long	3208900975
	.long	3971101203
	.long	3206549768
	.long	353687358
	.long	3209069242
	.long	3047695518
	.long	3208432442
	.long	3851119497
	.long	3207872727
	.long	0
	.long	1070596096
	.long	0
	.long	3220701184
	.long	2223626017
	.long	1069006900
	.long	1388676355
	.long	1012705760
	.long	1839008253
	.long	3215906806
	.long	815236617
	.long	3160082992
	.long	1250030689
	.long	1067698038
	.long	1288166708
	.long	1012882161
	.long	2094505532
	.long	3214479466
	.long	311037013
	.long	3156914212
	.long	2617751546
	.long	1066362061
	.long	2536920761
	.long	1010241475
	.long	1679260317
	.long	3213062352
	.long	3392965897
	.long	3157018830
	.long	1054244789
	.long	1064816864
	.long	3966408696
	.long	3211583052
	.long	1854848819
	.long	1063342388
	.long	3813743082
	.long	3209990587
	.long	395181184
	.long	1061696008
	.long	2456810233
	.long	3208392808
	.long	4089464644
	.long	1060125482
	.long	3698050549
	.long	3206684252
	.long	1112347086
	.long	1058131030
	.long	218099662
	.long	3203798608
	.long	0
	.long	1068498944
	.long	0
	.long	3219390464
	.long	525966855
	.long	1068285571
	.long	2606587153
	.long	1012297682
	.long	2443413391
	.long	3216426332
	.long	691043050
	.long	3159699549
	.long	1829248673
	.long	1069675485
	.long	797308607
	.long	1013894531
	.long	2897861001
	.long	3217829064
	.long	3729653017
	.long	3162035037
	.long	3517114205
	.long	1070988945
	.long	2096268315
	.long	1015157641
	.long	733746641
	.long	3219206769
	.long	2776288224
	.long	3161354348
	.long	312049674
	.long	1072253264
	.long	1779010374
	.long	3220866352
	.long	3398525311
	.long	3220286121
	.long	3932053403
	.long	3224771363
	.long	2526572080
	.long	3226863609
	.long	1052531014
	.long	3228950632
	.long	1237705257
	.long	3230476352
	.long	1818683855
	.long	3231504084
	.long	2771211543
	.long	3231812491
	.long	1784201120
	.long	3231087810
	.long	0
	.long	1069547520
	.long	0
	.long	3220701184
	.long	3317774106
	.long	1067973958
	.long	2923167300
	.long	1012645752
	.long	3241173892
	.long	3214918775
	.long	39491139
	.long	3156995939
	.long	285395131
	.long	1066727038
	.long	2541426776
	.long	1010932663
	.long	1226100891
	.long	3213595410
	.long	1495613600
	.long	3157951728
	.long	3870275689
	.long	1065488829
	.long	965710822
	.long	1009822465
	.long	3492316846
	.long	3212293408
	.long	2517483522
	.long	3156957513
	.long	1374076994
	.long	1064156941
	.long	372497316
	.long	3211297712
	.long	3813845480
	.long	3208055775
	.long	3900585526
	.long	3212419165
	.long	1960133516
	.long	3213181643
	.long	148017317
	.long	3214056684
	.long	3471465381
	.long	3214242476
	.long	65590178
	.long	3214048451
	.long	3138884841
	.long	3213067513
	.long	2557226666
	.long	3211122744
	.long	0
	.long	1069547520
	.long	0
	.long	3220701184
	.long	3319825997
	.long	1067973958
	.long	153387004
	.long	1010340046
	.long	3027068505
	.long	3214918775
	.long	2440895001
	.long	3158659807
	.long	1195164920
	.long	1066727039
	.long	1739223825
	.long	1010932951
	.long	3681176633
	.long	3213595373
	.long	2468586085
	.long	3156977493
	.long	2873291842
	.long	1065489208
	.long	1357898154
	.long	1010449195
	.long	449207067
	.long	3212287634
	.long	1843054170
	.long	3156472137
	.long	1688975421
	.long	1064223666
	.long	2606145257
	.long	3210999965
	.long	2843228885
	.long	1062852799
	.long	3218505005
	.long	3209735220
	.long	2000171025
	.long	1061515619
	.long	2480760959
	.long	3208347934
	.long	1915328357
	.long	1060202094
	.long	619623839
	.long	3207101805
	.long	3877388623
	.long	1057814602
	.long	1403207699
	.long	3206847388
	.long	0
	.long	1069547520
	.long	0
	.long	3220701184
	.long	3319825996
	.long	1067973958
	.long	2025830981
	.long	1009198696
	.long	3027068262
	.long	3214918775
	.long	462574407
	.long	3158874537
	.long	1195152753
	.long	1066727039
	.long	2026642833
	.long	1010473176
	.long	3680441114
	.long	3213595373
	.long	1280116659
	.long	3154901898
	.long	2858263562
	.long	1065489208
	.long	3653419668
	.long	1010119659
	.long	8491235
	.long	3212287634
	.long	2006410862
	.long	3152308929
	.long	669193667
	.long	1064223664
	.long	261358851
	.long	3210999947
	.long	3798337047
	.long	1062852562
	.long	3252853818
	.long	3209734051
	.long	2986380320
	.long	1061506613
	.long	2271475836
	.long	3208293716
	.long	4121652893
	.long	1060072878
	.long	1033391529
	.long	3206544683
	.long	472144880
	.long	1057491226
	.long	2996742642
	.long	3202556481
	.long	0
	.long	1067450368
	.long	0
	.long	3219390464
	.long	2310440956
	.long	1067243964
	.long	1910839802
	.long	1011085066
	.long	3231655782
	.long	3215394280
	.long	67697416
	.long	3158533143
	.long	747798972
	.long	1068652953
	.long	3842674820
	.long	1013874752
	.long	2960203073
	.long	3216848639
	.long	2643975567
	.long	3156914699
	.long	3514791177
	.long	1070021174
	.long	3133960404
	.long	1013937886
	.long	2900718461
	.long	3218240252
	.long	1762535320
	.long	3161495508
	.long	3933388189
	.long	1071460247
	.long	1623401109
	.long	3219631106
	.long	2243580782
	.long	1072656900
	.long	3113530625
	.long	3221833507
	.long	4278270359
	.long	3222733868
	.long	4152511010
	.long	3225895261
	.long	1448809675
	.long	3227655974
	.long	2046900051
	.long	3229157102
	.long	4134264961
	.long	3229841799
	.long	2742521525
	.long	3229722239
	.long	0
	.long	1068498944
	.long	0
	.long	3220701184
	.long	2295541704
	.long	1066929438
	.long	3837869256
	.long	1002512271
	.long	2403525199
	.long	3213885862
	.long	3057254264
	.long	3152556454
	.long	1273180940
	.long	1065699684
	.long	307148135
	.long	1007267775
	.long	4108180605
	.long	3212593363
	.long	867507169
	.long	3154906335
	.long	2406587489
	.long	1064486498
	.long	3600962311
	.long	1007048784
	.long	641070000
	.long	3211323670
	.long	942137894
	.long	3155422437
	.long	471088535
	.long	1063286275
	.long	3784803533
	.long	3210106468
	.long	2056014570
	.long	1061725436
	.long	975828111
	.long	3209750570
	.long	3879370210
	.long	3209336425
	.long	2803950026
	.long	3211135338
	.long	606260247
	.long	3211577897
	.long	1881144158
	.long	3211825519
	.long	670638336
	.long	3211103480
	.long	2138776162
	.long	3209709388
	.long	0
	.long	1068498944
	.long	0
	.long	3220701184
	.long	2295580191
	.long	1066929438
	.long	3681200523
	.long	1011869447
	.long	2400932937
	.long	3213885862
	.long	1282261590
	.long	3155177332
	.long	1435460813
	.long	1065699684
	.long	338806164
	.long	1009895735
	.long	2137816334
	.long	3212593362
	.long	2795067653
	.long	3156177866
	.long	4261873090
	.long	1064486517
	.long	3264144757
	.long	1007933656
	.long	2173718924
	.long	3211323291
	.long	3810711629
	.long	3155704795
	.long	2446322575
	.long	1063289064
	.long	2077064354
	.long	3210074792
	.long	504970137
	.long	1062005301
	.long	3765139549
	.long	3208846522
	.long	2079001974
	.long	1060716176
	.long	875759584
	.long	3207637152
	.long	4108525622
	.long	1059452531
	.long	4049318739
	.long	3206336314
	.long	3770858801
	.long	1058237468
	.long	2722340464
	.long	3205374723
	.long	0
	.long	1068498944
	.long	0
	.long	3220701184
	.long	2295580191
	.long	1066929438
	.long	2149023882
	.long	1011305144
	.long	2400932909
	.long	3213885862
	.long	1064137805
	.long	3156227957
	.long	1435458098
	.long	1065699684
	.long	4263810407
	.long	1007587847
	.long	2137652297
	.long	3212593362
	.long	1479815673
	.long	3155856170
	.long	4258425849
	.long	1064486517
	.long	3605379165
	.long	1009762642
	.long	2066788283
	.long	3211323291
	.long	3198576201
	.long	3153399561
	.long	1179383574
	.long	1063289064
	.long	140008546
	.long	3210074787
	.long	2930072161
	.long	1062005221
	.long	1129148062
	.long	3208846064
	.long	3175174977
	.long	1060711976
	.long	382350984
	.long	3207621985
	.long	3891461581
	.long	1059368087
	.long	1514607567
	.long	3205983602
	.long	292029317
	.long	1057267187
	.long	906505364
	.long	3202625257
	.long	0
	.long	1071644672
	.long	1431652600
	.long	1069897045
	.long	1431670732
	.long	1067799893
	.long	984555731
	.long	1065423122
	.long	472530941
	.long	1062650218
	.long	0
	.long	1072693248
	.long	0
	.long	0
	.long	939524096
	.long	1072704666
	.long	2209988083
	.long	1046076929
	.long	3489660928
	.long	1072716208
	.long	2715237930
	.long	1044950059
	.long	402653184
	.long	1072727877
	.long	36755401
	.long	1042114290
	.long	1744830464
	.long	1072739672
	.long	1032479461
	.long	1045685796
	.long	805306368
	.long	1072751596
	.long	269498903
	.long	1044815501
	.long	3489660928
	.long	1072763649
	.long	2766913307
	.long	1039293264
	.long	2818572288
	.long	1072775834
	.long	2127365126
	.long	1046127799
	.long	939524096
	.long	1072788152
	.long	3950214642
	.long	1045558597
	.long	3892314112
	.long	1072800603
	.long	2850403528
	.long	1045134939
	.long	805306368
	.long	1072813191
	.long	2810207104
	.long	1043762074
	.long	2281701376
	.long	1072825915
	.long	2397289153
	.long	1041802037
	.long	1744830464
	.long	1072838778
	.long	3785033707
	.long	1046074760
	.long	1610612736
	.long	1072851781
	.long	1955981725
	.long	1045924339
	.long	4026531840
	.long	1072864925
	.long	2226165573
	.long	1045725051
	.long	2684354560
	.long	1072878213
	.long	754984053
	.long	1046188044
	.long	134217728
	.long	1072891646
	.long	2842642093
	.long	1044483512
	.long	2952790016
	.long	1072905224
	.long	4200916017
	.long	1044586679
	.long	805306368
	.long	1072918951
	.long	714887508
	.long	1046276775
	.long	805306368
	.long	1072932827
	.long	3685248805
	.long	1045665407
	.long	1207959552
	.long	1072946854
	.long	2295779401
	.long	1045448912
	.long	536870912
	.long	1072961034
	.long	2676240988
	.long	1044345570
	.long	1610612736
	.long	1072975368
	.long	1086643152
	.long	1041785419
	.long	2952790016
	.long	1072989858
	.long	1078920847
	.long	1045890291
	.long	3489660928
	.long	1073004506
	.long	2624917087
	.long	1045747880
	.long	1879048192
	.long	1073019314
	.long	2595540835
	.long	1046115506
	.long	1342177280
	.long	1073034283
	.long	125577743
	.long	1046115646
	.long	805306368
	.long	1073049415
	.long	1771811520
	.long	1046140063
	.long	3623878656
	.long	1073064711
	.long	2774738112
	.long	1045766480
	.long	268435456
	.long	1073080175
	.long	565866387
	.long	1045869701
	.long	2952790016
	.long	1073095806
	.long	1492679939
	.long	1041050306
	.long	2147483648
	.long	1073111608
	.long	612974287
	.long	1044556049
	.long	1610612736
	.long	1073127582
	.long	843197631
	.long	1046084847
	.long	939524096
	.long	1073143730
	.long	3150480267
	.long	1045533800
	.long	3892314112
	.long	1073160053
	.long	2074373662
	.long	1043172334
	.long	1342177280
	.long	1073176555
	.long	587053675
	.long	1046022559
	.long	1879048192
	.long	1073193236
	.long	918464641
	.long	1045387276
	.long	805306368
	.long	1073210099
	.long	2285990923
	.long	1046166841
	.long	2550136832
	.long	1073227145
	.long	682424459
	.long	1043647713
	.long	2550136832
	.long	1073244377
	.long	1678188781
	.long	1045046423
	.long	1073741824
	.long	1073261797
	.long	3671932459
	.long	1044468998
	.long	2550136832
	.long	1073279406
	.long	1656324724
	.long	1043421043
	.long	2952790016
	.long	1073297207
	.long	2659845000
	.long	1042921660
	.long	2550136832
	.long	1073315202
	.long	1043233352
	.long	1046444345
	.long	2147483648
	.long	1073333393
	.long	2162418230
	.long	1044717444
	.long	2013265920
	.long	1073351782
	.long	630511316
	.long	1045098283
	.long	2952790016
	.long	1073370371
	.long	3831108133
	.long	1044508970
	.long	1342177280
	.long	1073389163
	.long	4245662865
	.long	1045817334
	.long	2550136832
	.long	1073408159
	.long	3588780877
	.long	1043705146
	.long	3087007744
	.long	1073427362
	.long	3182425146
	.long	1041483134
	.long	4026531840
	.long	1073446774
	.long	928333188
	.long	1044896498
	.long	2415919104
	.long	1073466398
	.long	2300504125
	.long	1041428596
	.long	3623878656
	.long	1073486235
	.long	1887994668
	.long	1045828938
	.long	671088640
	.long	1073506289
	.long	757630176
	.long	1046044485
	.long	3623878656
	.long	1073526560
	.long	2756972335
	.long	1045675585
	.long	1207959552
	.long	1073547053
	.long	3697834264
	.long	1044397131
	.long	3623878656
	.long	1073567768
	.long	479684631
	.long	1045687954
	.long	0
	.long	1073588710
	.long	730095629
	.long	1045354900
	.long	805306368
	.long	1073609879
	.long	4123739734
	.long	1045159130
	.long	3758096384
	.long	1073631278
	.long	3634164812
	.long	1046359295
	.long	2684354560
	.long	1073652911
	.long	3425467293
	.long	1044718726
	.long	3892314112
	.long	1073674779
	.long	2648473416
	.long	1046054248
	.long	1476395008
	.long	1073696886
	.long	54164518
	.long	1045131818
	.long	2147483648
	.long	1073719233
	.long	2196310654
	.long	1043982605
	.long	4277796864
	.long	1065758274
	.long	3164486458
	.long	1025308570
	.long	1697350398
	.long	1079448903
	.long	0
	.long	862978048
	.long	0
	.long	1127743488
	.long	33554432
	.long	1101004800
	.long	0
	.long	0
	.long	1
	.long	1048576
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.long	0
	.long	1073741824
	.long	1105497085
	.long	1077623260
	.long	261899793
	.long	1077588746
	.long	4235085970
	.long	1077578514
	.long	2406803786
	.long	3222762575
	.type	_vmldErfcHATab,@object
	.size	_vmldErfcHATab,5024
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
