/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdio.h>
#include <string.h>

#include <avian/common.h>
#include <avian/util/arg-parser.h>

namespace avian {
namespace util {

Arg::Arg(ArgParser& parser, bool required, const char* name, const char* desc)
    : next(0), required(required), name(name), desc(desc), value(0)
{
  *parser.last = this;
  parser.last = &next;
}

ArgParser::ArgParser() : first(0), last(&first)
{
}

bool ArgParser::parse(int ac, const char* const* av)
{
  Arg* state = 0;

  for (int i = 1; i < ac; i++) {
    if (state) {
      if (state->value) {
        fprintf(stderr,
                "duplicate parameter %s: '%s' and '%s'\n",
                state->name,
                state->value,
                av[i]);
        return false;
      }
      state->value = av[i];
      state = 0;
    } else {
      if (av[i][0] != '-') {
        fprintf(stderr, "expected -parameter\n");
        return false;
      }
      bool found = false;
      for (Arg* arg = first; arg; arg = arg->next) {
        if (strcmp(arg->name, &av[i][1]) == 0) {
          found = true;
          if (arg->desc == 0) {
            arg->value = "true";
          } else {
            state = arg;
          }
        }
      }
      if (not found) {
        fprintf(stderr, "unrecognized parameter %s\n", av[i]);
        return false;
      }
    }
  }

  if (state) {
    fprintf(stderr, "expected argument after -%s\n", state->name);
    return false;
  }

  for (Arg* arg = first; arg; arg = arg->next) {
    if (arg->required && !arg->value) {
      fprintf(stderr, "expected value for %s\n", arg->name);
      return false;
    }
  }

  return true;
}

void ArgParser::printUsage(const char* exe)
{
  fprintf(stderr, "usage:\n%s \\\n", exe);
  for (Arg* arg = first; arg; arg = arg->next) {
    const char* lineEnd = arg->next ? " \\" : "";
    if (arg->required) {
      fprintf(stderr, "  -%s\t%s%s\n", arg->name, arg->desc, lineEnd);
    } else if (arg->desc) {
      fprintf(stderr, "  [-%s\t%s]%s\n", arg->name, arg->desc, lineEnd);
    } else {
      fprintf(stderr, "  [-%s]%s\n", arg->name, lineEnd);
    }
  }
}

}  // namespace util
}  // namespace avian
