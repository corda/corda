//===-- lib/fixtfsi.c - Quad-precision -> integer conversion ------*- C -*-===//
//
// The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//
//
// This file implements quad-precision to integer conversion for the
// compiler-rt library. No range checking is performed; the behavior of this
// conversion is undefined for out of range values in the C standard.
//
//===----------------------------------------------------------------------===//

#define QUAD_PRECISION
#include "fp_lib.h"

#if defined(CRT_HAS_128BIT) && defined(CRT_LDBL_128BIT)
#include "fp_fixsi_impl.inc"

COMPILER_RT_ABI int __fixtfsi(fp_t a) {
 return __fixXsi(a);
}

#endif
