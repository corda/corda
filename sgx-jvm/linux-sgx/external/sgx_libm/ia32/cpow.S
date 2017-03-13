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
	.file "cpow.c"
	.text
..TXTST0:
# -- Begin  cpowf
	.text
       .align    16,0x90
	.globl cpowf
cpowf:
# parameter 1: 80 + %esp
# parameter 2: 88 + %esp
..B1.1:
..L1:

        pushl     %ebx
        subl      $72, %esp
        flds      80(%esp)
        flds      84(%esp)
        flds      88(%esp)
        flds      92(%esp)
        movl      %gs:20, %ecx
        xorl      %esp, %ecx
        movl      %ecx, 56(%esp)
        call      ..L2
..L2:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%ebx), %ebx
        fstpl     48(%esp)
        fxch      %st(2)
        fstpl     24(%esp)
        fstpl     32(%esp)
        fstpl     40(%esp)
        addl      $-48, %esp
        movl      72(%esp), %ecx
        lea       56(%esp), %eax
        movl      %eax, (%esp)
        lea       4(%esp), %edx
        movl      20(%eax), %eax
        movl      %ecx, (%edx)
        movl      %eax, 4(%edx)
        movl      80(%esp), %ecx
        movl      84(%esp), %eax
        movl      %ecx, 8(%edx)
        movl      %eax, 12(%edx)
        lea       20(%esp), %eax
        movl      88(%esp), %edx
        movl      %edx, (%eax)
        movl      92(%esp), %edx
        movl      %edx, 4(%eax)
        movl      96(%esp), %ecx
        movl      100(%esp), %edx
        movl      %ecx, 8(%eax)
        movl      %edx, 12(%eax)
        call      cpow@PLT
..B1.7:
        addl      $44, %esp
..B1.2:
        fldl      8(%esp)
        fstps     (%esp)
        fldl      16(%esp)
        fstps     4(%esp)
        movl      56(%esp), %ecx
        xorl      %esp, %ecx
        movl      (%esp), %eax
        movl      4(%esp), %edx
        cmpl      %gs:20, %ecx
        jne       ..B1.4
..B1.3:
        addl      $72, %esp
        popl      %ebx
        ret       
..B1.4:
        call      __stack_chk_fail@PLT
        .align    16,0x90
	.type	cpowf,@function
	.size	cpowf,.-cpowf
	.data
# -- End  cpowf
	.text
# -- Begin  cpow
	.text
       .align    16,0x90
	.globl cpow
cpow:
# parameter 1: 12 + %ebp
# parameter 2: 28 + %ebp
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
        movl      %gs:20, %edx
        movl      8(%ebp), %edi
        xorl      %esp, %edx
        movl      %edx, 224(%esp)
        fstl      208(%esp)
        fstpl     216(%esp)
..B2.2:
        fnstcw    206(%esp)
..B2.3:
        movzwl    206(%esp), %ecx
        movl      %ecx, %edx
        andl      $768, %edx
        cmpl      $768, %edx
        je        ..B2.38
..B2.4:
        orl       $-64768, %ecx
        movw      %cx, 204(%esp)
..B2.5:
        fldcw     204(%esp)
..B2.6:
        movl      $1, %esi
..B2.7:
        fldl      12(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnzfinite
..B2.41:
        addl      $16, %esp
..B2.8:
        call      ..L4
..L4:
        popl      %ebx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L4](%ebx), %ebx
        testl     %eax, %eax
        jne       ..B2.31
..B2.9:
        fldl      20(%ebp)
        fldl      ZEROA@GOTOFF(%ebx)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.10
        je        ..B2.27
..B2.10:
        fldl      28(%ebp)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.11
        je        ..B2.25
..B2.11:
        fxch      %st(2)
        fucom     %st(1)
        fnstsw    %ax
        sahf      
        jp        ..B2.12
        je        ..B2.20
..B2.12:
        fstp      %st(2)
        fldl      36(%ebp)
        fucompp   
        fnstsw    %ax
        sahf      
        jp        ..B2.13
        je        ..B2.19
..B2.13:
        fldl      12(%ebp)
        fstpt     36(%esp)
        fstpt     48(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      48(%esp)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.49:
        addl      $16, %esp
        fstpt     120(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B2.48:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $32, %esp
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(1)
        fstpt     60(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldl      28(%ebp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      expl@PLT
..B2.47:
        fldt      _LD_PI_DIV_180@GOTOFF(%ebx)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      expl@PLT
..B2.46:
        fldl      36(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     168(%esp)
        fldt      156(%esp)
        fldt      168(%esp)
        fdivrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      cosl@PLT
..B2.45:
        addl      $16, %esp
        fstpt     132(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      cosdl@PLT
..B2.44:
        addl      $16, %esp
        fstpt     84(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldt      16(%esp)
        fmull     36(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      sinl@PLT
..B2.43:
        addl      $16, %esp
        fstpt     144(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      sindl@PLT
..B2.42:
        addl      $16, %esp
        fstpt     96(%esp)
        fldt      132(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      144(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     180(%esp)
        fldt      144(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      96(%esp)
        fldt      132(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     192(%esp)
        fldt      180(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      192(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
..B2.14:
        testl     %esi, %esi
        je        ..B2.16
..B2.15:
        fldcw     206(%esp)
..B2.16:
        movl      208(%esp), %edx
        movl      %edx, (%edi)
        movl      212(%esp), %ecx
        movl      216(%esp), %esi
        movl      220(%esp), %edx
        movl      %ecx, 4(%edi)
        movl      %esi, 8(%edi)
        movl      %edx, 12(%edi)
        movl      224(%esp), %edx
        xorl      %esp, %edx
        cmpl      %gs:20, %edx
        jne       ..B2.18
..B2.17:
        movl      %edi, %eax
        addl      $244, %esp
        popl      %ebx
        popl      %edi
        popl      %esi
        movl      %ebp, %esp
        popl      %ebp
        ret       $4
..B2.18:
        call      __stack_chk_fail@PLT
..B2.19:
        fldl      12(%ebp)
        fstpt     36(%esp)
        fstpt     48(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      48(%esp)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        addl      $-32, %esp
        fstpt     (%esp)
        fldl      .L_2il0floatpacket.0@GOTOFF(%ebx)
        fmull     28(%ebp)
        fstpt     12(%esp)
        call      powl@PLT
..B2.53:
        addl      $32, %esp
        fstpt     72(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B2.52:
        addl      $32, %esp
        fstpt     60(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      cosdl@PLT
..B2.51:
        addl      $16, %esp
        fstpt     132(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      sindl@PLT
..B2.50:
        addl      $16, %esp
        fstpt     144(%esp)
        fldt      72(%esp)
        fldt      132(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      72(%esp)
        fldt      144(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.20:
        fldl      12(%ebp)
        fcom      %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B2.22
..B2.21:
        fstp      %st(1)
        fstp      %st(1)
        addl      $-32, %esp
        fstpt     (%esp)
        fstpt     12(%esp)
        call      powl@PLT
..B2.57:
        fldl      12(%ebp)
        addl      $32, %esp
        fxch      %st(1)
        fstpt     72(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.56:
        addl      $16, %esp
        fstpt     24(%esp)
        fldt      24(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fstpt     (%esp)
        call      cosl@PLT
..B2.55:
        addl      $16, %esp
        fstpt     132(%esp)
        fldt      24(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.54:
        addl      $16, %esp
        fstpt     144(%esp)
        fldt      132(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      144(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.22:
        fstp      %st(3)
        fxch      %st(1)
        fcomp     %st(2)
        fnstsw    %ax
        sahf      
        jbe       ..B2.24
..B2.23:
        fxch      %st(1)
        fstpt     36(%esp)
        fldt      36(%esp)
        fchs      
        fstpt     12(%esp)
        fstpt     48(%esp)
        fldt      12(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.65:
        addl      $16, %esp
        fstpt     120(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B2.64:
        addl      $32, %esp
        fstpt     60(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      expl@PLT
..B2.63:
        fldt      _LD_PI_DIV_180@GOTOFF(%ebx)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      expl@PLT
..B2.62:
        addl      $16, %esp
        fstpt     168(%esp)
        fldt      156(%esp)
        fldt      168(%esp)
        fdivrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fstpt     (%esp)
        call      cosl@PLT
..B2.61:
        addl      $16, %esp
        fstpt     132(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      cosdl@PLT
..B2.60:
        addl      $16, %esp
        fstpt     84(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fstpt     (%esp)
        call      sinl@PLT
..B2.59:
        addl      $16, %esp
        fstpt     144(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      sindl@PLT
..B2.58:
        addl      $16, %esp
        fstpt     96(%esp)
        fldt      132(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      144(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     180(%esp)
        fldt      144(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      96(%esp)
        fldt      132(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     192(%esp)
        fldt      180(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      192(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.24:
        fxch      %st(1)
        fstpl     208(%esp)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.25:
        fldl      36(%ebp)
        fucomp    %st(2)
        fnstsw    %ax
        sahf      
        jne       ..B2.11
        jp        ..B2.11
..B2.26:
        fstp      %st(0)
        fstp      %st(0)
        fldl      12(%ebp)
        fstpt     36(%esp)
        fstpt     48(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      48(%esp)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.69:
        addl      $16, %esp
        fstpt     120(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2l@PLT
..B2.68:
        addl      $32, %esp
        fstpt     60(%esp)
        fldt      120(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        faddp     %st, %st(1)
        fstpt     (%esp)
        call      sinl@PLT
..B2.67:
        fldl      ZEROA@GOTOFF(%ebx)
        fld1      
        addl      $16, %esp
        fstpl     208(%esp)
        addl      $-16, %esp
        fstpl     (%esp)
        fstpl     8(%esp)
        call      copysign@PLT
..B2.66:
        addl      $16, %esp
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.27:
        fldl      36(%ebp)
        fucomp    %st(1)
        fnstsw    %ax
        sahf      
        jne       ..B2.10
        jp        ..B2.10
..B2.28:
        fldl      12(%ebp)
        fxch      %st(1)
        fcomp     %st(1)
        fnstsw    %ax
        sahf      
        jbe       ..B2.30
..B2.29:
        fstpt     36(%esp)
        fldt      36(%esp)
        fchs      
        fstpt     12(%esp)
        fstpt     48(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B2.73:
        fldl      28(%ebp)
        addl      $32, %esp
        fxch      %st(1)
        fstpt     60(%esp)
        fldt      12(%esp)
        addl      $-32, %esp
        fstpt     (%esp)
        fstpt     12(%esp)
        call      powl@PLT
..B2.72:
        addl      $32, %esp
        fstpt     72(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      cosdl@PLT
..B2.71:
        addl      $16, %esp
        fstpt     84(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      sindl@PLT
..B2.70:
        addl      $16, %esp
        fstpt     96(%esp)
        fldt      72(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      72(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.30:
        fldl      28(%ebp)
        addl      $-16, %esp
        fstpl     8(%esp)
        fstpl     (%esp)
        fstpl     16(%esp)
        call      pow@PLT
..B2.74:
        fldl      16(%esp)
        addl      $16, %esp
        fstpl     216(%esp)
        fstpl     208(%esp)
        jmp       ..B2.14
..B2.31:
        fldl      20(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnzfinite
..B2.75:
        addl      $16, %esp
..B2.32:
        testl     %eax, %eax
        je        ..B2.9
..B2.33:
        fldl      28(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnzfinite
..B2.76:
        addl      $16, %esp
..B2.34:
        testl     %eax, %eax
        je        ..B2.9
..B2.35:
        fldl      36(%ebp)
        addl      $-16, %esp
        fstpl     (%esp)
        call      __libm_internal_isnzfinite
..B2.77:
        addl      $16, %esp
..B2.36:
        testl     %eax, %eax
        je        ..B2.9
..B2.37:
        fldl      12(%ebp)
        fstpt     36(%esp)
        fldl      20(%ebp)
        fstpt     48(%esp)
        fldt      36(%esp)
        fldt      36(%esp)
        fmulp     %st, %st(1)
        fldt      48(%esp)
        fldt      48(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     108(%esp)
        fldt      108(%esp)
        addl      $-16, %esp
        fstpt     (%esp)
        call      logl@PLT
..B2.85:
        addl      $16, %esp
        fstpt     120(%esp)
        fldt      48(%esp)
        fldt      36(%esp)
        addl      $-32, %esp
        fxch      %st(1)
        fstpt     (%esp)
        fstpt     12(%esp)
        call      atan2dl@PLT
..B2.84:
        fldt      .L_2il0floatpacket.1@GOTOFF(%ebx)
        addl      $32, %esp
        fstpt     (%esp)
        fldt      (%esp)
        fxch      %st(1)
        fstpt     60(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldl      28(%ebp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      expl@PLT
..B2.83:
        fldt      _LD_PI_DIV_180@GOTOFF(%ebx)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     156(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     36(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      expl@PLT
..B2.82:
        fldl      36(%ebp)
        addl      $16, %esp
        fxch      %st(1)
        fstpt     168(%esp)
        fldt      156(%esp)
        fldt      168(%esp)
        fdivrp    %st, %st(1)
        fstpt     72(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldt      16(%esp)
        fmulp     %st, %st(2)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      cosl@PLT
..B2.81:
        addl      $16, %esp
        fstpt     132(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      cosdl@PLT
..B2.80:
        addl      $16, %esp
        fstpt     84(%esp)
        fldt      120(%esp)
        addl      $-16, %esp
        fldt      16(%esp)
        fmull     36(%ebp)
        fmulp     %st, %st(1)
        fstpt     (%esp)
        call      sinl@PLT
..B2.79:
        addl      $16, %esp
        fstpt     144(%esp)
        fldt      60(%esp)
        addl      $-16, %esp
        fmull     28(%ebp)
        fstpt     (%esp)
        call      sindl@PLT
..B2.78:
        addl      $16, %esp
        fstpt     96(%esp)
        fldt      132(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      144(%esp)
        fldt      96(%esp)
        fmulp     %st, %st(1)
        fsubrp    %st, %st(1)
        fstpt     180(%esp)
        fldt      144(%esp)
        fldt      84(%esp)
        fmulp     %st, %st(1)
        fldt      96(%esp)
        fldt      132(%esp)
        fmulp     %st, %st(1)
        faddp     %st, %st(1)
        fstpt     192(%esp)
        fldt      180(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     208(%esp)
        fldt      192(%esp)
        fldt      72(%esp)
        fmulp     %st, %st(1)
        fstpl     216(%esp)
        jmp       ..B2.14
..B2.38:
        xorl      %esi, %esi
        jmp       ..B2.7
        .align    16,0x90
	.type	cpow,@function
	.size	cpow,.-cpow
	.data
# -- End  cpow
	.section .rodata, "a"
	.align 16
	.align 16
.L_2il0floatpacket.1:
	.byte	0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x80,0xfe,0x3f,0x00,0x00
	.type	.L_2il0floatpacket.1,@object
	.size	.L_2il0floatpacket.1,12
	.space 4, 0x00 	# pad
	.align 16
ZEROA:
	.long	0
	.long	0
	.type	ZEROA,@object
	.size	ZEROA,8
	.space 8, 0x00 	# pad
	.align 16
_LD_PI_DIV_180:
	.word	51374
	.word	38121
	.word	13586
	.word	36602
	.word	16377
	.word	0
	.type	_LD_PI_DIV_180,@object
	.size	_LD_PI_DIV_180,12
	.space 4, 0x00 	# pad
	.align 8
.L_2il0floatpacket.0:
	.long	0x00000000,0x3fe00000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,8
	.data
	.hidden __libm_internal_isnzfinite
	.section .note.GNU-stack, ""
# End
