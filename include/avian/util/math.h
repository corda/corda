/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_MATH_H
#define AVIAN_UTIL_MATH_H

#undef max
#undef min

namespace avian {
namespace util {

inline unsigned max(unsigned a, unsigned b)
{
  return (a > b ? a : b);
}

inline unsigned min(unsigned a, unsigned b)
{
  return (a < b ? a : b);
}

inline unsigned avg(unsigned a, unsigned b)
{
  return (a + b) / 2;
}

inline unsigned ceilingDivide(unsigned n, unsigned d)
{
  return (n + d - 1) / d;
}

inline bool powerOfTwo(unsigned n)
{
  for (; n > 2; n >>= 1)
    if (n & 1)
      return false;
  return true;
}

inline unsigned nextPowerOfTwo(unsigned n)
{
  unsigned r = 1;
  while (r < n)
    r <<= 1;
  return r;
}

inline unsigned log(unsigned n)
{
  unsigned r = 0;
  for (unsigned i = 1; i < n; ++r)
    i <<= 1;
  return r;
}

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_MATH_H
