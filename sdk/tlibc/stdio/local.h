/*	$OpenBSD: local.h,v 1.20 2011/11/08 18:30:42 guenther Exp $	*/

/*-
 * Copyright (c) 1990, 1993
 *	The Regents of the University of California.  All rights reserved.
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
 */

/*
 * Information local to this implementation of stdio,
 * in particular, macros and private variables.
 */

#include <wchar.h> 
#include "wcio.h"

#include "internal/arch.h" /* for SE_PAGE_SIZE */

#define FLOATING_POINT      1
#define PRINTF_WIDE_CHAR    1

/*
 * NB: to fit things in six character monocase externals, the stdio
 * code uses the prefix `__s' for stdio objects, typically followed
 * by a three-character attempt at a mnemonic.
 */

/* stdio buffers */
struct __sbuf {
    unsigned char *_base;
    int _size;
};

/*
 * stdio state variables.
 */
typedef struct __sFILE {
    unsigned char *_p;  /* current position in (some) buffer */
    int _r;             /* read space left for getc() */
    int _w;             /* write space left for putc() */
    short _flags;       /* flags, below; this FILE is free if 0 */
    short _file;        /* fileno, if Unix descriptor, else -1 */
    struct __sbuf _bf;  /* the buffer (at least 1 byte, if !NULL) */

    /* operations */
    int (*_read)(void *, char *, int); /* may for sscanf */

    /* extension data, to avoid further ABI breakage */
    struct __sbuf _ext;
} FILE;

#define __SLBF  0x0001      /* line buffered */
#define __SNBF  0x0002      /* unbuffered */
#define __SRD   0x0004      /* OK to read */
#define __SWR   0x0008      /* OK to write */
/* RD and WR are never simultaneously asserted */
#define __SRW   0x0010      /* open for reading & writing */
#define __SEOF  0x0020      /* found EOF */
#define __SERR  0x0040      /* found error */
#define __SMBF  0x0080      /* _buf is from malloc */
#define __SSTR  0x0200      /* this is an sprintf/snprintf string */
#define __SALC  0x4000      /* allocate string space dynamically */

#include "fileext.h"

int	__vfprintf(FILE *, const char *, __va_list);
int __vfwprintf(FILE *, const wchar_t *, __va_list);

#define __sferror(p)    (((p)->_flags & __SERR) != 0)

/*
 * Return true if the given FILE cannot be written now.
 */
#define	cantwrite(fp) 0 /* alreays writable for char array APIs  */

/* Disable warnings */
