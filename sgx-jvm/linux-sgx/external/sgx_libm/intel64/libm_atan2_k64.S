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
	.file "libm_atan2_k64.c"
	.text
..TXTST0:
# -- Begin  __libm_atan2_k64
	.text
       .align    16,0x90
	.globl __libm_atan2_k64
__libm_atan2_k64:
# parameter 1: %rdi
# parameter 2: %rsi
# parameter 3: %rdx
..B1.1:
	.cfi_startproc
..___tag_value___libm_atan2_k64.1:
..L2:

        pushq     %r13
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
        movq      %rsi, %r9
        movq      %rdx, %rax
        movzwl    6(%r9), %r11d
        andl      $32752, %r11d
        movb      7(%r9), %sil
        movb      7(%rax), %dl
        andb      $-128, %sil
        movzwl    6(%rax), %r10d
        andb      $-128, %dl
        andl      $32752, %r10d
        shrl      $4, %r11d
        shrb      $7, %sil
        shrb      $7, %dl
        shrl      $4, %r10d
        cmpl      $2047, %r11d
        jge       ..B1.34
..B1.2:
        cmpl      $2047, %r10d
        jge       ..B1.35
..B1.3:
        testl     %r11d, %r11d
        jne       ..B1.6
..B1.4:
        testl     $1048575, 4(%r9)
        jne       ..B1.6
..B1.5:
        cmpl      $0, (%r9)
        je        ..B1.29
..B1.6:
        testl     %r10d, %r10d
        jne       ..B1.9
..B1.7:
        testl     $1048575, 4(%rax)
        jne       ..B1.9
..B1.8:
        cmpl      $0, (%rax)
        je        ..B1.27
..B1.9:
        movzbl    %sil, %r8d
        lea       _dAtan2Tab(%rip), %rcx
        movsd     (%r9), %xmm4
        testl     %r11d, %r11d
        movsd     8(%r9), %xmm6
        movzbl    %dl, %r9d
        movsd     1904(%rcx,%r8,8), %xmm9
        movsd     (%rax), %xmm3
        mulsd     %xmm9, %xmm4
        movaps    %xmm3, %xmm2
        mulsd     %xmm9, %xmm6
        movsd     8(%rax), %xmm9
        movsd     1904(%rcx,%r9,8), %xmm0
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm9
        jle       ..B1.47
..B1.10:
        lea       1888+_dAtan2Tab(%rip), %rax
        lea       -1023(%r11), %r9d
        movl      %r9d, %r13d
        negl      %r13d
        addl      $1023, %r13d
        movq      (%rax), %r8
        andl      $2047, %r13d
        movq      %r8, -32(%rsp)
        shrq      $48, %r8
        shll      $4, %r13d
        andl      $-32753, %r8d
        movsd     %xmm4, -24(%rsp)
        orl       %r13d, %r8d
        movw      %r8w, -26(%rsp)
        movzwl    -18(%rsp), %eax
        andl      $-32753, %eax
        movsd     -32(%rsp), %xmm0
        orl       $-49168, %eax
        movw      %ax, -18(%rsp)
        mulsd     %xmm0, %xmm6
..B1.11:
        testl     %r10d, %r10d
        jle       ..B1.46
..B1.12:
        lea       1888+_dAtan2Tab(%rip), %rax
        lea       -1023(%r10), %r8d
        movsd     %xmm2, -16(%rsp)
        movq      (%rax), %r13
        movl      %r8d, %eax
        negl      %eax
        addl      $1023, %eax
        movq      %r13, -32(%rsp)
        andl      $2047, %eax
        shrq      $48, %r13
        shll      $4, %eax
        andl      $-32753, %r13d
        orl       %eax, %r13d
        movw      %r13w, -26(%rsp)
        movzwl    -10(%rsp), %eax
        andl      $-32753, %eax
        movsd     -32(%rsp), %xmm0
        orl       $-49168, %eax
        movw      %ax, -10(%rsp)
        mulsd     %xmm0, %xmm9
..B1.13:
        movl      %r9d, %eax
        subl      %r8d, %eax
        cmpl      $-54, %eax
        jle       ..B1.24
..B1.14:
        cmpl      $54, %eax
        jge       ..B1.21
..B1.15:
        testb     %dl, %dl
        jne       ..B1.17
..B1.16:
        lea       1992+_dAtan2Tab(%rip), %rax
        movsd     (%rax), %xmm12
        movaps    %xmm12, %xmm13
        jmp       ..B1.18
..B1.17:
        lea       1952+_dAtan2Tab(%rip), %rax
        lea       1960+_dAtan2Tab(%rip), %r10
        movsd     (%rax), %xmm12
        movsd     (%r10), %xmm13
..B1.18:
        negl      %r9d
        lea       1888+_dAtan2Tab(%rip), %rax
        movsd     -16(%rsp), %xmm8
        lea       1880+_dAtan2Tab(%rip), %r11
        movzwl    6(%rax), %r10d
        lea       1023(%r8,%r9), %r8d
        andl      $2047, %r8d
        andl      $-32753, %r10d
        movsd     (%rax), %xmm10
        lea       2016+_dAtan2Tab(%rip), %r9
        shll      $4, %r8d
        movaps    %xmm10, %xmm1
        movsd     %xmm10, -64(%rsp)
        orl       %r8d, %r10d
        movw      %r10w, -58(%rsp)
        movsd     -64(%rsp), %xmm4
        mulsd     %xmm4, %xmm8
        mulsd     %xmm4, %xmm9
        movsd     -24(%rsp), %xmm4
        movsd     (%r9), %xmm11
        movaps    %xmm4, %xmm5
        mulsd     %xmm11, %xmm5
        movaps    %xmm4, %xmm7
        movaps    %xmm5, %xmm3
        subsd     %xmm4, %xmm3
        subsd     %xmm3, %xmm5
        movaps    %xmm11, %xmm3
        mulsd     %xmm8, %xmm3
        subsd     %xmm5, %xmm7
        movaps    %xmm3, %xmm2
        addsd     %xmm6, %xmm7
        subsd     %xmm8, %xmm2
        subsd     %xmm2, %xmm3
        movaps    %xmm8, %xmm2
        divsd     %xmm3, %xmm1
        movaps    %xmm1, %xmm15
        movaps    %xmm3, %xmm0
        mulsd     %xmm11, %xmm15
        subsd     %xmm3, %xmm2
        movaps    %xmm15, %xmm14
        addsd     %xmm9, %xmm2
        subsd     %xmm1, %xmm14
        subsd     %xmm14, %xmm15
        movaps    %xmm10, %xmm14
        mulsd     %xmm15, %xmm0
        movaps    %xmm15, %xmm1
        mulsd     %xmm2, %xmm1
        subsd     %xmm0, %xmm14
        subsd     %xmm1, %xmm14
        movaps    %xmm15, %xmm1
        mulsd     %xmm5, %xmm1
        movaps    %xmm14, %xmm0
        addsd     %xmm10, %xmm0
        mulsd     %xmm0, %xmm14
        movaps    %xmm7, %xmm0
        mulsd     %xmm15, %xmm14
        mulsd     %xmm7, %xmm15
        mulsd     %xmm14, %xmm0
        mulsd     %xmm5, %xmm14
        addsd     %xmm14, %xmm0
        addsd     %xmm15, %xmm0
        movaps    %xmm0, %xmm14
        addsd     %xmm1, %xmm14
        comisd    (%r11), %xmm14
        subsd     %xmm14, %xmm1
        addsd     %xmm0, %xmm1
        jb        ..B1.20
..B1.19:
        movsd     %xmm14, -64(%rsp)
        lea       1984+_dAtan2Tab(%rip), %r9
        movl      -60(%rsp), %r8d
        lea       1872+_dAtan2Tab(%rip), %r10
        andl      $-524288, %r8d
        lea       1864+_dAtan2Tab(%rip), %r11
        orl       $262144, %r8d
        lea       1856+_dAtan2Tab(%rip), %r13
        movl      $0, -64(%rsp)
        movl      %r8d, -60(%rsp)
        lea       1848+_dAtan2Tab(%rip), %r8
        movsd     -64(%rsp), %xmm1
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm5
        mulsd     %xmm1, %xmm7
        movsd     (%r9), %xmm0
        movaps    %xmm11, %xmm1
        mulsd     %xmm0, %xmm3
        lea       1840+_dAtan2Tab(%rip), %r9
        mulsd     %xmm2, %xmm0
        movsd     %xmm14, -40(%rsp)
        movaps    %xmm3, %xmm14
        movaps    %xmm4, %xmm2
        addsd     %xmm0, %xmm14
        movl      -36(%rsp), %eax
        subsd     %xmm14, %xmm3
        andl      $2147483647, %eax
        addsd     %xmm3, %xmm0
        movaps    %xmm14, %xmm3
        addl      $-1069547520, %eax
        sarl      $18, %eax
        addsd     %xmm4, %xmm3
        andl      $-2, %eax
        subsd     %xmm3, %xmm2
        movslq    %eax, %rax
        addsd     %xmm2, %xmm14
        addsd     %xmm3, %xmm2
        subsd     %xmm2, %xmm4
        addsd     %xmm4, %xmm14
        movaps    %xmm3, %xmm4
        mulsd     %xmm11, %xmm4
        addsd     %xmm14, %xmm6
        movaps    %xmm4, %xmm15
        addsd     %xmm0, %xmm6
        subsd     %xmm3, %xmm15
        movaps    %xmm5, %xmm0
        subsd     %xmm15, %xmm4
        addsd     %xmm7, %xmm0
        subsd     %xmm4, %xmm3
        subsd     %xmm0, %xmm5
        addsd     %xmm6, %xmm3
        addsd     %xmm5, %xmm7
        movaps    %xmm0, %xmm5
        movaps    %xmm8, %xmm6
        addsd     %xmm8, %xmm5
        subsd     %xmm5, %xmm6
        addsd     %xmm6, %xmm0
        addsd     %xmm5, %xmm6
        subsd     %xmm6, %xmm8
        addsd     %xmm8, %xmm0
        addsd     %xmm0, %xmm9
        movaps    %xmm5, %xmm0
        mulsd     %xmm11, %xmm0
        addsd     %xmm7, %xmm9
        movaps    %xmm0, %xmm7
        subsd     %xmm5, %xmm7
        subsd     %xmm7, %xmm0
        movsd     (%r10), %xmm7
        lea       1832+_dAtan2Tab(%rip), %r10
        subsd     %xmm0, %xmm5
        addsd     %xmm9, %xmm5
        movaps    %xmm10, %xmm9
        divsd     %xmm0, %xmm9
        mulsd     %xmm9, %xmm1
        movaps    %xmm1, %xmm8
        subsd     %xmm9, %xmm8
        movaps    %xmm10, %xmm9
        subsd     %xmm8, %xmm1
        movaps    %xmm12, %xmm8
        mulsd     %xmm1, %xmm0
        movaps    %xmm1, %xmm2
        mulsd     %xmm1, %xmm5
        mulsd     %xmm4, %xmm2
        subsd     %xmm0, %xmm9
        subsd     %xmm5, %xmm9
        movaps    %xmm2, %xmm5
        addsd     %xmm9, %xmm10
        mulsd     %xmm10, %xmm9
        movaps    %xmm3, %xmm10
        mulsd     %xmm1, %xmm9
        mulsd     %xmm3, %xmm1
        mulsd     %xmm9, %xmm10
        mulsd     %xmm9, %xmm4
        addsd     %xmm4, %xmm10
        addsd     %xmm1, %xmm10
        addsd     %xmm10, %xmm5
        movaps    %xmm5, %xmm3
        movaps    %xmm5, %xmm15
        mulsd     %xmm5, %xmm3
        subsd     %xmm5, %xmm2
        mulsd     %xmm11, %xmm15
        mulsd     %xmm3, %xmm7
        addsd     %xmm2, %xmm10
        movaps    %xmm15, %xmm4
        addsd     (%r11), %xmm7
        subsd     %xmm5, %xmm4
        mulsd     %xmm3, %xmm7
        subsd     %xmm4, %xmm15
        addsd     (%r13), %xmm7
        subsd     %xmm15, %xmm5
        mulsd     %xmm3, %xmm7
        addsd     %xmm5, %xmm10
        addsd     (%r8), %xmm7
        mulsd     %xmm3, %xmm7
        lea       1824+_dAtan2Tab(%rip), %r11
        movaps    %xmm10, %xmm0
        addsd     (%r9), %xmm7
        mulsd     %xmm3, %xmm7
        movsd     (%rcx,%rax,8), %xmm4
        addsd     (%r10), %xmm7
        mulsd     %xmm3, %xmm7
        addsd     (%r11), %xmm7
        mulsd     %xmm3, %xmm7
        mulsd     %xmm7, %xmm11
        movaps    %xmm11, %xmm6
        subsd     %xmm7, %xmm6
        subsd     %xmm6, %xmm11
        movaps    %xmm11, %xmm14
        subsd     %xmm11, %xmm7
        mulsd     %xmm15, %xmm14
        mulsd     %xmm10, %xmm11
        mulsd     %xmm7, %xmm0
        mulsd     %xmm15, %xmm7
        addsd     %xmm11, %xmm0
        movaps    %xmm14, %xmm2
        movaps    %xmm14, %xmm11
        addsd     %xmm15, %xmm2
        addsd     %xmm7, %xmm0
        subsd     %xmm2, %xmm11
        movaps    %xmm2, %xmm3
        movaps    %xmm2, %xmm1
        addsd     %xmm11, %xmm15
        addsd     %xmm2, %xmm11
        addsd     %xmm4, %xmm3
        subsd     %xmm11, %xmm14
        subsd     %xmm3, %xmm1
        addsd     %xmm14, %xmm15
        addsd     %xmm1, %xmm4
        addsd     %xmm3, %xmm1
        addsd     %xmm15, %xmm10
        subsd     %xmm1, %xmm2
        addsd     %xmm0, %xmm10
        addsd     %xmm2, %xmm4
        movsd     %xmm3, -56(%rsp)
        addsd     %xmm10, %xmm4
        addsd     8(%rcx,%rax,8), %xmm4
        movb      %dl, %cl
        shlb      $7, %cl
        movsd     %xmm4, -48(%rsp)
        movb      -49(%rsp), %al
        andb      $127, %al
        orb       %cl, %al
        movb      %al, -49(%rsp)
        movsd     -56(%rsp), %xmm7
        movaps    %xmm7, %xmm5
        addsd     %xmm7, %xmm8
        movb      -41(%rsp), %r8b
        movb      %r8b, %r9b
        shrb      $7, %r8b
        subsd     %xmm8, %xmm5
        movaps    %xmm8, %xmm6
        addsd     %xmm5, %xmm12
        addsd     %xmm5, %xmm6
        xorb      %r8b, %dl
        andb      $127, %r9b
        shlb      $7, %dl
        subsd     %xmm6, %xmm7
        movsd     %xmm8, -56(%rsp)
        addsd     %xmm7, %xmm12
        orb       %dl, %r9b
        movb      %r9b, -41(%rsp)
        movsd     -48(%rsp), %xmm9
        movb      -49(%rsp), %dl
        movb      %dl, %al
        shrb      $7, %dl
        addsd     %xmm12, %xmm9
        movsd     %xmm12, -32(%rsp)
        addsd     %xmm13, %xmm9
        movsd     %xmm9, -48(%rsp)
        xorb      %sil, %dl
        movb      -41(%rsp), %cl
        movb      %cl, %r8b
        shrb      $7, %cl
        andb      $127, %al
        shlb      $7, %dl
        xorb      %sil, %cl
        andb      $127, %r8b
        shlb      $7, %cl
        orb       %dl, %al
        orb       %cl, %r8b
        movb      %al, -49(%rsp)
        movb      %r8b, -41(%rsp)
        movq      -56(%rsp), %rsi
        movq      -48(%rsp), %rax
        movq      %rsi, (%rdi)
        movq      %rax, 8(%rdi)
        xorl      %eax, %eax
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.20:
        movaps    %xmm14, %xmm0
        lea       1872+_dAtan2Tab(%rip), %rax
        mulsd     %xmm14, %xmm0
        lea       1864+_dAtan2Tab(%rip), %rcx
        movsd     (%rax), %xmm3
        lea       1856+_dAtan2Tab(%rip), %r8
        mulsd     %xmm0, %xmm3
        lea       1848+_dAtan2Tab(%rip), %r9
        lea       1840+_dAtan2Tab(%rip), %r10
        addsd     (%rcx), %xmm3
        mulsd     %xmm0, %xmm3
        lea       1832+_dAtan2Tab(%rip), %r11
        movaps    %xmm11, %xmm6
        movb      %dl, %al
        mulsd     %xmm14, %xmm6
        addsd     (%r8), %xmm3
        shlb      $7, %al
        mulsd     %xmm0, %xmm3
        movaps    %xmm6, %xmm2
        subsd     %xmm14, %xmm2
        addsd     (%r9), %xmm3
        subsd     %xmm2, %xmm6
        mulsd     %xmm0, %xmm3
        subsd     %xmm6, %xmm14
        addsd     (%r10), %xmm3
        addsd     %xmm14, %xmm1
        mulsd     %xmm0, %xmm3
        lea       1824+_dAtan2Tab(%rip), %r13
        addsd     (%r11), %xmm3
        mulsd     %xmm0, %xmm3
        movaps    %xmm6, %xmm5
        movaps    %xmm12, %xmm9
        addsd     (%r13), %xmm3
        mulsd     %xmm0, %xmm3
        mulsd     %xmm3, %xmm11
        movaps    %xmm11, %xmm14
        subsd     %xmm3, %xmm14
        subsd     %xmm14, %xmm11
        movaps    %xmm11, %xmm4
        subsd     %xmm11, %xmm3
        mulsd     %xmm6, %xmm4
        mulsd     %xmm1, %xmm11
        addsd     %xmm4, %xmm5
        movaps    %xmm3, %xmm7
        mulsd     %xmm1, %xmm7
        mulsd     %xmm6, %xmm3
        addsd     %xmm11, %xmm7
        movaps    %xmm4, %xmm11
        addsd     %xmm3, %xmm7
        subsd     %xmm5, %xmm11
        movsd     %xmm5, -56(%rsp)
        addsd     %xmm11, %xmm6
        addsd     %xmm5, %xmm11
        movb      -49(%rsp), %cl
        subsd     %xmm11, %xmm4
        andb      $127, %cl
        addsd     %xmm4, %xmm6
        orb       %al, %cl
        addsd     %xmm6, %xmm1
        movb      %cl, -49(%rsp)
        addsd     %xmm7, %xmm1
        movsd     -56(%rsp), %xmm8
        movaps    %xmm8, %xmm10
        addsd     %xmm8, %xmm9
        movsd     %xmm1, -48(%rsp)
        movaps    %xmm9, %xmm1
        movb      -41(%rsp), %r8b
        movb      %r8b, %r9b
        shrb      $7, %r8b
        subsd     %xmm9, %xmm10
        movsd     %xmm9, -56(%rsp)
        addsd     %xmm10, %xmm1
        addsd     %xmm12, %xmm10
        subsd     %xmm1, %xmm8
        xorb      %r8b, %dl
        andb      $127, %r9b
        shlb      $7, %dl
        addsd     %xmm8, %xmm10
        movsd     %xmm10, -32(%rsp)
        orb       %dl, %r9b
        movb      %r9b, -41(%rsp)
        movsd     -48(%rsp), %xmm12
        movb      -49(%rsp), %dl
        movb      %dl, %al
        shrb      $7, %dl
        addsd     %xmm10, %xmm12
        addsd     %xmm13, %xmm12
        movsd     %xmm12, -48(%rsp)
        xorb      %sil, %dl
        movb      -41(%rsp), %cl
        movb      %cl, %r8b
        shrb      $7, %cl
        andb      $127, %al
        shlb      $7, %dl
        xorb      %sil, %cl
        andb      $127, %r8b
        shlb      $7, %cl
        orb       %dl, %al
        orb       %cl, %r8b
        movb      %al, -49(%rsp)
        movb      %r8b, -41(%rsp)
        movq      -56(%rsp), %rsi
        movq      -48(%rsp), %rax
        movq      %rsi, (%rdi)
        movq      %rax, 8(%rdi)
        xorl      %eax, %eax
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.21:
        subl      %r10d, %r11d
        cmpl      $74, %r11d
        jge       ..B1.23
..B1.22:
        divsd     %xmm4, %xmm3
        lea       1944+_dAtan2Tab(%rip), %rax
        lea       1936+_dAtan2Tab(%rip), %rdx
        movsd     %xmm3, -32(%rsp)
        movsd     (%rax), %xmm0
        xorl      %eax, %eax
        movq      (%rdx), %rcx
        subsd     %xmm3, %xmm0
        movq      %rcx, (%rdi)
        shrq      $56, %rcx
        movsd     %xmm0, 8(%rdi)
        movb      15(%rdi), %r9b
        movb      %cl, %r8b
        shrb      $7, %cl
        movb      %r9b, %r10b
        shrb      $7, %r9b
        xorb      %sil, %cl
        xorb      %sil, %r9b
        shlb      $7, %cl
        andb      $127, %r8b
        shlb      $7, %r9b
        andb      $127, %r10b
        orb       %cl, %r8b
        orb       %r9b, %r10b
        movb      %r8b, 7(%rdi)
        movb      %r10b, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.23:
        lea       1936+_dAtan2Tab(%rip), %rax
        lea       1944+_dAtan2Tab(%rip), %rdx
        movq      (%rax), %rcx
        xorl      %eax, %eax
        movq      (%rdx), %r9
        movq      %rcx, (%rdi)
        movq      %r9, 8(%rdi)
        shrq      $56, %rcx
        shrq      $56, %r9
        movb      %cl, %r8b
        shrb      $7, %cl
        movb      %r9b, %r10b
        shrb      $7, %r9b
        xorb      %sil, %cl
        xorb      %sil, %r9b
        shlb      $7, %cl
        andb      $127, %r8b
        shlb      $7, %r9b
        andb      $127, %r10b
        orb       %cl, %r8b
        orb       %r9b, %r10b
        movb      %r8b, 7(%rdi)
        movb      %r10b, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.24:
        testb     %dl, %dl
        jne       ..B1.26
..B1.25:
        lea       2016+_dAtan2Tab(%rip), %rdx
        lea       1888+_dAtan2Tab(%rip), %rcx
        movsd     -24(%rsp), %xmm8
        movaps    %xmm8, %xmm7
        movsd     -16(%rsp), %xmm3
        movsd     (%rdx), %xmm10
        movaps    %xmm3, %xmm2
        mulsd     %xmm10, %xmm7
        mulsd     %xmm10, %xmm2
        movaps    %xmm7, %xmm0
        movsd     (%rcx), %xmm4
        subsd     %xmm8, %xmm0
        movaps    %xmm4, %xmm5
        subsd     %xmm0, %xmm7
        subsd     %xmm7, %xmm8
        addsd     %xmm6, %xmm8
        movaps    %xmm2, %xmm6
        subsd     %xmm3, %xmm6
        movaps    %xmm8, %xmm11
        subsd     %xmm6, %xmm2
        subsd     %xmm2, %xmm3
        addsd     %xmm9, %xmm3
        movaps    %xmm4, %xmm9
        divsd     %xmm2, %xmm9
        mulsd     %xmm9, %xmm10
        movaps    %xmm10, %xmm1
        subsd     %xmm9, %xmm1
        subsd     %xmm1, %xmm10
        mulsd     %xmm10, %xmm2
        movaps    %xmm10, %xmm13
        mulsd     %xmm10, %xmm3
        mulsd     %xmm7, %xmm13
        subsd     %xmm2, %xmm5
        movsd     %xmm10, -56(%rsp)
        subsd     %xmm3, %xmm5
        addsd     %xmm5, %xmm4
        mulsd     %xmm4, %xmm5
        mulsd     %xmm10, %xmm5
        mulsd     %xmm8, %xmm10
        mulsd     %xmm5, %xmm11
        mulsd     %xmm5, %xmm7
        movsd     %xmm5, -48(%rsp)
        addsd     %xmm7, %xmm11
        addsd     %xmm10, %xmm11
        movaps    %xmm11, %xmm12
        addsd     %xmm13, %xmm12
        movsd     %xmm12, (%rdi)
        subsd     %xmm12, %xmm13
        movsd     %xmm13, -32(%rsp)
        addsd     %xmm11, %xmm13
        movsd     %xmm13, 8(%rdi)
        movb      7(%rdi), %r8b
        movb      %r8b, %r9b
        movb      15(%rdi), %r10b
        movb      %r10b, %r11b
        shrb      $7, %r8b
        andb      $127, %r9b
        shrb      $7, %r10b
        movsd     %xmm12, -40(%rsp)
        xorb      %sil, %r8b
        xorb      %sil, %r10b
        shlb      $7, %r8b
        andb      $127, %r11b
        shlb      $7, %r10b
        orb       %r8b, %r9b
        orb       %r10b, %r11b
        movb      %r9b, 7(%rdi)
        movb      %r11b, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.26:
        lea       1952+_dAtan2Tab(%rip), %rax
        lea       1960+_dAtan2Tab(%rip), %rdx
        movq      (%rax), %rcx
        xorl      %eax, %eax
        movq      (%rdx), %r9
        movq      %rcx, (%rdi)
        movq      %r9, 8(%rdi)
        shrq      $56, %rcx
        shrq      $56, %r9
        movb      %cl, %r8b
        shrb      $7, %cl
        movb      %r9b, %r10b
        shrb      $7, %r9b
        xorb      %sil, %cl
        xorb      %sil, %r9b
        shlb      $7, %cl
        andb      $127, %r8b
        shlb      $7, %r9b
        andb      $127, %r10b
        orb       %cl, %r8b
        orb       %r9b, %r10b
        movb      %r8b, 7(%rdi)
        movb      %r10b, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.27:
        testl     %r11d, %r11d
        jne       ..B1.23
..B1.28:
        testl     $1048575, 4(%r9)
        jne       ..B1.23
        jmp       ..B1.57
..B1.29:
        jne       ..B1.23
..B1.31:
        testb     %dl, %dl
        jne       ..B1.26
..B1.32:
        lea       1992+_dAtan2Tab(%rip), %rax
        movq      (%rax), %rdx
        xorl      %eax, %eax
        movq      %rdx, (%rdi)
        movq      %rdx, 8(%rdi)
        shrq      $56, %rdx
        movb      %dl, %cl
        shrb      $7, %dl
        andb      $127, %cl
        xorb      %sil, %dl
        shlb      $7, %dl
        orb       %dl, %cl
        movb      %cl, 7(%rdi)
        movb      %cl, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.34:
        je        ..B1.51
..B1.56:
        cmpl      $2047, %r10d
..B1.35:
        je        ..B1.48
..B1.36:
        jl        ..B1.23
..B1.38:
        cmpl      $2047, %r11d
        jge       ..B1.42
..B1.39:
        testb     %dl, %dl
        je        ..B1.32
        jmp       ..B1.26
..B1.42:
        testb     %dl, %dl
        jne       ..B1.44
..B1.43:
        lea       1920+_dAtan2Tab(%rip), %rax
        lea       1928+_dAtan2Tab(%rip), %rdx
        movq      (%rax), %rcx
        xorl      %eax, %eax
        movq      (%rdx), %r9
        movq      %rcx, (%rdi)
        movq      %r9, 8(%rdi)
        shrq      $56, %rcx
        shrq      $56, %r9
        movb      %cl, %r8b
        shrb      $7, %cl
        movb      %r9b, %r10b
        shrb      $7, %r9b
        xorb      %sil, %cl
        xorb      %sil, %r9b
        shlb      $7, %cl
        andb      $127, %r8b
        shlb      $7, %r9b
        andb      $127, %r10b
        orb       %cl, %r8b
        orb       %r9b, %r10b
        movb      %r8b, 7(%rdi)
        movb      %r10b, 15(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.44:
        lea       1968+_dAtan2Tab(%rip), %rax
        lea       1976+_dAtan2Tab(%rip), %rdx
        movq      (%rax), %rcx
        xorl      %eax, %eax
        movq      (%rdx), %r9
        movq      %rcx, (%rdi)
        movq      %r9, 8(%rdi)
        shrq      $56, %rcx
        shrq      $56, %r9
        movb      %cl, %r8b
        shrb      $7, %cl
        movb      %r9b, %r10b
        shrb      $7, %r9b
        xorb      %sil, %cl
        xorb      %sil, %r9b
        shlb      $7, %cl
        andb      $127, %r8b
        shlb      $7, %r9b
        andb      $127, %r10b
        orb       %cl, %r8b
        orb       %r9b, %r10b
        movb      %r8b, 7(%rdi)
        movb      %r10b, 15(%rdi)
..B1.45:
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.46:
        lea       2000+_dAtan2Tab(%rip), %rax
        movl      $-1022, %r8d
        movsd     (%rax), %xmm0
        mulsd     %xmm0, %xmm2
        mulsd     %xmm0, %xmm9
        movsd     %xmm2, -16(%rsp)
        jmp       ..B1.13
..B1.47:
        lea       2000+_dAtan2Tab(%rip), %rax
        movaps    %xmm4, %xmm0
        movl      $-1022, %r9d
        movsd     (%rax), %xmm1
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm6
        movsd     %xmm0, -24(%rsp)
        jmp       ..B1.11
..B1.48:
        testl     $1048575, 4(%rax)
        jne       ..B1.50
..B1.49:
        cmpl      $0, (%rax)
        je        ..B1.38
..B1.50:
        movsd     (%r9), %xmm0
        addsd     (%rax), %xmm0
        lea       1992+_dAtan2Tab(%rip), %rax
        movq      (%rax), %rdx
        xorl      %eax, %eax
        movsd     %xmm0, (%rdi)
        movq      %rdx, 8(%rdi)
	.cfi_restore 13
        popq      %r13
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 16
	.cfi_offset 13, -16
..B1.51:
        testl     $1048575, 4(%r9)
        jne       ..B1.50
..B1.52:
        cmpl      $0, (%r9)
        jne       ..B1.50
..B1.55:
        cmpl      $2047, %r10d
        je        ..B1.48
        jmp       ..B1.36
..B1.57:
        cmpl      $0, (%r9)
        jne       ..B1.23
        jmp       ..B1.31
        .align    16,0x90
	.cfi_endproc
	.type	__libm_atan2_k64,@function
	.size	__libm_atan2_k64,.-__libm_atan2_k64
	.data
# -- End  __libm_atan2_k64
	.section .rodata, "a"
	.align 16
	.align 16
_dAtan2Tab:
	.long	3892314112
	.long	1069799150
	.long	2332892550
	.long	1039715405
	.long	1342177280
	.long	1070305495
	.long	270726690
	.long	1041535749
	.long	939524096
	.long	1070817911
	.long	2253973841
	.long	3188654726
	.long	3221225472
	.long	1071277294
	.long	3853927037
	.long	1043226911
	.long	2818572288
	.long	1071767563
	.long	2677759107
	.long	1044314101
	.long	3355443200
	.long	1072103591
	.long	1636578514
	.long	3191094734
	.long	1476395008
	.long	1072475260
	.long	1864703685
	.long	3188646936
	.long	805306368
	.long	1072747407
	.long	192551812
	.long	3192726267
	.long	2013265920
	.long	1072892781
	.long	2240369452
	.long	1043768538
	.long	0
	.long	1072999953
	.long	3665168337
	.long	3192705970
	.long	402653184
	.long	1073084787
	.long	1227953434
	.long	3192313277
	.long	2013265920
	.long	1073142981
	.long	3853283127
	.long	1045277487
	.long	805306368
	.long	1073187261
	.long	1676192264
	.long	3192868861
	.long	134217728
	.long	1073217000
	.long	4290763938
	.long	1042034855
	.long	671088640
	.long	1073239386
	.long	994303084
	.long	3189643768
	.long	402653184
	.long	1073254338
	.long	1878067156
	.long	1042652475
	.long	1610612736
	.long	1073265562
	.long	670314820
	.long	1045138554
	.long	3221225472
	.long	1073273048
	.long	691126919
	.long	3189987794
	.long	3489660928
	.long	1073278664
	.long	1618990832
	.long	3188194509
	.long	1207959552
	.long	1073282409
	.long	2198872939
	.long	1044806069
	.long	3489660928
	.long	1073285217
	.long	2633982383
	.long	1042307894
	.long	939524096
	.long	1073287090
	.long	1059367786
	.long	3189114230
	.long	2281701376
	.long	1073288494
	.long	3158525533
	.long	1044484961
	.long	3221225472
	.long	1073289430
	.long	286581777
	.long	1044893263
	.long	4026531840
	.long	1073290132
	.long	2000245215
	.long	3191647611
	.long	134217728
	.long	1073290601
	.long	4205071590
	.long	1045035927
	.long	536870912
	.long	1073290952
	.long	2334392229
	.long	1043447393
	.long	805306368
	.long	1073291186
	.long	2281458177
	.long	3188885569
	.long	3087007744
	.long	1073291361
	.long	691611507
	.long	1044733832
	.long	3221225472
	.long	1073291478
	.long	1816229550
	.long	1044363390
	.long	2281701376
	.long	1073291566
	.long	1993843750
	.long	3189837440
	.long	134217728
	.long	1073291625
	.long	3654754496
	.long	1044970837
	.long	4026531840
	.long	1073291668
	.long	3224300229
	.long	3191935390
	.long	805306368
	.long	1073291698
	.long	2988777976
	.long	3188950659
	.long	536870912
	.long	1073291720
	.long	1030371341
	.long	1043402665
	.long	3221225472
	.long	1073291734
	.long	1524463765
	.long	1044361356
	.long	3087007744
	.long	1073291745
	.long	2754295320
	.long	1044731036
	.long	134217728
	.long	1073291753
	.long	3099629057
	.long	1044970710
	.long	2281701376
	.long	1073291758
	.long	962914160
	.long	3189838838
	.long	805306368
	.long	1073291762
	.long	3543908206
	.long	3188950786
	.long	4026531840
	.long	1073291764
	.long	1849909620
	.long	3191935434
	.long	3221225472
	.long	1073291766
	.long	1641333636
	.long	1044361352
	.long	536870912
	.long	1073291768
	.long	1373968792
	.long	1043402654
	.long	134217728
	.long	1073291769
	.long	2033191599
	.long	1044970710
	.long	3087007744
	.long	1073291769
	.long	4117947437
	.long	1044731035
	.long	805306368
	.long	1073291770
	.long	315378368
	.long	3188950787
	.long	2281701376
	.long	1073291770
	.long	2428571750
	.long	3189838838
	.long	3221225472
	.long	1073291770
	.long	1608007466
	.long	1044361352
	.long	4026531840
	.long	1073291770
	.long	1895711420
	.long	3191935434
	.long	134217728
	.long	1073291771
	.long	2031108713
	.long	1044970710
	.long	536870912
	.long	1073291771
	.long	1362518342
	.long	1043402654
	.long	805306368
	.long	1073291771
	.long	317461253
	.long	3188950787
	.long	939524096
	.long	1073291771
	.long	4117231784
	.long	1044731035
	.long	1073741824
	.long	1073291771
	.long	1607942376
	.long	1044361352
	.long	1207959552
	.long	1073291771
	.long	2428929577
	.long	3189838838
	.long	1207959552
	.long	1073291771
	.long	2031104645
	.long	1044970710
	.long	1342177280
	.long	1073291771
	.long	1895722602
	.long	3191935434
	.long	1342177280
	.long	1073291771
	.long	317465322
	.long	3188950787
	.long	1342177280
	.long	1073291771
	.long	1362515546
	.long	1043402654
	.long	1342177280
	.long	1073291771
	.long	1607942248
	.long	1044361352
	.long	1342177280
	.long	1073291771
	.long	4117231610
	.long	1044731035
	.long	1342177280
	.long	1073291771
	.long	2031104637
	.long	1044970710
	.long	1342177280
	.long	1073291771
	.long	1540251232
	.long	1045150466
	.long	1342177280
	.long	1073291771
	.long	2644671394
	.long	1045270303
	.long	1342177280
	.long	1073291771
	.long	2399244691
	.long	1045360181
	.long	1342177280
	.long	1073291771
	.long	803971124
	.long	1045420100
	.long	1476395008
	.long	1073291771
	.long	3613709523
	.long	3192879152
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192849193
	.long	1476395008
	.long	1073291771
	.long	177735686
	.long	3192826724
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192811744
	.long	1476395008
	.long	1073291771
	.long	2754716064
	.long	3192800509
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192793019
	.long	1476395008
	.long	1073291771
	.long	1895722605
	.long	3192787402
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192783657
	.long	1476395008
	.long	1073291771
	.long	3613709523
	.long	3192780848
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192778976
	.long	1476395008
	.long	1073291771
	.long	177735686
	.long	3192777572
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192776635
	.long	1476395008
	.long	1073291771
	.long	2754716064
	.long	3192775933
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192775465
	.long	1476395008
	.long	1073291771
	.long	1895722605
	.long	3192775114
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192774880
	.long	1476395008
	.long	1073291771
	.long	3613709523
	.long	3192774704
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192774587
	.long	1476395008
	.long	1073291771
	.long	177735686
	.long	3192774500
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192774441
	.long	1476395008
	.long	1073291771
	.long	2754716064
	.long	3192774397
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192774368
	.long	1476395008
	.long	1073291771
	.long	1895722605
	.long	3192774346
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192774331
	.long	1476395008
	.long	1073291771
	.long	3613709523
	.long	3192774320
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192774313
	.long	1476395008
	.long	1073291771
	.long	177735686
	.long	3192774308
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192774304
	.long	1476395008
	.long	1073291771
	.long	2754716064
	.long	3192774301
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192774299
	.long	1476395008
	.long	1073291771
	.long	1895722605
	.long	3192774298
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192774297
	.long	1476395008
	.long	1073291771
	.long	3613709523
	.long	3192774296
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192774296
	.long	1476395008
	.long	1073291771
	.long	177735686
	.long	3192774296
	.long	1476395008
	.long	1073291771
	.long	3490996172
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	2754716064
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	2263862659
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1895722605
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1650295902
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1466225875
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1343512524
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1251477510
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1190120835
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1144103328
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1113424990
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1090416237
	.long	3192774295
	.long	1476395008
	.long	1073291771
	.long	1075077068
	.long	3192774295
	.long	1431655765
	.long	3218429269
	.long	2576978363
	.long	1070176665
	.long	2453154343
	.long	3217180964
	.long	4189149139
	.long	1069314502
	.long	1775019125
	.long	3216459198
	.long	273199057
	.long	1068739452
	.long	874748308
	.long	3215993277
	.long	0
	.long	1069547520
	.long	0
	.long	1072693248
	.long	0
	.long	1073741824
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.long	1413754136
	.long	1072243195
	.long	856972295
	.long	1015129638
	.long	1413754136
	.long	1073291771
	.long	856972295
	.long	1016178214
	.long	1413754136
	.long	1074340347
	.long	856972295
	.long	1017226790
	.long	2134057426
	.long	1073928572
	.long	1285458442
	.long	1016756537
	.long	0
	.long	3220176896
	.long	0
	.long	0
	.long	0
	.long	2144337920
	.long	0
	.long	1048576
	.long	33554432
	.long	1101004800
	.type	_dAtan2Tab,@object
	.size	_dAtan2Tab,2024
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
