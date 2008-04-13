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
  virtual ~Finder() { }
  virtual System::Region* find(const char* name) = 0;
  virtual bool exists(const char* name) = 0;
  virtual const char* path() = 0;
  virtual void dispose() = 0;
};

Finder*
makeFinder(System* s, const char* path, const char* bootLibrary);

} // namespace vm

#endif//FINDER_H
