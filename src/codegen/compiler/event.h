/* Copyright (c) 2008-2012, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_EVENT_H
#define AVIAN_CODEGEN_COMPILER_EVENT_H

namespace avian {
namespace codegen {
namespace compiler {

class Context;
class CodePromise;
class Snapshot;
class Link;
class Site;

const bool DebugReads = false;

class Event {
 public:
  Event(Context* c);

  virtual const char* name() = 0;

  virtual void compile(Context* c) = 0;

  virtual bool isBranch() { return false; }

  virtual bool allExits() { return false; }

  virtual Local* locals() { return localsBefore; }



  void addRead(Context* c, Value* v, Read* r);

  void addRead(Context* c, Value* v, const SiteMask& mask,
          Value* successor = 0);

  void addReads(Context* c, Value* v, unsigned size,
           const SiteMask& lowMask, Value* lowSuccessor,
           const SiteMask& highMask, Value* highSuccessor);

  void addReads(Context* c, Value* v, unsigned size,
           const SiteMask& lowMask, const SiteMask& highMask);



  CodePromise* makeCodePromise(Context* c);

  Event* next;
  Stack* stackBefore;
  Local* localsBefore;
  Stack* stackAfter;
  Local* localsAfter;
  CodePromise* promises;
  Read* reads;
  Site** junctionSites;
  Snapshot* snapshots;
  Link* predecessors;
  Link* successors;
  Cell<Link>* visitLinks;
  Block* block;
  LogicalInstruction* logicalInstruction;
  unsigned readCount;
};

void
appendCall(Context* c, Value* address, unsigned flags,
           TraceHandler* traceHandler, Value* result, unsigned resultSize,
           Stack* argumentStack, unsigned argumentCount,
           unsigned stackArgumentFootprint);

} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_EVENT_H
