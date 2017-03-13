/*	$OpenBSD: wchar.h,v 1.11 2010/07/24 09:58:39 guenther Exp $	*/
/*	$NetBSD: wchar.h,v 1.16 2003/03/07 07:11:35 tshiozak Exp $	*/

/*-
 * Copyright (c)1999 Citrus Project,
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
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

/*-
 * Copyright (c) 1999, 2000 The NetBSD Foundation, Inc.
 * All rights reserved.
 *
 * This code is derived from software contributed to The NetBSD Foundation
 * by Julian Coleman.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE NETBSD FOUNDATION, INC. AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#ifndef _WCHAR_H_
#define _WCHAR_H_

#include <sys/cdefs.h>
#include <sys/_types.h>
#include <sys/stdint.h> /* WCHAR_MAX/WCHAR_MIN */

#ifndef NULL
#ifdef __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

#if !defined(_WCHAR_T_DEFINED_) && !defined(__cplusplus)
#define _WCHAR_T_DEFINED_
#ifndef __WCHAR_TYPE__
#define __WCHAR_TYPE__ int
#endif
typedef __WCHAR_TYPE__ wchar_t;
#endif

#ifndef _MBSTATE_T_DEFINED_
#define _MBSTATE_T_DEFINED_
typedef __mbstate_t mbstate_t;
#endif

#ifndef _WINT_T_DEFINED_
#define _WINT_T_DEFINED_
typedef __wint_t    wint_t;
#endif

#ifndef _SIZE_T_DEFINED_
#define _SIZE_T_DEFINED_
typedef __size_t    size_t;
#endif

#ifndef WEOF
#define WEOF    ((wint_t)-1)
#endif

__BEGIN_DECLS

wint_t      _TLIBC_CDECL_ btowc(int);
int         _TLIBC_CDECL_ wctob(wint_t);
size_t      _TLIBC_CDECL_ mbrlen(const char *, size_t, mbstate_t *);
size_t      _TLIBC_CDECL_ mbrtowc(wchar_t *, const char *, size_t, mbstate_t *);
int         _TLIBC_CDECL_ mbsinit(const mbstate_t *);
size_t      _TLIBC_CDECL_ mbsrtowcs(wchar_t *, const char **, size_t, mbstate_t *);
size_t      _TLIBC_CDECL_ wcrtomb(char *, wchar_t, mbstate_t *);
wchar_t *   _TLIBC_CDECL_ wcschr(const wchar_t *, wchar_t);
int         _TLIBC_CDECL_ wcscmp(const wchar_t *, const wchar_t *);
int         _TLIBC_CDECL_ wcscoll(const wchar_t *, const wchar_t *);
size_t      _TLIBC_CDECL_ wcscspn(const wchar_t *, const wchar_t *);
size_t      _TLIBC_CDECL_ wcslen(const wchar_t *);
wchar_t *   _TLIBC_CDECL_ wcsncat(wchar_t *, const wchar_t *, size_t);
int         _TLIBC_CDECL_ wcsncmp(const wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wcsncpy(wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wcspbrk(const wchar_t *, const wchar_t *);
wchar_t *   _TLIBC_CDECL_ wcsrchr(const wchar_t *, wchar_t);
size_t      _TLIBC_CDECL_ wcsrtombs(char *, const wchar_t **, size_t, mbstate_t *);
size_t      _TLIBC_CDECL_ wcsspn(const wchar_t *, const wchar_t *);
wchar_t *   _TLIBC_CDECL_ wcsstr(const wchar_t *, const wchar_t *);
wchar_t *   _TLIBC_CDECL_ wcstok(wchar_t *, const wchar_t *, wchar_t **);
size_t      _TLIBC_CDECL_ wcsxfrm(wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wmemchr(const wchar_t *, wchar_t, size_t);
int         _TLIBC_CDECL_ wmemcmp(const wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wmemcpy(wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wmemmove(wchar_t *, const wchar_t *, size_t);
wchar_t *   _TLIBC_CDECL_ wmemset(wchar_t *, wchar_t, size_t);

int         _TLIBC_CDECL_ swprintf(wchar_t *, size_t, const wchar_t *, ...);
int         _TLIBC_CDECL_ vswprintf(wchar_t *, size_t, const wchar_t *, __va_list);

/* leagcy version of wcsstr */
wchar_t *   _TLIBC_CDECL_ wcswcs(const wchar_t *, const wchar_t *);

__END_DECLS

#endif /* !_WCHAR_H_ */
