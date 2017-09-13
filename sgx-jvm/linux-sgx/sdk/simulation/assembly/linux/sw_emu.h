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


/**
 * File: sw_emu.h
 * Description:
 *   This file defined macros which hide hardware differences.
 */

#ifndef _SW_EMU_H_
#define _SW_EMU_H_

#include "linux/linux-regs.h"

/*******************************************************************/

#define SE0func   _SE0
.type SE0func, @function
.protected SE0func

#if defined LINUX64

.macro SE0_SW   leafcode
    movabsq $\leafcode, %rax

    pushq   %rax
    pushq   %rbx
    pushq   %rcx
    pushq   %rdx
    pushq   %rsi
    pushq   %rdi

    /* 64bit ABI: parameter passing with registers */
    popq    %r9
    popq    %r8
    popq    %rcx
    popq    %rdx
    popq    %rsi
    popq    %rdi

    call    SE0func
.endm

#elif defined LINUX32

.macro SE0_SW   leafcode
    movl   $\leafcode, %eax

    pushl  %edi
    pushl  %esi
    pushl  %edx
    pushl  %ecx
    pushl  %ebx
    pushl  %eax

    call   SE0func
    add    $(SE_WORDSIZE * 6), %esp
.endm

#endif

/*******************************************************************/

.macro ECREATE_SW
    SE0_SW SE_ECREATE
.endm

.macro EADD_SW
    SE0_SW SE_EADD
.endm

.macro EINIT_SW
    SE0_SW SE_EINIT
.endm

.macro EREMOVE_SW
    SE0_SW SE_EREMOVE
.endm

/*******************************************************************/

/* This macro is used to generate simulation functions like:
 * DoECREATE_SW, DoEADD_SW, ...
*/
.macro DoSW inst
DECLARE_LOCAL_FUNC Do\()\inst\()_SW
    SE_PROLOG
    \inst\()_SW
    SE_EPILOG
.endm

#endif
