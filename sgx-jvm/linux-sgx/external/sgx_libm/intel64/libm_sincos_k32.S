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
	.file "libm_sincos_k32.c"
	.text
..TXTST0:
# -- Begin  __libm_sincos_k32
	.text
       .align    16,0x90
	.globl __libm_sincos_k32
__libm_sincos_k32:
# parameter 1: %xmm0
# parameter 2: %rdi
# parameter 3: %rsi
# parameter 4: %edx
..B1.1:
	.cfi_startproc
..___tag_value___libm_sincos_k32.1:
..L2:

        pushq     %r12
	.cfi_def_cfa_offset 16
	.cfi_offset 12, -16
        pushq     %rbx
	.cfi_def_cfa_offset 24
	.cfi_offset 3, -24
        pushq     %rbp
	.cfi_def_cfa_offset 32
	.cfi_offset 6, -32
        subq      $16, %rsp
	.cfi_def_cfa_offset 48
        lea       iones(%rip), %rax
        movsd     %xmm0, 8(%rsp)
        movl      %edx, %ecx
        movl      12(%rsp), %r12d
        movl      %r12d, %r10d
        shrl      $31, %r12d
        andl      $2147483647, %r10d
        imull     (%rax,%r12,4), %ecx
        movq      %rsi, %rbp
        andps     .L_2il0floatpacket.2(%rip), %xmm0
        movq      %rdi, %rbx
        cmpl      $1103101952, %r10d
        jae       ..B1.9
..B1.2:
        cmpl      $1073283072, %r10d
        ja        ..B1.6
..B1.3:
        incl      %ecx
        movl      %ecx, %edx
        andl      $2, %edx
        je        ..B1.5
..B1.4:
        subsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     %xmm0, 8(%rsp)
        jmp       ..B1.11
..B1.5:
        movsd     %xmm0, 8(%rsp)
        jmp       ..B1.11
..B1.6:
        lea       _INV_PI04(%rip), %rax
        lea       _TWO_52H(%rip), %rdx
        pxor      %xmm2, %xmm2
        pxor      %xmm5, %xmm5
        movsd     %xmm0, 8(%rsp)
        movsd     (%rax), %xmm3
        mulsd     %xmm0, %xmm3
        movsd     (%rdx), %xmm1
        addsd     %xmm3, %xmm1
        movsd     %xmm1, (%rsp)
        movl      (%rsp), %r9d
        cvtsi2sd  %r9d, %xmm2
        cmpltsd   %xmm2, %xmm3
        movd      %xmm3, %edi
        negl      %edi
        subl      %edi, %r9d
        addl      %r9d, %ecx
        movl      %ecx, %r8d
        andl      $1, %r8d
        addl      %r8d, %r9d
        addl      %r8d, %ecx
        cvtsi2sd  %r9d, %xmm5
        cmpl      $1090519040, %r10d
        jae       ..B1.8
..B1.7:
        lea       _DP2(%rip), %rax
        lea       8+_DP2(%rip), %rdx
        lea       16+_DP2(%rip), %rdi
        movsd     (%rax), %xmm1
        mulsd     %xmm5, %xmm1
        movsd     (%rdx), %xmm2
        movl      %ecx, %edx
        mulsd     %xmm5, %xmm2
        subsd     %xmm1, %xmm0
        movsd     (%rdi), %xmm3
        andl      $2, %edx
        mulsd     %xmm3, %xmm5
        subsd     %xmm2, %xmm0
        subsd     %xmm5, %xmm0
        movsd     %xmm0, 8(%rsp)
        jmp       ..B1.11
..B1.8:
        lea       _DP3(%rip), %rax
        lea       8+_DP3(%rip), %rdx
        movsd     8(%rsp), %xmm0
        lea       16+_DP3(%rip), %rdi
        lea       24+_DP3(%rip), %r8
        movsd     (%rax), %xmm1
        movsd     (%rdx), %xmm2
        movl      %ecx, %edx
        mulsd     %xmm5, %xmm1
        andl      $2, %edx
        mulsd     %xmm5, %xmm2
        subsd     %xmm1, %xmm0
        movsd     (%rdi), %xmm3
        subsd     %xmm2, %xmm0
        mulsd     %xmm5, %xmm3
        movsd     (%r8), %xmm4
        subsd     %xmm3, %xmm0
        mulsd     %xmm4, %xmm5
        subsd     %xmm5, %xmm0
        movsd     %xmm0, 8(%rsp)
        jmp       ..B1.11
..B1.9:
        movl      %ecx, %esi
        lea       8(%rsp), %rdi
        movsd     %xmm0, (%rdi)
..___tag_value___libm_sincos_k32.10:
        call      __libm_reduce_pi04d@PLT
..___tag_value___libm_sincos_k32.11:
..B1.17:
        movl      %eax, %ecx
..B1.10:
        incl      %ecx
        movl      %ecx, %edx
        movsd     8(%rsp), %xmm0
        andl      $2, %edx
..B1.11:
        movaps    %xmm0, %xmm2
        lea       24+_CP(%rip), %rsi
        mulsd     %xmm0, %xmm2
        lea       8+_CP(%rip), %rdi
        movaps    %xmm2, %xmm1
        lea       24+_SP(%rip), %r8
        mulsd     %xmm2, %xmm1
        lea       32+_CP(%rip), %r10
        movsd     (%rsi), %xmm6
        lea       32+_SP(%rip), %rsi
        mulsd     %xmm1, %xmm6
        lea       8+_SP(%rip), %r9
        movsd     (%rsi), %xmm4
        lea       16+_CP(%rip), %r11
        mulsd     %xmm1, %xmm4
        addsd     (%rdi), %xmm6
        lea       16+_SP(%rip), %rdi
        lea       2(%rcx), %eax
        movsd     (%r8), %xmm5
        lea       _SP(%rip), %r8
        movsd     (%r10), %xmm3
        mulsd     %xmm1, %xmm5
        addsd     (%rdi), %xmm4
        mulsd     %xmm1, %xmm3
        mulsd     %xmm1, %xmm6
        addsd     (%r9), %xmm5
        mulsd     %xmm1, %xmm4
        addsd     (%r11), %xmm3
        addsd     .L_2il0floatpacket.1(%rip), %xmm6
        mulsd     %xmm1, %xmm5
        addsd     (%r8), %xmm4
        mulsd     %xmm1, %xmm3
        mulsd     %xmm0, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     %xmm0, %xmm5
        mulsd     %xmm0, %xmm4
        shrl      $2, %ecx
        addsd     %xmm4, %xmm5
        andl      $1, %ecx
        xorq      %rcx, %r12
        lea       _CP(%rip), %rcx
        shrl      $2, %eax
        andl      $1, %eax
        testl     %edx, %edx
        addsd     (%rcx), %xmm3
        lea       ones(%rip), %rcx
        mulsd     %xmm2, %xmm3
        movsd     (%rcx,%r12,8), %xmm0
        addsd     %xmm3, %xmm6
        je        ..B1.13
..B1.12:
        mulsd     %xmm0, %xmm6
        movsd     (%rcx,%rax,8), %xmm0
        mulsd     %xmm0, %xmm5
        movsd     %xmm6, (%rbx)
        movsd     %xmm5, (%rbp)
        addq      $16, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
	.cfi_def_cfa_offset 48
	.cfi_offset 3, -24
	.cfi_offset 6, -32
	.cfi_offset 12, -16
..B1.13:
        mulsd     %xmm5, %xmm0
        movsd     %xmm0, (%rbx)
        movsd     (%rcx,%rax,8), %xmm0
        mulsd     %xmm0, %xmm6
        movsd     %xmm6, (%rbp)
..B1.14:
        addq      $16, %rsp
	.cfi_def_cfa_offset 32
	.cfi_restore 6
        popq      %rbp
	.cfi_def_cfa_offset 24
	.cfi_restore 3
        popq      %rbx
	.cfi_def_cfa_offset 16
	.cfi_restore 12
        popq      %r12
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_sincos_k32,@function
	.size	__libm_sincos_k32,.-__libm_sincos_k32
	.data
# -- End  __libm_sincos_k32
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.2:
	.long	0xffffffff,0x7fffffff,0x00000000,0x00000000
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,16
	.align 8
.L_2il0floatpacket.0:
	.long	0x54442d18,0x3fe921fb
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x3ff00000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
ones:
	.long	0x00000000,0x3ff00000
	.long	0x00000000,0xbff00000
	.type	ones,@object
	.size	ones,16
	.align 4
iones:
	.long	1
	.long	-1
	.type	iones,@object
	.size	iones,8
	.align 4
_INV_PI04:
	.long	1841940611
	.long	1072979760
	.type	_INV_PI04,@object
	.size	_INV_PI04,8
	.align 4
_TWO_52H:
	.long	0
	.long	1127743488
	.type	_TWO_52H,@object
	.size	_TWO_52H,8
	.align 4
_DP2:
	.long	1413742592
	.long	1072243195
	.long	1279262720
	.long	1031179299
	.long	1880851354
	.long	996723793
	.type	_DP2,@object
	.size	_DP2,24
	.align 4
_DP3:
	.long	1073741824
	.long	1072243195
	.long	0
	.long	1046758445
	.long	2147483648
	.long	1021855384
	.long	1880851354
	.long	996723793
	.type	_DP3,@object
	.size	_DP3,32
	.align 4
_CP:
	.long	4294960802
	.long	3219128319
	.long	1427442001
	.long	1067799893
	.long	2926645240
	.long	3210133867
	.long	2571283200
	.long	1056571689
	.long	2069816734
	.long	3197257552
	.type	_CP,@object
	.size	_CP,40
	.align 4
_SP:
	.long	1431654765
	.long	3217380693
	.long	285032968
	.long	1065423121
	.long	3653044354
	.long	3207201183
	.long	2777006020
	.long	1053236634
	.long	43514947
	.long	3193610888
	.type	_SP,@object
	.size	_SP,40
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
