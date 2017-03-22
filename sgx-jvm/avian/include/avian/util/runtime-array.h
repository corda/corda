/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_RUNTIME_ARRAY_H
#define AVIAN_UTIL_RUNTIME_ARRAY_H

#ifdef _MSC_VER

template <class T>
class RuntimeArray {
 public:
  RuntimeArray(unsigned size) : body(static_cast<T*>(malloc(size * sizeof(T))))
  {
  }

  ~RuntimeArray()
  {
    free(body);
  }

  T* body;
};

#define RUNTIME_ARRAY(type, name, size) RuntimeArray<type> name(size);
#define RUNTIME_ARRAY_BODY(name) name.body

#else  // not _MSC_VER

#define RUNTIME_ARRAY(type, name, size) type name##_body[size];
#define RUNTIME_ARRAY_BODY(name) name##_body

#endif

#endif  // AVIAN_UTIL_RUNTIME_ARRAY_H
