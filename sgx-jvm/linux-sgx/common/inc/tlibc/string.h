/*	$OpenBSD: string.h,v 1.20 2010/09/24 13:33:00 matthew Exp $	*/
/*	$NetBSD: string.h,v 1.6 1994/10/26 00:56:30 cgd Exp $	*/

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
 *	@(#)string.h	5.10 (Berkeley) 3/9/91
 */

#ifndef _STRING_H_
#define _STRING_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

#ifndef _SIZE_T_DEFINED_
typedef __size_t    size_t;
#define _SIZE_T_DEFINED_
#endif

#ifndef _ERRNO_T_DEFINED
#define _ERRNO_T_DEFINED
typedef int errno_t;
#endif

#ifndef NULL
#ifdef __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

__BEGIN_DECLS

void * _TLIBC_CDECL_ memchr(const void *, int, size_t);
int    _TLIBC_CDECL_ memcmp(const void *, const void *, size_t);
void * _TLIBC_CDECL_ memcpy(void *, const void *, size_t);
void * _TLIBC_CDECL_ memmove(void *, const void *, size_t);
void * _TLIBC_CDECL_ memset(void *, int, size_t);
char * _TLIBC_CDECL_ strchr(const char *, int);
int    _TLIBC_CDECL_ strcmp(const char *, const char *);
int    _TLIBC_CDECL_ strcoll(const char *, const char *);
size_t _TLIBC_CDECL_ strcspn(const char *, const char *);
char * _TLIBC_CDECL_ strerror(int);
size_t _TLIBC_CDECL_ strlen(const char *);
char * _TLIBC_CDECL_ strncat(char *, const char *, size_t);
int    _TLIBC_CDECL_ strncmp(const char *, const char *, size_t);
char * _TLIBC_CDECL_ strncpy(char *, const char *, size_t);
char * _TLIBC_CDECL_ strpbrk(const char *, const char *);
char * _TLIBC_CDECL_ strrchr(const char *, int);
size_t _TLIBC_CDECL_ strspn(const char *, const char *);
char * _TLIBC_CDECL_ strstr(const char *, const char *);
char * _TLIBC_CDECL_ strtok(char *, const char *);
size_t _TLIBC_CDECL_ strxfrm(char *, const char *, size_t);
size_t _TLIBC_CDECL_ strlcpy(char *, const char *, size_t);
errno_t _TLIBC_CDECL_ memset_s(void *s, size_t smax, int c, size_t n);

/*
 * Deprecated C99.
 */
_TLIBC_DEPRECATED_FUNCTION_(char * _TLIBC_CDECL_, strcat, char *, const char *);
_TLIBC_DEPRECATED_FUNCTION_(char * _TLIBC_CDECL_, strcpy, char *, const char *);

/* 
 * Common used non-C99 functions.
 */
char * _TLIBC_CDECL_ strndup(const char *, size_t);
size_t _TLIBC_CDECL_ strnlen(const char *, size_t);
int    _TLIBC_CDECL_ consttime_memequal(const void *b1, const void *b2, size_t len);

/*
 * Non-C99
 */
int    _TLIBC_CDECL_ bcmp(const void *, const void *, size_t);
void   _TLIBC_CDECL_ bcopy(const void *, void *, size_t);
void   _TLIBC_CDECL_ bzero(void *, size_t);
char * _TLIBC_CDECL_ index(const char *, int);
void * _TLIBC_CDECL_ mempcpy(void *, const void *, size_t);
char * _TLIBC_CDECL_ rindex(const char *, int);
char * _TLIBC_CDECL_ stpncpy(char *dest, const char *src, size_t n);
int    _TLIBC_CDECL_ strcasecmp(const char *, const char *);
int    _TLIBC_CDECL_ strncasecmp(const char *, const char *, size_t);

int    _TLIBC_CDECL_ ffs(int);
int    _TLIBC_CDECL_ ffsl(long int);
int    _TLIBC_CDECL_ ffsll(long long int);

char * _TLIBC_CDECL_ strtok_r(char *, const char *, char **);
int    _TLIBC_CDECL_ strerror_r(int, char *, size_t);

/*
 * Deprecated Non-C99.
 */
_TLIBC_DEPRECATED_FUNCTION_(char * _TLIBC_CDECL_, strdup, const char *);
_TLIBC_DEPRECATED_FUNCTION_(char * _TLIBC_CDECL_, stpcpy, char *dest, const char *src);

__END_DECLS

#endif /* _STRING_H_ */
