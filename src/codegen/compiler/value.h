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

#include "codegen/compiler.h"

namespace avian {
namespace codegen {
namespace compiler {

class Read;
class Site;

const int AnyFrameIndex = -2;
const int NoFrameIndex = -1;

const bool DebugSites = false;

class Value: public Compiler::Operand {
 public:
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

  Value(Site* site, Site* target, lir::ValueType type);

  bool findSite(Site* site);

  bool isBuddyOf(Value* b);

  void addSite(Context* c, Site* s);
};

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_VALUE_H