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
	.file "libm_log1pl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_log1p_k80
	.text
       .align    16,0x90
	.globl __libm_log1p_k80
__libm_log1p_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        movl      12(%ebp), %esi
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movzwl    8(%esi), %ecx
        andl      $32767, %ecx
        movl      %eax, 116(%esp)
        movl      24(%esi), %eax
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      8(%ebp), %edi
        lea       (%eax,%ecx), %edx
        cmpl      $16511, %edx
        jge       ..B1.9
..B1.2:
        cmpl      $16255, %edx
        jle       ..B1.8
..B1.3:
        addl      $-8383, %ecx
        cmpl      $16000, %ecx
        jbe       ..B1.6
..B1.4:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B1.5:
        movl      24(%esi), %eax
..B1.6:
        fldt      (%esi)
        addl      $16383, %eax
        fldt      12(%esi)
        andl      $32767, %eax
        andb      $127, 41(%esp)
        lea       68(%esp), %edx
        movzwl    -28(%edx), %ecx
        andl      $-32768, %ecx
        orl       %eax, %ecx
        lea       92(%esp), %eax
        movw      %cx, -52(%eax)
        movl      $-2147483648, -56(%eax)
        movl      $0, -60(%eax)
        fldt      -60(%eax)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     (%edx)
        fmulp     %st, %st(1)
        fstpt     -12(%eax)
        call      __libm_log1pl_k80.
..B1.7:
        fldt      92(%esp)
        fmul      %st(1), %st
        movl      $0, 24(%edi)
        fstpt     (%edi)
        fldt      104(%esp)
        fmulp     %st, %st(1)
        fstpt     12(%edi)
        jmp       ..B1.19
..B1.8:
        movl      (%esi), %eax
        movl      %eax, (%edi)
        movl      4(%esi), %edx
        movl      %edx, 4(%edi)
        movl      8(%esi), %ecx
        movl      %ecx, 8(%edi)
        movl      12(%esi), %eax
        movl      %eax, 12(%edi)
        movl      16(%esi), %edx
        movl      %edx, 16(%edi)
        movl      20(%esi), %ecx
        movl      %ecx, 20(%edi)
        movl      24(%esi), %esi
        movl      %esi, 24(%edi)
        jmp       ..B1.19
..B1.9:
        addl      $4, %esp
        pushl     %esi
        call      __libm_normalizel_k80
..B1.10:
        fildl     24(%esi)
        fstpt     32(%esp)
        lea       92(%esp), %eax
        fldt      (%esi)
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     -24(%eax)
        fldt      12(%esi)
        fstpt     12(%esp)
        fldt      12(%esp)
        fstpt     -12(%eax)
        movl      %eax, 24(%esp)
        call      __libm_logl_k80
..B1.11:
        fldt      92(%esp)
        fmul      %st(1), %st
        fstpt     92(%esp)
        fldt      92(%esp)
        fldt      104(%esp)
        fmulp     %st, %st(2)
        movzwl    100(%esp), %edx
        andl      $32767, %edx
        fxch      %st(1)
        fstpt     104(%esp)
        fldt      104(%esp)
        fldt      216+_CONSTANTS@GOTOFF(%ebx)
        fldt      228+_CONSTANTS@GOTOFF(%ebx)
        fldt      32(%esp)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     44(%esp)
        fldt      44(%esp)
        fld       %st(0)
        movzwl    52(%esp), %eax
        fadd      %st(5), %st
        fxch      %st(3)
        fmulp     %st, %st(2)
        andl      $32767, %eax
        fxch      %st(1)
        fstpt     56(%esp)
        fldt      56(%esp)
        cmpl      %edx, %eax
        movl      $0, 24(%edi)
        jg        ..B1.16
..B1.12:
        jne       ..B1.17
..B1.13:
        movl      48(%esp), %eax
        movl      96(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B1.16
..B1.14:
        jne       ..B1.17
..B1.15:
        movl      44(%esp), %eax
        cmpl      92(%esp), %eax
        jbe       ..B1.17
..B1.16:
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B1.18
..B1.17:
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
..B1.18:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%edi)
        fldt      (%edi)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%edi)
..B1.19:
        movl      116(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.21
..B1.20:
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.21:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	__libm_log1p_k80,@function
	.size	__libm_log1p_k80,.-__libm_log1p_k80
	.data
# -- End  __libm_log1p_k80
	.text
# -- Begin  __libm_log1pl_k80
	.text
       .align    16,0x90
	.hidden __libm_log1pl_k80
	.globl __libm_log1pl_k80
__libm_log1pl_k80:
# parameter 1: %eax
# parameter 2: %edx
..B2.1:
..L3:

        movl      4(%esp), %eax
        movl      8(%esp), %edx
	.hidden __libm_log1pl_k80.
	.globl __libm_log1pl_k80.
__libm_log1pl_k80.:
        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $404, %esp
        movl      %eax, %ecx
        fldt      (%edx)
        fld       %st(0)
        fldt      12(%edx)
        movl      %gs:20, %eax
        fadd      %st, %st(1)
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        fxch      %st(1)
        fsub      %st, %st(2)
        xorl      %esp, %eax
        fxch      %st(2)
        fsubrp    %st, %st(1)
        fldt      48+_CONSTANTS@GOTOFF(%ebx)
        movl      %eax, 388(%esp)
        fstpt     12(%ecx)
        fldt      12(%ecx)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        movzbl    9(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        movzwl    8(%esp), %esi
        movl      %eax, 16(%esp)
        andl      $32767, %esi
        shll      $15, %eax
        movl      4(%esp), %edi
        orl       %esi, %eax
        movl      %edi, 12(%esp)
        shll      $16, %eax
        shrl      $16, %edi
        orl       %edi, %eax
        cmpl      $32767, %esi
        je        ..B2.116
..B2.2:
        cmpl      $-1073774592, %eax
        jae       ..B2.101
..B2.3:
        fstp      %st(1)
        fxch      %st(1)
        fstpt     (%ecx)
        fldt      (%ecx)
..B2.4:
        cmpl      $16375, %esi
        jge       ..B2.52
..B2.5:
        fstp      %st(0)
        cmpl      $16363, %esi
        jge       ..B2.30
..B2.6:
        fstp      %st(0)
        cmpl      $16298, %esi
        jge       ..B2.8
..B2.7:
        fldt      (%edx)
        fstpt     (%ecx)
        fldt      12(%edx)
        fstpt     12(%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        jmp       ..B2.104
..B2.8:
        fldt      (%edx)
        fld       %st(0)
        fld       %st(1)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(3)
        fsub      %st(3), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st(0), %st
        fld       %st(0)
        fld       %st(4)
        fmul      %st(5), %st
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(5)
        fxch      %st(5)
        fstpt     16(%esp)
        fldt      16(%esp)
        fxch      %st(3)
        fstpt     32(%esp)
        fldt      32(%esp)
        fldt      12(%edx)
        fmulp     %st, %st(6)
        fxch      %st(5)
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      96+_CONSTANTS@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(2), %st
        fstpt     48(%esp)
        fldt      48(%esp)
        fxch      %st(1)
        fmul      %st(3), %st
        fstpt     (%esp)
        fldt      (%esp)
        fstpt     304(%esp)
        fld       %st(1)
        fsub      %st(5), %st
        faddp     %st, %st(5)
        fld       %st(1)
        fsub      %st(5), %st
        fxch      %st(1)
        fstpt     292(%esp)
        fxch      %st(6)
        fmulp     %st, %st(2)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(3)
        fmul      %st(6), %st
        fxch      %st(4)
        fmul      %st(5), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(4)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      12(%edx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fld       %st(0)
        fld       %st(1)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fld       %st(0)
        fmul      %st(4), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(1)
        fsub      %st(2), %st
        fxch      %st(1)
        fmul      %st(6), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     64(%esp)
        fldt      64(%esp)
        fld       %st(5)
        fsub      %st(1), %st
        fld       %st(2)
        fmul      %st(2), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmul      %st, %st(4)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(4), %st
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(6), %st
        faddp     %st, %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     316(%esp)
        fldt      316(%esp)
        fsubr     %st, %st(1)
        movzwl    324(%esp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     80(%esp)
        fldt      80(%esp)
        andl      $32767, %eax
        fstpt     328(%esp)
        fldt      16(%esp)
        fld       %st(0)
        fxch      %st(1)
        fmul      %st(3), %st
        fldt      64(%esp)
        fmul      %st, %st(2)
        fldt      32(%esp)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      (%edx)
        fmul      %st, %st(5)
        fldt      12(%edx)
        fmulp     %st, %st(7)
        fxch      %st(6)
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(4)
        fstpt     340(%esp)
        fldt      340(%esp)
        fadd      %st, %st(1)
        fxch      %st(4)
        fmulp     %st, %st(3)
        movzwl    348(%esp), %esi
        andl      $32767, %esi
        fxch      %st(2)
        fstpt     352(%esp)
        fldt      352(%esp)
        cmpl      %esi, %eax
        fldt      48(%esp)
        fldt      80(%esp)
        jg        ..B2.13
..B2.9:
        jne       ..B2.14
..B2.10:
        movl      320(%esp), %eax
        movl      344(%esp), %esi
        cmpl      %esi, %eax
        ja        ..B2.13
..B2.11:
        jne       ..B2.14
..B2.12:
        movl      316(%esp), %eax
        cmpl      340(%esp), %eax
        jbe       ..B2.14
..B2.13:
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(5)
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        jmp       ..B2.15
..B2.14:
        fxch      %st(4)
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
..B2.15:
        fld       %st(2)
        movzwl    300(%esp), %eax
        fadd      %st(1), %st
        fstpt     364(%esp)
        fldt      364(%esp)
        andl      $32767, %eax
        movzwl    372(%esp), %esi
        fld       %st(2)
        andl      $32767, %esi
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        cmpl      %esi, %eax
        fxch      %st(2)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     376(%esp)
        fldt      376(%esp)
        jg        ..B2.20
..B2.16:
        jne       ..B2.21
..B2.17:
        movl      296(%esp), %eax
        movl      368(%esp), %esi
        cmpl      %esi, %eax
        ja        ..B2.20
..B2.18:
        jne       ..B2.21
..B2.19:
        movl      292(%esp), %eax
        cmpl      364(%esp), %eax
        jbe       ..B2.21
..B2.20:
        fldt      (%esp)
        fxch      %st(3)
        fsub      %st(4), %st
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        jmp       ..B2.22
..B2.21:
        fldt      (%esp)
        fxch      %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.22:
        fld       %st(0)
        movzwl    8(%edx), %eax
        fadd      %st(2), %st
        fstpt     268(%esp)
        fldt      268(%esp)
        andl      $32767, %eax
        movzwl    276(%esp), %esi
        fsubr     %st, %st(2)
        andl      $32767, %esi
        fxch      %st(2)
        faddp     %st, %st(1)
        fstpt     280(%esp)
        fldt      280(%esp)
        cmpl      %esi, %eax
        fxch      %st(1)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     12(%ecx)
        fldt      12(%ecx)
        jg        ..B2.27
..B2.23:
        jne       ..B2.28
..B2.24:
        movl      272(%esp), %eax
        cmpl      4(%edx), %eax
        jb        ..B2.27
..B2.25:
        jne       ..B2.28
..B2.26:
        movl      (%edx), %eax
        cmpl      268(%esp), %eax
        jbe       ..B2.28
..B2.27:
        fldt      (%edx)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        jmp       ..B2.29
..B2.28:
        fldt      (%edx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      12(%edx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
..B2.29:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        jmp       ..B2.104
..B2.30:
        fldt      (%edx)
        fld       %st(0)
        fld       %st(1)
        fld       %st(2)
        fxch      %st(4)
        fstpt     48(%esp)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(1)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(3)
        fsubr     %st, %st(5)
        fxch      %st(1)
        fmul      %st(5), %st
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(4), %st
        fsubr     %st, %st(4)
        fxch      %st(1)
        faddp     %st, %st(4)
        fld       %st(5)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fxch      %st(2)
        fstpt     64(%esp)
        fldt      64(%esp)
        fldt      12(%edx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(0), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fmul      %st, %st(3)
        fld       %st(0)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(2)
        fmul      %st(4), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(5)
        fxch      %st(4)
        fstpt     16(%esp)
        fldt      16(%esp)
        fld       %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(4)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(5), %st
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fxch      %st(3)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fld       %st(4)
        fmulp     %st, %st(3)
        fstpt     80(%esp)
        fldt      80(%esp)
        fldt      12(%edx)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fxch      %st(2)
        fmulp     %st, %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(3)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fmul      %st, %st(3)
        fld       %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     96(%esp)
        fldt      96(%esp)
        fld       %st(1)
        fsub      %st(1), %st
        fldt      64(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fldt      12(%edx)
        fmul      %st(3), %st
        faddp     %st, %st(5)
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fstpt     32(%esp)
        fldt      32(%esp)
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fstpt     112(%esp)
        fldt      112(%esp)
        faddp     %st, %st(3)
        fldt      48(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fld       %st(0)
        fsub      %st(2), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fld       %st(1)
        fsub      %st(3), %st
        fxch      %st(3)
        fmul      %st(6), %st
        fxch      %st(2)
        fmulp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(5)
        fldt      96(%esp)
        fmul      %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(1)
        fldt      16(%esp)
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     316(%esp)
        fldt      316(%esp)
        fsubr     %st, %st(3)
        movzwl    324(%esp), %eax
        fxch      %st(3)
        faddp     %st, %st(1)
        andl      $32767, %eax
        fstpt     328(%esp)
        fldt      328(%esp)
        fldt      96+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     16(%esp)
        fldt      16(%esp)
        fstpt     304(%esp)
        fldt      80(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     292(%esp)
        fldt      292(%esp)
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fldt      32(%esp)
        fmul      %st(1), %st
        fstpt     340(%esp)
        fldt      340(%esp)
        movzwl    348(%esp), %esi
        andl      $32767, %esi
        fstpt     32(%esp)
        fldt      32(%esp)
        cmpl      %esi, %eax
        fldt      112(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     352(%esp)
        fldt      352(%esp)
        fldt      204+_CONSTANTS@GOTOFF(%ebx)
        fldt      192+_CONSTANTS@GOTOFF(%ebx)
        fldt      48(%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      180+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      168+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      156+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      144+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      132+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fld       %st(5)
        faddp     %st, %st(3)
        fldt      (%esp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        fldt      (%edx)
        jg        ..B2.35
..B2.31:
        jne       ..B2.36
..B2.32:
        movl      320(%esp), %eax
        movl      344(%esp), %esi
        cmpl      %esi, %eax
        ja        ..B2.35
..B2.33:
        jne       ..B2.36
..B2.34:
        movl      316(%esp), %eax
        cmpl      340(%esp), %eax
        jbe       ..B2.36
..B2.35:
        fldt      32(%esp)
        fxch      %st(3)
        fsubr     %st, %st(6)
        fxch      %st(3)
        faddp     %st, %st(6)
        fxch      %st(1)
        faddp     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(4)
        jmp       ..B2.37
..B2.36:
        fldt      32(%esp)
        fsub      %st(3), %st
        faddp     %st, %st(6)
        fxch      %st(4)
        faddp     %st, %st(5)
        faddp     %st, %st(4)
..B2.37:
        fld       %st(3)
        movzwl    300(%esp), %eax
        fadd      %st(1), %st
        fstpt     364(%esp)
        fldt      364(%esp)
        andl      $32767, %eax
        movzwl    372(%esp), %esi
        fsubr     %st, %st(1)
        andl      $32767, %esi
        fxch      %st(1)
        faddp     %st, %st(4)
        fxch      %st(3)
        fstpt     376(%esp)
        fldt      376(%esp)
        fld       %st(1)
        cmpl      %esi, %eax
        fadd      %st(4), %st
        jg        ..B2.42
..B2.38:
        jne       ..B2.43
..B2.39:
        movl      296(%esp), %eax
        movl      368(%esp), %esi
        cmpl      %esi, %eax
        ja        ..B2.42
..B2.40:
        jne       ..B2.43
..B2.41:
        movl      292(%esp), %eax
        cmpl      364(%esp), %eax
        jbe       ..B2.43
..B2.42:
        fsubr     %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      16(%esp)
        faddp     %st, %st(1)
        fxch      %st(2)
        jmp       ..B2.44
..B2.43:
        fsubr     %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(4)
        fldt      16(%esp)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
..B2.44:
        fld       %st(2)
        movzwl    8(%edx), %eax
        fadd      %st(1), %st
        fstpt     268(%esp)
        fldt      268(%esp)
        andl      $32767, %eax
        movzwl    276(%esp), %esi
        fsubr     %st, %st(1)
        andl      $32767, %esi
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     280(%esp)
        fldt      280(%esp)
        cmpl      %esi, %eax
        fxch      %st(1)
        fadd      %st(2), %st
        fstpt     12(%ecx)
        fldt      12(%ecx)
        jg        ..B2.49
..B2.45:
        jne       ..B2.50
..B2.46:
        movl      272(%esp), %eax
        cmpl      4(%edx), %eax
        jb        ..B2.49
..B2.47:
        jne       ..B2.50
..B2.48:
        movl      (%edx), %eax
        cmpl      268(%esp), %eax
        jbe       ..B2.50
..B2.49:
        fldt      (%edx)
        fsub      %st(1), %st
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      12(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        jmp       ..B2.51
..B2.50:
        fldt      (%edx)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        fldt      12(%edx)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
..B2.51:
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fldt      (%esp)
        faddp     %st, %st(1)
        fstpt     12(%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        jmp       ..B2.104
..B2.52:
        fstp      %st(1)
        fldt      (%edx)
        movzwl    8(%edx), %esi
        fld       %st(0)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        andl      $32767, %esi
        movzwl    8+_CONSTANTS@GOTOFF(%ebx), %eax
        cmpl      $16383, %esi
        movl      %eax, 12(%esp)
        fadd      %st, %st(1)
        jg        ..B2.57
..B2.53:
        jne       ..B2.58
..B2.54:
        movl      4(%edx), %eax
        cmpl      4+_CONSTANTS@GOTOFF(%ebx), %eax
        ja        ..B2.57
..B2.55:
        jne       ..B2.58
..B2.56:
        movl      (%edx), %eax
        cmpl      _CONSTANTS@GOTOFF(%ebx), %eax
        jbe       ..B2.58
..B2.57:
        fldt      12(%edx)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(1)
        fadd      %st, %st(3)
        fxch      %st(3)
        fadd      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.59
..B2.58:
        fldt      12(%edx)
        fld       %st(1)
        fsub      %st(3), %st
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
..B2.59:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     128(%esp)
        fldt      128(%esp)
        fsubr     %st, %st(2)
        movzwl    136(%esp), %edx
        fxch      %st(2)
        faddp     %st, %st(1)
        movl      132(%esp), %eax
        andl      $32767, %edx
        shrl      $23, %eax
        movzbl    %al, %eax
        movl      %edx, 16(%esp)
        fstpt     140(%esp)
        fldt      140(%esp)
        lea       -16383(%edx), %edx
        cmpl      $16383, %edx
        flds      __libm_rcp_table_256@GOTOFF(%ebx,%eax,4)
        jge       ..B2.61
..B2.60:
        movl      %edx, %esi
        negl      %esi
        addl      $16383, %esi
        movl      12(%esp), %edi
        andl      $32767, %esi
        andl      $-32768, %edi
        fxch      %st(3)
        fstpt     (%esp)
        fldt      (%esp)
        orl       %esi, %edi
        movw      %di, 8(%esp)
        fldt      (%esp)
        fxch      %st(1)
        fxch      %st(4)
        fxch      %st(1)
        jmp       ..B2.62
..B2.61:
        fldt      240+_CONSTANTS@GOTOFF(%ebx)
..B2.62:
        movzwl    148(%esp), %esi
        andl      $32767, %esi
        addl      $128, %esi
        fmul      %st, %st(3)
        cmpl      16(%esp), %esi
        jle       ..B2.64
..B2.63:
        fmulp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.65
..B2.64:
        fstp      %st(0)
        fstp      %st(1)
        fld       %st(3)
..B2.65:
        fldt      36+_CONSTANTS@GOTOFF(%ebx)
        movl      %edx, 112(%esp)
        fxch      %st(4)
        fstpt     96(%esp)
        shll      $4, %eax
        fstpt     16(%esp)
        fld       %st(1)
        fildl     112(%esp)
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fxch      %st(5)
        fmulp     %st, %st(1)
        fld       %st(3)
        fxch      %st(5)
        faddl     __libm_logl_table_256@GOTOFF(%eax,%ebx)
        fstpt     48(%esp)
        fldt      48(%esp)
        fstpt     (%esp)
        fld       %st(3)
        fxch      %st(4)
        fmul      %st(6), %st
        fxch      %st(1)
        faddl     8+__libm_logl_table_256@GOTOFF(%ebx,%eax)
        fstpt     32(%esp)
        fld       %st(2)
        fldt      60+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(3)
        fxch      %st(3)
        fsubr     %st, %st(5)
        faddp     %st, %st(5)
        fxch      %st(4)
        fsubr     %st, %st(5)
        fxch      %st(2)
        fmul      %st, %st(4)
        fstpt     64(%esp)
        fld       %st(2)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fld       %st(2)
        fsub      %st(4), %st
        fld       %st(2)
        fmul      %st(5), %st
        fxch      %st(5)
        fmul      %st(6), %st
        fxch      %st(1)
        fmul      %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(5), %st
        fsubr     %st, %st(5)
        fxch      %st(3)
        faddp     %st, %st(5)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fstpt     128(%esp)
        fldt      128(%esp)
        fsubr     %st, %st(1)
        movzwl    136(%esp), %eax
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %eax
        fxch      %st(1)
        fstpt     140(%esp)
        fldt      140(%esp)
        cmpl      $16383, %eax
        fldt      96(%esp)
        fld       %st(0)
        fsubr     %st(3), %st
        fstpt     80(%esp)
        jg        ..B2.70
..B2.66:
        jne       ..B2.71
..B2.67:
        movl      132(%esp), %eax
        cmpl      4+_CONSTANTS@GOTOFF(%ebx), %eax
        ja        ..B2.70
..B2.68:
        jne       ..B2.71
..B2.69:
        movl      128(%esp), %eax
        cmpl      _CONSTANTS@GOTOFF(%ebx), %eax
        jbe       ..B2.71
..B2.70:
        fldt      80(%esp)
        fsubrp    %st, %st(3)
        fsubr     %st, %st(2)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        jmp       ..B2.72
..B2.71:
        fldt      80(%esp)
        fadd      %st(1), %st
        fsubrp    %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(1)
..B2.72:
        fldt      80(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     160(%esp)
        fldt      160(%esp)
        fsubr     %st, %st(1)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     176(%esp)
        fxch      %st(2)
        fstpt     96(%esp)
        fld       %st(0)
        fxch      %st(2)
        fstpt     80(%esp)
        fldt      80(%esp)
        fxch      %st(1)
        fstpt     16(%esp)
        fldt      16(%esp)
        fldt      64(%esp)
        fld       %st(0)
        fmul      %st(2), %st
        fsubr     %st, %st(4)
        faddp     %st, %st(4)
        fld       %st(3)
        fld       %st(2)
        fsub      %st(5), %st
        fmul      %st, %st(1)
        fxch      %st(1)
        fadd      %st(0), %st
        fld       %st(0)
        fld       %st(6)
        fmul      %st(7), %st
        fadd      %st, %st(1)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fld       %st(2)
        fmul      %st(3), %st
        faddp     %st, %st(2)
        fld       %st(5)
        fmul      %st(5), %st
        fadd      %st(0), %st
        faddp     %st, %st(2)
        fld       %st(1)
        fadd      %st(1), %st
        fmul      %st, %st(4)
        fsubr     %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     128(%esp)
        fldt      128(%esp)
        fld       %st(1)
        fsub      %st(4), %st
        faddp     %st, %st(4)
        fxch      %st(1)
        fstpt     208(%esp)
        fldt      208(%esp)
        fld       %st(6)
        fmul      %st(4), %st
        fxch      %st(1)
        fsub      %st(4), %st
        fxch      %st(3)
        fmul      %st, %st(4)
        fxch      %st(7)
        fstpt     192(%esp)
        fldt      192(%esp)
        fmul      %st(3), %st
        faddp     %st, %st(4)
        fld       %st(3)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(4)
        faddp     %st, %st(1)
        fxch      %st(2)
        fmul      %st(6), %st
        faddp     %st, %st(2)
        fld       %st(3)
        fmulp     %st, %st(1)
        fld       %st(4)
        fldt      208(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fld       %st(0)
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fstpt     224(%esp)
        fldt      224(%esp)
        fldt      64(%esp)
        fldt      192(%esp)
        fxch      %st(1)
        fmul      %st(2), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     240(%esp)
        fldt      240(%esp)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(6)
        fxch      %st(6)
        faddp     %st, %st(1)
        fld       %st(2)
        fmulp     %st, %st(4)
        fldt      224(%esp)
        fmul      %st, %st(5)
        fxch      %st(5)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     144(%esp)
        fldt      144(%esp)
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fstpt     256(%esp)
        fldt      256(%esp)
        faddp     %st, %st(2)
        fldt      176(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     112(%esp)
        fldt      64(%esp)
        fldt      240(%esp)
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fld       %st(0)
        fsub      %st(3), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fmul      %st(2), %st
        fld       %st(1)
        fsub      %st(4), %st
        fmul      %st, %st(3)
        fxch      %st(4)
        fmul      %st(7), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(3)
        fmulp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(2)
        fxch      %st(4)
        fmulp     %st, %st(2)
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     316(%esp)
        fldt      316(%esp)
        fsubr     %st, %st(2)
        movzwl    324(%esp), %eax
        fxch      %st(2)
        faddp     %st, %st(1)
        andl      $32767, %eax
        fstpt     328(%esp)
        fldt      328(%esp)
        fldt      96+_CONSTANTS@GOTOFF(%ebx)
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fldt      208(%esp)
        fmul      %st(2), %st
        fstpt     292(%esp)
        fldt      292(%esp)
        fldt      128(%esp)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fstpt     128(%esp)
        fldt      128(%esp)
        fstpt     304(%esp)
        fldt      144(%esp)
        fmul      %st(1), %st
        fstpt     340(%esp)
        fldt      340(%esp)
        movzwl    348(%esp), %edx
        andl      $32767, %edx
        fstpt     144(%esp)
        fldt      144(%esp)
        cmpl      %edx, %eax
        fldt      256(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     352(%esp)
        fldt      352(%esp)
        fldt      204+_CONSTANTS@GOTOFF(%ebx)
        fldt      192+_CONSTANTS@GOTOFF(%ebx)
        fldt      176(%esp)
        fmul      %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      180+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      168+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      156+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmul      %st, %st(1)
        fldt      144+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fmulp     %st, %st(1)
        fldt      132+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(1)
        fldt      112(%esp)
        fmulp     %st, %st(1)
        fstpt     112(%esp)
        fld       %st(4)
        faddp     %st, %st(2)
        fldt      96(%esp)
        fldt      160(%esp)
        jg        ..B2.77
..B2.73:
        jne       ..B2.78
..B2.74:
        movl      320(%esp), %eax
        movl      344(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.77
..B2.75:
        jne       ..B2.78
..B2.76:
        movl      316(%esp), %eax
        cmpl      340(%esp), %eax
        jbe       ..B2.78
..B2.77:
        fldt      144(%esp)
        fxch      %st(4)
        fsubr     %st, %st(7)
        fxch      %st(4)
        faddp     %st, %st(7)
        fxch      %st(2)
        faddp     %st, %st(6)
        fxch      %st(4)
        faddp     %st, %st(5)
        fxch      %st(3)
        fxch      %st(4)
        jmp       ..B2.79
..B2.78:
        fldt      144(%esp)
        fsub      %st(4), %st
        faddp     %st, %st(7)
        fxch      %st(5)
        faddp     %st, %st(6)
        fxch      %st(5)
        faddp     %st, %st(1)
..B2.79:
        fld       %st(0)
        movzwl    300(%esp), %eax
        fadd      %st(2), %st
        fstpt     364(%esp)
        fldt      364(%esp)
        andl      $32767, %eax
        movzwl    372(%esp), %edx
        fld       %st(3)
        andl      $32767, %edx
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        cmpl      %edx, %eax
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     376(%esp)
        fldt      376(%esp)
        jg        ..B2.84
..B2.80:
        jne       ..B2.85
..B2.81:
        movl      296(%esp), %eax
        movl      368(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.84
..B2.82:
        jne       ..B2.85
..B2.83:
        movl      292(%esp), %eax
        cmpl      364(%esp), %eax
        jbe       ..B2.85
..B2.84:
        fxch      %st(1)
        fsubr     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fldt      128(%esp)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.86
..B2.85:
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      128(%esp)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.86:
        fld       %st(0)
        movzwl    24(%esp), %eax
        fadd      %st(2), %st
        fstpt     268(%esp)
        fldt      268(%esp)
        andl      $32767, %eax
        movzwl    276(%esp), %edx
        fld       %st(3)
        andl      $32767, %edx
        fadd      %st(1), %st
        fxch      %st(1)
        fsubr     %st, %st(3)
        cmpl      %edx, %eax
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     280(%esp)
        fldt      280(%esp)
        jg        ..B2.91
..B2.87:
        jne       ..B2.92
..B2.88:
        movl      20(%esp), %eax
        movl      272(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.91
..B2.89:
        jne       ..B2.92
..B2.90:
        movl      16(%esp), %eax
        cmpl      268(%esp), %eax
        jbe       ..B2.92
..B2.91:
        fldt      80(%esp)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        jmp       ..B2.93
..B2.92:
        fldt      80(%esp)
        fxch      %st(2)
        fsubr     %st, %st(3)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
..B2.93:
        fldt      112(%esp)
        fld       %st(1)
        movzwl    8(%esp), %edx
        fadd      %st(3), %st
        fstpt     16(%esp)
        fldt      16(%esp)
        andl      $32767, %edx
        movzwl    24(%esp), %eax
        fsubr     %st, %st(3)
        andl      $32767, %eax
        fxch      %st(2)
        faddp     %st, %st(3)
        cmpl      %edx, %eax
        faddp     %st, %st(2)
        fldt      48(%esp)
        fadd      %st(1), %st
        jg        ..B2.98
..B2.94:
        jne       ..B2.99
..B2.95:
        movl      20(%esp), %eax
        movl      4(%esp), %edx
        cmpl      %edx, %eax
        ja        ..B2.98
..B2.96:
        jne       ..B2.99
..B2.97:
        movl      16(%esp), %eax
        cmpl      (%esp), %eax
        jbe       ..B2.99
..B2.98:
        fldt      48(%esp)
        fxch      %st(1)
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      32(%esp)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        jmp       ..B2.100
..B2.99:
        fldt      48(%esp)
        fsub      %st(1), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      32(%esp)
        faddp     %st, %st(1)
..B2.100:
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%ecx)
        jmp       ..B2.104
..B2.101:
        cmpl      $1, 16(%esp)
        je        ..B2.108
..B2.123:
        fstp      %st(1)
..B2.102:
        fldt      12+_CONSTANTS@GOTOFF(%ebx)
        fxch      %st(1)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.103
        je        ..B2.107
..B2.103:
        fstp      %st(0)
        fldt      252+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
..B2.104:
        movl      388(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B2.106
..B2.105:
        addl      $404, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B2.106:
        fstp      %st(0)
        call      __stack_chk_fail@PLT
..B2.107:
        fdivp     %st, %st(1)
        fstpt     (%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        jmp       ..B2.104
..B2.108:
        cmpl      $16383, %esi
        jne       ..B2.123
..B2.109:
        cmpl      $-2147483648, 12(%esp)
        jne       ..B2.123
..B2.110:
        cmpl      $0, (%esp)
        jne       ..B2.123
..B2.111:
        fxch      %st(1)
        fstpt     (%esp)
        movzbl    9(%esp), %eax
        testl     $128, %eax
        jne       ..B2.102
..B2.112:
        cmpl      $0, 4(%esp)
        jne       ..B2.115
..B2.114:
        cmpl      $0, (%esp)
        je        ..B2.102
..B2.115:
        fxch      %st(1)
        fstpt     (%ecx)
        fldt      (%ecx)
        jmp       ..B2.4
..B2.116:
        fstp      %st(1)
        cmpl      $0, 16(%esp)
        je        ..B2.120
..B2.117:
        cmpl      $-2147483648, 12(%esp)
        jne       ..B2.120
..B2.118:
        cmpl      $0, (%esp)
        jne       ..B2.120
..B2.119:
        fstp      %st(0)
        fldt      252+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     (%ecx)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        jmp       ..B2.104
..B2.120:
        fstp      %st(1)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(1)
        fxch      %st(1)
        fstpt     (%ecx)
        jmp       ..B2.104
        .align    16,0x90
	.type	__libm_log1pl_k80,@function
	.size	__libm_log1pl_k80,.-__libm_log1pl_k80
	.data
# -- End  __libm_log1pl_k80
	.section .rodata, "a"
	.align 16
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	53711
	.word	6135
	.word	45426
	.word	16382
	.word	0
	.word	2022
	.word	26416
	.word	37831
	.word	62295
	.word	16333
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	32768
	.word	16415
	.word	0
	.word	43690
	.word	43690
	.word	43690
	.word	43690
	.word	16381
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	16317
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16386
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49149
	.word	0
	.word	52429
	.word	52428
	.word	52428
	.word	52428
	.word	16380
	.word	0
	.word	43691
	.word	43690
	.word	43690
	.word	43690
	.word	49148
	.word	0
	.word	37449
	.word	18724
	.word	9362
	.word	37449
	.word	16380
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49148
	.word	0
	.word	58254
	.word	36408
	.word	14563
	.word	58254
	.word	16379
	.word	0
	.word	52429
	.word	52428
	.word	52428
	.word	52428
	.word	49147
	.word	0
	.word	11916
	.word	59578
	.word	35746
	.word	47662
	.word	16379
	.word	0
	.word	0
	.word	0
	.word	6136
	.word	45426
	.word	16382
	.word	0
	.word	12704
	.word	55409
	.word	6480
	.word	47298
	.word	49116
	.word	0
	.word	0
	.word	0
	.word	0
	.word	16384
	.word	0
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,252
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_logl_table_256
	.hidden __libm_logl_k80
	.hidden __libm_normalizel_k80
	.section .note.GNU-stack, ""
# End
