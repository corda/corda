/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "machine.h"

using namespace vm;

namespace {

const uintptr_t PointerShift = log(BytesPerWord);

class Set {
 public:
  class Entry {
   public:
    object value;
    uint32_t number;
    int next;
  };

  static unsigned footprint(unsigned capacity) {
    return sizeof(Set)
      + pad(sizeof(int) * capacity)
      + pad(sizeof(Set::Entry) * capacity);
  }

  Set(unsigned capacity):
    size(0),
    capacity(capacity),
    index(reinterpret_cast<int*>
          (reinterpret_cast<uint8_t*>(this)
           + sizeof(Set))),
    entries(reinterpret_cast<Entry*>
            (reinterpret_cast<uint8_t*>(index) 
             + pad(sizeof(int) * capacity)))
  { }

  unsigned size;
  unsigned capacity;
  int* index;
  Entry* entries;
};

class Stack {
 public:
  class Entry {
   public:
    object value;
    int offset;
  };

  static const unsigned Capacity = 4096;

  Stack(Stack* next): next(next), entryCount(0) { }

  Stack* next;
  unsigned entryCount;
  Entry entries[Capacity];
};

class Context {
 public:
  Context(Thread* thread, FILE* out):
    thread(thread), out(out), objects(0), stack(0), nextNumber(1)
  { }

  ~Context() {
    if (objects) {
      thread->m->heap->free(objects, Set::footprint(objects->capacity));
    }
    while (stack) {
      Stack* dead = stack;
      stack = dead->next;
      thread->m->heap->free(stack, sizeof(Stack));
    }
  }

  Thread* thread;
  FILE* out;
  Set* objects;
  Stack* stack;
  uint32_t nextNumber;
};

void
push(Context* c, object p, int offset)
{
  if (c->stack == 0 or c->stack->entryCount == Stack::Capacity) {
    c->stack = new (c->thread->m->heap->allocate(sizeof(Stack)))
      Stack(c->stack);
  }
  Stack::Entry* e = c->stack->entries + (c->stack->entryCount++);
  e->value = p;
  e->offset = offset;
}

bool
pop(Context* c, object* p, int* offset)
{
  if (c->stack) {
    if (c->stack->entryCount == 0) {
      if (c->stack->next) {
        Stack* dead = c->stack;
        c->stack = dead->next;
        c->thread->m->heap->free(dead, sizeof(Stack));
      } else {
        return false;
      }
    }
    Stack::Entry* e = c->stack->entries + (--c->stack->entryCount);
    *p = e->value;
    *offset = e->offset;
    return true;
  } else {
    return false;
  }
}

unsigned
hash(object p, unsigned capacity)
{
  return (reinterpret_cast<uintptr_t>(p) >> PointerShift)
    & (capacity - 1);
}

Set::Entry*
find(Context* c, object p)
{
  if (c->objects == 0) return false;

  for (int i = c->objects->index[hash(p, c->objects->capacity)]; i >= 0;) {
    Set::Entry* e = c->objects->entries + i;
    if (e->value == p) {
      return e;
    }
    i = e->next;
  }

  return false;
}

Set::Entry*
add(Context* c UNUSED, Set* set, object p)
{
  assert(c->thread, set->size < set->capacity);

  unsigned index = hash(p, set->capacity);

  int offset = set->size++;
  Set::Entry* e = set->entries + offset;
  e->value = p;
  e->next = set->index[index];
  set->index[index] = offset;
  return e;
}

Set::Entry*
add(Context* c, object p)
{
  if (c->objects == 0 or c->objects->size == c->objects->capacity) {
    unsigned capacity;
    if (c->objects) {
      capacity = c->objects->capacity * 2;
    } else {
      capacity = 4096; // must be power of two
    }

    Set* set = new (c->thread->m->heap->allocate(Set::footprint(capacity)))
      Set(capacity);

    memset(set->index, 0xFF, sizeof(int) * capacity);

    if (c->objects) {
      for (unsigned i = 0; i < c->objects->capacity; ++i) {
        for (int j = c->objects->index[i]; j >= 0;) {
          Set::Entry* e = c->objects->entries + j;
          add(c, set, e->value);
          j = e->next;
        }
      }

      c->thread->m->heap->free
        (c->objects, Set::footprint(c->objects->capacity));
    }

    c->objects = set;
  }

  return add(c, c->objects, p);
}

enum {
  Root,
  ClassName,
  Push,
  LastChild,
  Pop,
  Size
};

inline object
get(object o, unsigned offsetInWords)
{
  return static_cast<object>
    (mask(cast<void*>(o, offsetInWords * BytesPerWord)));
}

void
write1(Context* c, uint8_t v)
{
  fwrite(&v, 1, 1, c->out);
}

void
write4(Context* c, uint32_t v)
{
  uint8_t b[] = { v >> 24, (v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF };
  fwrite(b, 4, 1, c->out);
}

void
writeString(Context* c, int8_t* p, unsigned size)
{
  write4(c, size);
  fwrite(p, size, 1, c->out);
}

unsigned
objectSize(Thread* t, object o)
{
  unsigned n = baseSize(t, o, objectClass(t, o));
  if (objectExtended(t, o)) {
    ++ n;
  }
  return n;
}



void
visit(Context* c, object p)
{
  Thread* t = c->thread;
  int nextChildOffset;

  write1(c, Root);

 visit: {
    Set::Entry* e = find(c, p);
    if (e) {
      write4(c, e->number);
    } else {
      e = add(c, p);
      e->number = c->nextNumber++;

      write4(c, e->number);

      write1(c, Size);
      write4(c, objectSize(t, p));

      if (objectClass(t, p) == arrayBody(t, t->m->types, Machine::ClassType)) {
        object name = className(t, p);
        if (name) {
          write1(c, ClassName);
          writeString(c, &byteArrayBody(t, name, 0),
                      byteArrayLength(t, name) - 1);
        }
      }

      nextChildOffset = walkNext(t, p, -1);
      if (nextChildOffset != -1) {
        goto children;
      }
    }
  }

  goto pop;

 children: {
    int next = walkNext(t, p, nextChildOffset);
    if (next >= 0) {
      write1(c, Push);
      push(c, p, next);
    } else {
      write1(c, LastChild);
    }
    p = get(p, nextChildOffset);
    goto visit;
  }

 pop: {
    if (pop(c, &p, &nextChildOffset)) {
      write1(c, Pop);
      goto children;
    }
  }
}

} // namespace

namespace vm {

void
dumpHeap(Thread* t, FILE* out)
{
  Context context(t, out);

  class Visitor : public Heap::Visitor {
   public:
    Visitor(Context* c): c(c) { }

    virtual void visit(void* p) {
      ::visit(c, static_cast<object>(mask(*static_cast<void**>(p))));
    }

    Context* c;
  } v(&context);

  add(&context, 0)->number = 0;

  visitRoots(t, &v);
}

} // namespace vm
