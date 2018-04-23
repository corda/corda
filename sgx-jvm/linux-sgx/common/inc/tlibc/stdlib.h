/*	$OpenBSD: stdlib.h,v 1.47 2010/05/18 22:24:55 tedu Exp $	*/
/*	$NetBSD: stdlib.h,v 1.25 1995/12/27 21:19:08 jtc Exp $	*/

/*-
* Copyright (c) 1990 The Regents of the University of California.
* All rights reserved.
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
*	@(#)stdlib.h	5.13 (Berkeley) 6/4/91
*/

#ifndef _STDLIB_H_
#define _STDLIB_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

#ifndef _SIZE_T_DEFINED_
#define _SIZE_T_DEFINED_
typedef __size_t    size_t;
#endif

#ifdef _TLIBC_WIN_
#if !defined(_WCHAR_T_DEFINED) && !defined (_NATIVE_WCHAR_T_DEFINED)
#define _WCHAR_T_DEFINED
typedef unsigned short  wchar_t;
#endif
#else
#if !defined(_WCHAR_T_DEFINED_) && !defined(__cplusplus)
#define _WCHAR_T_DEFINED_
#ifndef __WCHAR_TYPE__
#define __WCHAR_TYPE__ int
#endif
typedef __WCHAR_TYPE__ wchar_t;
#endif
#endif

#ifndef _DIV_T_DEFINED
typedef struct {
    int quot;       /* quotient */
    int rem;        /* remainder */
} div_t;

typedef struct {
    long quot;      /* quotient */
    long rem;       /* remainder */
} ldiv_t;

typedef struct {
    long long quot; /* quotient */
    long long rem;  /* remainder */
} lldiv_t;
#define _DIV_T_DEFINED
#endif

#ifndef NULL
#ifdef __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

#define EXIT_FAILURE    1
#define EXIT_SUCCESS    0

#define RAND_MAX        0x7fffffff
#define MB_CUR_MAX      1

__BEGIN_DECLS

_TLIBC_NORETURN_ void _TLIBC_CDECL_ abort(void);
int     _TLIBC_CDECL_ atexit(void (*)(void));
int     _TLIBC_CDECL_ abs(int);
double  _TLIBC_CDECL_ atof(const char *);
int     _TLIBC_CDECL_ atoi(const char *);
long    _TLIBC_CDECL_ atol(const char *);
void *  _TLIBC_CDECL_ bsearch(const void *, const void *, size_t, size_t, int (*)(const void *, const void *));
void *  _TLIBC_CDECL_ calloc(size_t, size_t);
div_t   _TLIBC_CDECL_ div(int, int);
void    _TLIBC_CDECL_ free(void *);
long    _TLIBC_CDECL_ labs(long);
ldiv_t  _TLIBC_CDECL_ ldiv(long, long);
void *  _TLIBC_CDECL_ malloc(size_t);
void *  _TLIBC_CDECL_ memalign(size_t, size_t);
void    _TLIBC_CDECL_ qsort(void *, size_t, size_t, int (*)(const void *, const void *));
void *  _TLIBC_CDECL_ realloc(void *, size_t);
double  _TLIBC_CDECL_ strtod(const char *, char **);
long    _TLIBC_CDECL_ strtol(const char *, char **, int);
float   _TLIBC_CDECL_ strtof(const char *, char **);

long long
        _TLIBC_CDECL_ atoll(const char *);
long long
        _TLIBC_CDECL_ llabs(long long);
lldiv_t
        _TLIBC_CDECL_ lldiv(long long, long long);
long long
        _TLIBC_CDECL_ strtoll(const char *, char **, int);
unsigned long
        _TLIBC_CDECL_ strtoul(const char *, char **, int);
long double
        _TLIBC_CDECL_ strtold(const char *, char **);
unsigned long long
        _TLIBC_CDECL_ strtoull(const char *, char **, int);

int     _TLIBC_CDECL_ mblen(const char *, size_t);
size_t  _TLIBC_CDECL_ mbstowcs(wchar_t *, const char *, size_t);
int     _TLIBC_CDECL_ wctomb(char *, wchar_t);
int     _TLIBC_CDECL_ mbtowc(wchar_t *, const char *, size_t);
size_t  _TLIBC_CDECL_ wcstombs(char *, const wchar_t *, size_t);


/*
 * Deprecated C99.
 */
_TLIBC_DEPRECATED_FUNCTION_(int     _TLIBC_CDECL_, atexit, void (_TLIBC_CDECL_ *)(void));
_TLIBC_DEPRECATED_FUNCTION_(int     _TLIBC_CDECL_, rand, void);
_TLIBC_DEPRECATED_FUNCTION_(void    _TLIBC_CDECL_, srand, unsigned);
_TLIBC_DEPRECATED_FUNCTION_(void    _TLIBC_CDECL_, exit, int);
_TLIBC_DEPRECATED_FUNCTION_(void    _TLIBC_CDECL_, _Exit, int);
_TLIBC_DEPRECATED_FUNCTION_(char *  _TLIBC_CDECL_, getenv, const char *);
_TLIBC_DEPRECATED_FUNCTION_(int     _TLIBC_CDECL_, system, const char *);

/*
 * Non-C99 Functions.
 */
void *  _TLIBC_CDECL_ alloca(size_t);

/*
 * Deprecated Non-C99.
 */
//_TLIBC_DEPRECATED_FUNCTION_(void _TLIBC_CDECL_, _exit, int);

__END_DECLS

#endif /* !_STDLIB_H_ */
