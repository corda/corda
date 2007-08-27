#ifndef FINDER_H
#define FINDER_H

#include "common.h"
#include "system.h"

namespace vm {

class Finder {
 public:
  class Data {
   public:
    virtual ~Data() { }
    virtual const uint8_t* start() = 0;
    virtual size_t length() = 0;
    virtual void dispose() = 0;
  };

  virtual ~Finder() { }
  virtual Data* find(const char* name) = 0;
  virtual bool exists(const char* name) = 0;
  virtual const char* path() = 0;
  virtual void dispose() = 0;
};

Finder*
makeFinder(System* s, const char* path);

} // namespace vm

#endif//FINDER_H
