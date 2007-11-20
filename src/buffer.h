#ifndef BUFFER_H
#define BUFFER_H

#include "system.h"

namespace vm {

class Buffer {
 public:
  Buffer(System* s, unsigned minimumCapacity):
    s(s),
    data(0),
    position(0),
    capacity(0),
    minimumCapacity(minimumCapacity)
  { }

  ~Buffer() {
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

  void append(uint8_t v) {
    ensure(1);
    data[position++] = v;
  }

  void append2(uint16_t v) {
    ensure(2);
    memcpy(data + position, &v, 2);
    position += 2;
  }

  void append4(uint32_t v) {
    ensure(4);
    memcpy(data + position, &v, 4);
    position += 4;
  }

  void set2(unsigned offset, uint32_t v) {
    assert(s, offset + 2 <= position);
    memcpy(data + offset, &v, 2);
  }

  void set4(unsigned offset, uint32_t v) {
    assert(s, offset + 4 <= position);
    memcpy(data + offset, &v, 4); 
  }

  uint8_t& get(unsigned offset) {
    assert(s, offset + 1 <= position);
    return data[offset];
  }

  uint16_t& get2(unsigned offset) {
    assert(s, offset + 2 <= position);
    return *reinterpret_cast<uint16_t*>(data + offset);
  }

  uint32_t& get4(unsigned offset) {
    assert(s, offset + 4 <= position);
    return *reinterpret_cast<uint32_t*>(data + offset);
  }

  uintptr_t& getAddress(unsigned offset) {
    assert(s, offset + 4 <= position);
    return *reinterpret_cast<uintptr_t*>(data + offset);
  }

  void appendAddress(uintptr_t v) {
    append4(v);
    if (BytesPerWord == 8) {
      // we have to use the preprocessor here to avoid a warning on
      // 32-bit systems
#ifdef __x86_64__
      append4(v >> 32);
#endif
    }
  }

  unsigned length() {
    return position;
  }

  void copyTo(void* b) {
    if (data) {
      memcpy(b, data, position);
    }
  }

  System* s;
  uint8_t* data;
  unsigned position;
  unsigned capacity;
  unsigned minimumCapacity;
};

} // namespace vm

#endif//BUFFER_H
