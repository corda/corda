/* Copyright (c) 2008-2013, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_ASSERT_H
#define AVIAN_UTIL_ASSERT_H

#include <stdlib.h>

namespace avian {
namespace util {

#define UNREACHABLE_ ::abort()

// TODO: print msg in debug mode
#define UNREACHABLE(msg) ::abort()

#define ASSERT(that)    \
  if (!(that)) {        \
    UNREACHABLE(#that); \
  }

}  // namespace util
}  // namespace avian

#endif  // AVIAN_UTIL_ASSERT_H
