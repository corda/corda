#ifndef ZONE_H
#define ZONE_H

#include "system.h"

namespace vm {

class Zone {
 public:
  class Segment {
   public:
    Segment(Segment* next): next(next) { }

    Segment* next;
    uint8_t data[0];
  };

  Zone(System* s, unsigned minimumCapacity):
    s(s),
    segment(0),
    position(0),
    capacity(0),
    minimumCapacity(minimumCapacity)
  { }

  ~Zone() {
    dispose();
  }

  void dispose() {
    for (Segment* seg = segment, *next; seg; seg = next) {
      next = seg->next;
      s->free(seg);
    }
  }

  void ensure(unsigned space) {
    if (position + space > capacity) {
      capacity = max(space, max(minimumCapacity, capacity * 2));
      segment = new (s->allocate(sizeof(Segment) + capacity)) Segment(segment);
      position = 0;
    }
  }

  void* allocate(unsigned size) {
    size = pad(size);
    ensure(size);
    void* r = segment->data + position;
    position += size;
    return r;
  }
  
  System* s;
  Segment* segment;
  unsigned position;
  unsigned capacity;
  unsigned minimumCapacity;
};

} // namespace vm

#endif//ZONE_H
