/*	$OpenBSD: cdefs.h,v 1.34 2012/08/14 20:11:37 matthew Exp $	*/
/*	$NetBSD: cdefs.h,v 1.16 1996/04/03 20:46:39 christos Exp $	*/

/*
 * Copyright (c) 1991, 1993
 *	The Regents of the University of California.  All rights reserved.
 *
 * This code is derived from software contributed to Berkeley by
 * Berkeley Software Design, Inc.
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
 *	@(#)cdefs.h	8.7 (Berkeley) 1/21/94
 */

#ifndef _SYS_CDEFS_H_
#define _SYS_CDEFS_H_

/* Declaration field in C/C++ headers */
#if defined(__cplusplus)
# define __BEGIN_DECLS extern "C" {
# define __END_DECLS }
#else
# define __BEGIN_DECLS
# define __END_DECLS
#endif

#if defined(__STDC__) || defined(__cplusplus) 
# define __CONCAT(x,y)  x ## y
# define __STRING(x)    #x
#else
# define __CONCAT(x,y)  x/**/y
# define __STRING(x)    "x"
#endif
/*
 * Macro to test if we're using a specific version of gcc or later.
 */
#if defined __GNUC__ && defined __GNUC_MINOR_
# define __GNUC_PREREQ__(ma, mi) \
    ((__GNUC__ > (ma)) || (__GNUC__ == (ma) && __GNUC_MINOR__ >= (mi)))
#else
# define __GNUC_PREREQ__(ma, mi) 0
#endif

/* Calling Convention: cdecl */
#define _TLIBC_CDECL_      

/* Thread Directive */
#define _TLIBC_THREAD_     /* __thread */

/* Deprecated Warnings */
#define _TLIBC_DEPRECATED_MSG(x)    __STRING(x)" is deprecated in tlibc."
#define _TLIBC_DEPRECATED_(x)       __attribute__((deprecated(_TLIBC_DEPRECATED_MSG(x))))

#ifndef _TLIBC_WARN_DEPRECATED_FUNCTIONS_
# define _TLIBC_DEPRECATED_FUNCTION_(__ret, __func, ...)
#else
# define _TLIBC_DEPRECATED_FUNCTION_(__ret, __func, ...)    \
    _TLIBC_DEPRECATED_(__func)  \
    __ret __func(__VA_ARGS__)
#endif

/* Static analysis for printf format strings.
 * _MSC_PRINTF_FORMAT_: MSVC SAL annotation for specifying format strings. 
 * _GCC_PRINTF_FORMAT_(x, y): GCC declaring attribute for checking format strings.
 *   x - index of the format string. In C++ non-static method, index 1 is reseved for 'this'.
 *   y - index of first variadic agrument in '...'.
 */
#define _GCC_PRINTF_FORMAT_(x, y)  __attribute__((__format__ (printf, x, y)))

/* Attribute - noreturn */
#define _TLIBC_NORETURN_   __attribute__ ((__noreturn__))

/*
 * GNU C version 2.96 adds explicit branch prediction so that
 * the CPU back-end can hint the processor and also so that
 * code blocks can be reordered such that the predicted path
 * sees a more linear flow, thus improving cache behavior, etc.
 *
 * The following two macros provide us with a way to utilize this
 * compiler feature.  Use __predict_true() if you expect the expression
 * to evaluate to true, and __predict_false() if you expect the
 * expression to evaluate to false.
 *
 * A few notes about usage:
 *
 *	* Generally, __predict_false() error condition checks (unless
 *	  you have some _strong_ reason to do otherwise, in which case
 *	  document it), and/or __predict_true() `no-error' condition
 *	  checks, assuming you want to optimize for the no-error case.
 *
 *	* Other than that, if you don't know the likelihood of a test
 *	  succeeding from empirical or other `hard' evidence, don't
 *	  make predictions.
 *
 *	* These are meant to be used in places that are run `a lot'.
 *	  It is wasteful to make predictions in code that is run
 *	  seldomly (e.g. at subsystem initialization time) as the
 *	  basic block reordering that this affects can often generate
 *	  larger code.
 */
#if defined(__GNUC__) && __GNUC_PREREQ__(2, 96)
#define __predict_true(exp)	__builtin_expect(((exp) != 0), 1)
#define __predict_false(exp)	__builtin_expect(((exp) != 0), 0)
#else
#define __predict_true(exp)	((exp) != 0)
#define __predict_false(exp)	((exp) != 0)
#endif

#endif /* !_SYS_CDEFS_H_ */
