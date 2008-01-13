#ifndef FINDER_H
#define FINDER_H

#include "common.h"
#include "system.h"
#include "allocator.h"

namespace vm {

class Finder {
 public:
  virtual ~Finder() { }
  virtual System::Region* find(const char* name) = 0;
  virtual bool exists(const char* name) = 0;
  virtual const char* path() = 0;
  virtual void dispose() = 0;
};

Finder*
makeFinder(System* s, const char* path);

} // namespace vm

#endif//FINDER_H
