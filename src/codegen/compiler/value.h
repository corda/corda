/* Copyright (c) 2008-2015, Avian Contributors

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

class Value : public ir::Value {
 public:
  Read* reads;
  Read* lastRead;
  Site* sites;
  Site* source;
  Site* target;
  Value* buddy;
  Value* nextWord;
  int16_t home;
  uint8_t wordIndex;

  Value(Site* site, Site* target, ir::Type type);

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
#endif  // not NDEBUG
};

inline bool isFloatValue(ir::Value* a)
{
  return static_cast<Value*>(a)->type.flavor() == ir::Type::Float;
}

inline bool isGeneralValue(ir::Value* a)
{
  return !isFloatValue(a);
}

Value* value(Context* c, ir::Type type, Site* site = 0, Site* target = 0);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_VALUE_H
