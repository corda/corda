#ifndef VECTOR_H
#define VECTOR_H

#include "system.h"

namespace vm {

template <class T = uint8_t>
class Vector {
 public:
  Vector(System* s, unsigned minimumCapacity):
    s(s),
    data(0),
    position(0),
    capacity(0),
    minimumCapacity(minimumCapacity)
  { }

  ~Vector() {
    dispose();
  }

  void dispose() {
    if (data) {
      s->free(data);
    }
  }

  void ensure(unsigned space) {
    if (position + space > capacity) {
      unsigned newCapacity = max
        (position + space, max(minimumCapacity, capacity * 2));
      uint8_t* newData = static_cast<uint8_t*>(s->allocate(newCapacity));
      if (data) {
        memcpy(newData, data, position);
        s->free(data);
      }
      data = newData;
    }
  }

  void get(unsigned offset, void* dst, unsigned size) {
    assert(s, offset >= 0);
    assert(s, offset + size <= position);
    mempcy(dst, data + offset, size);
  }

  void set(unsigned offset, const void* src, unsigned size) {
    assert(s, offset >= 0);
    assert(s, offset + size <= position);
    mempcy(data + offset, src, size);
  }

  void pop(void* dst, unsigned size) {
    get(position - size, dst, size);
    position -= size;
  }

  void* append(const void* p, unsigned size) {
    ensure(size);
    void* r = data + position;
    memcpy(r, p, size);
    position += size;
    return r;
  }

  void append(uint8_t v) {
    append(&v, 1);
  }

  void append2(uint16_t v) {
    append(&v, 2);
  }

  void append4(uint32_t v) {
    append(&v, 4);
  }

  void appendAddress(uintptr_t v) {
    append(&v, BytesPerWord);
  }

  template <class C = T>
  C* push(const C& v) {
    return static_cast<C*>(append(&v, sizeof(C)));
  }

  template <class C = T>
  C pop() {
    C r; pop(&r, sizeof(C));
    return r;
  }
  
  System* s;
  uint8_t* data;
  unsigned position;
  unsigned capacity;
  unsigned minimumCapacity;
};

} // namespace vm

#endif//VECTOR_H
