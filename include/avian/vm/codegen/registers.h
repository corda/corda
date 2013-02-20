/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_REGISTERS_H
#define AVIAN_CODEGEN_REGISTERS_H

#include "common.h"

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

} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_REGISTERS_H