/* Copyright (c) 2008-2015, Avian Contributors

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

  static constexpr unsigned maskStart(uint64_t mask, unsigned offset = 64) {
    return mask == 0 ? (offset & 63) : maskStart(mask << 1, offset - 1);
  }

  static constexpr unsigned maskLimit(uint64_t mask, unsigned offset = 0) {
    return mask == 0 ? offset : maskLimit(mask >> 1, offset + 1);
  }
public:
  constexpr RegisterMask(uint64_t mask) : mask(mask) {}
  constexpr RegisterMask() : mask(0) {}
  constexpr RegisterMask(Register reg) : mask(static_cast<uint64_t>(1) << reg.index()) {}

  constexpr unsigned begin() const {
    return maskStart(mask);
  }

  constexpr unsigned end() const {
    return maskLimit(mask);
  }

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
};

constexpr RegisterMask AnyRegisterMask(~static_cast<uint64_t>(0));
constexpr RegisterMask NoneRegisterMask(0);

constexpr RegisterMask Register::operator | (Register o) const {
  return RegisterMask(*this) | o;
}

class RegisterIterator;

class BoundedRegisterMask : public RegisterMask {
 public:
  uint8_t start;
  uint8_t limit;

  BoundedRegisterMask(RegisterMask mask)
      : RegisterMask(mask), start(mask.begin()), limit(mask.end())
  {
  }

  RegisterIterator begin() const;

  RegisterIterator end() const;
};

class RegisterIterator {
 public:
  int index;
  int direction;
  int limit;
  const RegisterMask mask;

  RegisterIterator(int index, int direction, int limit, RegisterMask mask)
      : index(index), direction(direction), limit(limit), mask(mask)
  {
  }

  bool operator !=(const RegisterIterator& o) const {
    return index != o.index;
  }

  Register operator *() {
    return Register(index);
  }

  void operator ++ () {
    if(index != limit) {
      index += direction;
    }
    while(index != limit && !mask.contains(Register(index))) {
      index += direction;
    }
  }
};

inline RegisterIterator BoundedRegisterMask::begin() const {
  // We use reverse iteration... for some reason.
  return RegisterIterator(limit - 1, -1, start - 1, *this);
}

inline RegisterIterator BoundedRegisterMask::end() const {
  // We use reverse iteration... for some reason.
  return RegisterIterator(start - 1, -1, start - 1, *this);
}

inline RegisterIterator begin(BoundedRegisterMask mask) {
  return mask.begin();
}

inline RegisterIterator end(BoundedRegisterMask mask) {
  return mask.end();
}

class RegisterFile {
 public:
  BoundedRegisterMask allRegisters;
  BoundedRegisterMask generalRegisters;
  BoundedRegisterMask floatRegisters;

  RegisterFile(RegisterMask generalRegisterMask, RegisterMask floatRegisterMask)
      : allRegisters(generalRegisterMask | floatRegisterMask),
        generalRegisters(generalRegisterMask),
        floatRegisters(floatRegisterMask)
  {
  }
};

}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_REGISTERS_H
