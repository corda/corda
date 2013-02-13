/* Copyright (c) 2008-2012, Avian Contributors

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

class Read {
 public:
  Read():
    value(0), event(0), eventNext(0)
  { }

  virtual bool intersect(SiteMask* mask, unsigned depth = 0) = 0;

  virtual Value* high(Context* c) { abort(c); }

  virtual Value* successor() = 0;
  
  virtual bool valid() = 0;

  virtual void append(Context* c, Read* r) = 0;

  virtual Read* next(Context* c) = 0;

  Value* value;
  Event* event;
  Read* eventNext;
};


} // namespace compiler
} // namespace codegen
} // namespace avian

#endif // AVIAN_CODEGEN_COMPILER_READ_H
