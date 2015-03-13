/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_ABORT_H
#define AVIAN_UTIL_ABORT_H

// TODO: remove reference back into the source directory!
// Note: this is needed for UNLIKELY
#include <avian/common.h>

namespace avian {
namespace util {

class Aborter {
 public:
  virtual void NO_RETURN abort() = 0;
};

inline Aborter* getAborter(Aborter* a)
{
  return a;
}

template <class T>
inline void NO_RETURN abort(T t)
{
  getAborter(t)->abort();
  ::abort();
}

template <class T>
inline void expect(T t, bool v)
{
  if (UNLIKELY(!v)) {
    abort(t);
  }
}

#ifdef NDEBUG
#define assertT(t, v)
#else
template <class T>
inline void assertT(T t, bool v)
{
  expect(t, v);
}
#endif

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_ABORT_H
