/*  $OpenBSD: inttypes.h,v 1.10 2009/01/13 18:13:51 kettenis Exp $  */

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

#ifndef _INTTYPES_H_
#define _INTTYPES_H_

#include <sys/stdint.h>

/*
 * 7.8.1 Macros for format specifiers
 *
 * Each of the following object-like macros expands to a string
 * literal containing a conversion specifier, possibly modified by
 * a prefix such as hh, h, l, or ll, suitable for use within the
 * format argument of a formatted input/output function when
 * converting the corresponding integer type.  These macro names
 * have the general form of PRI (character string literals for the
 * fprintf family) or SCN (character string literals for the fscanf
 * family), followed by the conversion specifier, followed by a
 * name corresponding to a similar typedef name.  For example,
 * PRIdFAST32 can be used in a format string to print the value of
 * an integer of type int_fast32_t.
 */

/* fprintf macros for signed integers */
#define PRId8           "d"     /* int8_t */
#define PRId16          "d"     /* int16_t */
#define PRId32          "d"     /* int32_t */
#ifdef __x86_64__
#define PRId64          "ld"    /* int64_t */
#else
#define PRId64          "lld"   /* int64_t */
#endif

#define PRIdLEAST8      "d"     /* int_least8_t */
#define PRIdLEAST16     "d"     /* int_least16_t */
#define PRIdLEAST32     "d"     /* int_least32_t */
#ifdef __x86_64__
#define PRIdLEAST64     "ld"    /* int_least64_t */
#else
#define PRIdLEAST64     "lld"   /* int_least64_t */
#endif

#define PRIdFAST8       "d"     /* int_fast8_t */
#ifdef __x86_64__
#define PRIdFAST16      "ld"    /* int_fast16_t */
#define PRIdFAST32      "ld"    /* int_fast32_t */
#define PRIdFAST64      "ld"    /* int_fast64_t */
#else
#define PRIdFAST16      "d"     /* int_fast16_t */
#define PRIdFAST32      "d"     /* int_fast32_t */
#define PRIdFAST64      "lld"   /* int_fast64_t */
#endif

#ifdef __x86_64__
#define PRIdMAX         "ld"    /* intmax_t */
#else
#if defined(__i386__) 
#define PRIdMAX         "lld"   /* intmax_t */
#else
#define PRIdMAX         "jd"    /* intmax_t */
#endif
#endif

#ifdef __i386__
#define PRIdPTR         "d"     /* intptr_t */
#else
#define PRIdPTR         "ld"    /* intptr_t */
#endif

#define PRIi8           "i"     /* int8_t */
#define PRIi16          "i"     /* int16_t */
#define PRIi32          "i"     /* int32_t */
#ifdef __x86_64__
#define PRIi64          "li"    /* int64_t */
#else
#define PRIi64          "lli"   /* int64_t */
#endif

#define PRIiLEAST8      "i"     /* int_least8_t */
#define PRIiLEAST16     "i"     /* int_least16_t */
#define PRIiLEAST32     "i"     /* int_least32_t */
#ifdef __x86_64__
#define PRIiLEAST64     "li"    /* int_least64_t */
#else
#define PRIiLEAST64     "lli"   /* int_least64_t */
#endif

#define PRIiFAST8       "i"     /* int_fast8_t */
#ifdef __x86_64__
#define PRIiFAST16      "li"    /* int_fast16_t */
#define PRIiFAST32      "li"    /* int_fast32_t */
#define PRIiFAST64      "li"    /* int_fast64_t */
#else
#define PRIiFAST16      "i"     /* int_fast16_t */
#define PRIiFAST32      "i"     /* int_fast32_t */
#define PRIiFAST64      "lli"   /* int_fast64_t */
#endif

#ifdef __x86_64__
#define PRIiMAX         "li"    /* intmax_t */
#else
#if defined(__i386__) 
#define PRIiMAX         "lli"   /* intmax_t */
#else
#define PRIiMAX         "ji"    /* intmax_t */
#endif
#endif

#ifdef __i386__
#define PRIiPTR         "i"     /* intptr_t */
#else
#define PRIiPTR         "li"    /* intptr_t */
#endif

/* fprintf macros for unsigned integers */
#define PRIo8           "o"     /* int8_t */
#define PRIo16          "o"     /* int16_t */
#define PRIo32          "o"     /* int32_t */
#ifdef __x86_64__
#define PRIo64          "lo"    /* int64_t */
#else
#define PRIo64          "llo"   /* int64_t */
#endif

#define PRIoLEAST8      "o"     /* int_least8_t */
#define PRIoLEAST16     "o"     /* int_least16_t */
#define PRIoLEAST32     "o"     /* int_least32_t */
#ifdef __x86_64__
#define PRIoLEAST64     "lo"    /* int_least64_t */
#else
#define PRIoLEAST64     "llo"   /* int_least64_t */
#endif

#define PRIoFAST8       "o"     /* int_fast8_t */
#ifdef __x86_64__
#define PRIoFAST16      "lo"    /* int_fast16_t */
#define PRIoFAST32      "lo"    /* int_fast32_t */
#define PRIoFAST64      "lo"    /* int_fast64_t */
#else
#define PRIoFAST16      "o"     /* int_fast16_t */
#define PRIoFAST32      "o"     /* int_fast32_t */
#define PRIoFAST64      "llo"   /* int_fast64_t */
#endif

#ifdef __x86_64__
#define PRIoMAX         "lo"    /* intmax_t */
#else
#if defined(__i386__) 
#define PRIoMAX         "llo"   /* intmax_t */
#else
#define PRIoMAX         "jo"    /* intmax_t */
#endif
#endif

#ifdef __i386__
#define PRIoPTR         "o"     /* intptr_t */
#else
#define PRIoPTR         "lo"    /* intptr_t */
#endif

#define PRIu8           "u"     /* uint8_t */
#define PRIu16          "u"     /* uint16_t */
#define PRIu32          "u"     /* uint32_t */

#ifdef __x86_64__
#define PRIu64          "lu"    /* uint64_t */
#else
#define PRIu64          "llu"   /* uint64_t */
#endif

#define PRIuLEAST8      "u"     /* uint_least8_t */
#define PRIuLEAST16     "u"     /* uint_least16_t */
#define PRIuLEAST32     "u"     /* uint_least32_t */

#ifdef __x86_64__
#define PRIuLEAST64     "lu"    /* uint_least64_t */
#else
#define PRIuLEAST64     "llu"   /* uint_least64_t */
#endif

#define PRIuFAST8       "u"     /* uint_fast8_t */

#ifdef __x86_64__
#define PRIuFAST16      "lu"    /* uint_fast16_t */
#define PRIuFAST32      "lu"    /* uint_fast32_t */
#define PRIuFAST64      "lu"    /* uint_fast64_t */
#else
#define PRIuFAST16      "u"     /* uint_fast16_t */
#define PRIuFAST32      "u"     /* uint_fast32_t */
#define PRIuFAST64      "llu"   /* uint_fast64_t */
#endif

#ifdef __x86_64__
#define PRIuMAX         "lu"    /* uintmax_t */
#else
#if defined(__i386__) 
#define PRIuMAX         "llu"   /* uintmax_t */
#else
#define PRIuMAX         "ju"    /* uintmax_t */
#endif
#endif

#ifdef __i386__
#define PRIuPTR         "u"     /* uintptr_t */
#else
#define PRIuPTR         "lu"    /* uintptr_t */
#endif

#define PRIx8           "x"     /* uint8_t */
#define PRIx16          "x"     /* uint16_t */
#define PRIx32          "x"     /* uint32_t */
#ifdef __x86_64__
#define PRIx64          "lx"    /* uint64_t */
#else
#define PRIx64          "llx"   /* uint64_t */
#endif

#define PRIxLEAST8      "x"     /* uint_least8_t */
#define PRIxLEAST16     "x"     /* uint_least16_t */
#define PRIxLEAST32     "x"     /* uint_least32_t */
#ifdef __x86_64__
#define PRIxLEAST64     "lx"    /* uint_least64_t */
#else
#define PRIxLEAST64     "llx"   /* uint_least64_t */
#endif

#define PRIxFAST8       "x"     /* uint_fast8_t */
#ifdef __x86_64__
#define PRIxFAST16      "lx"    /* uint_fast16_t */
#define PRIxFAST32      "lx"    /* uint_fast32_t */
#define PRIxFAST64      "lx"    /* uint_fast64_t */
#else
#define PRIxFAST16      "x"     /* uint_fast16_t */
#define PRIxFAST32      "x"     /* uint_fast32_t */
#define PRIxFAST64      "llx"   /* uint_fast64_t */
#endif

#ifdef __x86_64__
#define PRIxMAX         "lx"    /* uintmax_t */
#else
#if defined(__i386__) 
#define PRIxMAX         "llx"   /* uintmax_t */
#else
#define PRIxMAX         "jx"    /* uintmax_t */
#endif
#endif

#ifdef __i386__
#define PRIxPTR         "x"     /* uintptr_t */
#else
#define PRIxPTR         "lx"    /* uintptr_t */
#endif

#define PRIX8           "X"     /* uint8_t */
#define PRIX16          "X"     /* uint16_t */
#define PRIX32          "X"     /* uint32_t */

#ifdef __x86_64__
#define PRIX64          "lX"    /* uint64_t */
#else
#define PRIX64          "llX"   /* uint64_t */
#endif

#define PRIXLEAST8      "X"     /* uint_least8_t */
#define PRIXLEAST16     "X"     /* uint_least16_t */
#define PRIXLEAST32     "X"     /* uint_least32_t */
#ifdef __x86_64__
#define PRIXLEAST64     "lX"    /* uint_least64_t */
#else
#define PRIXLEAST64     "llX"   /* uint_least64_t */
#endif

#define PRIXFAST8       "X"     /* uint_fast8_t */
#ifdef __x86_64__
#define PRIXFAST16      "lX"    /* uint_fast16_t */
#define PRIXFAST32      "lX"    /* uint_fast32_t */
#define PRIXFAST64      "lX"    /* uint_fast64_t */
#else
#define PRIXFAST16      "X"     /* uint_fast16_t */
#define PRIXFAST32      "X"     /* uint_fast32_t */
#define PRIXFAST64      "llX"   /* uint_fast64_t */
#endif

#ifdef __x86_64__
#define PRIXMAX         "lX"    /* uintmax_t */
#else
#if defined(__i386__) 
#define PRIXMAX         "llX"   /* uintmax_t */
#else
#define PRIXMAX         "jX"    /* uintmax_t */
#endif
#endif

#ifdef __i386__
#define PRIXPTR         "X"     /* uintptr_t */
#else
#define PRIXPTR         "lX"    /* uintptr_t */
#endif

typedef struct {
    intmax_t quot;      /* quotient */
    intmax_t rem;       /* remainder */
} imaxdiv_t;

__BEGIN_DECLS

intmax_t _TLIBC_CDECL_ imaxabs(intmax_t);
imaxdiv_t _TLIBC_CDECL_ imaxdiv(intmax_t, intmax_t);
intmax_t _TLIBC_CDECL_ strtoimax(const char *, char **, int);
uintmax_t _TLIBC_CDECL_ strtoumax(const char *, char **, int);

__END_DECLS

#endif /* _INTTYPES_H_ */
