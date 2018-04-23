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
#include "sgx_spinlock.h"
#include "global_data.h"
#include "internal/util.h"
#include "thread_data.h"
#include "sgx_trts.h"
#include <assert.h>
#include <stdlib.h>

typedef void (*cxa_function_t)(void *para);

typedef struct _exit_function_t
{
    struct
    {
        uintptr_t fun;
        uintptr_t para;
        void *dso_handle;
    } cxa;

    struct _exit_function_t *next;
} exit_function_t;

static exit_function_t *g_exit_function = NULL;
static sgx_spinlock_t g_exit_function_lock = SGX_SPINLOCK_INITIALIZER;

static uintptr_t g_exit_function_cookie = 0;
#define ENC_CXA_FUNC_POINTER(x)  (uintptr_t)(x) ^ g_exit_function_cookie
#define DEC_CXA_FUNC_POINTER(x)  (cxa_function_t)((x) ^ g_exit_function_cookie)
#define ENC_CXA_PARA_POINTER(x)  (uintptr_t)(x) ^ g_exit_function_cookie
#define DEC_CXA_PARA_POINTER(x)  (void *)((x) ^ g_exit_function_cookie)

typedef void (*fp_t)(void);

/* required by global constructor when -fuse-cxa-atexit is enabled */
void *__dso_handle __attribute__((weak)) = &(__dso_handle);

int __cxa_atexit(void (*fun)(void *), void *para, void *dso)
{
    if(unlikely(g_exit_function_cookie == 0))
    {
        uintptr_t rand = 0;
        do
        {
            if(SGX_SUCCESS != sgx_read_rand((unsigned char *)&rand, sizeof(rand)))
            {
                return -1;
            }
        } while(rand == 0);

        sgx_spin_lock(&g_exit_function_lock);
        if(g_exit_function_cookie == 0)
        {
            g_exit_function_cookie = rand;
        }

        sgx_spin_unlock(&g_exit_function_lock);
    }

    if(!sgx_is_within_enclave(fun, 0))
    {
        return -1;
    }

    exit_function_t *exit_function = (exit_function_t *)malloc(sizeof(exit_function_t));
    if(!exit_function)
    {
        return -1;
    }

    exit_function->cxa.fun = ENC_CXA_FUNC_POINTER(fun);
    exit_function->cxa.para = ENC_CXA_PARA_POINTER(para);
    exit_function->cxa.dso_handle = dso;

    sgx_spin_lock(&g_exit_function_lock);
    exit_function->next = g_exit_function;
    g_exit_function = exit_function;
    sgx_spin_unlock(&g_exit_function_lock);

    return 0;
}

int atexit(void (*fun)(void))
{
    return __cxa_atexit((void (*)(void *))fun, NULL, __dso_handle);
}

static void do_atexit_aux(void)
{
    sgx_spin_lock(&g_exit_function_lock);
    exit_function_t *exit_function = g_exit_function;
    g_exit_function = NULL;
    sgx_spin_unlock(&g_exit_function_lock);

    while (exit_function != NULL)
    {
        cxa_function_t cxa_func = DEC_CXA_FUNC_POINTER(exit_function->cxa.fun);
        void *para = DEC_CXA_PARA_POINTER(exit_function->cxa.para);
        cxa_func(para);

        exit_function_t *tmp = exit_function;
        exit_function = exit_function->next;
        free(tmp);
    }
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

/* auxiliary routines */
static void do_dtors_aux(void)
{
    fp_t *p = NULL;
    uintptr_t uninit_array_addr;
    size_t uninit_array_size;
    const void *enclave_start = (const void*)&__ImageBase;

    elf_get_uninit_array(enclave_start, &uninit_array_addr, &uninit_array_size);

    if (uninit_array_addr == 0 || uninit_array_size == 0)
        return;

    fp_t *fp_start = (fp_t*)(uninit_array_addr + (uintptr_t)(enclave_start));
    fp_t *fp_end = fp_start + (uninit_array_size / sizeof(fp_t));

    /* traverse .fini_array in reverse order */
    for (p = fp_end - 1; p >= fp_start; p--)
    {
        (*p)();
    }
}

void init_global_object(void)
{
    do_ctors_aux();
}

void uninit_global_object(void)
{
    do_atexit_aux();
    do_dtors_aux();
}

