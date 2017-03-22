/*	$OpenBSD: ctype.h,v 1.22 2010/10/01 20:10:24 guenther Exp $	*/
/*	$NetBSD: ctype.h,v 1.14 1994/10/26 00:55:47 cgd Exp $	*/

/*
 * Copyright (c) 1989 The Regents of the University of California.
 * All rights reserved.
 * (c) UNIX System Laboratories, Inc.
 * All or some portions of this file are derived from material licensed
 * to the University of California by American Telephone and Telegraph
 * Co. or Unix System Laboratories, Inc. and are reproduced herein with
 * the permission of UNIX System Laboratories, Inc.
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
 *	@(#)ctype.h	5.3 (Berkeley) 4/3/91
 */

#ifndef _CTYPE_H_
#define _CTYPE_H_

#include <sys/cdefs.h>

__BEGIN_DECLS

int _TLIBC_CDECL_ isalnum(int);
int _TLIBC_CDECL_ isalpha(int);
int _TLIBC_CDECL_ iscntrl(int);
int _TLIBC_CDECL_ isdigit(int);
int _TLIBC_CDECL_ isgraph(int);
int _TLIBC_CDECL_ islower(int);
int _TLIBC_CDECL_ isprint(int);
int _TLIBC_CDECL_ ispunct(int);
int _TLIBC_CDECL_ isspace(int);
int _TLIBC_CDECL_ isupper(int);
int _TLIBC_CDECL_ isxdigit(int);
int _TLIBC_CDECL_ tolower(int);
int _TLIBC_CDECL_ toupper(int);
int _TLIBC_CDECL_ isblank(int);

__END_DECLS

#endif /* _CTYPE_H_ */
