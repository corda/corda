/* Copyright (c) 2008, Avian Contributors

   Permission to use, copy, modify, and/or distribute this software
   for any purpose with or without fee is hereby granted, provided
   that the above copyright notice and this permission notice appear
   in all copies.

   There is NO WARRANTY for this software.  See license.txt for
   details. */

#include "bootimage.h"
#include "heapwalk.h"
#include "common.h"
#include "machine.h"
#include "util.h"
#include "assembler.h"

// since we aren't linking against libstdc++, we must implement this
// ourselves:
extern "C" void __cxa_pure_virtual(void) { abort(); }

using namespace vm;

namespace {

bool
endsWith(const char* suffix, const char* s, unsigned length)
{
  unsigned suffixLength = strlen(suffix);
  return length >= suffixLength
    and memcmp(suffix, s + (length - suffixLength), suffixLength) == 0;
}

unsigned
codeMapSize(unsigned codeSize)
{
  return ceiling(codeSize, BitsPerWord) * BytesPerWord;
}

object
makeCodeImage(Thread* t, Zone* zone, BootImage* image, uint8_t* code,
              unsigned capacity)
{
  unsigned size = 0;
  t->m->processor->compileThunks(t, image, code, &size, capacity);
  
  object constants = 0;
  PROTECT(t, constants);
  
  object calls = 0;
  PROTECT(t, calls);

  for (Finder::Iterator it(t->m->finder); it.hasMore();) {
    unsigned nameSize;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)) {
      fprintf(stderr, "%.*s\n", nameSize - 6, name);
      object c = resolveClass
        (t, makeByteArray(t, "%.*s", nameSize - 6, name));
      PROTECT(t, c);

      if (classMethodTable(t, c)) {
        for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
          object method = arrayBody(t, classMethodTable(t, c), i);
          if (methodCode(t, method)) {
            t->m->processor->compileMethod
              (t, zone, code, &size, capacity, &constants, &calls, method);
          }
        }
      }
    }
  }

  for (; calls; calls = tripleThird(t, calls)) {
    static_cast<ListenPromise*>(pointerValue(t, tripleSecond(t, calls)))
      ->listener->resolve(methodCompiled(t, tripleFirst(t, calls)));
  }

  image->codeSize = size;

  return constants;
}

unsigned
heapMapSize(unsigned heapSize)
{
  return ceiling(heapSize, BitsPerWord * 8) * BytesPerWord;
}

unsigned
objectSize(Thread* t, object o)
{
  assert(t, not objectExtended(t, o));
  return baseSize(t, o, objectClass(t, o));
}

void
visitRoots(Machine* m, BootImage* image, HeapWalker* w)
{
  image->loader = w->visitRoot(m->loader);
  image->stringMap = w->visitRoot(m->stringMap);
  image->types = w->visitRoot(m->types);

  m->processor->visitRoots(image, w);
}

HeapWalker*
makeHeapImage(Thread* t, BootImage* image, uintptr_t* heap, uintptr_t* map,
              unsigned capacity)
{
  class Visitor: public HeapVisitor {
   public:
    Visitor(Thread* t, uintptr_t* heap, uintptr_t* map, unsigned capacity):
      t(t), currentObject(0), currentOffset(0), heap(heap), map(map),
      position(0), capacity(capacity)
    { }

    void visit(unsigned number) {
      if (currentObject) {
        unsigned index = currentObject - 1 + currentOffset;
        markBit(map, index);
        heap[index] = number;
      }

      currentObject = number;
    }

    virtual void root() {
      currentObject = 0;
    }

    virtual unsigned visitNew(object p) {
      if (p) {
        unsigned size = objectSize(t, p);
        assert(t, position + size < capacity);

        memcpy(heap + position, p, size * BytesPerWord);

        unsigned number = position + 1;
        position += size;

        visit(number);

        return number;
      } else {
        return 0;
      }
    }

    virtual void visitOld(object, unsigned number) {
      visit(number);
    }

    virtual void push(unsigned offset) {
      currentOffset = offset;
    }

    virtual void pop() {
      currentObject = 0;
    }

    Thread* t;
    unsigned currentObject;
    unsigned currentOffset;
    uintptr_t* heap;
    uintptr_t* map;
    unsigned position;
    unsigned capacity;
  } visitor(t, heap, map, capacity / BytesPerWord);

  HeapWalker* w = makeHeapWalker(t, &visitor);
  visitRoots(t->m, image, w);
  
  image->heapSize = visitor.position * BytesPerWord;

  return w;
}

void
updateConstants(Thread* t, object constants, uint8_t* code, uintptr_t* codeMap,
                HeapMap* heapTable)
{
  for (; constants; constants = tripleThird(t, constants)) {
    intptr_t target = heapTable->find(tripleFirst(t, constants));
    assert(t, target >= 0);

    void* dst = static_cast<ListenPromise*>
      (pointerValue(t, tripleSecond(t, constants)))->listener->resolve(target);

    assert(t, reinterpret_cast<intptr_t>(dst)
           >= reinterpret_cast<intptr_t>(code));

    markBit(codeMap, reinterpret_cast<intptr_t>(dst)
            - reinterpret_cast<intptr_t>(code));
  }
}

unsigned
offset(object a, uintptr_t* b)
{
  return reinterpret_cast<uintptr_t>(b) - reinterpret_cast<uintptr_t>(a);
}

void
writeBootImage(Thread* t, FILE*)
{
  Zone zone(t->m->system, t->m->heap, 64 * 1024);
  BootImage image;

  const unsigned CodeCapacity = 32 * 1024 * 1024;
  uint8_t* code = static_cast<uint8_t*>(t->m->heap->allocate(CodeCapacity));
  uintptr_t* codeMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(codeMapSize(CodeCapacity)));
  memset(codeMap, 0, codeMapSize(CodeCapacity));

  object constants = makeCodeImage(t, &zone, &image, code, CodeCapacity);

  const unsigned HeapCapacity = 32 * 1024 * 1024;
  uintptr_t* heap = static_cast<uintptr_t*>
    (t->m->heap->allocate(HeapCapacity));
  uintptr_t* heapMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(heapMapSize(HeapCapacity)));
  memset(heapMap, 0, heapMapSize(HeapCapacity));

  HeapWalker* heapWalker = makeHeapImage
    (t, &image, heap, heapMap, HeapCapacity);

  updateConstants(t, constants, code, codeMap, heapWalker->map());

  heapWalker->dispose();

  image.magic = BootImage::Magic;

  fprintf(stderr, "heap size %d code size %d\n",
          image.heapSize, image.codeSize);
//   fwrite(&image, sizeof(BootImage), 1, out);

//   fwrite(heapMap, pad(heapMapSize(image.heapSize)), 1, out);
//   fwrite(heap, pad(image.heapSize), 1, out);

//   fwrite(codeMap, pad(codeMapSize(image.codeSize)), 1, out);
//   fwrite(code, pad(image.codeSize), 1, out);
}

} // namespace

int
main(int ac, const char** av)
{
  if (ac != 2) {
    fprintf(stderr, "usage: %s <classpath>\n", av[0]);
    return -1;
  }

  System* s = makeSystem(0);
  Heap* h = makeHeap(s, 128 * 1024 * 1024);
  Finder* f = makeFinder(s, av[1], 0);
  Processor* p = makeProcessor(s, h);
  Machine* m = new (h->allocate(sizeof(Machine))) Machine(s, h, f, p, 0, 0);
  Thread* t = p->makeThread(m, 0, 0);
  
  enter(t, Thread::ActiveState);
  enter(t, Thread::IdleState);

  writeBootImage(t, stdout);

  return 0;
}
