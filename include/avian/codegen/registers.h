/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_REGISTERS_H
#define AVIAN_CODEGEN_REGISTERS_H

#include "avian/common.h"

namespace avian {
namespace codegen {

class RegisterMask {
public:
  uint32_t mask;
  uint8_t start;
  uint8_t limit;

  static unsigned maskStart(uint32_t mask);
  static unsigned maskLimit(uint32_t mask);

  inline RegisterMask(uint32_t mask):
    mask(mask),
    start(maskStart(mask)),
    limit(maskLimit(mask))
  { }
};

class RegisterFile {
public:
  RegisterMask allRegisters;
  RegisterMask generalRegisters;
  RegisterMask floatRegisters;

  inline RegisterFile(uint32_t generalRegisterMask, uint32_t floatRegisterMask):
    allRegisters(generalRegisterMask | floatRegisterMask),
    generalRegisters(generalRegisterMask),
    floatRegisters(floatRegisterMask)
  { }
};

class RegisterIterator {
public:
  int index;
  const RegisterMask& mask;

  inline RegisterIterator(const RegisterMask& mask):
    index(mask.start),
    mask(mask) {}

  inline bool hasNext() {
    return index < mask.limit;
  }

  inline int next() {
    int r = index;
    do {
      index++;
    } while(index < mask.limit && !(mask.mask & (1 << index)));
    return r;
  }
};

} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_REGISTERS_H