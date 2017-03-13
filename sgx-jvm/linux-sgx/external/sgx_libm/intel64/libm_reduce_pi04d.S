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
	.file "libm_reduce_pi04d.c"
	.text
..TXTST0:
# -- Begin  __libm_reduce_pi04d
	.text
       .align    16,0x90
	.globl __libm_reduce_pi04d
__libm_reduce_pi04d:
# parameter 1: %xmm0
# parameter 2: %rdi
# parameter 3: %esi
..B1.1:
	.cfi_startproc
..___tag_value___libm_reduce_pi04d.1:
..L2:

        subq      $24, %rsp
	.cfi_def_cfa_offset 32
        movl      %esi, %r8d
        movsd     %xmm0, (%rsp)
        xorb      %sil, %sil
..B1.2:
        fnstcw    10(%rsp)
..B1.3:
        movzwl    10(%rsp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.7
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 8(%rsp)
..B1.5:
        fldcw     8(%rsp)
..B1.6:
        movb      $1, %sil
..B1.7:
        movzwl    6(%rsp), %r10d
        movl      %r10d, %ecx
        andl      $32752, %ecx
        movl      $1374389535, %eax
        shrl      $4, %ecx
        andl      $-32753, %r10d
        lea       -1052(%rcx), %r11d
        imull     %r11d
        sarl      $31, %r11d
        lea       -200(%rcx), %r9d
        sarl      $3, %edx
        andl      $2047, %r9d
        subl      %r11d, %edx
        imull     $-25, %edx, %eax
        shll      $4, %r9d
        lea       -1052(%rax,%rcx), %ecx
        orl       %r9d, %r10d
        movw      %r10w, 6(%rsp)
        movsd     (%rsp), %xmm2
        andl      $-134217728, (%rsp)
        movsd     (%rsp), %xmm1
        cmpl      $17, %ecx
        subsd     %xmm1, %xmm2
        jl        ..B1.9
..B1.8:
        movslq    %edx, %rdx
        lea       _DP(%rip), %r9
        movsd     (%r9,%rdx,8), %xmm7
        movsd     8(%r9,%rdx,8), %xmm0
        incl      %edx
        mulsd     %xmm2, %xmm7
        mulsd     %xmm1, %xmm0
        addsd     %xmm0, %xmm7
        movsd     %xmm7, (%rsp)
        andl      $-1048576, (%rsp)
        subsd     (%rsp), %xmm7
        jmp       ..B1.10
..B1.9:
        lea       _DP(%rip), %r9
        pxor      %xmm7, %xmm7
..B1.10:
        movslq    %edx, %rdx
        movaps    %xmm1, %xmm3
        movaps    %xmm1, %xmm5
        lea       zero_none(%rip), %r11
        movaps    %xmm1, %xmm9
        movaps    %xmm1, %xmm11
        movaps    %xmm1, %xmm13
        movsd     (%r9,%rdx,8), %xmm4
        movsd     8(%r9,%rdx,8), %xmm0
        mulsd     %xmm2, %xmm4
        mulsd     %xmm0, %xmm3
        mulsd     %xmm2, %xmm0
        addsd     %xmm3, %xmm4
        movsd     16(%r9,%rdx,8), %xmm8
        addsd     %xmm4, %xmm7
        mulsd     %xmm8, %xmm5
        mulsd     %xmm2, %xmm8
        addsd     %xmm5, %xmm0
        movaps    %xmm7, %xmm6
        movsd     24(%r9,%rdx,8), %xmm10
        addsd     %xmm0, %xmm6
        movsd     %xmm6, (%rsp)
        movzwl    6(%rsp), %ecx
        shrl      $4, %ecx
        movd      %xmm6, %r10
        negl      %ecx
        movsd     32(%r9,%rdx,8), %xmm12
        movsd     40(%r9,%rdx,8), %xmm14
        addl      $51, %ecx
        sarq      %cl, %r10
        movl      %r10d, %eax
        shlq      %cl, %r10
        addl      %r8d, %eax
        movq      %r10, (%rsp)
        movl      %eax, %r8d
        andl      $1, %r8d
        subsd     (%rsp), %xmm7
        mulsd     %xmm12, %xmm9
        mulsd     %xmm2, %xmm12
        mulsd     %xmm14, %xmm11
        addsd     (%r11,%r8,8), %xmm7
        mulsd     %xmm2, %xmm14
        addsd     %xmm7, %xmm0
        addsd     %xmm11, %xmm12
        movaps    %xmm1, %xmm7
        testb     %sil, %sil
        mulsd     %xmm10, %xmm7
        mulsd     %xmm2, %xmm10
        addsd     %xmm7, %xmm8
        addsd     %xmm9, %xmm10
        addsd     %xmm8, %xmm0
        movsd     48(%r9,%rdx,8), %xmm15
        addsd     %xmm10, %xmm0
        mulsd     %xmm15, %xmm13
        mulsd     %xmm15, %xmm2
        addsd     %xmm12, %xmm0
        addsd     %xmm13, %xmm14
        movsd     56(%r9,%rdx,8), %xmm3
        addsd     %xmm14, %xmm0
        mulsd     %xmm3, %xmm1
        addsd     %xmm1, %xmm2
        addsd     %xmm2, %xmm0
        mulsd     .L_2il0floatpacket.0(%rip), %xmm0
        movsd     %xmm0, (%rdi)
        je        ..B1.12
..B1.11:
        fldcw     10(%rsp)
..B1.12:
        addq      $24, %rsp
	.cfi_def_cfa_offset 8
        ret       
        .align    16,0x90
	.cfi_endproc
	.type	__libm_reduce_pi04d,@function
	.size	__libm_reduce_pi04d,.-__libm_reduce_pi04d
	.data
# -- End  __libm_reduce_pi04d
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x54442d18,0x3fe921fb
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.align 8
zero_none:
	.long	0x00000000,0x00000000
	.long	0x00000000,0xbff00000
	.type	zero_none,@object
	.size	zero_none,16
	.align 4
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
	.type	_DP,@object
	.size	_DP,392
	.data
	.section .note.GNU-stack, ""
// -- Begin DWARF2 SEGMENT .eh_frame
	.section .eh_frame,"a",@progbits
.eh_frame_seg:
	.align 1
# End
