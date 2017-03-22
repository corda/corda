/****************************************************************

The author of this software is David M. Gay.

Copyright (C) 1998 by Lucent Technologies
All Rights Reserved

Permission to use, copy, modify, and distribute this software and
its documentation for any purpose and without fee is hereby
granted, provided that the above copyright notice appear in all
copies and that both that the copyright notice and this
permission notice and warranty disclaimer appear in supporting
documentation, and that the name of Lucent or any of its entities
not be used in advertising or publicity pertaining to
distribution of the software without specific, written prior
permission.

LUCENT DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS.
IN NO EVENT SHALL LUCENT OR ANY OF ITS ENTITIES BE LIABLE FOR ANY
SPECIAL, INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER
IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF
THIS SOFTWARE.

****************************************************************/

/* Please send bug reports to David M. Gay (dmg at acm dot org,
 * with " at " changed at "@" and " dot " changed to ".").	*/

#ifndef GDTOA_H_INCLUDED
#define GDTOA_H_INCLUDED

#include "arith.h"
#include <stddef.h> /* for size_t */

#ifndef Long
#define Long int
#endif
#ifndef ULong
typedef unsigned Long ULong;
#endif
#ifndef UShort
typedef unsigned short UShort;
#endif

#ifndef ANSI
#ifdef KR_headers
#define ANSI(x) ()
#define Void /*nothing*/
#else
#define ANSI(x) x
#define Void void
#endif
#endif /* ANSI */

#ifndef CONST
#ifdef KR_headers
#define CONST /* blank */
#else
#define CONST const
#endif
#endif /* CONST */

 enum {	/* return values from strtodg */
	STRTOG_Zero	= 0x000,
	STRTOG_Normal	= 0x001,
	STRTOG_Denormal	= 0x002,
	STRTOG_Infinite	= 0x003,
	STRTOG_NaN	= 0x004,
	STRTOG_NaNbits	= 0x005,
	STRTOG_NoNumber	= 0x006,
	STRTOG_NoMemory = 0x007,
	STRTOG_Retmask	= 0x00f,

	/* The following may be or-ed into one of the above values. */

	STRTOG_Inexlo	= 0x010, /* returned result rounded toward zero */
	STRTOG_Inexhi	= 0x020, /* returned result rounded away from zero */
	STRTOG_Inexact	= 0x030,
	STRTOG_Underflow= 0x040,
	STRTOG_Overflow	= 0x080,
	STRTOG_Neg	= 0x100 /* does not affect STRTOG_Inexlo or STRTOG_Inexhi */
	};

 typedef struct
FPI {
	int nbits;
	int emin;
	int emax;
	int rounding;
	int sudden_underflow;
	} FPI;

enum {	/* FPI.rounding values: same as FLT_ROUNDS */
	FPI_Round_zero = 0,
	FPI_Round_near = 1,
	FPI_Round_up = 2,
	FPI_Round_down = 3
	};

#ifdef __cplusplus
extern "C" {
#endif

extern char* __dtoa  ANSI((double d, int mode, int ndigits, int *decpt,
			int *sign, char **rve));
extern char* __gdtoa ANSI((FPI *fpi, int be, ULong *bits, int *kindp,
			int mode, int ndigits, int *decpt, char **rve));
extern void __freedtoa ANSI((char*));
extern int __strtodg ANSI((CONST char*, char**, FPI*, Long*, ULong*));
extern int	__strtorx  ANSI((CONST char*, char**, int, void*));

#ifdef __cplusplus
}
#endif
#endif /* GDTOA_H_INCLUDED */
