/* $OpenBSD: ctype_private.h,v 1.1 2005/08/08 05:53:00 espie Exp $ */
/* Written by Marc Espie, public domain */

#define _U  0x01
#define _L  0x02
#define _N  0x04
#define _S  0x08
#define _P  0x10
#define _C  0x20
#define _X  0x40
#define _B  0x80

#define CTYPE_NUM_CHARS       256
extern const char _C_ctype_[];
extern const short _C_toupper_[];
extern const short _C_tolower_[];

extern const char   *_ctype_;
extern const short  *_tolower_tab_;
extern const short  *_toupper_tab_;

