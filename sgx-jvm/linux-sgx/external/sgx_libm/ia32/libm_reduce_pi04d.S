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
	.hidden __libm_reduce_pi04d
	.globl __libm_reduce_pi04d
__libm_reduce_pi04d:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
# parameter 3: 20 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
        movl      16(%ebp), %eax
        movl      20(%ebp), %ebx
        movl      %eax, 8(%esp)
..B1.2:
        fnstcw    26(%esp)
..B1.3:
        movzwl    26(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.19
..B1.4:
        orl       $-64768, %ecx
        movw      %cx, 24(%esp)
..B1.5:
        fldcw     24(%esp)
..B1.6:
        movl      $1, 12(%esp)
..B1.7:
        movzwl    14(%ebp), %edi
        movl      %edi, %ecx
        andl      $32752, %ecx
        andl      $-32753, %edi
        shrl      $4, %ecx
        movl      $1374389535, %eax
        lea       -200(%ecx), %esi
        andl      $2047, %esi
        shll      $4, %esi
        orl       %esi, %edi
        lea       -1052(%ecx), %esi
        imull     %esi
        sarl      $3, %edx
        sarl      $31, %esi
        subl      %esi, %edx
        movw      %di, 14(%ebp)
        fldl      8(%ebp)
        imull     $-25, %edx, %edi
        andl      $-134217728, 8(%ebp)
        lea       -1052(%edi,%ecx), %ecx
        fldl      8(%ebp)
        cmpl      $17, %ecx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        fsubr     %st, %st(1)
        movl      %ecx, 20(%esp)
        jl        ..B1.9
..B1.8:
        fldl      _DP@GOTOFF(%ecx,%edx,8)
        fmul      %st(2), %st
        fldl      8+_DP@GOTOFF(%ecx,%edx,8)
        incl      %edx
        fmul      %st(2), %st
        faddp     %st, %st(1)
        fstl      (%esp)
        andl      $-1048576, (%esp)
        fsubl     (%esp)
        jmp       ..B1.10
..B1.9:
        fldz      
..B1.10:
        fld       %st(1)
        fld       %st(2)
        fldl      8+_DP@GOTOFF(%ecx,%edx,8)
        fmul      %st, %st(2)
        fmul      %st(5), %st
        fldl      _DP@GOTOFF(%ecx,%edx,8)
        fmul      %st(6), %st
        faddp     %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fld       %st(2)
        fldl      16+_DP@GOTOFF(%ecx,%edx,8)
        fmul      %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(3)
        fadd      %st(2), %st
        fstpl     (%esp)
        movzwl    6(%esp), %ecx
        andl      $32752, %ecx
        shrl      $4, %ecx
        negl      %ecx
        addl      $1075, %ecx
        movl      4(%esp), %esi
        cmpl      $31, %ecx
        movl      (%esp), %edi
        jbe       ..B1.12
..B1.11:
        movl      %esi, %edi
        movl      %edi, %esi
        sarl      $31, %esi
        sarl      %cl, %edi
        jmp       ..B1.13
..B1.12:
        shrdl     %cl, %esi, %edi
        sarl      %cl, %esi
..B1.13:
        movl      %edi, (%esp)
        cmpl      $31, %ecx
        movl      %esi, 4(%esp)
        jbe       ..B1.15
..B1.14:
        movl      %edi, %esi
        movl      $0, 16(%esp)
        shll      %cl, %esi
        jmp       ..B1.16
..B1.15:
        movl      %edi, %eax
        shll      %cl, %eax
        shldl     %cl, %edi, %esi
        movl      %eax, 16(%esp)
..B1.16:
        movl      16(%esp), %ecx
        addl      %edi, %ebx
        movl      %ecx, (%esp)
        fld       %st(3)
        movl      %esi, 4(%esp)
        movl      %ebx, %esi
        andl      $1, %esi
        movl      20(%esp), %edi
        fxch      %st(1)
        fmul      %st(5), %st
        fxch      %st(3)
        fsubl     (%esp)
        fldl      24+_DP@GOTOFF(%edi,%edx,8)
        fldl      32+_DP@GOTOFF(%edi,%edx,8)
        cmpl      $0, 12(%esp)
        fxch      %st(2)
        faddl     zero_none@GOTOFF(%edi,%esi,8)
        faddp     %st, %st(4)
        fld       %st(5)
        fmul      %st(1), %st
        faddp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fld       %st(4)
        fmul      %st(1), %st
        fxch      %st(4)
        fmul      %st(6), %st
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(4), %st
        fldl      40+_DP@GOTOFF(%edi,%edx,8)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(2)
        fxch      %st(2)
        fmul      %st(4), %st
        fxch      %st(3)
        fmull     56+_DP@GOTOFF(%edi,%edx,8)
        fldl      48+_DP@GOTOFF(%edi,%edx,8)
        fmul      %st, %st(3)
        movl      8(%esp), %edx
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fmull     .L_2il0floatpacket.0@GOTOFF(%edi)
        fstpl     (%edx)
        je        ..B1.18
..B1.17:
        fldcw     26(%esp)
..B1.18:
        movl      %ebx, %eax
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.19:
        movl      $0, 12(%esp)
        jmp       ..B1.7
        .align    16,0x90
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
# End
