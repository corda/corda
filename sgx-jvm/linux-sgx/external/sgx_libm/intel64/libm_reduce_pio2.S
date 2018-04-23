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
	.file "libm_reduce_pio2.c"
	.text
..TXTST0:
# -- Begin  __libm_reduce_pio2d
	.text
       .align    16,0x90
	.globl __libm_reduce_pio2d
__libm_reduce_pio2d:
# parameter 1: %xmm0
# parameter 2: %rdi
..B1.1:
	.cfi_startproc
..___tag_value___libm_reduce_pio2d.1:
..L2:

        movq      %rdi, %r9
        movsd     %xmm0, -8(%rsp)
        movzbl    -1(%rsp), %eax
        movl      %eax, %r8d
        andl      $127, %eax
        andl      $128, %r8d
        movb      %al, -1(%rsp)
        movzwl    -2(%rsp), %eax
        movl      %eax, %edx
        andl      $32752, %edx
        shrl      $4, %edx
        shrl      $7, %r8d
        cmpl      $1053, %edx
        jge       ..B1.9
..B1.2:
        lea       _PI04_INV(%rip), %rax
        movsd     -8(%rsp), %xmm0
        movsd     (%rax), %xmm1
        mulsd     %xmm0, %xmm1
        movsd     %xmm1, -16(%rsp)
        movzwl    -10(%rsp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        cmpl      $1023, %ecx
        jl        ..B1.4
..B1.3:
        movl      -12(%rsp), %esi
        negl      %ecx
        orl       $-1048576, %esi
        addl      $30, %ecx
        movl      -16(%rsp), %eax
        shll      $11, %esi
        shrl      $21, %eax
        orl       %eax, %esi
        shrl      %cl, %esi
        jmp       ..B1.5
..B1.4:
        xorl      %esi, %esi
..B1.5:
        movl      %esi, %ecx
        lea       (%r8,%r8), %eax
        andl      $1, %ecx
        negl      %eax
        addl      %esi, %ecx
        incl      %esi
        shrl      $1, %esi
        incl      %eax
        imull     %esi, %eax
        pxor      %xmm7, %xmm7
        cvtsi2sd  %ecx, %xmm7
        andl      $3, %eax
        cmpl      $1046, %edx
        jge       ..B1.7
..B1.6:
        lea       _PI04_29x4(%rip), %rdx
        lea       8+_PI04_29x4(%rip), %rcx
        lea       16+_PI04_29x4(%rip), %rsi
        lea       24+_PI04_29x4(%rip), %rdi
        movsd     (%rdx), %xmm1
        mulsd     %xmm7, %xmm1
        movsd     (%rcx), %xmm2
        subsd     %xmm1, %xmm0
        mulsd     %xmm7, %xmm2
        movaps    %xmm0, %xmm4
        movsd     (%rsi), %xmm3
        subsd     %xmm2, %xmm4
        mulsd     %xmm7, %xmm3
        subsd     %xmm4, %xmm0
        movsd     (%rdi), %xmm5
        subsd     %xmm2, %xmm0
        mulsd     %xmm5, %xmm7
        movaps    %xmm4, %xmm2
        subsd     %xmm3, %xmm2
        subsd     %xmm2, %xmm4
        subsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm0
        subsd     %xmm7, %xmm0
        jmp       ..B1.8
..B1.7:
        lea       _PI04_21x5(%rip), %rdx
        lea       8+_PI04_21x5(%rip), %rcx
        lea       16+_PI04_21x5(%rip), %rsi
        lea       24+_PI04_21x5(%rip), %rdi
        lea       32+_PI04_21x5(%rip), %r10
        movsd     (%rdx), %xmm1
        movsd     (%rcx), %xmm2
        mulsd     %xmm7, %xmm1
        mulsd     %xmm7, %xmm2
        subsd     %xmm1, %xmm0
        movaps    %xmm0, %xmm4
        movsd     (%rsi), %xmm3
        subsd     %xmm2, %xmm4
        mulsd     %xmm7, %xmm3
        subsd     %xmm4, %xmm0
        movaps    %xmm4, %xmm8
        subsd     %xmm2, %xmm0
        subsd     %xmm3, %xmm8
        movaps    %xmm8, %xmm5
        subsd     %xmm8, %xmm4
        movsd     (%r10), %xmm6
        subsd     %xmm3, %xmm4
        addsd     %xmm4, %xmm0
        addsd     %xmm0, %xmm5
        movaps    %xmm5, %xmm2
        subsd     %xmm5, %xmm8
        addsd     %xmm0, %xmm8
        movsd     (%rdi), %xmm0
        mulsd     %xmm7, %xmm0
        mulsd     %xmm6, %xmm7
        subsd     %xmm0, %xmm2
        subsd     %xmm2, %xmm5
        subsd     %xmm0, %xmm5
        addsd     %xmm5, %xmm8
        subsd     %xmm7, %xmm8
        movaps    %xmm8, %xmm0
..B1.8:
        movl      %r8d, %r8d
        lea       _ones(%rip), %rdx
        movsd     (%rdx,%r8,8), %xmm1
        mulsd     %xmm1, %xmm2
        mulsd     %xmm1, %xmm0
        movaps    %xmm2, %xmm3
        addsd     %xmm0, %xmm3
        movsd     %xmm3, (%r9)
        subsd     %xmm3, %xmm2
        addsd     %xmm0, %xmm2
        movsd     %xmm2, 8(%r9)
        ret       
..B1.9:
        andl      $-32753, %eax
        lea       -200(%rdx), %r10d
        andl      $2047, %r10d
        shll      $4, %r10d
        orl       %r10d, %eax
        movw      %ax, -2(%rsp)
        lea       -1052(%rdx), %eax
        imull     $83886, %eax, %edi
        movsd     -8(%rsp), %xmm2
        movsd     %xmm2, -24(%rsp)
        sarl      $21, %edi
        movslq    %edi, %rdi
        imull     $-25, %edi, %esi
        movl      -8(%rsp), %r11d
        lea       -1052(%rsi,%rdx), %ecx
        andl      $-134217728, %r11d
        lea       _DP(%rip), %rsi
        movl      %r11d, -24(%rsp)
        negl      %ecx
        movsd     -24(%rsp), %xmm1
        addl      $38, %ecx
        movaps    %xmm1, %xmm3
        movq      $-1, %rdx
        movsd     (%rsi,%rdi,8), %xmm0
        movaps    %xmm1, %xmm7
        movsd     8(%rsi,%rdi,8), %xmm5
        movaps    %xmm1, %xmm14
        mulsd     %xmm5, %xmm3
        subsd     %xmm1, %xmm2
        mulsd     %xmm2, %xmm0
        mulsd     %xmm2, %xmm5
        movaps    %xmm0, %xmm6
        shlq      %cl, %rdx
        addsd     %xmm3, %xmm6
        movaps    %xmm6, %xmm4
        subsd     %xmm6, %xmm0
        movsd     16(%rsi,%rdi,8), %xmm12
        addsd     %xmm3, %xmm0
        mulsd     %xmm12, %xmm7
        mulsd     %xmm2, %xmm12
        addsd     %xmm0, %xmm4
        movd      %xmm4, %r10
        movsd     24(%rsi,%rdi,8), %xmm3
        mulsd     %xmm3, %xmm14
        mulsd     %xmm2, %xmm3
        andq      %rdx, %r10
        movq      %r10, -16(%rsp)
        subsd     -16(%rsp), %xmm6
        movaps    %xmm6, %xmm8
        addsd     %xmm5, %xmm8
        movaps    %xmm8, %xmm10
        subsd     %xmm8, %xmm6
        addsd     %xmm7, %xmm10
        addsd     %xmm5, %xmm6
        subsd     %xmm10, %xmm8
        addsd     %xmm6, %xmm0
        addsd     %xmm7, %xmm8
        movaps    %xmm10, %xmm9
        movaps    %xmm1, %xmm5
        movsd     32(%rsi,%rdi,8), %xmm7
        addsd     %xmm8, %xmm0
        mulsd     %xmm7, %xmm5
        mulsd     %xmm2, %xmm7
        addsd     %xmm0, %xmm9
        movaps    %xmm0, %xmm13
        addsd     %xmm12, %xmm9
        addsd     %xmm14, %xmm9
        movsd     %xmm9, -16(%rsp)
        movzwl    -10(%rsp), %ecx
        shrl      $4, %ecx
        movd      %xmm9, %rax
        negl      %ecx
        movaps    %xmm1, %xmm9
        addl      $51, %ecx
        sarq      %cl, %rax
        movl      %eax, %edx
        shlq      %cl, %rax
        movl      %edx, %r11d
        movq      %rax, -16(%rsp)
        lea       _zero_none(%rip), %rax
        andl      $1, %r11d
        incl      %edx
        shrl      $1, %edx
        lea       _PI04_25x2(%rip), %rcx
        subsd     -16(%rsp), %xmm10
        movsd     (%rax,%r11,8), %xmm11
        lea       (%r8,%r8), %eax
        negl      %eax
        addsd     %xmm10, %xmm13
        incl      %eax
        subsd     %xmm13, %xmm10
        imull     %edx, %eax
        addsd     %xmm11, %xmm13
        addsd     %xmm10, %xmm0
        movaps    %xmm13, %xmm15
        movaps    %xmm1, %xmm11
        lea       _TWO_26H(%rip), %rdx
        andl      $3, %eax
        addsd     %xmm12, %xmm15
        movaps    %xmm15, %xmm4
        subsd     %xmm15, %xmm13
        addsd     %xmm14, %xmm4
        addsd     %xmm12, %xmm13
        subsd     %xmm4, %xmm15
        addsd     %xmm13, %xmm0
        addsd     %xmm14, %xmm15
        movaps    %xmm4, %xmm6
        movaps    %xmm1, %xmm14
        movsd     40(%rsi,%rdi,8), %xmm12
        addsd     %xmm3, %xmm6
        mulsd     %xmm12, %xmm9
        addsd     %xmm15, %xmm0
        mulsd     %xmm2, %xmm12
        subsd     %xmm6, %xmm4
        movaps    %xmm6, %xmm8
        addsd     %xmm3, %xmm4
        addsd     %xmm5, %xmm8
        addsd     %xmm4, %xmm0
        subsd     %xmm8, %xmm6
        movaps    %xmm8, %xmm10
        addsd     %xmm5, %xmm6
        addsd     %xmm7, %xmm10
        addsd     %xmm6, %xmm0
        subsd     %xmm10, %xmm8
        movsd     48(%rsi,%rdi,8), %xmm3
        movaps    %xmm10, %xmm13
        mulsd     %xmm3, %xmm11
        addsd     %xmm9, %xmm13
        addsd     %xmm7, %xmm8
        mulsd     %xmm2, %xmm3
        addsd     %xmm11, %xmm12
        subsd     %xmm13, %xmm10
        addsd     %xmm8, %xmm0
        addsd     %xmm9, %xmm10
        movsd     56(%rsi,%rdi,8), %xmm15
        movaps    %xmm13, %xmm4
        mulsd     %xmm15, %xmm14
        addsd     %xmm12, %xmm4
        addsd     %xmm10, %xmm0
        mulsd     %xmm15, %xmm2
        addsd     %xmm14, %xmm3
        subsd     %xmm4, %xmm13
        movaps    %xmm4, %xmm5
        addsd     %xmm12, %xmm13
        addsd     %xmm3, %xmm5
        addsd     %xmm13, %xmm0
        subsd     %xmm5, %xmm4
        addsd     %xmm3, %xmm4
        movsd     64(%rsi,%rdi,8), %xmm3
        mulsd     %xmm3, %xmm1
        addsd     %xmm4, %xmm0
        addsd     %xmm1, %xmm2
        movaps    %xmm2, %xmm3
        lea       8+_PI04_25x2(%rip), %rsi
        movsd     (%rdx), %xmm1
        lea       _ones(%rip), %rdx
        addsd     %xmm5, %xmm3
        mulsd     %xmm3, %xmm1
        addsd     %xmm1, %xmm3
        subsd     %xmm1, %xmm3
        subsd     %xmm3, %xmm5
        addsd     %xmm2, %xmm5
        movsd     (%rcx), %xmm2
        addsd     %xmm5, %xmm0
        movaps    %xmm0, %xmm1
        addsd     %xmm3, %xmm0
        mulsd     %xmm2, %xmm1
        mulsd     %xmm2, %xmm3
        mulsd     (%rsi), %xmm0
        addsd     %xmm0, %xmm1
        movaps    %xmm1, %xmm0
        addsd     %xmm3, %xmm0
        subsd     %xmm0, %xmm3
        addsd     %xmm1, %xmm3
        movsd     (%rdx,%r8,8), %xmm1
        mulsd     %xmm1, %xmm0
        mulsd     %xmm1, %xmm3
        movsd     %xmm0, (%r9)
        movsd     %xmm3, 8(%r9)
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_reduce_pio2d,@function
	.size	__libm_reduce_pio2d,.-__libm_reduce_pio2d
	.data
# -- End  __libm_reduce_pio2d
	.section .rodata, "a"
	.align 16
	.align 16
_PI04_INV:
	.long	1841940611
	.long	1072979760
	.type	_PI04_INV,@object
	.size	_PI04_INV,8
	.space 8, 0x00 	# pad
	.align 16
_PI04_29x4:
	.long	1409286144
	.long	1072243195
	.long	301989888
	.long	1040255814
	.long	1006632960
	.long	3156637299
	.long	2207917344
	.long	979464219
	.type	_PI04_29x4,@object
	.size	_PI04_29x4,32
	.align 16
_PI04_21x5:
	.long	0
	.long	1072243194
	.long	0
	.long	1051018307
	.long	0
	.long	3174514122
	.long	0
	.long	3153310618
	.long	3773204808
	.long	981752838
	.type	_PI04_21x5,@object
	.size	_PI04_21x5,40
	.space 8, 0x00 	# pad
	.align 16
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 16
_DP:
	.long	0
	.long	0
	.long	1610612736
	.long	1282694960
	.long	0
	.long	1256952721
	.long	536870912
	.long	1229269500
	.long	3221225472
	.long	1202544455
	.long	0
	.long	1176818551
	.long	2147483648
	.long	1148939346
	.long	536870912
	.long	1124701124
	.long	3758096384
	.long	1099498527
	.long	3758096384
	.long	1071929578
	.long	1342177280
	.long	1046982385
	.long	805306368
	.long	1020320658
	.long	2147483648
	.long	993817732
	.long	0
	.long	968598976
	.long	2684354560
	.long	942220475
	.long	2415919104
	.long	915426956
	.long	0
	.long	885849629
	.long	536870912
	.long	863855510
	.long	1610612736
	.long	836031391
	.long	4026531840
	.long	810828058
	.long	1073741824
	.long	784674491
	.long	1610612736
	.long	757207974
	.long	3489660928
	.long	732020890
	.long	0
	.long	703061904
	.long	1610612736
	.long	679713053
	.long	2147483648
	.long	652001705
	.long	1073741824
	.long	626850382
	.long	2147483648
	.long	597786158
	.long	805306368
	.long	575535400
	.long	536870912
	.long	548814833
	.long	268435456
	.long	523239288
	.long	3758096384
	.long	495550718
	.long	2952790016
	.long	469954840
	.long	1073741824
	.long	442925723
	.long	1073741824
	.long	416247094
	.long	3758096384
	.long	392128403
	.long	2147483648
	.long	364254062
	.long	3221225472
	.long	339643518
	.long	2684354560
	.long	313162111
	.long	805306368
	.long	286354345
	.long	2952790016
	.long	260811902
	.long	1610612736
	.long	234667567
	.long	3758096384
	.long	207520668
	.long	1073741824
	.long	182175017
	.long	4026531840
	.long	155380331
	.long	805306368
	.long	129417058
	.long	536870912
	.long	103691636
	.long	0
	.long	73760972
	.long	3221225472
	.long	48348958
	.long	536870912
	.long	23784188
	.type	_DP,@object
	.size	_DP,400
	.align 16
_zero_none:
	.long	0
	.long	0
	.long	0
	.long	3220176896
	.type	_zero_none,@object
	.size	_zero_none,16
	.align 16
_PI04_25x2:
	.long	1073741824
	.long	1072243195
	.long	407279769
	.long	1046758445
	.type	_PI04_25x2,@object
	.size	_PI04_25x2,16
	.align 16
_TWO_26H:
	.long	0
	.long	1100480512
	.type	_TWO_26H,@object
	.size	_TWO_26H,8
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
