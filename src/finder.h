/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef FINDER_H
#define FINDER_H

#include "common.h"
#include "system.h"
#include "allocator.h"

namespace vm {

class Finder {
 public:
  class IteratorImp {
   public:
    virtual const char* next(unsigned* size) = 0;
    virtual void dispose() = 0;
  };

  class Iterator {
   public:
    Iterator(Finder* finder):
      it(finder->iterator()),
      current(it->next(&currentSize))
    { }

    ~Iterator() {
      it->dispose();
    }

    bool hasMore() {
      return current != 0;
    }

    const char* next(unsigned* size) {
      if (current) {
        const char* v = current;
        *size = currentSize;
        current = it->next(&currentSize);
        return v;
      } else {
        return 0;
      }
    }

    IteratorImp* it;
    const char* current;
    unsigned currentSize;
  };

  virtual IteratorImp* iterator() = 0;
  virtual System::Region* find(const char* name) = 0;
  virtual bool exists(const char* name) = 0;
  virtual const char* path() = 0;
  virtual void dispose() = 0;
};

Finder*
makeFinder(System* s, const char* path, const char* bootLibrary);

} // namespace vm

#endif//FINDER_H
