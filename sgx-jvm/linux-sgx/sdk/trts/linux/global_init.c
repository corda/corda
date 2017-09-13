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


#include "internal/global_init.h"
#include "linux/elf_parser.h"
#include "global_data.h"
#include "internal/util.h"
#include "thread_data.h"
#include "sgx_trts.h"
#include <assert.h>
#include <stdlib.h>

typedef void (*fp_t)(void);

/* required by global constructor when -fuse-cxa-atexit is enabled */
void *__dso_handle __attribute__((weak)) = &(__dso_handle);

int __cxa_atexit(void (*fun)(void *), void *para, void *dso)
{
    (void)(fun);
    (void)(para);
    (void)(dso);

    return 0;
}

/* auxiliary routines */
static void do_ctors_aux(void)
{
    /* SGX RTS does not support .ctors currently */
   
    fp_t *p = NULL;
    uintptr_t init_array_addr = 0;
    size_t init_array_size = 0;
    const void *enclave_start = (const void*)&__ImageBase;

    if (0 != elf_get_init_array(enclave_start, &init_array_addr, &init_array_size)|| init_array_addr == 0 || init_array_size == 0)
        return;

    fp_t *fp_start = (fp_t*)(init_array_addr + (uintptr_t)(enclave_start));
    fp_t *fp_end = fp_start + (init_array_size / sizeof(fp_t));
    
    /* traverse .init_array in forward order */
    for (p = fp_start; p < fp_end; p++)
    {
        (*p)();
    }
}

void init_global_object(void)
{
    do_ctors_aux();
}

