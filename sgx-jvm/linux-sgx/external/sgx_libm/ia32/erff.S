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
	.file "erff.c"
	.text
..TXTST0:
# -- Begin  erff
	.text
       .align    16,0x90
	.globl erff
erff:
# parameter 1: 16 + %esp
..B1.1:
..L1:

        subl      $12, %esp
        movl      16(%esp), %edx
        movl      %edx, %eax
        andl      $-2147483648, %eax
        andl      $2147483647, %edx
        orl       $1065353216, %eax
        movl      %eax, (%esp)
        cmpl      $2139095040, %edx
        flds      (%esp)
        jge       ..B1.8
..B1.2:
        call      ..L2
..L2:
        popl      %eax
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%eax), %eax
        cmpl      $1081791744, %edx
        jge       ..B1.7
..B1.3:
        cmpl      $1065353216, %edx
        jl        ..B1.5
..B1.4:
        movl      %edx, %ecx
        andl      $8388607, %edx
        shrl      $23, %ecx
        orl       $1065353216, %edx
        movl      %edx, (%esp)
        flds      (%esp)
        fsubl     .L_2il0floatpacket.0@GOTOFF(%eax)
        fld       %st(0)
        lea       (%ecx,%ecx,2), %ecx
        shll      $5, %ecx
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      -12104+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(3), %st
        fldl      -12120+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     -12112+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(3), %st
        faddp     %st, %st(1)
        fldl      -12072+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(4), %st
        fxch      %st(1)
        faddl     -12128+_dbA@GOTOFF(%eax,%ecx)
        fxch      %st(1)
        faddl     -12080+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(3), %st
        fldl      -12088+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(5), %st
        faddp     %st, %st(1)
        fldl      -12040+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(5), %st
        fxch      %st(1)
        faddl     -12096+_dbA@GOTOFF(%eax,%ecx)
        fxch      %st(1)
        faddl     -12048+_dbA@GOTOFF(%eax,%ecx)
        fmulp     %st, %st(4)
        fldl      -12056+_dbA@GOTOFF(%eax,%ecx)
        fmulp     %st, %st(5)
        fxch      %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        faddl     -12064+_dbA@GOTOFF(%eax,%ecx)
        fmul      %st(1), %st
        faddp     %st, %st(3)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        addl      $12, %esp
        ret       
..B1.5:
        fstp      %st(0)
        flds      16(%esp)
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fldl      48+_dbA@GOTOFF(%eax)
        fmul      %st(1), %st
        fldl      56+_dbA@GOTOFF(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     32+_dbA@GOTOFF(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     40+_dbA@GOTOFF(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     16+_dbA@GOTOFF(%eax)
        fmul      %st(2), %st
        fxch      %st(1)
        faddl     24+_dbA@GOTOFF(%eax)
        fmulp     %st, %st(2)
        faddl     _dbA@GOTOFF(%eax)
        fxch      %st(1)
        faddl     8+_dbA@GOTOFF(%eax)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
..B1.6:
        addl      $12, %esp
        ret       
..B1.7:
        fldl      256+_dbA@GOTOFF(%eax)
        fmulp     %st, %st(1)
        addl      $12, %esp
        ret       
..B1.8:
        je        ..B1.11
..B1.9:
        fstp      %st(0)
        flds      16(%esp)
        fadd      %st(0), %st
..B1.11:
        addl      $12, %esp
        ret       
        .align    16,0x90
	.type	erff,@function
	.size	erff,.-erff
	.data
# -- End  erff
	.section .rodata, "a"
	.align 16
	.align 16
_dbA:
	.long	1346373606
	.long	1072827863
	.long	1710987328
	.long	3218608756
	.long	2039146822
	.long	1069343472
	.long	2052989911
	.long	3214639770
	.long	3741021613
	.long	1064657550
	.long	2828932584
	.long	3209415657
	.long	556515485
	.long	1058898537
	.long	1493771156
	.long	3202627648
	.long	1460823802
	.long	1072622165
	.long	647757120
	.long	1069445687
	.long	1478820123
	.long	3217479081
	.long	827266439
	.long	1069662880
	.long	4153551678
	.long	3215381932
	.long	3249389370
	.long	3213783616
	.long	3875823656
	.long	1066650297
	.long	2479749796
	.long	3212012677
	.long	4079794357
	.long	3210967401
	.long	3292960271
	.long	1062989018
	.long	1338933997
	.long	3204757561
	.long	4209598023
	.long	3207545998
	.long	2886434981
	.long	1072693201
	.long	1721637260
	.long	1060257935
	.long	960688093
	.long	3210436681
	.long	1128797080
	.long	1064950642
	.long	798939459
	.long	3213958757
	.long	937205111
	.long	1067493282
	.long	3504975339
	.long	3215465534
	.long	3388113641
	.long	1068080152
	.long	9542307
	.long	3215088353
	.long	1310184383
	.long	1065080747
	.long	3264801679
	.long	1066837484
	.long	3394984936
	.long	3214223095
	.long	4294967295
	.long	1072693247
	.type	_dbA,@object
	.size	_dbA,264
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3ff80000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.section .note.GNU-stack, ""
# End
