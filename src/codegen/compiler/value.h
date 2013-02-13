/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_VALUE_H
#define AVIAN_CODEGEN_COMPILER_VALUE_H

#include "codegen/lir.h"

namespace avian {
namespace codegen {
namespace compiler {

class Read;
class Site;

const int AnyFrameIndex = -2;
const int NoFrameIndex = -1;

class Value: public Compiler::Operand {
 public:
  Value(Site* site, Site* target, lir::ValueType type):
    reads(0), lastRead(0), sites(site), source(0), target(target), buddy(this),
    nextWord(this), home(NoFrameIndex), type(type), wordIndex(0)
  { }
  
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  Site* target;
  Value* buddy;
  Value* nextWord;
  int16_t home;
  lir::ValueType type;
  uint8_t wordIndex;
};

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_VALUE_H