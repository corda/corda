/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdio.h>

#include "avian/common.h"

#include <avian/util/arg-parser.h>

#include "test-harness.h"

using namespace avian::util;

TEST(ArgParser)
{
  {
    ArgParser parser;
    Arg arg1(parser, false, "arg1", "<value>");
    Arg required2(parser, true, "required2", "<value>");
    const char* args[]
        = {"myExecutable", "-arg1", "myValue1", "-required2", "myRequired2", 0};
    assertTrue(parser.parse(sizeof(args) / sizeof(char*) - 1, args));
    assertEqual("myValue1", arg1.value);
    assertEqual("myRequired2", required2.value);
  }

  {
    ArgParser parser;
    Arg arg1(parser, false, "arg1", "<value>");
    Arg required2(parser, true, "required2", "<value>");
    const char* args[] = {"myExecutable", "-arg1", "myValue1", "-required2", 0};
    assertFalse(parser.parse(sizeof(args) / sizeof(char*) - 1, args));
  }

  {
    ArgParser parser;
    Arg arg1(parser, false, "arg1", "<value>");
    Arg required2(parser, true, "required2", "<value>");
    const char* args[] = {"myExecutable", "-arg1", "myValue1", 0};
    assertFalse(parser.parse(sizeof(args) / sizeof(char*) - 1, args));
  }
}
