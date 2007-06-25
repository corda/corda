#ifndef CLASS_FINDER_H
#define CLASS_FINDER_H

#include "common.h"

namespace vm {

class ClassFinder {
 public:
  class Data {
   public:
    virtual ~Data() { }
    virtual const uint8_t* start() = 0;
    virtual size_t length() = 0;
    virtual void dispose() = 0;
  };

  virtual ~ClassFinder() { }
  virtual Data* find(const char* className) = 0;
};

} // namespace vm

#endif//CLASS_FINDER_H
