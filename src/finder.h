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

inline bool
readLine(const uint8_t* base, unsigned total, unsigned* start,
         unsigned* length)
{
  const uint8_t* p = base + *start;
  const uint8_t* end = base + total;
  while (p != end and (*p == '\n' or *p == '\r')) ++ p;

  *start = p - base;
  while (p != end and not (*p == '\n' or *p == '\r')) ++ p;

  *length = (p - base) - *start;

  return *length != 0;
}

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
      if (current) return true;
      current = it->next(&currentSize);
      return current != 0;
    }

    const char* next(unsigned* size) {
      if (hasMore()) {
        *size = currentSize;
        const char* v = current;
        current = 0;
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
  virtual System::FileType stat(const char* name,
                                unsigned* length,
                                bool tryDirectory = false) = 0;
  virtual const char* path() = 0;
  virtual void dispose() = 0;
};

Finder*
makeFinder(System* s, Allocator* a, const char* path, const char* bootLibrary);

Finder*
makeFinder(System* s, Allocator* a, const uint8_t* jarData,
           unsigned jarLength);

} // namespace vm

#endif//FINDER_H
