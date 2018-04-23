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
	.file "atan2f_gen.c"
	.text
..TXTST0:
# -- Begin  atan2f
	.text
       .align    16,0x90
	.globl atan2f
atan2f:
# parameter 1: %xmm0
# parameter 2: %xmm1
..B1.1:
	.cfi_startproc
..___tag_value_atan2f.1:
..L2:

        movd      %xmm0, %esi
        movd      %xmm1, %ecx
        movss     %xmm0, -16(%rsp)
        movss     %xmm1, -8(%rsp)
        movl      %esi, %edi
        movl      %esi, %eax
        movl      %ecx, %edx
        movl      %ecx, %r8d
        andl      $2147483647, %edi
        andl      $2147483647, %r8d
        shrl      $31, %eax
        shrl      $31, %edx
        movl      %ecx, -24(%rsp)
        cmpl      $2139095040, %edi
        movl      %esi, -24(%rsp)
        jl        ..B1.3
..B1.2:
        jg        ..B1.16
..B1.52:
        cmpl      $2139095040, %r8d
        jg        ..B1.16
        jmp       ..B1.5
..B1.3:
        cmpl      $2139095040, %r8d
        jl        ..B1.17
..B1.4:
        jg        ..B1.16
..B1.5:
        jl        ..B1.15
..B1.6:
        movl      %eax, %eax
        cmpl      $2139095040, %edi
        jge       ..B1.11
..B1.7:
        testl     %edx, %edx
        je        ..B1.9
..B1.8:
        lea       pi(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     (%rcx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.9:
        lea       _zeros(%rip), %rdx
        movss     (%rdx,%rax,4), %xmm0
..B1.10:
        ret       
..B1.11:
        lea       _small_value_64(%rip), %rcx
        testl     %edx, %edx
        movsd     (%rcx,%rax,8), %xmm1
        je        ..B1.13
..B1.12:
        lea       pi34(%rip), %rdx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     %xmm1, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.13:
        lea       pi4(%rip), %rdx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     %xmm1, %xmm0
        cvtsd2ss  %xmm0, %xmm0
..B1.14:
        ret       
..B1.15:
        movl      %eax, %eax
        lea       pi2(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     (%rcx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.16:
        movss     -8(%rsp), %xmm0
        mulss     -16(%rsp), %xmm0
        ret       
..B1.17:
        testl     %r8d, %edi
        jne       ..B1.25
..B1.18:
        testl     %edi, %edi
        jne       ..B1.24
..B1.19:
        movl      %eax, %eax
        testl     %r8d, %r8d
        je        ..B1.45
..B1.20:
        testl     %edx, %edx
        je        ..B1.22
..B1.21:
        lea       pi(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     (%rcx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.22:
        lea       _zeros(%rip), %rdx
        movss     (%rdx,%rax,4), %xmm0
..B1.23:
        ret       
..B1.24:
        testl     %r8d, %r8d
        je        ..B1.49
..B1.25:
        pxor      %xmm0, %xmm0
        pxor      %xmm5, %xmm5
        cvtss2sd  -16(%rsp), %xmm0
        cvtss2sd  -8(%rsp), %xmm5
        movsd     %xmm0, -40(%rsp)
        movsd     %xmm5, -32(%rsp)
        movl      -36(%rsp), %edi
        movl      -28(%rsp), %esi
        andl      $2147483647, %edi
        andl      $2147483647, %esi
        cmpl      %esi, %edi
        jl        ..B1.32
..B1.26:
        lea       1048576(%rsi), %ecx
        cmpl      %ecx, %edi
        jle       ..B1.31
..B1.27:
        movl      %eax, %eax
        lea       pi2(%rip), %rdx
        addl      $33554432, %esi
        cmpl      %esi, %edi
        movsd     (%rdx,%rax,8), %xmm4
        jle       ..B1.29
..B1.28:
        divsd     %xmm0, %xmm5
        subsd     %xmm5, %xmm4
        cvtsd2ss  %xmm4, %xmm4
        jmp       ..B1.30
..B1.29:
        divsd     %xmm0, %xmm5
        movaps    %xmm5, %xmm1
        subsd     %xmm5, %xmm4
        mulsd     %xmm5, %xmm1
        movaps    %xmm1, %xmm0
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.6(%rip), %xmm3
        movsd     .L_2il0floatpacket.10(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.7(%rip), %xmm3
        addsd     .L_2il0floatpacket.11(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.8(%rip), %xmm3
        addsd     .L_2il0floatpacket.12(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm0, %xmm2
        addsd     .L_2il0floatpacket.9(%rip), %xmm3
        addsd     .L_2il0floatpacket.13(%rip), %xmm2
        mulsd     %xmm0, %xmm3
        mulsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm3
        mulsd     %xmm3, %xmm5
        subsd     %xmm5, %xmm4
        cvtsd2ss  %xmm4, %xmm4
..B1.30:
        movaps    %xmm4, %xmm0
        ret       
..B1.31:
        movl      %eax, %eax
        lea       _ones(%rip), %rcx
        movl      %edx, %edx
        pxor      %xmm1, %xmm1
        pxor      %xmm2, %xmm2
        lea       pi4n(%rip), %rsi
        movsd     .L_2il0floatpacket.3(%rip), %xmm4
        cmpl      %eax, %edx
        cvtss2sd  (%rcx,%rax,4), %xmm1
        cvtss2sd  (%rcx,%rdx,4), %xmm2
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm5
        movaps    %xmm0, %xmm6
        addsd     %xmm5, %xmm0
        subsd     %xmm5, %xmm6
        divsd     %xmm0, %xmm6
        movaps    %xmm6, %xmm0
        mulsd     %xmm6, %xmm0
        movaps    %xmm0, %xmm3
        mulsd     %xmm0, %xmm3
        movsd     .L_2il0floatpacket.0(%rip), %xmm5
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm4
        addsd     .L_2il0floatpacket.1(%rip), %xmm5
        addsd     .L_2il0floatpacket.4(%rip), %xmm4
        mulsd     %xmm3, %xmm5
        mulsd     %xmm3, %xmm4
        addsd     .L_2il0floatpacket.2(%rip), %xmm5
        addsd     .L_2il0floatpacket.5(%rip), %xmm4
        mulsd     %xmm0, %xmm5
        addsd     %xmm4, %xmm5
        mulsd     %xmm5, %xmm6
        addsd     (%rsi,%rdx,8), %xmm6
        movaps    %xmm6, %xmm0
        xorps     .L_2il0floatpacket.15(%rip), %xmm0
        jne       ..L3
        movaps    %xmm6, %xmm0
..L3:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.32:
        lea       1048576(%rdi), %ecx
        cmpl      %ecx, %esi
        jle       ..B1.44
..B1.33:
        addl      $33554432, %edi
        cmpl      %edi, %esi
        jle       ..B1.40
..B1.34:
        testl     %edx, %edx
        jne       ..B1.39
..B1.35:
        divsd     %xmm5, %xmm0
        lea       _minnormf(%rip), %rax
        movaps    %xmm0, %xmm2
        andps     .L_2il0floatpacket.16(%rip), %xmm2
        movsd     (%rax), %xmm1
        comisd    %xmm2, %xmm1
        jbe       ..B1.37
..B1.36:
        movss     .L_2il0floatpacket.14(%rip), %xmm1
        mulss     %xmm1, %xmm1
        movss     %xmm1, -24(%rsp)
        jmp       ..B1.38
..B1.37:
        movl      $1065353216, -24(%rsp)
..B1.38:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.39:
        divsd     %xmm5, %xmm0
        movl      %eax, %eax
        lea       pi(%rip), %rdx
        addsd     (%rdx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.40:
        divsd     %xmm5, %xmm0
        movaps    %xmm0, %xmm2
        testl     %edx, %edx
        mulsd     %xmm0, %xmm2
        movaps    %xmm2, %xmm1
        mulsd     %xmm2, %xmm1
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        movsd     .L_2il0floatpacket.10(%rip), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm1, %xmm3
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        addsd     .L_2il0floatpacket.11(%rip), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm1, %xmm3
        addsd     .L_2il0floatpacket.8(%rip), %xmm4
        addsd     .L_2il0floatpacket.12(%rip), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm1, %xmm3
        addsd     .L_2il0floatpacket.9(%rip), %xmm4
        addsd     .L_2il0floatpacket.13(%rip), %xmm3
        mulsd     %xmm1, %xmm4
        mulsd     %xmm2, %xmm3
        addsd     %xmm3, %xmm4
        mulsd     %xmm0, %xmm4
        addsd     %xmm4, %xmm0
        je        ..B1.42
..B1.41:
        movl      %eax, %eax
        lea       pi(%rip), %rdx
        movsd     (%rdx,%rax,8), %xmm1
        addsd     %xmm1, %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.42:
        cvtsd2ss  %xmm0, %xmm0
..B1.43:
        ret       
..B1.44:
        movl      %eax, %eax
        lea       _ones(%rip), %rcx
        pxor      %xmm1, %xmm1
        pxor      %xmm2, %xmm2
        lea       pi4n(%rip), %rsi
        movsd     .L_2il0floatpacket.0(%rip), %xmm4
        cmpl      %eax, %edx
        cvtss2sd  (%rcx,%rax,4), %xmm1
        cvtss2sd  (%rcx,%rdx,4), %xmm2
        mulsd     %xmm1, %xmm0
        mulsd     %xmm2, %xmm5
        movaps    %xmm0, %xmm6
        addsd     %xmm5, %xmm0
        subsd     %xmm5, %xmm6
        xorps     .L_2il0floatpacket.15(%rip), %xmm6
        divsd     %xmm0, %xmm6
        movaps    %xmm6, %xmm0
        mulsd     %xmm6, %xmm0
        movaps    %xmm0, %xmm5
        mulsd     %xmm0, %xmm5
        mulsd     %xmm5, %xmm4
        movsd     .L_2il0floatpacket.3(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.1(%rip), %xmm4
        mulsd     %xmm5, %xmm4
        addsd     .L_2il0floatpacket.4(%rip), %xmm3
        mulsd     %xmm5, %xmm3
        addsd     .L_2il0floatpacket.2(%rip), %xmm4
        mulsd     %xmm0, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm3
        movsd     (%rsi,%rdx,8), %xmm7
        addsd     %xmm3, %xmm4
        mulsd     %xmm4, %xmm6
        subsd     %xmm6, %xmm7
        movaps    %xmm7, %xmm0
        xorps     .L_2il0floatpacket.15(%rip), %xmm0
        jne       ..L4
        movaps    %xmm7, %xmm0
..L4:
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.45:
        testl     %edx, %edx
        je        ..B1.47
..B1.46:
        lea       pi(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     (%rcx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
..B1.47:
        lea       _zeros(%rip), %rdx
        movss     (%rdx,%rax,4), %xmm0
..B1.48:
        ret       
..B1.49:
        lea       pi2(%rip), %rdx
        lea       _small_value_64(%rip), %rcx
        movsd     (%rdx,%rax,8), %xmm0
        addsd     (%rcx,%rax,8), %xmm0
        cvtsd2ss  %xmm0, %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	atan2f,@function
	.size	atan2f,.-atan2f
	.data
# -- End  atan2f
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.15:
	.long	0x00000000,0x80000000,0x00000000,0x00000000
	.type	.L_2il0floatpacket.15,@object
	.size	.L_2il0floatpacket.15,16
	.align 16
.L_2il0floatpacket.16:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.16,@object
	.size	.L_2il0floatpacket.16,16
	.align 8
.L_2il0floatpacket.0:
	.long	0xd9d9aa33,0xbfb1c1c0
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x04ba093e,0xbfc24485
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0x312dd43c,0xbfd55555
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x947e6edc,0x3fbbcbeb
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0x700fa0b3,0x3fc9997b
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0xfff8f7db,0x3fefffff
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xa1fbc9d9,0x3f9a8eb6
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x713e98d0,0x3fb32474
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0xa367efd7,0x3fbc70d3
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x8eac5238,0x3fc99999
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.10:
	.long	0x95b6793b,0xbfac6c73
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x306ebb4b,0xbfb73640
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0xe2eb2ece,0xbfc24920
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0x5552abff,0xbfd55555
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 8
pi:
	.long	0x54442d18,0x400921fb
	.long	0x54442d18,0xc00921fb
	.type	pi,@object
	.size	pi,16
	.align 8
pi34:
	.long	0x7f3321d2,0x4002d97c
	.long	0x7f3321d2,0xc002d97c
	.type	pi34,@object
	.size	pi34,16
	.align 8
pi4:
	.long	0x54442d18,0x3fe921fb
	.long	0x54442d18,0xbfe921fb
	.type	pi4,@object
	.size	pi4,16
	.align 8
pi2:
	.long	0x54442d18,0x3ff921fb
	.long	0x54442d18,0xbff921fb
	.type	pi2,@object
	.size	pi2,16
	.align 8
pi4n:
	.long	0x54442d18,0x3fe921fb
	.long	0x7f3321d2,0xc002d97c
	.type	pi4n,@object
	.size	pi4n,16
	.align 4
.L_2il0floatpacket.14:
	.long	0x0d800000
	.type	.L_2il0floatpacket.14,@object
	.size	.L_2il0floatpacket.14,4
	.align 4
.L_2il0floatpacket.17:
	.long	0x3f800000
	.type	.L_2il0floatpacket.17,@object
	.size	.L_2il0floatpacket.17,4
	.align 4
_small_value_64:
	.long	0
	.long	24117248
	.long	0
	.long	2171600896
	.type	_small_value_64,@object
	.size	_small_value_64,16
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_minnormf:
	.long	0
	.long	940572672
	.type	_minnormf,@object
	.size	_minnormf,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
