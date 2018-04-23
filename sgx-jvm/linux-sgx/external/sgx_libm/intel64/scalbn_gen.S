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
	.file "scalbn_gen.c"
	.text
..TXTST0:
# -- Begin  scalbn
	.text
       .align    16,0x90
	.globl scalbn
scalbn:
# parameter 1: %xmm0
# parameter 2: %edi
..B1.1:
	.cfi_startproc
..___tag_value_scalbn.1:
..L2:

        movsd     %xmm0, -8(%rsp)
        movzwl    -2(%rsp), %esi
        movl      %esi, %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $2047, %ecx
        je        ..B1.17
..B1.2:
        xorb      %r8b, %r8b
        testl     %ecx, %ecx
        jne       ..B1.7
..B1.3:
        testl     $1048575, -4(%rsp)
        jne       ..B1.6
..B1.4:
        cmpl      $0, -8(%rsp)
        jne       ..B1.6
..B1.5:
        movsd     -8(%rsp), %xmm0
        ret       
..B1.6:
        lea       _TWO_55(%rip), %rax
        movb      $1, %r8b
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        movsd     %xmm0, -8(%rsp)
        movzwl    -2(%rsp), %esi
        movl      %esi, %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        addl      $-55, %ecx
..B1.7:
        movl      $65536, %eax
        cmpl      $65536, %edi
        cmovg     %eax, %edi
        movl      $-65536, %edx
        cmpl      $-65536, %edi
        cmovle    %edx, %edi
        lea       (%rcx,%rdi), %eax
        testl     %eax, %eax
        jle       ..B1.12
..B1.8:
        cmpl      $2047, %eax
        jge       ..B1.11
..B1.9:
        andl      $2047, %eax
        andl      $-32753, %esi
        shll      $4, %eax
        orl       %eax, %esi
        movw      %si, -2(%rsp)
        movsd     -8(%rsp), %xmm0
..B1.10:
        ret       
..B1.11:
        movb      -1(%rsp), %al
        lea       _large_value_64(%rip), %rcx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rcx,%rdx,8), %xmm0
        mulsd     (%rcx), %xmm0
        ret       
..B1.12:
        cmpl      $-52, %eax
        jl        ..B1.16
..B1.13:
        lea       8+_TWO_55(%rip), %rdx
        lea       55(%rcx,%rdi), %eax
        andl      $2047, %eax
        andl      $-32753, %esi
        shll      $4, %eax
        orl       %eax, %esi
        movw      %si, -2(%rsp)
        testb     %r8b, %r8b
        movsd     -8(%rsp), %xmm0
        mulsd     (%rdx), %xmm0
        je        ..B1.15
..B1.14:
        testl     %edi, %edi
        jl        ..B1.10
..B1.15:
        ret       
..B1.16:
        movb      -1(%rsp), %al
        lea       _small_value_64(%rip), %rcx
        andb      $-128, %al
        shrb      $7, %al
        movzbl    %al, %edx
        movsd     (%rcx,%rdx,8), %xmm0
        mulsd     (%rcx), %xmm0
        ret       
..B1.17:
        lea       _ones(%rip), %rax
        movsd     -8(%rsp), %xmm0
        mulsd     (%rax), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	scalbn,@function
	.size	scalbn,.-scalbn
	.data
# -- End  scalbn
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_55:
	.long	0
	.long	1130364928
	.long	0
	.long	1015021568
	.type	_TWO_55,@object
	.size	_TWO_55,16
	.align 4
_large_value_64:
	.long	0
	.long	2121269248
	.long	0
	.long	4268752896
	.type	_large_value_64,@object
	.size	_large_value_64,16
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
