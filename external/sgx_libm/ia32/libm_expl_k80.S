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
	.file "libm_expl_k80.c"
	.text
..TXTST0:
# -- Begin  __libm_expl_k80
	.text
       .align    16,0x90
	.hidden __libm_expl_k80
	.globl __libm_expl_k80
__libm_expl_k80:
# parameter 1: 8 + %ebp
# parameter 2: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-16, %esp
        pushl     %edi
        pushl     %ebx
        subl      $40, %esp
        movl      12(%ebp), %ebx
        movl      8(%ebp), %ecx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        fldt      (%ebx)
        movzbl    9(%ebx), %eax
        movzwl    8(%ebx), %edi
        andl      $128, %eax
        andl      $32767, %edi
        shrl      $7, %eax
        fstpt     24(%esp)
        fldt      24(%esp)
        cmpl      $32767, %edi
        fldt      12(%ebx)
        je        ..B1.11
..B1.2:
        cmpl      $16358, %edi
        jge       ..B1.7
..B1.3:
        fldt      .L_2il0floatpacket.0@GOTOFF(%edx)
        cmpl      $16183, %edi
        jge       ..B1.5
..B1.4:
        fstp      %st(2)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fstpt     12(%ecx)
        fldt      12(%ecx)
        fxch      %st(1)
        fstpt     (%ecx)
        addl      $40, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.5:
        fldt      12+_P1@GOTOFF(%edx)
        fmul      %st(3), %st
        fldt      _P1@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(3), %st
        fmul      %st(3), %st
        faddp     %st, %st(3)
        fld       %st(0)
        fadd      %st(3), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(1)
        faddp     %st, %st(2)
        faddp     %st, %st(1)
        fstpt     12(%ecx)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
..B1.6:
        addl      $40, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.7:
        cmpl      $16405, %edi
        jge       ..B1.9
..B1.8:
        fldt      _KLN2@GOTOFF(%edx)
        fmulp     %st, %st(2)
        fldt      _TWO_63H@GOTOFF(%edx)
        fadd      %st, %st(2)
        fxch      %st(2)
        fstpt     (%esp)
        fldt      (%esp)
        movl      (%esp), %eax
        fsubp     %st, %st(2)
        fldt      24(%esp)
        fldt      _LN2K@GOTOFF(%edx)
        fmul      %st(3), %st
        fstpt     (%esp)
        fxch      %st(2)
        fchs      
        fldt      (%esp)
        movsbl    %al, %ebx
        fsubrp    %st, %st(3)
        fldt      12+_LN2K@GOTOFF(%edx)
        subl      %ebx, %eax
        fmulp     %st, %st(1)
        fstpt     (%esp)
        fldt      (%esp)
        shll      $4, %ebx
        fadd      %st(2), %st
        fld       %st(0)
        sarl      $8, %eax
        fsub      %st(3), %st
        fld       %st(1)
        movl      %eax, 16(%esp)
        fsub      %st(1), %st
        fsubrp    %st, %st(4)
        fldt      (%esp)
        fsubp     %st, %st(1)
        faddp     %st, %st(3)
        fld       %st(1)
        fadd      %st(1), %st
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(2)
        fsub      %st(2), %st
        fchs      
        fld       %st(0)
        fsubr     %st(3), %st
        fsubrp    %st, %st(4)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        faddp     %st, %st(3)
        fxch      %st(3)
        faddp     %st, %st(2)
        fldt      48+_P@GOTOFF(%edx)
        fmul      %st(1), %st
        fildl     16(%esp)
        fldt      24+_P@GOTOFF(%edx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fmul      %st(2), %st
        fldt      _P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmul      %st(2), %st
        fldt      36+_P@GOTOFF(%edx)
        fmul      %st(3), %st
        fldt      12+_P@GOTOFF(%edx)
        faddp     %st, %st(1)
        fmulp     %st, %st(3)
        fxch      %st(2)
        fmul      %st(4), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldl      2056+__libm_expl_table_256@GOTOFF(%edx,%ebx)
        fldl      2048+__libm_expl_table_256@GOTOFF(%edx,%ebx)
        fld       %st(0)
        fmul      %st(3), %st
        fxch      %st(3)
        fadd      %st(5), %st
        fmul      %st(2), %st
        faddp     %st, %st(3)
        fmul      %st, %st(4)
        fld       %st(0)
        fadd      %st(5), %st
        fsubr     %st, %st(1)
        fxch      %st(5)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fld       %st(2)
        fadd      %st(1), %st
        fstpt     (%ecx)
        fldt      (%ecx)
        fsubrp    %st, %st(3)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     12(%ecx)
        addl      $40, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.9:
        fstp      %st(1)
        fstp      %st(0)
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        lea       (,%eax,8), %ebx
        fstpt     12(%ecx)
        lea       (%ebx,%eax,4), %eax
        fldt      _inf_zeros@GOTOFF(%edx,%eax)
        fstpt     (%ecx)
        fldt      (%ecx)
..B1.10:
        addl      $40, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
..B1.11:
        fstp      %st(0)
        cmpl      $-2147483648, 28(%esp)
        jne       ..B1.15
..B1.12:
        cmpl      $0, 24(%esp)
        jne       ..B1.15
..B1.13:
        fstp      %st(0)
        lea       (,%eax,8), %ebx
        lea       (%ebx,%eax,4), %eax
        fldt      _inf_zeros@GOTOFF(%edx,%eax)
..B1.15:
        fldt      .L_2il0floatpacket.1@GOTOFF(%edx)
        fstpt     12(%ecx)
        fstpt     (%ecx)
        fldt      (%ecx)
        addl      $40, %esp
        popl      %ebx
        popl      %edi
        movl      %ebp, %esp
        popl      %ebp
        ret       
        .align    16,0x90
	.type	__libm_expl_k80,@function
	.size	__libm_expl_k80,.-__libm_expl_k80
	.data
# -- End  __libm_expl_k80
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.0:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.align 2
_P1:
	.word	43691
	.word	10
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	30584
	.word	43703
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.type	_P1,@object
	.size	_P1,24
	.align 2
_KLN2:
	.word	61628
	.word	23575
	.word	15145
	.word	47274
	.word	16391
	.word	0
	.type	_KLN2,@object
	.size	_KLN2,12
	.align 2
_TWO_63H:
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.type	_TWO_63H,@object
	.size	_TWO_63H,12
	.align 2
_LN2K:
	.word	0
	.word	0
	.word	6134
	.word	45426
	.word	16374
	.word	0
	.word	55756
	.word	58609
	.word	48341
	.word	59623
	.word	16343
	.word	0
	.type	_LN2K,@object
	.size	_LN2K,24
	.align 2
_P:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	10558
	.word	43680
	.word	43690
	.word	43690
	.word	16380
	.word	0
	.word	59664
	.word	43680
	.word	43690
	.word	43690
	.word	16378
	.word	0
	.word	56450
	.word	15979
	.word	35652
	.word	34952
	.word	16376
	.word	0
	.word	7105
	.word	47411
	.word	25657
	.word	46603
	.word	16373
	.word	0
	.type	_P,@object
	.size	_P,60
	.align 2
_inf_zeros:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32767
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.type	_inf_zeros,@object
	.size	_inf_zeros,24
	.data
	.hidden __libm_expl_table_256
	.section .note.GNU-stack, ""
# End
