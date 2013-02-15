/* Copyright (c) 2008-2011, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <stdio.h>

#include "common.h"
#include "codegen/assembler.h"
#include "codegen/targets.h"

#include "test-harness.h"

#include "system.h"


using namespace avian::codegen;
using namespace vm;

class BasicAssemblerTest : public Test {
public:
  BasicAssemblerTest():
    Test("BasicAssemblerTest")
  {}

  virtual void run() {
    System* s = makeSystem(0);
    Assembler::Architecture* arch = makeArchitectureNative(s, true);
    arch->release();
    s->dispose();
  }
} basicAssemblerTest;
