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


#include "linux/linux-regs.h"

    .file "lowlib.S"

    .text

DECLARE_LOCAL_FUNC get_bp
    mov %xbp, %xax
    ret

#define reg_xax (0 * SE_WORDSIZE)
#define reg_xbx (1 * SE_WORDSIZE)
#define reg_xcx (2 * SE_WORDSIZE)
#define reg_xdx (3 * SE_WORDSIZE)
#define reg_xsi (4 * SE_WORDSIZE)
#define reg_xdi (5 * SE_WORDSIZE)
#define reg_xbp (6 * SE_WORDSIZE)
#define reg_xsp (7 * SE_WORDSIZE)
#define reg_xip (8 * SE_WORDSIZE)

DECLARE_LOCAL_FUNC load_regs
    mov	naked_arg0, %xdx
    mov	reg_xax(%xdx), %xax
    mov	reg_xbx(%xdx), %xbx
    mov	reg_xcx(%xdx), %xcx
    mov	reg_xsi(%xdx), %xsi
    mov	reg_xdi(%xdx), %xdi
    mov	reg_xbp(%xdx), %xbp
    mov	reg_xsp(%xdx), %xsp
    mov	reg_xax(%xdx), %xax
    push reg_xip(%xdx)
    mov reg_xdx(%xdx), %xdx
    ret
