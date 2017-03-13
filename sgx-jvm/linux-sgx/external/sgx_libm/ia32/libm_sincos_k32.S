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
	.hidden __libm_sincos_k32
	.globl __libm_sincos_k32
__libm_sincos_k32:
# parameter 1: 8 + %ebp
# parameter 2: 16 + %ebp
# parameter 3: 20 + %ebp
# parameter 4: 24 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $52, %esp
        fldl      8(%ebp)
        movl      12(%ebp), %edi
        movl      %edi, %edx
        call      ..L2
..L2:
        popl      %esi
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%esi), %esi
        andl      $2147483647, %edx
        fabs      
        shrl      $31, %edi
        movl      24(%ebp), %ecx
        movl      16(%ebp), %eax
        movl      20(%ebp), %ebx
        imull     iones@GOTOFF(%esi,%edi,4), %ecx
        movl      %eax, 36(%esp)
        cmpl      $1103101952, %edx
        jae       ..B1.9
..B1.2:
        cmpl      $1073283072, %edx
        ja        ..B1.6
..B1.3:
        incl      %ecx
        movl      %ecx, %edx
        andl      $2, %edx
        je        ..B1.5
..B1.4:
        fsubl     .L_2il0floatpacket.0@GOTOFF(%esi)
        fstl      8(%ebp)
        jmp       ..B1.11
..B1.5:
        fstl      8(%ebp)
        jmp       ..B1.11
..B1.6:
        fldl      _INV_PI04@GOTOFF(%esi)
        fmul      %st(1), %st
        movl      %edi, 32(%esp)
        fxch      %st(1)
        fstl      8(%ebp)
        fldl      _TWO_52H@GOTOFF(%esi)
        fadd      %st(2), %st
        fstpl     16(%esp)
        fildl     16(%esp)
        movl      16(%esp), %edi
        fcomp     %st(2)
        fnstsw    %ax
        fxch      %st(1)
        fstp      %st(0)
        sahf      
        movl      $1, %eax
        ja        ..L3
        movl      $0, %eax
..L3:
        subl      %eax, %edi
        addl      %edi, %ecx
        movl      %ecx, %eax
        andl      $1, %eax
        addl      %eax, %edi
        addl      %eax, %ecx
        movl      %edi, 24(%esp)
        cmpl      $1090519040, %edx
        fildl     24(%esp)
        movl      32(%esp), %edi
        jae       ..B1.8
..B1.7:
        fldl      _DP2@GOTOFF(%esi)
        movl      %ecx, %edx
        fmul      %st(1), %st
        andl      $2, %edx
        fsubrp    %st, %st(2)
        fldl      8+_DP2@GOTOFF(%esi)
        fmul      %st(1), %st
        fsubrp    %st, %st(2)
        fldl      16+_DP2@GOTOFF(%esi)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstl      8(%ebp)
        jmp       ..B1.11
..B1.8:
        fstp      %st(1)
        fldl      _DP3@GOTOFF(%esi)
        movl      %ecx, %edx
        fmul      %st(1), %st
        andl      $2, %edx
        fldl      8+_DP3@GOTOFF(%esi)
        fmul      %st(2), %st
        fxch      %st(1)
        fsubrl    8(%ebp)
        fsubp     %st, %st(1)
        fldl      16+_DP3@GOTOFF(%esi)
        fmul      %st(2), %st
        fsubrp    %st, %st(1)
        fldl      24+_DP3@GOTOFF(%esi)
        fmulp     %st, %st(2)
        fsubp     %st, %st(1)
        fstl      8(%ebp)
        jmp       ..B1.11
..B1.9:
        fstl      (%esp)
        lea       8(%ebp), %eax
        movl      %eax, 8(%esp)
        fstpl     (%eax)
        movl      %ecx, 12(%esp)
        call      __libm_reduce_pi04d
..B1.17:
        movl      %eax, %ecx
..B1.10:
        fldl      8(%ebp)
        incl      %ecx
        movl      %ecx, %edx
        andl      $2, %edx
..B1.11:
        fldl      24+_CP@GOTOFF(%esi)
        fld       %st(1)
        fmul      %st(2), %st
        lea       2(%ecx), %eax
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(2)
        fld1      
        fxch      %st(3)
        faddl     8+_CP@GOTOFF(%esi)
        fmul      %st(1), %st
        shrl      $2, %ecx
        faddp     %st, %st(3)
        fldl      32+_CP@GOTOFF(%esi)
        andl      $1, %ecx
        fmul      %st(1), %st
        xorl      %ecx, %edi
        fldl      24+_SP@GOTOFF(%esi)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_CP@GOTOFF(%esi)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     8+_SP@GOTOFF(%esi)
        fmul      %st(2), %st
        fmul      %st(5), %st
        shrl      $2, %eax
        fadd      %st(5), %st
        fxch      %st(1)
        faddl     _CP@GOTOFF(%esi)
        fmul      %st(3), %st
        andl      $1, %eax
        testl     %edx, %edx
        faddp     %st, %st(4)
        fldl      32+_SP@GOTOFF(%esi)
        fmul      %st(2), %st
        faddl     16+_SP@GOTOFF(%esi)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddl     _SP@GOTOFF(%esi)
        fmulp     %st, %st(2)
        fxch      %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(2)
        fldl      ones@GOTOFF(%esi,%edi,8)
        je        ..B1.13
..B1.12:
        fxch      %st(2)
        fmull     ones@GOTOFF(%esi,%eax,8)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      36(%esp), %edx
        fstpl     (%edx)
        fstpl     (%ebx)
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        fxch      %st(1)
        fmull     ones@GOTOFF(%esi,%eax,8)
        fxch      %st(2)
        fmulp     %st, %st(1)
        movl      36(%esp), %edx
        fstpl     (%edx)
        fstpl     (%ebx)
..B1.14:
        addl      $52, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	__libm_sincos_k32,@function
	.size	__libm_sincos_k32,.-__libm_sincos_k32
	.data
# -- End  __libm_sincos_k32
	.section .rodata, "a"
	.align 8
	.align 8
.L_2il0floatpacket.0:
	.long	0x54442d18,0x3fe921fb
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
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
	.hidden __libm_reduce_pi04d
	.section .note.GNU-stack, ""
# End
