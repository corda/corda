/* Copyright (c) 2008-2014, Avian Contributors

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

  String(const char* text):
    text(text),
    length(strlen(text)) {}
  
  inline String(const char* text, size_t length):
    text(text),
    length(length) {}
};

class Tokenizer {
 public:

  Tokenizer(const char* s, char delimiter):
    s(s), limit(0), delimiter(delimiter)
  { }

  Tokenizer(String str, char delimiter):
    s(str.text), limit(str.text + str.length), delimiter(delimiter)
  { }

  bool hasMore() {
    while (s != limit and *s == delimiter) ++s;
    return s != limit and *s != 0;
  }

  String next() {
    const char* p = s;
    while (s != limit and *s and *s != delimiter) ++s;
    return String(p, s - p);
  }

  const char* s;
  const char* limit;
  char delimiter;
};

} // namespace util
} // namespace avain

#endif//AVIAN_UTIL_STRING_H
