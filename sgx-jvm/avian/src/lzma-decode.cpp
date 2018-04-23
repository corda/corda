/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/lzma-util.h"
#include "C/LzmaDec.h"

using namespace vm;

namespace {

int32_t read4(const uint8_t* in)
{
  return (static_cast<int32_t>(in[3]) << 24)
         | (static_cast<int32_t>(in[2]) << 16)
         | (static_cast<int32_t>(in[1]) << 8) | (static_cast<int32_t>(in[0]));
}

}  // namespace

namespace vm {

uint8_t* decodeLZMA(System* s,
                    avian::util::Alloc* a,
                    uint8_t* in,
                    size_t inSize,
                    size_t* outSize)
{
  const size_t PropHeaderSize = 5;
  const size_t HeaderSize = 13;

  int32_t outSize32 = read4(in + PropHeaderSize);
  expect(s, outSize32 >= 0);
  SizeT outSizeT = outSize32;

  uint8_t* out = static_cast<uint8_t*>(a->allocate(outSize32));

  SizeT inSizeT = inSize;
  LzmaAllocator allocator(a);

  ELzmaStatus status;
  int result = LzmaDecode(out,
                          &outSizeT,
                          in + HeaderSize,
                          &inSizeT,
                          in,
                          PropHeaderSize,
                          LZMA_FINISH_END,
                          &status,
                          &(allocator.allocator));

  expect(s, result == SZ_OK);
  expect(s, status == LZMA_STATUS_FINISHED_WITH_MARK);

  *outSize = outSize32;

  return out;
}

}  // namespace vm
