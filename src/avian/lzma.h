/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef LZMA_H
#define LZMA_H

#include <avian/system/system.h>

namespace avian {
namespace util {
class AllocOnly;
}
}

namespace vm {

uint8_t* decodeLZMA(System* s,
                    avian::util::Alloc* a,
                    uint8_t* in,
                    size_t inSize,
                    size_t* outSize);

uint8_t* encodeLZMA(System* s,
                    avian::util::Alloc* a,
                    uint8_t* in,
                    size_t inSize,
                    size_t* outSize);

}  // namespace vm

#endif  // LZMA_H
