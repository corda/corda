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

#ifndef _THREAD_DATA_H_
#define _THREAD_DATA_H_

#include "se_types.h"
#include "se_cdefs.h"

#ifdef TD_SUPPORT_MULTI_PLATFORM
/* To enable the SignTool to sign both 32/64-bit Enclave for ELF,
 * we need to make the struct `thread_data_t' have a consistent
 * definition for 32/64-bit compiler.
 *
 * We achieve it by forcing the compiler to check pre-defined macros
 *   `RTS_SYSTEM_WORDSIZE'
 *
 * |--------------------------+-------|
 * | RTS_SYSTEM_WORDSIZE = 32 | ELF32 |
 * |--------------------------+-------|
 * | RTS_SYSTEM_WORDSIZE = 64 | ELF64 |
 *
 */
#  ifndef RTS_SYSTEM_WORDSIZE
#    error RTS_SYSTEM_WORDSIZE should be pre-defined.
#  endif

/* Avoid to use `uintptr_t' in the struct `thread_data_t' and its members. */
#  if RTS_SYSTEM_WORDSIZE == 32
typedef uint32_t sys_word_t;
#  elif RTS_SYSTEM_WORDSIZE == 64
typedef uint64_t sys_word_t;
#  else
#    error Invalid value for 'RTS_SYSTEM_WORDSIZE'.
#  endif

#else

/* For uRTS, there is no need to define the macro 'TD_SUPPORT_MULTI_PLATFORM' */
typedef size_t sys_word_t;

/* SE_32 and SE_64 are defined in "se_cdefs.h" */
#  ifdef SE_32
#    define RTS_SYSTEM_WORDSIZE 32
#  elif defined(SE_64)
#    define RTS_SYSTEM_WORDSIZE 64
#  else
#    error Unknown system word size.
#  endif

#endif /* ! TD_SUPPORT_MULTI_PLATFORM */

/* The data structure currently is naturally aligned regardless of the value of
 * RTS_SYSTEM_WORDSIZE.
 *
 * However, we need to take care when modifying the data structure in future.
 */

typedef struct _thread_data_t
{
    sys_word_t  self_addr;
    sys_word_t  last_sp;            /* set by urts, relative to TCS */
    sys_word_t  stack_base_addr;    /* set by urts, relative to TCS */
    sys_word_t  stack_limit_addr;   /* set by urts, relative to TCS */
    sys_word_t  first_ssa_gpr;      /* set by urts, relative to TCS */
    sys_word_t  stack_guard;        /* GCC expects start_guard at 0x14 on x86 and 0x28 on x64 */

    sys_word_t  reserved;
    sys_word_t  xsave_size;         /* in bytes (se_ptrace.c needs to know its offset).*/
    sys_word_t  last_error;         /* init to be 0. Used by trts. */

#ifdef TD_SUPPORT_MULTI_PLATFORM
    sys_word_t  m_next;             /* next TD used by trusted thread library (of type "struct _thread_data *") */
#else
    struct _thread_data_t *m_next;
#endif
    sys_word_t  tls_addr;           /* points to TLS pages */
    sys_word_t  tls_array;          /* points to TD.tls_addr relative to TCS */
#ifdef TD_SUPPORT_MULTI_PLATFORM
    sys_word_t  exception_flag;     /* mark how many exceptions are being handled */
#else
    intptr_t    exception_flag;
#endif
    sys_word_t  cxx_thread_info[6];
    sys_word_t  stack_commit_addr;
} thread_data_t;

#ifdef __cplusplus
extern "C" {
#endif

thread_data_t *get_thread_data(void);

#ifdef __cplusplus
}
#endif

#endif
