/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "avian/lzma-util.h"
#include "C/LzmaEnc.h"

using namespace vm;

namespace {

SRes myProgress(void*, UInt64, UInt64)
{
  return SZ_OK;
}

}  // namespace

namespace vm {

uint8_t* encodeLZMA(System* s,
                    avian::util::Alloc* a,
                    uint8_t* in,
                    size_t inSize,
                    size_t* outSize)
{
  const unsigned PropHeaderSize = 5;
  const unsigned HeaderSize = 13;

  unsigned bufferSize = inSize * 2;

  uint8_t* buffer = static_cast<uint8_t*>(a->allocate(bufferSize));

  LzmaAllocator allocator(a);

  CLzmaEncProps props;
  LzmaEncProps_Init(&props);
  props.level = 9;
  props.writeEndMark = 1;

  ICompressProgress progress = {myProgress};

  SizeT propsSize = PropHeaderSize;

  int32_t inSize32 = inSize;
  memcpy(buffer + PropHeaderSize, &inSize32, 4);

  SizeT outSizeT = bufferSize;
  int result = LzmaEncode(buffer + HeaderSize,
                          &outSizeT,
                          in,
                          inSize,
                          &props,
                          buffer,
                          &propsSize,
                          1,
                          &progress,
                          &(allocator.allocator),
                          &(allocator.allocator));

  expect(s, result == SZ_OK);

  *outSize = outSizeT + HeaderSize;

  uint8_t* out = static_cast<uint8_t*>(a->allocate(*outSize));
  memcpy(out, buffer, *outSize);

  a->free(buffer, bufferSize);

  return out;
}

}  // namespace vm
