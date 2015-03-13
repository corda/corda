/* Copyright (c) 2008-2015, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#ifndef AVIAN_CODEGEN_COMPILER_READ_H
#define AVIAN_CODEGEN_COMPILER_READ_H

namespace avian {
namespace codegen {
namespace compiler {

class Context;
class SiteMask;
class Value;
class Event;

class Read {
 public:
  Read() : value(0), event(0), eventNext(0)
  {
  }

  virtual bool intersect(SiteMask* mask, unsigned depth = 0) = 0;

  virtual Value* high(Context* c)
  {
    abort(c);
  }

  virtual Value* successor() = 0;

  virtual bool valid() = 0;

  virtual void append(Context* c, Read* r) = 0;

  virtual Read* next(Context* c) = 0;

  Value* value;
  Event* event;
  Read* eventNext;
};

inline bool valid(Read* r)
{
  return r and r->valid();
}

class SingleRead : public Read {
 public:
  SingleRead(const SiteMask& mask, Value* successor);

  virtual bool intersect(SiteMask* mask, unsigned);

  virtual Value* high(Context*);

  virtual Value* successor();

  virtual bool valid();

  virtual void append(Context* c UNUSED, Read* r);

  virtual Read* next(Context*);

  Read* next_;
  SiteMask mask;
  Value* high_;
  Value* successor_;
};

class MultiRead : public Read {
 public:
  MultiRead();

  virtual bool intersect(SiteMask* mask, unsigned depth);

  virtual Value* successor();

  virtual bool valid();

  virtual void append(Context* c, Read* r);

  virtual Read* next(Context* c);

  void allocateTarget(Context* c);

  Read* nextTarget();

  List<Read*>* reads;
  List<Read*>* lastRead;
  List<Read*>* firstTarget;
  List<Read*>* lastTarget;
  bool visited;
};

class StubRead : public Read {
 public:
  StubRead();

  virtual bool intersect(SiteMask* mask, unsigned depth);

  virtual Value* successor();

  virtual bool valid();

  virtual void append(Context* c UNUSED, Read* r);

  virtual Read* next(Context*);

  Read* next_;
  Read* read;
  bool visited;
  bool valid_;
};

SingleRead* read(Context* c, const SiteMask& mask, Value* successor = 0);

}  // namespace compiler
}  // namespace codegen
}  // namespace avian

#endif  // AVIAN_CODEGEN_COMPILER_READ_H
