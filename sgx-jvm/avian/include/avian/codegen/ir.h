/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_IR_H
#define AVIAN_CODEGEN_IR_H

namespace avian {
namespace codegen {
namespace ir {

class TargetInfo {
 public:
  unsigned pointerSize;

  explicit TargetInfo(unsigned pointerSize) : pointerSize(pointerSize)
  {
  }
};

class Type {
 public:
  enum Flavor {
    // A GC-visiible reference
    Object,

    // GC-invisible types
    Integer,
    Float,
    Address,

    // Represents the lack of a return value
    // TODO: remove when possible
    Void,
  };

  typedef int16_t TypeDesc;

#define TY_DESC(flavor, size) ((flavor & 0xff) | ((size & 0xff) << 8))
  // TODO: once we upgrade to c++11, these should become plain constants (rather
  // than function calls).
  // The constructor will need to be declared 'constexpr'.
  static inline Type void_()
  {
    return TY_DESC(Void, 0);
  }
  static inline Type object()
  {
    return TY_DESC(Object, -1);
  }
  static inline Type iptr()
  {
    return TY_DESC(Integer, -1);
  }
  static inline Type i1()
  {
    return TY_DESC(Integer, 1);
  }
  static inline Type i2()
  {
    return TY_DESC(Integer, 2);
  }
  static inline Type i4()
  {
    return TY_DESC(Integer, 4);
  }
  static inline Type i8()
  {
    return TY_DESC(Integer, 8);
  }
  static inline Type f4()
  {
    return TY_DESC(Float, 4);
  }
  static inline Type f8()
  {
    return TY_DESC(Float, 8);
  }
  static inline Type addr()
  {
    return TY_DESC(Address, -1);
  }
#undef TY_DESC

 private:
  TypeDesc desc;

  friend class Types;

  // TODO: once we move to c++11, declare this 'constexpr', to allow
  // compile-time constants of this type.
  /* constexpr */ Type(TypeDesc desc) : desc(desc)
  {
  }

 public:
  inline Flavor flavor() const
  {
    return (Flavor)(desc & 0xff);
  }

  // If the size isn't known without inspecting the TargetInfo, returns -1.
  // Otherwise, matches size(TargetInfo).
  inline int rawSize() const
  {
    return desc >> 8;
  }

  inline unsigned size(const TargetInfo& t) const
  {
    int s = rawSize();
    if (s < 0) {
      return t.pointerSize;
    }
    return (unsigned)s;
  }

  inline bool operator==(const Type& other) const
  {
    return desc == other.desc;
  }

  inline bool operator!=(const Type& other) const
  {
    return !(*this == other);
  }
};

enum class ExtendMode { Signed, Unsigned };

enum class CallingConvention { Native, Avian };

class Value {
 public:
  ir::Type type;

  Value(ir::Type type) : type(type)
  {
  }
};

}  // namespace ir
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_IR_H
