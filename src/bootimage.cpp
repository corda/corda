/* Copyright (c) 2008-2010, Avian Contributors

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

const unsigned HeapCapacity = 768 * 1024 * 1024;

// Notes on immutable references in the heap image:
//
// One of the advantages of a bootimage-based build is that reduces
// the overhead of major GCs at runtime since we can avoid scanning
// the pre-built heap image entirely.  However, this only works if we
// can ensure that no part of the heap image (with exceptions noted
// below) ever points to runtime-allocated objects.  Therefore (most)
// references in the heap image are considered immutable, and any
// attempt to update them at runtime will cause the process to abort.
//
// However, some references in the heap image really must be updated
// at runtime: e.g. the static field table for each class.  Therefore,
// we allocate these as "fixed" objects, subject to mark-and-sweep
// collection, instead of as "copyable" objects subject to copying
// collection.  This strategy avoids the necessity of maintaining
// "dirty reference" bitsets at runtime for the entire heap image;
// each fixed object has its own bitset specific to that object.
//
// In addition to the "fixed" object solution, there are other
// strategies available to avoid attempts to update immutable
// references at runtime:
//
//  * Table-based: use a lazily-updated array or vector to associate
//    runtime data with heap image objects (see
//    e.g. getClassRuntimeData in machine.cpp).
//
//  * Update references at build time: for example, we set the names
//    of primitive classes before generating the heap image so that we
//    need not populate them lazily at runtime.

bool
endsWith(const char* suffix, const char* s, unsigned length)
{
  unsigned suffixLength = strlen(suffix);
  return length >= suffixLength
    and memcmp(suffix, s + (length - suffixLength), suffixLength) == 0;
}

object
makeCodeImage(Thread* t, Zone* zone, BootImage* image, uint8_t* code,
              uintptr_t* codeMap, const char* className,
              const char* methodName, const char* methodSpec)
{
  object constants = 0;
  PROTECT(t, constants);
  
  object calls = 0;
  PROTECT(t, calls);

  DelayedPromise* addresses = 0;

  for (Finder::Iterator it
         (static_cast<Finder*>
          (systemClassLoaderFinder(t, root(t, Machine::BootLoader))));
       it.hasMore();)
  {
    unsigned nameSize = 0;
    const char* name = it.next(&nameSize);

    if (endsWith(".class", name, nameSize)
        and (className == 0 or strncmp(name, className, nameSize - 6) == 0))
    {
//       fprintf(stderr, "%.*s\n", nameSize - 6, name);
      object c = resolveSystemClass
        (t, root(t, Machine::BootLoader),
         makeByteArray(t, "%.*s", nameSize - 6, name), true);

      PROTECT(t, c);

      if (classMethodTable(t, c)) {
        for (unsigned i = 0; i < arrayLength(t, classMethodTable(t, c)); ++i) {
          object method = arrayBody(t, classMethodTable(t, c), i);
          if (((methodName == 0
                or ::strcmp
                (reinterpret_cast<char*>
                 (&byteArrayBody
                  (t, vm::methodName(t, method), 0)), methodName) == 0)
               and (methodSpec == 0
                    or ::strcmp
                    (reinterpret_cast<char*>
                     (&byteArrayBody
                      (t, vm::methodSpec(t, method), 0)), methodSpec)
                    == 0)))
          {
            if (methodCode(t, method)
                or (methodFlags(t, method) & ACC_NATIVE))
            {
              PROTECT(t, method);

              t->m->processor->compileMethod
                (t, zone, &constants, &calls, &addresses, method);
            }

            object addendum = methodAddendum(t, method);
            if (addendum and methodAddendumExceptionTable(t, addendum)) {
              PROTECT(t, addendum);

              // resolve exception types now to avoid trying to update
              // immutable references at runtime
              for (unsigned i = 0; i < shortArrayLength
                     (t, methodAddendumExceptionTable(t, addendum)); ++i)
              {
                uint16_t index = shortArrayBody
                  (t, methodAddendumExceptionTable(t, addendum), i) - 1;

                object o = singletonObject
                  (t, addendumPool(t, addendum), index);

                if (objectClass(t, o) == type(t, Machine::ReferenceType)) {
                  o = resolveClass
                    (t, root(t, Machine::BootLoader), referenceName(t, o));
    
                  set(t, addendumPool(t, addendum),
                      SingletonBody + (index * BytesPerWord), o);
                }
              }
            }
          }
        }
      }
    }
  }

  for (; calls; calls = tripleThird(t, calls)) {
    object method = tripleFirst(t, calls);
    uintptr_t address;
    if (methodFlags(t, method) & ACC_NATIVE) {
      address = reinterpret_cast<uintptr_t>(code + image->thunks.native.start);
    } else {
      address = codeCompiled(t, methodCode(t, method));
    }

    static_cast<ListenPromise*>(pointerValue(t, tripleSecond(t, calls)))
      ->listener->resolve(address, 0);
  }

  for (; addresses; addresses = addresses->next) {
    uint8_t* value = reinterpret_cast<uint8_t*>(addresses->basis->value());
    assert(t, value >= code);

    void* location;
    bool flat = addresses->listener->resolve
      (reinterpret_cast<int64_t>(code), &location);
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

  for (HashMapIterator it(t, classLoaderMap(t, root(t, Machine::BootLoader)));
       it.hasMore();)
  {
    w->visitRoot(tripleSecond(t, it.next()));
  }

  image->bootLoader = w->visitRoot(root(t, Machine::BootLoader));
  image->appLoader = w->visitRoot(root(t, Machine::AppLoader));
  image->types = w->visitRoot(m->types);

  m->processor->visitRoots(t, w);

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
        if ((currentObject
             and (currentOffset * BytesPerWord) == ClassStaticTable)
            or instanceOf(t, type(t, Machine::SystemClassLoaderType), p))
        {
          // Static tables and system classloaders must be allocated
          // as fixed objects in the heap image so that they can be
          // marked as dirty and visited during GC.  Otherwise,
          // attempts to update references in these objects to point
          // to runtime-allocated memory would fail because we don't
          // scan non-fixed objects in the heap image during GC.

          FixedAllocator allocator
            (t->m->system, reinterpret_cast<uint8_t*>(heap + position),
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
writeBootImage2(Thread* t, FILE* out, BootImage* image, uint8_t* code,
                unsigned codeCapacity, const char* className,
                const char* methodName, const char* methodSpec)
{
  Zone zone(t->m->system, t->m->heap, 64 * 1024);

  uintptr_t* codeMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(codeMapSize(codeCapacity)));
  memset(codeMap, 0, codeMapSize(codeCapacity));

  object constants = makeCodeImage
    (t, &zone, image, code, codeMap, className, methodName, methodSpec);

  PROTECT(t, constants);

  // this map will not be used when the bootimage is loaded, so
  // there's no need to preserve it:
  setRoot(t, Machine::ByteArrayMap, makeWeakHashMap(t, 0, 0));

  // name all primitive classes so we don't try to update immutable
  // references at runtime:
  { object name = makeByteArray(t, "void");
    set(t, type(t, Machine::JvoidType), ClassName, name);
    
    name = makeByteArray(t, "boolean");
    set(t, type(t, Machine::JbooleanType), ClassName, name);

    name = makeByteArray(t, "byte");
    set(t, type(t, Machine::JbyteType), ClassName, name);

    name = makeByteArray(t, "short");
    set(t, type(t, Machine::JshortType), ClassName, name);

    name = makeByteArray(t, "char");
    set(t, type(t, Machine::JcharType), ClassName, name);

    name = makeByteArray(t, "int");
    set(t, type(t, Machine::JintType), ClassName, name);

    name = makeByteArray(t, "float");
    set(t, type(t, Machine::JfloatType), ClassName, name);

    name = makeByteArray(t, "long");
    set(t, type(t, Machine::JlongType), ClassName, name);

    name = makeByteArray(t, "double");
    set(t, type(t, Machine::JdoubleType), ClassName, name);
  }

  collect(t, Heap::MajorCollection);

  uintptr_t* heap = static_cast<uintptr_t*>
    (t->m->heap->allocate(HeapCapacity));
  uintptr_t* heapMap = static_cast<uintptr_t*>
    (t->m->heap->allocate(heapMapSize(HeapCapacity)));
  memset(heapMap, 0, heapMapSize(HeapCapacity));

  HeapWalker* heapWalker = makeHeapImage
    (t, image, heap, heapMap, HeapCapacity, constants);

  updateConstants(t, constants, code, codeMap, heapWalker->map());

  image->bootClassCount = hashMapSize
    (t, classLoaderMap(t, root(t, Machine::BootLoader)));

  unsigned* bootClassTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->bootClassCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it
           (t, classLoaderMap(t, root(t, Machine::BootLoader)));
         it.hasMore();)
    {
      bootClassTable[i++] = heapWalker->map()->find
        (tripleSecond(t, it.next()));
    }
  }

  image->appClassCount = hashMapSize
    (t, classLoaderMap(t, root(t, Machine::AppLoader)));

  unsigned* appClassTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->appClassCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it
           (t, classLoaderMap(t, root(t, Machine::AppLoader)));
         it.hasMore();)
    {
      appClassTable[i++] = heapWalker->map()->find(tripleSecond(t, it.next()));
    }
  }

  image->stringCount = hashMapSize(t, root(t, Machine::StringMap));
  unsigned* stringTable = static_cast<unsigned*>
    (t->m->heap->allocate(image->stringCount * sizeof(unsigned)));

  { unsigned i = 0;
    for (HashMapIterator it(t, root(t, Machine::StringMap)); it.hasMore();) {
      stringTable[i++] = heapWalker->map()->find
        (jreferenceTarget(t, tripleFirst(t, it.next())));
    }
  }

  unsigned* callTable = t->m->processor->makeCallTable(t, heapWalker);

  heapWalker->dispose();

  image->magic = BootImage::Magic;
  image->codeBase = reinterpret_cast<uintptr_t>(code);

  fprintf(stderr, "class count %d string count %d call count %d\n"
          "heap size %d code size %d\n",
          image->bootClassCount, image->stringCount, image->callCount,
          image->heapSize, image->codeSize);

  if (true) {
    fwrite(image, sizeof(BootImage), 1, out);

    fwrite(bootClassTable, image->bootClassCount * sizeof(unsigned), 1, out);
    fwrite(appClassTable, image->appClassCount * sizeof(unsigned), 1, out);
    fwrite(stringTable, image->stringCount * sizeof(unsigned), 1, out);
    fwrite(callTable, image->callCount * sizeof(unsigned) * 2, 1, out);

    unsigned offset = (image->bootClassCount * sizeof(unsigned))
      + (image->appClassCount * sizeof(unsigned))
      + (image->stringCount * sizeof(unsigned))
      + (image->callCount * sizeof(unsigned) * 2);

    while (offset % BytesPerWord) {
      uint8_t c = 0;
      fwrite(&c, 1, 1, out);
      ++ offset;
    }

    fwrite(heapMap, pad(heapMapSize(image->heapSize)), 1, out);
    fwrite(heap, pad(image->heapSize), 1, out);

    fwrite(codeMap, pad(codeMapSize(image->codeSize)), 1, out);
    fwrite(code, pad(image->codeSize), 1, out);
  }
}

uint64_t
writeBootImage(Thread* t, uintptr_t* arguments)
{
  FILE* out = reinterpret_cast<FILE*>(arguments[0]);
  BootImage* image = reinterpret_cast<BootImage*>(arguments[1]);
  uint8_t* code = reinterpret_cast<uint8_t*>(arguments[2]);
  unsigned codeCapacity = arguments[3];
  const char* className = reinterpret_cast<const char*>(arguments[4]);
  const char* methodName = reinterpret_cast<const char*>(arguments[5]);
  const char* methodSpec = reinterpret_cast<const char*>(arguments[6]);

  writeBootImage2
    (t, out, image, code, codeCapacity, className, methodName, methodSpec);

  return 1;
}

} // namespace

int
main(int ac, const char** av)
{
  if (ac < 3 or ac > 6) {
    fprintf(stderr, "usage: %s <classpath> <output file> "
            "[<class name> [<method name> [<method spec>]]]\n", av[0]);
    return -1;
  }

  System* s = makeSystem(0);
  Heap* h = makeHeap(s, HeapCapacity * 2);
  Classpath* c = makeClasspath(s, h, AVIAN_JAVA_HOME, AVIAN_EMBED_PREFIX);
  Finder* f = makeFinder(s, h, av[1], 0);
  Processor* p = makeProcessor(s, h, false);

  BootImage image;
  const unsigned CodeCapacity = 128 * 1024 * 1024;
  uint8_t* code = static_cast<uint8_t*>(h->allocate(CodeCapacity));
  p->initialize(&image, code, CodeCapacity);

  Machine* m = new (h->allocate(sizeof(Machine))) Machine
    (s, h, f, 0, p, c, 0, 0);
  Thread* t = p->makeThread(m, 0, 0);
  
  enter(t, Thread::ActiveState);
  enter(t, Thread::IdleState);

  FILE* output = vm::fopen(av[2], "wb");
  if (output == 0) {
    fprintf(stderr, "unable to open %s\n", av[2]);    
    return -1;
  }

  uintptr_t arguments[] = { reinterpret_cast<uintptr_t>(output),
                            reinterpret_cast<uintptr_t>(&image),
                            reinterpret_cast<uintptr_t>(code),
                            CodeCapacity,
                            reinterpret_cast<uintptr_t>(ac > 3 ? av[3] : 0),
                            reinterpret_cast<uintptr_t>(ac > 4 ? av[4] : 0),
                            reinterpret_cast<uintptr_t>(ac > 5 ? av[5] : 0) };

  run(t, writeBootImage, arguments);

  fclose(output);

  if (t->exception) {
    printTrace(t, t->exception);
    return -1;
  } else {
    return 0;
  }
}
