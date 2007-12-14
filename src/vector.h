#ifndef VECTOR_H
#define VECTOR_H

#include "system.h"

namespace vm {

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
    if (data and minimumCapacity >= 0) {
      s->free(data);
    }
  }

  void wrap(uint8_t* data, unsigned capacity) {
    dispose();

    this->data = data;
    this->position = 0;
    this->capacity = capacity;
    this->minimumCapacity = -1;
  }

  void ensure(unsigned space) {
    if (position + space > capacity) {
      assert(s, minimumCapacity >= 0);

      unsigned newCapacity = max
        (position + space, max(minimumCapacity, capacity * 2));
      uint8_t* newData = static_cast<uint8_t*>(s->allocate(newCapacity));
      if (data) {
        memcpy(newData, data, position);
        s->free(data);
      }
      data = newData;
      capacity = newCapacity;
    }
  }

  void get(unsigned offset, void* dst, unsigned size) {
    assert(s, offset + size <= position);
    memcpy(dst, data + offset, size);
  }

  void set(unsigned offset, const void* src, unsigned size) {
    assert(s, offset + size <= position);
    memcpy(data + offset, src, size);
  }

  void pop(void* dst, unsigned size) {
    get(position - size, dst, size);
    position -= size;
  }

  void* allocate(unsigned size) {
    ensure(size);
    void* r = data + position;
    position += size;
    return r;
  }

  void* append(const void* p, unsigned size) {
    void* r = allocate(size);
    memcpy(r, p, size);
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

  void appendAddress(void* v) {
    append(&v, BytesPerWord);
  }

  unsigned length() {
    return position;
  }

  template <class T>
  T* peek(unsigned offset) {
    assert(s, offset + sizeof(T) <= position);
    return reinterpret_cast<T*>(data + offset);
  }
  
  System* s;
  uint8_t* data;
  unsigned position;
  unsigned capacity;
  int minimumCapacity;
};

} // namespace vm

#endif//VECTOR_H
