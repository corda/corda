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

#ifndef _SE_PAGE_ATTR_H_
#define _SE_PAGE_ATTR_H_

// layout.entry.attribute is 16-bit length
typedef enum
{
    DoEADD = 0,
    DoEEXTEND,
    DoEREMOVE,
    DoPostADD,
    DoPostREMOVE,
    DynTHREAD,
    GrowDOWN,
} ATTRIBUTE_BITS_t;

#define PAGE_ATTR_EADD         (1<<DoEADD)
#define PAGE_ATTR_EEXTEND      (1<<DoEEXTEND)
#define PAGE_ATTR_EREMOVE      (1<<DoEREMOVE)
#define PAGE_ATTR_POST_ADD     (1<<DoPostADD)
#define PAGE_ATTR_POST_REMOVE  (1<<DoPostREMOVE)
#define PAGE_ATTR_DYN_THREAD   (1<<DynTHREAD)
#define PAGE_DIR_GROW_DOWN     (1<<GrowDOWN)
#define ADD_PAGE_ONLY           PAGE_ATTR_EADD
#define ADD_EXTEND_PAGE        (PAGE_ATTR_EADD | PAGE_ATTR_EEXTEND)
#define PAGE_ATTR_MASK         ~(PAGE_ATTR_EADD | PAGE_ATTR_EEXTEND | PAGE_ATTR_EREMOVE | PAGE_ATTR_POST_ADD | PAGE_ATTR_POST_REMOVE | PAGE_ATTR_DYN_THREAD | PAGE_DIR_GROW_DOWN)



#endif
