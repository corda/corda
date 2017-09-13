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

/*
 *This file wrapper some trace output.
*/

#ifndef _SE_DEBUG_H_
#define _SE_DEBUG_H_

#include <stdio.h>
#include <stdarg.h>

typedef enum
{
	SE_TRACE_ERROR,
	SE_TRACE_WARNING,
	SE_TRACE_NOTICE,
	SE_TRACE_DEBUG
} se_trace_t;

#ifndef SE_DEBUG_LEVEL
/* Each module need define their own SE_DEBUG_LEVEL */
#define SE_DEBUG_LEVEL SE_TRACE_ERROR
#endif

#ifdef __cplusplus
extern "C" {
#endif
int se_trace_internal(int debug_level, const char *fmt, ...);

#ifdef __cplusplus
}
#endif

/* For libraries, we usually define DISABLE_TRACE to disable any trace. */
/* For apps, we usually enable trace. */
#ifdef DISABLE_TRACE
#define SE_TRACE(...)
#define se_trace(...)
#else /* DISABLE_TRACE */
#define se_trace(debug_level, fmt, ...)     \
    do {                                    \
        if(debug_level <= SE_DEBUG_LEVEL)   \
            se_trace_internal(debug_level, fmt, ##__VA_ARGS__);       \
    }while(0)

/* For compatibility, SE_TRACE/se_trace is used in old code. */
/* New code should use SE_TRACE_DEBUG, SE_TRACE_NOTICE, SE_TRACE_WARNING, SE_TRACE_ERROR */
#define SE_TRACE(debug_level, fmt, ...) \
	    se_trace(debug_level, "[%s %s:%d] " fmt, __FUNCTION__, __FILE__, __LINE__, ##__VA_ARGS__)
#endif/* DISABLE_TRACE */

/* SE_TRACE_DEBUG and SE_TRACE_NOTICE print the debug information plus message. */
#define SE_TRACE_DEBUG(fmt, ...) se_trace(SE_TRACE_DEBUG, "[%s %s:%d] " fmt, __FUNCTION__, __FILE__, __LINE__, ##__VA_ARGS__)
#define SE_TRACE_NOTICE(fmt, ...) se_trace(SE_TRACE_NOTICE, "[%s %s:%d] " fmt, __FUNCTION__, __FILE__, __LINE__, ##__VA_ARGS__)
/* SE_TRACE_WARNING and SE_TRACE_ERROR only print message. */
#define SE_TRACE_WARNING(fmt, ...) se_trace(SE_TRACE_WARNING, fmt, ##__VA_ARGS__)
#define SE_TRACE_ERROR(fmt, ...) se_trace(SE_TRACE_ERROR, fmt, ##__VA_ARGS__)

#endif
