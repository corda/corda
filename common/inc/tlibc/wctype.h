/*	$OpenBSD: wctype.h,v 1.5 2006/01/06 18:53:04 millert Exp $	*/
/*	$NetBSD: wctype.h,v 1.5 2003/03/02 22:18:11 tshiozak Exp $	*/

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
 *
 *	citrus Id: wctype.h,v 1.4 2000/12/21 01:50:21 itojun Exp
 */

#ifndef _WCTYPE_H_
#define _WCTYPE_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

#ifndef _WINT_T_DEFINED_
#define _WINT_T_DEFINED_
typedef __wint_t    wint_t;
#endif

#ifndef _WCTRANS_T_DEFINED_
#define _WCTRANS_T_DEFINED_
typedef __wctrans_t wctrans_t;
#endif

#ifndef _WCTYPE_T_DEFINED_
#define _WCTYPE_T_DEFINED_
typedef __wctype_t  wctype_t;
#endif

#ifndef WEOF
#define WEOF    ((wint_t)-1)
#endif

__BEGIN_DECLS

/*
 * Deprecated definitions.
 */
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswalnum, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswalpha, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswblank, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswcntrl, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswdigit, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswgraph, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswlower, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswprint, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswpunct, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswspace, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswupper, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswxdigit, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(int         _TLIBC_CDECL_, iswctype, wint_t, wctype_t);
_TLIBC_DEPRECATED_FUNCTION_(wint_t      _TLIBC_CDECL_, towctrans, wint_t, wctrans_t);
_TLIBC_DEPRECATED_FUNCTION_(wint_t      _TLIBC_CDECL_, towlower, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(wint_t      _TLIBC_CDECL_, towupper, wint_t);
_TLIBC_DEPRECATED_FUNCTION_(wctrans_t   _TLIBC_CDECL_, wctrans, const char *);
_TLIBC_DEPRECATED_FUNCTION_(wctype_t    _TLIBC_CDECL_, wctype, const char *);

__END_DECLS

#endif /* _WCTYPE_H_ */
