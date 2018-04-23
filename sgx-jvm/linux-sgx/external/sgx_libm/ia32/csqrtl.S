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
	.file "csqrtl.c"
	.text
..TXTST0:
# -- Begin  csqrtl
	.text
       .align    16,0x90
	.globl csqrtl
csqrtl:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        movl      8(%ebp), %eax
        movl      %eax, 44(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        movl      %gs:20, %edx
        xorl      %esp, %edx
        movl      %edx, 196(%esp)
        fstpt     172(%esp)
        fldt      172(%esp)
        fstpt     184(%esp)
..B1.2:
        fnstcw    62(%esp)
..B1.3:
        movzwl    62(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B1.69
..B1.4:
        orl       $-64768, %edx
        movw      %dx, 60(%esp)
..B1.5:
        fldcw     60(%esp)
..B1.6:
        movl      $1, %esi
..B1.7:
        movzwl    20(%ebp), %ecx
        movl      %ecx, %edi
        andl      $32767, %edi
        cmpl      $32767, %edi
        jge       ..B1.30
..B1.8:
        cmpl      $0, 16(%ebp)
        jne       ..B1.10
..B1.9:
        cmpl      $0, 12(%ebp)
        je        ..B1.30
..B1.10:
        movzwl    32(%ebp), %eax
        movl      %eax, %edx
        andl      $32767, %edx
        cmpl      $32767, %edx
        jge       ..B1.48
..B1.11:
        cmpl      $0, 28(%ebp)
        jne       ..B1.13
..B1.12:
        cmpl      $0, 24(%ebp)
        je        ..B1.32
..B1.13:
        fldt      12(%ebp)
        testl     %edi, %edi
        jle       ..B1.56
..B1.14:
        andl      $-32768, %ecx
        addl      $-16383, %edi
        fstpt     148(%esp)
        fldt      148(%esp)
        orl       $-49153, %ecx
        movw      %cx, 156(%esp)
..B1.15:
        fldt      24(%ebp)
        testl     %edx, %edx
        movb      $63, 157(%esp)
        jle       ..B1.55
..B1.16:
        andl      $-32768, %eax
        addl      $-16383, %edx
        fstpt     76(%esp)
        fldt      76(%esp)
        orl       $-49153, %eax
        movw      %ax, 84(%esp)
..B1.17:
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        movb      $63, 85(%esp)
        fstpt     88(%esp)
        fldt      88(%esp)
        addl      $-32, %esp
        lea       132(%esp), %eax
        movl      %eax, (%esp)
        fxch      %st(2)
        fstpt     4(%esp)
        fstpt     16(%esp)
        fstpt     32(%esp)
        movl      %edx, -72(%eax)
        call      __libm_hypot2l_k80
..B1.72:
        fldt      32(%esp)
        movl      60(%esp), %edx
        addl      $32, %esp
..B1.18:
        fldt      132+_CONSTANTS@GOTOFF(%ebx)
        faddp     %st, %st(2)
        fxch      %st(1)
        fstpt     (%esp)
        fldt      100(%esp)
        fld       %st(0)
        fsqrt     
        fldt      84+_CONSTANTS@GOTOFF(%ebx)
        fstpt     32(%esp)
        fldt      32(%esp)
        fmul      %st(1), %st
        fld       %st(0)
        movl      (%esp), %eax
        fsubr     %st(2), %st
        sarl      $1, %eax
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(2), %st
        fld       %st(1)
        fmul      %st(2), %st
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(2), %st
        fsubr     %st, %st(2)
        fxch      %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        fmul      %st(0), %st
        faddp     %st, %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fldt      112(%esp)
        faddp     %st, %st(2)
        fldt      24+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st, %st(2)
        fxch      %st(1)
        fdivr     %st, %st(2)
        fxch      %st(1)
        fstpt     16(%esp)
        fld       %st(0)
        fadd      %st(2), %st
        fstpt     124(%esp)
        fldt      124(%esp)
        fsubr     %st, %st(1)
        movzwl    132(%esp), %ecx
        fxch      %st(1)
        faddp     %st, %st(2)
        andl      $32767, %ecx
        movl      %ecx, 12(%esp)
        fxch      %st(1)
        fstpt     136(%esp)
        fldt      136(%esp)
        lea       -16464(%ecx,%eax), %ecx
        cmpl      %ecx, %edi
        jle       ..B1.22
..B1.19:
        movl      12(%esp), %ecx
        lea       -16302(%ecx,%eax), %ecx
        cmpl      %ecx, %edi
        jge       ..B1.21
..B1.20:
        fstp      %st(2)
        subl      %eax, %edi
        fld       %st(0)
        addl      $16383, %edi
        fld       %st(1)
        movzwl    156(%esp), %ecx
        andl      $32767, %edi
        andl      $-32768, %ecx
        orl       %edi, %ecx
        movl      %eax, %edi
        movw      %cx, 156(%esp)
        fldt      148(%esp)
        fadd      %st, %st(2)
        fld       %st(2)
        fxch      %st(2)
        fsubr     %st(3), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(4)
        fsubp     %st, %st(1)
        faddp     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        jmp       ..B1.23
..B1.21:
        fstp      %st(0)
        fstp      %st(0)
        fldt      148(%esp)
        jmp       ..B1.23
..B1.22:
        fstp      %st(2)
        movl      %eax, %edi
..B1.23:
        fldt      32(%esp)
        lea       1(%edi), %ecx
        andl      $1, %ecx
        subl      %ecx, %edi
        decl      %edi
        sarl      $1, %edi
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %eax
        fldt      48+_CONSTANTS@GOTOFF(%eax,%ebx)
        fmul      %st, %st(2)
        fld       %st(2)
        fsqrt     
        fmul      %st, %st(2)
        fld       %st(2)
        fsubr     %st(1), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fsubr     %st(1), %st
        fxch      %st(2)
        fmulp     %st, %st(5)
        fld       %st(2)
        fmul      %st(3), %st
        fxch      %st(2)
        fmul      %st, %st(3)
        fxch      %st(3)
        fadd      %st(0), %st
        fld       %st(0)
        fadd      %st(3), %st
        fsubr     %st, %st(3)
        fxch      %st(1)
        faddp     %st, %st(3)
        fxch      %st(3)
        fmul      %st(0), %st
        faddp     %st, %st(2)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fsubrp    %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fld       %st(1)
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fdiv      %st(2), %st
        fadd      %st, %st(1)
        fxch      %st(1)
        fstpt     48(%esp)
        fldt      48(%esp)
        addl      $-32, %esp
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        fldt      12(%esp)
        movl      %edi, 24(%esp)
        fstpt     96(%esp)
        movl      %edx, 60(%esp)
        call      __libm_scalbl_k80
..B1.73:
        fldt      96(%esp)
        movl      60(%esp), %edx
        fxch      %st(1)
        fstpt     32(%esp)
        addl      $32, %esp
..B1.24:
        fldt      76(%esp)
        subl      %edi, %edx
        fldt      48(%esp)
        fld       %st(0)
        fdivr     %st(2), %st
        fmul      %st, %st(3)
        fld       %st(1)
        fldt      32(%esp)
        fld       %st(0)
        fmul      %st(3), %st
        fld       %st(0)
        fsubr     %st(4), %st
        faddp     %st, %st(1)
        fld       %st(0)
        fsubr     %st(4), %st
        fxch      %st(2)
        fmul      %st(5), %st
        fsubr     %st, %st(3)
        faddp     %st, %st(3)
        fxch      %st(2)
        fsubr     %st, %st(4)
        fld       %st(2)
        fmul      %st(1), %st
        fxch      %st(1)
        fmul      %st(2), %st
        fxch      %st(3)
        fmul      %st(5), %st
        faddp     %st, %st(3)
        fld       %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(1)
        fxch      %st(3)
        faddp     %st, %st(1)
        fxch      %st(1)
        fmulp     %st, %st(4)
        faddp     %st, %st(3)
        fld       %st(1)
        fxch      %st(1)
        fsubrp    %st, %st(4)
        fxch      %st(2)
        fsubrp    %st, %st(3)
        fldt      88(%esp)
        faddp     %st, %st(3)
        fxch      %st(3)
        fsubrp    %st, %st(2)
        fldt      48(%esp)
        fdivrp    %st, %st(2)
        fadd      %st(1), %st
        fsubr     %st, %st(2)
        fxch      %st(2)
        faddp     %st, %st(1)
        fldt      16(%esp)
        fmul      %st, %st(2)
        fmulp     %st, %st(1)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        movl      %edx, 24(%esp)
        call      __libm_scalbl_k80
..B1.74:
        addl      $32, %esp
..B1.25:
        movzbl    21(%ebp), %eax
        testl     $128, %eax
        jne       ..B1.27
..B1.26:
        fldt      (%esp)
        lea       184(%esp), %edi
        fstpt     -12(%edi)
        jmp       ..B1.28
..B1.27:
        fldt      (%esp)
        lea       184(%esp), %edi
        fxch      %st(1)
        fstpt     -12(%edi)
..B1.28:
        movzbl    33(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _CONSTANTS@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     (%edi)
        jmp       ..B1.51
..B1.30:
        movzwl    32(%ebp), %edx
        andl      $32767, %edx
..B1.31:
        cmpl      $32767, %edx
        jge       ..B1.48
..B1.32:
        movl      16(%ebp), %eax
        testl     %eax, %eax
        jne       ..B1.40
..B1.33:
        cmpl      $0, 12(%ebp)
        jne       ..B1.40
..B1.34:
        cmpl      $0, 28(%ebp)
        jne       ..B1.36
..B1.35:
        cmpl      $0, 24(%ebp)
        je        ..B1.40
..B1.36:
        fldt      24(%ebp)
        movzbl    33(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        cmpl      $2, %edx
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %eax
        fldt      _CONSTANTS@GOTOFF(%eax,%ebx)
        jl        ..B1.38
..B1.37:
        fldt      24+_CONSTANTS@GOTOFF(%ebx,%eax)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fsqrt     
        jmp       ..B1.39
..B1.38:
        fldt      108+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(2)
        fmul      %st, %st(1)
        fxch      %st(1)
        fsqrt     
        fldt      156+_CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
..B1.39:
        fstpt     172(%esp)
        fldt      172(%esp)
        fmulp     %st, %st(1)
        fstpt     184(%esp)
        jmp       ..B1.51
..B1.40:
        cmpl      $32767, %edi
        jl        ..B1.43
..B1.41:
        cmpl      $-2147483648, %eax
        jne       ..B1.47
..B1.42:
        cmpl      $0, 12(%ebp)
        jne       ..B1.47
..B1.43:
        fldt      12(%ebp)
        fldt      72+_CONSTANTS@GOTOFF(%ebx)
        movzbl    21(%ebp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        testl     %ecx, %ecx
        lea       (,%ecx,8), %eax
        lea       (%eax,%ecx,4), %edx
        fldt      _CONSTANTS@GOTOFF(%ebx,%edx)
        fmulp     %st, %st(2)
        fxch      %st(1)
        fstpt     148(%esp)
        fldt      148(%esp)
        fsqrt     
        jne       ..B1.45
..B1.44:
        fstpt     172(%esp)
        lea       184(%esp), %edi
        jmp       ..B1.46
..B1.45:
        fxch      %st(1)
        fstpt     172(%esp)
        lea       184(%esp), %edi
..B1.46:
        movzbl    33(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _CONSTANTS@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     (%edi)
        jmp       ..B1.51
..B1.47:
        fldt      12(%ebp)
        fmul      %st(0), %st
        fstpt     172(%esp)
        fldt      172(%esp)
        fstpt     184(%esp)
        jmp       ..B1.51
..B1.48:
        je        ..B1.63
..B1.49:
        cmpl      $32767, %edi
        je        ..B1.58
..B1.50:
        fldt      12(%ebp)
        fld       %st(0)
        fldt      24(%ebp)
        fmul      %st, %st(1)
        fxch      %st(1)
        fmul      %st, %st(2)
        fxch      %st(2)
        fstpt     172(%esp)
        fmulp     %st, %st(1)
        fstpt     184(%esp)
..B1.51:
        testl     %esi, %esi
        je        ..B1.53
..B1.52:
        fldcw     62(%esp)
..B1.53:
        movl      44(%esp), %eax
        movl      172(%esp), %edx
        movl      176(%esp), %ecx
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      180(%esp), %esi
        movl      184(%esp), %edi
        movl      188(%esp), %edx
        movl      192(%esp), %ecx
        movl      %esi, 8(%eax)
        movl      %edi, 12(%eax)
        movl      %edx, 16(%eax)
        movl      %ecx, 20(%eax)
        movl      196(%esp), %eax
        xorl      %esp, %eax
        cmpl      %gs:20, %eax
        jne       ..B1.57
..B1.54:
        movl      44(%esp), %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.55:
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     76(%esp)
        movzwl    84(%esp), %eax
        movl      %eax, %edx
        andl      $-32768, %eax
        andl      $32767, %edx
        orl       $-49153, %eax
        addl      $-16639, %edx
        movw      %ax, 84(%esp)
        jmp       ..B1.17
..B1.56:
        fldt      120+_CONSTANTS@GOTOFF(%ebx)
        fmul      %st(1), %st
        fstpt     148(%esp)
        movzwl    156(%esp), %ecx
        movl      %ecx, %edi
        andl      $-32768, %ecx
        andl      $32767, %edi
        orl       $-49153, %ecx
        addl      $-16639, %edi
        movw      %cx, 156(%esp)
        jmp       ..B1.15
..B1.57:
        call      __stack_chk_fail@PLT
..B1.58:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.50
..B1.59:
        cmpl      $0, 12(%ebp)
        jne       ..B1.50
..B1.60:
        fldt      12(%ebp)
        movzbl    21(%ebp), %eax
        testl     $128, %eax
        fmul      %st(0), %st
        fldt      24(%ebp)
        fmul      %st(0), %st
        jne       ..B1.62
..B1.61:
        fxch      %st(1)
        fstpt     172(%esp)
        fstpt     184(%esp)
        jmp       ..B1.51
..B1.62:
        fstpt     172(%esp)
        fstpt     184(%esp)
        jmp       ..B1.51
..B1.63:
        cmpl      $-2147483648, 28(%ebp)
        jne       ..B1.49
..B1.64:
        cmpl      $0, 24(%ebp)
        jne       ..B1.49
..B1.65:
        fldt      24(%ebp)
        movzbl    33(%ebp), %edx
        andl      $128, %edx
        shrl      $7, %edx
        fmul      %st(0), %st
        cmpl      $32767, %edi
        fstpt     172(%esp)
        fldt      172(%esp)
        lea       (,%edx,8), %eax
        lea       (%eax,%edx,4), %ecx
        fldt      _CONSTANTS@GOTOFF(%ebx,%ecx)
        fmulp     %st, %st(1)
        fstpt     184(%esp)
        jne       ..B1.51
..B1.66:
        cmpl      $-2147483648, 16(%ebp)
        jne       ..B1.68
..B1.67:
        cmpl      $0, 12(%ebp)
        je        ..B1.51
..B1.68:
        fldt      12(%ebp)
        fldt      _CONSTANTS@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     12(%ebp)
        jmp       ..B1.51
..B1.69:
        xorl      %esi, %esi
        jmp       ..B1.7
        .align    16,0x90
	.type	csqrtl,@function
	.size	csqrtl,.-csqrtl
	.data
# -- End  csqrtl
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
_CONSTANTS:
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49151
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16382
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	49150
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16383
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16384
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	0
	.word	32768
	.word	16415
	.word	0
	.word	49807
	.word	10485
	.word	36700
	.word	62914
	.word	16377
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	32766
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16639
	.word	0
	.word	0
	.word	0
	.word	0
	.word	49152
	.word	16446
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	16127
	.word	0
	.word	0
	.word	0
	.word	0
	.word	32768
	.word	8191
	.word	0
	.type	_CONSTANTS,@object
	.size	_CONSTANTS,168
	.data
	.hidden __libm_hypot2l_k80
	.hidden __libm_scalbl_k80
	.section .note.GNU-stack, ""
# End
