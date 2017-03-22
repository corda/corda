/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef VECTOR_H
#define VECTOR_H

#include <avian/target.h>

#include <avian/util/math.h>
#include <avian/util/abort.h>
#include <avian/util/slice.h>
#include <avian/util/allocator.h>

#undef max
#undef min

namespace vm {

class Vector {
 public:
  Vector(avian::util::Aborter* a,
         avian::util::Alloc* allocator,
         size_t minimumCapacity)
      : a(a),
        allocator(allocator),
        data(0, 0),
        position(0),
        minimumCapacity(minimumCapacity)
  {
  }

  ~Vector()
  {
    dispose();
  }

  void dispose()
  {
    if (data.items and minimumCapacity > 0) {
      allocator->free(data.items, data.count);
      data.items = 0;
      data.count = 0;
    }
  }

  void ensure(size_t space)
  {
    if (position + space > data.count) {
      assertT(a, minimumCapacity > 0);

      size_t newCapacity = avian::util::max(
          position + space, avian::util::max(minimumCapacity, data.count * 2));
      if (data.begin()) {
        data.resize(allocator, newCapacity);
      } else {
        data = avian::util::Slice<uint8_t>::alloc(allocator, newCapacity);
      }
    }
  }

  void get(size_t offset, void* dst, size_t size)
  {
    assertT(a, offset + size <= position);
    memcpy(dst, data.begin() + offset, size);
  }

  void set(size_t offset, const void* src, size_t size)
  {
    assertT(a, offset + size <= position);
    memcpy(data.begin() + offset, src, size);
  }

  void pop(void* dst, size_t size)
  {
    get(position - size, dst, size);
    position -= size;
  }

  void* allocate(size_t size)
  {
    ensure(size);
    void* r = data.begin() + position;
    position += size;
    return r;
  }

  void* append(const void* p, size_t size)
  {
    void* r = allocate(size);
    memcpy(r, p, size);
    return r;
  }

  void append(uint8_t v)
  {
    append(&v, 1);
  }

  void append2(uint16_t v)
  {
    append(&v, 2);
  }

  void append4(uint32_t v)
  {
    append(&v, 4);
  }

  void appendTargetAddress(target_uintptr_t v)
  {
    append(&v, TargetBytesPerWord);
  }

  void appendAddress(uintptr_t v)
  {
    append(&v, BytesPerWord);
  }

  void appendAddress(void* v)
  {
    append(&v, BytesPerWord);
  }

  void set2(size_t offset, uint16_t v)
  {
    assertT(a, offset <= position - 2);
    memcpy(data.begin() + offset, &v, 2);
  }

  size_t get(size_t offset)
  {
    uint8_t v;
    get(offset, &v, 1);
    return v;
  }

  size_t get2(size_t offset)
  {
    uint16_t v;
    get(offset, &v, 2);
    return v;
  }

  size_t get4(size_t offset)
  {
    uint32_t v;
    get(offset, &v, 4);
    return v;
  }

  uintptr_t getAddress(size_t offset)
  {
    uintptr_t v;
    get(offset, &v, BytesPerWord);
    return v;
  }

  size_t length()
  {
    return position;
  }

  template <class T>
  T* peek(size_t offset)
  {
    assertT(a, offset + sizeof(T) <= position);
    return reinterpret_cast<T*>(data.begin() + offset);
  }

  avian::util::Aborter* a;
  avian::util::Alloc* allocator;
  avian::util::Slice<uint8_t> data;
  size_t position;
  size_t minimumCapacity;
};

}  // namespace vm

#endif  // VECTOR_H
