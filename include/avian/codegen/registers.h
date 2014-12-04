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

typedef uint64_t RegisterMask;

class BoundedRegisterMask {
 public:
  RegisterMask mask;
  uint8_t start;
  uint8_t limit;

  static unsigned maskStart(RegisterMask mask);
  static unsigned maskLimit(RegisterMask mask);

  inline BoundedRegisterMask(RegisterMask mask)
      : mask(mask), start(maskStart(mask)), limit(maskLimit(mask))
  {
  }
};

class RegisterFile {
 public:
  BoundedRegisterMask allRegisters;
  BoundedRegisterMask generalRegisters;
  BoundedRegisterMask floatRegisters;

  inline RegisterFile(RegisterMask generalRegisterMask, RegisterMask floatRegisterMask)
      : allRegisters(generalRegisterMask | floatRegisterMask),
        generalRegisters(generalRegisterMask),
        floatRegisters(floatRegisterMask)
  {
  }
};

class RegisterIterator {
 public:
  int index;
  const BoundedRegisterMask& mask;

  inline RegisterIterator(const BoundedRegisterMask& mask)
      : index(mask.start), mask(mask)
  {
  }

  inline bool hasNext()
  {
    return index < mask.limit;
  }

  inline int next()
  {
    int r = index;
    do {
      index++;
    } while (index < mask.limit && !(mask.mask & (1 << index)));
    return r;
  }
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_REGISTERS_H
