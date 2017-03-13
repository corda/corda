/* $OpenBSD: limits.h,v 1.8 2009/11/27 19:54:35 guenther Exp $ */
/*
 * Copyright (c) 2002 Marc Espie.
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
 * THIS SOFTWARE IS PROVIDED BY THE OPENBSD PROJECT AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OPENBSD
 * PROJECT OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
#ifndef _SYS_LIMITS_H_
#define _SYS_LIMITS_H_

#include <sys/cdefs.h>

/* Common definitions for limits.h. */

#define CHAR_BIT    8                           /* number of bits in a char */

#define SCHAR_MAX   0x7f                        /* max value for a signed char */
#define SCHAR_MIN   (-0x7f - 1)                 /* min value for a signed char */

#define UCHAR_MAX   0xff                        /* max value for an unsigned char */
#ifdef __CHAR_UNSIGNED__
# define CHAR_MIN   0                           /* min value for a char */
# define CHAR_MAX   0xff                        /* max value for a char */
#else
# define CHAR_MAX   0x7f
# define CHAR_MIN   (-0x7f-1)
#endif

#define MB_LEN_MAX  1                           /* Allow UTF-8 (RFC 3629) */

#define USHRT_MAX   0xffff                      /* max value for an unsigned short */
#define SHRT_MAX    0x7fff                      /* max value for a short */
#define SHRT_MIN    (-0x7fff-1)                 /* min value for a short */

#define UINT_MAX    0xffffffffU                 /* max value for an unsigned int */
#define INT_MAX     0x7fffffff                  /* max value for an int */
#define INT_MIN     (-0x7fffffff-1)             /* min value for an int */

#ifdef __x86_64__
# define ULONG_MAX  0xffffffffffffffffUL        /* max value for unsigned long */
# define LONG_MAX   0x7fffffffffffffffL         /* max value for a signed long */
# define LONG_MIN   (-0x7fffffffffffffffL-1)    /* min value for a signed long */
#else
# define ULONG_MAX  0xffffffffUL                /* max value for an unsigned long */
# define LONG_MAX   0x7fffffffL                 /* max value for a long */
# define LONG_MIN   (-0x7fffffffL-1)            /* min value for a long */
#endif

#define ULLONG_MAX  0xffffffffffffffffULL       /* max value for unsigned long long */
#define LLONG_MAX   0x7fffffffffffffffLL        /* max value for a signed long long */
#define LLONG_MIN   (-0x7fffffffffffffffLL-1)   /* min value for a signed long long */

#ifdef __x86_64__
# define LONG_BIT   64
#else
# define LONG_BIT   32
#endif

#endif /* !_SYS_LIMITS_H_ */
