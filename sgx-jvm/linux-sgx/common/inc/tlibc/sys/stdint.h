/*	$OpenBSD: stdint.h,v 1.4 2006/12/10 22:17:55 deraadt Exp $	*/

/*
 * Copyright (c) 1997, 2005 Todd C. Miller <Todd.Miller@courtesan.com>
 *
 * Permission to use, copy, modify, and distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

#ifndef _SYS_STDINT_H_
#define _SYS_STDINT_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

/* 7.18.1.1 Exact-width integer types (also in sys/types.h) */
#ifndef _INT8_T_DEFINED_
#define _INT8_T_DEFINED_
typedef __int8_t        int8_t;
#endif

#ifndef _UINT8_T_DEFINED_
#define _UINT8_T_DEFINED_
typedef __uint8_t       uint8_t;
#endif

#ifndef _INT16_T_DEFINED_
#define _INT16_T_DEFINED_
typedef __int16_t       int16_t;
#endif

#ifndef _UINT16_T_DEFINED_
#define _UINT16_T_DEFINED_
typedef __uint16_t      uint16_t;
#endif

#ifndef _INT32_T_DEFINED_
#define _INT32_T_DEFINED_
typedef __int32_t       int32_t;
#endif

#ifndef _UINT32_T_DEFINED_
#define _UINT32_T_DEFINED_
typedef __uint32_t      uint32_t;
#endif

#ifndef _INT64_T_DEFINED_
#define _INT64_T_DEFINED_
typedef __int64_t       int64_t;
#endif

#ifndef _UINT64_T_DEFINED_
#define _UINT64_T_DEFINED_
typedef __uint64_t      uint64_t;
#endif

/* 7.18.1.2 Minimum-width integer types */
typedef __int_least8_t      int_least8_t;
typedef __uint_least8_t     uint_least8_t;
typedef __int_least16_t     int_least16_t;
typedef __uint_least16_t    uint_least16_t;
typedef __int_least32_t     int_least32_t;
typedef __uint_least32_t    uint_least32_t;
typedef __int_least64_t     int_least64_t;
typedef __uint_least64_t    uint_least64_t;

/* 7.18.1.3 Fastest minimum-width integer types */
typedef __int_fast8_t       int_fast8_t;
typedef __uint_fast8_t      uint_fast8_t;
typedef __int_fast16_t      int_fast16_t;
typedef __uint_fast16_t     uint_fast16_t;
typedef __int_fast32_t      int_fast32_t;
typedef __uint_fast32_t     uint_fast32_t;
typedef __int_fast64_t      int_fast64_t;
typedef __uint_fast64_t     uint_fast64_t;

/* 7.18.1.4 Integer types capable of holding object pointers */
#ifndef _INTPTR_T_DEFINED_
#define _INTPTR_T_DEFINED_
typedef __intptr_t      intptr_t;
#endif

#ifndef _UINTPTR_T_DEFINED_
#define _UINTPTR_T_DEFINED_
typedef __uintptr_t     uintptr_t;
#endif

/* 7.18.1.5 Greatest-width integer types */
typedef __intmax_t      intmax_t;
typedef __uintmax_t     uintmax_t;

//#if !defined(__cplusplus) || defined(__STDC_LIMIT_MACROS)
/*
 * 7.18.2 Limits of specified-width integer types.
 *
 * The following object-like macros specify the minimum and maximum limits
 * of integer types corresponding to the typedef names defined above.
 */

/* 7.18.2.1 Limits of exact-width integer types */
#define INT8_MIN        (-0x7f - 1)
#define INT16_MIN       (-0x7fff - 1)
#define INT32_MIN       (-0x7fffffff - 1)
#ifdef __x86_64__
#define INT64_MIN       (-0x7fffffffffffffffL - 1)
#else
#define INT64_MIN       (-0x7fffffffffffffffLL - 1)
#endif

#define INT8_MAX        0x7f
#define INT16_MAX       0x7fff
#define INT32_MAX       0x7fffffff
#ifdef __x86_64__
#define INT64_MAX       0x7fffffffffffffffL
#else
#define INT64_MAX       0x7fffffffffffffffLL
#endif

#define UINT8_MAX       0xff
#define UINT16_MAX      0xffff
#define UINT32_MAX      0xffffffffU
#ifdef __x86_64__
#define UINT64_MAX      0xffffffffffffffffUL
#else
#define UINT64_MAX      0xffffffffffffffffULL
#endif

/* 7.18.2.2 Limits of minimum-width integer types */
#define INT_LEAST8_MIN      INT8_MIN
#define INT_LEAST16_MIN     INT16_MIN
#define INT_LEAST32_MIN     INT32_MIN
#define INT_LEAST64_MIN     INT64_MIN

#define INT_LEAST8_MAX      INT8_MAX
#define INT_LEAST16_MAX     INT16_MAX
#define INT_LEAST32_MAX     INT32_MAX
#define INT_LEAST64_MAX     INT64_MAX

#define UINT_LEAST8_MAX     UINT8_MAX
#define UINT_LEAST16_MAX    UINT16_MAX
#define UINT_LEAST32_MAX    UINT32_MAX
#define UINT_LEAST64_MAX    UINT64_MAX

/* 7.18.2.3 Limits of fastest minimum-width integer types */
#define INT_FAST8_MIN       INT8_MIN
#define INT_FAST16_MIN      INT16_MIN
#define INT_FAST32_MIN      INT32_MIN
#define INT_FAST64_MIN      INT64_MIN

#define INT_FAST8_MAX       INT8_MAX
#ifdef __x86_64__
#define INT_FAST16_MAX      INT64_MAX
#define INT_FAST32_MAX      INT64_MAX
#else
#define INT_FAST16_MAX      INT32_MAX
#define INT_FAST32_MAX      INT32_MAX
#endif
#define INT_FAST64_MAX      INT64_MAX

#define UINT_FAST8_MAX      UINT8_MAX
#ifdef __x86_64__
#define UINT_FAST16_MAX     UINT64_MAX
#define UINT_FAST32_MAX     UINT64_MAX
#else
#define UINT_FAST16_MAX     UINT32_MAX
#define UINT_FAST32_MAX     UINT32_MAX
#endif
#define UINT_FAST64_MAX     UINT64_MAX

/* 7.18.2.4 Limits of integer types capable of holding object pointers */
#ifdef __x86_64__
#define INTPTR_MIN      INT64_MIN
#define INTPTR_MAX      INT64_MAX
#define UINTPTR_MAX     UINT64_MAX
#else
#define INTPTR_MIN      INT32_MIN
#define INTPTR_MAX      INT32_MAX
#define UINTPTR_MAX     UINT32_MAX
#endif

/* 7.18.2.5 Limits of greatest-width integer types */
#define INTMAX_MIN      INT64_MIN
#define INTMAX_MAX      INT64_MAX
#define UINTMAX_MAX     UINT64_MAX

/*
 * 7.18.3 Limits of other integer types.
 *
 * The following object-like macros specify the minimum and maximum limits
 * of integer types corresponding to types specified in other standard
 * header files.
 */

/* Limits of ptrdiff_t */
#define PTRDIFF_MIN     INTPTR_MIN
#define PTRDIFF_MAX     INTPTR_MAX

/* Limits of size_t (also in limits.h) */
#ifndef SIZE_MAX
#define SIZE_MAX        UINTPTR_MAX
#endif

/* Limits of wchar_t */
#ifdef _TLIBC_WIN_
# define WCHAR_MIN      0x0000
# define WCHAR_MAX      0xffff
#else
# ifdef __WCHAR_MAX__
#  define WCHAR_MAX __WCHAR_MAX__
# else
#  define WCHAR_MAX (2147483647)
# endif
# ifdef __WCHAR_MIN__
#  define WCHAR_MIN __WCHAR_MIN__
# elif L'\0' - 1 > 0
#  define WCHAR_MIN L'\0'
# else
#  define WCHAR_MIN (-WCHAR_MAX - 1)
# endif
#endif

/* Limits of wint_t */
# define WINT_MIN      (0u)
# define WINT_MAX      (4294967295u)

//#endif /* __cplusplus || __STDC_LIMIT_MACROS */

//#if !defined(__cplusplus) || defined(__STDC_CONSTANT_MACROS)
/*
 * 7.18.4 Macros for integer constants.
 *
 * The following function-like macros expand to integer constants
 * suitable for initializing objects that have integer types corresponding
 * to types defined in <stdint.h>.  The argument in any instance of
 * these macros shall be a decimal, octal, or hexadecimal constant with
 * a value that does not exceed the limits for the corresponding type.
 */

/* 7.18.4.1 Macros for minimum-width integer constants. */
#define INT8_C(_c)      (_c)
#define INT16_C(_c)     (_c)
#define INT32_C(_c)     (_c)
#define INT64_C(_c)     __CONCAT(_c, LL)

#define UINT8_C(_c)     (_c)
#define UINT16_C(_c)    (_c)
#define UINT32_C(_c)    __CONCAT(_c, U)
#define UINT64_C(_c)    __CONCAT(_c, ULL)

/* 7.18.4.2 Macros for greatest-width integer constants. */
#define INTMAX_C(_c)    __CONCAT(_c, LL)
#define UINTMAX_C(_c)   __CONCAT(_c, ULL)

//#endif /* __cplusplus || __STDC_CONSTANT_MACROS */

#endif /* _SYS_STDINT_H_ */
