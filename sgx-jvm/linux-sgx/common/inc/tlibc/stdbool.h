/* $OpenBSD: stdbool.h,v 1.5 2010/07/24 22:17:03 guenther Exp $ */

/*
 * Written by Marc Espie, September 25, 1999
 * Public domain.
 */

#ifndef _STDBOOL_H_
#define _STDBOOL_H_

#ifndef __cplusplus

#ifndef __GNUC__
/* Support for _C99: type _Bool is already built-in. */
/* `_Bool' type must promote to `int' or `unsigned int'. */
typedef enum {
    false = 0,
    true = 1
} _Bool;

/* And those constants must also be available as macros. */
# define false   false
# define true    true
#else  /* __GNUC__ */
# define false   0
# define true    1
#endif

/* User visible type `bool' is provided as a macro which may be redefined */
#define bool _Bool

#else /* __cplusplus */

# define _Bool   bool
# define bool    bool
# define false   false
# define true    true

#endif

/* Inform that everything is fine */
#define __bool_true_false_are_defined 1

#endif /* _STDBOOL_H_ */
