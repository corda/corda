/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "heapwalk.h"

using namespace vm;

namespace {

enum {
  Root,
  Size,
  ClassName,
  Push,
  Pop
};

void
write1(FILE* out, uint8_t v)
{
  fwrite(&v, 1, 1, out);
}

void
write4(FILE* out, uint32_t v)
{
  uint8_t b[] = { v >> 24, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF };
  fwrite(b, 4, 1, out);
}

void
writeString(FILE* out, int8_t* p, unsigned size)
{
  write4(out, size);
  fwrite(p, size, 1, out);
}

unsigned
objectSize(Thread* t, object o)
{
  return extendedSize(t, o, baseSize(t, o, objectClass(t, o)));
}

} // namespace

namespace vm {

void
dumpHeap(Thread* t, FILE* out)
{
  class Visitor: public HeapVisitor {
   public:
    Visitor(Thread* t, FILE* out): t(t), out(out), nextNumber(1) { }

    virtual void root() {
      write1(out, Root);      
    }

    virtual unsigned visitNew(object p) {
      if (p) {
        unsigned number = nextNumber++;
        write4(out, number);

        write1(out, Size);
        write4(out, objectSize(t, p));

        if (objectClass(t, p) == arrayBody(t, t->m->types, Machine::ClassType))
        {
          object name = className(t, p);
          if (name) {
            write1(out, ClassName);
            writeString(out, &byteArrayBody(t, name, 0),
                        byteArrayLength(t, name) - 1);
          }
        }

        return number;
      } else {
        return 0;
      }
    }

    virtual void visitOld(object, unsigned number) {
      write4(out, number);      
    }

    virtual void push(unsigned) {
      write1(out, Push);
    }

    virtual void pop() {
      write1(out, Pop);
    }

    Thread* t;
    FILE* out;
    unsigned nextNumber;
  } visitor(t, out);

  HeapWalker* w = makeHeapWalker(t, &visitor);
  w->visitAllRoots();
  w->dispose();
}

} // namespace vm
