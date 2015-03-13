/* Copyright (c) 2008-2015, Avian Contributors

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

TEST(RegisterIterator)
{
  BoundedRegisterMask regs(0x55);
  assertEqual<unsigned>(0, regs.start);
  assertEqual<unsigned>(7, regs.limit);

  for(int i = 0; i < 64; i++) {
    assertEqual<unsigned>(i, BoundedRegisterMask(static_cast<uint64_t>(1) << i).start);
    assertEqual<unsigned>(i + 1, BoundedRegisterMask(static_cast<uint64_t>(1) << i).limit);
  }

  auto it = regs.begin();
  auto end = regs.end();

  assertTrue(it != end);
  assertEqual<unsigned>(6, (*it).index());
  ++it;
  assertTrue(it != end);
  assertEqual<unsigned>(4, (*it).index());
  ++it;
  assertTrue(it != end);
  assertEqual<unsigned>(2, (*it).index());
  ++it;
  assertTrue(it != end);
  assertEqual<unsigned>(0, (*it).index());
  ++it;
  assertFalse(it != end);
}
