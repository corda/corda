/*
 * Copyright (C) 2011-2017 Intel Corporation. All rights reserved.
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
 */


.file "xsave_gnu.S"

#include "linux/linux-regs.h"

.macro DO_XSAVEC
#if defined(LINUX64)
    .byte   0x48, 0x0f, 0xc7, 0x21
#else
    .byte   0x0f, 0xc7, 0x21
#endif
.endm
.macro DO_XRSTOR
#if defined(LINUX64)
    .byte   0x48, 0x0f, 0xae, 0x29
#else
    .byte   0x0f, 0xae, 0x29
#endif
.endm
.macro DO_FXSAVE
#if defined(LINUX64)
    .byte   0x48, 0x0f, 0xae, 0x01
#else
    .byte   0x0f, 0xae, 0x01
#endif
.endm
.macro DO_FXRSTOR
#if defined(LINUX64)
    .byte   0x48, 0x0f, 0xae, 0x09
#else
    .byte   0x0f, 0xae, 0x09
#endif
.endm

.text
DECLARE_LOCAL_FUNC restore_xregs
#if defined(LINUX32)
    mov     SE_WORDSIZE(%esp), %ecx
    lea     g_xsave_enabled, %eax
#else
    mov     %rdi, %rcx
    mov     g_xsave_enabled@GOTPCREL(%rip), %rax
#endif
    movl    (%xax), %eax
    cmpl    $0, %eax
    jz      1f
    xor     %xax, %xax
    xor     %xdx, %xdx
    mov     $0xFFFFFFFF, %eax
    mov     $0xFFFFFFFF, %edx
    DO_XRSTOR
    jmp     2f
1:
    DO_FXRSTOR
2:
    ret

DECLARE_LOCAL_FUNC save_xregs
#if defined(LINUX32)
    mov     SE_WORDSIZE(%esp), %ecx
    lea     g_xsave_enabled, %eax
#else
    mov     %rdi, %rcx
    mov     g_xsave_enabled@GOTPCREL(%rip), %rax
#endif
    fwait
    movl    (%xax), %eax
    cmpl    $0, %eax
    jz      1f
    xor     %xax, %xax
    xor     %xdx, %xdx
    mov     $0xFFFFFFFF, %eax
    mov     $0xFFFFFFFF, %edx
    DO_XSAVEC
    jmp     2f
1:
    DO_FXSAVE
2:
    ret

