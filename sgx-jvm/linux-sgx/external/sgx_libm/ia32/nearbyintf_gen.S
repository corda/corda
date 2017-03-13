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
	.file "nearbyintf_gen.c"
	.text
..TXTST0:
# -- Begin  nearbyintf
	.text
       .align    16,0x90
	.globl nearbyintf
nearbyintf:
# parameter 1: 32 + %esp
..B1.1:
..L1:

        pushl     %edi
        pushl     %ebx
        pushl     %ebp
        subl      $16, %esp
..B1.2:
        fnstcw    4(%esp)
..B1.3:
        movzwl    34(%esp), %eax
        andl      $32640, %eax
        shrl      $7, %eax
        cmpl      $150, %eax
        jge       ..B1.40
..B1.4:
        movzwl    4(%esp), %ebx
        andl      $3072, %ebx
        call      ..L2
..L2:
        popl      %edx
        lea       _GLOBAL_OFFSET_TABLE_+[. - ..L2](%edx), %edx
        cmpl      $1024, %ebx
        je        ..B1.31
..B1.5:
        cmpl      $2048, %ebx
        jne       ..B1.15
..B1.6:
        cmpl      $127, %eax
        jl        ..B1.11
..B1.7:
        negl      %eax
        movl      $8388607, %edx
        addl      $22, %eax
        movl      32(%esp), %ebx
        movl      %ebx, %ebp
        movl      %eax, %ecx
        andl      $8388607, %ebp
        shll      %cl, %edx
        andl      $-8388608, %ebx
        andl      %ebp, %edx
        orl       %edx, %ebx
        movl      %ebx, %eax
        andl      $8388607, %eax
        movl      %ebx, 32(%esp)
        testl     $-2147483648, %ebx
        jne       ..B1.10
..B1.8:
        cmpl      %ebp, %eax
        je        ..B1.10
..B1.9:
        fld1      
        fadds     32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.10:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.11:
        testl     %eax, %eax
        jne       ..B1.14
..B1.12:
        testl     $8388607, 32(%esp)
        jne       ..B1.14
..B1.13:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.14:
        movzbl    35(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        movl      32(%esp), %eax
        movl      %eax, (%esp)
        flds      _pone_nzero@GOTOFF(%edx,%ecx,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.15:
        cmpl      $3072, %ebx
        jne       ..B1.20
..B1.16:
        cmpl      $127, %eax
        jl        ..B1.18
..B1.17:
        negl      %eax
        movl      $8388607, %edx
        addl      $22, %eax
        movl      %eax, %ecx
        shll      %cl, %edx
        movl      32(%esp), %ebx
        movl      %ebx, %ebp
        andl      %edx, %ebx
        andl      $-8388608, %ebp
        andl      $8388607, %ebx
        orl       %ebx, %ebp
        movl      %ebp, 32(%esp)
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.18:
        movzbl    35(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        movl      32(%esp), %eax
        movl      %eax, (%esp)
        flds      _zeros@GOTOFF(%edx,%ecx,4)
..B1.19:
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.20:
        cmpl      $127, %eax
        jl        ..B1.27
..B1.21:
        movl      32(%esp), %edi
        movl      %edi, %ebp
        andl      $8388607, %ebp
        lea       10(%eax), %ecx
        negl      %eax
        movl      %ebp, %ebx
        addl      $22, %eax
        andl      $-8388608, %edi
        shll      %cl, %ebx
        movl      %eax, %ecx
        movl      $8388607, %eax
        shll      %cl, %eax
        andl      %eax, %ebp
        orl       %ebp, %edi
        movl      %edi, 32(%esp)
        testl     %ebx, %ebx
        jge       ..B1.26
..B1.22:
        cmpl      $-2147483648, %ebx
        jne       ..B1.25
..B1.23:
        movzwl    34(%esp), %ecx
        movl      $1, %eax
        shrl      $7, %ecx
        negl      %ecx
        addl      $22, %ecx
        shll      %cl, %eax
        testl     %eax, 32(%esp)
        je        ..B1.26
..B1.24:
        flds      32(%esp)
        movzbl    35(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fadds     _ones@GOTOFF(%edx,%eax,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.25:
        flds      32(%esp)
        movzbl    35(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        fadds     _ones@GOTOFF(%edx,%eax,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.26:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.27:
        cmpl      $126, %eax
        jle       ..B1.29
..B1.28:
        movzbl    35(%esp), %eax
        andl      $128, %eax
        shrl      $7, %eax
        flds      _ones@GOTOFF(%edx,%eax,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.29:
        je        ..B1.41
..B1.30:
        movzbl    35(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        movl      32(%esp), %eax
        movl      %eax, (%esp)
        flds      _zeros@GOTOFF(%edx,%ecx,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.31:
        cmpl      $127, %eax
        jl        ..B1.36
..B1.32:
        negl      %eax
        movl      $8388607, %edi
        addl      $22, %eax
        movl      32(%esp), %ebx
        movl      %ebx, %ebp
        movl      %eax, %ecx
        andl      $8388607, %ebp
        shll      %cl, %edi
        andl      $-8388608, %ebx
        andl      %ebp, %edi
        orl       %edi, %ebx
        movl      %ebx, %eax
        andl      $8388607, %eax
        movl      %ebx, 32(%esp)
        testl     $-2147483648, %ebx
        je        ..B1.35
..B1.33:
        cmpl      %ebp, %eax
        je        ..B1.35
..B1.34:
        flds      32(%esp)
        fadds     .L_2il0floatpacket.0@GOTOFF(%edx)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.35:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.36:
        testl     %eax, %eax
        jne       ..B1.39
..B1.37:
        testl     $8388607, 32(%esp)
        jne       ..B1.39
..B1.38:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.39:
        movzbl    35(%esp), %ecx
        andl      $128, %ecx
        shrl      $7, %ecx
        movl      32(%esp), %eax
        movl      %eax, (%esp)
        flds      _pzero_none@GOTOFF(%edx,%ecx,4)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.40:
        flds      32(%esp)
        addl      $16, %esp
        popl      %ebp
        popl      %ebx
        popl      %edi
        ret       
..B1.41:
        testl     $8388607, 32(%esp)
        jg        ..B1.28
        jmp       ..B1.30
        .align    16,0x90
	.type	nearbyintf,@function
	.size	nearbyintf,.-nearbyintf
	.data
# -- End  nearbyintf
	.section .rodata, "a"
	.align 4
	.align 4
.L_2il0floatpacket.0:
	.long	0xbf800000
	.type	.L_2il0floatpacket.0,@object
	.size	.L_2il0floatpacket.0,4
	.align 4
_pone_nzero:
	.long	1065353216
	.long	2147483648
	.type	_pone_nzero,@object
	.size	_pone_nzero,8
	.align 4
_zeros:
	.long	0
	.long	2147483648
	.type	_zeros,@object
	.size	_zeros,8
	.align 4
_ones:
	.long	1065353216
	.long	3212836864
	.type	_ones,@object
	.size	_ones,8
	.align 4
_pzero_none:
	.long	0
	.long	3212836864
	.type	_pzero_none,@object
	.size	_pzero_none,8
	.data
	.section .note.GNU-stack, ""
# End
