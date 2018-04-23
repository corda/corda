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
	.file "acoshf_gen.c"
	.text
..TXTST0:
# -- Begin  acoshf
	.text
       .align    16,0x90
	.globl acoshf
acoshf:
# parameter 1: %xmm0
..B1.1:
	.cfi_startproc
..___tag_value_acoshf.1:
..L2:

        movd      %xmm0, %edx
        movss     %xmm0, -16(%rsp)
        lea       -1065353216(%rdx), %eax
        cmpl      $1073741824, %eax
        jae       ..B1.11
..B1.2:
        cmpl      $4194304, %eax
        jae       ..B1.6
..B1.3:
        testl     %eax, %eax
        jne       ..B1.5
..B1.4:
        pxor      %xmm0, %xmm0
        ret       
..B1.5:
        pxor      %xmm3, %xmm3
        cvtss2sd  -16(%rsp), %xmm3
        movsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     %xmm3, -24(%rsp)
        addsd     %xmm0, %xmm3
        movaps    %xmm3, %xmm2
        mulsd     %xmm3, %xmm2
        movsd     .L_2il0floatpacket.2(%rip), %xmm5
        movsd     .L_2il0floatpacket.6(%rip), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.3(%rip), %xmm5
        addsd     .L_2il0floatpacket.7(%rip), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.4(%rip), %xmm5
        addsd     .L_2il0floatpacket.8(%rip), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm2, %xmm4
        addsd     .L_2il0floatpacket.5(%rip), %xmm5
        addsd     .L_2il0floatpacket.9(%rip), %xmm4
        mulsd     %xmm2, %xmm5
        mulsd     %xmm3, %xmm4
        movsd     .L_2il0floatpacket.1(%rip), %xmm1
        addsd     %xmm4, %xmm5
        mulsd     %xmm3, %xmm1
        sqrtsd    %xmm1, %xmm1
        mulsd     %xmm1, %xmm5
        addsd     %xmm1, %xmm5
        jmp       ..B1.10
..B1.6:
        pxor      %xmm1, %xmm1
        cmpl      $134217728, %eax
        cvtss2sd  -16(%rsp), %xmm1
        jae       ..B1.8
..B1.7:
        movaps    %xmm1, %xmm0
        movl      $-1023, %edi
        mulsd     %xmm1, %xmm0
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
        addsd     %xmm2, %xmm0
        sqrtsd    %xmm0, %xmm0
        addsd     %xmm0, %xmm1
        movsd     %xmm1, -24(%rsp)
        jmp       ..B1.9
..B1.8:
        movsd     %xmm1, -24(%rsp)
        movl      $-1022, %edi
        movsd     .L_2il0floatpacket.0(%rip), %xmm2
..B1.9:
        movzwl    -18(%rsp), %edx
        movl      %edx, %eax
        andl      $-32753, %edx
        pxor      %xmm0, %xmm0
        orl       $-49168, %edx
        andl      $32752, %eax
        movw      %dx, -18(%rsp)
        pxor      %xmm1, %xmm1
        movq      __libm_rcp_table_256@GOTPCREL(%rip), %rsi
        movl      -20(%rsp), %ecx
        andl      $1048575, %ecx
        shrl      $12, %ecx
        cvtss2sd  (%rsi,%rcx,4), %xmm0
        mulsd     -24(%rsp), %xmm0
        movsd     .L_2il0floatpacket.11(%rip), %xmm5
        addsd     %xmm2, %xmm0
        mulsd     %xmm0, %xmm5
        shrl      $4, %eax
        addsd     .L_2il0floatpacket.12(%rip), %xmm5
        addl      %eax, %edi
        cvtsi2sd  %edi, %xmm1
        mulsd     %xmm0, %xmm5
        mulsd     .L_2il0floatpacket.13(%rip), %xmm1
        mulsd     %xmm0, %xmm5
        movq      __libm_logf_table_256@GOTPCREL(%rip), %r8
        addsd     %xmm0, %xmm5
        addsd     (%r8,%rcx,8), %xmm1
        movsd     %xmm0, -24(%rsp)
        addsd     %xmm1, %xmm5
..B1.10:
        cvtsd2ss  %xmm5, %xmm5
        movaps    %xmm5, %xmm0
        ret       
..B1.11:
        movl      %edx, %eax
        andl      $2147483647, %eax
        cmpl      $2139095040, %eax
        ja        ..B1.14
..B1.12:
        cmpl      $2139095040, %edx
        je        ..B1.14
..B1.13:
        pxor      %xmm0, %xmm0
        mulss     .L_2il0floatpacket.10(%rip), %xmm0
        ret       
..B1.14:
        movss     -16(%rsp), %xmm0
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	acoshf,@function
	.size	acoshf,.-acoshf
	.data
# -- End  acoshf
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0xbff00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
.L_2il0floatpacket.1:
	.long	0x00000000,0x40000000
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,8
	.align 8
.L_2il0floatpacket.2:
	.long	0xd28ee596,0x3ef3cb17
	.type	.L_2il0floatpacket.2,@object
	.size	.L_2il0floatpacket.2,8
	.align 8
.L_2il0floatpacket.3:
	.long	0x9b9f611c,0x3f31287a
	.type	.L_2il0floatpacket.3,@object
	.size	.L_2il0floatpacket.3,8
	.align 8
.L_2il0floatpacket.4:
	.long	0xac0cf195,0x3f5f1aa4
	.type	.L_2il0floatpacket.4,@object
	.size	.L_2il0floatpacket.4,8
	.align 8
.L_2il0floatpacket.5:
	.long	0x1236cb49,0x3f933333
	.type	.L_2il0floatpacket.5,@object
	.size	.L_2il0floatpacket.5,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xd27f5e7d,0xbf174400
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.align 8
.L_2il0floatpacket.7:
	.long	0x4197b1a0,0xbf46d307
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,8
	.align 8
.L_2il0floatpacket.8:
	.long	0xdacdeace,0xbf76db62
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,8
	.align 8
.L_2il0floatpacket.9:
	.long	0x552ea1bd,0xbfb55555
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,8
	.align 8
.L_2il0floatpacket.11:
	.long	0x6890dd10,0x3fd5555a
	.type	.L_2il0floatpacket.11,@object
	.size	.L_2il0floatpacket.11,8
	.align 8
.L_2il0floatpacket.12:
	.long	0xe7362feb,0xbfe00001
	.type	.L_2il0floatpacket.12,@object
	.size	.L_2il0floatpacket.12,8
	.align 8
.L_2il0floatpacket.13:
	.long	0xfefa39ef,0x3fe62e42
	.type	.L_2il0floatpacket.13,@object
	.size	.L_2il0floatpacket.13,8
	.align 4
.L_2il0floatpacket.10:
	.long	0x7f800000
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,4
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
