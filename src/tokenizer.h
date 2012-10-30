/* Copyright (c) 2010-2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef TOKENIZER_H
#define TOKENIZER_H

namespace vm {

class Tokenizer {
 public:
  class Token {
   public:
    Token(const char* s, unsigned length): s(s), length(length) { }

    const char* s;
    unsigned length;
  };

  Tokenizer(const char* s, char delimiter):
    s(s), limit(0), delimiter(delimiter)
  { }

  Tokenizer(const char* s, unsigned length, char delimiter):
    s(s), limit(s + length), delimiter(delimiter)
  { }

  bool hasMore() {
    while (s != limit and *s == delimiter) ++s;
    return s != limit and *s != 0;
  }

  Token next() {
    const char* p = s;
    while (s != limit and *s and *s != delimiter) ++s;
    return Token(p, s - p);
  }

  const char* s;
  const char* limit;
  char delimiter;
};

} // namespace

#endif//TOKENIZER_H
