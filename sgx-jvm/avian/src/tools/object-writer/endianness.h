/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_ENDIANNESS_H
#define AVIAN_ENDIANNESS_H

namespace avian {

namespace endian {

static union {
  uint32_t i;
  char c[4];
} _DetectEndianness = {1};

const bool LittleEndian = _DetectEndianness.c[0] == 1;

template <bool TargetLittleEndian>
class Endianness {
 public:
  static inline uint8_t v1(uint8_t v)
  {
    return v;
  }

  static inline uint16_t v2(uint16_t v)
  {
    if (LittleEndian == TargetLittleEndian) {
      return v;
    } else {
      return ((v >> 8) & 0xFF) | (v << 8);
    }
  }

  static inline uint32_t v4(uint32_t v)
  {
    if (LittleEndian == TargetLittleEndian) {
      return v;
    } else {
      return ((v >> 24) & 0x000000FF) | ((v >> 8) & 0x0000FF00)
             | ((v << 8) & 0x00FF0000) | ((v << 24));
    }
  }

  static inline uint32_t vAny(uint32_t v)
  {
    return v4(v);
  }

  static inline uint64_t v8(uint64_t v)
  {
    if (LittleEndian == TargetLittleEndian) {
      return v;
    } else {
      return ((static_cast<uint64_t>(v) >> 56)
              & (static_cast<uint64_t>(0xff) << 0))
             | ((static_cast<uint64_t>(v) >> 40)
                & (static_cast<uint64_t>(0xff) << 8))
             | ((static_cast<uint64_t>(v) >> 24)
                & (static_cast<uint64_t>(0xff) << 16))
             | ((static_cast<uint64_t>(v) >> 8)
                & (static_cast<uint64_t>(0xff) << 24))
             | ((static_cast<uint64_t>(v) << 8)
                & (static_cast<uint64_t>(0xff) << 32))
             | ((static_cast<uint64_t>(v) << 24)
                & (static_cast<uint64_t>(0xff) << 40))
             | ((static_cast<uint64_t>(v) << 40)
                & (static_cast<uint64_t>(0xff) << 48))
             | ((static_cast<uint64_t>(v) << 56));
    }
  }

  static inline uint64_t vAny(uint64_t v)
  {
    return v8(v);
  }
};

}  // namespace endian

}  // namespace avian

#endif  // AVIAN_ENDIANNESS_H
