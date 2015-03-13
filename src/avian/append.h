/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef APPEND_H
#define APPEND_H

#include <avian/common.h>
#include <avian/util/allocator.h>

namespace vm {

inline const char* append(avian::util::AllocOnly* allocator,
                          const char* a,
                          const char* b,
                          const char* c)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  unsigned cl = strlen(c);
  char* p = static_cast<char*>(allocator->allocate((al + bl + cl) + 1));
  memcpy(p, a, al);
  memcpy(p + al, b, bl);
  memcpy(p + al + bl, c, cl + 1);
  return p;
}

inline const char* append(avian::util::AllocOnly* allocator,
                          const char* a,
                          const char* b)
{
  unsigned al = strlen(a);
  unsigned bl = strlen(b);
  char* p = static_cast<char*>(allocator->allocate((al + bl) + 1));
  memcpy(p, a, al);
  memcpy(p + al, b, bl + 1);
  return p;
}

inline const char* copy(avian::util::AllocOnly* allocator, const char* a)
{
  unsigned al = strlen(a);
  char* p = static_cast<char*>(allocator->allocate(al + 1));
  memcpy(p, a, al + 1);
  return p;
}

}  // namespace vm

#endif  // APPEND_H
