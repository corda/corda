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
	.file "clog_gen.c"
	.text
..TXTST0:
# -- Begin  __libm_internal_clog
	.text
       .align    16,0x90
	.hidden __libm_internal_clog
	.globl __libm_internal_clog
__libm_internal_clog:
# parameter 1: 12 + %ebp
..B1.1:
..L1:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $180, %esp
        fldz      
        movl      %gs:20, %edx
        movl      8(%ebp), %esi
        xorl      %esp, %edx
        movl      %edx, 168(%esp)
        fstl      152(%esp)
        fstpl     160(%esp)
..B1.2:
        fnstcw    134(%esp)
..B1.3:
        movzwl    18(%ebp), %ecx
        andl      $32752, %ecx
        movzwl    26(%ebp), %edx
        andl      $32752, %edx
        shrl      $4, %ecx
        shrl      $4, %edx
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        cmpl      $2047, %ecx
        jge       ..B1.21
..B1.4:
        cmpl      $2047, %edx
        jge       ..B1.48
..B1.5:
        movzwl    134(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B1.26
..B1.6:
        orl       $-64768, %ecx
        movw      %cx, 132(%esp)
..B1.7:
        fldcw     132(%esp)
..B1.8:
        movl      $1, %edi
..B1.9:
        fldl      12(%ebp)
        fabs      
        fldl      20(%ebp)
        fabs      
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B1.11
        jp        ..B1.11
..B1.10:
        fld       %st(1)
        jmp       ..B1.12
..B1.11:
        fld       %st(0)
..B1.12:
        fxch      %st(1)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B1.14
..B1.13:
        fstp      %st(0)
        jmp       ..B1.15
..B1.14:
        fstp      %st(2)
..B1.15:
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fld       %st(3)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fld1      
        fsubr     %st(2), %st
        fabs      
        fcompl    .L_2il0floatpacket.6@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jbe       ..B1.17
..B1.16:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(1)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B1.44:
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     152(%esp)
        jmp       ..B1.18
..B1.17:
        fstp      %st(1)
        fxch      %st(3)
        fstpt     136(%esp)
        fstpt     (%esp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      12(%esp)
        fsub      %st(2), %st
        fsubrp    %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubrp    %st, %st(1)
        fstpt     36(%esp)
        fldt      24(%esp)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fldt      (%esp)
        fsubrp    %st, %st(1)
        fldt      36(%esp)
        fldt      24(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      136(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(2)
        fstpt     60(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmul      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      12(%esp)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     84(%esp)
        fldt      72(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fldt      60(%esp)
        fsubrp    %st, %st(1)
        fldt      84(%esp)
        fmulp     %st, %st(2)
        fldt      72(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      84(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      (%esp)
        fldt      60(%esp)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        fldt      (%esp)
        fsubrp    %st, %st(1)
        fstpt     120(%esp)
        fldt      (%esp)
        fldt      108(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(1)
        fsubrp    %st, %st(1)
        fldt      60(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     120(%esp)
        fldt      108(%esp)
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fsubrp    %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        fldt      120(%esp)
        fldt      48(%esp)
        faddp     %st, %st(1)
        fldt      96(%esp)
        addl      $-16, %esp
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      log1pl@PLT
..B1.45:
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     152(%esp)
..B1.18:
        testl     %edi, %edi
        je        ..B1.20
..B1.19:
        fldcw     134(%esp)
..B1.20:
        fldl      20(%ebp)
        addl      $-16, %esp
        fldl      12(%ebp)
        fstpl     8(%esp)
        fstpl     (%esp)
        call      atan2@PLT
        jmp       ..B1.47
..B1.21:
        je        ..B1.35
..B1.22:
        cmpl      $2047, %edx
        je        ..B1.28
..B1.23:
        fldl      _DBL_PINF@GOTOFF(%ebx)
        fstpl     152(%esp)
        addl      $-16, %esp
        fldl      20(%ebp)
        fldl      12(%ebp)
        fstpl     8(%esp)
        fstpl     (%esp)
        call      atan2@PLT
..B1.47:
        addl      $16, %esp
        fstpl     160(%esp)
..B1.24:
        movl      152(%esp), %edx
        movl      %edx, (%esi)
        movl      156(%esp), %ecx
        movl      160(%esp), %edi
        movl      164(%esp), %edx
        movl      %ecx, 4(%esi)
        movl      %edi, 8(%esi)
        movl      %edx, 12(%esi)
        movl      168(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B1.27
..B1.25:
        movl      %esi, %eax
        addl      $180, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B1.26:
        xorl      %edi, %edi
        jmp       ..B1.9
..B1.27:
        call      __stack_chk_fail@PLT
..B1.28:
        testl     $1048575, 24(%ebp)
        jne       ..B1.30
..B1.29:
        cmpl      $0, 20(%ebp)
        je        ..B1.23
..B1.30:
        fldl      20(%ebp)
        cmpl      $2047, %ecx
        fmul      %st(0), %st
        fstl      160(%esp)
        je        ..B1.32
..B1.31:
        fstpl     152(%esp)
        jmp       ..B1.24
..B1.32:
        testl     $1048575, 16(%ebp)
        jne       ..B1.31
..B1.33:
        cmpl      $0, 12(%ebp)
        jne       ..B1.31
..B1.34:
        fstp      %st(0)
        fldl      12(%ebp)
        fmul      %st(0), %st
        fstpl     152(%esp)
        jmp       ..B1.24
..B1.35:
        testl     $1048575, 16(%ebp)
        jne       ..B1.37
..B1.36:
        cmpl      $0, 12(%ebp)
        je        ..B1.22
..B1.37:
        fldl      12(%ebp)
        cmpl      $2047, %edx
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     160(%esp)
        je        ..B1.39
..B1.38:
        fldl      20(%ebp)
        fmulp     %st, %st(1)
        fstpl     152(%esp)
        jmp       ..B1.24
..B1.39:
        testl     $1048575, 24(%ebp)
        jne       ..B1.38
..B1.40:
        cmpl      $0, 20(%ebp)
        jne       ..B1.38
..B1.41:
        fstp      %st(0)
        fldl      20(%ebp)
        fmul      %st(0), %st
        fstpl     152(%esp)
        jmp       ..B1.24
..B1.48:
        cmpl      $2047, %ecx
        je        ..B1.35
        jmp       ..B1.22
        .align    16,0x90
	.type	__libm_internal_clog,@function
	.size	__libm_internal_clog,.-__libm_internal_clog
	.data
# -- End  __libm_internal_clog
	.text
# -- Begin  clog
	.text
       .align    16,0x90
	.globl clog
clog:
# parameter 1: 12 + %ebp
..B2.1:
..L3:

        pushl     %ebp
        movl      %esp, %ebp
        andl      $-64, %esp
        pushl     %esi
        pushl     %edi
        pushl     %ebx
        subl      $244, %esp
        fldz      
        fldl      12(%ebp)
        fldl      20(%ebp)
        movl      %gs:20, %eax
        xorl      %esp, %eax
        movl      %eax, 184(%esp)
        fstpl     176(%esp)
        fstpl     168(%esp)
        fstl      152(%esp)
        fstpl     160(%esp)
..B2.2:
        fnstcw    134(%esp)
..B2.3:
        movzwl    174(%esp), %edx
        andl      $32752, %edx
        movzwl    182(%esp), %eax
        andl      $32752, %eax
        shrl      $4, %edx
        shrl      $4, %eax
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        cmpl      $2047, %edx
        jge       ..B2.21
..B2.4:
        cmpl      $2047, %eax
        jge       ..B2.48
..B2.5:
        movzwl    134(%esp), %edx
        movl      %edx, %eax
        andl      $768, %eax
        cmpl      $768, %eax
        je        ..B2.26
..B2.6:
        orl       $-64768, %edx
        movw      %dx, 132(%esp)
..B2.7:
        fldcw     132(%esp)
..B2.8:
        movl      $1, %esi
..B2.9:
        fldl      168(%esp)
        fabs      
        fldl      176(%esp)
        fabs      
        fcom      %st(1)
        fnstsw    %ax
        sahf      
        jae       ..B2.11
        jp        ..B2.11
..B2.10:
        fld       %st(1)
        jmp       ..B2.12
..B2.11:
        fld       %st(0)
..B2.12:
        fxch      %st(1)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B2.14
..B2.13:
        fstp      %st(0)
        jmp       ..B2.15
..B2.14:
        fstp      %st(2)
..B2.15:
        fld       %st(0)
        fmul      %st(1), %st
        fld       %st(0)
        fld       %st(3)
        fmul      %st(4), %st
        fadd      %st, %st(1)
        fld1      
        fsubr     %st(2), %st
        fabs      
        fcompl    .L_2il0floatpacket.6@GOTOFF(%ebx)
        fnstsw    %ax
        sahf      
        jbe       ..B2.17
..B2.16:
        fstp      %st(3)
        fstp      %st(2)
        fstp      %st(2)
        fstp      %st(1)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.44:
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     152(%esp)
        jmp       ..B2.18
..B2.17:
        fstp      %st(1)
        fxch      %st(3)
        fstpt     136(%esp)
        fstpt     (%esp)
        fld       %st(0)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmulp     %st, %st(1)
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      12(%esp)
        fsub      %st(2), %st
        fsubrp    %st, %st(1)
        fstpt     24(%esp)
        fldt      24(%esp)
        fsubrp    %st, %st(1)
        fstpt     36(%esp)
        fldt      24(%esp)
        fldt      24(%esp)
        fmulp     %st, %st(1)
        fldt      (%esp)
        fsubrp    %st, %st(1)
        fldt      36(%esp)
        fldt      24(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      136(%esp)
        fldt      .L_2il0floatpacket.9@GOTOFF(%ebx)
        fmul      %st, %st(4)
        fxch      %st(3)
        fmulp     %st, %st(4)
        fxch      %st(3)
        faddp     %st, %st(4)
        faddp     %st, %st(3)
        fxch      %st(2)
        fstpt     48(%esp)
        fxch      %st(2)
        fstpt     60(%esp)
        fldt      .L_2il0floatpacket.8@GOTOFF(%ebx)
        fmul      %st(2), %st
        fstpt     12(%esp)
        fldt      12(%esp)
        fldt      12(%esp)
        fsub      %st(3), %st
        fsubrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      72(%esp)
        fsubrp    %st, %st(2)
        fxch      %st(1)
        fstpt     84(%esp)
        fldt      72(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fldt      60(%esp)
        fsubrp    %st, %st(1)
        fldt      84(%esp)
        fmulp     %st, %st(2)
        fldt      72(%esp)
        fmulp     %st, %st(2)
        faddp     %st, %st(1)
        fldt      84(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     96(%esp)
        fldt      (%esp)
        fldt      60(%esp)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        fldt      (%esp)
        fsubrp    %st, %st(1)
        fstpt     120(%esp)
        fldt      (%esp)
        fldt      108(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(1)
        fsubrp    %st, %st(1)
        fldt      60(%esp)
        fldt      120(%esp)
        fsubrp    %st, %st(1)
        faddp     %st, %st(1)
        fstpt     120(%esp)
        fldt      108(%esp)
        fldt      .L_2il0floatpacket.10@GOTOFF(%ebx)
        fsubrp    %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        fldt      120(%esp)
        fldt      48(%esp)
        faddp     %st, %st(1)
        fldt      96(%esp)
        addl      $-16, %esp
        faddp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      log1pl@PLT
..B2.45:
        fldt      .L_2il0floatpacket.7@GOTOFF(%ebx)
        addl      $16, %esp
        fmulp     %st, %st(1)
        fstpl     152(%esp)
..B2.18:
        testl     %esi, %esi
        je        ..B2.20
..B2.19:
        fldcw     134(%esp)
..B2.20:
        fldl      176(%esp)
        fldl      168(%esp)
        addl      $-16, %esp
        fstpl     8(%esp)
        fstpl     (%esp)
        call      atan2@PLT
        jmp       ..B2.47
..B2.21:
        je        ..B2.35
..B2.22:
        cmpl      $2047, %eax
        je        ..B2.28
..B2.23:
        fldl      _DBL_PINF@GOTOFF(%ebx)
        fstpl     152(%esp)
        fldl      176(%esp)
        fldl      168(%esp)
        addl      $-16, %esp
        fstpl     8(%esp)
        fstpl     (%esp)
        call      atan2@PLT
..B2.47:
        addl      $16, %esp
        fstpl     160(%esp)
..B2.24:
        fldl      152(%esp)
        movl      8(%ebp), %eax
        movl      156(%esp), %ecx
        movl      152(%esp), %edx
        movl      164(%esp), %edi
        movl      160(%esp), %esi
        fstp      %st(0)
        fldl      160(%esp)
        fstp      %st(0)
        movl      %edx, (%eax)
        movl      %ecx, 4(%eax)
        movl      %esi, 8(%eax)
        movl      %edi, 12(%eax)
        movl      184(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.27
..B2.25:
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.26:
        xorl      %esi, %esi
        jmp       ..B2.9
..B2.27:
        call      __stack_chk_fail@PLT
..B2.28:
        testl     $1048575, 180(%esp)
        jne       ..B2.30
..B2.29:
        cmpl      $0, 176(%esp)
        je        ..B2.23
..B2.30:
        fldl      176(%esp)
        cmpl      $2047, %edx
        fmul      %st(0), %st
        fstl      160(%esp)
        je        ..B2.32
..B2.31:
        fstpl     152(%esp)
        jmp       ..B2.24
..B2.32:
        testl     $1048575, 172(%esp)
        jne       ..B2.31
..B2.33:
        cmpl      $0, 168(%esp)
        jne       ..B2.31
..B2.34:
        fstp      %st(0)
        fldl      168(%esp)
        fmul      %st(0), %st
        fstpl     152(%esp)
        jmp       ..B2.24
..B2.35:
        testl     $1048575, 172(%esp)
        jne       ..B2.37
..B2.36:
        cmpl      $0, 168(%esp)
        je        ..B2.22
..B2.37:
        fldl      168(%esp)
        cmpl      $2047, %eax
        fld       %st(0)
        fmul      %st(1), %st
        fstpl     160(%esp)
        je        ..B2.39
..B2.38:
        fldl      176(%esp)
        fmulp     %st, %st(1)
        fstpl     152(%esp)
        jmp       ..B2.24
..B2.39:
        testl     $1048575, 180(%esp)
        jne       ..B2.38
..B2.40:
        cmpl      $0, 176(%esp)
        jne       ..B2.38
..B2.41:
        fstp      %st(0)
        fldl      176(%esp)
        fmul      %st(0), %st
        fstpl     152(%esp)
        jmp       ..B2.24
..B2.48:
        cmpl      $2047, %edx
        je        ..B2.35
        jmp       ..B2.22
        .align    16,0x90
	.type	clog,@function
	.size	clog,.-clog
	.data
# -- End  clog
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.7:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.7,@object
	.size	.L_2il0floatpacket.7,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.8:
	.byte	0x00,0x00,0x00,0x40,0x00,0x00,0x00,0x80,0x20,0x40,0x00,0x00
	.type	.L_2il0floatpacket.8,@object
	.size	.L_2il0floatpacket.8,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.9:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0x00,0x40,0x00,0x00
	.type	.L_2il0floatpacket.9,@object
	.size	.L_2il0floatpacket.9,12
	.space 4, 0x00 	# pad
	.align 16
.L_2il0floatpacket.10:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xff,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.10,@object
	.size	.L_2il0floatpacket.10,12
	.space 4, 0x00 	# pad
	.align 16
_DBL_PINF:
	.long	0
	.long	2146435072
	.type	_DBL_PINF,@object
	.size	_DBL_PINF,8
	.align 8
.L_2il0floatpacket.6:
	.long	0xeb851eb8,0x3f9eb851
	.type	.L_2il0floatpacket.6,@object
	.size	.L_2il0floatpacket.6,8
	.data
	.section .note.GNU-stack, ""
# End
