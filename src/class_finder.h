#ifndef CLASS_FINDER_H
#define CLASS_FINDER_H

#include "common.h"

namespace vm {

class ClassFinder {
 public:
  virtual ~ClassFinder() { }
  virtual const uint8_t* find(const char* className, unsigned* size) = 0;
  virtual void free(const uint8_t* class_) = 0;
};

} // namespace vm

#endif//CLASS_FINDER_H
