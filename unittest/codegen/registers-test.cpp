/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdio.h>

#include <avian/codegen/registers.h>

#include "test-harness.h"


using namespace avian::codegen;
using namespace vm;


TEST(RegisterIterator) {
  RegisterMask regs(0x55);
  assertEqual<unsigned>(0, regs.start);
  assertEqual<unsigned>(7, regs.limit);

  RegisterIterator it(regs);
  assertTrue(it.hasNext());
  assertEqual<unsigned>(0, it.next());
  assertTrue(it.hasNext());
  assertEqual<unsigned>(2, it.next());
  assertTrue(it.hasNext());
  assertEqual<unsigned>(4, it.next());
  assertTrue(it.hasNext());
  assertEqual<unsigned>(6, it.next());
  assertFalse(it.hasNext());
}
