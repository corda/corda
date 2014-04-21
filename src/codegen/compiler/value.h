/* Copyright (c) 2008-2014, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_VALUE_H
#define AVIAN_CODEGEN_COMPILER_VALUE_H

#include <avian/codegen/lir.h>
#include <avian/codegen/compiler.h>

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

  void grow(Context* c);

  void maybeSplit(Context* c);

  void split(Context* c);

  void removeSite(Context* c, Site* s);

  bool hasSite(Context* c);

  bool uniqueSite(Context* c, Site* s);

  void clearSites(Context* c);

#ifndef NDEBUG
  bool hasBuddy(Context* c, Value* b);
#endif // not NDEBUG

};

inline bool isGeneralValue(Compiler::Operand* a) {
  return static_cast<Value*>(a)->type == lir::ValueGeneral;
}

inline bool isFloatValue(Compiler::Operand* a) {
  return static_cast<Value*>(a)->type == lir::ValueFloat;
}

Value* value(Context* c, lir::ValueType type, Site* site = 0, Site* target = 0);

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_VALUE_H
