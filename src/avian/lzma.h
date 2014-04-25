/* Copyright (c) 2008-2014, Avian Contributors

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
class Allocator;
}
}

namespace vm {

uint8_t* decodeLZMA(System* s,
                    avian::util::Allocator* a,
                    uint8_t* in,
                    unsigned inSize,
                    unsigned* outSize);

uint8_t* encodeLZMA(System* s,
                    avian::util::Allocator* a,
                    uint8_t* in,
                    unsigned inSize,
                    unsigned* outSize);

} // namespace vm

#endif // LZMA_H
