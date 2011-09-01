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
#include "target.h"
#include "machine.h"

namespace vm {

class BootImage {
 public:
  class Thunk {
   public:
    Thunk():
      start(0), frameSavedOffset(0), length(0)
    { }

    Thunk(uint32_t start, uint32_t frameSavedOffset, uint32_t length):
      start(start), frameSavedOffset(frameSavedOffset), length(length)
    { }

    uint32_t start;
    uint32_t frameSavedOffset;
    uint32_t length;
  } PACKED;

  class ThunkCollection {
   public:
    Thunk default_;
    Thunk defaultVirtual;
    Thunk native;
    Thunk aioob;
    Thunk stackOverflow;
    Thunk table;
  } PACKED;

  static const uint32_t Magic = 0x22377322;

  uint32_t magic;

  uint32_t heapSize;
  uint32_t codeSize;

  uint32_t bootClassCount;
  uint32_t appClassCount;
  uint32_t stringCount;
  uint32_t callCount;

  uint32_t bootLoader;
  uint32_t appLoader;
  uint32_t types;
  uint32_t methodTree;
  uint32_t methodTreeSentinal;
  uint32_t virtualThunks;

  uint32_t compileMethodCall;
  uint32_t compileVirtualMethodCall;
  uint32_t invokeNativeCall;
  uint32_t throwArrayIndexOutOfBoundsCall;
  uint32_t throwStackOverflowCall;

#define THUNK(s) uint32_t s##Call;
#include "thunks.cpp"
#undef THUNK

  ThunkCollection thunks;
} PACKED;

class OffsetResolver {
 public:
  virtual unsigned fieldOffset(Thread*, object) = 0;
};

#define NAME(x) Target##x
#define LABEL(x) target_##x
#include "bootimage-template.cpp"
#undef LABEL
#undef NAME

#define NAME(x) x
#define LABEL(x) x
#include "bootimage-template.cpp"
#undef LABEL
#undef NAME

} // namespace vm

#endif//BOOTIMAGE_H
