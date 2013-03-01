/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include <avian/vm/system/system.h>

#include <avian/util/arg-parser.h>

#include <avian/vm/codegen/lir.h>
#include <avian/vm/codegen/assembler.h>
#include <avian/vm/codegen/targets.h>
#include <avian/vm/codegen/registers.h>

#include <avian/vm/heap/heap.h>

// since we aren't linking against libstdc++, we must implement this
// ourselves:
extern "C" void __cxa_pure_virtual(void) { abort(); }

using namespace vm;
using namespace avian::codegen;
using namespace avian::util;

class BasicEnv {
public:
  System* s;
  Heap* heap;
  Architecture* arch;

  BasicEnv():
    s(makeSystem(0)),
    heap(makeHeap(s, 32 * 1024)),
    arch(makeArchitectureNative(s, true))
  {
    arch->acquire();
  }

  ~BasicEnv() {
    arch->release();
    s->dispose();
  }
};

class Asm {
public:
  Zone zone;
  Assembler* a;

  Asm(BasicEnv& env):
    zone(env.s, env.heap, 8192),
    a(env.arch->makeAssembler(env.heap, &zone))
  { }

  ~Asm() {
    a->dispose();
  }
};

void generateCode(BasicEnv& env) {
  Asm a(env);
  for(RegisterIterator it(env.arch->registerFile()->generalRegisters); it.hasNext(); ) {
    int r = it.next();
    lir::Register reg(r);
    a.a->apply(lir::Add,
      OperandInfo(4, lir::RegisterOperand, &reg),
      OperandInfo(4, lir::RegisterOperand, &reg),
      OperandInfo(4, lir::RegisterOperand, &reg));
  }
  unsigned length = a.a->endBlock(false)->resolve(0, 0);
  printf("length: %d\n", length);
  uint8_t* data = static_cast<uint8_t*>(env.s->tryAllocate(length));
  a.a->setDestination(data);
  a.a->write();
  for(unsigned i = 0; i < length; i++) {
    printf("%02x ", data[i]);
  }
  printf("\n");
  env.s->free(data);
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

  BasicEnv env;

  generateCode(env);

  return 0;
}