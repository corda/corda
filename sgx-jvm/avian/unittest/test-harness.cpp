/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdio.h>

#include "test-harness.h"

// since we aren't linking against libstdc++, we must implement this
// ourselves:
extern "C" void __cxa_pure_virtual(void)
{
  abort();
}

Test* Test::first = 0;
Test** Test::last = &first;

Test::Test(const char* name) : next(0), failures(0), runs(0), name(name)
{
  *last = this;
  last = &next;
}

bool Test::runAll()
{
  int failures = 0;
  for (Test* t = Test::first; t; t = t->next) {
    printf("%32s: ", t->name);
    t->run();
    failures += t->failures;
    if (t->failures > 0) {
      printf("failure\n");
    } else {
      printf("success\n");
    }
  }
  return failures == 0;
}

int main(int argc UNUSED, char** argv UNUSED)
{
  if (Test::runAll()) {
    return 0;
  }
  return 1;
}
