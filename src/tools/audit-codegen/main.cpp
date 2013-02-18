/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "system.h"

#include "util/arg-parser.h"

#include "codegen/lir.h"
#include "codegen/assembler.h"
#include "codegen/targets.h"

// since we aren't linking against libstdc++, we must implement this
// ourselves:
extern "C" void __cxa_pure_virtual(void) { abort(); }

using namespace avian::codegen;
using namespace avian::util;

void generateCode(Assembler::Architecture* arch) {
  for()
}

class Arguments {
public:
  const char* output;
  const char* outputFormat;

  Arguments(int argc, char** argv) {
    ArgParser parser;
    Arg out(parser, true, "output", "<output object file>");
    Arg format(parser, true, "format", "<format of output object file>");

    if(!parser.parse(argc, argv)) {
      exit(1);
    }

    output = out.value;
    outputFormat = format.value;

    // TODO: sanitize format values
  }
};

int main(int argc, char** argv) {
  Arguments args(argc, argv);

  vm::System* s = vm::makeSystem(0);
  Assembler::Architecture* arch = makeArchitectureNative(s, true);
  arch->acquire();

  generateCode(arch);

  arch->release();
  s->dispose();
  return 0;
}