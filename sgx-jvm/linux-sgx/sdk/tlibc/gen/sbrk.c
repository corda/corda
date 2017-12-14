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

#include <stddef.h>
#include <unistd.h>

#include "trts_util.h"
#include "rts.h"
#include "util.h"
#include "global_data.h"
#include "trts_inst.h"

SE_DECLSPEC_EXPORT size_t g_peak_heap_used = 0;
/* Please be aware of: sbrk is not thread safe by default. */

static void *heap_base = NULL;
static size_t heap_size = 0;
static int is_edmm_supported = 0;
static size_t heap_min_size = 0;

int heap_init(void *_heap_base, size_t _heap_size, size_t _heap_min_size, int _is_edmm_supported)
{
    if (heap_base != NULL)
        return SGX_ERROR_UNEXPECTED;

    if ((_heap_base == NULL) || (((size_t) _heap_base) & (SE_PAGE_SIZE - 1)))
        return SGX_ERROR_UNEXPECTED;

    if (_heap_size & (SE_PAGE_SIZE - 1))
        return SGX_ERROR_UNEXPECTED;

    if (_heap_min_size & (SE_PAGE_SIZE - 1))
        return SGX_ERROR_UNEXPECTED;

    if (_heap_size > SIZE_MAX - (size_t)heap_base)
        return SGX_ERROR_UNEXPECTED;

    heap_base = _heap_base;
    heap_size = _heap_size;
    heap_min_size = _heap_min_size;
    is_edmm_supported = _is_edmm_supported;

    return SGX_SUCCESS;
}

void* sbrk(intptr_t n)
{
    static size_t heap_used;
    void *heap_ptr = NULL;
    size_t prev_heap_used = heap_used;
    void * start_addr;
    size_t size = 0;

    if (!heap_base)
        return (void *)(~(size_t)0);

    /* shrink the heap */
    if (n < 0) {

        n *= -1;
        if (heap_used < n)
            return (void *)(~(size_t)0);

        heap_used -= n;

        /* heap_used is never larger than heap_size, and since heap_size <= SIZE_MAX - (size_t)heap_base,
           there's no integer overflow here.
         */  
        heap_ptr = (void *)((size_t)heap_base + (size_t)heap_used);

        if (is_edmm_supported && (prev_heap_used > heap_min_size)) 
        {
            assert((n & (SE_PAGE_SIZE - 1)) == 0);

            if (heap_used > heap_min_size)
            {
                start_addr = heap_ptr;
                size = n;
            }
            else
            {
                /* heap_min_size is never larger than heap_size, and since heap_size <= SIZE_MAX - (size_t)heap_base,
                   there's no integer overflow here.
                 */  
                start_addr = (void *)((size_t)(heap_base) + heap_min_size);
                size = prev_heap_used - heap_min_size;
            }
            int ret = trim_EPC_pages(start_addr, size >> SE_PAGE_SHIFT);
            if (ret != 0)
            {
                heap_used = prev_heap_used;
                return (void *)(~(size_t)0);
            }
        }
        return heap_ptr;
    }

    /* extend the heap */
    if((heap_used > (SIZE_MAX - n)) || ((heap_used + n) > heap_size))
        return (void *)(~(size_t)0);

    /* heap_used is never larger than heap_size, and since heap_size <= SIZE_MAX - (size_t)heap_base,
       there's no integer overflow here.
     */  
    heap_ptr = (void *)((size_t)heap_base + (size_t)heap_used);
    heap_used += n;

    /* update g_peak_heap_used */
    g_peak_heap_used = (g_peak_heap_used < heap_used) ? heap_used : g_peak_heap_used;

    if (is_edmm_supported && heap_used > heap_min_size)
    {
        assert((n & (SE_PAGE_SIZE - 1)) == 0);

        if (prev_heap_used > heap_min_size)
        {
            start_addr = heap_ptr;
            size = n;
        }
        else
        {

            /* heap_min_size is never larger than heap_size, and since heap_size <= SIZE_MAX - (size_t)heap_base,
               there's no integer overflow here.
             */  
            start_addr = (void *)((size_t)(heap_base) + heap_min_size);
            size = heap_used - heap_min_size;
        }
        int ret = apply_EPC_pages(start_addr, size >> SE_PAGE_SHIFT);
        if (ret != 0)
        {
            heap_used = prev_heap_used;
            return (void *)(~(size_t)0);
        }
    }
    return heap_ptr;
}
