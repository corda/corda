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
	.file "log2l.c"
	.text
..TXTST0:
# -- Begin  log2l
	.text
       .align    16,0x90
	.globl log2l
log2l:
# parameter 1: 8 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $36, %esp
..B1.2:
        fnstcw    14(%esp)
..B1.3:
        movzbl    17(%ebp), %edi
        andl      $128, %edi
        shrl      $7, %edi
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      %edi, %ebx
        andl      $32767, %eax
        shll      $15, %ebx
        movl      12(%ebp), %esi
        orl       %eax, %ebx
        movl      %esi, %ecx
        shll      $16, %ebx
        shrl      $16, %ecx
        orl       %ecx, %ebx
        call      ..L2
..L2:
        popl      %ecx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ecx), %ecx
        cmpl      $2147450880, %ebx
        jae       ..B1.29
..B1.4:
        addl      $-1073676224, %ebx
        cmpl      $32863, %ebx
        ja        ..B1.13
..B1.5:
        cmpl      $16383, %eax
        je        ..B1.39
..B1.6:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.38
..B1.7:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.8:
        fldcw     12(%esp)
..B1.9:
        movl      $1, %eax
..B1.10:
        fldt      8(%ebp)
        testl     %eax, %eax
        fldl      _TWO_32H@GOTOFF(%ecx)
        fldt      84+_P@GOTOFF(%ecx)
        fldt      60+_P@GOTOFF(%ecx)
        fldt      36+_P@GOTOFF(%ecx)
        fxch      %st(4)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        fmul      %st(1), %st
        fmul      %st, %st(3)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(4)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(3), %st
        fld       %st(1)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fadd      %st(3), %st
        fsubp     %st, %st(3)
        fld       %st(1)
        fsub      %st(3), %st
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(4), %st
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st(5), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(5), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(5)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(5)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fldt      96+_P@GOTOFF(%ecx)
        fmul      %st, %st(1)
        fxch      %st(1)
        faddp     %st, %st(3)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.12
..B1.11:
        fldcw     14(%esp)
..B1.12:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.13:
        movzwl    14(%esp), %esi
        movl      %esi, %ebx
        andl      $768, %ebx
        cmpl      $768, %ebx
        je        ..B1.45
..B1.14:
        orl       $-64768, %esi
        movw      %si, 12(%esp)
..B1.15:
        fldcw     12(%esp)
..B1.16:
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      $1, %esi
        andl      $32767, %eax
..B1.17:
        testl     %eax, %eax
        jne       ..B1.20
..B1.18:
        fldt      8(%ebp)
        fmull     _TWO_75@GOTOFF(%ecx)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldl      _zeros@GOTOFF(%ecx)
        fxch      %st(1)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B1.19
        je        ..B1.42
..B1.19:
        fstp      %st(0)
        movzwl    16(%ebp), %edx
        movl      %edx, %eax
        movl      $-16458, %ebx
        andl      $32767, %eax
        jmp       ..B1.21
..B1.20:
        movl      $-16383, %ebx
..B1.21:
        movl      12(%ebp), %edi
        cmpl      $-2147483648, %edi
        jne       ..B1.26
..B1.22:
        cmpl      $0, 8(%ebp)
        jne       ..B1.26
..B1.23:
        addl      %eax, %ebx
        movl      %ebx, 16(%esp)
        testl     %esi, %esi
        fildl     16(%esp)
        fstpt     (%esp)
        je        ..B1.25
..B1.24:
        fldcw     14(%esp)
..B1.25:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.26:
        fldl      _TWO_32H@GOTOFF(%ecx)
        andl      $-32768, %edx
        fldt      84+_P@GOTOFF(%ecx)
        orl       $-49153, %edx
        fldt      60+_P@GOTOFF(%ecx)
        addl      %eax, %ebx
        movw      %dx, 16(%ebp)
        fldt      8(%ebp)
        fld       %st(0)
        shrl      $23, %edi
        fadd      %st(4), %st
        andl      $255, %edi
        fsubp     %st, %st(4)
        movl      %ebx, 16(%esp)
        fsub      %st(3), %st
        flds      __libm_rcp_table_256@GOTOFF(%ecx,%edi,4)
        fmul      %st, %st(4)
        fmulp     %st, %st(1)
        fxch      %st(3)
        faddl     8+_ones@GOTOFF(%ecx)
        fld       %st(0)
        shll      $4, %edi
        fadd      %st(4), %st
        fld       %st(0)
        testl     %esi, %esi
        fmul      %st(1), %st
        fmul      %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(2)
        fstpt     8(%ebp)
        fldt      8(%ebp)
        fldt      36+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fxch      %st(2)
        fmul      %st, %st(3)
        fldt      12+_P@GOTOFF(%ecx)
        faddp     %st, %st(4)
        fmul      %st, %st(3)
        fldt      72+_P@GOTOFF(%ecx)
        fmul      %st(1), %st
        fldt      48+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmul      %st(1), %st
        fldt      24+_P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(1)
        fldt      _P@GOTOFF(%ecx)
        faddp     %st, %st(1)
        fmulp     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fldt      96+_P@GOTOFF(%ecx)
        fmul      %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fmulp     %st, %st(2)
        fildl     16(%esp)
        fxch      %st(1)
        faddl     8+__libm_log2l_table_256@GOTOFF(%ecx,%edi)
        fxch      %st(1)
        faddl     __libm_log2l_table_256@GOTOFF(%edi,%ecx)
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        je        ..B1.28
..B1.27:
        fldcw     14(%esp)
..B1.28:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.29:
        movzwl    14(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.54
..B1.30:
        orl       $-64768, %edx
        movw      %dx, 12(%esp)
..B1.31:
        fldcw     12(%esp)
..B1.32:
        movzbl    17(%ebp), %edi
        movl      $1, %edx
        andl      $128, %edi
        shrl      $7, %edi
..B1.33:
        testl     %edi, %edi
        jne       ..B1.46
..B1.34:
        fldt      8(%ebp)
        fmull     _ones@GOTOFF(%ecx)
        fstpt     (%esp)
..B1.35:
        testl     %edx, %edx
        je        ..B1.37
..B1.36:
        fldcw     14(%esp)
..B1.37:
        fldt      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.38:
        xorl      %eax, %eax
        jmp       ..B1.10
..B1.39:
        cmpl      $-2147483648, %esi
        jne       ..B1.6
..B1.40:
        cmpl      $0, 8(%ebp)
        jne       ..B1.6
..B1.41:
        fldl      _zeros@GOTOFF(%ecx)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.42:
        fldl      8+_ones@GOTOFF(%ecx)
        testl     %esi, %esi
        fdivp     %st, %st(1)
        fstpl     (%esp)
        je        ..B1.44
..B1.43:
        fldcw     14(%esp)
..B1.44:
        fldl      (%esp)
        addl      $36, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.45:
        xorl      %esi, %esi
        jmp       ..B1.17
..B1.46:
        movzwl    16(%ebp), %eax
        andl      $32767, %eax
        cmpl      $32767, %eax
        jne       ..B1.49
..B1.47:
        cmpl      $-2147483648, 12(%ebp)
        jne       ..B1.34
..B1.48:
        cmpl      $0, 8(%ebp)
        jne       ..B1.34
        jmp       ..B1.53
..B1.49:
        testl     %eax, %eax
        jne       ..B1.53
..B1.50:
        cmpl      $0, 12(%ebp)
        jne       ..B1.53
..B1.51:
        cmpl      $0, 8(%ebp)
        jne       ..B1.53
..B1.52:
        fldl      8+_ones@GOTOFF(%ecx)
        fdivl     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.35
..B1.53:
        fldl      _infs@GOTOFF(%ecx)
        fmull     _zeros@GOTOFF(%ecx)
        fstpt     (%esp)
        jmp       ..B1.35
..B1.54:
        xorl      %edx, %edx
        jmp       ..B1.33
        .align    16,0x90
	.type	log2l,@function
	.size	log2l,.-log2l
	.data
# -- End  log2l
	.section .rodata, "a"
	.align 4
	.align 4
_TWO_32H:
	.long	0
	.long	1106771968
	.type	_TWO_32H,@object
	.size	_TWO_32H,8
	.align 4
_ones:
	.long	0
	.long	1072693248
	.long	0
	.long	3220176896
	.type	_ones,@object
	.size	_ones,16
	.align 4
_TWO_75:
	.long	0
	.long	1151336448
	.long	0
	.long	994050048
	.type	_TWO_75,@object
	.size	_TWO_75,16
	.align 4
_zeros:
	.long	0
	.long	0
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,16
	.align 4
_infs:
	.long	0
	.long	2146435072
	.long	0
	.long	4293918720
	.type	_infs,@object
	.size	_infs,16
	.align 2
_P:
	.word	17354
	.word	24031
	.word	3064
	.word	38062
	.word	16360
	.word	0
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	49150
	.word	0
	.word	60493
	.word	53279
	.word	20193
	.word	63032
	.word	16381
	.word	0
	.word	62043
	.word	23575
	.word	15145
	.word	47274
	.word	49149
	.word	0
	.word	33329
	.word	29325
	.word	25223
	.word	37819
	.word	16381
	.word	0
	.word	5068
	.word	45194
	.word	20193
	.word	63032
	.word	49148
	.word	0
	.word	43239
	.word	63403
	.word	6517
	.word	54028
	.word	16380
	.word	0
	.word	25259
	.word	45714
	.word	45135
	.word	47274
	.word	49148
	.word	0
	.word	0
	.word	0
	.word	14848
	.word	47274
	.word	16383
	.word	0
	.type	_P,@object
	.size	_P,108
	.data
	.hidden __libm_rcp_table_256
	.hidden __libm_log2l_table_256
	.section .note.GNU-stack, ""
# End
