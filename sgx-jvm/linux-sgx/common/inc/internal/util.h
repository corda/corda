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

#ifndef _UTIL_H_
#define _UTIL_H_

#include "arch.h"
#include <assert.h>

#ifdef __cplusplus
#define	GET_PTR(t, p, offset) reinterpret_cast<t*>( reinterpret_cast<size_t>(p) + static_cast<size_t>(offset) )
#define PTR_DIFF(p1, p2)	((reinterpret_cast<size_t>(p1) - reinterpret_cast<size_t>(p2)))
#else
#define	GET_PTR(t, p, offset) (t*)( (size_t)(p) + (size_t)(offset) )
#define PTR_DIFF(p1, p2)	((size_t)(p1) - (size_t)(p2))
#endif

#define DIFF(p1, p2)        (assert((size_t)(p1) >= (size_t)(p2)), ((size_t)(p1) - (size_t)(p2)))
#define DIFF64(p1, p2)      (assert((uint64_t)(p1) >= (uint64_t)(p2)), ((uint64_t)(p1) - (uint64_t)(p2)))

#define SE_PAGE_SHIFT       12
#define SE_BULK_PAGE_FRAME_SHIFT 4
#define SE_BULK_PAGE_FRAME_SIZE (1 << SE_BULK_PAGE_FRAME_SHIFT)
#define SE_BULK_PAGE_FRAME_MASK (SE_BULK_PAGE_FRAME_SIZE-1)
#define SE_BULK_PAGE_SHIFT	(SE_PAGE_SHIFT + SE_BULK_PAGE_FRAME_SHIFT)
#define SE_BULK_PAGE_SIZE	(1 << SE_BULK_PAGE_SHIFT)
#define SE_GUARD_PAGE_SHIFT 16
#define SE_GUARD_PAGE_SIZE (1 << SE_GUARD_PAGE_SHIFT)

#define	ROUND_TO(x, align)  (((x) + ((align)-1)) & ~((align)-1))
#define	ROUND_TO_PAGE(x)    ROUND_TO(x, SE_PAGE_SIZE)
#define	TRIM_TO_PAGE(x) ((x) & ~(SE_PAGE_SIZE-1))
#define PAGE_OFFSET(x) ((x) & (SE_PAGE_SIZE -1))
#ifdef __cplusplus
#define PAGE_ALIGN(t, x)	reinterpret_cast<t*>((reinterpret_cast<size_t>(x)+(SE_PAGE_SIZE-1)) & (~(SE_PAGE_SIZE-1)))
#else
#define PAGE_ALIGN(t, x)	(t*)( ((size_t)(x)+(SE_PAGE_SIZE-1)) & (~(SE_PAGE_SIZE-1)) )
#endif

#define IS_PAGE_ALIGNED(x)	(!((size_t)(x)&(SE_PAGE_SIZE-1)))

#define MIN(x, y) (((x)>(y))?(y):(x))
#define MAX(x, y) (((x)>(y))?(x):(y))
#define ARRAY_LENGTH(x) (sizeof(x)/sizeof(x[0]))

/* used to eliminate `unused variable' warning */
#define UNUSED(val) (void)(val)

#include <stddef.h>
#define container_of(ptr, type, member) (type *)( (char *)(ptr) - offsetof(type,member) )

#endif
