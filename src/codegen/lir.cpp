/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "lir.h"

namespace {

const char* lirOpcodeNames[] = {
  #define LIR_OP_0(x) #x
  #define LIR_OP_1(x) #x
  #define LIR_OP_2(x) #x
  #define LIR_OP_3(x) #x
  #include "lir-ops.inc.cpp"
  #undef LIR_OP_0
  #undef LIR_OP_1
  #undef LIR_OP_2
  #undef LIR_OP_3
};

}

namespace vm {

const char* LirInstr::opcodeName(Opcode op) {
  return lirOpcodeNames[op];
}

}