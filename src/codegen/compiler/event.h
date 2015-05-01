/* Copyright (c) 2008-2015, Avian Contributors

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
class StubRead;

const bool DebugReads = false;
const bool DebugMoves = false;

class Event {
 public:
  Event(Context* c);

  virtual const char* name() = 0;

  virtual void compile(Context* c) = 0;

  virtual bool isBranch()
  {
    return false;
  }

  virtual bool allExits()
  {
    return false;
  }

  virtual Local* locals()
  {
    return localsBefore;
  }

  void addRead(Context* c, Value* v, Read* r);

  void addRead(Context* c,
               Value* v,
               const SiteMask& mask,
               Value* successor = 0);

  void addReads(Context* c,
                Value* v,
                unsigned size,
                const SiteMask& lowMask,
                Value* lowSuccessor,
                const SiteMask& highMask,
                Value* highSuccessor);

  void addReads(Context* c,
                Value* v,
                unsigned size,
                const SiteMask& lowMask,
                const SiteMask& highMask);

  CodePromise* makeCodePromise(Context* c);

  bool isUnreachable();

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
  List<Link*>* visitLinks;
  Block* block;
  LogicalInstruction* logicalInstruction;
  unsigned readCount;
};

void finishAddRead(Context* c, Value* v, Read* r);

class StubReadPair {
 public:
  Value* value;
  StubRead* read;
};

class JunctionState {
 public:
  JunctionState(unsigned frameFootprint) : frameFootprint(frameFootprint)
  {
  }

  unsigned frameFootprint;
  StubReadPair reads[0];
};

class Link {
 public:
  Link(Event* predecessor,
       Link* nextPredecessor,
       Event* successor,
       Link* nextSuccessor,
       ForkState* forkState)
      : predecessor(predecessor),
        nextPredecessor(nextPredecessor),
        successor(successor),
        nextSuccessor(nextSuccessor),
        forkState(forkState),
        junctionState(0)
  {
  }

  unsigned countPredecessors();
  Link* lastPredecessor();
  unsigned countSuccessors();

  Event* predecessor;
  Link* nextPredecessor;
  Event* successor;
  Link* nextSuccessor;
  ForkState* forkState;
  JunctionState* junctionState;
};

Link* link(Context* c,
           Event* predecessor,
           Link* nextPredecessor,
           Event* successor,
           Link* nextSuccessor,
           ForkState* forkState);

void appendCall(Context* c,
                Value* address,
                ir::CallingConvention callingConvention,
                unsigned flags,
                TraceHandler* traceHandler,
                Value* result,
                util::Slice<ir::Value*> arguments);

void appendReturn(Context* c, Value* value);

void appendMove(Context* c,
                lir::BinaryOperation op,
                unsigned srcSize,
                unsigned srcSelectSize,
                Value* src,
                unsigned dstSize,
                Value* dst);

void appendCombine(Context* c,
                   lir::TernaryOperation op,
                   Value* first,
                   Value* second,
                   Value* result);

void appendTranslate(Context* c,
                     lir::BinaryOperation op,
                     Value* first,
                     Value* result);

void appendOperation(Context* c, lir::Operation op);

void appendMemory(Context* c,
                  Value* base,
                  int displacement,
                  Value* index,
                  unsigned scale,
                  Value* result);

void appendBranch(Context* c,
                  lir::TernaryOperation op,
                  Value* first,
                  Value* second,
                  Value* address);

void appendJump(Context* c,
                lir::UnaryOperation op,
                Value* address,
                bool exit = false,
                bool cleanLocals = false);

void appendBoundsCheck(Context* c,
                       Value* object,
                       unsigned lengthOffset,
                       Value* index,
                       intptr_t handler);

void appendFrameSite(Context* c, Value* value, int index);

void appendSaveLocals(Context* c);

void appendDummy(Context* c);

void appendBuddy(Context* c, Value* original, Value* buddy);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_EVENT_H
