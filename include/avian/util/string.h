/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_STRING_H
#define AVIAN_UTIL_STRING_H

#include <string.h>

namespace avian {
namespace util {

class String {
 public:
  const char* text;
  size_t length;

  String(const char* text) : text(text), length(strlen(text))
  {
  }

  inline String(const char* text, size_t length) : text(text), length(length)
  {
  }
};

}  // namespace util
}  // namespace avain

#endif  // AVIAN_UTIL_STRING_H
