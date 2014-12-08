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
  int8_t _index;
public:
  explicit constexpr Register(int8_t _index) : _index(_index) {}
  constexpr Register() : _index(-1) {}

  constexpr bool operator == (Register o) const {
    return _index == o._index;
  }

  constexpr bool operator != (Register o) const {
    return !(*this == o);
  }

  constexpr RegisterMask operator | (Register o) const;

  constexpr bool operator < (Register o) const {
    return _index < o._index;
  }

  constexpr bool operator > (Register o) const {
    return _index > o._index;
  }

  constexpr bool operator <= (Register o) const {
    return _index <= o._index;
  }

  constexpr bool operator >= (Register o) const {
    return _index >= o._index;
  }

  constexpr int index() const {
    return _index;
  }
};

constexpr Register NoRegister;

class RegisterMask {
private:
  uint64_t mask;
public:
  constexpr RegisterMask(uint64_t mask) : mask(mask) {}
  constexpr RegisterMask() : mask(0) {}
  constexpr RegisterMask(Register reg) : mask(static_cast<uint64_t>(1) << reg.index()) {}

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
    return (mask & (static_cast<uint64_t>(1) << reg.index())) != 0;
  }

  constexpr bool containsExactly(Register reg) const {
    return mask == (mask & (static_cast<uint64_t>(1) << reg.index()));
  }

  constexpr RegisterMask excluding(Register reg) const {
    return RegisterMask(mask & ~(static_cast<uint64_t>(1) << reg.index()));
  }

  constexpr RegisterMask including(Register reg) const {
    return RegisterMask(mask | (static_cast<uint64_t>(1) << reg.index()));
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

class BoundedRegisterMask : public RegisterMask {
 public:
  uint8_t start;
  uint8_t limit;

  static unsigned maskStart(RegisterMask mask);
  static unsigned maskLimit(RegisterMask mask);

  inline BoundedRegisterMask(RegisterMask mask)
      : RegisterMask(mask), start(maskStart(mask)), limit(maskLimit(mask))
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
    } while (index < mask.limit && !(mask.contains(Register(index))));
    return Register(r);
  }
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_REGISTERS_H
