#ifndef CLASS_FINDER_H
#define CLASS_FINDER_H

#include "common.h"
#include "system.h"

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
  virtual void dispose() = 0;
};

ClassFinder*
makeClassFinder(System* s, const char* path);

} // namespace vm

#endif//CLASS_FINDER_H
