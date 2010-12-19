/* Copyright (c) 2008-2010, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef BOOTIMAGE_H
#define BOOTIMAGE_H

#include "common.h"

namespace vm {

const unsigned BootMask = (~static_cast<unsigned>(0)) / BytesPerWord;

const unsigned BootShift = 32 - log(BytesPerWord);

const unsigned BootFlatConstant = 1 << BootShift;
const unsigned BootHeapOffset = 1 << (BootShift + 1);

class BootImage {
 public:
  class Thunk {
   public:
    Thunk():
      start(0), frameSavedOffset(0), length(0)
    { }

    Thunk(unsigned start, unsigned frameSavedOffset, unsigned length):
      start(start), frameSavedOffset(frameSavedOffset), length(length)
    { }

    unsigned start;
    unsigned frameSavedOffset;
    unsigned length;
  };

  class ThunkCollection {
   public:
    Thunk default_;
    Thunk defaultVirtual;
    Thunk native;
    Thunk aioob;
    Thunk stackOverflow;
    Thunk table;
  };

  static const unsigned Magic = 0x22377322;

  unsigned magic;

  unsigned heapSize;
  unsigned codeSize;

  unsigned bootClassCount;
  unsigned appClassCount;
  unsigned stringCount;
  unsigned callCount;

  unsigned bootLoader;
  unsigned appLoader;
  unsigned types;
  unsigned methodTree;
  unsigned methodTreeSentinal;
  unsigned virtualThunks;

  uintptr_t codeBase;

  ThunkCollection thunks;

  unsigned compileMethodCall;
  unsigned compileVirtualMethodCall;
  unsigned invokeNativeCall;
  unsigned throwArrayIndexOutOfBoundsCall;
  unsigned throwStackOverflowCall;

#define THUNK(s) unsigned s##Call;
#include "thunks.cpp"
#undef THUNK
};

inline unsigned
codeMapSize(unsigned codeSize)
{
  return ceiling(codeSize, BitsPerWord) * BytesPerWord;
}

inline unsigned
heapMapSize(unsigned heapSize)
{
  return ceiling(heapSize, BitsPerWord * BytesPerWord) * BytesPerWord;
}

inline object
bootObject(uintptr_t* heap, unsigned offset)
{
  if (offset) {
    return reinterpret_cast<object>(heap + offset - 1);
  } else {
    return 0;
  }
}

} // namespace vm

#endif//BOOTIMAGE_H
