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

class RegisterMask;

class Register {
private:
  int8_t index;
public:
  explicit constexpr Register(int8_t index) : index(index) {}
  constexpr Register() : index(-1) {}

  constexpr bool operator == (Register o) const {
    return index == o.index;
  }

  constexpr bool operator != (Register o) const {
    return !(*this == o);
  }

  constexpr RegisterMask operator | (Register o) const;

  constexpr operator int8_t() const {
    return index;
  }
};

constexpr Register NoRegister;

class RegisterMask {
private:
  uint64_t mask;
public:
  constexpr RegisterMask(uint64_t mask) : mask(mask) {}
  constexpr RegisterMask() : mask(0) {}
  constexpr RegisterMask(Register reg) : mask(static_cast<uint64_t>(1) << (int8_t)reg) {}

  constexpr RegisterMask operator &(RegisterMask o) const {
    return RegisterMask(mask & o.mask);
  }

  RegisterMask operator &=(RegisterMask o) {
    mask &= o.mask;
    return *this;
  }

  constexpr RegisterMask operator |(RegisterMask o) const {
    return RegisterMask(mask | o.mask);
  }

  constexpr bool contains(Register reg) const {
    return (mask & (static_cast<uint64_t>(1) << (int8_t)reg)) != 0;
  }

  constexpr bool containsExactly(Register reg) const {
    return mask == (mask & (static_cast<uint64_t>(1) << (int8_t)reg));
  }

  constexpr RegisterMask excluding(Register reg) const {
    return RegisterMask(mask & ~(static_cast<uint64_t>(1) << (int8_t)reg));
  }

  constexpr RegisterMask including(Register reg) const {
    return RegisterMask(mask | (static_cast<uint64_t>(1) << (int8_t)reg));
  }

  constexpr explicit operator uint64_t() const {
    return mask;
  }

  constexpr explicit operator bool() const {
    return mask != 0;
  }

  static RegisterMask Any;
  static RegisterMask None;
};

constexpr RegisterMask Register::operator | (Register o) const {
  return RegisterMask(*this) | o;
}

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

  inline Register next()
  {
    int r = index;
    do {
      index++;
    } while (index < mask.limit && !(mask.mask.contains(Register(index))));
    return Register(r);
  }
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_REGISTERS_H
