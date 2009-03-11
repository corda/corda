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

object
makeCodeImage(Thread* t, Zone* zone, BootImage* image, uint8_t* code,
              unsigned capacity, uintptr_t* codeMap, const char* className,
              const char* methodName, const char* methodSpec)
{
  unsigned size = 0;
  t->m->processor->compileThunks(t, image, code, &size, capacity);
  
  object constants = 0;
  PROTECT(t, constants);
  
  object calls = 0;
  PROTECT(t, calls);

  DelayedPromise* addresses = 0;

  for (Finder::Iterator it(t->m->finder); it.hasMore();) {
    unsigned nameSize = 0;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)
        and (className == 0 or strncmp(name, className, nameSize - 6) == 0))
    {
//       fprintf(stderr, "%.*s\n", nameSize - 6, name);
      object c = resolveClass
        (t, makeByteArray(t, "%.*s", nameSize - 6, name));

      if (t->exception) return 0;

      PROTECT(t, c);

      if (classMethodTable(t, c)) {
        for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
          object method = arrayBody(t, classMethodTable(t, c), i);
          if ((methodCode(t, method) or (methodFlags(t, method) & ACC_NATIVE))
              and ((methodName == 0
                    or strcmp
                    (reinterpret_cast<char*>
                     (&byteArrayBody
                      (t, vm::methodName(t, method), 0)), methodName) == 0)
                   and (methodSpec == 0
                        or strcmp
                        (reinterpret_cast<char*>
                         (&byteArrayBody
                          (t, vm::methodSpec(t, method), 0)), methodSpec)
                        == 0)))
          {
            t->m->processor->compileMethod
              (t, zone, code, &size, capacity, &constants, &calls, &addresses,
               method);
          }
        }
      }
    }
  }

  for (; calls; calls = tripleThird(t, calls)) {
    object method = tripleFirst(t, calls);
    uintptr_t address;
    if (methodFlags(t, method) & ACC_NATIVE) {
      address = reinterpret_cast<uintptr_t>(code + image->nativeThunk);
    } else {
      address = methodCompiled(t, method);
    }

    static_cast<ListenPromise*>(pointerValue(t, tripleSecond(t, calls)))
      ->listener->resolve(address, 0);
  }

  for (; addresses; addresses = addresses->next) {
    uint8_t* value = reinterpret_cast<uint8_t*>(addresses->basis->value());
    assert(t, value >= code);

    void* location;
    bool flat = addresses->listener->resolve(0, &location);
    uintptr_t offset = value - code;
    if (flat) {
      offset |= BootFlatConstant;
    }
    memcpy(location, &offset, BytesPerWord);

    assert(t, reinterpret_cast<intptr_t>(location)
           >= reinterpret_cast<intptr_t>(code));

    markBit(codeMap, reinterpret_cast<intptr_t>(location)
            - reinterpret_cast<intptr_t>(code));
  }

  image->codeSize = size;

  return constants;
}

unsigned
objectSize(Thread* t, object o)
{
  assert(t, not objectExtended(t, o));

  return baseSize(t, o, objectClass(t, o));
}

void
visitRoots(Thread* t, BootImage* image, HeapWalker* w, object constants)
{
  Machine* m = t->m;

  for (HashMapIterator it(t, m->classMap); it.hasMore();) {
    w->visitRoot(tripleSecond(t, it.next()));
  }

  image->loader = w->visitRoot(m->loader);
  image->types = w->visitRoot(m->types);

  m->processor->visitRoots(image, w);

  for (; constants; constants = tripleThird(t, constants)) {
    w->visitRoot(tripleFirst(t, constants));
  }
}

HeapWalker*
makeHeapImage(Thread* t, BootImage* image, uintptr_t* heap, uintptr_t* map,
              unsigned capacity, object constants)
{
  class Visitor: public HeapVisitor {
   public:
    Visitor(Thread* t, uintptr_t* heap, uintptr_t* map, unsigned capacity):
      t(t), currentObject(0), currentNumber(0), currentOffset(0), heap(heap),
      map(map), position(0), capacity(capacity)
    { }

    void visit(unsigned number) {
      if (currentObject) {
        unsigned offset = currentNumber - 1 + currentOffset;
        unsigned mark = heap[offset] & (~PointerMask);
        unsigned value = number | (mark << BootShift);

        if (value) markBit(map, offset);

        heap[offset] = value;
      }
    }

    virtual void root() {
      currentObject = 0;
    }

    virtual unsigned visitNew(object p) {
      if (p) {
        unsigned size = objectSize(t, p);

        unsigned number;
        if (currentObject
            and (currentOffset * BytesPerWord) == ClassStaticTable)
        {
          FixedAllocator allocator
            (t, reinterpret_cast<uint8_t*>(heap + position),
             (capacity - position) * BytesPerWord);

          unsigned totalInBytes;
          uintptr_t* dst = static_cast<uintptr_t*>
            (t->m->heap->allocateImmortalFixed
             (&allocator, size, true, &totalInBytes));

          memcpy(dst, p, size * BytesPerWord);

          dst[0] |= FixedMark;

          number = (dst - heap) + 1;
          position += ceiling(totalInBytes, BytesPerWord);
        } else {
          assert(t, position + size < capacity);
          memcpy(heap + position, p, size * BytesPerWord);

          number = position + 1;
          position += size;
        }

        visit(number);

        return number;
      } else {
        return 0;
      }
    }

    virtual void visitOld(object, unsigned number) {
      visit(number);
    }

    virtual void push(object object, unsigned number, unsigned offset) {
      currentObject = object;
      currentNumber = number;
      currentOffset = offset;
    }

    virtual void pop() {
      currentObject = 0;
    }

    Thread* t;
    object currentObject;
    unsigned currentNumber;
    unsigned currentOffset;
    uintptr_t* heap;
    uintptr_t* map;
    unsigned position;
    unsigned capacity;
  } visitor(t, heap, map, capacity / BytesPerWord);

  HeapWalker* w = makeHeapWalker(t, &visitor);
  visitRoots(t, image, w, constants);
  
  image->heapSize = visitor.position * BytesPerWord;

  return w;
}

void
updateConstants(Thread* t, object constants, uint8_t* code, uintptr_t* codeMap,
                HeapMap* heapTable)
{
  for (; constants; constants = tripleThird(t, constants)) {
    unsigned target = heapTable->find(tripleFirst(t, constants));
    assert(t, target > 0);

    for (Promise::Listener* pl = static_cast<ListenPromise*>
           (pointerValue(t, tripleSecond(t, constants)))->listener;
         pl; pl = pl->next)
    {
      void* location;
      bool flat = pl->resolve(0, &location);
      uintptr_t offset = target | BootHeapOffset;
      if (flat) {
        offset |= BootFlatConstant;
      }
      memcpy(location, &offset, BytesPerWord);

      assert(t, reinterpret_cast<intptr_t>(location)
             >= reinterpret_cast<intptr_t>(code));

      markBit(codeMap, reinterpret_cast<intptr_t>(location)
              - reinterpret_cast<intptr_t>(code));
    }
  }
}

unsigned
offset(object a, uintptr_t* b)
{
  return reinterpret_cast<uintptr_t>(b) - reinterpret_cast<uintptr_t>(a);
}

void
writeBootImage(Thread* t, FILE* out, const char* className,
               const char* methodName, const char* methodSpec)
{
  Zone zone(t->m->system, t->m->heap, 64 * 1024);
  BootImage image;

  const unsigned CodeCapacity = 32 * 1024 * 1024;
  uint8_t* code = static_cast<uint8_t*>(t->m->heap->allocate(CodeCapacity));
  uintptr_t* codeMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(codeMapSize(CodeCapacity)));
  memset(codeMap, 0, codeMapSize(CodeCapacity));

  object constants = makeCodeImage
    (t, &zone, &image, code, CodeCapacity, codeMap, className, methodName,
     methodSpec);

  if (t->exception) return;

  PROTECT(t, constants);

  const unsigned HeapCapacity = 32 * 1024 * 1024;
  uintptr_t* heap = static_cast<uintptr_t*>
    (t->m->heap->allocate(HeapCapacity));
  uintptr_t* heapMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(heapMapSize(HeapCapacity)));
  memset(heapMap, 0, heapMapSize(HeapCapacity));

  collect(t, Heap::MajorCollection);

  HeapWalker* heapWalker = makeHeapImage
    (t, &image, heap, heapMap, HeapCapacity, constants);

  updateConstants(t, constants, code, codeMap, heapWalker->map());

  image.classCount = hashMapSize(t, t->m->classMap);
  unsigned* classTable = static_cast<unsigned*>
    (t->m->heap->allocate(image.classCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it(t, t->m->classMap); it.hasMore();) {
      classTable[i++] = heapWalker->map()->find(tripleSecond(t, it.next()));
    }
  }

  image.stringCount = hashMapSize(t, t->m->stringMap);
  unsigned* stringTable = static_cast<unsigned*>
    (t->m->heap->allocate(image.stringCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it(t, t->m->stringMap); it.hasMore();) {
      stringTable[i++] = heapWalker->map()->find
        (jreferenceTarget(t, tripleFirst(t, it.next())));
    }
  }

  unsigned* callTable = t->m->processor->makeCallTable
    (t, &image, heapWalker, code);

  heapWalker->dispose();

  image.magic = BootImage::Magic;
  image.codeBase = reinterpret_cast<uintptr_t>(code);

  fprintf(stderr, "class count %d string count %d call count %d\n"
          "heap size %d code size %d\n",
          image.classCount, image.stringCount, image.callCount, image.heapSize,
          image.codeSize);

  if (true) {
    fwrite(&image, sizeof(BootImage), 1, out);

    fwrite(classTable, image.classCount * sizeof(unsigned), 1, out);
    fwrite(stringTable, image.stringCount * sizeof(unsigned), 1, out);
    fwrite(callTable, image.callCount * sizeof(unsigned) * 2, 1, out);

    unsigned offset = (image.classCount * sizeof(unsigned))
      + (image.stringCount * sizeof(unsigned))
      + (image.callCount * sizeof(unsigned) * 2);

    while (offset % BytesPerWord) {
      uint8_t c = 0;
      fwrite(&c, 1, 1, out);
      ++ offset;
    }

    fwrite(heapMap, pad(heapMapSize(image.heapSize)), 1, out);
    fwrite(heap, pad(image.heapSize), 1, out);

    fwrite(codeMap, pad(codeMapSize(image.codeSize)), 1, out);
    fwrite(code, pad(image.codeSize), 1, out);
  }
}

} // namespace

int
main(int ac, const char** av)
{
  if (ac < 2 or ac > 5) {
    fprintf(stderr, "usage: %s <classpath> "
            "[<class name> [<method name> [<method spec>]]]\n", av[0]);
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

  writeBootImage
    (t, stdout, (ac > 2 ? av[2] : 0), (ac > 3 ? av[3] : 0),
     (ac > 4 ? av[4] : 0));

  if (t->exception) {
    printTrace(t, t->exception);
  }

  return 0;
}
