/*	$OpenBSD: stdio.h,v 1.38 2009/11/09 00:18:27 kurt Exp $	*/
/*	$NetBSD: stdio.h,v 1.18 1996/04/25 18:29:21 jtc Exp $	*/

/*-
 * Copyright (c) 1990 The Regents of the University of California.
 * All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * Chris Torek.
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
 *	@(#)stdio.h	5.17 (Berkeley) 6/3/91
 */

#ifndef _STDIO_H_
#define _STDIO_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

#include <stdarg.h>

#ifndef _SIZE_T_DEFINED_
typedef __size_t    size_t;
#define _SIZE_T_DEFINED_
#endif

#ifndef NULL
# ifdef __cplusplus
#  define NULL      0
# else
#  define NULL      ((void *)0)
# endif
#endif

# define BUFSIZ  8192

#define EOF     (-1)

__BEGIN_DECLS

int _TLIBC_CDECL_ snprintf(char *, size_t, const char *, ...) _GCC_PRINTF_FORMAT_(3, 4);
int _TLIBC_CDECL_ vsnprintf(char *, size_t, const char *, __va_list) _GCC_PRINTF_FORMAT_(3, 0);

/*
 * Deprecated definitions.
 */
#if 0 /* No FILE */
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, fprintf, FILE *, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, putc, int, FILE *);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, fputc, int, FILE *);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, fputs, const char *, FILE *);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, fscanf, FILE *, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(size_t _TLIBC_CDECL_, fwrite, const void *, size_t, size_t, FILE *);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, printf, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, putchar, int);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, puts, const char *);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, scanf, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, sprintf, char *, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, sscanf, const char *, const char *, ...);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vfprintf, FILE *, const char *, __va_list);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vfscanf, FILE *, const char *, __va_list);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vprintf, const char *, __va_list);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vscanf, const char *, __va_list);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vsprintf, char *, const char *, __va_list);
_TLIBC_DEPRECATED_FUNCTION_(int _TLIBC_CDECL_, vsscanf, const char *, const char *, __va_list);
#endif

__END_DECLS


#endif /* !_STDIO_H_ */
