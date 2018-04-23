/*	$OpenBSD: _types.h,v 1.2 2008/03/16 19:42:57 otto Exp $	*/

/*-
 * Copyright (c) 1990, 1993
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 *	@(#)types.h	8.3 (Berkeley) 1/5/94
 */

#ifndef _SYS__TYPES_H_
#define _SYS__TYPES_H_

#include <sys/cdefs.h>
/* 7.18.1.1 Exact-width integer types */
typedef signed char         __int8_t;
typedef unsigned char       __uint8_t;
typedef short               __int16_t;
typedef unsigned short      __uint16_t;
typedef int                 __int32_t;
typedef unsigned int        __uint32_t;
#ifdef __x86_64__
typedef long                __int64_t;
typedef unsigned long       __uint64_t;
#else
typedef long long           __int64_t;
typedef unsigned long long  __uint64_t;
#endif

/* 7.18.1.2 Minimum-width integer types */
typedef __int8_t            __int_least8_t;
typedef __uint8_t           __uint_least8_t;
typedef __int16_t           __int_least16_t;
typedef __uint16_t          __uint_least16_t;
typedef __int32_t           __int_least32_t;
typedef __uint32_t          __uint_least32_t;
typedef __int64_t           __int_least64_t;
typedef __uint64_t          __uint_least64_t;

/* 7.18.1.3 Fastest minimum-width integer types */
typedef __int8_t            __int_fast8_t;
typedef __uint8_t           __uint_fast8_t;
#ifdef __x86_64__
/* Linux x86_64, from stdint.h */
typedef long int            __int_fast16_t;
typedef unsigned long int   __uint_fast16_t;
typedef long int            __int_fast32_t;
typedef unsigned long int   __uint_fast32_t;
typedef long int            __int_fast64_t;
typedef unsigned long int   __uint_fast64_t;
#else 
/* Android x86, and Linux x86 */
typedef __int32_t           __int_fast16_t;
typedef __uint32_t          __uint_fast16_t;
typedef __int32_t           __int_fast32_t;
typedef __uint32_t          __uint_fast32_t;
typedef __int64_t           __int_fast64_t;
typedef __uint64_t          __uint_fast64_t;
#endif

typedef long                __off_t;

/* 7.18.1.4 Integer types capable of holding object pointers */
#ifdef __i386__
typedef __int32_t           __intptr_t;
typedef __uint32_t          __uintptr_t;
typedef __int32_t           __ptrdiff_t;
/* Standard system types */
typedef __uint32_t          __size_t;
typedef __int32_t           __ssize_t;
typedef long double         __double_t;
typedef long double         __float_t;
#else
typedef __int64_t           __intptr_t;
typedef __uint64_t          __uintptr_t;
typedef __int64_t           __ptrdiff_t;

/* Standard system types */
typedef unsigned long       __size_t;
typedef long                __ssize_t;
typedef double              __double_t;
typedef float               __float_t;

#endif /* !__i386__ */

typedef long                __clock_t;

typedef long                __time_t;
typedef __builtin_va_list   __va_list;
typedef unsigned int        __wint_t;
/* wctype_t and wctrans_t are defined in wchar.h */
typedef unsigned long int   __wctype_t;
typedef int *               __wctrans_t;

/*
 * mbstate_t is an opaque object to keep conversion state, during multibyte
 * stream conversions. The content must not be referenced by user programs.
 */
/* For Linux, __mbstate_t is defined in wchar.h */
typedef struct {
    int __c;
    union {
        __wint_t __wc;
        char __wcb[4];
    } __v;
} __mbstate_t;

/* 7.18.1.5 Greatest-width integer types */
typedef __int64_t           __intmax_t;
typedef __uint64_t          __uintmax_t;

#endif /* !_SYS__TYPES_H_ */



