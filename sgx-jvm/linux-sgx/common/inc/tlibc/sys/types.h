/*	$OpenBSD: types.h,v 1.31 2008/03/16 19:42:57 otto Exp $	*/
/*	$NetBSD: types.h,v 1.29 1996/11/15 22:48:25 jtc Exp $	*/

/*-
 * Copyright (c) 1982, 1986, 1991, 1993
 *	The Regents of the University of California.  All rights reserved.
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
 *	@(#)types.h	8.4 (Berkeley) 1/21/94
 */

#ifndef _SYS_TYPES_H_
#define _SYS_TYPES_H_

#include <sys/_types.h>
#include <sys/endian.h>

typedef unsigned char   u_char;
typedef unsigned short  u_short;
typedef unsigned int    u_int;
typedef unsigned long   u_long;

typedef unsigned char   unchar;     /* Sys V compatibility */
typedef unsigned short  ushort;     /* Sys V compatibility */
typedef unsigned int    uint;       /* Sys V compatibility */
typedef unsigned long   ulong;      /* Sys V compatibility */

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

#ifndef _INTPTR_T_DEFINED_
#define _INTPTR_T_DEFINED_
typedef __intptr_t      intptr_t;
#endif

#ifndef _UINTPTR_T_DEFINED_
#define _UINTPTR_T_DEFINED_
typedef __uintptr_t     uintptr_t;
#endif

/* BSD-style unsigned bits types */
typedef __uint8_t       u_int8_t;
typedef __uint16_t      u_int16_t;
typedef __uint32_t      u_int32_t;
typedef __uint64_t      u_int64_t;


#ifndef _SIZE_T_DEFINED_
#define _SIZE_T_DEFINED_
typedef __size_t    size_t;
#endif

#ifndef _SSIZE_T_DEFINED_
#define _SSIZE_T_DEFINED_
typedef __ssize_t   ssize_t;
#endif

#ifndef _OFF_T_DEFINED_
#define _OFF_T_DEFINED_
typedef __off_t     off_t;
#endif

#endif /* !_SYS_TYPES_H_ */
