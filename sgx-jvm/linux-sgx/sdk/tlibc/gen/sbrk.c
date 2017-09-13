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


SE_DECLSPEC_EXPORT size_t g_peak_heap_used = 0;
/* Please be aware of: sbrk is not thread safe by default. */

void* sbrk(intptr_t n)
{
    static size_t heap_used;
    void *heap_ptr = NULL;

    void *heap_base = get_heap_base();
    size_t heap_size = get_heap_size();

    if (!heap_base)
        return (void *)(~(size_t)0);

    /* shrink the heap */
    if (n < 0) {

        if (heap_used <= INTPTR_MAX && ((intptr_t)heap_used + n) < 0)
            return (void *)(~(size_t)0);

        heap_used += n;
        heap_ptr = (void *)((size_t)heap_base + (size_t)heap_used);

        return heap_ptr;
    }

    /* extend the heap */
    if ((heap_used + n) > heap_size)
        return (void *)(~(size_t)0);

    heap_ptr = (void *)((size_t)heap_base + (size_t)heap_used);
    heap_used += n;

    /* update g_peak_heap_used */
    g_peak_heap_used = (g_peak_heap_used < heap_used) ? heap_used : g_peak_heap_used;

    return heap_ptr;
}
