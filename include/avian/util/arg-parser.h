/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_UTIL_ARG_PARSER_H
#define AVIAN_UTIL_ARG_PARSER_H

namespace avian {
namespace util {

class Arg;

class ArgParser {
 public:
  ArgParser();

  bool parse(int ac, const char* const* av);
  void printUsage(const char* exe);

 private:
  friend class Arg;

  Arg* first;
  Arg** last;
};

class Arg {
 public:
  Arg* next;
  bool required;
  const char* name;
  const char* desc;

  const char* value;

  Arg(ArgParser& parser, bool required, const char* name, const char* desc);
};

}  // namespace avian
}  // namespace util

#endif  // AVIAN_UTIL_ARG_PARSER_H
