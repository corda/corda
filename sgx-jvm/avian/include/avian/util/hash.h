/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_HASH_H
#define AVIAN_UTIL_HASH_H

#include "slice.h"

namespace avian {
namespace util {

inline uint32_t hash(const char* s)
{
  uint32_t h = 0;
  for (unsigned i = 0; s[i]; ++i) {
    h = (h * 31) + s[i];
  }
  return h;
}

inline uint32_t hash(Slice<const uint8_t> data)
{
  const uint8_t* s = data.begin();
  uint32_t h = 0;
  for (size_t i = 0; i < data.count; ++i) {
    h = (h * 31) + s[i];
  }
  return h;
}

inline uint32_t hash(Slice<const int8_t> data)
{
  return hash(Slice<const uint8_t>(
      reinterpret_cast<const uint8_t*>(data.begin()), data.count));
}

inline uint32_t hash(Slice<const uint16_t> data)
{
  const uint16_t* s = data.begin();
  uint32_t h = 0;
  for (size_t i = 0; i < data.count; ++i) {
    h = (h * 31) + s[i];
  }
  return h;
}

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_HASH_H
