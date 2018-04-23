/*	$OpenBSD: time.h,v 1.18 2006/01/06 18:53:04 millert Exp $	*/
/*	$NetBSD: time.h,v 1.9 1994/10/26 00:56:35 cgd Exp $	*/

/*
 * Copyright (c) 1989 The Regents of the University of California.
 * All rights reserved.
 *
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
 *	@(#)time.h	5.12 (Berkeley) 3/9/91
 */

#ifndef _TIME_H_
#define _TIME_H_

#include <sys/cdefs.h>
#include <sys/_types.h>

#ifndef NULL
#ifdef __cplusplus
#define NULL    0
#else
#define NULL    ((void *)0)
#endif
#endif

#if !defined (_CLOCK_T_DEFINED_) && !defined (_CLOCK_T_DEFINED)
#define _CLOCK_T_DEFINED_
#define _CLOCK_T_DEFINED
typedef __clock_t   clock_t;
#endif

#if !defined (_TIME_T_DEFINED_) && !defined (_TIME_T_DEFINED)
#define _TIME_T_DEFINED_
#define _TIME_T_DEFINED
typedef __time_t    time_t;
#endif

#if !defined (_SIZE_T_DEFINED_) && !defined (_SIZE_T_DEFINED)
#define _SIZE_T_DEFINED_
#define _SIZE_T_DEFINED
typedef __size_t    size_t;
#endif

#if !defined (_TM_DEFINED)
#define _TM_DEFINED
struct tm {
    int tm_sec;     /* seconds after the minute [0-60] */
    int tm_min;     /* minutes after the hour [0-59] */
    int tm_hour;    /* hours since midnight [0-23] */
    int tm_mday;    /* day of the month [1-31] */
    int tm_mon;     /* months since January [0-11] */
    int tm_year;    /* years since 1900 */
    int tm_wday;    /* days since Sunday [0-6] */
    int tm_yday;    /* days since January 1 [0-365] */
    int tm_isdst;   /* Daylight Saving Time flag */
    /* FIXME: naming issue exists on Fedora/Ubuntu */
    long tm_gmtoff; /* offset from UTC in seconds */
    char *tm_zone;  /* timezone abbreviation */
};
#endif

__BEGIN_DECLS

double _TLIBC_CDECL_ difftime(time_t, time_t);
char * _TLIBC_CDECL_ asctime(const struct tm *);
size_t _TLIBC_CDECL_ strftime(char *, size_t, const char *, const struct tm *);

/*
 * Non-C99
 */
char * _TLIBC_CDECL_ asctime_r(const struct tm *, char *);

__END_DECLS

#endif /* !_TIME_H_ */
