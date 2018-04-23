/*	$OpenBSD: math.h,v 1.27 2010/12/14 11:16:15 martynas Exp $	*/
/*
 * ====================================================
 * Copyright (C) 1993 by Sun Microsystems, Inc. All rights reserved.
 *
 * Developed at SunPro, a Sun Microsystems, Inc. business.
 * Permission to use, copy, modify, and distribute this
 * software is freely granted, provided that this notice 
 * is preserved.
 * ====================================================
 */

/*
 * from: @(#)fdlibm.h 5.1 93/09/24
 */

#ifndef _MATH_H_
#define _MATH_H_

#include <sys/_types.h>
#include <sys/cdefs.h>
#include <sys/limits.h>

#include <float.h>

typedef __float_t       float_t;
typedef __double_t      double_t;

#define FP_NAN         0x00
#define FP_INFINITE    0x01
#define FP_ZERO        0x02
#define FP_SUBNORMAL   0x03
#define FP_NORMAL      0x04

#define FP_ILOGB0       (-INT_MAX - 1)
#define FP_ILOGBNAN     (-INT_MAX - 1)

#define fpclassify(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __fpclassifyf(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __fpclassify(x) \
    :   __fpclassifyl(x))
#define isfinite(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __isfinitef(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __isfinite(x) \
    :   __isfinitel(x))
#define isnormal(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __isnormalf(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __isnormal(x) \
    :   __isnormall(x))
#define signbit(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __signbitf(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __signbit(x) \
    :   __signbitl(x))
#define isinf(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __isinff(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __isinf(x) \
    :   __isinfl(x))
#define isnan(x) \
    ((sizeof (x) == sizeof (float)) ? \
        __isnanf(x) \
    : (sizeof (x) == sizeof (double)) ? \
        __isnan(x) \
    :   __isnanl(x))

#define isgreater(x, y)         (!isunordered((x), (y)) && (x) > (y))
#define isgreaterequal(x, y)    (!isunordered((x), (y)) && (x) >= (y))
#define isless(x, y)            (!isunordered((x), (y)) && (x) < (y))
#define islessequal(x, y)       (!isunordered((x), (y)) && (x) <= (y))
#define islessgreater(x, y)     (!isunordered((x), (y)) && ((x) > (y) || (y) > (x)))
#define isunordered(x, y)       (isnan(x) || isnan(y))

__BEGIN_DECLS

extern char __infinity[];
#define HUGE_VAL    (*(double *)(void *)__infinity)
#define HUGE_VALF   ((float)HUGE_VAL)
#define HUGE_VALL   ((long double)HUGE_VAL)
#define INFINITY    HUGE_VALF
extern char __nan[];
#define NAN         (*(float *)(void *)__nan)

/*
 * ANSI/POSIX
 */
double _TLIBC_CDECL_ acos(double);
double _TLIBC_CDECL_ asin(double);
double _TLIBC_CDECL_ atan(double);
double _TLIBC_CDECL_ atan2(double, double);
double _TLIBC_CDECL_ cos(double);
double _TLIBC_CDECL_ sin(double);
double _TLIBC_CDECL_ tan(double);

double _TLIBC_CDECL_ cosh(double);
double _TLIBC_CDECL_ sinh(double);
double _TLIBC_CDECL_ tanh(double);

double _TLIBC_CDECL_ exp(double);
double _TLIBC_CDECL_ frexp(double, int *);
double _TLIBC_CDECL_ ldexp(double, int);
double _TLIBC_CDECL_ log(double);
double _TLIBC_CDECL_ log10(double);
double _TLIBC_CDECL_ modf(double, double *);

double _TLIBC_CDECL_ pow(double, double);
double _TLIBC_CDECL_ sqrt(double);

double _TLIBC_CDECL_ ceil(double);
double _TLIBC_CDECL_ fabs(double);
double _TLIBC_CDECL_ floor(double);
double _TLIBC_CDECL_ fmod(double, double);

/*
 * C99
 */
double _TLIBC_CDECL_ acosh(double);
double _TLIBC_CDECL_ asinh(double);
double _TLIBC_CDECL_ atanh(double);

double _TLIBC_CDECL_ exp2(double);  
double _TLIBC_CDECL_ expm1(double);
int    _TLIBC_CDECL_ ilogb(double);
double _TLIBC_CDECL_ log1p(double);
double _TLIBC_CDECL_ log2(double);
double _TLIBC_CDECL_ logb(double);
double _TLIBC_CDECL_ scalbn(double, int);
double _TLIBC_CDECL_ scalbln(double, long int); 

double _TLIBC_CDECL_ cbrt(double);
double _TLIBC_CDECL_ hypot(double, double);

double _TLIBC_CDECL_ erf(double);
double _TLIBC_CDECL_ erfc(double);
double _TLIBC_CDECL_ lgamma(double);
double _TLIBC_CDECL_ tgamma(double);

double _TLIBC_CDECL_ nearbyint(double);
double _TLIBC_CDECL_ rint(double);
long int _TLIBC_CDECL_ lrint(double); 
long long int _TLIBC_CDECL_ llrint(double); 
double _TLIBC_CDECL_ round(double);  
long int _TLIBC_CDECL_ lround(double); 
long long int _TLIBC_CDECL_ llround(double);
double _TLIBC_CDECL_ trunc(double);

double _TLIBC_CDECL_ remainder(double, double);
double _TLIBC_CDECL_ remquo(double, double, int *); 

double _TLIBC_CDECL_ copysign(double, double);
double _TLIBC_CDECL_ nan(const char *);
double _TLIBC_CDECL_ nextafter(double, double);

double _TLIBC_CDECL_ fdim(double, double); 
double _TLIBC_CDECL_ fmax(double, double); 
double _TLIBC_CDECL_ fmin(double, double); 

double _TLIBC_CDECL_ fma(double, double, double);

/*
 * Float versions of C99 functions
 */

float _TLIBC_CDECL_ acosf(float);
float _TLIBC_CDECL_ asinf(float);
float _TLIBC_CDECL_ atanf(float);
float _TLIBC_CDECL_ atan2f(float, float);
float _TLIBC_CDECL_ cosf(float);
float _TLIBC_CDECL_ sinf(float);
float _TLIBC_CDECL_ tanf(float);

float _TLIBC_CDECL_ acoshf(float);
float _TLIBC_CDECL_ asinhf(float);
float _TLIBC_CDECL_ atanhf(float);
float _TLIBC_CDECL_ coshf(float);
float _TLIBC_CDECL_ sinhf(float);
float _TLIBC_CDECL_ tanhf(float);

float _TLIBC_CDECL_ expf(float);
float _TLIBC_CDECL_ exp2f(float); 
float _TLIBC_CDECL_ expm1f(float); 
float _TLIBC_CDECL_ frexpf(float, int *);
int   _TLIBC_CDECL_ ilogbf(float);
float _TLIBC_CDECL_ ldexpf(float, int);
float _TLIBC_CDECL_ logf(float);
float _TLIBC_CDECL_ log10f(float);
float _TLIBC_CDECL_ log1pf(float);
float _TLIBC_CDECL_ log2f(float);
float _TLIBC_CDECL_ logbf(float);
float _TLIBC_CDECL_ modff(float, float *);
float _TLIBC_CDECL_ scalbnf(float, int);
float _TLIBC_CDECL_ scalblnf(float, long int);

float _TLIBC_CDECL_ cbrtf(float);
float _TLIBC_CDECL_ fabsf(float);
float _TLIBC_CDECL_ hypotf(float, float);
float _TLIBC_CDECL_ powf(float, float);
float _TLIBC_CDECL_ sqrtf(float);

float _TLIBC_CDECL_ erff(float);
float _TLIBC_CDECL_ erfcf(float);
float _TLIBC_CDECL_ lgammaf(float);
float _TLIBC_CDECL_ tgammaf(float);

float _TLIBC_CDECL_ ceilf(float);
float _TLIBC_CDECL_ floorf(float);
float _TLIBC_CDECL_ nearbyintf(float);

float _TLIBC_CDECL_ rintf(float);
long int _TLIBC_CDECL_ lrintf(float); 
long long int _TLIBC_CDECL_ llrintf(float); 
float _TLIBC_CDECL_ roundf(float); 
long int _TLIBC_CDECL_ lroundf(float);
long long int _TLIBC_CDECL_ llroundf(float);
float _TLIBC_CDECL_ truncf(float);

float _TLIBC_CDECL_ fmodf(float, float);
float _TLIBC_CDECL_ remainderf(float, float);
float _TLIBC_CDECL_ remquof(float, float, int *);

float _TLIBC_CDECL_ copysignf(float, float);
float _TLIBC_CDECL_ nanf(const char *);
float _TLIBC_CDECL_ nextafterf(float, float);

float _TLIBC_CDECL_ fdimf(float, float);
float _TLIBC_CDECL_ fmaxf(float, float);
float _TLIBC_CDECL_ fminf(float, float);

float _TLIBC_CDECL_ fmaf(float, float, float);

/*
 * Long double versions of C99 functions
 */

/* Macros defining long double functions to be their double counterparts
 * (long double is synonymous with double in this implementation).
 */

long double _TLIBC_CDECL_ acosl(long double);
long double _TLIBC_CDECL_ asinl(long double);
long double _TLIBC_CDECL_ atanl(long double);
long double _TLIBC_CDECL_ atan2l(long double, long double);
long double _TLIBC_CDECL_ cosl(long double);
long double _TLIBC_CDECL_ sinl(long double);
long double _TLIBC_CDECL_ tanl(long double);

long double _TLIBC_CDECL_ acoshl(long double);
long double _TLIBC_CDECL_ asinhl(long double);
long double _TLIBC_CDECL_ atanhl(long double);
long double _TLIBC_CDECL_ coshl(long double);
long double _TLIBC_CDECL_ sinhl(long double);
long double _TLIBC_CDECL_ tanhl(long double);

long double _TLIBC_CDECL_ expl(long double);
long double _TLIBC_CDECL_ exp2l(long double);
long double _TLIBC_CDECL_ expm1l(long double);
long double _TLIBC_CDECL_ frexpl(long double, int *);
int         _TLIBC_CDECL_ ilogbl(long double);
long double _TLIBC_CDECL_ ldexpl(long double, int);
long double _TLIBC_CDECL_ logl(long double);
long double _TLIBC_CDECL_ log10l(long double);
long double _TLIBC_CDECL_ log1pl(long double);
long double _TLIBC_CDECL_ log2l(long double);
long double _TLIBC_CDECL_ logbl(long double);
long double _TLIBC_CDECL_ modfl(long double, long double *);
long double _TLIBC_CDECL_ scalbnl(long double, int);
long double _TLIBC_CDECL_ scalblnl(long double, long int);

long double _TLIBC_CDECL_ cbrtl(long double);
long double _TLIBC_CDECL_ fabsl(long double);
long double _TLIBC_CDECL_ hypotl(long double, long double);
long double _TLIBC_CDECL_ powl(long double, long double);
long double _TLIBC_CDECL_ sqrtl(long double);

long double _TLIBC_CDECL_ erfl(long double);
long double _TLIBC_CDECL_ erfcl(long double);
long double _TLIBC_CDECL_ lgammal(long double);
long double _TLIBC_CDECL_ tgammal(long double);

long double _TLIBC_CDECL_ ceill(long double);
long double _TLIBC_CDECL_ floorl(long double);
long double _TLIBC_CDECL_ nearbyintl(long double);
long double _TLIBC_CDECL_ rintl(long double);
long int    _TLIBC_CDECL_ lrintl(long double);
long long int _TLIBC_CDECL_ llrintl(long double);
long double _TLIBC_CDECL_ roundl(long double);
long int    _TLIBC_CDECL_ lroundl(long double);
long long int _TLIBC_CDECL_ llroundl(long double);
long double _TLIBC_CDECL_ truncl(long double);

long double _TLIBC_CDECL_ fmodl(long double, long double);
long double _TLIBC_CDECL_ remainderl(long double, long double);
long double _TLIBC_CDECL_ remquol(long double, long double, int *);

long double _TLIBC_CDECL_ copysignl(long double, long double);
long double _TLIBC_CDECL_ nanl(const char *);
long double _TLIBC_CDECL_ nextafterl(long double, long double);

long double _TLIBC_CDECL_ fdiml(long double, long double);
long double _TLIBC_CDECL_ fmaxl(long double, long double);
long double _TLIBC_CDECL_ fminl(long double, long double);
long double _TLIBC_CDECL_ fmal(long double, long double, long double);

/* nexttoward():
*      The implementation in Intel math library is incompatible with MSVC.
*      Because sizeof(long double) is 8bytes with MSVC, 
*      but the expected long double size is 10bytes. 
*      And by default, MSVC doesn't provide nexttoward(). 
*      So we only provide Linux version here.
*/
double _TLIBC_CDECL_ nexttoward(double, long double);
float  _TLIBC_CDECL_ nexttowardf(float, long double);

long double _TLIBC_CDECL_ nexttowardl(long double, long double);

/*
 * Library implementation
 */
int _TLIBC_CDECL_ __fpclassify(double);
int _TLIBC_CDECL_ __fpclassifyf(float);
int _TLIBC_CDECL_ __isfinite(double);
int _TLIBC_CDECL_ __isfinitef(float);
int _TLIBC_CDECL_ __isinf(double);
int _TLIBC_CDECL_ __isinff(float);
int _TLIBC_CDECL_ __isnan(double);
int _TLIBC_CDECL_ __isnanf(float);
int _TLIBC_CDECL_ __isnormal(double);
int _TLIBC_CDECL_ __isnormalf(float);
int _TLIBC_CDECL_ __signbit(double);
int _TLIBC_CDECL_ __signbitf(float);

int _TLIBC_CDECL_ __fpclassifyl(long double);
int _TLIBC_CDECL_ __isfinitel(long double);
int _TLIBC_CDECL_ __isinfl(long double);
int _TLIBC_CDECL_ __isnanl(long double);
int _TLIBC_CDECL_ __isnormall(long double);
int _TLIBC_CDECL_ __signbitl(long double);

/* 
 * Non-C99 functions.
 */
double _TLIBC_CDECL_ drem(double, double);
double _TLIBC_CDECL_ exp10(double);
double _TLIBC_CDECL_ gamma(double);
double _TLIBC_CDECL_ gamma_r(double, int *);
double _TLIBC_CDECL_ j0(double);
double _TLIBC_CDECL_ j1(double);
double _TLIBC_CDECL_ jn(int, double);
double _TLIBC_CDECL_ lgamma_r(double, int *);
double _TLIBC_CDECL_ pow10(double);
double _TLIBC_CDECL_ scalb(double, double);
/* C99 Macro signbit.*/
double _TLIBC_CDECL_ significand(double);
void   _TLIBC_CDECL_ sincos(double, double *, double *);
double _TLIBC_CDECL_ y0(double);
double _TLIBC_CDECL_ y1(double);
double _TLIBC_CDECL_ yn(int, double);
/* C99 Macro isinf.*/
/* C99 Macro isnan.*/
int    _TLIBC_CDECL_ finite(double);

float _TLIBC_CDECL_ dremf(float, float);
float _TLIBC_CDECL_ exp10f(float);
float _TLIBC_CDECL_ gammaf(float);
float _TLIBC_CDECL_ gammaf_r(float, int *);
float _TLIBC_CDECL_ j0f(float);
float _TLIBC_CDECL_ j1f(float);
float _TLIBC_CDECL_ jnf(int, float);
float _TLIBC_CDECL_ lgammaf_r(float, int *);
float _TLIBC_CDECL_ pow10f(float);
float _TLIBC_CDECL_ scalbf(float, float);
int   _TLIBC_CDECL_ signbitf(float);
float _TLIBC_CDECL_ significandf(float);
void  _TLIBC_CDECL_ sincosf(float, float *, float *);
float _TLIBC_CDECL_ y0f(float);
float _TLIBC_CDECL_ y1f(float);
float _TLIBC_CDECL_ ynf(int, float);
int   _TLIBC_CDECL_ finitef(float);
int   _TLIBC_CDECL_ isinff(float);
int   _TLIBC_CDECL_ isnanf(float);

long double _TLIBC_CDECL_ dreml(long double, long double);
long double _TLIBC_CDECL_ exp10l(long double);
long double _TLIBC_CDECL_ gammal(long double);
long double _TLIBC_CDECL_ gammal_r(long double, int *);
long double _TLIBC_CDECL_ j0l(long double);
long double _TLIBC_CDECL_ j1l(long double);
long double _TLIBC_CDECL_ jnl(int, long double);
long double _TLIBC_CDECL_ lgammal_r(long double, int *);
long double _TLIBC_CDECL_ pow10l(long double);
long double _TLIBC_CDECL_ scalbl(long double, long double);
int         _TLIBC_CDECL_ signbitl(long double);
long double _TLIBC_CDECL_ significandl(long double);
void        _TLIBC_CDECL_ sincosl(long double, long double *, long double *);
long double _TLIBC_CDECL_ y1l(long double);
long double _TLIBC_CDECL_ y0l(long double);
long double _TLIBC_CDECL_ ynl(int, long double);
int         _TLIBC_CDECL_ finitel(long double);
int         _TLIBC_CDECL_ isinfl(long double);
int         _TLIBC_CDECL_ isnanl(long double);

/* 
 * TODO: From Intel Decimal Floating-Point Math Library
 * signbitd32/signbitd64/signbitd128, finited32/finited64/finited128
 * isinfd32/isinfd64/isinfd128, isnand32/isnand64/isnand128
 */
#if defined(__cplusplus) 
/* Clang does not support decimal floating point types.
 *
 * c.f.:
 * http://clang.llvm.org/docs/UsersManual.html#gcc-extensions-not-implemented-yet
 */
#if !defined(__clang__)
typedef float _Decimal32 __attribute__((mode(SD)));
typedef float _Decimal64 __attribute__((mode(DD)));
typedef float _Decimal128 __attribute__((mode(TD)));
#endif
#endif

__END_DECLS

#endif /* !_MATH_H_ */
