/* Copyright (c) 2008, Avian Contributors

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

class BootImage {
 public:
  static const unsigned Magic = 0x22377322;

  unsigned magic;

  unsigned heapSize;
  unsigned codeSize;

  unsigned loader;
  unsigned stringMap;
  unsigned types;

  unsigned callTable;
  unsigned methodTree;
  unsigned methodTreeSentinal;

  uintptr_t codeBase;

  unsigned defaultThunk;
  unsigned nativeThunk;
  unsigned aioobThunk;

  unsigned thunkTable;
  unsigned thunkSize;

  unsigned compileMethodCall;
  unsigned invokeNativeCall;
  unsigned throwArrayIndexOutOfBoundsCall;

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
  return ceiling(heapSize, BitsPerWord * 8) * BytesPerWord;
}

} // namespace vm

#endif//BOOTIMAGE_H
