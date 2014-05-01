/* Copyright (c) 2008-2014, Avian Contributors

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

class Type {
 public:
  enum Flavor {
    // A GC-visiible reference
    Object,

    // GC-invisible types
    Integer,
    Float,
    Address,

    // Represents individual halves of two-word types
    // (double/long on 32-bit systems)
    // TODO: remove when possible
    Half,
  };

 private:
  uint8_t flavor_;
  uint8_t size_;

  friend class Types;

 public:
  Type(uint8_t flavor_, uint8_t size_) : flavor_(flavor_), size_(size_)
  {
  }

  inline Flavor flavor() const
  {
    return (Flavor)flavor_;
  }

  inline unsigned size() const
  {
    return size_;
  }

  inline bool operator==(const Type& other) const
  {
    return flavor_ == other.flavor_ && size_ == other.size_;
  }

  inline bool operator!=(const Type& other) const
  {
    return !(*this == other);
  }
};

class Types {
 public:
  // An object reference type, which will be treated as a GC root
  Type object;

  // A pointer-sized integer type (neither/both signed or unsigned)
  // Note that these are just integers from the GC's perspective.
  Type address;

  // A 1-byte integer type (neither/both signed or unsigned)
  Type i1;

  // A 2-byte integer type (neither/both signed or unsigned)
  Type i2;

  // A 4-byte integer type (neither/both signed or unsigned)
  Type i4;

  // A 8-byte integer type (neither/both signed or unsigned)
  Type i8;

  // A 4-byte floating point type
  Type f4;

  // A 8-byte floating point type
  Type f8;

  Types(unsigned bytesPerWord)
      : object(Type::Object, bytesPerWord),
        address(Type::Integer, bytesPerWord),
        i1(Type::Integer, 1),
        i2(Type::Integer, 2),
        i4(Type::Integer, 4),
        i8(Type::Integer, 8),
        f4(Type::Float, 4),
        f8(Type::Float, 8)
  {
  }
};

}  // namespace ir
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_IR_H
